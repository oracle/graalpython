/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.ctypes;

import static com.oracle.graal.python.builtins.modules.ctypes.CtypesNodes.WCHAR_T_ENCODING;
import static com.oracle.graal.python.builtins.modules.ctypes.CtypesNodes.WCHAR_T_SIZE;
import static com.oracle.graal.python.nodes.ErrorMessages.BYTES_EXPECTED_INSTEAD_OF_P_INSTANCE;
import static com.oracle.graal.python.nodes.ErrorMessages.BYTE_STRING_TOO_LONG;
import static com.oracle.graal.python.nodes.ErrorMessages.STRING_TOO_LONG;
import static com.oracle.graal.python.nodes.ErrorMessages.UNICODE_STRING_EXPECTED_INSTEAD_OF_P_INSTANCE;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DOC__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.LazyPyCArrayTypeBuiltinsFactory.CharArrayRawNodeFactory;
import com.oracle.graal.python.builtins.modules.ctypes.LazyPyCArrayTypeBuiltinsFactory.CharArrayValueNodeFactory;
import com.oracle.graal.python.builtins.modules.ctypes.LazyPyCArrayTypeBuiltinsFactory.WCharArrayValueNodeFactory;
import com.oracle.graal.python.builtins.modules.ctypes.memory.PointerNodes;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.BufferFlags;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GetInternalByteArrayNode;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.GetSetDescriptor;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.function.BuiltinFunctionRootNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.object.PythonObjectSlowPathFactory;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.InternalByteArray;
import com.oracle.truffle.api.strings.TruffleString;

public final class LazyPyCArrayTypeBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        throw CompilerDirectives.shouldNotReachHere("Should not be part of initialization!");
    }

    /**
     * Those getters and setter of PyCArrayType are added conditionally
     *
     */

    @TruffleBoundary
    protected static void createCharArrayGetSet(PythonLanguage language, Object type) {
        NodeFactory<CharArrayRawNode> rawFactory = CharArrayRawNodeFactory.getInstance();
        Builtin rawNodeBuiltin = CharArrayRawNode.class.getAnnotation(Builtin.class);
        createGetSet(language, type, rawFactory, rawNodeBuiltin);

        NodeFactory<CharArrayValueNode> valueFactory = CharArrayValueNodeFactory.getInstance();
        Builtin valueNodeBuiltin = CharArrayValueNode.class.getAnnotation(Builtin.class);
        createGetSet(language, type, valueFactory, valueNodeBuiltin);
    }

    @TruffleBoundary
    protected static void createWCharArrayGetSet(PythonLanguage language, Object type) {
        NodeFactory<WCharArrayValueNode> valueFactory = WCharArrayValueNodeFactory.getInstance();
        Builtin valueNodeBuiltin = WCharArrayValueNode.class.getAnnotation(Builtin.class);
        createGetSet(language, type, valueFactory, valueNodeBuiltin);
    }

    @TruffleBoundary
    private static void createGetSet(PythonLanguage language, Object type, NodeFactory<? extends PythonBuiltinBaseNode> factory, Builtin builtin) {
        TruffleString name = toTruffleStringUncached(builtin.name());
        RootCallTarget rawCallTarget = language.createCachedCallTarget(
                        l -> new BuiltinFunctionRootNode(l, builtin, factory, true),
                        factory.getNodeClass(),
                        builtin.name());
        PythonObjectSlowPathFactory f = PythonContext.get(null).factory();
        int flags = PBuiltinFunction.getFlags(builtin, rawCallTarget);
        PBuiltinFunction getter = f.createBuiltinFunction(name, type, 1, flags, rawCallTarget);
        GetSetDescriptor callable = f.createGetSetDescriptor(getter, getter, name, type, false);
        callable.setAttribute(T___DOC__, toTruffleStringUncached(builtin.doc()));
        WriteAttributeToObjectNode.getUncached(true).execute(type, name, callable);
    }

    @Builtin(name = "raw", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, doc = "value")
    @GenerateNodeFactory
    abstract static class CharArrayRawNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = "isNoValue(value)")
        static PBytes doGet(CDataObject self, @SuppressWarnings("unused") PNone value,
                        @Bind("this") Node inliningTarget,
                        @Cached PointerNodes.ReadBytesNode read,
                        @Cached PythonObjectFactory factory) {
            return factory.createBytes(read.execute(inliningTarget, self.b_ptr, self.b_size));
        }

        @Specialization(limit = "3")
        Object doSet(VirtualFrame frame, CDataObject self, Object value,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary("value") PythonBufferAcquireLibrary acquireLib,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib,
                        @Cached PointerNodes.WriteBytesNode writeBytesNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            Object buffer = acquireLib.acquire(value, BufferFlags.PyBUF_SIMPLE, frame, this);
            try {
                byte[] bytes = bufferLib.getInternalOrCopiedByteArray(buffer);
                int len = bufferLib.getBufferLength(buffer);
                if (len > self.b_size) {
                    throw raiseNode.get(inliningTarget).raise(ValueError, BYTE_STRING_TOO_LONG);
                }
                writeBytesNode.execute(inliningTarget, self.b_ptr, bytes, 0, len);
                return PNone.NONE;
            } finally {
                bufferLib.release(buffer);
            }
        }
    }

    @Builtin(name = "value", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, doc = "string value")
    @GenerateNodeFactory
    abstract static class CharArrayValueNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = "isNoValue(value)")
        static PBytes doGet(CDataObject self, @SuppressWarnings("unused") PNone value,
                        @Bind("this") Node inliningTarget,
                        @Cached PointerNodes.StrLenNode strLenNode,
                        @Cached PointerNodes.ReadBytesNode read,
                        @Cached PythonObjectFactory factory) {
            return factory.createBytes(read.execute(inliningTarget, self.b_ptr, strLenNode.execute(inliningTarget, self.b_ptr)));
        }

        @Specialization
        static Object doSet(CDataObject self, PBytes value,
                        @Bind("this") Node inliningTarget,
                        @Cached GetInternalByteArrayNode getBytes,
                        @Cached PointerNodes.WriteBytesNode writeBytesNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            SequenceStorage storage = value.getSequenceStorage();
            int len = storage.length();
            if (len > self.b_size) {
                throw raiseNode.get(inliningTarget).raise(ValueError, BYTE_STRING_TOO_LONG);
            }
            byte[] bytes = getBytes.execute(inliningTarget, storage);
            writeBytesNode.execute(inliningTarget, self.b_ptr, bytes, 0, len);
            return PNone.NONE;
        }

        @Fallback
        static Object error(@SuppressWarnings("unused") Object self, Object value,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, BYTES_EXPECTED_INSTEAD_OF_P_INSTANCE, value);
        }
    }

    @Builtin(name = "value", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, doc = "string value")
    @GenerateNodeFactory
    abstract static class WCharArrayValueNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = "isNoValue(value)")
        static TruffleString doGet(CDataObject self, @SuppressWarnings("unused") PNone value,
                        @Bind("this") Node inliningTarget,
                        @Cached PointerNodes.WCsLenNode wCsLenNode,
                        @Cached PointerNodes.ReadBytesNode read,
                        @Cached TruffleString.FromByteArrayNode fromByteArrayNode,
                        @Cached TruffleString.SwitchEncodingNode switchEncodingNode) {
            byte[] bytes = read.execute(inliningTarget, self.b_ptr, wCsLenNode.execute(inliningTarget, self.b_ptr) * WCHAR_T_SIZE);
            TruffleString s = fromByteArrayNode.execute(bytes, WCHAR_T_ENCODING);
            return switchEncodingNode.execute(s, TS_ENCODING);
        }

        @Specialization(guards = "isString(value)")
        static Object doSet(CDataObject self, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToTruffleStringNode toTruffleStringNode,
                        @Cached TruffleString.SwitchEncodingNode switchEncodingNode,
                        @Cached TruffleString.GetInternalByteArrayNode getInternalByteArrayNode,
                        @Cached PointerNodes.WriteBytesNode writeBytesNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            TruffleString str = switchEncodingNode.execute(toTruffleStringNode.execute(inliningTarget, value), WCHAR_T_ENCODING);
            int len = str.byteLength(WCHAR_T_ENCODING);
            if (len > self.b_size) {
                throw raiseNode.get(inliningTarget).raise(ValueError, STRING_TOO_LONG);
            }
            InternalByteArray bytes = getInternalByteArrayNode.execute(str, WCHAR_T_ENCODING);
            writeBytesNode.execute(inliningTarget, self.b_ptr, bytes.getArray(), bytes.getOffset(), bytes.getLength());
            return PNone.NONE;
        }

        @Fallback
        static Object error(@SuppressWarnings("unused") Object self, Object value,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, UNICODE_STRING_EXPECTED_INSTEAD_OF_P_INSTANCE, value);
        }
    }

}
