/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.nodes.ErrorMessages.BASE_MUST_BE;
import java.util.List;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinConstructors.StrNode;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.AbsNode;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.BinNode;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.DivModNode;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.HexNode;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.OctNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.NativeBuiltin;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.AddRefCntNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.AsPythonObjectNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.GetNativeNullNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PRaiseNativeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ToNewRefNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.TransformExceptionToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.DynamicObjectNativeWrapper.PrimitiveNativeWrapper;
import com.oracle.graal.python.lib.PyNumberFloatNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic;
import com.oracle.graal.python.nodes.expression.BinaryOpNode;
import com.oracle.graal.python.nodes.expression.InplaceArithmetic;
import com.oracle.graal.python.nodes.expression.LookupAndCallInplaceNode;
import com.oracle.graal.python.nodes.expression.UnaryArithmetic;
import com.oracle.graal.python.nodes.expression.UnaryOpNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.truffle.PythonTypes;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node.Child;

@CoreFunctions(defineModule = PythonCextNumberBuiltins.PYTHON_CEXT_NUMBER)
@GenerateNodeFactory
public class PythonCextNumberBuiltins extends PythonBuiltins {

    public static final String PYTHON_CEXT_NUMBER = "python_cext_number";

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PythonCextNumberBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
    }

    ///////////// number /////////////

    @Builtin(name = "PyNumber_Check", minNumOfPositionalArgs = 1)
    @TypeSystemReference(PythonTypes.class)
    @GenerateNodeFactory
    abstract static class PyNumberCheckNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object check(VirtualFrame frame, Object obj,
                        @Cached com.oracle.graal.python.lib.PyNumberCheckNode checkNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                return checkNode.execute(frame, obj);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getNativeNullNode.execute();
            }
        }
    }

    @Builtin(name = "PyNumber_Index", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyNumberIndexNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object index(VirtualFrame frame, Object obj,
                        @Cached com.oracle.graal.python.lib.PyNumberIndexNode indexNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                return indexNode.execute(frame, obj);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getNativeNullNode.execute();
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
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                return intNode.executeWith(frame, obj, PNone.NO_VALUE);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getNativeNullNode.execute();
            }
        }
    }

    @Builtin(name = "PyNumber_Absolute", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyNumberAbsoluteNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object abs(VirtualFrame frame, Object obj,
                        @Cached AbsNode absNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                return absNode.execute(frame, obj);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getNativeNullNode.execute();
            }
        }
    }

    @Builtin(name = "PyNumber_Divmod", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyNumberDivmodeNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object div(VirtualFrame frame, Object a, Object b,
                        @Cached DivModNode divNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                return divNode.execute(frame, a, b);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getNativeNullNode.execute();
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
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                Object i = indexNode.execute(frame, n);
                return binNode.execute(frame, i);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getNativeNullNode.execute();
            }
        }

        @Specialization(guards = "base == 8")
        Object toBase(VirtualFrame frame, Object n, @SuppressWarnings("unused") int base,
                        @Cached OctNode octNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                return octNode.execute(frame, n);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getNativeNullNode.execute();
            }
        }

        @Specialization(guards = "base == 10")
        Object toBase(VirtualFrame frame, Object n, @SuppressWarnings("unused") int base,
                        @Cached com.oracle.graal.python.lib.PyNumberIndexNode indexNode,
                        @Cached StrNode strNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                Object i = indexNode.execute(frame, n);
                if (i instanceof Boolean) {
                    i = ((boolean) i) ? 1 : 0;
                }
                return strNode.executeWith(frame, i);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getNativeNullNode.execute();
            }
        }

        @Specialization(guards = "base == 16")
        Object toBase(VirtualFrame frame, Object n, @SuppressWarnings("unused") int base,
                        @Cached PyNumberIndexNode indexNode,
                        @Cached HexNode hexNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                Object i = indexNode.execute(frame, n);
                return hexNode.execute(frame, i);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getNativeNullNode.execute();
            }
        }

        @Specialization(guards = "!checkBase(base)")
        Object toBase(VirtualFrame frame, @SuppressWarnings("unused") Object n, @SuppressWarnings("unused") int base,
                        @Cached GetNativeNullNode getNativeNullNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raise(frame, getNativeNullNode.execute(), SystemError, BASE_MUST_BE);
        }

        protected boolean checkBase(int base) {
            return base == 2 || base == 8 || base == 10 || base == 16;
        }
    }

    // directly called without landing function
    @Builtin(name = "PyNumber_Float", minNumOfPositionalArgs = 2, declaresExplicitSelf = true)
    @GenerateNodeFactory
    abstract static class PyNumberFloat extends NativeBuiltin {

        @Specialization(guards = "object.isDouble()")
        static Object doDoubleNativeWrapper(@SuppressWarnings("unused") Object module, PrimitiveNativeWrapper object,
                        @Cached AddRefCntNode refCntNode) {
            return refCntNode.inc(object);
        }

        @Specialization(guards = "!object.isDouble()")
        static Object doLongNativeWrapper(@SuppressWarnings("unused") Object module, PrimitiveNativeWrapper object,
                        @Cached ToNewRefNode primitiveToSulongNode) {
            return primitiveToSulongNode.execute((double) object.getLong());
        }

        @Specialization
        Object doGeneric(VirtualFrame frame, Object module, Object object,
                        @Cached GetNativeNullNode getNativeNullNode,
                        @Cached ToNewRefNode toNewRefNode,
                        @Cached AsPythonObjectNode asPythonObjectNode,
                        @Cached PyNumberFloatNode pyNumberFloat) {
            try {
                return toNewRefNode.execute(pyNumberFloat.execute(frame, asPythonObjectNode.execute(object)));
            } catch (PException e) {
                transformToNative(frame, e);
                return toNewRefNode.execute(getNativeNullNode.execute(module));
            }
        }
    }

    // directly called without landing function
    @Builtin(name = "PyNumber_UnaryOp", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class PyNumberUnaryOp extends PythonBinaryBuiltinNode {
        static int MAX_CACHE_SIZE = UnaryArithmetic.values().length;

        @Specialization(guards = {"cachedOp == op", "left.isIntLike()"}, limit = "MAX_CACHE_SIZE")
        static Object doIntLikePrimitiveWrapper(VirtualFrame frame, PrimitiveNativeWrapper left, @SuppressWarnings("unused") int op,
                        @Cached("op") @SuppressWarnings("unused") int cachedOp,
                        @Cached("createCallNode(op)") UnaryOpNode callNode,
                        @Cached ToNewRefNode toSulongNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                return toSulongNode.execute(callNode.execute(frame, left.getLong()));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return toSulongNode.execute(getNativeNullNode.execute());
            }
        }

        @Specialization(guards = "cachedOp == op", limit = "MAX_CACHE_SIZE", replaces = "doIntLikePrimitiveWrapper")
        static Object doObject(VirtualFrame frame, Object left, @SuppressWarnings("unused") int op,
                        @Cached AsPythonObjectNode leftToJava,
                        @Cached("op") @SuppressWarnings("unused") int cachedOp,
                        @Cached("createCallNode(op)") UnaryOpNode callNode,
                        @Cached ToNewRefNode toSulongNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
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
                result = getNativeNullNode.execute();
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
        static Object doIntLikePrimitiveWrapper(VirtualFrame frame, PrimitiveNativeWrapper left, PrimitiveNativeWrapper right, @SuppressWarnings("unused") int op,
                        @Cached("op") @SuppressWarnings("unused") int cachedOp,
                        @Cached("createCallNode(op)") BinaryOpNode callNode,
                        @Cached ToNewRefNode toSulongNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                return toSulongNode.execute(callNode.executeObject(frame, left.getLong(), right.getLong()));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return toSulongNode.execute(getNativeNullNode.execute());
            }
        }

        @Specialization(guards = "cachedOp == op", limit = "MAX_CACHE_SIZE", replaces = "doIntLikePrimitiveWrapper")
        static Object doObject(VirtualFrame frame, Object left, Object right, @SuppressWarnings("unused") int op,
                        @Cached AsPythonObjectNode leftToJava,
                        @Cached AsPythonObjectNode rightToJava,
                        @Cached("op") @SuppressWarnings("unused") int cachedOp,
                        @Cached("createCallNode(op)") BinaryOpNode callNode,
                        @Cached ToNewRefNode toSulongNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
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
                result = getNativeNullNode.execute();
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
        static Object doIntLikePrimitiveWrapper(VirtualFrame frame, PrimitiveNativeWrapper left, PrimitiveNativeWrapper right, @SuppressWarnings("unused") int op,
                        @Cached("op") @SuppressWarnings("unused") int cachedOp,
                        @Cached("createCallNode(op)") LookupAndCallInplaceNode callNode,
                        @Cached ToNewRefNode toSulongNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                return toSulongNode.execute(callNode.execute(frame, left.getLong(), right.getLong()));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return toSulongNode.execute(getNativeNullNode.execute());
            }
        }

        @Specialization(guards = "cachedOp == op", limit = "MAX_CACHE_SIZE", replaces = "doIntLikePrimitiveWrapper")
        static Object doObject(VirtualFrame frame, Object left, Object right, @SuppressWarnings("unused") int op,
                        @Cached AsPythonObjectNode leftToJava,
                        @Cached AsPythonObjectNode rightToJava,
                        @Cached("op") @SuppressWarnings("unused") int cachedOp,
                        @Cached("createCallNode(op)") LookupAndCallInplaceNode callNode,
                        @Cached ToNewRefNode toSulongNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
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
                result = getNativeNullNode.execute();
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
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            try {
                return toSulongNode.execute(ensureCallNode().executeTernary(frame, o1.getLong(), o2.getLong(), o3.getLong()));
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return toSulongNode.execute(getNativeNullNode.execute());
            }
        }

        @Specialization(replaces = "doIntLikePrimitiveWrapper")
        Object doGeneric(VirtualFrame frame, Object o1, Object o2, Object o3,
                        @Cached AsPythonObjectNode o1ToJava,
                        @Cached AsPythonObjectNode o2ToJava,
                        @Cached AsPythonObjectNode o3ToJava,
                        @Cached ToNewRefNode toSulongNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached GetNativeNullNode getNativeNullNode) {
            // still try to avoid expensive materialization of primitives
            Object result;
            try {
                Object o1Value = unwrap(o1, o1ToJava);
                Object o2Value = unwrap(o2, o2ToJava);
                Object o3Value = unwrap(o3, o3ToJava);
                result = ensureCallNode().executeTernary(frame, o1Value, o2Value, o3Value);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                result = getNativeNullNode.execute();
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
}
