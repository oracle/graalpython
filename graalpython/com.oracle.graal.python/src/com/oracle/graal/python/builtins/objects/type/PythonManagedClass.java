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
package com.oracle.graal.python.builtins.objects.type;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__DOC__;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.PythonClassNativeWrapper;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.ComputeMroNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetSubclassesNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.sequence.storage.MroSequenceStorage;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.Shape;

public abstract class PythonManagedClass extends PythonObject implements PythonAbstractClass {
    @CompilationFinal(dimensions = 1) private PythonAbstractClass[] baseClasses;

    private final MroSequenceStorage methodResolutionOrder;

    private final Set<PythonAbstractClass> subClasses = Collections.newSetFromMap(new WeakHashMap<PythonAbstractClass, Boolean>());
    private final Shape instanceShape;
    private String name;
    private String qualName;

    /** {@code true} if the MRO contains a native class. */
    private final boolean needsNativeAllocation;
    @CompilationFinal private Object sulongType;

    @TruffleBoundary
    protected PythonManagedClass(PythonLanguage lang, Object typeClass, Shape classShape, Shape instanceShape, String name, PythonAbstractClass... baseClasses) {
        this(lang, typeClass, classShape, instanceShape, name, true, baseClasses);
    }

    @TruffleBoundary
    protected PythonManagedClass(PythonLanguage lang, Object typeClass, Shape classShape, Shape instanceShape, String name, boolean invokeMro, PythonAbstractClass... baseClasses) {
        super(typeClass, classShape);
        this.name = getBaseName(name);
        this.qualName = name;

        this.methodResolutionOrder = new MroSequenceStorage(name, 0);

        if (baseClasses.length == 1 && baseClasses[0] == null) {
            this.baseClasses = new PythonAbstractClass[]{};
        } else {
            unsafeSetSuperClass(baseClasses);
        }

        this.methodResolutionOrder.setInternalArrayObject(ComputeMroNode.doSlowPath(this, invokeMro));
        if (invokeMro) {
            this.methodResolutionOrder.setInitialized();
        }

        this.needsNativeAllocation = computeNeedsNativeAllocation();

        setAttribute(__DOC__, PNone.NONE);

        if (instanceShape != null) {
            this.instanceShape = instanceShape;
        } else {
            // provide our instances with a fresh shape tree
            this.instanceShape = lang.getShapeForClass(this);
        }
    }

    /**
     * Invoke metaclass mro() method and set the result as new method resolution order.
     */
    @TruffleBoundary
    public void invokeMro() {
        PythonAbstractClass[] mro = ComputeMroNode.invokeMro(this);
        if (mro != null) {
            this.methodResolutionOrder.setInternalArrayObject(mro);
        }
        this.methodResolutionOrder.setInitialized();
    }

    private static String getBaseName(String qname) {
        int lastDot = qname.lastIndexOf('.');
        if (lastDot != -1) {
            return qname.substring(lastDot + 1);
        }
        return qname;
    }

    public Assumption getLookupStableAssumption() {
        return methodResolutionOrder.getLookupStableAssumption();
    }

    /**
     * This method needs to be called if the mro changes. (currently not used)
     */
    public void lookupChanged() {
        CompilerAsserts.neverPartOfCompilation();
        methodResolutionOrder.lookupChanged();
        for (PythonAbstractClass subclass : getSubClasses()) {
            if (subclass != null) {
                subclass.lookupChanged();
            }
        }
    }

    public final Shape getInstanceShape() {
        return instanceShape;
    }

    PythonAbstractClass getSuperClass() {
        return getBaseClasses().length > 0 ? getBaseClasses()[0] : null;
    }

    public MroSequenceStorage getMethodResolutionOrder() {
        return methodResolutionOrder;
    }

    public String getQualName() {
        return qualName;
    }

