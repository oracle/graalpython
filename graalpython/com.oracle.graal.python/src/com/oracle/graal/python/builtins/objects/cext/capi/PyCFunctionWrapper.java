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

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.TransformExceptionToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTiming;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeToPythonNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNewRefNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtContext;
import com.oracle.graal.python.builtins.objects.function.BuiltinMethodDescriptor;
import com.oracle.graal.python.builtins.objects.function.BuiltinMethodDescriptor.BinaryBuiltinDescriptor;
import com.oracle.graal.python.builtins.objects.function.BuiltinMethodDescriptor.UnaryBuiltinDescriptor;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.nodes.argument.CreateArgumentsNode.CreateAndCheckArgumentsNode;
import com.oracle.graal.python.nodes.argument.keywords.ExpandKeywordStarargsNode;
import com.oracle.graal.python.nodes.argument.positional.ExecutePositionalStarargsNode;
import com.oracle.graal.python.nodes.call.CallTargetInvokeNode;
import com.oracle.graal.python.nodes.call.GenericInvokeNode;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CallTarget;
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
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.nfi.api.SignatureLibrary;

/**
 * A wrapper class for managed functions such that they can be called with native function pointers
 * (like C type {@code PyCFunction}). This is very similar to {@link PyProcsWrapper} but the main
 * difference is that this wrapper does not keep a reference to the function object but only to
 * either the {@link RootCallTarget} or the {@link BuiltinMethodDescriptor} (in case of built-in
 * functions).
 * <p>
 * Since in C, function pointers are expected to valid the whole time, NFI closure must be kept
 * alive as long as the context lives. Referencing a function object like {@link PyProcsWrapper}
 * does may therefore cause significant memory leaks.
 * </p>
 */
@ExportLibrary(InteropLibrary.class)
public abstract class PyCFunctionWrapper implements TruffleObject {

    protected final RootCallTarget callTarget;
    protected final Signature signature;
    protected final TruffleString callTargetName;
    protected final BuiltinMethodDescriptor builtinMethodDescriptor;
    protected final CApiTiming timing;
    private long pointer;

    protected PyCFunctionWrapper(RootCallTarget callTarget, Signature signature) {
        assert callTarget != null;
        assert signature != null;
        this.callTarget = callTarget;
        this.signature = signature;
        String ctName = callTarget.getRootNode().getName();
        this.callTargetName = PythonUtils.toTruffleStringUncached(ctName);
        this.builtinMethodDescriptor = null;
        this.timing = CApiTiming.create(false, ctName);
    }

    protected PyCFunctionWrapper(BuiltinMethodDescriptor builtinMethodDescriptor) {
        assert builtinMethodDescriptor != null;
        this.callTarget = null;
        this.signature = null;
        this.callTargetName = null;
        this.builtinMethodDescriptor = builtinMethodDescriptor;
        this.timing = CApiTiming.create(false, builtinMethodDescriptor.getName());
    }

