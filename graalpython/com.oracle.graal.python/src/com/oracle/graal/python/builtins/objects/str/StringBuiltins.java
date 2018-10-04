/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
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
package com.oracle.graal.python.builtins.objects.str;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__ADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__CONTAINS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__MOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__STR__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.IndexError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.KeyError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.LookupError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.MemoryError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.UnicodeEncodeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.SetItemNode;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.iterator.PStringIterator;
import com.oracle.graal.python.builtins.objects.list.ListBuiltins.ListAppendNode;
import com.oracle.graal.python.builtins.objects.list.ListBuiltins.ListReverseNode;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.slice.PSlice.SliceInfo;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.builtins.JoinInternalNode;
import com.oracle.graal.python.nodes.call.CallDispatchNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CastToIndexNode;
import com.oracle.graal.python.nodes.util.CastToIntegerFromIndexNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.formatting.StringFormatter;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PString)
public final class StringBuiltins extends PythonBuiltins {

    @Override
    protected List<com.oracle.truffle.api.dsl.NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return StringBuiltinsFactory.getFactories();
    }

    @Builtin(name = __STR__, fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class StrNode extends PythonUnaryBuiltinNode {
        @Specialization
        public Object str(PString self) {
            return self.getValue();
        }

        @Specialization
        public Object str(String self) {
            return self;
        }
    }

    @Builtin(name = __REPR__, fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class ReprNode extends PythonUnaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        public Object repr(String self) {
            boolean hasSingleQuote = self.contains("'");
            boolean hasDoubleQuote = self.contains("\"");
            boolean useDoubleQuotes = hasSingleQuote && !hasDoubleQuote;

            StringBuilder str = new StringBuilder(self.length() + 2);
            str.append(useDoubleQuotes ? '"' : '\'');
            int offset = 0;
            while (offset < self.length()) {
                int codepoint = self.codePointAt(offset);
                switch (codepoint) {
                    case '\n':
                        str.append("\\n");
                        break;
                    case '\r':
                        str.append("\\r");
                        break;
                    case '\t':
                        str.append("\\t");
                        break;
                    case '\b':
                        str.append("\\b");
                        break;
                    case 7:
                        str.append("\\a");
                        break;
                    case '\f':
                        str.append("\\f");
                        break;
                    case 11:
                        str.append("\\v");
                        break;
                    case '\\':
                        str.append("\\\\");
                        break;
                    case '"':
                        if (useDoubleQuotes) {
                            str.append("\\\"");
                        } else {
                            str.append('\"');
                        }
                        break;
                    case '\'':
                        if (useDoubleQuotes) {
                            str.append('\'');
                        } else {
                            str.append("\\'");
                        }
                        break;
                    default:
                        if (codepoint < 32 || (codepoint >= 0x7f && codepoint <= 0xa0)) {
                            str.append("\\x" + String.format("%02x", codepoint));
                        } else if (codepoint > 0xd7fc) { // determined by experimentation
                            if (codepoint < 0x10000) {
                                str.append("\\u").append(String.format("%04x", codepoint));
                            } else {
                                str.append("\\U").append(String.format("%08x", codepoint));
                            }
                        } else {
                            str.appendCodePoint(codepoint);
                        }
                        break;
                }
                offset += Character.charCount(codepoint);
            }
            str.append(useDoubleQuotes ? '"' : '\'');
            return str.toString();
        }
    }

    @Builtin(name = __CONTAINS__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class ContainsNode extends PythonBinaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        boolean contains(String self, String other) {
            return self.contains(other);
        }

        @SuppressWarnings("unused")
        @Fallback
        Object doGeneric(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __EQ__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class EqNode extends PythonBinaryBuiltinNode {
        @Specialization
        public boolean eq(String self, String other) {
            return self.equals(other);
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __NE__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class NeNode extends PythonBinaryBuiltinNode {
        @Specialization
        public boolean ne(String self, String other) {
            return !self.equals(other);
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __LT__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class LtNode extends PythonBinaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        boolean lt(String self, String other) {
            return self.compareTo(other) < 0;
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __LE__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class LeNode extends PythonBinaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        boolean le(String self, String other) {
            return self.compareTo(other) <= 0;
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __GT__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class GtNode extends PythonBinaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        boolean le(String self, String other) {
            return self.compareTo(other) > 0;
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __GE__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class GeNode extends PythonBinaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        boolean le(String self, String other) {
            return self.compareTo(other) >= 0;
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __ADD__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class AddNode extends PythonBinaryBuiltinNode {

        protected final ConditionProfile leftProfile1 = ConditionProfile.createBinaryProfile();
        protected final ConditionProfile leftProfile2 = ConditionProfile.createBinaryProfile();
        protected final ConditionProfile rightProfile1 = ConditionProfile.createBinaryProfile();
        protected final ConditionProfile rightProfile2 = ConditionProfile.createBinaryProfile();

        @Specialization(guards = "!concatGuard(self, other)")
        String doSSSimple(String self, String other) {
            if (LazyString.length(self, leftProfile1, leftProfile2) == 0) {
                return other;
            }
            return self;
        }

        @Specialization(guards = "!concatGuard(self, other.getCharSequence())")
        Object doSSSimple(String self, PString other) {
            if (LazyString.length(self, leftProfile1, leftProfile2) == 0) {
                return other;
            }
            return self;
        }

        @Specialization(guards = "!concatGuard(self.getCharSequence(), other)")
        Object doSSSimple(PString self, String other) {
            if (LazyString.length(self.getCharSequence(), leftProfile1, leftProfile2) == 0) {
                return other;
            }
            return self;
        }

        @Specialization(guards = "!concatGuard(self.getCharSequence(), self.getCharSequence())")
        PString doSSSimple(PString self, PString other) {
            if (LazyString.length(self.getCharSequence(), leftProfile1, leftProfile2) == 0) {
                return other;
            }
            return self;
        }

        @Specialization(guards = "concatGuard(self.getCharSequence(), other)")
        Object doSS(PString self, String other,
                        @Cached("createBinaryProfile()") ConditionProfile shortStringAppend) {
            return doIt(self.getCharSequence(), other, shortStringAppend);
        }

        @Specialization(guards = "concatGuard(self, other)")
        Object doSS(String self, String other,
                        @Cached("createBinaryProfile()") ConditionProfile shortStringAppend) {
            return doIt(self, other, shortStringAppend);
        }

        @Specialization(guards = "concatGuard(self, other.getCharSequence())")
        Object doSS(String self, PString other,
                        @Cached("createBinaryProfile()") ConditionProfile shortStringAppend) {
            return doIt(self, other.getCharSequence(), shortStringAppend);
        }

        @Specialization(guards = "concatGuard(self.getCharSequence(), other.getCharSequence())")
        Object doSS(PString self, PString other,
                        @Cached("createBinaryProfile()") ConditionProfile shortStringAppend) {
            return doIt(self.getCharSequence(), other.getCharSequence(), shortStringAppend);
        }

        private Object doIt(CharSequence left, CharSequence right, ConditionProfile shortStringAppend) {
            if (LazyString.UseLazyStrings) {
                int leftLength = LazyString.length(left, leftProfile1, leftProfile2);
                int rightLength = LazyString.length(right, rightProfile1, rightProfile2);
                int resultLength = leftLength + rightLength;
                if (resultLength >= LazyString.MinLazyStringLength) {
                    if (shortStringAppend.profile(leftLength == 1 || rightLength == 1)) {
                        return factory().createString(LazyString.createCheckedShort(left, right, resultLength));
                    } else {
                        return factory().createString(LazyString.createChecked(left, right, resultLength));
                    }
                }
            }
            return stringConcat(left, right);
        }

        @TruffleBoundary
        private static String stringConcat(CharSequence left, CharSequence right) {
            return left.toString() + right.toString();
        }

        @Specialization(guards = "!isString(other)")
        String doSO(@SuppressWarnings("unused") String self, Object other) {
            throw raise(TypeError, "Can't convert '%p' object to str implicitly", other);
        }

        @Specialization(guards = "!isString(other)")
        String doSO(@SuppressWarnings("unused") PString self, Object other) {
            throw raise(TypeError, "Can't convert '%p' object to str implicitly", other);
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        protected boolean concatGuard(CharSequence left, CharSequence right) {
            int leftLength = LazyString.length(left, leftProfile1, leftProfile2);
            int rightLength = LazyString.length(right, rightProfile1, rightProfile2);
            return leftLength > 0 && rightLength > 0;
        }
    }

    @Builtin(name = __RADD__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class RAddNode extends AddNode {
    }

    // str.startswith(prefix[, start[, end]])
    @Builtin(name = "startswith", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 5)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class StartsWithNode extends PythonBuiltinNode {
        @Specialization
        boolean startsWith(String self, String prefix, int start, int end) {
            if (end - start < prefix.length()) {
                return false;
            } else if (self.startsWith(prefix, start)) {
                return true;
            }
            return false;
        }

        @Specialization
        boolean startsWith(String self, PTuple prefix, int start, int end) {
            for (Object o : prefix.getArray()) {
                if (o instanceof String) {
                    if (startsWith(self, (String) o, start, end)) {
                        return true;
                    }
                } else if (o instanceof PString) {
                    if (startsWith(self, ((PString) o).getValue(), start, end)) {
                        return true;
                    }
                }
            }
            return false;
        }

        @Specialization
        boolean startsWith(String self, String prefix, int start, @SuppressWarnings("unused") PNone end) {
            return startsWith(self, prefix, start, self.length());
        }

        @Specialization
        boolean startsWith(String self, String prefix, @SuppressWarnings("unused") PNone start, @SuppressWarnings("unused") PNone end) {
            return startsWith(self, prefix, 0, self.length());
        }

        @Specialization
        boolean startsWith(String self, PTuple prefix, int start, @SuppressWarnings("unused") PNone end) {
            return startsWith(self, prefix, start, self.length());
        }

        @Specialization
        boolean startsWith(String self, PTuple prefix, @SuppressWarnings("unused") PNone start, @SuppressWarnings("unused") PNone end) {
            return startsWith(self, prefix, 0, self.length());
        }
    }

    // str.endswith(suffix[, start[, end]])
    @Builtin(name = "endswith", fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class EndsWithNode extends PythonBuiltinNode {

        @Specialization
        public Object endsWith(String self, String prefix) {
            if (self.endsWith(prefix)) {
                return true;
            }

            return false;
        }

        @Specialization
        public Object endsWith(String self, PTuple prefix) {
            for (Object o : prefix.getArray()) {
                if (o instanceof String) {
                    if (self.endsWith((String) o)) {
                        return true;
                    }
                } else if (o instanceof PString) {
                    if (self.endsWith(((PString) o).getValue())) {
                        return true;
                    }
                }
            }
            return false;
        }

        @Fallback
        public Object endsWith(Object self, Object prefix) {
            throw new RuntimeException("endsWith is not supported for " + self + " " + self.getClass() + " prefix " + prefix);
        }
    }

    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class FindBaseNode extends PythonBuiltinNode {

        @Specialization
        Object find(String self, String str, @SuppressWarnings("unused") PNone start, @SuppressWarnings("unused") PNone end) {
            return find(self, str);
        }

        @Specialization
        Object find(String self, String str, long start, @SuppressWarnings("unused") PNone end) {
            return findGeneric(self, str, start, -1);
        }

        @Specialization
        Object find(String self, String str, @SuppressWarnings("unused") PNone start, long end) {
            return findGeneric(self, str, -1, end);
        }

        @Specialization
        Object find(String self, String str, long start, long end) {
            return findGeneric(self, str, start, end);
        }

        @Specialization(guards = {"isNumberOrNone(start)", "isNumberOrNone(end)"}, rewriteOn = ArithmeticException.class)
        Object findGeneric(String self, String str, Object start, Object end) throws ArithmeticException {
            int startInt = getIntValue(start);
            int endInt = getIntValue(end);
            return findWithBounds(self, str, startInt, endInt);
        }

        @Specialization(guards = {"isNumberOrNone(start)", "isNumberOrNone(end)"}, replaces = "findGeneric")
        Object findGenericOvf(String self, String str, Object start, Object end) {
            try {
                int startInt = getIntValue(start);
                int endInt = getIntValue(end);
                return findWithBounds(self, str, startInt, endInt);
            } catch (ArithmeticException e) {
                throw raise(ValueError, "cannot fit 'int' into an index-sized integer");
            }
        }

        @Fallback
        @SuppressWarnings("unused")
        Object findFail(Object self, Object str, Object start, Object end) {
            throw raise(TypeError, "must be str, not %p", str);
        }

        protected static boolean isNumberOrNone(Object o) {
            return o instanceof PInt || o instanceof PNone;
        }

        private static int getIntValue(Object o) throws ArithmeticException {
            if (o instanceof Integer) {
                return (int) o;
            } else if (o instanceof Long) {
                return PInt.intValueExact((long) o);
            } else if (o instanceof Boolean) {
                return PInt.intValue((boolean) o);
            } else if (o instanceof PInt) {
                return ((PInt) o).intValueExact();
            } else if (o instanceof PNone) {
                return -1;
            }
            throw new IllegalArgumentException();
        }

        @SuppressWarnings("unused")
        protected int find(String self, String findStr) {
            throw new AssertionError("must not be reached");
        }

        @SuppressWarnings("unused")
        protected int findWithBounds(String self, String str, int start, int end) {
            throw new AssertionError("must not be reached");
        }
    }

    // str.rfind(str[, start[, end]])
    @Builtin(name = "rfind", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    public abstract static class RFindNode extends FindBaseNode {

        @Override
        @TruffleBoundary
        protected int find(String self, String findStr) {
            return self.lastIndexOf(findStr);
        }

        @Override
        @TruffleBoundary
        protected int findWithBounds(String self, String str, int start, int end) {
            if (start != -1 && end != -1) {
                int idx = self.lastIndexOf(str, end - str.length() - 1);
                return idx >= start ? idx : -1;
            } else if (start != -1) {
                int idx = self.lastIndexOf(str);
                return idx >= start ? idx : -1;
            } else {
                assert end != -1;
                return self.lastIndexOf(str, end - str.length() - 1);
            }
        }
    }

    // str.find(str[, start[, end]])
    @Builtin(name = "find", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    public abstract static class FindNode extends FindBaseNode {

        @Override
        @TruffleBoundary
        protected int find(String self, String findStr) {
            return self.indexOf(findStr);
        }

        @Override
        @TruffleBoundary
        protected int findWithBounds(String self, String str, int start, int end) {
            if (start != -1 && end != -1) {
                int idx = self.indexOf(str, start);
                return idx + str.length() <= end ? idx : -1;
            } else if (start != -1) {
                return self.indexOf(str, start);
            } else {
                assert end != -1;
                int idx = self.indexOf(str);
                return idx + str.length() <= end ? idx : -1;
            }
        }
    }

    // str.join(iterable)
    @Builtin(name = "join", fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class JoinNode extends PythonBuiltinNode {

        @Child private JoinInternalNode joinInternalNode;
        @Child private GetClassNode getClassNode;

        @Specialization
        protected String join(Object self, Object iterable) {
            if (joinInternalNode == null || getClassNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                joinInternalNode = insert(JoinInternalNode.create());
                getClassNode = insert(GetClassNode.create());
            }
            return joinInternalNode.execute(self, iterable, getClassNode.execute(iterable));
        }
    }

    // str.upper()
    @Builtin(name = "upper", fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class UpperNode extends PythonBuiltinNode {

        @Specialization
        @TruffleBoundary
        public String upper(String self) {
            return self.toUpperCase();
        }
    }

    // static str.maketrans()
    @Builtin(name = "maketrans", fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class MakeTransNode extends PythonBuiltinNode {

        @Specialization
        public PDict maketrans(String from, String to,
                        @Cached("create()") SetItemNode setItemNode) {
            if (from.length() != to.length()) {
                throw new RuntimeException("maketrans arguments must have same length");
            }

            PDict translation = factory().createDict();
            for (int i = 0; i < from.length(); i++) {
                int key = from.charAt(i);
                int value = to.charAt(i);
                translation.setDictStorage(setItemNode.execute(translation.getDictStorage(), key, value));
            }

            return translation;
        }
    }

    // str.translate()
    @Builtin(name = "translate", fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    @ImportStatic(SpecialMethodNames.class)
    public abstract static class TranslateNode extends PythonBuiltinNode {
        @Specialization
        public String translate(String self, String table) {
            char[] translatedChars = new char[self.length()];

            for (int i = 0; i < self.length(); i++) {
                char original = self.charAt(i);
                char translation = table.charAt(original);
                translatedChars[i] = translation;
            }

            return new String(translatedChars);
        }

        @Specialization
        public String translate(String self, PDict table,
                        @Cached("create(__GETITEM__)") LookupAndCallBinaryNode getItemNode,
                        @Cached("createBinaryProfile()") ConditionProfile errorProfile) {
            char[] translatedChars = new char[self.length()];

            for (int i = 0; i < self.length(); i++) {
                char original = self.charAt(i);
                Object translated = null;
                try {
                    translated = getItemNode.executeObject(table, (int) original);
                } catch (PException e) {
                    e.expect(KeyError, getCore(), errorProfile);
                }
                int ord = translated == null ? original : (int) translated;
                translatedChars[i] = (char) ord;
            }

            return new String(translatedChars);
        }
    }

    // str.lower()
    @Builtin(name = "lower", fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class LowerNode extends PythonBuiltinNode {

        @Specialization
        @TruffleBoundary
        public String lower(String self) {
            return self.toLowerCase();
        }
    }

    // str.capitalize()
    @Builtin(name = "capitalize", fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class CapitalizeNode extends PythonBuiltinNode {

        @Specialization
        @TruffleBoundary
        public String lower(String self) {
            return self.substring(0, 1).toUpperCase() + self.substring(1);
        }
    }

    // str.rpartition
    @Builtin(name = "rpartition", fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class RPartitionNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        public PList doSplit(String self, String sep,
                        @Cached("create()") ListAppendNode appendNode) {
            int lastIndexOf = self.lastIndexOf(sep);
            PList list = factory().createList();
            if (lastIndexOf == -1) {
                appendNode.execute(list, "");
                appendNode.execute(list, "");
                appendNode.execute(list, self);
            } else {
                appendNode.execute(list, self.substring(0, lastIndexOf));
                appendNode.execute(list, sep);
                appendNode.execute(list, self.substring(lastIndexOf + sep.length()));
            }
            return list;
        }
    }

    protected abstract static class SplitBaseNode extends PythonTernaryBuiltinNode {

        @Child private ListAppendNode appendNode;
        @Child private ListReverseNode reverseNode;

        protected ListAppendNode getAppendNode() {
            if (appendNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                appendNode = insert(ListAppendNode.create());
            }
            return appendNode;
        }

        protected ListReverseNode getReverseNode() {
            if (reverseNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                reverseNode = insert(ListReverseNode.create());
            }
            return reverseNode;
        }

    }

    // str.split
    @Builtin(name = "split", maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class SplitNode extends SplitBaseNode {

        @SuppressWarnings("unused")
        @Specialization
        public PList doSplit(String self, PNone sep, PNone maxsplit) {
            return splitfields(self, -1);
        }

        @SuppressWarnings("unused")
        @TruffleBoundary
        @Specialization
        public PList doSplit(String self, String sep, PNone maxsplit) {
            PList list = factory().createList();
            String[] strs = self.split(Pattern.quote(sep));
            for (String s : strs) {
                getAppendNode().execute(list, s);
            }
            return list;
        }

        @Specialization
        @TruffleBoundary
        public PList doSplit(String self, String sep, int maxsplit) {
            PList list = factory().createList();
            // Python gives the maximum number of splits, Java wants the max number of resulting
            // parts
            String[] strs = self.split(Pattern.quote(sep), maxsplit + 1);
            for (String s : strs) {
                getAppendNode().execute(list, s);
            }
            return list;
        }

        @Specialization
        public PList doSplit(String self, @SuppressWarnings("unused") PNone sep, int maxsplit) {
            return splitfields(self, maxsplit + 1);
        }

        @Fallback
        public Object doSplit(@SuppressWarnings("unused") Object self, Object sep, @SuppressWarnings("unused") Object maxsplit) {
            throw raise(TypeError, " Can't convert %p object to str implicitly", sep);
        }

        // See {@link PyString}
        private PList splitfields(String s, int maxsplit) {
            /*
             * Result built here is a list of split parts, exactly as required for s.split(None,
             * maxsplit). If there are to be n splits, there will be n+1 elements in L.
             */
            PList list = factory().createList();
            int length = s.length();
            int start = 0;
            int splits = 0;
            int index;

            int maxsplit2 = maxsplit;
            if (maxsplit2 < 0) {
                // Make all possible splits: there can't be more than:
                maxsplit2 = length;
            }

            // start is always the first character not consumed into a piece on the list
            while (start < length) {

                // Find the next occurrence of non-whitespace
                while (start < length) {
                    if (!Character.isWhitespace(s.charAt(start))) {
                        // Break leaving start pointing at non-whitespace
                        break;
                    }
                    start++;
                }

                if (start >= length) {
                    // Only found whitespace so there is no next segment
                    break;

                } else if (splits >= maxsplit2) {
                    // The next segment is the last and contains all characters up to the end
                    index = length;

                } else {
                    // The next segment runs up to the next next whitespace or end
                    for (index = start; index < length; index++) {
                        if (Character.isWhitespace(s.charAt(index))) {
                            // Break leaving index pointing at whitespace
                            break;
                        }
                    }
                }

                // Make a piece from start up to index
                getAppendNode().execute(list, s.substring(start, index));
                splits++;

                // Start next segment search at that point
                start = index;
            }

            return list;
        }
    }

    // str.split
    @Builtin(name = "rsplit", maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class RSplitNode extends SplitBaseNode {

        @SuppressWarnings("unused")
        @Specialization
        public PList doSplit(String self, PNone sep, PNone maxsplit) {
            return rsplitfields(self, -1);
        }

        @SuppressWarnings("unused")
        @TruffleBoundary
        @Specialization
        public PList doSplit(String self, String sep, PNone maxsplit) {
            PList list = factory().createList();
            String[] strs = self.split(Pattern.quote(sep));
            for (String s : strs) {
                getAppendNode().execute(list, s);
            }
            return list;
        }

        @Specialization
        public PList doSplit(String self, String sep, int maxsplit) {
            PList list = factory().createList();
            int splits = 0;
            int end = self.length();
            String remainder = self;
            while (splits < maxsplit) {
                int idx = remainder.lastIndexOf(sep);

                if (idx < 0) {
                    break;
                }

                getAppendNode().execute(list, self.substring(idx + 1, end));
                end = idx;
                splits++;
                remainder = remainder.substring(0, end);
            }

            if (!remainder.isEmpty()) {
                getAppendNode().execute(list, remainder);
            }

            getReverseNode().execute(list);
            return list;
        }

        @Specialization
        public PList doSplit(String self, @SuppressWarnings("unused") PNone sep, int maxsplit) {
            return rsplitfields(self, maxsplit);
        }

        @TruffleBoundary
        private static boolean isWhitespace(int codePoint) {
            return Character.isWhitespace(codePoint);
        }

        // See {@link PyString}
        private PList rsplitfields(String s, int maxsplit) {
            /*
             * Result built here is a list of split parts, exactly as required for s.split(None,
             * maxsplit). If there are to be n splits, there will be n+1 elements in L.
             */
            PList list = factory().createList();
            int length = s.length();
            int end = length - 1;
            int splits = 0;
            int index;

            int maxsplit2 = maxsplit;
            if (maxsplit2 < 0) {
                // Make all possible splits: there can't be more than:
                maxsplit2 = length;
            }

            // start is always the first character not consumed into a piece on the list
            while (end > 0) {

                // Find the next occurrence of non-whitespace
                while (end >= 0) {
                    if (!isWhitespace(s.codePointAt(end))) {
                        // Break leaving start pointing at non-whitespace
                        break;
                    }
                    end--;
                }

                if (end == 0) {
                    // Only found whitespace so there is no next segment
                    break;

                } else if (splits >= maxsplit2) {
                    // The next segment is the last and contains all characters up to the end
                    index = 0;

                } else {
                    // The next segment runs up to the next next whitespace or end
                    for (index = end; index >= 0; index--) {
                        if (isWhitespace(s.codePointAt(index))) {
                            // Break leaving index pointing at whitespace
                            break;
                        }
                    }
                }

                // Make a piece from start up to index
                getAppendNode().execute(list, s.substring(index + 1, end + 1));
                splits++;

                // Start next segment search at that point
                end = index;
            }

            getReverseNode().execute(list);
            return list;
        }
    }

    // str.splitlines([keepends])
    @Builtin(name = "splitlines", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class SplitLinesNode extends PythonBuiltinNode {

        @Specialization
        @TruffleBoundary
        public PList split(String str, @SuppressWarnings("unused") PNone keepends) {
            String[] split = str.split("\n");
            return factory().createList(Arrays.copyOf(split, split.length, Object[].class));
        }
    }

    // str.replace
    @Builtin(name = "replace", minNumOfPositionalArgs = 3, maxNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class ReplaceNode extends PythonBuiltinNode {

        @SuppressWarnings("unused")
        @TruffleBoundary
        @Specialization
        public String doReplace(String self, String old, String with, PNone maxsplit) {
            return self.replace(old, with);
        }

        @TruffleBoundary
        @Specialization
        public String doReplace(String self, String old, String with, int maxsplit) {
            String newSelf = self;
            for (int i = 0; i < maxsplit; i++) {
                newSelf = newSelf.replaceFirst(old, with);
            }
            return newSelf;
        }
    }

    @Builtin(name = "strip", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class StripNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        String strip(String self, String chars) {
            return self.replaceAll("^[" + Pattern.quote(chars) + "]+", "").replaceAll("[" + Pattern.quote(chars) + "]+$", "");
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "isNoValue(chars)")
        String strip(String self, PNone chars) {
            return self.trim();
        }
    }

    @Builtin(name = "rstrip", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class RStripNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        String rstrip(String self, String chars) {
            return self.replaceAll("[" + Pattern.quote(chars) + "]+$", "");
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "isNoValue(chars)")
        @TruffleBoundary
        String rstrip(String self, PNone chars) {
            return self.replaceAll("\\s+$", "");
        }
    }

    @Builtin(name = "lstrip", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class LStripNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        String rstrip(String self, String chars) {
            return self.replaceAll("^[" + Pattern.quote(chars) + "]+", "");
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "isNoValue(chars)")
        @TruffleBoundary
        String rstrip(String self, PNone chars) {
            return self.replaceAll("^\\s+", "");
        }
    }

    @Builtin(name = SpecialMethodNames.__LEN__, fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class LenNode extends PythonUnaryBuiltinNode {
        @Specialization
        public int len(String self) {
            return self.length();
        }

        @Specialization
        public int len(PString self) {
            return self.len();
        }
    }

    @Builtin(name = "index", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class IndexNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        int index(String self, String substr, PNone start, PNone end,
                        @Cached("createBinaryProfile()") ConditionProfile errorProfile) {
            return indexOf(self, substr, 0, self.length(), errorProfile);
        }

        @SuppressWarnings("unused")
        @Specialization
        int index(String self, String substr, int start, PNone end,
                        @Cached("createBinaryProfile()") ConditionProfile errorProfile) {
            return indexOf(self, substr, start, self.length(), errorProfile);
        }

        @Specialization
        int index(String self, String substr, int start, int end,
                        @Cached("createBinaryProfile()") ConditionProfile errorProfile) {
            return indexOf(self, substr, start, end, errorProfile);
        }

        private int indexOf(String self, String substr, int start, int end, ConditionProfile errorProfile) {
            int idx = op(self, substr, start);
            if (errorProfile.profile(idx > end || idx < 0)) {
                throw raise(ValueError, "substring not found");
            } else {
                return idx;
            }
        }

        @TruffleBoundary
        private static int op(String self, String substr, int start) {
            return self.indexOf(substr, start);
        }
    }

    @Builtin(name = "encode", fixedNumOfPositionalArgs = 1, keywordArguments = {"encoding", "errors"})
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class EncodeNode extends PythonBuiltinNode {

        @Specialization
        Object encode(String self, @SuppressWarnings("unused") PNone encoding, @SuppressWarnings("unused") PNone errors) {
            return encodeString(self, "utf-8", "strict");
        }

        @Specialization
        Object encode(String self, String encoding, @SuppressWarnings("unused") PNone errors) {
            return encodeString(self, encoding, "strict");
        }

        @Specialization
        Object encode(String self, @SuppressWarnings("unused") PNone encoding, String errors) {
            return encodeString(self, "utf-8", errors);
        }

        @Specialization
        Object encode(String self, String encoding, String errors) {
            return encodeString(self, encoding, errors);
        }

        @TruffleBoundary
        private Object encodeString(String self, String encoding, String errors) {
            CodingErrorAction errorAction;
            switch (errors) {
                case "ignore":
                    errorAction = CodingErrorAction.IGNORE;
                    break;
                case "replace":
                    errorAction = CodingErrorAction.REPLACE;
                    break;
                default:
                    errorAction = CodingErrorAction.REPORT;
                    break;
            }

            try {
                Charset cs = Charset.forName(encoding);
                ByteBuffer encoded = cs.newEncoder().onMalformedInput(errorAction).onUnmappableCharacter(errorAction).encode(CharBuffer.wrap(self));
                int n = encoded.remaining();
                byte[] data = new byte[n];
                encoded.get(data);
                return factory().createBytes(data);
            } catch (IllegalArgumentException e) {
                throw raise(LookupError, "unknown encoding: %s", encoding);
            } catch (CharacterCodingException e) {
                throw raise(UnicodeEncodeError, "%s", e.getMessage());
            }
        }
    }

    @Builtin(name = SpecialMethodNames.__MUL__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class MulNode extends PythonBinaryBuiltinNode {

        @Specialization
        PString doPStringInt(PString left, boolean right) {
            if (right) {
                return left;
            } else {
                return factory().createString("");
            }
        }

        @Specialization
        String doStringInt(String left, boolean right) {
            if (right) {
                return left;
            } else {
                return "";
            }
        }

        @Specialization
        @TruffleBoundary
        String doStringInt(String left, long right) {
            if (right <= 0) {
                return "";
            }
            if (right > Integer.MAX_VALUE) {
                throw raise(OverflowError, "cannot fit 'int' into an index-sized integer");
            }
            try {
                StringBuilder str = new StringBuilder(Math.multiplyExact(left.length(), (int) right));
                for (int i = 0; i < right; i++) {
                    str.append(left);
                }
                return str.toString();
            } catch (OutOfMemoryError | ArithmeticException e) {
                throw raise(MemoryError);
            }
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        String doStringPInt(String left, PInt right) {
            return doStringInt(left, right.longValue());
        }

        @Specialization
        String doStringPIntOvf(String left, PInt right) {
            try {
                return doStringInt(left, right.longValue());
            } catch (ArithmeticException e) {
                throw raise(MemoryError);
            }
        }

        @SuppressWarnings("unused")
        @Fallback
        PNotImplemented doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __RMUL__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class RMulNode extends MulNode {
    }

    @Builtin(name = __MOD__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class ModNode extends PythonBinaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        Object doGeneric(String left, Object right,
                        @Cached("create()") CallDispatchNode callNode,
                        @Cached("create()") GetClassNode getClassNode,
                        @Cached("create()") LookupAttributeInMRONode.Dynamic lookupAttrNode,
                        @Cached("create(__GETITEM__)") LookupAndCallBinaryNode getItemNode) {
            return new StringFormatter(getCore(), left).format(right, callNode, (object, key) -> lookupAttrNode.execute(getClassNode.execute(object), key), getItemNode);
        }
    }

    @Builtin(name = "isalnum", fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class IsAlnumNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        boolean doString(String self) {
            if (self.length() == 0) {
                return false;
            }
            for (int i = 0; i < self.length(); i++) {
                if (!Character.isLetterOrDigit(self.codePointAt(i))) {
                    return false;
                }
            }
            return true;
        }
    }

    @Builtin(name = "isalpha", fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class IsAlphaNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        boolean doString(String self) {
            if (self.length() == 0) {
                return false;
            }
            for (int i = 0; i < self.length(); i++) {
                if (!Character.isLetter(self.codePointAt(i))) {
                    return false;
                }
            }
            return true;
        }
    }

    @Builtin(name = "isdecimal", fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class IsDecimalNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        boolean doString(String self) {
            if (self.length() == 0) {
                return false;
            }
            for (int i = 0; i < self.length(); i++) {
                if (!Character.isDigit(self.codePointAt(i))) {
                    return false;
                }
            }
            return true;
        }
    }

    @Builtin(name = "isdigit", fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsDigitNode extends IsDecimalNode {
    }

    @Builtin(name = "isidentifier", fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class IsIdentifierNode extends PythonUnaryBuiltinNode {
        @Specialization
        boolean doString(String self) {
            return getCore().getParser().isIdentifier(getCore(), self);
        }
    }

    @Builtin(name = "islower", fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class IsLowerNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        boolean doString(String self) {
            int spaces = 0;
            if (self.length() == 0) {
                return false;
            }
            for (int i = 0; i < self.length(); i++) {
                int codePoint = self.codePointAt(i);
                if (!Character.isLowerCase(codePoint)) {
                    if (Character.isWhitespace(codePoint)) {
                        spaces++;
                    } else {
                        return false;
                    }
                }
            }
            return spaces == 0 || self.length() > spaces;
        }
    }

    @Builtin(name = "isnumeric", fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class IsNumericNode extends IsDecimalNode {
    }

    @Builtin(name = "isprintable", fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class IsPrintableNode extends PythonUnaryBuiltinNode {
        @TruffleBoundary
        private static boolean isPrintableChar(int i) {
            if (Character.isISOControl(i)) {
                return false;
            }
            Character.UnicodeBlock block = Character.UnicodeBlock.of(i);
            return block != null && block != Character.UnicodeBlock.SPECIALS;
        }

        @Specialization
        @TruffleBoundary
        boolean doString(String self) {
            for (int i = 0; i < self.length(); i++) {
                if (!isPrintableChar(self.codePointAt(i))) {
                    return false;
                }
            }
            return true;
        }
    }

    @Builtin(name = "isspace", fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class IsSpaceNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        boolean doString(String self) {
            if (self.length() == 0) {
                return false;
            }
            for (int i = 0; i < self.length(); i++) {
                if (!Character.isWhitespace(self.codePointAt(i))) {
                    return false;
                }
            }
            return true;
        }
    }

    @Builtin(name = "istitle", fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class IsTitleNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        boolean doString(String self) {
            boolean hasContent = false;
            boolean expectLower = false;
            if (self.length() == 0) {
                return false;
            }
            for (int i = 0; i < self.length(); i++) {
                int codePoint = self.codePointAt(i);
                if (!expectLower) {
                    if (Character.isTitleCase(codePoint) || Character.isUpperCase(codePoint)) {
                        expectLower = true;
                        hasContent = true;
                    } else if (Character.isLowerCase(codePoint)) {
                        return false;
                    } else {
                        // uncased characters are allowed
                        continue;
                    }
                } else {
                    if (Character.isTitleCase(codePoint) || Character.isUpperCase(codePoint)) {
                        return false;
                    } else if (Character.isLowerCase(codePoint)) {
                        continue;
                    } else {
                        // we expect another title start after an uncased character
                        expectLower = false;
                    }
                }
            }
            return hasContent;
        }
    }

    @Builtin(name = "isupper", fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class IsUpperNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        boolean doString(String self) {
            int spaces = 0;
            if (self.length() == 0) {
                return false;
            }
            for (int i = 0; i < self.length(); i++) {
                int codePoint = self.codePointAt(i);
                if (!Character.isUpperCase(codePoint)) {
                    if (Character.isWhitespace(codePoint)) {
                        spaces++;
                    } else {
                        return false;
                    }
                }
            }
            return spaces == 0 || self.length() > spaces;

        }
    }

    @Builtin(name = "zfill", fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class ZFillNode extends PythonBinaryBuiltinNode {

        public abstract String executeObject(String self, Object x);

        @Specialization
        public String doString(String self, long width) {
            return zfill(self, (int) width);
        }

        @Specialization
        public String doString(String self, PInt width,
                        @Cached("create()") CastToIndexNode toIndexNode) {
            return zfill(self, toIndexNode.execute(width));
        }

        @Specialization
        public String doString(String self, Object width,
                        @Cached("create()") CastToIntegerFromIndexNode widthCast,
                        @Cached("create()") ZFillNode recursiveNode) {
            return recursiveNode.executeObject(self, widthCast.execute(width));
        }

        private static String zfill(String self, int width) {
            int len = self.length();
            if (len >= width) {
                return self;
            }
            char[] chars = new char[width];
            int nzeros = width - len;
            int i = 0;
            int sStart = 0;
            if (len > 0) {
                char start = self.charAt(0);
                if (start == '+' || start == '-') {
                    chars[0] = start;
                    i += 1;
                    nzeros++;
                    sStart = 1;
                }
            }
            for (; i < nzeros; i++) {
                chars[i] = '0';
            }
            self.getChars(sStart, len, chars, i);
            return new String(chars);
        }

        public static ZFillNode create() {
            return StringBuiltinsFactory.ZFillNodeFactory.create();
        }
    }

    @Builtin(name = "title", fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class TitleNode extends PythonUnaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        public String doTitle(String self) {
            boolean shouldBeLowerCase = false;
            boolean translated;
            StringBuilder converted = new StringBuilder();
            for (int offset = 0; offset < self.length();) {
                int ch = self.codePointAt(offset);
                translated = false;
                if (Character.isAlphabetic(ch)) {
                    if (shouldBeLowerCase) {
                        // Should be lower case
                        if (Character.isUpperCase(ch)) {
                            translated = true;
                            if (ch < 256) {
                                converted.append((char) Character.toLowerCase(ch));
                            } else {
                                String origPart = new String(Character.toChars(ch));
                                String changedPart = origPart.toLowerCase();
                                converted.append(changedPart);
                            }
                        }
                    } else {
                        // Should be upper case
                        if (Character.isLowerCase(ch)) {
                            translated = true;
                            if (ch < 256) {
                                converted.append((char) Character.toUpperCase(ch));
                            } else {
                                String origPart = new String(Character.toChars(ch));
                                String changedPart = origPart.toUpperCase();
                                if (origPart.length() < changedPart.length()) {
                                    // the original char was mapped to more chars ->
                                    // we need to make upper case just the first one
                                    changedPart = doTitle(changedPart);
                                }
                                converted.append(changedPart);
                            }
                        }
                    }
                    // And this was a letter
                    shouldBeLowerCase = true;
                } else {
                    // This was not a letter
                    shouldBeLowerCase = false;
                }
                if (!translated) {
                    if (ch < 256) {
                        converted.append((char) ch);
                    } else {
                        converted.append(Character.toChars(ch));
                    }
                }
                offset += Character.charCount(ch);
            }
            return converted.toString();
        }
    }

    @Builtin(name = "center", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class CenterNode extends PythonBuiltinNode {

        protected CastToIndexNode toIndexNode;

        private CastToIndexNode getCastToIndexNode() {
            if (toIndexNode == null) {
                toIndexNode = CastToIndexNode.createOverflow();
            }
            return toIndexNode;
        }

        @Specialization
        public String createDefault(String self, long width, @SuppressWarnings("unused") PNone fill) {
            return make(self, getCastToIndexNode().execute(width), " ");
        }

        @Specialization(guards = "fill.codePointCount(0, fill.length()) == 1")
        public String create(String self, long width, String fill) {
            return make(self, getCastToIndexNode().execute(width), fill);
        }

        @Specialization(guards = "fill.codePointCount(0, fill.length()) != 1")
        public String createError(String self, long width, String fill) {
            throw raise(TypeError, "The fill character must be exactly one character long");
        }

        @Specialization
        public String createDefault(String self, PInt width, @SuppressWarnings("unused") PNone fill) {
            return make(self, getCastToIndexNode().execute(width), " ");
        }

        @Specialization(guards = "fill.codePointCount(0, fill.length()) == 1")
        public String create(String self, PInt width, String fill) {
            return make(self, getCastToIndexNode().execute(width), fill);
        }

        @Specialization(guards = "fill.codePointCount(0, fill.length()) != 1")
        public String createError(String self, PInt width, String fill) {
            throw raise(TypeError, "The fill character must be exactly one character long");
        }

        protected String make(String self, int width, String fill) {
            int fillChar = parseCodePoint(fill);
            int len = width - self.length();
            if (len <= 0) {
                return self;
            }
            int half = len / 2;
            if (len % 2 > 0 && width % 2 > 0) {
                half += 1;
            }

            return padding(half, fillChar) + self + padding(len - half, fillChar);
        }

        protected static String padding(int len, int codePoint) {
            int[] result = new int[len];
            for (int i = 0; i < len; i++) {
                result[i] = codePoint;
            }
            return new String(result, 0, len);
        }

        @TruffleBoundary
        protected static int parseCodePoint(String fillchar) {
            if (fillchar == null) {
                return ' ';
            }
            return fillchar.codePointAt(0);
        }
    }

    @Builtin(name = "ljust", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class LJustNode extends CenterNode {

        @Override
        protected String make(String self, int width, String fill) {
            int fillChar = parseCodePoint(fill);
            int len = width - self.length();
            if (len <= 0) {
                return self;
            }
            return self + padding(len, fillChar);
        }

    }

    @Builtin(name = "rjust", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class RJustNode extends CenterNode {

        @Override
        protected String make(String self, int width, String fill) {
            int fillChar = parseCodePoint(fill);
            int len = width - self.length();
            if (len <= 0) {
                return self;
            }
            return padding(len, fillChar) + self;
        }

    }

    @Builtin(name = __GETITEM__, fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class StrGetItemNode extends PythonBinaryBuiltinNode {

        @Specialization
        public String doString(String primary, PSlice slice) {
            SliceInfo info = slice.computeIndices(primary.length());
            final int start = info.start;
            int stop = info.stop;
            int step = info.step;

            if (step > 0 && stop < start) {
                stop = start;
            }
            if (step == 1) {
                return getSubString(primary, start, stop);
            } else {
                char[] newChars = new char[info.length];
                int j = 0;
                for (int i = start; j < info.length; i += step) {
                    newChars[j++] = primary.charAt(i);
                }

                return new String(newChars);
            }
        }

        @Specialization
        public String doString(String primary, int idx) {
            try {
                int index = idx;

                if (idx < 0) {
                    index += primary.length();
                }

                return charAtToString(primary, index);
            } catch (StringIndexOutOfBoundsException | ArithmeticException e) {
                throw raise(IndexError, "IndexError: string index out of range");
            }
        }

        @Specialization
        public String doString(String primary, PInt idx) {
            return doString(primary, idx.intValue());
        }

        @SuppressWarnings("unused")
        @Fallback
        Object doGeneric(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        @TruffleBoundary
        private static String getSubString(String origin, int start, int stop) {
            char[] chars = new char[stop - start];
            origin.getChars(start, stop, chars, 0);
            return new String(chars);
        }

        @TruffleBoundary
        private static String charAtToString(String primary, int index) {
            char charactor = primary.charAt(index);
            return new String(new char[]{charactor});
        }
    }

    @Builtin(name = __ITER__, fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class IterNode extends PythonUnaryBuiltinNode {

        @Specialization
        PStringIterator doString(String self) {
            return factory().createStringIterator(self);
        }

    }
}
