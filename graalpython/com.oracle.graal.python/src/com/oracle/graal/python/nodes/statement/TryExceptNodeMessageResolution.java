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
package com.oracle.graal.python.nodes.statement;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETATTRIBUTE__;

import java.util.ArrayList;

import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.PNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.frame.ReadGlobalOrBuiltinNode;
import com.oracle.graal.python.nodes.literal.TupleLiteralNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.CanResolve;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.KeyInfo;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;

@MessageResolution(receiverType = TryExceptNode.class)
class TryExceptNodeMessageResolution {
    @Resolve(message = "HAS_KEYS")
    abstract static class HasKeysNode extends Node {
        Object access(@SuppressWarnings("unused") TryExceptNode object) {
            return true;
        }
    }

    @Resolve(message = "KEYS")
    abstract static class KeysNode extends Node {
        Object access(TryExceptNode object) {
            return object.getContext().getEnv().asGuestValue(new String[]{StandardTags.TryBlockTag.CATCHES});
        }
    }

    @Resolve(message = "KEY_INFO")
    abstract static class KeyInfoNode extends Node {
        Object access(@SuppressWarnings("unused") TryExceptNode object, String name) {
            if (name.equals(StandardTags.TryBlockTag.CATCHES)) {
                return KeyInfo.INVOCABLE | KeyInfo.READABLE;
            } else {
                return KeyInfo.NONE;
            }
        }
    }

    @Resolve(message = "READ")
    abstract static class ReadNode extends Node {
        @Child LookupAndCallBinaryNode getAttr = LookupAndCallBinaryNode.create(__GETATTRIBUTE__);
        @Child PythonObjectFactory factory = PythonObjectFactory.create();

        CatchesFunction access(TryExceptNode object, String name) {
            return doit(object, name, getAttr, factory);
        }

        static CatchesFunction doit(TryExceptNode object, String name, LookupAndCallBinaryNode getAttr, PythonObjectFactory factory) {
            if (name.equals(StandardTags.TryBlockTag.CATCHES)) {
                if (object.getCatchesFunction() == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    ArrayList<Object> literalCatches = new ArrayList<>();
                    ExceptNode[] exceptNodes = object.getExceptNodes();
                    PythonModule builtins = object.getContext().getBuiltins();
                    if (builtins == null) {
                        return new CatchesFunction(null, null);
                    }

                    for (ExceptNode node : exceptNodes) {
                        PNode exceptType = node.getExceptType();
                        if (exceptType instanceof ReadGlobalOrBuiltinNode) {
                            try {
                                literalCatches.add(getAttr.executeObject(builtins, ((ReadGlobalOrBuiltinNode) exceptType).getAttributeId()));
                            } catch (PException e) {
                            }
                        } else if (exceptType instanceof TupleLiteralNode) {
                            for (PNode tupleValue : ((TupleLiteralNode) exceptType).getValues()) {
                                if (tupleValue instanceof ReadGlobalOrBuiltinNode) {
                                    try {
                                        literalCatches.add(getAttr.executeObject(builtins, ((ReadGlobalOrBuiltinNode) tupleValue).getAttributeId()));
                                    } catch (PException e) {
                                    }
                                }
                            }
                        }
                    }

                    Object isinstanceFunc = getAttr.executeObject(builtins, BuiltinNames.ISINSTANCE);
                    PTuple caughtClasses = factory.createTuple(literalCatches.toArray());

                    if (isinstanceFunc instanceof PBuiltinMethod) {
                        RootCallTarget callTarget = ((PBuiltinMethod) isinstanceFunc).getCallTarget();
                        object.setCatchesFunction(new CatchesFunction(callTarget, caughtClasses));
                    } else {
                        throw new IllegalStateException("isinstance was redefined, cannot check exceptions");
                    }
                }
                return object.getCatchesFunction();
            } else {
                throw UnknownIdentifierException.raise(name);
            }
        }
    }

    @Resolve(message = "INVOKE")
    abstract static class InvokeNode extends Node {
        @Child LookupAndCallBinaryNode getAttr = LookupAndCallBinaryNode.create(__GETATTRIBUTE__);
        @Child PythonObjectFactory factory = PythonObjectFactory.create();

        Object access(TryExceptNode object, String name, Object[] arguments) {
            CatchesFunction catchesFunction = ReadNode.doit(object, name, getAttr, factory);
            if (arguments.length != 1) {
                throw ArityException.raise(1, arguments.length);
            }
            return catchesFunction.catches(arguments[0]);
        }
    }

    @CanResolve
    abstract static class CheckFunction extends Node {
        protected static boolean test(TruffleObject receiver) {
            return receiver instanceof TryExceptNode;
        }
    }

    static class CatchesFunction implements TruffleObject {
        private final RootCallTarget isInstance;
        private final Object[] args = PArguments.create(2);

        CatchesFunction(RootCallTarget callTarget, PTuple caughtClasses) {
            this.isInstance = callTarget;
            PArguments.setArgument(args, 1, caughtClasses);
        }

        @ExplodeLoop
        boolean catches(Object exception) {
            if (isInstance == null) {
                return false;
            }
            if (exception instanceof PBaseException) {
                PArguments.setArgument(args, 0, exception);
                try {
                    return isInstance.call(args) == Boolean.TRUE;
                } catch (PException e) {
                }
            }
            return false;
        }

        public ForeignAccess getForeignAccess() {
            return null;
        }
    }
}
