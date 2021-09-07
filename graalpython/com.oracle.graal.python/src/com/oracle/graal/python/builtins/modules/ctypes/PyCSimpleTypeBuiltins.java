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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PyCSimpleType;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SimpleCData;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.StgDict;
import static com.oracle.graal.python.builtins.modules.ctypes.CDataTypeBuiltins._as_parameter_;
import static com.oracle.graal.python.builtins.modules.ctypes.CDataTypeBuiltins.from_param;
import static com.oracle.graal.python.builtins.modules.ctypes.CtypesModuleBuiltins.TYPEFLAG_ISPOINTER;
import static com.oracle.graal.python.nodes.ErrorMessages.A_TYPE_ATTRIBUTE_WHICH_MUST_BE_A_STRING_OF_LENGTH_1;
import static com.oracle.graal.python.nodes.ErrorMessages.CLASS_MUST_DEFINE_A_TYPE_ATTRIBUTE;
import static com.oracle.graal.python.nodes.ErrorMessages.CLASS_MUST_DEFINE_A_TYPE_STRING_ATTRIBUTE;
import static com.oracle.graal.python.nodes.ErrorMessages.TYPE_S_NOT_SUPPORTED;
import static com.oracle.graal.python.nodes.ErrorMessages.WHICH_MUST_BE_A_SINGLE_CHARACTER_STRING_CONTAINING_ONE_OF_S;
import static com.oracle.graal.python.nodes.ErrorMessages.WRONG_TYPE;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEW__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.AttributeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinConstructors.TypeNode;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.IsInstanceNode;
import com.oracle.graal.python.builtins.modules.ctypes.CFieldBuiltins.SetFuncNode;
import com.oracle.graal.python.builtins.modules.ctypes.FFIType.FieldDesc;
import com.oracle.graal.python.builtins.modules.ctypes.FFIType.FieldGet;
import com.oracle.graal.python.builtins.modules.ctypes.FFIType.FieldSet;
import com.oracle.graal.python.builtins.modules.ctypes.StgDictBuiltins.PyTypeStgDictNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject.LookupAttributeNode;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.str.StringNodes.InternStringNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetBaseClassNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.attributes.SetAttributeNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetDictIfExistsNode;
import com.oracle.graal.python.nodes.object.SetDictNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;

@CoreFunctions(extendClasses = PyCSimpleType)
public class PyCSimpleTypeBuiltins extends PythonBuiltins {

