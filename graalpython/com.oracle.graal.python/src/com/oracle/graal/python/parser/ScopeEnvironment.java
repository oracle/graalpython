/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.nodes.NodeFactory;
import com.oracle.graal.python.nodes.PNode;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__CLASS__;
import com.oracle.graal.python.nodes.argument.ReadArgumentNode;
import com.oracle.graal.python.nodes.argument.ReadIndexedArgumentNode;
import com.oracle.graal.python.nodes.argument.ReadVarArgsNode;
import com.oracle.graal.python.nodes.argument.ReadVarKeywordsNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import static com.oracle.graal.python.nodes.frame.FrameSlotIDs.RETURN_SLOT_ID;
import com.oracle.graal.python.nodes.frame.ReadNode;
import com.oracle.graal.python.nodes.generator.ReadGeneratorFrameVariableNode;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import org.antlr.v4.runtime.ParserRuleContext;

public class ScopeEnvironment implements CellFrameSlotSupplier {

    private final NodeFactory factory;

    private ScopeInfo currentScope;
    private ScopeInfo globalScope;
    
    private final HashMap<String, List<ScopeInfo>> unresolvedVars = new HashMap<>();

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
        currentScope = newScope;
        if (globalScope == null) {
            globalScope = currentScope;
        }
        return currentScope;
    }
    
    public ScopeInfo pushScope(String name, ScopeInfo.ScopeKind kind, FrameDescriptor frameDescriptor) {
        ScopeInfo newScope = new ScopeInfo(name, kind, frameDescriptor, currentScope);
        currentScope = newScope;
        if (globalScope == null) {
            globalScope = currentScope;
        }
        return currentScope;
    }

    public ScopeInfo popScope() {
        ScopeInfo definingScope = currentScope;
        Set<Object> identifiers = definingScope.getFrameDescriptor().getIdentifiers();
        Set<String> localySeenVars = definingScope.getSeenVars();
        if (localySeenVars != null || !unresolvedVars.isEmpty()) {
            for (Object identifier : identifiers) {
                String name = (String)identifier;
                
                if (localySeenVars != null) {
                    // remove the localy declared variable
                    localySeenVars.remove(name);
                }

                List<ScopeInfo> usedInScopes = unresolvedVars.remove(name);
                // was the declared varibale seen before?
                if (usedInScopes != null) { 
                    // make the varible freevar and cellvar in scopes, where is used
                    for (ScopeInfo scope : usedInScopes) {
                        scope.addFreeVar(name, true);
                        definingScope.addCellVar(name);
                        scope = scope.getParent();
                        while(scope != definingScope && scope.findFrameSlot(name) == null) {
                            scope.addFreeVar(name, true);
                            scope = scope.getParent();
                        }
                    }
                }
            }
        }
            
        // are in current scope used variables that are not defined
        if ((localySeenVars != null && !localySeenVars.isEmpty()) && definingScope.getScopeKind() != ScopeInfo.ScopeKind.Module) {
            // note this scope in global unresolved vars
            List<ScopeInfo> usedInScopes;
            for (String varName: localySeenVars) {
                if (!isGlobal(varName)) {
                    usedInScopes = unresolvedVars.get(varName);
                    if (usedInScopes == null) {
                        usedInScopes = new ArrayList<>();
                        unresolvedVars.put(varName, usedInScopes);
                    }
                    usedInScopes.add(definingScope);
                }
            }
            // clean the variables
            localySeenVars.clear();
        }
        currentScope = currentScope.getParent();
        return definingScope;
    }
    
    public void addSeenVar(String name) {
        currentScope.addSeenVar(name);
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

    public boolean isGlobaScope(ScopeInfo scope) {
        return globalScope == scope;
    }
    
    public ScopeInfo getGlobalScope() {
        return globalScope;
    }
    
    public boolean isNonlocal(String name) {
        assert name != null : "name is null!";
        return currentScope.isExplicitNonlocalVariable(name);
    }

    public FrameSlot createAndReturnLocal(String name) {
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

    public ReadNode findVariableNodeModule(String name) {
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
     
    private ReadNode findVariableNodeInGenerator(String name) {
        
        FrameSlot slot = currentScope.findFrameSlot(name);
        if (slot != null && !isCellInCurrentScope(name)) {
            // is local in generater?
            return ReadGeneratorFrameVariableNode.create(slot);
        }
        return findVariableNodeLEGB(name);
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

    public PNode getReadNode(String name, FrameSlot slot) {
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
                return findVariableNodeInGenerator(name);
            case ListComp:
            case Function:
                return findVariableNodeLEGB(name);
            case Class:
                return findVariableNodeClass(name);
            default:
                throw new IllegalStateException("Unexpected scopeKind " + getScopeKind());
        }
    }
    
    public ReadNode findVariable(String name, ScopeInfo scope) {
        ScopeInfo oldCurrent = currentScope;
        currentScope = scope;
        ReadNode result = findVariable(name);
        currentScope = oldCurrent;
        return result;
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
    
    public ScopeInfo setCurrentScope(ScopeInfo info) {
        ScopeInfo oldCurrent = currentScope;
        currentScope = info;
        return oldCurrent;
    } 
    
    public void setToGeneratorScope() {
        currentScope.setAsGenerator();
    }
}
