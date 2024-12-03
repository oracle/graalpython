/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.common;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.nodes.StringLiterals.J_LLVM_LANGUAGE;
import static com.oracle.graal.python.nodes.StringLiterals.T_DOT;
import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_STRING;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import java.io.IOException;

import com.oracle.graal.python.builtins.objects.cext.common.LoadCExtException.ImportException;
import com.oracle.graal.python.builtins.objects.exception.ExceptionNodes;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode.LookupAndCallUnaryDynamicNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.ExceptionUtils;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.TruffleString;

public abstract class CExtContext {
    public static final int METH_VARARGS = 0x0001;
    public static final int METH_KEYWORDS = 0x0002;
    public static final int METH_NOARGS = 0x0004;
    public static final int METH_O = 0x0008;
    public static final int METH_CLASS = 0x0010;
    public static final int METH_STATIC = 0x0020;
    public static final int METH_FASTCALL = 0x0080;
    public static final int METH_METHOD = 0x0200;

    // Filter out only the base convention, without orthogonal modifiers
    private static final int CALL_CONVENTION_MASK = METH_VARARGS | METH_KEYWORDS | METH_NOARGS | METH_O | METH_FASTCALL | METH_METHOD;

    private final PythonContext context;

    /** The LLVM bitcode library object representing 'libpython.*.so' or similar. */
    private final Object llvmLibrary;

    /**
     * The native API implementation was loaded as native code (as opposed to bitcode via Sulong).
     */
    protected final boolean useNativeBackend;

    public CExtContext(PythonContext context, Object llvmLibrary, boolean useNativeBackend) {
        this.context = context;
        this.llvmLibrary = llvmLibrary;
        this.useNativeBackend = useNativeBackend;
    }

    public final PythonContext getContext() {
        return context;
    }

    public final Object getLLVMLibrary() {
        return llvmLibrary;
    }

    public static boolean isMethVarargs(int flags) {
        return (flags & CALL_CONVENTION_MASK) == METH_VARARGS;
    }

    public static boolean isMethVarargsWithKeywords(int flags) {
        return (flags & CALL_CONVENTION_MASK) == (METH_VARARGS | METH_KEYWORDS);
    }

    public static boolean isMethNoArgs(int flags) {
        return (flags & CALL_CONVENTION_MASK) == METH_NOARGS;
    }

    public static boolean isMethO(int flags) {
        return (flags & CALL_CONVENTION_MASK) == METH_O;
    }

    public static boolean isMethFastcall(int flags) {
        return (flags & CALL_CONVENTION_MASK) == METH_FASTCALL;
    }

    public static boolean isMethFastcallWithKeywords(int flags) {
        return (flags & CALL_CONVENTION_MASK) == (METH_FASTCALL | METH_KEYWORDS);
    }

    public static boolean isMethMethod(int flags) {
        return (flags & CALL_CONVENTION_MASK) == (METH_FASTCALL | METH_KEYWORDS | METH_METHOD);
    }

    public static boolean isMethStatic(int flags) {
        return (flags & METH_STATIC) != 0;
    }

    public static boolean isClassOrStaticMethod(int flags) {
        return flags > 0 && (flags & (METH_CLASS | METH_STATIC)) != 0;
    }

    @TruffleBoundary
    protected static TruffleString getBaseName(TruffleString name) {
        int len = TruffleString.CodePointLengthNode.getUncached().execute(name, TS_ENCODING);
        if (len == 1) {
            return name.equalsUncached(T_DOT, TS_ENCODING) ? T_EMPTY_STRING : name;
        }
        int idx = name.lastIndexOfStringUncached(T_DOT, len, 0, TS_ENCODING);
        if (idx < 0) {
            return name;
        }
        if (idx == len - 1) {
            return T_EMPTY_STRING;
        }
        return name.substringUncached(idx + 1, len - idx - 1, TS_ENCODING, true);
    }

    public static Object loadLLVMLibrary(Node location, PythonContext context, TruffleString name, TruffleString path) throws ImportException, IOException {
        Env env = context.getEnv();
        try {
            TruffleString extSuffix = context.getSoAbi();
            TruffleFile realPath = context.getPublicTruffleFileRelaxed(path, extSuffix).getCanonicalFile();
            CallTarget callTarget = env.parseInternal(Source.newBuilder(J_LLVM_LANGUAGE, realPath).build());
            return callTarget.call();
        } catch (SecurityException e) {
            throw new ImportException(CExtContext.wrapJavaException(e, location), name, path, ErrorMessages.CANNOT_LOAD_M, path, e);
        } catch (RuntimeException e) {
            throw reportImportError(e, name, path);
        }
    }

    @TruffleBoundary
    protected static PException reportImportError(RuntimeException e, TruffleString name, TruffleString path) throws ImportException {
        StringBuilder sb = new StringBuilder();
        Object pythonCause = null;
        PException pcause = null;
        if (e instanceof PException) {
            Object excObj = ((PException) e).getEscapedException();
            pythonCause = excObj;
            pcause = (PException) e;
            sb.append(LookupAndCallUnaryDynamicNode.getUncached().executeObject(excObj, SpecialMethodNames.T___REPR__));
        } else {
            // that call will cause problems if the format string contains '%p'
            sb.append(e.getMessage());
        }
        Throwable cause = e;
        while ((cause = cause.getCause()) != null) {
            if (e instanceof PException) {
                Object pythonException = ((PException) e).getEscapedException();
                if (pythonCause != null) {
                    ExceptionNodes.SetCauseNode.executeUncached(pythonCause, pythonException);
                }
                pythonCause = pythonException;
                pcause = (PException) e;
            }
            if (cause.getMessage() != null) {
                sb.append(", ");
                sb.append(cause.getMessage());
            }
        }
        Object[] args = new Object[]{path, sb.toString()};
        if (pythonCause != null) {
            throw new ImportException(pcause, name, path, ErrorMessages.CANNOT_LOAD, args);
        } else {
            throw new ImportException(null, name, path, ErrorMessages.CANNOT_LOAD, args);
        }
    }

    @TruffleBoundary
    public static PException wrapJavaException(Throwable e, Node raisingNode) {
        TruffleString message = toTruffleStringUncached(e.getMessage());
        PBaseException excObject = PythonObjectFactory.getUncached().createBaseException(SystemError, message != null ? message : toTruffleStringUncached(e.toString()),
                        PythonUtils.EMPTY_OBJECT_ARRAY);
        return ExceptionUtils.wrapJavaException(e, raisingNode, excObject);
    }
}
