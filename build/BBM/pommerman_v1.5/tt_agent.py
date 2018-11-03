import pommerman
from pommerman import agents

import os
import sys
import array

from collections import defaultdict
import queue
import random
import heapq

import numpy as np
from pommerman.agents import SimpleAgent
from pommerman import constants
from pommerman import utility

from py4j.java_gateway import JavaGateway



verbose = False


class SimpleAgentDebugged(SimpleAgent):



    @staticmethod
    def _djikstra(board, my_position, bombs, enemies, depth=None, exclude=None,
                  verbose=False):
        assert (depth is not None)

        if exclude is None:
            exclude = [
                constants.Item.Fog, constants.Item.Rigid, constants.Item.Flames
            ]

        def out_of_range(p1, p2):
            x1, y1 = p1
            x2, y2 = p2
            return depth is not None and abs(y2 - y1) + abs(x2 - x1) > depth

        items = defaultdict(list)
        
        for bomb in bombs:
            if bomb['position'] == my_position:
                items[constants.Item.Bomb].append(my_position)

        dist = {}
        prev = {}

        mx, my = my_position
        for r in range(max(0, mx - depth), min(len(board), mx + depth)):
            for c in range(max(0, my - depth), min(len(board), my + depth)):
                position = (r, c)
                if any([
                        out_of_range(my_position, position),
                        utility.position_in_items(board, position, exclude),
                ]):
                    continue

                if position == my_position:
                    dist[position] = 0
                else:
                    dist[position] = np.inf

                prev[position] = None

                item = constants.Item(board[position])
                items[item].append(position)

        H = []
        heapq.heappush(H, (0, my_position))

        while H:
            min_dist, position = heapq.heappop(H)

            if not utility.position_is_passable(board, position, enemies):
                continue
            
            x, y = position
            for row, col in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
                new_position = (row + x, col + y)
                if new_position not in dist:
                    continue

                if min_dist + 1 < dist[new_position]:
                    dist[new_position] = min_dist + 1
                    prev[new_position] = position
                    heapq.heappush(H, (dist[new_position], new_position))


        return items, dist, prev
    
    


