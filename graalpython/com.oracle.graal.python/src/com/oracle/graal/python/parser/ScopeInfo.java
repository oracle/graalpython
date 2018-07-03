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

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.argument.ReadDefaultArgumentNode;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;

public class ScopeInfo {

    public enum ScopeKind {
        Module,
        Function,
        Class,
        // generator expression or generator function
        Generator,
        // list comprehension
        ListComp
    }

    private final String scopeId;
    private final FrameDescriptor frameDescriptor;
    private ScopeKind scopeKind;
    private final ScopeInfo parent;
    private final Set<ScopeInfo> childScopes = new HashSet<>();

    /**
     * Symbols declared using 'global' or 'nonlocal' statements.
     */
    private Set<String> explicitGlobalVariables = new HashSet<>();
    private Set<String> explicitNonlocalVariables = new HashSet<>();

    /**
     * Symbols which are local variables but are closed over in nested scopes
     */
    // variables that are referenced in enclosed contexts
    private Set<String> cellVars = new LinkedHashSet<>();
    // variables that are referenced from enclosing contexts
    private Set<String> freeVars = new LinkedHashSet<>();

    /**
     * An optional field that stores translated nodes of default argument values.
     * {@link #defaultArgumentNodes} is not null only when {@link #scopeKind} is Function, and the
     * function has default arguments.
     */
    private List<PNode> defaultArgumentNodes;
    private ReadDefaultArgumentNode[] defaultArgumentReads;

    private int loopCount = 0;

    public ScopeInfo(String scopeId, ScopeKind kind, FrameDescriptor frameDescriptor, ScopeInfo parent) {
        this.scopeId = scopeId;
        this.scopeKind = kind;
        this.frameDescriptor = frameDescriptor;
        this.parent = parent;
        // register current scope as child to parent scope
        if (this.parent != null) {
            this.parent.childScopes.add(this);
        }
    }

    public void incLoopCount() {
        loopCount++;
    }

    public int getLoopCount() {
        return loopCount;
    }

    public void resetLoopCount() {
        this.loopCount = 0;
    }

    public Set<ScopeInfo> getChildScopes() {
        return childScopes;
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

    public ScopeInfo getParent() {
        return parent;
    }

    public FrameSlot findFrameSlot(String identifier) {
        return this.getFrameDescriptor().findFrameSlot(identifier);
    }

    private void createSlotIfNotPresent(String identifier) {
        FrameSlot frameSlot = this.getFrameDescriptor().findFrameSlot(identifier);
        if (frameSlot == null) {
            this.getFrameDescriptor().addFrameSlot(identifier);
        }
    }

    public void addExplicitGlobalVariable(String identifier) {
        explicitGlobalVariables.add(identifier);
    }

    public void addExplicitNonlocalVariable(String identifier) {
        explicitNonlocalVariables.add(identifier);
    }

    public boolean isExplicitGlobalVariable(String identifier) {
        return explicitGlobalVariables.contains(identifier);
    }

    public boolean isExplicitNonlocalVariable(String identifier) {
        return explicitNonlocalVariables.contains(identifier);
    }

    public void addCellVar(String identifier) {
        addCellVar(identifier, false);
    }

    public void addCellVar(String identifier, boolean createFrameSlot) {
        this.cellVars.add(identifier);
        if (createFrameSlot) {
            this.createSlotIfNotPresent(identifier);
        }
    }

    public void addFreeVar(String identifier) {
        addFreeVar(identifier, false);
    }

    protected void addFreeVar(String identifier, boolean createFrameSlot) {
        this.freeVars.add(identifier);
        if (createFrameSlot) {
            this.createSlotIfNotPresent(identifier);
        }
    }

    public boolean isCellVar(String identifier) {
        return this.cellVars.contains(identifier);
    }

    public boolean isFreeVar(String identifier) {
        return this.freeVars.contains(identifier);
    }

    private static FrameSlot[] getFrameSlots(Collection<String> identifiers, ScopeInfo scope) {
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

    public void setDefaultArgumentNodes(List<PNode> defaultArgumentNodes) {
        this.defaultArgumentNodes = defaultArgumentNodes;
    }

    public List<PNode> getDefaultArgumentNodes() {
        return defaultArgumentNodes;
    }

    public void setDefaultArgumentReads(ReadDefaultArgumentNode[] defaultArgumentReads) {
        this.defaultArgumentReads = defaultArgumentReads;
    }

    public ReadDefaultArgumentNode[] getDefaultArgumentReads() {
        return this.defaultArgumentReads;
    }

    public void createFrameSlotsForCellAndFreeVars() {
        for (String identifier : cellVars) {
            createSlotIfNotPresent(identifier);
        }
        for (String identifier : freeVars) {
            createSlotIfNotPresent(identifier);
        }
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return scopeKind.toString() + " " + scopeId;
    }
}
