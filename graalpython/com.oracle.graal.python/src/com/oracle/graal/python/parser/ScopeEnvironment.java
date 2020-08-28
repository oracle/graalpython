/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.nodes.SpecialAttributeNames.__CLASS__;
import static com.oracle.graal.python.nodes.frame.FrameSlotIDs.RETURN_SLOT_ID;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.nodes.NodeFactory;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.argument.ReadArgumentNode;
import com.oracle.graal.python.nodes.argument.ReadIndexedArgumentNode;
import com.oracle.graal.python.nodes.argument.ReadVarArgsNode;
import com.oracle.graal.python.nodes.argument.ReadVarKeywordsNode;
import com.oracle.graal.python.nodes.cell.ReadLocalCellNode;
import com.oracle.graal.python.nodes.cell.WriteLocalCellNode;
import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.frame.FrameSlotIDs;
import com.oracle.graal.python.nodes.frame.ReadNode;
import com.oracle.graal.python.nodes.generator.ReadGeneratorFrameVariableNode;
import com.oracle.graal.python.nodes.generator.WriteGeneratorFrameVariableNode;
import com.oracle.graal.python.nodes.statement.StatementNode;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameUtil;

public class ScopeEnvironment implements CellFrameSlotSupplier {

    public static final String CLASS_VAR_PREFIX = "<>class";
    public static final String LAMBDA_NAME = "<lambda>";
    public static final String GENEXPR_NAME = "<genexpr>";
    public static int CLASS_VAR_PREFIX_IDX = CLASS_VAR_PREFIX.length();

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

