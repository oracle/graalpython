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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.modules.ctypes.CArgObjectBuiltins.PyCPointerTypeParamFunc;
import static com.oracle.graal.python.builtins.modules.ctypes.CDataTypeBuiltins.J_FROM_PARAM;
import static com.oracle.graal.python.builtins.modules.ctypes.CtypesModuleBuiltins.TYPEFLAG_ISPOINTER;
import static com.oracle.graal.python.builtins.modules.ctypes.CtypesModuleBuiltins._ctypes_alloc_format_string_with_shape;
import static com.oracle.graal.python.builtins.modules.ctypes.FFIType.ffi_type_pointer;
import static com.oracle.graal.python.nodes.ErrorMessages.EXPECTED_CDATA_INSTANCE;
import static com.oracle.graal.python.nodes.ErrorMessages.TYPE_MUST_BE_A_TYPE;
import static com.oracle.graal.python.nodes.ErrorMessages.TYPE_MUST_HAVE_STORAGE_INFO;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NEW__;
import static com.oracle.graal.python.nodes.StringLiterals.T_AMPERSAND;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinConstructors.TypeNode;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.IsInstanceNode;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.IsSubClassNode;
import com.oracle.graal.python.builtins.modules.ctypes.CDataTypeBuiltins.CDataTypeFromParamNode;
import com.oracle.graal.python.builtins.modules.ctypes.CtypesNodes.PyTypeCheck;
import com.oracle.graal.python.builtins.modules.ctypes.FFIType.FieldDesc;
import com.oracle.graal.python.builtins.modules.ctypes.StgDictBuiltins.PyObjectStgDictNode;
import com.oracle.graal.python.builtins.modules.ctypes.StgDictBuiltins.PyTypeStgDictNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageAddAllToOther;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetItem;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageSetItem;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.str.StringUtils;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsTypeNode;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetDictIfExistsNode;
import com.oracle.graal.python.nodes.object.SetDictNode;
import com.oracle.graal.python.nodes.util.CastToJavaBooleanNode;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
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

