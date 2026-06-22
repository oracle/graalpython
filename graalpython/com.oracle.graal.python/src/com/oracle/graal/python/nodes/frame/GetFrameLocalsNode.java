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
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageDelItem;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageSetItem;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.lib.PyDictGetItem;
import com.oracle.graal.python.nodes.bytecode_dsl.BytecodeDSLCodeUnit;
import com.oracle.graal.python.nodes.bytecode_dsl.BytecodeDSLFrameInfo;
import com.oracle.graal.python.runtime.CallerFlags;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.bytecode.BytecodeFrame;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
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

    @Specialization(guards = "!pyFrame.hasCustomLocals()")
    static Object doLoop(VirtualFrame frame, Node inliningTarget, PFrame pyFrame, boolean freshFrame,
                    @Cached InlinedBranchProfile create,
                    @Cached CopyDSLLocalsToDict copyLocalsToDict,
                    @Cached ReadFrameNode readFrameNode) {
        if (!freshFrame && pyFrame.needsRefresh(frame, CallerFlags.NEEDS_LOCALS)) {
            pyFrame = readFrameNode.refreshFrame(frame, pyFrame.getRef(), CallerFlags.NEEDS_LOCALS);
        }
        assert !pyFrame.outdatedCallerFlags(CallerFlags.NEEDS_LOCALS);
        // It doesn't have custom locals, so it has to be a builtin dict or null
        PDict localsDict = (PDict) pyFrame.getLocalsDict();
        if (localsDict == null) {
            create.enter(inliningTarget);
            localsDict = PFactory.createDict(PythonLanguage.get(inliningTarget));
            pyFrame.setLocalsDict(localsDict);
        }
        copyLocalsToDict.execute(pyFrame.getBytecodeFrame(), localsDict);
        return localsDict;
    }

    @Specialization(guards = "pyFrame.hasCustomLocals()")
    static Object doCustomLocals(PFrame pyFrame, @SuppressWarnings("unused") boolean freshFrame) {
        Object localsDict = pyFrame.getLocalsDict();
        assert localsDict != null;
        return localsDict;
    }

    @GenerateUncached
    @GenerateInline(false)       // footprint reduction 104 -> 86
    abstract static class CopyDSLLocalsToDict extends Node {

        abstract void execute(BytecodeFrame locals, PDict dict);

        @Specialization
        void doIt(BytecodeFrame locals, PDict dict,
                        @Bind Node inliningTarget,
                        @Cached InlinedIntValueProfile varCountProfile,
                        @Cached InlinedIntValueProfile regularVarCountProfile,
                        @Cached HashingStorageSetItem setItem,
                        @Cached HashingStorageDelItem delItem) {
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
                if (value == null) {
                    delItem.execute(inliningTarget, dict.getDictStorage(), name, dict);
                } else {
                    HashingStorage storage = setItem.execute(inliningTarget, dict.getDictStorage(), name, value);
                    dict.setDictStorage(storage);
                }
            }
        }
    }

    /**
     * Equivalent of CPython's {@code PyFrame_LocalsToFast}
     */
    public static void syncLocalsBackToFrame(BytecodeDSLCodeUnit co, BytecodeNode bytecodeNode, PFrame pyFrame, Frame localFrame) {
        CompilerAsserts.partialEvaluationConstant(co);
        if (!pyFrame.hasCustomLocals()) {
            PDict localsDict = (PDict) pyFrame.getLocalsDict();
            copyLocalsArray(localFrame, bytecodeNode, localsDict, co.varnames, 0, false);
            copyLocalsArray(localFrame, bytecodeNode, localsDict, co.cellvars, co.varnames.length, true);
            copyLocalsArray(localFrame, bytecodeNode, localsDict, co.freevars, co.varnames.length + co.cellvars.length, true);
        }
    }

    @ExplodeLoop
    private static void copyLocalsArray(Frame localFrame, BytecodeNode bytecodeNode, PDict localsDict, TruffleString[] namesArray, int offset, boolean deref) {
        CompilerAsserts.partialEvaluationConstant(namesArray);
        CompilerAsserts.partialEvaluationConstant(offset);
        for (int i = 0; i < namesArray.length; i++) {
            TruffleString varname = namesArray[i];
            Object value = getDictItemUncached(localsDict, varname);
            if (deref) {
                PCell cell = (PCell) bytecodeNode.getLocalValue(0, localFrame, offset + i);
                cell.setRef(value);
            } else {
                if (value == null) {
                    value = PNone.NONE;
                    // TODO warn: "assigning None to unbound local %s"
                }
                bytecodeNode.setLocalValue(0, localFrame, offset + i, value);
            }
        }
    }

    @TruffleBoundary
    private static Object getDictItemUncached(PDict localsDict, TruffleString varname) {
        return PyDictGetItem.executeUncached(localsDict, varname);
    }

    @NeverDefault
    public static GetFrameLocalsNode create() {
        return GetFrameLocalsNodeGen.create();
    }
}
