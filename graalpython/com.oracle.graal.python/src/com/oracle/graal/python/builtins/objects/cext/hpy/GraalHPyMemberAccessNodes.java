/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.common.CExtAsPythonObjectNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodesFactory.AsFixedNativePrimitiveNodeGen;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodesFactory.AsNativeBooleanNodeGen;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodesFactory.AsNativeCharNodeGen;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodesFactory.AsNativeDoubleNodeGen;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodesFactory.NativePrimitiveAsPythonBooleanNodeGen;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodesFactory.NativePrimitiveAsPythonCharNodeGen;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodesFactory.NativeUnsignedPrimitiveAsPythonObjectNodeGen;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodesFactory.StringAsPythonStringNodeGen;
import com.oracle.graal.python.builtins.objects.cext.common.CExtToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyMemberAccessNodesFactory.HPyBadMemberDescrNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyMemberAccessNodesFactory.HPyReadMemberNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyMemberAccessNodesFactory.HPyReadOnlyMemberNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyMemberAccessNodesFactory.HPyWriteMemberNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyFieldLoadNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyFieldStoreNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyGetNativeSpacePointerNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.PCallHPyFunction;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyFieldLoadNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyFieldStoreNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyGetNativeSpacePointerNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.PCallHPyFunctionNodeGen;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.DescriptorDeleteMarker;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.function.BuiltinFunctionRootNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.interop.PForeignToPTypeNode;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.GeneratedBy;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.strings.TruffleString;

public class GraalHPyMemberAccessNodes {

    static GraalHPyNativeSymbol getReadAccessorName(int type) {
        switch (type) {
            case HPY_MEMBER_SHORT:
                return GraalHPyNativeSymbol.GRAAL_HPY_READ_S;
            case HPY_MEMBER_INT:
                return GraalHPyNativeSymbol.GRAAL_HPY_READ_I;
            case HPY_MEMBER_LONG:
                return GraalHPyNativeSymbol.GRAAL_HPY_READ_L;
            case HPY_MEMBER_FLOAT:
                return GraalHPyNativeSymbol.GRAAL_HPY_READ_F;
            case HPY_MEMBER_DOUBLE:
                return GraalHPyNativeSymbol.GRAAL_HPY_READ_D;
            case HPY_MEMBER_STRING:
                return GraalHPyNativeSymbol.GRAAL_HPY_READ_STRING;
            case HPY_MEMBER_OBJECT:
            case HPY_MEMBER_OBJECT_EX:
                return GraalHPyNativeSymbol.GRAAL_HPY_READ_HPYFIELD;
            case HPY_MEMBER_CHAR:
            case HPY_MEMBER_BYTE:
            case HPY_MEMBER_BOOL:
                return GraalHPyNativeSymbol.GRAAL_HPY_READ_C;
            case HPY_MEMBER_UBYTE:
                return GraalHPyNativeSymbol.GRAAL_HPY_READ_UC;
            case HPY_MEMBER_USHORT:
                return GraalHPyNativeSymbol.GRAAL_HPY_READ_US;
            case HPY_MEMBER_UINT:
                return GraalHPyNativeSymbol.GRAAL_HPY_READ_UI;
            case HPY_MEMBER_ULONG:
                return GraalHPyNativeSymbol.GRAAL_HPY_READ_UL;
            case HPY_MEMBER_STRING_INPLACE:
                return GraalHPyNativeSymbol.GRAAL_HPY_READ_STRING_IN_PLACE;
            case HPY_MEMBER_LONGLONG:
                return GraalHPyNativeSymbol.GRAAL_HPY_READ_LL;
            case HPY_MEMBER_ULONGLONG:
                return GraalHPyNativeSymbol.GRAAL_HPY_READ_ULL;
            case HPY_MEMBER_HPYSSIZET:
                return GraalHPyNativeSymbol.GRAAL_HPY_READ_HPY_SSIZE_T;
            case HPY_MEMBER_NONE:
                return null;
        }
        throw CompilerDirectives.shouldNotReachHere("invalid member type");
    }

