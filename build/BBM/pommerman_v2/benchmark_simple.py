import os
import sys
import argparse
import random
import numpy as np
from run_comp import main
import pommerman
#from src.agents.agents import TimeExpandedAgent as MyAgent
#from src.agents.agents_osogami_team import TimeExpandedAgent as MyAgent
from src.agents.master_agent import MasterAgent as MyAgent


def main(config, location, render=False, interactive=False, checkpoint=[0,0]):

    if config in ["ffa", "FFA", "PommeFFACompetition-v0"]:
        config = 'PommeFFACompetition-v0'
    elif config in ["team", "Team", "PommeTeamCompetition-v0"]:
        config = 'PommeTeamCompetition-v0'
    else:
        raise ValueError()

    # List of four agents
    agent_list = [
        pommerman.agents.SimpleAgent(),
        pommerman.agents.SimpleAgent(),
        pommerman.agents.SimpleAgent(),
        pommerman.agents.SimpleAgent()
    ]

    agent_list[location] = MyAgent()
    if config == 'PommeTeamCompetition-v0':
        agent_list[location+2] = MyAgent()

    # Environment
    env = pommerman.make(config, agent_list)

    # Run
    state = env.reset()
    done = False
    step = 0
    while not done:
        step += 1
        print("STEP", step)
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

    parser = argparse.ArgumentParser(description="Benchmark against simple agent")
    parser.add_argument("--run",
                        nargs=3,
                        help="Run competitions")
    parser.add_argument("--analyze",
                        nargs=1,
                        default="team",
                        help="Analyze results")
    parser.add_argument("--logdir",
                        nargs=1,
                        default=0,
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

    if args.logdir:
        logdir = args.logdir[0]
    else:
        logdir = "log_benchmark"
    if not os.path.exists(logdir):
        os.mkdir(logdir)

    print("working in", logdir, args.logdir)

    if args.run:
        
        seed = int(args.run[0])
        location = int(args.run[1])

        filename_header = str(seed) + "_" + str(location)
        ls = os.listdir(logdir)
        if any([f.startswith(filename_header) for f in ls]):
            print("Exists", filename_header)
            exit()
        
        config = args.run[2]
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
        rewards, step = main(config,
                             location,
                             render=(args.render or args.interactive),
                             interactive=args.interactive,
                             checkpoint=[ren_checkpoint, int_checkpoint])
        print(rewards)

        filename = filename_header + "_" + str(step) + ".npy"
        filename = os.path.join(logdir, filename)
        np.save(filename, rewards)

    elif args.analyze:

        CHECK = True
        
        mode = args.analyze[0]

        if CHECK:
            if mode == "team":
                check = np.full((200,2), False)
            elif mode == "ffa":
                check = np.full((500,4), False)
        
        ls = os.listdir(logdir)
        total_reward = 0
        total_step = 0
        count = 0
        n_win = 0
        n_loss = 0
        n_tie = 0
        n_over500 = 0
        for filename in ls:
            rewards = np.load(os.path.join(logdir, filename))
            seed, location, step = filename.split(".")[0].split("_")
            if CHECK:
                check[(int(seed),int(location))] = True
            my_reward = rewards[int(location)]
            total_reward += my_reward
            total_step += int(step)
            count += 1
            if my_reward == 1:
                n_win += 1
            elif max(rewards) == 1:
                n_loss += 1
                print(filename)
            else:
                n_tie += 1
            if int(step) > 500:
                n_over500 += 1
        if mode == "team":
            print("[NIPS] ", end="")
        elif mode == "ffa":
            print("[FFA] ", end="")
        print("count (total/win/loss/tie | tie after 500 steps): %d/%d/%d/%d | %d" % (count, n_win, n_loss, n_tie, n_over500))
        print("average steps:", total_step / count)
        print("average reward:", total_reward / count)
        if CHECK:
            print("missing", np.where(check==False))
        
