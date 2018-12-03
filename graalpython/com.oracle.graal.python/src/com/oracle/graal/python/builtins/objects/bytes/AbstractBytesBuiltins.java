/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
 * Copyright (c) 2014, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.oracle.graal.python.builtins.objects.bytes;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import java.io.UnsupportedEncodingException;
import java.util.List;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PByteArray, PythonBuiltinClassType.PBytes})
public class AbstractBytesBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return AbstractBytesBuiltinsFactory.getFactories();
    }

    @Builtin(name = "lower", fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class LowerNode extends PythonUnaryBuiltinNode {
        @Node.Child private BytesNodes.ToBytesNode toBytes = BytesNodes.ToBytesNode.create();

        @CompilerDirectives.TruffleBoundary
        private static byte[] lower(byte[] bytes) {
            try {
                String string = new String(bytes, "ASCII");
                return string.toLowerCase().getBytes("ASCII");
            } catch (UnsupportedEncodingException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException();
            }
        }

        @Specialization
        PByteArray replace(PByteArray self) {
            return factory().createByteArray(lower(toBytes.execute(self)));
        }

        @Specialization
        PBytes replace(PBytes self) {
            return factory().createBytes(lower(toBytes.execute(self)));
        }
    }

    @Builtin(name = "upper", fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class UpperNode extends PythonUnaryBuiltinNode {
        @Node.Child private BytesNodes.ToBytesNode toBytes = BytesNodes.ToBytesNode.create();

        @CompilerDirectives.TruffleBoundary
        private static byte[] upper(byte[] bytes) {
            try {
                String string = new String(bytes, "ASCII");
                return string.toUpperCase().getBytes("ASCII");
            } catch (UnsupportedEncodingException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException();
            }
        }

        @Specialization
        PByteArray replace(PByteArray self) {
            return factory().createByteArray(upper(toBytes.execute(self)));
        }

        @Specialization
        PBytes replace(PBytes self) {
            return factory().createBytes(upper(toBytes.execute(self)));
        }
    }
}
