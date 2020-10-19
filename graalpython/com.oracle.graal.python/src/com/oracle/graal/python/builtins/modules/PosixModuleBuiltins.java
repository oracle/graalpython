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
import static com.oracle.truffle.api.TruffleFile.CREATION_TIME;
import static com.oracle.truffle.api.TruffleFile.IS_DIRECTORY;
import static com.oracle.truffle.api.TruffleFile.IS_REGULAR_FILE;
import static com.oracle.truffle.api.TruffleFile.IS_SYMBOLIC_LINK;
import static com.oracle.truffle.api.TruffleFile.LAST_ACCESS_TIME;
import static com.oracle.truffle.api.TruffleFile.LAST_MODIFIED_TIME;
import static com.oracle.truffle.api.TruffleFile.SIZE;
import static com.oracle.truffle.api.TruffleFile.UNIX_CTIME;
import static com.oracle.truffle.api.TruffleFile.UNIX_DEV;
import static com.oracle.truffle.api.TruffleFile.UNIX_GID;
import static com.oracle.truffle.api.TruffleFile.UNIX_GROUP;
import static com.oracle.truffle.api.TruffleFile.UNIX_INODE;
import static com.oracle.truffle.api.TruffleFile.UNIX_MODE;
import static com.oracle.truffle.api.TruffleFile.UNIX_NLINK;
import static com.oracle.truffle.api.TruffleFile.UNIX_OWNER;
import static com.oracle.truffle.api.TruffleFile.UNIX_PERMISSIONS;
import static com.oracle.truffle.api.TruffleFile.UNIX_UID;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.ProcessBuilder.Redirect;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.LinkOption;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;

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
import com.oracle.graal.python.builtins.modules.PosixModuleBuiltinsClinicProviders.StatNodeClinicProviderGen;
import com.oracle.graal.python.builtins.modules.PosixModuleBuiltinsFactory.StatNodeFactory;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes.LenNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GetItemDynamicNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GetItemNode;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PArguments.ThreadState;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.socket.PSocket;
import com.oracle.graal.python.builtins.objects.socket.SocketBuiltins;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.expression.IsExpressionNode.IsNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentCastNode.ArgumentCastNodeWithRaise;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.nodes.util.CastToJavaLongLossyNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.nodes.util.ChannelNodes.ReadFromChannelNode;
import com.oracle.graal.python.runtime.PosixResources;
import com.oracle.graal.python.runtime.PosixSupportLibrary;
import com.oracle.graal.python.runtime.PosixSupportLibrary.Buffer;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixException;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixFd;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixFileHandle;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixPath;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.exception.PythonExitException;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.util.FileDeleteShutdownHook;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.sun.security.auth.UnixNumericGroupPrincipal;
import com.sun.security.auth.UnixNumericUserPrincipal;

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

    // Apart from being consistent with definitions in C headers, the first three must have these
    // exact values on the Python side. SEEK_DATA and SEEK_HOLE should only be defined where
    // supported
    private static final int SEEK_SET = 0;
    private static final int SEEK_CUR = 1;
    private static final int SEEK_END = 2;
    private static final int SEEK_DATA = 3;
    private static final int SEEK_HOLE = 4;

    private static final int WNOHANG = 1;
    private static final int WUNTRACED = 3;

    private static final int F_OK = 0;
    private static final int X_OK = 1;
    private static final int W_OK = 2;
    private static final int R_OK = 4;

    private static PosixFilePermission[][] otherBitsToPermission = new PosixFilePermission[][]{
                    new PosixFilePermission[]{},
                    new PosixFilePermission[]{PosixFilePermission.OTHERS_EXECUTE},
                    new PosixFilePermission[]{PosixFilePermission.OTHERS_WRITE},
                    new PosixFilePermission[]{PosixFilePermission.OTHERS_WRITE, PosixFilePermission.OTHERS_EXECUTE},
                    new PosixFilePermission[]{PosixFilePermission.OTHERS_READ},
                    new PosixFilePermission[]{PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_EXECUTE},
                    new PosixFilePermission[]{PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_WRITE},
                    new PosixFilePermission[]{PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_WRITE, PosixFilePermission.OTHERS_EXECUTE},
    };
    private static PosixFilePermission[][] groupBitsToPermission = new PosixFilePermission[][]{
                    new PosixFilePermission[]{},
                    new PosixFilePermission[]{PosixFilePermission.GROUP_EXECUTE},
                    new PosixFilePermission[]{PosixFilePermission.GROUP_WRITE},
                    new PosixFilePermission[]{PosixFilePermission.GROUP_WRITE, PosixFilePermission.GROUP_EXECUTE},
                    new PosixFilePermission[]{PosixFilePermission.GROUP_READ},
                    new PosixFilePermission[]{PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_EXECUTE},
                    new PosixFilePermission[]{PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_WRITE},
                    new PosixFilePermission[]{PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_WRITE, PosixFilePermission.GROUP_EXECUTE},
    };
    private static PosixFilePermission[][] ownerBitsToPermission = new PosixFilePermission[][]{
                    new PosixFilePermission[]{},
                    new PosixFilePermission[]{PosixFilePermission.OWNER_EXECUTE},
                    new PosixFilePermission[]{PosixFilePermission.OWNER_WRITE},
                    new PosixFilePermission[]{PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE},
                    new PosixFilePermission[]{PosixFilePermission.OWNER_READ},
                    new PosixFilePermission[]{PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE},
                    new PosixFilePermission[]{PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE},
                    new PosixFilePermission[]{PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE},
    };

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
        builtinConstants.put("SEEK_SET", SEEK_SET);
        builtinConstants.put("SEEK_CUR", SEEK_CUR);
        builtinConstants.put("SEEK_END", SEEK_END);
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
                environment.put(new String(toBytes.execute(null, entry.key)), new String(toBytes.execute(null, entry.value)));
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

    @Builtin(name = "getcwd", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class CwdNode extends PythonBuiltinNode {
        @Specialization
        String cwd(VirtualFrame frame) {
            try {
                return getContext().getEnv().getCurrentWorkingDirectory().getPath();
            } catch (SecurityException e) {
                throw raiseOSError(frame, OSErrorEnum.EPERM);
            }
        }
    }

    @Builtin(name = "chdir", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ChdirNode extends PythonBuiltinNode {
        @Specialization
        PNone chdir(VirtualFrame frame, String spath) {
            Env env = getContext().getEnv();
            try {
                TruffleFile dir = env.getPublicTruffleFile(spath).getAbsoluteFile();
                env.setCurrentWorkingDirectory(dir);
                return PNone.NONE;
            } catch (Exception e) {
                throw raiseOSError(frame, e, spath);
            }
        }

        @Specialization(limit = "1")
        PNone chdirPath(VirtualFrame frame, Object path,
                        @CachedLibrary("path") PythonObjectLibrary lib) {
            return chdir(frame, lib.asPath(path));
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

    @Builtin(name = "fstat", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class FstatNode extends PythonFileNode {
        @Child private StatNode statNode;

        protected abstract Object executeWith(VirtualFrame frame, Object fd);

        @Specialization(guards = {"fd >= 0", "fd <= 2"})
        Object fstatStd(@SuppressWarnings("unused") int fd) {
            return factory().createTuple(new Object[]{
                            8592,
                            0, // ino
                            0, // dev
                            0, // nlink
                            0,
                            0,
                            0,
                            0,
                            0,
                            0
            });
        }

        @Specialization(guards = "fd > 2")
        Object fstat(VirtualFrame frame, int fd,
                        @Cached("create()") BranchProfile fstatForNonFile,
                        @Cached("createClassProfile()") ValueProfile channelClassProfile) {
            PosixResources resources = getResources();
            String filePath = resources.getFilePath(fd);
            if (filePath != null) {
                if (statNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    statNode = insert(StatNode.create());
                }
                return statNode.call(frame, resources.getFilePath(fd), PNone.NO_VALUE);
            } else {
                fstatForNonFile.enter();
                return fstatWithoutPath(resources, fd, channelClassProfile);
            }
        }

        @TruffleBoundary(allowInlining = true)
        private PTuple fstatWithoutPath(PosixResources resources, int fd, ValueProfile channelClassProfile) {
            Channel fileChannel = resources.getFileChannel(fd, channelClassProfile);
            int mode = 0;
            if (fileChannel instanceof ReadableByteChannel) {
                mode |= 0444;
            }
            if (fileChannel instanceof WritableByteChannel) {
                mode |= 0222;
            }
            return factory().createTuple(new Object[]{
                            mode,
                            0, // ino
                            0, // dev
                            0, // nlink
                            0,
                            0,
                            0,
                            0,
                            0,
                            0,
            });
        }

        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()")
        static Object fstatPInt(VirtualFrame frame, Object fd,
                        @CachedLibrary("fd") PythonObjectLibrary lib,
                        @Cached("create()") FstatNode recursive) {
            return recursive.executeWith(frame, lib.asSizeWithState(fd, PArguments.getThreadState(frame)));
        }

        protected static FstatNode create() {
            return PosixModuleBuiltinsFactory.FstatNodeFactory.create(null);
        }
    }

    @Builtin(name = "set_inheritable", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class SetInheritableNode extends PythonFileNode {
        @Specialization(guards = {"fd >= 0", "fd <= 2"})
        static Object setInheritableStd(@SuppressWarnings("unused") int fd, @SuppressWarnings("unused") Object inheritable) {
            // TODO: investigate if for the stdout/in/err this flag can be set
            return PNone.NONE;
        }

        @Specialization(guards = "fd > 2")
        Object setInheritable(VirtualFrame frame, int fd, @SuppressWarnings("unused") Object inheritable) {
            Channel ch = getResources().getFileChannel(fd);
            if (ch == null || ch instanceof PSocket) {
                throw raiseOSError(frame, OSErrorEnum.EBADF);
            }
            // TODO: investigate how to map this to the truffle file api (if supported)
            return PNone.NONE;
        }
    }

    @Builtin(name = "stat", minNumOfPositionalArgs = 1, parameterNames = {"path", "follow_symlinks"})
    @ArgumentClinic(name = "follow_symlinks", defaultValue = "true", conversion = ClinicConversion.Boolean)
    @GenerateNodeFactory
    @ImportStatic(SpecialMethodNames.class)
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class StatNode extends PythonBinaryClinicBuiltinNode {
        private static final int S_IFIFO = 0010000;
        private static final int S_IFCHR = 0020000;
        private static final int S_IFBLK = 0060000;
        private static final int S_IFSOCK = 0140000;
        private static final int S_IFLNK = 0120000;
        private static final int S_IFDIR = 0040000;
        private static final int S_IFREG = 0100000;

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return StatNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(limit = "2")
        Object doStatPath(VirtualFrame frame, Object path, boolean followSymlinks,
                        @CachedLibrary("path") PythonObjectLibrary lib) {
            return stat(frame, lib.asPath(path), followSymlinks);
        }

        @TruffleBoundary
        static long fileTimeToSeconds(FileTime t) {
            return t.to(TimeUnit.SECONDS);
        }

        Object stat(VirtualFrame frame, String path, boolean followSymlinks) {
            try {
                return statInternal(path, followSymlinks);
            } catch (Exception e) {
                throw raiseOSError(frame, e, path);
            }
        }

        @TruffleBoundary
        Object statInternal(String path, boolean followSymlinks) throws IOException {
            TruffleFile f = getContext().getPublicTruffleFileRelaxed(path, PythonLanguage.DEFAULT_PYTHON_EXTENSIONS);
            LinkOption[] linkOptions = followSymlinks ? new LinkOption[0] : new LinkOption[]{LinkOption.NOFOLLOW_LINKS};
            try {
                return unixStat(f, linkOptions);
            } catch (UnsupportedOperationException unsupported) {
                try {
                    return posixStat(f, linkOptions);
                } catch (UnsupportedOperationException unsupported2) {
                    return basicStat(f, linkOptions);
                }
            }
        }

        private PTuple unixStat(TruffleFile file, LinkOption... linkOptions) throws IOException {
            TruffleFile.Attributes attributes = file.getAttributes(Arrays.asList(
                            UNIX_MODE,
                            UNIX_INODE,
                            UNIX_DEV,
                            UNIX_NLINK,
                            UNIX_UID,
                            UNIX_GID,
                            SIZE,
                            LAST_ACCESS_TIME,
                            LAST_MODIFIED_TIME,
                            UNIX_CTIME), linkOptions);
            return factory().createTuple(new Object[]{
                            attributes.get(UNIX_MODE),
                            attributes.get(UNIX_INODE),
                            attributes.get(UNIX_DEV),
                            attributes.get(UNIX_NLINK),
                            attributes.get(UNIX_UID),
                            attributes.get(UNIX_GID),
                            attributes.get(SIZE),
                            fileTimeToSeconds(attributes.get(LAST_ACCESS_TIME)),
                            fileTimeToSeconds(attributes.get(LAST_MODIFIED_TIME)),
                            fileTimeToSeconds(attributes.get(UNIX_CTIME)),
            });
        }

        private PTuple posixStat(TruffleFile file, LinkOption... linkOptions) throws IOException {
            int mode = 0;
            long size = 0;
            long ctime = 0;
            long atime = 0;
            long mtime = 0;
            long gid = 0;
            long uid = 0;
            TruffleFile.Attributes attributes = file.getAttributes(Arrays.asList(
                            IS_DIRECTORY,
                            IS_SYMBOLIC_LINK,
                            IS_REGULAR_FILE,
                            LAST_MODIFIED_TIME,
                            LAST_ACCESS_TIME,
                            CREATION_TIME,
                            SIZE,
                            UNIX_OWNER,
                            UNIX_GROUP,
                            UNIX_PERMISSIONS), linkOptions);
            mode |= fileTypeBitsFromAttributes(attributes);
            mtime = fileTimeToSeconds(attributes.get(LAST_MODIFIED_TIME));
            ctime = fileTimeToSeconds(attributes.get(CREATION_TIME));
            atime = fileTimeToSeconds(attributes.get(LAST_ACCESS_TIME));
            size = attributes.get(SIZE);
            UserPrincipal owner = attributes.get(UNIX_OWNER);
            if (owner instanceof UnixNumericUserPrincipal) {
                try {
                    uid = strToLong(((UnixNumericUserPrincipal) owner).getName());
                } catch (NumberFormatException e2) {
                }
            }
            GroupPrincipal group = attributes.get(UNIX_GROUP);
            if (group instanceof UnixNumericGroupPrincipal) {
                try {
                    gid = strToLong(((UnixNumericGroupPrincipal) group).getName());
                } catch (NumberFormatException e2) {
                }
            }
            final Set<PosixFilePermission> posixFilePermissions = attributes.get(UNIX_PERMISSIONS);
            mode = posixPermissionsToMode(mode, posixFilePermissions);
            int inode = getInode(file);
            return factory().createTuple(new Object[]{
                            mode,
                            inode, // ino
                            0, // dev
                            0, // nlink
                            uid,
                            gid,
                            size,
                            atime,
                            mtime,
                            ctime,
            });
        }

        private PTuple basicStat(TruffleFile file, LinkOption... linkOptions) throws IOException {
            int mode = 0;
            long size = 0;
            long ctime = 0;
            long atime = 0;
            long mtime = 0;
            long gid = 0;
            long uid = 0;
            TruffleFile.Attributes attributes = file.getAttributes(Arrays.asList(
                            IS_DIRECTORY,
                            IS_SYMBOLIC_LINK,
                            IS_REGULAR_FILE,
                            LAST_MODIFIED_TIME,
                            LAST_ACCESS_TIME,
                            CREATION_TIME,
                            SIZE), linkOptions);
            mode |= fileTypeBitsFromAttributes(attributes);
            mtime = fileTimeToSeconds(attributes.get(LAST_MODIFIED_TIME));
            ctime = fileTimeToSeconds(attributes.get(CREATION_TIME));
            atime = fileTimeToSeconds(attributes.get(LAST_ACCESS_TIME));
            size = attributes.get(SIZE);
            if (file.isReadable()) {
                mode |= 0004;
                mode |= 0040;
                mode |= 0400;
            }
            if (file.isWritable()) {
                mode |= 0002;
                mode |= 0020;
                mode |= 0200;
            }
            if (file.isExecutable()) {
                mode |= 0001;
                mode |= 0010;
                mode |= 0100;
            }
            int inode = getInode(file);
            return factory().createTuple(new Object[]{
                            mode,
                            inode, // ino
                            0, // dev
                            0, // nlink
                            uid,
                            gid,
                            size,
                            atime,
                            mtime,
                            ctime,
            });
        }

        private static int fileTypeBitsFromAttributes(TruffleFile.Attributes attributes) {
            int mode = 0;
            if (attributes.get(IS_REGULAR_FILE)) {
                mode |= S_IFREG;
            } else if (attributes.get(IS_DIRECTORY)) {
                mode |= S_IFDIR;
            } else if (attributes.get(IS_SYMBOLIC_LINK)) {
                mode |= S_IFLNK;
            } else {
                // TODO: differentiate these
                mode |= S_IFSOCK | S_IFBLK | S_IFCHR | S_IFIFO;
            }
            return mode;
        }

        private int getInode(TruffleFile file) {
            TruffleFile canonical;
            try {
                canonical = file.getCanonicalFile();
            } catch (IOException | SecurityException e) {
                // best effort
                canonical = file.getAbsoluteFile();
            }
            return getContext().getResources().getInodeId(canonical.getPath());
        }

        @TruffleBoundary(allowInlining = true, transferToInterpreterOnException = false)
        private static long strToLong(String name) throws NumberFormatException {
            return Long.decode(name).longValue();
        }

        @TruffleBoundary(allowInlining = true)
        private static int posixPermissionsToMode(int inputMode, final Set<PosixFilePermission> posixFilePermissions) {
            int mode = inputMode;
            if (posixFilePermissions.contains(PosixFilePermission.OTHERS_READ)) {
                mode |= 0004;
            }
            if (posixFilePermissions.contains(PosixFilePermission.OTHERS_WRITE)) {
                mode |= 0002;
            }
            if (posixFilePermissions.contains(PosixFilePermission.OTHERS_EXECUTE)) {
                mode |= 0001;
            }
            if (posixFilePermissions.contains(PosixFilePermission.GROUP_READ)) {
                mode |= 0040;
            }
            if (posixFilePermissions.contains(PosixFilePermission.GROUP_WRITE)) {
                mode |= 0020;
            }
            if (posixFilePermissions.contains(PosixFilePermission.GROUP_EXECUTE)) {
                mode |= 0010;
            }
            if (posixFilePermissions.contains(PosixFilePermission.OWNER_READ)) {
                mode |= 0400;
            }
            if (posixFilePermissions.contains(PosixFilePermission.OWNER_WRITE)) {
                mode |= 0200;
            }
            if (posixFilePermissions.contains(PosixFilePermission.OWNER_EXECUTE)) {
                mode |= 0100;
            }
            return mode;
        }

        public static StatNode create() {
            return StatNodeFactory.create();
        }
    }

    @Builtin(name = "listdir", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class ListdirNode extends PythonBuiltinNode {
        @Specialization(limit = "3")
        Object listdir(VirtualFrame frame, Object pathArg,
                        @CachedLibrary("pathArg") PythonObjectLibrary lib) {
            String path = lib.asPath(pathArg);
            try {
                TruffleFile file = getContext().getPublicTruffleFileRelaxed(path, PythonLanguage.DEFAULT_PYTHON_EXTENSIONS);
                Collection<TruffleFile> listFiles = file.list();
                Object[] filenames = listToArray(listFiles);
                return factory().createList(filenames);
            } catch (Exception e) {
                throw raiseOSError(frame, e, path);
            }
        }

        @TruffleBoundary(allowInlining = true, transferToInterpreterOnException = false)
        private static Object[] listToArray(Collection<TruffleFile> listFiles) {
            Object[] filenames = new Object[listFiles.size()];
            int i = 0;
            for (TruffleFile f : listFiles) {
                filenames[i] = f.getName();
                i += 1;
            }
            return filenames;
        }
    }

    @Builtin(name = "ScandirIterator", minNumOfPositionalArgs = 2, constructsClass = PythonBuiltinClassType.PScandirIterator, isPublic = true)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class ScandirIterNode extends PythonBinaryBuiltinNode {

        @Specialization(limit = "1")
        Object doit(VirtualFrame frame, Object cls, Object pathArg,
                        @CachedLibrary("pathArg") PythonObjectLibrary lib) {
            String path = lib.asPath(pathArg);
            try {
                TruffleFile file = getContext().getEnv().getPublicTruffleFile(path);
                return factory().createScandirIterator(cls, path, file.newDirectoryStream(), PGuards.isBytes(pathArg));
            } catch (Exception e) {
                throw raiseOSError(frame, e, path);
            }
        }
    }

    @Builtin(name = "DirEntry", minNumOfPositionalArgs = 3, constructsClass = PythonBuiltinClassType.PDirEntry, isPublic = true)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class DirEntryNode extends PythonTernaryBuiltinNode {

        @Specialization(limit = "1")
        Object doit(VirtualFrame frame, Object cls, String name, Object pathArg,
                        @CachedLibrary("pathArg") PythonObjectLibrary lib) {
            String path = lib.asPath(pathArg);
            try {
                TruffleFile dir = getContext().getEnv().getPublicTruffleFile(path);
                TruffleFile file = dir.resolve(name);
                return factory().createDirEntry(cls, name, file);
            } catch (Exception e) {
                throw raiseOSError(frame, e, path);
            }
        }
    }

    @Builtin(name = "dup", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class DupNode extends PythonFileNode {
        @Specialization
        int dupInt(int fd) {
            return getResources().dup(fd);
        }

        @Specialization(replaces = "dupInt")
        int dupGeneric(Object fd,
                        @Cached CastToJavaIntExactNode castToJavaIntNode) {
            return getResources().dup(castToJavaIntNode.execute(fd));
        }
    }

    @Builtin(name = "dup2", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class Dup2Node extends PythonFileNode {
        @Specialization
        int dup(VirtualFrame frame, int fd, int fd2) {
            try {
                return getResources().dup2(fd, fd2);
            } catch (IOException e) {
                throw raiseOSError(frame, OSErrorEnum.EBADF);
            }
        }

        @Specialization(rewriteOn = OverflowException.class)
        int dupPInt(VirtualFrame frame, PInt fd, PInt fd2) throws OverflowException {
            try {
                return getResources().dup2(fd.intValueExact(), fd2.intValueExact());
            } catch (IOException e) {
                throw raiseOSError(frame, OSErrorEnum.EBADF);
            }
        }

        @Specialization(replaces = "dupPInt")
        int dupOvf(VirtualFrame frame, PInt fd, PInt fd2) {
            try {
                return dupPInt(frame, fd, fd2);
            } catch (OverflowException e) {
                throw raiseOSError(frame, OSErrorEnum.EBADF);
            }
        }
    }

    @Builtin(name = "open", minNumOfPositionalArgs = 2, parameterNames = {"pathname", "flags", "mode", "dir_fd"})
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class OpenNode extends PythonFileNode {

        @Specialization(guards = {"isNoValue(mode)", "isNoValue(dir_fd)"}, limit = "3")
        Object open(VirtualFrame frame, Object pathname, long flags, @SuppressWarnings("unused") PNone mode, PNone dir_fd,
                        @CachedLibrary("pathname") PythonObjectLibrary lib) {
            return openMode(frame, pathname, flags, 0777, dir_fd, lib);
        }

        @Specialization(guards = {"isNoValue(dir_fd)"}, limit = "3")
        Object openMode(VirtualFrame frame, Object pathArg, long flags, long fileMode, @SuppressWarnings("unused") PNone dir_fd,
                        @CachedLibrary("pathArg") PythonObjectLibrary lib) {
            String pathname = lib.asPath(pathArg);
            Set<StandardOpenOption> options = flagsToOptions((int) flags);
            FileAttribute<Set<PosixFilePermission>> attributes = modeToAttributes((int) fileMode);
            try {
                return doOpenFile(pathname, options, attributes);
            } catch (Exception e) {
                throw raiseOSError(frame, e, pathname);
            }
        }

        @TruffleBoundary
        private Object doOpenFile(String pathname, Set<StandardOpenOption> options, FileAttribute<Set<PosixFilePermission>> attributes) throws IOException {
            SeekableByteChannel fc;
            TruffleFile truffleFile = getContext().getPublicTruffleFileRelaxed(pathname, PythonLanguage.DEFAULT_PYTHON_EXTENSIONS);
            if (options.contains(StandardOpenOption.DELETE_ON_CLOSE)) {
                truffleFile = getContext().getEnv().createTempFile(truffleFile, null, null);
                options.remove(StandardOpenOption.CREATE_NEW);
                options.remove(StandardOpenOption.DELETE_ON_CLOSE);
                options.add(StandardOpenOption.CREATE);
                getContext().registerShutdownHook(new FileDeleteShutdownHook(truffleFile));
            }

            fc = truffleFile.newByteChannel(options, attributes);
            return getResources().open(truffleFile, fc);
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        @TruffleBoundary(allowInlining = true)
        private static FileAttribute<Set<PosixFilePermission>> modeToAttributes(int fileMode) {
            HashSet<PosixFilePermission> perms = new HashSet<>(Arrays.asList(ownerBitsToPermission[fileMode >> 6 & 7]));
            perms.addAll(Arrays.asList(groupBitsToPermission[fileMode >> 3 & 7]));
            perms.addAll(Arrays.asList(otherBitsToPermission[fileMode & 7]));
            return PosixFilePermissions.asFileAttribute(perms);
        }

        @TruffleBoundary(allowInlining = true)
        private static Set<StandardOpenOption> flagsToOptions(int flags) {
            Set<StandardOpenOption> options = new HashSet<>();
            if ((flags & WRONLY) != 0) {
                options.add(StandardOpenOption.WRITE);
            } else if ((flags & RDWR) != 0) {
                options.add(StandardOpenOption.READ);
                options.add(StandardOpenOption.WRITE);
            } else {
                options.add(StandardOpenOption.READ);
            }
            if ((flags & CREAT) != 0) {
                options.add(StandardOpenOption.WRITE);
                options.add(StandardOpenOption.CREATE);
            }
            if ((flags & EXCL) != 0) {
                options.add(StandardOpenOption.WRITE);
                options.add(StandardOpenOption.CREATE_NEW);
            }
            if ((flags & APPEND) != 0) {
                options.add(StandardOpenOption.WRITE);
                options.add(StandardOpenOption.APPEND);
            }
            if ((flags & NDELAY) != 0 || (flags & DIRECT) != 0) {
                options.add(StandardOpenOption.DSYNC);
            }
            if ((flags & SYNC) != 0) {
                options.add(StandardOpenOption.SYNC);
            }
            if ((flags & TRUNC) != 0) {
                options.add(StandardOpenOption.WRITE);
                options.add(StandardOpenOption.TRUNCATE_EXISTING);
            }
            if ((flags & TMPFILE) != 0) {
                options.add(StandardOpenOption.DELETE_ON_CLOSE);
            }
            return options;
        }
    }

    @Builtin(name = "nfi_open", minNumOfPositionalArgs = 2, parameterNames = {"path", "flags", "mode"}, keywordOnlyNames = {"dir_fd"})
    @ArgumentClinic(name = "path", conversionClass = PathConversionNode.class, args = {"false", "false"})
    @ArgumentClinic(name = "flags", conversion = ClinicConversion.Int, defaultValue = "-1")
    @ArgumentClinic(name = "mode", conversion = ClinicConversion.Int, defaultValue = "0777")
    @ArgumentClinic(name = "dir_fd", conversionClass = DirFdConversionNode.class)
    @GenerateNodeFactory
    abstract static class NfiOpenNode extends PythonQuaternaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.NfiOpenNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        Object open(VirtualFrame frame, PosixPath path, int flags, int mode, int dirFd,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached SysModuleBuiltins.AuditNode auditNode) {
            int fixedFlags = flags | CLOEXEC;
            auditNode.audit("open", path.originalObject, PNone.NONE, fixedFlags);
            try {
                return posixLib.openAt(getPosixSupport(), dirFd, path, fixedFlags, mode);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            }
        }
    }

    @Builtin(name = "nfi_close", minNumOfPositionalArgs = 1, parameterNames = {"fd"})
    @ArgumentClinic(name = "fd", conversion = ClinicConversion.Int, defaultValue = "-1")
    @GenerateNodeFactory
    abstract static class NfiCloseNode extends PythonUnaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.NfiCloseNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        Object close(VirtualFrame frame, int fd,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            try {
                posixLib.close(getPosixSupport(), fd);
                return PNone.NONE;
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            }
        }
    }

    @Builtin(name = "nfi_read", minNumOfPositionalArgs = 2, parameterNames = {"fd", "length"})
    @ArgumentClinic(name = "fd", conversion = ClinicConversion.Int, defaultValue = "-1")
    @ArgumentClinic(name = "length", conversion = ClinicConversion.Index, defaultValue = "-1")
    @GenerateNodeFactory
    abstract static class NfiReadNode extends PythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.NfiReadNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        Object read(VirtualFrame frame, int fd, int length,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            if (length < 0) {
                int error = OSErrorEnum.EINVAL.getNumber();
                throw raiseOSError(frame, error, posixLib.strerror(getPosixSupport(), error));
            }
            try {
                Buffer result = posixLib.read(getPosixSupport(), fd, length);
                if (result.length > Integer.MAX_VALUE) {
                    // sanity check that it is safe to cast result.length to int, to be removed once
                    // we support large arrays
                    throw CompilerDirectives.shouldNotReachHere("Posix read() returned more bytes than requested");
                }
                return factory().createBytes(result.data, 0, (int) result.length);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            }
        }
    }

    @Builtin(name = "nfi_write", minNumOfPositionalArgs = 2, parameterNames = {"fd", "data"})
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
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            try {
                return posixLib.write(getPosixSupport(), fd, Buffer.wrap(data));
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            }
        }
    }

    @Builtin(name = "nfi_dup", minNumOfPositionalArgs = 1, parameterNames = {"fd"})
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

    @Builtin(name = "nfi_dup2", minNumOfPositionalArgs = 2, parameterNames = {"fd", "fd2", "inheritable"})
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

    @Builtin(name = "nfi_get_inheritable", minNumOfPositionalArgs = 1, parameterNames = {"fd"})
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

    @Builtin(name = "nfi_set_inheritable", minNumOfPositionalArgs = 2, parameterNames = {"fd", "inheritable"})
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

    @Builtin(name = "nfi_pipe", minNumOfPositionalArgs = 0)
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

    @Builtin(name = "nfi_lseek", minNumOfPositionalArgs = 3, parameterNames = {"fd", "pos", "how"})
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

    @Builtin(name = "nfi_ftruncate", minNumOfPositionalArgs = 2, parameterNames = {"fd", "length"})
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
                        @Cached SysModuleBuiltins.AuditNode auditNode) {
            auditNode.audit("os.truncate", fd, length);
            try {
                posixLib.ftruncate(getPosixSupport(), fd, length);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "nfi_fsync", minNumOfPositionalArgs = 1, parameterNames = "fd")
    @ArgumentClinic(name = "fd", conversionClass = FileDescriptorConversionNode.class)
    @GenerateNodeFactory
    abstract static class NfiFSyncNode extends PythonUnaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.NfiFSyncNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        PNone fsync(VirtualFrame frame, int fd,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            try {
                posixLib.fsync(getPosixSupport(), fd);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "nfi_strerror", minNumOfPositionalArgs = 1, parameterNames = {"code"})
    @ArgumentClinic(name = "code", conversion = ClinicConversion.Int, defaultValue = "-1")
    @GenerateNodeFactory
    public abstract static class NfiStrErrorNode extends PythonUnaryClinicBuiltinNode {

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

    @Builtin(name = "lseek", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class LseekNode extends PythonFileNode {
        private final BranchProfile gotException = BranchProfile.create();
        private final ConditionProfile noFile = ConditionProfile.createBinaryProfile();

        @Specialization
        Object lseek(VirtualFrame frame, long fd, long pos, int how,
                        @Shared("channelClassProfile") @Cached("createClassProfile()") ValueProfile channelClassProfile) {
            Channel channel = getResources().getFileChannel((int) fd, channelClassProfile);
            if (noFile.profile(!(channel instanceof SeekableByteChannel))) {
                throw raiseOSError(frame, OSErrorEnum.ESPIPE);
            }
            SeekableByteChannel fc = (SeekableByteChannel) channel;
            try {
                return setPosition(pos, how, fc);
            } catch (Exception e) {
                gotException.enter();
                throw raiseOSError(frame, e);
            }
        }

        @Specialization(limit = "1")
        Object lseekGeneric(VirtualFrame frame, Object fd, Object pos, Object how,
                        @Shared("channelClassProfile") @Cached("createClassProfile()") ValueProfile channelClassProfile,
                        @CachedLibrary("fd") PythonObjectLibrary libFd,
                        @CachedLibrary("pos") PythonObjectLibrary libPos,
                        @Cached CastToJavaIntExactNode castHowNode) {

            return lseek(frame, libFd.asJavaLong(fd), libPos.asJavaLong(pos), castHowNode.execute(how), channelClassProfile);
        }

        @TruffleBoundary(allowInlining = true)
        private static Object setPosition(long pos, int how, SeekableByteChannel fc) throws IOException {
            switch (how) {
                case SEEK_CUR:
                    fc.position(fc.position() + pos);
                    break;
                case SEEK_END:
                    fc.position(fc.size() + pos);
                    break;
                case SEEK_SET:
                    fc.position(pos);
                    break;
                default:
                    throw new IllegalArgumentException();
            }
            return fc.position();
        }
    }

    @Builtin(name = "close", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class CloseNode extends PythonFileNode {
        private final ConditionProfile noFile = ConditionProfile.createBinaryProfile();

        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()")
        Object close(VirtualFrame frame, Object fdObject,
                        @CachedLibrary("fdObject") PythonObjectLibrary lib,
                        @Cached("createClassProfile()") ValueProfile channelClassProfile) {
            int fd = lib.asSizeWithState(fdObject, PArguments.getThreadState(frame));
            PosixResources resources = getResources();
            Channel channel = resources.getFileChannel(fd, channelClassProfile);
            if (noFile.profile(channel == null)) {
                throw raiseOSError(frame, OSErrorEnum.EBADF);
            } else {
                resources.close(fd);
            }
            return PNone.NONE;
        }

        @TruffleBoundary(allowInlining = true, transferToInterpreterOnException = false)
        private static void closeChannel(Channel channel) throws IOException {
            channel.close();
        }
    }

    @Builtin(name = "unlink", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class UnlinkNode extends PythonFileNode {

        @Specialization(limit = "3")
        Object unlink(VirtualFrame frame, Object pathArg,
                        @CachedLibrary("pathArg") PythonObjectLibrary lib) {
            String path = lib.asPath(pathArg);
            try {
                getContext().getEnv().getPublicTruffleFile(path).delete();
            } catch (Exception e) {
                throw raiseOSError(frame, e, path);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "remove", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class RemoveNode extends UnlinkNode {
    }

    @Builtin(name = "rmdir", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class RmdirNode extends UnlinkNode {
    }

    @Builtin(name = "mkdir", minNumOfPositionalArgs = 1, parameterNames = {"path", "mode", "dir_fd"})
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class MkdirNode extends PythonFileNode {

        @Specialization(limit = "3")
        Object mkdir(VirtualFrame frame, Object path, @SuppressWarnings("unused") PNone mode, PNone dirFd,
                        @CachedLibrary("path") PythonObjectLibrary lib) {
            return mkdirMode(frame, path, 511, dirFd, lib);
        }

        @Specialization(limit = "3")
        Object mkdirMode(VirtualFrame frame, Object pathArg, @SuppressWarnings("unused") int mode, @SuppressWarnings("unused") PNone dirFd,
                        @CachedLibrary("pathArg") PythonObjectLibrary lib) {
            String path = lib.asPath(pathArg);
            try {
                getContext().getEnv().getPublicTruffleFile(path).createDirectory();
            } catch (Exception e) {
                throw raiseOSError(frame, e, path);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "write", minNumOfPositionalArgs = 2, parameterNames = {"fd", "str"})
    @ArgumentClinic(name = "fd", conversion = ClinicConversion.Int, defaultValue = "-1")
    @ArgumentClinic(name = "str", conversion = ClinicConversion.Buffer)
    @GenerateNodeFactory
    public abstract static class WriteNode extends PythonBinaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PosixModuleBuiltinsClinicProviders.WriteNodeClinicProviderGen.INSTANCE;
        }

        @TruffleBoundary(allowInlining = true, transferToInterpreterOnException = false)
        private static int doWriteOp(byte[] data, WritableByteChannel channel) throws IOException {
            return channel.write(ByteBuffer.wrap(data));
        }

        @Specialization
        Object write(VirtualFrame frame, int fd, byte[] data,
                        @Cached("createClassProfile()") ValueProfile channelClassProfile,
                        @Cached BranchProfile gotExceptionProfile,
                        @Cached BranchProfile nonWritableChannelProfile) {
            Channel channel = getContext().getResources().getFileChannel(fd, channelClassProfile);
            if (!(channel instanceof WritableByteChannel)) {
                nonWritableChannelProfile.enter();
                throw raiseOSError(frame, OSErrorEnum.EBADF);
            }
            try {
                return doWriteOp(data, (WritableByteChannel) channel);
            } catch (Exception e) {
                gotExceptionProfile.enter();
                throw raiseOSError(frame, e);
            }
        }
    }

    @Builtin(name = "read", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class ReadNode extends PythonFileNode {

        @CompilationFinal private BranchProfile tooLargeProfile = BranchProfile.create();

        @Specialization
        Object readLong(@SuppressWarnings("unused") VirtualFrame frame, int fd, long requestedSize,
                        @Shared("profile") @Cached("createClassProfile()") ValueProfile channelClassProfile,
                        @Shared("readNode") @Cached ReadFromChannelNode readNode) {
            int size = (int) requestedSize;
            if (size != requestedSize) {
                tooLargeProfile.enter();
                size = ReadFromChannelNode.MAX_READ;
            }
            Channel channel = getResources().getFileChannel(fd, channelClassProfile);
            ByteSequenceStorage array = readNode.execute(channel, size);
            return factory().createBytes(array);
        }

        @Specialization(limit = "1")
        Object read(@SuppressWarnings("unused") VirtualFrame frame, int fd, Object requestedSize,
                        @Shared("profile") @Cached("createClassProfile()") ValueProfile channelClassProfile,
                        @Shared("readNode") @Cached ReadFromChannelNode readNode,
                        @CachedLibrary("requestedSize") PythonObjectLibrary libSize) {
            return readLong(frame, fd, libSize.asJavaLong(requestedSize), channelClassProfile, readNode);
        }

        @Specialization(limit = "1")
        Object readFdGeneric(@SuppressWarnings("unused") VirtualFrame frame, Object fd, Object requestedSize,
                        @Shared("profile") @Cached("createClassProfile()") ValueProfile channelClassProfile,
                        @Shared("readNode") @Cached ReadFromChannelNode readNode,
                        @CachedLibrary("requestedSize") PythonObjectLibrary libSize,
                        @Cached CastToJavaIntExactNode castToIntNode) {
            return readLong(frame, castToIntNode.execute(fd), libSize.asJavaLong(requestedSize), channelClassProfile, readNode);
        }
    }

    @Builtin(name = "isatty", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class IsATTYNode extends PythonBuiltinNode {
        @Specialization
        boolean isATTY(long fd) {
            if (fd >= 0 && fd <= 2) {
                return terminalIsInteractive(getContext());
            } else {
                return false;
            }
        }

        @Fallback
        static boolean isATTY(@SuppressWarnings("unused") Object fd) {
            return false;
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

    @Builtin(name = "chmod", minNumOfPositionalArgs = 2, parameterNames = {"path", "mode", "dir_fd", "follow_symlinks"})
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class ChmodNode extends PythonBuiltinNode {
        private final BranchProfile gotException = BranchProfile.create();

        @Specialization(limit = "1")
        Object chmod(VirtualFrame frame, Object path, long mode, @SuppressWarnings("unused") PNone dir_fd, @SuppressWarnings("unused") PNone follow_symlinks,
                        @CachedLibrary("path") PythonObjectLibrary lib) {
            return chmodFollow(frame, path, mode, dir_fd, true, lib);
        }

        @Specialization(limit = "1")
        Object chmodFollow(VirtualFrame frame, Object pathArg, long mode, @SuppressWarnings("unused") PNone dir_fd, boolean follow_symlinks,
                        @CachedLibrary("pathArg") PythonObjectLibrary lib) {
            String path = lib.asPath(pathArg);
            Set<PosixFilePermission> permissions = modeToPermissions(mode);
            try {
                TruffleFile truffleFile = getContext().getEnv().getPublicTruffleFile(path);
                if (!follow_symlinks) {
                    truffleFile = truffleFile.getCanonicalFile(LinkOption.NOFOLLOW_LINKS);
                } else {
                    truffleFile = truffleFile.getCanonicalFile();
                }
                truffleFile.setPosixPermissions(permissions);
            } catch (Exception e) {
                gotException.enter();
                throw raiseOSError(frame, e, path);
            }
            return PNone.NONE;
        }

        @TruffleBoundary(allowInlining = true)
        private static Set<PosixFilePermission> modeToPermissions(long mode) {
            Set<PosixFilePermission> permissions = new HashSet<>(Arrays.asList(otherBitsToPermission[(int) (mode & 7)]));
            permissions.addAll(Arrays.asList(groupBitsToPermission[(int) (mode >> 3 & 7)]));
            permissions.addAll(Arrays.asList(ownerBitsToPermission[(int) (mode >> 6 & 7)]));
            return permissions;
        }
    }

    @Builtin(name = "utime", minNumOfPositionalArgs = 1, parameterNames = {"path", "times", "ns", "dir_fd", "follow_symlinks"})
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class UtimeNode extends PythonBuiltinNode {
        @Child private GetItemNode getItemNode;
        @Child private LenNode lenNode;

        @SuppressWarnings("unused")
        @Specialization(limit = "1")
        Object utime(VirtualFrame frame, Object path, PNone times, PNone ns, PNone dir_fd, PNone follow_symlinks,
                        @CachedLibrary("path") PythonObjectLibrary lib) {
            long time = ((Double) TimeModuleBuiltins.timeSeconds()).longValue();
            TruffleFile file = getFile(frame, lib.asPath(path), true);
            setMtime(frame, file, time);
            setAtime(frame, file, time);
            return PNone.NONE;
        }

        @SuppressWarnings("unused")
        @Specialization(limit = "1")
        Object utime(VirtualFrame frame, Object path, PTuple times, PNone ns, PNone dir_fd, PNone follow_symlinks,
                        @CachedLibrary("path") PythonObjectLibrary lib) {
            long atime = getTime(frame, times, 0, "times");
            long mtime = getTime(frame, times, 1, "times");
            TruffleFile file = getFile(frame, lib.asPath(path), true);
            setMtime(frame, file, mtime);
            setAtime(frame, file, atime);
            return PNone.NONE;
        }

        @SuppressWarnings("unused")
        @Specialization(limit = "1")
        Object utime(VirtualFrame frame, Object path, PNone times, PTuple ns, PNone dir_fd, PNone follow_symlinks,
                        @CachedLibrary("path") PythonObjectLibrary lib) {
            long atime = getTime(frame, ns, 0, "ns") / 1000;
            long mtime = getTime(frame, ns, 1, "ns") / 1000;
            TruffleFile file = getFile(frame, lib.asPath(path), true);
            setMtime(frame, file, mtime);
            setAtime(frame, file, atime);
            return PNone.NONE;
        }

        @SuppressWarnings("unused")
        @Specialization(limit = "1")
        Object utime(VirtualFrame frame, Object path, PNone times, PTuple ns, PNone dir_fd, boolean follow_symlinks,
                        @CachedLibrary("path") PythonObjectLibrary lib) {
            long atime = getTime(frame, ns, 0, "ns") / 1000;
            long mtime = getTime(frame, ns, 1, "ns") / 1000;
            TruffleFile file = getFile(frame, lib.asPath(path), true);
            setMtime(frame, file, mtime);
            setAtime(frame, file, atime);
            return PNone.NONE;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!isPNone(times)", "!isPTuple(times)"})
        Object utimeWrongTimes(VirtualFrame frame, Object path, Object times, Object ns, Object dir_fd, Object follow_symlinks) {
            throw tupleError("times");
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!isPTuple(ns)", "!isPNone(ns)"})
        Object utimeWrongNs(VirtualFrame frame, Object path, PNone times, Object ns, Object dir_fd, Object follow_symlinks) {
            throw tupleError("ns");
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!isPNone(ns)"})
        Object utimeWrongNs(VirtualFrame frame, Object path, PTuple times, Object ns, Object dir_fd, Object follow_symlinks) {
            throw raise(ValueError, ErrorMessages.YOU_MAY_SPECIFY_EITHER_OR_BUT_NOT_BOTH, "utime", "times", "ns");
        }

        @SuppressWarnings("unused")
        @Fallback
        Object utimeError(VirtualFrame frame, Object path, Object times, Object ns, Object dir_fd, Object follow_symlinks) {
            throw raise(NotImplementedError, "utime");
        }

        private long getTime(VirtualFrame frame, PTuple times, int index, String argname) {
            if (getLength(times) <= index) {
                throw tupleError(argname);
            }
            if (getItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getItemNode = insert(GetItemNode.createNotNormalized());
            }
            Object mtimeObj = getItemNode.execute(frame, times.getSequenceStorage(), index);
            long mtime;
            if (mtimeObj instanceof Integer) {
                mtime = ((Integer) mtimeObj).longValue();
            } else if (mtimeObj instanceof Long) {
                mtime = ((Long) mtimeObj).longValue();
            } else if (mtimeObj instanceof PInt) {
                mtime = ((PInt) mtimeObj).longValue();
            } else if (mtimeObj instanceof Double) {
                mtime = ((Double) mtimeObj).longValue();
            } else if (mtimeObj instanceof PFloat) {
                mtime = (long) ((PFloat) mtimeObj).getValue();
            } else {
                throw tupleError(argname);
            }
            if (mtime < 0) {
                throw raise(ValueError, ErrorMessages.CANNOT_BE_NEGATIVE, "time");
            }
            return mtime;
        }

        private PException tupleError(String argname) {
            return raise(TypeError, ErrorMessages.MUST_BE_EITHER_OR, "utime", argname, "a tuple of two ints", "None");
        }

        private void setMtime(VirtualFrame frame, TruffleFile truffleFile, long mtime) {
            try {
                truffleFile.setLastModifiedTime(fileTimeFrom(mtime));
            } catch (Exception e) {
                throw raiseOSError(frame, e, truffleFile.getName());
            }
        }

        @TruffleBoundary
        private static FileTime fileTimeFrom(long mtime) {
            return FileTime.from(mtime, TimeUnit.SECONDS);
        }

        private void setAtime(VirtualFrame frame, TruffleFile truffleFile, long mtime) {
            try {
                truffleFile.setLastAccessTime(fileTimeFrom(mtime));
            } catch (Exception e) {
                throw raiseOSError(frame, e, truffleFile.getName());
            }
        }

        private TruffleFile getFile(VirtualFrame frame, String path, boolean followSymlinks) {
            TruffleFile truffleFile = getContext().getEnv().getPublicTruffleFile(path);
            if (!followSymlinks) {
                try {
                    truffleFile = truffleFile.getCanonicalFile(LinkOption.NOFOLLOW_LINKS);
                } catch (Exception e) {
                    throw raiseOSError(frame, e, truffleFile.getName());
                }
            }
            return truffleFile;
        }

        private int getLength(PTuple times) {
            if (lenNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lenNode = insert(SequenceNodes.LenNode.create());
            }
            return lenNode.execute(times);
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
                    int exitStatus = getResources().exitStatus(pid);
                    if (exitStatus == Integer.MIN_VALUE) {
                        // not terminated, yet, we should return 0
                        return factory().createTuple(new Object[]{0, 0});
                    } else {
                        return factory().createTuple(new Object[]{pid, exitStatus});
                    }
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

    @Builtin(name = "pipe", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class PipeNode extends PythonFileNode {

        @Specialization
        PTuple pipe(VirtualFrame frame) {
            int[] pipe;
            try {
                pipe = getResources().pipe();
            } catch (Exception e) {
                throw raiseOSError(frame, e);
            }
            return factory().createTuple(new Object[]{pipe[0], pipe[1]});
        }
    }

    @Builtin(name = "rename", minNumOfPositionalArgs = 2, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class RenameNode extends PythonFileNode {
        @Specialization(limit = "1")
        Object rename(VirtualFrame frame, Object src, Object dst, @SuppressWarnings("unused") Object[] args, @SuppressWarnings("unused") PNone kwargs,
                        @CachedLibrary("src") PythonObjectLibrary libSrc,
                        @CachedLibrary("dst") PythonObjectLibrary libDst) {
            return rename(frame, libSrc.asPath(src), libDst.asPath(dst));
        }

        @Specialization
        Object rename(VirtualFrame frame, Object src, Object dst, @SuppressWarnings("unused") Object[] args, PKeyword[] kwargs,
                        @CachedLibrary(limit = "1") PythonObjectLibrary libSrc,
                        @CachedLibrary(limit = "1") PythonObjectLibrary libDst) {

            Object effectiveSrc = src;
            Object effectiveDst = dst;
            PosixResources resources = getResources();
            for (int i = 0; i < kwargs.length; i++) {
                Object value = kwargs[i].getValue();
                if ("src_dir_fd".equals(kwargs[i].getName())) {
                    if (!(value instanceof Integer)) {
                        throw raiseOSError(frame, OSErrorEnum.EBADF);
                    }
                    effectiveSrc = resources.getFilePath((int) value);
                } else if ("dst_dir_fd".equals(kwargs[i].getName())) {
                    if (!(value instanceof Integer)) {
                        throw raiseOSError(frame, OSErrorEnum.EBADF);
                    }
                    effectiveDst = resources.getFilePath((int) value);
                }
            }
            return rename(frame, libSrc.asPath(effectiveSrc), libDst.asPath(effectiveDst));
        }

        private Object rename(VirtualFrame frame, String src, String dst) {
            try {
                TruffleFile dstFile = getContext().getEnv().getPublicTruffleFile(dst);
                if (dstFile.isDirectory()) {
                    throw raiseOSError(frame, OSErrorEnum.EISDIR);
                }
                TruffleFile file = getContext().getEnv().getPublicTruffleFile(src);
                file.move(dstFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                return PNone.NONE;
            } catch (Exception e) {
                throw raiseOSError(frame, e, src, dst);
            }
        }
    }

    @Builtin(name = "replace", minNumOfPositionalArgs = 2, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class ReplaceNode extends RenameNode {
    }

    @Builtin(name = "urandom", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class URandomNode extends PythonBuiltinNode {
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

        @Fallback
        Object urandomError(Object size) {
            throw raise(TypeError, ErrorMessages.ARG_EXPECTED_GOT, "integer", size);
        }
    }

    @Builtin(name = "uname", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class UnameNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary(allowInlining = true)
        PTuple uname() {
            String sysname = SysModuleBuiltins.getPythonOSName();
            String nodename = "";
            try {
                InetAddress addr;
                addr = InetAddress.getLocalHost();
                nodename = addr.getHostName();
            } catch (UnknownHostException | SecurityException ex) {
            }
            String release = System.getProperty("os.version", "");
            String version = "";
            String machine = SysModuleBuiltins.getPythonArch();
            return factory().createTuple(new Object[]{sysname, nodename, release, version, machine});
        }
    }

    @Builtin(name = "access", minNumOfPositionalArgs = 2, varArgsMarker = true, keywordOnlyNames = {"dir_fd", "effective_ids", "follow_symlinks"})
    @GenerateNodeFactory
    public abstract static class AccessNode extends PythonBuiltinNode {

        private final BranchProfile notImplementedBranch = BranchProfile.create();

        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()")
        boolean doGeneric(VirtualFrame frame, Object path, Object mode, @SuppressWarnings("unused") PNone dir_fd, @SuppressWarnings("unused") PNone effective_ids,
                        @SuppressWarnings("unused") PNone follow_symlinks,
                        @CachedLibrary("mode") PythonObjectLibrary libMode,
                        @CachedLibrary("path") PythonObjectLibrary libPath) {
            return access(libPath.asPath(path), libMode.asSizeWithState(mode, PArguments.getThreadState(frame)), PNone.NONE, false, true);
        }

        @Specialization
        boolean access(String path, int mode, Object dirFd, boolean effectiveIds, boolean followSymlinks) {
            if (dirFd != PNone.NONE || effectiveIds) {
                // TODO implement
                notImplementedBranch.enter();
                throw raise(NotImplementedError);
            }
            TruffleFile f = getContext().getEnv().getPublicTruffleFile(path);
            LinkOption[] linkOptions = followSymlinks ? new LinkOption[0] : new LinkOption[]{LinkOption.NOFOLLOW_LINKS};
            if (!f.exists(linkOptions)) {
                return false;
            }

            boolean result = true;
            if ((mode & X_OK) != 0) {
                result = result && f.isExecutable();
            }
            if ((mode & R_OK) != 0) {
                result = result && f.isReadable();
            }
            if ((mode & W_OK) != 0) {
                result = result && f.isWritable();
            }
            return result;
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

    @Builtin(name = "umask", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class UmaskNode extends PythonBuiltinNode {

        @Specialization
        long umask(long mask,   // TODO handle arg types properly (extend arg clinic to support
                                // pid_t, fd etc.)
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            return posixLib.umask(getPosixSupport(), mask);
        }
    }

    @Builtin(name = "get_terminal_size", maxNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class GetTerminalSizeNode extends PythonUnaryBuiltinNode {

        @Child private PythonObjectLibrary asPIntLib;
        @Child private GetTerminalSizeNode recursiveNode;

        @CompilationFinal private ConditionProfile errorProfile;
        @CompilationFinal private ConditionProfile overflowProfile;

        private PythonObjectLibrary getAsPIntLibrary() {
            if (asPIntLib == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                asPIntLib = insert(PythonObjectLibrary.getFactory().createDispatched(1));
            }
            return asPIntLib;
        }

        private ConditionProfile getErrorProfile() {
            if (errorProfile == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                errorProfile = ConditionProfile.createBinaryProfile();
            }
            return errorProfile;
        }

        private ConditionProfile getOverflowProfile() {
            if (overflowProfile == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                overflowProfile = ConditionProfile.createBinaryProfile();
            }
            return overflowProfile;
        }

        @Specialization(guards = "isNone(fd)")
        PTuple getTerminalSize(VirtualFrame frame, @SuppressWarnings("unused") PNone fd) {
            if (getErrorProfile().profile(getContext().getResources().getFileChannel(0) == null)) {
                throw raiseOSError(frame, OSErrorEnum.EBADF);
            }
            return factory().createTuple(new Object[]{getTerminalWidth(), getTerminalHeight()});
        }

        @Specialization
        PTuple getTerminalSize(VirtualFrame frame, int fd) {
            if (getErrorProfile().profile(getContext().getResources().getFileChannel(fd) == null)) {
                throw raiseOSError(frame, OSErrorEnum.EBADF);
            }
            return factory().createTuple(new Object[]{getTerminalWidth(), getTerminalHeight()});
        }

        @Specialization
        PTuple getTerminalSize(VirtualFrame frame, long fd) {
            if (getOverflowProfile().profile(Integer.MIN_VALUE > fd || fd > Integer.MAX_VALUE)) {
                raise(PythonErrorType.OverflowError, ErrorMessages.PYTHON_INT_TOO_LARGE_TO_CONV_TO, "C long");
            }
            if (getErrorProfile().profile(getContext().getResources().getFileChannel((int) fd) == null)) {
                throw raiseOSError(frame, OSErrorEnum.EBADF);
            }
            return factory().createTuple(new Object[]{getTerminalWidth(), getTerminalHeight()});
        }

        @Specialization
        PTuple getTerminalSize(VirtualFrame frame, PInt fd) {
            int value;
            try {
                value = fd.intValueExact();
                if (getContext().getResources().getFileChannel(value) == null) {
                    throw raiseOSError(frame, OSErrorEnum.EBADF);
                }
            } catch (OverflowException e) {
                throw raise(PythonErrorType.OverflowError, ErrorMessages.PYTHON_INT_TOO_LARGE_TO_CONV_TO, "C long");
            }
            return factory().createTuple(new Object[]{getTerminalWidth(), getTerminalHeight()});
        }

        private int getTerminalWidth() {
            return getContext().getOption(PythonOptions.TerminalWidth);
        }

        private int getTerminalHeight() {
            return getContext().getOption(PythonOptions.TerminalHeight);
        }

        @Fallback
        Object getTerminalSize(VirtualFrame frame, Object fd) {
            PythonObjectLibrary lib = getAsPIntLibrary();
            if (!lib.canBePInt(fd)) {
                throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.INTEGER_REQUIRED_GOT, fd);
            }
            Object value = lib.asPInt(fd);
            if (recursiveNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                recursiveNode = create();
            }
            return recursiveNode.call(frame, value);
        }

        protected static GetTerminalSizeNode create() {
            return PosixModuleBuiltinsFactory.GetTerminalSizeNodeFactory.create();
        }
    }

    @Builtin(name = "readlink", minNumOfPositionalArgs = 1, parameterNames = {"path"}, varArgsMarker = true, keywordOnlyNames = {"dirFd"}, doc = "readlink(path, *, dir_fd=None) -> path\n" +
                    "\nReturn a string representing the path to which the symbolic link points.\n")
    @GenerateNodeFactory
    abstract static class ReadlinkNode extends PythonBinaryBuiltinNode {
        @Specialization(limit = "1")
        String readlink(VirtualFrame frame, Object str, @SuppressWarnings("unused") PNone none,
                        @CachedLibrary("str") PythonObjectLibrary lib) {
            String path = lib.asPath(str);
            try {
                TruffleFile original = getContext().getEnv().getPublicTruffleFile(path);
                TruffleFile canonicalFile = original.getCanonicalFile();
                if (original.equals(canonicalFile)) {
                    throw raiseOSError(frame, OSErrorEnum.EINVAL, path);
                }
                return canonicalFile.getPath();
            } catch (Exception e) {
                throw raiseOSError(frame, e, path);
            }
        }
    }

    @Builtin(name = "strerror", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class StrErrorNode extends PythonBuiltinNode {

        private static final HashMap<Integer, String> STR_ERROR_MAP = new HashMap<>();

        @Specialization
        @TruffleBoundary
        static String getStrError(int errno) {
            if (STR_ERROR_MAP.isEmpty()) {
                for (OSErrorEnum error : OSErrorEnum.values()) {
                    STR_ERROR_MAP.put(error.getNumber(), error.getMessage());
                }
            }
            String result = STR_ERROR_MAP.get(errno);
            if (result == null) {
                result = "Unknown error " + errno;
            }
            return result;
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

    @Builtin(name = "symlink", minNumOfPositionalArgs = 2, parameterNames = {"src", "dst", "target_is_directory", "dir_fd"})
    @GenerateNodeFactory
    public abstract static class SymlinkNode extends PythonBuiltinNode {

        @Specialization(guards = {"isNoValue(dirFd)"}, limit = "1")
        PNone doSimple(VirtualFrame frame, Object srcObj, Object dstObj, @SuppressWarnings("unused") Object targetIsDir, @SuppressWarnings("unused") PNone dirFd,
                        @CachedLibrary("srcObj") PythonObjectLibrary libSrc,
                        @CachedLibrary("dstObj") PythonObjectLibrary libDst) {
            String src = libSrc.asPath(srcObj);
            String dst = libDst.asPath(dstObj);

            Env env = getContext().getEnv();
            TruffleFile dstFile = env.getPublicTruffleFile(dst);
            try {
                dstFile.createSymbolicLink(env.getPublicTruffleFile(src));
            } catch (Exception e) {
                throw raiseOSError(frame, e, src, dst);
            }
            return PNone.NONE;
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

    @Builtin(name = "fsync", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class FSyncNode extends PythonUnaryBuiltinNode {
        @Specialization
        PNone fsync(VirtualFrame frame, int fd) {
            if (!getContext().getResources().fsync(fd)) {
                throw raiseOSError(frame, OSErrorEnum.ENOENT);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "ftruncate", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class FTruncateNode extends PythonBinaryBuiltinNode {
        @Specialization
        PNone ftruncate(VirtualFrame frame, int fd, long length) {
            try {
                getContext().getResources().ftruncate(fd, length);
            } catch (Exception e) {
                throw raiseOSError(frame, e);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "set_blocking", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class SetBlockingNode extends PythonBinaryBuiltinNode {
        @Specialization
        PNone doPrimitive(VirtualFrame frame, int fd, boolean blocking,
                        @Shared("classProfile") @Cached("createClassProfile()") ValueProfile classProfile) {
            try {
                PSocket socket = getContext().getResources().getSocket(fd);
                if (socket != null) {
                    SocketBuiltins.SetBlockingNode.setBlocking(socket, blocking);
                    return PNone.NONE;
                }
                Channel fileChannel = getContext().getResources().getFileChannel(fd, classProfile);
                if (fileChannel instanceof SelectableChannel) {
                    setBlocking((SelectableChannel) fileChannel, blocking);
                    return PNone.NONE;
                }

                // if we reach this point, it's an invalid FD (either it does not exist or is not
                // selectable)
                throw raiseOSError(frame, OSErrorEnum.EBADFD);
            } catch (Exception e) {
                throw raiseOSError(frame, e);
            }
        }

        @Specialization(limit = "1")
        PNone doGeneric(VirtualFrame frame, Object fdObj, Object blockingObj,
                        @CachedLibrary("fdObj") PythonObjectLibrary fdLib,
                        @CachedLibrary("blockingObj") PythonObjectLibrary blockingLib,
                        @Cached CastToJavaIntExactNode castToJavaIntNode,
                        @Shared("classProfile") @Cached("createClassProfile()") ValueProfile classProfile) {

            int fd;
            try {
                fd = castToJavaIntNode.execute(fdLib.asPIntWithState(fdObj, PArguments.getThreadState(frame)));
            } catch (CannotCastException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }

            boolean blocking = blockingLib.isTrueWithState(blockingObj, PArguments.getThreadState(frame));

            return doPrimitive(frame, fd, blocking, classProfile);
        }

        @TruffleBoundary
        private static void setBlocking(SelectableChannel channel, boolean block) throws IOException {
            channel.configureBlocking(block);
        }
    }

    @Builtin(name = "get_blocking", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class GetBlockingNode extends PythonUnaryBuiltinNode {
        @Specialization
        boolean doPrimitive(VirtualFrame frame, int fd,
                        @Shared("classProfile") @Cached("createClassProfile()") ValueProfile classProfile) {
            PSocket socket = getContext().getResources().getSocket(fd);
            if (socket != null) {
                return SocketBuiltins.GetBlockingNode.get(socket);
            }
            Channel fileChannel = getContext().getResources().getFileChannel(fd, classProfile);
            if (fileChannel instanceof SelectableChannel) {
                return getBlocking((SelectableChannel) fileChannel);
            }

            // if we reach this point, it's an invalid FD (either it does not exist or is not
            // selectable)
            throw raiseOSError(frame, OSErrorEnum.EBADFD);
        }

        @Specialization(limit = "1")
        boolean doGeneric(VirtualFrame frame, Object fdObj,
                        @CachedLibrary("fdObj") PythonObjectLibrary fdLib,
                        @Cached CastToJavaIntExactNode castToJavaIntNode,
                        @Shared("classProfile") @Cached("createClassProfile()") ValueProfile classProfile) {

            int fd;
            try {
                fd = castToJavaIntNode.execute(fdLib.asPIntWithState(fdObj, PArguments.getThreadState(frame)));
            } catch (CannotCastException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }

            return doPrimitive(frame, fd, classProfile);
        }

        @TruffleBoundary
        private static boolean getBlocking(SelectableChannel channel) {
            return channel.isBlocking();
        }
    }

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

        public PathConversionNode(String functionName, String argumentName, boolean nullable, boolean allowFd) {
            this.functionNameWithColon = functionName != null ? functionName + ": " : "";
            this.argumentName = argumentName != null ? argumentName : "path";
            this.nullable = nullable;
            this.allowFd = allowFd;
        }

        @Specialization(guards = "nullable")
        PosixFileHandle doNone(@SuppressWarnings("unused") PNone value) {
            return PosixFileHandle.DEFAULT;
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
        PosixFileHandle doUnicode(String value) {
            return new PosixPath(value, value, checkPath(getStringBytes(value)));
        }

        @Specialization
        PosixFileHandle doUnicode(PString value,
                        @Cached CastToJavaStringNode castToJavaStringNode) {
            String str = castToJavaStringNode.execute(value);
            return new PosixPath(value, str, checkPath(getStringBytes(str)));
        }

        @Specialization
        PosixFileHandle doBytes(VirtualFrame frame, PBytesLike value,
                        @Cached BytesNodes.ToBytesNode toByteArrayNode) {
            return new PosixPath(value, checkPath(toByteArrayNode.execute(frame, value)));
        }

        @Specialization(guards = {"!isHandled(value)", "lib.isBuffer(value)"}, limit = "1")
        PosixFileHandle doBuffer(VirtualFrame frame, Object value,
                        @CachedLibrary("value") PythonObjectLibrary lib,
                        @Cached WarningsModuleBuiltins.WarnNode warningNode) {
            warningNode.warnFormat(frame, null, PythonBuiltinClassType.DeprecationWarning, 1,
                            ErrorMessages.S_S_SHOULD_BE_S_NOT_P, functionNameWithColon, argumentName, getAllowedTypes(), value);
            try {
                return new PosixPath(value, checkPath(lib.getBufferBytes(value)));
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
                        @Cached CastToJavaStringNode castToJavaStringNode) {
            Object func = lib.lookupAttributeOnType(value, __FSPATH__);
            if (func == PNone.NO_VALUE) {
                throw raise(TypeError, ErrorMessages.S_S_SHOULD_BE_S_NOT_P, functionNameWithColon, argumentName,
                                getAllowedTypes(), value);
            }
            Object pathObject = methodLib.callUnboundMethodWithState(func, PArguments.getThreadState(frame), value);
            // 'pathObject' replaces 'value' as the PosixPath.originalObject for auditing purposes
            // by design
            if (pathObject instanceof PBytesLike) {
                return doBytes(frame, (PBytesLike) pathObject, toByteArrayNode);
            }
            if (pathObject instanceof PString) {
                return doUnicode((PString) pathObject, castToJavaStringNode);
            }
            if (pathObject instanceof String) {
                return doUnicode((String) pathObject);
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

        private byte[] checkPath(byte[] path) {
            for (byte b : path) {
                if (b == 0) {
                    throw raise(ValueError, ErrorMessages.S_EMBEDDED_NULL_CHARACTER_IN_S, functionNameWithColon, argumentName);
                }
            }
            return path;
        }

        @TruffleBoundary
        private static byte[] getStringBytes(String str) {
            // TODO replace getBytes with PyUnicode_FSConverter equivalent
            return str.getBytes();
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
}
