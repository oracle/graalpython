/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.re;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.modules.TRegexUtil;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.array.PArray;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.cext.common.NativePointer;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.mmap.PMMap;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.lib.PyTupleGetItem;
import com.oracle.graal.python.lib.PyTupleSizeNode;
import com.oracle.graal.python.lib.PyUnicodeCheckNode;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.statement.AbstractImportNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING_BINARY;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

public class PatternNodes {

    @GenerateInline
    @GenerateCached(false)
    public abstract static class SearchNode extends Node {

        public static final int PYTHON_METHOD_COUNT = PythonMethod.PYTHON_METHOD_COUNT;

        public abstract Object execute(VirtualFrame frame, Node inliningTarget, PPattern self, Object stringObject, int pos, int endPos, PythonMethod method, boolean mustAdvance);

        @Specialization(guards = {"isSingleContext()", "pattern == cachedPattern", "method == cachedMethod", "mustAdvance == cachedMustAdvance", "!cache.isLocaleSensitive()"}, limit = "1")
        @SuppressWarnings({"truffle-static-method", "unused"})
        public static Object getSliceCached(VirtualFrame frame, Node inliningTarget, PPattern pattern, Object stringObject, int pos, int endPos, PythonMethod method, boolean mustAdvance,
                        @CachedLibrary(limit = "1") @Shared InteropLibrary interop,
                        @Cached(value = "pattern", weak = true) PPattern cachedPattern,
                        @Cached("method") PythonMethod cachedMethod,
                        @Cached("mustAdvance") boolean cachedMustAdvance,
                        @Cached(value = "pattern.cache", weak = true) TRegexCache cache,
                        @Cached(inline = false) @Shared TRegexCompileNode tRegexCompileNode,
                        @Cached(value = "tRegexCompileNode.execute(frame, pattern.cache, method, mustAdvance)") Object regexObject,
                        @Cached @Shared RECheckInputTypeNode reCheckInputTypeNode,
                        @Cached @Shared PyObjectSizeNode objectSizeNode,
                        @Cached(inline = false) @Shared TRegexCallExec tRegexCallExecNode,
                        @Cached @Shared TRegexUtil.InteropReadMemberNode interopReadMemberNode,
                        @Cached @Shared MatchNodes.NewNode newMatchNode,
                        @Cached @Shared InlinedConditionProfile matchProfile) {
            reCheckInputTypeNode.execute(frame, stringObject, pattern.cache.isBinary());

            if (pos >= endPos) {
                return PNone.NONE;
            }

            int length = objectSizeNode.execute(frame, inliningTarget, stringObject);
            if (pos < 0) {
                pos = 0;
            } else if (pos > length) {
                pos = length;
            }
            if (endPos < 0) {
                endPos = 0;
            } else if (endPos > length) {
                endPos = length;
            }

            if (interop.isNull(regexObject)) {
                PatternBuiltins.bailoutUnsupportedRegex(pattern.cache);
            }

            Object regexResult = tRegexCallExecNode.execute(frame, regexObject, stringObject, pos, endPos);
            boolean isMatch = TRegexUtil.TRegexResultAccessor.isMatch(regexResult, inliningTarget, interopReadMemberNode);

            if (matchProfile.profile(inliningTarget, isMatch)) {
                return newMatchNode.execute(frame, inliningTarget, pattern, regexResult, stringObject, pos, endPos);
            } else {
                return PNone.NONE;
            }
        }

