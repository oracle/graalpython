/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___OBJCLASS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REPR__;

import java.util.List;

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
import com.oracle.graal.python.builtins.objects.str.StringUtils.SimpleTruffleStringFormatNode;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotDescrSet.DescrSetBuiltinNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Built-in functions that are only used for {@link PythonBuiltinClassType#GetSetDescriptor}.
 */
@CoreFunctions(extendClasses = PythonBuiltinClassType.GetSetDescriptor)
public final class GetSetDescriptorTypeBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = GetSetDescriptorTypeBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return GetSetDescriptorTypeBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___OBJCLASS__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class ObjclassNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doGetSetDescriptor(GetSetDescriptor self) {
            return self.getType();
        }

        @Specialization
        static Object doHiddenAttrDescriptor(HiddenAttrDescriptor self) {
            return self.getType();
        }
    }

    @Builtin(name = J___REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class GetSetReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        TruffleString repr(GetSetDescriptor descr,
                        @Bind("this") Node inliningTarget,
                        @Shared("gerName") @Cached GetNameNode getName,
                        @Shared("format") @Cached SimpleTruffleStringFormatNode simpleTruffleStringFormatNode) {
            return simpleTruffleStringFormatNode.format("<attribute '%s' of '%s' objects>", descr.getName(), getName.execute(inliningTarget, descr.getType()));
        }

        @Specialization
        TruffleString repr(HiddenAttrDescriptor descr,
                        @Bind("this") Node inliningTarget,
                        @Shared("gerName") @Cached GetNameNode getName,
                        @Shared("format") @Cached SimpleTruffleStringFormatNode simpleTruffleStringFormatNode) {
            return simpleTruffleStringFormatNode.format("<attribute '%s' of '%s' objects>", descr.getAttr().getName(), getName.execute(inliningTarget, descr.getType()));
        }
    }

    @Slot(SlotKind.tp_descr_get)
    @GenerateUncached
    @GenerateNodeFactory
    abstract static class GetSetGetNode extends PythonTernaryBuiltinNode {
        @Specialization(guards = {"isNone(obj)", "!isPNone(type)"})
        static Object doNone(@SuppressWarnings("unused") Object descr, @SuppressWarnings("unused") PNone obj, @SuppressWarnings("unused") Object type) {
            return descr;
        }

        @Specialization(guards = "isNone(obj)")
        static Object doNoneNone(@SuppressWarnings("unused") Object descr, @SuppressWarnings("unused") PNone obj, @SuppressWarnings("unused") PNone type,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, ErrorMessages.GET_NONE_NONE_IS_INVALID);
        }

        // https://github.com/python/cpython/blob/e8b19656396381407ad91473af5da8b0d4346e88/Objects/descrobject.c#L149
        @Specialization(guards = "!isNone(obj)")
        static Object doGetSetDescriptor(VirtualFrame frame, GetSetDescriptor descr, Object obj, @SuppressWarnings("unused") Object type,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached DescriptorCheckNode descriptorCheckNode,
                        @Shared @Cached DescrGetNode getNode) {
            descriptorCheckNode.execute(inliningTarget, descr.getType(), descr.getName(), obj);
            return getNode.execute(frame, descr, obj);
        }

        @Specialization(guards = "!isNone(obj)")
        static Object doHiddenAttrDescriptor(VirtualFrame frame, HiddenAttrDescriptor descr, Object obj, @SuppressWarnings("unused") Object type,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached DescriptorCheckNode descriptorCheckNode,
                        @Shared @Cached DescrGetNode getNode) {
            descriptorCheckNode.execute(inliningTarget, descr.getType(), descr.getAttr(), obj);
            return getNode.execute(frame, descr, obj);
        }
    }

    @Slot(value = SlotKind.tp_descr_set, isComplex = true)
    @GenerateNodeFactory
    abstract static class DescrSet extends DescrSetBuiltinNode {
        @Specialization(guards = "!isNoValue(value)")
        static void doDescriptorSet(VirtualFrame frame, Object descr, Object obj, Object value,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached InlinedConditionProfile isGetSetDescrProfile,
                        @Shared @Cached DescriptorCheckNode descriptorCheckNode,
                        @Cached DescrSetNode setNode) {
            Object type;
            Object name;
            if (isGetSetDescrProfile.profile(inliningTarget, descr instanceof GetSetDescriptor)) {
                GetSetDescriptor getSet = (GetSetDescriptor) descr;
                type = getSet.getType();
                name = getSet.getName();
            } else if (descr instanceof HiddenAttrDescriptor hidden) {
                type = hidden.getType();
                name = hidden.getAttr();
            } else {
                throw CompilerDirectives.shouldNotReachHere("Not a GetSetDescriptor nor HiddenAttrDescriptor");
            }
            descriptorCheckNode.execute(inliningTarget, type, name, obj);
            setNode.execute(frame, descr, obj, value);
        }

        @Specialization(guards = "isNoValue(value)")
        static void doDescriptorDel(VirtualFrame frame, Object descr, Object obj, @SuppressWarnings("unused") Object value,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached InlinedConditionProfile isGetSetDescrProfile,
                        @Shared @Cached DescriptorCheckNode descriptorCheckNode,
                        @Cached DescrDeleteNode deleteNode) {
            Object type;
            Object name;
            if (isGetSetDescrProfile.profile(inliningTarget, descr instanceof GetSetDescriptor)) {
                GetSetDescriptor getSet = (GetSetDescriptor) descr;
                type = getSet.getType();
                name = getSet.getName();
            } else if (descr instanceof HiddenAttrDescriptor hidden) {
                type = hidden.getType();
                name = hidden.getAttr();
            } else {
                throw CompilerDirectives.shouldNotReachHere("Not a GetSetDescriptor nor HiddenAttrDescriptor");
            }
            descriptorCheckNode.execute(inliningTarget, type, name, obj);
            deleteNode.execute(frame, descr, obj);
        }
    }
}
