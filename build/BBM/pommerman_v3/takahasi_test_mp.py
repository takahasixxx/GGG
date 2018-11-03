import pommerman
from pommerman import agents

import sys
import argparse
import copy
import array
import time
import os
import numpy as np

from src.agents.master_agent import MasterAgent as MyAgentO
from tt_agent import MyAgent as MyAgentT
from multiprocessing import Pool
from py4j.java_gateway import JavaGateway









verbose = False





def battle(episode):

    print("Episode {} start".format(episode))

    # プロセスIDを取得しておく。
    pid = os.getpid()
    print("pid = {}".format(pid))


    # Javaへの接続を
    gateway = JavaGateway()
    addition_app = gateway.entry_point

    # ゲーム開始を伝える。
    addition_app.start_game(pid)



    # List of four agents
    if False:
        agent_list = [
            MyAgentT(),
            MyAgentT(),
            MyAgentT(),
            MyAgentT(),
        ]

        agent_list = [
            agents.SimpleAgent(),
            agents.SimpleAgent(),
            agents.SimpleAgent(),
            agents.SimpleAgent(),
        ]

    agent_list = [
        MyAgentO(),
        MyAgentT(),
        MyAgentO(),
        MyAgentT(),
    ]


    env = pommerman.make('PommeTeamCompetition-v0', agent_list)
    state = env.reset()
    step = 0
    done = False
    while not done:
        step += 1
        actions = env.act(state)
        state, reward, done, info = env.step(actions)
        print("pid={} step={} actions={}".format(pid, step, actions))

    # 結果を出力する。
    print("Episode {} finished, reward={}".format(episode, reward))


    # ゲーム終了を伝える。
    addition_app.finish_game(pid, reward[0], reward[1], reward[2], reward[3])

    # 不要なハンドルを閉じる。
    env.close()
    addition_app.close()






def main(render=False, interactive=False):
    pool = Pool(processes=32)
    pool.map(battle, range(100000))
    pool.close()





if __name__ == '__main__':

    parser = argparse.ArgumentParser(description="Train an IBM agent")
    parser.add_argument("--render",
                        default=False,
                        action="store_true",
                        help="Whether to render or not. Defaults to False.")
    parser.add_argument("--interactive",
                        default=False,
                        action="store_true",
                        help="Whether to pause after each step.")
    args = parser.parse_args()

    #random.seed(0)
    main(render=args.render, interactive=args.interactive)
