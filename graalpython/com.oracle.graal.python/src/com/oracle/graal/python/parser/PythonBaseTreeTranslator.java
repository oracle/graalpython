/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.RuleNode;
import org.antlr.v4.runtime.tree.TerminalNode;

import com.oracle.graal.python.builtins.objects.PEllipsis;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.Arity;
import com.oracle.graal.python.nodes.EmptyNode;
import com.oracle.graal.python.nodes.ModuleRootNode;
import com.oracle.graal.python.nodes.NodeFactory;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.argument.ReadDefaultArgumentNode;
import com.oracle.graal.python.nodes.argument.ReadIndexedArgumentNode;
import com.oracle.graal.python.nodes.argument.ReadKeywordNode;
import com.oracle.graal.python.nodes.argument.ReadVarArgsNode;
import com.oracle.graal.python.nodes.argument.ReadVarKeywordsNode;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode;
import com.oracle.graal.python.nodes.call.PythonCallNode;
import com.oracle.graal.python.nodes.classes.ClassDefinitionEpilogNode;
import com.oracle.graal.python.nodes.control.BlockNode;
import com.oracle.graal.python.nodes.control.ForNode;
import com.oracle.graal.python.nodes.control.GetIteratorNode;
import com.oracle.graal.python.nodes.control.LoopNode;
import com.oracle.graal.python.nodes.control.ReturnTargetNode;
import com.oracle.graal.python.nodes.control.StopIterationTargetNode;
import com.oracle.graal.python.nodes.expression.AndNode;
import com.oracle.graal.python.nodes.expression.CastToBooleanNode;
import com.oracle.graal.python.nodes.expression.OrNode;
import com.oracle.graal.python.nodes.frame.ReadGlobalOrBuiltinNode;
import com.oracle.graal.python.nodes.frame.ReadLocalNode;
import com.oracle.graal.python.nodes.frame.ReadNode;
import com.oracle.graal.python.nodes.frame.WriteIdentifierNode;
import com.oracle.graal.python.nodes.frame.WriteNode;
import com.oracle.graal.python.nodes.function.ClassBodyRootNode;
import com.oracle.graal.python.nodes.function.FunctionDefinitionNode;
import com.oracle.graal.python.nodes.function.FunctionRootNode;
import com.oracle.graal.python.nodes.function.GeneratorExpressionNode;
import com.oracle.graal.python.nodes.function.GeneratorFunctionDefinitionNode;
import com.oracle.graal.python.nodes.literal.LiteralNode;
import com.oracle.graal.python.nodes.literal.StarredExpressionNode;
import com.oracle.graal.python.nodes.literal.StringLiteralNode;
import com.oracle.graal.python.nodes.literal.TupleLiteralNode;
import com.oracle.graal.python.nodes.statement.DefaultParametersNode;
import com.oracle.graal.python.nodes.statement.ExceptNode;
import com.oracle.graal.python.nodes.statement.RaiseNode;
import com.oracle.graal.python.nodes.subscript.GetItemNode;
import com.oracle.graal.python.parser.antlr.Python3BaseVisitor;
import com.oracle.graal.python.parser.antlr.Python3Parser;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

public abstract class PythonBaseTreeTranslator<T> extends Python3BaseVisitor<Object> {

    protected final PythonCore core;
    protected final NodeFactory factory;
    protected final TranslationEnvironment environment;
    protected final LoopsBookKeeper loops;
    protected final AssignmentTranslator assigns;
    protected final Source source;
    protected final String name;

    public PythonBaseTreeTranslator(PythonCore core, String name, TranslationEnvironment environment, Source source) {
        this.name = name;
        this.core = core;
        this.source = source;
        this.factory = core.getLanguage().getNodeFactory();
        this.environment = environment.reset();
        this.loops = new LoopsBookKeeper();
        this.assigns = new AssignmentTranslator(core, environment, this);
    }

    public abstract T getTranslationResult();

    @SuppressWarnings("unchecked")
    @Override
    protected Object aggregateResult(Object aggregate, Object nextResult) {
        if (aggregate == null) {
            return nextResult;
        } else if (nextResult == null) {
            return aggregate;
        } else {
            ArrayList<Object> r = new ArrayList<>();
            if (aggregate instanceof List) {
                r.addAll((Collection<Object>) aggregate);
            } else {
                r.add(aggregate);
            }
            if (nextResult instanceof List) {
                r.addAll((Collection<? extends Object>) nextResult);
            } else {
                r.add(nextResult);
            }
            return r;
        }
    }

    @Override
    protected Object defaultResult() {
        return null;
    }

    @Override
    public Object visitChildren(RuleNode node) {
        Object r = super.visitChildren(node);
        deriveSourceSection(node, r);
        return r;
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
        environment.beginScope(ctx, ScopeInfo.ScopeKind.Module);
        PNode file = asBlockOrPNode(super.visitFile_input(ctx));
        deriveSourceSection(ctx, file);
        FrameDescriptor fd = environment.getCurrentFrame();
        environment.endScope(ctx);
        ModuleRootNode newNode = factory.createModuleRoot(name, file, fd);
        return newNode;
    }

    @Override
    public Object visitEval_input(Python3Parser.Eval_inputContext ctx) {
        environment.beginScope(ctx, ScopeInfo.ScopeKind.Module);
        PNode node = (PNode) super.visitEval_input(ctx);
        deriveSourceSection(ctx, node);
        FrameDescriptor fd = environment.getCurrentFrame();
        FrameSlot[] freeVarSlots = environment.getFreeVarSlots();
        environment.endScope(ctx);
        return factory.createModuleRoot(name, node, fd, freeVarSlots);
    }

    @Override
    public Object visitSingle_input(Python3Parser.Single_inputContext ctx) {
        environment.beginScope(ctx, ScopeInfo.ScopeKind.Function);
        PNode body = asBlockOrPNode(super.visitSingle_input(ctx));
        deriveSourceSection(ctx, body);
        FrameDescriptor fd = environment.getCurrentFrame();
        environment.endScope(ctx);
        return factory.createModuleRoot("<expression>", body, fd);
    }

    @Override
    public Object visitAtom_expr(Python3Parser.Atom_exprContext ctx) {
        // TODO: deal with AWAIT
        if (ctx.trailer().isEmpty()) {
            return super.visitAtom_expr(ctx);
        } else {
            PNode expr = (PNode) visitAtom(ctx.atom());
            for (Python3Parser.TrailerContext t : ctx.trailer()) {
                expr = visitTrailerFrom(expr, t);
            }
            return expr;
        }
    }

