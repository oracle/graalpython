/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
 * Copyright (c) 2014, Regents of the University of California
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

package com.oracle.graal.python.builtins.objects.foreign;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__ADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__BOOL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__CALL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__DELATTR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__DELITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__DIR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__FLOORDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETATTR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INDEX__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LEN__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__MUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RFLOORDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RSUB__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RTRUEDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SETATTR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__STR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SUB__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__TRUEDIV__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.AttributeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.MemoryError;

import java.util.Arrays;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.foreign.TruffleObjectBuiltinsFactory.MulNodeFactory;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.iterator.PForeignArrayIterator;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltinsFactory;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic;
import com.oracle.graal.python.nodes.expression.BinaryComparisonNode;
import com.oracle.graal.python.nodes.expression.CastToBooleanNode;
import com.oracle.graal.python.nodes.expression.CastToListNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.interop.PForeignToPTypeNode;
import com.oracle.graal.python.nodes.interop.PTypeToForeignNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ValueProfile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.TruffleObject)
public class TruffleObjectBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return TruffleObjectBuiltinsFactory.getFactories();
    }

    @Builtin(name = __BOOL__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class BoolNode extends UnboxNode {

        @Child private Node isNullNode;
        @Child private CastToBooleanNode castToBooleanNode;

        protected boolean isNull(TruffleObject o) {
            if (isNullNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isNullNode = insert(Message.IS_NULL.createNode());
            }
            return ForeignAccess.sendIsNull(isNullNode, o);
        }

        protected CastToBooleanNode getCastToBooleanNode() {
            if (castToBooleanNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castToBooleanNode = insert(CastToBooleanNode.createIfTrueNode());
            }
            return castToBooleanNode;
        }

        @Specialization(guards = {"!isNull(self)", "isBoxed(self)"})
        boolean doForeignBoxed(TruffleObject self) {
            try {
                return getCastToBooleanNode().executeWith(unboxLeft(self));
            } catch (UnsupportedMessageException e) {
                throw raise(PythonErrorType.RuntimeError, "Foreign boxed value '%s' cannot be unboxed.", self);
            }
        }

        @Specialization(guards = "isForeignArray(self)")
        boolean doForeignArray(TruffleObject self,
                        @Cached("GET_SIZE.createNode()") Node sizeNode) {
            try {
                return getCastToBooleanNode().executeWith(ForeignAccess.sendGetSize(sizeNode, self));
            } catch (UnsupportedMessageException e) {
                throw raise(PythonErrorType.RuntimeError, "Cannot get size of foreign array '%s'", self);
            }
        }

        @Specialization(guards = {"isForeignObject(self)", "!isBoxed(self)", "!isForeignArray(self)"})
        boolean doForeignObject(TruffleObject self) {
            return !isNull(self);
        }

        @Fallback
        Object doGeneric(@SuppressWarnings("unused") Object o) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __ADD__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class AddNode extends UnboxNode {
        @Child private LookupAndCallBinaryNode addNode = BinaryArithmetic.Add.create();

        @Specialization(guards = {"isBoxed(left)", "!isForeignObject(right)"})
        Object doForeignBoxed(TruffleObject left, Object right) {
            try {
                return addNode.executeObject(unboxLeft(left), right);
            } catch (UnsupportedMessageException e) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }

        @Specialization(guards = {"isBoxed(left)", "isBoxed(right)"})
        Object doForeignBoxed(TruffleObject left, TruffleObject right) {
            try {
                return doForeignBoxed(left, unboxRight(right));
            } catch (UnsupportedMessageException e) {
                // fall through
            }
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        @Specialization(guards = {"isForeignArray(left)", "isForeignArray(right)"})
        Object doForeignArray(TruffleObject left, TruffleObject right,
                        @Cached("READ.createNode()") Node readNode,
                        @Cached("GET_SIZE.createNode()") Node sizeNode) {

            Object[] unpackedLeft = unpackForeignArray(left, readNode, sizeNode);
            Object[] unpackedRight = unpackForeignArray(right, readNode, sizeNode);
            if (unpackedLeft != null && unpackedRight != null) {
                Object[] result = Arrays.copyOf(unpackedLeft, unpackedLeft.length + unpackedRight.length);
                for (int i = 0, j = unpackedLeft.length; i < unpackedRight.length && j < result.length; i++, j++) {
                    assert j < result.length;
                    result[j] = unpackedRight[i];
                }

                return factory().createList(result);
            }
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        @Specialization(guards = {"isForeignArray(left)", "right"})
        Object doForeignArray(TruffleObject left, @SuppressWarnings("unused") boolean right,
                        @Cached("READ.createNode()") Node readNode,
                        @Cached("GET_SIZE.createNode()") Node sizeNode) {
            Object[] unpacked = unpackForeignArray(left, readNode, sizeNode);
            if (unpacked != null) {
                return factory().createList(unpacked);
            }
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __RADD__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class RAddNode extends AddNode {
    }

    @Builtin(name = __LEN__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class LenNode extends PythonUnaryBuiltinNode {
        protected Node createHasSizeNode() {
            return Message.HAS_SIZE.createNode();
        }

        protected Node createGetSizeNode() {
            return Message.GET_SIZE.createNode();
        }

        @Specialization(guards = "isForeignObject(self)")
        public Object len(TruffleObject self,
                        @Cached("create()") PForeignToPTypeNode fromForeign,
                        @Cached("createHasSizeNode()") Node hasSizeNode,
                        @Cached("createGetSizeNode()") Node getSizeNode) {

            try {
                if (ForeignAccess.sendHasSize(hasSizeNode, self)) {
                    return fromForeign.executeConvert(ForeignAccess.sendGetSize(getSizeNode, self));
                }
            } catch (UnsupportedMessageException e) {
                // fall through
            }
            throw raise(AttributeError, "'foreign' object has no attribute 'len'");
        }

        @SuppressWarnings("unused")
        @Fallback
        Object doGeneric(Object self) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __MUL__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class MulNode extends UnboxNode {
        @Child private LookupAndCallBinaryNode mulNode = BinaryArithmetic.Mul.create();
        @Child private MulNode recursive;

        public abstract Object executeWith(Object left, Object right);

        @Specialization(guards = {"isBoxed(left)", "!isForeignObject(right)"})
        Object doForeignBoxed(TruffleObject left, Object right) {
            try {
                return mulNode.executeObject(unboxLeft(left), right);
            } catch (UnsupportedMessageException e) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }

        @Specialization(guards = {"isBoxed(left)", "isBoxed(right)"})
        Object doForeignBoxed(TruffleObject left, TruffleObject right) {
            try {
                return getRecursiveNode().executeWith(left, unboxRight(right));
            } catch (UnsupportedMessageException e) {
                // fall through
            }
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        @Specialization(guards = {"isForeignArray(left)", "!isBoxed(left)", "right > 0"})
        Object doForeignArray(TruffleObject left, int right,
                        @Cached("READ.createNode()") Node readNode,
                        @Cached("GET_SIZE.createNode()") Node sizeNode) {

            try {
                Object[] unpackForeignArray = unpackForeignArray(left, readNode, sizeNode);
                if (unpackForeignArray != null) {
                    Object[] repeatedData = new Object[Math.multiplyExact(unpackForeignArray.length, right)];

                    // repeat data
                    for (int i = 0; i < unpackForeignArray.length; i++) {
                        repeatedData[i] = unpackForeignArray[i % right];
                    }

                    return factory().createList(repeatedData);
                }
                return PNotImplemented.NOT_IMPLEMENTED;
            } catch (ArithmeticException e) {
                throw raise(MemoryError);
            }
        }

        @Specialization(guards = {"isForeignArray(left)", "!isBoxed(left)", "isBoxed(right)"})
        Object doForeignArray(TruffleObject left, TruffleObject right,
                        @Cached("READ.createNode()") Node readNode,
                        @Cached("GET_SIZE.createNode()") Node sizeNode) {

            try {
                Object[] unpackForeignArray = unpackForeignArray(left, readNode, sizeNode);
                if (unpackForeignArray != null) {
                    PList unpackedList = factory().createList(unpackForeignArray);
                    return mulNode.executeObject(unpackedList, unboxRight(right));
                }
            } catch (ArithmeticException e) {
                throw raise(MemoryError);
            } catch (UnsupportedMessageException e) {
                // fall through
            }
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        @Specialization(guards = {"isForeignArray(left)", "!isBoxed(left)", "right"})
        Object doForeignArray(TruffleObject left, @SuppressWarnings("unused") boolean right,
                        @Cached("READ.createNode()") Node readNode,
                        @Cached("GET_SIZE.createNode()") Node sizeNode) {
            Object[] unpacked = unpackForeignArray(left, readNode, sizeNode);
            if (unpacked != null) {
                return factory().createList(unpacked);
            }
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"isForeignArray(left)", "!isBoxed(left)", "!right"})
        Object doForeignArrayEmpty(TruffleObject left, boolean right) {
            return factory().createList();
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"isForeignArray(left)", "!isBoxed(left)", "right <= 0"})
        Object doForeignArrayEmpty(TruffleObject left, int right) {
            return factory().createList();
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"isForeignArray(left)", "!isBoxed(left)", "right <= 0"})
        Object doForeignArrayEmpty(TruffleObject left, long right) {
            return factory().createList();
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        private MulNode getRecursiveNode() {
            if (recursive == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                recursive = insert(MulNodeFactory.create(null));
            }
            return recursive;
        }
    }

    @Builtin(name = __RMUL__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class RMulNode extends MulNode {
    }

    @Builtin(name = __SUB__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class SubNode extends UnboxNode {
        @Child private LookupAndCallBinaryNode subNode = BinaryArithmetic.Sub.create();

        @Specialization(guards = {"isForeignObject(left)", "isForeignObject(right)"})
        Object addLeft(TruffleObject left, TruffleObject right) {
            try {
                return subNode.executeObject(unboxLeft(left), unboxRight(right));
            } catch (UnsupportedMessageException e) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }

        @Specialization(guards = {"isForeignObject(left)", "!isForeignObject(right)"})
        Object addLeft(TruffleObject left, Object right) {
            try {
                return subNode.executeObject(unboxLeft(left), right);
            } catch (UnsupportedMessageException e) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __RSUB__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class RSubNode extends UnboxNode {
        @Child private LookupAndCallBinaryNode subNode = BinaryArithmetic.Sub.create();

        @Specialization(guards = {"isBoxed(left)", "isBoxed(right)"})
        Object addLeft(TruffleObject right, TruffleObject left) {
            try {
                return subNode.executeObject(unboxLeft(left), unboxRight(right));
            } catch (UnsupportedMessageException e) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }

        @Specialization(guards = {"isBoxed(right)", "!isForeignObject(left)"})
        Object addLeft(TruffleObject right, Object left) {
            try {
                return subNode.executeObject(left, unboxRight(right));
            } catch (UnsupportedMessageException e) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @ImportStatic(Message.class)
    abstract static class UnboxNode extends PythonBuiltinNode {
        @Child private PForeignToPTypeNode fromForeignNode;
        @Child private Node isBoxedNode;
        @Child private Node hasSizeNode;
        @Child private Node unboxNode;
        @Child private Node hasKeysNode;

        private final ValueProfile unboxedTypeLeftProfile = ValueProfile.createClassProfile();
        private final ValueProfile unboxedRightTypeProfile = ValueProfile.createClassProfile();

        protected Object unboxLeft(TruffleObject boxedLeft) throws UnsupportedMessageException {
            return unboxedTypeLeftProfile.profile(unbox(boxedLeft));
        }

        protected Object unboxRight(TruffleObject boxedRight) throws UnsupportedMessageException {
            return unboxedRightTypeProfile.profile(unbox(boxedRight));
        }

        private Object unbox(TruffleObject boxedValue) throws UnsupportedMessageException {
            if (unboxNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                unboxNode = insert(Message.UNBOX.createNode());
            }
            return convertForeignValue(ForeignAccess.sendUnbox(unboxNode, boxedValue));
        }

        Object[] unpackForeignArray(TruffleObject left, Node readNode, Node sizeNode) {

            try {
                Object sizeObj = convertForeignValue(ForeignAccess.sendGetSize(sizeNode, left));
                if (sizeObj instanceof Integer) {
                    int size = (int) sizeObj;
                    Object[] data = new Object[size];

                    // read data
                    for (int i = 0; i < size; i++) {
                        data[i] = convertForeignValue(ForeignAccess.sendRead(readNode, left, i));
                    }

                    return data;
                }
            } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                // fall through
            }
            return null;
        }

        protected Object convertForeignValue(Object foreignVal) {
            if (fromForeignNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                fromForeignNode = insert(PForeignToPTypeNode.create());
            }
            return fromForeignNode.executeConvert(foreignVal);
        }

        protected boolean isNull(TruffleObject receiver, Node isNullNode) {
            return ForeignAccess.sendIsNull(isNullNode, receiver);
        }

        protected boolean isBoxed(TruffleObject receiver) {
            if (PGuards.isForeignObject(receiver)) {
                if (isBoxedNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    isBoxedNode = insert(Message.IS_BOXED.createNode());
                }
                return ForeignAccess.sendIsBoxed(isBoxedNode, receiver);
            }
            return false;
        }

        protected boolean isForeignArray(TruffleObject receiver) {
            if (PGuards.isForeignObject(receiver)) {
                if (hasSizeNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    hasSizeNode = insert(Message.HAS_SIZE.createNode());
                }
                return ForeignAccess.sendHasSize(hasSizeNode, receiver);
            }
            return false;
        }

        protected boolean isForeignMapping(TruffleObject receiver) {
            if (PGuards.isForeignObject(receiver)) {
                if (hasSizeNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    hasSizeNode = insert(Message.HAS_SIZE.createNode());
                }
                if (!ForeignAccess.sendHasSize(hasSizeNode, receiver)) {
                    return false;
                }
                if (hasKeysNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    hasKeysNode = insert(Message.HAS_KEYS.createNode());
                }
                return ForeignAccess.sendHasKeys(hasKeysNode, receiver);
            }
            return false;
        }
    }

    private abstract static class ForeignBinaryDelegate extends UnboxNode {
        @Child private LookupAndCallBinaryNode divNode;

        protected LookupAndCallBinaryNode getDelegate() {
            if (divNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                divNode = insert(getArithmetic().create());
            }
            return divNode;
        }

        protected abstract BinaryArithmetic getArithmetic();
    }

    @Builtin(name = __TRUEDIV__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class TrueDivNode extends ForeignBinaryDelegate {

        @Override
        protected BinaryArithmetic getArithmetic() {
            return BinaryArithmetic.TrueDiv;
        }

        @Specialization(guards = {"isBoxed(left)", "!isForeignObject(right)"})
        Object doForeignBoxed(TruffleObject left, Object right) {
            try {
                return getDelegate().executeObject(unboxLeft(left), right);
            } catch (UnsupportedMessageException e) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }

        @Specialization(guards = {"isBoxed(left)", "isBoxed(right)"})
        Object doForeignBoxed(TruffleObject left, TruffleObject right) {
            try {
                return doForeignBoxed(left, unboxRight(right));
            } catch (UnsupportedMessageException e) {
                // fall through
            }
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __RTRUEDIV__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class RTrueDivNode extends ForeignBinaryDelegate {

        @Override
        protected BinaryArithmetic getArithmetic() {
            return BinaryArithmetic.TrueDiv;
        }

        @Specialization(guards = {"isBoxed(right)", "!isForeignObject(left)"})
        Object doForeignBoxed(TruffleObject right, Object left) {
            try {
                return getDelegate().executeObject(left, unboxRight(right));
            } catch (UnsupportedMessageException e) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }

        @Specialization(guards = {"isBoxed(right)", "isBoxed(left)"})
        Object doForeignBoxed(TruffleObject right, TruffleObject left) {
            try {
                return doForeignBoxed(right, unboxLeft(left));
            } catch (UnsupportedMessageException e) {
                // fall through
            }
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __FLOORDIV__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class FloorDivNode extends TrueDivNode {
        @Override
        protected BinaryArithmetic getArithmetic() {
            return BinaryArithmetic.FloorDiv;
        }

    }

    @Builtin(name = __RFLOORDIV__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class RFloorDivNode extends RTrueDivNode {
        @Override
        protected BinaryArithmetic getArithmetic() {
            return BinaryArithmetic.FloorDiv;
        }
    }

    public abstract static class ForeignBinaryComparisonNode extends UnboxNode {

        @Child BinaryComparisonNode comparisonNode;

        protected ForeignBinaryComparisonNode(BinaryComparisonNode genericOp) {
            this.comparisonNode = genericOp;
        }

        @Specialization(guards = {"isBoxed(left)", "isBoxed(right)"})
        Object doComparison(TruffleObject left, TruffleObject right) {
            try {
                return comparisonNode.executeWith(unboxLeft(left), unboxRight(right));
            } catch (UnsupportedMessageException e) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }

        @Specialization(guards = {"isBoxed(left)", "!isForeignObject(right)"})
        Object doComparison(TruffleObject left, Object right) {
            try {
                return comparisonNode.executeWith(unboxLeft(left), right);
            } catch (UnsupportedMessageException e) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }

        @Specialization(guards = {"isNull(left, isNullNode)", "!isForeignObject(right)"}, limit = "1")
        Object doComparison(@SuppressWarnings("unused") TruffleObject left, Object right,
                        @SuppressWarnings("unused") @Cached("IS_NULL.createNode()") Node isNullNode) {
            return comparisonNode.executeWith(PNone.NONE, right);
        }

        @SuppressWarnings("unused")
        @Fallback
        public PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __LT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class LtNode extends ForeignBinaryComparisonNode {
        protected LtNode() {
            super(BinaryComparisonNode.create(__LT__, __GT__, "<"));
        }
    }

    @Builtin(name = __LE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class LeNode extends ForeignBinaryComparisonNode {
        protected LeNode() {
            super(BinaryComparisonNode.create(__LE__, __GE__, "<="));
        }
    }

    @Builtin(name = __GT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class GtNode extends ForeignBinaryComparisonNode {
        protected GtNode() {
            super(BinaryComparisonNode.create(__GT__, __LT__, ">"));
        }
    }

    @Builtin(name = __GE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class GeNode extends ForeignBinaryComparisonNode {
        protected GeNode() {
            super(BinaryComparisonNode.create(__GE__, __LE__, ">="));
        }
    }

    @Builtin(name = __EQ__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class EqNode extends ForeignBinaryComparisonNode {
        protected EqNode() {
            super(BinaryComparisonNode.create(__EQ__, __NE__, "=="));
        }
    }

    @Builtin(name = __ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IterNode extends UnboxNode {

        @Specialization(guards = "isForeignArray(iterable)")
        Object doForeignArray(TruffleObject iterable,
                        @Cached("GET_SIZE.createNode()") Node sizeNode) {
            try {
                Object size = ForeignAccess.sendGetSize(sizeNode, iterable);
                if (size instanceof Integer) {
                    return factory().createForeignArrayIterator(iterable, (int) size);
                }
            } catch (UnsupportedMessageException e) {
                // fall through
            }
            return PNone.NO_VALUE;
        }

        @Specialization(guards = "isBoxed(iterable)")
        Object doBoxedString(TruffleObject iterable) {
            try {
                Object unboxed = unboxLeft(iterable);
                if (unboxed instanceof String) {
                    return factory().createStringIterator((String) unboxed);
                }
            } catch (UnsupportedMessageException e) {
                // fall through
            }
            return PNone.NO_VALUE;
        }

        @Specialization(guards = "isForeignMapping(mapping)")
        Object doForeignMapping(TruffleObject mapping,
                        @Cached("GET_SIZE.createNode()") Node sizeNode,
                        @Cached("KEYS.createNode()") Node keysNode) {
            try {
                Object keysObj = ForeignAccess.sendKeys(keysNode, mapping);
                if (keysObj instanceof TruffleObject) {
                    Object size = ForeignAccess.sendGetSize(sizeNode, (TruffleObject) keysObj);
                    if (size instanceof Integer) {
                        return factory().createForeignArrayIterator((TruffleObject) keysObj, (int) size);
                    }
                }
            } catch (UnsupportedMessageException e) {
                // fall through
            }
            return PNone.NO_VALUE;
        }

        @Fallback
        PNone doGeneric(@SuppressWarnings("unused") Object o) {
            return PNone.NONE;
        }

    }

    @Builtin(name = __NEW__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    abstract static class NewNode extends UnboxNode {

        /**
         * A foreign function call specializes on the length of the passed arguments. Any
         * optimization based on the callee has to happen on the other side.
         */
        @Specialization(guards = {"isForeignObject(callee)", "!isNoValue(callee)", "keywords.length == 0"})
        protected Object doInteropCall(TruffleObject callee, Object[] arguments, @SuppressWarnings("unused") PKeyword[] keywords,
                        @Cached("NEW.createNode()") Node newNode,
                        @Cached("create()") PTypeToForeignNode toForeignNode,
                        @Cached("create()") PForeignToPTypeNode toPTypeNode) {
            try {
                Object[] convertedArgs = new Object[arguments.length];
                for (int i = 0; i < arguments.length; i++) {
                    convertedArgs[i] = toForeignNode.executeConvert(arguments[i]);
                }
                Object res = ForeignAccess.sendNew(newNode, callee, convertedArgs);
                return toPTypeNode.executeConvert(res);
            } catch (ArityException | UnsupportedTypeException | UnsupportedMessageException e) {
                throw raise(PythonErrorType.TypeError, "invalid instantiation of foreign object %s()", callee);
            }
        }

        @Fallback
        protected Object doGeneric(Object callee, @SuppressWarnings("unused") Object arguments, @SuppressWarnings("unused") Object keywords) {
            throw raise(PythonErrorType.TypeError, "invalid instantiation of foreign object %s()", callee);
        }
    }

    @Builtin(name = __CALL__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class CallNode extends UnboxNode {
        public final Object executeWithArgs(TruffleObject callee, Object[] arguments) {
            return this.execute(callee, arguments, PKeyword.EMPTY_KEYWORDS);
        }

        public abstract Object execute(TruffleObject callee, Object[] arguments, PKeyword[] keywords);

        /**
         * A foreign function call specializes on the length of the passed arguments. Any
         * optimization based on the callee has to happen on the other side.
         */
        @Specialization(guards = {"isForeignObject(callee)", "!isNoValue(callee)", "keywords.length == 0"})
        protected Object doInteropCall(TruffleObject callee, Object[] arguments, @SuppressWarnings("unused") PKeyword[] keywords,
                        @Cached("IS_EXECUTABLE.createNode()") Node isExecutableNode,
                        @Cached("EXECUTE.createNode()") Node executeNode,
                        @Cached("NEW.createNode()") Node newNode,
                        @Cached("create()") PTypeToForeignNode toForeignNode,
                        @Cached("create()") PForeignToPTypeNode toPTypeNode) {
            try {
                Object[] convertedArgs = new Object[arguments.length];
                for (int i = 0; i < arguments.length; i++) {
                    convertedArgs[i] = toForeignNode.executeConvert(arguments[i]);
                }
                if (ForeignAccess.sendIsExecutable(isExecutableNode, callee)) {
                    Object res = ForeignAccess.sendExecute(executeNode, callee, convertedArgs);
                    return toPTypeNode.executeConvert(res);
                } else {
                    Object res = ForeignAccess.sendNew(newNode, callee, convertedArgs);
                    return toPTypeNode.executeConvert(res);
                }
            } catch (ArityException | UnsupportedTypeException | UnsupportedMessageException e) {
                throw raise(PythonErrorType.TypeError, "invalid invocation of foreign callable %s()", callee);
            }
        }

        @Fallback
        protected Object doGeneric(Object callee, @SuppressWarnings("unused") Object arguments, @SuppressWarnings("unused") Object keywords) {
            throw raise(PythonErrorType.TypeError, "invalid invocation of foreign callable %s()", callee);
        }

        public static CallNode create() {
            return TruffleObjectBuiltinsFactory.CallNodeFactory.create(null);
        }
    }

    @Builtin(name = __GETITEM__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GetitemNode extends PythonBinaryBuiltinNode {
        @Child AccessForeignItemNodes.GetForeignItemNode getForeignItemNode = AccessForeignItemNodes.GetForeignItemNode.create();

        @Specialization
        Object doit(TruffleObject object, Object key) {
            return getForeignItemNode.execute(object, key);
        }
    }

    @Builtin(name = __GETATTR__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GetattrNode extends PythonBinaryBuiltinNode {
        @Child Node readNode = Message.READ.createNode();
        @Child PForeignToPTypeNode toPythonNode = PForeignToPTypeNode.create();

        @Specialization
        protected Object doIt(TruffleObject object, Object key) {
            try {
                return toPythonNode.executeConvert(ForeignAccess.sendRead(readNode, object, key));
            } catch (UnknownIdentifierException | UnsupportedMessageException e) {
                throw raise(PythonErrorType.AttributeError, "foreign object %s has no attribute %s", object, key);
            }
        }
    }

    @Builtin(name = __SETATTR__, minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class SetattrNode extends UnboxNode {
        @Specialization(guards = "isForeignObject(object)")
        protected PNone doIt(TruffleObject object, Object key, Object value,
                        @Cached("WRITE.createNode()") Node writeNode) {
            try {
                ForeignAccess.sendWrite(writeNode, object, key, value);
            } catch (UnknownIdentifierException | UnsupportedMessageException | UnsupportedTypeException e) {
                throw raise(PythonErrorType.AttributeError, "foreign object %s has no attribute %s", object, key);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = __SETITEM__, minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class SetitemNode extends PythonTernaryBuiltinNode {
        AccessForeignItemNodes.SetForeignItemNode setForeignItemNode = AccessForeignItemNodes.SetForeignItemNode.create();

        @Specialization
        Object doit(TruffleObject object, Object key, Object value) {
            setForeignItemNode.execute(object, key, value);
            return PNone.NONE;
        }
    }

    @Builtin(name = __DELATTR__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class DelattrNode extends UnboxNode {
        @Specialization(guards = "isForeignObject(object)")
        protected PNone doIt(TruffleObject object, Object key,
                        @Cached("REMOVE.createNode()") Node delNode) {
            try {
                ForeignAccess.sendRemove(delNode, object, key);
            } catch (UnknownIdentifierException | UnsupportedMessageException e) {
                throw raise(PythonErrorType.AttributeError, "foreign object %s has no attribute %s", object, key);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = __DELITEM__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class DelitemNode extends PythonBinaryBuiltinNode {
        AccessForeignItemNodes.RemoveForeignItemNode delForeignItemNode = AccessForeignItemNodes.RemoveForeignItemNode.create();

        @Specialization
        PNone doit(TruffleObject object, Object key) {
            delForeignItemNode.execute(object, key);
            return PNone.NONE;
        }
    }

    @Builtin(name = __DIR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class DirNode extends UnboxNode {
        @Specialization(guards = "isForeignObject(object)")
        protected Object doIt(TruffleObject object,
                        @Cached("HAS_KEYS.createNode()") Node hasKeysNode,
                        @Cached("KEYS.createNode()") Node keysNode) {
            if (ForeignAccess.sendHasKeys(hasKeysNode, object)) {
                try {
                    return ForeignAccess.sendKeys(keysNode, object);
                } catch (UnsupportedMessageException e) {
                    throw new IllegalStateException("The object '%s' claims to have keys, but does not support the KEYS message");
                }
            } else {
                return factory().createList();
            }
        }
    }

    @Builtin(name = __INDEX__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IndexNode extends UnboxNode {
        @Specialization(guards = "isForeignObject(object)")
        protected Object doIt(TruffleObject object) {
            if (isBoxed(object)) {
                try {
                    return unboxLeft(object);
                } catch (UnsupportedMessageException e) {
                    throw new IllegalStateException("The object '%s' claims to be boxed, but does not support the UNBOX message");
                }
            }
            throw raiseIndexError();
        }
    }

    @Builtin(name = __STR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class StrNode extends UnboxNode {
        protected final String method = __STR__;
        @Child private LookupAndCallUnaryNode callStrNode;
        @Child protected PythonUnaryBuiltinNode objectStrNode;

        @Specialization(guards = {"isBoxed(object)"})
        protected Object doBoxed(TruffleObject object) {
            try {
                return getCallStrNode().executeObject(unboxLeft(object));
            } catch (UnsupportedMessageException e) {
                throw new IllegalStateException("The object '%s' claims to be boxed, but does not support the UNBOX message");
            }
        }

        @Specialization(guards = {"isForeignArray(object)"})
        protected Object doArray(TruffleObject object,
                        @Cached("create()") CastToListNode asList,
                        @Cached("GET_SIZE.createNode()") Node sizeNode) {
            try {
                Object size = ForeignAccess.sendGetSize(sizeNode, object);
                if (size instanceof Integer) {
                    PForeignArrayIterator iterable = factory().createForeignArrayIterator(object, (int) size);
                    return getCallStrNode().executeObject(asList.executeWith(iterable));
                }
            } catch (PException | UnsupportedMessageException e) {
                // fall through
            }
            return doIt(object);
        }

        @Fallback
        protected Object doIt(Object object) {
            return getObjectStrNode().execute(object);
        }

        private LookupAndCallUnaryNode getCallStrNode() {
            if (callStrNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callStrNode = insert(LookupAndCallUnaryNode.create(method));
            }
            return callStrNode;
        }

        protected PythonUnaryBuiltinNode getObjectStrNode() {
            if (objectStrNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                objectStrNode = insert(ObjectBuiltinsFactory.StrNodeFactory.create());
            }
            return objectStrNode;
        }
    }

    @Builtin(name = __REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReprNode extends StrNode {
        protected final String method = __REPR__;

        @Override
        protected PythonUnaryBuiltinNode getObjectStrNode() {
            if (objectStrNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                objectStrNode = insert(ObjectBuiltinsFactory.ReprNodeFactory.create());
            }
            return objectStrNode;
        }
    }
}
