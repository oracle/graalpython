/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.pegparser.test.sst;

import com.oracle.graal.python.pegparser.sst.AliasTy;
import com.oracle.graal.python.pegparser.sst.ArgTy;
import com.oracle.graal.python.pegparser.sst.ArgumentsTy;
import com.oracle.graal.python.pegparser.sst.ComprehensionTy;
import com.oracle.graal.python.pegparser.sst.ConstantValue;
import com.oracle.graal.python.pegparser.sst.ExceptHandlerTy.ExceptHandler;
import com.oracle.graal.python.pegparser.sst.ExprContextTy;
import com.oracle.graal.python.pegparser.sst.ExprTy;
import com.oracle.graal.python.pegparser.sst.ExprTy.Attribute;
import com.oracle.graal.python.pegparser.sst.ExprTy.Await;
import com.oracle.graal.python.pegparser.sst.ExprTy.BinOp;
import com.oracle.graal.python.pegparser.sst.ExprTy.BoolOp;
import com.oracle.graal.python.pegparser.sst.KeywordTy;
import com.oracle.graal.python.pegparser.sst.MatchCaseTy;
import com.oracle.graal.python.pegparser.sst.ModTy.Expression;
import com.oracle.graal.python.pegparser.sst.ModTy.FunctionType;
import com.oracle.graal.python.pegparser.sst.ModTy.Interactive;
import com.oracle.graal.python.pegparser.sst.ModTy.Module;
import com.oracle.graal.python.pegparser.sst.PatternTy.MatchAs;
import com.oracle.graal.python.pegparser.sst.PatternTy.MatchClass;
import com.oracle.graal.python.pegparser.sst.PatternTy.MatchMapping;
import com.oracle.graal.python.pegparser.sst.PatternTy.MatchOr;
import com.oracle.graal.python.pegparser.sst.PatternTy.MatchSequence;
import com.oracle.graal.python.pegparser.sst.PatternTy.MatchSingleton;
import com.oracle.graal.python.pegparser.sst.PatternTy.MatchStar;
import com.oracle.graal.python.pegparser.sst.PatternTy.MatchValue;
import com.oracle.graal.python.pegparser.sst.SSTNode;
import com.oracle.graal.python.pegparser.sst.SSTreeVisitor;
import com.oracle.graal.python.pegparser.sst.StmtTy;
import com.oracle.graal.python.pegparser.sst.StmtTy.AnnAssign;
import com.oracle.graal.python.pegparser.sst.StmtTy.Assert;
import com.oracle.graal.python.pegparser.sst.StmtTy.Assign;
import com.oracle.graal.python.pegparser.sst.StmtTy.AsyncFor;
import com.oracle.graal.python.pegparser.sst.StmtTy.AsyncFunctionDef;
import com.oracle.graal.python.pegparser.sst.StmtTy.AsyncWith;
import com.oracle.graal.python.pegparser.sst.StmtTy.AugAssign;
import com.oracle.graal.python.pegparser.sst.StmtTy.ClassDef;
import com.oracle.graal.python.pegparser.sst.StmtTy.Delete;
import com.oracle.graal.python.pegparser.sst.StmtTy.Expr;
import com.oracle.graal.python.pegparser.sst.StmtTy.For;
import com.oracle.graal.python.pegparser.sst.StmtTy.FunctionDef;
import com.oracle.graal.python.pegparser.sst.StmtTy.TypeAlias;
import com.oracle.graal.python.pegparser.sst.TypeIgnoreTy.TypeIgnore;
import com.oracle.graal.python.pegparser.sst.TypeParamTy;
import com.oracle.graal.python.pegparser.sst.TypeParamTy.ParamSpec;
import com.oracle.graal.python.pegparser.sst.TypeParamTy.TypeVar;
import com.oracle.graal.python.pegparser.sst.TypeParamTy.TypeVarTuple;
import com.oracle.graal.python.pegparser.sst.WithItemTy;
import com.oracle.graal.python.pegparser.tokenizer.SourceRange;

public class SSTTreePrinterVisitor implements SSTreeVisitor<String> {

    private static final String INDENTATION = "    ";
    private int level = 0;

