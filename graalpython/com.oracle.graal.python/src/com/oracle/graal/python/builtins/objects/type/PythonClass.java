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
package com.oracle.graal.python.builtins.objects.type;

import static com.oracle.graal.python.nodes.SpecialAttributeNames.__DOC__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__NAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__QUALNAME__;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.NativeWrappers.PythonClassNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeClass;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.utilities.CyclicAssumption;

/**
 * Mutable class.
 */
public class PythonClass extends PythonObject implements LazyPythonClass {

    private final String className;

    @CompilationFinal(dimensions = 1) private PythonClass[] baseClasses;
    @CompilationFinal(dimensions = 1) private PythonClass[] methodResolutionOrder;

    /**
     * This assumption will be invalidated whenever the mro changes.
     */
    private final CyclicAssumption lookupStableAssumption;
    /**
     * These assumptions will be invalidated whenever the value of the given slot changes. All
     * assumptions will be invalidated if the mro changes.
     */
    private final Map<String, List<Assumption>> attributesInMROFinalAssumptions = new HashMap<>();

    private final Set<PythonClass> subClasses = Collections.newSetFromMap(new WeakHashMap<PythonClass, Boolean>());
    private final Shape instanceShape;
    private final FlagsContainer flags;

    /** {@code true} if the MRO contains a native class. */
    private boolean needsNativeAllocation;
    @CompilationFinal private Object sulongType;

    public final boolean isBuiltin() {
        return this instanceof PythonBuiltinClass;
    }

    @TruffleBoundary
    public PythonClass(LazyPythonClass typeClass, String name, Shape instanceShape, PythonClass... baseClasses) {
        super(typeClass, PythonLanguage.freshShape() /* do not inherit layout from the TypeClass */);
        this.className = name;

        if (baseClasses.length == 1 && baseClasses[0] == null) {
            this.baseClasses = new PythonClass[]{};
        } else {
            unsafeSetSuperClass(baseClasses);
        }

        this.flags = new FlagsContainer(getSuperClass());
        this.lookupStableAssumption = new CyclicAssumption(className);

        // Compute MRO
        computeMethodResolutionOrder();

        setAttribute(__NAME__, getBaseName(name));
        setAttribute(__QUALNAME__, className);
        setAttribute(__DOC__, PNone.NONE);
        // provide our instances with a fresh shape tree
        this.instanceShape = instanceShape;
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

    public Assumption createAttributeInMROFinalAssumption(String name) {
        CompilerAsserts.neverPartOfCompilation();
        List<Assumption> attrAssumptions = attributesInMROFinalAssumptions.getOrDefault(name, null);
        if (attrAssumptions == null) {
            attrAssumptions = new ArrayList<>();
            attributesInMROFinalAssumptions.put(name, attrAssumptions);
        }

        Assumption assumption = Truffle.getRuntime().createAssumption(name.toString());
        attrAssumptions.add(assumption);
        return assumption;
    }

    public void addAttributeInMROFinalAssumption(String name, Assumption assumption) {
        CompilerAsserts.neverPartOfCompilation();
        List<Assumption> attrAssumptions = attributesInMROFinalAssumptions.getOrDefault(name, null);
        if (attrAssumptions == null) {
            attrAssumptions = new ArrayList<>();
            attributesInMROFinalAssumptions.put(name, attrAssumptions);
        }

        attrAssumptions.add(assumption);
    }

    @TruffleBoundary
    public void invalidateAttributeInMROFinalAssumptions(String name) {
        List<Assumption> assumptions = attributesInMROFinalAssumptions.getOrDefault(name, new ArrayList<>());
        if (!assumptions.isEmpty()) {
            String message = className + "." + name;
            for (Assumption assumption : assumptions) {
                assumption.invalidate(message);
            }
        }
    }

    /**
     * This method needs to be called if the mro changes. (currently not used)
     */
    public void lookupChanged() {
        CompilerAsserts.neverPartOfCompilation();
        for (List<Assumption> list : attributesInMROFinalAssumptions.values()) {
            for (Assumption assumption : list) {
                assumption.invalidate();
            }
        }
        lookupStableAssumption.invalidate();
        for (PythonClass subclass : getSubClasses()) {
            if (subclass != null) {
                subclass.lookupChanged();
            }
        }
    }

    public Shape getInstanceShape() {
        return instanceShape;
    }

    PythonClass getSuperClass() {
        return getBaseClasses().length > 0 ? getBaseClasses()[0] : null;
    }

    PythonClass[] getMethodResolutionOrder() {
        return methodResolutionOrder;
    }

    String getName() {
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

        for (PythonClass cls : currentMRO) {
            if (cls instanceof PythonNativeClass) {
                needsNativeAllocation = true;
                break;
            }
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
    @TruffleBoundary
    public void setAttribute(Object key, Object value) {
        if (key instanceof String) {
            invalidateAttributeInMROFinalAssumptions((String) key);
        }
        super.setAttribute(key, value);
    }

    /**
     * This method supports initialization and solves boot-order problems and should not normally be
     * used.
     */
    private void unsafeSetSuperClass(PythonClass... newBaseClasses) {
        // TODO: if this is used outside bootstrapping, it needs to call
        // computeMethodResolutionOrder for subclasses.

        assert getBaseClasses() == null || getBaseClasses().length == 0;
        this.baseClasses = newBaseClasses;

        for (PythonClass base : getBaseClasses()) {
            if (base != null) {
                base.subClasses.add(this);
            }
        }
        computeMethodResolutionOrder();
    }

    final Set<PythonClass> getSubClasses() {
        return subClasses;
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return String.format("<class '%s'>", className);
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

    FlagsContainer getFlagsContainer() {
        return flags;
    }

    /**
     * Flags are copied from the initial dominant base class. However, classes may already be
     * created before the C API was initialized, i.e., flags were not set.
     */
    static final class FlagsContainer {
        PythonClass initialDominantBase;
        long flags;

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

    public final Object getSulongType() {
        return sulongType;
    }

    @TruffleBoundary
    public final void setSulongType(Object dynamicSulongType) {
        this.sulongType = dynamicSulongType;
    }

    public boolean needsNativeAllocation() {
        return needsNativeAllocation;
    }
}
