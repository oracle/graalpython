/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.modules.ImpModuleBuiltins.FrozenStatus.FROZEN_DISABLED;
import static com.oracle.graal.python.builtins.modules.ImpModuleBuiltins.FrozenStatus.FROZEN_EXCLUDED;
import static com.oracle.graal.python.builtins.modules.ImpModuleBuiltins.FrozenStatus.FROZEN_INVALID;
import static com.oracle.graal.python.builtins.modules.ImpModuleBuiltins.FrozenStatus.FROZEN_NOT_FOUND;
import static com.oracle.graal.python.builtins.modules.ImpModuleBuiltins.FrozenStatus.FROZEN_OKAY;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___LOADER__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___ORIGNAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___PATH__;
import static com.oracle.graal.python.nodes.StringLiterals.T_EXT_PYD;
import static com.oracle.graal.python.nodes.StringLiterals.T_EXT_SO;
import static com.oracle.graal.python.nodes.StringLiterals.T_NAME;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.NotImplementedError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ArgumentClinic.ClinicConversion;
import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.MarshalModuleBuiltins.Marshal.MarshalError;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext.ModuleSpec;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.common.LoadCExtException.ApiInitException;
import com.oracle.graal.python.builtins.objects.cext.common.LoadCExtException.ImportException;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.module.FrozenModules;
import com.oracle.graal.python.builtins.objects.module.PythonFrozenModule;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.str.StringNodes;
import com.oracle.graal.python.compiler.CodeUnit;
import com.oracle.graal.python.compiler.Compiler;
import com.oracle.graal.python.lib.PyMemoryViewFromObject;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectSetAttr;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PConstructAndRaiseNode;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToPythonObjectNode;
import com.oracle.graal.python.nodes.call.CallDispatchers;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.memory.ByteArraySupport;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.utilities.TriState;

@CoreFunctions(defineModule = ImpModuleBuiltins.J__IMP, isEager = true)
public final class ImpModuleBuiltins extends PythonBuiltins {

    public static final TruffleString T_ORIGIN = tsLiteral("origin");
    static final String J__IMP = "_imp";
    public static final TruffleString T__IMP = tsLiteral(J__IMP);

    private static class FrozenResult {
        final FrozenStatus status;
        final FrozenInfo info;

        FrozenResult(FrozenStatus status) {
            this(status, null);
        }

        FrozenResult(FrozenStatus status, FrozenInfo info) {
            this.status = status;
            this.info = info;
        }
    }

    private static class FrozenInfo {
        @SuppressWarnings("unused") final TruffleString name;
        final CodeUnit code;
        final boolean isPackage;
        final TruffleString origName;
        @SuppressWarnings("unused") final boolean isAlias;

        FrozenInfo(TruffleString name, CodeUnit code, boolean isPackage, TruffleString origName, boolean isAlias) {
            this.name = name;
            this.code = code;
            this.isPackage = isPackage;
            this.origName = origName;
            this.isAlias = isAlias;
        }
    }

    enum FrozenStatus {
        FROZEN_OKAY,
        FROZEN_BAD_NAME,    // The given module name wasn't valid.
        FROZEN_NOT_FOUND,   // It wasn't in frozen modules.
        FROZEN_DISABLED,    // -X frozen_modules=off (and not essential)
        FROZEN_EXCLUDED,    // Frozen module has no code. We don't use this in our frozen modules
        FROZEN_INVALID,     // Frozen module has empty code
    }

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ImpModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void postInitialize(Python3Core core) {
        super.postInitialize(core);
        PythonContext context = core.getContext();
        PythonModule mod = core.lookupBuiltinModule(T__IMP);
        mod.setAttribute(tsLiteral("check_hash_based_pycs"), context.getOption(PythonOptions.CheckHashPycsMode));
    }

    @Builtin(name = "acquire_lock")
    @GenerateNodeFactory
    public abstract static class AcquireLock extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        public Object run(@Cached GilNode gil) {
            gil.release(true);
            try {
                TruffleSafepoint.setBlockedThreadInterruptible(this, ReentrantLock::lockInterruptibly, getContext().getImportLock());
            } finally {
                gil.acquire();
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "release_lock")
    @GenerateNodeFactory
    public abstract static class ReleaseLockNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        public Object run() {
            ReentrantLock importLock = getContext().getImportLock();
            if (importLock.isHeldByCurrentThread()) {
                importLock.unlock();
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "lock_held")
    @GenerateNodeFactory
    public abstract static class LockHeld extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        public boolean run() {
            ReentrantLock importLock = getContext().getImportLock();
            return importLock.isHeldByCurrentThread();
        }
    }

