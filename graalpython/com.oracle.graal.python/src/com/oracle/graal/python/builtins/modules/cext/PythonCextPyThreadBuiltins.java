/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PY_THREAD_TYPE_LOCK;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Pointer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Void;
import static com.oracle.graal.python.builtins.objects.ints.PInt.intValue;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBinaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiNullaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiUnaryBuiltinNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor;
import com.oracle.graal.python.builtins.objects.thread.LockBuiltins.AcquireLockNode;
import com.oracle.graal.python.builtins.objects.thread.LockBuiltins.ReleaseLockNode;
import com.oracle.graal.python.builtins.objects.thread.PLock;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;

public final class PythonCextPyThreadBuiltins {

    private static final long LOCK_MASK = 0xA10C000000000000L;

    @CApiBuiltin(ret = PY_THREAD_TYPE_LOCK, args = {}, call = Direct)
    abstract static class PyThread_allocate_lock extends CApiNullaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        public long allocate() {
            CApiContext context = getCApiContext();
            long id = context.lockId.incrementAndGet() ^ LOCK_MASK;
            PLock lock = factory().createLock(PythonBuiltinClassType.PLock);
            context.locks.put(id, lock);
            return id;
        }
    }

    @CApiBuiltin(ret = Int, args = {PY_THREAD_TYPE_LOCK, Int}, call = Direct)
    abstract static class PyThread_acquire_lock extends CApiBinaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        public int acquire(long id, int waitflag,
                        @Cached AcquireLockNode acquireNode) {
            PLock lock = getCApiContext().locks.get(id);
            if (lock == null) {
                throw badInternalCall("lock");
            }
            return intValue((boolean) acquireNode.execute(null, lock, waitflag != 0 ? -1 : 0, PNone.NONE));
        }
    }

    @CApiBuiltin(ret = Void, args = {PY_THREAD_TYPE_LOCK}, call = Direct)
    abstract static class PyThread_release_lock extends CApiUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        public Object release(long id,
                        @Cached ReleaseLockNode releaseNode) {
            CApiContext context = getCApiContext();
            PLock lock = context.locks.get(id);
            if (lock == null) {
                throw badInternalCall("lock");
            }
            releaseNode.execute(null, lock);
            return PNone.NO_VALUE;
        }
    }

    @CApiBuiltin(ret = ArgDescriptor.Long, args = {}, call = Ignored)
    abstract static class PyTruffle_tss_create extends CApiNullaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        long tssCreate() {
            return getCApiContext().nextTssKey();
        }
    }

    @CApiBuiltin(ret = Pointer, args = {ArgDescriptor.Long}, call = Ignored)
    abstract static class PyTruffle_tss_get extends CApiUnaryBuiltinNode {
        @Specialization
        Object tssGet(long key) {
            Object value = getCApiContext().tssGet(key);
            if (value == null) {
                return getNULL();
            }
            return value;
        }
    }

    @CApiBuiltin(ret = Int, args = {ArgDescriptor.Long, Pointer}, call = Ignored)
    abstract static class PyTruffle_tss_set extends CApiBinaryBuiltinNode {
        @Specialization
        int tssSet(long key, Object value) {
            getCApiContext().tssSet(key, value);
            return 0;
        }
    }

    @CApiBuiltin(ret = Void, args = {ArgDescriptor.Long}, call = Ignored)
    abstract static class PyTruffle_tss_delete extends CApiUnaryBuiltinNode {
        @Specialization
        Object tssDelete(long key) {
            getCApiContext().tssDelete(key);
            return PNone.NONE;
        }
    }
}
