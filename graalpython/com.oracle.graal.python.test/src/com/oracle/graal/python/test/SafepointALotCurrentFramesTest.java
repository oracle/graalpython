/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.test;

import java.io.ByteArrayOutputStream;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.junit.Assert;
import org.junit.Test;

public class SafepointALotCurrentFramesTest {

    @Test
    public void currentFramesWhileThreadsExecute() {
        ByteArrayOutputStream errorOutput = new ByteArrayOutputStream();
        try (Engine engine = Engine.newBuilder("python").allowExperimentalOptions(true).//
                        option("engine.SafepointALot", "true").build();
                        Context context = Context.newBuilder("python").engine(engine).allowExperimentalOptions(true).allowAllAccess(true).err(errorOutput).build()) {
            context.eval("python", """
                            import sys
                            import threading
                            import time
                            from contextlib import nullcontext

                            worker_count = 16
                            run_for = 10.0
                            start = threading.Event()
                            stop = threading.Event()
                            errors = []

                            def leaf(value):
                                return value + 1

                            def generated_values(value):
                                for offset in range(4):
                                    yield leaf(value + offset)

                            def work():
                                with nullcontext():
                                    values = [item * 2 for item in generated_values(0)]
                                    return {item: item + 1 for item in values}

                            def record_error(error):
                                errors.append((type(error).__name__, str(error)))

                            def thread_exception(args):
                                record_error(args.exc_value)

                            threading.excepthook = thread_exception

                            def watch_frames():
                                try:
                                    start.wait()
                                    while not stop.is_set():
                                        sys._current_frames()
                                except BaseException as error:
                                    record_error(error)

                            def worker():
                                try:
                                    start.wait()
                                    while not stop.is_set():
                                        work()
                                except BaseException as error:
                                    record_error(error)

                            watcher = threading.Thread(target=watch_frames)
                            workers = [threading.Thread(target=worker) for _ in range(worker_count)]
                            watcher.start()
                            for thread in workers:
                                thread.start()
                            start.set()
                            try:
                                time.sleep(run_for)
                            finally:
                                stop.set()
                                watcher.join()
                                for thread in workers:
                                    thread.join()

                            if errors:
                                raise AssertionError(errors)
                            """);
        }
        assertNoUnexpectedErrors(errorOutput);
    }

    @Test
    public void weakrefCallbacksWhileMainThreadExecutes() {
        ByteArrayOutputStream errorOutput = new ByteArrayOutputStream();
        try (Engine engine = Engine.newBuilder("python").allowExperimentalOptions(true).//
                        option("engine.SafepointALot", "true").build();
                        Context context = Context.newBuilder("python").engine(engine).allowExperimentalOptions(true).allowAllAccess(true).err(errorOutput).build()) {
            context.eval("python", """
                            import sys
                            import gc
                            import time
                            import threading
                            import weakref
                            from contextlib import nullcontext

                            run_for = 10.0
                            start = threading.Event()
                            stop = threading.Event()
                            errors = []
                            live_refs = {}
                            shared_list = []
                            shared_dict = {"main": 0, "callback": 0}
                            callback_count = 0

                            def leaf(value):
                                return value + 1

                            def generated_values(value):
                                for offset in range(4):
                                    yield leaf(value + offset)

                            def work(role):
                                with nullcontext():
                                    values = [item * 2 for item in generated_values(0)]
                                    shared_list.extend(values)
                                    if len(shared_list) > 256:
                                        del shared_list[:128]
                                    shared_dict[role] = shared_dict[role] + 1
                                    return {item: item + 1 for item in values}

                            def record_error(error):
                                errors.append((type(error).__name__, str(error)))

                            def thread_exception(args):
                                record_error(args.exc_value)

                            threading.excepthook = thread_exception

                            class Target:
                                pass

                            def weakref_callback(reference):
                                global callback_count
                                try:
                                    live_refs.pop(id(reference), None)
                                    callback_count += 1
                                    frame = sys._getframe()
                                    while frame is not None:
                                        frame = frame.f_back
                                    work("callback")
                                except BaseException as error:
                                    record_error(error)

                            def submit_callbacks():
                                try:
                                    start.wait()
                                    while not stop.is_set():
                                        for _ in range(128):
                                            target = Target()
                                            reference = weakref.ref(target, weakref_callback)
                                            live_refs[id(reference)] = reference
                                        gc.collect()
                                except BaseException as error:
                                    record_error(error)

                            submitter = threading.Thread(target=submit_callbacks)
                            submitter.start()
                            start.set()
                            deadline = time.monotonic() + run_for
                            try:
                                while time.monotonic() < deadline:
                                    work("main")
                            finally:
                                stop.set()
                                submitter.join()
                                for _ in range(8):
                                    gc.collect()
                                    time.sleep(0.01)

                            if errors:
                                raise AssertionError(errors)
                            if callback_count == 0:
                                raise AssertionError("weakref callbacks were not submitted")
                            if shared_dict["main"] == 0 or shared_dict["callback"] == 0 or not shared_list:
                                raise AssertionError("shared data structures were not mutated")
                            """);
        }
        assertNoUnexpectedErrors(errorOutput);
    }

    private static void assertNoUnexpectedErrors(ByteArrayOutputStream errorOutput) {
        Assert.assertEquals("unexpected output in the context error stream", "", errorOutput.toString());
    }
}
