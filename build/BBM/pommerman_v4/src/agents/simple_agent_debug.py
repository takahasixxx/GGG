import heapq
import numpy as np
from collections import defaultdict
from pommerman import utility
from pommerman import constants
from pommerman.agents import SimpleAgent


class SimpleAgentDebugged(SimpleAgent):

    """
    SimpleAgent with standard Dijkstra
    """

    @staticmethod
    def _djikstra(board, my_position, bombs, enemies, depth=None, exclude=None):

        """
        Dijkstra method

        Parameters
        ----------
        board = np.array(obs['board'])

        my_position = tuple(obs['position'])

        bombs = convert_bombs(np.array(obs['bomb_blast_strength']))

        enemies = [constants.Item(e) for e in obs['enemies']]
        """
        
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

            if not utility.position_is_passable(board, position, enemies):
                continue
            
            x, y = position
            for row, col in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
                new_position = (row + x, col + y)
                if new_position not in dist:
                    continue

                if min_dist + 1 < dist[new_position]:
                    dist[new_position] = min_dist + 1
                    prev[new_position] = position
                    heapq.heappush(H, (dist[new_position], new_position))

        return items, dist, prev


