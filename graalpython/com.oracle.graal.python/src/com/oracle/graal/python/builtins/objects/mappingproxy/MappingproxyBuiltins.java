/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.builtins.objects.mappingproxy;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J_COPY;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J_GET;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J_ITEMS;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J_KEYS;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J_VALUES;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CLASS_GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REVERSED__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T_COPY;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T_GET;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T_ITEMS;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T_KEYS;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T_VALUES;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___REVERSED__;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.str.StringUtils.SimpleTruffleStringFormatNode;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryFunc.MpSubscriptBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryOp.BinaryOpBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotLen.LenBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotRichCompare;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSqContains.SqContainsBuiltinNode;
import com.oracle.graal.python.lib.PyMappingCheckNode;
import com.oracle.graal.python.lib.PyNumberOrNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectReprAsTruffleStringNode;
import com.oracle.graal.python.lib.PyObjectRichCompare;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.lib.PyObjectStrAsObjectNode;
import com.oracle.graal.python.lib.PySequenceContainsNode;
import com.oracle.graal.python.lib.RichCmpOp;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PMappingproxy)
public final class MappingproxyBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = MappingproxyBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<com.oracle.truffle.api.dsl.NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return MappingproxyBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = "mappingproxy", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class MappingproxyNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "!isNoValue(obj)")
        static Object doMapping(@SuppressWarnings("unused") Object cls, Object obj,
                        @Bind Node inliningTarget,
                        @Cached PyMappingCheckNode mappingCheckNode,
                        @Bind PythonLanguage language,
                        @Cached PRaiseNode raiseNode) {
            // descrobject.c mappingproxy_check_mapping()
            if (!(obj instanceof PList || obj instanceof PTuple) && mappingCheckNode.execute(inliningTarget, obj)) {
                return PFactory.createMappingproxy(language, obj);
            }
            throw raiseNode.raise(inliningTarget, PythonErrorType.TypeError, ErrorMessages.S_ARG_MUST_BE_S_NOT_P, "mappingproxy()", "mapping", obj);
        }

        @Specialization(guards = "isNoValue(none)")
        @SuppressWarnings("unused")
        static Object doMissing(Object klass, PNone none,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, PythonErrorType.TypeError, ErrorMessages.MISSING_D_REQUIRED_S_ARGUMENT_S_POS, "mappingproxy()", "mapping", 1);
        }
    }

    @Slot(value = SlotKind.tp_init, isComplex = true)
    @SlotSignature(name = "mappingproxy", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonBinaryBuiltinNode {

        @Specialization
        @SuppressWarnings("unused")
        Object doPMappingproxy(PMappingproxy self, Object mapping) {
            // nothing to do
            return PNone.NONE;
        }
    }

    @Slot(value = SlotKind.tp_iter, isComplex = true)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object iter(VirtualFrame frame, @SuppressWarnings("unused") PMappingproxy self,
                        @Bind Node inliningTarget,
                        @Cached PyObjectGetIter getIter) {
            return getIter.execute(frame, inliningTarget, self.getMapping());
        }
    }

    // keys()
    @Builtin(name = J_KEYS, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class KeysNode extends PythonUnaryBuiltinNode {
        @Specialization
        public Object items(VirtualFrame frame, PMappingproxy self,
                        @Bind Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            return callMethod.execute(frame, inliningTarget, self.getMapping(), T_KEYS);
        }
    }

    // items()
    @Builtin(name = J_ITEMS, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ItemsNode extends PythonUnaryBuiltinNode {
        @Specialization
        public Object items(VirtualFrame frame, PMappingproxy self,
                        @Bind Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            return callMethod.execute(frame, inliningTarget, self.getMapping(), T_ITEMS);
        }
    }

    // values()
    @Builtin(name = J_VALUES, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ValuesNode extends PythonUnaryBuiltinNode {
        @Specialization
        public Object values(VirtualFrame frame, PMappingproxy self,
                        @Bind Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            return callMethod.execute(frame, inliningTarget, self.getMapping(), T_VALUES);
        }
    }

    // get(key[, default])
    @Builtin(name = J_GET, minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class GetNode extends PythonBuiltinNode {
        @Specialization(guards = "isNoValue(defaultValue)")
        public Object get(VirtualFrame frame, PMappingproxy self, Object key, @SuppressWarnings("unused") PNone defaultValue,
                        @Bind Node inliningTarget,
                        @Shared("callMethod") @Cached PyObjectCallMethodObjArgs callMethod) {
            return callMethod.execute(frame, inliningTarget, self.getMapping(), T_GET, key);
        }

        @Specialization(guards = "!isNoValue(defaultValue)")
        public Object get(VirtualFrame frame, PMappingproxy self, Object key, Object defaultValue,
                        @Bind Node inliningTarget,
                        @Shared("callMethod") @Cached PyObjectCallMethodObjArgs callMethod) {
            return callMethod.execute(frame, inliningTarget, self.getMapping(), T_GET, key, defaultValue);
        }
    }

    @Slot(value = SlotKind.mp_subscript, isComplex = true)
    @GenerateNodeFactory
    public abstract static class GetItemNode extends MpSubscriptBuiltinNode {
        @Specialization
        Object getItem(VirtualFrame frame, PMappingproxy self, Object key,
                        @Bind Node inliningTarget,
                        @Cached PyObjectGetItem getItemNode) {
            return getItemNode.execute(frame, inliningTarget, self.getMapping(), key);
        }
    }

    @Slot(value = SlotKind.sq_contains, isComplex = true)
    @GenerateNodeFactory
    public abstract static class ContainsNode extends SqContainsBuiltinNode {
        @Specialization
        boolean run(VirtualFrame frame, PMappingproxy self, Object key,
                        @Bind Node inliningTarget,
                        @Cached PySequenceContainsNode containsNode) {
            return containsNode.execute(frame, inliningTarget, self.getMapping(), key);
        }
    }

    @Slot(value = SlotKind.mp_length, isComplex = true)
    @GenerateUncached
    @GenerateNodeFactory
    public abstract static class LenNode extends LenBuiltinNode {
        @Specialization
        public int len(VirtualFrame frame, PMappingproxy self,
                        @Bind Node inliningTarget,
                        @Cached PyObjectSizeNode sizeNode) {
            return sizeNode.execute(frame, inliningTarget, self.getMapping());
        }
    }

    // copy()
    @Builtin(name = J_COPY, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class CopyNode extends PythonUnaryBuiltinNode {
        @Specialization
        public Object copy(VirtualFrame frame, PMappingproxy self,
                        @Bind Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            return callMethod.execute(frame, inliningTarget, self.getMapping(), T_COPY);
        }
    }

    @Slot(value = SlotKind.tp_richcompare, isComplex = true)
    @GenerateNodeFactory
    public abstract static class MappingproxyRichCmpNode extends TpSlotRichCompare.RichCmpBuiltinNode {
        @Specialization
        Object doIt(VirtualFrame frame, PMappingproxy self, Object other, RichCmpOp op,
                        @Bind Node inliningTarget,
                        @Cached PyObjectRichCompare cmpNode) {
            return cmpNode.execute(frame, inliningTarget, self.getMapping(), other, op);
        }
    }

    @Slot(value = SlotKind.tp_str, isComplex = true)
    @GenerateNodeFactory
    abstract static class StrNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object str(VirtualFrame frame, PMappingproxy self,
                        @Bind Node inliningTarget,
                        @Cached PyObjectStrAsObjectNode strNode) {
            return strNode.execute(frame, inliningTarget, self.getMapping());
        }
    }

    @Slot(value = SlotKind.tp_repr, isComplex = true)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        static TruffleString repr(VirtualFrame frame, PMappingproxy self,
                        @Bind Node inliningTarget,
                        @Cached PyObjectReprAsTruffleStringNode reprNode,
                        @Cached SimpleTruffleStringFormatNode simpleTruffleStringFormatNode) {
            TruffleString mappingRepr = reprNode.execute(frame, inliningTarget, self.getMapping());
            return simpleTruffleStringFormatNode.format("mappingproxy(%s)", mappingRepr);
        }
    }

    @Builtin(name = J___CLASS_GETITEM__, minNumOfPositionalArgs = 2, isClassmethod = true)
    @GenerateNodeFactory
    public abstract static class ClassGetItemNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object classGetItem(Object cls, Object key,
                        @Bind PythonLanguage language) {
            return PFactory.createGenericAlias(language, cls, key);
        }
    }

    @Slot(value = SlotKind.nb_or, isComplex = true)
    @GenerateNodeFactory
    abstract static class OrNode extends BinaryOpBuiltinNode {

        @Specialization
        static Object or(VirtualFrame frame, Object self, Object other,
                        @Cached PyNumberOrNode orNode) {
            if (self instanceof PMappingproxy) {
                self = ((PMappingproxy) self).getMapping();
            }
            if (other instanceof PMappingproxy) {
                other = ((PMappingproxy) other).getMapping();
            }
            return orNode.execute(frame, self, other);
        }
    }

    @Slot(value = SlotKind.nb_inplace_or, isComplex = true)
    @GenerateNodeFactory
    abstract static class IOrNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object or(Object self, @SuppressWarnings("unused") Object other,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.IOR_IS_NOT_SUPPORTED_BY_P_USE_INSTEAD, self);
        }
    }

    @Builtin(name = J___REVERSED__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReversedNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object reversed(VirtualFrame frame, PMappingproxy self,
                        @Bind Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            return callMethod.execute(frame, inliningTarget, self.getMapping(), T___REVERSED__);
        }
    }
}
