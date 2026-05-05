# Native Extensions Support

CPython provides a [native extensions API](https://docs.python.org/3/c-api/index.html) for writing Python extensions in C/C++.
GraalPy provides experimental support for this API, which allows many packages like NumPy and PyTorch to work well for many use cases.
The support extends only to the API, not the binary interface (ABI), so extensions built for CPython are not binary compatible with GraalPy.
Packages that use the native API must be built and installed with GraalPy, and the prebuilt wheels for CPython from pypi.org cannot be used.
For best results, it is crucial that you only use the `pip` command that comes preinstalled in GraalPy virtual environments to install packages.
The version of `pip` shipped with GraalPy applies additional patches to packages upon installation to fix known compatibility issues and it is preconfigured to use an additional repository from graalvm.org where we publish a selection of prebuilt wheels for GraalPy.
Please do not update `pip` or use alternative tools such as `uv`.
