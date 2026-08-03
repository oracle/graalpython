/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.frame;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageAddAllToOther;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageSetItem;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.nodes.bytecode_dsl.BytecodeDSLFrameInfo;
import com.oracle.graal.python.runtime.CallerFlags;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.bytecode.BytecodeFrame;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedIntValueProfile;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Rough equivalent of CPython's {@code PyFrame_FastToLocalsWithError}. CPython copies the fast
 * locals to a dict. We first copy Truffle frame locals to PFrame locals in frame materialization.
 * Then, when requested, this node copies PFrame locals to a dict.
 */
@GenerateUncached
@GenerateInline(inlineByDefault = true)
@GenerateCached
public abstract class GetFrameLocalsNode extends Node {
    /**
     * @param freshFrame whether the frame was just materialized with locals sync and we know for
     *            sure it won't need sync. If unsure, pass false
     */
    public abstract Object execute(Frame frame, Node inliningTarget, PFrame pyFrame, boolean freshFrame);

    public final Object executeCached(VirtualFrame frame, PFrame pyFrame, boolean freshFrame) {
        return execute(frame, this, pyFrame, freshFrame);
    }

    public static Object executeUncached(PFrame pyFrame, boolean freshFrame) {
        return GetFrameLocalsNodeGen.getUncached().execute(null, null, pyFrame, freshFrame);
    }

    @Specialization(guards = "pyFrame.getCustomLocals() == null")
    static Object doLoop(VirtualFrame frame, Node inliningTarget, PFrame pyFrame, boolean freshFrame,
                    @Cached CopyDSLLocalsToDict copyLocalsToDict,
                    @Cached ReadFrameNode readFrameNode,
                    @Cached HashingStorageAddAllToOther addAllToOther) {
        if (!freshFrame && pyFrame.needsRefresh(frame, CallerFlags.NEEDS_LOCALS)) {
            pyFrame = readFrameNode.refreshFrame(frame, pyFrame.getRef(), CallerFlags.NEEDS_LOCALS);
        }
        assert !pyFrame.outdatedCallerFlags(CallerFlags.NEEDS_LOCALS);
        PDict locals = copyLocalsToDict.execute(pyFrame.getBytecodeFrame());
        PDict extraLocals = pyFrame.getExtraLocals();
        if (extraLocals != null) {
            addAllToOther.execute(frame, inliningTarget, extraLocals.getDictStorage(), locals);
        }
        return locals;
    }

    @Specialization(guards = "pyFrame.getCustomLocals() != null")
    static Object doCustomLocals(PFrame pyFrame, @SuppressWarnings("unused") boolean freshFrame) {
        return pyFrame.getCustomLocals();
    }

    @GenerateUncached
    @GenerateInline(false)       // footprint reduction 104 -> 86
    abstract static class CopyDSLLocalsToDict extends Node {

        abstract PDict execute(BytecodeFrame locals);

        @Specialization
        PDict doIt(BytecodeFrame locals,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached InlinedIntValueProfile varCountProfile,
                        @Cached InlinedIntValueProfile regularVarCountProfile,
                        @Cached HashingStorageSetItem setItem) {
            HashingStorage storage = new DynamicObjectStorage(language);
            BytecodeDSLFrameInfo info = (BytecodeDSLFrameInfo) locals.getFrameDescriptorInfo();
            int regularVarCount = regularVarCountProfile.profile(inliningTarget, info.getRegularVariableCount());
            int varCount = varCountProfile.profile(inliningTarget, info.getVariableCount());
            for (int i = 0; i < varCount; i++) {
                Object localValue = locals.getLocalValue(i);
                TruffleString name = info.getVariableName(i);
                Object value = localValue;
                if (i >= regularVarCount && value != null) {
                    value = ((PCell) value).getRef();
                }
                if (value != null) {
                    storage = setItem.execute(inliningTarget, storage, name, value);
                }
            }
            return PFactory.createDict(language, storage);
        }
    }

    @NeverDefault
    public static GetFrameLocalsNode create() {
        return GetFrameLocalsNodeGen.create();
    }
}
