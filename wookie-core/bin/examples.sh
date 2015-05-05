#!/bin/sh
# This will clean/install all the examples that are in the harness repo
# It will run it all in parallel so the logs will look kind of weird

cd ../../
ROOT_DIR=`pwd`
EXAMPLES_DIR="${ROOT_DIR}/examples"
EXDIRS=`ls -l ${EXAMPLES_DIR} | egrep '^d' | awk '{print $10}'`

if [ "$1" == "build" ]; then
  # build all the components
  for CD in ${EXDIRS}; do
    cd ${EXAMPLES_DIR}/${CD}
    mvn install &
  done
elif [ "$1" == "clean" ]; then
  for CD in ${EXDIRS}; do
    cd ${EXAMPLES_DIR}/${CD}
    mvn clean
    mvn install &
  done
fi
wait