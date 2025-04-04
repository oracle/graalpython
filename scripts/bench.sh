# Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# The Universal Permissive License (UPL), Version 1.0
#
# Subject to the condition set forth below, permission is hereby granted to any
# person obtaining a copy of this software, associated documentation and/or
# data (collectively the "Software"), free of charge and under any and all
# copyright rights in the Software, and any and all patent rights owned or
# freely licensable by each licensor hereunder covering either (i) the
# unmodified Software as contributed to or provided by such licensor, or (ii)
# the Larger Works (as defined below), to deal in both
#
# (a) the Software, and
#
# (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
# one is included with the Software each a "Larger Work" to which the Software
# is contributed by such licensors),
#
# without restriction, including without limitation the rights to copy, create
# derivative works of, display, perform, and distribute the Software and make,
# use, sell, offer for sale, import, export, have made, and have sold the
# Software and the Larger Work(s), and to sublicense the foregoing rights on
# either these or other terms.
#
# This license is subject to the following condition:
#
# The above copyright notice and either this complete permission notice or at a
# minimum a reference to the UPL must be included in all copies or substantial
# portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.

#!/usr/bin/env bash

function info {
	local msg=$1
	echo "-----------------------------------------------------------------------------------------"
	echo $msg
	echo "-----------------------------------------------------------------------------------------"
}

function bench-py-hotspot {
    info "benchmarking GRAALPYTHON with HOTSPOT"
    mx --dynamicimports /compiler benchmark micro-graalpython:*  --  --python-vm  graalpython  --python-vm-config default --jvm server --jvm-config default
}

function bench-py-graal-core {
    info "benchmarking GRAALPYTHON with GRAAL-CORE"
    mx --dynamicimports /compiler benchmark micro-graalpython:*  --  --python-vm  graalpython  --python-vm-config default --jvm server --jvm-config graal-core
}

function bench-cpython {
    info "benchmarking CPYTHON"
    mx benchmark micro-graalpython:*  --  --python-vm cpython  --python-vm-config default
}

function bench-pypy {
    info "benchmarking PYPY"
    mx benchmark micro-graalpython:*  --  --python-vm pypy  --python-vm-config default
}



# read -p "core, enterprise, hotspot, cpython, pypy? " vm
read -p "core, hotspot, cpython, pypy? " vm
case ${vm} in
    'core') bench-py-graal-core;;
    # 'enterprise') bench-py-graal-enterprise;;
    'hotspot') bench-py-hotspot;;
    'cpython') bench-cpython;;
    'pypy') bench-pypy;;
esac
