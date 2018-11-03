import numpy as np
import random
from copy import deepcopy
from collections import defaultdict
from pommerman import utility
from pommerman import constants
from pommerman import characters
from pommerman.forward_model import ForwardModel
import os
import sys
from .agents_osogami_comp2 import TimeExpandedAgent


verbose = False


class NIPSAgent(TimeExpandedAgent):

    def act(self, obs, action_space):

        #
        # Definitions
        #

        self._search_range = 10

        board = obs['board']
        my_position = obs["position"]  # tuple([x,y]): my position
        my_ammo = obs['ammo']  # int: the number of bombs I have
        my_blast_strength = obs['blast_strength']
        my_enemies = [constants.Item(e) for e in obs['enemies']]

        #
        # Prepare extended observations
        # - bomb moving direction
        # - flame remaining life
        #

        # Summarize information about bombs
        # curr_bombs : list of current bombs
        # moving_direction : array of moving direction of bombs
        curr_bombs, moving_direction, self._prev_bomb_life \
            = self._get_bombs(obs, self._prev_bomb_life)

        # Summarize information about flames
        curr_flames, self._prev_flame_life \
            = self._get_flames(board, self._prev_flame_life, self._prev_bomb_position_strength)

        # bombs to be exploded in the next step
        self._prev_bomb_position_strength = list()
        rows, cols = np.where(obs["bomb_blast_strength"] > 0)
        for position in zip(rows, cols):
            strength = int(obs["bomb_blast_strength"][position])
            self._prev_bomb_position_strength.append((position, strength))

        #
        # Understand current situation
        #

        # Simulation assuming enemies stay unmoved

        # List of simulated boards
        list_boards_no_move, _ \
            = self._board_sequence(board,
                                   curr_bombs,
                                   curr_flames,
                                   self._search_range,
                                   my_position,
                                   enemy_mobility=0)

        # List of the set of survivable time-positions at each time
        # and preceding positions
        survivable_no_move, prev_no_move \
            = self._search_time_expanded_network(list_boards_no_move,
                                                 my_position)

        # Items that can be reached in a survivable manner
        reachable_items_no_move, reached_no_move, next_to_items_no_move \
            = self._find_reachable_items(list_boards_no_move,
                                         my_position,
                                         survivable_no_move)

        # Simulation assuming enemies move

        for enemy_mobility in range(3, -1, -1):
            # List of boards simulated
            list_boards, _ = self._board_sequence(board,
                                                  curr_bombs,
                                                  curr_flames,
                                                  self._search_range,
                                                  my_position,
                                                  enemy_mobility=enemy_mobility)

            # List of the set of survivable time-positions at each time
            # and preceding positions
            survivable, prev = self._search_time_expanded_network(list_boards,
                                                                  my_position)

            if len(survivable[1]) > 0:
                # Gradually reduce the mobility of enemy, so we have at least one survivable action
                break

        # Items that can be reached in a survivable manner
        reachable_items, reached, next_to_items \
            = self._find_reachable_items(list_boards,
                                         my_position,
                                         survivable)

        # Survivable actions
        is_survivable, survivable_with_bomb \
            = self._get_survivable_actions(survivable,
                                           obs,
                                           curr_bombs,
                                           curr_flames)

        survivable_actions = [a for a in is_survivable if is_survivable[a]]
        
        if verbose:
            print("survivable actions are", survivable_actions)

        # Positions where we kick a bomb if we move to
        kickable = self._kickable_positions(obs, moving_direction)

        print()
        for t in range(0):
            print(list_boards[t])
            print(survivable[t])
            for key in prev[t]:
                print(key, prev[t][key])

        #
        # Choose an action
        #

        """
        # This is not effective in the current form
        if len(survivable_actions) > 1:
            # avoid the position if only one position at the following step
            # the number of positions that can be reached from the next position
            next = defaultdict(set)
            next_count = defaultdict(int)
            for position in survivable[1]:
                next[position] = set([p for p in prev[2] if position in prev[2][p]])
                next_count[position] = len(next[position])
            print("next count", next_count)
            if max(next_count.values()) > 1:
                for position in survivable[1]:
                    if next_count[position] == 1:
                        risky_action = self._get_direction(my_position, position)
                        is_survivable[risky_action] = False
                survivable_actions = [a for a in is_survivable if is_survivable[a]]                
        """

        # Do not stay on a bomb if I can
        if all([obs["bomb_life"][my_position] > 0,
                len(survivable_actions) > 1,
                is_survivable[constants.Action.Stop]]):
            is_survivable[constants.Action.Stop] = False
            survivable_actions = [a for a in is_survivable if is_survivable[a]]

        if len(survivable_actions) == 0:

            # must die
            # TODO: might want to do something that can help team mate
            # TODO: kick if possible
            print("Must die", constants.Action.Stop)
            return super().act(obs, action_space)
            # return constants.Action.Stop.value

        elif len(survivable_actions) == 1:

            # move to the position if it is the only survivable position
            action = survivable_actions[0]
            print("The only survivable action", action)
            return action.value

        # Move towards good items
        good_items = [constants.Item.ExtraBomb, constants.Item.IncrRange]
        # TODO : kick may be a good item only if I cannot kick yet
        # TODO : might want to destroy
        good_items.append(constants.Item.Kick)
        # positions with good items
        good_time_positions = set()
        for item in good_items:
            good_time_positions = good_time_positions.union(reachable_items[item])
        if len(good_time_positions) > 0:
            action = self._find_distance_minimizer(my_position,
                                                   good_time_positions,
                                                   prev,
                                                   is_survivable)
            if action is not None:
                print("Moving toward good item", action)
                return action.value

        # TODO : shoud check the survivability of all agents in one method

        # Place a bomb if
        # - it does not significantly reduce my survivability
        # - it can break wood
        # - it can reduce the survivability of enemies
        if is_survivable[constants.Action.Bomb]:
            # if survavable now after bomb, consider bomb
            if all([len(s) > 0 for s in survivable_with_bomb]):
                # if survivable all the time after bomb, consider bomb
                if all([self._can_break_wood(list_boards_no_move[-1],
                                             my_position,
                                             my_blast_strength)]
                       + [not utility.position_is_flames(board, my_position)
                          for board in list_boards_no_move[:10]]):
                    # place bomb if can break wood
                    print("Bomb to break wood", constants.Action.Bomb)
                    return constants.Action.Bomb.value

                for enemy in my_enemies:
                    # check if the enemy is reachable
                    if len(reachable_items_no_move[enemy]) == 0:
                        continue

                    # can reach the enemy at enemy_position in enemy_time step
                    enemy_time = reachable_items_no_move[enemy][0][0]
                    enemy_position = reachable_items_no_move[enemy][0][1:3]

                    # find direction towards enemy
                    positions = set([x[1:3] for x in next_to_items_no_move[enemy]])
                    for t in range(enemy_time, 1, -1):
                        _positions = set()
                        for position in positions:
                            _positions = _positions.union(prev_no_move[t][position])
                        positions = _positions.copy()

                    #if enemy_time <= my_blast_strength:
                    if True:
                        positions.add(my_position)
                        positions_after_bomb = set(survivable[1]).difference(positions)
                        if positions_after_bomb:
                            print("Bomb to kill an enemy", enemy, constants.Action.Bomb)
                            return constants.Action.Bomb.value
                    else:
                        # bomb to kick
                        x0, y0 = my_position
                        positions_against = [(2*x0-x, 2*y0-y) for (x, y) in positions]
                        positions_after_bomb = set(survivable[1]).intersection(positions_against)

                        if positions_after_bomb:
                            print("Bomb to kick", enemy, constants.Action.Bomb)
                            return constants.Action.Bomb.value

                    """
                    # check if placing a bomb can reduce the survivability
                    # of the enemy
                    survivable_before, _ = self._search_time_expanded_network(list_boards_no_move,
                                                                              enemy_position)

                    board_with_bomb = deepcopy(obs["board"])
                    curr_bombs_with_bomb = deepcopy(curr_bombs)
                    # lay a bomb
                    board_with_bomb[my_position] = constants.Item.Bomb.value
                    bomb = characters.Bomb(characters.Bomber(),  # dummy owner of the bomb
                                           my_position,
                                           constants.DEFAULT_BOMB_LIFE,
                                           my_blast_strength,
                                           None)
                    curr_bombs_with_bomb.append(bomb)
                    list_boards_with_bomb, _ \
                        = self._board_sequence(board_with_bomb,
                                               curr_bombs_with_bomb,
                                               curr_flames,
                                               self._search_range,
                                               my_position,
                                               enemy_mobility=0)
                    survivable_after, _ \
                        = self._search_time_expanded_network(list_boards_with_bomb,
                                                             enemy_position)

                    good_before = np.array([len(s) for s in survivable_before])
                    good_after = np.array([len(s) for s in survivable_after])
                    # TODO : what are good criteria?
                    if any(good_after < good_before):
                        # place a bomb if it makes sense
                        print("Bomb to kill an enemy", constants.Action.Bomb)
                        return constants.Action.Bomb.value
                    """

        # Move towards a wood
        if len(next_to_items_no_move[constants.Item.Wood]) > 0:
            # positions next to wood
            good_time_positions = next_to_items_no_move[constants.Item.Wood]
            action = self._find_distance_minimizer(my_position,
                                                   good_time_positions,
                                                   prev,
                                                   is_survivable)
            if action is not None:
                print("Moving toward wood", action)
                return action.value

        # kick whatever I can kick
        # -- tentative, this is generally not a good strategy
        if len(kickable) > 0:

            while kickable:
                # then consider what happens if I kick a bomb
                next_position = kickable.pop()

                # do not kick a bomb if it will break a wall
                if all([moving_direction[next_position] is None,
                        self._can_break_wood(board, next_position, my_blast_strength)]):
                    # if it is a static bomb
                    # do not kick if it is breaking a wall
                    continue

                my_action = self._get_direction(my_position, next_position)
                list_boards_with_kick, next_position \
                    = self._board_sequence(obs["board"],
                                           curr_bombs,
                                           curr_flames,
                                           self._search_range,
                                           my_position,
                                           my_action=my_action,
                                           can_kick=True,
                                           enemy_mobility=3)
                survivable_with_kick, prev_kick \
                    = self._search_time_expanded_network(list_boards_with_kick[1:],
                                                         next_position)
                if next_position in survivable_with_kick[0]:
                    print("Kicking", my_action)
                    return my_action.value

        #
        # back to the track
        #
        distance = defaultdict(int)
        for action in survivable_actions:
            next_position = utility.get_next_position(my_position, action)
            distance[action] = self.distance_to_track(my_position)


        # Move towards an enemy
        good_time_positions = set()
        for enemy in my_enemies:
            good_time_positions = good_time_positions.union(next_to_items[enemy])
        if len(good_time_positions) > 0:
            action = self._find_distance_minimizer(my_position,
                                                   good_time_positions,
                                                   prev,
                                                   is_survivable)

            if obs["bomb_life"][my_position] > 0:
                # if on a bomb, move away
                if action == constants.Action.Down and is_survivable[constants.Action.Up]:
                    action = constants.Action.Up
                elif action == constants.Action.Up and is_survivable[constants.Action.Down]:
                    action = constants.Action.Down
                elif action == constants.Action.Right and is_survivable[constants.Action.Left]:
                    action = constants.Action.Left
                elif action == constants.Action.Left and is_survivable[constants.Action.Right]:
                    action = constants.Action.Right
                else:
                    action = None

            if action is not None:
                print("Moving toward/against enemy", action)
                return action.value


        
        action = super().act(obs, action_space)
        if is_survivable[constants.Action(action)]:
            print("Action from prev. agent", constants.Action(action))
            return action
        else:
            action = random.choice(survivable_actions)
            print("Random action", action)
            return action.value
        
    def distance_to_track(self, position):
        x, y = position
        dx = min([abs(x - 1), abs(x - constants.BOARD_SIZE + 2)])
        dy = min([abs(y - 1), abs(y - constants.BOARD_SIZE + 2)])
        return min([dx, dy])