    static CExtAsPythonObjectNode getReadConverterNode(int type) {
        return switch (type) {
            // no conversion needed
            case HPY_MEMBER_SHORT, HPY_MEMBER_INT, HPY_MEMBER_LONG, HPY_MEMBER_FLOAT, HPY_MEMBER_DOUBLE, HPY_MEMBER_BYTE, HPY_MEMBER_UBYTE, HPY_MEMBER_USHORT, HPY_MEMBER_STRING_INPLACE,
                            HPY_MEMBER_HPYSSIZET, HPY_MEMBER_NONE, HPY_MEMBER_OBJECT, HPY_MEMBER_OBJECT_EX ->
                null;
            case HPY_MEMBER_STRING -> StringAsPythonStringNodeGen.create();
            case HPY_MEMBER_BOOL -> NativePrimitiveAsPythonBooleanNodeGen.create();
            case HPY_MEMBER_CHAR -> NativePrimitiveAsPythonCharNodeGen.create();
            case HPY_MEMBER_UINT, HPY_MEMBER_ULONG, HPY_MEMBER_LONGLONG, HPY_MEMBER_ULONGLONG -> NativeUnsignedPrimitiveAsPythonObjectNodeGen.create();
            default -> throw CompilerDirectives.shouldNotReachHere("invalid member type");
        };
    }

    static GraalHPyNativeSymbol getWriteAccessorName(int type) {
        return switch (type) {
            case HPY_MEMBER_SHORT -> GraalHPyNativeSymbol.GRAAL_HPY_WRITE_S;
            case HPY_MEMBER_INT -> GraalHPyNativeSymbol.GRAAL_HPY_WRITE_I;
            case HPY_MEMBER_LONG -> GraalHPyNativeSymbol.GRAAL_HPY_WRITE_L;
            case HPY_MEMBER_FLOAT -> GraalHPyNativeSymbol.GRAAL_HPY_WRITE_F;
            case HPY_MEMBER_DOUBLE -> GraalHPyNativeSymbol.GRAAL_HPY_WRITE_D;
            case HPY_MEMBER_NONE, HPY_MEMBER_STRING, HPY_MEMBER_STRING_INPLACE -> null;
            case HPY_MEMBER_OBJECT, HPY_MEMBER_OBJECT_EX -> GraalHPyNativeSymbol.GRAAL_HPY_WRITE_HPYFIELD;
            case HPY_MEMBER_CHAR, HPY_MEMBER_BYTE, HPY_MEMBER_BOOL -> GraalHPyNativeSymbol.GRAAL_HPY_WRITE_C;
            case HPY_MEMBER_UBYTE -> GraalHPyNativeSymbol.GRAAL_HPY_WRITE_UC;
            case HPY_MEMBER_USHORT -> GraalHPyNativeSymbol.GRAAL_HPY_WRITE_US;
            case HPY_MEMBER_UINT -> GraalHPyNativeSymbol.GRAAL_HPY_WRITE_UI;
            case HPY_MEMBER_ULONG -> GraalHPyNativeSymbol.GRAAL_HPY_WRITE_UL;
            case HPY_MEMBER_LONGLONG -> GraalHPyNativeSymbol.GRAAL_HPY_WRITE_LL;
            case HPY_MEMBER_ULONGLONG -> GraalHPyNativeSymbol.GRAAL_HPY_WRITE_ULL;
            case HPY_MEMBER_HPYSSIZET -> GraalHPyNativeSymbol.GRAAL_HPY_WRITE_HPY_SSIZE_T;
            default -> throw CompilerDirectives.shouldNotReachHere("invalid member type");
        };
    }

