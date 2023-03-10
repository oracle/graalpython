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
package com.oracle.graal.python.builtins.objects.cext.capi;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiMemberAccessNodesFactory.BadMemberDescrNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiMemberAccessNodesFactory.ReadMemberNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiMemberAccessNodesFactory.ReadOnlyMemberNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiMemberAccessNodesFactory.WriteMemberNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.PCallCapiFunctionNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitionsFactory.NativeToPythonNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitionsFactory.PythonToNativeTransferNodeGen;
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
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.DescriptorDeleteMarker;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsSameTypeNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.BuiltinFunctionRootNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.interop.PForeignToPTypeNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.GeneratedBy;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.strings.TruffleString;

public class CApiMemberAccessNodes {

    /* Member type values; see 'structmember.h' */
    public static final int T_SHORT = 0;
    public static final int T_INT = 1;
    public static final int T_LONG = 2;
    public static final int T_FLOAT = 3;
    public static final int T_DOUBLE = 4;
    public static final int T_STRING = 5;
    public static final int T_OBJECT = 6;
    public static final int T_CHAR = 7;
    public static final int T_BYTE = 8;
    public static final int T_UBYTE = 9;
    public static final int T_USHORT = 10;
    public static final int T_UINT = 11;
    public static final int T_ULONG = 12;
    public static final int T_STRING_INPLACE = 13;
    public static final int T_BOOL = 14;
    public static final int T_OBJECT_EX = 16;
    public static final int T_LONGLONG = 17;
    public static final int T_ULONGLONG = 18;
    public static final int T_PYSSIZET = 19;
    public static final int T_NONE = 20;

    static NativeCAPISymbol getReadAccessorName(int type) {
        switch (type) {
            case T_SHORT:
                return NativeCAPISymbol.FUN_READ_SHORT_MEMBER;
            case T_INT:
                return NativeCAPISymbol.FUN_READ_INT_MEMBER;
            case T_LONG:
                return NativeCAPISymbol.FUN_READ_LONG_MEMBER;
            case T_FLOAT:
                return NativeCAPISymbol.FUN_READ_FLOAT_MEMBER;
            case T_DOUBLE:
                return NativeCAPISymbol.FUN_READ_DOUBLE_MEMBER;
            case T_STRING:
                return NativeCAPISymbol.FUN_READ_STRING_MEMBER;
            case T_OBJECT:
                return NativeCAPISymbol.FUN_READ_OBJECT_MEMBER;
            case T_OBJECT_EX:
                return NativeCAPISymbol.FUN_READ_OBJECT_EX_MEMBER;
            case T_CHAR:
            case T_BYTE:
            case T_BOOL:
                return NativeCAPISymbol.FUN_READ_CHAR_MEMBER;
            case T_UBYTE:
                return NativeCAPISymbol.FUN_READ_UBYTE_MEMBER;
            case T_USHORT:
                return NativeCAPISymbol.FUN_READ_USHORT_MEMBER;
            case T_UINT:
                return NativeCAPISymbol.FUN_READ_UINT_MEMBER;
            case T_ULONG:
                return NativeCAPISymbol.FUN_READ_ULONG_MEMBER;
            case T_STRING_INPLACE:
                return NativeCAPISymbol.FUN_READ_STRING_IN_PLACE_MEMBER;
            case T_LONGLONG:
                return NativeCAPISymbol.FUN_READ_LONGLONG_MEMBER;
            case T_ULONGLONG:
                return NativeCAPISymbol.FUN_READ_ULONGLONG_MEMBER;
            case T_PYSSIZET:
                return NativeCAPISymbol.FUN_READ_PYSSIZET_MEMBER;
            case T_NONE:
                return null;
        }
        throw CompilerDirectives.shouldNotReachHere("invalid member type");
    }

