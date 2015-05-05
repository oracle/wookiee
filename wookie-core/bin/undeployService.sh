#!/bin/bash
cd ../
# create the services directory if it doesn't exist
if [ ! -d "services" ]; then
    mkdir services
fi

#make sure the script is being used correctly
if [ ! $# == 1 ]; then
    echo "Usage: ./undeployService.sh [SERVICE_NAME]"
    exit
fi

echo "undeploying service [$1] from current dev harness"
# just need to delete the directory
rm -rf services/$1