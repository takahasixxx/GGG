from pommerman.runner import DockerAgentRunner

from collections import defaultdict
import queue
import random
import heapq

import numpy as np

from pommerman.agents import SimpleAgent, BaseAgent
from pommerman import constants
from pommerman import utility


class TestSimpleAgent(BaseAgent):
    """This is a baseline agent. After you can beat it, submit your agent to
    compete.
    """

    def __init__(self, *args, **kwargs):
        super(TestSimpleAgent, self).__init__(*args, **kwargs)

        # Keep track of recently visited uninteresting positions so that we
        # don't keep visiting the same places.
        self._recently_visited_positions = []
        self._recently_visited_length = 6
        # Keep track of the previous direction to help with the enemy standoffs.
        self._prev_direction = None
        self.bomb_time = np.zeros((11,11))
        self.logging = False
        self.prev_action = None
        self.prev_pos = None


    def act(self, obs, action_space):
        def convert_bombs(bomb_map):
            ret = []
            locations = np.where(bomb_map > 0)
            for r, c in zip(locations[0], locations[1]):
                ret.append({
                    'position': (r, c),
                    'blast_strength': int(bomb_map[(r, c)])
                })
            return ret

        depth = 20

        my_position = tuple(obs['position'])
        board = np.array(obs['board'])
        bombs = convert_bombs(np.array(obs['bomb_blast_strength']))
        enemies = [constants.Item(e) for e in obs['enemies']]
        ammo = int(obs['ammo'])
        blast_strength = int(obs['blast_strength'])

        if self.prev_pos != None:
            if self.prev_pos == my_position:
                if 1 <= self.prev_action.value <= 4:
                    if self.logging:
                        print('freeze')
                    board[self.prev_pos] = constants.Item.Rigid.value

        items, dist, prev = self._djikstra(
            board, my_position, bombs, enemies, bomb_timer=self.bomb_time, depth=depth)

        if self.logging:
            print('my_position =', my_position)
            print('board =')
            print(board)
            print('dist =')
            print(dist)
            print('bombs =', bombs)
            print('enemies =', enemies)
            for e in enemies:
                print(e)
                pos = items.get(e, [])
                print('pos =', pos)
                print('pos_len=', len(pos))
                if len(pos) > 0:
                    print('xy=', pos[0][0], ',', pos[0][1])
                # print('pos_r =', x, ',',y)
            print('ammo =', ammo)
            print('blast_strength =', blast_strength)

        test_ary = np.ones((11,11))

        for c in range(11):
            for r in range(11):
                if (r,c) in dist:
                    test_ary[r, c] = dist[(r, c)]
                else:
                    test_ary[r, c] = -1

        if self.logging:
            print("dist_mat:")
            print(test_ary)


        # update bomb_time map
        bomb_life = 8
        has_bomb = {}
        already_breakable = np.zeros((11,11))
        for b in bombs:
            r, c = b['position']
            strength = b['blast_strength']
            # print('bomb_cr =', c, 'r=', r, 'st=', strength)

            if self.bomb_time[(r, c)] == 0:
                self.bomb_time[(r, c)] = bomb_life
            else:
                self.bomb_time[(r, c)] -= 1

            for row, col in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
                for d in range(1, strength):

                    new_pos = (r + d * row, c + d * col)

                    if TestSimpleAgent._out_of_board(new_pos):
                        continue

                    # if new_pos[0] < 0 or new_pos[0] > 10:
                    #     continue
                    # if new_pos[1] < 0 or new_pos[1] > 10:
                    #     continue

                    if utility.position_is_rigid(board, new_pos):
                        continue
                    
                    if utility.position_is_wood(board, new_pos):
                        already_breakable[new_pos] = 1

                    if self.bomb_time[new_pos] == 0:
                        self.bomb_time[new_pos] = bomb_life
                    else:
                        self.bomb_time[new_pos] -= 1

                    has_bomb[new_pos] = 1

        # clear up table
        for c in range(11):
            for r in range(11):
                if (r, c) not in has_bomb:
                    self.bomb_time[(r, c)] = 0
        

        if self.logging:
            print("bomb_time:")
            print(self.bomb_time)


        # evaluate each position in terms of breakable woods
        num_breakable = np.zeros((11,11))
        num_breakable_inside = np.zeros((11,11))
        for c in range(11):
            for r in range(11):
                if utility.position_is_wood(board, (r, c)):
                    if already_breakable[(r, c)]:
                        continue
                    for row, col in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
                        for d in range(1, blast_strength):
                            new_pos = (r + d * row, c + d * col)

                            if TestSimpleAgent._out_of_board(new_pos):
                                continue

                            if utility.position_is_passable(board, new_pos, enemies) or utility.position_is_flames(board, new_pos):
                                num_breakable[new_pos] += 1
                            else:
                                break

                    tmp_num = 0
                    has_passable = False
                    for row, col in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
                        new_pos = (r + row, c + col)
                        if TestSimpleAgent._out_of_board(new_pos):
                            continue

                        if utility.position_is_wood(board, new_pos):
                            tmp_num += 1
                        elif utility.position_is_passable(board, new_pos, enemies):
                            has_passable = True
                    
                    if (not has_passable) and tmp_num > 0:
                        tmp_num -= 1
                    
                    num_breakable_inside[(r, c)] = tmp_num


        if self.logging:
            print('num_breakable:')
            print(num_breakable)

            print('num_breakable_inside:')
            print(num_breakable_inside)


        num_breakable_total = np.zeros((11,11))
        for c in range(11):
            for r in range(11):
                num_breakable_total[(r, c)] = num_breakable[(r, c)]

                if num_breakable_total[(r, c)] == -1 or num_breakable_total[(r, c)] == np.inf:
                    continue

                for row, col in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
                    new_pos = (r + row, c + col)

                    if new_pos[0] < 0 or new_pos[0] > 10:
                        continue
                    if new_pos[1] < 0 or new_pos[1] > 10:
                        continue

                    num_breakable_total[(r, c)] += num_breakable_inside[new_pos] * 0.5


        if self.logging:
            print('num_breakable_total:')
            print(num_breakable_total)


        # evaluate each position in total
        pos_scores = np.zeros((11,11))
        for c in range(11):
            for r in range(11):
                if (r, c) not in dist:
                    pos_scores[(r, c)] = -1
                    continue
                elif dist[(r, c)] == np.inf:
                    pos_scores[(r, c)] = np.inf
                    continue

                if num_breakable_total[(r, c)] > 0:
                    pos_scores[(r, c)] += num_breakable_total[(r, c)]
                    pos_scores[(r, c)] += (depth - dist[(r, c)]) * 0.2
                
                # consider power-up items
                if board[(r, c)] in {constants.Item.ExtraBomb.value, constants.Item.IncrRange.value}:
                    pos_scores[(r, c)] += 50

        if self.logging:
            print('pos_score:')
            print(pos_scores)


        # consider degree of freedom
        dis_to_ene = 100
        for e in enemies:
            pos = items.get(e, [])
            if len(pos) > 0:
                d = abs(pos[0][0] - my_position[0]) + abs(pos[0][1] - my_position[1])
                if dis_to_ene > d:
                    dis_to_ene = d
        if dis_to_ene <= -4:
        # if direction is not None:
            deg_frees = np.zeros((11,11))
            for c in range(11):
                for r in range(11):
                    # if pos_scores[(r, c)] == np.inf:
                    #     continue
                    if not utility.position_is_passable(board, (r, c), enemies):
                        continue
                    
                    deg_free = 0
                    for row, col in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
                        new_pos = (r + row, c + col)
                        if new_pos[0] < 0 or new_pos[0] > 10:
                            continue
                        if new_pos[1] < 0 or new_pos[1] > 10:
                            continue

                        if utility.position_is_passable(board, new_pos, enemies) or utility.position_is_flames(board, new_pos):
                            deg_free += 1
                    
                    deg_frees[(r, c)] = deg_free

                    if deg_free <= 1:
                        pos_scores[(r, c)] -= 5

            if self.logging:
                print('deg_free')
                print(deg_frees)

        # consider bomb blast
        for i in range(len(bombs)):
            r, c = bombs[i]['position']
            strength = bombs[i]['blast_strength']

            pos_scores[(r, c)] = -20

            for row, col in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
                for d in range(1, strength):

                    new_pos = (r + d * row, c + d * col)
                    if new_pos[0] < 0 or new_pos[0] > 10:
                        continue
                    if new_pos[1] < 0 or new_pos[1] > 10:
                        continue

                    if new_pos not in dist:
                        continue
                    elif new_pos == np.inf:
                        continue
                    
                    pos_scores[new_pos] = -20

        if self.logging:                    
            print('consider blast pos_score:')
            print(pos_scores)


        # consider enemies
        for e in enemies:
            pos = items.get(e, [])
            if len(pos) > 0:
                r = pos[0][0]
                c = pos[0][1]

                for row, col in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
                    for d in range(1, blast_strength*2):
                        new_pos = (r + d * row, c + d * col)
                        if new_pos[0] < 0 or new_pos[0] > 10:
                            continue
                        if new_pos[1] < 0 or new_pos[1] > 10:
                            continue

                        if not utility.position_is_passable(board, new_pos, enemies):
                            break
                        
                        pos_scores[new_pos] += 0.3

        if self.logging:
            print('consider enemy:')
            print(pos_scores)


        h_r, h_c = -1, -1
        h_score = -1
        for c in range(11):
            for r in range(11):
                if (r, c) not in dist:
                    continue
                elif dist[(r, c)] == np.inf:
                    continue

                if h_score < pos_scores[(r, c)]:
                    h_score = pos_scores[(r, c)]
                    h_r, h_c = (r, c)


        if self.logging:
            print('h_score and pos:', h_score, '(r, c) =', h_r, ',', h_c)
            print('prev:')
            print(prev)

        # if current position is not the highest score position, move to the highest position.
        if h_r == -1:
            # print('action: Stop')
            self.prev_action = constants.Action.Stop
            # return constants.Action.Stop.value
        elif pos_scores[my_position] == h_score:
            if self._can_escape(pos_scores, my_position, blast_strength):
                # print('set bomb')
                self.prev_action = constants.Action.Bomb
                # return constants.Action.Bomb.value
            else:
                # print('action: Stop2')
                self.prev_action = constants.Action.Stop
                # return constants.Action.Stop.value
        else:
            # print('action: backtrack')
            self.prev_action = self._backtrack(my_position, (h_r, h_c), prev)
            # return self._backtrack(my_position, (h_r, h_c), prev)


        self.prev_pos = my_position
        if self.logging:
            print('action: ', self.prev_action)
        return self.prev_action.value
        




        # Move if we are in an unsafe place.
        unsafe_directions = self._directions_in_range_of_bomb(
            board, my_position, bombs, dist)
        if unsafe_directions:
            directions = self._find_safe_directions(
                board, my_position, unsafe_directions, bombs, enemies)
            return random.choice(directions).value

        # Lay pomme if we are adjacent to an enemy.
        if self._is_adjacent_enemy(items, dist, enemies) and self._maybe_bomb(
                ammo, blast_strength, items, dist, my_position):
            return constants.Action.Bomb.value

        # Move towards an enemy if there is one in exactly three reachable spaces.
        direction = self._near_enemy(my_position, items, dist, prev, enemies, 3)
        if direction is not None and (self._prev_direction != direction or
                                      random.random() < .5):
            self._prev_direction = direction
            return direction.value

        # Move towards a good item if there is one within two reachable spaces.
        direction = self._near_good_powerup(my_position, items, dist, prev, 2)
        if direction is not None:
            return direction.value

        # Maybe lay a bomb if we are within a space of a wooden wall.
        if self._near_wood(my_position, items, dist, prev, 1):
            if self._maybe_bomb(ammo, blast_strength, items, dist, my_position):
                return constants.Action.Bomb.value
            else:
                return constants.Action.Stop.value

        # Move towards a wooden wall if there is one within two reachable spaces and you have a bomb.
        direction = self._near_wood(my_position, items, dist, prev, 2)
        if direction is not None:
            directions = self._filter_unsafe_directions(board, my_position,
                                                        [direction], bombs)
            if directions:
                return directions[0].value

        # Choose a random but valid direction.
        directions = [
            constants.Action.Stop, constants.Action.Left,
            constants.Action.Right, constants.Action.Up, constants.Action.Down
        ]
        valid_directions = self._filter_invalid_directions(
            board, my_position, directions, enemies)
        directions = self._filter_unsafe_directions(board, my_position,
                                                    valid_directions, bombs)
        directions = self._filter_recently_visited(
            directions, my_position, self._recently_visited_positions)
        if len(directions) > 1:
            directions = [k for k in directions if k != constants.Action.Stop]
        if not len(directions):
            directions = [constants.Action.Stop]

        # Add this position to the recently visited uninteresting positions so we don't return immediately.
        self._recently_visited_positions.append(my_position)
        self._recently_visited_positions = self._recently_visited_positions[
            -self._recently_visited_length:]

        return random.choice(directions).value

    @staticmethod
    def _can_escape(dist, my_position, blast_strength):

        for row, col in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
            for d in range(1, blast_strength + 1):
                new_pos = (my_position[0] + row * d, my_position[1] + col * d)

                if TestSimpleAgent._out_of_board(new_pos):
                    continue

                # if new_pos in dist and (dist[new_pos] < 0 or dist[new_pos] == np.inf):
                if dist[new_pos] < 0 or dist[new_pos] == np.inf:
                    break

                if d == blast_strength:
                    return True
                
                if row == 0:
                    for row2, col2 in [(-1, 0), (1, 0)]:
                        new_pos2 = (new_pos[0] + row2, new_pos[1] + col2)

                        if TestSimpleAgent._out_of_board(new_pos2):
                            continue
                        
                        if 0 <= dist[new_pos2] < np.inf:
                            return True
                else:
                    for row2, col2 in [(0, -1), (0, 1)]:
                        new_pos2 = (new_pos[0] + row2, new_pos[1] + col2)

                        if TestSimpleAgent._out_of_board(new_pos2):
                            continue
                        
                        if 0 <= dist[new_pos2] < np.inf:
                            return True

        return False


    @staticmethod
    def _can_escape_old(board, my_position, blast_strength):

        for row, col in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
            for d in range(1, blast_strength + 1):
                new_pos = (my_position[0] + row * d, my_position[1] + col * d)

                if TestSimpleAgent._out_of_board(new_pos):
                    continue

                if not utility.position_is_passage(board, new_pos):
                    break

                if d == blast_strength:
                    return True
                
                if row == 0:
                    for row2, col2 in [(-1, 0), (1, 0)]:
                        new_pos2 = (new_pos[0] + row2, new_pos[1] + col2)

                        if TestSimpleAgent._out_of_board(new_pos2):
                            continue
                        
                        if utility.position_is_passage(board, new_pos2):
                            return True
                else:
                    for row2, col2 in [(0, -1), (0, 1)]:
                        new_pos2 = (new_pos[0] + row2, new_pos[1] + col2)

                        if TestSimpleAgent._out_of_board(new_pos2):
                            continue
                        
                        if utility.position_is_passage(board, new_pos2):
                            return True

        return False



    @staticmethod
    def _out_of_board(pos):
        if pos[0] < 0 or pos[0] > 10:
            return True
        if pos[1] < 0 or pos[1] > 10:
            return True
        
        return False




    @staticmethod
    def _backtrack(my_position, tgt_position, prev):

        # print('in backtrack')
        # print('my_pos =', my_position)
        # print('tgt pos =', tgt_position)

        cur_pos = tgt_position
        prev_pos = cur_pos

        while my_position != cur_pos:
            prev_pos = cur_pos
            cur_pos = prev[cur_pos]

        # print('prev_pos: ', prev_pos, ', cur_pos: ', cur_pos)


        if prev_pos[0] == cur_pos[0]:
            if prev_pos[1] < cur_pos[1]:
                # print('bt: Left')
                return constants.Action.Left
            else:
                # print('bt: Right')
                return constants.Action.Right
        else:
            if prev_pos[0] < cur_pos[0]:
                # print('bt: Up')
                return constants.Action.Up
            else:
                # print('bt: Down')
                return constants.Action.Down



    @staticmethod
    def _djikstra(board, my_position, bombs, enemies, bomb_timer=None, depth=None, exclude=None):

        if depth is None:
            depth = len(board) * 2

        if exclude is None:
            exclude = [constants.Item.Fog,
                       constants.Item.Rigid,
                       constants.Item.Flames]

        def out_of_range(p1, p2):
            x1, y1 = p1
            x2, y2 = p2
            return abs(y2 - y1) + abs(x2 - x1) > depth

        items = defaultdict(list)
        
        for bomb in bombs:
            if bomb['position'] == my_position:
                items[constants.Item.Bomb].append(my_position)

        dist = {}
        prev = {}

        mx, my = my_position
        for r in range(max(0, mx - depth), min(len(board), mx + depth)):
            for c in range(max(0, my - depth), min(len(board), my + depth)):
                position = (r, c)
                if any([
                        out_of_range(my_position, position),
                        utility.position_in_items(board, position, exclude),
                ]):
                    continue

                if position == my_position:
                    dist[position] = 0
                else:
                    dist[position] = np.inf

                prev[position] = None

                item = constants.Item(board[position])
                items[item].append(position)

        # Djikstra
        H = []
        heapq.heappush(H, (0, my_position))
        while H:
            min_dist, position = heapq.heappop(H)

            if (board[position] != constants.Item.Bomb.value) and not utility.position_is_passable(board, position, enemies):
                continue
            
            x, y = position
            for row, col in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
                new_position = (row + x, col + y)
                if new_position not in dist:
                    continue

                if not utility.position_is_passable(board, new_position, enemies):
                    continue

                if bomb_timer is not None:
                    t = bomb_timer[new_position]
                    if t > 0 and abs((min_dist + 1) - t) < 2:
                        continue

                if min_dist + 1 < dist[new_position]:
                    dist[new_position] = min_dist + 1
                    prev[new_position] = position
                    heapq.heappush(H, (dist[new_position], new_position))

        return items, dist, prev



    @staticmethod
    def _djikstra_error(board, my_position, bombs, enemies, depth=None, exclude=None):
        assert (depth is not None)

        if exclude is None:
            exclude = [
                constants.Item.Fog, constants.Item.Rigid, constants.Item.Flames
            ]

        def out_of_range(p1, p2):
            x1, y1 = p1
            x2, y2 = p2
            return depth is not None and abs(y2 - y1) + abs(x2 - x1) > depth

        items = defaultdict(list)
        dist = {}
        prev = {}
        Q = queue.PriorityQueue()

        mx, my = my_position
        for r in range(max(0, mx - depth), min(len(board), mx + depth)):
            for c in range(max(0, my - depth), min(len(board), my + depth)):
                position = (r, c)
                if any([
                        out_of_range(my_position, position),
                        utility.position_in_items(board, position, exclude),
                ]):
                    continue

                if position == my_position:
                    dist[position] = 0
                else:
                    dist[position] = np.inf

                prev[position] = None
                Q.put((dist[position], position))

        for bomb in bombs:
            if bomb['position'] == my_position:
                items[constants.Item.Bomb].append(my_position)

        while not Q.empty():
            _, position = Q.get()

            if utility.position_is_passable(board, position, enemies):
                x, y = position
                val = dist[(x, y)] + 1
                for row, col in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
                    new_position = (row + x, col + y)
                    if new_position not in dist:
                        continue

                    if val < dist[new_position]:
                        dist[new_position] = val
                        prev[new_position] = position

            item = constants.Item(board[position])
            items[item].append(position)

        return items, dist, prev

    def _directions_in_range_of_bomb(self, board, my_position, bombs, dist):
        ret = defaultdict(int)

        x, y = my_position
        for bomb in bombs:
            position = bomb['position']
            distance = dist.get(position)
            if distance is None:
                continue

            bomb_range = bomb['blast_strength']
            if distance > bomb_range:
                continue

            if my_position == position:
                # We are on a bomb. All directions are in range of bomb.
                for direction in [
                        constants.Action.Right,
                        constants.Action.Left,
                        constants.Action.Up,
                        constants.Action.Down,
                ]:
                    ret[direction] = max(ret[direction], bomb['blast_strength'])
            elif x == position[0]:
                if y < position[1]:
                    # Bomb is right.
                    ret[constants.Action.Right] = max(
                        ret[constants.Action.Right], bomb['blast_strength'])
                else:
                    # Bomb is left.
                    ret[constants.Action.Left] = max(ret[constants.Action.Left],
                                                     bomb['blast_strength'])
            elif y == position[1]:
                if x < position[0]:
                    # Bomb is down.
                    ret[constants.Action.Down] = max(ret[constants.Action.Down],
                                                     bomb['blast_strength'])
                else:
                    # Bomb is down.
                    ret[constants.Action.Up] = max(ret[constants.Action.Up],
                                                   bomb['blast_strength'])
        return ret

    def _find_safe_directions(self, board, my_position, unsafe_directions,
                              bombs, enemies):

        def is_stuck_direction(next_position, bomb_range, next_board, enemies):
            Q = queue.PriorityQueue()
            Q.put((0, next_position))
            seen = set()

            nx, ny = next_position
            is_stuck = True
            while not Q.empty():
                dist, position = Q.get()
                seen.add(position)

                px, py = position
                if nx != px and ny != py:
                    is_stuck = False
                    break

                if dist > bomb_range:
                    is_stuck = False
                    break

                for row, col in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
                    new_position = (row + px, col + py)
                    if new_position in seen:
                        continue

                    if not utility.position_on_board(next_board, new_position):
                        continue

                    if not utility.position_is_passable(next_board,
                                                        new_position, enemies):
                        continue

                    dist = abs(row + px - nx) + abs(col + py - ny)
                    Q.put((dist, new_position))
            return is_stuck

        # All directions are unsafe. Return a position that won't leave us locked.
        safe = []

        if len(unsafe_directions) == 4:
            next_board = board.copy()
            next_board[my_position] = constants.Item.Bomb.value

            for direction, bomb_range in unsafe_directions.items():
                next_position = utility.get_next_position(
                    my_position, direction)
                nx, ny = next_position
                if not utility.position_on_board(next_board, next_position) or \
                   not utility.position_is_passable(next_board, next_position, enemies):
                    continue

                if not is_stuck_direction(next_position, bomb_range, next_board,
                                          enemies):
                    # We found a direction that works. The .items provided
                    # a small bit of randomness. So let's go with this one.
                    return [direction]
            if not safe:
                safe = [constants.Action.Stop]
            return safe

        x, y = my_position
        disallowed = []  # The directions that will go off the board.

        for row, col in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
            position = (x + row, y + col)
            direction = utility.get_direction(my_position, position)

            # Don't include any direction that will go off of the board.
            if not utility.position_on_board(board, position):
                disallowed.append(direction)
                continue

            # Don't include any direction that we know is unsafe.
            if direction in unsafe_directions:
                continue

            if utility.position_is_passable(board, position,
                                            enemies) or utility.position_is_fog(
                                                board, position):
                safe.append(direction)

        if not safe:
            # We don't have any safe directions, so return something that is allowed.
            safe = [k for k in unsafe_directions if k not in disallowed]

        if not safe:
            # We don't have ANY directions. So return the stop choice.
            return [constants.Action.Stop]

        return safe

    @staticmethod
    def _is_adjacent_enemy(items, dist, enemies):
        for enemy in enemies:
            for position in items.get(enemy, []):
                if dist[position] == 1:
                    return True
        return False

    @staticmethod
    def _has_bomb(obs):
        return obs['ammo'] >= 1

    @staticmethod
    def _maybe_bomb(ammo, blast_strength, items, dist, my_position):
        """Returns whether we can safely bomb right now.

        Decides this based on:
        1. Do we have ammo?
        2. If we laid a bomb right now, will we be stuck?
        """
        # Do we have ammo?
        if ammo < 1:
            return False

        # Will we be stuck?
        x, y = my_position
        for position in items.get(constants.Item.Passage):
            if dist[position] == np.inf:
                continue

            # We can reach a passage that's outside of the bomb strength.
            if dist[position] > blast_strength:
                return True

            # We can reach a passage that's outside of the bomb scope.
            px, py = position
            if px != x and py != y:
                return True

        return False

    @staticmethod
    def _nearest_position(dist, objs, items, radius):
        nearest = None
        dist_to = max(dist.values())

        for obj in objs:
            for position in items.get(obj, []):
                d = dist[position]
                if d <= radius and d <= dist_to:
                    nearest = position
                    dist_to = d

        return nearest

    @staticmethod
    def _get_direction_towards_position(my_position, position, prev):
        if not position:
            return None

        next_position = position
        while prev[next_position] != my_position:
            next_position = prev[next_position]

        return utility.get_direction(my_position, next_position)

    @classmethod
    def _near_enemy(cls, my_position, items, dist, prev, enemies, radius):
        nearest_enemy_position = cls._nearest_position(dist, enemies, items,
                                                       radius)
        return cls._get_direction_towards_position(my_position,
                                                   nearest_enemy_position, prev)

    @classmethod
    def _near_good_powerup(cls, my_position, items, dist, prev, radius):
        objs = [
            constants.Item.ExtraBomb, constants.Item.IncrRange,
            constants.Item.Kick
        ]
        nearest_item_position = cls._nearest_position(dist, objs, items, radius)
        return cls._get_direction_towards_position(my_position,
                                                   nearest_item_position, prev)

    @classmethod
    def _near_wood(cls, my_position, items, dist, prev, radius):
        objs = [constants.Item.Wood]
        nearest_item_position = cls._nearest_position(dist, objs, items, radius)
        return cls._get_direction_towards_position(my_position,
                                                   nearest_item_position, prev)

    @staticmethod
    def _filter_invalid_directions(board, my_position, directions, enemies):
        ret = []
        for direction in directions:
            position = utility.get_next_position(my_position, direction)
            if utility.position_on_board(
                    board, position) and utility.position_is_passable(
                        board, position, enemies):
                ret.append(direction)
        return ret

    @staticmethod
    def _filter_unsafe_directions(board, my_position, directions, bombs):
        ret = []
        for direction in directions:
            x, y = utility.get_next_position(my_position, direction)
            is_bad = False
            for bomb in bombs:
                bx, by = bomb['position']
                blast_strength = bomb['blast_strength']
                if (x == bx and abs(by - y) <= blast_strength) or \
                   (y == by and abs(bx - x) <= blast_strength):
                    is_bad = True
                    break
            if not is_bad:
                ret.append(direction)
        return ret

    @staticmethod
    def _filter_recently_visited(directions, my_position,
                                 recently_visited_positions):
        ret = []
        for direction in directions:
            if not utility.get_next_position(
                    my_position, direction) in recently_visited_positions:
                ret.append(direction)

        if not ret:
            ret = directions
        return ret


class MyAgent_yszm(DockerAgentRunner):
    def __init__(self):
        self._agent = TestSimpleAgent()

    def act(self, observation, action_space):
        return self._agent.act(observation, action_space)


def main():
    agent = MyAgent_yszm()
    agent.run()


if __name__ == "__main__":
    main()
