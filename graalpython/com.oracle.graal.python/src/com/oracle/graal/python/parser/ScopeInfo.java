/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates.
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

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.oracle.graal.python.nodes.expression.ExpressionNode;
import com.oracle.graal.python.nodes.frame.FrameSlotIDs;
import com.oracle.graal.python.nodes.frame.PythonFrame;
import com.oracle.graal.python.nodes.function.FunctionDefinitionNode.KwDefaultExpressionNode;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameDescriptor.Builder;
import com.oracle.truffle.api.frame.FrameSlotKind;

public final class ScopeInfo {

    public enum ScopeKind {
        Module,
        Function,
        Class,
        // Generator Function
        Generator,
        // Generator Expression
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
    private final LinkedHashMap<Object, Integer> frameContents;
    private final ArrayList<Object> identifiers = new ArrayList<>();
    private final ArrayList<Integer> scopedTemps = new ArrayList<>();
    private int scopedTempIndex;
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
    // Used for serialization and deserialization
    private final int serializationId;

    // Qualified name for this scope
    private final String qualname;

    public ScopeInfo(String scopeId, ScopeKind kind, FrameDescriptor frameDescriptor, ScopeInfo parent) {
        this(scopeId, -1, kind, frameDescriptor, parent);
    }

    private ScopeInfo(String scopeId, int serializationId, ScopeKind kind, FrameDescriptor frameDescriptor, ScopeInfo parent) {
        this.scopeId = scopeId;
        this.scopeKind = kind;
        this.frameContents = new LinkedHashMap<>();
        if (frameDescriptor != null) {
            for (Object identifier : PythonFrame.getIdentifiers(frameDescriptor)) {
                this.frameContents.put(identifier, identifiers.size());
                this.identifiers.add(identifier);
            }
        }
        this.parent = parent;
        this.annotationsField = false;
        // register current scope as child to parent scope
        if (this.parent != null) {
            this.nextChildScope = this.parent.firstChildScope;
            this.parent.firstChildScope = this;
        }
        this.serializationId = serializationId == -1 ? this.hashCode() : serializationId;

        this.qualname = computeQualname();
    }

    private String computeQualname() {
        if (this.scopeKind != ScopeKind.Module) {
            StringBuilder sb = new StringBuilder();
            if (parent != null) {
                if (!parent.isExplicitGlobalVariable(this.scopeId)) {
                    sb.append(parent.getQualname());
                    if (isScopeFunctionLike(parent)) {
                        sb.append(".<locals>");
                    }
                }
            }
            if (isScopeFunctionLike(this) || scopeKind == ScopeKind.Class) {
                if (sb.length() != 0) {
                    sb.append('.');
                }
                sb.append(this.scopeId);
            }
            return sb.toString();
        } else {
            return "";
        }
    }

