from setuptools import setup, Extension
from os import path

setup(
    name="hpy-type-example",
    hpy_ext_modules=[
        Extension('simple_type', sources=['simple_type.c']),
        Extension('builtin_type', sources=['builtin_type.c']),
    ],
    setup_requires=['hpy'],
)
