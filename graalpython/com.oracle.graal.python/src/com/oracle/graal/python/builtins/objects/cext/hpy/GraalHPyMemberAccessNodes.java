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
import static com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType.Bool;
import static com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType.CDouble;
import static com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType.CFloat;
import static com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType.Int;
import static com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType.Int16_t;
import static com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType.Int64_t;
import static com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType.Int8_t;
import static com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType.Uint16_t;
import static com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType.Uint8_t;
import static com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType.UnsignedInt;
import static com.oracle.graal.python.builtins.objects.cext.hpy.HPyContextSignatureType.UnsignedLong;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.common.CExtAsPythonObjectNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.AsNativeCharNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.AsNativePrimitiveNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodesFactory.AsNativeCharNodeGen;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodesFactory.AsNativePrimitiveNodeGen;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodesFactory.NativePrimitiveAsPythonBooleanNodeGen;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodesFactory.NativePrimitiveAsPythonCharNodeGen;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodesFactory.NativeUnsignedPrimitiveAsPythonObjectNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyMemberAccessNodesFactory.HPyBadMemberDescrNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyMemberAccessNodesFactory.HPyReadMemberNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyMemberAccessNodesFactory.HPyReadOnlyMemberNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyMemberAccessNodesFactory.HPyWriteMemberNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyMemberAccessNodesFactory.PyFloatAsDoubleCachedNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyFromCharPointerNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodes.HPyGetNativeSpacePointerNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyGetNativeSpacePointerNodeGen;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.DescriptorDeleteMarker;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.lib.PyFloatAsDoubleNode;
import com.oracle.graal.python.lib.PyLongAsLongNode;
import com.oracle.graal.python.lib.PyLongAsLongNodeGen;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.BuiltinFunctionRootNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.interop.PForeignToPTypeNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.IsNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GeneratedBy;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.strings.TruffleString;

public class GraalHPyMemberAccessNodes {

    static HPyContextSignatureType getCType(int type) {
        switch (type) {
            case HPY_MEMBER_SHORT:
                return HPyContextSignatureType.Int16_t;
            case HPY_MEMBER_INT:
                return HPyContextSignatureType.Int;
            case HPY_MEMBER_LONG:
                return HPyContextSignatureType.Long;
            case HPY_MEMBER_FLOAT:
                return HPyContextSignatureType.CFloat;
            case HPY_MEMBER_DOUBLE:
                return HPyContextSignatureType.CDouble;
            case HPY_MEMBER_STRING:
                return HPyContextSignatureType.CharPtr;
            case HPY_MEMBER_OBJECT:
            case HPY_MEMBER_OBJECT_EX:
                return HPyContextSignatureType.HPyField;
            case HPY_MEMBER_CHAR:
            case HPY_MEMBER_BYTE:
                return HPyContextSignatureType.Int8_t;
            case HPY_MEMBER_BOOL:
                return HPyContextSignatureType.Bool;
            case HPY_MEMBER_UBYTE:
                return HPyContextSignatureType.Uint8_t;
            case HPY_MEMBER_USHORT:
                return HPyContextSignatureType.Uint16_t;
            case HPY_MEMBER_UINT:
                return HPyContextSignatureType.UnsignedInt;
            case HPY_MEMBER_ULONG:
                return HPyContextSignatureType.UnsignedLong;
            case HPY_MEMBER_STRING_INPLACE, HPY_MEMBER_NONE:
                return null;
            case HPY_MEMBER_LONGLONG:
                return HPyContextSignatureType.Uint64_t;
            case HPY_MEMBER_ULONGLONG:
                return HPyContextSignatureType.Int64_t;
            case HPY_MEMBER_HPYSSIZET:
                return HPyContextSignatureType.HPy_ssize_t;
        }
        throw CompilerDirectives.shouldNotReachHere("invalid member type");
    }

