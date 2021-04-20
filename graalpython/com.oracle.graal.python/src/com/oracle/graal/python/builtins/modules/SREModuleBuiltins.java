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
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
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
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

@CoreFunctions(defineModule = "_sre")
public class SREModuleBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return SREModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(PythonCore core) {
        builtinConstants.put("_with_tregex", core.getContext().getLanguage().getEngineOption(PythonOptions.WithTRegex));
        super.initialize(core);
    }

    abstract static class ToRegexSourceNode extends Node {

        public abstract Source execute(Object pattern, String flags);

        @TruffleBoundary
        private static String decodeLatin1(byte[] bytes) {
            try {
                return new String(bytes, "Latin1");
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
        protected Source doString(String pattern, String flags) {
            String options = "Flavor=PythonStr,Encoding=UTF-16";
            return constructRegexSource(options, pattern, flags);
        }

        @Specialization(guards = "stringLib.isString(pattern)")
        protected Source doBoxedString(Object pattern, String flags,
                        @CachedLibrary(limit = "1") InteropLibrary stringLib) {
            try {
                return doString(stringLib.asString(pattern), flags);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }

        @Specialization(guards = "bufferLib.isBuffer(pattern)")
        protected Source doBytesLike(Object pattern, String flags,
                        @CachedLibrary(limit = "3") PythonObjectLibrary bufferLib) {
            try {
                String options = "Flavor=PythonBytes,Encoding=BYTES";
                byte[] bytes = bufferLib.getBufferBytes(pattern);
                String patternStr = decodeLatin1(bytes);
                return constructRegexSource(options, patternStr, flags);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }
    }

    @Builtin(name = "tregex_compile_internal", minNumOfPositionalArgs = 3)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class TRegexCallCompile extends PythonTernaryBuiltinNode {

        @Specialization
        Object call(VirtualFrame frame, Object pattern, Object flags, PFunction fallbackCompiler,
                        @Cached BranchProfile potentialSyntaxError,
                        @Cached BranchProfile syntaxError,
                        @Cached BranchProfile unsupportedRegexError,
                        @Cached CastToJavaStringNode toStringNode,
                        @Cached ToRegexSourceNode toRegexSourceNode,
                        @Cached CallNode callFallbackCompilerNode,
                        @CachedLibrary(limit = "2") InteropLibrary exceptionLib,
                        @CachedLibrary(limit = "2") InteropLibrary compiledRegexLib,
                        @CachedContext(PythonLanguage.class) PythonContext context) {
            try {
                String flagsStr = toStringNode.execute(flags);
                Source regexSource = toRegexSourceNode.execute(pattern, flagsStr);
                Object compiledRegex = context.getEnv().parseInternal(regexSource).call();
                if (compiledRegexLib.isNull(compiledRegex)) {
                    unsupportedRegexError.enter();
                    if (context.getLanguage().getEngineOption(PythonOptions.TRegexUsesSREFallback)) {
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
                        @Cached BranchProfile typeError,
                        @CachedLibrary("callable") InteropLibrary interop,
                        @CachedContext(PythonLanguage.class) PythonContext context) {
            Object state = IndirectCallContext.enter(frame, context, this);
            try {
                return interop.execute(callable, inputStringOrBytes, fromIndex);
            } catch (ArityException | UnsupportedTypeException | UnsupportedMessageException e) {
                typeError.enter();
                throw raise(TypeError, "%s", e);
            } finally {
                IndirectCallContext.exit(frame, context, state);
            }
        }
    }
}
