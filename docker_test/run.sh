#!/bin/sh
#################################
# (C) Copyright IBM Corp. 2018
#################################

source myenv/bin/activate

nohup java -Xmx10G -jar BBMServer/BBMServer.jar &> log.txt &

python BBMServer/tt_agent_server.py &> log2.txt &

python BBMClient/test_run.py
