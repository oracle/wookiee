#!/bin/bash
function setValue {
    name=$1
    value=$2
    #only do anything if the value is true or false
    if [ "$value" = "true" ] || [ "$value" = "false" ]; then
        if [ "$value" = "true" ]; then
            notValue="false"
        else
            notValue="true"
        fi
        sedExp="3 s/enabled[ ]?=[ ]?${notValue}/enabled = ${value}/"
        realpath=`perl -e 'use Cwd "abs_path";print abs_path(shift)' ../components/${name}/${name}.conf`
        sed -i '' -E "${sedExp}" ${realpath}
    fi
}

name="${1%=*}"
value="${1#*=}"
if [ ${name} == "all" ]; then
    DIRS=`ls -l ../components/ | egrep '^d' | awk '{print $10}'`
    for DIR in $DIRS; do
      setValue $DIR $value
    done
else
    for comp in $@; do
        name="${comp%=*}"
        value="${comp#*=}"
        setValue $name $value
    done
fi
