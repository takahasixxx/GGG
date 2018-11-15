# (C) Copyright IBM Corp. 2018
from base_agent import MyBaseAgent
from pommerman import constants
from pommerman import utility
from copy import deepcopy
import numpy as np
from collections import defaultdict


verbose = False


class SurvivingAgent(MyBaseAgent):

    def __init__(self, search_range=10):
        self._search_range = search_range
        self._inv_tmp = 10000
        self.random = np.random.RandomState(1)
        super().__init__()        

    def _get_most_survivable_action(self, n_survivable):
        
        survivable_actions = list(n_survivable)
        if len(survivable_actions) == 0:
            return None
        elif len(survivable_actions) == 1:
            return survivable_actions[0]

        survivable_score = dict()
        for action in n_survivable:
            survivable_score[action] = sum([n for n in n_survivable[action]]) + self.random.uniform()
        best_survivable_score = max(survivable_score.values())

        most_survivable_action = None
        for action in survivable_actions:
            if survivable_score[action] == best_survivable_score:
                most_survivable_action = action
                break
         
        return most_survivable_action
        
    def _get_n_survivable(self, board, bombs, flames, obs, my_position, kickable, enemy_mobility=0):
        # board sequence
        list_boards, _ \
            = self._board_sequence(board,
                                   bombs,
                                   flames,
                                   self._search_range,
                                   my_position,
                                   enemy_mobility=enemy_mobility)

        # List of the set of survivable time-positions at each time
        survivable, _, succ, _ \
            = self._search_time_expanded_network(list_boards,
                                                 my_position,
                                                 get_succ=True)

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
        
        board = info['last_seen']
        #board = obs['board']
        my_position = obs["position"]  # tuple([x,y]): my position
        my_ammo = obs['ammo']  # int: the number of bombs I have
        my_blast_strength = obs['blast_strength']
        my_kick = obs["can_kick"]  # whether I can kick
        my_enemies = [constants.Item(e) for e in obs['enemies']]
        my_teammate = obs["teammate"]

        kickable, might_kickable \
            = self._kickable_positions(obs, info["moving_direction"],
                                       consider_agents=True)

        # enemy positions
        enemy_positions = list()
        for enemy in my_enemies:
            rows, cols = np.where(board==enemy.value)
            if len(rows) == 0:
                continue
            enemy_positions.append((rows[0], cols[0]))

        # teammate position
        teammate_position = None
        if my_teammate is not None:
            rows, cols = np.where(board==my_teammate.value)
            if len(rows):
                teammate_position = (rows[0], cols[0])       

        # positions that might be blocked
        if teammate_position is None:
            agent_positions = enemy_positions
        else:
            agent_positions = enemy_positions + [teammate_position]
        might_blocked = self._get_might_blocked(board, my_position, agent_positions, might_kickable)
        
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

        #
        # Survivability, when no enemies
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

        n_survivable_none = self._get_n_survivable(_board,
                                                   info["curr_bombs"],
                                                   info["curr_flames"],
                                                   _obs,
                                                   my_position,
                                                   set.union(kickable, might_kickable),
                                                   enemy_mobility=0)

        #
        # Survivable actions
        #
        
        survivable_actions_bomb = set([a for a in n_survivable_bomb if n_survivable_bomb[a][-1] > 0])      
        survivable_actions_move = set([a for a in n_survivable_move if n_survivable_move[a][-1] > 0])
        survivable_actions_none = set([a for a in n_survivable_none if n_survivable_none[a][-1] > 0])
                
        survivable_actions = set.intersection(survivable_actions_bomb,
                                              survivable_actions_move,
                                              survivable_actions_none)

        # if can survive without possibility of being blocked, then do so
        if not constants.Action.Stop in survivable_actions:
            _survivable_actions = [action for action in survivable_actions
                                   if not might_blocked[action]]
            if len(_survivable_actions):
                survivable_action = _survivable_actions
        

            _survivable_actions_bomb = [action for action in survivable_actions_bomb
                                        if not might_blocked[action]]
            _survivable_actions_move = [action for action in survivable_actions_move
                                        if not might_blocked[action]]
            _survivable_actions_none = [action for action in survivable_actions_none
                                        if not might_blocked[action]]
            if all([len(_survivable_actions_bomb) > 0,
                    len(_survivable_actions_move) > 0,
                    len(_survivable_actions_none) > 0]):
                survivable_action_bomb = _survivable_actions_bomb
                survivable_action_move = _survivable_actions_move
                survivable_action_none = _survivable_actions_none

        #
        # Choose actions
        #

        if len(survivable_actions) == 1:

            action = survivable_actions.pop()
            if verbose:
                print("Only survivable action", action)
            return action.value

        if len(survivable_actions) > 1:
            
            n_survivable_expected = dict()
            for a in survivable_actions:
                if might_blocked[a]:
                    n_survivable_expected[a] \
                        = np.array(n_survivable_bomb[a]) \
                        + np.array(n_survivable_move[constants.Action.Stop]) \
                        + np.array(n_survivable_none[a])
                    n_survivable_expected[a] = n_survivable_expected[a] / 3
                elif a in [constants.Action.Stop, constants.Action.Bomb]:
                    n_survivable_expected[a] = np.array(n_survivable_bomb[a]) + np.array(n_survivable_move[a])
                    n_survivable_expected[a] = n_survivable_expected[a] / 2                    
                else:
                    n_survivable_expected[a] = np.array(n_survivable_bomb[a]) + np.array(n_survivable_none[a])
                    n_survivable_expected[a] = n_survivable_expected[a] / 2
            action = self._get_most_survivable_action(n_survivable_expected)
            if verbose:
                print("Most survivable action", action)
            return action.value

        # no survivable actions for all cases
        survivable_actions = set(list(n_survivable_bomb.keys())
                                 + list(n_survivable_move.keys())
                                 + list(n_survivable_none.keys()))
        
        if len(survivable_actions) == 1:

            action = survivable_actions.pop()
            if verbose:
                print("Only might survivable action", action)
            return action.value

        if len(survivable_actions) > 1:
            
            for a in set.union(survivable_actions, {constants.Action.Stop}):
                if a not in n_survivable_bomb:
                    n_survivable_bomb[a] = np.zeros(self._search_range)
                if a not in n_survivable_move:
                    n_survivable_move[a] = np.zeros(self._search_range)
                if a not in n_survivable_none:
                    n_survivable_none[a] = np.zeros(self._search_range)

            n_survivable_expected = dict()
            for a in survivable_actions:
                if might_blocked[a]:
                    n_survivable_expected[a] \
                        = np.array(n_survivable_bomb[a]) \
                        + np.array(n_survivable_move[constants.Action.Stop]) \
                        + np.array(n_survivable_none[a])
                    n_survivable_expected[a] = n_survivable_expected[a] / 3
                elif a in [constants.Action.Stop, constants.Action.Bomb]:
                    n_survivable_expected[a] = np.array(n_survivable_bomb[a]) + np.array(n_survivable_move[a])
                    n_survivable_expected[a] = n_survivable_expected[a] / 2                    
                else:
                    n_survivable_expected[a] = np.array(n_survivable_bomb[a]) + np.array(n_survivable_none[a])
                    n_survivable_expected[a] = n_survivable_expected[a] / 2
            action = self._get_most_survivable_action(n_survivable_expected)
            if verbose:
                print("Most might survivable action", action)
            return action.value

        # no survivable action found for any cases
        # TODO : Then consider killing enemies or helping teammate

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

        total_frac_blocked, n_survivable_nodes, blocked_time_positions \
            = self._get_frac_blocked(_list_boards, my_enemies, board, obs["bomb_life"])
    
        if teammate_position is not None:
            total_frac_blocked_teammate, n_survivable_nodes_teammate, blocked_time_positions_teammate \
                = self._get_frac_blocked(_list_boards, [my_teammate], board, obs["bomb_life"])

        block = defaultdict(float)
        for action in [constants.Action.Stop,
                       constants.Action.Up, constants.Action.Down,
                       constants.Action.Left, constants.Action.Right]:

            next_position = self._get_next_position(my_position, action)
            if not self._on_board(next_position):
                continue

            if board[next_position] in [constants.Item.Rigid.value, constants.Item.Wood.value]:
                continue

            if next_position in set.union(kickable, might_kickable):
                # kick will be considered later
                continue

            block[action] = total_frac_blocked[next_position]
            if teammate_position is not None:
                block[action] *= (1 - total_frac_blocked_teammate[next_position])
            block[action] *= self._inv_tmp            
            block[action] -=  np.log(-np.log(self.random.uniform()))

        if any([my_ammo > 0,
                obs["bomb_blast_strength"][my_position] == 0]):

            list_boards_with_bomb, _ \
                = self._board_sequence(board,
                                       info["curr_bombs"],
                                       info["curr_flames"],
                                       self._search_range,
                                       my_position,
                                       my_blast_strength=my_blast_strength,
                                       my_action=constants.Action.Bomb)

            block[constants.Action.Bomb] \
                = self._get_frac_blocked_two_lists(list_boards_with_bomb,
                                                   n_survivable_nodes,
                                                   board,
                                                   my_enemies)

            if teammate_position is not None:
                block_teammate = self._get_frac_blocked_two_lists(list_boards_with_bomb,
                                                                  n_survivable_nodes,
                                                                  board,
                                                                  [my_teammate])
                block[constants.Action.Bomb] *= (1 - block_teammate)

            block[constants.Action.Bomb] *= self._inv_tmp
            block[constants.Action.Bomb] -=  np.log(-np.log(self.random.uniform()))
                    
        for next_position in set.union(kickable, might_kickable):
            
            my_action = self._get_direction(my_position, next_position)

            list_boards_with_kick, _ \
                = self._board_sequence(obs["board"],
                                       info["curr_bombs"],
                                       info["curr_flames"],
                                       self._search_range,
                                       my_position,
                                       my_action=my_action,
                                       can_kick=True)
            
            block[my_action] \
                = self._get_frac_blocked_two_lists(list_boards_with_kick,
                                                   n_survivable_nodes,
                                                   board,
                                                   my_enemies)

            if teammate_position is not None:
                block_teammate = self._get_frac_blocked_two_lists(list_boards_with_kick,
                                                                  n_survivable_nodes,
                                                                  board,
                                                                  [my_teammate])
                block[my_action] *= (1 - block_teammate)

            block[my_action] *= self._inv_tmp
            block[my_action] -=  np.log(-np.log(self.random.uniform()))

        max_block = -np.inf
        best_action = None
        for action in block:
            if block[action] > max_block:
                max_block = block[action]
                best_action = action

        if best_action is not None:
            if verbose:
                print("Best action to kill enemies or help teammate (cannot survive)")
            return best_action.value

        # The following will not be used
        
        if obs["ammo"] > 0 and obs["blast_strength"] == 0:
            action = constants.Action.Bomb
            if verbose:
                print("Suicide", action)
                return action.value

        kickable_positions = list(set.union(kickable, might_kickable))
        if kickable_positions:
            self.random.shuffle(kickable_positions)
            action = self._get_direction(my_position, kickable_positions[0])
            if verbose:
                print("Suicide kick", action)
                return action.value
                        
        all_actions = [constants.Action.Stop,
                       constants.Action.Up,
                       constants.Action.Down,
                       constants.Action.Right,
                       constants.Action.Left]
        self.random.shuffle(all_actions)
        for action in all_actions:
            next_position = self._get_next_position(my_position, action)
            if not self._on_board(next_position):
                continue
            if utility.position_is_wall(board, next_position):
                continue
            if verbose:
                print("Random action", action)
                return action.value

        action = constants.Action.Stop
        if verbose:
            print("No action found", action)
        return action.value
