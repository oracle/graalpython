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

import com.oracle.graal.python.parser.sst.ArgDefListBuilder.Parameter;
import com.oracle.graal.python.parser.sst.ArgDefListBuilder.ParameterWithDefValue;
import com.oracle.graal.python.parser.sst.SerializationUtils.SSTId;
import java.io.DataOutputStream;
import java.io.IOException;

public class SSTSerializerVisitor implements SSTreeVisitor<Boolean> {

    private final DataOutputStream out;

    private SSTNode lastNode;

    public SSTSerializerVisitor(DataOutputStream out) {
        this.out = out;
    }

    private void writeInt(int value) throws IOException {
        if (value < 0xFF) {
            out.writeByte(1);
            out.writeByte(value);
        } else if (value < 0xFFFF) {
            out.writeByte(2);
            out.writeByte((value & 0x0000FF00) >> 8);
            out.writeByte(value & 0x000000FF);
        } else if (value < 0x00FF0000) {
            out.writeByte(3);
            out.writeByte((value & 0x00FF0000) >> 16);
            out.writeByte((value & 0x0000FF00) >> 8);
            out.writeByte(value & 0x000000FF);
        } else {
            out.writeByte(4);
            out.writeByte((value & 0xFF000000) >> 24);
            out.writeByte((value & 0x00FF0000) >> 16);
            out.writeByte((value & 0x0000FF00) >> 8);
            out.writeByte(value & 0x000000FF);
        }
    }

    private void writePosition(SSTNode node) throws IOException {
        if (node.getStartOffset() < 0) {
            // there is no source section
            out.writeByte(0);
        } else {
            if (lastNode != null && lastNode.endOffset == node.endOffset && lastNode.startOffset == node.startOffset) {
                // the source section is the same as the previous node has.
                out.writeByte(10);
            } else {
                writeInt(node.startOffset);
                writeInt(node.endOffset);

            }
        }
        lastNode = node;
    }

    private void writeString(String text) throws IOException {
        out.writeUTF(text);
    }

    private void writeNodes(SSTNode[] nodes) throws IOException {
        writeInt(nodes.length);
        for (SSTNode value : nodes) {
            if (value != null) {
                value.accept(this);
            } else {
                out.writeByte(SerializationUtils.getSSTNodeTypeId(SSTId.NullID));
            }
        }
    }

