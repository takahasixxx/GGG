#
# Can profile by
#
#     python -m cProfile -s cumulative test.py
#

import pommerman
from src.agents.master_agent import MasterAgent as MyAgent
import random

def main():

    config = 'PommeTeamCompetition-v0'
    
    # List of four agents
    agent_list = [
        MyAgent(),
        MyAgent(),
        MyAgent(),
        MyAgent(),
    ]

    # Environment
    env = pommerman.make(config, agent_list)

    # Run
    state = env.reset()
    done = False
    step = 0
    while not done:
        step += 1
        print("STEP", step)
        actions = env.act(state)
        state, reward, done, info = env.step(actions)
    env.close()

    return

if __name__ == "__main__":

    random.seed(0)
    main()
