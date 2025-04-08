"""
Parse public_api.h and generate various stubs around
"""
import sys
import py
import pycparser
from packaging import version

if version.parse(pycparser.__version__) < version.parse('2.21'):
    raise ImportError('You need pycparser>=2.21 to run autogen')

from . import generate
from .ctx import (autogen_ctx_h,
                  autogen_ctx_def_h,
                  cpython_autogen_ctx_h)
from .trampolines import (autogen_trampolines_h,
                          cpython_autogen_api_impl_h,
                          universal_autogen_ctx_impl_h)
from .hpyfunc import autogen_hpyfunc_declare_h
from .hpyfunc import autogen_hpyfunc_trampoline_h
from .hpyfunc import autogen_ctx_call_i
from .hpyfunc import autogen_cpython_hpyfunc_trampoline_h
from .hpyslot import autogen_hpyslot_h
from .debug import (autogen_debug_ctx_init_h,
                    autogen_debug_wrappers,
                    autogen_debug_ctx_call_i)
from .trace import (autogen_tracer_ctx_init_h,
                    autogen_tracer_wrappers,
                    autogen_trace_func_table_c)
from .pypy import autogen_pypy_txt
from .doc import (autogen_function_index,
                  autogen_doc_api_mapping)

DEFAULT_GENERATORS = (autogen_ctx_h,
                      autogen_ctx_def_h,
                      cpython_autogen_ctx_h,
                      autogen_trampolines_h,
                      cpython_autogen_api_impl_h,
                      universal_autogen_ctx_impl_h,
                      autogen_hpyfunc_declare_h,
                      autogen_hpyfunc_trampoline_h,
                      autogen_ctx_call_i,
                      autogen_cpython_hpyfunc_trampoline_h,
                      autogen_hpyslot_h,
                      autogen_debug_ctx_init_h,
                      autogen_debug_wrappers,
                      autogen_debug_ctx_call_i,
                      autogen_tracer_ctx_init_h,
                      autogen_tracer_wrappers,
                      autogen_trace_func_table_c,
                      autogen_pypy_txt,
                      autogen_function_index,
                      autogen_doc_api_mapping)


def main():
    if len(sys.argv) != 2:
        print('Usage: python -m hpy.tools.autogen OUTDIR')
        sys.exit(1)
    outdir = py.path.local(sys.argv[1])

    generate(DEFAULT_GENERATORS, outdir)


if __name__ == '__main__':
    main()
