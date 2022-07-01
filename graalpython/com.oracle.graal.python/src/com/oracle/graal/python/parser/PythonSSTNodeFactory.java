/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.nodes.BuiltinNames.J___FUTURE__;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import java.util.ArrayList;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.ModuleRootNode;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.RootNodeFactory;
import com.oracle.graal.python.nodes.control.ReturnNode;
import com.oracle.graal.python.nodes.control.ReturnTargetNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.frame.ReadLocalVariableNode;
import com.oracle.graal.python.nodes.function.FunctionDefinitionNode;
import com.oracle.graal.python.nodes.function.FunctionRootNode;
import com.oracle.graal.python.nodes.literal.StringLiteralNode;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.graal.python.parser.ScopeInfo.ScopeKind;
import com.oracle.graal.python.parser.sst.AnnAssignmentSSTNode;
import com.oracle.graal.python.parser.sst.AnnotationSSTNode;
import com.oracle.graal.python.parser.sst.ArgDefListBuilder;
import com.oracle.graal.python.parser.sst.ArgListBuilder;
import com.oracle.graal.python.parser.sst.AssignmentSSTNode;
import com.oracle.graal.python.parser.sst.AugAssignmentSSTNode;
import com.oracle.graal.python.parser.sst.BinaryArithmeticSSTNode;
import com.oracle.graal.python.parser.sst.BlockSSTNode;
import com.oracle.graal.python.parser.sst.BooleanLiteralSSTNode;
import com.oracle.graal.python.parser.sst.CallSSTNode;
import com.oracle.graal.python.parser.sst.ClassSSTNode;
import com.oracle.graal.python.parser.sst.CollectionSSTNode;
import com.oracle.graal.python.parser.sst.DecoratorSSTNode;
import com.oracle.graal.python.parser.sst.FactorySSTVisitor;
import com.oracle.graal.python.parser.sst.FloatLiteralSSTNode;
import com.oracle.graal.python.parser.sst.ForComprehensionSSTNode;
import com.oracle.graal.python.parser.sst.ForSSTNode;
import com.oracle.graal.python.parser.sst.FunctionDefSSTNode;
import com.oracle.graal.python.parser.sst.GeneratorFactorySSTVisitor;
import com.oracle.graal.python.parser.sst.GetAttributeSSTNode;
import com.oracle.graal.python.parser.sst.ImportFromSSTNode;
import com.oracle.graal.python.parser.sst.ImportSSTNode;
import com.oracle.graal.python.parser.sst.NumberLiteralSSTNode;
import com.oracle.graal.python.parser.sst.ReturnSSTNode;
import com.oracle.graal.python.parser.sst.SSTNode;
import com.oracle.graal.python.parser.sst.SimpleSSTNode;
import com.oracle.graal.python.parser.sst.StarSSTNode;
import com.oracle.graal.python.parser.sst.StringLiteralSSTNode;
import com.oracle.graal.python.parser.sst.StringLiteralSSTNode.RawStringLiteralSSTNode;
import com.oracle.graal.python.parser.sst.StringUtils;
import com.oracle.graal.python.parser.sst.SubscriptSSTNode;
import com.oracle.graal.python.parser.sst.TernaryIfSSTNode;
import com.oracle.graal.python.parser.sst.VarLookupSSTNode;
import com.oracle.graal.python.parser.sst.WithSSTNode;
import com.oracle.graal.python.parser.sst.YieldExpressionSSTNode;
import com.oracle.graal.python.runtime.PythonParser;
import com.oracle.graal.python.runtime.PythonParser.ParserMode;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.strings.TruffleString;

public final class PythonSSTNodeFactory {

    /**
     * Service that allows parsing expressions found inside f-strings to SST nodes.
     */
    public interface FStringExprParser {
        SSTNode parseExpression(PythonParser.ParserErrorCallback errorCallback, String text, PythonSSTNodeFactory nodeFactory, boolean fromInteractiveSource);
    }

    private final RootNodeFactory rootNodeFactory;
    private final ScopeEnvironment scopeEnvironment;
    private final Source source;
    private final PythonParser.ParserErrorCallback errors;
    private FStringExprParser fStringExprParser;
    private boolean futureAnnotations = false;

