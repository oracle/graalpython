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

import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_ADDITEMS;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_APPEND;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_APPENDS;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_BINBYTES;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_BINBYTES8;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_BINFLOAT;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_BINGET;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_BININT;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_BININT1;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_BININT2;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_BINPERSID;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_BINPUT;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_BINSTRING;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_BINUNICODE;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_BINUNICODE8;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_BUILD;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_BYTEARRAY8;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_DICT;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_DUP;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_EMPTY_DICT;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_EMPTY_LIST;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_EMPTY_SET;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_EMPTY_TUPLE;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_EXT1;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_EXT2;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_EXT4;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_FLOAT;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_FRAME;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_FROZENSET;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_GET;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_GLOBAL;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_INST;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_INT;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_LIST;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_LONG;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_LONG1;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_LONG4;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_LONG_BINGET;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_LONG_BINPUT;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_MARK;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_MEMOIZE;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_NEWFALSE;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_NEWOBJ;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_NEWOBJ_EX;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_NEWTRUE;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_NEXT_BUFFER;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_NONE;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_OBJ;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_PERSID;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_POP;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_POP_MARK;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_PROTO;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_PUT;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_READONLY_BUFFER;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_REDUCE;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_SETITEM;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_SETITEMS;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_SHORT_BINBYTES;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_SHORT_BINSTRING;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_SHORT_BINUNICODE;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_STACK_GLOBAL;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_STOP;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_STRING;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_TUPLE;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_TUPLE1;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_TUPLE2;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_TUPLE3;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.OPCODE_UNICODE;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.PICKLE_PROTOCOL_HIGHEST;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.PREFETCH;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.READ_WHOLE_LINE;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.T_METHOD_PEEK;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.T_METHOD_PERSISTENT_LOAD;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.T_METHOD_READ;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.T_METHOD_READINTO;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.T_METHOD_READLINE;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.getValidIntString;
import static com.oracle.graal.python.nodes.BuiltinNames.T_ADD;
import static com.oracle.graal.python.nodes.BuiltinNames.T_APPEND;
import static com.oracle.graal.python.nodes.BuiltinNames.T_EXTEND;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DICT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GETINITARGS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___NEW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___SETSTATE__;
import static com.oracle.graal.python.nodes.StringLiterals.T_ASCII_UPPERCASE;
import static com.oracle.graal.python.nodes.StringLiterals.T_STRICT;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import org.graalvm.collections.Pair;

import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.BufferFlags;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.common.EmptyStorage;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageAddAllToOther;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageCopy;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorKey;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorNext;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorValue;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageSetItem;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.ints.IntNodes;
import com.oracle.graal.python.builtins.objects.ints.IntNodesFactory;
import com.oracle.graal.python.builtins.objects.list.ListBuiltins;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.memoryview.MemoryViewBuiltins;
import com.oracle.graal.python.builtins.objects.memoryview.MemoryViewBuiltinsFactory;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.set.PSet;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyMemoryViewFromObject;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectSetItem;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.SetAttributeNode;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.util.NumericSupport;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.strings.TruffleString;

public class PUnpickler extends PythonBuiltinObject {
    // Pickle data stack, store unpickled objects.
    private final PData stack;

    // The unpickler memo is just an array of PyObject *s. Using a dict is unnecessary, since the
    // keys are contiguous ints.
    private Object[] memo;
    // Number of objects in the memo
    private int memoLen;

    // persistent_id() method, can be NULL
    private Object persFunc;
    // borrowed reference to self if pers_func is an unbound method, NULL otherwise
    private Object persFuncSelf;

    private byte[] inputBuffer;
    private byte[] inputLine;
    private int inputLen;
    private int nextReadIdx;
    // index of first prefetched byte
    private int prefetchedIdx;

    // read() method of the input stream.
    private Object read;
    // readinto() method of the input stream.
    private Object readinto;
    // readline() method of the input stream.
    private Object readline;
    // peek() method of the input stream, or NULL
    private Object peek;
    // iterable of out-of-band buffers, or NULL
    private Object buffers;
    // Name of the encoding to be used for decoding strings pickled using Python 2.x. The default
    // value is "ASCII"
    private TruffleString encoding;
    // Name of errors handling scheme to used when decoding strings. The default value is "strict".
    private TruffleString errors;
    // Mark stack, used for unpickling container objects.
    private int[] marks;
    // Number of marks in the mark stack.
    private int numMarks;
    // Current allocated size of the mark stack.
    private int marksSize;
    // Protocol of the pickle loaded.
    private int proto;
    // Indicate whether Unpickler should fix the name of globals pickled by Python 2.x.
    private boolean fixImports;

    public PUnpickler(Object cls, Shape instanceShape) {
        super(cls, instanceShape);
        persFunc = null;
        inputBuffer = null;
        inputLine = null;
        inputLen = 0;
        nextReadIdx = 0;
        prefetchedIdx = 0;
        read = null;
        readinto = null;
        readline = null;
        peek = null;
        buffers = null;
        encoding = null;
        errors = null;
        marks = null;
        numMarks = 0;
        marksSize = 0;
        proto = 0;
        fixImports = false;
        memoLen = 0;
        memo = new Object[32];
        stack = new PData();
    }

    public Object getRead() {
        return read;
    }

    public boolean isFixImports() {
        return fixImports;
    }

    public void setFixImports(boolean fixImports) {
        this.fixImports = fixImports;
    }

    public int getProto() {
        return proto;
    }

    public void setProto(int proto) {
        this.proto = proto;
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

    public void initInternals(VirtualFrame frame, Node inliningTarget, PyObjectLookupAttr lookup) {
        final Pair<Object, Object> pair = PickleUtils.initMethodRef(frame, inliningTarget, lookup, this, T_METHOD_PERSISTENT_LOAD);
        this.persFunc = pair.getLeft();
        this.persFuncSelf = pair.getRight();
    }

    public void setInputStream(VirtualFrame frame, Node inliningTarget, PRaiseNode.Lazy raiseNode, PyObjectLookupAttr lookup, Object file) {
        this.peek = lookup.execute(frame, inliningTarget, file, T_METHOD_PEEK);
        if (this.peek == PNone.NO_VALUE) {
            this.peek = null;
        }
        this.readinto = lookup.execute(frame, inliningTarget, file, T_METHOD_READINTO);
        if (this.readinto == PNone.NO_VALUE) {
            this.readinto = null;
        }
        this.read = lookup.execute(frame, inliningTarget, file, T_METHOD_READ);
        this.readline = lookup.execute(frame, inliningTarget, file, T_METHOD_READLINE);
        if (this.readline == PNone.NO_VALUE || this.read == PNone.NO_VALUE) {
            throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, ErrorMessages.FILE_MUST_HAVE_A_AND_B_ATTRS, T_METHOD_READ, T_METHOD_READLINE);
        }
    }

    public void setStringInput(byte[] data, int dataLen) {
        this.inputBuffer = data;
        this.inputLen = dataLen;
        this.nextReadIdx = 0;
        this.prefetchedIdx = this.inputLen;
    }

    public void setInputEncoding(TruffleString encoding, TruffleString errors) {
        this.encoding = encoding;
        this.errors = errors;
        if (encoding == null) {
            this.encoding = T_ASCII_UPPERCASE;
        }
        if (errors == null) {
            this.errors = T_STRICT;
        }
    }

    public void setBuffers(VirtualFrame frame, Node inliningTarget, PyObjectGetIter getIter, Object buffers) {
        if (buffers == null || buffers == PNone.NONE || buffers == PNone.NO_VALUE) {
            this.buffers = null;
        } else {
            this.buffers = getIter.execute(frame, inliningTarget, buffers);
        }
    }

    private void resizeMemoList(int newSize) {
        assert newSize > memo.length;
        Object[] memoNew = new Object[newSize];
        PythonUtils.arraycopy(memo, 0, memoNew, 0, memo.length);
        memo = memoNew;
    }

    public Object memoGet(int idx) {
        return (idx >= memo.length) ? null : memo[idx];
    }

    public void memoPut(int idx, Object value) {
        if (idx >= memo.length) {
            resizeMemoList(idx * 2);
            assert idx < memo.length;
        }
        Object oldItem = memo[idx];
        memo[idx] = value;
        if (oldItem == null) {
            memoLen++;
        }
    }

    public void setMemo(Object[] memo) {
        this.memo = memo;
    }

    public Object[] getMemoCopy() {
        return PythonUtils.arrayCopyOf(memo, memo.length);
    }

