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

import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NEW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;
import static com.oracle.graal.python.nodes.StringLiterals.T_COMMA_SPACE;
import static com.oracle.graal.python.nodes.StringLiterals.T_EQ;
import static com.oracle.graal.python.nodes.StringLiterals.T_LPAREN;
import static com.oracle.graal.python.nodes.StringLiterals.T_RPAREN;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.NotImplementedError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.util.PythonUtils.EMPTY_TRUFFLESTRING_ARRAY;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.util.ArrayList;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.object.ObjectNodes;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeFlags;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.lib.PyDictGetItem;
import com.oracle.graal.python.lib.PyDictSetItem;
import com.oracle.graal.python.lib.PyObjectReprAsTruffleStringNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.HiddenAttr;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.builtins.ListNodes;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.runtime.sequence.storage.NativeSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.ObjectSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;

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
                PythonBuiltinClassType.PVersionInfo,
                PythonBuiltinClassType.PWindowsVersion,
                PythonBuiltinClassType.PFlags,
                PythonBuiltinClassType.PFloatInfo,
                PythonBuiltinClassType.PIntInfo,
                PythonBuiltinClassType.PHashInfo,
                PythonBuiltinClassType.PThreadInfo,
                PythonBuiltinClassType.PUnraisableHookArgs})
public final class StructSequenceBuiltins extends PythonBuiltins {

