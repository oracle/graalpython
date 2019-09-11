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

import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.cext.DynamicObjectNativeWrapper.PAsPointerNode;
import com.oracle.graal.python.builtins.objects.cext.DynamicObjectNativeWrapper.ToPyObjectNode;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.nodes.argument.keywords.ExecuteKeywordStarargsNode.ExpandKeywordStarargsNode;
import com.oracle.graal.python.nodes.argument.positional.ExecutePositionalStarargsNode.ExecutePositionalStarargsInteropNode;
import com.oracle.graal.python.nodes.argument.positional.PositionalArgumentsNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;

/**
 * Wrappers for methods used by native code.
 */
public abstract class ManagedMethodWrappers {

    @ExportLibrary(InteropLibrary.class)
    @ExportLibrary(NativeTypeLibrary.class)
    public abstract static class MethodWrapper extends PythonNativeWrapper {

        public MethodWrapper(Object method) {
            super(method);
        }

        @ExportMessage
        public boolean isPointer(
                        @Exclusive @Cached CExtNodes.IsPointerNode pIsPointerNode) {
            return pIsPointerNode.execute(this);
        }

        @ExportMessage
        public long asPointer(
                        @Exclusive @Cached PAsPointerNode pAsPointerNode) {
            return pAsPointerNode.execute(this);
        }

        @ExportMessage
        public void toNative(
                        @Exclusive @Cached ToPyObjectNode toPyObjectNode,
                        @Exclusive @Cached InvalidateNativeObjectsAllManagedNode invalidateNode) {
            invalidateNode.execute();
            setNativePointer(toPyObjectNode.execute(this));
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        public boolean hasNativeType() {
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

    @ExportLibrary(InteropLibrary.class)
    @ExportLibrary(NativeTypeLibrary.class)
    static class MethKeywords extends MethodWrapper {

        public MethKeywords(Object method) {
            super(method);
        }

        @ExportMessage
        protected boolean isExecutable() {
            return true;
        }

        @ExportMessage
        public Object execute(Object[] arguments,
                        @CachedLibrary("this") PythonNativeWrapperLibrary lib,
                        @Exclusive @Cached CExtNodes.ToJavaNode toJavaNode,
                        @Exclusive @Cached CExtNodes.ToSulongNode toSulongNode,
                        @Exclusive @Cached CallNode callNode,
                        @Exclusive @Cached ExecutePositionalStarargsInteropNode posStarargsNode,
                        @Exclusive @Cached ExpandKeywordStarargsNode expandKwargsNode) throws ArityException {
            if (arguments.length != 3) {
                throw ArityException.create(3, arguments.length);
            }

            // convert args
            Object receiver = toJavaNode.execute(arguments[0]);
            Object starArgs = toJavaNode.execute(arguments[1]);
            Object kwArgs = toJavaNode.execute(arguments[2]);

            Object[] starArgsArray = posStarargsNode.executeWithGlobalState(starArgs);
            Object[] pArgs = PositionalArgumentsNode.prependArgument(receiver, starArgsArray);
            PKeyword[] kwArgsArray = expandKwargsNode.executeWith(kwArgs);

            // execute
            return toSulongNode.execute(callNode.execute(null, lib.getDelegate(this), pArgs, kwArgsArray));
        }
    }

    @ExportLibrary(InteropLibrary.class)
    @ExportLibrary(NativeTypeLibrary.class)
    static class MethVarargs extends MethodWrapper {

        public MethVarargs(Object method) {
            super(method);
        }

        @ExportMessage
        protected boolean isExecutable() {
            return true;
        }

        @ExportMessage
        public Object execute(Object[] arguments,
                        @CachedLibrary("this") PythonNativeWrapperLibrary lib,
                        @Exclusive @Cached CExtNodes.ToJavaNode toJavaNode,
                        @Exclusive @Cached CExtNodes.ToSulongNode toSulongNode,
                        @Exclusive @Cached PythonAbstractObject.PExecuteNode executeNode) throws ArityException, UnsupportedMessageException {
            if (arguments.length != 1) {
                throw ArityException.create(1, arguments.length);
            }

            // convert args
            Object varArgs = toJavaNode.execute(arguments[0]);
            return toSulongNode.execute(executeNode.execute(lib.getDelegate(this), new Object[]{varArgs}));
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
