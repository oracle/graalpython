# Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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


# Stub audit hooks implementation for PEP 578
def audit(str, *args):
    pass

def addaudithook(hook):
    pass


# CPython builds for distros report empty strings too, because they are built from tarballs, not git
_git = ("graalpython", '', '')


@__graalpython__.builtin
def exit(arg=None):
    # see SystemExit_init, tuple of size 1 is unpacked
    code = arg
    if isinstance(arg, tuple) and len(arg) == 1:
        code = arg[0]
    raise SystemExit(code)


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
    return __graalpython__.sys_state.recursionlimit

@__graalpython__.builtin
def setrecursionlimit(value):
    if not isinstance(value, int):
        raise TypeError("an integer is required")
    if value <= 0:
        raise ValueError("recursion limit must be greater or equal than 1")
    __graalpython__.sys_state.recursionlimit = value

@__graalpython__.builtin
def getcheckinterval():
    return __graalpython__.sys_state.checkinterval

@__graalpython__.builtin
def setcheckinterval(value):
    import warnings
    warnings.warn("sys.getcheckinterval() and sys.setcheckinterval() are deprecated. Use sys.setswitchinterval() instead.", DeprecationWarning)
    if not isinstance(value, int):
        raise TypeError("an integer is required")
    __graalpython__.sys_state.checkinterval = value

@__graalpython__.builtin
def getswitchinterval():
    return __graalpython__.sys_state.switchinterval

@__graalpython__.builtin
def setswitchinterval(value):
    if not isinstance(value, (int, float)):
        raise TypeError("must be real number, not str")
    if value <= 0:
        raise ValueError("switch interval must be strictly positive")
    __graalpython__.sys_state.switchinterval = value

@__graalpython__.builtin
def displayhook(value):
    if value is None:
        return
    builtins = modules['builtins']
    # Set '_' to None to avoid recursion
    builtins._ = None
    text = repr(value)
    try:
        local_stdout = stdout
    except NameError as e:
        raise RuntimeError("lost sys.stdout") from e
    try:
        local_stdout.write(text)
    except UnicodeEncodeError:
        bytes = text.encode(local_stdout.encoding, 'backslashreplace')
        if hasattr(local_stdout, 'buffer'):
            local_stdout.buffer.write(bytes)
        else:
            text = bytes.decode(local_stdout.encoding, 'strict')
            local_stdout.write(text)
    local_stdout.write("\n")
    builtins._ = value


__displayhook__ = displayhook
