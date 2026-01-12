# Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

# ================================
#
# This script is used by ci to merge several retagger report JSON files, which is then used
# by running python3 runner.py merge-tags-from-reports reports-merged.json
#
# ================================

import os
import sys
import json
import glob
import argparse
from dataclasses import dataclass

# status we want to focus on
EXPORT_STATUS = ["FAILED"]

@dataclass
class Test:
    name:       str
    status:     str
    duration:   str


def read_report(path: str) -> list[Test]:
    tests = []
    with open(path) as f:
        data = json.load(f)
        for result in data:
            name, status, duration = result["name"], result["status"], result["duration"]
            if status in EXPORT_STATUS: tests.append(Test(f"{name}", status, duration))

    return tests

def merge_tests(report: list[Test], merged: dict[str, dict]):
    for test in report:
        if test.name not in merged:
            merged[test.name] = test.__dict__

def export_reports(merged: dict[str, dict], outfile: str):
    with open(outfile, "w") as f:
        json.dump(list(merged.values()), f)
    print(f"=== Exported {len(merged)} ({EXPORT_STATUS}) tests to {f.name} ===")

def merge_reports(reports: list[str], outfile: str):
    merged_reports = {}
    for report in reports:
        report_tests = read_report(report)
        merge_tests(report_tests, merged_reports)

    export_reports(merged_reports, outfile)

def main(outfile: str, source_dir: str, pattern: str):
    path = f"{source_dir}/{pattern}"
    files = glob.glob(path)

    files = [file for file in files if file.endswith(".json")]
    merge_reports(files, outfile)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Merge unittest retagger report JSON files")
    parser.add_argument("--outfile", help="Output file name (optional)", default="reports-merged.json")
    parser.add_argument("--dir", help="Reports files directory (optional)", default=".")
    parser.add_argument("--pattern", default="*", help="Pattern matching for input files (optional)")

    args = parser.parse_args()
    main(
        outfile=args.outfile,
        source_dir=args.dir,
        pattern=args.pattern
    )
