import numpy as np
import gym
import pommerman
from pommerman import agents
from trained_tensorforce_agent import TrainedTensorForceAgent
from train_ibm_agent import restore_agent


def main(config, render=False):

    # List of four agents
    env = gym.make(config)
    agent = agents.TensorForceAgent(algorithm="ppo")
    agent = agent.initialize(env)
    agent_list = [
        agents.SimpleAgent(),
        agents.SimpleAgent(),
        agents.SimpleAgent(),
        TrainedTensorForceAgent(restore_agent(agent), env),
    ]

    # Environment
    env = pommerman.make(config, agent_list)

    # Run
    rewards = list()
    for episode in range(100):
        state = env.reset()
        done = False
        while not done:
            if render:
                env.render()
            actions = env.act(state)
            state, reward, done, info = env.step(actions)
        rewards.append(reward)
        print('Episode {} finished'.format(episode), reward, np.mean(rewards, axis=0))
    print(np.mean(rewards, axis=0))
    env.close()


if __name__ == '__main__':

    config = 'PommeFFACompetition-v0'
    main(config, render=True)#False)
