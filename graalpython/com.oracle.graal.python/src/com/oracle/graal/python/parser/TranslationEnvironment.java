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

import static com.oracle.graal.python.nodes.SpecialAttributeNames.__CLASS__;
import static com.oracle.graal.python.nodes.frame.FrameSlotIDs.RETURN_SLOT_ID;
import static com.oracle.graal.python.nodes.frame.FrameSlotIDs.TEMP_LOCAL_PREFIX;

import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.nodes.NodeFactory;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.argument.ReadArgumentNode;
import com.oracle.graal.python.nodes.argument.ReadIndexedArgumentNode;
import com.oracle.graal.python.nodes.argument.ReadVarArgsNode;
import com.oracle.graal.python.nodes.argument.ReadVarKeywordsNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.frame.ReadNode;
import com.oracle.graal.python.nodes.function.FunctionDefinitionNode.KwDefaultExpressionNode;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.graal.python.parser.ScopeInfo.ScopeKind;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;

public final class TranslationEnvironment implements CellFrameSlotSupplier {

    private final NodeFactory factory;

    private ScopeInfo currentScope;
    private ScopeInfo globalScope;

    public TranslationEnvironment(PythonLanguage language) {
        this.factory = language.getNodeFactory();
    }

    public static TranslationEnvironment createFromScope(ScopeInfo scope) {
        TranslationEnvironment environment = new TranslationEnvironment(PythonLanguage.getCurrent());
        environment.currentScope = scope;
        ScopeInfo global = scope;
        while (global.getParent() != null) {
            global = global.getParent();
        }
        environment.globalScope = global;
        return environment;
    }

    public ScopeInfo pushScope(ParserRuleContext ctx, ScopeInfo.ScopeKind kind) {
        return pushScope(ctx, kind, null);
    }

    public ScopeInfo pushScope(ParserRuleContext ctx, ScopeInfo.ScopeKind kind, FrameDescriptor frameDescriptor) {
        ScopeInfo newScope = new ScopeInfo(TranslationUtil.getScopeId(ctx, kind), kind, frameDescriptor, currentScope);
        pushScope(newScope);
        if (globalScope == null) {
            globalScope = currentScope;
        }
        return currentScope;
    }

    public void pushScope(ScopeInfo scope) {
        assert scope != null;
        currentScope = scope;
    }

    public ScopeInfo popScope() {
        ScopeInfo old = currentScope;
        currentScope = currentScope.getParent();
        return old;
    }

    public boolean atModuleLevel() {
        assert currentScope != null;
        return currentScope == globalScope;
    }

    public boolean atNonModuleLevel() {
        assert currentScope != null;
        return currentScope != globalScope;
    }

    public ScopeInfo.ScopeKind getScopeKind() {
        return currentScope.getScopeKind();
    }

    public void setToGeneratorScope() {
        currentScope.setAsGenerator();
    }

    public boolean isInModuleScope() {
        return getScopeKind() == ScopeKind.Module;
    }

    public boolean isInFunctionScope() {
        return getScopeKind() == ScopeKind.Function || getScopeKind() == ScopeKind.Generator;
    }

    public boolean isInClassScope() {
        return getScopeKind() == ScopeKind.Class;
    }

    public boolean isInGeneratorScope() {
        return getScopeKind() == ScopeKind.Generator;
    }

    public boolean isInListComprehensionScope() {
        return getScopeKind() == ScopeKind.ListComp;
    }

    public String getCurrentScopeId() {
        return currentScope.getScopeId();
    }

    public FrameDescriptor getCurrentFrame() {
        FrameDescriptor frameDescriptor = currentScope.getFrameDescriptor();
        assert frameDescriptor != null;
        return frameDescriptor;
    }

    public FrameDescriptor getEnclosingFrame() {
        FrameDescriptor frameDescriptor = currentScope.getParent().getFrameDescriptor();
        assert frameDescriptor != null;
        return frameDescriptor;
    }

    public void createLocal(String name) {
        assert name != null : "name is null!";
        if (currentScope.getScopeKind() == ScopeKind.Module) {
            return;
        }
        if (isGlobal(name)) {
            return;
        }
        if (isNonlocal(name)) {
            return;
        }
        createAndReturnLocal(name);
    }

    private FrameSlot createAndReturnLocal(String name) {
        return currentScope.createSlotIfNotPresent(name);
    }

