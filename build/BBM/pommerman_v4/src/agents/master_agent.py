# (C) Copyright IBM Corp. 2018
import numpy as np
from copy import deepcopy
from pommerman import utility
from pommerman import constants
from .base_agent import MyBaseAgent
from .isolated_agent import IsolatedAgent
from .generic_agent import GenericAgent
from .battle_agent import BattleAgent
from .surviving_agent import SurvivingAgent
from .collapse_agent import CollapseAgent
import time
from collections import defaultdict


verbose = False


# TODO
# 1. Move to kick, wait to kick
# 5. Sacrifice an agent to win

# 2. Do not jump into flames of life 0, if that is not needed (and under uncertainty)
# 3. Take into account the flames after 10 steps when placing bombs

class MasterAgent(MyBaseAgent):

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

        """
        The master agent determines the phase of the game,
        and let the expert agent for that phase choose the action.
        """
        
        super().__init__()
        self._search_range = search_range

        self._steps = -1
        
        # Keep essential information in the previous steps
        self._prev_bomb_life = np.zeros(self.board_shape, dtype="uint8")
        self._prev_bomb_blast_strength = np.zeros(self.board_shape, dtype="uint8")
        self._prev_flame_life = np.zeros(self.board_shape, dtype="uint8")
        self._prev_moving_direction = np.full(self.board_shape, None)
        self._prev_board = None
        self._prev_bomb_position_strength = list()
        self._agent_blast_strength = dict()
        self._prev_action = None
        self._prev_position = None
        self._isolated = True

        # Estimated map
        self._num_rigid_found = 0
        self._might_remaining_powerup = True
        self._is_rigid = np.full(self.board_shape, False)  # false may be unknown
        self._last_seen = np.full(self.board_shape, constants.Item.Fog.value, dtype="uint8")
        self._since_last_seen = np.full(self.board_shape, np.inf)
        self._unreachable = np.full(self.board_shape, False)
        self._last_seen_agent_position = defaultdict(tuple)
        self._since_last_seen_agent = dict()

        # Slaves
        self.isolated_slave = IsolatedAgent(search_range=search_range)
        self.generic_slave = GenericAgent(search_range=search_range,
                                          enemy_mobility=enemy_mobility,
                                          enemy_bomb=enemy_bomb,
                                          inv_tmp=inv_tmp,
                                          chase_until=chase_until,
                                          interfere_threshold=interfere_threshold,
                                          my_survivability_coeff=my_survivability_coeff,
                                          teammate_survivability_coeff=teammate_survivability_coeff,
                                          bomb_threshold=bomb_threshold,
                                          chase_threshold=chase_threshold)
        self.battle_slave = BattleAgent(search_range=search_range,
                                        enemy_mobility=enemy_mobility,
                                        enemy_bomb=enemy_bomb,
                                        inv_tmp=inv_tmp,
                                        chase_until=chase_until,
                                        interfere_threshold=interfere_threshold,
                                        my_survivability_coeff=my_survivability_coeff,
                                        teammate_survivability_coeff=teammate_survivability_coeff,
                                        bomb_threshold=bomb_threshold,
                                        chase_threshold=chase_threshold)
        self.collapse_slave = CollapseAgent(search_range=search_range,
                                            enemy_mobility=enemy_mobility,
                                            enemy_bomb=enemy_bomb,
                                            inv_tmp=inv_tmp,
                                            chase_until=chase_until,
                                            interfere_threshold=interfere_threshold,
                                            my_survivability_coeff=my_survivability_coeff,
                                            teammate_survivability_coeff=teammate_survivability_coeff,
                                            bomb_threshold=bomb_threshold,
                                            chase_threshold=chase_threshold)
        self.surviving_slave = SurvivingAgent(search_range=search_range)

        self.max_time = 0

    def __del__(self):
        print("Maximum time to act:", self.max_time)
        
    def act(self, obs, action_space):

        t0 = time.perf_counter()
        
        # The number of steps
        self._steps += 1

        # TODO: deepcopy are not needed with Docker
        board = deepcopy(obs["board"])
        bomb_life = deepcopy(obs["bomb_life"])
        bomb_blast_strength = deepcopy(obs["bomb_blast_strength"])
        my_position = obs["position"]
        my_enemies = [constants.Item(e) for e in obs['enemies'] if e != constants.Item.AgentDummy]
        if obs["teammate"] != constants.Item.AgentDummy:
            my_teammate = obs["teammate"]
        else:
            my_teammate = None

        teammate_position = None
        if my_teammate is not None:
            rows, cols = np.where(board==my_teammate.value)
            if len(rows):
                teammate_position = (rows[0], cols[0])

        info = dict()
        info["prev_action"] = self._prev_action
        info["prev_position"] = self._prev_position
        
        #
        # Whether each location is Rigid
        #
        #  False may be an unknown
        #
        if self._num_rigid_found < constants.NUM_RIGID:
            self._is_rigid += (board == constants.Item.Rigid.value)
            self._is_rigid += (board.T == constants.Item.Rigid.value)
            self._num_rigid_found = np.sum(self._is_rigid)
            self._rigid_locations = np.where(self._is_rigid)
            self._unreachable = ~self._get_reachable(self._is_rigid)
            self._unreachable_locations = np.where(self._unreachable)
            
        #
        # What we have seen last time, and how many steps have past since then
        #
        #  Once we see a Rigid, we always see it
        #
        visible_locations = np.where(board != constants.Item.Fog.value)
        self._last_seen[visible_locations] = board[visible_locations]
        #self._last_seen[self._rigid_locations] = constants.Item.Rigid.value
        # unreachable -> rigid
        self._last_seen[self._unreachable_locations] = constants.Item.Rigid.value
        self._since_last_seen += 1
        self._since_last_seen[visible_locations] = 0
        self._since_last_seen[np.where(self._is_rigid)] = 0
        if self._steps == 0:
            # We have some knowledge about the initial configuration of the board
            C = constants.BOARD_SIZE - 2
            self._last_seen[(1, 1)] = constants.Item.Agent0.value
            self._last_seen[(C, 1)] = constants.Item.Agent1.value
            self._last_seen[(C, C)] = constants.Item.Agent2.value
            self._last_seen[(1, C)] = constants.Item.Agent3.value
            rows = np.array([1, C, 1, C])
            cols = np.array([1, 1, C, C])
            self._since_last_seen[(rows, cols)] = 0
            rows = np.array([1, 1, 1, 1, 2, 3, C - 1, C - 2, C, C, C, C, 2, 3, C - 1, C - 2])
            cols = np.array([2, 3, C - 1, C - 2, 1, 1, 1, 1, 2, 3, C - 1, C - 2, C, C, C, C])
            self._last_seen[(rows, cols)] = constants.Item.Passage.value
            self._since_last_seen[(rows, cols)] = 0

        info["since_last_seen"] = self._since_last_seen
        info["last_seen"] = self._last_seen
            
        #
        # Modify the board
        #

        #fog_positions = np.where(board==constants.Item.Fog.value)
        #board[fog_positions] = self._last_seen[fog_positions]

        board[self._unreachable_locations] = constants.Item.Rigid.value

        #
        # Summarize information about bombs
        #
        #  curr_bombs : list of current bombs
        #  moving_direction : array of moving direction of bombs
        info["curr_bombs"], info["moving_direction"] \
            = self._get_bombs(board,
                              bomb_blast_strength, self._prev_bomb_blast_strength,
                              bomb_life, self._prev_bomb_life)

        self._prev_bomb_life = bomb_life.copy()
        self._prev_bomb_blast_strength = bomb_blast_strength.copy()

        #
        # Bombs to be exploded in the next step
        #
        curr_bomb_position_strength = list()
        rows, cols = np.where(bomb_blast_strength > 0)
        for position in zip(rows, cols):
            strength = int(bomb_blast_strength[position])
            curr_bomb_position_strength.append((position, strength))

        #
        # Summarize information about flames
        #
        if self._prev_board is not None:
            info["curr_flames"], self._prev_flame_life \
                = self._get_flames(board,
                                   self._prev_board[-1],
                                   self._prev_flame_life,
                                   self._prev_bomb_position_strength,
                                   curr_bomb_position_strength,
                                   self._prev_moving_direction)
        else:
            info["curr_flames"] = []
        info["flame_life"] = self._prev_flame_life

        self._prev_moving_direction = deepcopy(info["moving_direction"])

        self._prev_bomb_position_strength = curr_bomb_position_strength
        
        #
        # List of simulated boards, assuming enemies stay unmoved
        #

        info["list_boards_no_move"], _ \
            = self._board_sequence(board,
                                   info["curr_bombs"],
                                   info["curr_flames"],
                                   self._search_range,
                                   my_position,
                                   enemy_mobility=0)

        #
        # Might appear item from flames
        #

        info["might_powerup"] = np.full(self.board_shape, False)
        if self._prev_board is None:
            # Flame life is 2
            # flame life is hardcoded in pommmerman/characters.py class Flame
            self._prev_board = [deepcopy(board), deepcopy(board), deepcopy(board)]
        else:
            old_board = self._prev_board.pop(0)
            self._prev_board.append(deepcopy(board))
            if self._might_remaining_powerup:
                # was wood and now flames
                was_wood = (old_board == constants.Item.Wood.value)
                now_flames = (board == constants.Item.Flames.value)
                info["might_powerup"] = was_wood * now_flames

                # now wood and will passage
                now_wood = (board == constants.Item.Wood.value)
                become_passage = (info["list_boards_no_move"][-1] ==constants.Item.Passage.value)
                info["might_powerup"] += now_wood * become_passage

                maybe_powerup = info["might_powerup"] \
                                + (self._last_seen == constants.Item.Wood.value) \
                                + (self._last_seen == constants.Item.ExtraBomb.value) \
                                + (self._last_seen == constants.Item.IncrRange.value) \
                                + (self._last_seen == constants.Item.Kick.value)            
                if not maybe_powerup.any():
                    self._might_remaining_powerup = False

        # update the estimate of enemy blast strength
        rows, cols = np.where(bomb_life == constants.DEFAULT_BOMB_LIFE - 1)
        for position in zip(rows, cols):
            if position == my_position:
                continue
            enemy = board[position]
            self._agent_blast_strength[enemy] = bomb_blast_strength[position]
        info["agent_blast_strength"] = self._agent_blast_strength

        # update the last seen enemy position
        for agent in self._since_last_seen_agent:
            self._since_last_seen_agent[agent] += 1

        for enemy in my_enemies:
            rows, cols = np.where(board == enemy.value)
            if len(rows):
                self._last_seen_agent_position[enemy] = (rows[0], cols[0])
                self._since_last_seen_agent[enemy] = 0
                continue

        if teammate_position is not None:
            self._last_seen_agent_position[my_teammate] = teammate_position
            self._since_last_seen_agent[my_teammate] = 0            

        info["last_seen_agent_position"] = self._last_seen_agent_position
        info["since_last_seen_agent"] = self._since_last_seen_agent
        
        #
        # Choose a slave to act
        #

        if self._isolated:
            is_wood_visible = (constants.Item.Wood.value in board)
            is_closed = self._is_closed(board, my_position)
            if any([not is_wood_visible, not is_closed]):
                self._isolated = False
                
        action = None
        if obs["game_env"] == 'pommerman.envs.v1:Pomme':
            print("CollapseAgent: ", end="")
            action = self.collapse_slave.act(obs, action_space, info)
        if self._isolated:
            # Act with an agent who do not consider other agents
            if verbose:
                print("IsolatedAgent: ", end="")
            action = self.isolated_slave.act(obs, action_space, info)
        elif not self._might_remaining_powerup:
            # Act with an agent who do not consider powerups
            print("BattleAgent: ", end="")
            action = self.battle_slave.act(obs, action_space, info)
        else:
            if verbose:
                print("GenericAgent: ", end="")
            action = self.generic_slave.act(obs, action_space, info)
        
        if action is None:
            # Act with a special agent, who only seeks to survive
            if verbose:
                print("\nSurvivingAgent: ", end="")
            action = self.surviving_slave.act(obs, action_space, info)

        this_time = time.perf_counter() - t0
        if this_time > self.max_time:
            self.max_time = this_time

        self._prev_action = action
        self._prev_position = my_position
        
        return action
            
    def _is_closed(self, board, position):

        """
        Check whether the position is srounded by Wood/Rigid.

        Parameters
        ----------
        board = np.array(obs['board'])

        position = tuple(obs['position'])
        """

        is_done = np.full(board.shape, False)
        is_done[position] = True
        to_search = [position]

        while to_search:
            x, y = to_search.pop()
            for dx, dy in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
                new_position = (x + dx, y + dy)
                if not self._on_board(new_position):
                    continue
                if is_done[new_position]:
                    continue
                is_done[new_position] = True
                if utility.position_is_agent(board, new_position):
                    return False
                if utility.position_is_wall(board, new_position):
                    continue
                if utility.position_is_fog(board, new_position):
                    continue
                to_search.append(new_position)

        return True
