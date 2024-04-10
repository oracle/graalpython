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
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.UnionType;
import static com.oracle.graal.python.builtins.modules.ctypes.StgDictObject.DICTFLAG_FINAL;
import static com.oracle.graal.python.nodes.truffle.TruffleStringMigrationHelpers.isJavaString;
import static com.oracle.graal.python.util.PythonUtils.ARRAY_ACCESSOR;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.truffle.api.strings.TruffleString.Encoding.US_ASCII;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonOS;
import com.oracle.graal.python.builtins.modules.ctypes.FFIType.FFI_TYPES;
import com.oracle.graal.python.builtins.modules.ctypes.memory.Pointer;
import com.oracle.graal.python.builtins.modules.ctypes.memory.PointerNodes;
import com.oracle.graal.python.builtins.modules.ctypes.memory.PointerReference;
import com.oracle.graal.python.builtins.objects.cext.common.NativePointer;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetBaseClassNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsSameTypeNode;
import com.oracle.graal.python.lib.PyObjectTypeCheck;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.InternalByteArray;
import com.oracle.truffle.api.strings.TruffleString;

public class CtypesNodes {

    public static final int WCHAR_T_SIZE = PythonOS.getPythonOS() == PythonOS.PLATFORM_WIN32 ? 2 : 4;
    public static final TruffleString.Encoding WCHAR_T_ENCODING = WCHAR_T_SIZE == 2 ? TruffleString.Encoding.UTF_16 : TruffleString.Encoding.UTF_32;

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    protected abstract static class PyTypeCheck extends Node {

        protected abstract boolean execute(Node inliningTarget, Object receiver, Object type);

        // corresponds to UnionTypeObject_Check
        protected final boolean isUnionTypeObject(Node inliningTarget, Object obj) {
            return execute(inliningTarget, obj, UnionType);
        }

        // corresponds to CDataObject_Check
        protected final boolean isCDataObject(Node inliningTarget, Object obj) {
            return obj instanceof CDataObject && execute(inliningTarget, obj, PyCData);
        }

        // corresponds to PyCArrayTypeObject_Check
        protected final boolean isPyCArrayTypeObject(Node inliningTarget, Object obj) {
            return execute(inliningTarget, obj, PyCArrayType);
        }

        // corresponds to ArrayObject_Check
        protected final boolean isArrayObject(Node inliningTarget, Object obj) {
            return execute(inliningTarget, obj, PyCArray);
        }

        // corresponds to PyCFuncPtrObject_Check
        protected final boolean isPyCFuncPtrObject(Node inliningTarget, Object obj) {
            return execute(inliningTarget, obj, PyCFuncPtr);
        }

        // corresponds to PyCFuncPtrTypeObject_Check
        protected final boolean isPyCFuncPtrTypeObject(Node inliningTarget, Object obj) {
            return execute(inliningTarget, obj, PyCFuncPtrType);
        }

        // corresponds to PyCPointerTypeObject_Check
        protected final boolean isPyCPointerTypeObject(Node inliningTarget, Object obj) {
            return execute(inliningTarget, obj, PyCPointerType);
        }

        // corresponds to PointerObject_Check
        protected final boolean isPointerObject(Node inliningTarget, Object obj) {
            return execute(inliningTarget, obj, PyCPointer);
        }

        // corresponds to PyCSimpleTypeObject_Check
        protected final boolean isPyCSimpleTypeObject(Node inliningTarget, Object obj) {
            return execute(inliningTarget, obj, PyCSimpleType);
        }

        /*
         * This function returns TRUE for c_int, c_void_p, and these kind of classes. FALSE
         * otherwise FALSE also for subclasses of c_int and such.
         */
        // corresponds to _ctypes_simple_instance
        boolean ctypesSimpleInstance(Node inliningTarget, Object type, GetBaseClassNode getBaseClassNode, IsSameTypeNode isSameTypeNode) {
            if (isPyCSimpleTypeObject(inliningTarget, type)) {
                return !isSameTypeNode.execute(inliningTarget, getBaseClassNode.execute(inliningTarget, type), SimpleCData);
            }
            return false;
        }

        // corresponds to PyCStructTypeObject_Check
        protected final boolean isPyCStructTypeObject(Node inliningTarget, Object obj) {
            return execute(inliningTarget, obj, PyCStructType);
        }

        @Specialization
        static boolean checkType(Node inliningTarget, Object receiver, Object type,
                        @Cached GetClassNode getClassNode,
                        @Cached(inline = false) IsSubtypeNode isSubtypeNode) {
            Object clazz = getClassNode.execute(inliningTarget, receiver);
            // IsSameTypeNode.execute(clazz, type) is done within IsSubtypeNode
            return isSubtypeNode.execute(clazz, type);
        }
    }