    public HashingStorage copyMemoToHashingStorage(Node inliningTarget, HashingStorageSetItem setItem) {
        HashingStorage hashingStorage = EmptyStorage.INSTANCE;
        for (int i = 0; i < memo.length; i++) {
            // frame not needed for integer key
            hashingStorage = setItem.execute(null, inliningTarget, hashingStorage, i, memo[i]);
        }
        return hashingStorage;
    }

    public void clearMemo() {
        this.memo = new Object[this.memo.length];
    }

    // inner nodes
    public abstract static class BasePickleReadNode extends PicklerNodes.BasePickleNode {
        @Child private PyMemoryViewFromObject memoryViewNode;
        @Child private MemoryViewBuiltins.ToReadonlyNode toReadonlyNode;
        @Child private PythonBufferAcquireLibrary bufferAcquireLibrary;
        @Child private PythonBufferAccessLibrary bufferAccessLibrary;

        @Child private TruffleString.ParseLongNode tsParseLongNode;

        @Child private TruffleString.ParseIntNode tsParseIntNode;

        @SuppressWarnings("this-escape") // we only need the reference, doesn't matter that the
                                         // object may not yet be fully constructed
        private final IndirectCallData indirectCallData = IndirectCallData.createFor(this);

        protected TruffleString.ParseLongNode ensureTsParseLongNode() {
            if (tsParseLongNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                tsParseLongNode = insert(TruffleString.ParseLongNode.create());
            }
            return tsParseLongNode;
        }

        protected TruffleString.ParseIntNode ensureTsParseIntNode() {
            if (tsParseIntNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                tsParseIntNode = insert(TruffleString.ParseIntNode.create());
            }
            return tsParseIntNode;
        }

        protected PyMemoryViewFromObject ensureMemoryViewNode() {
            if (memoryViewNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                memoryViewNode = insert(PyMemoryViewFromObject.create());
            }
            return memoryViewNode;
        }

        protected MemoryViewBuiltins.ToReadonlyNode getToReadonlyNode() {
            if (toReadonlyNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toReadonlyNode = insert(MemoryViewBuiltinsFactory.ToReadonlyNodeFactory.create());
            }
            return toReadonlyNode;
        }

        protected PythonBufferAcquireLibrary getBufferAcquireLibrary() {
            if (bufferAcquireLibrary == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                bufferAcquireLibrary = insert(PythonBufferAcquireLibrary.getFactory().createDispatched(3));
            }
            return bufferAcquireLibrary;
        }

        protected PythonBufferAccessLibrary getBufferAccessLibrary() {
            if (bufferAccessLibrary == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                bufferAccessLibrary = insert(PythonBufferAccessLibrary.getFactory().createDispatched(3));
            }
            return bufferAccessLibrary;
        }

        public Object createMemoryViewFromBytes(VirtualFrame frame, byte[] bytes, int n) {
            return ensureMemoryViewNode().execute(frame, factory().createByteArray(bytes, n));
        }

        public Object createMemoryView(VirtualFrame frame, Object obj) {
            return ensureMemoryViewNode().execute(frame, obj);
        }

        protected PException badReadLine() {
            return raise(PythonBuiltinClassType.UnpicklingError, ErrorMessages.PICKLE_DATA_WAS_TRUNCATED);
        }

        protected PException pDataStackRaiseUnderflow(PUnpickler self) {
            return raise(PythonBuiltinClassType.UnpicklingError,
                            self.stack.mark ? ErrorMessages.PDATA_UNEXPECTED_MARK_FOUND : ErrorMessages.PDATA_UNPICKLING_STACK_UNDERFLOW);
        }

        protected void skipConsumed(VirtualFrame frame, PUnpickler self) {
            int consumed = self.nextReadIdx - self.prefetchedIdx;
            if (consumed <= 0) {
                return;
            }

            // otherwise we did something wrong
            assert self.peek != null;
            // This makes a useless copy...
            call(frame, self.read, consumed);
            self.prefetchedIdx = self.nextReadIdx;
        }

        protected int setStringInput(PUnpickler self, VirtualFrame frame, Object input) {
            Object buffer = getBufferAcquireLibrary().acquire(input, BufferFlags.PyBUF_CONTIG_RO, frame, indirectCallData);
            try {
                self.inputBuffer = getBufferAccessLibrary().getCopiedByteArray(buffer);
                self.inputLen = getBufferAccessLibrary().getBufferLength(buffer);
                self.nextReadIdx = 0;
                self.prefetchedIdx = self.inputLen;
                return self.inputLen;
            } finally {
                getBufferAccessLibrary().release(input, frame, indirectCallData);
            }
        }

        protected int readFromFile(VirtualFrame frame, PUnpickler self, int n) {
            assert self.read != null;
            Object data;
            int readSize;
            skipConsumed(frame, self);
            if (n == READ_WHOLE_LINE) {
                data = call(frame, self.readline);
            } else {
                int len;
                // Prefetch some data without advancing the file pointer, if possible
                if (self.peek != null && n < PREFETCH) {
                    len = PREFETCH;
                    try {
                        data = call(frame, self.peek, len);

                        readSize = setStringInput(self, frame, data);
                        self.prefetchedIdx = 0;
                        if (n <= readSize) {
                            return n;
                        }
                    } catch (PException pe) {
                        pe.expectCached(PythonBuiltinClassType.NotImplementedError, ensureErrProfile());
                        // peek() is probably not supported by the given file object
                    }
                }
                len = n;
                data = call(frame, self.read, len);
            }
            assert data != null;
            readSize = setStringInput(self, frame, data);
            return readSize;
        }

        protected byte read(VirtualFrame frame, PUnpickler self) {
            return read(frame, self, 1).get(0);
        }

        protected ByteArrayView read(VirtualFrame frame, PUnpickler self, int n) {
            if (n <= self.inputLen - self.nextReadIdx) {
                ByteArrayView bytesView = new ByteArrayView(self.inputBuffer, self.nextReadIdx);
                self.nextReadIdx += n;
                return bytesView;
            }
            return readImpl(frame, self, n);
        }

        private ByteArrayView readImpl(VirtualFrame frame, PUnpickler self, int n) {
            int numRead;
            // TODO: when GR-24978 is completed we should use PY_SSIZE_T_MAX
            if (self.nextReadIdx > Integer.MAX_VALUE - 1) {
                throw raise(PythonBuiltinClassType.UnpicklingError, ErrorMessages.READ_OVERFLOW);
            }
            // This case is handled by the _Unpickler_Read() macro for efficiency
            assert self.nextReadIdx + n > self.inputLen;
            if (self.read == null) {
                throw badReadLine();
            }
            // Extend the buffer to satisfy desired size
            numRead = readFromFile(frame, self, n);
            if (numRead < n) {
                throw badReadLine();
            }
            self.nextReadIdx = n;
            return new ByteArrayView(self.inputBuffer);
        }

        protected int readInto(VirtualFrame frame, PUnpickler self, byte[] buffer) {
            return readInto(frame, self, buffer, buffer.length);
        }

        protected int readInto(VirtualFrame frame, PUnpickler self, byte[] buffer, int numBytes) {
            int n = numBytes;
            assert n != READ_WHOLE_LINE;
            // Read from available buffer data, if any
            int inBuffer = self.inputLen - self.nextReadIdx;
            int bufOffset = 0;
            if (inBuffer > 0) {
                int toRead = Math.min(inBuffer, n);
                PythonUtils.arraycopy(self.inputBuffer, self.nextReadIdx, buffer, bufOffset, toRead);
                self.nextReadIdx += toRead;
                bufOffset += toRead;
                n -= toRead;
                if (n == 0) {
                    // Entire read was satisfied from buffer
                    return n;
                }
            }

            // Read from file
            if (self.read == null) {
                // We're unpickling memory, this means the input is truncated
                throw badReadLine();
            }
            skipConsumed(frame, self);

            if (self.readinto == null) {
                // readinto() not supported on file-like object, fall back to read() and copy into
                // destination buffer (bpo-39681) */
                Object data = call(frame, self.read, n);
                if (!(data instanceof PBytes)) {
                    throw raise(PythonBuiltinClassType.ValueError, ErrorMessages.S_RETURNED_NON_BYTES_P, "read()", data);
                }
                int readSize = getBufferAccessLibrary().getBufferLength(data);
                if (readSize < n) {
                    throw badReadLine();
                }
                getBufferAccessLibrary().readIntoByteArray(data, 0, buffer, bufOffset, n);
                return n;
            }

            int readSize;
            if (bufOffset == 0) {
                // Call readinto() into user buffer
                Object bufObj = createMemoryViewFromBytes(frame, buffer, n);
                Object readSizeObj = call(frame, self.readinto, bufObj);
                readSize = asSizeExact(frame, readSizeObj);
                if (readSize < 0) {
                    throw raise(PythonBuiltinClassType.ValueError, ErrorMessages.S_RETURNED_NEG_SIZE, "readinto()");
                }
            } else {
                // we need to read into a temp byte[] - cannot create memoryview with offset
                byte[] temp = new byte[n];
                // Call readinto() into user buffer
                Object bufObj = createMemoryViewFromBytes(frame, temp, n);
                Object readSizeObj = call(frame, self.readinto, bufObj);
                readSize = asSizeExact(frame, readSizeObj);

                if (readSize < 0) {
                    throw raise(PythonBuiltinClassType.ValueError, ErrorMessages.S_RETURNED_NEG_SIZE, "readinto()");
                }
                PythonUtils.arraycopy(temp, 0, buffer, bufOffset, readSize);
            }

            if (readSize < n) {
                throw badReadLine();
            }
            return n;
        }