        @Specialization(guards = {"tRegexCompileNode.execute(frame, pattern.cache, method, mustAdvance) == regexObject", "method == cachedMethod",
                        "mustAdvance == cachedMustAdvance", "!cache.isLocaleSensitive()"}, limit = "1", replaces = "getSliceCached")
        @SuppressWarnings("truffle-static-method")
        public static Object getSliceCachedRegex(VirtualFrame frame, Node inliningTarget, PPattern pattern, Object stringObject, int pos, int endPos, PythonMethod method, boolean mustAdvance,
                        @CachedLibrary(limit = "1") @Shared InteropLibrary interop,
                        @Cached("method") PythonMethod cachedMethod,
                        @Cached("mustAdvance") @SuppressWarnings("unused") boolean cachedMustAdvance,
                        @Cached(inline = false) @Shared TRegexCompileNode tRegexCompileNode,
                        @Cached(value = "pattern.cache", weak = true) TRegexCache cache,
                        @Cached(value = "tRegexCompileNode.execute(frame, pattern.cache, method, mustAdvance)") Object regexObject,
                        @Cached @Shared RECheckInputTypeNode reCheckInputTypeNode,
                        @Cached @Shared PyObjectSizeNode objectSizeNode,
                        @Cached(inline = false) @Shared TRegexCallExec tRegexCallExecNode,
                        @Cached @Shared TRegexUtil.InteropReadMemberNode interopReadMemberNode,
                        @Cached @Shared MatchNodes.NewNode newMatchNode,
                        @Cached @Shared InlinedConditionProfile matchProfile) {
            return getSliceCached(frame, inliningTarget, pattern, stringObject, pos, endPos, method, mustAdvance,
                            interop, pattern, method, mustAdvance, cache, tRegexCompileNode, regexObject, reCheckInputTypeNode, objectSizeNode, tRegexCallExecNode, interopReadMemberNode, newMatchNode,
                            matchProfile);
        }

        @Specialization(guards = "method == cachedMethod", limit = "PYTHON_METHOD_COUNT", replaces = {"getSliceCached", "getSliceCachedRegex"})
        @SuppressWarnings("truffle-static-method")
        @ReportPolymorphism.Megamorphic
        public static Object getSliceDynamic(VirtualFrame frame, Node inliningTarget, PPattern pattern, Object stringObject, int pos, int endPos, PythonMethod method, boolean mustAdvance,
                        @Cached("method") PythonMethod cachedMethod,
                        @CachedLibrary(limit = "1") @Shared InteropLibrary interop,
                        @Cached(inline = false) @Shared TRegexCompileNode tRegexCompileNode,
                        @Cached @Shared RECheckInputTypeNode reCheckInputTypeNode,
                        @Cached @Shared PyObjectSizeNode objectSizeNode,
                        @Cached(inline = false) @Shared TRegexCallExec tRegexCallExecNode,
                        @Cached @Shared TRegexUtil.InteropReadMemberNode interopReadMemberNode,
                        @Cached @Shared MatchNodes.NewNode newMatchNode,
                        @Cached @Shared InlinedConditionProfile matchProfile) {
            Object regexObject = tRegexCompileNode.execute(frame, pattern.cache, method, mustAdvance);

            return getSliceCached(frame, inliningTarget, pattern, stringObject, pos, endPos, method, mustAdvance,
                            interop, pattern, method, mustAdvance, pattern.cache, tRegexCompileNode, regexObject, reCheckInputTypeNode, objectSizeNode, tRegexCallExecNode, interopReadMemberNode,
                            newMatchNode, matchProfile);
        }

        @Idempotent
        public final boolean isSingleContext() {
            return PythonLanguage.get(this).isSingleContext();
        }
    }

    @GenerateCached
    @ImportStatic(PythonMethod.class)
    @SuppressWarnings("truffle-inlining")
    public abstract static class TRegexCompileNode extends PNodeWithContext {

        private static final TruffleString T_GETLOCALE = tsLiteral("getlocale");
        private static final TruffleString T_LOCALE = tsLiteral("locale");
        private static final TruffleString T_C = tsLiteral("C");
        private static final TruffleString T_EN_US = tsLiteral("en_US");
        private static final TruffleString T_DOT = tsLiteral(".");

        // limit of 6 specializations = 3 Python methods * 2 values of mustAdvance
        protected static final int SPECIALIZATION_LIMIT = 2 * PythonMethod.PYTHON_METHOD_COUNT;

        abstract Object execute(VirtualFrame frame, TRegexCache tRegexCache, PythonMethod method, boolean mustAdvance);

