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

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;

import com.oracle.graal.python.parser.sst.ArgDefListBuilder.Parameter;
import com.oracle.graal.python.parser.sst.ArgDefListBuilder.ParameterWithDefValue;
import com.oracle.graal.python.parser.sst.SerializationUtils.SSTId;

public class SSTSerializerVisitor implements SSTreeVisitor<Boolean> {

    private final DataOutputStream out;

    private SSTNode lastNode;

    private final HashMap<String, Integer> stringTable = new HashMap<>();

    public SSTSerializerVisitor(DataOutputStream out) {
        this.out = out;
        stringTable.put(null, 0);
    }

    private void writeInt(int value) throws IOException {
        if ((value & ~0x7F) == 0) {
            out.writeByte(value);
        } else if ((value & ~0xFFFF) == 0) {
            out.writeByte(0x80);
            out.writeByte(0xff & (value >> 8));
            out.writeByte(0xff & value);
        } else if ((value & ~0xFFFFFF) == 0) {
            out.writeByte(0x81);
            out.writeByte(0xff & (value >> 16));
            out.writeByte(0xff & (value >> 8));
            out.writeByte(0xff & value);
        } else {
            out.writeByte(0x82);
            out.writeByte(0xff & (value >> 24));
            out.writeByte(0xff & (value >> 16));
            out.writeByte(0xff & (value >> 8));
            out.writeByte(0xff & value);
        }
    }

    private void writeLong(long value) throws IOException {
        if (value == (int) value) {
            writeInt((int) value);
        } else {
            out.writeByte(0x83);
            out.writeLong(value);
        }
    }

    private void writePosition(SSTNode node) throws IOException {
        if (node.getStartOffset() < 0) {
            // there is no source section
            writeInt(0);
        } else {
            if (lastNode != null && lastNode.endOffset == node.endOffset && lastNode.startOffset == node.startOffset) {
                // the source section is the same as the previous node has.
                writeInt(1);
            } else {
                assert node.startOffset >= 0;
                writeInt(node.startOffset + 2);
                writeInt(node.endOffset - node.startOffset);
            }
        }
        lastNode = node;
    }

    private void writeString(String text) throws IOException {
        Integer index = stringTable.get(text); // also handles "null"
        if (index != null) {
            writeInt(index + 2);
        } else {
            stringTable.put(text, stringTable.size());
            boolean simple = true;
            char[] array = text.toCharArray();
            for (char c : array) {
                if (c < 0 || c > 0xff) {
                    simple = false;
                    break;
                }
            }
            writeInt(simple ? 0 : 1);
            writeInt(text.length());
            if (simple) {
                for (char c : array) {
                    out.writeByte(c);
                }
            } else {
                for (char c : array) {
                    out.writeChar(c);
                }
            }
        }
    }

    private void writeId(SSTId id) throws IOException {
        out.writeByte(SerializationUtils.getSSTNodeTypeId(id));
    }

    private void writeNodeOrNull(SSTNode node) throws IOException {
        if (node == null) {
            writeId(SSTId.NullID);
        } else {
            node.accept(this);
        }
    }

    private void writeNodes(SSTNode[] nodes) throws IOException {
        writeInt(nodes.length);
        for (SSTNode value : nodes) {
            writeNodeOrNull(value);
        }
    }

    private void writeArgListBuilder(ArgListBuilder alb) throws IOException {
        SSTNode[] args = alb.getArgs();
        SSTNode[] nameArgNodes = alb.getNameArgNodes();
        String[] nameArgNames = alb.getNameArgNames();
        assert nameArgNodes.length == nameArgNames.length;
        SSTNode[] starArg = alb.getStarArg();
        SSTNode[] kwArg = alb.getKwArg();
        writeInt(args.length);
        writeInt(nameArgNodes.length);
        writeInt(starArg.length);
        writeInt(kwArg.length);

        for (SSTNode arg : args) {
            writeNodeOrNull(arg);
        }
        for (int i = 0; i < nameArgNodes.length; i++) {
            writeString(nameArgNames[i]);
            writeNodeOrNull(nameArgNodes[i]);
        }
        for (SSTNode arg : starArg) {
            writeNodeOrNull(arg);
        }
        for (SSTNode arg : kwArg) {
            writeNodeOrNull(arg);
        }
    }