    public PythonSSTNodeFactory(PythonParser.ParserErrorCallback errors, Source source, FStringExprParser fStringExprParser) {
        this.errors = errors;
        this.rootNodeFactory = RootNodeFactory.create(errors.getContext().getLanguage());
        this.scopeEnvironment = new ScopeEnvironment();
        this.source = source;
        this.fStringExprParser = fStringExprParser;
    }

    public ScopeEnvironment getScopeEnvironment() {
        return scopeEnvironment;
    }

    public void throwSyntaxError(int startOffset, int endOffset, TruffleString message, Object... messageParams) {
        throw errors.raiseInvalidSyntax(source, createSourceSection(startOffset, endOffset), message, messageParams);
    }

    public SSTNode createDecorator(String name, ArgListBuilder arg, int startOffset, int endOffset) {
        int dotIndex = name.indexOf('.');
        String mangledName = dotIndex == -1 ? mangleNameInCurrentScope(name) : mangleNameInCurrentScope(name.substring(0, dotIndex)) + name.substring(dotIndex);
        return new DecoratorSSTNode(mangledName, arg, startOffset, endOffset);
    }

    public SSTNode createImport(String name, String asName, int startOffset, int endOffset) {
        String varName;
        if (asName != null) {
            varName = asName;
        } else {
            // checking if the name is not something like module.submodule
            int dotIndex = name.indexOf('.');
            if (dotIndex == -1) {
                varName = name;
            } else {
                // create local variable just for the top module
                varName = name.substring(0, dotIndex);
            }
            varName = mangleNameInCurrentScope(varName);
        }
        scopeEnvironment.createLocal(varName);
        return new ImportSSTNode(scopeEnvironment.getCurrentScope(), name, asName, startOffset, endOffset);
    }

    public SSTNode createImportFrom(String from, String[][] asNames, int startOffset, int endOffset) {
        if (asNames != null) {
            for (String[] asName : asNames) {
                if (J___FUTURE__.equals(from) && asName[0].equals("annotations")) {
                    futureAnnotations = true;
                }
                scopeEnvironment.createLocal(asName[1] == null ? asName[0] : asName[1]);
            }
        } else {
            if (!scopeEnvironment.atModuleLevel()) {
                throw errors.raiseInvalidSyntax(source, createSourceSection(startOffset, endOffset), ErrorMessages.IMPORT_STAR_ONLY_ALLOWED_AT_MODULE_LEVEL);
            }
        }

        return new ImportFromSSTNode(scopeEnvironment.getCurrentScope(), from, asNames, startOffset, endOffset);
    }

    public SSTNode createAnnotationType(SSTNode type) {
        SSTNode annotType = type;
        if (futureAnnotations && type != null) {
            final String value = source.getCharacters().subSequence(type.getStartOffset(), type.getEndOffset()).toString();
            annotType = new RawStringLiteralSSTNode(value, type.getStartOffset(), type.getEndOffset());
        }
        return annotType;
    }

    public FunctionDefSSTNode createFunctionDef(ScopeInfo functionScope, String name, String enclosingClassName, ArgDefListBuilder argBuilder, SSTNode body, SSTNode resultAnnotation, int startOffset,
                    int endOffset) {
        SSTNode annotation = createAnnotationType(resultAnnotation);
        return new FunctionDefSSTNode(functionScope, name, enclosingClassName, argBuilder, body, annotation, startOffset, endOffset);
    }

    public String mangleNameInCurrentScope(String name) {
        if (cannotBeMangled(name)) {
            return name;
        }
        // then name can be mangled if is under class
        ScopeInfo scope = scopeEnvironment.getCurrentScope();
        while (scope != null && scope.getScopeKind() != ScopeKind.Class) {
            scope = scope.getParent();
        }

        if (scope != null) {
            try {
                return mangleName(scope.getScopeId(), name);
            } catch (OverflowException e) {
                throw PRaiseNode.raiseUncached(null, PythonBuiltinClassType.OverflowError, ErrorMessages.PRIVATE_IDENTIFIER_TOO_LARGE_TO_BE_MANGLED);
            }
        }
        return name;
    }