    static CExtAsPythonObjectNode getReadConverterNode(int type) {
        switch (type) {
            case T_SHORT:
            case T_INT:
            case T_LONG:
            case T_FLOAT:
            case T_DOUBLE:
            case T_BYTE:
            case T_UBYTE:
            case T_USHORT:
            case T_STRING_INPLACE:
            case T_PYSSIZET:
            case T_NONE:
                // no conversion needed
                return null;
            case T_STRING:
                return StringAsPythonStringNodeGen.create();
            case T_BOOL:
                return NativePrimitiveAsPythonBooleanNodeGen.create();
            case T_CHAR:
                return NativePrimitiveAsPythonCharNodeGen.create();
            case T_UINT:
            case T_ULONG:
            case T_LONGLONG:
            case T_ULONGLONG:
                return NativeUnsignedPrimitiveAsPythonObjectNodeGen.create();
            case T_OBJECT:
            case T_OBJECT_EX:
                return NativeToPythonNodeGen.create();
        }
        throw CompilerDirectives.shouldNotReachHere("invalid member type");
    }

    static NativeCAPISymbol getWriteAccessorName(int type) {
        switch (type) {
            case T_SHORT:
                return NativeCAPISymbol.FUN_WRITE_SHORT_MEMBER;
            case T_INT:
                return NativeCAPISymbol.FUN_WRITE_INT_MEMBER;
            case T_LONG:
                return NativeCAPISymbol.FUN_WRITE_LONG_MEMBER;
            case T_FLOAT:
                return NativeCAPISymbol.FUN_WRITE_FLOAT_MEMBER;
            case T_DOUBLE:
                return NativeCAPISymbol.FUN_WRITE_DOUBLE_MEMBER;
            case T_NONE:
            case T_STRING:
            case T_STRING_INPLACE:
                return null;
            case T_OBJECT:
                return NativeCAPISymbol.FUN_WRITE_OBJECT_MEMBER;
            case T_OBJECT_EX:
                return NativeCAPISymbol.FUN_WRITE_OBJECT_EX_MEMBER;
            case T_CHAR:
            case T_BYTE:
            case T_BOOL:
                return NativeCAPISymbol.FUN_WRITE_CHAR_MEMBER;
            case T_UBYTE:
                return NativeCAPISymbol.FUN_WRITE_UBYTE_MEMBER;
            case T_USHORT:
                return NativeCAPISymbol.FUN_WRITE_USHORT_MEMBER;
            case T_UINT:
                return NativeCAPISymbol.FUN_WRITE_UINT_MEMBER;
            case T_ULONG:
                return NativeCAPISymbol.FUN_WRITE_ULONG_MEMBER;
            case T_LONGLONG:
                return NativeCAPISymbol.FUN_WRITE_LONGLONG_MEMBER;
            case T_ULONGLONG:
                return NativeCAPISymbol.FUN_WRITE_ULONGLONG_MEMBER;
            case T_PYSSIZET:
                return NativeCAPISymbol.FUN_WRITE_PYSSIZET_MEMBER;
        }
        throw CompilerDirectives.shouldNotReachHere("invalid member type");
    }

    static CExtToNativeNode getWriteConverterNode(int type) {
        switch (type) {
            case T_CHAR:
                return AsNativeCharNodeGen.create();
            case T_BOOL:
                return AsNativeBooleanNodeGen.create();
            case T_BYTE:
            case T_UBYTE:
            case T_SHORT:
            case T_USHORT:
            case T_INT:
            case T_LONG:
            case T_PYSSIZET:
                // TODO(fa): use appropriate native type sizes
                /*
                 * Note: according to 'structmember.c: PyMember_SetOne', CPython always uses
                 * 'PyLong_AsLong' and just casts to the appropriate C type. This cast may be lossy.
                 */
                return AsFixedNativePrimitiveNodeGen.create(Long.BYTES, true);
            case T_FLOAT:
            case T_DOUBLE:
                return AsNativeDoubleNodeGen.create();
            case T_UINT:
                /*
                 * CPython converts to a unsigned long and just truncates to an unsigned int. For
                 * reference, see 'structmember.c: PyMember_SetOne' case 'T_UINT'.
                 */
            case T_ULONG:
            case T_LONGLONG:
            case T_ULONGLONG:
                // TODO(fa): use appropriate native type sizes
                return AsFixedNativePrimitiveNodeGen.create(Long.BYTES, false);
            case T_OBJECT:
            case T_OBJECT_EX:
                return PythonToNativeTransferNodeGen.create();
            case T_NONE:
            case T_STRING:
            case T_STRING_INPLACE:
                return null;
        }
        throw CompilerDirectives.shouldNotReachHere("invalid member type");
    }