    static CExtToNativeNode getWriteConverterNode(int type) {
        return switch (type) {
            case HPY_MEMBER_CHAR -> AsNativeCharNodeGen.create();
            case HPY_MEMBER_BOOL -> AsNativeBooleanNodeGen.create();
            // TODO(fa): use appropriate native type sizes
            case HPY_MEMBER_SHORT, HPY_MEMBER_INT, HPY_MEMBER_BYTE -> AsFixedNativePrimitiveNodeGen.create(Integer.BYTES, true);
            // TODO(fa): use appropriate native type sizes
            case HPY_MEMBER_LONG, HPY_MEMBER_HPYSSIZET -> AsFixedNativePrimitiveNodeGen.create(Long.BYTES, true);
            case HPY_MEMBER_FLOAT, HPY_MEMBER_DOUBLE -> AsNativeDoubleNodeGen.create();
            // TODO(fa): use appropriate native type sizes
            case HPY_MEMBER_USHORT, HPY_MEMBER_UINT, HPY_MEMBER_UBYTE -> AsFixedNativePrimitiveNodeGen.create(Integer.BYTES, false);
            // TODO(fa): use appropriate native type sizes
            case HPY_MEMBER_ULONG, HPY_MEMBER_LONGLONG, HPY_MEMBER_ULONGLONG -> AsFixedNativePrimitiveNodeGen.create(Long.BYTES, false);
            case HPY_MEMBER_OBJECT, HPY_MEMBER_OBJECT_EX, HPY_MEMBER_NONE, HPY_MEMBER_STRING, HPY_MEMBER_STRING_INPLACE -> null;
            default -> throw CompilerDirectives.shouldNotReachHere("invalid member type");
        };
    }

    /**
     * Special case: members with type {@code STRING} and {@code STRING_INPLACE} are always
     * read-only.
     */
    static boolean isReadOnlyType(int type) {
        return type == HPY_MEMBER_STRING || type == HPY_MEMBER_STRING_INPLACE;
    }

    public static class HPyMemberNodeFactory<T extends PythonBuiltinBaseNode> implements NodeFactory<T> {
        private final T node;

        public HPyMemberNodeFactory(T node) {
            this.node = node;
        }

        @Override
        public T createNode(Object... arguments) {
            return NodeUtil.cloneNode(node);
        }