    /**
     * Tests if the provided identifier is a candidate for name mangling.
     */
    @TruffleBoundary
    private static boolean cannotBeMangled(String identifier) {
        int len = identifier.length();
        // Don't mangle __whatever__ or names with dots.
        return len < 3 || identifier.charAt(0) != '_' || identifier.charAt(1) != '_' || (identifier.charAt(len - 1) == '_' && identifier.charAt(len - 2) == '_') || identifier.indexOf('.') != -1;
    }

    @TruffleBoundary
    public static TruffleString mangleName(TruffleString privateobj, TruffleString ident) throws OverflowException {
        return toTruffleStringUncached(mangleName(privateobj.toJavaStringUncached(), ident.toJavaStringUncached()));
    }

    /**
     * Implements semantics of {@code parser.c:_Py_Mangle}
     */
    @TruffleBoundary
    public static String mangleName(String privateobj, String ident) throws OverflowException {
        // Name mangling: __private becomes _classname__private. This is independent from how
        // the name is used.
        if (cannotBeMangled(ident)) {
            return ident;
        }
        // The length of 'privateobj' must be checked because if someone uses the 'type' constructor
        // it's allowed to pass an empty name.
        String privateobjStripped;
        if (privateobj.length() > 0 && privateobj.charAt(0) == '_') {
            // trim leading '_'
            int index = 1;
            int scopeNameLen = privateobj.length();
            while (index < scopeNameLen && privateobj.charAt(index) == '_') {
                index++;
            }
            privateobjStripped = index != scopeNameLen ? privateobj.substring(index) : null;
        } else {
            privateobjStripped = privateobj;
        }
        if (privateobjStripped != null) {
            if ((long) privateobjStripped.length() + ident.length() >= Integer.MAX_VALUE) {
                throw OverflowException.INSTANCE;
            }
            // ident = "_" + priv[ipriv:] + ident # i.e. 1+plen+nlen bytes
            return '_' + privateobjStripped + ident;
        }
        return ident;
    }

    public VarLookupSSTNode createVariableLookup(String name, int start, int stop) {
        String mangleName = mangleNameInCurrentScope(name);
        scopeEnvironment.addSeenVar(mangleName);
        return new VarLookupSSTNode(mangleName, start, stop);
    }

    public SSTNode createClassDefinition(String name, ArgListBuilder baseClasses, SSTNode body, int start, int stop) {
        // scopeEnvironment.createLocal(name);
        return new ClassSSTNode(scopeEnvironment.getCurrentScope(), name, baseClasses, body, start, stop);
    }

    public GetAttributeSSTNode createGetAttribute(SSTNode receiver, String name, int startOffset, int endOffset) {
        String mangledName = mangleNameInCurrentScope(name);
        return new GetAttributeSSTNode(receiver, mangledName, startOffset, endOffset);
    }

    public SSTNode registerGlobal(String[] names, int startOffset, int endOffset) {
        ScopeInfo scopeInfo = scopeEnvironment.getCurrentScope();
        ScopeInfo globalScope = scopeEnvironment.getGlobalScope();
        for (String name : names) {
            if (scopeInfo.isExplicitNonlocalVariable(name)) {
                throw errors.raiseInvalidSyntax(source, createSourceSection(startOffset, endOffset), ErrorMessages.NONLOCAL_AND_GLOBAL, name);
            }
            if (scopeInfo.findFrameSlot(name) != null) {
                // The expectation is that in the local context the variable can not have slot yet.
                // The slot is created by assignment or declaration
                throw errors.raiseInvalidSyntax(source, createSourceSection(startOffset, endOffset), ErrorMessages.NAME_IS_ASSIGNED_BEFORE_GLOBAL, name);
            }
            if (scopeInfo.getSeenVars() != null && scopeInfo.getSeenVars().contains(name)) {
                throw errors.raiseInvalidSyntax(source, createSourceSection(startOffset, endOffset), ErrorMessages.NAME_IS_USED_BEFORE_GLOBAL, name);
            }
            scopeInfo.addExplicitGlobalVariable(name);
            // place the global variable into global space, see test_global_statemnt.py
            globalScope.addExplicitGlobalVariable(name);
        }
        return new SimpleSSTNode(SimpleSSTNode.Type.EMPTY, startOffset, endOffset);
    }

