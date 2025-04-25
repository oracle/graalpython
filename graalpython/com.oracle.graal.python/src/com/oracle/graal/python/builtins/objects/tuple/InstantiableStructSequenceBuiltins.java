/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.tuple;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.util.Collections;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.lib.PyDictGetItem;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.builtins.ListNodes;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.runtime.sequence.storage.ObjectSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = {
                PythonBuiltinClassType.PStatResult,
                PythonBuiltinClassType.PStatvfsResult,
                PythonBuiltinClassType.PTerminalSize,
                PythonBuiltinClassType.PUnameResult,
                PythonBuiltinClassType.PStructTime,
                PythonBuiltinClassType.PProfilerEntry,
                PythonBuiltinClassType.PProfilerSubentry,
                PythonBuiltinClassType.PStructPasswd,
                PythonBuiltinClassType.PStructRusage,
                PythonBuiltinClassType.PFloatInfo,
                PythonBuiltinClassType.PIntInfo,
                PythonBuiltinClassType.PHashInfo,
                PythonBuiltinClassType.PThreadInfo,
                PythonBuiltinClassType.PUnraisableHookArgs})
public class InstantiableStructSequenceBuiltins extends PythonBuiltins {

    public static final TpSlots SLOTS = InstantiableStructSequenceBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return Collections.emptyList();
    }

    @Slot(value = Slot.SlotKind.tp_new, isComplex = true)
    @Slot.SlotSignature(minNumOfPositionalArgs = 2, parameterNames = {"$cls", "sequence", "dict"})
    @GenerateNodeFactory
    public abstract static class NewNode extends PythonTernaryBuiltinNode {

        @Specialization
        static PTuple withDict(VirtualFrame frame, Object cls, Object sequence, Object dict,
                        @Bind("this") Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached StructSequenceBuiltins.GetSizeNode getSizeNode,
                        @Cached StructSequenceBuiltins.GetFieldNamesNode getFieldNamesNode,
                        @Cached ListNodes.FastConstructListNode fastConstructListNode,
                        @Cached SequenceStorageNodes.GetItemScalarNode getItem,
                        @Cached BuiltinClassProfiles.IsBuiltinObjectProfile notASequenceProfile,
                        @Cached InlinedBranchProfile wrongLenProfile,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape,
                        @Cached PyDictGetItem dictGetItem,
                        @Cached InlinedConditionProfile hasDictProfile,
                        @Cached PRaiseNode raiseNode) {
            SequenceStorage seq;
            try {
                seq = fastConstructListNode.execute(frame, inliningTarget, sequence).getSequenceStorage();
            } catch (PException e) {
                e.expect(inliningTarget, TypeError, notASequenceProfile);
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.CONSTRUCTOR_REQUIRES_A_SEQUENCE);
            }

            boolean hasDict = hasDictProfile.profile(inliningTarget, dict instanceof PDict);
            if (!hasDict && dict != PNone.NO_VALUE) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.TAKES_A_DICT_AS_SECOND_ARG_IF_ANY, StructSequence.getTpName(cls));
            }

            int len = seq.length();
            int minLen = getSizeNode.execute(frame, inliningTarget, cls, StructSequence.T_N_SEQUENCE_FIELDS);
            int maxLen = getSizeNode.execute(frame, inliningTarget, cls, StructSequence.T_N_FIELDS);
            int unnamedFields = getSizeNode.execute(frame, inliningTarget, cls, StructSequence.T_N_UNNAMED_FIELDS);

            if (len < minLen || len > maxLen) {
                wrongLenProfile.enter(inliningTarget);
                if (minLen == maxLen) {
                    throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.TAKES_A_D_SEQUENCE, StructSequence.getTpName(cls), minLen, len);
                }
                if (len < minLen) {
                    throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.TAKES_AN_AT_LEAST_D_SEQUENCE, StructSequence.getTpName(cls), minLen, len);
                } else {    // len > maxLen
                    throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.TAKES_AN_AT_MOST_D_SEQUENCE, StructSequence.getTpName(cls), maxLen, len);
                }
            }

            Object[] dst = new Object[maxLen];
            for (int i = 0; i < seq.length(); i++) {
                dst[i] = getItem.execute(inliningTarget, seq, i);
            }
            TruffleString[] fieldNames = hasDict ? getFieldNamesNode.execute(inliningTarget, cls) : null;
            for (int i = seq.length(); i < dst.length; ++i) {
                if (hasDict) {
                    Object o = dictGetItem.execute(frame, inliningTarget, (PDict) dict, fieldNames[i - unnamedFields]);
                    dst[i] = o == null ? PNone.NONE : o;
                } else {
                    dst[i] = PNone.NONE;
                }
            }
            return PFactory.createTuple(language, cls, getInstanceShape.execute(cls), new ObjectSequenceStorage(dst, minLen));
        }
    }
}
