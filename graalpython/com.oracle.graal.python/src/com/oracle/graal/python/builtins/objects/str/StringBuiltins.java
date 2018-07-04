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
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__LT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__MOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__RMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__STR__;
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
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions;
import com.oracle.graal.python.builtins.modules.BuiltinFunctionsFactory;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.SetItemNode;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.builtins.JoinInternalNode;
import com.oracle.graal.python.nodes.call.CallDispatchNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.subscript.GetItemNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.formatting.StringFormatter;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = PString.class)
public final class StringBuiltins extends PythonBuiltins {

    @Override
    protected List<com.oracle.truffle.api.dsl.NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return StringBuiltinsFactory.getFactories();
    }

    @Builtin(name = __STR__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    public abstract static class StrNode extends PythonUnaryBuiltinNode {
        @Specialization
        public Object str(PString self) {
            return self;
        }

        @Specialization
        public Object str(String self) {
            return self;
        }
    }

    @Builtin(name = __REPR__, fixedNumOfArguments = 1)
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

    @Builtin(name = __CONTAINS__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class ContainsNode extends PythonBinaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        boolean contains(String self, String other) {
            return self.contains(other);
        }
    }

    @Builtin(name = __EQ__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class EqNode extends PythonBinaryBuiltinNode {
        @Specialization
        public boolean eq(String self, String other) {
            return self.equals(other);
        }

        @SuppressWarnings("unused")
        @Fallback
        Object doGeneric(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __NE__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class NeNode extends PythonBinaryBuiltinNode {
        @Specialization
        public boolean ne(String self, String other) {
            return !self.equals(other);
        }

        @SuppressWarnings("unused")
        @Fallback
        Object doGeneric(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __LT__, fixedNumOfArguments = 2)
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
        Object doGeneric(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __LE__, fixedNumOfArguments = 2)
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
        Object doGeneric(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __GT__, fixedNumOfArguments = 2)
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
        Object doGeneric(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __GE__, fixedNumOfArguments = 2)
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
        Object doGeneric(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __ADD__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class AddNode extends PythonBinaryBuiltinNode {
        @Specialization
        String doSS(String self, String other) {
            return new StringBuilder(self.length() + other.length()).append(self).append(other).toString();
        }

        @Specialization(guards = "!isString(other)")
        String doSO(@SuppressWarnings("unused") String self, Object other) {
            throw raise(TypeError, "Can't convert '%p' object to str implicitly", other);
        }

        @SuppressWarnings("unused")
        @Fallback
        Object doGeneric(Object self, Object other) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __RADD__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    public abstract static class RAddNode extends AddNode {
    }

    // str.startswith(prefix[, start[, end]])
    @Builtin(name = "startswith", minNumOfArguments = 2, maxNumOfArguments = 5)
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
    @Builtin(name = "endswith", fixedNumOfArguments = 2)
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

    // str.rfind(str[, start[, end]])
    @Builtin(name = "rfind", minNumOfArguments = 2, maxNumOfArguments = 4)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class RFindNode extends PythonBuiltinNode {

        @Specialization
        @TruffleBoundary
        @SuppressWarnings("unused")
        public Object rfind(String self, String str, PNone start, PNone end) {
            return self.lastIndexOf(str);
        }

        @Specialization
        @TruffleBoundary
        public Object rfind(String self, String str, int start, @SuppressWarnings("unused") PNone end) {
            return self.substring(start).lastIndexOf(str);
        }

        @Specialization
        @TruffleBoundary
        public Object rfind(String self, String str, @SuppressWarnings("unused") PNone start, int end) {
            return self.substring(0, end).lastIndexOf(str);
        }

        @Specialization
        @TruffleBoundary
        public Object rfind(String self, String str, int start, int end) {
            return self.substring(start, end).lastIndexOf(str);
        }

        @Fallback
        @SuppressWarnings("unused")
        public Object endsWith(Object self, Object str, Object start, Object end) {
            CompilerDirectives.transferToInterpreter();
            throw new RuntimeException("rfind is not supported for " + self + " " + self.getClass() + " prefix " + str);
        }
    }

    // str.find(str[, start[, end]])
    @Builtin(name = "find", minNumOfArguments = 2, maxNumOfArguments = 4)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class FindNode extends PythonBuiltinNode {

        @Specialization
        @TruffleBoundary
        @SuppressWarnings("unused")
        public Object find(String self, String str, PNone start, PNone end) {
            return self.indexOf(str);
        }

        @Specialization
        public Object find(String self, String str, int start, @SuppressWarnings("unused") PNone end) {
            return findGeneric(self, str, start, -1);
        }

        @Specialization
        public Object find(String self, String str, @SuppressWarnings("unused") PNone start, int end) {
            return findGeneric(self, str, -1, end);
        }

        @Specialization
        public Object find(String self, String str, int start, int end) {
            return findGeneric(self, str, start, end);
        }

        @TruffleBoundary
        private static Object findWithBounds(String self, String str, int start, int end) {
            if (start != -1 && end != -1) {
                return self.substring(0, end).indexOf(str, start);
            } else if (start != -1) {
                return self.indexOf(str, start);
            } else {
                assert end != -1;
                return self.substring(0, end).indexOf(str);
            }
        }

        protected boolean isNumberOrNone(Object o) {
            return o instanceof Integer || o instanceof PInt || o instanceof PNone;
        }

        private static int getIntValue(Object o) {
            if (o instanceof Integer) {
                return (int) o;
            } else if (o instanceof PInt) {
                return ((PInt) o).intValueExact();
            } else if (o instanceof PNone) {
                return -1;
            }
            throw new IllegalArgumentException();
        }

        @Specialization(guards = {"isNumberOrNone(start)", "isNumberOrNone(end)"})
        public Object findGeneric(String self, String str, Object start, Object end) {
            int startInt = getIntValue(start);
            int endInt = getIntValue(end);
            return findWithBounds(self, str, startInt, endInt);
        }

        @Fallback
        @SuppressWarnings("unused")
        public Object findFail(Object self, Object str, Object start, Object end) {
            CompilerDirectives.transferToInterpreter();
            throw new RuntimeException("find is not supported for \"" + self + "\", " + self.getClass() + ", prefix " + str);
        }
    }

    // str.join(iterable)
    @Builtin(name = "join", fixedNumOfArguments = 2)
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
    @Builtin(name = "upper", fixedNumOfArguments = 1)
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
    @Builtin(name = "maketrans", fixedNumOfArguments = 2)
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
                setItemNode.execute(translation, translation.getDictStorage(), key, value);
            }

            return translation;
        }
    }

    // str.translate()
    @Builtin(name = "translate", fixedNumOfArguments = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
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
                        @Cached("create()") GetItemNode getItemNode,
                        @Cached("createBinaryProfile()") ConditionProfile errorProfile) {
            char[] translatedChars = new char[self.length()];

            for (int i = 0; i < self.length(); i++) {
                char original = self.charAt(i);
                Object translated = null;
                try {
                    translated = getItemNode.execute(table, (int) original);
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
    @Builtin(name = "lower", fixedNumOfArguments = 1)
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
    @Builtin(name = "capitalize", fixedNumOfArguments = 1)
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
    @Builtin(name = "rpartition", fixedNumOfArguments = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class RPartitionNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        public PList doSplit(String self, String sep) {
            int lastIndexOf = self.lastIndexOf(sep);
            PList list = factory().createList();
            if (lastIndexOf == -1) {
                list.append("");
                list.append("");
                list.append(self);
            } else {
                list.append(self.substring(0, lastIndexOf));
                list.append(sep);
                list.append(self.substring(lastIndexOf + sep.length()));
            }
            return list;
        }
    }

    // str.split
    @Builtin(name = "split", maxNumOfArguments = 3)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class SplitNode extends PythonBuiltinNode {

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
                list.append(s);
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
                list.append(s);
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
                list.append(s.substring(start, index));
                splits++;

                // Start next segment search at that point
                start = index;
            }

            return list;
        }
    }

    // str.split
    @Builtin(name = "rsplit", maxNumOfArguments = 3)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class RSplitNode extends PythonBuiltinNode {

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
            for (String s : strs)
                list.append(s);
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

                list.append(self.substring(idx + 1, end));
                end = idx;
                splits++;
                remainder = remainder.substring(0, end);
            }

            if (!remainder.isEmpty()) {
                list.append(remainder);
            }

            list.reverse();
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
                list.append(s.substring(index + 1, end + 1));
                splits++;

                // Start next segment search at that point
                end = index;
            }

            list.reverse();
            return list;
        }
    }

    // str.splitlines([keepends])
    @Builtin(name = "splitlines", minNumOfArguments = 1, maxNumOfArguments = 2)
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
    @Builtin(name = "replace", minNumOfArguments = 3, maxNumOfArguments = 4)
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

    @Builtin(name = "strip", minNumOfArguments = 1, maxNumOfArguments = 2)
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

    @Builtin(name = "rstrip", minNumOfArguments = 1, maxNumOfArguments = 2)
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

    @Builtin(name = "lstrip", minNumOfArguments = 1, maxNumOfArguments = 2)
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

    @Builtin(name = SpecialMethodNames.__LEN__, fixedNumOfArguments = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class LenNode extends PythonUnaryBuiltinNode {
        @Specialization
        public int len(String self) {
            return self.length();
        }
    }

    @Builtin(name = "index", minNumOfArguments = 2, maxNumOfArguments = 4)
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

    @Builtin(name = "encode", fixedNumOfArguments = 1, keywordArguments = {"encoding", "errors"})
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

    @Builtin(name = SpecialMethodNames.__MUL__, fixedNumOfArguments = 2)
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
        Object doGeneric(Object left, Object right) {
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

    @Builtin(name = __RMUL__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class RMulNode extends MulNode {
    }

    @Builtin(name = __MOD__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class ModNode extends PythonBinaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        Object doGeneric(String left, Object right,
                        @Cached("create()") CallDispatchNode callNode,
                        @Cached("create()") GetClassNode getClassNode,
                        @Cached("create()") LookupAttributeInMRONode.Dynamic lookupAttrNode) {
            return new StringFormatter(getCore(), left).format(right, callNode, (object, key) -> lookupAttrNode.execute(getClassNode.execute(object), key));
        }

        protected BuiltinFunctions.ReprNode createReprNode() {
            return BuiltinFunctionsFactory.ReprNodeFactory.create(null);
        }
    }

    @Builtin(name = "isalnum", fixedNumOfArguments = 1)
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

    @Builtin(name = "isalpha", fixedNumOfArguments = 1)
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

    @Builtin(name = "isdecimal", fixedNumOfArguments = 1)
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

    @Builtin(name = "isdigit", fixedNumOfArguments = 1)
    @GenerateNodeFactory
    abstract static class IsDigitNode extends IsDecimalNode {
    }

    @Builtin(name = "isidentifier", fixedNumOfArguments = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class IsIdentifierNode extends PythonUnaryBuiltinNode {
        @Specialization
        boolean doString(String self) {
            return getCore().getParser().isIdentifier(getCore(), self);
        }
    }

    @Builtin(name = "islower", fixedNumOfArguments = 1)
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

    @Builtin(name = "isnumeric", fixedNumOfArguments = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class IsNumericNode extends IsDecimalNode {
    }

    @Builtin(name = "isprintable", fixedNumOfArguments = 1)
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

    @Builtin(name = "isspace", fixedNumOfArguments = 1)
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

    @Builtin(name = "istitle", fixedNumOfArguments = 1)
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

    @Builtin(name = "isupper", fixedNumOfArguments = 1)
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
}