        protected byte[] copyLine(PUnpickler self, byte[] line, int len) {
            return copyLine(self, line, 0, len);
        }

        protected byte[] copyLine(PUnpickler self, byte[] line, int lineStart, int len) {
            self.inputLine = new byte[len];
            PythonUtils.arraycopy(line, lineStart, self.inputLine, 0, len);
            return self.inputLine;
        }

        protected byte[] readLine(VirtualFrame frame, PUnpickler self) {
            int numRead;
            for (int i = self.nextReadIdx; i < self.inputLen; i++) {
                if (self.inputBuffer[i] == '\n') {
                    numRead = i - self.nextReadIdx + 1;
                    final byte[] line = copyLine(self, self.inputBuffer, self.nextReadIdx, numRead);
                    self.nextReadIdx = i + 1;
                    return line;
                }
            }
            if (self.read == null) {
                throw badReadLine();
            }

            numRead = readFromFile(frame, self, READ_WHOLE_LINE);
            if (numRead == 0 || self.inputBuffer[numRead - 1] != '\n') {
                throw badReadLine();
            }
            self.nextReadIdx = numRead;
            return copyLine(self, self.inputBuffer, numRead);
        }
    }

    public abstract static class FindClassNode extends BasePickleReadNode {
        public abstract Object execute(VirtualFrame frame, PUnpickler unpickler, TruffleString module, TruffleString name);

        @Specialization
        Object find(VirtualFrame frame, PUnpickler unpickler, TruffleString module, TruffleString name) {
            return findClass(frame, PythonContext.get(this).getCore(), unpickler, module, name);
        }
    }

    public abstract static class LoadNode extends BasePickleReadNode {
        @Child private PData.PDataPushNode pDataPushNode;
        @Child private PData.PDataPopNode pDataPopNode;
        @Child private PData.PDataPopTupleNode pDataPopTupleNode;
        @Child private PData.PDataPopListNode pDataPopListNode;
        @Child private HashingCollectionNodes.GetClonedHashingStorageNode getHashingStorageNode;
        @Child private ListBuiltins.ListExtendNode listExtendNode;
        @Child private SetAttributeNode.Dynamic setAttributeNode;
        @Child private IntNodes.PyLongFromByteArray pyLongFromByteArray;
        @Child private PyObjectSetItem setItemNode;
        @Child HashingStorageCopy hashCopy;
        @Child HashingStorageAddAllToOther addAllToOther;

        public abstract Object execute(VirtualFrame frame, PUnpickler self);

        protected HashingStorageCopy ensureHashingStorageCopy() {
            if (hashCopy == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                hashCopy = insert(HashingStorageCopy.create());
            }
            return hashCopy;
        }

        protected HashingStorageAddAllToOther ensureHashingStorageAddAllToOther() {
            if (addAllToOther == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                addAllToOther = insert(HashingStorageAddAllToOther.create());
            }
            return addAllToOther;
        }

        protected Object longFromBytes(byte[] data, boolean bigEndian) {
            if (pyLongFromByteArray == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                pyLongFromByteArray = insert(IntNodesFactory.PyLongFromByteArrayNodeGen.create());
            }
            return pyLongFromByteArray.executeCached(data, bigEndian);
        }

        protected void setAttribute(VirtualFrame frame, Object object, Object key, Object value) {
            if (setAttributeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                setAttributeNode = insert(new SetAttributeNode.Dynamic());
            }
            setAttributeNode.execute(frame, object, key, value);
        }

        protected void extendList(VirtualFrame frame, PList list, Object slice) {
            if (listExtendNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                listExtendNode = insert(ListBuiltins.ListExtendNode.create());
            }
            listExtendNode.execute(frame, list, slice);
        }

        protected void pDataPush(PUnpickler self, Object obj) {
            if (pDataPushNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                pDataPushNode = insert(PData.PDataPushNode.create());
            }
            pDataPushNode.execute(self.stack, obj);
        }

        protected Object pDataPop(PUnpickler self) {
            if (pDataPopNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                pDataPopNode = insert(PData.PDataPopNode.create());
            }
            return pDataPopNode.execute(self.stack);
        }

        protected Object pDataPopTuple(PUnpickler self, int start) {
            if (pDataPopTupleNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                pDataPopTupleNode = insert(PData.PDataPopTupleNode.create());
            }
            return pDataPopTupleNode.execute(self.stack, start);
        }

        protected Object pDataPopList(PUnpickler self, int start) {
            if (pDataPopListNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                pDataPopListNode = insert(PData.PDataPopListNode.create());
            }
            return pDataPopListNode.execute(self.stack, start);
        }

        protected HashingStorage getClonedHashingStorage(VirtualFrame frame, Object obj) {
            if (getHashingStorageNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getHashingStorageNode = insert(HashingCollectionNodes.GetClonedHashingStorageNode.create());
            }
            return getHashingStorageNode.doNoValueCached(frame, obj);
        }

        private void setItem(VirtualFrame frame, Object object, Object key, Object value) {
            if (setItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                setItemNode = insert(PyObjectSetItem.create());
            }
            setItemNode.executeCached(frame, object, key, value);
        }

        protected int marker(PUnpickler self) {
            if (self.numMarks < 1) {
                throw raise(PythonBuiltinClassType.UnpicklingError, ErrorMessages.COULD_NOT_FIND_MARK);
            }

            int mark = self.marks[--self.numMarks];
            self.stack.mark = self.numMarks != 0;
            self.stack.fence = self.numMarks != 0 ? self.marks[self.numMarks - 1] : 0;
            return mark;
        }

        void loadNone(PUnpickler self) {
            pDataPush(self, PNone.NONE);
        }

        private static long calcBinInt(ByteArrayView s, int nBytes) {
            long x = 0;

            for (int i = 0; i < nBytes; i++) {
                x |= (long) s.getUnsigned(i) << (8 * i);
            }

            // Unlike BININT1 and BININT2, BININT (more accurately BININT4) is signed, so on a box
            // with longs bigger than 4 bytes we need to extend a BININT's sign bit to the full
            // width.
            if (Long.BYTES > 4 && nBytes == 4) {
                x |= -(x & (1L << 31));
            }

            return x;
        }

        private int calcBinSize(ByteArrayView s, int nbytes) {
            int i;
            int x = 0;
            int n = nbytes;

            // TODO: when GR-24978 is completed we should use PY_SSIZE_T_MAX
            if (n > Integer.BYTES) {
                // Check for integer overflow. BINBYTES8 and BINUNICODE8 opcodes have 64-bit size
                // that can't be represented on 32-bit platform.
                for (i = Integer.BYTES; i < n; i++) {
                    if (s.get(i) != 0) {
                        throw raise(PythonBuiltinClassType.OverflowError);
                    }
                }
                n = Integer.BYTES;
            }

            for (i = 0; i < n; i++) {
                x |= s.getUnsigned(i) << (8 * i);
            }

            // TODO: GR-24978 check for PY_SSIZE_T_MAX (see: _cpickle.c:calc_binsize)
            // if (x > Integer.MAX_VALUE) {
            // throw raise(PythonBuiltinClassType.OverflowError);
            // }

            return x;
        }

        private void loadBinIntX(PUnpickler self, ByteArrayView s, int size) {
            long x = calcBinInt(s, size);
            pDataPush(self, x);
        }

        private void loadBinInt(VirtualFrame frame, PUnpickler self) {
            final ByteArrayView s = read(frame, self, 4);
            loadBinIntX(self, s, 4);
        }

