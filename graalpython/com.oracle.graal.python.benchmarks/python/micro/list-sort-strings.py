# Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

data = [
    str(x) for x in [
        'tuple-indexing-from-literal', 'cPickle-funcs', 'cPickle-strings', 'magic-iter', 'math-sqrt', 'attribute-bool',
        'c-issubtype-polymorphic-forced-to-native', 'genexp-builtin-call-sized', 'list-sort-objects',
        'attribute-access-super', 'list-indexing', 'object-allocate', 'special-add-sized', 'list-comp', 'special-len',
        'pickle_gen_data', 'cPickle-dicts', 'pickle-dicts', 'try-except-store-simple', 'generator-notaligned-sized',
        'object-layout-change', 'c-issubtype-polymorphic', 'list-iterating', 'objects.pic', 'generate-functions-sized',
        'function-call-sized', 'list-constructions-sized', 'c-magic-iter', 'class-access', 'try-except-store-two-types',
        'pickle-lists', 'cPickle-lists', 'mmap-anonymous-sized', 'funcs.pic', 'list-indexing-from-literal',
        'member-access', 'try-except-simple', 'c_arith_binop_2', 'c-magic-bool', 'dict-getitem-sized',
        'c-call-classmethod', 'call-method-polymorphic', 'strings.pic', 'generator-expression-sized',
        'tuple-indexing-from-constructor', 'c-list-iterating-obj', 'c-issubtype-monorphic', 'repeated-import',
        'list-iterating-explicit', 'pickle-objects', 'c_member_access', 'arith-binop', 'boolean-logic-sized',
        'special-add-int-sized', 'builtin-len', 'lists.pic', 'dicts.pic', 'pickle_bench', 'generator-parallel',
        'try-except-two-types', 'list-iterating-obj-sized', 'pickle-funcs', 'c_arith-binop', 'attribute-access',
        'c-instantiate-large', 'cPickle-objects', 'attribute-access-polymorphic', 'builtin-len-tuple-sized',
        'magic-bool-sized', 'c-call-method', 'arith-modulo-sized', 'mmap-file', 'list-indexing-from-constructor',
        'call-classmethod-sized', 'pickle-strings', 'list-sort-strings', 'generator-sized', 'for-range', 'pickle_utils',
    ]
]


def measure(num):
    m = "\0"
    for i in range(num):
        m = max(m, sorted(data)[-1])
    return m[0]


def __benchmark__(num=1000000):
    return measure(num)
