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

import com.oracle.graal.python.builtins.modules.TruffleCextBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.CExtBaseNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.ToJavaNode;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.ToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.NativeWrappers.PThreadState;
import com.oracle.graal.python.builtins.objects.cext.NativeWrappers.PySequenceArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.PThreadStateMRFactory.GetTypeIDNodeGen;
import com.oracle.graal.python.builtins.objects.cext.PThreadStateMRFactory.ThreadStateReadNodeGen;
import com.oracle.graal.python.builtins.objects.cext.PThreadStateMRFactory.ThreadStateWriteNodeGen;
import com.oracle.graal.python.builtins.objects.cext.PythonObjectNativeWrapperMR.InvalidateNativeObjectsAllManagedNode;
import com.oracle.graal.python.builtins.objects.cext.PythonObjectNativeWrapperMR.ToPyObjectNode;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.traceback.PTraceback;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;

@MessageResolution(receiverType = PThreadState.class)
public class PThreadStateMR {

    public static final String CUR_EXC_TYPE = "curexc_type";
    public static final String CUR_EXC_VALUE = "curexc_value";
    public static final String CUR_EXC_TRACEBACK = "curexc_traceback";
    public static final String EXC_TYPE = "exc_type";
    public static final String EXC_VALUE = "exc_value";
    public static final String EXC_TRACEBACK = "exc_traceback";
    public static final String DICT = "dict";
    public static final String PREV = "prev";

    @SuppressWarnings("unknown-message")
    @Resolve(message = "com.oracle.truffle.llvm.spi.GetDynamicType")
    abstract static class GetDynamicTypeNode extends Node {
        @Child private GetTypeIDNode getTypeIDNode;

        public Object access(@SuppressWarnings("unused") PThreadState object) {
            if (getTypeIDNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getTypeIDNode = insert(GetTypeIDNode.create());
            }
            return getTypeIDNode.execute();
        }
    }

    @Resolve(message = "READ")
    abstract static class ReadNode extends Node {
        @Child private ThreadStateReadNode readNode = ThreadStateReadNodeGen.create();
        @Child private ToSulongNode toSulongNode = ToSulongNode.create();

        public Object access(@SuppressWarnings("unused") PThreadState object, String key) {
            Object result = readNode.execute(key);
            return toSulongNode.execute(result != null ? result : PNone.NO_VALUE);
        }
    }

    @Resolve(message = "WRITE")
    abstract static class WriteNode extends Node {
        @Child private ThreadStateWriteNode writeNode = ThreadStateWriteNodeGen.create();
        @Child private ToJavaNode toJavaNode = ToJavaNode.create();
        @Child private ToSulongNode toSulongNode = ToSulongNode.create();

        public Object access(@SuppressWarnings("unused") PThreadState object, String key, Object value) {
            Object result = writeNode.execute(key, toJavaNode.execute(value));
            return toSulongNode.execute(result != null ? result : PNone.NO_VALUE);
        }
    }

    @ImportStatic(PThreadStateMR.class)
    abstract static class ThreadStateReadNode extends PNodeWithContext {
        @Child private GetClassNode getClassNode;
        @Child private ReadAttributeFromObjectNode readNativeNull;

        public abstract Object execute(Object key);

        @Specialization(guards = "eq(key, CUR_EXC_TYPE)")
        PythonAbstractClass doCurExcType(@SuppressWarnings("unused") String key) {
            PythonContext context = getContext();
            PException currentException = context.getCurrentException();
            if (currentException != null) {
                PBaseException exceptionObject = currentException.getExceptionObject();
                return getGetClassNode().execute(exceptionObject);
            }
            return null;
        }

        @Specialization(guards = "eq(key, CUR_EXC_VALUE)")
        PBaseException doCurExcValue(@SuppressWarnings("unused") String key) {
            PythonContext context = getContext();
            PException currentException = context.getCurrentException();
            if (currentException != null) {
                PBaseException exceptionObject = currentException.getExceptionObject();
                return exceptionObject;
            }
            return null;
        }

        @Specialization(guards = "eq(key, CUR_EXC_TRACEBACK)")
        PTraceback doCurExcTraceback(@SuppressWarnings("unused") String key) {
            PythonContext context = getContext();
            PException currentException = context.getCurrentException();
            if (currentException != null) {
                PBaseException exceptionObject = currentException.getExceptionObject();
                return exceptionObject.getTraceback(factory());
            }
            return null;
        }