    /**
     * Special case: members with type {@code STRING} and {@code STRING_INPLACE} are always
     * read-only.
     */
    static boolean isReadOnlyType(int type) {
        return type == T_STRING || type == T_STRING_INPLACE;
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

    @Builtin(name = "read_member", minNumOfPositionalArgs = 1, parameterNames = "$self")
    public abstract static class ReadMemberNode extends PythonUnaryBuiltinNode {
        private static final Builtin BUILTIN = ReadMemberNode.class.getAnnotation(Builtin.class);

        @Child private PCallCapiFunction callHPyFunctionNode;
        @Child private ToSulongNode toSulongNode;
        @Child private CExtAsPythonObjectNode asPythonObjectNode;
        @Child private PForeignToPTypeNode fromForeign;
        @Child private PRaiseNode raiseNode;

        /** The specified member type. */
        private final int type;

        /** The offset where to read from (will be passed to the native getter). */
        private final int offset;

        protected ReadMemberNode(int type, int offset, CExtAsPythonObjectNode asPythonObjectNode) {
            this.type = type;
            this.offset = offset;
            this.asPythonObjectNode = asPythonObjectNode;
            if (asPythonObjectNode == null) {
                fromForeign = PForeignToPTypeNode.create();
            }
        }

        @Specialization
        Object doGeneric(@SuppressWarnings("unused") VirtualFrame frame, Object self) {
            Object pyObjectPtr = ensureToSulongNode().execute(self);
            Object nativeResult = PNone.NONE;
            NativeCAPISymbol accessor = getReadAccessorName(type);
            if (accessor != null) {
                // This will call pure C functions that won't ever access the Python stack nor the
                // exception state. So, we don't need to setup an indirect call.
                nativeResult = ensureCallHPyFunctionNode().call(accessor, pyObjectPtr, (long) offset);
                if (asPythonObjectNode != null) {
                    Object result = asPythonObjectNode.execute(nativeResult);
                    if (type == T_OBJECT_EX && result == PNone.NO_VALUE) {
                        throw ensureRaiseNode().raise(PythonBuiltinClassType.AttributeError);
                    }
                    return result;
                }
            }
            // We still need to use 'PForeignToPTypeNode' to ensure that we do not introduce unknown
            // values into our value space.
            return fromForeign.executeConvert(nativeResult);
        }

        private PCallCapiFunction ensureCallHPyFunctionNode() {
            if (callHPyFunctionNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callHPyFunctionNode = insert(PCallCapiFunctionNodeGen.create());
            }
            return callHPyFunctionNode;
        }

        private ToSulongNode ensureToSulongNode() {
            if (toSulongNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toSulongNode = insert(ToSulongNode.create());
            }
            return toSulongNode;
        }

        private PRaiseNode ensureRaiseNode() {
            if (raiseNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                raiseNode = insert(PRaiseNode.create());
            }
            return raiseNode;
        }

        @TruffleBoundary
        public static PBuiltinFunction createBuiltinFunction(PythonLanguage language, Object owner, TruffleString propertyName, int type, int offset) {
            CExtAsPythonObjectNode asPythonObjectNode = getReadConverterNode(type);
            RootCallTarget callTarget = language.createCachedCallTarget(
                            l -> new BuiltinFunctionRootNode(l, BUILTIN, new HPyMemberNodeFactory<>(ReadMemberNodeGen.create(type, offset, asPythonObjectNode)), true),
                            CApiMemberAccessNodes.class, BUILTIN.name(), type, offset);
            int flags = PBuiltinFunction.getFlags(BUILTIN, callTarget);
            return PythonObjectFactory.getUncached().createBuiltinFunction(propertyName, owner, 0, flags, callTarget);
        }
    }

    @Builtin(name = "member_write_read_only", minNumOfPositionalArgs = 1, parameterNames = {"$self", "value"})
    protected abstract static class ReadOnlyMemberNode extends PythonBinaryBuiltinNode {
        private static final Builtin BUILTIN = ReadOnlyMemberNode.class.getAnnotation(Builtin.class);
        private final TruffleString propertyName;

        protected ReadOnlyMemberNode(TruffleString propertyName) {
            this.propertyName = propertyName;
        }

        @Specialization
        @SuppressWarnings("unused")
        Object doGeneric(Object self, Object value) {
            throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.ATTRIBUTE_S_OF_P_OBJECTS_IS_NOT_WRITABLE, propertyName, self);
        }

        @TruffleBoundary
        public static PBuiltinFunction createBuiltinFunction(PythonLanguage language, TruffleString propertyName) {
            RootCallTarget builtinCt = language.createCachedCallTarget(
                            l -> new BuiltinFunctionRootNode(l, BUILTIN, new HPyMemberNodeFactory<>(ReadOnlyMemberNodeGen.create(propertyName)), true),
                            CApiMemberAccessNodes.class, BUILTIN.name());
            int flags = PBuiltinFunction.getFlags(BUILTIN, builtinCt);
            return PythonObjectFactory.getUncached().createBuiltinFunction(propertyName, null, 0, flags, builtinCt);
        }
    }

