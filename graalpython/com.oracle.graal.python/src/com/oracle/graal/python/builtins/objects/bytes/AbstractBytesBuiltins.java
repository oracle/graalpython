/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.python.builtins.objects.bytes;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
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

    abstract static class AStripNode extends PythonBinaryBuiltinNode {
        int mod() {
            throw new RuntimeException();
        }

        int stop(@SuppressWarnings("unused") byte[] bs) {
            throw new RuntimeException();
        }

        int start(@SuppressWarnings("unused") byte[] bs) {
            throw new RuntimeException();
        }

        PByteArray newByteArrayFrom(@SuppressWarnings("unused") byte[] bs, @SuppressWarnings("unused") int i) {
            throw new RuntimeException();
        }

        PBytes newBytesFrom(@SuppressWarnings("unused") byte[] bs, @SuppressWarnings("unused") int i) {
            throw new RuntimeException();
        }

        private int findIndex(byte[] bs) {
            int i = start(bs);
            int stop = stop(bs);
            for (; i != stop; i += mod()) {
                if (!isWhitespace(bs[i])) {
                    break;
                }
            }
            return i;
        }

        @Specialization
        PByteArray strip(PByteArray self, @SuppressWarnings("unused") PNone bytes,
                        @Cached("create()") BytesNodes.ToBytesNode toBytesNode) {
            byte[] bs = toBytesNode.execute(self);
            return newByteArrayFrom(bs, findIndex(bs));
        }

        @Specialization
        PBytes strip(PBytes self, @SuppressWarnings("unused") PNone bytes,
                        @Cached("create()") BytesNodes.ToBytesNode toBytesNode) {
            byte[] bs = toBytesNode.execute(self);
            return newBytesFrom(bs, findIndex(bs));
        }

        @CompilerDirectives.TruffleBoundary
        private static boolean isWhitespace(byte b) {
            return Character.isWhitespace(b);
        }

        private int findIndex(byte[] bs, byte[] stripBs) {
            int i = start(bs);
            int stop = stop(bs);
            outer: for (; i != stop; i += mod()) {
                for (byte b : stripBs) {
                    if (b == bs[i]) {
                        continue outer;
                    }
                }
                break;
            }
            return i;
        }

        @Specialization
        PByteArray strip(PByteArray self, PBytes bytes,
                        @Cached("create()") BytesNodes.ToBytesNode selfToBytesNode,
                        @Cached("create()") BytesNodes.ToBytesNode otherToBytesNode) {
            byte[] stripBs = selfToBytesNode.execute(bytes);
            byte[] bs = otherToBytesNode.execute(self);
            return newByteArrayFrom(bs, findIndex(bs, stripBs));
        }

        @Specialization
        PBytes strip(PBytes self, PBytes bytes,
                        @Cached("create()") BytesNodes.ToBytesNode selfToBytesNode,
                        @Cached("create()") BytesNodes.ToBytesNode otherToBytesNode) {
            byte[] stripBs = selfToBytesNode.execute(bytes);
            byte[] bs = otherToBytesNode.execute(self);
            return newBytesFrom(bs, findIndex(bs, stripBs));
        }

    }

    @Builtin(name = "lstrip", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, keywordArguments = {"bytes"})
    @GenerateNodeFactory
    abstract static class LStripNode extends AStripNode {

        private static byte[] getResultBytes(int i, byte[] bs) {
            byte[] out;
            if (i != 0) {
                int len = bs.length - i;
                out = new byte[len];
                System.arraycopy(bs, i, out, 0, len);
            } else {
                out = bs;
            }
            return out;
        }

        @Override
        PByteArray newByteArrayFrom(byte[] bs, int i) {
            return factory().createByteArray(getResultBytes(i, bs));
        }

        @Override
        PBytes newBytesFrom(byte[] bs, int i) {
            return factory().createBytes(getResultBytes(i, bs));
        }

        @Override
        int mod() {
            return 1;
        }

        @Override
        int stop(byte[] bs) {
            return bs.length;
        }

        @Override
        int start(byte[] bs) {
            return 0;
        }
    }

    @Builtin(name = "rstrip", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, keywordArguments = {"bytes"})
    @GenerateNodeFactory
    abstract static class RStripNode extends AStripNode {

        private static byte[] getResultBytes(int i, byte[] bs) {
            byte[] out;
            int len = i + 1;
            if (len != bs.length) {
                out = new byte[len];
                System.arraycopy(bs, 0, out, 0, len);
            } else {
                out = bs;
            }
            return out;
        }

        @Override
        PByteArray newByteArrayFrom(byte[] bs, int i) {
            byte[] out = getResultBytes(i, bs);
            return factory().createByteArray(out);
        }

        @Override
        PBytes newBytesFrom(byte[] bs, int i) {
            byte[] out = getResultBytes(i, bs);
            return factory().createBytes(out);
        }

        @Override
        int mod() {
            return -1;
        }

        @Override
        int stop(byte[] bs) {
            return -1;
        }

        @Override
        int start(byte[] bs) {
            return bs.length - 1;
        }
    }
}
