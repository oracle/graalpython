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

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef;
import com.oracle.graal.python.builtins.objects.cext.hpy.HPyTypeExtra;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromDynamicObjectNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.interop.PForeignToPTypeNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.sequence.storage.MroSequenceStorage;
import com.oracle.graal.python.util.SuppressFBWarnings;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.utilities.TruffleWeakReference;

/**
 * Mutable class.
 */
@ExportLibrary(InteropLibrary.class)
public final class PythonClass extends PythonManagedClass {

    private static final int MRO_SUBTYPES_MAX = 64;
    private static final int MRO_SHAPE_INVALIDATIONS_MAX = 5;

    public HPyTypeExtra hPyTypeExtra;

    private final AtomicReference<Assumption> slotsFinalAssumption = new AtomicReference<>();
    private MroShape mroShape;
    /**
     * Array of all classes that contain this class in their MRO and that have non-null mroShape,
     * i.e., classes whose mro shape depends on this class. Including this class itself as long as
     * it is in its own MRO. The size of this array is bounded by {@link #MRO_SUBTYPES_MAX}. This
     * array may be over-allocated and padded with nulls at the end.
     */
    private TruffleWeakReference<PythonClass>[] mroShapeSubTypes;
    private byte mroShapeInvalidationsCount;

    public PythonClass(PythonLanguage lang, Object typeClass, Shape classShape, TruffleString name, Object base, PythonAbstractClass[] baseClasses) {
        super(lang, typeClass, classShape, null, name, base, baseClasses);
    }

    public PythonClass(PythonLanguage lang, Object typeClass, Shape classShape, TruffleString name, boolean invokeMro, Object base, PythonAbstractClass[] baseClasses) {
        super(lang, typeClass, classShape, null, name, invokeMro, false, base, baseClasses);
    }

    public Assumption getSlotsFinalAssumption() {
        Assumption result = slotsFinalAssumption.get();
        if (result == null) {
            result = Truffle.getRuntime().createAssumption("slots");
            if (!slotsFinalAssumption.compareAndSet(null, result)) {
                result = slotsFinalAssumption.get();
            }
        }
        return result;
    }

    public void invalidateSlotsFinalAssumption() {
        Assumption assumption = slotsFinalAssumption.get();
        if (assumption != null) {
            assumption.invalidate();
        }
    }

    @Override
    @TruffleBoundary
    @SuppressFBWarnings(value = "UR_UNINIT_READ_CALLED_FROM_SUPER_CONSTRUCTOR")
    public void setAttribute(Object key, Object value) {
        if (slotsFinalAssumption != null) {
            // It is OK when slotsFinalAssumption is null during the super ctor call
            invalidateSlotsFinalAssumption();
        }
        super.setAttribute(key, value);
        invalidateMroShapeSubTypes();
    }

    @ExportMessage(library = InteropLibrary.class)
    @SuppressWarnings("static-method")
    boolean isMetaObject() {
        return true;
    }

