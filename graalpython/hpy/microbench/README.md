To run the microbenchmarks
--------------------------

1. You need to have `hpy` installed in your virtuanenv. The easiest way
   to do it is:

       $ cd /path/to/hpy
       $ python setup.py develop

2. Build the extension modules needed for the microbenchmarks

       $ cd /path/to/hpy/microbench
       $ pip install cffi # needed to build _valgrind
       $ python setup.py build_ext --inplace

2. `py.test -v`

3. To run only cpy or hpy tests, use -m (to select markers):

       $ py.test -v -m hpy
       $ py.test -v -m cpy

4. Step (2) build `hpy_simple` using the CPython ABI by default. If you want
   to benchmark the universal mode, you need to build it explicitly:

       $ cd /path/to/hpy/microbench
       $ rm *.so  # make sure to delete CPython-ABI versions
       $ python setup.py --hpy-abi=universal build_ext --inplace
       $ py.test -v
