/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.nodes.ErrorMessages.INVALID_SEQ_ITEM;
import static com.oracle.graal.python.nodes.PGuards.cannotBeOverridden;
import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_STRING;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.MemoryError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsbCapacity;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.BytesUtils;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.ReadUnicodeArrayNode;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.str.StringNodesFactory.StringMaterializeNodeGen;
import com.oracle.graal.python.lib.GetNextNode;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromDynamicObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToDynamicObjectNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.InlinedGetClassNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.Encoding;
import com.oracle.truffle.api.strings.TruffleStringBuilder;
import com.oracle.truffle.api.strings.TruffleStringIterator;

public abstract class StringNodes {

    @GenerateUncached
    @ImportStatic(StringNodes.class)
    public abstract static class StringMaterializeNode extends Node {

        public static TruffleString executeUncached(PString s) {
            return StringMaterializeNodeGen.getUncached().execute(s);
        }

        public abstract TruffleString execute(PString materialize);

        @Specialization(guards = {"x.isNativeCharSequence()", "x.isNativeMaterialized()"})
        static TruffleString doMaterializedNative(PString x) {
            return x.getNativeCharSequence().getMaterialized();
        }

        @Specialization(guards = {"x.isNativeCharSequence()", "!x.isMaterialized()"}, replaces = "doMaterializedNative")
        static TruffleString doNative(PString x,
                        @Cached ReadUnicodeArrayNode readArray,
                        @Cached TruffleString.FromIntArrayUTF32Node fromArray) {
            NativeCharSequence sequence = x.getNativeCharSequence();
            assert TS_ENCODING == Encoding.UTF_32 : "needs switch_encoding otherwise";
            TruffleString materialized = fromArray.execute(readArray.execute(sequence.getPtr(), sequence.getElements(), sequence.getElementSize()));
            x.setMaterialized(materialized);
            return materialized;
        }

        @Specialization(guards = "x.isMaterialized()")
        static TruffleString doMaterialized(PString x) {
            return x.getMaterialized();
        }

        @NeverDefault
        public static StringMaterializeNode create() {
            return StringNodesFactory.StringMaterializeNodeGen.create();
        }
    }

    @GenerateUncached
    @ImportStatic(StringNodes.class)
    public abstract static class StringLenNode extends PNodeWithContext {

        public abstract int execute(Object str);

        @Specialization
        static int doString(TruffleString str,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode) {
            return codePointLengthNode.execute(str, TS_ENCODING);
        }

        @Specialization(guards = "x.isMaterialized()")
        static int doMaterialized(PString x,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode) {
            return doString(x.getMaterialized(), codePointLengthNode);
        }

        @Specialization(guards = "x.isNativeCharSequence()")
        static int doNativeCharSequence(PString x,
                        @Cached StringMaterializeNode materializeNode,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode) {
            return doString(materializeNode.execute(x), codePointLengthNode);
        }

        @Specialization(guards = {"x.isNativeCharSequence()", "x.isNativeMaterialized()"})
        static int nativeString(PString x,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode) {
            return doString(x.getNativeCharSequence().getMaterialized(), codePointLengthNode);
        }

        @Specialization(guards = {"x.isNativeCharSequence()", "!x.isNativeMaterialized()"}, replaces = "nativeString", limit = "3")
        static int nativeStringMat(@SuppressWarnings("unused") PString x,
                        @Bind("x.getNativeCharSequence()") NativeCharSequence ncs,
                        @CachedLibrary("ncs") InteropLibrary lib,
                        @Cached CastToJavaIntExactNode castToJavaIntNode) {
            return ncs.length(lib, castToJavaIntNode);
        }

