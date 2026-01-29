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

"""
Simple line plot example that saves to PDF.
"""


def run():
    # Execute all imports inside the `run` method so they're measured
    from pathlib import Path
    # Use a non-interactive backend to work in headless environments
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    import numpy as np

    # Ensure we have version info in the logs
    print(f"Using matplotlib version '{matplotlib.__version__}'")
    print(f"Using numpy version '{np.__version__}'")

    out_path = Path(__file__).parent / "simple_line_plot.pdf"

    # Reproducible data
    rng = np.random.default_rng(42)
    x = np.linspace(0.0, 10.0, 200)
    y = np.sin(x) + 0.15 * rng.standard_normal(x.size)

    plt.figure(figsize=(6, 4), dpi=150)
    plt.plot(x, np.sin(x), label="sin(x)", color="#1f77b4", linewidth=2.0)
    plt.scatter(x[::8], y[::8], label="samples", color="#ff7f0e", s=15, alpha=0.85)
    plt.title("Simple Line + Sampled Points")
    plt.xlabel("x")
    plt.ylabel("y")
    plt.grid(True, linestyle="--", alpha=0.4)
    plt.legend(loc="best")
    plt.tight_layout()

    plt.savefig(out_path, format="pdf")
    plt.close()


def warmupIterations():
    return 0


def iterations():
    return 1


def summary():
    return {
        "name": "OutlierRemovalAverageSummary",
        "lower-threshold": 0.0,
        "upper-threshold": 1.0,
    }
