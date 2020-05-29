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
import com.oracle.graal.python.nodes.literal.FormatStringLiteralNode.StringPart;
import com.oracle.graal.python.parser.ScopeInfo;
import com.oracle.graal.python.parser.sst.SerializationUtils.SSTId;
import java.io.DataInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;

public final class SSTDeserializer {

    private final DataInputStream stream;
    private final int offsetDelta;
    private int startIndex;
    private int endIndex;
    private ScopeInfo currentScope;

    private final ArrayList<String> stringTable = new ArrayList<>();
    private char[] charBuffer = new char[128]; // buffer for decoding strings

    public SSTDeserializer(DataInputStream read, ScopeInfo globalScope, int offsetDelta) {
        this.stream = read;
        this.currentScope = globalScope;
        this.offsetDelta = offsetDelta;
        this.stringTable.add(null);
    }

    public SSTNode readNode() throws IOException {
        // read the number of the node
        SSTId nodeId = SerializationUtils.getSSTNodeTypeFromId(stream.readByte());
        switch (nodeId) {
            case AndID:
                return readAnd();
            case AnnAssignmentID:
                return readAnnAssignment();
            case AssertID:
                return readAssert();
            case AssignmentID:
                return readAssignment();
            case AugAssignmentID:
                return readAugAssignment();
            case BinaryArithmeticID:
                return readBinaryArithmetic();
            case BlockID:
                return readBlock();
            case BooleanLiteralID:
                return readBooleanLiteral();
            case CallID:
                return readCall();
            case ClassID:
                return readClass();
            case CollectionID:
                return readCollection();
            case ComparisonID:
                return readComparison();
            case DecoratedID:
                return readDecorated();
            case DecoratorID:
                return readDecorator();
            case DelID:
                return readDel();
            case ExceptID:
                return readExcept();
            case ExpressionStatementID:
                return readExpressionStatement();
            case FloatLiteralID:
                return readFloatLiteral();
            case ForComprehensionID:
                return readForComprehension();
            case ForID:
                return readFor();
            case FunctionDefID:
                return readFunctionDef();
            case GetAttributeID:
                return readGetAttribute();
            case IfID:
                return readIf();
            case ImportFromID:
                return readImportFrom();
            case ImportID:
                return readImport();
            case LambdaID:
                return readLambda();
            case NotID:
                return readNot();
            case IntegerLiteralID:
                return readIntegerLiteral();
            case BigIntegerLiteralID:
                return readBigIntegerLiteral();
            case OrID:
                return readOr();
            case RaiseID:
                return readRaise();
            case ReturnID:
                return readReturn();
            case SimpleID:
                return readSimple();
            case SliceID:
                return readSlice();
            case StarID:
                return readStar();
            case RawStringLiteralID:
                return readRawStringLiteral();
            case BytesLiteralID:
                return readBytesLiteral();
            case FormatStringLiteralID:
                return readFormatStringLiteral();
            case SubscriptID:
                return readSubscript();
            case TernaryIfID:
                return readTernaryIf();
            case TryID:
                return readTry();
            case UnaryID:
                return readUnary();
            case VarLookupID:
                return readVarLookup();
            case WhileID:
                return readWhile();
            case WithID:
                return readWith();
            case YieldExpressionID:
                return readYieldExpression();
            case NullID:
                return null;
            default:

        }
        throw new UnsupportedOperationException("Not supported deserialization of id: " + nodeId);
    }

    private int readInt() throws IOException {
        return readInt(stream.readByte());
    }

    private int readInt(byte firstByte) throws IOException {
        switch (firstByte) {
            case (byte) 0x80: // two bytes
                return ((stream.readByte() & 0xFF) << 8) |
                                (stream.readByte() & 0xFF);
            case (byte) 0x81: // three bytes
                return ((stream.readByte() & 0xFF) << 16) |
                                ((stream.readByte() & 0xFF) << 8) |
                                (stream.readByte() & 0xFF);
            case (byte) 0x82: // four bytes
                return ((stream.readByte() & 0xFF) << 24) |
                                ((stream.readByte() & 0xFF) << 16) |
                                ((stream.readByte() & 0xFF) << 8) |
                                (stream.readByte() & 0xFF);
            default: // 7 bits
                return firstByte & 0xFF;
        }
    }