    @Builtin(name = "get_magic")
    @GenerateNodeFactory
    public abstract static class GetMagic extends PythonBuiltinNode {
        static final int MAGIC_NUMBER = 21000 + Compiler.BYTECODE_VERSION * 10;
        static final byte[] MAGIC_NUMBER_BYTES = new byte[4];
        static {
            ByteArraySupport.littleEndian().putInt(MAGIC_NUMBER_BYTES, 0, MAGIC_NUMBER);
            MAGIC_NUMBER_BYTES[2] = '\r';
            MAGIC_NUMBER_BYTES[3] = '\n';
        }

        @Specialization(guards = "isSingleContext()")
        PBytes runCachedSingleContext(
                        @Cached(value = "getMagicNumberPBytes()", weak = true) PBytes magicBytes) {
            return magicBytes;
        }

        @Specialization(replaces = "runCachedSingleContext")
        PBytes run(
                        @Bind PythonLanguage language) {
            return PFactory.createBytes(language, MAGIC_NUMBER_BYTES);
        }

        protected PBytes getMagicNumberPBytes() {
            return PFactory.createBytes(PythonLanguage.get(this), MAGIC_NUMBER_BYTES);
        }
    }

    @Builtin(name = "exec_dynamic", minNumOfPositionalArgs = 1, doc = "exec_dynamic($module, mod, /)\n--\n\nInitialize an extension module.")
    @GenerateNodeFactory
    public abstract static class ExecDynamicNode extends PythonBuiltinNode {
        @Specialization
        static int doPythonModule(VirtualFrame frame, PythonModule extensionModule,
                        @Bind PythonContext context,
                        @Bind Node inliningTarget,
                        @Cached("createFor($node)") IndirectCallData indirectCallData) {
            Object nativeModuleDef = extensionModule.getNativeModuleDef();
            if (nativeModuleDef == null) {
                return 0;
            }
            PythonLanguage language = context.getLanguage(inliningTarget);
            Object state = IndirectCallContext.enter(frame, language, context, indirectCallData);
            try {
                return doExec(inliningTarget, context, extensionModule, nativeModuleDef);
            } finally {
                IndirectCallContext.exit(frame, language, context, state);
            }
        }

        @TruffleBoundary
        private static int doExec(Node node, PythonContext context, PythonModule extensionModule, Object nativeModuleDef) {
            /*
             * Check if module is already initialized. CPython does that by testing if 'md_state !=
             * NULL'. So, we do the same.
             */
            Object mdState = extensionModule.getNativeModuleState();
            if (mdState != null && !InteropLibrary.getUncached().isNull(mdState)) {
                return 0;
            }

            if (!context.hasCApiContext()) {
                throw PRaiseNode.raiseStatic(node, PythonBuiltinClassType.SystemError, ErrorMessages.CAPI_NOT_YET_INITIALIZED);
            }

            /*
             * ExecModuleNode will run the module definition's exec function which may run arbitrary
             * C code. So we need to setup an indirect call.
             */
            return CExtNodes.execModule(node, context.getCApiContext(), extensionModule, nativeModuleDef);
        }

        @Fallback
        static int doOther(@SuppressWarnings("unused") Object extensionModule) {
            return 0;
        }
    }

    @Builtin(name = "is_builtin", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"name"})
    @ArgumentClinic(name = "name", conversion = ClinicConversion.TString)
    @GenerateNodeFactory
    public abstract static class IsBuiltin extends PythonUnaryClinicBuiltinNode {

