/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___ORIGNAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___PATH__;
import static com.oracle.graal.python.nodes.StringLiterals.T_EXT_PYD;
import static com.oracle.graal.python.nodes.StringLiterals.T_EXT_SO;
import static com.oracle.graal.python.nodes.StringLiterals.T_NAME;
import static com.oracle.graal.python.nodes.StringLiterals.T_SITE;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ImportError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.NotImplementedError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import org.graalvm.nativeimage.ImageInfo;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ArgumentClinic.ClinicConversion;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinConstructors.MemoryViewNode;
import com.oracle.graal.python.builtins.modules.MarshalModuleBuiltins.Marshal.MarshalError;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ExecModuleNode;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodesFactory.DefaultCheckFunctionResultNodeGen;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.CheckFunctionResultNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtContext;
import com.oracle.graal.python.builtins.objects.cext.common.CExtContext.ModuleSpec;
import com.oracle.graal.python.builtins.objects.cext.common.LoadCExtException.ApiInitException;
import com.oracle.graal.python.builtins.objects.cext.common.LoadCExtException.ImportException;
import com.oracle.graal.python.builtins.objects.cext.hpy.HPyExternalFunctionNodes.HPyCheckFunctionResultNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.HPyExternalFunctionNodesFactory.HPyCheckHandleResultNodeGen;
import com.oracle.graal.python.builtins.objects.code.CodeNodes;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.module.FrozenModules;
import com.oracle.graal.python.builtins.objects.module.PythonFrozenModule;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.compiler.Compiler;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectStrAsTruffleStringNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromDynamicObjectNode;
import com.oracle.graal.python.nodes.attributes.SetAttributeNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToDynamicObjectNode;
import com.oracle.graal.python.nodes.call.GenericInvokeNode;
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
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.memory.ByteArraySupport;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

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
        final byte[] data;
        final int size;
        final boolean isPackage;
        final TruffleString origName;
        @SuppressWarnings("unused") final boolean isAlias;

        FrozenInfo(byte[] data, int size) {
            this(null, data, size, false, null, false);
        }

        FrozenInfo(TruffleString name, byte[] data, int size, boolean isPackage, TruffleString origName, boolean isAlias) {
            this.name = name;
            this.data = data;
            this.size = size;
            this.isPackage = isPackage;
            this.origName = origName;
            this.isAlias = isAlias;
        }
    }

    enum FrozenStatus {
        FROZEN_OKAY,
        FROZEN_BAD_NAME,    // The given module name wasn't valid.
        FROZEN_NOT_FOUND,   // It wasn't in PyImport_FrozenModules.
        FROZEN_DISABLED,    // -X frozen_modules=off (and not essential)
        FROZEN_EXCLUDED, /*
                          * The PyImport_FrozenModules entry has NULL "code" (module is present but
                          * marked as unimportable, stops search).
                          */
        FROZEN_INVALID, /*
                         * The PyImport_FrozenModules entry is bogus (eg. does not contain
                         * executable code).
                         */
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
                getContext().getImportLock().lock();
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
        public PBytes runCachedSingleContext(
                        @Cached(value = "getMagicNumberPBytes()", weak = true) PBytes magicBytes) {
            return magicBytes;
        }

        @Specialization(replaces = "runCachedSingleContext")
        public PBytes run() {
            return factory().createBytes(MAGIC_NUMBER_BYTES);
        }

        protected PBytes getMagicNumberPBytes() {
            return factory().createBytes(MAGIC_NUMBER_BYTES);
        }
    }

    @Builtin(name = "__create_dynamic__", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class CreateDynamic extends PythonBinaryBuiltinNode {

        @Child private CheckFunctionResultNode checkResultNode;
        @Child private HPyCheckFunctionResultNode checkHPyResultNode;

        public abstract Object execute(VirtualFrame frame, PythonObject moduleSpec, Object filename);

        @Specialization
        Object run(VirtualFrame frame, PythonObject moduleSpec, @SuppressWarnings("unused") Object filename,
                        @Bind("this") Node inliningTarget,
                        @Cached ReadAttributeFromDynamicObjectNode readNameNode,
                        @Cached ReadAttributeFromDynamicObjectNode readOriginNode,
                        @Cached CastToTruffleStringNode castToTruffleStringNode,
                        @Cached TruffleString.EqualNode eqNode) {
            TruffleString name = castToTruffleStringNode.execute(inliningTarget, readNameNode.execute(moduleSpec, T_NAME));
            TruffleString path = castToTruffleStringNode.execute(inliningTarget, readOriginNode.execute(moduleSpec, T_ORIGIN));

            PythonContext context = getContext();
            PythonLanguage language = getLanguage();
            Object state = IndirectCallContext.enter(frame, language, context, this);
            try {
                return run(context, new ModuleSpec(name, path, moduleSpec));
            } catch (ApiInitException ie) {
                throw ie.reraise(getConstructAndRaiseNode(), frame);
            } catch (ImportException ie) {
                throw ie.reraise(getConstructAndRaiseNode(), frame);
            } catch (IOException e) {
                throw getConstructAndRaiseNode().raiseOSError(frame, e, eqNode);
            } finally {
                IndirectCallContext.exit(frame, language, context, state);
            }
        }

        @TruffleBoundary
        private Object run(PythonContext context, ModuleSpec spec) throws IOException, ApiInitException, ImportException {
            Object existingModule = findExtensionObject(spec);
            if (existingModule != null) {
                return existingModule;
            }
            return CExtContext.loadCExtModule(this, context, spec, getCheckResultNode(), getCheckHPyResultNode());
        }

        @SuppressWarnings({"static-method", "unused"})
        private Object findExtensionObject(ModuleSpec spec) {
            // TODO: to avoid initializing an extension module twice, keep an internal dict
            // and possibly return from there, i.e., _PyImport_FindExtensionObject(name, path)
            return null;
        }

        private CheckFunctionResultNode getCheckResultNode() {
            if (checkResultNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                checkResultNode = insert(DefaultCheckFunctionResultNodeGen.create());
            }
            return checkResultNode;
        }

        private HPyCheckFunctionResultNode getCheckHPyResultNode() {
            if (checkHPyResultNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                checkHPyResultNode = insert(HPyCheckHandleResultNodeGen.create());
            }
            return checkHPyResultNode;
        }
    }

    @Builtin(name = "exec_dynamic", minNumOfPositionalArgs = 1, doc = "exec_dynamic($module, mod, /)\n--\n\nInitialize an extension module.")
    @GenerateNodeFactory
    public abstract static class ExecDynamicNode extends PythonBuiltinNode {
        @Specialization
        int doPythonModule(VirtualFrame frame, PythonModule extensionModule,
                        @CachedLibrary(limit = "1") InteropLibrary lib,
                        @Cached ExecModuleNode execModuleNode) {
            Object nativeModuleDef = extensionModule.getNativeModuleDef();
            if (nativeModuleDef == null) {
                return 0;
            }

            /*
             * Check if module is already initialized. CPython does that by testing if 'md_state !=
             * NULL'. So, we do the same.
             */
            Object mdState = extensionModule.getNativeModuleState();
            if (mdState != null && !lib.isNull(mdState)) {
                return 0;
            }

            PythonContext context = getContext();
            if (!context.hasCApiContext()) {
                throw raise(PythonBuiltinClassType.SystemError, ErrorMessages.CAPI_NOT_YET_INITIALIZED);
            }

            /*
             * ExecModuleNode will run the module definition's exec function which may run arbitrary
             * C code. So we need to setup an indirect call.
             */
            PythonLanguage language = getLanguage();
            Object state = IndirectCallContext.enter(frame, language, context, this);
            try {
                return execModuleNode.execute(context.getCApiContext(), extensionModule, nativeModuleDef);
            } finally {
                IndirectCallContext.exit(frame, language, context, state);
            }
        }

        @Fallback
        static int doOther(@SuppressWarnings("unused") Object extensionModule) {
            return 0;
        }
    }

    @Builtin(name = "is_builtin", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IsBuiltin extends PythonBuiltinNode {

        @Specialization
        @TruffleBoundary
        public int run(TruffleString name) {
            if (getCore().lookupBuiltinModule(name) != null) {
                // TODO: missing "1" case when the builtin module can be re-initialized
                return -1;
            } else {
                return 0;
            }
        }

        @Specialization
        @TruffleBoundary
        public int run(PString name,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToTruffleStringNode toString) {
            try {
                return run(toString.execute(inliningTarget, name));
            } catch (CannotCastException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        @Fallback
        public int run(@SuppressWarnings("unused") Object noName) {
            return 0;
        }
    }

    @Builtin(name = "create_builtin", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class CreateBuiltin extends PythonBuiltinNode {

        public static final TruffleString T_LOADER = tsLiteral("loader");

        @Specialization
        public Object run(VirtualFrame frame, PythonObject moduleSpec,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToTruffleStringNode toStringNode,
                        @Cached("create(T___LOADER__)") SetAttributeNode setAttributeNode,
                        @Cached PyObjectLookupAttr lookup) {
            Object name = lookup.execute(frame, inliningTarget, moduleSpec, T_NAME);
            PythonModule builtinModule = getCore().lookupBuiltinModule(toStringNode.execute(inliningTarget, name));
            if (builtinModule != null) {
                // TODO: GR-26411 builtin modules cannot be re-initialized (see is_builtin)
                // We are setting the loader to the spec loader (since this is the loader that is
                // set during bootstrap); this, however, should be handled be the builtin module
                // reinitialization (if reinit is possible)
                Object loader = lookup.execute(frame, inliningTarget, moduleSpec, T_LOADER);
                if (loader != PNone.NO_VALUE) {
                    setAttributeNode.execute(frame, builtinModule, loader);
                }
                return builtinModule;
            }
            throw raise(NotImplementedError, toTruffleStringUncached("_imp.create_builtin"));
        }
    }

    @Builtin(name = "exec_builtin", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ExecBuiltin extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        public Object exec(PythonModule pythonModule) {
            final Python3Core core = getCore();
            if (!ImageInfo.inImageBuildtimeCode()) {
                final PythonBuiltins builtins = pythonModule.getBuiltins();
                assert builtins != null; // this is a builtin, therefore its builtins must have been
                                         // set at this point
                if (!builtins.isInitialized()) {
                    doPostInit(core, builtins);
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

        @Specialization
        boolean run(TruffleString name,
                        @Cached TruffleString.EqualNode equalNode) {
            // if PYTHONPATH is set, it is prepended to the sys.path on startup and thus might
            // override the site module from the stdlib
            if (!getContext().getOption(PythonOptions.PythonPath).isEmpty() && equalNode.execute(name, T_SITE, TS_ENCODING)) {
                return false;
            } else {
                return findFrozen(getContext(), name, equalNode).status == FROZEN_OKAY;
            }
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

        @Specialization
        boolean run(TruffleString name,
                        @Cached PRaiseNode raiseNode,
                        @Cached TruffleString.EqualNode equalNode) {
            FrozenResult result = findFrozen(getContext(), name, equalNode);
            if (result.status != FROZEN_EXCLUDED) {
                raiseFrozenError(result.status, name, raiseNode);
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

        @Specialization
        Object run(VirtualFrame frame, TruffleString name, Object dataObj,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib,
                        @Cached TruffleString.EqualNode equalNode,
                        @Cached PRaiseNode raiseNode,
                        @Cached InlinedConditionProfile isCodeObjectProfile) {
            FrozenInfo info;
            if (dataObj != PNone.NONE) {
                try {
                    info = new FrozenInfo(bufferLib.getInternalOrCopiedByteArray(dataObj), bufferLib.getBufferLength(dataObj));
                } finally {
                    bufferLib.release(dataObj, frame, this);
                }
                if (info.size == 0) {
                    /* Does not contain executable code. */
                    raiseFrozenError(FROZEN_INVALID, name, raiseNode);
                }
            } else {
                FrozenResult result = findFrozen(getContext(), name, equalNode);
                FrozenStatus status = result.status;
                info = result.info;
                raiseFrozenError(status, name, raiseNode);
            }

            Object code = null;

            try {
                code = MarshalModuleBuiltins.Marshal.load(info.data, info.size);
            } catch (MarshalError | NumberFormatException e) {
                raiseFrozenError(FROZEN_INVALID, name, raiseNode);
            }

            if (!isCodeObjectProfile.profile(inliningTarget, code instanceof PCode)) {
                throw raise(TypeError, ErrorMessages.NOT_A_CODE_OBJECT, name);
            }

            return code;
        }
    }

    // Will be public part of CPython from 3.11 (already merged into main)
    @Builtin(name = "find_frozen", parameterNames = {"name", "withData"}, minNumOfPositionalArgs = 1, isPublic = false, doc = "find_frozen($module, name, /, *, withdata=False)\n" +
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
    @ArgumentClinic(name = "withData", conversion = ArgumentClinic.ClinicConversion.Boolean, defaultValue = "false", useDefaultForNone = true)
    abstract static class FindFrozen extends PythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ImpModuleBuiltinsClinicProviders.FindFrozenClinicProviderGen.INSTANCE;
        }

        @Specialization
        Object run(VirtualFrame frame, TruffleString name, boolean withData,
                        @Cached MemoryViewNode memoryViewNode,
                        @Cached TruffleString.EqualNode equalNode,
                        @Cached PRaiseNode raiseNode) {
            FrozenResult result = findFrozen(getContext(), name, equalNode);
            FrozenStatus status = result.status;
            FrozenInfo info = result.info;

            switch (status) {
                case FROZEN_NOT_FOUND:
                case FROZEN_DISABLED:
                case FROZEN_BAD_NAME:
                    return PNone.NONE;
                default:
                    raiseFrozenError(status, name, raiseNode);
            }

            PMemoryView data = null;

            if (withData) {
                data = memoryViewNode.execute(frame, factory().createBytes(info.data));
            }

            Object[] returnValues = new Object[]{
                            data == null ? PNone.NONE : data,
                            info.isPackage,
                            info.origName == null ? PNone.NONE : info.origName
            };

            return factory().createTuple(returnValues);
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
        PythonModule run(TruffleString name) {
            return importFrozenModuleObject(getCore(), name, true);
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
        FrozenResult result = findFrozen(core.getContext(), name, TruffleString.EqualNode.getUncached());
        FrozenStatus status = result.status;
        FrozenInfo info = result.info;

        switch (status) {
            case FROZEN_NOT_FOUND:
            case FROZEN_DISABLED:
            case FROZEN_BAD_NAME:
                return null;
            default:
                if (doRaise) {
                    raiseFrozenError(status, name, PRaiseNode.getUncached());
                } else if (status != FROZEN_OKAY) {
                    return null;
                }
        }

        PCode code = (PCode) MarshalModuleBuiltins.Marshal.load(info.data, info.size);

        PythonModule module = globals == null ? core.factory().createPythonModule(name) : globals;

        if (info.isPackage) {
            /* Set __path__ to the empty list */
            WriteAttributeToDynamicObjectNode.getUncached().execute(module, T___PATH__, core.factory().createList());
        }

        RootCallTarget callTarget = CodeNodes.GetCodeCallTargetNode.executeUncached(code);
        GenericInvokeNode.getUncached().execute(callTarget, PArguments.withGlobals(module));

        Object origName = info.origName == null ? PNone.NONE : info.origName;
        WriteAttributeToDynamicObjectNode.getUncached().execute(module, T___ORIGNAME__, origName);

        return module;
    }

    /*
     * CPython's version of this accepts any object and casts, but all Python-level callers use
     * argument clinic to convert the name first. The only exception is
     * PyImport_ImportFrozenModuleObject, which we don't expose as C API and handle differently_
     */
    private static FrozenResult findFrozen(PythonContext context, TruffleString name, TruffleString.EqualNode equalNode) {
        if (context.getOption(PythonOptions.DisableFrozenModules)) {
            return new FrozenResult(FROZEN_DISABLED);
        }
        PythonFrozenModule module = FrozenModules.lookup(name.toJavaStringUncached());

        if (module == null) {
            return new FrozenResult(FROZEN_NOT_FOUND);
        }

        FrozenInfo info = new FrozenInfo(name,
                        module.getCode(),
                        module.getSize(),
                        module.isPackage(),
                        module.getName(),
                        !equalNode.execute(name, module.getName(), TS_ENCODING));

        if (module.getCode() == null) {
            /* It is frozen but marked as un-importable. */
            return new FrozenResult(FROZEN_EXCLUDED, info);
        }

        if (module.getCode()[0] == '\0' || module.getSize() == 0) {
            /* Does not contain executable code. */
            return new FrozenResult(FROZEN_INVALID, info);
        }

        return new FrozenResult(FROZEN_OKAY, info);
    }

    private static void raiseFrozenError(FrozenStatus status, TruffleString moduleName, PRaiseNode raiseNode) {
        switch (status) {
            case FROZEN_BAD_NAME:
                throw raiseNode.raise(ImportError, ErrorMessages.NO_SUCH_FROZEN_OBJECT, moduleName);
            case FROZEN_NOT_FOUND:
                throw raiseNode.raise(ImportError, ErrorMessages.NO_SUCH_FROZEN_OBJECT, moduleName);
            case FROZEN_DISABLED:
                throw raiseNode.raise(ImportError, ErrorMessages.FROZEN_DISABLED, moduleName);
            case FROZEN_EXCLUDED:
                throw raiseNode.raise(ImportError, ErrorMessages.FROZEN_EXCLUDED, moduleName);
            case FROZEN_INVALID:
                throw raiseNode.raise(ImportError, ErrorMessages.FROZEN_INVALID, moduleName);
            case FROZEN_OKAY:
                // There was no error.
                break;
            default:
                throw CompilerDirectives.shouldNotReachHere("unknown frozen status");
        }
    }

    @Builtin(name = "source_hash", minNumOfPositionalArgs = 2, parameterNames = {"key", "source"})
    @ArgumentClinic(name = "key", conversion = ArgumentClinic.ClinicConversion.Long)
    @ArgumentClinic(name = "source", conversion = ArgumentClinic.ClinicConversion.ReadableBuffer)
    @GenerateNodeFactory
    public abstract static class SourceHashNode extends PythonBinaryClinicBuiltinNode {
        @Specialization
        PBytes run(long magicNumber, Object sourceBuffer,
                        @Bind("this") Node inliningTarget,
                        @Cached BytesNodes.HashBufferNode hashBufferNode) {
            long sourceHash = hashBufferNode.execute(inliningTarget, sourceBuffer);
            return factory().createBytes(computeHash(magicNumber, sourceHash));
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
                        @Bind("this") Node inliningTarget,
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
        Object run() {
            return factory().createList(new Object[]{PythonContext.get(this).getSoAbi(), T_EXT_SO, T_EXT_PYD});
        }
    }

    @Builtin(name = "create_dynamic", minNumOfPositionalArgs = 1, parameterNames = {"moduleSpec", "fileName"})
    @GenerateNodeFactory
    public abstract static class CreateDynamicNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object run(VirtualFrame frame, PythonObject moduleSpec, Object fileNameIn,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedConditionProfile fileNameIsNoValueProfile,
                        @Cached PyObjectLookupAttr lookupAttr,
                        @Cached PyObjectStrAsTruffleStringNode asStringNode,
                        @Cached CreateDynamic createDynamicNode) {
            Object fileName = fileNameIsNoValueProfile.profile(inliningTarget, PGuards.isNoValue(fileNameIn)) ? PNone.NONE : fileNameIn;
            PythonContext ctx = getContext();
            TruffleString oldPackageContext = ctx.getPyPackageContext();
            ctx.setPyPackageContext(asStringNode.execute(frame, inliningTarget, lookupAttr.execute(frame, inliningTarget, moduleSpec, T_NAME)));
            try {
                return createDynamicNode.execute(frame, moduleSpec, fileName);
            } finally {
                ctx.setPyPackageContext(oldPackageContext);
            }
        }
    }

}
