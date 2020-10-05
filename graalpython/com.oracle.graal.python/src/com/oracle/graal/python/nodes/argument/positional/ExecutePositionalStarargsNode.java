/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.argument.positional;

import java.util.ArrayList;
import java.util.Iterator;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.set.PSet;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.control.GetNextNode.GetNextWithoutFrameNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;

@ImportStatic(PythonOptions.class)
public abstract class ExecutePositionalStarargsNode extends Node {
    public abstract Object[] executeWith(VirtualFrame frame, Object starargs);

    @Specialization
    static Object[] doObjectArray(Object[] starargs) {
        return starargs;
    }

    @Specialization
    static Object[] doTuple(PTuple starargs,
                    @Exclusive @Cached SequenceStorageNodes.ToArrayNode toArray) {
        return toArray.execute(starargs.getSequenceStorage());
    }

    @Specialization
    static Object[] doList(PList starargs,
                    @Exclusive @Cached SequenceStorageNodes.ToArrayNode toArray) {
        return toArray.execute(starargs.getSequenceStorage());
    }

    @Specialization(limit = "1")
    static Object[] doDict(PDict starargs,
                    @CachedLibrary("starargs.getDictStorage()") HashingStorageLibrary lib) {
        HashingStorage dictStorage = starargs.getDictStorage();
        int length = lib.length(dictStorage);
        Object[] args = new Object[length];
        Iterator<Object> iterator = lib.keys(dictStorage).iterator();
        for (int i = 0; i < args.length; i++) {
            assert iterator.hasNext();
            args[i] = iterator.next();
        }
        return args;
    }

    @Specialization(limit = "1")
    static Object[] doSet(PSet starargs,
                    @CachedLibrary("starargs.getDictStorage()") HashingStorageLibrary lib) {
        HashingStorage dictStorage = starargs.getDictStorage();
        int length = lib.length(dictStorage);
        Object[] args = new Object[length];
        Iterator<Object> iterator = lib.keys(dictStorage).iterator();
        for (int i = 0; i < args.length; i++) {
            assert iterator.hasNext();
            args[i] = iterator.next();
        }
        return args;
    }

    @Specialization
    static Object[] doNone(@SuppressWarnings("unused") PNone none) {
        return PythonUtils.EMPTY_OBJECT_ARRAY;
    }

    @Specialization(limit = "getCallSiteInlineCacheMaxDepth()")
    static Object[] starargs(VirtualFrame frame, Object object,
                    @Cached PRaiseNode raise,
                    @CachedLibrary("object") PythonObjectLibrary lib,
                    @Cached GetNextNode nextNode,
                    @Cached IsBuiltinClassProfile errorProfile) {
        Object iterator = lib.getIteratorWithFrame(object, frame);
        if (iterator != PNone.NO_VALUE && iterator != PNone.NONE) {
            ArrayList<Object> internalStorage = new ArrayList<>();
            while (true) {
                try {
                    addToList(internalStorage, nextNode.execute(frame, iterator));
                } catch (PException e) {
                    e.expectStopIteration(errorProfile);
                    return toArray(internalStorage);
                }
            }
        }
        throw raise.raise(PythonErrorType.TypeError, ErrorMessages.ARG_AFTER_MUST_BE_ITERABLE, object);
    }

    @TruffleBoundary(allowInlining = true)
    private static void addToList(ArrayList<Object> internalStorage, Object element) {
        internalStorage.add(element);
    }

    @TruffleBoundary(allowInlining = true)
    private static Object[] toArray(ArrayList<Object> internalStorage) {
        return internalStorage.toArray();
    }

    public static ExecutePositionalStarargsNode create() {
        return ExecutePositionalStarargsNodeGen.create();
    }

    @GenerateUncached
    @ImportStatic(PythonOptions.class)
    public abstract static class ExecutePositionalStarargsInteropNode extends PNodeWithContext {
        public abstract Object[] executeWithGlobalState(Object starargs);

        @Specialization
        static Object[] starargs(Object[] starargs) {
            return ExecutePositionalStarargsNode.doObjectArray(starargs);
        }

        @Specialization
        static Object[] doTuple(PTuple starargs,
                        @Exclusive @Cached SequenceStorageNodes.ToArrayNode toArray) {
            return ExecutePositionalStarargsNode.doTuple(starargs, toArray);
        }

        @Specialization
        static Object[] doList(PList starargs,
                        @Exclusive @Cached SequenceStorageNodes.ToArrayNode toArray) {
            return ExecutePositionalStarargsNode.doList(starargs, toArray);
        }

        @Specialization
        static Object[] doDict(PDict starargs,
                        @CachedLibrary(limit = "1") HashingStorageLibrary lib) {
            return ExecutePositionalStarargsNode.doDict(starargs, lib);
        }

        @Specialization
        static Object[] doSet(PSet starargs,
                        @CachedLibrary(limit = "1") HashingStorageLibrary lib) {
            return ExecutePositionalStarargsNode.doSet(starargs, lib);
        }

        @Specialization
        static Object[] starargs(@SuppressWarnings("unused") PNone none) {
            return ExecutePositionalStarargsNode.doNone(none);
        }

        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()")
        static Object[] starargs(Object object,
                        @Cached PRaiseNode raise,
                        @CachedLibrary("object") PythonObjectLibrary lib,
                        @Cached GetNextWithoutFrameNode nextNode,
                        @Cached IsBuiltinClassProfile errorProfile) {
            Object iterator = lib.getIterator(object);
            if (iterator != PNone.NO_VALUE && iterator != PNone.NONE) {
                ArrayList<Object> internalStorage = new ArrayList<>();
                while (true) {
                    try {
                        addToList(internalStorage, nextNode.executeWithGlobalState(iterator));
                    } catch (PException e) {
                        e.expectStopIteration(errorProfile);
                        return toArray(internalStorage);
                    }
                }
            }
            throw raise.raise(PythonErrorType.TypeError, ErrorMessages.ARG_AFTER_MUST_BE_ITERABLE, object);
        }
    }
}
