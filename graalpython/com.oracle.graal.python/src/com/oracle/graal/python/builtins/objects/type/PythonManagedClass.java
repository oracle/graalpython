/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates.
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
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DOC__;
import static com.oracle.graal.python.nodes.truffle.TruffleStringMigrationHelpers.assertNoJavaString;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonClassNativeWrapper;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.ComputeMroNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetSubclassesNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.sequence.storage.MroSequenceStorage;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.strings.TruffleString;

public abstract class PythonManagedClass extends PythonObject implements PythonAbstractClass {
    @CompilationFinal(dimensions = 1) private PythonAbstractClass[] baseClasses;

    @CompilationFinal private MroSequenceStorage methodResolutionOrder;

    private boolean abstractClass;

    // Needs to maintain the order. It would make sense to use weakrefs, but CPython doesn't do that
    private final LinkedHashSet<PythonAbstractClass> subClasses = new LinkedHashSet<>();
    @CompilationFinal private Shape instanceShape;
    private TruffleString name;
    private TruffleString qualName;

    /**
     * Access using methods in {@link SpecialMethodSlot}.
     *
     * @see SpecialMethodSlot
     */
    Object[] specialMethodSlots;
    @CompilationFinal protected long methodsFlags = 0L;

    /** {@code true} if the MRO contains a native class. */
    private final boolean needsNativeAllocation;
    @CompilationFinal private Object sulongType;
    @CompilationFinal private boolean mroInitialized = false;

    public PTuple mroStore;
    public PTuple basesTuple;

    @TruffleBoundary
    protected PythonManagedClass(PythonLanguage lang, Object typeClass, Shape classShape, Shape instanceShape, TruffleString name, PythonAbstractClass... baseClasses) {
        this(lang, typeClass, classShape, instanceShape, name, true, true, baseClasses);
    }

    @TruffleBoundary
    @SuppressWarnings("this-escape")
    protected PythonManagedClass(PythonLanguage lang, Object typeClass, Shape classShape, Shape instanceShape, TruffleString name, boolean invokeMro, boolean initDocAttr,
                    PythonAbstractClass... baseClasses) {
        super(typeClass, classShape);
        this.name = name;
        this.qualName = name;

        this.methodResolutionOrder = new MroSequenceStorage(name, 0);

        if (baseClasses.length == 1 && baseClasses[0] == null) {
            this.baseClasses = new PythonAbstractClass[]{};
        } else {
            unsafeSetSuperClass(baseClasses);
        }

        this.setMRO(ComputeMroNode.doSlowPath(this, invokeMro));
        if (invokeMro) {
            mroInitialized = true;
        }

        this.needsNativeAllocation = computeNeedsNativeAllocation();

        if (initDocAttr) {
            setAttribute(T___DOC__, PNone.NONE);
        }

        if (instanceShape != null) {
            this.instanceShape = instanceShape;
        } else {
            // provide our instances with a fresh shape tree
            this.instanceShape = lang.getShapeForClass(this);
        }
    }

    public boolean isMROInitialized() {
        return mroInitialized;
    }

    /**
     * Invoke metaclass mro() method and set the result as new method resolution order.
     */
    @TruffleBoundary
    public void invokeMro() {
        PythonAbstractClass[] mro = ComputeMroNode.invokeMro(this);
        if (mro != null) {
            this.setMRO(mro);
        }
        mroInitialized = true;
    }

    public Assumption getLookupStableAssumption() {
        return methodResolutionOrder.getLookupStableAssumption();
    }

    /**
     * This method needs to be called if the mro changes. (currently not used)
     */
    @Override
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

    public void setMRO(PythonAbstractClass[] mro) {
        methodResolutionOrder = new MroSequenceStorage(name, mro);
    }

    public MroSequenceStorage getMethodResolutionOrder() {
        return methodResolutionOrder;
    }

    public TruffleString getQualName() {
        return qualName;
    }

    public void setQualName(TruffleString qualName) {
        this.qualName = qualName;
    }

    public TruffleString getName() {
        return name;
    }

    public void setName(TruffleString name) {
        this.name = name;
    }

    public boolean isAbstractClass() {
        return abstractClass;
    }

