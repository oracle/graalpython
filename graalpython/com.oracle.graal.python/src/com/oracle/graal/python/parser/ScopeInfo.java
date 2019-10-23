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

import com.oracle.graal.python.nodes.SpecialAttributeNames;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.function.FunctionDefinitionNode.KwDefaultExpressionNode;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;

public final class ScopeInfo {

    public enum ScopeKind {
        Module,
        Function,
        Class,
        // generator expression or generator function
        Generator,
        // list comprehension
        ListComp,

        // new
        Transparent
    }

    private final String scopeId;
    private FrameDescriptor frameDescriptor;
    private final ArrayList<String> identifierToIndex;
    private ScopeKind scopeKind;
    private final ScopeInfo parent;

    private ScopeInfo firstChildScope; // start of a linked list
    private ScopeInfo nextChildScope; // next pointer for the linked list

    /**
     * Symbols declared using 'global' or 'nonlocal' statements.
     */
    private Set<String> explicitGlobalVariables;
    private Set<String> explicitNonlocalVariables;

    /**
     * Symbols which are local variables but are closed over in nested scopes
     */
    // variables that are referenced in enclosed contexts
    private TreeSet<String> cellVars;
    // variables that are referenced from enclosing contexts
    private TreeSet<String> freeVars;

    /**
     * An optional field that stores translated nodes of default argument values.
     * {@link #defaultArgumentNodes} is not null only when {@link #scopeKind} is Function, and the
     * function has default arguments.
     */
    private List<ExpressionNode> defaultArgumentNodes;

    /**
     * An optional field that stores translated nodes of default keyword-only argument values.
     * Keyword-only arguments are all arguments after a varargs marker (named or unnamed).
     * {@link #defaultArgumentNodes} is not null only when {@link #scopeKind} is Function, and the
     * function has default arguments.
     */
    private List<KwDefaultExpressionNode> kwDefaultArgumentNodes;

    private TreeSet<String> seenVars;

    private boolean annotationsField;

    public ScopeInfo(String scopeId, ScopeKind kind, FrameDescriptor frameDescriptor, ScopeInfo parent) {
        this.scopeId = scopeId;
        this.scopeKind = kind;
        this.frameDescriptor = frameDescriptor == null ? new FrameDescriptor() : frameDescriptor;
        this.parent = parent;
        this.annotationsField = false;
        this.identifierToIndex = new ArrayList<>();
        // register current scope as child to parent scope
        if (this.parent != null) {
            this.nextChildScope = this.parent.firstChildScope;
            this.parent.firstChildScope = this;
        }
    }

    public ScopeInfo getFirstChildScope() {
        return firstChildScope;
    }

    public ScopeInfo getNextChildScope() {
        return nextChildScope;
    }

    public String getScopeId() {
        return scopeId;
    }

    public ScopeKind getScopeKind() {
        return scopeKind;
    }

    public void setAsGenerator() {
        assert scopeKind == ScopeKind.Function || scopeKind == ScopeKind.Generator;
        scopeKind = ScopeKind.Generator;
    }

    public FrameDescriptor getFrameDescriptor() {
        return frameDescriptor;
    }

    public boolean hasAnnotations() {
        return annotationsField;
    }

    public void setHasAnnotations(boolean hasAnnotations) {
        this.annotationsField = hasAnnotations;
    }

    public void setFrameDescriptor(FrameDescriptor frameDescriptor) {
        this.frameDescriptor = frameDescriptor;
    }

    public ScopeInfo getParent() {
        return parent;
    }

    public FrameSlot findFrameSlot(String identifier) {
        assert identifier != null : "identifier is null!";
        return this.getFrameDescriptor().findFrameSlot(identifier);
    }

    public FrameSlot createSlotIfNotPresent(String identifier) {
        assert identifier != null : "identifier is null!";
        FrameSlot frameSlot = this.getFrameDescriptor().findFrameSlot(identifier);
        if (frameSlot == null) {
            identifierToIndex.add(identifier);
            return getFrameDescriptor().addFrameSlot(identifier);
        } else {
            return frameSlot;
        }
    }

    public void addSeenVar(String name) {
        if (seenVars == null) {
            seenVars = new TreeSet<>();
        }
        seenVars.add(name);
    }

    public Set<String> getSeenVars() {
        return seenVars;
    }

    public void addExplicitGlobalVariable(String identifier) {
        if (explicitGlobalVariables == null) {
            explicitGlobalVariables = new HashSet<>();
        }
        explicitGlobalVariables.add(identifier);
    }

    public void addExplicitNonlocalVariable(String identifier) {
        if (explicitNonlocalVariables == null) {
            explicitNonlocalVariables = new HashSet<>();
        }
        explicitNonlocalVariables.add(identifier);
    }

    public boolean isExplicitGlobalVariable(String identifier) {
        return explicitGlobalVariables != null && explicitGlobalVariables.contains(identifier);
    }

