package com.oracle.graal.python.builtins.objects.exception;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeMember;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions;
import com.oracle.graal.python.builtins.objects.traceback.MaterializeLazyTracebackNode;
import com.oracle.graal.python.builtins.objects.traceback.PTraceback;
import com.oracle.graal.python.lib.PyExceptionInstanceCheckNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.nodes.Node;

public final class ExceptionNodes {
    private static Object nullToNone(Object obj) {
        return obj != null ? obj : PNone.NONE;
    }

    private static Object noValueToNone(Object obj) {
        return obj != PNone.NO_VALUE ? obj : PNone.NONE;
    }

    private static Object noneToNoValue(Object obj) {
        return obj != PNone.NONE ? obj : PNone.NO_VALUE;
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class GetCauseNode extends Node {
        public abstract Object execute(Node inliningTarget, Object exception);

        public static Object executeUncached(Object e) {
            return ExceptionNodesFactory.GetCauseNodeGen.getUncached().execute(null, e);
        }

        @Specialization
        static Object doManaged(PBaseException exception) {
            return nullToNone(exception.getCause());
        }

        @Specialization(guards = "check.execute(inliningTarget, exception)")
        static Object doNative(@SuppressWarnings("unused") Node inliningTarget, PythonAbstractNativeObject exception,
                        @SuppressWarnings("unused") @Cached PyExceptionInstanceCheckNode check,
                        @Cached CApiTransitions.PythonToNativeNode toNative,
                        @Cached CApiTransitions.NativeToPythonNode toPython,
                        @Cached CExtNodes.PCallCapiFunction callGetter) {
            return noValueToNone(toPython.execute(callGetter.call(NativeMember.CAUSE.getGetterFunctionName(), toNative.execute(exception))));
        }

        @Specialization
        @SuppressWarnings("unused")
        static Object doInterop(Node inliningTarget, AbstractTruffleException exception) {
            return PNone.NONE;
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class SetCauseNode extends Node {
        public abstract void execute(Node inliningTarget, Object exception, Object value);

        public static void executeUncached(Object e, Object value) {
            ExceptionNodesFactory.SetCauseNodeGen.getUncached().execute(null, e, value);
        }

        @Specialization
        static void doManaged(PBaseException exception, Object value) {
            exception.setCause(value);
        }

        @Specialization(guards = "check.execute(inliningTarget, exception)")
        static void doNative(@SuppressWarnings("unused") Node inliningTarget, PythonAbstractNativeObject exception, Object value,
                        @SuppressWarnings("unused") @Cached PyExceptionInstanceCheckNode check,
                        @Cached CApiTransitions.PythonToNativeNode excToNative,
                        @Cached CApiTransitions.PythonToNativeNode valueToNative,
                        @Cached CExtNodes.PCallCapiFunction callGetter) {
            callGetter.call(NativeMember.CAUSE.getSetterFunctionName(), excToNative.execute(exception), valueToNative.execute(value));
        }

        @Specialization
        @SuppressWarnings("unused")
        static void doInterop(Node inliningTarget, AbstractTruffleException exception, Object value) {
            throw PRaiseNode.raiseUncached(inliningTarget, TypeError, ErrorMessages.CANNOT_SET_PROPERTY_ON_INTEROP_EXCEPTION);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class GetContextNode extends Node {
        public abstract Object execute(Node inliningTarget, Object exception);

        public static Object executeUncached(Object e) {
            return ExceptionNodesFactory.GetContextNodeGen.getUncached().execute(null, e);
        }

        @Specialization
        static Object doManaged(PBaseException exception) {
            return nullToNone(exception.getContext());
        }

        @Specialization(guards = "check.execute(inliningTarget, exception)")
        static Object doNative(@SuppressWarnings("unused") Node inliningTarget, PythonAbstractNativeObject exception,
                        @SuppressWarnings("unused") @Cached PyExceptionInstanceCheckNode check,
                        @Cached CApiTransitions.PythonToNativeNode toNative,
                        @Cached CApiTransitions.NativeToPythonNode toPython,
                        @Cached CExtNodes.PCallCapiFunction callGetter) {
            return noValueToNone(toPython.execute(callGetter.call(NativeMember.CONTEXT.getGetterFunctionName(), toNative.execute(exception))));
        }

        @Specialization
        @SuppressWarnings("unused")
        static Object doInterop(Node inliningTarget, AbstractTruffleException exception) {
            return PNone.NONE;
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class SetContextNode extends Node {
        public abstract void execute(Node inliningTarget, Object exception, Object value);

        public static void executeUncached(Object e, Object value) {
            ExceptionNodesFactory.SetContextNodeGen.getUncached().execute(null, e, value);
        }

        @Specialization
        static void doManaged(PBaseException exception, Object value) {
            exception.setContext(value);
        }

        @Specialization(guards = "check.execute(inliningTarget, exception)")
        static void doNative(@SuppressWarnings("unused") Node inliningTarget, PythonAbstractNativeObject exception, Object value,
                        @SuppressWarnings("unused") @Cached PyExceptionInstanceCheckNode check,
                        @Cached CApiTransitions.PythonToNativeNode excToNative,
                        @Cached CApiTransitions.PythonToNativeNode valueToNative,
                        @Cached CExtNodes.PCallCapiFunction callGetter) {
            callGetter.call(NativeMember.CONTEXT.getSetterFunctionName(), excToNative.execute(exception), valueToNative.execute(noneToNoValue(value)));
        }

        @Specialization
        @SuppressWarnings("unused")
        static void doInterop(Node inliningTarget, AbstractTruffleException exception, Object value) {
            throw PRaiseNode.raiseUncached(inliningTarget, TypeError, ErrorMessages.CANNOT_SET_PROPERTY_ON_INTEROP_EXCEPTION);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class GetSuppressContextNode extends Node {
        public abstract boolean execute(Node inliningTarget, Object exception);

        public static boolean executeUncached(Object e) {
            return ExceptionNodesFactory.GetSuppressContextNodeGen.getUncached().execute(null, e);
        }

        @Specialization
        static boolean doManaged(PBaseException exception) {
            return exception.getSuppressContext();
        }

        @Specialization(guards = "check.execute(inliningTarget, exception)")
        static boolean doNative(@SuppressWarnings("unused") Node inliningTarget, PythonAbstractNativeObject exception,
                        @SuppressWarnings("unused") @Cached PyExceptionInstanceCheckNode check,
                        @Cached CApiTransitions.PythonToNativeNode toNative,
                        @Cached CApiTransitions.NativeToPythonNode toPython,
                        @Cached CExtNodes.PCallCapiFunction callGetter) {
            return (int) toPython.execute(callGetter.call(NativeMember.SUPPRESS_CONTEXT.getGetterFunctionName(), toNative.execute(noneToNoValue(exception)))) != 0;
        }

        @Specialization
        @SuppressWarnings("unused")
        static boolean doInterop(Node inliningTarget, AbstractTruffleException exception) {
            return false;
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class SetSuppressContextNode extends Node {
        public abstract void execute(Node inliningTarget, Object exception, boolean value);

        public static void executeUncached(Object e, boolean value) {
            ExceptionNodesFactory.SetSuppressContextNodeGen.getUncached().execute(null, e, value);
        }

        @Specialization
        static void doManaged(PBaseException exception, boolean value) {
            exception.setSuppressContext(value);
        }

        @Specialization(guards = "check.execute(inliningTarget, exception)")
        static void doNative(@SuppressWarnings("unused") Node inliningTarget, PythonAbstractNativeObject exception, boolean value,
                        @SuppressWarnings("unused") @Cached PyExceptionInstanceCheckNode check,
                        @Cached CApiTransitions.PythonToNativeNode excToNative,
                        @Cached CExtNodes.PCallCapiFunction callGetter) {
            callGetter.call(NativeMember.SUPPRESS_CONTEXT.getSetterFunctionName(), excToNative.execute(exception), value ? 1 : 0);
        }

        @Specialization
        @SuppressWarnings("unused")
        static void doInterop(Node inliningTarget, AbstractTruffleException exception, boolean value) {
            throw PRaiseNode.raiseUncached(inliningTarget, TypeError, ErrorMessages.CANNOT_SET_PROPERTY_ON_INTEROP_EXCEPTION);
        }
    }

    /**
     * Use this node to get the traceback object of an exception object. The traceback may need to
     * be created lazily and this node takes care of it.
     */
    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class GetTracebackNode extends Node {

        public abstract Object execute(Node inliningTarget, Object e);

        public static Object executeUncached(Object e) {
            return ExceptionNodesFactory.GetTracebackNodeGen.getUncached().execute(null, e);
        }

        @Specialization
        static Object doManaged(PBaseException e,
                        @Cached MaterializeLazyTracebackNode materializeLazyTracebackNode) {
            PTraceback result = null;
            if (e.getTraceback() != null) {
                result = materializeLazyTracebackNode.execute(e.getTraceback());
            }
            return nullToNone(result);
        }

        @Specialization(guards = "check.execute(inliningTarget, exception)")
        static Object doNative(@SuppressWarnings("unused") Node inliningTarget, PythonAbstractNativeObject exception,
                        @SuppressWarnings("unused") @Cached PyExceptionInstanceCheckNode check,
                        @Cached CApiTransitions.PythonToNativeNode toNative,
                        @Cached CApiTransitions.NativeToPythonNode toPython,
                        @Cached CExtNodes.PCallCapiFunction callGetter) {
            return noValueToNone(toPython.execute(callGetter.call(NativeMember.TRACEBACK.getGetterFunctionName(), toNative.execute(exception))));
        }

        @Specialization
        static Object doForeign(@SuppressWarnings("unused") AbstractTruffleException e) {
            return PNone.NONE;
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class SetTracebackNode extends Node {
        public abstract void execute(Node inliningTarget, Object exception, Object value);

        public static void executeUncached(Object e, Object value) {
            ExceptionNodesFactory.SetTracebackNodeGen.getUncached().execute(null, e, value);
        }

        @Specialization
        static void doManaged(PBaseException exception, @SuppressWarnings("unused") PNone value) {
            exception.clearTraceback();
        }

        @Specialization
        static void doManaged(PBaseException exception, PTraceback value) {
            exception.setTraceback(value);
        }

        @Specialization(guards = "check.execute(inliningTarget, exception)")
        static void doNative(@SuppressWarnings("unused") Node inliningTarget, PythonAbstractNativeObject exception, Object value,
                        @SuppressWarnings("unused") @Cached PyExceptionInstanceCheckNode check,
                        @Cached CApiTransitions.PythonToNativeNode excToNative,
                        @Cached CApiTransitions.PythonToNativeNode valueToNative,
                        @Cached CExtNodes.PCallCapiFunction callGetter) {
            callGetter.call(NativeMember.TRACEBACK.getSetterFunctionName(), excToNative.execute(exception), valueToNative.execute(noneToNoValue(value)));
        }

        @Specialization
        @SuppressWarnings("unused")
        static void doInterop(Node inliningTarget, AbstractTruffleException exception, Object value) {
            throw PRaiseNode.raiseUncached(inliningTarget, TypeError, ErrorMessages.CANNOT_SET_PROPERTY_ON_INTEROP_EXCEPTION);
        }
    }
}
