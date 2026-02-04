# Numpy benchmark port process for benchmarking with the PolyBench harness

## Brief explanation

These PolyBench benchmarks have been manually generated for the file `bench_core.py`  
that was copied from the `numpy` repository.  
Each benchmark file maps 1:1 to a `test_*` method of a `Benchmark` extending class  
inside `bench_core.py` and the files are named following the pattern of `<file>-<class>-<method>.py`  
- e.g. the PolyBench benchmark file `bench_core.Core.time_array_1.py` corresponds to  
the `time_array_1` method of the `Core` class which is defined in the `bench_core.py` file.  

If required, the imported set of benchmarks could be expanded to other numpy benchmarks analogously.

## Version information

Version: 1.26.4  
Source repository: https://github.com/numpy/numpy  

The version was chosen to align with the one used in `graalpython/mx.graalpython/mx_graalpython_python_benchmarks.py::NumPySuite`.

## Files sourced from the numpy project

### bench_core.py
Original file path: numpy/benchmarks/benchmarks/bench_core.py  
Changes made to the original file, as reported by the `diff` tool:
```
1,2c1,2
< from .common import Benchmark
< 
---
> import sys
> import os
3a4,5
> sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
> from common import Benchmark
4a7,8
> # Ensure we have numpy version info in the logs
> print(f"Using numpy version '{np.__version__}'")
```

### common.py
Original file path: numpy/benchmarks/benchmarks/common.py  
No modifications made.

### License.txt
The BSD 3-clause license copied from the numpy repository.

## Original files necessary for benchmarking with the PolyBench harness

### `<file>-<class>-<method>.py` files
These are new files that do not have a corresponding match in the numpy repository.  
They are thus licensed under the Oracle UPL license used elsewhere in this repository.  
These files are generated to conform to the expectations of the PolyBench harness.  
These files were generated with the help of a generative AI model using the following pattern:
```
# Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import sys
import os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

LOOP_COUNT = <LOOP_COUNT_VALUE>
<BENCH_SOURCE_CLASS_INSTANCE> = None


def setup():
    from <BENCH_SOURCE_FILE> import <BENCH_SOURCE_CLASS>
    global <BENCH_SOURCE_CLASS_INSTANCE>
    <BENCH_SOURCE_CLASS_INSTANCE> = <BENCH_SOURCE_CLASS>()
    <BENCH_SOURCE_CLASS_INSTANCE>.setup()


def __benchmark__():
    # Original workload
    <BENCH_SOURCE_CLASS_INSTANCE>.<BENCH_SOURCE_METHOD>()


def run():
    for _ in range(LOOP_COUNT):
        __benchmark__()


def warmupIterations():
    return 0


def iterations():
    return 10


def summary():
    return {
        "name": "OutlierRemovalAverageSummary",
        "lower-threshold": 0.0,
        "upper-threshold": 1.0,
    }


def dependencies():
    # Required alongside this file if copied elsewhere
    return ["<BENCH_SOURCE_FILE>.py", "common.py"]

```
Where:
* `<BENCH_SOURCE_CLASS_INSTANCE>` is a global variable that stores the instance of the benchmark class.
* `<BENCH_SOURCE_FILE>` is the name of the benchmark file (without the extension, e.g. `bench_core`).
* `<BENCH_SOURCE_CLASS>` is the benchmark class (e.g. `Core`).
* `<BENCH_SOURCE_METHOD>` is the benchmark method (e.g. `time_array_1`).
* `<LOOP_COUNT_VALUE>` is a number tweaked to ensure a workload long enough to minimize noise.  
The value was initially set by the AI model and subsequently tweaked by hand.  
The value was tweaked so that the benchmark executes somewhere in the range between 10ms and 1s.

This template is not exhaustive and is provided for the purpose of facilitating maintenance.  
There are multiple edge-cases one has to consider when generating a benchmark file:
* The benchmark class might not implement the `setup` method.
* The benchmark method might require arguments to be passed.  
In such cases, the values were chosen from the `params` field of the benchmark class and stored  
as global constants next to the `LOOP_COUNT` constant.