    private void writeArgListBuilder(ArgListBuilder alb) throws IOException {
        writeNodes(alb.getArgs());
        writeNodes(alb.getNameArgNodes());
        String[] names = alb.getNameArgNames();
        for (String name : names) {
            out.writeUTF(name);
        }
        writeNodes(alb.getStarArg());
        writeNodes(alb.getKwArg());
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
                out.writeUTF(arg.name == null ? "" : arg.name);
                writeNodeOrNull(arg.type);
                if (arg instanceof ParameterWithDefValue) {
                    out.write(1);
                    ((ParameterWithDefValue) arg).value.accept(this);
                } else {
                    out.write(0);
                }
            }
        }
        args = argBuilder.getKWArgs();
        if (args == null) {
            writeInt(0);
        } else {
            writeInt(args.length);
            for (Parameter arg : args) {
                out.writeUTF(arg.name);
                writeNodeOrNull(arg.type);
                if (arg instanceof ParameterWithDefValue) {
                    out.write(1);
                    ((ParameterWithDefValue) arg).value.accept(this);
                } else {
                    out.write(0);
                }
            }
        }
    }

    private void write(String[] values) throws IOException {
        writeInt(values.length);
        for (String value : values) {
            out.writeUTF(value);
        }
    }

    private void writeStringOrNull(String value) throws IOException {
        if (value != null) {
            out.writeByte(1);
            out.writeUTF(value);
        } else {
            out.writeByte(0);
        }
    }

    private void writeNodeOrNull(SSTNode node) throws IOException {
        if (node != null) {
            out.writeByte(1);
            node.accept(this);
        } else {
            out.writeByte(0);
        }
    }

    private void handleIOExceptin(IOException e) {
        throw new RuntimeException("Problem during SST serialization.", e);
    }

    @Override
    public Boolean visit(AndSSTNode node) {
        try {
            out.writeByte(SerializationUtils.getSSTNodeTypeId(SSTId.AndID));
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
            out.writeByte(SerializationUtils.getSSTNodeTypeId(SSTId.AnnAssignmentID));
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
            out.writeByte(SerializationUtils.getSSTNodeTypeId(SSTId.AssertID));
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
            out.writeByte(SerializationUtils.getSSTNodeTypeId(SSTId.AssignmentID));
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
            out.writeByte(SerializationUtils.getSSTNodeTypeId(SSTId.AugAssignmentID));
            writePosition(node);
            node.lhs.accept(this);
            node.rhs.accept(this);
            out.writeByte(node.operation.length());
            out.writeChars(node.operation);
        } catch (IOException e) {
            handleIOExceptin(e);
        }
        return true;
    }

    @Override
    public Boolean visit(BinaryArithmeticSSTNode node) {
        try {
            out.writeByte(SerializationUtils.getSSTNodeTypeId(SSTId.BinaryArithmeticID));
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
            out.writeByte(SerializationUtils.getSSTNodeTypeId(SSTId.BlockID));
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
            out.writeByte(SerializationUtils.getSSTNodeTypeId(SSTId.BooleanLiteralID));
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
            out.writeByte(SerializationUtils.getSSTNodeTypeId(SSTId.CallID));
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
            out.writeByte(SerializationUtils.getSSTNodeTypeId(SSTId.ClassID));
            writePosition(node);
            writeString(node.name);
            if (node.baseClasses != null) {
                out.writeByte(1);
                writeArgListBuilder(node.baseClasses);
            } else {
                out.writeByte(0);
            }
            out.writeInt(node.classScope.getSerializetionId());
            node.body.accept(this);
        } catch (IOException e) {
            handleIOExceptin(e);
        }
        return true;
    }

    @Override
    public Boolean visit(CollectionSSTNode node) {
        try {
            out.writeByte(SerializationUtils.getSSTNodeTypeId(SSTId.CollectionID));
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
            out.writeByte(SerializationUtils.getSSTNodeTypeId(SSTId.ComparisonID));
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
            out.writeByte(SerializationUtils.getSSTNodeTypeId(SSTId.DecoratedID));
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
            out.writeByte(SerializationUtils.getSSTNodeTypeId(SSTId.DecoratorID));
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
            out.writeByte(SerializationUtils.getSSTNodeTypeId(SSTId.DelID));
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
            out.writeByte(SerializationUtils.getSSTNodeTypeId(SSTId.ExceptID));
            writePosition(node);
            writeNodeOrNull(node.test);
            writeStringOrNull(node.asName);
            node.body.accept(this);
        } catch (IOException e) {
            handleIOExceptin(e);
        }
        return true;
    }

    @Override
    public Boolean visit(ExpressionStatementSSTNode node) {
        try {
            out.writeByte(SerializationUtils.getSSTNodeTypeId(SSTId.ExpressionStatementID));
            node.expression.accept(this);
        } catch (IOException e) {
            handleIOExceptin(e);
        }
        return true;
    }

    @Override
    public Boolean visit(FloatLiteralSSTNode node) {
        try {
            out.writeByte(SerializationUtils.getSSTNodeTypeId(SSTId.FloatLiteralID));
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
            out.writeByte(SerializationUtils.getSSTNodeTypeId(SSTId.ForComprehensionID));
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
            out.writeByte(SerializationUtils.getSSTNodeTypeId(SSTId.ForID));
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
            out.writeByte(SerializationUtils.getSSTNodeTypeId(SSTId.FunctionDefID));
            writePosition(node);
            out.writeInt(node.functionScope.getSerializetionId());
            out.writeUTF(node.name);
            writeStringOrNull(node.enclosingClassName);
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
            out.writeByte(SerializationUtils.getSSTNodeTypeId(SSTId.GetAttributeID));
            writePosition(node);
            out.writeUTF(node.name);
            node.receiver.accept(this);
        } catch (IOException e) {
            handleIOExceptin(e);
        }
        return true;
    }

    @Override
    public Boolean visit(IfSSTNode node) {
        try {
            out.writeByte(SerializationUtils.getSSTNodeTypeId(SSTId.IfID));
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
            out.writeByte(SerializationUtils.getSSTNodeTypeId(SSTId.ImportFromID));
            writePosition(node);
            String[][] asNames = node.asNames;
            if (asNames != null) {
                out.writeByte(1);
                out.writeByte(asNames.length);
                for (String[] names : asNames) {
                    out.writeUTF(names[0]);
                    writeStringOrNull(names[1]);
                }
            } else {
                out.writeByte(0);
            }
            out.writeUTF(node.from);
        } catch (IOException e) {
            handleIOExceptin(e);
        }
        return true;
    }

    @Override
    public Boolean visit(ImportSSTNode node) {
        try {
            out.writeByte(SerializationUtils.getSSTNodeTypeId(SSTId.ImportID));
            writePosition(node);
            out.writeUTF(node.name);
            writeStringOrNull(node.asName);
        } catch (IOException e) {
            handleIOExceptin(e);
        }
        return true;
    }

    @Override
    public Boolean visit(LambdaSSTNode node) {
        try {
            out.writeByte(SerializationUtils.getSSTNodeTypeId(SSTId.LambdaID));
            writePosition(node);
            out.writeInt(node.functionScope.getSerializetionId());
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
            out.writeByte(SerializationUtils.getSSTNodeTypeId(SSTId.NotID));
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
            out.writeByte(SerializationUtils.getSSTNodeTypeId(SSTId.NumberLiteralID));
            writePosition(node);
            out.writeByte(node.start);
            out.writeByte(node.base);
            out.writeBoolean(node.negative);
        } catch (IOException e) {
            handleIOExceptin(e);
        }
        return true;
    }

    @Override
    public Boolean visit(OrSSTNode node) {
        try {
            out.writeByte(SerializationUtils.getSSTNodeTypeId(SSTId.OrID));
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
            out.writeByte(SerializationUtils.getSSTNodeTypeId(SSTId.RaiseID));
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
            out.writeByte(SerializationUtils.getSSTNodeTypeId(SSTId.ReturnID));
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
            out.writeByte(SerializationUtils.getSSTNodeTypeId(SSTId.SimpleID));
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
            out.writeByte(SerializationUtils.getSSTNodeTypeId(SSTId.SliceID));
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
            out.writeByte(SerializationUtils.getSSTNodeTypeId(SSTId.StarID));
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
            out.writeByte(SerializationUtils.getSSTNodeTypeId(SSTId.StringLiteralID));
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
            out.writeByte(SerializationUtils.getSSTNodeTypeId(SSTId.SubscriptID));
            writePosition(node);
            node.subscript.accept(this);
            node.receiver.accept(this);
        } catch (IOException e) {
            handleIOExceptin(e);
        }
        return true;
    }

    @Override
    public Boolean visit(TernaryArithmeticSSTNode node) {
        try {
            out.writeByte(SerializationUtils.getSSTNodeTypeId(SSTId.TernaryArithmeticID));
            writePosition(node);
            node.left.accept(this);
            node.right.accept(this);
        } catch (IOException e) {
            handleIOExceptin(e);
        }
        return true;
    }

    @Override
    public Boolean visit(TernaryIfSSTNode node) {
        try {
            out.writeByte(SerializationUtils.getSSTNodeTypeId(SSTId.TernaryIfID));
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
            out.writeByte(SerializationUtils.getSSTNodeTypeId(SSTId.TryID));
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
            out.writeByte(SerializationUtils.getSSTNodeTypeId(SSTId.UnaryID));
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
            out.writeByte(SerializationUtils.getSSTNodeTypeId(SSTId.VarLookupID));
            writePosition(node);
        } catch (IOException e) {
            handleIOExceptin(e);
        }
        return true;
    }

    @Override
    public Boolean visit(WhileSSTNode node) {
        try {
            out.writeByte(SerializationUtils.getSSTNodeTypeId(SSTId.WhileID));
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
            out.writeByte(SerializationUtils.getSSTNodeTypeId(SSTId.WithID));
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
            out.writeByte(SerializationUtils.getSSTNodeTypeId(SSTId.YieldExpressionID));
            writePosition(node);
            out.writeBoolean(node.isFrom);
            writeNodeOrNull(node.value);
        } catch (IOException e) {
            handleIOExceptin(e);
        }
        return true;
    }

}