    public SSTNode registerNonLocal(String[] names, int startOffset, int endOffset) {
        ScopeInfo scopeInfo = scopeEnvironment.getCurrentScope();
        if (scopeInfo.getScopeKind() == ScopeKind.Module) {
            throw errors.raiseInvalidSyntax(source, createSourceSection(startOffset, endOffset), ErrorMessages.NONLOCAL_AT_MODULE_LEVEL);
        }
        for (String name : names) {
            if (scopeInfo.isExplicitGlobalVariable(name)) {
                throw errors.raiseInvalidSyntax(source, createSourceSection(startOffset, endOffset), ErrorMessages.NONLOCAL_AND_GLOBAL, name);
            }
            if (scopeInfo.findFrameSlot(name) != null) {
                // the expectation is that in the local context the variable can not have slot yet.
                // The slot is created by assignment or declaration
                throw errors.raiseInvalidSyntax(source, createSourceSection(startOffset, endOffset), ErrorMessages.NAME_IS_ASSIGNED_BEFORE_NONLOCAL, name);
            }
            scopeInfo.addExplicitNonlocalVariable(name);
        }
        return new SimpleSSTNode(SimpleSSTNode.Type.EMPTY, startOffset, endOffset);
    }

    public ReturnSSTNode createReturn(SSTNode value, int startOffset, int endOffset) {
        if (!scopeEnvironment.isInFunctionScope()) {
            throw errors.raiseInvalidSyntax(source, createSourceSection(startOffset, endOffset), ErrorMessages.RETURN_OUTSIDE_FUNC);
        }
        return new ReturnSSTNode(value, startOffset, endOffset);
    }

    public WithSSTNode createWith(SSTNode expression, SSTNode target, SSTNode body, int start, int end) {
        if (target != null) {
            checkAssignable(target, target.getStartOffset(), target.getEndOffset());
            if (target instanceof VarLookupSSTNode) {
                scopeEnvironment.createLocal(((VarLookupSSTNode) target).getName());
            }
        }
        return new WithSSTNode(expression, target, body, start, end);
    }

    public ForComprehensionSSTNode createForComprehension(boolean async, SSTNode target, SSTNode name, SSTNode[] variables, SSTNode iterator, SSTNode[] conditions, ForComprehensionSSTNode innerFor,
                    PythonBuiltinClassType resultType, int lineNumber,
                    int level, int startOffset, int endOffset) {
        for (SSTNode variable : variables) {
            checkAssignable(variable, variable.getStartOffset(), variable.getEndOffset());
            declareVar(variable);
        }
        return new ForComprehensionSSTNode(scopeEnvironment.getCurrentScope(), async, target, name, variables, iterator, conditions, innerFor, resultType, lineNumber, level, startOffset, endOffset);
    }

    public SSTNode createAssignment(SSTNode[] lhs, SSTNode rhs, int start, int stop) {
        for (SSTNode variable : lhs) {
            checkAssignable(variable, start, stop);
            declareVar(variable);
        }
        if (lhs.length == 1 && lhs[0] instanceof StarSSTNode) {
            throw errors.raiseInvalidSyntax(source, createSourceSection(start, stop), ErrorMessages.STARRED_ASSIGMENT_MUST_BE_IN_LIST_OR_TUPLE);
        }
        return new AssignmentSSTNode(lhs, rhs, start, stop);
    }

    public SSTNode createAnnAssignment(AnnotationSSTNode annotation, SSTNode rhs, int start, int end) {
        checkAssignable(annotation.getLhs(), start, end);
        declareVar(annotation.getLhs());
        return new AnnAssignmentSSTNode(annotation, rhs, start, end);
    }

