#!/bin/bash

echo "Changing Scala Version to $1, and Scala Artifact Version to $2"

sed -i "s/<scala\.version>.*</<scala\.version>$1</" wookiee-core/pom.xml
sed -i "s/<scala\.artifact\.version>.*</<scala\.artifact\.version>$2</" wookiee-core/pom.xml

sed -i "s/<scala\.version>.*</<scala\.version>$1</" wookiee-test/pom.xml
sed -i "s/<scala\.artifact\.version>.*</<scala\.artifact\.version>$2</" wookiee-test/pom.xml
