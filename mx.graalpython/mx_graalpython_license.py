#!/usr/bin/env python
from collections import defaultdict
import os
import re
import sys


COPYRIGHT = "Copyright (c) 2018, Oracle and/or its affiliates."

REGENTS_COPYRIGHT = "Regents of the University of California"

LICENSE = defaultdict(lambda: """ *
 * All rights reserved.
 */
""")
BSD_LICENSE = defaultdict(lambda: """ *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
""")
UPL_LICENSE = defaultdict(lambda: """ *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 *     one is included with the Software (each a "Larger Work" to which the
 *     Software is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
""")


LICENSE[".py"] = LICENSE["java"].replace(" */", "").replace(" *", "#")
BSD_LICENSE[".py"] = BSD_LICENSE["java"].replace(" */", "").replace(" *", "#")
UPL_LICENSE[".py"] = UPL_LICENSE["java"].replace(" */", "").replace(" *", "#")

ORACLE_COPYRIGHT_RE = re.compile('Copyright \(c\) .*Oracle and\/or its affiliates')

COPYRIGHT_RE = defaultdict(lambda: re.compile('\s*\/\*\s*\*?\s*Copyright', re.M))
COPYRIGHT_RE[".py"] = re.compile('\s*\/\*\s*\*?\s*Copyright', re.M)

OLD_COPYRIGHT_RE = re.compile('(Copyright \(c\) 201\d, .*)')

COPYRIGHT_END = defaultdict(lambda: re.compile('\*\/'))
COPYRIGHT_END[".py"] = re.compile('#\n\n', re.M)

OLD_COPYRIGHT_FMT = defaultdict(lambda: "/*\n * %s\n * ")
OLD_COPYRIGHT_FMT[".py"] = "# %s\n# "

COPYRIGHT_FMT = defaultdict(lambda: "/*\n * %s\n%s")
COPYRIGHT_FMT[".py"] = "# %s\n%s"

def update_license(filename):
    print(filename)
    ending = os.path.splitext(filename)[1]
    content = ""
    with open(filename) as f:
        content = f.read()

    if ORACLE_COPYRIGHT_RE.search(content):
        if False: # update to UPL
            content = content.replace(LICENSE[ending], UPL_LICENSE[ending])
            with open(filename, 'w') as f:
                f.write(content)
            return True
        if False: # ensure we have BSD in UC files
            if ending == ".py":
                calif = "# Copyright (c) 2013-2016, Regents of the University of California\n" + BSD_LICENSE[ending]
            else:
                calif = " * Copyright (c) 2013-2016, Regents of the University of California. All rights reserved.\n" + + BSD_LICENSE[ending]
            content = content.replace(LICENSE[ending], calif)
            with open(filename, 'w') as f:
                f.write(content)
            return True

        # nothing to do
        return False

    if COPYRIGHT_RE[ending].search(content):
        oldcopy = OLD_COPYRIGHT_RE.search(content)
        if not oldcopy:
            return False
        old_copyright = oldcopy.group(1)
        endidx = oldcopy.start(1)
        content[0:endidx+2] = OLD_COPYRIGHT_FMT[ending] % COPYRIGHT
        with open(filename, 'w') as f:
            f.write(content)
        return True
    else:
        with open(filename, 'w') as f:
            f.write(COPYRIGHT_FMT[ending] % (COPYRIGHT, UPL_LICENSE[ending]))
            f.write(content)
    return True


def update_license_headers(filename=None):
    if filename:
        return update_license(filename)
    else:
        updated = False
        for root, dirs, files in os.walk(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))):
            if (("graalpython/com.oracle.graal.python" in root) or ("graalpython/lib-graalpython" in root)):
                if ("mxbuild" in root) or ("include/private" in root):
                    continue
                for f in files:
                    if f.endswith(".c") or f.endswith(".java") or f.endswith(".py"):
                        updated = update_license(os.path.join(root, f)) or updated
        return updated
