import os
import numpy as np
import argparse
from tensorforce.execution import Runner
from tensorforce.contrib.openai_gym import OpenAIGym
import gym

from pommerman import make
from pommerman import characters
from pommerman.agents import TensorForceAgent, RandomAgent, SimpleAgent
from pommerman.cli.train_with_tensorforce import WrappedEnv

def make_agent_env(n_ppo, n_simple, render):
    # Create environment/agents
    config = "PommeFFACompetition-v0"
    agents = [TensorForceAgent(algorithm="ppo") for _ in range(n_ppo)]
    agents += [SimpleAgent() for _ in range(n_simple)]
    n_random = 4 - n_ppo - n_simple
    agents += [RandomAgent() for _ in range(n_random)]
    env = make(config, agents, None)
    training_agent = agents[0]
    env.set_training_agent(training_agent.agent_id)

    # Map to Tensorforce environment/agents
    wrapped_env = WrappedEnv(env, visualize=render)
    agent = training_agent.initialize(env)

    return agent, wrapped_env


def restore_agent(agent, id=None):
    directory = os.path.join(os.getcwd(), "log")
    if id is None:
        try:
            # restore latest
            ls = os.listdir(directory)
            index_files = [os.path.join(directory, f) for f in ls if f.endswith(".index")]
            index_files.sort(key=lambda x: os.path.getmtime(x), reverse=True)
            filename = index_files[0].strip(".index").split(os.sep)[-1]
            id = int(filename.strip("agent-"))
        except:
            return agent
    filename = "agent-" + str(id)
    print("Restoring from", directory, filename)
    agent.restore_model(directory=directory, file=filename)
    return agent


def main():
    parser = argparse.ArgumentParser(description="Train an IBM agent")
    parser.add_argument("--render",
                        default=False,
                        action='store_true',
                        help="Whether to render or not. Defaults to False.")
    args = parser.parse_args()

    for n_simple in [3]:#[1, 2, 3]:
    
        agent, environment = make_agent_env(1, n_simple, args.render)
        agent = restore_agent(agent)

        # Run
        runner = Runner(agent=agent, environment=environment)
        while True:
            runner.run(episodes=100, max_episode_timesteps=2000)
            ave_reward = np.mean(runner.episode_rewards)
            print("Average reward: %f with %d SimpleAgents"
                  % (ave_reward, n_simple))

            directory = os.path.join(os.getcwd(), "log", "agent")
            runner.agent.save_model(directory=directory)

            if ave_reward > 0 and n_simple < 3:
                break
            if ave_reward > 0.9:
                break

        try:
            runner.close()
        except AttributeError as e:
            pass


if __name__ == "__main__":
    main()
