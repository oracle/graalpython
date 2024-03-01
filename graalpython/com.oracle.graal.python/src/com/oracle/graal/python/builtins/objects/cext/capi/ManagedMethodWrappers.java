/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.capi;

import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.checkThrowableBeforeNative;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.TransformExceptionToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapper.PythonStructNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeToPythonNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNewRefNode;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.nodes.argument.keywords.ExpandKeywordStarargsNode;
import com.oracle.graal.python.nodes.argument.positional.ExecutePositionalStarargsNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;

/**
 * Wrappers for methods used by native code.
 */
public abstract class ManagedMethodWrappers {

    @ExportLibrary(InteropLibrary.class)
    public abstract static class MethodWrapper extends PythonStructNativeWrapper {

        public MethodWrapper(Object method) {
            super(method, false);
        }

        @ExportMessage
        public boolean isPointer() {
            return isNative();
        }

        @ExportMessage
        public long asPointer() {
            return getNativePointer();
        }

        @ExportMessage
        @TruffleBoundary
        public void toNative() {
            if (!isPointer()) {
                setNativePointer(PythonContext.get(null).getCApiContext().registerClosure(getSignature(), this, getDelegate()));
            }
        }

        protected abstract String getSignature();
    }

    @ExportLibrary(InteropLibrary.class)
    static final class MethKeywords extends MethodWrapper {

        public MethKeywords(Object method) {
            super(method);
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        protected boolean isExecutable() {
            return true;
        }

        @ExportMessage
        public Object execute(Object[] arguments,
                        @Bind("$node") Node inliningTarget,
                        @Exclusive @Cached NativeToPythonNode toJavaNode,
                        @Exclusive @Cached PythonToNativeNewRefNode toSulongNode,
                        @Exclusive @Cached CallNode callNode,
                        @Exclusive @Cached ExecutePositionalStarargsNode posStarargsNode,
                        @Exclusive @Cached ExpandKeywordStarargsNode expandKwargsNode,
                        @Exclusive @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            boolean mustRelease = gil.acquire();
            try {
                if (arguments.length != 3) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw ArityException.create(3, 3, arguments.length);
                }

                try {
                    // convert args
                    Object receiver = toJavaNode.execute(arguments[0]);
                    Object starArgs = toJavaNode.execute(arguments[1]);
                    Object kwArgs = toJavaNode.execute(arguments[2]);

                    Object[] pArgs;
                    if (starArgs != PNone.NO_VALUE) {
                        Object[] starArgsArray = posStarargsNode.executeWith(null, starArgs);
                        pArgs = PythonUtils.prependArgument(receiver, starArgsArray);
                    } else {
                        pArgs = new Object[]{receiver};
                    }
                    PKeyword[] kwArgsArray = expandKwargsNode.execute(inliningTarget, kwArgs);

                    // execute
                    return toSulongNode.execute(callNode.execute(null, getDelegate(), pArgs, kwArgsArray));
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "MethKeywords", getDelegate());
                }
            } catch (PException e) {
                transformExceptionToNativeNode.execute(inliningTarget, e);
                return PythonContext.get(callNode).getNativeNull();
            } finally {
                gil.release(mustRelease);
            }
        }

        @Override
        protected String getSignature() {
            return "(POINTER,POINTER,POINTER):POINTER";
        }
    }

    /**
     * Creates a wrapper for signature {@code meth(*args, **kwargs)}.
     */
    public static MethodWrapper createKeywords(Object method) {
        return new MethKeywords(method);
    }
}
