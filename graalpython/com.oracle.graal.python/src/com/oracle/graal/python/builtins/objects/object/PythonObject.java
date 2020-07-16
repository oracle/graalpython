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
package com.oracle.graal.python.builtins.objects.object;

import java.util.ArrayList;
import java.util.List;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.common.PHashingCollection;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToDynamicObjectNode;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.HiddenKey;
import com.oracle.truffle.api.object.Shape;

@ExportLibrary(PythonObjectLibrary.class)
public class PythonObject extends PythonAbstractObject {
    public static final HiddenKey DICT = new HiddenKey("ob_dict");
    protected static final HiddenKey CLASS = new HiddenKey("ob_type");
    private static final byte CLASS_CHANGED_FLAG = 1;

    private final Object initialPythonClass;
    public final DynamicObject storage;

    public PythonObject(Object pythonClass, DynamicObject storage) {
        assert pythonClass != null;
        assert consistentStorage(pythonClass, storage);
        this.initialPythonClass = pythonClass;
        this.storage = storage;
    }

    private static boolean consistentStorage(Object pythonClass, DynamicObject storage) {
        DynamicObjectLibrary dylib = DynamicObjectLibrary.getUncached();
        Object constantClass = dylib.getOrDefault(storage, CLASS, null);
        if (constantClass == null) {
            return true;
        }
        if (constantClass instanceof PythonBuiltinClass) {
            constantClass = ((PythonBuiltinClass) constantClass).getType();
        }
        return constantClass == (pythonClass instanceof PythonBuiltinClass ? ((PythonBuiltinClass) pythonClass).getType() : pythonClass);
    }

    @ExportMessage
    public void setLazyPythonClass(Object cls,
                    @CachedLibrary("this.storage") DynamicObjectLibrary dylib) {
        // n.b.: the CLASS property is usually a constant property that is stored in the shape in
        // single-context-mode. If we change it for the first time, there's an implicit shape
        // transition
        dylib.setShapeFlags(storage, dylib.getShapeFlags(storage) | CLASS_CHANGED_FLAG);
        dylib.put(storage, CLASS, cls);
    }

    @ExportMessage
    public static class GetLazyPythonClass {
        public static boolean hasInitialClass(PythonObject self, DynamicObjectLibrary dylib) {
            return (dylib.getShapeFlags(self.storage) & CLASS_CHANGED_FLAG) == 0;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"klass != null", "self.storage.getShape() == cachedShape", "hasInitialClass"}, limit = "1")
        public static Object getConstantClass(PythonObject self,
                        @CachedLibrary("self.storage") DynamicObjectLibrary dylib,
                        @Cached("hasInitialClass(self, dylib)") boolean hasInitialClass,
                        @Cached("self.storage.getShape()") Shape cachedShape,
                        @Cached("getPythonClass(self, dylib)") Object klass) {
            return klass;
        }

        @Specialization(replaces = "getConstantClass")
        public static Object getPythonClass(PythonObject self,
                        @CachedLibrary("self.storage") DynamicObjectLibrary dylib) {
            return dylib.getOrDefault(self.storage, CLASS, self.initialPythonClass);
        }
    }

    public final DynamicObject getStorage() {
        return storage;
    }

    @SuppressWarnings("deprecation")
    @TruffleBoundary
    public final Object getAttribute(Object key) {
        return DynamicObjectLibrary.getUncached().getOrDefault(getStorage(), key, PNone.NO_VALUE);
    }

    @SuppressWarnings("deprecation")
    @TruffleBoundary
    public void setAttribute(Object name, Object value) {
        CompilerAsserts.neverPartOfCompilation();
        DynamicObjectLibrary.getUncached().putWithFlags(getStorage(), name, value, 0);
    }

    @SuppressWarnings("deprecation")
    @TruffleBoundary
    public List<String> getAttributeNames() {
        ArrayList<String> keyList = new ArrayList<>();
        for (Object o : getStorage().getShape().getKeyList()) {
            if (o instanceof String && DynamicObjectLibrary.getUncached().getOrDefault(getStorage(), o, PNone.NO_VALUE) != PNone.NO_VALUE) {
                keyList.add((String) o);
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
        Object storedPythonClass = DynamicObjectLibrary.getUncached().getOrDefault(storage, CLASS, null);
        if (storedPythonClass instanceof PythonManagedClass) {
            className = ((PythonManagedClass) storedPythonClass).getQualName();
        } else if (storedPythonClass instanceof PythonBuiltinClassType) {
            className = ((PythonBuiltinClassType) storedPythonClass).getName();
        } else if (PGuards.isNativeClass(storedPythonClass)) {
            className = "native";
        }
        return "<" + className + " object at 0x" + Integer.toHexString(hashCode()) + ">";
    }

    @ExportMessage
    public boolean hasDict(@CachedLibrary("this.storage") DynamicObjectLibrary dylib) {
        return dylib.containsKey(storage, DICT);
    }

    @ExportMessage
    public PHashingCollection getDict(@CachedLibrary("this.storage") DynamicObjectLibrary dylib) {
        return (PHashingCollection) dylib.getOrDefault(storage, DICT, null);
    }

    @ExportMessage
    public final void setDict(PHashingCollection dict,
                    @Cached WriteAttributeToDynamicObjectNode writeNode) {
        writeNode.execute(storage, DICT, dict);
    }

    private static final Shape emptyShape = Shape.newBuilder().
        allowImplicitCastIntToDouble(false).
        allowImplicitCastIntToLong(true).
        shapeFlags(0).
        propertyAssumptions(true).
        build();

    public static Shape freshShape(Object klass) {
        return Shape.newBuilder(emptyShape).addConstantProperty(CLASS, klass, 0).build();
    }

    public static Shape freshShape() {
        return emptyShape;
    }
}
