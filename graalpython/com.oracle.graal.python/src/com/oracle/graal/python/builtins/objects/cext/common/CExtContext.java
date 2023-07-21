/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.nodes.StringLiterals.J_NFI_LANGUAGE;
import static com.oracle.graal.python.nodes.StringLiterals.T_DASH;
import static com.oracle.graal.python.nodes.StringLiterals.T_DOT;
import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_STRING;
import static com.oracle.graal.python.nodes.StringLiterals.T_UNDERSCORE;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.io.IOException;

import com.ibm.icu.impl.Punycode;
import com.ibm.icu.text.StringPrepParseException;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.CheckFunctionResultNode;
import com.oracle.graal.python.builtins.objects.cext.common.LoadCExtException.ApiInitException;
import com.oracle.graal.python.builtins.objects.cext.common.LoadCExtException.ImportException;
import com.oracle.graal.python.builtins.objects.cext.hpy.HPyExternalFunctionNodes.HPyCheckFunctionResultNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.jni.GraalHPyJNIContext;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.str.StringNodes;
import com.oracle.graal.python.builtins.objects.str.StringUtils;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode.LookupAndCallUnaryDynamicNode;
import com.oracle.graal.python.runtime.PosixConstants;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.ExceptionUtils;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.CodeRange;

public abstract class CExtContext {

    // Due to the cycle CExtContext -> CApiContext < CExtContext this needs to be done lazily
    private static TruffleLogger LOGGER;

    private static TruffleLogger getLogger() {
        if (LOGGER == null) {
            LOGGER = CApiContext.getLogger(CExtContext.class);
        }
        return LOGGER;
    }

    protected static final String J_HPY_INIT = "HPyInit_";
    protected static final String J_HPY_MAJOR_VER_FUN = "get_required_hpy_major_version_";
    protected static final String J_HPY_MINOR_VER_FUN = "get_required_hpy_minor_version_";
    private static final TruffleString T_PY_INIT = tsLiteral("PyInit_");
    private static final TruffleString T_PY_INIT_U = tsLiteral("PyInitU_");

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

    /** A cache for C symbols. */
    private DynamicObject symbolCache;

    protected boolean supportsNativeBackend = true;