        @Specialization
        static int doNativeObject(PythonNativeObject x,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedGetClassNode getClassNode,
                        @Cached IsSubtypeNode isSubtypeNode,
                        @Cached PCallCapiFunction callNativeUnicodeAsStringNode,
                        @Cached PythonToNativeNode toSulongNode,
                        @Cached PRaiseNode raiseNode) {
            if (isSubtypeNode.execute(getClassNode.execute(inliningTarget, x), PythonBuiltinClassType.PString)) {
                // read the native data
                Object result = callNativeUnicodeAsStringNode.call(NativeCAPISymbol.FUN_PY_UNICODE_GET_LENGTH, toSulongNode.execute(x));
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

    @GenerateUncached
    public abstract static class CastToJavaStringCheckedNode extends PNodeWithContext {
        public final String cast(Object object, TruffleString errMsgFormat, Object... errMsgArgs) {
            return executeInternal(object, errMsgFormat, errMsgArgs);
        }

        protected abstract String executeInternal(Object object, TruffleString errMsgFormat, Object[] errMsgArgs);

        @Specialization
        static String doConvert(TruffleString self, @SuppressWarnings("unused") TruffleString errMsgFormat, @SuppressWarnings("unused") Object[] errMsgArgs,
                        @Cached TruffleString.ToJavaStringNode toJavaStringNode) {
            return toJavaStringNode.execute(self);
        }

        @Specialization(guards = "!isTruffleString(self)")
        static String doConvert(Object self, TruffleString errMsgFormat, Object[] errMsgArgs,
                        @Cached CastToJavaStringNode castToJavaStringNode,
                        @Cached PRaiseNode raiseNode) {
            try {
                return castToJavaStringNode.execute(self);
            } catch (CannotCastException e) {
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, errMsgFormat, errMsgArgs);
            }
        }
    }

    @GenerateUncached
    public abstract static class CastToTruffleStringCheckedNode extends PNodeWithContext {
        public final TruffleString cast(Object object, TruffleString errMsgFormat, Object... errMsgArgs) {
            return execute(object, errMsgFormat, errMsgArgs);
        }

        public abstract TruffleString execute(Object object, TruffleString errMsgFormat, Object[] errMsgArgs);

        @Specialization
        static TruffleString doTruffleString(TruffleString self, @SuppressWarnings("unused") TruffleString errMsgFormat, @SuppressWarnings("unused") Object[] errMsgArgs) {
            return self;
        }

        @Specialization(guards = "!isTruffleString(self)")
        static TruffleString doConvert(Object self, TruffleString errMsgFormat, Object[] errMsgArgs,
                        @Cached CastToTruffleStringNode castToTruffleStringNode,
                        @Cached PRaiseNode raiseNode) {
            try {
                return castToTruffleStringNode.execute(self);
            } catch (CannotCastException e) {
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, errMsgFormat, errMsgArgs);
            }
        }
    }

    @ImportStatic({PGuards.class, PythonOptions.class})
    public abstract static class JoinInternalNode extends PNodeWithContext {
        public abstract TruffleString execute(VirtualFrame frame, TruffleString self, Object iterable);

        @Specialization
        static TruffleString doString(TruffleString self, TruffleString arg,
                        @Cached TruffleString.CreateCodePointIteratorNode createCodePointIteratorNode,
                        @Cached TruffleStringIterator.NextNode nextNode,
                        @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Cached TruffleStringBuilder.AppendCodePointNode appendCodePointNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode) {
            if (arg.isEmpty()) {
                return T_EMPTY_STRING;
            }
            TruffleStringBuilder sb = TruffleStringBuilder.create(TS_ENCODING);
            TruffleStringIterator it = createCodePointIteratorNode.execute(arg, TS_ENCODING);
            assert it.hasNext();
            appendCodePointNode.execute(sb, nextNode.execute(it), 1, true);
            while (it.hasNext()) {
                appendStringNode.execute(sb, self);
                appendCodePointNode.execute(sb, nextNode.execute(it), 1, true);
            }
            return toStringNode.execute(sb);
        }

        // This specialization is just for better interpreter performance.
        // IMPORTANT: only do this if the sequence is exactly list or tuple (not subclassed); for
        // semantics, see CPython's 'abstract.c' function 'PySequence_Fast'
        @Specialization(guards = "isExactlyListOrTuple(inliningTarget, getClassNode, sequence)", limit = "1")
        static TruffleString doPSequence(TruffleString self, PSequence sequence,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Cached InlinedGetClassNode getClassNode,
                        @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Cached InlinedConditionProfile isEmptyProfile,
                        @Cached InlinedConditionProfile isSingleItemProfile,
                        @Cached SequenceStorageNodes.GetItemNode getItemNode,
                        @Cached CastToTruffleStringNode castToStringNode,
                        @Cached PRaiseNode raise,
                        @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode) {

            SequenceStorage storage = getSequenceStorageNode.execute(sequence);
            int len = storage.length();

            // shortcut
            if (isEmptyProfile.profile(inliningTarget, len == 0)) {
                return T_EMPTY_STRING;
            }

            int i = 0;

            // manually peel first iteration
            Object item = getItemNode.execute(storage, i);
            try {
                // shortcut
                if (isSingleItemProfile.profile(inliningTarget, len == 1)) {
                    return castToStringNode.execute(item);
                }
                TruffleStringBuilder sb = TruffleStringBuilder.create(TS_ENCODING);
                appendStringNode.execute(sb, castToStringNode.execute(item));

                for (i = 1; i < len; i++) {
                    appendStringNode.execute(sb, self);
                    item = getItemNode.execute(storage, i);
                    appendStringNode.execute(sb, castToStringNode.execute(item));
                }
                return toStringNode.execute(sb);
            } catch (OutOfMemoryError e) {
                throw raise.raise(MemoryError);
            } catch (CannotCastException e) {
                throw raise.raise(PythonBuiltinClassType.TypeError, INVALID_SEQ_ITEM, i, item);
            }
        }

