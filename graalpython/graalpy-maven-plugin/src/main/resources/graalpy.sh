#!/usr/bin/env bash

source="${BASH_SOURCE[0]}"
while [ -h "$source" ] ; do
    prev_source="$source"
    source="$(readlink "$source")";
    if [[ "$source" != /* ]]; then
        # if the link was relative, it was relative to where it came from
        dir="$( cd -P "$( dirname "$prev_source" )" && pwd )"
        source="$dir/$source"
    fi
done
location="$( cd -P "$( dirname "$source" )" && pwd )"

if [ -z "$MVN" ]; then
    MVN=mvn
fi

args="$(printf "\v")--python.Executable=$0"
for var in "$@"; do
    args="${args}$(printf "\v")${var}"
done

curdir=`pwd`
export GRAAL_PYTHON_ARGS="${args}$(printf "\v")"
$MVN -f "${location}/../pom.xml" graalpy:exec -Dexec.workingdir="${curdir}"
