# Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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

# ----------------------------------------------------------------------------------------------------------------------
#
# the exceptions / errors
#
# ----------------------------------------------------------------------------------------------------------------------

def UnicodeEncodeError__init__(self, encoding, object, start, end, reason):
    BaseException.__init__(self, encoding, object, start, end, reason)
    self.encoding = encoding
    self.object = object
    self.start = start
    self.end = end
    self.reason = reason


def UnicodeEncodeError__str__(self):    
    if not hasattr(self, 'object'):
        return BaseException.__str__(self)
    if self.start < len(self.object) and self.start + 1 == self.end:
        badchar = ord(self.object[self.start])
        if badchar <= 0xff:
            fmt = "'%s' codec can't encode character '\\x%02x' in position %d: %s"
        elif badchar <= 0xffff:
            fmt = "'%s' codec can't encode character '\\u%04x' in position %d: %s"
        else:
            fmt = "'%s' codec can't encode character '\\U%08x' in position %d: %s"
        return fmt % (self.encoding, badchar, self.start, self.reason)
    return "'%s' codec can't encode characters in position %d-%d: %s" % (self.encoding, self.start, self.end - 1, self.reason)


UnicodeEncodeError.__init__ = UnicodeEncodeError__init__
UnicodeEncodeError.__str__ = UnicodeEncodeError__str__
del UnicodeEncodeError__init__
del UnicodeEncodeError__str__


def UnicodeDecodeError__init__(self, encoding, object, start, end, reason):
    BaseException.__init__(self, encoding, object, start, end, reason)
    self.encoding = encoding
    if isinstance(object, bytes):
        self.object = object
    else:
        self.object = bytes(object)
    self.start = start
    self.end = end
    self.reason = reason

def UnicodeEncodeError__init__(self, encoding, object, start, end, reason):
    BaseException.__init__(self, encoding, object, start, end, reason)
    self.encoding = encoding
    self.object = object
    self.start = start
    self.end = end
    self.reason = reason


def UnicodeDecodeError__str__(self):
    if not hasattr(self, 'object'):
        return BaseException.__str__(self)
    if self.start < len(self.object) and self.start + 1 == self.end:
        byte = self.object[self.start]
        return "'%s' codec can't decode byte 0x%02x in position %d: %s" % (self.encoding, byte, self.start, self.reason)
    return "'%s' codec can't decode bytes in position %d-%d: %s" % (self.encoding, self.start, self.end - 1, self.reason)


UnicodeDecodeError.__init__ = UnicodeDecodeError__init__
UnicodeDecodeError.__str__ = UnicodeDecodeError__str__
del UnicodeDecodeError__init__
del UnicodeDecodeError__str__


def UnicodeTranslateError__init__(self, object, start, end, reason):
    self.object = object
    self.start = start
    self.end = end
    self.reason = reason


def UnicodeTranslateError__str__(self):
    if not hasattr(self, 'object'):
        return BaseException.__str__(self)
    if self.start < len(self.object) and self.start + 1 == self.end:
        badchar = ord(self.object[self.start])
        if badchar <= 0xff:
            fmt = "can't translate character '\\x%02x' in position %d: %s"
        elif badchar <= 0xffff:
            fmt = "can't translate character '\\u%04x' in position %d: %s"
        else:
            fmt = "can't translate character '\\U%08x' in position %d: %s"
        return fmt % (badchar, self.start, self.reason)
    return "can't translate characters in position %d-%d: %s" % (self.start, self.end - 1, self.reason)


UnicodeTranslateError.__init__ = UnicodeTranslateError__init__
UnicodeTranslateError.__str__ = UnicodeTranslateError__str__
del UnicodeTranslateError__init__
del UnicodeTranslateError__str__


# These errors are just an alias of OSError (i.e. 'EnvironmentError is OSError == True')
EnvironmentError = OSError
IOError = OSError
