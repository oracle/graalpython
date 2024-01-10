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

import static com.oracle.graal.python.builtins.modules.ctypes.CtypesModuleBuiltins.TYPEFLAG_HASPOINTER;
import static com.oracle.graal.python.builtins.modules.ctypes.CtypesModuleBuiltins.TYPEFLAG_ISPOINTER;
import static com.oracle.graal.python.builtins.modules.ctypes.PyCPointerTypeBuiltins.T__TYPE_;
import static com.oracle.graal.python.nodes.ErrorMessages.ARRAY_TOO_LARGE;
import static com.oracle.graal.python.nodes.ErrorMessages.CLASS_MUST_DEFINE_A_LENGTH_ATTRIBUTE;
import static com.oracle.graal.python.nodes.ErrorMessages.CLASS_MUST_DEFINE_A_TYPE_ATTRIBUTE;
import static com.oracle.graal.python.nodes.ErrorMessages.THE_LENGTH_ATTRIBUTE_IS_TOO_LARGE;
import static com.oracle.graal.python.nodes.ErrorMessages.THE_LENGTH_ATTRIBUTE_MUST_BE_AN_INTEGER;
import static com.oracle.graal.python.nodes.ErrorMessages.THE_LENGTH_ATTRIBUTE_MUST_NOT_BE_NEGATIVE;
import static com.oracle.graal.python.nodes.ErrorMessages.TYPE_MUST_HAVE_STORAGE_INFO;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NEW__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.AttributeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinConstructors.TypeNode;
import com.oracle.graal.python.builtins.modules.ctypes.FFIType.FieldDesc;
import com.oracle.graal.python.builtins.modules.ctypes.StgDictBuiltins.PyTypeStgDictNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageAddAllToOther;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.GetDictIfExistsNode;
import com.oracle.graal.python.nodes.object.SetDictNode;
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

@CoreFunctions(extendClasses = PythonBuiltinClassType.PyCArrayType)
public final class PyCArrayTypeBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PyCArrayTypeBuiltinsFactory.getFactories();
    }

    protected static final TruffleString T__LENGTH_ = tsLiteral("_length_");

    @ImportStatic({PyCPointerTypeBuiltins.class, PyCArrayTypeBuiltins.class, SpecialMethodNames.class})
    @Builtin(name = J___NEW__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    protected abstract static class PyCArrayTypeNewNode extends PythonBuiltinNode {

        @Specialization
        static Object PyCArrayType_new(VirtualFrame frame, Object type, Object[] args, PKeyword[] kwds,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectLookupAttr lookupAttrType,
                        @Cached PyObjectLookupAttr lookupAttrLength,
                        @Cached IsBuiltinObjectProfile profile,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached TypeNode typeNew,
                        @Cached GetDictIfExistsNode getDict,
                        @Cached SetDictNode setDict,
                        @Cached HashingStorageAddAllToOther addAllToOtherNode,
                        @Cached PyTypeStgDictNode pyTypeStgDictNode,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            /*
             * create the new instance (which is a class, since we are a metatype!)
             */
            Object result = typeNew.execute(frame, type, args[0], args[1], args[2], kwds);
            Object length_attr = lookupAttrLength.execute(frame, inliningTarget, result, T__LENGTH_);
            if (length_attr == PNone.NO_VALUE) {
                throw raiseNode.get(inliningTarget).raise(AttributeError, CLASS_MUST_DEFINE_A_LENGTH_ATTRIBUTE);
            }

            int length;
            try {
                length = asSizeNode.executeExact(frame, inliningTarget, length_attr);
            } catch (PException e) {
                if (e.expectTypeOrOverflowError(inliningTarget, profile)) {
                    throw raiseNode.get(inliningTarget).raise(OverflowError, THE_LENGTH_ATTRIBUTE_IS_TOO_LARGE);
                } else {
                    throw raiseNode.get(inliningTarget).raise(TypeError, THE_LENGTH_ATTRIBUTE_MUST_BE_AN_INTEGER);
                }
            }

            if (length < 0) {
                throw raiseNode.get(inliningTarget).raise(ValueError, THE_LENGTH_ATTRIBUTE_MUST_NOT_BE_NEGATIVE);
            }

            Object type_attr = lookupAttrType.execute(frame, inliningTarget, result, T__TYPE_);
            if (type_attr == PNone.NO_VALUE) {
                throw raiseNode.get(inliningTarget).raise(AttributeError, CLASS_MUST_DEFINE_A_TYPE_ATTRIBUTE);
            }

            StgDictObject stgdict = factory.createStgDictObject(PythonBuiltinClassType.StgDict);

            StgDictObject itemdict = pyTypeStgDictNode.execute(inliningTarget, type_attr);
            if (itemdict == null) {
                throw raiseNode.get(inliningTarget).raise(TypeError, TYPE_MUST_HAVE_STORAGE_INFO);
            }

            assert itemdict.format != null;
            stgdict.format = itemdict.format;
            stgdict.ndim = itemdict.ndim + 1;
            stgdict.shape = new int[stgdict.ndim];
            stgdict.shape[0] = length;
            if (stgdict.ndim > 1) {
                for (int i = 0, j = 1; i < stgdict.ndim - 1; i++, j++) { // memmove
                    stgdict.shape[j] = itemdict.shape[i];
                }
            }

            int itemsize = itemdict.size;
            if (itemsize != 0 && length > Integer.MAX_VALUE / itemsize) {
                throw raiseNode.get(inliningTarget).raise(OverflowError, ARRAY_TOO_LARGE);
            }

            int itemalign = itemdict.align;

            if ((itemdict.flags & (TYPEFLAG_ISPOINTER | TYPEFLAG_HASPOINTER)) != 0) {
                stgdict.flags |= TYPEFLAG_HASPOINTER;
            }

            stgdict.size = itemsize * length;
            stgdict.align = itemalign;
            stgdict.length = length;
            stgdict.proto = type_attr;

            stgdict.paramfunc = CArgObjectBuiltins.PyCArrayTypeParamFunc;

            /* Arrays are passed as pointers to function calls. */
            stgdict.ffi_type_pointer = FFIType.ffi_type_pointer;

            /* replace the class dict by our updated spam dict */
            PDict resDict = getDict.execute(result);
            if (resDict == null) {
                resDict = factory.createDictFixedStorage((PythonObject) result);
            }
            addAllToOtherNode.execute(frame, inliningTarget, resDict.getDictStorage(), stgdict);
            setDict.execute(inliningTarget, (PythonObject) result, stgdict);

            /*
             * Special case for character arrays. A permanent annoyance: char arrays are also
             * strings!
             */
            if (itemdict.getfunc == FieldDesc.c.getfunc) {
                LazyPyCArrayTypeBuiltins.createCharArrayGetSet(PythonLanguage.get(inliningTarget), result);
            } else if (itemdict.getfunc == FieldDesc.u.getfunc) { // CTYPES_UNICODE
                LazyPyCArrayTypeBuiltins.createWCharArrayGetSet(PythonLanguage.get(inliningTarget), result);
            }

            return result;
        }
    }
}
