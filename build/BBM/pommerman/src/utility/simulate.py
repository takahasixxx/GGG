from pommerman import constants, characters
from pommerman.forward_model import ForwardModel
import numpy as np


def in_board(position, board_size):
    if position[0] < 0:
        return False
    if position[0] >= board_size[0]:
        return False
    if position[1] < 0:
        return False
    if position[1] >= board_size[1]:
        return False
    return True
    

def noop_board_sequence(obs, length):

    """
    Simulate the sequence of boards, assuming agents stay unmoved
    """

    model = ForwardModel()

    # Dummy objects
    actions = [constants.Action.Stop.value] * 4  # agents stay unmoved
    curr_agents = list()  # empty list of Bombers
    curr_items = dict()  # we never know hidden items

    # Prepare initial state
    curr_board = obs["board"]
    
    curr_bombs = list()
    rows, cols = np.where(obs["bomb_life"] > 0)
    for row, col in zip(rows, cols):
        bomber = characters.Bomber()  # dummy owner of the bomb
        position = (row, col)
        life = int(obs["bomb_life"][row][col])
        blast_strength = int(obs["bomb_blast_strength"][row][col])
        moving_direction = None  # TODO: this may be known
        bomb = characters.Bomb(bomber,
                               position,
                               life,
                               blast_strength,
                               moving_direction)
        curr_bombs.append(bomb)
        # overwrite bomb over agent if they overlap
        curr_board[position] = constants.Item.Bomb.value
    
    curr_flames = list()
    rows, cols = np.where(obs["board"] == constants.Item.Flames.value)
    for row, col in zip(rows, cols):
        position = (row, col)
        life = None  # TODO: this may be known
        if life is not None:
            flame = characters.Flame(position, life)
        else:
            flame = characters.Flame(position)
        curr_flames.append(flame)

    # Simulate
    list_boards = [curr_board.copy()]
    for _ in range(length):
        curr_board, _, curr_bombs, _, curr_flames = model.step(actions,
                                                               curr_board,
                                                               curr_agents,
                                                               curr_bombs,
                                                               curr_items,
                                                               curr_flames)
        list_boards.append(curr_board.copy())

    return list_boards


def DO_NOT_USE_simple_board_sequence(obs, length):
    """
    Assume agents stay unmoved

    Parameters
    ----------
    obs : dict
        observation for an agent
    length : int
        length of the sequence to generate

    Return
    ------
    list_boards : list of a given length
        sequence of boards in progress
    """
    
    # positions of bombs
    rows, cols = np.where(obs[0]["bomb_life"] > 0)
    bomb_positions = [(row, col) for row, col in zip(rows, cols)]
    bomb_life = np.array([obs[0]["bomb_life"][row][col] for row, col in bomb_positions], dtype="uint8")
    bomb_strength = np.array([obs[0]["bomb_blast_strength"][row][col] for row, col in bomb_positions], dtype="uint8")

    # initial board
    board = obs["board"]
    list_boards = [board]

    
    for i in range(length - 1):
        # Convert flame -> passage
        is_flame = (board == constants.Item.Flames.value)
        board *= ~is_flame  # zero if flame
        board += np.array(constants.Item.Passage.value, dtype="uint8") * is_flame
        # blast if no remaining life
        idx_to_blast = np.where(bomb_life==1)[0]
        for idx in idx_to_blast:
            row, col = bomb_positions[idx]
            strength = bomb_strength[idx]
            

        # reduce life time of bombs

if __name__ == "__main__":

    import pommerman
    import sys
    import random
    random.seed(0)
    
    config='PommeFFACompetition-v0'
    _agent_list = [pommerman.agents.SimpleAgent() for _ in range(4)]
    env = pommerman.make(config, _agent_list)
    obs = env.reset()
    for _ in range(10):
        actions = env.act(obs)
        obs, reward, done, info = env.step(actions)
        env.render()
    sys.stdin.readline()

    list_boards = noop_board_sequence(obs[0], 10)
    for board in list_boards:
        print(board)
        env._board = board
        env.render()
        sys.stdin.readline()
    
    env.close()
