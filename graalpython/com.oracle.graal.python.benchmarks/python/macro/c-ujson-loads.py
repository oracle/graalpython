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

import json

ensure_packages(ujson="5.11.0")
import ujson


def make_event(index):
    return {
        "id": index,
        "type": ("PushEvent", "IssuesEvent", "PullRequestEvent")[index % 3],
        "actor": {
            "id": 10_000 + index,
            "login": f"user-{index % 128}",
            "display_login": f"User {index % 128}",
            "url": f"https://api.github.com/users/user-{index % 128}",
        },
        "repo": {
            "id": 20_000 + index % 64,
            "name": f"graalpy/project-{index % 64}",
            "url": f"https://api.github.com/repos/graalpy/project-{index % 64}",
        },
        "payload": {
            "repository_id": 20_000 + index % 64,
            "ref": f"refs/heads/feature-{index % 32}",
            "before": f"{index:040x}",
            "head": f"{index + 1:040x}",
            "size": 3,
            "commits": [
                {
                    "sha": f"{index * 3 + commit:040x}",
                    "author": {"email": f"user{commit}@example.com", "name": f"Author {commit}"},
                    "message": f"Update benchmark data {index}:{commit}",
                    "distinct": commit != 1,
                }
                for commit in range(3)
            ],
        },
        "public": index % 5 != 0,
        "created_at": f"2026-07-{index % 28 + 1:02d}T{index % 24:02d}:{index % 60:02d}:00Z",
    }


JSON_LINES = tuple(
    json.dumps(make_event(index), ensure_ascii=False, separators=(",", ":")).encode() for index in range(512)
)


def __benchmark__(iterations=150):
    result = None
    for _ in range(iterations):
        for line in JSON_LINES:
            result = ujson.loads(line)
    return result
