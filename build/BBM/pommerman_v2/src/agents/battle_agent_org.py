from .base_agent import MyBaseAgent
from pommerman import constants
from pommerman import characters
from pommerman import utility
import numpy as np
import random
from copy import deepcopy


verbose = False


class BattleAgent(MyBaseAgent):

    """
    This agent should be used when there are no more Woods and Powerups even in the flames.
    Just focus on killin enemies, while surviving.
    """
    
    def act(self, obs, action_space, info):

        #
        # Definitions
        #

        self._search_range = 10

        board = obs['board']
        my_position = obs["position"]  # tuple([x,y]): my position
        my_ammo = obs['ammo']  # int: the number of bombs I have
        my_blast_strength = obs['blast_strength']
        my_enemies = [constants.Item(e) for e in obs['enemies']]
        my_teammate = obs["teammate"]
        my_kick = obs["can_kick"]  # whether I can kick

        print("my position", my_position, end="\t")
        
        #
        # Understand current situation
        #

        # List of the set of survivable time-positions at each time
        # and preceding positions
        survivable_no_move, prev_no_move \
            = self._search_time_expanded_network(info["list_boards_no_move"],
                                                 my_position)

        # Items that can be reached in a survivable manner
        reachable_items_no_move, reached_no_move, next_to_items_no_move \
            = self._find_reachable_items(info["list_boards_no_move"],
                                         my_position,
                                         survivable_no_move)

        # Simulation assuming enemies move

        for enemy_mobility in range(3, -1, -1):
            # List of boards simulated
            list_boards, _ = self._board_sequence(board,
                                                  info["curr_bombs"],
                                                  info["curr_flames"],
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
                                           info["curr_bombs"],
                                           info["curr_flames"])

        survivable_actions = [a for a in is_survivable if is_survivable[a]]
        
        # if verbose:
        if True:
            print("survivable actions are", survivable_actions)

        # Positions where we kick a bomb if we move to
        if my_kick:
            kickable = self._kickable_positions(obs, info["moving_direction"])
        else:
            kickable = set()

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

            print("Must die")
            return None

        elif len(survivable_actions) == 1:

            # move to the position if it is the only survivable position
            action = survivable_actions[0]
            print("The only survivable action", action)
            return action.value

        # TODO : shoud check the survivability of all agents in one method

        # Place a bomb if
        # - it does not significantly reduce my survivability
        # - it can reduce the survivability of enemies

        consider_bomb = True
        if survivable_with_bomb is None:
            consider_bomb = False
        elif any([len(s) <= 2 for s in survivable_with_bomb[1:]]):
            # if not sufficiently survivable all the time after bomb, do not bomb
            consider_bomb = False

        if consider_bomb:
            # place bomb if can reach fog/enemy
            if self._can_break(info["list_boards_no_move"][-1],
                               my_position,
                               my_blast_strength,
                               [constants.Item.Fog] + my_enemies):
                print("Bomb to break fog/enemy", constants.Action.Bomb)
                print(info["list_boards_no_move"][-1])
                return constants.Action.Bomb.value

            for enemy in my_enemies:
                # check if the enemy is reachable
                if len(reachable_items_no_move[enemy]) == 0:
                    continue

                # can reach the enemy at enemy_position in enemy_time step
                enemy_time = reachable_items_no_move[enemy][0][0]
                enemy_position = reachable_items_no_move[enemy][0][1:3]

                # check if placing a bomb can reduce the survivability
                # of the enemy
                survivable_before, _ = self._search_time_expanded_network(info["list_boards_no_move"],
                                                                          enemy_position)

                board_with_bomb = deepcopy(obs["board"])
                curr_bombs_with_bomb = deepcopy(info["curr_bombs"])
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
                                           info["curr_flames"],
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
                    print("before", good_before)
                    print("after ", good_after)
                    print([len(s) for s in survivable])
                    print([len(s) for s in survivable_with_bomb])
                    return constants.Action.Bomb.value
            
                """
                # find direction towards enemy
                positions = set([x[1:3] for x in next_to_items_no_move[enemy]])
                for t in range(enemy_time, 1, -1):
                    _positions = set()
                    for position in positions:
                        _positions = _positions.union(prev_no_move[t][position])
                    positions = _positions.copy()

                if enemy_time <= my_blast_strength:
                    #if True:
                    positions.add(my_position)
                    positions_after_bomb = set(survivable[1]).difference(positions)
                    if positions_after_bomb:
                        print("Bomb to kill an enemy", enemy, constants.Action.Bomb)
                        return constants.Action.Bomb.value
                """

            # if I can kick, consider placing a bomb to kick
            if my_kick and my_position in survivable_with_bomb[3]:
                # consdier a sequence of actions: place bomb -> move (action) -> move back (kick)
                for action in [constants.Action.Up,
                               constants.Action.Down,
                               constants.Action.Left,
                               constants.Action.Right]:
                    if not is_survivable[action]:
                        continue

                    if action == constants.Action.Up:
                        # kick direction is down
                        dx = 1
                        dy = 0
                    elif action == constants.Action.Down:
                        # kick direction is up
                        dx = -1
                        dy = 0 
                    elif action == constants.Action.Left:
                        # kick direction is right
                        dx = 0
                        dy = 1
                    elif action == constants.Action.Right:
                        # kick direction is left
                        dx = 0
                        dy = -1
                    else:
                        raise ValueError()

                    _next_position = (my_position[0] + dx, my_position[1] + dy)
                    if not self._on_board(_next_position):
                        continue
                    else:
                        next_position = _next_position

                    # Find where the bomb stops if kicked
                    for t in range(int(obs["bomb_life"][my_position]) - 2):
                        if not utility.position_is_passage(board, next_position):
                            break
                        _next_position = (next_position[0] + dx, next_position[1] + dy)
                        if not self._on_board(_next_position):
                            break
                        else:
                            next_position = _next_position
                        if utility.position_is_fog(board, next_position):
                            print("Bomb to kick into fog", action)
                            return constants.Action.Bomb.value
                        elif utility.position_is_enemy(list_boards[t+2], next_position, my_enemies):
                            print("Bomb to kick towards enemy", action)
                            return constants.Action.Bomb.value

                """
                x0, y0 = my_position
                positions_against = [(2*x0-x, 2*y0-y) for (x, y) in positions]
                positions_after_bomb = set(survivable[1]).intersection(positions_against)

                if positions_after_bomb:
                    print("Bomb to kick", enemy, constants.Action.Bomb)
                    return constants.Action.Bomb.value
                """

        # kick 
        if len(kickable) > 0:

            while kickable:
                # then consider what happens if I kick a bomb
                next_position = kickable.pop()

                # do not kick a bomb if it will break enemies
                if info["moving_direction"][next_position] is None:
                    # if it is a static bomb
                    if self._can_break(info["list_boards_no_move"][0],
                                       next_position,
                                       my_blast_strength,
                                       my_enemies):
                        continue
                
                my_action = self._get_direction(my_position, next_position)
                list_boards_with_kick, next_position \
                    = self._board_sequence(obs["board"],
                                           info["curr_bombs"],
                                           info["curr_flames"],
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

        # if on a bomb, consider where to kick in the following step        
        if obs["bomb_life"][my_position] > 0:
            # For each survivable move in the next step,
            # check what happens if we kick in the following step.
            # If the bomb is kicked into a fog, plan to kick.
            # If the bomb is kicked toward an enemy, plan to kick.
            # Otherwise, do not plan to kick.
            for action in [constants.Action.Up,
                           constants.Action.Down,
                           constants.Action.Left,
                           constants.Action.Right]:
                if not is_survivable[action]:
                    continue

                if action == constants.Action.Up:
                    # kick direction is down
                    dx = 1
                    dy = 0
                elif action == constants.Action.Down:
                    # kick direction is up
                    dx = -1
                    dy = 0 
                elif action == constants.Action.Left:
                    # kick direction is right
                    dx = 0
                    dy = 1
                elif action == constants.Action.Right:
                    # kick direction is left
                    dx = 0
                    dy = -1
                else:
                    raise ValueError()

                _next_position = (my_position[0] + dx, my_position[1] + dy)
                if not self._on_board(_next_position):
                    continue
                else:
                    next_position = _next_position

                # Find where the bomb stops if kicked
                for t in range(int(obs["bomb_life"][my_position]) - 1):
                    if not utility.position_is_passage(board, next_position):
                        break
                    _next_position = (next_position[0] + dx, next_position[1] + dy)
                    if not self._on_board(_next_position):
                        break
                    else:
                        next_position = _next_position
                if utility.position_is_fog(board, next_position):
                    print("Moving to kick into fog", action)
                    return action.value
                elif utility.position_is_enemy(list_boards[t+2], next_position, my_enemies):
                    print("Moving to kick towards enemy", action)

        # Move towards an enemy
        good_time_positions = set()
        for enemy in my_enemies:
            good_time_positions = good_time_positions.union(next_to_items[enemy])

        if len(good_time_positions) > 0:
            action = self._find_distance_minimizer(my_position,
                                                   good_time_positions,
                                                   prev,
                                                   is_survivable)

            if action is not None:
                print("Moving toward enemy", action)
                return action.value

        #
        # Random action
        #
        action = random.choice(survivable_actions)
        print("Random action", action)
        return action.value
        
