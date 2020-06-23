# Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

@__graalpython__.builtin
def hasattr(obj, key):
    default = object()
    return getattr(obj, key, default) is not default


@__graalpython__.builtin
def any(iterable):
    for i in iterable:
        if i:
            return True
    return False


@__graalpython__.builtin
def all(iterable):
    for i in iterable:
        if not i:
            return False
    return True


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


from sys import _getframe as __getframe__


@__graalpython__.builtin
def vars(*obj):
    """Return a dictionary of all the attributes currently bound in obj.  If
    called with no argument, return the variables bound in local scope."""
    if len(obj) == 0:
        # TODO inlining _caller_locals().items() in the dict comprehension does not work for now, investigate!
        return __getframe__(0).f_locals
    elif len(obj) != 1:
        raise TypeError("vars() takes at most 1 argument.")
    try:
        return obj[0].__dict__
    except AttributeError:
        raise TypeError("vars() argument must have __dict__ attribute")


@__graalpython__.builtin
def format(value, format_spec=''):
    """Return value.__format__(format_spec)

    format_spec defaults to the empty string.
    See the Format Specification Mini-Language section of help('FORMATTING') for
    details."""
    return type(value).__format__(value, format_spec)


@__graalpython__.builtin
def sorted(iterable, key=None, reverse=False):
    """Return a new list containing all items from the iterable in ascending order.

    A custom key function can be supplied to customize the sort order, and the
    reverse flag can be set to request the result in descending order.
    """
    result = list(iterable)
    result.sort(key=key, reverse=reverse)
    return result


@__graalpython__.builtin
def input(prompt=None):
    import sys
    print(prompt, end="", flush=True)
    result = []
    while True:
        ch = sys.stdin.read(1)
        if ch:
            if ch == "\n":
                break
            result.append(ch)
        else:
            break
    return "".join(result)


class filter(object):
    def __init__(self, predicateOrNone, iterable):
        self.predicateOrNone = predicateOrNone
        self.iterable = iter(iterable)

    def __iter__(self):
        return self

    def __next__(self):
        while True:
            item = next(self.iterable)
            if self.predicateOrNone is None:
                if item:
                    return item
            else:
                if self.predicateOrNone(item):
                    return item
