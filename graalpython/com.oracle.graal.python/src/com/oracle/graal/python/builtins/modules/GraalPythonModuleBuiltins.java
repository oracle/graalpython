/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.nodes.BuiltinNames.__GRAALPYTHON__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__NAME__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ImportError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.NotImplementedError;

import java.io.PrintWriter;
import java.util.List;
import java.util.logging.Level;

import org.graalvm.nativeimage.ImageInfo;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.builtins.objects.generator.PGenerator;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.method.PMethod;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.argument.ReadIndexedArgumentNode;
import com.oracle.graal.python.nodes.argument.ReadVarArgsNode;
import com.oracle.graal.python.nodes.function.FunctionRootNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.subscript.GetItemNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.CachedLanguage;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.llvm.api.Toolchain;

@CoreFunctions(defineModule = __GRAALPYTHON__)
public class GraalPythonModuleBuiltins extends PythonBuiltins {
    public static final String LLVM_LANGUAGE = "llvm";
    private static final TruffleLogger LOGGER = PythonLanguage.getLogger(GraalPythonModuleBuiltins.class);

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return GraalPythonModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(PythonCore core) {
        super.initialize(core);
        builtinConstants.put("is_native", TruffleOptions.AOT);
        PythonContext ctx = core.getContext();
        String encodingOpt = ctx.getLanguage().getEngineOption(PythonOptions.StandardStreamEncoding);
        String standardStreamEncoding = null;
        String standardStreamError = null;
        if (encodingOpt != null && !encodingOpt.isEmpty()) {
            String[] parts = encodingOpt.split(":");
            if (parts.length > 0) {
                standardStreamEncoding = parts[0].isEmpty() ? "utf-8" : parts[0];
                standardStreamError = parts.length > 1 && !parts[1].isEmpty() ? parts[1] : "strict";
            }
        }
        if (standardStreamEncoding == null) {
            standardStreamEncoding = "utf-8";
            standardStreamError = "surrogateescape";
        }

        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine(String.format("Setting default stdio encoding to %s:%s", standardStreamEncoding, standardStreamError));
        }
        this.builtinConstants.put("stdio_encoding", standardStreamEncoding);
        this.builtinConstants.put("stdio_error", standardStreamError);
        // we need these during core initialization, they are re-set in postInitialize
        postInitialize(core);
    }

    @Override
    public void postInitialize(PythonCore core) {
        super.postInitialize(core);
        PythonContext context = core.getContext();
        PythonModule mod = core.lookupBuiltinModule(__GRAALPYTHON__);
        PythonLanguage language = context.getLanguage();
        if (!ImageInfo.inImageBuildtimeCode()) {
            mod.setAttribute("home", language.getHome());
        }
        mod.setAttribute("in_image_buildtime", ImageInfo.inImageBuildtimeCode());
        mod.setAttribute("in_image", ImageInfo.inImageCode());
        String coreHome = context.getCoreHome();
        String stdlibHome = context.getStdlibHome();
        String capiHome = context.getCAPIHome();
        Env env = context.getEnv();
        LanguageInfo llvmInfo = env.getInternalLanguages().get(LLVM_LANGUAGE);
        Toolchain toolchain = env.lookup(llvmInfo, Toolchain.class);
        mod.setAttribute("jython_emulation_enabled", language.getEngineOption(PythonOptions.EmulateJython));
        mod.setAttribute("host_import_enabled", context.getEnv().isHostLookupAllowed());
        mod.setAttribute("core_home", coreHome);
        mod.setAttribute("stdlib_home", stdlibHome);
        mod.setAttribute("capi_home", capiHome);
        mod.setAttribute("platform_id", toolchain.getIdentifier());
        mod.setAttribute("flags", core.factory().createTuple(new Object[]{
                        0, // bytes_warning
                        PInt.intValue(!context.getOption(PythonOptions.PythonOptimizeFlag)), // debug
                        PInt.intValue(context.getOption(PythonOptions.DontWriteBytecodeFlag)),  // dont_write_bytecode
                        0, // hash_randomization
                        PInt.intValue(context.getOption(PythonOptions.IgnoreEnvironmentFlag)), // ignore_environment
                        PInt.intValue(context.getOption(PythonOptions.InspectFlag)), // inspect
                        PInt.intValue(context.getOption(PythonOptions.TerminalIsInteractive)), // interactive
                        PInt.intValue(context.getOption(PythonOptions.IsolateFlag)), // isolated
                        PInt.intValue(context.getOption(PythonOptions.NoSiteFlag)), // no_site
                        PInt.intValue(context.getOption(PythonOptions.NoUserSiteFlag)), // no_user_site
                        PInt.intValue(context.getOption(PythonOptions.PythonOptimizeFlag)), // optimize
                        PInt.intValue(context.getOption(PythonOptions.QuietFlag)), // quiet
                        PInt.intValue(context.getOption(PythonOptions.VerboseFlag)), // verbose
                        false, // dev_mode
                        0, // utf8_mode
        }));
    }

    @Builtin(name = "cache_module_code", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class CacheModuleCode extends PythonTernaryBuiltinNode {
        private static final TruffleLogger LOGGER = PythonLanguage.getLogger(CacheModuleCode.class);

        @Specialization
        public Object run(String modulename, String moduleFile, @SuppressWarnings("unused") PNone modulepath,
                        @Shared("ctxt") @CachedContext(PythonLanguage.class) PythonContext ctxt,
                        @Shared("lang") @CachedLanguage PythonLanguage lang) {
            return doCache(modulename, moduleFile, PythonUtils.EMPTY_STRING_ARRAY, ctxt, lang);
        }

        @Specialization
        public Object run(String modulename, String moduleFile, PList modulepath,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @Shared("cast") @Cached CastToJavaStringNode castString,
                        @Shared("ctxt") @CachedContext(PythonLanguage.class) PythonContext ctxt,
                        @Shared("lang") @CachedLanguage PythonLanguage lang) {
            SequenceStorage sequenceStorage = modulepath.getSequenceStorage();
            int n = lenNode.execute(sequenceStorage);
            Object[] pathList = sequenceStorage.getInternalArray();
            assert n <= pathList.length;
            String[] paths = new String[n];
            for (int i = 0; i < n; i++) {
                try {
                    paths[i] = castString.execute(pathList[i]);
                } catch (CannotCastException e) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new IllegalStateException();
                }
            }
            return doCache(modulename, moduleFile, paths, ctxt, lang);
        }

        private Object doCache(String modulename, String moduleFile, String[] modulepath, PythonContext ctxt, PythonLanguage lang) {
            assert !ctxt.isInitialized() : "this can only be called during initialization";
            final CallTarget ct = lang.cacheCode(moduleFile, () -> null);
            if (ct == null) {
                throw raise(NotImplementedError, "cannot cache something we haven't cached before");
            }
            return cacheWithModulePath(modulename, modulepath, lang, ct);
        }

        private static Object cacheWithModulePath(String modulename, String[] modulepath, PythonLanguage lang, final CallTarget ct) {
            CallTarget cachedCt = lang.cacheCode(modulename, () -> ct, modulepath);
            if (cachedCt != ct) {
                LOGGER.log(Level.WARNING, () -> "Invalid attempt to re-cache " + modulename);
            }
            return PNone.NONE;
        }

        @Specialization
        public Object run(String modulename, PCode code, @SuppressWarnings("unused") PNone modulepath,
                        @CachedLanguage PythonLanguage lang) {
            final CallTarget ct = code.getRootCallTarget();
            if (ct == null) {
                throw raise(NotImplementedError, "cannot cache a synthetically constructed code object");
            }
            return cacheWithModulePath(modulename, PythonUtils.EMPTY_STRING_ARRAY, lang, ct);
        }

        @Specialization
        public Object run(String modulename, PCode code, PList modulepath,
                        @Shared("cast") @Cached CastToJavaStringNode castString,
                        @CachedLanguage PythonLanguage lang) {
            final CallTarget ct = code.getRootCallTarget();
            if (ct == null) {
                throw raise(NotImplementedError, "cannot cache a synthetically constructed code object");
            }
            Object[] pathList = modulepath.getSequenceStorage().getInternalArray();
            String[] paths = new String[pathList.length];
            for (int i = 0; i < pathList.length; i++) {
                try {
                    paths[i] = castString.execute(pathList[i]);
                } catch (CannotCastException e) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new IllegalStateException();
                }
            }
            return cacheWithModulePath(modulename, paths, lang, ct);
        }
    }

    @Builtin(name = "has_cached_code", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class HasCachedCode extends PythonUnaryBuiltinNode {
        private static final TruffleLogger LOGGER = PythonLanguage.getLogger(HasCachedCode.class);

        @Specialization
        public boolean run(String modulename,
                        @CachedContext(PythonLanguage.class) PythonContext ctxt,
                        @CachedLanguage PythonLanguage lang) {
            boolean b = ctxt.getOption(PythonOptions.WithCachedSources) && lang.hasCachedCode(modulename);
            if (b) {
                LOGGER.log(Level.FINEST, () -> "Cached code re-used for " + modulename);
            }
            return b;
        }
    }

    @Builtin(name = "get_cached_code_path", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class CachedCodeIsPackage extends PythonUnaryBuiltinNode {
        private static final TruffleLogger LOGGER = PythonLanguage.getLogger(CachedCodeIsPackage.class);

        @Specialization
        public Object run(String modulename,
                        @CachedContext(PythonLanguage.class) PythonContext ctxt,
                        @CachedLanguage PythonLanguage lang) {
            String[] modulePath = null;
            if (ctxt.getOption(PythonOptions.WithCachedSources)) {
                modulePath = lang.cachedCodeModulePath(modulename);
            }
            if (modulePath != null) {
                Object[] outPath = new Object[modulePath.length];
                PythonUtils.arraycopy(modulePath, 0, outPath, 0, modulePath.length);
                LOGGER.log(Level.FINEST, () -> "Cached code re-used for " + modulename);
                return factory().createList(outPath);
            } else {
                return PNone.NONE;
            }
        }

        @Fallback
        public Object run(@SuppressWarnings("unused") Object modulename) {
            return PNone.NONE;
        }
    }

    @Builtin(name = "get_cached_code", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class GetCachedCode extends PythonUnaryBuiltinNode {
        @Specialization
        public Object run(String modulename,
                        @CachedLanguage PythonLanguage lang) {
            final CallTarget ct = lang.cacheCode(modulename, () -> null);
            if (ct == null) {
                throw raise(ImportError, ErrorMessages.NO_CACHED_CODE, modulename);
            } else {
                return factory().createCode((RootCallTarget) ct);
            }
        }
    }

    @Builtin(name = "dump_truffle_ast", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class DumpTruffleAstNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        public String doIt(PFunction func) {
            return NodeUtil.printTreeToString(func.getCallTarget().getRootNode());
        }

        @Specialization(guards = "isFunction(method.getFunction())")
        @TruffleBoundary
        public String doIt(PMethod method) {
            // cast ensured by guard
            PFunction fun = (PFunction) method.getFunction();
            return NodeUtil.printTreeToString(fun.getCallTarget().getRootNode());
        }

        @Specialization
        @TruffleBoundary
        public String doIt(PGenerator gen) {
            return NodeUtil.printTreeToString(gen.getCurrentCallTarget().getRootNode());
        }

        @Specialization
        @TruffleBoundary
        public String doIt(PCode code) {
            return NodeUtil.printTreeToString(code.getRootNode());
        }

        @Fallback
        @TruffleBoundary
        public Object doit(Object object) {
            return "truffle ast dump not supported for " + object.toString();
        }

        protected static boolean isFunction(Object callee) {
            return callee instanceof PFunction;
        }
    }

    @Builtin(name = "current_import", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class CurrentImport extends PythonBuiltinNode {
        @Specialization
        String doIt() {
            return getContext().getCurrentImport();
        }
    }

    @Builtin(name = "tdebug", takesVarArgs = true)
    @GenerateNodeFactory
    public abstract static class DebugNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        public Object doIt(Object[] args) {
            PrintWriter stdout = new PrintWriter(getContext().getStandardOut());
            for (int i = 0; i < args.length; i++) {
                stdout.println(args[i]);
            }
            stdout.flush();
            return PNone.NONE;
        }
    }

    @Builtin(name = "builtin", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class BuiltinNode extends PythonUnaryBuiltinNode {
        @Child private GetItemNode getNameNode = GetItemNode.create();

        @Specialization
        public Object doIt(VirtualFrame frame, PFunction func) {
            PFunction builtinFunc = convertToBuiltin(func);
            PythonObject globals = func.getGlobals();
            PythonModule builtinModule;
            if (globals instanceof PythonModule) {
                builtinModule = (PythonModule) globals;
            } else {
                String moduleName = (String) getNameNode.execute(frame, globals, __NAME__);
                builtinModule = getCore().lookupBuiltinModule(moduleName);
                assert builtinModule != null;
            }
            return factory().createBuiltinMethod(builtinModule, builtinFunc);
        }

        @TruffleBoundary
        public synchronized PFunction convertToBuiltin(PFunction func) {
            /*
             * (tfel): To be compatible with CPython, builtin module functions must be bound to
             * their respective builtin module. We ignore that builtin functions should really be
             * builtin methods here - it does not hurt if they are normal methods. What does hurt,
             * however, is if they are not bound, because then using these functions in class field
             * won't work when they are called from an instance of that class due to the implicit
             * currying with "self".
             */
            Signature signature = func.getSignature();
            PFunction builtinFunc;
            FunctionRootNode functionRootNode = (FunctionRootNode) func.getFunctionRootNode();
            if (signature.getParameterIds().length > 0 && signature.getParameterIds()[0].equals("self")) {
                /*
                 * If the first parameter is called self, we assume the function does explicitly
                 * declare the module argument
                 */
                builtinFunc = func;
                functionRootNode.setPythonInternal(true);
            } else {
                /*
                 * Otherwise, we create a new function with a signature that requires one extra
                 * argument in front. We actually modify the function's AST here, so the original
                 * PFunction cannot be used anymore (its signature won't agree with it's indexed
                 * parameter reads).
                 */
                assert !functionRootNode.isPythonInternal() : "a function cannot be rewritten as builtin twice";
                functionRootNode = functionRootNode.rewriteWithNewSignature(signature.createWithSelf(), new NodeVisitor() {

                    @Override
                    public boolean visit(Node node) {
                        if (node instanceof ReadVarArgsNode) {
                            ReadVarArgsNode varArgsNode = (ReadVarArgsNode) node;
                            node.replace(ReadVarArgsNode.create(varArgsNode.getIndex() + 1, varArgsNode.isBuiltin()));
                        } else if (node instanceof ReadIndexedArgumentNode) {
                            node.replace(ReadIndexedArgumentNode.create(((ReadIndexedArgumentNode) node).getIndex() + 1));
                        }
                        return true;
                    }
                });

                String name = func.getName();
                builtinFunc = factory().createFunction(name, func.getEnclosingClassName(),
                                factory().createCode(PythonUtils.getOrCreateCallTarget(functionRootNode)),
                                func.getGlobals(), func.getDefaults(), func.getKwDefaults(), func.getClosure());
            }

            return builtinFunc;
        }
    }

    @Builtin(name = "builtin_method", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class BuiltinMethodNode extends PythonUnaryBuiltinNode {
        @Specialization
        public Object doIt(PFunction func) {
            FunctionRootNode functionRootNode = (FunctionRootNode) func.getFunctionRootNode();
            functionRootNode.setPythonInternal(true);
            return func;
        }
    }

    @Builtin(name = "get_toolchain_path", minNumOfPositionalArgs = 1)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class GetToolPathNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        protected Object getToolPath(String tool) {
            Env env = getContext().getEnv();
            LanguageInfo llvmInfo = env.getInternalLanguages().get(LLVM_LANGUAGE);
            Toolchain toolchain = env.lookup(llvmInfo, Toolchain.class);
            TruffleFile toolPath = toolchain.getToolPath(tool);
            if (toolPath == null) {
                return PNone.NONE;
            }
            return toolPath.toString();
        }
    }

    // Equivalent of PyObject_TypeCheck
    @Builtin(name = "type_check", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class TypeCheckNode extends PythonBinaryBuiltinNode {
        @Specialization(limit = "3")
        boolean typeCheck(Object instance, Object cls,
                        @CachedLibrary("instance") PythonObjectLibrary lib) {
            return lib.typeCheck(instance, cls);
        }
    }
}
