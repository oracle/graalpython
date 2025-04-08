from setuptools import setup, Extension
from os import path

setup(
    name="hpy-mixed-example",
    hpy_ext_modules=[
        Extension('mixed', sources=[path.join(path.dirname(__file__), 'mixed.c')]),
    ],
    setup_requires=['hpy'],
)
