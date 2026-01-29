#!/usr/bin/env python3
"""
Run all matplotlib demo scripts in this directory in a single Python process.

Usage:
  python tst/run_all_matplotlib_demos_inproc.py
"""

from pathlib import Path
import runpy
import time

# Force a headless backend once for the entire process, before any pyplot import.
import matplotlib
matplotlib.use("Agg")
# Make subsequent matplotlib.use(...) calls inside the demo scripts a no-op.
matplotlib.use = lambda *args, **kwargs: None  # type: ignore[assignment]

BASE = Path(__file__).parent

SCRIPTS = [
    "simple_line_plot.py",
    "subplots_and_styles.py",
    "categorical_bar_and_box.py",
    "distributions_hist_2d.py",
    "polar_quiver_stream.py",
    "3d_surface_wireframe.py",
]
while True:
  print(f"=== RUN iteration ===")
  start1 = time.time()
  for script in SCRIPTS:
      print(f"  === RUN {script} ===")
      start = time.time()
      runpy.run_path(str(BASE / script), run_name="__main__")
      dur = time.time() - start
      print(f"  time: {dur}")
  dur = time.time() - start1
  print(f"overall time: {dur}")

print("All demo scripts executed in one process.")