        @Specialization(guards = "eq(key, EXC_TYPE)")
        PythonAbstractClass doExcType(@SuppressWarnings("unused") String key) {
            PythonContext context = getContext();
            PException currentException = context.getCaughtException();
            if (currentException != null) {
                PBaseException exceptionObject = currentException.getExceptionObject();
                return getGetClassNode().execute(exceptionObject);
            }
            return null;
        }

        @Specialization(guards = "eq(key, EXC_VALUE)")
        PBaseException doExcValue(@SuppressWarnings("unused") String key) {
            PythonContext context = getContext();
            PException currentException = context.getCaughtException();
            if (currentException != null) {
                PBaseException exceptionObject = currentException.getExceptionObject();
                return exceptionObject;
            }
            return null;
        }

        @Specialization(guards = "eq(key, EXC_TRACEBACK)")
        PTraceback doExcTraceback(@SuppressWarnings("unused") String key) {
            PythonContext context = getContext();
            PException currentException = context.getCaughtException();
            if (currentException != null) {
                PBaseException exceptionObject = currentException.getExceptionObject();
                return exceptionObject.getTraceback(factory());
            }
            return null;
        }

        @Specialization(guards = "eq(key, DICT)")
        PDict doDict(@SuppressWarnings("unused") String key) {
            PThreadState customThreadState = getContext().getCustomThreadState();
            PDict threadStateDict = customThreadState.getThreadStateDict();
            if (threadStateDict == null) {
                threadStateDict = factory().createDict();
                customThreadState.setThreadStateDict(threadStateDict);
            }
            return threadStateDict;
        }

        @Specialization(guards = "eq(key, PREV)")
        Object doPrev(@SuppressWarnings("unused") String key) {
            return getNativeNull();
        }

        protected Object getNativeNull() {
            if (readNativeNull == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readNativeNull = insert(ReadAttributeFromObjectNode.create());
            }
            Object wrapper = readNativeNull.execute(getCore().lookupBuiltinModule("python_cext"), TruffleCextBuiltins.NATIVE_NULL);
            assert wrapper instanceof PythonNativeNull;
            return wrapper;
        }

        protected static boolean eq(String key, String expected) {
            return expected.equals(key);
        }

        private GetClassNode getGetClassNode() {
            if (getClassNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getClassNode = insert(GetClassNode.create());
            }
            return getClassNode;
        }
    }

    @ImportStatic(PThreadStateMR.class)
    abstract static class ThreadStateWriteNode extends PNodeWithContext {
        public abstract Object execute(Object key, Object value);

        @Specialization(guards = "isCurrentExceptionMember(key)")
        PNone doResetCurException(@SuppressWarnings("unused") String key, @SuppressWarnings("unused") PNone value) {
            getContext().setCurrentException(null);
            return PNone.NO_VALUE;
        }

        @Specialization(guards = "isCaughtExceptionMember(key)")
        PNone doResetCaughtException(@SuppressWarnings("unused") String key, @SuppressWarnings("unused") PNone value) {
            getContext().setCaughtException(null);
            return PNone.NO_VALUE;
        }

        @Specialization(guards = "eq(key, CUR_EXC_TYPE)")
        LazyPythonClass doCurExcType(@SuppressWarnings("unused") String key, LazyPythonClass value) {
            setCurrentException(factory().createBaseException(value));
            return value;
        }

        @Specialization(guards = "eq(key, CUR_EXC_VALUE)")
        PBaseException doCurExcValue(@SuppressWarnings("unused") String key, PBaseException value) {
            setCurrentException(value);
            return value;
        }

        @Specialization(guards = "eq(key, CUR_EXC_TRACEBACK)")
        PTraceback doCurExcTraceback(@SuppressWarnings("unused") String key, PTraceback value) {
            setCurrentException(value.getException());
            return value;
        }

        @Specialization(guards = "eq(key, EXC_TYPE)")
        LazyPythonClass doExcType(@SuppressWarnings("unused") String key, LazyPythonClass value) {
            setCaughtException(factory().createBaseException(value));
            return value;
        }

        @Specialization(guards = "eq(key, EXC_VALUE)")
        PBaseException doExcValue(@SuppressWarnings("unused") String key, PBaseException value) {
            setCaughtException(value);
            return value;
        }

