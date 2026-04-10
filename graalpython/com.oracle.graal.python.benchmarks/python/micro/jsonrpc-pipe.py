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
import io
import json
import os
import re
import subprocess
import sys
import time


EMAIL_RE = re.compile(r"\s+")
NON_DIGIT_RE = re.compile(r"\D+")
_STATE = None


class Endpoint:
    def __init__(self, mode, reader, writer, closeables=()):
        self.mode = mode
        self.reader = reader
        self.writer = writer
        self.closeables = closeables

    def write_message(self, message):
        line = json.dumps(message, separators=(",", ":"))
        if self.mode == "text":
            self.writer.write(line)
            self.writer.write("\n")
            self.writer.flush()
            return
        payload = (line + "\n").encode("utf-8")
        if self.mode == "buffer":
            self.writer.write(payload)
            self.writer.flush()
            return
        write_all(self.writer, payload)

    def read_message(self):
        if self.mode == "text":
            line = self.reader.readline()
        else:
            data = self.reader.readline()
            line = data.decode("utf-8") if data else ""
        if not line:
            raise EOFError("unexpected EOF while reading line")
        return json.loads(line)

    def close(self):
        streams = self.closeables if self.closeables else (self.reader, self.writer)
        for stream in streams:
            if hasattr(stream, "close"):
                try:
                    stream.close()
                except OSError:
                    pass


class FDLineReader:
    def __init__(self, fd):
        self.fd = fd
        self.pending = bytearray()

    def readline(self):
        while True:
            newline = self.pending.find(b"\n")
            if newline >= 0:
                line = bytes(self.pending[: newline + 1])
                del self.pending[: newline + 1]
                return line
            chunk = os.read(self.fd, 4096)
            if not chunk:
                if not self.pending:
                    return b""
                line = bytes(self.pending)
                self.pending.clear()
                return line
            self.pending.extend(chunk)


class State:
    def __init__(self, roundtrips, client_io, worker_io, workload, payload_bytes, batch_size):
        self.roundtrips = roundtrips
        self.client_io = client_io
        self.worker_io = worker_io
        self.workload = workload
        self.payload_bytes = payload_bytes
        self.batch_size = batch_size
        self.next_request_id = 1
        self.process = None
        self.endpoint = None


def write_all(fd, data):
    view = memoryview(data)
    while view:
        written = os.write(fd, view)
        view = view[written:]


def create_text_endpoint(read_raw, write_raw):
    reader_buffer = io.BufferedReader(read_raw, buffer_size=8192)
    writer_buffer = io.BufferedWriter(write_raw, buffer_size=8192)
    reader = io.TextIOWrapper(reader_buffer, encoding="utf-8", newline=None)
    writer = io.TextIOWrapper(writer_buffer, encoding="utf-8", newline="\n", line_buffering=False, write_through=False)
    return Endpoint("text", reader, writer)


def create_buffer_endpoint(read_raw, write_raw):
    reader = io.BufferedReader(read_raw, buffer_size=8192)
    writer = io.BufferedWriter(write_raw, buffer_size=8192)
    return Endpoint("buffer", reader, writer)


def create_fd_endpoint(read_fd, write_fd, closeables=()):
    return Endpoint("fd", FDLineReader(read_fd), write_fd, closeables)


def create_parent_endpoint(process, mode):
    if mode == "text":
        return create_text_endpoint(process.stdout, process.stdin)
    if mode == "buffer":
        return create_buffer_endpoint(process.stdout, process.stdin)
    return create_fd_endpoint(process.stdout.fileno(), process.stdin.fileno(), (process.stdout, process.stdin))


def create_worker_endpoint(mode):
    if mode == "text":
        return Endpoint("text", sys.stdin, sys.stdout)
    if mode == "buffer":
        return Endpoint("buffer", sys.stdin.buffer, sys.stdout.buffer)
    return create_fd_endpoint(0, 1)


def normalize_email(value):
    return EMAIL_RE.sub("", value.strip().lower())


def normalize_phone(value):
    digits = NON_DIGIT_RE.sub("", value)
    if digits.startswith("00"):
        digits = digits[2:]
    return digits


