/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.capi;

import static com.oracle.graal.python.builtins.objects.PythonAbstractObject.NATIVE_POINTER_FREED;
import static com.oracle.graal.python.builtins.objects.PythonAbstractObject.UNINITIALIZED;
import static com.oracle.graal.python.nfi2.NativeMemory.NULLPTR;
import static com.oracle.graal.python.nfi2.NativeMemory.callocPtrArray;
import static com.oracle.graal.python.nfi2.NativeMemory.mallocPtrArray;
import static com.oracle.graal.python.nfi2.NativeMemory.writePtrArrayElement;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.HandlePointerConverter;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructs;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.nfi2.NativeMemory;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonContext.CApiState;
import com.oracle.graal.python.runtime.PythonContext.PythonThreadState;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.interop.InteropLibrary;

/**
 * Emulates CPython's {@code PyThreadState} struct.
 * <p>
 * This wrapper does intentionally not implement {@link InteropLibrary#isPointer(Object)},
 * {@link InteropLibrary#asPointer(Object)}, and {@link InteropLibrary#toNative(Object)} because the
 * factory method {@link #getOrCreateNativeThreadState(PythonLanguage, PythonContext)} will already
 * return the appropriate pointer object that implements that.
 * </p>
 */
public abstract class PThreadState {
    private static final TruffleLogger LOGGER = CApiContext.getLogger(PThreadState.class);
    private static final int GRAALPY_DEALLOC_STACK_INITIAL_CAPACITY = 3;

    /** Same as _PY_NSMALLNEGINTS */
    public static final int PY_NSMALLNEGINTS = 5;

    /** Same as _PY_NSMALLPOSINTS */
    public static final int PY_NSMALLPOSINTS = 257;

    private PThreadState() {
    }

    public static long getOrCreateNativeThreadState(PythonLanguage language, PythonContext context) {
        return getOrCreateNativeThreadState(context.getThreadState(language));
    }

    public static long getOrCreateNativeThreadState(PythonThreadState threadState) {
        long pointer = threadState.getNativePointer();
        if (CompilerDirectives.injectBranchProbability(CompilerDirectives.SLOWPATH_PROBABILITY, pointer == UNINITIALIZED)) {
            pointer = PThreadState.allocateCLayout();
            threadState.setNativePointer(pointer);
        }
        return pointer;
    }

    @TruffleBoundary
    public static PDict getOrCreateThreadStateDict(PythonContext context, PythonThreadState threadState) {
        /*
         * C API initialization must be finished at that time. This implies that there is already a
         * native thread state.
         */
        assert context.getCApiState() == CApiState.INITIALIZED;

        long nativeThreadState = threadState.getNativePointer();
        assert nativeThreadState != NULLPTR;

        PDict threadStateDict = threadState.getDict();
        if (threadStateDict != null) {
            assert PythonToNativeNode.executeLongUncached(threadStateDict) == CStructAccess.readPtrField(nativeThreadState, CFields.PyThreadState__dict);
            return threadStateDict;
        }

        threadStateDict = PFactory.createDict(context.getLanguage());
        threadState.setDict(threadStateDict);
        assert CStructAccess.readPtrField(nativeThreadState, CFields.PyThreadState__dict) == NULLPTR;
        CStructAccess.writePtrField(nativeThreadState, CFields.PyThreadState__dict, PythonToNativeNode.executeLongUncached(threadStateDict));

        return threadStateDict;
    }

