/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.BranchProfile;

@GenerateUncached
public abstract class UnpackSequenceNode extends PNodeWithContext {
    public abstract int execute(Frame virtualFrame, int stackTop, Frame localFrame, Object collection, int count);

    @Specialization(guards = {"cannotBeOverridden(sequence, getClassNode)", "!isPString(sequence)"}, limit = "1")
    @ExplodeLoop
    static int doUnpackSequence(int initialStackTop, Frame localFrame, PSequence sequence, int count,
                    @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                    @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                    @Cached SequenceStorageNodes.LenNode lenNode,
                    @Cached SequenceStorageNodes.GetItemScalarNode getItemNode,
                    @Cached BranchProfile errorProfile,
                    @Shared("raise") @Cached PRaiseNode raiseNode) {
        int resultStackTop = initialStackTop + count;
        int stackTop = resultStackTop;
        SequenceStorage storage = getSequenceStorageNode.execute(sequence);
        int len = lenNode.execute(storage);
        if (len == count) {
            for (int i = 0; i < count; i++) {
                localFrame.setObject(stackTop--, getItemNode.execute(storage, i));
            }
        } else {
            errorProfile.enter();
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
    static int doUnpackIterable(Frame virtualFrame, int initialStackTop, Frame localFrame, Object collection, int count,
                    @Cached PyObjectGetIter getIter,
                    @Cached GetNextNode getNextNode,
                    @Cached IsBuiltinClassProfile notIterableProfile,
                    @Cached IsBuiltinClassProfile stopIterationProfile1,
                    @Cached IsBuiltinClassProfile stopIterationProfile2,
                    @Shared("raise") @Cached PRaiseNode raiseNode) {
        int resultStackTop = initialStackTop + count;
        int stackTop = resultStackTop;
        Object iterator;
        try {
            iterator = getIter.execute(virtualFrame, collection);
        } catch (PException e) {
            e.expectTypeError(notIterableProfile);
            throw raiseNode.raise(TypeError, ErrorMessages.CANNOT_UNPACK_NON_ITERABLE, collection);
        }
        for (int i = 0; i < count; i++) {
            try {
                Object item = getNextNode.execute(virtualFrame, iterator);
                localFrame.setObject(stackTop--, item);
            } catch (PException e) {
                e.expectStopIteration(stopIterationProfile1);
                throw raiseNode.raise(ValueError, ErrorMessages.NOT_ENOUGH_VALUES_TO_UNPACK, count, i);
            }
        }
        try {
            getNextNode.execute(virtualFrame, iterator);
        } catch (PException e) {
            e.expectStopIteration(stopIterationProfile2);
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