    public static final TpSlots SLOTS = StructSequenceBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return StructSequenceBuiltinsFactory.getFactories();
    }

    @GenerateInline
    @GenerateCached(false)
    abstract static class GetSizeNode extends Node {
        public abstract int execute(Node inliningTarget, Object type, TruffleString key);

        @Specialization
        static int doPBCT(Node inliningTarget, PythonBuiltinClassType type, TruffleString key,
                        @Shared @Cached("createForceType()") ReadAttributeFromObjectNode read) {
            return doGeneric(PythonContext.get(inliningTarget).lookupType(type), key, read);
        }

        @Fallback
        static int doGeneric(Object type, TruffleString key,
                        @Shared @Cached("createForceType()") ReadAttributeFromObjectNode read) {
            return (int) read.execute(type, key);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    abstract static class GetFieldNamesNode extends Node {
        public abstract TruffleString[] execute(Node inliningTarget, Object type);

        @Specialization
        static TruffleString[] doPBCT(Node inliningTarget, PythonBuiltinClassType type,
                        @Shared @Cached HiddenAttr.ReadNode readNode) {
            return doManaged(inliningTarget, PythonContext.get(inliningTarget).lookupType(type), readNode);
        }

        @Specialization
        static TruffleString[] doManaged(Node inliningTarget, PythonManagedClass type,
                        @Shared @Cached HiddenAttr.ReadNode readNode) {
            return (TruffleString[]) readNode.execute(inliningTarget, type, HiddenAttr.STRUCTSEQ_FIELD_NAMES, EMPTY_TRUFFLESTRING_ARRAY);
        }

        @Specialization
        @TruffleBoundary
        static TruffleString[] doNative(PythonAbstractNativeObject type) {
            CStructAccess.ReadPointerNode read = CStructAccess.ReadPointerNode.getUncached();
            Object membersPtr = read.readFromObj(type, CFields.PyTypeObject__tp_members);
            List<TruffleString> members = new ArrayList<>();
            InteropLibrary lib = InteropLibrary.getUncached();
            if (!PGuards.isNullOrZero(membersPtr, lib)) {
                for (int i = 0;; i++) {
                    Object memberNamePtr = read.readStructArrayElement(membersPtr, i, CFields.PyMemberDef__name);
                    if (PGuards.isNullOrZero(memberNamePtr, lib)) {
                        break;
                    }
                    TruffleString name = CExtNodes.FromCharPointerNode.executeUncached(memberNamePtr);
                    members.add(name);
                }
            }
            return members.toArray(new TruffleString[0]);
        }
    }

    @Builtin(name = J___NEW__, minNumOfPositionalArgs = 2, parameterNames = {"$cls", "sequence", "dict"})
    @GenerateNodeFactory
    public abstract static class NewNode extends PythonTernaryBuiltinNode {

        @Specialization
        static PTuple withDict(VirtualFrame frame, Object cls, Object sequence, Object dict,
                        @Bind("this") Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached TypeNodes.GetTypeFlagsNode getFlags,
                        @Cached GetSizeNode getSizeNode,
                        @Cached GetFieldNamesNode getFieldNamesNode,
                        @Cached ListNodes.FastConstructListNode fastConstructListNode,
                        @Cached SequenceStorageNodes.GetItemScalarNode getItem,
                        @Cached BuiltinClassProfiles.IsBuiltinObjectProfile notASequenceProfile,
                        @Cached InlinedBranchProfile wrongLenProfile,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape,
                        @Cached PyDictGetItem dictGetItem,
                        @Cached InlinedConditionProfile hasDictProfile,
                        @Cached PRaiseNode raiseNode) {
            // FIXME this should be checked by type.__call__
            if ((getFlags.execute(cls) & TypeFlags.DISALLOW_INSTANTIATION) != 0) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.CANNOT_CREATE_INSTANCES, StructSequence.getTpName(cls));
            }
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
            int minLen = getSizeNode.execute(inliningTarget, cls, StructSequence.T_N_SEQUENCE_FIELDS);
            int maxLen = getSizeNode.execute(inliningTarget, cls, StructSequence.T_N_FIELDS);
            int unnamedFields = getSizeNode.execute(inliningTarget, cls, StructSequence.T_N_UNNAMED_FIELDS);

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

    @Slot(value = SlotKind.tp_repr, isComplex = true)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {

        @Specialization
        static TruffleString repr(VirtualFrame frame, PTuple self,
                        @Bind("this") Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached GetFieldNamesNode getFieldNamesNode,
                        @Cached ObjectNodes.GetFullyQualifiedNameNode getQName,
                        @Cached SequenceStorageNodes.GetItemScalarNode getItemNode,
                        @Cached PyObjectReprAsTruffleStringNode reprNode,
                        @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode) {
            Object type = getClassNode.execute(inliningTarget, self);
            TruffleStringBuilder sb = TruffleStringBuilder.create(TS_ENCODING);
            appendStringNode.execute(sb, getQName.execute(frame, type));
            appendStringNode.execute(sb, T_LPAREN);
            SequenceStorage tupleStore = self.getSequenceStorage();
            int visibleSize = tupleStore.length();
            TruffleString[] fieldNames = getFieldNamesNode.execute(inliningTarget, type);
            if (visibleSize > 0) {
                appendStringNode.execute(sb, fieldNames[0]);
                appendStringNode.execute(sb, T_EQ);
                appendStringNode.execute(sb, reprNode.execute(frame, inliningTarget, getItemNode.execute(inliningTarget, tupleStore, 0)));
                for (int i = 1; i < visibleSize; i++) {
                    appendStringNode.execute(sb, T_COMMA_SPACE);
                    appendStringNode.execute(sb, fieldNames[i]);
                    appendStringNode.execute(sb, T_EQ);
                    appendStringNode.execute(sb, reprNode.execute(frame, inliningTarget, getItemNode.execute(inliningTarget, tupleStore, i)));
                }
            }
            appendStringNode.execute(sb, T_RPAREN);
            return toStringNode.execute(sb);
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReduceNode extends PythonUnaryBuiltinNode {

        @Specialization
        static PTuple reduce(PTuple self,
                        @Bind("this") Node inliningTarget,
                        @Cached GetClassNode getClass,
                        @Cached GetSizeNode getSizeNode,
                        @Cached GetFieldNamesNode getFieldNamesNode,
                        @Cached SequenceStorageNodes.GetItemScalarNode getSeqItem,
                        @Cached SequenceStorageNodes.GetItemSliceNode getSeqSlice,
                        @Cached PyDictSetItem dictSetItem,
                        @Cached PRaiseNode raiseNode,
                        @Bind PythonLanguage language) {
            SequenceStorage storage = self.getSequenceStorage();
            Object type = getClass.execute(inliningTarget, self);
            int nFields = getSizeNode.execute(inliningTarget, type, StructSequence.T_N_FIELDS);
            int nVisibleFields = storage.length();
            int nUnnamedFields = getSizeNode.execute(inliningTarget, type, StructSequence.T_N_UNNAMED_FIELDS);
            PTuple tuple = PFactory.createTuple(language, getSeqSlice.execute(storage, 0, nVisibleFields, 1, nVisibleFields));
            PDict dict = PFactory.createDict(language);
            if (nFields > nVisibleFields) {
                if (storage instanceof NativeSequenceStorage) {
                    // TODO Native storage conversion wouldn't preserve the items past length
                    throw raiseNode.raise(inliningTarget, NotImplementedError, ErrorMessages.UNSUPPORTED_ACCESS_OF_STRUCT_SEQUENCE_NATIVE_STORAGE);
                }
                assert storage.getCapacity() >= nFields;
                TruffleString[] fieldNames = getFieldNamesNode.execute(inliningTarget, type);
                for (int i = nVisibleFields; i < nFields; i++) {
                    TruffleString n = fieldNames[i - nUnnamedFields];
                    // We're reading past length, GetItemScalarNode doesn't do a bounds check
                    dictSetItem.execute(inliningTarget, dict, n, getSeqItem.execute(inliningTarget, storage, i));
                }
            }
            return PFactory.createTuple(language, new Object[]{type, PFactory.createTuple(language, new Object[]{tuple, dict})});
        }
    }
}
