/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.str;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.MemoryError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.util.Arrays;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.ToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.NativeCAPISymbols;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeObject;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.slice.PIntSlice;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.slice.PSlice.SliceInfo;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.control.GetNextNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.subscript.SliceLiteralNode;
import com.oracle.graal.python.nodes.subscript.SliceLiteralNode.CoerceToIntSlice;
import com.oracle.graal.python.nodes.subscript.SliceLiteralNode.ComputeIndices;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

public abstract class StringNodes {

    public static boolean isNativeCharSequence(PString x) {
        return x.getCharSequence() instanceof NativeCharSequence;
    }

    public static boolean isNativeMaterialized(PString seq) {
        return ((NativeCharSequence) seq.getCharSequence()).isMaterialized();
    }

    public static boolean isLazyCharSequence(PString x) {
        return x.getCharSequence() instanceof LazyString;
    }

    public static boolean isMaterialized(PString x) {
        return x.getCharSequence() instanceof String;
    }

    @GenerateUncached
    @ImportStatic(StringNodes.class)
    public abstract static class StringMaterializeNode extends Node {

        public abstract String execute(PString materialize);

        @Specialization(guards = {"isNativeCharSequence(x)", "isNativeMaterialized(x)"})
        static String doMaterializedNative(PString x) {
            return ((NativeCharSequence) x.getCharSequence()).materialize();
        }

        @Specialization(guards = {"isNativeCharSequence(x)"}, replaces = "doMaterializedNative")
        static String doNative(PString x,
                        @Cached PCallCapiFunction callCStringToStringNode) {
            // cast guaranteed by the guard
            NativeCharSequence nativeCharSequence = (NativeCharSequence) x.getCharSequence();
            String materialized = (String) callCStringToStringNode.call(NativeCAPISymbols.FUN_PY_TRUFFLE_CSTR_TO_STRING, nativeCharSequence.getPtr());
            x.setCharSequence(materialized);
            return materialized;
        }

        @Specialization(guards = "isLazyCharSequence(x)")
        static String doLazyString(PString x) {
            // cast guaranteed by the guard
            String materialized = ((LazyString) x.getCharSequence()).materialize();
            x.setCharSequence(materialized);
            return materialized;
        }

        @Specialization(guards = "isMaterialized(x)")
        static String doMaterialized(PString x) {
            // cast guaranteed by the guard
            return (String) x.getCharSequence();
        }
    }

    @GenerateUncached
    @ImportStatic(StringNodes.class)
    public abstract static class StringLenNode extends Node {

        public abstract int execute(Object str);

        @Specialization
        static int doString(String str) {
            return str.length();
        }

        @Specialization(guards = "isMaterialized(x)")
        static int doMaterialized(PString x) {
            // cast guaranteed by the guard
            return ((String) x.getCharSequence()).length();
        }

        @Specialization(guards = "isNativeCharSequence(x)")
        static int doNativeCharSequence(PString x, @Cached StringMaterializeNode materializeNode) {
            return materializeNode.execute(x).length();
        }

        @Specialization(guards = "isLazyCharSequence(x)")
        static int doLazyString(PString x) {
            // cast guaranteed by the guard
            return ((LazyString) x.getCharSequence()).length();
        }

        @Specialization(guards = {"isNativeCharSequence(x)", "isNativeMaterialized(x)"})
        static int nativeString(PString x) {
            return ((NativeCharSequence) x.getCharSequence()).length();
        }

        @Specialization(guards = {"isNativeCharSequence(x)", "!isNativeMaterialized(x)"}, replaces = "nativeString")
        static int nativeStringMat(PString x, @Cached PCallCapiFunction callCapi) {
            NativeCharSequence ncs = (NativeCharSequence) x.getCharSequence();
            ncs.materialize(callCapi);
            return ncs.length();
        }

        @Specialization(limit = "2")
        static int doNativeObject(PythonNativeObject x,
                        @CachedLibrary("x") PythonObjectLibrary plib,
                        @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PCallCapiFunction callNativeUnicodeAsStringNode,
                        @Cached ToSulongNode toSulongNode,
                        @Cached PRaiseNode raiseNode) {
            if (isSubtypeNode.execute(plib.getLazyPythonClass(x), PythonBuiltinClassType.PString)) {
                // read the native data
                Object result = callNativeUnicodeAsStringNode.call(NativeCAPISymbols.FUN_PY_UNICODE_GET_LENGTH, toSulongNode.execute(x));
                assert result instanceof Number;
                return intValue((Number) result);
            }
            // the object's type is not a subclass of 'str'
            throw raiseNode.raise(PythonBuiltinClassType.TypeError, ErrorMessages.BAD_ARG_TYPE_FOR_BUILTIN_OP);
        }

