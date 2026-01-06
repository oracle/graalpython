/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.weakref;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.ObjectNodes;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.referencetype.PReferenceType;
import com.oracle.graal.python.builtins.objects.referencetype.ReferenceTypeBuiltins;
import com.oracle.graal.python.builtins.objects.str.StringNodes;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryFunc;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryOp;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotGetAttr;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotInquiry;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotLen;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotMpAssSubscript;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotRichCompare;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSetAttr;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSizeArgFun;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSqAssItem;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSqContains;
import com.oracle.graal.python.lib.PyIterCheckNode;
import com.oracle.graal.python.lib.PyIterNextNode;
import com.oracle.graal.python.lib.PyNumberAbsoluteNode;
import com.oracle.graal.python.lib.PyNumberDivmodNode;
import com.oracle.graal.python.lib.PyNumberFloatNode;
import com.oracle.graal.python.lib.PyNumberIndexNode;
import com.oracle.graal.python.lib.PyNumberInvertNode;
import com.oracle.graal.python.lib.PyNumberLongNode;
import com.oracle.graal.python.lib.PyNumberNegativeNode;
import com.oracle.graal.python.lib.PyNumberPowerNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectRichCompare;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.lib.PyObjectStrAsObjectNode;
import com.oracle.graal.python.lib.PySequenceContainsNode;
import com.oracle.graal.python.lib.RichCmpOp;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.util.CastToJavaBooleanNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.strings.TruffleString;

import java.util.List;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ReferenceError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.nodes.BuiltinNames.T_BOOL;
import static com.oracle.graal.python.nodes.BuiltinNames.T_ROUND;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___BYTES__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REVERSED__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ROUND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___AND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___BYTES__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___CALL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___DELITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___FLOORDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IAND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IFLOORDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ILSHIFT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IMATMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IMOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IOR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IRSHIFT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ISUB__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ITRUEDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IXOR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___LSHIFT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___MATMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___MOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___MUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___OR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___POS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___REVERSED__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___RSHIFT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___SETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___SUB__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___TRUEDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___XOR__;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PProxyType)
public final class ProxyTypeBuiltins extends PythonBuiltins {

