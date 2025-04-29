#!/bin/bash
set -e
ROOT=`pwd` # we expect this script to be run from the repo root

# Allow the caller to override the Python runtime used
PYTHON=${PYTHON:-python3}

_install_hpy() {
    echo "Installing hpy"
    # at the moment this install hpy.devel and hpy.universal. Eventually, we
    # will want to split those into two separate packages
    local PYTHON="$1"
    pushd ${ROOT}
    ${PYTHON} -m pip install -U pip
    ${PYTHON} -m pip install wheel
    ${PYTHON} -m pip install .
    popd
}

_test_pof() {
    echo "==== testing pof ===="
    # this assumes that pof is already installed, e.g. after calling
    # wheel or setup_py_install
    ${PYTHON} -m pip install pytest pytest-azurepipelines
    cd proof-of-concept
    ${PYTHON} -m pytest
}

_build_wheel() {
    HPY_ABI="$1"
    local VENV="venv/wheel_builder_$HPY_ABI"
    # we use this venv just to build the wheel, and then we install the wheel
    # in the currently active virtualenv
    echo "Create venv: $VENV"
    ${PYTHON} -m venv "$VENV"
    local PY_BUILDER="`pwd`/$VENV/bin/python3"
    if [ -x "`pwd`/$VENV/Scripts/python.exe" ]
    then
        # Set the correct python executable for Windows
        PY_BUILDER="`pwd`/$VENV/Scripts/python.exe"
    fi
    echo
    echo "Installing hpy and requirements"
    _install_hpy ${PY_BUILDER}
    pushd proof-of-concept
    ${PY_BUILDER} -m pip install -r requirements.txt
    echo
    echo "Building wheel"
    ${PY_BUILDER} setup.py --hpy-abi="$HPY_ABI" bdist_wheel
    popd
}

_myrm() {
    for path in "$@"
    do
        if [ -d "$path" -o -f "$path" ]
        then
            echo "rm $path"
            rm -rf "$path"
        else
            echo "skipping $path"
        fi
    done
}

clean() {
    echo "=== cleaning up old stuff ==="
    _myrm ${ROOT}/venv/wheel_builder_{cpython,universal}
    _myrm ${ROOT}/venv/wheel_runner_{cpython,universal}
    _myrm ${ROOT}/venv/setup_py_install_{cpython,universal}
    _myrm ${ROOT}/venv/setup_py_build_ext_inplace_{cpython,universal}
    _myrm ${ROOT}/build
    _myrm ${ROOT}/proof-of-concept/build
    _myrm ${ROOT}/proof-of-concept/dist
    # remove files written by build_ext --inplace
    _myrm ${ROOT}/proof-of-concept/pof*{.so,.py}
    _myrm ${ROOT}/proof-of-concept/pofpackage/foo*{.so,.py}
    _myrm ${ROOT}/proof-of-concept/pofcpp*{.so,.py}
    _myrm ${ROOT}/proof-of-concept/pofpackage/bar*{.so,.py}
    echo
}

wheel() {
    # build a wheel, install and test
    HPY_ABI="$1"
    local VENV="venv/wheel_runner_$HPY_ABI"
    clean
    echo "=== testing setup.py bdist_wheel" $HPY_ABI "==="
    _build_wheel "$HPY_ABI"
    WHEEL=`ls proof-of-concept/dist/*.whl`
    echo "Wheel created: ${WHEEL}"
    echo
    echo "Create venv: $VENV"
    ${PYTHON} -m venv "$VENV"
    if [ -e "$VENV/bin/activate" ] ; then
        source "$VENV/bin/activate"
    else
        source "$VENV/Scripts/activate"
    fi
    _install_hpy ${PYTHON}
    echo "Installing wheel"
    ${PYTHON} -m pip install $WHEEL
    echo
    _test_pof
}

setup_py_install() {
    # install proof-of-concept using setup.py install and run tests
    HPY_ABI="$1"
    VENV="venv/setup_py_install_$HPY_ABI"
    clean
    echo "=== testing setup.py --hpy-abi=$HPY_ABI install ==="
    echo "Create venv: $VENV"
    ${PYTHON} -m venv "$VENV"
    if [ -e "$VENV/bin/activate" ] ; then
        source "$VENV/bin/activate"
    else
        source "$VENV/Scripts/activate"
    fi
    _install_hpy ${PYTHON}
    echo
    echo "Running setup.py"
    pushd proof-of-concept
    ${PYTHON} setup.py --hpy-abi="$HPY_ABI" install
    popd
    echo
    _test_pof
}

setup_py_build_ext_inplace() {
    # install proof-of-concept using setup.py install and run tests
    HPY_ABI="$1"
    VENV="venv/setup_py_build_ext_inplace_$HPY_ABI"
    clean
    echo "=== testing setup.py --hpy-abi=$HPY_ABI build_ext --inplace ==="
    echo "Create venv: $VENV"
    ${PYTHON} -m venv "$VENV"
    if [ -e "$VENV/bin/activate" ] ; then
        source "$VENV/bin/activate"
    else
        source "$VENV/Scripts/activate"
    fi
    _install_hpy ${PYTHON}
    echo
    echo "Running setup.py"
    pushd proof-of-concept
    echo python is $(which ${PYTHON})
    ${PYTHON} setup.py --hpy-abi="$HPY_ABI" build_ext --inplace
    popd
    echo
    _test_pof
}

# ======== main code =======

# validate arguments
if [[ "$#" -lt 1  || ( "$#" -lt 2  && "$1" != "clean") ]]; then
  echo "Usage: $0 COMMAND [TARGET_ABI]" >&2
  echo "Commands:" >&2
  echo "  wheel TARGET_ABI: build a wheel, install and test"
  echo "  setup_py_install TARGET_ABI: install poc using 'setup.py install' & run tests" >&2
  echo "  clean: clean build artifacts" >&2
  echo "Target ABIs:" >&2
  echo "  universal: Binary intended for any Python implementation" >&2
  echo "  cpython : Binary optimized for CPython" >&2
  exit 1
fi

if [ ! -d "proof-of-concept" ] ; then
    echo "Script must be run in the repo root" >&2
    exit 1
fi

# call the function mentioned as the first arg
COMMAND="$1"
shift
$COMMAND "$@"
