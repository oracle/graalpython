/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Long;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PY_THREAD_TYPE_LOCK;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Pointer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.UNSIGNED_LONG;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Void;
import static com.oracle.graal.python.builtins.objects.ints.PInt.intValue;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltinNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext;
import com.oracle.graal.python.builtins.objects.thread.PLock;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.nodes.EncapsulatingNodeReference;

public final class PythonCextPyThreadBuiltins {

    private static final long LOCK_MASK = 0xA10C000000000000L;

    @CApiBuiltin(ret = PY_THREAD_TYPE_LOCK, args = {}, call = Direct, acquireGil = false, canRaise = false)
    public static long PyThread_allocate_lock() {
        CApiContext context = CApiBuiltinNode.getStaticCApiContext();
        long id = context.lockId.incrementAndGet() ^ LOCK_MASK;
        PLock lock = PFactory.createLock(PythonLanguage.get(null));
        context.locks.put(id, lock);
        return id;
    }

    @CApiBuiltin(ret = Int, args = {PY_THREAD_TYPE_LOCK, Int}, call = Direct, acquireGil = false, canRaise = true)
    public static int PyThread_acquire_lock(long id, int waitflag) {
        CApiContext context = CApiBuiltinNode.getStaticCApiContext();
        PLock lock = context.locks.get(id);
        if (lock == null) {
            throw PythonCextBuiltins.badInternalCall("PyThread_acquire_lock", "lock");
        }
        boolean result;
        if (waitflag != 0) {
            result = lock.acquireBlocking(EncapsulatingNodeReference.getCurrent().get());
        } else {
            result = lock.acquireNonBlocking();
        }
        return intValue(result);
    }

    @CApiBuiltin(ret = Void, args = {PY_THREAD_TYPE_LOCK}, call = Direct, acquireGil = false, canRaise = true)
    public static void PyThread_release_lock(long id) {
        CApiContext context = CApiBuiltinNode.getStaticCApiContext();
        PLock lock = context.locks.get(id);
        if (lock == null) {
            throw PythonCextBuiltins.badInternalCall("PyThread_release_lock", "lock");
        }
        lock.release();
    }

    @CApiBuiltin(ret = Long, args = {}, call = Ignored, acquireGil = false, canRaise = false)
    public static long GraalPyPrivate_tss_create() {
        return CApiBuiltinNode.getStaticCApiContext().nextTssKey();
    }

    @CApiBuiltin(ret = Pointer, args = {Long}, call = Ignored, acquireGil = false, canRaise = false)
    public static long GraalPyPrivate_tss_get(long key) {
        return CApiBuiltinNode.getStaticCApiContext().tssGet(key);
    }

    @CApiBuiltin(ret = Int, args = {Long, Pointer}, call = Ignored, acquireGil = false, canRaise = false)
    public static int GraalPyPrivate_tss_set(long key, long value) {
        CApiBuiltinNode.getStaticCApiContext().tssSet(key, value);
        return 0;
    }

    @CApiBuiltin(ret = Void, args = {Long}, call = Ignored, acquireGil = false, canRaise = false)
    public static void GraalPyPrivate_tss_delete(long key) {
        CApiBuiltinNode.getStaticCApiContext().tssDelete(key);
    }

    @SuppressWarnings("deprecation") // deprecated in JDK19
    @CApiBuiltin(ret = UNSIGNED_LONG, args = {}, call = Direct, acquireGil = false, canRaise = false)
    public static long PyThread_get_thread_ident() {
        return Thread.currentThread().getId();
    }
}
