/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.nfi2.NativeMemory.NULLPTR;
import static com.oracle.truffle.api.CompilerDirectives.shouldNotReachHere;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTiming;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeToPythonNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNewRefNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.TransformExceptionToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtContext;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.nfi2.Nfi;
import com.oracle.graal.python.nfi2.NfiType;
import com.oracle.graal.python.nfi2.NfiUpcallSignature;
import com.oracle.graal.python.nodes.argument.CreateArgumentsNode;
import com.oracle.graal.python.nodes.argument.keywords.ExpandKeywordStarargsNode;
import com.oracle.graal.python.nodes.argument.positional.ExecutePositionalStarargsNode;
import com.oracle.graal.python.nodes.call.CallDispatchers.SimpleIndirectInvokeNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * A wrapper class for managed functions such that they can be called with native function pointers
 * (like C type {@code PyCFunction}). This is very similar to {@link PyProcsWrapper} but the main
 * difference is that this wrapper does not keep a reference to the function object but only to the
 * {@link RootCallTarget}
 * <p>
 * Since in C, function pointers are expected to valid the whole time, NFI closure must be kept
 * alive as long as the context lives. Referencing a function object like {@link PyProcsWrapper}
 * does may therefore cause significant memory leaks.
 * </p>
 */
public abstract class PyCFunctionWrapper {

    private static final NfiUpcallSignature SIGNATURE_1_ARG = Nfi.createUpcallSignature(NfiType.RAW_POINTER, NfiType.RAW_POINTER);
    private static final NfiUpcallSignature SIGNATURE_2_ARG = Nfi.createUpcallSignature(NfiType.RAW_POINTER, NfiType.RAW_POINTER, NfiType.RAW_POINTER);
    private static final NfiUpcallSignature SIGNATURE_3_ARG = Nfi.createUpcallSignature(NfiType.RAW_POINTER, NfiType.RAW_POINTER, NfiType.RAW_POINTER, NfiType.RAW_POINTER);

    private static final MethodHandle HANDLE_UNARY;
    private static final MethodHandle HANDLE_BINARY;
    private static final MethodHandle HANDLE_VARARGS;
    private static final MethodHandle HANDLE_KEYWORDS;