        @Specialization
        static TruffleString doGeneric(VirtualFrame frame, TruffleString string, Object iterable,
                        @Bind("this") Node inliningTarget,
                        @Cached PRaiseNode raise,
                        @Cached PyObjectGetIter getIter,
                        @Cached GetNextNode nextNode,
                        @Cached IsBuiltinObjectProfile errorProfile0,
                        @Cached IsBuiltinObjectProfile errorProfile1,
                        @Cached IsBuiltinObjectProfile errorProfile2,
                        @Cached CastToTruffleStringNode castStrNode,
                        @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode) {
            Object iterator;
            try {
                iterator = getIter.execute(frame, iterable);
            } catch (PException e) {
                e.expect(inliningTarget, PythonBuiltinClassType.TypeError, errorProfile0);
                throw raise.raise(PythonBuiltinClassType.TypeError, ErrorMessages.CAN_ONLY_JOIN_ITERABLE);
            }
            try {
                TruffleStringBuilder sb = TruffleStringBuilder.create(TS_ENCODING);
                try {
                    appendStringNode.execute(sb, checkItem(nextNode.execute(frame, iterator), 0, castStrNode, raise));
                } catch (PException e) {
                    e.expectStopIteration(inliningTarget, errorProfile1);
                    return T_EMPTY_STRING;
                }
                int i = 1;
                while (true) {
                    Object value;
                    try {
                        value = nextNode.execute(frame, iterator);
                    } catch (PException e) {
                        e.expectStopIteration(inliningTarget, errorProfile2);
                        return toStringNode.execute(sb);
                    }
                    appendStringNode.execute(sb, string);
                    appendStringNode.execute(sb, checkItem(value, i++, castStrNode, raise));
                }
            } catch (OutOfMemoryError e) {
                throw raise.raise(MemoryError);
            }
        }

        private static TruffleString checkItem(Object item, int pos, CastToTruffleStringNode castNode, PRaiseNode raise) {
            try {
                return castNode.execute(item);
            } catch (CannotCastException e) {
                throw raise.raise(TypeError, INVALID_SEQ_ITEM, pos, item);
            }
        }

        static boolean isExactlyListOrTuple(Node inliningTarget, InlinedGetClassNode getClassNode, PSequence sequence) {
            Object clazz = getClassNode.execute(inliningTarget, sequence);
            return clazz == PythonBuiltinClassType.PList || clazz == PythonBuiltinClassType.PTuple;
        }
    }

    @ImportStatic(PGuards.class)
    public abstract static class SpliceNode extends PNodeWithContext {

        public abstract void execute(TruffleStringBuilder sb, Object translated);

        @Specialization(guards = "isNone(none)")
        @SuppressWarnings("unused")
        static void doNone(TruffleStringBuilder sb, PNone none) {
        }

        @Specialization
        static void doInt(TruffleStringBuilder sb, int translated,
                        @Shared("raise") @Cached PRaiseNode raise,
                        @Cached TruffleStringBuilder.AppendCodePointNode appendCodePointNode) {
            if (Character.isValidCodePoint(translated)) {
                appendCodePointNode.execute(sb, translated, 1, true);
            } else {
                throw raise.raise(ValueError, ErrorMessages.INVALID_UNICODE_CODE_POINT);
            }
        }

        @Specialization
        static void doLong(TruffleStringBuilder sb, long translated,
                        @Shared("raise") @Cached PRaiseNode raise,
                        @Cached TruffleStringBuilder.AppendCodePointNode appendCodePointNode) {
            try {
                doInt(sb, PInt.intValueExact(translated), raise, appendCodePointNode);
            } catch (OverflowException e) {
                throw raiseError(raise);
            }
        }

        @Specialization
        static void doPInt(TruffleStringBuilder sb, PInt translated,
                        @Shared("raise") @Cached PRaiseNode raise,
                        @Cached TruffleStringBuilder.AppendCodePointNode appendCodePointNode) {
            try {
                doInt(sb, translated.intValueExact(), raise, appendCodePointNode);
            } catch (OverflowException e) {
                throw raiseError(raise);
            }
        }

