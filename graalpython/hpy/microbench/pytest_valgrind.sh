#!/bin/bash

valgrind --tool=callgrind --instr-atstart=no python3-dbg -m pytest "$@"
