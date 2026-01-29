#!/usr/bin/env python3
"""
Distribution plots: 1D histogram, 2D histogram, and hexbin with colorbars.

Run:
  python tst/distributions_hist_2d.py
"""

from pathlib import Path

# Use a non-interactive backend to work in headless environments
import matplotlib
matplotlib.use("Agg")

import matplotlib.pyplot as plt
import numpy as np


def main() -> None:
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

    print(f"Wrote PDF: {out_path.resolve()}")


if __name__ == "__main__":
    main()
