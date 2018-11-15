# (C) Copyright IBM Corp. 2018
import os
import sys
this_dir = os.path.dirname(os.path.abspath(__file__))
src_dir = os.path.join(this_dir, "agents")
sys.path.append(src_dir)
from docker_agent import DockerAgent
from master_agent import MasterAgent as MyAgent


if __name__ == "__main__":

    _agent = MyAgent()
    agent = DockerAgent(_agent)
    agent.run()