    public ScopeInfo pushScope(String name, ScopeInfo.ScopeKind kind) {
        if (kind == ScopeInfo.ScopeKind.Function && !name.equals(LAMBDA_NAME)) {
            createLocal(getCurrentScope().getScopeKind() == ScopeInfo.ScopeKind.Class
                            ? ScopeEnvironment.CLASS_VAR_PREFIX + name
                            : name);
        }
        return pushScope(name, kind, null);
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
        ScopeInfo.ScopeKind definingScopeKind = definingScope.getScopeKind();
        if (localySeenVars != null || !unresolvedVars.isEmpty()) {
            for (Object identifier : identifiers) {
                String name = identifier instanceof String ? (String) identifier : identifier.toString();

                if (localySeenVars != null) {
                    // remove the locally declared variable
                    if (definingScopeKind == ScopeInfo.ScopeKind.Class && name.startsWith(CLASS_VAR_PREFIX)) {
                        localySeenVars.remove(name.substring(CLASS_VAR_PREFIX_IDX));
                    } else {
                        localySeenVars.remove(name);
                    }
                }

                List<ScopeInfo> usedInScopes = unresolvedVars.get(name);
                // was the declared variable seen before?
                if (usedInScopes != null && !(definingScopeKind == ScopeInfo.ScopeKind.Module && definingScope.findFrameSlot(name) != null)) {
                    createCellAndFreeVars(usedInScopes, definingScope, name);
                }
            }
        }

        if (definingScopeKind == ScopeInfo.ScopeKind.Class) {
            if (unresolvedVars.containsKey(__CLASS__)) {
                // in the class scope the __class__ doesn't exist yet, but has to be treated
                // as it is defined in enclosing class
                String name = __CLASS__;
                List<ScopeInfo> usedInScopes = unresolvedVars.get(name);
                createCellAndFreeVars(usedInScopes, definingScope, name);
            }
            if (unresolvedVars.containsKey("super")) {
                // when super is used in some inner socpe of class scope, then
                // implicit __class__ has to be created.
                List<ScopeInfo> usedInScopes = unresolvedVars.get("super");
                List<ScopeInfo> copy = new ArrayList<>(usedInScopes);
                for (ScopeInfo scope : copy) {
                    if (scope != null) {
                        // we need to find out, whether the scope is a under the defining scope
                        ScopeInfo tmpScope = scope;
                        ScopeInfo parentDefiningScope = definingScope.getParent();
                        while (tmpScope != null && tmpScope != definingScope && tmpScope != parentDefiningScope) {
                            tmpScope = tmpScope.getParent();
                        }
                        if (definingScope == tmpScope) {
                            usedInScopes.remove(scope);
                            scope.addFreeVar(__CLASS__, true);
                            definingScope.addCellVar(__CLASS__, true);
                            scope = scope.getParent();
                            while (scope != null && scope != definingScope && (scope.findFrameSlot(__CLASS__) == null || !scope.isFreeVar(__CLASS__))) {
                                scope.addFreeVar(__CLASS__, true);
                                scope = scope.getParent();
                            }
                        }
                    }
                }
                if (usedInScopes.isEmpty()) {
                    unresolvedVars.remove("super");
                }
            }
        }

        // are in current scope used variables that are not defined
        if ((localySeenVars != null && !localySeenVars.isEmpty()) && definingScopeKind != ScopeInfo.ScopeKind.Module) {
            // note this scope in global unresolved vars
            List<ScopeInfo> usedInScopes;
            for (String varName : localySeenVars) {
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

        if (definingScopeKind == ScopeInfo.ScopeKind.Class) {
            boolean copy = false;
            for (Object identifier : identifiers) {
                String name = (String) identifier;
                if (name.startsWith(CLASS_VAR_PREFIX)) {
                    definingScope.getFrameDescriptor().removeFrameSlot(identifier);
                    name = name.substring(CLASS_VAR_PREFIX_IDX);
                    definingScope.createSlotIfNotPresent(name);
                    copy = true;
                }
            }
            if (copy) {
                // we copy it because the indexes are now wrong due the issue GR-17984
                definingScope.setFrameDescriptor(definingScope.getFrameDescriptor().copy());
            }
        } else {
            if (definingScope.hasExplicitGlobalVariables()) {
                Set<String> globalsVariables = definingScope.getExplicitGlobalVariables();
                List<ScopeInfo> usedInScopes;
                for (String varName : globalsVariables) {
                    usedInScopes = unresolvedVars.get(varName);
                    // In inner scopes, where a variable is just read and is marked as global in
                    // some outer scope, has to be read as global as well. But if in the inner scope
                    // it is written into to variabl, then it's local one. See
                    // test_global_statement.py.
                    if (usedInScopes != null) {
                        List<ScopeInfo> copy = new ArrayList<>(usedInScopes);
                        for (ScopeInfo scope : copy) {
                            ScopeInfo tmpScope = scope;
                            ScopeInfo parentDefiningScope = definingScope.getParent();
                            while (tmpScope != null && tmpScope != definingScope && tmpScope != parentDefiningScope) {
                                tmpScope = tmpScope.getParent();
                            }
                            if (definingScope == tmpScope) {
                                usedInScopes.remove(scope);
                            }
                        }
                    }
                }
            }
        }
        currentScope = currentScope.getParent();
        return definingScope;
    }

    private void createCellAndFreeVars(List<ScopeInfo> usedInScopes, ScopeInfo definingScope, String name) {
        // make the variable freevar and cellvar in scopes, where is used
        List<ScopeInfo> copy = new ArrayList<>(usedInScopes);
        for (ScopeInfo scope : copy) {
            if (scope != null) {
                // we need to find out, whether the scope is a under the defining scope
                ScopeInfo tmpScope = scope;
                ScopeInfo parentDefiningScope = definingScope.getParent();
                while (tmpScope != null && tmpScope != definingScope && tmpScope != parentDefiningScope) {
                    tmpScope = tmpScope.getParent();
                }
                if (definingScope == tmpScope) {
                    usedInScopes.remove(scope);
                    scope.addFreeVar(name, true);
                    definingScope.addCellVar(name, true);
                    scope = scope.getParent();
                    while (scope != null && scope != definingScope && (scope.findFrameSlot(name) == null || !scope.isFreeVar(name))) {
                        scope.addFreeVar(name, true);
                        scope = scope.getParent();
                    }
                }
            }
        }
        if (usedInScopes.isEmpty()) {
            unresolvedVars.remove(name);
        }
    }

    public void addSeenVar(String name) {
        currentScope.addSeenVar(name);
    }

    public boolean atModuleLevel() {
        assert currentScope != null;
        return currentScope == globalScope;
    }

    public ScopeInfo.ScopeKind getScopeKind() {
        return currentScope.getScopeKind();
    }

    private boolean isCellInCurrentScope(String name) {
        return currentScope.isFreeVar(name) || currentScope.isCellVar(name);
    }

    public boolean isInFunctionScope() {
        ScopeInfo.ScopeKind kind = getScopeKind();
        return kind == ScopeInfo.ScopeKind.Function || kind == ScopeInfo.ScopeKind.Generator || kind == ScopeInfo.ScopeKind.DictComp || kind == ScopeInfo.ScopeKind.GenExp ||
                        kind == ScopeInfo.ScopeKind.ListComp || kind == ScopeInfo.ScopeKind.SetComp;
    }

    public boolean isInGeneratorScope() {
        ScopeInfo.ScopeKind kind = getScopeKind();
        return kind == ScopeInfo.ScopeKind.Generator || kind == ScopeInfo.ScopeKind.DictComp || kind == ScopeInfo.ScopeKind.GenExp || kind == ScopeInfo.ScopeKind.ListComp ||
                        kind == ScopeInfo.ScopeKind.SetComp;
    }

    public boolean isGlobal(String name) {
        assert name != null : "name is null!";
        return currentScope.isExplicitGlobalVariable(name);
    }

    public ScopeInfo getGlobalScope() {
        return globalScope;
    }

    public void setGlobalScope(ScopeInfo info) {
        globalScope = info;
        currentScope = info;
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
        } else if (slot != null && isCellInCurrentScope(name)) {
            return ReadLocalCellNode.create(slot, currentScope.isFreeVar(name), ReadGeneratorFrameVariableNode.create(slot));
        }

        return findVariableNodeLEGB(name);
    }

    private ReadNode findVariableNodeClass(String name) {
        FrameSlot cellSlot = null;
        if (name.equals(__CLASS__)) {
            boolean isFreeVar = currentScope.isFreeVar(name);
            if (isFreeVar) {
                // If __class__ is freevar in the class scope, then is stored in frameslot with
                // different name.
                // This is preventing corner situation, when body of class has two variables with
                // the same name __class__. The first one is __class__ freevar comming from outer
                // scope
                // and the second one is __class__ (implicit) closure for inner methods,
                // where __class__ or super is used. Both of them can have different values.
                cellSlot = currentScope.findFrameSlot(FrameSlotIDs.FREEVAR__CLASS__);
                return (ReadNode) factory.createReadClassAttributeNode(name, cellSlot, isFreeVar);
            }
            return (ReadNode) factory.createReadClassAttributeNode(name, null, isFreeVar);
        }
        if (isCellInCurrentScope(name)) {
            cellSlot = currentScope.findFrameSlot(name);
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
            if (isInGeneratorScope()) {
                return findVariableNodeInGenerator(name);
            }
            if (currentScope.getScopeKind() == ScopeInfo.ScopeKind.Class && __CLASS__.equals(name)) {
                // If __class__ is freevar in the class scope, then is stored in frameslot with
                // different name.
                // This is preventing corner situation, when body of class has two variables with
                // the same name __class__. The first one is __class__ freevar comming from outer
                // scope
                // and the second one is __class__ (implicit) closure for inner methods,
                // where __class__ or super is used. Both of them can have different values.
                FrameSlot slot = currentScope.findFrameSlot(FrameSlotIDs.FREEVAR__CLASS__);
                if (slot != null) {
                    return (ReadNode) getReadNode(name, slot);
                }
            }
            return findVariableInLocalOrEnclosingScopes(name);
        }

        switch (getScopeKind()) {
            case Module:
                return findVariableNodeModule(name);
            case Generator:
            case GenExp:
            case ListComp:
            case DictComp:
            case SetComp:
                return findVariableNodeInGenerator(name);
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

    private StatementNode getWriteNode(String name, FrameSlot slot, ExpressionNode right) {
        if (isCellInCurrentScope(name)) {
            return !isInGeneratorScope()
                            ? factory.createWriteLocalCell(right, slot)
                            : WriteLocalCellNode.create(slot, ReadGeneratorFrameVariableNode.create(slot), right);
        }
        return !isInGeneratorScope()
                        ? factory.createWriteLocal(right, slot)
                        : WriteGeneratorFrameVariableNode.create(slot, right);
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

    public void setFreeVarsInRootScope(Frame frame) {
        if (frame != null) {
            for (Object identifier : frame.getFrameDescriptor().getIdentifiers()) {
                FrameSlot frameSlot = frame.getFrameDescriptor().findFrameSlot(identifier);
                if (frameSlot != null && frame.isObject(frameSlot)) {
                    Object value = FrameUtil.getObjectSafe(frame, frameSlot);
                    if (value instanceof PCell) {
                        globalScope.addFreeVar((String) frameSlot.getIdentifier(), false);
                    }
                }
            }
        }
    }
}
