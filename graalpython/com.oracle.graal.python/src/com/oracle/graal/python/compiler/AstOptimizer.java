/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.compiler;

import java.math.BigInteger;
import java.util.Arrays;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.ToArrayNode;
import com.oracle.graal.python.builtins.objects.complex.PComplex;
import com.oracle.graal.python.builtins.objects.ellipsis.PEllipsis;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.compiler.bytecode_dsl.BaseBytecodeDSLVisitor;
import com.oracle.graal.python.lib.PyNumberAddNode;
import com.oracle.graal.python.lib.PyNumberAndNode;
import com.oracle.graal.python.lib.PyNumberFloorDivideNode;
import com.oracle.graal.python.lib.PyNumberInvertNode;
import com.oracle.graal.python.lib.PyNumberLshiftNode;
import com.oracle.graal.python.lib.PyNumberMultiplyNode;
import com.oracle.graal.python.lib.PyNumberNegativeNode;
import com.oracle.graal.python.lib.PyNumberOrNode;
import com.oracle.graal.python.lib.PyNumberPositiveNode;
import com.oracle.graal.python.lib.PyNumberPowerNode;
import com.oracle.graal.python.lib.PyNumberRemainderNode;
import com.oracle.graal.python.lib.PyNumberRshiftNode;
import com.oracle.graal.python.lib.PyNumberSubtractNode;
import com.oracle.graal.python.lib.PyNumberTrueDivideNode;
import com.oracle.graal.python.lib.PyNumberXorNode;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.pegparser.sst.ArgTy;
import com.oracle.graal.python.pegparser.sst.ArgumentsTy;
import com.oracle.graal.python.pegparser.sst.CmpOpTy;
import com.oracle.graal.python.pegparser.sst.ComprehensionTy;
import com.oracle.graal.python.pegparser.sst.ConstantValue;
import com.oracle.graal.python.pegparser.sst.ExprContextTy;
import com.oracle.graal.python.pegparser.sst.ExprTy;
import com.oracle.graal.python.pegparser.sst.ModTy;
import com.oracle.graal.python.pegparser.sst.OperatorTy;
import com.oracle.graal.python.pegparser.sst.SSTNode;
import com.oracle.graal.python.pegparser.sst.StmtTy;
import com.oracle.graal.python.pegparser.sst.TypeParamTy;
import com.oracle.graal.python.pegparser.sst.UnaryOpTy;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.strings.TruffleString;

/** The common AST optimizer used before bytecode generation and for {@code PyCF_OPTIMIZED_AST}. */
public final class AstOptimizer implements BaseBytecodeDSLVisitor<ExprTy> {
    private static final int MAX_INT_SIZE = 128;
    private static final int MAX_COLLECTION_SIZE = 256;

    private final int optimizationLevel;
    private final boolean futureAnnotations;

    private AstOptimizer(int optimizationLevel, boolean futureAnnotations) {
        this.optimizationLevel = optimizationLevel;
        this.futureAnnotations = futureAnnotations;
    }

    @TruffleBoundary
    public static void optimize(ModTy mod, int optimizationLevel, boolean futureAnnotations) {
        mod.accept(new AstOptimizer(optimizationLevel, futureAnnotations || hasFutureAnnotations(mod)));
    }

    @Override
    public ExprTy defaultValue(SSTNode node) {
        return node instanceof ExprTy expression ? expression : null;
    }

    @Override
    public ExprTy visitExpr(ExprTy node) {
        return node == null ? null : node.accept(this);
    }

    @Override
    public ExprTy visit(ModTy.FunctionType node) {
        return null;
    }

    @Override
    public ExprTy visit(ExprTy.Name node) {
        if (node.context == ExprContextTy.Load && node.id.equals("__debug__")) {
            return constant(node, ConstantValue.ofBoolean(optimizationLevel == 0));
        }
        return node;
    }