    private void writeArguments(ArgDefListBuilder argBuilder) throws IOException {
        out.writeByte(argBuilder.getSplatIndex());
        out.writeByte(argBuilder.getPositionalOnlyIndex());
        out.writeByte(argBuilder.getKWargIndex());
        Parameter[] args = argBuilder.getArgs();
        if (args == null) {
            writeInt(0);
        } else {
            writeInt(args.length);
            for (Parameter arg : args) {
                writeString(arg.name == null ? "" : arg.name);
                writeNodeOrNull(arg.type);
                writeNodeOrNull(arg instanceof ParameterWithDefValue ? ((ParameterWithDefValue) arg).value : null);
            }
        }
        args = argBuilder.getKWArgs();
        if (args == null) {
            writeInt(0);
        } else {
            writeInt(args.length);
            for (Parameter arg : args) {
                writeString(arg.name);
                writeNodeOrNull(arg.type);
                writeNodeOrNull(arg instanceof ParameterWithDefValue ? ((ParameterWithDefValue) arg).value : null);
            }
        }
    }

    private void write(String[] values) throws IOException {
        writeInt(values.length);
        for (String value : values) {
            writeString(value);
        }
    }

    private static void handleIOExceptin(IOException e) {
        throw new RuntimeException("Problem during SST serialization.", e);
    }

    @Override
    public Boolean visit(AndSSTNode node) {
        try {
            writeId(SSTId.AndID);
            writePosition(node);
            writeNodes(node.values);
        } catch (IOException e) {
            handleIOExceptin(e);
        }
        return true;
    }

    @Override
    public Boolean visit(AnnAssignmentSSTNode node) {
        try {
            writeId(SSTId.AnnAssignmentID);
            writePosition(node);
            node.type.accept(this);
            node.lhs[0].accept(this);
            node.rhs.accept(this);
        } catch (IOException e) {
            handleIOExceptin(e);
        }
        return true;
    }

    @Override
    public Boolean visit(AssertSSTNode node) {
        try {
            writeId(SSTId.AssertID);
            writePosition(node);
            node.test.accept(this);
            writeNodeOrNull(node.message);
        } catch (IOException e) {
            handleIOExceptin(e);
        }
        return true;
    }

    @Override
    public Boolean visit(AssignmentSSTNode node) {
        try {
            writeId(SSTId.AssignmentID);
            writePosition(node);
            writeNodes(node.lhs);
            node.rhs.accept(this);
        } catch (IOException e) {
            handleIOExceptin(e);
        }
        return true;
    }

    @Override
    public Boolean visit(AugAssignmentSSTNode node) {
        try {
            writeId(SSTId.AugAssignmentID);
            writePosition(node);
            node.lhs.accept(this);
            node.rhs.accept(this);
            writeString(node.operation);
        } catch (IOException e) {
            handleIOExceptin(e);
        }
        return true;
    }

    @Override
    public Boolean visit(BinaryArithmeticSSTNode node) {
        try {
            writeId(SSTId.BinaryArithmeticID);
            writePosition(node);
            node.left.accept(this);
            node.right.accept(this);
            out.writeByte(SerializationUtils.getBinaryArithemticId(node.operation));
        } catch (IOException e) {
            handleIOExceptin(e);
        }
        return true;
    }

    @Override
    public Boolean visit(BlockSSTNode node) {
        try {
            writeId(SSTId.BlockID);
            writePosition(node);
            writeInt(node.statements.length);
            for (SSTNode statement : node.statements) {
                statement.accept(this);
            }
        } catch (IOException e) {
            handleIOExceptin(e);
        }
        return true;
    }

    @Override
    public Boolean visit(BooleanLiteralSSTNode node) {
        try {
            writeId(SSTId.BooleanLiteralID);
            writePosition(node);
            out.writeBoolean(node.value);
        } catch (IOException e) {
            handleIOExceptin(e);
        }
        return true;
    }

