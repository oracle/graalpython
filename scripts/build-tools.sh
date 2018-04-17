#!/usr/bin/env bash
CWD=`pwd`
cd ../graal-enterprise/tools-enterprise/
mx build 
cd #{CWD}