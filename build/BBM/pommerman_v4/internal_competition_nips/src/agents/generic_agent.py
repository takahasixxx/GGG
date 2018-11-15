# (C) Copyright IBM Corp. 2018
from base_agent import MyBaseAgent
from pommerman import constants
from pommerman import characters
from pommerman import utility
import numpy as np
from copy import deepcopy
from collections import defaultdict
from tools import search_time_expanded_network
#from joblib import Parallel, delayed


verbose = True


class GenericAgent(MyBaseAgent):
    
    def __init__(self,
                 search_range=10,
                 enemy_mobility=4,
                 enemy_bomb=1,
                 chase_until=25,
                 inv_tmp=100,
                 interfere_threshold=0.5,
                 my_survivability_coeff=0.5,
                 teammate_survivability_coeff=0.5,
                 bomb_threshold=0.1,
                 chase_threshold=0.1,
                 backoff=0.9):

        #
        # Parameters
        #
        
        self._search_range = search_range
        self._enemy_mobility = enemy_mobility
        self._enemy_bomb = enemy_bomb
        self._inv_tmp = inv_tmp  # inverse temperature in choosing best position against enemies
        self._inv_tmp_init = inv_tmp  # inverse temperature in choosing best position against enemies
        self._chase_until = chase_until  # how long to chase enemies after losing sight
        self._interfere_threshold = interfere_threshold   # fraction of teammate survivability allowed to interfere
        self._my_survivability_threshold \
            = my_survivability_coeff * self._search_range * (self._search_range + 1)  # avoid risky actions below this threshold
        self._teammate_survivability_threshold \
            = teammate_survivability_coeff * self._search_range * (self._search_range + 1)  # avoid risky actions below this threshold
        self._bomb_threshold = bomb_threshold  # survivability-tradeoff threshold to place bomb
        self._chase_threshold = chase_threshold  # minimum fraction of blocks to chase
        self._backoff = backoff  # factor to reduce inv_tmp to avoid deadlock
        
        self.random = np.random.RandomState(0)
        super().__init__()


    def _action_most_survivable(self, n_survivable):

        most_survivable_action = None
        max_n_survivable = 0
        for action in n_survivable:
            n = sum(n_survivable[action]) + self.random.uniform()
            if n > max_n_survivable:
                max_n_survivable = n
                most_survivable_action = action

        return most_survivable_action
    
    def _action_to_target(self, my_position, reachable_items, prev, is_survivable):
        
        good_time_positions = reachable_items["target"]

        return self._find_distance_minimizer(my_position,
                                             good_time_positions,
                                             prev,
                                             is_survivable)
    
    def _action_to_powerup(self, my_position, reachable_items, prev, is_survivable):

        good_items = [constants.Item.ExtraBomb, constants.Item.IncrRange, constants.Item.Kick]

        good_time_positions = set()  # positions with good items
        for item in good_items:
            good_time_positions = good_time_positions.union(reachable_items[item])

        return self._find_distance_minimizer(my_position,
                                             good_time_positions,
                                             prev,
                                             is_survivable)
    
    def _action_to_might_powerup(self, my_position, reachable_items, prev, is_survivable):
        
        good_time_positions = reachable_items["might_powerup"]

        return self._find_distance_minimizer(my_position,
                                             good_time_positions,
                                             prev,
                                             is_survivable)
        
    def _action_to_enemy(self, my_position, next_to_fogs, prev, is_survivable, info, my_enemies):

        enemy_position = None
        latest = np.inf
        for enemy in my_enemies:
            if not enemy in info["since_last_seen_agent"]:
                continue
            when = info["since_last_seen_agent"][enemy]
            #if when == 0:
            #    # I see him now
            #    continue
            if when > self._chase_until:
                # I have not seen him too long
                continue
            if when < latest:
                latest = when
                enemy_position = info["last_seen_agent_position"][enemy]

        if enemy_position is None:
            return None

        xx, yy = enemy_position
        # move towards the fog nearest to the enemy position
        shortest_distance = np.inf
        best_time_position = []
        for t, x, y in next_to_fogs:
            fog_to_enemy = abs(x - xx) + abs(y - yy)
            if t + fog_to_enemy < shortest_distance:
                shortest_distance = t + fog_to_enemy
                best_time_position = [(t, x, y)]
        
        return self._find_distance_minimizer(my_position,
                                             best_time_position,
                                             prev,
                                             is_survivable)

    def _action_away_from_teammate(self, my_position, next_to_fogs, prev, is_survivable, info, my_teammate):

        if my_teammate not in info["since_last_seen_agent"]:
            return None
        
        if info["since_last_seen_agent"][my_teammate] > self._chase_until:
            return None
        
        xx, yy = info["last_seen_agent_position"][my_teammate]

        # move towards the fog nearest to the enemy position
        longest_distance = -np.inf
        best_time_position = []
        for t, x, y in next_to_fogs:
            fog_to_enemy = abs(x - xx) + abs(y - yy)
            if t + fog_to_enemy > longest_distance:
                longest_distance = t + fog_to_enemy
                best_time_position = [(t, x, y)]
        
        return self._find_distance_minimizer(my_position,
                                             best_time_position,
                                             prev,
                                             is_survivable)

    def _action_to_fog(self, my_position, next_to_fogs, prev, is_survivable, info):

        best_time_position = []
        oldest = 0
        for t, x, y in next_to_fogs:
            neighbors = [(x + dx, y + dy) for dx, dy in [(-1, 0), (1, 0), (0, -1), (0, 1)]]
            age = max([info["since_last_seen"][position] for position in neighbors if self._on_board(position)])
            if age > oldest:
                oldest = age
                best_time_position = [(t, x, y)]

        return self._find_distance_minimizer(my_position,
                                             best_time_position,
                                             prev,
                                             is_survivable)
    
    def act(self, obs, action_space, info):

        #
        # Definitions
        #

        board = obs['board']
        recently_seen_positions = (info["since_last_seen"] < 3)
        board[recently_seen_positions] = info["last_seen"][recently_seen_positions]
        my_position = obs["position"]  # tuple([x,y]): my position
        my_ammo = obs['ammo']  # int: the number of bombs I have
        my_blast_strength = obs['blast_strength']
        my_enemies = [constants.Item(e) for e in obs['enemies'] if e != constants.Item.AgentDummy]
        if obs["teammate"] != constants.Item.AgentDummy:
            my_teammate = obs["teammate"]
        else:
            my_teammate = None
        my_kick = obs["can_kick"]  # whether I can kick

        if verbose:
            print("my position", my_position, "ammo", my_ammo, "blast", my_blast_strength, "kick", my_kick, end="\t")

        my_next_position = {constants.Action.Stop: my_position,
                            constants.Action.Bomb: my_position}
        for action in [constants.Action.Up, constants.Action.Down,
                       constants.Action.Left, constants.Action.Right]:
            next_position = self._get_next_position(my_position, action)
            if self._on_board(next_position):
                if board[next_position] in [constants.Item.Rigid.value, constants.Item.Wood.value]:
                    my_next_position[action] = None
                else:
                    my_next_position[action] = next_position
            else:
                my_next_position[action] = None

        #
        # Understand current situation
        #

        if all([info["prev_action"] not in [constants.Action.Stop, constants.Action.Bomb],
                info["prev_position"] == my_position]):
            # if previously blocked, do not reapeat with some probability
            self._inv_tmp *= self._backoff
        else:
            self._inv_tmp = self._inv_tmp_init

        
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
        
        # where to place bombs to break wood
        digging, bomb_target = self._get_digging_positions(board, my_position, info)                

        if digging is None:
            bomb_target, n_breakable \
                = self._get_bomb_target(info["list_boards_no_move"][-1],
                                        my_position,
                                        my_blast_strength,
                                        constants.Item.Wood)

        # Positions where we kick a bomb if we move to
        if my_kick:
            kickable, might_kickable = self._kickable_positions(obs, info["moving_direction"])
        else:
            kickable = set()
            might_kickable = set()

        # positions that might be blocked
        if teammate_position is None:
            agent_positions = enemy_positions
        else:
            agent_positions = enemy_positions + [teammate_position]
        might_blocked = self._get_might_blocked(board, my_position, agent_positions, might_kickable)

        # enemy positions over time
        # these might be dissappeared due to extra flames
        if len(enemy_positions):
            rows = [p[0] for p in enemy_positions]
            cols = [p[1] for p in enemy_positions]
            list_enemy_positions = [(rows, cols)]
            _enemy_positions = list()
            for t in range(self._enemy_mobility):
                rows, cols = list_enemy_positions[-1]
                for x, y in zip(rows, cols):
                    for dx, dy in [(1, 0), (-1, 0), (0, 1), (0, -1)]:
                        next_position = (x + dx, y + dy)
                        if not self._on_board(next_position):
                            continue
                        _board = info["list_boards_no_move"][t]
                        if any([utility.position_is_passage(_board, next_position),
                                utility.position_is_powerup(_board, next_position)]):
                            _enemy_positions.append(next_position)
            _enemy_positions = set(_enemy_positions)
            rows = [p[0] for p in _enemy_positions]
            cols = [p[1] for p in _enemy_positions]
            list_enemy_positions.append((rows, cols))
        else:
            list_enemy_positions = []
            
        
        # survivable actions
        is_survivable = dict()
        for a in self._get_all_actions():
            is_survivable[a] = False
        n_survivable = dict()
        list_boards = dict()
        for my_action in self._get_all_actions():

            next_position = my_next_position[my_action]

            if next_position is None:
                continue

            if my_action == constants.Action.Bomb:
                if any([my_ammo == 0,
                        obs["bomb_blast_strength"][next_position] > 0]):
                    continue
            
            if all([utility.position_is_flames(board, next_position),
                    info["flame_life"][next_position] > 1]):
                continue

            if all([my_action != constants.Action.Stop,
                    obs["bomb_blast_strength"][next_position] > 0,
                    next_position not in set.union(kickable, might_kickable)]):
                continue

            if next_position in set.union(kickable, might_kickable):
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
            
            # list of boards from next steps
            list_boards[my_action], _ \
                = self._board_sequence(obs["board"],
                                       info["curr_bombs"],
                                       info["curr_flames"],
                                       self._search_range,
                                       my_position,
                                       my_blast_strength=my_blast_strength,
                                       my_action=my_action,
                                       can_kick=my_kick,
                                       enemy_mobility=self._enemy_mobility,
                                       enemy_bomb=self._enemy_bomb,
                                       agent_blast_strength=info["agent_blast_strength"])

            # wood might be disappeared, because of overestimated bombs
            for t in range(len(list_boards[my_action])):
                wood_positions = np.where(info["list_boards_no_move"][t] == constants.Item.Wood.value)
                list_boards[my_action][t][wood_positions] = constants.Item.Wood.value                       

            # agents might be disappeared, because of overestimated bombs
            for t, positions in enumerate(list_enemy_positions):
                list_boards[my_action][t][positions] = constants.Item.AgentDummy.value
            
            # some bombs may explode with extra bombs, leading to under estimation
            for t in range(len(list_boards[my_action])):
                flame_positions = np.where(info["list_boards_no_move"][t] == constants.Item.Flames.value)
                list_boards[my_action][t][flame_positions] = constants.Item.Flames.value
                
        """
        processed = Parallel(n_jobs=-1, verbose=0)(
            [delayed(search_time_expanded_network)(list_boards[action][1:], my_next_position[action], action)
             for action in list_boards]
        )
        for survivable, my_action in processed:
            if my_next_position[my_action] in survivable[0]:
                is_survivable[my_action] = True
                n_survivable[my_action] = [1] + [len(s) for s in survivable[1:]]
        """
        
        for my_action in list_boards:
            survivable = search_time_expanded_network(list_boards[my_action][1:],
                                                      my_next_position[my_action])
            if my_next_position[my_action] in survivable[0]:
                is_survivable[my_action] = True
                n_survivable[my_action] = [1] + [len(s) for s in survivable[1:]]
        
        survivable_actions = list()
        for a in is_survivable:
            if not is_survivable[a]:
                continue
            if might_blocked[a] and not is_survivable[constants.Action.Stop]:
                continue
            if n_survivable[a][-1] <= 1:
                is_survivable[a] = False
                continue
            survivable_actions.append(a)

        #
        # Choose action
        #
                
        if len(survivable_actions) == 0:

            #
            # return None, if no survivable actions
            #
        
            return None

        elif len(survivable_actions) == 1:

            #
            # Choose the survivable action, if it is the only choice
            #
            
            action = survivable_actions[0]
            if verbose:
                print("The only survivable action", action)
            return action.value


        #
        # Bomb at a target
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

        total_frac_blocked, n_survivable_nodes, blocked_time_positions \
            = self._get_frac_blocked(_list_boards, my_enemies, board, obs["bomb_life"])
    
        if teammate_position is not None:
            total_frac_blocked_teammate, n_survivable_nodes_teammate, blocked_time_positions_teammate \
                = self._get_frac_blocked(_list_boards, [my_teammate], board, obs["bomb_life"])

            """
            np.set_printoptions(precision=3)
            print("enemy")
            print(total_frac_blocked)
            print("teammate")
            print(total_frac_blocked_teammate)
            print("product")
            prod = total_frac_blocked * (1 - total_frac_blocked_teammate)
            print(prod[:5,:5])
            """

        p_survivable = defaultdict(float)
        for action in n_survivable:
            p_survivable[action] = sum(n_survivable[action]) / self._my_survivability_threshold
            if p_survivable[action] > 1:
                p_survivable[action] = 1

        block = defaultdict(float)
        for action in [constants.Action.Stop,
                       constants.Action.Up, constants.Action.Down,
                       constants.Action.Left, constants.Action.Right]:
            next_position = my_next_position[action]
            if next_position is None:
                continue
            if next_position in set.union(kickable, might_kickable):
                # kick will be considered later
                continue
            if all([utility.position_is_flames(board, next_position),
                    info["flame_life"][next_position] > 1,
                    is_survivable[constants.Action.Stop]]):
                # if the next position is flames,
                # I want to stop to wait, which must be feasible
                block[action] = total_frac_blocked[next_position] * p_survivable[constants.Action.Stop]
                if teammate_position is not None:
                    block[action] *= (1 - total_frac_blocked_teammate[next_position])
                block[action] *= self._inv_tmp
                block[action] -=  np.log(-np.log(self.random.uniform()))
                continue
            elif not is_survivable[action]:
                continue
            if all([might_blocked[action],
                    not is_survivable[constants.Action.Stop]]):
                continue

            block[action] = total_frac_blocked[next_position] * p_survivable[action]
            if teammate_position is not None:
                block[action] *= (1 - total_frac_blocked_teammate[next_position])
            block[action] *= self._inv_tmp            
            block[action] -=  np.log(-np.log(self.random.uniform()))
            if might_blocked[action]:
                block[action] = (total_frac_blocked[my_position] * p_survivable[constants.Action.Stop]
                                 + total_frac_blocked[next_position] * p_survivable[action]) / 2
                if teammate_position is not None:                    
                    block[action] *= (1 - total_frac_blocked_teammate[next_position])
                block[action] *= self._inv_tmp                
                block[action] -=  np.log(-np.log(self.random.uniform()))

        if is_survivable[constants.Action.Bomb]:
            list_boards_with_bomb, _ \
                = self._board_sequence(board,
                                       info["curr_bombs"],
                                       info["curr_flames"],
                                       self._search_range,
                                       my_position,
                                       my_blast_strength=my_blast_strength,
                                       my_action=constants.Action.Bomb)

            n_survivable_nodes_with_bomb = defaultdict(int)
            for enemy in my_enemies:
                # get survivable tree of the enemy
                rows, cols = np.where(board==enemy.value)
                if len(rows) == 0:
                    continue
                enemy_position = (rows[0], cols[0])
                _survivable = search_time_expanded_network(list_boards_with_bomb,
                                                           enemy_position)
                n_survivable_nodes_with_bomb[enemy] = sum([len(positions) for positions in _survivable])

            n_with_bomb = sum([n_survivable_nodes_with_bomb[enemy] for enemy in my_enemies])
            n_with_none = sum([n_survivable_nodes[enemy] for enemy in my_enemies])
            if n_with_none == 0:
                total_frac_blocked_with_bomb = 0

                # place more bombs, so the stacked enemy cannot kick
                x, y = my_position
                for dx, dy in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
                    next_position = (x + dx, y + dy)
                    following_position = (x + 2 * dx, y + 2 * dy)
                    if not self._on_board(following_position):
                        continue
                    if all([obs["bomb_life"][next_position] > 0,
                            board[following_position] > constants.Item.AgentDummy.value]):
                        total_frac_blocked_with_bomb = 1
            else:
                total_frac_blocked_with_bomb = 1 - n_with_bomb / n_with_none

            if teammate_position is not None:
                # get survivable tree of the teammate
                _survivable = search_time_expanded_network(list_boards_with_bomb, teammate_position)
                n_survivable_nodes_with_bomb_teammate = sum([len(positions) for positions in _survivable])

                n_with_bomb = n_survivable_nodes_with_bomb_teammate
                n_with_none = n_survivable_nodes_teammate[my_teammate]
                if n_with_none == 0:
                    total_frac_blocked_with_bomb_teammate = 0
                else:
                    total_frac_blocked_with_bomb_teammate = 1 - n_with_bomb / n_with_none

            action = constants.Action.Bomb
            block[action] = total_frac_blocked_with_bomb * p_survivable[action]
            if teammate_position is not None:
                block[action] *= (1 - total_frac_blocked_with_bomb_teammate)
            block[action] *= self._inv_tmp
            block[action] -=  np.log(-np.log(self.random.uniform()))

        for next_position in kickable:

            action = self._get_direction(my_position, next_position)
            if not is_survivable[action]:
                continue

            list_boards_with_kick, _ \
                = self._board_sequence(obs["board"],
                                       info["curr_bombs"],
                                       info["curr_flames"],
                                       self._search_range,
                                       my_position,
                                       my_action=action,
                                       can_kick=True)

            n_survivable_nodes_with_kick = defaultdict(int)
            for enemy in my_enemies:
                # get survivable tree of the enemy
                rows, cols = np.where(board==enemy.value)
                if len(rows) == 0:
                    continue
                enemy_position = (rows[0], cols[0])
                _survivable = search_time_expanded_network(list_boards_with_kick,
                                                           enemy_position)
                n_survivable_nodes_with_kick[enemy] = sum([len(positions) for positions in _survivable])

                n_with_kick = sum([n_survivable_nodes_with_kick[enemy] for enemy in my_enemies])
                n_with_none = sum([n_survivable_nodes[enemy] for enemy in my_enemies])
                if n_with_none == 0:
                    total_frac_blocked[next_position] = 0
                else:
                    total_frac_blocked[next_position] = 1 - n_with_kick / n_with_none

            if teammate_position is not None:
                # get survivable tree of the teammate
                _survivable = search_time_expanded_network(list_boards_with_kick, teammate_position)
                n_survivable_nodes_with_kick_teammate = sum([len(positions) for positions in _survivable])

                n_with_kick = n_survivable_nodes_with_kick_teammate
                n_with_none = n_survivable_nodes_teammate[my_teammate]
                if n_with_none == 0:
                    total_frac_blocked_teammate[next_position] = 0
                else:
                    total_frac_blocked_teammate[next_position] = 1 - n_with_kick / n_with_none
            
            block[action] = total_frac_blocked[next_position] * p_survivable[action]
            if teammate_position is not None:
                block[action] *= (1 - total_frac_blocked_teammate[next_position])
            block[action] *= self._inv_tmp
            block[action] -=  np.log(-np.log(self.random.uniform()))

        max_block = -np.inf
        best_action = None
        for action in block:
            if block[action] > max_block:
                max_block = block[action]
                best_action = action

        if block[constants.Action.Bomb] > self._bomb_threshold * self._inv_tmp:
            if teammate_position is not None:
                teammate_safety = total_frac_blocked_with_bomb_teammate * n_survivable_nodes_with_bomb_teammate
                if any([teammate_safety > self._teammate_survivability_threshold,
                        total_frac_blocked_with_bomb_teammate < self._interfere_threshold,
                        total_frac_blocked_with_bomb_teammate < total_frac_blocked_teammate[my_position]]):
                    teammate_ok = True
                else:
                    teammate_ok = False
            else:
                teammate_ok = True

            if teammate_ok:
                if best_action == constants.Action.Bomb:                    
                    if verbose:
                        print("Bomb is best", constants.Action.Bomb)
                    return constants.Action.Bomb.value

                if best_action == constants.Action.Stop:
                    if verbose:
                        print("Place a bomb at a locally optimal position", constants.Action.Bomb)
                    return constants.Action.Bomb.value

        #
        # Move towards where to bomb
        #

        if best_action not in [None, constants.Action.Bomb]:
            next_position = my_next_position[best_action]

            should_chase = (total_frac_blocked[next_position] > self._chase_threshold)

            if teammate_position is not None:
                teammate_safety = total_frac_blocked_teammate[next_position] * n_survivable_nodes_teammate[my_teammate]
                if any([teammate_safety > self._teammate_survivability_threshold,
                        total_frac_blocked_teammate[next_position] < self._interfere_threshold,
                        total_frac_blocked_teammate[next_position] < total_frac_blocked_teammate[my_position]]):
                    teammate_ok = True
                else:
                    teammate_ok = False
            else:
                teammate_ok = True

            if should_chase and teammate_ok:
                if all([utility.position_is_flames(board, next_position),
                        info["flame_life"][next_position] > 1,
                        is_survivable[constants.Action.Stop]]):
                    action = constants.Action.Stop
                    if verbose:
                        print("Wait flames life", action)
                    return action.value
                else:
                    if verbose:
                        print("Move towards better place to bomb", best_action)
                    return best_action.value                

        # Exclude the action representing stop to wait
        max_block = -np.inf
        best_action = None
        for action in survivable_actions:
            if block[action] > max_block:
                max_block = block[action]
                best_action = action
                
        #
        # Do not take risky actions
        #

        most_survivable_action = self._action_most_survivable(n_survivable)

        # ignore actions with low survivability
        _survivable_actions = list()
        for action in n_survivable:
            n = sum(n_survivable[action])
            if not is_survivable[action]:
                continue
            elif n > self._my_survivability_threshold:
                _survivable_actions.append(action)
            else:
                print("RISKY", action)
                is_survivable[action] = False

        if len(_survivable_actions) > 1:
            survivable_actions = _survivable_actions
        elif best_action is not None:
            if verbose:
                print("Take the best action in danger", best_action)
            return best_action.value
        else:
            # Take the most survivable action
            if verbose:
                print("Take the most survivable action", most_survivable_action)
            return most_survivable_action.value

        #
        # Do not interfere with teammate
        #

        if all([teammate_position is not None,
                len(enemy_positions) > 0 or len(info["curr_bombs"]) > 0]):
            # ignore actions that interfere with teammate
            min_interfere = np.inf
            least_interfere_action = None
            _survivable_actions = list()
            for action in survivable_actions:
                if action == constants.Action.Bomb:
                    frac = total_frac_blocked_with_bomb_teammate
                else:
                    next_position = my_next_position[action]
                    frac = total_frac_blocked_teammate[next_position]
                if frac < min_interfere:
                    min_interfere = frac
                    least_interfere_action = action
                if frac < self._interfere_threshold:
                    _survivable_actions.append(action)
                else:
                    print("INTERFERE", action)
                    is_survivable[action] = False

            if len(_survivable_actions) > 1:
                survivable_actions = _survivable_actions
            elif best_action is not None:
                # Take the least interfering action
                if verbose:
                    print("Take the best action in intereference", best_action)
                return best_action.value
            else:
                if verbose:
                    print("Take the least interfering action", least_interfere_action)
                return least_interfere_action.value

        #
        # Bomb to break wood
        #

        consider_bomb = True
        if not is_survivable[constants.Action.Bomb]:
            consider_bomb = False
        elif self._might_break_powerup(info["list_boards_no_move"][-1],
                                       my_position,
                                       my_blast_strength,
                                       info["might_powerup"]):
            # if might break an item, do not bomb
            consider_bomb = False

        if consider_bomb and bomb_target[my_position]:
            # place bomb if I am at a bomb target
            if verbose:
                print("Bomb at a bomb target", constants.Action.Bomb)
            return constants.Action.Bomb.value

        #
        # Find reachable items
        #

        # List of boards simulated
        list_boards, _ = self._board_sequence(board,
                                              info["curr_bombs"],
                                              info["curr_flames"],
                                              self._search_range,
                                              my_position,
                                              enemy_mobility=self._enemy_mobility,
                                              enemy_bomb=self._enemy_bomb,
                                              agent_blast_strength=info["agent_blast_strength"])
        # wood might be disappeared, because of overestimated bombs
        for t in range(len(list_boards)):
            wood_positions = np.where(info["list_boards_no_move"][t] == constants.Item.Wood.value)
            list_boards[t][wood_positions] = constants.Item.Wood.value                       

        # some bombs may explode with extra bombs, leading to under estimation
        for t in range(len(list_boards)):
            flame_positions = np.where(info["list_boards_no_move"][t] == constants.Item.Flames.value)
            list_boards[t][flame_positions] = constants.Item.Flames.value
        
        # List of the set of survivable time-positions at each time
        # and preceding positions
        survivable, prev, _, _ = self._search_time_expanded_network(list_boards,
                                                                    my_position)        
        if len(survivable[-1]) == 0:
            survivable = [set() for _ in range(len(survivable))]

        # Items and bomb target that can be reached in a survivable manner
        reachable_items, reached, next_to_items \
            = self._find_reachable_items(list_boards,
                                         my_position,
                                         survivable,
                                         bomb_target,
                                         info["might_powerup"])

        #
        # Move to dig
        #

        action_to_target = self._action_to_target(my_position, reachable_items, prev, is_survivable)

        if all([digging is not None,
                action_to_target is not None]):
            time_to_reach = reachable_items["target"][0][0]
            if any([my_ammo and board[digging] in [constants.Item.Passage.value,
                                                   constants.Item.ExtraBomb.value,
                                                   constants.Item.IncrRange.value,
                                                   constants.Item.Kick.value],
                    info["flame_life"][digging] <= time_to_reach
                    and utility.position_is_flames(board, digging)]):
                if verbose:
                    print("Move to dig", action_to_target)
                return action_to_target.value        

        #
        # Move towards good items
        #

        action = self._action_to_powerup(my_position, reachable_items, prev, is_survivable)
        if action is not None:
            if verbose:
                print("Moving toward good item", action)
            return action.value

        #
        # Move towards where to bomb to break wood
        #

        if action_to_target is not None:
            if verbose:
                print("Moving toward where to bomb", action_to_target)
            return action_to_target.value

        #
        # Move toward might powerups
        #

        action = self._action_to_might_powerup(my_position, reachable_items, prev, is_survivable)
        if action is not None:
            if verbose:
                print("Moving toward might-powerups", action)
            return action.value

        #
        # If I have seen an enemy recently and cannot see him now, them move to the last seen position
        #

        action = self._action_to_enemy(my_position, next_to_items[constants.Item.Fog], prev, is_survivable, info,
                                       my_enemies)
        if action is not None:
            if verbose:
                print("Moving toward last seen enemy", action)
            return action.value            
        
        #
        # If I have seen a teammate recently, them move away from the last seen position
        #

        action = self._action_away_from_teammate(my_position,
                                                 next_to_items[constants.Item.Fog],
                                                 prev,
                                                 is_survivable,
                                                 info,
                                                 my_teammate)
        if action is not None:
            if verbose:
                print("Moving away from last seen teammate", action)
            return action.value            
        
        #
        # Move towards a fog where we have not seen longest
        #
        
        action = self._action_to_fog(my_position, next_to_items[constants.Item.Fog], prev, is_survivable, info)

        if action is not None:
            #if True:
            if self.random.uniform() < 0.8:
                if verbose:
                    print("Moving toward oldest fog", action)
                return action.value            

        #
        # Choose most survivable action
        #

        max_block = -np.inf
        best_action = None
        for action in survivable_actions:
            if action == constants.Action.Bomb:
                continue
            if block[action] > max_block:
                max_block = block[action]
                best_action = action

        if verbose:
            print("Take the best action among safe actions (nothing else to do)", best_action)

        if best_action is None:
            # this should not be the case
            return None
        else:
            return best_action.value
        
        