    @Override
    public Boolean visit(CallSSTNode node) {
        try {
            writeId(SSTId.CallID);
            writePosition(node);
            node.target.accept(this);
            writeArgListBuilder(node.parameters);
        } catch (IOException e) {
            handleIOExceptin(e);
        }
        return true;
    }

    @Override
    public Boolean visit(ClassSSTNode node) {
        try {
            writeId(SSTId.ClassID);
            writePosition(node);
            writeString(node.name);
            if (node.baseClasses != null) {
                out.writeByte(1);
                writeArgListBuilder(node.baseClasses);
            } else {
                out.writeByte(0);
            }
            out.writeInt(node.scope.getSerializetionId());
            node.body.accept(this);
        } catch (IOException e) {
            handleIOExceptin(e);
        }
        return true;
    }

    @Override
    public Boolean visit(CollectionSSTNode node) {
        try {
            writeId(SSTId.CollectionID);
            writePosition(node);
            // TODO the type has to handled in different way
            out.writeByte(SerializationUtils.getPythonBuiltinClassTypeId(node.type));
            writeNodes(node.values);
        } catch (IOException e) {
            handleIOExceptin(e);
        }
        return true;
    }

    @Override
    public Boolean visit(ComparisonSSTNode node) {
        try {
            writeId(SSTId.ComparisonID);
            writePosition(node);
            node.firstValue.accept(this);
            writeNodes(node.otherValues);
            write(node.operations);
        } catch (IOException e) {
            handleIOExceptin(e);
        }
        return true;
    }

    @Override
    public Boolean visit(DecoratedSSTNode node) {
        try {
            writeId(SSTId.DecoratedID);
            writePosition(node);
            node.decorated.accept(this);
            writeNodes(node.decorators);
        } catch (IOException e) {
            handleIOExceptin(e);
        }
        return true;
    }

    @Override
    public Boolean visit(DecoratorSSTNode node) {
        try {
            writeId(SSTId.DecoratorID);
            writePosition(node);
            writeString(node.name);
            if (node.arg != null) {
                out.writeByte(1);
                writeArgListBuilder(node.arg);
            } else {
                out.writeByte(0);
            }

        } catch (IOException e) {
            handleIOExceptin(e);
        }
        return true;
    }

    @Override
    public Boolean visit(DelSSTNode node) {
        try {
            writeId(SSTId.DelID);
            writePosition(node);
            writeNodes(node.expressions);
        } catch (IOException e) {
            handleIOExceptin(e);
        }
        return true;
    }

    @Override
    public Boolean visit(ExceptSSTNode node) {
        try {
            writeId(SSTId.ExceptID);
            writePosition(node);
            writeNodeOrNull(node.test);
            writeString(node.asName);
            node.body.accept(this);
        } catch (IOException e) {
            handleIOExceptin(e);
        }
        return true;
    }

    @Override
    public Boolean visit(ExpressionStatementSSTNode node) {
        try {
            writeId(SSTId.ExpressionStatementID);
            node.expression.accept(this);
        } catch (IOException e) {
            handleIOExceptin(e);
        }
        return true;
    }

    @Override
    public Boolean visit(FloatLiteralSSTNode node) {
        try {
            writeId(SSTId.FloatLiteralID);
            writePosition(node);
            out.writeBoolean(node.imaginary);
            writeString(node.value);
        } catch (IOException e) {
            handleIOExceptin(e);
        }
        return true;
    }

    @Override
    public Boolean visit(ForComprehensionSSTNode node) {
        try {
            writeId(SSTId.ForComprehensionID);
            writePosition(node);
            out.writeBoolean(node.async);
            node.iterator.accept(this);
            writeInt(node.level);
            writeInt(node.line);
            writeNodeOrNull(node.name);
            out.writeByte(SerializationUtils.getPythonBuiltinClassTypeId(node.resultType));
            out.writeInt(node.scope.getSerializetionId());
            node.target.accept(this);
            writeNodes(node.variables);
            writeNodes(node.conditions);
        } catch (IOException e) {
            handleIOExceptin(e);
        }
        return true;
    }

