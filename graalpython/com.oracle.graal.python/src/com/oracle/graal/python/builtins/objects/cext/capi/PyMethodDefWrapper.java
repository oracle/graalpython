/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DOC__;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext.LLVMType;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.FromCharPointerNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.GetLLVMType;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.capi.DynamicObjectNativeWrapper.ToNativeOtherNode;
import com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers.CStringWrapper;
import com.oracle.graal.python.builtins.objects.cext.common.CExtContext;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToDynamicObjectNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.interop.InteropArray;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.HiddenKey;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;

/**
 * Wrapper object that emulate the ABI for {@code PyMethodDef}.
 *
 * <pre>
 *     struct PyMethodDef {
 *         const char  *ml_name;   // The name of the built-in function/method
 *         PyCFunction ml_meth;    // The C function that implements it
 *         int ml_flags;           // Combination of METH_xxx flags, which mostly describe the args expected by the C
 *         const char*ml_doc;      // The __doc__ attribute, or NULL
 *     };
 * </pre>
 */
@ExportLibrary(InteropLibrary.class)
@ExportLibrary(value = NativeTypeLibrary.class, useForAOT = false)
public class PyMethodDefWrapper extends PythonNativeWrapper {
    public static final String J_ML_NAME = "ml_name";
    public static final String J_ML_METH = "ml_meth";
    public static final String J_ML_FLAGS = "ml_flags";
    public static final String J_ML_DOC = "ml_doc";

    /**
     * Extensions that write to {@code __doc__} expect to be able to deallocate the string, so we
     * need to keep the C pointer around in a separate hidden field.
     */
    public static final HiddenKey __C_DOC__ = new HiddenKey("__c_doc__");

    public PyMethodDefWrapper(PythonObject delegate) {
        super(delegate);
    }

    @ExportMessage
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    boolean isMemberReadable(String member) {
        switch (member) {
            case J_ML_NAME:
            case J_ML_METH:
            case J_ML_FLAGS:
            case J_ML_DOC:
                return true;
            default:
                return false;
        }
    }

