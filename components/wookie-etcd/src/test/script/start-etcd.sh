#!/bin/bash
export DIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )
cd $DIR
if [[ ${TRAVIS_PULL_REQUEST}  == "" ]]; then
 which boot2docker
 export boot2docker_installed=$?

 if [[ ${boot2docker_installed} -eq 0 ]]; then
   boot2docker up
   eval "$(boot2docker shellinit)"
   printf "etcd-url=http://`boot2docker ip`:4001" > ../../../target/etcd.properties
 fi
 docker-compose stop
 docker-compose rm --force
 docker-compose up -d etcd
else
 curl -sLo - http://j.mp/install-travis-docker | sh -xe
 nohup ./run "docker-compose up -d etcd && docker-compose ps && date" &
 echo $! > etcd.pid
fi