    protected static final String SIMPLE_TYPE_CHARS = "cbBhHiIlLdfuzZqQPXOv?g";

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PyCSimpleTypeBuiltinsFactory.getFactories();
    }

    @ImportStatic(PyCPointerTypeBuiltins.class)
    @Builtin(name = __NEW__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    protected abstract static class PyCSimpleTypeNewNode extends PythonBuiltinNode {

        protected boolean isStruct() {
            return true;
        }

        @Specialization
        Object PyCSimpleType_new(VirtualFrame frame, Object type, Object[] args, PKeyword[] kwds,
                        @Cached TypeNode typeNew,
                        @Cached InternStringNode internStringNode,
                        @Cached GetDictIfExistsNode getDict,
                        @Cached SetDictNode setDict,
                        @CachedLibrary(limit = "1") HashingStorageLibrary hlib,
                        @Cached("create(_type_)") LookupAttributeInMRONode lookupAttrId,
                        @Cached GetBaseClassNode getBaseClassNode,
                        @Cached CastToJavaStringNode toJavaStringNode,
                        @Cached SetAttributeNode.Dynamic setAttrString,
                        @Cached PyTypeStgDictNode pyTypeStgDictNode) {
            /*
             * create the new instance (which is a class, since we are a metatype!)
             */
            Object result = typeNew.execute(frame, type, args[0], args[1], args[2], kwds);

            Object proto = lookupAttrId.execute(result);
            if (proto == PNone.NO_VALUE) {
                throw raise(AttributeError, CLASS_MUST_DEFINE_A_TYPE_ATTRIBUTE);
            }
            String proto_str;
            int proto_len;
            if (PGuards.isString(proto)) {
                proto_str = toJavaStringNode.execute(proto);
                proto_len = PString.length(proto_str);
            } else {
                throw raise(TypeError, CLASS_MUST_DEFINE_A_TYPE_STRING_ATTRIBUTE);
            }
            if (proto_len != 1) {
                throw raise(ValueError, A_TYPE_ATTRIBUTE_WHICH_MUST_BE_A_STRING_OF_LENGTH_1);
            }
            if (PString.indexOf(SIMPLE_TYPE_CHARS, proto_str, 0) == -1) {
                throw raise(AttributeError, WHICH_MUST_BE_A_SINGLE_CHARACTER_STRING_CONTAINING_ONE_OF_S, SIMPLE_TYPE_CHARS);
            }
            FieldDesc fmt = FFIType._ctypes_get_fielddesc(PString.charAt(proto_str, 0));
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
            char code = PString.charAt(proto_str, 0);
            stgdict.format = ctypesAllocFormatStringForType(code, false);

            stgdict.paramfunc = CArgObjectBuiltins.PyCSimpleTypeParamFunc;

            /* This consumes the refcount on proto which we have */
            stgdict.proto = proto_str;

            /* replace the class dict by our updated spam dict */
            PDict resDict = getDict.execute(result);
            if (resDict == null) {
                resDict = factory().createDictFixedStorage((PythonObject) result);
            }
            stgdict.setDictStorage(hlib.addAllToOther(resDict.getDictStorage(), stgdict.getDictStorage()));
            setDict.execute((PythonObject) result, stgdict);

            /*
             * Install from_param class methods in ctypes base classes. Overrides the
             * PyCSimpleType_from_param generic method.
             */
            if (getBaseClassNode.execute(result) == getCore().lookupType(SimpleCData)) {
                switch (proto_str) {
                    case "z": /* c_char_p */
                        LazyPyCSimpleTypeBuiltins.addCCharPFromParam(getLanguage(), result);
                        stgdict.flags |= TYPEFLAG_ISPOINTER;
                        break;
                    case "Z": /* c_wchar_p */
                        LazyPyCSimpleTypeBuiltins.addCWCharPFromParam(getLanguage(), result);
                        stgdict.flags |= TYPEFLAG_ISPOINTER;
                        break;
                    case "P": /* c_void_p */
                        LazyPyCSimpleTypeBuiltins.addCVoidPFromParam(getLanguage(), result);
                        stgdict.flags |= TYPEFLAG_ISPOINTER;
                        break;
                    case "s":
                    case "X":
                    case "O":
                        stgdict.flags |= TYPEFLAG_ISPOINTER;
                        break;
                    default:
                        break;
                }
            }

            if (type == PyCSimpleType && fmt.setfunc_swapped != FieldSet.nil && fmt.getfunc_swapped != FieldGet.nil) {
                Object swapped = CreateSwappedType(frame, type, args, kwds, proto, fmt,
                                typeNew,
                                internStringNode,
                                toJavaStringNode,
                                getDict,
                                setDict,
                                hlib,
                                factory());
                StgDictObject sw_dict = pyTypeStgDictNode.execute(swapped);
                setAttrString.execute(frame, result, "__ctype_be__", swapped);
                setAttrString.execute(frame, result, "__ctype_le__", result);
                setAttrString.execute(frame, swapped, "__ctype_le__", result);
                setAttrString.execute(frame, swapped, "__ctype_be__", swapped);
                /* We are creating the type for the OTHER endian */
                sw_dict.format = PString.cat(">", PString.charAt(stgdict.format, 1));
            }

            return result;
        }
    }

    static Object CreateSwappedType(VirtualFrame frame, Object type, Object[] args, PKeyword[] kwds, Object proto, FieldDesc fmt,
                    TypeNode typeNew,
                    InternStringNode internStringNode,
                    CastToJavaStringNode toString,
                    GetDictIfExistsNode getDict,
                    SetDictNode setDict,
                    HashingStorageLibrary hlib,
                    PythonObjectFactory factory) {
        int argsLen = args.length;
        Object[] swapped_args = new Object[argsLen];
        String suffix = toString.execute(internStringNode.execute("_be"));
        String name = toString.execute(args[0]);
        Object newname = PString.cat(name, suffix);

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
        stgdict.setDictStorage(hlib.addAllToOther(resDict.getDictStorage(), stgdict.getDictStorage()));
        setDict.execute((PythonObject) result, stgdict);

        return result;
    }

    /*
     * This is a *class method*. Convert a parameter into something that ConvParam can handle.
     */
    @ImportStatic(CDataTypeBuiltins.class)
    @Builtin(name = from_param, minNumOfPositionalArgs = 2, declaresExplicitSelf = true)
    @GenerateNodeFactory
    protected abstract static class FromParamNode extends PythonBinaryBuiltinNode {

        @Specialization
        Object PyCSimpleType_from_param(VirtualFrame frame, Object type, Object value,
                        @Cached SetFuncNode setFuncNode,
                        @Cached IsInstanceNode isInstanceNode,
                        @Cached PyTypeStgDictNode pyTypeStgDictNode,
                        @Cached LookupAttributeNode lookupAsParam) {
            /*
             * If the value is already an instance of the requested type, we can use it as is
             */
            if (isInstanceNode.executeWith(frame, value, type)) {
                return value;
            }

            StgDictObject dict = pyTypeStgDictNode.checkAbstractClass(type, getRaiseNode());

            /* I think we can rely on this being a one-character string */
            String fmt = (String) dict.proto;
            assert fmt != null;

            FieldDesc fd = FFIType._ctypes_get_fielddesc(PString.charAt(fmt, 0));
            assert fd != null;

            PyCArgObject parg = factory().createCArgObject();
            parg.tag = PString.charAt(fmt, 0);
            parg.pffi_type = fd.pffi_type;
            parg.value.createStorage(parg.pffi_type, value);
            try {
                parg.obj = setFuncNode.execute(frame, fd.setfunc, parg.value, value, 0);
                return parg;
            } catch (PException e) {
                // pass through to check for _as_parameter_
            }

            Object as_parameter = lookupAsParam.execute(frame, value, _as_parameter_, false);
            if (as_parameter != null) {
                // Py_EnterRecursiveCall("while processing _as_parameter_"); TODO
                Object r = PyCSimpleType_from_param(frame, type, as_parameter, setFuncNode, isInstanceNode, pyTypeStgDictNode, lookupAsParam);
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
    static String ctypesAllocFormatStringForType(char code, boolean big_endian) {
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
        return PString.cat(big_endian ? '>' : '<', pep_code);
    }
}
