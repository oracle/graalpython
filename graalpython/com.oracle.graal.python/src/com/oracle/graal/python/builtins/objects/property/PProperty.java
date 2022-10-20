/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.property;

import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.truffle.api.object.Shape;

/**
 * A property object (like CPython's {@code descobject.c: propertyobject}).
 */
public final class PProperty extends PythonBuiltinObject {
    private Object fget;
    private Object fset;
    private Object fdel;
    private Object doc;
    private Object propertyName;
    private boolean getterDoc;

    public PProperty(Object cls, Shape instanceShape) {
        super(cls, instanceShape);
    }

    public PProperty(Object cls, Shape instanceShape, Object fget, Object fset, Object fdel, Object doc) {
        super(cls, instanceShape);
        this.fget = fget;
        this.fset = fset;
        this.fdel = fdel;
        this.doc = doc;
    }

    public Object getFget() {
        return fget;
    }

    void setFget(Object fget) {
        this.fget = fget;
    }

    public Object getFset() {
        return fset;
    }

    void setFset(Object fset) {
        this.fset = fset;
    }

    public Object getFdel() {
        return fdel;
    }

    void setFdel(Object fdel) {
        this.fdel = fdel;
    }

    public Object getDoc() {
        return doc;
    }

    public void setDoc(Object doc) {
        this.doc = doc;
    }

    public boolean getGetterDoc() {
        return getterDoc;
    }

    public void setGetterDoc(boolean getterDoc) {
        this.getterDoc = getterDoc;
    }

    public Object getPropertyName() {
        return propertyName;
    }

    public void setPropertyName(Object propertyName) {
        this.propertyName = propertyName;
    }
}
