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
Subplots and style variations; saves a multi-panel PDF.
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

    out_path = Path(__file__).parent / "subplots_and_styles.pdf"

    rng = np.random.default_rng(123)
    x = np.linspace(0, 2*np.pi, 200)
    y1 = np.sin(x)
    y2 = np.cos(x)
    y3 = np.sin(2*x) * np.exp(-0.3*x)
    y4 = rng.normal(loc=0.0, scale=1.0, size=200)

    fig, axs = plt.subplots(2, 2, figsize=(8, 6), dpi=150)

    # 1) Basic line styles
    ax = axs[0, 0]
    ax.plot(x, y1, label="sin(x)", color="#1f77b4", linewidth=2.0)
    ax.plot(x, y2, label="cos(x)", color="#ff7f0e", linestyle="--", linewidth=2.0)
    ax.set_title("Line Styles")
    ax.set_xlabel("x")
    ax.set_ylabel("y")
    ax.grid(True, linestyle=":", alpha=0.5)
    ax.legend(loc="best")

    # 2) Markers and transparency
    ax = axs[0, 1]
    ax.plot(x, y3, color="#2ca02c", linewidth=1.5)
    ax.scatter(x[::10], y3[::10], color="#d62728", s=20, alpha=0.8, label="samples")
    ax.set_title("Markers and Decay")
    ax.annotate("decay", xy=(2.0, y3[np.searchsorted(x, 2.0)]), xytext=(3.5, 0.8),
                arrowprops=dict(arrowstyle="->", color="gray"), color="gray")
    ax.grid(True, alpha=0.4)
    ax.legend(loc="best")

    # 3) Simple bar chart
    ax = axs[1, 0]
    categories = ["A", "B", "C", "D", "E"]
    values = np.abs(rng.normal(3.0, 1.0, size=len(categories)))
    bars = ax.bar(categories, values, color="#9467bd", edgecolor="black", alpha=0.85)
    for b in bars:
        ax.text(b.get_x() + b.get_width()/2, b.get_height() + 0.05,
                f"{b.get_height():.1f}", ha="center", va="bottom", fontsize=8)
    ax.set_title("Bar Chart")
    ax.set_ylabel("Value")
    ax.set_ylim(0, max(values) * 1.2)
    ax.grid(axis="y", linestyle="--", alpha=0.3)

    # 4) Histogram with style
    ax = axs[1, 1]
    ax.hist(y4, bins=20, color="#8c564b", edgecolor="white", alpha=0.9)
    ax.set_title("Histogram")
    ax.set_xlabel("Value")
    ax.set_ylabel("Frequency")
    ax.grid(True, linestyle="--", alpha=0.3)

    fig.suptitle("Subplots and Styles", fontsize=14)
    fig.tight_layout(rect=[0, 0.03, 1, 0.95])

    fig.savefig(out_path, format="pdf")
    plt.close(fig)


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