    public final RootCallTarget getCallTarget() {
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
    protected void toNative(
                    @CachedLibrary(limit = "1") SignatureLibrary signatureLibrary) {
        if (pointer == 0) {
            CApiContext cApiContext = PythonContext.get(null).getCApiContext();
            pointer = cApiContext.registerClosure(getSignature(), this, getDelegate(), signatureLibrary);
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

    protected abstract String getFlagsRepr();

    @TruffleBoundary
    private static String toString(Object name, String flagsRepr, long pointer) {
        String ptr = pointer != 0 ? " at 0x" + Long.toHexString(pointer) : "";
        return String.format("PyCFunction(%s, %s)%s", name, flagsRepr, ptr);
    }

    @Override
    @TruffleBoundary
    public String toString() {
        return PyCFunctionWrapper.toString(builtinMethodDescriptor != null ? builtinMethodDescriptor.getName() : callTargetName, getFlagsRepr(), pointer);
    }

    /**
     * Creates a wrapper for a {@link PBuiltinFunction} that can go to native. The flags are
     * required to determine the signature. The resulting {@link PyCFunctionWrapper} will not
     * reference the built-in function object but will only wrap either its
     * {@link BuiltinMethodDescriptor} (if available) or its {@link RootCallTarget}.
     */
    @TruffleBoundary
    public static PyCFunctionWrapper createFromBuiltinFunction(CApiContext cApiContext, PBuiltinFunction builtinFunction) {
        int flags = builtinFunction.getFlags();

        // try to use the BuiltinMethodDescriptor if available
        BuiltinMethodDescriptor builtinMethodDescriptor = BuiltinMethodDescriptor.get(builtinFunction);
        if (builtinMethodDescriptor != null) {
            /*
             * If we create a PyCFunctionWrapper for a BuiltinMethodDescriptor, we need to register
             * the call target because it may happen that the wrapper is used to create another
             * 'builtin_function_or_method' or 'method_descriptor' in which case we need to have the
             * call target available.
             */
            if (CExtContext.isMethNoArgs(flags) && builtinMethodDescriptor instanceof UnaryBuiltinDescriptor ||
                            CExtContext.isMethO(flags) && builtinMethodDescriptor instanceof BinaryBuiltinDescriptor) {
                cApiContext.getContext().getLanguage().registerBuiltinDescriptorCallTarget(builtinMethodDescriptor, builtinFunction.getCallTarget());
            }
            if (CExtContext.isMethNoArgs(flags) && builtinMethodDescriptor instanceof UnaryBuiltinDescriptor) {
                return cApiContext.getOrCreatePyCFunctionWrapper(builtinMethodDescriptor, PyCFunctionUnaryWrapper::new);
            } else if (CExtContext.isMethO(flags) && builtinMethodDescriptor instanceof BinaryBuiltinDescriptor) {
                return cApiContext.getOrCreatePyCFunctionWrapper(builtinMethodDescriptor, PyCFunctionBinaryWrapper::new);
            }
        }
        RootCallTarget ct = builtinFunction.getCallTarget();
        Signature signature = builtinFunction.getSignature();
        if (CExtContext.isMethNoArgs(flags)) {
            return cApiContext.getOrCreatePyCFunctionWrapper(ct, k -> new PyCFunctionUnaryWrapper(k, signature));
        } else if (CExtContext.isMethO(flags)) {
            return cApiContext.getOrCreatePyCFunctionWrapper(ct, k -> new PyCFunctionBinaryWrapper(k, signature));
        } else if (CExtContext.isMethVarargs(flags)) {
            int numDefaults = builtinFunction.getDefaults().length;
            return cApiContext.getOrCreatePyCFunctionWrapper(ct, k -> new PyCFunctionVarargsWrapper(k, signature, numDefaults));
        } else if (CExtContext.isMethVarargsWithKeywords(flags)) {
            int numDefaults = builtinFunction.getDefaults().length;
            return cApiContext.getOrCreatePyCFunctionWrapper(ct, k -> new PyCFunctionKeywordsWrapper(k, signature, numDefaults));
        } else {
            throw CompilerDirectives.shouldNotReachHere("other signature " + Integer.toHexString(flags));
        }
    }

    /**
     * This is very much like {@link com.oracle.graal.python.nodes.call.CallDispatchNode} but just
     * for calling {@link CallTarget call tagets} directly instead of function/method objects. This
     * node essentially serves as an inline cache for the invoked call target. This node will
     * automatically fall back to a {@link GenericInvokeNode} if the inline cache flows over.
     */
    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    abstract static class CallTargetDispatchNode extends Node {

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
    static final class PyCFunctionUnaryWrapper extends PyCFunctionWrapper {

        PyCFunctionUnaryWrapper(RootCallTarget callTarget, Signature signature) {
            super(callTarget, signature);
        }

        private PyCFunctionUnaryWrapper(BuiltinMethodDescriptor builtinMethodDescriptor) {
            super(builtinMethodDescriptor);
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @Bind("$node") Node inliningTarget,
                        @Cached PythonToNativeNewRefNode toNativeNode,
                        @Cached CallUnaryMethodNode callUnaryNode,
                        @Cached CreateAndCheckArgumentsNode createArgsNode,
                        @Cached CallTargetDispatchNode invokeNode,
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
                    Object jArg0 = toJavaNode.execute(arguments[0]);
                    if (builtinMethodDescriptor != null) {
                        assert callTarget == null;
                        result = callUnaryNode.executeObject(null, builtinMethodDescriptor, jArg0);
                    } else {
                        assert callTarget != null;
                        assert callTargetName != null;
                        Object[] pArgs = createArgsNode.execute(inliningTarget, callTargetName, PythonUtils.EMPTY_OBJECT_ARRAY, PKeyword.EMPTY_KEYWORDS, signature, jArg0, null,
                                        PythonUtils.EMPTY_OBJECT_ARRAY, PKeyword.EMPTY_KEYWORDS, false);
                        result = invokeNode.execute(inliningTarget, callTarget, pArgs);
                    }
                    return toNativeNode.execute(result);
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, toString(), "");
                }
            } catch (PException e) {
                transformExceptionToNativeNode.execute(inliningTarget, e);
                return PythonContext.get(gil).getNativeNull();
            } finally {
                CApiTiming.exit(timing);
                gil.release(mustRelease);
            }
        }

        @Override
        protected String getSignature() {
            return "(POINTER):POINTER";
        }

        @Override
        protected String getFlagsRepr() {
            return "METH_NOARGS";
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class PyCFunctionBinaryWrapper extends PyCFunctionWrapper {

        PyCFunctionBinaryWrapper(RootCallTarget callTarget, Signature signature) {
            super(callTarget, signature);
        }

        private PyCFunctionBinaryWrapper(BuiltinMethodDescriptor builtinMethodDescriptor) {
            super(builtinMethodDescriptor);
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @Bind("$node") Node inliningTarget,
                        @Cached PythonToNativeNewRefNode toNativeNode,
                        @Cached CallBinaryMethodNode callBinaryMethodNode,
                        @Cached CallTargetDispatchNode invokeNode,
                        @Cached CreateAndCheckArgumentsNode createArgsNode,
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
                        assert builtinMethodDescriptor instanceof BinaryBuiltinDescriptor;
                        result = callBinaryMethodNode.executeObject(builtinMethodDescriptor, jArg0, jArg1);
                    } else {
                        assert callTarget != null;
                        assert callTargetName != null;
                        Object[] pArgs = createArgsNode.execute(inliningTarget, callTargetName, new Object[]{jArg1}, PKeyword.EMPTY_KEYWORDS, signature, jArg0, null,
                                        PythonUtils.EMPTY_OBJECT_ARRAY, PKeyword.EMPTY_KEYWORDS, false);
                        result = invokeNode.execute(inliningTarget, callTarget, pArgs);
                    }
                    return toNativeNode.execute(result);
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, toString(), "");
                }
            } catch (PException e) {
                transformExceptionToNativeNode.execute(inliningTarget, e);
                return PythonContext.get(gil).getNativeNull();
            } finally {
                CApiTiming.exit(timing);
                gil.release(mustRelease);
            }
        }