    public CExtContext(PythonContext context, Object llvmLibrary) {
        this.context = context;
        this.llvmLibrary = llvmLibrary;
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

    public static final class Store extends DynamicObject {
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
     * A simple helper object that just remembers the name and the path of the original module spec
     * object and also keeps a reference to it. This should avoid redundant attribute reads.
     */
    @ValueType
    public static final class ModuleSpec {
        public final TruffleString name;
        public final TruffleString path;
        public final Object originalModuleSpec;
        private TruffleString encodedName;
        private boolean ascii;

        public ModuleSpec(TruffleString name, TruffleString path, Object originalModuleSpec) {
            this.name = name;
            this.path = path;
            this.originalModuleSpec = originalModuleSpec;
        }

        /**
         * Get the variable part of a module's export symbol name. Returns a bytes instance. For
         * non-ASCII-named modules, the name is encoded as per PEP 489. The hook_prefix pointer is
         * set to either ascii_only_prefix or nonascii_prefix, as appropriate.
         */
        @TruffleBoundary
        TruffleString getEncodedName() {
            if (encodedName != null) {
                return encodedName;
            }

            // Get the short name (substring after last dot)
            TruffleString basename = getBaseName(name);

            boolean canEncode = canEncode(basename);

            if (canEncode) {
                ascii = true;
            } else {
                ascii = false;
                try {
                    basename = TruffleString.fromJavaStringUncached(Punycode.encode(basename.toJavaStringUncached(), null).toString(), TS_ENCODING);
                } catch (StringPrepParseException e) {
                    throw CompilerDirectives.shouldNotReachHere();
                }
            }

            // replace '-' by '_'; note: this is fast and does not use regex
            return (encodedName = StringNodes.StringReplaceNode.getUncached().execute(basename, T_DASH, T_UNDERSCORE, -1));
        }

        @TruffleBoundary
        private static boolean canEncode(TruffleString basename) {
            return TruffleString.GetCodeRangeNode.getUncached().execute(basename, TS_ENCODING) == CodeRange.ASCII;
        }

        @TruffleBoundary
        public TruffleString getInitFunctionName() {
            /*
             * n.b.: 'getEncodedName' also sets 'ascii' and must therefore be called before 'ascii'
             * is queried
             */
            TruffleString s = getEncodedName();
            return StringUtils.cat((ascii ? T_PY_INIT : T_PY_INIT_U), s);
        }
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

    private static boolean moduleMatches(String name, String[] modules) {
        for (String module : modules) {
            module = module.trim();
            if (!module.isEmpty()) {
                if (name.equals(module)) {
                    return true;
                }
                if (name.length() > module.length() && name.startsWith(module) && name.charAt(module.length()) == '.') {
                    return true;
                }
            }
        }
        return false;
    }

    private static String dlopenFlagsToString(int flags) {
        String str = "RTLD_NOW";
        if ((flags & PosixConstants.RTLD_LAZY.value) != 0) {
            str = "RTLD_LAZY";
        }
        if ((flags & PosixConstants.RTLD_GLOBAL.value) != 0) {
            str += "|RTLD_GLOBAL";
        }
        return str;
    }

    /**
     * This method loads a C extension module (C API) and will initialize the
     * corresponding native contexts if necessary.
     *
     * @param location The node that's requesting this operation. This is required for reporting
     *            correct source code location in case exceptions occur.
     * @param context The Python context object.
     * @param spec The name and path of the module (also containing the original module spec
     *            object).
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
    public static Object loadCExtModule(Node location, PythonContext context, ModuleSpec spec, CheckFunctionResultNode checkFunctionResultNode, HPyCheckFunctionResultNode checkHPyResultNode)
                    throws IOException, ApiInitException, ImportException {

        // we always need to load the CPython C API (even for HPy modules)
        CApiContext cApiContext = CApiContext.ensureCapiWasLoaded(location, context, spec.name, spec.path);
        Object library = null;

        if (cApiContext.supportsNativeBackend) {
            String nativeModuleOption = context.getOption(PythonOptions.NativeModules);
            boolean loaded = cApiContext.ensureNative();
            if (loaded) {
                String name = spec.name.toJavaStringUncached();
                if (!isForcedLLVM(name) && (nativeModuleOption.equals("all") || moduleMatches(name, nativeModuleOption.split(",")))) {
                    GraalHPyJNIContext.loadJNIBackend();
                    getLogger().config("loading module " + spec.path + " as native");
                    String loadExpr = String.format("load(%s) \"%s\"", dlopenFlagsToString(context.getDlopenFlags()), spec.path);
                    if (PythonOptions.UsePanama.getValue(context.getEnv().getOptions())) {
                        loadExpr = "with panama " + loadExpr;
                    }
                    try {
                        Source src = Source.newBuilder(J_NFI_LANGUAGE, loadExpr, "load " + spec.name).build();
                        library = context.getEnv().parseInternal(src).call();
                    } catch (AbstractTruffleException e) {
                        if (e instanceof PException pe) {
                            throw pe;
                        } else {
                            throw new ImportException(CExtContext.wrapJavaException(e, location), spec.name, spec.path, ErrorMessages.CANNOT_LOAD_M, spec.path, e);
                        }
                    }
                }
            } else {
                cApiContext.supportsNativeBackend = false;
            }
        }

        if (library == null) {
            library = loadLLVMLibrary(location, context, spec.name, spec.path);
            try {
                if (InteropLibrary.getUncached(library).getLanguage(library).toString().startsWith("class com.oracle.truffle.nfi")) {
                    if (cApiContext.supportsNativeBackend) {
                        getLogger().config("loading module " + spec.path + " as native (no bitcode found)");
                    } else {
                        throw PRaiseNode.raiseUncached(null, SystemError, ErrorMessages.CANNOT_MULTICONTEXT);
                    }
                } else {
                    getLogger().config("loading module " + spec.path + " as llvm bitcode");
                }
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
        InteropLibrary llvmInteropLib = InteropLibrary.getUncached(library);

        // Now, try to detect the C extension's API by looking for the appropriate init
        // functions.
        try {
            return cApiContext.initCApiModule(location, library, spec.getInitFunctionName(), spec, llvmInteropLib, checkFunctionResultNode);
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            throw new ImportException(CExtContext.wrapJavaException(e, location), spec.name, spec.path, ErrorMessages.CANNOT_INITIALIZE_WITH, spec.path, spec.getEncodedName(), "");
        }
    }

    private static boolean isForcedLLVM(String name) {
        return "_mmap".equals(name) || "_cpython_struct".equals(name);
    }

    public static Object loadLLVMLibrary(Node location, PythonContext context, TruffleString name, TruffleString path) throws ImportException, IOException {
        Env env = context.getEnv();
        try {
            TruffleString extSuffix = context.getSoAbi();
            CallTarget callTarget = env.parseInternal(Source.newBuilder(J_LLVM_LANGUAGE, context.getPublicTruffleFileRelaxed(path, extSuffix)).build());
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
        PBaseException pythonCause = null;
        PException pcause = null;
        if (e instanceof PException) {
            PBaseException excObj = ((PException) e).getEscapedException();
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
        TruffleString message = toTruffleStringUncached(e.getMessage());
        PBaseException excObject = PythonObjectFactory.getUncached().createBaseException(SystemError, message != null ? message : toTruffleStringUncached(e.toString()),
                        PythonUtils.EMPTY_OBJECT_ARRAY);
        return ExceptionUtils.wrapJavaException(e, raisingNode, excObject);
    }
}
