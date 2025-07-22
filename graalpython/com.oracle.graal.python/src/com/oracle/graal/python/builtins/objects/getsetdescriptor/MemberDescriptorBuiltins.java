/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.getsetdescriptor;

import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.DescriptorBuiltins.DescrDeleteNode;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.DescriptorBuiltins.DescrGetNode;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.DescriptorBuiltins.DescrSetNode;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.DescriptorBuiltins.DescriptorCheckNode;
import com.oracle.graal.python.builtins.objects.object.ObjectNodes.GetIdNode;
import com.oracle.graal.python.builtins.objects.str.StringUtils.SimpleTruffleStringFormatNode;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotDescrGet.DescrGetBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotDescrSet.DescrSetBuiltinNode;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Built-in functions that are only used for {@link PythonBuiltinClassType#MemberDescriptor}.
 */
@CoreFunctions(extendClasses = PythonBuiltinClassType.MemberDescriptor)
public final class MemberDescriptorBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = MemberDescriptorBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return MemberDescriptorBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.tp_repr, isComplex = true)
    @GenerateNodeFactory
    abstract static class MemberDescriptorReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        static TruffleString repr(GetSetDescriptor descr,
                        @Bind Node inliningTarget,
                        @Cached TypeNodes.GetNameNode getName,
                        @Cached SimpleTruffleStringFormatNode simpleTruffleStringFormatNode) {
            return simpleTruffleStringFormatNode.format("<member '%s' of '%s' objects>", descr.getName(), getName.execute(inliningTarget, descr.getType()));
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class MemberDescriptorReduceNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object doGeneric(GetSetDescriptor descr,
                        @Cached ReadAttributeFromObjectNode readAttributeFromObjectNode,
                        @Cached GetIdNode getIdNode,
                        @Bind PythonLanguage language) {
            Object getattr = readAttributeFromObjectNode.execute(getContext().getBuiltins(), BuiltinNames.T_GETATTR);
            Object id = getIdNode.execute(getattr);
            return PFactory.createTuple(language, new Object[]{id, PFactory.createTuple(language, new Object[]{descr.getType(), descr.getName()})});
        }
    }

    @Slot(value = SlotKind.tp_descr_get, isComplex = true)
    @GenerateUncached
    @GenerateNodeFactory
    abstract static class MemberGetNode extends DescrGetBuiltinNode {

        @Specialization(guards = "isNoValue(obj)")
        static Object doNone(@SuppressWarnings("unused") Object descr, @SuppressWarnings("unused") PNone obj, @SuppressWarnings("unused") Object type) {
            return descr;
        }

        @Specialization(guards = "!isNoValue(obj)")
        static Object doGetSetDescriptor(VirtualFrame frame, GetSetDescriptor descr, Object obj, @SuppressWarnings("unused") Object type,
                        @Bind Node inliningTarget,
                        @Cached DescriptorCheckNode descriptorCheckNode,
                        @Cached DescrGetNode getNode) {
            descriptorCheckNode.execute(inliningTarget, descr.getType(), descr.getName(), obj);
            // TODO(fa): READ_RESTRICTED (see descrobject.c: member_get)
            return getNode.execute(frame, descr, obj);
        }
    }

    @Slot(value = SlotKind.tp_descr_set, isComplex = true)
    @GenerateNodeFactory
    abstract static class DescrSet extends DescrSetBuiltinNode {
        @Specialization(guards = "!isNoValue(value)")
        static void doGetSetDescriptorSet(VirtualFrame frame, GetSetDescriptor descr, Object obj, Object value,
                        @Bind Node inliningTarget,
                        @Shared @Cached DescriptorCheckNode descriptorCheckNode,
                        @Cached DescrSetNode setNode) {
            descriptorCheckNode.execute(inliningTarget, descr.getType(), descr.getName(), obj);
            setNode.execute(frame, descr, obj, value);
        }

        @Specialization(guards = "isNoValue(value)")
        static void doGetSetDescriptorDel(VirtualFrame frame, GetSetDescriptor descr, Object obj, Object value,
                        @Bind Node inliningTarget,
                        @Shared @Cached DescriptorCheckNode descriptorCheckNode,
                        @Cached DescrDeleteNode deleteNode) {
            descriptorCheckNode.execute(inliningTarget, descr.getType(), descr.getName(), obj);
            deleteNode.execute(frame, descr, obj);
        }
    }
}
