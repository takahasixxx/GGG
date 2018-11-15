import os
import sys
import csv
import argparse
import random
import numpy as np
from run_comp import main
import pommerman
from src.agents.master_agent import MasterAgent as MyAgent
from collections import defaultdict
from copy import deepcopy


def main(location, base_params, new_params,
         render=False, interactive=False, checkpoint=[0,0]):

    config = 'PommeTeamCompetition-v0'

    # List of four agents
    agent_list = [
        MyAgent(**base_params),
        MyAgent(**base_params),
        MyAgent(**base_params),
        MyAgent(**base_params),
    ]

    agent_list[location] = MyAgent(**new_params)
    agent_list[location+2] = MyAgent(**new_params)

    # Environment
    env = pommerman.make(config, agent_list)

    # Run
    state = env.reset()
    done = False
    step = 0
    while not done:
        step += 1
        #print("STEP", step)
        if render and step > checkpoint[0]:
            env.render()
        actions = env.act(state)
        state, reward, done, info = env.step(actions)
        if interactive and step > checkpoint[1]:
            sys.stdin.readline()
    if render:
        env.render()
        sys.stdin.readline()
    env.close()

    return reward, step

if __name__ == "__main__":

    parser = argparse.ArgumentParser(description="self-play")
    parser.add_argument("--run",
                        nargs=2,
                        help="Run competitions")
    parser.add_argument("--analyze",
                        action="store_true",
                        default=False,
                        help="Analyze results")
    parser.add_argument("--render",
                        nargs=1,
                        default=0,
                        help="Render")
    parser.add_argument("--interactive",
                        nargs=1,
                        default=0,
                        help="render and pause after each step.")
    args = parser.parse_args()

    base_params = {
        "search_range" : 10,
        "enemy_mobility" : 4,
        "enemy_bomb" : 1,
        "chase_until" : 25,
        "inv_tmp" : 100,
        "interfere_threshold" : 0.5,
        "my_survivability_coeff" : 0.5,
        "teammate_survivability_coeff" : 0.5,
        "bomb_threshold" : 0.1,
        "chase_threshold" : 0.1,
        "backoff" : 0.9
    }

    logdir = "log_selfplay"
    for value in base_params.values():
        logdir += "_" + str(value)
    print("logging in", logdir)
    if not os.path.exists(logdir):
        os.mkdir(logdir)
    
    if args.run:
        
        seed = int(args.run[0])
        location = int(args.run[1])

        pairs = list()
        for key in ["enemy_mobility", "enemy_bomb", "chase_until"]:
            for diff in [-1, 1]:
                value = base_params[key] + diff
                pairs.append((key, value))

        for key in ["inv_tmp",
                    "my_survivability_coeff",
                    "teammate_survivability_coeff",
                    "bomb_threshold",
                    "chase_threshold"]:
            for factor in [0.5, 2]:            
                value = base_params[key] * factor
                pairs.append((key, value))

        for key in ["backoff"]:
            for value in [0.8, 0.95]:
                pairs.append((key,value))

        for key in ["interfere_threshold"]:
            for value in [0.25, 0.75]:
                pairs.append((key,value))

        pairs.append((None, None))
                
        filename = str(seed) + "_" + str(location) + ".csv"
        filename = os.path.join(logdir, filename)
        if os.path.exists(filename):
            print("Exists", filename)
            exit()
        
        with open(filename, 'w') as f:
            writer = csv.writer(f)
        
            for key, value in pairs:
                new_params = deepcopy(base_params)
                if key is not None:
                    new_params[key] = value

                if args.interactive:
                    int_checkpoint = int(args.interactive[0])
                    ren_checkpoint = int(args.interactive[0])
                elif args.render:
                    int_checkpoint = 0
                    ren_checkpoint = int(args.render[0])
                else:
                    int_checkpoint = 0
                    ren_checkpoint = 0

                random.seed(seed)
                print("seed", seed, key, value)
                rewards, step = main(location,
                                     base_params,
                                     new_params,
                                     render=(args.render or args.interactive),
                                     interactive=args.interactive,
                                     checkpoint=[ren_checkpoint, int_checkpoint])
                print(rewards)

                row = rewards + [step, key, value]
                writer.writerow(row)
        

    if args.analyze:

        ls = os.listdir(logdir)

        n_win = defaultdict(int)
        n_tie = defaultdict(int)
        n_loss = defaultdict(int)
        n_steps = defaultdict(int)
        
        for filename in ls:
            words = filename.split(".")[0].split("_")
            seed = int(words[0])
            location = int(words[1])
            
            with open(os.path.join(logdir,filename), 'r') as f:
                writer = csv.reader(f)

                for line in writer:
                    my_score = int(line[location])
                    base_score = int(line[location+1])
                    steps = int(line[4])
                    param = line[5] + " " + line[6]

                    if my_score > base_score:
                        n_win[param] += 1
                    elif my_score < base_score:
                        n_loss[param] += 1
                    else:
                        n_tie[param] += 1

                    n_steps[param] += steps

        for param in sorted(n_steps):
            total = n_win[param] + n_tie[param] + n_loss[param]
            print("%3d, %3d, %3d, %3d, %3.2f"
                  % (n_win[param], n_loss[param], n_tie[param], total, n_steps[param]/total),
                  end=", ")
            print(param)
