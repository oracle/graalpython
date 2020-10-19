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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.ProcessProperties;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum.ErrorAndMessagePair;
import com.oracle.graal.python.nodes.util.ChannelNodes.ReadFromChannelNode;
import com.oracle.graal.python.runtime.PosixSupportLibrary.Buffer;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixException;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixPath;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.util.FileDeleteShutdownHook;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;

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

    private final PythonContext context;
    private int currentUmask = 0022;
    private boolean hasDefaultUmask = true;

    public EmulatedPosixSupport(PythonContext context) {
        this.context = context;
        setEnv(context.getEnv());
    }

    @ExportMessage
    @ImportStatic(ImageInfo.class)
    public static class Getpid {
        @TruffleBoundary
        @Specialization(guards = "inImageRuntimeCode()")
        static long inNativeImage(@SuppressWarnings("unused") EmulatedPosixSupport receiver) {
            return ProcessProperties.getProcessID();
        }

        @TruffleBoundary
        @Specialization(guards = "!inImageRuntimeCode()", rewriteOn = Exception.class)
        static long usingProc(@SuppressWarnings("unused") EmulatedPosixSupport receiver,
                        @CachedContext(PythonLanguage.class) ContextReference<PythonContext> ctxRef) throws Exception {
            TruffleFile statFile = ctxRef.get().getPublicTruffleFileRelaxed("/proc/self/stat");
            return Long.parseLong(new String(statFile.readAllBytes()).trim().split(" ")[0]);
        }

        @TruffleBoundary
        @Specialization(guards = "!inImageRuntimeCode()", replaces = "usingProc")
        static long usingMXBean(@SuppressWarnings("unused") EmulatedPosixSupport receiver) {
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
                    @Exclusive @Cached ConditionProfile defaultDirFdPofile,
                    @Cached PathToJavaStr pathToJavaStr) throws PosixException {
        String pathname = pathToJavaStr.execute(path);
        TruffleFile file = resolvePath(dirFd, pathname, defaultDirFdPofile);
        Set<StandardOpenOption> options = flagsToOptions(flags);
        FileAttribute<Set<PosixFilePermission>> attributes = modeToAttributes(mode & ~currentUmask);
        try {
            return openTruffleFile(file, options, attributes);
        } catch (Exception e) {
            errorBranch.enter();
            ErrorAndMessagePair errAndMsg = OSErrorEnum.fromException(e);
            throw posixException(errAndMsg.oserror, errAndMsg.message, path);
        }
    }

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
    public void fsyncMessage(int fd) {
        throw CompilerDirectives.shouldNotReachHere("Not implemented");
    }

    @ExportMessage
    public boolean getBlocking(int fd) {
        throw CompilerDirectives.shouldNotReachHere("Not implemented");
    }

    @ExportMessage
    public void setBlocking(int fd, boolean blocking) {
        throw CompilerDirectives.shouldNotReachHere("Not implemented");
    }

    // ------------------
    // Helpers

    static PosixException posixException(OSErrorEnum osError) throws PosixException {
        throw new PosixException(osError.getNumber(), osError.getMessage());
    }

    static PosixException posixException(OSErrorEnum osError, String customMessage, PosixPath path1) throws PosixException {
        throw new PosixException(osError.getNumber(), customMessage, path1.originalObject);
    }

    private static PosixException posixException(ErrorAndMessagePair pair) throws PosixException {
        throw new PosixException(pair.oserror.getNumber(), pair.message);
    }

    @GenerateUncached
    abstract static class PathToJavaStr extends Node {
        public abstract String execute(PosixPath path);

        @Specialization(guards = "path.originalString != null")
        String doString(PosixPath path) {
            return path.originalString;
        }

        @Fallback
        @TruffleBoundary
        String doBytes(PosixPath path) {
            return new String(path.path, StandardCharsets.UTF_8);
        }
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
        if (COMPATIBILITY_LOGGER.isLoggable(Level.INFO)) {
            COMPATIBILITY_LOGGER.info(String.format(fmt, arg));
        }
    }

    @TruffleBoundary
    public static void compatibilityIgnored(String fmt, Object... args) {
        if (COMPATIBILITY_LOGGER.isLoggable(Level.WARNING)) {
            COMPATIBILITY_LOGGER.warning("Ignored: " + String.format(fmt, args));
        }
    }
}
