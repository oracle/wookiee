#!/bin/bash
# This can clean/install all your components and then push them to the components
# directory for the harness to pick them up
# The logs will look weird because we are building them all in parallel
# You can switch off parallel execution by using the false parameter as the second
# input parameter

# This forces the script to exit if any component fails
set -e

cd ../../
ROOT_DIR=`pwd`
COMPONENT_DIR="${ROOT_DIR}/components"
DEP_COMPONENT_DIR="${ROOT_DIR}/wookie-core/components"
COMPDIRS=`ls -l ${COMPONENT_DIR} | egrep '^d' | awk '{print $NF}'`

if [ "$1" == "build" ]; then
  # build all the components
  for CD in ${COMPDIRS}; do
    cd ${COMPONENT_DIR}/${CD}
    if [ "$2" == "false" ]; then
      mvn install
    else
      mvn install &
    fi
  done
elif [ "$1" == "clean" ]; then
  for CD in ${COMPDIRS}; do
    cd ${COMPONENT_DIR}/${CD}
    mvn clean
    if [ "$2" == "false" ]; then
      mvn install
    else
      mvn install &
    fi
  done
fi
wait

# There are two ways of creating your components, first one would be to do the following:
# 1. Create a directory with the name of the component (match the config)
# 2. link the conf file as a file under the newly created directory
# 3. Create a libs folder that contains all dependent libraries and component jar
#
# Second way is to do the following:
# 1. Link uber jar under components directory
rm -rf ${DEP_COMPONENT_DIR}/*
mkdir -p ${DEP_COMPONENT_DIR}
cd ${DEP_COMPONENT_DIR}
for DIR in ${COMPDIRS}; do
  # Below would cause an issue if version changed, so will need to
  # discover the jar based on some regex pattern
  ln -s ${COMPONENT_DIR}/$DIR/target/${DIR}-1.0-SNAPSHOT-shaded.jar
  #mkdir -p ${DEP_COMPONENT_DIR}/${DIR}
  #cd ${DEP_COMPONENT_DIR}/${DIR}
  #ln -s ${COMPONENT_DIR}/$DIR/target/lib
  #ln -s ${COMPONENT_DIR}/$DIR/src/main/resources/$DIR.conf
done
