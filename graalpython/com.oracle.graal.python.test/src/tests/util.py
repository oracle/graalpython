# Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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
import subprocess
import sys
import time
import unittest

IS_BYTECODE_DSL = sys.implementation.name == 'graalpy'
TRANSIENT_GRAALPY_STARTUP_BLOCKING_IO = "ERROR: BlockingIOError: [Errno 11] Resource temporarily unavailable"


def _is_sandboxed():
    return (
        sys.implementation.name == 'graalpy' and
        __graalpython__.posix_module_backend() == 'java' and
        __graalpython__.sha3_module_backend() == 'java' and
        __graalpython__.pyexpat_module_backend() == 'java'
    )


def skip_if_sandboxed(reason=''):
    def wrapper(test):
        if _is_sandboxed():
            return unittest.skip(f"Skipped in sandboxed configuration. {reason}")(test)
        return test
    return wrapper


def is_native_compression_backend():
    return sys.implementation.name != 'graalpy' or __graalpython__.zlib_module_backend() == 'native'


def _jdk_major_version():
    version = __graalpython__.get_jdk_version()
    return int(version.split(".", 1)[0].split("-", 1)[0])


def has_capi():
    return not (sys.implementation.name == 'graalpy' and _jdk_major_version() < 25)


def needs_capi(test):
    if not has_capi():
        return unittest.skip("Needs C API support on JDK 25 or newer")(test)
    return test


def skipIfBytecodeDSL(reason=''):
    def wrapper(test):
        if IS_BYTECODE_DSL:
            return unittest.skip(f"Skipped on Bytecode DSL interpreter. {reason}")(test)
        return test
    return wrapper


def skipUnlessBytecodeDSL(reason=''):
    def wrapper(test):
        return test
    return wrapper


def storage_to_native(s):
    if sys.implementation.name == 'graalpy':
        assert hasattr(__graalpython__, 'storage_to_native'), "Needs to be run with --python.EnableDebuggingBuiltins"
        __graalpython__.storage_to_native(s)


def assert_raises(err, fn, *args, err_check=None, **kwargs):
    raised = False
    try:
        fn(*args, **kwargs)
    except err as e:
        raised = True
        if err_check:
            if isinstance(err_check, str):
                assert err_check in str(e), f"Substring '{err_check}' not found in '{str(e)}'"
            else:
                assert err_check(e)
    assert raised


def _contains_transient_graalpy_startup_blocking_io(output):
    if output is None:
        return False
    if isinstance(output, bytes):
        return TRANSIENT_GRAALPY_STARTUP_BLOCKING_IO.encode() in output
    return TRANSIENT_GRAALPY_STARTUP_BLOCKING_IO in output


def run_subprocess_with_graalpy_startup_retry(args, *, attempts=5, retry_delay=0.2, **kwargs):
    unsupported_kwargs = {"stdout", "stderr", "capture_output"} & kwargs.keys()
    if unsupported_kwargs:
        raise TypeError(f"unsupported keyword arguments: {', '.join(sorted(unsupported_kwargs))}")
    check = kwargs.pop("check", False)
    for attempt in range(attempts):
        result = subprocess.run(args, stdout=subprocess.PIPE, stderr=subprocess.PIPE, **kwargs)
        if result.returncode == 0 or not (
            _contains_transient_graalpy_startup_blocking_io(result.stdout) or
            _contains_transient_graalpy_startup_blocking_io(result.stderr)
        ):
            break
        if attempt + 1 < attempts:
            time.sleep(retry_delay)
            retry_delay *= 2
    if check and result.returncode != 0:
        raise subprocess.CalledProcessError(result.returncode, result.args, output=result.stdout, stderr=result.stderr)
    return result