class MyAgent(SimpleAgentDebugged):




    def __init__(self, *args, **kwargs):
        print("__init__")
        self._pid = os.getpid();

        self._me = -1

        super(MyAgent, self).__init__(*args, **kwargs)
        gateway = JavaGateway()
        self._addition_app = gateway.entry_point




    def init_agent(self, id, game_type):
        print("init_agent")
        self._character = self._character(id, game_type)
        self._addition_app.init_agent(self._pid, self._me)




    def episode_end(self, reward):
        print("episode_end")
        self._addition_app.episode_end(self._pid, self._me, reward)





    def act(self, obs, action_space):

        ##########################################################################################################################################
        ##########################################################################################################################################
        ##########################################################################################################################################
        ##########################################################################################################################################

        #print("act")

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
        action = self._addition_app.act(self._pid, self._me, x, y, ammo, blast_strength, can_kick, board_buffer, bomb_blast_strength_buffer, bomb_life_buffer, alive_buffer, enemies_list_buffer)
        if action != -1:
            return action

        ##########################################################################################################################################
        ##########################################################################################################################################
        ##########################################################################################################################################
        ##########################################################################################################################################



        def convert_bombs(bomb_map):
            ret = []
            locations = np.where(bomb_map > 0)
            for r, c in zip(locations[0], locations[1]):
                ret.append({
                    'position': (r, c),
                    'blast_strength': int(bomb_map[(r, c)])
                })
            return ret

        my_position = tuple(obs['position'])
        board = np.array(obs['board'])
        bombs = convert_bombs(np.array(obs['bomb_blast_strength']))
        enemies = [constants.Item(e) for e in obs['enemies']]
        ammo = int(obs['ammo'])
        blast_strength = int(obs['blast_strength'])
        items, dist, prev = self._djikstra(
            board, my_position, bombs, enemies, depth=20, verbose=True)

        # safety score
        safety_score = self._make_safety_score(board, items, bombs, enemies)
        if safety_score[my_position] == -np.inf:
            max_distance = 5
            safe_positions = list()
            maybe_positions = list()
            mx, my = my_position
            for x in range(max([0, mx-max_distance]), min([len(board), mx+max_distance])):
                for y in range(max([0, my-max_distance]), min([len(board), my+max_distance])):
                    if not (x, y) in dist:
                        # unreachable
                        continue
                    if safety_score[(x, y)] > 1:
                        safe_positions.append((x, y))
                    elif safety_score[(x, y)] == 1:
                        maybe_positions.append((x, y))
            nearest = None
            dist_to = max(dist.values())
            for position in safe_positions:
                d = dist[position]
                if d <= dist_to:
                    nearest = position
                    dist_to = d
            if dist_to > max_distance:
                for position in maybe_positions:
                    d = dist[position]
                    if d <= dist_to:
                        nearest = position
                        dist_to = d
            if nearest is not None:
                # found a way to escape
                if prev[nearest] is not None:
                    direction = self._get_direction_towards_position(my_position, nearest, prev)
                    if verbose:
                        print("escaping")
                        #print(safety_score)
                    return direction.value

        """
        # Move if we are in an unsafe place.
        unsafe_directions = self._directions_in_range_of_bomb(
            board, my_position, bombs, dist)
        if unsafe_directions:
            directions = self._find_safe_directions(
                board, my_position, unsafe_directions, bombs, enemies)
            #if True:#verbose:
            #    print("unsafe")
            safest = np.nanargmax([safety_score[utility.get_next_position(my_position, d)] for d in directions])
            return directions[safest]
            # return random.choice(directions).value
        """
        
        # Move towards a good item if there is one within two reachable spaces.
        direction = self._near_good_powerup(my_position, items, dist, prev, 20)
        if direction is not None:
            if safety_score[utility.get_next_position(my_position, direction)] > 1:
                if verbose:
                    print("item")
                return direction.value
            else:
                if verbose:
                    print("item but unsafe")    

        # break whatever you can break
        to_break = self._what_to_break(board, my_position, blast_strength)
        maybe = self._maybe_bomb(ammo, blast_strength, items, dist, my_position)
        if len(to_break) > 0 and maybe:
            if verbose:
                print("to break", to_break)
            return constants.Action.Bomb.value

        """
        # Lay pomme if we are adjacent to an enemy.
        if self._is_adjacent_enemy(items, dist, enemies) and self._maybe_bomb(
                ammo, blast_strength, items, dist, my_position):
            print("adjacent")
            return constants.Action.Bomb.value
        """

        # Move towards an enemy if there is one in exactly three reachable spaces.

        if constants.Item.Wood not in items and constants.Item.ExtraBomb not in items and constants.Item.Kick not in items:
            to_chase = 3
        else:
            to_chase = 3
        direction = self._near_enemy(my_position, items, dist, prev, enemies, to_chase)
        if direction is not None and (self._prev_direction != direction or
                                      random.random() < .5):
            self._prev_direction = direction
            if verbose:
                print("enemy")
            return direction.value

        """
        # Move towards a good item if there is one within two reachable spaces.
        direction = self._near_good_powerup(my_position, items, dist, prev, 2)
        if direction is not None:
            return direction.value
        """

        """
        # Maybe lay a bomb if we are within a space of a wooden wall.
        if self._near_wood(my_position, items, dist, prev, 1):
            if self._maybe_bomb(ammo, blast_strength, items, dist, my_position):
                print("maybe:bomb")
                return constants.Action.Bomb.value
#            else:
#                print("maybe:stop")
#                return constants.Action.Stop.value
        """

        # Move towards a wooden wall if there is one within two reachable spaces and you have a bomb.
        direction = self._near_wood(my_position, items, dist, prev, 2)
        if direction is not None:
            if safety_score[utility.get_next_position(my_position, direction)] > 1:
                if verbose:
                    print("wood")
                return direction.value
            #directions = self._filter_unsafe_directions(board, my_position,
            #                                            [direction], bombs)
            #if directions:
            #    if True:#verbose:
            #        print("wood", directions)
            #    return directions[0].value
            
        # Choose a random but valid direction.
        directions = [
            constants.Action.Stop, constants.Action.Left,
            constants.Action.Right, constants.Action.Up, constants.Action.Down
        ]
        valid_directions = self._filter_invalid_directions(
            board, my_position, directions, enemies)
        directions = self._filter_unsafe_directions(board, my_position,
                                                    valid_directions, bombs)
        directions = self._filter_recently_visited(
            directions, my_position, self._recently_visited_positions)
        if len(directions) > 1:
            directions = [k for k in directions if k != constants.Action.Stop]
        if not len(directions):
            directions = [constants.Action.Stop]

        # Add this position to the recently visited uninteresting positions so we don't return immediately.
        self._recently_visited_positions.append(my_position)
        self._recently_visited_positions = self._recently_visited_positions[
            -self._recently_visited_length:]

        p = [safety_score[utility.get_next_position(my_position, d)] for d in directions]
        if verbose:
            print("random", p, directions)
        p = np.exp(p)
        if len(p) == 1:
            p = [1]
        else:
            p /= np.sum(p)
        try:
            #return np.random.choice(directions, p=p).value
            return random.choices(directions, weights=p).value
        except:
            return random.choice(directions).value

    @classmethod
    def _what_to_break(cls, board, my_position, blast_strength):
        x, y = my_position
        to_break = list()
        # To up
        for dx in range(1, blast_strength):
            if x + dx >= len(board[0]):
                break
            position = (x + dx, y)
            if utility.position_is_rigid(board, position):
                # stop searching this direction
                break
            elif utility.position_is_wood(board, position) or utility.position_is_agent(board, position):
                to_break.append(constants.Item(board[position]))
                break
        # To down
        for dx in range(1, blast_strength):
            if x - dx < 0:
                break
            position = (x - dx, y)
            if utility.position_is_rigid(board, position):
                # stop searching this direction
                break
            elif utility.position_is_wood(board, position) or utility.position_is_agent(board, position):
                to_break.append(constants.Item(board[position]))
                break
        # To right
        for dy in range(1, blast_strength):
            if y + dy >= len(board):
                break
            position = (x, y + dy)
            if utility.position_is_rigid(board, position):
                # stop searching this direction
                break
            elif utility.position_is_wood(board, position) or utility.position_is_agent(board, position):
                to_break.append(constants.Item(board[position]))
                break
        # To left
        for dy in range(1, blast_strength):
            if y - dy < 0:
                break
            position = (x, y - dy)
            if utility.position_is_rigid(board, position):
                # stop searching this direction
                break
            elif utility.position_is_wood(board, position) or utility.position_is_agent(board, position):
                to_break.append(constants.Item(board[position]))
                break
        return to_break

    @classmethod
    def _make_safety_score(cls, board, items, bombs, enemies):
        safety_score = np.ones(board.shape)
        for bomb in bombs:
            x, y = bomb["position"]
            bomb_range = bomb["blast_strength"]
            safety_score[(x, y)] = -np.inf
            for dx in range(1, bomb_range):
                if x + dx >= len(board):
                    break
                position = (x + dx, y)
                if utility.position_is_rigid(board, position):
                    #safety_score[position] = -np.inf
                    break
                safety_score[position] = -np.inf
            for dx in range(1, bomb_range):
                if x - dx < 0:
                    break
                position = (x - dx, y)
                if utility.position_is_rigid(board, position):
                    #safety_score[position] = -np.inf
                    break
                safety_score[position] = -np.inf
            for dy in range(1, bomb_range):
                if y + dy >= len(board[0]):
                    break
                position = (x, y + dy)
                if utility.position_is_rigid(board, position):
                    #safety_score[position] = -np.inf
                    break
                safety_score[position] = -np.inf
            for dy in range(1, bomb_range):
                if y - dy < 0:
                    break
                position = (x, y - dy)
                if utility.position_is_rigid(board, position):
                    #safety_score[position] = -np.inf
                    break
                safety_score[position] = -np.inf

        # wall
        for x in range(len(board)):
            for y in range(len(board)):
                position = (x, y)
                if utility.position_is_wall(board, position):
                    safety_score[position] = -np.inf

        is_safe = (safety_score == 1)

        safety_score[1:, :] += is_safe[:-1, :]
        safety_score[:-1, :] += is_safe[1:, :]
        safety_score[:, 1:] += is_safe[:, :-1]
        safety_score[:, :-1] += is_safe[:, 1:]

        # enemies
        for enemy in enemies:
            for position in items.get(enemy, []):
                x, y = position
                safety_score[position] -= 1
                if x > 0:
                    safety_score[(x - 1, y)] -= 1
                if y > 0:
                    safety_score[(x, y - 1)] -= 1
                if x < len(board) - 1:
                    safety_score[(x + 1, y)] -= 1
                if y < len(board) - 1:
                    safety_score[(x, y + 1)] -= 1

        return safety_score


