import os
import sys
import random
import array

from pommerman.agents import BaseAgent
from py4j.java_gateway import JavaGateway

verbose = False





class MyAgent(BaseAgent):




    def __init__(self):
        super().__init__()

        self._pid = os.getpid()
        self._me = -1
        self._caller_id = random.randint(1000000, 10000000)
        print("MyAgent.__init__, pid={}, caller_id={}, me={}".format(self._pid, self._caller_id, self._me))
        gateway = JavaGateway()
        self._addition_app = gateway.entry_point




    def init_agent(self, id, game_type):
        super().init_agent(id, game_type)

        print("MyAgent.init_agent, pid={}, caller_id={}, me={}".format(self._pid, self._caller_id, self._me))
        self._addition_app.init_agent(self._pid, self._caller_id, self._me)




    def episode_end(self, reward):
        print("MyAgent.episode_end, pid={}, caller_id={}, me={}".format(self._pid, self._caller_id, self._me))
        self._addition_app.episode_end(self._pid, self._caller_id, self._me, reward)





    def act(self, obs, action_space):

        ##########################################################################################################################################
        ##########################################################################################################################################
        ##########################################################################################################################################
        ##########################################################################################################################################

        print("MyAgent.act, pid={}, caller_id={}, me={}".format(self._pid, self._caller_id, self._me))


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


        # pick up all data from obs.
        position = obs['position']
        ammo = (int)(obs['ammo'])
        blast_strength = (int)(obs['blast_strength'])
        can_kick = (bool)(obs['can_kick'])

        board = obs['board']
        bomb_blast_strength = obs['bomb_blast_strength']
        bomb_life = obs['bomb_life']

        alive = obs['alive']
        enemies = obs['enemies']
        teammate = obs['teammate']



        # reshape array, list, or other non-primitive objects into byte array objects to send it to Java.
        x = (int)(position[0])
        y = (int)(position[1])

        self._me = (int)(board[x][y])

        board_buffer = pack_into_buffer(board)
        bomb_blast_strength_buffer = pack_into_buffer(bomb_blast_strength)
        bomb_life_buffer = pack_into_buffer(bomb_life)

        alive_buffer = pack_into_buffer2(alive)

        enemies_list = []
        for enemy in enemies:
            enemies_list.append(enemy.value)
        enemies_list_buffer = pack_into_buffer2(enemies_list)


        # call Java function.
        action = self._addition_app.act(self._pid, self._caller_id, self._me, x, y, ammo, blast_strength, can_kick, board_buffer, bomb_blast_strength_buffer, bomb_life_buffer, alive_buffer, enemies_list_buffer)
        return action

        ##########################################################################################################################################
        ##########################################################################################################################################
        ##########################################################################################################################################
        ##########################################################################################################################################