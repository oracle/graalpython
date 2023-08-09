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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PyCSimpleType;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SimpleCData;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.StgDict;
import static com.oracle.graal.python.builtins.modules.ctypes.CDataTypeBuiltins.J_FROM_PARAM;
import static com.oracle.graal.python.builtins.modules.ctypes.CDataTypeBuiltins.T__AS_PARAMETER_;
import static com.oracle.graal.python.builtins.modules.ctypes.CtypesModuleBuiltins.TYPEFLAG_ISPOINTER;
import static com.oracle.graal.python.builtins.modules.ctypes.PyCPointerTypeBuiltins.T__TYPE_;
import static com.oracle.graal.python.nodes.ErrorMessages.A_TYPE_ATTRIBUTE_WHICH_MUST_BE_A_STRING_OF_LENGTH_1;
import static com.oracle.graal.python.nodes.ErrorMessages.CLASS_MUST_DEFINE_A_TYPE_ATTRIBUTE;
import static com.oracle.graal.python.nodes.ErrorMessages.CLASS_MUST_DEFINE_A_TYPE_STRING_ATTRIBUTE;
import static com.oracle.graal.python.nodes.ErrorMessages.TYPE_S_NOT_SUPPORTED;
import static com.oracle.graal.python.nodes.ErrorMessages.WHICH_MUST_BE_A_SINGLE_CHARACTER_STRING_CONTAINING_ONE_OF_S;
import static com.oracle.graal.python.nodes.ErrorMessages.WRONG_TYPE;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NEW__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.AttributeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinConstructors.TypeNode;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.IsInstanceNode;
import com.oracle.graal.python.builtins.modules.ctypes.CFieldBuiltins.SetFuncNode;
import com.oracle.graal.python.builtins.modules.ctypes.FFIType.FieldDesc;
import com.oracle.graal.python.builtins.modules.ctypes.FFIType.FieldGet;
import com.oracle.graal.python.builtins.modules.ctypes.FFIType.FieldSet;
import com.oracle.graal.python.builtins.modules.ctypes.StgDictBuiltins.PyTypeStgDictNode;
import com.oracle.graal.python.builtins.modules.ctypes.memory.Pointer;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageAddAllToOther;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.str.StringNodes.InternStringNode;
import com.oracle.graal.python.builtins.objects.str.StringUtils;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetBaseClassNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.attributes.SetAttributeNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetDictIfExistsNode;
import com.oracle.graal.python.nodes.object.SetDictNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.object.PythonObjectSlowPathFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PyCSimpleType)
public final class PyCSimpleTypeBuiltins extends PythonBuiltins {

    private static final TruffleString T_SIMPLE_TYPE_CHARS = tsLiteral("cbBhHiIlLdfuzZqQPXOv?g");
    private static final int SIMPLE_TYPE_CHARS_LENGTH = T_SIMPLE_TYPE_CHARS.codePointLengthUncached(TS_ENCODING);
    protected static final TruffleString T_UPPER_Z = tsLiteral("Z");
    protected static final TruffleString T_LOWER_Z = tsLiteral("z");
    protected static final TruffleString T_UPPER_P = tsLiteral("P");
    protected static final TruffleString T_LOWER_S = tsLiteral("s");
    protected static final TruffleString T_UPPER_X = tsLiteral("X");
    protected static final TruffleString T_UPPER_O = tsLiteral("O");
    private static final TruffleString T__BE = tsLiteral("_be");

