from pommerman.agents.base_agent import BaseAgent
from pommerman import utility
from pommerman import constants
from pommerman import characters
from pommerman.forward_model import ForwardModel
import numpy as np
from copy import deepcopy
from collections import defaultdict


class MyBaseAgent(BaseAgent):
    
    def __init__(self):

        """
        The master agent determines the phase of the game,
        and let the expert agent for that phase choose the action.
        """
        
        super().__init__()

        self.board_shape = (constants.BOARD_SIZE, constants.BOARD_SIZE)
    
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

    def _get_bombs(self, board, bomb_blast_strength, prev_bomb_blast_strength, bomb_life, prev_bomb_life):

        """
        Summarize information about bombs

        Parameters
        ----------
        board : array
        bomb_blast_strength : array
        bomb_life : array
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

        # Keep bombs under fog
        bomb_positions_under_fog = np.where((prev_bomb_life > 1) * (board == constants.Item.Fog.value))
        bomb_life[bomb_positions_under_fog] = prev_bomb_life[bomb_positions_under_fog] - 1
        bomb_blast_strength[bomb_positions_under_fog] = prev_bomb_blast_strength[bomb_positions_under_fog]

        # Prepare information about moving bombs

        # diff = 0 if no bomb -> no bomb
        # diff = 1 if the remaining life of a bomb is decremented
        # diff = -9 if no bomb -> new bomb
        diff = prev_bomb_life - bomb_life

        moving = (diff != 0) * (diff != 1) * (diff != -9)

        # move_from: previous positions of moving bombs
        rows, cols = np.where(moving * (diff > 0))
        move_from = [position for position in zip(rows, cols)]

        # move_to: current positions of moving bombs
        rows, cols = np.where(moving * (diff < 0))
        move_to = [position for position in zip(rows, cols)]

        # TODO : Consider bombs moving into fog
        matched_move_from = [False] * len(move_from)
        
        curr_bombs = list()
        rows, cols = np.where(bomb_life > 0)
        moving_direction = np.full(self.board_shape, None)
        for position in zip(rows, cols):
            this_bomb_life = bomb_life[position]
            if position in move_to:
                # then the bomb is moving, so find the moving direction
                for i, prev_position in enumerate(move_from):
                    if prev_bomb_life[prev_position] != this_bomb_life + 1:
                        # the previous life of the bomb at the previous position
                        # must be +1 of the life of this bomb
                        continue
                    dx = position[0] - prev_position[0]
                    dy = position[1] - prev_position[1]
                    if abs(dx) + abs(dy) == 2:
                        # this can be a moving bomb whose direction is changed by kick
                        agent_position = (prev_position[0] + dx, prev_position[1])
                        if utility.position_is_agent(board, agent_position):
                            # the agent must have kicked
                            print("agent must have kicked at", agent_position)
                            moving_direction[position] = self._get_direction(agent_position,
                                                                             position)
                            break
                        agent_position = (prev_position[0], prev_position[1] + dy)
                        if utility.position_is_agent(board, agent_position):
                            # the agent must have kicked
                            print("agent must have kicked at", agent_position)
                            moving_direction[position] = self._get_direction(agent_position,
                                                                             position)
                            break
                    if abs(dx) + abs(dy) != 1:
                        # the previous position must be 1 manhattan distance
                        # from this position
                        continue
                    moving_direction[position] = self._get_direction(prev_position,
                                                                     position)
                    # TODO: there might be multiple possibilities of
                    # where the bomb came from
                    matched_move_from[i] = True
                    break
            bomb = characters.Bomb(characters.Bomber(),  # dummy owner of the bomb
                                   position,
                                   this_bomb_life,
                                   int(bomb_blast_strength[position]),
                                   moving_direction[position])
            curr_bombs.append(bomb)
            
        return curr_bombs, moving_direction

    def _get_flames(self, board, prev_board, prev_flame_life, bomb_position_strength, moving_direction):

        """
        Summarize information about flames

        Parameters
        ----------
        board : array
            pommerman board
        prev_flame_life : array
            remaining life of flames in the previous step
        bomb_position_strength : list
           list of pairs of position and strength of bombs just exploded
        moving_direction : array
            direction of moving bombs

        Return
        ------
        curr_flames : list
            list of Flames
        flame_life : array
            remaining life of flames
        """

        # decrement the life of existing flames by 1
        flame_life = prev_flame_life - (prev_flame_life > 0)  

        # set the life of new flames
        locations = np.where((prev_board!=constants.Item.Flames.value) * (board==constants.Item.Flames.value))
        flame_life[locations] = 3

        # set the life of overestimated flames at 0 
        locations = np.where(board!=constants.Item.Flames.value)
        flame_life[locations] = 0

        for (x, y), strength in bomb_position_strength:

            # for moving bombs, we cannot exactly tell whether it has stopped or not
            # so, consider both possibility

            dx = 0
            dy = 0
            if moving_direction[(x, y)] == constants.Action.Right:
                dy = 1
            elif moving_direction[(x, y)] == constants.Action.Left:
                dy = -1
            elif moving_direction[(x, y)] == constants.Action.Down:
                dx = 1
            elif moving_direction[(x, y)] == constants.Action.Up:
                dx = -1            
            possible_positions = [(x, y)]
            if moving_direction[(x, y)] is not None:
                next_position = (x + dx, y + dy)
                if self._on_board(next_position):
                    possible_positions.append(next_position)

            """
            # there is also a possibility that a bomb just started to move, or the direction is changed by kicking
            for (dx, dy) in [(1, 0), (-1, 0), (0, 1), (0, -1)]:
                agent_position = (x + dx, y + dy)
                if not self._on_board(agent_position):
                    continue
                if not utility.position_is_agent(prev_board, agent_position):
                    continue               
                # the agent might have kicked
                next_position = (x - dx, y - dy)
                if self._on_board(next_position):
                    possible_positions.append(next_position)
            """

            for (xx, yy) in possible_positions:
                if not utility.position_is_flames(board, (xx, yy)):
                    # not exploded yet
                    continue
                # To up and stop
                for dx in range(0, strength):
                    position = (xx + dx, yy)
                    if not self._on_board(position):
                        break
                    elif utility.position_is_flames(board, position):
                        flame_life[position] = 3
                # To down
                for dx in range(1, strength):
                    position = (xx - dx, yy)
                    if not self._on_board(position):
                        break
                    elif utility.position_is_flames(board, position):
                        flame_life[position] = 3
                # To right
                for dy in range(1, strength):
                    position = (xx, yy + dy)
                    if not self._on_board(position):
                        break
                    elif utility.position_is_flames(board, position):
                        flame_life[position] = 3
                # To left
                for dy in range(1, strength):
                    position = (xx, yy - dy)
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

        # four_positions: neighboring positions that are survivable
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

    def _board_sequence(self, board, bombs, flames, length, my_position,
                        my_action=None, can_kick=False, enemy_mobility=0):
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

        # Overwrite bomb over agent/fog if they overlap
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
            # if t == 0 and len(_agents) > 0:
            if t == 0 and my_action is not None:
                my_next_position = _agents[0].position
                if all([my_next_position != my_position,
                        _board[my_position] != constants.Item.Flames.value,
                        _board[my_position] != constants.Item.Bomb.value]):   
                    # I have moved, I did not die, and I was not on a bomb
                    _board[my_position] = constants.Item.Passage.value

            # Overwrite bomb over agent/fog if they overlap
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
        succ : list
            succ[t] : dict
            succ[t][position] : list of positions to which
                                one can reach the position at time t + 1
        subtree : list
            subtree[t] : dict
            subtree[t][position] : set of time-positions that are the children of (t, position)
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
            return [set()] * depth, [list()] * depth, [list()] * depth, [defaultdict(set)] * depth
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
        succ = [defaultdict(list) for _ in range(depth)]
        for t in range(depth-1, 0, -1):
            for position in survivable[t]:
                # for each position surviving at time t
                # if the position is on a bomb, I must have stayed there since I placed the bomb
                if list_boards[t][position] == constants.Item.Bomb.value:
                    if reachable[(t-1,)+position]:
                        prev[t][position].append(position)
                        succ[t-1][position].append(position)
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
                        succ[t-1][prev_position].append(position)

            # the set of prev_positions at time t-1
            # from which one can reach the surviving positions at time t
            survivable[t-1] = set([position for prevs in prev[t].values()
                                   for position in prevs])

        subtree = [defaultdict(set) for _ in range(depth)]
        for position in survivable[depth-1]:
            subtree[depth-1][position] = {(depth-1, position)}
        for t in range(depth-2, -1, -1):
            for position in survivable[t]:
                list_of_set = [{(t,position)}] + [subtree[t+1][child] for child in succ[t][position]]
                subtree[t][position] = set().union(*list_of_set)

        return survivable, prev, succ, subtree

    def _count_survivable(cls, succ, time, position):
        """
        Count the number of survivable positions at each step, starting at "position" at "time"
        """
        next_survivable = {position}
        info = [deepcopy(next_survivable)]
        n_survivable = [1]        
        for t in range(time, len(succ) - 1):
            _next_survivable = []
            for pos in next_survivable:
                _next_survivable += succ[t][pos]
            next_survivable = set(_next_survivable)
            info.append(deepcopy(next_survivable))
            n_survivable.append(len(next_survivable))            
        return n_survivable, info

    def _find_reachable_items(self, list_boards, my_position, time_positions, bomb_target=None):

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

        if bomb_target is None:
            bomb_target = np.full(self.board_shape, False)

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
                if bomb_target[position]:
                    items["target"].append((t,) + position)
                _checked[position] = True
                x, y = position
                for row, col in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
                    next_position = (x + row, y + col)
                    if not self._on_board(next_position):
                        continue
                    if _checked[next_position]:
                        continue
                    _checked[next_position] = True
                    if any([utility.position_is_agent(board, next_position),
                            utility.position_is_fog(board, next_position)]):
                        item = constants.Item(board[next_position])
                        items[item].append((t,)+next_position)
                        next_to_items[item].append((t,) + position)
                    # ignoring wall that will not exist when explode
                    if utility.position_is_wood(list_boards[-1], next_position):
                        item = constants.Item(board[next_position])
                        items[item].append((t,)+next_position)
                        next_to_items[item].append((t,) + position)

        return items, reached, next_to_items

    def _get_survivable_actions(self, survivable, obs, curr_bombs, curr_flames,
                                enemy_mobility=0):

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

        # If I have at least one bomb, and no bomb in my position,
        # then consider what happens if I lay a bomb
        if all([obs["ammo"] > 0, obs["bomb_life"][my_position] == 0]):

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
                                       enemy_mobility=enemy_mobility)
            survivable_with_bomb, prev_bomb, next_bomb, _ \
                = self._search_time_expanded_network(list_boards_with_bomb[1:],
                                                     my_position)
            survivable_with_bomb = [{my_position}] + survivable_with_bomb

            if my_position in survivable_with_bomb[1]:
                is_survivable[constants.Action.Bomb] = True
        else:
            survivable_with_bomb = None
            list_boards_with_bomb = None

        return is_survivable, survivable_with_bomb

    def _kickable_positions(self, obs, moving_direction, consider_agents=True):

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
            might_blocked = False
            if consider_agents:
                # neighboring agent might block (or change the direction) immediately
                for dx, dy in [(-1, -1), (1, -1), (-1, 1), (1, 1)]:
                    neighboring_position = (x + dx, y + dy)
                    if not self._on_board(neighboring_position):
                        continue
                    if np.sum(np.abs(np.array(neighboring_position) - np.array(next_position))) != 1:
                        continue
                    if utility.position_is_agent(obs["board"], neighboring_position):
                        print("agent is blocking at", neighboring_position)
                        might_blocked = True
                        break
                if might_blocked:
                    continue
                for dx, dy in [(-1, -1), (1, -1), (-1, 1), (1, 1)]:
                    neighboring_position = (next_position[0] + dx, next_position[1] + dy)
                    if not self._on_board(neighboring_position):
                        continue
                    if np.sum(np.abs(np.array(neighboring_position) - np.array(following_position))) != 1:
                        continue
                    if utility.position_is_agent(obs["board"], neighboring_position):
                        print("agent is blocking at", neighboring_position)
                        might_blocked = True
                        break
                if might_blocked:
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
                #if obs["bomb_life"][coming_position] <= 1:
                #    # kick and die
                #    continue
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
                        dy == -1]):
                    # coming from left
                    print("can kick a moving bomb coming from left at", next_position)
                    kickable.add(next_position)
                    break
                if all([moving_direction[coming_position] == constants.Action.Left,
                        dx == 0,
                        dy == 1]):
                    # coming from right
                    print("can kick a moving bomb coming from right at", next_position)
                    break

        return kickable
    
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
        # To down
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
        # To up
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
    def _get_direction(cls, this_position, next_position):

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

    @classmethod
    def _get_next_position(cls, position, action):
        """
        Returns the next position
        """
        x, y = position
        if action == constants.Action.Right:
            return (x, y + 1)
        elif action == constants.Action.Left:
            return (x, y - 1)
        elif action == constants.Action.Down:
            return (x + 1, y)
        elif action == constants.Action.Up:
            return (x - 1, y)
        else:
            return (x, y)

        
    @classmethod
    def _might_break_powerup(cls, board, my_position, blast_strength, might_powerup):

        """
        Whether one might break a powerup by placing a bomb at my position

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
            True iff might break a powerup by placing a bomb
        """

        x, y = my_position
        # To up
        for dx in range(1, blast_strength):
            if x + dx >= len(board[0]):
                break
            position = (x + dx, y)
            if utility.position_is_powerup(board, position) or might_powerup[position]:
                return True
            if not utility.position_is_passage(board, position):
                # stop searching this direction
                break
        # To down
        for dx in range(1, blast_strength):
            if x - dx < 0:
                break
            position = (x - dx, y)
            if utility.position_is_powerup(board, position) or might_powerup[position]:
                return True
            if not utility.position_is_passage(board, position):
                # stop searching this direction
                break
        # To right
        for dy in range(1, blast_strength):
            if y + dy >= len(board):
                break
            position = (x, y + dy)
            if utility.position_is_powerup(board, position) or might_powerup[position]:
                return True
            if not utility.position_is_passage(board, position):
                # stop searching this direction
                break
        # To left
        for dy in range(1, blast_strength):
            if y - dy < 0:
                break
            position = (x, y - dy)
            if utility.position_is_powerup(board, position) or might_powerup[position]:
                return True
            if not utility.position_is_passage(board, position):
                # stop searching this direction
                break
        return False

    def _get_breakable(self, board, my_position, blast_strength, target_item):

        """
        For each position in board, count the number of woods that can be broken
        by placing a bomb with the given blast strength at that position
        """

        n_breakable = np.zeros(board.shape)
        broken_by = defaultdict(list)  # the bomb positions where each item will be broken
        to_break = defaultdict(list)  # items that will be broken by the bomb at each positions

        reachable = np.full(board.shape, False)
        q = [my_position]
        while q:
            p = q.pop()
            if reachable[p]:
                continue
            else:
                reachable[p] = True
                for dx, dy in [(1, 0), (-1, 0), (0, 1), (0, -1)]:
                    next_position = (p[0] + dx, p[1] + dy)
                    if not self._on_board(next_position):
                        continue
                    if reachable[next_position]:
                        continue
                    if utility.position_is_wall(board, next_position):
                        continue
                    q.append(next_position)                   
        
        rows, cols = np.where(board == target_item.value)
        for wood_position in zip(rows, cols):
            x, y = wood_position
            for dx in range(1, min([blast_strength, board.shape[1] - x])):
                position = (x + dx, y)
                if reachable[position]:
                    n_breakable[position] += 1
                    broken_by[(x, y)].append(position)
                    to_break[position].append((x, y))
                else:
                    break
            for dx in range(1, min([blast_strength, x + 1])):
                position = (x - dx, y)
                if reachable[position]:
                    n_breakable[position] += 1
                    broken_by[(x, y)].append(position)
                    to_break[position].append((x, y))
                else:
                    break
            for dy in range(1, min([blast_strength, board.shape[1] - y])):
                position = (x, y + dy)
                if reachable[position]:
                    n_breakable[position] += 1
                    broken_by[(x, y)].append(position)
                    to_break[position].append((x, y))
                else:
                    break
            for dy in range(1, min([blast_strength, y + 1])):
                position = (x, y - dy)
                if reachable[position]:
                    n_breakable[position] += 1
                    broken_by[(x, y)].append(position)
                    to_break[position].append((x, y))
                else:
                    break
            
        return n_breakable, broken_by, to_break


    def _get_bomb_target(self, board, my_position, blast_strength, target_item):   

        # the number of target_items that can be broken by placing a bomb at each position
        n_breakable, broken_by, to_break = self._get_breakable(board,
                                                               my_position,
                                                               blast_strength,
                                                               target_item)

        target = np.full(board.shape, False)
        _n_breakable = deepcopy(n_breakable)
        covered_item = np.full(board.shape, False)
        
        count = np.max(_n_breakable)
        while count > 0:
            # highest count will be the target
            positions = (np.where(_n_breakable == count))
            target[positions] = True

            rows, cols = positions
            for bomb_position in zip(rows, cols):
                for item_position in to_break[bomb_position]:
                    if covered_item[item_position]:
                        continue
                    for another_bomb_position in broken_by[item_position]:
                        _n_breakable[another_bomb_position] -= 1
                    covered_item[item_position] = True

            count = np.max(_n_breakable)

        return target, n_breakable

    def _get_frac_blocked(self, list_boards, my_enemies, board, bomb_life):

        frac_blocked = dict()
        n_nodes = dict()
        for enemy in my_enemies:
            frac_blocked[enemy] = np.zeros(self.board_shape)

            # get survivable tree of the enemy
            rows, cols = np.where(board==enemy.value)
            if len(rows) == 0:
                continue
            enemy_position = (rows[0], cols[0])
            survivable, _, _, subtree \
                = self._search_time_expanded_network(list_boards,
                                                     enemy_position)

            # time-positions that can be blocked by placing a bomb now
            all_positions = set().union(*[positions for positions in survivable])
            blocked_time_positions = defaultdict(set)
            for position in all_positions:
                # do not consider the position that has a bomb now
                if bomb_life[position] > 0:
                    continue
                blocked_time_positions[position] = set().union(*[s[position] for s in subtree])

            # TODO : leaves are blocked by flames
                
            # fraction of time-positions blocked by placing a bomb at each position
            n_nodes[enemy] = sum([len(positions) for positions in survivable])
            for position in blocked_time_positions:
                frac_blocked[enemy][position] = len(blocked_time_positions[position]) / n_nodes[enemy]
        total_frac_blocked = np.sum([frac_blocked[enemy] for enemy in my_enemies], axis=0)

        return total_frac_blocked, n_nodes
        

    def _get_reachable(cls, is_rigid):

        """
        check if reachable to the main passage
        """

        # check only the upper right triangular area
        # use symmetry to fill in the remaining

        reachable = np.full(is_rigid.shape, False)

        # set the outer most
        reachable[0, :] = ~is_rigid[0, :]
        reachable[:, -1] = ~is_rigid[:, -1]

        # set the three corner
        reachable[(0, 0)] = ~(is_rigid[(0, 1)] and is_rigid[(1,0)])
        reachable[(0, -1)] = ~(is_rigid[(0, -2)] and is_rigid[(1,-1)])
        reachable[(-1, -1)] = ~(is_rigid[(-1, -2)] and is_rigid[(-2,-1)])

        # set the main passage
        reachable[1, 1:-1] = True
        reachable[1:-1, -2] = True

        # set the inner area
        reachable[2, 2:-2] = ~is_rigid[2, 2:-2]
        reachable[2:-2, -3] = ~is_rigid[2:-2, -3]

        checked = np.full(is_rigid.shape, True)
        for i in range(3, is_rigid.shape[0] - 3):
            checked[i, i:-3] = False

        cols = np.where(reachable[2, 3:-3])[0] + 3
        rows = np.where(reachable[3:-3, -3])[0] + 3
        Q = [(2, c) for c in cols] + [(r, -3) for r in rows]
        while Q:
            q = Q.pop()
            for (dx, dy) in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
                position = (q[0] + dx, q[1] + dy)
                if checked[position]:
                    continue
                checked[position] = True
                if is_rigid[position]:
                    continue
                reachable[position] = True
                Q.append(position)

        # by symmetry
        reachable += reachable.T
                
        return reachable
