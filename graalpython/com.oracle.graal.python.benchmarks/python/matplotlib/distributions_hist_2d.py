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
Distribution plots: 1D histogram, 2D histogram, and hexbin with colorbars.
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

    out_path = Path(__file__).parent / "distributions_2d.pdf"

    rng = np.random.default_rng(7)
    n = 6000

    # Create two correlated variables
    x = rng.normal(0.0, 1.0, n)
    y = 0.65 * x + rng.normal(0.0, 0.8, n)

    fig, axs = plt.subplots(1, 3, figsize=(12, 4), dpi=150)

    # 1) 1D histograms overlayed
    ax = axs[0]
    ax.hist(x, bins=40, alpha=0.8, label="x", color="#1f77b4", edgecolor="white")
    ax.hist(y, bins=40, alpha=0.6, label="y", color="#ff7f0e", edgecolor="white")
    ax.set_title("1D Histograms")
    ax.set_xlabel("Value")
    ax.set_ylabel("Frequency")
    ax.grid(True, linestyle="--", alpha=0.3)
    ax.legend(loc="best")

    # 2) 2D histogram via pcolormesh
    ax = axs[1]
    H, xedges, yedges = np.histogram2d(x, y, bins=60)
    X, Y = np.meshgrid(xedges, yedges)
    pcm = ax.pcolormesh(X, Y, H.T, cmap="viridis", shading="auto")
    ax.set_title("2D Histogram")
    ax.set_xlabel("x")
    ax.set_ylabel("y")
    cb = fig.colorbar(pcm, ax=ax)
    cb.set_label("Count")
    ax.grid(False)

    # 3) Hexbin with log color scale
    ax = axs[2]
    hb = ax.hexbin(x, y, gridsize=45, cmap="plasma", mincnt=1, bins="log")
    ax.set_title("Hexbin (log density)")
    ax.set_xlabel("x")
    ax.set_ylabel("y")
    cb = fig.colorbar(hb, ax=ax)
    cb.set_label("log10(count)")
    ax.grid(False)

    fig.suptitle("Distributions: 1D and 2D Density", fontsize=14)
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
