from pommerman.agents import BaseAgent
from pommerman import characters

class TrainedTensorForceAgent(BaseAgent):

    def __init__(self, tensorforce_agent, env, character=characters.Bomber):
        super(TrainedTensorForceAgent, self).__init__(character)
        self.tensorforce_agent = tensorforce_agent
        self.env = env

    def act(self, obs, action_space):
        states = self.env.featurize(obs)
        action = self.tensorforce_agent.act(states)
        return action


