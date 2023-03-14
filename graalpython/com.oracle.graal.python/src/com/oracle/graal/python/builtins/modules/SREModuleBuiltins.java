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

import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.builtins.objects.slice.SliceNodes;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonSeptenaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.BufferToTruffleStringNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.strings.TruffleString;
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

        private final String pattern;
        private final String flags;
        public final boolean binary;

        private Object searchRegexp;
        private Object matchRegexp;
        private Object fullMatchRegexp;

        private Object mustAdvanceSearchRegexp;
        private Object mustAdvanceMatchRegexp;
        private Object mustAdvanceFullMatchRegexp;

        private EconomicMap<RegexKey, Object> localeSensitiveRegexps;
        private static final String ENCODING_UTF_32 = "Encoding=UTF-32";
        private static final String ENCODING_LATIN_1 = "Encoding=LATIN-1";

        @TruffleBoundary
        public TRegexCache(Object pattern, TruffleString flags) {
            String patternStr;
            boolean binary = true;
            try {
                patternStr = CastToTruffleStringNode.getUncached().execute(pattern).toJavaStringUncached();
                binary = false;
            } catch (CannotCastException ce) {
                Object buffer;
                try {
                    buffer = PythonBufferAcquireLibrary.getUncached().acquireReadonly(pattern);
                } catch (PException e) {
                    throw PRaiseNode.getUncached().raise(TypeError, ErrorMessages.EXPECTED_STR_OR_BYTESLIKE_OBJ);
                }
                PythonBufferAccessLibrary bufferLib = PythonBufferAccessLibrary.getUncached();
                try {
                    byte[] bytes = bufferLib.getInternalOrCopiedByteArray(buffer);
                    int bytesLen = bufferLib.getBufferLength(buffer);
                    patternStr = new String(bytes, 0, bytesLen, StandardCharsets.ISO_8859_1);
                } finally {
                    bufferLib.release(buffer);
                }
            }
            this.pattern = patternStr;
            this.binary = binary;
            this.flags = flags.toJavaStringUncached();
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

        private String getOptions(int pythonMethod, boolean mustAdvance) {
            StringBuilder sb = new StringBuilder();
            switch (pythonMethod) {
                case METHOD_SEARCH:
                    sb.append("PythonMethod=search");
                    break;
                case METHOD_MATCH:
                    sb.append("PythonMethod=match");
                    break;
                case METHOD_FULLMATCH:
                    sb.append("PythonMethod=fullmatch");
                    break;
                default:
                    throw CompilerDirectives.shouldNotReachHere();
            }
            if (mustAdvance) {
                sb.append(',');
                sb.append("MustAdvance=true");
            }
            return sb.toString();
        }

        @TruffleBoundary
        public Object compile(PythonContext context, int method, boolean mustAdvance) {
            String options = getOptions(method, mustAdvance);
            InteropLibrary lib = InteropLibrary.getUncached();
            Object regexp;
            try {
                Source regexSource = constructSource(binary ? ENCODING_LATIN_1 : ENCODING_UTF_32, options, pattern, flags);
                Object compiledRegex = context.getEnv().parseInternal(regexSource).call();
                if (lib.isNull(compiledRegex)) {
                    regexp = PNone.NONE;
                } else {
                    regexp = compiledRegex;
                }
            } catch (RuntimeException e) {
                try {
                    if (lib.isException(e)) {
                        if (lib.getExceptionType(e) == ExceptionType.PARSE_ERROR) {
                            TruffleString reason = lib.asTruffleString(lib.getExceptionMessage(e)).switchEncodingUncached(TS_ENCODING);
                            SourceSection sourceSection = lib.getSourceLocation(e);
                            int position = sourceSection.getCharIndex();
                            // passed like Object array concats the values
                            throw PRaiseNode.getUncached().raise(ValueError, new Object[]{reason, position});
                        }
                    }
                } catch (UnsupportedMessageException e1) {
                    throw CompilerDirectives.shouldNotReachHere();
                }
                // just re-throw
                throw e;
            }
            setRegexp(method, mustAdvance, regexp);
            return regexp;
        }

        private static Source constructSource(String encoding, String options, String pattern, String flags) {
            StringBuilder sb = new StringBuilder();
            sb.append("Flavor=Python");
            sb.append(',');
            sb.append(encoding);
            if (!options.isEmpty()) {
                sb.append(',');
                sb.append(options);
            }
            sb.append('/');
            sb.append(pattern);
            sb.append('/');
            sb.append(flags);
            return Source.newBuilder("regex", sb.toString(), "re").mimeType("application/tregex").internal(true).build();
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

    @Builtin(name = "tregex_compile", minNumOfPositionalArgs = 3)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class TRegexCompileNode extends PythonTernaryBuiltinNode {

        @Specialization(guards = { "method == cachedMethod", "mustAdvance == cachedMustAdvance" }, limit = "6")
        Object compile(VirtualFrame frame, TRegexCache tRegexCache, int method, boolean mustAdvance,
                      @Cached("method") int cachedMethod,
                      @Cached("mustAdvance") boolean cachedMustAdvance) {
            final Object tRegex = tRegexCache.getRegexp(method, mustAdvance);
            if (tRegex != null) {
                return tRegex;
            } else {
                return tRegexCache.compile(getContext(), method, mustAdvance);
            }
        }
    }

    @Builtin(name = "tregex_search", minNumOfPositionalArgs = 7)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class TRegexSearch extends PythonSeptenaryBuiltinNode {

        private static final PTuple SUPPORTED_BINARY_INPUT_TYPES = PythonObjectFactory.getUncached().createTuple(new Object[] {PythonBuiltinClassType.PBytes, PythonBuiltinClassType.PByteArray, PythonBuiltinClassType.PMMap, PythonBuiltinClassType.PMemoryView, PythonBuiltinClassType.PArray});
        private static final PTuple SUPPORTED_INPUT_TYPES = PythonObjectFactory.getUncached().createTuple(new Object[] {PythonBuiltinClassType.PString, SUPPORTED_BINARY_INPUT_TYPES});
        private static final TruffleString T_UNSUPPORTED_INPUT_TYPE = tsLiteral("expected string or bytes-like object");
        private static final TruffleString T_UNEXPECTED_BYTES = tsLiteral("cannot use a string pattern on a bytes-like object");
        private static final TruffleString T_UNEXPECTED_STR = tsLiteral("cannot use a bytes pattern on a string-like object");

        @Specialization(guards = "tRegexCache.binary == binary", limit = "2")
        Object search(VirtualFrame frame, TRegexCache tRegexCache, Object inputStringOrBytes, int pos, int endPos, int method, boolean mustAdvance, Object matchConstructor,
                      @Cached("tRegexCache.binary") boolean binary,
                      @Cached BuiltinFunctions.IsInstanceNode isSupportedNode,
                      @Cached BuiltinFunctions.IsInstanceNode isExpectedNode,
                      @Cached ConditionProfile unsupportedInputTypeProfile,
                      @Cached ConditionProfile unexpectedInputTypeProfile,
                      @Cached BuiltinFunctions.LenNode lenNode,
                      @Cached ConditionProfile truncatingInputProfile,
                      @Cached SliceNodes.CreateSliceNode createSliceNode,
                      @Cached PyObjectGetItem getItemNode,
                      @Cached TRegexCompileNode tRegexCompileNode,
                      @Cached TRegexCallExec tRegexCallExec,
                      @Cached ConditionProfile matchProfile,
                      @CachedLibrary(limit = "1") InteropLibrary libResult,
                      @Cached CallNode constructResultNode) {
            if (unsupportedInputTypeProfile.profile(!(boolean) isSupportedNode.execute(frame, inputStringOrBytes, SUPPORTED_INPUT_TYPES))) {
                throw getRaiseNode().raise(TypeError, T_UNSUPPORTED_INPUT_TYPE);
            }
            if (unexpectedInputTypeProfile.profile(!binary && !(boolean) isExpectedNode.execute(frame, inputStringOrBytes, PythonBuiltinClassType.PString))) {
                throw getRaiseNode().raise(TypeError, T_UNEXPECTED_BYTES);
            }
            if (unexpectedInputTypeProfile.profile(binary && (boolean) isExpectedNode.execute(frame, inputStringOrBytes, PythonBuiltinClassType.PString))) {
                throw getRaiseNode().raise(TypeError, T_UNEXPECTED_STR);
            }
            int length = (int) lenNode.execute(frame, inputStringOrBytes);
            if (endPos < 0) {
                endPos = 0;
            } else if (endPos > length) {
                endPos = length;
            }
            if (pos < 0) {
                pos = 0;
            } else if (pos > length) {
                pos = length;
            }
            Object truncatedInput = inputStringOrBytes;
            if (truncatingInputProfile.profile(endPos != length)) {
                truncatedInput = getItemNode.execute(frame, inputStringOrBytes, createSliceNode.execute(0, endPos, 1));
            }
            Object compiledRegex = tRegexCompileNode.execute(frame, tRegexCache, method, mustAdvance);
            Object regexResult = tRegexCallExec.execute(frame, compiledRegex, truncatedInput, pos);
            try {
                if (matchProfile.profile((boolean) libResult.readMember(regexResult, "isMatch"))) {
                    return constructResultNode.execute(matchConstructor, pos, endPos, regexResult);
                } else {
                    return PNone.NONE;
                }
            } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                throw CompilerDirectives.shouldNotReachHere();
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
                    @CachedLibrary("callable") InteropLibrary interop,
                    @Cached BranchProfile binaryProfile) {
            TruffleString input;
            Object buffer = null;
            try {
                try {
                    // This would materialize the string if it was native
                    input = cast.execute(inputStringOrBytes);
                } catch (CannotCastException e1) {
                    binaryProfile.enter();
                    // It's bytes or other buffer object
                    buffer = bufferAcquireLib.acquireReadonly(inputStringOrBytes, frame, this);
                    input = bufferToTruffleStringNode.execute(buffer, 0);
                }
                try {
                    return interop.invokeMember(callable, "exec", input, fromIndex);
                } catch (ArityException | UnsupportedTypeException | UnsupportedMessageException | UnknownIdentifierException e2) {
                    throw CompilerDirectives.shouldNotReachHere("could not call TRegex exec method", e2);
                }
            } finally {
                if (buffer != null) {
                    bufferLib.release(buffer, frame, this);
                }
            }
        }
    }
}
