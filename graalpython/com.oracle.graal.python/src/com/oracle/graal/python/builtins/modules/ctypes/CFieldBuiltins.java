/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.objects.bytes.BytesUtils.createUTF8String;
import static com.oracle.graal.python.nodes.ErrorMessages.CANT_DELETE_ATTRIBUTE;
import static com.oracle.graal.python.nodes.ErrorMessages.HAS_NO_STGINFO;
import static com.oracle.graal.python.nodes.ErrorMessages.NOT_A_CTYPE_INSTANCE;
import static com.oracle.graal.python.nodes.ErrorMessages.UNICODE_STRING_EXPECTED_INSTEAD_OF_S_INSTANCE;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GET__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__SET__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.NotImplementedError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.CDataTypeBuiltins.PyCDataGetNode;
import com.oracle.graal.python.builtins.modules.ctypes.CDataTypeBuiltins.PyCDataSetNode;
import com.oracle.graal.python.builtins.modules.ctypes.CtypesNodes.PyTypeCheck;
import com.oracle.graal.python.builtins.modules.ctypes.FFIType.FieldDesc;
import com.oracle.graal.python.builtins.modules.ctypes.FFIType.FieldGet;
import com.oracle.graal.python.builtins.modules.ctypes.FFIType.FieldSet;
import com.oracle.graal.python.builtins.modules.ctypes.PtrValue.ByteArrayStorage;
import com.oracle.graal.python.builtins.modules.ctypes.PtrValue.MemoryViewStorage;
import com.oracle.graal.python.builtins.modules.ctypes.PtrValue.NativePointerStorage;
import com.oracle.graal.python.builtins.modules.ctypes.StgDictBuiltins.PyTypeStgDictNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.BytesUtils;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GetInternalByteArrayNode;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.str.StringNodes.CastToJavaStringCheckedNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.lib.PyFloatAsDoubleNode;
import com.oracle.graal.python.lib.PyLongAsIntNode;
import com.oracle.graal.python.lib.PyLongAsLongNode;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.nodes.PNodeWithRaise;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;

