from .base_agent import MyBaseAgent
from pommerman import constants
from pommerman import characters
from pommerman import utility
import numpy as np
import random
from copy import deepcopy


verbose = False


class GenericAgent(MyBaseAgent):
    
    def __init__(self):
        self._search_range = 10
        super().__init__()

    def act(self, obs, action_space, info):

        #
        # Definitions
        #

        enemy_mobility = 4

        board = obs['board']
        my_position = obs["position"]  # tuple([x,y]): my position
        my_ammo = obs['ammo']  # int: the number of bombs I have
        my_blast_strength = obs['blast_strength']
        my_enemies = [constants.Item(e) for e in obs['enemies']]
        my_teammate = obs["teammate"]
        my_kick = obs["can_kick"]  # whether I can kick

        print("my position", my_position, "ammo", my_ammo, "blast", my_blast_strength, "kick", my_kick, end="\t")
        
        #
        # Understand current situation
        #

        # fraction of blocked node in the survival trees of enemies
        _list_boards = deepcopy(info["list_boards_no_move"])
        if obs["bomb_blast_strength"][my_position]:
            for b in _list_boards:
                if utility.position_is_agent(b, my_position):
                    b[my_position] = constants.Item.Bomb.value
        else:
            for b in _list_boards:
                if utility.position_is_agent(b, my_position):
                    b[my_position] = constants.Item.Passage.value

        total_frac_blocked, n_survivable_nodes \
            = self._get_frac_blocked(_list_boards, my_enemies, board, obs["bomb_life"])
        bomb_target_enemy = (total_frac_blocked > 0)
        
        # where to place bombs to break wood
        bomb_target_wood, n_breakable \
            = self._get_bomb_target(info["list_boards_no_move"][-1],
                                    my_position,
                                    my_blast_strength,
                                    constants.Item.Wood)

        #bomb_target = bomb_target_enemy + bomb_target_wood
        bomb_target = bomb_target_wood
        
        # List of boards simulated
        list_boards, _ = self._board_sequence(board,
                                              info["curr_bombs"],
                                              info["curr_flames"],
                                              self._search_range,
                                              my_position,
                                              enemy_mobility=enemy_mobility)

        # List of the set of survivable time-positions at each time
        # and preceding positions
        survivable, prev, succ, _ \
            = self._search_time_expanded_network(list_boards,
                                                 my_position)

        # Survivable actions
        is_survivable, survivable_with_bomb \
            = self._get_survivable_actions(survivable,
                                           obs,
                                           info["curr_bombs"],
                                           info["curr_flames"],
                                           enemy_mobility=enemy_mobility)

        survivable_actions = [a for a in is_survivable if is_survivable[a]] 

        n_survivable = dict()
        kick_actions = list()
        if my_kick:
            # Positions where we kick a bomb if we move to
            kickable = self._kickable_positions(obs, info["moving_direction"])
            for next_position in kickable:
                # consider what happens if I kick a bomb
                my_action = self._get_direction(my_position, next_position)

                # do not kick into fog
                dx = next_position[0] - my_position[0]
                dy = next_position[1] - my_position[1]
                position = next_position
                is_fog = False
                while self._on_board(position):
                    if utility.position_is_fog(board, position):
                        is_fog = True
                        break
                    position = (position[0] + dx, position[1] + dy)
                if is_fog:
                    continue

                list_boards_with_kick, next_position \
                    = self._board_sequence(obs["board"],
                                           info["curr_bombs"],
                                           info["curr_flames"],
                                           self._search_range,
                                           my_position,
                                           my_action=my_action,
                                           can_kick=True,
                                           enemy_mobility=enemy_mobility)
                #print(list_boards_with_kick)
                survivable_with_kick, prev_kick, succ_kick, _ \
                    = self._search_time_expanded_network(list_boards_with_kick[1:],
                                                         next_position)
                if next_position in survivable_with_kick[0]:
                    survivable_actions.append(my_action)
                    is_survivable[my_action] = True
                    n_survivable[my_action] = [1] + [len(s) for s in survivable_with_kick[1:]]
                    kick_actions.append(my_action)
        else:
            kickable = set()
        
        if len(survivable_actions) == 0:
            return None

        #
        # Items and bomb target that can be reached in a survivable manner
        #
        
        reachable_items, reached, next_to_items \
            = self._find_reachable_items(list_boards,
                                         my_position,
                                         survivable,
                                         bomb_target)

            
        #
        # Evaluate the survivability of each action
        #

        x, y = my_position
        for action in survivable_actions:
            # for each survivable action, check the survivability
            if action == constants.Action.Bomb:
                n_survivable[action] = [len(s) for s in survivable_with_bomb[1:]]
                continue
            
            if action == constants.Action.Up:
                dx = -1
                dy = 0
            elif action == constants.Action.Down:
                dx = 1
                dy = 0 
            elif action == constants.Action.Left:
                dx = 0
                dy = -1
            elif action == constants.Action.Right:
                dx = 0
                dy = 1
            elif action == constants.Action.Stop:
                dx = 0
                dy = 0
            else:
                raise ValueError()
            next_position = (x + dx, y + dy)
            n_survivable[action], _info = self._count_survivable(succ, 1, next_position)

        #if True:
        if verbose:
            print("n_survivable")
            for a in n_survivable:
                print(a, n_survivable[a])

        #
        # Choose the survivable action, if it is the only choice
        #

        if len(survivable_actions) == 1:

            # move to the position if it is the only survivable position
            action = survivable_actions[0]
            print("The only survivable action", action)
            return action.value

        #
        # Bomb if it has dominating survivability
        #

        if is_survivable[constants.Action.Bomb]:
            bomb_is_most_survivable = True
            bomb_sorted = np.array(sorted(n_survivable[constants.Action.Bomb]))
            for action in n_survivable:
                if action == constants.Action.Bomb:
                    continue
                action_sorted = np.array(sorted(n_survivable[action]))
                if any(action_sorted > bomb_sorted):
                    bomb_is_most_survivable = False
                    break
            if bomb_is_most_survivable:
                action = constants.Action.Bomb
                print("Bomb to survive", action)
                return action.value

        #
        # Bomb at a target
        #
            
        consider_bomb = True
        if survivable_with_bomb is None:
            consider_bomb = False
        elif any([len(s) <= 1 for s in survivable_with_bomb[2:]]):
            # if not sufficiently survivable all the time after bomb, do not bomb
            consider_bomb = False
        elif self._might_break_powerup(info["list_boards_no_move"][-1],
                                       my_position,
                                       my_blast_strength,
                                       info["might_powerup"]):
            # if might break an item, do not bomb
            consider_bomb = False

        best_action = None
        max_block = 0
        for action in survivable_actions:
            if action == constants.Action.Stop:
                continue
            next_position = self._get_next_position(my_position, action)
            block = total_frac_blocked[next_position]
            if block > max_block:
                max_block = block
                best_action = action

        if consider_bomb and best_action == constants.Action.Stop:
            print("Place a bomb at a locally optimal position", constants.Action.Bomb)
            return constants.Action.Bomb.value
                
        #
        # Move towards where to bomb
        #

        if best_action not in [None, constants.Action.Stop]:
            print("Move towards better place to bomb", best_action)
            return best_action.value

        #
        # Bomb to break wood
        #
        
        if consider_bomb and bomb_target[my_position]:
            # place bomb if I am at a bomb target
            print("Bomb at a bomb target", constants.Action.Bomb)
            return constants.Action.Bomb.value

        #
        # Move towards good items
        #

        good_items = [constants.Item.ExtraBomb, constants.Item.IncrRange, constants.Item.Kick]
        good_time_positions = set()  # positions with good items
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

        #
        # Move towards where to bomb to break wood
        #

        good_time_positions = reachable_items["target"]
        action = self._find_distance_minimizer(my_position,
                                               good_time_positions,
                                               prev,
                                               is_survivable)
        if action is not None:
            print("Moving toward where to bomb", action)
            return action.value

        #
        # Kick
        #

        for my_action in kick_actions:
            if my_action == constants.Action.Up:
                next_position = (my_position[0] - 1, my_position[1])
            elif my_action == constants.Action.Down:
                next_position = (my_position[0] + 1, my_position[1])
            elif my_action == constants.Action.Right:
                next_position = (my_position[0], my_position[1] + 1)
            elif my_action == constants.Action.Left:
                next_position = (my_position[0], my_position[1] - 1)
            # do not kick a bomb if it will break a wall, enemies
            if info["moving_direction"][next_position] is None:
                print("checking static bomb")
                # if it is a static bomb                    
                if self._can_break(info["list_boards_no_move"][0],
                                   next_position,
                                   my_blast_strength,
                                   [constants.Item.Wood] + my_enemies):
                    continue

            list_boards_with_kick_no_move, _ \
                = self._board_sequence(obs["board"],
                                       info["curr_bombs"],
                                       info["curr_flames"],
                                       self._search_range,
                                       my_position,
                                       my_action=my_action,
                                       can_kick=True,
                                       enemy_mobility=0)

            for enemy in my_enemies:
                rows, cols = np.where(board==enemy.value)
                if len(rows) == 0:
                    continue
                enemy_position = (rows[0], cols[0])
                _survivable, _, _, _ \
                    = self._search_time_expanded_network(list_boards_with_kick_no_move,
                                                         enemy_position)

                n_survivable_nodes_with_kick = sum([len(positions) for positions in _survivable])
                if n_survivable_nodes_with_kick < n_survivable_nodes[enemy]:
                    print("Kicking to reduce the survivability",
                          n_survivable_nodes[enemy], "->", n_survivable_nodes_with_kick,
                          my_action)
                    return my_action.value

        #
        # TODO : move toward might powerups
        #
        
        #
        # Move towards a fog where we have not seen longest
        #

        best_time_position = None
        oldest = 0
        for t, x, y in next_to_items[constants.Item.Fog]:
            neighbors = [(x+dx, y+dy) for dx, dy in [(-1, 0), (1, 0), (0, -1), (0, 1)]]
            age = max([info["since_last_seen"][position] for position in neighbors if self._on_board(position)])
            if age > oldest:
                oldest = age
                best_time_position = (t, x, y)

        if best_time_position is not None:
            action = self._find_distance_minimizer(my_position,
                                                   [best_time_position],
                                                   prev,
                                                   is_survivable)
            if action is not None:
                print("Moving toward oldest fog", action)
                return action.value            

        #
        # Choose most survivable action
        #

        survivable_score = dict()
        for action in n_survivable:
            #survivable_score[action] = sum([-n**(-5) for n in n_survivable[action]])
            survivable_score[action] = sum([n for n in n_survivable[action]])
            if verbose:
                print(action, survivable_score[action], n_survivable[action])                
        best_survivable_score = max(survivable_score.values())

        most_survivable_action = None
        random.shuffle(survivable_actions)
        for action in survivable_actions:
            if survivable_score[action] == best_survivable_score:
                most_survivable_action = action
                break
         
        print("Most survivable action", most_survivable_action)
        return most_survivable_action.value
        
