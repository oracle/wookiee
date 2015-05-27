#!/bin/bash
export DIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )
cd $DIR
if [[ ${TRAVIS_PULL_REQUEST}  == "" ]]; then
 which boot2docker
 export boot2docker_installed=$?

 if [[ ${boot2docker_installed} -eq 0 ]]; then
   eval "$(boot2docker shellinit)"
 fi

 docker-compose stop
 docker-compose rm --force
else
 ./run "docker-compose stop && docker-compose rm --force && docker-compose ps && date"
fi
