#!/bin/bash
export DIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )
cd $DIR
# are we running in travis-ci?
if [[ ${TRAVIS_PULL_REQUEST}  == "" ]]; then
 # check to see if we are using boot2docker
 which boot2docker
 export boot2docker_installed=$?

 if [[ ${boot2docker_installed} -eq 0 ]]; then
   eval "$(boot2docker shellinit)"
 fi

 # manage the docker container
 docker-compose stop
 docker-compose rm --force

else
 # remove when travis is happy with docker
 exit 0

 # running in travis-ci and stop and remove the pid
 kill `cat etcd.pid`
 rm -f docker.started etcd.pid
 echo "Shutdown docker.."
fi