        @Specialization
        @TruffleBoundary
        public int run(TruffleString name) {
            if (getContext().lookupBuiltinModule(name) != null) {
                // TODO: missing "1" case when the builtin module can be re-initialized
                return -1;
            } else {
                return 0;
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ImpModuleBuiltinsClinicProviders.IsBuiltinClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "create_builtin", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class CreateBuiltin extends PythonBuiltinNode {

        public static final TruffleString T_LOADER = tsLiteral("loader");

        @Specialization
        @TruffleBoundary
        static Object run(PythonObject moduleSpec,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context) {
            Object name = PyObjectLookupAttr.executeUncached(moduleSpec, T_NAME);
            TruffleString nameStr = StringNodes.CastToTruffleStringChecked0Node.getUncached().cast(null, name, ErrorMessages.BAD_ARG_TO_INTERNAL_FUNC);
            PythonModule builtinModule = context.lookupBuiltinModule(nameStr);
            if (builtinModule != null) {
                // TODO: GR-26411 builtin modules cannot be re-initialized (see is_builtin)
                // We are setting the loader to the spec loader (since this is the loader that is
                // set during bootstrap); this, however, should be handled be the builtin module
                // reinitialization (if reinit is possible)
                Object loader = PyObjectLookupAttr.executeUncached(moduleSpec, T_LOADER);
                if (loader != PNone.NO_VALUE) {
                    PyObjectSetAttr.executeUncached(builtinModule, T___LOADER__, loader);
                }
                return builtinModule;
            }
            throw PRaiseNode.raiseStatic(inliningTarget, NotImplementedError, toTruffleStringUncached("_imp.create_builtin"));
        }
    }

    @Builtin(name = "exec_builtin", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ExecBuiltin extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        public Object exec(PythonModule pythonModule) {
            final PythonContext context = getContext();
            if (!context.getEnv().isPreInitialization()) {
                final PythonBuiltins builtins = pythonModule.getBuiltins();
                assert builtins != null; // this is a builtin, therefore its builtins must have been
                                         // set at this point
                if (!builtins.isInitialized()) {
                    doPostInit(context, builtins);
                    builtins.setInitialized(true);
                }
            }
            return PNone.NONE;
        }

        @TruffleBoundary
        private static void doPostInit(Python3Core core, PythonBuiltins builtins) {
            builtins.postInitialize(core);
        }
    }

    @Builtin(name = "is_frozen", parameterNames = {"name"}, minNumOfPositionalArgs = 1, doc = "is_frozen($module, name, /)\\n\"\n" +
                    "--\n" +
                    "\n" +
                    "Returns True if the module name corresponds to a frozen module.")
    @GenerateNodeFactory
    @ArgumentClinic(name = "name", conversion = ArgumentClinic.ClinicConversion.TString)
    abstract static class IsFrozen extends PythonUnaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ImpModuleBuiltinsClinicProviders.IsFrozenClinicProviderGen.INSTANCE;
        }

        @TruffleBoundary
        @Specialization
        boolean run(TruffleString name) {
            return findFrozen(getContext(), name).status == FROZEN_OKAY;
        }
    }

    @Builtin(name = "is_frozen_package", parameterNames = {"name"}, minNumOfPositionalArgs = 1, doc = "is_frozen_package($module, name, /)\n" +
                    "--\n" +
                    "\n" +
                    "Returns True if the module name is of a frozen package.")
    @GenerateNodeFactory
    @ArgumentClinic(name = "name", conversion = ArgumentClinic.ClinicConversion.TString)
    abstract static class IsFrozenPackage extends PythonUnaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ImpModuleBuiltinsClinicProviders.IsFrozenClinicProviderGen.INSTANCE;
        }