        @Specialization
        static void doString(TruffleStringBuilder sb, TruffleString translated,
                        @Cached TruffleStringBuilder.AppendStringNode appendStringNode) {
            appendStringNode.execute(sb, translated);
        }

        @Specialization(guards = {"!isInteger(translated)", "!isPInt(translated)", "!isNone(translated)"})
        static void doObject(TruffleStringBuilder sb, Object translated,
                        @Shared("raise") @Cached PRaiseNode raise,
                        @Cached CastToTruffleStringNode castToStringNode,
                        @Cached TruffleStringBuilder.AppendStringNode appendStringNode) {

            try {
                TruffleString translatedStr = castToStringNode.execute(translated);
                doString(sb, translatedStr, appendStringNode);
            } catch (CannotCastException e) {
                throw raise.raise(PythonBuiltinClassType.TypeError, ErrorMessages.CHARACTER_MAPPING_MUST_RETURN_INT_NONE_OR_STR);
            }
        }

        private static PException raiseError(PRaiseNode raise) {
            return raise.raise(ValueError, ErrorMessages.CHARACTER_MAPPING_MUST_BE_IN_RANGE, PInt.toHexString(Character.MAX_CODE_POINT + 1));
        }
    }

    @ImportStatic(PGuards.class)
    @GenerateUncached
    public abstract static class InternStringNode extends Node {
        public abstract PString execute(Object string);

        @Specialization
        static PString doString(TruffleString string,
                        @Shared("writeNode") @Cached WriteAttributeToDynamicObjectNode writeNode,
                        @Cached PythonObjectFactory factory) {
            final PString interned = factory.createString(string);
            writeNode.execute(interned, PString.INTERNED, true);
            return interned;
        }

        @Specialization
        static PString doPString(PString string,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedGetClassNode getClassNode,
                        @Shared("writeNode") @Cached WriteAttributeToDynamicObjectNode writeNode) {
            if (cannotBeOverridden(getClassNode.execute(inliningTarget, string))) {
                writeNode.execute(string, PString.INTERNED, true);
                return string;
            }
            return null;
        }

        @Fallback
        static PString doOthers(@SuppressWarnings("unused") Object string) {
            return null;
        }

        public static InternStringNode getUncached() {
            return StringNodesFactory.InternStringNodeGen.getUncached();
        }
    }

    @ImportStatic(PGuards.class)
    @GenerateUncached
    public abstract static class IsInternedStringNode extends Node {
        public abstract boolean execute(PString string);

        @Specialization
        static boolean doIt(PString string,
                        @Cached ReadAttributeFromDynamicObjectNode readNode) {
            final Object isInterned = readNode.execute(string, PString.INTERNED);
            return isInterned instanceof Boolean && (boolean) isInterned;
        }
    }

    @GenerateUncached
    public abstract static class StringReplaceNode extends Node {
        public abstract TruffleString execute(TruffleString str, TruffleString old, TruffleString with, int maxCount);

