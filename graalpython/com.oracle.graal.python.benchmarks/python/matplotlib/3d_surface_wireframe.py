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
3D plotting example: surface, wireframe, and contour projections.
Saves to PDF.
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
