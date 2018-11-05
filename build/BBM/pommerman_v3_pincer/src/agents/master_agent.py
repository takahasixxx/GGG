import numpy as np
from copy import deepcopy
from pommerman import utility
from pommerman import constants
from .base_agent import MyBaseAgent
from .isolated_agent import IsolatedAgent
from .generic_agent import GenericAgent
from .battle_agent import BattleAgent
from .surviving_agent import SurvivingAgent
import time
from collections import defaultdict


# TODO
# 1. Move to kick, wait to kick
# 2. Do not jump into flames of life 0, if that is not needed (and under uncertainty)
# 3. Take into account the flames after 10 steps when placing bombs
# 5. Sacrifice an agent to win
# 6. Do not keep moving toward oldest fog

class MasterAgent(MyBaseAgent):

    def __init__(self):

        """
        The master agent determines the phase of the game,
        and let the expert agent for that phase choose the action.
        """
        
        super().__init__()
        self._search_range = 10

        self._steps = -1
        
        # Keep essential information in the previous steps
        self._prev_bomb_life = np.zeros(self.board_shape, dtype="uint8")
        self._prev_bomb_blast_strength = np.zeros(self.board_shape, dtype="uint8")
        self._prev_flame_life = np.zeros(self.board_shape, dtype="uint8")
        self._prev_moving_direction = np.full(self.board_shape, None)
        self._prev_board = None
        self._prev_bomb_position_strength = list()
        self._enemy_blast_strength = dict()

        # Estimated map
        self._num_rigid_found = 0
        self._might_remaining_powerup = True
        self._is_rigid = np.full(self.board_shape, False)  # false may be unknown
        self._last_seen = np.full(self.board_shape, constants.Item.Fog.value, dtype="uint8")
        self._since_last_seen = np.full(self.board_shape, np.inf)
        self._unreachable = np.full(self.board_shape, False)

        # Slaves
        self.isolated_slave = IsolatedAgent()
        self.generic_slave = GenericAgent()
        self.battle_slave = BattleAgent()
        self.surviving_slave = SurvivingAgent()

        self.max_time = 0

    def __del__(self):
        print("Maximum time to act:", self.max_time)
        
    def act(self, obs, action_space):

        t0 = time.perf_counter()
        
        # The number of steps
        self._steps += 1

        #if self._steps == 119:
        #    print("SPECIAL MOVE")
        #    return constants.Action.Up.value
        
        # TODO: deepcopy are not needed with Docker
        board = deepcopy(obs["board"])
        bomb_life = deepcopy(obs["bomb_life"])
        bomb_blast_strength = deepcopy(obs["bomb_blast_strength"])
        my_position = obs["position"]

        info = dict()
        
        #print("observed blast strength")
        #print(bomb_blast_strength)
        #print("observed bomb life")
        #print(bomb_life)
        
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
            self._enemy_blast_strength[enemy] = bomb_blast_strength[position]
        info["enemy_blast_strength"] = self._enemy_blast_strength
                    
        #
        # Choose a slave to act
        #
        
        is_wood_visible = (constants.Item.Wood.value in board)
        is_closed = self._is_closed(board, my_position)

        if is_wood_visible and is_closed:
            # Act with an agent who do not consider other agents
            print("IsolatedAgent: ", end="")
            action = self.isolated_slave.act(obs, action_space, info)
        elif not self._might_remaining_powerup:
            # Act with an agent who do not consider powerups
            print("BattleAgent: ", end="")
            action = self.battle_slave.act(obs, action_space, info)
        else:
            print("GenericAgent: ", end="")
            action = self.generic_slave.act(obs, action_space, info)

        if action is None:
            # Act with a special agent, who only seeks to survive
            print("\nSurvivingAgent: ", end="")
            action = self.surviving_slave.act(obs, action_space, info)

        this_time = time.perf_counter() - t0
        if this_time > self.max_time:
            self.max_time = this_time

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