        @Override
        public Class<T> getNodeClass() {
            return determineNodeClass(node);
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

    @Builtin(name = "hpy_member_read", minNumOfPositionalArgs = 1, parameterNames = "$self")
    protected abstract static class HPyReadMemberNode extends PythonUnaryBuiltinNode {
        private static final Builtin builtin = HPyReadMemberNode.class.getAnnotation(Builtin.class);

        @Child private PCallHPyFunction callHPyFunctionNode;
        @Child private CExtAsPythonObjectNode asPythonObjectNode;
        @Child private PForeignToPTypeNode fromForeign;
        @Child private HPyGetNativeSpacePointerNode readNativeSpaceNode;
        @Child private HPyFieldLoadNode hPyFieldLoadNode;

        /** The name of the native getter function. */
        private final GraalHPyNativeSymbol accessor;

        /** The specified member type. */
        private final int type;

        /** The offset where to read from (will be passed to the native getter). */
        private final int offset;

        protected HPyReadMemberNode(GraalHPyNativeSymbol accessor, int offset, int type, CExtAsPythonObjectNode asPythonObjectNode) {
            this.accessor = accessor;
            this.offset = offset;
            this.type = type;
            if (type == HPY_MEMBER_OBJECT || type == HPY_MEMBER_OBJECT_EX) {
                this.hPyFieldLoadNode = HPyFieldLoadNodeGen.create();
            }
            this.asPythonObjectNode = asPythonObjectNode;
            if (asPythonObjectNode == null) {
                fromForeign = PForeignToPTypeNode.create();
            }
        }

        @Specialization
        Object doGeneric(@SuppressWarnings("unused") VirtualFrame frame, Object self) {
            GraalHPyContext hPyContext = getContext().getHPyContext();

            Object nativeSpacePtr = ensureReadNativeSpaceNode().execute(self);
            if (nativeSpacePtr == PNone.NO_VALUE) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raise(PythonBuiltinClassType.SystemError, ErrorMessages.ATTEMPTING_READ_FROM_OFFSET_D, offset, self);
            }

            Object nativeResult = PNone.NONE;
            if (accessor != null) {
                // This will call pure C functions that won't ever access the Python stack nor the
                // exception state. So, we don't need to setup an indirect call.
                nativeResult = ensureCallHPyFunctionNode().call(hPyContext, accessor, nativeSpacePtr, (long) offset);
                if (hPyFieldLoadNode != null) {
                    assert type == HPY_MEMBER_OBJECT || type == HPY_MEMBER_OBJECT_EX;
                    if (self instanceof PythonObject pythonObject) {
                        Object loadedFieldValue = hPyFieldLoadNode.execute(this, pythonObject, nativeResult);
                        if (loadedFieldValue == GraalHPyHandle.NULL_HANDLE_DELEGATE) {
                            if (type == HPY_MEMBER_OBJECT) {
                                return PNone.NONE;
                            } else {
                                throw raise(PythonBuiltinClassType.AttributeError);
                            }
                        }
                        return loadedFieldValue;
                    } else {
                        throw CompilerDirectives.shouldNotReachHere("Cannot have HPyField on non-Python object");
                    }

                }
                if (asPythonObjectNode != null) {
                    return asPythonObjectNode.execute(nativeResult);
                }
            }
            // We still need to use 'PForeignToPTypeNode' to ensure that we do not introduce unknown
            // values into our value space.
            return fromForeign.executeConvert(nativeResult);
        }

        private PCallHPyFunction ensureCallHPyFunctionNode() {
            if (callHPyFunctionNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callHPyFunctionNode = insert(PCallHPyFunctionNodeGen.create());
            }
            return callHPyFunctionNode;
        }

        private HPyGetNativeSpacePointerNode ensureReadNativeSpaceNode() {
            if (readNativeSpaceNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readNativeSpaceNode = insert(HPyGetNativeSpacePointerNodeGen.create());
            }
            return readNativeSpaceNode;
        }

        @TruffleBoundary
        public static PBuiltinFunction createBuiltinFunction(PythonLanguage language, TruffleString propertyName, int type, int offset) {
            GraalHPyNativeSymbol accessor = getReadAccessorName(type);
            CExtAsPythonObjectNode asPythonObjectNode = getReadConverterNode(type);
            RootCallTarget callTarget = language.createCachedCallTarget(
                            l -> new BuiltinFunctionRootNode(l, builtin, new HPyMemberNodeFactory<>(HPyReadMemberNodeGen.create(accessor, offset, type, asPythonObjectNode)), true),
                            HPyReadMemberNode.class, builtin.name(), type, offset);
            int flags = PBuiltinFunction.getFlags(builtin, callTarget);
            return PythonObjectFactory.getUncached().createBuiltinFunction(propertyName, null, 0, flags, callTarget);
        }
    }

    @Builtin(name = "hpy_member_write_read_only", minNumOfPositionalArgs = 1, parameterNames = {"$self", "value"})
    protected abstract static class HPyReadOnlyMemberNode extends PythonBinaryBuiltinNode {
        private static final Builtin builtin = HPyReadOnlyMemberNode.class.getAnnotation(Builtin.class);
        private final TruffleString propertyName;

        protected HPyReadOnlyMemberNode(TruffleString propertyName) {
            this.propertyName = propertyName;
        }

        @Specialization
        @SuppressWarnings("unused")
        Object doGeneric(Object self, Object value) {
            throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.ATTRIBUTE_S_OF_P_OBJECTS_IS_NOT_WRITABLE, propertyName, self);
        }

