#!/bin/sh
# the seednode should expose a port 5120 which allows the btc node to call it when a new block is found
docker exec -it seednode ss -l | grep 5120
