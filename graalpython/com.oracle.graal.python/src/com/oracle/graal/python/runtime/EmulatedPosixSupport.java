/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.runtime;

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

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.LinkOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.ProcessProperties;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum.ErrorAndMessagePair;
import com.oracle.graal.python.builtins.objects.socket.PSocket;
import com.oracle.graal.python.builtins.objects.socket.SocketBuiltins;
import com.oracle.graal.python.nodes.util.ChannelNodes.ReadFromChannelNode;
import com.oracle.graal.python.runtime.PosixSupportLibrary.Buffer;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixException;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixPath;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.util.FileDeleteShutdownHook;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleFile.Attributes;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.ExportMessage.Ignore;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.sun.security.auth.UnixNumericGroupPrincipal;

// TODO: user documentation that "java" POSIX support
//    * ignores PyUnicode_FSConverter (the conversion function will be part of the PosixSupportLibrary)
//    * treats bytes always as UTF-8

/**
 * Implementation that emulates as much as possible using the Truffle API.
 *
 * Limitations of the POSIX emulation:
 * <ul>
 * <li>Any global state that is changed in Python extensions' native code is not reflected in this
 * emulation layer. Namely: umask, current working directory.</li>
 * <li>umask is set to hard-coded default of 0022 and not inherited from the OS. We may add an
 * option that would allow to change this.</li>
 * <li>It ignores {@code PyUnicode_FSConverter} and takes any String paths as-is and bytes objects
 * that are passed as paths are always converted to Strings using UTF-8.</li>>
 * <li>Fork does not actually for the process, any arguments related to resources inheritance for
 * the child process, are silently ignored.</li>
 * <li>All file descriptors are not inheritable (newly spawned processes will not see them).</li>
 * </ul>
 */
@ExportLibrary(PosixSupportLibrary.class)
@SuppressWarnings("unused")
public final class EmulatedPosixSupport extends PosixResources {
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

    private static final PosixFilePermission[][] otherBitsToPermission = new PosixFilePermission[][]{
                    new PosixFilePermission[]{},
                    new PosixFilePermission[]{PosixFilePermission.OTHERS_EXECUTE},
                    new PosixFilePermission[]{PosixFilePermission.OTHERS_WRITE},
                    new PosixFilePermission[]{PosixFilePermission.OTHERS_WRITE, PosixFilePermission.OTHERS_EXECUTE},
                    new PosixFilePermission[]{PosixFilePermission.OTHERS_READ},
                    new PosixFilePermission[]{PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_EXECUTE},
                    new PosixFilePermission[]{PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_WRITE},
                    new PosixFilePermission[]{PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_WRITE, PosixFilePermission.OTHERS_EXECUTE},
    };
    private static final PosixFilePermission[][] groupBitsToPermission = new PosixFilePermission[][]{
                    new PosixFilePermission[]{},
                    new PosixFilePermission[]{PosixFilePermission.GROUP_EXECUTE},
                    new PosixFilePermission[]{PosixFilePermission.GROUP_WRITE},
                    new PosixFilePermission[]{PosixFilePermission.GROUP_WRITE, PosixFilePermission.GROUP_EXECUTE},
                    new PosixFilePermission[]{PosixFilePermission.GROUP_READ},
                    new PosixFilePermission[]{PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_EXECUTE},
                    new PosixFilePermission[]{PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_WRITE},
                    new PosixFilePermission[]{PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_WRITE, PosixFilePermission.GROUP_EXECUTE},
    };
    private static final PosixFilePermission[][] ownerBitsToPermission = new PosixFilePermission[][]{
                    new PosixFilePermission[]{},
                    new PosixFilePermission[]{PosixFilePermission.OWNER_EXECUTE},
                    new PosixFilePermission[]{PosixFilePermission.OWNER_WRITE},
                    new PosixFilePermission[]{PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE},
                    new PosixFilePermission[]{PosixFilePermission.OWNER_READ},
                    new PosixFilePermission[]{PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE},
                    new PosixFilePermission[]{PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE},
                    new PosixFilePermission[]{PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE},
    };

