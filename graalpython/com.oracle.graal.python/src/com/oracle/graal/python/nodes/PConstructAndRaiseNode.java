/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.nodes.StringLiterals.T_NAME;
import static com.oracle.graal.python.nodes.StringLiterals.T_PATH;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.ssl.SSLErrorCode;
import com.oracle.graal.python.nodes.PConstructAndRaiseNodeGen.LazyNodeGen;
import com.oracle.graal.python.nodes.call.special.CallVarargsMethodNode;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixException;
import com.oracle.graal.python.runtime.PosixSupportLibrary.UnsupportedPosixFeatureException;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.formatting.ErrorMessageFormatter;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@GenerateUncached
@ImportStatic(PGuards.class)
@SuppressWarnings("truffle-inlining")       // footprint reduction 28 -> 9
public abstract class PConstructAndRaiseNode extends Node {
    public final PException executeWithArgsOnly(Frame frame, PythonBuiltinClassType type, Object[] arguments) {
        return execute(frame, type, null, null, null, arguments, PKeyword.EMPTY_KEYWORDS);
    }

    public final PException executeWithFmtMessageAndArgs(Frame frame, PythonBuiltinClassType type, TruffleString format, Object[] formatArgs, Object[] arguments) {
        return execute(frame, type, null, format, formatArgs, arguments, PKeyword.EMPTY_KEYWORDS);
    }

    public abstract PException execute(Frame frame, PythonBuiltinClassType type, Object cause, TruffleString format, Object[] formatArgs, Object[] arguments, PKeyword[] keywords);

    @TruffleBoundary
    private static TruffleString getFormattedMessage(TruffleString format, Object[] formatArgs) {
        return toTruffleStringUncached(ErrorMessageFormatter.format(format, formatArgs));
    }

    private PException raiseInternal(VirtualFrame frame, PythonBuiltinClassType type, Object cause, Object[] arguments, PKeyword[] keywords,
                    CallVarargsMethodNode callNode, Python3Core core, TruffleString.FromJavaStringNode fromJavaStringNode) {
        if (arguments != null) {
            for (int i = 0; i < arguments.length; ++i) {
                if (arguments[i] instanceof String) {
                    arguments[i] = fromJavaStringNode.execute((String) arguments[i], TS_ENCODING);
                }
            }
        }
        PBaseException error = (PBaseException) callNode.execute(frame, core.lookupType(type), arguments, keywords);
        if (cause != null) {
            error.setContext(cause);
            error.setCause(cause);
        }
        return PRaiseNode.raiseExceptionObject(this, error);
    }

    @Specialization(guards = {"format == null", "formatArgs == null"})
    PException constructAndRaiseNoFormatString(VirtualFrame frame, PythonBuiltinClassType type, Object cause, @SuppressWarnings("unused") TruffleString format,
                    @SuppressWarnings("unused") Object[] formatArgs,
                    Object[] arguments, PKeyword[] keywords,
                    @Cached.Shared("callNode") @Cached CallVarargsMethodNode callNode,
                    @Shared("js2ts") @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
        Python3Core core = PythonContext.get(this);
        return raiseInternal(frame, type, cause, arguments, keywords, callNode, core, fromJavaStringNode);
    }

    @Specialization(guards = {"format != null", "arguments == null"})
    PException constructAndRaiseNoArgs(VirtualFrame frame, PythonBuiltinClassType type, Object cause, TruffleString format, Object[] formatArgs,
                    @SuppressWarnings("unused") Object[] arguments, PKeyword[] keywords,
                    @Cached.Shared("callNode") @Cached CallVarargsMethodNode callNode,
                    @Shared("js2ts") @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
        Python3Core core = PythonContext.get(this);
        Object[] args = new Object[]{formatArgs != null ? getFormattedMessage(format, formatArgs) : format};
        return raiseInternal(frame, type, cause, args, keywords, callNode, core, fromJavaStringNode);
    }

    @Specialization(guards = {"format != null", "arguments != null"})
    PException constructAndRaise(VirtualFrame frame, PythonBuiltinClassType type, Object cause, TruffleString format, Object[] formatArgs,
                    Object[] arguments, PKeyword[] keywords,
                    @Cached.Shared("callNode") @Cached CallVarargsMethodNode callNode,
                    @Shared("js2ts") @Cached TruffleString.FromJavaStringNode fromJavaStringNode) {
        Python3Core core = PythonContext.get(this);
        Object[] args = new Object[arguments.length + 1];
        args[0] = (formatArgs != null) ? getFormattedMessage(format, formatArgs) : format;
        System.arraycopy(arguments, 0, args, 1, arguments.length);
        return raiseInternal(frame, type, cause, args, keywords, callNode, core, fromJavaStringNode);
    }

