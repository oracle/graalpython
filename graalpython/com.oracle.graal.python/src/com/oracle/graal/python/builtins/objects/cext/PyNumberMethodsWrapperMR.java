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

import static com.oracle.graal.python.builtins.objects.cext.NativeMemberNames.NB_ADD;
import static com.oracle.graal.python.builtins.objects.cext.NativeMemberNames.NB_AND;
import static com.oracle.graal.python.builtins.objects.cext.NativeMemberNames.NB_INDEX;
import static com.oracle.graal.python.builtins.objects.cext.NativeMemberNames.NB_INPLACE_MULTIPLY;
import static com.oracle.graal.python.builtins.objects.cext.NativeMemberNames.NB_MULTIPLY;
import static com.oracle.graal.python.builtins.objects.cext.NativeMemberNames.NB_POW;
import static com.oracle.graal.python.builtins.objects.cext.NativeMemberNames.NB_TRUE_DIVIDE;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__AND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__IMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INDEX__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__MUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__POW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__TRUEDIV__;

import com.oracle.graal.python.builtins.objects.cext.CExtNodes.ToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.PyNumberMethodsWrapperMRFactory.ReadMethodNodeGen;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.nodes.Node;

@MessageResolution(receiverType = PyNumberMethodsWrapper.class)
public class PyNumberMethodsWrapperMR {

    @Resolve(message = "READ")
    abstract static class ReadNode extends Node {
        @Child private ReadMethodNode readMethodNode = ReadMethodNodeGen.create();
        @Child private ToSulongNode toSulongNode;

        public Object access(PyNumberMethodsWrapper object, String key) {
            // translate key to attribute name
            PythonManagedClass delegate = object.getPythonClass();
            return getToSulongNode().execute(readMethodNode.execute(delegate, key));
        }

        private ToSulongNode getToSulongNode() {
            if (toSulongNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toSulongNode = insert(ToSulongNode.create());
            }
            return toSulongNode;
        }
    }

    abstract static class ReadMethodNode extends PNodeWithContext {

        public abstract Object execute(PythonManagedClass clazz, String key);

        @Specialization(limit = "99", guards = {"eq(cachedKey, key)"})
        Object getMethod(PythonManagedClass clazz, @SuppressWarnings("unused") String key,
                        @Cached("key") @SuppressWarnings("unused") String cachedKey,
                        @Cached("createLookupNode(cachedKey)") LookupAttributeInMRONode lookupNode) {
            if (lookupNode != null) {
                return lookupNode.execute(clazz);
            }
            // TODO extend list
            CompilerDirectives.transferToInterpreter();
            throw UnknownIdentifierException.raise(key);
        }

        protected LookupAttributeInMRONode createLookupNode(String key) {
            switch (key) {
                case NB_ADD:
                    return LookupAttributeInMRONode.create(__ADD__);
                case NB_AND:
                    return LookupAttributeInMRONode.create(__AND__);
                case NB_INDEX:
                    return LookupAttributeInMRONode.create(__INDEX__);
                case NB_POW:
                    return LookupAttributeInMRONode.create(__POW__);
                case NB_TRUE_DIVIDE:
                    return LookupAttributeInMRONode.create(__TRUEDIV__);
                case NB_MULTIPLY:
                    return LookupAttributeInMRONode.create(__MUL__);
                case NB_INPLACE_MULTIPLY:
                    return LookupAttributeInMRONode.create(__IMUL__);
                default:
                    // TODO extend list
                    throw UnknownIdentifierException.raise(key);
            }
        }

        protected static boolean eq(String expected, String actual) {
            return expected.equals(actual);
        }
    }

}
