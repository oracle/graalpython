#!/usr/bin/env python3
"""
3D plotting example: surface, wireframe, and contour projections.
Saves to PDF.

Run:
  python tst/3d_surface_wireframe.py
"""

from pathlib import Path

# Use a non-interactive backend to work in headless environments
import matplotlib
matplotlib.use("Agg")

import matplotlib.pyplot as plt
import numpy as np
from mpl_toolkits.mplot3d import Axes3D  # noqa: F401  # needed for 3D projection


def main() -> None:
    out_path = Path(__file__).parent / "surface_3d.pdf"

    # Domain and function
    x = np.linspace(-4, 4, 200)
    y = np.linspace(-4, 4, 200)
    X, Y = np.meshgrid(x, y)

    R = np.sqrt(X**2 + Y**2) + 1e-12
    Z = np.sin(R) / R + 0.15 * np.cos(3*X) * np.sin(3*Y) / (1 + 0.5 * (X**2 + Y**2))

    fig = plt.figure(figsize=(7.5, 5.8), dpi=150)
    ax = fig.add_subplot(111, projection="3d")

    # Surface with colormap
    surf = ax.plot_surface(X, Y, Z, cmap="viridis", linewidth=0, antialiased=True, alpha=0.95)

    # Wireframe overlay (sparser grid to avoid clutter)
    step = 10
    ax.plot_wireframe(X[::step, ::step], Y[::step, ::step], Z[::step, ::step],
                      rstride=1, cstride=1, color="k", linewidth=0.3, alpha=0.5)

    # Contour projections on Z, X, and Y planes
    z_offset = Z.min() - 0.4
    ax.contour(X, Y, Z, zdir="z", offset=z_offset, cmap="viridis", levels=18, linewidths=0.8)

    x_offset = x.min() - 0.6
    ax.contour(X, Y, Z, zdir="x", offset=x_offset, cmap="magma", levels=14, linewidths=0.7)

    y_offset = y.max() + 0.6
    ax.contour(X, Y, Z, zdir="y", offset=y_offset, cmap="plasma", levels=14, linewidths=0.7)

    # Axes labels and limits
    ax.set_xlabel("X")
    ax.set_ylabel("Y")
    ax.set_zlabel("Z")

    ax.set_xlim(x_offset, x.max())
    ax.set_ylim(y.min(), y_offset)
    ax.set_zlim(z_offset, Z.max())

    # Colorbar
    cb = fig.colorbar(surf, ax=ax, shrink=0.6, aspect=12, pad=0.08)
    cb.set_label("Z value")

    # View angle
    ax.view_init(elev=25, azim=-55)

    ax.set_title("3D Surface + Wireframe + Contour Projections")
    fig.tight_layout()

    fig.savefig(out_path, format="pdf")
    plt.close(fig)

    print(f"Wrote PDF: {out_path.resolve()}")


if __name__ == "__main__":
    main()
