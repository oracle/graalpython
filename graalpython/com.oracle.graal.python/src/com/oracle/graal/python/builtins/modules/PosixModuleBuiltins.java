/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates.
 * Copyright (c) 2014, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.builtins.modules;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__FSPATH__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.NotImplementedError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.ProcessBuilder.Redirect;
import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ArgumentClinic.ClinicConversion;
import com.oracle.graal.python.annotations.ArgumentClinic.PrimitiveType;
import com.oracle.graal.python.annotations.ClinicConverterFactory;
import com.oracle.graal.python.annotations.ClinicConverterFactory.ArgumentName;
import com.oracle.graal.python.annotations.ClinicConverterFactory.BuiltinName;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes.LenNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GetItemDynamicNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GetItemNode;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PArguments.ThreadState;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.posix.PNfiScandirIterator;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.expression.BinaryArithmetic;
import com.oracle.graal.python.nodes.expression.IsExpressionNode.IsNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentCastNode.ArgumentCastNodeWithRaise;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CastToJavaLongLossyNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.PosixResources;
import com.oracle.graal.python.runtime.PosixSupportLibrary;
import com.oracle.graal.python.runtime.PosixSupportLibrary.Buffer;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixException;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonExitException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(defineModule = "posix")
public class PosixModuleBuiltins extends PythonBuiltins {
    private static final int TMPFILE = 4259840;
    private static final int TEMPORARY = 4259840;
    private static final int SYNC = 1052672;
    private static final int RSYNC = 1052672;
    private static final int CLOEXEC = PosixSupportLibrary.O_CLOEXEC;
    private static final int DIRECT = 16384;
    private static final int DSYNC = 4096;
    private static final int NDELAY = 2048;
    private static final int NONBLOCK = 2048;
    private static final int APPEND = 1024;
    private static final int TRUNC = 512;
    private static final int EXCL = 128;
    private static final int CREAT = 64;
    private static final int RDWR = 2;
    private static final int WRONLY = 1;
    private static final int RDONLY = 0;

    // TODO map Python's SEEK_SET, SEEK_CUR, SEEK_END values to the underlying OS values if they are different
    private static final int SEEK_DATA = 3;
    private static final int SEEK_HOLE = 4;

    private static final int WNOHANG = 1;
    private static final int WUNTRACED = 3;

    private static final int F_OK = 0;
    private static final int X_OK = 1;
    private static final int W_OK = 2;
    private static final int R_OK = 4;

