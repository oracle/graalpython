package com.oracle.graal.python.builtins.objects.cext;

import static com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols.FUN_GET_THREAD_STATE_TYPE_ID;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.modules.TruffleCextBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.traceback.PTraceback;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.CachedLanguage;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;

@ExportLibrary(InteropLibrary.class)
@ExportLibrary(NativeTypeLibrary.class)
public class PThreadState extends NativeWrappers.PythonNativeWrapper {
    public static final String CUR_EXC_TYPE = "curexc_type";
    public static final String CUR_EXC_VALUE = "curexc_value";
    public static final String CUR_EXC_TRACEBACK = "curexc_traceback";
    public static final String EXC_TYPE = "exc_type";
    public static final String EXC_VALUE = "exc_value";
    public static final String EXC_TRACEBACK = "exc_traceback";
    public static final String DICT = "dict";
    public static final String PREV = "prev";

    private PDict dict;

    static boolean isInstance(TruffleObject obj) {
        return obj instanceof PThreadState;
    }

    public PDict getThreadStateDict() {
        return dict;
    }

    public void setThreadStateDict(PDict dict) {
        this.dict = dict;
    }

    // READ
    @ExportMessage
    @Override
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    @Override
    protected boolean isMemberReadable(String member) {
        switch (member) {
            case CUR_EXC_TYPE:
            case CUR_EXC_VALUE:
            case CUR_EXC_TRACEBACK:
            case EXC_TYPE:
            case EXC_VALUE:
            case EXC_TRACEBACK:
            case DICT:
            case PREV:
                return true;
            default:
                return false;
        }
    }

    @ExportMessage
    @Override
    protected Object getMembers(boolean includeInternal) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    protected Object readMember(String member,
                    @Cached.Exclusive @Cached(allowUncached = true) ReadNode readNode) {
        return readNode.execute(this, member);
    }

    abstract static class ReadNode extends Node {
        public abstract Object execute(PThreadState object, String key);

        @Specialization
        public Object execute(@SuppressWarnings("unused") PThreadState object, String key,
                        @Cached.Exclusive @Cached ThreadStateReadNode readNode,
                        @Cached.Exclusive @Cached CExtNodes.ToSulongNode toSulongNode) {
            Object result = readNode.execute(key);
            return toSulongNode.execute(result != null ? result : PNone.NO_VALUE);
        }
    }

    @ImportStatic(PThreadState.class)
    abstract static class ThreadStateReadNode extends PNodeWithContext {
        public abstract Object execute(Object key);

