/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.codecs;

import static com.oracle.graal.python.nodes.BuiltinNames.T_ENCODINGS;
import static com.oracle.graal.python.nodes.ErrorMessages.HANDLER_MUST_BE_CALLABLE;
import static com.oracle.graal.python.nodes.ErrorMessages.UNKNOWN_ERROR_HANDLER;
import static com.oracle.graal.python.nodes.StringLiterals.T_BACKSLASHREPLACE;
import static com.oracle.graal.python.nodes.StringLiterals.T_IGNORE;
import static com.oracle.graal.python.nodes.StringLiterals.T_NAMEREPLACE;
import static com.oracle.graal.python.nodes.StringLiterals.T_REPLACE;
import static com.oracle.graal.python.nodes.StringLiterals.T_STRICT;
import static com.oracle.graal.python.nodes.StringLiterals.T_SURROGATEESCAPE;
import static com.oracle.graal.python.nodes.StringLiterals.T_SURROGATEPASS;
import static com.oracle.graal.python.nodes.StringLiterals.T_XMLCHARREFREPLACE;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.LookupError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import com.oracle.graal.python.builtins.modules.codecs.ErrorHandlers.BackslashReplaceErrorHandlerNode;
import com.oracle.graal.python.builtins.modules.codecs.ErrorHandlers.IgnoreErrorHandlerNode;
import com.oracle.graal.python.builtins.modules.codecs.ErrorHandlers.NameReplaceErrorHandlerNode;
import com.oracle.graal.python.builtins.modules.codecs.ErrorHandlers.ReplaceErrorHandlerNode;
import com.oracle.graal.python.builtins.modules.codecs.ErrorHandlers.StrictErrorHandlerNode;
import com.oracle.graal.python.builtins.modules.codecs.ErrorHandlers.SurrogateEscapeErrorHandlerNode;
import com.oracle.graal.python.builtins.modules.codecs.ErrorHandlers.SurrogatePassErrorHandlerNode;
import com.oracle.graal.python.builtins.modules.codecs.ErrorHandlers.XmlCharRefReplaceErrorHandlerNode;
import com.oracle.graal.python.builtins.modules.codecs.ErrorHandlersFactory.BackslashReplaceErrorHandlerNodeGen;
import com.oracle.graal.python.builtins.modules.codecs.ErrorHandlersFactory.NameReplaceErrorHandlerNodeGen;
import com.oracle.graal.python.builtins.modules.codecs.ErrorHandlersFactory.IgnoreErrorHandlerNodeGen;
import com.oracle.graal.python.builtins.modules.codecs.ErrorHandlersFactory.ReplaceErrorHandlerNodeGen;
import com.oracle.graal.python.builtins.modules.codecs.ErrorHandlersFactory.StrictErrorHandlerNodeGen;
import com.oracle.graal.python.builtins.modules.codecs.ErrorHandlersFactory.SurrogateEscapeErrorHandlerNodeGen;
import com.oracle.graal.python.builtins.modules.codecs.ErrorHandlersFactory.SurrogatePassErrorHandlerNodeGen;
import com.oracle.graal.python.builtins.modules.codecs.ErrorHandlersFactory.XmlCharRefReplaceErrorHandlerNodeGen;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.lib.PyCallableCheckNode;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.statement.AbstractImportNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.graal.python.util.Supplier;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

public final class CodecsRegistry {

    // Equivalent of PyCodec_LookupError
    @GenerateInline
    @GenerateCached(false)
    public abstract static class PyCodecLookupErrorNode extends Node {

        public abstract Object execute(Node inliningTarget, TruffleString name);

        @Specialization
        static Object lookup(Node inliningTarget, TruffleString name,
                        @Cached InlinedConditionProfile resultProfile,
                        @Cached PRaiseNode.Lazy raiseNode) {
            PythonContext context = PythonContext.get(inliningTarget);
            ensureRegistryInitialized(context);
            if (name == null) {
                name = T_STRICT;
            }
            Object result = getErrorHandler(context, name);
            if (resultProfile.profile(inliningTarget, result == null)) {
                throw raiseNode.get(inliningTarget).raise(LookupError, UNKNOWN_ERROR_HANDLER, name);
            }
            return result;
        }
    }

