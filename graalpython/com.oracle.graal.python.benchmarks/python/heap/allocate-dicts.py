# Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import time

# follows the distribution of dict sizes in import-a-lot benchmark
#
# OQL query:
# var objs = toArray(heap.objects("com.oracle.graal.python.builtins.objects.common.ObjectHashMap", false));
# var counts = {};
# for each (var m in objs) {
#  var s = m.size;
#  counts[s] = counts[s] == null ? 1 : counts[s] + 1;
# }
# map(sort(unique(map(objs, "it.size")), "lhs - rhs"),
#    function (s) { return { size: s, count: counts[s] }; })

FACTOR = 500
N = 256 + 8
keys = [str(i) for i in range(N)]
small_dicts0  = [{} for i in range(22 * FACTOR)]
small_dicts1  = [{1:1} for i in range(215 * FACTOR)]
small_dicts2  = [{1:1, 2:3} for i in range(220 * FACTOR)]
small_dicts4  = [{keys[k % N]:1 for k in range(4)} for i in range(145 * FACTOR)]
small_dicts8  = [{keys[k % N]:1 for k in range(8)} for i in range(51 * FACTOR)]
dicts1 = [{(keys[k % N]):1 for k in range(8 + i % 256)} for i in range(61 * FACTOR)]

# Sleep a bit to shake out weakref callbacks and get more measurement samples
for i in range(30):
    time.sleep(0.1)
