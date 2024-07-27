/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.bytecode;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;

import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.lib.GetNextNode;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;

@GenerateUncached
@GenerateInline(false) // used in BCI root node
public abstract class UnpackSequenceNode extends PNodeWithContext {
    public abstract int execute(Frame frame, int stackTop, Object collection, int count);

    @Specialization(guards = "isBuiltinSequence(sequence)")
    @ExplodeLoop
    static int doUnpackSequence(VirtualFrame frame, int initialStackTop, PSequence sequence, int count,
                    @Bind("this") Node inliningTarget,
                    @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                    @Cached SequenceStorageNodes.GetItemScalarNode getItemNode,
                    @Shared("raise") @Cached PRaiseNode raiseNode) {
        CompilerAsserts.partialEvaluationConstant(count);
        int resultStackTop = initialStackTop + count;
        int stackTop = resultStackTop;
        SequenceStorage storage = getSequenceStorageNode.execute(inliningTarget, sequence);
        int len = storage.length();
        if (len == count) {
            for (int i = 0; i < count; i++) {
                frame.setObject(stackTop--, getItemNode.execute(inliningTarget, storage, i));
            }
        } else {
            if (len < count) {
                throw raiseNode.raise(ValueError, ErrorMessages.NOT_ENOUGH_VALUES_TO_UNPACK, count, len);
            } else {
                throw raiseNode.raise(ValueError, ErrorMessages.TOO_MANY_VALUES_TO_UNPACK, count);
            }
        }
        return resultStackTop;
    }

    @Fallback
    @ExplodeLoop
    static int doUnpackIterable(Frame frame, int initialStackTop, Object collection, int count,
                    @Bind("this") Node inliningTarget,
                    @Cached PyObjectGetIter getIter,
                    @Cached GetNextNode getNextNode,
                    @Cached IsBuiltinObjectProfile notIterableProfile,
                    @Cached IsBuiltinObjectProfile stopIterationProfile1,
                    @Cached IsBuiltinObjectProfile stopIterationProfile2,
                    @Shared("raise") @Cached PRaiseNode raiseNode) {
        CompilerAsserts.partialEvaluationConstant(count);
        int resultStackTop = initialStackTop + count;
        int stackTop = resultStackTop;
        Object iterator;
        try {
            iterator = getIter.execute(frame, inliningTarget, collection);
        } catch (PException e) {
            e.expectTypeError(inliningTarget, notIterableProfile);
            throw raiseNode.raise(TypeError, ErrorMessages.CANNOT_UNPACK_NON_ITERABLE, collection);
        }
        for (int i = 0; i < count; i++) {
            try {
                Object item = getNextNode.execute(frame, iterator);
                frame.setObject(stackTop--, item);
            } catch (PException e) {
                e.expectStopIteration(inliningTarget, stopIterationProfile1);
                throw raiseNode.raise(ValueError, ErrorMessages.NOT_ENOUGH_VALUES_TO_UNPACK, count, i);
            }
        }
        try {
            getNextNode.execute(frame, iterator);
        } catch (PException e) {
            e.expectStopIteration(inliningTarget, stopIterationProfile2);
            return resultStackTop;
        }
        throw raiseNode.raise(ValueError, ErrorMessages.TOO_MANY_VALUES_TO_UNPACK, count);
    }

    public static UnpackSequenceNode create() {
        return UnpackSequenceNodeGen.create();
    }

    public static UnpackSequenceNode getUncached() {
        return UnpackSequenceNodeGen.getUncached();
    }
}
