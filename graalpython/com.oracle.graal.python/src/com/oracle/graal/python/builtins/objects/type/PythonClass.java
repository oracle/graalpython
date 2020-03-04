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

import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromDynamicObjectNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.GetLazyClassNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

/**
 * Mutable class.
 */
@ExportLibrary(InteropLibrary.class)
public final class PythonClass extends PythonManagedClass {

    public PythonClass(LazyPythonClass typeClass, String name, PythonAbstractClass[] baseClasses) {
        super(typeClass, name, baseClasses);
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isMetaObject() {
        return true;
    }

    @ExportMessage
    boolean isMetaInstance(Object instance,
                    @Cached GetLazyClassNode getClass,
                    @Cached IsSubtypeNode isSubtype) {
        return isSubtype.execute(getClass.execute(instance), this);
    }

    @ExportMessage
    String getMetaSimpleName(
                    @Exclusive @Cached ReadAttributeFromDynamicObjectNode getName,
                    @Shared("castStr") @Cached CastToJavaStringNode castStr) {
        // n.b.: we're reading directly from the storage here, because this
        // method must not have side-effects, so even if there's a __dict__, we
        // cannot call its __getitem__
        String result = castStr.execute(getName.execute(getStorage(), SpecialAttributeNames.__NAME__));
        if (result == null) {
            return "unnamed-class";
        } else {
            return result;
        }
    }

    @ExportMessage
    String getMetaQualifiedName(
                    @Exclusive @Cached ReadAttributeFromDynamicObjectNode getName,
                    @Shared("castStr") @Cached CastToJavaStringNode castStr) {
        // n.b.: we're reading directly from the storage here, because this
        // method must not have side-effects, so even if there's a __dict__, we
        // cannot call its __getitem__
        String result = castStr.execute(getName.execute(getStorage(), SpecialAttributeNames.__QUALNAME__));
        if (result == null) {
            return "unnamed-class";
        } else {
            return result;
        }
    }
}
