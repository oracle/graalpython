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

import static com.oracle.graal.python.nodes.frame.FrameSlotIDs.TEMP_LOCAL_PREFIX;

import com.oracle.graal.python.PythonLanguage;
import java.math.BigInteger;
import java.util.ArrayList;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.complex.PComplex;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.nodes.EmptyNode;
import com.oracle.graal.python.nodes.ModuleRootNode;
import com.oracle.graal.python.nodes.NodeFactory;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.call.PythonCallNode;
import com.oracle.graal.python.nodes.control.BlockNode;
import com.oracle.graal.python.nodes.control.BreakNode;
import com.oracle.graal.python.nodes.control.BreakTargetNode;
import com.oracle.graal.python.nodes.control.ContinueNode;
import com.oracle.graal.python.nodes.control.ContinueTargetNode;
import com.oracle.graal.python.nodes.control.IfNode;
import com.oracle.graal.python.nodes.control.ReturnNode;
import com.oracle.graal.python.nodes.control.ReturnTargetNode;
import com.oracle.graal.python.nodes.control.WhileNode;
import com.oracle.graal.python.nodes.expression.AndNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic;
import com.oracle.graal.python.nodes.expression.CastToBooleanNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.expression.OrNode;
import com.oracle.graal.python.nodes.expression.TernaryArithmetic;
import com.oracle.graal.python.nodes.expression.TernaryIfNode;
import com.oracle.graal.python.nodes.expression.UnaryArithmetic;
import com.oracle.graal.python.nodes.frame.ReadNode;
import com.oracle.graal.python.nodes.function.FunctionDefinitionNode;
import com.oracle.graal.python.nodes.function.FunctionDefinitionNode.KwDefaultExpressionNode;
import com.oracle.graal.python.nodes.function.FunctionRootNode;
import com.oracle.graal.python.nodes.literal.BooleanLiteralNode;
import com.oracle.graal.python.nodes.literal.ComplexLiteralNode;
import com.oracle.graal.python.nodes.literal.DoubleLiteralNode;
import com.oracle.graal.python.nodes.literal.IntegerLiteralNode;
import com.oracle.graal.python.nodes.literal.LongLiteralNode;
import com.oracle.graal.python.nodes.literal.ObjectLiteralNode;
import com.oracle.graal.python.nodes.literal.PIntLiteralNode;
import com.oracle.graal.python.nodes.literal.StringLiteralNode;
import com.oracle.graal.python.nodes.statement.AssertNode;
import com.oracle.graal.python.nodes.statement.ExceptNode;
import com.oracle.graal.python.nodes.statement.ImportStarNode;
import com.oracle.graal.python.nodes.statement.RaiseNode;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.graal.python.nodes.subscript.SliceLiteralNode;
import com.oracle.graal.python.parser.ScopeInfo.ScopeKind;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import java.util.Arrays;
import java.util.List;
import org.antlr.v4.runtime.ParserRuleContext;

final class ExplicitVar {
    private final boolean global;
    private final String name;
    private final ExplicitVar next;

    public ExplicitVar(boolean global, String name, ExplicitVar next) {
        this.global = global;
        this.name = name;
        this.next = next;
    }
}



