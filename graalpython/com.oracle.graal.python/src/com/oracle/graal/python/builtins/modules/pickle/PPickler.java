/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.pickle;

import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___NAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___NEWOBJ_EX__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___NEWOBJ__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___QUALNAME__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T_ITEMS;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___NEW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___REDUCE_EX__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___REDUCE__;
import static com.oracle.graal.python.nodes.StringLiterals.T_NEWLINE;
import static com.oracle.graal.python.nodes.StringLiterals.T_UTF8;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.KeyError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.PicklingError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import org.graalvm.collections.Pair;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorKey;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorNext;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorValue;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageLen;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.ellipsis.PEllipsis;
import com.oracle.graal.python.builtins.objects.ints.IntNodes;
import com.oracle.graal.python.builtins.objects.ints.IntNodesFactory;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.lib.PyCallableCheckNode;
import com.oracle.graal.python.lib.PyFloatAsDoubleNode;
import com.oracle.graal.python.lib.PyLongAsLongNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectReprAsTruffleStringNode;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.lib.PyObjectStrAsObjectNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.builtins.ListNodes;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.Consumer;
import com.oracle.graal.python.util.NumericSupport;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.strings.TruffleString;

public class PPickler extends PythonBuiltinObject {
    // Memo table, keep track of the seen objects to support self-referential objects pickling
    private MemoTable memo;
    // persistent_id() method, can be NULL
    private Object persFunc;
    // borrowed reference to self if pers_func is an unbound method, NULL otherwise
    private Object persFuncSelf;
    // private dispatch_table, can be NULL
    private Object dispatchTable;
    // hook for invoking user-defined callbacks instead of save_global when pickling functions and
    // classes
    private Object reducerOverride;
    // write() method of the output stream.
    private Object write;
    // Write into a local bytearray buffer before flushing to the stream.
    private byte[] outputBuffer;
    // Length of output_buffer
    private int outputLen;
    // Allocation size of output_buffer
    private int maxOutputLen;
    // Pickle protocol number, >= 0
    private int proto;
    // true if proto > 0
    private int bin;
    // True when framing is enabled, proto >= 4
    private boolean framing;
    // Position in output_buffer where the current frame begins. -1 if there is no frame currently
    // open.
    private int frameStart;
    // Enable fast mode if set to a true value. The fast mode disable the usage of memo,
    // therefore speeding the pickling process by not generating superfluous PUT opcodes. It
    // should not be used if with self-referential objects
    private int fast;
    private int fastNesting;
    // Indicate whether Pickler should fix the name of globals for Python 2.x.
    private boolean fixImports;
    private final Map<Object, Object> fastMemo = createFastMemoTable();
    // Callback for out-of-band buffers, or NULL
    private Object bufferCallback;

    public PPickler(Object cls, Shape instanceShape) {
        super(cls, instanceShape);
        persFunc = null;
        dispatchTable = null;
        bufferCallback = null;
        write = null;
        proto = 0;
        bin = 0;
        framing = false;
        frameStart = -1;
        fast = 0;
        fastNesting = 0;
        fixImports = false;
        maxOutputLen = PickleUtils.WRITE_BUF_SIZE;
        outputLen = 0;
        reducerOverride = null;

        memo = new MemoTable();
        outputBuffer = new byte[maxOutputLen];
    }

    @TruffleBoundary
    private static Map<Object, Object> createFastMemoTable() {
        return Collections.synchronizedMap(new WeakHashMap<>());
    }

    public int getProto() {
        return proto;
    }

    public int getBin() {
        return bin;
    }

    public void setBin(int bin) {
        this.bin = bin;
    }

    public boolean isBin() {
        return bin != 0;
    }

    public int getFast() {
        return fast;
    }

    public void setFast(int fast) {
        this.fast = fast;
    }

    public boolean isFast() {
        return fast != 0;
    }

    public boolean isFraming() {
        return framing;
    }

    public Object getWrite() {
        return write;
    }

    public Object getDispatchTable() {
        return dispatchTable;
    }

    public void setDispatchTable(Object dispatchTable) {
        this.dispatchTable = dispatchTable;
    }

    public MemoTable getMemo() {
        return memo;
    }

    public void setMemo(MemoTable memo) {
        this.memo = memo;
    }

    public Object getPersFunc() {
        return persFunc;
    }

    public void setPersFunc(Object persFunc) {
        this.persFunc = persFunc;
    }

    public Object getPersFuncSelf() {
        return persFuncSelf;
    }

    public void setPersFuncSelf(Object persFuncSelf) {
        this.persFuncSelf = persFuncSelf;
    }

    @TruffleBoundary
    private boolean fastMemoContains(Object object) {
        return this.fastMemo.containsKey(object);
    }

    @TruffleBoundary
    private void fastMemoPut(Object object) {
        this.fastMemo.put(object, true);
    }

    @TruffleBoundary
    private void fastMemoRemove(Object object) {
        this.fastMemo.remove(object);
    }

    // helper methods
    public void setProtocol(Node inliningTarget, PRaiseNode.Lazy raiseNode, int protocol, boolean fixImports) {
        proto = protocol;
        if (proto < 0) {
            proto = PickleUtils.PICKLE_PROTOCOL_HIGHEST;
        } else if (proto > PickleUtils.PICKLE_PROTOCOL_HIGHEST) {
            throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.ValueError, ErrorMessages.PICKLE_PROTO_MUST_BE_LE, PickleUtils.PICKLE_PROTOCOL_HIGHEST);
        }

