/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.CompilerDirectives.shouldNotReachHere;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.pegparser.ParserCallbacks;
import com.oracle.graal.python.pegparser.ParserCallbacks.ErrorType;
import com.oracle.graal.python.pegparser.ParserCallbacks.WarningType;
import com.oracle.graal.python.pegparser.sst.ArgTy;
import com.oracle.graal.python.pegparser.sst.ArgumentsTy;
import com.oracle.graal.python.pegparser.sst.CmpOpTy;
import com.oracle.graal.python.pegparser.sst.ConstantValue;
import com.oracle.graal.python.pegparser.sst.ConstantValue.Kind;
import com.oracle.graal.python.pegparser.sst.ExprContextTy;
import com.oracle.graal.python.pegparser.sst.ExprTy;
import com.oracle.graal.python.pegparser.sst.ExprTy.Constant;
import com.oracle.graal.python.pegparser.sst.SSTNode;
import com.oracle.graal.python.pegparser.tokenizer.SourceRange;

public class SSTUtils {
    public static void checkForbiddenArgs(ParserCallbacks parserCallbacks, SourceRange currentLocation, ArgumentsTy args) {
        if (args != null) {
            if (args.posOnlyArgs != null) {
                for (ArgTy arg : args.posOnlyArgs) {
                    checkForbiddenName(parserCallbacks, currentLocation, arg.arg, ExprContextTy.Store);
                }
            }
            if (args.args != null) {
                for (ArgTy arg : args.args) {
                    checkForbiddenName(parserCallbacks, currentLocation, arg.arg, ExprContextTy.Store);
                }
            }
            if (args.kwOnlyArgs != null) {
                for (ArgTy arg : args.kwOnlyArgs) {
                    checkForbiddenName(parserCallbacks, currentLocation, arg.arg, ExprContextTy.Store);
                }
            }
            if (args.varArg != null) {
                checkForbiddenName(parserCallbacks, currentLocation, args.varArg.arg, ExprContextTy.Store);
            }
            if (args.kwArg != null) {
                checkForbiddenName(parserCallbacks, currentLocation, args.kwArg.arg, ExprContextTy.Store);
            }
        }
    }

    public static void checkForbiddenName(ParserCallbacks parserCallbacks, SourceRange currentLocation, String id, ExprContextTy context) {
        if (context == ExprContextTy.Store) {
            if (id.equals("__debug__")) {
                throw parserCallbacks.onError(ErrorType.Syntax, currentLocation, "cannot assign to __debug__");
            }
        }
        if (context == ExprContextTy.Del) {
            if (id.equals("__debug__")) {
                throw parserCallbacks.onError(ErrorType.Syntax, currentLocation, "cannot delete __debug__");
            }
        }
    }

    public static void checkSubscripter(ParserCallbacks parserCallbacks, ExprTy e) {
        if (e instanceof ExprTy.Constant) {
            switch (((ExprTy.Constant) e).value.kind) {
                case NONE:
                case ELLIPSIS:
                case BOOLEAN:
                case LONG:
                case BIGINTEGER:
                case DOUBLE:
                case COMPLEX:
                case FROZENSET:
                    break;
                default:
                    return;
            }
        } else if (!(e instanceof ExprTy.Set || e instanceof ExprTy.SetComp || e instanceof ExprTy.GeneratorExp || e instanceof ExprTy.Lambda)) {
            return;
        }
        warn(parserCallbacks, e, "'%s' object is not subscriptable; perhaps you missed a comma?", SSTUtils.inferType(e).getName());
    }

    public static void checkIndex(ParserCallbacks parserCallbacks, ExprTy e, ExprTy s) {
        PythonBuiltinClassType indexType = SSTUtils.inferType(s);
        if (indexType == null || indexType == PythonBuiltinClassType.Boolean || indexType == PythonBuiltinClassType.PInt || indexType == PythonBuiltinClassType.PSlice) {
            return;
        }
        if (e instanceof ExprTy.Constant) {
            switch (((ExprTy.Constant) e).value.kind) {
                case CODEPOINTS:
                case BYTES:
                case TUPLE:
                    break;
                default:
                    return;
            }
        } else if (!(e instanceof ExprTy.Tuple || e instanceof ExprTy.List || e instanceof ExprTy.ListComp || e instanceof ExprTy.JoinedStr || e instanceof ExprTy.FormattedValue)) {
            return;
        }
        warn(parserCallbacks, e, "%s indices must be integers or slices, not %s; perhaps you missed a comma?", SSTUtils.inferType(e).getName(), indexType.getName());
    }