    private PNode visitTrailerFrom(PNode owner, Python3Parser.TrailerContext t) {
        if (t.NAME() != null) {
            return factory.createGetAttribute(owner, t.NAME().getText());
        } else if (t.subscriptlist() != null) {
            return visitSubscriptFrom(owner, t);
        } else {
            assert t.getChild(0).getText().equals("(");
            return visitCallTo(owner, t.arglist());
        }
    }

    private void visitCallArglist(Python3Parser.ArglistContext arglist, List<PNode> argumentNodes, List<PNode> keywords, PNode[] splatArguments) {
        PNode starargs = EmptyNode.create();
        PNode kwargs = EmptyNode.create();
        if (arglist != null) {
            for (Python3Parser.ArgumentContext argctx : arglist.argument()) {
                PNode defaultarg = getDefaultarg(argctx);
                if (defaultarg != null) {
                    keywords.add(defaultarg);
                } else {
                    PNode arg;
                    if (argctx.comp_for() != null) {
                        arg = createComprehensionExpression(argctx);
                    } else {
                        arg = (PNode) argctx.accept(this);
                    }
                    if (isKwarg(argctx)) {
                        if (!EmptyNode.isEmpty(kwargs)) {
                            kwargs = factory.createDictionaryConcat(kwargs, arg);
                        } else {
                            kwargs = arg;
                        }
                    } else if (isStararg(argctx)) {
                        if (!EmptyNode.isEmpty(kwargs)) {
                            throw core.raise(SyntaxError, "iterable argument unpacking follows keyword argument unpacking");
                        }
                        if (!EmptyNode.isEmpty(starargs)) {
                            starargs = factory.createBinaryOperation("+", starargs, arg);
                        } else {
                            starargs = arg;
                        }
                    } else {
                        if (!keywords.isEmpty()) {
                            throw core.raise(SyntaxError, "positional argument follows keyword argument");
                        }
                        if (!EmptyNode.isEmpty(kwargs)) {
                            throw core.raise(SyntaxError, "positional argument follows keyword argument unpacking");
                        }
                        argumentNodes.add(arg);
                    }
                }
            }
        }
        splatArguments[0] = starargs;
        splatArguments[1] = kwargs;
    }

    private PNode visitCallTo(PNode owner, Python3Parser.ArglistContext arglist) {
        List<PNode> argumentNodes = new ArrayList<>();
        List<PNode> keywords = new ArrayList<>();
        PNode[] splatArguments = new PNode[2];
        visitCallArglist(arglist, argumentNodes, keywords, splatArguments);
        PythonCallNode callNode = PythonCallNode.create(owner, argumentNodes.toArray(new PNode[0]), keywords.toArray(new PNode[0]), splatArguments[0], splatArguments[1]);
        if (argumentNodes.size() == 0 && owner instanceof ReadGlobalOrBuiltinNode && ((ReadGlobalOrBuiltinNode) owner).getAttributeId().equals(SUPER)) {
            // super call without arguments
            environment.registerSpecialClassCellVar();
        }
        return callNode;
    }

    private PNode visitSubscriptFrom(PNode owner, Python3Parser.TrailerContext t) {
        List<PNode> subscriptlist = asList(visitSubscriptlist(t.subscriptlist()));
        PNode rhs;
        if (subscriptlist.size() > 1) {
            rhs = factory.createTupleLiteral(subscriptlist);
        } else {
            rhs = subscriptlist.get(0);
        }
        return factory.createSubscriptLoad(owner, rhs);
    }

