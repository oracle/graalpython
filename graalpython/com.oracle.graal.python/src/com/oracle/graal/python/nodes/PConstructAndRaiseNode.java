/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.nodes.call.special.CallVarargsMethodNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.formatting.ErrorMessageFormatter;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.CachedLanguage;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;

@GenerateUncached
@ImportStatic(PGuards.class)
public abstract class PConstructAndRaiseNode extends Node {
    private static final ErrorMessageFormatter FORMATTER = new ErrorMessageFormatter();

    public final PException executeWithArgsOnly(Frame frame, PythonBuiltinClassType type, Object[] arguments) {
        return execute(frame, type, null, null, null, arguments, PKeyword.EMPTY_KEYWORDS);
    }

    public final PException executeWithArgsOnly(Frame frame, PythonBuiltinClassType type, PBaseException cause, Object[] arguments) {
        return execute(frame, type, cause, null, null, arguments, PKeyword.EMPTY_KEYWORDS);
    }

    public final PException executeWithArgsAndKwargs(Frame frame, PythonBuiltinClassType type, Object[] arguments, PKeyword[] keywords) {
        return execute(frame, type, null, null, null, arguments, keywords);
    }

    public final PException executeWithArgsAndKwargs(Frame frame, PythonBuiltinClassType type, PBaseException cause, Object[] arguments, PKeyword[] keywords) {
        return execute(frame, type, cause, null, null, arguments, keywords);
    }

    public final PException executeWithFmtMessageAndKwargs(Frame frame, PythonBuiltinClassType type, String format, Object[] formatArgs, PKeyword[] keywords) {
        return execute(frame, type, null, format, formatArgs, null, keywords);
    }

    public final PException executeWithFmtMessageAndKwargs(Frame frame, PythonBuiltinClassType type, PBaseException cause, String format, Object[] formatArgs, PKeyword[] keywords) {
        return execute(frame, type, cause, format, formatArgs, null, keywords);
    }

    public final PException executeWithFmtMessageAndArgs(Frame frame, PythonBuiltinClassType type, String format, Object[] formatArgs, Object[] arguments) {
        return execute(frame, type, null, format, formatArgs, arguments, PKeyword.EMPTY_KEYWORDS);
    }

    public final PException executeWithFmtMessageAndArgs(Frame frame, PythonBuiltinClassType type, PBaseException cause, String format, Object[] formatArgs, Object[] arguments) {
        return execute(frame, type, cause, format, formatArgs, arguments, PKeyword.EMPTY_KEYWORDS);
    }

    public abstract PException execute(Frame frame, PythonBuiltinClassType type, PBaseException cause, String format, Object[] formatArgs, Object[] arguments, PKeyword[] keywords);

    @CompilerDirectives.TruffleBoundary
    private static String getFormattedMessage(PythonObjectLibrary pol, String format, Object[] formatArgs) {
        return FORMATTER.format(pol, format, formatArgs);
    }

    private PException raiseInternal(VirtualFrame frame, PythonBuiltinClassType type, PBaseException cause, Object[] arguments, PKeyword[] keywords,
                    CallVarargsMethodNode callNode, PythonLanguage language, PythonCore core) {
        PBaseException error = (PBaseException) callNode.execute(frame, core.lookupType(type), arguments, keywords);
        if (cause != null) {
            error.setContext(cause);
            error.setCause(cause);
        }
        return PRaiseNode.raise(this, error, PythonOptions.isPExceptionWithJavaStacktrace(language));
    }

    @Specialization(guards = {"format == null", "formatArgs == null"})
    PException constructAndRaiseNoFormatString(VirtualFrame frame, PythonBuiltinClassType type, PBaseException cause, @SuppressWarnings("unused") String format, @SuppressWarnings("unused") Object[] formatArgs,
                    Object[] arguments, PKeyword[] keywords,
                    @Cached.Shared("callNode") @Cached CallVarargsMethodNode callNode,
                    @CachedLanguage PythonLanguage language,
                    @CachedContext(PythonLanguage.class) PythonContext context) {
        PythonCore core = context.getCore();
        return raiseInternal(frame, type, cause, arguments, keywords, callNode, language, core);
    }

    @Specialization(guards = {"format != null", "arguments == null"})
    PException constructAndRaiseNoArgs(VirtualFrame frame, PythonBuiltinClassType type, PBaseException cause, String format, Object[] formatArgs,
                    @SuppressWarnings("unused") Object[] arguments, PKeyword[] keywords,
                    @Cached.Shared("callNode") @Cached CallVarargsMethodNode callNode,
                    @Cached.Shared("pol") @CachedLibrary(limit = "3") PythonObjectLibrary pol,
                    @CachedLanguage PythonLanguage language,
                    @CachedContext(PythonLanguage.class) PythonContext context) {
        PythonCore core = context.getCore();
        Object[] args = new Object[]{formatArgs != null ? getFormattedMessage(pol, format, formatArgs) : format};
        return raiseInternal(frame, type, cause, args, keywords, callNode, language, core);
    }

