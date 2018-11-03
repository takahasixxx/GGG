from collections import defaultdict
import numpy as np
import random
from copy import deepcopy

verbose = False

class MCTSBase:

    def __init__(self, env):
        """
        The base class of Monte Carlo Tree Search

        Parameters
        ----------
        env : gym.environment
            environment where MCTS is run
        """
        self.environment = env
        self.networks = None
        self.set_exploration_level(1.0)
        
        self.visit_count = defaultdict(int)  # N
        self.total_value = defaultdict(float)  # W
        self.prior_action_probability = dict()  # P
        # we will compute Q from W and N when needed

    def run(self, n_iterations, temperature):
        """
        Run a MCTS for n_iterations times
        To compute the distribution of actions (policy improvement)
        and the value of the root node (policy evaluation)

        Parameters
        ----------
        n_iterations : int
            Number of iterations from the root node
        temperature : float
            Temerature parameter used when choosing an action

        Returns
        -------
        distributions : array or array
            Distribution of the actions actions from the root node for each agent
        values : array
            Value for each of the agents
        """
        if verbose:
            print("Run MCTS from step", self.environment._step_count, "for", n_iterations, "iterations")

        if self.networks is None:
            print("Must set networks before run")
            return None, None

        # Run MCTS
        self._search(n_iterations)

        # Summarize the results
        distributions = list()
        values = list()
        for state in self.environment.get_observations():
            # Consider the node for each agent
            node = self._get_feature(state)
            # The distribution of actions at the node
            pi = self._get_posterior_action_probability(node, temperature)
            distributions.append(pi)
            # The value of the state
            value = self._get_posterior_value(node)
            values.append(value)
            
        return distributions, values

    def _search(self, n_iterations):
        """
        Run a MCTS for n_iterations times
        
        Parameters
        ----------
        n_iterations : int
            Number of iterations from the root node
        """
        # Store the root node (initial state)
        init_game_state = self.get_state()
        #print("START MCTS from", init_game_state, "for", n_iterations, "iterations")
        for _ in range(n_iterations):            
            # Restore the root node for a new run
            self.set_state(init_game_state)
            # Run the MCTS once
            self._search_each()
        # Restore the root node
        self.set_state(init_game_state)
     
    def _search_each(self):
        """
        Run a MCTS once
        """
        #
        # Forward search
        #
        # Root node seen by each agent
        states = self.environment.get_observations()
        state_features = [self._get_feature(state) for state in states]
        cumulative_rewards = np.zeros(len(states))
        
        # Add root node in the search path for each agent
        search_paths = [[node] for node in state_features]
        values_paths = [deepcopy(cumulative_rewards)]
        is_root = True
        while True:
            # Update the visit count of the node visited by each agent
            for node in state_features:
                self.visit_count[node] += 1                
            # Stop if leaf node
            if any([self.visit_count[f] == 1 for f in state_features]):
                # Get values to backup and set action probabilities at the leaf node
                values = list()
                for index, node in enumerate(state_features):
                    # value: the value of the node as estimated by the network
                    # pi: the prior action probabilities as estimated by the network
                    pi, value = self._evaluate_network(index, node)
                    for a in self._get_actions():
                        self.prior_action_probability[(node, a)] = pi[a]
                    values.append(value)
                if len(values_paths) > 0:
                    values_paths[-1] += values
                break
            # Choose the next action for each agent
            actions = list()
            for i, node in enumerate(state_features):            
                action = self._select_action(node, is_root)
                actions.append(action)
                search_paths[i].append((node, action))
            # Move to the next node
            states, rewards, terminal, info = self.environment.step(actions)
            cumulative_rewards += rewards
            values_paths.append(deepcopy(cumulative_rewards))
            if terminal:
                break
            is_root = False
            # Update the state feature (node) seen by each agent
            state_features = [self._get_feature(state) for state in states]

        values_paths = np.vstack(values_paths)
        values_paths = [values_paths[:,i].tolist() for i in range(values_paths.shape[1])]
            
        #
        # Backward update
        #
        self._backup(values_paths, search_paths)

    def _backup(self, values_paths, search_paths):
        """
        Backup given values along given search paths

        Parameters
        ----------
        last_values : list
            values along the paths to backup for each agent
        search_paths : list
            search path which the value is backed up for each agent
        """
        if verbose:
            print("Backup")
            print(" values", values_paths)
            print(" nodes ", search_paths)
        for values_path, search_path in zip(values_paths, search_paths):
            # Backup a value along a search path for each agent
            self._backup_each(values_path, search_path)

    def _backup_each(self, values_path, search_path):
        """
        Backup a given value along a given search path

        Parameters
        ----------
        last_value : float
            value to backup
        search_path : list
            list of node (state feature) along which the value is backed up
        """
        self.visit_count[search_path[0]] += 1
        for i in range(1, len(search_path)):
            node = search_path[i]
            # Update visit count
            self.visit_count[node] += 1
            # Update total value (action_value * N)
            self.total_value[node] += values_path[-1] - values_path[i-1]
            
        """
        for node in search_path:
            # Update visit count
            self.visit_count[node] += 1
            # Update total value (action_value * N)
            self.total_value[node] += last_value
        """

    def _evaluate_network(self, index, input):
        """
        Evaluate the policy-value network with input, which is a leaf node in MCTS

        Parameters
        ----------
        index : int
            index of the network to evaluate
        input : tuple
            Input to the network (feature of a state)
        
        Return
        ------
        distribution : array
            Output of the action head, representing the distribution of actions at the state
        value : float
            Output of the value head, representing the value of the state
        """
        distribution, value = self.networks[index].evaluate([input])
        # value is given by an array of size 1
        return distribution[0], float(value)

    def _select_action(self, node, add_noise):
        """
        Select an action at a given node in MCTS

        Parameters
        ----------
        node : tuple
            node (feature of a state) where an action is selected
        add_noise : boolean
            whether to add Dirichlet noise

        Return
        ------
        action_index : int
            index of the action selected at the node
        """
        # Compute UCB(a) = Q(a) + c_puct * P(a) * sqrt(sum_b N(b)) / (1 + N(a))
        Q = [self.total_value[(node, a)] / max([1, self.visit_count[(node, a)]])
             for a in self._get_actions()]
        if verbose:
            print("Choosing action at", node)
            print(" total value", [self.total_value[(node, a)] for a in self._get_actions()])
            print(" visit_count", [self.visit_count[(node, a)] for a in self._get_actions()])
            print(" prior_actio", [self.prior_action_probability[(node, a)] for a in self._get_actions()])
        # const: c_puct * sqrt(sum_b N(b))
        const = self.exploration_level
        const *= np.sqrt(np.sum([self.visit_count[(node, b)]
                                 for b in self._get_actions()]))
        # U(a): P(a) / (1 + N(a))
        P = np.array([self.prior_action_probability[(node, a)] for a in self._get_actions()])
        if add_noise:
            alpha = 0.03
            eps = 0.25
            eta = np.random.dirichlet(np.ones(self.environment.action_space.n)*alpha)
            P = (1 - eps) * P + eps * eta 
        #U = [self.prior_action_probability[(node, a)] / (1 + self.visit_count[(node, a)])
        #     for a in self._get_actions()]
        U = [P[a] / (1 + self.visit_count[(node, a)]) for a in self._get_actions()]
        UCB = np.array(Q) + const * np.array(U)
        # Choose (the index of) the action having the maximum UCB
        action_index = np.argmax(UCB)
        if verbose:
            print(" Q", Q)
            print(" const", const)
            print(" U", U)
            print(" selected ", action_index, UCB)
        return action_index

    def _get_posterior_action_probability(self, node, temperature):
        """
        Compute the posterior action probability (after MCTS)

        Parameters
        ----------
        node : tuple
            node (state feature) where posterior action probability is computed
        tempearture : float
            level of exploration

        Return
        ------
        pi : array
            posterior action probability
        """
        pi = [self.visit_count[(node, a)] for a in self._get_actions()]
        if temperature > 0:
            # pi(a) ~ N(a)**(1/temperature)
            pi = [p**(1/temperature) for p in pi]
            pi /= np.sum(pi)
        else:
            # greedy if temperature is 0
            idx = np.nanargmax(pi)
            pi = np.array([int(i==idx) for i in self._get_actions()])
        return pi

    def _get_posterior_value(self, node):
        """
        Compute the posterior value of a (root) node (after MCTS)

        Parameters
        ----------
        node : tuple
            node (state feature) where posterior action probability is computed        

        value : float
            posterior value
        """
        N = [self.visit_count[(node, a)] for a in self._get_actions()]
        W = [self.total_value[(node, a)] for a in self._get_actions()]
        value = np.sum(W) / np.sum(N)
        return value
    
    def set_networks(self, networks):
        """
        Set the policy-value network used to choose actions and evaluate leaf nodes in MCTS
 
        Parameters
        ----------
        networks : list of Network
            policy-value network for each agent
        """
        self.networks = networks
        
    def set_exploration_level(self, c_puct):
        """
        Set the c_puct constant that determines the level of exploration in MCTS
        c_puct is a constant not specified in the paper
        perhaps one of the hyper parameters determined by Bayesian optimization
        1 or 5 is used in some blogs
        
        exploration_level: float
            constant determining the level of exploration
        """
        self.exploration_level = c_puct

    def _get_actions(self):
        """
        Get the iterator over the index of actions

        Return
        ------
        range
            range(number_of_actions)
        """
        return range(self.environment.action_space.n)

    def get_state(self):
        raise NotImplementedError()

    def set_state(self, state):
        raise NotImplementedError()
            
    def _get_feature(self, state, action=None):
        raise NotImplementedError()

