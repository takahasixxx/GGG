from .base_agent import MyBaseAgent
from pommerman import constants
from pommerman import utility
from pommerman import characters
import random
import numpy as np
from copy import deepcopy
from collections import defaultdict


verbose = False


class SurvivingAgent(MyBaseAgent):

    def __init__(self):
        self._search_range = 10
        super().__init__()

    def _get_survivable_with_enemy(self, list_boards, my_position, enemy_position):

        # List of the set of survivable time-positions at each time
        # and preceding positions
        my_survivable, my_prev, my_succ, my_subtree \
            = self._search_time_expanded_network(list_boards,
                                                 my_position)

        if not enemy_position:
            return my_survivable, my_survivable


        enemy_survivable = dict()
        # survivable positions for enemy
        for enemy in enemy_position:
            print("position", enemy_position[enemy])
            enemy_survivable[enemy], _, _, _ \
                = self._search_time_expanded_network(list_boards,
                                                     enemy_position[enemy])

        my_survivable_with_enemy = deepcopy(my_survivable)
        for t in range(self._search_range + 1):
            for enemy in enemy_survivable:
                my_survivable_with_enemy[t] \
                    = my_survivable_with_enemy[t] - enemy_survivable[enemy][t]
            if not my_survivable_with_enemy[t]:
                my_survivable_with_enemy[t+1:] = [set()] * len(my_survivable_with_enemy[t+1:])
                break

        return my_survivable, my_prev, my_succ, my_survivable_with_enemy

        
    def act(self, obs, action_space, info):

        #
        # Definitions
        #
        
        board = obs['board']
        my_position = obs["position"]  # tuple([x,y]): my position
        my_kick = obs["can_kick"]  # whether I can kick
        my_enemies = [constants.Item(e) for e in obs['enemies']]
        my_teammate = obs["teammate"]
        my_ammo = obs['ammo']  # int: the number of bombs I have
        my_blast_strength = obs['blast_strength']
        
        enemy_position = dict()
        for enemy in my_enemies:
            positions = np.argwhere(board==enemy.value)
            if len(positions) == 0:
                continue
            enemy_position[enemy] = tuple(positions[0])

        survivable_steps = defaultdict(int)
            
        #
        # survivable tree in standard case
        #
        
        list_boards_no_kick = deepcopy(info["list_boards_no_move"])

        # remove myself
        if obs["bomb_blast_strength"][my_position]:
            for b in list_boards_no_kick:
                if utility.position_is_agent(b, my_position):
                    b[my_position] = constants.Item.Bomb.value
        else:
            for b in list_boards_no_kick:
                if utility.position_is_agent(b, my_position):
                    b[my_position] = constants.Item.Passage.value

        my_survivable, my_prev, my_succ, my_survivable_with_enemy \
            = self._get_survivable_with_enemy(list_boards_no_kick, my_position, enemy_position)

        life = defaultdict(int)
        for t in range(self._search_range, 0, -1):
            for position in my_survivable_with_enemy[t]:
                if not life[(t,)+position]:
                    life[(t,)+position] = t
                for prev_position in my_prev[t][position]:
                    life[(t-1,)+prev_position] = max([life[(t-1,)+prev_position],
                                                      life[(t,)+position]])

        for next_position in my_survivable[1]:
            my_action = self._get_direction(my_position, next_position)
            survivable_steps[my_action] = life[(1,)+next_position]

        #
        # survivable tree if I lay bomb
        #

        if all([obs["ammo"] > 0, obs["bomb_life"][my_position] == 0]):
            # if I can lay a bomb

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

            my_survivable_with_bomb, my_prev_with_bomb, my_succ_with_bomb, my_survivable_with_bomb_enemy \
                = self._get_survivable_with_enemy(list_boards_with_bomb, my_position, enemy_position)

            life = defaultdict(int)
            for t in range(self._search_range, 0, -1):
                for position in my_survivable_with_bomb_enemy[t]:
                    if not life[(t,)+position]:
                        life[(t,)+position] = t
                    for prev_position in my_prev_with_bomb[t][position]:
                        life[(t-1,)+prev_position] = max([life[(t-1,)+prev_position],
                                                          life[(t,)+position]])

            survivable_steps[constants.Action.Bomb] = life[(1,)+my_position]

        print("survivable steps")
        print(survivable_steps)

        if survivable_steps:
            values = np.array(list(survivable_steps.values()))
            print(values)
            best_index = np.where(values==np.max(values))
            best_actions = np.array(list(survivable_steps.keys()))[best_index]
            
            best_action = random.choice(best_actions)
            print("Most survivable action", best_action)

            return best_action.value

        else:
            print("No actions: stop")
            return constants.Action.Stop.value


        #
        # survivable tree if I kick
        #

        if my_kick:
            # Positions where I kick a bomb if I move to
            kickable, more_kickable = self._kickable_positions(obs, info["moving_direction"])

            for next_position in set.union(*[kickable, more_kickable]):
                # consider what happens if I kick a bomb
                my_action = self._get_direction(my_position, next_position)

                list_boards_with_kick, next_position \
                    = self._board_sequence(obs["board"],
                                           info["curr_bombs"],
                                           info["curr_flames"],
                                           self._search_range,
                                           my_position,
                                           my_action=my_action,
                                           can_kick=True,
                                           enemy_mobility=0)

                my_survivable_with_kick[next_position], my_prev_with_kick[next_position], my_succ_with_bomb[next_position], my_survivable_with_kick_enemy[next_position] \
                    = self._get_survivable_with_enemy(list_boards_with_kick[1:], next_position, enemy_position)

                survivable_with_kick, prev_kick, succ_kick, _ \
                    = self._search_time_expanded_network(list_boards_with_kick[1:],
                                                         next_position)



        # Survivable actions
        is_survivable, survivable_with_bomb \
            = self._get_survivable_actions(my_survivable,
                                           obs,
                                           info["curr_bombs"],
                                           info["curr_flames"],
                                           enemy_mobility=0)

        survivable_actions = [a for a in is_survivable if is_survivable[a]]

        n_survivable = dict()
        kick_actions = list()
        if my_kick:
            # Positions where we kick a bomb if we move to
            kickable = self._kickable_positions(obs, info["moving_direction"])
            for next_position in kickable:
                # consider what happens if I kick a bomb
                my_action = self._get_direction(my_position, next_position)

                list_boards_with_kick, next_position \
                    = self._board_sequence(obs["board"],
                                           info["curr_bombs"],
                                           info["curr_flames"],
                                           self._search_range,
                                           my_position,
                                           my_action=my_action,
                                           can_kick=True,
                                           enemy_mobility=0)
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
            n_survivable[action], _info = self._count_survivable(my_succ, 1, next_position)

        most_survivable_action = None
        if survivable_actions:
            survivable_score = dict()
            for action in n_survivable:
                #survivable_score[action] = sum([-n**(-5) for n in n_survivable[action]])
                survivable_score[action] = sum([n for n in n_survivable[action]])
                if verbose:
                    print(action, survivable_score[action], n_survivable[action])                
            best_survivable_score = max(survivable_score.values())
        
            random.shuffle(survivable_actions)
            for action in survivable_actions:
                if survivable_score[action] == best_survivable_score:
                    most_survivable_action = action
                    break

        if most_survivable_action is not None:
            print("Most survivable action", most_survivable_action)
            return most_survivable_action.value
    
        # kick if possible
        if my_kick:
            kickable = self._kickable_positions(obs, info["moving_direction"])
        else:
            kickable = set()
        print("Kickable", my_kick, kickable)
        while kickable:
            next_position = kickable.pop()
            action = self._get_direction(my_position, next_position)
            print("Must kick to survive", action)
            return action.value

        # move towards a teammate if she is blocking
        for action in [constants.Action.Right,
                       constants.Action.Left,
                       constants.Action.Down,
                       constants.Action.Up]:
            next_position = utility.get_next_position(my_position, action)
            if not self._on_board(next_position):
                continue
            if utility._position_is_item(board, next_position, my_teammate):
                print("Must move to teammate to survive", action)
                return action.value

        # move towards an enemy
        for action in [constants.Action.Right,
                       constants.Action.Left,
                       constants.Action.Down,
                       constants.Action.Up]:
            next_position = utility.get_next_position(my_position, action)
            if not self._on_board(next_position):
                continue
            if utility.position_is_enemy(board, next_position, my_enemies):
                print("Must move to enemy to survive", action)
                return action.value

        # move towards anywhere besides ridid
        for action in [constants.Action.Right,
                       constants.Action.Left,
                       constants.Action.Down,
                       constants.Action.Up]:
            next_position = utility.get_next_position(my_position, action)
            if not self._on_board(next_position):
                continue
            if utility.position_is_rigid(board, next_position):
                continue
            if utility.position_is_wood(board, next_position):
                continue
            if utility.position_is_bomb(info["curr_bombs"], next_position):
                continue
            print("Try moving to survive", action)
            return action.value

        action = constants.Action.Stop
        print("Must die", action)
        return action
