from collections import defaultdict
import random
import os
import numpy as np
from datetime import datetime
import pickle
from copy import deepcopy


class BetaOne:

    def __init__(self, environment, mcts,
                 log_best_network="log_best_network",
                 log_dataset="log_dataset",
                 render=True):
        """
        An reinforcement learning algorithm loosely following
        AlphaGo Zero / AlphaZero

        Parameters
        ----------
        environment : OpenAI Gym Environment
            Environment of the game
        mcts : MCTSBase
            MCTS including a policy-value network
        log_best_network : string, optional
            Directory for storing best networks
        log_dataset : string, optional
            Directory for storing dataset from self-play
        """

        self.environment = environment
        self.mcts = mcts
        #self.best_network = self.mcts.networks[0].deepcopy()
        self.best_network = deepcopy(self.mcts.networks[0])
        self.n_agents = len(mcts.networks)

        self.size_datasets = 1000
        
        self.log_best_network = log_best_network
        if not os.path.exists(self.log_best_network):
            os.mkdir(self.log_best_network)
        self.log_dataset = log_dataset
        if not os.path.exists(self.log_dataset):
            os.mkdir(self.log_dataset)
        
        self.render = render
        self.verbose = False

    def run(self, n_repeat, n_iterations, n_mcts, temperature=1.0):
        """
        Run the BetaOne algorithm

        Parameters
        ----------
        n_repeat : int
            Number of runs of self-play, optimization, evaluation
        n_iterations : int
            Number of iterations of a self-play in each run
        n_mcts : int
            Number of MCTS at each node to select an action
        temperature : float, optional
            temperature when MCTS selects actions
        """

        datasets = list()
        for _ in range(n_repeat):
            
            # Self-play
            print("Run self-play for", n_iterations, "iterations with",
                  n_mcts, "MCTS search for each action")
            dataset, rewards = self.play(n_iterations, n_mcts, temperature)
            datasets += dataset
            # Dump the dataset
            filename = datetime.now().strftime("%y%m%d-%H%M%S") + ".pkl"
            filename = os.path.join(self.log_dataset, filename)
            with open(filename, mode="wb") as f:
                pickle.dump(dataset, f)

            # Optimize the network based on the self-play
            print("Optimizing the network... ", end="")
            self.optimize(datasets)
            print("Done")

            # Evaluate the new network against the best to see
            # if the new network becomes best
            print("Evaluating the new network...", end="")
            better = self.evaluate(self.mcts.networks[0],
                                   self.best_network,
                                   n_iterations, n_mcts)
            print("Done")

            # Replace the best if the new network is better
            if better:
                print("Replacing the best network")
                # Store the best network
                #self.best_network = self.mcts.networks[0].deepcopy()
                self.best_network = deepcopy(self.mcts.networks[0])
                # Dump the best network
                filename = datetime.now().strftime("%y%m%d-%H%M%S") + ".hdf5"
                filename = os.path.join(self.log_best_network, filename)
                self.best_network.save_weights(filename)
            else:
                print("Best network unchanged")

            # Truncate the datasets to use
            datasets = datasets[-self.size_datasets:]

    def play(self, n_iterations, n_mcts, temperature=1.0):
        """
        Run self-play for a given number of iterations

        Parameters
        ----------
        n_iterations : int
            Number of iterations
        n_mcts : int
            Number of MCTS at each node to select an action
        temperature : float, optional
            temperature when MCTS selects actions

        Return
        ------
        dataset : list
            list of dictionaries, containing training data based on the self play
        rewards : list
            list of rewards received by the agents
        """
        
        dataset = list()
        rewards = list()
        for i in range(n_iterations):
            if self.verbose:
                print("Self play iteration %d/%d" % (i, n_iterations))

            # Reset environment to random initial state
            self.environment._init_game_state = None
            obs = self.environment.reset()

            # Run self play
            data, reward = self._play_each(n_mcts, temperature)
            dataset.append(data)
            rewards.append(reward)

        return dataset, rewards

    def _play_each(self, n_mcts, temperature=1.0):
        """
        Run self-play once

        Parameters
        ----------
        n_mcts : int
            Number of MCTS at each node to select an action
        temperature : float, optional
            temperature when MCTS selects actions

        Return
        ------
        data : dictionary of list
            data["X"] : list of input
            data["y"] : list of pair of action probabilities and value
        reward : array
            reward for each agent
        """

        data = defaultdict(list)
        terminal = False
        states = self.environment.reset()
        cumulative_reward = np.zeros(len(states))
        while not terminal:
            if self.render:
                self.environment.render()

            # Run MCTS to get action probabilities and values
            distributions, values = self.mcts.run(n_mcts, temperature)
            for state, pi, value in zip(states, distributions, values):
                node = self.mcts._get_feature(state)
                data["X"].append(node)
                data["action"].append(pi)
                data["value"].append([value])

            # Choose action for each agent
            actions = [random.choices(range(self.environment.action_space.n),
                                      weights=pi)[0]
                       for pi in distributions]

            # Take the actions
            if self.verbose:
                print("Taking self-play actions", actions,
                      "at state", self.environment.model.state,
                      "at step", self.environment._step_count)
            states, reward, terminal, info = self.environment.step(actions)
            cumulative_reward += reward

        if self.render:
            self.environment.render()

        return data, cumulative_reward

    def optimize(self, dataset, index=0):
        """
        Optimize the policy-value network, using the dataset

        Parameters
        ----------
        dataset : list
            list of dictionaries of data, generated by self.play
        index : int, optional
            index of the network to optimize
        """

        X = np.vstack([data["X"] for data in dataset])
        y = np.vstack([data["action"] for data in dataset])
        z = np.vstack([data["value"] for data in dataset])
        self.mcts.networks[index].fit(X, [y, z])
            
    def evaluate(self, new_network, best_network, n_iterations, n_mcts):
        """
        Parameters
        ----------
        new_network : Network
            New policy-value network to be compared against the best
        best_network : Network
            Current best policy-value network
        n_iterations : int
            Number of iterations of a self play
        n_mcts : int
            Number of MCTS at each node to select an action

        Return
        ------
        boolean
            whether the new network is better than the best
        """

        # Choose the positions of new agents (and best agents)
        if self.n_agents==1:
            new_agents = [0]
        else:
            new_agents = random.sample(range(self.n_agents), k=self.n_agents/2)
        print("Evaluating new networks at", new_agents)

        # Set the policy-value networks according to the positions
        networks = list()
        for i in range(self.n_agents):
            if i in new_agents:
                networks.append(new_network)
            else:
                networks.append(best_network)
        self.mcts.set_networks(networks)

        # Run self-play
        _, rewards = self.play(n_iterations, n_mcts, temperature=0)
        
        return self._new_is_better(rewards, new_agents)

    def _new_is_better(self, rewards, agents):
        """
        Determine whether the (new) agents are better than the others (best agents)

        Parameters
        ----------
        rewards : list
            List of rewards for the agents in self play
        agents : list
            Positions of the (new) agents
        """
        # Compute the winning rate of the specified agents
        n_wins = np.sum(np.array(rewards)==1, axis=0)
        total_wins = np.sum(n_wins)
        if total_wins == 0:
            return True
        win_rate = np.sum([n_wins[i] for i in agents]) / total_wins

        if win_rate > 0.55:
            return True
        else:
            return False