        private void loadBinInt1(VirtualFrame frame, PUnpickler self) {
            final ByteArrayView s = read(frame, self, 1);
            loadBinIntX(self, s, 1);
        }

        private void loadBinInt2(VirtualFrame frame, PUnpickler self) {
            final ByteArrayView s = read(frame, self, 2);
            loadBinIntX(self, s, 2);
        }

        private void loadInt(VirtualFrame frame, PUnpickler self) {
            Object value;
            final byte[] s = readLine(frame, self);
            if (s.length < 2) {
                throw badReadLine();
            }

            try {
                long x = PickleUtils.asciiBytesToLong(s, ensureTsParseLongNode(), ensureTsFromByteArray());
                if (s.length == 3 && (x == 0 || x == 1)) {
                    value = x != 0;
                } else {
                    value = x;
                }
            } catch (TruffleString.NumberFormatException nfe) {
                // Hm, maybe we've got something long. Let's try reading it as a Python int object.
                value = parseInt(frame, getValidIntString(s));
            }
            pDataPush(self, value);
        }

        private void loadLong(VirtualFrame frame, PUnpickler self) {
            Object value;
            final byte[] s = readLine(frame, self);
            if (s.length < 2) {
                throw badReadLine();
            }

            // s[len-2] will usually be 'L' (and s[len-1] is '\n'); we need to remove the 'L' before
            // calling PyLong_FromString. In order to maintain compatibility with Python 3.0.0, we
            // don't actually *require* the 'L' to be present.
            if (s[s.length - 2] == 'L') {
                s[s.length - 2] = 0;
            }
            try {
                value = PickleUtils.asciiBytesToLong(s, ensureTsParseLongNode(), ensureTsFromByteArray());
            } catch (TruffleString.NumberFormatException nfe) {
                value = parseInt(frame, s);
            }
            pDataPush(self, value);
        }

        private void loadCountedLong(VirtualFrame frame, PUnpickler self, int n) {
            assert n == 1 || n == 4;
            int size = n;
            final ByteArrayView nbytes = read(frame, self, size);
            size = (int) calcBinInt(nbytes, size);

            Object value;

            if (size < 0) {
                throw raise(PythonErrorType.UnpicklingError, ErrorMessages.LONG_PICKLE_HAS_NEG_BYTE_CNT);
            }

            if (size == 0) {
                value = 0L;
            } else {
                // Read the raw little-endian bytes and convert.
                final ByteArrayView pdata = read(frame, self, size);
                value = longFromBytes(pdata.getBytes(size), false);
            }
            pDataPush(self, value);
        }

        private void loadFloat(VirtualFrame frame, PUnpickler self) {
            Object value;
            final byte[] s = readLine(frame, self);
            if (s.length < 2) {
                throw badReadLine();
            }

            // TODO: (cbasca) we skip an entire branch from _pickle.c:load_float
            // TODO: (cbasca) should we return a PFloat ? (same for load_int/long variants)
            value = PickleUtils.asciiBytesToDouble(s, getRaiseNode(), PythonBuiltinClassType.OverflowError);
            pDataPush(self, value);
        }

        private void loadBinFloat(VirtualFrame frame, PUnpickler self) {
            Object value;
            ByteArrayView s = read(frame, self, 8);

            value = NumericSupport.bigEndian().getDouble(s.getBytes(Double.BYTES), 0);
            pDataPush(self, value);
        }

        private void loadCountedBinBytes(VirtualFrame frame, PUnpickler self, int nbytes) {
            final ByteArrayView s = read(frame, self, nbytes);
            int size = calcBinSize(s, nbytes);
            if (size < 0) {
                throw raise(PythonBuiltinClassType.OverflowError, ErrorMessages.S_EXCEEDS_MAX_SIZE_N_BYTES, "BINBYTES", Integer.MAX_VALUE);
            }

            byte[] buffer = new byte[size];
            readInto(frame, self, buffer);

            Object bytes = factory().createBytes(buffer);
            pDataPush(self, bytes);
        }

        private void loadCountedByteArray(VirtualFrame frame, PUnpickler self) {
            final ByteArrayView s = read(frame, self, 8);
            int size = calcBinSize(s, 8);
            if (size < 0) {
                throw raise(PythonBuiltinClassType.OverflowError, ErrorMessages.S_EXCEEDS_MAX_SIZE_N_BYTES, "BYTEARRAY8", Integer.MAX_VALUE);
            }

            byte[] buffer = new byte[size];
            readInto(frame, self, buffer);

            Object bytearray = factory().createByteArray(buffer);
            pDataPush(self, bytearray);
        }

        private void loadNextBuffer(VirtualFrame frame, PUnpickler self) {
            if (self.buffers == null) {
                throw raise(PythonBuiltinClassType.UnpicklingError, ErrorMessages.PICKLE_STREAM_NO_BUFFERS);
            }

            Object buf = getNextItem(frame, self.buffers);
            if (buf == null) {
                throw raise(PythonBuiltinClassType.UnpicklingError, ErrorMessages.NOT_ENOUGH_BUFFERS);
            }

            pDataPush(self, buf);
        }

        private void loadReadOnlyBuffer(VirtualFrame frame, PUnpickler self) {
            int len = self.stack.size;
            if (len <= self.stack.fence) {
                throw pDataStackRaiseUnderflow(self);
            }

            Object obj = self.stack.data[len - 1];
            Object view = createMemoryView(frame, obj);

            boolean readonly = getBufferAccessLibrary().isReadonly(view);
            if (!readonly) {
                // Original object is writable
                // set the view's readonly attr to true
                view = getToReadonlyNode().call(frame, view);
                self.stack.data[len - 1] = view;
            }
        }

        private void loadCountedBinString(VirtualFrame frame, PUnpickler self, int nbytes) {
            Object obj;
            ByteArrayView s = read(frame, self, nbytes);

            int size = calcBinSize(s, nbytes);
            if (size < 0) {
                throw raise(PythonBuiltinClassType.UnpicklingError, ErrorMessages.S_EXCEEDS_MAX_SIZE_N_BYTES, "BINSTRING", Integer.MAX_VALUE);
            }

            s = read(frame, self, size);

            // Convert Python 2.x strings to bytes if the *encoding* given to the Unpickler was
            // 'bytes'. Otherwise, convert them to unicode.
            final PBytes bytes = factory().createBytes(s.getBytes(size), size);
            if (ensureTsEqualNode().execute(self.encoding, T_CODEC_BYTES, TS_ENCODING)) {
                obj = bytes;
            } else {
                obj = decode(frame, bytes, self.encoding, self.errors);
            }

            pDataPush(self, obj);
        }

        private void loadString(VirtualFrame frame, PUnpickler self) {
            Object bytes;
            Object obj;
            byte[] s = readLine(frame, self);
            int len = s.length;
            // Strip the newline
            len--;
            int pStart;
            // Strip outermost quotes
            if (len >= 2 && s[0] == s[len - 1] && (s[0] == '\'' || s[0] == '"')) {
                pStart = 1;
                len -= 2;
            } else {
                throw raise(PythonBuiltinClassType.UnpicklingError, ErrorMessages.S_OPCODE_ARG_MUST_BE_QUOTED, "STRING");
            }
            assert len >= 0;

            // Use the PyBytes API to decode the string, since that is what is used to encode, and
            // then coerce the result to Unicode.
            bytes = escapeDecode(frame, factory(), s, pStart, len);

            // Leave the Python 2.x strings as bytes if the *encoding* given to the Unpickler was
            // 'bytes'. Otherwise, convert them to unicode.
            if (ensureTsEqualNode().execute(self.encoding, T_CODEC_BYTES, TS_ENCODING)) {
                obj = bytes;
            } else {
                obj = decode(frame, bytes, self.encoding, self.errors);
            }

            pDataPush(self, obj);
        }

        private void loadUnicode(VirtualFrame frame, PUnpickler self) {
            Object str;
            final byte[] s = readLine(frame, self);
            if (s.length < 1) {
                throw badReadLine();
            }

            str = unicodeRawDecodeEscape(frame, s, s.length - 1);
            pDataPush(self, str);
        }

        private void loadBinCountedUnicode(VirtualFrame frame, PUnpickler self, int nbytes) {
            Object str;
            ByteArrayView s = read(frame, self, nbytes);

            int size = calcBinSize(s, nbytes);
            if (size < 0) {
                throw raise(PythonBuiltinClassType.OverflowError, ErrorMessages.S_EXCEEDS_MAX_SIZE_N_BYTES, "BINUNICODE", Integer.MAX_VALUE);
            }

            s = read(frame, self, size);

            str = decodeUTF8(frame, s, size, T_ERRORS_SURROGATEPASS);
            pDataPush(self, str);
        }

