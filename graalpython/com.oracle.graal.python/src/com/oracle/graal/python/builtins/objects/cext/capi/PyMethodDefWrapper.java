/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.nodes.SpecialAttributeNames.__DOC__;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CArrayWrappers.CStringWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.FromCharPointerNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.GetNativeNullNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtContext;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToDynamicObjectNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.interop.InteropArray;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
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
@ExportLibrary(NativeTypeLibrary.class)
public class PyMethodDefWrapper extends PythonNativeWrapper {
    public static final String ML_NAME = "ml_name";
    public static final String ML_METH = "ml_meth";
    public static final String ML_FLAGS = "ml_flags";
    public static final String ML_DOC = "ml_doc";

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
            case ML_NAME:
            case ML_METH:
            case ML_FLAGS:
            case ML_DOC:
                return true;
            default:
                return false;
        }
    }

    @ExportMessage
    protected Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        return new InteropArray(new Object[]{ML_NAME, ML_METH, ML_FLAGS, ML_DOC});
    }

    @ExportMessage
    protected Object readMember(String member,
                    @CachedLibrary("this") PythonNativeWrapperLibrary lib,
                    @Cached ReadFieldNode readFieldNode) {
        return readFieldNode.execute(lib.getDelegate(this), member);
    }

    @GenerateUncached
    @ImportStatic(PyMethodDefWrapper.class)
    abstract static class ReadFieldNode extends Node {

        public abstract Object execute(Object delegate, String key);

        protected static boolean eq(String expected, String actual) {
            return expected.equals(actual);
        }

        @Specialization(guards = {"eq(ML_NAME, key)"})
        static Object getName(PythonObject object, @SuppressWarnings("unused") String key,
                        @Cached PythonAbstractObject.PInteropGetAttributeNode getAttrNode,
                        @Shared("toSulongNode") @Cached ToSulongNode toSulongNode,
                        @Shared("castToJavaStringNode") @Cached CastToJavaStringNode castToJavaStringNode,
                        @Shared("getNativeNullNode") @Cached GetNativeNullNode getNativeNullNode) {
            Object name = getAttrNode.execute(object, SpecialAttributeNames.__NAME__);
            if (!PGuards.isPNone(name)) {
                try {
                    return new CStringWrapper(castToJavaStringNode.execute(name));
                } catch (CannotCastException e) {
                    // fall through
                }
            }
            return toSulongNode.execute(getNativeNullNode.execute());
        }

        @Specialization(guards = {"eq(ML_DOC, key)"})
        static Object getDoc(PythonObject object, @SuppressWarnings("unused") String key,
                        @Cached ReadAttributeFromObjectNode getAttrNode,
                        @Shared("toSulongNode") @Cached ToSulongNode toSulongNode,
                        @Shared("castToJavaStringNode") @Cached CastToJavaStringNode castToJavaStringNode,
                        @Shared("getNativeNullNode") @Cached GetNativeNullNode getNativeNullNode) {
            Object doc = getAttrNode.execute(object, __DOC__);
            if (!PGuards.isPNone(doc)) {
                try {
                    return new CStringWrapper(castToJavaStringNode.execute(doc));
                } catch (CannotCastException e) {
                    // fall through
                }
            }
            return toSulongNode.execute(getNativeNullNode.execute());
        }

        @Specialization(guards = {"eq(ML_METH, key)"})
        static Object getMeth(PythonObject object, @SuppressWarnings("unused") String key,
                        @Shared("toSulongNode") @Cached ToSulongNode toSulongNode) {
            return toSulongNode.execute(object);
        }

        @Specialization(guards = {"eq(ML_FLAGS, key)"})
        static Object getFlags(PythonObject object, @SuppressWarnings("unused") String key) {
            PBuiltinFunction fun = null;
            if (object instanceof PBuiltinFunction) {
                fun = (PBuiltinFunction) object;
            } else if (object instanceof PBuiltinMethod) {
                fun = ((PBuiltinMethod) object).getFunction();
            }
            if (fun != null) {
                return getFlags(fun);
            }
            return 0;
        }

        @TruffleBoundary
        private static int getFlags(PBuiltinFunction object) {
            Builtin builtin = getBuiltin(object);
            int flags = 0;
            if (builtin.isClassmethod()) {
                flags |= CExtContext.METH_CLASS;
            }
            if (builtin.isStaticmethod()) {
                flags |= CExtContext.METH_STATIC;
            }
            Signature signature = object.getSignature();
            int params = signature.getParameterIds().length;
            if (params == 1) {
                // only 'self'
                flags |= CExtContext.METH_NOARGS;
            } else if (params == 2) {
                flags |= CExtContext.METH_O;
            } else if (signature.takesKeywordArgs()) {
                flags |= CExtContext.METH_VARARGS;
            } else if (signature.takesVarArgs()) {
                flags |= CExtContext.METH_VARARGS;
            }
            return flags | CExtContext.METH_FASTCALL;
        }

        @TruffleBoundary
        private static Builtin getBuiltin(PBuiltinFunction delegate) {
            NodeFactory<? extends PythonBuiltinBaseNode> builtinNodeFactory = delegate.getBuiltinNodeFactory();
            if (builtinNodeFactory != null) {
                assert builtinNodeFactory.getNodeClass().getAnnotationsByType(Builtin.class).length > 0 : "PBuiltinFunction " + delegate + " is expected to have a Builtin annotated node.";
                return builtinNodeFactory.getNodeClass().getAnnotationsByType(Builtin.class)[0];
            }
            return null;
        }
    }

    @ExportMessage
    protected boolean isMemberModifiable(String member) {
        return ML_DOC.equals(member);
    }

    @ExportMessage
    protected boolean isMemberInsertable(@SuppressWarnings("unused") String member) {
        return false;
    }

    @ExportMessage
    protected void writeMember(String member, Object value,
                    @CachedLibrary("this") PythonNativeWrapperLibrary lib,
                    @Exclusive @Cached WriteFieldNode writeFieldNode) throws UnsupportedMessageException, UnknownIdentifierException {
        writeFieldNode.execute(lib.getDelegate(this), member, value);
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

        @Specialization(guards = {"!isBuiltinMethod(object)", "!isBuiltinFunction(object)", "eq(ML_DOC, key)"})
        static void doPythonObject(PythonObject object, @SuppressWarnings("unused") String key, Object value,
                        @Cached("key") @SuppressWarnings("unused") String cachedKey,
                        @Cached PythonAbstractObject.PInteropSetAttributeNode setAttrNode,
                        @Shared("fromCharPointerNode") @Cached FromCharPointerNode fromCharPointerNode) throws UnsupportedMessageException, UnknownIdentifierException {
            setAttrNode.execute(object, __DOC__, fromCharPointerNode.execute(value));
        }

        @Specialization(guards = {"isBuiltinMethod(object) || isBuiltinFunction(object)", "eq(ML_DOC, key)"})
        static void doBuiltinFunctionOrMethod(PythonBuiltinObject object, @SuppressWarnings("unused") String key, Object value,
                        @Cached("key") @SuppressWarnings("unused") String cachedKey,
                        @Exclusive @Cached WriteAttributeToDynamicObjectNode writeAttrToDynamicObjectNode,
                        @Shared("fromCharPointerNode") @Cached FromCharPointerNode fromCharPointerNode) {
            // Since CPython does directly write to `ml_doc`, writing the __doc__ attribute
            // circumvents any checks if the attribute may be written according to the common Python
            // rules. So, directly write to the Python object's storage.
            writeAttrToDynamicObjectNode.execute(object.getStorage(), __DOC__, fromCharPointerNode.execute(value));
        }
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    protected boolean hasNativeType() {
        // TODO implement native type
        return false;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public Object getNativeType() {
        // TODO implement native type
        return null;
    }
}
