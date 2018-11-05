# Second internal Pommerman competition

## Rules of the game

We follow the rules of the FFA competition, as described in https://www.pommerman.com/competitions

The top two winning (namely all) agents from the first competition might also join.

## Submission due

11:59 pm JST, July 23

## How to submit your agent

Push your Dockerfile under this directory of the master branch.

Note that your Dockerfile will be built from `bomberman/pommerman/` directory by

```docker build -t [image_name] -f internal_competition_2/[dockerfile_name] .```

The name of your Dockerfile should be `Dockerfile-xxxxx`.  Please do not use special characters in `xxxxx`.  Ideally, `xxxxx` should indicate who you are.  An example is `Dockerfile-osogami`.

Most typically, your agent source code should also be pushed to this repository.