#!/bin/bash
cd ../
# create the services directory if it doesn't exist
if [ ! -d "services" ]; then
    mkdir services
fi

#make sure the script is being used correctly
if [ ! $# == 2 ]; then
    echo "Usage: ./deployService.sh [SERVICE_NAME] [SERVICE_LOCATION]"
    exit
fi

# check if the location is valid for what we are doing
if [ ! -d "$2" ]; then
    echo "Service location $2 does not exist"
    exit
fi
# check if there is a target directory and it is compiled and everything
if [ ! -d "$2/target/lib" ]; then
    echo "Service may not have been compiled, no lib directory detected"
    exit
fi

cd services
# remove the service first
rm -rf $1
echo "deploying new service [$1] from location $2"
# do all the symbolic linking here
mkdir $1
cd $1
ln -s $2/target/lib
cmd="find $2/target -regex '$2/target/$1-.*\.jar'"
jar=$(eval ${cmd})
#echo ${cmd} = ${jar}
ln -s ${jar}
mkdir conf
cd conf
ln -s $2/src/main/resources/application.conf


