#!/bin/sh


nohup java -Xmx10G -jar BBMServer/BBMServer.jar &> log.txt &

python3.6 BBMServer/tt_agent_server.py 

