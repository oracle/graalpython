# Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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
import os
import sys
from tests import util

# IMPORTANT: DO NOT MOVE!
# This test checks that lineno works on frames,
# it MUST stay on this line!
def test_lineno():
    assert sys._getframe(0).f_lineno == 47

if not util.IS_BYTECODE_DSL: # Blocked by GR-61955
    # IMPORTANT: DO NOT MOVE!
    def test_nested_lineno():
        def test_nested():
            return sys._getframe(0)

        f = test_nested()
        assert f.f_lineno == 53

    # IMPORTANT: DO NOT MOVE!
    def test_nested_lineno_return_loc():
        def test_nested():
            f = sys._getframe(0)
            if True:
                return f
            return None

        f = test_nested()
        assert f.f_lineno == 63

    # IMPORTANT: DO NOT MOVE!
    def test_nested_lineno_implicit_return():
        f = None
        def test_nested():
            nonlocal f
            f = sys._getframe(0)
            dummy = 42

        test_nested()
        assert f.f_lineno == 75

    # IMPORTANT: DO NOT MOVE!
    def test_nested_lineno_finally():
        def test_nested():
            try:
                return sys._getframe(0)
            finally:
                dummy = 42

        f = test_nested()
        assert f.f_lineno == 86, f.f_lineno

    # IMPORTANT: DO NOT MOVE!
    def test_nested_lineno_multiline_return():
        def test_nested():
            f = sys._getframe(0)
            if f:
                return (
                    f)
            return None

        f = test_nested()
        assert f.f_lineno == 96

    # IMPORTANT: DO NOT MOVE!
    def test_nested_lineno_raise():
        f = None
        def test_nested():
            nonlocal f
            f = sys._getframe(0)
            raise ValueError("should happen")
            raise ArgumentError("should not happen")
        try:
            test_nested()
        except ValueError as e:
            assert "should happen" in str(e)
            assert f.f_lineno == 109


def test_read_and_write_locals():
    a = 1
    b = ''
    ls = sys._getframe(0).f_locals
    assert ls['a'] == 1
    assert ls['b'] == ''
    ls['a'] = sys
    assert ls['a'] == sys


def test_backref():
    a = 'test_backref'

    def foo():
        a = 'foo'
        return sys._getframe(0).f_back

    assert foo().f_locals['a'] == 'test_backref'

    def get_frame():
        return sys._getframe(0)

    def get_frame_caller():
        return get_frame()

    def do_stackwalk(f):
        stack = []
        while f:
            stack.append(f)
            f = f.f_back
        return stack

    stack = do_stackwalk(get_frame_caller())
    actual_fnames = [n.f_code.co_name for n in stack]
    expected_prefix = ['get_frame', 'get_frame_caller', 'test_backref']
    assert len(stack) >= len(expected_prefix)
    assert expected_prefix == actual_fnames[:len(expected_prefix)]


def test_backref_recursive():
    def get_frame():
        return sys._getframe(0)

    def foo(i):
        if i == 1:
            f = get_frame()
            stack = []
            while f:
                stack.append(f)
                f = f.f_back
            return stack
        else:
            # This recursive call will cause
            return foo(i + 1)

    def bar():
        return foo(0)

    s = bar()
    print([n.f_code for n in s])


def test_code():
    code = sys._getframe().f_code
    assert code.co_filename == test_code.__code__.co_filename
    assert code.co_firstlineno == test_code.__code__.co_firstlineno
    assert code.co_name == test_code.__code__.co_name


def test_getframemodulename():
    assert sys._getframemodulename() == __name__
    assert sys._getframemodulename(0) == __name__
    assert sys._getframemodulename(-1) == __name__
    assert sys._getframemodulename(10**6) is None

    namespace = {"sys": sys, "__name__": "test_getframemodulename_custom"}
    exec("def get_module_name(depth=0): return sys._getframemodulename(depth)", namespace)
    assert namespace["get_module_name"]() == "test_getframemodulename_custom"
    assert namespace["get_module_name"](1) == __name__


def test_getframemodulename_uses_function_module():
    def get_module_name():
        return sys._getframemodulename()

    old_module = get_module_name.__module__
    try:
        get_module_name.__module__ = "test_getframemodulename_function_module"
        assert get_module_name() == "test_getframemodulename_function_module"
    finally:
        get_module_name.__module__ = old_module


def test_getframemodulename_missing_name():
    namespace = {"sys": sys}
    exec("def get_module_name(): return sys._getframemodulename()", namespace)
    assert namespace["get_module_name"]() is None


def test_getframemodulename_non_string_name():
    namespace = {"sys": sys, "__name__": 42}
    exec("def get_module_name(): return sys._getframemodulename()", namespace)
    assert namespace["get_module_name"]() == 42


def test_getframemodulename_audit():
    seen = []

    def hook(event, args):
        if event == "sys._getframemodulename":
            seen.append(args)

    sys.addaudithook(hook)
    assert sys._getframemodulename() == __name__
    assert seen[-1] == (0,)


def test_builtins():
    assert print == sys._getframe().f_builtins["print"]


def test_locals_sync():
    a = 1
    l = locals()
    assert l == {'a': 1}
    b = 2
    # Forces caller frame materialization, this used to erroneously cause the locals dict to update
    globals()
    assert l == {'a': 1}
    # Now this should really cause the locals dict to update
    locals()
    assert l == {'a': 1, 'b': 2, 'l': l}


