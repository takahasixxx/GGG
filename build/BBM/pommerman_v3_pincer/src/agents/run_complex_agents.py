import pommerman
from complex_agent import ComplexAgent, SimpleAgentSeed
import numpy as np
import argparse
import os

def main(recently_visited_length=6,
         enemy_range=3,
         item_range=2,
         wood_range=2):
    # List of four agents
    agent_list = [
        ComplexAgent(recently_visited_length,
                     enemy_range,
                     item_range,
                     wood_range),
        SimpleAgentSeed(1),
        SimpleAgentSeed(2),
        SimpleAgentSeed(3)
    ]

    # Environment of FFA competition
    env = pommerman.make('PommeFFACompetition-v0', agent_list)

    # Run
    cum_reward = np.zeros(len(agent_list))
    n_episode = 100
    for episode in range(n_episode):
        state = env.reset()
        done = False
        while not done:
            #env.render()
            actions = env.act(state)
            state, reward, done, info = env.step(actions)
        cum_reward += np.array(reward)

    ave_reward = cum_reward / n_episode

    env.close()

    return ave_reward


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description="Train an IBM agent")
    parser.add_argument("recent", help="recently_visited_length")
    parser.add_argument("enemy", help="enemy_range")
    parser.add_argument("item", help="item_range")
    parser.add_argument("wood", help="wood_range")
    args = parser.parse_args()

    recently_visited_length = int(args.recent)
    enemy_range = int(args.enemy)
    item_range = int(args.item)
    wood_range = int(args.wood)

    ave_reward = main(recently_visited_length,
                      enemy_range,
                      item_range,
                      wood_range)

    print("Average reward", ave_reward, args)

    directory = "log_complex"
    if not os.path.exists(directory):
        os.mkdir(directory)
    filename = "reward_" + args.recent + "_" + args.enemy + "_" + args.item + "_" + args.wood + ".npy"
    np.save(os.path.join(directory, filename),
            ave_reward)
