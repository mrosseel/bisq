# Bisq Seed Node

<<<<<<< HEAD
## Running using systemd

* Install bisq-seednode.service in /etc/systemd/system
* Install bisq-seednode in /etc/default
* Install blocknotify.sh in bitcoind's ~/.bitcoin/ folder and chmod 700 it
* Modify the executable paths and configuration as necessary
* Then you can do:


=======
## Hardware

Highly recommended to use SSD! Minimum specs:

* CPU: 4 cores
* RAM: 8 GB
* SSD: 512 GB (HDD is too slow)

## Software

The following OS's are known to work well:

* Ubuntu 18.04
* Ubuntu 20.04
* FreeBSD 12

### Installation

Start with a clean Ubuntu server installation, and run the script
```bash
curl -s https://raw.githubusercontent.com/bisq-network/bisq/master/seednode/install_seednode_debian.sh | sudo bash
>>>>>>> a54eeeabccd1c48bdfc03dc3313ffb983a921325
```

This will install and configure Tor, Bitcoin, and Bisq-Seednode services to start on boot.

### Firewall

Next, configure your OS firewall to only allow SSH and Bitcoin P2P
```bash
ufw allow 22/tcp
ufw allow 8333/tcp
ufw enable
```

### Syncing

After installation, watch the Bitcoin blockchain sync progress
```bash
sudo tail -f /bitcoin/debug.log
```

After Bitcoin is fully synced, start the bisq service
```bash
sudo systemctl start bisq
sudo journalctl --unit bisq --follow
```

After Bisq is fully synced, check your Bitcoin and Bisq onion hostnames:
```bash
sudo -H -u bitcoin bitcoin-cli getnetworkinfo|grep address
sudo cat /bisq/bisq-seednode/btc_mainnet/tor/hiddenservice/hostname
```

### Testing

After your Bisq seednode is ready, test it by connecting to your new btcnode and bisq!

macOS:
```bash
/Applications/Bisq.app/Contents/MacOS/Bisq --seedNodes=foo.onion:8000 --btcNodes=foo.onion:8333
```

### Monitoring

If you run a main seednode, you also are obliged to activate the monitoring feed by running

```bash
bash <(curl -s https://raw.githubusercontent.com/bisq-network/bisq/master/monitor/install_collectd_debian.sh)
```
Follow the instruction given by the script and report your certificate to the seednode group!

### Upgrading

To upgrade your seednode to a new tag, for example v1.2.5
```bash
sudo -u bisq -s
cd bisq
git fetch origin
git checkout v1.2.5 # new tag
./gradlew clean build -x test
exit
sudo service bisq restart
sudo journalctl --unit bisq --follow
```

### Uninstall

If you need to start over, you can run the uninstall script in this repo
```bash
sudo ./delete_seednode_debian.sh
```
<<<<<<< HEAD


## Running using docker (experimental)

The production docker image checks out its own copy of the bisq source code, 
Here's the checklist to start your docker seednode:

- create a `.env` file in the seednode directory. 
This file will be used as variables by docker-compose.yml which is located in the same directory.

```
ONION_ADDRESS=my_onion_address
APP_NAME=seed_BTC_MAINNET_my_onion_address
WORKING_DIR=/root/.local/share/seed_BTC_MAINNET_rm7b56wbrcczpjvl/
TOR_HIDDEN_SERVICE_DIR=/root/.local/share/seed_BTC_MAINNET_5quyxpxheyvzmb2d/btc_mainnet/tor/hiddenservice
RPC_PORT=8332
RPC_USER=some_user
RPC_PASSWORD=some_password
BTC_DATA_DIR=/home/some_user/.bitcoin/
SEEDNODE_URL=https://github.com/bisq-network/bisq.git
SEEDNODE_BRANCH=master
```

For the SEEDNODE_BRANCH value not only a branch name is allowed, a commit hash works as well

The WORKING_DIR maps the ./local directory to the location where the seednode docker container stores the logs, 
tor private keys, ... . Best is to start the container and then copy your own private keys into this directory.

- start your seednode and bitcoin full node:

```
docker-compose up -d
```

- perform 'docker ps' and 'docker logs seednode' and 'docker logs btcd' to make sure everything is working correctly.

### Known issues

- at startup, the seednode can try to call the bitcoin api to get some information. If the full node hasn't 
finished starting up this will result in an error and the btcli process will be killed. Simply restarting 
the seednode once btcd is running will fix this. (note: this is a bug in the seednode)

### TODO

- fix startup bug
- publish image
- remove hardcoded docker ip addresses (it works, but might be done prettier)
=======
WARNING: this script will delete all data!

>>>>>>> a54eeeabccd1c48bdfc03dc3313ffb983a921325
