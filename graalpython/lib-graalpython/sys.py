# Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# The Universal Permissive License (UPL), Version 1.0
#
# Subject to the condition set forth below, permission is hereby granted to any
# person obtaining a copy of this software, associated documentation and/or
# data (collectively the "Software"), free of charge and under any and all
# copyright rights in the Software, and any and all patent rights owned or
# freely licensable by each licensor hereunder covering either (i) the
# unmodified Software as contributed to or provided by such licensor, or (ii)
# the Larger Works (as defined below), to deal in both
#
# (a) the Software, and
#
# (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
# one is included with the Software each a "Larger Work" to which the Software
# is contributed by such licensors),
#
# without restriction, including without limitation the rights to copy, create
# derivative works of, display, perform, and distribute the Software and make,
# use, sell, offer for sale, import, export, have made, and have sold the
# Software and the Larger Work(s), and to sublicense the foregoing rights on
# either these or other terms.
#
# This license is subject to the following condition:
#
# The above copyright notice and either this complete permission notice or at a
# minimum a reference to the UPL must be included in all copies or substantial
# portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.

def make_implementation_info():
    from _descriptor import SimpleNamespace, make_named_tuple_class
    version_info_type = make_named_tuple_class(
        "version_info", ["major", "minor", "micro", "releaselevel", "serial"]
    )
    return SimpleNamespace(
        name="graalpython",
        cache_tag="graalpython",
        version=version_info_type(version_info),
        _multiarch=__gmultiarch
    )
implementation = make_implementation_info()
del make_implementation_info
del __gmultiarch
version_info = implementation.version
hexversion = ((version_info.major << 24) |
              (version_info.minor << 16) |
              (version_info.micro << 8) |
              (0xa << 4) | # 0xA is alpha, 0xB is beta, 0xC is rc, 0xF is final
              (version_info.serial << 0))


def make_flags_class():
    from _descriptor import make_named_tuple_class
    get_set_descriptor = type(type(make_flags_class).__code__)

    names = ["bytes_warning", "debug", "dont_write_bytecode",
             "hash_randomization", "ignore_environment", "inspect",
             "interactive", "isolated", "no_site", "no_user_site", "optimize",
             "quiet", "verbose", "dev_mode", "utf8_mode"]

    flags_class = make_named_tuple_class("sys.flags", names)

    def make_func(i):
        def func(self):
            return __graalpython__.flags[i]
        return func

    for i, f in enumerate(names):
        setattr(flags_class, f, get_set_descriptor(fget=make_func(i), name=f, owner=flags_class))

    return flags_class


flags = make_flags_class()()
del make_flags_class


def make_float_info_class():
    from _descriptor import make_named_tuple_class
    return make_named_tuple_class(
        "float_info",
        ["max",
         "max_exp",
         "max_10_exp",
         "min",
         "min_exp",
         "min_10_exp",
         "dig",
         "mant_dig",
         "epsilon",
         "radix",
         "rounds"]
    )
float_info = make_float_info_class()(float_info)
del make_float_info_class

float_repr_style = 'short'

def make_int_info_class():
    from _descriptor import make_named_tuple_class
    return make_named_tuple_class(
        "int_info",
        ["bits_per_digit",
         "sizeof_digit"]
    )
int_info = make_int_info_class()((32, 4))
del make_int_info_class


def make_hash_info_class():
    from _descriptor import make_named_tuple_class
    return make_named_tuple_class(
        "hash_info",
        ["algorithm",
         "cutoff",
         "hash_bits",
         "imag",
         "inf",
         "modulus",
         "nan",
         "seed_bits",
         "width"]
    )
hash_info = make_hash_info_class()(hash_info)
del make_hash_info_class


def make_unraisable_hook_args_class():
    from _descriptor import make_named_tuple_class
    return make_named_tuple_class(
        "UnraisableHookArgs",
        ["exc_type", "exc_value", "exc_traceback", "err_msg", "object"],
    )
__UnraisableHookArgs = make_unraisable_hook_args_class()
del make_unraisable_hook_args_class


meta_path = []
path_hooks = []
path_importer_cache = {}
# these will be initialized explicitly from Java:
# prefix, base_prefix, exec_prefix, base_exec_prefix
warnoptions = []