    private static final int S_IFIFO = 0010000;
    private static final int S_IFCHR = 0020000;
    private static final int S_IFBLK = 0060000;
    private static final int S_IFSOCK = 0140000;
    private static final int S_IFLNK = 0120000;
    private static final int S_IFDIR = 0040000;
    private static final int S_IFREG = 0100000;

    private final PythonContext context;
    private int currentUmask = 0022;
    private boolean hasDefaultUmask = true;

    public EmulatedPosixSupport(PythonContext context) {
        this.context = context;
        setEnv(context.getEnv());
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public String getBackend() {
        return "java";
    }

    @ExportMessage
    @ImportStatic(ImageInfo.class)
    public static class Getpid {
        @Specialization(rewriteOn = Exception.class)
        @TruffleBoundary
        static long getPid(EmulatedPosixSupport receiver) throws Exception {
            if (ImageInfo.inImageRuntimeCode()) {
                return ProcessProperties.getProcessID();
            }
            TruffleFile statFile = receiver.context.getPublicTruffleFileRelaxed("/proc/self/stat");
            return Long.parseLong(new String(statFile.readAllBytes()).trim().split(" ")[0]);
        }

        @Specialization(replaces = "getPid")
        @TruffleBoundary
        static long getPidFallback(@SuppressWarnings("unused") EmulatedPosixSupport receiver) {
            String info = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
            return Long.parseLong(info.split("@")[0]);
        }
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public long umask(long umask) {
        long prev = currentUmask;
        currentUmask = (int) (umask & 00777);
        if (hasDefaultUmask) {
            compatibilityInfo("Returning default umask '%o' (ignoring the real umask value set in the OS)", prev);
        }
        hasDefaultUmask = false;
        return umask;
    }

    @ExportMessage
    @SuppressWarnings({"unused", "static-method"})
    public String strerror(int errorCode,
                    @Shared("errorBranch") @Cached BranchProfile errorBranch) {
        OSErrorEnum err = OSErrorEnum.fromNumber(errorCode);
        if (err == null) {
            errorBranch.enter();
            return "Invalid argument";
        }
        return err.getMessage();
    }

    @ExportMessage(name = "close")
    public int closeMessage(int fd) throws PosixException {
        // TODO: to be replaced with super.close once the super class is merged with this class
        try {
            if (!removeFD(fd)) {
                throw posixException(OSErrorEnum.EBADF);
            }
            return 0;
        } catch (IOException ignored) {
            return -1;
        }
    }

    @ExportMessage
    @SuppressWarnings({"unused", "static-method"})
    public int openAt(int dirFd, PosixPath path, int flags, int mode,
                    @Shared("errorBranch") @Cached BranchProfile errorBranch,
                    @Shared("defaultDirProfile") @Cached ConditionProfile defaultDirFdPofile) throws PosixException {
        String pathname = pathToJavaStr(path);
        TruffleFile file = resolvePath(dirFd, pathname, defaultDirFdPofile);
        Set<StandardOpenOption> options = flagsToOptions(flags);
        FileAttribute<Set<PosixFilePermission>> attributes = modeToAttributes(mode & ~currentUmask);
        try {
            return openTruffleFile(file, options, attributes);
        } catch (Exception e) {
            errorBranch.enter();
            ErrorAndMessagePair errAndMsg = OSErrorEnum.fromException(e);
            throw posixException(errAndMsg, path);
        }
    }

    @TruffleBoundary
    private int openTruffleFile(TruffleFile truffleFile, Set<StandardOpenOption> options, FileAttribute<Set<PosixFilePermission>> attributes) throws IOException {
        SeekableByteChannel fc;
        TruffleFile file;
        if (options.contains(StandardOpenOption.DELETE_ON_CLOSE)) {
            file = context.getEnv().createTempFile(truffleFile, null, null);
            options.remove(StandardOpenOption.CREATE_NEW);
            options.remove(StandardOpenOption.DELETE_ON_CLOSE);
            options.add(StandardOpenOption.CREATE);
            context.registerShutdownHook(new FileDeleteShutdownHook(file));
        } else {
            file = truffleFile;
        }
        fc = file.newByteChannel(options, attributes);
        return open(file, fc);
    }

    @ExportMessage
    public long write(int fd, Buffer data,
                    @Shared("channelClass") @Cached("createClassProfile()") ValueProfile channelClassProfile,
                    @Shared("errorBranch") @Cached BranchProfile errorBranch) throws PosixException {
        Channel channel = getFileChannel(fd, channelClassProfile);
        if (!(channel instanceof WritableByteChannel)) {
            errorBranch.enter();
            throw posixException(OSErrorEnum.EBADF);
        }
        try {
            return doWriteOp(data.getByteBuffer(), (WritableByteChannel) channel);
        } catch (Exception e) {
            errorBranch.enter();
            throw posixException(OSErrorEnum.fromException(e));
        }
    }

    @TruffleBoundary(allowInlining = true)
    private static int doWriteOp(ByteBuffer data, WritableByteChannel channel) throws IOException {
        return channel.write(data);
    }

    @ExportMessage
    @SuppressWarnings({"unused", "static-method"})
    public Buffer read(int fd, long length,
                    @Shared("channelClass") @Cached("createClassProfile()") ValueProfile channelClassProfile,
                    @Cached ReadFromChannelNode readNode) {
        Channel channel = getFileChannel(fd, channelClassProfile);
        ByteSequenceStorage array = readNode.execute(channel, (int) length);
        return new Buffer(array.getInternalByteArray(), array.length());
    }

    @Override
    @ExportMessage
    public int dup(int fd) {
        // TODO: will disappear once the super class is merged with this class
        return super.dup(fd);
    }

    @ExportMessage
    public int dup2(int fd, int fd2, @SuppressWarnings("unused") boolean inheritable) throws PosixException {
        // TODO: will merge with super.dup2 once the super class is merged with this class
        try {
            return super.dup2(fd, fd2);
        } catch (IOException ex) {
            throw posixException(OSErrorEnum.fromException(ex));
        }
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public boolean getInheritable(int fd) {
        compatibilityIgnored("getting inheritable for file descriptor %d in POSIX emulation layer (not supported, always returns false)", fd);
        return false;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public void setInheritable(int fd, boolean inheritable) {
        compatibilityIgnored("setting inheritable '%b' for file descriptor %d in POSIX emulation layer (not supported)", inheritable, fd);
    }

    @ExportMessage(name = "pipe")
    public int[] pipeMessage() throws PosixException {
        // TODO: will merge with super.pipe once the super class is merged with this class
        try {
            return super.pipe();
        } catch (IOException ex) {
            throw posixException(OSErrorEnum.fromException(ex));
        }
    }

    @ExportMessage
    public long lseek(int fd, long offset, int how,
                    @Shared("channelClass") @Cached("createClassProfile()") ValueProfile channelClassProfile,
                    @Shared("errorBranch") @Cached BranchProfile errorBranch,
                    @Exclusive @Cached ConditionProfile noFile) throws PosixException {
        Channel channel = getFileChannel(fd, channelClassProfile);
        if (noFile.profile(!(channel instanceof SeekableByteChannel))) {
            throw posixException(OSErrorEnum.ESPIPE);
        }
        SeekableByteChannel fc = (SeekableByteChannel) channel;
        try {
            return setPosition(offset, how, fc);
        } catch (Exception e) {
            errorBranch.enter();
            throw posixException(OSErrorEnum.fromException(e));
        }
    }

    @TruffleBoundary(allowInlining = true)
    private static long setPosition(long pos, int how, SeekableByteChannel fc) throws IOException {
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

    @ExportMessage(name = "ftruncate")
    public void ftruncateMessage(int fd, long length) throws PosixException {
        // TODO: will merge with super.ftruncate once the super class is merged with this class
        try {
            ftruncate(fd, length);
        } catch (Exception e) {
            throw posixException(OSErrorEnum.fromException(e));
        }
    }

    @ExportMessage(name = "fsync")
    public void fsyncMessage(int fd) throws PosixException {
        if (!fsync(fd)) {
            throw posixException(OSErrorEnum.ENOENT);
        }
    }

    @ExportMessage
    public boolean getBlocking(int fd,
                    @Shared("channelClass") @Cached("createClassProfile()") ValueProfile channelClassProfile) throws PosixException {
        PSocket socket = getSocket(fd);
        if (socket != null) {
            return SocketBuiltins.GetBlockingNode.get(socket);
        }
        Channel fileChannel = getFileChannel(fd, channelClassProfile);
        if (fileChannel instanceof SelectableChannel) {
            return getBlocking((SelectableChannel) fileChannel);
        }
        if (fileChannel == null) {
            throw posixException(OSErrorEnum.EBADFD);
        }
        // If the file channel is not selectable, we assume it to be blocking
        // Note: Truffle does not seem to provide API to get selectable channel for files
        return true;
    }

    @TruffleBoundary
    @Ignore
    private static boolean getBlocking(SelectableChannel channel) {
        return channel.isBlocking();
    }

    @ExportMessage
    @SuppressWarnings({"static-method", "unused"})
    public void setBlocking(int fd, boolean blocking,
                    @Shared("channelClass") @Cached("createClassProfile()") ValueProfile channelClassProfile) throws PosixException {
        try {
            PSocket socket = getSocket(fd);
            if (socket != null) {
                SocketBuiltins.SetBlockingNode.setBlocking(socket, blocking);
                return;
            }
            Channel fileChannel = getFileChannel(fd, channelClassProfile);
            if (fileChannel instanceof SelectableChannel) {
                setBlocking((SelectableChannel) fileChannel, blocking);
            }
            if (fileChannel != null) {
                if (blocking) {
                    // Already blocking
                    return;
                }
                throw new PosixException(OSErrorEnum.EPERM.getNumber(), "Emulated posix support does not support non-blocking mode for regular files.");
            }

        } catch (Exception e) {
            throw posixException(OSErrorEnum.fromException(e));
        }
        // if we reach this point, it's an invalid FD
        throw posixException(OSErrorEnum.EBADFD);
    }

    @TruffleBoundary
    @Ignore
    private static void setBlocking(SelectableChannel channel, boolean block) throws IOException {
        channel.configureBlocking(block);
    }

    @ExportMessage
    public int[] getTerminalSize(int fd) throws PosixException {
        if (getFileChannel(fd) == null) {
            throw posixException(OSErrorEnum.EBADF);
        }
        return new int[]{context.getOption(PythonOptions.TerminalWidth), context.getOption(PythonOptions.TerminalHeight)};
    }

    @ExportMessage
    public long[] fstatAt(int dirFd, PosixPath path, boolean followSymlinks,
                    @Shared("errorBranch") @Cached BranchProfile errorBranch,
                    @Shared("defaultDirProfile") @Cached ConditionProfile defaultDirFdPofile) throws PosixException {
        String pathname = pathToJavaStr(path);
        TruffleFile f = resolvePath(dirFd, pathname, defaultDirFdPofile);
        LinkOption[] linkOptions = followSymlinks ? new LinkOption[0] : new LinkOption[]{LinkOption.NOFOLLOW_LINKS};
        try {
            return fstat(f, linkOptions);
        } catch (Exception e) {
            errorBranch.enter();
            ErrorAndMessagePair errAndMsg = OSErrorEnum.fromException(e);
            throw posixException(errAndMsg, path);
        }
    }

    @ExportMessage
    public long[] fstat(int fd, Object originalPath, boolean handleEintr,
                    @Exclusive @Cached BranchProfile nullPathProfile,
                    @Shared("channelClass") @Cached("createClassProfile()") ValueProfile channelClassProfile,
                    @Shared("errorBranch") @Cached BranchProfile errorBranch,
                    @Shared("defaultDirProfile") @Cached ConditionProfile defaultDirFdPofile) throws PosixException {
        String path = getFilePath(fd);
        if (path == null) {
            nullPathProfile.enter();
            Channel fileChannel = getFileChannel(fd, channelClassProfile);
            if (fileChannel == null) {
                errorBranch.enter();
                throw posixException(OSErrorEnum.EBADF);
            }
            return fstatWithoutPath(fileChannel);
        }
        TruffleFile f = context.getPublicTruffleFileRelaxed(path);
        try {
            return fstat(f, new LinkOption[0]);
        } catch (Exception e) {
            errorBranch.enter();
            ErrorAndMessagePair errAndMsg = OSErrorEnum.fromException(e);
            throw posixException(errAndMsg, originalPath);
        }
    }

    private static long[] fstatWithoutPath(Channel fileChannel) {
        int mode = 0;
        if (fileChannel instanceof ReadableByteChannel) {
            mode |= 0444;
        }
        if (fileChannel instanceof WritableByteChannel) {
            mode |= 0222;
        }
        long[] res = new long[13];
        res[0] = mode;
        return res;
    }

    @Ignore
    @TruffleBoundary
    private long[] fstat(TruffleFile f, LinkOption[] linkOptions) throws IOException {
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

    private static long[] unixStat(TruffleFile file, LinkOption... linkOptions) throws IOException {
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
        return setTimestamps(attributes, new long[]{
                        attributes.get(UNIX_MODE),
                        attributes.get(UNIX_INODE),
                        attributes.get(UNIX_DEV),
                        attributes.get(UNIX_NLINK),
                        attributes.get(UNIX_UID),
                        attributes.get(UNIX_GID),
                        attributes.get(SIZE),
                        // dummy values will be filled in by setTimestamps
                        0, 0, 0, 0, 0, 0
        });
    }

    private long[] posixStat(TruffleFile file, LinkOption... linkOptions) throws IOException {
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
        final Set<PosixFilePermission> posixFilePermissions = attributes.get(UNIX_PERMISSIONS);
        return setTimestamps(attributes, new long[]{
                        posixPermissionsToMode(fileTypeBitsFromAttributes(attributes), posixFilePermissions),
                        getInode(file), // ino
                        0, // dev
                        0, // nlink
                        getPrincipalId(attributes.get(UNIX_OWNER)),
                        getPrincipalId(attributes.get(UNIX_GROUP)),
                        attributes.get(SIZE),
                        // dummy values will be filled in by setTimestamps
                        0, 0, 0, 0, 0, 0
        });
    }

    private long[] basicStat(TruffleFile file, LinkOption... linkOptions) throws IOException {
        TruffleFile.Attributes attributes = file.getAttributes(Arrays.asList(
                        IS_DIRECTORY,
                        IS_SYMBOLIC_LINK,
                        IS_REGULAR_FILE,
                        LAST_MODIFIED_TIME,
                        LAST_ACCESS_TIME,
                        CREATION_TIME,
                        SIZE), linkOptions);
        int mode = fileTypeBitsFromAttributes(attributes);
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
        return setTimestamps(attributes, new long[]{
                        mode,
                        inode, // ino
                        0, // dev
                        0, // nlink
                        0,
                        0,
                        attributes.get(SIZE),
                        // dummy values will be filled in by setTimestamps
                        0, 0, 0, 0, 0, 0
        });
    }

    private static long[] setTimestamps(Attributes attributes, long[] statResult) {
        FileTime atime = attributes.get(LAST_ACCESS_TIME);
        FileTime mtime = attributes.get(LAST_MODIFIED_TIME);
        FileTime ctime = attributes.get(UNIX_CTIME);
        statResult[7] = fileTimeToSeconds(atime);
        statResult[8] = fileTimeToSeconds(mtime);
        statResult[9] = fileTimeToSeconds(ctime);
        statResult[10] = fileTimeNanoSecondsPart(atime);
        statResult[11] = fileTimeNanoSecondsPart(mtime);
        statResult[12] = fileTimeNanoSecondsPart(ctime);
        return statResult;
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
        return context.getResources().getInodeId(canonical.getPath());
    }

    @TruffleBoundary(allowInlining = true)
    private static long getPrincipalId(UserPrincipal principal) {
        if (principal instanceof UnixNumericGroupPrincipal) {
            try {
                return Long.decode(principal.getName());
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
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

    @ExportMessage
    @SuppressWarnings("static-method")
    public Object[] uname() {
        return new Object[]{PythonUtils.getPythonOSName(), getHostName(),
                        getOsVersion(), "", PythonUtils.getPythonArch()};
    }

    @TruffleBoundary
    private static String getOsVersion() {
        return System.getProperty("os.version", "");
    }

    @TruffleBoundary
    private static String getHostName() {
        try {
            InetAddress addr;
            addr = InetAddress.getLocalHost();
            return addr.getHostName();
        } catch (UnknownHostException | SecurityException ignore) {
            return "";
        }
    }

    @ExportMessage
    public void unlinkAt(int dirFd, PosixPath path, @SuppressWarnings("unused") boolean rmdir,
                    @Shared("errorBranch") @Cached BranchProfile errorBranch,
                    @Shared("defaultDirProfile") @Cached ConditionProfile defaultDirFdPofile) throws PosixException {
        // TODO handle rmdir parameter
        String pathname = pathToJavaStr(path);
        TruffleFile f = resolvePath(dirFd, pathname, defaultDirFdPofile);
        try {
            f.delete();
        } catch (Exception e) {
            errorBranch.enter();
            throw posixException(OSErrorEnum.fromException(e), path);
        }
    }

    @ExportMessage
    public void symlinkAt(PosixPath target, int linkDirFd, PosixPath link,
                    @Shared("errorBranch") @Cached BranchProfile errorBranch,
                    @Shared("defaultDirProfile") @Cached ConditionProfile defaultDirFdPofile) throws PosixException {
        String linkPath = pathToJavaStr(link);
        TruffleFile linkFile = resolvePath(linkDirFd, linkPath, defaultDirFdPofile);
        String targetPath = pathToJavaStr(target);
        TruffleFile targetFile = context.getEnv().getPublicTruffleFile(targetPath);
        try {
            linkFile.createSymbolicLink(targetFile);
        } catch (Exception e) {
            errorBranch.enter();
            throw posixException(OSErrorEnum.fromException(e), target, link);
        }
    }

    @ExportMessage
    @SuppressWarnings({"static-method", "unused"})
    public void mkdirAt(int dirFd, PosixPath pathname, int mode) {
        throw CompilerDirectives.shouldNotReachHere("Not implemented");
    }

    @ExportMessage
    @SuppressWarnings({"static-method", "unused"})
    public Object getcwd() {
        // dummy implementation needed for setUp/tearDown in test_posix.py
        return "<dummy>";
    }

    @ExportMessage
    @SuppressWarnings({"static-method", "unused"})
    public void chdir(PosixPath path) {
        // dummy implementation needed for setUp/tearDown in test_posix.py
        if (!"<dummy>".equals(path.value)) {
            throw CompilerDirectives.shouldNotReachHere("Not implemented");
        }
    }

    @ExportMessage
    @SuppressWarnings({"static-method", "unused"})
    public void fchdir(int fd, Object filename, boolean handleEintr) {
        throw CompilerDirectives.shouldNotReachHere("Not implemented");
    }

    @ExportMessage
    @SuppressWarnings({"static-method", "unused"})
    public boolean isatty(int fd) {
        throw CompilerDirectives.shouldNotReachHere("Not implemented");
    }

    // ------------------
    // Path conversions

    @ExportMessage
    @SuppressWarnings("static-method")
    public Object createPathFromString(String path) {
        return checkEmbeddedNulls(path);
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    @TruffleBoundary
    public Object createPathFromBytes(byte[] path) {
        return checkEmbeddedNulls(new String(path, StandardCharsets.UTF_8));
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public String getPathAsString(Object path) {
        return (String) path;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    @TruffleBoundary
    public PBytes getPathAsBytes(Object path, PythonObjectFactory factory) {
        return factory.createBytes(((String) path).getBytes(StandardCharsets.UTF_8));
    }

    private static String checkEmbeddedNulls(String s) {
        for (int i = 0; i < s.length(); ++i) {
            if (s.charAt(i) == 0) {
                return null;
            }
        }
        return s;
    }

    // ------------------
    // Helpers

    static PosixException posixException(OSErrorEnum osError) throws PosixException {
        throw new PosixException(osError.getNumber(), osError.getMessage());
    }

    static PosixException posixException(ErrorAndMessagePair pair, PosixPath path1) throws PosixException {
        throw new PosixException(pair.oserror.getNumber(), pair.message, path1.originalObject);
    }

    static PosixException posixException(ErrorAndMessagePair pair, PosixPath path1, PosixPath path2) throws PosixException {
        throw new PosixException(pair.oserror.getNumber(), pair.message, path1.originalObject, path2.originalObject);
    }

    static PosixException posixException(ErrorAndMessagePair pair, Object path1) throws PosixException {
        throw new PosixException(pair.oserror.getNumber(), pair.message, path1);
    }

    private static PosixException posixException(ErrorAndMessagePair pair) throws PosixException {
        throw new PosixException(pair.oserror.getNumber(), pair.message);
    }

    @TruffleBoundary
    static long fileTimeToSeconds(FileTime t) {
        return t.to(TimeUnit.SECONDS);
    }

    @TruffleBoundary
    static long fileTimeNanoSecondsPart(FileTime t) {
        return t.toInstant().getNano();
    }

    /**
     * Resolves the path relative to the directory given as file descriptor. Honors the
     * {@link PosixSupportLibrary#DEFAULT_DIR_FD}.
     */
    private TruffleFile resolvePath(int dirFd, String pathname, ConditionProfile defaultDirFdPofile) throws PosixException {
        if (defaultDirFdPofile.profile(dirFd == PosixSupportLibrary.DEFAULT_DIR_FD)) {
            return context.getPublicTruffleFileRelaxed(pathname, PythonLanguage.DEFAULT_PYTHON_EXTENSIONS);
        } else {
            String dirPath = getFilePathOrDefault(dirFd, null);
            if (dirPath == null) {
                throw posixException(OSErrorEnum.EBADF);
            }
            TruffleFile dir = context.getPublicTruffleFileRelaxed(dirPath, PythonLanguage.DEFAULT_PYTHON_EXTENSIONS);
            return dir.resolve(pathname);
        }
    }

    private static String pathToJavaStr(PosixPath path) {
        return (String) path.value;
    }

    @TruffleBoundary(allowInlining = true)
    private String getFilePathOrDefault(int fd, String defaultValue) {
        return filePaths.getOrDefault(fd, defaultValue);
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

    private static final TruffleLogger COMPATIBILITY_LOGGER = PythonLanguage.getCompatibilityLogger(EmulatedPosixSupport.class);

    @TruffleBoundary
    public static void compatibilityInfo(String fmt, Object arg) {
        if (COMPATIBILITY_LOGGER.isLoggable(Level.FINER)) {
            COMPATIBILITY_LOGGER.log(Level.FINER, String.format(fmt, arg));
        }
    }

    @TruffleBoundary
    public static void compatibilityIgnored(String fmt, Object... args) {
        if (COMPATIBILITY_LOGGER.isLoggable(Level.FINE)) {
            COMPATIBILITY_LOGGER.log(Level.FINE, "Ignored: " + String.format(fmt, args));
        }
    }
}