    // Equivalent of PyCodec_RegisterError
    @GenerateInline
    @GenerateCached(false)
    public abstract static class PyCodecRegisterErrorNode extends Node {

        public abstract void execute(Node inliningTarget, TruffleString name, Object handler);

        @Specialization(guards = "callableCheckNode.execute(handler)")
        static void register(Node inliningTarget, TruffleString name, Object handler,
                        @SuppressWarnings("unused") @Cached(inline = false) @Shared("callableCheck") PyCallableCheckNode callableCheckNode) {
            PythonContext context = PythonContext.get(inliningTarget);
            ensureRegistryInitialized(context);
            putErrorHandler(context, name, handler);
        }

        @Specialization(guards = "!callableCheckNode.execute(handler)")
        static void registerNoCallable(@SuppressWarnings("unused") TruffleString name, @SuppressWarnings("unused") Object handler,
                        @SuppressWarnings("unused") @Cached(inline = false) @Shared("callableCheck") PyCallableCheckNode callableCheckNode,
                        @Cached(inline = false) PRaiseNode raiseNode) {
            throw raiseNode.raise(TypeError, HANDLER_MUST_BE_CALLABLE);
        }
    }

    public static void ensureRegistryInitialized(PythonContext context) {
        if (CompilerDirectives.injectBranchProbability(CompilerDirectives.SLOWPATH_PROBABILITY, !context.isCodecsInitialized())) {
            registerDefaultHandler(context, T_STRICT, StrictErrorHandlerNode.class, StrictErrorHandlerNodeGen::create);
            registerDefaultHandler(context, T_IGNORE, IgnoreErrorHandlerNode.class, IgnoreErrorHandlerNodeGen::create);
            registerDefaultHandler(context, T_REPLACE, ReplaceErrorHandlerNode.class, ReplaceErrorHandlerNodeGen::create);
            registerDefaultHandler(context, T_XMLCHARREFREPLACE, XmlCharRefReplaceErrorHandlerNode.class, XmlCharRefReplaceErrorHandlerNodeGen::create);
            registerDefaultHandler(context, T_BACKSLASHREPLACE, BackslashReplaceErrorHandlerNode.class, BackslashReplaceErrorHandlerNodeGen::create);
            registerDefaultHandler(context, T_NAMEREPLACE, NameReplaceErrorHandlerNode.class, NameReplaceErrorHandlerNodeGen::create);
            registerDefaultHandler(context, T_SURROGATEPASS, SurrogatePassErrorHandlerNode.class, SurrogatePassErrorHandlerNodeGen::create);
            registerDefaultHandler(context, T_SURROGATEESCAPE, SurrogateEscapeErrorHandlerNode.class, SurrogateEscapeErrorHandlerNodeGen::create);
            AbstractImportNode.importModule(T_ENCODINGS);
            context.markCodecsInitialized();
        }
    }

    @TruffleBoundary
    private static void registerDefaultHandler(PythonContext context, TruffleString name, Class<?> nodeClass, Supplier<PythonBuiltinBaseNode> nodeSupplier) {
        PBuiltinFunction f = PythonUtils.createMethod(context.getLanguage(), null, nodeClass, null, 0, nodeSupplier, name.toJavaStringUncached());
        putErrorHandler(context, name, f);
    }

    @TruffleBoundary
    private static Object getErrorHandler(PythonContext ctx, TruffleString name) {
        return ctx.getCodecErrorRegistry().get(name);
    }

    @TruffleBoundary
    private static void putErrorHandler(PythonContext ctx, TruffleString name, Object handler) {
        ctx.getCodecErrorRegistry().put(name, handler);
    }
}
