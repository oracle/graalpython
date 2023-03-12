/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules;

import static com.oracle.graal.python.nodes.StringLiterals.T_COMMA;
import static com.oracle.graal.python.nodes.StringLiterals.T_SLASH;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.List;
import java.util.Objects;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithRaiseAndIndirectCall;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.BufferToTruffleStringNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.Encoding;
import com.oracle.truffle.api.strings.TruffleStringBuilder;
import org.graalvm.collections.EconomicMap;

@CoreFunctions(defineModule = "_sre")
public class SREModuleBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return SREModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        addBuiltinConstant("_with_tregex", core.getContext().getLanguage().getEngineOption(PythonOptions.WithTRegex));
        addBuiltinConstant("_with_sre", core.getContext().getLanguage().getEngineOption(PythonOptions.TRegexUsesSREFallback));
        super.initialize(core);
    }

    private static final int METHOD_SEARCH = 0;
    private static final int METHOD_MATCH = 1;
    private static final int METHOD_FULLMATCH = 2;

    public static final class TRegexCache {

        private final Object pattern;
        private final TruffleString flags;

        private Object searchRegexp;
        private Object matchRegexp;
        private Object fullMatchRegexp;

        private Object mustAdvanceSearchRegexp;
        private Object mustAdvanceMatchRegexp;
        private Object mustAdvanceFullMatchRegexp;

        private EconomicMap<RegexKey, Object> localeSensitiveRegexps;

        private static final TruffleString T_PYTHON_METHOD_SEARCH = tsLiteral("PythonMethod=search");
        private static final TruffleString T_PYTHON_METHOD_MATCH = tsLiteral("PythonMethod=match");
        private static final TruffleString T_PYTHON_METHOD_FULLMATCH = tsLiteral("PythonMethod=fullmatch");
        private static final TruffleString T_MUST_ADVANCE_TRUE = tsLiteral("MustAdvance=true");

        public TRegexCache(Object pattern, TruffleString flags) {
            this.pattern = pattern;
            this.flags = flags;
        }

        public Object getRegexp(int method, boolean mustAdvance) {
            switch (method) {
                case METHOD_SEARCH:
                    if (mustAdvance) {
                        return mustAdvanceSearchRegexp;
                    } else {
                        return searchRegexp;
                    }
                case METHOD_MATCH:
                    if (mustAdvance) {
                        return mustAdvanceMatchRegexp;
                    } else {
                        return matchRegexp;
                    }
                case METHOD_FULLMATCH:
                    if (mustAdvance) {
                        return mustAdvanceFullMatchRegexp;
                    } else {
                        return fullMatchRegexp;
                    }
                default:
                    throw CompilerDirectives.shouldNotReachHere();
            }
        }

        public void setRegexp(int method, boolean mustAdvance, Object regexp) {
            switch (method) {
                case METHOD_SEARCH:
                    if (mustAdvance) {
                        mustAdvanceSearchRegexp = regexp;
                    } else {
                        searchRegexp = regexp;
                    }
                    break;
                case METHOD_MATCH:
                    if (mustAdvance) {
                        mustAdvanceMatchRegexp = regexp;
                    } else {
                        matchRegexp = regexp;
                    }
                    break;
                case METHOD_FULLMATCH:
                    if (mustAdvance) {
                        mustAdvanceFullMatchRegexp = regexp;
                    } else {
                        fullMatchRegexp = regexp;
                    }
                    break;
                default:
                    throw CompilerDirectives.shouldNotReachHere();
            }
        }

        @TruffleBoundary
        private TruffleString getOptionsUncached(int pythonMethod, boolean mustAdvance) {
            TruffleStringBuilder options = TruffleStringBuilder.create(TS_ENCODING);
            switch (pythonMethod) {
                case METHOD_SEARCH:
                    options.appendStringUncached(T_PYTHON_METHOD_SEARCH);
                    break;
                case METHOD_MATCH:
                    options.appendStringUncached(T_PYTHON_METHOD_MATCH);
                    break;
                case METHOD_FULLMATCH:
                    options.appendStringUncached(T_PYTHON_METHOD_FULLMATCH);
                    break;
                default:
                    throw CompilerDirectives.shouldNotReachHere();
            }
            if (mustAdvance) {
                options.appendStringUncached(T_COMMA);
                options.appendStringUncached(T_MUST_ADVANCE_TRUE);
            }
            return options.toStringUncached();
        }

        public Object compile(VirtualFrame frame, int method, boolean mustAdvance, TRegexCompileInternalNode tRegexCompileInternalNode) {
            final Object regexp = tRegexCompileInternalNode.execute(frame, pattern, flags, getOptionsUncached(method, mustAdvance));
            setRegexp(method, mustAdvance, regexp);
            return regexp;
        }

        private static final class RegexKey {
            private final String pythonMethod;
            private final boolean mustAdvance;
            private final String pythonLocale;

            RegexKey(String pythonMethod, boolean mustAdvance, String pythonLocale) {
                this.pythonMethod = pythonMethod;
                this.mustAdvance = mustAdvance;
                this.pythonLocale = pythonLocale;
            }

            @Override
            public boolean equals(Object obj) {
                if (!(obj instanceof RegexKey)) {
                    return false;
                }
                RegexKey other = (RegexKey) obj;
                return this.pythonMethod.equals(other.pythonMethod) && this.mustAdvance == other.mustAdvance && this.pythonLocale.equals(other.pythonLocale);
            }

            @Override
            public int hashCode() {
                return Objects.hash(pythonMethod, mustAdvance, pythonLocale);
            }
        }
    }

    @Builtin(name = "tregex_init_cache", minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class TRegexInitCache extends PythonBinaryBuiltinNode {

        @Specialization
        Object call(VirtualFrame frame, Object pattern, Object flags,
                    @Cached CastToTruffleStringNode flagsToStringNode) {
            TruffleString flagsStr = flagsToStringNode.execute(flags);
            return new TRegexCache(pattern, flagsStr);
        }
    }

    abstract static class ToRegexSourceNode extends PNodeWithRaiseAndIndirectCall {

        private static final TruffleString T_FLAVOR_PYTHON = tsLiteral("Flavor=Python");
        private static final TruffleString T_ENCODING_UTF_32 = tsLiteral("Encoding=UTF-32");
        private static final TruffleString T_ENCODING_LATIN_1 = tsLiteral("Encoding=LATIN-1");

        public abstract Source execute(VirtualFrame frame, Object pattern, TruffleString flags, TruffleString options);

        private static Source constructRegexSource(Node inliningTarget, TruffleString encoding, TruffleString options, TruffleString pattern, TruffleString flags,
                        InlinedConditionProfile nonEmptyOptionsProfile,
                        TruffleStringBuilder.AppendStringNode appendStringNode, TruffleStringBuilder.ToStringNode toStringNode, TruffleString.ToJavaStringNode toJavaStringNode) {
            TruffleStringBuilder sb = TruffleStringBuilder.create(TS_ENCODING);
            appendStringNode.execute(sb, T_FLAVOR_PYTHON);
            appendStringNode.execute(sb, T_COMMA);
            appendStringNode.execute(sb, encoding);
            if (nonEmptyOptionsProfile.profile(inliningTarget, !options.isEmpty())) {
                appendStringNode.execute(sb, T_COMMA);
                appendStringNode.execute(sb, options);
            }
            appendStringNode.execute(sb, T_SLASH);
            appendStringNode.execute(sb, pattern);
            appendStringNode.execute(sb, T_SLASH);
            appendStringNode.execute(sb, flags);
            return createSourceBoundary(toJavaStringNode.execute(toStringNode.execute(sb)));
        }

        @TruffleBoundary
        private static Source createSourceBoundary(String regexSourceStr) {
            return Source.newBuilder("regex", regexSourceStr, "re").mimeType("application/tregex").internal(true).build();
        }

        @Specialization
        protected Source doString(TruffleString pattern, TruffleString flags, TruffleString options,
                        @Bind("this") Node inliningTarget,
                        @Shared("nonEmptyOptions") @Cached InlinedConditionProfile nonEmptyOptionsProfile,
                        @Shared("appendStr") @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Shared("toString") @Cached TruffleStringBuilder.ToStringNode toStringNode,
                        @Shared("ts2js") @Cached TruffleString.ToJavaStringNode toJavaStringNode) {
            return constructRegexSource(inliningTarget, T_ENCODING_UTF_32, options, pattern, flags, nonEmptyOptionsProfile, appendStringNode, toStringNode, toJavaStringNode);
        }

        @Specialization
        @SuppressWarnings("truffle-static-method")
        protected Source doGeneric(VirtualFrame frame, Object pattern, TruffleString flags, TruffleString options,
                        @Bind("this") Node inliningTarget,
                        @Shared("nonEmptyOptions") @Cached InlinedConditionProfile nonEmptyOptionsProfile,
                        @Shared("appendStr") @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Shared("toString") @Cached TruffleStringBuilder.ToStringNode toStringNode,
                        @Shared("ts2js") @Cached TruffleString.ToJavaStringNode toJavaStringNode,
                        @Cached CastToTruffleStringNode cast,
                        @CachedLibrary(limit = "3") PythonBufferAcquireLibrary bufferAcquireLib,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib,
                        @Cached TruffleString.FromByteArrayNode fromByteArrayNode,
                        @Cached TruffleString.SwitchEncodingNode switchEncodingNode) {
            try {
                return doString(cast.execute(pattern), flags, options, inliningTarget, nonEmptyOptionsProfile, appendStringNode, toStringNode, toJavaStringNode);
            } catch (CannotCastException ce) {
                Object buffer;
                try {
                    buffer = bufferAcquireLib.acquireReadonly(pattern, frame, this);
                } catch (PException e) {
                    throw raise(TypeError, ErrorMessages.EXPECTED_STR_OR_BYTESLIKE_OBJ);
                }
                try {
                    byte[] bytes = bufferLib.getInternalOrCopiedByteArray(buffer);
                    int bytesLen = bufferLib.getBufferLength(buffer);
                    TruffleString patternStr = switchEncodingNode.execute(fromByteArrayNode.execute(bytes, 0, bytesLen, Encoding.ISO_8859_1, false), TS_ENCODING);
                    return constructRegexSource(inliningTarget, T_ENCODING_LATIN_1, options, patternStr, flags, nonEmptyOptionsProfile, appendStringNode, toStringNode, toJavaStringNode);
                } finally {
                    bufferLib.release(buffer, frame, this);
                }
            }
        }
    }

    @Builtin(name = "tregex_compile_internal", minNumOfPositionalArgs = 3)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class TRegexCompileInternalNode extends PythonTernaryBuiltinNode {

        @Specialization
        Object call(VirtualFrame frame, Object pattern, Object flags, Object options,
                    @Bind("this") Node inliningTarget,
                    @Cached InlinedBranchProfile potentialSyntaxError,
                    @Cached InlinedBranchProfile syntaxError,
                    @Cached InlinedBranchProfile unsupportedRegexError,
                    @Cached CastToTruffleStringNode flagsToStringNode,
                    @Cached CastToTruffleStringNode optionsToStringNode,
                    @Cached ToRegexSourceNode toRegexSourceNode,
                    @CachedLibrary(limit = "2") InteropLibrary exceptionLib,
                    @CachedLibrary(limit = "2") InteropLibrary compiledRegexLib,
                    @Cached TruffleString.SwitchEncodingNode switchEncodingNode) {
            try {
                TruffleString flagsStr = flagsToStringNode.execute(flags);
                TruffleString optionsStr = optionsToStringNode.execute(options);
                Source regexSource = toRegexSourceNode.execute(frame, pattern, flagsStr, optionsStr);
                Object compiledRegex = getContext().getEnv().parseInternal(regexSource).call();
                if (compiledRegexLib.isNull(compiledRegex)) {
                    unsupportedRegexError.enter(inliningTarget);
                    return PNone.NONE;
                } else {
                    return compiledRegex;
                }
            } catch (RuntimeException e) {
                return handleError(e, inliningTarget, syntaxError, potentialSyntaxError, exceptionLib, switchEncodingNode);
            }
        }

        private Object handleError(RuntimeException e, Node inliningTarget, InlinedBranchProfile syntaxError, InlinedBranchProfile potentialSyntaxError, InteropLibrary lib,
                                   TruffleString.SwitchEncodingNode switchEncodingNode) {
            try {
                if (lib.isException(e)) {
                    potentialSyntaxError.enter(inliningTarget);
                    if (lib.getExceptionType(e) == ExceptionType.PARSE_ERROR) {
                        syntaxError.enter(inliningTarget);
                        TruffleString reason = switchEncodingNode.execute(lib.asTruffleString(lib.getExceptionMessage(e)), TS_ENCODING);
                        SourceSection sourceSection = lib.getSourceLocation(e);
                        int position = sourceSection.getCharIndex();
                        // passed like Object array concats the values
                        throw raise(ValueError, new Object[]{reason, position});
                    }
                }
            } catch (UnsupportedMessageException e1) {
                throw CompilerDirectives.shouldNotReachHere();
            }
            // just re-throw
            throw e;
        }
    }

    @Builtin(name = "tregex_compile", minNumOfPositionalArgs = 3)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class TRegexCompileNode extends PythonTernaryBuiltinNode {

        @Specialization(guards = { "method == cachedMethod", "mustAdvance == cachedMustAdvance" }, limit = "6")
        Object compile(VirtualFrame frame, TRegexCache tRegexCache, int method, boolean mustAdvance,
                      @Cached TRegexCompileInternalNode tRegexCompileInternalNode,
                      @Cached("method") int cachedMethod,
                      @Cached("mustAdvance") boolean cachedMustAdvance) {
            final Object tRegex = tRegexCache.getRegexp(method, mustAdvance);
            if (tRegex != null) {
                return tRegex;
            } else {
                return tRegexCache.compile(frame, method, mustAdvance, tRegexCompileInternalNode);
            }
        }
    }

    @Builtin(name = "tregex_call_exec", minNumOfPositionalArgs = 3)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class TRegexCallExec extends PythonTernaryBuiltinNode {

        @Specialization(limit = "1")
        Object call(VirtualFrame frame, Object callable, Object inputStringOrBytes, Number fromIndex,
                        @Cached CastToTruffleStringNode cast,
                        @CachedLibrary(limit = "3") PythonBufferAcquireLibrary bufferAcquireLib,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib,
                        @Cached BufferToTruffleStringNode bufferToTruffleStringNode,
                        @CachedLibrary("callable") InteropLibrary interop) {
            PythonContext context = getContext();
            PythonLanguage language = getLanguage();
            TruffleString input;
            Object buffer = null;
            try {
                try {
                    // This would materialize the string if it was native
                    input = cast.execute(inputStringOrBytes);
                } catch (CannotCastException e1) {
                    // It's bytes or other buffer object
                    buffer = bufferAcquireLib.acquireReadonly(inputStringOrBytes, frame, this);
                    input = bufferToTruffleStringNode.execute(buffer, 0);
                }
                Object state = IndirectCallContext.enter(frame, language, context, this);
                try {
                    return interop.execute(callable, input, fromIndex);
                } catch (ArityException | UnsupportedTypeException | UnsupportedMessageException e2) {
                    throw CompilerDirectives.shouldNotReachHere("could not call TRegex exec method", e2);
                } finally {
                    IndirectCallContext.exit(frame, language, context, state);
                }
            } finally {
                if (buffer != null) {
                    bufferLib.release(buffer, frame, this);
                }
            }
        }
    }
}