    @Override
    public Boolean visit(ForSSTNode node) {
        try {
            writeId(SSTId.ForID);
            writePosition(node);
            out.writeBoolean(node.containsBreak);
            out.writeBoolean(node.containsContinue);
            writeNodes(node.targets);
            node.iterator.accept(this);
            node.body.accept(this);
            writeNodeOrNull(node.elseStatement);
        } catch (IOException e) {
            handleIOExceptin(e);
        }
        return true;
    }

    @Override
    public Boolean visit(FunctionDefSSTNode node) {
        try {
            writeId(SSTId.FunctionDefID);
            writePosition(node);
            out.writeInt(node.scope.getSerializetionId());
            writeString(node.name);
            writeString(node.enclosingClassName);
            writeArguments(node.argBuilder);
            node.body.accept(this);

        } catch (IOException e) {
            handleIOExceptin(e);
        }
        return true;
    }

    @Override
    public Boolean visit(GetAttributeSSTNode node) {
        try {
            writeId(SSTId.GetAttributeID);
            writePosition(node);
            writeString(node.name);
            node.receiver.accept(this);
        } catch (IOException e) {
            handleIOExceptin(e);
        }
        return true;
    }

    @Override
    public Boolean visit(IfSSTNode node) {
        try {
            writeId(SSTId.IfID);
            writePosition(node);
            node.test.accept(this);
            node.thenStatement.accept(this);
            writeNodeOrNull(node.elseStatement);
        } catch (IOException e) {
            handleIOExceptin(e);
        }
        return true;
    }

    @Override
    public Boolean visit(ImportFromSSTNode node) {
        try {
            writeId(SSTId.ImportFromID);
            writePosition(node);
            String[][] asNames = node.asNames;
            if (asNames != null) {
                out.writeByte(1);
                out.writeByte(asNames.length);
                for (String[] names : asNames) {
                    writeString(names[0]);
                    writeString(names[1]);
                }
            } else {
                out.writeByte(0);
            }
            writeString(node.from);
        } catch (IOException e) {
            handleIOExceptin(e);
        }
        return true;
    }

    @Override
    public Boolean visit(ImportSSTNode node) {
        try {
            writeId(SSTId.ImportID);
            writePosition(node);
            writeString(node.name);
            writeString(node.asName);
        } catch (IOException e) {
            handleIOExceptin(e);
        }
        return true;
    }

    @Override
    public Boolean visit(LambdaSSTNode node) {
        try {
            writeId(SSTId.LambdaID);
            writePosition(node);
            out.writeInt(node.scope.getSerializetionId());
            if (node.args != null) {
                out.writeByte(1);
                writeArguments(node.args);
            } else {
                out.writeByte(0);
            }
            node.body.accept(this);
        } catch (IOException e) {
            handleIOExceptin(e);
        }
        return true;
    }

    @Override
    public Boolean visit(NotSSTNode node) {
        try {
            writeId(SSTId.NotID);
            writePosition(node);
            node.value.accept(this);
        } catch (IOException e) {
            handleIOExceptin(e);
        }
        return true;
    }

    @Override
    public Boolean visit(NumberLiteralSSTNode node) {
        try {
            writeId(SSTId.NumberLiteralID);
            writePosition(node);
            out.writeByte(node.start);
            out.writeByte(node.base);
            out.writeBoolean(node.negative);
            writeString(node.value);
        } catch (IOException e) {
            handleIOExceptin(e);
        }
        return true;
    }

    @Override
    public Boolean visit(OrSSTNode node) {
        try {
            writeId(SSTId.OrID);
            writePosition(node);
            writeNodes(node.values);
        } catch (IOException e) {
            handleIOExceptin(e);
        }
        return true;
    }

    @Override
    public Boolean visit(RaiseSSTNode node) {
        try {
            writeId(SSTId.RaiseID);
            writePosition(node);
            writeNodeOrNull(node.value);
            writeNodeOrNull(node.from);
        } catch (IOException e) {
            handleIOExceptin(e);
        }
        return true;
    }

