/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.types;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___ARGS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___PARAMETERS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___MODULE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INSTANCECHECK__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SUBCLASSCHECK__;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltins;
import com.oracle.graal.python.builtins.objects.set.PFrozenSet;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsSameTypeNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryFunc.MpSubscriptBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryOp.BinaryOpBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotGetAttr.GetAttrBuiltinNode;
import com.oracle.graal.python.lib.PyNumberOrNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotHashFun.HashBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotRichCompare.RichCmpBuiltinNode;
import com.oracle.graal.python.lib.RichCmpOp;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectHashNode;
import com.oracle.graal.python.lib.PyObjectRichCompareBool;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.StringLiterals;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PUnionType)
public final class UnionTypeBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = UnionTypeBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return UnionTypeBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___ARGS__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class ArgsNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object args(PUnionType self) {
            return self.getArgs();
        }
    }

    @Builtin(name = J___PARAMETERS__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class ParametersNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object parameters(PUnionType self,
                        @Bind PythonLanguage language) {
            if (self.getParameters() == null) {
                self.setParameters(PFactory.createTuple(language, GenericTypeNodes.makeParameters(self.getArgs())));
            }
            return self.getParameters();
        }
    }

    @Slot(value = SlotKind.nb_or, isComplex = true)
    @GenerateNodeFactory
    abstract static class OrNode extends BinaryOpBuiltinNode {
        @Specialization
        Object union(Object self, Object other,
                        @Cached GenericTypeNodes.UnionTypeOrNode orNode) {
            return orNode.execute(self, other);
        }
    }

    @Slot(value = SlotKind.tp_repr, isComplex = true)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {
        private static final TruffleString SEPARATOR = tsLiteral(" | ");

        @Specialization
        @TruffleBoundary
        Object repr(PUnionType self) {
            TruffleStringBuilder sb = TruffleStringBuilder.create(TS_ENCODING);
            SequenceStorage argsStorage = self.getArgs().getSequenceStorage();
            for (int i = 0; i < argsStorage.length(); i++) {
                if (i > 0) {
                    sb.appendStringUncached(SEPARATOR);
                }
                reprItem(sb, SequenceStorageNodes.GetItemScalarNode.executeUncached(argsStorage, i));
            }
            return sb.toStringUncached();
        }

        // Equivalent of union_repr_item in CPython
        private static void reprItem(TruffleStringBuilder sb, Object obj) {
            if (IsSameTypeNode.executeUncached(obj, PythonBuiltinClassType.PNone)) {
                sb.appendStringUncached(StringLiterals.T_NONE);
                return;
            }
            GenericTypeNodes.reprItem(sb, obj);
        }
    }

    @Slot(value = SlotKind.tp_hash, isComplex = true)
    @GenerateNodeFactory
    abstract static class HashNode extends HashBuiltinNode {
        @Specialization
        static long hash(VirtualFrame frame, PUnionType self,
                        @Bind Node inliningTarget,
                        @Cached PyObjectHashNode hashNode,
                        @Cached HashingCollectionNodes.GetClonedHashingStorageNode getHashingStorageNode,
                        @Bind PythonLanguage language) {
            PFrozenSet argSet = PFactory.createFrozenSet(language, getHashingStorageNode.getForSets(frame, inliningTarget, self.getArgs()));
            return hashNode.execute(frame, inliningTarget, argSet);
        }
    }

    @Slot(value = SlotKind.tp_getattro, isComplex = true)
    @GenerateNodeFactory
    abstract static class GetAttributeNode extends GetAttrBuiltinNode {

        @Specialization
        Object getattribute(VirtualFrame frame, PUnionType self, Object nameObj,
                        @Bind Node inliningTarget,
                        @Cached CastToTruffleStringNode cast,
                        @Cached TruffleString.EqualNode equalNode,
                        @Cached GetClassNode getClassNode,
                        @Cached PyObjectGetAttr getAttr,
                        @Cached ObjectBuiltins.GetAttributeNode genericGetAttribute) {
            TruffleString name;
            try {
                name = cast.execute(inliningTarget, nameObj);
            } catch (CannotCastException e) {
                return genericGetAttribute.execute(frame, self, nameObj);
            }
            if (equalNode.execute(name, T___MODULE__, TS_ENCODING)) {
                return getAttr.execute(frame, inliningTarget, getClassNode.execute(inliningTarget, self), name);
            }
            return genericGetAttribute.execute(frame, self, nameObj);
        }
    }

    @Builtin(name = J___INSTANCECHECK__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class InstanceCheckNode extends PythonBinaryBuiltinNode {
        @Specialization
        static boolean check(VirtualFrame frame, PUnionType self, Object other,
                        @Bind Node inliningTarget,
                        @Cached SequenceStorageNodes.GetItemScalarNode getItem,
                        @Cached BuiltinFunctions.IsInstanceNode isInstanceNode,
                        @Cached PRaiseNode raiseNode) {
            SequenceStorage argsStorage = self.getArgs().getSequenceStorage();
            boolean result = false;
            for (int i = 0; i < argsStorage.length(); i++) {
                Object arg = getItem.execute(inliningTarget, argsStorage, i);
                if (arg instanceof PGenericAlias) {
                    throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.ISINSTANCE_ARG_2_CANNOT_CONTAIN_A_PARAMETERIZED_GENERIC);
                }
                if (!result) {
                    result = isInstanceNode.executeWith(frame, other, arg);
                    // Cannot break here, the check for GenericAlias needs to check all args
                }
            }
            return result;
        }
    }

    @Builtin(name = J___SUBCLASSCHECK__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class SubclassCheckNode extends PythonBinaryBuiltinNode {
        @Specialization
        static boolean check(VirtualFrame frame, PUnionType self, Object other,
                        @Bind Node inliningTarget,
                        @Cached TypeNodes.IsTypeNode isTypeNode,
                        @Cached SequenceStorageNodes.GetItemScalarNode getItem,
                        @Cached BuiltinFunctions.IsSubClassNode isSubClassNode,
                        @Cached PRaiseNode raiseNode) {
            if (!isTypeNode.execute(inliningTarget, other)) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.ISSUBCLASS_ARG_1_MUST_BE_A_CLASS);
            }
            SequenceStorage argsStorage = self.getArgs().getSequenceStorage();
            boolean result = false;
            for (int i = 0; i < argsStorage.length(); i++) {
                Object arg = getItem.execute(inliningTarget, argsStorage, i);
                if (arg instanceof PGenericAlias) {
                    throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.ISSUBCLASS_ARG_2_CANNOT_CONTAIN_A_PARAMETERIZED_GENERIC);
                }
                if (!result) {
                    result = isSubClassNode.executeWith(frame, other, arg);
                    // Cannot break here, the check for GenericAlias needs to check all args
                }
            }
            return result;
        }
    }

    @Slot(value = SlotKind.tp_richcompare, isComplex = true)
    @GenerateNodeFactory
    abstract static class EqNode extends RichCmpBuiltinNode {
        @Specialization(guards = "op.isEqOrNe()")
        static boolean eq(VirtualFrame frame, PUnionType self, PUnionType other, RichCmpOp op,
                        @Bind Node inliningTarget,
                        @Cached HashingCollectionNodes.GetClonedHashingStorageNode getHashingStorageNode,
                        @Cached PyObjectRichCompareBool eqNode,
                        @Bind PythonLanguage language) {
            PFrozenSet argSet1 = PFactory.createFrozenSet(language, getHashingStorageNode.getForSets(frame, inliningTarget, self.getArgs()));
            PFrozenSet argSet2 = PFactory.createFrozenSet(language, getHashingStorageNode.getForSets(frame, inliningTarget, other.getArgs()));
            return eqNode.execute(frame, inliningTarget, argSet1, argSet2, op);
        }

        @Fallback
        @SuppressWarnings("unused")
        Object eq(Object self, Object other, RichCmpOp op) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Slot(value = SlotKind.mp_subscript, isComplex = true)
    @GenerateNodeFactory
    abstract static class GetItemNode extends MpSubscriptBuiltinNode {

        @Specialization
        Object getitem(VirtualFrame frame, PUnionType self, Object item,
                        @Bind Node inliningTarget,
                        @Cached InlinedBranchProfile createProfile,
                        @Cached PyNumberOrNode orNode) {
            if (self.getParameters() == null) {
                createProfile.enter(inliningTarget);
                self.setParameters(PFactory.createTuple(PythonLanguage.get(inliningTarget), GenericTypeNodes.makeParameters(self.getArgs())));
            }
            Object[] newargs = GenericTypeNodes.subsParameters(this, self, self.getArgs(), self.getParameters(), item);
            Object result = newargs[0];
            for (int i = 1; i < newargs.length; i++) {
                result = orNode.execute(frame, result, newargs[i]);
            }
            return result;
        }
    }
}
