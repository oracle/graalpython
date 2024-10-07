# Dummy package for testing the tox support
from setuptools import setup, find_packages
setup(
    name='leftpad',
    version='1.0.0',
    author='GraalPy developers',
    author_email='graalvm-users@oss.oracle.com',
    package_dir={'': 'src'},
    packages=find_packages(where='src'),
)
