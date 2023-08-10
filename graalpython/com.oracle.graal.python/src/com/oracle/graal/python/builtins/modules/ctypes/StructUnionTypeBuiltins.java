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

import static com.oracle.graal.python.builtins.modules.ctypes.CtypesModuleBuiltins.TYPEFLAG_HASBITFIELD;
import static com.oracle.graal.python.builtins.modules.ctypes.CtypesModuleBuiltins.TYPEFLAG_HASPOINTER;
import static com.oracle.graal.python.builtins.modules.ctypes.CtypesModuleBuiltins.TYPEFLAG_HASUNION;
import static com.oracle.graal.python.builtins.modules.ctypes.CtypesModuleBuiltins.TYPEFLAG_ISPOINTER;
import static com.oracle.graal.python.builtins.modules.ctypes.CtypesModuleBuiltins._ctypes_alloc_format_string_with_shape;
import static com.oracle.graal.python.builtins.modules.ctypes.FFIType.FFI_TYPES.FFI_TYPE_STRUCT;
import static com.oracle.graal.python.builtins.modules.ctypes.PyCPointerTypeBuiltins.T_UPPER_B;
import static com.oracle.graal.python.builtins.modules.ctypes.PyCPointerTypeBuiltins.T_UPPER_T_LEFTBRACE;
import static com.oracle.graal.python.builtins.modules.ctypes.StgDictObject.DICTFLAG_FINAL;
import static com.oracle.graal.python.nodes.ErrorMessages.BIT_FIELDS_NOT_ALLOWED_FOR_TYPE_S;
import static com.oracle.graal.python.nodes.ErrorMessages.FIELDS_IS_FINAL;
import static com.oracle.graal.python.nodes.ErrorMessages.FIELDS_MUST_BE_A_SEQUENCE_OF_NAME_C_TYPE_PAIRS;
import static com.oracle.graal.python.nodes.ErrorMessages.FIELDS_MUST_BE_A_SEQUENCE_OF_PAIRS;
import static com.oracle.graal.python.nodes.ErrorMessages.NUMBER_OF_BITS_INVALID_FOR_BIT_FIELD;
import static com.oracle.graal.python.nodes.ErrorMessages.PACK_MUST_BE_A_NON_NEGATIVE_INTEGER;
import static com.oracle.graal.python.nodes.ErrorMessages.SECOND_ITEM_IN_FIELDS_TUPLE_INDEX_D_MUST_BE_A_C_TYPE;
import static com.oracle.graal.python.nodes.ErrorMessages.STRUCTURE_OR_UNION_CANNOT_CONTAIN_ITSELF;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NEW__;
import static com.oracle.graal.python.nodes.StringLiterals.T_RBRACE;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.AttributeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinConstructors.TypeNode;
import com.oracle.graal.python.builtins.modules.ctypes.CFieldBuiltins.PyCFieldFromDesc;
import com.oracle.graal.python.builtins.modules.ctypes.CtypesNodes.PyTypeCheck;
import com.oracle.graal.python.builtins.modules.ctypes.FFIType.FFI_TYPES;
import com.oracle.graal.python.builtins.modules.ctypes.FFIType.FieldDesc;
import com.oracle.graal.python.builtins.modules.ctypes.StgDictBuiltins.MakeAnonFieldsNode;
import com.oracle.graal.python.builtins.modules.ctypes.StgDictBuiltins.PyTypeStgDictNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageAddAllToOther;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetItem;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes.CheckIsSequenceNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GetInternalObjectArrayNode;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.str.StringUtils;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetBaseClassNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithRaise;
import com.oracle.graal.python.nodes.attributes.SetAttributeNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.GetDictIfExistsNode;
import com.oracle.graal.python.nodes.object.SetDictNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;