    private static boolean isScopeFunctionLike(ScopeInfo scope) {
        switch (scope.getScopeKind()) {
            case GenExp:
            case ListComp:
            case DictComp:
            case SetComp:
            case Function:
            case Generator:
                return true;
            default:
                return false;
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

    public String getQualname() {
        return qualname;
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

    public Set<Object> getFrameIdentifiers() {
        return frameContents.keySet();
    }

    public void replaceFrameIdentifier(Object identifier, Object newIdentifier) {
        Integer index = frameContents.get(identifier);
        assert index != null;
        identifiers.set(index, newIdentifier);
        frameContents.remove(identifier);
        frameContents.put(newIdentifier, index);
    }

    public FrameDescriptor createFrameDescriptor() {
        Builder frameBuilder = FrameDescriptor.newBuilder();
        int i = 0;
        for (Object id : identifiers) {
            FrameSlotKind kind = id == RETURN_SLOT_ID ? FrameSlotKind.Object : FrameSlotKind.Illegal;
            int idx = frameBuilder.addSlot(kind, id, null);
            assert idx == i;
            i++;
        }
        return frameBuilder.build();
    }

    public boolean hasAnnotations() {
        return annotationsField;
    }

    public void setHasAnnotations(boolean hasAnnotations) {
        this.annotationsField = hasAnnotations;
    }

    public ScopeInfo getParent() {
        return parent;
    }

    public Integer findFrameSlot(Object identifier) {
        return frameContents.get(identifier);
    }

    private int createSlotIfNotPresent(Object identifier) {
        return frameContents.computeIfAbsent(identifier, id -> {
            identifiers.add(id);
            return identifiers.size() - 1;
        });
    }

    public void defineSlot(Object identifier) {
        createSlotIfNotPresent(identifier);
    }

    public int getReturnSlot() {
        return createSlotIfNotPresent(RETURN_SLOT_ID);
    }

    public int createTemp() {
        return createSlotIfNotPresent(FrameSlotIDs.getTempLocal(identifiers.size()));
    }

    public int acquireScopedTemp() {
        if (scopedTempIndex == scopedTemps.size()) {
            scopedTemps.add(createTemp());
        }
        return scopedTemps.get(scopedTempIndex++);
    }

    public void releaseScopedTemp() {
        assert scopedTempIndex > 0;
        scopedTempIndex--;
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

    public boolean hasExplicitGlobalVariables() {
        return explicitGlobalVariables != null && !explicitGlobalVariables.isEmpty();
    }

    public Set<String> getExplicitGlobalVariables() {
        return explicitGlobalVariables;
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
            defineSlot(identifier);
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
            defineSlot(identifier);
        }
    }

    public void addFreeVar(String identifier, boolean createFrameSlot) {
        if (freeVars == null) {
            freeVars = new TreeSet<>();
        }
        freeVars.add(identifier);
        if (createFrameSlot) {
            if (scopeKind == ScopeKind.Class && __CLASS__.equals(identifier)) {
                // This is preventing corner situation, when body of class has two variables with
                // the same name __class__. The first one is __class__ freevar coming from outer
                // scope
                // and the second one is __class__ (implicit) closure for inner methods,
                // where __class__ or super is used. Both of them can have different values.
                // So the first one is stored in slot with different identifier.
                defineSlot(FrameSlotIDs.FREEVAR__CLASS__);
            } else {
                defineSlot(identifier);
            }
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
            defineSlot(identifier);
        }
    }

    public boolean isCellVar(String identifier) {
        return cellVars != null && cellVars.contains(identifier);
    }

    public boolean isFreeVar(String identifier) {
        return freeVars != null && freeVars.contains(identifier);
    }

    private static final int[] EMPTY = new int[0];

    private int[] getFrameSlots(Collection<String> ids) {
        if (ids == null) {
            return EMPTY;
        }
        int[] slots = new int[ids.size()];
        int i = 0;
        for (String identifier : ids) {
            /*
             * When replacing code.co_freevars, we might see non-existing identifiers here. Those
             * should never be accessed, so use a value that will trip.
             */
            Integer slot = findFrameSlot(identifier);
            slots[i++] = slot == null ? Integer.MAX_VALUE : slot;
        }
        return slots;
    }

    public TreeSet<String> getCellVars() {
        return cellVars;
    }

    public TreeSet<String> getFreeVars() {
        return freeVars;
    }

    public int[] getCellVarSlots() {
        return getFrameSlots(cellVars);
    }

    public int[] getFreeVarSlots() {
        if (freeVars == null) {
            return EMPTY;
        }
        int[] slots = new int[freeVars.size()];
        int i = 0;
        for (String identifier : freeVars) {
            slots[i] = findFrameSlot(identifier);
            if (scopeKind == ScopeKind.Class && __CLASS__.equals(identifier)) {
                /*
                 * If __class__ is freevar in the class scope, then is stored in frameslot with
                 * different name. This is preventing corner situation, when body of class has two
                 * variables with the same name __class__. The first one is __class__ freevar
                 * comming from outer scope and the second one is __class__ (implicit) closure for
                 * inner methods, where __class__ or super is used. Both of them can have different
                 * values. slot can be null, when there is not used __class__ / super in a method,
                 * so __class__ is defined only as freevar not cellvar
                 */
                slots[i] = findFrameSlot(FrameSlotIDs.FREEVAR__CLASS__);
            }
            i++;
        }
        return slots;
    }

    public int[] getFreeVarSlotsInParentScope() {
        assert parent != null : "cannot get current freeVars in parent scope, parent scope cannot be null!";
        return parent.getFrameSlots(freeVars);
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

    public void debugPrint(StringBuilder sb, int indent) {
        indent(sb, indent);
        sb.append("Scope: ").append(scopeId).append("\n");
        indent(sb, indent + 1);
        sb.append("Kind: ").append(scopeKind).append("\n");
        Set<String> names = new HashSet<>();
        for (Object id : getFrameIdentifiers()) {
            names.add(id.toString());
        }
        indent(sb, indent + 1);
        sb.append("FrameDescriptor: ");
        printSet(sb, new TreeSet<>(names));
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
        writeIdentifiers(out, scope.identifiers);

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

    private static void writeIdentifiers(DataOutput out, Collection<Object> identifiers) throws IOException {
        for (Object identifier : identifiers) {
            if (identifier instanceof String) {
                out.writeByte(1);
                out.writeUTF((String) identifier);
            } else if (identifier == FrameSlotIDs.RETURN_SLOT_ID) {
                out.writeByte(2);
            } else if (identifier == FrameSlotIDs.FREEVAR__CLASS__) {
                out.writeByte(3);
            }
            // we don't have to serialize temp slots, they will be recreated during
            // execution tree creation.
        }
        out.writeByte(0);
    }

    private static void readIdentifiersAndCreateSlots(DataInput input, ScopeInfo scope) throws IOException {
        byte kind = input.readByte();
        while (kind != 0) {
            switch (kind) {
                case 1:
                    scope.defineSlot(input.readUTF());
                    break;
                case 2:
                    scope.defineSlot(FrameSlotIDs.RETURN_SLOT_ID);
                    break;
                case 3:
                    scope.defineSlot(FrameSlotIDs.FREEVAR__CLASS__);
                    break;
            }
            kind = input.readByte();
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
        readIdentifiersAndCreateSlots(input, scope);

        int len = input.readInt();
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