@CoreFunctions(extendClasses = PythonBuiltinClassType.CField)
public class CFieldBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return CFieldBuiltinsFactory.getFactories();
    }

    @Builtin(name = "offset", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class OffsetNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object doit(CFieldObject self) {
            return self.offset;
        }

    }

    @Builtin(name = "size", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class SizeNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object doit(CFieldObject self) {
            return self.size;
        }

    }

    @Builtin(name = __SET__, minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    @SuppressWarnings("unused")
    public abstract static class SetNode extends PythonTernaryBuiltinNode {
        @Specialization(guards = "!isNone(value)")
        protected Object doit(VirtualFrame frame, CFieldObject self, Object inst, Object value,
                        @Cached PyTypeCheck pyTypeCheck,
                        @Cached PyCDataSetNode cDataSetNode) {
            if (!pyTypeCheck.isCDataObject(inst)) {
                throw raise(TypeError, NOT_A_CTYPE_INSTANCE);
            }
            if (value == PNone.NO_VALUE) {
                throw raise(TypeError, CANT_DELETE_ATTRIBUTE);
            }
            CDataObject dst = (CDataObject) inst;
            cDataSetNode.execute(frame, dst, self.proto, self.setfunc, value,
                            self.index, self.size, dst.b_ptr.ref(self.offset), factory());
            return PNone.NONE;
        }

        @SuppressWarnings("unused")
        @Specialization
        protected Object doit(CFieldObject self, Object inst, PNone value,
                        @Cached PyCDataSetNode cDataSetNode) {
            throw raise(TypeError, CANT_DELETE_ATTRIBUTE);
        }
    }

    @Builtin(name = __GET__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class GetNode extends PythonTernaryBuiltinNode {
        @Specialization
        protected Object doit(CFieldObject self, Object inst, @SuppressWarnings("unused") Object type,
                        @Cached PyCDataGetNode pyCDataGetNode,
                        @Cached PyTypeCheck pyTypeCheck) {
            if (inst == PNone.NO_VALUE) {
                return self;
            }
            if (!pyTypeCheck.isCDataObject(inst)) {
                throw raise(TypeError, NOT_A_CTYPE_INSTANCE);
            }
            CDataObject src = (CDataObject) inst;
            return pyCDataGetNode.execute(self.proto, self.getfunc, inst, self.index, self.size, src.b_ptr.ref(self.offset), factory());
        }
    }

    @Builtin(name = __REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {

        @Specialization
        String PyCField_repr(CFieldObject self,
                        @Cached GetNameNode getNameNode) {
            int bits = self.size >> 16;
            int size = self.size & 0xFFFF;
            String name = getNameNode.execute(self.proto);
            if (bits != 0) {
                return PythonUtils.format("<Field type=%s, ofs=%d:%d, bits=%d>", name, self.offset, size, bits);
            } else {
                return PythonUtils.format("<Field type=%s, ofs=%d, size=%d>", name, self.offset, size);
            }
        }
    }

    private static final int NO_BITFIELD = 0;
    private static final int NEW_BITFIELD = 1;
    private static final int CONT_BITFIELD = 2;
    private static final int EXPAND_BITFIELD = 3;

    public static final int PFIELD_SIZE = 0;
    public static final int PBITOFS = 1;
    public static final int PSIZE = 2;
    public static final int POFFSET = 3;
    public static final int PALIGN = 4;

    /*
     * Expects the size, index and offset for the current field in props[psize] and props[poffset],
     * stores the total size so far in props[psize], the offset for the next field in
     * props[poffset], the alignment requirements for the current field in props[palign], and
     * returns a field desriptor for this field.
     * 
     * bitfields extension: bitsize != 0: this is a bit field. pbitofs points to the current bit
     * offset, this will be updated. prev_desc points to the type of the previous bitfield, if any.
     */
    @SuppressWarnings("fallthrough")
    abstract static class PyCFieldFromDesc extends PNodeWithRaise {

        abstract Object execute(Object desc, int index, int bitsize, int pack, boolean big_endian, int[] props, PythonObjectFactory factory);

        @Specialization
        Object PyCField_FromDesc(Object desc, int index, int bitsize, int pack, boolean big_endian, int[] props, PythonObjectFactory factory,
                        @Cached PyTypeCheck pyTypeCheck,
                        @Cached PyTypeStgDictNode pyTypeStgDictNode) {
            CFieldObject self = factory.createCFieldObject(PythonBuiltinClassType.CField);
            StgDictObject dict = pyTypeStgDictNode.execute(desc);
            if (dict == null) {
                throw raise(TypeError, HAS_NO_STGINFO);
            }
            int fieldtype;
            if (bitsize != 0 /* this is a bitfield request */
                            && props[PFIELD_SIZE] != 0 /* we have a bitfield open */
                            /* GCC */
                            && dict.size * 8 <= props[PFIELD_SIZE] && (props[PBITOFS] + bitsize) <= props[PFIELD_SIZE]) {
                /* continue bit field */
                fieldtype = CONT_BITFIELD;
            } else if (bitsize != 0 /* this is a bitfield request */
                            && props[PFIELD_SIZE] != 0 /* we have a bitfield open */
                            && dict.size * 8 >= props[PFIELD_SIZE] && (props[PBITOFS] + bitsize) <= dict.size * 8) {
                /* expand bit field */
                fieldtype = EXPAND_BITFIELD;
            } else if (bitsize != 0) {
                /* start new bitfield */
                fieldtype = NEW_BITFIELD;
                props[PBITOFS] = 0;
                props[PFIELD_SIZE] = dict.size * 8;
            } else {
                /* not a bit field */
                fieldtype = NO_BITFIELD;
                props[PBITOFS] = 0;
                props[PFIELD_SIZE] = 0;
            }

            int size, align;
            size = dict.size;
            FieldSet setfunc = FieldSet.nil;
            FieldGet getfunc = FieldGet.nil;

            /*
             * Field descriptors for 'c_char * n' are be scpecial cased to return a Python string
             * instead of an Array object instance...
             */
            if (pyTypeCheck.isPyCArrayTypeObject(desc)) {
                StgDictObject adict = pyTypeStgDictNode.execute(desc);
                if (adict != null && adict.proto != null) {
                    StgDictObject idict = pyTypeStgDictNode.execute(adict.proto);
                    if (idict == null) {
                        throw raise(TypeError, HAS_NO_STGINFO);
                    }
                    if (idict.getfunc == FieldDesc.c.getfunc) {
                        FieldDesc fd = FieldDesc.s;
                        getfunc = fd.getfunc;
                        setfunc = fd.setfunc;
                    }

                    if (idict.getfunc == FieldDesc.u.getfunc) { // CTYPES_UNICODE
                        FieldDesc fd = FieldDesc.U;
                        getfunc = fd.getfunc;
                        setfunc = fd.setfunc;
                    }
                }
            }

            self.setfunc = setfunc;
            self.getfunc = getfunc;
            self.index = index;

            self.proto = desc;

            switch (fieldtype) {
                case NEW_BITFIELD:
                    if (big_endian) {
                        self.size = (bitsize << 16) + props[PFIELD_SIZE] - props[PBITOFS] - bitsize;
                    } else {
                        self.size = (bitsize << 16) + props[PBITOFS];
                    }
                    props[PBITOFS] = bitsize;
                    /* fall through */
                case NO_BITFIELD:
                    if (pack != 0) {
                        align = Math.min(pack, dict.align);
                    } else {
                        align = dict.align;
                    }
                    if (align != 0 && props[POFFSET] % align != 0) {
                        int delta = align - (props[POFFSET] % align);
                        props[PSIZE] += delta;
                        props[POFFSET] += delta;
                    }

                    if (bitsize == 0) {
                        self.size = size;
                    }
                    props[PSIZE] += size;

                    self.offset = props[POFFSET];
                    props[POFFSET] += size;

                    props[PALIGN] = align;
                    break;

                case EXPAND_BITFIELD:
                    props[POFFSET] += dict.size - props[PFIELD_SIZE] / 8;
                    props[PSIZE] += dict.size - props[PFIELD_SIZE] / 8;

                    props[PFIELD_SIZE] = dict.size * 8;

                    if (big_endian) {
                        self.size = (bitsize << 16) + props[PFIELD_SIZE] - props[PBITOFS] - bitsize;
                    } else {
                        self.size = (bitsize << 16) + props[PBITOFS];
                    }

                    // is already updated for the NEXT field
                    self.offset = props[POFFSET] - size;
                    props[PBITOFS] += bitsize;
                    break;

                case CONT_BITFIELD:
                    if (big_endian) {
                        self.size = (bitsize << 16) + props[PFIELD_SIZE] - props[PBITOFS] - bitsize;
                    } else {
                        self.size = (bitsize << 16) + props[PBITOFS];
                    }

                    // is already updated for the NEXT field
                    self.offset = props[POFFSET] - size;
                    props[PBITOFS] += bitsize;
                    break;
            }

            return self;
        }
    }

    @ImportStatic(FFIType.class)
    protected abstract static class SetFuncNode extends PNodeWithRaise {

        abstract Object execute(VirtualFrame frame, FieldSet setfunc, PtrValue ptr, Object value, int size);

        @Specialization(guards = "setfunc.isType(BOOL_TYPE)")
        static Object doBool(@SuppressWarnings("unused") FieldSet setfunc, PtrValue ptr, boolean value, int size) {
            ptr.ptr.setValue(value, ptr.offset / (size != 0 ? size : 1));
            return PNone.NONE;
        }

        @Specialization(guards = "setfunc.isType(BOOL_TYPE)")
        static Object doBoolObj(VirtualFrame frame, FieldSet setfunc, PtrValue ptr, Object valueObj, int size,
                        @Cached PyObjectIsTrueNode isTrueNode) {
            boolean value = isTrueNode.execute(frame, valueObj);
            return doBool(setfunc, ptr, value, size);
        }

        @Specialization(guards = "setfunc.isType(BYTE_TYPE)")
        static Object doByte(@SuppressWarnings("unused") FieldSet setfunc, PtrValue ptr, byte value, int size) {
            ptr.ptr.setValue(value, ptr.offset / (size != 0 ? size : 1));
            return PNone.NONE;
        }

        @Specialization(guards = "setfunc.isType(BYTE_TYPE)")
        static Object doByteObj(VirtualFrame frame, FieldSet setfunc, PtrValue ptr, Object valueObj, int size,
                        @Cached PyLongAsIntNode asIntNode) {
            byte value = (byte) asIntNode.execute(frame, valueObj); // TODO
            return doByte(setfunc, ptr, value, size);
        }

        @Specialization(guards = "setfunc.isType(SHORT_TYPE)")
        static Object doShort(@SuppressWarnings("unused") FieldSet setfunc, PtrValue ptr, short value, int size) {
            ptr.ptr.setValue(value, ptr.offset / (size != 0 ? size : 1));
            return PNone.NONE;
        }

        @Specialization(guards = "setfunc.isType(SHORT_TYPE)")
        static Object doShortObj(VirtualFrame frame, FieldSet setfunc, PtrValue ptr, Object valueObj, int size,
                        @Cached PyLongAsIntNode asIntNode) {
            short value = (short) asIntNode.execute(frame, valueObj);
            return doShort(setfunc, ptr, value, size);
        }

        @Specialization(guards = "setfunc.isType(INT_TYPE)")
        static Object doInt(@SuppressWarnings("unused") FieldSet setfunc, PtrValue ptr, int value, int size) {
            ptr.ptr.setValue(value, ptr.offset / (size != 0 ? size : 1));
            return PNone.NONE;
        }

        @Specialization(guards = "setfunc.isType(INT_TYPE)")
        static Object doIntObj(VirtualFrame frame, FieldSet setfunc, PtrValue ptr, Object valueObj, int size,
                        @Cached PyLongAsIntNode asIntNode) {
            int value = asIntNode.execute(frame, valueObj);
            return doInt(setfunc, ptr, value, size);
        }

        @Specialization(guards = "setfunc.isType(LONG_TYPE)")
        static Object doLong(@SuppressWarnings("unused") FieldSet setfunc, PtrValue ptr, long value, int size) {
            ptr.ptr.setValue(value, ptr.offset / (size != 0 ? size : 1));
            return PNone.NONE;
        }

        @Specialization(guards = "setfunc.isType(LONG_TYPE)")
        static Object doLongObj(VirtualFrame frame, FieldSet setfunc, PtrValue ptr, Object valueObj, int size,
                        @Cached PyLongAsLongNode asLongNode) {
            long value = asLongNode.execute(frame, valueObj);
            return doLong(setfunc, ptr, value, size);
        }

        @Specialization(guards = "setfunc.isType(FLOAT_TYPE)")
        static Object doFloat(@SuppressWarnings("unused") FieldSet setfunc, PtrValue ptr, float value, int size) {
            ptr.ptr.setValue(value, ptr.offset / (size != 0 ? size : 1));
            return PNone.NONE;
        }

        @Specialization(guards = "setfunc.isType(FLOAT_TYPE)")
        static Object doFloatObj(VirtualFrame frame, FieldSet setfunc, PtrValue ptr, Object valueObj, int size,
                        @Cached PyFloatAsDoubleNode asFloat) {
            float value = (float) asFloat.execute(frame, valueObj);
            return doFloat(setfunc, ptr, value, size);
        }

        @Specialization(guards = "setfunc.isType(DOUBLE_TYPE)")
        static Object doDouble(@SuppressWarnings("unused") FieldSet setfunc, PtrValue ptr, double value, int size) {
            ptr.ptr.setValue(value, ptr.offset / (size != 0 ? size : 1));
            return PNone.NONE;
        }

        @Specialization(guards = "setfunc.isType(DOUBLE_TYPE)")
        static Object doDoubleObj(VirtualFrame frame, FieldSet setfunc, PtrValue ptr, Object valueObj, int size,
                        @Cached PyFloatAsDoubleNode asFloat) {
            double value = asFloat.execute(frame, valueObj);
            return doDouble(setfunc, ptr, value, size);
        }

        @Specialization(guards = "setfunc.isType(STRING_TYPE)")
        static Object doString(@SuppressWarnings("unused") FieldSet setfunc, PtrValue ptr, String value, @SuppressWarnings("unused") int size) {
            byte[] ptrBytes = ((ByteArrayStorage) ptr.ptr).value;
            byte[] strBytes = BytesUtils.utf8StringToBytes(value);
            PythonUtils.arraycopy(strBytes, 0, ptrBytes, 0, strBytes.length);
            return PNone.NONE;
        }

        @Specialization(guards = "setfunc.isType(STRING_TYPE)")
        static Object doStringObj(FieldSet setfunc, PtrValue ptr, Object valueObj, int size,
                        @Cached CastToJavaStringCheckedNode asString) {
            String value = asString.execute(valueObj, UNICODE_STRING_EXPECTED_INSTEAD_OF_S_INSTANCE, new Object[]{valueObj});
            return doString(setfunc, ptr, value, size);
        }

        @Specialization(guards = "setfunc.isType(BYTE_ARRAY_TYPE)")
        static Object doBytes(@SuppressWarnings("unused") FieldSet setfunc, PtrValue ptr, byte[] value, @SuppressWarnings("unused") int size) {
            ptr.toBytes(value);
            return PNone.NONE;
        }

        @Specialization(guards = "setfunc.isType(BYTE_ARRAY_TYPE)")
        static Object doBytesObj(FieldSet setfunc, PtrValue ptr, PBytes valueObj, int size,
                        @Cached GetInternalByteArrayNode getBytes) {
            byte[] value = getBytes.execute(valueObj.getSequenceStorage());
            return doBytes(setfunc, ptr, value, size);
        }

        @Specialization(guards = "setfunc.isType(OBJECT_TYPE) || setfunc.isType(POINTER_TYPE)")
        static Object doObject(@SuppressWarnings("unused") FieldSet setfunc, PtrValue ptr, Object value, @SuppressWarnings("unused") int size) {
            ptr.toNativePointer(value);
            return PNone.NONE;
        }

        @SuppressWarnings("unused")
        @Fallback
        Object error(VirtualFrame frame, FieldSet setfunc, PtrValue ptr, Object value, int size) {
            throw raise(NotImplementedError);
        }

    }

    @ImportStatic(FFIType.class)
    protected abstract static class GetFuncNode extends PNodeWithRaise {

        abstract Object execute(FieldGet setfunc, PtrValue adr, int size, PythonObjectFactory factory);

        @Specialization(guards = "getfunc.isType(BYTE_TYPE)")
        static Object getValue(@SuppressWarnings("unused") FieldGet getfunc, PtrValue ptr, int size, PythonObjectFactory factory) {
            return factory.createBytes(new byte[]{(byte) ptr.ptr.getValue(ptr.offset / (size != 0 ? size : 1))});
        }

        @Specialization(guards = "getfunc.isType(POINTER_TYPE)")
        static Object getPointerValue(@SuppressWarnings("unused") FieldGet getfunc, PtrValue ptr, @SuppressWarnings("unused") int size, PythonObjectFactory factory,
                        @CachedLibrary(limit = "1") InteropLibrary ilib) {
            Object p;
            if (ptr.ptr instanceof MemoryViewStorage) {
                PMemoryView mv = ((MemoryViewStorage) ptr.ptr).value;
                p = mv.getBufferPointer();
                p = p != null ? p : mv.getBuffer();
            } else if (ptr.isNativePointer()) {
                p = ((NativePointerStorage) ptr.ptr).value;
                p = p != null ? p : PNone.NONE;
            } else {
                p = PNone.NONE;
            }
            if (ilib.isPointer(p)) {
                try {
                    return factory.createNativeVoidPtr((TruffleObject) p, ilib.asPointer(p));
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
            } else {
                return factory.createNativeVoidPtr(p);
            }
        }

        @Specialization(guards = {"getfunc.isType(STRING_TYPE)"})
        static Object getString(@SuppressWarnings("unused") FieldGet getfunc, PtrValue ptr, @SuppressWarnings("unused") int size, @SuppressWarnings("unused") PythonObjectFactory factory) {
            byte[] bytes = ((ByteArrayStorage) ptr.ptr).value;
            for (int i = 0; i < bytes.length; i++) {
                if (bytes[i] == 0) {
                    bytes = PythonUtils.arrayCopyOf(bytes, i);
                    break;
                }
            }
            return createUTF8String(bytes);
        }

        @Fallback
        Object asIs(@SuppressWarnings("unused") FieldGet getfunc, PtrValue ptr, int size, @SuppressWarnings("unused") PythonObjectFactory factory) {
            return ptr.ptr.getValue(ptr.offset / (size != 0 ? size : 1));
        }
    }
}
