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

import com.oracle.graal.python.builtins.objects.cext.NativeWrappers.PythonNativeWrapper;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.nodes.argument.keywords.ExecuteKeywordStarargsNode;
import com.oracle.graal.python.nodes.argument.positional.ExecutePositionalStarargsNode;
import com.oracle.graal.python.nodes.argument.positional.PositionalArgumentsNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.runtime.interop.PythonMessageResolution;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.PrimitiveValueProfile;

/**
 * Wrappers for methods used by native code.
 */
public abstract class ManagedMethodWrappers {

    @ExportLibrary(InteropLibrary.class)
    public abstract static class MethodWrapper extends PythonNativeWrapper {

        public MethodWrapper(Object method) {
            super(method);
        }

        static boolean isInstance(TruffleObject o) {
            return o instanceof MethodWrapper;
        }

        @ExportMessage
        public boolean isPointer(@Cached.Exclusive @Cached(allowUncached = true) CExtNodes.IsPointerNode pIsPointerNode) {
            return pIsPointerNode.execute(this);
        }

        @ExportMessage
        public long asPointer(@Cached.Exclusive @Cached(allowUncached = true) PythonObjectNativeWrapperMR.PAsPointerNode pAsPointerNode) {
            return pAsPointerNode.execute(this);
        }

        @ExportMessage
        public void toNative(@Cached.Exclusive @Cached(allowUncached = true) PythonObjectNativeWrapperMR.ToPyObjectNode toPyObjectNode) {
            toPyObjectNode.execute(this);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static class MethKeywords extends MethodWrapper {

        public MethKeywords(Object method) {
            super(method);
        }

        @ExportMessage
        public boolean isExecutable() {
            return true;
        }

        @ExportMessage
        public Object execute(Object[] arguments,
                              @Cached.Exclusive @Cached(allowUncached = true) ExecuteNode executeNode) {
            return executeNode.execute(this, arguments);
        }

        abstract static class ExecuteNode extends Node {
            public abstract Object execute(MethKeywords object, Object[] arguments);

            @Specialization
            public Object execute(MethKeywords object, Object[] arguments,
                                  @Cached.Exclusive @Cached CExtNodes.ToJavaNode toJavaNode,
                                  @Cached.Exclusive @Cached CExtNodes.ToSulongNode toSulongNode,
                                  @Cached.Exclusive @Cached CallNode dispatch,
                                  @Cached.Exclusive @Cached ExecutePositionalStarargsNode posStarargsNode,
                                  @Cached.Exclusive @Cached ExecuteKeywordStarargsNode expandKwargsNode,
                                  @Cached.Exclusive @Cached("createEqualityProfile()") PrimitiveValueProfile starArgsLenProfile) {
                if (arguments.length != 3) {
                    throw ArityException.raise(3, arguments.length);
                }

                // convert args

                Object receiver = toJavaNode.execute(arguments[0]);
                Object starArgs = toJavaNode.execute(arguments[1]);
                Object kwArgs = toJavaNode.execute(arguments[2]);

                Object[] starArgsArray = posStarargsNode.executeWith(starArgs);
                int starArgsLen = starArgsLenProfile.profile(starArgsArray.length);
                Object[] pArgs = PositionalArgumentsNode.prependArgument(receiver, starArgsArray, starArgsLen);
                PKeyword[] kwArgsArray = expandKwargsNode.executeWith(kwArgs);

                // execute

                if (dispatch == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    dispatch = insert(CallNode.create());
                }
                return toSulongNode.execute(dispatch.execute(null, object.getDelegate(), pArgs, kwArgsArray));
            }
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static class MethVarargs extends MethodWrapper {

        public MethVarargs(Object method) {
            super(method);
        }

        @ExportMessage
        public boolean isExecutable() {
            return true;
        }

        @ExportMessage
        public Object execute(Object[] arguments,
                              @Cached.Exclusive @Cached(allowUncached = true) ExecuteNode executeNode) {
            return executeNode.execute(this, arguments);
        }

        abstract static class ExecuteNode extends Node {
            public abstract Object execute(MethVarargs object, Object[] arguments);

            @Specialization
            @ExplodeLoop
            public Object execute(MethVarargs object, Object[] arguments,
                                  @Cached.Exclusive @Cached CExtNodes.ToJavaNode toJavaNode,
                                  @Cached.Exclusive @Cached CExtNodes.ToSulongNode toSulongNode,
                                  @Cached.Exclusive @Cached PythonMessageResolution.ExecuteNode executeNode) {
                if (arguments.length != 1) {
                    throw ArityException.raise(1, arguments.length);
                }

                // convert args

                Object varArgs = toJavaNode.execute(arguments[0]);

                // execute

                if (executeNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    executeNode = insert(new PythonMessageResolution.ExecuteNode());
                }
                return toSulongNode.execute(executeNode.execute(object.getDelegate(), new Object[]{varArgs}));
            }
        }
    }

    /**
     * Creates a wrapper for signature {@code meth(*args, **kwargs)}.
     */
    public static MethodWrapper createKeywords(Object method) {
        return new MethKeywords(method);
    }

    /**
     * Creates a wrapper for signature {@code meth(*args)}.
     */
    public static MethodWrapper createVarargs(Object method) {
        return new MethVarargs(method);
    }


}