    public static void checkCompare(ParserCallbacks parserCallbacks, ExprTy.Compare node) {
        ExprTy leftExpr = node.left;
        boolean left = checkIsArg(leftExpr);
        int n = node.ops == null ? 0 : node.ops.length;
        for (int i = 0; i < n; ++i) {
            CmpOpTy op = node.ops[i];
            ExprTy rightExpr = node.comparators[i];
            boolean right = checkIsArg(rightExpr);
            if (op == CmpOpTy.Is || op == CmpOpTy.IsNot) {
                if (!right || !left) {
                    ExprTy literal = !left ? leftExpr : rightExpr;
                    warn(parserCallbacks, node, op == CmpOpTy.Is ? "\"is\" with '%s' literal. Did you mean \"==\"?" : "\"is not\" with '%s' literal. Did you mean \"!=\"?",
                                    SSTUtils.inferType(literal).getName());
                }
            }
            left = right;
            leftExpr = rightExpr;
        }
    }

    private static boolean checkIsArg(ExprTy e) {
        if (e instanceof ExprTy.Constant) {
            ConstantValue.Kind kind = ((Constant) e).value.kind;
            return kind == Kind.NONE || kind == Kind.BOOLEAN || kind == Kind.ELLIPSIS;
        }
        return true;
    }

    public static void checkCaller(ParserCallbacks parserCallbacks, ExprTy e) {
        if (e instanceof ExprTy.Constant || e instanceof ExprTy.Tuple || e instanceof ExprTy.List || e instanceof ExprTy.ListComp || e instanceof ExprTy.Dict || e instanceof ExprTy.DictComp ||
                        e instanceof ExprTy.Set || e instanceof ExprTy.SetComp || e instanceof ExprTy.GeneratorExp || e instanceof ExprTy.JoinedStr || e instanceof ExprTy.FormattedValue) {
            warn(parserCallbacks, e, "'%s' object is not callable; perhaps you missed a comma?", SSTUtils.inferType(e).getName());
        }
    }

    public static PythonBuiltinClassType inferType(ExprTy e) {
        if (e instanceof ExprTy.Tuple) {
            return PythonBuiltinClassType.PTuple;
        }
        if (e instanceof ExprTy.List || e instanceof ExprTy.ListComp) {
            return PythonBuiltinClassType.PList;
        }
        if (e instanceof ExprTy.Dict || e instanceof ExprTy.DictComp) {
            return PythonBuiltinClassType.PDict;
        }
        if (e instanceof ExprTy.Set || e instanceof ExprTy.SetComp) {
            return PythonBuiltinClassType.PSet;
        }
        if (e instanceof ExprTy.GeneratorExp) {
            return PythonBuiltinClassType.PGenerator;
        }
        if (e instanceof ExprTy.Lambda) {
            return PythonBuiltinClassType.PFunction;
        }
        if (e instanceof ExprTy.JoinedStr || e instanceof ExprTy.FormattedValue) {
            return PythonBuiltinClassType.PString;
        }
        if (e instanceof ExprTy.Constant) {
            switch (((ExprTy.Constant) e).value.kind) {
                case NONE:
                    return PythonBuiltinClassType.PNone;
                case ELLIPSIS:
                    return PythonBuiltinClassType.PEllipsis;
                case BOOLEAN:
                    return PythonBuiltinClassType.Boolean;
                case DOUBLE:
                    return PythonBuiltinClassType.PFloat;
                case COMPLEX:
                    return PythonBuiltinClassType.PComplex;
                case LONG:
                case BIGINTEGER:
                    return PythonBuiltinClassType.PInt;
                case CODEPOINTS:
                    return PythonBuiltinClassType.PString;
                case BYTES:
                    return PythonBuiltinClassType.PBytes;
                case TUPLE:
                    return PythonBuiltinClassType.PTuple;
                case FROZENSET:
                    return PythonBuiltinClassType.PFrozenSet;
                default:
                    throw shouldNotReachHere("Invalid ConstantValue kind: " + ((ExprTy.Constant) e).value.kind);
            }
        }
        return null;
    }

    private static void warn(ParserCallbacks parserCallbacks, SSTNode node, String message, Object... arguments) {
        parserCallbacks.onWarning(WarningType.Syntax, node.getSourceRange(), message, arguments);
    }
}
