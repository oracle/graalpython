# Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import sys
import csv
import argparse

def read_file(filename):
    with open(filename) as csvfile:
        return {x["unittest"]:x for x in csv.DictReader(csvfile) if x["unittest"] != "TOTAL"}

# ----------------------------------------------------------------------------------------------------------------------
#
# main tool
#
# ----------------------------------------------------------------------------------------------------------------------
def main(prog, args):
    parser = argparse.ArgumentParser(prog=prog,
                                     description="Compare the result of two runs of the standard python unittests based on their csv reports.")
    parser.add_argument("-v", "--verbose", help="Verbose output.", action="store_true")
    parser.add_argument("csvfile1", help="first input csv file")
    parser.add_argument("csvfile2", help="first input csv file")

    global flags
    flags = parser.parse_args(args=args)
    print("comparing {} and {}".format(flags.csvfile1, flags.csvfile2))
    
    raw1 = read_file(flags.csvfile1)
    raw2 = read_file(flags.csvfile2)
    print("number of entries: {} / {}".format(len(raw1.keys()), len(raw2.keys())))
    
    result = []
    missing_tests = []
    new_tests = []
    for name, data in raw1.items():
        other_data = raw2.get(name)
        if other_data:
            result.append({'name':name, 'old_passing':int(data['num_passes']), 'new_passing':int(other_data['num_passes'])})
        else:
            missing_tests.append(name)

    for name in raw2.keys():
        if not raw1.get(name):
            new_tests.append(name)

    def custom(a):
        return (a['new_passing'] - a['old_passing']) * 1000000 + a['old_passing']

    result = sorted(result, key=custom)
    
    RED = u"\u001b[31m"
    GREEN = u"\u001b[32m"
    RESET = u"\u001b[0m"
    
    for entry in result:
        delta = entry['new_passing'] - entry['old_passing']
        if delta != 0:
            print("%s%30s: %d (from %d to %d passing tests)" % (GREEN if delta > 0 else RED, entry['name'], delta, entry['old_passing'], entry['new_passing']))
    print(RESET)    

if __name__ == "__main__":
    main(sys.argv[0], sys.argv[1:])