        @TruffleBoundary
        private static int intValue(Number result) {
            return result.intValue();
        }
    }

    public abstract static class CastToJavaStringCheckedNode extends Node {
        public final String cast(Object object, String errMsgFormat, Object... errMsgArgs) {
            return execute(object, errMsgFormat, errMsgArgs);
        }

        public abstract String execute(Object object, String errMsgFormat, Object[] errMsgArgs);

        @Specialization
        static String doConvert(Object self, String errMsgFormat, Object[] errMsgArgs,
                        @Cached CastToJavaStringNode castToJavaStringNode,
                        @Cached BranchProfile errorBranch,
                        @Cached PRaiseNode raiseNode) {
            try {
                return castToJavaStringNode.execute(self);
            } catch (CannotCastException e) {
                errorBranch.enter();
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, errMsgFormat, errMsgArgs);
            }
        }
    }

    @ImportStatic({PGuards.class, PythonOptions.class})
    public abstract static class JoinInternalNode extends PNodeWithContext {

        public static final String INVALID_SEQ_ITEM = "sequence item %d: expected str instance, %p found";

        public abstract String execute(VirtualFrame frame, String self, Object iterable);

        @Specialization
        static String doString(String self, String arg) {
            if (arg.isEmpty()) {
                return "";
            }
            return joinString(self, arg);
        }

        @TruffleBoundary
        private static String joinString(String self, String arg) {
            StringBuilder sb = new StringBuilder();
            char[] joinString = arg.toCharArray();
            assert joinString.length > 0;
            for (int i = 0; i < joinString.length - 1; i++) {
                sb.append(joinString[i]);
                sb.append(self);
            }
            sb.append(joinString[joinString.length - 1]);
            return sb.toString();
        }

        // This specialization is just for better interpreter performance.
        // IMPORTANT: only do this if the sequence is exactly list or tuple (not subclassed); for
        // semantics, see CPython's 'abstract.c' function 'PySequence_Fast'
        @Specialization(guards = "isExactlyListOrTuple(plib, tupleProfile, listProfile, sequence)")
        static String doPSequence(VirtualFrame frame, String self, PSequence sequence,
                        @SuppressWarnings("unused") @CachedLibrary(limit = "3") PythonObjectLibrary plib,
                        @Cached @SuppressWarnings("unused") IsBuiltinClassProfile tupleProfile,
                        @Cached @SuppressWarnings("unused") IsBuiltinClassProfile listProfile,
                        @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @Cached("createBinaryProfile()") ConditionProfile isEmptyProfile,
                        @Cached SequenceStorageNodes.GetItemNode getItemNode,
                        @Cached CastToJavaStringCheckedNode castToJavaStringNode,
                        @Cached PRaiseNode raise) {

            SequenceStorage storage = getSequenceStorageNode.execute(sequence);
            int len = lenNode.execute(storage);

            // shortcut
            if (isEmptyProfile.profile(len == 0)) {
                return "";
            }

            StringBuilder sb = new StringBuilder();
            int i = 0;

            try {
                // manually peel first iteration
                Object item = getItemNode.execute(frame, storage, i);
                append(sb, castToJavaStringNode.cast(item, INVALID_SEQ_ITEM, i, item));

                for (i = 1; i < len; i++) {
                    append(sb, self);
                    item = getItemNode.execute(frame, storage, i);
                    append(sb, castToJavaStringNode.cast(item, INVALID_SEQ_ITEM, i, item));
                }
                return toString(sb);
            } catch (OutOfMemoryError e) {
                throw raise.raise(MemoryError);
            }
        }

        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()")
        static String doGeneric(VirtualFrame frame, String string, Object iterable,
                        @Cached PRaiseNode raise,
                        @CachedLibrary("iterable") PythonObjectLibrary lib,
                        @Cached GetNextNode nextNode,
                        @Cached IsBuiltinClassProfile errorProfile0,
                        @Cached IsBuiltinClassProfile errorProfile1,
                        @Cached IsBuiltinClassProfile errorProfile2,
                        @Cached CastToJavaStringNode castStrNode) {

            try {
                Object iterator = lib.getIteratorWithFrame(iterable, frame);
                StringBuilder str = new StringBuilder();
                try {
                    append(str, checkItem(nextNode.execute(frame, iterator), 0, castStrNode, raise));
                } catch (PException e) {
                    e.expectStopIteration(errorProfile1);
                    return "";

                }
                int i = 1;
                while (true) {
                    Object value;
                    try {
                        value = nextNode.execute(frame, iterator);
                    } catch (PException e) {
                        e.expectStopIteration(errorProfile2);
                        return toString(str);
                    }
                    append(str, string);
                    append(str, checkItem(value, i++, castStrNode, raise));
                }
            } catch (PException e) {
                e.expect(PythonBuiltinClassType.TypeError, errorProfile0);
                throw raise.raise(PythonBuiltinClassType.TypeError, ErrorMessages.CAN_ONLY_JOIN_ITERABLE);
            } catch (OutOfMemoryError e) {
                throw raise.raise(MemoryError);
            }
        }

        private static String checkItem(Object item, int pos, CastToJavaStringNode castNode, PRaiseNode raise) {
            try {
                return castNode.execute(item);
            } catch (CannotCastException e) {
                throw raise.raise(TypeError, INVALID_SEQ_ITEM, pos, item);
            }
        }

        @TruffleBoundary(allowInlining = true)
        static StringBuilder append(StringBuilder sb, String o) {
            return sb.append(o);
        }

        @TruffleBoundary(allowInlining = true)
        static String toString(StringBuilder sb) {
            return sb.toString();
        }

        static boolean isExactlyListOrTuple(PythonObjectLibrary lib, IsBuiltinClassProfile tupleProfile, IsBuiltinClassProfile listProfile, PSequence sequence) {
            Object cls = lib.getLazyPythonClass(sequence);
            return tupleProfile.profileClass(cls, PythonBuiltinClassType.PTuple) || listProfile.profileClass(cls, PythonBuiltinClassType.PList);
        }
    }

    public abstract static class SpliceNode extends PNodeWithContext {

        public abstract char[] execute(char[] translatedChars, int i, Object translated);

        @Specialization
        static char[] doInt(char[] translatedChars, int i, int translated,
                        @Shared("raise") @Cached PRaiseNode raise,
                        @Cached BranchProfile ovf) {
            try {
                translatedChars[i] = PInt.charValueExact(translated);
                return translatedChars;
            } catch (OverflowException e) {
                ovf.enter();
                throw raiseError(raise);
            }
        }

        @Specialization
        static char[] doLong(char[] translatedChars, int i, long translated,
                        @Shared("raise") @Cached PRaiseNode raise,
                        @Cached BranchProfile ovf) {
            try {
                translatedChars[i] = PInt.charValueExact(translated);
                return translatedChars;
            } catch (OverflowException e) {
                ovf.enter();
                throw raiseError(raise);
            }
        }

        @Specialization
        static char[] doPInt(char[] translatedChars, int i, PInt translated,
                        @Shared("raise") @Cached PRaiseNode raise,
                        @Cached BranchProfile ovf) {
            double doubleValue = translated.doubleValue();
            char t = (char) doubleValue;
            if (t != doubleValue) {
                ovf.enter();
                throw raiseError(raise);
            }
            translatedChars[i] = t;
            return translatedChars;
        }

        @Specialization(guards = "translated.length() == 1")
        @TruffleBoundary
        static char[] doStringChar(char[] translatedChars, int i, String translated) {
            translatedChars[i] = translated.charAt(0);
            return translatedChars;
        }

        @Specialization(replaces = "doStringChar")
        @TruffleBoundary
        static char[] doString(char[] translatedChars, int i, String translated) {
            int transLen = translated.length();
            if (transLen == 1) {
                translatedChars[i] = translated.charAt(0);
            } else if (transLen == 0) {
                int len = translatedChars.length;
                return Arrays.copyOf(translatedChars, len - 1);
            } else {
                int len = translatedChars.length;
                char[] copy = Arrays.copyOf(translatedChars, len + transLen - 1);
                translated.getChars(0, transLen, copy, i);
                return copy;
            }
            return translatedChars;
        }

        @Specialization
        static char[] doObject(char[] translatedChars, int i, Object translated,
                        @Shared("raise") @Cached PRaiseNode raise,
                        @Cached BranchProfile ovf,
                        @Cached CastToJavaStringNode castToJavaStringNode) {

            if (translated instanceof Integer || translated instanceof Long) {
                return doLong(translatedChars, i, ((Number) translated).longValue(), raise, ovf);
            } else if (translated instanceof PInt) {
                return doPInt(translatedChars, i, (PInt) translated, raise, ovf);
            }

            try {
                String translatedStr = castToJavaStringNode.execute(translated);
                return doString(translatedChars, i, translatedStr);
            } catch (CannotCastException e) {
                throw raise.raise(PythonBuiltinClassType.TypeError, ErrorMessages.CHARACTER_MAPPING_MUST_RETURN_INT_NONE_OR_STR);
            }
        }

        private static PException raiseError(PRaiseNode raise) {
            return raise.raise(ValueError, ErrorMessages.CHARACTER_MAPPING_MUST_BE_IN_RANGE, PInt.toHexString(Character.MAX_CODE_POINT + 1));
        }

    }

    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class FindBaseNode extends PNodeWithContext {

        @Child private PythonObjectLibrary lib;
        @Child private PythonObjectFactory objectFactory;

        public abstract int execute(VirtualFrame frame, String self, Object substr, Object start, Object end);

        private PythonObjectLibrary getLibrary() {
            if (lib == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lib = insert(PythonObjectLibrary.getFactory().createDispatched(PythonOptions.getCallSiteInlineCacheMaxDepth()));
            }
            return lib;
        }

        private SliceInfo computeSlice(@SuppressWarnings("unused") VirtualFrame frame, int length, long start, long end) {
            PIntSlice tmpSlice = factory().createIntSlice(getLibrary().asSize(start, null), getLibrary().asSize(end, null), 1);
            // We need to distinguish between slice with length == 0 and a slice that's out of
            // bounds when matching empty strings
            if (start > length) {
                return null;
            }
            return tmpSlice.computeIndices(length);
        }

        @Specialization
        int findString(String self, String str, @SuppressWarnings("unused") PNone start, @SuppressWarnings("unused") PNone end) {
            return find(self, str);
        }

        @Specialization
        int findStringStart(VirtualFrame frame, String self, String str, long start, @SuppressWarnings("unused") PNone end) {
            int len = self.length();
            SliceInfo info = computeSlice(frame, len, start, len);
            if (info == null) {
                return -1;
            }
            return findWithBounds(self, str, info.start, info.stop);
        }

        @Specialization
        int findStringEnd(VirtualFrame frame, String self, String str, @SuppressWarnings("unused") PNone start, long end) {
            SliceInfo info = computeSlice(frame, self.length(), 0, end);
            if (info == null) {
                return -1;
            }
            return findWithBounds(self, str, info.start, info.stop);
        }

        @Specialization
        int findStringStartEnd(VirtualFrame frame, String self, String str, long start, long end) {
            SliceInfo info = computeSlice(frame, self.length(), start, end);
            if (info == null) {
                return -1;
            }
            return findWithBounds(self, str, info.start, info.stop);
        }

        @Specialization
        int findStringGeneric(VirtualFrame frame, String self, String str, Object start, Object end,
                        @Cached SliceLiteralNode sliceNode,
                        @Cached CoerceToIntSlice cast,
                        @Cached ComputeIndices compute) {
            PSlice slice = sliceNode.execute(frame, PGuards.isPNone(start) ? PNone.NONE : start, PGuards.isPNone(end) ? PNone.NONE : end, 1);
            SliceInfo info = compute.execute(cast.execute(slice), self.length());
            if (info == null) {
                return -1;
            }
            return findWithBounds(self, str, info.start, info.stop);
        }

        @Specialization
        int findGeneric(VirtualFrame frame, String self, Object sub, Object start, Object end,
                        @Cached CastToJavaStringCheckedNode castSubNode,
                        @Cached SliceLiteralNode slice,
                        @Cached CoerceToIntSlice cast,
                        @Cached ComputeIndices compute) {
            String subStr = castSubNode.cast(sub, ErrorMessages.MUST_BE_STR_NOT_P, sub);
            return findStringGeneric(frame, self, subStr, start, end, slice, cast, compute);
        }

        @SuppressWarnings("unused")
        protected int find(String self, String findStr) {
            CompilerAsserts.neverPartOfCompilation();
            throw new IllegalStateException("should not be reached");
        }

        @SuppressWarnings("unused")
        protected int findWithBounds(String self, String str, int start, int end) {
            CompilerAsserts.neverPartOfCompilation();
            throw new IllegalStateException("should not be reached");
        }

        protected final PythonObjectFactory factory() {
            if (objectFactory == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                objectFactory = insert(PythonObjectFactory.create());
            }
            return objectFactory;
        }

    }

    public abstract static class FindNode extends FindBaseNode {
        @Override
        @TruffleBoundary
        protected int find(String self, String findStr) {
            return self.indexOf(findStr);
        }

        @Override
        protected int findWithBounds(String self, String str, int start, int end) {
            if (end - start < str.length()) {
                return -1;
            }
            int idx = PString.indexOf(self, str, start);
            return idx + str.length() <= end ? idx : -1;
        }

        public static FindNode create() {
            return StringNodesFactory.FindNodeGen.create();
        }
    }

    public abstract static class RFindNode extends FindBaseNode {

        @Override
        @TruffleBoundary
        protected int find(String self, String findStr) {
            return self.lastIndexOf(findStr);
        }

        @Override
        protected int findWithBounds(String self, String str, int start, int end) {
            if (end - start < str.length()) {
                return -1;
            }
            int idx = PString.lastIndexOf(self, str, end - str.length());
            return idx >= start ? idx : -1;
        }

        public static RFindNode create() {
            return StringNodesFactory.RFindNodeGen.create();
        }
    }
}
