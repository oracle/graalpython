#!/usr/bin/env python3
# Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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

import inspect
import sys
import types
from functools import wraps


def make_fast_depends_utils_module():
    # Model the frame-walking dependency-injection helper shape found in the wild.
    module = types.ModuleType("autogen.fast_depends.utils")
    filename = "autogen/fast_depends/utils.py"
    module.__file__ = filename
    code = r'''
import inspect


def collect_outer_stack_locals():
    frame = inspect.currentframe()
    current_filename = __file__
    locals_ = {}
    while frame:
        frame_filename = frame.f_code.co_filename
        if frame_filename != current_filename:
            locals_.update(frame.f_locals)
        frame = frame.f_back
    return locals_
'''
    exec(compile(code, filename, "exec"), module.__dict__)
    return module


fast_depends_utils = make_fast_depends_utils_module()
collect_outer_stack_locals = fast_depends_utils.collect_outer_stack_locals


class Depends:
    def __init__(self, dependency):
        self.dependency = dependency


def get_typed_signature(call):
    outer_locals = collect_outer_stack_locals()
    signature = inspect.signature(call)
    resolved = []
    for name, parameter in signature.parameters.items():
        annotation = parameter.annotation
        if isinstance(annotation, str):
            try:
                annotation = eval(annotation, call.__globals__, outer_locals)
            except NameError:
                annotation = object
        resolved.append((name, annotation, parameter.default))
    return resolved


def build_call_model(call, depth):
    typed_signature = get_typed_signature(call)
    if depth <= 0:
        return typed_signature
    for _, _, default in typed_signature:
        if isinstance(default, Depends):
            build_call_model(default.dependency, depth - 1)
    return typed_signature


def inject(call=None, *, depth=3):
    def decorate(func):
        @wraps(func)
        def func_wrapper(*args, **kwargs):
            build_call_model(func, depth)
            return func(*args, **kwargs)

        return func_wrapper

    if call is None:
        return decorate
    return decorate(call)


def make_dependency_chain(index, depth):
    class LocalPayload:
        __slots__ = ("value",)

        def __init__(self, value):
            self.value = value

    def leaf(item: "LocalPayload" = None):
        return item.value if item else index

    dependency = leaf
    for layer in range(depth):
        previous = dependency

        def dependency(item: "LocalPayload" = None, dep=Depends(previous), layer=layer):
            if item is None:
                item = LocalPayload(index + layer)
            return dep.dependency(item)

    return dependency


def generator_adapter(value):
    yield value
    yield value + 1


def make_tool(index, depth):
    dependency = make_dependency_chain(index, depth)

    @inject(depth=depth)
    def tool(payload: "LocalPayload" = None, dep=Depends(dependency)):
        return sum(value for value in generator_adapter(dep.dependency(payload)))

    class LocalPayload:
        __slots__ = ("value",)

        def __init__(self, value):
            self.value = value

    return tool


def register_for_execution(functions):
    registry = {}

    def decorator(func):
        build_call_model(func, 2)
        registry[func.__name__] = func
        functions.append(func)
        return func

    return decorator, registry


def create_agent(function_count, depth):
    functions = []
    decorator, registry = register_for_execution(functions)
    for index in range(function_count):
        decorator(make_tool(index, depth))
    return registry, functions


def invoke_workflow(functions, iterations):
    total = 0
    for _ in range(iterations):
        for func in functions:
            total += func()
    return total


def main():
    iterations = int(sys.argv[1]) if len(sys.argv) > 1 else 1000
    function_count = int(sys.argv[2]) if len(sys.argv) > 2 else 4
    depth = int(sys.argv[3]) if len(sys.argv) > 3 else 4
    _, functions = create_agent(function_count, depth)
    print("done result=%d" % invoke_workflow(functions, iterations))


if __name__ == "__main__":
    main()
