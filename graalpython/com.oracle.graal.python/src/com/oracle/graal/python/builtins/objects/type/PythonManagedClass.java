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

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.PythonClassNativeWrapper;
import com.oracle.graal.python.builtins.objects.common.PHashingCollection;
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
    private final FlagsContainer flags;
    private String name;
    private String qualName;

    /** {@code true} if the MRO contains a native class. */
    private final boolean needsNativeAllocation;
    @CompilationFinal private Object sulongType;

    @TruffleBoundary
    protected PythonManagedClass(Object typeClass, Shape classShape, Shape instanceShape, String name, PythonAbstractClass... baseClasses) {
        super(typeClass, classShape);
        this.name = getBaseName(name);
        this.qualName = name;

        this.methodResolutionOrder = new MroSequenceStorage(name, 0);

        if (baseClasses.length == 1 && baseClasses[0] == null) {
            this.baseClasses = new PythonAbstractClass[]{};
        } else {
            unsafeSetSuperClass(baseClasses);
        }

        this.flags = new FlagsContainer(getSuperClass());

        // Compute MRO
        this.methodResolutionOrder.setInternalArrayObject(ComputeMroNode.doSlowPath(this));
        this.methodResolutionOrder.setInitialized();
        this.needsNativeAllocation = computeNeedsNativeAllocation();

        setAttribute(__DOC__, PNone.NONE);

        if (instanceShape != null) {
            this.instanceShape = instanceShape;
        } else {
            // provide our instances with a fresh shape tree
            if (PythonLanguage.getCurrent().singleContextAssumption.isValid()) {
                this.instanceShape = PythonObject.freshShape(this);
            } else {
                this.instanceShape = PythonObject.freshShape();
            }
        }
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
        PythonAbstractClass[] oldBaseClasses = getBaseClasses();
        try {
            setSuperClassInternal(newBaseClasses);
        } catch (PException pe) {
            setSuperClassInternal(oldBaseClasses);
            throw pe;
        }
    }

    private void setSuperClassInternal(PythonAbstractClass[] basses) {
        for (PythonAbstractClass base : basses) {
            if (base != null) {
                GetSubclassesNode.getUncached().execute(base).add(this);
            }
        }

        this.baseClasses = basses;
        this.methodResolutionOrder.setInternalArrayObject(ComputeMroNode.doSlowPath(this));

        Set<PythonAbstractClass> subclasses = GetSubclassesNode.getUncached().execute(this);
        for (PythonAbstractClass scls : subclasses) {
            if (scls instanceof PythonManagedClass) {
                ((PythonManagedClass) scls).methodResolutionOrder.setInternalArrayObject(ComputeMroNode.doSlowPath(scls));
            }
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
        PythonAbstractClass initialDominantBase;
        long flags;

        public FlagsContainer(PythonAbstractClass superClass) {
            this.initialDominantBase = superClass;
        }

        private void setValue(long flags) {
            if (initialDominantBase != null) {
                initialDominantBase = null;
            }
            this.flags = flags;
        }
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
            return dict instanceof PHashingCollection;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"self == cachedManagedClass", "dictExists(dict)"}, assumptions = "singleContextAssumption()", limit = "1")
        static PHashingCollection getConstant(PythonManagedClass self,
                        @Cached(value = "self", weak = true) PythonManagedClass cachedManagedClass,
                        @Cached(value = "self.getAttribute(DICT)", weak = true) Object dict) {
            // type.__dict__ is a read-only attribute
            return (PHashingCollection) dict;
        }

        @Specialization(replaces = "getConstant")
        static PHashingCollection getDict(PythonManagedClass self,
                        @CachedLibrary("self") DynamicObjectLibrary dylib) {
            return (PHashingCollection) dylib.getOrDefault(self, DICT, null);
        }
    }
}
