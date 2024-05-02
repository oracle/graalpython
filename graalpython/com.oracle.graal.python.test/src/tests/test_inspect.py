# Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
import inspect
import re
from inspect import Parameter
import hashlib
import posix


TEST_CASES = [
    (SystemExit.__init__, '($self, /, *args, **kwargs)',
              [('self', Parameter.POSITIONAL_ONLY),
              ('args', Parameter.VAR_POSITIONAL),
              ('kwargs', Parameter.VAR_KEYWORD)]),
    (list.append, '($self, object, /)',
              [('self', Parameter.POSITIONAL_ONLY),
              ('object', Parameter.POSITIONAL_ONLY)]),
    (hashlib.md5, '($module, /, string=b\'\', *, usedforsecurity=True)',
              [('string', Parameter.POSITIONAL_OR_KEYWORD),
              ('usedforsecurity', Parameter.KEYWORD_ONLY)]),
    (posix.open, '($module, /, path, flags, mode=511, *, dir_fd=None)',
              [('path', Parameter.POSITIONAL_OR_KEYWORD),
              ('flags', Parameter.POSITIONAL_OR_KEYWORD),
              ('mode', Parameter.POSITIONAL_OR_KEYWORD),
              ('dir_fd', Parameter.KEYWORD_ONLY)]),
    (abs, '($module, x, /)',
              [('x', Parameter.POSITIONAL_ONLY)]),
    (pow, '($module, /, base, exp, mod=None)',
              [('base', Parameter.POSITIONAL_OR_KEYWORD),
              ('exp', Parameter.POSITIONAL_OR_KEYWORD),
              ('mod', Parameter.POSITIONAL_OR_KEYWORD)]),
]


def normalize_signature_text(text):
    # For the time being:
    # GraalPy does not distinguish $self and $module
    # GraalPy does not print default values into the signature text
    return re.sub(r'=[^,)]*', '', text.replace("=()", "")).replace("$module", "$self")


def test_inspect_signature():
    for (fun, expected_signature, expected_params) in TEST_CASES:
        actual = inspect.signature(fun)
        actual_params = [(p.name, p.kind) for p in actual.parameters.values()]
        assert actual_params == expected_params, f"{expected_params !r}\nactual:{actual_params}\nexpect:{expected_params}"
        assert normalize_signature_text(fun.__text_signature__) == normalize_signature_text(expected_signature)


# def _create_test_cases(funs):
#     for funId in funs:
#         fun = eval(funId)
#         signature = inspect.signature(fun)
#         params = [f"('{p.name}', Parameter.{p.kind})" for p in signature.parameters.values()]
#         params = ',\n        '.join(params)
#         text_signature = fun.__text_signature__.replace("'", "\\'")
#         print(f"({funId}, '{text_signature}',\n        [{params}]),")
#
#
# _create_test_cases([
#     'SystemExit.__init__',
#     'hashlib.md5',
#     'posix.open',
#     'abs',
#     'list',
#     'pow'])