    @Override
    public ExprTy visit(ExprTy.BinOp node) {
        ExprTy optimizedFormat = tryOptimizeFormat(node);
        if (optimizedFormat != null) {
            return visitExpr(optimizedFormat);
        }
        BaseBytecodeDSLVisitor.super.visit(node);
        ConstantValue left = getConstant(node.left);
        ConstantValue right = getConstant(node.right);
        if (left != null && right != null) {
            try {
                Object leftObject = PythonUtils.pythonObjectFromConstantValue(left);
                Object rightObject = PythonUtils.pythonObjectFromConstantValue(right);
                Object result = foldBinOp(node.op, leftObject, rightObject);
                ConstantValue value = asConstantValue(result);
                if (value != null) {
                    return constant(node, value);
                }
            } catch (PException e) {
                // CPython ignores ordinary errors raised while attempting constant folding.
            }
        }
        return node;
    }

    @Override
    public ExprTy visit(ExprTy.UnaryOp node) {
        BaseBytecodeDSLVisitor.super.visit(node);
        if (node.op == UnaryOpTy.Not && node.operand instanceof ExprTy.Compare compare && compare.ops.length == 1) {
            CmpOpTy inverted = invertComparison(compare.ops[0]);
            if (inverted != null) {
                return new ExprTy.Compare(compare.left, new CmpOpTy[]{inverted}, compare.comparators, compare.getSourceRange());
            }
        }
        ConstantValue operand = getConstant(node.operand);
        if (operand != null) {
            try {
                Object result = foldUnaryOp(node.op, PythonUtils.pythonObjectFromConstantValue(operand));
                ConstantValue value = asConstantValue(result);
                if (value != null) {
                    return constant(node, value);
                }
            } catch (PException e) {
                // See visit(BinOp).
            }
        }
        return node;
    }

    @Override
    public ExprTy visit(ExprTy.Tuple node) {
        BaseBytecodeDSLVisitor.super.visit(node);
        if (node.context == ExprContextTy.Load) {
            ConstantValue[] values = constantElements(node.elements);
            if (values != null) {
                return constant(node, ConstantValue.ofTuple(values));
            }
        }
        return node;
    }

    @Override
    public ExprTy visit(ExprTy.Subscript node) {
        BaseBytecodeDSLVisitor.super.visit(node);
        ConstantValue value = getConstant(node.value);
        ConstantValue slice = getConstant(node.slice);
        if (node.context == ExprContextTy.Load && value != null && slice != null) {
            try {
                Object result = PyObjectGetItem.getUncached().execute(null, null, PythonUtils.pythonObjectFromConstantValue(value), PythonUtils.pythonObjectFromConstantValue(slice));
                ConstantValue constant = asConstantValue(result);
                if (constant != null) {
                    return constant(node, constant);
                }
            } catch (PException e) {
                // See visit(BinOp).
            }
        }
        return node;
    }

    @Override
    public ExprTy visit(ExprTy.Compare node) {
        BaseBytecodeDSLVisitor.super.visit(node);
        if (node.ops.length > 0 && (node.ops[node.ops.length - 1] == CmpOpTy.In || node.ops[node.ops.length - 1] == CmpOpTy.NotIn)) {
            int last = node.comparators.length - 1;
            node.comparators[last] = optimizeIterable(node.comparators[last]);
        }
        return node;
    }

    @Override
    public ExprTy visit(StmtTy.For node) {
        BaseBytecodeDSLVisitor.super.visit(node);
        node.iter = optimizeIterable(node.iter);
        return null;
    }

    @Override
    public ExprTy visit(StmtTy.FunctionDef node) {
        visitSequence(node.typeParams);
        visitNode(node.args);
        visitSequence(node.body);
        visitSequence(node.decoratorList);
        if (!futureAnnotations) {
            node.returns = visitExpr(node.returns);
        }
        return null;
    }

