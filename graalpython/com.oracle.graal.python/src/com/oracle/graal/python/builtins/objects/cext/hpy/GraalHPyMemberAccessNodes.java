/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.hpy;

import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPY_MEMBER_BOOL;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPY_MEMBER_BYTE;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPY_MEMBER_CHAR;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPY_MEMBER_DOUBLE;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPY_MEMBER_FLOAT;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPY_MEMBER_HPYSSIZET;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPY_MEMBER_INT;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPY_MEMBER_LONG;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPY_MEMBER_LONGLONG;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPY_MEMBER_NONE;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPY_MEMBER_OBJECT;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPY_MEMBER_OBJECT_EX;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPY_MEMBER_SHORT;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPY_MEMBER_STRING;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPY_MEMBER_STRING_INPLACE;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPY_MEMBER_UBYTE;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPY_MEMBER_UINT;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPY_MEMBER_ULONG;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPY_MEMBER_ULONGLONG;
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.HPY_MEMBER_USHORT;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.objects.cext.common.CExtAsPythonObjectNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyMemberAccessNodesFactory.ReadMemberNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.PCallHPyFunction;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyAsNoneNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyAsPythonObjectNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyUnsignedPrimitiveAsPythonObjectNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.PCallHPyFunctionNodeGen;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.nodes.function.BuiltinFunctionRootNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.GeneratedBy;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.RootNode;

public class GraalHPyMemberAccessNodes {

    static String getAccessorName(int type) {
        switch (type) {
            case HPY_MEMBER_SHORT:
                return GraalHPyNativeSymbols.GRAAL_HPY_READ_S;
            case HPY_MEMBER_INT:
                return GraalHPyNativeSymbols.GRAAL_HPY_READ_I;
            case HPY_MEMBER_LONG:
                return GraalHPyNativeSymbols.GRAAL_HPY_READ_L;
            case HPY_MEMBER_FLOAT:
                return GraalHPyNativeSymbols.GRAAL_HPY_READ_F;
            case HPY_MEMBER_DOUBLE:
                return GraalHPyNativeSymbols.GRAAL_HPY_READ_D;
            case HPY_MEMBER_STRING:
                return GraalHPyNativeSymbols.GRAAL_HPY_READ_STRING;
            case HPY_MEMBER_OBJECT:
            case HPY_MEMBER_OBJECT_EX:
                return GraalHPyNativeSymbols.GRAAL_HPY_READ_HPY;
            case HPY_MEMBER_CHAR:
            case HPY_MEMBER_BYTE:
            case HPY_MEMBER_BOOL:
                return GraalHPyNativeSymbols.GRAAL_HPY_READ_C;
            case HPY_MEMBER_UBYTE:
                return GraalHPyNativeSymbols.GRAAL_HPY_READ_UC;
            case HPY_MEMBER_USHORT:
                return GraalHPyNativeSymbols.GRAAL_HPY_READ_US;
            case HPY_MEMBER_UINT:
                return GraalHPyNativeSymbols.GRAAL_HPY_READ_UI;
            case HPY_MEMBER_ULONG:
                return GraalHPyNativeSymbols.GRAAL_HPY_READ_UL;
            case HPY_MEMBER_STRING_INPLACE:
                return GraalHPyNativeSymbols.GRAAL_HPY_READ_STRING_IN_PLACE;
            case HPY_MEMBER_LONGLONG:
                return GraalHPyNativeSymbols.GRAAL_HPY_READ_LL;
            case HPY_MEMBER_ULONGLONG:
                return GraalHPyNativeSymbols.GRAAL_HPY_READ_ULL;
            case HPY_MEMBER_HPYSSIZET:
                return GraalHPyNativeSymbols.GRAAL_HPY_READ_HPY_SSIZE_T;
            case HPY_MEMBER_NONE:
                return null;
        }
        throw CompilerDirectives.shouldNotReachHere("invalid member type");
    }

