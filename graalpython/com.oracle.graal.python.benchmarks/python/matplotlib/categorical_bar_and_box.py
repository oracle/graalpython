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
Categorical plots: grouped bar chart with error bars and a boxplot.
Saves a multi-page PDF using PdfPages.
"""


def _colorize_boxplot(bp, facecolor="#1f77b4", edgecolor="black", alpha=0.6):
    for box in bp["boxes"]:
        box.set(facecolor=facecolor, edgecolor=edgecolor, alpha=alpha)
    for median in bp["medians"]:
        median.set(color="black", linewidth=1.2)
    for whisker in bp["whiskers"]:
        whisker.set(color=edgecolor, linewidth=1.0)
    for cap in bp["caps"]:
        cap.set(color=edgecolor, linewidth=1.0)
    for flier in bp["fliers"]:
        flier.set(marker="o", markersize=3, markerfacecolor="white", markeredgecolor=edgecolor, alpha=0.7)


def run():
    # Execute all imports inside the `run` method so they're measured
    from pathlib import Path
    # Use a non-interactive backend to work in headless environments
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    import numpy as np
    from matplotlib.backends.backend_pdf import PdfPages

    # Ensure we have version info in the logs
    print(f"Using matplotlib version '{matplotlib.__version__}'")
    print(f"Using numpy version '{np.__version__}'")

    out_path = Path(__file__).parent / "categorical_plots.pdf"

    rng = np.random.default_rng(2024)
    categories = ["A", "B", "C", "D"]
    n = len(categories)

    with PdfPages(out_path) as pdf:
        # Page 1: Grouped bar chart with error bars
        x = np.arange(n)
        bar_w = 0.35

        means1 = rng.normal(3.0, 0.4, n)
        errs1 = rng.uniform(0.1, 0.4, n)

        means2 = rng.normal(2.2, 0.5, n)
        errs2 = rng.uniform(0.1, 0.4, n)

        fig1, ax1 = plt.subplots(figsize=(7, 4), dpi=150)
        b1 = ax1.bar(x - bar_w / 2, means1, yerr=errs1, width=bar_w, capsize=3,
                     label="Series 1", color="#1f77b4", edgecolor="black", alpha=0.85)
        b2 = ax1.bar(x + bar_w / 2, means2, yerr=errs2, width=bar_w, capsize=3,
                     label="Series 2", color="#ff7f0e", edgecolor="black", alpha=0.85)

        ax1.set_xticks(x, categories)
        ax1.set_ylabel("Value")
        ax1.set_title("Grouped Bar Chart with Error Bars")
        ax1.grid(axis="y", linestyle="--", alpha=0.35)
        ax1.legend(loc="best")

        # Annotate bars with heights
        for bars in (b1, b2):
            for rect in bars:
                h = rect.get_height()
                ax1.text(rect.get_x() + rect.get_width() / 2.0, h + 0.05,
                         f"{h:.2f}", ha="center", va="bottom", fontsize=8, rotation=0)

        fig1.tight_layout()
        pdf.savefig(fig1)
        plt.close(fig1)

        # Page 2: Boxplot across categories
        # Generate some synthetic distributions with varying mean/variance
        mus = [2.8, 3.2, 2.5, 3.5]
        sigmas = [0.50, 0.60, 0.45, 0.55]
        data = [rng.normal(loc=m, scale=s, size=400) for m, s in zip(mus, sigmas)]

        fig2, ax2 = plt.subplots(figsize=(7, 4), dpi=150)
        bp = ax2.boxplot(
            data,
            labels=categories,
            widths=0.6,
            patch_artist=True,
            showfliers=True,
            whis=(5, 95),
        )
        # Colorize boxes with a palette
        palette = ["#1f77b4", "#ff7f0e", "#2ca02c", "#9467bd"]
        for box, color in zip(bp["boxes"], palette):
            box.set(facecolor=color, edgecolor="black", alpha=0.6)

        # Style the rest
        for median in bp["medians"]:
            median.set(color="black", linewidth=1.4)
        for whisker in bp["whiskers"]:
            whisker.set(color="black", linewidth=1.0)
        for cap in bp["caps"]:
            cap.set(color="black", linewidth=1.0)
        for flier in bp["fliers"]:
            flier.set(marker="o", markersize=3, markerfacecolor="white", markeredgecolor="black", alpha=0.7)

        ax2.set_title("Boxplot by Category")
        ax2.set_ylabel("Distribution")
        ax2.grid(axis="y", linestyle="--", alpha=0.35)
        fig2.tight_layout()

        pdf.savefig(fig2)
        plt.close(fig2)


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
