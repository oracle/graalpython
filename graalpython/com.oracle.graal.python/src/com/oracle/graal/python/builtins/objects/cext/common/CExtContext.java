/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.CheckFunctionResultNode;
import com.oracle.graal.python.builtins.objects.cext.common.LoadCExtException.ApiInitException;
import com.oracle.graal.python.builtins.objects.cext.common.LoadCExtException.ImportException;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext;
import com.oracle.graal.python.builtins.objects.cext.hpy.HPyExternalFunctionNodes.HPyCheckFunctionResultNode;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode.LookupAndCallUnaryDynamicNode;
import com.oracle.graal.python.nodes.statement.ExceptionHandlingStatementNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.source.Source;

public abstract class CExtContext {

    public static final CExtContext LAZY_CONTEXT = new CExtContext(null, null, null) {
        @Override
        protected Store initializeSymbolCache() {
            return null;
        }
    };

    public static final int METH_VARARGS = 0x0001;
    public static final int METH_KEYWORDS = 0x0002;
    public static final int METH_NOARGS = 0x0004;
    public static final int METH_O = 0x0008;
    public static final int METH_CLASS = 0x0010;
    public static final int METH_STATIC = 0x0020;
    public static final int METH_FASTCALL = 0x0080;

    private final PythonContext context;

    /** The LLVM bitcode library object representing 'libpython.*.so' or similar. */
    private final Object llvmLibrary;

    /** A factory for creating context-specific conversion nodes. */
    private final ConversionNodeSupplier supplier;

    /** A cache for C symbols. */
    private DynamicObject symbolCache;

    public CExtContext(PythonContext context, Object llvmLibrary, ConversionNodeSupplier supplier) {
        this.context = context;
        this.llvmLibrary = llvmLibrary;
        this.supplier = supplier;
    }

    public final PythonContext getContext() {
        return context;
    }

    public final Object getLLVMLibrary() {
        return llvmLibrary;
    }

    public final ConversionNodeSupplier getSupplier() {
        return supplier;
    }

    public static boolean isMethVarargs(int flags) {
        return (flags & METH_VARARGS) != 0;
    }

    public static boolean isMethKeywords(int flags) {
        return (flags & METH_KEYWORDS) != 0;
    }

    public static boolean isMethNoArgs(int flags) {
        return (flags & METH_NOARGS) != 0;
    }

    public static boolean isMethO(int flags) {
        return (flags & METH_O) != 0;
    }

    @SuppressWarnings("unused")
    public static boolean isMethFastcall(int flags) {
        return (flags & METH_FASTCALL) != 0 && !isMethFastcallWithKeywords(flags);
    }

    public static boolean isMethFastcallWithKeywords(int flags) {
        return (flags & METH_FASTCALL) != 0 && (flags & METH_KEYWORDS) != 0;
    }

    protected static final class Store extends DynamicObject {
        public Store(Shape shape) {
            super(shape);
        }
    }

    public final DynamicObject getSymbolCache() {
        if (symbolCache == null) {
            symbolCache = initializeSymbolCache();
        }
        return symbolCache;
    }

    protected abstract Store initializeSymbolCache();

    /**
     * This method loads a C extension module (C API or HPy API) and will initialize the
     * corresponding native contexts if necessary.
     * 
     * @param location The node that's requesting this operation. This is required for reporting
     *            correct source code location in case exceptions occur.
     * @param context The Python context object.
     * @param name The name of the module to load (also just required for creating appropriate error
     *            messages).
     * @param path The path of the C extension module to load (usually something ending with
     *            {@code .so} or {@code .dylib} or similar).
     * @param interop An interop library instance. It can also be the uncached instance but cached
     *            ones are useful if this method is repeatedly called.
     * @param checkFunctionResultNode An adopted node instance. This is necessary because the result
     *            check could raise an exception and only an adopted node will report useful source
     *            locations.
     * @param checkHPyResultNode Similar to {@code checkFunctionResultNode} but for an HPy
     *            extension.
     * @return A Python module.
     * @throws IOException If the specified file cannot be loaded.
     * @throws ApiInitException If the corresponding native context could not be initialized.
     * @throws ImportException If an exception occurred during C extension initialization.
     */
    @TruffleBoundary
    public static Object loadCExtModule(Node location, PythonContext context, String name, String path,
                    InteropLibrary interop,
                    CheckFunctionResultNode checkFunctionResultNode,
                    HPyCheckFunctionResultNode checkHPyResultNode) throws IOException, ApiInitException, ImportException {
        // we always need to load the CPython C API (even for HPy modules)
        CApiContext cApiContext = CApiContext.ensureCapiWasLoaded(location, context, name, path);
        Object llvmLibrary = loadLLVMLibrary(location, context, name, path);

        // Now, try to detect the C extension's API by looking for the appropriate init
        // functions.
        String basename = name.substring(name.lastIndexOf('.') + 1);
        String hpyInitFuncName = "HPyInit_" + basename;
        String initFuncName = "PyInit_" + basename;
        try {
            if (interop.isMemberExisting(llvmLibrary, hpyInitFuncName)) {
                GraalHPyContext hpyContext = GraalHPyContext.ensureHPyWasLoaded(location, context, name, path);
                return hpyContext.initHPyModule(context, llvmLibrary, hpyInitFuncName, name, path, false, interop, checkHPyResultNode);
            }
            return cApiContext.initCApiModule(location, llvmLibrary, initFuncName, name, path, interop, checkFunctionResultNode);
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            throw new ImportException(CExtContext.wrapJavaException(e, location), name, path, ErrorMessages.CANNOT_INITIALIZE_WITH, path, basename, "");
        }
    }

    protected static TruffleObject loadLLVMLibrary(Node location, PythonContext context, String name, String path) throws ImportException, IOException {
        Env env = context.getEnv();
        try {
            String extSuffix = context.getSoAbi();
            CallTarget callTarget = env.parseInternal(Source.newBuilder(PythonLanguage.LLVM_LANGUAGE, context.getPublicTruffleFileRelaxed(path, extSuffix)).build());
            return (TruffleObject) callTarget.call();
        } catch (SecurityException e) {
            throw new ImportException(CExtContext.wrapJavaException(e, location), name, path, ErrorMessages.CANNOT_LOAD_M, path, e);
        } catch (RuntimeException e) {
            throw reportImportError(e, name, path);
        }
    }

    @TruffleBoundary
    protected static PException reportImportError(RuntimeException e, String name, String path) throws ImportException {
        StringBuilder sb = new StringBuilder();
        PBaseException pythonCause = null;
        PException pcause = null;
        if (e instanceof PException) {
            PBaseException excObj = ((PException) e).getEscapedException();
            pythonCause = excObj;
            pcause = (PException) e;
            sb.append(LookupAndCallUnaryDynamicNode.getUncached().executeObject(excObj, SpecialMethodNames.__REPR__));
        } else {
            // that call will cause problems if the format string contains '%p'
            sb.append(e.getMessage());
        }
        Throwable cause = e;
        while ((cause = cause.getCause()) != null) {
            if (e instanceof PException) {
                PBaseException pythonException = ((PException) e).getEscapedException();
                if (pythonCause != null) {
                    pythonCause.setCause(pythonException);
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
        String message = e.getMessage();
        PBaseException excObject = PythonObjectFactory.getUncached().createBaseException(SystemError, message != null ? message : e.toString(), PythonUtils.EMPTY_OBJECT_ARRAY);
        return ExceptionHandlingStatementNode.wrapJavaException(e, raisingNode, excObject);
    }
}