    private String indent() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < level; i++) {
            sb.append(INDENTATION);
        }
        return sb.toString();
    }

    private static String addHeader(SSTNode node) {
        StringBuilder sb = new StringBuilder();
        sb.append(node.getClass().getSimpleName());
        appendSourceRange(sb, node.getSourceRange());
        return sb.toString();
    }

    private static void appendSourceRange(StringBuilder sb, SourceRange sourceRange) {
        sb.append('[').append(sourceRange.startLine).append(':').append(sourceRange.startColumn);
        sb.append('-').append(sourceRange.endLine).append(':').append(sourceRange.endColumn).append(']');
    }

    private StringBuilder appendNewLineIndented(StringBuilder sb, String str) {
        return sb.append('\n').append(indent()).append(str);
    }

    private void putOnSameLineIfShort(StringBuilder sb, SSTNode node) {
        String nodeText = node.accept(this);
        if (nodeText.contains("\n")) {
            sb.append("\n").append(indent()).append(INDENTATION);
            sb.append(nodeText.replace("\n", "\n" + INDENTATION));
        } else {
            sb.append(nodeText);
        }
    }

    private void appendNode(StringBuilder sb, String name, SSTNode node) {
        if (node != null) {
            appendNewLineIndented(sb, name + ": ");
            putOnSameLineIfShort(sb, node);
        }
    }

    private void appendString(StringBuilder sb, String name, Object value) {
        if (value != null) {
            appendNewLineIndented(sb, name).append(value);
        }
    }

    private void appendNodes(StringBuilder sb, String header, SSTNode[] nodes) {
        if (nodes != null && nodes.length > 0) {
            appendNewLineIndented(sb, header + ':');
            level++;
            for (SSTNode s : nodes) {
                if (s != null) {
                    appendNewLineIndented(sb, s.accept(this));
                }
            }
            level--;
        }
    }

    private static void appendEscapedString(StringBuilder sb, String s) {
        sb.append('"');
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c >= 32 && c <= 127) {
                        sb.append(c);
                    } else {
                        sb.append(String.format("\\u%04x", (int) c));
                    }
                    break;
            }
        }
        sb.append('"');
    }

    @Override
    public String visit(AliasTy node) {
        StringBuilder sb = new StringBuilder();
        sb.append(node.name);
        if (node.asName != null) {
            sb.append(" as ").append(node.asName);
        }
        return sb.toString();
    }

    @Override
    public String visit(ArgTy node) {
        return addHeader(node) + " " + node.arg;
    }

    @Override
    public String visit(ArgumentsTy node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        appendNodes(sb, "Args", node.args);
        appendNodes(sb, "PosOnlyArgs", node.posOnlyArgs);
        appendNode(sb, "VarArg", node.varArg);
        appendNodes(sb, "KwOnlyArgs", node.kwOnlyArgs);
        appendNode(sb, "Kwarg", node.kwArg);
        appendNodes(sb, "Defaults", node.defaults);
        appendNodes(sb, "KwDefaults", node.kwDefaults);
        level--;
        return sb.toString();
    }

    @Override
    public String visit(ComprehensionTy node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        appendNode(sb, "Target", node.target);
        appendNode(sb, "Iterator", node.iter);
        appendNodes(sb, "Ifs", node.ifs);
        level--;
        return sb.toString();
    }

    @Override
    public String visit(Attribute node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node)).append(' ').append('\n');
        level++;
        sb.append(indent()).append("Receiver: ");
        putOnSameLineIfShort(sb, node.value);
        appendString(sb, "Attr: ", node.attr);
        level--;
        return sb.toString();
    }

    @Override
    public String visit(Await node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        appendNode(sb, "Value", node.value);
        level--;
        return sb.toString();
    }

    @Override
    public String visit(BinOp node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        appendString(sb, "Op: ", node.op);
        appendNode(sb, "LHS", node.left);
        appendNode(sb, "RHS", node.right);
        level--;
        return sb.toString();
    }

    @Override
    public String visit(BoolOp node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node)).append(": ").append(node.op);
        level++;
        for (ExprTy e : node.values) {
            appendNewLineIndented(sb, e.accept(this));
        }
        level--;
        return sb.toString();
    }

    @Override
    public String visit(ExprTy.Call node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node)).append("\n");
        level++;
        sb.append(indent()).append("Target: ");
        putOnSameLineIfShort(sb, node.func);
        appendNodes(sb, "Args", node.args);
        appendNodes(sb, "KWArgs", node.keywords);
        level--;
        return sb.toString();
    }

    @Override
    public String visit(ExprTy.Compare node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node)).append("\n");
        level++;
        sb.append(indent()).append("LHS: ");
        putOnSameLineIfShort(sb, node.left);
        for (int i = 0; i < node.ops.length; i++) {
            sb.append("\n").append(indent()).append("Op: ").append(node.ops[i].toString()).append("\n");
            sb.append(indent()).append("RHS: ");
            putOnSameLineIfShort(sb, node.comparators[i]);
        }
        level--;
        return sb.toString();

    }

    @Override
    public String visit(ExprTy.Constant node) {
        StringBuilder sb = new StringBuilder();
        sb.append(node.value.kind);
        appendSourceRange(sb, node.getSourceRange());
        sb.append(" Value: ");
        appendConstantValue(sb, node.value);
        if (node.kind != null) {
            sb.append(" Kind: ");
            // parser always returns a j.l.String, never a byte[]
            appendEscapedString(sb, (String) node.kind);
        }
        return sb.toString();
    }

    private static void appendConstantValue(StringBuilder sb, ConstantValue value) {
        switch (value.kind) {
            case BIGINTEGER:
                sb.append(value.getBigInteger());
                break;
            case BOOLEAN:
                sb.append(value.getBoolean());
                break;
            case LONG:
                sb.append(value.getLong());
                break;
            case DOUBLE:
                sb.append(value.getDouble());
                break;
            case COMPLEX:
                double[] val = value.getComplex();
                sb.append(String.format("%g%+gj", val[0], val[1]));
                break;
            case CODEPOINTS:
                appendEscapedString(sb, value.getCodePoints().toJavaString());
                break;
            case ELLIPSIS:
            case NONE:
                sb.append((Object) null);
                break;
            default:
                sb.append("<unprintable value>");
        }
    }

    @Override
    public String visit(ExprTy.Dict node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node)).append('\n');
        level++;
        sb.append(indent()).append("Values:");
        level++;
        for (int i = 0; i < node.keys.length; i++) {
            appendNode(sb, "Key", node.keys[i]);
            appendNode(sb, "Val", node.values[i]);
        }
        level--;
        level--;
        return sb.toString();
    }

    @Override
    public String visit(ExprTy.DictComp node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node)).append('\n');
        level++;
        sb.append(indent()).append("Key: ").append(node.key.accept(this)).append('\n');
        sb.append(indent()).append("Value: ").append(node.value.accept(this));
        for (SSTNode n : node.generators) {
            appendNewLineIndented(sb, n.accept(this));
        }
        level--;
        return sb.toString();
    }

    @Override
    public String visit(ExprTy.FormattedValue node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        appendNode(sb, "Spec", node.formatSpec);
        appendNode(sb, "Value", node.value);
        appendString(sb, "Conversion: ", node.conversion);
        level--;
        return sb.toString();
    }

    @Override
    public String visit(ExprTy.GeneratorExp node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        appendNode(sb, "Element", node.element);
        for (SSTNode n : node.generators) {
            appendNewLineIndented(sb, n.accept(this));
        }
        level--;
        return sb.toString();
    }

    @Override
    public String visit(ExprTy.IfExp node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        appendNode(sb, "Test", node.test);
        appendNode(sb, "Then", node.body);
        appendNode(sb, "Else", node.orElse);
        level--;
        return sb.toString();
    }

    @Override
    public String visit(ExprTy.JoinedStr node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node)).append('\n');
        level++;
        sb.append(indent()).append(" Values:");
        level++;
        for (ExprTy v : node.values) {
            appendNewLineIndented(sb, v.accept(this));
        }
        level -= 2;
        return sb.toString();

    }

    @Override
    public String visit(ExprTy.Lambda node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        if (node.args != null) {
            appendNewLineIndented(sb, node.args.accept(this));
        }
        appendNode(sb, "Body", node.body);
        level--;
        return sb.toString();
    }

    @Override
    public String visit(ExprTy.List node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        if (node.context != null && node.context != ExprContextTy.Load) {
            sb.append(" Context: ").append(node.context);
        }
        if (node.elements != null) {
            sb.append('\n');
            level++;
            sb.append(indent()).append("Values:");
            level++;
            for (SSTNode value : node.elements) {
                appendNewLineIndented(sb, value.accept(this));
            }
            level--;
            level--;
        }
        return sb.toString();
    }

    @Override
    public String visit(ExprTy.ListComp node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node)).append('\n');
        level++;
        sb.append(indent()).append("Element: ").append(node.element.accept(this));
        for (SSTNode n : node.generators) {
            appendNewLineIndented(sb, n.accept(this));
        }
        level--;
        return sb.toString();
    }

    @Override
    public String visit(ExprTy.Name node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node)).append(" Value: \"").append(node.id).append('"');
        if (node.context != ExprContextTy.Load) {
            sb.append(' ').append(node.context);
        }
        return sb.toString();

    }

    @Override
    public String visit(ExprTy.NamedExpr node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        appendNewLineIndented(sb, "Target: ");
        putOnSameLineIfShort(sb, node.target);
        appendNewLineIndented(sb, "Value: ");
        putOnSameLineIfShort(sb, node.value);
        level--;
        return sb.toString();

    }

    @Override
    public String visit(ExprTy.Set node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node)).append('\n');
        level++;
        sb.append(indent()).append("Values:");
        level++;
        for (SSTNode value : node.elements) {
            appendNewLineIndented(sb, value.accept(this));
        }
        level--;
        level--;
        return sb.toString();
    }

    @Override
    public String visit(ExprTy.SetComp node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node)).append('\n');
        level++;
        sb.append(indent()).append("Element: ").append(node.element.accept(this));
        for (SSTNode n : node.generators) {
            appendNewLineIndented(sb, n.accept(this));
        }
        level--;
        return sb.toString();
    }

    @Override
    public String visit(ExprTy.Slice node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        sb.append('\n').append(indent()).append("Start: ");
        if (node.lower != null) {
            putOnSameLineIfShort(sb, node.lower);
        }
        sb.append('\n').append(indent()).append("Stop: ");
        if (node.upper != null) {
            putOnSameLineIfShort(sb, node.upper);
        }
        sb.append('\n').append(indent()).append("Step: ");
        if (node.step != null) {
            putOnSameLineIfShort(sb, node.step);
        }
        level--;
        return sb.toString();

    }

    @Override
    public String visit(ExprTy.Starred node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        if (node.context != null) {
            sb.append('\n').append(indent()).append(" Context: ").append(node.context);
        }
        sb.append('\n').append(indent()).append(" Expr: ");
        putOnSameLineIfShort(sb, node.value);
        level--;
        return sb.toString();
    }

    @Override
    public String visit(ExprTy.Subscript node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        if (node.context != null && node.context != ExprContextTy.Load) {
            sb.append(" Context: ").append(node.context);
        }
        level++;
        appendNode(sb, "Slice", node.slice);
        appendNode(sb, "Value", node.value);
        level--;
        return sb.toString();
    }

    @Override
    public String visit(ExprTy.Tuple node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        if (node.context != null && node.context != ExprContextTy.Load) {
            sb.append(" Context: ").append(node.context);
        }
        if (node.elements != null) {
            sb.append('\n');
            level++;
            sb.append(indent()).append("Values:");
            level++;
            for (SSTNode value : node.elements) {
                appendNewLineIndented(sb, value.accept(this));
            }
            level--;
            level--;
        }
        return sb.toString();
    }

    @Override
    public String visit(ExprTy.UnaryOp node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node)).append("\n");
        level++;
        sb.append(indent()).append("Op: ").append(node.op.toString()).append("\n");
        sb.append(indent()).append("Value: ").append(node.operand.accept(this));
        level--;
        return sb.toString();

    }

    @Override
    public String visit(ExprTy.Yield node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        if (node.value != null) {
            level++;
            appendNewLineIndented(sb, node.value.accept(this));
            level--;
        }
        return sb.toString();
    }

    @Override
    public String visit(ExprTy.YieldFrom node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        if (node.value != null) {
            level++;
            appendNewLineIndented(sb, node.value.accept(this));
            level--;
        }
        return sb.toString();
    }

    @Override
    public String visit(KeywordTy node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        appendNewLineIndented(sb, node.arg).append(": ").append(node.value.accept(this));
        level--;
        return sb.toString();
    }

    @Override
    public String visit(Expression node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        if (node.body != null) {
            level++;
            appendNewLineIndented(sb, node.body.accept(this));
            level--;
        }
        return sb.toString();
    }

    @Override
    public String visit(FunctionType node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        return sb.toString();
    }

    @Override
    public String visit(Interactive node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        if (node.body != null) {
            level++;
            for (SSTNode child : node.body) {
                appendNewLineIndented(sb, child.accept(this));
            }
            level--;
        }
        return sb.toString();
    }

    @Override
    public String visit(Module node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        if (node.body != null) {
            level++;
            for (SSTNode child : node.body) {
                appendNewLineIndented(sb, child.accept(this));
            }
            level--;
        }
        return sb.toString();
    }

    @Override
    public String visit(TypeIgnore node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        return sb.toString();

    }

    @Override
    public String visit(AnnAssign node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node)).append(node.isSimple ? "simple" : "not simple").append("\n");
        level++;
        sb.append(indent()).append("Annotation: ").append(node.annotation.accept(this)).append('\n');
        sb.append(indent()).append("LHS: ").append(node.target.accept(this)).append('\n');
        sb.append(indent()).append("RHS: ").append(node.value == null ? "<null>" : node.value.accept(this));
        level--;
        return sb.toString();
    }

    @Override
    public String visit(Assert node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        appendNode(sb, "Test", node.test);
        appendNode(sb, "Msg", node.msg);
        level--;
        return sb.toString();

    }

    @Override
    public String visit(Assign node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node)).append("\n");
        level++;
        sb.append(indent()).append("LHS: ");
        if (node.targets.length == 1) {
            putOnSameLineIfShort(sb, node.targets[0]);
            sb.append("\n");
        } else {
            sb.append("\n");
            level++;
            for (SSTNode lh : node.targets) {
                sb.append(indent()).append(lh.accept(this)).append("\n");
            }
            level--;
        }
        sb.append(indent()).append("RHS: ");
        putOnSameLineIfShort(sb, node.value);
        if (node.typeComment != null) {
            sb.append('\n');
            sb.append(indent()).append("TypeComment: ").append(node.typeComment);
        }
        level--;
        return sb.toString();
    }

    @Override
    public String visit(AsyncFor node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        appendNode(sb, "Target", node.target);
        appendNode(sb, "Iter", node.iter);
        appendNodes(sb, "Body", node.body);
        appendNodes(sb, "Else", node.orElse);
        level--;
        return sb.toString();
    }

    @Override
    public String visit(AsyncFunctionDef node) {
        return visitFunctionOrAsyncFunction(node, node.name, node.args, node.body, node.decoratorList, node.returns, node.typeComment, node.typeParams);
    }

    @Override
    public String visit(AsyncWith node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        appendNodes(sb, "Items", node.items);
        appendNodes(sb, "Body", node.body);
        if (node.typeComment != null) {
            appendString(sb, "TypeComment", node.typeComment);
        }
        level--;
        return sb.toString();
    }

    @Override
    public String visit(AugAssign node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        appendString(sb, "Op: ", node.op);
        appendNode(sb, "LHS", node.target);
        appendNode(sb, "RHS", node.value);
        level--;
        return sb.toString();
    }

    @Override
    public String visit(ClassDef node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node)).append(" ").append(node.name);
        level++;
        appendNodes(sb, "Decorators", node.decoratorList);
        appendNodes(sb, "Bases", node.bases);
        appendNodes(sb, "Keywords", node.keywords);
        appendNodes(sb, "TypeParams", node.typeParams);
        appendNewLineIndented(sb, "---- Class body of ").append(node.name).append(" ----");
        for (SSTNode stm : node.body) {
            appendNewLineIndented(sb, stm.accept(this));
        }
        if (sb.lastIndexOf("\n") != sb.length() - 1) {
            sb.append('\n');
        }
        sb.append(indent()).append("---- End of ").append(node.name).append(" class ----");
        level--;
        return sb.toString();

    }

    @Override
    public String visit(Delete node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        sb.append('\n').append(indent()).append("Targets:");
        level++;
        for (SSTNode n : node.targets) {
            sb.append('\n').append(indent()).append(n.accept(this));
        }
        level--;
        level--;
        return sb.toString();
    }

    @Override
    public String visit(Expr node) {
        return node.value.accept(this);
    }

    @Override
    public String visit(For node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        appendNode(sb, "Target", node.target);
        appendNode(sb, "Iter", node.iter);
        appendNodes(sb, "Body", node.body);
        appendNodes(sb, "Else", node.orElse);
        level--;
        return sb.toString();
    }

    @Override
    public String visit(FunctionDef node) {
        return visitFunctionOrAsyncFunction(node, node.name, node.args, node.body, node.decoratorList, node.returns, node.typeComment, node.typeParams);
    }

    private String visitFunctionOrAsyncFunction(SSTNode node, String name, ArgumentsTy args, StmtTy[] body, ExprTy[] decoratorList, ExprTy returns, Object typeComment, TypeParamTy[] typeParams) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        appendNewLineIndented(sb, "Name:").append(name);
        if (decoratorList != null) {
            appendNewLineIndented(sb, "Decorators:");
            for (SSTNode n : decoratorList) {
                appendNewLineIndented(sb, n.accept(this));
            }
        }
        if (args != null) {
            appendNewLineIndented(sb, args.accept(this));
        }
        if (returns != null) {
            appendNode(sb, "Result Annotation", returns);
        }
        if (typeComment != null) {
            appendNewLineIndented(sb, "Type Comment: ").append(typeComment);
        }
        appendNodes(sb, "TypeParams", typeParams);
        appendNewLineIndented(sb, "---- Function body of ").append(name).append(" ----");
        for (SSTNode stm : body) {
            appendNewLineIndented(sb, stm.accept(this));
        }
        if (sb.lastIndexOf("\n") != sb.length() - 1) {
            sb.append('\n');
        }
        sb.append(indent()).append("---- End of ").append(name).append(" function ----");
        level--;

        return sb.toString();
    }

    @Override
    public String visit(StmtTy.Global node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node)).append(": ");
        for (String id : node.names) {
            sb.append(id).append(' ');
        }
        return sb.toString();
    }

    @Override
    public String visit(StmtTy.If node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        appendNode(sb, "Test", node.test);
        appendNodes(sb, "Then", node.body);
        appendNodes(sb, "Else", node.orElse);
        level--;
        return sb.toString();
    }

    @Override
    public String visit(StmtTy.Import node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        for (AliasTy a : node.names) {
            appendNewLineIndented(sb, a.accept(this));
        }
        level--;
        return sb.toString();

    }

    @Override
    public String visit(StmtTy.ImportFrom node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node)).append(" Level ").append(node.level).append(' ').append(node.module);
        level++;
        for (AliasTy a : node.names) {
            appendNewLineIndented(sb, a.accept(this));
        }
        level--;
        return sb.toString();

    }

    @Override
    public String visit(StmtTy.Match node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        appendNode(sb, "Subject", node.subject);
        appendNodes(sb, "Cases", node.cases);
        level--;
        return sb.toString();
    }

    @Override
    public String visit(MatchCaseTy node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        appendNode(sb, "Pattern", node.pattern);
        appendNode(sb, "Guard", node.guard);
        appendNodes(sb, "Body", node.body);
        level--;
        return sb.toString();
    }

    @Override
    public String visit(MatchAs node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        appendNode(sb, "Pattern", node.pattern);
        if (node.name != null) {
            appendNewLineIndented(sb, "Name: ").append(node.name);
        }
        level--;
        return sb.toString();
    }

    @Override
    public String visit(MatchClass node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        appendNode(sb, "Cls", node.cls);
        appendNodes(sb, "Patterns", node.patterns);
        if (node.kwdAttrs != null) {
            appendNewLineIndented(sb, "KwdAttrs:");
            for (String s : node.kwdAttrs) {
                sb.append(' ').append(s);
            }
        }
        appendNodes(sb, "KwdPatterns", node.kwdPatterns);
        level--;
        return sb.toString();
    }

    @Override
    public String visit(MatchMapping node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        appendNodes(sb, "Keys", node.keys);
        appendNodes(sb, "Patterns", node.patterns);
        if (node.rest != null) {
            appendNewLineIndented(sb, "Rest: ").append(node.rest);
        }
        level--;
        return sb.toString();
    }

    @Override
    public String visit(MatchOr node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        appendNodes(sb, "Patterns", node.patterns);
        level--;
        return sb.toString();
    }

    @Override
    public String visit(MatchSequence node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        appendNodes(sb, "Patterns", node.patterns);
        level--;
        return sb.toString();

    }

    @Override
    public String visit(MatchSingleton node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        sb.append(", value: ");
        appendConstantValue(sb, node.value);
        return sb.toString();
    }

    @Override
    public String visit(MatchStar node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        if (node.name != null) {
            sb.append(": ").append(node.name);
        }
        return sb.toString();

    }

    @Override
    public String visit(MatchValue node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        appendNode(sb, "Value", node.value);
        level--;
        return sb.toString();
    }

    @Override
    public String visit(StmtTy.Nonlocal node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node)).append(": ");
        for (String id : node.names) {
            sb.append(id).append(' ');
        }
        return sb.toString();
    }

    @Override
    public String visit(StmtTy.Raise node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        appendNode(sb, "Exc", node.exc);
        appendNode(sb, "Cause", node.cause);
        level--;
        return sb.toString();

    }

    @Override
    public String visit(StmtTy.Return node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        if (node.value != null) {
            sb.append(" ");
            putOnSameLineIfShort(sb, node.value);
        }
        return sb.toString();
    }

    @Override
    public String visit(StmtTy.Try node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        appendNodes(sb, "Body", node.body);
        appendNodes(sb, "Except", node.handlers);
        appendNodes(sb, "Finally", node.finalBody);
        appendNodes(sb, "Else", node.orElse);
        level--;
        return sb.toString();
    }

    @Override
    public String visit(StmtTy.TryStar node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        appendNodes(sb, "Body", node.body);
        appendNodes(sb, "Except", node.handlers);
        appendNodes(sb, "Finally", node.finalBody);
        appendNodes(sb, "Else", node.orElse);
        level--;
        return sb.toString();
    }

    @Override
    public String visit(ExceptHandler node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        appendNode(sb, "Type", node.type);
        appendString(sb, "Var: ", node.name);
        appendNodes(sb, "Body", node.body);
        level--;
        return sb.toString();
    }

    @Override
    public String visit(StmtTy.While node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        appendNode(sb, "Condition", node.test);
        appendNodes(sb, "Body", node.body);
        appendNodes(sb, "Else", node.orElse);
        level--;
        return sb.toString();
    }

    @Override
    public String visit(StmtTy.With node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        appendNodes(sb, "Items", node.items);
        appendNodes(sb, "Body", node.body);
        if (node.typeComment != null) {
            appendString(sb, "TypeComment", node.typeComment);
        }
        level--;
        return sb.toString();
    }

    @Override
    public String visit(WithItemTy node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        appendNode(sb, "ContextExpr", node.contextExpr);
        appendNode(sb, "OptionalVars", node.optionalVars);
        level--;
        return sb.toString();

    }

    @Override
    public String visit(StmtTy.Break node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        return sb.toString();
    }

    @Override
    public String visit(StmtTy.Continue node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        return sb.toString();
    }

    @Override
    public String visit(StmtTy.Pass node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        return sb.toString();
    }

    @Override
    public String visit(TypeAlias node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        appendNode(sb, "Name", node.name);
        appendNodes(sb, "TypeParams", node.typeParams);
        appendNode(sb, "Value", node.value);
        level--;
        return sb.toString();
    }

    @Override
    public String visit(TypeVar node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        appendString(sb, "Name: ", node.name);
        appendNode(sb, "Bound", node.bound);
        level--;
        return sb.toString();
    }

    @Override
    public String visit(ParamSpec node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        appendString(sb, "Name: ", node.name);
        level--;
        return sb.toString();
    }

    @Override
    public String visit(TypeVarTuple node) {
        StringBuilder sb = new StringBuilder();
        sb.append(addHeader(node));
        level++;
        appendString(sb, "Name: ", node.name);
        level--;
        return sb.toString();
    }
}
