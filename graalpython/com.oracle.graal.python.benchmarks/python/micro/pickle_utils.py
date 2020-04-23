# Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import types
import random
import string
import pickle
import os


def get_pickler(module):
    print(">>> using the {} pickler ... ".format(module))
    dumps, loads = None, None
    if module == "pickle":
        import pickle
        dumps = pickle.dumps
        loads = pickle.loads
    elif module == "cPickle":
        import cPickle
        dumps = cPickle.dumps
        loads = cPickle.loads
    return dumps, loads


def random_string(size):
    return ''.join([random.choice(string.ascii_letters + string.digits + string.punctuation) for _ in range(size)])


def random_list(size):
    def elem():
        t = random.randint(0, 2)
        if t == 0:
            return random.randint(0, 10000000000)
        elif t == 1:
            return float(random.randint(0, 10000000000))
        return random_string(random.randint(100, 1000))
    return [elem() for _ in range(size)]


def random_dict(size, ints_only=False):
    def elem():
        if ints_only:
            return random.randint(0, 10000000000)
        else:
            t = random.randint(0, 2)
            if t == 0:
                return random.randint(0, 10000000000)
            elif t == 1:
                return float(random.randint(0, 10000000000))
            return random_string(random.randint(100, 1000))
    return {'key_'+str(random.randint(0, 1000000000)): elem() for _ in range(size)}


CLS_TEMPLATE = 'class MyClass_{name}:\n\t{attrs}'

ATTR_TEMPLATE = 'def attr_{name}(self):\n\t\treturn {value}'

NUM_CLASSES = 10
NUM_ATTRS = 20
TYPES = []

for i in range(NUM_CLASSES):
    de_fstring = CLS_TEMPLATE.format(name=i, attrs="\n\t".join([
        ATTR_TEMPLATE.format(name=k, value=v) for k, v in random_dict(NUM_ATTRS, ints_only=True).items()
    ]))
    exec(de_fstring)
    TYPES.append(globals()["MyClass_{}".format(i)])


def random_instance():
    return TYPES[random.randint(0, len(TYPES)-1)]()


FUNCS = [v for k, v in globals().items() if isinstance(v, types.FunctionType)]


def random_func():
    return FUNCS[random.randint(0, len(FUNCS) - 1)]


def get_data(name):
    file_name = "{}.pickle".format(name)
    file_name = os.path.abspath(os.path.join(os.path.split(__file__)[0], file_name))
    print(">>> loading {} data file ... ".format(file_name))
    with open(file_name, "r+b") as FILE:
        return pickle.load(FILE)
