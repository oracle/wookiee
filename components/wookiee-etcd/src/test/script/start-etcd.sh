#!/bin/bash
export DIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )
cd $DIR

if [[ ${TRAVIS_PULL_REQUEST}  == "" ]]; then

 # when on a mac or windows you need to use boot2docker
 which boot2docker
 export boot2docker_installed=$?

 if [[ ${boot2docker_installed} -eq 0 ]]; then
   boot2docker up
   eval "$(boot2docker shellinit)"
   # write out the docker container ip so that we can connect to it in the test cases
   printf "etcd-url=http://`boot2docker ip`:4001" > ../../../target/etcd.properties
 fi

 # manage the docker container for etcd
 docker-compose stop
 docker-compose rm --force
 docker-compose up -d etcd

else
 # remove when travis is happy with docker
 exit 0

 # when using travis-ci we need to run within User Linux Mode.
 curl -sLo - http://j.mp/install-travis-docker | sh -xe
 # start docker in a process and nohup it
 nohup ./run "docker-compose up -d etcd && docker-compose ps && date && touch docker.started" &

 # wait for it to start no more than 20 sec
 i="0"
 while [ ! -f docker.started ] && [ $i -lt 4 ]
 do
  echo "Waiting for docker to start..."
  sleep 5
  i=$[$i+1]
 done
 #save the pid of so we can kill it.
 echo $! > etcd.pid
fi