    private long readLong() throws IOException {
        byte firstByte = stream.readByte();
        if (firstByte == (byte) 0x83) {
            return stream.readLong();
        } else {
            return readInt(firstByte);
        }
    }

    private void readPosition() throws IOException {
        int marker = readInt();
        if (marker == 0) {
            // no source section
            startIndex = -1;
            endIndex = -1;
        } else if (marker == 1) {
            // source section is the same as has the previous node
        } else {
            // source section are two numbers
            startIndex = marker - 2;
            int length = readInt();
            endIndex = startIndex + length;
            if (offsetDelta > 0) {
                startIndex -= offsetDelta;
                endIndex -= offsetDelta;
            }
        }
    }

    private String readString() throws IOException {
        int marker = readInt();
        if (marker == 0 || marker == 1) {
            int length = readInt();
            if (charBuffer.length < length) {
                int len = charBuffer.length;
                while (len < length) {
                    len *= 2;
                }
                charBuffer = new char[len];
            }
            char[] chars = charBuffer;
            if (marker == 0) {
                // new simple string
                // one byte per character
                for (int i = 0; i < length; i++) {
                    chars[i] = (char) stream.readUnsignedByte();
                }
            } else {
                assert marker == 1;
                // new complex string
                // read actual char values
                for (int i = 0; i < length; i++) {
                    chars[i] = stream.readChar();
                }
            }
            String result = new String(chars, 0, length);
            stringTable.add(result);
            return result;
        } else {
            // existing string
            return stringTable.get(marker - 2);
        }
    }

    private String[] readStrings() throws IOException {
        int len = readInt();
        String[] values = new String[len];
        for (int i = 0; i < len; i++) {
            values[i] = readString();
        }
        return values;
    }

    private SSTNode readAnd() throws IOException {
        readPosition();
        int startOffset = startIndex;
        int endOffset = endIndex;
        SSTNode[] values = readNodes();
        return new AndSSTNode(values, startOffset, endOffset);
    }

    private SSTNode readAnnAssignment() throws IOException {
        readPosition();
        int startOffset = startIndex;
        int endOffset = endIndex;
        SSTNode type = readNode();
        SSTNode lhs = readNode();
        SSTNode rhs = readNode();
        return new AnnAssignmentSSTNode(lhs, type, rhs, startOffset, endOffset);
    }

    private SSTNode readAssert() throws IOException {
        readPosition();
        int startOffset = startIndex;
        int endOffset = endIndex;
        SSTNode test = readNode();
        SSTNode message = readNode();
        return new AssertSSTNode(test, message, startOffset, endOffset);
    }

    private SSTNode readAssignment() throws IOException {
        readPosition();
        int startOffset = startIndex;
        int endOffset = endIndex;
        SSTNode[] lhs = readNodes();
        SSTNode right = readNode();
        return new AssignmentSSTNode(lhs, right, startOffset, endOffset);
    }

    private SSTNode readAugAssignment() throws IOException {
        readPosition();
        int startOffset = startIndex;
        int endOffset = endIndex;
        SSTNode lhs = readNode();
        SSTNode rhs = readNode();
        String operation = readString();
        return new AugAssignmentSSTNode(lhs, operation, rhs, startOffset, endOffset);
    }

    private SSTNode readBinaryArithmetic() throws IOException {
        readPosition();
        int startOffset = startIndex;
        int endOffset = endIndex;
        SSTNode left = readNode();
        SSTNode right = readNode();
        byte operation = stream.readByte();
        BinaryArithmetic ba = SerializationUtils.getBinaryArithmeticFromId(operation);
        return new BinaryArithmeticSSTNode(ba, left, right, startOffset, endOffset);
    }

    private SSTNode readBlock() throws IOException {
        readPosition();
        int startOffset = startIndex;
        int endOffset = endIndex;
        SSTNode[] statements = readNodes();
        return new BlockSSTNode(statements, startOffset, endOffset);
    }

    private SSTNode readBooleanLiteral() throws IOException {
        readPosition();
        int startOffset = startIndex;
        int endOffset = endIndex;
        boolean value = stream.readBoolean();
        return new BooleanLiteralSSTNode(value, startOffset, endOffset);
    }

