#!/bin/bash

function required() {
    command -v "$1" >/dev/null 2>&1 || { echo >&2 "I require $1 but it's not installed.  Aborting."; exit 1; }
}

buildsbt="build.sbt"
if [ -f "$buildsbt" ]
then
echo "$buildsbt found. Rood dir"
else
echo "$buildsbt not found. Not in the root directorry"
exit 1
fi

required docker-compose
required sbt
required java
required unzip

# Start DB
docker-compose up -d
# Build app
sbt dist
# Run the sulution
cd target/universal
unzip evolutiongamingtest-0.1.zip
cd evolutiongamingtest-0.1
./bin/evolutiongamingtest -Dconfig.resource=prod-application.conf