/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 *     one is included with the Software (each a "Larger Work" to which the
 *     Software is contributed by such licensors),
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
package com.oracle.graal.python.builtins.objects.cext;

import com.oracle.graal.python.builtins.modules.TruffleCextBuiltins.AsPythonObjectNode;
import com.oracle.graal.python.builtins.modules.TruffleCextBuiltins.ToSulongNode;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.cext.PySequenceArrayWrapperMRFactory.ReadArrayItemNodeGen;
import com.oracle.graal.python.builtins.objects.cext.PySequenceArrayWrapperMRFactory.WriteArrayItemNodeGen;
import com.oracle.graal.python.builtins.objects.list.ListBuiltins;
import com.oracle.graal.python.builtins.objects.list.ListBuiltinsFactory;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.tuple.TupleBuiltins;
import com.oracle.graal.python.builtins.objects.tuple.TupleBuiltinsFactory;
import com.oracle.graal.python.nodes.PBaseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.truffle.PythonTypes;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;

@MessageResolution(receiverType = PySequenceArrayWrapper.class)
public class PySequenceArrayWrapperMR {

    @Resolve(message = "READ")
    abstract static class ReadNode extends Node {
        @Child private ReadArrayItemNode readArrayItemNode;
        @Child private ToSulongNode toSulongNode;

        public Object access(PySequenceArrayWrapper object, Object key) {
            return getToSulongNode().execute(getReadArrayItemNode().execute(object.getDelegate(), key));
        }

        private ReadArrayItemNode getReadArrayItemNode() {
            if (readArrayItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                readArrayItemNode = insert(ReadArrayItemNode.create());
            }
            return readArrayItemNode;
        }

        private ToSulongNode getToSulongNode() {
            if (toSulongNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toSulongNode = insert(ToSulongNode.create());
            }
            return toSulongNode;
        }
    }

    @Resolve(message = "WRITE")
    abstract static class WriteNode extends Node {
        @Child private WriteArrayItemNode writeArrayItemNode;
        @Child private ToJavaNode toJavaNode;

        public Object access(PySequenceArrayWrapper object, Object key, Object value) {
            if (writeArrayItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                writeArrayItemNode = insert(WriteArrayItemNode.create());
            }
            writeArrayItemNode.execute(object.getDelegate(), key, getToJavaNode().execute(value));

            // A C expression assigning to an array returns the assigned value.
            return value;
        }

        private ToJavaNode getToJavaNode() {
            if (toJavaNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toJavaNode = insert(ToJavaNode.create());
            }
            return toJavaNode;
        }
    }

    /**
     * Does the same conversion as the native function {@code to_java}.
     */
    static class ToJavaNode extends PBaseNode {
        @Child private PCallNativeNode callNativeNode = PCallNativeNode.create(1);
        @Child private AsPythonObjectNode toJavaNode = AsPythonObjectNode.create();

        @CompilationFinal TruffleObject nativeToJavaFunction;

        Object execute(Object value) {
            if (nativeToJavaFunction == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                nativeToJavaFunction = (TruffleObject) getContext().getEnv().importSymbol(NativeCAPISymbols.FUNCTION_NATIVE_TO_JAVA);
            }
            return toJavaNode.execute(callNativeNode.execute(nativeToJavaFunction, new Object[]{value}));
        }

        public static ToJavaNode create() {
            return new ToJavaNode();
        }

    }

    @ImportStatic(SpecialMethodNames.class)
    @TypeSystemReference(PythonTypes.class)
    abstract static class ReadArrayItemNode extends Node {

        public abstract Object execute(Object arrayObject, Object idx);

        @Specialization
        Object doTuple(PTuple tuple, long idx,
                        @Cached("createTupleGetItem()") TupleBuiltins.GetItemNode getItemNode) {
            return getItemNode.execute(tuple, idx);
        }

        @Specialization
        Object doTuple(PList list, long idx,
                        @Cached("createListGetItem()") ListBuiltins.GetItemNode getItemNode) {
            return getItemNode.execute(list, idx);
        }

        @Specialization
        Object doTuple(PBytes tuple, long idx) {
            // simulate sentinel value
            if (idx == tuple.len()) {
                return (byte) 0;
            }
            return tuple.getInternalByteArray()[(int) idx];
        }

        protected static ListBuiltins.GetItemNode createListGetItem() {
            return ListBuiltinsFactory.GetItemNodeFactory.create(null);
        }

        protected static TupleBuiltins.GetItemNode createTupleGetItem() {
            return TupleBuiltinsFactory.GetItemNodeFactory.create(null);
        }

        public static ReadArrayItemNode create() {
            return ReadArrayItemNodeGen.create();
        }
    }

    @ImportStatic(SpecialMethodNames.class)
    @TypeSystemReference(PythonTypes.class)
    abstract static class WriteArrayItemNode extends Node {

        public abstract Object execute(Object arrayObject, Object idx, Object value);

        @Specialization
        Object doTuple(PTuple tuple, long idx, Object value) {
            Object[] store = tuple.getArray();
            store[(int) idx] = value;
            return value;
        }

        @Specialization
        Object doTuple(PList list, long idx, Object value,
                        @Cached("createListSetItem()") ListBuiltins.SetItemNode setItemNode) {
            return setItemNode.execute(list, idx, value);
        }

        @Specialization
        Object doTuple(PBytes tuple, long idx, byte value) {
            tuple.getInternalByteArray()[(int) idx] = value;
            return value;
        }

        protected static ListBuiltins.SetItemNode createListSetItem() {
            return ListBuiltinsFactory.SetItemNodeFactory.create(null);
        }

        public static WriteArrayItemNode create() {
            return WriteArrayItemNodeGen.create();
        }
    }
}