    // ImportError helpers
    public final PException raiseImportErrorWithModule(Frame frame, Object name, Object path, TruffleString format, Object... formatArgs) {
        return raiseImportErrorInternal(frame, format, formatArgs, new PKeyword[]{new PKeyword(T_NAME, name), new PKeyword(T_PATH, path)});
    }

    public final PException raiseImportError(Frame frame, TruffleString format, Object... formatArgs) {
        return raiseImportErrorInternal(frame, format, formatArgs, PKeyword.EMPTY_KEYWORDS);
    }

    private PException raiseImportErrorInternal(Frame frame, TruffleString format, Object[] formatArgs, PKeyword[] keywords) {
        return execute(frame, PythonBuiltinClassType.ImportError, null, format, formatArgs, null, keywords);
    }

    public final PException raiseImportErrorWithModuleAndCause(Frame frame, Object cause, Object name, Object path, TruffleString format, Object... formatArgs) {
        return execute(frame, PythonBuiltinClassType.ImportError, cause, format, formatArgs, null, new PKeyword[]{new PKeyword(T_NAME, name), new PKeyword(T_PATH, path)});
    }

    // OSError helpers
    @TruffleBoundary
    private static TruffleString getMessage(Exception exception) {
        return toTruffleStringUncached(exception.getMessage());
    }

    private PException raise(Frame frame, PythonBuiltinClassType err, TruffleString message, Object... formatArgs) {
        return executeWithFmtMessageAndArgs(frame, err, message, formatArgs, PythonUtils.EMPTY_OBJECT_ARRAY);
    }

    private static Object[] createOsErrorArgs(int errno, TruffleString message) {
        return new Object[]{errno, message};
    }

    private static Object[] createOsErrorArgs(int errno, TruffleString message, Object filename1) {
        return createOsErrorArgs(errno, message, filename1, null);
    }

    private static Object[] createOsErrorArgs(OSErrorEnum osErrorEnum, TruffleString filename1) {
        return createOsErrorArgs(osErrorEnum.getNumber(), osErrorEnum.getMessage(), filename1, null);
    }

    private static Object[] createOsErrorArgs(int errno, TruffleString message, Object filename1, Object filename2) {
        if (filename1 != null) {
            if (filename2 != null) {
                return new Object[]{errno, message, filename1, 0, filename2};
            }
            return new Object[]{errno, message, filename1};
        }
        assert filename2 == null;
        return new Object[]{errno, message};
    }

    private static Object[] createOsErrorArgs(OSErrorEnum osErrorEnum) {
        return new Object[]{osErrorEnum.getNumber(), osErrorEnum.getMessage()};
    }

    private static Object[] createOsErrorArgs(Exception exception, TruffleString.EqualNode eqNode) {
        OSErrorEnum.ErrorAndMessagePair errorAndMessage = OSErrorEnum.fromException(exception, eqNode);
        return new Object[]{errorAndMessage.oserror.getNumber(), errorAndMessage.message};
    }

    private PException raiseOSErrorInternal(Frame frame, Object[] arguments) {
        return executeWithArgsOnly(frame, PythonBuiltinClassType.OSError, arguments);
    }

    public final PException raiseOSError(Frame frame, OSErrorEnum osErrorEnum) {
        return raiseOSErrorInternal(frame, createOsErrorArgs(osErrorEnum));
    }

    public final PException raiseOSError(Frame frame, OSErrorEnum osErrorEnum, TruffleString filename) {
        return raiseOSErrorInternal(frame, createOsErrorArgs(osErrorEnum, filename));
    }

    public final PException raiseOSError(Frame frame, Exception exception, TruffleString.EqualNode eqNode) {
        return raiseOSErrorInternal(frame, createOsErrorArgs(exception, eqNode));
    }

    public final PException raiseOSError(Frame frame, OSErrorEnum osErrorEnum, Exception exception) {
        return raiseOSError(frame, osErrorEnum, getMessage(exception));
    }

    public final PException raiseOSError(Frame frame, int errno, TruffleString message, Object filename) {
        return raiseOSErrorInternal(frame, createOsErrorArgs(errno, message, filename));
    }

    public final PException raiseOSError(VirtualFrame frame, int errno, TruffleString message) {
        return raiseOSErrorInternal(frame, createOsErrorArgs(errno, message));
    }

    public final PException raiseOSErrorFromPosixException(VirtualFrame frame, PosixException e) {
        return raiseOSErrorInternal(frame, createOsErrorArgs(e.getErrorCode(), e.getMessageAsTruffleString()));
    }

