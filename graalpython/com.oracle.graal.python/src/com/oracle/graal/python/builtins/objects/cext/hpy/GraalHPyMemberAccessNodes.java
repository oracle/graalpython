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
import static com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyDef.OBJECT_HPY_NATIVE_SPACE;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cell.CellBuiltins;
import com.oracle.graal.python.builtins.objects.cell.CellBuiltins.GetRefNode;
import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.cext.common.CExtAsPythonObjectNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyMemberAccessNodesFactory.HPyReadMemberNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyMemberAccessNodesFactory.HPyWriteMemberNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.PCallHPyFunction;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyAsHandleNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyAsNativeDoubleNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyAsNativePrimitiveNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyAsNoneNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyAsPythonObjectNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyUnsignedPrimitiveAsPythonObjectNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.PCallHPyFunctionNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.HPyExternalFunctionNodes.HPyExternalFunctionInvokeNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.HPyExternalFunctionNodesFactory.HPyExternalFunctionInvokeNodeGen;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.function.BuiltinFunctionRootNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.GeneratedBy;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.RootNode;

public class GraalHPyMemberAccessNodes {

    static String getReadAccessorName(int type) {
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

    static CExtAsPythonObjectNode getReadConverterNode(int type) {
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

    static String getWriteAccessorName(int type) {
        switch (type) {
            case HPY_MEMBER_SHORT:
                return GraalHPyNativeSymbols.GRAAL_HPY_WRITE_S;
            case HPY_MEMBER_INT:
                return GraalHPyNativeSymbols.GRAAL_HPY_WRITE_I;
            case HPY_MEMBER_LONG:
                return GraalHPyNativeSymbols.GRAAL_HPY_WRITE_L;
            case HPY_MEMBER_FLOAT:
                return GraalHPyNativeSymbols.GRAAL_HPY_WRITE_F;
            case HPY_MEMBER_DOUBLE:
                return GraalHPyNativeSymbols.GRAAL_HPY_WRITE_D;
            case HPY_MEMBER_STRING:
                return GraalHPyNativeSymbols.GRAAL_HPY_WRITE_STRING;
            case HPY_MEMBER_OBJECT:
            case HPY_MEMBER_OBJECT_EX:
            case HPY_MEMBER_NONE:
                return GraalHPyNativeSymbols.GRAAL_HPY_WRITE_HPY;
            case HPY_MEMBER_CHAR:
            case HPY_MEMBER_BYTE:
            case HPY_MEMBER_BOOL:
                return GraalHPyNativeSymbols.GRAAL_HPY_WRITE_C;
            case HPY_MEMBER_UBYTE:
                return GraalHPyNativeSymbols.GRAAL_HPY_WRITE_UC;
            case HPY_MEMBER_USHORT:
                return GraalHPyNativeSymbols.GRAAL_HPY_WRITE_US;
            case HPY_MEMBER_UINT:
                return GraalHPyNativeSymbols.GRAAL_HPY_WRITE_UI;
            case HPY_MEMBER_ULONG:
                return GraalHPyNativeSymbols.GRAAL_HPY_WRITE_UL;
            case HPY_MEMBER_STRING_INPLACE:
                return GraalHPyNativeSymbols.GRAAL_HPY_WRITE_STRING_IN_PLACE;
            case HPY_MEMBER_LONGLONG:
                return GraalHPyNativeSymbols.GRAAL_HPY_WRITE_LL;
            case HPY_MEMBER_ULONGLONG:
                return GraalHPyNativeSymbols.GRAAL_HPY_WRITE_ULL;
            case HPY_MEMBER_HPYSSIZET:
                return GraalHPyNativeSymbols.GRAAL_HPY_WRITE_HPY_SSIZE_T;
        }
        throw CompilerDirectives.shouldNotReachHere("invalid member type");
    }

    static CExtToNativeNode getWriteConverterNode(int type) {
        switch (type) {
            case HPY_MEMBER_SHORT:
            case HPY_MEMBER_INT:
            case HPY_MEMBER_CHAR:
            case HPY_MEMBER_BYTE:
            case HPY_MEMBER_BOOL:
                // TODO(fa): use appropriate native type sizes
                return HPyAsNativePrimitiveNodeGen.create(Integer.BYTES, true);
            case HPY_MEMBER_LONG:
            case HPY_MEMBER_HPYSSIZET:
                // TODO(fa): use appropriate native type sizes
                return HPyAsNativePrimitiveNodeGen.create(Long.BYTES, true);
            case HPY_MEMBER_FLOAT:
            case HPY_MEMBER_DOUBLE:
                return HPyAsNativeDoubleNodeGen.create();
            case HPY_MEMBER_USHORT:
            case HPY_MEMBER_UINT:
            case HPY_MEMBER_UBYTE:
                // TODO(fa): use appropriate native type sizes
                return HPyAsNativePrimitiveNodeGen.create(Integer.BYTES, false);
            case HPY_MEMBER_ULONG:
            case HPY_MEMBER_LONGLONG:
            case HPY_MEMBER_ULONGLONG:
                // TODO(fa): use appropriate native type sizes
                return HPyAsNativePrimitiveNodeGen.create(Long.BYTES, false);
            case HPY_MEMBER_OBJECT:
            case HPY_MEMBER_OBJECT_EX:
            case HPY_MEMBER_NONE:
                return HPyAsHandleNodeGen.create();
            case HPY_MEMBER_STRING:
            case HPY_MEMBER_STRING_INPLACE:
                return null;
        }
        throw CompilerDirectives.shouldNotReachHere("invalid member type");
    }

    public static class HPyMemberNodeFactory<T extends PythonBuiltinBaseNode> implements NodeFactory<T> {
        private final T node;
        private final Class<T> nodeClass;

        public HPyMemberNodeFactory(T node) {
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
    protected abstract static class HPyReadMemberNode extends PythonUnaryBuiltinNode {
        private static final Builtin builtin = HPyReadMemberNode.class.getAnnotation(Builtin.class);

        @Child private PCallHPyFunction callHPyFunctionNode;
        @Child private CExtAsPythonObjectNode asPythonObjectNode;
        @Child private ReadAttributeFromObjectNode readNativeSpaceNode;

        /** The name of the native getter function. */
        private final String accessor;

        /** The offset where to read from (will be passed to the native getter). */
        private final int offset;

        protected HPyReadMemberNode(String accessor, int offset, CExtAsPythonObjectNode asPythonObjectNode) {
            this.accessor = accessor;
            this.offset = offset;
            this.asPythonObjectNode = asPythonObjectNode;
        }

        @Specialization
        Object doGeneric(@SuppressWarnings("unused") VirtualFrame frame, Object self) {
            GraalHPyContext hPyContext = getContext().getHPyContext();

            Object nativeSpacePtr = ensureReadNativeSpaceNode().execute(self, OBJECT_HPY_NATIVE_SPACE);
            if (nativeSpacePtr == PNone.NO_VALUE) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raise(PythonBuiltinClassType.SystemError, "Attempting to read from offset %d but object '%s' has no associated native space.", offset, self);
            }

            // This will call pure C functions that won't ever access the Python stack nor the
            // exception state. So, we don't need to setup an indirect call.
            Object nativeResult = ensureCallHPyFunctionNode().call(hPyContext, accessor, nativeSpacePtr, (long) offset);
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

        private ReadAttributeFromObjectNode ensureReadNativeSpaceNode() {
            if (readNativeSpaceNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readNativeSpaceNode = insert(ReadAttributeFromObjectNode.create());
            }
            return readNativeSpaceNode;
        }

        @TruffleBoundary
        public static PBuiltinFunction createBuiltinFunction(PythonLanguage language, String propertyName, int type, int offset) {
            String accessor = getReadAccessorName(type);
            CExtAsPythonObjectNode asPythonObjectNode = getReadConverterNode(type);
            RootNode rootNode = new BuiltinFunctionRootNode(language, builtin,
                            new HPyMemberNodeFactory<>(HPyReadMemberNodeGen.create(accessor, offset, asPythonObjectNode)), true);
            return PythonObjectFactory.getUncached().createBuiltinFunction(propertyName, null, 0, PythonUtils.getOrCreateCallTarget(rootNode));
        }
    }

    @Builtin(minNumOfPositionalArgs = 2, parameterNames = {"$self", "value"})
    protected abstract static class HPyWriteMemberNode extends PythonBinaryBuiltinNode {
        private static final Builtin builtin = HPyWriteMemberNode.class.getAnnotation(Builtin.class);

        @Child private PCallHPyFunction callHPyFunctionNode;
        @Child private CExtToNativeNode toNativeNode;
        @Child private ReadAttributeFromObjectNode readNativeSpaceNode;

        /** The name of the native getter function. */
        private final String accessor;

        /** The offset where to read from (will be passed to the native getter). */
        private final int offset;

        protected HPyWriteMemberNode(String accessor, int offset, CExtToNativeNode toNativeNode) {
            this.accessor = accessor;
            this.offset = offset;
            this.toNativeNode = toNativeNode;
        }

        @Specialization
        Object doGeneric(VirtualFrame frame, Object self, Object value) {
            GraalHPyContext hPyContext = getContext().getHPyContext();

            Object nativeSpacePtr = ensureReadNativeSpaceNode().execute(self, OBJECT_HPY_NATIVE_SPACE);
            if (nativeSpacePtr == PNone.NO_VALUE) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raise(PythonBuiltinClassType.SystemError, "Attempting to write to offset %d but object '%s' has no associated native space.", offset, self);
            }

            // convert value if needed
            Object nativeValue;
            if (toNativeNode != null) {
                // The conversion to a native primitive may call arbitrary user code. So we need to
                // prepare an indirect call.
                Object savedState = IndirectCallContext.enter(frame, getContext(), this);
                try {
                    nativeValue = toNativeNode.execute(hPyContext, value);
                } finally {
                    IndirectCallContext.exit(frame, getContext(), savedState);
                }
            } else {
                nativeValue = value;
            }

            // This will call pure C functions that won't ever access the Python stack nor the
            // exception state. So, we don't need to setup an indirect call.
            ensureCallHPyFunctionNode().call(hPyContext, accessor, nativeSpacePtr, (long) offset, nativeValue);
            return value;
        }

        private PCallHPyFunction ensureCallHPyFunctionNode() {
            if (callHPyFunctionNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callHPyFunctionNode = insert(PCallHPyFunctionNodeGen.create());
            }
            return callHPyFunctionNode;
        }

        private ReadAttributeFromObjectNode ensureReadNativeSpaceNode() {
            if (readNativeSpaceNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readNativeSpaceNode = insert(ReadAttributeFromObjectNode.create());
            }
            return readNativeSpaceNode;
        }

        @TruffleBoundary
        public static PBuiltinFunction createBuiltinFunction(PythonLanguage language, String propertyName, int type, int offset) {
            String accessor = getWriteAccessorName(type);
            CExtToNativeNode toNativeNode = getWriteConverterNode(type);
            RootNode rootNode = new BuiltinFunctionRootNode(language, builtin, new HPyMemberNodeFactory<>(HPyWriteMemberNodeGen.create(accessor, offset, toNativeNode)), true);
            return PythonObjectFactory.getUncached().createBuiltinFunction(propertyName, null, 0, PythonUtils.getOrCreateCallTarget(rootNode));
        }
    }

    /**
     * A simple and lightweight Python root node that invokes a native getter function. The native
     * call target and the native closure pointer are passed as Python closure.
     */
    abstract static class HPyGetSetDescriptorRootNode extends PRootNode {

        /**
         * The index of the cell that contains the target (i.e. the pointer of native getter/setter
         * function).
         */
        static final int CELL_INDEX_TARGET = 0;

        /**
         * The index of the cell that contains the native closure pointer (i.e. a native pointer of
         * type {@code void*}).
         */
        static final int CELL_INDEX_CLOSURE = 1;

        @Child private HPyExternalFunctionInvokeNode invokeNode;
        @Child private CellBuiltins.GetRefNode readTargetCellNode;
        @Child private CellBuiltins.GetRefNode readClosureCellNode;

        private final String name;

        HPyGetSetDescriptorRootNode(PythonLanguage language, String name) {
            super(language);
            this.name = name;
        }

        static PCell[] createPythonClosure(Object target, Object closure, PythonObjectFactory factory) {
            PCell[] pythonClosure = new PCell[2];

            PCell targetCell = factory.createCell(Truffle.getRuntime().createAssumption());
            targetCell.setRef(target);
            pythonClosure[CELL_INDEX_TARGET] = targetCell;

            PCell closureCell = factory.createCell(Truffle.getRuntime().createAssumption());
            closureCell.setRef(closure);
            pythonClosure[CELL_INDEX_CLOSURE] = closureCell;
            return pythonClosure;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            PCell[] frameClosure = PArguments.getClosure(frame);
            assert frameClosure.length == 2 : "invalid closure for HPyGetSetDescriptorSetterRootNode";

            Object target = ensureReadTargetCellNode().execute(frameClosure[CELL_INDEX_TARGET]);
            Object closure = ensureReadClosureCellNode().execute(frameClosure[CELL_INDEX_CLOSURE]);

            return ensureInvokeNode().execute(frame, name, target, createArguments(frame, closure));
        }

        protected abstract Object[] createArguments(VirtualFrame frame, Object closure);

        @Override
        public String getName() {
            return name;
        }

        private HPyExternalFunctionInvokeNode ensureInvokeNode() {
            if (invokeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                invokeNode = insert(HPyExternalFunctionInvokeNodeGen.create());
            }
            return invokeNode;
        }

        private GetRefNode ensureReadTargetCellNode() {
            if (readTargetCellNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readTargetCellNode = insert(GetRefNode.create());
            }
            return readTargetCellNode;
        }

        private GetRefNode ensureReadClosureCellNode() {
            if (readClosureCellNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readClosureCellNode = insert(GetRefNode.create());
            }
            return readClosureCellNode;
        }

        @Override
        public boolean isPythonInternal() {
            return true;
        }

        @Override
        public boolean isInternal() {
            return true;
        }
    }

    /**
     * A simple and lightweight Python root node that invokes a native getter function. The native
     * call target and the native closure pointer are passed as Python closure.
     */
    static final class HPyGetSetDescriptorGetterRootNode extends HPyGetSetDescriptorRootNode {
        private static final Signature SIGNATURE = new Signature(-1, false, -1, false, new String[]{"$self"}, null);

        HPyGetSetDescriptorGetterRootNode(PythonLanguage language, String name) {
            super(language, name);
        }

        @Override
        protected Object[] createArguments(VirtualFrame frame, Object closure) {
            return new Object[]{PArguments.getArgument(frame, 0), closure};
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }

        @TruffleBoundary
        public static PFunction createFunction(PythonContext context, String propertyName, Object target, Object closure) {
            PythonObjectFactory factory = PythonObjectFactory.getUncached();
            PCell[] pythonClosure = createPythonClosure(target, closure, factory);

            HPyGetSetDescriptorGetterRootNode rootNode = new HPyGetSetDescriptorGetterRootNode(context.getLanguage(), propertyName);
            PCode code = factory.createCode(PythonUtils.getOrCreateCallTarget(rootNode));
            return factory.createFunction(propertyName, "", code, context.getBuiltins(), pythonClosure);
        }

    }

    /**
     * A simple and lightweight Python root node that invokes a native setter function. The native
     * call target and the native closure pointer are passed as Python closure.
     */
    static final class HPyGetSetDescriptorSetterRootNode extends HPyGetSetDescriptorRootNode {
        static final Signature SIGNATURE = new Signature(-1, false, -1, false, new String[]{"$self", "value"}, null);

        HPyGetSetDescriptorSetterRootNode(PythonLanguage language, String name) {
            super(language, name);
        }

        @Override
        protected Object[] createArguments(VirtualFrame frame, Object closure) {
            return new Object[]{PArguments.getArgument(frame, 0), PArguments.getArgument(frame, 1), closure};
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }

        @TruffleBoundary
        public static PFunction createFunction(PythonContext context, String propertyName, Object target, Object closure) {
            HPyGetSetDescriptorSetterRootNode rootNode = new HPyGetSetDescriptorSetterRootNode(context.getLanguage(), propertyName);
            PythonObjectFactory factory = PythonObjectFactory.getUncached();
            PCell[] pythonClosure = createPythonClosure(target, closure, factory);
            PCode code = factory.createCode(PythonUtils.getOrCreateCallTarget(rootNode));
            return factory.createFunction(propertyName, "", code, context.getBuiltins(), pythonClosure);
        }
    }

    static final class HPyGetSetDescriptorNotWritableRootNode extends HPyGetSetDescriptorRootNode {

        @Child private PRaiseNode raiseNode;
        @Child private PythonObjectLibrary lib;
        @Child private GetNameNode getNameNode;

        HPyGetSetDescriptorNotWritableRootNode(PythonLanguage language, String name) {
            super(language, name);
        }

        @Override
        protected Object[] createArguments(VirtualFrame frame, Object closure) {
            if (raiseNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                raiseNode = insert(PRaiseNode.create());
            }
            if (lib == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lib = insert(PythonObjectLibrary.getFactory().createDispatched(1));
            }
            if (getNameNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getNameNode = insert(GetNameNode.create());
            }
            Object type = lib.getLazyPythonClass(PArguments.getArgument(frame, 0));
            throw raiseNode.raise(PythonBuiltinClassType.AttributeError, ErrorMessages.ATTR_S_OF_S_IS_NOT_WRITABLE, getName(), getNameNode.execute(type));
        }

        @Override
        public Signature getSignature() {
            return HPyGetSetDescriptorSetterRootNode.SIGNATURE;
        }

        @TruffleBoundary
        public static PFunction createFunction(PythonContext context, String propertyName) {
            HPyGetSetDescriptorNotWritableRootNode rootNode = new HPyGetSetDescriptorNotWritableRootNode(context.getLanguage(), propertyName);
            PythonObjectFactory factory = PythonObjectFactory.getUncached();
            PCode code = factory.createCode(PythonUtils.getOrCreateCallTarget(rootNode));
            // we don't need the closure
            return factory.createFunction(propertyName, "", code, context.getBuiltins(), PythonUtils.NO_CLOSURE);
        }
    }
}
