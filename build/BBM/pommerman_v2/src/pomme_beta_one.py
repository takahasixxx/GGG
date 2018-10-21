import numpy as np
import pommerman
from network import PolicyValueMLP
from mcts import MCTSBase
from beta_one import BetaOne


class PommeMCTS(MCTSBase):

    """
    Implementing methods specific to Pommerman
    """
    
    def get_state(self):
        return self.environment.get_json_info()

    def set_state(self, state):
        self.environment._init_game_state = state

    def _get_feature(self, state, action=None):
        state_feature = list()
        for key in state:
            if key in ["teammate"]:
                continue
            elif key == "enemies":
                for i, agent in enumerate([pommerman.constants.Item.Agent0,
                                           pommerman.constants.Item.Agent1,
                                           pommerman.constants.Item.Agent2,
                                           pommerman.constants.Item.Agent3]):
                    if agent not in state[key]:
                        state_feature += [i]
            elif key in ["position"]:
                state_feature += state[key]
            elif key in ["alive"]:
                # binary indicator of whether each agent is alive
                state_feature += [agent in state[key] for agent in [10, 11, 12, 13]]
            elif key in ["board", "bomb_blast_strength", "bomb_life"]:
                state_feature += state[key].flatten().tolist()
            elif key in ["blast_strength", "can_kick", "ammo"]:
                state_feature += [state[key]]
            else:
                raise ValueError()
        
        state_feature = tuple(state_feature)
        if action is None:
            return state_feature
        else:
            return state_feature, action

            
if __name__ == "__main__":

    import random    
    random.seed(0)
    np.random.seed(1)

    # Environment
    config='PommeFFACompetition-v0'
    # list of dummy agents only to make an environment
    _agent_list = [pommerman.agents.BaseAgent() for _ in range(4)]
    env = pommerman.make(config, _agent_list)

    # MCTS
    mcts = PommeMCTS(env)

    # Network
    states = env.reset()
    nodes = [mcts._get_feature(state) for state in states]
    network = PolicyValueMLP(input_dim=len(nodes[0]),
                             hidden_dim=16,
                             action_dim=env.action_space.n)
    mcts.set_networks([network]*4)

    # Run BetaOne algorithm
    betaone = BetaOne(env, mcts)
    betaone.run(10, 10, 10)
    