        private void loadCountedTuple(PUnpickler self, int len) {
            Object tuple;

            if (self.stack.size < len) {
                throw pDataStackRaiseUnderflow(self);
            }

            tuple = pDataPopTuple(self, self.stack.size - len);
            pDataPush(self, tuple);
        }

        private void loadTuple(PUnpickler self) {
            int i = marker(self);
            loadCountedTuple(self, self.stack.size - i);
        }

        private void loadEmptyList(PUnpickler self) {
            pDataPush(self, factory().createList());
        }

        private void loadList(PUnpickler self) {
            int i = marker(self);
            Object list = pDataPopList(self, i);
            pDataPush(self, list);
        }

        private void loadEmptyDict(PUnpickler self) {
            pDataPush(self, factory().createDict());
        }

        private void loadDict(VirtualFrame frame, PUnpickler self) {
            Object key, value;
            int i, j, k;

            i = marker(self);
            j = self.stack.size;

            if ((j - i) % 2 != 0) {
                throw raise(PythonBuiltinClassType.UnpicklingError, ErrorMessages.ODD_NR_ITEMS_FOR_S, "DICT");
            }

            HashingStorage storage = EmptyStorage.INSTANCE;
            for (k = i + 1; k < j; k += 2) {
                key = self.stack.data[k - 1];
                value = self.stack.data[k];
                storage = setHashingStorageItem(frame, storage, key, value);
            }

            self.stack.clear(i);
            pDataPush(self, factory().createDict(storage));
        }

        private void loadEmptySet(PUnpickler self) {
            pDataPush(self, factory().createSet());
        }

        private void loadAddItems(VirtualFrame frame, PUnpickler self) {
            int mark = marker(self);
            int len = self.stack.size;
            if (mark > len || mark <= self.stack.fence) {
                throw pDataStackRaiseUnderflow(self);
            }
            if (len == mark) {
                // nothing to do
                return;
            }

            Object set = self.stack.data[mark - 1];
            if (set instanceof PSet) {
                Object items = pDataPopTuple(self, mark);
                final HashingStorage union = ((PSet) set).getDictStorage().unionCached(getHashingStorage(frame, items), ensureHashingStorageCopy(), ensureHashingStorageAddAllToOther());
                ((PSet) set).setDictStorage(union);
            } else {
                Object add_func;
                add_func = lookupAttributeStrict(frame, set, T_ADD);
                for (int i = mark; i < len; i++) {
                    Object item = self.stack.data[i];
                    try {
                        call(frame, add_func, item);
                    } catch (PException pe) {
                        self.stack.clear(i + 1);
                        self.stack.size = mark;
                        throw pe;
                    }
                }
                self.stack.size = mark;
            }
        }

        private void loadFrozenSet(VirtualFrame frame, PUnpickler self) {
            int i = marker(self);
            Object items = pDataPopTuple(self, i);
            Object frozenset = factory().createFrozenSet(getClonedHashingStorage(frame, items));
            pDataPush(self, frozenset);
        }

        private Object instantiate(VirtualFrame frame, Object cls, Object args) {
            // Caller must assure args are a tuple. Normally, args come from Pdata_poptuple which
            // packs objects from the top of the stack into a newly created tuple.
            assert args instanceof PTuple;
            if (length(frame, args) == 0 && PGuards.isPythonClass(cls)) {
                Object func = getLookupAttrNode().executeCached(frame, cls, T___GETINITARGS__);
                if (func == PNone.NO_VALUE) {
                    final Object newMethod = lookupAttributeStrict(frame, cls, T___NEW__);
                    return callNew(frame, newMethod, cls);
                }
            }
            return callStarArgs(frame, cls, args);
        }

        private void loadObj(VirtualFrame frame, PUnpickler self) {
            Object cls, args, obj = null;
            int i = marker(self);

            if (self.stack.size - i < 1) {
                throw pDataStackRaiseUnderflow(self);
            }

            args = pDataPopTuple(self, i + 1);
            cls = pDataPop(self);
            if (cls != null) {
                obj = instantiate(frame, cls, args);
            }

            assert obj != null;
            pDataPush(self, obj);
        }

        private void loadInst(VirtualFrame frame, PythonContext ctx, PUnpickler self) {
            Object cls = null;
            Object obj = null;
            int i = marker(self);
            byte[] s = readLine(frame, self);
            if (s.length < 2) {
                throw badReadLine();
            }

            // Here it is safe to use PyUnicode_DecodeASCII(), even though non-ASCII identifiers are
            // permitted in Python 3.0, since the INST opcode is only supported by older protocols
            // on Python 2.x.
            Object moduleName = decodeASCII(frame, s, s.length - 1, T_ERRORS_STRICT);

            s = readLine(frame, self);
            if (s != null) {
                if (s.length < 2) {
                    throw badReadLine();
                }
                try {
                    Object className = decodeASCII(frame, s, s.length - 1, T_ERRORS_STRICT);
                    cls = findClass(frame, ctx.getCore(), self, moduleName, className);
                } catch (PException ignored) {
                }
            }

            assert cls != null;
            Object args = pDataPopTuple(self, i);
            if (args != null) {
                obj = instantiate(frame, cls, args);
            }

            assert obj != null;
            pDataPush(self, obj);
        }

        private void loadNewObj(VirtualFrame frame, PUnpickler self) {
            // Stack is ... cls argtuple, and we want to call cls.__new__(cls, *argtuple).
            Object args = pDataPop(self);
            if (!(args instanceof PTuple)) {
                throw raise(PythonBuiltinClassType.UnpicklingError, ErrorMessages.S_EXPECTED_AN_ARG_S, "NEWOBJ", "tuple.");
            }

            Object cls = pDataPop(self);
            if (!PGuards.isPythonClass(cls)) {
                throw raise(PythonBuiltinClassType.UnpicklingError, ErrorMessages.S_CLASS_ARG_S, "NEWOBJ", "isn't a type object");
            }
            // Call __new__
            final Object tpNew = getLookupAttrNode().executeCached(frame, cls, T___NEW__);
            if (tpNew == PNone.NO_VALUE) {
                throw raise(PythonBuiltinClassType.UnpicklingError, ErrorMessages.S_CLASS_ARG_S, "NEWOBJ", "has NULL tp_new");
            }

            Object obj = callNew(frame, tpNew, cls, args);
            pDataPush(self, obj);
        }

        private void loadNewObjEx(VirtualFrame frame, PUnpickler self) {
            Object kwargs = pDataPop(self);
            Object args = pDataPop(self);
            Object cls = pDataPop(self);
            if (!PGuards.isPythonClass(cls)) {
                throw raise(PythonBuiltinClassType.UnpicklingError, ErrorMessages.S_CLASS_ARG_MUST_BE_TYPE_NOT_P, "NEWOBJ_EX", cls);
            }
            final Object tpNew = getLookupAttrNode().executeCached(frame, cls, T___NEW__);
            if (tpNew == PNone.NO_VALUE) {
                throw raise(PythonBuiltinClassType.UnpicklingError, ErrorMessages.S_CLASS_ARG_DOES_NOT_HAVE_S, "NEWOBJ_EX", T___NEW__);
            }
            if (!(args instanceof PTuple)) {
                throw raise(PythonBuiltinClassType.UnpicklingError, ErrorMessages.S_ARG_MUST_BE_S_NOT_P, "NEWOBJ_EX args", "tuple", args);
            }
            if (!(kwargs instanceof PDict)) {
                throw raise(PythonBuiltinClassType.UnpicklingError, ErrorMessages.S_ARG_MUST_BE_S_NOT_P, "NEWOBJ_EX kwargs", "dict", kwargs);
            }

            Object obj = callNew(frame, tpNew, cls, args, kwargs);
            pDataPush(self, obj);
        }

        private void doAppend(VirtualFrame frame, PUnpickler self, int x) {
            Object value, slice, list;
            int len, i;

            len = self.stack.size;
            if (x > len || x <= self.stack.fence) {
                throw pDataStackRaiseUnderflow(self);
            }
            if (len == x) {
                // nothing to do
                return;
            }

            list = self.stack.data[x - 1];

            if (list instanceof PList) {
                slice = pDataPopList(self, x);
                extendList(frame, (PList) list, slice);
            } else {
                Object extendFunc = getLookupAttrNode().executeCached(frame, list, T_EXTEND);
                if (extendFunc != PNone.NO_VALUE) {
                    slice = pDataPopList(self, x);
                    call(frame, extendFunc, slice);
                } else {
                    // Even if the PEP 307 requires extend() and append() methods, fall back on
                    // append() if the object
                    // has no extend() method for backward compatibility.
                    Object appendFunc = lookupAttributeStrict(frame, list, T_APPEND);
                    for (i = x; i < len; i++) {
                        value = self.stack.data[i];
                        try {
                            call(frame, appendFunc, value);
                        } catch (PException pe) {
                            self.stack.clear(i + 1);
                            self.stack.size = x;
                            return;
                        }
                    }
                    self.stack.size = x;
                }
            }
        }