    static {
        try {
            HANDLE_UNARY = MethodHandles.lookup().findStatic(PyCFunctionUnaryWrapper.class, "executeUnary", MethodType.methodType(long.class, PyCFunctionUnaryWrapper.class, long.class));
            HANDLE_BINARY = MethodHandles.lookup().findStatic(PyCFunctionBinaryWrapper.class, "executeBinary",
                            MethodType.methodType(long.class, PyCFunctionBinaryWrapper.class, long.class, long.class));
            HANDLE_VARARGS = MethodHandles.lookup().findStatic(PyCFunctionVarargsWrapper.class, "executeVarargs",
                            MethodType.methodType(long.class, PyCFunctionVarargsWrapper.class, long.class, long.class));
            HANDLE_KEYWORDS = MethodHandles.lookup().findStatic(PyCFunctionKeywordsWrapper.class, "executeKeywords",
                            MethodType.methodType(long.class, PyCFunctionKeywordsWrapper.class, long.class, long.class, long.class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    protected final RootCallTarget callTarget;
    protected final Signature signature;
    protected final TruffleString callTargetName;
    protected final CApiTiming timing;
    private final long pointer;

    /**
     * Built-in functions may appear as {@link CExtContext#METH_VARARGS} etc. but we implement them
     * as nodes where the specializations have a fixed arity and then sometimes allow optional
     * arguments (specified with {@link Builtin#minNumOfPositionalArgs()},
     * {@link Builtin#maxNumOfPositionalArgs()}, and similar). This is usually not a problem because
     * if the built-in function is called via the function object, this will provide the correct
     * number of default values. In this case, the call target of the built-in function will be
     * called directly. So, we need to manually provide the number of default values (which is
     * {@link PNone#NO_VALUE} to indicate that the argument is missing).
     */
    protected final Object[] defaults;

    @SuppressWarnings("this-escape")
    protected PyCFunctionWrapper(RootCallTarget callTarget, Signature signature, Object[] defaults, NfiUpcallSignature upcallSignature, MethodHandle methodHandle) {
        assert callTarget != null;
        assert signature != null;
        this.callTarget = callTarget;
        this.signature = signature;
        this.defaults = defaults;
        String ctName = callTarget.getRootNode().getName();
        this.callTargetName = PythonUtils.toTruffleStringUncached(ctName);
        this.timing = CApiTiming.create(false, ctName);
        CApiContext cApiContext = PythonContext.get(null).getCApiContext();
        this.pointer = cApiContext.registerClosure(getClass().getSimpleName(), upcallSignature, methodHandle.bindTo(this), this, getDelegate());
    }

    public final RootCallTarget getCallTarget() {
        return callTarget;
    }

    public final Object getDelegate() {
        assert callTarget != null;
        return callTarget;
    }

    public final long getPointer() {
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
        return PyCFunctionWrapper.toString(callTargetName, getFlagsRepr(), pointer);
    }

    /**
     * Creates a wrapper for a {@link PBuiltinFunction} that can go to native. The flags are
     * required to determine the signature. The resulting {@link PyCFunctionWrapper} will not
     * reference the built-in function object but will only wrap its {@link RootCallTarget}.
     */
    @TruffleBoundary
    public static PyCFunctionWrapper createFromBuiltinFunction(CApiContext cApiContext, PBuiltinFunction builtinFunction) {
        int flags = builtinFunction.getFlags();

        RootCallTarget ct = builtinFunction.getCallTarget();
        Signature signature = builtinFunction.getSignature();
        Object[] defaults = builtinFunction.getDefaults();
        if (CExtContext.isMethNoArgs(flags)) {
            return cApiContext.getOrCreatePyCFunctionWrapper(ct, k -> new PyCFunctionUnaryWrapper(k, signature, defaults));
        } else if (CExtContext.isMethO(flags)) {
            return cApiContext.getOrCreatePyCFunctionWrapper(ct, k -> new PyCFunctionBinaryWrapper(k, signature, defaults));
        } else if (CExtContext.isMethVarargs(flags)) {
            return cApiContext.getOrCreatePyCFunctionWrapper(ct, k -> new PyCFunctionVarargsWrapper(k, signature, defaults));
        } else if (CExtContext.isMethVarargsWithKeywords(flags)) {
            return cApiContext.getOrCreatePyCFunctionWrapper(ct, k -> new PyCFunctionKeywordsWrapper(k, signature, defaults));
        } else {
            throw shouldNotReachHere("other signature " + Integer.toHexString(flags));
        }
    }

    static final class PyCFunctionUnaryWrapper extends PyCFunctionWrapper {

        PyCFunctionUnaryWrapper(RootCallTarget callTarget, Signature signature, Object[] defaults) {
            super(callTarget, signature, defaults, SIGNATURE_1_ARG, HANDLE_UNARY);
        }

        @SuppressWarnings("try")
        private static long executeUnary(PyCFunctionUnaryWrapper self, long arg0) {
            try (var gil = GilNode.uncachedAcquire()) {
                CApiTiming.enter();
                try {
                    Object jArg0 = NativeToPythonNode.executeRawUncached(arg0);
                    Object[] pArgs = CreateArgumentsNode.executeUncached(self.callTargetName, PythonUtils.EMPTY_OBJECT_ARRAY, PKeyword.EMPTY_KEYWORDS, self.signature, jArg0, null,
                                    self.defaults, PKeyword.EMPTY_KEYWORDS, false);
                    Object result = SimpleIndirectInvokeNode.executeUncached(self.callTarget, pArgs);
                    return PythonToNativeNewRefNode.executeLongUncached(result);
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, self.toString(), "");
                }
            } catch (PException e) {
                TransformExceptionToNativeNode.executeUncached(e);
                return NULLPTR;
            } finally {
                CApiTiming.exit(self.timing);
            }
        }

        @Override
        protected String getFlagsRepr() {
            return "METH_NOARGS";
        }
    }

    static final class PyCFunctionBinaryWrapper extends PyCFunctionWrapper {

        PyCFunctionBinaryWrapper(RootCallTarget callTarget, Signature signature, Object[] defaults) {
            super(callTarget, signature, defaults, SIGNATURE_2_ARG, HANDLE_BINARY);
        }

        @SuppressWarnings("try")
        private static long executeBinary(PyCFunctionBinaryWrapper self, long arg0, long arg1) {
            try (var gil = GilNode.uncachedAcquire()) {
                CApiTiming.enter();
                try {
                    Object jArg0 = NativeToPythonNode.executeRawUncached(arg0);
                    Object jArg1 = NativeToPythonNode.executeRawUncached(arg1);
                    Object[] pArgs = CreateArgumentsNode.executeUncached(self.callTargetName, new Object[]{jArg1}, PKeyword.EMPTY_KEYWORDS, self.signature, jArg0, null,
                                    self.defaults, PKeyword.EMPTY_KEYWORDS, false);
                    Object result = SimpleIndirectInvokeNode.executeUncached(self.callTarget, pArgs);
                    return PythonToNativeNewRefNode.executeLongUncached(result);
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, self.toString(), "");
                }
            } catch (PException e) {
                TransformExceptionToNativeNode.executeUncached(e);
                return NULLPTR;
            } finally {
                CApiTiming.exit(self.timing);
            }
        }

        @Override
        protected String getFlagsRepr() {
            return "METH_O";
        }
    }

    static final class PyCFunctionVarargsWrapper extends PyCFunctionWrapper {

        PyCFunctionVarargsWrapper(RootCallTarget callTarget, Signature signature, Object[] defaults) {
            super(callTarget, signature, defaults, SIGNATURE_2_ARG, HANDLE_VARARGS);
        }

        @SuppressWarnings("try")
        private static long executeVarargs(PyCFunctionVarargsWrapper self, long arg0, long arg1) {
            try (var gil = GilNode.uncachedAcquire()) {
                CApiTiming.enter();
                try {
                    Object receiver = NativeToPythonNode.executeRawUncached(arg0);
                    Object starArgs = NativeToPythonNode.executeRawUncached(arg1);
                    Object[] starArgsArray = ExecutePositionalStarargsNode.executeUncached(starArgs);
                    Object[] pArgs = CreateArgumentsNode.executeUncached(self.callTargetName, starArgsArray, PKeyword.EMPTY_KEYWORDS, self.signature, receiver, null,
                                    self.defaults, PKeyword.EMPTY_KEYWORDS, false);
                    Object result = SimpleIndirectInvokeNode.executeUncached(self.callTarget, pArgs);
                    return PythonToNativeNewRefNode.executeLongUncached(result);
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, self.toString(), "");
                }
            } catch (PException e) {
                TransformExceptionToNativeNode.executeUncached(e);
                return NULLPTR;
            } finally {
                CApiTiming.exit(self.timing);
            }
        }

        @Override
        protected String getFlagsRepr() {
            return "METH_VARARGS";
        }
    }

    static final class PyCFunctionKeywordsWrapper extends PyCFunctionWrapper {

        PyCFunctionKeywordsWrapper(RootCallTarget callTarget, Signature signature, Object[] defaults) {
            super(callTarget, signature, defaults, SIGNATURE_3_ARG, HANDLE_KEYWORDS);
        }

        @SuppressWarnings("try")
        private static long executeKeywords(PyCFunctionKeywordsWrapper self, long arg0, long arg1, long arg2) {
            try (var gil = GilNode.uncachedAcquire()) {
                CApiTiming.enter();
                try {
                    Object receiver = NativeToPythonNode.executeRawUncached(arg0);
                    Object starArgs = NativeToPythonNode.executeRawUncached(arg1);
                    Object kwArgs = NativeToPythonNode.executeRawUncached(arg2);
                    Object[] starArgsArray = ExecutePositionalStarargsNode.executeUncached(starArgs);
                    PKeyword[] kwArgsArray = ExpandKeywordStarargsNode.getUncached().execute(null, kwArgs);
                    Object[] pArgs = CreateArgumentsNode.executeUncached(self.callTargetName, starArgsArray, kwArgsArray, self.signature, receiver, null,
                                    self.defaults, PKeyword.EMPTY_KEYWORDS, false);
                    Object result = SimpleIndirectInvokeNode.executeUncached(self.callTarget, pArgs);
                    return PythonToNativeNewRefNode.executeLongUncached(result);
                } catch (Throwable t) {
                    throw checkThrowableBeforeNative(t, self.toString(), "");
                }
            } catch (PException e) {
                TransformExceptionToNativeNode.executeUncached(e);
                return NULLPTR;
            } finally {
                CApiTiming.exit(self.timing);
            }
        }

        @Override
        protected String getFlagsRepr() {
            return "METH_KEYWORDS";
        }
    }
}
