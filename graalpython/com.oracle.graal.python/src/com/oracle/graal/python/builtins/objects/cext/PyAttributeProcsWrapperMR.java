/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 *     one is included with the Software (each a "Larger Work" to which the
 *     Software is contributed by such licensors),
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

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.ToJavaNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.ToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.PyAttributeProcsWrapper.GetAttrWrapper;
import com.oracle.graal.python.builtins.objects.cext.PyAttributeProcsWrapper.SetAttrWrapper;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.interop.PythonMessageResolution;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;

@MessageResolution(receiverType = PyAttributeProcsWrapper.class)
public class PyAttributeProcsWrapperMR {

    @Resolve(message = "EXECUTE")
    abstract static class ExecuteNode extends Node {
        @Child PythonMessageResolution.ExecuteNode executeNode;
        @Child private ToSulongNode toSulongNode;
        @Child private ToJavaNode toJavaNode;
        @CompilationFinal ConditionProfile attributeErrorProfile;

        public Object access(GetAttrWrapper object, Object[] arguments) {
            if (arguments.length != 2) {
                throw ArityException.raise(2, arguments.length);
            }
            Object[] converted = new Object[2];
            converted[0] = getToJavaNode().execute(arguments[0]);
            converted[1] = getToJavaNode().execute(arguments[1]);
            Object result;
            try {
                result = getExecuteNode().execute(object.getDelegate(), converted);
            } catch (PException e) {
                // TODO move to node
                e.expectAttributeError(PythonLanguage.getCore(), getProfile());
                result = PNone.NO_VALUE;
            }
            return getToSulongNode().execute(result);
        }

        public Object access(SetAttrWrapper object, Object[] arguments) {
            if (arguments.length != 3) {
                throw ArityException.raise(3, arguments.length);
            }
            Object[] converted = new Object[3];
            converted[0] = getToJavaNode().execute(arguments[0]);
            converted[1] = getToJavaNode().execute(arguments[1]);
            converted[2] = getToJavaNode().execute(arguments[2]);
            return getToSulongNode().execute(getExecuteNode().execute(object.getDelegate(), converted));
        }

        private PythonMessageResolution.ExecuteNode getExecuteNode() {
            if (executeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                executeNode = insert(new PythonMessageResolution.ExecuteNode());
            }
            return executeNode;
        }

        private ConditionProfile getProfile() {
            if (attributeErrorProfile == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                attributeErrorProfile = ConditionProfile.createBinaryProfile();
            }
            return attributeErrorProfile;
        }

        private ToJavaNode getToJavaNode() {
            if (toJavaNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toJavaNode = insert(ToJavaNode.create());
            }
            return toJavaNode;
        }

        private ToSulongNode getToSulongNode() {
            if (toSulongNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toSulongNode = insert(ToSulongNode.create());
            }
            return toSulongNode;
        }
    }

}
