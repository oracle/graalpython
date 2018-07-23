/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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

import static com.oracle.graal.python.runtime.exception.PythonErrorType.AttributeError;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Location;
import com.oracle.truffle.api.object.Property;
import com.oracle.truffle.api.object.Shape;

public class PythonObject extends PythonAbstractObject {
    protected final PythonClass pythonClass;
    protected final DynamicObject storage;
    private PDict dict;

    public PythonObject(PythonClass pythonClass) {
        assert pythonClass != null : getClass().getSimpleName();
        this.pythonClass = pythonClass;
        storage = pythonClass.getInstanceShape().newInstance();
    }

    public PythonObject(PythonClass pythonClass, Shape instanceShape) {
        if (pythonClass == null) {
            CompilerDirectives.transferToInterpreter();
            // special case for base type class
            assert this instanceof PythonBuiltinClass;
            this.pythonClass = (PythonClass) this;
        } else {
            this.pythonClass = pythonClass;
        }
        storage = instanceShape.newInstance();
    }

    public final PythonClass getPythonClass() {
        assert pythonClass != null;
        return pythonClass;
    }

    public final DynamicObject getStorage() {
        return storage;
    }

    /**
     * Does this object have an instance variable defined?
     */
    public final boolean isOwnAttribute(String name) {
        return getStorage().containsKey(name);
    }

    public final Location getOwnValidLocation(String attributeId) {
        if (!getStorage().getShape().isValid()) {
            getStorage().updateShape();
        }
        Property property = getStorage().getShape().getProperty(attributeId);
        if (property != null) {
            return property.getLocation();
        } else {
            return null;
        }
    }

    public PythonObject getValidStorageFullLookup(String attributeId) {
        PythonObject s = null;
        if (isOwnAttribute(attributeId)) {
            s = this;
        } else if (pythonClass != null) {
            s = pythonClass.getValidStorageFullLookup(attributeId);
        }
        return s;
    }

    @TruffleBoundary
    public Object getAttribute(String name) {
        // Find the storage location
        Location storageLocation = getOwnValidLocation(name);

        // Continue the look up in PythonType.
        if (storageLocation == null) {
            return pythonClass == null ? PNone.NO_VALUE : pythonClass.getAttribute(name);
        }

        return storageLocation.get(getStorage());
    }

    @TruffleBoundary
    public void setAttribute(Object name, Object value) {
        CompilerAsserts.neverPartOfCompilation();
        getStorage().define(name, value);
    }

    @TruffleBoundary
    public void deleteAttribute(String name) {
        // Find the storage location
        if (!getStorage().containsKey(name)) {
            throw PythonLanguage.getCore().raise(AttributeError, "%s object has no attribute %s", this, name);
        } else {
            getStorage().delete(name);
        }
    }

    @TruffleBoundary
    public List<String> getAttributeNames() {
        ArrayList<String> keyList = new ArrayList<>();
        for (Object o : getStorage().getShape().getKeyList()) {
            if (o instanceof String && getStorage().get(o) != PNone.NO_VALUE) {
                keyList.add((String) o);
            }
        }
        return keyList;
    }

    public Set<String> getAllAttributeNames() {
        Set<String> keyList = new LinkedHashSet<>();
        PythonClass[] methodResolutionOrder = this.getPythonClass().getMethodResolutionOrder();
        for (Object o : getStorage().getShape().getKeyList()) {
            if (o instanceof String) {
                keyList.add((String) o);
            }
        }
        for (PythonClass klass : methodResolutionOrder) {
            keyList.addAll(klass.getAttributeNames());
        }
        return keyList;
    }

    protected Map<String, Object> getAttributes() {
        final Map<String, Object> attributesMap = new HashMap<>();
        for (Property p : getStorage().getShape().getProperties()) {
            if (p.getKey() instanceof String) {
                attributesMap.put((String) p.getKey(), p.getLocation().get(getStorage()));
            }
        }
        return attributesMap;
    }

    public final PythonClass asPythonClass() {
        if (this instanceof PythonClass) {
            return (PythonClass) this;
        }

        return getPythonClass();
    }

    @Override
    public int compareTo(Object o) {
        return this.equals(o) ? 0 : 1;
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
        return "<" + pythonClass.getName() + " object at 0x" + Integer.toHexString(hashCode()) + ">";
    }

    /**
     * Returns the dictionary backed by {@link #storage} (only available for user objects).
     */
    public PDict getDict() {
        return dict;
    }

    public void setDict(PDict dict) {
        this.dict = dict;
    }
}