    public void setQualName(String qualName) {
        this.qualName = qualName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    private boolean computeNeedsNativeAllocation() {
        for (PythonAbstractClass cls : getMethodResolutionOrder().getInternalClassArray()) {
            if (PGuards.isNativeClass(cls)) {
                return true;
            }
        }
        return false;
    }

    @Override
    @TruffleBoundary
    public void setAttribute(Object key, Object value) {
        invalidateFinalAttribute(key);
        super.setAttribute(key, value);
    }

    @TruffleBoundary
    public void invalidateFinalAttribute(Object key) {
        CompilerAsserts.neverPartOfCompilation();
        if (key instanceof String) {
            methodResolutionOrder.invalidateAttributeInMROFinalAssumptions((String) key);
        }
    }

    /**
     * This method supports initialization and solves boot-order problems and should not normally be
     * used.
     */
    private void unsafeSetSuperClass(PythonAbstractClass... newBaseClasses) {
        CompilerAsserts.neverPartOfCompilation();
        // TODO: if this is used outside bootstrapping, it needs to call
        // computeMethodResolutionOrder for subclasses.

        assert getBaseClasses() == null || getBaseClasses().length == 0;
        this.baseClasses = newBaseClasses;

        for (PythonAbstractClass base : getBaseClasses()) {
            if (base instanceof PythonManagedClass && !((PythonManagedClass) base).getMethodResolutionOrder().isInitialized()) {
                throw PRaiseNode.getUncached().raise(TypeError, ErrorMessages.CANNOT_EXTEND_INCOMPLETE_P, base);
            }
        }
        for (PythonAbstractClass base : getBaseClasses()) {
            if (base != null) {
                GetSubclassesNode.getUncached().execute(base).add(this);
            }
        }
    }

    @TruffleBoundary
    public void setSuperClass(PythonAbstractClass... newBaseClasses) {
        ArrayList<Set<PythonAbstractClass>> newBasesSubclasses = new ArrayList<>(newBaseClasses.length);
        for (PythonAbstractClass newBase : newBaseClasses) {
            newBasesSubclasses.add(GetSubclassesNode.getUncached().execute(newBase));
        }

        PythonAbstractClass[] oldBaseClasses = getBaseClasses();
        Object[] oldMRO = this.methodResolutionOrder.getInternalArray();

        Set<PythonAbstractClass> subclasses = GetSubclassesNode.getUncached().execute(this);
        PythonAbstractClass[] subclassesArray = subclasses.toArray(new PythonAbstractClass[subclasses.size()]);
        Object[][] oldSubClasssMROs = new Object[subclasses.size()][];
        for (int i = 0; i < subclassesArray.length; i++) {
            PythonAbstractClass scls = subclassesArray[i];
            if (scls instanceof PythonManagedClass) {
                oldSubClasssMROs[i] = ((PythonManagedClass) scls).methodResolutionOrder.getInternalArray();
            }
        }

        try {
            for (PythonAbstractClass base : newBaseClasses) {
                if (base != null) {
                    GetSubclassesNode.getUncached().execute(base).add(this);
                }
            }

            this.baseClasses = newBaseClasses;
            this.methodResolutionOrder.setInternalArrayObject(ComputeMroNode.doSlowPath(this));
            this.methodResolutionOrder.lookupChanged();

            for (PythonAbstractClass scls : subclasses) {
                if (scls instanceof PythonManagedClass) {
                    PythonManagedClass pmc = (PythonManagedClass) scls;
                    pmc.methodResolutionOrder.setInternalArrayObject(ComputeMroNode.doSlowPath(scls));
                    pmc.methodResolutionOrder.lookupChanged();
                }
            }
        } catch (PException pe) {
            // undo
            for (int i = 0; i < newBaseClasses.length; i++) {
                PythonAbstractClass base = newBaseClasses[i];
                if (base != null) {
                    Set<PythonAbstractClass> s = GetSubclassesNode.getUncached().execute(base);
                    s.clear();
                    s.addAll(newBasesSubclasses.get(i));
                }
            }

            this.baseClasses = oldBaseClasses;
            this.methodResolutionOrder.setInternalArrayObject(oldMRO);
            this.methodResolutionOrder.lookupChanged();

            for (int i = 0; i < subclassesArray.length; i++) {
                PythonAbstractClass scls = subclassesArray[i];
                if (oldSubClasssMROs[i] != null) {
                    PythonManagedClass pmc = (PythonManagedClass) scls;
                    pmc.methodResolutionOrder.setInternalArrayObject(oldSubClasssMROs[i]);
                    pmc.methodResolutionOrder.lookupChanged();
                }
            }
            throw pe;
        }
    }

    final Set<PythonAbstractClass> getSubClasses() {
        return subClasses;
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return String.format("<class '%s'>", qualName);
    }

    public PythonAbstractClass[] getBaseClasses() {
        return baseClasses;
    }

    public PythonClassNativeWrapper getClassNativeWrapper() {
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

    public static boolean isInstance(Object object) {
        return object instanceof PythonClass || object instanceof PythonBuiltinClass;
    }

    public static PythonManagedClass cast(Object object) {
        if (object instanceof PythonClass) {
            return (PythonClass) object;
        } else {
            return (PythonBuiltinClass) object;
        }
    }

    @ExportMessage
    static class GetDict {
        protected static boolean dictExists(Object dict) {
            return dict instanceof PDict;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"self == cachedManagedClass", "dictExists(dict)"}, assumptions = "singleContextAssumption()", limit = "1")
        static PDict getConstant(PythonManagedClass self,
                        @Cached(value = "self", weak = true) PythonManagedClass cachedManagedClass,
                        @Cached(value = "self.getAttribute(DICT)", weak = true) Object dict) {
            // type.__dict__ is a read-only attribute
            return (PDict) dict;
        }

        @Specialization(replaces = "getConstant")
        static PDict getDict(PythonManagedClass self,
                        @Shared("dylib") @CachedLibrary(limit = "4") DynamicObjectLibrary dylib) {
            return (PDict) dylib.getOrDefault(self, DICT, null);
        }
    }
}