    public static final TpSlots SLOTS = ProxyTypeBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ProxyTypeBuiltinsFactory.getFactories();
    }

    @Slot(value = Slot.SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = "ProxyType", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class NewNode extends PythonBuiltinNode {

        @Specialization
        static PProxyType proxyType(VirtualFrame frame, Object cls, Object object, @SuppressWarnings("unused") Object callback,
                        @Bind Node inliningTarget,
                        @Cached NewProxyTypeNode newProxyTypeNode) {
            return newProxyTypeNode.execute(frame, inliningTarget, object, callback);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class NewProxyTypeNode extends Node {

        public abstract PProxyType execute(VirtualFrame frame, Node inliningTarget, Object object, Object callbackObject);

        @Specialization
        static PProxyType newNode(VirtualFrame frame, Node inliningTarget, Object object, Object callbackObject,
                        @Bind PythonLanguage language,
                        @Cached ReferenceTypeBuiltins.ReferenceTypeNode newReferenceNode) {
            final Object callback;
            if (callbackObject == PNone.NO_VALUE) {
                callback = PNone.NONE;
            } else {
                callback = callbackObject;
            }

            Object referenceClass = PythonBuiltinClassType.PReferenceType;
            PReferenceType weakReference = newReferenceNode.execute(referenceClass, object, callback);

            PythonBuiltinClassType cls = PythonBuiltinClassType.PProxyType;
            Shape shape = cls.getInstanceShape(language);
            return new PProxyType(cls, shape, object, weakReference);
        }
    }

    @Slot(value = Slot.SlotKind.tp_getattro, isComplex = true)
    @GenerateNodeFactory
    public abstract static class GetAttributeNode extends TpSlotGetAttr.GetAttrBuiltinNode {

        @Specialization
        static Object getAttribute(VirtualFrame frame, PProxyType proxy, Object keyObject,
                        @Bind Node inliningTarget,
                        @Cached StringNodes.CastToTruffleStringChecked1Node castKeyNode,
                        @Cached PyObjectGetAttr getAttrNode) {
            TruffleString key = castKeyNode.cast(inliningTarget, keyObject, ErrorMessages.ATTR_NAME_MUST_BE_STRING, keyObject);
            Object object = unwrap(proxy, inliningTarget);
            return getAttrNode.execute(frame, inliningTarget, object, key);
        }
    }

    @Slot(value = Slot.SlotKind.tp_setattro, isComplex = true)
    @GenerateNodeFactory
    public abstract static class SetAttrNode extends TpSlotSetAttr.SetAttrBuiltinNode {

        @Specialization
        static void setAttribute(VirtualFrame frame, PProxyType self, Object keyObject, Object value,
                        @Bind Node inliningTarget,
                        @Cached StringNodes.CastToTruffleStringChecked1Node castKeyNode,
                        @Cached ObjectNodes.GenericSetAttrNode genericSetAttrNode,
                        @Cached WriteAttributeToObjectNode write) {
            TruffleString key = castKeyNode.cast(inliningTarget, keyObject, ErrorMessages.ATTR_NAME_MUST_BE_STRING, keyObject);
            Object object = unwrap(self, inliningTarget);
            genericSetAttrNode.execute(inliningTarget, frame, object, key, value, write);
        }
    }

    @Slot(value = Slot.SlotKind.mp_subscript, isComplex = true)
    @GenerateNodeFactory
    public abstract static class GetItemNode extends TpSlotBinaryFunc.MpSubscriptBuiltinNode {

        @Specialization
        static Object getItem(VirtualFrame frame, PProxyType self, Object index,
                        @Bind Node inliningTarget,
                        @Cached PyObjectGetItem getItem) {
            Object object = unwrap(self, inliningTarget);
            return getItem.execute(frame, inliningTarget, object, index);
        }
    }

    @Slot(value = Slot.SlotKind.mp_ass_subscript, isComplex = true)
    @GenerateNodeFactory
    abstract static class SetSubscriptNode extends TpSlotMpAssSubscript.MpAssSubscriptBuiltinNode {

        @Specialization(guards = "!isNoValue(value)")
        static void setItem(VirtualFrame frame, PProxyType self, Object key, Object value,
                        @Bind Node inliningTarget,
                        @Cached @Shared PyObjectCallMethodObjArgs callMethod) {
            Object object = unwrap(self, inliningTarget);
            callMethod.execute(frame, inliningTarget, object, T___SETITEM__, key, value);
        }

        @Specialization(guards = "isNoValue(value)")
        static void deleteItem(VirtualFrame frame, PProxyType self, Object key, @SuppressWarnings("unused") Object value,
                        @Bind Node inliningTarget,
                        @Cached @Shared PyObjectCallMethodObjArgs callMethod) {
            Object object = unwrap(self, inliningTarget);
            callMethod.execute(frame, inliningTarget, object, T___DELITEM__, key);
        }
    }

    @GenerateNodeFactory
    @Slot(value = Slot.SlotKind.tp_repr, isComplex = true)
    abstract static class ReprNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object repr(PProxyType self,
                        @Bind Node inliningTarget,
                        @Cached ObjectNodes.GetIdNode getIdNode,
                        @Cached TypeNodes.GetQualNameNode getQualNameNode,
                        @Cached CastToJavaStringNode castToJavaStringNode) {
            Object object = unwrap(self, inliningTarget);

            long selfId = (long) getIdNode.execute(self);
            long objectId = (long) getIdNode.execute(object);
            TruffleString objectTypeNameTS = getQualNameNode.execute(inliningTarget, ((PythonObject) object).getPythonClass());
            String objectTypeName = castToJavaStringNode.execute(objectTypeNameTS);

            return reprBoundary(selfId, objectId, objectTypeName);
        }

        @TruffleBoundary
        private static TruffleString reprBoundary(long selfId, long objectId, String typeName) {
            String string = String.format("<weakproxy at %d to %s at %d>", selfId, typeName, objectId);
            return TruffleString.FromJavaStringNode.getUncached().execute(string, TS_ENCODING);
        }
    }

    @Slot(value = Slot.SlotKind.tp_str, isComplex = true)
    @GenerateNodeFactory
    public abstract static class StrNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object str(VirtualFrame frame, PProxyType self,
                        @Bind Node inliningTarget,
                        @Cached PyObjectStrAsObjectNode strNode) {
            Object object = unwrap(self, inliningTarget);
            return strNode.execute(frame, inliningTarget, object);
        }
    }

    @Slot(value = Slot.SlotKind.tp_richcompare, isComplex = true)
    @GenerateNodeFactory
    public abstract static class RichCompareNode extends TpSlotRichCompare.RichCmpBuiltinNode {
        @Specialization
        static Object richCmp(VirtualFrame frame, PProxyType self, Object other, RichCmpOp op,
                        @Bind Node inliningTarget,
                        @Cached PyObjectRichCompare richCompareNode) {
            Object selfUnwrapped = unwrap(self, inliningTarget);
            Object otherUnwrapped = unwrap(other, inliningTarget);
            return richCompareNode.execute(frame, inliningTarget, selfUnwrapped, otherUnwrapped, op);
        }
    }

    @Slot(value = Slot.SlotKind.nb_bool)
    @GenerateUncached
    @GenerateNodeFactory
    public abstract static class BoolNode extends TpSlotInquiry.NbBoolBuiltinNode {

        @Specialization
        static boolean bool(PProxyType self,
                        @Bind Node inliningTarget,
                        @Cached PyObjectLookupAttr lookupNode,
                        @Cached com.oracle.graal.python.nodes.call.CallNode callNode,
                        @Cached CastToJavaBooleanNode castToJavaBooleanNode) {
            Object object = unwrap(self, inliningTarget);
            PythonModule builtins = PythonContext.get(inliningTarget).getBuiltins();
            Object bool = lookupNode.execute(null, inliningTarget, builtins, T_BOOL);
            Object isBool = callNode.executeWithoutFrame(bool, object);
            return castToJavaBooleanNode.execute(inliningTarget, isBool);
        }
    }

    @Slot(value = Slot.SlotKind.tp_call, isComplex = true)
    @SlotSignature(minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class CallNode extends PythonVarargsBuiltinNode {

        @Specialization
        static Object call(VirtualFrame frame, PProxyType self, Object[] arguments, PKeyword[] keywords,
                        @Bind Node inliningTarget,
                        @Cached PyObjectLookupAttr lookupNode,
                        @Cached com.oracle.graal.python.nodes.call.CallNode callNode) {
            Object object = unwrap(self, inliningTarget);
            Object call = lookupNode.execute(frame, inliningTarget, object, T___CALL__);
            return callNode.execute(frame, call, arguments, keywords);
        }
    }

    @Slot(value = Slot.SlotKind.sq_length, isComplex = true)
    @Slot(value = Slot.SlotKind.mp_length, isComplex = true)
    @GenerateNodeFactory
    abstract static class LenNode extends TpSlotLen.LenBuiltinNode {

        @Specialization
        static int len(VirtualFrame frame, PProxyType self,
                        @Bind Node inliningTarget,
                        @Cached PyObjectSizeNode sizeNode) {
            Object object = unwrap(self, inliningTarget);
            return sizeNode.execute(frame, inliningTarget, object);
        }
    }

    @Slot(value = Slot.SlotKind.tp_iter, isComplex = true)
    @GenerateNodeFactory
    abstract static class IterNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object iter(VirtualFrame frame, PProxyType self,
                        @Bind Node inliningTarget,
                        @Cached PyObjectGetIter getIter) {
            Object object = unwrap(self, inliningTarget);
            return getIter.execute(frame, inliningTarget, object);
        }
    }

    @Slot(value = Slot.SlotKind.tp_iternext, isComplex = true)
    @GenerateNodeFactory
    public abstract static class NextNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object iternext(VirtualFrame frame, PProxyType self,
                        @Bind Node inliningTarget,
                        @Cached PyIterCheckNode iterCheckNode,
                        @Cached PRaiseNode raiseNode,
                        @Cached PyIterNextNode iterNextNode) {
            Object object = unwrap(self, inliningTarget);

            if (!(iterCheckNode.execute(inliningTarget, object))) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.WEAKREF_PROXY_REFERENCED_A_NON_ITERATOR_S_OBJECT, ((PythonObject) object).getPythonClass());
            }

            return iterNextNode.execute(frame, inliningTarget, object);
        }
    }

    @Slot(value = Slot.SlotKind.sq_contains, isComplex = true)
    @GenerateNodeFactory
    public abstract static class ContainsNode extends TpSlotSqContains.SqContainsBuiltinNode {

        @Specialization
        boolean contains(VirtualFrame frame, PProxyType self, Object key,
                        @Bind Node inliningTarget,
                        @Cached PySequenceContainsNode containsNode) {
            Object object = unwrap(self, inliningTarget);
            return containsNode.execute(frame, inliningTarget, object, key);
        }
    }

    @Slot(value = Slot.SlotKind.nb_add, isComplex = true)
    @GenerateNodeFactory
    abstract static class AddNode extends TpSlotBinaryOp.BinaryOpBuiltinNode {

        @Specialization
        static Object add(VirtualFrame frame, Object left, Object right,
                        @Bind Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            Object leftUnwrapped = unwrap(left, inliningTarget);
            Object rightUnwrapped = unwrap(right, inliningTarget);
            return callMethod.execute(frame, inliningTarget, leftUnwrapped, T___ADD__, rightUnwrapped);
        }
    }

    @Slot(value = Slot.SlotKind.nb_inplace_add, isComplex = true)
    @GenerateNodeFactory
    abstract static class IAddNode extends TpSlotBinaryOp.BinaryOpBuiltinNode {

        @Specialization
        static Object iadd(VirtualFrame frame, PProxyType left, Object right,
                        @Bind Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            Object leftUnwrapped = unwrap(left, inliningTarget);
            Object rightUnwrapped = unwrap(right, inliningTarget);
            return callMethod.execute(frame, inliningTarget, leftUnwrapped, T___IADD__, rightUnwrapped);
        }
    }

    @Slot(value = Slot.SlotKind.nb_subtract, isComplex = true)
    @GenerateNodeFactory
    abstract static class SubNode extends TpSlotBinaryOp.BinaryOpBuiltinNode {

        @Specialization
        static Object sub(VirtualFrame frame, Object left, Object right,
                        @Bind Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            Object leftUnwrapped = unwrap(left, inliningTarget);
            Object rightUnwrapped = unwrap(right, inliningTarget);
            return callMethod.execute(frame, inliningTarget, leftUnwrapped, T___SUB__, rightUnwrapped);
        }
    }

    @Slot(value = Slot.SlotKind.nb_inplace_subtract, isComplex = true)
    @GenerateNodeFactory
    abstract static class ISubNode extends TpSlotBinaryOp.BinaryOpBuiltinNode {

        @Specialization
        static Object isub(VirtualFrame frame, PProxyType left, Object right,
                        @Bind Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            Object leftUnwrapped = unwrap(left, inliningTarget);
            Object rightUnwrapped = unwrap(right, inliningTarget);
            return callMethod.execute(frame, inliningTarget, leftUnwrapped, T___ISUB__, rightUnwrapped);
        }
    }

    @Slot(value = Slot.SlotKind.nb_multiply, isComplex = true)
    @GenerateNodeFactory
    abstract static class MulNode extends TpSlotBinaryOp.BinaryOpBuiltinNode {

        @Specialization
        static Object mul(VirtualFrame frame, Object left, Object right,
                        @Bind Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            Object leftUnwrapped = unwrap(left, inliningTarget);
            Object rightUnwrapped = unwrap(right, inliningTarget);
            return callMethod.execute(frame, inliningTarget, leftUnwrapped, T___MUL__, rightUnwrapped);
        }
    }

    @Slot(value = Slot.SlotKind.nb_inplace_multiply, isComplex = true)
    @GenerateNodeFactory
    abstract static class IMulNode extends TpSlotBinaryOp.BinaryOpBuiltinNode {

        @Specialization
        static Object imul(VirtualFrame frame, PProxyType left, Object right,
                        @Bind Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            Object leftUnwrapped = unwrap(left, inliningTarget);
            Object rightUnwrapped = unwrap(right, inliningTarget);
            return callMethod.execute(frame, inliningTarget, leftUnwrapped, T___IMUL__, rightUnwrapped);
        }
    }

    @Slot(value = Slot.SlotKind.nb_matrix_multiply, isComplex = true)
    @GenerateNodeFactory
    abstract static class MatMulNode extends TpSlotBinaryOp.BinaryOpBuiltinNode {

        @Specialization
        static Object matmul(VirtualFrame frame, Object left, Object right,
                        @Bind Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            Object leftUnwrapped = unwrap(left, inliningTarget);
            Object rightUnwrapped = unwrap(right, inliningTarget);
            return callMethod.execute(frame, inliningTarget, leftUnwrapped, T___MATMUL__, rightUnwrapped);
        }
    }

    @Slot(value = Slot.SlotKind.nb_inplace_matrix_multiply, isComplex = true)
    @GenerateNodeFactory
    abstract static class IMatMulNode extends TpSlotBinaryOp.BinaryOpBuiltinNode {

        @Specialization
        static Object imatmul(VirtualFrame frame, PProxyType left, Object right,
                        @Bind Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            Object leftUnwrapped = unwrap(left, inliningTarget);
            Object rightUnwrapped = unwrap(right, inliningTarget);
            return callMethod.execute(frame, inliningTarget, leftUnwrapped, T___IMATMUL__, rightUnwrapped);
        }
    }

    @Slot(value = Slot.SlotKind.nb_true_divide, isComplex = true)
    @GenerateNodeFactory
    abstract static class DivNode extends TpSlotBinaryOp.BinaryOpBuiltinNode {

        @Specialization
        static Object truediv(VirtualFrame frame, Object left, Object right,
                        @Bind Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            Object leftUnwrapped = unwrap(left, inliningTarget);
            Object rightUnwrapped = unwrap(right, inliningTarget);
            return callMethod.execute(frame, inliningTarget, leftUnwrapped, T___TRUEDIV__, rightUnwrapped);
        }
    }

    @Slot(value = Slot.SlotKind.nb_inplace_true_divide, isComplex = true)
    @GenerateNodeFactory
    abstract static class IDivNode extends TpSlotBinaryOp.BinaryOpBuiltinNode {

        @Specialization
        static Object itruediv(VirtualFrame frame, PProxyType left, Object right,
                        @Bind Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            Object leftUnwrapped = unwrap(left, inliningTarget);
            Object rightUnwrapped = unwrap(right, inliningTarget);
            return callMethod.execute(frame, inliningTarget, leftUnwrapped, T___ITRUEDIV__, rightUnwrapped);
        }
    }

    @Slot(value = Slot.SlotKind.nb_floor_divide, isComplex = true)
    @GenerateNodeFactory
    abstract static class FloorDivNode extends TpSlotBinaryOp.BinaryOpBuiltinNode {

        @Specialization
        static Object floordiv(VirtualFrame frame, Object left, Object right,
                        @Bind Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            Object leftUnwrapped = unwrap(left, inliningTarget);
            Object rightUnwrapped = unwrap(right, inliningTarget);
            return callMethod.execute(frame, inliningTarget, leftUnwrapped, T___FLOORDIV__, rightUnwrapped);
        }
    }

    @Slot(value = Slot.SlotKind.nb_inplace_floor_divide, isComplex = true)
    @GenerateNodeFactory
    abstract static class IFloorDivNode extends TpSlotBinaryOp.BinaryOpBuiltinNode {

        @Specialization
        static Object ifloordiv(VirtualFrame frame, PProxyType left, Object right,
                        @Bind Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            Object leftUnwrapped = unwrap(left, inliningTarget);
            Object rightUnwrapped = unwrap(right, inliningTarget);
            return callMethod.execute(frame, inliningTarget, leftUnwrapped, T___IFLOORDIV__, rightUnwrapped);
        }
    }

    @Slot(value = Slot.SlotKind.nb_remainder, isComplex = true)
    @GenerateNodeFactory
    public abstract static class ModNode extends TpSlotBinaryOp.BinaryOpBuiltinNode {

        @Specialization
        static Object mod(VirtualFrame frame, Object left, Object right,
                        @Bind Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            Object leftUnwrapped = unwrap(left, inliningTarget);
            Object rightUnwrapped = unwrap(right, inliningTarget);
            return callMethod.execute(frame, inliningTarget, leftUnwrapped, T___MOD__, rightUnwrapped);
        }
    }

    @Slot(value = Slot.SlotKind.nb_inplace_remainder, isComplex = true)
    @GenerateNodeFactory
    public abstract static class IModNode extends TpSlotBinaryOp.BinaryOpBuiltinNode {

        @Specialization
        static Object imod(VirtualFrame frame, PProxyType left, Object right,
                        @Bind Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            Object leftUnwrapped = unwrap(left, inliningTarget);
            Object rightUnwrapped = unwrap(right, inliningTarget);
            return callMethod.execute(frame, inliningTarget, leftUnwrapped, T___IMOD__, rightUnwrapped);
        }
    }

    @Slot(value = Slot.SlotKind.nb_divmod, isComplex = true)
    @GenerateNodeFactory
    abstract static class DivModNode extends TpSlotBinaryOp.BinaryOpBuiltinNode {

        @Specialization
        static Object divmod(VirtualFrame frame, Object left, Object right,
                        @Bind Node inliningTarget,
                        @Cached PyNumberDivmodNode divmodNode) {
            Object leftUnwrapped = unwrap(left, inliningTarget);
            Object rightUnwrapped = unwrap(right, inliningTarget);
            return divmodNode.execute(frame, inliningTarget, leftUnwrapped, rightUnwrapped);
        }
    }

    @Slot(value = Slot.SlotKind.nb_power, isComplex = true)
    @GenerateNodeFactory
    abstract static class PowerNode extends PythonTernaryBuiltinNode {

        @Specialization
        static Object pow(VirtualFrame frame, Object left, Object right, Object modObject,
                        @Bind Node inliningTarget,
                        @Cached PyNumberPowerNode power) {
            Object leftUnwrapped = unwrap(left, inliningTarget);
            Object rightUnwrapped = unwrap(right, inliningTarget);
            Object mod = modObject == PNone.NO_VALUE ? PNone.NONE : modObject;
            return power.execute(frame, leftUnwrapped, rightUnwrapped, mod);
        }
    }

    // TODO: implement __ipow__. There is an issue with missing slot base class in SlotsMapping.java

    @Slot(value = Slot.SlotKind.nb_lshift, isComplex = true)
    @GenerateNodeFactory
    abstract static class LShiftNode extends TpSlotBinaryOp.BinaryOpBuiltinNode {

        @Specialization
        static Object lshift(VirtualFrame frame, Object left, Object right,
                        @Bind Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            Object leftUnwrapped = unwrap(left, inliningTarget);
            Object rightUnwrapped = unwrap(right, inliningTarget);
            return callMethod.execute(frame, inliningTarget, leftUnwrapped, T___LSHIFT__, rightUnwrapped);
        }
    }

    @Slot(value = Slot.SlotKind.nb_inplace_lshift, isComplex = true)
    @GenerateNodeFactory
    abstract static class ILShiftNode extends TpSlotBinaryOp.BinaryOpBuiltinNode {

        @Specialization
        static Object ilshift(VirtualFrame frame, PProxyType left, Object right,
                        @Bind Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            Object leftUnwrapped = unwrap(left, inliningTarget);
            Object rightUnwrapped = unwrap(right, inliningTarget);
            return callMethod.execute(frame, inliningTarget, leftUnwrapped, T___ILSHIFT__, rightUnwrapped);
        }
    }

    @Slot(value = Slot.SlotKind.nb_rshift, isComplex = true)
    @GenerateNodeFactory
    abstract static class RShiftNode extends TpSlotBinaryOp.BinaryOpBuiltinNode {

        @Specialization
        static Object rshift(VirtualFrame frame, Object left, Object right,
                        @Bind Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            Object leftUnwrapped = unwrap(left, inliningTarget);
            Object rightUnwrapped = unwrap(right, inliningTarget);
            return callMethod.execute(frame, inliningTarget, leftUnwrapped, T___RSHIFT__, rightUnwrapped);
        }
    }

    @Slot(value = Slot.SlotKind.nb_inplace_rshift, isComplex = true)
    @GenerateNodeFactory
    abstract static class IRShiftNode extends TpSlotBinaryOp.BinaryOpBuiltinNode {

        @Specialization
        static Object irshift(VirtualFrame frame, PProxyType left, Object right,
                        @Bind Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            Object leftUnwrapped = unwrap(left, inliningTarget);
            Object rightUnwrapped = unwrap(right, inliningTarget);
            return callMethod.execute(frame, inliningTarget, leftUnwrapped, T___IRSHIFT__, rightUnwrapped);
        }
    }

    @Slot(value = Slot.SlotKind.nb_and, isComplex = true)
    @GenerateNodeFactory
    abstract static class AndNode extends TpSlotBinaryOp.BinaryOpBuiltinNode {

        @Specialization
        static Object and(VirtualFrame frame, Object left, Object right,
                        @Bind Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            Object leftUnwrapped = unwrap(left, inliningTarget);
            Object rightUnwrapped = unwrap(right, inliningTarget);
            return callMethod.execute(frame, inliningTarget, leftUnwrapped, T___AND__, rightUnwrapped);
        }
    }

    @Slot(value = Slot.SlotKind.nb_inplace_and, isComplex = true)
    @GenerateNodeFactory
    abstract static class IAndNode extends TpSlotBinaryOp.BinaryOpBuiltinNode {

        @Specialization
        static Object iand(VirtualFrame frame, PProxyType left, Object right,
                        @Bind Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            Object leftUnwrapped = unwrap(left, inliningTarget);
            Object rightUnwrapped = unwrap(right, inliningTarget);
            return callMethod.execute(frame, inliningTarget, leftUnwrapped, T___IAND__, rightUnwrapped);
        }
    }

    @Slot(value = Slot.SlotKind.nb_xor, isComplex = true)
    @GenerateNodeFactory
    public abstract static class XorNode extends TpSlotBinaryOp.BinaryOpBuiltinNode {

        @Specialization
        static Object xor(VirtualFrame frame, Object left, Object right,
                        @Bind Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            Object leftUnwrapped = unwrap(left, inliningTarget);
            Object rightUnwrapped = unwrap(right, inliningTarget);
            return callMethod.execute(frame, inliningTarget, leftUnwrapped, T___XOR__, rightUnwrapped);
        }
    }

    @Slot(value = Slot.SlotKind.nb_inplace_xor, isComplex = true)
    @GenerateNodeFactory
    public abstract static class IXorNode extends TpSlotBinaryOp.BinaryOpBuiltinNode {

        @Specialization
        static Object ixor(VirtualFrame frame, PProxyType left, Object right,
                        @Bind Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            Object leftUnwrapped = unwrap(left, inliningTarget);
            Object rightUnwrapped = unwrap(right, inliningTarget);
            return callMethod.execute(frame, inliningTarget, leftUnwrapped, T___IXOR__, rightUnwrapped);
        }
    }

    @Slot(value = Slot.SlotKind.nb_or, isComplex = true)
    @GenerateNodeFactory
    public abstract static class OrNode extends TpSlotBinaryOp.BinaryOpBuiltinNode {

        @Specialization
        static Object or(VirtualFrame frame, Object left, Object right,
                        @Bind Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            Object leftUnwrapped = unwrap(left, inliningTarget);
            Object rightUnwrapped = unwrap(right, inliningTarget);
            return callMethod.execute(frame, inliningTarget, leftUnwrapped, T___OR__, rightUnwrapped);
        }
    }

    @Slot(value = Slot.SlotKind.nb_inplace_or, isComplex = true)
    @GenerateNodeFactory
    public abstract static class IOrNode extends TpSlotBinaryOp.BinaryOpBuiltinNode {

        @Specialization
        static Object ior(VirtualFrame frame, PProxyType left, Object right,
                        @Bind Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            Object leftUnwrapped = unwrap(left, inliningTarget);
            Object rightUnwrapped = unwrap(right, inliningTarget);
            return callMethod.execute(frame, inliningTarget, leftUnwrapped, T___IOR__, rightUnwrapped);
        }
    }

    @Slot(value = Slot.SlotKind.nb_negative, isComplex = true)
    @GenerateNodeFactory
    abstract static class NegNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object negative(VirtualFrame frame, PProxyType self,
                        @Bind Node inliningTarget,
                        @Cached PyNumberNegativeNode negativeNode) {
            Object object = unwrap(self, inliningTarget);
            return negativeNode.execute(frame, object);
        }
    }

    @Slot(value = Slot.SlotKind.nb_positive, isComplex = true)
    @GenerateNodeFactory
    abstract static class PosNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object positive(VirtualFrame frame, PProxyType self,
                        @Bind Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            Object object = unwrap(self, inliningTarget);
            return callMethod.execute(frame, inliningTarget, object, T___POS__);
        }
    }

    @Slot(value = Slot.SlotKind.nb_absolute, isComplex = true)
    @GenerateNodeFactory
    abstract static class AbsNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object absolute(VirtualFrame frame, PProxyType self,
                        @Bind Node inliningTarget,
                        @Cached PyNumberAbsoluteNode absoluteNode) {
            Object object = unwrap(self, inliningTarget);
            return absoluteNode.execute(frame, object);
        }
    }

    @Slot(value = Slot.SlotKind.nb_invert, isComplex = true)
    @GenerateNodeFactory
    abstract static class InvertNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object invert(VirtualFrame frame, PProxyType self,
                        @Bind Node inliningTarget,
                        @Cached PyNumberInvertNode invertNode) {
            Object object = unwrap(self, inliningTarget);
            return invertNode.execute(frame, object);
        }
    }

    @Slot(value = Slot.SlotKind.nb_int, isComplex = true)
    @GenerateNodeFactory
    abstract static class IntNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object getInt(VirtualFrame frame, PProxyType self,
                        @Bind Node inliningTarget,
                        @Cached PyNumberLongNode longNode) {
            Object object = unwrap(self, inliningTarget);
            return longNode.execute(frame, inliningTarget, object);
        }
    }

    @Slot(value = Slot.SlotKind.nb_float, isComplex = true)
    @GenerateNodeFactory
    abstract static class FloatNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object getFloat(VirtualFrame frame, PProxyType self,
                        @Bind Node inliningTarget,
                        @Cached PyNumberFloatNode floatNode) {
            Object object = unwrap(self, inliningTarget);
            return floatNode.execute(frame, inliningTarget, object);
        }
    }

    @Slot(value = Slot.SlotKind.nb_index, isComplex = true)
    @GenerateNodeFactory
    abstract static class IndexNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object index(VirtualFrame frame, PProxyType self,
                        @Bind Node inliningTarget,
                        @Cached PyNumberIndexNode indexNode) {
            Object object = unwrap(self, inliningTarget);
            return indexNode.execute(frame, inliningTarget, object);
        }
    }

    @Builtin(name = J___BYTES__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class BytesNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object getBytes(VirtualFrame frame, PProxyType self,
                        @Bind Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            Object object = unwrap(self, inliningTarget);
            return callMethod.execute(frame, inliningTarget, object, T___BYTES__);
        }
    }

    @Builtin(name = J___REVERSED__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReversedNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object reversed(VirtualFrame frame, PProxyType self,
                        @Bind Node inliningTarget,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            Object object = unwrap(self, inliningTarget);
            return callMethod.execute(frame, inliningTarget, object, T___REVERSED__);
        }
    }

    private static Object unwrap(Object object, Node node) {
        if (!(object instanceof PProxyType proxy)) {
            return object;
        }

        Object referencedObject = proxy.weakReference.getPyObject();
        if (referencedObject == PNone.NONE) {
            throw PRaiseNode.raiseStatic(node, ReferenceError, ErrorMessages.WEAKLY_REFERENCED_OBJECT_NO_LONGER_EXISTS);
        }

        return referencedObject;
    }
}
