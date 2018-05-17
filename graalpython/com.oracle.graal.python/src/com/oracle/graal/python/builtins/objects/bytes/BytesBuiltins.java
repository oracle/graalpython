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

import static com.oracle.graal.python.nodes.SpecialMethodNames.__ADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__CONTAINS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LEN__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__MUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__STR__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.nio.charset.CodingErrorAction;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.iterator.PSequenceIterator;
import com.oracle.graal.python.builtins.objects.set.PSet;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.nodes.PBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

@CoreFunctions(extendClasses = PBytes.class)
public class BytesBuiltins extends PythonBuiltins {

    public static CodingErrorAction toCodingErrorAction(String errors, PBaseNode n) {
        switch (errors) {
            case "strict":
                return CodingErrorAction.REPORT;
            case "ignore":
                return CodingErrorAction.IGNORE;
            case "replace":
                return CodingErrorAction.REPLACE;
        }
        throw n.raise(PythonErrorType.LookupError, "unknown error handler name '%s'", errors);
    }

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinNode>> getNodeFactories() {
        return BytesBuiltinsFactory.getFactories();
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
        public boolean eq(PBytes self, PByteArray other) {
            return self.equals(other);
        }

        @Specialization
        public boolean eq(PBytes self, PBytes other) {
            return self.equals(other);
        }

        @SuppressWarnings("unused")
        @Fallback
        public Object eq(Object self, Object other) {
            if (self instanceof PBytes) {
                return PNotImplemented.NOT_IMPLEMENTED;
            }
            throw raise(TypeError, "descriptor '__eq__' requires a 'bytes' object but received a '%p'", self);
        }
    }

    @Builtin(name = __LT__, fixedNumOfArguments = 2)
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
        public Object add(PBytes self, PIBytesLike other) {
            return self.concat(factory(), other);
        }

