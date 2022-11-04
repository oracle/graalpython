# Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import sys, ast, unittest

# TODO skip until 3.10
# @unittest.skipIf(sys.version_info.minor < 10, "Requires Python 3.10+")
# def test_guard():
#     def f(x, g):
#         match x:
#             case x if g == 1:
#                 return 42
#             case _:
#                 return 0
#
#     assert f(1, 1) == 42
#     assert f(1, 2) == 0
#
#     def f(x):
#         match x:
#             case x as g if g == 1:
#                 42
#             case _:
#                 0
#
#     assert f(1) == 42
#     assert f(2) == 0

# @unittest.skipIf(sys.version_info.minor < 10, "Requires Python 3.10+")
# def test_complex_as_binary_op():
#     src = """
# def f(a):
#     match a:
#         case 2+3j:
#             return "match add"
#         case 2-3j:
#             return "match sub"
#     return "no match"
# """
#
#     tree = ast.parse(src)
#     # replace "case 2+3j" with "case 2+(4+3j)"
#     tree.body[0].body[0].cases[0].pattern.value.right = ast.Constant(4+3j)
#     # replace "case 2-3j" with "case 2-(4+3j)"
#     tree.body[0].body[0].cases[1].pattern.value.right = ast.Constant(4+3j)
#     ast.fix_missing_locations(tree)
#     vars = {}
#     exec(compile(tree, "<string>", "exec"), vars)
#     f = vars['f']
#
#     assert f(6+3j) == "match add"
#     assert f(-2-3j) == "match sub"
#
# @unittest.skipIf(sys.version_info.minor < 10, "Requires Python 3.10+")
# def test_long_mapping():
#     def f(x):
#         match d:
#             case {0:0, 1:1, 2:2, 3:3, 4:4, 5:5, 6:6, 7:7, 8:8, 9:9, 10:10, 11:11, 12:12, 13:13, 14:14, 15:15, 16:16, 17:17, 18:18, 19:19, 20:20, 21:21, 22:22, 23:23, 24:24, 25:25, 26:26, 27:27, 28:28, 29:29, 30:30, 31:31, 32:32, 33:33}:
#                 return 42
#         return 0
#
#     d = {0:0, 1:1, 2:2, 3:3, 4:4, 5:5, 6:6, 7:7, 8:8, 9:9, 10:10, 11:11, 12:12, 13:13, 14:14, 15:15, 16:16, 17:17, 18:18, 19:19, 20:20, 21:21, 22:22, 23:23, 24:24, 25:25, 26:26, 27:27, 28:28, 29:29, 30:30, 31:31, 32:32, 33:33}
#     assert f(d) == 42
#
#     def star_match(x):
#         match d:
#             case {0:0, 1:1, 2:2, 3:3, 4:4, 5:5, 6:6, 7:7, 8:8, 9:9, 10:10, 11:11, 12:12, 13:13, 14:14, 15:15, 16:16, 17:17, 18:18, 19:19, 20:20, 21:21, 22:22, 23:23, 24:24, 25:25, 26:26, 27:27, 28:28, 29:29, 30:30, 31:31, 32:32, **z}:
#                 return z
#         return 0
#
#     assert star_match(d) == {33:33}