    public final PException raiseOSErrorFromPosixException(VirtualFrame frame, PosixException e, Object filename1) {
        return raiseOSErrorInternal(frame, createOsErrorArgs(e.getErrorCode(), e.getMessageAsTruffleString(), filename1));
    }

    public final PException raiseOSErrorFromPosixException(VirtualFrame frame, PosixException e, Object filename1, Object filename2) {
        return raiseOSErrorInternal(frame, createOsErrorArgs(e.getErrorCode(), e.getMessageAsTruffleString(), filename1, filename2));
    }

    public final PException raiseOSErrorUnsupported(VirtualFrame frame, UnsupportedPosixFeatureException e) {
        return raiseOSError(frame, OSErrorEnum.EINVAL, createUnsupportedErrorMessage(e));
    }

    @TruffleBoundary
    private static TruffleString createUnsupportedErrorMessage(UnsupportedPosixFeatureException e) {
        return TruffleString.fromJavaStringUncached(e.getMessage(), TS_ENCODING);
    }

    public final PException raiseSSLError(Frame frame, TruffleString message) {
        return raiseSSLError(frame, message, PythonUtils.EMPTY_OBJECT_ARRAY);
    }

    private PException raiseSSLError(Frame frame, TruffleString message, Object... formatArgs) {
        return raise(frame, PythonBuiltinClassType.SSLError, message, formatArgs);
    }

    public PException raiseSSLError(Frame frame, SSLErrorCode errorCode, Exception ex) {
        return raiseSSLError(frame, errorCode, getMessage(ex));
    }

    public PException raiseSSLError(Frame frame, SSLErrorCode errorCode, TruffleString format, Object... formatArgs) {
        TruffleString message = getFormattedMessage(format, formatArgs);
        try {
            return executeWithFmtMessageAndArgs(frame, errorCode.getType(), null, null, new Object[]{errorCode.getErrno(), message});
        } catch (PException pException) {
            setSSLErrorAttributes(pException, errorCode, message);
            return pException;
        }
    }

    public static PException raiseUncachedSSLError(TruffleString message) {
        return getUncached().raiseSSLError(null, message);
    }

    public static PException raiseUncachedSSLError(TruffleString message, Object... formatArgs) {
        return getUncached().raiseSSLError(null, message, formatArgs);
    }

    public static PException raiseUncachedSSLError(SSLErrorCode errorCode, Exception ex) {
        return getUncached().raiseSSLError(null, errorCode, ex);
    }

    public static PException raiseUncachedSSLError(SSLErrorCode errorCode, TruffleString format, Object... formatArgs) {
        return getUncached().raiseSSLError(null, errorCode, format, formatArgs);
    }

    private PException raiseOSErrorSubType(Frame frame, PythonBuiltinClassType osErrorSubtype, TruffleString format, Object... fmtArgs) {
        TruffleString message = getFormattedMessage(format, fmtArgs);
        final OSErrorEnum osErrorEnum = errorType2errno(osErrorSubtype);
        assert osErrorEnum != null : "could not determine an errno for this error, either not an OSError subtype or multiple errno codes are available";
        return executeWithArgsOnly(frame, osErrorSubtype, new Object[]{osErrorEnum.getNumber(), message});
    }

    public final PException raiseFileNotFoundError(Frame frame, TruffleString format, Object... fmtArgs) {
        return raiseOSErrorSubType(frame, PythonBuiltinClassType.FileNotFoundError, format, fmtArgs);
    }

    public final PException raiseTimeoutError(Frame frame, TruffleString message) {
        return executeWithArgsOnly(frame, PythonBuiltinClassType.TimeoutError, new Object[]{message});
    }

    public final PException raiseUnicodeEncodeError(Frame frame, String encoding, TruffleString object, int start, int end, String reason) {
        return executeWithArgsOnly(frame, PythonBuiltinClassType.UnicodeEncodeError, new Object[]{encoding, object, start, end, reason});
    }

    @NeverDefault
    public static PConstructAndRaiseNode create() {
        return PConstructAndRaiseNodeGen.create();
    }

    public static PConstructAndRaiseNode getUncached() {
        return PConstructAndRaiseNodeGen.getUncached();
    }

    @GenerateInline
    @GenerateUncached
    @GenerateCached(false)
    public abstract static class Lazy extends Node {
        public static Lazy getUncached() {
            return LazyNodeGen.getUncached();
        }

        public final PConstructAndRaiseNode get(Node inliningTarget) {
            return execute(inliningTarget);
        }

        abstract PConstructAndRaiseNode execute(Node inliningTarget);

        @Specialization
        static PConstructAndRaiseNode doIt(@Cached(inline = false) PConstructAndRaiseNode node) {
            return node;
        }
    }
}
