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

from pickle_utils import *
import pickle
import os


MOD_DIR = os.path.abspath(os.path.split(__file__)[0])


def gen_dics(size=500, size_min=10, size_max=100, strings=None):
    print(">>>> gen {} dicts ...".format(size))
    DATA = []
    for i in range(size):
        if i % 10 == 0:
            print(".", end="", flush=True)
        DATA.append(random_dict(random.randint(size_min, size_max), strings=strings))
    print("\npickling dicts ... ")
    with open(os.path.join(MOD_DIR, "dicts.pickle"), "w+b") as FILE:
        pickle.dump(DATA, FILE)
    print("done")


def gen_funcs(size=1000):
    print(">>>> gen {} funcs ...".format(size))
    DATA = []
    for i in range(size):
        if i % 100 == 0:
            print(".", end="", flush=True)
        DATA.append(random_func())
    print("\npickling funcs ...")
    with open(os.path.join(MOD_DIR, "funcs.pickle"), "w+b") as FILE:
        pickle.dump(DATA, FILE)
    print("done")


def gen_lists(size=500, size_min=10, size_max=100, strings=None):
    print(">>>> gen {} lists ...".format(size))
    DATA = []
    for i in range(size):
        if i % 100 == 0:
            print(".", end="", flush=True)
        DATA.append(random_list(random.randint(size_min, size_max), strings=strings))
    print("\npickling lists ...")
    with open(os.path.join(MOD_DIR, "lists.pickle"), "w+b") as FILE:
        pickle.dump(DATA, FILE)
    print("done")


def gen_objects(size=500):
    print(">>>> gen {} objects ...".format(size))
    DATA = []
    for i in range(size):
        if i % 100 == 0:
            print(".", end="", flush=True)
        DATA.append(random_instance())
    print("\npickling objects ...")
    with open(os.path.join(MOD_DIR, "objects.pickle"), "w+b") as FILE:
        pickle.dump(DATA, FILE)
    print("done")


def gen_strings(size=10000, size_min=10, size_max=1000):
    DATA = get_data("strings")
    if DATA and len(DATA) >= size:
        print("strings fixture is already generated, reusing ... ")
        DATA = DATA[:size]
    else:
        print(">>>> gen {} strings ...".format(size))
        DATA = []
        for i in range(size):
            if i % 1000 == 0:
                print(".", end="", flush=True)
            DATA.append(random_string(random.randint(size_min, size_max)))

        print("\npickling strings ...")
        with open(os.path.join(MOD_DIR, "strings.pickle"), "w+b") as FILE:
            pickle.dump(DATA, FILE)
        print("done")
    return DATA


if __name__ == '__main__':
    strings = gen_strings()
    gen_dics(strings=strings)
    gen_funcs()
    gen_lists(strings=strings)
    gen_objects()