    private PNode getDefaultarg(Python3Parser.ArgumentContext ctx) {
        if (ctx.test().size() == 2) {
            // see Python3 grammar definition. The lhs `test' node must really be a NAME
            // In CPython, ast.c ensures this
            String argName = ctx.test(0).accept(new ExtractNameVisitor());
            if (argName == null) {
                throw core.raise(SyntaxError, "Keyword can't be an expression");
            }
            return factory.createKeywordLiteral(asBlockOrPNode(ctx.test(1).accept(this)), argName);
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
        PNode start;
        int stopidx;
        if (ctx.getChild(0) instanceof TerminalNode) {
            start = EmptyNode.create();
            stopidx = 1;
        } else {
            start = asBlockOrPNode(ctx.getChild(0).accept(this));
            stopidx = 2;
        }
        PNode stop;
        if (stopidx >= ctx.getChildCount() || ctx.getChild(stopidx) instanceof TerminalNode || ctx.getChild(stopidx) == ctx.sliceop()) {
            stop = EmptyNode.create();
        } else {
            stop = asBlockOrPNode(ctx.getChild(stopidx).accept(this));
        }
        PNode step;
        if (ctx.sliceop() == null || ctx.sliceop().test() == null) {
            step = EmptyNode.create();
        } else {
            step = asBlockOrPNode(ctx.sliceop().test().accept(this));
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
            return parseSpecalLiteral(ctx.getText());
        } else if (ctx.dictorsetmaker() != null) {
            return super.visitAtom(ctx);
        } else if (ctx.getChild(0).getText().equals("{")) { // empty dict
            return factory.createDictLiteral();
        } else if (ctx.getChild(0).getText().equals("[")) { // list
            if (ctx.testlist_comp() != null && ctx.testlist_comp().comp_for() != null) {
                return factory.callBuiltin(LIST, (PNode) super.visitAtom(ctx));
            } else {
                return factory.createListLiteral(asList(super.visitAtom(ctx)));
            }
        } else if (ctx.getChild(0).getText().equals("(")) { // tuple
            List<PNode> ll = asList(super.visitAtom(ctx));
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
            List<PNode> nodes = asList(super.visitSetmaker(ctx));
            Set<PNode> setNodes = new HashSet<>(nodes);
            return factory.createSetLiteral(setNodes);
        } else {
            return factory.callBuiltin(SET, createComprehensionExpression(ctx));
        }
    }

    private PNode createComprehensionExpression(ParserRuleContext ctx) {
        return createComprehensionExpression(ctx, c -> asBlockOrPNode(c.getChild(0).accept(this)));
    }

    private PNode createComprehensionExpression(ParserRuleContext ctx, Function<ParserRuleContext, PNode> getBlock) {
        try {
            environment.beginScope(ctx, ScopeInfo.ScopeKind.Generator);
            PNode block = getBlock.apply(ctx);
            PNode yield = factory.createYield(block, environment.getReturnSlot());
            yield.assignSourceSection(block.getSourceSection());
            PNode body = createGeneratorExpression(ctx.getChild(Python3Parser.Comp_forContext.class, 0), yield);
            SourceSection srcSection = body.getSourceSection();
            body = new ReturnTargetNode(body, factory.createReadLocal(environment.getReturnSlot()));
            body.assignSourceSection(srcSection);
            int lineNum = ctx.getStart().getLine();
            GeneratorExpressionNode genExprDef = createGeneratorExpressionDefinition(body, lineNum);
            genExprDef.setEnclosingFrameDescriptor(environment.getEnclosingFrame());
            genExprDef.assignSourceSection(srcSection);
            return genExprDef;
        } finally {
            environment.endScope(ctx);
        }
    }

    private GeneratorExpressionNode createGeneratorExpressionDefinition(PNode body, int lineNum) {
        FrameDescriptor fd = environment.getCurrentFrame();
        String generatorName = "generator_exp:" + lineNum;
        FunctionRootNode funcRoot = factory.createFunctionRoot(body.getSourceSection(), generatorName, true, fd, body, environment.getExecutionCellSlots());
        GeneratorTranslator gtran = new GeneratorTranslator(funcRoot);
        return new GeneratorExpressionNode(generatorName, gtran.translate(), fd, environment.getDefinitionCellSlots(), environment.getExecutionCellSlots(),
                        gtran.getNumOfActiveFlags(),
                        gtran.getNumOfGeneratorBlockNode(),
                        gtran.getNumOfGeneratorForNode());
    }

    @Override
    public Object visitDictmaker(Python3Parser.DictmakerContext ctx) {
        if (ctx.comp_for() != null) {
            return visitDictmakerComprehension(ctx);
        } else {
            return visitNormalDictmaker(ctx);
        }
    }

    private PNode visitNormalDictmaker(Python3Parser.DictmakerContext ctx) {
        ArrayList<PNode> keys = new ArrayList<>();
        ArrayList<PNode> values = new ArrayList<>();
        ArrayList<PNode> mappables = new ArrayList<>();
        int i = 0;
        int limit = ctx.getChildCount();
        while (i < limit) {
            ParseTree child = ctx.getChild(i);
            if (child instanceof Python3Parser.TestContext) {
                keys.add(asBlockOrPNode(child.accept(this)));
                values.add(asBlockOrPNode(ctx.getChild(i + 2).accept(this)));
                i += 3;
            } else if (child instanceof Python3Parser.ExprContext) {
                // we create a dict literal because the expr might refer to
                // another dictionary, and we don't want to share identity
                mappables.add(factory.createDictLiteral(keys, values));
                keys.clear();
                values.clear();
                mappables.add(asBlockOrPNode(child.accept(this)));
                i += 1;
            } else {
                i += 1;
            }
        }
        if (mappables.size() == 0) {
            return factory.createDictLiteral(keys, values);
        } else {
            return factory.createDictionaryConcat(mappables.toArray(new PNode[0]));
        }
    }

    private PNode visitDictmakerComprehension(Python3Parser.DictmakerContext ctx) {
        if (!ctx.expr().isEmpty()) {
            throw core.raise(SyntaxError, "dict unpacking cannot be used in dict comprehension");
        }
        return factory.callBuiltin(DICT, createComprehensionExpression(ctx, c -> factory.createTupleLiteral(Arrays.asList(
                        asBlockOrPNode(ctx.test(0).accept(this)), asBlockOrPNode(ctx.test(1).accept(this))))));
    }

    private Object parseSpecalLiteral(String text) {
        if (text.equals("...")) {
            return factory.createObjectLiteral(PEllipsis.INSTANCE);
        } else if (text.equals("None")) {
            return factory.createObjectLiteral(PNone.NONE);
        } else if (text.equals("True")) {
            return factory.createBooleanLiteral(true);
        } else if (text.equals("False")) {
            return factory.createBooleanLiteral(false);
        } else {
            throw core.raise(SyntaxError, "Unknown literal %s", text);
        }
    }

    @SuppressWarnings("unused")
    private PNode parseString(String[] strings) {
        StringBuilder sb = new StringBuilder();
        Boolean stringIsBytes = null;
        Boolean stringIsRaw = null;

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
            if (isRaw || isBytes) {
                // TODO: bytes need (some) of the escaping too!, but not all
                sb.append(text);
            } else {
                sb.append(unescapeJavaString(text));
            }

            if (stringIsBytes == null) {
                stringIsBytes = isBytes;
            } else if (stringIsBytes != isBytes) {
                throw core.raise(SyntaxError, "cannot mix bytes and nonbytes literals");
            }
            if (stringIsRaw == null) {
                stringIsRaw = isRaw;
            } else if (stringIsRaw != isRaw) {
                throw core.raise(SyntaxError, "cannot mix raw and non-raw literals");
            }
        }

        if (stringIsBytes != null && stringIsBytes) {
            return factory.createBytesLiteral(sb.toString(), stringIsRaw != null ? stringIsRaw : false);
        }
        return factory.createStringLiteral(sb.toString());
    }