        @TruffleBoundary
        public static PBuiltinFunction createBuiltinFunction(PythonLanguage language, TruffleString propertyName) {
            RootCallTarget builtinCt = language.createCachedCallTarget(l -> new BuiltinFunctionRootNode(l, builtin, new HPyMemberNodeFactory<>(HPyReadOnlyMemberNodeGen.create(propertyName)), true),
                            GraalHPyMemberAccessNodes.class, builtin.name());
            int flags = PBuiltinFunction.getFlags(builtin, builtinCt);
            return PythonObjectFactory.getUncached().createBuiltinFunction(propertyName, null, 0, flags, builtinCt);
        }
    }

    @Builtin(name = "hpy_bad_member_descr", minNumOfPositionalArgs = 2, parameterNames = {"$self", "value"})
    protected abstract static class HPyBadMemberDescrNode extends PythonBinaryBuiltinNode {
        private static final Builtin builtin = HPyBadMemberDescrNode.class.getAnnotation(Builtin.class);

        @Specialization
        Object doGeneric(Object self, @SuppressWarnings("unused") Object value) {
            if (value == DescriptorDeleteMarker.INSTANCE) {
                // This node is actually only used for T_NONE, so this error message is right.
                throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.CAN_T_DELETE_NUMERIC_CHAR_ATTRIBUTE);
            }
            throw raise(PythonBuiltinClassType.SystemError, ErrorMessages.BAD_MEMBER_DESCR_TYPE_FOR_P, self);
        }

