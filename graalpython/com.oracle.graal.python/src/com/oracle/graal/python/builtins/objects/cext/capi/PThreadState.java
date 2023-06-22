/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
// skip GIL
package com.oracle.graal.python.builtins.objects.cext.capi;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext.LLVMType;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.GetLLVMType;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeToPythonNode;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.traceback.MaterializeLazyTracebackNode;
import com.oracle.graal.python.builtins.objects.traceback.LazyTraceback;
import com.oracle.graal.python.builtins.objects.traceback.PTraceback;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonContext.PythonThreadState;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;

/**
 * Emulates CPython's {@code PyThreadState} struct.
 */
@ExportLibrary(InteropLibrary.class)
@ExportLibrary(value = NativeTypeLibrary.class, useForAOT = false)
public final class PThreadState extends PythonNativeWrapper {
    public static final String J_CUR_EXC_TYPE = "curexc_type";
    public static final String J_CUR_EXC_VALUE = "curexc_value";
    public static final String J_CUR_EXC_TRACEBACK = "curexc_traceback";
    public static final String J_EXC_TYPE = "exc_type";
    public static final String J_EXC_VALUE = "exc_value";
    public static final String J_EXC_INFO = "exc_info";
    public static final String J_EXC_TRACEBACK = "exc_traceback";
    public static final String J_DICT = "dict";
    public static final String J_PREV = "prev";
    public static final String J_RECURSION_DEPTH = "recursion_depth";
    public static final String J_OVERFLOWED = "overflowed";
    public static final String J_INTERP = "interp";
    public static final String J_USE_TRACING = "use_tracing";
    public static final String J_GILSTATE_COUNTER = "gilstate_counter";

    private final PythonThreadState threadState;

    private PThreadState(PythonThreadState threadState) {
        this.threadState = threadState;
    }

    public static PThreadState getThreadState(PythonLanguage language, PythonContext context) {
        PythonThreadState threadState = context.getThreadState(language);
        PThreadState nativeWrapper = threadState.getNativeWrapper();
        if (nativeWrapper == null) {
            nativeWrapper = new PThreadState(threadState);
            threadState.setNativeWrapper(nativeWrapper);
        }
        // does not require a 'to_sulong' since it is already a native wrapper type
        return nativeWrapper;
    }

    public PythonThreadState getThreadState() {
        return threadState;
    }

    // READ
    @ExportMessage
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    protected boolean isMemberReadable(String member) {
        switch (member) {
            case J_CUR_EXC_TYPE:
            case J_CUR_EXC_VALUE:
            case J_CUR_EXC_TRACEBACK:
            case J_EXC_TYPE:
            case J_EXC_VALUE:
            case J_EXC_INFO:
            case J_EXC_TRACEBACK:
            case J_DICT:
            case J_PREV:
            case J_RECURSION_DEPTH:
            case J_OVERFLOWED:
            case J_INTERP:
            case J_USE_TRACING:
            case J_GILSTATE_COUNTER:
                return true;
            default:
                return false;
        }
    }