        private void loadGlobal(VirtualFrame frame, PythonContext ctx, PUnpickler self) {
            Object global = null;
            TruffleString globalName;
            byte[] s = readLine(frame, self);
            if (s.length < 2) {
                throw badReadLine();
            }
            TruffleString moduleName = PickleUtils.decodeUTF8Strict(s, s.length - 1, ensureTsFromByteArray(), ensureTsSwitchEncodingNode());

            s = readLine(frame, self);
            if (s != null) {
                if (s.length < 2) {
                    throw badReadLine();
                }
                globalName = PickleUtils.decodeUTF8Strict(s, s.length - 1, ensureTsFromByteArray(), ensureTsSwitchEncodingNode());
                if (globalName != null) {
                    global = findClass(frame, ctx.getCore(), self, moduleName, globalName);
                }
            }

            pDataPush(self, global);
        }

        private void loadStackGlobal(VirtualFrame frame, PythonContext ctx, PUnpickler self) {
            Object globalName = null;
            Object moduleName = null;
            try {
                globalName = pDataPop(self);
                moduleName = pDataPop(self);
            } catch (PException pe) {
                pe.expectCached(PythonBuiltinClassType.UnpicklingError, ensureErrProfile());
            }
            if (!PGuards.isString(moduleName) || !PGuards.isString(globalName)) {
                throw raise(PythonBuiltinClassType.UnpicklingError, ErrorMessages.S_REQ_STR, "STACK_GLOBAL");
            }
            Object global = findClass(frame, ctx.getCore(), self, moduleName, globalName);
            pDataPush(self, global);
        }

        private void loadAppend(VirtualFrame frame, PUnpickler self) {
            if (self.stack.size - 1 <= self.stack.fence) {
                throw pDataStackRaiseUnderflow(self);
            }
            doAppend(frame, self, self.stack.size - 1);
        }

        private void loadAppends(VirtualFrame frame, PUnpickler self) {
            int i = marker(self);
            doAppend(frame, self, i);
        }

        private void loadBuild(VirtualFrame frame, PUnpickler self) {
            Object slotstate;
            // Stack is ... instance, state. We want to leave instance at the stack top, possibly
            // mutated via instance.__setstate__(state).
            if (self.stack.size - 2 < self.stack.fence) {
                throw pDataStackRaiseUnderflow(self);
            }

            Object state = pDataPop(self);
            Object inst = self.stack.data[self.stack.size - 1];

            Object setstate = getLookupAttrNode().executeCached(frame, inst, T___SETSTATE__);
            if (setstate != PNone.NO_VALUE) {
                // The explicit __setstate__ is responsible for everything.
                call(frame, setstate, state);
                return;
            }

            // A default __setstate__. First see whether state embeds a slot state dict too (a proto
            // 2 addition).
            if (state instanceof PTuple && length(frame, state) == 2) {
                Object tmp = state;

                state = getItem(frame, tmp, 0);
                slotstate = getItem(frame, tmp, 1);
            } else {
                slotstate = null;
            }

            // Set inst.__dict__ from the state dict (if any).
            if (state != PNone.NONE) {
                if (!(state instanceof PDict)) {
                    throw raise(PythonBuiltinClassType.UnpicklingError, ErrorMessages.S_STATE_NOT_DICT, "");
                }
                Object dict = lookupAttributeStrict(frame, inst, T___DICT__);

                final HashingStorage storage = getHashingStorage(frame, state);
                // entries = hashLib.entries(storage);
                final HashingStorage dictStorage = getHashingStorage(frame, dict);
                ensureHashingStorageAddAllToOther().executeCached(frame, storage, dictStorage);

                HashingStorageIterator it = getHashingStorageIterator(storage);
                HashingStorageIteratorNext nextNode = ensureHashingStorageIteratorNext();
                HashingStorageIteratorKey getKeyNode = ensureHashingStorageIteratorKey();
                HashingStorageIteratorValue getValueNode = ensureHashingStorageIteratorValue();
                while (nextNode.executeCached(storage, it)) {
                    // normally the keys for instance attributes are interned. we should try to do
                    // that here.
                    // TODO: cbasca: GR-28568 when string interning is supported in truffle (intern
                    // the keys)
                    // if (PyUnicode_CheckExact(d_key))
                    // PyUnicode_InternInPlace(&d_key);
                    // TODO: shouldn't this be setting the storage back to the dict? The issue is
                    // that it may be a temporary storage created in getHashingStorage
                    setHashingStorageItem(frame, dictStorage, getKeyNode.executeCached(storage, it), getValueNode.executeCached(storage, it));
                }
            }

            // Also set instance attributes from the slotstate dict (if any).
            if (slotstate != null) {
                if (!(slotstate instanceof PDict)) {
                    throw raise(PythonBuiltinClassType.UnpicklingError, ErrorMessages.S_STATE_NOT_DICT, "slot");
                }
                final HashingStorage storage = getHashingStorage(frame, slotstate);
                HashingStorageIterator it = getHashingStorageIterator(storage);
                HashingStorageIteratorNext nextNode = ensureHashingStorageIteratorNext();
                HashingStorageIteratorKey getKeyNode = ensureHashingStorageIteratorKey();
                HashingStorageIteratorValue getValueNode = ensureHashingStorageIteratorValue();
                while (nextNode.executeCached(storage, it)) {
                    setAttribute(frame, inst, getKeyNode.executeCached(storage, it), getValueNode.executeCached(storage, it));
                }
            }
        }

        private void loadDup(PUnpickler self) {
            int len = self.stack.size;
            if (len <= self.stack.fence) {
                throw pDataStackRaiseUnderflow(self);
            }
            Object last = self.stack.data[len - 1];
            pDataPush(self, last);
        }

        private void loadBinGet(VirtualFrame frame, PUnpickler self) {
            byte s = read(frame, self);
            int idx = s & 0xff;

            Object value = self.memoGet(idx);
            if (value == null) {
                throw raise(PythonBuiltinClassType.UnpicklingError, ErrorMessages.MEMO_VALUE_NOT_FOUND_AT_INDEX_D, idx);
            }

            pDataPush(self, value);
        }

        private void loadLongBinGet(VirtualFrame frame, PUnpickler self) {
            ByteArrayView s = read(frame, self, 4);
            int idx = calcBinSize(s, 4);

            Object value = self.memoGet(idx);
            if (value == null) {
                throw raise(PythonBuiltinClassType.UnpicklingError, ErrorMessages.MEMO_VALUE_NOT_FOUND_AT_INDEX_D, idx);
            }

            pDataPush(self, value);
        }

        private void loadGet(VirtualFrame frame, PUnpickler self) {
            byte[] s = readLine(frame, self);
            if (s.length < 2) {
                throw badReadLine();
            }
            int idx;
            try {
                idx = PickleUtils.asciiBytesToInt(s, ensureTsParseIntNode(), ensureTsFromByteArray());
            } catch (TruffleString.NumberFormatException nfe) {
                // TODO handle exception [GR-38101]
                throw CompilerDirectives.shouldNotReachHere();
            }
            Object value = self.memoGet(idx);
            if (value == null) {
                throw raise(PythonBuiltinClassType.UnpicklingError, ErrorMessages.MEMO_VALUE_NOT_FOUND_AT_INDEX_D, idx);
            }
            pDataPush(self, value);
        }

        private static void loadMark(PUnpickler self) {
            // Note that we split the (pickle.py) stack into two stacks, an object stack and a mark
            // stack. Here we
            // push a mark onto the mark stack.

            if (self.numMarks >= self.marksSize) {
                int alloc = (self.numMarks << 1) + 20;
                int[] marksNew = new int[alloc];
                if (self.marks != null) {
                    PythonUtils.arraycopy(self.marks, 0, marksNew, 0, Math.min(alloc, self.marks.length));
                }
                self.marks = marksNew;
                self.marksSize = alloc;
            }

            self.stack.mark = true;
            self.marks[self.numMarks++] = self.stack.fence = self.stack.size;
        }