    public AnnotationSSTNode createAnnotation(SSTNode lhs, SSTNode type, int start, int end) {
        SSTNode annotType = createAnnotationType(type);
        // checking if the annotation has the right target
        if (!(lhs instanceof VarLookupSSTNode || lhs instanceof GetAttributeSSTNode || lhs instanceof SubscriptSSTNode)) {
            if (lhs instanceof CollectionSSTNode) {
                CollectionSSTNode collectionNode = (CollectionSSTNode) lhs;
                if (collectionNode.getType() == PythonBuiltinClassType.PList) {
                    throw errors.raiseInvalidSyntax(source, createSourceSection(lhs.getStartOffset(), lhs.getEndOffset()), ErrorMessages.ONLY_SINGLE_TARGET_CAN_BE_ANNOTATED, "list");
                } else if (collectionNode.getType() == PythonBuiltinClassType.PTuple) {
                    throw errors.raiseInvalidSyntax(source, createSourceSection(lhs.getStartOffset(), lhs.getEndOffset()), ErrorMessages.ONLY_SINGLE_TARGET_CAN_BE_ANNOTATED, "tuple");
                }
            }
            throw errors.raiseInvalidSyntax(source, createSourceSection(lhs.getStartOffset(), lhs.getEndOffset()), ErrorMessages.ILLEGAL_TARGET_FOR_ANNOTATION);
        }
        if (!scopeEnvironment.getCurrentScope().hasAnnotations()) {
            scopeEnvironment.getCurrentScope().setHasAnnotations(true);
        }
        return new AnnotationSSTNode(lhs, annotType, start, end);
    }

    public SSTNode createAugAssignment(SSTNode lhs, String operation, SSTNode rhs, int startOffset, int endOffset) {
        // checking if the augment assingment is valid
        checkAssignable(lhs, startOffset, endOffset);
        if (!(lhs instanceof VarLookupSSTNode || lhs instanceof GetAttributeSSTNode || lhs instanceof SubscriptSSTNode)) {
            throw errors.raiseInvalidSyntax(source, createSourceSection(startOffset, endOffset), ErrorMessages.ILLEGAL_EXPRESSION_FOR_AUGMENTED_ASSIGNEMNT);
        }
        declareVar(lhs);
        return new AugAssignmentSSTNode(lhs, operation, rhs, startOffset, endOffset);
    }

    private void checkForbiddenName(String name, int startOffset, int endOffset) {
        if (BuiltinNames.J___DEBUG__.equals(name)) {
            throw errors.raiseInvalidSyntax(source, createSourceSection(startOffset, endOffset), ErrorMessages.CANNOT_ASSIGN_TO, BuiltinNames.T___DEBUG__);
        }
    }

