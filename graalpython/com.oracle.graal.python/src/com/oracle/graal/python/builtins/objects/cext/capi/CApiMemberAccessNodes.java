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
package com.oracle.graal.python.builtins.objects.cext.capi;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiMemberAccessNodesFactory.BadMemberDescrNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiMemberAccessNodesFactory.ReadMemberNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiMemberAccessNodesFactory.ReadOnlyMemberNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiMemberAccessNodesFactory.WriteByteNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiMemberAccessNodesFactory.WriteCharNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiMemberAccessNodesFactory.WriteDoubleNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiMemberAccessNodesFactory.WriteFloatNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiMemberAccessNodesFactory.WriteIntNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiMemberAccessNodesFactory.WriteLongNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiMemberAccessNodesFactory.WriteMemberNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiMemberAccessNodesFactory.WriteObjectExNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiMemberAccessNodesFactory.WriteObjectNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiMemberAccessNodesFactory.WriteShortNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiMemberAccessNodesFactory.WriteUIntNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiMemberAccessNodesFactory.WriteULongNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitionsFactory.PythonToNativeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.common.CExtAsPythonObjectNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.AsNativeCharNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.AsNativeDoubleNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.AsNativePrimitiveNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodesFactory.NativePrimitiveAsPythonBooleanNodeGen;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodesFactory.NativePrimitiveAsPythonCharNodeGen;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodesFactory.NativeUnsignedByteNodeGen;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodesFactory.NativeUnsignedPrimitiveAsPythonObjectNodeGen;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodesFactory.NativeUnsignedShortNodeGen;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodesFactory.StringAsPythonStringNodeGen;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccessFactory;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructs;
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
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Cached;
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

    /**
     * Special case: members with type {@code STRING} and {@code STRING_INPLACE} are always
     * read-only.
     */
    private static boolean isReadOnlyType(int type) {
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

    private static CStructAccess.ReadBaseNode getReadNode(int type) {
        switch (type) {
            case T_SHORT:
            case T_USHORT:
                return CStructAccessFactory.ReadI16NodeGen.create();
            case T_INT:
            case T_UINT:
                return CStructAccessFactory.ReadI32NodeGen.create();
            case T_LONG:
            case T_ULONG:
                return CStructAccessFactory.ReadI64NodeGen.create();
            case T_FLOAT:
                return CStructAccessFactory.ReadFloatNodeGen.create();
            case T_DOUBLE:
                return CStructAccessFactory.ReadDoubleNodeGen.create();
            case T_STRING:
                return CStructAccessFactory.ReadPointerNodeGen.create();
            case T_OBJECT:
                return CStructAccessFactory.ReadObjectNodeGen.create();
            case T_OBJECT_EX:
                return CStructAccessFactory.ReadObjectNodeGen.create();
            case T_CHAR:
            case T_BYTE:
            case T_UBYTE:
            case T_BOOL:
                return CStructAccessFactory.ReadByteNodeGen.create();
            case T_STRING_INPLACE:
                return CStructAccessFactory.GetElementPtrNodeGen.create();
            case T_LONGLONG:
            case T_ULONGLONG:
                assert CStructs.long__long.size() == Long.BYTES;
                return CStructAccessFactory.ReadI64NodeGen.create();
            case T_PYSSIZET:
                assert CStructs.Py_ssize_t.size() == Long.BYTES;
                return CStructAccessFactory.ReadI64NodeGen.create();
            case T_NONE:
                return null;
        }
        throw CompilerDirectives.shouldNotReachHere("invalid member type");
    }

    private static CExtAsPythonObjectNode getReadConverterNode(int type) {
        switch (type) {
            case T_SHORT:
            case T_INT:
            case T_LONG:
            case T_LONGLONG:
            case T_FLOAT:
            case T_DOUBLE:
            case T_BYTE:
            case T_PYSSIZET:
            case T_NONE:
                // no conversion needed
                return null;
            case T_STRING:
            case T_STRING_INPLACE:
                return StringAsPythonStringNodeGen.create();
            case T_BOOL:
                return NativePrimitiveAsPythonBooleanNodeGen.create();
            case T_CHAR:
                return NativePrimitiveAsPythonCharNodeGen.create();
            case T_UBYTE:
                return NativeUnsignedByteNodeGen.create();
            case T_USHORT:
                return NativeUnsignedShortNodeGen.create();
            case T_UINT:
            case T_ULONG:
            case T_ULONGLONG:
                return NativeUnsignedPrimitiveAsPythonObjectNodeGen.create();
            case T_OBJECT:
            case T_OBJECT_EX:
                return null;
        }
        throw CompilerDirectives.shouldNotReachHere("invalid member type");
    }

    @Builtin(name = "read_member", minNumOfPositionalArgs = 1, parameterNames = "$self")
    public abstract static class ReadMemberNode extends PythonUnaryBuiltinNode {
        private static final Builtin BUILTIN = ReadMemberNode.class.getAnnotation(Builtin.class);

        @Child private PythonToNativeNode toSulongNode;
        @Child private CExtAsPythonObjectNode asPythonObjectNode;
        @Child private PForeignToPTypeNode fromForeign;
        @Child private PRaiseNode raiseNode;

        @Child private CStructAccess.ReadBaseNode read;

        /** The specified member type. */
        private final int type;

        /** The offset where to read from (will be passed to the native getter). */
        private final int offset;

        protected ReadMemberNode(int type, int offset, CExtAsPythonObjectNode asPythonObjectNode) {
            this.type = type;
            this.read = getReadNode(type);
            this.offset = offset;
            this.asPythonObjectNode = asPythonObjectNode;
            if (asPythonObjectNode == null) {
                fromForeign = PForeignToPTypeNode.create();
            }
        }

        @Specialization
        Object doGeneric(@SuppressWarnings("unused") VirtualFrame frame, Object self) {
            if (read == null) {
                return PNone.NONE;
            } else {
                Object nativeResult = read.readGeneric(ensureToSulongNode().execute(self), offset);
                assert !(nativeResult instanceof Byte || nativeResult instanceof Short || nativeResult instanceof Float || nativeResult instanceof Character || nativeResult instanceof PException ||
                                nativeResult instanceof String) : nativeResult + " " + nativeResult.getClass();
                if (type == T_OBJECT_EX && nativeResult == PNone.NO_VALUE) {
                    throw ensureRaiseNode().raise(PythonBuiltinClassType.AttributeError);
                }
                if (type == T_OBJECT && nativeResult == PNone.NO_VALUE) {
                    nativeResult = PNone.NONE;
                }
                if (asPythonObjectNode != null) {
                    return asPythonObjectNode.execute(nativeResult);
                } else {
                    return nativeResult;
                }
            }
        }

        private PythonToNativeNode ensureToSulongNode() {
            if (toSulongNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toSulongNode = insert(PythonToNativeNodeGen.create());
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

    abstract static class WriteTypeNode extends Node {

        abstract void execute(Object pointer, Object newValue);
    }

    abstract static class WriteByteNode extends WriteTypeNode {

        @Specialization
        static void write(Object pointer, Object newValue,
                        @Cached AsNativePrimitiveNode asLong,
                        @Cached CStructAccess.WriteByteNode write) {
            write.write(pointer, (byte) asLong.toInt64(newValue, true));
        }
    }

    abstract static class WriteShortNode extends WriteTypeNode {

        @Specialization
        static void write(Object pointer, Object newValue,
                        @Cached AsNativePrimitiveNode asLong,
                        @Cached CStructAccess.WriteI16Node write) {
            write.write(pointer, (short) asLong.toInt64(newValue, true));
        }
    }

    abstract static class WriteIntNode extends WriteTypeNode {

        @Specialization
        static void write(Object pointer, Object newValue,
                        @Cached AsNativePrimitiveNode asLong,
                        @Cached CStructAccess.WriteIntNode write) {
            write.write(pointer, (int) asLong.toInt64(newValue, true));
        }
    }

    abstract static class WriteLongNode extends WriteTypeNode {

        @Specialization
        static void write(Object pointer, Object newValue,
                        @Cached AsNativePrimitiveNode asLong,
                        @Cached CStructAccess.WriteLongNode write,
                        @Cached IsBuiltinClassProfile exceptionProfile) {
            try {
                write.write(pointer, asLong.toInt64(newValue, true));
            } catch (PException e) {
                /*
                 * Special case: if conversion raises an OverflowError, CPython still assigns the
                 * error indication value -1 to the member. That looks rather like a bug but let's
                 * just do the same.
                 */
                e.expectOverflowError(exceptionProfile);
                write.write(pointer, -1);
                throw e;
            }
        }
    }

    abstract static class WriteUIntNode extends WriteTypeNode {

        @Specialization
        static void write(Object pointer, Object newValue,
                        @Cached AsNativePrimitiveNode asLong,
                        @Cached CStructAccess.WriteIntNode write,
                        @Cached PRaiseNode raiseNode,
                        @Cached IsBuiltinClassProfile exceptionProfile) {
            /*
             * This emulates the arguably buggy behavior from CPython where it accepts MIN_LONG to
             * MAX_ULONG values.
             */
            try {
                write.write(pointer, (int) asLong.toUInt64(newValue, true));
            } catch (PException e) {
                /*
                 * Special case: accept signed long as well.
                 */
                e.expectOverflowError(exceptionProfile);
                write.write(pointer, (int) asLong.toInt64(newValue, true));
                // swallowing the exception
            }
        }
    }

    abstract static class WriteULongNode extends WriteTypeNode {

        @Specialization
        static void write(Object pointer, Object newValue,
                        @Cached AsNativePrimitiveNode asLong,
                        @Cached CStructAccess.WriteLongNode write,
                        @Cached IsBuiltinClassProfile exceptionProfile) {
            try {
                write.write(pointer, asLong.toUInt64(newValue, true));
            } catch (PException e) {
                /*
                 * Special case: if conversion raises an OverflowError, CPython still assigns the
                 * error indication value -1 to the member. That looks rather like a bug but let's
                 * just do the same.
                 */
                e.expectOverflowError(exceptionProfile);
                write.write(pointer, -1);
                throw e;
            }
        }
    }

    abstract static class WriteDoubleNode extends WriteTypeNode {

        @Specialization
        static void write(Object pointer, Object newValue,
                        @Cached AsNativeDoubleNode asDouble,
                        @Cached CStructAccess.WriteDoubleNode write,
                        @Cached IsBuiltinClassProfile exceptionProfile) {
            try {
                write.write(pointer, asDouble.executeDouble(newValue));
            } catch (PException e) {
                /*
                 * Special case: if conversion raises an OverflowError, CPython still assigns the
                 * error indication value -1 to the member. That looks rather like a bug but let's
                 * just do the same.
                 */
                e.expectTypeError(exceptionProfile);
                write.write(pointer, -1);
                throw e;
            }
        }
    }

    abstract static class WriteFloatNode extends WriteTypeNode {

        @Specialization
        static void write(Object pointer, Object newValue,
                        @Cached AsNativeDoubleNode asDouble,
                        @Cached CStructAccess.WriteFloatNode write) {
            write.write(pointer, (float) asDouble.executeDouble(newValue));
        }
    }

    abstract static class WriteObjectNode extends WriteTypeNode {

        @Specialization
        static void write(Object pointer, Object newValue,
                        @Cached CStructAccess.WriteObjectNewRefNode write) {
            write.write(pointer, newValue);
        }
    }

    abstract static class WriteObjectExNode extends WriteTypeNode {

        @Specialization
        static void write(Object pointer, Object newValue,
                        @Cached CStructAccess.ReadObjectNode read,
                        @Cached CStructAccess.WriteObjectNewRefNode write,
                        @Cached PRaiseNode raise) {
            Object current = read.readGeneric(pointer, 0);
            if (newValue == DescriptorDeleteMarker.INSTANCE && current == PNone.NO_VALUE) {
                throw raise.raise(PythonBuiltinClassType.AttributeError);
            }
            write.write(pointer, newValue);
        }
    }

    abstract static class WriteCharNode extends WriteTypeNode {

        @Specialization
        static void write(Object pointer, Object newValue,
                        @Cached AsNativeCharNode asChar,
                        @Cached CStructAccess.WriteByteNode write) {
            write.write(pointer, asChar.executeByte(newValue));
        }
    }

    private static WriteTypeNode getWriteNode(int type) {
        switch (type) {
            case T_SHORT:
            case T_USHORT:
                return WriteShortNodeGen.create();
            case T_INT:
                return WriteIntNodeGen.create();
            case T_UINT:
                return WriteUIntNodeGen.create();
            case T_LONG:
                return WriteLongNodeGen.create();
            case T_FLOAT:
                return WriteFloatNodeGen.create();
            case T_DOUBLE:
                return WriteDoubleNodeGen.create();
            case T_NONE:
            case T_STRING:
            case T_STRING_INPLACE:
                return null;
            case T_OBJECT:
                return WriteObjectNodeGen.create();
            case T_OBJECT_EX:
                return WriteObjectExNodeGen.create();
            case T_CHAR:
                return WriteCharNodeGen.create();
            case T_BOOL:
            case T_BYTE:
            case T_UBYTE:
                return WriteByteNodeGen.create();
            case T_ULONG:
            case T_ULONGLONG:
                return WriteULongNodeGen.create();
            case T_LONGLONG:
            case T_PYSSIZET:
                assert CStructs.long__long.size() == Long.BYTES;
                assert CStructs.Py_ssize_t.size() == Long.BYTES;
                return WriteLongNodeGen.create();
            default:
                throw CompilerDirectives.shouldNotReachHere("invalid member type");
        }
    }

    @Builtin(name = "write_member", minNumOfPositionalArgs = 2, parameterNames = {"$self", "value"})
    public abstract static class WriteMemberNode extends PythonBinaryBuiltinNode {
        private static final Builtin BUILTIN = WriteMemberNode.class.getAnnotation(Builtin.class);

        @Child private PythonToNativeNode toSulongNode;
        @Child private GetClassNode getClassNode;
        @Child private IsSameTypeNode isSameTypeNode;
        @Child private WriteTypeNode write;
        @Child private CStructAccess.GetElementPtrNode getElement;

        /** The specified member type. */
        private final int type;

        /** The offset where to read from (will be passed to the native getter). */
        private final int offset;

        protected WriteMemberNode(int type, int offset) {
            this.type = type;
            this.offset = offset;
            this.write = getWriteNode(type);
            this.toSulongNode = PythonToNativeNodeGen.create();
            this.getElement = CStructAccessFactory.GetElementPtrNodeGen.create();
        }

        @Specialization
        Object doGeneric(VirtualFrame frame, Object self, Object value) {
            Object selfPtr = toSulongNode.execute(self);
            selfPtr = getElement.readGeneric(selfPtr, offset);

            /*
             * Deleting values is only allowed for members with object type (see structmember.c:
             * PyMember_SetOne). Note: it's allowed for type T_OBJECT_EX if the attribute was set
             * before. This case is handled by the native function {@link
             * NativeCAPISymbol#FUN_WRITE_OBJECT_EX_MEMBER}.
             */
            if (type != T_OBJECT && type != T_OBJECT_EX) {
                if (value == DescriptorDeleteMarker.INSTANCE) {
                    throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.CAN_T_DELETE_NUMERIC_CHAR_ATTRIBUTE);
                }
            }

            if (type == T_BOOL && !ensureIsSameTypeNode().execute(PythonBuiltinClassType.Boolean, ensureGetClassNode().execute(value))) {
                throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.ATTRIBUTE_TYPE_VALUE_MUST_BE_BOOL);
            }

            write.execute(selfPtr, value);
            return PNone.NONE;
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

        @TruffleBoundary
        public static PBuiltinFunction createBuiltinFunction(PythonLanguage language, Object owner, TruffleString propertyName, int type, int offset) {
            if (isReadOnlyType(type)) {
                return ReadOnlyMemberNode.createBuiltinFunction(language, propertyName);
            } else if (type == T_NONE) {
                return BadMemberDescrNode.createBuiltinFunction(language, propertyName);
            }
            //
            RootCallTarget callTarget = language.createCachedCallTarget(
                            l -> new BuiltinFunctionRootNode(l, BUILTIN, new HPyMemberNodeFactory<>(WriteMemberNodeGen.create(type, offset)), true),
                            CApiMemberAccessNodes.class, BUILTIN.name(), type, offset);
            int flags = PBuiltinFunction.getFlags(BUILTIN, callTarget);
            return PythonObjectFactory.getUncached().createBuiltinFunction(propertyName, owner, 0, flags, callTarget);
        }
    }
}