    @ExportMessage
    protected Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        return new InteropArray(new Object[]{J_ML_NAME, J_ML_METH, J_ML_FLAGS, J_ML_DOC});
    }

    @ExportMessage
    protected Object readMember(String member,
                    @Cached ReadFieldNode readFieldNode,
                    @Exclusive @Cached GilNode gil) {
        boolean mustRelease = gil.acquire();
        try {
            return readFieldNode.execute(getDelegate(), member);
        } finally {
            gil.release(mustRelease);
        }
    }

    @GenerateUncached
    @ImportStatic(PyMethodDefWrapper.class)
    abstract static class ReadFieldNode extends Node {

        public abstract Object execute(Object delegate, String key);

        protected static boolean eq(String expected, String actual) {
            return expected.equals(actual);
        }

        @Specialization(guards = {"eq(J_ML_NAME, key)"})
        static Object getName(PythonObject object, @SuppressWarnings("unused") String key,
                        @Cached PythonAbstractObject.PInteropGetAttributeNode getAttrNode,
                        @Shared("toSulongNode") @Cached ToSulongNode toSulongNode,
                        @Shared("castToStringNode") @Cached CastToTruffleStringNode castToStringNode) {
            Object name = getAttrNode.execute(object, SpecialAttributeNames.T___NAME__);
            if (!PGuards.isPNone(name)) {
                try {
                    return new CStringWrapper(castToStringNode.execute(name));
                } catch (CannotCastException e) {
                    // fall through
                }
            }
            return toSulongNode.execute(PythonContext.get(toSulongNode).getNativeNull());
        }

        @Specialization(guards = {"eq(J_ML_DOC, key)"})
        static Object getDoc(PythonObject object, @SuppressWarnings("unused") String key,
                        @Cached ReadAttributeFromObjectNode getAttrNode,
                        @Shared("toSulongNode") @Cached ToSulongNode toSulongNode,
                        @Shared("castToStringNode") @Cached CastToTruffleStringNode castToStringNode) {
            Object doc = getAttrNode.execute(object, __C_DOC__);
            if (doc != PNone.NO_VALUE) {
                return doc;
            }
            doc = getAttrNode.execute(object, T___DOC__);
            if (!PGuards.isPNone(doc)) {
                try {
                    return new CStringWrapper(castToStringNode.execute(doc));
                } catch (CannotCastException e) {
                    // fall through
                }
            }
            return toSulongNode.execute(PythonContext.get(toSulongNode).getNativeNull());
        }

        @Specialization(guards = {"eq(J_ML_METH, key)"})
        static Object getMethFromBuiltinMethod(PBuiltinMethod object, String key,
                        @Shared("toSulongNode") @Cached ToSulongNode toSulongNode) {
            return getMethFromBuiltinFunction(object.getFunction(), key, toSulongNode);
        }

        @Specialization(guards = {"eq(J_ML_METH, key)"})
        static Object getMethFromBuiltinFunction(PBuiltinFunction object, @SuppressWarnings("unused") String key,
                        @Shared("toSulongNode") @Cached ToSulongNode toSulongNode) {
            PKeyword[] kwDefaults = object.getKwDefaults();
            for (int i = 0; i < kwDefaults.length; i++) {
                if (ExternalFunctionNodes.KW_CALLABLE.equals(kwDefaults[i].getName())) {
                    return kwDefaults[i].getValue();
                }
            }
            return createFunctionWrapper(object, toSulongNode);
        }

        @Specialization(guards = {"eq(J_ML_METH, key)"}, replaces = {"getMethFromBuiltinMethod", "getMethFromBuiltinFunction"})
        static Object getMeth(PythonObject object, @SuppressWarnings("unused") String key,
                        @Shared("toSulongNode") @Cached ToSulongNode toSulongNode) {
            if (object instanceof PBuiltinMethod) {
                return getMethFromBuiltinMethod((PBuiltinMethod) object, key, toSulongNode);
            } else if (object instanceof PBuiltinFunction) {
                return getMethFromBuiltinFunction((PBuiltinFunction) object, key, toSulongNode);
            }
            return createFunctionWrapper(object, toSulongNode);
        }

        @TruffleBoundary
        private static Object createFunctionWrapper(PythonObject object, ToSulongNode toSulongNode) {
            int flags = getFlags(object, null);
            PythonNativeWrapper wrapper;
            if (CExtContext.isMethNoArgs(flags)) {
                wrapper = PyProcsWrapper.createUnaryFuncWrapper(object);
            } else if (CExtContext.isMethO(flags)) {
                wrapper = PyProcsWrapper.createBinaryFuncWrapper(object);
            } else if (CExtContext.isMethVarargsWithKeywords(flags)) {
                wrapper = PyProcsWrapper.createVarargKeywordWrapper(object);
            } else if (CExtContext.isMethVarargs(flags)) {
                wrapper = PyProcsWrapper.createVarargWrapper(object);
            } else {
                throw CompilerDirectives.shouldNotReachHere("other signature " + Integer.toHexString(flags));
            }
            return toSulongNode.execute(wrapper);
        }

        @Specialization(guards = {"eq(J_ML_FLAGS, key)"})
        static int getFlags(PythonObject object, @SuppressWarnings("unused") String key) {
            if (object instanceof PBuiltinFunction) {
                return ((PBuiltinFunction) object).getFlags();
            } else if (object instanceof PBuiltinMethod) {
                return ((PBuiltinMethod) object).getFunction().getFlags();
            }
            return 0;
        }
    }

    @ExportMessage
    protected boolean isMemberModifiable(String member) {
        return J_ML_DOC.equals(member);
    }

    @ExportMessage
    protected boolean isMemberInsertable(@SuppressWarnings("unused") String member) {
        return false;
    }

    @ExportMessage
    protected void writeMember(String member, Object value,
                    @Exclusive @Cached WriteFieldNode writeFieldNode,
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException, UnknownIdentifierException {
        boolean mustRelease = gil.acquire();
        try {
            writeFieldNode.execute(getDelegate(), member, value);
        } finally {
            gil.release(mustRelease);
        }
    }

    @ExportMessage
    protected boolean isMemberRemovable(@SuppressWarnings("unused") String member) {
        return false;
    }

    @ExportMessage
    protected void removeMember(@SuppressWarnings("unused") String member) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    @GenerateUncached
    @ImportStatic({SpecialMethodNames.class, PGuards.class, PyMethodDefWrapper.class})
    abstract static class WriteFieldNode extends Node {

        public abstract void execute(Object delegate, String key, Object value) throws UnsupportedMessageException, UnknownIdentifierException;

        protected static boolean eq(String expected, String actual) {
            return expected.equals(actual);
        }

        @Specialization(guards = {"!isBuiltinMethod(object)", "!isBuiltinFunction(object)", "eq(J_ML_DOC, key)"})
        static void doPythonObject(PythonObject object, @SuppressWarnings("unused") String key, Object value,
                        @Cached("key") @SuppressWarnings("unused") String cachedKey,
                        @Cached PythonAbstractObject.PInteropSetAttributeNode setAttrNode,
                        @Shared("writeAttrDynamic") @Cached WriteAttributeToDynamicObjectNode writeAttrToDynamicObjectNode,
                        @Shared("fromCharPointerNode") @Cached FromCharPointerNode fromCharPointerNode) throws UnsupportedMessageException, UnknownIdentifierException {
            setAttrNode.execute(object, T___DOC__, fromCharPointerNode.execute(value));
            writeAttrToDynamicObjectNode.execute(object, __C_DOC__, value);
        }

        @Specialization(guards = {"isBuiltinMethod(object) || isBuiltinFunction(object)", "eq(J_ML_DOC, key)"})
        static void doBuiltinFunctionOrMethod(PythonBuiltinObject object, @SuppressWarnings("unused") String key, Object value,
                        @Cached("key") @SuppressWarnings("unused") String cachedKey,
                        @Shared("writeAttrDynamic") @Cached WriteAttributeToDynamicObjectNode writeAttrToDynamicObjectNode,
                        @Shared("fromCharPointerNode") @Cached FromCharPointerNode fromCharPointerNode) {
            // Since CPython does directly write to `ml_doc`, writing the __doc__ attribute
            // circumvents any checks if the attribute may be written according to the common Python
            // rules. So, directly write to the Python object's storage.
            writeAttrToDynamicObjectNode.execute(object, T___DOC__, fromCharPointerNode.execute(value));
            writeAttrToDynamicObjectNode.execute(object, __C_DOC__, value);
        }
    }

    @ExportMessage
    boolean isPointer() {
        return isNative();
    }

    @ExportMessage
    long asPointer() {
        return getNativePointer();
    }

    @ExportMessage
    protected void toNative(
                    @Cached ToNativeOtherNode toNativeNode) {
        toNativeNode.execute(this, NativeCAPISymbol.FUN_PYTRUFFLE_ALLOCATE_METHOD_DEF);
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasNativeType() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    Object getNativeType(
                    @Cached GetLLVMType getLLVMType) {
        return getLLVMType.execute(LLVMType.PyMethodDef);
    }
}