        @Specialization(guards = {"tRegexCache == cachedTRegexCache", "method == cachedMethod", "mustAdvance == cachedMustAdvance", "!cachedTRegexCache.isLocaleSensitive()"}, limit = "2")
        @SuppressWarnings("unused")
        Object cached(VirtualFrame frame, TRegexCache tRegexCache, PythonMethod method, boolean mustAdvance,
                        @Cached(value = "tRegexCache", weak = true) TRegexCache cachedTRegexCache,
                        @Cached("method") PythonMethod cachedMethod,
                        @Cached("mustAdvance") boolean cachedMustAdvance,
                        @Cached("getCompiledRegexLocaleNonSensitive(tRegexCache, method, mustAdvance)") Object compiledRegex) {
            return compiledRegex;
        }

        protected Object getCompiledRegexLocaleNonSensitive(TRegexCache tRegexCache, PythonMethod method, boolean mustAdvance) {
            final Object tRegex = tRegexCache.getRegexp(method, mustAdvance);
            if (tRegex != null) {
                return tRegex;
            } else {
                return tRegexCache.compile(this, getContext(), method, mustAdvance, null);
            }
        }

        @Specialization(guards = {"method == cachedMethod", "mustAdvance == cachedMustAdvance", "!tRegexCache.isLocaleSensitive()"}, limit = "SPECIALIZATION_LIMIT", replaces = "cached")
        Object localeNonSensitive(@SuppressWarnings("unused") VirtualFrame frame, TRegexCache tRegexCache, PythonMethod method, boolean mustAdvance,
                        @Cached("method") @SuppressWarnings("unused") PythonMethod cachedMethod,
                        @Cached("mustAdvance") @SuppressWarnings("unused") boolean cachedMustAdvance) {
            return getCompiledRegexLocaleNonSensitive(tRegexCache, method, cachedMustAdvance);
        }

        @Specialization(guards = {"method == cachedMethod", "mustAdvance == cachedMustAdvance", "tRegexCache.isLocaleSensitive()"}, limit = "SPECIALIZATION_LIMIT", replaces = "cached")
        @SuppressWarnings("truffle-static-method")
        Object localeSensitive(VirtualFrame frame, TRegexCache tRegexCache, PythonMethod method, boolean mustAdvance,
                        @Cached("method") @SuppressWarnings("unused") PythonMethod cachedMethod,
                        @Cached("mustAdvance") @SuppressWarnings("unused") boolean cachedMustAdvance,
                        @Cached("lookupGetLocaleFunction()") Object getLocale,
                        @Cached CallNode callGetLocale) {
            Object localeSettings = callGetLocale.execute(frame, getLocale);
            TruffleString locale = getLocaleFromSettings(localeSettings);

            final Object tRegex = tRegexCache.getLocaleSensitiveRegexp(method, mustAdvance, locale);
            if (tRegex != null) {
                return tRegex;
            } else {
                return tRegexCache.compile(this, getContext(), method, mustAdvance, locale);
            }
        }

        @TruffleBoundary
        @NeverDefault
        protected Object lookupGetLocaleFunction() {
            PythonModule locale = AbstractImportNode.importModule(T_LOCALE);
            return PyObjectLookupAttr.executeUncached(locale, T_GETLOCALE);
        }

        @TruffleBoundary
        @NeverDefault
        private TruffleString getLocaleFromSettings(Object localeSettings) {
            // locale settings is a tuple (<language code>, <encoding>)

            if (!(localeSettings instanceof PTuple tuple)) {
                throw CompilerDirectives.shouldNotReachHere();
            }

            if (PyTupleSizeNode.executeUncached(tuple) != 2) {
                throw CompilerDirectives.shouldNotReachHere();
            }

            Object languageObject = PyTupleGetItem.executeUncached(tuple, 0);
            Object encodingObject = PyTupleGetItem.executeUncached(tuple, 1);

            if (languageObject == PNone.NONE || encodingObject == PNone.NONE) {
                return T_C;
            }

            final TruffleString language;
            if (languageObject instanceof PNone) {
                language = T_EN_US;
            } else {
                language = CastToTruffleStringNode.executeUncached(languageObject);
            }

            // return locale in form "<lang>.<encoding>", e.g. "en.UTF-8"
            TruffleString encoding = CastToTruffleStringNode.executeUncached(encodingObject);
            TruffleString languageWithDot = TruffleString.ConcatNode.getUncached().execute(language, T_DOT, TS_ENCODING, true);
            return TruffleString.ConcatNode.getUncached().execute(languageWithDot, encoding, TS_ENCODING, true);
        }
    }

