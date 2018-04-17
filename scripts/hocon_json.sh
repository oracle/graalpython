#!/usr/bin/env bash
if [ -z $1 ]; then
    CIFILE="ci"
else
    CIFILE="$1"
fi

if [ -z $2 ]; then
    CIJSON=${CIFILE}
else
    CIJSON="$2"
fi

echo "${CIFILE}.hocon --> ${CIJSON}.json"
pyhocon -i ${CIFILE}.hocon -f json -o ${CIJSON}.json
