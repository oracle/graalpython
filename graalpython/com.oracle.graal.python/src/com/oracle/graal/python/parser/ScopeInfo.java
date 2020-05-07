/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.frame.FrameSlotIDs;
import com.oracle.graal.python.nodes.function.FunctionDefinitionNode.KwDefaultExpressionNode;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public final class ScopeInfo {

    public enum ScopeKind {
        Module,
        Function,
        Class,
        // Generator Function
        Generator,
        // Generatro Expression
        GenExp,
        // List Comprehension
        ListComp,
        // Set Comprehension
        SetComp,
        // Dir Comprehension
        DictComp,
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
    // Used for serialization and deseraialization
    private final int serializationId;

    public ScopeInfo(String scopeId, ScopeKind kind, FrameDescriptor frameDescriptor, ScopeInfo parent) {
        this(scopeId, -1, kind, frameDescriptor, parent);
    }

    private ScopeInfo(String scopeId, int serializationId, ScopeKind kind, FrameDescriptor frameDescriptor, ScopeInfo parent) {
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
        this.serializationId = serializationId == -1 ? this.hashCode() : serializationId;
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

    public int getSerializetionId() {
        return this.serializationId;
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
        addSeenVar(identifier);
        explicitNonlocalVariables.add(identifier);
    }

    public boolean isExplicitGlobalVariable(String identifier) {
        return explicitGlobalVariables != null && explicitGlobalVariables.contains(identifier);
    }

    public boolean isExplicitNonlocalVariable(String identifier) {
        return explicitNonlocalVariables != null && explicitNonlocalVariables.contains(identifier);
    }

    public Set<String> getExplicitNonlocalVariables() {
        return explicitNonlocalVariables;
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

    public void setCellVars(String[] identifiers) {
        if (cellVars == null) {
            cellVars = new TreeSet<>();
        } else {
            cellVars.clear();
        }
        for (String identifier : identifiers) {
            cellVars.add(identifier);
            createSlotIfNotPresent(identifier);
        }
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

    public void setFreeVars(String[] identifiers) {
        if (freeVars == null) {
            freeVars = new TreeSet<>();
        } else {
            freeVars.clear();
        }
        for (String identifier : identifiers) {
            freeVars.add(identifier);
            createSlotIfNotPresent(identifier);
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

    public ScopeInfo getChildScope(String id) {
        ScopeInfo scope = firstChildScope;
        while (scope != null) {
            if (scope.getScopeId().equals(id)) {
                return scope;
            }
            scope = scope.nextChildScope;
        }
        return null;
    }

    public ScopeInfo getChildScope(int serId) {
        ScopeInfo scope = firstChildScope;
        while (scope != null) {
            if (scope.getSerializetionId() == serId) {
                return scope;
            }
            scope = scope.nextChildScope;
        }
        return null;
    }

    public static void write(DataOutput out, ScopeInfo scope) throws IOException {
        out.writeByte(scope.scopeKind.ordinal());
        out.writeUTF(scope.scopeId);
        out.writeInt(scope.getSerializetionId());
        out.writeBoolean(scope.hasAnnotations());
        // for recreating frame descriptor
        Set<Object> identifiers = scope.getFrameDescriptor().getIdentifiers();
        List<String> names = new ArrayList<>();
        for (Object identifier : identifiers) {
            if (identifier instanceof String) {
                String name = (String) identifier;
                if (!name.startsWith(FrameSlotIDs.TEMP_LOCAL_PREFIX) && !name.startsWith(FrameSlotIDs.RETURN_SLOT_ID)) {
                    names.add((String) identifier);
                }
            }
        }
        out.writeInt(names.size());
        for (String name : names) {
            out.writeUTF(name);
        }

        if (scope.explicitGlobalVariables == null) {
            out.writeInt(0);
        } else {
            out.writeInt(scope.explicitGlobalVariables.size());
            for (String identifier : scope.explicitGlobalVariables) {
                out.writeUTF(identifier);
            }
        }

        if (scope.explicitNonlocalVariables == null) {
            out.writeInt(0);
        } else {
            out.writeInt(scope.explicitNonlocalVariables.size());
            for (String identifier : scope.explicitNonlocalVariables) {
                out.writeUTF(identifier);
            }
        }

        if (scope.cellVars == null) {
            out.writeInt(0);
        } else {
            out.writeInt(scope.cellVars.size());
            for (String identifier : scope.cellVars) {
                out.writeUTF(identifier);
            }
        }

        if (scope.freeVars == null) {
            out.writeInt(0);
        } else {
            out.writeInt(scope.freeVars.size());
            for (String identifier : scope.freeVars) {
                out.writeUTF(identifier);
            }
        }

        ScopeInfo child = scope.firstChildScope;
        if (child == null) {
            out.writeInt(0);
        } else {
            List<ScopeInfo> children = new ArrayList<>();
            while (child != null) {
                children.add(child);
                child = child.nextChildScope;
            }
            out.writeInt(children.size());
            for (int i = children.size() - 1; i >= 0; i--) {
                write(out, children.get(i));
            }
        }
    }

    public static ScopeInfo read(DataInput input, ScopeInfo parent) throws IOException {
        byte kindByte = input.readByte();
        if (kindByte == -1) {
            // there is end of the scope marker, no other scope in parent.
            return null;
        }
        ScopeKind kind = ScopeKind.values()[kindByte];

        String id = input.readUTF();
        int serializationId = input.readInt();
        boolean hasAnnotations = input.readBoolean();

        ScopeInfo scope = new ScopeInfo(id, serializationId, kind, null, parent);
        scope.annotationsField = hasAnnotations;
        int len = input.readInt();
        for (int i = 0; i < len; i++) {
            scope.createSlotIfNotPresent(input.readUTF());
        }

        len = input.readInt();
        for (int i = 0; i < len; i++) {
            scope.addExplicitGlobalVariable(input.readUTF());
        }

        len = input.readInt();
        for (int i = 0; i < len; i++) {
            scope.addExplicitNonlocalVariable(input.readUTF());
        }

        len = input.readInt();
        for (int i = 0; i < len; i++) {
            scope.addCellVar(input.readUTF());
        }

        len = input.readInt();
        for (int i = 0; i < len; i++) {
            scope.addFreeVar(input.readUTF(), false);
        }

        int childrenCount = input.readInt();
        for (int i = 0; i < childrenCount; i++) {
            read(input, scope);
        }

        return scope;
    }

}
