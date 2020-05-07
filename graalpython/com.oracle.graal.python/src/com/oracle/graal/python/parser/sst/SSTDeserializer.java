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
import com.oracle.graal.python.parser.ScopeInfo;
import com.oracle.graal.python.parser.sst.SerializationUtils.SSTId;
import java.io.DataInputStream;
import java.io.IOException;

public class SSTDeserializer {

    private final DataInputStream stream;
    private final int offsetDelta;
    private int startIndex;
    private int endIndex;
    private ScopeInfo currentScope;

    public SSTDeserializer(DataInputStream read, ScopeInfo globalScope, int offsetDelta) {
        this.stream = read;
        currentScope = globalScope;
        this.offsetDelta = offsetDelta;
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
            case NumberLiteralID:
                return readNumberLiteral();
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
            case StringLiteralID:
                return readStringLiteral();
            case SubscriptID:
                return readSubscript();
            case TernaryArithmeticID:
                return readTernaryArithmetic();
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

    private int readInt(byte byteLen) throws IOException {
        switch (byteLen) {
            case 1:
                return stream.readByte() & 0xFF;
            case 2:
                return ((stream.readByte() & 0xFF) << 8) |
                                (stream.readByte() & 0xFF);
            case 3:
                return ((stream.readByte() & 0xFF) << 16) |
                                ((stream.readByte() & 0xFF) << 8) |
                                (stream.readByte() & 0xFF);
            case 4:
                return ((stream.readByte() & 0xFF) << 24) |
                                ((stream.readByte() & 0xFF) << 16) |
                                ((stream.readByte() & 0xFF) << 8) |
                                (stream.readByte() & 0xFF);
            default:
                throw new UnsupportedOperationException("Unsupportted length of integer: " + byteLen);
        }
    }

    private void readPosition() throws IOException {
        byte kind = stream.readByte();
        switch (kind) {
            case 0:
                // no source section
                startIndex = -1;
                endIndex = -1;
                break;
            case 10:
                // source section is the same as has the previous node
                break;
            default:
                // source section are two numbers
                startIndex = readInt(kind);
                endIndex = readInt(stream.readByte());
                if (offsetDelta > 0) {
                    startIndex -= offsetDelta;
                    endIndex -= offsetDelta;
                }
        }
    }

    private String[] readStrings() throws IOException {
        int len = readInt(stream.readByte());
        String[] values = new String[len];
        for (int i = 0; i < len; i++) {
            values[i] = stream.readUTF();
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
        SSTNode message = stream.readByte() == 1 ? readNode() : null;
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
        char[] operation = new char[stream.readByte()];
        for (int i = 0; i < operation.length; i++) {
            operation[i] = stream.readChar();
        }
        return new AugAssignmentSSTNode(lhs, new String(operation), rhs, startOffset, endOffset);
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
        int len = readInt(stream.readByte());
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
        SSTNode test = stream.readByte() == 1 ? readNode() : null;
        String asName = stream.readByte() == 1 ? stream.readUTF() : null;
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
        String value = readString();
        return new FloatLiteralSSTNode(value, imaginary, startOffset, endOffset);
    }

    private SSTNode readForComprehension() throws IOException {
        readPosition();
        int startOffset = startIndex;
        int endOffset = endIndex;
        boolean async = stream.readBoolean();
        SSTNode iterator = readNode();
        int level = readInt(stream.readByte());
        int line = readInt(stream.readByte());
        SSTNode name = stream.readByte() == 1 ? readNode() : null;
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
        SSTNode elseStatement = stream.readByte() == 1 ? readNode() : null;
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
        String name = stream.readUTF();
        String enclosingClassName = stream.readByte() == 1 ? stream.readUTF() : null;
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
        String name = stream.readUTF();
        SSTNode receiver = readNode();
        return new GetAttributeSSTNode(receiver, name, startOffset, endOffset);
    }

    private SSTNode readIf() throws IOException {
        readPosition();
        int startOffset = startIndex;
        int endOffset = endIndex;
        SSTNode test = readNode();
        SSTNode thenStatement = readNode();
        SSTNode elseStatemtn = stream.readByte() == 1 ? readNode() : null;
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
                names[0] = stream.readUTF();
                names[1] = stream.readByte() == 1 ? stream.readUTF() : null;
            }
        }
        String from = stream.readUTF();
        return new ImportFromSSTNode(currentScope, from, asNames, startOffset, endOffset);
    }

