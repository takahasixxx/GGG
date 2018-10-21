# First interal Pommerman competition

## Rules of the game

We follow the rules of the FFA competition, as described in https://www.pommerman.com/competitions

## Submission due

11:59 pm JST, June 21

## How to submit your agent

Push your Dockerfile under this directory of the master branch.

Note that your Dockerfile will be built from `bomberman/pommerman/` directory by

```docker build -t [image_name] -f internal_competition_1/[dockerfile_name] .```

The name of your Dockerfile should be `Dockerfile-xxxxx`.  Please do not use special characters in `xxxxx`.  Ideally, `xxxxx` should indicate who you are.  An example is `Dockerfile-osogami`.

Most typically, your agent source code should also be pushed to this repository.

## Results

```
   agent | win | loss | win rate
----------------------------------
osogami2 | 923 | 3216 | 0.223
 osogami | 836 | 3303 | 0.202
    yszm | 785 | 3354 | 0.190
   yszm2 | 730 | 3409 | 0.176
```