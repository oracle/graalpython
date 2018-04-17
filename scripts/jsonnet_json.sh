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

echo "${CIFILE}.jsonnet --> ${CIJSON}.json"
jsonnet ${CIFILE}.jsonnet > ${CIJSON}.json