        @TruffleBoundary
        @Specialization
        static boolean run(TruffleString name,
                        @Bind PythonContext context) {
            FrozenResult result = findFrozen(context, name);
            if (result.status != FROZEN_EXCLUDED) {
                raiseFrozenError(result.status, name);
            }
            return result.info.isPackage;
        }
    }

    @Builtin(name = "get_frozen_object", parameterNames = {"name", "data"}, minNumOfPositionalArgs = 1, doc = "get_frozen_object($module, name, data=None, /)\n" +
                    "--\n" +
                    "\n" +
                    "Create a code object for a frozen module.")
    @GenerateNodeFactory
    @ArgumentClinic(name = "name", conversion = ArgumentClinic.ClinicConversion.TString)
    @ArgumentClinic(name = "data", conversion = ClinicConversion.ReadableBuffer, defaultValue = "PNone.NONE", useDefaultForNone = true)
    abstract static class GetFrozenObject extends PythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ImpModuleBuiltinsClinicProviders.GetFrozenObjectClinicProviderGen.INSTANCE;
        }

        @TruffleBoundary
        @Specialization
        static Object run(TruffleString name, Object dataObj,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context) {
            FrozenInfo info;
            if (dataObj != PNone.NONE) {
                byte[] bytes;
                int size;
                PythonBufferAccessLibrary bufferLib = PythonBufferAccessLibrary.getUncached();
                try {
                    bytes = bufferLib.getInternalOrCopiedByteArray(dataObj);
                    size = bufferLib.getBufferLength(dataObj);
                } finally {
                    bufferLib.release(dataObj);
                }
                if (size == 0) {
                    /* Does not contain executable code. */
                    raiseFrozenError(FROZEN_INVALID, name);
                }

                Object code = null;

                try {
                    code = MarshalModuleBuiltins.Marshal.load(context, bytes, size);
                } catch (MarshalError | NumberFormatException e) {
                    raiseFrozenError(FROZEN_INVALID, name);
                }

                if (!(code instanceof PCode)) {
                    throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.NOT_A_CODE_OBJECT, name);
                }

                return code;
            } else {
                FrozenResult result = findFrozen(context, name);
                FrozenStatus status = result.status;
                info = result.info;
                raiseFrozenError(status, name);

                RootCallTarget callTarget = createCallTarget(context, info);
                return PFactory.createCode(context.getLanguage(), callTarget);
            }
        }
    }

    @Builtin(name = "find_frozen", parameterNames = {"name", "withdata"}, minNumOfPositionalArgs = 1, doc = "find_frozen($module, name, /, *, withdata=False)\n" +
                    "--\n" +
                    "\n" +
                    "Return info about the corresponding frozen module (if there is one) or None.\n" +
                    "\n" +
                    "The returned info (a 3-tuple):\n" +
                    "\n" +
                    " * data         the raw marshalled bytes\n" +
                    " * is_package   whether or not it is a package\n" +
                    " * origname     the originally frozen module\'s name, or None if not\n" +
                    "                a stdlib module (this will usually be the same as\n" +
                    "                the module\'s current name)")
    @GenerateNodeFactory
    @ArgumentClinic(name = "name", conversion = ArgumentClinic.ClinicConversion.TString)
    @ArgumentClinic(name = "withdata", conversion = ArgumentClinic.ClinicConversion.Boolean, defaultValue = "false", useDefaultForNone = true)
    abstract static class FindFrozen extends PythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ImpModuleBuiltinsClinicProviders.FindFrozenClinicProviderGen.INSTANCE;
        }

        @TruffleBoundary
        @Specialization
        static Object run(TruffleString name, boolean withData,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context) {
            FrozenResult result = findFrozen(context, name);
            FrozenStatus status = result.status;
            FrozenInfo info = result.info;

            switch (status) {
                case FROZEN_NOT_FOUND:
                case FROZEN_DISABLED:
                case FROZEN_BAD_NAME:
                    return PNone.NONE;
                default:
                    raiseFrozenError(status, name);
            }

            PMemoryView data = null;

            if (withData) {
                byte[] bytes = MarshalModuleBuiltins.serializeCodeUnit(inliningTarget, context, info.code);
                data = PyMemoryViewFromObject.getUncached().execute(null, PFactory.createBytes(context.getLanguage(inliningTarget), bytes));
            }

            Object[] returnValues = new Object[]{
                            data == null ? PNone.NONE : data,
                            info.isPackage,
                            info.origName == null ? PNone.NONE : info.origName
            };

            return PFactory.createTuple(context.getLanguage(inliningTarget), returnValues);
        }
    }

    @Builtin(name = "init_frozen", parameterNames = {"name"}, minNumOfPositionalArgs = 1, doc = "init_frozen($module, name, /)\n" +
                    "--\n" +
                    "\n" +
                    "Initializes a frozen module.")
    @GenerateNodeFactory
    @ArgumentClinic(name = "name", conversion = ArgumentClinic.ClinicConversion.TString)
    abstract static class InitFrozen extends PythonUnaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ImpModuleBuiltinsClinicProviders.InitFrozenClinicProviderGen.INSTANCE;
        }

        @Specialization
        @TruffleBoundary
        static PythonModule run(TruffleString name,
                        @Bind PythonContext context) {
            return importFrozenModuleObject(context, name, true);
        }
    }

    /**
     * Equivalent to CPythons PyImport_FrozenModuleObject. Initialize a frozen module. Returns the
     * imported module, null, or raises a Python exception.
     */
    @TruffleBoundary
    public static PythonModule importFrozenModuleObject(Python3Core core, TruffleString name, boolean doRaise) {
        return importFrozenModuleObject(core, name, doRaise, null);
    }

    /**
     * @see #importFrozenModuleObject
     *
     *      Uses {@code globals} if given as the globals for execution.
     */
    @TruffleBoundary
    public static PythonModule importFrozenModuleObject(Python3Core core, TruffleString name, boolean doRaise, PythonModule globals) {
        FrozenResult result = findFrozen(core.getContext(), name);
        FrozenStatus status = result.status;
        FrozenInfo info = result.info;

        switch (status) {
            case FROZEN_NOT_FOUND:
            case FROZEN_DISABLED:
            case FROZEN_BAD_NAME:
                return null;
            default:
                if (doRaise) {
                    raiseFrozenError(status, name);
                } else if (status != FROZEN_OKAY) {
                    return null;
                }
        }

        RootCallTarget callTarget = createCallTarget(core.getContext(), info);
        PythonModule module = globals == null ? PFactory.createPythonModule(name) : globals;

        if (info.isPackage) {
            /* Set __path__ to the empty list */
            WriteAttributeToPythonObjectNode.getUncached().execute(module, T___PATH__, PFactory.createList(core.getLanguage()));
        }

        CallDispatchers.SimpleIndirectInvokeNode.executeUncached(callTarget, PArguments.withGlobals(module));

        Object origName = info.origName == null ? PNone.NONE : info.origName;
        WriteAttributeToPythonObjectNode.getUncached().execute(module, T___ORIGNAME__, origName);

        return module;
    }

    private static RootCallTarget createCallTarget(PythonContext context, FrozenInfo info) {
        String name = PythonLanguage.FROZEN_FILENAME_PREFIX + info.name + PythonLanguage.FROZEN_FILENAME_SUFFIX;
        Source source = Source.newBuilder("python", "", name).content(Source.CONTENT_NONE).build();
        return context.getLanguage().callTargetFromBytecode(context, source, info.code);
    }

    /*
     * CPython's version of this accepts any object and casts, but all Python-level callers use
     * argument clinic to convert the name first. The only exception is
     * PyImport_ImportFrozenModuleObject, which we don't expose as C API and handle differently_
     */
    private static FrozenResult findFrozen(PythonContext context, TruffleString name) {
        TriState override = context.getOverrideFrozenModules();
        if (override == TriState.FALSE || (override == TriState.UNDEFINED && context.getOption(PythonOptions.DisableFrozenModules))) {
            return new FrozenResult(FROZEN_DISABLED);
        }
        PythonFrozenModule module = FrozenModules.lookup(name.toJavaStringUncached());

        if (module == null) {
            return new FrozenResult(FROZEN_NOT_FOUND);
        }

        boolean isAlias = module.getOriginalName() == null || !name.equalsUncached(module.getOriginalName(), TS_ENCODING);
        FrozenInfo info = new FrozenInfo(name,
                        module.getCode(),
                        module.isPackage(),
                        module.getOriginalName(),
                        !isAlias);

        if (info.code == null) {
            return new FrozenResult(FROZEN_INVALID, info);
        }

        return new FrozenResult(FROZEN_OKAY, info);
    }

    private static void raiseFrozenError(FrozenStatus status, TruffleString moduleName) {
        if (status == FROZEN_OKAY) {
            // There was no error.
            return;
        }
        TruffleString message = switch (status) {
            case FROZEN_BAD_NAME, FROZEN_NOT_FOUND -> ErrorMessages.NO_SUCH_FROZEN_OBJECT;
            case FROZEN_DISABLED -> ErrorMessages.FROZEN_DISABLED;
            case FROZEN_EXCLUDED -> ErrorMessages.FROZEN_EXCLUDED;
            case FROZEN_INVALID -> ErrorMessages.FROZEN_INVALID;
            default -> throw CompilerDirectives.shouldNotReachHere("unknown frozen status");
        };
        throw PConstructAndRaiseNode.getUncached().raiseImportErrorWithModule(null, moduleName, PNone.NONE, message, moduleName);
    }

    @Builtin(name = "source_hash", minNumOfPositionalArgs = 2, parameterNames = {"key", "source"})
    @ArgumentClinic(name = "key", conversion = ArgumentClinic.ClinicConversion.Long)
    @ArgumentClinic(name = "source", conversion = ArgumentClinic.ClinicConversion.ReadableBuffer)
    @GenerateNodeFactory
    public abstract static class SourceHashNode extends PythonBinaryClinicBuiltinNode {
        @TruffleBoundary
        @Specialization
        static PBytes run(long magicNumber, Object sourceBuffer,
                        @Bind PythonLanguage language) {
            long sourceHash = BytesNodes.HashBufferNode.executeUncached(sourceBuffer);
            return PFactory.createBytes(language, computeHash(magicNumber, sourceHash));
        }

        @TruffleBoundary
        private static byte[] computeHash(long magicNumber, long sourceHash) {
            byte[] hash = new byte[Long.BYTES];
            long hashCode = magicNumber ^ sourceHash;
            for (int i = 0; i < hash.length; i++) {
                hash[i] = (byte) (hashCode << (8 * i));
            }
            return hash;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ImpModuleBuiltinsClinicProviders.SourceHashNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "_fix_co_filename", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class FixCoFilename extends PythonBinaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        public Object run(PCode code, PString path,
                        @Bind Node inliningTarget,
                        @Cached CastToTruffleStringNode castToStringNode) {
            code.setFilename(castToStringNode.execute(inliningTarget, path));
            return PNone.NONE;
        }

        @Specialization
        @TruffleBoundary
        public Object run(PCode code, TruffleString path) {
            code.setFilename(path);
            return PNone.NONE;
        }
    }

    @Builtin(name = "extension_suffixes")
    @GenerateNodeFactory
    public abstract static class ExtensionSuffixesNode extends PythonBuiltinNode {
        @Specialization
        Object run(
                        @Bind PythonLanguage language) {
            return PFactory.createList(language, new Object[]{PythonContext.get(this).getSoAbi(), T_EXT_SO, T_EXT_PYD});
        }
    }

    @Builtin(name = "create_dynamic", minNumOfPositionalArgs = 1, parameterNames = {"spec", "file"})
    @GenerateNodeFactory
    public abstract static class CreateDynamicNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object run(VirtualFrame frame, PythonObject moduleSpec, @SuppressWarnings("unused") Object fileName,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @Cached("createFor($node)") IndirectCallData indirectCallData) {
            PythonLanguage language = context.getLanguage(inliningTarget);
            Object state = IndirectCallContext.enter(frame, language, context, indirectCallData);
            try {
                return doCreate(moduleSpec, inliningTarget, context);
            } finally {
                IndirectCallContext.exit(frame, language, context, state);
            }
        }

        @TruffleBoundary
        private static Object doCreate(PythonObject moduleSpec, Node inliningTarget, PythonContext context) {
            try {
                TruffleString name = CastToTruffleStringNode.executeUncached(PyObjectGetAttr.executeUncached(moduleSpec, T_NAME));
                TruffleString path = CastToTruffleStringNode.executeUncached(PyObjectGetAttr.executeUncached(moduleSpec, T_ORIGIN));
                ModuleSpec spec = new ModuleSpec(name, path, moduleSpec);
                CApiContext cApiContext = context.getCApiContext();
                if (cApiContext != null) {
                    // TODO check m_size
                    // TODO populate m_copy?
                    PythonModule existingModule = cApiContext.findExtension(spec.path, spec.name);
                    if (existingModule != null) {
                        return existingModule;
                    }
                }
                TruffleString oldPackageContext = context.getPyPackageContext();
                context.setPyPackageContext(name);
                try {
                    return CApiContext.loadCExtModule(inliningTarget, context, spec);
                } finally {
                    context.setPyPackageContext(oldPackageContext);
                }
            } catch (CannotCastException e) {
                throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.BAD_ARG_TYPE_FOR_BUILTIN_OP);
            } catch (ApiInitException ie) {
                throw ie.reraise();
            } catch (ImportException ie) {
                throw ie.reraise();
            } catch (IOException e) {
                throw PConstructAndRaiseNode.getUncached().raiseOSError(null, e, TruffleString.EqualNode.getUncached());
            }
        }
    }

    @Builtin(name = "_override_frozen_modules_for_tests", minNumOfPositionalArgs = 1, parameterNames = {"override"})
    @ArgumentClinic(name = "override", conversion = ClinicConversion.Int)
    @GenerateNodeFactory
    abstract static class OverrideFrozenModulesForTests extends PythonUnaryClinicBuiltinNode {
        @Specialization
        @TruffleBoundary
        Object set(int override) {
            TriState value = TriState.UNDEFINED;
            if (override > 0) {
                value = TriState.TRUE;
            } else if (override < 0) {
                value = TriState.FALSE;
            }
            PythonContext.get(null).setOverrideFrozenModules(value);
            return PNone.NONE;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ImpModuleBuiltinsClinicProviders.OverrideFrozenModulesForTestsClinicProviderGen.INSTANCE;
        }
    }
}