    public boolean isExplicitNonlocalVariable(String identifier) {
        return explicitNonlocalVariables != null && explicitNonlocalVariables.contains(identifier);
    }

    public void addCellVar(String identifier) {
        addCellVar(identifier, false);
    }

    public void addCellVar(String identifier, boolean createFrameSlot) {
        if (cellVars == null) {
            cellVars = new TreeSet<>();
        }
        cellVars.add(identifier);
        if (createFrameSlot) {
            this.createSlotIfNotPresent(identifier);
        }
    }

    public void addFreeVar(String identifier) {
        addFreeVar(identifier, false);
    }

    public void addFreeVar(String identifier, boolean createFrameSlot) {
        if (freeVars == null) {
            freeVars = new TreeSet<>();
        }
        freeVars.add(identifier);
        if (createFrameSlot) {
            this.createSlotIfNotPresent(identifier);
        }
    }

    public boolean isCellVar(String identifier) {
        return cellVars != null && cellVars.contains(identifier);
    }

    public boolean isFreeVar(String identifier) {
        return freeVars != null && freeVars.contains(identifier);
    }

    private static final FrameSlot[] EMPTY = new FrameSlot[0];

    private static FrameSlot[] getFrameSlots(Collection<String> identifiers, ScopeInfo scope) {
        if (identifiers == null) {
            return EMPTY;
        }
        assert scope != null : "getting frame slots: scope cannot be null!";
        FrameSlot[] slots = new FrameSlot[identifiers.size()];
        int i = 0;
        for (String identifier : identifiers) {
            slots[i++] = scope.findFrameSlot(identifier);
        }
        return slots;
    }

    public FrameSlot[] getCellVarSlots() {
        return getFrameSlots(cellVars, this);
    }

    public FrameSlot[] getFreeVarSlots() {
        return getFrameSlots(freeVars, this);
    }

    public FrameSlot[] getFreeVarSlotsInParentScope() {
        assert parent != null : "cannot get current freeVars in parent scope, parent scope cannot be null!";
        return getFrameSlots(freeVars, parent);
    }

    public void setDefaultArgumentNodes(List<ExpressionNode> defaultArgumentNodes) {
        this.defaultArgumentNodes = defaultArgumentNodes;
    }

    public void setDefaultKwArgumentNodes(List<KwDefaultExpressionNode> defaultArgs) {
        this.kwDefaultArgumentNodes = defaultArgs;

    }

    public boolean isInClassScope() {
        return getScopeKind() == ScopeKind.Class;
    }

    public List<ExpressionNode> getDefaultArgumentNodes() {
        return defaultArgumentNodes;
    }

    public List<KwDefaultExpressionNode> getDefaultKwArgumentNodes() {
        return kwDefaultArgumentNodes;
    }

    public void createFrameSlotsForCellAndFreeVars() {
        if (cellVars != null) {
            cellVars.forEach((identifier) -> {
                createSlotIfNotPresent(identifier);
            });
        }
        if (freeVars != null) {
            freeVars.forEach((identifier) -> {
                createSlotIfNotPresent(identifier);
            });
        }
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return scopeKind.toString() + " " + scopeId;
    }

    public Integer getVariableIndex(String name) {
        for (int i = 0; i < identifierToIndex.size(); i++) {
            if (identifierToIndex.get(i).equals(name)) {
                return i;
            }
        }
        throw new IllegalStateException("Cannot find argument for name " + name + " in scope " + getScopeId());
    }

    public void debugPrint(StringBuilder sb, int indent) {
        indent(sb, indent);
        sb.append("Scope: ").append(scopeId).append("\n");
        indent(sb, indent + 1);
        sb.append("Kind: ").append(scopeKind).append("\n");
        Set<String> names = new HashSet<>();
        frameDescriptor.getIdentifiers().forEach((id) -> {
            names.add((String) id);
        });
        indent(sb, indent + 1);
        sb.append("FrameDescriptor: ");
        printSet(sb, names);
        sb.append("\n");
        indent(sb, indent + 1);
        sb.append("CellVars: ");
        printSet(sb, cellVars);
        sb.append("\n");
        indent(sb, indent + 1);
        sb.append("FreeVars: ");
        printSet(sb, freeVars);
        sb.append("\n");
        ScopeInfo child = firstChildScope;
        while (child != null) {
            child.debugPrint(sb, indent + 1);
            child = child.nextChildScope;
        }
    }

    private static void indent(StringBuilder sb, int indent) {
        for (int i = 0; i < indent; i++) {
            sb.append("    ");
        }
    }

    private static void printSet(StringBuilder sb, Set<String> set) {
        if (set == null || set.isEmpty()) {
            sb.append("Empty");
        } else {
            sb.append("[");
            boolean first = true;
            for (String name : set) {
                if (first) {
                    sb.append(name);
                    first = false;
                } else {
                    sb.append(", ").append(name);
                }
            }
            sb.append("]");
        }
    }

}