public final class PythonNodeFactory {

//    public static final class Scope {
//
//        private FrameDescriptor frameDescriptor;
//        private ScopeKind kind;
//        private Scope parent;
//        ExplicitVar explicitVars;
//
//        public Scope(ScopeKind kind, Scope parent, FrameDescriptor frameDescriptor) {
//            this.kind = kind;
//            this.parent = parent;
//            this.frameDescriptor = frameDescriptor;
//        }
//
//        public void setKind(ScopeKind kind) {
//            this.kind = kind;
//            }
//    }
    
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
    
    
    void log(Object... args) {
//        for (int i = 0; i < scopes.size(); i++) {
//            System.out.print("    ");
//        }
        ScopeInfo currentScope = scopeEnvironment.getCurrentScope();
        while (currentScope != null && currentScope.getParent() != null) {
            System.out.print("    ");
            currentScope = currentScope.getParent();
        }
        StackTraceElement[] stackTrace = new RuntimeException().getStackTrace();
        System.out.print(stackTrace[1].getMethodName());
        for (Object o : args) {
            System.out.print(' ');
            print(o);
        }
        System.out.println();
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
    
//    private final ArrayList<Scope> scopes = new ArrayList<>();
//    private Scope generatorScope;
//
//    private Scope currentScope() {
//        return scopeEnvironment.get(scopeEnvironment.size() - 1);
//    }

    public final class ArgDefListBuilder {

        
        public void param(String name, ExpressionNode type, ExpressionNode defValue) {
            log(name, type, defValue);
        }

        public void splat(String name, ExpressionNode type) {
            log(name, type);
        }

        public void kwargs(String name, ExpressionNode type) {
            log(name, type);
        }
    }

    public final class ArgListBuilder {
        private final ExpressionNode[] EMPTY = new ExpressionNode[0];
        
        private List<ExpressionNode> args;
        private List<ExpressionNode> nameArgs;
        private ExpressionNode starArg;
        private ExpressionNode kwArg;
       
        public ExpressionNode[] build() {
            log();
            return null;
        }

        public void addArg(ExpressionNode value) {
            log(value);
            if (args == null) {
                args = new ArrayList<>();
            }
            args.add(value);
        }
        
        public ExpressionNode[] getArgs() {
            return args == null ? EMPTY : args.toArray(new ExpressionNode[0]);
        }
        
        public void addNamedArg(String name, ExpressionNode value) {
            log(name, value);
            if (nameArgs == null) {
                nameArgs = new ArrayList<>();
            }
            nameArgs.add(nodeFactory.createKeywordLiteral(value, name ));
        }

        public ExpressionNode[] getNameArgs() {
            return nameArgs == null ? EMPTY : nameArgs.toArray(EMPTY);
        }
        
        public boolean existNameArg() {
            return !(nameArgs == null || nameArgs.isEmpty());
        }
        
        public void addKwArg(ExpressionNode value) {
            log(value);
            if (kwArg == null) {
                kwArg = value;
            } else {
                kwArg = nodeFactory.createDictionaryConcat(kwArg, value);
            }
        }
        
        public ExpressionNode getKwArgs() {
            return kwArg;
        }

        public void addStarArg(ExpressionNode value) {
            log(value);
            if (starArg == null) {
                starArg = value;
            } else {
                starArg = nodeFactory.createBinaryOperation("+", starArg, value);
            }
        }
        
        public ExpressionNode getStarArgs() {
            return starArg;
        }
        
    }

    public ExpressionNode asExpression(StatementNode[] statements, int start, int stop) {
        if (statements == null || statements.length == 0) {
            return EmptyNode.create();
        }
        int len = statements.length;
        ExpressionNode result;
        if (len == 1) {
            result = asExpression(statements[0]);
            result.assignSourceSection(createSourceSection(start, stop));
        } else {
            // two and more
            ExpressionNode en = asExpression(statements[len - 1]);
            if (len == 2 ) {
                result = en.withSideEffect(statements[0]);
            } else {
                result = en.withSideEffect(Arrays.copyOf(statements, len - 1));
            }
        }
        result.assignSourceSection(createSourceSection(start, stop));
        return result;
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
    
    public ArgDefListBuilder argDefListBuilder() {
        log();
        return new ArgDefListBuilder();
    }

    public ArgListBuilder argListBuilder() {
        log();
        return new ArgListBuilder();
    }

    public StatementNode createBlock(StatementNode[] statements) {
        log((Object) statements);
        return BlockNode.create(statements);
    }

    public StatementNode createImport(String name, int level, String asName) {
        log(name, asName);

    if (asName == null) {
       ExpressionNode importNode = nodeFactory.createImport(name).asExpression();
       return new ImportStarNode(name, level);
    } //else {
//
// }
//
// ArrayList<String> fromlist = new ArrayList<>();
// ArrayList<WriteNode> asNodes = new ArrayList<>();
// if (ctx.import_as_names() != null) {
// for (Python3Parser.Import_as_nameContext ic : ctx.import_as_names().import_as_name()) {
// String attribute = ic.NAME(0).getText();
// String asName = attribute;
// if (ic.NAME().size() == 2) {
// asName = ic.NAME(1).getText();
// }
// fromlist.add(attribute);
// asNodes.add((WriteNode) environment.findVariable(asName).makeWriteNode(EmptyNode.create()));
// }
// return factory.createImportFrom(sb.toString(), fromlist.toArray(new String[0]),
// asNodes.toArray(new WriteNode[0]), level);
//
// } else {
// if (!environment.atModuleLevel()) {
// throw errors.raiseInvalidSyntax(source, deriveSourceSection(ctx), "import * only allowed at
// module level");
// }
// return factory.createImportStar(sb.toString(), level);
// }
        return null;
    }

    public StatementNode createImportFrom(String name, Object[] asNames) {
        log(name, asNames);
// return ImportFromNode.create(importee, fromlist, readNodes, level);
        return null;
    }

    public StatementNode createRaise(ExpressionNode value, ExpressionNode from) {
        log(value, from);
        return RaiseNode.create(value, from);
    }

    public StatementNode createExpressionStatement(ExpressionNode expression) {
        log(expression);
        return expression == null ? null : expression.asStatement();
    }

    public StatementNode createReturn(ExpressionNode value, int start, int stop) {
        log(value);
        StatementNode result = new ReturnNode.FrameReturnNode(nodeFactory.createWriteLocal(value, scopeEnvironment.getReturnSlot()));
        result.assignSourceSection(createSourceSection(start, stop));
        return result;
    }

    public StatementNode createBreak() {
        log();
        return new BreakNode();
    }

    public StatementNode createContinue() {
        log();
        return new ContinueNode();
    }

    public StatementNode createPass(int start, int stop) {
        log();
        EmptyNode result = EmptyNode.create();
        result.assignSourceSection(createSourceSection(start, stop));
        return result.asStatement();
    }
    public StatementNode createDel(ExpressionNode[] result) {
        log((Object) result);
        return null;
    }

    public ExpressionNode createYield(ExpressionNode value) {
        log(value);

        return null;
    }

    public ExpressionNode createFunction(String name, String enclosingClassName, StatementNode body, int startIndex, int stopIndex) {
        Signature signature = Signature.EMPTY;
        StatementNode argumentLoads = BlockNode.create();
        
        ExpressionNode doc = null;
        doc = nodeFactory.createStringLiteral("");
        ExpressionNode funcDef;
        
        body = nodeFactory.createBlock(argumentLoads, body);
        ReturnTargetNode returnTarget = new ReturnTargetNode(body, nodeFactory.createReadLocal(scopeEnvironment.getReturnSlot()));
        SourceSection sourceSection = createSourceSection(startIndex, stopIndex);
        returnTarget.assignSourceSection(sourceSection);
        
        ExpressionNode[] defaults = null;
        KwDefaultExpressionNode[] kwDefaults = null;
        
        /**
         * Function root
         */
        FrameDescriptor fd = scopeEnvironment.getCurrentFrame();
        FunctionRootNode funcRoot = nodeFactory.createFunctionRoot(sourceSection, name, scopeEnvironment.isInGeneratorScope(), fd, returnTarget, scopeEnvironment.getExecutionCellSlots(), signature);
        if(scopeEnvironment.isInGeneratorScope()) {
            // todo
            funcDef = null;
        } else {
            RootCallTarget ct = Truffle.getRuntime().createCallTarget(funcRoot);
            funcDef = new FunctionDefinitionNode(name, enclosingClassName, doc, defaults, kwDefaults, ct, scopeEnvironment.getDefinitionCellSlots(), scopeEnvironment.getExecutionCellSlots());
        }
        return funcDef;
    }
    
    public StatementNode createReadNodeForFuncDef(ExpressionNode funcDef, String name) {
        ReadNode funcVar = scopeEnvironment.findVariable(name);
        StatementNode result = funcVar.makeWriteNode(funcDef);
        // TODO I'm not sure, whether this assingning of sourcesection is right. 
        result.assignSourceSection(((FunctionDefinitionNode)funcDef).getFunctionRoot().getSourceSection());
        return result;
    }
    
    public StatementNode createIf(ExpressionNode test, StatementNode thenStatement, StatementNode elseStatement, int start, int stop) {
        log(test, thenStatement, elseStatement);
        StatementNode node = new IfNode(CastToBooleanNode.createIfTrueNode(test), thenStatement, elseStatement);
        node.assignSourceSection(createSourceSection(start, stop));
        return node;
    }

    public StatementNode createWhile(ExpressionNode test, StatementNode body) {
        log(test, body);
        return new WhileNode(CastToBooleanNode.createIfTrueNode(test), body);
    }

    public StatementNode createLoopElse(StatementNode loop, StatementNode elseStatement) {
        log(loop, elseStatement);
        return null;
    }

    public StatementNode createFor(ExpressionNode[] expressions, ExpressionNode list, StatementNode body) {
        log(expressions, list, body);
        return null;
    }

    public StatementNode createBreakTarget(StatementNode node) {
        log(node);
        return new BreakTargetNode(node, null);
    }

    public StatementNode createContinueTarget(StatementNode node) {
        log(node);
        return new ContinueTargetNode(node);
    }

    public StatementNode createTry(StatementNode body, ExceptNode[] exceptNodes, StatementNode elseStatement, StatementNode finallyStatement) {
        log(body, exceptNodes, elseStatement, finallyStatement);
        return null;
    }

    public ExceptNode createExcept(ExpressionNode test, String asName, StatementNode body) {
        log(test, asName, body);
        return null;
    }

    public ExpressionNode createOr(ExpressionNode[] values) {
        log((Object) values);
        assert values.length >= 2;
        ExpressionNode last = values[0];
        for (int i = 1; i < values.length; i++) {
            last = new OrNode(last, values[i]);
        }
        return last;
    }

    public ExpressionNode createAnd(ExpressionNode[] values) {
        log((Object) values);
        assert values.length >= 2;
        ExpressionNode last = values[0];
        for (int i = 1; i < values.length; i++) {
            last = new AndNode(last, values[i]);
        }
        return last;
    }

    public ExpressionNode createNot(ExpressionNode value) {
        log(value);
        return CastToBooleanNode.createIfFalseNode(value);
    }

    public ExpressionNode createComparison(ExpressionNode firstValue, String[] operations, ExpressionNode[] otherValues, int start, int stop) {
        log(firstValue, operations, otherValues);
        String operator;
        ExpressionNode left = firstValue;
        ExpressionNode right;
        ExpressionNode result = null;
        for (int i = 0; i < operations.length; i++) {
            operator = operations[i];
            right = otherValues[i];
            if (result == null) {
                result = nodeFactory.createComparisonOperation(operator, left, right);
            } else {
                ExpressionNode next = nodeFactory.createComparisonOperation(operator, left, right);
                result = new AndNode(result, next);
            }
            left = right;
        }
        result.assignSourceSection(createSourceSection(start, stop));
        return result;
    }

    public ExpressionNode createArithmetic(BinaryArithmetic operation, ExpressionNode left, ExpressionNode right, int start, int stop) {
        log(operation, left, right);
        ExpressionNode result = operation.create(left, right);
        result.assignSourceSection(createSourceSection(start, stop));
        return result;
    }

    public ExpressionNode createArithmetic(UnaryArithmetic arithmetic, ExpressionNode operand) {
        log(arithmetic, operand);
        return arithmetic.create(operand);
    }

    public ExpressionNode createPow(ExpressionNode left, ExpressionNode right, int start, int stop) {
        log(left, right);
        ExpressionNode result = TernaryArithmetic.Pow.create(left, right);
        result.assignSourceSection(createSourceSection(start, stop));
        return result;
    }

    public ExpressionNode createCall(ExpressionNode target, ArgListBuilder parameters, int start, int stop) {
        log(target, parameters);
//        List<ExpressionNode> argumentNodes = new ArrayList<>();
//        List<ExpressionNode> keywords = new ArrayList<>();
//        ExpressionNode[] splatArguments = new ExpressionNode[2];
//        visitCallArglist(arglist, argumentNodes, keywords, splatArguments);
//        PythonCallNode callNode = PythonCallNode.create(target, argumentNodes.toArray(new ExpressionNode[0]), keywords.toArray(new ExpressionNode[0]), splatArguments[0], splatArguments[1]);
//        if (argumentNodes.size() == 0 && owner instanceof ReadGlobalOrBuiltinNode && ((ReadGlobalOrBuiltinNode) owner).getAttributeId().equals(SUPER)) {
//            // super call without arguments
//            environment.registerSpecialClassCellVar();
//        }
        PythonCallNode callNode = PythonCallNode.create(target, parameters.getArgs(), parameters.getNameArgs(), parameters.getStarArgs(), parameters.getKwArgs());
        callNode.assignSourceSection(createSourceSection(start, stop));
        // remove source section for the taget to be comaptiable with old parser behavior
        // TODO check, whether we really need to delete the source sections
        target.assignSourceSection(null);
        return callNode;
    }

    public ExpressionNode createSubscript(ExpressionNode receiver, ExpressionNode[] parameters) {
        log(receiver, parameters);
        return null;
    }

    public ExpressionNode createGetAttribute(ExpressionNode receiver, String name) {
        log(receiver, name);
        return null;
    }

    public ExpressionNode createBooleanLiteral(boolean value, int start, int stop) {
        log(value);
        ExpressionNode result = new BooleanLiteralNode(value);
        result.assignSourceSection(createSourceSection(start, stop));
        return result;
    }

    public ExpressionNode createObjectLiteral(Object value, int start, int stop) {
        log(value);
        ExpressionNode result =  new ObjectLiteralNode(value);
        result.assignSourceSection(createSourceSection(start, stop));
        return result;
    }

    public ExpressionNode createStringLiteral(String[] values, int startIndex, int stopIndex) {
        log((Object) values);
        StringLiteralNode result;
        if (values.length == 1) {
            result = new StringLiteralNode(values[0]);
        } else {
            StringBuilder str = new StringBuilder();
            for (String s : values) {
                str.append(s);
            }
            result = new StringLiteralNode(str.toString());
        }
        result.assignSourceSection(createSourceSection(startIndex, stopIndex));
        return result;
    }

    public ExpressionNode createNumberLiteral(String value, int start, int base, int startIndex, int stopIndex) {
        log(value, start, base);

        int i = start;
        long result = 0;
        while (i < value.length()) {
            long next = result * base + digitValue(value.charAt(i));
            if (next < 0) {
                // overflow
                BigInteger bigResult = BigInteger.valueOf(result);
                BigInteger bigBase = BigInteger.valueOf(base);
                while (i < value.length()) {
                    bigResult = bigResult.multiply(bigBase).add(BigInteger.valueOf(digitValue(value.charAt(i))));
                    i++;
                }
                PIntLiteralNode intLiteral = new PIntLiteralNode(bigResult);
                intLiteral.assignSourceSection(createSourceSection(startIndex, stopIndex));
                return intLiteral;
            }
            result = next;
            i++;
        }
        
        ExpressionNode intLiteral = result <= Integer.MAX_VALUE ? new IntegerLiteralNode((int) result) : new LongLiteralNode(result);
        intLiteral.assignSourceSection(createSourceSection(startIndex, stopIndex));
        return intLiteral;
    }

    private static int digitValue(char ch) {
        if (ch >= '0' && ch <= '9') {
            return ch - '0';
        } else if (ch >= 'a' && ch <= 'f') {
            return ch - 'a' + 10;
        } else {
            assert ch >= 'A' && ch <= 'f';
            return ch - 'A' + 10;
        }
    }

    public ExpressionNode createFloatNumberLiteral(String value, boolean imaginary, int start, int stop) {
        log(value, imaginary);
        ExpressionNode result;
        if (imaginary) {
            double imag = Double.parseDouble(value.substring(0, value.length() - 1));
            result = new ComplexLiteralNode(new PComplex(PythonBuiltinClassType.PComplex, 0, imag));
        } else {
            result =  new DoubleLiteralNode(Double.parseDouble(value));
        }
        result.assignSourceSection(createSourceSection(start, stop));
        return result;
    }

    public ExpressionNode createVariableLookup(ParserCtxInfo parserInfo, String name, int start, int stop) {
        log(name);
        if (parserInfo.isVarDefinition) {
            scopeEnvironment.createLocal(name);
        }
        ExpressionNode result = (ExpressionNode)scopeEnvironment.findVariable(name);
        result.assignSourceSection(createSourceSection(start, stop));
        return result;
    }

    public StatementNode createClassDefinition(String name, ExpressionNode[] baseClasses, StatementNode body) {
        log(name, baseClasses, body);
        return null;
    }

    public void registerGlobal(String[] names) {
        log((Object) names);
//        Scope scope = currentScope();
//        for (String name : names) {
//            scope.explicitVars = new ExplicitVar(true, name, scope.explicitVars);
//        }
        ScopeInfo scopeInfo = scopeEnvironment.getCurrentScope();
        for (String name : names) {
            scopeInfo.addExplicitGlobalVariable(name);
        }
    }

    public void registerNonLocal(String[] names) {
        log((Object) names);
        ScopeInfo scopeInfo = scopeEnvironment.getCurrentScope();
        for (String name : names) {
            scopeInfo.addExplicitNonlocalVariable(name);
        }
    }

    public Object createAssert(ExpressionNode test, ExpressionNode message) {
        log(test, message);
        return new AssertNode(CastToBooleanNode.createIfTrueNode(test), message);
    }

    public StatementNode createWith(ExpressionNode expression, ExpressionNode target, StatementNode body) {
        log(expression, target, body);
        return null;
    }

    public ExpressionNode createStar(ExpressionNode value) {
        log(value);
        return null;
    }

    public ExpressionNode createSubscript(ExpressionNode receiver, ExpressionNode subscript) {
        log(receiver, subscript);
        return null;
    }

    public ExpressionNode createCollection(ExpressionNode[] values, PythonBuiltinClassType type) {
        log(values, type);
        return null;
    }

    public ExpressionNode createSlice(ExpressionNode start, ExpressionNode stop, ExpressionNode step) {
        log(start, stop, step);
        ExpressionNode lower = start == null ? new ObjectLiteralNode(PNone.NO_VALUE) : start;
        ExpressionNode upper = stop == null ? new ObjectLiteralNode(PNone.NO_VALUE) : stop;
        ExpressionNode stepValue = step == null ? new ObjectLiteralNode(PNone.NO_VALUE) : step;
        return SliceLiteralNode.create(lower, upper, stepValue);
    }

    public ExpressionNode createForComprehension(boolean async, ExpressionNode target, ExpressionNode name, ExpressionNode value, ExpressionNode[] conditions, PythonBuiltinClassType resultType) {
        log(async, target, name, value, conditions, resultType);
        return null;
    }

    public StatementNode createTestAssign(ExpressionNode lhs, ExpressionNode test, ExpressionNode rhs, int start, int stop) {
        log(lhs, test, rhs);
        return null;
    }

    public StatementNode createAugmentedAssignment(ExpressionNode lhs, String assignOp, ExpressionNode rhs, int start, int stop) {
        log(lhs, assignOp, rhs);
        return null;
    }

    public ReadNode makeTempLocalVariable() {
        String tempName = TEMP_LOCAL_PREFIX + scopeEnvironment.getCurrentScope().getFrameDescriptor().getSize();
        FrameSlot tempSlot = scopeEnvironment.getCurrentScope().createSlotIfNotPresent(tempName);
        return (ReadNode) nodeFactory.createReadLocal(tempSlot);
    }
    
    public StatementNode createAssignment(ExpressionNode[] lhs, ExpressionNode rhs, int start, int stop) {
        log(lhs, rhs);
        int len = lhs.length;
        StatementNode result;
        if (len == 1) {
            result = ((ReadNode) lhs[0]).makeWriteNode(rhs);
        } else {
            StatementNode [] assignments = new StatementNode[len + 1];
            ReadNode tmp = makeTempLocalVariable();
            StatementNode tmpWrite = tmp.makeWriteNode(rhs);
            assignments[0] = tmpWrite;
            for (int i = lhs.length - 1; i >= 0; i--) {
                assignments[len - i] = ((ReadNode) lhs[i]).makeWriteNode((ExpressionNode) tmp);
            }
            result = nodeFactory.createBlock(assignments);
        }
        result.assignSourceSection(createSourceSection(start, stop));
        return result;
    }

    public ExpressionNode createIfExpression(ExpressionNode test, ExpressionNode thenValue, ExpressionNode elseValue, int start, int stop) {
        log(test, thenValue, elseValue);
        ExpressionNode result = new TernaryIfNode(CastToBooleanNode.createIfTrueNode(test), thenValue, elseValue);
        result.assignSourceSection(createSourceSection(start, stop));
        return result;
    }

    public ExpressionNode createLambda(ArgDefListBuilder args, ExpressionNode body) {
        log(args, body);
        return null;
    }
    
    public ModuleRootNode createModuleRoot(String name, ExpressionNode file, FrameDescriptor fd) {
        log(name, file);
        String doc = (new DocExtractor()).extract(file);
        if (doc != null) {
            int cut = doc.startsWith("'''") || doc.startsWith("\"\"\"") ? 3 : 1;
            doc = doc.substring(cut, doc.length() - cut);
        }
//        if (file instanceof ExpressionNode.ExpressionWithSideEffect) { 
//                && ((ExpressionNode.ExpressionWithSideEffect)file).getSideEffect() instanceof StringLiteralNode)
        return nodeFactory.createModuleRoot(name, doc, file, fd);
    }

//    public Scope createScope(ScopeKind kind) {
//        return createScope(kind, null);
//    }
//
//    public Scope createScope(ScopeKind kind, FrameDescriptor locals) {
//        log(kind, locals);
//        Scope parent = scopeEnvironment.isEmpty() ? null : scopeEnvironment.get(scopeEnvironment.size() - 1);
//        Scope newScope = new Scope(kind, parent, locals);
//        scopeEnvironment.add(newScope);
//        return newScope;
//    }
    
    public ScopeInfo createScope(ParserRuleContext ctx, ScopeKind kind) {
        return createScope(ctx, kind, null);
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
    
    public boolean createGeneratorScope(ExpressionNode target, ExpressionNode name) {
        log(target, name);

        // we're still within the transparent arguments scope
//
//        if (generatorScope == null) {
//            generatorScope = scopeEnvironment.get(scopeEnvironment.size() - 1);
//            generatorScope.setKind(ScopeKind.Generator);
//            return true;
//        }
        // get rid of the argument scope immediately
        leaveScope();
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
    
    public void assignSourceSection(PNode node, int start, int stop) {
        node.assignSourceSection(createSourceSection(start, stop));
    }
    
    private static class DocExtractor {
        
        private boolean firstStatement = true;
        
        public String extract(StringLiteralNode node) {
            return node.getValue();
        }
        
        public String extract(StatementNode node) {
            if (node instanceof ExpressionNode.ExpressionStatementNode) {
                return extract(((ExpressionNode.ExpressionStatementNode) node).getExpression());
            }
            return null;
        }
        
        public String extract(ExpressionNode node) {
            if (node instanceof StringLiteralNode) {
                return extract((StringLiteralNode) node);
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
