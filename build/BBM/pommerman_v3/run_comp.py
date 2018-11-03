import os
import sys
import json
import random
import argparse
import numpy as np
import gym
import pommerman
from pommerman import agents


def main(agent_names, competition="comp1", render=False, interactive=False, record_json_dir=None):

    # List of four agents
    agent_list = [agents.DockerAgent(competition+"/"+agent_names[i], port=12345+i)
                  for i in range(4)]

    # Environment
    config = 'PommeFFACompetition-v0'
    env = pommerman.make(config, agent_list)

    # Run
    state = env.reset()
    done = False
    step = 0
    
    if record_json_dir is not None:
        suffix = '%d.json' % step
        info = env.get_json_info()
        with open(os.path.join(record_json_dir, suffix), 'w') as f:
            f.write(json.dumps(info, sort_keys=True, indent=4))
    
    while not done:
        step += 1
        if render:
            env.render()
        actions = env.act(state)
        state, reward, done, info = env.step(actions)
        if record_json_dir is not None:
            suffix = '%d.json' % step
            info = env.get_json_info()
            with open(os.path.join(record_json_dir, suffix), 'w') as f:
                f.write(json.dumps(info, sort_keys=True, indent=4))
        if interactive:
            sys.stdin.readline()
    env.close()

    return reward


if __name__ == '__main__':

    parser = argparse.ArgumentParser(description="Run a competition agent")
    parser.add_argument("agent1", help="agent 1")
    parser.add_argument("agent2", help="agent 2")
    parser.add_argument("agent3", help="agent 3")
    parser.add_argument("agent4", help="agent 4")
    parser.add_argument("seed", help="Seed for random number generator")
    parser.add_argument("--record",
                        default=False,
                        action="store_true",
                        help="Whether to record json to replay. Defaults to False.")
    parser.add_argument("--render",
                        default=False,
                        action="store_true",
                        help="Whether to render or not. Defaults to False.")
    parser.add_argument("--interactive",
                        default=False,
                        action="store_true",
                        help="Whether to pause after each step.")
    args = parser.parse_args()

    random.seed(int(args.seed))

    if args.record:
        record_json_dir = "json_" + args.location + "_" + args.seed
        if not os.path.exists(record_json_dir):
            os.mkdir(record_json_dir)
    else:
        record_json_dir = None

    agent_names = [args.agent1,
                   args.agent2,
                   args.agent3,
                   args.agent4]
    reward = main(agent_names,
                  render=args.render,
                  interactive=args.interactive,
                  record_json_dir=record_json_dir)

    directory = "log_comp1"
    if not os.path.exists(directory):
        os.mkdir(directory)
    filename = "reward"
    for agent in agent_names:
        filename += "_" + agent
    filename += "_" + args.seed + ".npy"
    filename = os.path.join(directory, filename)
    np.save(filename, reward)