        @TruffleBoundary
        public static PBuiltinFunction createBuiltinFunction(PythonLanguage language, TruffleString propertyName) {
            RootCallTarget builtinCt = language.createCachedCallTarget(l -> new BuiltinFunctionRootNode(l, builtin, new HPyMemberNodeFactory<>(HPyBadMemberDescrNodeGen.create()), true),
                            HPyBadMemberDescrNode.class, builtin);
            int flags = PBuiltinFunction.getFlags(builtin, builtinCt);
            return PythonObjectFactory.getUncached().createBuiltinFunction(propertyName, null, 0, flags, builtinCt);
        }
    }

    @Builtin(name = "hpy_write_member", minNumOfPositionalArgs = 2, parameterNames = {"$self", "value"})
    protected abstract static class HPyWriteMemberNode extends PythonBinaryBuiltinNode {
        private static final Builtin builtin = HPyWriteMemberNode.class.getAnnotation(Builtin.class);

        @Child private PCallHPyFunction callHPyFunctionNode;
        @Child private CExtToNativeNode toNativeNode;
        @Child private HPyGetNativeSpacePointerNode readNativeSpaceNode;
        @Child private InteropLibrary resultLib;
        @Child private HPyFieldStoreNode hpyFieldStoreNode;

        /** The specified member type. */
        private final int type;

        /** The offset where to read from (will be passed to the native getter). */
        private final int offset;

        protected HPyWriteMemberNode(int type, int offset) {
            this.type = type;
            this.offset = offset;
            this.toNativeNode = getWriteConverterNode(type);
            if (type == HPY_MEMBER_OBJECT || type == HPY_MEMBER_OBJECT_EX) {
                this.hpyFieldStoreNode = HPyFieldStoreNodeGen.create();
            }
        }

        @Specialization
        Object doGeneric(VirtualFrame frame, Object self, Object value) {
            PythonContext context = getContext();
            GraalHPyContext hPyContext = context.getHPyContext();

            Object nativeSpacePtr = ensureReadNativeSpaceNode().execute(self);
            if (nativeSpacePtr == PNone.NO_VALUE) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raise(PythonBuiltinClassType.SystemError, ErrorMessages.ATTEMPTING_WRITE_OFFSET_D, offset, self);
            }

            /*
             * Deleting values is only allowed for members with object type (see structmember.c:
             * PyMember_SetOne).
             */
            Object newValue;
            if (value == DescriptorDeleteMarker.INSTANCE) {
                if (type == HPY_MEMBER_OBJECT_EX) {
                    if (resultLib == null) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        resultLib = insert(InteropLibrary.getFactory().createDispatched(2));
                    }
                    /*
                     * This will call pure C functions that won't ever access the Python stack nor
                     * the exception state. So, we don't need to setup an indirect call.
                     */
                    Object oldValue = ensureCallHPyFunctionNode().call(hPyContext, getReadAccessorName(type), nativeSpacePtr, (long) offset);
                    if (resultLib.isNull(oldValue)) {
                        throw raise(PythonBuiltinClassType.AttributeError);
                    }
                } else if (type != HPY_MEMBER_OBJECT) {
                    throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.CAN_T_DELETE_NUMERIC_CHAR_ATTRIBUTE);
                }
                // NO_VALUE will be converted to the NULL handle
                newValue = PNone.NO_VALUE;
            } else {
                newValue = value;
            }

            GraalHPyNativeSymbol accessor = getWriteAccessorName(type);
            if (accessor != null) {
                // convert value if needed
                Object nativeValue;
                /*
                 * Writing an object is special because we store the object into an HPyField. We
                 * first need to read the old value to get the numeric value of the HPyField (if it
                 * has been initialized already).
                 */
                if (hpyFieldStoreNode != null) {
                    assert type == HPY_MEMBER_OBJECT || type == HPY_MEMBER_OBJECT_EX;
                    if (self instanceof PythonObject pythonObject) {
                        Object oldValue = ensureCallHPyFunctionNode().call(hPyContext, getReadAccessorName(type), nativeSpacePtr, (long) offset);
                        int fieldIdx = hpyFieldStoreNode.execute(this, pythonObject, oldValue, newValue);
                        nativeValue = GraalHPyHandle.createField(newValue, fieldIdx);
                    } else {
                        throw CompilerDirectives.shouldNotReachHere("Cannot have HPyField on non-Python object");
                    }
                } else if (toNativeNode != null) {
                    // The conversion to a native primitive may call arbitrary user code. So we need
                    // to prepare an indirect call.
                    Object savedState = IndirectCallContext.enter(frame, getLanguage(), context, this);
                    try {
                        nativeValue = toNativeNode.execute(newValue);
                    } finally {
                        IndirectCallContext.exit(frame, getLanguage(), context, savedState);
                    }
                } else {
                    nativeValue = newValue;
                }

                // This will call pure C functions that won't ever access the Python stack nor the
                // exception state. So, we don't need to setup an indirect call.
                ensureCallHPyFunctionNode().call(hPyContext, accessor, nativeSpacePtr, (long) offset, nativeValue);
            }
            return PNone.NONE;
        }

        private PCallHPyFunction ensureCallHPyFunctionNode() {
            if (callHPyFunctionNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callHPyFunctionNode = insert(PCallHPyFunctionNodeGen.create());
            }
            return callHPyFunctionNode;
        }

        private HPyGetNativeSpacePointerNode ensureReadNativeSpaceNode() {
            if (readNativeSpaceNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readNativeSpaceNode = insert(HPyGetNativeSpacePointerNodeGen.create());
            }
            return readNativeSpaceNode;
        }

        @TruffleBoundary
        public static PBuiltinFunction createBuiltinFunction(PythonLanguage language, TruffleString propertyName, int type, int offset) {
            GraalHPyNativeSymbol accessor = getWriteAccessorName(type);
            if (accessor == null) {
                if (isReadOnlyType(type)) {
                    return HPyReadOnlyMemberNode.createBuiltinFunction(language, propertyName);
                }
                return HPyBadMemberDescrNode.createBuiltinFunction(language, propertyName);
            }
            //
            RootCallTarget callTarget = language.createCachedCallTarget(
                            l -> new BuiltinFunctionRootNode(l, builtin, new HPyMemberNodeFactory<>(HPyWriteMemberNodeGen.create(type, offset)), true),
                            HPyWriteMemberNode.class, builtin.name(), type, offset);
            int flags = PBuiltinFunction.getFlags(builtin, callTarget);
            return PythonObjectFactory.getUncached().createBuiltinFunction(propertyName, null, 0, flags, callTarget);
        }
    }

}
