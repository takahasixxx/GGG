## complile

```
python -m compileall run_my_agent.py
```

This generates the bytecode `__pycache__/source.cpython-36.pyc`

If you want, the bytecode can be decompiled into the Python source code by

```
pip install uncompyle6
uncompyle6 __pycache__/source.cpython-36.pyc
```

You might also want to obfuscate the code by

```
pip install pyminifier
pyminifier --nominify --obfuscate --nonlatin run_my_agent.py
```

This unfortunately erroniously renames the act() function, which
override the parrent function.  Once this is manually fixed, it works
fine.

## build docker

docker build -t pommerman/ffa-competition-agent -f Dockerfile .
