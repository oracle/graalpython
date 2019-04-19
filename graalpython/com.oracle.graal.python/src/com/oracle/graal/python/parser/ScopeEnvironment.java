/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.graal.python.parser;

import com.oracle.graal.python.nodes.NodeFactory;
import com.oracle.graal.python.nodes.PNode;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__CLASS__;
import static com.oracle.graal.python.nodes.frame.FrameSlotIDs.RETURN_SLOT_ID;
import com.oracle.graal.python.nodes.frame.ReadNode;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import org.antlr.v4.runtime.ParserRuleContext;

public class ScopeEnvironment implements CellFrameSlotSupplier {

    private final NodeFactory factory;

    private ScopeInfo currentScope;
    private ScopeInfo globalScope;

    public ScopeEnvironment(NodeFactory factory) {
        this.factory = factory;
    }

    public ScopeInfo getCurrentScope() {
        return currentScope;
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

    private boolean isCellInCurrentScope(String name) {
        return currentScope.isFreeVar(name) || currentScope.isCellVar(name);
    }
    
    public boolean isInGeneratorScope() {
        return getScopeKind() == ScopeInfo.ScopeKind.Generator;
    }

    public boolean isGlobal(String name) {
        assert name != null : "name is null!";
        return currentScope.isExplicitGlobalVariable(name);
    }

    public boolean isNonlocal(String name) {
        assert name != null : "name is null!";
        return currentScope.isExplicitNonlocalVariable(name);
    }

    private FrameSlot createAndReturnLocal(String name) {
        return currentScope.createSlotIfNotPresent(name);
    }

    public FrameSlot getReturnSlot() {
        return currentScope.createSlotIfNotPresent(RETURN_SLOT_ID);
    }
    
    public FrameDescriptor getCurrentFrame() {
        FrameDescriptor frameDescriptor = currentScope.getFrameDescriptor();
        assert frameDescriptor != null;
        return frameDescriptor;
    }
    
    public ExecutionCellSlots getExecutionCellSlots() {
        return new ExecutionCellSlots(this);
    }
    
    public DefinitionCellSlots getDefinitionCellSlots() {
        return new DefinitionCellSlots(this);
    }
    
    public void createLocal(String name) {
        assert name != null : "name is null!";
        if (currentScope.getScopeKind() == ScopeInfo.ScopeKind.Module) {
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

    private PNode getReadNode(String name, FrameSlot slot) {
        if (isCellInCurrentScope(name)) {
            return factory.createReadLocalCell(slot, currentScope.isFreeVar(name));
        }
        return factory.createReadLocal(slot);
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
    
}
