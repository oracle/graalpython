/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates.
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

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonClassNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitionsFactory.PythonToNativeNodeGen;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.ComputeMroNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetSubclassesAsArrayNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetSubclassesNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.storage.MroSequenceStorage;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.CodePointAtIndexNode;
import com.oracle.truffle.api.strings.TruffleString.CodePointLengthNode;

public abstract class PythonManagedClass extends PythonObject implements PythonAbstractClass {
    @CompilationFinal private Object base;
    @CompilationFinal(dimensions = 1) private PythonAbstractClass[] baseClasses;

    @CompilationFinal private MroSequenceStorage methodResolutionOrder;

    private boolean abstractClass;

    // Needs to maintain the order. It would make sense to use weakrefs, but CPython doesn't do that
    private final PDict subClasses;
    @CompilationFinal private Shape instanceShape;
    private TruffleString name;
    private TruffleString qualName;

    /**
     * Access using methods in {@link SpecialMethodSlot}.
     *
     * @see SpecialMethodSlot
     */
    Object[] specialMethodSlots;

    private TpSlots tpSlots;

    @CompilationFinal protected long methodsFlags = 0L;

    /** {@code true} if the MRO contains a native class. */
    private final boolean needsNativeAllocation;
    @CompilationFinal private boolean mroInitialized = false;

    public PTuple mroStore;
    public PTuple basesTuple;

    @TruffleBoundary
    protected PythonManagedClass(PythonLanguage lang, Object typeClass, Shape classShape, Shape instanceShape, TruffleString name, Object base, PythonAbstractClass[] baseClasses, TpSlots slots) {
        this(lang, typeClass, classShape, instanceShape, name, true, true, base, baseClasses, slots);
    }

    @TruffleBoundary
    @SuppressWarnings("this-escape")
    protected PythonManagedClass(PythonLanguage lang, Object typeClass, Shape classShape, Shape instanceShape, TruffleString name, boolean invokeMro, boolean initDocAttr,
                    Object base, PythonAbstractClass[] baseClasses, TpSlots slots) {
        super(typeClass, classShape);
        this.name = name;
        this.qualName = name;
        this.base = base;
        this.tpSlots = slots;

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

        this.subClasses = PythonObjectFactory.getUncached().createDict();
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
        for (PythonAbstractClass subclass : GetSubclassesAsArrayNode.executeUncached(this)) {
            if (subclass != null) {
                subclass.lookupChanged();
            }
        }
    }

    public final TpSlots getTpSlots() {
        return tpSlots;
    }

    public final void setTpSlots(TpSlots tpSlots) {
        this.tpSlots = tpSlots;
    }

    public final Shape getInstanceShape() {
        return instanceShape;
    }

    Object getBase() {
        return base;
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
    public void setAttribute(TruffleString key, Object value) {
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
        if (TpSlots.canBeSpecialMethod(key, CodePointLengthNode.getUncached(), CodePointAtIndexNode.getUncached())) {
            if (this.tpSlots != null) {
                // This is called during type instantiation from copyDictSlots when the tp slots are
                // not initialized yet
                TpSlots.updateSlot(this, key);
            }
            // TODO: will be removed:
            SpecialMethodSlot slot = SpecialMethodSlot.findSpecialSlotUncached(key);
            if (slot != null) {
                SpecialMethodSlot.fixupSpecialMethodSlot(this, slot, value);
            }
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
                    Object nativeBase = PythonToNativeNodeGen.getUncached().execute(base);
                    PCallCapiFunction.getUncached().call(NativeCAPISymbol.FUN_TRUFFLE_CHECK_TYPE_READY, nativeBase);
                }
                GetSubclassesNode.unsafeAddSubclass(base, this);
            }
        }
    }

    @TruffleBoundary
    public final void setBases(Object newBaseClass, PythonAbstractClass[] newBaseClasses) {
        Object oldBase = getBase();
        PythonAbstractClass[] oldBaseClasses = getBaseClasses();
        PythonAbstractClass[] oldMRO = (PythonAbstractClass[]) this.methodResolutionOrder.getInternalArray();

        PythonAbstractClass[] subclassesArray = GetSubclassesAsArrayNode.executeUncached(this);
        PythonAbstractClass[][] oldSubClasssMROs = new PythonAbstractClass[subclassesArray.length][];
        for (int i = 0; i < subclassesArray.length; i++) {
            PythonAbstractClass scls = subclassesArray[i];
            if (scls instanceof PythonManagedClass) {
                oldSubClasssMROs[i] = (PythonAbstractClass[]) ((PythonManagedClass) scls).methodResolutionOrder.getInternalArray();
            }
        }

        try {
            // for what follows see also typeobject.c#type_set_bases()
            this.base = newBaseClass;
            this.baseClasses = newBaseClasses;
            this.methodResolutionOrder.lookupChanged();
            this.setMRO(ComputeMroNode.doSlowPath(this));

            for (PythonAbstractClass scls : subclassesArray) {
                if (scls instanceof PythonManagedClass pmc) {
                    pmc.methodResolutionOrder.lookupChanged();
                    pmc.setMRO(ComputeMroNode.doSlowPath(scls));
                }
            }
        } catch (PException pe) {
            // undo
            if (this.baseClasses == newBaseClasses) {
                // take no action if bases were replaced through reentrance
                // revert only if set in this call
                // e.g. the mro() call might have manipulated __bases__
                this.base = oldBase;
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

        if (this.baseClasses == newBaseClasses) {
            // take no action if bases were replaced through reentrance
            boolean isCtxInitialized = PythonContext.get(null).isInitialized();
            for (PythonAbstractClass base : oldBaseClasses) {
                if (base instanceof PythonManagedClass) {
                    if (isCtxInitialized) {
                        GetSubclassesNode.executeUncached(base).delItem(this);
                    } else {
                        // slots aren't populated yet during context initialization
                        GetSubclassesNode.unsafeRemoveSubclass(base, this);
                    }
                }
            }
            for (PythonAbstractClass base : newBaseClasses) {
                if (base instanceof PythonManagedClass) {
                    if (isCtxInitialized) {
                        GetSubclassesNode.executeUncached(base).setItem(this, this);
                    } else {
                        // slots aren't populated yet during context initialization
                        GetSubclassesNode.unsafeAddSubclass(base, this);
                    }
                }
            }
        }
    }

    final PDict getSubClasses() {
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