    public void setAbstractClass(boolean abstractClass) {
        this.abstractClass = abstractClass;
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
    public void setAttribute(Object keyObj, Object value) {
        Object key = assertNoJavaString(keyObj);
        super.setAttribute(key, value);
        onAttributeUpdate(key, value);
    }

    /**
     * Fast-path check designed for PE code.
     */
    public boolean canSkipOnAttributeUpdate(TruffleString key, @SuppressWarnings("unused") Object value, TruffleString.CodePointLengthNode codePointLengthNode,
                    TruffleString.CodePointAtIndexNode codePointAtIndexNode) {
        return !methodResolutionOrder.hasAttributeInMROFinalAssumptions() &&
                        !SpecialMethodSlot.canBeSpecial(key, codePointLengthNode, codePointAtIndexNode);
    }

    public final void onAttributeUpdate(Object key, Object value) {
        // In compilation: use a profile and call the String key overload
        CompilerAsserts.neverPartOfCompilation();
        if (key instanceof TruffleString) {
            onAttributeUpdate((TruffleString) key, value);
        }
    }

    @TruffleBoundary
    public void onAttributeUpdate(TruffleString key, Object value) {
        methodResolutionOrder.invalidateAttributeInMROFinalAssumptions(key);
        SpecialMethodSlot slot = SpecialMethodSlot.findSpecialSlotUncached(key);
        if (slot != null) {
            SpecialMethodSlot.fixupSpecialMethodSlot(this, slot, value);
        }
    }

    @TruffleBoundary
    public void setMethodsFlags(long flag) {
        assert CompilerDirectives.inInterpreter();
        methodsFlags |= flag;
    }

    public long getMethodsFlags() {
        return methodsFlags;
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
            if (base instanceof PythonManagedClass && !((PythonManagedClass) base).mroInitialized) {
                throw PRaiseNode.getUncached().raise(TypeError, ErrorMessages.CANNOT_EXTEND_INCOMPLETE_P, base);
            }
        }
        for (PythonAbstractClass base : getBaseClasses()) {
            if (base != null) {
                if (PGuards.isNativeClass(base)) {
                    Object nativeBase = ToSulongNode.getUncached().execute(base);
                    PCallCapiFunction.getUncached().call(NativeCAPISymbol.FUN_TRUFFLE_CHECK_TYPE_READY, nativeBase);
                }
                GetSubclassesNode.getUncached().execute(base).add(this);
            }
        }
    }

    @TruffleBoundary
    public final void setSuperClass(PythonAbstractClass... newBaseClasses) {
        ArrayList<Set<PythonAbstractClass>> newBasesSubclasses = new ArrayList<>(newBaseClasses.length);
        for (PythonAbstractClass newBase : newBaseClasses) {
            newBasesSubclasses.add(GetSubclassesNode.getUncached().execute(newBase));
        }

        PythonAbstractClass[] oldBaseClasses = getBaseClasses();
        PythonAbstractClass[] oldMRO = (PythonAbstractClass[]) this.methodResolutionOrder.getInternalArray();

        Set<PythonAbstractClass> subclasses = GetSubclassesNode.getUncached().execute(this);
        PythonAbstractClass[] subclassesArray = subclasses.toArray(new PythonAbstractClass[subclasses.size()]);
        PythonAbstractClass[][] oldSubClasssMROs = new PythonAbstractClass[subclasses.size()][];
        for (int i = 0; i < subclassesArray.length; i++) {
            PythonAbstractClass scls = subclassesArray[i];
            if (scls instanceof PythonManagedClass) {
                oldSubClasssMROs[i] = (PythonAbstractClass[]) ((PythonManagedClass) scls).methodResolutionOrder.getInternalArray();
            }
        }

        try {
            // for what follows see also typeobject.c#type_set_bases()
            this.baseClasses = newBaseClasses;
            this.methodResolutionOrder.lookupChanged();
            this.setMRO(ComputeMroNode.doSlowPath(this));

            for (PythonAbstractClass scls : subclasses) {
                if (scls instanceof PythonManagedClass) {
                    PythonManagedClass pmc = (PythonManagedClass) scls;
                    pmc.methodResolutionOrder.lookupChanged();
                    pmc.setMRO(ComputeMroNode.doSlowPath(scls));
                }
            }
            if (this.baseClasses == newBaseClasses) {
                // take no action if bases were replaced through reentrance
                for (PythonAbstractClass base : oldBaseClasses) {
                    if (base instanceof PythonManagedClass) {
                        GetSubclassesNode.getUncached().execute(base).remove(this);
                    }
                }
                for (PythonAbstractClass base : newBaseClasses) {
                    if (base instanceof PythonManagedClass) {
                        GetSubclassesNode.getUncached().execute(base).add(this);
                    }
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
            if (this.baseClasses == newBaseClasses) {
                // take no action if bases were replaced through reentrance
                // revert only if set in this call
                // e.g. the mro() call might have manipulated __bases__
                this.baseClasses = oldBaseClasses;
            }
            this.methodResolutionOrder.lookupChanged();
            this.setMRO(oldMRO);

            for (int i = 0; i < subclassesArray.length; i++) {
                PythonAbstractClass scls = subclassesArray[i];
                if (oldSubClasssMROs[i] != null) {
                    PythonManagedClass pmc = (PythonManagedClass) scls;
                    pmc.methodResolutionOrder.lookupChanged();
                    pmc.setMRO(oldSubClasssMROs[i]);
                }
            }
            throw pe;
        }
    }

    final LinkedHashSet<PythonAbstractClass> getSubClasses() {
        return subClasses;
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return String.format("<class '%s'>", qualName);
    }

    public final PythonAbstractClass[] getBaseClasses() {
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

    /**
     * Sets the {@link PythonObject#HAS_SLOTS_BUT_NO_DICT_FLAG} shape flag in the
     * {@code instanceShape}. This method must not be called after the type has been initialized and
     * used.
     */
    @TruffleBoundary
    public void setHasSlotsButNoDictFlag() {
        instanceShape = PythonLanguage.getShapeForClassWithoutDict(this);
    }
}