    private static String unescapeJavaString(String st) {
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
                }
                i++;
            }
            sb.append(ch);
        }
        return sb.toString();
    }

    private Object parseNumber(String text) {
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

    static int maxHexadecimalIntegerLength = Integer.toString(Integer.MAX_VALUE, 16).length() - 1;
    static int maxDecimalIntegerLength = Integer.toString(Integer.MAX_VALUE, 10).length() - 1;
    static int maxOctalIntegerLength = Integer.toString(Integer.MAX_VALUE, 8).length() - 1;
    static int maxBinaryIntegerLength = Integer.toString(Integer.MAX_VALUE, 2).length() - 1;

    private Object parseInteger(String text) {
        String intString = text;
        int radix = 10;
        if (text.toLowerCase().startsWith("0x")) {
            radix = 16;
            intString = text.substring(2);
            if (intString.length() < maxHexadecimalIntegerLength) {
                return factory.createIntegerLiteral(Integer.parseInt(intString, radix));
            }
        } else if (text.toLowerCase().startsWith("0o")) {
            radix = 8;
            intString = text.substring(2);
            if (intString.length() < maxOctalIntegerLength) {
                return factory.createIntegerLiteral(Integer.parseInt(intString, radix));
            }
        } else if (text.toLowerCase().startsWith("0b")) {
            radix = 2;
            intString = text.substring(2);
            if (intString.length() < maxBinaryIntegerLength) {
                return factory.createIntegerLiteral(Integer.parseInt(intString, radix));
            }
        } else if (intString.length() < maxDecimalIntegerLength) {
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
        return factory.createComplexLiteral(PythonObjectFactory.get().createComplex(0.0, Double.parseDouble(text)));
    }

    private Object parseFloatNumber(String text) {
        return factory.createDoubleLiteral(Double.parseDouble(text));
    }

    @Override
    public PNode visitDecorator(Python3Parser.DecoratorContext ctx) {
        PNode decoratorfunction = (PNode) environment.findVariable(ctx.dotted_name().NAME(0).getText());
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
    public List<PNode> visitDecorators(Python3Parser.DecoratorsContext ctx) {
        List<PNode> r = new ArrayList<>();
        // Decorators should be executed from the inside out, so we reverse the list
        for (int i = ctx.decorator().size() - 1; i >= 0; i--) {
            Python3Parser.DecoratorContext decorator = ctx.decorator(i);
            PNode node = (PNode) decorator.accept(this);
            deriveSourceSection(decorator, node);
            r.add(node);
        }
        return r;
    }

    @Override
    public Object visitDecorated(Python3Parser.DecoratedContext ctx) {
        List<PNode> decorators = visitDecorators(ctx.decorators());
        PNode definition;
        String definitionName;
        if (ctx.classdef() != null) {
            definition = (PNode) ctx.classdef().accept(this);
            definitionName = ctx.classdef().NAME().getText();
        } else if (ctx.funcdef() != null) {
            definition = (PNode) ctx.funcdef().accept(this);
            definitionName = ctx.funcdef().NAME().getText();
        } else if (ctx.async_funcdef() != null) {
            definition = (PNode) ctx.async_funcdef().accept(this);
            definitionName = ctx.async_funcdef().funcdef().NAME().getText();
        } else {
            throw new RuntimeException("unsupported decorated definition");
        }
        if (definition instanceof WriteNode) {
            definition = ((WriteNode) definition).getRhs();
        }
        for (PNode decorator : decorators) {
            definition = PythonCallNode.create(decorator, new PNode[]{definition}, new PNode[]{}, EmptyNode.create(), EmptyNode.create());
            definition.assignSourceSection(decorator.getSourceSection());
        }
        return environment.findVariable(definitionName).makeWriteNode(definition);
    }

    private PNode getRhsImport(Python3Parser.Dotted_nameContext ctx, PNode importNode) {
        PNode rhsImport = importNode;
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
        PNode importNode = factory.createImport(dotted_name);
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
                throw core.raise(SyntaxError, "import * only allowed at module level");
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
        PNode currentComparison = null;
        PNode lhs = (PNode) expressions.get(i).accept(this);
        while (i < comparisons.size()) {
            String op = comparisons.get(i).getText();
            i += 1;
            PNode rhs = (PNode) expressions.get(i).accept(this);
            PNode nextComp;
            if (rhs instanceof LiteralNode || rhs instanceof ReadNode || i == expressions.size() - 1) {
                nextComp = factory.createComparisonOperation(op, lhs, rhs);
                lhs = rhs;
            } else {
                ReadNode tempVar = environment.makeTempLocalVariable();
                PNode tempAsgn = tempVar.makeWriteNode(rhs);
                nextComp = factory.createBlock(tempAsgn, factory.createComparisonOperation(op, lhs, (PNode) tempVar));
                lhs = (PNode) tempVar;
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
            return factory.createUnaryOperation(ctx.getChild(0).getText(), (PNode) ctx.factor().accept(this));
        } else {
            return super.visitFactor(ctx);
        }
    }

    @Override
    public Object visitNot_test(Python3Parser.Not_testContext ctx) {
        if (ctx.not_test() != null) {
            return factory.createUnaryOperation("not", (PNode) ctx.not_test().accept(this));
        } else {
            return super.visitNot_test(ctx);
        }
    }

    @Override
    public Object visitOr_test(Python3Parser.Or_testContext ctx) {
        PNode lhs = (PNode) ctx.and_test(0).accept(this);
        int limit = ctx.getChildCount();
        for (int i = 1; i < limit; i++) {
            if (ctx.and_test(i) == null) {
                break;
            }
            lhs = new OrNode(lhs, (PNode) ctx.and_test(i).accept(this));
        }
        return lhs;
    }

    @Override
    public Object visitAnd_test(Python3Parser.And_testContext ctx) {
        PNode lhs = (PNode) ctx.not_test(0).accept(this);
        int limit = ctx.getChildCount();
        for (int i = 1; i < limit; i++) {
            if (ctx.not_test(i) == null) {
                break;
            }
            lhs = new AndNode(lhs, (PNode) ctx.not_test(i).accept(this));
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
        List<PNode> blockList = new ArrayList<>();
        for (PNode target : exprlist) {
            delTarget(blockList, target);
        }
        return factory.createBlock(blockList);
    }

    private void delTarget(List<PNode> blockList, PNode target) {
        if (target instanceof GetItemNode) {
            GetItemNode getItem = (GetItemNode) target;
            blockList.add(factory.createDeleteItem(getItem.getPrimary(), getItem.getSlice()));
        } else if (target instanceof GetAttributeNode) {
            GetAttributeNode getAttribute = (GetAttributeNode) target;
            blockList.add(factory.createDeleteAttribute(getAttribute.getObject(), getAttribute.getKey()));
        } else if (target instanceof ReadLocalNode) {
            blockList.add(target); // this will raise an error, if the variable is not bound
            ReadLocalNode readLocal = (ReadLocalNode) target;
            blockList.add(readLocal.makeDeleteNode());
        } else if (target instanceof ReadGlobalOrBuiltinNode) {
            ReadGlobalOrBuiltinNode readGlobalOrBuiltin = (ReadGlobalOrBuiltinNode) target;
            blockList.add(factory.createDeleteGlobal(readGlobalOrBuiltin.getAttributeId()));
        } else if (target instanceof TupleLiteralNode) {
            for (PNode targetValue : ((TupleLiteralNode) target).getValues()) {
                delTarget(blockList, targetValue);
            }
        } else {
            throw core.raise(SyntaxError, "can't delete '%s'", target.getSourceSection().getCharacters());
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
        PNode lhs = (PNode) ctx.getChild(0).accept(this);
        for (int i = 1; i < limit; i += 2) {
            String op = ctx.getChild(i).getText();
            PNode rhs = (PNode) ctx.getChild(i + 1).accept(this);
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
                return factory.createFrameReturn(factory.createWriteLocal((PNode) ctx.testlist().accept(this), environment.getReturnSlot()));
            }
        }
        throw core.raise(SyntaxError, "'return' outside function");
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
        List<PNode> testlist = asList(super.visitTestlist_star_expr(ctx));
        if (testlist.size() > 1 || lastChildIsComma(ctx)) {
            return factory.createTupleLiteral(testlist);
        } else {
            return testlist.get(0);
        }
    }

    @Override
    public Object visitTestlist(Python3Parser.TestlistContext ctx) {
        List<PNode> testlist = asList(super.visitTestlist(ctx));
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
        PNode body = asBlockOrPNode(ctx.suite(0).accept(this));
        LoopInfo info = loops.endLoop();
        // the else node is outside of the loop info structure
        PNode orelse = ctx.suite().size() == 2 ? asBlockOrPNode(ctx.suite(1).accept(this)) : EmptyNode.create();
        return createWhileNode(test, body, orelse, info);
    }

    private PNode createWhileNode(PNode test, PNode body, PNode orelse, LoopInfo info) {
        PNode wrappedBody = body;

        if (info.hasContinue()) {
            wrappedBody = factory.createContinueTarget(body);
        }

        // assignSourceToBlockNode(wrappedBody, bodyStmt);
        PNode whileNode = factory.createWhile(factory.toBooleanCastNode(test), wrappedBody);
        // assignSourceFromNode(node, whileNode);

        if (!EmptyNode.isEmpty(orelse)) {
            whileNode = factory.createElse(whileNode, orelse);
        }

        if (info.hasBreak()) {
            return factory.createBreakTarget(whileNode);
        } else {
            return whileNode;
        }
    }

    @Override
    public Object visitFor_stmt(Python3Parser.For_stmtContext ctx) {
        loops.beginLoop(ctx);
        PNode target = assigns.translate(ctx.exprlist());
        PNode iter = asBlockOrPNode(ctx.testlist().accept(this));
        PNode body = asBlockOrPNode(ctx.suite(0).accept(this));
        LoopInfo info = loops.endLoop();
        // the else node is outside of the loop info structure
        PNode orelse = ctx.suite().size() == 2 ? asBlockOrPNode(ctx.suite(1).accept(this)) : EmptyNode.create();
        return createForNode(target, iter, body, orelse, info);
    }

    private PNode createForNode(PNode target, PNode iter, PNode body, PNode orelse, LoopInfo info) {
        PNode wrappedBody = body;
        if (info.hasContinue()) {
            wrappedBody = factory.createContinueTarget(body);
        }

        // assignSourceToBlockNode(wrappedBody, bodyStmt);
        PNode forNode = createForInScope(target, iter, wrappedBody);
        // assignSourceFromNode(node, forNode);

        if (!EmptyNode.isEmpty(orelse)) {
            forNode = factory.createElse(forNode, orelse);
        }

        if (info.hasBreak()) {
            return factory.createBreakTarget(forNode);
        } else {
            return forNode;
        }
    }

    private LoopNode createForInScope(PNode target, PNode iterator, PNode body) {
        GetIteratorNode getIterator = factory.createGetIterator(iterator);
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
        PNode body = asBlockOrPNode(ctx.suite().accept(this));
        List<Python3Parser.With_itemContext> itemList = ctx.with_item();
        for (int i = itemList.size() - 1; i >= 0; i--) {
            Python3Parser.With_itemContext itemContext = itemList.get(i);
            PNode withNode = (PNode) itemContext.test().accept(this);
            PNode asNameNode = null;
            if (itemContext.expr() != null) {
                asNameNode = assigns.translate(itemContext.expr());
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
        PNode right;
        if (ctx.yield_arg() != null) {
            right = (PNode) ctx.yield_arg().accept(this);
        } else {
            right = EmptyNode.create();
        }
        return factory.createYield(right, environment.getReturnSlot());
    }

    @Override
    public Object visitIf_stmt(Python3Parser.If_stmtContext ctx) {
        return makeIfElse(ctx, 0);
    }

    private PNode makeIfElse(Python3Parser.If_stmtContext ctx, int i) {
        int suiteCount = ctx.suite().size();
        if (suiteCount <= i) {
            return EmptyNode.create();
        }
        CastToBooleanNode test = factory.toBooleanCastNode(asBlockOrPNode(ctx.test(i).accept(this)));
        PNode ifBody = asBlockOrPNode(ctx.suite(i).accept(this));
        PNode elseBody;
        int testCount = ctx.test().size();
        if (testCount < suiteCount && suiteCount > i + 1 && !(testCount > i + 1)) {
            // we have an else without test
            elseBody = asBlockOrPNode(ctx.suite(i + 1).accept(this));
        } else {
            elseBody = makeIfElse(ctx, i + 1);
        }
        return factory.createIf(test, ifBody, elseBody);
    }

    @Override
    public Object visitTry_stmt(Python3Parser.Try_stmtContext ctx) {
        PNode tryNode = asBlockOrPNode(ctx.suite(0).accept(this));
        List<PNode> exceptClauses = new ArrayList<>();
        PNode elseNode = EmptyNode.create();
        PNode finallyNode = EmptyNode.create();
        int i = 3; // 0 == 'try', 1 == ':', 2 == tryNode
        while (i < ctx.getChildCount()) {
            ParseTree child = ctx.getChild(i);
            i += 2; // skip the ':'
            if (child instanceof TerminalNode) {
                if (child.getText().equals("else")) {
                    elseNode = asBlockOrPNode(ctx.getChild(i).accept(this));
                    i += 1;
                    continue;
                } else if (child.getText().equals("finally")) {
                    finallyNode = asBlockOrPNode(ctx.getChild(i).accept(this));
                    i += 1;
                    continue;
                }
            }
            boolean gotDefaultExcept = false;
            if (child instanceof Python3Parser.Except_clauseContext) {
                Python3Parser.Except_clauseContext excctx = (Python3Parser.Except_clauseContext) child;
                PNode exceptType = null;
                PNode exceptName = null;
                if (excctx.test() != null) {
                    if (gotDefaultExcept) {
                        throw core.raise(SyntaxError, "default except: must be last");
                    }
                    exceptType = (PNode) excctx.test().accept(this);
                    if (excctx.NAME() != null) {
                        exceptName = environment.findVariable(excctx.NAME().getText()).makeWriteNode(null);
                    }
                } else {
                    gotDefaultExcept = true;
                }
                PNode exceptBody = asBlockOrPNode(ctx.getChild(i).accept(this));
                exceptClauses.add(new ExceptNode(exceptBody, exceptType, exceptName));
                i += 1;
                continue;
            }
            assert false;
        }

        /**
         * Specialize except StopIteration to StopIterationTargetNode.
         */
        if (exceptClauses.size() == 1 && EmptyNode.isEmpty(elseNode) && EmptyNode.isEmpty(finallyNode) && ctx.except_clause(0).test() != null) {
            if (ctx.except_clause(0).test().getText().equals("StopIteration")) {
                PNode exceptBody = (PNode) ctx.except_clause(0).accept(this);
                return new StopIterationTargetNode(tryNode, exceptBody);
            }
        }

        return factory.createTryExceptElseFinallyNode(tryNode, exceptClauses.toArray(new ExceptNode[0]), elseNode, finallyNode);
    }

    @Override
    public Object visitRaise_stmt(Python3Parser.Raise_stmtContext ctx) {
        PNode type = (ctx.test().size() == 0) ? EmptyNode.create() : (PNode) ctx.test(0).accept(this);
        PNode cause = (ctx.test().size() < 2) ? EmptyNode.create() : (PNode) ctx.test(1).accept(this);
        return RaiseNode.create(type, cause);
    }

    @Override
    public Object visitAssert_stmt(Python3Parser.Assert_stmtContext ctx) {
        CastToBooleanNode test = factory.toBooleanCastNode((PNode) ctx.test(0).accept(this));
        PNode msg = null;
        if (ctx.test().size() > 1) {
            msg = (PNode) ctx.test(1).accept(this);
        }
        return factory.createAssert(test, msg);
    }

    @Override
    public Object visitStar_expr(Python3Parser.Star_exprContext ctx) {
        return StarredExpressionNode.create((PNode) super.visitStar_expr(ctx));
    }

    @Override
    public Object visitStmt(Python3Parser.StmtContext ctx) {
        Object node = super.visitStmt(ctx);
        if (node instanceof PNode) {
            ((PNode) node).markAsStatement();
        }
        return node;
    }

    @Override
    public Object visitFuncdef(Python3Parser.FuncdefContext ctx) {
        String funcName = ctx.NAME().getText();
        String enclosingClassName = environment.isInClassScope() ? environment.getCurrentScopeId() : null;

        /**
         * translate default arguments in FunctionDef's declaring scope.
         */
        List<PNode> defaultArgs = new ArrayList<>();
        Python3Parser.TypedargslistContext argslist = ctx.parameters().typedargslist();
        if (argslist != null) {
            for (Python3Parser.DefparameterContext defc : argslist.defparameter()) {
                if (defc.test() != null) {
                    defaultArgs.add((PNode) defc.test().accept(this));
                }
            }
        }

        environment.beginScope(ctx, ScopeInfo.ScopeKind.Function);
        environment.setDefaultArgumentNodes(defaultArgs);

        /**
         * Parameters
         */
        PNode argumentLoads = visitArgs(ctx.parameters().typedargslist());
        Arity arity = createArity(funcName, argumentLoads);

        /**
         * Function body
         */
        List<PNode> bodyNodes = asList(ctx.suite().accept(this));
        PNode doc = null;
        if (bodyNodes.size() > 0 && bodyNodes.get(0) instanceof StringLiteralNode) {
            doc = bodyNodes.remove(0);
        }
        PNode body = asBlockOrPNode(bodyNodes);

        body = factory.createBlock(argumentLoads, body);
        body = new ReturnTargetNode(body, factory.createReadLocal(environment.getReturnSlot()));
        deriveSourceSection(ctx, body);

        /**
         * Defaults
         */
        PNode defaults = createDefaultArgumentsNode();

        /**
         * Function root
         */
        FrameDescriptor fd = environment.getCurrentFrame();
        String fullName = enclosingClassName == null ? funcName : enclosingClassName + '.' + funcName;
        FunctionRootNode funcRoot = factory.createFunctionRoot(deriveSourceSection(ctx), fullName, environment.isInGeneratorScope(), fd, body, environment.getExecutionCellSlots());
        RootCallTarget ct = Truffle.getRuntime().createCallTarget(funcRoot);

        /**
         * Definition
         */
        PNode funcDef;
        if (environment.isInGeneratorScope()) {
            GeneratorTranslator gtran = new GeneratorTranslator(funcRoot);
            funcDef = GeneratorFunctionDefinitionNode.create(funcName, enclosingClassName, core, arity, defaults, gtran.translate(), fd,
                            environment.getDefinitionCellSlots(), environment.getExecutionCellSlots(),
                            gtran.getNumOfActiveFlags(), gtran.getNumOfGeneratorBlockNode(), gtran.getNumOfGeneratorForNode());
        } else {
            funcDef = new FunctionDefinitionNode(funcName, enclosingClassName, core, arity, defaults, ct, fd, environment.getDefinitionCellSlots(), environment.getExecutionCellSlots());
        }
        environment.endScope(ctx);

        ReadNode funcVar = environment.findVariable(funcName);
        if (doc == null) {
            return funcVar.makeWriteNode(funcDef);
        } else {
            return factory.createBlock(
                            funcVar.makeWriteNode(funcDef),
                            factory.createSetAttribute((PNode) funcVar, __DOC__, doc),
                            (PNode) funcVar);
        }
    }

    public PNode visitArgs(ParserRuleContext ctx) {
        if (ctx == null) {
            return EmptyNode.create();
        }
        assert ctx instanceof Python3Parser.TypedargslistContext || ctx instanceof Python3Parser.VarargslistContext;
        List<PNode> argumentReads = new ArrayList<>();
        List<ReadDefaultArgumentNode> defaultReads = new ArrayList<>();
        List<String> keywordNames = new ArrayList<>();
        int childCount = ctx.getChildCount();
        boolean varargsSeen = false;
        for (int i = 0; i < childCount; i++) {
            ParserRuleContext child = ctx.getChild(ParserRuleContext.class, i);
            PNode argumentReadNode = null;
            ParserRuleContext test = null;
            String argname = null;
            if (child instanceof Python3Parser.DefparameterContext) {
                argname = ((Python3Parser.DefparameterContext) child).tfpdef().NAME().getText();
                test = ((Python3Parser.DefparameterContext) child).test();
            } else if (child instanceof Python3Parser.VdefparameterContext) {
                argname = ((Python3Parser.VdefparameterContext) child).vfpdef().NAME().getText();
                test = ((Python3Parser.VdefparameterContext) child).test();
            } else if (child instanceof Python3Parser.SplatparameterContext) {
                varargsSeen = true;
                Python3Parser.SplatparameterContext splat = (Python3Parser.SplatparameterContext) child;
                argname = splat.tfpdef() == null ? null : splat.tfpdef().NAME().getText();
                if (argname != null) {
                    argumentReadNode = environment.getWriteVarArgsToLocal(argname);
                }
            } else if (child instanceof Python3Parser.VsplatparameterContext) {
                varargsSeen = true;
                Python3Parser.VsplatparameterContext splat = (Python3Parser.VsplatparameterContext) child;
                argname = splat.vfpdef() == null ? null : splat.vfpdef().NAME().getText();
                if (argname != null) {
                    argumentReadNode = environment.getWriteVarArgsToLocal(argname);
                }
            } else if (child instanceof Python3Parser.KwargsparameterContext) {
                Python3Parser.KwargsparameterContext splat = (Python3Parser.KwargsparameterContext) child;
                argname = splat.tfpdef().NAME().getText();
                argumentReadNode = environment.getWriteKwArgsToLocal(argname, keywordNames.toArray(new String[0]));
            } else if (child instanceof Python3Parser.VkwargsparameterContext) {
                Python3Parser.VkwargsparameterContext splat = (Python3Parser.VkwargsparameterContext) child;
                argname = splat.vfpdef().NAME().getText();
                argumentReadNode = environment.getWriteKwArgsToLocal(argname, keywordNames.toArray(new String[0]));
            }
            if (argumentReadNode == null && argname != null) {
                if (test != null) {
                    ReadDefaultArgumentNode readDefaultArgumentNode = new ReadDefaultArgumentNode();
                    defaultReads.add(readDefaultArgumentNode);
                    if (!varargsSeen) {
                        argumentReadNode = environment.getWriteKeywordArgumentToLocal(argname, readDefaultArgumentNode);
                    } else {
                        argumentReadNode = environment.getWriteRequiredKeywordArgumentToLocal(argname, readDefaultArgumentNode);
                    }
                    keywordNames.add(argname);
                } else if (varargsSeen) {
                    argumentReadNode = environment.getWriteRequiredKeywordArgumentToLocal(argname);
                    keywordNames.add(argname);
                } else {
                    argumentReadNode = environment.getWriteArgumentToLocal(argname);
                }
            }
            if (argumentReadNode != null) {
                argumentReads.add(argumentReadNode);
            }
        }
        environment.setDefaultArgumentReads(defaultReads.toArray(new ReadDefaultArgumentNode[0]));
        return factory.createBlock(argumentReads);
    }

    private PNode createDefaultArgumentsNode() {
        if (environment.hasDefaultArguments()) {
            List<PNode> defaultParameters = environment.getDefaultArgumentNodes();
            ReadDefaultArgumentNode[] defaultReads = environment.getDefaultArgumentReads();
            return new DefaultParametersNode(defaultParameters.toArray(new PNode[defaultParameters.size()]),
                            defaultReads);
        } else {
            return EmptyNode.create();
        }
    }

    @Override
    public Object visitLambdef(Python3Parser.LambdefContext ctx) {
        /**
         * translate default arguments in FunctionDef's declaring scope.
         */
        List<PNode> defaultArgs = new ArrayList<>();
        if (ctx.varargslist() != null) {
            for (Python3Parser.VdefparameterContext defc : ctx.varargslist().vdefparameter()) {
                if (defc.test() != null) {
                    defaultArgs.add((PNode) defc.test().accept(this));
                }
            }
        }

        String funcname = "anonymous";
        environment.beginScope(ctx, ScopeInfo.ScopeKind.Function);
        environment.setDefaultArgumentNodes(defaultArgs);

        /**
         * Parameters
         */
        PNode argumentLoads = visitArgs(ctx.varargslist());
        Arity arity = createArity(funcname, argumentLoads);

        /**
         * Lambda body
         */
        PNode body = (PNode) ctx.lambdef_body().accept(this);
        body = factory.createBlock(argumentLoads, body);
        if (environment.isInGeneratorScope()) {
            // If we're in a generator scope, we need a return target, that will
            // be transformed later to catch yield exceptions and return the yielded value
            body = new ReturnTargetNode(body, factory.createReadLocal(environment.getReturnSlot()));
            deriveSourceSection(ctx, body);
        }

        /**
         * Defaults
         */
        PNode defaults = createDefaultArgumentsNode();

        /**
         * Lambda function root
         */
        FrameDescriptor fd = environment.getCurrentFrame();
        FunctionRootNode funcRoot = factory.createFunctionRoot(deriveSourceSection(ctx), funcname, environment.isInGeneratorScope(), fd, body, environment.getExecutionCellSlots());
        RootCallTarget ct = Truffle.getRuntime().createCallTarget(funcRoot);

        /**
         * Definition
         */
        PNode funcDef;
        if (environment.isInGeneratorScope()) {
            GeneratorTranslator gtran = new GeneratorTranslator(funcRoot);
            funcDef = GeneratorFunctionDefinitionNode.create(funcname, null, core, arity, defaults, gtran.translate(), fd,
                            environment.getDefinitionCellSlots(), environment.getExecutionCellSlots(),
                            gtran.getNumOfActiveFlags(), gtran.getNumOfGeneratorBlockNode(), gtran.getNumOfGeneratorForNode());
        } else {
            funcDef = new FunctionDefinitionNode(funcname, null, core, arity, defaults, ct, fd, environment.getDefinitionCellSlots(), environment.getExecutionCellSlots());
        }
        environment.endScope(ctx);

        return funcDef;
    }

    private static Arity createArity(String functionName, PNode argBlock) {
        boolean takesFixedNumOfArgs = true;

        PNode[] statements;
        if (argBlock instanceof BlockNode) {
            statements = ((BlockNode) argBlock).getStatements();
        } else if (argBlock instanceof EmptyNode) {
            statements = new PNode[0];
        } else {
            statements = new PNode[]{argBlock};
        }
        int maxNumOfArgs = 0;
        int minNumOfArgs = 0;
        List<String> parameterIds = new ArrayList<>();
        List<String> keywordNames = new ArrayList<>();
        for (PNode writeLocal : statements) {
            WriteIdentifierNode writeNode = (WriteIdentifierNode) writeLocal;
            PNode rhs = writeNode.getRhs();
            if (rhs instanceof ReadVarArgsNode) {
                maxNumOfArgs = -1;
            } else if (rhs instanceof ReadVarKeywordsNode) {
                maxNumOfArgs = -1;
            } else if (rhs instanceof ReadKeywordNode) {
                if (((ReadKeywordNode) rhs).canBePositional()) {
                    // this default can be passed positionally
                    maxNumOfArgs++;
                }
                keywordNames.add((String) writeNode.getIdentifier());
                takesFixedNumOfArgs = false;
            } else if (rhs instanceof ReadIndexedArgumentNode) {
                minNumOfArgs++;
                maxNumOfArgs++;
                parameterIds.add((String) writeNode.getIdentifier());
            } else {
                assert false;
            }
        }

        takesFixedNumOfArgs = takesFixedNumOfArgs && maxNumOfArgs == minNumOfArgs;
        boolean takesVarArgs = maxNumOfArgs == -1;
        boolean takesKeywordArg = true;

        return new Arity(functionName, minNumOfArgs, maxNumOfArgs, takesKeywordArg, takesVarArgs, parameterIds, keywordNames);
    }

    @Override
    public Object visitTest(Python3Parser.TestContext ctx) {
        if (ctx.test() != null) {
            return factory.createIf(factory.toBooleanCastNode((PNode) ctx.or_test(1).accept(this)),
                            (PNode) ctx.or_test(0).accept(this),
                            (PNode) ctx.test().accept(this));
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
            }

            parent = parent.getParent();
        }

        return String.join(".", stack);
    }

    @Override
    public Object visitClassdef(Python3Parser.ClassdefContext ctx) {
        String className = ctx.NAME().getText();
        String qualName = calculateQualname(ctx);

        List<PNode> argumentNodes = new ArrayList<>();
        List<PNode> keywords = new ArrayList<>();
        PNode[] splatArguments = new PNode[2];
        visitCallArglist(ctx.arglist(), argumentNodes, keywords, splatArguments);

        environment.beginScope(ctx, ScopeInfo.ScopeKind.Class);

        PNode body = asClassBody(ctx.suite().accept(this), qualName);
        ClassBodyRootNode classBodyRoot = factory.createClassBodyRoot(deriveSourceSection(ctx), className, environment.getCurrentFrame(), body,
                        environment.getExecutionCellSlots());
        RootCallTarget ct = Truffle.getRuntime().createCallTarget(classBodyRoot);
        FunctionDefinitionNode funcDef = new FunctionDefinitionNode(className, null, core, Arity.createOneArgument(className),
                        EmptyNode.create(), ct, environment.getCurrentFrame(), environment.getDefinitionCellSlots(), environment.getExecutionCellSlots());
        environment.endScope(ctx);

        argumentNodes.add(0, factory.createStringLiteral(className));
        argumentNodes.add(0, funcDef);
        PNode owner = factory.createGetAttribute(factory.createBuiltinsLiteral(), __BUILD_CLASS__);
        PNode classDef = PythonCallNode.create(owner, argumentNodes.toArray(new PNode[0]), keywords.toArray(new PNode[0]), splatArguments[0], splatArguments[1]);
        deriveSourceSection(ctx, classDef);
        ReadNode read = environment.findVariable(className);
        return factory.createBlock(read.makeWriteNode(classDef), factory.createWriteCellVar((PNode) read, classBodyRoot, __CLASS__));
    }

    @SuppressWarnings("unchecked")
    private static List<PNode> asList(Object accept) {
        if (accept == null) {
            return new ArrayList<>();
        }
        if (accept instanceof List) {
            return (List<PNode>) accept;
        } else if (accept instanceof PNode) {
            List<PNode> r = new ArrayList<>();
            r.add((PNode) accept);
            return r;
        } else {
            throw new RuntimeException("unexpected parse result");
        }
    }

    protected PNode asBlockOrPNode(Object accept) {
        List<PNode> asList = asList(accept);
        if (asList.isEmpty()) {
            return EmptyNode.create();
        } else if (asList.size() == 1) {
            return asList.get(0);
        } else {
            PNode block = factory.createBlock(asList);
            SourceSection sourceSection = asList.get(0).getSourceSection();
            SourceSection sourceSection2 = asList.get(asList.size() - 1).getSourceSection();
            if (sourceSection != null && sourceSection2 != null) {
                block.assignSourceSection(createSourceSection(sourceSection.getCharIndex(), sourceSection2.getCharEndIndex() - sourceSection.getCharIndex()));
            } else if (sourceSection != null) {
                block.assignSourceSection(sourceSection);
            } else {
                block.assignSourceSection(sourceSection2);
            }
            return block;
        }
    }

    protected PNode asClassBody(Object accept, String qualName) {
        List<PNode> body = asList(accept);
        if (body.size() > 0 && body.get(0) instanceof StringLiteralNode) {
            environment.createLocal(__DOC__);
            body.set(0, environment.findVariable(__DOC__).makeWriteNode(body.get(0)));
        }
        body.add(new ClassDefinitionEpilogNode(qualName));
        return asBlockOrPNode(body);
    }

    @Override
    public Object visitTestlist_comp(Python3Parser.Testlist_compContext ctx) {
        if (ctx.comp_for() == null) {
            return super.visitTestlist_comp(ctx);
        } else {
            return createComprehensionExpression(ctx);
        }
    }

    private PNode createGeneratorExpression(Python3Parser.Comp_forContext comp_for, PNode yield) {
        // TODO: async
        PNode targets = assigns.translate(comp_for.exprlist());
        PNode myBody = yield;
        PNode iterator = asBlockOrPNode(comp_for.or_test().accept(this));
        PNode condition = null;
        Python3Parser.Comp_iterContext comp_iter = comp_for.comp_iter();
        while (comp_iter != null && comp_iter.comp_if() != null) {
            PNode nextIf = asBlockOrPNode(comp_iter.comp_if().test_nocond().accept(this));
            if (condition == null) {
                condition = nextIf;
            } else {
                condition = factory.createBinaryOperation("and", condition, nextIf);
            }
            comp_iter = comp_iter.comp_if().comp_iter();
        }
        if (comp_iter != null && comp_iter.comp_for() != null) {
            myBody = createGeneratorExpression(comp_iter.comp_for(), yield);
        }
        if (condition != null) {
            myBody = factory.createIf(factory.createYesNode(condition), myBody, EmptyNode.create());
        }
        LoopNode loop = createForInScope(targets, iterator, myBody);
        deriveSourceSection(comp_for, loop);
        return loop;
    }
}
