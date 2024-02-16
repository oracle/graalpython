/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.TransformExceptionToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTiming;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeToPythonNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNewRefNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtContext;
import com.oracle.graal.python.builtins.objects.function.BuiltinMethodDescriptor;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.nodes.argument.keywords.ExpandKeywordStarargsNode;
import com.oracle.graal.python.nodes.argument.positional.ExecutePositionalStarargsNode;
import com.oracle.graal.python.nodes.call.CallTargetInvokeNode;
import com.oracle.graal.python.nodes.call.GenericInvokeNode;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;

@ExportLibrary(InteropLibrary.class)
public abstract class PyCFunctionWrapper implements TruffleObject {

    protected final CallTarget callTarget;
    protected final BuiltinMethodDescriptor builtinMethodDescriptor;
    protected final CApiTiming timing;
    private long pointer;

    protected PyCFunctionWrapper(CallTarget callTarget) {
        assert callTarget != null;
        this.callTarget = callTarget;
        this.builtinMethodDescriptor = null;
        this.timing = CApiTiming.create(false, callTarget);
    }

    protected PyCFunctionWrapper(BuiltinMethodDescriptor builtinMethodDescriptor) {
        assert builtinMethodDescriptor != null;
        this.callTarget = null;
        this.builtinMethodDescriptor = builtinMethodDescriptor;
        this.timing = CApiTiming.create(false, builtinMethodDescriptor);
    }

    public final CallTarget getCallTarget() {
        return callTarget;
    }

    public final Object getDelegate() {
        if (builtinMethodDescriptor != null) {
            return builtinMethodDescriptor;
        }
        assert callTarget != null;
        return callTarget;
    }

    abstract String getSignature();

