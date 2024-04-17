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

# This micro benchmark checks that the allocation of the bytes array, which is
# internally done in try-catch(OutOfMemoryError) is virtualized in partially
# evaluated code.
#
# Note: for ordinary host Java code compilation Graal never virtualizes or moves
# allocations in try-catch(OutOfMemoryError)) for compatibility reasons.
#
# Note2: we want to catch OutOfMemoryError and translate it to Python MemoryError,
# so that we can attach a precise location to it if possible, but we accept that
# under some circumstances the compiler may move the allocation out of the try-catch
# and we will catch it elsewhere (probably the catch-all in PBytecodeRootNode) and
# attach imprecise location to it. Alternative is to force the allocation using
# CompilerDirectives.ensureAllocatedHere, which would, however, prevent any virtualization,
# which is deemed a price to high to pay for a precise location of MemoryError.
# Possible future improvement: detect try-except(MemoryError) in Python and use
# CompilerDirectives.ensureAllocatedHere only in such try blocks.


def virtualize(ctor):
    b = ctor(5000000)
    return b is not None


def measure(n):
    for i in range(1,n):
        virtualize(bytes)


def __benchmark__(num=10000):
    return measure(num)
