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

for var in "$@"; do    
    args="$args$(printf "\v")$var"
done

export GRAAL_PYTHON_ARGS=$args
mvn -f $location/pom.xml exec:java -Dexec.mainClass=com.oracle.graal.python.shell.GraalPythonMain -Dorg.graalvm.launcher.executablename=$0