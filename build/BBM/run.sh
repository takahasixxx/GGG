#!/bin/sh

source myenv/bin/activate

python --version
pip --version


# kill all BBM processes.
ps aux | grep BBM.jar | grep -v grep | awk '{print $2}' | while read LINE; do echo $LINE; kill -9 $LINE; done
ps aux | grep takahasi_test.py | grep -v grep | awk '{print $2}' | while read LINE; do echo $LINE; kill -9 $LINE; done


#start java server
nohup nice -n 19 java -Xmx10G -jar BBM.jar > log.txt &
sleep 1

#start python client
nohup nice -n 19 python -u pommerman/takahasi_test.py > log2.txt &


tail -f log.txt | while IFS= read -r LINE; do printf '%s\n' "$LINE" | iconv -f UTF-8 -t SJIS; done