    private static boolean terminalIsInteractive(PythonContext context) {
        return context.getOption(PythonOptions.TerminalIsInteractive);
    }

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PosixModuleBuiltinsFactory.getFactories();
    }

    public abstract static class PythonFileNode extends PythonBuiltinNode {
        protected PosixResources getResources() {
            return getContext().getResources();
        }
    }

    public PosixModuleBuiltins() {
        builtinConstants.put("O_RDONLY", RDONLY);
        builtinConstants.put("O_WRONLY", WRONLY);
        builtinConstants.put("O_RDWR", RDWR);
        builtinConstants.put("O_CREAT", CREAT);
        builtinConstants.put("O_EXCL", EXCL);
        builtinConstants.put("O_TRUNC", TRUNC);
        builtinConstants.put("O_APPEND", APPEND);
        builtinConstants.put("O_NONBLOCK", NONBLOCK);
        builtinConstants.put("O_NDELAY", NDELAY);
        builtinConstants.put("O_DSYNC", DSYNC);
        builtinConstants.put("O_DIRECT", DIRECT);
        builtinConstants.put("O_CLOEXEC", CLOEXEC);
        builtinConstants.put("O_RSYNC", RSYNC);
        builtinConstants.put("O_SYNC", SYNC);
        builtinConstants.put("O_TEMPORARY", TEMPORARY);
        builtinConstants.put("O_TMPFILE", TMPFILE);

        builtinConstants.put("SEEK_DATA", SEEK_DATA);
        builtinConstants.put("SEEK_HOLE", SEEK_HOLE);

        builtinConstants.put("WNOHANG", WNOHANG);
        builtinConstants.put("WUNTRACED", WUNTRACED);

        builtinConstants.put("F_OK", F_OK);
        builtinConstants.put("X_OK", X_OK);
        builtinConstants.put("W_OK", W_OK);
        builtinConstants.put("R_OK", R_OK);
    }

    @Override
    public void initialize(PythonCore core) {
        super.initialize(core);
        builtinConstants.put("_have_functions", core.factory().createList());
        builtinConstants.put("environ", core.factory().createDict());
    }

    @Override
    public void postInitialize(PythonCore core) {
        super.postInitialize(core);

        // fill the environ dictionary with the current environment
        Map<String, String> getenv = System.getenv();
        PDict environ = core.factory().createDict();
        for (Entry<String, String> entry : getenv.entrySet()) {
            String value;
            if ("__PYVENV_LAUNCHER__".equals(entry.getKey())) {
                // On Mac, the CPython launcher uses this env variable to specify the real Python
                // executable. It will be honored by packages like "site". So, if it is set, we
                // overwrite it with our executable to ensure that subprocesses will use us.
                value = core.getContext().getOption(PythonOptions.Executable);
            } else {
                value = entry.getValue();
            }
            environ.setItem(core.factory().createBytes(entry.getKey().getBytes()), core.factory().createBytes(value.getBytes()));
        }
        PythonModule posix = core.lookupBuiltinModule("posix");
        Object environAttr = posix.getAttribute("environ");
        ((PDict) environAttr).setDictStorage(environ.getDictStorage());
    }

    @Builtin(name = "execv", minNumOfPositionalArgs = 3, declaresExplicitSelf = true)
    @GenerateNodeFactory
    public abstract static class ExecvNode extends PythonBuiltinNode {
        @Child private BytesNodes.ToBytesNode toBytes = BytesNodes.ToBytesNode.create();

        @Specialization
        Object execute(VirtualFrame frame, PythonModule thisModule, String path, PList args) {
            return doExecute(frame, thisModule, path, args);
        }

        @Specialization
        Object execute(VirtualFrame frame, PythonModule thisModule, String path, PTuple args) {
            // in case of execl the PList happens to be in the tuples first entry
            Object list = GetItemDynamicNode.getUncached().execute(args.getSequenceStorage(), 0);
            return doExecute(frame, thisModule, path, list instanceof PList ? (PList) list : args);
        }

        @Specialization(limit = "1")
        Object executePath(VirtualFrame frame, PythonModule thisModule, Object path, PTuple args,
                        @CachedLibrary("path") PythonObjectLibrary lib) {
            return execute(frame, thisModule, lib.asPath(path), args);
        }

        @Specialization(limit = "1")
        Object executePath(VirtualFrame frame, PythonModule thisModule, Object path, PList args,
                        @CachedLibrary("path") PythonObjectLibrary lib) {
            return doExecute(frame, thisModule, lib.asPath(path), args);
        }

        Object doExecute(VirtualFrame frame, PythonModule thisModule, String path, PSequence args) {
            if (!getContext().isExecutableAccessAllowed()) {
                throw raiseOSError(frame, OSErrorEnum.EPERM);
            }
            try {
                return doExecuteInternal(thisModule, path, args);
            } catch (Exception e) {
                throw raiseOSError(frame, e, path);
            }
        }

        @TruffleBoundary
        Object doExecuteInternal(PythonModule thisModule, String path, PSequence args) throws IOException {
            int size = args.getSequenceStorage().length();
            if (size == 0) {
                throw raise(ValueError, ErrorMessages.ARG_D_MUST_NOT_BE_EMPTY, 2);
            }
            String[] cmd = new String[size];
            // We don't need the path variable because it's already in the array
            // but I need to process it for CI gate
            cmd[0] = path;
            for (int i = 0; i < size; i++) {
                cmd[i] = GetItemDynamicNode.getUncached().execute(args.getSequenceStorage(), i).toString();
            }
            PDict environ = (PDict) thisModule.getAttribute("environ");
            ProcessBuilder builder = new ProcessBuilder(cmd);
            Map<String, String> environment = builder.environment();
            environ.entries().forEach(entry -> {
                environment.put(new String(toBytes.execute(entry.key)), new String(toBytes.execute(entry.value)));
            });
            Process pr = builder.start();
            BufferedReader bfr = new BufferedReader(new InputStreamReader(pr.getInputStream()));
            OutputStream stream = getContext().getEnv().out();
            String line = "";
            while ((line = bfr.readLine()) != null) {
                stream.write(line.getBytes());
                stream.write("\n".getBytes());
            }
            BufferedReader stderr = new BufferedReader(new InputStreamReader(pr.getErrorStream()));
            OutputStream errStream = getContext().getEnv().err();
            line = "";
            while ((line = stderr.readLine()) != null) {
                errStream.write(line.getBytes());
                errStream.write("\n".getBytes());
            }
            try {
                pr.waitFor();
            } catch (InterruptedException e) {
                throw new IOException(e);
            }
            throw new PythonExitException(this, pr.exitValue());
        }
    }

    @Builtin(name = "getpid", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class GetPidNode extends PythonBuiltinNode {
        @Specialization
        long getPid(@CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            return posixLib.getpid(getPosixSupport());
        }
    }

    @Builtin(name = "getuid", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class GetUidNode extends PythonBuiltinNode {
        @Specialization
        static int getPid() {
            return getSystemUid();
        }

        @TruffleBoundary
        static int getSystemUid() {
            String osName = System.getProperty("os.name");
            if (osName.contains("Linux")) {
                return (int) new com.sun.security.auth.module.UnixSystem().getUid();
            }
            return 1000;
        }
    }

    @Builtin(name = "open", minNumOfPositionalArgs = 2, parameterNames = {"path", "flags", "mode"}, keywordOnlyNames = {"dir_fd"})
    @ArgumentClinic(name = "path", conversionClass = PathConversionNode.class, args = {"false", "false"})
    @ArgumentClinic(name = "flags", conversion = ClinicConversion.Int, defaultValue = "-1")
    @ArgumentClinic(name = "mode", conversion = ClinicConversion.Int, defaultValue = "0777")
    @ArgumentClinic(name = "dir_fd", conversionClass = DirFdConversionNode.class)
    @GenerateNodeFactory
    abstract static class NfiOpenNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.NfiOpenNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        int open(VirtualFrame frame, PosixPath path, int flags, int mode, int dirFd,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Cached BranchProfile asyncProfile,
                        @Cached BranchProfile errorProfile) {
            int fixedFlags = flags | CLOEXEC;
            auditNode.audit("open", path.originalObject, PNone.NONE, fixedFlags);
            while (true) {
                try {
                    return posixLib.openat(getPosixSupport(), dirFd, path.value, fixedFlags, mode);
                } catch (PosixException e) {
                    errorProfile.enter();
                    if (e.getErrorCode() == OSErrorEnum.EINTR.getNumber()) {
                        getContext().triggerAsyncActions(frame, asyncProfile);
                    } else {
                        throw raiseOSErrorFromPosixException(frame, e, path.originalObject);
                    }
                }
            }
        }
    }

    @Builtin(name = "close", minNumOfPositionalArgs = 1, parameterNames = {"fd"})
    @ArgumentClinic(name = "fd", conversion = ClinicConversion.Int, defaultValue = "-1")
    @GenerateNodeFactory
    abstract static class NfiCloseNode extends PythonUnaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.NfiCloseNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        PNone close(VirtualFrame frame, int fd,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            try {
                posixLib.close(getPosixSupport(), fd);
                return PNone.NONE;
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            }
        }
    }

    @Builtin(name = "read", minNumOfPositionalArgs = 2, parameterNames = {"fd", "length"})
    @ArgumentClinic(name = "fd", conversion = ClinicConversion.Int, defaultValue = "-1")
    @ArgumentClinic(name = "length", conversion = ClinicConversion.Index, defaultValue = "-1")
    @GenerateNodeFactory
    abstract static class NfiReadNode extends PythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.NfiReadNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        PBytes read(VirtualFrame frame, int fd, int length,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached BranchProfile asyncProfile,
                        @Cached BranchProfile errorProfile) {
            if (length < 0) {
                int error = OSErrorEnum.EINVAL.getNumber();
                throw raiseOSError(frame, error, posixLib.strerror(getPosixSupport(), error));
            }
            while (true) {
                try {
                    Buffer result = posixLib.read(getPosixSupport(), fd, length);
                    if (result.length > Integer.MAX_VALUE) {
                        // sanity check that it is safe to cast result.length to int, to be removed
                        // once we support large arrays
                        throw CompilerDirectives.shouldNotReachHere("Posix read() returned more bytes than requested");
                    }
                    return factory().createBytes(result.data, 0, (int) result.length);
                } catch (PosixException e) {
                    errorProfile.enter();
                    if (e.getErrorCode() == OSErrorEnum.EINTR.getNumber()) {
                        getContext().triggerAsyncActions(frame, asyncProfile);
                    } else {
                        throw raiseOSErrorFromPosixException(frame, e);
                    }
                }
            }
        }
    }

    @Builtin(name = "write", minNumOfPositionalArgs = 2, parameterNames = {"fd", "data"})
    @ArgumentClinic(name = "fd", conversion = ClinicConversion.Int, defaultValue = "-1")
    @ArgumentClinic(name = "data", conversion = ClinicConversion.Buffer)
    @GenerateNodeFactory
    abstract static class NfiWriteNode extends PythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.NfiWriteNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        long write(VirtualFrame frame, int fd, byte[] data,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached BranchProfile asyncProfile,
                        @Cached BranchProfile errorProfile) {
            while (true) {
                try {
                    return posixLib.write(getPosixSupport(), fd, Buffer.wrap(data));
                } catch (PosixException e) {
                    errorProfile.enter();
                    if (e.getErrorCode() == OSErrorEnum.EINTR.getNumber()) {
                        getContext().triggerAsyncActions(frame, asyncProfile);
                    } else {
                        throw raiseOSErrorFromPosixException(frame, e);
                    }
                }
            }
        }
    }

    @Builtin(name = "dup", minNumOfPositionalArgs = 1, parameterNames = {"fd"})
    @ArgumentClinic(name = "fd", conversion = ClinicConversion.Int, defaultValue = "-1")
    @GenerateNodeFactory
    abstract static class NfiDupNode extends PythonUnaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.NfiDupNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        int dup(VirtualFrame frame, int fd,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            try {
                return posixLib.dup(getPosixSupport(), fd);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            }
        }
    }

    @Builtin(name = "dup2", minNumOfPositionalArgs = 2, parameterNames = {"fd", "fd2", "inheritable"})
    @ArgumentClinic(name = "fd", conversion = ClinicConversion.Int, defaultValue = "-1")
    @ArgumentClinic(name = "fd2", conversion = ClinicConversion.Int, defaultValue = "-1")
    @ArgumentClinic(name = "inheritable", conversion = ClinicConversion.Boolean, defaultValue = "true")
    @GenerateNodeFactory
    abstract static class NfiDup2Node extends PythonTernaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.NfiDup2NodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        int dup2(VirtualFrame frame, int fd, int fd2, boolean inheritable,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            if (fd < 0 || fd2 < 0) {
                // CPython does not set errno here and raises a 'random' OSError
                // (possibly with errno=0 Success)
                int error = OSErrorEnum.EINVAL.getNumber();
                throw raiseOSError(frame, error, posixLib.strerror(getPosixSupport(), error));
            }

            try {
                return posixLib.dup2(getPosixSupport(), fd, fd2, inheritable);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            }
        }
    }

    @Builtin(name = "get_inheritable", minNumOfPositionalArgs = 1, parameterNames = {"fd"})
    @ArgumentClinic(name = "fd", conversion = ClinicConversion.Int, defaultValue = "-1")
    @GenerateNodeFactory
    abstract static class NfiGetInheritableNode extends PythonUnaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.NfiGetInheritableNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        boolean getInheritable(VirtualFrame frame, int fd,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            try {
                return posixLib.getInheritable(getPosixSupport(), fd);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            }
        }
    }

    @Builtin(name = "set_inheritable", minNumOfPositionalArgs = 2, parameterNames = {"fd", "inheritable"})
    @ArgumentClinic(name = "fd", conversion = ClinicConversion.Int, defaultValue = "-1")
    @ArgumentClinic(name = "inheritable", conversion = ClinicConversion.Int, defaultValue = "-1")
    @GenerateNodeFactory
    abstract static class NfiSetInheritableNode extends PythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.NfiSetInheritableNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        PNone setInheritable(VirtualFrame frame, int fd, int inheritable,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            try {
                // not sure why inheritable is not a boolean, but that is how they do it in CPython
                posixLib.setInheritable(getPosixSupport(), fd, inheritable != 0);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "pipe", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    abstract static class NfiPipeNode extends PythonBuiltinNode {

        @Specialization
        PTuple pipe(VirtualFrame frame,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            int[] pipe;
            try {
                pipe = posixLib.pipe(getPosixSupport());
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            }
            return factory().createTuple(new Object[]{pipe[0], pipe[1]});
        }
    }

    @Builtin(name = "lseek", minNumOfPositionalArgs = 3, parameterNames = {"fd", "pos", "how"})
    @ArgumentClinic(name = "fd", conversion = ClinicConversion.Int, defaultValue = "-1")
    @ArgumentClinic(name = "pos", conversionClass = OffsetConversionNode.class)
    @ArgumentClinic(name = "how", conversion = ClinicConversion.Int, defaultValue = "-1")
    @GenerateNodeFactory
    abstract static class NfiLseekNode extends PythonTernaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.NfiLseekNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        long lseek(VirtualFrame frame, int fd, long pos, int how,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            try {
                return posixLib.lseek(getPosixSupport(), fd, pos, how);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            }
        }
    }

    @Builtin(name = "ftruncate", minNumOfPositionalArgs = 2, parameterNames = {"fd", "length"})
    @ArgumentClinic(name = "fd", conversion = ClinicConversion.Int, defaultValue = "-1")
    @ArgumentClinic(name = "length", conversionClass = OffsetConversionNode.class)
    @GenerateNodeFactory
    abstract static class NfiFtruncateNode extends PythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.NfiFtruncateNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        PNone ftruncate(VirtualFrame frame, int fd, long length,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Cached BranchProfile asyncProfile,
                        @Cached BranchProfile errorProfile) {
            auditNode.audit("os.truncate", fd, length);
            while (true) {
                try {
                    posixLib.ftruncate(getPosixSupport(), fd, length);
                    return PNone.NONE;
                } catch (PosixException e) {
                    errorProfile.enter();
                    if (e.getErrorCode() == OSErrorEnum.EINTR.getNumber()) {
                        getContext().triggerAsyncActions(frame, asyncProfile);
                    } else {
                        throw raiseOSErrorFromPosixException(frame, e);
                    }
                }
            }
        }
    }

    @Builtin(name = "fsync", minNumOfPositionalArgs = 1, parameterNames = "fd")
    @ArgumentClinic(name = "fd", conversionClass = FileDescriptorConversionNode.class)
    @GenerateNodeFactory
    abstract static class NfiFSyncNode extends PythonUnaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.NfiFSyncNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        PNone fsync(VirtualFrame frame, int fd,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached BranchProfile asyncProfile,
                        @Cached BranchProfile errorProfile) {
            while (true) {
                try {
                    posixLib.fsync(getPosixSupport(), fd);
                    return PNone.NONE;
                } catch (PosixException e) {
                    errorProfile.enter();
                    if (e.getErrorCode() == OSErrorEnum.EINTR.getNumber()) {
                        getContext().triggerAsyncActions(frame, asyncProfile);
                    } else {
                        throw raiseOSErrorFromPosixException(frame, e);
                    }
                }
            }
        }
    }

    @Builtin(name = "get_blocking", minNumOfPositionalArgs = 1, parameterNames = {"fd"})
    @ArgumentClinic(name = "fd", conversion = ClinicConversion.Int, defaultValue = "-1")
    @GenerateNodeFactory
    abstract static class NfiGetBlockingNode extends PythonUnaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.NfiGetBlockingNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        boolean getBlocking(VirtualFrame frame, int fd,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            try {
                return posixLib.getBlocking(getPosixSupport(), fd);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            }
        }
    }

    @Builtin(name = "set_blocking", minNumOfPositionalArgs = 2, parameterNames = {"fd", "blocking"})
    @ArgumentClinic(name = "fd", conversion = ClinicConversion.Int, defaultValue = "-1")
    @ArgumentClinic(name = "blocking", conversion = ClinicConversion.Boolean, defaultValue = "false")
    @GenerateNodeFactory
    abstract static class NfiSetBlockingNode extends PythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.NfiSetBlockingNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        PNone setBlocking(VirtualFrame frame, int fd, boolean blocking,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            try {
                posixLib.setBlocking(getPosixSupport(), fd, blocking);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "get_terminal_size", minNumOfPositionalArgs = 0, parameterNames = {"fd"})
    @ArgumentClinic(name = "fd", conversion = ClinicConversion.Int, defaultValue = "1")
    @GenerateNodeFactory
    abstract static class NfiGetTerminalSizeNode extends PythonUnaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.NfiGetTerminalSizeNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        PTuple getTerminalSize(VirtualFrame frame, int fd,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            // TODO default value should be fileno(stdout)
            try {
                int[] result = posixLib.getTerminalSize(getPosixSupport(), fd);
                // TODO intrinsify the named tuple
                return factory().createTuple(new Object[]{result[0], result[1]});
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            }
        }
    }

    @Builtin(name = "stat", minNumOfPositionalArgs = 1, parameterNames = {"path"}, keywordOnlyNames = {"dir_fd", "follow_symlinks"})
    @ArgumentClinic(name = "path", conversionClass = PathConversionNode.class, args = {"false", "true"})
    @ArgumentClinic(name = "dir_fd", conversionClass = DirFdConversionNode.class)
    @ArgumentClinic(name = "follow_symlinks", conversion = ClinicConversion.Boolean, defaultValue = "true")
    @GenerateNodeFactory
    abstract static class NfiStatNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.NfiStatNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        PTuple doStatPath(VirtualFrame frame, PosixPath path, int dirFd, boolean followSymlinks,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached @Shared("positive") ConditionProfile positiveLongProfile) {
            try {
                long[] out = posixLib.fstatat(getPosixSupport(), dirFd, path.value, followSymlinks);
                return createStatResult(factory(), positiveLongProfile, out);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e, path.originalObject);
            }
        }

        @Specialization(guards = "!isDefault(dirFd)")
        @SuppressWarnings("unused")
        PTuple doStatFdWithDirFd(PosixFd fd, int dirFd, boolean followSymlinks) {
            throw raise(ValueError, ErrorMessages.CANT_SPECIFY_DIRFD_WITHOUT_PATH, "stat");
        }

        @Specialization(guards = {"isDefault(dirFd)", "!followSymlinks"})
        @SuppressWarnings("unused")
        PTuple doStatFdWithFollowSymlinks(VirtualFrame frame, PosixFd fd, int dirFd, boolean followSymlinks) {
            throw raise(ValueError, ErrorMessages.CANNOT_USE_FD_AND_FOLLOW_SYMLINKS_TOGETHER, "stat");
        }

        @Specialization(guards = {"isDefault(dirFd)", "followSymlinks"})
        PTuple doStatFd(VirtualFrame frame, PosixFd fd, @SuppressWarnings("unused") int dirFd, @SuppressWarnings("unused") boolean followSymlinks,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached @Shared("positive") ConditionProfile positiveLongProfile) {
            try {
                long[] out = posixLib.fstat(getPosixSupport(), fd.fd);
                return createStatResult(factory(), positiveLongProfile, out);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e, fd.originalObject);
            }
        }

        protected static boolean isDefault(int dirFd) {
            return dirFd == PosixSupportLibrary.DEFAULT_DIR_FD;
        }
    }

    @Builtin(name = "lstat", minNumOfPositionalArgs = 1, parameterNames = {"path"}, keywordOnlyNames = {"dir_fd"})
    @ArgumentClinic(name = "path", conversionClass = PathConversionNode.class, args = {"false", "false"})
    @ArgumentClinic(name = "dir_fd", conversionClass = DirFdConversionNode.class)
    @GenerateNodeFactory
    abstract static class NfiLStatNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.NfiLStatNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        PTuple doStatPath(VirtualFrame frame, PosixPath path, int dirFd,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached ConditionProfile positiveLongProfile) {
            try {
                long[] out = posixLib.fstatat(getPosixSupport(), dirFd, path.value, false);
                return createStatResult(factory(), positiveLongProfile, out);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e, path.originalObject);
            }
        }
    }

    @Builtin(name = "fstat", minNumOfPositionalArgs = 1, parameterNames = {"fd"})
    @ArgumentClinic(name = "fd", conversion = ClinicConversion.Int, defaultValue = "-1")
    @GenerateNodeFactory
    abstract static class NfiFStatNode extends PythonUnaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.NfiFStatNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        PTuple doStatFd(VirtualFrame frame, int fd,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached ConditionProfile positiveLongProfile,
                        @Cached BranchProfile asyncProfile,
                        @Cached BranchProfile errorProfile) {
            while (true) {
                try {
                    long[] out = posixLib.fstat(getPosixSupport(), fd);
                    return createStatResult(factory(), positiveLongProfile, out);
                } catch (PosixException e) {
                    errorProfile.enter();
                    if (e.getErrorCode() == OSErrorEnum.EINTR.getNumber()) {
                        getContext().triggerAsyncActions(frame, asyncProfile);
                    } else {
                        throw raiseOSErrorFromPosixException(frame, e);
                    }
                }
            }
        }
    }

    @Builtin(name = "uname", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    abstract static class NfiUnameNode extends PythonBuiltinNode {

        @Specialization
        PTuple uname(VirtualFrame frame,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            try {
                return factory().createTuple(posixLib.uname(getPosixSupport()));
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            }
        }
    }

    @Builtin(name = "unlink", minNumOfPositionalArgs = 1, parameterNames = {"path"}, varArgsMarker = true, keywordOnlyNames = {"dir_fd"})
    @ArgumentClinic(name = "path", conversionClass = PathConversionNode.class, args = {"false", "false"})
    @ArgumentClinic(name = "dir_fd", conversionClass = DirFdConversionNode.class)
    @GenerateNodeFactory
    abstract static class NfiUnlinkNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.NfiUnlinkNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        PNone unlink(VirtualFrame frame, PosixPath path, int dirFd,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached SysModuleBuiltins.AuditNode auditNode) {
            auditNode.audit("os.remove", path.originalObject, dirFdForAudit(dirFd));
            try {
                posixLib.unlinkat(getPosixSupport(), dirFd, path.value, false);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e, path.originalObject);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "remove", minNumOfPositionalArgs = 1, parameterNames = {"path"}, varArgsMarker = true, keywordOnlyNames = {"dir_fd"})
    @ArgumentClinic(name = "path", conversionClass = PathConversionNode.class, args = {"false", "false"})
    @ArgumentClinic(name = "dir_fd", conversionClass = DirFdConversionNode.class)
    @GenerateNodeFactory
    abstract static class NfiRemoveNode extends NfiUnlinkNode {

        // although this built-in is the same as unlink(), we need to provide our own
        // ArgumentClinicProvider because the error messages contain the name of the built-in

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.NfiRemoveNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "symlink", minNumOfPositionalArgs = 2, parameterNames = {"src", "dst", "target_is_directory"}, keywordOnlyNames = {"dir_fd"})
    @ArgumentClinic(name = "src", conversionClass = PathConversionNode.class, args = {"false", "false"})
    @ArgumentClinic(name = "dst", conversionClass = PathConversionNode.class, args = {"false", "false"})
    @ArgumentClinic(name = "target_is_directory", conversion = ClinicConversion.Boolean, defaultValue = "false")
    @ArgumentClinic(name = "dir_fd", conversionClass = DirFdConversionNode.class)
    @GenerateNodeFactory
    abstract static class NfiSymlinkNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.NfiSymlinkNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        PNone symlink(VirtualFrame frame, PosixPath src, PosixPath dst, @SuppressWarnings("unused") boolean targetIsDir, int dirFd,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            try {
                posixLib.symlinkat(getPosixSupport(), src.value, dirFd, dst.value);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e, src.originalObject, dst.originalObject);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "mkdir", minNumOfPositionalArgs = 1, parameterNames = {"path", "mode"}, keywordOnlyNames = {"dir_fd"})
    @ArgumentClinic(name = "path", conversionClass = PathConversionNode.class, args = {"false", "false"})
    @ArgumentClinic(name = "mode", conversion = ClinicConversion.Int, defaultValue = "0777")
    @ArgumentClinic(name = "dir_fd", conversionClass = DirFdConversionNode.class)
    @GenerateNodeFactory
    abstract static class NfiMkdirNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.NfiMkdirNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        PNone mkdir(VirtualFrame frame, PosixPath path, int mode, int dirFd,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached SysModuleBuiltins.AuditNode auditNode) {
            auditNode.audit("os.mkdir", path.originalObject, mode, dirFdForAudit(dirFd));
            try {
                posixLib.mkdirat(getPosixSupport(), dirFd, path.value, mode);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e, path.originalObject);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "rmdir", minNumOfPositionalArgs = 1, parameterNames = {"path"}, keywordOnlyNames = {"dir_fd"})
    @ArgumentClinic(name = "path", conversionClass = PathConversionNode.class, args = {"false", "false"})
    @ArgumentClinic(name = "dir_fd", conversionClass = DirFdConversionNode.class)
    @GenerateNodeFactory
    abstract static class NfiRmdirNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.NfiRmdirNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        PNone rmdir(VirtualFrame frame, PosixPath path, int dirFd,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached SysModuleBuiltins.AuditNode auditNode) {
            auditNode.audit("os.rmdir", path.originalObject, dirFdForAudit(dirFd));
            try {
                posixLib.unlinkat(getPosixSupport(), dirFd, path.value, true);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e, path.originalObject);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "getcwd", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    abstract static class NfiGetcwdNode extends PythonBuiltinNode {
        @Specialization
        String getcwd(VirtualFrame frame,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            try {
                return posixLib.getPathAsString(getPosixSupport(), posixLib.getcwd(getPosixSupport()));
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            }
        }
    }

    @Builtin(name = "getcwdb", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    abstract static class NfiGetcwdbNode extends PythonBuiltinNode {
        @Specialization
        PBytes getcwdb(VirtualFrame frame,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            try {
                return opaquePathToBytes(posixLib.getcwd(getPosixSupport()), posixLib, getPosixSupport(), factory());
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            }
        }
    }

    @Builtin(name = "chdir", minNumOfPositionalArgs = 1, parameterNames = {"path"})
    @ArgumentClinic(name = "path", conversionClass = PathConversionNode.class, args = {"false", "true"})
    @GenerateNodeFactory
    abstract static class NfiChdirNode extends PythonUnaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.NfiChdirNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        PNone chdirPath(VirtualFrame frame, PosixPath path,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            try {
                posixLib.chdir(getPosixSupport(), path.value);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e, path.originalObject);
            }
            return PNone.NONE;
        }

        @Specialization
        PNone chdirFd(VirtualFrame frame, PosixFd fd,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            try {
                posixLib.fchdir(getPosixSupport(), fd.fd);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e, fd.originalObject);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "fchdir", minNumOfPositionalArgs = 1, parameterNames = {"fd"})
    @ArgumentClinic(name = "fd", conversionClass = FileDescriptorConversionNode.class)
    @GenerateNodeFactory
    abstract static class NfiFchdirNode extends PythonUnaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.NfiFchdirNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        PNone fchdir(VirtualFrame frame, int fd,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached BranchProfile asyncProfile,
                        @Cached BranchProfile errorProfile) {
            while (true) {
                try {
                    posixLib.fchdir(getPosixSupport(), fd);
                    return PNone.NONE;
                } catch (PosixException e) {
                    errorProfile.enter();
                    if (e.getErrorCode() == OSErrorEnum.EINTR.getNumber()) {
                        getContext().triggerAsyncActions(frame, asyncProfile);
                    } else {
                        throw raiseOSErrorFromPosixException(frame, e);
                    }
                }
            }
        }
    }

    @Builtin(name = "isatty", minNumOfPositionalArgs = 1, parameterNames = {"fd"})
    @ArgumentClinic(name = "fd", conversion = ClinicConversion.Int, defaultValue = "-1")
    @GenerateNodeFactory
    abstract static class NfiIsattyNode extends PythonUnaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.NfiIsattyNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        boolean isatty(int fd,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            return posixLib.isatty(getPosixSupport(), fd);
        }
    }

    @Builtin(name = "ScandirIterator", takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PNfiScandirIterator, isPublic = false)
    @GenerateNodeFactory
    abstract static class NfiScandirIteratorNode extends PythonBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        Object scandirIterator(Object args, Object kwargs) {
            throw raise(TypeError, ErrorMessages.CANNOT_CREATE_INSTANCES, "posix.ScandirIterator");
        }
    }

    @Builtin(name = "DirEntry", takesVarArgs = true, takesVarKeywordArgs = true, constructsClass = PythonBuiltinClassType.PNfiDirEntry, isPublic = true)
    @GenerateNodeFactory
    abstract static class NfiDirEntryNode extends PythonBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        Object dirEntry(Object args, Object kwargs) {
            throw raise(TypeError, ErrorMessages.CANNOT_CREATE_INSTANCES, "posix.DirEntry");
        }
    }

    @Builtin(name = "scandir", minNumOfPositionalArgs = 0, parameterNames = {"path"})
    @ArgumentClinic(name = "path", conversionClass = PathConversionNode.class, args = {"true", "true"})
    @GenerateNodeFactory
    abstract static class NfiScandirNode extends PythonUnaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.NfiScandirNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        PNfiScandirIterator scandirPath(VirtualFrame frame, PosixPath path,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached SysModuleBuiltins.AuditNode auditNode) {
            auditNode.audit("os.scandir", path.originalObject == null ? PNone.NONE : path.originalObject);
            try {
                return factory().createNfiScandirIterator(getContext(), posixLib.opendir(getPosixSupport(), path.value), path);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e, path.originalObject);
            }
        }

        @Specialization
        PNfiScandirIterator scandirFd(VirtualFrame frame, PosixFd fd,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached SysModuleBuiltins.AuditNode auditNode) {
            auditNode.audit("os.scandir", fd.originalObject);
            try {
                return factory().createNfiScandirIterator(getContext(), posixLib.fdopendir(getPosixSupport(), fd.fd), fd);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e, fd.originalObject);
            }
        }
    }

    @Builtin(name = "listdir", minNumOfPositionalArgs = 0, parameterNames = {"path"})
    @ArgumentClinic(name = "path", conversionClass = PathConversionNode.class, args = {"true", "true"})
    @GenerateNodeFactory
    abstract static class NfiListdirNode extends PythonUnaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.NfiListdirNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        PList listdirPath(VirtualFrame frame, PosixPath path,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached SysModuleBuiltins.AuditNode auditNode) {
            auditNode.audit("os.listdir", path.originalObject == null ? PNone.NONE : path.originalObject);
            try {
                return listdir(frame, posixLib.opendir(getPosixSupport(), path.value), path.wasBufferLike, posixLib);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e, path.originalObject);
            }
        }

        @Specialization
        PList listdirFd(VirtualFrame frame, PosixFd fd,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached SysModuleBuiltins.AuditNode auditNode) {
            auditNode.audit("os.listdir", fd.originalObject);
            try {
                return listdir(frame, posixLib.fdopendir(getPosixSupport(), fd.fd), false, posixLib);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e, fd.originalObject);
            }
        }

        private PList listdir(VirtualFrame frame, Object dirStream, boolean produceBytes, PosixSupportLibrary posixLib) {
            List<Object> list = new ArrayList<>();
            try {
                while (true) {
                    Object dirEntry = posixLib.readdir(getPosixSupport(), dirStream);
                    if (dirEntry == null) {
                        return factory().createList(listToArray(list));
                    }
                    Object name = posixLib.dirEntryGetName(getPosixSupport(), dirEntry);
                    if (produceBytes) {
                        addToList(list, opaquePathToBytes(name, posixLib, getPosixSupport(), factory()));
                    } else {
                        addToList(list, posixLib.getPathAsString(getPosixSupport(), name));
                    }
                }
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            } finally {
                posixLib.closedir(getPosixSupport(), dirStream);
            }
        }

        @TruffleBoundary
        private static void addToList(List<Object> list, Object element) {
            list.add(element);
        }

        @TruffleBoundary
        private static Object[] listToArray(List<Object> list) {
            return list.toArray();
        }
    }

    @Builtin(name = "utime", minNumOfPositionalArgs = 1, parameterNames = {"path", "times"}, varArgsMarker = true, keywordOnlyNames = {"ns", "dir_fd", "follow_symlinks"})
    @ArgumentClinic(name = "path", conversionClass = PathConversionNode.class, args = {"false", "true"})
    @ArgumentClinic(name = "dir_fd", conversionClass = DirFdConversionNode.class)
    @ArgumentClinic(name = "follow_symlinks", conversion = ClinicConversion.Boolean, defaultValue = "true")
    @GenerateNodeFactory
    abstract static class NfiUtimeNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.NfiUtimeNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = {"isNoValue(ns)"})
        @SuppressWarnings("unused")
        PNone pathNow(VirtualFrame frame, PosixPath path, PNone times, PNone ns, int dirFd, boolean followSymlinks,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            auditNode.audit("os.utime", path.originalObject, PNone.NONE, PNone.NONE, dirFdForAudit(dirFd));
            callUtimeNsAt(frame, path, null, dirFd, followSymlinks, posixLib);
            return PNone.NONE;
        }

        @Specialization(guards = {"isNoValue(ns)", "isDefault(dirFd)", "followSymlinks"})
        @SuppressWarnings("unused")
        PNone fdNow(VirtualFrame frame, PosixFd fd, PNone times, PNone ns, int dirFd, boolean followSymlinks,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            auditNode.audit("os.utime", fd.originalObject, PNone.NONE, PNone.NONE, dirFdForAudit(dirFd));
            callFutimeNs(frame, fd, null, posixLib);
            return PNone.NONE;
        }

        @Specialization(guards = {"isNoValue(ns)"})
        PNone pathTimes(VirtualFrame frame, PosixPath path, PTuple times, @SuppressWarnings("unused") PNone ns, int dirFd, boolean followSymlinks,
                        @Cached LenNode lenNode,
                        @Cached("createNotNormalized()") GetItemNode getItemNode,
                        @Cached ObjectToTimespecNode objectToTimespecNode,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            long[] timespec = convertToTimespec(frame, times, lenNode, getItemNode, objectToTimespecNode);
            auditNode.audit("os.utime", path.originalObject, times, PNone.NONE, dirFdForAudit(dirFd));
            callUtimeNsAt(frame, path, timespec, dirFd, followSymlinks, posixLib);
            return PNone.NONE;
        }

        @Specialization(guards = {"isNoValue(ns)", "isDefault(dirFd)", "followSymlinks"})
        @SuppressWarnings("unused")
        PNone fdTimes(VirtualFrame frame, PosixFd fd, PTuple times, PNone ns, int dirFd, boolean followSymlinks,
                        @Cached LenNode lenNode,
                        @Cached("createNotNormalized()") GetItemNode getItemNode,
                        @Cached ObjectToTimespecNode objectToTimespecNode,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            long[] timespec = convertToTimespec(frame, times, lenNode, getItemNode, objectToTimespecNode);
            auditNode.audit("os.utime", fd.originalObject, times, PNone.NONE, dirFdForAudit(dirFd));
            callFutimeNs(frame, fd, timespec, posixLib);
            return PNone.NONE;
        }

        @Specialization
        PNone pathNs(VirtualFrame frame, PosixPath path, @SuppressWarnings("unused") PNone times, PTuple ns, int dirFd, boolean followSymlinks,
                        @Cached LenNode lenNode,
                        @Cached("createNotNormalized()") GetItemNode getItemNode,
                        @Cached SplitLongToSAndNsNode splitLongToSAndNsNode,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            long[] timespec = convertToTimespec(frame, ns, lenNode, getItemNode, splitLongToSAndNsNode);
            auditNode.audit("os.utime", path.originalObject, PNone.NONE, ns, dirFdForAudit(dirFd));
            callUtimeNsAt(frame, path, timespec, dirFd, followSymlinks, posixLib);
            return PNone.NONE;
        }

        @Specialization(guards = {"isDefault(dirFd)", "followSymlinks"})
        @SuppressWarnings("unused")
        PNone fdNs(VirtualFrame frame, PosixFd fd, PNone times, PTuple ns, int dirFd, boolean followSymlinks,
                        @Cached LenNode lenNode,
                        @Cached("createNotNormalized()") GetItemNode getItemNode,
                        @Cached SplitLongToSAndNsNode splitLongToSAndNsNode,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            long[] timespec = convertToTimespec(frame, ns, lenNode, getItemNode, splitLongToSAndNsNode);
            auditNode.audit("os.utime", fd.originalObject, PNone.NONE, ns, dirFdForAudit(dirFd));
            callFutimeNs(frame, fd, timespec, posixLib);
            return PNone.NONE;
        }

        @Specialization(guards = {"!isPNone(times)", "!isNoValue(ns)"})
        @SuppressWarnings("unused")
        PNone bothSpecified(VirtualFrame frame, Object path, Object times, Object ns, int dirFd, boolean followSymlinks) {
            throw raise(ValueError, ErrorMessages.YOU_MAY_SPECIFY_EITHER_OR_BUT_NOT_BOTH, "utime", "times", "ns");
        }

        @Specialization(guards = {"!isPNone(times)", "!isPTuple(times)", "isNoValue(ns)"})
        @SuppressWarnings("unused")
        PNone timesNotATuple(VirtualFrame frame, Object path, Object times, PNone ns, int dirFd, boolean followSymlinks) {
            throw timesTupleError();
        }

        @Specialization(guards = {"!isNoValue(ns)", "!isPTuple(ns)"})
        @SuppressWarnings("unused")
        PNone nsNotATuple(VirtualFrame frame, Object path, PNone times, Object ns, int dirFd, boolean followSymlinks) {
            // ns can actually also contain objects implementing __divmod__, but CPython produces
            // this error message
            throw raise(TypeError, ErrorMessages.MUST_BE, "utime", "ns", "a tuple of two ints");
        }

        @Specialization(guards = {"isPNone(times) || isNoValue(ns)", "!isDefault(dirFd)"})
        @SuppressWarnings("unused")
        PNone fdWithDirFd(VirtualFrame frame, PosixFd fd, Object times, Object ns, int dirFd, boolean followSymlinks) {
            throw raise(ValueError, ErrorMessages.CANT_SPECIFY_DIRFD_WITHOUT_PATH, "utime");
        }

        @Specialization(guards = {"isPNone(times) || isNoValue(ns)", "isDefault(dirFd)", "!followSymlinks"})
        @SuppressWarnings("unused")
        PNone fdWithFollowSymlinks(VirtualFrame frame, PosixFd fd, Object times, Object ns, int dirFd, boolean followSymlinks) {
            throw raise(ValueError, ErrorMessages.CANNOT_USE_FD_AND_FOLLOW_SYMLINKS_TOGETHER, "utime");
        }

        private PException timesTupleError() {
            // times can actually also contain floats, but CPython produces this error message
            throw raise(TypeError, ErrorMessages.MUST_BE_EITHER_OR, "utime", "times", "a tuple of two ints", "None");
        }

        private long[] convertToTimespec(VirtualFrame frame, PTuple times, LenNode lenNode, GetItemNode getItemNode, ConvertToTimespecBaseNode convertToTimespecBaseNode) {
            if (lenNode.execute(times) != 2) {
                throw timesTupleError();
            }
            long[] timespec = new long[4];
            convertToTimespecBaseNode.execute(frame, getItemNode.execute(frame, times.getSequenceStorage(), 0), timespec, 0);
            convertToTimespecBaseNode.execute(frame, getItemNode.execute(frame, times.getSequenceStorage(), 1), timespec, 2);
            return timespec;
        }

        private void callUtimeNsAt(VirtualFrame frame, PosixPath path, long[] timespec, int dirFd, boolean followSymlinks, PosixSupportLibrary posixLib) {
            try {
                posixLib.utimensat(getPosixSupport(), dirFd, path.value, timespec, followSymlinks);
            } catch (PosixException e) {
                // filename is intentionally not included, see CPython's os_utime_impl
                throw raiseOSErrorFromPosixException(frame, e);
            }
        }

        private void callFutimeNs(VirtualFrame frame, PosixFd fd, long[] timespec, PosixSupportLibrary posixLib) {
            try {
                posixLib.futimens(getPosixSupport(), fd.fd, timespec);
            } catch (PosixException e) {
                // filename is intentionally not included, see CPython's os_utime_impl
                throw raiseOSErrorFromPosixException(frame, e);
            }
        }

        protected static boolean isDefault(int dirFd) {
            return dirFd == PosixSupportLibrary.DEFAULT_DIR_FD;
        }
    }

    @Builtin(name = "rename", minNumOfPositionalArgs = 2, parameterNames = {"src", "dst"}, varArgsMarker = true, keywordOnlyNames = {"src_dir_fd", "dst_dir_fd"})
    @ArgumentClinic(name = "src", conversionClass = PathConversionNode.class, args = {"false", "false"})
    @ArgumentClinic(name = "dst", conversionClass = PathConversionNode.class, args = {"false", "false"})
    @ArgumentClinic(name = "src_dir_fd", conversionClass = DirFdConversionNode.class)
    @ArgumentClinic(name = "dst_dir_fd", conversionClass = DirFdConversionNode.class)
    @GenerateNodeFactory
    abstract static class NfiRenameNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.NfiRenameNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        PNone rename(VirtualFrame frame, PosixPath src, PosixPath dst, int srcDirFd, int dstDirFd,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached SysModuleBuiltins.AuditNode auditNode) {
            auditNode.audit("os.rename", src.originalObject, dst.originalObject, dirFdForAudit(srcDirFd), dirFdForAudit(dstDirFd));
            try {
                posixLib.renameat(getPosixSupport(), srcDirFd, src.value, dstDirFd, dst.value);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e, src.originalObject, dst.originalObject);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "replace", minNumOfPositionalArgs = 2, parameterNames = {"src", "dst"}, varArgsMarker = true, keywordOnlyNames = {"src_dir_fd", "dst_dir_fd"})
    @ArgumentClinic(name = "src", conversionClass = PathConversionNode.class, args = {"false", "false"})
    @ArgumentClinic(name = "dst", conversionClass = PathConversionNode.class, args = {"false", "false"})
    @ArgumentClinic(name = "src_dir_fd", conversionClass = DirFdConversionNode.class)
    @ArgumentClinic(name = "dst_dir_fd", conversionClass = DirFdConversionNode.class)
    @GenerateNodeFactory
    abstract static class NfiReplaceNode extends NfiRenameNode {

        // although this built-in is the same as rename(), we need to provide our own
        // ArgumentClinicProvider because the error messages contain the name of the built-in

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.NfiReplaceNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "access", minNumOfPositionalArgs = 2, parameterNames = {"path", "mode"}, varArgsMarker = true, keywordOnlyNames = {"dir_fd", "effective_ids", "follow_symlinks"})
    @ArgumentClinic(name = "path", conversionClass = PathConversionNode.class, args = {"false", "false"})
    @ArgumentClinic(name = "mode", conversion = ClinicConversion.Int, defaultValue = "-1")
    @ArgumentClinic(name = "dir_fd", conversionClass = DirFdConversionNode.class)
    @ArgumentClinic(name = "effective_ids", defaultValue = "false", conversion = ClinicConversion.Boolean)
    @ArgumentClinic(name = "follow_symlinks", defaultValue = "true", conversion = ClinicConversion.Boolean)
    @GenerateNodeFactory
    abstract static class NfiAccessNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.NfiAccessNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        boolean access(PosixPath path, int mode, int dirFd, boolean effectiveIds, boolean followSymlinks,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            return posixLib.faccessat(getPosixSupport(), dirFd, path.value, mode, effectiveIds, followSymlinks);
        }
    }

    @Builtin(name = "chmod", minNumOfPositionalArgs = 2, parameterNames = {"path", "mode"}, varArgsMarker = true, keywordOnlyNames = {"dir_fd", "follow_symlinks"})
    @ArgumentClinic(name = "path", conversionClass = PathConversionNode.class, args = {"false", "true"})
    @ArgumentClinic(name = "mode", conversion = ClinicConversion.Int, defaultValue = "-1")
    @ArgumentClinic(name = "dir_fd", conversionClass = DirFdConversionNode.class)
    @ArgumentClinic(name = "follow_symlinks", defaultValue = "true", conversion = ClinicConversion.Boolean)
    @GenerateNodeFactory
    abstract static class NfiChmodNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.NfiChmodNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        PNone chmodFollow(VirtualFrame frame, PosixPath path, int mode, int dirFd, boolean followSymlinks,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            auditNode.audit("os.chmod", path.originalObject, mode, dirFdForAudit(dirFd));
            try {
                posixLib.fchmodat(getPosixSupport(), dirFd, path.value, mode, followSymlinks);
            } catch (PosixException e) {
                // TODO CPython checks for ENOTSUP as well
                if (e.getErrorCode() == OSErrorEnum.EOPNOTSUPP.getNumber() && !followSymlinks) {
                    if (dirFd != PosixSupportLibrary.DEFAULT_DIR_FD) {
                        throw raise(ValueError, ErrorMessages.CANNOT_USE_FD_AND_FOLLOW_SYMLINKS_TOGETHER, "chmod");
                    } else {
                        throw raise(NotImplementedError, ErrorMessages.UNAVAILABLE_ON_THIS_PLATFORM, "chmod", "follow_symlinks");
                    }
                }
                throw raiseOSErrorFromPosixException(frame, e, path.originalObject);
            }
            return PNone.NONE;
        }

        @Specialization
        PNone chmodFollow(VirtualFrame frame, PosixFd fd, int mode, int dirFd, @SuppressWarnings("unused") boolean followSymlinks,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            auditNode.audit("os.chmod", fd.originalObject, mode, dirFdForAudit(dirFd));
            // Unlike stat and utime which raise CANT_SPECIFY_DIRFD_WITHOUT_PATH or
            // CANNOT_USE_FD_AND_FOLLOW_SYMLINKS_TOGETHER when an inappropriate combination of
            // arguments is used, CPython's implementation of chmod simply ignores dir_fd and
            // follow_symlinks if a fd is specified instead of a path.
            try {
                posixLib.fchmod(getPosixSupport(), fd.fd, mode);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e, fd.originalObject);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "readlink", minNumOfPositionalArgs = 1, parameterNames = {"path"}, varArgsMarker = true, keywordOnlyNames = {"dir_fd"}, doc = "readlink(path, *, dir_fd=None) -> path\n" +
                    "\nReturn a string representing the path to which the symbolic link points.\n")
    @ArgumentClinic(name = "path", conversionClass = PathConversionNode.class, args = {"false", "false"})
    @ArgumentClinic(name = "dir_fd", conversionClass = DirFdConversionNode.class)
    @GenerateNodeFactory
    abstract static class NfiReadlinkNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.NfiReadlinkNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = "path.wasBufferLike")
        PBytes readlinkAsBytes(VirtualFrame frame, PosixPath path, int dirFd,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            try {
                return opaquePathToBytes(posixLib.readlinkat(getPosixSupport(), dirFd, path.value), posixLib, getPosixSupport(), factory());
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e, path.originalObject);
            }
        }

        @Specialization(guards = "!path.wasBufferLike")
        String readlinkAsString(VirtualFrame frame, PosixPath path, int dirFd,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            try {
                return posixLib.getPathAsString(getPosixSupport(), posixLib.readlinkat(getPosixSupport(), dirFd, path.value));
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e, path.originalObject);
            }
        }
    }

    @Builtin(name = "strerror", minNumOfPositionalArgs = 1, parameterNames = {"code"})
    @ArgumentClinic(name = "code", conversion = ClinicConversion.Int, defaultValue = "-1")
    @GenerateNodeFactory
    abstract static class NfiStrErrorNode extends PythonUnaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.NfiStrErrorNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        String getStrError(int code,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            return posixLib.strerror(getPosixSupport(), code);
        }
    }

    @Builtin(name = "_exit", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class ExitNode extends PythonBuiltinNode {
        @TruffleBoundary
        @Specialization
        Object exit(int status) {
            throw new PythonExitException(this, status);
        }
    }

    @Builtin(name = "waitpid", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class WaitpidNode extends PythonFileNode {
        @SuppressWarnings("unused")
        @Specialization
        PTuple waitpid(VirtualFrame frame, int pid, int options) {
            try {
                if (options == 0) {
                    int exitStatus = getResources().waitpid(pid);
                    return factory().createTuple(new Object[]{pid, exitStatus});
                } else if (options == WNOHANG) {
                    int[] res = getResources().exitStatus(pid);
                    return factory().createTuple(new Object[]{res[0], res[1]});
                } else {
                    throw raise(PythonBuiltinClassType.NotImplementedError, "Only 0 or WNOHANG are supported for waitpid");
                }
            } catch (IndexOutOfBoundsException e) {
                if (pid <= 0) {
                    throw raiseOSError(frame, OSErrorEnum.ECHILD);
                } else {
                    throw raiseOSError(frame, OSErrorEnum.ESRCH);
                }
            } catch (InterruptedException e) {
                throw raiseOSError(frame, OSErrorEnum.EINTR);
            }
        }

        @SuppressWarnings("unused")
        @Specialization
        PTuple waitpidFallback(VirtualFrame frame, Object pid, Object options,
                        @CachedLibrary(limit = "2") PythonObjectLibrary lib) {
            ThreadState threadState = PArguments.getThreadState(frame);
            return waitpid(frame, lib.asSizeWithState(pid, threadState), lib.asSizeWithState(options, threadState));
        }
    }

    @Builtin(name = "system", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class SystemNode extends PythonBuiltinNode {
        private static final TruffleLogger LOGGER = PythonLanguage.getLogger(SystemNode.class);

        static final String[] shell;
        static {
            String osProperty = System.getProperty("os.name");
            shell = osProperty != null && osProperty.toLowerCase(Locale.ENGLISH).startsWith("windows") ? new String[]{"cmd.exe", "/c"}
                            : new String[]{(System.getenv().getOrDefault("SHELL", "sh")), "-c"};
        }

        static class PipePump extends Thread {
            private static final int MAX_READ = 8192;
            private final InputStream in;
            private final OutputStream out;
            private final byte[] buffer;
            private volatile boolean finish;

            public PipePump(String name, InputStream in, OutputStream out) {
                this.setName(name);
                this.in = in;
                this.out = out;
                this.buffer = new byte[MAX_READ];
                this.finish = false;
            }

            @Override
            public void run() {
                try {
                    while (!finish || in.available() > 0) {
                        if (Thread.interrupted()) {
                            finish = true;
                        }
                        int read = in.read(buffer, 0, Math.min(MAX_READ, in.available()));
                        if (read == -1) {
                            return;
                        }
                        out.write(buffer, 0, read);
                    }
                } catch (IOException e) {
                }
            }

            public void finish() {
                finish = true;
                // Make ourselves max priority to flush data out as quickly as possible
                setPriority(Thread.MAX_PRIORITY);
                Thread.yield();
            }
        }

        @TruffleBoundary
        @Specialization
        int system(String cmd) {
            PythonContext context = getContext();
            if (!context.isExecutableAccessAllowed()) {
                return -1;
            }
            LOGGER.fine(() -> "os.system: " + cmd);
            String[] command = new String[]{shell[0], shell[1], cmd};
            Env env = context.getEnv();
            try {
                ProcessBuilder pb = new ProcessBuilder(command);
                pb.directory(new File(env.getCurrentWorkingDirectory().getPath()));
                PipePump stdout = null, stderr = null;
                boolean stdsArePipes = !terminalIsInteractive(context);
                if (stdsArePipes) {
                    pb.redirectInput(Redirect.PIPE);
                    pb.redirectOutput(Redirect.PIPE);
                    pb.redirectError(Redirect.PIPE);
                } else {
                    pb.inheritIO();
                }
                Process proc = pb.start();
                if (stdsArePipes) {
                    proc.getOutputStream().close(); // stdin will be closed
                    stdout = new PipePump(cmd + " [stdout]", proc.getInputStream(), env.out());
                    stderr = new PipePump(cmd + " [stderr]", proc.getErrorStream(), env.err());
                    stdout.start();
                    stderr.start();
                }
                int exitStatus = proc.waitFor();
                if (stdsArePipes) {
                    stdout.finish();
                    stderr.finish();
                }
                return exitStatus;
            } catch (IOException | InterruptedException e) {
                return -1;
            }
        }
    }

    @Builtin(name = "urandom", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 1, parameterNames = {"size"})
    @ArgumentClinic(name = "size", conversion = ClinicConversion.Index, defaultValue = "0")
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class URandomNode extends PythonUnaryClinicBuiltinNode {
        private static SecureRandom secureRandom;

        private static SecureRandom createRandomInstance() {
            try {
                return SecureRandom.getInstance("NativePRNGNonBlocking");
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }

        @Specialization
        @TruffleBoundary(allowInlining = true)
        PBytes urandom(int size) {
            if (secureRandom == null) {
                secureRandom = createRandomInstance();
            }
            byte[] bytes = new byte[size];
            secureRandom.nextBytes(bytes);
            return factory().createBytes(bytes);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.URandomNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "cpu_count", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    abstract static class CpuCountNode extends PythonBuiltinNode {
        @TruffleBoundary
        @Specialization
        static int getCpuCount() {
            return Runtime.getRuntime().availableProcessors();
        }
    }

    @Builtin(name = "umask", minNumOfPositionalArgs = 1, parameterNames = {"mask"})
    @ArgumentClinic(name = "mask", conversion = ClinicConversion.Int, defaultValue = "-1")
    @GenerateNodeFactory
    abstract static class UmaskNode extends PythonUnaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.UmaskNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        int umask(VirtualFrame frame, int mask,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            try {
                return posixLib.umask(getPosixSupport(), mask);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            }
        }
    }

    @Builtin(name = "ctermid", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    abstract static class CtermId extends PythonBuiltinNode {
        @Specialization
        static String ctermid() {
            return "/dev/tty";
        }
    }

    @Builtin(name = "kill", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class KillNode extends PythonBinaryBuiltinNode {
        private static final String[] KILL_SIGNALS = new String[]{"SIGKILL", "SIGQUIT", "SIGTRAP", "SIGABRT"};
        private static final String[] TERMINATION_SIGNALS = new String[]{"SIGTERM", "SIGINT"};

        @Specialization
        PNone kill(VirtualFrame frame, int pid, int signal,
                        @Cached ReadAttributeFromObjectNode readSignalNode,
                        @Cached IsNode isNode) {
            PythonContext context = getContext();
            PythonModule signalModule = context.getCore().lookupBuiltinModule("_signal");
            for (String name : TERMINATION_SIGNALS) {
                Object value = readSignalNode.execute(signalModule, name);
                if (isNode.execute(signal, value)) {
                    try {
                        context.getResources().sigterm(pid);
                    } catch (IndexOutOfBoundsException e) {
                        throw raiseOSError(frame, OSErrorEnum.ESRCH);
                    }
                    return PNone.NONE;
                }
            }
            for (String name : KILL_SIGNALS) {
                Object value = readSignalNode.execute(signalModule, name);
                if (isNode.execute(signal, value)) {
                    try {
                        context.getResources().sigkill(pid);
                    } catch (IndexOutOfBoundsException e) {
                        throw raiseOSError(frame, OSErrorEnum.ESRCH);
                    }
                    return PNone.NONE;
                }
            }
            Object dfl = readSignalNode.execute(signalModule, "SIG_DFL");
            if (isNode.execute(signal, dfl)) {
                try {
                    context.getResources().sigdfl(pid);
                } catch (IndexOutOfBoundsException e) {
                    throw raiseOSError(frame, OSErrorEnum.ESRCH);
                }
                return PNone.NONE;
            }
            throw raise(PythonBuiltinClassType.NotImplementedError, "Sending arbitrary signals to child processes. Can only send some kill and term signals.");
        }

        @Specialization(replaces = "kill")
        PNone killFallback(VirtualFrame frame, Object pid, Object signal,
                        @CachedLibrary(limit = "getCallSiteInlineCacheMaxDepth()") PythonObjectLibrary lib,
                        @Cached ReadAttributeFromObjectNode readSignalNode,
                        @Cached IsNode isNode) {
            ThreadState state = PArguments.getThreadState(frame);
            return kill(frame, lib.asSizeWithState(pid, state), lib.asSizeWithState(signal, state), readSignalNode, isNode);
        }
    }

    // ------------------
    // Helpers

    abstract static class ConvertToTimespecBaseNode extends PythonBuiltinBaseNode {
        abstract void execute(VirtualFrame frame, Object obj, long[] timespec, int offset);
    }

    /**
     * Equivalent of {@code _PyTime_ObjectToTimespec} as used in {@code os_utime_impl}.
     */
    abstract static class ObjectToTimespecNode extends ConvertToTimespecBaseNode {

        @Specialization(guards = "!isNan(value)")
        void doDoubleNotNan(double value, long[] timespec, int offset) {
            double denominator = 1000000000.0;
            double floatPart = value % 1;
            double intPart = value - floatPart;

            floatPart = Math.floor(floatPart * denominator);
            if (floatPart >= denominator) {
                floatPart -= denominator;
                intPart += 1.0;
            } else if (floatPart < 0) {
                floatPart += denominator;
                intPart -= 1.0;
            }
            assert 0.0 <= floatPart && floatPart < denominator;
            if (!MathGuards.fitLong(intPart)) {
                throw raise(OverflowError, ErrorMessages.TIMESTAMP_OUT_OF_RANGE);
            }
            timespec[offset] = (long) intPart;
            timespec[offset + 1] = (long) floatPart;
            assert 0 <= timespec[offset + 1] && timespec[offset + 1] < (long) denominator;
        }

        @Specialization(guards = "isNan(value)")
        @SuppressWarnings("unused")
        void doDoubleNan(double value, long[] timespec, int offset) {
            throw raise(ValueError, ErrorMessages.INVALID_VALUE_NAN);
        }

        @Specialization
        void doPFloat(PFloat obj, long[] timespec, int offset) {
            double value = obj.getValue();
            if (Double.isNaN(value)) {
                throw raise(ValueError, ErrorMessages.INVALID_VALUE_NAN);
            }
            doDoubleNotNan(value, timespec, offset);
        }

        @Specialization
        void doInt(int value, long[] timespec, int offset) {
            timespec[offset] = value;
            timespec[offset + 1] = 0;
        }

        @Specialization
        void doLong(long value, long[] timespec, int offset) {
            timespec[offset] = value;
            timespec[offset + 1] = 0;
        }

        @Specialization(guards = {"!isDouble(value)", "!isPFloat(value)", "!isInteger(value)"}, limit = "1")
        void doGeneric(VirtualFrame frame, Object value, long[] timespec, int offset,
                        @CachedLibrary("value") PythonObjectLibrary lib,
                        @Cached IsBuiltinClassProfile overflowProfile) {
            try {
                timespec[offset] = lib.asJavaLongWithState(value, PArguments.getThreadState(frame));
            } catch (PException e) {
                e.expect(OverflowError, overflowProfile);
                throw raise(OverflowError, ErrorMessages.TIMESTAMP_OUT_OF_RANGE);
            }
            timespec[offset + 1] = 0;
        }

        protected static boolean isNan(double value) {
            return Double.isNaN(value);
        }
    }

    /**
     * Equivalent of {@code split_py_long_to_s_and_ns} as used in {@code os_utime_impl}.
     */
    abstract static class SplitLongToSAndNsNode extends ConvertToTimespecBaseNode {

        private static final long BILLION = 1000000000;

        @Specialization
        void doInt(int value, long[] timespec, int offset) {
            doLong(value, timespec, offset);
        }

        @Specialization
        void doLong(long value, long[] timespec, int offset) {
            timespec[offset] = Math.floorDiv(value, BILLION);
            timespec[offset + 1] = Math.floorMod(value, BILLION);
        }

        @Specialization(guards = {"!isInteger(value)"})
        void doGeneric(VirtualFrame frame, Object value, long[] timespec, int offset,
                        @Cached("createDivmod()") LookupAndCallBinaryNode callDivmod,
                        @Cached LenNode lenNode,
                        @Cached("createNotNormalized()") GetItemNode getItemNode,
                        @CachedLibrary(limit = "2") PythonObjectLibrary lib) {
            Object divmod = callDivmod.executeObject(frame, value, BILLION);
            if (!PGuards.isPTuple(divmod) || lenNode.execute((PSequence) divmod) != 2) {
                throw raise(TypeError, ErrorMessages.MUST_RETURN_2TUPLE, value, divmod);
            }
            SequenceStorage storage = ((PTuple) divmod).getSequenceStorage();
            timespec[offset] = lib.asJavaLongWithState(getItemNode.execute(frame, storage, 0), PArguments.getThreadState(frame));
            timespec[offset + 1] = lib.asJavaLongWithState(getItemNode.execute(frame, storage, 1), PArguments.getThreadState(frame));
        }

        protected static LookupAndCallBinaryNode createDivmod() {
            return BinaryArithmetic.DivMod.create();
        }
    }

    static int dirFdForAudit(int dirFd) {
        return dirFd == PosixSupportLibrary.DEFAULT_DIR_FD ? -1 : dirFd;
    }

    public static PTuple createStatResult(PythonObjectFactory factory, ConditionProfile positiveLongProfile, long[] out) {
        Object[] res = new Object[16];
        for (int i = 0; i < 7; i++) {
            res[i] = PInt.createPythonIntFromUnsignedLong(factory, positiveLongProfile, out[i]);
        }
        res[6] = out[6];
        for (int i = 7; i < 10; i++) {
            long seconds = out[i];
            long nsFraction = out[i + 3];
            res[i] = seconds;
            res[i + 3] = seconds + nsFraction * 1.0e-9;
            res[i + 6] = factory.createInt(convertToNanoseconds(seconds, nsFraction));
        }
        // TODO intrinsify the os.stat_result named tuple and create the instance directly
        return factory.createTuple(res);
    }

    @TruffleBoundary
    private static BigInteger convertToNanoseconds(long sec, long ns) {
        // TODO it may be possible to do this in long without overflow
        BigInteger r = BigInteger.valueOf(sec);
        r = r.multiply(BigInteger.valueOf(1000000000));
        return r.add(BigInteger.valueOf(ns));
    }

    public static PBytes opaquePathToBytes(Object opaquePath, PosixSupportLibrary posixLib, Object posixSupport, PythonObjectFactory factory) {
        Buffer buf = posixLib.getPathAsBytes(posixSupport, opaquePath);
        if (buf.length > Integer.MAX_VALUE) {
            // sanity check that it is safe to cast result.length to int, to be removed once
            // we support large arrays
            throw CompilerDirectives.shouldNotReachHere("Posix path cannot fit into a Java array");
        }
        return factory.createBytes(buf.data, 0, (int) buf.length);
    }

    // ------------------
    // Converters

    /**
     * Equivalent of CPython's {@code path_converter()}. Always returns an {@code int}. If the
     * parameter is omitted, returns {@link PosixSupportLibrary#DEFAULT_DIR_FD}.
     */
    public abstract static class DirFdConversionNode extends ArgumentCastNodeWithRaise {

        @Specialization
        int doNone(@SuppressWarnings("unused") PNone value) {
            return PosixSupportLibrary.DEFAULT_DIR_FD;
        }

        @Specialization
        int doFdBool(boolean value) {
            return PInt.intValue(value);
        }

        @Specialization
        int doFdInt(int value) {
            return value;
        }

        @Specialization
        int doFdLong(long value) {
            return longToFd(value, getRaiseNode());
        }

        @Specialization
        int doFdPInt(PInt value,
                        @Cached CastToJavaLongLossyNode castToLongNode) {
            return doFdLong(castToLongNode.execute(value));
        }

        @Specialization(guards = {"!isPNone(value)", "!canBeInteger(value)", "lib.canBeIndex(value)"}, limit = "3")
        int doIndex(VirtualFrame frame, Object value,
                        @CachedLibrary("value") PythonObjectLibrary lib,
                        @Cached CastToJavaLongLossyNode castToLongNode) {
            Object o = lib.asIndexWithState(value, PArguments.getThreadState(frame));
            return doFdLong(castToLongNode.execute(o));
        }

        @Fallback
        Object doGeneric(Object value) {
            throw raise(TypeError, ErrorMessages.ARG_SHOULD_BE_INT_OR_NONE, value);
        }

        private static int longToFd(long value, PRaiseNode raiseNode) {
            if (value > Integer.MAX_VALUE) {
                throw raiseNode.raise(OverflowError, ErrorMessages.FD_IS_GREATER_THAN_MAXIMUM);
            }
            if (value < Integer.MIN_VALUE) {
                throw raiseNode.raise(OverflowError, ErrorMessages.FD_IS_LESS_THAN_MINIMUM);
            }
            return (int) value;
        }

        @ClinicConverterFactory(shortCircuitPrimitive = PrimitiveType.Int)
        public static DirFdConversionNode create() {
            return PosixModuleBuiltinsFactory.DirFdConversionNodeGen.create();
        }
    }

    /**
     * Equivalent of CPython's {@code path_converter()}. Always returns an instance of
     * {@link PosixFileHandle}.
     */
    public abstract static class PathConversionNode extends ArgumentCastNodeWithRaise {

        private final String functionNameWithColon;
        private final String argumentName;
        protected final boolean nullable;
        protected final boolean allowFd;
        @CompilationFinal private ContextReference<PythonContext> contextRef;

        public PathConversionNode(String functionName, String argumentName, boolean nullable, boolean allowFd) {
            this.functionNameWithColon = functionName != null ? functionName + ": " : "";
            this.argumentName = argumentName != null ? argumentName : "path";
            this.nullable = nullable;
            this.allowFd = allowFd;
        }

        @Specialization(guards = "nullable")
        PosixFileHandle doNone(@SuppressWarnings("unused") PNone value,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            return new PosixPath(null, checkPath(posixLib.createPathFromString(getPosixSupport(), ".")), false);
        }

        @Specialization(guards = "allowFd")
        PosixFileHandle doFdBool(boolean value) {
            return new PosixFd(value, PInt.intValue(value));
        }

        @Specialization(guards = "allowFd")
        PosixFileHandle doFdInt(int value) {
            return new PosixFd(value, value);
        }

        @Specialization(guards = "allowFd")
        PosixFileHandle doFdLong(long value) {
            return new PosixFd(value, DirFdConversionNode.longToFd(value, getRaiseNode()));
        }

        @Specialization(guards = "allowFd")
        PosixFileHandle doFdPInt(PInt value,
                        @Cached CastToJavaLongLossyNode castToLongNode) {
            return new PosixFd(value, DirFdConversionNode.longToFd(castToLongNode.execute(value), getRaiseNode()));
        }

        @Specialization
        PosixFileHandle doUnicode(String value,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            return new PosixPath(value, checkPath(posixLib.createPathFromString(getPosixSupport(), value)), false);
        }

        @Specialization
        PosixFileHandle doUnicode(PString value,
                        @Cached CastToJavaStringNode castToJavaStringNode,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            String str = castToJavaStringNode.execute(value);
            return new PosixPath(value, checkPath(posixLib.createPathFromString(getPosixSupport(), str)), false);
        }

        @Specialization
        PosixFileHandle doBytes(PBytesLike value,
                        @Cached BytesNodes.ToBytesNode toByteArrayNode,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            return new PosixPath(value, checkPath(posixLib.createPathFromBytes(getPosixSupport(), toByteArrayNode.execute(value))), true);
        }

        @Specialization(guards = {"!isHandled(value)", "lib.isBuffer(value)"}, limit = "1")
        PosixFileHandle doBuffer(VirtualFrame frame, Object value,
                        @CachedLibrary("value") PythonObjectLibrary lib,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached WarningsModuleBuiltins.WarnNode warningNode) {
            warningNode.warnFormat(frame, null, PythonBuiltinClassType.DeprecationWarning, 1,
                            ErrorMessages.S_S_SHOULD_BE_S_NOT_P, functionNameWithColon, argumentName, getAllowedTypes(), value);
            try {
                return new PosixPath(value, checkPath(posixLib.createPathFromBytes(getPosixSupport(), lib.getBufferBytes(value))), true);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere("Object claims to be a buffer but does not implement getBufferBytes");
            }
        }

        @Specialization(guards = {"!isHandled(value)", "!lib.isBuffer(value)", "allowFd", "lib.canBeIndex(value)"}, limit = "3")
        PosixFileHandle doIndex(VirtualFrame frame, Object value,
                        @CachedLibrary("value") PythonObjectLibrary lib,
                        @Cached CastToJavaLongLossyNode castToLongNode) {
            Object o = lib.asIndexWithState(value, PArguments.getThreadState(frame));
            return new PosixFd(value, DirFdConversionNode.longToFd(castToLongNode.execute(o), getRaiseNode()));
        }

        @Specialization(guards = {"!isHandled(value)", "!lib.isBuffer(value)", "!allowFd || !lib.canBeIndex(value)"}, limit = "3")
        PosixFileHandle doGeneric(VirtualFrame frame, Object value,
                        @CachedLibrary("value") PythonObjectLibrary lib,
                        @CachedLibrary(limit = "2") PythonObjectLibrary methodLib,
                        @Cached BytesNodes.ToBytesNode toByteArrayNode,
                        @Cached CastToJavaStringNode castToJavaStringNode,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            Object func = lib.lookupAttributeOnType(value, __FSPATH__);
            if (func == PNone.NO_VALUE) {
                throw raise(TypeError, ErrorMessages.S_S_SHOULD_BE_S_NOT_P, functionNameWithColon, argumentName,
                                getAllowedTypes(), value);
            }
            Object pathObject = methodLib.callUnboundMethodWithState(func, PArguments.getThreadState(frame), value);
            // 'pathObject' replaces 'value' as the PosixPath.originalObject for auditing purposes
            // by design
            if (pathObject instanceof PBytesLike) {
                return doBytes((PBytesLike) pathObject, toByteArrayNode, posixLib);
            }
            if (pathObject instanceof PString) {
                return doUnicode((PString) pathObject, castToJavaStringNode, posixLib);
            }
            if (pathObject instanceof String) {
                return doUnicode((String) pathObject, posixLib);
            }
            throw raise(TypeError, ErrorMessages.EXPECTED_FSPATH_TO_RETURN_STR_OR_BYTES, value, pathObject);
        }

        protected boolean isHandled(Object value) {
            return PGuards.isPNone(value) && nullable || PGuards.canBeInteger(value) && allowFd || PGuards.isString(value) || PGuards.isBytes(value);
        }

        private String getAllowedTypes() {
            return allowFd && nullable ? "string, bytes, os.PathLike, integer or None"
                            : allowFd ? "string, bytes, os.PathLike or integer" : nullable ? "string, bytes, os.PathLike or None" : "string, bytes or os.PathLike";
        }

        private Object checkPath(Object path) {
            if (path == null) {
                throw raise(ValueError, ErrorMessages.S_EMBEDDED_NULL_CHARACTER_IN_S, functionNameWithColon, argumentName);
            }
            return path;
        }

        private ContextReference<PythonContext> getContextRef() {
            if (contextRef == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                contextRef = lookupContextReference(PythonLanguage.class);
            }
            return contextRef;
        }

        private PythonContext getContext() {
            return getContextRef().get();
        }

        protected final Object getPosixSupport() {
            return getContext().getPosixSupport();
        }

        @ClinicConverterFactory
        public static PathConversionNode create(@BuiltinName String functionName, @ArgumentName String argumentName, boolean nullable, boolean allowFd) {
            return PosixModuleBuiltinsFactory.PathConversionNodeGen.create(functionName, argumentName, nullable, allowFd);
        }
    }

    /**
     * Equivalent of CPython's {@code Py_off_t_converter()}. Always returns a {@code long}.
     */
    public abstract static class OffsetConversionNode extends ArgumentCastNodeWithRaise {

        @Specialization
        static long doInt(int i) {
            return i;
        }

        @Specialization
        static long doLong(long l) {
            return l;
        }

        @Specialization(limit = "3")
        static long doOthers(VirtualFrame frame, Object value,
                        @CachedLibrary("value") PythonObjectLibrary lib) {
            return lib.asJavaLongWithState(value, PArguments.getThreadState(frame));
        }

        @ClinicConverterFactory(shortCircuitPrimitive = PrimitiveType.Long)
        public static OffsetConversionNode create() {
            return PosixModuleBuiltinsFactory.OffsetConversionNodeGen.create();
        }
    }

    /**
     * Equivalent of CPython's {@code fildes_converter()}. Always returns an {@code int}.
     */
    public abstract static class FileDescriptorConversionNode extends ArgumentCastNodeWithRaise {

        @Specialization
        int doFdInt(int value) {
            return value;
        }

        @Specialization(guards = "!isInt(value)", limit = "3")
        int doIndex(VirtualFrame frame, Object value,
                        @CachedLibrary("value") PythonObjectLibrary lib) {
            return lib.asFileDescriptorWithState(value, PArguments.getThreadState(frame));
        }

        protected static boolean isInt(Object value) {
            return value instanceof Integer;
        }

        @ClinicConverterFactory(shortCircuitPrimitive = PrimitiveType.Int)
        public static FileDescriptorConversionNode create() {
            return PosixModuleBuiltinsFactory.FileDescriptorConversionNodeGen.create();
        }
    }

    /**
     * Represents the result of {@code path_t} conversion. Similar to CPython's {@code path_t}
     * structure, but only contains the results of the conversion, not the conversion parameters.
     */
    public abstract static class PosixFileHandle {

        /**
         * Contains the original object (or the object returned by {@code __fspath__}) for auditing
         * purposes. This field is {code null} iff the path parameter was optional and the caller
         * did not provide it.
         */
        public final Object originalObject;

        protected PosixFileHandle(Object originalObject) {
            this.originalObject = originalObject;
        }
    }

    /**
     * Contains the path converted to the representation used by the {@code PosixSupportLibrary}
     * implementation
     *
     * @see PosixSupportLibrary#createPathFromString(Object, String)
     * @see PosixSupportLibrary#createPathFromBytes(Object, byte[])
     */
    public static class PosixPath extends PosixFileHandle {
        public final Object value;
        public final boolean wasBufferLike;

        public PosixPath(Object originalObject, Object value, boolean wasBufferLike) {
            super(originalObject);
            this.value = value;
            this.wasBufferLike = wasBufferLike;
        }
    }

    /**
     * Contains the file descriptor if it was allowed in the argument conversion node and the caller
     * provided an integer instead of a path.
     */
    public static class PosixFd extends PosixFileHandle {
        public final int fd;

        public PosixFd(Object originalObject, int fd) {
            super(originalObject);
            this.fd = fd;
        }
    }
}