    @Override
    public Boolean visit(ReturnSSTNode node) {
        try {
            writeId(SSTId.ReturnID);
            writePosition(node);
            writeNodeOrNull(node.value);
        } catch (IOException e) {
            handleIOExceptin(e);
        }
        return true;
    }

    @Override
    public Boolean visit(SimpleSSTNode node) {
        try {
            writeId(SSTId.SimpleID);
            writePosition(node);
            out.writeByte(SerializationUtils.getSimpleSSTNodeTypeId(node.type));
        } catch (IOException e) {
            handleIOExceptin(e);
        }
        return true;
    }

    @Override
    public Boolean visit(SliceSSTNode node) {
        try {
            writeId(SSTId.SliceID);
            writePosition(node);
            writeNodeOrNull(node.start);
            writeNodeOrNull(node.step);
            writeNodeOrNull(node.stop);
        } catch (IOException e) {
            handleIOExceptin(e);
        }
        return true;
    }

    @Override
    public Boolean visit(StarSSTNode node) {
        try {
            writeId(SSTId.StarID);
            writePosition(node);
            node.value.accept(this);
        } catch (IOException e) {
            handleIOExceptin(e);
        }
        return true;
    }

    @Override
    public Boolean visit(StringLiteralSSTNode node) {
        try {
            writeId(SSTId.StringLiteralID);
            writePosition(node);
            write(node.values);
        } catch (IOException e) {
            handleIOExceptin(e);
        }
        return true;
    }

    @Override
    public Boolean visit(SubscriptSSTNode node) {
        try {
            writeId(SSTId.SubscriptID);
            writePosition(node);
            node.subscript.accept(this);
            node.receiver.accept(this);
        } catch (IOException e) {
            handleIOExceptin(e);
        }
        return true;
    }

    @Override
    public Boolean visit(TernaryIfSSTNode node) {
        try {
            writeId(SSTId.TernaryIfID);
            writePosition(node);
            node.test.accept(this);
            node.thenStatement.accept(this);
            node.elseStatement.accept(this);
        } catch (IOException e) {
            handleIOExceptin(e);
        }
        return true;
    }

    @Override
    public Boolean visit(TrySSTNode node) {
        try {
            writeId(SSTId.TryID);
            writePosition(node);
            node.body.accept(this);
            writeNodes(node.exceptNodes);
            writeNodeOrNull(node.elseStatement);
            writeNodeOrNull(node.finallyStatement);
        } catch (IOException e) {
            handleIOExceptin(e);
        }
        return true;
    }

    @Override
    public Boolean visit(UnarySSTNode node) {
        try {
            writeId(SSTId.UnaryID);
            writePosition(node);
            out.writeByte(SerializationUtils.getUnaryArithemticId(node.arithmetic));
            node.value.accept(this);
        } catch (IOException e) {
            handleIOExceptin(e);
        }
        return true;
    }

    @Override
    public Boolean visit(VarLookupSSTNode node) {
        try {
            writeId(SSTId.VarLookupID);
            writePosition(node);
            writeString(node.name);
        } catch (IOException e) {
            handleIOExceptin(e);
        }
        return true;
    }

    @Override
    public Boolean visit(WhileSSTNode node) {
        try {
            writeId(SSTId.WhileID);
            writePosition(node);
            out.writeBoolean(node.containsBreak);
            out.writeBoolean(node.containsContinue);
            node.test.accept(this);
            node.body.accept(this);
            writeNodeOrNull(node.elseStatement);
        } catch (IOException e) {
            handleIOExceptin(e);
        }
        return true;
    }

    @Override
    public Boolean visit(WithSSTNode node) {
        try {
            writeId(SSTId.WithID);
            writePosition(node);
            node.expression.accept(this);
            writeNodeOrNull(node.target);
            node.body.accept(this);
        } catch (IOException e) {
            handleIOExceptin(e);
        }
        return true;
    }

    @Override
    public Boolean visit(YieldExpressionSSTNode node) {
        try {
            writeId(SSTId.YieldExpressionID);
            writePosition(node);
            out.writeBoolean(node.isFrom);
            writeNodeOrNull(node.value);
        } catch (IOException e) {
            handleIOExceptin(e);
        }
        return true;
    }

}