        @Specialization(guards = "eq(key, CUR_EXC_TYPE)")
        PythonClass doCurExcType(@SuppressWarnings("unused") String key,
                        @Cached.Shared("getClassNode") @Cached GetClassNode getClassNode) {
            PythonContext context = getContext();
            PException currentException = context.getCurrentException();
            if (currentException != null) {
                PBaseException exceptionObject = currentException.getExceptionObject();
                return getClassNode.execute(exceptionObject);
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
        PythonClass doExcType(@SuppressWarnings("unused") String key,
                        @Cached.Shared("getClassNode") @Cached GetClassNode getClassNode) {
            PythonContext context = getContext();
            PException currentException = context.getCaughtException();
            if (currentException != null) {
                PBaseException exceptionObject = currentException.getExceptionObject();
                return getClassNode.execute(exceptionObject);
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
        Object doPrev(@SuppressWarnings("unused") String key,
                        @Cached.Exclusive @Cached ReadAttributeFromObjectNode readNativeNull) {
            return getNativeNull(readNativeNull);
        }

        protected Object getNativeNull(ReadAttributeFromObjectNode readNativeNull) {
            Object wrapper = readNativeNull.execute(getCore().lookupBuiltinModule("python_cext"), TruffleCextBuiltins.NATIVE_NULL);
            assert wrapper instanceof PythonNativeNull;
            return wrapper;
        }

        protected static boolean eq(String key, String expected) {
            return expected.equals(key);
        }
    }

    // WRITE
    @ExportMessage
    @Override
    protected boolean isMemberModifiable(String member) {
        switch (member) {
            case CUR_EXC_TYPE:
            case CUR_EXC_VALUE:
            case CUR_EXC_TRACEBACK:
            case EXC_TYPE:
            case EXC_VALUE:
            case EXC_TRACEBACK:
                return true;
            default:
                return false;
        }
    }

    @ExportMessage
    @Override
    protected boolean isMemberInsertable(String member) {
        // TODO: cbasca, fangerer is this true ?
        switch (member) {
            case CUR_EXC_TYPE:
            case CUR_EXC_VALUE:
            case CUR_EXC_TRACEBACK:
            case EXC_TYPE:
            case EXC_VALUE:
            case EXC_TRACEBACK:
                return true;
            default:
                return false;
        }
    }

    @ExportMessage
    protected void writeMember(String member, Object value,
                    @Cached.Exclusive @Cached(allowUncached = true) WriteNode writeNode) {
        writeNode.execute(this, member, value);
    }

    @ExportMessage
    @Override
    protected boolean isMemberRemovable(String member) {
        return false;
    }

    @ExportMessage
    @Override
    protected void removeMember(String member) throws UnsupportedMessageException, UnknownIdentifierException {
        throw UnsupportedMessageException.create();
    }

    abstract static class WriteNode extends Node {
        public abstract Object execute(PThreadState object, String key, Object value);

        @Specialization
        public Object execute(@SuppressWarnings("unused") PThreadState object, String key, Object value,
                        @Cached.Exclusive @Cached ThreadStateWriteNode writeNode,
                        @Cached.Exclusive @Cached CExtNodes.ToJavaNode toJavaNode,
                        @Cached.Exclusive @Cached CExtNodes.ToSulongNode toSulongNode) {
            Object result = writeNode.execute(key, toJavaNode.execute(value));
            return toSulongNode.execute(result != null ? result : PNone.NO_VALUE);
        }
    }

    @ImportStatic(PThreadState.class)
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
        PythonClass doCurExcType(@SuppressWarnings("unused") String key, PythonClass value) {
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
        PythonClass doExcType(@SuppressWarnings("unused") String key, PythonClass value) {
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
        @CompilerDirectives.TruffleBoundary
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

    // TO POINTER / AS POINTER / TO NATIVE
    @ExportMessage
    protected boolean isPointer(@Cached.Exclusive @Cached(allowUncached = true) IsPointerNode isPointerNode) {
        return isPointerNode.execute(this);
    }

    @ExportMessage
    public long asPointer(@CachedLibrary(limit = "1") InteropLibrary interopLibrary) throws UnsupportedMessageException {
        Object nativePointer = this.getNativePointer();
        if (nativePointer instanceof Long) {
            return (long) nativePointer;
        }
        return interopLibrary.asPointer(nativePointer);
    }

    @ExportMessage
    protected void toNative(@Cached.Exclusive @Cached(allowUncached = true) ToNativeNode toNativeNode) {
        toNativeNode.execute(this);
    }

    @ExportMessage
    protected boolean hasNativeType() {
        return true;
    }

    @ExportMessage(name = "getNativeType")
    abstract static class GetTypeIDNode {
        @Specialization(assumptions = "language.singleContextAssumption")
        static Object doByteArray(@SuppressWarnings("unused") PThreadState receiver,
                        @CachedLanguage @SuppressWarnings("unused") PythonLanguage language,
                        @Exclusive @Cached("callGetThreadStateTypeIDUncached()") Object nativeType) {
            // TODO(fa): use weak reference ?
            return nativeType;
        }

        @Specialization(replaces = "doByteArray")
        static Object doByteArrayMultiCtx(@SuppressWarnings("unused") PThreadState receiver,
                        @Exclusive @Cached PCallCapiFunction callUnaryNode) {
            return callUnaryNode.call(FUN_GET_THREAD_STATE_TYPE_ID);
        }

        protected static Object callGetThreadStateTypeIDUncached() {
            return PCallCapiFunction.getUncached().call(FUN_GET_THREAD_STATE_TYPE_ID);
        }

// protected static Assumption singleContextAssumption() {
// return PythonContext.getSingleNativeContextAssumption();
// }
    }

    abstract static class ToNativeNode extends Node {
        public abstract Object execute(PThreadState obj);

        @Specialization
        Object execute(PThreadState obj,
                        @Cached.Exclusive @Cached ToPyObjectNode toPyObjectNode,
                        @Cached.Exclusive @Cached InvalidateNativeObjectsAllManagedNode invalidateNode) {
            invalidateNode.execute();
            if (!obj.isNative()) {
                obj.setNativePointer(toPyObjectNode.execute(obj));
            }
            return obj;
        }
    }

    abstract static class IsPointerNode extends Node {
        public abstract boolean execute(PThreadState obj);

        @Specialization
        boolean access(PThreadState obj,
                        @Cached.Exclusive @Cached CExtNodes.IsPointerNode pIsPointerNode) {
            return pIsPointerNode.execute(obj);
        }
    }
}
