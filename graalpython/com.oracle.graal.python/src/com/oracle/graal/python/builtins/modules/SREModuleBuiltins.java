/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.io.UnsupportedEncodingException;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.nodes.PNodeWithRaiseAndIndirectCall;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.util.PythonUtils;
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
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

@CoreFunctions(defineModule = "_sre")
public class SREModuleBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return SREModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        builtinConstants.put("_with_tregex", core.getContext().getLanguage().getEngineOption(PythonOptions.WithTRegex));
        super.initialize(core);
    }

    abstract static class ToRegexSourceNode extends PNodeWithRaiseAndIndirectCall {

        public abstract Source execute(VirtualFrame frame, Object pattern, String flags, String options);

        @TruffleBoundary
        private static String decodeLatin1(byte[] bytes, int length) {
            try {
                return new String(bytes, 0, length, "Latin1");
            } catch (UnsupportedEncodingException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }

        // TruffleBoundary because of StringBuilder#append in compiled code
        @TruffleBoundary
        private static Source constructRegexSource(String options, String pattern, String flags) {
            String regexSourceStr = options + "/" + pattern + "/" + flags;
            return Source.newBuilder("regex", regexSourceStr, "re").mimeType("application/tregex").internal(true).build();
        }

        @Specialization
        protected Source doString(String pattern, String flags, String options,
                        @Cached ConditionProfile nonEmptyOptionsProfile) {
            StringBuilder allOptions = PythonUtils.newStringBuilder("Flavor=PythonStr,Encoding=UTF-16");
            if (nonEmptyOptionsProfile.profile(!options.isEmpty())) {
                PythonUtils.append(allOptions, ',');
                PythonUtils.append(allOptions, options);
            }
            return constructRegexSource(PythonUtils.sbToString(allOptions), pattern, flags);
        }

        @Specialization(limit = "3")
        protected Source doGeneric(VirtualFrame frame, Object pattern, String flags, String options,
                        @Cached ConditionProfile nonEmptyOptionsProfile,
                        @Cached CastToJavaStringNode cast,
                        @CachedLibrary("pattern") PythonBufferAcquireLibrary bufferAcquireLib,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib) {
            try {
                return doString(cast.execute(pattern), flags, options, nonEmptyOptionsProfile);
            } catch (CannotCastException ce) {
                Object buffer;
                try {
                    buffer = bufferAcquireLib.acquireReadonly(pattern, frame, this);
                } catch (PException e) {
                    throw raise(TypeError, "expected string or bytes-like object");
                }
                try {
                    StringBuilder allOptions = PythonUtils.newStringBuilder("Flavor=PythonBytes,Encoding=BYTES");
                    if (nonEmptyOptionsProfile.profile(!options.isEmpty())) {
                        PythonUtils.append(allOptions, ',');
                        PythonUtils.append(allOptions, options);
                    }
                    byte[] bytes = bufferLib.getInternalOrCopiedByteArray(buffer);
                    int bytesLen = bufferLib.getBufferLength(buffer);
                    String patternStr = decodeLatin1(bytes, bytesLen);
                    return constructRegexSource(PythonUtils.sbToString(allOptions), patternStr, flags);
                } finally {
                    bufferLib.release(buffer, frame, this);
                }
            }
        }
    }

    @Builtin(name = "tregex_compile_internal", minNumOfPositionalArgs = 4)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class TRegexCallCompile extends PythonQuaternaryBuiltinNode {

        @Specialization
        Object call(VirtualFrame frame, Object pattern, Object flags, Object options, PFunction fallbackCompiler,
                        @Cached BranchProfile potentialSyntaxError,
                        @Cached BranchProfile syntaxError,
                        @Cached BranchProfile unsupportedRegexError,
                        @Cached CastToJavaStringNode flagsToStringNode,
                        @Cached CastToJavaStringNode optionsToStringNode,
                        @Cached ToRegexSourceNode toRegexSourceNode,
                        @Cached CallNode callFallbackCompilerNode,
                        @CachedLibrary(limit = "2") InteropLibrary exceptionLib,
                        @CachedLibrary(limit = "2") InteropLibrary compiledRegexLib) {
            try {
                String flagsStr = flagsToStringNode.execute(flags);
                String optionsStr = optionsToStringNode.execute(options);
                Source regexSource = toRegexSourceNode.execute(frame, pattern, flagsStr, optionsStr);
                Object compiledRegex = getContext().getEnv().parseInternal(regexSource).call();
                if (compiledRegexLib.isNull(compiledRegex)) {
                    unsupportedRegexError.enter();
                    if (getLanguage().getEngineOption(PythonOptions.TRegexUsesSREFallback)) {
                        return callFallbackCompilerNode.execute(frame, fallbackCompiler, pattern, flags);
                    } else {
                        throw raise(ValueError, "regular expression not supported, no fallback engine present");
                    }
                } else {
                    return compiledRegex;
                }
            } catch (RuntimeException e) {
                return handleError(e, syntaxError, potentialSyntaxError, exceptionLib);
            }
        }

        private Object handleError(RuntimeException e, BranchProfile syntaxError, BranchProfile potentialSyntaxError, InteropLibrary lib) {
            try {
                if (lib.isException(e)) {
                    potentialSyntaxError.enter();
                    if (lib.getExceptionType(e) == ExceptionType.PARSE_ERROR) {
                        syntaxError.enter();
                        Object reason = lib.asString(lib.getExceptionMessage(e));
                        SourceSection sourceSection = lib.getSourceLocation(e);
                        int position = sourceSection.getCharIndex();
                        throw raise(ValueError, reason, position);
                    }
                }
            } catch (UnsupportedMessageException e1) {
                throw CompilerDirectives.shouldNotReachHere();
            }
            // just re-throw
            throw e;
        }
    }

    @Builtin(name = "tregex_call_exec", minNumOfPositionalArgs = 3)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class TRegexCallExec extends PythonTernaryBuiltinNode {

        @Specialization(limit = "1")
        Object call(VirtualFrame frame, Object callable, Object inputStringOrBytes, Number fromIndex,
                        @Cached CastToJavaStringNode cast,
                        @Cached BranchProfile typeError,
                        @CachedLibrary("callable") InteropLibrary interop) {
            PythonContext context = getContext();
            PythonLanguage language = getLanguage();
            Object input = inputStringOrBytes;
            try {
                // This would materialize the string if it was native
                input = cast.execute(inputStringOrBytes);
            } catch (CannotCastException e) {
                // It's bytes or other buffer object
            }
            Object state = IndirectCallContext.enter(frame, language, context, this);
            try {
                return interop.execute(callable, input, fromIndex);
            } catch (ArityException | UnsupportedTypeException | UnsupportedMessageException e) {
                typeError.enter();
                throw raise(TypeError, "%m", e);
            } finally {
                IndirectCallContext.exit(frame, language, context, state);
            }
        }
    }
}