        @SuppressWarnings("unused")
        @Fallback
        public Object add(Object self, Object other) {
            throw raise(TypeError, "can't concat bytes to %p", other);
        }
    }

    @Builtin(name = __RADD__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class RAddNode extends PythonBuiltinNode {
        @Specialization
        public Object add(PBytes self, PIBytesLike other) {
            return self.concat(factory(), other);
        }

        @SuppressWarnings("unused")
        @Fallback
        public Object add(Object self, Object other) {
            throw raise(TypeError, "can't concat bytes to %p", other);
        }
    }

    @Builtin(name = __MUL__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class MulNode extends PythonBuiltinNode {
        @Specialization
        public Object mul(PBytes self, int times) {
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
        public Object str(PBytes self) {
            return self.toString();
        }
    }

    @Builtin(name = __REPR__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        public Object repr(PBytes self) {
            return self.toString();
        }
    }

    // bytes.join(iterable)
    @Builtin(name = "join", fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class JoinNode extends PythonBuiltinNode {
        @Specialization
        public PBytes join(PBytes bytes, PSequence seq) {
            return factory().createBytes(BytesUtils.join(getCore(), bytes.getInternalByteArray(), seq.getSequenceStorage().getInternalArray()));
        }

        @Specialization
        public PBytes join(PBytes bytes, PSet set) {
            Object[] values = new Object[set.size()];
            int i = 0;
            for (Object value : set.getDictStorage().keys()) {
                values[i++] = value;
            }
            return factory().createBytes(BytesUtils.join(getCore(), bytes.getInternalByteArray(), values));
        }

        @Fallback
        public PBytes join(Object self, Object arg) {
            throw new RuntimeException("invalid arguments type for join(): self " + self + ", arg " + arg);
        }
    }

    @Builtin(name = __LEN__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class LenNode extends PythonUnaryBuiltinNode {
        @Specialization
        public int len(PBytes self) {
            return self.len();
        }
    }

    @Builtin(name = __CONTAINS__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class ContainsNode extends PythonBinaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        boolean contains(PBytes self, PBytes other) {
            return new String(self.getInternalByteArray()).contains(new String(other.getInternalByteArray()));
        }

        @Specialization
        @TruffleBoundary
        boolean contains(PBytes self, PByteArray other) {
            return new String(self.getInternalByteArray()).contains(new String(other.getInternalByteArray()));
        }

        @Specialization(guards = "!isBytes(other)")
        boolean contains(@SuppressWarnings("unused") PBytes self, Object other) {
            throw raise(TypeError, "a bytes-like object is required, not '%p'", other);
        }
    }

    @Builtin(name = __ITER__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    abstract static class IterNode extends PythonBinaryBuiltinNode {
        @Specialization
        PSequenceIterator contains(PBytes self) {
            return factory().createSequenceIterator(self);
        }
    }

    @Builtin(name = "startswith", minNumOfArguments = 2, maxNumOfArguments = 4)
    @GenerateNodeFactory
    abstract static class StartsWithNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        @TruffleBoundary
        boolean startswith(PBytes self, String prefix, PNone start, PNone end) {
            return new String(self.getInternalByteArray()).startsWith(prefix);
        }

        @SuppressWarnings("unused")
        @Specialization
        boolean startswith(PBytes self, PIBytesLike prefix, PNone start, PNone end) {
            byte[] bytes = self.getInternalByteArray();
            byte[] other = prefix.getInternalByteArray();
            if (bytes.length < other.length) {
                return false;
            }
            for (int i = 0; i < other.length; i++) {
                if (bytes[i] != other[i]) {
                    return false;
                }
            }
            return true;
        }
    }

    @Builtin(name = "endswith", minNumOfArguments = 2, maxNumOfArguments = 4)
    @GenerateNodeFactory
    abstract static class EndsWithNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        @TruffleBoundary
        boolean startswith(PBytes self, String prefix, PNone start, PNone end) {
            return new String(self.getInternalByteArray()).endsWith(prefix);
        }

        @SuppressWarnings("unused")
        @Specialization
        boolean startswith(PBytes self, PIBytesLike prefix, PNone start, PNone end) {
            byte[] bytes = self.getInternalByteArray();
            byte[] other = prefix.getInternalByteArray();
            int offset = bytes.length - other.length;
            if (offset < 0) {
                return false;
            }
            for (int i = 0; i < other.length; i++) {
                if (bytes[i + offset] != other[i]) {
                    return false;
                }
            }
            return true;
        }
    }

    @Builtin(name = "strip", minNumOfArguments = 1, maxNumOfArguments = 2, keywordArguments = {"bytes"})
    @GenerateNodeFactory
    abstract static class StripNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        PBytes strip(PBytes self, @SuppressWarnings("unused") PNone bytes) {
            return factory().createBytes(new String(self.getInternalByteArray()).trim().getBytes());
        }
    }

    // str.find(bytes[, start[, end]])
    @Builtin(name = "find", minNumOfArguments = 2, maxNumOfArguments = 4)
    @GenerateNodeFactory
    abstract static class FindNode extends PythonBuiltinNode {
        @Specialization
        int find(PBytes self, int sub, @SuppressWarnings("unused") PNone start, @SuppressWarnings("unused") PNone end) {
            return find(self, sub, 0, self.len());
        }

        @Specialization
        int find(PBytes self, int sub, int start, @SuppressWarnings("unused") PNone end) {
            return find(self, sub, start, self.len());
        }

        @Specialization
        int find(PBytes self, int sub, int start, int ending) {
            return BytesUtils.find(self, sub, start, ending);
        }

        @Specialization
        int find(PBytes self, PIBytesLike sub, @SuppressWarnings("unused") PNone start, @SuppressWarnings("unused") PNone end) {
            return find(self, sub, 0, self.len());
        }

        @Specialization
        int find(PBytes self, PIBytesLike sub, int start, @SuppressWarnings("unused") PNone end) {
            return find(self, sub, start, self.len());
        }

        @Specialization
        int find(PBytes self, PIBytesLike sub, int start, int ending) {
            return BytesUtils.find(self, sub, start, ending);
        }
    }

    @Builtin(name = __GETITEM__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class GetitemNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object getitem(PBytes self, int idx) {
            return self.getItem(idx);
        }

        @Specialization
        Object getitem(PBytes self, PSlice slice) {
            return self.getSlice(factory(), slice);
        }
    }
}
