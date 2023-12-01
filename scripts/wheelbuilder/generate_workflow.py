# Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

from wheelbuilder import (
    BuildSpec,
    Linux,
    LinuxX86,
    LinuxARM,
    Mac,
    MacX86,
    MacARM,
    Windows,
    WindowsX86,
)

BuildSpec(name="psutil")
numpy = BuildSpec(
    name="numpy",
    extra_versions=["1.21.6", "1.22.4", "1.23.1", "1.23.4"],
    system_dependencies={
        Linux: ["gcc-toolset-12-gcc-gfortran", "openblas-devel"],
        Mac: ["gcc", "openblas"],
    },
)
BuildSpec(
    name="pandas",
    system_dependencies=["openblas"],
    spec_dependencies=[numpy],
)
pybind11 = BuildSpec(name="pybind11")
ninja = BuildSpec(name="ninja")
pillow = BuildSpec(
    name="Pillow",
    system_dependencies={
        Linux: [
            "libtiff-devel",
            "libjpeg-devel",
            "openjpeg2-devel",
            "zlib-devel",
            "freetype-devel",
            "lcms2-devel",
            "libwebp-devel",
        ],
        Mac: ["libjpeg", "libtiff", "little-cms2", "openjpeg", "webp"],
    },
)
contourpy = BuildSpec(
    name="contourpy",
    spec_dependencies=[ninja, pybind11, numpy],
)
kiwisolver = BuildSpec(
    name="kiwisolver",
    spec_dependencies=[pybind11],
)
BuildSpec(
    name="matplotlib",
    spec_dependencies=[pillow, kiwisolver, numpy, contourpy],
    system_dependencies=["openblas"],
)
scipy = BuildSpec(
    name="scipy",
    spec_dependencies=[numpy],
    system_dependencies={
        Linux: ["gcc-toolset-9", "gcc-toolset-9-gcc-gfortran", "openblas-devel"],
        Mac: ["gcc", "openblas", "pkg-config"],
    },
    before_build={
        Linux: ["source /opt/rh/gcc-toolset-9/enable"],
        Mac: [
            "export PKG_CONFIG_PATH=/opt/homebrew/opt/openblas/lib/pkgconfig",
            "export FFLAGS=-fallow-argument-mismatch",
        ],
    },
)
BuildSpec(
    name="scikit-learn",
    spec_dependencies=[numpy, scipy],
    system_dependencies=["openblas"],
    before_build={
        Mac: [
            "export PKG_CONFIG_PATH=/opt/homebrew/opt/openblas/lib/pkgconfig",
            "export FFLAGS=-fallow-argument-mismatch",
        ],
    },
)
cffi = BuildSpec(
    name="cffi",
    system_dependencies={
        Linux: ["libffi-devel"],
        Mac: ["libffi"],
    },
)
pyyaml = BuildSpec(name="PyYAML")
cmake = BuildSpec(name="cmake")
BuildSpec(
    name="torch",
    spec_dependencies=[numpy, ninja, cmake, pybind11, cffi, pyyaml],
    system_dependencies={
        Linux: ["openblas-devel", "/usr/bin/cmake", "/usr/bin/sudo"],
        Mac: ["openblas", "cmake"],
    },
    before_build={
        Mac: [
            "export USE_CUDA=0",
            "export PKG_CONFIG_PATH=/opt/homebrew/opt/openblas/lib/pkgconfig",
        ],
    },
    custom_steps=[
        {
            "uses": "Jimver/cuda-toolkit@v0.2.11",
            "id": "cuda-toolkit",
            "if": "runner.os != 'macOS'",
            "with": {
                "cuda": "11.7.0",
            },
        }
    ],
    environment={
        "MAX_JOBS": 4,
        "BUILD_TEST": 0,
    },
)


if __name__ == "__main__":
    import os
    from wheelbuilder.generator import generate

    workflow_directory = os.path.join(
        os.path.dirname(__file__), "..", "..", ".github", "workflows"
    )
    generate(workflow_directory)
