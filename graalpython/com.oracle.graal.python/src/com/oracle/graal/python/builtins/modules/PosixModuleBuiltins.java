/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
import java.nio.file.LinkOption;
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
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.PosixModuleBuiltinsFactory.CastToPathNodeGen;
import com.oracle.graal.python.builtins.modules.PosixModuleBuiltinsFactory.ConvertPathlikeObjectNodeGen;
import com.oracle.graal.python.builtins.modules.PosixModuleBuiltinsFactory.ReadFromChannelNodeGen;
import com.oracle.graal.python.builtins.modules.PosixModuleBuiltinsFactory.StatNodeFactory;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.OpaqueBytes;
import com.oracle.graal.python.builtins.objects.bytes.PByteArray;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.bytes.PIBytesLike;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes.LenNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GetItemNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.ToByteArrayNode;
import com.oracle.graal.python.builtins.objects.floats.PFloat;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CastToIndexNode;
import com.oracle.graal.python.runtime.PosixResources;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.exception.PythonExitException;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.dsl.Cached;
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

    @Builtin(name = "getcwd", fixedNumOfPositionalArgs = 0)
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

    @Builtin(name = "chdir", fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ChdirNode extends PythonBuiltinNode {
        @Specialization
        PNone chdir(String spath) {
            Env env = getContext().getEnv();
            try {
                TruffleFile dir = env.getTruffleFile(spath);
                env.setCurrentWorkingDirectory(dir);
                return PNone.NONE;
            } catch (UnsupportedOperationException | IllegalArgumentException | SecurityException e) {
                throw raise(PythonErrorType.FileNotFoundError, "No such file or directory: '%s'", spath);
            }
        }
    }

    @Builtin(name = "getpid", fixedNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class GetPidNode extends PythonBuiltinNode {
        @Specialization
        int getPid() {
            // TODO: this needs to be implemented properly at some point (consider managed execution
            // as well)
            return getContext().hashCode();
        }
    }

    @Builtin(name = "fstat", fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class FstatNode extends PythonFileNode {
        @Child StatNode statNode;

        protected abstract Object executeWith(Object fd);

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
        Object fstat(int fd,
                        @Cached("create()") BranchProfile fstatForNonFile,
                        @Cached("createClassProfile()") ValueProfile channelClassProfile) {
            PosixResources resources = getResources();
            String filePath = resources.getFilePath(fd);
            if (filePath != null) {
                if (statNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    statNode = insert(StatNode.create());
                }
                return statNode.executeWith(resources.getFilePath(fd), PNone.NO_VALUE);
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
        Object fstatPInt(Object fd,
                        @Cached("createOverflow()") CastToIndexNode castToIntNode,
                        @Cached("create()") FstatNode recursive) {
            return recursive.executeWith(castToIntNode.execute(fd));
        }

        protected static FstatNode create() {
            return PosixModuleBuiltinsFactory.FstatNodeFactory.create(null);
        }
    }

    @Builtin(name = "set_inheritable", fixedNumOfPositionalArgs = 2)
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
            TruffleFile f = getContext().getEnv().getTruffleFile(path);
            if (!f.exists()) {
                throw raise(OSError, "No such file or directory: '%s'", path);
            }
            // TODO: investigate how to map this to the truffle file api (if supported)
            return PNone.NONE;
        }
    }

    @Builtin(name = "stat", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class StatNode extends PythonBinaryBuiltinNode {
        private final BranchProfile fileNotFound = BranchProfile.create();

        private static final int S_IFIFO = 0010000;
        private static final int S_IFCHR = 0020000;
        private static final int S_IFBLK = 0060000;
        private static final int S_IFSOCK = 0140000;
        private static final int S_IFLNK = 0120000;
        private static final int S_IFDIR = 0040000;
        private static final int S_IFREG = 0100000;

        protected abstract Object executeWith(Object path, Object followSymlinks);

        @Specialization
        Object doStatPath(String path, boolean followSymlinks) {
            return stat(path, followSymlinks);
        }

        @Specialization(guards = "isNoValue(followSymlinks)")
        Object doStatDefault(String path, @SuppressWarnings("unused") PNone followSymlinks) {
            return stat(path, true);
        }

        @TruffleBoundary
        long fileTimeToSeconds(FileTime t) {
            return t.to(TimeUnit.SECONDS);
        }

        Object stat(String path, boolean followSymlinks) {
            TruffleFile f = getContext().getEnv().getTruffleFile(path);
            LinkOption[] linkOptions = followSymlinks ? new LinkOption[0] : new LinkOption[]{LinkOption.NOFOLLOW_LINKS};
            try {
                if (!f.exists(linkOptions)) {
                    throw fileNoFound(path);
                }
            } catch (SecurityException e) {
                throw fileNoFound(path);
            }
            int mode = 0;
            long size = 0;
            long ctime = 0;
            long atime = 0;
            long mtime = 0;
            long gid = 0;
            long uid = 0;
            if (f.isRegularFile(linkOptions)) {
                mode |= S_IFREG;
            } else if (f.isDirectory(linkOptions)) {
                mode |= S_IFDIR;
            } else if (f.isSymbolicLink()) {
                mode |= S_IFLNK;
            } else {
                // TODO: differentiate these
                mode |= S_IFSOCK | S_IFBLK | S_IFCHR | S_IFIFO;
            }
            try {
                mtime = fileTimeToSeconds(f.getLastModifiedTime(linkOptions));
            } catch (IOException e1) {
                mtime = 0;
            }
            try {
                ctime = fileTimeToSeconds(f.getCreationTime(linkOptions));
            } catch (IOException e1) {
                ctime = 0;
            }
            try {
                atime = fileTimeToSeconds(f.getLastAccessTime(linkOptions));
            } catch (IOException e1) {
                atime = 0;
            }
            UserPrincipal owner;
            try {
                owner = f.getOwner(linkOptions);
                if (owner instanceof UnixNumericUserPrincipal) {
                    uid = strToLong(((UnixNumericUserPrincipal) owner).getName());
                }
            } catch (NumberFormatException | IOException | UnsupportedOperationException | SecurityException e2) {
            }
            try {
                GroupPrincipal group = f.getGroup(linkOptions);
                if (group instanceof UnixNumericGroupPrincipal) {
                    gid = strToLong(((UnixNumericGroupPrincipal) group).getName());
                }
            } catch (NumberFormatException | IOException | UnsupportedOperationException | SecurityException e2) {
            }
            try {
                final Set<PosixFilePermission> posixFilePermissions = f.getPosixPermissions(linkOptions);
                mode = posixPermissionsToMode(mode, posixFilePermissions);
            } catch (UnsupportedOperationException | IOException e1) {
                if (f.isReadable()) {
                    mode |= 0004;
                    mode |= 0040;
                    mode |= 0400;
                }
                if (f.isWritable()) {
                    mode |= 0002;
                    mode |= 0020;
                    mode |= 0200;
                }
                if (f.isExecutable()) {
                    mode |= 0001;
                    mode |= 0010;
                    mode |= 0100;
                }
            }
            try {
                size = f.size(linkOptions);
            } catch (IOException e) {
                size = 0;
            }
            return factory().createTuple(new Object[]{
                            mode,
                            0, // ino
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

        protected static StatNode create() {
            return StatNodeFactory.create();
        }
    }

    @Builtin(name = "listdir", fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class ListdirNode extends PythonBuiltinNode {
        private final BranchProfile gotException = BranchProfile.create();

        @Specialization
        Object listdir(String path) {
            try {
                TruffleFile file = getContext().getEnv().getTruffleFile(path);
                Collection<TruffleFile> listFiles = file.list();
                Object[] filenames = listToArray(listFiles);
                return factory().createList(filenames);
            } catch (SecurityException | IOException e) {
                gotException.enter();
                throw raise(OSError, path);
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

    @Builtin(name = "dup", fixedNumOfPositionalArgs = 1)
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

    @Builtin(name = "open", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 4, keywordArguments = {"mode", "dir_fd"})
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class OpenNode extends PythonFileNode {
        private final BranchProfile gotException = BranchProfile.create();

        @Specialization(guards = {"isNoValue(mode)", "isNoValue(dir_fd)"})
        Object open(String pathname, int flags, @SuppressWarnings("unused") PNone mode, PNone dir_fd) {
            return open(pathname, flags, 0777, dir_fd);
        }

        @Specialization(guards = {"isNoValue(dir_fd)"})
        Object open(String pathname, int flags, int fileMode, @SuppressWarnings("unused") PNone dir_fd) {
            Set<StandardOpenOption> options = flagsToOptions(flags);
            FileAttribute<Set<PosixFilePermission>>[] attributes = modeToAttributes(fileMode);
            TruffleFile truffleFile = getContext().getEnv().getTruffleFile(pathname);
            try {
                SeekableByteChannel fc = truffleFile.newByteChannel(options, attributes);
                return getResources().open(truffleFile, fc);
            } catch (IOException e) {
                gotException.enter();
                throw raise(OSError, e);
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

    @Builtin(name = "lseek", fixedNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class LseekNode extends PythonFileNode {
        private final BranchProfile gotException = BranchProfile.create();
        private final ConditionProfile noFile = ConditionProfile.createBinaryProfile();

        @Specialization
        Object lseek(int fd, long pos, int how,
                        @Cached("createClassProfile()") ValueProfile channelClassProfile) {
            Channel channel = getResources().getFileChannel(fd, channelClassProfile);
            if (noFile.profile(channel == null || !(channel instanceof SeekableByteChannel))) {
                throw raise(OSError, "Illegal seek");
            }
            SeekableByteChannel fc = (SeekableByteChannel) channel;
            try {
                return setPosition(pos, how, fc);
            } catch (IOException e) {
                gotException.enter();
                throw raise(OSError, e);
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

    @Builtin(name = "close", fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class CloseNode extends PythonFileNode {
        private final BranchProfile gotException = BranchProfile.create();
        private final ConditionProfile noFile = ConditionProfile.createBinaryProfile();

        @Specialization
        Object close(int fd,
                        @Cached("createClassProfile()") ValueProfile channelClassProfile) {
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

    @Builtin(name = "unlink", fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class UnlinkNode extends PythonFileNode {
        private final BranchProfile gotException = BranchProfile.create();

        @Specialization
        Object unlink(String path) {
            try {
                getContext().getEnv().getTruffleFile(path).delete();
            } catch (RuntimeException | IOException e) {
                gotException.enter();
                throw raise(OSError, e);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "remove", fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class RemoveNode extends UnlinkNode {
    }

    @Builtin(name = "rmdir", fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class RmdirNode extends UnlinkNode {
    }

    @Builtin(name = "mkdir", fixedNumOfPositionalArgs = 1, keywordArguments = {"mode", "dir_fd"})
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class MkdirNode extends PythonFileNode {
        private final BranchProfile gotException = BranchProfile.create();

        @Specialization
        Object mkdir(String path, @SuppressWarnings("unused") PNone mode, PNone dirFd) {
            return mkdir(path, 511, dirFd);
        }

        @Specialization
        Object mkdir(String path, @SuppressWarnings("unused") int mode, @SuppressWarnings("unused") PNone dirFd) {
            try {
                getContext().getEnv().getTruffleFile(path).createDirectory();
            } catch (RuntimeException | IOException e) {
                gotException.enter();
                throw raise(OSError, e);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "write", fixedNumOfPositionalArgs = 2)
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

    abstract static class ReadFromChannelNode extends PNodeWithContext {
        private final BranchProfile gotException = BranchProfile.create();

        abstract ByteSequenceStorage execute(Channel channel, int size);

        @Specialization
        ByteSequenceStorage readSeekable(SeekableByteChannel channel, int size) {
            long availableSize;
            try {
                availableSize = availableSize(channel);
            } catch (IOException e) {
                gotException.enter();
                throw raise(OSError, e);
            }
            if (availableSize > ReadNode.MAX_READ) {
                availableSize = ReadNode.MAX_READ;
            }
            int sz = (int) Math.min(availableSize, size);
            return readReadable(channel, sz);
        }

        @TruffleBoundary(transferToInterpreterOnException = false)
        private static long availableSize(SeekableByteChannel channel) throws IOException {
            return channel.size() - channel.position();
        }

        @Specialization
        ByteSequenceStorage readReadable(ReadableByteChannel channel, int size) {
            int sz = Math.min(size, ReadNode.MAX_READ);
            ByteBuffer dst = allocateBuffer(sz);
            int readSize = readIntoBuffer(channel, dst);
            byte[] array;
            if (readSize <= 0) {
                array = new byte[0];
                readSize = 0;
            } else {
                array = getByteBufferArray(dst);
            }
            ByteSequenceStorage byteSequenceStorage = new ByteSequenceStorage(array);
            byteSequenceStorage.setNewLength(readSize);
            return byteSequenceStorage;
        }

        @Specialization
        ByteSequenceStorage readGeneric(Channel channel, int size) {
            if (channel instanceof SeekableByteChannel) {
                return readSeekable((SeekableByteChannel) channel, size);
            } else if (channel instanceof ReadableByteChannel) {
                return readReadable((ReadableByteChannel) channel, size);
            } else {
                throw raise(OSError, "file not opened for reading");
            }
        }

        @TruffleBoundary(allowInlining = true)
        private static byte[] getByteBufferArray(ByteBuffer dst) {
            return dst.array();
        }

        @TruffleBoundary(allowInlining = true)
        private int readIntoBuffer(ReadableByteChannel readableChannel, ByteBuffer dst) {
            try {
                return readableChannel.read(dst);
            } catch (IOException e) {
                gotException.enter();
                throw raise(OSError, e);
            }
        }

        @TruffleBoundary(allowInlining = true)
        private static ByteBuffer allocateBuffer(int sz) {
            return ByteBuffer.allocate(sz);
        }

        public static ReadFromChannelNode create() {
            return ReadFromChannelNodeGen.create();
        }
    }

    @Builtin(name = "read", fixedNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class ReadNode extends PythonFileNode {
        private static final int MAX_READ = Integer.MAX_VALUE / 2;

        @Specialization(guards = "readOpaque(frame)")
        Object readOpaque(@SuppressWarnings("unused") VirtualFrame frame, int fd, @SuppressWarnings("unused") Object requestedSize,
                        @Cached("createClassProfile()") ValueProfile channelClassProfile,
                        @Cached("create()") ReadFromChannelNode readNode) {
            Channel channel = getResources().getFileChannel(fd, channelClassProfile);
            ByteSequenceStorage bytes = readNode.execute(channel, MAX_READ);
            return new OpaqueBytes(Arrays.copyOf(bytes.getInternalByteArray(), bytes.length()));
        }

        @Specialization(guards = "!readOpaque(frame)")
        Object read(@SuppressWarnings("unused") VirtualFrame frame, int fd, long requestedSize,
                        @Cached("createClassProfile()") ValueProfile channelClassProfile,
                        @Cached("create()") BranchProfile tooLarge,
                        @Cached("create()") ReadFromChannelNode readNode) {
            int size;
            try {
                size = Math.toIntExact(requestedSize);
            } catch (ArithmeticException e) {
                tooLarge.enter();
                size = MAX_READ;
            }
            Channel channel = getResources().getFileChannel(fd, channelClassProfile);
            ByteSequenceStorage array = readNode.execute(channel, size);
            return factory().createBytes(array);
        }

        /**
         * @param frame - only used so the DSL sees this as a dynamic check
         */
        protected boolean readOpaque(VirtualFrame frame) {
            return OpaqueBytes.isEnabled(getContext());
        }
    }

    @Builtin(name = "isatty", fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class IsATTYNode extends PythonBuiltinNode {
        @Specialization
        boolean isATTY(int fd) {
            switch (fd) {
                case 0:
                case 1:
                case 2:
                    // TODO: XXX: actually check
                    // TODO: We can only return true here once we
                    // have at least basic subprocess module support,
                    // because otherwise we break the REPL help
                    // return consoleCheck();
                    return false;
                default:
                    return false;
            }
        }

        @TruffleBoundary(allowInlining = true)
        private static boolean consoleCheck() {
            return System.console() != null;
        }
    }

    @Builtin(name = "_exit", fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class ExitNode extends PythonBuiltinNode {
        @Specialization
        Object exit(int status) {
            throw new PythonExitException(this, status);
        }
    }

    @Builtin(name = "chmod", minNumOfPositionalArgs = 2, keywordArguments = {"dir_fd", "follow_symlinks"})
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class ChmodNode extends PythonBuiltinNode {
        private final BranchProfile gotException = BranchProfile.create();

        @Specialization
        Object chmod(String path, int mode, @SuppressWarnings("unused") PNone dir_fd, @SuppressWarnings("unused") PNone follow_symlinks) {
            Set<PosixFilePermission> permissions = modeToPermissions(mode);
            try {
                TruffleFile truffleFile = getContext().getEnv().getTruffleFile(path);
                truffleFile.setPosixPermissions(permissions);
            } catch (IOException e) {
                gotException.enter();
                throw raise(OSError, e);
            }
            return PNone.NONE;
        }

        @TruffleBoundary(allowInlining = true)
        private static Set<PosixFilePermission> modeToPermissions(int mode) {
            Set<PosixFilePermission> permissions = new HashSet<>(Arrays.asList(otherBitsToPermission[mode & 7]));
            permissions.addAll(Arrays.asList(groupBitsToPermission[mode >> 3 & 7]));
            permissions.addAll(Arrays.asList(ownerBitsToPermission[mode >> 6 & 7]));
            return permissions;
        }

        @SuppressWarnings("unused")
        @Fallback
        Object chmod(Object path, Object mode, Object dir_fd, Object follow_symlinks) {
            throw raise(NotImplementedError, "chmod");
        }
    }

    @Builtin(name = "utime", minNumOfPositionalArgs = 1, keywordArguments = {"times", "ns", "dir_fd", "follow_symlinks"})
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class UtimeNode extends PythonBuiltinNode {
        @Child private GetItemNode getItemNode;
        @Child private LenNode lenNode;

        @SuppressWarnings("unused")
        @Specialization
        Object utime(String path, PNone times, PNone ns, PNone dir_fd, PNone follow_symlinks) {
            long time = ((Double) TimeModuleBuiltins.timeSeconds()).longValue();
            setMtime(path, time);
            setAtime(path, time);
            return PNone.NONE;
        }

        @SuppressWarnings("unused")
        @Specialization
        Object utime(String path, PTuple times, PNone ns, PNone dir_fd, PNone follow_symlinks) {
            long atime = getTime(times, 0, "times");
            long mtime = getTime(times, 1, "times");
            setMtime(path, mtime);
            setAtime(path, atime);
            return PNone.NONE;
        }

        @SuppressWarnings("unused")
        @Specialization
        Object utime(String path, PNone times, PTuple ns, PNone dir_fd, PNone follow_symlinks) {
            long atime = getTime(ns, 0, "ns") / 1000;
            long mtime = getTime(ns, 1, "ns") / 1000;
            setMtime(path, mtime);
            setAtime(path, atime);
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

        private void setMtime(String path, long mtime) {
            try {
                getContext().getEnv().getTruffleFile(path).setLastModifiedTime(FileTime.fromMillis(mtime));
            } catch (IOException e) {
            }
        }

        private void setAtime(String path, long mtime) {
            try {
                getContext().getEnv().getTruffleFile(path).setLastAccessTime(FileTime.fromMillis(mtime));
            } catch (IOException e) {
            }
        }

        private int getLength(PTuple times) {
            if (lenNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lenNode = insert(SequenceNodes.LenNode.create());
            }
            return lenNode.execute(times);
        }
    }

    @Builtin(name = "waitpid", fixedNumOfPositionalArgs = 2)
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

    @Builtin(name = "system", fixedNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    abstract static class SystemNode extends PythonBuiltinNode {
        static final String[] shell = System.getProperty("os.name").toLowerCase().startsWith("windows") ? new String[]{"cmd.exe", "/c"}
                        : new String[]{(System.getenv().getOrDefault("SHELL", "sh")), "-c"};

        static class PipePump extends Thread {
            private static final int MAX_READ = 8192;
            private final InputStream in;
            private final OutputStream out;
            private final byte[] buffer;
            private volatile boolean finish;
            private volatile boolean flush;

            public PipePump(InputStream in, OutputStream out) {
                this.in = in;
                this.out = out;
                this.buffer = new byte[MAX_READ];
                this.finish = false;
                this.flush = false;
            }

            @Override
            public void run() {
                try {
                    while (!finish || (flush && in.available() > 0)) {
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

            public void finish(boolean force_flush) {
                finish = true;
                flush = force_flush;
                if (flush) {
                    // If we need to flush, make ourselves max priority to pump data out as quickly
                    // as possible
                    setPriority(Thread.MAX_PRIORITY);
                }
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
            String[] command = new String[]{shell[0], shell[1], cmd};
            Env env = context.getEnv();
            try {
                ProcessBuilder pb = new ProcessBuilder(command);
                pb.redirectInput(Redirect.PIPE);
                pb.redirectOutput(Redirect.PIPE);
                pb.redirectError(Redirect.PIPE);
                Process proc = pb.start();
                PipePump stdin = new PipePump(env.in(), proc.getOutputStream());
                PipePump stdout = new PipePump(proc.getInputStream(), env.out());
                PipePump stderr = new PipePump(proc.getErrorStream(), env.err());
                stdin.start();
                stdout.start();
                stderr.start();
                int exitStatus = proc.waitFor();
                stdin.finish(false);
                stdout.finish(true);
                stderr.finish(true);
                return exitStatus;
            } catch (IOException | InterruptedException e) {
                return -1;
            }
        }
    }

    @Builtin(name = "pipe", fixedNumOfPositionalArgs = 0)
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
        @Child private LookupAndCallUnaryNode callFspathNode;
        @CompilationFinal private ValueProfile resultTypeProfile;

        public abstract String execute(Object o);

        @Specialization
        String doPString(String obj) {
            return obj;
        }

        @Specialization
        String doPString(PString obj) {
            return obj.getValue();
        }

        @Fallback
        String doGeneric(Object obj) {
            if (callFspathNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callFspathNode = insert(LookupAndCallUnaryNode.create(__FSPATH__));
            }
            if (resultTypeProfile == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                resultTypeProfile = ValueProfile.createClassProfile();
            }
            Object profiled = resultTypeProfile.profile(callFspathNode.executeObject(obj));
            if (profiled instanceof String) {
                return (String) profiled;
            } else if (profiled instanceof PString) {
                return doPString((PString) profiled);
            }
            throw raise(TypeError, "invalid type %p return from path-like object", profiled);
        }

        public static ConvertPathlikeObjectNode create() {
            return ConvertPathlikeObjectNodeGen.create();
        }

    }

    @Builtin(name = "rename", minNumOfPositionalArgs = 2, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class RenameNode extends PythonFileNode {
        @Specialization
        Object rename(Object src, Object dst, @SuppressWarnings("unused") Object[] args, @SuppressWarnings("unused") PNone kwargs,
                        @Cached("create()") ConvertPathlikeObjectNode convertSrcNode,
                        @Cached("create()") ConvertPathlikeObjectNode convertDstNode) {
            return rename(convertSrcNode.execute(src), convertDstNode.execute(dst));
        }

        @Specialization
        Object rename(Object src, Object dst, @SuppressWarnings("unused") Object[] args, PKeyword[] kwargs,
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
            return rename(convertSrcNode.execute(effectiveSrc), convertDstNode.execute(effectiveDst));
        }

        private Object rename(String src, String dst) {
            try {
                TruffleFile dstFile = getContext().getEnv().getTruffleFile(dst);
                if (dstFile.isDirectory()) {
                    throw raise(OSError, "%s is a directory", dst);
                }
                TruffleFile file = getContext().getEnv().getTruffleFile(src);
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

    @Builtin(name = "urandom", fixedNumOfPositionalArgs = 1)
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

    @Builtin(name = "uname", fixedNumOfPositionalArgs = 0)
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

    @Builtin(name = "access", fixedNumOfPositionalArgs = 2, takesVarArgs = true, keywordArguments = {"dir_fd", "effective_ids", "follow_symlinks"})
    @GenerateNodeFactory
    public abstract static class AccessNode extends PythonBuiltinNode {

        @Child private CastToIndexNode castToIntNode;
        @Child private CastToPathNode castToPathNode;

        private final BranchProfile notImplementedBranch = BranchProfile.create();

        @Specialization(guards = "isNoValue(kwargs)")
        boolean doGeneric(Object path, Object mode, @SuppressWarnings("unused") Object[] args, @SuppressWarnings("unused") PNone kwargs) {
            return access(castToPath(path), castToInt(mode), PNone.NONE, false, true);
        }

        @Specialization
        boolean doGeneric(Object path, Object mode, @SuppressWarnings("unused") Object[] args, PKeyword[] kwargs) {
            boolean effectiveIds = false;
            boolean followSymlinks = true;
            Object dirFd = PNone.NONE;
            for (int i = 0; i < kwargs.length; i++) {
                if ("dir_fd".equals(kwargs[i].getName())) {
                    dirFd = kwargs[i].getValue();
                } else if ("effective_ids".equals(kwargs[i].getName())) {
                    effectiveIds = (boolean) kwargs[i].getValue();
                } else if ("follow_symlinks".equals(kwargs[i].getName())) {
                    followSymlinks = (boolean) kwargs[i].getValue();
                }
            }
            return access(castToPath(path), castToInt(mode), dirFd, effectiveIds, followSymlinks);
        }

        private String castToPath(Object path) {
            if (castToPathNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castToPathNode = insert(CastToPathNode.create());
            }
            return castToPathNode.execute(path);
        }

        private int castToInt(Object mode) {
            if (castToIntNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castToIntNode = insert(CastToIndexNode.createOverflow());
            }
            return castToIntNode.execute(mode);
        }

        private boolean access(String path, int mode, Object dirFd, boolean effectiveIds, boolean followSymlinks) {
            if (dirFd != PNone.NONE || effectiveIds) {
                // TODO implement
                notImplementedBranch.enter();
                throw raise(NotImplementedError);
            }
            TruffleFile f = getContext().getEnv().getTruffleFile(path);
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

    abstract static class CastToPathNode extends PNodeWithContext {

        @Child private BuiltinConstructors.StrNode strNode;
        @Child private CastToPathNode recursive;

        public abstract String execute(Object x);

        public static CastToPathNode create() {
            return CastToPathNodeGen.create();
        }

        @Specialization
        protected String doString(String x) {
            return x;
        }

        @Specialization
        protected String doPString(PString x) {
            return x.getValue();
        }

        @Fallback
        protected String doGeneric(Object x) {
            if (strNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                strNode = insert(BuiltinConstructorsFactory.StrNodeFactory.create(null));
            }
            if (recursive == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                recursive = insert(CastToPathNode.create());
            }
            return recursive.execute(strNode.executeWith(PythonBuiltinClassType.PString, x, PNone.NO_VALUE, PNone.NO_VALUE));
        }
    }
}