@CoreFunctions(extendClasses = {
                PythonBuiltinClassType.PyCStructType,
                PythonBuiltinClassType.UnionType
})
public final class StructUnionTypeBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return StructUnionTypeBuiltinsFactory.getFactories();
    }

    private static final int MAX_STRUCT_SIZE = 16;

    protected static final TruffleString T__ABSTRACT_ = tsLiteral("_abstract_");
    protected static final TruffleString T__FIELDS_ = tsLiteral("_fields_");
    protected static final TruffleString T__SWAPPEDBYTES_ = tsLiteral("_swappedbytes_");
    protected static final TruffleString T__USE_BROKEN_OLD_CTYPES_STRUCTURE_SEMANTICS_ = tsLiteral("_use_broken_old_ctypes_structure_semantics_");
    protected static final TruffleString T__PACK_ = tsLiteral("_pack_");

    @ImportStatic(StructUnionTypeBuiltins.class)
    @Builtin(name = J___NEW__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class StructUnionTypeNewNode extends PythonBuiltinNode {

        protected boolean isStruct() {
            return true;
        }

        @Specialization
        protected Object StructUnionTypeNew(VirtualFrame frame, Object type, Object[] args, PKeyword[] kwds,
                        @Bind("this") Node inliningTarget,
                        @Cached HashingStorageAddAllToOther addAllToOtherNode,
                        @Cached HashingStorageGetItem getItemResDict,
                        @Cached HashingStorageGetItem getItemStgDict,
                        @Cached TypeNode typeNew,
                        @Cached PyTypeStgDictNode pyTypeStgDictNode,
                        @Cached GetDictIfExistsNode getDict,
                        @Cached SetDictNode setDict,
                        @Cached GetBaseClassNode getBaseClassNode,
                        @Cached("create(T__FIELDS_)") SetAttributeNode setFieldsAttributeNode) {
            /*
             * create the new instance (which is a class, since we are a metatype!)
             */
            Object result = typeNew.execute(frame, type, args[0], args[1], args[2], kwds);

            PDict resDict = getDict.execute(result);
            if (resDict == null) {
                resDict = factory().createDictFixedStorage((PythonObject) result);
            }
            if (getItemResDict.hasKey(inliningTarget, resDict.getDictStorage(), T__ABSTRACT_)) {
                return result;
            }

            StgDictObject dict = factory().createStgDictObject(PythonBuiltinClassType.StgDict);
            if (!isStruct()) {
                dict.flags |= TYPEFLAG_HASUNION;
            }
            /*
             * replace the class dict by our updated stgdict, which holds info about storage
             * requirements of the instances
             */
            dict.setDictStorage(addAllToOtherNode.execute(frame, inliningTarget, resDict.getDictStorage(), dict.getDictStorage()));
            setDict.execute(inliningTarget, result, dict);
            dict.format = T_UPPER_B;

            dict.paramfunc = CArgObjectBuiltins.StructUnionTypeParamFunc;
            Object fieldsValue = getItemStgDict.execute(inliningTarget, dict.getDictStorage(), T__FIELDS_);
            if (fieldsValue != null) {
                setFieldsAttributeNode.execute(frame, result, fieldsValue);
            } else {
                StgDictObject basedict = pyTypeStgDictNode.execute(getBaseClassNode.execute(inliningTarget, result));
                if (basedict == null) {
                    return result;
                }

                /* copy base dict */
                StgDictObject.clone(dict, basedict);
                dict.flags &= ~DICTFLAG_FINAL; /* clear the 'final' flag in the subclass dict */
                basedict.flags |= DICTFLAG_FINAL; /* set the 'final' flag in the baseclass dict */
            }
            return result;
        }
    }

    @ImportStatic(StructUnionTypeBuiltins.class)
    protected abstract static class PyCStructUnionTypeUpdateStgDict extends PNodeWithRaise {
        abstract void execute(VirtualFrame frame, Object type, Object fields, boolean isStruct, PythonObjectFactory factory);

        /*
         * Retrieve the (optional) _pack_ attribute from a type, the _fields_ attribute, and create
         * an StgDictObject. Used for Structure and Union subclasses.
         */
        @SuppressWarnings("fallthrough")
        @Specialization
        void PyCStructUnionType_update_stgdict(VirtualFrame frame, Object type, Object fields, boolean isStruct, PythonObjectFactory factory,
                        @Bind("this") Node inliningTarget,
                        @Cached PyTypeCheck pyTypeCheck,
                        @Cached GetInternalObjectArrayNode getArray,
                        @Cached PyCFieldFromDesc cFieldFromDesc,
                        @Cached GetNameNode getNameNode,
                        @Cached CheckIsSequenceNode isSequenceNode,
                        @Cached PyObjectGetItem getItemNode,
                        @Cached PyObjectSizeNode sizeNode,
                        @Cached PyTypeStgDictNode pyTypeStgDictNode,
                        @Cached GetBaseClassNode getBaseClassNode,
                        @Cached MakeAnonFieldsNode makeAnonFieldsNode,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached SetAttributeNode.Dynamic setAttr,
                        @Cached IsBuiltinObjectProfile isBuiltinClassProfile,
                        @Cached PyObjectLookupAttr lookupSwappedbytes,
                        @Cached PyObjectLookupAttr lookupPack,
                        @Cached PyObjectLookupAttr lookupBrokenCtypes,
                        @Cached StringUtils.SimpleTruffleStringFormatNode formatNode,
                        @Cached CastToTruffleStringNode castToTruffleStringNode,
                        @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode) {
            /*
             * HACK Alert: I cannot be bothered to fix ctypes.com, so there has to be a way to use
             * the old, broken semantics: _fields_ are not extended but replaced in subclasses.
             *
             * XXX Remove this in ctypes 1.0!
             */
            boolean use_broken_old_ctypes_semantics;
            Object tmp = lookupSwappedbytes.execute(frame, inliningTarget, type, T__SWAPPEDBYTES_);
            boolean big_endian;
            // PY_BIG_ENDIAN;
            big_endian = tmp == PNone.NO_VALUE; // !PY_BIG_ENDIAN;

            tmp = lookupBrokenCtypes.execute(frame, inliningTarget, type, T__USE_BROKEN_OLD_CTYPES_STRUCTURE_SEMANTICS_);
            use_broken_old_ctypes_semantics = tmp != PNone.NO_VALUE;

            tmp = lookupPack.execute(frame, inliningTarget, type, T__PACK_);
            boolean isPacked = tmp != PNone.NO_VALUE;
            int pack = 0;
            if (tmp != PNone.NO_VALUE) {
                try {
                    pack = asSizeNode.executeLossy(frame, inliningTarget, tmp);
                } catch (PException e) {
                    e.expectTypeOrOverflowError(inliningTarget, isBuiltinClassProfile);
                    throw raise(ValueError, PACK_MUST_BE_A_NON_NEGATIVE_INTEGER);
                }
            }

            int len;
            try {
                isSequenceNode.execute(fields);
                len = sizeNode.execute(frame, inliningTarget, fields);
            } catch (PException e) {
                e.expectTypeError(inliningTarget, isBuiltinClassProfile);
                throw raise(TypeError, FIELDS_MUST_BE_A_SEQUENCE_OF_PAIRS);
            }

            StgDictObject stgdict = pyTypeStgDictNode.execute(type);
            /* If this structure/union is already marked final we cannot assign _fields_ anymore. */

            if ((stgdict.flags & DICTFLAG_FINAL) != 0) { /* is final ? */
                throw raise(AttributeError, FIELDS_IS_FINAL);
            }

            stgdict.format = null;

            stgdict.ffi_type_pointer = new FFIType();
            stgdict.ffi_type_pointer.elements = null;

            StgDictObject basedict = pyTypeStgDictNode.execute(getBaseClassNode.execute(inliningTarget, type));
            if (basedict != null) {
                stgdict.flags |= basedict.flags & (TYPEFLAG_HASUNION | TYPEFLAG_HASBITFIELD);
            }
            if (!isStruct) {
                stgdict.flags |= TYPEFLAG_HASUNION;
            }
            int offset, size, align;
            int union_size, total_align;
            int field_size = 0;
            int ffi_ofs;
            if (basedict != null && !use_broken_old_ctypes_semantics) {
                size = offset = basedict.size;
                align = basedict.align;
                union_size = 0;
                total_align = align != 0 ? align : 1;
                stgdict.ffi_type_pointer.type = FFI_TYPE_STRUCT;
                int ffielemLen = basedict.length + len;
                stgdict.ffi_type_pointer.elements = new FFIType[ffielemLen];
                if (basedict.ffi_type_pointer.elements != null && basedict.ffi_type_pointer.elements.length == ffielemLen) {
                    for (int idx = 0; idx < ffielemLen; idx++) {
                        stgdict.ffi_type_pointer.elements[idx] = new FFIType(basedict.ffi_type_pointer.elements[idx], null);
                    }
                }
                ffi_ofs = basedict.length;
            } else {
                offset = 0;
                size = 0;
                align = 0;
                union_size = 0;
                total_align = 1;
                stgdict.ffi_type_pointer.type = FFI_TYPE_STRUCT;
                stgdict.ffi_type_pointer.elements = new FFIType[len];
                /*-
                for (int idx = 0; idx < len + 1; idx++) {
                    stgdict.ffi_type_pointer.elements[idx] = new FFIType();
                }

                 */
                ffi_ofs = 0;
            }

            assert (stgdict.format == null);
            if (isStruct && !isPacked) {
                stgdict.format = T_UPPER_T_LEFTBRACE;
            } else {
                /*
                 * PEP3118 doesn't support union, or packed structures (well, only standard packing,
                 * but we don't support the pep for that). Use 'B' for bytes.
                 */
                stgdict.format = T_UPPER_B;
            }
            Object[] fieldsNames = new Object[len];
            int[] fieldsOffsets = new int[len];
            FFI_TYPES[] fieldsTypes = new FFI_TYPES[len];

            int bitofs = 0;
            boolean arrays_seen = false;
            for (int i = 0; i < len; i++) {
                Object pair = getItemNode.execute(frame, inliningTarget, fields, i);
                // !PyArg_ParseTuple(pair, "UO|i", & name, &desc, &bitsize)
                if (!PGuards.isPTuple(pair)) {
                    fieldsError();
                }
                Object[] tuple = getArray.execute(inliningTarget, ((PTuple) pair).getSequenceStorage());
                int tupleLen = tuple.length;
                if (tupleLen < 2 || !PGuards.isString(tuple[0]) || (tupleLen > 2 && !PGuards.isInteger(tuple[2]))) {
                    fieldsError();
                }
                Object name = tuple[0];
                Object desc = tuple[1];
                int bitsize = tupleLen >= 3 ? (int) tuple[2] : 0;

                if (pyTypeCheck.isPyCArrayTypeObject(desc)) {
                    arrays_seen = true;
                }
                StgDictObject dict = pyTypeStgDictNode.execute(desc);
                if (dict == null) {
                    throw raise(TypeError, SECOND_ITEM_IN_FIELDS_TUPLE_INDEX_D_MUST_BE_A_C_TYPE, i);
                }
                stgdict.ffi_type_pointer.elements[ffi_ofs + i] = dict.ffi_type_pointer;
                if ((dict.flags & (TYPEFLAG_ISPOINTER | TYPEFLAG_HASPOINTER)) != 0) {
                    stgdict.flags |= TYPEFLAG_HASPOINTER;
                }
                stgdict.flags |= dict.flags & (TYPEFLAG_HASUNION | TYPEFLAG_HASBITFIELD);
                dict.flags |= DICTFLAG_FINAL; /* mark field type final */
                if (tupleLen == 3) { /* bits specified */
                    stgdict.flags |= TYPEFLAG_HASBITFIELD;
                    switch (dict.ffi_type_pointer.type) {
                        case FFI_TYPE_UINT8:
                        case FFI_TYPE_UINT16:
                        case FFI_TYPE_UINT32:
                        case FFI_TYPE_SINT64:
                        case FFI_TYPE_UINT64:
                            break;

                        case FFI_TYPE_SINT8:
                        case FFI_TYPE_SINT16:
                        case FFI_TYPE_SINT32:
                            if (dict.getfunc != FieldDesc.c.getfunc && dict.getfunc != FieldDesc.u.getfunc) {
                                break;
                            }
                            /* else fall through */
                        default:
                            throw raise(TypeError, BIT_FIELDS_NOT_ALLOWED_FOR_TYPE_S, getNameNode.execute(inliningTarget, desc));
                    }
                    if (bitsize <= 0 || bitsize > dict.size * 8) {
                        throw raise(ValueError, NUMBER_OF_BITS_INVALID_FOR_BIT_FIELD);
                    }
                } else {
                    bitsize = 0;
                }

                if (isStruct && !isPacked) {
                    TruffleString fieldfmt = dict.format != null ? dict.format : T_UPPER_B;
                    TruffleString buf = formatNode.format("%s:%s:", fieldfmt, castToTruffleStringNode.execute(inliningTarget, name));

                    if (dict.shape != null) {
                        stgdict.format = _ctypes_alloc_format_string_with_shape(dict.ndim, dict.shape, stgdict.format, buf, appendStringNode, toStringNode, formatNode);
                    } else {
                        stgdict.format = StringUtils.cat(stgdict.format, buf);
                    }
                }

                CFieldObject prop;
                if (isStruct) {
                    int[] props = new int[]{field_size, bitofs, size, offset, align};
                    prop = cFieldFromDesc.execute(desc, i, bitsize, pack, big_endian, props, factory);
                    field_size = props[0];
                    bitofs = props[1];
                    size = props[2];
                    offset = props[3];
                    align = props[4];
                    fieldsNames[i] = name;
                    fieldsOffsets[i] = prop.index;
                    fieldsTypes[i] = dict.ffi_type_pointer.type;
                } else /* union */ {
                    size = 0;
                    offset = 0;
                    align = 0;
                    int[] props = new int[]{field_size, bitofs, size, offset, align};
                    prop = cFieldFromDesc.execute(desc, i, bitsize, pack, big_endian, props, factory);
                    field_size = props[0];
                    bitofs = props[1];
                    size = props[2];
                    offset = props[3];
                    align = props[4];
                    union_size = Math.max(size, union_size);
                }

                total_align = Math.max(align, total_align);

                setAttr.execute(frame, type, name, prop);
            }
            stgdict.fieldsNames = fieldsNames;
            stgdict.fieldsOffsets = fieldsOffsets;
            stgdict.fieldsTypes = fieldsTypes;

            if (isStruct && !isPacked) {
                stgdict.format = StringUtils.cat(stgdict.format, T_RBRACE);
            }

            if (!isStruct) {
                size = union_size;
            }

            /* Adjust the size according to the alignment requirements */
            size = ((size + total_align - 1) / total_align) * total_align;

            stgdict.ffi_type_pointer.alignment = total_align;
            stgdict.ffi_type_pointer.size = size;

            stgdict.size = size;
            stgdict.align = total_align;
            stgdict.length = len; /* ADD ffi_ofs? */

            if (arrays_seen && (size <= MAX_STRUCT_SIZE)) {
                /*
                 * See bpo-22273. Arrays are normally treated as pointers, which is fine when an
                 * array name is being passed as parameter, but not when passing structures by value
                 * that contain arrays. On 64-bit Linux, small structures passed by value are passed
                 * in registers, and in order to do this, libffi needs to know the true type of the
                 * array members of structs. Treating them as pointers breaks things.
                 *
                 * By small structures, we mean ones that are 16 bytes or less. In that case, there
                 * can't be more than 16 elements after unrolling arrays, as we (will) disallow
                 * bitfields. So we can collect the true ffi_type values in a fixed-size local array
                 * on the stack and, if any arrays were seen, replace the ffi_type_pointer.elements
                 * with a more accurate set, to allow libffi to marshal them into registers
                 * correctly. It means one more loop over the fields, but if we got here, the
                 * structure is small, so there aren't too many of those.
                 *
                 * Although the passing in registers is specific to 64-bit Linux, the
                 * array-in-struct vs. pointer problem is general. But we restrict the type
                 * transformation to small structs nonetheless.
                 *
                 * Note that although a union may be small in terms of memory usage, it could
                 * contain many overlapping declarations of arrays, e.g.
                 *
                 * union { unsigned int_8 foo [16]; unsigned uint_8 bar [16]; unsigned int_16
                 * baz[8]; unsigned uint_16 bozz[8]; unsigned int_32 fizz[4]; unsigned uint_32
                 * buzz[4]; }
                 *
                 * which is still only 16 bytes in size. We need to convert this into the following
                 * equivalent for libffi:
                 *
                 * union { struct { int_8 e1; int_8 e2; ... int_8 e_16; } f1; struct { uint_8 e1;
                 * uint_8 e2; ... uint_8 e_16; } f2; struct { int_16 e1; int_16 e2; ... int_16 e_8;
                 * } f3; struct { uint_16 e1; uint_16 e2; ... uint_16 e_8; } f4; struct { int_32 e1;
                 * int_32 e2; ... int_32 e_4; } f5; struct { uint_32 e1; uint_32 e2; ... uint_32
                 * e_4; } f6; }
                 *
                 * So the struct/union needs setting up as follows: all non-array elements copied
                 * across as is, and all array elements replaced with an equivalent struct which has
                 * as many fields as the array has elements, plus one NULL pointer.
                 */

                /*- (mq) XXX This is specific to cpython and not needed in our case
                // first pass to see how much memory to allocate
                for (int i = 0; i < len; ++i) {
                    Object pair = getItemNode.execute(frame, fields, i);
                    // PyArg_ParseTuple(pair, "UO|i", & name, &desc, &bitsize)
                    if (!PGuards.isPTuple(pair)) {
                        fieldsError();
                    }
                    Object[] tuple = getArray.execute(((PTuple) pair).getSequenceStorage());
                    int tupleLen = tuple.length;
                    if (tupleLen < 2 || !PGuards.isString(tuple[0]) || (tupleLen > 2 && !PGuards.isInteger(tuple[2]))) {
                        fieldsError();
                    }
                    Object desc = tuple[1];

                    StgDictObject dict = pyTypeStgDictNode.execute(desc);
                    if (dict == null) {
                        throw raise(TypeError, SECOND_ITEM_IN_FIELDS_TUPLE_INDEX_D_MUST_BE_A_C_TYPE, i);
                    }
                    if (pyTypeCheck.isPyCArrayTypeObject(desc)) {
                        // It's an array.
                        StgDictObject edict = pyTypeStgDictNode.execute(dict.proto);
                        if (edict == null) {
                            throw raise(TypeError, SECOND_ITEM_IN_FIELDS_TUPLE_INDEX_D_MUST_BE_A_C_TYPE, i);
                        }
                    }
                }
                */

                /*
                 * the first block takes up ffi_ofs + len + 1 which is the pointers * for this
                 * struct/union. The second block takes up num_ffi_type_pointers, so the sum of
                 * these is ffi_ofs + len + 1 + num_ffi_type_pointers as allocated above. The last
                 * bit is the num_ffi_types structs.
                 */
                /* of this struct/union */
                FFIType[] element_types = new FFIType[ffi_ofs + len /* + 1 */];
                if (ffi_ofs != 0 && (basedict != null)) {
                    for (int idx = 0; idx < ffi_ofs; idx++) {
                        element_types[idx] = new FFIType(basedict.ffi_type_pointer.elements[idx], null);
                    }
                }
                int element_index = ffi_ofs; /* index into element_types for this */

                /* second pass to actually set the type pointers */
                for (int i = 0; i < len; ++i) {
                    Object pair = getItemNode.execute(frame, inliningTarget, fields, i);
                    /*
                     * In theory, we made this call in the first pass, so it *shouldn't* fail.
                     * However, you never know, and the code above might change later - keeping the
                     * check in here is a tad defensive but it will affect program size only
                     * slightly and performance hardly at all.
                     */
                    // !PyArg_ParseTuple(pair, "UO|i", & name, &desc, &bitsize)
                    if (!PGuards.isPTuple(pair)) {
                        fieldsError();
                    }
                    Object[] tuple = getArray.execute(inliningTarget, ((PTuple) pair).getSequenceStorage());
                    int tupleLen = tuple.length;
                    if (tupleLen < 2 || !PGuards.isString(tuple[0]) || (tupleLen > 2 && !PGuards.isInteger(tuple[2]))) {
                        fieldsError();
                    }
                    Object desc = tuple[1];
                    StgDictObject dict = pyTypeStgDictNode.execute(desc);
                    /* Possibly this check could be avoided, but see above comment. */
                    if (dict == null) {
                        throw raise(TypeError, SECOND_ITEM_IN_FIELDS_TUPLE_INDEX_D_MUST_BE_A_C_TYPE, i);
                    }
                    assert (element_index < (ffi_ofs + len)); /* will be used below */
                    if (!pyTypeCheck.isPyCArrayTypeObject(desc)) {
                        /* Not an array. Just copy over the element ffi_type. */
                        element_types[element_index++] = dict.ffi_type_pointer;
                    } else {
                        int length = dict.length;
                        StgDictObject edict = pyTypeStgDictNode.execute(dict.proto);
                        if (edict == null) {
                            throw raise(TypeError, SECOND_ITEM_IN_FIELDS_TUPLE_INDEX_D_MUST_BE_A_C_TYPE, i);
                        }
                        FFIType ffiType = new FFIType(
                                        length * edict.ffi_type_pointer.size,
                                        edict.ffi_type_pointer.alignment,
                                        FFI_TYPE_STRUCT,
                                        new FFIType[length]);
                        element_types[element_index++] = ffiType;
                        /* Copy over the element's type, length times. */
                        for (int j = 0; j < length; j++) {
                            ffiType.elements[j] = edict.ffi_type_pointer;
                        }
                        // dummy_types[dummy_index++] = null;
                    }
                }

                // element_types[element_index] = null;
                /*
                 * Replace the old elements with the new, taking into account base class elements
                 * where necessary.
                 */
                assert stgdict.ffi_type_pointer.elements != null;
                stgdict.ffi_type_pointer.elements = element_types;
            }

            /*
             * We did check that this flag was NOT set above, it must not have been set until now.
             */
            if ((stgdict.flags & DICTFLAG_FINAL) != 0) {
                throw raise(AttributeError, STRUCTURE_OR_UNION_CANNOT_CONTAIN_ITSELF);
            }
            stgdict.flags |= DICTFLAG_FINAL;

            makeAnonFieldsNode.execute(frame, type, factory);
        }

        void fieldsError() {
            throw raise(TypeError, FIELDS_MUST_BE_A_SEQUENCE_OF_NAME_C_TYPE_PAIRS);
        }
    }
}
