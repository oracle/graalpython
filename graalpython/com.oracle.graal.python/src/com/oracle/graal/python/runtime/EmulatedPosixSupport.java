/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.runtime.PosixSupportLibrary.MAP_ANONYMOUS;
import static com.oracle.graal.python.runtime.PosixSupportLibrary.PROT_EXEC;
import static com.oracle.graal.python.runtime.PosixSupportLibrary.PROT_NONE;
import static com.oracle.graal.python.runtime.PosixSupportLibrary.PROT_READ;
import static com.oracle.graal.python.runtime.PosixSupportLibrary.PROT_WRITE;
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
import static java.lang.Math.addExact;
import static java.lang.Math.multiplyExact;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.WritableByteChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.LinkOption;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.ProcessProperties;
import org.graalvm.polyglot.io.ProcessHandler.Redirect;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.bytes.BytesUtils;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum.ErrorAndMessagePair;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.socket.PSocket;
import com.oracle.graal.python.builtins.objects.socket.SocketBuiltins;
import com.oracle.graal.python.builtins.objects.socket.SocketUtils;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.expression.IsExpressionNode.IsNode;
import com.oracle.graal.python.nodes.util.ChannelNodes.ReadFromChannelNode;
import com.oracle.graal.python.runtime.PosixSupportLibrary.Buffer;
import com.oracle.graal.python.runtime.PosixSupportLibrary.ChannelNotSelectableException;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixException;
import com.oracle.graal.python.runtime.PosixSupportLibrary.SelectResult;
import com.oracle.graal.python.runtime.PosixSupportLibrary.Timeval;
import com.oracle.graal.python.runtime.PosixSupportLibrary.UnsupportedPosixFeatureException;
import com.oracle.graal.python.runtime.exception.PythonExitException;
import com.oracle.graal.python.runtime.sequence.storage.ByteSequenceStorage;
import com.oracle.graal.python.util.FileDeleteShutdownHook;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleFile.Attributes;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.io.TruffleProcessBuilder;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.library.ExportMessage.Ignore;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.sun.security.auth.UnixNumericGroupPrincipal;

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
 * <li>Fork does not actually fork the process, any arguments related to resources inheritance for
 * the child process, are silently ignored.</li>
 * <li>All file descriptors are not inheritable (newly spawned processes will not see them).</li>
 * <li>When using {@code scandir}, some attributes of the directory entries that are fetched eagerly
 * when accessing the next entry by the native POSIX implementation are queried lazily by the
 * emulated implementation. This may result in observable difference in behavior if the attributes
 * change in between the access of the next entry and the query for the value of such attribute.
 * </li>
 * <li>Resolution of file access/modification times depends on the JDK and the best we can guarantee
 * is seconds resolution. Some operations may even override nano seconds component of, e.g., access
 * time, when updating the modification time.</li>
 * <li>{@code faccessAt} does not support: effective IDs, and no follow symlinks unless the mode is
 * only F_OK.</li>
 * <li>{@code select} supports only network sockets, but not regular files.</li>
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
    private final ConcurrentHashMap<String, String> environ = new ConcurrentHashMap<>();
    private int currentUmask = 0022;
    private boolean hasDefaultUmask = true;

    public EmulatedPosixSupport(PythonContext context) {
        this.context = context;
        setEnv(context.getEnv());
    }

    @Override
    public void postInitialize() {
        environ.putAll(System.getenv());
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
    public int umask(int umask) {
        int prev = currentUmask;
        currentUmask = umask & 00777;
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
    public int openat(int dirFd, Object path, int flags, int mode,
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
            throw posixException(errAndMsg);
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
            context.registerAtexitHook(new FileDeleteShutdownHook(file));
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
                    @Cached ReadFromChannelNode readNode,
                    @Shared("errorBranch") @Cached BranchProfile errorBranch) throws PosixException {
        Channel channel = getFileChannel(fd, channelClassProfile);
        if (channel == null) {
            errorBranch.enter();
            throw posixException(OSErrorEnum.EBADF);
        }
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
    @TruffleBoundary
    public SelectResult select(int[] readfds, int[] writefds, int[] errorfds, Timeval timeout) throws PosixException {
        SelectableChannel[] readChannels = getSelectableChannels(readfds);
        SelectableChannel[] writeChannels = getSelectableChannels(writefds);
        // Java doesn't support any exceptional conditions we could apply on errfds, report a
        // warning if errfds is not a subset of readfds & writefds
        errfdsCheck: for (int fd : errorfds) {
            for (int fd2 : readfds) {
                if (fd == fd2) {
                    continue errfdsCheck;
                }
            }
            for (int fd2 : writefds) {
                if (fd == fd2) {
                    continue errfdsCheck;
                }
            }
            compatibilityIgnored("POSIX emultaion layer doesn't support waiting on exceptional conditions in select()");
            break;
        }

        boolean[] wasBlocking = new boolean[readChannels.length + writeChannels.length];
        int i = 0;

        for (SelectableChannel channel : readChannels) {
            wasBlocking[i++] = channel.isBlocking();
        }
        for (SelectableChannel channel : writeChannels) {
            wasBlocking[i++] = channel.isBlocking();
        }

        final int readOps = SelectionKey.OP_READ | SelectionKey.OP_ACCEPT;
        final int writeOps = SelectionKey.OP_WRITE;

        try (Selector selector = Selector.open()) {
            for (SelectableChannel channel : readChannels) {
                channel.configureBlocking(false);
                channel.register(selector, readOps & channel.validOps());
            }

            for (SelectableChannel channel : writeChannels) {
                channel.configureBlocking(false);
                channel.register(selector, writeOps);
            }

            // IMPORTANT: The meaning of the timeout value is slightly different: 'timeout == 0.0'
            // means we should not block and return immediately, for which we use selectNow().
            // 'timeout == None' means we should wait indefinitely, i.e., we need to pass 0 to the
            // Java API.
            long timeoutMs;
            boolean useSelectNow = false;
            if (timeout == null) {
                timeoutMs = 0;
            } else {
                try {
                    timeoutMs = addExact(multiplyExact(timeout.getSeconds(), 1000L), timeout.getMicroseconds() / 1000L);
                } catch (ArithmeticException ex) {
                    throw posixException(OSErrorEnum.EINVAL);
                }
                if (timeoutMs == 0) {
                    useSelectNow = true;
                }
            }
            int selected = useSelectNow ? selector.selectNow() : selector.select(timeoutMs);

            // remove non-selected channels from given lists
            boolean[] resReadfds = createSelectedMap(readfds, readChannels, selector, readOps);
            boolean[] resWritefds = createSelectedMap(writefds, writeChannels, selector, writeOps);
            boolean[] resErrfds = new boolean[errorfds.length];

            assert selected == countSelected(resReadfds) + countSelected(resWritefds) + countSelected(resErrfds);
            return new SelectResult(resReadfds, resWritefds, resErrfds);
        } catch (IOException e) {
            throw posixException(OSErrorEnum.fromException(e));
        } finally {
            i = 0;
            try {
                for (SelectableChannel channel : readChannels) {
                    if (wasBlocking[i++]) {
                        channel.configureBlocking(true);
                    }
                }
                for (SelectableChannel channel : writeChannels) {
                    if (wasBlocking[i++]) {
                        channel.configureBlocking(true);
                    }
                }
            } catch (IOException e) {
                // We didn't manage to restore the blocking status, ignore
            }
        }
    }

    private static boolean[] createSelectedMap(int[] fds, SelectableChannel[] channels, Selector selector, int op) {
        boolean[] result = new boolean[fds.length];
        for (int i = 0; i < channels.length; i++) {
            SelectableChannel channel = channels[i];
            SelectionKey selectionKey = channel.keyFor(selector);
            result[i] = (selectionKey.readyOps() & op) != 0;
        }
        return result;
    }

    private static int countSelected(boolean[] selected) {
        int res = 0;
        for (boolean b : selected) {
            if (b) {
                res += 1;
            }
        }
        return res;
    }

    private SelectableChannel[] getSelectableChannels(int[] fds) throws PosixException {
        SelectableChannel[] channels = new SelectableChannel[fds.length];
        for (int i = 0; i < fds.length; i++) {
            Channel ch = getFileChannel(fds[i]);
            if (ch == null) {
                throw posixException(OSErrorEnum.EBADF);
            }
            if (ch instanceof SelectableChannel) {
                channels[i] = (SelectableChannel) ch;
            } else if (ch instanceof PSocket) {
                PSocket socket = (PSocket) ch;
                if (socket.getSocket() != null) {
                    channels[i] = socket.getSocket();
                } else if (socket.getServerSocket() != null) {
                    channels[i] = socket.getServerSocket();
                } else {
                    throw posixException(OSErrorEnum.EBADF);
                }
            } else {
                throw ChannelNotSelectableException.INSTANCE;
            }
        }
        return channels;
    }

    @ExportMessage
    public long lseek(int fd, long offset, int how,
                    @Shared("channelClass") @Cached("createClassProfile()") ValueProfile channelClassProfile,
                    @Shared("errorBranch") @Cached BranchProfile errorBranch,
                    @Exclusive @Cached ConditionProfile notSupported,
                    @Exclusive @Cached ConditionProfile noFile,
                    @Exclusive @Cached ConditionProfile notSeekable) throws PosixException {
        Channel channel = getFileChannel(fd, channelClassProfile);
        if (noFile.profile(channel == null)) {
            throw posixException(OSErrorEnum.EBADF);
        }
        if (notSeekable.profile(!(channel instanceof SeekableByteChannel))) {
            throw posixException(OSErrorEnum.ESPIPE);
        }
        SeekableByteChannel fc = (SeekableByteChannel) channel;
        long newPos;
        try {
            newPos = setPosition(offset, how, fc);
        } catch (Exception e) {
            errorBranch.enter();
            throw posixException(OSErrorEnum.fromException(e));
        }
        if (notSupported.profile(newPos == -1)) {
            throw new UnsupportedPosixFeatureException("emulated lseek cannot seek beyond the file size. " +
                            "Please enable native posix support using " +
                            "the following option '--python.PosixModuleBackend=native'");
        }
        return newPos;
    }

    /*-
     * There are two main differences between emulated lseek and native lseek: 
     *      1- native lseek allows setting position beyond file size.
     *      2- native lseek current position doesn't change after file truncate, i.e. position stays beyond file size.
     * XXX: we do not currently track the later case, which might produce inconsistent results compare to native lseek.
     */
    @TruffleBoundary(allowInlining = true)
    private static long setPosition(long pos, int how, SeekableByteChannel fc) throws IOException {
        long newPos = pos;
        switch (how) {
            case SEEK_CUR:
                newPos += fc.position();
                break;
            case SEEK_END:
                newPos += fc.size();
                break;
            case SEEK_SET:
                break;
            default:
                throw new IllegalArgumentException();
        }
        fc.position(newPos);
        long p = fc.position();
        return p != newPos ? -1 : p;
    }

    @ExportMessage(name = "ftruncate")
    public void ftruncateMessage(int fd, long length,
                    @Shared("errorBranch") @Cached BranchProfile errorBranch) throws PosixException {
        // TODO: will merge with super.ftruncate once the super class is merged with this class
        Object ret;
        try {
            ret = ftruncate(fd, length);
        } catch (Exception e) {
            throw posixException(OSErrorEnum.fromException(e));
        }
        if (ret == null) {
            errorBranch.enter();
            throw posixException(OSErrorEnum.EBADF);
        }
    }

    @ExportMessage(name = "fsync")
    public void fsyncMessage(int fd) throws PosixException {
        if (!fsync(fd)) {
            throw posixException(OSErrorEnum.ENOENT);
        }
    }

    @ExportMessage
    final void flock(int fd, int operation,
                    @Shared("errorBranch") @Cached BranchProfile errorBranch,
                    @Shared("channelClass") @Cached("createClassProfile()") ValueProfile channelClassProfile) throws PosixException {
        Channel channel = getFileChannel(fd, channelClassProfile);
        if (channel == null) {
            errorBranch.enter();
            throw posixException(OSErrorEnum.EBADFD);
        }
        // TODO: support other types, throw unsupported feature exception otherwise (GR-28740)
        if (channel instanceof FileChannel) {
            FileChannel fc = (FileChannel) channel;
            FileLock lock = getFileLock(fd);
            try {
                lock = doLockOperation(operation, fc, lock);
            } catch (IOException e) {
                throw posixException(OSErrorEnum.fromException(e));
            }
            setFileLock(fd, lock);
        }
    }

    @TruffleBoundary
    private static FileLock doLockOperation(int operation, FileChannel fc, FileLock oldLock) throws IOException {
        FileLock lock = oldLock;
        if (lock == null) {
            if ((operation & PosixSupportLibrary.LOCK_SH) != 0) {
                if ((operation & PosixSupportLibrary.LOCK_NB) != 0) {
                    lock = fc.tryLock(0, Long.MAX_VALUE, true);
                } else {
                    lock = fc.lock(0, Long.MAX_VALUE, true);
                }
            } else if ((operation & PosixSupportLibrary.LOCK_EX) != 0) {
                if ((operation & PosixSupportLibrary.LOCK_NB) != 0) {
                    lock = fc.tryLock();
                } else {
                    lock = fc.lock();
                }
            } else {
                // not locked, that's ok
            }
        } else {
            if ((operation & PosixSupportLibrary.LOCK_UN) != 0) {
                lock.release();
                lock = null;
            } else if ((operation & PosixSupportLibrary.LOCK_EX) != 0) {
                if (lock.isShared()) {
                    if ((operation & PosixSupportLibrary.LOCK_NB) != 0) {
                        FileLock newLock = fc.tryLock();
                        if (newLock != null) {
                            lock = newLock;
                        }
                    } else {
                        lock = fc.lock();
                    }
                }
            } else {
                // we already have a suitable lock
            }
        }
        return lock;
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
                SocketUtils.setBlocking(socket, blocking);
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

        } catch (PosixException e) {
            throw e;
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
    public long[] fstatat(int dirFd, Object path, boolean followSymlinks,
                    @Shared("errorBranch") @Cached BranchProfile errorBranch,
                    @Shared("defaultDirProfile") @Cached ConditionProfile defaultDirFdPofile) throws PosixException {
        String pathname = pathToJavaStr(path);
        TruffleFile f = resolvePath(dirFd, pathname, defaultDirFdPofile);
        LinkOption[] linkOptions = getLinkOptions(followSymlinks);
        try {
            return fstat(f, linkOptions);
        } catch (Exception e) {
            errorBranch.enter();
            ErrorAndMessagePair errAndMsg = OSErrorEnum.fromException(e);
            throw posixException(errAndMsg);
        }
    }

    @ExportMessage
    public long[] fstat(int fd,
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
        TruffleFile f = getTruffleFile(path);
        try {
            return fstat(f, new LinkOption[0]);
        } catch (Exception e) {
            errorBranch.enter();
            ErrorAndMessagePair errAndMsg = OSErrorEnum.fromException(e);
            throw posixException(errAndMsg);
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
                        getEmulatedInode(file), // ino
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
        int inode = getEmulatedInode(file);
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

    private int getEmulatedInode(TruffleFile file) {
        compatibilityInfo("Using artificial emulated inode number for file '%s'", file);
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
    public void unlinkat(int dirFd, Object path, @SuppressWarnings("unused") boolean rmdir,
                    @Shared("errorBranch") @Cached BranchProfile errorBranch,
                    @Shared("defaultDirProfile") @Cached ConditionProfile defaultDirFdPofile) throws PosixException {
        String pathname = pathToJavaStr(path);
        TruffleFile f = resolvePath(dirFd, pathname, defaultDirFdPofile);
        if (f.exists(LinkOption.NOFOLLOW_LINKS)) {
            // we cannot check this if the file does not exist
            boolean isDirectory = f.isDirectory(LinkOption.NOFOLLOW_LINKS);
            if (isDirectory != rmdir) {
                errorBranch.enter();
                throw posixException(isDirectory ? OSErrorEnum.EISDIR : OSErrorEnum.ENOTDIR);
            }
        }
        try {
            f.delete();
        } catch (Exception e) {
            errorBranch.enter();
            throw posixException(OSErrorEnum.fromException(e));
        }
    }

    @ExportMessage
    public void symlinkat(Object target, int linkDirFd, Object link,
                    @Shared("errorBranch") @Cached BranchProfile errorBranch,
                    @Shared("defaultDirProfile") @Cached ConditionProfile defaultDirFdPofile) throws PosixException {
        String linkPath = pathToJavaStr(link);
        TruffleFile linkFile = resolvePath(linkDirFd, linkPath, defaultDirFdPofile);
        String targetPath = pathToJavaStr(target);
        TruffleFile targetFile = getTruffleFile(targetPath);
        try {
            linkFile.createSymbolicLink(targetFile);
        } catch (Exception e) {
            errorBranch.enter();
            throw posixException(OSErrorEnum.fromException(e));
        }
    }

    @ExportMessage
    public void mkdirat(int dirFd, Object path, int mode,
                    @Shared("errorBranch") @Cached BranchProfile errorBranch,
                    @Shared("defaultDirProfile") @Cached ConditionProfile defaultDirFdPofile) throws PosixException {
        String pathStr = pathToJavaStr(path);
        TruffleFile linkFile = resolvePath(dirFd, pathStr, defaultDirFdPofile);
        try {
            linkFile.createDirectory();
        } catch (Exception e) {
            errorBranch.enter();
            throw posixException(OSErrorEnum.fromException(e));
        }
    }

    @ExportMessage
    public Object getcwd() {
        return context.getEnv().getCurrentWorkingDirectory().toString();
    }

    @ExportMessage
    public void chdir(Object path,
                    @Shared("errorBranch") @Cached BranchProfile errorBranch) throws PosixException {
        chdirStr(pathToJavaStr(path), errorBranch);
    }

    @ExportMessage
    public void fchdir(int fd,
                    @Shared("errorBranch") @Cached BranchProfile errorBranch,
                    @Exclusive @Cached BranchProfile asyncProfile) throws PosixException {
        String path = getFilePath(fd);
        if (path == null) {
            errorBranch.enter();
            throw posixException(OSErrorEnum.EBADF);
        }
        chdirStr(path, errorBranch);
    }

    private void chdirStr(String pathStr, BranchProfile errorBranch) throws PosixException {
        TruffleFile truffleFile = getTruffleFile(pathStr).getAbsoluteFile();
        if (!truffleFile.exists()) {
            errorBranch.enter();
            throw posixException(OSErrorEnum.ENOENT);
        }
        if (!truffleFile.isDirectory()) {
            errorBranch.enter();
            throw posixException(OSErrorEnum.ENOTDIR);
        }
        try {
            context.getEnv().setCurrentWorkingDirectory(truffleFile);
        } catch (IllegalArgumentException ignored) {
            errorBranch.enter();
            throw posixException(OSErrorEnum.ENOENT);
        } catch (SecurityException ignored) {
            errorBranch.enter();
            throw posixException(OSErrorEnum.EACCES);
        } catch (UnsupportedOperationException ignored) {
            errorBranch.enter();
            String msg = "The filesystem does not support changing of the current working directory";
            throw posixException(new ErrorAndMessagePair(OSErrorEnum.EIO, msg));
        }
    }

    @ExportMessage
    @TruffleBoundary
    public boolean isatty(int fd) {
        if (isStandardStream(fd)) {
            return context.getOption(PythonOptions.TerminalIsInteractive);
        } else {
            // These two files are explicitly specified by POSIX
            String path = getFilePath(fd);
            return path != null && (path.equals("/dev/tty") || path.equals("/dev/console"));
        }
    }

    private static final class EmulatedDirStream {
        final DirectoryStream<TruffleFile> dirStream;
        final Iterator<TruffleFile> iterator;

        private EmulatedDirStream(DirectoryStream<TruffleFile> dirStream) {
            this.dirStream = dirStream;
            iterator = getIterator(dirStream);
        }

        @TruffleBoundary
        static Iterator<TruffleFile> getIterator(DirectoryStream<TruffleFile> dirStream) {
            return dirStream.iterator();
        }
    }

    @ExportMessage
    public Object opendir(Object path,
                    @Shared("errorBranch") @Cached BranchProfile errorBranch,
                    @Shared("defaultDirProfile") @Cached ConditionProfile defaultDirFdPofile) throws PosixException {
        return opendirImpl(pathToJavaStr(path), errorBranch);
    }

    @ExportMessage
    public Object fdopendir(int fd,
                    @Shared("errorBranch") @Cached BranchProfile errorBranch) throws PosixException {
        String path = getFilePath(fd);
        if (path == null) {
            errorBranch.enter();
            throw posixException(OSErrorEnum.ENOENT);
        }
        return opendirImpl(path, errorBranch);
    }

    private EmulatedDirStream opendirImpl(String path, BranchProfile errorBranch) throws PosixException {
        TruffleFile file = getTruffleFile(path);
        try {
            return new EmulatedDirStream(file.newDirectoryStream());
        } catch (IOException e) {
            errorBranch.enter();
            throw posixException(OSErrorEnum.fromException(e));
        }
    }

    @ExportMessage
    @TruffleBoundary
    @SuppressWarnings("static-method")
    public void closedir(Object dirStreamObj,
                    @Shared("errorBranch") @Cached BranchProfile errorBranch) {
        EmulatedDirStream dirStream = (EmulatedDirStream) dirStreamObj;
        try {
            dirStream.dirStream.close();
        } catch (IOException e) {
            errorBranch.enter();
            LOGGER.log(Level.WARNING, "Closing a directory threw an exception", e);
        }
    }

    @ExportMessage
    @TruffleBoundary
    @SuppressWarnings("static-method")
    public Object readdir(Object dirStreamObj) {
        EmulatedDirStream dirStream = (EmulatedDirStream) dirStreamObj;
        if (dirStream.iterator.hasNext()) {
            return dirStream.iterator.next();
        } else {
            return null;
        }
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public Object dirEntryGetName(Object dirEntry) {
        TruffleFile file = (TruffleFile) dirEntry;
        return file.getName();
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public Object dirEntryGetPath(Object dirEntry, Object scandirPath) {
        TruffleFile file = (TruffleFile) dirEntry;
        // Given that scandirPath must have been successfully channeled via opendir, we can assume
        // that it is a valid path
        TruffleFile dir = context.getPublicTruffleFileRelaxed(pathToJavaStr(scandirPath));
        // We let the filesystem handle the proper concatenation of the two paths
        return dir.resolve(file.getName()).getPath();
    }

    @ExportMessage
    @TruffleBoundary
    @SuppressWarnings("static-method")
    public long dirEntryGetInode(Object dirEntry) throws PosixException {
        TruffleFile file = (TruffleFile) dirEntry;
        try {
            Attributes attributes = file.getAttributes(Collections.singletonList(UNIX_INODE), LinkOption.NOFOLLOW_LINKS);
            return attributes.get(UNIX_INODE);
        } catch (UnsupportedOperationException e) {
            return getEmulatedInode(file);
        } catch (IOException e) {
            throw posixException(OSErrorEnum.fromException(e));
        }
    }

    @ExportMessage
    @TruffleBoundary
    @SuppressWarnings("static-method")
    public int dirEntryGetType(Object dirEntry) {
        TruffleFile file = (TruffleFile) dirEntry;
        try {
            Attributes attrs = file.getAttributes(Arrays.asList(IS_DIRECTORY, IS_SYMBOLIC_LINK, IS_REGULAR_FILE), LinkOption.NOFOLLOW_LINKS);
            if (attrs.get(IS_DIRECTORY)) {
                return PosixSupportLibrary.DT_DIR;
            } else if (attrs.get(IS_SYMBOLIC_LINK)) {
                return PosixSupportLibrary.DT_LNK;
            } else if (attrs.get(IS_REGULAR_FILE)) {
                return PosixSupportLibrary.DT_REG;
            }
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "Silenced exception in dirEntryGetType", e);
            // The caller will re-try using stat, which can throw PosixException and it should get
            // the same error again
        }
        return PosixSupportLibrary.DT_UNKNOWN;
    }

    @ExportMessage
    public void utimensat(int dirFd, Object path, long[] timespec, boolean followSymlinks,
                    @Shared("setUTime") @Cached SetUTimeNode setUTimeNode,
                    @Shared("defaultDirProfile") @Cached ConditionProfile defaultDirFdPofile) throws PosixException {
        String pathStr = pathToJavaStr(path);
        TruffleFile file = resolvePath(dirFd, pathStr, defaultDirFdPofile);
        setUTimeNode.execute(file, timespec, followSymlinks);
    }

    @ExportMessage
    public void futimens(int fd, long[] timespec,
                    @Shared("setUTime") @Cached SetUTimeNode setUTimeNode) throws PosixException {
        String path = getFilePath(fd);
        TruffleFile file = getTruffleFile(path);
        setUTimeNode.execute(file, timespec, true);
    }

    @GenerateUncached
    public abstract static class SetUTimeNode extends Node {
        abstract void execute(TruffleFile file, long[] timespec, boolean followSymlinks) throws PosixException;

        @Specialization(guards = "timespec == null")
        static void doCurrentTime(TruffleFile file, long[] timespec, boolean followSymlinks,
                        @Shared("errorBranch") @Cached BranchProfile errBranch) throws PosixException {
            FileTime time = FileTime.fromMillis(System.currentTimeMillis());
            setFileTimes(followSymlinks, file, time, time, errBranch);
        }

        // the second guard is just so that Truffle does not generate dead code that throws
        // UnsupportedSpecializationException..
        @Specialization(guards = {"timespec != null", "file != null"})
        static void doGivenTime(TruffleFile file, long[] timespec, boolean followSymlinks,
                        @Shared("errorBranch") @Cached BranchProfile errBranch) throws PosixException {
            FileTime atime = toFileTime(timespec[0], timespec[1]);
            FileTime mtime = toFileTime(timespec[2], timespec[3]);
            setFileTimes(followSymlinks, file, mtime, atime, errBranch);
        }

        private static void setFileTimes(boolean followSymlinks, TruffleFile file, FileTime mtime, FileTime atime, BranchProfile errBranch) throws PosixException {
            try {
                file.setLastAccessTime(atime, getLinkOptions(followSymlinks));
                file.setLastModifiedTime(mtime, getLinkOptions(followSymlinks));
            } catch (Exception e) {
                errBranch.enter();
                final ErrorAndMessagePair errAndMsg = OSErrorEnum.fromException(e);
                // setLastAccessTime/setLastModifiedTime and NOFOLLOW_LINKS does not work (at least)
                // on OpenJDK8 on Linux and gives ELOOP error. See some explanation in this thread:
                // https://stackoverflow.com/questions/17308363/symlink-lastmodifiedtime-in-java-1-7
                if (errAndMsg.oserror == OSErrorEnum.ELOOP && !followSymlinks) {
                    throw new UnsupportedPosixFeatureException("utime with 'follow symlinks' flag is not supported");
                }
                throw posixException(errAndMsg);
            }
        }

        private static FileTime toFileTime(long seconds, long nanos) {
            // JDK allows to set only one "time" per one operation, so
            // UnixFileAttributeViews#setTimes is setting only one of the mtime/atime but internally
            // it needs to call POSIX utime providing both, so it takes the other from fstat, but
            // since JDK's fstat wrapper does not support better granularity than seconds, it
            // will override nanoseconds component that may have been set by our code already.
            // To make this less confusing, we intentionally ignore the nanoseconds component, even
            // thought we could set microseconds for one of the mtime/atime (the one that is set
            // last)
            return FileTime.from(seconds, TimeUnit.SECONDS);
        }
    }

    @ExportMessage
    public void renameat(int oldDirFd, Object oldPath, int newDirFd, Object newPath,
                    @Shared("defaultDirProfile") @Cached ConditionProfile defaultDirFdPofile) throws PosixException {
        try {
            TruffleFile newFile = resolvePath(newDirFd, pathToJavaStr(newPath), defaultDirFdPofile);
            if (newFile.isDirectory()) {
                throw posixException(OSErrorEnum.EISDIR);
            }
            TruffleFile oldFile = resolvePath(oldDirFd, pathToJavaStr(oldPath), defaultDirFdPofile);
            oldFile.move(newFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            throw posixException(OSErrorEnum.fromException(e));
        }
    }

    @ExportMessage
    public boolean faccessat(int dirFd, Object path, int mode, boolean effectiveIds, boolean followSymlinks,
                    @Shared("errorBranch") @Cached BranchProfile errBranch,
                    @Shared("defaultDirProfile") @Cached ConditionProfile defaultDirFdPofile) {
        if (effectiveIds) {
            errBranch.enter();
            throw new UnsupportedPosixFeatureException("faccess with effective user IDs");
        }
        TruffleFile file = null;
        try {
            file = resolvePath(dirFd, pathToJavaStr(path), defaultDirFdPofile);
        } catch (PosixException e) {
            // When the dirFd is invalid descriptor, we just return false, like the real faccessat
            return false;
        }
        if (!file.exists(getLinkOptions(followSymlinks))) {
            return false;
        }
        if (mode == F_OK) {
            // we are supposed to just check the existence
            return true;
        }
        if (!followSymlinks) {
            // TruffleFile#isExecutable/isReadable/isWriteable does not support LinkOptions, but
            // that's probably because Java NIO does not support NOFOLLOW_LINKS in permissions check
            errBranch.enter();
            throw new UnsupportedPosixFeatureException("faccess with effective user IDs");
        }
        boolean result = true;
        if ((mode & X_OK) != 0) {
            result = result && file.isExecutable();
        }
        if ((mode & R_OK) != 0) {
            result = result && file.isReadable();
        }
        if ((mode & W_OK) != 0) {
            result = result && file.isWritable();
        }
        return result;
    }

    @ExportMessage
    public void fchmodat(int dirFd, Object path, int mode, boolean followSymlinks,
                    @Shared("defaultDirProfile") @Cached ConditionProfile defaultDirFdPofile) throws PosixException {
        TruffleFile file = resolvePath(dirFd, pathToJavaStr(path), defaultDirFdPofile);
        Set<PosixFilePermission> permissions = modeToPosixFilePermissions(mode);
        try {
            file.setPosixPermissions(permissions, getLinkOptions(followSymlinks));
        } catch (Exception e) {
            throw posixException(OSErrorEnum.fromException(e));
        }
    }

    @ExportMessage
    public void fchmod(int fd, int mode) throws PosixException {
        String path = getFilePath(fd);
        if (path == null) {
            throw posixException(OSErrorEnum.EBADF);
        }
        TruffleFile file = getTruffleFile(path);
        Set<PosixFilePermission> permissions = modeToPosixFilePermissions(mode);
        try {
            file.setPosixPermissions(permissions);
        } catch (Exception e) {
            throw posixException(OSErrorEnum.fromException(e));
        }
    }

    @ExportMessage
    public Object readlinkat(int dirFd, Object path,
                    @Shared("defaultDirProfile") @Cached ConditionProfile defaultDirFdPofile) throws PosixException {
        TruffleFile file = resolvePath(dirFd, pathToJavaStr(path), defaultDirFdPofile);
        try {
            TruffleFile canonicalFile = file.getCanonicalFile();
            if (file.equals(canonicalFile)) {
                throw posixException(OSErrorEnum.EINVAL);
            }
            return canonicalFile.getPath();
        } catch (PosixException e) {
            throw e;
        } catch (Exception e) {
            throw posixException(OSErrorEnum.fromException(e));
        }
    }

    private static final String[] KILL_SIGNALS = new String[]{"SIGKILL", "SIGQUIT", "SIGTRAP", "SIGABRT"};
    private static final String[] TERMINATION_SIGNALS = new String[]{"SIGTERM", "SIGINT"};

    @ExportMessage
    public void kill(long pid, int signal,
                    @Cached ReadAttributeFromObjectNode readSignalNode,
                    @Cached IsNode isNode) throws PosixException {
        // TODO looking up the signal values by name is probably not compatible with CPython
        // (the user might change the value of _signal.SIGKILL, but kill(pid, 9) should still work
        PythonModule signalModule = context.getCore().lookupBuiltinModule("_signal");
        for (String name : TERMINATION_SIGNALS) {
            Object value = readSignalNode.execute(signalModule, name);
            if (isNode.execute(signal, value)) {
                try {
                    sigterm((int) pid);
                } catch (IndexOutOfBoundsException e) {
                    throw posixException(OSErrorEnum.ESRCH);
                }
                return;
            }
        }
        for (String name : KILL_SIGNALS) {
            Object value = readSignalNode.execute(signalModule, name);
            if (isNode.execute(signal, value)) {
                try {
                    sigkill((int) pid);
                } catch (IndexOutOfBoundsException e) {
                    throw posixException(OSErrorEnum.ESRCH);
                }
                return;
            }
        }
        Object dfl = readSignalNode.execute(signalModule, "SIG_DFL");
        if (isNode.execute(signal, dfl)) {
            try {
                sigdfl((int) pid);
            } catch (IndexOutOfBoundsException e) {
                throw posixException(OSErrorEnum.ESRCH);
            }
            return;
        }
        throw new UnsupportedPosixFeatureException("Sending arbitrary signals to child processes. Can only send some kill and term signals.");
    }

    @ExportMessage
    public long[] waitpid(long pid, int options) throws PosixException {
        try {
            if (options == 0) {
                int exitStatus = waitpid((int) pid);
                return new long[]{pid, exitStatus};
            } else if (options == WNOHANG) {
                // TODO: simplify once the super class is merged with this class
                int[] res = exitStatus((int) pid);
                return new long[]{res[0], res[1]};
            } else {
                throw new UnsupportedPosixFeatureException("Only 0 or WNOHANG are supported for waitpid");
            }
        } catch (IndexOutOfBoundsException e) {
            if (pid < -1) {
                throw new UnsupportedPosixFeatureException("Process groups are not supported.");
            } else if (pid <= 0) {
                throw posixException(OSErrorEnum.ECHILD);
            } else {
                throw posixException(OSErrorEnum.ESRCH);
            }
        } catch (InterruptedException e) {
            throw posixException(OSErrorEnum.EINTR);
        }
    }

    // TODO the implementation of the following builtins is taken from posix.py,
    // do they really make sense for the emulated backend? Is the handling of exist status correct?

    @ExportMessage
    @SuppressWarnings("static-method")
    public boolean wcoredump(int status) {
        return false;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public boolean wifcontinued(int status) {
        return false;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public boolean wifstopped(int status) {
        return false;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public boolean wifsignaled(int status) {
        return status > 128;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public boolean wifexited(int status) {
        return !wifsignaled(status);
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public int wexitstatus(int status) {
        return status & 127;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public int wtermsig(int status) {
        return status - 128;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public int wstopsig(int status) {
        return 0;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    @TruffleBoundary
    public long getuid() {
        String osName = System.getProperty("os.name");
        if (osName.contains("Linux")) {
            return new com.sun.security.auth.module.UnixSystem().getUid();
        }
        return 1000;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public long getppid() {
        throw new UnsupportedPosixFeatureException("Emulated getppid not supported");
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public long getsid(long pid) {
        throw new UnsupportedPosixFeatureException("Emulated getsid not supported");
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public String ctermid() {
        return "/dev/tty";
    }

    @ExportMessage
    @TruffleBoundary
    public void setenv(Object name, Object value, boolean overwrite) {
        String nameStr = pathToJavaStr(name);
        String valueStr = pathToJavaStr(value);
        if (overwrite) {
            environ.put(nameStr, valueStr);
        } else {
            environ.putIfAbsent(nameStr, valueStr);
        }
    }

    @ExportMessage
    @TruffleBoundary
    public int forkExec(Object[] executables, Object[] args, Object cwd, Object[] env, int stdinReadFd, int stdinWriteFd, int stdoutReadFd, int stdoutWriteFd, int stderrReadFd, int stderrWriteFd,
                    int errPipeReadFd, int errPipeWriteFd, boolean closeFds, boolean restoreSignals, boolean callSetsid, int[] fdsToKeep) throws PosixException {

        // TODO there are a few arguments we ignore, we should throw an exception or report a
        // compatibility warning

        // TODO do we need to do this check (and the isExecutable() check later)?
        TruffleFile cwdFile = cwd == null ? context.getEnv().getCurrentWorkingDirectory() : getTruffleFile(pathToJavaStr(cwd));
        if (!cwdFile.exists()) {
            throw posixException(OSErrorEnum.ENOENT);
        }

        HashMap<String, String> envMap = null;
        if (env != null) {
            envMap = new HashMap<>(env.length);
            for (Object o : env) {
                String str = pathToJavaStr(o);
                String[] strings = str.split("=", 2);
                if (strings.length == 2) {
                    envMap.put(strings[0], strings[1]);
                } else {
                    throw new UnsupportedPosixFeatureException("Only key=value environment variables are supported");
                }
            }
        }

        String[] argStrings;
        if (args.length == 0) {
            // Posix execv() function distinguishes between the name of the file to be executed and
            // the arguments. It is only a convention that the first argument is the filename of the
            // executable and is not even mandatory, i.e. it is possible to exec a program with no
            // arguments at all, but some programs fail by printing "A NULL argv[0] was passed
            // through an exec system call".
            // https://stackoverflow.com/questions/36673765/why-can-the-execve-system-call-run-bin-sh-without-any-argv-arguments-but-not
            // Java's Process API uses the first argument as the executable name so we always need
            // to provide it.
            argStrings = new String[1];
        } else {
            argStrings = new String[args.length];
            for (int i = 0; i < args.length; ++i) {
                argStrings[i] = pathToJavaStr(args[i]);
            }
        }

        IOException firstError = null;
        for (Object o : executables) {
            String path = pathToJavaStr(o);
            TruffleFile executableFile = cwdFile.resolve(path);
            if (executableFile.isExecutable()) {
                argStrings[0] = path;
                try {
                    return exec(argStrings, cwdFile, envMap, stdinWriteFd, stdinReadFd, stdoutWriteFd, stdoutReadFd, stderrWriteFd, errPipeWriteFd, stderrReadFd);
                } catch (IOException ex) {
                    if (firstError == null) {
                        firstError = ex;
                    }
                }
            } else {
                LOGGER.finest(() -> "_posixsubprocess.fork_exec not executable: " + executableFile);
            }
        }

        // TODO we probably do not need to use the pipe at all, CPython uses it to pass errno from
        // the child to the parent which then raises an OSError. Since we are still in the parent,
        // we could just throw an exception.
        // However, there is no errno if the executables array is empty - CPython raises
        // SubprocessError in that case
        if (errPipeWriteFd != -1) {
            handleIOError(errPipeWriteFd, firstError);
        }
        // TODO returning -1 is not correct - we should either throw an exception directly or use
        // the pipe to pretend that there is a child - in which case we should return a valid pid_t
        // (and pretend that the child exited in the upcoming waitpid call)
        return -1;
    }

    private int exec(String[] argStrings, TruffleFile cwd, Map<String, String> env,
                    int p2cwrite, int p2cread, int c2pwrite, int c2pread,
                    int errwrite, int errpipe_write, int errread) throws IOException {
        LOGGER.finest(() -> "_posixsubprocess.fork_exec trying to exec: " + String.join(" ", argStrings));
        TruffleProcessBuilder pb = context.getEnv().newProcessBuilder(argStrings);
        if (p2cread != -1 && p2cwrite != -1) {
            pb.redirectInput(Redirect.PIPE);
        } else {
            pb.redirectInput(Redirect.INHERIT);
        }

        if (c2pread != -1 && c2pwrite != -1) {
            pb.redirectOutput(Redirect.PIPE);
        } else {
            pb.redirectOutput(Redirect.INHERIT);
        }

        if (errread != -1 && errwrite != -1) {
            pb.redirectError(Redirect.PIPE);
        } else {
            pb.redirectError(Redirect.INHERIT);
        }

        if (errwrite == c2pwrite) {
            pb.redirectErrorStream(true);
        }

        pb.directory(cwd);
        if (env != null) {
            pb.clearEnvironment(true);
            pb.environment(env);
        }

        ProcessWrapper process = new ProcessWrapper(pb.start(), p2cwrite != -1, c2pread != 1, errread != -1);
        try {
            if (p2cwrite != -1) {
                // user code is expected to close the unused ends of the pipes
                getFileChannel(p2cwrite).close();
                fdopen(p2cwrite, process.getOutputChannel());
            }
            if (c2pread != -1) {
                getFileChannel(c2pread).close();
                fdopen(c2pread, process.getInputChannel());
            }
            if (errread != -1) {
                getFileChannel(errread).close();
                fdopen(errread, process.getErrorChannel());
            }
        } catch (IOException ex) {
            // We only want to rethrow the IOException that may come out of pb.start()
            if (errpipe_write != -1) {
                handleIOError(errpipe_write, ex);
            }
            return -1;
        }

        return registerChild(process);
    }

    @TruffleBoundary(allowInlining = true)
    private void handleIOError(int errpipe_write, IOException e) {
        // write exec error information here. Data format: "exception name:hex
        // errno:description". The exception can be null if we did not find any file in the
        // execList that could be executed
        Channel err = getFileChannel(errpipe_write);
        if (!(err instanceof WritableByteChannel)) {
            // TODO if we are pretending to be the child, then we should probably ignore errors like
            // we do below
            throw new UnsupportedPosixFeatureException(ErrorMessages.ERROR_WRITING_FORKEXEC);
        } else {
            ErrorAndMessagePair pair;
            if (e == null) {
                pair = new ErrorAndMessagePair(OSErrorEnum.ENOENT, OSErrorEnum.ENOENT.getMessage());
            } else {
                pair = OSErrorEnum.fromException(e);
            }
            try {
                ((WritableByteChannel) err).write(ByteBuffer.wrap(("OSError:" + Long.toHexString(pair.oserror.getNumber()) + ":" + pair.message).getBytes()));
            } catch (IOException e1) {
            }
        }
    }

    @ExportMessage
    public void execv(Object pathname, Object[] args) throws PosixException {
        assert args.length > 0;
        String[] cmd = new String[args.length];
        // ProcessBuilder does not accept separate executable name, we must overwrite the 0-th
        // argument
        cmd[0] = pathToJavaStr(pathname);
        for (int i = 1; i < cmd.length; ++i) {
            cmd[i] = pathToJavaStr(args[i]);
        }
        try {
            execvInternal(cmd);
        } catch (Exception e) {
            throw posixException(OSErrorEnum.fromException(e));
        }
        throw CompilerDirectives.shouldNotReachHere("Execv must not return normally");
    }

    @TruffleBoundary
    private void execvInternal(String[] cmd) throws IOException {
        TruffleProcessBuilder builder = context.getEnv().newProcessBuilder(cmd);
        builder.clearEnvironment(true);
        builder.environment(environ);
        Process pr = builder.start();
        // TODO how do env.out()/err() relate to FDs 1, 2? What about stdin?
        // Also, native execv 'kills' all threads
        BufferedReader bfr = new BufferedReader(new InputStreamReader(pr.getInputStream()));
        OutputStream stream = context.getEnv().out();
        String line;
        while ((line = bfr.readLine()) != null) {
            stream.write(line.getBytes());
            stream.write("\n".getBytes());
        }
        BufferedReader stderr = new BufferedReader(new InputStreamReader(pr.getErrorStream()));
        OutputStream errStream = context.getEnv().err();
        while ((line = stderr.readLine()) != null) {
            errStream.write(line.getBytes());
            errStream.write("\n".getBytes());
        }
        try {
            pr.waitFor();
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
        // TODO python-specific, missing location
        throw new PythonExitException(null, pr.exitValue());
    }

    @ExportMessage
    @TruffleBoundary
    public int system(Object commandObj) {
        String cmd = pathToJavaStr(commandObj);
        LOGGER.fine(() -> "os.system: " + cmd);

        String[] command;
        String osProperty = System.getProperty("os.name");
        if (osProperty != null && osProperty.toLowerCase(Locale.ENGLISH).startsWith("windows")) {
            command = new String[]{"cmd.exe", "/c", cmd};
        } else {
            command = new String[]{(environ.getOrDefault("SHELL", "sh")), "-c", cmd};
        }
        Env env = context.getEnv();
        try {
            TruffleProcessBuilder pb = context.getEnv().newProcessBuilder(command);
            pb.directory(env.getCurrentWorkingDirectory());
            PipePump stdout = null, stderr = null;
            boolean stdsArePipes = !context.getOption(PythonOptions.TerminalIsInteractive);
            if (stdsArePipes) {
                pb.redirectInput(Redirect.PIPE);
                pb.redirectOutput(Redirect.PIPE);
                pb.redirectError(Redirect.PIPE);
            } else {
                pb.inheritIO(true);
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

    // TODO merge with ProcessWrapper
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

    public static final class MMapHandle {
        private static final MMapHandle NONE = new MMapHandle(null, 0);
        private SeekableByteChannel channel;
        private final long offset;

        public MMapHandle(SeekableByteChannel channel, long offset) {
            this.channel = channel;
            this.offset = offset;
        }

        @Override
        public String toString() {
            CompilerAsserts.neverPartOfCompilation();
            return String.format("Emulated mmap [channel=%s, offset=%d]", channel, offset);
        }
    }

    private static final class AnonymousMap implements SeekableByteChannel {
        private final byte[] data;

        private boolean open = true;
        private int cur;

        public AnonymousMap(int cap) {
            this.data = new byte[cap];
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void close() throws IOException {
            open = false;
        }

        @Override
        public int read(ByteBuffer dst) throws IOException {
            int nread = Math.min(dst.remaining(), data.length - cur);
            dst.put(data, cur, nread);
            return nread;
        }

        @Override
        public int write(ByteBuffer src) throws IOException {
            int nwrite = Math.min(src.remaining(), data.length - cur);
            src.get(data, cur, nwrite);
            return nwrite;
        }

        @Override
        public long position() throws IOException {
            return cur;
        }

        @Override
        public SeekableByteChannel position(long newPosition) throws IOException {
            if (newPosition < 0) {
                throw new IllegalArgumentException();
            }
            cur = (int) newPosition;
            return this;
        }

        @Override
        public long size() throws IOException {
            return data.length;
        }

        @Override
        public SeekableByteChannel truncate(long size) throws IOException {
            for (int i = 0; i < size; i++) {
                data[i] = 0;
            }
            return this;
        }
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    final MMapHandle mmap(long length, int prot, int flags, int fd, long offset,
                    @Shared("defaultDirProfile") @Cached ConditionProfile isAnonymousProfile) throws PosixException {
        if (prot == PROT_NONE) {
            return MMapHandle.NONE;
        }

        // Note: the profile is not really defaultDirProfile, but it's good to share...
        if (isAnonymousProfile.profile((flags & MAP_ANONYMOUS) != 0)) {
            try {
                return new MMapHandle(new AnonymousMap(PythonUtils.toIntExact(length)), 0);
            } catch (OverflowException e) {
                CompilerDirectives.transferToInterpreter();
                throw new UnsupportedPosixFeatureException(String.format("Anonymous mapping in mmap for memory larger than %d", Integer.MAX_VALUE));
            }
        }

        String path = getFilePath(fd);
        TruffleFile file = getTruffleFile(path);
        Set<StandardOpenOption> options = mmapProtToOptions(prot);

        // we create a new channel, the file may be closed but the mmap object should still work
        SeekableByteChannel fileChannel;
        try {
            fileChannel = newByteChannel(file, options);
            position(fileChannel, offset);
            return new MMapHandle(fileChannel, offset);
        } catch (IOException e) {
            throw posixException(OSErrorEnum.fromException(e));
        }
    }

    @TruffleBoundary
    private static Set<StandardOpenOption> mmapProtToOptions(int prot) {
        HashSet<StandardOpenOption> options = new HashSet<>();
        if ((prot & PROT_READ) != 0) {
            options.add(StandardOpenOption.READ);
        }
        if ((prot & PROT_WRITE) != 0) {
            options.add(StandardOpenOption.WRITE);
        }
        if ((prot & PROT_EXEC) != 0) {
            throw new UnsupportedPosixFeatureException("mmap: flag PROT_EXEC is not supported");
        }
        return options;
    }

    @TruffleBoundary
    private static SeekableByteChannel newByteChannel(TruffleFile file, Set<StandardOpenOption> options) throws IOException {
        return file.newByteChannel(options);
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public byte mmapReadByte(Object mmap, long index,
                    @Shared("errorBranch") @Cached BranchProfile errBranch) throws PosixException {
        if (mmap == MMapHandle.NONE) {
            errBranch.enter();
            throw posixException(OSErrorEnum.EACCES);
        }
        MMapHandle handle = (MMapHandle) mmap;
        ByteBuffer readingBuffer = allocateByteBuffer(1);
        int readSize = readBytes(handle, index, readingBuffer, errBranch);
        if (readSize == 0) {
            throw posixException(OSErrorEnum.ENODATA);
        }
        return getByte(readingBuffer);
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public int mmapReadBytes(Object mmap, long index, byte[] bytes, int length,
                    @Shared("errorBranch") @Cached BranchProfile errBranch) throws PosixException {
        if (mmap == MMapHandle.NONE) {
            errBranch.enter();
            throw posixException(OSErrorEnum.EACCES);
        }
        MMapHandle handle = (MMapHandle) mmap;
        int sz;
        try {
            sz = PythonUtils.toIntExact(length);
        } catch (OverflowException e) {
            errBranch.enter();
            throw posixException(OSErrorEnum.EOVERFLOW);
        }
        ByteBuffer readingBuffer = allocateByteBuffer(sz);
        int readSize = readBytes(handle, index, readingBuffer, errBranch);
        if (readSize > 0) {
            getByteBufferArray(readingBuffer, bytes, readSize);
        }
        return readSize;
    }

    private static int readBytes(MMapHandle handle, long index, ByteBuffer readingBuffer, BranchProfile errBranch) throws PosixException {
        try {
            position(handle.channel, index + handle.offset);
            return readChannel(handle.channel, readingBuffer);
        } catch (IOException e) {
            errBranch.enter();
            throw posixException(OSErrorEnum.fromException(e));
        }
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public void mmapWriteBytes(Object mmap, long index, byte[] bytes, int length,
                    @Shared("errorBranch") @Cached BranchProfile errBranch) throws PosixException {
        if (mmap == MMapHandle.NONE) {
            errBranch.enter();
            throw posixException(OSErrorEnum.EACCES);
        }
        MMapHandle handle = (MMapHandle) mmap;
        try {
            position(handle.channel, handle.offset + index);
            int written = handle.channel.write(ByteBuffer.wrap(bytes, 0, length));
            if (written != length) {
                throw posixException(OSErrorEnum.EIO);
            }
        } catch (Exception e) {
            // Catching generic Exception to also cover NonWritableChannelException
            errBranch.enter();
            throw posixException(OSErrorEnum.fromException(e));
        }
    }

    @ExportMessage
    @SuppressWarnings({"static-method", "unused"})
    public void mmapFlush(Object mmap, long offset, long length) {
        // Intentionally noop
        // If we had access to the underlying NIO FileChannel, we could explicitly set force(true)
        // when creating the channel. Another possibility would be exposing JDK's memory mapped
        // files support via Truffle API, which would allow for more compatible (and completely
        // different implementation of mmap in emulated posix)
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public void mmapUnmap(Object mmap, @SuppressWarnings("unused") long length) throws PosixException {
        if (mmap == MMapHandle.NONE) {
            return;
        }
        MMapHandle handle = (MMapHandle) mmap;
        if (handle.channel != null) {
            try {
                closeChannel(handle.channel);
            } catch (IOException e) {
                throw posixException(OSErrorEnum.fromException(e));
            }
            handle.channel = null;
        }
    }

    @TruffleBoundary
    private static void closeChannel(Channel ch) throws IOException {
        ch.close();
    }

    @TruffleBoundary
    private static void position(SeekableByteChannel ch, long offset) throws IOException {
        ch.position(offset);
    }

    @TruffleBoundary(allowInlining = true)
    private static ByteBuffer allocateByteBuffer(int n) {
        return ByteBuffer.allocate(n);
    }

    @TruffleBoundary(allowInlining = true)
    protected static void getByteBufferArray(ByteBuffer src, byte[] dst, int readSize) {
        src.flip();
        src.get(dst, 0, readSize);
    }

    @TruffleBoundary(allowInlining = true)
    protected static byte getByte(ByteBuffer src) {
        src.flip();
        return src.get();
    }

    @TruffleBoundary
    private static int readChannel(Object readableChannel, ByteBuffer dst) throws IOException {
        return ((ReadableByteChannel) readableChannel).read(dst);
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
    public Object createPathFromBytes(byte[] path) {
        return checkEmbeddedNulls(BytesUtils.createUTF8String(path));
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public String getPathAsString(Object path) {
        return (String) path;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public Buffer getPathAsBytes(Object path) {
        return Buffer.wrap(BytesUtils.utf8StringToBytes((String) path));
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

    private TruffleFile getTruffleFile(String path) throws PosixException {
        try {
            return context.getPublicTruffleFileRelaxed(path, PythonLanguage.DEFAULT_PYTHON_EXTENSIONS);
        } catch (Exception ex) {
            // So far it seem that this can only be InvalidPath exception from Java NIO, but we stay
            // on the safe side and catch generic exception
            throw posixException(OSErrorEnum.fromException(ex));
        }
    }

    /**
     * Resolves the path relative to the directory given as file descriptor. Honors the
     * {@link PosixSupportLibrary#DEFAULT_DIR_FD}.
     */
    private TruffleFile resolvePath(int dirFd, String pathname, ConditionProfile defaultDirFdPofile) throws PosixException {
        if (defaultDirFdPofile.profile(dirFd == PosixSupportLibrary.DEFAULT_DIR_FD)) {
            return getTruffleFile(pathname);
        } else {
            TruffleFile file = getTruffleFile(pathname);
            if (file.isAbsolute()) {
                // Even if the dirFd is non-existing or otherwise wrong, we should not trigger
                // any error if the file path is already absolute
                return file;
            }
            String dirPath = getFilePathOrDefault(dirFd, null);
            if (dirPath == null) {
                throw posixException(OSErrorEnum.EBADF);
            }
            TruffleFile dir = getTruffleFile(dirPath);
            return dir.resolve(pathname);
        }
    }

    private static String pathToJavaStr(Object path) {
        return (String) path;
    }

    @TruffleBoundary(allowInlining = true)
    private String getFilePathOrDefault(int fd, String defaultValue) {
        return filePaths.getOrDefault(fd, defaultValue);
    }

    @TruffleBoundary(allowInlining = true)
    private static FileAttribute<Set<PosixFilePermission>> modeToAttributes(int fileMode) {
        Set<PosixFilePermission> perms = modeToPosixFilePermissions(fileMode);
        return PosixFilePermissions.asFileAttribute(perms);
    }

    @TruffleBoundary(allowInlining = true)
    private static Set<PosixFilePermission> modeToPosixFilePermissions(int fileMode) {
        HashSet<PosixFilePermission> perms = new HashSet<>(Arrays.asList(ownerBitsToPermission[fileMode >> 6 & 7]));
        perms.addAll(Arrays.asList(groupBitsToPermission[fileMode >> 3 & 7]));
        perms.addAll(Arrays.asList(otherBitsToPermission[fileMode & 7]));
        return perms;
    }

    @TruffleBoundary(allowInlining = true)
    private static Set<StandardOpenOption> flagsToOptions(int flags) {
        Set<StandardOpenOption> options = new HashSet<>();
        if ((flags & WRONLY) != 0) {
            options.add(StandardOpenOption.WRITE);
        }
        if ((flags & APPEND) != 0) {
            options.add(StandardOpenOption.WRITE);
            options.add(StandardOpenOption.APPEND);
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

    public static LinkOption[] getLinkOptions(boolean followSymlinks) {
        return followSymlinks ? new LinkOption[0] : new LinkOption[]{LinkOption.NOFOLLOW_LINKS};
    }

    private static final TruffleLogger LOGGER = PythonLanguage.getLogger(EmulatedPosixSupport.class);
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
