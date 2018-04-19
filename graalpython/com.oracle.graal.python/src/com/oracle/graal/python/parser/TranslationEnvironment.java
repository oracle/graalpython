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

import static com.oracle.graal.python.nodes.SpecialAttributeNames.__CLASS__;
import static com.oracle.graal.python.nodes.frame.FrameSlotIDs.LIST_COMPREHENSION_SLOT_ID;
import static com.oracle.graal.python.nodes.frame.FrameSlotIDs.RETURN_SLOT_ID;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.antlr.v4.runtime.ParserRuleContext;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.nodes.NodeFactory;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.argument.ReadDefaultArgumentNode;
import com.oracle.graal.python.nodes.argument.ReadIndexedArgumentNode;
import com.oracle.graal.python.nodes.argument.ReadKeywordNode;
import com.oracle.graal.python.nodes.argument.ReadVarArgsNode;
import com.oracle.graal.python.nodes.argument.ReadVarKeywordsNode;
import com.oracle.graal.python.nodes.frame.ReadNode;
import com.oracle.graal.python.parser.ScopeInfo.ScopeKind;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameUtil;

public class TranslationEnvironment implements CellFrameSlotSupplier {

    private final NodeFactory factory;

    private Map<ParserRuleContext, ScopeInfo> scopeInfos;
    private ScopeInfo currentScope;
    private ScopeInfo globalScope;
    private int scopeLevel;

    private static final String TEMP_LOCAL_PREFIX = "<>temp_";
    private int listComprehensionSlotCounter = 0;

    public TranslationEnvironment(PythonLanguage language) {
        this.factory = language.getNodeFactory();
        scopeInfos = new HashMap<>();
    }

    public TranslationEnvironment reset() {
        scopeLevel = 0;
        listComprehensionSlotCounter = 0;
        return this;
    }

    public void beginScope(ParserRuleContext ctx, ScopeInfo.ScopeKind kind) {
        scopeLevel++;
        ScopeInfo info = scopeInfos.get(ctx);
        currentScope = info != null ? info : new ScopeInfo(TranslationUtil.getScopeId(ctx, kind), kind, new FrameDescriptor(), currentScope);

        if (globalScope == null) {
            globalScope = currentScope;
        }
    }

    public void beginScope(ParserRuleContext ctx, ScopeInfo.ScopeKind kind, FrameDescriptor fd) {
        scopeLevel++;
        ScopeInfo info = scopeInfos.get(ctx);
        currentScope = info != null ? info : new ScopeInfo(TranslationUtil.getScopeId(ctx, kind), kind, fd, currentScope);

        if (globalScope == null) {
            globalScope = currentScope;
        }
    }

    public void endScope(ParserRuleContext ctx) {
        scopeLevel--;
        scopeInfos.put(ctx, currentScope);
        currentScope = currentScope.getParent();
    }

    public boolean atModuleLevel() {
        assert scopeLevel > 0;
        return scopeLevel == 1;
    }

    public boolean atNonModuleLevel() {
        assert scopeLevel > 0;
        return scopeLevel > 1;
    }

    public ScopeInfo.ScopeKind getScopeKind() {
        return currentScope.getScopeKind();
    }

    public void setToGeneratorScope() {
        currentScope.setAsGenerator();
    }

    public boolean isInModuleScope() {
        return getScopeKind() == ScopeInfo.ScopeKind.Module;
    }

    public boolean isInFunctionScope() {
        return getScopeKind() == ScopeInfo.ScopeKind.Function || getScopeKind() == ScopeInfo.ScopeKind.Generator;
    }

    public boolean isInClassScope() {
        return getScopeKind() == ScopeInfo.ScopeKind.Class;
    }

    public boolean isInGeneratorScope() {
        return getScopeKind() == ScopeInfo.ScopeKind.Generator;
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
        return currentScope.getFrameDescriptor().findOrAddFrameSlot(name);
    }

    private boolean isCellInCurrentScope(String name) {
        return currentScope.isFreeVar(name) || currentScope.isCellVar(name);
    }

