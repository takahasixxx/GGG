#!/bin/sh
#################################
# (C) Copyright IBM Corp. 2018
#################################


python3 -m venv myenv

source myenv/bin/activate

pip install -r pommerman_v3/requirements.txt 

python --version
pip --version

deactivate
