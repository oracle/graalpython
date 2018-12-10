/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.AsCharPointer;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.FromCharPointerNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.ToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.PyMethodDescrWrapperMRFactory.ReadFieldNodeGen;
import com.oracle.graal.python.builtins.objects.cext.PyMethodDescrWrapperMRFactory.WriteFieldNodeGen;
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
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.nodes.Node;

@MessageResolution(receiverType = PyMethodDescrWrapper.class)
public class PyMethodDescrWrapperMR {
    @Resolve(message = "READ")
    abstract static class ReadNode extends Node {
        @Child private ReadFieldNode readFieldNode = ReadFieldNodeGen.create();

        public Object access(PyMethodDescrWrapper object, String key) {
            return readFieldNode.execute(object.getDelegate(), key);
        }
    }

    @Resolve(message = "WRITE")
    abstract static class WriteNode extends Node {
        @Child private WriteFieldNode writeFieldNode = WriteFieldNodeGen.create();

        public int access(PyMethodDescrWrapper object, String key, Object value) {
            writeFieldNode.execute(object.getDelegate(), key, value);
            return 0;
        }
    }

    @ImportStatic({SpecialMethodNames.class})
    abstract static class ReadFieldNode extends Node {
        public static final String NAME = "ml_name";
        public static final String DOC = "ml_doc";

        @Child private ToSulongNode toSulongNode;
        @Child private AsCharPointer asCharPointerNode;

        public abstract Object execute(Object delegate, String key);

        protected boolean eq(String expected, String actual) {
            return expected.equals(actual);
        }

        private ToSulongNode getToSulongNode() {
            if (toSulongNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toSulongNode = insert(ToSulongNode.create());
            }
            return toSulongNode;
        }

        private AsCharPointer getAsCharPointer() {
            if (asCharPointerNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                asCharPointerNode = insert(AsCharPointer.create());
            }
            return asCharPointerNode;
        }

        @Specialization(guards = {"eq(NAME, key)"})
        Object getName(PythonObject object, @SuppressWarnings("unused") String key,
                        @Cached("key") @SuppressWarnings("unused") String cachedKey,
                        @Cached("create(__GETATTRIBUTE__)") LookupAndCallBinaryNode getAttrNode) {
            Object doc = getAttrNode.executeObject(object, SpecialAttributeNames.__NAME__);
            if (doc == PNone.NONE) {
                return getToSulongNode().execute(PNone.NO_VALUE);
            } else {
                return getAsCharPointer().execute(doc);
            }
        }

        @Specialization(guards = {"eq(DOC, key)"})
        Object getDoc(PythonObject object, @SuppressWarnings("unused") String key,
                        @Cached("key") @SuppressWarnings("unused") String cachedKey,
                        @Cached("create()") ReadAttributeFromObjectNode getAttrNode) {
            Object doc = getAttrNode.execute(object, SpecialAttributeNames.__DOC__);
            if (doc instanceof PNone) {
                return getToSulongNode().execute(PNone.NO_VALUE);
            } else {
                return getAsCharPointer().execute(doc);
            }
        }
    }

    @ImportStatic({SpecialMethodNames.class})
    abstract static class WriteFieldNode extends Node {
        public static final String DOC = "ml_doc";

        @Child private FromCharPointerNode fromCharPointerNode;

        public abstract void execute(Object delegate, String key, Object value);

        protected boolean eq(String expected, String actual) {
            return expected.equals(actual);
        }

        private FromCharPointerNode getFromCharPointer() {
            if (fromCharPointerNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                fromCharPointerNode = insert(FromCharPointerNode.create());
            }
            return fromCharPointerNode;
        }

        @Specialization(guards = {"eq(DOC, key)"})
        void getDoc(PythonObject object, @SuppressWarnings("unused") String key, Object value,
                        @Cached("key") @SuppressWarnings("unused") String cachedKey,
                        @Cached("create(__SETATTR__)") LookupAndCallTernaryNode setAttrNode) {
            setAttrNode.execute(object, SpecialAttributeNames.__DOC__, getFromCharPointer().execute(value));
        }
    }
}
