# -*- coding: utf-8 -*-

from setuptools import setup, Extension


setup(
    name="hpy-porting-example",
    hpy_ext_modules=[
        Extension("step_02_hpy_legacy", sources=["step_02_hpy_legacy.c"])
    ],
    py_modules=["step_02_hpy_legacy"],
    setup_requires=['hpy'],
)