    private SSTNode readImport() throws IOException {
        readPosition();
        int startOffset = startIndex;
        int endOffset = endIndex;
        String name = stream.readUTF();
        String asNames = stream.readByte() == 1 ? stream.readUTF() : null;
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

    private SSTNode readNumberLiteral() throws IOException {
        readPosition();
        int startOffset = startIndex;
        int endOffset = endIndex;
        byte start = stream.readByte();
        byte base = stream.readByte();
        boolean negativ = stream.readBoolean();
        // String value = source.getCharacters().subSequence(negativ ? startOffset + 1 :
        // startOffset, endOffset).toString();
        String value = readString();
        char c = value.charAt(0);
        if (c == '(') {
            value = value.substring(1);
            c = value.charAt(0);
        }
        if (c == ' ') {
            value = value.trim();
        }
        return new NumberLiteralSSTNode(value, negativ, start, base, startOffset, endOffset);
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
        SSTNode value = stream.readByte() == 1 ? readNode() : null;
        SSTNode from = stream.readByte() == 1 ? readNode() : null;
        return new RaiseSSTNode(value, from, startOffset, endOffset);
    }

    private SSTNode readReturn() throws IOException {
        readPosition();
        int startOffset = startIndex;
        int endOffset = endIndex;
        SSTNode value = stream.readByte() == 1 ? readNode() : null;
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
        SSTNode start = stream.readByte() == 1 ? readNode() : null;
        SSTNode step = stream.readByte() == 1 ? readNode() : null;
        SSTNode stop = stream.readByte() == 1 ? readNode() : null;
        return new SliceSSTNode(start, stop, step, startOffset, endOffset);
    }

    private SSTNode readStar() throws IOException {
        readPosition();
        int startOffset = startIndex;
        int endOffset = endIndex;
        SSTNode value = readNode();
        return new StarSSTNode(value, startOffset, endOffset);
    }

    private SSTNode readStringLiteral() throws IOException {
        readPosition();
        int startOffset = startIndex;
        int endOffset = endIndex;
        String[] values = readStrings();
        return new StringLiteralSSTNode(values, startOffset, endOffset);
    }

    private SSTNode readSubscript() throws IOException {
        readPosition();
        int startOffset = startIndex;
        int endOffset = endIndex;
        SSTNode subscript = readNode();
        SSTNode receiver = readNode();
        return new SubscriptSSTNode(receiver, subscript, startOffset, endOffset);
    }

    private SSTNode readTernaryArithmetic() throws IOException {
        readPosition();
        int startOffset = startIndex;
        int endOffset = endIndex;
        SSTNode left = readNode();
        SSTNode right = readNode();
        return new TernaryArithmeticSSTNode(left, right, startOffset, endOffset);
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
        int len = readInt(stream.readByte());
        ExceptSSTNode[] exceptNodes = new ExceptSSTNode[len];
        for (int i = 0; i < len; i++) {
            exceptNodes[i] = (ExceptSSTNode) readNode();
        }
        SSTNode elseStatement = stream.readByte() == 1 ? readNode() : null;
        SSTNode finallyStatement = stream.readByte() == 1 ? readNode() : null;
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
        SSTNode elseStatement = stream.readByte() == 1 ? readNode() : null;
        WhileSSTNode whileNode = new WhileSSTNode(test, body, containsContinue, containsBreak, startOffset, endOffset);
        whileNode.setElse(elseStatement);
        return whileNode;
    }

    private SSTNode readWith() throws IOException {
        readPosition();
        int startOffset = startIndex;
        int endOffset = endIndex;
        SSTNode expression = readNode();
        SSTNode target = stream.readByte() == 1 ? readNode() : null;
        SSTNode body = readNode();
        return new WithSSTNode(expression, target, body, startOffset, endOffset);
    }

    private SSTNode readYieldExpression() throws IOException {
        readPosition();
        int startOffset = startIndex;
        int endOffset = endIndex;
        boolean isFrom = stream.readBoolean();
        SSTNode value = stream.readByte() == 1 ? readNode() : null;
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
        int len = readInt(stream.readByte());
        SSTNode[] nodes = new SSTNode[len];
        for (int i = 0; i < len; i++) {
            nodes[i] = readNode();
        }
        return nodes;
    }

    private ArgListBuilder readArgListBuilder() throws IOException {
        SSTNode[] nodes = readNodes();
        ArgListBuilder alb = new ArgListBuilder();
        for (SSTNode node : nodes) {
            alb.addArg(node);
        }
        nodes = readNodes();
        for (int i = 0; i < nodes.length; i++) {
            alb.addNamedArg(stream.readUTF(), nodes[i]);
        }
        nodes = readNodes();
        for (SSTNode node : nodes) {
            alb.addStarArg(node);
        }
        nodes = readNodes();
        for (SSTNode node : nodes) {
            alb.addKwArg(node);
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

        int len = readInt(stream.readByte());
        int i;
        for (i = 0; i < len; i++) {
            if (i == posOnlyIndex) {
                builder.markPositionalOnlyIndex();
            }
            name = stream.readUTF();
            if (name.isEmpty()) {
                name = null;
            }
            type = stream.readByte() == 1 ? type = readNode() : null;
            if (stream.readByte() == 1) {
                value = readNode();
                builder.addParam(name, type, value);
            } else {
                if (splatIndex == i) {
                    builder.addSplat(name, type);
                } else {
                    builder.addParam(name, type, null);
                }
            }
        }

        len = i + readInt(stream.readByte());
        for (; i < len; i++) {
            name = stream.readUTF();
            type = stream.readByte() == 1 ? type = readNode() : null;
            if (stream.readByte() == 1) {
                value = readNode();
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

    private String readString() throws IOException {
        return stream.readUTF();
    }
}
