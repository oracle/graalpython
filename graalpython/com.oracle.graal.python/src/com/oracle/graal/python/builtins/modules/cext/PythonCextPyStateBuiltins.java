/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.cext;

import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Direct;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Ignored;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Int;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Pointer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyFrameObjectTransfer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectBorrowed;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyThreadState;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Py_ssize_t;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Void;

import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBinaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiNullaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiUnaryBuiltinNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext;
import com.oracle.graal.python.builtins.objects.cext.capi.PThreadState;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.thread.PThread;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.frame.GetCurrentFrameRef;
import com.oracle.graal.python.nodes.frame.ReadCallerFrameNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonContext.PythonThreadState;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.ThreadLocalAction;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

public final class PythonCextPyStateBuiltins {

    @CApiBuiltin(ret = Int, args = {}, acquireGil = false, call = Ignored)
    abstract static class GraalPyPrivate_GILState_Check extends CApiNullaryBuiltinNode {

        @Specialization
        Object check() {
            return PythonContext.get(this).ownsGil() ? 1 : 0;
        }
    }

    @CApiBuiltin(ret = Int, args = {}, acquireGil = false, call = Ignored)
    abstract static class GraalPyPrivate_GILState_Ensure extends CApiNullaryBuiltinNode {

        @Specialization
        static Object save(@Cached GilNode gil) {
            boolean acquired = gil.acquire();
            return acquired ? 1 : 0;
        }
    }

    @CApiBuiltin(ret = Void, args = {}, acquireGil = false, call = Ignored)
    abstract static class GraalPyPrivate_GILState_Release extends CApiNullaryBuiltinNode {

        @Specialization
        static Object restore(
                        @Cached GilNode gil) {
            gil.release(true);
            return PNone.NO_VALUE;
        }
    }

    @CApiBuiltin(ret = PyThreadState, args = {Pointer}, call = Ignored)
    abstract static class GraalPyPrivate_ThreadState_Get extends CApiUnaryBuiltinNode {

        @Specialization(limit = "1")
        static Object get(Object tstateCurrentPtr,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @CachedLibrary("tstateCurrentPtr") InteropLibrary lib) {
            PythonThreadState pythonThreadState = context.getThreadState(context.getLanguage(inliningTarget));
            if (!lib.isNull(tstateCurrentPtr)) {
                pythonThreadState.setNativeThreadLocalVarPointer(tstateCurrentPtr);
            }
            return PThreadState.getOrCreateNativeThreadState(pythonThreadState);
        }
    }

    @CApiBuiltin(ret = Void, args = {}, call = Ignored)
    abstract static class GraalPyPrivate_BeforeThreadDetach extends CApiNullaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        Object doIt() {
            getContext().disposeThread(Thread.currentThread(), true);
            return PNone.NO_VALUE;
        }
    }

    @CApiBuiltin(ret = PyObjectBorrowed, args = {}, call = Direct)
    abstract static class PyThreadState_GetDict extends CApiNullaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        static PDict get(
                        @Bind Node inliningTarget,
                        @Bind PythonContext context) {
            PythonThreadState threadState = context.getThreadState(context.getLanguage(inliningTarget));
            PDict threadStateDict = threadState.getDict();
            if (threadStateDict == null) {
                threadStateDict = PFactory.createDict(context.getLanguage());
                threadState.setDict(threadStateDict);
            }
            return threadStateDict;
        }
    }

    @CApiBuiltin(ret = Int, args = {ArgDescriptor.UNSIGNED_LONG, PyObject}, call = Direct)
    abstract static class PyThreadState_SetAsyncExc extends CApiBinaryBuiltinNode {
        public static final TruffleLogger LOGGER = CApiContext.getLogger(PyThreadState_SetAsyncExc.class);

        @Specialization
        @TruffleBoundary
        int doIt(long id, Object exceptionObject) {
            for (Thread thread : getContext().getThreads()) {
                if (PThread.getThreadId(thread) == id) {
                    if (PGuards.isNoValue(exceptionObject)) {
                        LOGGER.warning("The application used PyThreadState_SetAsyncExc to clear an exception on another thread. " +
                                        "This is not supported and ignored by GraalPy.");
                        return 1;
                    }
                    ThreadLocalAction action = new ThreadLocalAction(true, false) {
                        static final int MAX_MISSED_COUNT = 20;
                        int missedCount = 0;

                        @Override
                        protected void perform(Access access) {
                            if (missedCount == MAX_MISSED_COUNT) {
                                throw PRaiseNode.raiseExceptionObjectStatic(null, exceptionObject);
                            }
                            // If possible, we do not want to raise in some internal code, it could
                            // corrupt internal data structures.
                            Node location = access.getLocation();
                            if (location != null) {
                                RootNode rootNode = location.getRootNode();
                                if (rootNode instanceof PRootNode && !rootNode.isInternal()) {
                                    throw PRaiseNode.raiseExceptionObjectStatic(null, exceptionObject);
                                }
                            }
                            // Heuristic fabricated out of thin air:
                            if (missedCount++ < MAX_MISSED_COUNT) {
                                if (missedCount % 2 == 0) {
                                    try {
                                        Thread.sleep(1);
                                    } catch (InterruptedException ignored) {
                                    }
                                }
                                getContext().getEnv().submitThreadLocal(new Thread[]{thread}, this);
                            }
                        }
                    };
                    getContext().getEnv().submitThreadLocal(new Thread[]{thread}, action);
                    return 1;
                }
            }
            return 0;
        }
    }

    @CApiBuiltin(ret = PyFrameObjectTransfer, args = {PyThreadState}, call = Direct)
    abstract static class PyThreadState_GetFrame extends CApiUnaryBuiltinNode {
        @Specialization
        PFrame get(@SuppressWarnings("unused") Object threadState,
                        @Bind Node inliningTarget,
                        @Cached GetCurrentFrameRef getCurrentFrameRef,
                        @Cached ReadCallerFrameNode readCallerFrameNode) {
            PFrame.Reference frameRef = getCurrentFrameRef.execute(null, inliningTarget);
            return readCallerFrameNode.executeWith(frameRef, 0);
        }
    }

    @CApiBuiltin(ret = PyObjectBorrowed, args = {Py_ssize_t}, call = Ignored)
    abstract static class GraalPyPrivate_State_FindModule extends CApiUnaryBuiltinNode {

        @Specialization
        Object doGeneric(long mIndex) {
            try {
                int i = PInt.intValueExact(mIndex);
                Object result = getCApiContext().getModuleByIndex(i);
                if (result == null) {
                    return getNativeNull();
                }
                return result;
            } catch (CannotCastException | OverflowException e) {
                return getNativeNull();
            }
        }
    }
}
