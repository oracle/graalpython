from setuptools import setup, Extension

setup(
    name="hpy.microbench",
    setup_requires=['cffi', 'hpy'],
    ext_modules = [
        Extension('cpy_simple',
                  ['src/cpy_simple.c'],
                  extra_compile_args=['-g'])
    ],
    hpy_ext_modules = [
        Extension('hpy_simple',
                  ['src/hpy_simple.c'],
                  extra_compile_args=['-g']),
    ],
    cffi_modules=["_valgrind_build.py:ffibuilder"],
)
