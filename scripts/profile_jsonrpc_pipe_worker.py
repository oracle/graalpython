#!/usr/bin/env python3
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

from __future__ import annotations

import argparse
import json
from pathlib import Path
import subprocess
import sys
from typing import BinaryIO, TextIO


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Drive the jsonrpc-pipe worker under a profiler.")
    parser.add_argument("--graalpy", required=True, help="Path to GraalPy launcher")
    parser.add_argument("--worker-io", choices=("text", "buffer"), required=True)
    parser.add_argument("--profiler", choices=("async", "gprofng"), required=True)
    parser.add_argument("--requests", type=int, default=30000)
    parser.add_argument("--output", required=True, help="Profile output file (async) or experiment dir (gprofng)")
    parser.add_argument("--async-profiler-dir", default="/tmp/async-profiler-1.8.3-linux-x64")
    parser.add_argument("--benchmark", default="graalpython/com.oracle.graal.python.benchmarks/python/micro/jsonrpc-pipe.py")
    return parser.parse_args()


def build_worker_cmd(args: argparse.Namespace) -> list[str]:
    benchmark = str(Path(args.benchmark).resolve())
    worker = [args.graalpy, benchmark, "--worker", f"--worker-io={args.worker_io}"]
    if args.profiler == "async":
        lib = Path(args.async_profiler_dir) / "build" / "libasyncProfiler.so"
        return [
            args.graalpy,
            f"--vm.agentpath:{lib}=start,event=cpu,file={args.output}",
            "--vm.XX:+UnlockDiagnosticVMOptions",
            "--vm.XX:+DebugNonSafepoints",
            benchmark,
            "--worker",
            f"--worker-io={args.worker_io}",
        ]
    return [
        "gprofng",
        "collect",
        "app",
        "-O",
        args.output,
        "-F",
        "off",
        "--",
        *worker,
    ]


def make_request(index: int) -> dict[str, object]:
    return {
        "jsonrpc": "2.0",
        "id": index,
        "method": "mask",
        "params": {
            "email": f" User{index}.payload-payload@Example.COM ",
            "phone": f"+49 (170) {index:04d}-payload-",
            "region": "eu",
            "source": "microbench",
        },
    }


def read_json_line_text(stream: TextIO, stderr: TextIO) -> dict[str, object]:
    while True:
        line = stream.readline()
        if not line:
            raise RuntimeError(f"worker terminated early: {stderr.read()}")
        if line.lstrip().startswith("{"):
            return json.loads(line)


def read_json_line_binary(stream: BinaryIO, stderr: BinaryIO) -> dict[str, object]:
    while True:
        line = stream.readline()
        if not line:
            raise RuntimeError(f"worker terminated early: {stderr.read().decode('utf-8', errors='replace')}")
        if line.lstrip().startswith(b"{"):
            return json.loads(line)


def drive_text(process: subprocess.Popen[str], requests: int) -> None:
    assert process.stdin is not None
    assert process.stdout is not None
    assert process.stderr is not None
    for i in range(requests):
        process.stdin.write(json.dumps(make_request(i), separators=(",", ":")) + "\n")
        process.stdin.flush()
        read_json_line_text(process.stdout, process.stderr)


def drive_binary(process: subprocess.Popen[bytes], requests: int) -> None:
    assert process.stdin is not None
    assert process.stdout is not None
    assert process.stderr is not None
    for i in range(requests):
        payload = (json.dumps(make_request(i), separators=(",", ":")) + "\n").encode("utf-8")
        process.stdin.write(payload)
        process.stdin.flush()
        read_json_line_binary(process.stdout, process.stderr)


def main() -> int:
    args = parse_args()
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    if args.profiler == "gprofng" and output.exists():
        if output.is_dir():
            subprocess.check_call(["rm", "-rf", str(output)])
        else:
            output.unlink()
    cmd = build_worker_cmd(args)
    process_cwd = None
    if args.profiler == "gprofng":
        process_cwd = str(output.parent)
        cmd[4] = output.name
    text_mode = args.worker_io == "text"
    if text_mode:
        process = subprocess.Popen(
            cmd,
            cwd=process_cwd,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            encoding="utf-8",
            bufsize=1,
        )
        try:
            drive_text(process, args.requests)
            process.stdin.close()
            rc = process.wait(timeout=120)
            if rc != 0:
                raise RuntimeError(process.stderr.read())
            sys.stdout.write(process.stderr.read())
        finally:
            if process.poll() is None:
                process.kill()
    else:
        process = subprocess.Popen(
            cmd,
            cwd=process_cwd,
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            bufsize=0,
        )
        try:
            drive_binary(process, args.requests)
            process.stdin.close()
            rc = process.wait(timeout=120)
            if rc != 0:
                raise RuntimeError(process.stderr.read().decode("utf-8", errors="replace"))
            sys.stdout.write(process.stderr.read().decode("utf-8", errors="replace"))
        finally:
            if process.poll() is None:
                process.kill()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