    private void checkAssignable(SSTNode lhs, int startOffset, int endOffset) {
        if (lhs instanceof VarLookupSSTNode) {
            checkForbiddenName(((VarLookupSSTNode) lhs).getName(), startOffset, endOffset);
        } else if (lhs instanceof GetAttributeSSTNode) {
            checkForbiddenName(((GetAttributeSSTNode) lhs).getName(), startOffset, endOffset);
        } else if (lhs instanceof StarSSTNode) {
            checkAssignable(((StarSSTNode) lhs).getValue(), startOffset, endOffset);
        } else if (lhs instanceof BinaryArithmeticSSTNode) {
            throw errors.raiseInvalidSyntax(source, createSourceSection(startOffset, endOffset), ErrorMessages.CANNOT_ASSIGN_TO, "operator");
        } else if (lhs instanceof SimpleSSTNode) {
            SimpleSSTNode.Type type = ((SimpleSSTNode) lhs).getType();
            if (type == SimpleSSTNode.Type.ELLIPSIS) {
                throw errors.raiseInvalidSyntax(source, createSourceSection(startOffset, endOffset), ErrorMessages.CANNOT_ASSIGN_TO, "Ellipsis");
            } else if (type == SimpleSSTNode.Type.NONE) {
                throw errors.raiseInvalidSyntax(source, createSourceSection(startOffset, endOffset), ErrorMessages.CANNOT_ASSIGN_TO, "None");
            }
        } else if (lhs instanceof CallSSTNode) {
            throw errors.raiseInvalidSyntax(source, createSourceSection(lhs.getStartOffset(), lhs.getEndOffset()), ErrorMessages.CANNOT_ASSIGN_TO, "function call");
        } else if (lhs instanceof BooleanLiteralSSTNode) {
            if (((BooleanLiteralSSTNode) lhs).getValue()) {
                throw errors.raiseInvalidSyntax(source, createSourceSection(startOffset, endOffset), ErrorMessages.CANNOT_ASSIGN_TO, "True");
            } else {
                throw errors.raiseInvalidSyntax(source, createSourceSection(startOffset, endOffset), ErrorMessages.CANNOT_ASSIGN_TO, "False");
            }
        } else if (lhs instanceof FloatLiteralSSTNode || lhs instanceof NumberLiteralSSTNode || lhs instanceof StringLiteralSSTNode) {
            if (lhs instanceof StringLiteralSSTNode.FormatStringLiteralSSTNode) {
                throw errors.raiseInvalidSyntax(source, createSourceSection(startOffset, endOffset), ErrorMessages.CANNOT_ASSIGN_TO, "f-string expression");
            }
            throw errors.raiseInvalidSyntax(source, createSourceSection(startOffset, endOffset), ErrorMessages.CANNOT_ASSIGN_TO, "literal");
        } else if (lhs instanceof CollectionSSTNode) {
            CollectionSSTNode collectionNode = (CollectionSSTNode) lhs;
            PythonBuiltinClassType type = collectionNode.getType();
            if (type == PythonBuiltinClassType.PDict) {
                throw errors.raiseInvalidSyntax(source, createSourceSection(startOffset, endOffset), ErrorMessages.CANNOT_ASSIGN_TO, "dict display");
            } else if (type == PythonBuiltinClassType.PSet) {
                throw errors.raiseInvalidSyntax(source, createSourceSection(startOffset, endOffset), ErrorMessages.CANNOT_ASSIGN_TO, "set display");
            }
            for (SSTNode node : collectionNode.getValues()) {
                checkAssignable(node, startOffset, endOffset);
            }
        } else if (lhs instanceof YieldExpressionSSTNode) {
            throw errors.raiseInvalidSyntax(source, createSourceSection(startOffset, endOffset), ErrorMessages.CANNOT_ASSIGN_TO, "yield expression");
        } else if (lhs instanceof ForComprehensionSSTNode) {
            PythonBuiltinClassType resultType = ((ForComprehensionSSTNode) lhs).getResultType();
            if (resultType == PythonBuiltinClassType.PGenerator) {
                throw errors.raiseInvalidSyntax(source, createSourceSection(startOffset, endOffset), ErrorMessages.CANNOT_ASSIGN_TO, "generator expression");
            }
            TruffleString calleeName;
            switch (resultType) {
                case PList:
                    calleeName = BuiltinNames.T_LIST;
                    break;
                case PSet:
                    calleeName = BuiltinNames.T_SET;
                    break;
                case PDict:
                    calleeName = BuiltinNames.T_DICT;
                    break;
                default:
                    calleeName = null;
            }
            if (calleeName == null) {
                throw errors.raiseInvalidSyntax(source, createSourceSection(startOffset, endOffset), ErrorMessages.CANNOT_ASSIGN_TO, "comprehension");
            } else {
                throw errors.raiseInvalidSyntax(source, createSourceSection(startOffset, endOffset), ErrorMessages.CANNOT_ASSIGN_TO_COMPREHENSION, calleeName);
            }
        } else if (lhs instanceof TernaryIfSSTNode) {
            throw errors.raiseInvalidSyntax(source, createSourceSection(startOffset, endOffset), ErrorMessages.CANNOT_ASSIGN_TO, "conditional expression");
        }
    }

    private void declareVar(SSTNode value) {
        if (value instanceof VarLookupSSTNode) {
            String name = ((VarLookupSSTNode) value).getName();
            if (!scopeEnvironment.isNonlocal(name)) {
                scopeEnvironment.createLocal(
                                scopeEnvironment.getCurrentScope().getScopeKind() != ScopeKind.Class
                                                ? name
                                                : ScopeEnvironment.CLASS_VAR_PREFIX + name);
            }
        } else if (value instanceof CollectionSSTNode) {
            CollectionSSTNode collection = (CollectionSSTNode) value;
            for (SSTNode variable : collection.getValues()) {
                declareVar(variable);
            }
        } else if (value instanceof StarSSTNode) {
            declareVar(((StarSSTNode) value).getValue());
        }
    }

