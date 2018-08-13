/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.AsCharPointerNodeGen;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.AsPythonObjectNodeGen;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.ToJavaNodeGen;
import com.oracle.graal.python.builtins.objects.cext.CExtNodesFactory.ToSulongNodeGen;
import com.oracle.graal.python.builtins.objects.cext.NativeWrappers.PythonClassNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.NativeWrappers.PythonNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.NativeWrappers.PythonObjectNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.NativeWrappers.TruffleObjectNativeWrapper;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.nodes.PBaseNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;

public abstract class CExtNodes {

    /**
     * For some builtin classes, the CPython approach to creating a subclass instance is to just
     * call the alloc function and then assign some fields. This needs to be done in C. This node
     * will call that subtype C function with two arguments, the C type object and an object
     * argument to fill in from.
     */
    public static class SubtypeNew extends PBaseNode {
        private final TruffleObject subtypeFunc;
        @Child private Node executeNode = Message.EXECUTE.createNode();
        @Child private ToSulongNode toSulongNode = ToSulongNode.create();
        @Child private ToJavaNode toJavaNode = ToJavaNode.create();

        /**
         * @param typenamePrefix the <code>typename</code> in <code>typename_subtype_new</code>
         */
        public SubtypeNew(String typenamePrefix) {
            subtypeFunc = (TruffleObject) getContext().getEnv().importSymbol(typenamePrefix + "_subtype_new");
            assert subtypeFunc != null;
        }

        public Object execute(PythonNativeClass object, Object arg) {
            try {
                return toJavaNode.execute(ForeignAccess.sendExecute(executeNode, subtypeFunc, toSulongNode.execute(object), arg));
            } catch (UnsupportedMessageException | UnsupportedTypeException | ArityException e) {
                throw new IllegalStateException("C subtype_new function failed", e);
            }
        }
    }

    public static class FromNativeSubclassNode<T> extends PBaseNode {
        private final PythonBuiltinClassType expectedType;
        private final String conversionFuncName;
        @CompilationFinal private PythonBuiltinClass expectedClass;
        @CompilationFinal private TruffleObject conversionFunc;
        @Child private Node executeNode;
        @Child private GetClassNode getClass = GetClassNode.create();
        @Child private IsSubtypeNode isSubtype = IsSubtypeNode.create();
        @Child private ToSulongNode toSulongNode;

        private FromNativeSubclassNode(PythonBuiltinClassType expectedType, String conversionFuncName) {
            this.expectedType = expectedType;
            this.conversionFuncName = conversionFuncName;
        }

        private PythonBuiltinClass getExpectedClass() {
            if (expectedClass == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                expectedClass = getCore().lookupType(expectedType);
            }
            return expectedClass;
        }

        private Node getExecNode() {
            if (executeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                executeNode = insert(Message.EXECUTE.createNode());
            }
            return executeNode;
        }

        private TruffleObject getConversionFunc() {
            if (conversionFunc == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                conversionFunc = (TruffleObject) getContext().getEnv().importSymbol(conversionFuncName);
            }
            return conversionFunc;
        }

        private ToSulongNode getToSulongNode() {
            if (toSulongNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toSulongNode = insert(ToSulongNode.create());
            }
            return toSulongNode;
        }

        @SuppressWarnings("unchecked")
        public T execute(PythonNativeObject object) {
            if (isSubtype(object)) {
                try {
                    return (T) ForeignAccess.sendExecute(getExecNode(), getConversionFunc(), getToSulongNode().execute(object));
                } catch (UnsupportedMessageException | UnsupportedTypeException | ArityException e) {
                    throw new IllegalStateException("C object conversion function failed", e);
                }
            }
            return null;
        }

        public boolean isSubtype(PythonNativeObject object) {
            return isSubtype.execute(getClass.execute(object), getExpectedClass());
        }

        public static <T> FromNativeSubclassNode<T> create(PythonBuiltinClassType expectedType, String conversionFuncName) {
            return new FromNativeSubclassNode<>(expectedType, conversionFuncName);
        }
    }

    @ImportStatic(PGuards.class)
    abstract static class CExtBaseNode extends PBaseNode {

        protected static boolean isNativeWrapper(Object obj) {
            return obj instanceof PythonNativeWrapper;
        }

    }

    public abstract static class ToSulongNode extends CExtBaseNode {

        public abstract Object execute(Object obj);

        /*
         * This is very sad. Only for Sulong, we cannot hand out java.lang.Strings, because then it
         * won't know what to do with them when they go native. So all places where Strings may be
         * passed from Python into C code need to wrap Strings into PStrings.
         */
        @Specialization
        Object doString(String str) {
            return PythonObjectNativeWrapper.wrap(factory().createString(str));
        }

        @Specialization
        Object doBoolean(boolean b) {
            return PythonObjectNativeWrapper.wrap(factory().createInt(b));
        }

        @Specialization
        Object doInteger(int i) {
            return PythonObjectNativeWrapper.wrap(factory().createInt(i));
        }

