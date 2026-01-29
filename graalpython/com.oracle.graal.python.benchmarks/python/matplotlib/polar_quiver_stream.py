#!/usr/bin/env python3
"""
Vector and polar plots: polar plot, quiver, and streamplot on separate pages.
Saves a multi-page PDF using PdfPages.

Run:
  python tst/polar_quiver_stream.py
"""

from pathlib import Path

# Use a non-interactive backend to work in headless environments
import matplotlib
matplotlib.use("Agg")

import matplotlib.pyplot as plt
import numpy as np
from matplotlib.backends.backend_pdf import PdfPages


def page_polar(ax):
    # Polar demo with multiple radii and an area fill
    theta = np.linspace(0, 2*np.pi, 512)
    r1 = 1.0 + 0.3*np.sin(5*theta)
    r2 = 0.7 + 0.2*np.cos(3*theta + 0.5)

    ax.plot(theta, r1, color="#1f77b4", linewidth=2.0, label="r1(θ) = 1 + 0.3 sin(5θ)")
    ax.plot(theta, r2, color="#ff7f0e", linewidth=2.0, linestyle="--", label="r2(θ) = 0.7 + 0.2 cos(3θ+0.5)")
    ax.fill_between(theta, r2, color="#ff7f0e", alpha=0.25, step="mid")
    ax.set_theta_zero_location("N")
    ax.set_theta_direction(-1)
    ax.set_title("Polar Plot", va="bottom")
    ax.grid(True, alpha=0.4)
    ax.legend(loc="upper right", bbox_to_anchor=(1.25, 1.15), frameon=False)


def page_quiver(ax):
    # Quiver plot for a simple rotational vector field
    n = 25
    x = np.linspace(-2.0, 2.0, n)
    y = np.linspace(-2.0, 2.0, n)
    X, Y = np.meshgrid(x, y)

    # Vector field: rotation around origin
    U = -Y
    V = X
    speed = np.hypot(U, V)

    q = ax.quiver(X, Y, U, V, speed, cmap="viridis", pivot="mid", angles="xy", scale=35, width=0.006)
    ax.set_aspect("equal", adjustable="box")
    ax.set_title("Quiver: Rotational Field")
    ax.set_xlabel("x")
    ax.set_ylabel("y")
    cb = ax.figure.colorbar(q, ax=ax, pad=0.01)
    cb.set_label("|v|")
    ax.grid(True, linestyle="--", alpha=0.3)


def page_streamplot(ax):
    # Streamplot with linewidth and color mapped to speed
    x = np.linspace(-3.0, 3.0, 200)
    y = np.linspace(-3.0, 3.0, 200)
    X, Y = np.meshgrid(x, y)

    # Double-vortex-like field
    U = 1 - (X**2) + (Y**2)
    V = -2*X*Y
    speed = np.sqrt(U**2 + V**2)

    lw = 1.5 * speed / (speed.max() + 1e-12)
    strm = ax.streamplot(X, Y, U, V, color=speed, linewidth=lw, cmap="plasma", density=1.4, arrowsize=1.2)
    ax.set_aspect("equal", adjustable="box")
    ax.set_title("Streamplot: Speed-coded")
    ax.set_xlabel("x")
    ax.set_ylabel("y")
    cb = ax.figure.colorbar(strm.lines, ax=ax, pad=0.01)
    cb.set_label("|v|")
    ax.grid(True, linestyle="--", alpha=0.25)


def main() -> None:
    out_path = Path(__file__).parent / "vector_and_polar.pdf"

    with PdfPages(out_path) as pdf:
        # Page 1: Polar
        fig1 = plt.figure(figsize=(6.2, 5.5), dpi=150)
        ax1 = fig1.add_subplot(111, projection="polar")
        page_polar(ax1)
        fig1.tight_layout()
        pdf.savefig(fig1)
        plt.close(fig1)

        # Page 2: Quiver
        fig2, ax2 = plt.subplots(figsize=(6.2, 5.0), dpi=150)
        page_quiver(ax2)
        fig2.tight_layout()
        pdf.savefig(fig2)
        plt.close(fig2)

        # Page 3: Streamplot
        fig3, ax3 = plt.subplots(figsize=(6.6, 5.2), dpi=150)
        page_streamplot(ax3)
        fig3.tight_layout()
        pdf.savefig(fig3)
        plt.close(fig3)

    print(f"Wrote PDF: {out_path.resolve()}")


if __name__ == "__main__":
    main()
