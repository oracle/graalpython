/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.AttributeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.MemoryError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.objects.str.StringUtils.simpleTruffleStringFormatUncached;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___BASES__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___BASES__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___AND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___BOOL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CALL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CONTAINS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___DELATTR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___DELITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___DIR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___DIVMOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___FLOORDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GETATTR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___HASH__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INDEX__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INSTANCECHECK__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___LEN__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___LE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___LT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___MUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NEW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NEXT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___OR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RAND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RDIVMOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RFLOORDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ROR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RSUB__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RTRUEDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RXOR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SETATTR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___STR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SUB__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___TRUEDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___XOR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___INSTANCECHECK__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___LEN__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___NEXT__;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.iterator.PForeignArrayIterator;
import com.oracle.graal.python.builtins.objects.object.ObjectNodes;
import com.oracle.graal.python.builtins.objects.str.StringBuiltins;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.lib.PyObjectRichCompareBool;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.BitAndNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.BitOrNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.BitXorNode;
import com.oracle.graal.python.nodes.expression.BinaryComparisonNode;
import com.oracle.graal.python.nodes.expression.BinaryOpNode;
import com.oracle.graal.python.nodes.expression.CastToListExpressionNode.CastToListNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.interop.PForeignToPTypeNode;
import com.oracle.graal.python.nodes.object.IsForeignObjectNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.StopIterationException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.ForeignObject)
public final class ForeignObjectBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ForeignObjectBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___BOOL__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class BoolNode extends PythonUnaryBuiltinNode {
        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()")
        static boolean bool(Object receiver,
                        @CachedLibrary("receiver") InteropLibrary lib,
                        @Cached GilNode gil) {
            gil.release(true);
            try {
                if (lib.isBoolean(receiver)) {
                    return lib.asBoolean(receiver);
                }
                if (lib.fitsInLong(receiver)) {
                    return lib.asLong(receiver) != 0;
                }
                if (lib.fitsInBigInteger(receiver)) {
                    return !isBigIntegerZero(lib.asBigInteger(receiver));
                }
                if (lib.fitsInDouble(receiver)) {
                    return lib.asDouble(receiver) != 0.0;
                }
                if (lib.hasArrayElements(receiver)) {
                    return lib.getArraySize(receiver) != 0;
                }
                if (lib.hasHashEntries(receiver)) {
                    return lib.getHashSize(receiver) != 0;
                }
                if (lib.isString(receiver)) {
                    return !lib.asTruffleString(receiver).isEmpty();
                }
                return !lib.isNull(receiver);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            } finally {
                gil.acquire();
            }
        }