        private void loadBinPut(VirtualFrame frame, PUnpickler self) {
            byte s = read(frame, self);
            if (self.stack.size <= self.stack.fence) {
                throw pDataStackRaiseUnderflow(self);
            }
            Object value = self.stack.data[self.stack.size - 1];
            int idx = s & 0xff;
            self.memoPut(idx, value);
        }

        private void loadLongBinPut(VirtualFrame frame, PUnpickler self) {
            ByteArrayView s = read(frame, self, 4);
            if (self.stack.size <= self.stack.fence) {
                throw pDataStackRaiseUnderflow(self);
            }

            Object value = self.stack.data[self.stack.size - 1];
            int idx = calcBinSize(s, 4);
            if (idx < 0) {
                throw raise(PythonBuiltinClassType.ValueError, ErrorMessages.NEG_S_ARG, "LONG_BINPUT");
            }
            self.memoPut(idx, value);
        }

        private void loadPut(VirtualFrame frame, PUnpickler self) {
            byte[] s = readLine(frame, self);
            if (s.length < 2) {
                throw badReadLine();
            }
            if (self.stack.size <= self.stack.fence) {
                throw pDataStackRaiseUnderflow(self);
            }

            Object value = self.stack.data[self.stack.size - 1];
            int idx;
            try {
                idx = PickleUtils.asciiBytesToInt(s, ensureTsParseIntNode(), ensureTsFromByteArray());
            } catch (TruffleString.NumberFormatException nfe) {
                // TODO handle exception [GR-38101]
                throw CompilerDirectives.shouldNotReachHere();
            }
            if (idx < 0) {
                throw raise(PythonBuiltinClassType.ValueError, ErrorMessages.NEG_S_ARG, "PUT");
            }
            self.memoPut(idx, value);
        }

        private void loadMemoize(PUnpickler self) {
            if (self.stack.size <= self.stack.fence) {
                throw pDataStackRaiseUnderflow(self);
            }
            Object value = self.stack.data[self.stack.size - 1];
            self.memoPut(self.memoLen, value);
        }

        private void loadPop(PUnpickler self) {
            int len = self.stack.size;

            // Note that we split the (pickle.py) stack into two stacks, an object stack and a
            // mark stack. We have to be clever and pop the right one. We do this by looking at the
            // top of the mark stack first, and only signalling a stack underflow if the object
            // stack is empty and the mark stack doesn't match our expectations.
            if (self.numMarks > 0 && self.marks[self.numMarks - 1] == len) {
                self.numMarks--;
                self.stack.mark = self.numMarks != 0;
                self.stack.fence = self.numMarks != 0 ? self.marks[self.numMarks - 1] : 0;
            } else if (len <= self.stack.fence) {
                throw pDataStackRaiseUnderflow(self);
            } else {
                len--;
                self.stack.size = len;
            }
        }

        private void loadPopMark(PUnpickler self) {
            int i = marker(self);
            self.stack.clear(i);
        }

        private void doSetItems(VirtualFrame frame, PUnpickler self, int x) {
            Object value, key;
            int len, i;

            len = self.stack.size;
            if (x > len || x <= self.stack.fence) {
                throw pDataStackRaiseUnderflow(self);
            }
            if (len == x) {
                // nothing to do
                return;
            }
            if ((len - x) % 2 != 0) {
                // Corrupt or hostile pickle -- we never write one like this.
                throw raise(PythonBuiltinClassType.UnpicklingError, ErrorMessages.ODD_NR_ITEMS_FOR_S, "SETITEMS");
            }

            Object dict = self.stack.data[x - 1];
            final boolean isBuiltinDict = dict instanceof PDict pDict && PGuards.isBuiltinDict(pDict);
            for (i = x + 1; i < len; i += 2) {
                key = self.stack.data[i - 1];
                value = self.stack.data[i];
                if (isBuiltinDict) {
                    setDictItem(frame, (PDict) dict, key, value);
                } else {
                    setItem(frame, dict, key, value);
                }
            }

            self.stack.clear(x);
        }

        private void loadSetItem(VirtualFrame frame, PUnpickler self) {
            doSetItems(frame, self, self.stack.size - 2);
        }

        private void loadSetItems(VirtualFrame frame, PUnpickler self) {
            int i = marker(self);
            doSetItems(frame, self, i);
        }

        private void loadPersId(VirtualFrame frame, PUnpickler self) {
            if (self.persFunc == null) {
                throw raise(PythonBuiltinClassType.UnpicklingError, ErrorMessages.PID_NO_FUNC);
            }
            byte[] s = readLine(frame, self);
            if (s.length < 1) {
                throw badReadLine();
            }

            Object pid;
            try {
                pid = decodeASCII(frame, s, s.length - 1, T_ERRORS_STRICT);
            } catch (PException pe) {
                pe.expectCached(PythonBuiltinClassType.UnicodeDecodeError, ensureErrProfile());
                throw raise(PythonBuiltinClassType.UnpicklingError, ErrorMessages.PID_PROTO_0);
            }

            pDataPush(self, callPersistentLoad(frame, self, pid));
        }

        private Object callPersistentLoad(VirtualFrame frame, PUnpickler self, Object pid) {
            if (self.persFuncSelf == null) {
                return call(frame, self.persFunc, pid);
            } else {
                return call(frame, self.persFunc, self.persFuncSelf, pid);
            }
        }

        private void loadBinPersId(VirtualFrame frame, PUnpickler self) {
            if (self.persFunc == null) {
                throw raise(PythonBuiltinClassType.UnpicklingError, ErrorMessages.PID_NO_FUNC);
            }
            Object pid = pDataPop(self);
            pDataPush(self, callPersistentLoad(frame, self, pid));
        }

        private void loadReduce(VirtualFrame frame, PUnpickler self) {
            Object obj = null;

            Object argtup = pDataPop(self);
            Object callable = pDataPop(self);
            if (callable != null) {
                obj = callStarArgs(frame, callable, argtup);
            }
            assert obj != null;
            pDataPush(self, obj);
        }

        private void loadProto(VirtualFrame frame, PUnpickler self) {
            final ByteArrayView s = read(frame, self, 1);

            int i = s.getUnsigned(0);
            if (i <= PICKLE_PROTOCOL_HIGHEST) {
                self.proto = i;
                return;
            }

            throw raise(PythonBuiltinClassType.ValueError, ErrorMessages.UNSUPPORTED_PICKLE_PROTO, i);
        }

        private void loadFrame(VirtualFrame frame, PUnpickler self) {
            ByteArrayView s = read(frame, self, 8);

            int frameLen = calcBinSize(s, 8);
            if (frameLen < 0) {
                throw raise(PythonBuiltinClassType.OverflowError, ErrorMessages.S_EXCEEDS_MAX_SIZE_N_BYTES, "FRAME", Integer.MAX_VALUE);
            }

            s = read(frame, self, frameLen);

            // Rewind to start of frame
            self.nextReadIdx -= frameLen;
        }

        private void loadExtension(VirtualFrame frame, PythonContext ctx, PUnpickler self, int nbytes) {
            assert (nbytes == 1 || nbytes == 2 || nbytes == 4);
            // the nbytes bytes after the opcode
            ByteArrayView codebytes = read(frame, self, nbytes);
            // calc_binint returns long
            long code = calcBinInt(codebytes, nbytes);

            if (code <= 0) {
                // note that 0 is forbidden
                // Corrupt or hostile pickle.
                throw raise(PythonBuiltinClassType.UnpicklingError, ErrorMessages.EXT_CODE_LE_0);
            }

            // Look for the code in the cache.
            final PickleState st = getGlobalState(ctx.getCore());
            // the object to push
            Object obj = getDictItem(frame, st.extensionCache, code);
            if (obj != null) {
                // Bingo.
                pDataPush(self, obj);
            }

            // Look up the (moduleName, className) pair.
            // (moduleName, className)
            Object pair = getDictItem(frame, st.invertedRegistry, code);
            if (pair == null) {
                throw raise(PythonBuiltinClassType.ValueError, ErrorMessages.EXT_UNREGISTERED, code);
            }

            // Since the extension registry is manipulable via Python code, confirm that pair is
            // really a 2-tuple of strings.
            if (!(pair instanceof PTuple) || length(frame, pair) != 2) {
                throw raise(PythonBuiltinClassType.ValueError, ErrorMessages.INV_REG_NOT_2TUPLE, code);
            }

            Object moduleName = getItem(frame, pair, 0);
            if (!PGuards.isString(moduleName)) {
                throw raise(PythonBuiltinClassType.ValueError, ErrorMessages.INV_REG_NOT_2TUPLE, code);
            }

            Object className = getItem(frame, pair, 1);
            if (!PGuards.isString(className)) {
                throw raise(PythonBuiltinClassType.ValueError, ErrorMessages.INV_REG_NOT_2TUPLE, code);
            }

            // Load the object.
            obj = findClass(frame, ctx.getCore(), self, moduleName, className);

            // Cache code -> obj.
            // cpython expects a PyDict so this is a safe cast
            assert st.extensionCache instanceof PDict;
            setDictItem(frame, (PDict) st.extensionCache, code, obj);
            pDataPush(self, obj);
        }

