import sys
import os
# Add benchmark directory to path to allow import of bm_pickle.py
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import bm_pickle

def run():
    bm_pickle.__benchmark__(["pickle_dict"])


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


def dependencies():
    return ["bm_pickle.py"]
