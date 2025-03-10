/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.builtins.objects.getsetdescriptor;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.BoundBuiltinCallable;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.strings.TruffleString;

public final class GetSetDescriptor extends PythonBuiltinObject implements BoundBuiltinCallable<GetSetDescriptor> {
    private final Object get;
    private final Object set;
    private final PString name;
    private final boolean allowsDelete;
    private final Object type;

    public GetSetDescriptor(PythonLanguage lang, Object get, Object set, TruffleString name, Object type) {
        this(lang, get, set, name, type, false);
    }

    public GetSetDescriptor(Object clazz, Shape shape, Object get, Object set, TruffleString name, Object type, boolean allowsDelete) {
        super(clazz, shape);
        // every descriptor has a '__doc__' attribute
        setAttribute(SpecialAttributeNames.T___DOC__, PNone.NONE);
        this.get = get;
        this.set = set;
        this.name = PythonUtils.toPString(name);
        this.type = type;
        this.allowsDelete = allowsDelete;
    }

    public GetSetDescriptor(PythonLanguage lang, Object get, Object set, TruffleString name, Object type, boolean allowsDelete) {
        this(PythonBuiltinClassType.GetSetDescriptor, PythonBuiltinClassType.GetSetDescriptor.getInstanceShape(lang), get, set, name, type, allowsDelete);
    }

    public Object getGet() {
        return get;
    }

    public Object getSet() {
        return set;
    }

    public TruffleString getName() {
        return name.getMaterialized();
    }

    public PString getCApiName() {
        return name;
    }

    public Object getType() {
        return type;
    }

    public boolean allowsDelete() {
        return allowsDelete;
    }

    public GetSetDescriptor boundToObject(PythonBuiltinClassType klass, PythonLanguage language) {
        if (klass == type) {
            return this;
        } else {
            return PFactory.createGetSetDescriptor(language, get, set, getName(), klass, allowsDelete);
        }
    }
}
