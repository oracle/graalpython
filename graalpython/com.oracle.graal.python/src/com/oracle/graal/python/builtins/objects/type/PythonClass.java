/*
 * Copyright (c) 2017, 2026, Oracle and/or its affiliates.
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

import java.util.ArrayDeque;
import java.util.Arrays;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetSubclassesAsArrayNode;
import com.oracle.graal.python.nodes.HiddenAttr;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromPythonObjectNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.interop.PForeignToPTypeNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.util.SuppressFBWarnings;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Mutable class.
 */
@ExportLibrary(InteropLibrary.class)
public final class PythonClass extends PythonManagedClass {

    private static final int MRO_SHAPE_INVALIDATIONS_MAX = 5;

    /**
     * MroShape is only set if all base classes in MRO have mroShape set.
     * Most notably there must be no native type in MRO.
     */
    private MroShape mroShape;
    private byte mroShapeInvalidationsCount;

    public PythonClass(Node location, PythonLanguage lang, Object typeClass, Shape classShape, TruffleString name, Object base, PythonAbstractClass[] baseClasses) {
        super(location, lang, typeClass, classShape, null, name, base, baseClasses, null);
    }

    public PythonClass(Node location, PythonLanguage lang, Object typeClass, Shape classShape, TruffleString name, boolean invokeMro, Object base, PythonAbstractClass[] baseClasses) {
        super(location, lang, typeClass, classShape, null, name, invokeMro, false, base, baseClasses, null);
    }

    public void setTpSlots(TpSlots tpSlots) {
        this.tpSlots = tpSlots;
    }

    @Override
    @TruffleBoundary
    @SuppressFBWarnings(value = "UR_UNINIT_READ_CALLED_FROM_SUPER_CONSTRUCTOR")
    public void setAttribute(TruffleString key, Object value) {
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
                    @Bind Node inliningTarget,
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
            if (key instanceof TruffleString ts) {
                Object value = ReadAttributeFromPythonObjectNode.getUncached().execute(self, ts);
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

    public void setDictHiddenProp(Node inliningTarget, HiddenAttr.WriteNode writeNode, InlinedBranchProfile hasMroShapeProfile, Object value) {
        writeNode.execute(inliningTarget, this, HiddenAttr.DICT, value);
        if (mroShape != null) {
            hasMroShapeProfile.enter(inliningTarget);
            invalidateMroShapeSubTypes();
        }
    }

    public void makeStaticBase(DynamicObject.SetShapeFlagsNode setShapeFlagsNode) {
        setShapeFlagsNode.executeAdd(this, IS_STATIC_BASE);
    }

    public boolean isStaticBase() {
        return (getShape().getFlags() & IS_STATIC_BASE) != 0;
    }

    public MroShape getMroShape() {
        return mroShape;
    }

    public void initializeMroShape(PythonLanguage language) {
        assert mroShape == null;
        if (!language.isSingleContext()) {
            reinitializeMroShape(language);
        }
    }

    @SuppressWarnings("unchecked")
    @TruffleBoundary
    private void reinitializeMroShape(PythonLanguage language) {
        mroShape = MroShape.create(getMethodResolutionOrder(), language);
    }

    @TruffleBoundary
    @Override
    public void onAttributeUpdate(TruffleString key, Object newValue) {
        PythonAbstractClass[] allSubclasses = GetSubclassesAsArrayNode.executeRecursiveUncached(this);
        super.onAttributeUpdate(key, newValue, allSubclasses);
        if (mroShape == null || newValue == PNone.NO_VALUE || mroShapeInvalidationsCount >= MRO_SHAPE_INVALIDATIONS_MAX) {
            // Any NO_VALUE means that we cannot rely on Shapes anymore, because they do not
            // reflect the actual properties
            invalidateMroShapeSubTypes(allSubclasses);
        } else {
            mroShapeInvalidationsCount++;
            updateMroShapeSubTypes(PythonLanguage.get(null), allSubclasses);
        }
    }

    @TruffleBoundary
    private void invalidateMroShapeSubTypes() {
        mroShape = null;
        for (PythonAbstractClass subclass : GetSubclassesAsArrayNode.executeUncached(this)) {
            if (subclass instanceof PythonClass pc && pc.mroShape != null) {
                pc.invalidateMroShapeSubTypes();
            }
        }
    }

    private void invalidateMroShapeSubTypes(PythonAbstractClass[] subclasses) {
        // Note: intentionally not a TruffleBoundary
        mroShape = null;
        for (PythonAbstractClass subclass : subclasses) {
            if (subclass instanceof PythonClass pc && pc.mroShape != null) {
                pc.mroShape = null;
            }
        }
    }

    @TruffleBoundary
    private void updateMroShapeSubTypes(PythonLanguage lang) {
        for (PythonAbstractClass subclass : GetSubclassesAsArrayNode.executeUncached(this)) {
            if (subclass instanceof PythonClass pc) {
                pc.mroShape = MroShape.create(pc.getMethodResolutionOrder(), lang);
                pc.updateMroShapeSubTypes(lang);
            }
        }
    }

    private void updateMroShapeSubTypes(PythonLanguage lang, PythonAbstractClass[] subclasses) {
        mroShape = MroShape.create(getMethodResolutionOrder(), lang);
        for (PythonAbstractClass subclass : subclasses) {
            if (subclass instanceof PythonClass pc) {
                pc.mroShape = MroShape.create(pc.getMethodResolutionOrder(), lang);
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
        while (!toProcess.isEmpty()) {
            Object next = toProcess.pop();
            if (next instanceof PythonClass) {
                ((PythonClass) next).updateMroShapeSubTypes(lang);
            } else {
                toProcess.addAll(Arrays.asList(GetSubclassesAsArrayNode.executeUncached(next)));
            }
        }
    }
}
