#################################
# (C) Copyright IBM Corp. 2018
#################################
import pommerman
from pommerman import agents
from tt_agent import MyAgent as AgentTT


def main():
    """Simple function to bootstrap a game"""
    # Print all possible environments in the Pommerman registry
    print(pommerman.REGISTRY)



    # Create a set of agents (exactly four)
    agent_list = [
        agents.SimpleAgent(),
        AgentTT(),
        agents.SimpleAgent(),
        AgentTT(),
    ]


    # Make the "Free-For-All" environment using the agent list
    env = pommerman.make('PommeTeamCompetition-v0', agent_list)
    #env = pommerman.make(''PommeTeamCompetition-v1'', agent_list)



    # Run the episodes just like OpenAI Gym
    for i_episode in range(1):
        state = env.reset()
        done = False
        for frame in range(30):
            actions = env.act(state)
            state, reward, done, info = env.step(actions)
        print('Episode {} finished'.format(i_episode))
    env.close()


if __name__ == '__main__':
    main()
    