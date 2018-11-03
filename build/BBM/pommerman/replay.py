import os
import sys
import json
import argparse
import pommerman
from pommerman import agents

if __name__ == "__main__":

    parser = argparse.ArgumentParser(description="Run a competition agent")
    parser.add_argument("directory", help="Directory")
    parser.add_argument("begin", help="step to start")
    parser.add_argument("end", help="step to stop")
    parser.add_argument("--interactive",
                        default=False,
                        action="store_true",
                        help="Whether to pause after each step.")
    args = parser.parse_args()
    
    # List of four dummy agents
    agent_list = [agents.RandomAgent(),
                  agents.RandomAgent(),
                  agents.RandomAgent(),
                  agents.RandomAgent()]

    # Environment
    config = 'PommeFFACompetition-v0'
    env = pommerman.make(config, agent_list)
    env.reset()

    # Replay
    begin = int(args.begin)
    end = int(args.end)
    for step in range(begin, end + 1):
        json_file = str(step) + ".json"
        json_file = os.path.join(args.directory, json_file)
        if not os.path.exists(json_file):
            print(json_file, "not found")
            break
        print("reading", json_file)
        env.set_init_game_state(json_file)
        env.reset()
        env.render()
        if args.interactive:
            sys.stdin.readline()
