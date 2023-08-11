/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorKey;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorNext;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageLen;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.set.PSet;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.GetNextNode;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.util.ArrayBuilder;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

@ImportStatic(PythonOptions.class)
@GenerateUncached
@GenerateInline(false) // footprint reduction 40 -> 24
public abstract class ExecutePositionalStarargsNode extends Node {
    public abstract Object[] executeWith(Frame frame, Object starargs);

    @Specialization
    static Object[] doObjectArray(Object[] starargs) {
        return starargs;
    }

    @Specialization
    static Object[] doTuple(PTuple starargs,
                    @Bind("this") Node inliningTarget,
                    @Shared("toArray") @Cached SequenceStorageNodes.ToArrayNode toArray) {
        return toArray.execute(inliningTarget, starargs.getSequenceStorage());
    }

    @Specialization
    static Object[] doList(PList starargs,
                    @Bind("this") Node inliningTarget,
                    @Shared("toArray") @Cached SequenceStorageNodes.ToArrayNode toArray) {
        return toArray.execute(inliningTarget, starargs.getSequenceStorage());
    }

    // Separate PDict and PSet specializations to avoid instanceof checks on non-leaf class
    // PHashingCollection

    @Specialization
    static Object[] doDict(PDict starargs,
                    @Bind("this") Node inliningTarget,
                    @Shared("doHashingStorage") @Cached ExecutePositionalStarargsDictStorageNode node) {
        return node.execute(inliningTarget, starargs.getDictStorage());
    }

    @Specialization
    static Object[] doSet(PSet starargs,
                    @Bind("this") Node inliningTarget,
                    @Shared("doHashingStorage") @Cached ExecutePositionalStarargsDictStorageNode node) {
        return node.execute(inliningTarget, starargs.getDictStorage());
    }

    @Specialization
    static Object[] doNone(PNone none,
                    @Shared("raise") @Cached PRaiseNode raise) {
        throw raise.raise(PythonErrorType.TypeError, ErrorMessages.ARG_AFTER_MUST_BE_ITERABLE, none);
    }

    @Specialization
    static Object[] starargs(VirtualFrame frame, Object object,
                    @Bind("this") Node inliningTarget,
                    @Shared("raise") @Cached PRaiseNode raise,
                    @Cached PyObjectGetIter getIter,
                    @Cached GetNextNode nextNode,
                    @Cached IsBuiltinObjectProfile errorProfile) {
        Object iterator = getIter.execute(frame, inliningTarget, object);
        if (iterator != PNone.NO_VALUE && iterator != PNone.NONE) {
            ArrayBuilder<Object> internalStorage = new ArrayBuilder<>();
            while (true) {
                try {
                    internalStorage.add(nextNode.execute(frame, iterator));
                } catch (PException e) {
                    e.expectStopIteration(inliningTarget, errorProfile);
                    return internalStorage.toArray(new Object[0]);
                }
            }
        }
        throw raise.raise(PythonErrorType.TypeError, ErrorMessages.ARG_AFTER_MUST_BE_ITERABLE, object);
    }

    @NeverDefault
    public static ExecutePositionalStarargsNode create() {
        return ExecutePositionalStarargsNodeGen.create();
    }

    public static ExecutePositionalStarargsNode getUncached() {
        return ExecutePositionalStarargsNodeGen.getUncached();
    }

    // Extracted into a node shared between PDict/PSet specializations to reduce the footprint
    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    abstract static class ExecutePositionalStarargsDictStorageNode extends Node {
        abstract Object[] execute(Node inliningTarget, HashingStorage storage);

        @Specialization
        static Object[] doIt(Node inliningTarget, HashingStorage storage,
                        @Cached HashingStorageLen lenNode,
                        @Cached HashingStorageGetIterator getIter,
                        @Cached HashingStorageIteratorNext iterNext,
                        @Cached HashingStorageIteratorKey iteratorKey) {
            int length = lenNode.execute(inliningTarget, storage);
            Object[] args = new Object[length];
            HashingStorageIterator it = getIter.execute(inliningTarget, storage);
            for (int i = 0; i < args.length; i++) {
                boolean hasNext = iterNext.execute(inliningTarget, storage, it);
                assert hasNext;
                args[i] = iteratorKey.execute(inliningTarget, storage, it);
            }
            return args;
        }
    }
}