    private SSTNode readCall() throws IOException {
        readPosition();
        int startOffset = startIndex;
        int endOffset = endIndex;
        SSTNode target = readNode();
        ArgListBuilder alb = readArgListBuilder();
        return new CallSSTNode(target, alb, startOffset, endOffset);
    }

    private SSTNode readClass() throws IOException {
        readPosition();
        int startOffset = startIndex;
        int endOffset = endIndex;
        String name = readString();
        ArgListBuilder alb = stream.readByte() == 1 ? readArgListBuilder() : null;
        int serializationId = stream.readInt();
        ScopeInfo tmpScope = currentScope;
        ScopeInfo scope = currentScope.getChildScope(serializationId);
        currentScope = scope;
        SSTNode body = readNode();
        currentScope = tmpScope;
        return new ClassSSTNode(scope, name, alb, body, startOffset, endOffset);
    }

    private SSTNode readCollection() throws IOException {
        readPosition();
        int startOffset = startIndex;
        int endOffset = endIndex;
        PythonBuiltinClassType type = SerializationUtils.getPythonBuiltinClassTypeFromId(stream.readByte());
        SSTNode[] values = readNodes();
        return new CollectionSSTNode(values, type, startOffset, endOffset);
    }

    private SSTNode readComparison() throws IOException {
        readPosition();
        int startOffset = startIndex;
        int endOffset = endIndex;
        SSTNode firstValue = readNode();
        SSTNode[] otherValues = readNodes();
        String[] operations = readStrings();
        return new ComparisonSSTNode(firstValue, operations, otherValues, startOffset, endOffset);
    }

    private SSTNode readDecorated() throws IOException {
        readPosition();
        int startOffset = startIndex;
        int endOffset = endIndex;

        SSTNode decorated = readNode();
        int len = readInt();
        DecoratorSSTNode[] decorators = new DecoratorSSTNode[len];
        for (int i = 0; i < len; i++) {
            decorators[i] = (DecoratorSSTNode) readNode();
        }
        return new DecoratedSSTNode(decorators, decorated, startOffset, endOffset);
    }

    private SSTNode readDecorator() throws IOException {
        readPosition();
        int startOffset = startIndex;
        int endOffset = endIndex;

        String name = readString();
        ArgListBuilder args = stream.readByte() == 1 ? readArgListBuilder() : null;
        return new DecoratorSSTNode(name, args, startOffset, endOffset);
    }

    private SSTNode readDel() throws IOException {
        readPosition();
        int startOffset = startIndex;
        int endOffset = endIndex;
        SSTNode[] expreassions = readNodes();
        return new DelSSTNode(expreassions, startOffset, endOffset);
    }

    private SSTNode readExcept() throws IOException {
        readPosition();
        int startOffset = startIndex;
        int endOffset = endIndex;
        SSTNode test = readNode();
        String asName = readString();
        SSTNode body = readNode();
        return new ExceptSSTNode(test, asName, body, startOffset, endOffset);
    }

    private SSTNode readExpressionStatement() throws IOException {
        SSTNode expression = readNode();
        return new ExpressionStatementSSTNode(expression);
    }

    private SSTNode readFloatLiteral() throws IOException {
        readPosition();
        int startOffset = startIndex;
        int endOffset = endIndex;
        boolean imaginary = stream.readBoolean();
        double value = Double.longBitsToDouble(readLong());
        return new FloatLiteralSSTNode(value, imaginary, startOffset, endOffset);
    }

    private SSTNode readForComprehension() throws IOException {
        readPosition();
        int startOffset = startIndex;
        int endOffset = endIndex;
        boolean async = stream.readBoolean();
        SSTNode iterator = readNode();
        int level = readInt();
        int line = readInt();
        SSTNode name = readNode();
        PythonBuiltinClassType type = SerializationUtils.getPythonBuiltinClassTypeFromId(stream.readByte());
        int serializationId = stream.readInt();
        ScopeInfo tmpScope = currentScope;
        ScopeInfo scope = currentScope.getChildScope(serializationId);
        currentScope = scope;
        SSTNode target = readNode();
        SSTNode[] variables = readNodes();
        SSTNode[] conditions = readNodes();
        currentScope = tmpScope;
        return new ForComprehensionSSTNode(scope, async, target, name, variables, iterator, conditions, type, line, level, startOffset, endOffset);
    }

