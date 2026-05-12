/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.method;

import static com.oracle.graal.python.nodes.BuiltinNames.J_CLASSMETHOD;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.List;

import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetObjectSlotsNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotDescrGet.CallSlotDescrGet;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotDescrGet.DescrGetBuiltinNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectReprAsTruffleStringNode;
import com.oracle.graal.python.lib.PyObjectSetAttr;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;
import com.oracle.truffle.api.strings.TruffleStringBuilderUTF32;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PClassmethod)
public final class ClassmethodBuiltins extends PythonBuiltins {

    public static final TpSlots SLOTS = ClassmethodBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ClassmethodBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = J_CLASSMETHOD, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ClassmethodNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object doObjectIndirect(Object cls, @SuppressWarnings("unused") Object callable,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape) {
            return PFactory.createClassmethod(cls, getInstanceShape.execute(cls));
        }
    }

    @Slot(value = SlotKind.tp_init, isComplex = true)
    @Slot.SlotSignature(name = "classmethod", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class InitNode extends PythonBinaryBuiltinNode {
        @Specialization
        protected PNone init(VirtualFrame frame, PDecoratedMethod self, Object callable,
                        @Bind Node inliningTarget,
                        @Cached PyObjectLookupAttr lookup,
                        @Cached PyObjectSetAttr setAttr) {
            self.setCallable(callable);
            DecoratedMethodBuiltins.wraps(frame, self, callable, inliningTarget, lookup, setAttr);
            return PNone.NONE;
        }
    }

    @Slot(value = SlotKind.tp_descr_get, isComplex = true)
    @ReportPolymorphism
    @GenerateUncached
    @GenerateNodeFactory
    abstract static class GetNode extends DescrGetBuiltinNode {
        // If self.getCallable() is null, let the next @Specialization handle that
        @Specialization(guards = {"isSingleContext()", "isNoValue(type) == typeIsNoValue", "cachedSelf == self", "cachedCallable != null"}, limit = "3")
        static Object getCached(VirtualFrame frame, @SuppressWarnings("unused") PDecoratedMethod self, Object obj, Object type,
                        @Bind Node inliningTarget,
                        @SuppressWarnings("unused") @Cached(value = "self", weak = true) PDecoratedMethod cachedSelf,
                        @Cached(value = "self.getCallable()", weak = true) Object cachedCallable,
                        @Cached("isNoValue(type)") boolean typeIsNoValue,
                        @Exclusive @Cached GetClassNode getClass,
                        @Exclusive @Cached GetObjectSlotsNode getCallableSlots,
                        @Exclusive @Cached CallSlotDescrGet callGet,
                        @Exclusive @Cached ClassmethodCommonBuiltins.MakeMethodNode makeMethod) {
            Object actualType = typeIsNoValue ? getClass.execute(inliningTarget, obj) : type;
            return doGet(frame, inliningTarget, actualType, cachedCallable, getCallableSlots, callGet, makeMethod);
        }

        @InliningCutoff
        @Specialization(replaces = "getCached")
        static Object get(VirtualFrame frame, PDecoratedMethod self, Object obj, Object type,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached GetClassNode getClass,
                        @Exclusive @Cached GetObjectSlotsNode getCallableSlots,
                        @Exclusive @Cached CallSlotDescrGet callGet,
                        @Exclusive @Cached ClassmethodCommonBuiltins.MakeMethodNode makeMethod,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            Object actualType = PGuards.isNoValue(type) ? getClass.execute(inliningTarget, obj) : type;
            return doGet(frame, inliningTarget, actualType, ClassmethodCommonBuiltins.getCallable(inliningTarget, self, raiseNode),
                            getCallableSlots, callGet, makeMethod);
        }

        private static Object doGet(VirtualFrame frame, Node inliningTarget, Object type, Object callable,
                        GetObjectSlotsNode getCallableSlots, CallSlotDescrGet callGet, ClassmethodCommonBuiltins.MakeMethodNode makeMethod) {
            // GraalPy exposes tp_descr_get on method objects, but CPython's method type does not.
            if (!(callable instanceof PMethod)) {
                TpSlot get = getCallableSlots.execute(inliningTarget, callable).tp_descr_get();
                if (get != null) {
                    return callGet(frame, inliningTarget, type, callable, callGet, get);
                }
            }
            return makeMethod.execute(inliningTarget, type, callable);
        }

        @InliningCutoff
        private static Object callGet(VirtualFrame frame, Node inliningTarget, Object type, Object callable, CallSlotDescrGet callGet, TpSlot get) {
            return callGet.execute(frame, inliningTarget, get, callable, type, type);
        }
    }

    @Slot(value = SlotKind.tp_repr, isComplex = true)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {
        private static final TruffleString PREFIX = tsLiteral("<classmethod(");
        private static final int PREFIX_LEN = PREFIX.byteLength(TS_ENCODING);
        private static final TruffleString SUFFIX = tsLiteral(")>");
        private static final int SUFFIX_LEN = SUFFIX.byteLength(TS_ENCODING);

        @Specialization
        Object repr(VirtualFrame frame, PDecoratedMethod self,
                        @Bind Node inliningTarget,
                        @Cached PyObjectReprAsTruffleStringNode repr,
                        @Cached TruffleStringBuilder.AppendStringNode append,
                        @Cached TruffleStringBuilder.ToStringNode toString) {
            TruffleString callableRepr = repr.execute(frame, inliningTarget, self.getCallable());
            TruffleStringBuilderUTF32 sb = TruffleStringBuilder.createUTF32(PREFIX_LEN + callableRepr.byteLength(TS_ENCODING) + SUFFIX_LEN);
            append.execute(sb, PREFIX);
            append.execute(sb, callableRepr);
            append.execute(sb, SUFFIX);
            return toString.execute(sb);
        }
    }
}
