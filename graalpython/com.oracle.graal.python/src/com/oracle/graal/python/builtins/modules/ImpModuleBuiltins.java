/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.runtime.exception.PythonErrorType.NotImplementedError;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodesFactory;
import com.oracle.graal.python.frozen.PythonFrozenModule;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.truffle.api.dsl.Bind;
import org.graalvm.nativeimage.ImageInfo;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ExecModuleNode;
import com.oracle.graal.python.builtins.objects.cext.capi.DynamicObjectNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodesFactory.DefaultCheckFunctionResultNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeMember;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.CheckFunctionResultNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtContext;
import com.oracle.graal.python.builtins.objects.cext.common.CExtContext.ModuleSpec;
import com.oracle.graal.python.builtins.objects.cext.common.LoadCExtException.ApiInitException;
import com.oracle.graal.python.builtins.objects.cext.common.LoadCExtException.ImportException;
import com.oracle.graal.python.builtins.objects.cext.hpy.HPyExternalFunctionNodes.HPyCheckFunctionResultNode;
import com.oracle.graal.python.builtins.objects.cext.hpy.HPyExternalFunctionNodesFactory.HPyCheckHandleResultNodeGen;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.ints.IntBuiltins;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectStrAsJavaStringNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromDynamicObjectNode;
import com.oracle.graal.python.nodes.attributes.SetAttributeNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.parser.sst.SerializationUtils;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;

@CoreFunctions(defineModule = "_imp", isEager = true)
public class ImpModuleBuiltins extends PythonBuiltins {