    @ExportMessage
    boolean isExecutable() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings({"unused", "static-method"})
    protected Object execute(Object[] arguments) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
        throw CompilerDirectives.shouldNotReachHere("abstract class");
    }

    @ExportMessage
    @TruffleBoundary
    protected void toNative() {
        if (pointer == 0) {
            pointer = PythonContext.get(null).getCApiContext().registerClosure(getSignature(), this, getDelegate());
        }
    }

    @ExportMessage
    protected boolean isPointer() {
        return pointer != 0;
    }

    @ExportMessage
    protected long asPointer() {
        return pointer;
    }

    /**
     * Creates a wrapper for a {@link PBuiltinFunction} that can go to native. The flags are
     * required to determine the signature. The resulting {@link PyCFunctionWrapper} will not
     * reference the built-in function object but will only wrap either its
     * {@link BuiltinMethodDescriptor} (if available) or its {@link RootCallTarget}.
     */
    public static PyCFunctionWrapper createCallTargetWrapper(CApiContext cApiContext, PBuiltinFunction builtinFunction) {
        CompilerAsserts.neverPartOfCompilation();
        PyCFunctionWrapper wrapper;
        int flags = builtinFunction.getFlags();
        BuiltinMethodDescriptor builtinMethodDescriptor = BuiltinMethodDescriptor.get(builtinFunction);
        if (builtinMethodDescriptor != null) {
            if (CExtContext.isMethNoArgs(flags)) {
                wrapper = cApiContext.getOrCreatePyCFunctionWrapper(builtinMethodDescriptor, PyCFunctionUnaryWrapper::new);
            } else if (CExtContext.isMethO(flags)) {
                wrapper = cApiContext.getOrCreatePyCFunctionWrapper(builtinMethodDescriptor, PyCFunctionBinaryWrapper::new);
            } else {
                throw CompilerDirectives.shouldNotReachHere("other signature " + Integer.toHexString(flags));
            }
        } else {
            RootCallTarget ct = builtinFunction.getCallTarget();
            if (CExtContext.isMethNoArgs(flags)) {
                wrapper = cApiContext.getOrCreatePyCFunctionWrapper(ct, PyCFunctionUnaryWrapper::new);
            } else if (CExtContext.isMethO(flags)) {
                wrapper = cApiContext.getOrCreatePyCFunctionWrapper(ct, PyCFunctionBinaryWrapper::new);
            } else if (CExtContext.isMethVarargs(flags)) {
                wrapper = cApiContext.getOrCreatePyCFunctionWrapper(ct, PyCFunctionVarargsWrapper::new);
            } else if (CExtContext.isMethVarargsWithKeywords(flags)) {
                wrapper = cApiContext.getOrCreatePyCFunctionWrapper(ct, PyCFunctionKeywordsWrapper::new);
            } else {
                throw CompilerDirectives.shouldNotReachHere("other signature " + Integer.toHexString(flags));
            }
        }
        return wrapper;
    }

    /**
     * Takes the Python arguments array (i.e. {@link PArguments}) and invokes the given call target.
     * It tries to call the call target directly using a {@link CallTargetInvokeNode} and by
     * (weakly) caching the call target. Otherwise, a {@link GenericInvokeNode} will
     */
    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    abstract static class PyCFunctionWrapperCallTargetInvokeNode extends Node {

        abstract Object execute(Node inliningTarget, CallTarget ct, Object[] pythonArguments);

        @Specialization(guards = "ct == cachedCt", limit = "1")
        static Object doCallTargetDirect(@SuppressWarnings("unused") CallTarget ct, Object[] args,
                        @SuppressWarnings("unused") @Cached(value = "ct", weak = true) CallTarget cachedCt,
                        @Cached("create(ct, true, false)") CallTargetInvokeNode callNode) {
            assert PArguments.isPythonFrame(args);
            return callNode.execute(null, null, null, null, args);
        }

        @Specialization(replaces = "doCallTargetDirect")
        static Object doCallTargetIndirect(CallTarget ct, Object[] args,
                        @Cached(inline = false) GenericInvokeNode callNode) {
            assert PArguments.isPythonFrame(args);
            return callNode.execute(ct, args);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static class PyCFunctionUnaryWrapper extends PyCFunctionWrapper {

        protected PyCFunctionUnaryWrapper(CallTarget callTarget) {
            super(callTarget);
        }

        protected PyCFunctionUnaryWrapper(BuiltinMethodDescriptor builtinMethodDescriptor) {
            super(builtinMethodDescriptor);
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @Bind("$node") Node inliningTarget,
                        @Cached PythonToNativeNewRefNode toNativeNode,
                        @Cached CallUnaryMethodNode executeNode,
                        @Cached PyCFunctionWrapperCallTargetInvokeNode callTargetInvokeNode,
                        @Cached NativeToPythonNode toJavaNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            boolean mustRelease = gil.acquire();
            CApiTiming.enter();
            try {
                /*
                 * Accept a second argument here, since these functions are sometimes called using
                 * METH_O with a "NULL" value.
                 */
                if (arguments.length > 2) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw ArityException.create(1, 2, arguments.length);
                }
                try {
                    Object result;
                    if (builtinMethodDescriptor != null) {
                        assert callTarget == null;
                        result = executeNode.executeObject(null, builtinMethodDescriptor, toJavaNode.execute(arguments[0]));
                    } else {
                        assert callTarget != null;
                        result = callTargetInvokeNode.execute(inliningTarget, callTarget, createPArguments(toJavaNode.execute(arguments[0])));
                    }
                    return toNativeNode.execute(result);
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "PyCFunction(METH_NOARGS)", getDelegate());
                }
            } catch (PException e) {
                transformExceptionToNativeNode.execute(null, inliningTarget, e);
                return PythonContext.get(gil).getNativeNull().getPtr();
            } finally {
                CApiTiming.exit(timing);
                gil.release(mustRelease);
            }
        }

        private static Object[] createPArguments(Object arg) {
            Object[] pArguments = PArguments.create(1);
            PArguments.setArgument(pArguments, 0, arg);
            return pArguments;
        }

        @Override
        protected String getSignature() {
            return "(POINTER):POINTER";
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static class PyCFunctionBinaryWrapper extends PyCFunctionWrapper {

        protected PyCFunctionBinaryWrapper(CallTarget callTarget) {
            super(callTarget);
        }

        protected PyCFunctionBinaryWrapper(BuiltinMethodDescriptor builtinMethodDescriptor) {
            super(builtinMethodDescriptor);
        }

        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @Bind("$node") Node inliningTarget,
                        @Cached PythonToNativeNewRefNode toNativeNode,
                        @Cached CallBinaryMethodNode executeNode,
                        @Cached PyCFunctionWrapperCallTargetInvokeNode callTargetInvokeNode,
                        @Cached NativeToPythonNode toJavaNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            boolean mustRelease = gil.acquire();
            CApiTiming.enter();
            try {
                if (arguments.length != 2) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw ArityException.create(2, 2, arguments.length);
                }
                try {
                    Object result;
                    Object jArg0 = toJavaNode.execute(arguments[0]);
                    Object jArg1 = toJavaNode.execute(arguments[1]);
                    if (builtinMethodDescriptor != null) {
                        assert callTarget == null;
                        result = executeNode.executeObject(null, builtinMethodDescriptor, jArg0, jArg1);
                    } else {
                        assert callTarget != null;
                        result = callTargetInvokeNode.execute(inliningTarget, callTarget, createPArguments(jArg0, jArg1));
                    }
                    return toNativeNode.execute(result);
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "PyCFunction(METH_NOARGS)", getDelegate());
                }
            } catch (PException e) {
                transformExceptionToNativeNode.execute(null, inliningTarget, e);
                return PythonContext.get(gil).getNativeNull().getPtr();
            } finally {
                CApiTiming.exit(timing);
                gil.release(mustRelease);
            }
        }

        private static Object[] createPArguments(Object arg0, Object arg1) {
            Object[] pArguments = PArguments.create(2);
            PArguments.setArgument(pArguments, 0, arg0);
            PArguments.setArgument(pArguments, 1, arg1);
            return pArguments;
        }

        @Override
        protected String getSignature() {
            return "(POINTER,POINTER):POINTER";
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static class PyCFunctionVarargsWrapper extends PyCFunctionWrapper {

        protected PyCFunctionVarargsWrapper(CallTarget callTarget) {
            super(callTarget);
        }

        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @Bind("$node") Node inliningTarget,
                        @Cached PythonToNativeNewRefNode toNativeNode,
                        @Cached ExecutePositionalStarargsNode posStarargsNode,
                        @Cached PyCFunctionWrapperCallTargetInvokeNode callTargetInvokeNode,
                        @Cached NativeToPythonNode toJavaNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            boolean mustRelease = gil.acquire();
            CApiTiming.enter();
            try {
                if (arguments.length != 2) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw ArityException.create(2, 2, arguments.length);
                }
                try {
                    Object result;
                    Object receiver = toJavaNode.execute(arguments[0]);
                    Object starArgs = toJavaNode.execute(arguments[1]);
                    // currently, we do not have a BuiltinMethodDescriptor for varargs functions
                    assert builtinMethodDescriptor == null;
                    assert callTarget != null;
                    Object[] starArgsArray = posStarargsNode.executeWith(null, starArgs);
                    result = callTargetInvokeNode.execute(inliningTarget, callTarget, createPArguments(receiver, starArgsArray));
                    return toNativeNode.execute(result);
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "PyCFunction(METH_VARARGS)", getDelegate());
                }
            } catch (PException e) {
                transformExceptionToNativeNode.execute(null, inliningTarget, e);
                return PythonContext.get(gil).getNativeNull().getPtr();
            } finally {
                CApiTiming.exit(timing);
                gil.release(mustRelease);
            }
        }

        private static Object[] createPArguments(Object receiver, Object[] varargs) {
            Object[] pArguments = PArguments.create(1);
            PArguments.setArgument(pArguments, 0, receiver);
            PArguments.setVariableArguments(pArguments, varargs);
            return pArguments;
        }

        @Override
        protected String getSignature() {
            return "(POINTER,POINTER):POINTER";
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static class PyCFunctionKeywordsWrapper extends PyCFunctionWrapper {

        protected PyCFunctionKeywordsWrapper(CallTarget callTarget) {
            super(callTarget);
        }

        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @Bind("$node") Node inliningTarget,
                        @Cached PythonToNativeNewRefNode toNativeNode,
                        @Cached ExecutePositionalStarargsNode posStarargsNode,
                        @Cached PyCFunctionWrapperCallTargetInvokeNode callTargetInvokeNode,
                        @Cached ExpandKeywordStarargsNode expandKwargsNode,
                        @Cached NativeToPythonNode toJavaNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Exclusive @Cached GilNode gil) throws ArityException {
            boolean mustRelease = gil.acquire();
            CApiTiming.enter();
            try {
                if (arguments.length != 3) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw ArityException.create(3, 3, arguments.length);
                }
                try {
                    Object receiver = toJavaNode.execute(arguments[0]);
                    Object starArgs = toJavaNode.execute(arguments[1]);
                    Object kwArgs = toJavaNode.execute(arguments[2]);
                    // currently, we do not have a BuiltinMethodDescriptor for varargs functions
                    assert builtinMethodDescriptor == null;
                    assert callTarget != null;

                    Object[] starArgsArray = posStarargsNode.executeWith(null, starArgs);
                    PKeyword[] kwArgsArray = expandKwargsNode.execute(inliningTarget, kwArgs);
                    Object result = callTargetInvokeNode.execute(inliningTarget, callTarget, createPArguments(receiver, starArgsArray, kwArgsArray));
                    return toNativeNode.execute(result);
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, "PyCFunction(METH_KEYWORDS)", getDelegate());
                }
            } catch (PException e) {
                transformExceptionToNativeNode.execute(null, inliningTarget, e);
                return PythonContext.get(gil).getNativeNull().getPtr();
            } finally {
                CApiTiming.exit(timing);
                gil.release(mustRelease);
            }
        }

        private static Object[] createPArguments(Object receiver, Object[] varargs, PKeyword[] kwargs) {
            Object[] pArguments = PArguments.create(1);
            PArguments.setArgument(pArguments, 0, receiver);
            PArguments.setVariableArguments(pArguments, varargs);
            PArguments.setKeywordArguments(pArguments, kwargs);
            return pArguments;
        }

        @Override
        protected String getSignature() {
            return "(POINTER,POINTER,POINTER):POINTER";
        }
    }
}