    @ExportMessage
    protected Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        return new PythonAbstractObject.Keys(
                        new Object[]{J_CUR_EXC_TYPE, J_CUR_EXC_VALUE, J_CUR_EXC_TRACEBACK, J_EXC_TYPE, J_EXC_VALUE, J_EXC_TRACEBACK, J_DICT, J_PREV, J_RECURSION_DEPTH, J_OVERFLOWED, J_INTERP,
                                        J_USE_TRACING, J_GILSTATE_COUNTER});
    }

    @ImportStatic(PThreadState.class)
    @GenerateUncached
    @ExportMessage
    abstract static class ReadMember {

        @Specialization(guards = "eq(key, J_CUR_EXC_TYPE)")
        static Object doCurExcType(PThreadState receiver, @SuppressWarnings("unused") String key,
                        @Shared("toSulong") @Cached ToSulongNode toSulongNode,
                        @Shared("getClassNode") @Cached GetClassNode getClassNode) {
            PException currentException = receiver.threadState.getCurrentException();
            Object result = null;
            if (currentException != null) {
                result = getClassNode.execute(currentException.getUnreifiedException());
            }
            return toSulongNode.execute(result != null ? result : PNone.NO_VALUE);
        }

        @Specialization(guards = "eq(key, J_CUR_EXC_VALUE)")
        static Object doCurExcValue(PThreadState receiver, @SuppressWarnings("unused") String key,
                        @Shared("toSulong") @Cached ToSulongNode toSulongNode) {
            PException currentException = receiver.threadState.getCurrentException();
            Object result = null;
            if (currentException != null) {
                result = currentException.getEscapedException();
            }
            return toSulongNode.execute(result != null ? result : PNone.NO_VALUE);
        }

        @Specialization(guards = "eq(key, J_CUR_EXC_TRACEBACK)")
        static Object doCurExcTraceback(PThreadState receiver, @SuppressWarnings("unused") String key,
                        @Shared("toSulong") @Cached ToSulongNode toSulongNode,
                        @Shared("getTraceback") @Cached MaterializeLazyTracebackNode materializeLazyTracebackNode) {
            PException currentException = receiver.threadState.getCurrentException();
            PTraceback result = null;
            if (currentException != null) {
                LazyTraceback traceback = currentException.getTraceback();
                result = traceback != null ? materializeLazyTracebackNode.execute(traceback) : null;
            }
            return toSulongNode.execute(result != null ? result : PNone.NO_VALUE);
        }

        @Specialization(guards = "eq(key, J_EXC_TYPE)")
        static Object doExcType(PThreadState receiver, @SuppressWarnings("unused") String key,
                        @Shared("toSulong") @Cached ToSulongNode toSulongNode,
                        @Shared("getClassNode") @Cached GetClassNode getClassNode) {
            PException caughtException = receiver.threadState.getCaughtException();
            Object result = null;
            if (caughtException != null) {
                result = getClassNode.execute(caughtException.getUnreifiedException());
            }
            return toSulongNode.execute(result != null ? result : PNone.NO_VALUE);
        }

        @Specialization(guards = "eq(key, J_EXC_VALUE)")
        static Object doExcValue(PThreadState receiver, @SuppressWarnings("unused") String key,
                        @Shared("toSulong") @Cached ToSulongNode toSulongNode) {
            PException currentException = receiver.threadState.getCaughtException();
            Object result = null;
            if (currentException != null) {
                result = currentException.getEscapedException();
            }
            return toSulongNode.execute(result != null ? result : PNone.NO_VALUE);
        }

        @Specialization(guards = "eq(key, J_EXC_TRACEBACK)")
        static Object doExcTraceback(PThreadState receiver, @SuppressWarnings("unused") String key,
                        @Shared("toSulong") @Cached ToSulongNode toSulongNode,
                        @Shared("getTraceback") @Cached MaterializeLazyTracebackNode materializeLazyTracebackNode) {
            PException currentException = receiver.threadState.getCaughtException();
            PTraceback result = null;
            if (currentException != null) {
                result = materializeLazyTracebackNode.execute(currentException.getTraceback());
            }
            return toSulongNode.execute(result != null ? result : PNone.NO_VALUE);
        }

        @Specialization(guards = "eq(key, J_DICT)")
        static Object doDict(PThreadState receiver, @SuppressWarnings("unused") String key,
                        @Cached PythonObjectFactory factory,
                        @Shared("toSulong") @Cached ToSulongNode toSulongNode) {
            PDict threadStateDict = receiver.threadState.getDict();
            if (threadStateDict == null) {
                threadStateDict = factory.createDict();
                receiver.threadState.setDict(threadStateDict);
            }
            return toSulongNode.execute(threadStateDict);
        }

        @Specialization(guards = "eq(key, J_PREV)")
        @SuppressWarnings("unused")
        static Object doPrev(PThreadState receiver, String key,
                        @CachedLibrary("receiver") InteropLibrary receiverLib) {
            return PythonContext.get(receiverLib).getNativeNull().getPtr();
        }

        @Specialization(guards = "eq(key, J_EXC_INFO)")
        static Object doExcInfo(PThreadState receiver, @SuppressWarnings("unused") String key) {
            PException currentException = receiver.threadState.getCaughtException();
            PBaseException caughtExceptionObject = null;
            if (currentException != null) {
                caughtExceptionObject = currentException.getEscapedException();
            }
            /*
             * CPython initializes 'PyThreadState->exc_info' with 'PyThreadState->exc_state' where
             * the latter is always allocated but its the members may be NULL. So, we must always
             * allow access to the stack item even if there is no exception.
             */
            return new PyErrStackItem(caughtExceptionObject);
        }

        private static class DepthCounter implements FrameInstanceVisitor<Object> {
            public long depth = 0;

            public Object visitFrame(FrameInstance frameInstance) {
                depth++;
                return null;
            }
        }

        @Specialization(guards = "eq(key, J_RECURSION_DEPTH)")
        @SuppressWarnings("unused")
        static long doRecursionDepth(PThreadState receiver, String key) {
            DepthCounter visitor = new DepthCounter();
            Truffle.getRuntime().iterateFrames(visitor);
            return visitor.depth;
        }

        @Specialization(guards = "eq(key, J_OVERFLOWED)")
        @SuppressWarnings("unused")
        static long doOverflowed(PThreadState receiver, String key) {
            return 0;
        }

        @Specialization(guards = "eq(key, J_INTERP)")
        @SuppressWarnings("unused")
        static Object doInterpreterState(PThreadState receiver, String key,
                        @CachedLibrary("receiver") InteropLibrary receiverLib) {
            return PythonContext.get(receiverLib).getNativeNull().getPtr();
        }

        @Specialization(guards = "eq(key, J_USE_TRACING)")
        @SuppressWarnings("unused")
        static long doUseTracing(PThreadState receiver, String key) {
            return 0;
        }

        @Specialization(guards = "eq(key, J_GILSTATE_COUNTER)")
        @SuppressWarnings("unused")
        static long doGilstateCounter(PThreadState receiver, String key) {
            return 1;
        }

        protected static boolean eq(String key, String expected) {
            return expected.equals(key);
        }
    }

    // WRITE
    @ExportMessage
    protected boolean isMemberModifiable(String member) {
        switch (member) {
            case J_CUR_EXC_TYPE:
            case J_CUR_EXC_VALUE:
            case J_CUR_EXC_TRACEBACK:
            case J_EXC_TYPE:
            case J_EXC_VALUE:
            case J_EXC_TRACEBACK:
            case J_RECURSION_DEPTH:
            case J_OVERFLOWED:
            case J_GILSTATE_COUNTER:
                return true;
            default:
                return false;
        }
    }

    @ExportMessage
    protected boolean isMemberInsertable(@SuppressWarnings("unused") String member) {
        return false;
    }

    @ExportMessage
    protected void writeMember(String member, Object value,
                    @Cached ThreadStateWriteNode writeNode,
                    @Cached NativeToPythonNode toJavaNode) throws UnknownIdentifierException {
        writeNode.execute(threadState, member, toJavaNode.execute(value));
    }

    @ExportMessage
    protected boolean isMemberRemovable(@SuppressWarnings("unused") String member) {
        return false;
    }

    @ExportMessage
    protected void removeMember(@SuppressWarnings("unused") String member) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @ImportStatic(PThreadState.class)
    @GenerateUncached
    abstract static class ThreadStateWriteNode extends PNodeWithContext {
        public abstract Object execute(PythonThreadState threadState, Object key, Object value) throws UnknownIdentifierException;

        @Specialization(guards = {"isCurrentExceptionMember(key)", "isResetValue(value, lib)"})
        @SuppressWarnings("unused")
        static PNone doResetCurException(PythonThreadState threadState, String key, Object value,
                        @Shared("lib") @CachedLibrary(limit = "1") InteropLibrary lib) {
            threadState.setCaughtException(null);
            return PNone.NO_VALUE;
        }

        @Specialization(guards = {"isCaughtExceptionMember(key)", "isResetValue(value, lib)"})
        @SuppressWarnings("unused")
        static PNone doResetCaughtException(PythonThreadState threadState, String key, PNone value,
                        @Shared("lib") @CachedLibrary(limit = "1") InteropLibrary lib) {
            threadState.setCaughtException(PException.NO_EXCEPTION);
            return PNone.NO_VALUE;
        }

        @Specialization(guards = "eq(key, J_CUR_EXC_TYPE)")
        Object doCurExcType(PythonThreadState threadState, @SuppressWarnings("unused") String key, Object value,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            setCurrentException(getLanguage(), threadState, factory.createBaseException(value));
            return value;
        }

        @Specialization(guards = "eq(key, J_CUR_EXC_VALUE)")
        PBaseException doCurExcValue(PythonThreadState threadState, @SuppressWarnings("unused") String key, PBaseException value) {
            setCurrentException(getLanguage(), threadState, value);
            return value;
        }

        @Specialization(guards = "eq(key, J_CUR_EXC_TRACEBACK)")
        PTraceback doCurExcTraceback(PythonThreadState threadState, @SuppressWarnings("unused") String key, PTraceback value) {
            PException e = threadState.getCurrentException();
            threadState.setCurrentException(PException.fromExceptionInfo(e.getUnreifiedException(), value, PythonOptions.isPExceptionWithJavaStacktrace(getLanguage())));
            return value;
        }

        @Specialization(guards = "eq(key, J_EXC_TYPE)")
        PythonClass doExcType(PythonThreadState threadState, @SuppressWarnings("unused") String key, PythonClass value,
                        @Shared("factory") @Cached PythonObjectFactory factory) {
            setCaughtException(getLanguage(), threadState, factory.createBaseException(value));
            return value;
        }

        @Specialization(guards = "eq(key, J_EXC_VALUE)")
        PBaseException doExcValue(PythonThreadState threadState, @SuppressWarnings("unused") String key, PBaseException value) {
            setCaughtException(getLanguage(), threadState, value);
            return value;
        }

        @Specialization(guards = "eq(key, J_EXC_TRACEBACK)")
        PTraceback doExcTraceback(PythonThreadState threadState, @SuppressWarnings("unused") String key, PTraceback value) {
            PException e = threadState.getCaughtException();
            boolean withJavaStacktrace = PythonOptions.isPExceptionWithJavaStacktrace(getLanguage());
            threadState.setCaughtException(PException.fromExceptionInfo(e.getUnreifiedException(), value, withJavaStacktrace));
            return value;
        }

        @Specialization(guards = "eq(key, J_RECURSION_DEPTH)")
        @SuppressWarnings("unused")
        static Object doRecursionDepth(PythonThreadState threadState, String key, int value) {
            // TODO: (tfel) Can we not ignore this?
            return null;
        }

        @Specialization(guards = "eq(key, J_OVERFLOWED)")
        @SuppressWarnings("unused")
        static Object doOverflowed(PythonThreadState threadState, String key, int value) {
            // TODO: (tfel) Can we not ignore this?
            return null;
        }

        @Specialization(guards = "eq(key, J_GILSTATE_COUNTER)")
        @SuppressWarnings("unused")
        static Object doGilstateCounter(PythonThreadState threadState, String key, int value) {
            // Ignoring reference counting, always reporting 1
            return null;
        }

        @Specialization(guards = {"!isCurrentExceptionMember(key)", "!isCaughtExceptionMember(key)"})
        static Object doGeneric(@SuppressWarnings("unused") PythonThreadState threadState, Object key, @SuppressWarnings("unused") Object value) throws UnknownIdentifierException {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw UnknownIdentifierException.create(key.toString());
        }

        private static void setCurrentException(PythonLanguage language, PythonThreadState threadState, PBaseException exceptionObject) {
            boolean withJavaStacktrace = PythonOptions.isPExceptionWithJavaStacktrace(language);
            PException currentException = threadState.getCurrentException();
            LazyTraceback traceback = currentException != null ? currentException.getTraceback() : null;
            PException curException = PException.fromExceptionInfo(exceptionObject, traceback, withJavaStacktrace);
            threadState.setCurrentException(curException);
        }

        private static void setCaughtException(PythonLanguage language, PythonThreadState threadState, PBaseException exceptionObject) {
            boolean withJavaStacktrace = PythonOptions.isPExceptionWithJavaStacktrace(language);
            PException oldCaughtException = threadState.getCaughtException();
            LazyTraceback traceback = oldCaughtException != null ? oldCaughtException.getTraceback() : null;
            PException caughtException = PException.fromExceptionInfo(exceptionObject, traceback, withJavaStacktrace);
            threadState.setCaughtException(caughtException);
        }

        protected static boolean eq(Object key, String expected) {
            return expected.equals(key);
        }

        protected static boolean isCurrentExceptionMember(Object key) {
            return eq(key, J_CUR_EXC_TYPE) || eq(key, J_CUR_EXC_VALUE) || eq(key, J_CUR_EXC_TRACEBACK);
        }

        protected static boolean isCaughtExceptionMember(Object key) {
            return eq(key, J_EXC_TYPE) || eq(key, J_EXC_VALUE) || eq(key, J_EXC_TRACEBACK);
        }

        protected static boolean isResetValue(Object value, InteropLibrary lib) {
            // TODO(fa): workaround until Sulong supports this
            if (lib.isNumber(value) && lib.fitsInLong(value)) {
                try {
                    long lvalue = lib.asLong(value);
                    return lvalue == 0;
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere();
                }
            }
            return value instanceof PNone;
        }
    }

    // TO POINTER / AS POINTER / TO NATIVE
    @ExportMessage
    protected boolean isPointer() {
        return isNative();
    }

    @ExportMessage
    public long asPointer() {
        return getNativePointer();
    }

    @ExportMessage
    protected void toNative(
                    @Bind("$node") Node inliningTarget,
                    @Cached InlinedConditionProfile isNativeProfile) {
        if (!isNative(inliningTarget, isNativeProfile)) {
            CApiTransitions.firstToNative(this);
        }
    }

    @ExportMessage
    protected boolean hasNativeType() {
        return true;
    }

    @ExportMessage(name = "getNativeType")
    Object getNativeType(
                    @Cached GetLLVMType getLLVMType) {
        return getLLVMType.execute(LLVMType.PyThreadState);
    }
}
