/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.parser.sst;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic;
import com.oracle.graal.python.nodes.expression.UnaryArithmetic;

public class SerializationUtils {
    public static byte VERSION = 2;

    public static enum SSTId {
        AndID,
        AnnAssignmentID,
        AssertID,
        AssignmentID,
        AugAssignmentID,
        BinaryArithmeticID,
        BlockID,
        BooleanLiteralID,
        CallID,
        ClassID,
        CollectionID,
        ComparisonID,
        DecoratedID,
        DecoratorID,
        DelID,
        ExceptID,
        ExpressionStatementID,
        FloatLiteralID,
        ForComprehensionID,
        ForID,
        FunctionDefID,
        GetAttributeID,
        IfID,
        ImportFromID,
        ImportID,
        LambdaID,
        NotID,
        IntegerLiteralID,
        BigIntegerLiteralID,
        OrID,
        RaiseID,
        ReturnID,
        SimpleID,
        SliceID,
        StarID,
        RawStringLiteralID,
        BytesLiteralID,
        FormatStringLiteralID,
        SubscriptID,
        TernaryIfID,
        TryID,
        UnaryID,
        VarLookupID,
        WhileID,
        WithID,
        YieldExpressionID,
        NullID; // represent null value in the tree (needed for collections for example)

        public static final SSTId[] VALUES = values();
    }

    // This implementation requires that if the enum is changed,
    // (the ordinal numbers are changed), then the magic nymber in _imp.py has to be changed.
    public static byte getSSTNodeTypeId(SSTId sstId) {
        return (byte) sstId.ordinal();
    }

    public static SSTId getSSTNodeTypeFromId(byte id) {
        return SSTId.VALUES[id];
    }

    public static byte getUnaryArithemticId(UnaryArithmetic ua) {
        switch (ua) {
            case Pos:
                return 1;
            case Neg:
                return 2;
            case Invert:
                return 3;
            default:
                throw new UnsupportedOperationException("Serialization of " + ua.name() + " is not supported.");
        }
    }

    public static UnaryArithmetic getUnaryArithmeticFromId(byte id) {
        switch (id) {
            case 1:
                return UnaryArithmetic.Pos;
            case 2:
                return UnaryArithmetic.Neg;
            case 3:
                return UnaryArithmetic.Invert;
            default:
                throw new UnsupportedOperationException("Deserialization of UnaryArithmetic with id " + id + " is not supported.");
        }
    }

    public static byte getBinaryArithemticId(BinaryArithmetic ba) {
        switch (ba) {
            case Add:
                return 1;
            case Sub:
                return 2;
            case Mul:
                return 3;
            case TrueDiv:
                return 4;
            case FloorDiv:
                return 5;
            case Mod:
                return 6;
            case LShift:
                return 7;
            case RShift:
                return 8;
            case And:
                return 9;
            case Or:
                return 10;
            case Xor:
                return 11;
            case MatMul:
                return 12;
            case Pow:
                return 13;
            default:
                throw new UnsupportedOperationException("Serialization of " + ba.name() + " is not supported.");
        }
    }

    public static BinaryArithmetic getBinaryArithmeticFromId(byte id) {
        switch (id) {
            case 1:
                return BinaryArithmetic.Add;
            case 2:
                return BinaryArithmetic.Sub;
            case 3:
                return BinaryArithmetic.Mul;
            case 4:
                return BinaryArithmetic.TrueDiv;
            case 5:
                return BinaryArithmetic.FloorDiv;
            case 6:
                return BinaryArithmetic.Mod;
            case 7:
                return BinaryArithmetic.LShift;
            case 8:
                return BinaryArithmetic.RShift;
            case 9:
                return BinaryArithmetic.And;
            case 10:
                return BinaryArithmetic.Or;
            case 11:
                return BinaryArithmetic.Xor;
            case 12:
                return BinaryArithmetic.MatMul;
            case 13:
                return BinaryArithmetic.Pow;
            default:
                throw new UnsupportedOperationException("Deserialization of BinaryArithmetic with id " + id + " is not supported.");
        }
    }

    public static byte getPythonBuiltinClassTypeId(PythonBuiltinClassType pt) {
        switch (pt) {
            case PDict:
                return 1;
            case PGenerator:
                return 2;
            case PList:
                return 3;
            case PSet:
                return 4;
            case PTuple:
                return 5;
            default:
                throw new UnsupportedOperationException("Serialization of " + pt.name() + " is not supported.");
        }
    }

    public static PythonBuiltinClassType getPythonBuiltinClassTypeFromId(byte id) {
        switch (id) {
            case 1:
                return PythonBuiltinClassType.PDict;
            case 2:
                return PythonBuiltinClassType.PGenerator;
            case 3:
                return PythonBuiltinClassType.PList;
            case 4:
                return PythonBuiltinClassType.PSet;
            case 5:
                return PythonBuiltinClassType.PTuple;
            default:
                throw new UnsupportedOperationException("Deserialization of PythonBuiltinClassType with id " + id + " is not supported.");
        }
    }

    public static byte getSimpleSSTNodeTypeId(SimpleSSTNode.Type st) {
        switch (st) {
            case BREAK:
                return 1;
            case CONTINUE:
                return 2;
            case PASS:
                return 3;
            case NONE:
                return 4;
            case ELLIPSIS:
                return 5;
            case EMPTY:
                return 6;
            default:
                throw new UnsupportedOperationException("Serialization of " + st.name() + " is not supported.");
        }
    }

    public static SimpleSSTNode.Type getSimpleSSTNodeTypeFromId(byte id) {
        switch (id) {
            case 1:
                return SimpleSSTNode.Type.BREAK;
            case 2:
                return SimpleSSTNode.Type.CONTINUE;
            case 3:
                return SimpleSSTNode.Type.PASS;
            case 4:
                return SimpleSSTNode.Type.NONE;
            case 5:
                return SimpleSSTNode.Type.ELLIPSIS;
            case 6:
                return SimpleSSTNode.Type.EMPTY;
            default:
                throw new UnsupportedOperationException("Deserialization SimpleSSTNode.Type with id " + id + " is not supported.");
        }
    }

}
