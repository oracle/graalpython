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

if [ -z "$JAVA_HOME" ]; then
    JAVA=$JAVA_HOME/bin/java
else
    JAVA=java
fi

for var in "$@"; do    
    args="${args}$(printf "\v")${var}"
done

export GRAAL_PYTHON_ARGS=$args
echo graalpy.sh is going to execute: mvn -f "${location}/pom.xml" exec:exec -Dexec.executable=$JAVA -Dexec.args="--module-path %classpath '-Dorg.graalvm.launcher.executablename=$0' --module org.graalvm.py.launcher/com.oracle.graal.python.shell.GraalPythonMain"
mvn -f "${location}/pom.xml" exec:exec -Dexec.executable="${JAVA}" -Dexec.args="--module-path %classpath '-Dorg.graalvm.launcher.executablename=$0' --module org.graalvm.py.launcher/com.oracle.graal.python.shell.GraalPythonMain"
