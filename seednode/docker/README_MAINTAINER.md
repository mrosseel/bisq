# Day to day operation

- Look at running containers
> docker ps

expected: 4 running containers (seednode, btcd, collectd, nginx).

- Look at the BTCD logs
> ./btc_logs.sh

- Look at the seednode logs
> ./seednode_logs.sh


- Start missing containers
> cd ~/bisq/seednode
> docker-compose up -d
 
- Restart a specific container
> cd ~/bisq/seednode
> docker-compose restart seednode

- Location of the logs, bisq protobuffer files, ...
> cd ~/bisq/seednode/local


# Upgrading to a new release

- install a new release
> cd ~/bisq/seednode

edit the '.env' file and set the SEEDNODE_BRANCH variable to either a branch name or a commit hash.
Then issue these commands:

> docker-compose build seednode
> docker-compose up -d


# Known issues

if btcd can't notify the seednode, it's because the seednode's port 5120 has died.
The temp fix is to restart the seednode, but make sure the btcd is fully running first.

