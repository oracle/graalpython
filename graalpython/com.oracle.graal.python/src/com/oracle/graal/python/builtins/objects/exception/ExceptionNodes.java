package com.oracle.graal.python.builtins.objects.exception;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.util.IllegalFormatException;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeMember;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions;
import com.oracle.graal.python.builtins.objects.traceback.MaterializeLazyTracebackNode;
import com.oracle.graal.python.builtins.objects.traceback.PTraceback;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyExceptionInstanceCheckNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.formatting.ErrorMessageFormatter;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

public final class ExceptionNodes {
    private static Object nullToNone(Object obj) {
        return obj != null ? obj : PNone.NONE;
    }

    private static Object noValueToNone(Object obj) {
        return obj != PNone.NO_VALUE ? obj : PNone.NONE;
    }

    private static Object noneToNativeNull(Node node, Object obj) {
        return obj != PNone.NONE ? obj : PythonContext.get(node).getNativeNull();
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
    @ImportStatic(PGuards.class)
    public abstract static class SetCauseNode extends Node {
        public abstract void execute(Node inliningTarget, Object exception, Object value);

        public static void executeUncached(Object e, Object value) {
            ExceptionNodesFactory.SetCauseNodeGen.getUncached().execute(null, e, value);
        }

        @Specialization(guards = "isNone(value)")
        static void doManaged(PBaseException exception, @SuppressWarnings("unused") PNone value) {
            exception.setCause(null);
        }

        @Specialization(guards = "!isNone(value)")
        static void doManaged(PBaseException exception, Object value) {
            exception.setCause(value);
        }

        @Specialization(guards = "check.execute(inliningTarget, exception)")
        static void doNative(Node inliningTarget, PythonAbstractNativeObject exception, Object value,
                        @SuppressWarnings("unused") @Cached PyExceptionInstanceCheckNode check,
                        @Cached CApiTransitions.PythonToNativeNode excToNative,
                        @Cached CApiTransitions.PythonToNativeNode valueToNative,
                        @Cached CExtNodes.PCallCapiFunction callSetter) {
            callSetter.call(NativeMember.CAUSE.getSetterFunctionName(), excToNative.execute(exception), valueToNative.execute(noneToNativeNull(inliningTarget, value)));
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
    @ImportStatic(PGuards.class)
    public abstract static class SetContextNode extends Node {
        public abstract void execute(Node inliningTarget, Object exception, Object value);

        public static void executeUncached(Object e, Object value) {
            ExceptionNodesFactory.SetContextNodeGen.getUncached().execute(null, e, value);
        }

        @Specialization(guards = "isNone(value)")
        static void doManaged(PBaseException exception, @SuppressWarnings("unused") PNone value) {
            exception.setContext(null);
        }

        @Specialization(guards = "!isNone(value)")
        static void doManaged(PBaseException exception, Object value) {
            exception.setContext(value);
        }

        @Specialization(guards = "check.execute(inliningTarget, exception)")
        static void doNative(Node inliningTarget, PythonAbstractNativeObject exception, Object value,
                        @SuppressWarnings("unused") @Cached PyExceptionInstanceCheckNode check,
                        @Cached CApiTransitions.PythonToNativeNode excToNative,
                        @Cached CApiTransitions.PythonToNativeNode valueToNative,
                        @Cached CExtNodes.PCallCapiFunction callSetter) {
            callSetter.call(NativeMember.CONTEXT.getSetterFunctionName(), excToNative.execute(exception), valueToNative.execute(noneToNativeNull(inliningTarget, value)));
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
        static boolean doNative(Node inliningTarget, PythonAbstractNativeObject exception,
                        @SuppressWarnings("unused") @Cached PyExceptionInstanceCheckNode check,
                        @Cached CApiTransitions.PythonToNativeNode toNative,
                        @Cached CExtNodes.PCallCapiFunction callGetter) {
            return (byte) callGetter.call(NativeMember.SUPPRESS_CONTEXT.getGetterFunctionName(), toNative.execute(noneToNativeNull(inliningTarget, exception))) != 0;
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
                        @Cached CExtNodes.PCallCapiFunction callSetter) {
            callSetter.call(NativeMember.SUPPRESS_CONTEXT.getSetterFunctionName(), excToNative.execute(exception), (byte) (value ? 1 : 0));
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
        static void doNative(Node inliningTarget, PythonAbstractNativeObject exception, Object value,
                        @SuppressWarnings("unused") @Cached PyExceptionInstanceCheckNode check,
                        @Cached CApiTransitions.PythonToNativeNode excToNative,
                        @Cached CApiTransitions.PythonToNativeNode valueToNative,
                        @Cached CExtNodes.PCallCapiFunction callSetter) {
            callSetter.call(NativeMember.TRACEBACK.getSetterFunctionName(), excToNative.execute(exception), valueToNative.execute(noneToNativeNull(inliningTarget, value)));
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
    public abstract static class GetArgsNode extends Node {
        public abstract PTuple execute(Node inliningTarget, Object exception);

        public static PTuple executeUncached(Object e) {
            return ExceptionNodesFactory.GetArgsNodeGen.getUncached().execute(null, e);
        }

        @TruffleBoundary
        private static String getFormattedMessage(TruffleString format, Object... args) {
            String jFormat = format.toJavaStringUncached();
            try {
                // pre-format for custom error message formatter
                if (ErrorMessageFormatter.containsCustomSpecifier(jFormat)) {
                    return ErrorMessageFormatter.format(jFormat, args);
                }
                return String.format(jFormat, args);
            } catch (IllegalFormatException e) {
                // According to PyUnicode_FromFormat, invalid format specifiers are just ignored.
                return jFormat;
            }
        }

        @Specialization
        static PTuple doManaged(Node inliningTarget, PBaseException self,
                        @Cached PythonObjectFactory factory,
                        @Cached InlinedConditionProfile nullArgsProfile,
                        @Cached InlinedConditionProfile hasMessageFormat,
                        @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
            PTuple args = self.getArgs();
            if (nullArgsProfile.profile(inliningTarget, args == null)) {
                if (hasMessageFormat.profile(inliningTarget, !self.hasMessageFormat())) {
                    args = factory.createEmptyTuple();
                } else {
                    // lazily format the exception message:
                    args = factory.createTuple(new Object[]{fromJavaStringNode.execute(getFormattedMessage(self.getMessageFormat(), self.getMessageArgs()), TS_ENCODING)});
                }
                self.setArgs(args);
            }
            return args;
        }

        @Specialization(guards = "check.execute(inliningTarget, exception)")
        static PTuple doNative(@SuppressWarnings("unused") Node inliningTarget, PythonAbstractNativeObject exception,
                        @SuppressWarnings("unused") @Cached PyExceptionInstanceCheckNode check,
                        @Cached CApiTransitions.PythonToNativeNode toNative,
                        @Cached CApiTransitions.NativeToPythonNode toPython,
                        @Cached CExtNodes.PCallCapiFunction callGetter) {
            return (PTuple) toPython.execute(callGetter.call(NativeMember.ARGS.getGetterFunctionName(), toNative.execute(exception)));
        }

        @Specialization
        @SuppressWarnings("unused")
        static PTuple doInterop(Node inliningTarget, AbstractTruffleException exception,
                        @Cached PythonObjectFactory factory) {
            return factory.createEmptyTuple();
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class SetArgsNode extends Node {
        public abstract void execute(Node inliningTarget, Object exception, PTuple argsTuple);

        public static void executeUncached(Object e, PTuple argsTuple) {
            ExceptionNodesFactory.SetArgsNodeGen.getUncached().execute(null, e, argsTuple);
        }

        @Specialization
        static void doManaged(PBaseException exception, PTuple argsTuple) {
            exception.setArgs(argsTuple);
        }

        @Specialization(guards = "check.execute(inliningTarget, exception)")
        static void doNative(@SuppressWarnings("unused") Node inliningTarget, PythonAbstractNativeObject exception, PTuple argsTuple,
                        @SuppressWarnings("unused") @Cached PyExceptionInstanceCheckNode check,
                        @Cached CApiTransitions.PythonToNativeNode excToNative,
                        @Cached CApiTransitions.PythonToNativeNode valueToNative,
                        @Cached CExtNodes.PCallCapiFunction callSetter) {
            callSetter.call(NativeMember.ARGS.getSetterFunctionName(), excToNative.execute(exception), valueToNative.execute(argsTuple));
        }

        @Specialization
        @SuppressWarnings("unused")
        static void doInterop(Node inliningTarget, AbstractTruffleException exception, PTuple argsTuple) {
            throw PRaiseNode.raiseUncached(inliningTarget, TypeError, ErrorMessages.CANNOT_SET_PROPERTY_ON_INTEROP_EXCEPTION);
        }
    }
}
