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

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.GetNativeNullNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.ToJavaNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.ToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.DynamicObjectNativeWrapper.PAsPointerNode;
import com.oracle.graal.python.builtins.objects.cext.DynamicObjectNativeWrapper.ToPyObjectNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;

@ExportLibrary(InteropLibrary.class)
@ExportLibrary(NativeTypeLibrary.class)
public abstract class PyProcsWrapper extends PythonNativeWrapper {

    public PyProcsWrapper(Object delegate) {
        super(delegate);
    }

    @ExportMessage
    protected boolean isExecutable() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings({"unused", "static-method"})
    protected Object execute(Object[] arguments) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
        throw new IllegalStateException("should not reach");
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

    @ExportMessage
    protected boolean isPointer(
                    @Cached CExtNodes.IsPointerNode isPointerNode) {
        return isPointerNode.execute(this);
    }

    @ExportMessage
    protected long asPointer(
                    @Exclusive @Cached PAsPointerNode pAsPointerNode) {
        return pAsPointerNode.execute(this);
    }

    @ExportMessage
    protected void toNative(
                    @Exclusive @Cached ToPyObjectNode toPyObjectNode,
                    @Exclusive @Cached InvalidateNativeObjectsAllManagedNode invalidateNode) {
        invalidateNode.execute();
        setNativePointer(toPyObjectNode.execute(this));
    }

    @ExportLibrary(InteropLibrary.class)
    static class GetAttrWrapper extends PyProcsWrapper {

        public GetAttrWrapper(Object delegate) {
            super(delegate);
        }

        @ExportMessage
        protected Object execute(Object[] arguments,
                        @CachedLibrary("this") PythonNativeWrapperLibrary lib,
                        @Cached ToSulongNode toSulongNode,
                        @Cached PythonAbstractObject.PExecuteNode executeNode,
                        @Cached ToJavaNode toJavaNode,
                        @Exclusive @Cached IsBuiltinClassProfile errProfile,
                        @Cached GetNativeNullNode getNativeNullNode,
                        @CachedContext(PythonLanguage.class) PythonContext context) throws ArityException, UnsupportedMessageException {
            if (arguments.length != 2) {
                throw ArityException.create(2, arguments.length);
            }
            Object[] converted = new Object[2];
            converted[0] = toJavaNode.execute(arguments[0]);
            converted[1] = toJavaNode.execute(arguments[1]);
            Object result;
            try {
                result = toSulongNode.execute(executeNode.execute(lib.getDelegate(this), converted));
            } catch (PException e) {
                // TODO move to node
                e.expectAttributeError(errProfile);

                // This node cannot have a frame so we cannot neither be sure if the PFrame is
                // already available nor we can create it at this point. Furthermore, the last
                // Python caller also won't eagerly create the PFrame since this could unnecessarily
                // expensive. So, we just need to provide the frame info. This will be enough to
                // re-create the current stack at a later point in time.
                e.getExceptionObject().reifyException(context.peekTopFrameInfo());

                context.setCurrentException(e);
                result = getNativeNullNode.execute();
            }
            return result;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static class SetAttrWrapper extends PyProcsWrapper {

        public SetAttrWrapper(Object delegate) {
            super(delegate);
        }

        @ExportMessage
        protected int execute(Object[] arguments,
                        @CachedLibrary("this") PythonNativeWrapperLibrary lib,
                        @Cached PythonAbstractObject.PExecuteNode executeNode,
                        @Cached ToJavaNode toJavaNode,
                        @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef) throws ArityException, UnsupportedMessageException {
            if (arguments.length != 3) {
                throw ArityException.create(3, arguments.length);
            }
            Object[] converted = new Object[3];
            converted[0] = toJavaNode.execute(arguments[0]);
            converted[1] = toJavaNode.execute(arguments[1]);
            converted[2] = toJavaNode.execute(arguments[2]);
            try {
                executeNode.execute(lib.getDelegate(this), converted);
                return 0;
            } catch (PException e) {
                PythonContext context = contextRef.get();
                e.getExceptionObject().reifyException(context.peekTopFrameInfo());
                context.setCurrentException(e);
                return -1;
            }
        }

    }

    @ExportLibrary(InteropLibrary.class)
    static class SsizeargfuncWrapper extends PyProcsWrapper {
        public SsizeargfuncWrapper(Object delegate) {
            super(delegate);
        }

        @ExportMessage
        @Specialization
        protected Object execute(Object[] arguments,
                        @CachedLibrary("this") PythonNativeWrapperLibrary lib,
                        @Cached ToSulongNode toSulongNode,
                        @Cached PythonAbstractObject.PExecuteNode executeNode,
                        @Cached ToJavaNode toJavaNode,
                        @Cached GetNativeNullNode getNativeNullNode,
                        @CachedContext(PythonLanguage.class) ContextReference<PythonContext> contextRef) throws ArityException, UnsupportedMessageException {
            if (arguments.length != 2) {
                throw ArityException.create(2, arguments.length);
            }
            Object[] converted = new Object[2];
            converted[0] = toJavaNode.execute(arguments[0]);
            assert arguments[1] instanceof Number;
            converted[1] = arguments[1];
            Object result;
            try {
                result = toSulongNode.execute(executeNode.execute(lib.getDelegate(this), converted));
            } catch (PException e) {
                PythonContext context = contextRef.get();
                e.getExceptionObject().reifyException(context.peekTopFrameInfo());
                context.setCurrentException(e);
                result = getNativeNullNode.execute();
            }
            return result;
        }
    }

    public static GetAttrWrapper createGetAttrWrapper(Object getAttrMethod) {
        return new GetAttrWrapper(getAttrMethod);
    }

    public static SetAttrWrapper createSetAttrWrapper(Object setAttrMethod) {
        return new SetAttrWrapper(setAttrMethod);
    }

    public static SsizeargfuncWrapper createSsizeargfuncWrapper(Object ssizeArgMethod) {
        return new SsizeargfuncWrapper(ssizeArgMethod);
    }
}