    static CExtAsPythonObjectNode getConverterNode(int type) {
        switch (type) {
            case HPY_MEMBER_SHORT:
            case HPY_MEMBER_INT:
            case HPY_MEMBER_LONG:
            case HPY_MEMBER_FLOAT:
            case HPY_MEMBER_DOUBLE:
            case HPY_MEMBER_STRING:
            case HPY_MEMBER_CHAR:
            case HPY_MEMBER_BYTE:
            case HPY_MEMBER_BOOL:
            case HPY_MEMBER_UBYTE:
            case HPY_MEMBER_USHORT:
            case HPY_MEMBER_STRING_INPLACE:
            case HPY_MEMBER_HPYSSIZET:
                // no conversion needed
                return null;
            case HPY_MEMBER_UINT:
            case HPY_MEMBER_ULONG:
            case HPY_MEMBER_LONGLONG:
            case HPY_MEMBER_ULONGLONG:
                return HPyUnsignedPrimitiveAsPythonObjectNodeGen.create();
            case HPY_MEMBER_OBJECT:
            case HPY_MEMBER_OBJECT_EX:
                return HPyAsPythonObjectNodeGen.create();
            case HPY_MEMBER_NONE:
                return HPyAsNoneNodeGen.create();
        }
        throw CompilerDirectives.shouldNotReachHere("invalid member type");
    }

    public static class ReadMemberNodeFactory<T extends PythonBuiltinBaseNode> implements NodeFactory<T> {
        private final T node;
        private final Class<T> nodeClass;

        public ReadMemberNodeFactory(T node) {
            this.node = node;
            this.nodeClass = determineNodeClass(node);
        }

        @Override
        public T createNode(Object... arguments) {
            return NodeUtil.cloneNode(node);
        }

        @Override
        public Class<T> getNodeClass() {
            return nodeClass;
        }

        @SuppressWarnings("unchecked")
        private static <T> Class<T> determineNodeClass(T node) {
            CompilerAsserts.neverPartOfCompilation();
            Class<T> nodeClass = (Class<T>) node.getClass();
            GeneratedBy genBy = nodeClass.getAnnotation(GeneratedBy.class);
            if (genBy != null) {
                nodeClass = (Class<T>) genBy.value();
                assert nodeClass.isAssignableFrom(node.getClass());
            }
            return nodeClass;
        }

        @Override
        public List<List<Class<?>>> getNodeSignatures() {
            throw new IllegalAccessError();
        }

        @Override
        public List<Class<? extends Node>> getExecutionSignature() {
            throw new IllegalAccessError();
        }

    }

    @Builtin(minNumOfPositionalArgs = 1, parameterNames = "$self")
    protected abstract static class ReadMemberNode extends PythonUnaryBuiltinNode {
        private static final Builtin builtin = ReadMemberNode.class.getAnnotation(Builtin.class);

        @Child private PCallHPyFunction callHPyFunctionNode;
        @Child private CExtAsPythonObjectNode asPythonObjectNode;

        /** The name of the native getter function. */
        private final String accessor;

        /** The offset where to read from (will be passed to the native getter). */
        private final int offset;

        protected ReadMemberNode(String accessor, int offset, CExtAsPythonObjectNode asPythonObjectNode) {
            this.accessor = accessor;
            this.offset = offset;
            this.asPythonObjectNode = asPythonObjectNode;
        }

        @Specialization
        Object doGeneric(@SuppressWarnings("unused") VirtualFrame frame, Object self) {
            GraalHPyContext hPyContext = getContext().getHPyContext();

            // This will call pure C functions that won't ever access the Python stack nor the
            // exception state. So, we don't need to setup an indirect call.
            Object nativeResult = ensureCallHPyFunctionNode().call(hPyContext, accessor, self, (long) offset);
            if (asPythonObjectNode != null) {
                return asPythonObjectNode.execute(hPyContext, nativeResult);
            }
            return nativeResult;
        }

        private PCallHPyFunction ensureCallHPyFunctionNode() {
            if (callHPyFunctionNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callHPyFunctionNode = insert(PCallHPyFunctionNodeGen.create());
            }
            return callHPyFunctionNode;
        }

        @TruffleBoundary
        public static PBuiltinFunction createBuiltinFunction(PythonLanguage language, String propertyName, String accessor, int offset, CExtAsPythonObjectNode asPythonObjectNode) {
            RootNode rootNode = new BuiltinFunctionRootNode(language, builtin,
                            new ReadMemberNodeFactory<>(ReadMemberNodeGen.create(accessor, offset, asPythonObjectNode)), true);
            return PythonObjectFactory.getUncached().createBuiltinFunction(propertyName, null, 0, PythonUtils.getOrCreateCallTarget(rootNode));
        }
    }
}
