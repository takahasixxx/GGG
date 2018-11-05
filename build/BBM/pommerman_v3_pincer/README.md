# Instructions

## Install requirements (for running a competition locally)

pip install -r requirements.txt

## Create a docker image of an ibm-agent

docker build -t pommerman/ibm-agent -f examples/docker-agent/Dockerfile .

## Run a competition

python examples/run_with_ibm_agent.py



# Might be useful for debug in the future

## To run the docker container with graphics, do

docker run --rm -it -e DISPLAY=$DISPLAY -v /tmp/.X11-unix:/tmp/.X11-unix pommerman/ibm-agent bash

## To run the docker container alone, do

docker run --rm -it -p 12345:10080 pommerman/ibm-agent