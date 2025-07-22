/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.cext;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Direct;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Ignored;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.ConstCharPtrAsTruffleString;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Int;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Pointer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectTransfer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyTypeObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyTypeObjectTransfer;
import static com.oracle.graal.python.util.PythonUtils.EMPTY_OBJECT_ARRAY;

import java.util.ArrayList;
import java.util.Arrays;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiQuaternaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiTernaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiUnaryBuiltinNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.FromCharPointerNode;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.tuple.StructSequence;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.runtime.sequence.storage.ObjectSequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.strings.TruffleString;

public final class PythonCextStructSeqBuiltins {

    @CApiBuiltin(ret = Int, args = {PyTypeObject, Pointer, Int}, call = Ignored)
    abstract static class PyTruffleStructSequence_InitType2 extends CApiTernaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        static int doGeneric(PythonAbstractClass klass, Object fields, int nInSequence,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached CStructAccess.ReadPointerNode readNode,
                        @Cached FromCharPointerNode fromCharPtr) {

            ArrayList<TruffleString> names = new ArrayList<>();
            ArrayList<TruffleString> docs = new ArrayList<>();

            int pos = 0;

            while (true) {

                Object name = readNode.readArrayElement(fields, pos * 2);
                if ((name instanceof Long && (long) name == 0) || lib.isNull(name)) {
                    break;
                }
                Object doc = readNode.readArrayElement(fields, pos * 2 + 1);
                names.add(fromCharPtr.execute(name));
                docs.add(lib.isNull(doc) ? null : fromCharPtr.execute(doc));
                pos++;
            }

            TruffleString[] fieldNames = names.toArray(TruffleString[]::new);
            TruffleString[] fieldDocs = docs.toArray(TruffleString[]::new);

            StructSequence.Descriptor d = new StructSequence.Descriptor(nInSequence, fieldNames, fieldDocs);
            StructSequence.initType(PythonContext.get(readNode), klass, d);
            return 0;
        }
    }

    @CApiBuiltin(ret = PyTypeObjectTransfer, args = {ConstCharPtrAsTruffleString, ConstCharPtrAsTruffleString, Pointer, Int}, call = Ignored)
    abstract static class PyTruffleStructSequence_NewType extends CApiQuaternaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        Object doGeneric(TruffleString typeName, TruffleString typeDoc, Object fields, int nInSequence,
                        @Cached PyTruffleStructSequence_InitType2 initNode,
                        @Cached ReadAttributeFromObjectNode readTypeBuiltinNode,
                        @CachedLibrary(limit = "1") DynamicObjectLibrary dylib,
                        @Cached CallNode callTypeNewNode,
                        @Bind PythonLanguage language) {
            Object typeBuiltin = readTypeBuiltinNode.execute(getCore().getBuiltins(), BuiltinNames.T_TYPE);
            PTuple bases = PFactory.createTuple(language, new Object[]{PythonBuiltinClassType.PTuple});
            PDict namespace = PFactory.createDict(language, new PKeyword[]{new PKeyword(SpecialAttributeNames.T___DOC__, typeDoc)});
            Object cls = callTypeNewNode.executeWithoutFrame(typeBuiltin, typeName, bases, namespace);
            initNode.execute(cls, fields, nInSequence);
            if (cls instanceof PythonClass) {
                ((PythonClass) cls).makeStaticBase(dylib);
            }
            return cls;
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyTypeObject}, call = Direct)
    abstract static class PyStructSequence_New extends CApiUnaryBuiltinNode {

        @Specialization
        static Object doGeneric(Object cls,
                        @Bind Node inliningTarget,
                        @Cached("createForceType()") ReadAttributeFromObjectNode readRealSizeNode,
                        @Cached CastToJavaIntExactNode castToIntNode,
                        @Bind PythonLanguage language,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape,
                        @Cached PRaiseNode raiseNode) {
            try {
                Object realSizeObj = readRealSizeNode.execute(cls, StructSequence.T_N_FIELDS);
                if (realSizeObj == PNone.NO_VALUE) {
                    throw raiseNode.raise(inliningTarget, SystemError, ErrorMessages.BAD_ARG_TO_INTERNAL_FUNC, EMPTY_OBJECT_ARRAY);
                } else {
                    int realSize = castToIntNode.execute(inliningTarget, realSizeObj);
                    Object[] values = new Object[realSize];
                    Arrays.fill(values, PNone.NO_VALUE); // Initialize to C NULL
                    return PFactory.createTuple(language, cls, getInstanceShape.execute(cls), new ObjectSequenceStorage(values));
                }
            } catch (CannotCastException e) {
                throw CompilerDirectives.shouldNotReachHere("attribute 'n_fields' is expected to be a Java int");
            }
        }
    }
}