    @ExportMessage
    boolean isMetaInstance(Object instance,
                    @Bind("$node") Node inliningTarget,
                    @Cached GetClassNode getClassNode,
                    @Cached PForeignToPTypeNode convert,
                    @Cached IsSubtypeNode isSubtype,
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            return isSubtype.execute(getClassNode.execute(inliningTarget, convert.executeConvert(instance)), this);
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    String getMetaSimpleName(@Exclusive @Cached GilNode gil,
                    @Shared("ts2js") @Cached TruffleString.ToJavaStringNode toJavaStringNode) {
        boolean mustRelease = gil.acquire();
        try {
            return toJavaStringNode.execute(getName());
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    String getMetaQualifiedName(@Exclusive @Cached GilNode gil,
                    @Shared("ts2js") @Cached TruffleString.ToJavaStringNode toJavaStringNode) {
        boolean mustRelease = gil.acquire();
        try {
            return toJavaStringNode.execute(getQualName());
        } finally {
            gil.release(mustRelease);
        }
    }

    /*
     * N.b.: (tfel): This method is used to cache the source section of the first defined attribute
     * that has a source section. This isn't precisely the classes definition location, but it is
     * close. We can safely cache this regardless of any later shape changes or redefinitions,
     * because this is best-effort only anyway. If it is called early, it is very likely we're
     * getting some location near the actual definition. If it is called late, and potentially after
     * some monkey-patching, we'll get some other source location.
     */
    protected static SourceSection findSourceSection(PythonManagedClass self) {
        for (Object key : self.getShape().getKeys()) {
            if (key instanceof TruffleString) {
                Object value = ReadAttributeFromDynamicObjectNode.getUncached().execute(self, key);
                InteropLibrary uncached = InteropLibrary.getFactory().getUncached();
                if (uncached.hasSourceLocation(value)) {
                    try {
                        return uncached.getSourceLocation(value);
                    } catch (UnsupportedMessageException e) {
                        // should not happen due to hasSourceLocation check
                    }
                }
            }
        }
        return null;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    protected SourceSection getSourceLocation(
                    @Exclusive @Cached GilNode gil,
                    @Bind("gil.acquire()") boolean mustRelease,
                    @Shared("src") @Cached(value = "findSourceSection(this)", allowUncached = true, neverDefault = false) SourceSection section) throws UnsupportedMessageException {
        try {
            if (section != null) {
                return section;
            } else {
                throw UnsupportedMessageException.create();
            }
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    protected boolean hasSourceLocation(
                    @Exclusive @Cached GilNode gil,
                    @Bind("gil.acquire()") boolean mustRelease,
                    @Shared("src") @Cached(value = "findSourceSection(this)", allowUncached = true, neverDefault = false) SourceSection section) {
        try {
            return section != null;
        } finally {
            gil.release(mustRelease);
        }
    }

    @Override
    public void setMRO(PythonAbstractClass[] mro) {
        super.setMRO(mro);
        mroShape = null;
        invalidateMroShapeSubTypes();
    }

    public void setMRO(PythonAbstractClass[] mro, PythonLanguage language) {
        super.setMRO(mro);
        if (!language.isSingleContext()) {
            mroShape = null;
            invalidateMroShapeSubTypes();
        }
    }

    public void setDictHiddenProp(Node inliningTarget, DynamicObjectLibrary dylib, InlinedBranchProfile hasMroShapeProfile, Object value) {
        dylib.setShapeFlags(this, dylib.getShapeFlags(this) | HAS_MATERIALIZED_DICT);
        dylib.put(this, DICT, value);
        if (mroShapeSubTypes != null) {
            hasMroShapeProfile.enter(inliningTarget);
            invalidateMroShapeSubTypes();
        }
    }

    public void makeStaticBase(DynamicObjectLibrary dylib) {
        dylib.setShapeFlags(this, dylib.getShapeFlags(this) | IS_STATIC_BASE);
    }

    public boolean isStaticBase(DynamicObjectLibrary dylib) {
        return (dylib.getShapeFlags(this) & IS_STATIC_BASE) != 0;
    }

    public MroShape getMroShape() {
        return mroShape;
    }

    public void initializeMroShape(PythonLanguage language) {
        assert mroShapeSubTypes == null;
        assert mroShape == null;
        if (!language.isSingleContext()) {
            reinitializeMroShape(language);
        }
    }

    @SuppressWarnings("unchecked")
    @TruffleBoundary
    private void reinitializeMroShape(PythonLanguage language) {
        MroSequenceStorage mro = getMethodResolutionOrder();
        mroShape = MroShape.create(mro, language);
        if (mroShape != null) {
            // add this class as a subtype of all classes in the mro (including itself)
            mroLoop: for (int mroIdx = 0; mroIdx < mro.length(); mroIdx++) {
                PythonManagedClass managedClass = (PythonManagedClass) mro.getItemNormalized(mroIdx);
                if (managedClass instanceof PythonBuiltinClass) {
                    // builtin classes are assumed immutable, so we do not need to register in their
                    // mro subtypes array (in fact they do not have such array)
                    continue;
                }
                PythonClass klass = (PythonClass) managedClass;
                TruffleWeakReference<PythonClass>[] subTypes = klass.mroShapeSubTypes;
                if (subTypes == null) {
                    klass.mroShapeSubTypes = (TruffleWeakReference<PythonClass>[]) new TruffleWeakReference<?>[8];
                    klass.mroShapeSubTypes[0] = new TruffleWeakReference<>(this);
                    continue;
                }
                for (int subTypesIdx = 0; subTypesIdx < subTypes.length; subTypesIdx++) {
                    if (subTypes[subTypesIdx] == null) {
                        subTypes[subTypesIdx] = new TruffleWeakReference<>(this);
                        continue mroLoop;
                    } else if (subTypes[subTypesIdx].get() == this) {
                        continue mroLoop;
                    }
                }
                if (subTypes.length >= MRO_SUBTYPES_MAX) {
                    mroShape = null;
                    mroShapeSubTypes = null;
                    break;
                } else {
                    klass.mroShapeSubTypes = Arrays.copyOf(subTypes, subTypes.length * 2);
                    klass.mroShapeSubTypes[subTypes.length] = new TruffleWeakReference<>(this);
                }
            }
        }
    }

    public boolean hasMroShapeSubTypes() {
        return mroShapeSubTypes != null;
    }

    @Override
    public boolean canSkipOnAttributeUpdate(TruffleString key, @SuppressWarnings("unused") Object newValue, TruffleString.CodePointLengthNode codePointLengthNode,
                    TruffleString.CodePointAtIndexNode codePointAtIndexNode) {
        return super.canSkipOnAttributeUpdate(key, newValue, codePointLengthNode, codePointAtIndexNode) && mroShapeSubTypes == null;
    }

    @TruffleBoundary
    @Override
    public void onAttributeUpdate(TruffleString key, Object newValue) {
        super.onAttributeUpdate(key, newValue);
        if (hasMroShapeSubTypes()) {
            if (newValue == PNone.NO_VALUE || mroShapeInvalidationsCount >= MRO_SHAPE_INVALIDATIONS_MAX) {
                // Any NO_VALUE means that we cannot rely on Shapes anymore, because they do not
                // reflect the actual properties
                invalidateMroShapeSubTypes();
            } else {
                mroShapeInvalidationsCount++;
                updateMroShapeSubTypes(PythonLanguage.get(null));
            }
        }
    }

    private void invalidateMroShapeSubTypes() {
        // Note: intentionally not a TruffleBoundary
        if (hasMroShapeSubTypes()) {
            for (WeakReference<PythonClass> subTypeRef : mroShapeSubTypes) {
                if (subTypeRef == null) {
                    break;
                }
                PythonClass subType = subTypeRef.get();
                if (subType != null) {
                    subType.mroShape = null;
                }
            }
            mroShapeSubTypes = null;
        }
    }

    @TruffleBoundary
    private void updateMroShapeSubTypes(PythonLanguage lang) {
        if (hasMroShapeSubTypes()) {
            for (WeakReference<PythonClass> subTypeRef : mroShapeSubTypes) {
                if (subTypeRef == null) {
                    break;
                }
                PythonClass subType = subTypeRef.get();
                if (subType != null) {
                    subType.mroShape = MroShape.create(subType.getMethodResolutionOrder(), lang);
                }
            }
        }
    }

    /**
     * Can be used to update MRO shapes in inheritance hierarchy of a builtin.
     */
    @TruffleBoundary
    static void updateMroShapeSubTypes(PythonBuiltinClass klass) {
        ArrayDeque<Object> toProcess = new ArrayDeque<>();
        toProcess.add(klass);
        PythonLanguage lang = PythonLanguage.get(null);
        while (toProcess.size() > 0) {
            Object next = toProcess.pop();
            if (next instanceof PythonClass) {
                ((PythonClass) next).updateMroShapeSubTypes(lang);
            } else {
                toProcess.addAll(((PythonBuiltinClass) next).getSubClasses());
            }
        }
    }

    public long getBasicSize() {
        return hPyTypeExtra != null ? hPyTypeExtra.basicSize : -1;
    }

    public long getItemSize() {
        return hPyTypeExtra != null ? hPyTypeExtra.itemSize : -1;
    }

    public Object getHPyDestroyFunc() {
        if (hPyTypeExtra != null) {
            return hPyTypeExtra.hpyDestroyFunc;
        }
        return null;
    }

    public Object getTpName() {
        return hPyTypeExtra != null ? hPyTypeExtra.tpName : null;
    }

    public long getFlags() {
        return hPyTypeExtra != null ? hPyTypeExtra.flags : 0;
    }

    public int getBuiltinShape() {
        return hPyTypeExtra != null ? hPyTypeExtra.builtinShape : GraalHPyDef.HPyType_BUILTIN_SHAPE_LEGACY;
    }

    public Object getHPyDefaultCallFunc() {
        return hPyTypeExtra != null ? hPyTypeExtra.defaultCallFunc : null;
    }

    public long getHPyVectorcallOffset() {
        return hPyTypeExtra != null ? hPyTypeExtra.vectorcallOffset : Long.MIN_VALUE;
    }

    public void setHPyDestroyFunc(Object destroyFunc) {
        hPyTypeExtra.hpyDestroyFunc = destroyFunc;
    }

    public void setHPyDefaultCallFunc(Object defaultCallFunc) {
        hPyTypeExtra.defaultCallFunc = defaultCallFunc;
    }

    public void setHPyVectorcallOffset(int vectorcallOffset) {
        hPyTypeExtra.vectorcallOffset = vectorcallOffset;
    }

    public void setHPyTypeExtra(HPyTypeExtra hpyTypeExtra) {
        this.hPyTypeExtra = hpyTypeExtra;
    }

    public boolean isHPyType() {
        return hPyTypeExtra != null;
    }
}
