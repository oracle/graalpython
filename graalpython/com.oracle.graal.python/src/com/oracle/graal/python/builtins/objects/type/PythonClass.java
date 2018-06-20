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
package com.oracle.graal.python.builtins.objects.type;

import static com.oracle.graal.python.nodes.SpecialAttributeNames.__DOC__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__NAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__QUALNAME__;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.NativeWrappers.PythonClassNativeWrapper;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PythonCallable;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.object.Layout;
import com.oracle.truffle.api.object.ObjectType;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.utilities.CyclicAssumption;

/**
 * Mutable class.
 */
public class PythonClass extends PythonObject {

    private static final Layout objectLayout = Layout.newLayout().build();
    private final String className;

    @CompilationFinal(dimensions = 1) private PythonClass[] baseClasses;
    @CompilationFinal(dimensions = 1) private PythonClass[] methodResolutionOrder;
    private CyclicAssumption lookupStableAssumption;

    private final Set<PythonClass> subClasses = Collections.newSetFromMap(new WeakHashMap<PythonClass, Boolean>());
    private final Shape instanceShape;
    private final FlagsContainer flags;

    public final boolean isBuiltin() {
        return this instanceof PythonBuiltinClass;
    }

    @TruffleBoundary
    public PythonClass(PythonClass typeClass, String name, PythonClass... baseClasses) {
        super(typeClass);
        this.className = name;
        this.lookupStableAssumption = new CyclicAssumption(className);

        assert baseClasses.length > 0;
        if (baseClasses.length == 1 && baseClasses[0] == null) {
            this.baseClasses = new PythonClass[]{};
        } else {
            unsafeSetSuperClass(baseClasses);
        }

        this.flags = new FlagsContainer(getSuperClass());

        // Compute MRO
        computeMethodResolutionOrder();

        // do not inherit layout from the TypeClass.
        storage = freshShape().newInstance();
        setAttribute(__NAME__, getBaseName(name));
        setAttribute(__QUALNAME__, className);
        setAttribute(__DOC__, PNone.NONE);
        // provide our instances with a fresh shape tree
        instanceShape = freshShape();
    }

    private static String getBaseName(String qname) {
        int lastDot = qname.lastIndexOf('.');
        if (lastDot != -1) {
            return qname.substring(lastDot + 1);
        }
        return qname;
    }

    public Assumption getLookupStableAssumption() {
        return lookupStableAssumption.getAssumption();
    }

    @TruffleBoundary
    public void lookupChanged() {
        lookupStableAssumption.invalidate();
        for (PythonClass subclass : getSubClasses()) {
            if (subclass != null) {
                subclass.lookupChanged();
            }
        }
    }

    private static Shape freshShape() {
        return objectLayout.createShape(new ObjectType());
    }

    public Shape getInstanceShape() {
        return instanceShape;
    }

    public PythonClass getSuperClass() {
        return getBaseClasses().length > 0 ? getBaseClasses()[0] : null;
    }

    public PythonClass[] getMethodResolutionOrder() {
        return methodResolutionOrder;
    }

    public String getName() {
        return className;
    }

    private void computeMethodResolutionOrder() {
        PythonClass[] currentMRO = null;

        if (getBaseClasses().length == 0) {
            currentMRO = new PythonClass[]{this};
        } else if (getBaseClasses().length == 1) {
            PythonClass[] baseMRO = getBaseClasses()[0].getMethodResolutionOrder();

            if (baseMRO == null) {
                currentMRO = new PythonClass[]{this};
            } else {
                currentMRO = new PythonClass[baseMRO.length + 1];
                System.arraycopy(baseMRO, 0, currentMRO, 1, baseMRO.length);
                currentMRO[0] = this;
            }
        } else {
            MROMergeState[] toMerge = new MROMergeState[getBaseClasses().length + 1];

            for (int i = 0; i < getBaseClasses().length; i++) {
                toMerge[i] = new MROMergeState();
                toMerge[i].mro = getBaseClasses()[i].getMethodResolutionOrder();
            }

            toMerge[getBaseClasses().length] = new MROMergeState();
            toMerge[getBaseClasses().length].mro = getBaseClasses();
            ArrayList<PythonClass> mro = new ArrayList<>();
            mro.add(this);
            currentMRO = mergeMROs(toMerge, mro);
        }

        methodResolutionOrder = currentMRO;
    }