    protected static Object getValue(FFI_TYPES type, byte[] storage, int offset) {
        return switch (type) {
            case FFI_TYPE_UINT8, FFI_TYPE_SINT8 -> storage[offset];
            case FFI_TYPE_UINT16, FFI_TYPE_SINT16 -> ARRAY_ACCESSOR.getShort(storage, offset);
            case FFI_TYPE_UINT32, FFI_TYPE_SINT32 -> ARRAY_ACCESSOR.getInt(storage, offset);
            case FFI_TYPE_UINT64, FFI_TYPE_SINT64, FFI_TYPE_POINTER -> ARRAY_ACCESSOR.getLong(storage, offset);
            case FFI_TYPE_FLOAT -> ARRAY_ACCESSOR.getFloat(storage, offset);
            case FFI_TYPE_DOUBLE -> ARRAY_ACCESSOR.getDouble(storage, offset);
            default -> throw CompilerDirectives.shouldNotReachHere("Unexpected value type for getValue");
        };
    }

    protected static void setValue(FFI_TYPES type, byte[] storage, int offset, Object value) {
        switch (type) {
            case FFI_TYPE_UINT8, FFI_TYPE_SINT8 -> storage[offset] = (byte) value;
            case FFI_TYPE_UINT16, FFI_TYPE_SINT16 -> ARRAY_ACCESSOR.putShort(storage, offset, (Short) value);
            case FFI_TYPE_UINT32, FFI_TYPE_SINT32 -> ARRAY_ACCESSOR.putInt(storage, offset, (Integer) value);
            case FFI_TYPE_UINT64, FFI_TYPE_SINT64 -> ARRAY_ACCESSOR.putLong(storage, offset, (Long) value);
            case FFI_TYPE_FLOAT -> ARRAY_ACCESSOR.putFloat(storage, offset, (Float) value);
            case FFI_TYPE_DOUBLE -> ARRAY_ACCESSOR.putDouble(storage, offset, (Double) value);
            case FFI_TYPE_STRUCT -> setValue(storage, value, offset);
            default -> throw CompilerDirectives.shouldNotReachHere("Unexpected value type for setValue");
        }
    }

