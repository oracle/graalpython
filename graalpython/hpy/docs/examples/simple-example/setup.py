from setuptools import setup, Extension
from os import path

setup(
    name="hpy-simple-example",
    hpy_ext_modules=[
        Extension('simple', sources=[path.join(path.dirname(__file__), 'simple.c')]),
    ],
    setup_requires=['hpy'],
)
