#!/usr/bin/env bash

function info {
	local msg=$1
	echo "-----------------------------------------------------------------------------------------"
	echo $msg
	echo "-----------------------------------------------------------------------------------------"
}

function bench-py-hotspot {
    info "benchmarking GRAALPYTHON with HOTSPOT"
    mx --dynamicimports /graal-enterprise benchmark micro-graalpython:*  --  --python-vm  graalpython  --python-vm-config default --jvm server --jvm-config default
}

function bench-py-graal-core {
    info "benchmarking GRAALPYTHON with GRAAL-CORE"
    mx --dynamicimports /graal-enterprise benchmark micro-graalpython:*  --  --python-vm  graalpython  --python-vm-config default --jvm server --jvm-config graal-core
}

function bench-py-graal-enterprise {
    info "benchmarking GRAALPYTHON with GRAAL-ENTERPRISE"
    mx --dynamicimports /graal-enterprise benchmark micro-graalpython:*  --  --python-vm  graalpython  --python-vm-config default --jvm server --jvm-config graal-enterprise
}

function bench-cpython {
    info "benchmarking CPYTHON"
    mx benchmark micro-graalpython:*  --  --python-vm cpython  --python-vm-config default
}

function bench-pypy {
    info "benchmarking PYPY"
    mx benchmark micro-graalpython:*  --  --python-vm pypy  --python-vm-config default
}



echo "If you are running this script for the first time, you may consider setting up your local env with <setup_bench_local.sh>"

read -p "core, enterprise, hotspot, cpython, pypy? " vm
case ${vm} in
    'core') bench-py-graal-core;;
    'enterprise') bench-py-graal-enterprise;;
    'hotspot') bench-py-hotspot;;
    'cpython') bench-cpython;;
    'pypy') bench-pypy;;
esac
