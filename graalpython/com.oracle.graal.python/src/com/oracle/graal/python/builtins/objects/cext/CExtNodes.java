package com.oracle.graal.python.builtins.objects.cext;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.AsPythonObjectNodeGen;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.ToSulongNodeGen;
import com.oracle.graal.python.nodes.PBaseNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.profiles.ConditionProfile;

public abstract class CExtNodes {

    @ImportStatic(PGuards.class)
    public abstract static class ToSulongNode extends PBaseNode {

        public abstract Object execute(Object obj);

        /*
         * This is very sad. Only for Sulong, we cannot hand out java.lang.Strings, because then it
         * won't know what to do with them when they go native. So all places where Strings may be
         * passed from Python into C code need to wrap Strings into PStrings.
         */
        @Specialization
        Object run(String str) {
            return PythonObjectNativeWrapper.wrap(factory().createString(str));
        }

        @Specialization
        Object run(boolean b) {
            return PythonObjectNativeWrapper.wrap(factory().createInt(b));
        }

        @Specialization
        Object run(int integer) {
            return PythonObjectNativeWrapper.wrap(factory().createInt(integer));
        }

        @Specialization
        Object run(long integer) {
            return PythonObjectNativeWrapper.wrap(factory().createInt(integer));
        }

        @Specialization
        Object run(double number) {
            return PythonObjectNativeWrapper.wrap(factory().createFloat(number));
        }

        @Specialization
        Object runNativeClass(PythonNativeClass object) {
            return object.object;
        }

        @Specialization
        Object runNativeObject(PythonNativeObject object) {
            return object.object;
        }

        @Specialization(guards = {"!isNativeClass(object)", "!isNativeObject(object)", "!isNoValue(object)"})
        Object runNativeObject(PythonAbstractObject object) {
            assert object != PNone.NO_VALUE;
            return PythonObjectNativeWrapper.wrap(object);
        }

        @Fallback
        Object run(Object obj) {
            return obj;
        }

        protected static boolean isNativeClass(PythonAbstractObject o) {
            return o instanceof PythonNativeClass;
        }

        protected static boolean isNativeObject(PythonAbstractObject o) {
            return o instanceof PythonNativeObject;
        }

        public static ToSulongNode create() {
            return ToSulongNodeGen.create();
        }
    }

    /**
     * Unwraps objects contained in {@link PythonObjectNativeWrapper} instances or wraps objects
     * allocated in native code for consumption in Java.
     */
    @ImportStatic(PGuards.class)
    public abstract static class AsPythonObjectNode extends PBaseNode {
        public abstract Object execute(Object value);

        @Child GetClassNode getClassNode = GetClassNode.create();
        ConditionProfile branchCond = ConditionProfile.createBinaryProfile();

        @Specialization
        Object run(PythonObjectNativeWrapper object) {
            return object.getPythonObject();
        }

        @Specialization
        Object run(PythonAbstractObject object) {
            return object;
        }

        @Fallback
        Object run(Object obj) {
            if (branchCond.profile(getClassNode.execute(obj) == getCore().getForeignClass())) {
                // TODO: this should very likely only be done for objects that come from Sulong...
                // TODO: prevent calling this from any other place
                return factory().createNativeObjectWrapper(obj);
            } else {
                return obj;
            }
        }

        @TruffleBoundary
        public static Object doSlowPath(Object object) {
            if (object instanceof PythonObjectNativeWrapper) {
                return ((PythonObjectNativeWrapper) object).getPythonObject();
            } else if (GetClassNode.getItSlowPath(object) == PythonLanguage.getCore().getForeignClass()) {
                throw new AssertionError("Unsupported slow path operation: converting 'to_java(" + object + ")");
            }
            return object;
        }

        public static AsPythonObjectNode create() {
            return AsPythonObjectNodeGen.create();
        }
    }

    /**
     * Does the same conversion as the native function {@code to_java}.
     */
    static class ToJavaNode extends PBaseNode {
        @Child private PCallNativeNode callNativeNode = PCallNativeNode.create(1);
        @Child private AsPythonObjectNode toJavaNode = AsPythonObjectNode.create();

        @CompilationFinal TruffleObject nativeToJavaFunction;

        Object execute(Object value) {
            if (nativeToJavaFunction == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                nativeToJavaFunction = (TruffleObject) getContext().getEnv().importSymbol(NativeCAPISymbols.FUNCTION_NATIVE_TO_JAVA);
            }
            return toJavaNode.execute(callNativeNode.execute(nativeToJavaFunction, new Object[]{value}));
        }

        public static ToJavaNode create() {
            return new ToJavaNode();
        }

    }

}