    @Builtin(name = "bad_member_descr", minNumOfPositionalArgs = 2, parameterNames = {"$self", "value"})
    protected abstract static class BadMemberDescrNode extends PythonBinaryBuiltinNode {
        private static final Builtin BUILTIN = BadMemberDescrNode.class.getAnnotation(Builtin.class);

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
            RootCallTarget builtinCt = language.createCachedCallTarget(
                            l -> new BuiltinFunctionRootNode(l, BUILTIN, new HPyMemberNodeFactory<>(BadMemberDescrNodeGen.create()), true),
                            CApiMemberAccessNodes.class, BUILTIN.name());
            int flags = PBuiltinFunction.getFlags(BUILTIN, builtinCt);
            return PythonObjectFactory.getUncached().createBuiltinFunction(propertyName, null, 0, flags, builtinCt);
        }
    }

    @Builtin(name = "write_member", minNumOfPositionalArgs = 2, parameterNames = {"$self", "value"})
    public abstract static class WriteMemberNode extends PythonBinaryBuiltinNode {
        private static final Builtin BUILTIN = WriteMemberNode.class.getAnnotation(Builtin.class);

        @Child private PCallCapiFunction callHPyFunctionNode;
        @Child private CExtToNativeNode toNativeNode;
        @Child private ToSulongNode toSulongNode;
        @Child private GetClassNode getClassNode;
        @Child private IsSameTypeNode isSameTypeNode;
        @Child private IsBuiltinClassProfile overflowProfile;

        /** The specified member type. */
        private final int type;

        /** The offset where to read from (will be passed to the native getter). */
        private final int offset;

        protected WriteMemberNode(int type, int offset, CExtToNativeNode toNativeNode) {
            this.type = type;
            this.offset = offset;
            this.toNativeNode = toNativeNode;
        }

        @Specialization
        Object doGeneric(VirtualFrame frame, Object self, Object value) {
            Object selfPtr = ensureToSulongNode().execute(self);

            /*
             * Deleting values is only allowed for members with object type (see structmember.c:
             * PyMember_SetOne). Note: it's allowed for type T_OBJECT_EX if the attribute was set
             * before. This case is handled by the native function {@link
             * NativeCAPISymbol#FUN_WRITE_OBJECT_EX_MEMBER}.
             */
            Object newValue;
            if (value == DescriptorDeleteMarker.INSTANCE) {
                if (type != T_OBJECT && type != T_OBJECT_EX) {
                    throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.CAN_T_DELETE_NUMERIC_CHAR_ATTRIBUTE);
                }
                // NO_VALUE will be converted to the NULL
                newValue = PNone.NO_VALUE;
            } else {
                newValue = value;
            }

            if (type == T_BOOL && !ensureIsSameTypeNode().execute(PythonBuiltinClassType.Boolean, ensureGetClassNode().execute(newValue))) {
                throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.ATTRIBUTE_TYPE_VALUE_MUST_BE_BOOL);
            }

            NativeCAPISymbol accessor = getWriteAccessorName(type);
            if (accessor != null) {
                // convert value if needed
                Object nativeValue;
                if (toNativeNode != null) {
                    PythonContext context = getContext();
                    // The conversion to a native primitive may call arbitrary user code. So we need
                    // to prepare an indirect call.
                    Object savedState = IndirectCallContext.enter(frame, getLanguage(), context, this);
                    try {
                        nativeValue = toNativeNode.execute(newValue);
                    } catch (PException e) {
                        /*
                         * Special case for T_LONG, T_ULONG, T_PYSSIZET: if conversion raises an
                         * OverflowError, CPython still assigns the error indication value -1 to the
                         * member. That looks rather like a bug but let's just do the same.
                         */
                        if (type == T_LONG || type == T_ULONG || type == T_PYSSIZET) {
                            e.expectOverflowError(ensureOverflowProfile());
                            ensureCallHPyFunctionNode().call(accessor, selfPtr, (long) offset, (long) -1);
                        } else if (type == T_DOUBLE) {
                            e.expectTypeError(ensureOverflowProfile());
                            ensureCallHPyFunctionNode().call(accessor, selfPtr, (long) offset, (long) -1);
                        }
                        throw e;
                    } finally {
                        IndirectCallContext.exit(frame, getLanguage(), context, savedState);
                    }
                } else {
                    nativeValue = newValue;
                }

                // This will call pure C functions that won't ever access the Python stack nor the
                // exception state. So, we don't need to setup an indirect call.
                int result = (int) ensureCallHPyFunctionNode().call(accessor, selfPtr, (long) offset, nativeValue);
                // If the result is 1, an exception occurred
                if (result != 0) {
                    throw raise(PythonBuiltinClassType.AttributeError);
                }
            }
            return PNone.NONE;
        }

        private PCallCapiFunction ensureCallHPyFunctionNode() {
            if (callHPyFunctionNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callHPyFunctionNode = insert(PCallCapiFunctionNodeGen.create());
            }
            return callHPyFunctionNode;
        }

        private ToSulongNode ensureToSulongNode() {
            if (toSulongNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toSulongNode = insert(ToSulongNode.create());
            }
            return toSulongNode;
        }

        private GetClassNode ensureGetClassNode() {
            if (getClassNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getClassNode = insert(GetClassNode.create());
            }
            return getClassNode;
        }

        private IsSameTypeNode ensureIsSameTypeNode() {
            if (isSameTypeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isSameTypeNode = insert(IsSameTypeNode.create());
            }
            return isSameTypeNode;
        }

        private IsBuiltinClassProfile ensureOverflowProfile() {
            if (overflowProfile == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                overflowProfile = insert(IsBuiltinClassProfile.create());
            }
            return overflowProfile;
        }

        @TruffleBoundary
        public static PBuiltinFunction createBuiltinFunction(PythonLanguage language, Object owner, TruffleString propertyName, int type, int offset) {
            NativeCAPISymbol accessor = getWriteAccessorName(type);
            CExtToNativeNode toNativeNode = getWriteConverterNode(type);
            if (accessor == null) {
                if (isReadOnlyType(type)) {
                    return ReadOnlyMemberNode.createBuiltinFunction(language, propertyName);
                }
                return BadMemberDescrNode.createBuiltinFunction(language, propertyName);
            }
            //
            RootCallTarget callTarget = language.createCachedCallTarget(
                            l -> new BuiltinFunctionRootNode(l, BUILTIN, new HPyMemberNodeFactory<>(WriteMemberNodeGen.create(type, offset, toNativeNode)), true),
                            CApiMemberAccessNodes.class, BUILTIN.name(), type, offset);
            int flags = PBuiltinFunction.getFlags(BUILTIN, callTarget);
            return PythonObjectFactory.getUncached().createBuiltinFunction(propertyName, owner, 0, flags, callTarget);
        }
    }
}
