#!/usr/bin/env python3
"""
Simple line plot example that saves to PDF.

Run:
  python tst/simple_line_plot.py
"""

import os
from pathlib import Path

# Use a non-interactive backend to work in headless environments
import matplotlib
matplotlib.use("Agg")

import matplotlib.pyplot as plt
import numpy as np


def main() -> None:
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

    print(f"Wrote PDF: {out_path.resolve()}")


if __name__ == "__main__":
    main()
