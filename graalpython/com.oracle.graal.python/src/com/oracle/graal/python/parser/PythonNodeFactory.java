/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.python.parser;


import com.oracle.graal.python.PythonLanguage;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.nodes.EmptyNode;
import com.oracle.graal.python.nodes.ModuleRootNode;
import com.oracle.graal.python.nodes.NodeFactory;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.control.BaseBlockNode;
import com.oracle.graal.python.nodes.control.ReturnTargetNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.function.FunctionRootNode;
import com.oracle.graal.python.nodes.literal.StringLiteralNode;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.graal.python.parser.ScopeInfo.ScopeKind;
import com.oracle.graal.python.parser.sst.ArgListBuilder;
import com.oracle.graal.python.parser.sst.AssignmentSSTNode;
import com.oracle.graal.python.parser.sst.AugAssignmentSSTNode;
import com.oracle.graal.python.parser.sst.BlockSSTNode;
import com.oracle.graal.python.parser.sst.ClassSSTNode;
import com.oracle.graal.python.parser.sst.CollectionSSTNode;
import com.oracle.graal.python.parser.sst.FactorySSTVisitor;
import com.oracle.graal.python.parser.sst.ForComprehensionSSTNode;
import com.oracle.graal.python.parser.sst.ForSSTNode;
import com.oracle.graal.python.parser.sst.ImportFromSSTNode;
import com.oracle.graal.python.parser.sst.ImportSSTNode;
import com.oracle.graal.python.parser.sst.SSTNode;
import com.oracle.graal.python.parser.sst.SimpleSSTNode;
import com.oracle.graal.python.parser.sst.StarSSTNode;
import com.oracle.graal.python.parser.sst.VarLookupSSTNode;
import com.oracle.graal.python.parser.sst.WithSSTNode;
import com.oracle.graal.python.parser.sst.YieldExpressionSSTNode;
import com.oracle.graal.python.runtime.PythonParser;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import org.antlr.v4.runtime.ParserRuleContext;



public final class PythonNodeFactory {
    
    private final PythonLanguage language;
    private final NodeFactory nodeFactory;
    private final ScopeEnvironment scopeEnvironment;
    private final Source source;
    
    
    public PythonNodeFactory(PythonLanguage language, Source source) {
        this.language = language;
        this.nodeFactory = NodeFactory.create(language);
        this.scopeEnvironment = new ScopeEnvironment(nodeFactory);
        this.source = source;
    }
    
    
    private void logIndent() {
        ScopeInfo currentScope = scopeEnvironment.getCurrentScope();
        while (currentScope != null && currentScope.getParent() != null) {
            System.out.print("    ");
            currentScope = currentScope.getParent();
        }
    }
    
    void log(Object... args) {
//        logIndent();
//        StackTraceElement[] stackTrace = new RuntimeException().getStackTrace();
//        System.out.print(stackTrace[1].getMethodName());
//        for (Object o : args) {
//            System.out.print(' ');
//            print(o);
//        }
//        System.out.println();
    }

    private static void print(Object o) {
        if (o == null) {
            System.out.print('-');
        } else if (o instanceof Object[]) {
            Object[] array = (Object[]) o;
            System.out.print('[');
            for (int i = 0; i < array.length; i++) {
                if (i > 0) {
                    System.out.print(", ");
                }
                print(array[i]);
            }
            System.out.print(']');
        } else if (o instanceof FrameDescriptor) {
            System.out.print(((FrameDescriptor) o).getSlots());
        } else {
            System.out.print(o);
        }
    }

    public ScopeEnvironment getScopeEnvironment() {
        return scopeEnvironment;
    }
    
    
    
    public ExpressionNode asExpression(PNode node) {
        if (node instanceof ExpressionNode.ExpressionStatementNode) {
            return ((ExpressionNode.ExpressionStatementNode) node).getExpression();
        }
        if (node instanceof ExpressionNode) {
            return (ExpressionNode) node;
        }
        if (node instanceof StatementNode) {
            ExpressionNode emptyNode = EmptyNode.create().withSideEffect((StatementNode) node);
            return emptyNode;
        }
        if (node == null) {
            return EmptyNode.create();
        } else {
            throw new IllegalArgumentException("unexpected class: " + node.getClass());
        }
    }

    public SSTNode createImport(String name, int level, String asName, int startOffset, int endOffset) {
        scopeEnvironment.createLocal(asName == null ? name : asName);
        return new ImportSSTNode(scopeEnvironment.getCurrentScope(), name, asName, startOffset, endOffset);
    }
    
    public SSTNode createImportFrom(String from, String[][] asNames, int startOffset, int endOffset) {
        if (asNames != null) {
            for (String[] asName : asNames) {
                scopeEnvironment.createLocal(asName[1] == null ? asName[0] : asName[1]);
            }
        }
    
        return new ImportFromSSTNode(scopeEnvironment.getCurrentScope(), from, asNames, startOffset, endOffset);
    }