@CoreFunctions(extendClasses = PythonBuiltinClassType.PyCPointerType)
public final class PyCPointerTypeBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PyCPointerTypeBuiltinsFactory.getFactories();
    }

    protected static final TruffleString T__TYPE_ = tsLiteral("_type_");
    protected static final String J_SET_TYPE = "set_type";

    protected static final TruffleString T_UPPER_B = tsLiteral("B");
    protected static final TruffleString T_UPPER_T_LEFTBRACE = tsLiteral("T{");

    @ImportStatic(PyCPointerTypeBuiltins.class)
    @Builtin(name = J___NEW__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    protected abstract static class PyCPointerTypeNewNode extends PythonBuiltinNode {

        @Specialization
        protected Object PyCPointerType_new(VirtualFrame frame, Object type, Object[] args, PKeyword[] kwds,
                        @Bind("this") Node inliningTarget,
                        @Cached HashingStorageGetItem getItem,
                        @Cached HashingStorageAddAllToOther addAllToOtherNode,
                        @Cached GetDictIfExistsNode getDict,
                        @Cached SetDictNode setDict,
                        @Cached IsTypeNode isTypeNode,
                        @Cached TypeNode newType,
                        @Cached PyTypeStgDictNode pyTypeStgDictNode,
                        @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode,
                        @Cached StringUtils.SimpleTruffleStringFormatNode formatNode) {
            /*
             * stgdict items size, align, length contain info about pointers itself, stgdict.proto
             * has info about the pointed to type!
             */
            StgDictObject stgdict = factory().createStgDictObject(PythonBuiltinClassType.StgDict);
            stgdict.size = StgDictObject.VOID_PTR_SIZE;
            stgdict.align = FieldDesc.P.pffi_type.alignment;
            stgdict.length = 1;
            stgdict.ffi_type_pointer = ffi_type_pointer;
            stgdict.paramfunc = PyCPointerTypeParamFunc;
            stgdict.flags |= TYPEFLAG_ISPOINTER;

            PDict typedict = (PDict) args[2];
            // Borrowed ref:
            Object proto = getItem.execute(inliningTarget, typedict.getDictStorage(), T__TYPE_);
            if (proto != null) {
                PyCPointerType_SetProto(inliningTarget, stgdict, proto, isTypeNode, pyTypeStgDictNode, getRaiseNode());
                StgDictObject itemdict = pyTypeStgDictNode.execute(proto);
                /* PyCPointerType_SetProto has verified proto has a stgdict. */
                /*
                 * If itemdict.format is NULL, then this is a pointer to an incomplete type. We
                 * create a generic format string 'pointer to bytes' in this case.
                 */
                TruffleString current_format = itemdict.format != null ? itemdict.format : T_UPPER_B;
                if (itemdict.shape != null) {
                    /* pointer to an array: the shape needs to be prefixed */
                    stgdict.format = _ctypes_alloc_format_string_with_shape(itemdict.ndim, itemdict.shape, T_AMPERSAND, current_format, appendStringNode, toStringNode, formatNode);
                } else {
                    stgdict.format = StringUtils.cat(T_AMPERSAND, current_format);
                }
            }

            /* create the new instance (which is a class, since we are a metatype!) */
            Object /* type */ result = newType.execute(frame, type, args[0], args[1], typedict, kwds);

            /* replace the class dict by our updated spam dict */
            PDict resDict = getDict.execute(result);
            if (resDict == null) {
                resDict = factory().createDictFixedStorage((PythonObject) result);
            }
            addAllToOtherNode.execute(frame, inliningTarget, resDict.getDictStorage(), stgdict);
            setDict.execute(inliningTarget, result, stgdict);

            return result;
        }
    }

    @Builtin(name = J_FROM_PARAM, minNumOfPositionalArgs = 2, declaresExplicitSelf = true)
    @GenerateNodeFactory
    protected abstract static class FromParamNode extends PythonBinaryBuiltinNode {

        // Corresponds to _byref
        /* _byref consumes a refcount to its argument */
        protected PyCArgObject byref(Object obj,
                        @Cached PyTypeCheck pyTypeCheck) {
            if (!pyTypeCheck.isCDataObject(obj)) {
                throw raise(PythonErrorType.TypeError, EXPECTED_CDATA_INSTANCE);
            }

            CDataObject cdata = (CDataObject) obj;

            PyCArgObject parg = factory().createCArgObject();
            parg.tag = 'P';
            parg.pffi_type = ffi_type_pointer;
            parg.obj = cdata;
            parg.valuePointer = cdata.b_ptr;
            return parg;
        }

        @Specialization
        static Object none(@SuppressWarnings("unused") Object type, @SuppressWarnings("unused") PNone value) {
            /* ConvParam will convert to a NULL pointer later */
            return PNone.NONE;
        }

        @Specialization(guards = "!isPNone(value)")
        Object PyCPointerType_from_param(VirtualFrame frame, Object type, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached PyTypeCheck pyTypeCheck,
                        @Cached CastToJavaBooleanNode toJavaBooleanNode,
                        @Cached IsInstanceNode isInstanceNode,
                        @Cached IsSubClassNode isSubClassNode,
                        @Cached PyObjectStgDictNode pyObjectStgDictNode,
                        @Cached PyTypeStgDictNode pyTypeStgDictNode,
                        @Cached CDataTypeFromParamNode fromParamNode) {
            StgDictObject typedict = pyTypeStgDictNode.checkAbstractClass(type, getRaiseNode());
            /*
             * If we expect POINTER(<type>), but receive a <type> instance, accept it by calling
             * byref(<type>).
             */
            if (isInstanceNode.executeWith(frame, value, typedict.proto)) {
                return byref(value, pyTypeCheck);
            }

            if (pyTypeCheck.isPointerObject(value) || pyTypeCheck.isArrayObject(value)) {
                /*
                 * Array instances are also pointers when the item types are the same.
                 */
                StgDictObject v = pyObjectStgDictNode.execute(value);
                assert v != null : "Cannot be NULL for pointer or array objects";
                if (toJavaBooleanNode.execute(inliningTarget, isSubClassNode.execute(frame, v.proto, typedict.proto))) {
                    return value;
                }
            }
            return fromParamNode.execute(frame, type, value);
        }
    }

    @Builtin(name = J_SET_TYPE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    protected abstract static class SetTypeNode extends PythonBuiltinNode {

        @Specialization
        Object PyCPointerType_set_type(Object self, TruffleString type,
                        @Bind("this") Node inliningTarget,
                        @Cached HashingStorageSetItem setItem,
                        @Cached IsTypeNode isTypeNode,
                        @Cached PyTypeStgDictNode pyTypeStgDictNode) {
            StgDictObject dict = pyTypeStgDictNode.checkAbstractClass(self, getRaiseNode());
            PyCPointerType_SetProto(inliningTarget, dict, type, isTypeNode, pyTypeStgDictNode, getRaiseNode());
            dict.setDictStorage(setItem.execute(inliningTarget, dict.getDictStorage(), T__TYPE_, type));
            return PNone.NONE;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isString(type)")
        Object error(Object self, Object type) {
            throw raise(TypeError, TYPE_MUST_BE_A_TYPE);
        }
    }

    /*
     *
     * The PyCPointerType_Type metaclass must ensure that the subclass of Pointer can be created. It
     * must check for a _type_ attribute in the class. Since are no runtime created properties, a
     * CField is probably *not* needed ?
     *
     * class IntPointer(Pointer): _type_ = "i"
     *
     * The PyCPointer_Type provides the functionality: a contents method/property, a size
     * property/method, and the sequence protocol.
     *
     */
    static void PyCPointerType_SetProto(Node inliningTarget, StgDictObject stgdict, Object proto,
                    IsTypeNode isTypeNode,
                    PyTypeStgDictNode pyTypeStgDictNode,
                    PRaiseNode raiseNode) {
        if (proto == null || !isTypeNode.execute(inliningTarget, proto)) {
            throw raiseNode.raise(TypeError, TYPE_MUST_BE_A_TYPE);
        }
        if (pyTypeStgDictNode.execute(proto) == null) {
            throw raiseNode.raise(TypeError, TYPE_MUST_HAVE_STORAGE_INFO);
        }
        stgdict.proto = proto;
    }
}
