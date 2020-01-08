/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.BytesUtils;
import com.oracle.graal.python.builtins.objects.bytes.PIBytesLike;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory.ToByteArrayNodeGen;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.source.Source;

@CoreFunctions(defineModule = "_sre")
public class SREModuleBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return SREModuleBuiltinsFactory.getFactories();
    }

    @Builtin(name = "_build_regex_engine", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class BuildRegexEngine extends PythonUnaryBuiltinNode {
        @Specialization(guards = "!getFlag(context, WithTRegex)")
        Object useSRE(@SuppressWarnings("unused") String code,
                        @SuppressWarnings("unused") @CachedContext(PythonLanguage.class) PythonContext context) {
            return PNone.NONE;
        }

        @Specialization(guards = "getFlag(context, WithTRegex)")
        @TruffleBoundary
        Object run(String code,
                        @SuppressWarnings("unused") @CachedContext(PythonLanguage.class) PythonContext context) {
            return getContext().getEnv().parseInternal(Source.newBuilder("regex", code, "build-regex-engine").build()).call();
        }
    }

    /**
     * Replaces any <it>quoted</it> escape sequence like {@code "\\n"} (two characters; backslash +
     * 'n') by its single character like {@code "\n"} (one character; newline).
     */
    @Builtin(name = "_process_escape_sequences", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ProcessEscapeSequences extends PythonUnaryBuiltinNode {

        @Child private SequenceStorageNodes.ToByteArrayNode toByteArrayNode;
        @Child private BytesNodes.ToBytesNode toBytesNode;

        @Specialization
        Object run(PString str) {
            return run(str.getValue());
        }

        @Specialization
        @TruffleBoundary(transferToInterpreterOnException = false, allowInlining = true)
        Object run(String str) {
            if (containsBackslash(str)) {
                StringBuilder sb = BytesUtils.decodeEscapes(getCore(), str, true);
                return sb.toString();
            }
            return str;
        }

        @Specialization
        Object run(PIBytesLike str) {
            byte[] bytes = doBytes(getToByteArrayNode().execute(str.getSequenceStorage()));
            if (bytes != null) {
                return factory().createByteArray(bytes);
            }
            return str;
        }

        @Specialization
        Object run(VirtualFrame frame, PMemoryView memoryView) {
            byte[] bytes = doBytes(getToBytesNode().execute(frame, memoryView));
            if (bytes != null) {
                return factory().createByteArray(bytes);
            }
            return memoryView;
        }

        @TruffleBoundary(transferToInterpreterOnException = false, allowInlining = true)
        private byte[] doBytes(byte[] str) {
            try {
                StringBuilder sb = BytesUtils.decodeEscapes(getCore(), new String(str, "ascii"), true);
                return sb.toString().getBytes("ascii");
            } catch (UnsupportedEncodingException e) {
            }
            return null;
        }

        private static boolean containsBackslash(String str) {
            CompilerAsserts.neverPartOfCompilation();
            for (int i = 0; i < str.length(); i++) {
                if (str.charAt(i) == '\\') {
                    return true;
                }
            }
            return false;
        }

        @Fallback
        Object run(Object o) {
            throw raise(PythonErrorType.TypeError, "expected string, not %p", o);
        }

        private SequenceStorageNodes.ToByteArrayNode getToByteArrayNode() {
            if (toByteArrayNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toByteArrayNode = insert(ToByteArrayNodeGen.create());
            }
            return toByteArrayNode;
        }

        private BytesNodes.ToBytesNode getToBytesNode() {
            if (toBytesNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toBytesNode = insert(BytesNodes.ToBytesNode.create());
            }
            return toBytesNode;
        }
    }

    @Builtin(name = "tregex_call_compile", minNumOfPositionalArgs = 3)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class TRegexCallCompile extends PythonTernaryBuiltinNode {

        @Specialization(limit = "1")
        Object call(VirtualFrame frame, Object callable, Object arg1, Object arg2,
                        @Cached("create()") BranchProfile syntaxError,
                        @Cached("create()") BranchProfile typeError,
                        @CachedLibrary("callable") InteropLibrary interop,
                        @CachedContext(PythonLanguage.class) PythonContext context) {
            Object state = IndirectCallContext.enter(frame, context, this);
            try {
                return interop.execute(callable, arg1, arg2);
            } catch (ArityException | UnsupportedTypeException | UnsupportedMessageException e) {
                typeError.enter();
                throw raise(TypeError, "%s", e);
            } catch (RuntimeException e) {
                if (e instanceof TruffleException && ((TruffleException) e).isSyntaxError()) {
                    syntaxError.enter();
                    throw raise(ValueError, "%s", e);
                }
                // just re-throw
                throw e;
            } finally {
                IndirectCallContext.exit(frame, context, state);
            }
        }
    }

    @Builtin(name = "tregex_call_exec", minNumOfPositionalArgs = 3)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class TRegexCallExec extends PythonTernaryBuiltinNode {

        @Specialization(limit = "1")
        Object call(VirtualFrame frame, Object callable, Object arg1, Number arg2,
                        @Cached("create()") BranchProfile typeError,
                        @CachedLibrary("callable") InteropLibrary interop,
                        @CachedContext(PythonLanguage.class) PythonContext context) {
            Object state = IndirectCallContext.enter(frame, context, this);
            try {
                return interop.execute(callable, arg1, arg2);
            } catch (ArityException | UnsupportedTypeException | UnsupportedMessageException e) {
                typeError.enter();
                throw raise(TypeError, "%s", e);
            } finally {
                IndirectCallContext.exit(frame, context, state);
            }
        }
    }
}