        this.bin = (proto > 0) ? 1 : 0;
        this.fixImports = fixImports && proto < 3;
    }

    public void setOutputStream(VirtualFrame frame, Node inliningTarget, PRaiseNode.Lazy raiseNode, PyObjectLookupAttr lookup, Object file) {
        write = lookup.execute(frame, inliningTarget, file, PickleUtils.T_METHOD_WRITE);
        if (write == PNone.NO_VALUE) {
            throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, ErrorMessages.FILE_MUST_HAVE_WRITE_ATTR);
        }
    }

    public void setBufferCallback(Node inliningTarget, PRaiseNode.Lazy raiseNode, Object callback) {
        bufferCallback = callback;
        if (PGuards.isNone(callback) || PGuards.isNoValue(callback)) {
            bufferCallback = null;
        }
        if (bufferCallback != null && proto < 5) {
            throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.ValueError, ErrorMessages.BUFFCB_NEEDS_PROTO_GE_5);
        }
    }

    public void initInternals(VirtualFrame frame, Node inliningTarget, PyObjectLookupAttr lookup) {
        if (this.memo == null) {
            this.memo = new MemoTable();
        }

        this.outputLen = 0;
        if (this.outputBuffer == null) {
            this.maxOutputLen = PickleUtils.WRITE_BUF_SIZE;
            this.outputBuffer = new byte[this.maxOutputLen];
        }

        this.fast = 0;
        this.fastNesting = 0;

        final Pair<Object, Object> pair = PickleUtils.initMethodRef(frame, inliningTarget, lookup, this, PickleUtils.T_METHOD_PERSISTENT_ID);
        this.persFunc = pair.getLeft();
        this.persFuncSelf = pair.getRight();

        this.dispatchTable = lookup.execute(frame, inliningTarget, this, PickleUtils.T_ATTR_DISPATCH_TABLE);
        if (this.dispatchTable == PNone.NO_VALUE) {
            this.dispatchTable = null;
        }
    }

    public void clearMemo() {
        this.memo.clear();
    }

    public void clearBuffer() {
        this.outputBuffer = new byte[this.maxOutputLen];
        this.outputLen = 0;
        this.frameStart = -1;
    }

    public void commitFrame() {
        if (!isFraming() || frameStart == -1) {
            return;
        }

        int frameLen = outputLen - frameStart - PickleUtils.FRAME_HEADER_SIZE;
        ByteArrayView qdata = new ByteArrayView(outputBuffer, frameStart);
        if (frameLen >= PickleUtils.FRAME_SIZE_MIN) {
            qdata.put(0, PickleUtils.OPCODE_FRAME);
            qdata.add(1);
            qdata.writeSize64(frameLen);
        } else {
            qdata.memmove(PickleUtils.FRAME_HEADER_SIZE, frameLen);
            outputLen -= PickleUtils.FRAME_HEADER_SIZE;
        }
        frameStart = -1;
    }

    public PBytes getString(PythonObjectFactory factory) {
        commitFrame();
        return factory.createBytes(outputBuffer, outputLen);
    }

    // inner nodes
    public abstract static class BasePickleWriteNode extends PicklerNodes.BasePickleNode {
        static final byte[] TUPLE_LEN_2_OPCODE = new byte[]{PickleUtils.OPCODE_EMPTY_TUPLE, PickleUtils.OPCODE_TUPLE1, PickleUtils.OPCODE_TUPLE2, PickleUtils.OPCODE_TUPLE3};
        static final byte NEW_LINE_BYTE = '\n';
        static final TruffleString DICT_ITEMS = tsLiteral("dict items");
        static final TruffleString LATIN1 = tsLiteral("latin1");
        static final TruffleString REDUCE_OVERRIDE = tsLiteral("reducer_override");
        static final TruffleString T_L_NEW_LINE = tsLiteral("L\n");
        static final TruffleString T_SET = tsLiteral("set");
        static final TruffleString T_DICTIONARY = tsLiteral("dictionary");

        @Child private IntNodes.PyLongSign pyLongSign;
        @Child private IntNodes.PyLongNumBits pyLongNumBits;
        @Child private IntNodes.PyLongAsByteArray pyLongAsByteArray;
        @Child private ListNodes.ConstructListNode constructListNode;
        @Child private IsSubtypeNode isSubTypeNode;
        @Child private TypeNodes.IsTypeNode isTypeNode;
        @Child private FlushToFileNode flushToFileNode;
        @Child private CallNode callNode;

        protected boolean isType(Object obj) {
            if (isTypeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isTypeNode = insert(TypeNodes.IsTypeNode.create());
            }
            return isTypeNode.executeCached(obj);
        }

        protected void flushToFile(VirtualFrame frame, PPickler pickler) {
            if (flushToFileNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                flushToFileNode = insert(PPicklerFactory.FlushToFileNodeGen.create());
            }
            flushToFileNode.execute(frame, pickler);
        }

        protected int getSign(Object value) {
            if (pyLongSign == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                pyLongSign = insert(IntNodesFactory.PyLongSignNodeGen.create());
            }
            return pyLongSign.execute(value);
        }

        protected int getNumBits(Object value) {
            if (pyLongNumBits == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                pyLongNumBits = insert(IntNodesFactory.PyLongNumBitsNodeGen.create());
            }
            return pyLongNumBits.execute(value);
        }

        protected byte[] longAsBytes(Object value, int size, boolean bigEndian) {
            if (pyLongAsByteArray == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                pyLongAsByteArray = insert(IntNodesFactory.PyLongAsByteArrayNodeGen.create());
            }
            return pyLongAsByteArray.executeCached(value, size, bigEndian);
        }

        protected Object createList(VirtualFrame frame, Object iterable) {
            if (constructListNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                constructListNode = insert(ListNodes.ConstructListNode.create());
            }
            return constructListNode.execute(frame, iterable);
        }

        protected boolean isSubType(Object clsA, Object clsB) {
            if (isSubTypeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isSubTypeNode = insert(IsSubtypeNode.create());
            }
            return isSubTypeNode.execute(clsA, clsB);
        }

        protected CallNode getCallNode() {
            if (callNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callNode = insert(CallNode.create());
            }
            return callNode;
        }

        public void opcodeBoundary(VirtualFrame frame, PPickler pickler) {
            if (!pickler.isFraming() || pickler.frameStart == -1) {
                return;
            }
            int frameLen = pickler.outputLen - pickler.frameStart - PickleUtils.FRAME_HEADER_SIZE;
            if (frameLen >= PickleUtils.FRAME_SIZE_TARGET) {
                pickler.commitFrame();
                // Flush the content of the committed frame to the underlying
                // file and reuse the pickler buffer for the next frame so as
                // to limit memory usage when dumping large complex objects to
                // a file.
                if (pickler.write != null) {
                    flushToFile(frame, pickler);
                    pickler.clearBuffer();
                }
            }
        }

        public static void handleError(PPickler pickler) {
            pickler.framing = false;
            pickler.reducerOverride = null;
        }

        protected void writeASCII(PPickler pickler, TruffleString string) {
            assert string.getCodeRangeUncached(TS_ENCODING) == TruffleString.CodeRange.ASCII;
            final byte[] bytes = PythonUtils.getAsciiBytes(string, ensureTsCopyToByteArrayNode(), ensureTsSwitchEncodingNode());
            write(pickler, bytes, bytes.length);
        }

        protected void write(PPickler pickler, byte oneByte) {
            write(pickler, new byte[]{oneByte}, 1);
        }

        protected void write(PPickler pickler, byte[] bytes) {
            write(pickler, bytes, bytes.length);
        }

        protected void write(PPickler pickler, byte[] bytes, int dataLen) {
            boolean needNewFrame = pickler.isFraming() && pickler.frameStart == -1;
            int n = (needNewFrame) ? dataLen + PickleUtils.FRAME_HEADER_SIZE : dataLen;
            int required = pickler.outputLen + n;

            if (required > pickler.maxOutputLen) {
                // Make place in buffer for the pickle chunk
                // TODO: when GR-24978 is completed we should use PY_SSIZE_T_MAX
                if (pickler.outputLen >= Integer.MAX_VALUE / 2 - n) {
                    throw raise(PythonBuiltinClassType.MemoryError);
                }
                pickler.maxOutputLen = (pickler.outputLen + n) / 2 * 3;
                pickler.outputBuffer = PickleUtils.resize(pickler.outputBuffer, pickler.maxOutputLen);
            }
            if (needNewFrame) {
                int frameStart = pickler.outputLen;
                pickler.frameStart = frameStart;
                for (int i = 0; i < PickleUtils.FRAME_HEADER_SIZE; i++) {
                    // Write an invalid value, for debugging
                    pickler.outputBuffer[frameStart + i] = (byte) 0xFE;
                }
                pickler.outputLen += PickleUtils.FRAME_HEADER_SIZE;
            }
            PythonUtils.arraycopy(bytes, 0, pickler.outputBuffer, pickler.outputLen, dataLen);
            pickler.outputLen += dataLen;
        }

        protected void writeBytes(VirtualFrame frame, PPickler pickler, byte[] header, int headerSize, byte[] data, int dataSize, Object payload) {
            boolean bypassBuffer = dataSize >= PickleUtils.FRAME_SIZE_TARGET;
            boolean framing = pickler.framing;

            if (bypassBuffer) {
                assert pickler.outputBuffer != null;
                // Commit the previous frame.
                pickler.commitFrame();
                // Disable framing temporarily
                pickler.framing = false;
            }

            write(pickler, header, headerSize);

            if (bypassBuffer && pickler.write != null) {
                // Dump the output buffer to the file.
                flushToFile(frame, pickler);
                // Stream write the payload into the file without going through the output buffer.
                Object pld = payload;
                if (pld == null) {
                    // TODO: It would be better to use a memoryview with a linked original string if
                    // this is possible.
                    pld = factory().createBytes(data, 0, dataSize);
                }
                getCallNode().execute(frame, pickler.write, pld);
                // Reinitialize the buffer for subsequent calls to _Pickler_Write.
                pickler.clearBuffer();
            } else {
                write(pickler, data, dataSize);
            }

            // Re-enable framing for subsequent calls to _Pickler_Write.
            pickler.framing = framing;
        }

        protected PTuple createTuple(Object... items) {
            if (items.length == 0) {
                return factory().createEmptyTuple();
            }
            return factory().createTuple(items);
        }

        protected void fastSaveEnter(PPickler pickler, Object obj) {
            // if fast_nesting < 0, we're doing an error exit.
            if (++pickler.fastNesting >= PickleUtils.FAST_NESTING_LIMIT) {
                if (pickler.fastMemoContains(obj)) {
                    pickler.fastNesting = -1;
                    throw raise(ValueError, ErrorMessages.FAST_MEMO_CANT_PICKLE_CYCLIC_OBJ_P_S, obj, obj);
                }
                pickler.fastMemoPut(obj);
            }
        }

        protected static void fastSaveLeave(PPickler pickler, Object obj) {
            if (pickler.fastNesting-- >= PickleUtils.FAST_NESTING_LIMIT) {
                pickler.fastMemoRemove(obj);
            }
        }
    }

    public abstract static class FlushToFileNode extends BasePickleWriteNode {
        public abstract void execute(VirtualFrame frame, PPickler pickler);

        @Specialization
        public void flush(VirtualFrame frame, PPickler pickler) {
            assert pickler.write != null;
            // This will commit the frame first
            PBytes output = pickler.getString(factory());
            call(frame, pickler.write, output);
        }
    }

    public abstract static class SaveNode extends BasePickleWriteNode {
        @Child private SaveNode recursiveSaveNode;
        @Child private PyLongAsLongNode pyLongAsLongNode;
        @Child private PyObjectStrAsObjectNode pyObjectStrAsObjectNode;
        @Child private PyObjectIsTrueNode isTrueNode;
        @Child private PythonBufferAcquireLibrary bufferAcquireLibrary;
        @Child private PythonBufferAccessLibrary bufferLibrary;
        @Child private PyCallableCheckNode callableCheckNode;
        @Child private PyObjectReprAsTruffleStringNode reprNode;
        @Child private PyObjectGetIter getIterNode;
        @Child private HashingStorageLen hashingStorageLenNode;

        private int getHashingStorageLength(HashingStorage storage) {
            if (hashingStorageLenNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                hashingStorageLenNode = insert(HashingStorageLen.create());
            }
            return hashingStorageLenNode.executeCached(storage);
        }

        private void save(VirtualFrame frame, PPickler pickler, Object obj, int persSave) {
            if (recursiveSaveNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                recursiveSaveNode = insert(PPicklerFactory.SaveNodeGen.create());
            }
            recursiveSaveNode.execute(frame, pickler, obj, persSave);
        }

        private long asLong(VirtualFrame frame, Object object) {
            if (pyLongAsLongNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                pyLongAsLongNode = insert(PyLongAsLongNode.create());
            }
            return pyLongAsLongNode.executeCached(frame, object);
        }

        private Object convertToStr(VirtualFrame frame, Object object) {
            if (pyObjectStrAsObjectNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                pyObjectStrAsObjectNode = insert(PyObjectStrAsObjectNode.create());
            }
            return pyObjectStrAsObjectNode.executeCached(frame, object);
        }

        private boolean isTrue(VirtualFrame frame, Object object) {
            if (isTrueNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                isTrueNode = insert(PyObjectIsTrueNode.create());
            }
            return isTrueNode.executeCached(frame, object);
        }

        private boolean isCallable(Object object) {
            if (callableCheckNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callableCheckNode = insert(PyCallableCheckNode.create());
            }
            return callableCheckNode.execute(object);
        }

        private TruffleString repr(VirtualFrame frame, Object object) {
            if (reprNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                reprNode = insert(PyObjectReprAsTruffleStringNode.create());
            }
            return reprNode.executeCached(frame, object);
        }

        private Object getIter(VirtualFrame frame, Object object) {
            if (getIterNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getIterNode = insert(PyObjectGetIter.create());
            }
            return getIterNode.executeCached(frame, object);
        }

        private PythonBufferAcquireLibrary getBufferAcquireLibrary() {
            if (bufferAcquireLibrary == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                bufferAcquireLibrary = insert(PythonBufferAcquireLibrary.getFactory().createDispatched(3));
            }
            return bufferAcquireLibrary;
        }

        private PythonBufferAccessLibrary getBufferLibrary() {
            if (bufferLibrary == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                bufferLibrary = insert(PythonBufferAccessLibrary.getFactory().createDispatched(3));
            }
            return bufferLibrary;
        }

        public abstract void execute(VirtualFrame frame, PPickler pickler, Object obj, int persSave);

        // collections
        private void saveHashingStorageBatched(VirtualFrame frame, PPickler pickler, HashingStorage storage,
                        byte opcodeBatch, TruffleString name, boolean saveValues) {
            int initialSize = getHashingStorageLength(storage);
            HashingStorageIterator it = getHashingStorageIterator(storage);
            HashingStorageIteratorNext nextNode = ensureHashingStorageIteratorNext();
            HashingStorageIteratorKey getKeyNode = ensureHashingStorageIteratorKey();
            HashingStorageIteratorValue getValueNode = null;
            if (saveValues) {
                getValueNode = ensureHashingStorageIteratorValue();
            }
            int i;
            do {
                i = 0;
                write(pickler, PickleUtils.OPCODE_MARK);
                while (nextNode.executeCached(storage, it)) {
                    save(frame, pickler, getKeyNode.executeCached(storage, it), 0);
                    if (saveValues) {
                        save(frame, pickler, getValueNode.executeCached(storage, it), 0);
                    }
                    if (++i == PickleUtils.BATCHSIZE) {
                        break;
                    }
                }
                write(pickler, opcodeBatch);
                if (getHashingStorageLength(storage) != initialSize) {
                    throw raise(PythonBuiltinClassType.RuntimeError, ErrorMessages.CHANGED_SIZE_DURING_ITERATION, name);
                }
            } while (i == PickleUtils.BATCHSIZE);
        }

        private void saveDictHashingStorageBatched(VirtualFrame frame, PPickler pickler, HashingStorage storage) {
            saveHashingStorageBatched(frame, pickler, storage, PickleUtils.OPCODE_SETITEMS, T_DICTIONARY, true);
        }

        private void saveSetHashingStorageBatched(VirtualFrame frame, PPickler pickler, HashingStorage storage) {
            saveHashingStorageBatched(frame, pickler, storage, PickleUtils.OPCODE_ADDITEMS, T_SET, false);
        }

        private void saveSetHashingStorage(VirtualFrame frame, PPickler pickler, HashingStorage storage) {
            HashingStorageIterator it = getHashingStorageIterator(storage);
            HashingStorageIteratorNext next = ensureHashingStorageIteratorNext();
            HashingStorageIteratorKey getKeyNode = ensureHashingStorageIteratorKey();
            while (next.executeCached(storage, it)) {
                Object item = getKeyNode.executeCached(storage, it);
                save(frame, pickler, item, 0);
            }
        }

        private void saveIteratorBatchedUnrolled(VirtualFrame frame, PPickler pickler, Object iterator, byte opcodeOneItem, byte opcodeMultipleItems, Consumer<Object> checkItem,
                        Consumer<Object> saveItem) {
            int n;
            Object obj;
            do {
                // Get first item
                final Object firstItem = getNextItem(frame, iterator);
                if (firstItem == null) {
                    // nothing more to add
                    break;
                }
                checkItem.accept(firstItem);

                // Try to get a second item
                obj = getNextItem(frame, iterator);
                if (obj == null) {
                    // Only one item to write
                    saveItem.accept(firstItem);
                    write(pickler, opcodeOneItem);
                    break;
                }
                // More than one item to write
                // Pump out MARK, items, APPENDS.
                write(pickler, PickleUtils.OPCODE_MARK);
                saveItem.accept(firstItem);
                n = 1;
                while (true) {
                    // Fetch and save up to BATCHSIZE items
                    checkItem.accept(obj);
                    saveItem.accept(obj);
                    n += 1;

                    if (n == PickleUtils.BATCHSIZE) {
                        break;
                    }

                    obj = getNextItem(frame, iterator);
                    if (obj == null) {
                        break;
                    }
                }
                write(pickler, opcodeMultipleItems);

            } while (n == PickleUtils.BATCHSIZE);
        }

        private void saveDictIteratorBatchUnrolled(VirtualFrame frame, PPickler pickler, Object iterator) {
            saveIteratorBatchedUnrolled(frame, pickler, iterator, PickleUtils.OPCODE_SETITEM, PickleUtils.OPCODE_SETITEMS,
                            (Object item) -> {
                                if (!(item instanceof PTuple) || length(frame, item) != 2) {
                                    throw raise(TypeError, ErrorMessages.MUST_S_ITER_RETURN_2TUPLE, DICT_ITEMS);
                                }
                            },
                            (Object item) -> {
                                save(frame, pickler, getItem(frame, item, 0), 0);
                                save(frame, pickler, getItem(frame, item, 1), 0);
                            });
        }

        private void saveListIteratorBatchUnrolled(VirtualFrame frame, PPickler pickler, Object iterator) {
            saveIteratorBatchedUnrolled(frame, pickler, iterator, PickleUtils.OPCODE_APPEND, PickleUtils.OPCODE_APPENDS,
                            (Object item) -> {
                            },
                            (Object item) -> save(frame, pickler, item, 0));
        }

        private void saveIterator(VirtualFrame frame, PPickler pickler, Object iterator, byte opcode, Consumer<Object> itemConsumer) {
            while (true) {
                Object item = getNextItem(frame, iterator);
                if (item == null) {
                    break;
                }
                itemConsumer.accept(item);
                if (opcode != PickleUtils.NO_OPCODE) {
                    write(pickler, opcode);
                }
            }
        }

        private void saveListIterator(VirtualFrame frame, PPickler pickler, Object iterator) {
            saveIterator(frame, pickler, iterator, PickleUtils.OPCODE_APPEND,
                            (Object item) -> save(frame, pickler, item, 0));
        }

        private void saveFrozenSetIterator(VirtualFrame frame, PPickler pickler, Object iterator) {
            saveIterator(frame, pickler, iterator, PickleUtils.NO_OPCODE,
                            (Object item) -> save(frame, pickler, item, 0));
        }

        private void saveDictIterator(VirtualFrame frame, PPickler pickler, Object iterator) {
            saveIterator(frame, pickler, iterator, PickleUtils.OPCODE_SETITEM,
                            (Object item) -> {
                                if (!(item instanceof PTuple) || length(frame, item) != 2) {
                                    throw raise(TypeError, ErrorMessages.MUST_S_ITER_RETURN_2TUPLE, DICT_ITEMS);
                                }
                                save(frame, pickler, getItem(frame, item, 0), 0);
                                save(frame, pickler, getItem(frame, item, 1), 0);
                            });
        }

        // save methods

        private boolean savePers(VirtualFrame frame, PPickler pickler, Object obj) {
            Object pid;
            if (pickler.persFuncSelf == null) {
                pid = getCallNode().execute(frame, pickler.persFunc, obj);
            } else {
                pid = getCallNode().execute(frame, pickler.persFunc, pickler.persFuncSelf, obj);
            }
            if (pid != PNone.NONE) {
                if (pickler.isBin()) {
                    save(frame, pickler, pid, 1);
                    write(pickler, PickleUtils.OPCODE_BINPERSID);
                } else {
                    final TruffleString pidStr = asString(convertToStr(frame, pid));
                    // XXX: Should it check whether the pid contains embedded newlines?
                    if (!PythonUtils.isAscii(pidStr, ensureTsGetCodeRangeNode())) {
                        throw raise(PythonBuiltinClassType.PicklingError, ErrorMessages.PIDS_MUST_BE_ASCII_STRS);
                    }
                    write(pickler, PickleUtils.OPCODE_PERSID);
                    writeASCII(pickler, pidStr);
                    writeASCII(pickler, T_NEWLINE);
                }
                return true;
            }
            return false;
        }

        // memo methods
        private void memoGet(PPickler pickler, int value) {
            int len;
            byte[] pdata;
            if (pickler.isBin()) {
                pdata = new byte[5];
                if (value < 256) {
                    pdata[0] = PickleUtils.OPCODE_BINGET;
                    pdata[1] = (byte) (value & 0xff);
                    len = 2;
                } else if (Long.compareUnsigned(value, 0xffffffffL) <= 0) {
                    pdata[0] = PickleUtils.OPCODE_LONG_BINGET;
                    pdata[1] = (byte) (value & 0xff);
                    pdata[2] = (byte) ((value >> 8) & 0xff);
                    pdata[3] = (byte) ((value >> 16) & 0xff);
                    pdata[4] = (byte) ((value >> 24) & 0xff);
                    len = 5;
                } else {
                    // unlikely
                    throw raise(PicklingError, ErrorMessages.MEMO_ID_TOO_LARGE_FOR_S, "LONG_BINGET");
                }
            } else {
                pdata = new byte[30];
                pdata[0] = PickleUtils.OPCODE_GET;
                len = PickleUtils.toAsciiBytesWithNewLine(pdata, 1, value, ensureTsFromLongNode(), ensureTsCopyToByteArrayNode());
            }

            write(pickler, pdata, len);
        }

        private void memoPut(PPickler pickler, Object obj) {
            if (pickler.isFast()) {
                return;
            }

            int idx = pickler.memo.size();
            pickler.memo.set(obj, idx);

            if (pickler.proto >= 4) {
                write(pickler, PickleUtils.OPCODE_MEMOIZE);
                return;
            } else {
                byte[] pdata;
                int len;
                if (!pickler.isBin()) {
                    pdata = new byte[30];
                    pdata[0] = PickleUtils.OPCODE_PUT;
                    len = PickleUtils.toAsciiBytesWithNewLine(pdata, 1, idx, ensureTsFromLongNode(), ensureTsCopyToByteArrayNode());
                } else {
                    pdata = new byte[5];
                    if (idx < 256) {
                        pdata[0] = PickleUtils.OPCODE_BINPUT;
                        pdata[1] = (byte) idx;
                        len = 2;
                    } else if (Long.compareUnsigned(idx, 0xffffffffL) <= 0) {
                        pdata[0] = PickleUtils.OPCODE_LONG_BINPUT;
                        pdata[1] = (byte) (idx & 0xff);
                        pdata[2] = (byte) ((idx >> 8) & 0xff);
                        pdata[3] = (byte) ((idx >> 16) & 0xff);
                        pdata[4] = (byte) ((idx >> 24) & 0xff);
                        len = 5;
                    } else {
                        // unlikely
                        throw raise(PicklingError, ErrorMessages.MEMO_ID_TOO_LARGE_FOR_S, "LONG_BINPUT");
                    }
                }
                write(pickler, pdata, len);
            }
        }

        // save methods
        private void handleReduce(VirtualFrame frame, PythonContext ctx, PPickler pickler, Object obj, Object reduceValue) {
            if (PGuards.isString(reduceValue)) {
                saveGlobal(frame, ctx, pickler, obj, reduceValue);
                return;
            }

            if (!(reduceValue instanceof PTuple)) {
                throw raise(PicklingError, ErrorMessages.S_MUST_RETURN_S_OR_S, T___REDUCE__, "string", "tuple");
            }

            saveReduce(frame, ctx, pickler, reduceValue, obj);
        }

        private void saveReduce(VirtualFrame frame, PythonContext ctx, PPickler pickler, Object arguments, Object obj) {
            // We're saving obj, and args is the 2-thru-5 tuple returned by the appropriate
            // __reduce__ method for obj.

            boolean useNewobj = false, useNewobjEx = false;

            if (!(arguments instanceof PTuple)) {
                throw getRaiseNode().raiseBadInternalCall();
            }
            int size = length(frame, arguments);
            if (size < 2 || size > 6) {
                throw raise(PicklingError, ErrorMessages.TUPLE_RET_BY_REDUCE_2_6);
            }

            Object callable = getItem(frame, arguments, 0);
            Object argtup = getItem(frame, arguments, 1);
            Object state = getItem(frame, arguments, size, 2, null);
            Object listitems = getItem(frame, arguments, size, 3, PNone.NONE);
            Object dictitems = getItem(frame, arguments, size, 4, PNone.NONE);
            Object stateSetter = getItem(frame, arguments, size, 5, PNone.NONE);

            if (!isCallable(callable)) {
                throw raise(PicklingError, ErrorMessages.S_ITEM_REDUCE_MUST_BE_S, "first", "callable");
            }

            if (!(argtup instanceof PTuple)) {
                throw raise(PicklingError, ErrorMessages.S_ITEM_REDUCE_MUST_BE_S, "second", "a tuple");
            }

            if (state == PNone.NONE) {
                state = null;
            }

            if (listitems == PNone.NONE) {
                listitems = null;
            } else if (!isIterator(listitems)) {
                throw raise(PicklingError, ErrorMessages.S_ELEM_REDUCE_MUST_BE_S_NOT_P, "fourth", "an iterator", listitems);
            }

            if (dictitems == PNone.NONE) {
                dictitems = null;
            } else if (!isIterator(dictitems)) {
                throw raise(PicklingError, ErrorMessages.S_ELEM_REDUCE_MUST_BE_S_NOT_P, "fifth", "an iterator", dictitems);
            }

            if (stateSetter == PNone.NONE) {
                stateSetter = null;
            } else if (!isCallable(stateSetter)) {
                throw raise(PicklingError, ErrorMessages.S_ELEM_REDUCE_MUST_BE_S_NOT_P, "sixth", "a function", stateSetter);
            }

            if (pickler.proto >= 2) {
                Object name = lookupAttribute(frame, callable, T___NAME__);
                if (name != PNone.NO_VALUE) {
                    final TruffleString stringName = asString(name);
                    if (stringName != null) {
                        useNewobjEx = ensureTsEqualNode().execute(stringName, T___NEWOBJ_EX__, TS_ENCODING);
                        if (!useNewobjEx) {
                            useNewobj = ensureTsEqualNode().execute(stringName, T___NEWOBJ__, TS_ENCODING);
                        }
                    }
                }
            }

            final int argtupSize = length(frame, argtup);

            if (useNewobjEx) {
                Object cls;
                Object args;
                Object kwargs;

                if (argtupSize != 3) {
                    throw raise(PicklingError, ErrorMessages.LEN_OF_S_MUST_BE_D_NOT_D, "NEWOBJ_EX", 3, argtupSize);
                }

                cls = getItem(frame, argtup, 0);
                if (!isType(cls)) {
                    throw raise(PicklingError, ErrorMessages.S_ITEM_FROM_S_MUST_BE_S_NOT_P, "first", "NEWOBJ_EX", "a class", cls);
                }

                args = getItem(frame, argtup, 1);
                if (!(args instanceof PTuple)) {
                    throw raise(PicklingError, ErrorMessages.S_ITEM_FROM_S_MUST_BE_S_NOT_P, "second", "NEWOBJ_EX", "a tuple", args);
                }

                kwargs = getItem(frame, argtup, 2);
                if (!(kwargs instanceof PDict)) {
                    throw raise(PicklingError, ErrorMessages.S_ITEM_FROM_S_MUST_BE_S_NOT_P, "third", "NEWOBJ_EX", "a dict", kwargs);
                }

                if (pickler.proto >= 4) {
                    save(frame, pickler, cls, 0);
                    save(frame, pickler, args, 0);
                    save(frame, pickler, kwargs, 0);
                    write(pickler, PickleUtils.OPCODE_NEWOBJ_EX);
                } else {
                    PickleState st = getGlobalState(ctx.getCore());
                    final int argsSize = length(frame, args);
                    Object[] newargs = new Object[argsSize + 2];
                    Object clsNew;
                    int i;

                    clsNew = lookupAttributeStrict(frame, cls, T___NEW__);
                    newargs[0] = clsNew;
                    newargs[1] = cls;
                    for (i = 0; i < argsSize; i++) {
                        Object item = getItem(frame, args, i);
                        newargs[i + 2] = item;
                    }
                    callable = callStarArgsAndKwArgs(frame, st.partial, newargs, kwargs);

                    save(frame, pickler, callable, 0);
                    save(frame, pickler, factory().createEmptyTuple(), 0);
                    write(pickler, PickleUtils.OPCODE_REDUCE);
                }
            } else if (useNewobj) {
                Object cls;
                Object newargtup;
                Object objClass;
                boolean p;

                // Sanity checks.
                if (argtupSize < 1) {
                    throw raise(PicklingError, ErrorMessages.IS_EMPTY, "__newobj__ arglist");
                }

                cls = getItem(frame, argtup, 0);
                if (!isType(cls)) {
                    throw raise(PicklingError, ErrorMessages.ARGS_0_FROM_S_ARGS_S, "__newobj__", "is not a type");
                }

                if (obj != null) {
                    objClass = getClass(frame, obj);
                    p = objClass != cls;
                    if (p) {
                        throw raise(PicklingError, ErrorMessages.ARGS_0_FROM_S_ARGS_S, "__newobj__", "has the wrong class");
                    }
                }

                // XXX: These calls save() are prone to infinite recursion. Imagine
                // what happen if the value returned by the __reduce__() method of
                // some extension type contains another object of the same type. Ouch!
                //
                // Here is a quick example, that I ran into, to illustrate what I
                // mean:
                //
                // >>> import pickle, copyreg
                // >>> copyreg.dispatch_table.pop(complex)
                // >>> pickle.dumps(1+2j)
                // Traceback (most recent call last):
                // ...
                // RecursionError: maximum recursion depth exceeded
                //
                // Removing the complex class from copyreg.dispatch_table made the
                // __reduce_ex__() method emit another complex object:
                //
                // >>> (1+1j).__reduce_ex__(2)
                // (<function __newobj__ at 0xb7b71c3c>,
                // (<class 'complex'>, (1+1j)), None, None, None)
                //
                // Thus when save() was called on newargstup (the 2nd item) recursion
                // ensued. Of course, the bug was in the complex class which had a
                // broken __getnewargs__() that emitted another complex object. But,
                // the point, here, is it is quite easy to end up with a broken reduce
                // function.

                // Save the class and its __new__ arguments
                save(frame, pickler, cls, 0);
                newargtup = getItem(frame, argtup, factory().createIntSlice(1, argtupSize, 1));
                save(frame, pickler, newargtup, 0);
                write(pickler, PickleUtils.OPCODE_NEWOBJ);
            } else {
                // Not using NEWOBJ
                save(frame, pickler, callable, 0);
                save(frame, pickler, argtup, 0);
                write(pickler, PickleUtils.OPCODE_REDUCE);
            }

            // obj can be NULL when save_reduce() is used directly. A NULL obj means the caller do
            // not want to memoize the object. Not particularly useful, but that is to mimic the
            // behavior save_reduce() in pickle.py when obj is None.
            if (obj != null) {
                // If the object is already in the memo, this means it is recursive. In this case,
                // throw away everything we put on the stack, and fetch the object back from the
                // memo
                int memoIndex = pickler.memo.get(obj);
                if (memoIndex != -1) {
                    write(pickler, PickleUtils.OPCODE_POP);
                    memoGet(pickler, memoIndex);
                } else {
                    memoPut(pickler, obj);
                }
            }

            if (listitems != null) {
                batchList(frame, pickler, listitems);
            }

            if (dictitems != null) {
                batchDict(frame, pickler, dictitems);
            }

            if (state != null) {
                if (stateSetter == null) {
                    save(frame, pickler, state, 0);
                    write(pickler, PickleUtils.OPCODE_BUILD);
                } else {
                    // If a state_setter is specified, call it instead of load_build to update obj's
                    // with its previous state. The first 4 save/write instructions push
                    // state_setter and its tuple of expected arguments (obj, state) onto the stack.
                    // The REDUCE opcode triggers the state_setter(obj, state) function call.
                    // Finally, because state-updating routines only do in-place modification, the
                    // whole operation has to be stack-transparent. Thus, we finally pop the call's
                    // output from the stack.
                    save(frame, pickler, stateSetter, 0);
                    save(frame, pickler, obj, 0);
                    save(frame, pickler, state, 0);
                    write(pickler, PickleUtils.OPCODE_TUPLE2);
                    write(pickler, PickleUtils.OPCODE_REDUCE);
                    write(pickler, PickleUtils.OPCODE_POP);
                }
            }
        }

        private void saveNone(PPickler pickler, @SuppressWarnings("unused") Object obj) {
            write(pickler, PickleUtils.OPCODE_NONE);
        }

        private void saveBool(VirtualFrame frame, PPickler pickler, Object obj) {
            if (pickler.proto >= 2) {
                write(pickler, isTrue(frame, obj) ? PickleUtils.OPCODE_NEWTRUE : PickleUtils.OPCODE_NEWFALSE);
            } else {
                // These aren't opcodes -- they're ways to pickle bools before protocol 2 so that
                // unpicklers written before bools were introduced unpickle them as ints, but
                // unpicklers after can recognize that bools were intended. Note that protocol 2
                // added direct ways to pickle bools.
                writeASCII(pickler, isTrue(frame, obj) ? PickleUtils.T_PROTO_LE2_TRUE : PickleUtils.T_PROTO_LE2_FALSE);
            }
        }

        private void saveLong(VirtualFrame frame, PPickler pickler, Object obj) {
            TruffleString repr;
            try {
                long value = asLong(frame, obj);
                if (value <= 0x7fffffffL && value >= (-0x7fffffffL - 1)) {
                    // result fits in a signed 4-byte integer. Note: we can't use -0x80000000L in
                    // the above condition because some compilers (e.g., MSVC) will promote
                    // 0x80000000L to an unsigned type before applying the unary minus when
                    // sizeof(long) <= 4. The resulting value stays unsigned which is commonly not
                    // what we want, so MSVC happily warns us about it. However, that result would
                    // have been fine because we guard for sizeof(long) <= 4 which turns the
                    // condition true in that particular case.
                    byte[] pdata;
                    int len;

                    if (pickler.isBin()) {
                        pdata = new byte[5];
                        pdata[1] = (byte) (value & 0xff);
                        pdata[2] = (byte) ((value >> 8) & 0xff);
                        pdata[3] = (byte) ((value >> 16) & 0xff);
                        pdata[4] = (byte) ((value >> 24) & 0xff);

                        if ((pdata[4] != 0) || (pdata[3] != 0)) {
                            pdata[0] = PickleUtils.OPCODE_BININT;
                            len = 5;
                        } else if (pdata[2] != 0) {
                            pdata[0] = PickleUtils.OPCODE_BININT2;
                            len = 3;
                        } else {
                            pdata[0] = PickleUtils.OPCODE_BININT1;
                            len = 2;
                        }
                    } else {
                        pdata = new byte[32];
                        pdata[0] = PickleUtils.OPCODE_INT;
                        len = PickleUtils.toAsciiBytesWithNewLine(pdata, 1, value, ensureTsFromLongNode(), ensureTsCopyToByteArrayNode());
                    }
                    write(pickler, pdata, len);
                    return;
                }
            } catch (PException e) {
                e.expectCached(OverflowError, ensureErrProfile());
            }

            if (pickler.proto >= 2) {
                byte[] header = new byte[5];
                // Linear-time pickling.
                final int sign = getSign(obj);
                if (sign == 0) {
                    header[0] = PickleUtils.OPCODE_LONG1;
                    header[1] = 0;
                    write(pickler, header, 2);
                }

                final int nbits = getNumBits(obj);
                // How many bytes do we need? There are nbits >> 3 full bytes of data, and nbits & 7
                // leftover bits. If there are any leftover bits, then we clearly need another byte.
                // What's not so obvious is that we *probably* need another byte even if there
                // aren't any leftovers: the most-significant bit of the most-significant byte acts
                // like a sign bit, and it's usually got a sense opposite of the one we need. The
                // exception is ints of the form -(2**(8*j-1)) for j > 0. Such an int is its own
                // 256's-complement, so has the right sign bit even without the extra byte. That's a
                // pain to check for in advance, though, so we always grab an extra byte at the
                // start, and cut it back later if possible.
                int nbytes = (nbits >> 3) + 1;
                if (Long.compareUnsigned(nbytes, 0x7fffffffL) > 0) {
                    throw raise(OverflowError, ErrorMessages.S_TO_LARGE_TO_PICKLE, "int");
                }
                byte[] pdata = longAsBytes(obj, nbytes, false);
                // If the int is negative, this may be a byte more than needed. This is so iff the
                // MSB is all redundant sign bits.
                if (sign < 0 && nbytes > 1 && pdata[nbytes - 1] == (byte) 0xff && (pdata[nbytes - 2] & 0x80) != 0) {
                    nbytes--;
                }
                int size;
                if (nbytes < 256) {
                    header[0] = PickleUtils.OPCODE_LONG1;
                    header[1] = (byte) nbytes;
                    size = 2;
                } else {
                    header[0] = PickleUtils.OPCODE_LONG4;
                    size = nbytes;
                    for (int i = 1; i < 5; i++) {
                        header[i] = (byte) (size & 0xff);
                        size >>= 8;
                    }
                    size = 5;
                }

                write(pickler, header, size);
                write(pickler, pdata, nbytes);

            } else {
                repr = repr(frame, obj);
                write(pickler, PickleUtils.OPCODE_LONG);
                writeASCII(pickler, asStringStrict(repr));
                writeASCII(pickler, T_L_NEW_LINE);
            }
        }

        private void saveFloat(VirtualFrame frame, PPickler pickler, Object obj, Node inliningTarget, PyFloatAsDoubleNode asDoubleNode) {
            final double value = asDoubleNode.execute(frame, inliningTarget, obj);
            if (pickler.isBin()) {
                byte[] pdata = new byte[9];
                pdata[0] = PickleUtils.OPCODE_BINFLOAT;
                NumericSupport.bigEndian().putDouble(pdata, 1, value);
                write(pickler, pdata, 9);
            } else {
                write(pickler, PickleUtils.OPCODE_FLOAT);
                TruffleString repr = PickleUtils.doubleToAsciiString(value);
                writeASCII(pickler, repr);
                writeASCII(pickler, T_NEWLINE);
            }
        }

        private void saveBytes(VirtualFrame frame, PythonContext ctx, PPickler pickler, Object obj, IndirectCallData indirectCallData) {
            Object buffer = getBufferAcquireLibrary().acquireReadonly(obj, frame, indirectCallData);
            try {
                if (pickler.proto < 3) {
                    // Older pickle protocols do not have an opcode for pickling bytes objects.
                    // Therefore, we need to fake the copy protocol (i.e., the __reduce__ method) to
                    // permit bytes object unpickling. Here we use a hack to be compatible with
                    // Python
                    // 2. Since in Python 2 'bytes' is just an alias for 'str' (which has different
                    // parameters than the actual bytes object), we use codecs.encode to create the
                    // appropriate 'str' object when unpickled using Python 2 *and* the appropriate
                    // 'bytes' object when unpickled using Python 3. Again this is a hack and we
                    // don't
                    // need to do this with newer protocols.
                    Object reduceValue;

                    if (getBufferLibrary().getBufferLength(buffer) == 0) {
                        reduceValue = createTuple(ctx.getCore().lookupType(PythonBuiltinClassType.PBytes), createTuple());
                    } else {
                        PickleState st = getGlobalState(ctx.getCore());
                        final TruffleString unicodeStr = PickleUtils.decodeLatin1Strict(getBufferLibrary().getCopiedByteArray(buffer), ensureTsFromByteArray(), ensureTsSwitchEncodingNode());
                        reduceValue = createTuple(st.codecsEncode, createTuple(unicodeStr, LATIN1));
                    }
                    // save_reduce() will memoize the object automatically.
                    saveReduce(frame, ctx, pickler, reduceValue, obj);
                } else {
                    byte[] bytes = getBufferLibrary().getCopiedByteArray(buffer);
                    saveBytesData(frame, pickler, obj, bytes, bytes.length);
                }
            } finally {
                getBufferLibrary().release(buffer, frame, indirectCallData);
            }
        }

        private void saveBytesData(VirtualFrame frame, PPickler pickler, Object obj, byte[] data, int size) {
            assert pickler.proto >= 3;
            byte[] header = new byte[9];
            int len;

            if (size <= 0xff) {
                header[0] = PickleUtils.OPCODE_SHORT_BINBYTES;
                header[1] = (byte) size;
                len = 2;
            } else if (Long.compareUnsigned(size, 0xffffffffL) < 0) {
                header[0] = PickleUtils.OPCODE_BINBYTES;
                header[1] = (byte) (size & 0xff);
                header[2] = (byte) ((size >> 8) & 0xff);
                header[3] = (byte) ((size >> 16) & 0xff);
                header[4] = (byte) ((size >> 24) & 0xff);
                len = 5;
            } else if (pickler.proto >= 4) {
                header[0] = PickleUtils.OPCODE_BINBYTES8;
                PickleUtils.writeSize64(header, 1, size);
                len = 9;
            } else {
                throw raise(OverflowError, ErrorMessages.SER_OVER_4GB);
            }

            writeBytes(frame, pickler, header, len, data, size, obj);
            memoPut(pickler, obj);
        }

        private void writeUnicodeBinary(VirtualFrame frame, PPickler pickler, Object obj) {
            Object encoded = null;
            byte[] header = new byte[9];
            int len;
            byte[] data = PickleUtils.encodeUTF8Strict(asStringStrict(obj), ensureTsSwitchEncodingNode(), ensureTsCopyToByteArrayNode(), ensureTsGetCodeRangeNode());

            if (data == null) {
                // Issue #8383: for strings with lone surrogates, fallback on the "surrogatepass"
                // error handler.
                encoded = getItem(frame, encode(obj, T_UTF8, T_ERRORS_SURROGATEPASS), 0);
                // Checkstyle: stop
                //@formatter:off
                // data = PickleUtils.encodeUTF8Strict(asStringStrict(encoded), ensureTsSwitchEncodingNode(), ensureTsCopyToByteArrayNode(), ensureTsGetCodeRangeNode());
                // Checkstyle: start
                //@formatter:on
                // TODO: [GR-39571] TruffleStrings: allow preservation of UTF-16 surrogate
                data = toBytes(frame, encoded);
            }

            int size = data.length;

            if (size <= 0xff && pickler.proto >= 4) {
                header[0] = PickleUtils.OPCODE_SHORT_BINUNICODE;
                header[1] = (byte) (size & 0xff);
                len = 2;
            } else if (Long.compareUnsigned(size, 0xffffffffL) <= 0) {
                header[0] = PickleUtils.OPCODE_BINUNICODE;
                header[1] = (byte) (size & 0xff);
                header[2] = (byte) ((size >> 8) & 0xff);
                header[3] = (byte) ((size >> 16) & 0xff);
                header[4] = (byte) ((size >> 24) & 0xff);
                len = 5;
            } else if (pickler.proto >= 4) {
                header[0] = PickleUtils.OPCODE_BINUNICODE8;
                PickleUtils.writeSize64(header, 1, size);
                len = 9;
            } else {
                throw raise(OverflowError, ErrorMessages.SER_OVER_4GB);
            }

            writeBytes(frame, pickler, header, len, data, size, encoded);
        }

        private void saveUnicode(VirtualFrame frame, PPickler pickler, Object obj) {
            if (pickler.isBin()) {
                writeUnicodeBinary(frame, pickler, obj);
            } else {
                byte[] encoded = PickleUtils.rawUnicodeEscape(asStringStrict(obj), ensureTsCodePointLengthNode(), ensureTsCodePointAtIndexNode());
                write(pickler, PickleUtils.OPCODE_UNICODE);
                write(pickler, encoded);
                writeASCII(pickler, T_NEWLINE);
            }
            memoPut(pickler, obj);
        }

        private void batchDictExact(VirtualFrame frame, PPickler pickler, PDict dict) {
            final HashingStorage storage = getHashingStorage(frame, dict);
            final int length = getHashingStorageLength(storage);
            // Special-case len(d) == 1 to save space.
            if (length == 1) {
                HashingStorageIterator it = getHashingStorageIterator(storage);
                if (!ensureHashingStorageIteratorNext().executeCached(storage, it)) {
                    throw raise(PythonBuiltinClassType.RuntimeError, ErrorMessages.CHANGED_SIZE_DURING_ITERATION, T_DICTIONARY);
                }
                HashingStorageIteratorKey getKeyNode = ensureHashingStorageIteratorKey();
                HashingStorageIteratorValue getValueNode = ensureHashingStorageIteratorValue();
                save(frame, pickler, getKeyNode.executeCached(storage, it), 0);
                save(frame, pickler, getValueNode.executeCached(storage, it), 0);
                write(pickler, PickleUtils.OPCODE_SETITEM);
            } else {
                // Write in batches of BATCHSIZE.
                saveDictHashingStorageBatched(frame, pickler, storage);
            }
        }

        private void batchDict(VirtualFrame frame, PPickler pickler, Object iterator) {
            assert iterator != null;
            if (pickler.proto == 0) {
                // SETITEMS isn't available; do one at a time.
                saveDictIterator(frame, pickler, iterator);
            } else {
                // proto > 0: write in batches of BATCHSIZE.
                saveDictIteratorBatchUnrolled(frame, pickler, iterator);
            }
        }

        private void saveDict(VirtualFrame frame, Node inliningTarget, PyObjectCallMethodObjArgs callMethod, PPickler pickler, Object obj) {
            byte[] header = new byte[3];
            int len;

            if (pickler.isFast()) {
                fastSaveEnter(pickler, obj);
            }

            // Create an empty dict.
            if (pickler.isBin()) {
                header[0] = PickleUtils.OPCODE_EMPTY_DICT;
                len = 1;
            } else {
                header[0] = PickleUtils.OPCODE_MARK;
                header[1] = PickleUtils.OPCODE_DICT;
                len = 2;
            }

            write(pickler, header, len);
            memoPut(pickler, obj);

            if (length(frame, obj) > 0) {
                if (PGuards.isDict(obj) && pickler.proto > 0) {
                    batchDictExact(frame, pickler, (PDict) obj);
                } else {
                    final Object items = callMethod.execute(frame, inliningTarget, obj, T_ITEMS);
                    batchDict(frame, pickler, getIter(frame, items));
                }
            }

            if (pickler.isFast()) {
                fastSaveLeave(pickler, obj);
            }
        }

        private void batchSetExact(VirtualFrame frame, PPickler pickler, Object obj) {
            final HashingStorage storage = getHashingStorage(frame, obj);
            saveSetHashingStorageBatched(frame, pickler, storage);
        }

        private void batchSet(VirtualFrame frame, PPickler pickler, Object obj) {
            Object iterator = getIter(frame, obj);
            int setSize = length(frame, obj);
            int i;
            do {
                i = 0;
                write(pickler, PickleUtils.OPCODE_MARK);
                while (true) {
                    Object item = getNextItem(frame, iterator);
                    if (item == null) {
                        break;
                    }
                    save(frame, pickler, item, 0);
                    if (++i == PickleUtils.BATCHSIZE) {
                        break;
                    }
                }
                write(pickler, PickleUtils.OPCODE_ADDITEMS);
                if (length(frame, obj) != setSize) {
                    throw raise(PythonBuiltinClassType.RuntimeError, ErrorMessages.CHANGED_SIZE_DURING_ITERATION, "set");
                }
            } while (i == PickleUtils.BATCHSIZE);
        }

        private void saveSet(VirtualFrame frame, PythonContext ctx, PPickler pickler, Object obj) {
            Object reduceValue;
            if (pickler.proto < 4) {
                Object items = createList(frame, obj);
                reduceValue = createTuple(ctx.getCore().lookupType(PythonBuiltinClassType.PSet), createTuple(items));
                saveReduce(frame, ctx, pickler, reduceValue, obj);
                return;
            }

            write(pickler, PickleUtils.OPCODE_EMPTY_SET);
            memoPut(pickler, obj);

            final int setSize = length(frame, obj);
            if (setSize == 0) {
                // nothing to do
                return;
            }

            // Write in batches of BATCHSIZE.
            if (PGuards.isPSet(obj)) {
                batchSetExact(frame, pickler, obj);
            } else {
                batchSet(frame, pickler, obj);
            }
        }

        private void saveFrozenset(VirtualFrame frame, PythonContext ctx, PPickler pickler, Object obj) {
            if (pickler.isFast()) {
                fastSaveEnter(pickler, obj);
            }

            if (pickler.proto < 4) {
                Object items = createList(frame, obj);
                Object reduceValue = createTuple(ctx.getCore().lookupType(PythonBuiltinClassType.PFrozenSet), createTuple(items));
                saveReduce(frame, ctx, pickler, reduceValue, obj);
                return;
            }

            write(pickler, PickleUtils.OPCODE_MARK);

            if (PGuards.isPFrozenSet(obj)) {
                final HashingStorage storage = getHashingStorage(frame, obj);
                saveSetHashingStorage(frame, pickler, storage);
            } else {
                final Object iterator = getIter(frame, obj);
                saveFrozenSetIterator(frame, pickler, iterator);
            }

            // If the object is already in the memo, this means it is recursive. In this case, throw
            // away everything we put on the stack, and fetch the object back from the memo.
            int memoIndex = pickler.memo.get(obj);
            if (memoIndex != -1) {
                write(pickler, PickleUtils.OPCODE_POP_MARK);
                memoGet(pickler, memoIndex);
                return;
            }

            write(pickler, PickleUtils.OPCODE_FROZENSET);
            memoPut(pickler, obj);
        }

        private void batchListExact(VirtualFrame frame, PPickler pickler, Object obj) {
            final SequenceStorage storage = getSequenceStorage(obj);
            Object item;
            if (length(frame, obj) == 1) {
                item = getItem(frame, storage, 0);
                save(frame, pickler, item, 0);
                write(pickler, PickleUtils.OPCODE_APPEND);
                return;
            }

            // Write in batches of BATCHSIZE.
            int total = 0;
            int thisBatch;
            do {
                thisBatch = 0;
                write(pickler, PickleUtils.OPCODE_MARK);
                while (total < length(frame, obj)) {
                    item = getItem(frame, storage, total);
                    save(frame, pickler, item, 0);
                    total++;
                    if (++thisBatch == PickleUtils.BATCHSIZE) {
                        break;
                    }
                }
                write(pickler, PickleUtils.OPCODE_APPENDS);
            } while (total < length(frame, obj));
        }

        private void batchList(VirtualFrame frame, PPickler pickler, Object iterator) {
            assert iterator != null;
            if (pickler.proto == 0) {
                // APPENDS isn't available; do one at a time.
                saveListIterator(frame, pickler, iterator);
            } else {
                // proto > 0: write in batches of BATCHSIZE.
                saveListIteratorBatchUnrolled(frame, pickler, iterator);
            }
        }

        private void saveList(VirtualFrame frame, PPickler pickler, Object obj) {
            byte[] header = new byte[3];
            int len;

            if (pickler.isFast()) {
                fastSaveEnter(pickler, obj);
            }

            // Create an empty list.
            if (pickler.isBin()) {
                header[0] = PickleUtils.OPCODE_EMPTY_LIST;
                len = 1;
            } else {
                header[0] = PickleUtils.OPCODE_MARK;
                header[1] = PickleUtils.OPCODE_LIST;
                len = 2;
            }

            write(pickler, header, len);
            len = length(frame, obj);
            memoPut(pickler, obj);

            if (len != 0) {
                // Materialize the list elements.
                if (PGuards.isList(obj) && pickler.proto > 0) {
                    batchListExact(frame, pickler, obj);
                } else {
                    batchList(frame, pickler, getIter(frame, obj));
                }
            }

            if (pickler.isFast()) {
                fastSaveLeave(pickler, obj);
            }
        }

        private void storeTupleElements(VirtualFrame frame, PPickler pickler, Object obj, int len) {
            // A helper for save_tuple. Push the len elements in tuple t on the stack
            assert PyObjectSizeNode.executeUncached(frame, obj) == len;
            for (int i = 0; i < len; i++) {
                Object element = getItem(frame, obj, i);
                save(frame, pickler, element, 0);
            }
        }

        private void saveTuple(VirtualFrame frame, PPickler pickler, Object obj) {
            int len = length(frame, obj);

            if (len == 0) {
                byte[] pdata = new byte[2];

                if (pickler.proto != 0) {
                    pdata[0] = PickleUtils.OPCODE_EMPTY_TUPLE;
                    len = 1;
                } else {
                    pdata[0] = PickleUtils.OPCODE_MARK;
                    pdata[1] = PickleUtils.OPCODE_TUPLE;
                    len = 2;
                }

                write(pickler, pdata, len);
                return;
            }

            // The tuple isn't in the memo now. If it shows up there after saving the tuple
            // elements, the tuple must be recursive, in which case we'll pop everything we put on
            // the stack, and fetch its value from the memo.
            if (len <= 3 && pickler.proto >= 2) {
                // Use TUPLE{1,2,3} opcodes.
                storeTupleElements(frame, pickler, obj, len);

                int memoIndex = pickler.memo.get(obj);
                if (memoIndex != -1) {
                    // pop the len elements
                    for (int i = 0; i < len; i++) {
                        write(pickler, PickleUtils.OPCODE_POP);
                    }
                    // fetch from memo
                    memoGet(pickler, memoIndex);
                    return;
                } else {
                    // Not recursive.
                    write(pickler, TUPLE_LEN_2_OPCODE[len]);
                }

                memoPut(pickler, obj);
                return;
            }

            // proto < 2 and len > 0, or proto >= 2 and len > 3. Generate MARK e1 e2 ... TUPLE
            write(pickler, PickleUtils.OPCODE_MARK);
            storeTupleElements(frame, pickler, obj, len);

            int memoIndex = pickler.memo.get(obj);
            if (memoIndex != -1) {
                // pop the stack stuff we pushed
                if (pickler.isBin()) {
                    write(pickler, PickleUtils.OPCODE_POP_MARK);
                } else {
                    // Note that we pop one more than len, to remove the MARK too.
                    for (int i = 0; i <= len; i++) {
                        write(pickler, PickleUtils.OPCODE_POP);
                    }
                }
                // fetch from memo
                memoGet(pickler, memoIndex);
                return;
            } else {
                // Not recursive.
                write(pickler, PickleUtils.OPCODE_TUPLE);
            }

            memoPut(pickler, obj);
        }

        private void saveBytearrayData(VirtualFrame frame, PPickler pickler, Object obj, byte[] data, int size) {
            assert pickler.proto >= 5;
            if (size < 0) {
                return;
            }

            byte[] header = new byte[9];

            header[0] = PickleUtils.OPCODE_BYTEARRAY8;
            PickleUtils.writeSize64(header, 1, size);

            int len = 9;
            writeBytes(frame, pickler, header, len, data, size, obj);
            memoPut(pickler, obj);
        }

        private void saveBytearray(VirtualFrame frame, PythonContext ctx, PPickler pickler, Object obj, IndirectCallData indirectCallData) {
            Object buffer = getBufferAcquireLibrary().acquireReadonly(obj, frame, indirectCallData);
            try {
                if (pickler.proto < 5) {
                    // Older pickle protocols do not have an opcode for pickling bytearrays.
                    Object reduceValue;

                    final PythonBuiltinClass byteArrayClass = ctx.getCore().lookupType(PythonBuiltinClassType.PByteArray);
                    if (getBufferLibrary().getBufferLength(buffer) == 0) {
                        reduceValue = createTuple(byteArrayClass, createTuple());
                    } else {
                        byte[] bytes = getBufferLibrary().getCopiedByteArray(buffer);
                        reduceValue = createTuple(byteArrayClass, createTuple(factory().createBytes(bytes)));
                    }

                    // save_reduce() will memoize the object automatically.
                    saveReduce(frame, ctx, pickler, reduceValue, obj);
                } else {
                    saveBytearrayData(frame, pickler, obj, getBufferLibrary().getCopiedByteArray(buffer), length(frame, obj));
                }
            } finally {
                getBufferLibrary().release(buffer, frame, indirectCallData);
            }
        }

        private void savePicklebuffer(VirtualFrame frame, PPickler pickler, PPickleBuffer obj) {
            if (pickler.proto < 5) {
                throw raise(PicklingError, ErrorMessages.PICKLEBUFF_CANNOT_PICKLE_WITH_PROTO5);
            }

            Object buffer = obj.getView();
            PythonBufferAccessLibrary bufferLib = getBufferLibrary();
            int bytesLen = bufferLib.getBufferLength(buffer);
            byte[] bytes = bufferLib.getInternalOrCopiedByteArray(buffer);
            boolean inBand = true;

            if (pickler.bufferCallback != null) {
                Object ret = getCallNode().execute(frame, pickler.bufferCallback, obj);
                inBand = isTrue(frame, ret);
            }

            boolean readOnly = bufferLib.isReadonly(bufferLib);
            if (inBand) {
                // Write data in-band
                if (readOnly) {
                    saveBytesData(frame, pickler, obj, bytes, bytesLen);
                } else {
                    saveBytearrayData(frame, pickler, obj, bytes, bytesLen);
                }
            } else {
                // Write data out-of-band
                write(pickler, PickleUtils.OPCODE_NEXT_BUFFER);
                if (readOnly) {
                    write(pickler, PickleUtils.OPCODE_READONLY_BUFFER);
                }
            }
        }

        private void saveSingletonType(VirtualFrame frame, PythonContext ctx, PPickler pickler, Object obj, Object singleton) {
            Object reduceValue = createTuple(ctx.getCore().lookupType(PythonBuiltinClassType.PythonClass), createTuple(singleton));
            saveReduce(frame, ctx, pickler, reduceValue, obj);
        }

        private void saveType(VirtualFrame frame, PythonContext ctx, PPickler pickler, Object obj) {
            if (isBuiltinClass(obj, PythonBuiltinClassType.PNone)) {
                saveSingletonType(frame, ctx, pickler, obj, PNone.NONE);
            } else if (isBuiltinClass(obj, PythonBuiltinClassType.PEllipsis)) {
                saveSingletonType(frame, ctx, pickler, obj, PEllipsis.INSTANCE);
            } else if (isBuiltinClass(obj, PythonBuiltinClassType.PNotImplemented)) {
                saveSingletonType(frame, ctx, pickler, obj, PNotImplemented.NOT_IMPLEMENTED);
            } else {
                Object type = (obj instanceof PythonBuiltinClassType) ? ctx.getCore().lookupType((PythonBuiltinClassType) obj) : obj;
                saveGlobal(frame, ctx, pickler, type, null);
            }
        }

        private void saveGlobal(VirtualFrame frame, PythonContext ctx, PPickler pickler, Object obj, Object name) {
            PickleState st = getGlobalState(ctx.getCore());
            Object gName;
            if (name != null) {
                gName = name;
            } else {
                gName = lookupAttribute(frame, obj, T___QUALNAME__);
                if (gName == PNone.NO_VALUE) {
                    gName = lookupAttributeStrict(frame, obj, T___NAME__);
                }
            }
            TruffleString globalName = asStringStrict(gName);

            TruffleString[] dottedPath = getDottedPath(obj, globalName);
            TruffleString moduleName = whichModule(frame, ctx, obj, dottedPath);

            // XXX: Change to use the import C API directly with level=0 to disallow relative
            // imports.
            // XXX: PyImport_ImportModuleLevel could be used. However, this bypasses
            // builtins.__import__. Therefore, _pickle, unlike pickle.py, will ignore custom import
            // functions (IMHO, this would be a nice security feature). The import C API would need
            // to be extended to support the extra parameters of __import__ to fix that.
            Object module = PickleUtils.importDottedModule(moduleName);
            final Pair<Object, Object> pair = getDeepAttribute(frame, getLookupAttrNode(), module, dottedPath);
            if (pair == null) {
                throw raise(PicklingError, ErrorMessages.CANT_PICKLE_P_ATTR_LOOKUP_FAIL_S_S, obj, globalName, moduleName);
            }
            Object cls = pair.getLeft();
            Object parent = pair.getRight();
            if (cls != obj) {
                throw raise(PicklingError, ErrorMessages.CANT_PICKLE_P_NOT_SAME_OBJ_AS_S_S, obj, moduleName, globalName);
            }

            boolean genGlobal;
            if (pickler.proto >= 2) {
                genGlobal = false;
                // See whether this is in the extension registry, and if so generate an EXT opcode.
                PTuple extensionKey;
                Object codeObj;
                long code;
                byte[] pdata = new byte[5];
                int n;

                extensionKey = createTuple(moduleName, globalName);
                codeObj = getDictItem(frame, st.extensionRegistry, extensionKey);
                // The object is not registered in the extension registry. This is the most likely
                // code path.
                if (codeObj == null) {
                    genGlobal = true;
                } else {
                    // XXX: pickle.py doesn't check neither the type, nor the range of the value
                    // returned by the extension_registry. It should for consistency.
                    // Verify code_obj has the right type and value.
                    if (!PGuards.canBeInteger(codeObj)) {
                        throw raise(PicklingError, ErrorMessages.CANT_PICKLE_P_EXT_CODE_P_NOT_AN_INT, obj, codeObj);
                    }

                    code = asLong(frame, codeObj);
                    if (code <= 0 || code > 0x7fffffffL) {
                        throw raise(PicklingError, ErrorMessages.CANT_PICKLE_P_EXT_CODE_OO_RANGE, obj, code);
                    }

                    // Generate an EXT opcode
                    if (code <= 0xff) {
                        pdata[0] = PickleUtils.OPCODE_EXT1;
                        pdata[1] = (byte) code;
                        n = 2;
                    } else if (code <= 0xffff) {
                        pdata[0] = PickleUtils.OPCODE_EXT2;
                        pdata[1] = (byte) (code & 0xff);
                        pdata[2] = (byte) ((code >> 8) & 0xff);
                        n = 3;
                    } else {
                        pdata[0] = PickleUtils.OPCODE_EXT4;
                        pdata[1] = (byte) (code & 0xff);
                        pdata[2] = (byte) ((code >> 8) & 0xff);
                        pdata[3] = (byte) ((code >> 16) & 0xff);
                        pdata[4] = (byte) ((code >> 24) & 0xff);
                        n = 5;
                    }

                    write(pickler, pdata, n);
                }
            } else {
                genGlobal = true;
            }

            if (genGlobal) {
                byte[] encoded;
                TruffleString lastname = dottedPath[dottedPath.length - 1];

                if (parent == module) {
                    globalName = lastname;
                }

                if (pickler.proto >= 4) {
                    save(frame, pickler, moduleName, 0);
                    save(frame, pickler, globalName, 0);
                    write(pickler, PickleUtils.OPCODE_STACK_GLOBAL);
                } else if (parent != module) {
                    Object reduceValue = createTuple(st.getattr, createTuple(parent, lastname));
                    saveReduce(frame, ctx, pickler, reduceValue, null);
                } else {
                    // Generate a normal global opcode if we are using a pickle protocol < 4, or if
                    // the object is not registered in the extension registry
                    write(pickler, PickleUtils.OPCODE_GLOBAL);
                    // For protocol < 3 and if the user didn't request against doing so, we convert
                    // module names to the old 2.x module names.
                    if (pickler.proto < 3 && pickler.fixImports) {
                        final Pair<TruffleString, TruffleString> to2Mapping = get3to2Mapping(frame, ctx.getCore(), moduleName, globalName);
                        moduleName = to2Mapping.getLeft();
                        globalName = to2Mapping.getRight();
                    }

                    // Since Python 3.0 now supports non-ASCII identifiers, we encode both the
                    // module name and the global name using UTF-8. We do so only when we are using
                    // the pickle protocol newer than version 3. This is to ensure compatibility
                    // with older Unpickler running on Python 2.x.
                    TruffleString.Encoding encoding = (pickler.proto == 3) ? TruffleString.Encoding.UTF_8 : TruffleString.Encoding.US_ASCII;
                    encoded = PickleUtils.encodeStrict(moduleName, ensureTsSwitchEncodingNode(), encoding, ensureTsCopyToByteArrayNode(), ensureTsGetCodeRangeNode());
                    if (encoded == null) {
                        throw raise(PicklingError, ErrorMessages.CANT_PICKLE_MODULE_S_USING_PROTO_D, moduleName, pickler.proto);
                    }
                    write(pickler, encoded);
                    writeASCII(pickler, T_NEWLINE);
                    // Save the name of the module
                    encoded = PickleUtils.encodeStrict(globalName, ensureTsSwitchEncodingNode(), encoding, ensureTsCopyToByteArrayNode(), ensureTsGetCodeRangeNode());
                    if (encoded == null) {
                        throw raise(PicklingError, ErrorMessages.CANT_PICKLE_MODULE_S_USING_PROTO_D, moduleName, pickler.proto);
                    }
                    write(pickler, encoded);
                    writeASCII(pickler, T_NEWLINE);
                }
                // Memoize the object
                memoPut(pickler, obj);
            }
        }

        @Specialization
        void saveGeneric(VirtualFrame frame, PPickler pickler, Object objArg, int persSave,
                        @Bind("this") Node inliningTarget,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @Cached PyFloatAsDoubleNode asDoubleNode,
                        @Cached CallNode callNode,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            Object obj = objArg;
            opcodeBoundary(frame, pickler);

            // The extra pers_save argument is necessary to avoid calling save_pers()
            // on its returned object.
            if (persSave == 0 && pickler.persFunc != null) {
                if (savePers(frame, pickler, obj)) {
                    return;
                }
            }

            final Object type = getClass(obj);
            // The old cPickle had an optimization that used switch-case statement dispatching on
            // the first letter of the type name. This has was removed since benchmarks shown that
            // this optimization was actually slowing things down.

            // Atom types; these aren't memoized, so don't check the memo.
            if (obj == PNone.NONE) {
                saveNone(pickler, obj);
                return;
            } else if (isBuiltinClass(type, PythonBuiltinClassType.Boolean)) {
                saveBool(frame, pickler, obj);
                return;
            } else if (isBuiltinClass(type, PythonBuiltinClassType.PInt)) {
                saveLong(frame, pickler, obj);
                return;
            } else if (isBuiltinClass(type, PythonBuiltinClassType.PFloat)) {
                saveFloat(frame, pickler, obj, inliningTarget, asDoubleNode);
                return;
            }

            // to avoid misses in memo for the class:
            if (obj instanceof PythonBuiltinClassType classType) {
                obj = PythonContext.get(this).getCore().lookupType(classType);
            }

            // Check the memo to see if it has the object. If so, generate a GET (or BINGET) opcode,
            // instead of pickling the object once again.
            int memoIndex = pickler.memo.get(obj);
            if (memoIndex != -1) {
                memoGet(pickler, memoIndex);
                return;
            }

            PythonContext ctx = PythonContext.get(this);

            if (isBuiltinClass(type, PythonBuiltinClassType.PBytes)) {
                saveBytes(frame, ctx, pickler, obj, indirectCallData);
                return;
            } else if (isBuiltinClass(type, PythonBuiltinClassType.PString)) {
                saveUnicode(frame, pickler, obj);
                return;
            } else if (isBuiltinClass(type, PythonBuiltinClassType.PDict)) {
                saveDict(frame, inliningTarget, callMethod, pickler, obj);
                return;
            } else if (isBuiltinClass(type, PythonBuiltinClassType.PSet)) {
                saveSet(frame, ctx, pickler, obj);
                return;
            } else if (isBuiltinClass(type, PythonBuiltinClassType.PFrozenSet)) {
                saveFrozenset(frame, ctx, pickler, obj);
                return;
            } else if (isBuiltinClass(type, PythonBuiltinClassType.PList)) {
                saveList(frame, pickler, obj);
                return;
            } else if (isBuiltinClass(type, PythonBuiltinClassType.PTuple)) {
                saveTuple(frame, pickler, obj);
                return;
            } else if (isBuiltinClass(type, PythonBuiltinClassType.PByteArray)) {
                saveBytearray(frame, ctx, pickler, obj, indirectCallData);
                return;
            } else if (obj instanceof PPickleBuffer buffer) {
                savePicklebuffer(frame, pickler, buffer);
                return;
            }

            // Now, check reducer_override. If it returns NotImplemented, fallback to save_type or
            // save_global, and then perhaps to the regular reduction mechanism.
            if (pickler.reducerOverride != null) {
                Object reduceValue = callNode.execute(frame, pickler.reducerOverride, obj);
                if (reduceValue != PNotImplemented.NOT_IMPLEMENTED) {
                    handleReduce(frame, ctx, pickler, obj, reduceValue);
                    return;
                }
            }

            if (isBuiltinClass(type, PythonBuiltinClassType.PythonClass)) {
                saveType(frame, ctx, pickler, obj);
                return;
            } else if (isBuiltinClass(type, PythonBuiltinClassType.PFunction)) {
                saveGlobal(frame, ctx, pickler, obj, null);
                return;
            }

            // Get a reduction callable, and call it. This may come from self.dispatch_table,
            // copyreg.dispatch_table, the object's __reduce_ex__ method, or the object's __reduce__
            // method.
            Object reduceFunc = null;
            if (pickler.dispatchTable == null) {
                PickleState state = getGlobalState(ctx.getCore());
                reduceFunc = getDictItem(frame, state.dispatchTable, type);
            } else {
                try {
                    reduceFunc = getItem(frame, pickler.dispatchTable, type);
                } catch (PException ex) {
                    ex.expectCached(KeyError, ensureErrProfile());
                }
            }

            Object reduceValue;
            if (reduceFunc != null) {
                reduceValue = callNode.execute(frame, reduceFunc, obj);
            } else if (isSubType(type, PythonBuiltinClassType.PythonClass)) {
                saveGlobal(frame, ctx, pickler, obj, null);
                return;
            } else {
                // XXX: If the __reduce__ method is defined, __reduce_ex__ is automatically defined
                // as __reduce__. While this is convenient, this make it impossible to know which
                // method was actually called. Of course, this is not a big deal. But still, it
                // would be nice to let the user know which method was called when something go
                // wrong. Incidentally, this means if __reduce_ex__ is not defined, we don't
                // actually have to check for a __reduce__ method.
                // Check for a __reduce_ex__ method.
                reduceFunc = lookupAttribute(frame, obj, T___REDUCE_EX__);
                if (reduceFunc != PNone.NO_VALUE) {
                    reduceValue = callNode.execute(frame, reduceFunc, pickler.proto);
                } else {
                    // Check for a __reduce__ method.
                    reduceFunc = lookupAttribute(frame, obj, T___REDUCE__);
                    if (reduceFunc != PNone.NO_VALUE) {
                        reduceValue = callNode.execute(frame, reduceFunc);
                    } else {
                        throw raise(PicklingError, ErrorMessages.CANNOT_PICKLE_P_P, type, obj);
                    }
                }
            }

            handleReduce(frame, ctx, pickler, obj, reduceValue);
        }
    }

    public abstract static class DumpNode extends BasePickleWriteNode {
        public abstract void execute(VirtualFrame frame, PPickler pickler, Object obj);

        @Specialization
        public void dump(VirtualFrame frame, PPickler pickler, Object obj,
                        @Cached SaveNode saveNode) {
            try {
                final Object tmp = getLookupAttrNode().executeCached(frame, pickler, REDUCE_OVERRIDE);
                if (tmp != PNone.NO_VALUE) {
                    pickler.reducerOverride = tmp;
                } else {
                    pickler.reducerOverride = null;
                }

                if (pickler.proto >= 2) {
                    assert pickler.proto <= 256;
                    byte[] header = new byte[]{PickleUtils.OPCODE_PROTO, (byte) pickler.proto};

                    try {
                        write(pickler, header, 2);
                    } catch (Exception e) {
                        handleError(pickler);
                        throw e;
                    }

                    if (pickler.proto >= 4) {
                        pickler.framing = true;
                    }
                }

                saveNode.execute(frame, pickler, obj, 0);
                write(pickler, PickleUtils.OPCODE_STOP);
                pickler.commitFrame();
            } finally {
                pickler.framing = false;
            }
        }
    }
}