    private SSTNode readFor() throws IOException {
        readPosition();
        int startOffset = startIndex;
        int endOffset = endIndex;
        boolean containsBreak = stream.readBoolean();
        boolean containsContinue = stream.readBoolean();
        SSTNode[] targets = readNodes();
        SSTNode iterator = readNode();
        SSTNode body = readNode();
        SSTNode elseStatement = readNode();
        ForSSTNode forNode = new ForSSTNode(targets, iterator, body, containsContinue, startOffset, endOffset);
        forNode.setContainsBreak(containsBreak);
        forNode.elseStatement = elseStatement;
        return forNode;
    }

    private SSTNode readFunctionDef() throws IOException {
        readPosition();
        int startOffset = startIndex;
        int endOffset = endIndex;
        int serializationId = stream.readInt();
        String name = readString();
        String enclosingClassName = readString();
        ArgDefListBuilder argBuilder = readArguments();
        ScopeInfo tmpScope = currentScope;
        ScopeInfo scope = currentScope.getChildScope(serializationId);
        currentScope = scope;
        SSTNode body = readNode();
        currentScope = tmpScope;
        return new FunctionDefSSTNode(scope, name, enclosingClassName, argBuilder, body, startOffset, endOffset);
    }

    private SSTNode readGetAttribute() throws IOException {
        readPosition();
        int startOffset = startIndex;
        int endOffset = endIndex;
        String name = readString();
        SSTNode receiver = readNode();
        return new GetAttributeSSTNode(receiver, name, startOffset, endOffset);
    }

    private SSTNode readIf() throws IOException {
        readPosition();
        int startOffset = startIndex;
        int endOffset = endIndex;
        SSTNode test = readNode();
        SSTNode thenStatement = readNode();
        SSTNode elseStatemtn = readNode();
        return new IfSSTNode(test, thenStatement, elseStatemtn, startOffset, endOffset);
    }

    private SSTNode readImportFrom() throws IOException {
        readPosition();
        int startOffset = startIndex;
        int endOffset = endIndex;
        String[][] asNames = null;
        if (stream.readByte() == 1) {
            asNames = new String[stream.readByte()][2];
            for (String[] names : asNames) {
                names[0] = readString();
                names[1] = readString();
            }
        }
        String from = readString();
        return new ImportFromSSTNode(currentScope, from, asNames, startOffset, endOffset);
    }

    private SSTNode readImport() throws IOException {
        readPosition();
        int startOffset = startIndex;
        int endOffset = endIndex;
        String name = readString();
        String asNames = readString();
        return new ImportSSTNode(currentScope, name, asNames, startOffset, endOffset);
    }

    private SSTNode readLambda() throws IOException {
        readPosition();
        int startOffset = startIndex;
        int endOffset = endIndex;
        int serializationId = stream.readInt();
        ArgDefListBuilder argBuilder = stream.readByte() == 1 ? readArguments() : null;
        ScopeInfo tmpScope = currentScope;
        ScopeInfo scope = currentScope.getChildScope(serializationId);
        currentScope = scope;
        SSTNode body = readNode();
        currentScope = tmpScope;
        return new LambdaSSTNode(scope, argBuilder, body, startOffset, endOffset);
    }

    private SSTNode readNot() throws IOException {
        readPosition();
        int startOffset = startIndex;
        int endOffset = endIndex;
        SSTNode value = readNode();
        return new NotSSTNode(value, startOffset, endOffset);
    }

    private SSTNode readIntegerLiteral() throws IOException {
        readPosition();
        int startOffset = startIndex;
        int endOffset = endIndex;
        long value = readLong();
        return new NumberLiteralSSTNode.IntegerLiteralSSTNode(value, startOffset, endOffset);
    }

    private SSTNode readBigIntegerLiteral() throws IOException {
        readPosition();
        int startOffset = startIndex;
        int endOffset = endIndex;
        int length = readInt();
        byte[] bytes = new byte[length];
        stream.readFully(bytes);
        return new NumberLiteralSSTNode.BigIntegerLiteralSSTNode(new BigInteger(bytes), startOffset, endOffset);
    }

