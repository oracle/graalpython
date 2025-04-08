# -*- coding: utf-8 -*-

from setuptools import setup, Extension

# now we can add --hpy-abi=universal to the invocation of setup.py to build a
# universal binary
setup(
    name="hpy-porting-example",
    hpy_ext_modules=[
        Extension("step_03_hpy_final", sources=["step_03_hpy_final.c"])
    ],
    py_modules=["step_03_hpy_final"],
    setup_requires=['hpy'],
)