    @Override
    public ExprTy visit(StmtTy.AsyncFunctionDef node) {
        visitSequence(node.typeParams);
        visitNode(node.args);
        visitSequence(node.body);
        visitSequence(node.decoratorList);
        if (!futureAnnotations) {
            node.returns = visitExpr(node.returns);
        }
        return null;
    }

    @Override
    public ExprTy visit(StmtTy.ClassDef node) {
        BaseBytecodeDSLVisitor.super.visit(node);
        visitSequence(node.typeParams);
        return null;
    }

    @Override
    public ExprTy visit(StmtTy.AnnAssign node) {
        node.target = visitExpr(node.target);
        if (!futureAnnotations) {
            node.annotation = visitExpr(node.annotation);
        }
        node.value = visitExpr(node.value);
        return null;
    }

    @Override
    public ExprTy visit(ArgumentsTy node) {
        visitSequence(node.posOnlyArgs);
        visitSequence(node.args);
        visitNode(node.varArg);
        visitSequence(node.kwOnlyArgs);
        visitSequence(node.kwDefaults);
        visitNode(node.kwArg);
        visitSequence(node.defaults);
        return null;
    }

    @Override
    public ExprTy visit(ArgTy node) {
        if (!futureAnnotations) {
            node.annotation = visitExpr(node.annotation);
        }
        return null;
    }

    @Override
    public ExprTy visit(StmtTy.TryStar node) {
        visitSequence(node.body);
        visitSequence(node.handlers);
        visitSequence(node.orElse);
        visitSequence(node.finalBody);
        return null;
    }

    @Override
    public ExprTy visit(StmtTy.TypeAlias node) {
        node.name = visitExpr(node.name);
        visitSequence(node.typeParams);
        node.value = visitExpr(node.value);
        return null;
    }

    @Override
    public ExprTy visit(ComprehensionTy node) {
        BaseBytecodeDSLVisitor.super.visit(node);
        node.iter = optimizeIterable(node.iter);
        return null;
    }

    @Override
    public ExprTy visit(TypeParamTy.TypeVar node) {
        node.bound = visitExpr(node.bound);
        node.defaultValue = visitExpr(node.defaultValue);
        return null;
    }

    @Override
    public ExprTy visit(TypeParamTy.ParamSpec node) {
        node.defaultValue = visitExpr(node.defaultValue);
        return null;
    }

    @Override
    public ExprTy visit(TypeParamTy.TypeVarTuple node) {
        node.defaultValue = visitExpr(node.defaultValue);
        return null;
    }

    private static ExprTy optimizeIterable(ExprTy expression) {
        if (expression instanceof ExprTy.List list) {
            ConstantValue[] constants = constantElements(list.elements);
            if (constants != null) {
                return constant(expression, ConstantValue.ofTuple(constants));
            } else {
                return new ExprTy.Tuple(list.elements, list.context, list.getSourceRange());
            }
        } else if (expression instanceof ExprTy.Set set) {
            ConstantValue[] constants = constantElements(set.elements);
            if (constants != null) {
                return constant(expression, ConstantValue.ofFrozenset(constants));
            }
        }
        return expression;
    }

    private static ExprTy tryOptimizeFormat(ExprTy.BinOp node) {
        ConstantValue format = getConstant(node.left);
        if (node.op != OperatorTy.Mod || format == null || !(node.right instanceof ExprTy.Tuple tuple)) {
            return null;
        }
        if (format.kind != ConstantValue.Kind.CODEPOINTS || tuple.elements.length != 1) {
            return null;
        }
        String spec = format.getCodePoints().toJavaString();
        if (!(spec.equals("%s") || spec.equals("%r") || spec.equals("%a"))) {
            return null;
        }
        ExprTy formatted = new ExprTy.FormattedValue(tuple.elements[0], spec.charAt(1), null, tuple.elements[0].getSourceRange());
        return new ExprTy.JoinedStr(new ExprTy[]{formatted}, node.getSourceRange());
    }

