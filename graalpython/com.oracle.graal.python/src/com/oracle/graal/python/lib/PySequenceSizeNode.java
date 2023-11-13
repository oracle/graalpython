/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.lib;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageLen;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.set.PSet;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.str.StringNodes;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetObjectSlotsNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotLen;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.PRaiseNode.Lazy;
import com.oracle.graal.python.nodes.object.GetClassNode.GetPythonObjectClassNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Equivalent of CPython's {@code PySequence_Size}, which has a slightly different semantics to
 * {@code PyObject_Size}.
 */
@GenerateUncached
@GenerateInline
@GenerateCached(false)
@ImportStatic(PGuards.class)
public abstract class PySequenceSizeNode extends Node {
    // Note: these fast-paths are duplicated in IteratorNodes$GetLength, because there is no simple
    // way to share them effectively without unnecessary indirections and overhead in the
    // interpreter

    public abstract int execute(Frame frame, Node inliningTarget, Object object);

    @Specialization
    static int doTruffleString(TruffleString str,
                    @Cached(inline = false) TruffleString.CodePointLengthNode codePointLengthNode) {
        return codePointLengthNode.execute(str, TS_ENCODING);
    }

    @Specialization(guards = "cannotBeOverriddenForImmutableType(object)")
    static int doList(PList object) {
        return object.getSequenceStorage().length();
    }

    @Specialization(guards = "cannotBeOverriddenForImmutableType(object)")
    static int doTuple(PTuple object) {
        return object.getSequenceStorage().length();
    }

    @Specialization(guards = "cannotBeOverriddenForImmutableType(object)")
    static int doDict(Node inliningTarget, PDict object,
                    @Shared("hashingStorageLen") @Cached HashingStorageLen lenNode) {
        return lenNode.execute(inliningTarget, object.getDictStorage());
    }

    @Specialization(guards = "cannotBeOverridden(object, inliningTarget, getClassNode)")
    static int doSet(Node inliningTarget, PSet object,
                    @Shared("getClass") @SuppressWarnings("unused") @Cached GetPythonObjectClassNode getClassNode,
                    @Shared("hashingStorageLen") @Cached HashingStorageLen lenNode) {
        return lenNode.execute(inliningTarget, object.getDictStorage());
    }

    @Specialization(guards = "cannotBeOverridden(object, inliningTarget, getClassNode)")
    @InliningCutoff
    static int doPString(Node inliningTarget, PString object,
                    @Shared("getClass") @SuppressWarnings("unused") @Cached GetPythonObjectClassNode getClassNode,
                    @Cached(inline = false) StringNodes.StringLenNode lenNode) {
        return lenNode.execute(object);
    }

    @Specialization(guards = "cannotBeOverridden(object, inliningTarget, getClassNode)")
    static int doPBytes(Node inliningTarget, PBytesLike object,
                    @Shared("getClass") @SuppressWarnings("unused") @Cached GetPythonObjectClassNode getClassNode) {
        return object.getSequenceStorage().length();
    }

    @Fallback
    static int doOthers(Frame frame, Node inliningTarget, Object object,
                    @Cached GetObjectSlotsNode getTpSlotsNode,
                    @Cached TpSlotLen.CallSlotLenNode callSlotLenNode,
                    @Cached InlinedBranchProfile hasNoSqLenBranch,
                    @Cached PRaiseNode.Lazy raiseNode) {
        TpSlots slots = getTpSlotsNode.execute(inliningTarget, object);
        if (slots.sq_length() != null) {
            return callSlotLenNode.execute((VirtualFrame) frame, inliningTarget, slots.sq_length(), object);
        }
        hasNoSqLenBranch.enter(inliningTarget);
        throw raiseError(object, inliningTarget, raiseNode, slots);
    }

    @InliningCutoff
    private static PException raiseError(Object object, Node inliningTarget, Lazy raiseNode, TpSlots slots) {
        TruffleString error = ErrorMessages.OBJ_HAS_NO_LEN;
        if (slots.mp_length() == null) {
            error = ErrorMessages.IS_NOT_A_SEQUENCE;
        }
        throw raiseNode.get(inliningTarget).raise(TypeError, error, object);
    }
}