    private FrameSlot findSlotInCurrentScope(String name) {
        assert name != null : "name is null!";
        return currentScope.getFrameDescriptor().findFrameSlot(name);
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

    private PNode getWriteNode(String name, FrameSlot slot, PNode right) {
        if (isCellInCurrentScope(name)) {
            return factory.createWriteLocalCell(right, slot);
        }
        return factory.createWriteLocal(right, slot);
    }

    private PNode getWriteNode(String name, Function<FrameSlot, PNode> getReadNode) {
        FrameSlot slot = findSlotInCurrentScope(name);
        PNode right = getReadNode.apply(slot);

        return getWriteNode(name, slot, right);
    }

    public PNode getWriteArgumentToLocal(String name) {
        return getWriteNode(name, slot -> ReadIndexedArgumentNode.create(slot.getIndex()));
    }

    public PNode getWriteKeywordArgumentToLocal(String name, ReadDefaultArgumentNode readDefaultArgumentNode) {
        return getWriteNode(name, slot -> ReadKeywordNode.create(name, slot.getIndex(), readDefaultArgumentNode));
    }

    public PNode getWriteRequiredKeywordArgumentToLocal(String name) {
        return getWriteNode(name, slot -> ReadKeywordNode.create(name));
    }

    public PNode getWriteRequiredKeywordArgumentToLocal(String name, ReadDefaultArgumentNode readDefaultArgumentNode) {
        return getWriteNode(name, slot -> ReadKeywordNode.create(name, readDefaultArgumentNode));
    }

    public PNode getWriteVarArgsToLocal(String name) {
        return getWriteNode(name, slot -> ReadVarArgsNode.create(slot.getIndex()));
    }

    public PNode getWriteKwArgsToLocal(String name, String[] names) {
        return getWriteNode(name, slot -> ReadVarKeywordsNode.createForUserFunction(names));
    }

    public void registerCellVariable(String identifier) {
        if (currentScope != globalScope && findSlotInCurrentScope(identifier) == null) {
            // symbol frameslot not found in current scope => free variable in current scope
            ScopeInfo definitionScope = findVariableScope(currentScope, identifier);
            if (definitionScope != null && definitionScope != globalScope) {
                definitionScope.addCellVar(identifier);
                // register it as a free variable in all parent scopes up until the defining scope
                // (except it)
                ScopeInfo scope = currentScope;
                while (scope != definitionScope) {
                    scope.addFreeVar(identifier);
                    scope = scope.getParent();
                }
            }
        }
    }

    private ScopeInfo getRootScope() {
        ScopeInfo scope = scopeInfos.values().iterator().next();
        while (scope.getParent() != null) {
            scope = scope.getParent();
        }
        return scope;
    }

    public void setFreeVarsInRootScope(Frame frame) {
        ScopeInfo rootScope = getRootScope();
        for (Object identifier : frame.getFrameDescriptor().getIdentifiers()) {
            FrameSlot frameSlot = frame.getFrameDescriptor().findFrameSlot(identifier);
            if (frameSlot != null && frame.isObject(frameSlot)) {
                Object value = FrameUtil.getObjectSafe(frame, frameSlot);
                if (value instanceof PCell) {
                    rootScope.addFreeVar((String) frameSlot.getIdentifier(), false);
                }
            }
        }
    }

    private ScopeInfo findVariableScope(ScopeInfo enclosingScope, String identifier) {
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
        FrameSlot slot = findSlotInCurrentScope(name);
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
            cellSlot = findSlotInCurrentScope(name);
        }
        return (ReadNode) factory.createReadClassAttributeNode(name, cellSlot, currentScope.isFreeVar(name));
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
                return findVariableInGlobalOrBuiltinScope(name);
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
        globalScope.getFrameDescriptor().findOrAddFrameSlot(name);
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

    protected void setDefaultArgumentNodes(List<PNode> defaultArgs) {
        currentScope.setDefaultArgumentNodes(defaultArgs);
    }

    protected List<PNode> getDefaultArgumentNodes() {
        List<PNode> defaultArgs = currentScope.getDefaultArgumentNodes();
        return defaultArgs;
    }

    protected boolean hasDefaultArguments() {
        return currentScope.getDefaultArgumentNodes() != null && currentScope.getDefaultArgumentNodes().size() > 0;
    }

    protected void setDefaultArgumentReads(ReadDefaultArgumentNode[] defaultReads) {
        currentScope.setDefaultArgumentReads(defaultReads);
    }

    protected ReadDefaultArgumentNode[] getDefaultArgumentReads() {
        return currentScope.getDefaultArgumentReads();
    }

    public FrameSlot getReturnSlot() {
        return currentScope.getFrameDescriptor().findOrAddFrameSlot(RETURN_SLOT_ID);
    }

    public FrameSlot nextListComprehensionSlot() {
        listComprehensionSlotCounter++;
        return getListComprehensionSlot();
    }

    public FrameSlot getListComprehensionSlot() {
        return currentScope.getFrameDescriptor().findOrAddFrameSlot(LIST_COMPREHENSION_SLOT_ID + listComprehensionSlotCounter);
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
            for (ScopeInfo childScope : classScope.getChildScopes()) {
                if (childScope.getScopeKind() == ScopeKind.Function) {
                    childScope.addFreeVar(__CLASS__, true);
                }
            }
        }
    }

    public void createFrameSlotsForCellAndFreeVars() {
        for (ScopeInfo scope : scopeInfos.values()) {
            scope.createFrameSlotsForCellAndFreeVars();
        }
    }
}