    @GenerateInline(false)       // footprint reduction 36 -> 17
    public abstract static class RECheckInputTypeNode extends Node {

        private static final TruffleString T_UNSUPPORTED_INPUT_TYPE = tsLiteral("expected string or bytes-like object");
        private static final TruffleString T_UNEXPECTED_BYTES = tsLiteral("cannot use a string pattern on a bytes-like object");
        private static final TruffleString T_UNEXPECTED_STR = tsLiteral("cannot use a bytes pattern on a string-like object");

        public abstract void execute(VirtualFrame frame, Object input, boolean expectBytes);

        @Specialization
        static void check(Object input, boolean expectBytes,
                        @Bind Node inliningTarget,
                        @Cached PyUnicodeCheckNode unicodeCheckNode,
                        @Cached BytesNodes.BytesLikeCheck bytesLikeCheck,
                        @Cached PRaiseNode unexpectedStrRaise,
                        @Cached PRaiseNode unexpectedBytesRaise,
                        @Cached PRaiseNode unexpectedTypeRaise) {
            if (unicodeCheckNode.execute(inliningTarget, input)) {
                if (expectBytes) {
                    throw unexpectedStrRaise.raise(inliningTarget, TypeError, T_UNEXPECTED_STR);
                }
                return;
            }
            if (bytesLikeCheck.execute(inliningTarget, input) || input instanceof PMMap || input instanceof PMemoryView || input instanceof PArray) {
                if (!expectBytes) {
                    throw unexpectedBytesRaise.raise(inliningTarget, TypeError, T_UNEXPECTED_BYTES);
                }
                return;
            }
            throw unexpectedTypeRaise.raise(inliningTarget, TypeError, T_UNSUPPORTED_INPUT_TYPE);
        }
    }

    @GenerateCached
    @ImportStatic(PythonMethod.class)
    @SuppressWarnings("truffle-inlining")
    public abstract static class TRegexCallExec extends Node {

        @Child private BufferToTruffleStringNode bufferToTruffleStringNode;

        public abstract Object execute(VirtualFrame frame, Object callable, Object inputStringOrBytes, int fromIndex, int toIndex);

        // limit of 2 specializations to allow inlining of both a must_advance=False and a
        // must_advance=True version in re builtins like sub, split, findall
        @Specialization(guards = "callable == cachedCallable", limit = "2")
        @SuppressWarnings("truffle-static-method")
        Object doCached(VirtualFrame frame, @SuppressWarnings("unused") Object callable, Object inputStringOrBytes, int fromIndex, int toIndex,
                        @Bind Node inliningTarget,
                        @Cached(value = "callable", weak = true) Object cachedCallable,
                        @Cached @Shared CastToTruffleStringNode cast,
                        @Cached @Shared TRegexUtil.InvokeExecMethodWithMaxIndexNode invokeExecNode,
                        @Cached @Shared InlinedBranchProfile binaryProfile) {
            TruffleString input;
            try {
                // This would materialize the string if it was native
                input = cast.execute(inliningTarget, inputStringOrBytes);
            } catch (CannotCastException e1) {
                binaryProfile.enter(inliningTarget);
                // It's bytes or other buffer object
                input = getBufferToTruffleStringNode().execute(frame, inputStringOrBytes);
            }
            return invokeExecNode.execute(inliningTarget, cachedCallable, input, fromIndex, toIndex);
        }

