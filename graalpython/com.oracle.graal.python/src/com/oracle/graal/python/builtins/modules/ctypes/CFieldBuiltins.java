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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.builtins.modules.ctypes.CtypesNodes.SERIALIZE_LE;
import static com.oracle.graal.python.nodes.ErrorMessages.CANT_DELETE_ATTRIBUTE;
import static com.oracle.graal.python.nodes.ErrorMessages.HAS_NO_STGINFO;
import static com.oracle.graal.python.nodes.ErrorMessages.NOT_A_CTYPE_INSTANCE;
import static com.oracle.graal.python.nodes.ErrorMessages.NOT_IMPLEMENTED;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GET__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SET__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.NotImplementedError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.CDataTypeBuiltins.PyCDataGetNode;
import com.oracle.graal.python.builtins.modules.ctypes.CDataTypeBuiltins.PyCDataSetNode;
import com.oracle.graal.python.builtins.modules.ctypes.CtypesNodes.GetBytesFromNativePointerNode;
import com.oracle.graal.python.builtins.modules.ctypes.CtypesNodes.PyTypeCheck;
import com.oracle.graal.python.builtins.modules.ctypes.FFIType.FFI_TYPES;
import com.oracle.graal.python.builtins.modules.ctypes.FFIType.FieldDesc;
import com.oracle.graal.python.builtins.modules.ctypes.FFIType.FieldGet;
import com.oracle.graal.python.builtins.modules.ctypes.FFIType.FieldSet;
import com.oracle.graal.python.builtins.modules.ctypes.PtrValue.ByteArrayStorage;
import com.oracle.graal.python.builtins.modules.ctypes.PtrValue.MemoryViewStorage;
import com.oracle.graal.python.builtins.modules.ctypes.PtrValue.NativePointerStorage;
import com.oracle.graal.python.builtins.modules.ctypes.PtrValue.PrimitiveStorage;
import com.oracle.graal.python.builtins.modules.ctypes.StgDictBuiltins.PyTypeStgDictNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes.ToBytesWithoutFrameNode;
import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeVoidPtr;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GetInternalByteArrayNode;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.str.StringUtils.SimpleTruffleStringFormatNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.lib.PyFloatAsDoubleNode;
import com.oracle.graal.python.lib.PyFloatCheckExactNode;
import com.oracle.graal.python.lib.PyLongAsLongNode;
import com.oracle.graal.python.lib.PyLongCheckNode;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithRaise;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

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

    @Builtin(name = J___SET__, minNumOfPositionalArgs = 3)
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
            cDataSetNode.execute(frame, dst, self.proto, self.setfunc, value, self.index, self.size, dst.b_ptr.ref(self.offset));
            return PNone.NONE;
        }

        @SuppressWarnings("unused")
        @Specialization
        protected Object doit(CFieldObject self, Object inst, PNone value,
                        @Cached PyCDataSetNode cDataSetNode) {
            throw raise(TypeError, CANT_DELETE_ATTRIBUTE);
        }
    }

    @Builtin(name = J___GET__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 3)
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
            return pyCDataGetNode.execute(self.proto, self.getfunc, inst, self.index, self.size, src.b_ptr.ref(self.offset));
        }
    }

    @Builtin(name = J___REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {

        @Specialization
        TruffleString PyCField_repr(CFieldObject self,
                        @Cached GetNameNode getNameNode,
                        @Cached SimpleTruffleStringFormatNode simpleTruffleStringFormatNode) {
            int bits = self.size >> 16;
            int size = self.size & 0xFFFF;
            TruffleString name = getNameNode.execute(self.proto);
            if (bits != 0) {
                return simpleTruffleStringFormatNode.format("<Field type=%s, ofs=%d:%d, bits=%d>", name, self.offset, size, bits);
            } else {
                return simpleTruffleStringFormatNode.format("<Field type=%s, ofs=%d, size=%d>", name, self.offset, size);
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

        abstract CFieldObject execute(Object desc, int index, int bitsize, int pack, boolean big_endian, int[] props, PythonObjectFactory factory);

        @Specialization
        CFieldObject PyCField_FromDesc(Object desc, int index, int bitsize, int pack, boolean big_endian, int[] props, PythonObjectFactory factory,
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
             * Field descriptors for 'c_char * n' are a special case that returns a Python string
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

    /******************************************************************/
    /*
     * Accessor functions
     */

    /*
     * Derived from Modules/structmodule.c: Helper routine to get a Python integer and raise the
     * appropriate error if it isn't one
     */

    static long get_long(VirtualFrame frame, Object v,
                    PyFloatCheckExactNode floatCheck,
                    PyLongAsLongNode asLongNode,
                    PRaiseNode raiseNode) {
        if (floatCheck.execute(v)) {
            throw raiseNode.raise(TypeError, ErrorMessages.INT_EXPECTED_INSTEAD_FLOAT);
        }
        return asLongNode.execute(frame, v); // PyLong_AsUnsignedLongMask(v);
    }

    /* byte swapping macros */
    static short SWAP_2(short v) {
        return (short) (((v >> 8) & 0x00FF) |
                        ((v << 8) & 0xFF00));
    }

    static int SWAP_4(int v) {
        return (((v & 0x000000FF) << 24) |
                        ((v & 0x0000FF00) << 8) |
                        ((v & 0x00FF0000) >> 8) |
                        (((v >> 24) & 0xFF)));
    }

    static long SWAP_8(long v) {
        return (((v & 0x00000000000000FFL) << 56) |
                        ((v & 0x000000000000FF00L) << 40) |
                        ((v & 0x0000000000FF0000L) << 24) |
                        ((v & 0x00000000FF000000L) << 8) |
                        ((v & 0x000000FF00000000L) >> 8) |
                        ((v & 0x0000FF0000000000L) >> 24) |
                        ((v & 0x00FF000000000000L) >> 40) |
                        (((v >> 56) & 0xFF)));
    }

    @ImportStatic({FFIType.class, FieldSet.class})
    @GenerateUncached
    protected abstract static class SetFuncNode extends Node {

        abstract Object execute(VirtualFrame frame, FieldSet setfunc, PtrValue ptr, Object value, int size);

        @Specialization(guards = "setfunc == b_set || setfunc == B_set")
        Object b_set(VirtualFrame frame, FieldSet setfunc, PtrValue ptr, Object value, @SuppressWarnings("unused") int size,
                        @Shared("fc") @Cached PyFloatCheckExactNode floatCheck,
                        @Shared("l") @Cached PyLongAsLongNode asLongNode,
                        @Shared("raise") @Cached PRaiseNode raiseNode) {
            byte val = (byte) get_long(frame, value, floatCheck, asLongNode, raiseNode);
            ptr.writePrimitive(setfunc.ffiType, val);
            return PNone.NONE;
        }

        @Specialization(guards = "setfunc == h_set || setfunc == H_set")
        Object h_set(VirtualFrame frame, FieldSet setfunc, PtrValue ptr, Object value, @SuppressWarnings("unused") int size,
                        @Shared("fc") @Cached PyFloatCheckExactNode floatCheck,
                        @Shared("l") @Cached PyLongAsLongNode asLongNode,
                        @Shared("raise") @Cached PRaiseNode raiseNode) {
            short val = (short) get_long(frame, value, floatCheck, asLongNode, raiseNode);
            ptr.writePrimitive(setfunc.ffiType, val);
            return PNone.NONE;
        }

        @Specialization(guards = "setfunc == h_set_sw || setfunc == H_set_sw")
        Object h_set_sw(VirtualFrame frame, FieldSet setfunc, PtrValue ptr, Object value, @SuppressWarnings("unused") int size,
                        @Shared("fc") @Cached PyFloatCheckExactNode floatCheck,
                        @Shared("l") @Cached PyLongAsLongNode asLongNode,
                        @Shared("raise") @Cached PRaiseNode raiseNode) {
            short val = (short) get_long(frame, value, floatCheck, asLongNode, raiseNode);
            val = SWAP_2(val);
            ptr.writePrimitive(setfunc.ffiType, val);
            return PNone.NONE;
        }

        @Specialization(guards = "setfunc == i_set || setfunc == I_set")
        Object i_set(VirtualFrame frame, FieldSet setfunc, PtrValue ptr, Object value, @SuppressWarnings("unused") int size,
                        @Shared("fc") @Cached PyFloatCheckExactNode floatCheck,
                        @Shared("l") @Cached PyLongAsLongNode asLongNode,
                        @Shared("raise") @Cached PRaiseNode raiseNode) {
            int val = (int) get_long(frame, value, floatCheck, asLongNode, raiseNode);
            ptr.writePrimitive(setfunc.ffiType, val);
            return PNone.NONE;
        }

        @Specialization(guards = "setfunc == i_set_sw || setfunc == I_set_sw")
        Object i_set_sw(VirtualFrame frame, FieldSet setfunc, PtrValue ptr, Object value, @SuppressWarnings("unused") int size,
                        @Shared("fc") @Cached PyFloatCheckExactNode floatCheck,
                        @Shared("l") @Cached PyLongAsLongNode asLongNode,
                        @Shared("raise") @Cached PRaiseNode raiseNode) {
            int val = (int) get_long(frame, value, floatCheck, asLongNode, raiseNode);
            val = SWAP_4(val);
            ptr.writePrimitive(setfunc.ffiType, val);
            return PNone.NONE;
        }

        /* http://msdn.microsoft.com/en-us/library/cc237864.aspx */
        private static final short VARIANT_FALSE = 0x0000;
        private static final short VARIANT_TRUE = (short) 0xFFFF;

        /* short BOOL - VARIANT_BOOL */
        @Specialization(guards = "setfunc == vBOOL_set")
        static Object vBOOL_set(VirtualFrame frame, FieldSet setfunc, PtrValue ptr, Object value, @SuppressWarnings("unused") int size,
                        @Cached PyObjectIsTrueNode isTrueNode) {
            if (!isTrueNode.execute(frame, value)) {
                ptr.writePrimitive(setfunc.ffiType, VARIANT_FALSE);
            } else {
                ptr.writePrimitive(setfunc.ffiType, VARIANT_TRUE);
            }
            return PNone.NONE;
        }

        @Specialization(guards = "setfunc == bool_set")
        static Object bool_set(VirtualFrame frame, FieldSet setfunc, PtrValue ptr, Object value, @SuppressWarnings("unused") int size,
                        @Cached PyObjectIsTrueNode isTrueNode) {
            byte f = (byte) (isTrueNode.execute(frame, value) ? 1 : 0);
            ptr.writePrimitive(setfunc.ffiType, f);
            return PNone.NONE;
        }

        @Specialization(guards = "setfunc == l_set || setfunc == L_set")
        Object l_set(VirtualFrame frame, FieldSet setfunc, PtrValue ptr, Object value, @SuppressWarnings("unused") int size,
                        @Shared("fc") @Cached PyFloatCheckExactNode floatCheck,
                        @Shared("l") @Cached PyLongAsLongNode asLongNode,
                        @Shared("raise") @Cached PRaiseNode raiseNode) {
            long val = get_long(frame, value, floatCheck, asLongNode, raiseNode);
            ptr.writePrimitive(setfunc.ffiType, val);
            return PNone.NONE;
        }

        @Specialization(guards = "setfunc == l_set_sw || setfunc == L_set_sw")
        Object l_set_sw(VirtualFrame frame, FieldSet setfunc, PtrValue ptr, Object value, @SuppressWarnings("unused") int size,
                        @Shared("fc") @Cached PyFloatCheckExactNode floatCheck,
                        @Shared("l") @Cached PyLongAsLongNode asLongNode,
                        @Shared("raise") @Cached PRaiseNode raiseNode) {
            long val = get_long(frame, value, floatCheck, asLongNode, raiseNode);
            val = SWAP_8(val);
            ptr.writePrimitive(setfunc.ffiType, val);
            return PNone.NONE;
        }

        /*****************************************************************
         * non-integer accessor methods, not supporting bit fields
         */

        @Specialization(guards = "setfunc == d_set || setfunc == g_set")
        static Object d_set(VirtualFrame frame, FieldSet setfunc, PtrValue ptr, Object value, @SuppressWarnings("unused") int size,
                        @Cached PyFloatAsDoubleNode asDoubleNode) {
            double x = asDoubleNode.execute(frame, value);
            ptr.writePrimitive(setfunc.ffiType, x);
            return PNone.NONE;
        }

        @Specialization(guards = "setfunc == d_set_sw")
        static Object d_set_sw(VirtualFrame frame, FieldSet setfunc, PtrValue ptr, Object value, @SuppressWarnings("unused") int size,
                        @Cached PyFloatAsDoubleNode asDoubleNode) {
            byte[] bytes = new byte[Double.BYTES];
            CtypesNodes.SERIALIZE_BE.putDouble(bytes, 0, asDoubleNode.execute(frame, value));
            double x = SERIALIZE_LE.getDouble(bytes, 0);
            ptr.writePrimitive(setfunc.ffiType, x);
            return PNone.NONE;
        }

        @Specialization(guards = "setfunc == f_set")
        static Object f_set(VirtualFrame frame, FieldSet setfunc, PtrValue ptr, Object value, @SuppressWarnings("unused") int size,
                        @Cached PyFloatAsDoubleNode asDoubleNode) {
            float x = (float) asDoubleNode.execute(frame, value);
            ptr.writePrimitive(setfunc.ffiType, x);
            return PNone.NONE;
        }

        @Specialization(guards = "setfunc == f_set_sw")
        static Object f_set_sw(VirtualFrame frame, FieldSet setfunc, PtrValue ptr, Object value, @SuppressWarnings("unused") int size,
                        @Cached PyFloatAsDoubleNode asDoubleNode) {
            byte[] bytes = new byte[Float.BYTES];
            CtypesNodes.SERIALIZE_BE.putFloat(bytes, 0, (float) asDoubleNode.execute(frame, value));
            float x = SERIALIZE_LE.getFloat(bytes, 0);
            ptr.writePrimitive(setfunc.ffiType, x);
            return PNone.NONE;
        }

        @Specialization(guards = "setfunc == O_set")
        static Object O_set(FieldSet setfunc, PtrValue ptr, Object value, @SuppressWarnings("unused") int size) {
            /* Hm, does the memory block need it's own refcount or not? */
            ptr.writePrimitive(setfunc.ffiType, value);
            return value;
        }

        @Specialization(guards = "setfunc == c_set")
        Object c_set(FieldSet setfunc, PtrValue ptr, Object value, @SuppressWarnings("unused") int size,
                        @Cached GetInternalByteArrayNode getBytes,
                        @Shared("raise") @Cached PRaiseNode raiseNode) {
            if (PGuards.isBytes(value)) {
                PBytesLike bytes = (PBytesLike) value;
                if (bytes.getSequenceStorage().length() == 1) {
                    byte[] b = getBytes.execute(bytes.getSequenceStorage());
                    ptr.writePrimitive(setfunc.ffiType, b[0]);
                    return PNone.NONE;
                }
            }
            if (PGuards.isInteger(value)) {
                int val = (int) value;
                if (!(val < 0 || val >= 256)) {
                    byte b = (byte) val;
                    ptr.writePrimitive(setfunc.ffiType, b);
                    return PNone.NONE;
                }
            }

            throw raiseNode.raise(TypeError, ErrorMessages.ONE_CHARACTER_BYTES_BYTEARRAY_INTEGER_EXPECTED);
        }

        /* u - a single wchar_t character */
        @Specialization(guards = "setfunc == u_set")
        Object u_set(FieldSet setfunc, PtrValue ptr, Object value, @SuppressWarnings("unused") int size,
                        @Cached CastToTruffleStringNode toString,
                        @Cached GetNameNode getNameNode,
                        @Cached GetClassNode getClassNode,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached TruffleString.SwitchEncodingNode switchEncodingNode,
                        @Cached TruffleString.ReadCharUTF16Node readCharNode,
                        @Shared("raise") @Cached PRaiseNode raiseNode) { // CTYPES_UNICODE
            if (!PGuards.isString(value)) {
                throw raiseNode.raise(TypeError, ErrorMessages.UNICODE_STRING_EXPECTED_INSTEAD_OF_S_INSTANCE,
                                getNameNode.execute(getClassNode.execute(value)));
            }
            TruffleString str = toString.execute(value);
            str = switchEncodingNode.execute(str, TruffleString.Encoding.UTF_16);
            if (codePointLengthNode.execute(str, TruffleString.Encoding.UTF_16) != 1) {
                throw raiseNode.raise(TypeError, ErrorMessages.ONE_CHARACTER_UNICODE_EXPECTED);
            }
            ptr.writePrimitive(setfunc.ffiType, (short) readCharNode.execute(str, 0));
            return PNone.NONE;
        }

        @Specialization(guards = "setfunc == U_set")
        Object U_set(@SuppressWarnings("unused") FieldSet setfunc, PtrValue ptr, Object value, int size,
                        @Cached CastToTruffleStringNode toString,
                        @Cached GetNameNode getNameNode,
                        @Cached GetClassNode getClassNode,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached TruffleString.SwitchEncodingNode switchEncodingNode,
                        @Cached TruffleString.ReadCharUTF16Node readCharNode,
                        @Shared("raise") @Cached PRaiseNode raiseNode) { // CTYPES_UNICODE
            /* It's easier to calculate in characters than in bytes */
            int wcharSize = FieldDesc.u.pffi_type.size;
            int length = size / wcharSize;

            if (!PGuards.isString(value)) {
                throw raiseNode.raise(TypeError, ErrorMessages.UNICODE_STRING_EXPECTED_INSTEAD_OF_S_INSTANCE, getNameNode.execute(getClassNode.execute(value)));
            }

            TruffleString str = toString.execute(value);
            str = switchEncodingNode.execute(str, TruffleString.Encoding.UTF_16);
            int strLen = codePointLengthNode.execute(str, TruffleString.Encoding.UTF_16);
            if (strLen > length) {
                throw raiseNode.raise(ValueError, ErrorMessages.STR_TOO_LONG, strLen, length);
            }
            for (int i = 0; i < strLen; i++) {
                ptr.writeArrayElement(FieldDesc.u.pffi_type, i * 2, (short) readCharNode.execute(str, i));
            }
            return value;
        }

        @Specialization(guards = "setfunc == s_set")
        Object s_set(@SuppressWarnings("unused") FieldSet setfunc, PtrValue ptr, Object value, int length,
                        @Bind("this") Node inliningTarget,
                        @Cached GetNameNode getNameNode,
                        @Cached GetClassNode getClassNode,
                        @Cached ToBytesWithoutFrameNode getBytes,
                        @Shared("raise") @Cached PRaiseNode raiseNode) {
            byte[] data;
            int size;

            if (!PGuards.isPBytes(value)) {
                throw raiseNode.raise(TypeError, ErrorMessages.EXPECTED_BYTES_S_FOUND, getNameNode.execute(getClassNode.execute(value)));
            }

            // a copy is expected.. no need for memcpy
            data = getBytes.execute(inliningTarget, value);
            size = data.length; /* XXX Why not Py_SIZE(value)? */
            if (size < length) {
                /*
                 * This will copy the terminating NUL character if there is space for it.
                 */
                ++size;
            } else if (size > length) {
                throw raiseNode.raise(ValueError, ErrorMessages.BYTES_TOO_LONG, size, length);
            }
            /* Also copy the terminating NUL character if there is space */
            ptr.writeBytesArrayElement(data);

            return PNone.NONE;
        }

        @Specialization(guards = "setfunc == z_set")
        Object z_set(@SuppressWarnings("unused") FieldSet setfunc, PtrValue ptr, Object value, @SuppressWarnings("unused") int size,
                        @Bind("this") Node inliningTarget,
                        @Cached GetNameNode getNameNode,
                        @Cached GetClassNode getClassNode,
                        @Cached PyLongCheckNode longCheckNode,
                        @Cached ToBytesWithoutFrameNode getBytes,
                        @Cached PRaiseNode raiseNode) {
            if (value == PNone.NONE) {
                ptr.toNil();
                return value;
            }
            if (PGuards.isPBytes(value)) {
                ptr.writeBytesArrayElement(getBytes.execute(inliningTarget, value));
                return value;
            } else if (value instanceof PythonNativeVoidPtr) {
                ptr.writeBytesArrayElement(getBytes.execute(inliningTarget, ((PythonNativeVoidPtr) value).getPointerObject()));
                return PNone.NONE;
            } else if (longCheckNode.execute(value)) {
                // *(char **)ptr = (char *)PyLong_AsUnsignedLongMask(value);
                throw raiseNode.raise(PythonBuiltinClassType.NotImplementedError);
            }
            throw raiseNode.raise(TypeError, ErrorMessages.BYTES_OR_INT_ADDR_EXPECTED_INSTEAD_OF_S, getNameNode.execute(getClassNode.execute(value)));
        }

        @Specialization(guards = "setfunc == Z_set")
        Object Z_set(@SuppressWarnings("unused") FieldSet setfunc, PtrValue ptr, Object v, @SuppressWarnings("unused") int size,
                        @Cached CastToTruffleStringNode toString,
                        @Cached GetNameNode getNameNode,
                        @Cached GetClassNode getClassNode,
                        @Cached PyLongCheckNode longCheckNode,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached TruffleString.SwitchEncodingNode switchEncodingNode,
                        @Cached TruffleString.ReadCharUTF16Node readCharNode,
                        @Shared("raise") @Cached PRaiseNode raiseNode) { // CTYPES_UNICODE
            Object value = v;
            if (value == PNone.NONE) {
                ptr.toNil();
                return value;
            }
            if (value instanceof PythonNativeVoidPtr) {
                value = ((PythonNativeVoidPtr) value).getPointerObject();
                // ptr.writePrimitive(setfunc.ffiType, v);
                // return PNone.NONE;
            } else if (longCheckNode.execute(value)) {
                // *(wchar_t **)ptr = (wchar_t *)PyLong_AsUnsignedLongMask(value);
                throw raiseNode.raise(PythonBuiltinClassType.NotImplementedError);
            }
            if (!PGuards.isString(value)) {
                throw raiseNode.raise(TypeError, ErrorMessages.UNICODE_STR_OR_INT_ADDR_EXPECTED_INSTEAD_OF_S, getNameNode.execute(getClassNode.execute(value)));
            }

            /*
             * We must create a wchar_t* buffer from the unicode object, and keep it alive
             */
            TruffleString buffer = toString.execute(value);
            buffer = switchEncodingNode.execute(buffer, TruffleString.Encoding.UTF_16);
            int len = codePointLengthNode.execute(buffer, TruffleString.Encoding.UTF_16);
            ptr.ensureCapacity(len * 2);
            for (int i = 0; i < len; i++) {
                ptr.writeArrayElement(FieldDesc.u.pffi_type, i * 2, (short) readCharNode.execute(buffer, i));
            }
            return buffer;
        }

        @Specialization(guards = "setfunc == P_set")
        Object P_set(@SuppressWarnings("unused") FieldSet setfunc, PtrValue ptr, Object value, @SuppressWarnings("unused") int size,
                        @Cached PyLongCheckNode longCheckNode,
                        @Shared("raise") @Cached PRaiseNode raiseNode) {
            if (value == PNone.NONE) {
                ptr.toNil();
                return PNone.NONE;
            }

            if (!(value instanceof PythonNativeVoidPtr)) {
                if (longCheckNode.execute(value)) {
                    throw raiseNode.raise(PythonBuiltinClassType.NotImplementedError);
                }
                throw raiseNode.raise(TypeError, ErrorMessages.CANNOT_BE_CONVERTED_TO_POINTER);
            }

            // v = (void *)PyLong_AsUnsignedLongMask(value);
            Object v = ((PythonNativeVoidPtr) value).getPointerObject();
            ptr.toNativePointer(v, FFI_TYPES.FFI_TYPE_POINTER);
            return PNone.NONE;
        }

        @SuppressWarnings("unused")
        @Fallback
        Object error(VirtualFrame frame, FieldSet setfunc, PtrValue ptr, Object value, int size,
                        @Shared("raise") @Cached PRaiseNode raiseNode) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw PRaiseNode.getUncached().raise(NotImplementedError, toTruffleStringUncached("Field setter %s is not supported yet."), setfunc.name());
        }

    }

    @ImportStatic(FieldGet.class)
    @GenerateUncached
    protected abstract static class GetFuncNode extends Node {

        abstract Object execute(FieldGet getfunc, PtrValue adr, int size);

        @Specialization(guards = "getfunc == vBOOL_get")
        static Object vBOOL_get(FieldGet getfunc, PtrValue ptr, @SuppressWarnings("unused") int size) {
            Object obj = ptr.getPrimitiveValue(getfunc.ffiType);
            assert obj instanceof Short;
            // GET_BITFIELD(val, size);
            return ((short) obj) != 0;
        }

        @Specialization(guards = "getfunc == bool_get")
        static boolean bool_get(FieldGet getfunc, PtrValue ptr, @SuppressWarnings("unused") int size) {
            Object obj = ptr.getPrimitiveValue(getfunc.ffiType);
            assert obj instanceof Byte;
            return ((byte) obj) != 0;
        }

        @Specialization(guards = "getfunc == b_get")
        static int b_get(FieldGet getfunc, PtrValue ptr, @SuppressWarnings("unused") int size) {
            Object obj = ptr.getPrimitiveValue(getfunc.ffiType);
            assert obj instanceof Byte;
            byte b = (byte) obj;
            // GET_BITFIELD(val, size);
            return b;
        }

        @Specialization(guards = "getfunc == B_get")
        static int B_get(FieldGet getfunc, PtrValue ptr, @SuppressWarnings("unused") int size) {
            Object obj = ptr.getPrimitiveValue(getfunc.ffiType);
            assert obj instanceof Byte;
            byte b = (byte) obj;
            // GET_BITFIELD(val, size);
            return 0xFF & b;
        }

        @Specialization(guards = "getfunc == h_get")
        static int h_get(FieldGet getfunc, PtrValue ptr, @SuppressWarnings("unused") int size) {
            Object obj = ptr.getPrimitiveValue(getfunc.ffiType);
            assert obj instanceof Short;
            // GET_BITFIELD(val, size);
            return (short) obj;
        }

        @Specialization(guards = "getfunc == h_get_sw")
        static int h_get_sw(FieldGet getfunc, PtrValue ptr, @SuppressWarnings("unused") int size) {
            Object obj = ptr.getPrimitiveValue(getfunc.ffiType);
            assert obj instanceof Short;
            short val = (short) obj;
            val = SWAP_2(val);
            // GET_BITFIELD(val, size);
            return val;
        }

        @Specialization(guards = "getfunc == H_get")
        static int H_get(FieldGet getfunc, PtrValue ptr, @SuppressWarnings("unused") int size) {
            Object obj = ptr.getPrimitiveValue(getfunc.ffiType);
            assert obj instanceof Short;
            short s = (short) obj;
            // GET_BITFIELD(val, size);
            return 0xFFFF & s;
        }

        @Specialization(guards = "getfunc == H_get_sw")
        static int H_get_sw(FieldGet getfunc, PtrValue ptr, @SuppressWarnings("unused") int size) {
            Object obj = ptr.getPrimitiveValue(getfunc.ffiType);
            assert obj instanceof Short;
            short val = SWAP_2((short) obj);
            // GET_BITFIELD(val, size);
            return 0xFFFF & val;
        }

        @Specialization(guards = "getfunc == i_get")
        static int i_get(FieldGet getfunc, PtrValue ptr, @SuppressWarnings("unused") int size) {
            Object obj = ptr.getPrimitiveValue(getfunc.ffiType);
            assert obj instanceof Integer;
            // GET_BITFIELD(val, size);
            return (int) obj;
        }

        @Specialization(guards = "getfunc == i_get_sw")
        static Object i_get_sw(FieldGet getfunc, PtrValue ptr, @SuppressWarnings("unused") int size) {
            Object obj = ptr.getPrimitiveValue(getfunc.ffiType);
            assert obj instanceof Integer;
            // GET_BITFIELD(val, size);
            return SWAP_4((int) obj);
        }

        @Specialization(guards = "getfunc == I_get")
        static Object I_get(FieldGet getfunc, PtrValue ptr, @SuppressWarnings("unused") int size) {
            Object obj = ptr.getPrimitiveValue(getfunc.ffiType);
            assert obj instanceof Integer;
            int val = (int) obj;
            // GET_BITFIELD(val, size);
            return val < 0 ? 0xFFFFFFFFL & val : val;
        }

        @Specialization(guards = "getfunc == I_get_sw")
        static Object I_get_sw(FieldGet getfunc, PtrValue ptr, @SuppressWarnings("unused") int size) {
            Object obj = ptr.getPrimitiveValue(getfunc.ffiType);
            assert obj instanceof Integer;
            int val = SWAP_4((int) obj);
            // GET_BITFIELD(val, size);
            return val < 0 ? 0xFFFFFFFFL & val : val;
        }

        @Specialization(guards = "getfunc == l_get")
        static Object l_get(FieldGet getfunc, PtrValue ptr, @SuppressWarnings("unused") int size) {
            Object obj = ptr.getPrimitiveValue(getfunc.ffiType);
            assert obj instanceof Long;
            // GET_BITFIELD(val, size);
            return obj;
        }

        @Specialization(guards = "getfunc == l_get_sw")
        static Object l_get_sw(FieldGet getfunc, PtrValue ptr, @SuppressWarnings("unused") int size) {
            Object obj = ptr.getPrimitiveValue(getfunc.ffiType);
            assert obj instanceof Long;
            long val = (long) obj;
            // GET_BITFIELD(val, size);
            return SWAP_8(val);
        }

        @Specialization(guards = "getfunc == L_get")
        static Object L_get(FieldGet getfunc, PtrValue ptr, @SuppressWarnings("unused") int size,
                        @Cached PythonObjectFactory factory) {
            Object obj = ptr.getPrimitiveValue(getfunc.ffiType);
            assert obj instanceof Long;
            long val = (long) obj;
            // GET_BITFIELD(val, size);
            return val < 0 ? factory.createInt(PInt.longToUnsignedBigInteger(val)) : val;
        }

        @Specialization(guards = "getfunc == L_get_sw")
        static Object L_get_sw(FieldGet getfunc, PtrValue ptr, @SuppressWarnings("unused") int size,
                        @Cached PythonObjectFactory factory) {
            Object obj = ptr.getPrimitiveValue(getfunc.ffiType);
            assert obj instanceof Long;
            long val = (long) obj;
            val = SWAP_8(val);
            // GET_BITFIELD(val, size);
            return val < 0 ? factory.createInt(PInt.longToUnsignedBigInteger(val)) : val;
        }

        @Specialization(guards = "getfunc == d_get")
        static Object d_get(FieldGet getfunc, PtrValue ptr, @SuppressWarnings("unused") int size) {
            Object obj = ptr.getPrimitiveValue(getfunc.ffiType);
            assert obj instanceof Double;
            return obj;
        }

        @Specialization(guards = "getfunc == d_get_sw")
        static double d_get_sw(FieldGet getfunc, PtrValue ptr, @SuppressWarnings("unused") int size) {
            Object obj = ptr.getPrimitiveValue(getfunc.ffiType);
            assert obj instanceof Double;
            byte[] bytes = new byte[Double.BYTES];
            CtypesNodes.SERIALIZE_BE.putDouble(bytes, 0, (double) obj);
            return SERIALIZE_LE.getDouble(bytes, 0);
        }

        @Specialization(guards = "getfunc == f_get")
        static double f_get(FieldGet getfunc, PtrValue ptr, @SuppressWarnings("unused") int size) {
            Object obj = ptr.getPrimitiveValue(getfunc.ffiType);
            assert obj instanceof Float;
            return (float) obj;
        }

        @Specialization(guards = "getfunc == f_get_sw")
        static double f_get_sw(FieldGet getfunc, PtrValue ptr, @SuppressWarnings("unused") int size) {
            Object obj = ptr.getPrimitiveValue(getfunc.ffiType);
            assert obj instanceof Float;
            byte[] bytes = new byte[Float.BYTES];
            CtypesNodes.SERIALIZE_BE.putFloat(bytes, 0, (float) obj);
            return SERIALIZE_LE.getFloat(bytes, 0);
        }

        /*
         * py_object refcounts:
         *
         * 1. If we have a py_object instance, O_get must Py_INCREF the returned object, of course.
         * If O_get is called from a function result, no py_object instance is created - so
         * callproc.c::GetResult has to call Py_DECREF.
         *
         * 2. The memory block in py_object owns a refcount. So, py_object must call Py_DECREF on
         * destruction. Maybe only when b_needsfree is non-zero.
         */
        @Specialization(guards = "getfunc == O_get")
        Object O_get(FieldGet getfunc, PtrValue ptr, @SuppressWarnings("unused") int size,
                        @Cached PRaiseNode raiseNode) {
            if (ptr.isNil()) {
                /* Set an error if not yet set */
                throw raiseNode.raise(ValueError, ErrorMessages.PY_OBJ_IS_NULL);
            }
            return ptr.getPrimitiveValue(getfunc.ffiType);
        }

        @Specialization(guards = "getfunc == c_get")
        static Object c_get(FieldGet getfunc, PtrValue ptr, @SuppressWarnings("unused") int size,
                        @Cached PythonObjectFactory factory) {
            Object obj = ptr.getPrimitiveValue(getfunc.ffiType);
            assert obj instanceof Byte;
            byte b = (byte) obj;
            return factory.createBytes(new byte[]{b});
        }

        @Specialization(guards = "getfunc == u_get")
        static Object u_get(FieldGet getfunc, PtrValue ptr, @SuppressWarnings("unused") int size,
                        @Cached TruffleString.FromCharArrayUTF16Node fromCharArrayUTF16Node,
                        @Cached TruffleString.SwitchEncodingNode switchEncodingNode) { // CTYPES_UNICODE
            Object obj = ptr.getPrimitiveValue(getfunc.ffiType);
            assert obj instanceof Short;
            short v = (short) obj;
            return switchEncodingNode.execute(fromCharArrayUTF16Node.execute(new char[]{(char) v}), TS_ENCODING);
        }

        /* U - a unicode string */
        @Specialization(guards = "getfunc == U_get")
        static Object U_get(@SuppressWarnings("unused") FieldGet getfunc, PtrValue ptr, @SuppressWarnings("unused") int size,
                        @Cached TruffleString.FromCharArrayUTF16Node fromCharArrayUTF16Node,
                        @Cached TruffleString.SwitchEncodingNode switchEncodingNode) { // CTYPES_UNICODE
            assert ptr.ptr instanceof ByteArrayStorage;
            byte[] p = ((ByteArrayStorage) ptr.ptr).value;
            int wcharSize = FieldDesc.u.pffi_type.size;
            int s = Math.min(size, p.length) / wcharSize;
            /*
             * We need 'result' to be able to count the characters with wcslen, since ptr may not be
             * NUL terminated. If the length is smaller (if it was actually NUL terminated, we
             * construct a new one and throw away the result.
             */
            /* chop off at the first NUL character, if any. */
            char[] str = new char[s];
            for (int i = 0; i < str.length; i++) {
                char c = (char) SERIALIZE_LE.getShort(p, i * 2);
                if (c == 0) {
                    str = PythonUtils.arrayCopyOf(str, i);
                    break;
                }
                str[i] = c;
            }
            return switchEncodingNode.execute(fromCharArrayUTF16Node.execute(str), TS_ENCODING);
        }

        @Specialization(guards = "getfunc == s_get")
        static Object s_get(@SuppressWarnings("unused") FieldGet getfunc, PtrValue ptr, @SuppressWarnings("unused") int size,
                        @Cached PythonObjectFactory factory) {
            assert ptr.ptr instanceof ByteArrayStorage;
            byte[] p = ((ByteArrayStorage) ptr.ptr).value;

            int i;
            for (i = 0; i < size; ++i) {
                if (p[i] == '\0') {
                    break;
                }
            }

            byte[] str = i < p.length ? PythonUtils.arrayCopyOf(p, i) : p;
            return factory.createBytes(str);
        }

        @Specialization(guards = "getfunc == z_get")
        static Object z_get(@SuppressWarnings("unused") FieldGet getfunc, PtrValue ptr, @SuppressWarnings("unused") int size,
                        @Cached PythonObjectFactory factory,
                        @CachedLibrary(limit = "1") InteropLibrary lib, /*- limit=1 should be enough for nfi pointer */
                        @Cached PRaiseNode raiseNode,
                        @Cached GetBytesFromNativePointerNode getNativeBytes) {
            /* XXX What about invalid pointers ??? */
            byte[] bytes = null;
            if (!ptr.isNil()) {
                if (ptr.ptr instanceof ByteArrayStorage) {
                    bytes = ((ByteArrayStorage) ptr.ptr).value;
                    bytes = PythonUtils.arrayCopyOfRange(bytes, ptr.offset, bytes.length - ptr.offset);
                } else if (ptr.ptr instanceof NativePointerStorage) {
                    try {
                        Object adr;
                        if (ptr.offset > 0) {
                            adr = lib.asPointer(ptr.getNativePointer()) + ptr.offset;
                        } else {
                            adr = ptr.getNativePointer();
                        }
                        if (ptr.ptr.type == FFI_TYPES.FFI_TYPE_STRUCT) {
                            // We need to get the pointer from the struct
                            byte[] pbytes = getNativeBytes.execute(adr, FFI_TYPES.FFI_TYPE_POINTER.getSize());
                            Object p = SERIALIZE_LE.getLong(pbytes, 0);
                            // Now we have the byte buffer pointer, we can get the string of bytes.
                            bytes = getNativeBytes.execute(p, -1);
                        } else {
                            assert ptr.ptr.type == FFI_TYPES.FFI_TYPE_POINTER;
                            bytes = getNativeBytes.execute(adr, -1);
                        }
                    } catch (UnsupportedMessageException e) {
                        throw raiseNode.raise(SystemError, e);
                    }
                }
                if (bytes == null) {
                    throw raiseNode.raise(SystemError, NOT_IMPLEMENTED);
                }
                return factory.createBytes(bytes);
            } else {
                return PNone.NONE;
            }
        }

        @Specialization(guards = "getfunc == Z_get")
        static Object Z_get(@SuppressWarnings("unused") FieldGet getfunc, PtrValue ptr, @SuppressWarnings("unused") int size,
                        @Cached TruffleString.FromCharArrayUTF16Node fromCharArrayUTF16Node,
                        @Cached TruffleString.SwitchEncodingNode switchEncodingNode) {
            if (!ptr.isNil()) {
                assert ptr.ptr instanceof ByteArrayStorage;
                byte[] p = ((ByteArrayStorage) ptr.ptr).value;
                int wcharSize = FieldDesc.u.pffi_type.size;
                int s = p.length / wcharSize;
                char[] str = new char[s];
                for (int i = 0; i < s; i++) {
                    char c = (char) SERIALIZE_LE.getShort(p, i * 2);
                    if (c == 0) {
                        str = PythonUtils.arrayCopyOf(str, i);
                        break;
                    }
                    str[i] = c;
                }
                return switchEncodingNode.execute(fromCharArrayUTF16Node.execute(str), TS_ENCODING);
            } else {
                return PNone.NONE;
            }
        }

        @Specialization(guards = "getfunc == P_get")
        static Object P_get(@SuppressWarnings("unused") FieldGet getfunc, PtrValue ptr, int size,
                        @CachedLibrary(limit = "1") InteropLibrary ilib,
                        @Cached PythonObjectFactory factory) {
            assert size == 8;
            if (ptr.isNil()) {
                return 0L;
            }
            Object p;
            if (ptr.ptr instanceof MemoryViewStorage storage) {
                PMemoryView mv = storage.value;
                p = mv.getBufferPointer();
                // TODO this doesn't work for managed memory views that didn't go native
                p = p != null ? p : mv.getBuffer();
            } else if (ptr.ptr instanceof NativePointerStorage storage) {
                p = storage.getValue();
                if (p == null) {
                    return 0L;
                }
            } else if (ptr.ptr instanceof ByteArrayStorage storage) {
                return PythonUtils.arrayAccessor.getLong(storage.value, 0);
            } else if (ptr.ptr instanceof PrimitiveStorage storage) {
                return storage.getValue();
            } else {
                return 0L;
            }
            if (ilib.isPointer(p)) {
                try {
                    return factory.createNativeVoidPtr(p, ilib.asPointer(p));
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
            } else {
                return factory.createNativeVoidPtr(p);
            }
        }

    }
}