# default prompt for interactive shell
ps1 = ">>> "

# continue prompt for interactive shell
ps2 = "... "

# Stub audit hooks implementation for PEP 578
def audit(str, *args):
    pass

def addaudithook(hook):
    pass


@__graalpython__.builtin
def exit(arg=0):
    raise SystemExit(arg)


def make_excepthook():
    def simple_print_traceback(e):
        print("Traceback (most recent call last):", file=stderr);
        tb = e.__traceback__
        while tb is not None:
            print('  File "%s", line %d, in %s' % (
                tb.tb_frame.f_code.co_filename,
                tb.tb_lineno,
                tb.tb_frame.f_code.co_name
            ), file=stderr)
            tb = tb.tb_next
        msg = str(e)
        if msg:
            print("%s: %s" % (type(e).__qualname__, msg), file=stderr)
        else:
            print(type(e).__qualname__, file=stderr)

    def __print_traceback__(typ, value, tb):
        try:
            import traceback
            lines = traceback.format_exception(typ, value, tb)
            # CPython's C traceback printer diverges from traceback.print_exception in this small detail.
            # We'd like to contribute to CPython to fix the divergence, but for now we do just
            # a string substitution to pass the tests
            lines[-1] = lines[-1].replace(f'<unprintable {typ.__name__} object>', f'<exception str() failed>')
            for line in lines:
                print(line, file=stderr, end="")
        except BaseException as exc:
            print("Error in sys.excepthook:\n", file=stderr)
            simple_print_traceback(exc)
            print("\nOriginal exception was:\n", file=stderr)
            simple_print_traceback(value)

    return __print_traceback__


__excepthook__ = make_excepthook()
excepthook = __excepthook__
del make_excepthook


def make_unraisablehook():
    def __unraisablehook__(unraisable, /):
        try:
            if unraisable.object:
                try:
                    r = repr(unraisable.object)
                except Exception:
                    r = "<object repr() failed>"
                if unraisable.err_msg:
                    print(f"{unraisable.err_msg}: {r}", file=stderr)
                else:
                    print(f"Exception ignored in: {r}", file=stderr)
            elif unraisable.err_msg:
                print(f"{unraisable.err_msg}:", file=stderr)
        except BaseException:
            # let it fall through to the exception printer
            pass
        __excepthook__(unraisable.exc_type, unraisable.exc_value, unraisable.exc_traceback)

    return __unraisablehook__


__unraisablehook__ = make_unraisablehook()
unraisablehook = __unraisablehook__
del make_unraisablehook


@__graalpython__.builtin
def breakpointhook(*args, **kws):
    import importlib, os, warnings
    hookname = os.getenv('PYTHONBREAKPOINT')
    if hookname is None or len(hookname) == 0:
        warnings.warn('Graal Python cannot run pdb, yet, consider using `--inspect` on the commandline', RuntimeWarning)
        hookname = 'pdb.set_trace'
    elif hookname == '0':
        return None
    modname, dot, funcname = hookname.rpartition('.')
    if dot == '':
        modname = 'builtins'
    try:
        module = importlib.import_module(modname)
        hook = getattr(module, funcname)
    except:
        warnings.warn(
            'Ignoring unimportable $PYTHONBREAKPOINT: "{}"'.format(
                hookname),
            RuntimeWarning)
    else:
        return hook(*args, **kws)


__breakpointhook__ = breakpointhook


@__graalpython__.builtin
def getrecursionlimit():
    return 1000


@__graalpython__.builtin
def displayhook(value):
    if value is None:
        return
    builtins = modules['builtins']
    # Set '_' to None to avoid recursion
    builtins._ = None
    text = repr(value)
    try:
        stdout.write(text)
    except UnicodeEncodeError:
        bytes = text.encode(stdout.encoding, 'backslashreplace')
        if hasattr(stdout, 'buffer'):
            stdout.buffer.write(bytes)
        else:
            text = bytes.decode(stdout.encoding, 'strict')
            stdout.write(text)
    stdout.write("\n")
    builtins._ = value


__displayhook__ = displayhook
