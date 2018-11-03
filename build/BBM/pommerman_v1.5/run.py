import os
import sys
import json
import random
import argparse
import numpy as np
import gym
import pommerman
path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "examples")
sys.path.append(path)
from src.agents.agents_osogami_team import TimeExpandedAgent as MyAgent
from src.agents.agents_osogami_comp2 import TimeExpandedAgent as PreviousAgent
#from ffa_competition.run_my_agent import _MyAgent as PreviousAgent
from pommerman.agents.simple_agent import SimpleAgent

def main(location, render=False, interactive=False, record_json_dir=None):

    # List of four agents
    """
    agent_list = [
        pommerman.agents.DockerAgent("comp2/osogami1", port=12345),
        pommerman.agents.DockerAgent("comp2/osogami1", port=12346),
        pommerman.agents.DockerAgent("comp2/osogami1", port=12347),
        pommerman.agents.DockerAgent("comp2/osogami1", port=12348),
    ]
    """
    agent_list = [
        PreviousAgent(),
        PreviousAgent(),
        PreviousAgent(),
        PreviousAgent(),
    ]
    agent_list = [
        SimpleAgent(),
        SimpleAgent(),
        SimpleAgent(),
        SimpleAgent(),
    ]
    agent_list[location] = MyAgent()

    # Environment
    config = 'PommeFFACompetition-v0'
    #config = 'PommeTeamCompetition-v0'
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

    CHECKPOINT = 0
            
    while not done:
        step += 1
        print("STEP: %d" % step)
        if render and step > CHECKPOINT:
            env.render()
        actions = env.act(state)
        state, reward, done, info = env.step(actions)
        if record_json_dir is not None:
            suffix = '%d.json' % step
            info = env.get_json_info()
            with open(os.path.join(record_json_dir, suffix), 'w') as f:
                f.write(json.dumps(info, sort_keys=True, indent=4))
        if interactive and step > CHECKPOINT:
            sys.stdin.readline()
    env.render()
    sys.stdin.readline()
    env.close()

    return reward


if __name__ == '__main__':

    parser = argparse.ArgumentParser(description="Run a competition agent")
    parser.add_argument("location", help="Initial location")
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
    reward = main(location=int(args.location),
                  render=args.render,
                  interactive=args.interactive,
                  record_json_dir=record_json_dir)

    directory = "log"
    if not os.path.exists(directory):
        os.mkdir(directory)
    filename = "reward_" + args.location + "_" + args.seed + ".npy"
    filename = os.path.join(directory, filename)
    np.save(filename, reward)
