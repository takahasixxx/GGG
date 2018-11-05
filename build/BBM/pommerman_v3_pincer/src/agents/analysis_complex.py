import os
import numpy as np

directory = "log_complex"
ls = os.listdir(directory)
for filename in sorted(ls):
    ave_reward = np.load(os.path.join(directory, filename))
    r = - np.sum(ave_reward[1:])
    if r < 2:
        continue
    print("%1.2f" % r, end="\t")
    print(ave_reward, filename)
