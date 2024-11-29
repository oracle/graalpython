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

import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.lib.PyObjectReprAsTruffleStringNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.interop.PForeignToPTypeNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.utilities.TriState;

/**
 * A Python built-in class that is immutable.
 */
@ExportLibrary(InteropLibrary.class)
public final class PythonBuiltinClass extends PythonManagedClass {
    private final PythonBuiltinClassType type;

    @TruffleBoundary
    public PythonBuiltinClass(PythonLanguage lang, PythonBuiltinClassType builtinClass, PythonAbstractClass base) {
        super(lang, builtinClass.getType(), builtinClass.getType().getInstanceShape(lang), builtinClass.getInstanceShape(lang), builtinClass.getName(), base, new PythonAbstractClass[]{base},
                        builtinClass.getSlots());
        this.type = builtinClass;
        this.methodsFlags = type.getMethodsFlags();
    }

    @Override
    public void setAttribute(TruffleString name, Object value) {
        CompilerAsserts.neverPartOfCompilation();
        if (!PythonContext.get(null).isCoreInitialized()) {
            setAttributeUnsafe(name, value);
        } else {
            throw PRaiseNode.raiseUncached(null, TypeError, ErrorMessages.CANT_SET_ATTRIBUTE_R_OF_IMMUTABLE_TYPE_N, PyObjectReprAsTruffleStringNode.executeUncached(name), this);
        }
    }

    /**
     * Modify attributes in an unsafe way, should only use when initializing.
     */
    public void setAttributeUnsafe(TruffleString name, Object value) {
        super.setAttribute(name, value);
    }

    public PythonBuiltinClassType getType() {
        return type;
    }

    @TruffleBoundary
    @Override
    public void onAttributeUpdate(TruffleString key, Object newValue) {
        assert !PythonContext.get(null).isCoreInitialized();
        // Ideally, startup code should not create ASTs that rely on assumptions of props of
        // builtins. So there should be no assumptions to invalidate yet
        assert !getMethodResolutionOrder().invalidateAttributeInMROFinalAssumptions(key);
        assert checkSpecialMethodUpdate(key, newValue);
        SpecialMethodSlot slot = SpecialMethodSlot.findSpecialSlotUncached(key);
        if (slot != null) {
            SpecialMethodSlot.fixupSpecialMethodSlot(this, slot, newValue);
        }
        // NO_VALUE changes MRO lookup results without actually changing any Shapes in the MRO, this
        // can prevent some optimizations, so it is best to avoid any code that triggers such code
        // paths during initialization
        assert newValue != PNone.NO_VALUE;
        PythonClass.updateMroShapeSubTypes(this);
    }

    private static boolean checkSpecialMethodUpdate(TruffleString key, Object newValue) {
        // We disallow Python based slots for builtins, so that we can always satisfy slot lookup
        // only with PythonBuiltinClassType#slots
        // TODO: change to the commented out line below once all @Builtin are converted to @Slot
        // assert ... || (newValue instanceof PBuiltinFunction pbf && pbf.getSlot() != null);
        assert !TpSlots.isSpecialMethod(key) || (newValue instanceof PBuiltinFunction pbf);
        return true;
    }

    @ExportMessage(library = InteropLibrary.class)
    @SuppressWarnings("static-method")
    boolean isMetaObject() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isMetaInstance(Object instance,
                    @Bind("$node") Node inliningTarget,
                    @Cached GetClassNode getClassNode,
                    @Shared("convert") @Cached PForeignToPTypeNode convert,
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
            return toJavaStringNode.execute(type.getName());
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    String getMetaQualifiedName(@Exclusive @Cached GilNode gil,
                    @Shared("ts2js") @Cached TruffleString.ToJavaStringNode toJavaStringNode) {
        boolean mustRelease = gil.acquire();
        try {
            return toJavaStringNode.execute(type.getPrintName());
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    @ImportStatic(PGuards.class)
    static class IsIdenticalOrUndefined {
        @Specialization
        static TriState doPBCT(PythonBuiltinClass self, PythonBuiltinClassType other) {
            return self.getType() == other ? TriState.TRUE : TriState.FALSE;
        }

        @Specialization(guards = "!isPythonBuiltinClassType(other)")
        static TriState doOther(PythonBuiltinClass self, Object other,
                        @Shared("convert") @Cached PForeignToPTypeNode convert,
                        @CachedLibrary(limit = "3") InteropLibrary otherLib,
                        @Cached IsNode isNode,
                        @Exclusive @Cached GilNode gil) {
            return self.isIdenticalOrUndefined(other, convert, otherLib, isNode, gil);
        }
    }
}
