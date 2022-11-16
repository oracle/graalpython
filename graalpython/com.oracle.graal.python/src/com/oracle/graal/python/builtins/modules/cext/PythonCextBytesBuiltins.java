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
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.nodes.ErrorMessages.BAD_ARG_TO_INTERNAL_FUNC_WAS_S_P;
import static com.oracle.graal.python.nodes.ErrorMessages.CANNOT_CONVERT_P_OBJ_TO_S;
import static com.oracle.graal.python.nodes.ErrorMessages.NATIVE_S_SUBTYPES_NOT_IMPLEMENTED;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ITER__;

import java.util.Arrays;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinConstructors.BytesNode;
import com.oracle.graal.python.builtins.modules.BuiltinConstructors.StrNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.NativeBuiltin;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.BytesBuiltins;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiGuards;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.AsPythonObjectNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PRaiseNativeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.TransformExceptionToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.GetByteArrayNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.str.StringBuiltins.EncodeNode;
import com.oracle.graal.python.builtins.objects.str.StringBuiltins.ModNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CastToByteNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendsModule = PythonCextBuiltins.PYTHON_CEXT)
@GenerateNodeFactory
public final class PythonCextBytesBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PythonCextBytesBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
    }

    @Builtin(name = "PyBytes_Size", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class PyBytesSizeNode extends PythonUnaryBuiltinNode {
        @Specialization
        public static int size(VirtualFrame frame, PBytes obj,
                        @Cached PyObjectSizeNode sizeNode) {
            return sizeNode.execute(frame, obj);
        }

        @Specialization(guards = {"!isPBytes(obj)", "isBytesSubtype(frame, obj, getClassNode, isSubtypeNode)"})
        public static int sizeNative(VirtualFrame frame, @SuppressWarnings("unused") Object obj,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raiseInt(frame, -1, PythonBuiltinClassType.NotImplementedError, NATIVE_S_SUBTYPES_NOT_IMPLEMENTED, "bytes");
        }

        @Specialization(guards = {"!isPBytes(obj)", "!isBytesSubtype(frame, obj, getClassNode, isSubtypeNode)"})
        public static int size(VirtualFrame frame, Object obj,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached StrNode strNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raiseInt(frame, -1, SystemError, BAD_ARG_TO_INTERNAL_FUNC_WAS_S_P, strNode.executeWith(frame, obj), obj);
        }

        protected boolean isBytesSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PBytes);
        }
    }

    @Builtin(name = "PyBytes_Check", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class PyBytesCheckNode extends PythonUnaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public static Object check(PBytes obj) {
            return true;
        }

        @Specialization(guards = "!isPBytes(obj)")
        public static Object check(VirtualFrame frame, Object obj,
                        @Cached GetClassNode getClassNode,
                        @Cached IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PBytes);
        }
    }

    @Builtin(name = "PyBytes_Concat", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class PyBytesConcatNode extends PythonBinaryBuiltinNode {
        @Specialization
        public Object concat(VirtualFrame frame, PBytes original, Object newPart,
                        @Cached BytesBuiltins.AddNode addNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return addNode.execute(frame, original, newPart);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }

        @Specialization(guards = {"!isPBytes(original)", "isBytesSubtype(frame, original, getClassNode, isSubtypeNode)"})
        public Object concatNative(VirtualFrame frame, @SuppressWarnings("unused") Object original, @SuppressWarnings("unused") Object newPart,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raise(frame, getContext().getNativeNull(), PythonBuiltinClassType.NotImplementedError, NATIVE_S_SUBTYPES_NOT_IMPLEMENTED, "bytes");
        }

        @Specialization(guards = {"!isPBytes(original)", "!isBytesSubtype(frame, original, getClassNode, isSubtypeNode)"})
        public Object concat(VirtualFrame frame, Object original, @SuppressWarnings("unused") Object newPart,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached StrNode strNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raise(frame, getContext().getNativeNull(), SystemError, BAD_ARG_TO_INTERNAL_FUNC_WAS_S_P, strNode.executeWith(frame, original), original);
        }

        protected boolean isBytesSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PBytes);
        }
    }

    @Builtin(name = "PyBytes_Join", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class PyBytesJoinNode extends PythonBinaryBuiltinNode {
        @Specialization
        public Object join(VirtualFrame frame, PBytes original, Object newPart,
                        @Cached BytesBuiltins.JoinNode joinNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return joinNode.execute(frame, original, newPart);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }

        @Specialization(guards = {"!isPBytes(original)", "isBytesSubtype(frame, original, getClassNode, isSubtypeNode)"})
        public Object joinNative(VirtualFrame frame, @SuppressWarnings("unused") Object original, @SuppressWarnings("unused") Object newPart,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raise(frame, getContext().getNativeNull(), PythonBuiltinClassType.NotImplementedError, NATIVE_S_SUBTYPES_NOT_IMPLEMENTED, "bytes");
        }

        @Specialization(guards = {"!isPBytes(original)", "!isBytesSubtype(frame, original, getClassNode, isSubtypeNode)"})
        public Object join(VirtualFrame frame, @SuppressWarnings("unused") Object original, @SuppressWarnings("unused") Object newPart,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @Cached StrNode strNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raise(frame, getContext().getNativeNull(), SystemError, BAD_ARG_TO_INTERNAL_FUNC_WAS_S_P, strNode.executeWith(frame, original), original);
        }

        protected boolean isBytesSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PBytes);
        }
    }

    @Builtin(name = "PyBytes_FromFormat", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class PyBytesFromFormatNode extends PythonBinaryBuiltinNode {
        @Specialization
        public Object fromFormat(VirtualFrame frame, TruffleString fmt, Object args,
                        @Cached ModNode modeNode,
                        @Cached EncodeNode encodeNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                Object formated = modeNode.execute(frame, fmt, args);
                return encodeNode.execute(frame, formated, PNone.NONE, PNone.NONE);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }
    }

    @Builtin(name = "PyBytes_FromObject", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class PyBytesFromObjectNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = {"isPBytes(obj) || isBytesSubtype(frame, obj, getClassNode, isSubtypeNode)"})
        public static Object fromObject(@SuppressWarnings("unused") VirtualFrame frame, @SuppressWarnings("unused") Object obj,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode) {
            return obj;
        }

        @Specialization(guards = {"!isPBytes(obj)", "!isBytesSubtype(frame, obj, getClassNode, isSubtypeNode)", "isAcceptedSubtype(frame, obj, getClassNode, isSubtypeNode, lookupAttrNode)"})
        public Object fromObject(VirtualFrame frame, Object obj,
                        @Cached BytesNode bytesNode,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @SuppressWarnings("unused") @Cached PyObjectLookupAttr lookupAttrNode,
                        @Cached TransformExceptionToNativeNode transformExceptionToNativeNode) {
            try {
                return bytesNode.execute(frame, PythonBuiltinClassType.PBytes, obj, PNone.NO_VALUE, PNone.NO_VALUE);
            } catch (PException e) {
                transformExceptionToNativeNode.execute(e);
                return getContext().getNativeNull();
            }
        }

        @Specialization(guards = {"!isPBytes(obj)", "!isBytesSubtype(frame, obj, getClassNode, isSubtypeNode)", "!isAcceptedSubtype(frame, obj, getClassNode, isSubtypeNode, lookupAttrNode)"})
        public Object fromObject(VirtualFrame frame, Object obj,
                        @SuppressWarnings("unused") @Cached GetClassNode getClassNode,
                        @SuppressWarnings("unused") @Cached IsSubtypeNode isSubtypeNode,
                        @SuppressWarnings("unused") @Cached PyObjectLookupAttr lookupAttrNode,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raise(frame, getContext().getNativeNull(), TypeError, CANNOT_CONVERT_P_OBJ_TO_S, obj, "bytes");
        }

        protected static boolean isBytesSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode) {
            return isSubtypeNode.execute(frame, getClassNode.execute(obj), PythonBuiltinClassType.PBytes);
        }

        protected static boolean isAcceptedSubtype(VirtualFrame frame, Object obj, GetClassNode getClassNode, IsSubtypeNode isSubtypeNode, PyObjectLookupAttr lookupAttrNode) {
            Object klass = getClassNode.execute(obj);
            return isSubtypeNode.execute(frame, klass, PythonBuiltinClassType.PList) ||
                            isSubtypeNode.execute(frame, klass, PythonBuiltinClassType.PTuple) ||
                            isSubtypeNode.execute(frame, klass, PythonBuiltinClassType.PMemoryView) ||
                            (!isSubtypeNode.execute(frame, klass, PythonBuiltinClassType.PString) && lookupAttrNode.execute(frame, obj, T___ITER__) != PNone.NO_VALUE);
        }
    }

    @Builtin(name = "PyBytes_FromStringAndSize", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @ImportStatic(CApiGuards.class)
    abstract static class PyBytesFromStringAndSizeNode extends NativeBuiltin {
        // n.b.: the specializations for PIBytesLike are quite common on
        // managed, when the PySequenceArrayWrapper that we used never went
        // native, and during the upcall to here it was simply unwrapped again
        // with the ToJava (rather than mapped from a native pointer back into a
        // PythonNativeObject)

        @Specialization
        Object doGeneric(VirtualFrame frame, PythonNativeWrapper object, long size,
                        @Cached AsPythonObjectNode asPythonObjectNode,
                        @Exclusive @Cached BytesNodes.ToBytesNode getByteArrayNode,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            byte[] ary = getByteArrayNode.execute(frame, asPythonObjectNode.execute(object));
            PBytes result;
            if (size >= 0 && size < ary.length) {
                // cast to int is guaranteed because of 'size < ary.length'
                result = factory().createBytes(Arrays.copyOf(ary, (int) size));
            } else {
                result = factory().createBytes(ary);
            }
            return toSulongNode.execute(result);
        }

        @Specialization(guards = "!isNativeWrapper(nativePointer)")
        Object doNativePointer(VirtualFrame frame, Object nativePointer, long size,
                        @Exclusive @Cached GetByteArrayNode getByteArrayNode,
                        @Shared("toSulongNode") @Cached CExtNodes.ToSulongNode toSulongNode) {
            try {
                return toSulongNode.execute(factory().createBytes(getByteArrayNode.execute(nativePointer, size)));
            } catch (InteropException e) {
                return raiseNative(frame, getContext().getNativeNull(), PythonErrorType.TypeError, ErrorMessages.M, e);
            } catch (OverflowException e) {
                return raiseNative(frame, getContext().getNativeNull(), PythonErrorType.SystemError, ErrorMessages.NEGATIVE_SIZE_PASSED);
            }
        }
    }

    @Builtin(name = "_PyBytes_Resize", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class PyBytesResize extends PythonBinaryBuiltinNode {

        @Specialization
        static int resize(VirtualFrame frame, PBytes self, long newSizeL,
                        @Cached SequenceStorageNodes.GetItemNode getItemNode,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached CastToByteNode castToByteNode) {

            SequenceStorage storage = self.getSequenceStorage();
            int newSize = asSizeNode.executeExact(frame, newSizeL);
            int len = storage.length();
            byte[] smaller = new byte[newSize];
            for (int i = 0; i < newSize && i < len; i++) {
                smaller[i] = castToByteNode.execute(frame, getItemNode.execute(storage, i));
            }
            self.setSequenceStorage(new ByteSequenceStorage(smaller));
            return 0;
        }

        @Specialization(guards = "!isBytes(self)")
        static int add(VirtualFrame frame, Object self, @SuppressWarnings("unused") Object o,
                        @Cached PRaiseNativeNode raiseNativeNode) {
            return raiseNativeNode.raiseInt(frame, -1, SystemError, ErrorMessages.EXPECTED_S_NOT_P, "a set object", self);
        }

    }
}
