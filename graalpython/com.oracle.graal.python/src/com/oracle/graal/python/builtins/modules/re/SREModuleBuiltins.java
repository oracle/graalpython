/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.NotImplementedError;
import static com.oracle.graal.python.nodes.BuiltinNames.T__SRE;
import static com.oracle.graal.python.nodes.ErrorMessages.BAD_CHAR_IN_GROUP_NAME;
import static com.oracle.graal.python.nodes.ErrorMessages.BAD_ESCAPE_END_OF_STRING;
import static com.oracle.graal.python.nodes.ErrorMessages.BAD_ESCAPE_S;
import static com.oracle.graal.python.nodes.ErrorMessages.INVALID_GROUP_REFERENCE;
import static com.oracle.graal.python.nodes.ErrorMessages.MISSING_GROUP_NAME;
import static com.oracle.graal.python.nodes.ErrorMessages.MISSING_LEFT_ANGLE_BRACKET;
import static com.oracle.graal.python.nodes.ErrorMessages.MISSING_RIGHT_ANGLE_BRACKET;
import static com.oracle.graal.python.nodes.ErrorMessages.OCTAL_ESCAPE_OUT_OF_RANGE;
import static com.oracle.graal.python.nodes.ErrorMessages.UNKNOWN_GROUP_NAME;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING_BINARY;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.TRegexUtil;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.lib.PyTupleGetItem;
import com.oracle.graal.python.lib.PyTupleSizeNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.statement.AbstractImportNode;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.object.Shape;
import org.graalvm.collections.EconomicMap;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.array.PArray;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.cext.common.NativePointer;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.mmap.PMMap;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.str.StringUtils;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectAsciiNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectReprAsTruffleStringNode;
import com.oracle.graal.python.lib.PyUnicodeCheckNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromModuleNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.IndirectCallData.InteropCallData;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.formatting.ErrorMessageFormatter;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.util.ArrayBuilder;
import com.oracle.graal.python.util.IntArrayBuilder;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;
import com.oracle.truffle.api.strings.TruffleStringBuilderUTF32;

@CoreFunctions(defineModule = "_sre")
public final class SREModuleBuiltins extends PythonBuiltins {

    private static final TruffleString T_ERROR = tsLiteral("error");

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return SREModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        addBuiltinConstant("_METHOD_SEARCH", PythonMethod.Search);
        addBuiltinConstant("_METHOD_MATCH", PythonMethod.Match);
        addBuiltinConstant("_METHOD_FULLMATCH", PythonMethod.FullMatch);

        addBuiltinConstant("CODESIZE", 4);
        addBuiltinConstant("MAGIC", 20221023);
        addBuiltinConstant("MAXREPEAT", 4294967295L);
        addBuiltinConstant("MAXGROUPS", 2147483647);

