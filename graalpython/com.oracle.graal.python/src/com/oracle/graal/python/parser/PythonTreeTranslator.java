/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.parser;

import static com.oracle.graal.python.nodes.BuiltinNames.DICT;
import static com.oracle.graal.python.nodes.BuiltinNames.LIST;
import static com.oracle.graal.python.nodes.BuiltinNames.SET;
import static com.oracle.graal.python.nodes.BuiltinNames.SUPER;
import static com.oracle.graal.python.nodes.BuiltinNames.__BUILD_CLASS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__CLASS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__DOC__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.SyntaxError;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Function;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PEllipsis;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.BytesUtils;
import com.oracle.graal.python.builtins.objects.complex.PComplex;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.nodes.EmptyNode;
import com.oracle.graal.python.nodes.NodeFactory;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode;
import com.oracle.graal.python.nodes.call.PythonCallNode;
import com.oracle.graal.python.nodes.classes.ClassDefinitionPrologueNode;
import com.oracle.graal.python.nodes.control.BlockNode;
import com.oracle.graal.python.nodes.control.ForNode;
import com.oracle.graal.python.nodes.control.GetIteratorExpressionNode;
import com.oracle.graal.python.nodes.control.LoopNode;
import com.oracle.graal.python.nodes.control.ReturnTargetNode;
import com.oracle.graal.python.nodes.expression.AndNode;
import com.oracle.graal.python.nodes.expression.CastToBooleanNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.expression.OrNode;
import com.oracle.graal.python.nodes.frame.ReadGlobalOrBuiltinNode;
import com.oracle.graal.python.nodes.frame.ReadLocalNode;
import com.oracle.graal.python.nodes.frame.ReadNameNode;
import com.oracle.graal.python.nodes.frame.ReadNode;
import com.oracle.graal.python.nodes.frame.WriteNode;
import com.oracle.graal.python.nodes.function.ClassBodyRootNode;
import com.oracle.graal.python.nodes.function.FunctionDefinitionNode;
import com.oracle.graal.python.nodes.function.FunctionDefinitionNode.KwDefaultExpressionNode;
import com.oracle.graal.python.nodes.function.FunctionRootNode;
import com.oracle.graal.python.nodes.function.GeneratorExpressionNode;
import com.oracle.graal.python.nodes.function.GeneratorFunctionDefinitionNode;
import com.oracle.graal.python.nodes.literal.LiteralNode;
import com.oracle.graal.python.nodes.literal.StarredExpressionNode;
import com.oracle.graal.python.nodes.literal.StringLiteralNode;
import com.oracle.graal.python.nodes.literal.TupleLiteralNode;
import com.oracle.graal.python.nodes.statement.ExceptNode;
import com.oracle.graal.python.nodes.statement.RaiseNode;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.graal.python.nodes.subscript.GetItemNode;
import com.oracle.graal.python.parser.antlr.Python3BaseVisitor;
import com.oracle.graal.python.parser.antlr.Python3Parser;
import com.oracle.graal.python.parser.antlr.Python3Parser.ArgumentContext;
import com.oracle.graal.python.parser.antlr.Python3Parser.Lambdef_bodyContext;
import com.oracle.graal.python.parser.antlr.Python3Parser.Lambdef_nocond_bodyContext;
import com.oracle.graal.python.parser.antlr.Python3Parser.VarargslistContext;
import com.oracle.graal.python.runtime.PythonParser.ParserErrorCallback;
import com.oracle.graal.python.runtime.PythonParser.ParserMode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

public final class PythonTreeTranslator extends Python3BaseVisitor<Object> {

    protected final ParserErrorCallback errors;
    protected final NodeFactory factory;
    protected final TranslationEnvironment environment;
    protected final LoopsBookKeeper loops;
    protected final AssignmentTranslator assigns;
    protected final Source source;
    protected final String name;

    protected final ParserMode mode;

    public PythonTreeTranslator(ParserErrorCallback errors, String name, TranslationEnvironment environment, Source source, ParserMode mode) {
        this.errors = errors;
        this.name = name;
        this.environment = environment;
        this.source = source;
        this.mode = mode;
        this.factory = errors.getLanguage().getNodeFactory();
        this.loops = new LoopsBookKeeper();
        this.assigns = new AssignmentTranslator(errors, environment, this);
    }

