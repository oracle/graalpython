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
package com.oracle.graal.python.builtins.objects.object;

import static com.oracle.graal.python.nodes.HiddenAttributes.CLASS;
import static com.oracle.graal.python.nodes.truffle.TruffleStringMigrationHelpers.assertNoJavaString;

import java.util.ArrayList;
import java.util.List;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.nodes.HiddenAttributes;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.HiddenKey;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.strings.TruffleString;

public class PythonObject extends PythonAbstractObject {
    public static final HiddenKey DICT = HiddenAttributes.DICT;
    public static final byte CLASS_CHANGED_FLAG = 0b1;
    /**
     * Indicates that the object doesn't allow {@code __dict__}, but may have slots
     */
    public static final byte HAS_SLOTS_BUT_NO_DICT_FLAG = 0b10;
    /**
     * Indicates that the shape has some properties that may contain {@link PNone#NO_VALUE} and
     * therefore the shape itself is not enough to resolve any lookups.
     */
    public static final byte HAS_NO_VALUE_PROPERTIES = 0b100;
    /**
     * Indicates that the object has a dict in the form of an actual dictionary
     */
    public static final byte HAS_MATERIALIZED_DICT = 0b1000;
    /**
     * Indicates that the object is a static base in the CPython's tp_new_wrapper sense.
     *
     * @see com.oracle.graal.python.nodes.function.builtins.WrapTpNew
     */
    public static final byte IS_STATIC_BASE = 0b10000;

    private final Object initialPythonClass;

    private Object[] hpyData;

    @SuppressWarnings("this-escape") // escapes in the assertion
    public PythonObject(Object pythonClass, Shape instanceShape) {
        super(instanceShape);
        assert pythonClass != null;
        assert consistentStorage(pythonClass);
        this.initialPythonClass = pythonClass;
    }

    private boolean consistentStorage(Object pythonClass) {
        DynamicObjectLibrary dylib = DynamicObjectLibrary.getUncached();
        Object constantClass = dylib.getOrDefault(this, CLASS, null);
        if (constantClass == null) {
            return true;
        }
        if (constantClass instanceof PythonBuiltinClass) {
            constantClass = ((PythonBuiltinClass) constantClass).getType();
        }
        if (constantClass instanceof PythonAbstractNativeObject && pythonClass instanceof PythonAbstractNativeObject) {
            return true;
        }
        return constantClass == (pythonClass instanceof PythonBuiltinClass ? ((PythonBuiltinClass) pythonClass).getType() : pythonClass);
    }

    public void setPythonClass(Object cls, DynamicObjectLibrary dylib) {
        // n.b.: the CLASS property is usually a constant property that is stored in the shape
        // in
        // single-context-mode. If we change it for the first time, there's an implicit shape
        // transition
        dylib.setShapeFlags(this, dylib.getShapeFlags(this) | CLASS_CHANGED_FLAG);
        dylib.put(this, CLASS, cls);
    }

    public void setDict(DynamicObjectLibrary dylib, PDict dict) {
        dylib.setShapeFlags(this, dylib.getShapeFlags(this) | HAS_MATERIALIZED_DICT);
        dylib.put(this, DICT, dict);
    }

    @NeverDefault
    public Object getInitialPythonClass() {
        return initialPythonClass;
    }

    public final DynamicObject getStorage() {
        return this;
    }

    @SuppressWarnings("deprecation")
    @TruffleBoundary
    public final Object getAttribute(Object key) {
        return DynamicObjectLibrary.getUncached().getOrDefault(getStorage(), assertNoJavaString(key), PNone.NO_VALUE);
    }

    @SuppressWarnings("deprecation")
    @TruffleBoundary
    public void setAttribute(Object nameObj, Object value) {
        Object name = assertNoJavaString(nameObj);
        assert name instanceof TruffleString || name instanceof HiddenKey : name.getClass().getSimpleName();
        CompilerAsserts.neverPartOfCompilation();
        DynamicObjectLibrary.getUncached().put(getStorage(), name, assertNoJavaString(value));
    }

    @SuppressWarnings("deprecation")
    @TruffleBoundary
    public List<TruffleString> getAttributeNames() {
        ArrayList<TruffleString> keyList = new ArrayList<>();
        for (Object o : getStorage().getShape().getKeyList()) {
            if (o instanceof TruffleString && DynamicObjectLibrary.getUncached().getOrDefault(getStorage(), o, PNone.NO_VALUE) != PNone.NO_VALUE) {
                keyList.add((TruffleString) o);
            }
        }
        return keyList;
    }

    @Override
    public int compareTo(Object o) {
        return this == o ? 0 : 1;
    }

    /**
     * Important: toString can be called from arbitrary locations, so it cannot do anything that may
     * execute Python code or rely on a context being available.
     *
     * The Python-level string representation functionality always needs to be implemented as
     * __repr__/__str__ builtins (which in turn can call toString if this already implements the
     * correct behavior).
     */
    @Override
    public String toString() {
        String className = "unknown";
        Object storedPythonClass = DynamicObjectLibrary.getUncached().getOrDefault(this, CLASS, null);
        if (storedPythonClass instanceof PythonManagedClass) {
            className = ((PythonManagedClass) storedPythonClass).getQualName().toJavaStringUncached();
        } else if (storedPythonClass instanceof PythonBuiltinClassType) {
            className = ((PythonBuiltinClassType) storedPythonClass).getName().toJavaStringUncached();
        } else if (PGuards.isNativeClass(storedPythonClass)) {
            className = "native";
        }
        return "<" + className + " object at 0x" + Integer.toHexString(hashCode()) + ">";
    }

    /* needed for some guards in exported messages of subclasses */
    public static int getCallSiteInlineCacheMaxDepth() {
        return PythonOptions.getCallSiteInlineCacheMaxDepth();
    }

    public final Object[] getHPyData() {
        return hpyData;
    }

    public final void setHPyData(Object[] hpyFields) {
        this.hpyData = hpyFields;
    }
}
