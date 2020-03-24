#!/bin/bash

echo "Changing Scala Version to $1, and Scala Artifact Version to $2"

sed -i "s/<scala\.version>.*</<scala\.version>$1</" pom.xml
sed -i "s/<scala\.artifact\.version>.*</<scala\.artifact\.version>$2</" pom.xml
