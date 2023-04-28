/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.ctypes;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PyCArray;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PyCArrayType;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PyCData;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PyCFuncPtr;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PyCFuncPtrType;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PyCPointer;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PyCPointerType;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PyCSimpleType;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PyCStructType;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SimpleCData;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.UnionType;
import static com.oracle.graal.python.nodes.BuiltinNames.T__CTYPES;
import static com.oracle.graal.python.nodes.truffle.TruffleStringMigrationHelpers.isJavaString;
import static com.oracle.graal.python.util.PythonUtils.EMPTY_BYTE_ARRAY;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.nio.ByteOrder;

import com.oracle.graal.python.builtins.modules.ctypes.FFIType.FFI_TYPES;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetBaseClassNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.InlinedIsSameTypeNode;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.InlinedGetClassNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.memory.ByteArraySupport;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

public class CtypesNodes {

    @GenerateUncached
    protected abstract static class PyTypeCheck extends Node {

        protected abstract boolean execute(Object receiver, Object type);

        // corresponds to UnionTypeObject_Check
        protected final boolean isUnionTypeObject(Object obj) {
            return execute(obj, UnionType);
        }

        // corresponds to CDataObject_Check
        protected final boolean isCDataObject(Object obj) {
            return obj instanceof CDataObject && execute(obj, PyCData);
        }

        // corresponds to PyCArrayTypeObject_Check
        protected final boolean isPyCArrayTypeObject(Object obj) {
            return execute(obj, PyCArrayType);
        }

        // corresponds to ArrayObject_Check
        protected final boolean isArrayObject(Object obj) {
            return execute(obj, PyCArray);
        }

        // corresponds to PyCFuncPtrObject_Check
        protected final boolean isPyCFuncPtrObject(Object obj) {
            return execute(obj, PyCFuncPtr);
        }

        // corresponds to PyCFuncPtrTypeObject_Check
        protected final boolean isPyCFuncPtrTypeObject(Object obj) {
            return execute(obj, PyCFuncPtrType);
        }

        // corresponds to PyCPointerTypeObject_Check
        protected final boolean isPyCPointerTypeObject(Object obj) {
            return execute(obj, PyCPointerType);
        }

        // corresponds to PointerObject_Check
        protected final boolean isPointerObject(Object obj) {
            return execute(obj, PyCPointer);
        }

        // corresponds to PyCSimpleTypeObject_Check
        protected final boolean isPyCSimpleTypeObject(Object obj) {
            return execute(obj, PyCSimpleType);
        }

        /*
         * This function returns TRUE for c_int, c_void_p, and these kind of classes. FALSE
         * otherwise FALSE also for subclasses of c_int and such.
         */
        // corresponds to _ctypes_simple_instance
        boolean ctypesSimpleInstance(Node inliningTarget, Object type, GetBaseClassNode getBaseClassNode, InlinedIsSameTypeNode isSameTypeNode) {
            if (isPyCSimpleTypeObject(type)) {
                return !isSameTypeNode.execute(inliningTarget, getBaseClassNode.execute(type), SimpleCData);
            }
            return false;
        }

        // corresponds to PyCStructTypeObject_Check
        protected final boolean isPyCStructTypeObject(Object obj) {
            return execute(obj, PyCStructType);
        }

        @Specialization
        static boolean checkType(Object receiver, Object type,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedGetClassNode getClassNode,
                        @Cached IsSubtypeNode isSubtypeNode) {
            Object clazz = getClassNode.execute(inliningTarget, receiver);
            // IsSameTypeNode.execute(clazz, type) is done within IsSubtypeNode
            return isSubtypeNode.execute(clazz, type);
        }
    }

    @GenerateUncached
    protected abstract static class GetBytesFromNativePointerNode extends PNodeWithContext {

        abstract byte[] execute(Object pointer, int size);

        protected CtypesModuleBuiltins getCtypesMod(PythonContext context) {
            return (CtypesModuleBuiltins) context.lookupBuiltinModule(T__CTYPES).getBuiltins();
        }

        @Specialization(guards = "size > 0")
        byte[] getBytes(Object pointer, int size,
                        @Shared("c") @Cached(value = "getContext()", allowUncached = true, neverDefault = false) PythonContext context,
                        @Cached(value = "getCtypesMod(context)", allowUncached = true, neverDefault = false) CtypesModuleBuiltins mod,
                        @Shared("r") @Cached PRaiseNode raiseNode,
                        @CachedLibrary(limit = "2") InteropLibrary lib) {
            try {
                byte[] bytes = new byte[size];
                lib.execute(mod.getMemcpyFunction(), context.getEnv().asGuestValue(bytes), pointer, size);
                return bytes;
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw raiseNode.raise(SystemError, e);
            }
        }

        @Specialization(guards = "ignored < 0")
        byte[] getStringBytes(Object pointer, @SuppressWarnings("unused") int ignored,
                        @Shared("c") @Cached(value = "getContext()", allowUncached = true, neverDefault = false) PythonContext context,
                        @Cached(value = "getCtypesMod(context)", allowUncached = true, neverDefault = false) CtypesModuleBuiltins mod,
                        @Shared("r") @Cached PRaiseNode raiseNode,
                        @CachedLibrary(limit = "2") InteropLibrary lib) {
            try {
                long size = (Long) lib.execute(mod.getStrlenFunction(), pointer);
                byte[] bytes = new byte[(int) size];
                lib.execute(mod.getMemcpyFunction(), context.getEnv().asGuestValue(bytes), pointer, size);
                return bytes;
            } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                throw raiseNode.raise(SystemError, e);
            }
        }