    protected static void setValue(byte[] value, Object v, int idx) {
        if (v instanceof Byte) {
            value[idx] = (byte) v;
            return;
        } else if (v instanceof Short) {
            ARRAY_ACCESSOR.putShort(value, idx, (short) v);
            return;
        } else if (v instanceof Integer) {
            ARRAY_ACCESSOR.putInt(value, idx, (int) v);
            return;
        } else if (v instanceof Long) {
            ARRAY_ACCESSOR.putLong(value, idx, (long) v);
            return;
        } else if (v instanceof Double) {
            ARRAY_ACCESSOR.putDouble(value, idx, (double) v);
            return;
        } else if (v instanceof Boolean) {
            value[idx] = (byte) (((boolean) v) ? 1 : 0);
            return;
        } else if (v instanceof Float) {
            ARRAY_ACCESSOR.putFloat(value, idx, (float) v);
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

    @GenerateInline
    @GenerateCached(false)
    abstract static class HandleFromPointerNode extends Node {
        abstract Object execute(Node inliningTarget, Pointer pointer);

        final CtypesModuleBuiltins.DLHandler getDLHandler(Node inliningTarget, Pointer pointer) {
            Object handle = execute(inliningTarget, pointer);
            if (handle instanceof CtypesModuleBuiltins.DLHandler dlHandler) {
                return dlHandler;
            }
            return null;
        }

        final CtypesModuleBuiltins.NativeFunction getNativeFunction(Node inliningTarget, Pointer pointer) {
            Object handle = execute(inliningTarget, pointer);
            if (handle instanceof CtypesModuleBuiltins.NativeFunction nativeFunction) {
                return nativeFunction;
            }
            return null;
        }

        @Specialization
        static Object convert(Node inliningTarget, Pointer pointer,
                        @Cached PointerNodes.GetPointerValueAsObjectNode getPointerValueAsObjectNode) {
            Object handle = getPointerValueAsObjectNode.execute(inliningTarget, pointer);
            if (handle instanceof Long address) {
                return CtypesModuleBuiltins.getObjectAtAddress(PythonContext.get(inliningTarget), address);
            }
            return handle;
        }
    }

    @GenerateInline
    @GenerateCached(false)
    abstract static class HandleFromLongNode extends Node {
        abstract Object execute(Node inliningTarget, Object pointerObj);

        final CtypesModuleBuiltins.DLHandler getDLHandler(Node inliningTarget, Object pointerObj) {
            Object handle = execute(inliningTarget, pointerObj);
            if (handle instanceof CtypesModuleBuiltins.DLHandler dlHandler) {
                return dlHandler;
            }
            return null;
        }

        final CtypesModuleBuiltins.NativeFunction getNativeFunction(Node inliningTarget, Object pointerObj) {
            Object handle = execute(inliningTarget, pointerObj);
            if (handle instanceof CtypesModuleBuiltins.NativeFunction nativeFunction) {
                return nativeFunction;
            }
            return null;
        }

        @Specialization
        static Object convert(Node inliningTarget, Object pointerObj,
                        @Cached PointerNodes.PointerFromLongNode pointerFromLongNode,
                        @Cached HandleFromPointerNode handleFromPointerNode) {
            Pointer pointer = pointerFromLongNode.execute(inliningTarget, pointerObj);
            return handleFromPointerNode.execute(inliningTarget, pointer);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class PyCDataFromBaseObjNode extends Node {
        public abstract CDataObject execute(Node inliningTarget, Object type, CDataObject base, int index, Pointer adr);

        @Specialization
        static CDataObject PyCData_FromBaseObj(Node inliningTarget, Object type, CDataObject base, int index, Pointer adr,
                        @Cached PRaiseNode.Lazy raiseNode,
                        @Cached StgDictBuiltins.PyTypeStgDictNode pyTypeStgDictNode,
                        @Cached CreateCDataObjectNode createCDataObjectNode,
                        @Cached PyCDataMallocBufferNode mallocBufferNode,
                        @Cached PointerNodes.MemcpyNode memcpyNode) {
            StgDictObject dict = pyTypeStgDictNode.checkAbstractClass(inliningTarget, type, raiseNode);
            dict.flags |= DICTFLAG_FINAL;
            CDataObject cmem;

            if (base != null) { /* use base's buffer */
                cmem = createCDataObjectNode.execute(inliningTarget, type, adr, dict.size, false);
                cmem.b_base = base;
            } else { /* copy contents of adr */
                cmem = mallocBufferNode.execute(inliningTarget, type, dict);
                memcpyNode.execute(inliningTarget, cmem.b_ptr, adr, dict.size);
            }
            cmem.b_length = dict.length;
            cmem.b_index = index;
            return cmem;
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class CreateCDataObjectNode extends Node {
        public abstract CDataObject execute(Node inliningTarget, Object type, Pointer pointer, int size, boolean needsfree);

        @Specialization
        static CDataObject doCreate(Node inliningTarget, Object type, Pointer pointer, int size, boolean needsfree,
                        @Cached(inline = false) IsSubtypeNode isSubtypeNode,
                        @Cached(inline = false) PythonObjectFactory factory) {
            CDataObject result;
            if (isSubtypeNode.execute(type, PyCFuncPtr)) {
                result = factory.createPyCFuncPtrObject(type, pointer, size, needsfree);
            } else {
                result = factory.createCDataObject(type, pointer, size, needsfree);
            }
            if (needsfree) {
                new PointerReference(result, pointer, PythonContext.get(inliningTarget).getSharedFinalizer());
            }
            return result;
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class PyCDataMallocBufferNode extends Node {
        public abstract CDataObject execute(Node inliningTarget, Object type, StgDictObject dict);

        @Specialization
        static CDataObject doCreate(Node inliningTarget, Object type, StgDictObject dict,
                        @Cached CreateCDataObjectNode createCDataObjectNode) {
            Pointer pointer = dict.size > 0 ? Pointer.allocate(dict.ffi_type_pointer, dict.size) : Pointer.NULL;
            return createCDataObjectNode.execute(inliningTarget, type, pointer, dict.size, true);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class GenericPyCDataNewNode extends Node {
        public abstract CDataObject execute(Node inliningTarget, Object type, StgDictObject dict);

        @Specialization
        static CDataObject doCreate(Node inliningTarget, Object type, StgDictObject dict,
                        @Cached PyCDataMallocBufferNode mallocBufferNode) {
            CDataObject obj = mallocBufferNode.execute(inliningTarget, type, dict);
            obj.b_length = dict.length;
            dict.flags |= DICTFLAG_FINAL;
            return obj;
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class CDataGetBufferNode extends Node {
        public abstract void execute(Node inliningTarget, CDataObject self, Object view, int flags);

        @Specialization
        static void getBuffer(Node inliningTarget, CDataObject self, Object view, @SuppressWarnings("unused") int flags,
                        @Cached PointerNodes.GetPointerValueAsObjectNode getPointerValue,
                        @Cached GetClassNode getClassNode,
                        @Cached PyObjectTypeCheck typeCheck,
                        @Cached StgDictBuiltins.PyTypeStgDictNode stgDictNode,
                        @Cached(inline = false) TruffleString.SwitchEncodingNode switchEncodingNode,
                        @Cached(inline = false) TruffleString.GetInternalByteArrayNode getInternalByteArrayNode,
                        @Cached(inline = false) CStructAccess.AllocateNode allocateNode,
                        @Cached(inline = false) CStructAccess.WriteByteNode writeByteNode,
                        @Cached(inline = false) CStructAccess.WritePointerNode writePointerNode,
                        @Cached(inline = false) CStructAccess.WriteObjectNewRefNode writeObjectNewRefNode,
                        @Cached(inline = false) CStructAccess.WriteLongNode writeLongNode,
                        @Cached(inline = false) CStructAccess.WriteIntNode writeIntNode) {
            Object itemType = getClassNode.execute(inliningTarget, self);
            StgDictObject dict = stgDictNode.execute(inliningTarget, itemType);
            while (typeCheck.execute(inliningTarget, itemType, PythonBuiltinClassType.PyCArrayType)) {
                StgDictObject stgDict = stgDictNode.execute(inliningTarget, itemType);
                itemType = stgDict.proto;
            }
            StgDictObject itemDict = stgDictNode.execute(inliningTarget, itemType);

            NativePointer nativeNull = PythonContext.get(inliningTarget).getNativeNull();

            writePointerNode.write(view, CFields.Py_buffer__buf, getPointerValue.execute(inliningTarget, self.b_ptr));
            writeLongNode.write(view, CFields.Py_buffer__len, self.b_size);
            writeObjectNewRefNode.write(view, CFields.Py_buffer__obj, self);
            writeIntNode.write(view, CFields.Py_buffer__readonly, 0);

            Object formatPtr;
            if (dict.format != null) {
                InternalByteArray formatArray = getInternalByteArrayNode.execute(switchEncodingNode.execute(dict.format, US_ASCII), US_ASCII);
                formatPtr = allocateNode.alloc(formatArray.getLength() + 1);
                writeByteNode.writeByteArray(formatPtr, formatArray.getArray(), formatArray.getLength(), formatArray.getOffset(), 0);
            } else {
                formatPtr = allocateNode.alloc(2);
                writeByteNode.write(formatPtr, (byte) 'B');
            }
            writePointerNode.write(view, CFields.Py_buffer__format, formatPtr);
            writeIntNode.write(view, CFields.Py_buffer__ndim, dict.ndim);
            Object shapePtr = allocateNode.alloc(dict.shape.length * Long.BYTES);
            long[] shapeArray = new long[dict.shape.length];
            for (int i = 0; i < dict.shape.length; i++) {
                shapeArray[i] = dict.shape[i];
            }
            writeLongNode.writeLongArray(shapePtr, shapeArray);
            writePointerNode.write(view, CFields.Py_buffer__shape, shapePtr);
            writeLongNode.write(view, CFields.Py_buffer__itemsize, itemDict.size);
            writePointerNode.write(view, CFields.Py_buffer__strides, nativeNull);
            writePointerNode.write(view, CFields.Py_buffer__suboffsets, nativeNull);
            writePointerNode.write(view, CFields.Py_buffer__internal, nativeNull);
        }
    }

    @GenerateInline
    @GenerateCached(false)
    public abstract static class CDataReleaseBufferNode extends Node {
        public abstract void execute(Node inliningTarget, Object view);

        @Specialization
        static void getBuffer(Object view,
                        @Cached(inline = false) CStructAccess.ReadPointerNode readPointerNode,
                        @Cached(inline = false) CStructAccess.FreeNode freeNode) {
            freeNode.free(readPointerNode.read(view, CFields.Py_buffer__format));
            freeNode.free(readPointerNode.read(view, CFields.Py_buffer__shape));
        }
    }
}
