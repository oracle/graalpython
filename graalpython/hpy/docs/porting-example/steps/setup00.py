# -*- coding: utf-8 -*-

from setuptools import setup, Extension


setup(
    name="hpy-porting-example",
    ext_modules=[
        Extension("step_00_c_api", sources=["step_00_c_api.c"])
    ],
    py_modules=["step_00_c_api"],
)
