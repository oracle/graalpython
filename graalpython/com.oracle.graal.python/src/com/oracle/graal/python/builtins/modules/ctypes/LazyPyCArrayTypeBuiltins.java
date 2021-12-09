/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.objects.bytes.BytesUtils.createUTF8String;
import static com.oracle.graal.python.nodes.ErrorMessages.BYTES_EXPECTED_INSTEAD_OF_S_INSTANCE;
import static com.oracle.graal.python.nodes.ErrorMessages.BYTE_STRING_TOO_LONG;
import static com.oracle.graal.python.nodes.ErrorMessages.STRING_TOO_LONG;
import static com.oracle.graal.python.nodes.ErrorMessages.UNICODE_STRING_EXPECTED_INSTEAD_OF_S_INSTANCE;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__DOC__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.NotImplementedError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.PtrValue.ByteArrayStorage;
import com.oracle.graal.python.builtins.modules.ctypes.LazyPyCArrayTypeBuiltinsFactory.CharArrayRawNodeFactory;
import com.oracle.graal.python.builtins.modules.ctypes.LazyPyCArrayTypeBuiltinsFactory.CharArrayValueNodeFactory;
import com.oracle.graal.python.builtins.modules.ctypes.LazyPyCArrayTypeBuiltinsFactory.WCharArrayValueNodeFactory;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.BufferFlags;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.builtins.objects.bytes.BytesUtils;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GetInternalByteArrayNode;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.GetSetDescriptor;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.function.BuiltinFunctionRootNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PythonObjectSlowPathFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;

public class LazyPyCArrayTypeBuiltins extends PythonBuiltins {

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
        String name = builtin.name();
        RootCallTarget rawCallTarget = language.createCachedCallTarget(
                        l -> new BuiltinFunctionRootNode(l, builtin, factory, true),
                        factory.getNodeClass(),
                        builtin.name());
        PythonObjectSlowPathFactory f = PythonContext.get(null).factory();
        int flags = PBuiltinFunction.getFlags(builtin, rawCallTarget);
        PBuiltinFunction getter = f.createBuiltinFunction(name, type, 1, flags, rawCallTarget);
        GetSetDescriptor callable = f.createGetSetDescriptor(getter, getter, name, type, false);
        callable.setAttribute(__DOC__, builtin.doc());
        WriteAttributeToObjectNode.getUncached(true).execute(type, name, callable);
    }

    @Builtin(name = "raw", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, doc = "value")
    @GenerateNodeFactory
    abstract static class CharArrayRawNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = "isNoValue(value)", limit = "1")
        Object doGet(CDataObject self, @SuppressWarnings("unused") PNone value,
                        @CachedLibrary("self") PythonBufferAccessLibrary bufferLib) {
            return factory().createBytes(bufferLib.getInternalOrCopiedByteArray(self));
        }

        @Specialization
        Object doSet(VirtualFrame frame, CDataObject self, Object value,
                        @CachedLibrary(limit = "1") PythonBufferAcquireLibrary qlib,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary alib) {
            Object buf = qlib.acquire(value, BufferFlags.PyBUF_SIMPLE, frame, this);
            byte[] bytes = alib.getInternalOrCopiedByteArray(buf);
            if (bytes.length > self.b_size) {
                throw raise(ValueError, BYTE_STRING_TOO_LONG);
            }
            if (self.b_ptr.isManagedBytes()) {
                ByteArrayStorage storage = (ByteArrayStorage) self.b_ptr.ptr;
                storage.memcpy(self.b_ptr.offset, bytes);
            } else {
                throw raise(NotImplementedError, "Some storage types aren't supported yet.");
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "value", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, doc = "string value")
    @GenerateNodeFactory
    abstract static class CharArrayValueNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = "isNoValue(value)")
        Object doGet(CDataObject self, @SuppressWarnings("unused") PNone value) {
            if (self.b_ptr.isManagedBytes()) {
                return factory().createBytes(ByteArrayStorage.trim((ByteArrayStorage) self.b_ptr.ptr, self.b_ptr.offset));
            } else {
                throw raise(NotImplementedError, "Some storage types aren't supported yet.");
            }
        }

        @Specialization
        Object doSet(CDataObject self, PBytes value,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @Cached GetInternalByteArrayNode getBytes) {
            if (self.b_ptr.isManagedBytes()) {
                int len = lenNode.execute(value.getSequenceStorage());
                if (len > self.b_size) {
                    throw raise(ValueError, BYTE_STRING_TOO_LONG);
                }
                ByteArrayStorage storage = (ByteArrayStorage) self.b_ptr.ptr;
                storage.memcpy(self.b_ptr.offset, getBytes.execute(value.getSequenceStorage()));
            } else {
                throw raise(NotImplementedError, "Some storage types aren't supported yet.");
            }
            return PNone.NONE;
        }

        @Specialization(guards = {"!isNoValue(value)", "!isPBytes(value)"})
        Object error(@SuppressWarnings("unused") CDataObject self, Object value,
                        @Cached GetClassNode getClassNode,
                        @Cached GetNameNode getNameNode) {
            throw raise(TypeError, BYTES_EXPECTED_INSTEAD_OF_S_INSTANCE, getNameNode.execute(getClassNode.execute(value)));
        }
    }

    @Builtin(name = "value", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, doc = "string value")
    @GenerateNodeFactory
    abstract static class WCharArrayValueNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = "isNoValue(value)")
        Object doGet(CDataObject self, @SuppressWarnings("unused") PNone value) {
            return createUTF8String(ByteArrayStorage.trim((ByteArrayStorage) self.b_ptr.ptr, self.b_ptr.offset));
        }

        @Specialization(guards = "isString(value)")
        Object doSet(CDataObject self, Object value,
                        @Cached CastToJavaStringNode toJavaStringNode) {
            String str = toJavaStringNode.execute(value);
            int len = PString.length(str);
            if (len > self.b_size) {
                throw raise(ValueError, STRING_TOO_LONG);
            }
            ByteArrayStorage storage = (ByteArrayStorage) self.b_ptr.ptr;
            storage.memcpy(self.b_ptr.offset, BytesUtils.utf8StringToBytes(str));
            return PNone.NONE;
        }

        @Specialization(guards = {"!isNoValue(value)", "!isString(value)"})
        Object error(@SuppressWarnings("unused") CDataObject self, Object value,
                        @Cached GetClassNode getClassNode,
                        @Cached GetNameNode getNameNode) {
            throw raise(TypeError, UNICODE_STRING_EXPECTED_INSTEAD_OF_S_INSTANCE, getNameNode.execute(getClassNode.execute(value)));
        }
    }

}