    public VarLookupSSTNode createVariableLookup(String name, int start, int stop) {
        scopeEnvironment.addSeenVar(name);
        return new VarLookupSSTNode(name, start, stop);
    }
    
    public SSTNode createClassDefinition(String name,  ArgListBuilder baseClasses, SSTNode body, int start, int stop) {
//        scopeEnvironment.createLocal(name);
        return new ClassSSTNode(scopeEnvironment.getCurrentScope(), name, baseClasses, body, start, stop);
    }

    public SSTNode registerGlobal(String[] names, int startOffset, int endOffset) {
        ScopeInfo scopeInfo = scopeEnvironment.getCurrentScope();
        ScopeInfo globalScope = scopeEnvironment.getGlobalScope();
        for (String name : names) {
            scopeInfo.addExplicitGlobalVariable(name);
            globalScope.createSlotIfNotPresent(name);
        }
        return new SimpleSSTNode(SimpleSSTNode.Type.EMPTY, startOffset, endOffset);
    }

    public SSTNode registerNonLocal(String[] names, int startOffset, int endOffset) {
        ScopeInfo scopeInfo = scopeEnvironment.getCurrentScope();
        for (String name : names) {
            scopeInfo.addExplicitNonlocalVariable(name);
        }
        return new SimpleSSTNode(SimpleSSTNode.Type.EMPTY, startOffset, endOffset);
    }

    public WithSSTNode createWith(SSTNode expression, SSTNode target, SSTNode body, int start, int end) {
        String name;
        if (target instanceof VarLookupSSTNode) {
            name = ((VarLookupSSTNode)target).getName();
            scopeEnvironment.createLocal(name);
        }        
        return new WithSSTNode(expression, target, body, start, end);
    }
    
    public SSTNode createForComprehension(boolean async, SSTNode target, SSTNode name, SSTNode[] variables, SSTNode iterator, SSTNode[] conditions, PythonBuiltinClassType resultType, int lineNumber, int level,  int startOffset, int endOffset) {
        for (SSTNode variable: variables) {
            declareVar(variable);
        }
        return new ForComprehensionSSTNode(scopeEnvironment.getCurrentScope(), async, target, name, variables, iterator, conditions, resultType, lineNumber, level, startOffset, endOffset);
    }
    
    public SSTNode createAssignment(SSTNode[] lhs, SSTNode rhs, int start, int stop) {
        for(SSTNode variable : lhs) {
            declareVar(variable);
        }
        return new AssignmentSSTNode(lhs, rhs, start, stop);
    }
    
    public SSTNode createAugAssignment(SSTNode lhs, String operation, SSTNode rhs, int startOffset, int endOffset) {
        declareVar(lhs);
        return new AugAssignmentSSTNode(lhs, operation, rhs, startOffset, endOffset);
    }
    
    private void declareVar(SSTNode value) {
        if (value instanceof VarLookupSSTNode) {
            String name = ((VarLookupSSTNode)value).getName();
            if (!scopeEnvironment.isNonlocal(name)) {
                scopeEnvironment.createLocal(
                        scopeEnvironment.getCurrentScope().getScopeKind() != ScopeKind.Class
                        ? name
                        : ScopeEnvironment.CLASS_VAR_PREFIX + name
                );
            }
        } else if (value instanceof CollectionSSTNode) {
            CollectionSSTNode collection = (CollectionSSTNode) value;
            for (SSTNode variable : collection.getValues()) {
                declareVar(variable);
            }
        } else if (value instanceof StarSSTNode) {
            declareVar(((StarSSTNode)value).getValue());
        }
    }
    
    public ForSSTNode createForSSTNode(SSTNode[] targets, SSTNode iterator, SSTNode body, boolean containsContinue, int startOffset, int endOffset) {
        for(SSTNode target : targets) {
            if (target instanceof VarLookupSSTNode) {
                scopeEnvironment.createLocal(((VarLookupSSTNode)target).getName());
            }
        }
        return new ForSSTNode(targets, iterator, body, containsContinue, startOffset, endOffset);
    }
    
    public YieldExpressionSSTNode createYieldExpressionSSTNode(SSTNode value, boolean isFrom, int startOffset, int endOffset) {
        scopeEnvironment.setToGeneratorScope();
        return new YieldExpressionSSTNode(value, isFrom, startOffset, endOffset);
    }
    
