import os
import sys
this_dir = os.path.dirname(os.path.abspath(__file__))
src_dir = os.path.join(this_dir, "..", "..", "src", "agents")
sys.path.append(src_dir)
from docker_agent import DockerAgent
from pommerman.agents import SimpleAgent as MyAgent


if __name__ == "__main__":

    _agent = MyAgent()
    agent = DockerAgent(_agent)
    agent.run()
