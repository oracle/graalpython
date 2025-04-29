from setuptools import setup, Extension
import platform

cpp_compile_extra_args = []

if platform.system() == "Windows":
    compile_extra_args = ['/WX']
    cpp_compile_extra_args = [
        "/std:c++latest",  # MSVC C7555
    ]
else:
    compile_extra_args = ['-Werror']


setup(
    name="hpy-pof",
    packages = ['pofpackage'],
    zip_safe=False,
    hpy_ext_modules=[
        Extension('pof', 
                    sources=['pof.c'],
                    extra_compile_args=compile_extra_args),
        Extension('pofpackage.foo',
                    sources=['pofpackage/foo.c'],
                    extra_compile_args=compile_extra_args),
        Extension('pofcpp',
                  sources=['pofcpp.cpp'],
                  language='C++',
                  extra_compile_args=compile_extra_args + cpp_compile_extra_args),
        Extension('pofpackage.bar',
                  sources=['pofpackage/bar.cpp'],
                  language='C++',
                  extra_compile_args=compile_extra_args + cpp_compile_extra_args),
    ],
    setup_requires=['hpy'],
)
