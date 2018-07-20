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

import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.util.ArrayList;

import com.oracle.graal.python.builtins.objects.bytes.BytesNodesFactory.BytesJoinNodeGen;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodesFactory.ToBytesNodeGen;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.nodes.PBaseNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.control.GetIteratorNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.ConditionProfile;

public abstract class BytesNodes {

    public abstract static class BytesJoinNode extends PBaseNode {

        public abstract byte[] execute(byte[] sep, Object iterable);

        @Specialization
        public byte[] join(byte[] sep, Object iterable,
                        @Cached("create()") GetIteratorNode getIteratorNode,
                        @Cached("create()") GetNextNode getNextNode,
                        @Cached("create()") ToBytesNode toBytesNode,
                        @Cached("createBinaryProfile()") ConditionProfile errorProfile) {
            ArrayList<byte[]> parts = new ArrayList<>();
            int partsTotalSize = 0;
            Object iterator = getIteratorNode.executeWith(iterable);
            while (true) {
                try {
                    partsTotalSize += append(parts, toBytesNode.execute(getNextNode.execute(iterator)));
                } catch (PException e) {
                    e.expectStopIteration(getCore(), errorProfile);
                    return joinArrays(sep, parts, partsTotalSize);
                }
            }
        }

        @TruffleBoundary(allowInlining = true, transferToInterpreterOnException = false)
        private static int append(ArrayList<byte[]> parts, byte[] barr) {
            parts.add(barr);
            return barr.length;
        }

        @TruffleBoundary(allowInlining = true, transferToInterpreterOnException = false)
        private static byte[] joinArrays(byte[] sep, ArrayList<byte[]> parts, int partsTotalSize) {
            byte[] joinedBytes = new byte[partsTotalSize + (parts.size() - 1) * sep.length];
            int offset = 0;
            byte[] array = parts.get(0);
            System.arraycopy(array, 0, joinedBytes, offset, array.length);
            offset += array.length;
            for (int i = 1; i < parts.size(); i++) {
                array = parts.get(i);
                System.arraycopy(sep, 0, joinedBytes, offset, sep.length);
                offset += sep.length;
                System.arraycopy(array, 0, joinedBytes, offset, array.length);
                offset += array.length;
            }
            return joinedBytes;
        }

        public static BytesJoinNode create() {
            return BytesJoinNodeGen.create();
        }
    }

    @ImportStatic(PGuards.class)
    abstract static class ToBytesNode extends PBaseNode {

        static final String TO_BYTES = "tobytes";

        protected final boolean allowRecursive;

        ToBytesNode(boolean allowRecursive) {
            this.allowRecursive = allowRecursive;
        }

        public abstract byte[] execute(Object obj);

        @Specialization
        byte[] doBytes(PBytes bytes) {
            return bytes.getInternalByteArray();
        }

        @Specialization
        byte[] doByteArray(PByteArray byteArray) {
            return byteArray.getInternalByteArray();
        }

        @Specialization(guards = "isByteStorage(sequence)")
        byte[] doSequence(PSequence sequence) {
            return ((ByteSequenceStorage) sequence.getSequenceStorage()).getInternalByteArray();
        }

        @Specialization(guards = "allowRecursive")
        byte[] doMemoryView(PMemoryView memoryView,
                        @Cached("createRecursive()") ToBytesNode recursive,
                        @Cached("create(TO_BYTES)") LookupAndCallUnaryNode callToBytesNode) {
            return recursive.execute(callToBytesNode.executeObject(memoryView));
        }

        @Fallback
        byte[] doError(Object obj) {
            throw raise(TypeError, "expected a bytes-like object, %p found", obj);
        }

        protected ToBytesNode createRecursive() {
            return ToBytesNode.create(false);
        }

        public static ToBytesNode create() {
            return ToBytesNodeGen.create(true);
        }

        public static ToBytesNode create(boolean allowRecursive) {
            return ToBytesNodeGen.create(allowRecursive);
        }
    }

}
