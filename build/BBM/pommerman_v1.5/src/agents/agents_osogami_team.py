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
this_dir = os.path.dirname(os.path.abspath(__file__))
src_dir = os.path.join(this_dir, "..", "..", "ffa_competition")
sys.path.append(src_dir)
from run_my_agent import _MyAgent as PreviousAgent


verbose = False


class TimeExpandedAgent(PreviousAgent):

    def __init__(self):

        """
        Agent with time-expanded network
        """

        super().__init__()

        self.board_shape = (constants.BOARD_SIZE, constants.BOARD_SIZE)

        # Board in the previous step
        self._prev_bomb_life = np.zeros(self.board_shape, dtype="uint8")
        self._prev_flame_life = np.zeros(self.board_shape, dtype="uint8")
        self._prev_board = None
        self._prev_bomb_position_strength = list()

    def _on_board(self, position):

        """
        Whether the given position is on board

        Parameters
        ----------
        position : tuple
            2D coordinate

        Return
        ------
        boolean
            True iff the position is on board
        """

        if position[0] < 0:
            return False

        if position[0] >= self.board_shape[0]:
            return False

        if position[1] < 0:
            return False

        if position[1] >= self.board_shape[1]:
            return False

        return True

    def _board_sequence(self, board, bombs, flames, length, my_position,
                        my_action=None, can_kick=False, enemy_mobility=3):
        """
        Simulate the sequence of boards, assuming agents stay unmoved

        Parameters
        ----------
        board : array
            initial board
        bombs : list
            list of initial bombs
        flames : list
            list of initial flames
        length : int
            length of the board sequence to simulate
        my_position : tuple
            position of my agent
        my_action : Action, optional
            my action at the first step
        can_kick : boolean, optional
            whether I can kick
        enemy_mobility : int, optional
            number of steps where enemies move nondeterministically

        Return
        ------
        list_boards : list
            list of boards
        """

        # Forward model to simulate
        model = ForwardModel()

        # Prepare initial state
        _board = board.copy()
        _bombs = deepcopy(bombs)
        _flames = deepcopy(flames)
        _items = dict()  # we never know hidden items
        _actions = [constants.Action.Stop.value] * 4
        if my_action is not None:
            agent = characters.Bomber()
            agent.agent_id = board[my_position] - 10
            agent.position = my_position
            agent.can_kick = can_kick
            _agents = [agent]
            _actions[agent.agent_id] = my_action
        else:
            _agents = list()

        my_next_position = None

        # Get enemy positions to take into account their mobility
        rows, cols = np.where(_board > constants.Item.AgentDummy.value)
        enemy_positions = [position for position in zip(rows, cols)
                           if position != my_position]

        # List of enemies
        enemies = list()
        for position in enemy_positions:
            agent = characters.Bomber()
            agent.agent_id = board[position] - 10
            agent.position = position
            enemies.append(agent)

        _agents = _agents + enemies

        # Overwrite bomb over agent if they overlap
        for bomb in _bombs:
            _board[bomb.position] = constants.Item.Bomb.value

        # Simulate
        list_boards = [_board.copy()]
        for t in range(length):
            # Standard simulation step            
            _board, _agents, _bombs, _, _flames \
                = model.step(_actions,
                             _board,
                             _agents,
                             _bombs,
                             _items,
                             _flames)

            # Overwrite passage over my agent when it has moved to a passage
            if t == 0 and len(_agents) > 0:
                agent = _agents[0]
                my_next_position = agent.position
                if all([agent.position != my_position,
                        _board[agent.position] != constants.Item.Flames.value,
                        _board[agent.position] != constants.Item.Bomb.value]):   
                    # I did not die and did not stay on a bomb
                    _board[agent.position] = constants.Item.Passage.value

            # Overwrite bomb over agent if they overlap
            for bomb in _bombs:
                _board[bomb.position] = constants.Item.Bomb.value

            # Take into account the nondeterministic mobility of enemies
            if t < enemy_mobility:
                _enemy_positions = set()
                for x, y in enemy_positions:
                    # for each enemy position in the previous step
                    for dx, dy in [(0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)]:
                        # consider the next possible position
                        next_position = (x + dx, y + dy)
                        if not self._on_board(next_position):
                            # ignore if out of board
                            continue
                        if any([utility.position_is_passage(_board, next_position),
                                utility.position_is_powerup(_board, next_position),
                                (next_position == my_position
                                 and utility.position_is_agent(_board, next_position)
                                )]):
                            # possible as a next position
                            # TODO : what to do with my position
                            _enemy_positions.add(next_position)
                            _board[next_position] = constants.Item.AgentDummy.value
                enemy_positions = _enemy_positions

            _actions = [constants.Action.Stop.value] * 4
            _agents = enemies
            list_boards.append(_board.copy())

        return list_boards, my_next_position

    def _search_time_expanded_network(self, list_boards, my_position):

        """
        Find survivable time-positions in the list of boards from my position

        Parameters
        ----------
        list_boards : list
            list of boards, generated by _board_sequence
        my_position : tuple
            my position, where the search starts

        Return
        ------
        survivable : list
            list of the set of survivable time-positions at each time
            survivable[t] : set of survivable positions at time t
        prev : list
            prev[t] : dict
            prev[t][position] : list of positions from which
                                one can reach the position at time t
        """

        depth = len(list_boards)

        # TODO : what to do with Fog?
        exclude = [constants.Item.Fog,
                   constants.Item.Rigid,
                   constants.Item.Wood,
                   constants.Item.Bomb,
                   constants.Item.Flames,
                   constants.Item.AgentDummy]

        if list_boards[0][my_position] == constants.Item.Flames.value:
            return [set()] * depth, [list()] * depth
        
        # Forward search for reachable positions
        # reachable[(t,x,y]): whether can reach (x,y) at time t
        reachable = np.full((depth,) + self.board_shape, False)
        reachable[(0,)+my_position] = True
        next_positions = set([my_position])
        my_position_get_flame = False
        for t in range(1, depth):
            if list_boards[t][my_position] == constants.Item.Flames.value:
                my_position_get_flame = True
            curr_positions = next_positions
            next_positions = set()

            # add all possible positions
            for curr_position in curr_positions:
                next_positions.add(curr_position)
                x, y = curr_position
                for row, col in [(0, 0), (-1, 0), (1, 0), (0, -1), (0, 1)]:
                    next_positions.add((x + row, y + col))

            for position in next_positions.copy():
                if not self._on_board(position):
                    # remove out of positions
                    next_positions.remove(position)
                elif list_boards[t][position] == constants.Item.AgentDummy.value:
                    # TODO: this may be too conservative
                    # avoid contact to other agents
                    next_positions.remove(position)
                elif position == my_position and not my_position_get_flame:
                    # can stay even on bomb until getting flame
                    continue
                elif utility.position_in_items(list_boards[t], position, exclude):
                    # remove blocked
                    next_positions.remove(position)
                elif utility.position_is_agent(list_boards[t], position):
                    # if occupied by another agent
                    next_positions.remove(position)

            for position in next_positions:
                reachable[(t,)+position] = True

        # Backward search for survivable positions
        # survivable[t]: set of survavable positions at time t
        # prev[t][position]: list of positions from which
        #                    one can reach the position at time t
        survivable = [set() for _ in range(depth)]
        survivable[-1] = next_positions
        prev = [defaultdict(list) for _ in range(depth+1)]
        for t in range(depth-1, 0, -1):
            for position in survivable[t]:
                # for each position surviving at time t
                # if the position is on a bomb, I must have stayed there since I placed the bomb
                if list_boards[t][position] == constants.Item.Bomb.value:
                    if reachable[(t-1,)+position]:
                        prev[t][position].append(position)
                        continue

                # otherwise, standard case
                x, y = position
                for row, col in [(0, 0), (-1, 0), (1, 0), (0, -1), (0, 1)]:
                    # consider the prev_position at time t - 1
                    prev_position = (x + row, y + col)
                    if not self._on_board(prev_position):
                        # discard the prev_position if out of board
                        continue
                    if reachable[(t-1,)+prev_position]:
                        # can reach the position at time t
                        # from the prev_position at time t-1
                        prev[t][position].append(prev_position)

            # the set of prev_positions at time t-1
            # from which one can reach the surviving positions at time t
            survivable[t-1] = set([position for prevs in prev[t].values()
                                   for position in prevs])

        return survivable, prev

    def _find_reachable_items(self, list_boards, my_position, time_positions):

        """
        Find items reachable from my position

        Parameters
        ----------
        list_boards : list
            list of boards, generated by _board_sequence
        my_position : tuple
            my position, where the search starts
        time_positions : list
            survivable time-positions, generated by _search_time_expanded_network

        Return
        ------
        items : dict
            items[item] : list of time-positions from which one can reach item
        reached : array
            minimum time to reach each position on the board
        next_to_items : dict
            next_to_items[item] : list of time-positions from which one can reach
                                  the position next to item
        """

        # items found on time_positions and the boundary (for Wood)
        items = defaultdict(list)

        # reached[position] : minimum time to reach the position
        reached = np.full(self.board_shape, np.inf)

        # whether already checked the position
        _checked = np.full(self.board_shape, False)

        # positions next to wood or other agents (count twice if next to two woods)
        next_to_items = defaultdict(list)

        for t, positions in enumerate(time_positions):
            # check the positions reached at time t
            board = list_boards[t]
            for position in positions:
                if reached[position] < np.inf:
                    continue
                reached[position] = t
                item = constants.Item(board[position])
                items[item].append((t,) + position)
                _checked[position] = True
                x, y = position
                for row, col in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
                    next_position = (x + row, y + col)
                    if not self._on_board(next_position):
                        continue
                    if _checked[next_position]:
                        continue
                    _checked[next_position] = True
                    if utility.position_is_agent(board, next_position):
                        item = constants.Item(board[next_position])
                        items[item].append((t,)+next_position)
                        next_to_items[item].append((t,) + position)
                    # ignoring wall that will not exist when explode
                    if utility.position_is_wood(list_boards[-1], next_position):
                        item = constants.Item(board[next_position])
                        items[item].append((t,)+next_position)
                        next_to_items[item].append((t,) + position)

        return items, reached, next_to_items

    @classmethod
    def _can_break_wood_obsolete(cls, board, my_position, blast_strength):
        print("Use _can_break instead")

        """
        Whether one cay break a wood by placing a bomb at my position

        Parameters
        ----------
        board : array
            board
        my_position : tuple
            where to place a bomb
        blast_strength : int
           strength of the bomb

        Return
        ------
        boolean
            True iff can break a wood by placing a bomb
        """

        x, y = my_position
        # To up
        for dx in range(1, blast_strength):
            if x + dx >= len(board[0]):
                break
            position = (x + dx, y)
            if utility.position_is_wood(board, position):
                return True
            elif not utility.position_is_passage(board, position):
                # stop searching this direction
                break
        # To down
        for dx in range(1, blast_strength):
            if x - dx < 0:
                break
            position = (x - dx, y)
            if utility.position_is_wood(board, position):
                return True
            elif not utility.position_is_passage(board, position):
                # stop searching this direction
                break
        # To right
        for dy in range(1, blast_strength):
            if y + dy >= len(board):
                break
            position = (x, y + dy)
            if utility.position_is_wood(board, position):
                return True
            elif not utility.position_is_passage(board, position):
                # stop searching this direction
                break
        # To left
        for dy in range(1, blast_strength):
            if y - dy < 0:
                break
            position = (x, y - dy)
            if utility.position_is_wood(board, position):
                return True
            elif not utility.position_is_passage(board, position):
                # stop searching this direction
                break
        return False

    @classmethod
    def _can_break(cls, board, my_position, blast_strength, what_to_break):

        """
        Whether one cay break what_to_break by placing a bomb at my position

        Parameters
        ----------
        board : array
            board
        my_position : tuple
            where to place a bomb
        blast_strength : int
           strength of the bomb

        Return
        ------
        boolean
            True iff can break what_to_break by placing a bomb
        """

        x, y = my_position
        # To up
        for dx in range(1, blast_strength):
            if x + dx >= len(board[0]):
                break
            position = (x + dx, y)
            for item in what_to_break:
                if utility._position_is_item(board, position, item):
                    return True
            if not utility.position_is_passage(board, position):
                # stop searching this direction
                break
        # To down
        for dx in range(1, blast_strength):
            if x - dx < 0:
                break
            position = (x - dx, y)
            for item in what_to_break:
                if utility._position_is_item(board, position, item):
                    return True
            if not utility.position_is_passage(board, position):
                # stop searching this direction
                break
        # To right
        for dy in range(1, blast_strength):
            if y + dy >= len(board):
                break
            position = (x, y + dy)
            for item in what_to_break:
                if utility._position_is_item(board, position, item):
                    return True
            if not utility.position_is_passage(board, position):
                # stop searching this direction
                break
        # To left
        for dy in range(1, blast_strength):
            if y - dy < 0:
                break
            position = (x, y - dy)
            for item in what_to_break:
                if utility._position_is_item(board, position, item):
                    return True
            if not utility.position_is_passage(board, position):
                # stop searching this direction
                break
        return False
    
    @classmethod
    def _might_break_item(cls, board, my_position, blast_strength, might_item):

        """
        Whether one might break an item by placing a bomb at my position

        Parameters
        ----------
        board : array
            board
        my_position : tuple
            where to place a bomb
        blast_strength : int
           strength of the bomb

        Return
        ------
        boolean
            True iff might break an item by placing a bomb
        """

        x, y = my_position
        # To up
        for dx in range(1, blast_strength):
            if x + dx >= len(board[0]):
                break
            position = (x + dx, y)
            if utility.position_is_powerup(board, position) or might_item[position]:
                return True
            if not utility.position_is_passage(board, position):
                # stop searching this direction
                break
        # To down
        for dx in range(1, blast_strength):
            if x - dx < 0:
                break
            position = (x - dx, y)
            if utility.position_is_powerup(board, position) or might_item[position]:
                return True
            if not utility.position_is_passage(board, position):
                # stop searching this direction
                break
        # To right
        for dy in range(1, blast_strength):
            if y + dy >= len(board):
                break
            position = (x, y + dy)
            if utility.position_is_powerup(board, position) or might_item[position]:
                return True
            if not utility.position_is_passage(board, position):
                # stop searching this direction
                break
        # To left
        for dy in range(1, blast_strength):
            if y - dy < 0:
                break
            position = (x, y - dy)
            if utility.position_is_powerup(board, position) or might_item[position]:
                return True
            if not utility.position_is_passage(board, position):
                # stop searching this direction
                break
        return False
    
    def _find_distance_minimizer(self, my_position, good_time_positions,
                                 prev, is_survivable):

        """
        Which direction to move to minimize a distance score to good time-positions

        Parameters
        ----------
        my_position : tuple
            position to start search
        good_time_positions : set
            set of time-positions where one can reach good items
        prev : list
            preceding positions, generated by _search_time_expanded_network
        is_survivable : dict
            whether a given action is survivable

        Return
        ------
        direction : constants.Item.Action
            direction that minimizes the distance score
        """

        x, y = my_position

        # neighboring positions that are survivable
        four_positions = list()
        if is_survivable[constants.Action.Up]:
            four_positions.append((x - 1, y))
        if is_survivable[constants.Action.Down]:
            four_positions.append((x + 1, y))
        if is_survivable[constants.Action.Left]:
            four_positions.append((x, y - 1))
        if is_survivable[constants.Action.Right]:
            four_positions.append((x, y + 1))

        # score_next_position[(x,y)]:
        # how much the total inverse distances to good items are reduced
        score_next_position = defaultdict(int)
        for t, x, y in good_time_positions:
            if t == 0:
                if is_survivable[constants.Action.Stop]:
                    return constants.Action.Stop
                else:
                    continue
            elif t == 1:
                if (x, y) in four_positions:
                    # now next to the good position, so go ahead
                    score_next_position = {(x, y): 1}
                    break
                else:
                    continue

            # (x, y) is good and can be reached in t steps
            positions = {(x, y)}
            for s in range(t, 1, -1):
                prev_positions = set()
                for position in positions:
                    prev_positions = prev_positions.union(prev[s][position])
                positions = prev_positions
            # the last "positions" is the positions at step 1 to reach (x,y) at step t
            # maximize the potential sum 1/(t+1)
            for position in four_positions:
                # 1/t - 1/(t+1) = 1 / t(t+1)
                score_next_position[position] -= 1 / (t*(t+1))
            for position in positions:
                # 1/(t-1) - 1/t + 1/t - 1/(t+1) = 2 / t(t+2)
                score_next_position[position] += 2 / ((t-1)*(t+1))

        if len(score_next_position) == 0:
            return None

        # TODO: score_next_position is just a feature,
        # so we can be more sophisticated
        best_score, best_next_position \
            = max((v, k) for k, v in score_next_position.items())

        if best_score > 0:
            return self._get_direction(my_position, best_next_position)
        else:
            return None

    def _get_direction(self, this_position, next_position):

        """
        Direction from this position to next position

        Parameters
        ----------
        this_position : tuple
            this position
        next_position : tuple
            next position

        Return
        ------
        direction : constants.Item.Action
        """
        if this_position == next_position:
            return constants.Action.Stop
        else:
            return utility.get_direction(this_position, next_position)

    def _get_bombs(self, obs, prev_bomb_life):

        """
        Summarize information about bombs

        Parameters
        ----------
        obs : dict
            pommerman observation
        prev_bomb_life : array
            remaining life of bombs at the previous step

        Return
        ------
        curr_bombs : list
            list of bombs
        moving_direction : array
            array of moving direction of bombs
            moving_direction[position] : direction of bomb at position
        bomb_life : array
            Copy the remaining life of bombs for the next step
        """

        # Prepare information about moving bombs

        # diff = 0 if no bomb -> no bomb
        # diff = 1 if the remaining life of a bomb is decremented
        # diff = -9 if no bomb -> bomb
        diff = prev_bomb_life - obs["bomb_life"]

        moving = (diff != 0) * (diff != 1) * (diff != -9)

        # move_from: previous positions of moving bombs
        rows, cols = np.where(moving * (diff > 0))
        move_from = [position for position in zip(rows, cols)]

        # move_to: current positions of moving bombs
        rows, cols = np.where(moving * (diff < 0))
        move_to = [position for position in zip(rows, cols)]

        curr_bombs = list()
        rows, cols = np.where(obs["bomb_life"] > 0)
        moving_direction = np.full(self.board_shape, None)
        for position in zip(rows, cols):
            this_bomb_life = obs["bomb_life"][position]
            if position in move_to:
                # then the bomb is moving, so find the moving direction
                for prev_position in move_from:
                    if prev_bomb_life[prev_position] != this_bomb_life + 1:
                        # the previous life of the bomb at the previous position
                        # must be +1 of the life of this bomb
                        continue
                    dx = position[0] - prev_position[0]
                    dy = position[1] - prev_position[1]
                    if abs(dx) + abs(dy) != 1:
                        # the previous position must be 1 manhattan distance
                        # from this position
                        continue
                    moving_direction[position] = self._get_direction(prev_position,
                                                                     position)
                    # TODO: there might be multiple possibilities of
                    # where the bomb came from
                    break
            bomb = characters.Bomb(characters.Bomber(),  # dummy owner of the bomb
                                   position,
                                   this_bomb_life,
                                   int(obs["bomb_blast_strength"][position]),
                                   moving_direction[position])
            curr_bombs.append(bomb)

        return curr_bombs, moving_direction, obs["bomb_life"].copy()

    def _get_flames(self, board, prev_flame_life, bomb_position_strength):

        """
        Summarize information about flames

        Parameters
        ----------
        board : array
            pommerman board
        prev_flame_life : array
            remaining life of flames in the previous step
        exploted_position_strength : list
           list of pairs of position and strength of bombs just exploded

        Return
        ------
        curr_flames : list
            list of Flames
        flame_life : array
            remaining life of flames
        """

        flame_life = prev_flame_life - (prev_flame_life > 0)  # decrement by 1

        for (x, y), strength in bomb_position_strength:
            if not utility.position_is_flames(board, (x, y)):
                # not exploded yet
                continue
            # To up and stop
            for dx in range(0, strength):
                position = (x + dx, y)
                if not self._on_board(position):
                    break
                elif utility.position_is_flames(board, position):
                    flame_life[position] = 3
            # To down
            for dx in range(1, strength):
                position = (x - dx, y)
                if not self._on_board(position):
                    break
                elif utility.position_is_flames(board, position):
                    flame_life[position] = 3
            # To right
            for dy in range(1, strength):
                position = (x, y + dy)
                if not self._on_board(position):
                    break
                elif utility.position_is_flames(board, position):
                    flame_life[position] = 3
            # To left
            for dy in range(1, strength):
                position = (x, y - dy)
                if not self._on_board(position):
                    break
                elif utility.position_is_flames(board, position):
                    flame_life[position] = 3

        curr_flames = list()
        rows, cols = np.where(flame_life > 0)
        for position in zip(rows, cols):
            flame = characters.Flame(position, flame_life[position] - 1)
            curr_flames.append(flame)

        return curr_flames, flame_life

    def _kickable_positions(self, obs, moving_direction):

        """
        Parameters
        ----------
        obs : dict
            pommerman observation
        """

        if not obs["can_kick"]:
            return set()

        kickable = set()
        # my position
        x, y = obs["position"]

        # Find neigoboring positions around me
        on_board_next_positions = list()
        for dx, dy in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
            next_position = (x + dx, y + dy)
            if self._on_board(next_position):
                on_board_next_positions.append(next_position)

        # Check if can kick a static bomb
        for next_position in on_board_next_positions:
            if obs["board"][next_position] != constants.Item.Bomb.value:
                # not a bomb
                continue
            if moving_direction[next_position] is not None:
                # moving
                continue
            if obs["bomb_life"][next_position] <= 1:
                # kick and die
                continue
            following_position = (2 * next_position[0] - x,
                                  2 * next_position[1] - y)
            if not self._on_board(following_position):
                # cannot kick to that direction
                continue
            if not utility.position_is_passage(obs["board"], following_position):
                # cannot kick to that direction
                continue
            print("can kick a static bomb at", next_position)
            kickable.add(next_position)

        # Check if can kick a moving bomb
        for next_position in on_board_next_positions:
            if next_position in kickable:
                # can kick a static bomb
                continue
            x, y = next_position
            for dx, dy in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
                coming_position = (x + dx, y + dy)
                if coming_position == obs["position"]:
                    # cannot come from my position
                    continue
                if not self._on_board(coming_position):
                    # cannot come from out of board
                    continue
                if obs["bomb_life"][coming_position] <= 1:
                    # kick and die
                    continue
                if all([moving_direction[coming_position] == constants.Action.Up,
                        dx == 1,
                        dy == 0]):
                    # coming from below
                    print("can kick a moving bomb coming from below at", next_position)
                    kickable.add(next_position)
                    break
                if all([moving_direction[coming_position] == constants.Action.Down,
                        dx == -1,
                        dy == 0]):
                    # coming from above
                    print("can kick a moving bomb coming from",
                          coming_position, "above to", next_position)
                    kickable.add(next_position)
                    break
                if all([moving_direction[coming_position] == constants.Action.Right,
                        dx == 0,
                        dy == 1]):
                    # coming from left
                    print("can kick a moving bomb coming from left at", next_position)
                    kickable.add(next_position)
                    break
                if all([moving_direction[coming_position] == constants.Action.Left,
                        dx == 0,
                        dy == -1]):
                    # coming from right
                    print("can kick a moving bomb coming from right at", next_position)
                    kickable.add(next_position)
                    break

        return kickable

    def _get_survivable_actions(self, survivable, obs, curr_bombs, curr_flames):

        my_position = obs["position"]
        my_blast_strength = obs["blast_strength"]

        # is_survivable[action]: whether survivable with action
        is_survivable = defaultdict(bool)
        x, y = my_position

        if (x + 1, y) in survivable[1]:
            is_survivable[constants.Action.Down] = True

        if (x - 1, y) in survivable[1]:
            is_survivable[constants.Action.Up] = True

        if (x, y + 1) in survivable[1]:
            is_survivable[constants.Action.Right] = True

        if (x, y - 1) in survivable[1]:
            is_survivable[constants.Action.Left] = True

        if (x, y) in survivable[1]:
            is_survivable[constants.Action.Stop] = True

        # TODO : shoud check the survivability of all agents in one method

        # If I have at least one bomb, no bomb in my position,
        # and the position is safe
        # then consider what happens if I lay a bomb
        if all([obs["ammo"] > 0,
                obs["bomb_life"][my_position] == 0,
                is_survivable[constants.Action.Stop],
                sum(is_survivable.values()) > 1]):

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
                                       my_position)
            survivable_with_bomb, prev_bomb \
                = self._search_time_expanded_network(list_boards_with_bomb,
                                                     my_position)

            if my_position in survivable_with_bomb[1]:
                is_survivable[constants.Action.Bomb] = True
        else:
            survivable_with_bomb = None
            list_boards_with_bomb = None

        return is_survivable, survivable_with_bomb

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
        my_teammate = obs["teammate"]
        my_kick = obs["can_kick"]  # whether I can kick

        print("my position", my_position, end="\t")
        
        #
        # Prepare extended observations
        # - bomb moving direction
        # - flame remaining life
        # - flaming wood
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

        # was_wood: boolean, indicating whether each location was wood three steps ago
        if self._prev_board is None:
            # Flame life is 2
            self._prev_board = [deepcopy(board), deepcopy(board), deepcopy(board)]
            might_item = np.full(self.board_shape, False)
        else:
            old_board = self._prev_board.pop(0)
            self._prev_board.append(deepcopy(board))
            # was wood and now flames
            might_item = (old_board == constants.Item.Wood.value) * (board == constants.Item.Flames.value)

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

        # now wood and will passage
        might_item += (board == constants.Item.Wood.value) * (list_boards_no_move[-1] ==constants.Item.Passage.value)
        
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
        
        # if verbose:
        if True:
            print("survivable actions are", survivable_actions)

        # Positions where we kick a bomb if we move to
        if my_kick:
            kickable = self._kickable_positions(obs, moving_direction)
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

            # kick if possible
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
                if utility.position_is_bomb(curr_bombs, next_position):
                    continue
                print("Try moving to survive", action)
                return action.value

            print("Must die", constants.Action.Stop)
            return constants.Action.Stop.value

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

        consider_bomb = True
        if survivable_with_bomb is None:
            consider_bomb = False
        elif any([len(s) <= 2 for s in survivable_with_bomb[1:]]):
            # if not sufficiently survivable all the time after bomb, do not bomb
            consider_bomb = False
        elif self._might_break_item(list_boards_no_move[-1],
                                    my_position,
                                    my_blast_strength,
                                    might_item):
            # if might break an item, do not bomb
            consider_bomb = False
        # might not want to place a bomb which will be to be exploded in chain
        #if all([not utility.position_is_flames(board, my_position)
        #        for board in list_boards_no_move[:10]]):

        if consider_bomb:
            # place bomb if can break wood, reach fog/enemy
            if self._can_break(list_boards_no_move[-1],
                               my_position,
                               my_blast_strength,
                               [constants.Item.Wood, constants.Item.Fog] + my_enemies):
                print("Bomb to break wood/fog/enemy", constants.Action.Bomb)
                print(list_boards_no_move[-1])
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
            if my_kick and my_position in survivable_with_bomb[2]:
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
                            print("Moving to kick into fog", action)
                            return action.value
                        elif utility.position_is_enemy(list_boards[t+2], next_position, my_enemies):
                            print("Moving to kick towards enemy", action)
                            return action.value

                """
                x0, y0 = my_position
                positions_against = [(2*x0-x, 2*y0-y) for (x, y) in positions]
                positions_after_bomb = set(survivable[1]).intersection(positions_against)

                if positions_after_bomb:
                    print("Bomb to kick", enemy, constants.Action.Bomb)
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

        # kick 
        if len(kickable) > 0:

            while kickable:
                # then consider what happens if I kick a bomb
                next_position = kickable.pop()

                # do not kick a bomb if it will break a wall, enemies, or fog
                if moving_direction[next_position] is None:
                    # if it is a static bomb
                    if self._can_break(list_boards_no_move[-1],
                                       next_position,
                                       my_blast_strength,
                                       [constants.Item.Wood, constants.Item.Fog] + my_enemies):
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
        # as in the agent from the previous competition
        #
        action = super().act(obs, action_space)
        if is_survivable[constants.Action(action)]:
            print("Action from prev. agent", constants.Action(action))
            return action
        else:
            action = random.choice(survivable_actions)
            print("Random action", action)
            return action.value
        
    