        @Specialization(guards = "eq(key, EXC_TRACEBACK)")
        PTraceback doExcTraceback(@SuppressWarnings("unused") String key, PTraceback value) {
            setCaughtException(value.getException());
            return value;
        }

        private void setCurrentException(PBaseException exceptionObject) {
            try {
                throw raise(exceptionObject);
            } catch (PException e) {
                exceptionObject.reifyException();
                getContext().setCurrentException(e);
            }
        }

        private void setCaughtException(PBaseException exceptionObject) {
            try {
                throw raise(exceptionObject);
            } catch (PException e) {
                exceptionObject.reifyException();
                getContext().setCurrentException(e);
            }
        }

        @Fallback
        @TruffleBoundary
        Object doGeneric(Object key, @SuppressWarnings("unused") Object value) {
            throw UnknownIdentifierException.raise(key.toString());
        }

        protected static boolean eq(String key, String expected) {
            return expected.equals(key);
        }

        protected static boolean isCurrentExceptionMember(String key) {
            return eq(key, CUR_EXC_TYPE) || eq(key, CUR_EXC_VALUE) || eq(key, CUR_EXC_TRACEBACK);
        }

        protected static boolean isCaughtExceptionMember(String key) {
            return eq(key, EXC_TYPE) || eq(key, EXC_VALUE) || eq(key, EXC_TRACEBACK);
        }
    }

    @Resolve(message = "TO_NATIVE")
    abstract static class ToNativeNode extends Node {
        @Child private ToPyObjectNode toPyObjectNode = ToPyObjectNode.create();
        @Child private InvalidateNativeObjectsAllManagedNode invalidateNode = InvalidateNativeObjectsAllManagedNode.create();

        Object access(PThreadState obj) {
            invalidateNode.execute();
            if (!obj.isNative()) {
                obj.setNativePointer(toPyObjectNode.execute(obj));
            }
            return obj;
        }
    }

    @Resolve(message = "IS_POINTER")
    abstract static class IsPointerNode extends Node {
        @Child private CExtNodes.IsPointerNode pIsPointerNode = CExtNodes.IsPointerNode.create();

        boolean access(PThreadState obj) {
            return pIsPointerNode.execute(obj);
        }
    }

    @Resolve(message = "AS_POINTER")
    abstract static class AsPointerNode extends Node {
        @Child private Node asPointerNode;

        long access(PySequenceArrayWrapper obj) {
            // the native pointer object must either be a TruffleObject or a primitive
            Object nativePointer = obj.getNativePointer();
            if (nativePointer instanceof TruffleObject) {
                if (asPointerNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    asPointerNode = insert(Message.AS_POINTER.createNode());
                }
                try {
                    return ForeignAccess.sendAsPointer(asPointerNode, (TruffleObject) nativePointer);
                } catch (UnsupportedMessageException e) {
                    throw e.raise();
                }
            }
            return (long) nativePointer;
        }
    }

    abstract static class GetTypeIDNode extends CExtBaseNode {

        @Child private PCallNativeNode callUnaryNode;

        @CompilationFinal private TruffleObject funGetThreadStateTypeID;

        public abstract Object execute();

        @Specialization(assumptions = "singleContextAssumption()")
        Object doByteArray(@Cached("callGetByteArrayTypeID()") Object nativeType) {
            // TODO(fa): use weak reference ?
            return nativeType;
        }

        @Specialization(replaces = "doByteArray")
        Object doByteArrayMultiCtx() {
            return callGetByteArrayTypeIDCached();
        }

        protected Object callGetByteArrayTypeID() {
            return callGetArrayTypeID(importCAPISymbol(NativeCAPISymbols.FUN_GET_THREAD_STATE_TYPE_ID));
        }

        private Object callGetByteArrayTypeIDCached() {
            if (funGetThreadStateTypeID == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                funGetThreadStateTypeID = importCAPISymbol(NativeCAPISymbols.FUN_GET_THREAD_STATE_TYPE_ID);
            }
            return callGetArrayTypeID(funGetThreadStateTypeID);
        }

        private Object callGetArrayTypeID(TruffleObject fun) {
            if (callUnaryNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callUnaryNode = insert(PCallNativeNode.create());
            }
            return callUnaryNode.execute(fun, new Object[0]);
        }

        public static GetTypeIDNode create() {
            return GetTypeIDNodeGen.create();
        }
    }
}