        @Specialization
        Object doLong(long l) {
            return PythonObjectNativeWrapper.wrap(factory().createInt(l));
        }

        @Specialization
        Object doDouble(double d) {
            return PythonObjectNativeWrapper.wrap(factory().createFloat(d));
        }

        @Specialization
        Object doNativeClass(PythonNativeClass nativeClass) {
            return nativeClass.object;
        }

        @Specialization
        Object doNativeObject(PythonNativeObject nativeObject) {
            return nativeObject.object;
        }

        @Specialization(guards = "!isNativeClass(object)")
        Object doPythonClass(PythonClass object) {
            return PythonClassNativeWrapper.wrap(object);
        }

        @Specialization(guards = {"!isPythonClass(object)", "!isNativeObject(object)", "!isNoValue(object)"})
        Object runNativeObject(PythonAbstractObject object) {
            assert object != PNone.NO_VALUE;
            return PythonObjectNativeWrapper.wrap(object);
        }

        @Specialization(guards = {"isForeignObject(object)", "!isNativeWrapper(object)"})
        Object doPythonClass(TruffleObject object) {
            return TruffleObjectNativeWrapper.wrap(object);
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
    public abstract static class AsPythonObjectNode extends CExtBaseNode {
        public abstract Object execute(Object value);

        @Child GetClassNode getClassNode;

        @Specialization
        Object doNativeWrapper(PythonNativeWrapper object) {
            return object.getDelegate();
        }

        @Specialization(guards = {"isForeignObject(object)", "!isNativeWrapper(object)"})
        PythonAbstractObject doNativeObject(TruffleObject object) {
            return factory().createNativeObjectWrapper(object);
        }

        @Specialization
        PythonAbstractObject doPythonObject(PythonAbstractObject object) {
            return object;
        }

        @Specialization
        String doString(String object) {
            return object;
        }

        @Specialization
        int doLong(int i) {
            return i;
        }

        @Specialization
        long doLong(long l) {
            return l;
        }

        @Specialization
        double doDouble(double d) {
            return d;
        }

        @Fallback
        Object run(Object obj) {
            throw raise(PythonErrorType.SystemError, "invalid object from native: %s", obj);
        }

        protected boolean isForeignObject(TruffleObject obj) {
            // TODO we could probably also just use 'PGuards.isForeignObject'
            if (getClassNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getClassNode = insert(GetClassNode.create());
            }
            return getClassNode.execute(obj) == getCore().lookupType(PythonBuiltinClassType.TruffleObject);
        }

        @TruffleBoundary
        public static Object doSlowPath(PythonCore core, Object object) {
            if (object instanceof PythonNativeWrapper) {
                return ((PythonNativeWrapper) object).getDelegate();
            } else if (GetClassNode.getItSlowPath(object) == core.lookupType(PythonBuiltinClassType.TruffleObject)) {
                throw new AssertionError("Unsupported slow path operation: converting 'to_java(" + object + ")");
            }
            return object;
        }

        public static AsPythonObjectNode create() {
            return AsPythonObjectNodeGen.create();
        }
    }

    /**
     * Does the same conversion as the native function {@code to_java}. The node tries to avoid
     * calling the native function for resolving native handles.
     */
    public abstract static class ToJavaNode extends CExtBaseNode {
        @Child private PCallNativeNode callNativeNode;
        @Child private AsPythonObjectNode toJavaNode = AsPythonObjectNode.create();

        @CompilationFinal TruffleObject nativeToJavaFunction;

        public abstract Object execute(Object value);

        @Specialization
        PythonAbstractObject doPythonObject(PythonAbstractObject value) {
            return value;
        }

        @Specialization
        Object doWrapper(PythonObjectNativeWrapper value) {
            return toJavaNode.execute(value);
        }

        @Fallback
        Object doForeign(Object value) {
            if (callNativeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callNativeNode = insert(PCallNativeNode.create());
            }
            if (callNativeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
            }
            if (nativeToJavaFunction == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                nativeToJavaFunction = (TruffleObject) getContext().getEnv().importSymbol(NativeCAPISymbols.FUN_NATIVE_TO_JAVA);
            }
            return toJavaNode.execute(callNativeNode.execute(nativeToJavaFunction, new Object[]{value}));
        }

        public static ToJavaNode create() {
            return ToJavaNodeGen.create();
        }
    }

    public abstract static class AsCharPointer extends CExtBaseNode {

        @CompilationFinal TruffleObject truffle_string_to_cstr;
        @CompilationFinal TruffleObject truffle_byte_array_to_native;

        public abstract Object execute(Object obj);

        @Specialization
        Object doPString(PString str,
                        @Cached("createExecute()") Node executeNode) {
            return doString(str.getValue(), executeNode);
        }

        @Specialization
        Object doString(String str,
                        @Cached("createExecute()") Node executeNode) {
            try {
                return ForeignAccess.sendExecute(executeNode, getTruffleStringToCstr(), str, str.length());
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw e.raise();
            }
        }

        @Specialization
        Object doByteArray(byte[] arr,
                        @Cached("createExecute()") Node executeNode) {
            try {
                return ForeignAccess.sendExecute(executeNode, getTruffleByteArrayToNative(), getContext().getEnv().asGuestValue(arr), arr.length);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw e.raise();
            }
        }

        TruffleObject getTruffleStringToCstr() {
            if (truffle_string_to_cstr == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                truffle_string_to_cstr = (TruffleObject) getContext().getEnv().importSymbol(NativeCAPISymbols.FUN_PY_TRUFFLE_STRING_TO_CSTR);
            }
            return truffle_string_to_cstr;
        }

        TruffleObject getTruffleByteArrayToNative() {
            if (truffle_byte_array_to_native == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                truffle_byte_array_to_native = (TruffleObject) getContext().getEnv().importSymbol(NativeCAPISymbols.FUN_PY_TRUFFLE_BYTE_ARRAY_TO_NATIVE);
            }
            return truffle_byte_array_to_native;
        }

        protected Node createExecute() {
            return Message.EXECUTE.createNode();
        }

        public static AsCharPointer create() {
            return AsCharPointerNodeGen.create();
        }
    }

    public static class FromCharPointerNode extends CExtBaseNode {

        @CompilationFinal TruffleObject truffle_cstr_to_string;
        @Child private Node executeNode;

        TruffleObject getTruffleStringToCstr() {
            if (truffle_cstr_to_string == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                truffle_cstr_to_string = (TruffleObject) getContext().getEnv().importSymbol(NativeCAPISymbols.FUN_PY_TRUFFLE_CSTR_TO_STRING);
            }
            return truffle_cstr_to_string;
        }

        private Node getExecuteNode() {
            if (executeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                executeNode = insert(Message.EXECUTE.createNode());
            }
            return executeNode;
        }

        public String execute(Object charPtr) {
            try {
                return (String) ForeignAccess.sendExecute(getExecuteNode(), getTruffleStringToCstr(), charPtr);
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw e.raise();
            }
        }

        public static FromCharPointerNode create() {
            return new FromCharPointerNode();
        }
    }

    public static class GetNativeClassNode extends CExtBaseNode {

        @Child PCallNativeNode callGetObTypeNode;
        @Child ToJavaNode toJavaNode;

        @CompilationFinal private TruffleObject func;

        public PythonClass execute(PythonNativeObject object) {
            // do not convert wrap 'object.object' since that is really the native pointer object
            Object[] args = new Object[]{object.object};
            return (PythonClass) getToJavaNode().execute(getCallGetObTypeNode().execute(getObTypeFunction(), args));
        }

        private ToJavaNode getToJavaNode() {
            if (toJavaNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toJavaNode = insert(ToJavaNode.create());
            }
            return toJavaNode;
        }

        private PCallNativeNode getCallGetObTypeNode() {
            if (callGetObTypeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callGetObTypeNode = insert(PCallNativeNode.create());
            }
            return callGetObTypeNode;
        }

        TruffleObject getObTypeFunction() {
            if (func == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                func = (TruffleObject) getContext().getEnv().importSymbol(NativeCAPISymbols.FUN_GET_OB_TYPE);
            }
            return func;
        }

        public static GetNativeClassNode create() {
            return new GetNativeClassNode();
        }
    }

    public static class SizeofWCharNode extends CExtBaseNode {

        @CompilationFinal long wcharSize = -1;

        public long execute() {
            if (wcharSize < 0) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                try {
                    wcharSize = (long) ForeignAccess.sendExecute(Message.EXECUTE.createNode(), getNativeFunction());
                    assert wcharSize >= 0L;
                } catch (InteropException e) {
                    throw e.raise();
                }
            }
            return wcharSize;
        }

        TruffleObject getNativeFunction() {
            CompilerAsserts.neverPartOfCompilation();
            return (TruffleObject) getContext().getEnv().importSymbol(NativeCAPISymbols.FUN_WHCAR_SIZE);
        }

        public static SizeofWCharNode create() {
            return new SizeofWCharNode();
        }
    }

    public static class IsNode extends CExtBaseNode {
        @CompilationFinal private TruffleObject isFunc = null;
        @Child Node executeNode = Message.createExecute(2).createNode();

        public boolean execute(PythonNativeObject a, PythonNativeObject b) {
            try {
                return (int) ForeignAccess.sendExecute(executeNode, getNativeFunction(), a.object, b.object) != 0;
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(NativeCAPISymbols.FUN_PTR_COMPARE + " didn't work!");
            }
        }

        TruffleObject getNativeFunction() {
            if (isFunc == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isFunc = (TruffleObject) getContext().getEnv().importSymbol(NativeCAPISymbols.FUN_PTR_COMPARE);
            }
            return isFunc;
        }

    }
}