    @Specialization(guards = {"format != null", "arguments != null"})
    PException constructAndRaise(VirtualFrame frame, PythonBuiltinClassType type, PBaseException cause, String format, Object[] formatArgs,
                    Object[] arguments, PKeyword[] keywords,
                    @Cached.Shared("callNode") @Cached CallVarargsMethodNode callNode,
                    @Cached.Shared("pol") @CachedLibrary(limit = "3") PythonObjectLibrary pol,
                    @CachedLanguage PythonLanguage language,
                    @CachedContext(PythonLanguage.class) PythonContext context) {
        PythonCore core = context.getCore();
        Object[] args = new Object[arguments.length + 1];
        args[0] = formatArgs != null ? getFormattedMessage(pol, format, formatArgs) : format;
        System.arraycopy(arguments, 0, args, 1, arguments.length);
        return raiseInternal(frame, type,cause, args, keywords, callNode, language, core);
    }

    // ImportError helpers
    private PException raiseImportErrorInternal(Frame frame, PBaseException cause, String format, Object[] formatArgs, PKeyword[] keywords) {
        return executeWithFmtMessageAndKwargs(frame, PythonBuiltinClassType.ImportError, cause, format, formatArgs, keywords);
    }

    private PException raiseImportErrorInternal(Frame frame, String format, Object[] formatArgs, PKeyword[] keywords) {
        return executeWithFmtMessageAndKwargs(frame, PythonBuiltinClassType.ImportError, format, formatArgs, keywords);
    }

    public final PException raiseImportError(Frame frame, Object name, Object path, String format, Object... formatArgs) {
        return raiseImportErrorInternal(frame, format, formatArgs, new PKeyword[]{new PKeyword("name", name), new PKeyword("path", path)});
    }

    public final PException raiseImportError(Frame frame, String format, Object... formatArgs) {
        return raiseImportErrorInternal(frame, format, formatArgs, PKeyword.EMPTY_KEYWORDS);
    }

    public final PException raiseImportError(Frame frame, PBaseException cause, Object name, Object path, String format, Object... formatArgs) {
        return raiseImportErrorInternal(frame, cause, format, formatArgs, new PKeyword[]{new PKeyword("name", name), new PKeyword("path", path)});
    }

    // OSError helpers
    @CompilerDirectives.TruffleBoundary
    private static String getMessage(Exception exception) {
        return exception.getMessage();
    }

    private static Object[] createOsErrorArgs(OSErrorEnum osErrorEnum, String filename1, String filename2) {
        return new Object[]{osErrorEnum.getNumber(), osErrorEnum.getMessage(),
                        (filename1 != null) ? filename1 : PNone.NONE,
                        PNone.NONE,
                        (filename2 != null) ? filename2 : PNone.NONE};
    }

    private static Object[] createOsErrorArgs(Exception exception, String filename1, String filename2) {
        OSErrorEnum.ErrorAndMessagePair errorAndMessage = OSErrorEnum.fromException(exception);
        return new Object[]{errorAndMessage.oserror.getNumber(), errorAndMessage.message,
                        (filename1 != null) ? filename1 : PNone.NONE,
                        PNone.NONE,
                        (filename2 != null) ? filename2 : PNone.NONE};
    }

    private PException raiseOSErrorInternal(Frame frame, Object[] arguments) {
        return executeWithArgsOnly(frame, PythonBuiltinClassType.OSError, arguments);
    }

    public final PException raiseOSError(Frame frame, OSErrorEnum osErrorEnum) {
        return raiseOSErrorInternal(frame, createOsErrorArgs(osErrorEnum, null, null));
    }

    public final PException raiseOSError(Frame frame, OSErrorEnum osErrorEnum, String filename) {
        return raiseOSErrorInternal(frame, createOsErrorArgs(osErrorEnum, filename, null));
    }

    public final PException raiseOSError(Frame frame, OSErrorEnum osErrorEnum, String filename, String filename2) {
        return raiseOSErrorInternal(frame, createOsErrorArgs(osErrorEnum, filename, filename2));
    }

    public final PException raiseOSError(Frame frame, Exception exception) {
        return raiseOSErrorInternal(frame, createOsErrorArgs(exception, null, null));
    }

    public final PException raiseOSError(Frame frame, Exception exception, String filename) {
        return raiseOSErrorInternal(frame, createOsErrorArgs(exception, filename, null));
    }

    public final PException raiseOSError(Frame frame, Exception exception, String filename, String filename2) {
        return raiseOSErrorInternal(frame, createOsErrorArgs(exception, filename, filename2));
    }

    public final PException raiseOSError(Frame frame, OSErrorEnum osErrorEnum, Exception exception) {
        return raiseOSError(frame, osErrorEnum, getMessage(exception));
    }

    public static PConstructAndRaiseNode create() {
        return PConstructAndRaiseNodeGen.create();
    }

    public static PConstructAndRaiseNode getUncached() {
        return PConstructAndRaiseNodeGen.getUncached();
    }
}
