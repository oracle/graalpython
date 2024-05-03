# Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

psutil = BuildSpec(name="psutil")
numpy = BuildSpec(
    name="numpy",
    platforms=[Linux, Mac, Windows],
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
    name="pillow",
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
        Linux: ["gcc-toolset-12-gcc-gfortran", "openblas-devel"],
        Mac: ["gcc", "openblas", "pkg-config"],
    },
    before_build={
        Linux: [
            "export FFLAGS=-fallow-argument-mismatch",
        ],
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
        Linux: [
            "export FFLAGS=-fallow-argument-mismatch",
        ],
        Mac: [
            "export PKG_CONFIG_PATH=/opt/homebrew/opt/openblas/lib/pkgconfig",
            "export FFLAGS=-fallow-argument-mismatch",
        ],
    },
)
cffi = BuildSpec(
    name="cffi",
    before_build=[
        "graalpy/bin/graalpy -m pip install wheel",
    ],
    system_dependencies={
        Linux: ["libffi-devel"],
        Mac: ["libffi"],
    },
)
pyyaml = BuildSpec(
    name="PyYAML",
    platforms=[Linux, Mac, Windows],
)
cmake = BuildSpec(name="cmake")
BuildSpec(
    name="ujson",
    platforms=[Linux, Mac, Windows],
)
BuildSpec(
    name="torch",
    spec_dependencies=[numpy, ninja, cmake, pybind11, cffi, pyyaml],
    system_dependencies={
        Linux: ["openblas-devel", "/usr/bin/cmake", "/usr/bin/sudo", "libffi-devel"],
        Mac: ["openblas", "cmake", "libffi"],
    },
    before_build={
        Linux: [
            "export USE_CUDA=0",
        ],
        Mac: [
            "export USE_CUDA=0",
            "export PKG_CONFIG_PATH=/opt/homebrew/opt/openblas/lib/pkgconfig",
        ],
    },
    environment={
        "MAX_JOBS": 4,
        "BUILD_TEST": 0,
    },
)
opt_einsum = BuildSpec(
    name="opt_einsum",
    spec_dependencies=[numpy],
    platforms=[LinuxX86],
)
keras_preprocessing = BuildSpec(
    name="Keras_Preprocessing",
    spec_dependencies=[numpy],
    platforms=[LinuxX86],
)
grpcio = BuildSpec(
    name="grpcio",
    platforms=[LinuxX86],
)
ml_dtypes = BuildSpec(
    name="ml_dtypes",
    platforms=[LinuxX86],
)
wrapt = BuildSpec(
    name="wrapt",
    platforms=[LinuxX86],
)
h5py = BuildSpec(
    name="h5py",
    platforms=[LinuxX86],
    system_dependencies={
        Linux: ["hdf5-devel"],
    },
)
BuildSpec(
    name="tensorflow",
    platforms=[LinuxX86],
    spec_dependencies=[grpcio, psutil, wrapt, ml_dtypes, h5py, numpy, opt_einsum, keras_preprocessing],
    before_build=[
        "export PIP_FIND_LINKS=$(pwd)",
        "pip install pip numpy wheel packaging requests opt_einsum",
        "pip install keras_preprocessing --no-deps",
        "curl -L https://github.com/bazelbuild/bazel/releases/download/6.4.0/bazel-6.4.0-linux-x86_64 -o $(pwd)/graalpy/bin/bazel",
        "chmod +x graalpy/bin/bazel",
        "export PATH=$(pwd)/graalpy/bin/:$PATH",
        "bazel --version",
    ],
    system_dependencies=[
        "openblas-devel", "/usr/bin/cmake", "/usr/bin/sudo", "/usr/bin/curl", "java-11-openjdk-devel"
    ],
)


if __name__ == "__main__":
    import os
    from wheelbuilder.generator import generate

    workflow_directory = os.path.join(
        os.path.dirname(__file__), "..", "..", ".github", "workflows"
    )
    generate(workflow_directory)
