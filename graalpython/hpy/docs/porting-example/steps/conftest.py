# -*- coding: utf-8 -*-

""" Pytest configuration for the porting example tests. """

import glob
import os
import sys

import pytest


sys.path.insert(0, os.path.join(os.path.dirname(__file__)))


class PortingStep:
    def __init__(self, src):
        self.name = os.path.splitext(os.path.basename(src))[0]
        self.src = src

    def import_step(self):
        return __import__(self.name)


PORTING_STEPS = [
    PortingStep(src) for src in sorted(
        glob.glob(os.path.join(os.path.dirname(__file__), "step_*.c")))
]


@pytest.fixture(
    params=PORTING_STEPS,
    ids=[step.name for step in PORTING_STEPS],
)
def step(request):
    return request.param