    public ForSSTNode createForSSTNode(SSTNode[] targets, SSTNode iterator, SSTNode body, boolean containsContinue, int startOffset, int endOffset) {
        for (SSTNode target : targets) {
            checkAssignable(target, target.getStartOffset(), target.getEndOffset());
            createLocalVariable(target);
        }
        return new ForSSTNode(targets, iterator, body, containsContinue, startOffset, endOffset);
    }

    private void createLocalVariable(SSTNode var) {
        if (var instanceof VarLookupSSTNode) {
            scopeEnvironment.createLocal(((VarLookupSSTNode) var).getName());
        } else if (var instanceof CollectionSSTNode) {
            for (SSTNode item : ((CollectionSSTNode) var).getValues()) {
                createLocalVariable(item);
            }
        }
    }

    public YieldExpressionSSTNode createYieldExpressionSSTNode(SSTNode value, boolean isFrom, int startOffset, int endOffset) {
        ScopeKind scopeKind = scopeEnvironment.getScopeKind();
        if (!(scopeKind == ScopeKind.Function || scopeKind == ScopeKind.Generator)) {
            TruffleString message;
            switch (scopeKind) {
                case ListComp:
                    message = ErrorMessages.YIELD_INSIDE_LIST_COMPREHENSION;
                    break;
                case DictComp:
                    message = ErrorMessages.YIELD_INSIDE_DICT_COMPREHENSION;
                    break;
                case SetComp:
                    message = ErrorMessages.YIELD_INSIDE_SET_COMPREHENSION;
                    break;
                case GenExp:
                    message = ErrorMessages.YIELD_INSIDE_GENERATOR_COMPREHENSION;
                    break;
                default:
                    message = ErrorMessages.YIELD_OUTSIDE_FUNCTION;
            }
            throw errors.raiseInvalidSyntax(source, createSourceSection(startOffset, endOffset), message);
        }
        scopeEnvironment.setToGeneratorScope();
        return new YieldExpressionSSTNode(value, isFrom, startOffset, endOffset);
    }

    public StringLiteralSSTNode createStringLiteral(String[] values, int startOffset, int endOffset) {
        return StringLiteralSSTNode.create(values, startOffset, endOffset, source, errors, this, fStringExprParser);
    }