    /**
     * This method runs on a critical bootstrap path when creating the native thread state. It may
     * execute while the C API state is still INITIALIZING and before the current thread has
     * installed its native 'tstate_current' TLS slot. So, this code must stay very restricted: only
     * use bootstrap-safe allocation and raw struct writes here.
     *
     * In particular, do not introduce conversions such as PythonToNative(NewRef)Node or any other
     * code paths that may poll the native reference queue, materialize additional native wrappers,
     * or otherwise assume that the native thread state is already fully initialized.
     */
    @TruffleBoundary
    private static long allocateCLayout() {
        long ptr = CStructAccess.allocate(CStructs.PyThreadState);
        PythonContext pythonContext = PythonContext.get(null);
        /*
         * As in CPython, the thread state dict is initialized lazily. This is necessary to avoid
         * cycles in the bootstrapping process because creating the dict will need the GC state
         * which needs the thread state.
         */
        CStructAccess.writePtrField(ptr, CFields.PyThreadState__dict, NULLPTR);
        CApiContext cApiContext = pythonContext.getCApiContext();
        long smallInts = mallocPtrArray(PY_NSMALLNEGINTS + PY_NSMALLPOSINTS);
        long deallocatingState = CStructAccess.getFieldPtr(ptr, CFields.PyThreadState__graalpy_deallocating);
        long deallocating = mallocPtrArray(GRAALPY_DEALLOC_STACK_INITIAL_CAPACITY);
        CStructAccess.writePtrField(ptr, CFields.PyThreadState__small_ints, smallInts);
        CStructAccess.writePtrField(deallocatingState, CFields.GraalPyDeallocState__items, deallocating);
        for (int i = -PY_NSMALLNEGINTS; i < PY_NSMALLPOSINTS; i++) {
            writePtrArrayElement(smallInts, i + PY_NSMALLNEGINTS, CApiTransitions.HandlePointerConverter.intToPointer(i));
        }
        CStructAccess.writePtrField(ptr, CFields.PyThreadState__gc, cApiContext.getGCState());
        CStructAccess.writeIntField(deallocatingState, CFields.GraalPyDeallocState__len, 0);
        CStructAccess.writeIntField(deallocatingState, CFields.GraalPyDeallocState__capacity, GRAALPY_DEALLOC_STACK_INITIAL_CAPACITY);
        // py_recursion_limit = Py_DEFAULT_RECURSION_LIMIT (1000)
        // (cpython/Include/internal/pycore_runtime_init.h)
        int recLimit = pythonContext.getSysModuleState().getRecursionLimit();
        CStructAccess.writeIntField(ptr, CFields.PyThreadState__py_recursion_limit, recLimit);
        CStructAccess.writeIntField(ptr, CFields.PyThreadState__py_recursion_remaining, recLimit);
        // c_recursion_remaining = Py_C_RECURSION_LIMIT (1000) (cpython/Include/cpython/pystate.h)
        CStructAccess.writeIntField(ptr, CFields.PyThreadState__c_recursion_remaining, recLimit);
        LOGGER.fine(String.format("Allocated (PyThreadState *)0x%x", ptr));
        return ptr;
    }

    public static int growDeallocatingStack(long nativeThreadState, long newCapacity) {
        CompilerAsserts.neverPartOfCompilation();
        assert nativeThreadState != NULLPTR;
        long deallocatingState = CStructAccess.getFieldPtr(nativeThreadState, CFields.PyThreadState__graalpy_deallocating);
        long oldItems = CStructAccess.readPtrField(deallocatingState, CFields.GraalPyDeallocState__items);
        int oldCapacity = CStructAccess.readIntField(deallocatingState, CFields.GraalPyDeallocState__capacity);
        assert newCapacity > oldCapacity;
        assert newCapacity <= Integer.MAX_VALUE;

        long newItems;
        try {
            newItems = callocPtrArray(newCapacity);
        } catch (OutOfMemoryError e) {
            return -1;
        }

        if (oldItems != NULLPTR) {
            NativeMemory.memcpy(newItems, oldItems, oldCapacity * NativeMemory.POINTER_SIZE);
            NativeMemory.free(oldItems);
        }
        CStructAccess.writePtrField(deallocatingState, CFields.GraalPyDeallocState__items, newItems);
        CStructAccess.writeIntField(deallocatingState, CFields.GraalPyDeallocState__capacity, (int) newCapacity);
        return 0;
    }

    @TruffleBoundary
    public static void dispose(PythonThreadState threadState) {
        long nativeCompanion = threadState.getNativePointer();
        if (nativeCompanion == UNINITIALIZED || nativeCompanion == NATIVE_POINTER_FREED) {
            return;
        }

        assert !HandlePointerConverter.pointsToPyHandleSpace(nativeCompanion);
        threadState.clearNativePointer();

        long deallocatingState = CStructAccess.getFieldPtr(nativeCompanion, CFields.PyThreadState__graalpy_deallocating);
        long deallocatingItems = CStructAccess.readPtrField(deallocatingState, CFields.GraalPyDeallocState__items);
        if (deallocatingItems != NULLPTR) {
            NativeMemory.free(deallocatingItems);
        }

        // TODO(fa): decref PyThreadState__dict
        LOGGER.fine(String.format("Freeing (PyThreadState *)0x%x", nativeCompanion));
        NativeMemory.free(nativeCompanion);
    }
}