        @TruffleBoundary
        static boolean isBigIntegerZero(BigInteger number) {
            return number.compareTo(BigInteger.ZERO) == 0;
        }
    }

    static Object[] unpackForeignArray(Object left, InteropLibrary lib, PForeignToPTypeNode convert) {
        try {
            long sizeObj = lib.getArraySize(left);
            if (sizeObj < Integer.MAX_VALUE) {
                int size = (int) sizeObj;
                Object[] data = new Object[size];

                // read data
                for (int i = 0; i < size; i++) {
                    data[i] = convert.executeConvert(lib.readArrayElement(left, i));
                }

                return data;
            }
        } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException("object does not unpack to array as it claims to");
        }
        return null;
    }

    @Builtin(name = J___LEN__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class LenNode extends PythonUnaryBuiltinNode {
        @Specialization
        static long len(Object self,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached GilNode gil,
                        @Cached PRaiseNode.Lazy raiseNode) {
            gil.release(true);
            try {
                if (lib.hasArrayElements(self)) {
                    return lib.getArraySize(self);
                } else if (lib.isIterator(self) || lib.hasIterator(self)) {
                    return 0; // a value signifying it has a length, but it's unknown
                } else if (lib.hasHashEntries(self)) {
                    return lib.getHashSize(self);
                }
            } catch (UnsupportedMessageException e) {
                // fall through
            } finally {
                gil.acquire();
            }
            throw raiseNode.get(inliningTarget).raise(AttributeError, ErrorMessages.FOREIGN_OBJ_HAS_NO_ATTR_S, T___LEN__);
        }
    }

    abstract static class ForeignBinaryNode extends PythonBinaryBuiltinNode {
        @Child private BinaryOpNode op;
        protected final boolean reverse;

        protected ForeignBinaryNode(BinaryOpNode op, boolean reverse) {
            this.op = op;
            this.reverse = reverse;
        }

        @Specialization(guards = {"lib.isBoolean(left)"})
        Object doComparisonBool(VirtualFrame frame, Object left, Object right,
                        @Shared @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Shared @Cached GilNode gil) {
            boolean leftBoolean;
            gil.release(true);
            try {
                leftBoolean = lib.asBoolean(left);
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException("object does not unpack to boolean as it claims to");
            } finally {
                gil.acquire();
            }
            if (!reverse) {
                return op.executeObject(frame, leftBoolean, right);
            } else {
                return op.executeObject(frame, right, leftBoolean);
            }

        }

        @Specialization(guards = "lib.fitsInLong(left)")
        Object doComparisonLong(VirtualFrame frame, Object left, Object right,
                        @Shared @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Shared @Cached GilNode gil) {
            assert !lib.isBoolean(left);
            long leftLong;
            gil.release(true);
            try {
                leftLong = lib.asLong(left);
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException("object does not unpack to long as it claims to");
            } finally {
                gil.acquire();
            }
            if (!reverse) {
                return op.executeObject(frame, leftLong, right);
            } else {
                return op.executeObject(frame, right, leftLong);
            }
        }

        @Specialization(guards = {"!lib.fitsInLong(left)", "lib.fitsInBigInteger(left)"})
        Object doComparisonBigInt(VirtualFrame frame, Object left, Object right,
                        @Shared @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Shared @Cached GilNode gil,
                        @Cached.Exclusive @Cached PythonObjectFactory factory) {
            assert !lib.isBoolean(left);
            BigInteger leftBigInteger;
            PInt leftInt;
            gil.release(true);
            try {
                leftBigInteger = lib.asBigInteger(left);
                leftInt = factory.createInt(leftBigInteger);
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException("object does not unpack to BigInteger as it claims to");
            } finally {
                gil.acquire();
            }
            if (!reverse) {
                return op.executeObject(frame, leftInt, right);
            } else {
                return op.executeObject(frame, right, leftInt);
            }
        }

        @Specialization(guards = {"!lib.fitsInLong(left)", "!lib.fitsInBigInteger(left)", "lib.fitsInDouble(left)"})
        Object doComparisonDouble(VirtualFrame frame, Object left, Object right,
                        @Shared @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Shared @Cached GilNode gil) {
            assert !lib.isBoolean(left);
            double leftDouble;
            gil.release(true);
            try {
                leftDouble = lib.asDouble(left);
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException("object does not unpack to double as it claims to");
            } finally {
                gil.acquire();
            }
            if (!reverse) {
                return op.executeObject(frame, leftDouble, right);
            } else {
                return op.executeObject(frame, right, leftDouble);
            }
        }

        @Specialization(guards = {"!lib.fitsInLong(left)", "!lib.fitsInBigInteger(left)", "!lib.fitsInDouble(left)", "lib.isString(left)"})
        Object doComparisonString(VirtualFrame frame, Object left, Object right,
                        @Shared @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached TruffleString.SwitchEncodingNode switchEncodingNode,
                        @Shared @Cached GilNode gil) {
            assert !lib.isBoolean(left);
            TruffleString leftString;
            gil.release(true);
            try {
                leftString = switchEncodingNode.execute(lib.asTruffleString(left), TS_ENCODING);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            } finally {
                gil.acquire();
            }
            if (!reverse) {
                return op.executeObject(frame, leftString, right);
            } else {
                return op.executeObject(frame, right, leftString);
            }
        }

        @SuppressWarnings("unused")
        @Fallback
        public static PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = J___ADD__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class AddNode extends ForeignBinaryNode {
        AddNode() {
            super(BinaryArithmetic.Add.create(), false);
        }

        AddNode(boolean reverse) {
            super(BinaryArithmetic.Add.create(), reverse);
        }

        @Specialization(insertBefore = "doGeneric", guards = {"lib.hasArrayElements(left)", "lib.hasArrayElements(right)"})
        static Object doForeignArray(Object left, Object right,
                        @Cached PythonObjectFactory factory,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached PForeignToPTypeNode convert,
                        @Cached GilNode gil) {
            gil.release(true);
            try {
                Object[] unpackedLeft = unpackForeignArray(left, lib, convert);
                Object[] unpackedRight = unpackForeignArray(right, lib, convert);
                if (unpackedLeft != null && unpackedRight != null) {
                    Object[] result = Arrays.copyOf(unpackedLeft, unpackedLeft.length + unpackedRight.length);
                    System.arraycopy(unpackedRight, 0, result, unpackedLeft.length, unpackedRight.length);
                    return factory.createList(result);
                }
            } finally {
                gil.acquire();
            }
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = J___RADD__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class RAddNode extends AddNode {
        RAddNode() {
            super(true);
        }
    }

    @Builtin(name = J___MUL__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class MulNode extends ForeignBinaryNode {
        MulNode() {
            super(BinaryArithmetic.Mul.create(), false);
        }

        @Specialization(insertBefore = "doComparisonBool", guards = {"!lib.isBoolean(left)", "!lib.isNumber(left)", "!lib.isString(left)", "lib.hasArrayElements(left)", "lib.fitsInLong(right)"})
        static Object doForeignArray(Object left, Object right,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PRaiseNode raise,
                        @Shared @Cached PythonObjectFactory factory,
                        @Shared @Cached PForeignToPTypeNode convert,
                        @Shared @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached CastToJavaIntExactNode cast,
                        @Shared @Cached GilNode gil) {
            gil.release(true);
            try {
                long rightLong;
                try {
                    rightLong = lib.asLong(right);
                } catch (UnsupportedMessageException e) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new IllegalStateException("object does not unpack to index-sized int as it claims to");
                }
                return doMulArray(left, cast.execute(inliningTarget, rightLong), raise, factory, convert, lib);
            } finally {
                gil.acquire();
            }
        }

        @Specialization(insertBefore = "doComparisonBool", guards = {"!lib.isBoolean(left)", "!lib.isNumber(left)", "!lib.isString(left)", "lib.hasArrayElements(left)", "lib.isBoolean(right)"})
        static Object doForeignArrayForeignBoolean(Object left, Object right,
                        @Shared @Cached PRaiseNode raise,
                        @Shared @Cached PythonObjectFactory factory,
                        @Shared @Cached PForeignToPTypeNode convert,
                        @Shared @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Shared @Cached GilNode gil) {
            gil.release(true);
            try {
                boolean rightBoolean;
                try {
                    rightBoolean = lib.asBoolean(right);
                } catch (UnsupportedMessageException e) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new IllegalStateException("object does not unpack to boolean (to be used as index) as it claims to");
                }
                return doMulArray(left, rightBoolean ? 1 : 0, raise, factory, convert, lib);
            } finally {
                gil.acquire();
            }
        }

        private static PythonAbstractObject doMulArray(Object left, int rightInt, PRaiseNode raise, PythonObjectFactory factory, PForeignToPTypeNode convert, InteropLibrary lib) {
            try {
                Object[] unpackForeignArray = unpackForeignArray(left, lib, convert);
                if (unpackForeignArray != null) {
                    if (rightInt < 0) {
                        return factory.createList();
                    }
                    Object[] repeatedData = new Object[Math.multiplyExact(unpackForeignArray.length, rightInt)];

                    for (int i = 0; i < rightInt; i++) {
                        System.arraycopy(unpackForeignArray, 0, repeatedData, i * unpackForeignArray.length, unpackForeignArray.length);
                    }

                    return factory.createList(repeatedData);
                }
                return PNotImplemented.NOT_IMPLEMENTED;
            } catch (ArithmeticException e) {
                throw raise.raise(MemoryError);
            }
        }
    }

    @Builtin(name = J___RMUL__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class RMulNode extends MulNode {
    }

    @Builtin(name = J___SUB__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class SubNode extends ForeignBinaryNode {
        SubNode() {
            super(BinaryArithmetic.Sub.create(), false);
        }
    }

    @Builtin(name = J___RSUB__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class RSubNode extends ForeignBinaryNode {
        RSubNode() {
            super(BinaryArithmetic.Sub.create(), true);
        }
    }

    @Builtin(name = J___TRUEDIV__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class TrueDivNode extends ForeignBinaryNode {
        TrueDivNode() {
            super(BinaryArithmetic.TrueDiv.create(), false);
        }
    }

    @Builtin(name = J___RTRUEDIV__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class RTrueDivNode extends ForeignBinaryNode {
        RTrueDivNode() {
            super(BinaryArithmetic.TrueDiv.create(), true);
        }
    }

    @Builtin(name = J___FLOORDIV__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class FloorDivNode extends ForeignBinaryNode {
        FloorDivNode() {
            super(BinaryArithmetic.FloorDiv.create(), false);
        }
    }

    @Builtin(name = J___RFLOORDIV__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class RFloorDivNode extends ForeignBinaryNode {
        RFloorDivNode() {
            super(BinaryArithmetic.FloorDiv.create(), true);
        }
    }

    @Builtin(name = J___DIVMOD__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class DivModNode extends ForeignBinaryNode {
        DivModNode() {
            super(BinaryArithmetic.DivMod.create(), false);
        }
    }

    @Builtin(name = J___RDIVMOD__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class RDivModNode extends ForeignBinaryNode {
        RDivModNode() {
            super(BinaryArithmetic.DivMod.create(), true);
        }
    }

    public abstract static class ForeignBinaryComparisonNode extends PythonBinaryBuiltinNode {
        @Child private BinaryComparisonNode comparisonNode;

        protected ForeignBinaryComparisonNode(BinaryComparisonNode op) {
            this.comparisonNode = op;
        }

        @Specialization(guards = {"lib.isBoolean(left)"})
        Object doComparisonBool(VirtualFrame frame, Object left, Object right,
                        @Shared @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Shared @Cached GilNode gil) {
            boolean leftBoolean;
            gil.release(true);
            try {
                leftBoolean = lib.asBoolean(left);
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException("object does not unpack to boolean for comparison as it claims to");
            } finally {
                gil.acquire();
            }
            return comparisonNode.executeObject(frame, leftBoolean, right);
        }

        @Specialization(guards = {"lib.fitsInLong(left)"})
        Object doComparisonLong(VirtualFrame frame, Object left, Object right,
                        @Shared @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Shared @Cached GilNode gil) {
            long leftLong;
            gil.release(true);
            try {
                leftLong = lib.asLong(left);
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException("object does not unpack to long for comparison as it claims to");
            } finally {
                gil.acquire();
            }
            return comparisonNode.executeObject(frame, leftLong, right);
        }

        @Specialization(guards = {"!lib.fitsInLong(left)", "lib.fitsInBigInteger(left)"})
        Object doComparisonBigInt(VirtualFrame frame, Object left, Object right,
                        @Shared @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Shared @Cached GilNode gil,
                        @Cached PythonObjectFactory factory) {
            BigInteger leftBigInteger;
            PInt leftInt;
            gil.release(true);
            try {
                leftBigInteger = lib.asBigInteger(left);
                leftInt = factory.createInt(leftBigInteger);
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException("object does not unpack to BigInteger as it claims to");
            } finally {
                gil.acquire();
            }
            return comparisonNode.executeObject(frame, leftInt, right);
        }

        @Specialization(guards = {"lib.fitsInDouble(left)"})
        Object doComparisonDouble(VirtualFrame frame, Object left, Object right,
                        @Shared @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Shared @Cached GilNode gil) {
            double leftDouble;
            gil.release(true);
            try {
                leftDouble = lib.asDouble(left);
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException("object does not unpack to double for comparison as it claims to");
            } finally {
                gil.acquire();
            }
            return comparisonNode.executeObject(frame, leftDouble, right);
        }

        @Specialization(guards = "lib.isNull(left)")
        Object doComparison(VirtualFrame frame, @SuppressWarnings("unused") Object left, Object right,
                        @SuppressWarnings("unused") @Shared @CachedLibrary(limit = "3") InteropLibrary lib) {
            return comparisonNode.executeObject(frame, PNone.NONE, right);
        }

        @Specialization(guards = "lib.isString(left)")
        Object doComparisonString(VirtualFrame frame, @SuppressWarnings("unused") Object left, Object right,
                        @SuppressWarnings("unused") @Shared @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached GilNode gil,
                        @Cached TruffleString.SwitchEncodingNode switchEncodingNode) {
            TruffleString leftString;
            gil.release(true);
            try {
                leftString = switchEncodingNode.execute(lib.asTruffleString(left), TS_ENCODING);
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException("object does not unpack to string for comparison as it claims to");
            } finally {
                gil.acquire();
            }
            return comparisonNode.executeObject(frame, leftString, right);
        }

        @SuppressWarnings("unused")
        @Fallback
        public static PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = J___LT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class LtNode extends ForeignBinaryComparisonNode {
        protected LtNode() {
            super(BinaryComparisonNode.LtNode.create());
        }
    }

    @Builtin(name = J___LE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class LeNode extends ForeignBinaryComparisonNode {
        protected LeNode() {
            super(BinaryComparisonNode.LeNode.create());
        }
    }

    @Builtin(name = J___GT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class GtNode extends ForeignBinaryComparisonNode {
        protected GtNode() {
            super(BinaryComparisonNode.GtNode.create());
        }
    }

    @Builtin(name = J___GE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class GeNode extends ForeignBinaryComparisonNode {
        protected GeNode() {
            super(BinaryComparisonNode.GeNode.create());
        }
    }

    @Builtin(name = J___EQ__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class EqNode extends ForeignBinaryComparisonNode {
        protected EqNode() {
            super(BinaryComparisonNode.EqNode.create());
        }
    }

    @Builtin(name = J___HASH__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class HashNode extends PythonUnaryBuiltinNode {
        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()")
        static int hash(Object self,
                        @CachedLibrary("self") InteropLibrary library) {
            if (library.hasIdentity(self)) {
                try {
                    return library.identityHashCode(self);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
            }
            return hashCodeBoundary(self);
        }

        @TruffleBoundary
        private static int hashCodeBoundary(Object self) {
            return self.hashCode();
        }
    }

    @Builtin(name = J___CONTAINS__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class ContainsNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object contains(VirtualFrame frame, Object self, Object arg,
                        // accesses both self and iterator
                        @CachedLibrary(limit = "3") InteropLibrary library,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectRichCompareBool.EqNode eqNode,
                        @Cached TruffleString.SwitchEncodingNode switchEncodingNode,
                        @Cached StringBuiltins.ContainsNode containsNode,
                        @Cached PForeignToPTypeNode convertNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            try {
                if (library.isString(self)) {
                    TruffleString selfStr = switchEncodingNode.execute(library.asTruffleString(self), TS_ENCODING);
                    return containsNode.execute(frame, selfStr, arg);
                }
                if (library.hasArrayElements(self)) {
                    for (int i = 0; i < library.getArraySize(self); i++) {
                        if (library.isArrayElementReadable(self, i)) {
                            Object element = convertNode.executeConvert(library.readArrayElement(self, i));
                            if (eqNode.compare(frame, inliningTarget, arg, element)) {
                                return true;
                            }
                        }
                    }
                    return false;
                }
                Object iterator = null;
                if (library.isIterator(self)) {
                    iterator = self;
                } else if (library.hasHashEntries(self)) {
                    iterator = library.getHashKeysIterator(self);
                } else if (library.hasIterator(self)) {
                    iterator = library.getIterator(self);
                }
                if (iterator != null) {
                    try {
                        while (library.hasIteratorNextElement(iterator)) {
                            Object next = convertNode.executeConvert(library.getIteratorNextElement(iterator));
                            if (eqNode.compare(frame, inliningTarget, arg, next)) {
                                return true;
                            }
                        }
                    } catch (StopIterationException e) {
                        // fallthrough
                    }
                    return false;
                }
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.FOREIGN_OBJ_ISNT_ITERABLE);
            } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
    }

    @Builtin(name = J___ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {

        @Specialization(limit = "3")
        static Object doGeneric(Object object,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode raiseNode,
                        @CachedLibrary("object") InteropLibrary lib,
                        @Cached PForeignToPTypeNode convertNode,
                        @Cached TruffleString.SwitchEncodingNode switchEncodingNode,
                        @Cached GilNode gil) {
            gil.release(true);
            try {
                if (lib.isIterator(object)) {
                    return convertNode.executeConvert(object);
                } else if (lib.hasIterator(object)) {
                    return convertNode.executeConvert(lib.getIterator(object));
                } else if (lib.hasArrayElements(object)) {
                    long size = lib.getArraySize(object);
                    if (size < Integer.MAX_VALUE) {
                        return factory.createForeignArrayIterator(object);
                    }
                    throw raiseNode.raise(TypeError, ErrorMessages.FOREIGN_OBJ_ISNT_ITERABLE);
                } else if (lib.isString(object)) {
                    return factory.createStringIterator(switchEncodingNode.execute(lib.asTruffleString(object), TS_ENCODING));
                } else if (lib.hasHashEntries(object)) {
                    // just like dict.__iter__, we take the keys by default
                    return convertNode.executeConvert(lib.getHashKeysIterator(object));
                }
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            } finally {
                gil.acquire();
            }
            throw raiseNode.raise(TypeError, ErrorMessages.FOREIGN_OBJ_ISNT_ITERABLE);
        }
    }

    @Builtin(name = J___NEXT__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class NextNode extends PythonUnaryBuiltinNode {

        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()")
        static Object doForeignArray(Object iterator,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedConditionProfile notIterator,
                        @Cached PRaiseNode raiseNode,
                        @CachedLibrary("iterator") InteropLibrary lib,
                        @Cached PForeignToPTypeNode convertNode,
                        @Cached GilNode gil) {
            if (notIterator.profile(inliningTarget, lib.isIterator(iterator))) {
                gil.release(true);
                try {
                    return convertNode.executeConvert(lib.getIteratorNextElement(iterator));
                } catch (StopIterationException e) {
                    throw raiseNode.raiseStopIteration();
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere("iterator claimed to be iterator but wasn't");
                } finally {
                    gil.acquire();
                }
            } else {
                throw raiseNode.raise(AttributeError, ErrorMessages.FOREIGN_OBJ_HAS_NO_ATTR_S, T___NEXT__);
            }
        }
    }

    @Builtin(name = J___NEW__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    abstract static class NewNode extends PythonBuiltinNode {
        /**
         * A foreign function call specializes on the length of the passed arguments. Any
         * optimization based on the callee has to happen on the other side.a
         */
        @Specialization(guards = {"isForeignObjectNode.execute(inliningTarget, callee)", "!isNoValue(callee)", "keywords.length == 0"}, limit = "1")
        static Object doInteropCall(Object callee, Object[] arguments, @SuppressWarnings("unused") PKeyword[] keywords,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Cached IsForeignObjectNode isForeignObjectNode,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached PForeignToPTypeNode toPTypeNode,
                        @Cached GilNode gil,
                        @Cached PRaiseNode.Lazy raiseNode) {
            gil.release(true);
            try {
                Object res = lib.instantiate(callee, arguments);
                return toPTypeNode.executeConvert(res);
            } catch (ArityException | UnsupportedTypeException | UnsupportedMessageException e) {
                throw raiseNode.get(inliningTarget).raise(PythonErrorType.TypeError, ErrorMessages.INVALID_INSTANTIATION_OF_FOREIGN_OBJ);
            } finally {
                gil.acquire();
            }
        }

        @Fallback
        @SuppressWarnings("unused")
        static Object doGeneric(Object callee, Object arguments, Object keywords,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(PythonErrorType.TypeError, ErrorMessages.INVALID_INSTANTIATION_OF_FOREIGN_OBJ);
        }
    }

    @Builtin(name = J___CALL__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class CallNode extends PythonBuiltinNode {
        public final Object executeWithArgs(VirtualFrame frame, Object callee, Object[] arguments) {
            return execute(frame, callee, arguments, PKeyword.EMPTY_KEYWORDS);
        }

        public abstract Object execute(VirtualFrame frame, Object callee, Object[] arguments, PKeyword[] keywords);

        /**
         * A foreign function call specializes on the length of the passed arguments. Any
         * optimization based on the callee has to happen on the other side.
         */
        @Specialization(guards = {"isForeignObjectNode.execute(inliningTarget, callee)", "!isNoValue(callee)", "keywords.length == 0"}, limit = "1")
        static Object doInteropCall(VirtualFrame frame, Object callee, Object[] arguments, @SuppressWarnings("unused") PKeyword[] keywords,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @SuppressWarnings("unused") @Cached IsForeignObjectNode isForeignObjectNode,
                        @CachedLibrary(limit = "4") InteropLibrary lib,
                        @Cached PForeignToPTypeNode toPTypeNode,
                        @Cached GilNode gil,
                        @Cached PRaiseNode.Lazy raiseNode) {
            PythonLanguage language = PythonLanguage.get(inliningTarget);
            PythonContext context = PythonContext.get(inliningTarget);
            try {
                Object state = IndirectCallContext.enter(frame, language, context, indirectCallData);
                gil.release(true);
                try {
                    if (lib.isExecutable(callee)) {
                        return toPTypeNode.executeConvert(lib.execute(callee, arguments));
                    } else {
                        return toPTypeNode.executeConvert(lib.instantiate(callee, arguments));
                    }
                } finally {
                    gil.acquire();
                    IndirectCallContext.exit(frame, language, context, state);
                }
            } catch (ArityException | UnsupportedTypeException | UnsupportedMessageException e) {
                throw raiseNode.get(inliningTarget).raise(PythonErrorType.TypeError, ErrorMessages.INVALID_INSTANTIATION_OF_FOREIGN_OBJ);
            }
        }

        @Fallback
        @SuppressWarnings("unused")
        static Object doGeneric(Object callee, Object arguments, Object keywords,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(PythonErrorType.TypeError, ErrorMessages.INVALID_INSTANTIATION_OF_FOREIGN_OBJ);
        }

        @NeverDefault
        public static CallNode create() {
            return ForeignObjectBuiltinsFactory.CallNodeFactory.create(null);
        }
    }

    @Builtin(name = J___GETITEM__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GetitemNode extends PythonBinaryBuiltinNode {
        @Child private AccessForeignItemNodes.GetForeignItemNode getForeignItemNode = AccessForeignItemNodes.GetForeignItemNode.create();

        @Specialization
        Object doit(VirtualFrame frame, Object object, Object key) {
            return getForeignItemNode.execute(frame, object, key);
        }
    }

    @Builtin(name = J___GETATTR__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class GetattrNode extends PythonBinaryBuiltinNode {

        @Specialization
        static Object doIt(Object object, Object memberObj,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "getAttributeAccessInlineCacheMaxDepth()") InteropLibrary read,
                        @Cached CastToJavaStringNode castToString,
                        @Cached GilNode gil,
                        @Cached PForeignToPTypeNode toPythonNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            gil.release(true);
            try {
                String member = castToString.execute(memberObj);
                if (read.isMemberReadable(object, member)) {
                    return toPythonNode.executeConvert(read.readMember(object, member));
                }
            } catch (CannotCastException e) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, ErrorMessages.ATTR_NAME_MUST_BE_STRING, memberObj);
            } catch (UnknownIdentifierException | UnsupportedMessageException ignore) {
            } finally {
                gil.acquire();
            }
            throw raiseNode.get(inliningTarget).raise(PythonErrorType.AttributeError, ErrorMessages.FOREIGN_OBJ_HAS_NO_ATTR_S, memberObj);
        }
    }

    @ImportStatic(PGuards.class)
    @Builtin(name = J___SETATTR__, minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class SetattrNode extends PythonTernaryBuiltinNode {
        @Specialization
        static PNone doIt(Object object, Object key, Object value,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached CastToJavaStringNode castToString,
                        @Cached GilNode gil,
                        @Cached PRaiseNode.Lazy raiseNode) {
            gil.release(true);
            try {
                lib.writeMember(object, castToString.execute(key), value);
            } catch (CannotCastException e) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, ErrorMessages.ATTR_NAME_MUST_BE_STRING, key);
            } catch (UnknownIdentifierException | UnsupportedMessageException | UnsupportedTypeException e) {
                throw raiseNode.get(inliningTarget).raise(PythonErrorType.AttributeError, ErrorMessages.FOREIGN_OBJ_HAS_NO_ATTR_S, key);
            } finally {
                gil.acquire();
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = J___SETITEM__, minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class SetitemNode extends PythonTernaryBuiltinNode {
        @Child private AccessForeignItemNodes.SetForeignItemNode setForeignItemNode = AccessForeignItemNodes.SetForeignItemNode.create();

        @Specialization
        Object doit(VirtualFrame frame, Object object, Object key, Object value) {
            setForeignItemNode.execute(frame, object, key, value);
            return PNone.NONE;
        }
    }

    @Builtin(name = J___DELATTR__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class DelattrNode extends PythonBinaryBuiltinNode {
        @Specialization
        static PNone doIt(Object object, Object key,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached CastToJavaStringNode castToString,
                        @Cached GilNode gil,
                        @Cached PRaiseNode.Lazy raiseNode) {
            gil.release(true);
            try {
                lib.removeMember(object, castToString.execute(key));
            } catch (CannotCastException e) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, ErrorMessages.ATTR_NAME_MUST_BE_STRING, key);
            } catch (UnknownIdentifierException | UnsupportedMessageException e) {
                throw raiseNode.get(inliningTarget).raise(PythonErrorType.AttributeError, ErrorMessages.FOREIGN_OBJ_HAS_NO_ATTR_S, key);
            } finally {
                gil.acquire();
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = J___DELITEM__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class DelitemNode extends PythonBinaryBuiltinNode {
        @Child private AccessForeignItemNodes.RemoveForeignItemNode delForeignItemNode = AccessForeignItemNodes.RemoveForeignItemNode.create();

        @Specialization
        PNone doit(VirtualFrame frame, Object object, Object key) {
            delForeignItemNode.execute(frame, object, key);
            return PNone.NONE;
        }
    }

    @Builtin(name = J___DIR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class DirNode extends PythonUnaryBuiltinNode {
        @Specialization
        protected Object doIt(Object object,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached GilNode gil,
                        @Cached PythonObjectFactory.Lazy factory) {
            if (lib.hasMembers(object)) {
                gil.release(true);
                try {
                    return lib.getMembers(object);
                } catch (UnsupportedMessageException e) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new IllegalStateException("foreign object claims to have members, but does not return them");
                } finally {
                    gil.acquire();
                }
            } else {
                return factory.get(inliningTarget).createList();
            }
        }
    }

    @Builtin(name = J___INDEX__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IndexNode extends PythonUnaryBuiltinNode {
        @Specialization(limit = "3")
        protected static Object doIt(Object object,
                        @Cached PRaiseNode raiseNode,
                        @CachedLibrary("object") InteropLibrary lib,
                        @Cached GilNode gil,
                        @Cached PythonObjectFactory factory) {
            gil.release(true);
            try {
                if (lib.isBoolean(object)) {
                    try {
                        return PInt.intValue(lib.asBoolean(object));
                    } catch (UnsupportedMessageException e) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        throw new IllegalStateException("foreign value claims to be a boolean but isn't");
                    }
                }
                if (lib.fitsInInt(object)) {
                    try {
                        return lib.asInt(object);
                    } catch (UnsupportedMessageException e) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        throw new IllegalStateException("foreign value claims it fits into index-sized int, but doesn't");
                    }
                }
                if (lib.fitsInLong(object)) {
                    try {
                        return lib.asLong(object);
                    } catch (UnsupportedMessageException e) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        throw new IllegalStateException("foreign value claims it fits into index-sized long, but doesn't");
                    }
                }
                if (lib.fitsInBigInteger(object)) {
                    try {
                        var big = lib.asBigInteger(object);
                        return factory.createInt(big);
                    } catch (UnsupportedMessageException e) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        throw new IllegalStateException("foreign value claims to be a big integer but isn't");
                    }
                }
                throw raiseNode.raiseIntegerInterpretationError(object);
            } finally {
                gil.acquire();
            }
        }
    }

    @Builtin(name = J___STR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class StrNode extends PythonUnaryBuiltinNode {
        @Child private LookupAndCallUnaryNode callStrNode;
        @Child private CastToListNode castToListNode;
        @Child private TruffleString.SwitchEncodingNode switchEncodingNode;

        @Specialization
        Object str(VirtualFrame frame, Object object,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached GilNode gil,
                        @Cached InlinedBranchProfile isNull,
                        @Cached InlinedBranchProfile isBoolean,
                        @Cached InlinedBranchProfile isString,
                        @Cached InlinedBranchProfile isLong,
                        @Cached InlinedBranchProfile isDouble,
                        @Cached InlinedBranchProfile isArray,
                        @Cached InlinedBranchProfile defaultCase,
                        @Cached PythonObjectFactory.Lazy factory) {
            try {
                if (lib.isNull(object)) {
                    isNull.enter(inliningTarget);
                    return getCallStrNode().executeObject(frame, PNone.NONE);
                } else if (lib.isBoolean(object)) {
                    isBoolean.enter(inliningTarget);
                    boolean value;
                    gil.release(true);
                    try {
                        value = lib.asBoolean(object);
                    } finally {
                        gil.acquire();
                    }
                    return getCallStrNode().executeObject(frame, value);
                } else if (lib.isString(object)) {
                    isString.enter(inliningTarget);
                    TruffleString value;
                    gil.release(true);
                    try {
                        value = lib.asTruffleString(object);
                    } finally {
                        gil.acquire();
                    }
                    return getCallStrNode().executeObject(frame, getSwitchEncodingNode().execute(value, TS_ENCODING));
                } else if (lib.fitsInLong(object)) {
                    isLong.enter(inliningTarget);
                    long value;
                    gil.release(true);
                    try {
                        value = lib.asLong(object);
                    } finally {
                        gil.acquire();
                    }
                    return getCallStrNode().executeObject(frame, value);
                } else if (lib.fitsInDouble(object)) {
                    isDouble.enter(inliningTarget);
                    double value;
                    gil.release(true);
                    try {
                        value = lib.asDouble(object);
                    } finally {
                        gil.acquire();
                    }
                    return getCallStrNode().executeObject(frame, value);
                } else if (lib.hasArrayElements(object)) {
                    isArray.enter(inliningTarget);
                    long size;
                    gil.release(true);
                    try {
                        size = lib.getArraySize(object);
                    } finally {
                        gil.acquire();
                    }
                    if (size <= Integer.MAX_VALUE && size >= 0) {
                        PForeignArrayIterator iterable = factory.get(inliningTarget).createForeignArrayIterator(object);
                        return getCallStrNode().executeObject(frame, getCastToListNode().execute(frame, iterable));
                    }
                }
            } catch (UnsupportedMessageException e) {
                // Fall back to the generic impl
            }
            defaultCase.enter(inliningTarget);
            return defaultConversion(frame, lib, object);
        }

        private LookupAndCallUnaryNode getCallStrNode() {
            if (callStrNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callStrNode = insert(LookupAndCallUnaryNode.create(SpecialMethodSlot.Str));
            }
            return callStrNode;
        }

        private CastToListNode getCastToListNode() {
            if (castToListNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castToListNode = insert(CastToListNode.create());
            }
            return castToListNode;
        }

        protected TruffleString.SwitchEncodingNode getSwitchEncodingNode() {
            if (switchEncodingNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                switchEncodingNode = insert(TruffleString.SwitchEncodingNode.create());
            }
            return switchEncodingNode;
        }

        protected TruffleString defaultConversion(@SuppressWarnings("unused") VirtualFrame frame, InteropLibrary lib, Object object) {
            try {
                return getSwitchEncodingNode().execute(lib.asTruffleString(lib.toDisplayString(object)), TS_ENCODING);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere("toDisplayString result not convertible to String");
            }
        }
    }

    @Builtin(name = J___REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReprNode extends StrNode {
        @Child private ObjectNodes.DefaultObjectReprNode defaultReprNode;

        @Override
        protected TruffleString defaultConversion(VirtualFrame frame, InteropLibrary lib, Object object) {
            try {
                if (getContext().getEnv().isHostObject(object)) {
                    boolean isMetaObject = lib.isMetaObject(object);
                    Object metaObject = null;
                    if (isMetaObject) {
                        metaObject = object;
                    } else if (lib.hasMetaObject(object)) {
                        metaObject = lib.getMetaObject(object);
                    }
                    if (metaObject != null) {
                        TruffleString displayName = getSwitchEncodingNode().execute(lib.asTruffleString(lib.toDisplayString(metaObject)), TS_ENCODING);
                        return simpleTruffleStringFormatUncached("<%s[%s] at 0x%s>", isMetaObject ? "JavaClass" : "JavaObject", displayName,
                                        PythonAbstractObject.systemHashCodeAsHexString(object));
                    }
                }
            } catch (UnsupportedMessageException e) {
                // fallthrough to default
            }
            if (defaultReprNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                defaultReprNode = insert(ObjectNodes.DefaultObjectReprNode.create());
            }
            return defaultReprNode.executeCached(frame, object);
        }
    }

    @Builtin(name = J___BASES__, minNumOfPositionalArgs = 1, isGetter = true, isSetter = false)
    @GenerateNodeFactory
    @ImportStatic(PGuards.class)
    abstract static class BasesNode extends PythonUnaryBuiltinNode {
        @Specialization(limit = "3")
        static Object getBases(Object self,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary("self") InteropLibrary lib,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (lib.isMetaObject(self)) {
                return factory.createTuple(PythonUtils.EMPTY_OBJECT_ARRAY);
            } else {
                throw raiseNode.get(inliningTarget).raise(AttributeError, ErrorMessages.FOREIGN_OBJ_HAS_NO_ATTR_S, T___BASES__);
            }
        }
    }

    @Builtin(name = J___INSTANCECHECK__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @ImportStatic(PGuards.class)
    abstract static class InstancecheckNode extends PythonBinaryBuiltinNode {
        @Specialization(limit = "3")
        static Object check(Object self, Object instance,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary("self") InteropLibrary lib,
                        @Cached GilNode gil,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (lib.isMetaObject(self)) {
                gil.release(true);
                try {
                    return lib.isMetaInstance(self, instance);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere();
                } finally {
                    gil.acquire();
                }
            } else {
                throw raiseNode.get(inliningTarget).raise(AttributeError, ErrorMessages.FOREIGN_OBJ_HAS_NO_ATTR_S, T___INSTANCECHECK__);
            }
        }
    }

    @Builtin(name = J___RAND__, minNumOfPositionalArgs = 2)
    @Builtin(name = J___AND__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class AndNode extends PythonBinaryBuiltinNode {
        @Specialization(limit = "3")
        protected static Object op(VirtualFrame frame, Object left, Object right,
                        @Cached BitAndNode andNode,
                        @CachedLibrary("left") InteropLibrary lib,
                        @Cached GilNode gil) {
            if (lib.isNumber(left) && lib.fitsInLong(left)) {
                try {
                    long leftLong;
                    gil.release(true);
                    try {
                        leftLong = lib.asLong(left);
                    } finally {
                        gil.acquire();
                    }
                    return andNode.executeObject(frame, leftLong, right);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere();
                }
            } else {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }
    }

    @Builtin(name = J___ROR__, minNumOfPositionalArgs = 2)
    @Builtin(name = J___OR__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class OrNode extends PythonBinaryBuiltinNode {
        @Specialization(limit = "3")
        protected static Object op(VirtualFrame frame, Object left, Object right,
                        @Cached BitOrNode orNode,
                        @CachedLibrary("left") InteropLibrary lib,
                        @Cached GilNode gil) {
            if (lib.isNumber(left) && lib.fitsInLong(left)) {
                try {
                    long leftLong;
                    gil.release(true);
                    try {
                        leftLong = lib.asLong(left);
                    } finally {
                        gil.acquire();
                    }
                    return orNode.executeObject(frame, leftLong, right);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere();
                }
            } else {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }
    }

    @Builtin(name = J___RXOR__, minNumOfPositionalArgs = 2)
    @Builtin(name = J___XOR__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class XorNode extends PythonBinaryBuiltinNode {
        @Specialization(limit = "3")
        protected static Object op(VirtualFrame frame, Object left, Object right,
                        @Cached BitXorNode xorNode,
                        @CachedLibrary("left") InteropLibrary lib,
                        @Cached GilNode gil) {
            if (lib.isNumber(left) && lib.fitsInLong(left)) {
                try {
                    long leftLong;
                    gil.release(true);
                    try {
                        leftLong = lib.asLong(left);
                    } finally {
                        gil.acquire();
                    }
                    return xorNode.executeObject(frame, leftLong, right);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere();
                }
            } else {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
        }
    }
}
