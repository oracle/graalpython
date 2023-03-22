/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.nodes.BuiltinNames.J_ARRAY;

import java.lang.reflect.Array;
import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithRaise;
import com.oracle.graal.python.nodes.builtins.ListNodes;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;

@CoreFunctions(defineModule = "jarray")
public final class JArrayModuleBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return JArrayModuleBuiltinsFactory.getFactories();
    }

    abstract static class ArrayFromTypeCode extends PNodeWithRaise {
        public abstract Object execute(int length, String typeCode);

        protected static final String Z = "z";
        protected static final String C = "c";
        protected static final String B = "b";
        protected static final String H = "h";
        protected static final String I = "i";
        protected static final String L = "l";
        protected static final String F = "f";
        protected static final String D = "d";

        @Specialization(guards = "eq(typeCode, Z)")
        static Object z(int length, @SuppressWarnings("unused") String typeCode) {
            return new boolean[length];
        }

        @Specialization(guards = "eq(typeCode, C)")
        static Object c(int length, @SuppressWarnings("unused") String typeCode) {
            return new char[length];
        }

        @Specialization(guards = "eq(typeCode, B)")
        static Object b(int length, @SuppressWarnings("unused") String typeCode) {
            return new byte[length];
        }

        @Specialization(guards = "eq(typeCode, H)")
        static Object h(int length, @SuppressWarnings("unused") String typeCode) {
            return new short[length];
        }

        @Specialization(guards = "eq(typeCode, I)")
        static Object i(int length, @SuppressWarnings("unused") String typeCode) {
            return new int[length];
        }

        @Specialization(guards = "eq(typeCode, L)")
        static Object l(int length, @SuppressWarnings("unused") String typeCode) {
            return new long[length];
        }

        @Specialization(guards = "eq(typeCode, F)")
        static Object f(int length, @SuppressWarnings("unused") String typeCode) {
            return new float[length];
        }

        @Specialization(guards = "eq(typeCode, D)")
        static Object d(int length, @SuppressWarnings("unused") String typeCode) {
            return new double[length];
        }

        @Fallback
        @SuppressWarnings("unused")
        Object error(int length, String typeCode) {
            throw raise(ValueError, ErrorMessages.INVALID_TYPE_CODE, typeCode);
        }

        protected static boolean eq(String a, String b) {
            return b.equals(a);
        }
    }

    @Builtin(name = "zeros", minNumOfPositionalArgs = 2, parameterNames = {"length", "type"})
    @ArgumentClinic(name = "length", conversion = ArgumentClinic.ClinicConversion.Index)
    @GenerateNodeFactory
    abstract static class ZerosNode extends PythonBinaryClinicBuiltinNode {
        public abstract Object execute(int length, Object type);

        @Specialization(guards = "isString(typeCodeObj)")
        Object fromTypeCode(int length, Object typeCodeObj,
                        @Cached CastToJavaStringNode cast,
                        @Cached ArrayFromTypeCode fromTypeCodeNode) {
            String typeCode = cast.execute(typeCodeObj);
            Object array = fromTypeCodeNode.execute(length, typeCode);
            return getContext().getEnv().asGuestValue(array);
        }

        @Specialization(guards = "!isString(classObj)")
        Object fromClass(int length, Object classObj) {
            TruffleLanguage.Env env = getContext().getEnv();
            if (env.isHostObject(classObj)) {
                Object clazz = env.asHostObject(classObj);
                if (clazz instanceof Class) {
                    Object array = Array.newInstance((Class<?>) clazz, length);
                    return env.asGuestValue(array);
                }
            }
            throw raise(TypeError, ErrorMessages.SECOND_ARG_MUST_BE_STR_OR_JAVA_CLS, classObj);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return JArrayModuleBuiltinsClinicProviders.ZerosNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = J_ARRAY, minNumOfPositionalArgs = 2, parameterNames = {"sequence", "type"})
    @GenerateNodeFactory
    abstract static class ArrayNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object fromSequence(PSequence sequence, Object type,
                        @Bind("this") Node inliningTarget,
                        @Shared @CachedLibrary(limit = "5") InteropLibrary lib,
                        @Shared @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Shared @Cached SequenceStorageNodes.GetItemScalarNode getItemScalarNode,
                        @Shared @Cached ZerosNode zerosNode) {
            SequenceStorage storage = getSequenceStorageNode.execute(inliningTarget, sequence);
            int length = storage.length();
            Object array = zerosNode.execute(length, type);
            for (int i = 0; i < length; i++) {
                Object value = getItemScalarNode.execute(inliningTarget, storage, i);
                try {
                    lib.writeArrayElement(array, i, value);
                } catch (UnsupportedTypeException e) {
                    throw raise(TypeError, ErrorMessages.TYPE_P_NOT_SUPPORTED_BY_FOREIGN_OBJ, value);
                } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
                    throw CompilerDirectives.shouldNotReachHere("failed to set array item");
                }
            }
            return array;
        }

        @Specialization(guards = "!isPSequence(sequence)")
        Object fromIterable(VirtualFrame frame, Object sequence, Object type,
                        @Bind("this") Node inliningTarget,
                        @Cached ListNodes.ConstructListNode constructListNode,
                        @Shared @CachedLibrary(limit = "5") InteropLibrary lib,
                        @Shared @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Shared @Cached SequenceStorageNodes.GetItemScalarNode getItemScalarNode,
                        @Shared @Cached ZerosNode zerosNode) {
            PList list = constructListNode.execute(frame, sequence);
            return fromSequence(list, type, inliningTarget, lib, getSequenceStorageNode, getItemScalarNode, zerosNode);
        }
    }
}
