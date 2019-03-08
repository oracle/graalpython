package com.oracle.graal.python.builtins.objects.cext;

import static com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols.FUN_GET_THREAD_STATE_TYPE_ID;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.modules.TruffleCextBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.DynamicObjectNativeWrapper.ToPyObjectNode;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.traceback.PTraceback;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;

@ExportLibrary(InteropLibrary.class)
@ExportLibrary(NativeTypeLibrary.class)
public class PThreadState extends PythonNativeWrapper {
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
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
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
    protected Object getMembers(@SuppressWarnings("unused") boolean includeInternal) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @ExportMessage
    protected Object readMember(String member,
                    @Exclusive @Cached ThreadStateReadNode readNode,
                    @Exclusive @Cached CExtNodes.ToSulongNode toSulongNode) {
        Object result = readNode.execute(member);
        return toSulongNode.execute(result != null ? result : PNone.NO_VALUE);
    }

    @ImportStatic(PThreadState.class)
    @GenerateUncached
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
                    @Exclusive @Cached ThreadStateWriteNode writeNode,
                    @Exclusive @Cached CExtNodes.ToJavaNode toJavaNode) throws UnknownIdentifierException {
        writeNode.execute(member, toJavaNode.execute(value));
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
        public abstract Object execute(Object key, Object value) throws UnknownIdentifierException;

        @Specialization(guards = "isCurrentExceptionMember(key)")
        PNone doResetCurException(@SuppressWarnings("unused") String key, @SuppressWarnings("unused") PNone value,
                        @Shared("context") @CachedContext(PythonLanguage.class) PythonContext context) {
            context.setCurrentException(null);
            return PNone.NO_VALUE;
        }

        @Specialization(guards = "isCaughtExceptionMember(key)")
        PNone doResetCaughtException(@SuppressWarnings("unused") String key, @SuppressWarnings("unused") PNone value,
                        @Shared("context") @CachedContext(PythonLanguage.class) PythonContext context) {
            context.setCaughtException(null);
            return PNone.NO_VALUE;
        }

        @Specialization(guards = "eq(key, CUR_EXC_TYPE)")
        PythonClass doCurExcType(@SuppressWarnings("unused") String key, PythonClass value,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode,
                        @Shared("context") @CachedContext(PythonLanguage.class) PythonContext context) {
            setCurrentException(raiseNode, context, factory().createBaseException(value));
            return value;
        }

        @Specialization(guards = "eq(key, CUR_EXC_VALUE)")
        PBaseException doCurExcValue(@SuppressWarnings("unused") String key, PBaseException value,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode,
                        @Shared("context") @CachedContext(PythonLanguage.class) PythonContext context) {
            setCurrentException(raiseNode, context, value);
            return value;
        }

        @Specialization(guards = "eq(key, CUR_EXC_TRACEBACK)")
        PTraceback doCurExcTraceback(@SuppressWarnings("unused") String key, PTraceback value,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode,
                        @Shared("context") @CachedContext(PythonLanguage.class) PythonContext context) {
            setCurrentException(raiseNode, context, value.getException());
            return value;
        }

        @Specialization(guards = "eq(key, EXC_TYPE)")
        PythonClass doExcType(@SuppressWarnings("unused") String key, PythonClass value,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode,
                        @Shared("context") @CachedContext(PythonLanguage.class) PythonContext context) {
            setCaughtException(raiseNode, context, factory().createBaseException(value));
            return value;
        }

        @Specialization(guards = "eq(key, EXC_VALUE)")
        PBaseException doExcValue(@SuppressWarnings("unused") String key, PBaseException value,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode,
                        @Shared("context") @CachedContext(PythonLanguage.class) PythonContext context) {
            setCaughtException(raiseNode, context, value);
            return value;
        }

        @Specialization(guards = "eq(key, EXC_TRACEBACK)")
        PTraceback doExcTraceback(@SuppressWarnings("unused") String key, PTraceback value,
                        @Shared("raiseNode") @Cached PRaiseNode raiseNode,
                        @Shared("context") @CachedContext(PythonLanguage.class) PythonContext context) {
            setCaughtException(raiseNode, context, value.getException());
            return value;
        }

        private static void setCurrentException(PRaiseNode raiseNode, PythonContext context, PBaseException exceptionObject) {
            try {
                throw raiseNode.raise(exceptionObject);
            } catch (PException e) {
                exceptionObject.reifyException();
                context.setCurrentException(e);
            }
        }

        private static void setCaughtException(PRaiseNode raiseNode, PythonContext context, PBaseException exceptionObject) {
            try {
                throw raiseNode.raise(exceptionObject);
            } catch (PException e) {
                exceptionObject.reifyException();
                context.setCurrentException(e);
            }
        }

        @Specialization(guards = {"!isCurrentExceptionMember(key)", "!isCaughtExceptionMember(key)"})
        @TruffleBoundary
        Object doGeneric(Object key, @SuppressWarnings("unused") Object value) throws UnknownIdentifierException {
            throw UnknownIdentifierException.create(key.toString());
        }

        protected static boolean eq(Object key, String expected) {
            return expected.equals(key);
        }

        protected static boolean isCurrentExceptionMember(Object key) {
            return eq(key, CUR_EXC_TYPE) || eq(key, CUR_EXC_VALUE) || eq(key, CUR_EXC_TRACEBACK);
        }

        protected static boolean isCaughtExceptionMember(Object key) {
            return eq(key, EXC_TYPE) || eq(key, EXC_VALUE) || eq(key, EXC_TRACEBACK);
        }
    }

    // TO POINTER / AS POINTER / TO NATIVE
    @ExportMessage
    protected boolean isPointer(
                    @Exclusive @Cached CExtNodes.IsPointerNode pIsPointerNode) {
        return pIsPointerNode.execute(this);
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
    protected void toNative(
                    @Exclusive @Cached ToPyObjectNode toPyObjectNode,
                    @Exclusive @Cached InvalidateNativeObjectsAllManagedNode invalidateNode) {
        invalidateNode.execute();
        if (!isNative()) {
            setNativePointer(toPyObjectNode.execute(this));
        }
    }

    @ExportMessage
    protected boolean hasNativeType() {
        return true;
    }

    @ExportMessage(name = "getNativeType")
    abstract static class GetTypeIDNode {
        @Specialization(assumptions = "singleNativeContextAssumption()")
        static Object doByteArray(@SuppressWarnings("unused") PThreadState receiver,
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

        protected static Assumption singleNativeContextAssumption() {
            return PythonContext.getSingleNativeContextAssumption();
        }
    }
}