    private SSTNode readOr() throws IOException {
        readPosition();
        int startOffset = startIndex;
        int endOffset = endIndex;
        SSTNode[] values = readNodes();
        return new OrSSTNode(values, startOffset, endOffset);
    }

    private SSTNode readRaise() throws IOException {
        readPosition();
        int startOffset = startIndex;
        int endOffset = endIndex;
        SSTNode value = readNode();
        SSTNode from = readNode();
        return new RaiseSSTNode(value, from, startOffset, endOffset);
    }

    private SSTNode readReturn() throws IOException {
        readPosition();
        int startOffset = startIndex;
        int endOffset = endIndex;
        SSTNode value = readNode();
        return new ReturnSSTNode(value, startOffset, endOffset);
    }

    private SSTNode readSimple() throws IOException {
        readPosition();
        int startOffset = startIndex;
        int endOffset = endIndex;
        SimpleSSTNode.Type type = SerializationUtils.getSimpleSSTNodeTypeFromId(stream.readByte());
        return new SimpleSSTNode(type, startOffset, endOffset);
    }

    private SSTNode readSlice() throws IOException {
        readPosition();
        int startOffset = startIndex;
        int endOffset = endIndex;
        SSTNode start = readNode();
        SSTNode step = readNode();
        SSTNode stop = readNode();
        return new SliceSSTNode(start, stop, step, startOffset, endOffset);
    }

    private SSTNode readStar() throws IOException {
        readPosition();
        int startOffset = startIndex;
        int endOffset = endIndex;
        SSTNode value = readNode();
        return new StarSSTNode(value, startOffset, endOffset);
    }

    private SSTNode readRawStringLiteral() throws IOException {
        readPosition();
        int startOffset = startIndex;
        int endOffset = endIndex;
        String value = readString();
        return new StringLiteralSSTNode.RawStringLiteralSSTNode(value, startOffset, endOffset);
    }

    private SSTNode readBytesLiteral() throws IOException {
        readPosition();
        int startOffset = startIndex;
        int endOffset = endIndex;
        byte[] value = new byte[readInt()];
        stream.readFully(value);
        return new StringLiteralSSTNode.BytesLiteralSSTNode(value, startOffset, endOffset);
    }

    private SSTNode readFormatStringLiteral() throws IOException {
        readPosition();
        int startOffset = startIndex;
        int endOffset = endIndex;
        StringPart[] value = new StringPart[readInt()];
        for (int i = 0; i < value.length; i++) {
            boolean isFormatString = stream.readBoolean();
            String text = readString();
            value[i] = new StringPart(text, isFormatString);
        }
        return new StringLiteralSSTNode.FormatStringLiteralSSTNode(value, startOffset, endOffset);
    }

    private SSTNode readSubscript() throws IOException {
        readPosition();
        int startOffset = startIndex;
        int endOffset = endIndex;
        SSTNode subscript = readNode();
        SSTNode receiver = readNode();
        return new SubscriptSSTNode(receiver, subscript, startOffset, endOffset);
    }

    private SSTNode readTernaryIf() throws IOException {
        readPosition();
        int startOffset = startIndex;
        int endOffset = endIndex;
        SSTNode test = readNode();
        SSTNode thenStatement = readNode();
        SSTNode elseStatement = readNode();
        return new TernaryIfSSTNode(test, thenStatement, elseStatement, startOffset, endOffset);
    }

    private SSTNode readTry() throws IOException {
        readPosition();
        int startOffset = startIndex;
        int endOffset = endIndex;
        SSTNode body = readNode();
        int len = readInt();
        ExceptSSTNode[] exceptNodes = new ExceptSSTNode[len];
        for (int i = 0; i < len; i++) {
            exceptNodes[i] = (ExceptSSTNode) readNode();
        }
        SSTNode elseStatement = readNode();
        SSTNode finallyStatement = readNode();
        return new TrySSTNode(body, exceptNodes, elseStatement, finallyStatement, startOffset, endOffset);
    }