def mask_email(value):
    name, _, domain = value.partition("@")
    if not domain:
        return "***"
    return "%s***@%s" % (name[:1], domain)


def mask_phone(value):
    if len(value) <= 4:
        return "*" * len(value)
    return "*" * (len(value) - 4) + value[-4:]


def mask_row(row):
    email = normalize_email(str(row.get("email", "")))
    phone = normalize_phone(str(row.get("phone", "")))
    return {
        "email_normalized": email,
        "phone_normalized": phone,
        "email_masked": mask_email(email) if email else None,
        "phone_masked": mask_phone(phone) if phone else None,
        "region": str(row.get("region", "")).upper(),
        "source": str(row.get("source", "")).lower(),
    }


def make_echo_payload(payload_bytes):
    if payload_bytes <= 0:
        return ""
    unit = "payload-"
    return (unit * ((payload_bytes // len(unit)) + 1))[:payload_bytes]


def make_mask_row(index, payload_bytes):
    suffix = make_echo_payload(max(payload_bytes, 8))
    return {
        "email": " User%s.%s@Example.COM " % (index, suffix),
        "phone": "+49 (170) %04d-%s" % (index, suffix[:8]),
        "region": "eu",
        "source": "microbench",
    }


def build_request(kind, request_id, payload_bytes, batch_size):
    if kind == "health":
        method = "health"
        params = {}
    elif kind == "echo":
        method = "echo"
        params = {"payload": make_echo_payload(payload_bytes)}
    elif kind == "mask":
        method = "mask"
        params = make_mask_row(request_id, payload_bytes)
    elif kind == "mask_batch":
        method = "mask_batch"
        params = {"rows": [make_mask_row(request_id + i, payload_bytes) for i in range(batch_size)]}
    else:
        raise AssertionError("unsupported request kind: %s" % kind)
    return {"jsonrpc": "2.0", "id": request_id, "method": method, "params": params}


def handle_request(message):
    request_id = message.get("id")
    method = message.get("method")
    params = message.get("params", {})
    if method == "health":
        result = {"ok": True, "worker": "jsonrpc-pipe", "protocol": "json-rpc-2.0-ndjson"}
    elif method == "echo":
        payload = str(dict(params).get("payload", ""))
        result = {"ok": True, "echo": payload, "size": len(payload)}
    elif method == "mask":
        result = {"ok": True, "normalized": mask_row(dict(params))}
    elif method == "mask_batch":
        rows = [mask_row(dict(row)) for row in dict(params).get("rows", [])]
        result = {"ok": True, "normalized": rows, "count": len(rows)}
    else:
        return {"jsonrpc": "2.0", "id": request_id, "error": {"code": -32601, "message": "method not found"}}
    return {"jsonrpc": "2.0", "id": request_id, "result": result}


def validate_response(request, response, kind, payload_bytes, batch_size):
    if response.get("id") != request["id"]:
        raise AssertionError("mismatched response id")
    if "error" in response:
        raise AssertionError("worker returned error: %s" % (response["error"],))
    result = response.get("result")
    if not isinstance(result, dict) or not result.get("ok"):
        raise AssertionError("unexpected response payload: %s" % (response,))
    if kind == "echo":
        if result.get("echo") != make_echo_payload(payload_bytes):
            raise AssertionError("echo payload mismatch")
    elif kind == "mask":
        expected = mask_row(make_mask_row(int(request["id"]), payload_bytes))
        if result.get("normalized") != expected:
            raise AssertionError("mask result mismatch")
    elif kind == "mask_batch":
        expected_rows = [mask_row(make_mask_row(int(request["id"]) + i, payload_bytes)) for i in range(batch_size)]
        if result.get("count") != batch_size or result.get("normalized") != expected_rows:
            raise AssertionError("mask_batch result mismatch")


def run_roundtrips(state):
    completed = 0
    for _ in range(state.roundtrips):
        request = build_request(state.workload, state.next_request_id, state.payload_bytes, state.batch_size)
        state.next_request_id += 1
        state.endpoint.write_message(request)
        response = state.endpoint.read_message()
        validate_response(request, response, state.workload, state.payload_bytes, state.batch_size)
        completed += 1
    return completed


def parse_int(value):
    if isinstance(value, int):
        return value
    return int(str(value).replace("_", ""))


def __process_args__(roundtrips=500, client_io="text", worker_io="text", workload="mask", payload_bytes=64, batch_size=8):
    return [
        parse_int(roundtrips),
        str(client_io),
        str(worker_io),
        str(workload),
        parse_int(payload_bytes),
        parse_int(batch_size),
    ]


def __setup__(roundtrips=500, client_io="text", worker_io="text", workload="mask", payload_bytes=64, batch_size=8):
    global _STATE
    __teardown__()
    state = State(roundtrips, client_io, worker_io, workload, payload_bytes, batch_size)
    command = [
        sys.executable,
        __file__,
        "--worker",
        "--worker-io=%s" % worker_io,
    ]
    process = subprocess.Popen(
        command,
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        bufsize=0,
    )
    state.process = process
    state.endpoint = create_parent_endpoint(process, client_io)
    _STATE = state


def __benchmark__(roundtrips=500, client_io="text", worker_io="text", workload="mask", payload_bytes=64, batch_size=8):
    if _STATE is None:
        __setup__(roundtrips, client_io, worker_io, workload, payload_bytes, batch_size)
    return run_roundtrips(_STATE)


def __teardown__():
    global _STATE
    state = _STATE
    _STATE = None
    if state is None:
        return
    try:
        if state.endpoint is not None:
            state.endpoint.close()
    finally:
        if state.process is not None:
            stderr = b""
            try:
                stderr = state.process.stderr.read() if state.process.stderr is not None else b""
            except OSError:
                pass
            return_code = state.process.wait()
            if return_code != 0:
                raise RuntimeError("worker exited with status %d: %s" % (return_code, stderr.decode("utf-8", errors="replace")))


def run_worker(worker_io):
    endpoint = create_worker_endpoint(worker_io)
    try:
        while True:
            try:
                request = endpoint.read_message()
            except EOFError:
                return 0
            endpoint.write_message(handle_request(request))
    finally:
        endpoint.close()


def run_direct(roundtrips, client_io, worker_io, workload, payload_bytes, batch_size):
    start = time.perf_counter()
    __setup__(roundtrips, client_io, worker_io, workload, payload_bytes, batch_size)
    try:
        completed = __benchmark__(roundtrips, client_io, worker_io, workload, payload_bytes, batch_size)
    finally:
        __teardown__()
    wall = time.perf_counter() - start
    print("roundtrips=%d" % completed)
    print("wall_s=%s" % wall)
    print("throughput_ops_s=%s" % (completed / wall if wall else 0.0))
    return 0


def main(argv=None):
    parser = argparse.ArgumentParser(description="Strict JSON-RPC-like pipe roundtrip microbenchmark.")
    parser.add_argument("--worker", action="store_true")
    parser.add_argument("--worker-io", choices=("text", "buffer", "fd"), default="text")
    parser.add_argument("--roundtrips", type=parse_int, default=500)
    parser.add_argument("--client-io", choices=("text", "buffer", "fd"), default="text")
    parser.add_argument("--workload", choices=("health", "echo", "mask", "mask_batch"), default="mask")
    parser.add_argument("--payload-bytes", type=parse_int, default=64)
    parser.add_argument("--batch-size", type=parse_int, default=8)
    args = parser.parse_args(argv)
    if args.worker:
        return run_worker(args.worker_io)
    return run_direct(args.roundtrips, args.client_io, args.worker_io, args.workload, args.payload_bytes, args.batch_size)


def run():
    __setup__()
    try:
        __benchmark__()
    finally:
        __teardown__()


def warmupIterations():
    return 5


def iterations():
    return 10


def summary():
    return {
        "name": "OutlierRemovalAverageSummary",
        "lower-threshold": 0,
        "upper-threshold": 0.3,
    }


if __name__ == "__main__":
    raise SystemExit(main())
