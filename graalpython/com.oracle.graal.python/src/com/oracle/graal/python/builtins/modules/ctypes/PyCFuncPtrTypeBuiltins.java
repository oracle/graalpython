/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.modules.ctypes.CDataTypeBuiltins.T_FROM_PARAM;
import static com.oracle.graal.python.builtins.modules.ctypes.CtypesModuleBuiltins.TYPEFLAG_ISPOINTER;
import static com.oracle.graal.python.nodes.ErrorMessages.ARGTYPES_MUST_BE_A_SEQUENCE_OF_TYPES;
import static com.oracle.graal.python.nodes.ErrorMessages.CLASS_MUST_DEFINE_FLAGS_WHICH_MUST_BE_AN_INTEGER;
import static com.oracle.graal.python.nodes.ErrorMessages.ITEM_D_IN_ARGTYPES_HAS_NO_FROM_PARAM_METHOD;
import static com.oracle.graal.python.nodes.ErrorMessages.RESTYPE_MUST_BE_A_TYPE_A_CALLABLE_OR_NONE1;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NEW__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinConstructors.TypeNode;
import com.oracle.graal.python.builtins.modules.ctypes.FFIType.FieldDesc;
import com.oracle.graal.python.builtins.modules.ctypes.FFIType.FieldSet;
import com.oracle.graal.python.builtins.modules.ctypes.StgDictBuiltins.PyTypeStgDictNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageAddAllToOther;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetItem;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GetInternalObjectArrayNode;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyCallableCheckNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.object.GetDictIfExistsNode;
import com.oracle.graal.python.nodes.object.SetDictNode;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PyCFuncPtrType)
public final class PyCFuncPtrTypeBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PyCFuncPtrTypeBuiltinsFactory.getFactories();
    }

    protected static final int PARAMFLAG_FIN = 0x1;
    protected static final int PARAMFLAG_FOUT = 0x2;
    protected static final int PARAMFLAG_FLCID = 0x4;

    protected static final TruffleString T_FLAGS_ = tsLiteral("_flags_");
    protected static final TruffleString T_ARGTYPES_ = tsLiteral("_argtypes_");
    protected static final TruffleString T_RESTYPE_ = tsLiteral("_restype_");
    protected static final TruffleString T__CHECK_RETVAL_ = tsLiteral("_check_retval_");
    protected static final TruffleString T___CTYPES_FROM_OUTPARAM__ = tsLiteral("__ctypes_from_outparam__");

    private static final TruffleString T_X_BRACES = tsLiteral("X{}");

    @ImportStatic(PyCPointerTypeBuiltins.class)
    @Builtin(name = J___NEW__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    protected abstract static class PyCFuncPtrTypeNewNode extends PythonBuiltinNode {

        @Specialization
        static Object PyCFuncPtrType_new(VirtualFrame frame, Object type, Object[] args, PKeyword[] kwds,
                        @Bind("this") Node inliningTarget,
                        @Cached TypeNode typeNew,
                        @Cached PyTypeStgDictNode pyTypeStgDictNode,
                        @Cached CastToJavaIntExactNode asNumber,
                        @Cached PyObjectLookupAttr lookupAttr,
                        @Cached GetInternalObjectArrayNode getArray,
                        @Cached GetDictIfExistsNode getDict,
                        @Cached SetDictNode setDict,
                        @Cached PyCallableCheckNode callableCheck,
                        @Cached HashingStorageGetItem getItem,
                        @Cached HashingStorageAddAllToOther addAllToOtherNode,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            StgDictObject stgdict = factory.createStgDictObject(PythonBuiltinClassType.StgDict);

            stgdict.paramfunc = CArgObjectBuiltins.PyCFuncPtrTypeParamFunc;
            /*
             * We do NOT expose the function signature in the format string. It is impossible,
             * generally, because the only requirement for the argtypes items is that they have a
             * .from_param method - we do not know the types of the arguments (although, in
             * practice, most argtypes would be a ctypes type).
             */
            stgdict.format = T_X_BRACES;
            stgdict.flags |= TYPEFLAG_ISPOINTER;

            /* create the new instance (which is a class, since we are a metatype!) */
            Object result = typeNew.execute(frame, type, args[0], args[1], args[2], kwds);

            /* replace the class dict by our updated storage dict */
            PDict resDict = getDict.execute(result);
            if (resDict == null) {
                resDict = factory.createDictFixedStorage((PythonObject) result);
            }
            addAllToOtherNode.execute(frame, inliningTarget, resDict.getDictStorage(), stgdict);
            setDict.execute(inliningTarget, result, stgdict);
            stgdict.align = FieldDesc.P.pffi_type.alignment;
            stgdict.length = 1;
            stgdict.size = FFIType.ffi_type_pointer.size;
            stgdict.setfunc = FieldSet.nil;
            stgdict.ffi_type_pointer = FFIType.ffi_type_pointer;

            Object ob = getItem.execute(inliningTarget, stgdict.getDictStorage(), T_FLAGS_);
            if (!PGuards.isInteger(ob)) {
                throw raiseNode.get(inliningTarget).raise(TypeError, CLASS_MUST_DEFINE_FLAGS_WHICH_MUST_BE_AN_INTEGER);
            }
            stgdict.flags = asNumber.execute(inliningTarget, ob) | TYPEFLAG_ISPOINTER;

            /* _argtypes_ is optional... */
            ob = getItem.execute(inliningTarget, stgdict.getDictStorage(), T_ARGTYPES_);
            if (ob != null) {
                if (!PGuards.isPTuple(ob)) {
                    throw raiseNode.get(inliningTarget).raise(TypeError, ARGTYPES_MUST_BE_A_SEQUENCE_OF_TYPES);
                }
                SequenceStorage storage = ((PTuple) ob).getSequenceStorage();
                Object[] obtuple = getArray.execute(inliningTarget, storage);
                Object[] converters = converters_from_argtypes(frame, inliningTarget, obtuple, storage.length(), raiseNode, lookupAttr);
                stgdict.argtypes = obtuple;
                stgdict.converters = converters;
            }

            ob = getItem.execute(inliningTarget, stgdict.getDictStorage(), T_RESTYPE_);
            if (!PGuards.isPNone(ob)) {
                StgDictObject dict = pyTypeStgDictNode.execute(inliningTarget, ob);
                if (dict == null && !callableCheck.execute(inliningTarget, ob)) {
                    throw raiseNode.get(inliningTarget).raise(TypeError, RESTYPE_MUST_BE_A_TYPE_A_CALLABLE_OR_NONE1);
                }
                stgdict.restype = ob;
                Object checker = lookupAttr.execute(frame, inliningTarget, ob, T__CHECK_RETVAL_);
                stgdict.checker = checker != PNone.NO_VALUE ? checker : null;
            }

            return result;
        }

        static Object[] converters_from_argtypes(VirtualFrame frame, Node inliningTarget, Object[] args, int nArgs,
                        PRaiseNode.Lazy raiseNode,
                        PyObjectLookupAttr lookupAttr) {
            Object[] converters = new Object[nArgs];

            for (int i = 0; i < nArgs; ++i) {
                Object cnv;
                Object tp = args[i];

                cnv = lookupAttr.execute(frame, inliningTarget, tp, T_FROM_PARAM);
                if (cnv == PNone.NO_VALUE) {
                    throw raiseNode.get(inliningTarget).raise(TypeError, ITEM_D_IN_ARGTYPES_HAS_NO_FROM_PARAM_METHOD, i + 1);
                }
                converters[i] = cnv;
            }
            return converters;
        }
    }
}