    public Node createParserResult(SSTNode parserSSTResult, PythonParser.ParserMode mode, Frame currentFrame, ArrayList<String> deprecationWarnings) {
        assert currentFrame == null || mode == ParserMode.InlineEvaluation || mode == ParserMode.WithArguments;
        Node result;
        boolean isGen = false;
        Frame useFrame = currentFrame;
        if (useFrame != null && PArguments.isPythonFrame(useFrame) && PArguments.getGeneratorFrameSafe(useFrame) != null) {
            useFrame = PArguments.getGeneratorFrame(useFrame);
            isGen = true;
            scopeEnvironment.setCurrentScope(new ScopeInfo("evalgen", ScopeKind.Generator, useFrame.getFrameDescriptor(), scopeEnvironment.getGlobalScope()));
        } else {
            if (mode == PythonParser.ParserMode.Deserialization && parserSSTResult instanceof FunctionDefSSTNode) {
                // we need to chek, if the deserialized function is not generator
                FunctionDefSSTNode fDef = (FunctionDefSSTNode) parserSSTResult;
                if (fDef.getScope().getScopeKind() == ScopeKind.Generator) {
                    isGen = true;
                }
            }
            scopeEnvironment.setCurrentScope(scopeEnvironment.getGlobalScope());
        }
        scopeEnvironment.setFreeVarsInRootScope(useFrame);
        FactorySSTVisitor factoryVisitor = new FactorySSTVisitor(errors, getScopeEnvironment(), errors.getContext().getLanguage().getNodeFactory(), source, mode == PythonParser.ParserMode.Statement);
        if (isGen) {
            factoryVisitor = new GeneratorFactorySSTVisitor(errors, getScopeEnvironment(), errors.getContext().getLanguage().getNodeFactory(), source, factoryVisitor);
        }
        if (mode == PythonParser.ParserMode.Deserialization) {
            result = parserSSTResult.accept(factoryVisitor);
        } else {
            ExpressionNode body = mode == PythonParser.ParserMode.Eval
                            ? (ExpressionNode) parserSSTResult.accept(factoryVisitor)
                            : parserSSTResult instanceof BlockSSTNode
                                            ? factoryVisitor.asExpression((BlockSSTNode) parserSSTResult)
                                            : FactorySSTVisitor.asExpression(parserSSTResult.accept(factoryVisitor));
            switch (mode) {
                case Eval:
                    scopeEnvironment.setCurrentScope(scopeEnvironment.getGlobalScope());
                    StatementNode evalReturn = new ReturnNode.FrameReturnNode(body, scopeEnvironment.getCurrentScope().getReturnSlot());
                    ReturnTargetNode returnTarget = new ReturnTargetNode(evalReturn, ReadLocalVariableNode.create(scopeEnvironment.getCurrentScope().getReturnSlot()));
                    ExecutionCellSlots executionCellSlots = scopeEnvironment.getExecutionCellSlots();
                    FunctionRootNode functionRoot = rootNodeFactory.createFunctionRoot(body.getSourceSection(), source.getName(), false, scopeEnvironment.getGlobalScope().createFrameDescriptor(),
                                    returnTarget,
                                    executionCellSlots, Signature.EMPTY, null);
                    result = functionRoot;
                    break;
                case File:
                    result = rootNodeFactory.createModuleRoot(source.getName(), getModuleDoc(body), body, scopeEnvironment.getGlobalScope().createFrameDescriptor(),
                                    scopeEnvironment.getGlobalScope().hasAnnotations());
                    ((ModuleRootNode) result).assignSourceSection(createSourceSection(0, source.getLength()));
                    break;
                case InlineEvaluation:
                    result = body;
                    break;
                case InteractiveStatement:
                    result = rootNodeFactory.createModuleRoot("<expression>", getModuleDoc(body), body, scopeEnvironment.getGlobalScope().createFrameDescriptor(),
                                    scopeEnvironment.getGlobalScope().hasAnnotations());
                    ((ModuleRootNode) result).assignSourceSection(createSourceSection(0, source.getLength()));
                    break;
                case Statement:
                    body.assignSourceSection(body.getSourceSection());
                    result = rootNodeFactory.createModuleRoot("<expression>", getModuleDoc(body), body, scopeEnvironment.getGlobalScope().createFrameDescriptor(),
                                    scopeEnvironment.getGlobalScope().hasAnnotations());
                    ((ModuleRootNode) result).assignSourceSection(createSourceSection(0, source.getLength()));
                    break;
                case WithArguments:
                    // find created function RootNode
                    final Node[] fromVisitor = new Node[1];
                    body.accept((Node node) -> {
                        if (node instanceof FunctionDefinitionNode) {
                            fromVisitor[0] = ((FunctionDefinitionNode) node).getFunctionRoot();
                            return false;
                        }
                        return true;
                    });
                    result = fromVisitor[0];
                    break;
                default:
                    throw new RuntimeException("unexpected mode: " + mode);
            }
        }
        if (result instanceof PRootNode) {
            ((PRootNode) result).setDeprecationWarnings(deprecationWarnings);
        }
        return result;
    }

    private static TruffleString getModuleDoc(ExpressionNode from) {
        StringLiteralNode sln = StringUtils.extractDoc(from);
        TruffleString doc = null;
        if (sln != null) {
            doc = sln.getValue();
        }
        return doc;
    }

    private SourceSection createSourceSection(int start, int stop) {
        if (source.getLength() > start && source.getLength() >= stop) {
            return source.createSection(start, stop - start);
        } else {
            return source.createUnavailableSection();
        }
    }

}