        @Specialization(replaces = "doCached")
        @ReportPolymorphism.Megamorphic
        Object doUncached(VirtualFrame frame, Object callable, Object inputStringOrBytes, int fromIndex, int toIndex,
                        @Bind Node inliningTarget,
                        @Cached @Shared CastToTruffleStringNode cast,
                        @Cached @Shared TRegexUtil.InvokeExecMethodWithMaxIndexNode invokeExecNode,
                        @Cached @Shared InlinedBranchProfile binaryProfile) {
            return doCached(frame, callable, inputStringOrBytes, fromIndex, toIndex, inliningTarget, callable, cast, invokeExecNode, binaryProfile);
        }

        private BufferToTruffleStringNode getBufferToTruffleStringNode() {
            if (bufferToTruffleStringNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                bufferToTruffleStringNode = insert(BufferToTruffleStringNode.create());
            }
            return bufferToTruffleStringNode;
        }

        @GenerateInline(false)
        abstract static class BufferToTruffleStringNode extends PNodeWithContext {

            public abstract TruffleString execute(VirtualFrame frame, Object buffer);

            @Specialization(limit = "3")
            static TruffleString convert(VirtualFrame frame, Object bytesLikeObject,
                            @Bind Node inliningTarget,
                            @Shared @Cached("createFor($node)") IndirectCallData.InteropCallData callData,
                            @Cached(inline = true) BufferToTruffleStringInnerNode innerNode,
                            @CachedLibrary("bytesLikeObject") PythonBufferAcquireLibrary bufferAcquireLib,
                            @CachedLibrary(limit = "1") @Shared PythonBufferAccessLibrary bufferLib) {
                Object buffer = null;
                try {
                    buffer = bufferAcquireLib.acquireReadonly(bytesLikeObject, frame, callData);
                    return innerNode.execute(inliningTarget, buffer);
                } finally {
                    if (buffer != null) {
                        bufferLib.release(buffer, frame, callData);
                    }
                }
            }

            @NeverDefault
            public static BufferToTruffleStringNode create() {
                return PatternNodesFactory.TRegexCallExecNodeGen.BufferToTruffleStringNodeGen.create();
            }
        }

        @GenerateInline
        abstract static class BufferToTruffleStringInnerNode extends PNodeWithContext {

            public abstract TruffleString execute(Node inliningTarget, Object buffer);

            @Specialization(limit = "4")
            static TruffleString convert(Node inliningTarget, Object buffer,
                            @CachedLibrary(value = "buffer") PythonBufferAccessLibrary bufferLib,
                            @Cached TruffleString.FromByteArrayNode fromByteArrayNode,
                            @Cached TruffleString.FromNativePointerNode fromNativePointerNode,
                            @Cached InlinedBranchProfile internalArrayProfile,
                            @Cached InlinedBranchProfile nativeProfile,
                            @Cached InlinedBranchProfile fallbackProfile) {
                PythonBufferAccessLibrary.assertIsBuffer(buffer);
                int len = bufferLib.getBufferLength(buffer);
                if (bufferLib.hasInternalByteArray(buffer)) {
                    internalArrayProfile.enter(inliningTarget);
                    byte[] bytes = bufferLib.getInternalByteArray(buffer);
                    return fromByteArrayNode.execute(bytes, 0, len, TS_ENCODING_BINARY, false);
                }
                if (bufferLib.isNative(buffer)) {
                    nativeProfile.enter(inliningTarget);
                    Object ptr = bufferLib.getNativePointer(buffer);
                    if (ptr != null) {
                        if (ptr instanceof Long lptr) {
                            ptr = new NativePointer(lptr);
                        }
                        return fromNativePointerNode.execute(ptr, 0, len, TS_ENCODING_BINARY, false);
                    }
                }
                fallbackProfile.enter(inliningTarget);
                byte[] bytes = bufferLib.getCopiedByteArray(buffer);
                return fromByteArrayNode.execute(bytes, 0, len, TS_ENCODING_BINARY, false);
            }
        }
    }
}