    public static Node translate(ParserErrorCallback errors, String name, ParserRuleContext input, TranslationEnvironment environment, Source source, ParserMode mode) {
        PythonTreeTranslator translator = new PythonTreeTranslator(errors, name, environment, source, mode);
        try {
            Object parseResult = input.accept(translator);
            if (mode == ParserMode.InlineEvaluation) {
                assert parseResult instanceof PNode : "expected PNode result for InlineEvaluation";
                return (Node) parseResult;
            } else {
                assert parseResult instanceof RootNode : "expected RootNode result from parsing";
                return (Node) parseResult;
            }
        } catch (PException e) {
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object visitChildren(RuleNode node) {
        int count = node.getChildCount();

        if (count == 0) {
            return null;
        } else if (count == 1) {
            Object result = node.getChild(0).accept(this);
            deriveSourceSection(node, result);
            return result;
        } else {
            ArrayList<Object> list = null;
            Object singleElement = null;
            for (int i = 0; i < count; i++) {
                Object element = node.getChild(i).accept(this);
                if (element != null) {
                    if (singleElement == null) {
                        singleElement = element;
                    } else {
                        if (list == null) {
                            if (singleElement instanceof ArrayList) {
                                list = (ArrayList<Object>) singleElement;
                            } else {
                                list = new ArrayList<>(count);
                                addToList(list, singleElement);
                            }
                        }
                        addToList(list, element);
                    }
                }
            }
            if (list != null) {
                return list;
            } else if (singleElement != null) {
                deriveSourceSection(node, singleElement);
                return singleElement;
            } else {
                return null;
            }
        }
    }

    private static void addToList(ArrayList<Object> list, Object element) {
        assert element != null;
        if (element instanceof ArrayList<?>) {
            list.addAll((ArrayList<?>) element);
        } else {
            list.add(element);
        }
    }

    protected void deriveSourceSection(RuleNode node, Object r) {
        if (r instanceof PNode && ((PNode) r).getSourceSection() == null) {
            SourceSection derivedSection = deriveSourceSection(node);
            if (derivedSection != null) {
                ((PNode) r).assignSourceSection(derivedSection);
            }
        }
    }

    private SourceSection createSourceSection(int start, int stop) {
        if (source.getLength() > start && source.getLength() >= stop) {
            return source.createSection(start, stop);
        } else {
            return source.createUnavailableSection();
        }
    }

    private SourceSection deriveSourceSection(RuleNode node) {
        if (node instanceof Token) {
            int start = ((Token) node).getStartIndex();
            int stop = ((Token) node).getStopIndex();
            return createSourceSection(start, stop);
        } else if (node instanceof ParserRuleContext) {
            int start = ((ParserRuleContext) node).getStart().getStartIndex();
            int stop = ((ParserRuleContext) node).getStop().getStopIndex();
            return createSourceSection(start, stop - start + 1);
        }
        return null;
    }

    @Override
    public Object visitFile_input(Python3Parser.File_inputContext ctx) {
        environment.pushScope(ctx.scope);
        ExpressionNode file = asExpression(super.visitFile_input(ctx));
        deriveSourceSection(ctx, file);
        environment.popScope();
        return factory.createModuleRoot(name, getModuleDoc(ctx), file, ctx.scope.getFrameDescriptor());
    }

    @Override
    public Object visitEval_input(Python3Parser.Eval_inputContext ctx) {
        environment.pushScope(ctx.scope);
        ExpressionNode node = (ExpressionNode) super.visitEval_input(ctx);
        deriveSourceSection(ctx, node);
        StatementNode evalReturn = factory.createFrameReturn(factory.createWriteLocal(node, environment.getReturnSlot()));
        ReturnTargetNode returnTarget = new ReturnTargetNode(evalReturn, factory.createReadLocal(environment.getReturnSlot()));
        FunctionRootNode functionRoot = factory.createFunctionRoot(node.getSourceSection(), name, false, ctx.scope.getFrameDescriptor(), returnTarget, environment.getExecutionCellSlots(),
                        Signature.EMPTY);
        environment.popScope();
        return functionRoot;
    }

    @Override
    public Object visitSingle_input(Python3Parser.Single_inputContext ctx) {
        environment.pushScope(ctx.scope);
        ExpressionNode body = asExpression(super.visitSingle_input(ctx));
        deriveSourceSection(ctx, body);
        environment.popScope();
        if (mode == ParserMode.InlineEvaluation) {
            return body;
        } else if (mode == ParserMode.Statement) {
            body = factory.createPrintExpression(body);
            deriveSourceSection(ctx, body);
            return factory.createModuleRoot("<expression>", getModuleDoc(ctx), body, ctx.scope.getFrameDescriptor());
        } else {
            return factory.createModuleRoot("<expression>", getModuleDoc(ctx), body, ctx.scope.getFrameDescriptor());
        }
    }

    private String getModuleDoc(ParserRuleContext ctx) {
        Python3Parser.Simple_stmtContext firstStatement = null;
        if (ctx instanceof Python3Parser.Single_inputContext) {
            firstStatement = ((Python3Parser.Single_inputContext) ctx).simple_stmt();
        } else if (ctx instanceof Python3Parser.File_inputContext) {
            List<Python3Parser.StmtContext> stmt = ((Python3Parser.File_inputContext) ctx).stmt();
            if (!stmt.isEmpty()) {
                firstStatement = stmt.get(0).simple_stmt();
            }
        }

        if (firstStatement != null) {
            try {
                PNode stringNode = parseString(new String[]{firstStatement.getText().trim()});
                if (stringNode instanceof StringLiteralNode) {
                    return ((StringLiteralNode) stringNode).getValue();
                }
            } catch (Exception ignored) {
                // not a string literal
            }
        }
        return null;
    }

    @Override
    public Object visitAtom_expr(Python3Parser.Atom_exprContext ctx) {
        // TODO: deal with AWAIT
        if (ctx.trailer().isEmpty()) {
            return super.visitAtom_expr(ctx);
        } else {
            ExpressionNode expr = (ExpressionNode) visitAtom(ctx.atom());
            for (Python3Parser.TrailerContext t : ctx.trailer()) {
                expr = visitTrailerFrom(expr, t);
            }
            return expr;
        }
    }

    private ExpressionNode visitTrailerFrom(ExpressionNode owner, Python3Parser.TrailerContext t) {
        if (t.NAME() != null) {
            return factory.createGetAttribute(owner, t.NAME().getText());
        } else if (t.subscriptlist() != null) {
            return visitSubscriptFrom(owner, t);
        } else {
            assert t.getChild(0).getText().equals("(");
            return visitCallTo(owner, t.arglist());
        }
    }

    private void visitCallArglist(Python3Parser.ArglistContext arglist, List<ExpressionNode> argumentNodes, List<ExpressionNode> keywords, ExpressionNode[] splatArguments) {
        ExpressionNode starargs = null;
        ExpressionNode kwargs = null;
        if (arglist != null) {
            for (Python3Parser.ArgumentContext argctx : arglist.argument()) {
                ExpressionNode defaultarg = getDefaultarg(argctx);
                if (defaultarg != null) {
                    keywords.add(defaultarg);
                } else {
                    ExpressionNode arg = (ExpressionNode) argctx.accept(this);
                    if (isKwarg(argctx)) {
                        if (kwargs != null) {
                            kwargs = factory.createDictionaryConcat(kwargs, arg);
                        } else {
                            kwargs = arg;
                        }
                    } else if (isStararg(argctx)) {
                        if (kwargs != null) {
                            throw errors.raiseInvalidSyntax(source, deriveSourceSection(argctx), "iterable argument unpacking follows keyword argument unpacking");
                        }
                        if (starargs != null) {
                            starargs = factory.createBinaryOperation("+", starargs, arg);
                        } else {
                            starargs = arg;
                        }
                    } else {
                        if (!keywords.isEmpty()) {
                            throw errors.raiseInvalidSyntax(source, deriveSourceSection(argctx), "positional argument follows keyword argument");
                        }
                        if (kwargs != null) {
                            throw errors.raiseInvalidSyntax(source, deriveSourceSection(argctx), "positional argument follows keyword argument unpacking");
                        }
                        argumentNodes.add(arg);
                    }
                }
            }
        }
        splatArguments[0] = starargs;
        splatArguments[1] = kwargs;
    }

    private ExpressionNode visitCallTo(ExpressionNode owner, Python3Parser.ArglistContext arglist) {
        List<ExpressionNode> argumentNodes = new ArrayList<>();
        List<ExpressionNode> keywords = new ArrayList<>();
        ExpressionNode[] splatArguments = new ExpressionNode[2];
        visitCallArglist(arglist, argumentNodes, keywords, splatArguments);
        PythonCallNode callNode = PythonCallNode.create(owner, argumentNodes.toArray(new ExpressionNode[0]), keywords.toArray(new ExpressionNode[0]), splatArguments[0], splatArguments[1]);
        if (argumentNodes.size() == 0 && owner instanceof ReadGlobalOrBuiltinNode && ((ReadGlobalOrBuiltinNode) owner).getAttributeId().equals(SUPER)) {
            // super call without arguments
            environment.registerSpecialClassCellVar();
        }
        return callNode;
    }

    private ExpressionNode visitSubscriptFrom(ExpressionNode owner, Python3Parser.TrailerContext t) {
        List<ExpressionNode> subscriptlist = asList(visitSubscriptlist(t.subscriptlist()));
        ExpressionNode rhs;
        if (subscriptlist.size() > 1) {
            rhs = factory.createTupleLiteral(subscriptlist);
        } else {
            rhs = subscriptlist.get(0);
        }
        return factory.createSubscriptLoad(owner, rhs);
    }

    private ExpressionNode getDefaultarg(Python3Parser.ArgumentContext ctx) {
        if (ctx.test().size() == 2) {
            // see Python3 grammar definition. The lhs `test' node must really be a NAME
            // In CPython, ast.c ensures this
            String argName = ctx.test(0).accept(new ExtractNameVisitor());
            if (argName == null) {
                throw errors.raiseInvalidSyntax(source, deriveSourceSection(ctx), "Keyword can't be an expression");
            }
            return factory.createKeywordLiteral((ExpressionNode) ctx.test(1).accept(this), argName);
        } else {
            return null;
        }
    }

    private static boolean isStararg(Python3Parser.ArgumentContext ctx) {
        return ctx.getChild(0) instanceof TerminalNode && ctx.getChild(0).getText().equals("*");
    }

    private static boolean isKwarg(Python3Parser.ArgumentContext ctx) {
        return ctx.getChild(0) instanceof TerminalNode && ctx.getChild(0).getText().equals("**");
    }

    private static class ExtractNameVisitor extends Python3BaseVisitor<String> {
        @Override
        protected String aggregateResult(String aggregate, String nextResult) {
            return aggregate == null ? nextResult : aggregate;
        }

        @Override
        public String visitAtom(Python3Parser.AtomContext ctx) {
            return ctx.NAME() != null ? ctx.NAME().getText() : null;
        }
    }

    @Override
    public Object visitSubscript(Python3Parser.SubscriptContext ctx) {
        if (ctx.getChildCount() == 1) {
            ParseTree child = ctx.getChild(0);
            if (!(child instanceof TerminalNode)) {
                return super.visitSubscript(ctx);
            }
        }
        ExpressionNode start;
        int stopidx;
        if (ctx.getChild(0) instanceof TerminalNode) {
            start = EmptyNode.create();
            stopidx = 1;
        } else {
            start = (ExpressionNode) ctx.getChild(0).accept(this);
            stopidx = 2;
        }
        ExpressionNode stop;
        if (stopidx >= ctx.getChildCount() || ctx.getChild(stopidx) instanceof TerminalNode || ctx.getChild(stopidx) == ctx.sliceop()) {
            stop = EmptyNode.create();
        } else {
            stop = (ExpressionNode) ctx.getChild(stopidx).accept(this);
        }
        ExpressionNode step;
        if (ctx.sliceop() == null || ctx.sliceop().test() == null) {
            step = EmptyNode.create();
        } else {
            step = (ExpressionNode) ctx.sliceop().test().accept(this);
        }
        return factory.createSlice(start, stop, step);
    }

    @Override
    public Object visitAtom(Python3Parser.AtomContext ctx) {
        if (ctx.NUMBER() != null) {
            return parseNumber(ctx.NUMBER().getText());
        } else if (!ctx.STRING().isEmpty()) {
            String[] textStr = new String[ctx.STRING().size()];
            for (int i = 0; i < ctx.STRING().size(); i++) {
                textStr[i] = ctx.STRING().get(i).getText();
            }
            return parseString(textStr);
        } else if (ctx.NAME() != null) {
            return environment.findVariable(ctx.NAME().getText());
        } else if (ctx.getChildCount() == 1) {
            return parseSpecialLiteral(ctx);
        } else if (ctx.dictorsetmaker() != null) {
            return super.visitAtom(ctx);
        } else if (ctx.getChild(0).getText().equals("{")) { // empty dict
            return factory.createDictLiteral();
        } else if (ctx.getChild(0).getText().equals("[")) { // list
            if (ctx.testlist_comp() != null && ctx.testlist_comp().comp_for() != null) {
                return factory.callBuiltin(LIST, (ExpressionNode) super.visitAtom(ctx));
            } else {
                return factory.createListLiteral(asList(super.visitAtom(ctx)));
            }
        } else if (ctx.getChild(0).getText().equals("(")) { // tuple
            List<ExpressionNode> ll = asList(super.visitAtom(ctx));
            if (ll.size() != 1 || lastChildIsComma(ctx.testlist_comp())) {
                return factory.createTupleLiteral(ll);
            } else {
                return ll.get(0);
            }
        } else {
            return super.visitAtom(ctx);
        }
    }

    @Override
    public Object visitSetmaker(Python3Parser.SetmakerContext ctx) {
        if (ctx.comp_for() == null) {
            List<ExpressionNode> nodes = asList(super.visitSetmaker(ctx));
            return factory.createSetLiteral(nodes);
        } else {
            return factory.callBuiltin(SET, createComprehensionExpression(ctx, ctx.comp_for()));
        }
    }

    private ExpressionNode createComprehensionExpression(ParserRuleContext ctx, Python3Parser.Comp_forContext compctx) {
        return createComprehensionExpression(ctx, compctx, c -> (ExpressionNode) c.getChild(0).accept(this));
    }

    private ExpressionNode createComprehensionExpression(ParserRuleContext ctx, Python3Parser.Comp_forContext compctx, Function<ParserRuleContext, ExpressionNode> getBlock) {
        try {
            environment.pushScope(compctx.scope);
            ExpressionNode block = getBlock.apply(ctx);
            ExpressionNode yield = factory.createYield(block, environment.getReturnSlot());
            yield.assignSourceSection(block.getSourceSection());
            StatementNode body = createGeneratorExpression(ctx.getChild(Python3Parser.Comp_forContext.class, 0), asBlock(yield));
            SourceSection srcSection = body.getSourceSection();
            ExpressionNode returnTarget = new ReturnTargetNode(body, factory.createReadLocal(environment.getReturnSlot()));
            returnTarget.assignSourceSection(srcSection);
            int lineNum = ctx.getStart().getLine();
            GeneratorExpressionNode genExprDef = createGeneratorExpressionDefinition(returnTarget, lineNum);
            genExprDef.setEnclosingFrameDescriptor(environment.getEnclosingFrame());
            genExprDef.assignSourceSection(srcSection);
            return genExprDef;
        } finally {
            environment.popScope();
        }
    }

    private GeneratorExpressionNode createGeneratorExpressionDefinition(ExpressionNode body, int lineNum) {
        FrameDescriptor fd = environment.getCurrentFrame();
        String generatorName = environment.getCurrentScope().getParent().getScopeId() + ".<locals>.<genexp>:" + source.getName() + ":" + lineNum;
        FunctionRootNode funcRoot = factory.createFunctionRoot(body.getSourceSection(), generatorName, true, fd, body, environment.getExecutionCellSlots(), Signature.EMPTY);
        GeneratorTranslator gtran = new GeneratorTranslator(funcRoot, true);
        RootCallTarget callTarget = gtran.translate();
        ExpressionNode loopIterator = gtran.getGetOuterMostLoopIterator();
        return new GeneratorExpressionNode(generatorName, callTarget, loopIterator, fd, environment.getDefinitionCellSlots(), environment.getExecutionCellSlots(),
                        gtran.getFlagSlots(), gtran.getIndexSlots());
    }

    @Override
    public Object visitDictmaker(Python3Parser.DictmakerContext ctx) {
        if (ctx.comp_for() != null) {
            return visitDictmakerComprehension(ctx);
        } else {
            return visitNormalDictmaker(ctx);
        }
    }

    private ExpressionNode visitNormalDictmaker(Python3Parser.DictmakerContext ctx) {
        ArrayList<ExpressionNode> keys = new ArrayList<>();
        ArrayList<ExpressionNode> values = new ArrayList<>();
        ArrayList<ExpressionNode> mappables = new ArrayList<>();
        int i = 0;
        int limit = ctx.getChildCount();
        while (i < limit) {
            ParseTree child = ctx.getChild(i);
            if (child instanceof Python3Parser.TestContext) {
                keys.add((ExpressionNode) child.accept(this));
                values.add((ExpressionNode) ctx.getChild(i + 2).accept(this));
                i += 3;
            } else if (child instanceof Python3Parser.ExprContext) {
                // we create a dict literal because the expr might refer to
                // another dictionary, and we don't want to share identity
                mappables.add(factory.createDictLiteral(keys, values));
                keys.clear();
                values.clear();
                mappables.add((ExpressionNode) child.accept(this));
                i += 1;
            } else {
                i += 1;
            }
        }
        if (mappables.size() == 0) {
            return factory.createDictLiteral(keys, values);
        } else {
            return factory.createDictionaryConcat(mappables.toArray(new ExpressionNode[0]));
        }
    }

    private PNode visitDictmakerComprehension(Python3Parser.DictmakerContext ctx) {
        if (!ctx.expr().isEmpty()) {
            throw errors.raiseInvalidSyntax(source, deriveSourceSection(ctx), "dict unpacking cannot be used in dict comprehension");
        }
        return factory.callBuiltin(DICT,
                        createComprehensionExpression(ctx, ctx.comp_for(), c -> factory.createTupleLiteral((ExpressionNode) ctx.test(0).accept(this), (ExpressionNode) ctx.test(1).accept(this))));
    }

    private Object parseSpecialLiteral(Python3Parser.AtomContext ctx) {
        String txt = ctx.getText();
        if (txt.equals("...")) {
            return factory.createObjectLiteral(PEllipsis.INSTANCE);
        } else if (txt.equals("None")) {
            return factory.createObjectLiteral(PNone.NONE);
        } else if (txt.equals("True")) {
            return factory.createBooleanLiteral(true);
        } else if (txt.equals("False")) {
            return factory.createBooleanLiteral(false);
        } else {
            throw errors.raiseInvalidSyntax(source, deriveSourceSection(ctx), "Unknown literal %s", txt);
        }
    }

    private static class BytesBuilder {
        List<byte[]> bytes = new ArrayList<>();
        int len = 0;

        void append(byte[] b) {
            len += b.length;
            bytes.add(b);
        }

        byte[] build() {
            byte[] output = new byte[len];
            int offset = 0;
            for (byte[] bs : bytes) {
                System.arraycopy(bs, 0, output, offset, bs.length);
                offset += bs.length;
            }
            return output;
        }
    }

    @SuppressWarnings("unused")
    private PNode parseString(String[] strings) {
        StringBuilder sb = null;
        BytesBuilder bb = null;

        for (String text : strings) {
            boolean isRaw = false;
            boolean isBytes = false;
            boolean isFormat = false;

            int strStartIndex = 1;
            int strEndIndex = text.length() - 1;

            for (int i = 0; i < 3; i++) {
                char chr = Character.toLowerCase(text.charAt(i));

                if (chr == 'r') {
                    isRaw = true;
                } else if (chr == 'u') {
                    // unicode case (default)
                } else if (chr == 'b') {
                    isBytes = true;
                } else if (chr == 'f') {
                    isFormat = true;
                } else if (chr == '\'' || chr == '"') {
                    strStartIndex = i + 1;
                    break;
                }
            }

            if (text.endsWith("'''") || text.endsWith("\"\"\"")) {
                strStartIndex += 2;
                strEndIndex -= 2;
            }

            text = text.substring(strStartIndex, strEndIndex);
            if (isBytes) {
                if (sb != null) {
                    throw errors.raise(SyntaxError, "cannot mix bytes and nonbytes literals");
                }
                if (bb == null) {
                    bb = new BytesBuilder();
                }
                if (isRaw) {
                    bb.append(text.getBytes());
                } else {
                    bb.append(BytesUtils.fromString(errors, text));
                }
            } else {
                if (bb != null) {
                    throw errors.raise(SyntaxError, "cannot mix bytes and nonbytes literals");
                }
                if (sb == null) {
                    sb = new StringBuilder();
                }
                if (isRaw) {
                    sb.append(text);
                } else {
                    sb.append(unescapeJavaString(text));
                }
            }
        }

        if (bb != null) {
            return factory.createBytesLiteral(bb.build());
        } else if (sb != null) {
            return factory.createStringLiteral(sb.toString());
        } else {
            return factory.createStringLiteral("");
        }
    }

    public static String unescapeJavaString(String st) {
        if (st.indexOf("\\") == -1) {
            return st;
        }
        StringBuilder sb = new StringBuilder(st.length());
        for (int i = 0; i < st.length(); i++) {
            char ch = st.charAt(i);
            if (ch == '\\') {
                char nextChar = (i == st.length() - 1) ? '\\' : st.charAt(i + 1);
                // Octal escape?
                if (nextChar >= '0' && nextChar <= '7') {
                    String code = "" + nextChar;
                    i++;
                    if ((i < st.length() - 1) && st.charAt(i + 1) >= '0' && st.charAt(i + 1) <= '7') {
                        code += st.charAt(i + 1);
                        i++;
                        if ((i < st.length() - 1) && st.charAt(i + 1) >= '0' && st.charAt(i + 1) <= '7') {
                            code += st.charAt(i + 1);
                            i++;
                        }
                    }
                    sb.append((char) Integer.parseInt(code, 8));
                    continue;
                }
                switch (nextChar) {
                    case '\\':
                        ch = '\\';
                        break;
                    case 'a':
                        ch = '\u0007';
                        break;
                    case 'b':
                        ch = '\b';
                        break;
                    case 'f':
                        ch = '\f';
                        break;
                    case 'n':
                        ch = '\n';
                        break;
                    case 'r':
                        ch = '\r';
                        break;
                    case 't':
                        ch = '\t';
                        break;
                    case 'v':
                        ch = '\u000b';
                        break;
                    case '\"':
                        ch = '\"';
                        break;
                    case '\'':
                        ch = '\'';
                        break;
                    case '\r':
                        nextChar = (i == st.length() - 2) ? '\\' : st.charAt(i + 2);
                        if (nextChar == '\n') {
                            i++;
                        }
                        i++;
                        continue;
                    case '\n':
                        i++;
                        continue;
                    // Hex Unicode: u????
                    case 'u':
                        if (i >= st.length() - 5) {
                            ch = 'u';
                            break;
                        }
                        int code = Integer.parseInt(
                                        "" + st.charAt(i + 2) + st.charAt(i + 3) + st.charAt(i + 4) + st.charAt(i + 5), 16);
                        sb.append(Character.toChars(code));
                        i += 5;
                        continue;
                    // Hex Unicode: U????????
                    case 'U':
                        if (i >= st.length() - 9) {
                            ch = 'U';
                            break;
                        }
                        code = Integer.parseInt(st.substring(i + 2, i + 10), 16);
                        sb.append(Character.toChars(code));
                        i += 9;
                        continue;
                    // Hex Unicode: x??
                    case 'x':
                        if (i >= st.length() - 3) {
                            ch = 'u';
                            break;
                        }
                        int hexCode = Integer.parseInt("" + st.charAt(i + 2) + st.charAt(i + 3), 16);
                        sb.append(Character.toChars(hexCode));
                        i += 3;
                        continue;
                    default:
                        sb.append(ch);
                        sb.append(nextChar);
                        i++;
                        continue;
                }
                i++;
            }
            sb.append(ch);
        }
        return sb.toString();
    }

    private Object parseNumber(String inputText) {
        String text = inputText.replace("_", "");
        if (text.endsWith("j") || text.endsWith("J") || text.contains(".")) {
            return parseDottedNumber(text);
        } else {
            if (text.startsWith("0x") || text.startsWith("0X")) {
                return parseInteger(text);
            } else if (text.contains("e") || text.contains("E")) {
                return parseDottedNumber(text);
            } else {
                return parseInteger(text);
            }
        }
    }

    private static int maxHexadecimalIntegerLength = Integer.toString(Integer.MAX_VALUE, 16).length() - 1;
    private static int maxDecimalIntegerLength = Integer.toString(Integer.MAX_VALUE, 10).length() - 1;
    private static int maxOctalIntegerLength = Integer.toString(Integer.MAX_VALUE, 8).length() - 1;
    private static int maxBinaryIntegerLength = Integer.toString(Integer.MAX_VALUE, 2).length() - 1;

    private Object parseInteger(String text) {
        String intString = text;
        int radix = 10;
        if (text.length() >= 2 && text.charAt(0) == '0') {
            if (text.charAt(1) == 'x' || text.charAt(1) == 'X') {
                radix = 16;
                intString = text.substring(2);
                if (intString.length() < maxHexadecimalIntegerLength) {
                    return factory.createIntegerLiteral(Integer.parseInt(intString, radix));
                }
            } else if (text.charAt(1) == 'o' || text.charAt(1) == 'O') {
                radix = 8;
                intString = text.substring(2);
                if (intString.length() < maxOctalIntegerLength) {
                    return factory.createIntegerLiteral(Integer.parseInt(intString, radix));
                }
            } else if (text.charAt(1) == 'b' || text.charAt(1) == 'B') {
                radix = 2;
                intString = text.substring(2);
                if (intString.length() < maxBinaryIntegerLength) {
                    return factory.createIntegerLiteral(Integer.parseInt(intString, radix));
                }
            }
        }
        if (radix == 10 && intString.length() < maxDecimalIntegerLength) {
            return factory.createIntegerLiteral(Integer.parseInt(intString, radix));
        }

        // Won't fit into integer literal, parse digit by digit into big integer
        BigInteger integer = BigInteger.ZERO;
        BigInteger bigRadix = BigInteger.valueOf(radix);
        byte[] bytes = intString.getBytes();
        int pow = 0;
        for (int i = bytes.length - 1; i >= 0; i--) {
            BigInteger next = BigInteger.valueOf(Integer.parseInt(new String(new byte[]{bytes[i]}), radix));
            integer = integer.add(next.multiply(bigRadix.pow(pow)));
            pow += 1;
        }
        try {
            return factory.createIntegerLiteral(integer.intValueExact());
        } catch (ArithmeticException e) {
            try {
                return factory.createLongLiteral(integer.longValueExact());
            } catch (ArithmeticException e1) {
                return factory.createPIntLiteral(integer);
            }
        }
    }

    private Object parseDottedNumber(String text) {
        if (text.toLowerCase().endsWith("j")) {
            return parseImaginaryNumber(text.substring(0, text.length() - 1));
        } else {
            return parseFloatNumber(text);
        }
    }

    private Object parseImaginaryNumber(String text) {
        return factory.createComplexLiteral(new PComplex(PythonBuiltinClassType.PComplex, 0.0, Double.parseDouble(text)));
    }

    private Object parseFloatNumber(String text) {
        return factory.createDoubleLiteral(Double.parseDouble(text));
    }

    @Override
    public PNode visitDecorator(Python3Parser.DecoratorContext ctx) {
        ExpressionNode decoratorfunction = (ExpressionNode) environment.findVariable(ctx.dotted_name().NAME(0).getText());
        for (int i = 1; i < ctx.dotted_name().NAME().size(); i++) {
            decoratorfunction = factory.createGetAttribute(decoratorfunction, ctx.dotted_name().NAME(i).getText());
        }
        if (ctx.getChild(2) instanceof TerminalNode && ctx.getChild(2).getText().equals("(")) {
            return visitCallTo(decoratorfunction, ctx.arglist());
        } else {
            return decoratorfunction;
        }
    }

    @Override
    public List<ExpressionNode> visitDecorators(Python3Parser.DecoratorsContext ctx) {
        List<ExpressionNode> r = new ArrayList<>();
        // Decorators should be executed from the inside out, so we reverse the list
        for (int i = ctx.decorator().size() - 1; i >= 0; i--) {
            Python3Parser.DecoratorContext decorator = ctx.decorator(i);
            ExpressionNode node = (ExpressionNode) decorator.accept(this);
            deriveSourceSection(decorator, node);
            r.add(node);
        }
        return r;
    }

    @Override
    public Object visitDecorated(Python3Parser.DecoratedContext ctx) {
        List<ExpressionNode> decorators = visitDecorators(ctx.decorators());
        ExpressionNode definition;
        String definitionName;
        if (ctx.classdef() != null) {
            definition = asDefinition(ctx.classdef().accept(this));
            definitionName = ctx.classdef().NAME().getText();
        } else if (ctx.funcdef() != null) {
            definition = asDefinition(ctx.funcdef().accept(this));
            definitionName = ctx.funcdef().NAME().getText();
        } else if (ctx.async_funcdef() != null) {
            definition = asDefinition(ctx.async_funcdef().accept(this));
            definitionName = ctx.async_funcdef().funcdef().NAME().getText();
        } else {
            throw new RuntimeException("unsupported decorated definition");
        }
        for (ExpressionNode decorator : decorators) {
            definition = PythonCallNode.create(decorator, new ExpressionNode[]{definition}, new ExpressionNode[]{}, null, null);
            definition.assignSourceSection(decorator.getSourceSection());
        }
        return environment.findVariable(definitionName).makeWriteNode(definition);
    }

    private static ExpressionNode asDefinition(Object accept) {
        ExpressionNode definition;
        if (accept instanceof WriteNode) {
            definition = ((WriteNode) accept).getRhs();
        } else if (accept instanceof ExpressionNode) {
            definition = (ExpressionNode) accept;
        } else {
            throw new IllegalArgumentException();
        }
        return definition;
    }

    private ExpressionNode getRhsImport(Python3Parser.Dotted_nameContext ctx, ExpressionNode importNode) {
        ExpressionNode rhsImport = importNode;
        for (int i = 1;; i++) {
            TerminalNode ctxName = ctx.NAME(i);
            if (ctxName == null) {
                break;
            }
            rhsImport = factory.createGetAttribute(rhsImport, ctxName.getText());
        }
        return rhsImport;
    }

    @Override
    public Object visitImport_name(Python3Parser.Import_nameContext ctx) {
        return visitDotted_as_names(ctx.dotted_as_names());
    }

    @Override
    public Object visitDotted_as_names(Python3Parser.Dotted_as_namesContext ctx) {
        return super.visitDotted_as_names(ctx);
    }

    @Override
    public Object visitDotted_as_name(Python3Parser.Dotted_as_nameContext ctx) {
        String dotted_name = ctx.dotted_name().getText();
        ExpressionNode importNode = factory.createImport(dotted_name).asExpression();
        if (ctx.NAME() != null) {
            return environment.findVariable(ctx.NAME().getText()).makeWriteNode(getRhsImport(ctx.dotted_name(), importNode));
        } else {
            return environment.findVariable(ctx.dotted_name().NAME(0).getText()).makeWriteNode(importNode);
        }
    }

    @Override
    public Object visitImport_from(Python3Parser.Import_fromContext ctx) {
        StringBuilder sb = new StringBuilder();
        int level = 0;
        for (int i = 1; i < ctx.getChildCount(); i++) {
            ParseTree child = ctx.getChild(i);
            String text = child.getText();
            if (child instanceof TerminalNode) {
                if (text.equals("import")) {
                    break;
                } else if (text.equals(".")) {
                    level += 1;
                    continue;
                } else if (text.equals("...")) {
                    level += 3;
                    continue;
                }
            }
            sb.append(text);
        }

        ArrayList<String> fromlist = new ArrayList<>();
        ArrayList<WriteNode> asNodes = new ArrayList<>();
        if (ctx.import_as_names() != null) {
            for (Python3Parser.Import_as_nameContext ic : ctx.import_as_names().import_as_name()) {
                String attribute = ic.NAME(0).getText();
                String asName = attribute;
                if (ic.NAME().size() == 2) {
                    asName = ic.NAME(1).getText();
                }
                fromlist.add(attribute);
                asNodes.add((WriteNode) environment.findVariable(asName).makeWriteNode(EmptyNode.create()));
            }
            return factory.createImportFrom(sb.toString(), fromlist.toArray(new String[0]), asNodes.toArray(new WriteNode[0]), level);
        } else {
            if (!environment.atModuleLevel()) {
                throw errors.raiseInvalidSyntax(source, deriveSourceSection(ctx), "import * only allowed at module level");
            }
            return factory.createImportStar(sb.toString(), level);
        }
    }

    /**
     * Chained comparisons. <br>
     * x < y <= z is equivalent to x < y and y <= z, except that y is evaluated only once
     */
    @Override
    public Object visitComparison(Python3Parser.ComparisonContext ctx) {
        List<Python3Parser.ExprContext> expressions = ctx.expr();
        List<Python3Parser.Comp_opContext> comparisons = ctx.comp_op();
        if (comparisons.isEmpty()) {
            return expressions.get(0).accept(this);
        }

        int i = 0;
        ExpressionNode currentComparison = null;
        ExpressionNode lhs = (ExpressionNode) expressions.get(i).accept(this);
        while (i < comparisons.size()) {
            String op = comparisons.get(i).getText();
            i += 1;
            ExpressionNode rhs = (ExpressionNode) expressions.get(i).accept(this);
            ExpressionNode nextComp;
            if (rhs instanceof LiteralNode || rhs instanceof ReadNode || i == expressions.size() - 1) {
                nextComp = factory.createComparisonOperation(op, lhs, rhs);
                lhs = rhs;
            } else {
                ReadNode tempVar = environment.makeTempLocalVariable();
                StatementNode tempAsgn = tempVar.makeWriteNode(rhs);
                nextComp = factory.createComparisonOperation(op, lhs, (ExpressionNode) tempVar).withSideEffect(tempAsgn);
                lhs = (ExpressionNode) tempVar;
            }
            if (currentComparison == null) {
                currentComparison = nextComp;
            } else {
                currentComparison = new AndNode(currentComparison, nextComp);
            }
        }
        return currentComparison;
    }

    @Override
    public Object visitFactor(Python3Parser.FactorContext ctx) {
        if (ctx.factor() != null) {
            return factory.createUnaryOperation(ctx.getChild(0).getText(), (ExpressionNode) ctx.factor().accept(this));
        } else {
            return super.visitFactor(ctx);
        }
    }

    @Override
    public Object visitNot_test(Python3Parser.Not_testContext ctx) {
        if (ctx.not_test() != null) {
            return factory.createUnaryOperation("not", (ExpressionNode) ctx.not_test().accept(this));
        } else {
            return super.visitNot_test(ctx);
        }
    }

    @Override
    public Object visitOr_test(Python3Parser.Or_testContext ctx) {
        ExpressionNode lhs = (ExpressionNode) ctx.and_test(0).accept(this);
        int limit = ctx.getChildCount();
        for (int i = 1; i < limit; i++) {
            if (ctx.and_test(i) == null) {
                break;
            }
            lhs = new OrNode(lhs, (ExpressionNode) ctx.and_test(i).accept(this));
        }
        return lhs;
    }

    @Override
    public Object visitAnd_test(Python3Parser.And_testContext ctx) {
        ExpressionNode lhs = (ExpressionNode) ctx.not_test(0).accept(this);
        int limit = ctx.getChildCount();
        for (int i = 1; i < limit; i++) {
            if (ctx.not_test(i) == null) {
                break;
            }
            lhs = new AndNode(lhs, (ExpressionNode) ctx.not_test(i).accept(this));
        }
        return lhs;
    }

    @Override
    public Object visitPass_stmt(Python3Parser.Pass_stmtContext ctx) {
        return EmptyNode.create();
    }

    @Override
    public Object visitDel_stmt(Python3Parser.Del_stmtContext ctx) {
        List<PNode> exprlist = asList(super.visitDel_stmt(ctx));
        List<StatementNode> blockList = new ArrayList<>();
        for (PNode target : exprlist) {
            delTarget(blockList, target);
        }
        return factory.createBlock(blockList);
    }

    private void delTarget(List<StatementNode> blockList, PNode target) {
        if (target instanceof GetItemNode) {
            GetItemNode getItem = (GetItemNode) target;
            blockList.add(factory.createDeleteItem(getItem.getPrimary(), getItem.getSlice()));
        } else if (target instanceof GetAttributeNode) {
            GetAttributeNode getAttribute = (GetAttributeNode) target;
            blockList.add(factory.createDeleteAttribute(getAttribute.getObject(), getAttribute.getKey()));
        } else if (target instanceof ReadLocalNode) {
            // this will raise an error, if the variable is not bound
            blockList.add(((ExpressionNode) target).asStatement());
            blockList.add(((ReadLocalNode) target).makeDeleteNode());
        } else if (target instanceof ReadGlobalOrBuiltinNode) {
            ReadGlobalOrBuiltinNode readGlobalOrBuiltin = (ReadGlobalOrBuiltinNode) target;
            blockList.add(factory.createDeleteGlobal(readGlobalOrBuiltin.getAttributeId()));
        } else if (target instanceof ReadNameNode) {
            ReadNameNode readName = (ReadNameNode) target;
            blockList.add(factory.createDeleteName(readName.getAttributeId()));
        } else if (target instanceof TupleLiteralNode) {
            for (PNode targetValue : ((TupleLiteralNode) target).getValues()) {
                delTarget(blockList, targetValue);
            }
        } else {
            throw errors.raiseInvalidSyntax(target.getSourceSection().getSource(), target.getSourceSection(), "can't delete '%s'", target.getSourceSection().getCharacters());
        }
    }

    @Override
    public Object visitExpr(Python3Parser.ExprContext ctx) {
        return visitBinaryOperation(ctx);
    }

    @Override
    public Object visitXor_expr(Python3Parser.Xor_exprContext ctx) {
        return visitBinaryOperation(ctx);
    }

    @Override
    public Object visitAnd_expr(Python3Parser.And_exprContext ctx) {
        return visitBinaryOperation(ctx);
    }

    @Override
    public Object visitShift_expr(Python3Parser.Shift_exprContext ctx) {
        return visitBinaryOperation(ctx);
    }

    @Override
    public Object visitArith_expr(Python3Parser.Arith_exprContext ctx) {
        return visitBinaryOperation(ctx);
    }

    @Override
    public Object visitTerm(Python3Parser.TermContext ctx) {
        return visitBinaryOperation(ctx);
    }

    @Override
    public Object visitPower(Python3Parser.PowerContext ctx) {
        return visitBinaryOperation(ctx);
    }

    private PNode visitBinaryOperation(ParserRuleContext ctx) {
        int limit = ctx.getChildCount();
        ExpressionNode lhs = (ExpressionNode) ctx.getChild(0).accept(this);
        for (int i = 1; i < limit; i += 2) {
            String op = ctx.getChild(i).getText();
            ExpressionNode rhs = (ExpressionNode) ctx.getChild(i + 1).accept(this);
            lhs = factory.createBinaryOperation(op, lhs, rhs);
        }
        return lhs;
    }

    @Override
    public Object visitReturn_stmt(Python3Parser.Return_stmtContext ctx) {
        if (environment.isInFunctionScope()) {
            if (ctx.testlist() == null) {
                return factory.createReturn();
            } else {
                return factory.createFrameReturn(factory.createWriteLocal((ExpressionNode) ctx.testlist().accept(this), environment.getReturnSlot()));
            }
        }
        throw errors.raiseInvalidSyntax(source, deriveSourceSection(ctx), "'return' outside function");
    }

    private static boolean lastChildIsComma(ParserRuleContext ctx) {
        if (ctx == null) {
            return false;
        }
        ParseTree child = ctx.getChild(ctx.getChildCount() - 1);
        return (child instanceof TerminalNode) && child.getText().equals(",");
    }

    @Override
    public Object visitTestlist_star_expr(Python3Parser.Testlist_star_exprContext ctx) {
        List<ExpressionNode> testlist = asList(super.visitTestlist_star_expr(ctx));
        if (testlist.size() > 1 || lastChildIsComma(ctx)) {
            return factory.createTupleLiteral(testlist);
        } else {
            return testlist.get(0);
        }
    }

    @Override
    public Object visitTestlist(Python3Parser.TestlistContext ctx) {
        List<ExpressionNode> testlist = asList(super.visitTestlist(ctx));
        if (testlist.size() > 1 || lastChildIsComma(ctx)) {
            return factory.createTupleLiteral(testlist);
        } else {
            return testlist.get(0);
        }
    }

    @Override
    public Object visitBreak_stmt(Python3Parser.Break_stmtContext ctx) {
        loops.addBreak();
        return factory.createBreak();
    }

    @Override
    public Object visitContinue_stmt(Python3Parser.Continue_stmtContext ctx) {
        loops.addContinue();
        return factory.createContinue();
    }

    @Override
    public Object visitWhile_stmt(Python3Parser.While_stmtContext ctx) {
        loops.beginLoop(ctx);
        PNode test = (PNode) ctx.test().accept(this);
        StatementNode body = asBlock(ctx.suite(0).accept(this));
        LoopInfo info = loops.endLoop();
        // the else node is outside of the loop info structure
        StatementNode orelse = ctx.suite().size() == 2 ? asBlock(ctx.suite(1).accept(this)) : factory.createBlock();
        return createWhileNode(test, body, orelse, info);
    }

    private PNode createWhileNode(PNode test, StatementNode body, StatementNode orelse, LoopInfo info) {
        StatementNode wrappedBody = body;

        if (info.hasContinue()) {
            wrappedBody = factory.createContinueTarget(body);
        }

        // assignSourceToBlockNode(wrappedBody, bodyStmt);
        StatementNode whileNode = factory.createWhile(factory.toBooleanCastNode(test), wrappedBody);
        // assignSourceFromNode(node, whileNode);

        if (info.hasBreak()) {
            if (!EmptyNode.isEmpty(orelse)) {
                whileNode = factory.createBreakTarget(whileNode, orelse);
            } else {
                whileNode = factory.createBreakTarget(whileNode);
            }
        } else if (!EmptyNode.isEmpty(orelse)) {
            whileNode = factory.createElse(whileNode, orelse);
        }

        return whileNode;
    }

    @Override
    public Object visitFor_stmt(Python3Parser.For_stmtContext ctx) {
        loops.beginLoop(ctx);
        StatementNode target = assigns.translate(ctx.exprlist());
        ExpressionNode iter = (ExpressionNode) ctx.testlist().accept(this);
        StatementNode body = asBlock(ctx.suite(0).accept(this));
        LoopInfo info = loops.endLoop();
        // the else node is outside of the loop info structure
        StatementNode orelse = ctx.suite().size() == 2 ? asBlock(ctx.suite(1).accept(this)) : factory.createBlock();
        return createForNode(target, iter, body, orelse, info);
    }

    private StatementNode createForNode(StatementNode target, ExpressionNode iter, StatementNode body, StatementNode orelse, LoopInfo info) {
        StatementNode wrappedBody = body;
        if (info.hasContinue()) {
            wrappedBody = factory.createContinueTarget(body);
        }

        // assignSourceToBlockNode(wrappedBody, bodyStmt);
        StatementNode forNode = createForInScope(target, iter, wrappedBody);
        // assignSourceFromNode(node, forNode);

        if (info.hasBreak()) {
            if (!EmptyNode.isEmpty(orelse)) {
                forNode = factory.createBreakTarget(forNode, orelse);
            } else {
                forNode = factory.createBreakTarget(forNode);
            }
        } else if (!EmptyNode.isEmpty(orelse)) {
            forNode = factory.createElse(forNode, orelse);
        }

        return forNode;
    }

    private LoopNode createForInScope(StatementNode target, ExpressionNode iterator, StatementNode body) {
        GetIteratorExpressionNode getIterator = factory.createGetIterator(iterator);
        getIterator.assignSourceSection(iterator.getSourceSection());
        return new ForNode(body, target, getIterator);
    }

    @Override
    public Object visitExpr_stmt(Python3Parser.Expr_stmtContext ctx) {
        if (ctx.normassign().isEmpty() && ctx.annassign() == null && ctx.augassign() == null) {
            return super.visitExpr_stmt(ctx);
        } else {
            return assigns.translate(ctx);
        }
    }

    @Override
    public Object visitWith_stmt(Python3Parser.With_stmtContext ctx) {
        StatementNode body = asBlock(ctx.suite().accept(this));
        List<Python3Parser.With_itemContext> itemList = ctx.with_item();
        for (int i = itemList.size() - 1; i >= 0; i--) {
            Python3Parser.With_itemContext itemContext = itemList.get(i);
            ExpressionNode withNode = (ExpressionNode) itemContext.test().accept(this);
            WriteNode asNameNode = null;
            if (itemContext.expr() != null) {
                asNameNode = (WriteNode) assigns.translate(itemContext.expr());
            }
            body = factory.createWithNode(withNode, asNameNode, body);
        }
        return body;
    }

    @Override
    public Object visitGlobal_stmt(Python3Parser.Global_stmtContext ctx) {
        return EmptyNode.create();
    }

    @Override
    public Object visitNonlocal_stmt(Python3Parser.Nonlocal_stmtContext ctx) {
        return EmptyNode.create();
    }

    @Override
    public Object visitYield_expr(Python3Parser.Yield_exprContext ctx) {
        environment.setToGeneratorScope();
        ExpressionNode right;
        if (ctx.yield_arg() != null) {
            if (ctx.yield_arg().testlist() != null) {
                right = (ExpressionNode) ctx.yield_arg().testlist().accept(this);
            } else {
                assert ctx.yield_arg().test() != null;
                right = (ExpressionNode) ctx.yield_arg().test().accept(this);
                return factory.createYieldFrom(right, environment.getReturnSlot());
            }
        } else {
            right = EmptyNode.create();
        }
        return factory.createYield(right, environment.getReturnSlot());
    }

    @Override
    public Object visitIf_stmt(Python3Parser.If_stmtContext ctx) {
        return makeIfElse(ctx, 0);
    }

    private StatementNode makeIfElse(Python3Parser.If_stmtContext ctx, int i) {
        int suiteCount = ctx.suite().size();
        if (suiteCount <= i) {
            return factory.createBlock();
        }
        CastToBooleanNode test = factory.toBooleanCastNode((PNode) ctx.test(i).accept(this));
        StatementNode ifBody = asBlock(ctx.suite(i).accept(this));
        StatementNode elseBody;
        int testCount = ctx.test().size();
        if (testCount < suiteCount && suiteCount > i + 1 && !(testCount > i + 1)) {
            // we have an else without test
            elseBody = asBlock(ctx.suite(i + 1).accept(this));
        } else {
            elseBody = makeIfElse(ctx, i + 1);
        }
        return factory.createIf(test, ifBody, elseBody);
    }

    @Override
    public Object visitTry_stmt(Python3Parser.Try_stmtContext ctx) {
        StatementNode tryNode = asBlock(ctx.suite(0).accept(this));
        List<ExceptNode> exceptClauses = new ArrayList<>();
        StatementNode elseNode = factory.createBlock();
        StatementNode finallyNode = null;
        int i = 3; // 0 == 'try', 1 == ':', 2 == tryNode
        boolean gotDefaultExcept = false;
        while (i < ctx.getChildCount()) {
            ParseTree child = ctx.getChild(i);
            i += 2; // skip the ':'
            if (child instanceof TerminalNode) {
                if (child.getText().equals("else")) {
                    elseNode = asBlock(ctx.getChild(i).accept(this));
                    i += 1;
                    continue;
                } else if (child.getText().equals("finally")) {
                    finallyNode = asBlock(ctx.getChild(i).accept(this));
                    i += 1;
                    continue;
                }
            }
            if (child instanceof Python3Parser.Except_clauseContext) {
                Python3Parser.Except_clauseContext excctx = (Python3Parser.Except_clauseContext) child;
                ExpressionNode exceptType = null;
                WriteNode exceptName = null;
                if (excctx.test() != null) {
                    if (gotDefaultExcept) {
                        throw errors.raiseInvalidSyntax(source, deriveSourceSection(excctx), "default except: must be last");
                    }
                    exceptType = (ExpressionNode) excctx.test().accept(this);
                    if (excctx.NAME() != null) {
                        exceptName = (WriteNode) environment.findVariable(excctx.NAME().getText()).makeWriteNode(null);
                    }
                } else {
                    gotDefaultExcept = true;
                }
                StatementNode exceptBody = asBlock(ctx.getChild(i).accept(this));
                exceptClauses.add(new ExceptNode(exceptBody, exceptType, exceptName));
                i += 1;
                continue;
            }
            assert false;
        }

        return factory.createTryExceptElseFinallyNode(tryNode, exceptClauses.toArray(new ExceptNode[0]), elseNode, finallyNode);
    }

    @Override
    public Object visitRaise_stmt(Python3Parser.Raise_stmtContext ctx) {
        ExpressionNode type = (ctx.test().size() == 0) ? EmptyNode.create() : (ExpressionNode) ctx.test(0).accept(this);
        ExpressionNode cause = (ctx.test().size() < 2) ? EmptyNode.create() : (ExpressionNode) ctx.test(1).accept(this);
        return RaiseNode.create(type, cause);
    }

    @Override
    public Object visitAssert_stmt(Python3Parser.Assert_stmtContext ctx) {
        CastToBooleanNode test = factory.toBooleanCastNode((PNode) ctx.test(0).accept(this));
        ExpressionNode msg = null;
        if (ctx.test().size() > 1) {
            msg = (ExpressionNode) ctx.test(1).accept(this);
        }
        return factory.createAssert(test, msg);
    }

    @Override
    public Object visitStar_expr(Python3Parser.Star_exprContext ctx) {
        return StarredExpressionNode.create((ExpressionNode) super.visitStar_expr(ctx));
    }

    @Override
    public Object visitFuncdef(Python3Parser.FuncdefContext ctx) {
        String funcName = ctx.NAME().getText();
        String enclosingClassName = environment.isInClassScope() ? environment.getCurrentScopeId() : null;

        List<ExpressionNode> defaultArgs = new ArrayList<>();
        List<KwDefaultExpressionNode> defaultKwArgs = new ArrayList<>();

        environment.pushScope(ctx.scope);
        environment.setDefaultArgumentNodes(defaultArgs);
        environment.setDefaultKwArgumentNodes(defaultKwArgs);

        /**
         * Parameters
         */
        Args args = visitArgs(ctx.parameters().typedargslist(), defaultArgs, defaultKwArgs);
        StatementNode argumentLoads = args.node;
        Signature signature = args.signature;

        /**
         * Function body
         */
        List<PNode> bodyNodes = asList(ctx.suite().accept(this));
        ExpressionNode doc = null;
        if (bodyNodes.size() > 0 && bodyNodes.get(0) instanceof StringLiteralNode) {
            doc = (ExpressionNode) bodyNodes.remove(0);
        } else {
            doc = factory.createStringLiteral("");
        }
        StatementNode body = asBlock(bodyNodes);

        body = factory.createBlock(argumentLoads, body);
        ReturnTargetNode returnTarget = new ReturnTargetNode(body, factory.createReadLocal(environment.getReturnSlot()));
        deriveSourceSection(ctx, returnTarget);

        /**
         * Defaults
         */
        ExpressionNode[] defaults = createDefaultArgumentsNode();
        KwDefaultExpressionNode[] kwDefaults = createDefaultKwArgumentsNode();

        /**
         * Function root
         */
        FrameDescriptor fd = environment.getCurrentFrame();
        FunctionRootNode funcRoot = factory.createFunctionRoot(deriveSourceSection(ctx), funcName, environment.isInGeneratorScope(), fd, returnTarget, environment.getExecutionCellSlots(), signature);

        /**
         * Definition
         */
        ExpressionNode funcDef;
        if (environment.isInGeneratorScope()) {
            GeneratorTranslator gtran = new GeneratorTranslator(funcRoot, false);
            RootCallTarget ct = gtran.translate();
            funcDef = GeneratorFunctionDefinitionNode.create(funcName, enclosingClassName, doc, defaults, kwDefaults, ct, fd,
                            environment.getDefinitionCellSlots(), environment.getExecutionCellSlots(), gtran.getFlagSlots(), gtran.getIndexSlots());
        } else {
            RootCallTarget ct = Truffle.getRuntime().createCallTarget(funcRoot);
            funcDef = new FunctionDefinitionNode(funcName, enclosingClassName, doc, defaults, kwDefaults, ct, environment.getDefinitionCellSlots(), environment.getExecutionCellSlots());
        }
        environment.popScope();

        ReadNode funcVar = environment.findVariable(funcName);
        return funcVar.makeWriteNode(funcDef);
    }

    private final class Args {
        final StatementNode node;
        final Signature signature;

        Args(Signature signature) {
            this.node = BlockNode.create();
            this.signature = signature;
        }

        Args(StatementNode node, Signature signature) {
            this.node = node;
            this.signature = signature;
        }
    }

    public Args visitArgs(ParserRuleContext ctx, List<ExpressionNode> defaultArgs, List<KwDefaultExpressionNode> defaultKwArgs) {
        if (ctx == null) {
            return new Args(new Signature(false, -1, false, new String[0], new String[0]));
        }
        assert ctx instanceof Python3Parser.TypedargslistContext || ctx instanceof Python3Parser.VarargslistContext;
        boolean starArgsMarker = false;
        List<String> parameterIds = new ArrayList<>();
        List<StatementNode> argumentReads = new ArrayList<>();
        List<String> keywordNames = new ArrayList<>();
        int childCount = ctx.getChildCount();
        int varargsIdx = -1;
        boolean kwargsSeen = false;
        for (int i = 0; i < childCount; i++) {
            ParserRuleContext child = ctx.getChild(ParserRuleContext.class, i);
            StatementNode argumentReadNode = null;
            ParserRuleContext test = null;
            String argname = null;
            if (child instanceof Python3Parser.DefparameterContext) {
                argname = ((Python3Parser.DefparameterContext) child).tfpdef().NAME().getText();
                test = ((Python3Parser.DefparameterContext) child).test();
            } else if (child instanceof Python3Parser.VdefparameterContext) {
                argname = ((Python3Parser.VdefparameterContext) child).vfpdef().NAME().getText();
                test = ((Python3Parser.VdefparameterContext) child).test();
            } else if (child instanceof Python3Parser.SplatparameterContext) {
                Python3Parser.SplatparameterContext splat = (Python3Parser.SplatparameterContext) child;
                argname = splat.tfpdef() == null ? null : splat.tfpdef().NAME().getText();
                if (argname != null) {
                    varargsIdx = i;
                    argumentReadNode = environment.getWriteVarArgsToLocal(argname, i);
                } else {
                    starArgsMarker = true;
                }
            } else if (child instanceof Python3Parser.VsplatparameterContext) {
                Python3Parser.VsplatparameterContext splat = (Python3Parser.VsplatparameterContext) child;
                argname = splat.vfpdef() == null ? null : splat.vfpdef().NAME().getText();
                if (argname != null) {
                    varargsIdx = i;
                    argumentReadNode = environment.getWriteVarArgsToLocal(argname, i);
                } else {
                    starArgsMarker = true;
                }
            } else if (child instanceof Python3Parser.KwargsparameterContext) {
                kwargsSeen = true;
                Python3Parser.KwargsparameterContext splat = (Python3Parser.KwargsparameterContext) child;
                argname = splat.tfpdef().NAME().getText();
                argumentReadNode = environment.getWriteKwArgsToLocal(argname, keywordNames.toArray(new String[0]));
            } else if (child instanceof Python3Parser.VkwargsparameterContext) {
                kwargsSeen = true;
                Python3Parser.VkwargsparameterContext splat = (Python3Parser.VkwargsparameterContext) child;
                argname = splat.vfpdef().NAME().getText();
                argumentReadNode = environment.getWriteKwArgsToLocal(argname, keywordNames.toArray(new String[0]));
            }
            if (argumentReadNode == null && argname != null) {
                ExpressionNode defaultValueNode = null;
                if (test != null) {
                    /**
                     * translate default arguments in FunctionDef's declaring scope.
                     */
                    ScopeInfo definitionScope = environment.popScope();
                    defaultValueNode = (ExpressionNode) test.accept(this);
                    environment.pushScope(definitionScope);
                }
                if (varargsIdx == -1 && !starArgsMarker) {
                    // positional argument, possibly with a default
                    if (defaultValueNode != null) {
                        defaultArgs.add(defaultValueNode);
                    }
                    argumentReadNode = environment.getWriteArgumentToLocal(argname, parameterIds.size());
                    parameterIds.add(argname);
                } else {
                    // keyword-only argument
                    argumentReadNode = environment.getWriteArgumentToLocal(argname, parameterIds.size() + keywordNames.size());
                    keywordNames.add(argname);
                    if (defaultValueNode != null) {
                        defaultKwArgs.add(KwDefaultExpressionNode.create(argname, defaultValueNode));
                    }
                }
            }
            if (argumentReadNode != null) {
                argumentReads.add(argumentReadNode);
            }
        }
        return new Args(factory.createBlock(argumentReads), new Signature(kwargsSeen, varargsIdx, starArgsMarker, parameterIds, keywordNames));
    }

    private ExpressionNode[] createDefaultArgumentsNode() {
        if (environment.hasDefaultArguments()) {
            List<ExpressionNode> defaultParameters = environment.getDefaultArgumentNodes();
            return defaultParameters.toArray(new ExpressionNode[defaultParameters.size()]);
        } else {
            return null;
        }
    }

    private KwDefaultExpressionNode[] createDefaultKwArgumentsNode() {
        if (environment.hasDefaultKwArguments()) {
            List<KwDefaultExpressionNode> defaultParameters = environment.getDefaultKwArgumentNodes();
            return defaultParameters.toArray(new KwDefaultExpressionNode[defaultParameters.size()]);
        } else {
            return null;
        }
    }

    private PNode createLambda(ParserRuleContext ctx, VarargslistContext varargslist, ParserRuleContext bodyCtx, ScopeInfo scope) {
        /**
         * translate default arguments in FunctionDef's declaring scope.
         */
        List<ExpressionNode> defaultArgs = new ArrayList<>();
        List<KwDefaultExpressionNode> defaultKwArgs = new ArrayList<>();

        String funcname = "anonymous";
        environment.pushScope(scope);
        environment.setDefaultArgumentNodes(defaultArgs);
        environment.setDefaultKwArgumentNodes(defaultKwArgs);

        /**
         * Parameters
         */
        Args args = visitArgs(varargslist, defaultArgs, defaultKwArgs);
        StatementNode argumentLoads = args.node;
        Signature signature = args.signature;

        /**
         * Lambda body
         */
        StatementNode frameReturn = factory.createFrameReturn(factory.createWriteLocal((ExpressionNode) bodyCtx.accept(this), environment.getReturnSlot()));
        StatementNode body = factory.createBlock(argumentLoads, frameReturn);
        ReturnTargetNode returnTargetNode = new ReturnTargetNode(body, factory.createReadLocal(environment.getReturnSlot()));
        deriveSourceSection(ctx, returnTargetNode);

        /**
         * Defaults
         */
        ExpressionNode[] defaults = createDefaultArgumentsNode();
        KwDefaultExpressionNode[] kwDefaults = createDefaultKwArgumentsNode();

        /**
         * Lambda function root
         */
        FrameDescriptor fd = environment.getCurrentFrame();
        FunctionRootNode funcRoot = factory.createFunctionRoot(deriveSourceSection(ctx), funcname, environment.isInGeneratorScope(), fd, returnTargetNode, environment.getExecutionCellSlots(),
                        signature);

        /**
         * Definition
         */
        PNode funcDef;
        if (environment.isInGeneratorScope()) {
            GeneratorTranslator gtran = new GeneratorTranslator(funcRoot, false);
            RootCallTarget ct = gtran.translate();
            funcDef = GeneratorFunctionDefinitionNode.create(funcname, null, null, defaults, kwDefaults, ct, fd,
                            environment.getDefinitionCellSlots(), environment.getExecutionCellSlots(),
                            gtran.getFlagSlots(), gtran.getIndexSlots());
        } else {
            RootCallTarget ct = Truffle.getRuntime().createCallTarget(funcRoot);
            funcDef = new FunctionDefinitionNode(funcname, null, null, defaults, kwDefaults, ct, environment.getDefinitionCellSlots(), environment.getExecutionCellSlots());
        }
        environment.popScope();

        return funcDef;
    }

    @Override
    public Object visitLambdef(Python3Parser.LambdefContext ctx) {
        VarargslistContext varargslist = ctx.varargslist();
        Lambdef_bodyContext bodyCtx = ctx.lambdef_body();
        ScopeInfo scope = ctx.scope;
        return createLambda(ctx, varargslist, bodyCtx, scope);
    }

    @Override
    public Object visitLambdef_nocond(Python3Parser.Lambdef_nocondContext ctx) {
        VarargslistContext varargslist = ctx.varargslist();
        Lambdef_nocond_bodyContext bodyCtx = ctx.lambdef_nocond_body();
        return createLambda(ctx, varargslist, bodyCtx, ctx.scope);
    }

    @Override
    public Object visitTest(Python3Parser.TestContext ctx) {
        if (ctx.test() != null) {
            return factory.createTernaryIf(factory.toBooleanCastNode((PNode) ctx.or_test(1).accept(this)),
                            (ExpressionNode) ctx.or_test(0).accept(this),
                            (ExpressionNode) ctx.test().accept(this));
        } else {
            return super.visitTest(ctx);
        }
    }

    private static String calculateQualname(Python3Parser.ClassdefContext ctx) {
        String className = ctx.NAME().getText();
        ParserRuleContext parent = ctx.getParent();
        Deque<String> stack = new ArrayDeque<>();
        stack.push(className);

        while (parent != null) {
            if (parent instanceof Python3Parser.FuncdefContext) {
                Python3Parser.FuncdefContext funcdefContext = (Python3Parser.FuncdefContext) parent;
                stack.push("<locals>");
                stack.push(funcdefContext.NAME().getText());
            } else if (parent instanceof Python3Parser.ClassdefContext) {
                Python3Parser.ClassdefContext classdefContext = (Python3Parser.ClassdefContext) parent;
                stack.push(classdefContext.NAME().getText());
            }

            parent = parent.getParent();
        }

        return String.join(".", stack);
    }

    @Override
    public Object visitClassdef(Python3Parser.ClassdefContext ctx) {
        String className = ctx.NAME().getText();
        String qualName = calculateQualname(ctx);

        List<ExpressionNode> argumentNodes = new ArrayList<>();
        List<ExpressionNode> keywords = new ArrayList<>();
        ExpressionNode[] splatArguments = new ExpressionNode[2];
        visitCallArglist(ctx.arglist(), argumentNodes, keywords, splatArguments);

        environment.pushScope(ctx.scope);
        environment.registerSpecialClassCellVar();
        ExpressionNode body = asClassBody(ctx.suite().accept(this), qualName);
        ClassBodyRootNode classBodyRoot = factory.createClassBodyRoot(deriveSourceSection(ctx), className, environment.getCurrentFrame(), body, environment.getExecutionCellSlots());
        RootCallTarget ct = Truffle.getRuntime().createCallTarget(classBodyRoot);
        FunctionDefinitionNode funcDef = new FunctionDefinitionNode(className, null, null, null, null, ct, environment.getDefinitionCellSlots(), environment.getExecutionCellSlots());
        environment.popScope();

        argumentNodes.add(0, factory.createStringLiteral(className));
        argumentNodes.add(0, funcDef);
        ExpressionNode owner = factory.createGetAttribute(factory.createBuiltinsLiteral(), __BUILD_CLASS__);
        ExpressionNode classDef = PythonCallNode.create(owner, argumentNodes.toArray(new ExpressionNode[0]), keywords.toArray(new ExpressionNode[0]), splatArguments[0], splatArguments[1]);
        deriveSourceSection(ctx, classDef);

        ReadNode read = environment.findVariable(className);

        ReadNode tempLocal = environment.makeTempLocalVariable();
        ExpressionNode newClass = ((ExpressionNode) tempLocal).withSideEffect(
                        factory.createBlock(tempLocal.makeWriteNode(classDef),
                                        factory.createWriteCellVar((ExpressionNode) tempLocal, classBodyRoot, __CLASS__)));
        return read.makeWriteNode(newClass);
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> asList(Object accept) {
        if (accept == null) {
            return new ArrayList<>();
        } else if (accept instanceof List) {
            return (List<T>) accept;
        } else if (accept instanceof PNode) {
            List<T> r = new ArrayList<>();
            r.add((T) accept);
            return r;
        } else {
            throw new RuntimeException("unexpected parse result");
        }
    }

    private StatementNode asBlock(Object accept) {
        if (accept == null) {
            return factory.createBlock();
        } else if (accept instanceof StatementNode) {
            return (StatementNode) accept;
        } else if (accept instanceof ExpressionNode) {
            return ((ExpressionNode) accept).asStatement();
        } else if (accept instanceof List) {
            @SuppressWarnings("unchecked")
            List<PNode> inputList = (List<PNode>) accept;
            if (inputList.size() == 0) {
                return factory.createBlock();
            } else if (inputList.size() == 1) {
                return asBlock(inputList.get(0));
            } else {
                StatementNode[] statements = new StatementNode[inputList.size()];
                for (int i = 0; i < statements.length; i++) {
                    statements[i] = asBlock(inputList.get(i));
                }
                return factory.createBlock(statements);
            }
        } else {
            throw new IllegalArgumentException("unexpected class: " + accept.getClass());
        }
    }

    private ExpressionNode asExpression(Object accept) {
        if (accept instanceof List) {
            @SuppressWarnings("unchecked")
            List<PNode> list = (List<PNode>) accept;
            if (list.size() == 0) {
                return EmptyNode.create();
            } else if (list.size() == 1) {
                return asExpression(list.get(0));
            } else {
                ExpressionNode asExpression = asExpression(list.remove(list.size() - 1));
                return asExpression.withSideEffect(asBlock(list));
            }
        } else if (accept instanceof ExpressionNode.ExpressionStatementNode) {
            return ((ExpressionNode.ExpressionStatementNode) accept).getExpression();
        } else if (accept instanceof ExpressionNode) {
            return (ExpressionNode) accept;
        } else if (accept instanceof StatementNode) {
            return EmptyNode.create().withSideEffect((StatementNode) accept);
        } else if (accept == null) {
            return EmptyNode.create();
        } else {
            throw new IllegalArgumentException("unexpected class: " + accept.getClass());
        }
    }

    protected ExpressionNode asClassBody(Object accept, String qualName) {
        List<PNode> body = asList(accept);
        if (body.size() > 0 && body.get(0) instanceof StringLiteralNode) {
            environment.createLocal(__DOC__);
            body.set(0, environment.findVariable(__DOC__).makeWriteNode((ExpressionNode) body.get(0)));
        }
        body.add(0, new ClassDefinitionPrologueNode(qualName));
        return new ReturnTargetNode(asBlock(body), factory.createNullLiteral());
    }

    @Override
    public Object visitTestlist_comp(Python3Parser.Testlist_compContext ctx) {
        if (ctx.comp_for() == null) {
            return super.visitTestlist_comp(ctx);
        } else {
            return createComprehensionExpression(ctx, ctx.comp_for());
        }
    }

    @Override
    public Object visitArgument(ArgumentContext ctx) {
        if (ctx.comp_for() == null) {
            return super.visitArgument(ctx);
        } else {
            return createComprehensionExpression(ctx, ctx.comp_for());
        }
    }

    private StatementNode createGeneratorExpression(Python3Parser.Comp_forContext comp_for, StatementNode yield) {
        return createGeneratorExpression(comp_for, yield, true);
    }

    private StatementNode createGeneratorExpression(Python3Parser.Comp_forContext comp_for, StatementNode yield, boolean iteratorInParentScope) {
        // TODO: async
        ScopeInfo old = null;
        if (iteratorInParentScope) {
            old = environment.popScope();
        }
        ExpressionNode iterator = (ExpressionNode) comp_for.or_test().accept(this);
        if (iteratorInParentScope) {
            environment.pushScope(old);
        }

        StatementNode targets = assigns.translate(comp_for.exprlist());
        StatementNode myBody = yield;
        ExpressionNode condition = null;
        Python3Parser.Comp_iterContext comp_iter = comp_for.comp_iter();
        while (comp_iter != null && comp_iter.comp_if() != null) {
            ExpressionNode nextIf = (ExpressionNode) comp_iter.comp_if().test_nocond().accept(this);
            if (condition == null) {
                condition = nextIf;
            } else {
                condition = factory.createBinaryOperation("and", condition, nextIf);
            }
            comp_iter = comp_iter.comp_if().comp_iter();
        }
        if (comp_iter != null && comp_iter.comp_for() != null) {
            myBody = createGeneratorExpression(comp_iter.comp_for(), yield, false);
        }
        if (condition != null) {
            myBody = factory.createIf(factory.createYesNode(condition), myBody, factory.createBlock());
        }
        LoopNode loop = createForInScope(targets, iterator, myBody);
        deriveSourceSection(comp_for, loop);
        return loop;
    }
}
