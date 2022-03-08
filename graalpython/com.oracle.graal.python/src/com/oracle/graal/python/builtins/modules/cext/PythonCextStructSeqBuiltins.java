/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___NEW__;
import static com.oracle.graal.python.util.PythonUtils.EMPTY_OBJECT_ARRAY;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.AsPythonObjectNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PRaiseNativeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ToNewRefNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.TransformExceptionToNativeNode;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.tuple.StructSequence;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.util.OverflowException;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendsModule = PythonCextBuiltins.PYTHON_CEXT)
@GenerateNodeFactory
public final class PythonCextStructSeqBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PythonCextStructSeqBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
    }

    // directly called without landing function
    @Builtin(name = "PyStructSequence_InitType2", minNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    abstract static class PyStructSequenceInitType2 extends PythonCextBuiltins.NativeBuiltin {

        @Specialization(limit = "1")
        static int doGeneric(Object klass, Object fieldNamesObj, Object fieldDocsObj, int nInSequence,
                        @Cached CExtNodes.AsPythonObjectNode asPythonObjectNode,
                        @CachedLibrary("fieldNamesObj") InteropLibrary lib,
                        @Cached(parameters = "true") WriteAttributeToObjectNode clearNewNode,
                        @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
            return initializeStructType(asPythonObjectNode.execute(klass), fieldNamesObj, fieldDocsObj, nInSequence, PythonLanguage.get(lib), lib, clearNewNode, fromJavaStringNode);
        }

        static int initializeStructType(Object klass, Object fieldNamesObj, Object fieldDocsObj, int nInSequence, PythonLanguage language, InteropLibrary lib, WriteAttributeToObjectNode clearNewNode,
                        TruffleString.FromJavaStringNode fromJavaStringNode) {
            // 'fieldNames' and 'fieldDocs' must be of same type; they share the interop lib
            assert fieldNamesObj.getClass() == fieldDocsObj.getClass();

            try {
                int n = PInt.intValueExact(lib.getArraySize(fieldNamesObj));
                if (n != lib.getArraySize(fieldDocsObj)) {
                    // internal error: the C function must type the object correctly
                    throw CompilerDirectives.shouldNotReachHere("len(fieldNames) != len(fieldDocs)");
                }
                TruffleString[] fieldNames = new TruffleString[n];
                TruffleString[] fieldDocs = new TruffleString[n];
                for (int i = 0; i < n; i++) {
                    fieldNames[i] = cast(lib.readArrayElement(fieldNamesObj, i), fromJavaStringNode);
                    fieldDocs[i] = cast(lib.readArrayElement(fieldDocsObj, i), fromJavaStringNode);
                }
                clearNewNode.execute(klass, T___NEW__, PNone.NO_VALUE);
                StructSequence.Descriptor d = new StructSequence.Descriptor(null, nInSequence, fieldNames, fieldDocs);
                StructSequence.initType(language, klass, d);
                return 0;
            } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
                throw CompilerDirectives.shouldNotReachHere();
            } catch (OverflowException e) {
                // fall through
            }
            return -1;
        }

        private static TruffleString cast(Object object, TruffleString.FromJavaStringNode fromJavaStringNode) {
            if (object instanceof String) {
                return fromJavaStringNode.execute((String) object, TS_ENCODING);
            }
            if (object instanceof TruffleString) {
                return (TruffleString) object;
            }
            throw CompilerDirectives.shouldNotReachHere("object is expected to be a Java string");
        }
    }

    // directly called without landing function
    @Builtin(name = "PyStructSequence_NewType", minNumOfPositionalArgs = 5)
    @GenerateNodeFactory
    abstract static class PyStructSequenceNewType extends PythonCextBuiltins.NativeBuiltin {

        @Specialization(limit = "1")
        Object doGeneric(VirtualFrame frame, TruffleString typeName, TruffleString typeDoc, Object fieldNamesObj, Object fieldDocsObj, int nInSequence,
                        @Cached ReadAttributeFromObjectNode readTypeBuiltinNode,
                        @Cached CallNode callTypeNewNode,
                        @CachedLibrary("fieldNamesObj") InteropLibrary lib,
                        @Cached(parameters = "true") WriteAttributeToObjectNode clearNewNode,
                        @Cached ToNewRefNode toNewRefNode,
                        @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
            try {
                Object typeBuiltin = readTypeBuiltinNode.execute(getCore().getBuiltins(), BuiltinNames.T_TYPE);
                PTuple bases = factory().createTuple(new Object[]{PythonBuiltinClassType.PTuple});
                PDict namespace = factory().createDict(new PKeyword[]{new PKeyword(SpecialAttributeNames.T___DOC__, typeDoc)});
                Object cls = callTypeNewNode.execute(typeBuiltin, typeName, bases, namespace);
                PyStructSequenceInitType2.initializeStructType(cls, fieldNamesObj, fieldDocsObj, nInSequence, getLanguage(), lib, clearNewNode, fromJavaStringNode);
                return toNewRefNode.execute(cls);
            } catch (PException e) {
                transformToNative(frame, e);
                return getContext().getNativeNull();
            }
        }
    }

    // directly called without landing function
    @Builtin(name = "PyStructSequence_New", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class PyStructSequenceNew extends PythonUnaryBuiltinNode {

        @Specialization
        Object doGeneric(Object clsPtr,
                        @Cached AsPythonObjectNode asPythonObjectNode,
                        @Cached("createForceType()") ReadAttributeFromObjectNode readRealSizeNode,
                        @Cached CastToJavaIntExactNode castToIntNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                        @Cached ToNewRefNode toNewRefNode) {
            try {
                Object cls = asPythonObjectNode.execute(clsPtr);
                Object realSizeObj = readRealSizeNode.execute(cls, StructSequence.T_N_FIELDS);
                Object res;
                if (realSizeObj == PNone.NO_VALUE) {
                    PRaiseNativeNode.raiseNative(null, SystemError, ErrorMessages.BAD_ARG_TO_INTERNAL_FUNC, EMPTY_OBJECT_ARRAY, getRaiseNode(), transformExceptionToNativeNode);
                    res = getContext().getNativeNull();
                } else {
                    int realSize = castToIntNode.execute(realSizeObj);
                    res = factory().createTuple(cls, new Object[realSize]);
                }
                return toNewRefNode.execute(res);
            } catch (CannotCastException e) {
                throw CompilerDirectives.shouldNotReachHere("attribute 'n_fields' is expected to be a Java int");
            }
        }
    }

}
