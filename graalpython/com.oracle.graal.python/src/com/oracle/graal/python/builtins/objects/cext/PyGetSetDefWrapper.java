/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext;

import static com.oracle.graal.python.nodes.SpecialAttributeNames.__DOC__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__NAME__;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;

/**
 * Wraps a PythonObject to provide a native view with a shape like {@code PyGetSetDef}.
 */
@ExportLibrary(InteropLibrary.class)
@ExportLibrary(NativeTypeLibrary.class)
@ImportStatic(SpecialMethodNames.class)
public class PyGetSetDefWrapper extends PythonNativeWrapper {
    public static final String NAME = "name";
    public static final String DOC = "doc";

    public PyGetSetDefWrapper(PythonObject delegate) {
        super(delegate);
    }

    @ExportMessage
    protected boolean hasMembers() {
        return true;
    }

    @ExportMessage
    protected boolean isMemberReadable(String member) {
        switch (member) {
            case NAME:
            case DOC:
                return true;
            default:
                return false;
        }
    }

    @ExportMessage
    protected Object getMembers(@SuppressWarnings("unused") boolean includeInternal) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    protected Object readMember(String member,
                    @CachedLibrary("this") PythonNativeWrapperLibrary lib,
                    @Exclusive @Cached ReadFieldNode readFieldNode) {
        return readFieldNode.execute(lib.getDelegate(this), member);
    }

    @ImportStatic({SpecialMethodNames.class, PyGetSetDefWrapper.class})
    @GenerateUncached
    abstract static class ReadFieldNode extends Node {
        public abstract Object execute(Object delegate, String key);

        protected static boolean eq(String expected, String actual) {
            return expected.equals(actual);
        }

        @Specialization(guards = {"eq(NAME, key)"})
        Object getName(PythonObject object, @SuppressWarnings("unused") String key,
                        @Exclusive @Cached("key") @SuppressWarnings("unused") String cachedKey,
                        @Shared("getAttrNode") @Cached PythonAbstractObject.PInteropGetAttributeNode getAttrNode,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode,
                        @Shared("asCharPointerNode") @Cached CExtNodes.AsCharPointerNode asCharPointerNode) {
            Object doc = getAttrNode.execute(object, __NAME__);
            if (doc == PNone.NONE) {
                return toSulongNode.execute(PNone.NO_VALUE);
            } else {
                return asCharPointerNode.execute(doc);
            }
        }

        @Specialization(guards = {"eq(DOC, key)"})
        Object getDoc(PythonObject object, @SuppressWarnings("unused") String key,
                        @Exclusive @Cached("key") @SuppressWarnings("unused") String cachedKey,
                        @Shared("getAttrNode") @Cached PythonAbstractObject.PInteropGetAttributeNode getAttrNode,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode,
                        @Shared("asCharPointerNode") @Cached CExtNodes.AsCharPointerNode asCharPointerNode) {
            Object doc = getAttrNode.execute(object, __DOC__);
            if (doc == PNone.NONE) {
                return toSulongNode.execute(PNone.NO_VALUE);
            } else {
                return asCharPointerNode.execute(doc);
            }
        }
    }

    @ExportMessage
    protected boolean isMemberModifiable(String member) {
        return DOC.equals(member);
    }

    @ExportMessage
    protected boolean isMemberInsertable(String member) {
        return DOC.equals(member);
    }

    @ExportMessage
    protected void writeMember(String member, Object value,
                    @CachedLibrary("this") PythonNativeWrapperLibrary lib,
                    @Cached PythonAbstractObject.PInteropSetAttributeNode setAttrNode,
                    @Exclusive @Cached CExtNodes.FromCharPointerNode fromCharPointerNode) throws UnsupportedMessageException, UnknownIdentifierException {
        if (!DOC.equals(member)) {
            CompilerDirectives.transferToInterpreter();
            throw UnsupportedMessageException.create();
        }
        setAttrNode.execute(lib.getDelegate(this), SpecialAttributeNames.__DOC__, fromCharPointerNode.execute(value));
    }

    @ExportMessage
    protected boolean isMemberRemovable(@SuppressWarnings("unused") String member) {
        return false;
    }

    @ExportMessage
    protected void removeMember(@SuppressWarnings("unused") String member) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    protected boolean hasNativeType() {
        // TODO implement native type
        return false;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public Object getNativeType() {
        // TODO implement native type
        return null;
    }
}