    PythonClass[] mergeMROs(MROMergeState[] toMerge, List<PythonClass> mro) {
        int idx;
        scan: for (idx = 0; idx < toMerge.length; idx++) {
            if (toMerge[idx].isMerged()) {
                continue scan;
            }

            PythonClass candidate = toMerge[idx].getCandidate();
            for (MROMergeState mergee : toMerge) {
                if (mergee.pastnextContains(candidate)) {
                    continue scan;
                }
            }

            mro.add(candidate);

            for (MROMergeState element : toMerge) {
                element.noteMerged(candidate);
            }

            // restart scan
            idx = -1;
        }

        for (MROMergeState mergee : toMerge) {
            if (!mergee.isMerged()) {
                throw new IllegalStateException();
            }
        }

        return mro.toArray(new PythonClass[mro.size()]);
    }

    @Override
    public PythonObject getValidStorageFullLookup(String attributeId) {
        PythonObject store = null;

        if (isOwnAttribute(attributeId)) {
            store = this;
        } else if (getBaseClasses().length > 0) {
            store = getBaseClasses()[0].getValidStorageFullLookup(attributeId);
        }

        return store;
    }

    public PythonCallable lookUpMethod(String methodName) {
        Object attr = getAttribute(methodName);
        assert attr != null;

        if (attr instanceof PythonCallable) {
            return (PythonCallable) attr;
        }

        return null;
    }

    public void addMethod(PFunction method) {
        setAttribute(method.getName(), method);
    }

    @Override
    @TruffleBoundary
    public void setAttribute(Object name, Object value) {
        super.setAttribute(name, value);
        lookupChanged();
    }

    @Override
    public Object getAttribute(String name) {
        for (PythonClass o : methodResolutionOrder) {
            if (o.getStorage().containsKey(name)) {
                return o.getStorage().get(name);
            }
        }
        return PNone.NO_VALUE;
    }

    /**
     * This method supports initialization and solves boot-order problems and should not normally be
     * used.
     */
    public void unsafeSetSuperClass(PythonClass... newBaseClasses) {
        assert getBaseClasses() == null || getBaseClasses().length == 0;
        this.baseClasses = newBaseClasses;

        for (PythonClass base : getBaseClasses()) {
            if (base != null) {
                base.subClasses.add(this);
            }
        }
        computeMethodResolutionOrder();
    }

    public final Set<PythonClass> getSubClasses() {
        return subClasses;
    }

    @Override
    public String toString() {
        return "<class \'" + className + "\'>";
    }

    public PythonClass[] getBaseClasses() {
        return baseClasses;
    }

    public long getFlags() {
        return flags.getValue();
    }

    public void setFlags(long flags) {
        this.flags.setValue(flags);
    }

    /**
     * Flags are copied from the initial dominant base class. However, classes may already be
     * created before the C API was initialized, i.e., flags were not set.
     */
    private static final class FlagsContainer {
        private PythonClass initialDominantBase;
        private long flags;

        public FlagsContainer(PythonClass superClass) {
            this.initialDominantBase = superClass;
        }

        @TruffleBoundary
        private long getValue() {
            // This method is only called from C code, i.e., the flags of the initial super class
            // must be available.
            if (initialDominantBase != null) {
                assert this != initialDominantBase.flags;
                flags = initialDominantBase.flags.getValue();
                initialDominantBase = null;
            }
            return flags;
        }

        private void setValue(long flags) {
            if (initialDominantBase != null) {
                initialDominantBase = null;
            }
            this.flags = flags;
        }
    }

    @Override
    public PythonClassNativeWrapper getNativeWrapper() {
        return (PythonClassNativeWrapper) super.getNativeWrapper();
    }
}
