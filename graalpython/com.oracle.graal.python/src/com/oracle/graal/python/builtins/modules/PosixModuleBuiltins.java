/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates.
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
import static com.oracle.graal.python.runtime.exception.PythonErrorType.FileNotFoundError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.NotImplementedError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OSError;
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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.ProcessBuilder.Redirect;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.PosixModuleBuiltinsFactory.ConvertPathlikeObjectNodeGen;
import com.oracle.graal.python.builtins.modules.PosixModuleBuiltinsFactory.StatNodeFactory;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes.ToBytesNode;
import com.oracle.graal.python.builtins.objects.bytes.PByteArray;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.bytes.PIBytesLike;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes.LenNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GetItemDynamicNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GetItemNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.ToByteArrayNode;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.PRaiseOSErrorNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CastToIndexNode;
import com.oracle.graal.python.nodes.util.CastToIntegerFromIntNode;
import com.oracle.graal.python.nodes.util.CastToJavaLongNode;
import com.oracle.graal.python.nodes.util.CastToStringNode;
import com.oracle.graal.python.nodes.util.ChannelNodes.ReadFromChannelNode;
import com.oracle.graal.python.runtime.PosixResources;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.exception.PythonExitException;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
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

    private static final int SEEK_SET = 0;
    private static final int SEEK_CUR = 1;
    private static final int SEEK_END = 2;

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
        return PythonOptions.getFlag(context, PythonOptions.TerminalIsInteractive);
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
        builtinConstants.put("O_RSYNC", RSYNC);
        builtinConstants.put("O_SYNC", SYNC);
        builtinConstants.put("O_TEMPORARY", TEMPORARY);
        builtinConstants.put("O_TMPFILE", TMPFILE);
        builtinConstants.put("SEEK_SET", SEEK_SET);
        builtinConstants.put("SEEK_CUR", SEEK_CUR);
        builtinConstants.put("SEEK_END", SEEK_END);

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
            environ.setItem(core.factory().createBytes(entry.getKey().getBytes()), core.factory().createBytes(entry.getValue().getBytes()));
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
        Object execute(PythonModule thisModule, String path, PList args) {
            return doExecute(thisModule, path, args);
        }

        @Specialization
        Object execute(PythonModule thisModule, PString path, PTuple args) {
            return execute(thisModule, path.getValue(), args);
        }

        @Specialization
        Object execute(PythonModule thisModule, String path, PTuple args) {
            // in case of execl the PList happens to be in the tuples first entry
            Object list = GetItemDynamicNode.getUncached().execute(args.getSequenceStorage(), 0);
            return doExecute(thisModule, path, list instanceof PList ? (PList) list : args);
        }

        @Specialization
        Object execute(PythonModule thisModule, PString path, PList args) {
            return doExecute(thisModule, path.getValue(), args);
        }

        @TruffleBoundary
        Object doExecute(PythonModule thisModule, String path, PSequence args) {
            try {
                if (!getContext().isExecutableAccessAllowed()) {
                    throw raise(OSError, "executable access denied");
                }
                int size = args.getSequenceStorage().length();
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
            } catch (IOException e) {
                throw raise(PythonErrorType.ValueError, "Could not execute script '%s'", e.getMessage());
            }
        }
    }

    @Builtin(name = "getcwd", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class CwdNode extends PythonBuiltinNode {
        @Specialization
        String cwd() {
            try {
                return getContext().getEnv().getCurrentWorkingDirectory().getPath();
            } catch (SecurityException e) {
                return "";
            }
        }
    }

    @Builtin(name = "chdir", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ChdirNode extends PythonBuiltinNode {
        @Specialization
        PNone chdir(String spath) {
            Env env = getContext().getEnv();
            try {
                TruffleFile dir = env.getPublicTruffleFile(spath).getAbsoluteFile();
                env.setCurrentWorkingDirectory(dir);
                return PNone.NONE;
            } catch (UnsupportedOperationException | IllegalArgumentException | SecurityException e) {
                throw raise(PythonErrorType.FileNotFoundError, "No such file or directory: '%s'", spath);
            }
        }

        @Specialization
        PNone chdirPString(PString spath) {
            return chdir(spath.getValue());
        }
    }

    @Builtin(name = "getpid", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class GetPidNode extends PythonBuiltinNode {
        @Specialization
        int getPid() {
            // TODO: this needs to be implemented properly at some point (consider managed execution
            // as well)
            return getContext().hashCode();
        }
    }

    @Builtin(name = "getuid", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class GetUidNode extends PythonBuiltinNode {
        @Specialization
        int getPid() {
            return getSystemUid();
        }

        @TruffleBoundary
        int getSystemUid() {
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
                return statNode.executeWith(frame, resources.getFilePath(fd), PNone.NO_VALUE);
            } else {
                fstatForNonFile.enter();
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
        }

        @Specialization
        Object fstatPInt(VirtualFrame frame, Object fd,
                        @Cached("createOverflow()") CastToIndexNode castToIntNode,
                        @Cached("create()") FstatNode recursive) {
            return recursive.executeWith(frame, castToIntNode.execute(fd));
        }

        protected static FstatNode create() {
            return PosixModuleBuiltinsFactory.FstatNodeFactory.create(null);
        }
    }

    @Builtin(name = "set_inheritable", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class SetInheritableNode extends PythonFileNode {
        @Specialization(guards = {"fd >= 0", "fd <= 2"})
        Object setInheritableStd(@SuppressWarnings("unused") int fd, @SuppressWarnings("unused") Object inheritable) {
            // TODO: investigate if for the stdout/in/err this flag can be set
            return PNone.NONE;
        }

        @Specialization(guards = "fd > 2")
        Object setInheritable(int fd, @SuppressWarnings("unused") Object inheritable) {
            String path = getResources().getFilePath(fd);
            TruffleFile f = getContext().getEnv().getPublicTruffleFile(path);
            if (!f.exists()) {
                throw raise(OSError, "No such file or directory: '%s'", path);
            }
            // TODO: investigate how to map this to the truffle file api (if supported)
            return PNone.NONE;
        }
    }

    @Builtin(name = "stat", minNumOfPositionalArgs = 1, parameterNames = {"path", "follow_symlinks"})
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class StatNode extends PythonBinaryBuiltinNode {
        @Child private ToBytesNode toBytesNode;

        private final BranchProfile fileNotFound = BranchProfile.create();

        private static final int S_IFIFO = 0010000;
        private static final int S_IFCHR = 0020000;
        private static final int S_IFBLK = 0060000;
        private static final int S_IFSOCK = 0140000;
        private static final int S_IFLNK = 0120000;
        private static final int S_IFDIR = 0040000;
        private static final int S_IFREG = 0100000;

        protected abstract Object executeWith(VirtualFrame frame, Object path, Object followSymlinks);

        @Specialization
        Object doStatPath(String path, boolean followSymlinks) {
            return stat(path, followSymlinks);
        }

        @Specialization
        Object doStatDefault(VirtualFrame frame, PIBytesLike path, boolean followSymlinks) {
            return stat(toJavaString(frame, path), followSymlinks);
        }

        @Specialization(guards = "isNoValue(followSymlinks)")
        Object doStatDefault(String path, @SuppressWarnings("unused") PNone followSymlinks) {
            return stat(path, true);
        }

        @Specialization(guards = "isNoValue(followSymlinks)")
        Object doStatDefault(VirtualFrame frame, PIBytesLike path, @SuppressWarnings("unused") PNone followSymlinks) {
            return stat(toJavaString(frame, path), true);
        }

        @TruffleBoundary
        long fileTimeToSeconds(FileTime t) {
            return t.to(TimeUnit.SECONDS);
        }

        @TruffleBoundary
        Object stat(String path, boolean followSymlinks) {
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

        private PTuple unixStat(TruffleFile file, LinkOption... linkOptions) {
            try {
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
            } catch (IOException | SecurityException e) {
                throw fileNoFound(file.getPath());
            }
        }

        private PTuple posixStat(TruffleFile file, LinkOption... linkOptions) {
            try {
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
            } catch (IOException | SecurityException e) {
                throw fileNoFound(file.getPath());
            }
        }

        private PTuple basicStat(TruffleFile file, LinkOption... linkOptions) {
            try {
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
            } catch (IOException | SecurityException e) {
                throw fileNoFound(file.getPath());
            }
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

        private PException fileNoFound(String path) {
            fileNotFound.enter();
            throw raise(FileNotFoundError, "No such file or directory: '%s'", path);
        }

        @TruffleBoundary(allowInlining = true, transferToInterpreterOnException = false)
        private static long strToLong(String name) throws NumberFormatException {
            return new Long(name).longValue();
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

        private String toJavaString(VirtualFrame frame, PIBytesLike bytesLike) {
            if (toBytesNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toBytesNode = insert(ToBytesNode.create());
            }
            return newString(toBytesNode.execute(frame, bytesLike));
        }

        @TruffleBoundary
        private static String newString(byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }

        public static StatNode create() {
            return StatNodeFactory.create();
        }
    }

    @Builtin(name = "listdir", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class ListdirNode extends PythonBuiltinNode {
        @Specialization
        Object listdir(VirtualFrame frame, String path,
                        @Cached PRaiseOSErrorNode raiseOS) {
            try {
                TruffleFile file = getContext().getPublicTruffleFileRelaxed(path, PythonLanguage.DEFAULT_PYTHON_EXTENSIONS);
                Collection<TruffleFile> listFiles = file.list();
                Object[] filenames = listToArray(listFiles);
                return factory().createList(filenames);
            } catch (NoSuchFileException e) {
                throw raiseOS.raiseOSError(frame, OSErrorEnum.ENOENT, path);
            } catch (SecurityException e) {
                throw raiseOS.raiseOSError(frame, OSErrorEnum.EPERM, path);
            } catch (IOException e) {
                throw raiseOS.raiseOSError(frame, OSErrorEnum.ENOTDIR, path);
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
        private final BranchProfile gotException = BranchProfile.create();

        @Specialization
        Object doit(LazyPythonClass cls, String path) {
            try {
                TruffleFile file = getContext().getEnv().getPublicTruffleFile(path);
                return factory().createScandirIterator(cls, path, file.newDirectoryStream());
            } catch (SecurityException | IOException e) {
                gotException.enter();
                throw raise(OSError, path);
            }
        }
    }

    @Builtin(name = "DirEntry", minNumOfPositionalArgs = 3, constructsClass = PythonBuiltinClassType.PDirEntry, isPublic = true)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class DirEntryNode extends PythonTernaryBuiltinNode {
        private final BranchProfile gotException = BranchProfile.create();

        @Specialization
        Object doit(LazyPythonClass cls, String name, String path) {
            try {
                TruffleFile dir = getContext().getEnv().getPublicTruffleFile(path);
                TruffleFile file = dir.resolve(name);
                return factory().createDirEntry(cls, name, file);
            } catch (SecurityException | InvalidPathException e) {
                gotException.enter();
                throw raise(OSError, path);
            }
        }
    }

    @Builtin(name = "dup", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class DupNode extends PythonFileNode {
        @Specialization
        int dup(int fd) {
            return getResources().dup(fd);
        }

        @Specialization(rewriteOn = ArithmeticException.class)
        int dupPInt(PInt fd) {
            return getResources().dup(fd.intValueExact());
        }

        @Specialization(replaces = "dupPInt")
        int dupOvf(PInt fd) {
            try {
                return dupPInt(fd);
            } catch (ArithmeticException e) {
                throw raise(OSError, "invalid fd %r", fd);
            }
        }
    }

    @Builtin(name = "open", minNumOfPositionalArgs = 2, parameterNames = {"pathname", "flags", "mode", "dir_fd"})
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class OpenNode extends PythonFileNode {
        @Child private SequenceStorageNodes.ToByteArrayNode toByteArrayNode;

        private final BranchProfile gotException = BranchProfile.create();

        @Specialization(guards = {"isNoValue(mode)", "isNoValue(dir_fd)"})
        Object open(VirtualFrame frame, String pathname, int flags, @SuppressWarnings("unused") PNone mode, PNone dir_fd) {
            return open(frame, pathname, flags, 0777, dir_fd);
        }

        @Specialization(guards = {"isNoValue(dir_fd)"})
        Object open(VirtualFrame frame, String pathname, int flags, int fileMode, @SuppressWarnings("unused") PNone dir_fd) {
            Set<StandardOpenOption> options = flagsToOptions(flags);
            FileAttribute<Set<PosixFilePermission>>[] attributes = modeToAttributes(fileMode);
            TruffleFile truffleFile = getContext().getPublicTruffleFileRelaxed(pathname, PythonLanguage.DEFAULT_PYTHON_EXTENSIONS);
            try {
                SeekableByteChannel fc = truffleFile.newByteChannel(options, attributes);
                return getResources().open(truffleFile, fc);
            } catch (NoSuchFileException e) {
                gotException.enter();
                throw raiseOSError(frame, OSErrorEnum.ENOENT, e.getFile());
            } catch (AccessDeniedException e) {
                gotException.enter();
                throw raiseOSError(frame, OSErrorEnum.EACCES, e.getFile());
            } catch (FileSystemException e) {
                gotException.enter();
                // TODO FileSystemException can have more reasons, not only is a directory -> should
                // be handled more accurate
                throw raiseOSError(frame, OSErrorEnum.EISDIR, e.getFile());
            } catch (IOException e) {
                gotException.enter();
                // if this happen, we should raise OSError with appropriate errno
                throw raiseOSError(frame, -1);
            }
        }

        @Specialization(guards = {"isNoValue(dir_fd)"})
        Object open(VirtualFrame frame, PBytes pathname, int flags, int fileMode, PNone dir_fd) {
            return open(frame, decode(getByteArray(pathname)), flags, fileMode, dir_fd);
        }

        private byte[] getByteArray(PIBytesLike pByteArray) {
            if (toByteArrayNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toByteArrayNode = insert(ToByteArrayNode.create());
            }
            return toByteArrayNode.execute(pByteArray.getSequenceStorage());
        }

        @TruffleBoundary
        private String decode(byte[] raw) {
            try {
                return new String(raw, "ascii");
            } catch (UnsupportedEncodingException e) {
                throw raise(PythonBuiltinClassType.UnicodeDecodeError, e);
            }
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        @TruffleBoundary(allowInlining = true)
        private static FileAttribute<Set<PosixFilePermission>>[] modeToAttributes(int fileMode) {
            FileAttribute<Set<PosixFilePermission>> fa1 = PosixFilePermissions.asFileAttribute(new HashSet<>(Arrays.asList(otherBitsToPermission[fileMode & 7])));
            FileAttribute<Set<PosixFilePermission>> fa2 = PosixFilePermissions.asFileAttribute(new HashSet<>(Arrays.asList(groupBitsToPermission[fileMode >> 3 & 7])));
            FileAttribute<Set<PosixFilePermission>> fa3 = PosixFilePermissions.asFileAttribute(new HashSet<>(Arrays.asList(ownerBitsToPermission[fileMode >> 6 & 7])));
            return new FileAttribute[]{fa1, fa2, fa3};
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

    @Builtin(name = "lseek", minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class LseekNode extends PythonFileNode {
        private final BranchProfile gotException = BranchProfile.create();
        private final ConditionProfile noFile = ConditionProfile.createBinaryProfile();

        @Specialization
        Object lseek(VirtualFrame frame, long fd, long pos, int how,
                        @Cached PRaiseOSErrorNode raise,
                        @Cached("createClassProfile()") ValueProfile channelClassProfile) {
            Channel channel = getResources().getFileChannel((int) fd, channelClassProfile);
            if (noFile.profile(channel == null || !(channel instanceof SeekableByteChannel))) {
                throw raise.raiseOSError(frame, OSErrorEnum.ESPIPE);
            }
            SeekableByteChannel fc = (SeekableByteChannel) channel;
            try {
                return setPosition(pos, how, fc);
            } catch (IOException e) {
                gotException.enter();
                // if this happen, we should raise OSError with appropriate errno
                throw raise.raiseOSError(frame, -1);
            }
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
                default:
                    fc.position(pos);
            }
            return fc.position();
        }
    }

    @Builtin(name = "close", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class CloseNode extends PythonFileNode {
        private final BranchProfile gotException = BranchProfile.create();
        private final ConditionProfile noFile = ConditionProfile.createBinaryProfile();

        @Specialization
        Object close(Object fdObject,
                        @Cached CastToIndexNode castToIndex,
                        @Cached("createClassProfile()") ValueProfile channelClassProfile) {
            int fd = castToIndex.execute(fdObject);
            PosixResources resources = getResources();
            Channel channel = resources.getFileChannel(fd, channelClassProfile);
            if (noFile.profile(channel == null)) {
                throw raise(OSError, "invalid fd");
            } else {
                resources.close(fd);
                try {
                    closeChannel(channel);
                } catch (IOException e) {
                    gotException.enter();
                    throw raise(OSError, e);
                }
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
        private final BranchProfile gotException = BranchProfile.create();

        @Specialization
        Object unlink(String path) {
            try {
                getContext().getEnv().getPublicTruffleFile(path).delete();
            } catch (RuntimeException | IOException e) {
                gotException.enter();
                throw raise(OSError, e);
            }
            return PNone.NONE;
        }

        @Specialization
        Object unlink(VirtualFrame frame, Object pathLike,
                        @Cached("createFspath()") LookupAndCallUnaryNode callFspathNode,
                        @Cached CastToStringNode castToStringNode) {
            try {
                Object fsPathObj = callFspathNode.executeObject(frame, pathLike);
                getContext().getEnv().getPublicTruffleFile(castToStringNode.execute(frame, fsPathObj)).delete();
            } catch (RuntimeException | IOException e) {
                gotException.enter();
                throw raise(OSError, e);
            }
            return PNone.NONE;
        }

        protected static LookupAndCallUnaryNode createFspath() {
            return LookupAndCallUnaryNode.create(__FSPATH__);
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
        private final BranchProfile gotException = BranchProfile.create();

        @Specialization
        Object mkdir(VirtualFrame frame, String path, @SuppressWarnings("unused") PNone mode, PNone dirFd) {
            return mkdir(frame, path, 511, dirFd);
        }

        @Specialization
        Object mkdir(VirtualFrame frame, String path, @SuppressWarnings("unused") int mode, @SuppressWarnings("unused") PNone dirFd) {
            try {
                getContext().getEnv().getPublicTruffleFile(path).createDirectory();
            } catch (FileAlreadyExistsException e) {
                throw raiseOSError(frame, OSErrorEnum.EEXIST, path);
            } catch (RuntimeException | IOException e) {
                gotException.enter();
                // if this happen, we should raise OSError with appropriate errno
                throw raiseOSError(frame, -1);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "write", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class WriteNode extends PythonFileNode {
        @Child private SequenceStorageNodes.ToByteArrayNode toByteArrayNode;
        private final BranchProfile gotException = BranchProfile.create();
        private final BranchProfile notWritable = BranchProfile.create();

        public abstract Object executeWith(Object fd, Object data);

        @Specialization
        Object write(int fd, byte[] data,
                        @Cached("createClassProfile()") ValueProfile channelClassProfile) {
            Channel channel = getResources().getFileChannel(fd, channelClassProfile);
            if (channel instanceof WritableByteChannel) {
                try {
                    return doWriteOp(data, (WritableByteChannel) channel);
                } catch (NonWritableChannelException | IOException e) {
                    gotException.enter();
                    throw raise(OSError, e);
                }
            } else {
                notWritable.enter();
                throw raise(OSError, "file not opened for writing");
            }
        }

        @TruffleBoundary(allowInlining = true, transferToInterpreterOnException = false)
        private static int doWriteOp(byte[] data, WritableByteChannel channel) throws IOException {
            return channel.write(ByteBuffer.wrap(data));
        }

        @Specialization
        Object write(int fd, String data,
                        @Cached("createClassProfile()") ValueProfile channelClassProfile) {
            return write(fd, stringToBytes(data), channelClassProfile);
        }

        @TruffleBoundary
        private static byte[] stringToBytes(String data) {
            return data.getBytes();
        }

        @Specialization
        Object write(int fd, PBytes data,
                        @Cached("createClassProfile()") ValueProfile channelClassProfile) {
            return write(fd, getByteArray(data), channelClassProfile);
        }

        @Specialization
        Object write(int fd, PByteArray data,
                        @Cached("createClassProfile()") ValueProfile channelClassProfile) {
            return write(fd, getByteArray(data), channelClassProfile);
        }

        @Specialization
        Object writePInt(Object fd, Object data,
                        @Cached("createOverflow()") CastToIndexNode castToIntNode,
                        @Cached("create()") WriteNode recursive) {
            return recursive.executeWith(castToIntNode.execute(fd), data);
        }

        private byte[] getByteArray(PIBytesLike pByteArray) {
            if (toByteArrayNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                toByteArrayNode = insert(ToByteArrayNode.create());
            }
            return toByteArrayNode.execute(pByteArray.getSequenceStorage());
        }

        public static WriteNode create() {
            return PosixModuleBuiltinsFactory.WriteNodeFactory.create(null);
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
            int size;
            try {
                size = Math.toIntExact(requestedSize);
            } catch (ArithmeticException e) {
                tooLargeProfile.enter();
                size = ReadFromChannelNode.MAX_READ;
            }
            Channel channel = getResources().getFileChannel(fd, channelClassProfile);
            ByteSequenceStorage array = readNode.execute(channel, size);
            return factory().createBytes(array);
        }

        @Specialization
        Object read(@SuppressWarnings("unused") VirtualFrame frame, int fd, Object requestedSize,
                        @Shared("profile") @Cached("createClassProfile()") ValueProfile channelClassProfile,
                        @Shared("readNode") @Cached ReadFromChannelNode readNode,
                        @Cached CastToJavaLongNode castToLongNode) {
            return readLong(frame, fd, castToLongNode.execute(requestedSize), channelClassProfile, readNode);
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
        boolean isATTY(@SuppressWarnings("unused") Object fd) {
            return false;
        }
    }

    @Builtin(name = "_exit", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class ExitNode extends PythonBuiltinNode {
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

        @Specialization
        Object chmod(String path, long mode, @SuppressWarnings("unused") PNone dir_fd, @SuppressWarnings("unused") PNone follow_symlinks) {
            return chmod(path, mode, dir_fd, true);
        }

        @Specialization
        Object chmod(String path, long mode, @SuppressWarnings("unused") PNone dir_fd, boolean follow_symlinks) {
            Set<PosixFilePermission> permissions = modeToPermissions(mode);
            try {
                TruffleFile truffleFile = getContext().getEnv().getPublicTruffleFile(path);
                if (!follow_symlinks) {
                    truffleFile = truffleFile.getCanonicalFile(LinkOption.NOFOLLOW_LINKS);
                } else {
                    truffleFile = truffleFile.getCanonicalFile();
                }
                truffleFile.setPosixPermissions(permissions);
            } catch (IOException e) {
                gotException.enter();
                throw raise(OSError, e);
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
        @Specialization
        Object utime(String path, PNone times, PNone ns, PNone dir_fd, PNone follow_symlinks) {
            long time = ((Double) TimeModuleBuiltins.timeSeconds()).longValue();
            setMtime(getFile(path, true), time);
            setAtime(getFile(path, true), time);
            return PNone.NONE;
        }

        @SuppressWarnings("unused")
        @Specialization
        Object utime(String path, PTuple times, PNone ns, PNone dir_fd, PNone follow_symlinks) {
            long atime = getTime(times, 0, "times");
            long mtime = getTime(times, 1, "times");
            setMtime(getFile(path, true), mtime);
            setAtime(getFile(path, true), atime);
            return PNone.NONE;
        }

        @SuppressWarnings("unused")
        @Specialization
        Object utime(String path, PNone times, PTuple ns, PNone dir_fd, PNone follow_symlinks) {
            long atime = getTime(ns, 0, "ns") / 1000;
            long mtime = getTime(ns, 1, "ns") / 1000;
            setMtime(getFile(path, true), mtime);
            setAtime(getFile(path, true), atime);
            return PNone.NONE;
        }

        @SuppressWarnings("unused")
        @Specialization
        Object utime(String path, PNone times, PTuple ns, PNone dir_fd, boolean follow_symlinks) {
            long atime = getTime(ns, 0, "ns") / 1000;
            long mtime = getTime(ns, 1, "ns") / 1000;
            setMtime(getFile(path, true), mtime);
            setAtime(getFile(path, true), atime);
            return PNone.NONE;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!isPNone(times)", "!isPTuple(times)"})
        Object utimeWrongTimes(String path, Object times, Object ns, Object dir_fd, Object follow_symlinks) {
            throw tupleError("times");
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!isPTuple(ns)", "!isPNone(ns)"})
        Object utimeWrongNs(String path, PNone times, Object ns, Object dir_fd, Object follow_symlinks) {
            throw tupleError("ns");
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!isPNone(ns)"})
        Object utimeWrongNs(String path, PTuple times, Object ns, Object dir_fd, Object follow_symlinks) {
            throw raise(ValueError, "utime: you may specify either 'times' or 'ns' but not both");
        }

        @SuppressWarnings("unused")
        @Fallback
        Object utimeError(Object path, Object times, Object ns, Object dir_fd, Object follow_symlinks) {
            throw raise(NotImplementedError, "utime");
        }

        private long getTime(PTuple times, int index, String argname) {
            if (getLength(times) <= index) {
                throw tupleError(argname);
            }
            if (getItemNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getItemNode = insert(GetItemNode.createNotNormalized());
            }
            Object mtimeObj = getItemNode.execute(times.getSequenceStorage(), index);
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
                throw raise(ValueError, "time cannot be negative");
            }
            return mtime;
        }

        private PException tupleError(String argname) {
            return raise(TypeError, "utime: '%s' must be either a tuple of two ints or None", argname);
        }

        private void setMtime(TruffleFile truffleFile, long mtime) {
            try {
                truffleFile.setLastModifiedTime(FileTime.from(mtime, TimeUnit.SECONDS));
            } catch (IOException | SecurityException e) {
                throw raise();
            }
        }

        private void setAtime(TruffleFile truffleFile, long mtime) {
            try {
                truffleFile.setLastAccessTime(FileTime.from(mtime, TimeUnit.SECONDS));
            } catch (IOException | SecurityException e) {
                throw raise();
            }
        }

        private TruffleFile getFile(String path, boolean followSymlinks) {
            TruffleFile truffleFile = getContext().getEnv().getPublicTruffleFile(path);
            if (!followSymlinks) {
                try {
                    truffleFile = truffleFile.getCanonicalFile(LinkOption.NOFOLLOW_LINKS);
                } catch (IOException | SecurityException e) {
                    throw raise();
                }
            }
            return truffleFile;
        }

        private PException raise() {
            throw raise(ValueError, "Operation not allowed");
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
        @Specialization(guards = {"options == 0"})
        @TruffleBoundary
        PTuple waitpid(int pid, int options) {
            try {
                int exitStatus = getResources().waitpid(pid);
                return factory().createTuple(new Object[]{pid, exitStatus});
            } catch (ArrayIndexOutOfBoundsException | InterruptedException e) {
                throw raise(OSError, "not a valid child pid");
            }
        }

        @SuppressWarnings("unused")
        @Fallback
        PTuple waitpid(Object pid, Object options) {
            throw raise(NotImplementedError, "waitpid");
        }
    }

    @Builtin(name = "system", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class SystemNode extends PythonBuiltinNode {
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
            PythonLanguage.getLogger().fine(() -> "os.system: " + cmd);
            String[] command = new String[]{shell[0], shell[1], cmd};
            Env env = context.getEnv();
            try {
                ProcessBuilder pb = new ProcessBuilder(command);
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
        private final BranchProfile gotException = BranchProfile.create();

        @Specialization
        PTuple pipe() {
            int[] pipe;
            try {
                pipe = getResources().pipe();
            } catch (IOException e) {
                gotException.enter();
                throw raise(OSError, e);
            }
            return factory().createTuple(new Object[]{pipe[0], pipe[1]});
        }
    }

    public abstract static class ConvertPathlikeObjectNode extends PNodeWithContext {
        @Child private PRaiseNode raise;
        @Child private LookupAndCallUnaryNode callFspathNode;
        @CompilationFinal private ValueProfile resultTypeProfile;

        public abstract String execute(VirtualFrame frame, Object o);

        @Specialization
        String doPString(String obj) {
            return obj;
        }

        @Specialization
        String doPString(PString obj) {
            return obj.getValue();
        }

        @Fallback
        String doGeneric(VirtualFrame frame, Object obj) {
            if (callFspathNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callFspathNode = insert(LookupAndCallUnaryNode.create(__FSPATH__));
            }
            if (resultTypeProfile == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                resultTypeProfile = ValueProfile.createClassProfile();
            }
            Object profiled = resultTypeProfile.profile(callFspathNode.executeObject(frame, obj));
            if (profiled instanceof String) {
                return (String) profiled;
            } else if (profiled instanceof PString) {
                return doPString((PString) profiled);
            }
            if (raise == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                raise = insert(PRaiseNode.create());
            }
            throw raise.raise(TypeError, "invalid type %p return from path-like object", profiled);
        }

        public static ConvertPathlikeObjectNode create() {
            return ConvertPathlikeObjectNodeGen.create();
        }

    }

    @Builtin(name = "rename", minNumOfPositionalArgs = 2, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class RenameNode extends PythonFileNode {
        @Specialization
        Object rename(VirtualFrame frame, Object src, Object dst, @SuppressWarnings("unused") Object[] args, @SuppressWarnings("unused") PNone kwargs,
                        @Cached("create()") ConvertPathlikeObjectNode convertSrcNode,
                        @Cached("create()") ConvertPathlikeObjectNode convertDstNode) {
            return rename(convertSrcNode.execute(frame, src), convertDstNode.execute(frame, dst));
        }

        @Specialization
        Object rename(VirtualFrame frame, Object src, Object dst, @SuppressWarnings("unused") Object[] args, PKeyword[] kwargs,
                        @Cached("create()") ConvertPathlikeObjectNode convertSrcNode,
                        @Cached("create()") ConvertPathlikeObjectNode convertDstNode) {

            Object effectiveSrc = src;
            Object effectiveDst = dst;
            PosixResources resources = getResources();
            for (int i = 0; i < kwargs.length; i++) {
                Object value = kwargs[i].getValue();
                if ("src_dir_fd".equals(kwargs[i].getName())) {
                    if (!(value instanceof Integer)) {
                        throw raise(OSError, "invalid file descriptor provided");
                    }
                    effectiveSrc = resources.getFilePath((int) value);
                } else if ("dst_dir_fd".equals(kwargs[i].getName())) {
                    if (!(value instanceof Integer)) {
                        throw raise(OSError, "invalid file descriptor provided");
                    }
                    effectiveDst = resources.getFilePath((int) value);
                }
            }
            return rename(convertSrcNode.execute(frame, effectiveSrc), convertDstNode.execute(frame, effectiveDst));
        }

        private Object rename(String src, String dst) {
            try {
                TruffleFile dstFile = getContext().getEnv().getPublicTruffleFile(dst);
                if (dstFile.isDirectory()) {
                    throw raise(OSError, "%s is a directory", dst);
                }
                TruffleFile file = getContext().getEnv().getPublicTruffleFile(src);
                file.move(dstFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                return PNone.NONE;
            } catch (IOException e) {
                throw raise(OSError, "cannot rename %s to %s", src, dst);
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
        @Specialization
        @TruffleBoundary(allowInlining = true)
        PBytes urandom(int size) {
            // size is in bytes
            BigInteger bigInteger = new BigInteger(size * 8, new Random());
            // sign may introduce an extra byte
            byte[] range = Arrays.copyOfRange(bigInteger.toByteArray(), 0, size);
            return factory().createBytes(range);
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

        @Child private CastToIndexNode castToIntNode;
        @Child private ConvertPathlikeObjectNode castToPathNode;

        private final BranchProfile notImplementedBranch = BranchProfile.create();

        @Specialization
        boolean doGeneric(VirtualFrame frame, Object path, Object mode, @SuppressWarnings("unused") PNone dir_fd, @SuppressWarnings("unused") PNone effective_ids,
                        @SuppressWarnings("unused") PNone follow_symlinks) {
            return access(castToPath(frame, path), castToInt(mode), PNone.NONE, false, true);
        }

        private String castToPath(VirtualFrame frame, Object path) {
            if (castToPathNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castToPathNode = insert(ConvertPathlikeObjectNode.create());
            }
            return castToPathNode.execute(frame, path);
        }

        private int castToInt(Object mode) {
            if (castToIntNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castToIntNode = insert(CastToIndexNode.createOverflow());
            }
            return castToIntNode.execute(mode);
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
        @Specialization
        int getCpuCount() {
            return Runtime.getRuntime().availableProcessors();
        }
    }

    @Builtin(name = "umask", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class UmaskNode extends PythonBuiltinNode {
        @Specialization
        int getAndSetUmask(int umask) {
            if (umask == 0022) {
                return 0022;
            }
            if (umask == 0) {
                // TODO: change me, this does not really set the umask, workaround needed for pip
                // it returns the previous mask (which in our case is always 0022)
                return 0022;
            } else {
                throw raise(NotImplementedError, "setting the umask to anything other than the default");
            }
        }
    }

    @Builtin(name = "get_terminal_size", maxNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class GetTerminalSizeNode extends PythonUnaryBuiltinNode {
        private static final String ERROR_MESSAGE = "[Errno 9] Bad file descriptor";

        @Child private CastToIntegerFromIntNode castIntNode;
        @Child private GetTerminalSizeNode recursiveNode;

        @CompilationFinal private ConditionProfile errorProfile;
        @CompilationFinal private ConditionProfile overflowProfile;

        private CastToIntegerFromIntNode getCastIntNode() {
            if (castIntNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castIntNode = insert(CastToIntegerFromIntNode.create(val -> {
                    throw raise(PythonBuiltinClassType.TypeError, "an integer is required (got type %p)", val);
                }));
            }
            return castIntNode;
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
        PTuple getTerminalSize(@SuppressWarnings("unused") PNone fd) {
            if (getErrorProfile().profile(getContext().getResources().getFileChannel(0) == null)) {
                throw raise(OSError, ERROR_MESSAGE);
            }
            return factory().createTuple(new Object[]{PythonOptions.getTerminalWidth(), PythonOptions.getTerminalHeight()});
        }

        @Specialization
        PTuple getTerminalSize(int fd) {
            if (getErrorProfile().profile(getContext().getResources().getFileChannel(fd) == null)) {
                throw raise(OSError, ERROR_MESSAGE);
            }
            return factory().createTuple(new Object[]{PythonOptions.getTerminalWidth(), PythonOptions.getTerminalHeight()});
        }

        @Specialization
        PTuple getTerminalSize(long fd) {
            if (getOverflowProfile().profile(Integer.MIN_VALUE > fd || fd > Integer.MAX_VALUE)) {
                raise(PythonErrorType.OverflowError, "Python int too large to convert to C long");
            }
            if (getErrorProfile().profile(getContext().getResources().getFileChannel((int) fd) == null)) {
                throw raise(OSError, "[Errno 9] Bad file descriptor");
            }
            return factory().createTuple(new Object[]{PythonOptions.getTerminalWidth(), PythonOptions.getTerminalHeight()});
        }

        @Specialization
        @TruffleBoundary
        PTuple getTerminalSize(PInt fd) {
            int value;
            try {
                value = fd.intValueExact();
                if (getContext().getResources().getFileChannel(value) == null) {
                    throw raise(OSError, ERROR_MESSAGE);
                }
            } catch (ArithmeticException e) {
                throw raise(PythonErrorType.OverflowError, "Python int too large to convert to C long");
            }
            return factory().createTuple(new Object[]{PythonOptions.getTerminalWidth(), PythonOptions.getTerminalHeight()});
        }

        @Fallback
        Object getTerminalSize(VirtualFrame frame, Object fd) {
            Object value = getCastIntNode().execute(fd);
            if (recursiveNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                recursiveNode = create();
            }
            return recursiveNode.execute(frame, value);
        }

        protected GetTerminalSizeNode create() {
            return PosixModuleBuiltinsFactory.GetTerminalSizeNodeFactory.create();
        }
    }

    @Builtin(name = "readlink", minNumOfPositionalArgs = 1, parameterNames = {"path"}, varArgsMarker = true, keywordOnlyNames = {"dirFd"}, doc = "readlink(path, *, dir_fd=None) -> path\n" +
                    "\nReturn a string representing the path to which the symbolic link points.\n")
    @GenerateNodeFactory
    abstract static class ReadlinkNode extends PythonBinaryBuiltinNode {
        @Specialization
        String readlinkPString(PString str, PNone none) {
            return readlink(str.getValue(), none);
        }

        @Specialization
        String readlink(String str, @SuppressWarnings("unused") PNone none) {
            try {
                return getContext().getEnv().getPublicTruffleFile(str).getCanonicalFile().getPath();
            } catch (IOException e) {
                throw raise(OSError, e);
            }
        }
    }

    @Builtin(name = "strerror", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class StrErrorNode extends PythonBuiltinNode {

        private static final HashMap<Integer, String> STR_ERROR_MAP = new HashMap<>();

        @Specialization
        String getStrError(int errno) {
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
        String ctermid() {
            return "/dev/tty";
        }
    }

    @Builtin(name = "symlink", minNumOfPositionalArgs = 2, parameterNames = {"src", "dst", "target_is_directory", "dir_fd"})
    @GenerateNodeFactory
    public abstract static class SymlinkNode extends PythonBuiltinNode {

        @Specialization(guards = {"isNoValue(targetIsDir)", "isNoValue(dirFd)"})
        PNone doSimple(VirtualFrame frame, Object srcObj, Object dstObj, @SuppressWarnings("unused") PNone targetIsDir, @SuppressWarnings("unused") PNone dirFd,
                        @Cached ConvertPathlikeObjectNode castSrcToPath,
                        @Cached ConvertPathlikeObjectNode castDstToPath) {
            String src = castSrcToPath.execute(frame, srcObj);
            String dst = castDstToPath.execute(frame, dstObj);

            Env env = getContext().getEnv();
            TruffleFile dstFile = env.getPublicTruffleFile(dst);
            try {
                dstFile.createSymbolicLink(env.getPublicTruffleFile(src));
            } catch (IOException e) {
                throw raiseOSError(frame, OSErrorEnum.EIO, e);
            }
            return PNone.NONE;
        }
    }

}
