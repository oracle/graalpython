# Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

def hasattr(obj, key):
    try:
        type(obj).__getattribute__(obj, key)
        return True
    except AttributeError:
        return False


# We re-define the print function here, because that makes it easier for us to
# deal with the default arguments. The builtin version simply requires all
# arguments.
def make_print():
    builtin_print = print

    def func(*objects, sep=" ", end="\n", file=None, flush=False):
        if file is not None:
            sz = len(objects) - 1
            for i in range(sz):
                file.write(str(objects[i]))
                file.write(str(sep))
            file.write(str(objects[-1]))
            file.write(str(end))
        else:
            builtin_print(tuple(objects), sep, end, file, flush)
    return func
print = make_print()
del make_print


# We close over the globals to avoid leaking sys to the builtins scope
def make_globals_function():
    import sys

    def func():
        return sys._getframe(1).f_globals
    return func
globals = make_globals_function()
del make_globals_function


def make_locals_function():
    import sys

    def func():
        return sys._getframe(1).f_locals
    return func
locals = make_locals_function()
del make_locals_function


def any(iterable):
    for i in iterable:
        if i:
            return True
    return False


def all(iterable):
    for i in iterable:
        if not i:
            return False
    return True


def filter(func, iterable):
    result = []
    for i in iterable:
        if func(i):
            result.append(i)
    return tuple(result)


def exec(source, globals=None, locals=None):
    # compile returns the source if already a code object
    return eval(compile(source, "<exec>", "exec"), globals, locals)


# This is re-defined later during bootstrap in classes.py
def __build_class__(func, name, *bases, metaclass=None, **kwargs):
    """
    Stage 1 helper function used by the class statement
    """
    if metaclass is not None or len(kwargs) > 0:
        import _posix
        print("Tried to use keyword arguments in class definition too early during bootstrap")
        _posix.exit(-1)
    ns = {}
    func(ns)
    return type(name, bases, ns)


class map(object):
    def __init__(self, func, iterable, *args):
        self.__func = func
        iterators = [iter(iterable)]
        for i in args:
            iterators.append(iter(i))
        self.__iterators = iterators

    def __next__(self):
        args = []
        for it in self.__iterators:
            args.append(next(it))
        return self.__func(*args)

    def __iter__(self):
        return self


def _caller_locals():
    import sys
    return sys._getframe(2).f_locals


def vars(*obj):
    """Return a dictionary of all the attributes currently bound in obj.  If
    called with no argument, return the variables bound in local scope."""
    if len(obj) == 0:
        # TODO inlining _caller_locals().items() in the dict comprehension does not work for now, investigate!
        items = _caller_locals().items()
        return {k: v for k, v in items}
    elif len(obj) != 1:
        raise TypeError("vars() takes at most 1 argument.")
    try:
        return dict(obj[0].__dict__)
    except AttributeError:
        raise TypeError("vars() argument must have __dict__ attribute")


def format(value, format_spec=''):
    """Return value.__format__(format_spec)

    format_spec defaults to the empty string.
    See the Format Specification Mini-Language section of help('FORMATTING') for
    details."""
    return value.__format__(format_spec)


def sorted(iterable, key=None, reverse=False):
    """Return a new list containing all items from the iterable in ascending order.

    A custom key function can be supplied to customize the sort order, and the
    reverse flag can be set to request the result in descending order.
    """
    result = list(iterable)
    result.sort(key=key, reverse=reverse)
    return result
