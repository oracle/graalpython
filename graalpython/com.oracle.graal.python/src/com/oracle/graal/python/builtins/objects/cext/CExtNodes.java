/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 *     one is included with the Software (each a "Larger Work" to which the
 *     Software is contributed by such licensors),
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

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.AsCharPointerNodeGen;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.AsPythonObjectNodeGen;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.ToSulongNodeGen;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.nodes.PBaseNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
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

        @Specialization(guards = "!isNativeClass(object)")
        Object runNativeObject(PythonClass object) {
            return PythonClassNativeWrapper.wrap(object);
        }

        @Specialization(guards = {"!isPythonClass(object)", "!isNativeObject(object)", "!isNoValue(object)"})
        Object runNativeObject(PythonAbstractObject object) {
            assert object != PNone.NO_VALUE;
            return PythonObjectNativeWrapper.wrap(object);
        }

        @Fallback
        Object run(Object obj) {
            assert obj != null : "Java 'null' cannot be a Sulong value";
            return obj;
        }

        protected static boolean isNativeClass(PythonAbstractObject o) {
            return o instanceof PythonNativeClass;
        }

        protected static boolean isPythonClass(PythonAbstractObject o) {
            return o instanceof PythonClass;
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
                nativeToJavaFunction = (TruffleObject) getContext().getEnv().importSymbol(NativeCAPISymbols.FUN_NATIVE_TO_JAVA);
            }
            return toJavaNode.execute(callNativeNode.execute(nativeToJavaFunction, new Object[]{value}));
        }

        public static ToJavaNode create() {
            return new ToJavaNode();
        }

    }

    public abstract static class AsCharPointer extends PBaseNode {

        @CompilationFinal TruffleObject truffle_string_to_cstr;
        @Child private Node executeNode;

        public abstract Object execute(String s);

        TruffleObject getTruffleStringToCstr() {
            if (truffle_string_to_cstr == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                truffle_string_to_cstr = (TruffleObject) getContext().getEnv().importSymbol(NativeCAPISymbols.FUN_PY_TRUFFLE_STRING_TO_CSTR);
            }
            return truffle_string_to_cstr;
        }

        private Node getExecuteNode() {
            if (executeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                executeNode = insert(Message.createExecute(1).createNode());
            }
            return executeNode;
        }

        @Specialization
        Object doString(String str) {
            try {
                return ForeignAccess.sendExecute(getExecuteNode(), getTruffleStringToCstr(), str);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw e.raise();
            }
        }

        public static AsCharPointer create() {
            return AsCharPointerNodeGen.create();
        }
    }

}
