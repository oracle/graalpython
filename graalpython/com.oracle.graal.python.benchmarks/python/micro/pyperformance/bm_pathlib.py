"""
Test the performance of pathlib operations.

This benchmark stresses the creation of small objects, globbing, and system
calls.
"""

# Python imports
import os
import pathlib
import tempfile


NUM_FILES = 2000


def generate_filenames(tmp_path, num_files):
    i = 0
    while num_files:
        for ext in [".py", ".txt", ".tar.gz", ""]:
            i += 1
            yield os.path.join(tmp_path, str(i) + ext)
            num_files -= 1


tmp_path = None
def setup():
    global tmp_path
    tmp_path = tempfile.mkdtemp()
    for fn in generate_filenames(tmp_path, NUM_FILES):
        with open(fn, "wb") as f:
            f.write(b'benchmark')


def bench_pathlib(loops, tmp_path):
    base_path = pathlib.Path(tmp_path)

    # Warm up the filesystem cache and keep some objects in memory.
    path_objects = list(base_path.iterdir())
    # FIXME: does this code really cache anything?
    for p in path_objects:
        p.stat()
    assert len(path_objects) == NUM_FILES, len(path_objects)

    range_it = range(loops)

    for _ in range_it:
        # Do something simple with each path.
        for p in base_path.iterdir():
            p.stat()
        for p in base_path.glob("*.py"):
            p.stat()
        for p in base_path.iterdir():
            p.stat()
        for p in base_path.glob("*.py"):
            p.stat()


def run():
    bench_pathlib(8, tmp_path)


def warmupIterations():
    return 0


def iterations():
    return 10


def summary():
    return {
        "name": "OutlierRemovalAverageSummary",
        "lower-threshold": 0.0,
        "upper-threshold": 1.0,
    }