    protected static final TruffleString T_CTYPE_BE = tsLiteral("__ctype_be__");
    protected static final TruffleString T_CTYPE_LE = tsLiteral("__ctype_le__");

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PyCSimpleTypeBuiltinsFactory.getFactories();
    }

    @ImportStatic(PyCPointerTypeBuiltins.class)
    @Builtin(name = J___NEW__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    protected abstract static class PyCSimpleTypeNewNode extends PythonBuiltinNode {

        @Specialization
        Object PyCSimpleType_new(VirtualFrame frame, Object type, Object[] args, PKeyword[] kwds,
                        @Bind("this") Node inliningTarget,
                        @Cached TypeNode typeNew,
                        @Cached InternStringNode internStringNode,
                        @Cached GetDictIfExistsNode getDict,
                        @Cached SetDictNode setDict,
                        @Cached HashingStorageAddAllToOther addAllToOtherNode,
                        @Cached PyObjectLookupAttr lookupAttrType,
                        @Cached GetBaseClassNode getBaseClassNode,
                        @Cached CastToTruffleStringNode toTruffleStringNode,
                        @Cached SetAttributeNode.Dynamic setAttrString,
                        @Cached PyTypeStgDictNode pyTypeStgDictNode,
                        @Cached TruffleString.IndexOfStringNode indexOfStringNode,
                        @Cached TruffleString.CodePointAtIndexNode codePointAtIndexNode,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached TruffleString.SwitchEncodingNode switchEncodingNode,
                        @Cached TruffleString.EqualNode eqNode,
                        @Cached TruffleString.FromCharArrayUTF16Node fromCharArrayNode) {

            /*
             * create the new instance (which is a class, since we are a metatype!)
             */
            Object result = typeNew.execute(frame, type, args[0], args[1], args[2], kwds);

            Object proto = lookupAttrType.execute(frame, inliningTarget, result, T__TYPE_);
            if (proto == PNone.NO_VALUE) {
                throw raise(AttributeError, CLASS_MUST_DEFINE_A_TYPE_ATTRIBUTE);
            }
            TruffleString proto_str;
            int proto_len;
            if (PGuards.isString(proto)) {
                proto_str = toTruffleStringNode.execute(inliningTarget, proto);
                proto_len = codePointLengthNode.execute(proto_str, TS_ENCODING);
            } else {
                throw raise(TypeError, CLASS_MUST_DEFINE_A_TYPE_STRING_ATTRIBUTE);
            }
            if (proto_len != 1) {
                throw raise(ValueError, A_TYPE_ATTRIBUTE_WHICH_MUST_BE_A_STRING_OF_LENGTH_1);
            }
            if (indexOfStringNode.execute(T_SIMPLE_TYPE_CHARS, proto_str, 0, SIMPLE_TYPE_CHARS_LENGTH, TS_ENCODING) < 0) {
                throw raise(AttributeError, WHICH_MUST_BE_A_SINGLE_CHARACTER_STRING_CONTAINING_ONE_OF_S, T_SIMPLE_TYPE_CHARS);
            }

            char code = (char) codePointAtIndexNode.execute(proto_str, 0, TS_ENCODING);
            FieldDesc fmt = FFIType._ctypes_get_fielddesc(code);
            if (fmt == null) {
                throw raise(ValueError, TYPE_S_NOT_SUPPORTED, proto_str);
            }

            StgDictObject stgdict = factory().createStgDictObject(StgDict);

            stgdict.ffi_type_pointer = fmt.pffi_type;
            stgdict.align = fmt.pffi_type.alignment;
            stgdict.length = 0;
            stgdict.size = fmt.pffi_type.size;
            stgdict.setfunc = fmt.setfunc;
            stgdict.getfunc = fmt.getfunc;
            stgdict.format = ctypesAllocFormatStringForType(code, false, fromCharArrayNode, switchEncodingNode);

            stgdict.paramfunc = CArgObjectBuiltins.PyCSimpleTypeParamFunc;

            /* This consumes the refcount on proto which we have */
            stgdict.proto = proto_str;

            /* replace the class dict by our updated spam dict */
            PDict resDict = getDict.execute(result);
            if (resDict == null) {
                resDict = factory().createDictFixedStorage((PythonObject) result);
            }
            addAllToOtherNode.execute(frame, inliningTarget, resDict.getDictStorage(), stgdict);
            setDict.execute(inliningTarget, (PythonObject) result, stgdict);

            /*
             * Install from_param class methods in ctypes base classes. Overrides the
             * PyCSimpleType_from_param generic method.
             */
            Python3Core core = getContext();
            PythonObjectSlowPathFactory factory = core.factory();
            if (getBaseClassNode.execute(inliningTarget, result) == core.lookupType(SimpleCData)) {
                if (eqNode.execute(T_LOWER_Z, proto_str, TS_ENCODING)) { /* c_char_p */
                    LazyPyCSimpleTypeBuiltins.addCCharPFromParam(factory, getLanguage(), result);
                    stgdict.flags |= TYPEFLAG_ISPOINTER;
                } else if (eqNode.execute(T_UPPER_Z, proto_str, TS_ENCODING)) { /* c_wchar_p */
                    LazyPyCSimpleTypeBuiltins.addCWCharPFromParam(factory, getLanguage(), result);
                    stgdict.flags |= TYPEFLAG_ISPOINTER;

                } else if (eqNode.execute(T_UPPER_P, proto_str, TS_ENCODING)) { /* c_void_p */
                    LazyPyCSimpleTypeBuiltins.addCVoidPFromParam(factory, getLanguage(), result);
                    stgdict.flags |= TYPEFLAG_ISPOINTER;
                } else if (eqNode.execute(T_LOWER_S, proto_str, TS_ENCODING) ||
                                eqNode.execute(T_UPPER_X, proto_str, TS_ENCODING) ||
                                eqNode.execute(T_UPPER_O, proto_str, TS_ENCODING)) {
                    stgdict.flags |= TYPEFLAG_ISPOINTER;
                }
            }

            if (type == PyCSimpleType && fmt.setfunc_swapped != FieldSet.nil && fmt.getfunc_swapped != FieldGet.nil) {
                Object swapped = CreateSwappedType(frame, inliningTarget, type, args, kwds, proto, fmt,
                                typeNew,
                                internStringNode,
                                toTruffleStringNode,
                                getDict,
                                setDict,
                                addAllToOtherNode,
                                factory());
                StgDictObject sw_dict = pyTypeStgDictNode.execute(swapped);
                setAttrString.execute(frame, result, T_CTYPE_BE, swapped);
                setAttrString.execute(frame, result, T_CTYPE_LE, result);
                setAttrString.execute(frame, swapped, T_CTYPE_LE, result);
                setAttrString.execute(frame, swapped, T_CTYPE_BE, swapped);
                /* We are creating the type for the OTHER endian */
                sw_dict.format = switchEncodingNode.execute(fromCharArrayNode.execute(new char[]{'>', (char) codePointAtIndexNode.execute(stgdict.format, 1, TS_ENCODING)}), TS_ENCODING);
            }

            return result;
        }
    }

    private static Object CreateSwappedType(VirtualFrame frame, Node inliningTarget, Object type, Object[] args, PKeyword[] kwds, Object proto, FieldDesc fmt,
                    TypeNode typeNew,
                    InternStringNode internStringNode,
                    CastToTruffleStringNode toString,
                    GetDictIfExistsNode getDict,
                    SetDictNode setDict,
                    HashingStorageAddAllToOther addAllToOther,
                    PythonObjectFactory factory) {
        int argsLen = args.length;
        Object[] swapped_args = new Object[argsLen];
        TruffleString suffix = toString.execute(inliningTarget, internStringNode.execute(inliningTarget, T__BE));
        TruffleString name = toString.execute(inliningTarget, args[0]);
        TruffleString newname = StringUtils.cat(name, suffix);

        swapped_args[0] = newname;
        PythonUtils.arraycopy(args, 1, swapped_args, 1, argsLen - 1);

        /*
         * create the new instance (which is a class, since we are a metatype!)
         */
        Object result = typeNew.execute(frame, type, swapped_args[0], swapped_args[1], swapped_args[2], kwds);
        StgDictObject stgdict = factory.createStgDictObject(StgDict);
        stgdict.ffi_type_pointer = fmt.pffi_type;
        stgdict.align = fmt.pffi_type.alignment;
        stgdict.length = 0;
        stgdict.size = fmt.pffi_type.size;
        stgdict.setfunc = fmt.setfunc_swapped;
        stgdict.getfunc = fmt.getfunc_swapped;

        stgdict.proto = proto;

        /* replace the class dict by our updated spam dict */
        PDict resDict = getDict.execute(result);
        if (resDict == null) {
            resDict = factory.createDictFixedStorage((PythonObject) result);
        }
        addAllToOther.execute(frame, inliningTarget, resDict.getDictStorage(), stgdict);
        setDict.execute(inliningTarget, (PythonObject) result, stgdict);

        return result;
    }

    /*
     * This is a *class method*. Convert a parameter into something that ConvParam can handle.
     */
    @ImportStatic(CDataTypeBuiltins.class)
    @Builtin(name = J_FROM_PARAM, minNumOfPositionalArgs = 2, declaresExplicitSelf = true)
    @GenerateNodeFactory
    protected abstract static class FromParamNode extends PythonBinaryBuiltinNode {

        @Specialization
        Object PyCSimpleType_from_param(VirtualFrame frame, Object type, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached SetFuncNode setFuncNode,
                        @Cached IsInstanceNode isInstanceNode,
                        @Cached PyTypeStgDictNode pyTypeStgDictNode,
                        @Cached PyObjectLookupAttr lookupAsParam,
                        @Cached TruffleString.CodePointAtIndexNode codePointAtIndexNode) {
            /*
             * If the value is already an instance of the requested type, we can use it as is
             */
            if (isInstanceNode.executeWith(frame, value, type)) {
                return value;
            }

            StgDictObject dict = pyTypeStgDictNode.checkAbstractClass(type, getRaiseNode());

            /* I think we can rely on this being a one-character string */
            TruffleString fmt = (TruffleString) dict.proto;
            assert fmt != null;

            char code = (char) codePointAtIndexNode.execute(fmt, 0, TS_ENCODING);
            FieldDesc fd = FFIType._ctypes_get_fielddesc(code);
            assert fd != null;

            PyCArgObject parg = factory().createCArgObject();
            parg.tag = code;
            parg.pffi_type = fd.pffi_type;
            parg.valuePointer = Pointer.allocate(parg.pffi_type, dict.size);
            try {
                parg.obj = setFuncNode.execute(frame, fd.setfunc, parg.valuePointer, value, 0);
                return parg;
            } catch (PException e) {
                // pass through to check for _as_parameter_
            }

            Object as_parameter = lookupAsParam.execute(frame, inliningTarget, value, T__AS_PARAMETER_);
            if (as_parameter != PNone.NO_VALUE) {
                // Py_EnterRecursiveCall("while processing _as_parameter_"); TODO
                Object r = PyCSimpleType_from_param(frame, type, as_parameter, inliningTarget, setFuncNode, isInstanceNode, pyTypeStgDictNode, lookupAsParam, codePointAtIndexNode);
                // Py_LeaveRecursiveCall();
                return r;
            }
            throw raise(TypeError, WRONG_TYPE);
        }
    }

    /*
     * Allocate a memory block for a pep3118 format string, filled with a suitable PEP 3118 type
     * code corresponding to the given ctypes type. Returns NULL on failure, with the error
     * indicator set.
     *
     * This produces type codes in the standard size mode (cf. struct module), since the endianness
     * may need to be swapped to a non-native one later on.
     */
    // corresponds to _ctypes_alloc_format_string_for_type
    static TruffleString ctypesAllocFormatStringForType(char code, boolean big_endian, TruffleString.FromCharArrayUTF16Node fromCharArrayNode, TruffleString.SwitchEncodingNode switchEncodingNode) {
        /* The standard-size code is the same as the ctypes one */
        char pep_code = code;

        switch (code) {
            // #if SIZEOF_INT == 2
            // case 'i': pep_code = 'h'; break;
            // case 'I': pep_code = 'H'; break;
            // #elif SIZEOF_INT == 4
            case 'i':
                pep_code = 'i';
                break;
            case 'I':
                pep_code = 'I';
                break;
            // #elif SIZEOF_INT == 8
            // case 'i': pep_code = 'q'; break;
            // case 'I': pep_code = 'Q'; break;
            // #if SIZEOF_LONG == 4
            // case 'l': pep_code = 'l'; break;
            // case 'L': pep_code = 'L'; break;
            // #elif SIZEOF_LONG == 8
            case 'l':
                pep_code = 'q';
                break;
            case 'L':
                pep_code = 'Q';
                break;
            // #if SIZEOF__BOOL == 1
            case '?':
                pep_code = '?';
                break;
            // #elif SIZEOF__BOOL == 2
            // case '?': pep_code = 'H'; break;
            // #elif SIZEOF__BOOL == 4
            // case '?': pep_code = 'L'; break;
            // #elif SIZEOF__BOOL == 8
            // case '?': pep_code = 'Q'; break;
        }
        return switchEncodingNode.execute(fromCharArrayNode.execute(new char[]{big_endian ? '>' : '<', pep_code}), TS_ENCODING);
    }
}