        @Override
        protected String getSignature() {
            return "(POINTER,POINTER):POINTER";
        }

        @Override
        protected String getFlagsRepr() {
            return "METH_O";
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class PyCFunctionVarargsWrapper extends PyCFunctionWrapper {

        /**
         * Built-in functions may appear as {@link CExtContext#METH_VARARGS} but we implement them
         * as nodes where the specializations have a fixed arity and then sometimes allow optional
         * arguments (specified with {@link Builtin#minNumOfPositionalArgs()},
         * {@link Builtin#maxNumOfPositionalArgs()}, and similar). This is usually not a problem
         * because if the built-in function is called via the function object, this will provide the
         * correct number of default values. In this case, the call target of the built-in function
         * will be called directly. So, we need to manually provide the number of default values
         * (which is {@link PNone#NO_VALUE} to indicate that the argument is missing).
         */
        private final int numDefaults;

        PyCFunctionVarargsWrapper(RootCallTarget callTarget, Signature signature, int numDefaults) {
            super(callTarget, signature);
            assert numDefaults >= 0;
            this.numDefaults = numDefaults;
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @Bind("$node") Node inliningTarget,
                        @Cached PythonToNativeNewRefNode toNativeNode,
                        @Cached ExecutePositionalStarargsNode posStarargsNode,
                        @Cached CreateAndCheckArgumentsNode createArgsNode,
                        @Cached CallTargetDispatchNode invokeNode,
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
                    Object[] pArgs = createArgsNode.execute(inliningTarget, callTargetName, starArgsArray, PKeyword.EMPTY_KEYWORDS, signature, receiver, null,
                                    PBuiltinFunction.generateDefaults(numDefaults), PKeyword.EMPTY_KEYWORDS, false);
                    result = invokeNode.execute(inliningTarget, callTarget, pArgs);
                    return toNativeNode.execute(result);
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, toString(), "");
                }
            } catch (PException e) {
                transformExceptionToNativeNode.execute(inliningTarget, e);
                return PythonContext.get(gil).getNativeNull();
            } finally {
                CApiTiming.exit(timing);
                gil.release(mustRelease);
            }
        }

        @Override
        protected String getSignature() {
            return "(POINTER,POINTER):POINTER";
        }

        @Override
        protected String getFlagsRepr() {
            return "METH_VARARGS";
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class PyCFunctionKeywordsWrapper extends PyCFunctionWrapper {

        /**
         * see {@link PyCFunctionVarargsWrapper#numDefaults}
         */
        private final int numDefaults;

        PyCFunctionKeywordsWrapper(RootCallTarget callTarget, Signature signature, int numDefaults) {
            super(callTarget, signature);
            assert numDefaults >= 0;
            this.numDefaults = numDefaults;
        }

        @ExportMessage
        Object execute(Object[] arguments,
                        @Bind("$node") Node inliningTarget,
                        @Cached PythonToNativeNewRefNode toNativeNode,
                        @Cached ExecutePositionalStarargsNode posStarargsNode,
                        @Cached CreateAndCheckArgumentsNode createArgsNode,
                        @Cached CallTargetDispatchNode invokeNode,
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
                    assert callTargetName != null;

                    Object[] starArgsArray = posStarargsNode.executeWith(null, starArgs);
                    PKeyword[] kwArgsArray = expandKwargsNode.execute(inliningTarget, kwArgs);
                    Object[] pArgs = createArgsNode.execute(inliningTarget, callTargetName, starArgsArray, kwArgsArray, signature, receiver, null, PBuiltinFunction.generateDefaults(numDefaults),
                                    PKeyword.EMPTY_KEYWORDS, false);
                    Object result = invokeNode.execute(inliningTarget, callTarget, pArgs);
                    return toNativeNode.execute(result);
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, toString(), "");
                }
            } catch (PException e) {
                transformExceptionToNativeNode.execute(inliningTarget, e);
                return PythonContext.get(gil).getNativeNull();
            } finally {
                CApiTiming.exit(timing);
                gil.release(mustRelease);
            }
        }

        @Override
        protected String getSignature() {
            return "(POINTER,POINTER,POINTER):POINTER";
        }

        @Override
        protected String getFlagsRepr() {
            return "METH_KEYWORDS";
        }
    }
}
