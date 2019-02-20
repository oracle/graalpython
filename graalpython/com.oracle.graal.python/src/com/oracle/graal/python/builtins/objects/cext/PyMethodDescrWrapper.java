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

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.NativeWrappers.PythonNativeWrapper;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallTernaryNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;

/**
 * Wraps a PythonObject to provide a native view with a shape like {@code PyMethodDescr}.
 */
@ExportLibrary(InteropLibrary.class)
public class PyMethodDescrWrapper extends PythonNativeWrapper {
    public static final String NAME = "ml_name";
    public static final String DOC = "ml_doc";

    public PyMethodDescrWrapper(PythonObject delegate) {
        super(delegate);
    }

    static boolean isInstance(TruffleObject o) {
        return o instanceof PyMethodDescrWrapper;
    }

    @ExportMessage
    @Override
    protected boolean hasMembers() {
        return true;
    }

    @ExportMessage
    @Override
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
    @Override
    protected Object getMembers(boolean includeInternal) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    protected Object readMember(String member,
                                @Cached.Exclusive @Cached(allowUncached = true) ReadFieldNode readFieldNode) {
        return readFieldNode.execute(this.getDelegate(), member);
    }

    @ImportStatic({SpecialMethodNames.class})
    abstract static class ReadFieldNode extends Node {
        public static final String NAME = PyMethodDescrWrapper.NAME;
        public static final String DOC = PyMethodDescrWrapper.DOC;

        public abstract Object execute(Object delegate, String key);

        protected boolean eq(String expected, String actual) {
            return expected.equals(actual);
        }

        @Specialization(guards = {"eq(NAME, key)"})
        Object getName(PythonObject object, @SuppressWarnings("unused") String key,
                       @Cached("key") @SuppressWarnings("unused") String cachedKey,
                       @Cached("create(__GETATTRIBUTE__)") LookupAndCallBinaryNode getAttrNode,
                       @Cached.Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode,
                       @Cached.Shared("asCharPointerNode") @Cached CExtNodes.AsCharPointer asCharPointerNode) {
            Object doc = getAttrNode.executeObject(object, SpecialAttributeNames.__NAME__);
            if (doc == PNone.NONE) {
                return toSulongNode.execute(PNone.NO_VALUE);
            } else {
                return asCharPointerNode.execute(doc);
            }
        }

        @Specialization(guards = {"eq(DOC, key)"})
        Object getDoc(PythonObject object, @SuppressWarnings("unused") String key,
                      @Cached("key") @SuppressWarnings("unused") String cachedKey,
                      @Cached("create()") ReadAttributeFromObjectNode getAttrNode,
                      @Cached.Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode,
                      @Cached.Shared("asCharPointerNode") @Cached CExtNodes.AsCharPointer asCharPointerNode) {
            Object doc = getAttrNode.execute(object, SpecialAttributeNames.__DOC__);
            if (doc instanceof PNone) {
                return toSulongNode.execute(PNone.NO_VALUE);
            } else {
                return asCharPointerNode.execute(doc);
            }
        }
    }

    @ExportMessage
    @Override
    protected boolean isMemberModifiable(String member) {
        return member.equals(DOC);
    }

    @ExportMessage
    @Override
    protected boolean isMemberInsertable(String member) {
        return member.equals(DOC);
    }

    @ExportMessage
    protected void writeMember(String member, Object value,
                               @Cached.Exclusive @Cached(allowUncached = true) WriteFieldNode writeFieldNode) {
        writeFieldNode.execute(this.getDelegate(), member, value);
    }

    @ExportMessage
    @Override
    protected boolean isMemberRemovable(String member) {
        return false;
    }

    @ExportMessage
    @Override
    protected void removeMember(String member) throws UnsupportedMessageException, UnknownIdentifierException {
        throw UnsupportedMessageException.create();
    }

    @ImportStatic({SpecialMethodNames.class, PGuards.class})
    abstract static class WriteFieldNode extends Node {
        public static final String DOC = PyMethodDescrWrapper.DOC;

        public abstract void execute(Object delegate, String key, Object value);

        protected boolean eq(String expected, String actual) {
            return expected.equals(actual);
        }

        @Specialization(guards = {"!isBuiltinMethod(object)", "eq(DOC, key)"})
        void getDoc(PythonObject object, @SuppressWarnings("unused") String key, Object value,
                    @Cached("key") @SuppressWarnings("unused") String cachedKey,
                    @Cached("create(__SETATTR__)") LookupAndCallTernaryNode setAttrNode,
                    @Cached.Shared("fromCharPointerNode") @Cached CExtNodes.FromCharPointerNode fromCharPointerNode) {
            setAttrNode.execute(object, SpecialAttributeNames.__DOC__, fromCharPointerNode.execute(value));
        }

        @Specialization(guards = {"eq(DOC, key)"})
        void getDoc(PBuiltinMethod object, @SuppressWarnings("unused") String key, Object value,
                    @Cached("key") @SuppressWarnings("unused") String cachedKey,
                    @Cached.Shared("fromCharPointerNode") @Cached CExtNodes.FromCharPointerNode fromCharPointerNode) {
            CompilerDirectives.transferToInterpreter();
            object.getStorage().define(SpecialAttributeNames.__DOC__, fromCharPointerNode.execute(value));
        }
    }
}
