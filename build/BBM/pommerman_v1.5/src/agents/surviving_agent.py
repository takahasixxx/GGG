from .base_agent import MyBaseAgent
from pommerman import constants
from pommerman import utility
import random


verbose = False


class SurvivingAgent(MyBaseAgent):

    def __init__(self):
        self._search_range = 10
        super().__init__()
    
    def act(self, obs, action_space, info):

        #
        # Definitions
        #
        
        board = obs['board']
        my_position = obs["position"]  # tuple([x,y]): my position
        my_kick = obs["can_kick"]  # whether I can kick
        my_enemies = [constants.Item(e) for e in obs['enemies']]
        my_teammate = obs["teammate"]

        # List of the set of survivable time-positions at each time
        # and preceding positions
        survivable, prev, succ, _ \
            = self._search_time_expanded_network(info["list_boards_no_move"],
                                                 my_position)

        # Survivable actions
        is_survivable, survivable_with_bomb \
            = self._get_survivable_actions(survivable,
                                           obs,
                                           info["curr_bombs"],
                                           info["curr_flames"],
                                           enemy_mobility=0)

        survivable_actions = [a for a in is_survivable if is_survivable[a]]

        n_survivable = dict()
        kick_actions = list()
        if my_kick:
            # Positions where we kick a bomb if we move to
            kickable = self._kickable_positions(obs, info["moving_direction"])
            for next_position in kickable:
                # consider what happens if I kick a bomb
                my_action = self._get_direction(my_position, next_position)

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

                list_boards_with_kick, next_position \
                    = self._board_sequence(obs["board"],
                                           info["curr_bombs"],
                                           info["curr_flames"],
                                           self._search_range,
                                           my_position,
                                           my_action=my_action,
                                           can_kick=True,
                                           enemy_mobility=0)
                #print(list_boards_with_kick)
                survivable_with_kick, prev_kick, succ_kick, _ \
                    = self._search_time_expanded_network(list_boards_with_kick[1:],
                                                         next_position)
                if next_position in survivable_with_kick[0]:
                    survivable_actions.append(my_action)
                    is_survivable[my_action] = True
                    n_survivable[my_action] = [1] + [len(s) for s in survivable_with_kick[1:]]
                    kick_actions.append(my_action)
        else:
            kickable = set()
                
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

        most_survivable_action = None
        if survivable_actions:
            survivable_score = dict()
            for action in n_survivable:
                #survivable_score[action] = sum([-n**(-5) for n in n_survivable[action]])
                survivable_score[action] = sum([n for n in n_survivable[action]])
                if verbose:
                    print(action, survivable_score[action], n_survivable[action])                
            best_survivable_score = max(survivable_score.values())
        
            random.shuffle(survivable_actions)
            for action in survivable_actions:
                if survivable_score[action] == best_survivable_score:
                    most_survivable_action = action
                    break

        if most_survivable_action is not None:
            print("Most survivable action", most_survivable_action)
            return most_survivable_action.value
    
        # kick if possible
        if my_kick:
            kickable = self._kickable_positions(obs, info["moving_direction"])
        else:
            kickable = set()
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
            if utility.position_is_bomb(info["curr_bombs"], next_position):
                continue
            print("Try moving to survive", action)
            return action.value

        action = constants.Action.Stop
        print("Must die", action)
        return action