    static CExtAsPythonObjectNode getReadConverterNode(int type) {
        return switch (type) {
            // no conversion needed
            case HPY_MEMBER_SHORT, HPY_MEMBER_INT, HPY_MEMBER_LONG, HPY_MEMBER_FLOAT, HPY_MEMBER_DOUBLE, HPY_MEMBER_BYTE, HPY_MEMBER_UBYTE, HPY_MEMBER_USHORT, HPY_MEMBER_STRING,
                            HPY_MEMBER_STRING_INPLACE, HPY_MEMBER_HPYSSIZET, HPY_MEMBER_NONE, HPY_MEMBER_OBJECT, HPY_MEMBER_OBJECT_EX -> null;
            case HPY_MEMBER_BOOL -> NativePrimitiveAsPythonBooleanNodeGen.create();
            case HPY_MEMBER_CHAR -> NativePrimitiveAsPythonCharNodeGen.create();
            case HPY_MEMBER_UINT, HPY_MEMBER_ULONG, HPY_MEMBER_LONGLONG, HPY_MEMBER_ULONGLONG -> NativeUnsignedPrimitiveAsPythonObjectNodeGen.create();
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

        @Child private GraalHPyCAccess.ReadGenericNode readGenericNode;
        @Child private GraalHPyCAccess.ReadHPyFieldNode readHPyFieldNode;
        @Child private GraalHPyCAccess.GetElementPtrNode getElementPtrNode;
        @Child private GraalHPyCAccess.IsNullNode isNullNode;
        @Child private HPyFromCharPointerNode fromCharPointerNode;
        @Child private CExtAsPythonObjectNode asPythonObjectNode;
        @Child private PForeignToPTypeNode fromForeign;
        @Child private HPyGetNativeSpacePointerNode readNativeSpaceNode;

        /** The specified member type. */
        private final int type;

        /** The name of the native getter function. */
        private final HPyContextSignatureType fieldType;

        /** The offset where to read from (will be passed to the native getter). */
        private final int offset;

        protected HPyReadMemberNode(int offset, int type, CExtAsPythonObjectNode asPythonObjectNode) {
            this.fieldType = getCType(type);
            this.offset = offset;
            this.type = type;
            this.asPythonObjectNode = asPythonObjectNode;
            if (asPythonObjectNode == null) {
                fromForeign = PForeignToPTypeNode.create();
            }
        }

        @Specialization
        Object doGeneric(@SuppressWarnings("unused") VirtualFrame frame, Object self,
                        @Bind("this") Node inliningTarget,
                        @Cached PRaiseNode.Lazy raiseNode) {
            GraalHPyContext hPyContext = getContext().getHPyContext();

            Object nativeSpacePtr = ensureReadNativeSpaceNode().executeCached(self);
            if (nativeSpacePtr == PNone.NO_VALUE) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.SystemError, ErrorMessages.ATTEMPTING_READ_FROM_OFFSET_D, offset, self);
            }
            Object nativeResult;
            switch (type) {
                case HPY_MEMBER_OBJECT, HPY_MEMBER_OBJECT_EX: {
                    if (self instanceof PythonObject pythonObject) {
                        Object fieldValue = ensureReadHPyFieldNode(hPyContext).read(hPyContext, pythonObject, nativeSpacePtr, offset);
                        if (fieldValue == GraalHPyHandle.NULL_HANDLE_DELEGATE) {
                            if (type == HPY_MEMBER_OBJECT) {
                                return PNone.NONE;
                            } else {
                                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.AttributeError);
                            }
                        }
                        return fieldValue;
                    } else {
                        throw CompilerDirectives.shouldNotReachHere("Cannot have HPyField on non-Python object");
                    }
                }
                case HPY_MEMBER_NONE:
                    return PNone.NONE;
                case HPY_MEMBER_STRING:
                    nativeResult = ensureReadGenericNode(hPyContext).execute(hPyContext, nativeSpacePtr, offset, fieldType);
                    if (ensureIsNullNode(hPyContext).execute(hPyContext, nativeResult)) {
                        return PNone.NONE;
                    }
                    return ensureFromCharPointerNode(hPyContext).execute(hPyContext, nativeResult, false);
                case HPY_MEMBER_STRING_INPLACE:
                    Object elementPtr = ensureGetElementPtrNode(hPyContext).execute(hPyContext, nativeSpacePtr, offset);
                    return ensureFromCharPointerNode(hPyContext).execute(hPyContext, elementPtr, false);
                default:
                    // default case: reading a primitive or a pointer
                    assert fieldType != null;
                    nativeResult = ensureReadGenericNode(hPyContext).execute(hPyContext, nativeSpacePtr, offset, fieldType);
                    if (asPythonObjectNode != null) {
                        return asPythonObjectNode.execute(nativeResult);
                    }
                    /*
                     * We still need to use 'PForeignToPTypeNode' to ensure that we do not introduce
                     * unknown values into our value space.
                     */
                    return fromForeign.executeConvert(nativeResult);
            }
        }

        private GraalHPyCAccess.ReadGenericNode ensureReadGenericNode(GraalHPyContext ctx) {
            if (readGenericNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readGenericNode = insert(GraalHPyCAccess.ReadGenericNode.create(ctx));
            }
            return readGenericNode;
        }

        private GraalHPyCAccess.IsNullNode ensureIsNullNode(GraalHPyContext ctx) {
            if (isNullNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isNullNode = insert(GraalHPyCAccess.IsNullNode.create(ctx));
            }
            return isNullNode;
        }

        private GraalHPyCAccess.ReadHPyFieldNode ensureReadHPyFieldNode(GraalHPyContext ctx) {
            if (readHPyFieldNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readHPyFieldNode = insert(GraalHPyCAccess.ReadHPyFieldNode.create(ctx));
            }
            return readHPyFieldNode;
        }

        private GraalHPyCAccess.GetElementPtrNode ensureGetElementPtrNode(GraalHPyContext ctx) {
            if (getElementPtrNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getElementPtrNode = insert(GraalHPyCAccess.GetElementPtrNode.create(ctx));
            }
            return getElementPtrNode;
        }

        private HPyFromCharPointerNode ensureFromCharPointerNode(GraalHPyContext ctx) {
            if (fromCharPointerNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                fromCharPointerNode = insert(HPyFromCharPointerNode.create(ctx));
            }
            return fromCharPointerNode;
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
            CExtAsPythonObjectNode asPythonObjectNode = getReadConverterNode(type);
            RootCallTarget callTarget = language.createCachedCallTarget(
                            l -> new BuiltinFunctionRootNode(l, builtin, new HPyMemberNodeFactory<>(HPyReadMemberNodeGen.create(offset, type, asPythonObjectNode)), true),
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
        Object doGeneric(Object self, Object value,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(PythonBuiltinClassType.TypeError, ErrorMessages.ATTRIBUTE_S_OF_P_OBJECTS_IS_NOT_WRITABLE, propertyName, self);
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
        static Object doGeneric(Object self, @SuppressWarnings("unused") Object value,
                        @Cached PRaiseNode raiseNode) {
            if (value == DescriptorDeleteMarker.INSTANCE) {
                // This node is actually only used for T_NONE, so this error message is right.
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, ErrorMessages.CAN_T_DELETE_NUMERIC_CHAR_ATTRIBUTE);
            }
            throw raiseNode.raise(PythonBuiltinClassType.SystemError, ErrorMessages.BAD_MEMBER_DESCR_TYPE_FOR_P, self);
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

        @Child private AsNativeCharNode asNativeCharNode;
        @Child private PyLongAsLongNode pyLongAsLongNode;
        @Child private PyFloatAsDoubleCachedNode pyFloatAsDoubleNode;
        @Child private AsNativePrimitiveNode asNativePrimitiveNode;
        @Child private IsBuiltinObjectProfile isBuiltinObjectProfile;
        @Child private IsNode isNode;
        @Child private GraalHPyCAccess.ReadHPyFieldNode readHPyFieldNode;
        @Child private GraalHPyCAccess.WriteHPyFieldNode writeHPyFieldNode;
        @Child private GraalHPyCAccess.WriteGenericNode writeGenericNode;
        @Child private HPyGetNativeSpacePointerNode readNativeSpaceNode;

        /** The specified member type. */
        private final int type;

        /** The offset where to read from (will be passed to the native getter). */
        private final int offset;

        protected HPyWriteMemberNode(int type, int offset) {
            this.type = type;
            this.offset = offset;
        }

        @Specialization
        Object doGeneric(VirtualFrame frame, Object self, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached PRaiseNode.Lazy raiseNode) {
            PythonContext context = getContext();
            GraalHPyContext hPyContext = context.getHPyContext();

            Object nativeSpacePtr = ensureReadNativeSpaceNode().executeCached(self);
            if (nativeSpacePtr == PNone.NO_VALUE) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw PRaiseNode.raiseUncached(this, PythonBuiltinClassType.SystemError, ErrorMessages.ATTEMPTING_WRITE_OFFSET_D, offset, self);
            }

            /*
             * Deleting values is only allowed for members with object type (see structmember.c:
             * PyMember_SetOne).
             */
            Object newValue;
            if (value == DescriptorDeleteMarker.INSTANCE) {
                if (type == HPY_MEMBER_OBJECT_EX) {
                    if (self instanceof PythonObject pythonObject) {
                        Object oldValue = ensureReadHPyFieldNode(hPyContext).read(hPyContext, pythonObject, nativeSpacePtr, offset);
                        if (oldValue == PNone.NO_VALUE) {
                            throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.AttributeError);
                        }
                    } else {
                        throw CompilerDirectives.shouldNotReachHere("Cannot have HPyField on non-Python object");
                    }
                } else if (type != HPY_MEMBER_OBJECT) {
                    throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, ErrorMessages.CAN_T_DELETE_NUMERIC_CHAR_ATTRIBUTE);
                }
                // NO_VALUE will be converted to the NULL handle
                newValue = PNone.NO_VALUE;
            } else {
                newValue = value;
            }

            long val;
            switch (type) {
                case HPY_MEMBER_SHORT:
                    val = ensurePyLongAsLongNode().executeCached(frame, newValue);
                    ensureWriteGenericNode(hPyContext).execute(hPyContext, nativeSpacePtr, offset, Int16_t, val);
                    // TODO(fa): truncation warning
                    // if ((long_val > SHRT_MAX) || (long_val < SHRT_MIN))
                    // WARN("Truncation of value to short");
                    break;
                case HPY_MEMBER_INT:
                    val = ensurePyLongAsLongNode().executeCached(frame, newValue);
                    ensureWriteGenericNode(hPyContext).execute(hPyContext, nativeSpacePtr, offset, Int, val);
                    // TODO(fa): truncation warning
                    // if ((long_val > INT_MAX) || (long_val < INT_MIN))
                    // WARN("Truncation of value to int");
                    break;
                case HPY_MEMBER_LONG:
                    val = ensurePyLongAsLongNode().executeCached(frame, newValue);
                    ensureWriteGenericNode(hPyContext).execute(hPyContext, nativeSpacePtr, offset, HPyContextSignatureType.Long, val);
                    break;
                case HPY_MEMBER_FLOAT: {
                    float fvalue = (float) ensurePyFloatAsDoubleNode().execute(frame, newValue);
                    ensureWriteGenericNode(hPyContext).execute(hPyContext, nativeSpacePtr, offset, CFloat, fvalue);
                    break;
                }
                case HPY_MEMBER_DOUBLE: {
                    double dvalue = ensurePyFloatAsDoubleNode().execute(frame, newValue);
                    ensureWriteGenericNode(hPyContext).execute(hPyContext, nativeSpacePtr, offset, CDouble, dvalue);
                    break;
                }
                case HPY_MEMBER_STRING, HPY_MEMBER_STRING_INPLACE:
                    /*
                     * This node is never created for string members because they are not writeable
                     * and we create HPyReadOnlyMemberNode for those.
                     */
                    throw CompilerDirectives.shouldNotReachHere();
                case HPY_MEMBER_OBJECT, HPY_MEMBER_OBJECT_EX:
                    if (self instanceof PythonObject pythonObject) {
                        ensureWriteHPyFieldNode(hPyContext).execute(hPyContext, pythonObject, nativeSpacePtr, offset, newValue);
                    } else {
                        throw CompilerDirectives.shouldNotReachHere("Cannot have HPyField on non-Python object");
                    }
                    break;
                case HPY_MEMBER_CHAR:
                    val = ensureAsNativeCharNode().executeByte(newValue);
                    ensureWriteGenericNode(hPyContext).execute(hPyContext, nativeSpacePtr, offset, Int8_t, val);
                    break;
                case HPY_MEMBER_BYTE:
                    val = ensurePyLongAsLongNode().executeCached(frame, newValue);
                    ensureWriteGenericNode(hPyContext).execute(hPyContext, nativeSpacePtr, offset, Int8_t, val);
                    // TODO(fa): truncation warning
                    // if ((long_val > CHAR_MAX) || (long_val < CHAR_MIN))
                    // WARN("Truncation of value to char");
                    break;
                case HPY_MEMBER_UBYTE:
                    val = ensurePyLongAsLongNode().executeCached(frame, newValue);
                    ensureWriteGenericNode(hPyContext).execute(hPyContext, nativeSpacePtr, offset, Uint8_t, val);
                    // TODO(fa): truncation warning
                    // if ((long_val > UCHAR_MAX) || (long_val < 0))
                    // WARN("Truncation of value to unsigned char");
                    break;
                case HPY_MEMBER_USHORT:
                    val = ensurePyLongAsLongNode().executeCached(frame, newValue);
                    ensureWriteGenericNode(hPyContext).execute(hPyContext, nativeSpacePtr, offset, Uint16_t, val);
                    // TODO(fa): truncation warning
                    // if ((long_val > USHRT_MAX) || (long_val < 0))
                    // WARN("Truncation of value to unsigned short");
                    break;
                case HPY_MEMBER_UINT: {
                    val = ensurePyLongAsLongNode().executeCached(frame, newValue);
                    Object uint = ensureAsNativePrimitiveNode().execute(val, 0, hPyContext.getCTypeSize(UnsignedInt), true);
                    ensureWriteGenericNode(hPyContext).execute(hPyContext, nativeSpacePtr, offset, UnsignedInt, uint);
                    break;
                }
                case HPY_MEMBER_ULONG: {
                    val = ensurePyLongAsLongNode().executeCached(frame, newValue);
                    Object ulong = ensureAsNativePrimitiveNode().execute(val, 0, hPyContext.getCTypeSize(UnsignedLong), true);
                    ensureWriteGenericNode(hPyContext).execute(hPyContext, nativeSpacePtr, offset, UnsignedLong, ulong);
                    break;
                }
                case HPY_MEMBER_BOOL:
                    // note: exact type check is sufficient; bool cannot be subclassed
                    if (!ensureIsBuiltinObjectProfile().profileObject(this, newValue, PythonBuiltinClassType.Boolean)) {
                        throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, ErrorMessages.ATTR_VALUE_MUST_BE_BOOL);
                    }
                    val = ensureIsNode().isTrue(newValue) ? 1 : 0;
                    ensureWriteGenericNode(hPyContext).execute(hPyContext, nativeSpacePtr, offset, Bool, val);
                    break;
                case HPY_MEMBER_LONGLONG:
                    val = ensurePyLongAsLongNode().executeCached(frame, newValue);
                    ensureWriteGenericNode(hPyContext).execute(hPyContext, nativeSpacePtr, offset, Int64_t, val);
                    break;
                case HPY_MEMBER_ULONGLONG:
                    val = ensurePyLongAsLongNode().executeCached(frame, newValue);
                    val = (long) ensureAsNativePrimitiveNode().execute(val, 0, 8, true);
                    ensureWriteGenericNode(hPyContext).execute(hPyContext, nativeSpacePtr, offset, Int64_t, val);
                    break;
                case HPY_MEMBER_HPYSSIZET:
                    // TODO(fa): PyLongAsLong is not correct
                    val = ensurePyLongAsLongNode().executeCached(frame, newValue);
                    ensureWriteGenericNode(hPyContext).execute(hPyContext, nativeSpacePtr, offset, HPyContextSignatureType.HPy_ssize_t, val);
                    break;
                default:
                    throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.SystemError, ErrorMessages.BAD_MEMBER_DESCR_TYPE_FOR_S, "");
            }
            return PNone.NONE;
        }

        private GraalHPyCAccess.ReadHPyFieldNode ensureReadHPyFieldNode(GraalHPyContext ctx) {
            if (readHPyFieldNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readHPyFieldNode = insert(GraalHPyCAccess.ReadHPyFieldNode.create(ctx));
            }
            return readHPyFieldNode;
        }

        private GraalHPyCAccess.WriteHPyFieldNode ensureWriteHPyFieldNode(GraalHPyContext ctx) {
            if (writeHPyFieldNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                writeHPyFieldNode = insert(GraalHPyCAccess.WriteHPyFieldNode.create(ctx));
            }
            return writeHPyFieldNode;
        }

        private GraalHPyCAccess.WriteGenericNode ensureWriteGenericNode(GraalHPyContext ctx) {
            if (writeGenericNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                writeGenericNode = insert(GraalHPyCAccess.WriteGenericNode.create(ctx));
            }
            return writeGenericNode;
        }

        private HPyGetNativeSpacePointerNode ensureReadNativeSpaceNode() {
            if (readNativeSpaceNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readNativeSpaceNode = insert(HPyGetNativeSpacePointerNodeGen.create());
            }
            return readNativeSpaceNode;
        }

        private PyLongAsLongNode ensurePyLongAsLongNode() {
            if (pyLongAsLongNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                pyLongAsLongNode = insert(PyLongAsLongNodeGen.create());
            }
            return pyLongAsLongNode;
        }

        private PyFloatAsDoubleCachedNode ensurePyFloatAsDoubleNode() {
            if (pyFloatAsDoubleNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                pyFloatAsDoubleNode = insert(PyFloatAsDoubleCachedNodeGen.create());
            }
            return pyFloatAsDoubleNode;
        }

        private AsNativePrimitiveNode ensureAsNativePrimitiveNode() {
            if (asNativePrimitiveNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                asNativePrimitiveNode = insert(AsNativePrimitiveNodeGen.create());
            }
            return asNativePrimitiveNode;
        }

        private IsBuiltinObjectProfile ensureIsBuiltinObjectProfile() {
            if (isBuiltinObjectProfile == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isBuiltinObjectProfile = insert(IsBuiltinObjectProfile.create());
            }
            return isBuiltinObjectProfile;
        }

        private IsNode ensureIsNode() {
            if (isNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isNode = insert(IsNode.create());
            }
            return isNode;
        }

        private AsNativeCharNode ensureAsNativeCharNode() {
            if (asNativeCharNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                asNativeCharNode = insert(AsNativeCharNodeGen.create());
            }
            return asNativeCharNode;
        }

        @TruffleBoundary
        public static PBuiltinFunction createBuiltinFunction(PythonLanguage language, TruffleString propertyName, int type, int offset) {
            if (isReadOnlyType(type)) {
                return HPyReadOnlyMemberNode.createBuiltinFunction(language, propertyName);
            }
            if (type == HPY_MEMBER_NONE) {
                return HPyBadMemberDescrNode.createBuiltinFunction(language, propertyName);
            }
            RootCallTarget callTarget = language.createCachedCallTarget(
                            l -> new BuiltinFunctionRootNode(l, builtin, new HPyMemberNodeFactory<>(HPyWriteMemberNodeGen.create(type, offset)), true),
                            HPyWriteMemberNode.class, builtin.name(), type, offset);
            int flags = PBuiltinFunction.getFlags(builtin, callTarget);
            return PythonObjectFactory.getUncached().createBuiltinFunction(propertyName, null, 0, flags, callTarget);
        }
    }

    @GenerateInline(false)
    abstract static class PyFloatAsDoubleCachedNode extends Node {

        public abstract double execute(VirtualFrame frame, Object object);

        @Specialization
        static double doGeneric(VirtualFrame frame, Object object,
                        @Bind("this") Node inliningTarget,
                        @Cached PyFloatAsDoubleNode pyFloatAsDoubleNode) {
            return pyFloatAsDoubleNode.execute(frame, inliningTarget, object);
        }
    }
}
