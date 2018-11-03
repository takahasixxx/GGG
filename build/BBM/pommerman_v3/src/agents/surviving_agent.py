from .base_agent import MyBaseAgent
from pommerman import constants
from pommerman import utility
import random
from copy import deepcopy
import numpy as np


verbose = False


class SurvivingAgent(MyBaseAgent):

    def __init__(self):
        self._search_range = 10
        super().__init__()

    def _get_n_survivable(self, board, bombs, flames, obs, my_position, kickable, enemy_mobility=0):
        # board sequence
        list_boards, _ \
            = self._board_sequence(board,
                                   bombs,
                                   flames,
                                   self._search_range,
                                   my_position,
                                   enemy_mobility=enemy_mobility)
        #for t, b in enumerate(list_boards):
        #    print(t)
        #    print(b)
        
        # List of the set of survivable time-positions at each time
        survivable, _, succ, _ \
            = self._search_time_expanded_network(list_boards,
                                                 my_position)

        # Survivable actions
        is_survivable, survivable_with_bomb \
            = self._get_survivable_actions(survivable,
                                           obs,
                                           bombs,
                                           flames,
                                           enemy_mobility=enemy_mobility)

        # consider what happens if I kick a bomb
        n_survivable = dict()
        for next_position in kickable:
            # Positions where we kick a bomb if we move to
            my_action = self._get_direction(my_position, next_position)

            list_boards_with_kick, next_position \
                = self._board_sequence(board,
                                       bombs,
                                       flames,
                                       self._search_range,
                                       my_position,
                                       my_action=my_action,
                                       can_kick=True,
                                       enemy_mobility=enemy_mobility)
            survivable_with_kick, _, _, _ \
                = self._search_time_expanded_network(list_boards_with_kick[1:],
                                                     next_position)
            if next_position in survivable_with_kick[0]:
                is_survivable[my_action] = True
                n_survivable[my_action] = [1] + [len(s) for s in survivable_with_kick[1:]]

        survivable_actions = [a for a in is_survivable if is_survivable[a]]

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

        return n_survivable

    def act(self, obs, action_space, info):

        #
        # Definitions
        #
        
        board = obs['board']
        my_position = obs["position"]  # tuple([x,y]): my position
        my_kick = obs["can_kick"]  # whether I can kick
        my_enemies = [constants.Item(e) for e in obs['enemies']]
        my_teammate = obs["teammate"]

        kickable, might_kickable \
            = self._kickable_positions(obs, info["moving_direction"],
                                       consider_agents=True)

        #
        # Survivability, when enemy is replaced by a bomb, and no move afterwards
        #

        # replace enemy with bomb
        _bombs = deepcopy(info["curr_bombs"])
        rows, cols = np.where(board > constants.Item.AgentDummy.value)
        for position in zip(rows, cols):
            if board[position] not in my_enemies:
                continue            
            if obs["bomb_blast_strength"][position]:
                # already a bomb
                continue
            bomb = characters.Bomb(characters.Bomber(),  # dummy owner of the bomb
                                   position,
                                   constants.DEFAULT_BOMB_LIFE,
                                   enemy_blast_strength_map[position],
                                   None)
            _bombs.append(bomb)

        n_survivable_bomb = self._get_n_survivable(board,
                                                   _bombs,
                                                   info["curr_flames"],
                                                   obs,
                                                   my_position,
                                                   set.union(kickable, might_kickable),
                                                   enemy_mobility=0)
        print("survivable bomb")
        for a in n_survivable_bomb:
            print(a, n_survivable_bomb[a])

        survivable_actions_bomb = set(n_survivable_bomb)
        
        #
        # Survivability, when enemy moves one position or stay unmoved
        #
        
        n_survivable_move = self._get_n_survivable(board,
                                                   info["curr_bombs"],
                                                   info["curr_flames"],
                                                   obs,
                                                   my_position,
                                                   set.union(kickable, might_kickable),
                                                   enemy_mobility=1)

        # If my move is survivable with bomb but not with move,
        # then my move must be blocked by an enemy.
        # I might be blocked by an enemy with such my move,
        # it will end up in stop and enemy is also stop,
        # so my survivability with such my move should be the
        # same as my survivability with stop when enemy stops

        if constants.Action.Stop in survivable_actions_bomb:
            for action in survivable_actions_bomb:
                if action in [constants.Action.Stop, constants.Action.Bomb]:
                    continue
                if action not in n_survivable_move:
                    n_survivable_move[action] = n_survivable_bomb[constants.Action.Stop]
                
        survivable_actions_move = set(n_survivable_move)
                
        #print("survivable move")
        #for a in n_survivable_move:
        #    print(a, n_survivable_move[a])
            
        # if survivable by not stopping when enemy place a bomb,
        # then do not stop
        if survivable_actions_bomb - {constants.Action.Stop}:
            survivable_actions_bomb -= {constants.Action.Stop}
            survivable_actions_move -= {constants.Action.Stop}
        #if survivable_actions_bomb - {constants.Action.Bomb}:
        #    survivable_actions_bomb -= {constants.Action.Bomb}
        #    survivable_actions_move -= {constants.Action.Bomb}

        survivable_actions = set.intersection(survivable_actions_bomb,
                                              survivable_actions_move)

        #print("survivable", survivable_actions)

        if len(survivable_actions) == 0:
            if survivable_actions_bomb:
                action = self._get_most_survivable_action(n_survivable_bomb)
                print("Most survivable action when enemy place a bomb", action)
                return action.value
            elif survivable_actions_move:
                action = self._get_most_survivable_action(n_survivable_move)
                print("Most survivable action when enemy moves", action)
                return action.value
            else:
                #
                # Survivability with no enemies or teammate
                #

                _board = deepcopy(board)
                agent_positions = np.where(_board > constants.Item.AgentDummy.value)
                _board[agent_positions] = constants.Item.Passage.value
                _board[my_position] = board[my_position]

                _obs = {"position" : obs["position"],
                        "blast_strength" : obs["blast_strength"],
                        "ammo" : obs["ammo"],
                        "bomb_life" : obs["bomb_life"],
                        "board" : _board}

                n_survivable = self._get_n_survivable(_board,
                                                      info["curr_bombs"],
                                                      info["curr_flames"],
                                                      _obs,
                                                      my_position,
                                                      set.union(kickable, might_kickable),
                                                      enemy_mobility=0)

                survivable_actions = list(n_survivable)

                if survivable_actions:
                    action = self._get_most_survivable_action(n_survivable)
                    print("Most survivable action when no enemy", action)
                    return action.value
                else:
                    if obs["ammo"] > 0 and obs["blast_strength"] == 0:
                        action = constants.Action.Bomb
                        print("Suicide", action)
                        return action.value
                    else:
                        all_actions = [constants.Action.Stop,
                                       constants.Action.Up,
                                       constants.Action.Down,
                                       constants.Action.Right,
                                       constants.Action.Left]
                        random.shuffle(all_actions)
                        for action in all_actions:
                            next_position = self._get_next_position(my_position, action)
                            if not self._on_board(next_position):
                                continue
                            if not utility.position_is_wall(board, next_position):
                                continue
                            print("Random action", action)
                            return action.value
                        
        elif len(survivable_actions) == 1:

            action = survivable_actions.pop()
            print("Only survivable action", action)
            return action.value

        else:
            
            n_survivable_min = dict()
            for a in survivable_actions:
                n_survivable_min[a] = min([n_survivable_bomb[a], n_survivable_move[a]])
            action = self._get_most_survivable_action(n_survivable_min)
            print("Most survivable action when no enemy", action)
            return action.value
                

        action = constants.Action.Stop
        print("No action found", action)
        return action.value
