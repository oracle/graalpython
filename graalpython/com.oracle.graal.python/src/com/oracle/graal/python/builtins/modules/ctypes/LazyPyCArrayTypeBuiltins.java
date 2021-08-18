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
import static com.oracle.graal.python.builtins.objects.bytes.BytesUtils.getBytes;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__DOC__;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.LazyPyCArrayTypeBuiltinsFactory.CharArrayRawNodeFactory;
import com.oracle.graal.python.builtins.modules.ctypes.LazyPyCArrayTypeBuiltinsFactory.CharArrayValueNodeFactory;
import com.oracle.graal.python.builtins.modules.ctypes.PtrValue.ByteArrayStorage;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.GetSetDescriptor;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.function.BuiltinFunctionRootNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
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
    private static void createGetSet(PythonLanguage language, Object type, NodeFactory<? extends PythonBuiltinBaseNode> factory, Builtin builtin) {
        String name = builtin.name();
        RootCallTarget rawCallTarget = language.createCachedCallTarget(
                        l -> new BuiltinFunctionRootNode(l, builtin, factory, true),
                        factory.getNodeClass(),
                        builtin.name());
        PythonObjectFactory f = PythonObjectFactory.getUncached();
        int flags = PBuiltinFunction.getFlags(builtin, rawCallTarget);
        PBuiltinFunction getter = f.createBuiltinFunction(name, type, 1, flags, rawCallTarget);
        GetSetDescriptor callable = f.createGetSetDescriptor(getter, getter, name, type, false);
        callable.setAttribute(__DOC__, builtin.doc());
        WriteAttributeToObjectNode.getUncached(true).execute(type, name, callable);
    }

    @Builtin(name = "raw", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, doc = "value")
    @GenerateNodeFactory
    abstract static class CharArrayRawNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = "isNoValue(value)")
        Object doGet(CDataObject self, @SuppressWarnings("unused") PNone value) {
            assert self.b_ptr.ptr instanceof ByteArrayStorage;
            return factory().createBytes(self.getBufferBytes());
        }

        @Specialization
        Object doSet(CDataObject self, Object value,
                        @CachedLibrary(limit = "1") PythonObjectLibrary lib) {
            self.b_ptr = PtrValue.bytes(getBytes(lib, value));
            return PNone.NONE;
        }
    }

    @Builtin(name = "value", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, doc = "string value")
    @GenerateNodeFactory
    abstract static class CharArrayValueNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = "isNoValue(value)")
        Object doGet(CDataObject self, @SuppressWarnings("unused") PNone value) {
            return createUTF8String(self.getBufferBytes());
        }

        @Specialization
        Object doSet(CDataObject self, Object value,
                        @CachedLibrary(limit = "1") PythonObjectLibrary lib) {
            self.b_ptr = PtrValue.bytes(getBytes(lib, value));
            return PNone.NONE;
        }
    }

    // TODO WCharArray
}
