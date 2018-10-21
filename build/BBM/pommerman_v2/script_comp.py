import os
import subprocess
import argparse
import random
import numpy as np
import time
from datetime import datetime
from collections import defaultdict
from run_comp import main
import subprocess


if __name__ == "__main__":

    parser = argparse.ArgumentParser(description="Second competition")
    parser.add_argument("comp_number",
                        help="Competion number")
    parser.add_argument("--clean",
                        default=False,
                        action="store_true",
                        help="Remove all docker images and constainers as well as log directories")
    parser.add_argument("--docker",
                        default=False,
                        action="store_true",
                        help="Make docker images")
    parser.add_argument("--run",
                        default=False,
                        action="store_true",
                        help="Run competitions")
    parser.add_argument("--analyze",
                        default=False,
                        action="store_true",
                        help="Analyze results")
    parser.add_argument("--render",
                        default=False,
                        action="store_true",
                        help="Render")
    args = parser.parse_args()

    comp_number = int(args.comp_number)    
    
    directory = "internal_competition_" + str(comp_number)
    docker_dir = "comp" + str(comp_number)
    record_json_dir = "log_comp" + str(comp_number) + "_json"
    record_reward_dir = "log_comp" + str(comp_number) + "_reward"

    if args.clean:

        commands = [
            # stop all containers
            "docker stop $(docker ps -q)",        

            # remove all containers (not needed)
            # "docker rm $(docker ps -aq)",

            # remove json dir
            "rm -rf " + record_json_dir,

            # remove reward dir
            "rm -rf " + record_reward_dir,
        ]
        
        for command in commands:
            print(command)
            subprocess.call(command, shell=True)

        # remove all images
        p = subprocess.Popen("docker images",
                             shell=True, stdout=subprocess.PIPE)
        stdout, _ = p.communicate()
        for line in stdout.decode("utf-8").split("\n"):
            if line.startswith(docker_dir) or line.startswith("<none>"):
                name = line[40:52]
            else:
                continue
            command = "docker rmi " + name
            print(command)
            subprocess.call(command, shell=True)

    if args.docker:
        # Build docker images from Dockerfiles
        ls = os.listdir(directory)
        for filename in ls:
            if "Dockerfile" not in filename:
                continue
            name = filename.split("-")[1]
            command = "docker build"
            command += " -t " + docker_dir + "/" + name
            command += " -f " + os.path.join(directory, filename) + " ."
            print(command)
            subprocess.call(command, shell=True)

    if args.run:

        if not os.path.exists(record_json_dir):
            os.mkdir(record_json_dir)
        if not os.path.exists(record_reward_dir):
            os.mkdir(record_reward_dir)
            
        # List of the names of all agents to compete against each other
        all_agent_names = list()
        p = subprocess.Popen("docker images",
                             shell=True, stdout=subprocess.PIPE)
        stdout, _ = p.communicate()
        for line in stdout.decode("utf-8").split("\n"):
            if not line.startswith(docker_dir):
                continue
            name = line.split(" ")[0].split("/")[1]
            all_agent_names.append(name)

        count_win = np.zeros(len(all_agent_names))
        count = np.zeros(len(all_agent_names))
        total_count = 0

        start_time = time.time()
        while True:

            #
            # Select 4 agents to play
            #

            # Choose agents who have not played yet
            if min(count) == 0:
                is_zero = (count==0)
                agent_names = np.random.choice(all_agent_names,
                                               min([4, sum(is_zero)]),
                                               p=is_zero/sum(is_zero),
                                               replace=False)
                agent_names = agent_names.tolist()
            else:
                agent_names = []

            # Choose agents who have already played
            if len(agent_names) < 4:
                non_zero_agents = [i for i in range(len(count)) if count[i] > 0]
                # UCB_i = mu_i + sqrt(2 log(N) / n_i)
                UCB = [count_win[i] / count[i]
                       + np.sqrt(2 * np.log(total_count) / count[i])
                       for i in non_zero_agents]
                while len(agent_names) < 4:
                    maximizer = np.nanargmax(UCB)
                    UCB.pop(maximizer)
                    agent = non_zero_agents.pop(maximizer)
                    agent_names.append(all_agent_names[agent])

            # Shuffle locations
            np.random.shuffle(agent_names)

            # Update count
            for name in agent_names:
                idx = all_agent_names.index(name)
                count[idx] += 1
            total_count += 4

            #
            # Prepare log storage
            #

            stamp = datetime.now().strftime("%y%m%d-%H%M%S")
            
            # Specify where to log json files
            """
            _record_json_dir = os.path.join(record_json_dir, "json")
            for agent in agent_names:
                _record_json_dir += "_" + agent
            _record_json_dir += "_" + stamp
            if not os.path.exists(_record_json_dir):
                os.mkdir(_record_json_dir)
            """
            _record_json_dir = None

            # Specify where to log reward files
            filename = "reward"
            for agent in agent_names:
                filename += "_" + agent
            filename += "_" + stamp + ".npy"
            filename = os.path.join(record_reward_dir, filename)

            # Run competition
            reward = main(agent_names,
                          competition="comp" + str(comp_number),
                          record_json_dir=_record_json_dir,
                          render=args.render)
            np.save(filename, reward)
            #reward = np.random.randint(2, size=4)

            for i in range(4):
                if reward[i] == 1:
                    agent = all_agent_names.index(agent_names[i])
                    count_win[agent] += 1
            
            # time in seconds
            if time.time() - start_time > 60 * 60 * 24: # 24 hours
                break

    if args.analyze:
        count_win = defaultdict(int)
        count_loss = defaultdict(int)
        count_tie = defaultdict(int)
        ls = os.listdir(record_reward_dir)
        for filename in ls:
            agent_names = filename.split("_")[1:5]
            rewards = np.load(os.path.join(record_reward_dir, filename))
            if all(rewards == -1):
                for agent in agent_names:
                    count_tie[agent] += 1
                continue
            for agent, reward in zip(agent_names, rewards):
                if reward == 1:
                    count_win[agent] += 1
                elif reward == -1:
                    count_loss[agent] += 1
                else:
                    raise ValueError()
        agent_names = set(count_win).union(count_loss)
        def f(x):
            return count_win[x]
        agent_names = sorted(agent_names, key=f, reverse=True)
        print("     agent | win | tie | loss | win rate")
        print("----------------------------------------")
        for agent in agent_names:
            print("%10s | %3d | %3d | %4d | %1.3f"
                  % (agent,
                     count_win[agent],
                     count_tie[agent],
                     count_loss[agent],
                     count_win[agent] / (count_win[agent] + count_tie[agent] + count_loss[agent])))