        @Specialization
        static TruffleString doReplace(TruffleString self, TruffleString old, TruffleString with, int maxCountArg,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached TruffleString.IndexOfStringNode indexOfStringNode,
                        @Cached TruffleString.SubstringNode substringNode,
                        @Cached TruffleString.CreateCodePointIteratorNode createCodePointIteratorNode,
                        @Cached TruffleStringIterator.NextNode nextNode,
                        @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Cached TruffleStringBuilder.AppendCodePointNode appendCodePointNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode) {
            int maxCount = maxCountArg < 0 ? Integer.MAX_VALUE : maxCountArg;
            if (maxCount == 0) {
                return self;
            }
            if (old.isEmpty()) {
                if (self.isEmpty() && maxCountArg >= 0) {
                    // corner case: "".replace("","x", <m>) returns "x" for m >=0
                    return with;
                }
                int selfLen = self.byteLength(TS_ENCODING);
                int selfCpLen = codePointLengthNode.execute(self, TS_ENCODING);
                TruffleStringBuilder sb = TruffleStringBuilder.create(TS_ENCODING, selfLen + with.byteLength(TS_ENCODING) * Math.min(maxCount, selfCpLen + 1));
                int replacements = 0;
                TruffleStringIterator it = createCodePointIteratorNode.execute(self, TS_ENCODING);
                int i = 0;
                while (it.hasNext()) {
                    if (replacements++ >= maxCount) {
                        TruffleString rest = substringNode.execute(self, i, selfCpLen - i, TS_ENCODING, true);
                        appendStringNode.execute(sb, rest);
                        return toStringNode.execute(sb);
                    }
                    appendStringNode.execute(sb, with);
                    int codePoint = nextNode.execute(it);
                    appendCodePointNode.execute(sb, codePoint, 1, true);
                    ++i;
                }
                if (replacements < maxCount) {
                    appendStringNode.execute(sb, with);
                }
                return toStringNode.execute(sb);
            } else {
                int selfCpLen = codePointLengthNode.execute(self, TS_ENCODING);
                int oldCpLen = codePointLengthNode.execute(old, TS_ENCODING);
                int idx = indexOfStringNode.execute(self, old, 0, selfCpLen, TS_ENCODING);
                if (idx < 0) {
                    return self;
                } else {
                    TruffleStringBuilder sb = TruffleStringBuilder.create(TS_ENCODING);
                    int start = 0;
                    int replacements = 0;
                    do {
                        TruffleString substr = substringNode.execute(self, start, idx - start, TS_ENCODING, true);
                        appendStringNode.execute(sb, substr);
                        appendStringNode.execute(sb, with);
                        start = idx + oldCpLen;
                        if (++replacements >= maxCount || start >= selfCpLen) {
                            break;
                        }
                        idx = indexOfStringNode.execute(self, old, start, selfCpLen, TS_ENCODING);
                    } while (idx >= 0);
                    TruffleString rest = substringNode.execute(self, start, selfCpLen - start, TS_ENCODING, true);
                    appendStringNode.execute(sb, rest);
                    return toStringNode.execute(sb);
                }
            }
        }

        public static StringReplaceNode getUncached() {
            return StringNodesFactory.StringReplaceNodeGen.getUncached();
        }
    }

    @GenerateUncached
    public abstract static class StringReprNode extends Node {
        public abstract TruffleString execute(TruffleString self);

        @Specialization
        static TruffleString doString(TruffleString self,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached TruffleString.IndexOfCodePointNode indexOfCodePointNode,
                        @Cached TruffleString.CreateCodePointIteratorNode createCodePointIteratorNode,
                        @Cached TruffleStringIterator.NextNode nextNode,
                        @Cached TruffleStringBuilder.AppendCodePointNode appendCodePointNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode) {
            int selfLen = codePointLengthNode.execute(self, TS_ENCODING);
            boolean hasSingleQuote = indexOfCodePointNode.execute(self, '\'', 0, selfLen, TS_ENCODING) >= 0;
            boolean hasDoubleQuote = indexOfCodePointNode.execute(self, '"', 0, selfLen, TS_ENCODING) >= 0;
            boolean useDoubleQuotes = hasSingleQuote && !hasDoubleQuote;

            TruffleStringBuilder sb = TruffleStringBuilder.create(TS_ENCODING, tsbCapacity(selfLen + 2));
            TruffleStringIterator it = createCodePointIteratorNode.execute(self, TS_ENCODING);
            byte[] buffer = new byte[12];
            appendCodePointNode.execute(sb, useDoubleQuotes ? '"' : '\'', 1, true);
            while (it.hasNext()) {
                int codepoint = nextNode.execute(it);
                switch (codepoint) {
                    case '"':
                        if (useDoubleQuotes) {
                            appendCodePointNode.execute(sb, '\\', 1, true);
                        }
                        appendCodePointNode.execute(sb, '"', 1, true);
                        break;
                    case '\'':
                        if (!useDoubleQuotes) {
                            appendCodePointNode.execute(sb, '\\', 1, true);
                        }
                        appendCodePointNode.execute(sb, '\'', 1, true);
                        break;
                    case '\\':
                        appendCodePointNode.execute(sb, '\\', 1, true);
                        appendCodePointNode.execute(sb, '\\', 1, true);
                        break;
                    default:
                        if (StringUtils.isPrintable(codepoint)) {
                            appendCodePointNode.execute(sb, codepoint, 1, true);
                        } else {
                            int len = BytesUtils.unicodeEscape(codepoint, 0, buffer);
                            for (int i = 0; i < len; i++) {
                                appendCodePointNode.execute(sb, buffer[i], 1, true);
                            }
                        }
                        break;
                }
            }
            appendCodePointNode.execute(sb, (byte) (useDoubleQuotes ? '"' : '\''), 1, true);
            return toStringNode.execute(sb);
        }

        public static StringReprNode getUncached() {
            return StringNodesFactory.StringReprNodeGen.getUncached();
        }
    }
}
