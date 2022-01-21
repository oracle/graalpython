/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.cext;

import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_PY_TRUFFLE_PY_MAPPING_CHECK;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_PY_TRUFFLE_PY_MAPPING_SIZE;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_PY_TRUFFLE_PY_OBJECT_SIZE;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_PY_TRUFFLE_PY_SEQUENCE_CHECK;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol.FUN_PY_TRUFFLE_PY_SEQUENCE_SIZE;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.nodes.ErrorMessages.BASE_MUST_BE;
import static com.oracle.graal.python.nodes.ErrorMessages.OBJ_ISNT_MAPPING;
import static com.oracle.graal.python.nodes.ErrorMessages.P_OBJ_DOES_NOT_SUPPORT_ITEM_ASSIGMENT;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__IADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__IMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.ITEMS;
import static com.oracle.graal.python.nodes.SpecialMethodNames.KEYS;
import static com.oracle.graal.python.nodes.SpecialMethodNames.VALUES;

import java.util.List;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinConstructors.StrNode;
import com.oracle.graal.python.builtins.modules.BuiltinConstructors.TupleNode;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.AbsNode;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.BinNode;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.DivModNode;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.HexNode;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.NextNode;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.OctNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.NativeBuiltin;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.PyErrRestoreNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.AddRefCntNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.AsPythonObjectNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PRaiseNativeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ToNewRefNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.TransformExceptionToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.DynamicObjectNativeWrapper.PrimitiveNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.CheckPrimitiveFunctionResultNode;
import com.oracle.graal.python.builtins.objects.dict.DictBuiltins.ItemsNode;
import com.oracle.graal.python.builtins.objects.dict.DictBuiltins.KeysNode;
import com.oracle.graal.python.builtins.objects.dict.DictBuiltins.ValuesNode;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.mappingproxy.PMappingproxy;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsSameTypeNode;
import com.oracle.graal.python.lib.PyNumberFloatNode;
import com.oracle.graal.python.lib.PyObjectDelItem;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.builtins.ListNodes.ConstructListNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic.MulNode;
import com.oracle.graal.python.nodes.expression.BinaryOpNode;
import com.oracle.graal.python.nodes.expression.ContainsNode;
import com.oracle.graal.python.nodes.expression.InplaceArithmetic;
import com.oracle.graal.python.nodes.expression.LookupAndCallInplaceNode;
import com.oracle.graal.python.nodes.expression.UnaryArithmetic;
import com.oracle.graal.python.nodes.expression.UnaryOpNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.subscript.SliceLiteralNode;
import com.oracle.graal.python.nodes.truffle.PythonTypes;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendsModule = PythonCextBuiltins.PYTHON_CEXT)
@GenerateNodeFactory
public final class PythonCextAbstractBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PythonCextAbstractBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
    }

    /////// PyNumber ///////

    @Builtin(name = "PyNumber_Check", minNumOfPositionalArgs = 1)
    @TypeSystemReference(PythonTypes.class)
    @GenerateNodeFactory
    abstract static class PyNumberCheckNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object check(VirtualFrame frame, Object obj,
                        @Cached com.oracle.graal.python.lib.PyNumberCheckNode checkNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return checkNode.execute(frame, obj);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }
    }

    @Builtin(name = "PyNumber_Index", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyNumberIndexNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object index(VirtualFrame frame, Object obj,
                        @Cached com.oracle.graal.python.lib.PyNumberIndexNode indexNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return indexNode.execute(frame, obj);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }
    }

    @Builtin(name = "PyNumber_Long", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyNumberLongNode extends PythonUnaryBuiltinNode {

        @Specialization
        int nlong(int i) {
            return i;
        }

        @Specialization
        long nlong(long i) {
            return i;
        }

        @Fallback
        Object nlong(VirtualFrame frame, Object obj,
                        @Cached com.oracle.graal.python.builtins.modules.BuiltinConstructors.IntNode intNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return intNode.executeWith(frame, obj, PNone.NO_VALUE);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }
    }

    @Builtin(name = "PyNumber_Absolute", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyNumberAbsoluteNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object abs(VirtualFrame frame, Object obj,
                        @Cached AbsNode absNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return absNode.execute(frame, obj);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }
    }

    @Builtin(name = "PyNumber_Divmod", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyNumberDivmodeNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object div(VirtualFrame frame, Object a, Object b,
                        @Cached DivModNode divNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return divNode.execute(frame, a, b);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }
    }

    @Builtin(name = "PyNumber_ToBase", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyNumberToBaseNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "base == 2")
        Object toBase(VirtualFrame frame, Object n, @SuppressWarnings("unused") int base,
                        @Cached com.oracle.graal.python.lib.PyNumberIndexNode indexNode,
                        @Cached BinNode binNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                Object i = indexNode.execute(frame, n);
                return binNode.execute(frame, i);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }

        @Specialization(guards = "base == 8")
        Object toBase(VirtualFrame frame, Object n, @SuppressWarnings("unused") int base,
                        @Cached OctNode octNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return octNode.execute(frame, n);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }

        @Specialization(guards = "base == 10")
        Object toBase(VirtualFrame frame, Object n, @SuppressWarnings("unused") int base,
                        @Cached com.oracle.graal.python.lib.PyNumberIndexNode indexNode,
                        @Cached StrNode strNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                Object i = indexNode.execute(frame, n);
                if (i instanceof Boolean) {
                    i = ((boolean) i) ? 1 : 0;
                }
                return strNode.executeWith(frame, i);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }

        @Specialization(guards = "base == 16")
        Object toBase(VirtualFrame frame, Object n, @SuppressWarnings("unused") int base,
                        @Cached PyNumberIndexNode indexNode,
                        @Cached HexNode hexNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                Object i = indexNode.execute(frame, n);
                return hexNode.execute(frame, i);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }

        @Specialization(guards = "!checkBase(base)")
        Object toBase(VirtualFrame frame, @SuppressWarnings("unused") Object n, @SuppressWarnings("unused") int base,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raise(frame, getContext().getNativeNull(), SystemError, BASE_MUST_BE);
        }

        protected boolean checkBase(int base) {
            return base == 2 || base == 8 || base == 10 || base == 16;
        }
    }

    // directly called without landing function
    @Builtin(name = "PyNumber_Float", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyNumberFloat extends NativeBuiltin {

        @Specialization(guards = "object.isDouble()")
        static Object doDoubleNativeWrapper(PrimitiveNativeWrapper object,
                        @Cached AddRefCntNode refCntNode) {
            return refCntNode.inc(object);
        }

        @Specialization(guards = "!object.isDouble()")
        static Object doLongNativeWrapper(PrimitiveNativeWrapper object,
                        @Cached ToNewRefNode primitiveToSulongNode) {
            return primitiveToSulongNode.execute((double) object.getLong());
        }

        @Specialization
        Object doGeneric(VirtualFrame frame, Object object,
                        @Cached ToNewRefNode toNewRefNode,
                        @Cached AsPythonObjectNode asPythonObjectNode,
                        @Cached PyNumberFloatNode pyNumberFloat) {
            try {
                return toNewRefNode.execute(pyNumberFloat.execute(frame, asPythonObjectNode.execute(object)));
            } catch (PException e) {
                transformToNative(frame, e);
                return toNewRefNode.execute(getContext().getNativeNull());
            }
        }
    }

    // directly called without landing function
    @Builtin(name = "PyNumber_UnaryOp", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyNumberUnaryOp extends PythonBinaryBuiltinNode {
        static int MAX_CACHE_SIZE = UnaryArithmetic.values().length;

        @Specialization(guards = {"cachedOp == op", "left.isIntLike()"}, limit = "MAX_CACHE_SIZE")
        Object doIntLikePrimitiveWrapper(VirtualFrame frame, PrimitiveNativeWrapper left, @SuppressWarnings("unused") int op,
                        @Cached("op") @SuppressWarnings("unused") int cachedOp,
                        @Cached("createCallNode(op)") UnaryOpNode callNode,
                        @Cached ToNewRefNode toSulongNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return toSulongNode.execute(callNode.execute(frame, left.getLong()));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return toSulongNode.execute(getContext().getNativeNull());
            }
        }

        @Specialization(guards = "cachedOp == op", limit = "MAX_CACHE_SIZE", replaces = "doIntLikePrimitiveWrapper")
        Object doObject(VirtualFrame frame, Object left, @SuppressWarnings("unused") int op,
                        @Cached AsPythonObjectNode leftToJava,
                        @Cached("op") @SuppressWarnings("unused") int cachedOp,
                        @Cached("createCallNode(op)") UnaryOpNode callNode,
                        @Cached ToNewRefNode toSulongNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            // still try to avoid expensive materialization of primitives
            Object result;
            try {
                Object leftValue;
                if (left instanceof PrimitiveNativeWrapper) {
                    leftValue = PyNumberBinOp.extract((PrimitiveNativeWrapper) left);
                } else {
                    leftValue = leftToJava.execute(left);
                }
                result = callNode.execute(frame, leftValue);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                result = getContext().getNativeNull();
            }
            return toSulongNode.execute(result);
        }

        /**
         * This needs to stay in sync with {@code abstract.c: enum e_unaryop}.
         */
        static UnaryOpNode createCallNode(int op) {
            UnaryArithmetic unaryArithmetic;
            switch (op) {
                case 0:
                    unaryArithmetic = UnaryArithmetic.Pos;
                    break;
                case 1:
                    unaryArithmetic = UnaryArithmetic.Neg;
                    break;
                case 2:
                    unaryArithmetic = UnaryArithmetic.Invert;
                    break;
                default:
                    throw CompilerDirectives.shouldNotReachHere("invalid unary operator");
            }
            return unaryArithmetic.create();
        }
    }

    // directly called without landing function
    @Builtin(name = "PyNumber_BinOp", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class PyNumberBinOp extends PythonTernaryBuiltinNode {
        static int MAX_CACHE_SIZE = BinaryArithmetic.values().length;

        @Specialization(guards = {"cachedOp == op", "left.isIntLike()", "right.isIntLike()"}, limit = "MAX_CACHE_SIZE")
        Object doIntLikePrimitiveWrapper(VirtualFrame frame, PrimitiveNativeWrapper left, PrimitiveNativeWrapper right, @SuppressWarnings("unused") int op,
                        @Cached("op") @SuppressWarnings("unused") int cachedOp,
                        @Cached("createCallNode(op)") BinaryOpNode callNode,
                        @Cached ToNewRefNode toSulongNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return toSulongNode.execute(callNode.executeObject(frame, left.getLong(), right.getLong()));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return toSulongNode.execute(getContext().getNativeNull());
            }
        }

        @Specialization(guards = "cachedOp == op", limit = "MAX_CACHE_SIZE", replaces = "doIntLikePrimitiveWrapper")
        Object doObject(VirtualFrame frame, Object left, Object right, @SuppressWarnings("unused") int op,
                        @Cached AsPythonObjectNode leftToJava,
                        @Cached AsPythonObjectNode rightToJava,
                        @Cached("op") @SuppressWarnings("unused") int cachedOp,
                        @Cached("createCallNode(op)") BinaryOpNode callNode,
                        @Cached ToNewRefNode toSulongNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            // still try to avoid expensive materialization of primitives
            Object result;
            try {
                if (left instanceof PrimitiveNativeWrapper || right instanceof PrimitiveNativeWrapper) {
                    Object leftValue;
                    Object rightValue;
                    if (left instanceof PrimitiveNativeWrapper) {
                        leftValue = extract((PrimitiveNativeWrapper) left);
                    } else {
                        leftValue = leftToJava.execute(left);
                    }
                    if (right instanceof PrimitiveNativeWrapper) {
                        rightValue = extract((PrimitiveNativeWrapper) right);
                    } else {
                        rightValue = rightToJava.execute(right);
                    }
                    result = callNode.executeObject(frame, leftValue, rightValue);
                } else {
                    result = callNode.executeObject(frame, leftToJava.execute(left), rightToJava.execute(right));
                }
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                result = getContext().getNativeNull();
            }
            return toSulongNode.execute(result);
        }

        static Object extract(PrimitiveNativeWrapper wrapper) {
            if (wrapper.isIntLike()) {
                return wrapper.getLong();
            }
            if (wrapper.isDouble()) {
                return wrapper.getDouble();
            }
            if (wrapper.isBool()) {
                return wrapper.getBool();
            }
            throw CompilerDirectives.shouldNotReachHere("unexpected wrapper state");
        }

        /**
         * This needs to stay in sync with {@code abstract.c: enum e_binop}.
         */
        static BinaryOpNode createCallNode(int op) {
            return getBinaryArithmetic(op).create();
        }

        private static BinaryArithmetic getBinaryArithmetic(int op) {
            switch (op) {
                case 0:
                    return BinaryArithmetic.Add;
                case 1:
                    return BinaryArithmetic.Sub;
                case 2:
                    return BinaryArithmetic.Mul;
                case 3:
                    return BinaryArithmetic.TrueDiv;
                case 4:
                    return BinaryArithmetic.LShift;
                case 5:
                    return BinaryArithmetic.RShift;
                case 6:
                    return BinaryArithmetic.Or;
                case 7:
                    return BinaryArithmetic.And;
                case 8:
                    return BinaryArithmetic.Xor;
                case 9:
                    return BinaryArithmetic.FloorDiv;
                case 10:
                    return BinaryArithmetic.Mod;
                case 12:
                    return BinaryArithmetic.MatMul;
                default:
                    throw CompilerDirectives.shouldNotReachHere("invalid binary operator");
            }
        }

    }

    // directly called without landing function
    @Builtin(name = "PyNumber_InPlaceBinOp", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class PyNumberInPlaceBinOp extends PythonTernaryBuiltinNode {
        static int MAX_CACHE_SIZE = InplaceArithmetic.values().length;

        @Specialization(guards = {"cachedOp == op", "left.isIntLike()", "right.isIntLike()"}, limit = "MAX_CACHE_SIZE")
        Object doIntLikePrimitiveWrapper(VirtualFrame frame, PrimitiveNativeWrapper left, PrimitiveNativeWrapper right, @SuppressWarnings("unused") int op,
                        @Cached("op") @SuppressWarnings("unused") int cachedOp,
                        @Cached("createCallNode(op)") LookupAndCallInplaceNode callNode,
                        @Cached ToNewRefNode toSulongNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return toSulongNode.execute(callNode.execute(frame, left.getLong(), right.getLong()));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return toSulongNode.execute(getContext().getNativeNull());
            }
        }

        @Specialization(guards = "cachedOp == op", limit = "MAX_CACHE_SIZE", replaces = "doIntLikePrimitiveWrapper")
        Object doObject(VirtualFrame frame, Object left, Object right, @SuppressWarnings("unused") int op,
                        @Cached AsPythonObjectNode leftToJava,
                        @Cached AsPythonObjectNode rightToJava,
                        @Cached("op") @SuppressWarnings("unused") int cachedOp,
                        @Cached("createCallNode(op)") LookupAndCallInplaceNode callNode,
                        @Cached ToNewRefNode toSulongNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            // still try to avoid expensive materialization of primitives
            Object result;
            try {
                if (left instanceof PrimitiveNativeWrapper || right instanceof PrimitiveNativeWrapper) {
                    Object leftValue;
                    Object rightValue;
                    if (left instanceof PrimitiveNativeWrapper) {
                        leftValue = PyNumberBinOp.extract((PrimitiveNativeWrapper) left);
                    } else {
                        leftValue = leftToJava.execute(left);
                    }
                    if (right instanceof PrimitiveNativeWrapper) {
                        rightValue = PyNumberBinOp.extract((PrimitiveNativeWrapper) right);
                    } else {
                        rightValue = rightToJava.execute(right);
                    }
                    result = callNode.execute(frame, leftValue, rightValue);
                } else {
                    result = callNode.execute(frame, leftToJava.execute(left), rightToJava.execute(right));
                }
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                result = getContext().getNativeNull();
            }
            return toSulongNode.execute(result);
        }

        /**
         * This needs to stay in sync with {@code abstract.c: enum e_binop}.
         */
        static LookupAndCallInplaceNode createCallNode(int op) {
            return getInplaceArithmetic(op).create();
        }

        private static InplaceArithmetic getInplaceArithmetic(int op) {
            switch (op) {
                case 0:
                    return InplaceArithmetic.IAdd;
                case 1:
                    return InplaceArithmetic.ISub;
                case 2:
                    return InplaceArithmetic.IMul;
                case 3:
                    return InplaceArithmetic.ITrueDiv;
                case 4:
                    return InplaceArithmetic.ILShift;
                case 5:
                    return InplaceArithmetic.IRShift;
                case 6:
                    return InplaceArithmetic.IOr;
                case 7:
                    return InplaceArithmetic.IAnd;
                case 8:
                    return InplaceArithmetic.IXor;
                case 9:
                    return InplaceArithmetic.IFloorDiv;
                case 10:
                    return InplaceArithmetic.IMod;
                case 12:
                    return InplaceArithmetic.IMatMul;
                default:
                    throw CompilerDirectives.shouldNotReachHere("invalid binary operator");
            }
        }

    }

    // directly called without landing function
    @Builtin(name = "PyNumber_InPlacePower", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class PyNumberInPlacePower extends PythonTernaryBuiltinNode {
        @Child private LookupAndCallInplaceNode callNode;

        @Specialization(guards = {"o1.isIntLike()", "o2.isIntLike()", "o3.isIntLike()"})
        Object doIntLikePrimitiveWrapper(VirtualFrame frame, PrimitiveNativeWrapper o1, PrimitiveNativeWrapper o2, PrimitiveNativeWrapper o3,
                        @Cached ToNewRefNode toSulongNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return toSulongNode.execute(ensureCallNode().executeTernary(frame, o1.getLong(), o2.getLong(), o3.getLong()));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return toSulongNode.execute(getContext().getNativeNull());
            }
        }

        @Specialization(replaces = "doIntLikePrimitiveWrapper")
        Object doGeneric(VirtualFrame frame, Object o1, Object o2, Object o3,
                        @Cached AsPythonObjectNode o1ToJava,
                        @Cached AsPythonObjectNode o2ToJava,
                        @Cached AsPythonObjectNode o3ToJava,
                        @Cached ToNewRefNode toSulongNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            // still try to avoid expensive materialization of primitives
            Object result;
            try {
                Object o1Value = unwrap(o1, o1ToJava);
                Object o2Value = unwrap(o2, o2ToJava);
                Object o3Value = unwrap(o3, o3ToJava);
                result = ensureCallNode().executeTernary(frame, o1Value, o2Value, o3Value);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                result = getContext().getNativeNull();
            }
            return toSulongNode.execute(result);
        }

        private static Object unwrap(Object left, AsPythonObjectNode toJavaNode) {
            if (left instanceof PrimitiveNativeWrapper) {
                return PyNumberBinOp.extract((PrimitiveNativeWrapper) left);
            } else {
                return toJavaNode.execute(left);
            }
        }

        private LookupAndCallInplaceNode ensureCallNode() {
            if (callNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callNode = insert(InplaceArithmetic.IPow.create());
            }
            return callNode;
        }
    }

    /////// PySequence ///////

    @Builtin(name = "PySequence_Tuple", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class PySequenceTupleNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "isTuple(obj, getClassNode)")
        public PTuple values(PTuple obj,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode) {
            return obj;
        }

        @Specialization(guards = "!isTuple(obj, getClassNode)")
        public Object values(VirtualFrame frame, Object obj,
                        @Cached TupleNode tupleNode,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return tupleNode.execute(frame, PythonBuiltinClassType.PTuple, obj);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }

        protected boolean isTuple(Object obj, GetClassNode getClassNode) {
            return getClassNode.execute(obj) == PythonBuiltinClassType.PTuple;
        }
    }

    @Builtin(name = "PySequence_List", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class PySequenceListNode extends PythonUnaryBuiltinNode {
        @Specialization
        public Object values(VirtualFrame frame, Object obj,
                        @Cached ConstructListNode listNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return listNode.execute(frame, obj);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }
    }

    @Builtin(name = "PySequence_SetItem", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class PySequenceSetItemNode extends PythonTernaryBuiltinNode {
        @Specialization(guards = "checkNode.execute(obj)")
        public Object setItem(VirtualFrame frame, Object obj, Object key, Object value,
                        @SuppressWarnings("unused") @Cached com.oracle.graal.python.lib.PySequenceCheckNode checkNode,
                        @Cached PyObjectLookupAttr lookupAttrNode,
                        @Cached ConditionProfile hasSetItem,
                        @Cached CallNode callNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                Object setItemCallable = lookupAttrNode.execute(frame, obj, __SETITEM__);
                if (hasSetItem.profile(setItemCallable == PNone.NO_VALUE)) {
                    throw raise(TypeError, P_OBJ_DOES_NOT_SUPPORT_ITEM_ASSIGMENT, obj);
                } else {
                    callNode.execute(setItemCallable, key, value);
                    return 0;
                }
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return -1;
            }
        }

        @Specialization(guards = "!checkNode.execute(obj)")
        Object setItem(VirtualFrame frame, Object obj, @SuppressWarnings("unused") Object key, @SuppressWarnings("unused") Object value,
                        @SuppressWarnings("unused") @Cached com.oracle.graal.python.lib.PySequenceCheckNode checkNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raiseInt(frame, -1, TypeError, ErrorMessages.IS_NOT_A_SEQUENCE, obj);
        }
    }

    @Builtin(name = "PySequence_GetSlice", minNumOfPositionalArgs = 3)
    @TypeSystemReference(PythonTypes.class)
    @GenerateNodeFactory
    abstract static class PySequenceGetSliceNode extends PythonTernaryBuiltinNode {

        @Specialization(guards = "checkNode.execute(obj)")
        Object getSlice(VirtualFrame frame, Object obj, long iLow, long iHigh,
                        @SuppressWarnings("unused") @Cached com.oracle.graal.python.lib.PySequenceCheckNode checkNode,
                        @Cached PyObjectLookupAttr lookupAttrNode,
                        @Cached SliceLiteralNode sliceNode,
                        @Cached CallNode callNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                Object getItemCallable = lookupAttrNode.execute(frame, obj, __GETITEM__);
                return callNode.execute(getItemCallable, sliceNode.execute(frame, iLow, iHigh, PNone.NONE));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }

        @Specialization(guards = "!checkNode.execute(obj)")
        Object getSlice(VirtualFrame frame, Object obj, @SuppressWarnings("unused") Object key, @SuppressWarnings("unused") Object value,
                        @SuppressWarnings("unused") @Cached com.oracle.graal.python.lib.PySequenceCheckNode checkNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raise(frame, getContext().getNativeNull(), TypeError, ErrorMessages.OBJ_IS_UNSLICEABLE, obj);
        }
    }

    @Builtin(name = "PySequence_Contains", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PySequenceContainsNode extends PythonBinaryBuiltinNode {

        @Specialization
        Object contains(VirtualFrame frame, Object haystack, Object needle,
                        @Cached ContainsNode containsNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return containsNode.executeObject(frame, needle, haystack);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return -1;
            }
        }
    }

    @Builtin(name = "PySequence_Repeat", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PySequenceRepeatNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "checkNode.execute(obj)")
        Object repeat(VirtualFrame frame, Object obj, long n,
                        @SuppressWarnings("unused") @Cached com.oracle.graal.python.lib.PySequenceCheckNode checkNode,
                        @Cached("createMul()") MulNode mulNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return mulNode.executeObject(frame, obj, n);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }

        @Specialization(guards = "!checkNode.execute(obj)")
        protected Object repeat(VirtualFrame frame, Object obj, @SuppressWarnings("unused") Object n,
                        @SuppressWarnings("unused") @Cached com.oracle.graal.python.lib.PySequenceCheckNode checkNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raise(frame, getContext().getNativeNull(), TypeError, ErrorMessages.OBJ_CANT_BE_REPEATED, obj);
        }

        protected MulNode createMul() {
            return (MulNode) BinaryArithmetic.Mul.create();
        }
    }

    @Builtin(name = "PySequence_InPlaceRepeat", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PySequenceInPlaceRepeatNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = {"checkNode.execute(obj)"})
        Object repeat(VirtualFrame frame, Object obj, long n,
                        @Cached PyObjectLookupAttr lookupNode,
                        @Cached CallNode callNode,
                        @Cached("createMul()") MulNode mulNode,
                        @SuppressWarnings("unused") @Cached com.oracle.graal.python.lib.PySequenceCheckNode checkNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                Object imulCallable = lookupNode.execute(frame, obj, __IMUL__);
                if (imulCallable != PNone.NO_VALUE) {
                    Object ret = callNode.execute(frame, imulCallable, n);
                    return ret;
                }
                return mulNode.executeObject(frame, obj, n);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }

        @Specialization(guards = "!checkNode.execute(obj)")
        protected Object repeat(VirtualFrame frame, Object obj, @SuppressWarnings("unused") Object n,
                        @SuppressWarnings("unused") @Cached com.oracle.graal.python.lib.PySequenceCheckNode checkNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raise(frame, getContext().getNativeNull(), TypeError, ErrorMessages.OBJ_CANT_BE_REPEATED, obj);
        }

        protected MulNode createMul() {
            return (MulNode) BinaryArithmetic.Mul.create();
        }
    }

    @Builtin(name = "PySequence_Concat", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PySequenceConcatNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = {"checkNode.execute(s1)", "checkNode.execute(s1)"})
        Object concat(VirtualFrame frame, Object s1, Object s2,
                        @SuppressWarnings("unused") @Cached com.oracle.graal.python.lib.PySequenceCheckNode checkNode,
                        @Cached("createAdd()") BinaryArithmetic.AddNode addNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return addNode.executeObject(frame, s1, s2);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }

        @Specialization(guards = {"!checkNode.execute(s1) || checkNode.execute(s2)"})
        protected Object cantConcat(VirtualFrame frame, Object s1, @SuppressWarnings("unused") Object s2,
                        @SuppressWarnings("unused") @Cached com.oracle.graal.python.lib.PySequenceCheckNode checkNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raise(frame, getContext().getNativeNull(), TypeError, ErrorMessages.OBJ_CANT_BE_CONCATENATED, s1);
        }

        protected BinaryArithmetic.AddNode createAdd() {
            return (BinaryArithmetic.AddNode) BinaryArithmetic.Add.create();
        }
    }

    @Builtin(name = "PySequence_InPlaceConcat", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PySequenceInPlaceConcatNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = {"checkNode.execute(s1)"})
        Object concat(VirtualFrame frame, Object s1, Object s2,
                        @Cached PyObjectLookupAttr lookupNode,
                        @Cached CallNode callNode,
                        @Cached("createAdd()") BinaryArithmetic.AddNode addNode,
                        @SuppressWarnings("unused") @Cached com.oracle.graal.python.lib.PySequenceCheckNode checkNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                Object iaddCallable = lookupNode.execute(frame, s1, __IADD__);
                if (iaddCallable != PNone.NO_VALUE) {
                    return callNode.execute(frame, iaddCallable, s2);
                }
                return addNode.executeObject(frame, s1, s2);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }

        @Specialization(guards = "!checkNode.execute(s1)")
        protected Object concat(VirtualFrame frame, Object s1, @SuppressWarnings("unused") Object s2,
                        @SuppressWarnings("unused") @Cached com.oracle.graal.python.lib.PySequenceCheckNode checkNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raise(frame, getContext().getNativeNull(), TypeError, ErrorMessages.OBJ_CANT_BE_CONCATENATED, s1);
        }

        protected BinaryArithmetic.AddNode createAdd() {
            return (BinaryArithmetic.AddNode) BinaryArithmetic.Add.create();
        }
    }

    @Builtin(name = "PySequence_DelItem", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class PySequenceDelItemNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object run(VirtualFrame frame, Object o, Object i,
                        @Cached PyObjectDelItem delItemNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                delItemNode.execute(frame, o, i);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return -1;
            }
            return 0;
        }
    }

    @Builtin(name = "PySequence_Check", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PySequenceCheckNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "!isNativeObject(object)")
        static boolean check(Object object,
                        @Cached com.oracle.graal.python.lib.PySequenceCheckNode check) {
            return check.execute(object);
        }

        @Specialization(guards = "isNativeObject(object)")
        static Object doNative(VirtualFrame frame, Object object,
                        @Cached ToSulongNode toSulongNode,
                        @Cached PCallCapiFunction callCapiFunction,
                        @Cached CheckPrimitiveFunctionResultNode checkFunctionResultNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            Object result = callCapiFunction.call(FUN_PY_TRUFFLE_PY_SEQUENCE_CHECK, toSulongNode.execute(object));
            checkFunctionResultNode.execute(PythonContext.get(callCapiFunction), FUN_PY_TRUFFLE_PY_SEQUENCE_CHECK.getName(), result);
            return result;
        }
    }

    @Builtin(name = "PySequence_GetItem", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PySequenceGetItemNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object doManaged(VirtualFrame frame, Object listWrapper, Object position,
                        @Cached com.oracle.graal.python.lib.PySequenceCheckNode pySequenceCheck,
                        @Cached com.oracle.graal.python.lib.PyObjectGetItem getItem,
                        @Cached AsPythonObjectNode listWrapperAsPythonObjectNode,
                        @Cached AsPythonObjectNode positionAsPythonObjectNode,
                        @Cached ToNewRefNode toNewRefNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                Object delegate = listWrapperAsPythonObjectNode.execute(listWrapper);
                if (!pySequenceCheck.execute(delegate)) {
                    throw raise(TypeError, ErrorMessages.OBJ_DOES_NOT_SUPPORT_INDEXING, delegate);
                }
                Object item = getItem.execute(frame, delegate, positionAsPythonObjectNode.execute(position));
                return toNewRefNode.execute(item);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return toNewRefNode.execute(getContext().getNativeNull());
            }
        }
    }

    @Builtin(name = "PySequence_Size", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @ImportStatic(SpecialMethodNames.class)
    abstract static class PySequenceSizeNode extends PythonUnaryBuiltinNode {

        // cant use PySequence_Size: PySequence_Size returns the __len__ value also for
        // subclasses of types not accepted by PySequence_Check as long they have an overriden
        // __len__ method
        @Specialization(guards = {"!isNativeObject(obj)", "!isDictOrMappingProxy(obj, isSameType, getClassNode)"}, limit = "1")
        static Object doSequence(VirtualFrame frame, Object obj,
                        @Shared("isSameType") @SuppressWarnings("unused") @Cached IsSameTypeNode isSameType,
                        @Shared("getClass") @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @Cached com.oracle.graal.python.lib.PyObjectSizeNode sizeNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return sizeNode.execute(frame, obj);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return -1;
            }
        }

        @Specialization(guards = "isNativeObject(obj)")
        static Object doNative(VirtualFrame frame, Object obj,
                        @Cached ToSulongNode toSulongNode,
                        @Cached PCallCapiFunction callCapiFunction,
                        @Cached CheckPrimitiveFunctionResultNode checkFunctionResultNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                Object result = callCapiFunction.call(FUN_PY_TRUFFLE_PY_SEQUENCE_SIZE, toSulongNode.execute(obj));
                checkFunctionResultNode.execute(PythonContext.get(callCapiFunction), FUN_PY_TRUFFLE_PY_SEQUENCE_SIZE.getName(), result);
                return result;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return -1;
            }
        }

        @Specialization(guards = {"isDictOrMappingProxy(obj, isSameType, getClassNode)"}, limit = "1")
        static Object notSequence(VirtualFrame frame, Object obj,
                        @Shared("isSameType") @SuppressWarnings("unused") @Cached IsSameTypeNode isSameType,
                        @Shared("getClass") @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raiseInt(frame, -1, TypeError, ErrorMessages.IS_NOT_A_SEQUENCE, obj);
        }

        protected static boolean isDictOrMappingProxy(Object obj, IsSameTypeNode isSameType, GetClassNode getClassNode) {
            return obj instanceof PMappingproxy || isSameType.execute(getClassNode.execute(obj), PythonBuiltinClassType.PDict);
        }
    }

    /////// PyObject ///////

    @Builtin(name = "PyObject_GetItem", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyObjectGetItemNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object doManaged(VirtualFrame frame, Object listWrapper, Object key,
                        @Cached com.oracle.graal.python.lib.PyObjectGetItem getItem,
                        @Cached AsPythonObjectNode listWrapperAsPythonObjectNode,
                        @Cached AsPythonObjectNode keyAsPythonObjectNode,
                        @Cached ToNewRefNode toNewRefNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                Object delegate = listWrapperAsPythonObjectNode.execute(listWrapper);
                Object item = getItem.execute(frame, delegate, keyAsPythonObjectNode.execute(key));
                return toNewRefNode.execute(item);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return toNewRefNode.execute(getContext().getNativeNull());
            }
        }
    }

    @Builtin(name = "PyObject_Size", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @ImportStatic(SpecialMethodNames.class)
    abstract static class PyObjectSizeNode extends PythonUnaryBuiltinNode {

        @Specialization(guards = {"!isNativeObject(obj)"})
        static int doGenericUnboxed(VirtualFrame frame, Object obj,
                        @Cached com.oracle.graal.python.lib.PyObjectSizeNode sizeNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                // TODO: theoretically, it is legal for __LEN__ to return a PythonNativeVoidPtr,
                // which is not handled in c.o.g.p.lib.PyObjectSizeNode at this point
                return sizeNode.execute(frame, obj);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return -1;
            }
        }

        @Specialization(guards = {"isNativeObject(obj)"})
        static Object size(VirtualFrame frame, Object obj,
                        @Cached ToSulongNode toSulongNode,
                        @Cached AsPythonObjectNode asPythonObjectNode,
                        @Cached PCallCapiFunction callCapiFunction,
                        @Cached CheckPrimitiveFunctionResultNode checkFunctionResultNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                Object result = callCapiFunction.call(FUN_PY_TRUFFLE_PY_OBJECT_SIZE, toSulongNode.execute(obj));
                checkFunctionResultNode.execute(PythonContext.get(callCapiFunction), FUN_PY_TRUFFLE_PY_OBJECT_SIZE.getName(), result);
                return result;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return -1;
            }
        }
    }

    /////// PyMapping ///////

    @Builtin(name = "PyMapping_Keys", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class PyMappingKeysNode extends PythonUnaryBuiltinNode {
        @Specialization
        public Object keys(VirtualFrame frame, PDict obj,
                        @Cached KeysNode keysNode,
                        @Cached ConstructListNode listNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return listNode.execute(frame, keysNode.execute(frame, obj));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }

        @Specialization(guards = "!isDict(obj)")
        public Object keys(VirtualFrame frame, Object obj,
                        @Cached PyObjectGetAttr getAttrNode,
                        @Cached CallNode callNode,
                        @Cached ConstructListNode listNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return getKeys(frame, obj, getAttrNode, callNode, listNode);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }

    }

    private static PList getKeys(VirtualFrame frame, Object obj, PyObjectGetAttr getAttrNode, CallNode callNode, ConstructListNode listNode) {
        Object attr = getAttrNode.execute(frame, obj, KEYS);
        return listNode.execute(frame, callNode.execute(frame, attr));
    }

    @Builtin(name = "PyMapping_Items", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class PyMappingItemsNode extends PythonUnaryBuiltinNode {
        @Specialization
        public Object items(VirtualFrame frame, PDict obj,
                        @Cached ItemsNode itemsNode,
                        @Cached ConstructListNode listNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return listNode.execute(frame, itemsNode.execute(frame, obj));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }

        @Specialization(guards = "!isDict(obj)")
        public Object items(VirtualFrame frame, Object obj,
                        @Cached PyObjectGetAttr getAttrNode,
                        @Cached CallNode callNode,
                        @Cached ConstructListNode listNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                Object attr = getAttrNode.execute(frame, obj, ITEMS);
                return listNode.execute(frame, callNode.execute(frame, attr));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }
    }

    @Builtin(name = "PyMapping_Values", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class PyMappingValuesNode extends PythonUnaryBuiltinNode {
        @Specialization
        public Object values(VirtualFrame frame, PDict obj,
                        @Cached ConstructListNode listNode,
                        @Cached ValuesNode valuesNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return listNode.execute(frame, valuesNode.execute(frame, obj));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }

        @Specialization(guards = "!isDict(obj)")
        public Object values(VirtualFrame frame, Object obj,
                        @Cached PyObjectGetAttr getAttrNode,
                        @Cached CallNode callNode,
                        @Cached ConstructListNode listNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                Object attr = getAttrNode.execute(frame, obj, VALUES);
                return listNode.execute(frame, callNode.execute(frame, attr));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }
    }

    @Builtin(name = "PyMapping_Check", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @ImportStatic(SpecialMethodNames.class)
    abstract static class PyMappingCheckNode extends PythonUnaryBuiltinNode {

        @Specialization(guards = "!isNativeObject(object)")
        static boolean doPythonObject(PythonObject object,
                        @Cached com.oracle.graal.python.lib.PyMappingCheckNode checkNode) {
            return checkNode.execute(object);
        }

        @Specialization(guards = "isNativeObject(obj)")
        static Object doNative(VirtualFrame frame, Object obj,
                        @Cached ToSulongNode toSulongNode,
                        @Cached PCallCapiFunction callCapiFunction,
                        @Cached CheckPrimitiveFunctionResultNode checkFunctionResultNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            Object result = callCapiFunction.call(FUN_PY_TRUFFLE_PY_MAPPING_CHECK, toSulongNode.execute(obj));
            checkFunctionResultNode.execute(PythonContext.get(callCapiFunction), FUN_PY_TRUFFLE_PY_MAPPING_CHECK.getName(), result);
            return result;
        }
    }

    @Builtin(name = "PyMapping_Size", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @ImportStatic(SpecialMethodNames.class)
    abstract static class PyMappingSizeNode extends PythonUnaryBuiltinNode {

        // cant use PyMapping_Check: PyMapping_Size returns the __len__ value also for
        // subclasses of types not accepted by PyMapping_Check as long they have an overriden
        // __len__ method
        @Specialization(guards = {"!isNativeObject(obj)", "!isSetOrDeque(obj, isSameType, getClassNode)"}, limit = "1")
        static int doMapping(VirtualFrame frame, Object obj,
                        @Cached com.oracle.graal.python.lib.PyObjectSizeNode sizeNode,
                        @Shared("isSameType") @SuppressWarnings("unused") @Cached IsSameTypeNode isSameType,
                        @Shared("getClass") @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return sizeNode.execute(frame, obj);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return -1;
            }
        }

        @Specialization(guards = "isNativeObject(obj)")
        static Object doNative(VirtualFrame frame, Object obj,
                        @Cached ToSulongNode toSulongNode,
                        @Cached PCallCapiFunction callCapiFunction,
                        @Cached CheckPrimitiveFunctionResultNode checkFunctionResultNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                Object result = callCapiFunction.call(FUN_PY_TRUFFLE_PY_MAPPING_SIZE, toSulongNode.execute(obj));
                checkFunctionResultNode.execute(PythonContext.get(callCapiFunction), FUN_PY_TRUFFLE_PY_MAPPING_SIZE.getName(), result);
                return result;
            } catch (PException e) {
                transformExceptionToNativeNode.execute(frame, e);
                return -1;
            }
        }

        @Specialization(guards = {"isSetOrDeque(obj, isSameType, getClassNode)"}, limit = "1")
        static Object notMapping(VirtualFrame frame, Object obj,
                        @Shared("isSameType") @SuppressWarnings("unused") @Cached IsSameTypeNode isSameType,
                        @Shared("getClass") @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raiseInt(frame, -1, TypeError, OBJ_ISNT_MAPPING, obj);
        }

        protected static boolean isSetOrDeque(Object obj, IsSameTypeNode isSameType, GetClassNode getClassNode) {
            return isSameType(obj, PythonBuiltinClassType.PSet, isSameType, getClassNode) ||
                            isSameType(obj, PythonBuiltinClassType.PFrozenSet, isSameType, getClassNode) ||
                            isSameType(obj, PythonBuiltinClassType.PDeque, isSameType, getClassNode);
        }

        private static boolean isSameType(Object obj, PythonBuiltinClassType type, IsSameTypeNode isSameType, GetClassNode getClassNode) {
            return isSameType.execute(getClassNode.execute(obj), type);
        }
    }

    /////// PyIter ///////

    @Builtin(name = "PyIter_Next", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyIterNextNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object check(VirtualFrame frame, Object object,
                        @Cached NextNode nextNode,
                        @Cached PyErrRestoreNode restoreNode,
                        @Cached IsBuiltinClassProfile isClassProfile,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return nextNode.execute(frame, object, PNone.NO_VALUE);
            } catch (PException e) {
                if (isClassProfile.profileException(e, PythonBuiltinClassType.StopIteration)) {
                    restoreNode.execute(frame, PNone.NONE, PNone.NONE, PNone.NONE);
                    return getContext().getNativeNull();
                } else {
                    transformExceptionToNativeNode.execute(e);
                    return getContext().getNativeNull();
                }
            }
        }
    }
}