def test_locals_cells():
    x = 1

    def foo():
        return x, locals()

    assert foo()[1]['x'] == 1

    cell = foo.__closure__[0]

    assert type(locals()['cell']).__name__ == 'cell'


def test_locals_freevar_in_class():
    x = 1

    class Foo:
        c = x
        assert 'c' in locals()
        assert 'x' not in locals()


def test_backref_from_traceback():
    def bar():
        raise RuntimeError

    def foo():
        bar()

    try:
        foo()
    except Exception as e:
        assert e.__traceback__.tb_frame.f_back.f_code == sys._getframe(0).f_back.f_code
        assert e.__traceback__.tb_next.tb_next.tb_frame.f_back.f_code == foo.__code__
        assert e.__traceback__.tb_next.tb_frame.f_back.f_code == test_backref_from_traceback.__code__


def test_backref_from_traceback_after_cached_transition():
    def bar(should_raise):
        if should_raise:
            raise RuntimeError

    def foo(should_raise):
        bar(should_raise)

    for _ in range(64): # we probably do not execute 64-times in uncached
        foo(False)

    try:
        foo(True)
    except Exception as e:
        assert e.__traceback__.tb_next.tb_next.tb_frame.f_back.f_code == foo.__code__
        assert e.__traceback__.tb_next.tb_frame.f_back.f_code == test_backref_from_traceback_after_cached_transition.__code__


def test_frame_from_another_thread():
    import sys, threading
    event1 = threading.Event()
    event2 = threading.Event()
    event3 = threading.Event()
    event4 = threading.Event()
    frame = None
    def target():
        # Mind the line numbers
        nonlocal frame
        frame = sys._getframe()
        a = 1
        event1.set()
        event2.wait(timeout=60)
        b = 2
        event3.set()
        event4.wait(timeout=60)
    thread = threading.Thread(target=target)
    thread.start()
    event1.wait(timeout=60)
    firstlineno = target.__code__.co_firstlineno
    assert 5 <= frame.f_lineno - firstlineno <= 6
    assert frame.f_locals['a'] == 1
    assert 'b' not in frame.f_locals
    event2.set()
    event3.wait(timeout=60)
    assert 8 <= frame.f_lineno - firstlineno <= 9
    assert frame.f_locals['b'] == 2
    event4.set()
    thread.join(timeout=60)


OTHER_RUNNING_INNER = 'running_inner'
OTHER_RUNNING_OUTER = 'running_outer'
OTHER_TERMINATED = 'terminated'


def current_frames_includes_other_thread(test_case):
    import sys, threading

    ready = threading.Event()
    outer_ready = threading.Event()
    release_inner = threading.Event()
    release_outer = threading.Event()
    worker_ident = None

    def target():
        nonlocal worker_ident
        worker_ident = threading.get_ident()
        def target_inner():
            my_local_var = 60
            ready.set()
            release_inner.wait(timeout=my_local_var)
        target_local_var = 13
        target_inner()
        outer_ready.set()
        release_outer.wait(60)
        return target_local_var

    thread = threading.Thread(target=target)
    thread.start()
    try:
        assert ready.wait(timeout=60)
        frames = sys._current_frames()
        if test_case == OTHER_TERMINATED:
            release_inner.set()
            release_outer.set()
            thread.join(timeout=60)
        elif test_case == OTHER_RUNNING_OUTER:
            release_inner.set()
            assert outer_ready.wait(timeout=60)
        assert worker_ident in frames

        frame = frames[worker_ident]
        if frame is None:
            return # we hit the timeout

        def format_frame(frame):
            code = frame.f_code
            return f"{code.co_name}@{os.path.basename(code.co_filename)}"

        seen_frames = []
        while frame is not None and frame.f_code.co_name != "target_inner":
            seen_frames.append(format_frame(frame))
            frame = frame.f_back

        message = f"test case: {test_case}; traversed frames: {', '.join(seen_frames) or '<none>'}"
        assert frame is not None, message
        assert frame.f_code.co_name == "target_inner", f"{frame.f_code.co_name=}; {message}"
        assert "target_inner" in repr(frame), f"{repr(frame)=}; {message}"
        assert "my_local_var" in frame.f_locals, f"{frame.f_locals.keys()=}; {message}"

        frame = frame.f_back
        assert frame is not None
        assert "target_inner" not in repr(frame)
        assert "target" in repr(frame)
        assert frame.f_code.co_name == "target"
        assert "target_local_var" in frame.f_locals
    finally:
        release_inner.set()
        release_outer.set()
        thread.join(timeout=60)


def test_current_frames_includes_other_thread_terminated():
    current_frames_includes_other_thread(OTHER_TERMINATED)

def test_current_frames_includes_other_thread_in_inner():
    current_frames_includes_other_thread(OTHER_RUNNING_INNER)

def test_current_frames_includes_other_thread_in_outer():
    current_frames_includes_other_thread(OTHER_RUNNING_OUTER)


# this must be the last test!
def test_clearing_globals():
    global foo, junk
    foo = 123
    assert "foo" in globals().keys()
    assert "junk" not in globals().keys()
    junk = globals().clear()
    assert "foo" not in globals().keys()
    assert "junk" in globals().keys()
