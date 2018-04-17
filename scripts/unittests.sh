#!/bin/bash

cd "$(dirname $0)"
cd ..
mx python3 graalpython/com.oracle.graal.python.test/src/graalpytest.py -v graalpython/com.oracle.graal.python.test/src/tests/
