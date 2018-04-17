/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
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

import static com.oracle.graal.python.nodes.SpecialMethodNames.__ADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LEN__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__MUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__STR__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.set.PSet;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.control.GetIteratorNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

@CoreFunctions(extendClasses = PByteArray.class)
public class ByteArrayBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinNode>> getNodeFactories() {
        return ByteArrayBuiltinsFactory.getFactories();
    }

    @Builtin(name = __INIT__, takesVariableArguments = true, minNumOfArguments = 1, takesVariableKeywords = true)
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        public PNone init(Object self, Object args, Object kwargs) {
            // TODO: tfel: throw an error if we get additional arguments and the __new__
            // method was the same as object.__new__
            return PNone.NONE;
        }
    }

    @Builtin(name = __EQ__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class EqNode extends PythonBuiltinNode {
        @Specialization
        public boolean eq(PByteArray self, PByteArray other) {
            return self.equals(other);
        }

        @Specialization
        public boolean eq(PByteArray self, PBytes other) {
            return self.equals(other);
        }

        @SuppressWarnings("unused")
        @Fallback
        public Object eq(Object self, Object other) {
            if (self instanceof PByteArray) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            throw raise(TypeError, "descriptor '__eq__' requires a 'bytearray' object but received a '%p'", self);
        }
    }

    @Builtin(name = SpecialMethodNames.__LT__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class LtNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean contains(PSequence self, PSequence other) {
            return self.lessThan(other);
        }
    }

    @Builtin(name = __ADD__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class AddNode extends PythonBuiltinNode {
        @Specialization
        public Object add(PByteArray self, PIBytesLike other) {
            return self.concat(factory(), other);
        }

        @SuppressWarnings("unused")
        @Fallback
        public Object add(Object self, Object other) {
            throw raise(TypeError, "can't concat bytearray to %p", other);
        }
    }

    @Builtin(name = __RADD__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class RAddNode extends PythonBuiltinNode {
        @Specialization
        public Object add(PByteArray self, PIBytesLike other) {
            return self.concat(factory(), other);
        }

        @SuppressWarnings("unused")
        @Fallback
        public Object add(Object self, Object other) {
            throw raise(TypeError, "can't concat bytearray to %p", other);
        }
    }

    @Builtin(name = __MUL__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class MulNode extends PythonBuiltinNode {
        @Specialization
        public Object mul(PByteArray self, int times) {
            return self.__mul__(factory(), times);
        }

        @SuppressWarnings("unused")
        @Fallback
        public Object mul(Object self, Object other) {
            throw raise(TypeError, "can't multiply sequence by non-int of type '%p'", other);
        }
    }

    @Builtin(name = __RMUL__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class RMulNode extends MulNode {
    }

    @Builtin(name = __STR__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class StrNode extends PythonUnaryBuiltinNode {
        @Specialization
        public Object str(PByteArray self) {
            return self.toString();
        }
    }

    @Builtin(name = __REPR__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        public Object repr(PByteArray self) {
            return self.toString();
        }
    }

    // bytearray.append(x)
    @Builtin(name = "append", fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class ByteArrayAppendNode extends PythonBuiltinNode {

        @Specialization(guards = "isEmptyStorage(byteArray)")
        public PByteArray appendEmpty(PByteArray byteArray, Object arg) {
            byteArray.append(arg);
            return byteArray;
        }

        @Specialization(guards = "isByteStorage(byteArray)")
        public PByteArray appendInt(PByteArray byteArray, int arg) {
            ByteSequenceStorage store = (ByteSequenceStorage) byteArray.getSequenceStorage();
            store.appendInt(arg);
            return byteArray;
        }

        @Specialization(guards = "isByteStorage(byteArray)")
        public PByteArray appendInt(PByteArray byteArray, byte arg) {
            ByteSequenceStorage store = (ByteSequenceStorage) byteArray.getSequenceStorage();
            store.appendByte(arg);
            return byteArray;
        }
    }

    // bytearray.copy()
    @Builtin(name = "copy", fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class ByteArrayCopyNode extends PythonBuiltinNode {

        @Specialization
        public PByteArray copy(PByteArray byteArray) {
            return byteArray.copy();
        }
    }

    // bytearray.index(x)
    @Builtin(name = "index", fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class ByteArrayIndexNode extends PythonBuiltinNode {
        @Specialization
        public int index(PByteArray byteArray, Object arg) {
            return byteArray.index(arg);
        }
    }

    // bytearray.count(x)
    @Builtin(name = "count", fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class ByteArrayCountNode extends PythonBuiltinNode {

        @Specialization
        public int count(PByteArray byteArray, Object arg) {
            return byteArray.count(arg);
        }
    }

    // bytearray.reverse()
    @Builtin(name = "reverse", fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class ByteArrayReverseNode extends PythonBuiltinNode {

        @Specialization
        public PNone reverse(PByteArray byteArray) {
            byteArray.reverse();
            return PNone.NONE;
        }
    }

    // bytearray.clear()
    @Builtin(name = "clear", fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class ByteArrayClearNode extends PythonBuiltinNode {

        @Specialization
        public PNone clear(PByteArray byteArray) {
            byteArray.clear();
            return PNone.NONE;
        }
    }

    @Builtin(name = __ITER__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class ByteArrayIterNode extends PythonUnaryBuiltinNode {
        @Specialization
        public Object iter(PByteArray byteArray,
                        @Cached("create()") GetIteratorNode getIterator) {
            return getIterator.executeWith(byteArray);
        }
    }

    // bytearray.join(iterable)
    @Builtin(name = "join", fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class JoinNode extends PythonBuiltinNode {
        @Specialization
        public PByteArray join(PByteArray byteArray, PSequence seq) {
            return factory().createByteArray(byteArray.join(getCore(), seq.getSequenceStorage().getInternalArray()));
        }

        @Specialization
        public PByteArray join(PByteArray byteArray, PSet set) {
            Object[] values = new Object[set.size()];
            int i = 0;
            for (Object value : set.getDictStorage().keys()) {
                values[i++] = value;
            }
            return factory().createByteArray(byteArray.join(getCore(), values));
        }

        @Fallback
        public PByteArray join(Object self, Object arg) {
            throw new RuntimeException("invalid arguments type for join(): self " + self + ", arg " + arg);
        }
    }

    @Builtin(name = __LEN__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class LenNode extends PythonUnaryBuiltinNode {
        @Specialization
        public int len(PByteArray self) {
            return self.len();
        }
    }

    @Builtin(name = SpecialMethodNames.__CONTAINS__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class ContainsNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean contains(PSequence self, Object other) {
            return self.index(other) != -1;
        }
    }

    @Builtin(name = "find", minNumOfArguments = 2, maxNumOfArguments = 4)
    @GenerateNodeFactory
    abstract static class FindNode extends PythonBuiltinNode {
        @Specialization
        int find(PByteArray self, int sub, @SuppressWarnings("unused") PNone start, @SuppressWarnings("unused") PNone end) {
            return find(self, sub, 0, self.len());
        }

        @Specialization
        int find(PByteArray self, int sub, int start, @SuppressWarnings("unused") PNone end) {
            return find(self, sub, start, self.len());
        }

        @Specialization
        int find(PByteArray self, int sub, int start, int ending) {
            byte[] haystack = self.getInternalByteArray();
            int end = ending;
            if (start >= haystack.length) {
                return -1;
            } else if (end < 0) {
                end = end % haystack.length + 1;
            } else if (end > haystack.length) {
                end = haystack.length;
            }
            for (int i = start; i < haystack.length; i++) {
                if (haystack[i] == sub) {
                    return i;
                }
            }
            return -1;
        }

        @Specialization
        int find(PByteArray self, PIBytesLike sub, @SuppressWarnings("unused") PNone start, @SuppressWarnings("unused") PNone end) {
            return find(self, sub, 0, self.len());
        }

        @Specialization
        int find(PByteArray self, PIBytesLike sub, int start, @SuppressWarnings("unused") PNone end) {
            return find(self, sub, start, self.len());
        }

        @Specialization
        int find(PByteArray self, PIBytesLike sub, int start, int endIng) {
            byte[] haystack = self.getInternalByteArray();
            byte[] needle = sub.getInternalByteArray();
            int end = endIng;
            if (start >= haystack.length) {
                return -1;
            } else if (end < 0) {
                end = end % haystack.length + 1;
            } else if (end > haystack.length) {
                end = haystack.length;
            }
            end = end - needle.length;
            outer: for (int i = start; i < end; i++) {
                for (int j = 0; j < needle.length; j++) {
                    if (needle[j] != haystack[i + j]) {
                        continue outer;
                    }
                }
                return i;
            }
            return -1;
        }
    }

    @Builtin(name = "translate", minNumOfArguments = 2, maxNumOfArguments = 3)
    @GenerateNodeFactory
    abstract static class TranslateNode extends PythonBuiltinNode {
        @Specialization
        PByteArray translate(PByteArray self, PIBytesLike table, @SuppressWarnings("unused") PNone delete) {
            if (table.getInternalByteArray().length != 256) {
                throw raise(ValueError, "translation table must be 256 characters long");
            }
            byte[] newBytes = self.getInternalBytesArrayCopy();
            byte[] tableBytes = table.getInternalByteArray();
            for (int i = 0; i < newBytes.length; i++) {
                byte b = newBytes[i];
                newBytes[i] = tableBytes[b];
            }
            return factory().createByteArray(newBytes);
        }
    }

    @Builtin(name = __GETITEM__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class GetitemNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object getitem(PByteArray self, int idx) {
            return self.getItem(idx);
        }

        @Specialization
        Object getitem(PByteArray self, PSlice slice) {
            return self.getSlice(factory(), slice);
        }
    }
}
