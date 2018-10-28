import pommerman
from pommerman import agents

import sys
import argparse

import numpy as np


from src.agents.master_agent import MasterAgent as MyAgentO
from tt_agent import MyAgent as MyAgentT


verbose = False


def main(render=False, interactive=False):
    # List of four agents
    agent_list = [
        #agents.SimpleAgent(),
        MyAgentO(),
        MyAgentT(),
        #agents.SimpleAgent(),
        #agents.SimpleAgent(),
        #TimeExpandedAgent(),
        MyAgentO(),
        MyAgentT(),
        #MyAgent(),
        #MyAgent(),
        #SimpleAgentDebugged(),
        #agents.DockerAgent("pommerman/ibm-agent", port=12345),
    ]

    # Environment of FFA competition
    #env = pommerman.make('PommeFFACompetition-v0', agent_list)
    env = pommerman.make('PommeTeamCompetition-v0', agent_list)


    # Run
    rewards = list()
    for episode in range(10000000):
        state = env.reset()
        done = False
        step = 0
        while not done:
            if verbose:
                print("Step: ", step)
            step += 1
            if render:
                env.render()
            actions = env.act(state)
            if verbose:
                print(actions[-1])
            state, reward, done, info = env.step(actions)
            if interactive:
                sys.stdin.readline()

        for agent in agent_list:
            agent.episode_end(reward[agent.agent_id])
        #rewards.append(reward)
        print('Episode {} finished'.format(episode), reward)

    rewards = np.array(rewards)
    print(np.mean(rewards, axis=0))
    env.close()






if __name__ == '__main__':

    parser = argparse.ArgumentParser(description="Train an IBM agent")
    parser.add_argument("--render",
                        default=False,
                        action="store_true",
                        help="Whether to render or not. Defaults to False.")
    parser.add_argument("--interactive",
                        default=False,
                        action="store_true",
                        help="Whether to pause after each step.")
    args = parser.parse_args()

    #random.seed(0)
    main(render=args.render, interactive=args.interactive)