    static final String HPY_SUFFIX = ".hpy.so";

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ImpModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void postInitialize(Python3Core core) {
        super.postInitialize(core);
        PythonContext context = core.getContext();
        PythonModule mod = core.lookupBuiltinModule("_imp");
        mod.setAttribute("check_hash_based_pycs", context.getOption(PythonOptions.CheckHashPycsMode));
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
        static final int MAGIC_NUMBER = 21000 + SerializationUtils.VERSION * 10;

        @Child private IntBuiltins.ToBytesNode toBytesNode = IntBuiltins.ToBytesNode.create();
        @Child private PythonBufferAccessLibrary bufferLib = PythonBufferAccessLibrary.getFactory().createDispatched(1);

        @Specialization(guards = "isSingleContext()")
        public PBytes runCachedSingleContext(@SuppressWarnings("unused") VirtualFrame frame,
                        @Cached(value = "getMagicNumberPBytes(frame)", weak = true) PBytes magicBytes) {
            return magicBytes;
        }

        @Specialization(replaces = "runCachedSingleContext")
        public PBytes run(@SuppressWarnings("unused") VirtualFrame frame,
                        @Cached(value = "getMagicNumberBytes(frame)", dimensions = 1) byte[] magicBytes) {
            return factory().createBytes(magicBytes);
        }

        protected PBytes getMagicNumberPBytes(VirtualFrame frame) {
            return factory().createBytes(getMagicNumberBytes(frame));
        }

        protected byte[] getMagicNumberBytes(VirtualFrame frame) {
            PBytes magic = toBytesNode.execute(frame, MAGIC_NUMBER, 2, "little", false);
            byte[] magicBytes = bufferLib.getInternalOrCopiedByteArray(magic);
            return new byte[]{magicBytes[0], magicBytes[1], '\r', '\n'};
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
                        @Cached ReadAttributeFromDynamicObjectNode readNameNode,
                        @Cached ReadAttributeFromDynamicObjectNode readOriginNode,
                        @Cached CastToJavaStringNode castToJavaStringNode) {
            String name = castToJavaStringNode.execute(readNameNode.execute(moduleSpec, "name"));
            String path = castToJavaStringNode.execute(readOriginNode.execute(moduleSpec, "origin"));

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
                throw getConstructAndRaiseNode().raiseOSError(frame, e);
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
                        @CachedLibrary(limit = "1") HashingStorageLibrary lib,
                        @Cached ExecModuleNode execModuleNode) {
            Object nativeModuleDef = extensionModule.getNativeModuleDef();
            if (nativeModuleDef == null) {
                return 0;
            }

            /*
             * Check if module is already initialized. CPython does that by testing if 'md_state !=
             * NULL'. So, we do the same. Currently, we store this in the generic storage of the
             * native wrapper.
             */
            DynamicObjectNativeWrapper nativeWrapper = extensionModule.getNativeWrapper();
            if (nativeWrapper != null && nativeWrapper.getNativeMemberStore() != null) {
                Object item = lib.getItem(nativeWrapper.getNativeMemberStore(), NativeMember.MD_STATE.getMemberName());
                if (item != PNone.NO_VALUE) {
                    return 0;
                }
            }

            PythonContext context = getContext();
            if (!context.hasCApiContext()) {
                throw raise(PythonBuiltinClassType.SystemError, "C API not yet initialized");
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
        public int run(String name) {
            if (getCore().lookupBuiltinModule(name) != null) {
                // TODO: missing "1" case when the builtin module can be re-initialized
                return -1;
            } else {
                return 0;
            }
        }

        @Specialization
        public int run(@SuppressWarnings("unused") Object noName) {
            return 0;
        }
    }

    @Builtin(name = "create_builtin", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class CreateBuiltin extends PythonBuiltinNode {
        @Specialization
        public Object run(VirtualFrame frame, PythonObject moduleSpec,
                        @Cached CastToJavaStringNode toJavaStringNode,
                        @Cached("create(__LOADER__)") SetAttributeNode setAttributeNode,
                        @Cached PyObjectLookupAttr lookup) {
            Object name = lookup.execute(frame, moduleSpec, "name");
            PythonModule builtinModule = getBuiltinModule(toJavaStringNode.execute(name));
            if (builtinModule != null) {
                // TODO: GR-26411 builtin modules cannot be re-initialized (see is_builtin)
                // We are setting the loader to the spec loader (since this is the loader that is
                // set during bootstrap); this, however, should be handled be the builtin module
                // reinitialization (if reinit is possible)
                Object loader = lookup.execute(frame, moduleSpec, "loader");
                if (loader != PNone.NO_VALUE) {
                    setAttributeNode.executeVoid(frame, builtinModule, loader);
                }
                return builtinModule;
            }
            throw raise(NotImplementedError, "_imp.create_builtin");
        }

        @TruffleBoundary
        private PythonModule getBuiltinModule(String name) {
            return getCore().lookupBuiltinModule(name);
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

    @Builtin(name = "is_frozen", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IsFrozen extends PythonBuiltinNode {

        @Specialization
        public boolean run(String name) {
            return getCore().lookupFrozenModule(name) != null;
        }
    }

    @Builtin(name = "get_frozen_object", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public static abstract class GetFrozenObject extends PythonBuiltinNode {
        @Specialization
        public Object run(VirtualFrame frame, String name) {
            //TODO: dataobj based path
            PythonFrozenModule frozenModule = lookupFrozenModule(name);
            // TODO: if (frozenModule == null) {// set frozen error}
            if (frozenModule.getName() == null) {
                frozenModule.setName(name);
            }
//            if (frozenModule.getSize() == 0) {
//                // TODO: set frozen error
//            }

            // byte[] byteCode = SequenceStorageNodesFactory.ToByteArrayNodeGen.getUncached().execute(frozenModule.getCode());
            Object code = MarshalModuleBuiltins.Marshal.load(frozenModule.getCode(), frozenModule.getSize());
            // Todo: error if if (code == null)

            // TODO: Code object check

            return code;
        }

        @TruffleBoundary
        private PythonFrozenModule lookupFrozenModule(String name) {
            return getCore().lookupFrozenModule(name);
        }
    }

    @Builtin(name = "source_hash", minNumOfPositionalArgs = 2, parameterNames = {"key", "source"})
    @ArgumentClinic(name = "key", conversion = ArgumentClinic.ClinicConversion.Long)
    @ArgumentClinic(name = "source", conversion = ArgumentClinic.ClinicConversion.ReadableBuffer)
    @GenerateNodeFactory
    public abstract static class SourceHashNode extends PythonBinaryClinicBuiltinNode {
        @Specialization
        PBytes run(long magicNumber, Object sourceBuffer,
                        @Cached BytesNodes.HashBufferNode hashBufferNode) {
            long sourceHash = hashBufferNode.execute(sourceBuffer);
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
                        @Cached CastToJavaStringNode castToJavaStringNode) {
            code.setFilename(castToJavaStringNode.execute(path));
            return PNone.NONE;
        }

        @Specialization
        @TruffleBoundary
        public Object run(PCode code, String path) {
            code.setFilename(path);
            return PNone.NONE;
        }
    }

    @Builtin(name = "extension_suffixes")
    @GenerateNodeFactory
    public abstract static class ExtensionSuffixesNode extends PythonBuiltinNode {
        @Specialization
        Object run() {
            return factory().createList(new Object[]{PythonContext.get(this).getSoAbi(), HPY_SUFFIX, ".so", ".dylib", ".su"});
        }
    }

    @Builtin(name = "create_dynamic", minNumOfPositionalArgs = 1, parameterNames = {"moduleSpec", "fileName"})
    @GenerateNodeFactory
    public abstract static class CreateDynamicNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(fileName)")
        Object runNoFileName(VirtualFrame frame, PythonObject moduleSpec, @SuppressWarnings("unused") PNone fileName,
                        @Cached PyObjectStrAsJavaStringNode asStrignNode,
                        @Cached CreateDynamic createDynamicNode) {
            return run(frame, moduleSpec, PNone.NONE, asStrignNode, createDynamicNode);
        }

        @Specialization(guards = "!isNoValue(fileName)")
        Object run(VirtualFrame frame, PythonObject moduleSpec, Object fileName,
                        @Cached PyObjectStrAsJavaStringNode asStrignNode,
                        @Cached CreateDynamic createDynamicNode) {
            PythonContext ctx = getContext();
            String oldPackageContext = ctx.getPyPackageContext();
            ctx.setPyPackageContext(asStrignNode.execute(frame, PyObjectLookupAttr.getUncached().execute(frame, moduleSpec, "name")));
            try {
                return createDynamicNode.execute(frame, moduleSpec, fileName);
            } finally {
                ctx.setPyPackageContext(oldPackageContext);
            }
        }
    }

}
