import argparse
import os
import numpy as np
from collections import defaultdict

if __name__ == '__main__':

    parser = argparse.ArgumentParser(description="Analyze results")
    parser.add_argument("directory", help="directory to analyze")
    args = parser.parse_args()

    ls = os.listdir(args.directory)

    result = defaultdict(list)
    for location in range(4):
        this_ls = [f for f in ls if "_"+str(location)+"_" in f]
        rewards = list()
        for filename in this_ls:
            _, location, seed = filename.strip(".npy").split("_")
            location = int(location)
            seed = int(location)
            reward = np.load(os.path.join(args.directory, filename))
            if reward[location] == 1:
                result["win"].append((location, seed))
            elif max(reward) == 1:
                result["loss"].append((location, seed))
            else:
                result["tie"].append((location, seed))
            rewards.append(reward)
        rewards = np.vstack(rewards)
        print("location:", location, "average reward:", np.mean(rewards, axis=0))

    for key in result:
        print(key, len(result[key]))
