/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.modules.ctypes.CtypesNodes.WCHAR_T_ENCODING;
import static com.oracle.graal.python.builtins.modules.ctypes.CtypesNodes.WCHAR_T_SIZE;
import static com.oracle.graal.python.nodes.ErrorMessages.CANT_DELETE_ATTRIBUTE;
import static com.oracle.graal.python.nodes.ErrorMessages.HAS_NO_STGINFO;
import static com.oracle.graal.python.nodes.ErrorMessages.NOT_A_CTYPE_INSTANCE;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REPR__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.NotImplementedError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.ARRAY_ACCESSOR_SWAPPED;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import java.util.List;

import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
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
import com.oracle.graal.python.builtins.modules.ctypes.StgDictBuiltins.PyTypeStgDictNode;
import com.oracle.graal.python.builtins.modules.ctypes.memory.Pointer;
import com.oracle.graal.python.builtins.modules.ctypes.memory.PointerNodes;
import com.oracle.graal.python.builtins.modules.ctypes.memory.PointerReference;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes.ToBytesWithoutFrameNode;
import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.capsule.PyCapsule;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GetInternalByteArrayNode;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.str.StringUtils.SimpleTruffleStringFormatNode;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotDescrGet.DescrGetBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotDescrSet.DescrSetBuiltinNode;
import com.oracle.graal.python.lib.PyFloatAsDoubleNode;
import com.oracle.graal.python.lib.PyLongAsLongNode;
import com.oracle.graal.python.lib.PyLongCheckNode;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.InternalByteArray;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.CField)
public final class CFieldBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = CFieldBuiltinsSlotsGen.SLOTS;

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

    @Slot(value = SlotKind.tp_descr_set, isComplex = true)
    @GenerateNodeFactory
    public abstract static class DescrSet extends DescrSetBuiltinNode {
        @Specialization(guards = "!isNoValue(value)")
        static void doit(VirtualFrame frame, CFieldObject self, Object inst, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached PyTypeCheck pyTypeCheck,
                        @Cached PyCDataSetNode cDataSetNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (!pyTypeCheck.isCDataObject(inliningTarget, inst)) {
                throw raiseNode.get(inliningTarget).raise(TypeError, NOT_A_CTYPE_INSTANCE);
            }
            CDataObject dst = (CDataObject) inst;
            cDataSetNode.execute(frame, dst, self.proto, self.setfunc, value, self.index, self.size, dst.b_ptr.withOffset(self.offset));
        }

        @Specialization(guards = "isNoValue(value)")
        @InliningCutoff
        static void doit(CFieldObject self, Object inst, Object value,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, CANT_DELETE_ATTRIBUTE);
        }
    }

    @Slot(SlotKind.tp_descr_get)
    @GenerateUncached
    @GenerateNodeFactory
    abstract static class GetNode extends DescrGetBuiltinNode {

        @Specialization
        static Object doit(CFieldObject self, Object inst, @SuppressWarnings("unused") Object type,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedConditionProfile instIsNoValueProfile,
                        @Cached PyCDataGetNode pyCDataGetNode,
                        @Cached PyTypeCheck pyTypeCheck,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (instIsNoValueProfile.profile(inliningTarget, inst == PNone.NO_VALUE)) {
                return self;
            }
            if (!pyTypeCheck.isCDataObject(inliningTarget, inst)) {
                throw raiseNode.get(inliningTarget).raise(TypeError, NOT_A_CTYPE_INSTANCE);
            }
            CDataObject src = (CDataObject) inst;
            return pyCDataGetNode.execute(inliningTarget, self.proto, self.getfunc, src, self.index, self.size, src.b_ptr.withOffset(self.offset));
        }
    }

    @Builtin(name = J___REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {

        @Specialization
        TruffleString PyCField_repr(CFieldObject self,
                        @Bind("this") Node inliningTarget,
                        @Cached GetNameNode getNameNode,
                        @Cached SimpleTruffleStringFormatNode simpleTruffleStringFormatNode) {
            int bits = self.size >> 16;
            int size = self.size & 0xFFFF;
            TruffleString name = getNameNode.execute(inliningTarget, self.proto);
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
    @GenerateInline
    @GenerateCached(false)
    @SuppressWarnings("fallthrough")
    abstract static class PyCFieldFromDesc extends Node {

        abstract CFieldObject execute(Node inliningTarget, Object desc, int index, int bitsize, int pack, boolean big_endian, int[] props, PythonObjectFactory factory);

        @Specialization
        static CFieldObject PyCField_FromDesc(Node inliningTarget, Object desc, int index, int bitsize, int pack, boolean big_endian, int[] props, PythonObjectFactory factory,
                        @Cached PyTypeCheck pyTypeCheck,
                        @Cached PyTypeStgDictNode pyTypeStgDictNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            CFieldObject self = factory.createCFieldObject(PythonBuiltinClassType.CField);
            StgDictObject dict = pyTypeStgDictNode.execute(inliningTarget, desc);
            if (dict == null) {
                throw raiseNode.get(inliningTarget).raise(TypeError, HAS_NO_STGINFO);
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
            if (pyTypeCheck.isPyCArrayTypeObject(inliningTarget, desc)) {
                StgDictObject adict = pyTypeStgDictNode.execute(inliningTarget, desc);
                if (adict != null && adict.proto != null) {
                    StgDictObject idict = pyTypeStgDictNode.execute(inliningTarget, adict.proto);
                    if (idict == null) {
                        throw raiseNode.get(inliningTarget).raise(TypeError, HAS_NO_STGINFO);
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

    /*
     * Accessor functions
     */

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
    @SuppressWarnings("truffle-inlining")       // footprint reduction 112 -> 96
    protected abstract static class SetFuncNode extends Node {

        abstract Object execute(VirtualFrame frame, FieldSet setfunc, Pointer ptr, Object value, int size);

        @Specialization(guards = "setfunc == b_set || setfunc == B_set")
        static Object b_set(VirtualFrame frame, @SuppressWarnings("unused") FieldSet setfunc, Pointer ptr, Object value, @SuppressWarnings("unused") int size,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PyLongAsLongNode asLongNode,
                        @Shared @Cached PointerNodes.WriteByteNode writeByteNode) {
            byte val = (byte) asLongNode.execute(frame, inliningTarget, value);
            writeByteNode.execute(inliningTarget, ptr, val);
            return PNone.NONE;
        }

        @Specialization(guards = "setfunc == h_set || setfunc == H_set")
        static Object h_set(VirtualFrame frame, @SuppressWarnings("unused") FieldSet setfunc, Pointer ptr, Object value, @SuppressWarnings("unused") int size,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PyLongAsLongNode asLongNode,
                        @Shared @Cached PointerNodes.WriteShortNode writeShortNode) {
            short val = (short) asLongNode.execute(frame, inliningTarget, value);
            writeShortNode.execute(inliningTarget, ptr, val);
            return PNone.NONE;
        }

        @Specialization(guards = "setfunc == h_set_sw || setfunc == H_set_sw")
        static Object h_set_sw(VirtualFrame frame, @SuppressWarnings("unused") FieldSet setfunc, Pointer ptr, Object value, @SuppressWarnings("unused") int size,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PyLongAsLongNode asLongNode,
                        @Shared @Cached PointerNodes.WriteShortNode writeShortNode) {
            short val = (short) asLongNode.execute(frame, inliningTarget, value);
            val = SWAP_2(val);
            writeShortNode.execute(inliningTarget, ptr, val);
            return PNone.NONE;
        }

        @Specialization(guards = "setfunc == i_set || setfunc == I_set")
        static Object i_set(VirtualFrame frame, @SuppressWarnings("unused") FieldSet setfunc, Pointer ptr, Object value, @SuppressWarnings("unused") int size,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PyLongAsLongNode asLongNode,
                        @Shared @Cached PointerNodes.WriteIntNode writeIntNode) {
            int val = (int) asLongNode.execute(frame, inliningTarget, value);
            writeIntNode.execute(inliningTarget, ptr, val);
            return PNone.NONE;
        }

        @Specialization(guards = "setfunc == i_set_sw || setfunc == I_set_sw")
        static Object i_set_sw(VirtualFrame frame, @SuppressWarnings("unused") FieldSet setfunc, Pointer ptr, Object value, @SuppressWarnings("unused") int size,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PyLongAsLongNode asLongNode,
                        @Shared @Cached PointerNodes.WriteIntNode writeIntNode) {
            int val = (int) asLongNode.execute(frame, inliningTarget, value);
            val = SWAP_4(val);
            writeIntNode.execute(inliningTarget, ptr, val);
            return PNone.NONE;
        }

        /* http://msdn.microsoft.com/en-us/library/cc237864.aspx */
        private static final short VARIANT_FALSE = 0x0000;
        private static final short VARIANT_TRUE = (short) 0xFFFF;

        /* short BOOL - VARIANT_BOOL */
        @Specialization(guards = "setfunc == vBOOL_set")
        static Object vBOOL_set(VirtualFrame frame, @SuppressWarnings("unused") FieldSet setfunc, Pointer ptr, Object value, @SuppressWarnings("unused") int size,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PyObjectIsTrueNode isTrueNode,
                        @Shared @Cached PointerNodes.WriteShortNode writeShortNode) {
            short val;
            if (!isTrueNode.execute(frame, inliningTarget, value)) {
                val = VARIANT_FALSE;
            } else {
                val = VARIANT_TRUE;
            }
            writeShortNode.execute(inliningTarget, ptr, val);
            return PNone.NONE;
        }

        @Specialization(guards = "setfunc == bool_set")
        static Object bool_set(VirtualFrame frame, @SuppressWarnings("unused") FieldSet setfunc, Pointer ptr, Object value, @SuppressWarnings("unused") int size,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PyObjectIsTrueNode isTrueNode,
                        @Shared @Cached PointerNodes.WriteByteNode writeByteNode) {
            byte val = (byte) (isTrueNode.execute(frame, inliningTarget, value) ? 1 : 0);
            writeByteNode.execute(inliningTarget, ptr, val);
            return PNone.NONE;
        }

        @Specialization(guards = "setfunc == l_set || setfunc == L_set")
        static Object l_set(VirtualFrame frame, @SuppressWarnings("unused") FieldSet setfunc, Pointer ptr, Object value, @SuppressWarnings("unused") int size,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PyLongAsLongNode asLongNode,
                        @Shared @Cached PointerNodes.WriteLongNode writeLongNode) {
            long val = asLongNode.execute(frame, inliningTarget, value);
            writeLongNode.execute(inliningTarget, ptr, val);
            return PNone.NONE;
        }

        @Specialization(guards = "setfunc == l_set_sw || setfunc == L_set_sw")
        static Object l_set_sw(VirtualFrame frame, @SuppressWarnings("unused") FieldSet setfunc, Pointer ptr, Object value, @SuppressWarnings("unused") int size,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PyLongAsLongNode asLongNode,
                        @Shared @Cached PointerNodes.WriteLongNode writeLongNode) {
            long val = asLongNode.execute(frame, inliningTarget, value);
            val = SWAP_8(val);
            writeLongNode.execute(inliningTarget, ptr, val);
            return PNone.NONE;
        }

        /*****************************************************************
         * non-integer accessor methods, not supporting bit fields
         */

        @Specialization(guards = "setfunc == d_set || setfunc == g_set")
        static Object d_set(VirtualFrame frame, @SuppressWarnings("unused") FieldSet setfunc, Pointer ptr, Object value, @SuppressWarnings("unused") int size,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached PyFloatAsDoubleNode asDoubleNode,
                        @Exclusive @Cached PointerNodes.WriteLongNode writeLongNode) {
            double x = asDoubleNode.execute(frame, inliningTarget, value);
            writeLongNode.execute(inliningTarget, ptr, Double.doubleToRawLongBits(x));
            return PNone.NONE;
        }

        @Specialization(guards = "setfunc == d_set_sw")
        static Object d_set_sw(VirtualFrame frame, @SuppressWarnings("unused") FieldSet setfunc, Pointer ptr, Object value, @SuppressWarnings("unused") int size,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached PyFloatAsDoubleNode asDoubleNode,
                        @Exclusive @Cached PointerNodes.WriteLongNode writeLongNode) {
            writeLongNode.execute(inliningTarget, ptr, SWAP_8(Double.doubleToRawLongBits(asDoubleNode.execute(frame, inliningTarget, value))));
            return PNone.NONE;
        }

        @Specialization(guards = "setfunc == f_set")
        static Object f_set(VirtualFrame frame, @SuppressWarnings("unused") FieldSet setfunc, Pointer ptr, Object value, @SuppressWarnings("unused") int size,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached PyFloatAsDoubleNode asDoubleNode,
                        @Exclusive @Cached PointerNodes.WriteIntNode writeIntNode) {
            float x = (float) asDoubleNode.execute(frame, inliningTarget, value);
            writeIntNode.execute(inliningTarget, ptr, Float.floatToRawIntBits(x));
            return PNone.NONE;
        }

        @Specialization(guards = "setfunc == f_set_sw")
        static Object f_set_sw(VirtualFrame frame, @SuppressWarnings("unused") FieldSet setfunc, Pointer ptr, Object value, @SuppressWarnings("unused") int size,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached PyFloatAsDoubleNode asDoubleNode,
                        @Exclusive @Cached PointerNodes.WriteIntNode writeIntNode) {
            writeIntNode.execute(inliningTarget, ptr, SWAP_4(Float.floatToRawIntBits((float) asDoubleNode.execute(frame, inliningTarget, value))));
            return PNone.NONE;
        }

        @Specialization(guards = "setfunc == O_set")
        @SuppressWarnings("unused")
        static Object O_set(@SuppressWarnings("unused") FieldSet setfunc, Pointer ptr, Object value, @SuppressWarnings("unused") int size,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached PointerNodes.WritePointerNode writePointerNode,
                        @Shared @Cached PythonObjectFactory factory) {
            writePointerNode.execute(inliningTarget, ptr, Pointer.pythonObject(value));
            return PNone.NONE;
        }

        @Specialization(guards = "setfunc == c_set")
        static Object c_set(@SuppressWarnings("unused") FieldSet setfunc, Pointer ptr, Object value, @SuppressWarnings("unused") int size,
                        @Bind("this") Node inliningTarget,
                        @Cached GetInternalByteArrayNode getBytes,
                        @Exclusive @Cached PointerNodes.WriteByteNode writeByteNode,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            if (PGuards.isBytes(value)) {
                PBytesLike bytes = (PBytesLike) value;
                if (bytes.getSequenceStorage().length() == 1) {
                    byte[] b = getBytes.execute(inliningTarget, bytes.getSequenceStorage());
                    writeByteNode.execute(inliningTarget, ptr, b[0]);
                    return PNone.NONE;
                }
            }
            if (PGuards.isInteger(value)) {
                int val = (int) value;
                if (!(val < 0 || val >= 256)) {
                    writeByteNode.execute(inliningTarget, ptr, (byte) val);
                    return PNone.NONE;
                }
            }

            throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.ONE_CHARACTER_BYTES_BYTEARRAY_INTEGER_EXPECTED);
        }

        /* u - a single wchar_t character */
        @Specialization(guards = "setfunc == u_set")
        static Object u_set(@SuppressWarnings("unused") FieldSet setfunc, Pointer ptr, Object value, @SuppressWarnings("unused") int size,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached CastToTruffleStringNode toString,
                        @Shared @Cached TruffleString.SwitchEncodingNode switchEncodingNode,
                        @Shared @Cached TruffleString.GetInternalByteArrayNode getInternalByteArrayNode,
                        @Exclusive @Cached PointerNodes.WriteBytesNode writeBytesNode,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) { // CTYPES_UNICODE
            if (!PGuards.isString(value)) {
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.UNICODE_STRING_EXPECTED_INSTEAD_OF_P_INSTANCE, value);
            }
            TruffleString str = switchEncodingNode.execute(toString.execute(inliningTarget, value), WCHAR_T_ENCODING);
            InternalByteArray bytes = getInternalByteArrayNode.execute(str, WCHAR_T_ENCODING);
            if (bytes.getLength() != WCHAR_T_SIZE) {
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.ONE_CHARACTER_UNICODE_EXPECTED);
            }
            writeBytesNode.execute(inliningTarget, ptr, bytes.getArray(), bytes.getOffset(), bytes.getLength());
            return PNone.NONE;
        }

        @Specialization(guards = "setfunc == U_set")
        static Object U_set(@SuppressWarnings("unused") FieldSet setfunc, Pointer ptr, Object value, int size,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached CastToTruffleStringNode toString,
                        @Shared @Cached TruffleString.SwitchEncodingNode switchEncodingNode,
                        @Shared @Cached TruffleString.GetInternalByteArrayNode getInternalByteArrayNode,
                        @Exclusive @Cached PointerNodes.WriteBytesNode writeBytesNode,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) { // CTYPES_UNICODE
            if (!PGuards.isString(value)) {
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.UNICODE_STRING_EXPECTED_INSTEAD_OF_P_INSTANCE, value);
            }

            TruffleString str = switchEncodingNode.execute(toString.execute(inliningTarget, value), WCHAR_T_ENCODING);
            InternalByteArray bytes = getInternalByteArrayNode.execute(str, WCHAR_T_ENCODING);
            if (bytes.getLength() > size) {
                throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.STR_TOO_LONG, bytes.getLength(), size);
            }
            writeBytesNode.execute(inliningTarget, ptr, bytes.getArray(), bytes.getOffset(), bytes.getLength());
            return value;
        }

        @Specialization(guards = "setfunc == s_set")
        static Object s_set(@SuppressWarnings("unused") FieldSet setfunc, Pointer ptr, Object value, int length,
                        @Bind("this") Node inliningTarget,
                        @Cached ToBytesWithoutFrameNode getBytes,
                        @Exclusive @Cached PointerNodes.WriteBytesNode writeBytesNode,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            if (!PGuards.isPBytes(value)) {
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.EXPECTED_BYTES_P_FOUND, value);
            }

            byte[] data = getBytes.execute(inliningTarget, value);
            int size = data.length;
            if (size < length) {
                /*
                 * This will copy the terminating NUL character if there is space for it.
                 */
                ++size;
            } else if (size > length) {
                throw raiseNode.get(inliningTarget).raise(ValueError, ErrorMessages.BYTES_TOO_LONG, size, length);
            }
            writeBytesNode.execute(inliningTarget, ptr, data);

            return PNone.NONE;
        }

        @Specialization(guards = "setfunc == z_set")
        static Object z_set(@SuppressWarnings("unused") FieldSet setfunc, Pointer ptr, Object value, @SuppressWarnings("unused") int size,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached PyLongCheckNode longCheckNode,
                        @Exclusive @Cached PointerNodes.PointerFromLongNode pointerFromLongNode,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib,
                        @Exclusive @Cached PointerNodes.WritePointerNode writePointerNode,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            if (value == PNone.NONE) {
                writePointerNode.execute(inliningTarget, ptr, Pointer.NULL);
                return PNone.NONE;
            } else if (longCheckNode.execute(inliningTarget, value)) {
                writePointerNode.execute(inliningTarget, ptr, pointerFromLongNode.execute(inliningTarget, value));
                return PNone.NONE;
            }
            if (PGuards.isPBytes(value)) {
                int len = bufferLib.getBufferLength(value);
                byte[] bytes = new byte[len + 1];
                bufferLib.readIntoByteArray(value, 0, bytes, 0, len);
                /* ptr is a char**, we need to add the indirection */
                Pointer valuePtr = Pointer.bytes(bytes);
                writePointerNode.execute(inliningTarget, ptr, valuePtr);
                /*
                 * We make a copy of the memory, so we need to register a destructor to free the
                 * memory in case it goes to native.
                 */
                new PointerReference(value, valuePtr, PythonContext.get(inliningTarget).getSharedFinalizer());
                return value;
            }
            throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.BYTES_OR_INT_ADDR_EXPECTED_INSTEAD_OF_P, value);
        }

        @Specialization(guards = "setfunc == Z_set")
        static Object Z_set(@SuppressWarnings("unused") FieldSet setfunc, Pointer ptr, Object value, @SuppressWarnings("unused") int size,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached CastToTruffleStringNode toString,
                        @Exclusive @Cached PyLongCheckNode longCheckNode,
                        @Exclusive @Cached PointerNodes.PointerFromLongNode pointerFromLongNode,
                        @Shared @Cached TruffleString.SwitchEncodingNode switchEncodingNode,
                        @Cached TruffleString.CopyToByteArrayNode copyToByteArrayNode,
                        @Exclusive @Cached PointerNodes.WritePointerNode writePointerNode,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode,
                        @Shared @Cached PythonObjectFactory factory) { // CTYPES_UNICODE
            if (value == PNone.NONE) {
                writePointerNode.execute(inliningTarget, ptr, Pointer.NULL);
                return PNone.NONE;
            } else if (longCheckNode.execute(inliningTarget, value)) {
                writePointerNode.execute(inliningTarget, ptr, pointerFromLongNode.execute(inliningTarget, value));
                return PNone.NONE;
            }
            if (!PGuards.isString(value)) {
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.UNICODE_STR_OR_INT_ADDR_EXPECTED_INSTEAD_OF_P, value);
            }

            TruffleString str = switchEncodingNode.execute(toString.execute(inliningTarget, value), WCHAR_T_ENCODING);
            int byteLength = str.byteLength(WCHAR_T_ENCODING);
            byte[] bytes = new byte[byteLength + WCHAR_T_SIZE];
            copyToByteArrayNode.execute(str, 0, bytes, 0, byteLength, WCHAR_T_ENCODING);

            /* ptr is a char**, we need to add the indirection */
            Pointer valuePtr = Pointer.bytes(bytes);
            writePointerNode.execute(inliningTarget, ptr, valuePtr);
            return createPyMemCapsule(valuePtr, factory);
        }

        @Specialization(guards = "setfunc == P_set")
        static Object P_set(@SuppressWarnings("unused") FieldSet setfunc, Pointer ptr, Object value, @SuppressWarnings("unused") int size,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached PyLongCheckNode longCheckNode,
                        @Exclusive @Cached PointerNodes.PointerFromLongNode pointerFromLongNode,
                        @Exclusive @Cached PointerNodes.WritePointerNode writePointerNode,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            Pointer valuePtr;
            if (value == PNone.NONE) {
                valuePtr = Pointer.NULL;
            } else if (longCheckNode.execute(inliningTarget, value)) {
                valuePtr = pointerFromLongNode.execute(inliningTarget, value);
            } else {
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.CANNOT_BE_CONVERTED_TO_POINTER);
            }

            writePointerNode.execute(inliningTarget, ptr, valuePtr);
            return PNone.NONE;
        }

        @SuppressWarnings("unused")
        @Fallback
        static Object error(VirtualFrame frame, FieldSet setfunc, Pointer ptr, Object value, int size) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw PRaiseNode.getUncached().raise(NotImplementedError, toTruffleStringUncached("Field setter %s is not supported yet."), setfunc.name());
        }

        private static final byte[] CTYPES_CFIELD_CAPSULE_NAME_PYMEM = PyCapsule.capsuleName("_ctypes/cfield.c pymem");

        private static PyCapsule createPyMemCapsule(Pointer pointer, PythonObjectFactory factory) {
            PyCapsule capsule = factory.createCapsuleJavaName(pointer, CTYPES_CFIELD_CAPSULE_NAME_PYMEM);
            new PointerReference(capsule, pointer, PythonContext.get(factory).getSharedFinalizer());
            return capsule;
        }
    }

    @ImportStatic(FieldGet.class)
    @GenerateUncached
    @SuppressWarnings("truffle-inlining")       // footprint reduction 100 -> 81
    protected abstract static class GetFuncNode extends Node {

        abstract Object execute(FieldGet getfunc, Pointer adr, int size);

        @Specialization(guards = "getfunc == vBOOL_get")
        static Object vBOOL_get(@SuppressWarnings("unused") FieldGet getfunc, Pointer ptr, @SuppressWarnings("unused") int size,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PointerNodes.ReadShortNode readShortNode) {
            // GET_BITFIELD(val, size);
            return readShortNode.execute(inliningTarget, ptr) != 0;
        }

        @Specialization(guards = "getfunc == bool_get")
        static boolean bool_get(@SuppressWarnings("unused") FieldGet getfunc, Pointer ptr, @SuppressWarnings("unused") int size,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PointerNodes.ReadByteNode readByteNode) {
            return readByteNode.execute(inliningTarget, ptr) != 0;
        }

        @Specialization(guards = "getfunc == b_get")
        static int b_get(@SuppressWarnings("unused") FieldGet getfunc, Pointer ptr, @SuppressWarnings("unused") int size,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PointerNodes.ReadByteNode readByteNode) {
            // GET_BITFIELD(val, size);
            return readByteNode.execute(inliningTarget, ptr);
        }

        @Specialization(guards = "getfunc == B_get")
        static int B_get(@SuppressWarnings("unused") FieldGet getfunc, Pointer ptr, @SuppressWarnings("unused") int size,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PointerNodes.ReadByteNode readByteNode) {
            // GET_BITFIELD(val, size);
            return readByteNode.execute(inliningTarget, ptr) & 0xFF;
        }

        @Specialization(guards = "getfunc == h_get")
        static int h_get(@SuppressWarnings("unused") FieldGet getfunc, Pointer ptr, @SuppressWarnings("unused") int size,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PointerNodes.ReadShortNode readShortNode) {
            // GET_BITFIELD(val, size);
            return readShortNode.execute(inliningTarget, ptr);
        }

        @Specialization(guards = "getfunc == h_get_sw")
        static int h_get_sw(@SuppressWarnings("unused") FieldGet getfunc, Pointer ptr, @SuppressWarnings("unused") int size,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PointerNodes.ReadShortNode readShortNode) {
            // GET_BITFIELD(val, size);
            return SWAP_2(readShortNode.execute(inliningTarget, ptr));
        }

        @Specialization(guards = "getfunc == H_get")
        static int H_get(@SuppressWarnings("unused") FieldGet getfunc, Pointer ptr, @SuppressWarnings("unused") int size,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PointerNodes.ReadShortNode readShortNode) {
            // GET_BITFIELD(val, size);
            return readShortNode.execute(inliningTarget, ptr) & 0xFFFF;
        }

        @Specialization(guards = "getfunc == H_get_sw")
        static int H_get_sw(@SuppressWarnings("unused") FieldGet getfunc, Pointer ptr, @SuppressWarnings("unused") int size,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PointerNodes.ReadShortNode readShortNode) {
            // GET_BITFIELD(val, size);
            return SWAP_2(readShortNode.execute(inliningTarget, ptr)) & 0xFFFF;
        }

        @Specialization(guards = "getfunc == i_get")
        static int i_get(@SuppressWarnings("unused") FieldGet getfunc, Pointer ptr, @SuppressWarnings("unused") int size,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PointerNodes.ReadIntNode readIntNode) {
            // GET_BITFIELD(val, size);
            return readIntNode.execute(inliningTarget, ptr);
        }

        @Specialization(guards = "getfunc == i_get_sw")
        static Object i_get_sw(@SuppressWarnings("unused") FieldGet getfunc, Pointer ptr, @SuppressWarnings("unused") int size,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PointerNodes.ReadIntNode readIntNode) {
            // GET_BITFIELD(val, size);
            return SWAP_4(readIntNode.execute(inliningTarget, ptr));
        }

        @Specialization(guards = "getfunc == I_get")
        static Object I_get(@SuppressWarnings("unused") FieldGet getfunc, Pointer ptr, @SuppressWarnings("unused") int size,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PointerNodes.ReadIntNode readIntNode) {
            // GET_BITFIELD(val, size);
            return readIntNode.execute(inliningTarget, ptr) & 0xFFFFFFFFL;
        }

        @Specialization(guards = "getfunc == I_get_sw")
        static Object I_get_sw(@SuppressWarnings("unused") FieldGet getfunc, Pointer ptr, @SuppressWarnings("unused") int size,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PointerNodes.ReadIntNode readIntNode) {
            // GET_BITFIELD(val, size);
            return SWAP_4(readIntNode.execute(inliningTarget, ptr)) & 0xFFFFFFFFL;
        }

        @Specialization(guards = "getfunc == l_get")
        static Object l_get(@SuppressWarnings("unused") FieldGet getfunc, Pointer ptr, @SuppressWarnings("unused") int size,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PointerNodes.ReadLongNode readLongNode) {
            // GET_BITFIELD(val, size);
            return readLongNode.execute(inliningTarget, ptr);
        }

        @Specialization(guards = "getfunc == l_get_sw")
        static Object l_get_sw(@SuppressWarnings("unused") FieldGet getfunc, Pointer ptr, @SuppressWarnings("unused") int size,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PointerNodes.ReadLongNode readLongNode) {
            // GET_BITFIELD(val, size);
            return SWAP_8(readLongNode.execute(inliningTarget, ptr));
        }

        @Specialization(guards = "getfunc == L_get")
        static Object L_get(@SuppressWarnings("unused") FieldGet getfunc, Pointer ptr, @SuppressWarnings("unused") int size,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PointerNodes.ReadLongNode readLongNode,
                        @Shared @Cached PythonObjectFactory factory) {
            long val = readLongNode.execute(inliningTarget, ptr);
            // GET_BITFIELD(val, size);
            return val < 0 ? factory.createInt(PInt.longToUnsignedBigInteger(val)) : val;
        }

        @Specialization(guards = "getfunc == L_get_sw")
        static Object L_get_sw(@SuppressWarnings("unused") FieldGet getfunc, Pointer ptr, @SuppressWarnings("unused") int size,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PointerNodes.ReadLongNode readLongNode,
                        @Shared @Cached PythonObjectFactory factory) {
            long val = SWAP_8(readLongNode.execute(inliningTarget, ptr));
            // GET_BITFIELD(val, size);
            return val < 0 ? factory.createInt(PInt.longToUnsignedBigInteger(val)) : val;
        }

        @Specialization(guards = "getfunc == d_get")
        static Object d_get(@SuppressWarnings("unused") FieldGet getfunc, Pointer ptr, @SuppressWarnings("unused") int size,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PointerNodes.ReadLongNode readLongNode) {
            return Double.longBitsToDouble(readLongNode.execute(inliningTarget, ptr));
        }

        @Specialization(guards = "getfunc == d_get_sw")
        static double d_get_sw(@SuppressWarnings("unused") FieldGet getfunc, Pointer ptr, @SuppressWarnings("unused") int size,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PointerNodes.ReadBytesNode readBytesNode) {
            byte[] bytes = readBytesNode.execute(inliningTarget, ptr, Double.BYTES);
            return ARRAY_ACCESSOR_SWAPPED.getDouble(bytes, 0);
        }

        @Specialization(guards = "getfunc == f_get")
        static double f_get(@SuppressWarnings("unused") FieldGet getfunc, Pointer ptr, @SuppressWarnings("unused") int size,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PointerNodes.ReadIntNode readIntNode) {
            return Float.intBitsToFloat(readIntNode.execute(inliningTarget, ptr));
        }

        @Specialization(guards = "getfunc == f_get_sw")
        static double f_get_sw(@SuppressWarnings("unused") FieldGet getfunc, Pointer ptr, @SuppressWarnings("unused") int size,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PointerNodes.ReadBytesNode readBytesNode) {
            byte[] bytes = readBytesNode.execute(inliningTarget, ptr, Float.BYTES);
            return ARRAY_ACCESSOR_SWAPPED.getFloat(bytes, 0);
        }

        @Specialization(guards = "getfunc == O_get")
        static Object O_get(@SuppressWarnings("unused") FieldGet getfunc, Pointer ptr, @SuppressWarnings("unused") int size,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached PointerNodes.ReadPointerNode readPointerNode,
                        @Cached PointerNodes.ReadPythonObject readPythonObject,
                        @Cached PRaiseNode raiseNode) {
            Pointer valuePtr = readPointerNode.execute(inliningTarget, ptr);
            if (valuePtr.isNull()) {
                throw raiseNode.raise(ValueError, ErrorMessages.PY_OBJ_IS_NULL);
            }
            return readPythonObject.execute(inliningTarget, valuePtr);
        }

        @Specialization(guards = "getfunc == c_get")
        static Object c_get(@SuppressWarnings("unused") FieldGet getfunc, Pointer ptr, @SuppressWarnings("unused") int size,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PointerNodes.ReadByteNode readByteNode,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createBytes(new byte[]{readByteNode.execute(inliningTarget, ptr)});
        }

        @Specialization(guards = "getfunc == u_get")
        static Object u_get(@SuppressWarnings("unused") FieldGet getfunc, Pointer ptr, @SuppressWarnings("unused") int size,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PointerNodes.ReadBytesNode readBytesNode,
                        @Shared @Cached TruffleString.FromByteArrayNode fromByteArrayNode,
                        @Shared @Cached TruffleString.SwitchEncodingNode switchEncodingNode) { // CTYPES_UNICODE
            byte[] bytes = readBytesNode.execute(inliningTarget, ptr, WCHAR_T_SIZE);
            return switchEncodingNode.execute(fromByteArrayNode.execute(bytes, WCHAR_T_ENCODING, false), TS_ENCODING);
        }

        /* U - a unicode string */
        @Specialization(guards = "getfunc == U_get")
        static Object U_get(@SuppressWarnings("unused") FieldGet getfunc, Pointer ptr, int size,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PointerNodes.WCsLenNode wCsLenNode,
                        @Shared @Cached PointerNodes.ReadBytesNode readBytesNode,
                        @Shared @Cached TruffleString.FromByteArrayNode fromByteArrayNode,
                        @Shared @Cached TruffleString.SwitchEncodingNode switchEncodingNode) { // CTYPES_UNICODE
            int wcslen = wCsLenNode.execute(inliningTarget, ptr, size / WCHAR_T_SIZE);
            byte[] bytes = readBytesNode.execute(inliningTarget, ptr, wcslen * WCHAR_T_SIZE);
            return switchEncodingNode.execute(fromByteArrayNode.execute(bytes, WCHAR_T_ENCODING, false), TS_ENCODING);
        }

        @Specialization(guards = "getfunc == s_get")
        static Object s_get(@SuppressWarnings("unused") FieldGet getfunc, Pointer ptr, int size,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PointerNodes.StrLenNode strLenNode,
                        @Shared @Cached PointerNodes.ReadBytesNode readBytesNode,
                        @Shared @Cached PythonObjectFactory factory) {
            return factory.createBytes(readBytesNode.execute(inliningTarget, ptr, strLenNode.execute(inliningTarget, ptr, size)));
        }

        @Specialization(guards = "getfunc == z_get")
        static Object z_get(@SuppressWarnings("unused") FieldGet getfunc, Pointer ptr, @SuppressWarnings("unused") int size,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PointerNodes.ReadPointerNode readPointerNode,
                        @Shared @Cached PythonObjectFactory factory,
                        @Shared @Cached PointerNodes.StrLenNode strLenNode,
                        @Shared @Cached PointerNodes.ReadBytesNode readBytesNode) {
            // ptr is a char**, we need to deref it to get char*
            Pointer valuePtr = readPointerNode.execute(inliningTarget, ptr);
            if (valuePtr.isNull()) {
                return PNone.NONE;
            }
            byte[] bytes = readBytesNode.execute(inliningTarget, valuePtr, strLenNode.execute(inliningTarget, valuePtr));
            return factory.createBytes(bytes);
        }

        @Specialization(guards = "getfunc == Z_get")
        static Object Z_get(@SuppressWarnings("unused") FieldGet getfunc, Pointer ptr, @SuppressWarnings("unused") int size,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PointerNodes.ReadPointerNode readPointerNode,
                        @Shared @Cached PointerNodes.WCsLenNode wCsLenNode,
                        @Shared @Cached PointerNodes.ReadBytesNode readBytesNode,
                        @Shared @Cached TruffleString.FromByteArrayNode fromByteArrayNode,
                        @Shared @Cached TruffleString.SwitchEncodingNode switchEncodingNode) {
            // ptr is a char**, we need to deref it to get char*
            Pointer valuePtr = readPointerNode.execute(inliningTarget, ptr);
            if (valuePtr.isNull()) {
                return PNone.NONE;
            }
            byte[] bytes = readBytesNode.execute(inliningTarget, valuePtr, wCsLenNode.execute(inliningTarget, valuePtr, size) * WCHAR_T_SIZE);
            return switchEncodingNode.execute(fromByteArrayNode.execute(bytes, WCHAR_T_ENCODING, false), TS_ENCODING);
        }

        @Specialization(guards = "getfunc == P_get")
        static Object P_get(@SuppressWarnings("unused") FieldGet getfunc, Pointer ptr, @SuppressWarnings("unused") int size,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached PointerNodes.ReadPointerNode readPointerNode,
                        @Cached PointerNodes.GetPointerValueAsObjectNode getPointerValueAsObjectNode,
                        @Shared @Cached PythonObjectFactory factory) {
            Pointer valuePtr = readPointerNode.execute(inliningTarget, ptr);
            if (valuePtr.isNull()) {
                return 0L;
            }
            Object p = getPointerValueAsObjectNode.execute(inliningTarget, valuePtr);
            if (p instanceof Long) {
                long val = (long) p;
                return val < 0 ? factory.createInt(PInt.longToUnsignedBigInteger(val)) : val;
            }
            return factory.createNativeVoidPtr(p);
        }
    }
}
