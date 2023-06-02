# Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

"""Create and build a skeleton maven java polyglot pythn project.


"""

import argparse
import itertools
import os
import shutil
import sys
import zipfile

assert sys.pycache_prefix is None


def ensure_directories(zf, path):
    """
    Recursively create directory entries in a zip file.
    """
    for prefix in itertools.accumulate(path.split("/"), func=lambda a, b: f"{a}/{b}"):
        dirname = f"{prefix}/"
        try:
            zf.getinfo(dirname)
        except KeyError:
            zf.writestr(zipfile.ZipInfo(dirname), b"")

def main(args):
    parser = argparse.ArgumentParser(prog=f"{sys.executable} -m standalone")    
    parser.add_argument(
        "--keep-temp", action="store_true", help="Keep temporary files for debugging."
    )
    parser.add_argument(
        "-o",
        "--output-directory",
        help="The directory to write the Java project to.",
        required=True,
    )

    parsed_args = parser.parse_args(args)

    target_dir = parsed_args.output_directory
    os.makedirs(target_dir, exist_ok=True)
    
    shutil.copytree(os.path.join(os.path.dirname(__file__), "app/src"), os.path.join(target_dir, "src"))
    
    vfs_home = os.path.join(target_dir, "src", "main", "resources", "vfs", "home")
    os.makedirs(vfs_home, exist_ok=True)
    shutil.copytree(__graalpython__.capi_home, os.path.join(vfs_home, "lib-graalpython"))
    shutil.copytree(__graalpython__.stdlib_home, os.path.join(vfs_home, "lib-python", "3"))
    
    shutil.copy(os.path.join(os.path.dirname(__file__), "app/native-image-resources.json"), target_dir )
    shutil.copy(os.path.join(os.path.dirname(__file__), "app/pom.xml"), target_dir)

if __name__ == "__main__":
    main(sys.argv[1:])