    private boolean isCellInCurrentScope(String name) {
        return currentScope.isFreeVar(name) || currentScope.isCellVar(name);
    }

    @Override
    public FrameSlot[] getCellVarSlots() {
        return currentScope.getCellVarSlots();
    }

    @Override
    public FrameSlot[] getFreeVarSlots() {
        return currentScope.getFreeVarSlots();
    }

    @Override
    public FrameSlot[] getFreeVarDefinitionSlots() {
        return currentScope.getFreeVarSlotsInParentScope();
    }

    public ExecutionCellSlots getExecutionCellSlots() {
        return new ExecutionCellSlots(this);
    }

    public DefinitionCellSlots getDefinitionCellSlots() {
        return new DefinitionCellSlots(this);
    }

    private PNode getReadNode(String name, FrameSlot slot) {
        if (isCellInCurrentScope(name)) {
            return factory.createReadLocalCell(slot, currentScope.isFreeVar(name));
        }
        return factory.createReadLocal(slot);
    }

    private StatementNode getWriteNode(String name, FrameSlot slot, ExpressionNode right) {
        if (isCellInCurrentScope(name)) {
            return factory.createWriteLocalCell(right, slot);
        }
        return factory.createWriteLocal(right, slot);
    }

    private StatementNode getWriteNode(String name, ReadArgumentNode readNode) {
        ExpressionNode right = readNode.asExpression();
        return getWriteNode(name, currentScope.findFrameSlot(name), right);
    }

    public StatementNode getWriteArgumentToLocal(String name, int index) {
        return getWriteNode(name, ReadIndexedArgumentNode.create(index));
    }

    public StatementNode getWriteVarArgsToLocal(String name, int index) {
        return getWriteNode(name, ReadVarArgsNode.create(index));
    }

    public StatementNode getWriteKwArgsToLocal(String name, String[] names) {
        return getWriteNode(name, ReadVarKeywordsNode.createForUserFunction(names));
    }

    static ScopeInfo findVariableScope(ScopeInfo enclosingScope, String identifier) {
        ScopeInfo parentScope = enclosingScope.getParent();
        if (parentScope != null) {
            FrameSlot slot = parentScope.findFrameSlot(identifier);
            // the class body is NOT an enclosing scope for methods defined within the class
            if (slot != null) {
                if (parentScope.getScopeKind() != ScopeKind.Class || enclosingScope.getScopeKind() != ScopeKind.Function) {
                    return parentScope;
                }
            }
            return findVariableScope(parentScope, identifier);
        }
        return null;
    }

    public boolean isVariableInEnclosingScopes(String identifier) {
        return findVariableScope(currentScope, identifier) != null;
    }

    private ReadNode findVariableInLocalOrEnclosingScopes(String name) {
        FrameSlot slot = currentScope.findFrameSlot(name);
        if (slot != null) {
            return (ReadNode) getReadNode(name, slot);
        }
        return null;
    }

    private ReadNode findVariableNodeLEGB(String name) {
        // 1 (local scope) & 2 (enclosing scope(s))
        ReadNode readNode = findVariableInLocalOrEnclosingScopes(name);
        if (readNode != null) {
            return readNode;
        }

        // 3 (global scope) & 4 (builtin)
        return findVariableInGlobalOrBuiltinScope(name);
    }

    private ReadNode findVariableNodeClass(String name) {
        FrameSlot cellSlot = null;
        if (isCellInCurrentScope(name)) {
            cellSlot = currentScope.findFrameSlot(name);
        }
        if (name.equals(__CLASS__)) {
            return (ReadNode) factory.createReadClassAttributeNode(name, null, currentScope.isFreeVar(name));
        }
        return (ReadNode) factory.createReadClassAttributeNode(name, cellSlot, currentScope.isFreeVar(name));
    }

    private ReadNode findVariableNodeModule(String name) {
        if (currentScope.isFreeVar(name)) {
            // this is covering the special eval case where free vars pass through to the eval
            // module scope
            FrameSlot cellSlot = currentScope.findFrameSlot(name);
            return (ReadNode) factory.createReadLocalCell(cellSlot, true);
        }
        return factory.createLoadName(name);
    }

    private ReadNode findVariableInGlobalOrBuiltinScope(String name) {
        return (ReadNode) factory.createReadGlobalOrBuiltinScope(name);
    }