    private SSTNode readUnary() throws IOException {
        readPosition();
        int startOffset = startIndex;
        int endOffset = endIndex;
        UnaryArithmetic arithmetic = SerializationUtils.getUnaryArithmeticFromId(stream.readByte());
        SSTNode value = readNode();
        return new UnarySSTNode(arithmetic, value, startOffset, endOffset);
    }

    private SSTNode readWhile() throws IOException {
        readPosition();
        int startOffset = startIndex;
        int endOffset = endIndex;
        boolean containsBreak = stream.readBoolean();
        boolean containsContinue = stream.readBoolean();
        SSTNode test = readNode();
        SSTNode body = readNode();
        SSTNode elseStatement = readNode();
        WhileSSTNode whileNode = new WhileSSTNode(test, body, containsContinue, containsBreak, startOffset, endOffset);
        whileNode.setElse(elseStatement);
        return whileNode;
    }

    private SSTNode readWith() throws IOException {
        readPosition();
        int startOffset = startIndex;
        int endOffset = endIndex;
        SSTNode expression = readNode();
        SSTNode target = readNode();
        SSTNode body = readNode();
        return new WithSSTNode(expression, target, body, startOffset, endOffset);
    }

    private SSTNode readYieldExpression() throws IOException {
        readPosition();
        int startOffset = startIndex;
        int endOffset = endIndex;
        boolean isFrom = stream.readBoolean();
        SSTNode value = readNode();
        return new YieldExpressionSSTNode(value, isFrom, startOffset, endOffset);
    }

    private SSTNode readVarLookup() throws IOException {
        readPosition();
        int startOffset = startIndex;
        int endOffset = endIndex;
        // String name = source.getCharacters().subSequence(startOffset, endOffset).toString();
        String name = readString();
        return new VarLookupSSTNode(name, startOffset, endOffset);
    }

    private SSTNode[] readNodes() throws IOException {
        int len = readInt();
        SSTNode[] nodes = new SSTNode[len];
        for (int i = 0; i < len; i++) {
            nodes[i] = readNode();
        }
        return nodes;
    }

    private ArgListBuilder readArgListBuilder() throws IOException {
        int argCount = readInt();
        int namedArgCount = readInt();
        int starArgCount = readInt();
        int kwArgCount = readInt();

        ArgListBuilder alb = new ArgListBuilder(argCount, namedArgCount, starArgCount, kwArgCount);

        for (int i = 0; i < argCount; i++) {
            alb.addArg(readNode());
        }
        for (int i = 0; i < namedArgCount; i++) {
            alb.addNamedArg(readString(), readNode());
        }
        for (int i = 0; i < starArgCount; i++) {
            alb.addStarArg(readNode());
        }
        for (int i = 0; i < kwArgCount; i++) {
            alb.addKwArg(readNode());
        }
        return alb;
    }

    private ArgDefListBuilder readArguments() throws IOException {
        ArgDefListBuilder builder = new ArgDefListBuilder();
        int splatIndex = stream.readByte();
        int posOnlyIndex = stream.readByte();
        int kwargIndex = stream.readByte();
        String name;
        SSTNode type;
        SSTNode value;

        int len = readInt();
        int i;
        for (i = 0; i < len; i++) {
            if (i == posOnlyIndex) {
                builder.markPositionalOnlyIndex();
            }
            name = readString();
            if (name.isEmpty()) {
                name = null;
            }
            type = readNode();
            value = readNode();
            if (value != null) {
                builder.addParam(name, type, value);
            } else {
                if (splatIndex == i) {
                    builder.addSplat(name, type);
                } else {
                    builder.addParam(name, type, null);
                }
            }
        }
        if (i == posOnlyIndex) {
            // handle case like def fn(p1, p2, /, **kw)
            builder.markPositionalOnlyIndex();
        }

        len = i + readInt();
        for (; i < len; i++) {
            name = readString();
            type = readNode();
            value = readNode();
            if (value != null) {
                builder.addParam(name, type, value);
            } else {
                if (splatIndex == i) {
                    builder.addSplat(name, type);
                } else {
                    if (kwargIndex >= len - i - 1) {
                        builder.addKwargs(name, type);
                    } else {
                        builder.addParam(name, type, null);
                    }
                }
            }
        }

        return builder;
    }
}
