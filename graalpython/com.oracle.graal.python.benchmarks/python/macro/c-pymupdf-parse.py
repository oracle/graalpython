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

import io
import os
import subprocess


def find_cpython3_on_path():
    exe_name = "python3"
    paths = os.environ["PATH"].split(os.pathsep)
    for path in paths:
        exe_path = os.path.join(path, exe_name)
        if "cpython" in subprocess.getoutput(f"'{exe_path}' -c 'import sys;print(sys.implementation.name)'"):
            return exe_path


os.environ["CPYTHON_EXE"] = find_cpython3_on_path()
ensure_packages(pymupdf="1.25.4", pymupdf4llm="0.0.18")


import pymupdf
import pymupdf4llm

PDF_BYTES = None


def parse_pdf():
    pdf_stream = io.BytesIO(PDF_BYTES)
    docType = "pdf"
    doc = pymupdf.open(stream=pdf_stream, filetype=docType)
    # doc = pymupdf.open("./sample.txt")
    md_text = pymupdf4llm.to_markdown(doc, show_progress = True)
    doc.close()
    return md_text


def __setup__(*args):
    global PDF_BYTES
    with open(os.path.join(os.path.dirname(__file__), "sample.txt"), "rb") as f:
        PDF_BYTES = f.read()


def __benchmark__(num=1):
    parse_pdf()