        private void loadBool(PUnpickler self, Python3Core core, boolean bool) {
            pDataPush(self, bool ? core.getTrue() : core.getFalse());
        }

        @Specialization
        public Object load(VirtualFrame frame, PUnpickler self) {
            byte s;

            self.numMarks = 0;
            self.stack.mark = false;
            self.stack.fence = 0;
            self.proto = 0;
            if (self.stack.getSize() > 0) {
                self.stack.clear(0);
            }
            PythonContext ctx = PythonContext.get(this);

            while (true) {
                try {
                    s = read(frame, self);
                } catch (PException pe) {
                    pe.expectCached(PythonBuiltinClassType.UnpicklingError, ensureErrProfile());
                    throw raise(PythonBuiltinClassType.EOFError, ErrorMessages.RAN_OUT_OF_INPUT);
                }
                switch (s) {
                    case OPCODE_NONE:
                        loadNone(self);
                        continue;
                    case OPCODE_BININT:
                        loadBinInt(frame, self);
                        continue;
                    case OPCODE_BININT1:
                        loadBinInt1(frame, self);
                        continue;
                    case OPCODE_BININT2:
                        loadBinInt2(frame, self);
                        continue;
                    case OPCODE_INT:
                        loadInt(frame, self);
                        continue;
                    case OPCODE_LONG:
                        loadLong(frame, self);
                        continue;
                    case OPCODE_LONG1:
                        loadCountedLong(frame, self, 1);
                        continue;
                    case OPCODE_LONG4:
                        loadCountedLong(frame, self, 4);
                        continue;
                    case OPCODE_FLOAT:
                        loadFloat(frame, self);
                        continue;
                    case OPCODE_BINFLOAT:
                        loadBinFloat(frame, self);
                        continue;
                    case OPCODE_SHORT_BINBYTES:
                        loadCountedBinBytes(frame, self, 1);
                        continue;
                    case OPCODE_BINBYTES:
                        loadCountedBinBytes(frame, self, 4);
                        continue;
                    case OPCODE_BINBYTES8:
                        loadCountedBinBytes(frame, self, 8);
                        continue;
                    case OPCODE_BYTEARRAY8:
                        loadCountedByteArray(frame, self);
                        continue;
                    case OPCODE_NEXT_BUFFER:
                        loadNextBuffer(frame, self);
                        continue;
                    case OPCODE_READONLY_BUFFER:
                        loadReadOnlyBuffer(frame, self);
                        continue;
                    case OPCODE_SHORT_BINSTRING:
                        loadCountedBinString(frame, self, 1);
                        continue;
                    case OPCODE_BINSTRING:
                        loadCountedBinString(frame, self, 4);
                        continue;
                    case OPCODE_STRING:
                        loadString(frame, self);
                        continue;
                    case OPCODE_UNICODE:
                        loadUnicode(frame, self);
                        continue;
                    case OPCODE_SHORT_BINUNICODE:
                        loadBinCountedUnicode(frame, self, 1);
                        continue;
                    case OPCODE_BINUNICODE:
                        loadBinCountedUnicode(frame, self, 4);
                        continue;
                    case OPCODE_BINUNICODE8:
                        loadBinCountedUnicode(frame, self, 8);
                        continue;
                    case OPCODE_EMPTY_TUPLE:
                        loadCountedTuple(self, 0);
                        continue;
                    case OPCODE_TUPLE1:
                        loadCountedTuple(self, 1);
                        continue;
                    case OPCODE_TUPLE2:
                        loadCountedTuple(self, 2);
                        continue;
                    case OPCODE_TUPLE3:
                        loadCountedTuple(self, 3);
                        continue;
                    case OPCODE_TUPLE:
                        loadTuple(self);
                        continue;
                    case OPCODE_EMPTY_LIST:
                        loadEmptyList(self);
                        continue;
                    case OPCODE_LIST:
                        loadList(self);
                        continue;
                    case OPCODE_EMPTY_DICT:
                        loadEmptyDict(self);
                        continue;
                    case OPCODE_DICT:
                        loadDict(frame, self);
                        continue;
                    case OPCODE_EMPTY_SET:
                        loadEmptySet(self);
                        continue;
                    case OPCODE_ADDITEMS:
                        loadAddItems(frame, self);
                        continue;
                    case OPCODE_FROZENSET:
                        loadFrozenSet(frame, self);
                        continue;
                    case OPCODE_OBJ:
                        loadObj(frame, self);
                        continue;
                    case OPCODE_INST:
                        loadInst(frame, ctx, self);
                        continue;
                    case OPCODE_NEWOBJ:
                        loadNewObj(frame, self);
                        continue;
                    case OPCODE_NEWOBJ_EX:
                        loadNewObjEx(frame, self);
                        continue;
                    case OPCODE_GLOBAL:
                        loadGlobal(frame, ctx, self);
                        continue;
                    case OPCODE_STACK_GLOBAL:
                        loadStackGlobal(frame, ctx, self);
                        continue;
                    case OPCODE_APPEND:
                        loadAppend(frame, self);
                        continue;
                    case OPCODE_APPENDS:
                        loadAppends(frame, self);
                        continue;
                    case OPCODE_BUILD:
                        loadBuild(frame, self);
                        continue;
                    case OPCODE_DUP:
                        loadDup(self);
                        continue;
                    case OPCODE_BINGET:
                        loadBinGet(frame, self);
                        continue;
                    case OPCODE_LONG_BINGET:
                        loadLongBinGet(frame, self);
                        continue;
                    case OPCODE_GET:
                        loadGet(frame, self);
                        continue;
                    case OPCODE_MARK:
                        loadMark(self);
                        continue;
                    case OPCODE_BINPUT:
                        loadBinPut(frame, self);
                        continue;
                    case OPCODE_LONG_BINPUT:
                        loadLongBinPut(frame, self);
                        continue;
                    case OPCODE_PUT:
                        loadPut(frame, self);
                        continue;
                    case OPCODE_MEMOIZE:
                        loadMemoize(self);
                        continue;
                    case OPCODE_POP:
                        loadPop(self);
                        continue;
                    case OPCODE_POP_MARK:
                        loadPopMark(self);
                        continue;
                    case OPCODE_SETITEM:
                        loadSetItem(frame, self);
                        continue;
                    case OPCODE_SETITEMS:
                        loadSetItems(frame, self);
                        continue;
                    case OPCODE_PERSID:
                        loadPersId(frame, self);
                        continue;
                    case OPCODE_BINPERSID:
                        loadBinPersId(frame, self);
                        continue;
                    case OPCODE_REDUCE:
                        loadReduce(frame, self);
                        continue;
                    case OPCODE_PROTO:
                        loadProto(frame, self);
                        continue;
                    case OPCODE_FRAME:
                        loadFrame(frame, self);
                        continue;
                    case OPCODE_EXT1:
                        loadExtension(frame, ctx, self, 1);
                        continue;
                    case OPCODE_EXT2:
                        loadExtension(frame, ctx, self, 2);
                        continue;
                    case OPCODE_EXT4:
                        loadExtension(frame, ctx, self, 4);
                        continue;
                    case OPCODE_NEWTRUE:
                        loadBool(self, ctx.getCore(), true);
                        continue;
                    case OPCODE_NEWFALSE:
                        loadBool(self, ctx.getCore(), false);
                        continue;
                    case OPCODE_STOP:
                        break;
                    default:
                        if (0x20 <= s && s <= 0x7e && s != '\'' && s != '\\') {
                            throw raise(PythonBuiltinClassType.UnpicklingError, ErrorMessages.INVALID_LOAD_KEY_CHR, s);
                        } else {
                            throw raise(PythonBuiltinClassType.UnpicklingError, ErrorMessages.INVALID_LOAD_KEY_HEX, s);
                        }
                }

                // and we are done!
                break;
            }

            skipConsumed(frame, self);
            return pDataPop(self);
        }
    }
}
