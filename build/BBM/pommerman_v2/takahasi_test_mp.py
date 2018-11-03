import pommerman
from pommerman import agents

import sys
import argparse
import copy
import array
import time
import os
import numpy as np

from pommerman.constants import Action

from src.agents.master_agent import MasterAgent as MyAgentO
from tt_agent import MyAgent as MyAgentT

from py4j.java_gateway import JavaGateway

from multiprocessing import Pool
import multiprocessing as multi














verbose = False
forwardModelDebug = False

gateway = JavaGateway()
addition_app = gateway.entry_point


def pack_into_buffer(data):
    data.flatten()
    data.flatten().tolist()
    header = array.array('i', list(data.shape))
    body = array.array('d', data.flatten().tolist())
    if sys.byteorder != 'big':
        header.byteswap()
        body.byteswap()
    buffer = bytearray(header.tostring() + body.tostring())
    return buffer


def pack_into_buffer2(data):
    header = array.array('i', [len(data), 1])
    body = array.array('d', data)
    if sys.byteorder != 'big':
        header.byteswap()
        body.byteswap()
    buffer = bytearray(header.tostring() + body.tostring())
    return buffer


def send_env(env, flag):
    flameMat = np.zeros((11,11))
    lifeMat = np.zeros((11,11))
    powerMat = np.zeros((11,11))
    ownerMat = np.zeros((11,11))
    dirMat = np.zeros((11,11))
    info = np.zeros((4,30))

    for bbb in env._bombs:
        x = bbb.position[0]
        y = bbb.position[1]
        print("bbb.position = ", x, y)
        power = bbb.blast_strength
        life = bbb.life
        lifeMat[x][y] = life
        powerMat[x][y] = power
        ownerMat[x][y] = bbb.bomber.agent_id + 10
        if bbb.moving_direction == Action.Stop :
            dirMat[x][y] = 0
        if bbb.moving_direction == Action.Up :
            dirMat[x][y] = 1
        if bbb.moving_direction == Action.Down :
            dirMat[x][y] = 2
        if bbb.moving_direction == Action.Left :
            dirMat[x][y] = 3
        if bbb.moving_direction == Action.Right :
            dirMat[x][y] = 4


    for bbb in env._flames:
        x = bbb.position[0]
        y = bbb.position[1]
        life = bbb._life
        flameMat[x][y] = life + 1

    for aaa in env._agents:
        print(aaa)
        c = aaa._character
        id = c.agent_id
        alive = c.is_alive
        x = c.position[0]
        y = c.position[1]
        ammo = c.ammo
        power = c.blast_strength
        kick = c.can_kick
        info[id][0] = alive
        info[id][1] = x
        info[id][2] = y
        info[id][3] = ammo
        info[id][4] = power
        info[id][5] = kick

    b1 = pack_into_buffer(env._board)
    b2 = pack_into_buffer(lifeMat)
    b3 = pack_into_buffer(powerMat)
    b4 = pack_into_buffer(ownerMat)
    b5 = pack_into_buffer(dirMat)
    b6 = pack_into_buffer(flameMat)
    b7 = pack_into_buffer(info)
    addition_app.check_env(flag, b1, b2, b3, b4, b5, b6, b7)


def send_action(actions):
    b1 = pack_into_buffer2(actions)
    addition_app.check_actions(b1)







def battle(episode):

    pid = os.getpid()
    print(episode, ", ", pid)



    res = addition_app.start_game(pid)
    print(res)



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
    done = False
    step = 0
    while not done:
        step += 1
        actions = env.act(state)
        state, reward, done, info = env.step(actions)

    # rewards.append(reward)
    print('Episode {} finished'.format(episode), reward)



    res = addition_app.finish_game(pid, reward[0], reward[1], reward[2], reward[3])
    print(res)






def main(render=False, interactive=False):
    pool = Pool(processes=1)
    pool.map(battle, range(10000))
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
