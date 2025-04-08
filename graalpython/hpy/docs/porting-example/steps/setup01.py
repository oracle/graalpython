# -*- coding: utf-8 -*-

from setuptools import setup, Extension


setup(
    name="hpy-porting-example",
    hpy_ext_modules=[
        Extension("step_01_hpy_legacy", sources=["step_01_hpy_legacy.c"])
    ],
    py_modules=["step_01_hpy_legacy"],
    setup_requires=['hpy'],
)