        @Specialization(guards = "size == 0")
        static byte[] empty(@SuppressWarnings("unused") Object pointer, @SuppressWarnings("unused") int size) {
            return EMPTY_BYTE_ARRAY;
        }

    }

    protected static final ByteArraySupport SERIALIZE_LE = ByteArraySupport.littleEndian();
    protected static final ByteArraySupport SERIALIZE_BE = ByteArraySupport.bigEndian();
    protected static final ByteArraySupport SERIALIZE_SWAP = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN ? ByteArraySupport.bigEndian() : ByteArraySupport.littleEndian();

    protected static Object getValue(FFI_TYPES type, byte[] storage, int offset) {
        switch (type) {
            case FFI_TYPE_UINT8_ARRAY:
            case FFI_TYPE_SINT8_ARRAY:
            case FFI_TYPE_UINT8:
            case FFI_TYPE_SINT8:
                return storage[offset];
            case FFI_TYPE_UINT16_ARRAY:
            case FFI_TYPE_SINT16_ARRAY:
            case FFI_TYPE_UINT16:
            case FFI_TYPE_SINT16:
                return SERIALIZE_LE.getShort(storage, offset);
            case FFI_TYPE_UINT32_ARRAY:
            case FFI_TYPE_SINT32_ARRAY:
            case FFI_TYPE_UINT32:
            case FFI_TYPE_SINT32:
                return SERIALIZE_LE.getInt(storage, offset);
            case FFI_TYPE_UINT64_ARRAY:
            case FFI_TYPE_SINT64_ARRAY:
            case FFI_TYPE_UINT64:
            case FFI_TYPE_SINT64:
            case FFI_TYPE_POINTER:
                return SERIALIZE_LE.getLong(storage, offset);
            case FFI_TYPE_FLOAT_ARRAY:
            case FFI_TYPE_FLOAT:
                return SERIALIZE_LE.getFloat(storage, offset);
            case FFI_TYPE_DOUBLE_ARRAY:
            case FFI_TYPE_DOUBLE:
                return SERIALIZE_LE.getDouble(storage, offset);
            default:
                throw CompilerDirectives.shouldNotReachHere("Incompatible value type for ByteArrayStorage");
        }
    }

    protected static void setValue(FFI_TYPES type, byte[] storage, int offset, Object value) {
        switch (type) {
            case FFI_TYPE_UINT8_ARRAY:
            case FFI_TYPE_SINT8_ARRAY:
            case FFI_TYPE_UINT8:
            case FFI_TYPE_SINT8:
                storage[offset] = (byte) value;
                break;
            case FFI_TYPE_UINT16_ARRAY:
            case FFI_TYPE_SINT16_ARRAY:
            case FFI_TYPE_UINT16:
            case FFI_TYPE_SINT16:
                SERIALIZE_LE.putShort(storage, offset, (Short) value);
                break;
            case FFI_TYPE_UINT32_ARRAY:
            case FFI_TYPE_SINT32_ARRAY:
            case FFI_TYPE_UINT32:
            case FFI_TYPE_SINT32:
                SERIALIZE_LE.putInt(storage, offset, (Integer) value);
                break;
            case FFI_TYPE_UINT64_ARRAY:
            case FFI_TYPE_SINT64_ARRAY:
            case FFI_TYPE_UINT64:
            case FFI_TYPE_SINT64:
                SERIALIZE_LE.putLong(storage, offset, (Long) value);
                break;
            case FFI_TYPE_FLOAT_ARRAY:
            case FFI_TYPE_FLOAT:
                SERIALIZE_LE.putFloat(storage, offset, (Float) value);
                break;
            case FFI_TYPE_DOUBLE_ARRAY:
            case FFI_TYPE_DOUBLE:
                SERIALIZE_LE.putDouble(storage, offset, (Double) value);
                break;
            case FFI_TYPE_STRUCT:
                setValue(storage, value, offset);
                break;
            default:
                throw CompilerDirectives.shouldNotReachHere("Incompatible value type for ByteArrayStorage");
        }
    }

    protected static void setValue(byte[] value, Object v, int idx) {
        if (v instanceof Byte) {
            value[idx] = (byte) v;
            return;
        } else if (v instanceof Short) {
            SERIALIZE_LE.putShort(value, idx, (short) v);
            return;
        } else if (v instanceof Integer) {
            SERIALIZE_LE.putInt(value, idx, (int) v);
            return;
        } else if (v instanceof Long) {
            SERIALIZE_LE.putLong(value, idx, (long) v);
            return;
        } else if (v instanceof Double) {
            SERIALIZE_LE.putDouble(value, idx, (double) v);
            return;
        } else if (v instanceof Boolean) {
            value[idx] = (byte) (((boolean) v) ? 1 : 0);
            return;
        } else if (v instanceof Float) {
            SERIALIZE_LE.putFloat(value, idx, (float) v);
            return;
        } else if (isJavaString(v)) {
            String s = (String) v;
            if (length(s) == 1) {
                value[idx] = (byte) charAt(s, 0);
                return;
            }
        } else if (v instanceof TruffleString) {
            TruffleString s = (TruffleString) v;
            if (s.codePointLengthUncached(TS_ENCODING) == 1) {
                value[idx] = (byte) s.codePointAtIndexUncached(0, TS_ENCODING);
                return;
            }
        }

        throw CompilerDirectives.shouldNotReachHere("Incompatible value type for ByteArrayStorage");
    }

    @TruffleBoundary(allowInlining = true)
    public static int length(String s) {
        return s.length();
    }

    @TruffleBoundary(allowInlining = true)
    public static char charAt(String s, int i) {
        return s.charAt(i);
    }
}
