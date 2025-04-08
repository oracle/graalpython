# setup.py

from setuptools import setup, Extension
from os import path

DIR = path.dirname(__file__)
setup(
    name="hpy-quickstart",
    hpy_ext_modules=[
        Extension('quickstart', sources=[path.join(DIR, 'quickstart.c')]),
    ],
    setup_requires=['hpy'],
)
