# Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
import random
import re
import sys
try:
    import _sysconfig as syscfg
except Exception:
    import sysconfig as syscfg

if syscfg.get_config_var('WITH_THREAD'):
    import threading
    import unittest
    from test import support

    from _thread import start_new_thread

    thread = support.import_module('_thread')
    import time
    import weakref


    NUMTASKS = 10
    NUMTRIPS = 3
    POLL_SLEEP = 0.010 # seconds = 10 ms

    _print_mutex = thread.allocate_lock()

    support.verbose = False


    def verbose_print(arg):
        """Helper function for printing out debugging output."""
        if support.verbose:
            with _print_mutex:
                print(arg)


    class BasicThreadTest(unittest.TestCase):

        def setUp(self):
            self.done_mutex = thread.allocate_lock()
            self.done_mutex.acquire()
            self.running_mutex = thread.allocate_lock()
            self.random_mutex = thread.allocate_lock()
            self.created = 0
            self.running = 0
            self.next_ident = 0

            self._threads = support.threading_setup()

        def tearDown(self):
            support.threading_cleanup(*self._threads)


    class ThreadRunningTests(BasicThreadTest):

        def newtask(self):
            with self.running_mutex:
                self.next_ident += 1
                verbose_print("creating task %s" % self.next_ident)
                thread.start_new_thread(self.task, (self.next_ident,))
                self.created += 1
                self.running += 1

        def task(self, ident):
            with self.random_mutex:
                delay = random.random() / 10000.0
            verbose_print("task %s will run for %sus" % (ident, round(delay*1e6)))
            time.sleep(delay)
            verbose_print("task %s done" % ident)
            with self.running_mutex:
                self.running -= 1
                if self.created == NUMTASKS and self.running == 0:
                    self.done_mutex.release()

        def test_starting_threads(self):
            # Basic test for thread creation.
            for i in range(NUMTASKS):
                self.newtask()
            verbose_print("waiting for tasks to complete...")
            self.done_mutex.acquire()
            verbose_print("all tasks done")

        def test_stack_size(self):
            # Various stack size tests.
            self.assertEqual(thread.stack_size(), 0, "initial stack size is not 0")

            thread.stack_size(0)
            self.assertEqual(thread.stack_size(), 0, "stack_size not reset to default")

        def test__count(self):
            # Test the _count() function.
            orig = thread._count()
            _append_lock = thread.allocate_lock()
            mut = thread.allocate_lock()
            mut.acquire()
            started = []
            done = []

            def task():
                with _append_lock:
                    started.append(None)
                mut.acquire()
                mut.release()
                with _append_lock:
                    done.append(None)

            thread.start_new_thread(task, ())
            while not started:
                time.sleep(POLL_SLEEP)
            self.assertEqual(thread._count(), orig + 1)
            # Allow the task to finish.
            mut.release()
            while not done:
                time.sleep(POLL_SLEEP)
            self.assertEqual(thread._count(), orig)

        # def test_save_exception_state_on_error(self):
        #     # See issue #14474
        #     def task():
        #         started.release()
        #         raise SyntaxError
        #
        #     def mywrite(self, *args):
        #         try:
        #             raise ValueError
        #         except ValueError:
        #             pass
        #         real_write(self, *args)
        #     c = thread._count()
        #     started = thread.allocate_lock()
        #     with support.captured_output("stderr") as stderr:
        #         real_write = stderr.write
        #         stderr.write = mywrite
        #         started.acquire()
        #         thread.start_new_thread(task, ())
        #         started.acquire()
        #         while thread._count() > c:
        #             time.sleep(POLL_SLEEP)
        #     self.assertIn("Traceback", stderr.getvalue())


    class Barrier(object):
        def __init__(self, num_threads):
            self.num_threads = num_threads
            self.waiting = 0
            self.checkin_mutex = thread.allocate_lock()
            self.checkout_mutex = thread.allocate_lock()
            self.checkout_mutex.acquire()

        def enter(self):
            self.checkin_mutex.acquire()
            self.waiting = self.waiting + 1
            if self.waiting == self.num_threads:
                self.waiting = self.num_threads - 1
                self.checkout_mutex.release()
                return
            self.checkin_mutex.release()

            self.checkout_mutex.acquire()
            self.waiting = self.waiting - 1
            if self.waiting == 0:
                self.checkin_mutex.release()
                return
            self.checkout_mutex.release()


    class BarrierTest(BasicThreadTest):

        def test_barrier(self):
            self.bar = Barrier(NUMTASKS)
            self.running = NUMTASKS
            for i in range(NUMTASKS):
                thread.start_new_thread(self.task2, (i,))
            verbose_print("waiting for tasks to end")
            self.done_mutex.acquire()
            verbose_print("tasks done")

        def task2(self, ident):
            for i in range(NUMTRIPS):
                if ident == 0:
                    # give it a good chance to enter the next
                    # barrier before the others are all out
                    # of the current one
                    delay = 0
                else:
                    with self.random_mutex:
                        delay = random.random() / 10000.0
                verbose_print("task %s will run for %sus" %
                              (ident, round(delay * 1e6)))
                time.sleep(delay)
                verbose_print("task %s entering %s" % (ident, i))
                self.bar.enter()
                verbose_print("task %s leaving barrier" % ident)
            with self.running_mutex:
                self.running -= 1
                # Must release mutex before releasing done, else the main thread can
                # exit and set mutex to None as part of global teardown; then
                # mutex.release() raises AttributeError.
                finished = self.running == 0
            if finished:
                self.done_mutex.release()


    def _wait():
        # A crude wait/yield function not relying on synchronization primitives.
        time.sleep(0.01)


    class Bunch(object):
        """
        A bunch of threads.
        """
        def __init__(self, f, n, wait_before_exit=False):
            """
            Construct a bunch of `n` threads running the same function `f`.
            If `wait_before_exit` is True, the threads won't terminate until
            do_finish() is called.
            """
            self.f = f
            self.n = n
            self.started = []
            self.finished = []
            self._can_exit = not wait_before_exit
            self._append_lock = thread.allocate_lock()

            def task():
                tid = threading.get_ident()
                # TODO: remove the append lock once append like ops are thread safe
                with self._append_lock:
                    self.started.append(tid)
                try:
                    f()
                finally:
                    # TODO: remove the append lock once append like ops are thread safe
                    with self._append_lock:
                        self.finished.append(tid)
                    while not self._can_exit:
                        _wait()
            try:
                for i in range(n):
                    start_new_thread(task, ())
            except:
                self._can_exit = True
                raise

        def wait_for_started(self):
            while len(self.started) < self.n:
                _wait()

        def wait_for_finished(self):
            while len(self.finished) < self.n:
                _wait()

        def do_finish(self):
            self._can_exit = True


    class BaseTestCase(unittest.TestCase):
        failureException = AssertionError

        def setUp(self):
            self._threads = support.threading_setup()

        def tearDown(self):
            support.threading_cleanup(*self._threads)
            # TODO: revert patch when os.waitpid(-1, ...) is implemented
            # support.reap_children()

        def assertLess(self, a, b, msg=None):
            if not a < b:
                standardMsg = '%s not less than %s' % (a, b)
                self.fail(self._formatMessage(msg, standardMsg))

        def assertGreaterEqual(self, a, b, msg=None):
            """Just like self.assertTrue(a >= b), but with a nicer default message."""
            if not a >= b:
                standardMsg = '%s not greater than or equal to %s' % (a, b)
                self.fail(self._formatMessage(msg, standardMsg))

        def assertTimeout(self, actual, expected):
            # The waiting and/or time.time() can be imprecise, which
            # is why comparing to the expected value would sometimes fail
            # (especially under Windows).
            self.assertGreaterEqual(actual, expected * 0.6)
            # Test nothing insane happened
            self.assertLess(actual, expected * 10.0)

        def assertRegexpMatches(self, text, expected_regexp, msg=None):
            """Fail the test unless the text matches the regular expression."""
            if isinstance(expected_regexp, str):
                expected_regexp = re.compile(expected_regexp)
            if not expected_regexp.search(text):
                msg = msg or "Regexp didn't match"
                msg = '%s: %r not found in %r' % (msg, expected_regexp.pattern, text)
                raise self.failureException(msg)


    class LockTests(BaseTestCase):
        locktype = thread.allocate_lock

        def test_constructor(self):
            lock = self.locktype()
            del lock

        @unittest.skipIf(sys.implementation.name == 'cpython' and sys.version_info[0:2] < (3, 5), "skipping for cPython versions < 3.5")
        def test_repr(self):
            lock = self.locktype()
            self.assertRegexpMatches(repr(lock), "<unlocked .* object (.*)?at .*>")
            del lock

        @unittest.skipIf(sys.implementation.name == 'cpython' and sys.version_info[0:2] < (3, 5), "skipping for cPython versions < 3.5")
        def test_locked_repr(self):
            lock = self.locktype()
            lock.acquire()
            self.assertRegexpMatches(repr(lock), "<locked .* object (.*)?at .*>")
            del lock

        def test_acquire_destroy(self):
            lock = self.locktype()
            lock.acquire()
            del lock

        def test_acquire_release(self):
            lock = self.locktype()
            lock.acquire()
            lock.release()
            del lock

        def test_try_acquire(self):
            lock = self.locktype()
            self.assertTrue(lock.acquire(False))
            lock.release()

        def test_try_acquire_contended(self):
            lock = self.locktype()
            lock.acquire()
            result = []

            def f():
                result.append(lock.acquire(False))
            Bunch(f, 1).wait_for_finished()
            self.assertFalse(result[0])
            lock.release()

        # def test_acquire_contended(self):
        #     lock = self.locktype()
        #     lock.acquire()
        #     N = 5
        #
        #     def f():
        #         lock.acquire()
        #         lock.release()
        #
        #     b = Bunch(f, N)
        #     b.wait_for_started()
        #     _wait()
        #     self.assertEqual(len(b.finished), 0)
        #     lock.release()
        #     b.wait_for_finished()
        #     self.assertEqual(len(b.finished), N)

        def test_with(self):
            lock = self.locktype()
            def f():
                lock.acquire()
                lock.release()
            def _with(err=None):
                with lock:
                    if err is not None:
                        raise err
            _with()
            # Check the lock is unacquired
            Bunch(f, 1).wait_for_finished()
            self.assertRaises(TypeError, _with, TypeError)
            # Check the lock is unacquired
            Bunch(f, 1).wait_for_finished()

        def test_thread_leak(self):
            # The lock shouldn't leak a Thread instance when used from a foreign
            # (non-threading) thread.
            lock = self.locktype()

            def f():
                lock.acquire()
                lock.release()
            n = len(threading.enumerate())
            # We run many threads in the hope that existing threads ids won't
            # be recycled.
            Bunch(f, 15).wait_for_finished()
            if len(threading.enumerate()) != n:
                # There is a small window during which a Thread instance's
                # target function has finished running, but the Thread is still
                # alive and registered.  Avoid spurious failures by waiting a
                # bit more (seen on a buildbot).
                time.sleep(0.4)
                self.assertEqual(n, len(threading.enumerate()))

        def test_timeout(self):
            lock = self.locktype()
            # Can't set timeout if not blocking
            self.assertRaises(ValueError, lock.acquire, 0, 1)
            # Invalid timeout values
            self.assertRaises(ValueError, lock.acquire, timeout=-100)
            self.assertRaises(OverflowError, lock.acquire, timeout=1e100)
            self.assertRaises(OverflowError, lock.acquire, timeout=thread.TIMEOUT_MAX + 1)
            # TIMEOUT_MAX is ok
            lock.acquire(timeout=thread.TIMEOUT_MAX)
            lock.release()
            t1 = time.time()
            self.assertTrue(lock.acquire(timeout=5))
            t2 = time.time()
            # Just a sanity test that it didn't actually wait for the timeout.
            self.assertLess(t2 - t1, 5)
            results = []

            def f():
                t1 = time.time()
                results.append(lock.acquire(timeout=0.5))
                t2 = time.time()
                results.append(t2 - t1)
            Bunch(f, 1).wait_for_finished()
            self.assertFalse(results[0])
            self.assertTimeout(results[1], 0.5)

        def test_weakref_exists(self):
            lock = self.locktype()
            ref = weakref.ref(lock)
            self.assertTrue(ref() is not None)

        # weakrefs are not yet full functional
        # def test_weakref_deleted(self):
        #     lock = self.locktype()
        #     ref = weakref.ref(lock)
        #     del lock
        #     self.assertIsNone(ref())

        def test_reacquire(self):
            # Lock needs to be released before re-acquiring.
            lock = self.locktype()
            phase = []

            def f():
                lock.acquire()
                phase.append(None)
                lock.acquire()
                phase.append(None)
            start_new_thread(f, ())
            while len(phase) == 0:
                _wait()
            _wait()
            self.assertEqual(len(phase), 1)
            lock.release()
            while len(phase) == 1:
                _wait()
            self.assertEqual(len(phase), 2)

        def test_different_thread(self):
            # Lock can be released from a different thread.
            lock = self.locktype()
            lock.acquire()

            def f():
                lock.release()
            b = Bunch(f, 1)
            b.wait_for_finished()
            lock.acquire()
            lock.release()

        def test_state_after_timeout(self):
            # Issue #11618: check that lock is in a proper state after a
            # (non-zero) timeout.
            lock = self.locktype()
            lock.acquire()
            self.assertFalse(lock.acquire(timeout=0.01))
            lock.release()
            self.assertFalse(lock.locked())
            self.assertTrue(lock.acquire(blocking=False))