    public Node createParserResult(SSTNode parserSSTResult, PythonParser.ParserMode mode, PythonParser.ParserErrorCallback errors, Source source, Frame currentFrame) {
        Node result;
        scopeEnvironment.setCurrentScope(scopeEnvironment.getGlobalScope());
        FactorySSTVisitor factoryVisitor = new FactorySSTVisitor(errors, getScopeEnvironment(), errors.getLanguage().getNodeFactory(), source);
        ExpressionNode body =  mode == PythonParser.ParserMode.Eval
                ? (ExpressionNode)parserSSTResult.accept(factoryVisitor)
                : factoryVisitor.asExpression((BlockSSTNode)parserSSTResult);
        FrameDescriptor fd = currentFrame == null ? null : currentFrame.getFrameDescriptor();
        switch (mode) {
            case Eval:
                scopeEnvironment.setCurrentScope(scopeEnvironment.getGlobalScope());
                StatementNode evalReturn = nodeFactory.createFrameReturn(nodeFactory.createWriteLocal(body, scopeEnvironment.getReturnSlot()));
                ReturnTargetNode returnTarget = new ReturnTargetNode(evalReturn, nodeFactory.createReadLocal(scopeEnvironment.getReturnSlot()));
                FunctionRootNode functionRoot = nodeFactory.createFunctionRoot(body.getSourceSection(), source.getName(), false, fd, returnTarget, scopeEnvironment.getExecutionCellSlots(), Signature.EMPTY);
                result = functionRoot;
                break;
            case File:
                result = nodeFactory.createModuleRoot(source.getName(), getModuleDoc(body), body, scopeEnvironment.getGlobalScope().getFrameDescriptor());
                break;    
            case InlineEvaluation:
                result = body;
                break;
            case InteractiveStatement:
                result = nodeFactory.createModuleRoot("<expression>", getModuleDoc(body), body, fd);
                break;
            case Statement:
                ExpressionNode printExpression = nodeFactory.createPrintExpression(body);
                printExpression.assignSourceSection(body.getSourceSection());
                result = nodeFactory.createModuleRoot("<expression>", getModuleDoc(body), body, fd);
                break;
            default:
                throw new RuntimeException("unexpected mode: " + mode);
        }
        return result;
    }
           
    public String getModuleDoc (ExpressionNode from) {
        StringLiteralNode sln = (new DocExtractor()).extract(from);
        String doc = null;
        if (sln != null) {
            doc = sln.getValue();
        }
        return doc;
    }
    
    public ScopeInfo createScope(ParserRuleContext ctx, ScopeKind kind) {
        return createScope(ctx, kind, null);
    }
    
    public ScopeInfo createScope(String name, ScopeKind kind) {
        log(kind, null);
        if (kind == ScopeKind.Function && !name.equals("lambda")) {
            scopeEnvironment.createLocal(scopeEnvironment.getCurrentScope().getScopeKind() == ScopeKind.Class
                    ? ScopeEnvironment.CLASS_VAR_PREFIX + name
                    : name);
        }
        return scopeEnvironment.pushScope(name, kind, null);
    }

    public ScopeInfo createScope(ParserRuleContext ctx, ScopeKind kind, FrameDescriptor locals) {
        log(kind, locals);
        return scopeEnvironment.pushScope(ctx, kind, locals);
    }

    public void leaveScope() {
        log();
        scopeEnvironment.popScope();        
    }
    
    public ScopeInfo getCurrentScope() {
        return scopeEnvironment.getCurrentScope();
    }
    
    public boolean createGeneratorScope(SSTNode target, SSTNode name) {
        log(target, name);
        createScope(name.toString(), ScopeKind.Generator);
        return false;
    }

    public void leaveGeneratorScope(boolean scopeCreated) {
        log(scopeCreated);
        if (scopeCreated) {
            leaveScope();
        }
    }
    
    private SourceSection createSourceSection(int start, int stop) {
        if (source.getLength() > start && source.getLength() >= stop) {
            return source.createSection(start, stop - start);
        } else {
            return source.createUnavailableSection();
        }
    }
    
    public static class DocExtractor {
        
        private boolean firstStatement = true;
        
        public StringLiteralNode extract(StatementNode node) {
            if (node instanceof ExpressionNode.ExpressionStatementNode) {
                return extract(((ExpressionNode.ExpressionStatementNode) node).getExpression());
            } else if (node instanceof BaseBlockNode) {
                StatementNode[] statements = ((BaseBlockNode)node).getStatements();
                if (statements != null && statements.length > 0) {
                    return extract(statements[0]);
                }
                return null;
            }
            return null;
        }
        
        public StringLiteralNode extract(ExpressionNode node) {
            if (node instanceof StringLiteralNode) {
                return (StringLiteralNode) node;
            } else if (node instanceof ExpressionNode.ExpressionWithSideEffect) {
                return extract(((ExpressionNode.ExpressionWithSideEffect)node).getSideEffect());
            } else if (node instanceof ExpressionNode.ExpressionWithSideEffects) {
                StatementNode[] sideEffects = ((ExpressionNode.ExpressionWithSideEffects)node).getSideEffects();
                if (sideEffects != null && sideEffects.length > 0) {
                    return extract(sideEffects[0]);
                }
            }
            return null;
        }
    }
}