    private static ConstantValue[] constantElements(ExprTy[] elements) {
        ConstantValue[] values = new ConstantValue[elements.length];
        for (int i = 0; i < elements.length; i++) {
            values[i] = getConstant(elements[i]);
            if (values[i] == null) {
                return null;
            }
        }
        return values;
    }

    private static boolean hasFutureAnnotations(ModTy mod) {
        StmtTy[] statements;
        if (mod instanceof ModTy.Module module) {
            statements = module.body;
        } else if (mod instanceof ModTy.Interactive interactive) {
            statements = interactive.body;
        } else {
            return false;
        }
        if (statements == null) {
            return false;
        }
        for (StmtTy statement : statements) {
            if (statement instanceof StmtTy.ImportFrom importFrom && "__future__".equals(importFrom.module)) {
                if (importFrom.names != null) {
                    for (var alias : importFrom.names) {
                        if ("annotations".equals(alias.name)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static ConstantValue getConstant(ExprTy expression) {
        return expression instanceof ExprTy.Constant constant ? constant.value : null;
    }

    private static ExprTy.Constant constant(SSTNode node, ConstantValue value) {
        return new ExprTy.Constant(value, null, node.getSourceRange());
    }

    private static CmpOpTy invertComparison(CmpOpTy op) {
        return switch (op) {
            case Is -> CmpOpTy.IsNot;
            case IsNot -> CmpOpTy.Is;
            case In -> CmpOpTy.NotIn;
            case NotIn -> CmpOpTy.In;
            default -> null;
        };
    }

    private static Object foldBinOp(OperatorTy op, Object left, Object right) {
        return switch (op) {
            case Add -> PyNumberAddNode.getUncached().execute(null, left, right);
            case Sub -> PyNumberSubtractNode.getUncached().execute(null, left, right);
            case Mult -> isSafeMultiply(left, right) ? PyNumberMultiplyNode.getUncached().execute(null, left, right) : PNone.NO_VALUE;
            case Div -> PyNumberTrueDivideNode.getUncached().execute(null, left, right);
            case FloorDiv -> PyNumberFloorDivideNode.getUncached().execute(null, left, right);
            case Mod -> left instanceof TruffleString || left instanceof PBytes ? PNone.NO_VALUE : PyNumberRemainderNode.getUncached().execute(null, left, right);
            case Pow -> isSafePower(left, right) ? PyNumberPowerNode.getUncached().execute(null, left, right) : PNone.NO_VALUE;
            case LShift -> isSafeLshift(left, right) ? PyNumberLshiftNode.getUncached().execute(null, left, right) : PNone.NO_VALUE;
            case RShift -> PyNumberRshiftNode.getUncached().execute(null, left, right);
            case BitOr -> PyNumberOrNode.getUncached().execute(null, left, right);
            case BitXor -> PyNumberXorNode.getUncached().execute(null, left, right);
            case BitAnd -> PyNumberAndNode.getUncached().execute(null, left, right);
            case MatMult -> PNone.NO_VALUE;
        };
    }

    private static Object foldUnaryOp(UnaryOpTy op, Object operand) {
        return switch (op) {
            case Invert -> PyNumberInvertNode.getUncached().execute(null, operand);
            case Not -> !PyObjectIsTrueNode.executeUncached(operand);
            case UAdd -> PyNumberPositiveNode.getUncached().execute(null, operand);
            case USub -> PyNumberNegativeNode.getUncached().execute(null, operand);
        };
    }

    private static ConstantValue asConstantValue(Object value) {
        if (value == PNone.NO_VALUE) {
            return null;
        } else if (value == PNone.NONE) {
            return ConstantValue.NONE;
        } else if (value == PEllipsis.INSTANCE) {
            return ConstantValue.ELLIPSIS;
        } else if (value instanceof Boolean bool) {
            return ConstantValue.ofBoolean(bool);
        } else if (value instanceof Integer integer) {
            return ConstantValue.ofLong(integer.longValue());
        } else if (value instanceof Long longValue) {
            return ConstantValue.ofLong(longValue);
        } else if (value instanceof PInt integer) {
            return ConstantValue.ofBigInteger(integer.getValue());
        } else if (value instanceof Double doubleValue) {
            return ConstantValue.ofDouble(doubleValue);
        } else if (value instanceof PComplex complex) {
            return ConstantValue.ofComplex(complex.getReal(), complex.getImag());
        } else if (value instanceof TruffleString string) {
            return ConstantValue.ofCodePoints(PythonUtils.truffleStringToCodePoints(string));
        } else if (value instanceof PBytes bytes && bytes.getSequenceStorage() instanceof ByteSequenceStorage storage) {
            return ConstantValue.ofBytes(Arrays.copyOf(storage.getInternalByteArray(), storage.length()));
        } else if (value instanceof PTuple tuple) {
            Object[] objects = ToArrayNode.executeUncached(tuple.getSequenceStorage());
            ConstantValue[] values = new ConstantValue[objects.length];
            for (int i = 0; i < values.length; i++) {
                values[i] = asConstantValue(objects[i]);
                if (values[i] == null) {
                    return null;
                }
            }
            return ConstantValue.ofTuple(values);
        }
        return null;
    }

    private static boolean isSafeMultiply(Object left, Object right) {
        BigInteger leftInt = asBigInteger(left);
        BigInteger rightInt = asBigInteger(right);
        if (leftInt != null && rightInt != null && leftInt.signum() != 0 && rightInt.signum() != 0) {
            return leftInt.bitLength() + rightInt.bitLength() <= MAX_INT_SIZE;
        }
        if (leftInt != null) {
            return isSafeSequenceRepeat(leftInt, right);
        }
        if (rightInt != null) {
            return isSafeSequenceRepeat(rightInt, left);
        }
        return true;
    }

    private static boolean isSafeSequenceRepeat(BigInteger count, Object sequence) {
        int size;
        if (sequence instanceof PTuple tuple) {
            size = tuple.getSequenceStorage().length();
        } else if (sequence instanceof TruffleString string) {
            size = string.codePointLengthUncached(PythonUtils.TS_ENCODING);
        } else if (sequence instanceof PBytes bytes) {
            size = bytes.getSequenceStorage().length();
        } else {
            return true;
        }
        return count.signum() < 0 || size == 0 || count.compareTo(BigInteger.valueOf(MAX_COLLECTION_SIZE / size)) <= 0;
    }

    private static boolean isSafePower(Object left, Object right) {
        BigInteger base = asBigInteger(left);
        BigInteger exponent = asBigInteger(right);
        return base == null || exponent == null || base.signum() == 0 || exponent.signum() <= 0 ||
                        exponent.bitLength() <= 31 && exponent.intValue() <= MAX_INT_SIZE && base.bitLength() <= MAX_INT_SIZE / exponent.intValue();
    }

    private static boolean isSafeLshift(Object left, Object right) {
        BigInteger value = asBigInteger(left);
        BigInteger shift = asBigInteger(right);
        return value == null || shift == null || value.signum() == 0 || shift.signum() == 0 ||
                        shift.signum() > 0 && shift.bitLength() <= 31 && shift.intValue() <= MAX_INT_SIZE && value.bitLength() <= MAX_INT_SIZE - shift.intValue();
    }

    private static BigInteger asBigInteger(Object value) {
        if (value instanceof Integer integer) {
            return BigInteger.valueOf(integer.longValue());
        } else if (value instanceof Long longValue) {
            return BigInteger.valueOf(longValue);
        } else if (value instanceof PInt integer) {
            return integer.getValue();
        } else if (value instanceof Boolean bool) {
            return bool ? BigInteger.ONE : BigInteger.ZERO;
        }
        return null;
    }
}
