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

import com.oracle.graal.python.builtins.objects.cext.CExtNodes.ToJavaNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.ToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.ManagedMethodWrappers.MethKeywords;
import com.oracle.graal.python.builtins.objects.cext.ManagedMethodWrappers.MethVarargs;
import com.oracle.graal.python.builtins.objects.cext.ManagedMethodWrappers.MethodWrapper;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.nodes.argument.ArityCheckNode;
import com.oracle.graal.python.nodes.argument.CreateArgumentsNode;
import com.oracle.graal.python.nodes.argument.keywords.ExecuteKeywordStarargsNode;
import com.oracle.graal.python.nodes.argument.positional.ExecutePositionalStarargsNode;
import com.oracle.graal.python.nodes.argument.positional.PositionalArgumentsNode;
import com.oracle.graal.python.nodes.call.CallDispatchNode;
import com.oracle.graal.python.runtime.interop.PythonMessageResolution;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ValueProfile;

@MessageResolution(receiverType = MethodWrapper.class)
public class ManagedMethodWrappersMR {

    @Resolve(message = "EXECUTE")
    abstract static class ExecuteNode extends Node {
        @Child PythonMessageResolution.ExecuteNode executeNode;
        @Child private ToJavaNode toJavaNode;
        @Child private ToSulongNode toSulongNode;

        @Child private ExecutePositionalStarargsNode posStarargsNode = ExecutePositionalStarargsNode.create();
        @Child private PositionalArgumentsNode posArgsNode = PositionalArgumentsNode.create();
        @Child private ExecuteKeywordStarargsNode expandKwargsNode = ExecuteKeywordStarargsNode.create();
        @Child private CallDispatchNode dispatch;
        @Child private CreateArgumentsNode createArgs = CreateArgumentsNode.create();
        @Child private ArityCheckNode arityCheckNode = ArityCheckNode.create();
        final ValueProfile classProfile = ValueProfile.createClassProfile();

        private CallDispatchNode getDispatchNode() {
            if (dispatch == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                dispatch = insert(CallDispatchNode.create("<foreign-invoke>"));
            }
            return dispatch;
        }

        @ExplodeLoop
        public Object access(MethKeywords object, Object[] arguments) {
            if (executeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                executeNode = insert(new PythonMessageResolution.ExecuteNode());
            }
            if (arguments.length != 3) {
                throw ArityException.raise(3, arguments.length);
            }

            // convert args
            Object[] converted = new Object[arguments.length];
            for (int i = 0; i < arguments.length; i++) {
                converted[i] = getToJavaNode().execute(arguments[i]);
            }

            Object[] userArgs = posArgsNode.executeWithArguments(converted[0], posStarargsNode.executeWith(converted[1]));
            Object[] pArgs = createArgs.execute(userArgs);
            PKeyword[] kwargs = expandKwargsNode.executeWith(converted[2]);
            return getToSulongNode().execute(getDispatchNode().executeCall(object.getDelegate(), pArgs, kwargs));
        }

        @ExplodeLoop
        public Object access(MethVarargs object, Object[] arguments) {
            if (executeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                executeNode = insert(new PythonMessageResolution.ExecuteNode());
            }
            if (arguments.length != 1) {
                throw ArityException.raise(1, arguments.length);
            }

            // convert args
            return getToSulongNode().execute(executeNode.execute(object.getDelegate(), new Object[]{getToJavaNode().execute(arguments[0])}));
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