        super.initialize(core);
    }

    public enum PythonMethod implements TruffleObject {
        Search(tsLiteral("search")),
        Match(tsLiteral("match")),
        FullMatch(tsLiteral("fullmatch"));

        public static final int PYTHON_METHOD_COUNT = PythonMethod.values().length;

        private final TruffleString name;

        PythonMethod(TruffleString name) {
            this.name = name;
        }

        public TruffleString getMethodName() {
            return name;
        }

        public String getTRegexOption() {
            return "PythonMethod=" + name.toJavaStringUncached();
        }
    }

    @Builtin(name = "getcodesize", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    abstract static class GetCodeSizeNode extends PythonBuiltinNode {
        private static final TruffleString TS_GETCODESIZE_NOT_YET_IMPLEMENTED = tsLiteral("_sre.getcodesize is not yet implemented");

        @Specialization
        static Object getCodeSize(Object self, Object[] args, PKeyword[] keywords,
                                  @Bind Node inliningTarget,
                                  @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(inliningTarget, NotImplementedError, TS_GETCODESIZE_NOT_YET_IMPLEMENTED);
        }
    }

    @Builtin(name = "unicode_iscased", minNumOfPositionalArgs = 2, parameterNames = {"$module", "codepoint"}, doc = "unicode_iscased($module, character, /)\n--\n\n")
    @GenerateNodeFactory
    @ArgumentClinic(name = "codepoint", conversion = ArgumentClinic.ClinicConversion.Int)
    abstract static class UnicodeIsCasedNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SREModuleBuiltinsClinicProviders.UnicodeIsCasedNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        @TruffleBoundary
        static boolean isCased(Object module, int codepoint) {
            if (Character.isLetter(codepoint)) {
                return false;
            }

            return Character.toLowerCase(codepoint) != Character.toUpperCase(codepoint);
        }
    }

    @Builtin(name = "unicode_tolower", minNumOfPositionalArgs = 2, parameterNames = {"$module", "codepoint"}, doc = "unicode_tolower($module, character, /)\n--\n\n")
    @GenerateNodeFactory
    @ArgumentClinic(name = "codepoint", conversion = ArgumentClinic.ClinicConversion.Int)
    abstract static class UnicodeToLowerNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SREModuleBuiltinsClinicProviders.UnicodeToLowerNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        @TruffleBoundary
        static int toLower(Object module, int codepoint) {
            return Character.toLowerCase(codepoint);
        }
    }

    @Builtin(name = "ascii_iscased", minNumOfPositionalArgs = 2, parameterNames = {"$module", "codepoint"}, doc = "unicode_iscased($module, character, /)\n--\n\n")
    @GenerateNodeFactory
    @ArgumentClinic(name = "codepoint", conversion = ArgumentClinic.ClinicConversion.Int)
    abstract static class AsciiIsCasedNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SREModuleBuiltinsClinicProviders.AsciiIsCasedNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        @TruffleBoundary
        static boolean isCased(Object module, int codepoint) {
            return codepoint < 128 && Character.isLetter(codepoint);
        }
    }

    @Builtin(name = "ascii_tolower", minNumOfPositionalArgs = 2, parameterNames = {"$module", "codepoint"}, doc = "unicode_tolower($module, character, /)\n--\n\n")
    @GenerateNodeFactory
    @ArgumentClinic(name = "codepoint", conversion = ArgumentClinic.ClinicConversion.Int)
    abstract static class AsciiToLowerNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SREModuleBuiltinsClinicProviders.AsciiToLowerNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        @TruffleBoundary
        static int toLower(Object module, int codepoint) {
            if (codepoint >= 128) {
                return codepoint;
            }

            return Character.toLowerCase(codepoint);
        }
    }

    @Builtin(name = "template", minNumOfPositionalArgs = 2, parameterNames = {"pattern", "template"})
    @GenerateNodeFactory
    abstract static class TemplateNode extends PythonBuiltinNode {
        private static final TruffleString INVALID_TEMPLATE = tsLiteral("invalid template");

        @Specialization
        PTemplate template(VirtualFrame frame, PPattern pattern, PList template,
                           @Bind Node inliningTarget,
                           @Cached @Shared PRaiseNode raiseNode,
                           @Cached SequenceStorageNodes.GetItemScalarNode getItem,
                           @Cached PyNumberAsSizeNode asSizeNode,
                           @Cached TypeNodes.GetInstanceShape getInstanceShape) {
            // template is a list containing interleaved literal strings (str or bytes)
            // and group indices (int), as returned by _parser.parse_template:
            // [str 1, int 1, str 2, int 2, ..., str N-1, int N-1, str N].

            SequenceStorage storage = template.getSequenceStorage();
            int length = storage.length();

            if ((length & 1) == 0 || length < 1) {
                throw raiseNode.raise(inliningTarget, TypeError, INVALID_TEMPLATE);
            }

            Object[] literals = new Object[length / 2 + 1]; // there is an extra trailing literal
            int[] indices = new int[length / 2];
            for (int i = 0; i < length; i++) {
                Object item = getItem.execute(inliningTarget, storage, i);

                if ((i & 1) == 1) {
                    // group index
                    int index = asSizeNode.executeExact(frame, inliningTarget, item);
                    if (index < 0) {
                        throw raiseNode.raise(inliningTarget, TypeError, INVALID_TEMPLATE);
                    }

                    indices[i / 2] = index;
                } else {
                    // string (or bytes) literal
                    literals[i / 2] = item;
                }
            }

            Object cls = PythonBuiltinClassType.SRETemplate;
            Shape shape = getInstanceShape.execute(cls);
            return new PTemplate(cls, shape, literals, indices);
        }

        @Fallback
        PTemplate template(Object pattern, Object template,
                           @Bind Node inliningTarget,
                           @Cached @Shared PRaiseNode raiseNode) {
            throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.ARG_D_MUST_BE_S_NOT_P, "template()", 2, "list", template);
        }
    }

    public static final class TRegexCache {

        private final Object originalPattern;
        private final String pattern;
        private final String flags;
        private final boolean binary;
        private final boolean localeSensitive;

        private Object searchRegexp;
        private Object matchRegexp;
        private Object fullMatchRegexp;

        private Object mustAdvanceSearchRegexp;
        private Object mustAdvanceMatchRegexp;
        private Object mustAdvanceFullMatchRegexp;
        private final EconomicMap<RegexKey, Object> localeSensitiveRegexps;

        private static final String ENCODING_UTF_32 = "Encoding=UTF-32";
        private static final String ENCODING_LATIN_1 = "Encoding=LATIN-1";
        private static final TruffleString T_VALUE_ERROR_UNICODE_FLAG_BYTES_PATTERN = tsLiteral("cannot use UNICODE flag with a bytes pattern");
        private static final TruffleString T_VALUE_ERROR_LOCALE_FLAG_STR_PATTERN = tsLiteral("cannot use LOCALE flag with a str pattern");
        private static final TruffleString T_VALUE_ERROR_ASCII_UNICODE_INCOMPATIBLE = tsLiteral("ASCII and UNICODE flags are incompatible");
        private static final TruffleString T_VALUE_ERROR_ASCII_LOCALE_INCOMPATIBLE = tsLiteral("ASCII and LOCALE flags are incompatible");

        public static final int FLAG_IGNORECASE = 2;
        public static final int FLAG_LOCALE = 4;
        public static final int FLAG_MULTILINE = 8;
        public static final int FLAG_DOTALL = 16;
        public static final int FLAG_UNICODE = 32;
        public static final int FLAG_VERBOSE = 64;
        public static final int FLAG_ASCII = 256;

        @TruffleBoundary
        public TRegexCache(Node node, Object pattern, int flags) {
            this.originalPattern = pattern;
            String patternStr;
            boolean binary = true;
            try {
                patternStr = CastToTruffleStringNode.executeUncached(pattern).toJavaStringUncached();
                binary = false;
            } catch (CannotCastException ce) {
                Object buffer;
                try {
                    buffer = PythonBufferAcquireLibrary.getUncached().acquireReadonly(pattern);
                } catch (PException e) {
                    throw PRaiseNode.raiseStatic(node, TypeError, ErrorMessages.EXPECTED_STR_OR_BYTESLIKE_OBJ);
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
            this.flags = getTRegexFlags(flags);
            this.localeSensitive = calculateLocaleSensitive();
            this.localeSensitiveRegexps = this.localeSensitive ? EconomicMap.create() : null;
        }

        public boolean isBinary() {
            return binary;
        }

        public Object getPattern() {
            return pattern;
        }

        public String getFlags() {
            return flags;
        }

        @Idempotent
        public boolean isLocaleSensitive() {
            return localeSensitive;
        }

        private static String getTRegexFlags(int flags) {
            StringBuilder sb = new StringBuilder();
            if ((flags & FLAG_IGNORECASE) != 0) {
                sb.append('i');
            }
            if ((flags & FLAG_LOCALE) != 0) {
                sb.append('L');
            }
            if ((flags & FLAG_MULTILINE) != 0) {
                sb.append('m');
            }
            if ((flags & FLAG_DOTALL) != 0) {
                sb.append('s');
            }
            if ((flags & FLAG_UNICODE) != 0) {
                sb.append('u');
            }
            if ((flags & FLAG_VERBOSE) != 0) {
                sb.append('x');
            }
            if ((flags & FLAG_ASCII) != 0) {
                sb.append('a');
            }
            return sb.toString();
        }

        /**
         * Tests whether the regex is locale-sensitive. It is not completely precise. In some
         * instances, it will return {@code true} even though the regex is *not* locale-sensitive.
         * This is the case when sequences resembling inline flags appear in character classes or
         * comments.
         */
        private boolean calculateLocaleSensitive() {
            if (!isBinary()) {
                return false;
            }
            if (flags.indexOf('L') != -1) {
                return true;
            }
            int position = 0;
            while (position < pattern.length()) {
                position = pattern.indexOf("(?", position);
                if (position == -1) {
                    break;
                }
                int backslashPosition = position - 1;
                while (backslashPosition >= 0 && pattern.charAt(backslashPosition) == '\\') {
                    backslashPosition--;
                }
                // jump over '(?'
                position += 2;
                if ((position - backslashPosition) % 2 == 0) {
                    // found odd number of backslashes, the parentheses is a literal
                    continue;
                }
                while (position < pattern.length() && "aiLmsux".indexOf(pattern.charAt(position)) != -1) {
                    if (pattern.charAt(position) == 'L') {
                        return true;
                    }
                    position++;
                }
            }
            return false;
        }

        public Object getRegexp(PythonMethod method, boolean mustAdvance) {
            assert !isLocaleSensitive();
            switch (method) {
                case Search:
                    if (mustAdvance) {
                        return mustAdvanceSearchRegexp;
                    } else {
                        return searchRegexp;
                    }
                case Match:
                    if (mustAdvance) {
                        return mustAdvanceMatchRegexp;
                    } else {
                        return matchRegexp;
                    }
                case FullMatch:
                    if (mustAdvance) {
                        return mustAdvanceFullMatchRegexp;
                    } else {
                        return fullMatchRegexp;
                    }
                default:
                    throw CompilerDirectives.shouldNotReachHere();
            }
        }

        @TruffleBoundary
        public Object getLocaleSensitiveRegexp(PythonMethod method, boolean mustAdvance, TruffleString locale) {
            assert isLocaleSensitive();
            return localeSensitiveRegexps.get(new RegexKey(method, mustAdvance, locale));
        }

        private void setRegexp(PythonMethod method, boolean mustAdvance, Object regexp) {
            assert !isLocaleSensitive();
            switch (method) {
                case Search:
                    if (mustAdvance) {
                        mustAdvanceSearchRegexp = regexp;
                    } else {
                        searchRegexp = regexp;
                    }
                    break;
                case Match:
                    if (mustAdvance) {
                        mustAdvanceMatchRegexp = regexp;
                    } else {
                        matchRegexp = regexp;
                    }
                    break;
                case FullMatch:
                    if (mustAdvance) {
                        mustAdvanceFullMatchRegexp = regexp;
                    } else {
                        fullMatchRegexp = regexp;
                    }
                    break;
            }
        }

        @TruffleBoundary
        private void setLocaleSensitiveRegexp(PythonMethod method, boolean mustAdvance, TruffleString locale, Object regexp) {
            assert isLocaleSensitive();
            localeSensitiveRegexps.put(new RegexKey(method, mustAdvance, locale), regexp);
        }

        private String getTRegexOptions(String encoding, PythonMethod pythonMethod, boolean mustAdvance, TruffleString locale) {
            StringBuilder sb = new StringBuilder();
            sb.append("Flavor=Python");
            sb.append(',');
            sb.append(encoding);
            sb.append(',');
            sb.append(pythonMethod.getTRegexOption());
            if (mustAdvance) {
                sb.append(',');
                sb.append("MustAdvance=true");
            }
            if (locale != null) {
                sb.append(',');
                sb.append("PythonLocale=" + locale.toJavaStringUncached());
            }
            return sb.toString();
        }

        @TruffleBoundary
        public Object compile(Node node, PythonContext context, PythonMethod method, boolean mustAdvance, TruffleString locale) {
            String encoding = isBinary() ? ENCODING_LATIN_1 : ENCODING_UTF_32;
            String options = getTRegexOptions(encoding, method, mustAdvance, locale);
            InteropLibrary lib = InteropLibrary.getUncached();
            Object regexp;
            try {
                Source regexSource = Source.newBuilder("regex", options + '/' + pattern + '/' + flags, "re").mimeType("application/tregex").internal(true).build();
                Object compiledRegex = context.getEnv().parseInternal(regexSource).call();
                if (lib.isNull(compiledRegex)) {
                    regexp = PNone.NONE;
                } else {
                    regexp = compiledRegex;
                }
            } catch (RuntimeException e) {
                throw handleCompilationError(node, e, lib);
            }
            if (isLocaleSensitive()) {
                setLocaleSensitiveRegexp(method, mustAdvance, locale, regexp);
            } else {
                setRegexp(method, mustAdvance, regexp);
            }
            return regexp;
        }

        // No BoundaryCallContext: lookups attribute on a builtin module; constructs builtin
        // exceptions
        private RuntimeException handleCompilationError(Node node, RuntimeException e, InteropLibrary lib) {
            try {
                if (lib.isException(e)) {
                    if (lib.getExceptionType(e) == ExceptionType.PARSE_ERROR) {
                        TruffleString reason = lib.asTruffleString(lib.getExceptionMessage(e)).switchEncodingUncached(TS_ENCODING);
                        if (reason.equalsUncached(T_VALUE_ERROR_UNICODE_FLAG_BYTES_PATTERN, TS_ENCODING) ||
                                        reason.equalsUncached(T_VALUE_ERROR_LOCALE_FLAG_STR_PATTERN, TS_ENCODING) ||
                                        reason.equalsUncached(T_VALUE_ERROR_ASCII_UNICODE_INCOMPATIBLE, TS_ENCODING) ||
                                        reason.equalsUncached(T_VALUE_ERROR_ASCII_LOCALE_INCOMPATIBLE, TS_ENCODING)) {
                            return PRaiseNode.raiseStatic(node, ValueError, reason);
                        } else {
                            SourceSection sourceSection = lib.getSourceLocation(e);
                            int position = sourceSection.getCharIndex();
                            return RaiseRegexErrorNode.executeWithPatternAndPositionUncached(reason, originalPattern, position, node);
                        }
                    }
                }
            } catch (UnsupportedMessageException e1) {
                return CompilerDirectives.shouldNotReachHere();
            }
            // just re-throw
            return e;
        }

        private static final class RegexKey {
            private final PythonMethod pythonMethod;
            private final boolean mustAdvance;
            private final TruffleString pythonLocale;

            RegexKey(PythonMethod pythonMethod, boolean mustAdvance, TruffleString pythonLocale) {
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
                return this.pythonMethod == other.pythonMethod && this.mustAdvance == other.mustAdvance && this.pythonLocale.equalsUncached(other.pythonLocale, TS_ENCODING);
            }

            @Override
            public int hashCode() {
                return Objects.hash(pythonMethod, mustAdvance, pythonLocale);
            }
        }
    }

    @GenerateCached
    @ImportStatic(PythonMethod.class)
    @SuppressWarnings("truffle-inlining")
    public abstract static class TRegexCompileInner extends PNodeWithContext {

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
                            @Shared @Cached("createFor($node)") InteropCallData callData,
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
                return SREModuleBuiltinsFactory.TRegexCallExecNodeGen.BufferToTruffleStringNodeGen.create();
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

    /**
     * There are multiple nested inner nodes used in {@link PatternBuiltins.SplitNode}. The number at the end of
     * each inner node indicates the nesting level.
     * <p>
     * First level: separate specializations for strings ({@link TruffleString}, {@link PString})
     * and bytes-objects ({@link PBytesLike}).
     */
    @GenerateInline
    abstract static class TRegexRESplitInnerNode1 extends Node {

        abstract Object execute(Node inliningTarget, VirtualFrame frame, Object compiledRegex, Object compiledRegexMustAdvance, Object input, int maxsplit,
                        boolean binary, int groupCount);

        @Specialization(guards = "!binary")
        static Object doString(Node inliningTarget, @SuppressWarnings("unused") VirtualFrame frame, Object compiledRegex, Object compiledRegexMustAdvance, Object inputObj, int maxsplit,
                        boolean binary, int groupCount,
                        @Cached CastToTruffleStringNode cast,
                        @Cached @Cached.Exclusive TRegexRESplitInnerNode2 innerNode) {
            TruffleString input = cast.castKnownString(inliningTarget, inputObj);
            return innerNode.execute(inliningTarget, compiledRegex, compiledRegexMustAdvance, input, maxsplit, binary, groupCount);
        }

        @Specialization(guards = "binary")
        static Object doBytes(Node inliningTarget, VirtualFrame frame, Object compiledRegex, Object compiledRegexMustAdvance, Object inputObj, int maxsplit,
                        boolean binary, int groupCount,
                        @Cached TRegexCallExec.BufferToTruffleStringNode bufferToTruffleStringNode,
                        @Cached @Cached.Exclusive TRegexRESplitInnerNode2 innerNode) {
            TruffleString input = bufferToTruffleStringNode.execute(frame, inputObj);
            return innerNode.execute(inliningTarget, compiledRegex, compiledRegexMustAdvance, input, maxsplit, binary, groupCount);
        }
    }

    /**
     * Second level: separate specializations for regexes with and without capture groups.
     */
    @GenerateInline
    abstract static class TRegexRESplitInnerNode2 extends Node {

        abstract Object execute(Node inliningTarget, Object compiledRegex, Object compiledRegexMustAdvance, TruffleString input, int maxsplit,
                        boolean binary, int groupCount);

        @Specialization(guards = "groupCount == 1")
        static Object count1(Node inliningTarget, Object compiledRegex, Object compiledRegexMustAdvance, TruffleString input, int maxsplit, boolean binary, int groupCount,
                        @Cached @Cached.Exclusive TRegexRESplitInnerNode3 innerNode) {
            return innerNode.execute(inliningTarget, compiledRegex, compiledRegexMustAdvance, input, maxsplit, binary, groupCount, false);
        }

        @Specialization(guards = "groupCount > 1")
        static Object count2(Node inliningTarget, Object compiledRegex, Object compiledRegexMustAdvance, TruffleString input, int maxsplit, boolean binary, int groupCount,
                        @Cached @Cached.Exclusive TRegexRESplitInnerNode3 innerNode) {
            return innerNode.execute(inliningTarget, compiledRegex, compiledRegexMustAdvance, input, maxsplit, binary, groupCount, true);
        }
    }

    /**
     * Third level: method implementation.
     */
    @GenerateInline
    abstract static class TRegexRESplitInnerNode3 extends Node {

        abstract Object execute(Node inliningTarget, Object compiledRegex, Object compiledRegexMustAdvance, TruffleString input, int maxsplit, boolean binary, int groupCount,
                        boolean hasCaptureGroups);

        @Specialization
        static Object doString(Node inliningTarget, Object compiledRegex, Object compiledRegexMustAdvance, TruffleString input, int maxsplit, boolean binary, int groupCount,
                        boolean hasCaptureGroups,
                        @Cached TRegexUtil.InvokeExecMethodNode invokeExecMethodNodeMustAdvance,
                        @Cached TRegexUtil.InvokeExecMethodNode invokeExecMethodNode,
                        @Cached TRegexUtil.InteropReadMemberNode readIsMatchNode,
                        @Cached TRegexUtil.InvokeGetGroupBoundariesMethodNode readStartNode,
                        @Cached TRegexUtil.InvokeGetGroupBoundariesMethodNode readEndNode,
                        @Cached TRegexUtil.InvokeGetGroupBoundariesMethodNode readStartNode2,
                        @Cached TRegexUtil.InvokeGetGroupBoundariesMethodNode readEndNode2,
                        @Cached TruffleString.SubstringByteIndexNode substringByteIndexNode,
                        @Cached TruffleString.CopyToByteArrayNode copyToByteArrayNode) {
            CompilerAsserts.partialEvaluationConstant(binary);
            CompilerAsserts.partialEvaluationConstant(hasCaptureGroups);
            TruffleString.Encoding encoding = binary ? TS_ENCODING_BINARY : TS_ENCODING;
            int stringLength = toCodepointIndex(input.byteLength(encoding), binary);
            int pos = 0;
            int n = 0;
            boolean mustAdvance = false;
            ArrayBuilder<Object> result = new ArrayBuilder<>(16);
            while ((maxsplit == 0 || n < maxsplit) && pos <= stringLength) {
                final Object searchResult;
                if (mustAdvance) {
                    searchResult = invokeExecMethodNodeMustAdvance.execute(inliningTarget, compiledRegexMustAdvance, input, pos);
                } else {
                    searchResult = invokeExecMethodNode.execute(inliningTarget, compiledRegex, input, pos);
                }
                if (!TRegexUtil.TRegexResultAccessor.isMatch(searchResult, inliningTarget, readIsMatchNode)) {
                    break;
                }
                n++;
                int start = TRegexUtil.TRegexResultAccessor.captureGroupStart(searchResult, 0, inliningTarget, readStartNode);
                int end = TRegexUtil.TRegexResultAccessor.captureGroupEnd(searchResult, 0, inliningTarget, readEndNode);
                result.add(createSubstring(inliningTarget, input, binary, pos, start, substringByteIndexNode, copyToByteArrayNode));
                if (hasCaptureGroups) {
                    for (int i = 0; i < groupCount - 1; i++) {
                        // using a separate pair of capture group read nodes here, because the first
                        // capture group access may cause a lazy capture group evaluation call
                        // inside TRegex, and we want to avoid that in the inner loop
                        int substringStart = TRegexUtil.TRegexResultAccessor.captureGroupStart(searchResult, i + 1, inliningTarget, readStartNode2);
                        int substringEnd = TRegexUtil.TRegexResultAccessor.captureGroupEnd(searchResult, i + 1, inliningTarget, readEndNode2);
                        result.add(createSubstringSplit(inliningTarget, input, binary, substringStart, substringEnd, substringByteIndexNode, copyToByteArrayNode));
                    }
                }
                pos = end;
                mustAdvance = start == end;
            }
            result.add(createSubstring(inliningTarget, input, binary, pos, stringLength, substringByteIndexNode, copyToByteArrayNode));
            return PFactory.createList(PythonLanguage.get(inliningTarget), result.toObjectArray());
        }

        private static Object createSubstringSplit(Node inliningTarget, TruffleString input, boolean binary, int substringStart, int substringEnd,
                        TruffleString.SubstringByteIndexNode substringByteIndexNode,
                        TruffleString.CopyToByteArrayNode copyToByteArrayNode) {
            CompilerAsserts.partialEvaluationConstant(binary);
            if (substringStart < 0) {
                return PNone.NONE;
            }
            return createSubstring(inliningTarget, input, binary, substringStart, substringEnd, substringByteIndexNode, copyToByteArrayNode);
        }
    }

    private static Object createSubstring(Node inliningTarget, TruffleString input, boolean binary, int substringStart, int substringEnd,
                    TruffleString.SubstringByteIndexNode substringByteIndexNode,
                    TruffleString.CopyToByteArrayNode copyToByteArrayNode) {
        CompilerAsserts.partialEvaluationConstant(binary);
        assert substringStart >= 0 && substringEnd >= substringStart;
        int byteIndexStart = toByteIndex(substringStart, binary);
        int byteLength = toByteIndex(substringEnd - substringStart, binary);
        if (binary) {
            byte[] bytes = new byte[byteLength];
            copyToByteArrayNode.execute(input, byteIndexStart, bytes, 0, byteLength, TS_ENCODING_BINARY);
            return PFactory.createBytes(PythonLanguage.get(inliningTarget), bytes);
        } else {
            return substringByteIndexNode.execute(input, byteIndexStart, byteLength, TS_ENCODING, false);
        }
    }

    /**
     * There are multiple nested inner nodes used in {@link PatternBuiltins.FindAllNode}. The number at the end
     * of each inner node indicates the nesting level.
     * <p>
     * First level: separate specializations for strings ({@link TruffleString}, {@link PString})
     * and bytes-objects ({@link PBytesLike}).
     */
    @GenerateInline
    abstract static class TRegexREFindAllInnerNode1 extends Node {

        abstract Object execute(Node inliningTarget, VirtualFrame frame, Object compiledRegex, Object compiledRegexMustAdvance, Object input, int pos, int endpos,
                        boolean binary, int groupCount);

        @Specialization(guards = "!binary")
        static Object doString(Node inliningTarget, @SuppressWarnings("unused") VirtualFrame frame, Object compiledRegex, Object compiledRegexMustAdvance, Object inputObj, int pos, int endpos,
                        boolean binary, int groupCount,
                        @Cached CastToTruffleStringNode cast,
                        @Cached @Cached.Exclusive TRegexREFindAllInnerNode2 innerNode) {
            TruffleString input = cast.castKnownString(inliningTarget, inputObj);
            return innerNode.execute(inliningTarget, compiledRegex, compiledRegexMustAdvance, input, pos, endpos, binary, groupCount);
        }

        @Specialization(guards = "binary")
        static Object doBytes(Node inliningTarget, VirtualFrame frame, Object compiledRegex, Object compiledRegexMustAdvance, Object inputObj, int pos, int endpos,
                        boolean binary, int groupCount,
                        @Cached TRegexCallExec.BufferToTruffleStringNode bufferToTruffleStringNode,
                        @Cached @Cached.Exclusive TRegexREFindAllInnerNode2 innerNode) {
            TruffleString input = bufferToTruffleStringNode.execute(frame, inputObj);
            return innerNode.execute(inliningTarget, compiledRegex, compiledRegexMustAdvance, input, pos, endpos, binary, groupCount);
        }
    }

    /**
     * Second level: separate specializations for regexes without capture groups, a single capture
     * group, and multiple capture groups.
     */
    @GenerateInline
    abstract static class TRegexREFindAllInnerNode2 extends Node {

        abstract Object execute(Node inliningTarget, Object compiledRegex, Object compiledRegexMustAdvance, TruffleString input, int pos, int endpos,
                        boolean binary, int groupCount);

        @Specialization(guards = "groupCount == 1")
        static Object count1(Node inliningTarget, Object compiledRegex, Object compiledRegexMustAdvance, TruffleString input, int pos, int endpos, boolean binary, int groupCount,
                        @Cached @Cached.Exclusive TRegexREFindAllInnerNode3 innerNode) {
            return innerNode.execute(inliningTarget, compiledRegex, compiledRegexMustAdvance, input, pos, endpos, binary, groupCount, false);
        }

        @Specialization(guards = "groupCount == 2")
        static Object count2(Node inliningTarget, Object compiledRegex, Object compiledRegexMustAdvance, TruffleString input, int pos, int endpos, boolean binary, int groupCount,
                        @Cached @Cached.Exclusive TRegexREFindAllInnerNode3 innerNode) {
            return innerNode.execute(inliningTarget, compiledRegex, compiledRegexMustAdvance, input, pos, endpos, binary, groupCount, false);
        }

        @Specialization(guards = "groupCount > 2")
        static Object createTuples(Node inliningTarget, Object compiledRegex, Object compiledRegexMustAdvance, TruffleString input, int pos, int endpos, boolean binary, int groupCount,
                        @Cached @Cached.Exclusive TRegexREFindAllInnerNode3 innerNode) {
            return innerNode.execute(inliningTarget, compiledRegex, compiledRegexMustAdvance, input, pos, endpos, binary, groupCount, true);
        }
    }

    /**
     * Third level: method implementation.
     */
    @GenerateInline
    abstract static class TRegexREFindAllInnerNode3 extends Node {

        abstract Object execute(Node inliningTarget, Object compiledRegex, Object compiledRegexMustAdvance, TruffleString input, int pos, int endpos, boolean binary, int groupCount,
                        boolean createTuples);

        @Specialization
        static Object doString(Node inliningTarget, Object compiledRegex, Object compiledRegexMustAdvance, TruffleString input, int posArg, int endposArg, boolean binary, int groupCount,
                        boolean createTuples,
                        @Cached TRegexUtil.InvokeExecMethodWithMaxIndexNode invokeExecMethodNodeMustAdvance,
                        @Cached TRegexUtil.InvokeExecMethodWithMaxIndexNode invokeExecMethodNode,
                        @Cached TRegexUtil.InteropReadMemberNode readIsMatchNode,
                        @Cached TRegexUtil.InvokeGetGroupBoundariesMethodNode readStartNode,
                        @Cached TRegexUtil.InvokeGetGroupBoundariesMethodNode readEndNode,
                        @Cached TRegexUtil.InvokeGetGroupBoundariesMethodNode readStartNode2,
                        @Cached TRegexUtil.InvokeGetGroupBoundariesMethodNode readEndNode2,
                        @Cached TruffleString.SubstringByteIndexNode substringByteIndexNode,
                        @Cached TruffleString.CopyToByteArrayNode copyToByteArrayNode) {
            CompilerAsserts.partialEvaluationConstant(binary);
            CompilerAsserts.partialEvaluationConstant(createTuples);
            TruffleString.Encoding encoding = binary ? TS_ENCODING_BINARY : TS_ENCODING;
            int stringLength = toCodepointIndex(input.byteLength(encoding), binary);
            int endpos = endposArg < 0 ? 0 : Math.min(endposArg, stringLength);
            int pos = posArg < 0 ? 0 : Math.min(posArg, endpos);
            boolean mustAdvance = false;
            ArrayBuilder<Object> result = new ArrayBuilder<>(16);
            while (pos <= endpos) {
                final Object searchResult;
                if (mustAdvance) {
                    searchResult = invokeExecMethodNodeMustAdvance.execute(inliningTarget, compiledRegexMustAdvance, input, pos, endpos);
                } else {
                    searchResult = invokeExecMethodNode.execute(inliningTarget, compiledRegex, input, pos, endpos);
                }
                if (!TRegexUtil.TRegexResultAccessor.isMatch(searchResult, inliningTarget, readIsMatchNode)) {
                    break;
                }
                int start = TRegexUtil.TRegexResultAccessor.captureGroupStart(searchResult, 0, inliningTarget, readStartNode);
                int end = TRegexUtil.TRegexResultAccessor.captureGroupEnd(searchResult, 0, inliningTarget, readEndNode);
                final Object resultEntry;
                if (createTuples) {
                    Object[] tuple = new Object[groupCount - 1];
                    for (int i = 0; i < groupCount - 1; i++) {
                        int substringStart = TRegexUtil.TRegexResultAccessor.captureGroupStart(searchResult, i + 1, inliningTarget, readStartNode2);
                        int substringEnd = TRegexUtil.TRegexResultAccessor.captureGroupEnd(searchResult, i + 1, inliningTarget, readEndNode2);
                        tuple[i] = createSubstringFindAll(inliningTarget, input, binary, substringStart, substringEnd, substringByteIndexNode, copyToByteArrayNode);
                    }
                    resultEntry = PFactory.createTuple(PythonLanguage.get(inliningTarget), tuple);
                } else {
                    CompilerAsserts.partialEvaluationConstant(groupCount);
                    final int substringStart;
                    final int substringEnd;
                    if (groupCount == 1) {
                        substringStart = start;
                        substringEnd = end;
                    } else {
                        assert groupCount == 2;
                        substringStart = TRegexUtil.TRegexResultAccessor.captureGroupStart(searchResult, 1, inliningTarget, readStartNode2);
                        substringEnd = TRegexUtil.TRegexResultAccessor.captureGroupEnd(searchResult, 1, inliningTarget, readEndNode2);
                    }
                    resultEntry = createSubstringFindAll(inliningTarget, input, binary, substringStart, substringEnd, substringByteIndexNode, copyToByteArrayNode);
                }
                result.add(resultEntry);
                pos = end;
                mustAdvance = start == end;
            }
            return PFactory.createList(PythonLanguage.get(inliningTarget), result.toObjectArray());
        }

        private static Object createSubstringFindAll(Node inliningTarget, TruffleString input, boolean binary, int substringStart, int substringEnd,
                        TruffleString.SubstringByteIndexNode substringByteIndexNode,
                        TruffleString.CopyToByteArrayNode copyToByteArrayNode) {
            CompilerAsserts.partialEvaluationConstant(binary);
            if (substringStart < 0) {
                if (binary) {
                    return PFactory.createEmptyBytes(PythonLanguage.get(inliningTarget));
                } else {
                    return TS_ENCODING.getEmpty();
                }
            }
            return createSubstring(inliningTarget, input, binary, substringStart, substringEnd, substringByteIndexNode, copyToByteArrayNode);
        }
    }

    /**
     * There are multiple nested inner nodes used in {@link PatternBuiltins.SubNode}. The number at the end of
     * each inner node indicates the nesting level.
     * <p>
     * First level: separate specializations for strings ({@link TruffleString}, {@link PString})
     * and bytes-objects ({@link PBytesLike}).
     */
    @GenerateInline
    abstract static class TRegexRESubnInnerNode1 extends Node {

        abstract Object execute(Node inliningTarget, VirtualFrame frame, PythonObject pattern, Object compiledRegex, Object compiledRegexMustAdvance, Object replacement, Object input, int count,
                        boolean binary, boolean isCallable, boolean returnTuple);

        @Specialization(guards = "!binary")
        static Object doString(Node inliningTarget, VirtualFrame frame, PythonObject pattern, Object compiledRegex, Object compiledRegexMustAdvance, Object replacement, Object inputObj, int count,
                        @SuppressWarnings("unused") boolean binary,
                        boolean isCallable,
                        boolean returnTuple,
                        @Cached CastToTruffleStringNode cast,
                        @Cached @Cached.Exclusive TRegexRESubnInnerNode2 innerNode) {
            TruffleString input = cast.castKnownString(inliningTarget, inputObj);
            assert TS_ENCODING == TruffleString.Encoding.UTF_32 : "remove the >> 2 when switching to UTF-8";
            int stringLength = input.byteLength(TS_ENCODING) >> 2;
            TruffleStringBuilderUTF32 result = TruffleStringBuilder.createUTF32(Math.max(32, stringLength));
            return innerNode.execute(inliningTarget, frame, pattern, compiledRegex, compiledRegexMustAdvance, replacement, input, inputObj, count, binary, isCallable, returnTuple, stringLength,
                            result);
        }

        @Specialization(guards = "binary")
        static Object doBytes(Node inliningTarget, VirtualFrame frame, PythonObject pattern, Object compiledRegex, Object compiledRegexMustAdvance, Object replacement, Object inputObj, int count,
                        @SuppressWarnings("unused") boolean binary,
                        boolean isCallable,
                        boolean returnTuple,
                        @Cached TRegexCallExec.BufferToTruffleStringNode bufferToTruffleStringNode,
                        @Cached @Cached.Exclusive TRegexRESubnInnerNode2 innerNode) {
            TruffleString input = bufferToTruffleStringNode.execute(frame, inputObj);
            int stringLength = input.byteLength(TS_ENCODING_BINARY);
            TruffleStringBuilder result = TruffleStringBuilder.create(TS_ENCODING_BINARY, Math.max(32, stringLength));
            return innerNode.execute(inliningTarget, frame, pattern, compiledRegex, compiledRegexMustAdvance, replacement, input, inputObj, count, binary, isCallable, returnTuple, stringLength,
                            result);
        }
    }

    /**
     * Second level: Separate specializations for callable and non-callable replacement objects.
     */
    @GenerateInline
    abstract static class TRegexRESubnInnerNode2 extends Node {

        abstract Object execute(Node inliningTarget, VirtualFrame frame, PythonObject pattern, Object compiledRegex, Object compiledRegexMustAdvance, Object replacement, TruffleString input,
                        Object originalInput,
                        int count,
                        boolean binary,
                        boolean isCallable,
                        boolean returnTuple,
                        int stringLength,
                        TruffleStringBuilder result);

        @Specialization(guards = "isCallable")
        static Object doCallable(Node inliningTarget, VirtualFrame frame, PythonObject pattern, Object compiledRegex, Object compiledRegexMustAdvance, Object replacement, TruffleString input,
                        Object originalInput,
                        int count,
                        boolean binary,
                        @SuppressWarnings("unused") boolean isCallable,
                        boolean returnTuple,
                        int stringLength,
                        TruffleStringBuilder result,
                        @Cached TRegexUtil.InvokeExecMethodNode invokeExecMethodNodeMustAdvance,
                        @Cached TRegexUtil.InvokeExecMethodNode invokeExecMethodNode,
                        @Cached TRegexUtil.InteropReadMemberNode readIsMatchNode,
                        @Cached TRegexUtil.InvokeGetGroupBoundariesMethodNode readStartNode,
                        @Cached TRegexUtil.InvokeGetGroupBoundariesMethodNode readEndNode,
                        @Cached TruffleStringBuilder.AppendSubstringByteIndexNode appendSubstringNode,
                        @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode,
                        @Cached TruffleString.CopyToByteArrayNode copyToByteArrayNode,
                        @Cached CallNode callNode,
                        @Cached @Cached.Exclusive CastToTruffleStringNode cast,
                        @Cached @Cached.Exclusive TRegexCallExec.BufferToTruffleStringNode bufferToTruffleStringNode,
                        @Cached MatchNodes.NewNode newMatchNode) {
            int n = 0;
            int pos = 0;
            boolean mustAdvance = false;
            while ((count == 0 || n < count) && pos <= stringLength) {
                final Object searchResult;
                if (mustAdvance) {
                    searchResult = invokeExecMethodNodeMustAdvance.execute(inliningTarget, compiledRegexMustAdvance, input, pos);
                } else {
                    searchResult = invokeExecMethodNode.execute(inliningTarget, compiledRegex, input, pos);
                }
                if (!TRegexUtil.TRegexResultAccessor.isMatch(searchResult, inliningTarget, readIsMatchNode)) {
                    break;
                }
                n++;
                int start = TRegexUtil.TRegexResultAccessor.captureGroupStart(searchResult, 0, inliningTarget, readStartNode);
                int end = TRegexUtil.TRegexResultAccessor.captureGroupEnd(searchResult, 0, inliningTarget, readEndNode);
                appendSubstringNode.execute(result, input, toByteIndex(pos, binary), toByteIndex(start - pos, binary));

                final Object match = newMatchNode.execute(frame, inliningTarget, (PPattern) pattern, searchResult, originalInput, pos, end);
                Object callResult = callNode.execute(frame, replacement, match);

                if (callResult != PNone.NONE) {
                    if (binary) {
                        appendStringNode.execute(result, bufferToTruffleStringNode.execute(frame, callResult));
                    } else {
                        appendStringNode.execute(result, cast.castKnownString(inliningTarget, callResult));
                    }
                }
                pos = end;
                mustAdvance = start == end;
            }
            final TruffleString resultString;
            if (n == 0) {
                resultString = input;
            } else {
                appendSubstringNode.execute(result, input, toByteIndex(pos, binary), toByteIndex(stringLength - pos, binary));
                resultString = toStringNode.execute(result, binary);
            }
            final Object resultObject;
            if (binary) {
                resultObject = PFactory.createBytes(PythonLanguage.get(inliningTarget), copyToByteArrayNode.execute(resultString, TS_ENCODING_BINARY));
            } else {
                resultObject = resultString;
            }
            if (returnTuple) {
                return PFactory.createTuple(PythonLanguage.get(inliningTarget), new Object[]{resultObject, n});
            } else {
                return resultObject;
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!isCallable", "binary"})
        static Object doBinary(Node inliningTarget, VirtualFrame frame, PythonObject pattern, Object compiledRegex, Object compiledRegexMustAdvance, Object replacementObj, TruffleString input,
                        Object originalInput,
                        int count,
                        boolean binary,
                        boolean isCallable,
                        boolean returnTuple,
                        int stringLength,
                        TruffleStringBuilder result,
                        @Cached @Shared RECheckInputTypeNode checkInputTypeNode,
                        @Cached @Cached.Exclusive TRegexCallExec.BufferToTruffleStringNode bufferToTruffleStringNode,
                        @Cached @Cached.Exclusive TRegexRESubnInnerNode3 innerNode) {
            checkInputTypeNode.execute(frame, replacementObj, binary);
            TruffleString replacement = bufferToTruffleStringNode.execute(frame, replacementObj);
            return innerNode.execute(inliningTarget, frame, compiledRegex, compiledRegexMustAdvance, replacement, input, count, binary, returnTuple, stringLength, result);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!isCallable", "!binary"})
        static Object doString(Node inliningTarget, VirtualFrame frame, PythonObject pattern, Object compiledRegex, Object compiledRegexMustAdvance, Object replacementObj, TruffleString input,
                        Object originalInput,
                        int count,
                        boolean binary,
                        boolean isCallable,
                        boolean returnTuple,
                        int stringLength,
                        TruffleStringBuilder result,
                        @Cached @Shared RECheckInputTypeNode checkInputTypeNode,
                        @Cached @Cached.Exclusive CastToTruffleStringNode cast,
                        @Cached @Cached.Exclusive TRegexRESubnInnerNode3 innerNode) {
            checkInputTypeNode.execute(frame, replacementObj, binary);
            TruffleString replacement = cast.castKnownString(inliningTarget, replacementObj);
            return innerNode.execute(inliningTarget, frame, compiledRegex, compiledRegexMustAdvance, replacement, input, count, binary, returnTuple, stringLength, result);
        }

    }

    /**
     * Third level: method implementation and caching of non-callable replacement objects.
     */
    @GenerateInline
    abstract static class TRegexRESubnInnerNode3 extends Node {

        private static final int UNROLL_MAX = 4;

        abstract Object execute(Node inliningTarget, VirtualFrame frame, Object compiledRegex, Object compiledRegexMustAdvance, TruffleString replacement, TruffleString input,
                        int count,
                        boolean binary,
                        boolean returnTuple,
                        int stringLength,
                        TruffleStringBuilder result);

        @SuppressWarnings("unused")
        @Specialization(guards = "replacement == cachedReplacement", limit = "2")
        static Object doCached(Node inliningTarget, VirtualFrame frame, Object compiledRegex, Object compiledRegexMustAdvance, TruffleString replacement, TruffleString input,
                        int count,
                        boolean binary,
                        boolean returnTuple,
                        int stringLength,
                        TruffleStringBuilder result,
                        @Cached("replacement") TruffleString cachedReplacement,
                        @Cached @Shared ParseReplacementNode parseReplacementNode,
                        @Cached("parseReplacementNode.execute(inliningTarget, frame, compiledRegex, replacement, binary)") ParsedReplacement cachedParsedReplacement,
                        @Cached @Shared TRegexUtil.InvokeExecMethodNode invokeExecMethodNodeMustAdvance,
                        @Cached @Shared TRegexUtil.InvokeExecMethodNode invokeExecMethodNode,
                        @Cached @Shared TRegexUtil.InteropReadMemberNode readIsMatchNode,
                        @Cached @Shared TRegexUtil.InvokeGetGroupBoundariesMethodNode readStartNode,
                        @Cached @Shared TRegexUtil.InvokeGetGroupBoundariesMethodNode readEndNode,
                        @Cached @Shared TruffleStringBuilder.AppendCodePointNode appendCodePointNode,
                        @Cached @Shared TruffleStringBuilder.AppendSubstringByteIndexNode appendSubstringNode,
                        @Cached @Shared TruffleStringBuilder.ToStringNode toStringNode,
                        @Cached @Shared TruffleString.CopyToByteArrayNode copyToByteArrayNode) {
            return doReplace(compiledRegex, compiledRegexMustAdvance, replacement, input, count, binary, returnTuple, stringLength, result, cachedParsedReplacement,
                            inliningTarget,
                            invokeExecMethodNodeMustAdvance,
                            invokeExecMethodNode,
                            readIsMatchNode,
                            readStartNode,
                            readEndNode,
                            appendCodePointNode,
                            appendSubstringNode,
                            toStringNode,
                            copyToByteArrayNode);
        }

        @Specialization(replaces = "doCached")
        static Object doOnTheFly(Node inliningTarget, VirtualFrame frame, Object compiledRegex, Object compiledRegexMustAdvance, TruffleString replacement, TruffleString input,
                        int count,
                        boolean binary,
                        boolean returnTuple,
                        int stringLength,
                        TruffleStringBuilder result,
                        @Cached @Shared ParseReplacementNode parseReplacementNode,
                        @Cached @Shared TRegexUtil.InvokeExecMethodNode invokeExecMethodNodeMustAdvance,
                        @Cached @Shared TRegexUtil.InvokeExecMethodNode invokeExecMethodNode,
                        @Cached @Shared TRegexUtil.InteropReadMemberNode readIsMatchNode,
                        @Cached @Shared TRegexUtil.InvokeGetGroupBoundariesMethodNode readStartNode,
                        @Cached @Shared TRegexUtil.InvokeGetGroupBoundariesMethodNode readEndNode,
                        @Cached @Shared TruffleStringBuilder.AppendCodePointNode appendCodePointNode,
                        @Cached @Shared TruffleStringBuilder.AppendSubstringByteIndexNode appendSubstringNode,
                        @Cached @Shared TruffleStringBuilder.ToStringNode toStringNode,
                        @Cached @Shared TruffleString.CopyToByteArrayNode copyToByteArrayNode) {
            ParsedReplacement parsedReplacement = parseReplacementNode.execute(inliningTarget, frame, compiledRegex, replacement, binary);
            return doReplace(compiledRegex, compiledRegexMustAdvance, replacement, input, count, binary, returnTuple, stringLength, result, parsedReplacement,
                            inliningTarget,
                            invokeExecMethodNodeMustAdvance,
                            invokeExecMethodNode,
                            readIsMatchNode,
                            readStartNode,
                            readEndNode,
                            appendCodePointNode,
                            appendSubstringNode,
                            toStringNode,
                            copyToByteArrayNode);
        }

        private static Object doReplace(Object compiledRegex, Object compiledRegexMustAdvance, TruffleString replacement, TruffleString input, int count, boolean binary, boolean returnTuple,
                        int stringLength,
                        TruffleStringBuilder result,
                        ParsedReplacement parsedReplacement,
                        Node inliningTarget,
                        TRegexUtil.InvokeExecMethodNode invokeExecMethodNodeMustAdvance,
                        TRegexUtil.InvokeExecMethodNode invokeExecMethodNode,
                        TRegexUtil.InteropReadMemberNode readIsMatchNode,
                        TRegexUtil.InvokeGetGroupBoundariesMethodNode readStartNode,
                        TRegexUtil.InvokeGetGroupBoundariesMethodNode readEndNode,
                        TruffleStringBuilder.AppendCodePointNode appendCodePointNode,
                        TruffleStringBuilder.AppendSubstringByteIndexNode appendSubstringNode,
                        TruffleStringBuilder.ToStringNode toStringNode,
                        TruffleString.CopyToByteArrayNode copyToByteArrayNode) {
            int n = 0;
            int pos = 0;
            boolean mustAdvance = false;
            while ((count == 0 || n < count) && pos < stringLength) {
                final Object searchResult;
                if (mustAdvance) {
                    searchResult = invokeExecMethodNodeMustAdvance.execute(inliningTarget, compiledRegexMustAdvance, input, pos);
                } else {
                    searchResult = invokeExecMethodNode.execute(inliningTarget, compiledRegex, input, pos);
                }
                if (!TRegexUtil.TRegexResultAccessor.isMatch(searchResult, inliningTarget, readIsMatchNode)) {
                    break;
                }
                n++;
                int start = TRegexUtil.TRegexResultAccessor.captureGroupStart(searchResult, 0, inliningTarget, readStartNode);
                int end = TRegexUtil.TRegexResultAccessor.captureGroupEnd(searchResult, 0, inliningTarget, readEndNode);
                appendSubstringNode.execute(result, input, toByteIndex(pos, binary), toByteIndex(start - pos, binary));
                if (CompilerDirectives.isPartialEvaluationConstant(parsedReplacement) && parsedReplacement.size() <= UNROLL_MAX) {
                    applyReplacementUnrolled(parsedReplacement, result, replacement, input, searchResult, binary, inliningTarget, readStartNode, readEndNode, appendCodePointNode, appendSubstringNode);
                } else {
                    applyReplacement(parsedReplacement, result, replacement, input, searchResult, binary, inliningTarget, readStartNode, readEndNode, appendCodePointNode, appendSubstringNode);
                }
                pos = end;
                mustAdvance = start == end;
            }
            final TruffleString resultString;
            if (n == 0) {
                resultString = input;
            } else {
                appendSubstringNode.execute(result, input, toByteIndex(pos, binary), toByteIndex(stringLength - pos, binary));
                resultString = toStringNode.execute(result, binary);
            }
            final Object resultObject;
            if (binary) {
                resultObject = PFactory.createBytes(PythonLanguage.get(inliningTarget), copyToByteArrayNode.execute(resultString, TS_ENCODING_BINARY));
            } else {
                resultObject = resultString;
            }
            if (returnTuple) {
                return PFactory.createTuple(PythonLanguage.get(inliningTarget), new Object[]{resultObject, n});
            } else {
                return resultObject;
            }
        }

        @ExplodeLoop
        private static void applyReplacementUnrolled(ParsedReplacement parsedReplacement, TruffleStringBuilder result, TruffleString replacement, TruffleString string,
                        Object searchResult,
                        boolean binary,
                        Node inliningTarget,
                        TRegexUtil.InvokeGetGroupBoundariesMethodNode readStartNode,
                        TRegexUtil.InvokeGetGroupBoundariesMethodNode readEndNode,
                        TruffleStringBuilder.AppendCodePointNode appendCodePointNode,
                        TruffleStringBuilder.AppendSubstringByteIndexNode appendSubstringByteIndexNode) {
            CompilerAsserts.partialEvaluationConstant(parsedReplacement);
            int size = parsedReplacement.size();
            CompilerAsserts.partialEvaluationConstant(size);
            for (int i = 0; i < size; i++) {
                parsedReplacement.apply(i, replacement, string, result, binary, searchResult, inliningTarget, readStartNode, readEndNode, appendCodePointNode, appendSubstringByteIndexNode);
            }
        }

        private static void applyReplacement(ParsedReplacement parsedReplacement, TruffleStringBuilder result, TruffleString replacement, TruffleString string,
                        Object searchResult,
                        boolean binary,
                        Node inliningTarget,
                        TRegexUtil.InvokeGetGroupBoundariesMethodNode readStartNode,
                        TRegexUtil.InvokeGetGroupBoundariesMethodNode readEndNode,
                        TruffleStringBuilder.AppendCodePointNode appendCodePointNode,
                        TruffleStringBuilder.AppendSubstringByteIndexNode appendSubstringByteIndexNode) {
            int size = parsedReplacement.size();
            for (int i = 0; i < size; i++) {
                parsedReplacement.apply(i, replacement, string, result, binary, searchResult, inliningTarget, readStartNode, readEndNode, appendCodePointNode, appendSubstringByteIndexNode);
            }
        }
    }

    static final class ParsedReplacement {

        private static final ParsedReplacement EMPTY = new ParsedReplacement(new int[]{});

        private static final int TOKEN_KIND_CODEPOINT = -1;
        private static final int TOKEN_KIND_GROUP_REF = -2;

        @CompilationFinal(dimensions = 1) private final int[] tokens;

        private ParsedReplacement(int[] tokens) {
            this.tokens = tokens;
        }

        private int size() {
            return tokens.length >> 1;
        }

        private void apply(int iToken, TruffleString replacement, TruffleString input, TruffleStringBuilder result, boolean binary, Object searchResult,
                        Node inliningTarget,
                        TRegexUtil.InvokeGetGroupBoundariesMethodNode readStartNode,
                        TRegexUtil.InvokeGetGroupBoundariesMethodNode readEndNode,
                        TruffleStringBuilder.AppendCodePointNode appendCodePointNode,
                        TruffleStringBuilder.AppendSubstringByteIndexNode appendSubstringByteIndexNode) {
            int tokenPart0 = tokens[iToken << 1];
            int tokenPart1 = tokens[(iToken << 1) + 1];
            if (tokenPart0 == TOKEN_KIND_CODEPOINT) {
                appendCodePointNode.execute(result, tokenPart1);
            } else {
                final int start;
                final int length;
                final TruffleString s;
                if (tokenPart0 == TOKEN_KIND_GROUP_REF) {
                    start = toByteIndex(TRegexUtil.TRegexResultAccessor.captureGroupStart(searchResult, tokenPart1, inliningTarget, readStartNode), binary);
                    if (start < 0) {
                        return;
                    }
                    length = toByteIndex(TRegexUtil.TRegexResultAccessor.captureGroupEnd(searchResult, tokenPart1, inliningTarget, readEndNode), binary) - start;
                    s = input;
                } else {
                    start = tokenPart0;
                    length = tokenPart1;
                    s = replacement;
                }
                appendSubstringByteIndexNode.execute(result, s, start, length);
            }
        }

        private static final class Builder {
            private final IntArrayBuilder tokens = new IntArrayBuilder();

            void codepoint(int codepoint) {
                tokens.add(TOKEN_KIND_CODEPOINT);
                tokens.add(codepoint);
            }

            void literal(int fromByteIndex, int toByteIndex) {
                tokens.add(fromByteIndex);
                tokens.add(toByteIndex - fromByteIndex);
            }

            void groupReference(int groupNumber) {
                tokens.add(TOKEN_KIND_GROUP_REF);
                tokens.add(groupNumber);
            }

            ParsedReplacement build() {
                return new ParsedReplacement(tokens.toArray());
            }
        }
    }

    @GenerateInline(false) // Only for errors
    @GenerateUncached
    abstract static class RaiseRegexErrorNode extends Node {
        public final PException execute(VirtualFrame frame, TruffleString message, Object pattern, int position) {
            return executeWithPatternAndPosition(frame, message, pattern, position);
        }

        public final PException executeFormatted(VirtualFrame frame, TruffleString message, Object pattern, int position, Object... formatArgs) {
            return execute(frame, doFormat(message, formatArgs), pattern, position);
        }

        @TruffleBoundary
        private static TruffleString doFormat(TruffleString message, Object[] formatArgs) {
            return TruffleString.fromJavaStringUncached(ErrorMessageFormatter.format(message, formatArgs), TS_ENCODING);
        }

        public abstract PException executeWithPatternAndPosition(VirtualFrame frame, TruffleString message, Object pattern, Object position);

        public static PException executeWithPatternAndPositionUncached(TruffleString message, Object pattern, Object position, Node location) {
            return createAndRaise(null, message, pattern, position, location, PythonContext.get(location), ReadAttributeFromModuleNode.getUncached(), CallNode.getUncached());
        }

        @Specialization
        static PException createAndRaise(VirtualFrame frame, TruffleString message, Object pattern, Object position,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @Cached ReadAttributeFromModuleNode readAttribute,
                        @Cached CallNode callNode) {
            PythonModule module = context.lookupBuiltinModule(T__SRE);
            Object errorType = readAttribute.execute(module, T_ERROR);
            assert !(errorType instanceof PNone);
            Object exception = callNode.execute(frame, errorType, message, pattern, position);
            throw PRaiseNode.raiseExceptionObjectStatic(inliningTarget, exception);
        }
    }

    @GenerateInline
    abstract static class ParseReplacementNode extends Node {

        abstract ParsedReplacement execute(Node inliningTarget, VirtualFrame frame, Object tregexCompiledRegex, TruffleString replacement, boolean binary);

        @Specialization
        static ParsedReplacement parseReplacement(Node inliningTarget, VirtualFrame frame, Object tregexCompiledRegex, TruffleString replacement, boolean binary,
                        @Cached TruffleString.ByteIndexOfCodePointNode indexOfNode,
                        @Cached TruffleString.CodePointAtByteIndexNode codePointAtByteIndexNode,
                        @Cached TruffleString.SubstringByteIndexNode substringByteIndexNode,
                        @Cached TruffleString.GetCodeRangeNode getCodeRangeNode,
                        @Cached TRegexUtil.InteropReadMemberNode readGroupCountNode,
                        @Cached TRegexUtil.InteropReadMemberNode readNamedGroupsNode,
                        @CachedLibrary(limit = "3") InteropLibrary genericInteropLib,
                        @Cached PRaiseNode raiseNode,
                        @Cached RaiseRegexErrorNode raiseRegexErrorNode,
                        @Cached StringUtils.IsIdentifierNode isIdentifierNode,
                        @Cached InlinedBranchProfile errorProfile) {
            CompilerAsserts.partialEvaluationConstant(binary);
            assert TS_ENCODING == TruffleString.Encoding.UTF_32 : "replace codepointLengthAscii with 1 when switching to UTF-8";
            int codepointLengthAscii = binary ? 1 : 4;
            TruffleString.Encoding encoding = binary ? TS_ENCODING_BINARY : TS_ENCODING;
            ParsedReplacement.Builder builder = new ParsedReplacement.Builder();
            int length = replacement.byteLength(encoding);
            int numberOfCaptureGroups = TRegexUtil.TRegexCompiledRegexAccessor.groupCount(tregexCompiledRegex, inliningTarget, readGroupCountNode);
            int lastPos = 0;
            int lastLiteralPos = 0;
            while (lastPos < length) {
                int backslashPos = indexOfNode.execute(replacement, '\\', lastPos, length, encoding);
                builder.literal(lastLiteralPos, backslashPos < 0 ? length : backslashPos);
                if (backslashPos < 0) {
                    return builder.build();
                }
                int nextCPPos = backslashPos + codepointLengthAscii;
                if (nextCPPos >= length) {
                    throw raiseRegexErrorNode.execute(frame, BAD_ESCAPE_END_OF_STRING, replacement, toCodepointIndex(length, binary));
                }
                int firstCodepoint = codePointAtByteIndexNode.execute(replacement, nextCPPos, encoding);
                nextCPPos += codepointLengthAscii;
                int secondCodepoint = nextCPPos < length ? codePointAtByteIndexNode.execute(replacement, nextCPPos, encoding) : -1;
                if (firstCodepoint == 'g') {
                    if (secondCodepoint != '<') {
                        throw raiseRegexErrorNode.execute(frame, MISSING_LEFT_ANGLE_BRACKET, replacement, toCodepointIndex(nextCPPos, binary));
                    }
                    int nameStartPos = nextCPPos + codepointLengthAscii;
                    int nameEndPos = 0;
                    if (nameStartPos >= length || (nameEndPos = indexOfNode.execute(replacement, '>', nameStartPos, length, encoding)) < 0 || nameEndPos == nameStartPos) {
                        errorProfile.enter(inliningTarget);
                        throw raiseRegexErrorNode.execute(frame, nameStartPos >= length || nameEndPos == nameStartPos ? MISSING_GROUP_NAME : MISSING_RIGHT_ANGLE_BRACKET, replacement,
                                        toCodepointIndex(nameStartPos, binary));
                    }
                    int nameLength = nameEndPos - nameStartPos;
                    assert nameLength > 0;
                    TruffleString name = substringByteIndexNode.execute(replacement, nameStartPos, nameLength, encoding, true);
                    int groupNumber = -1;
                    boolean ascii = getCodeRangeNode.execute(name, encoding) == TruffleString.CodeRange.ASCII;
                    if (ascii) {
                        groupNumber = 0;
                        for (int i = 0; i < nameLength; i += codepointLengthAscii) {
                            int d = codePointAtByteIndexNode.execute(name, i, encoding);
                            if (isDecimalDigit(d)) {
                                groupNumber = (groupNumber * 10) + digitValue(d);
                            } else {
                                groupNumber = -1;
                                break;
                            }
                            if (groupNumber >= numberOfCaptureGroups) {
                                errorProfile.enter(inliningTarget);
                                throw raiseRegexErrorNode.executeFormatted(frame, INVALID_GROUP_REFERENCE, replacement, toCodepointIndex(nameStartPos, binary), name);
                            }
                        }
                    }
                    if (groupNumber < 0) {
                        if (!isIdentifierNode.execute(inliningTarget, name) || binary && !ascii) {
                            errorProfile.enter(inliningTarget);
                            throw raiseRegexErrorNode.executeFormatted(frame, BAD_CHAR_IN_GROUP_NAME, replacement, toCodepointIndex(nameStartPos, binary),
                                            binary ? PyObjectAsciiNode.executeUncached(name) : PyObjectReprAsTruffleStringNode.executeUncached(name));
                        }
                        Object namedCaptureGroups = TRegexUtil.TRegexCompiledRegexAccessor.namedCaptureGroups(tregexCompiledRegex, inliningTarget, readNamedGroupsNode);
                        if (!TRegexUtil.TRegexNamedCaptureGroupsAccessor.hasGroup(namedCaptureGroups, name, genericInteropLib)) {
                            throw raiseNode.raise(inliningTarget, PythonErrorType.IndexError, UNKNOWN_GROUP_NAME, name);
                        }
                        groupNumber = TRegexUtil.TRegexNamedCaptureGroupsAccessor.getGroupNumber(namedCaptureGroups, name, genericInteropLib);
                    }
                    builder.groupReference(groupNumber);
                    nextCPPos = nameEndPos + codepointLengthAscii;
                    lastPos = lastLiteralPos = nextCPPos;
                } else if (firstCodepoint == '0') {
                    int octalEscape;
                    if (isOctalDigit(secondCodepoint)) {
                        nextCPPos += codepointLengthAscii;
                        octalEscape = digitValue(secondCodepoint);
                        if (nextCPPos < length) {
                            int thirdCodepoint = codePointAtByteIndexNode.execute(replacement, nextCPPos, encoding);
                            if (isOctalDigit(thirdCodepoint)) {
                                nextCPPos += codepointLengthAscii;
                                octalEscape = (octalEscape * 8) + digitValue(thirdCodepoint);
                            }
                        }
                    } else {
                        octalEscape = 0;
                    }
                    builder.codepoint(octalEscape);
                    lastPos = lastLiteralPos = nextCPPos;
                } else if (isDecimalDigit(firstCodepoint)) {
                    int groupNumber = digitValue(firstCodepoint);
                    if (isDecimalDigit(secondCodepoint)) {
                        nextCPPos += codepointLengthAscii;
                        int thirdCodepoint;
                        if (Math.max(firstCodepoint, secondCodepoint) <= '7' && nextCPPos < length &&
                                        isOctalDigit(thirdCodepoint = codePointAtByteIndexNode.execute(replacement, nextCPPos, encoding))) {
                            nextCPPos += codepointLengthAscii;
                            // Single and double-digit escapes are group references, but three-digit
                            // escapes are octal character codes. Hopefully this will be deprecated
                            // at some point
                            int octalEscape = digitValue(firstCodepoint) * 64 + digitValue(secondCodepoint) * 8 + digitValue(thirdCodepoint);
                            if (octalEscape > 0xff) {
                                errorProfile.enter(inliningTarget);
                                TruffleString octalEscapeString = replacement.substringByteIndexUncached(backslashPos, nextCPPos - backslashPos, encoding, true);
                                throw raiseRegexErrorNode.executeFormatted(frame, OCTAL_ESCAPE_OUT_OF_RANGE, replacement, toCodepointIndex(backslashPos, binary), octalEscapeString);
                            }
                            builder.codepoint(octalEscape);
                            groupNumber = -1;
                        } else {
                            groupNumber = groupNumber * 10 + digitValue(secondCodepoint);
                        }
                    }
                    if (groupNumber >= 0) {
                        if (groupNumber >= numberOfCaptureGroups) {
                            errorProfile.enter(inliningTarget);
                            throw raiseRegexErrorNode.executeFormatted(frame, INVALID_GROUP_REFERENCE, replacement, toCodepointIndex(backslashPos + codepointLengthAscii, binary), groupNumber);
                        }
                        builder.groupReference(groupNumber);
                    }
                    lastPos = lastLiteralPos = nextCPPos;
                } else {
                    int escape = switch (firstCodepoint) {
                        case 'a' -> '\u0007';
                        case 'b' -> '\b';
                        case 'f' -> '\f';
                        case 'n' -> '\n';
                        case 'r' -> '\r';
                        case 't' -> '\t';
                        case 'v' -> '\u000b';
                        case '\\' -> '\\';
                        default -> {
                            // check if character is in [A-Za-z]
                            int lowercased = firstCodepoint | 0x20;
                            if ('a' <= lowercased && lowercased <= 'z') {
                                // nextCPPos points at a character next to firstCodepoint
                                int startAt = toCodepointIndex(nextCPPos, binary) - 2;
                                throw raiseRegexErrorNode.executeFormatted(frame, BAD_ESCAPE_S, replacement, startAt, toEscapeSequence(firstCodepoint));
                            } else {
                                yield -1;
                            }
                        }
                    };
                    if (escape >= 0) {
                        // valid escape sequence
                        builder.codepoint(escape);
                        lastLiteralPos = nextCPPos;
                    }
                    lastPos = nextCPPos;
                }
            }

            builder.literal(lastLiteralPos, length);
            return builder.build();
        }

        @TruffleBoundary
        static String toEscapeSequence(int codepoint) {
            return "\\" + (char) codepoint;
        }
    }

    public static void bailoutUnsupportedRegex(TRegexCache cache) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw CompilerDirectives.shouldNotReachHere("unsupported regular expression: /" + cache.pattern + "/" + cache.flags);
    }

    private static int toByteIndex(int index, boolean binary) {
        assert TS_ENCODING == TruffleString.Encoding.UTF_32 : "remove this method when switching to UTF-8";
        return binary ? index : index << 2;
    }

    private static int toCodepointIndex(int i, boolean binary) {
        assert TS_ENCODING == TruffleString.Encoding.UTF_32 : "remove this when switching to UTF-8";
        return binary ? i : i >> 2;
    }

    private static int digitValue(int d) {
        assert isDecimalDigit(d);
        return d - '0';
    }

    private static boolean isDecimalDigit(int d) {
        return '0' <= d && d <= '9';
    }

    private static boolean isOctalDigit(int d) {
        return '0' <= d && d <= '7';
    }

}
