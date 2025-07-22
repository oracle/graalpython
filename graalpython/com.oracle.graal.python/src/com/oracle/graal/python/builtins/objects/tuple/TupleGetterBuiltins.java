/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.nodes.BuiltinNames.J_TUPLE_GETTER;
import static com.oracle.graal.python.nodes.ErrorMessages.CANT_DELETE_ATTRIBUTE;
import static com.oracle.graal.python.nodes.ErrorMessages.CANT_SET_ATTRIBUTE;
import static com.oracle.graal.python.nodes.ErrorMessages.DESC_FOR_INDEX_S_FOR_S_DOESNT_APPLY_TO_P;
import static com.oracle.graal.python.nodes.ErrorMessages.TUPLE_OUT_OF_BOUNDS;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___DOC__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.tuple.TupleGetterBuiltinsClinicProviders.TupleGetterNodeClinicProviderGen;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotDescrGet.DescrGetBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotDescrSet.DescrSetBuiltinNode;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PTupleGetter)
public final class TupleGetterBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = TupleGetterBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return TupleGetterBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = J_TUPLE_GETTER, parameterNames = {"cls", "index", "doc"})
    @ArgumentClinic(name = "index", conversion = ArgumentClinic.ClinicConversion.Index)
    @GenerateNodeFactory
    public abstract static class TupleGetterNode extends PythonTernaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return TupleGetterNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        Object construct(@SuppressWarnings("unused") Object cls, int index, Object doc,
                        @Bind PythonLanguage language) {
            return PFactory.createTupleGetter(language, index, doc);
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object reduce(PTupleGetter self,
                        @Bind Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Bind PythonLanguage language) {
            PTuple args = PFactory.createTuple(language, new Object[]{self.getIndex(), self.getDoc()});
            return PFactory.createTuple(language, new Object[]{getClassNode.execute(inliningTarget, self), args});
        }
    }

    @Slot(value = SlotKind.tp_descr_get, isComplex = true)
    @GenerateNodeFactory
    abstract static class TupleGetterGetNode extends DescrGetBuiltinNode {
        @Specialization()
        static Object getTuple(VirtualFrame frame, PTupleGetter self, PTuple instance, @SuppressWarnings("unused") Object owner,
                        @Bind Node inliningTarget,
                        @Cached PyObjectSizeNode sizeNode,
                        @Cached TupleBuiltins.GetItemNode getItemNode,
                        @Cached PRaiseNode raiseNode) {
            final int index = self.getIndex();
            if (index >= sizeNode.execute(frame, inliningTarget, instance)) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.IndexError, TUPLE_OUT_OF_BOUNDS);
            }
            return getItemNode.execute(frame, instance, index);
        }

        @Specialization
        static Object getNone(@SuppressWarnings("unused") VirtualFrame frame, PTupleGetter self, @SuppressWarnings("unused") PNone instance, @SuppressWarnings("unused") Object owner) {
            return self;
        }

        @Fallback
        @InliningCutoff
        static Object getOthers(@SuppressWarnings("unused") VirtualFrame frame, Object self, Object instance, @SuppressWarnings("unused") Object owner,
                        @Bind Node inliningTarget) {
            final int index = ((PTupleGetter) self).getIndex();
            throw PRaiseNode.raiseStatic(inliningTarget, PythonBuiltinClassType.TypeError, DESC_FOR_INDEX_S_FOR_S_DOESNT_APPLY_TO_P,
                            index, "tuple subclasses", instance);
        }
    }

    @Slot(value = SlotKind.tp_descr_set, isComplex = true)
    @GenerateNodeFactory
    abstract static class DescrSet extends DescrSetBuiltinNode {
        @Specialization
        @TruffleBoundary
        @SuppressWarnings("unused")
        void set(PTupleGetter self, Object instance, Object value) {
            if (PGuards.isNoValue(value)) {
                throw PRaiseNode.raiseStatic(this, PythonBuiltinClassType.AttributeError, CANT_DELETE_ATTRIBUTE);
            } else {
                throw PRaiseNode.raiseStatic(this, PythonBuiltinClassType.AttributeError, CANT_SET_ATTRIBUTE);
            }
        }
    }

    @Builtin(name = J___DOC__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class TupleGetterDocNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "!isNoValue(value)")
        Object set(PTupleGetter self, Object value) {
            self.setDoc(value);
            return PNone.NONE;
        }

        @Specialization(guards = "isNoValue(value)")
        Object get(PTupleGetter self, @SuppressWarnings("unused") PNone value) {
            return self.getDoc();
        }
    }
}