    public ReadNode findVariable(String name) {
        assert name != null : "name is null!";

        if (isGlobal(name)) {
            return findVariableInGlobalOrBuiltinScope(name);
        } else if (isNonlocal(name)) {
            return findVariableInLocalOrEnclosingScopes(name);
        }

        switch (getScopeKind()) {
            case Module:
                return findVariableNodeModule(name);
            case Generator:
            case ListComp:
            case Function:
                return findVariableNodeLEGB(name);
            case Class:
                return findVariableNodeClass(name);
            default:
                throw new IllegalStateException("Unexpected scopeKind " + getScopeKind());
        }
    }

    public ReadNode makeTempLocalVariable() {
        String tempName = TEMP_LOCAL_PREFIX + currentScope.getFrameDescriptor().getSize();
        FrameSlot tempSlot = createAndReturnLocal(tempName);
        return (ReadNode) factory.createReadLocal(tempSlot);
    }

    public static FrameSlot makeTempLocalVariable(FrameDescriptor frameDescriptor) {
        String tempName = TEMP_LOCAL_PREFIX + frameDescriptor.getSize();
        return frameDescriptor.findOrAddFrameSlot(tempName);
    }

    private void createGlobal(String name) {
        assert name != null : "name is null!";
        globalScope.createSlotIfNotPresent(name);
    }

    public void addLocalGlobals(String name) {
        assert name != null : "name is null!";
        createGlobal(name);
        currentScope.addExplicitGlobalVariable(name);
    }

    public void addNonlocal(String name) {
        assert name != null : "name is null!";
        currentScope.addExplicitNonlocalVariable(name);
    }

    public boolean isGlobal(String name) {
        assert name != null : "name is null!";
        return currentScope.isExplicitGlobalVariable(name);
    }

    public boolean isNonlocal(String name) {
        assert name != null : "name is null!";
        return currentScope.isExplicitNonlocalVariable(name);
    }

    public int getCurrentFrameSize() {
        return currentScope.getFrameDescriptor().getSize();
    }

    protected void setDefaultArgumentNodes(List<ExpressionNode> defaultArgs) {
        currentScope.setDefaultArgumentNodes(defaultArgs);
    }

    protected void setDefaultKwArgumentNodes(List<KwDefaultExpressionNode> defaultArgs) {
        currentScope.setDefaultKwArgumentNodes(defaultArgs);
    }

    protected List<ExpressionNode> getDefaultArgumentNodes() {
        return currentScope.getDefaultArgumentNodes();
    }

    protected List<KwDefaultExpressionNode> getDefaultKwArgumentNodes() {
        return currentScope.getDefaultKwArgumentNodes();
    }

    protected boolean hasDefaultArguments() {
        return currentScope.getDefaultArgumentNodes() != null && currentScope.getDefaultArgumentNodes().size() > 0;
    }

    protected boolean hasDefaultKwArguments() {
        return currentScope.getDefaultKwArgumentNodes() != null && currentScope.getDefaultKwArgumentNodes().size() > 0;
    }

    public FrameSlot getReturnSlot() {
        return currentScope.createSlotIfNotPresent(RETURN_SLOT_ID);
    }

    private ScopeInfo findEnclosingClassScope() {
        ScopeInfo scope = currentScope;
        while (scope != globalScope) {
            if (scope.getScopeKind() == ScopeKind.Class) {
                return scope;
            }
            scope = scope.getParent();
        }
        return null;
    }

    public void registerSpecialClassCellVar() {
        ScopeInfo classScope = findEnclosingClassScope();
        if (classScope != null) {
            // 1) create a cellvar in the class body (__class__), the class itself is stored here
            classScope.addCellVar(__CLASS__, true);
            // 2) all class methods receive a __class__ freevar
            ScopeInfo childScope = classScope.getFirstChildScope();
            while (childScope != null) {
                if (childScope.getScopeKind() == ScopeKind.Function) {
                    childScope.addFreeVar(__CLASS__, true);
                }
                childScope = childScope.getNextChildScope();
            }
        }
    }

    public FrameSlot findFrameSlot(String identifier) {
        return currentScope.findFrameSlot(identifier);
    }

    public ScopeInfo getCurrentScope() {
        return currentScope;
    }

    public ScopeInfo getGlobalScope() {
        return globalScope;
    }
}
