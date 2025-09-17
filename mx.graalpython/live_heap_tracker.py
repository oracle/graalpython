# Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

from pathlib import Path
import re
import subprocess
import sys
import time

TOTAL_RE = re.compile(r'^Total +\d+ +(\d+)', re.MULTILINE)
PRIVATE_RE = re.compile(r'Private_(?:Clean|Dirty):\s+(\d+) kB')


def jmap(jmap_binary, ppid):
    if not jmap_binary:
        return 0
    try:
        jmap_output = subprocess.check_output(
            [jmap_binary, '-histo:live', str(ppid)],
            universal_newlines=True,
            stderr=subprocess.DEVNULL,
        )
        if match := TOTAL_RE.search(jmap_output):
            heap_bytes = int(match.group(1))
            return heap_bytes
    except (subprocess.CalledProcessError, subprocess.TimeoutExpired):
        pass
    return 0


def uss(ppid):
    smap = Path(f"/proc/{ppid}/smaps")
    try:
        memory_map = smap.read_text()
        total_bytes = sum(int(val) * 1024 for val in PRIVATE_RE.findall(memory_map))
        return total_bytes
    except FileNotFoundError:
        pass
    return 0


def main():
    output_file = sys.argv[1]
    iterations = int(sys.argv[2])
    jmap_binary = sys.argv[3]
    benchmark = sys.argv[4:]
    with open(output_file, 'w') as f:
        for _ in range(iterations):
            proc = subprocess.Popen(benchmark)
            ppid = proc.pid
            while proc.poll() is None:
                time.sleep(0.3)
                uss_bytes = uss(ppid)
                heap_bytes = jmap(jmap_binary, ppid)
                f.write(f"{heap_bytes} {uss_bytes}\n")
            if proc.returncode != 0:
                sys.exit(proc.returncode)


if __name__ == '__main__':
    main()
