/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.objects.exception.OsErrorBuiltins.errorType2errno;
import static com.oracle.graal.python.builtins.objects.ssl.SSLErrorBuiltins.setSSLErrorAttributes;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.ssl.SSLErrorCode;
import com.oracle.graal.python.nodes.call.special.CallVarargsMethodNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.formatting.ErrorMessageFormatter;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
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
    private static String getFormattedMessage(String format, Object[] formatArgs) {
        return FORMATTER.format(format, formatArgs);
    }

    private PException raiseInternal(VirtualFrame frame, PythonBuiltinClassType type, PBaseException cause, Object[] arguments, PKeyword[] keywords,
                    CallVarargsMethodNode callNode, Python3Core core) {
        PBaseException error = (PBaseException) callNode.execute(frame, core.lookupType(type), arguments, keywords);
        if (cause != null) {
            error.setContext(cause);
            error.setCause(cause);
        }
        return PRaiseNode.raise(this, error, PythonOptions.isPExceptionWithJavaStacktrace(PythonLanguage.get(this)));
    }

    @Specialization(guards = {"format == null", "formatArgs == null"})
    PException constructAndRaiseNoFormatString(VirtualFrame frame, PythonBuiltinClassType type, PBaseException cause, @SuppressWarnings("unused") String format,
                    @SuppressWarnings("unused") Object[] formatArgs,
                    Object[] arguments, PKeyword[] keywords,
                    @Cached.Shared("callNode") @Cached CallVarargsMethodNode callNode) {
        Python3Core core = PythonContext.get(this);
        return raiseInternal(frame, type, cause, arguments, keywords, callNode, core);
    }

    @Specialization(guards = {"format != null", "arguments == null"})
    PException constructAndRaiseNoArgs(VirtualFrame frame, PythonBuiltinClassType type, PBaseException cause, String format, Object[] formatArgs,
                    @SuppressWarnings("unused") Object[] arguments, PKeyword[] keywords,
                    @Cached.Shared("callNode") @Cached CallVarargsMethodNode callNode) {
        Python3Core core = PythonContext.get(this);
        Object[] args = new Object[]{formatArgs != null ? getFormattedMessage(format, formatArgs) : format};
        return raiseInternal(frame, type, cause, args, keywords, callNode, core);
    }

    @Specialization(guards = {"format != null", "arguments != null"})
    PException constructAndRaise(VirtualFrame frame, PythonBuiltinClassType type, PBaseException cause, String format, Object[] formatArgs,
                    Object[] arguments, PKeyword[] keywords,
                    @Cached.Shared("callNode") @Cached CallVarargsMethodNode callNode) {
        Python3Core core = PythonContext.get(this);
        Object[] args = new Object[arguments.length + 1];
        args[0] = (formatArgs != null) ? getFormattedMessage(format, formatArgs) : format;
        System.arraycopy(arguments, 0, args, 1, arguments.length);
        return raiseInternal(frame, type, cause, args, keywords, callNode, core);
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

    public final PException raise(Frame frame, PythonBuiltinClassType err, String message, Object... formatArgs) {
        return executeWithFmtMessageAndArgs(frame, err, message, formatArgs, PythonUtils.EMPTY_OBJECT_ARRAY);
    }

    private static Object[] createOsErrorArgs(int errno, String message, Object filename1, Object filename2) {
        return new Object[]{errno, message, filename1, null, filename2};
    }

    private static Object[] createOsErrorArgs(OSErrorEnum osErrorEnum, String filename1, String filename2) {
        return new Object[]{osErrorEnum.getNumber(), osErrorEnum.getMessage(), filename1, null, filename2};
    }

    private static Object[] createOsErrorArgs(Exception exception, String filename1, String filename2) {
        OSErrorEnum.ErrorAndMessagePair errorAndMessage = OSErrorEnum.fromException(exception);
        return new Object[]{errorAndMessage.oserror.getNumber(), errorAndMessage.message, filename1, null, filename2};
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

    public final PException raiseOSError(Frame frame, int errno, String message, Object filename) {
        return raiseOSErrorInternal(frame, createOsErrorArgs(errno, message, filename, null));
    }

    public final PException raiseOSError(Frame frame, int errno, String message, Object filename, Object filename2) {
        return raiseOSErrorInternal(frame, createOsErrorArgs(errno, message, filename, filename2));
    }

    public final PException raiseSSLError(Frame frame, String message) {
        return raiseSSLError(frame, message, PythonUtils.EMPTY_OBJECT_ARRAY);
    }

    public final PException raiseSSLError(Frame frame, String message, Object... formatArgs) {
        return raise(frame, PythonBuiltinClassType.SSLError, message, formatArgs);
    }

    public PException raiseSSLError(Frame frame, SSLErrorCode errorCode, Exception ex) {
        return raiseSSLError(frame, errorCode, getMessage(ex));
    }

    public PException raiseSSLError(Frame frame, SSLErrorCode errorCode, String format, Object... formatArgs) {
        String message = getFormattedMessage(format, formatArgs);
        try {
            return executeWithFmtMessageAndArgs(frame, errorCode.getType(), null, null, new Object[]{errorCode.getErrno(), message});
        } catch (PException pException) {
            setSSLErrorAttributes(pException, errorCode, message);
            return pException;
        }
    }

    public static PException raiseUncachedSSLError(String message) {
        return getUncached().raiseSSLError(null, message);
    }

    public static PException raiseUncachedSSLError(String message, Object... formatArgs) {
        return getUncached().raiseSSLError(null, message, formatArgs);
    }

    public static PException raiseUncachedSSLError(SSLErrorCode errorCode, Exception ex) {
        return getUncached().raiseSSLError(null, errorCode, ex);
    }

    public static PException raiseUncachedSSLError(SSLErrorCode errorCode, String format, Object... formatArgs) {
        return getUncached().raiseSSLError(null, errorCode, format, formatArgs);
    }

    public final PException raiseOSErrorSubType(Frame frame, PythonBuiltinClassType osErrorSubtype, String format, Object... fmtArgs) {
        String message = getFormattedMessage(format, fmtArgs);
        final OSErrorEnum osErrorEnum = errorType2errno(osErrorSubtype);
        assert osErrorEnum != null : "could not determine an errno for this error, either not an OSError subtype or multiple errno codes are available";
        return executeWithArgsOnly(frame, osErrorSubtype, new Object[]{osErrorEnum.getNumber(), message});
    }

    public final PException raiseFileNotFoundError(Frame frame, String format, Object... fmtArgs) {
        return raiseOSErrorSubType(frame, PythonBuiltinClassType.FileNotFoundError, format, fmtArgs);
    }

    public final PException raiseSocketTimeoutError(Frame frame, String format, Object... fmtArgs) {
        return raiseOSErrorSubType(frame, PythonBuiltinClassType.SocketTimeout, format, fmtArgs);
    }

    public final PException raiseUnicodeEncodeError(Frame frame, String encoding, String object, int start, int end, String reason) {
        return executeWithArgsOnly(frame, PythonBuiltinClassType.UnicodeEncodeError, new Object[]{encoding, object, start, end, reason});
    }

    public final PException raiseUnicodeDecodeError(Frame frame, String encoding, Object object, int start, int end, String reason) {
        return executeWithArgsOnly(frame, PythonBuiltinClassType.UnicodeDecodeError, new Object[]{encoding, object, start, end, reason});
    }

    public final PException raiseUnicodeTranslateError(Frame frame, String object, int start, int end, String reason) {
        return executeWithArgsOnly(frame, PythonBuiltinClassType.UnicodeTranslateError, new Object[]{object, start, end, reason});
    }

    public static PException raiseUncachedUnicodeDecodeError(String encoding, Object object, int start, int end, String reason) {
        return getUncached().raiseUnicodeDecodeError(null, encoding, object, start, end, reason);
    }

    public static PConstructAndRaiseNode create() {
        return PConstructAndRaiseNodeGen.create();
    }

    public static PConstructAndRaiseNode getUncached() {
        return PConstructAndRaiseNodeGen.getUncached();
    }
}
