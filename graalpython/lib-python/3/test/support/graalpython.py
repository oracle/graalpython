# Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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

# GraalPython specific support. This code should work on both CPython and GraalPython

def graalpython_ignore(assertion, default_return=None, message=None):
   '''
   Allows to ignore single assertion of otherwise passing test. Assertions ignored this way can be enabled by
   exporting environment variable PROCESS_IGNORED_GRAALPYTHON_ASSERTS.

   :param assertion: executable object, typically lambda with the assertion that should be ignored.
   :param default_return: return value to be used if the assertion is ignored.
   :param message: dummy argument that can be used to provide an explanation why the assertion needs to be ignored.
   :return: The result of calling 'assertion' or 'default_return' if the assertion was ignored.
   '''
   import os
   if os.environ.get('PROCESS_IGNORED_GRAALPYTHON_ASSERTS', None) is None:
      return default_return
   result = assertion()
   if message is not None:
      print("\nIgnored assertion '{}' is passing! Stack trace:".format(message))
   else:
      print("\nIgnored assertion is passing! Stack trace:")
   import sys
   import traceback
   traceback.print_stack(f=sys._getframe().f_back, limit=1)
   return result