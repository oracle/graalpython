# Copyright (c) 2018, Oracle and/or its affiliates.
#
# The Universal Permissive License (UPL), Version 1.0
#
# Subject to the condition set forth below, permission is hereby granted to any
# person obtaining a copy of this software, associated documentation and/or data
# (collectively the "Software"), free of charge and under any and all copyright
# rights in the Software, and any and all patent rights owned or freely
# licensable by each licensor hereunder covering either (i) the unmodified
# Software as contributed to or provided by such licensor, or (ii) the Larger
# Works (as defined below), to deal in both
#
# (a) the Software, and
# (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
#     one is included with the Software (each a "Larger Work" to which the
#     Software is contributed by such licensors),
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

# ----------------------------------------------------------------------------------------------------------------------
#
# the exceptions / errors
#
# ----------------------------------------------------------------------------------------------------------------------
class SystemExit(BaseException):
    def __init__(self, *args):
        if len(args) > 1:
            self.code = args
        elif len(args) == 1:
            self.code = args[0]
        else:
            self.code = 0
        BaseException.__init__(self, *args)


class KeyboardInterrupt(BaseException):
    pass


class GeneratorExit(BaseException):
    pass


class Exception(BaseException):
    pass


class StopIteration(Exception):
    pass


class ArithmeticError(Exception):
    pass


class FloatingPointError(ArithmeticError):
    pass


class OverflowError(ArithmeticError):
    pass


class ZeroDivisionError(ArithmeticError):
    pass


class AssertionError(Exception):
    pass


class AttributeError(Exception):
    pass


class BufferError(Exception):
    pass


class EOFError(Exception):
    pass


class ImportError(Exception):
    def __init__(self, msg, name=None, path=None):
        self.message = msg
        self.name = name
        self.path = path


class ModuleNotFoundError(ImportError):
    def __init__(self, msg, name=None):
        self.msg = msg
        self.name = name

    def __str__(self):
        if self.name is not None:
            return "ModuleNotFound: '" + self.name + "'. " + self.msg
        else:
            return "ModuleNotFound: " + self.msg


class LookupError(Exception):
    pass


class IndexError(LookupError):
    pass


class KeyError(LookupError):
    pass


class MemoryError(Exception):
    pass


class NameError(Exception):
    pass


class UnboundLocalError(NameError):
    pass


class OSError(Exception):
    pass

class IOError(Exception):
    pass


# TODO all the OS errors

class ReferenceError(Exception):
    pass


class RuntimeError(Exception):
    pass


class NotImplementedError(Exception):
    pass


class SyntaxError(Exception):
    pass


class IndentationError(SyntaxError):
    pass


class TabError(IndentationError):
    pass


class SystemError(Exception):
    pass


class TypeError(Exception):
    pass


class ValueError(Exception):
    pass


class UnicodeError(ValueError):
    pass


class UnicodeDecodeError(UnicodeError):
    pass


class UnicodeEncodeError(UnicodeError):
    pass


class UnicodeTranslateError(UnicodeError):
    pass


# ----------------------------------------------------------------------------------------------------------------------
#
# the warnings
#
# ----------------------------------------------------------------------------------------------------------------------
class Warning(Exception):
    pass


class BytesWarning(Warning):
    pass


class DeprecationWarning(Warning):
    pass


class FutureWarning(Warning):
    pass


class ImportWarning(Warning):
    pass


class PendingDeprecationWarning(Warning):
    pass


class ResourceWarning(Warning):
    pass


class RuntimeWarning(Warning):
    pass


class SyntaxWarning(Warning):
    pass


class UnicodeWarning(Warning):
    pass


class UserWarning(Warning):
    pass
