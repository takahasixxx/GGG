#!/bin/sh
#################################
# (C) Copyright IBM Corp. 2018
#################################


nohup java -Xmx10G -jar /BBMServer/BBMServer.jar &

sleep 5

python3.6 /BBMServer/test_run.py

#python3.6 /BBMServer/tt_agent_server.py

python3.6 /BBMServer/tt_agent_server.py
