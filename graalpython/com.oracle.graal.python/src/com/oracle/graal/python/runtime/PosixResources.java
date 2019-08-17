/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channel;
import java.nio.channels.Channels;
import java.nio.channels.Pipe;
import java.nio.channels.SeekableByteChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import com.oracle.graal.python.builtins.objects.socket.PSocket;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.profiles.ValueProfile;

/**
 * This class manages the set of file descriptors and child PIDs of a context. File descriptors are
 * associated with {@link String} paths and {@link Channel}s, their capabilities depending on the
 * kind of channel.
 *
 * It also manages the list of virtual child PIDs.
 */
public class PosixResources {
    private final int FD_STDIN = 0;
    private final int FD_STDOUT = 1;
    private final int FD_STDERR = 2;

    /** Context-local file-descriptor mappings and PID mappings */
    private final SortedMap<Integer, ChannelWrapper> files;
    private final Map<Integer, String> filePaths;
    private final List<PSocket> sockets;
    private final List<Process> children;
    private final Map<String, Integer> inodes;
    private int inodeCnt = 0;

    private static class ProcessGroup extends Process {
        private final List<Process> children;

        ProcessGroup(List<Process> children) {
            this.children = children;
        }

        @Override
        public int waitFor() throws InterruptedException {
            for (Process child : children) {
                if (child != null) {
                    return child.waitFor();
                }
            }
            return 0;
        }

        @Override
        public OutputStream getOutputStream() {
            throw new RuntimeException();
        }

        @Override
        public InputStream getInputStream() {
            throw new RuntimeException();
        }

        @Override
        public InputStream getErrorStream() {
            throw new RuntimeException();
        }

        @Override
        public int exitValue() {
            for (Process child : children) {
                if (!child.isAlive()) {
                    return child.exitValue();
                }
            }
            throw new IllegalThreadStateException();
        }

        @Override
        public void destroy() {
            for (Process child : children) {
                if (child != null) {
                    child.destroy();
                }
            }
        }

        @Override
        public Process destroyForcibly() {
            for (Process child : children) {
                if (child != null) {
                    child.destroyForcibly();
                }
            }
            return this;
        }
    }

    private static class ChannelWrapper {
        Channel channel;
        int cnt;

        ChannelWrapper() {
            this(null, 0);
        }

        ChannelWrapper(Channel channel) {
            this(channel, 1);
        }

        ChannelWrapper(Channel channel, int cnt) {
            this.channel = channel;
            this.cnt = cnt;
        }

        void setNewChannel(InputStream inputStream) {
            this.channel = Channels.newChannel(inputStream);
            this.cnt = 1;
        }

        void setNewChannel(OutputStream outputStream) {
            this.channel = Channels.newChannel(outputStream);
            this.cnt = 1;
        }
    }

    public PosixResources() {
        files = Collections.synchronizedSortedMap(new TreeMap<>());
        filePaths = Collections.synchronizedMap(new HashMap<>());
        sockets = Collections.synchronizedList(new ArrayList<>());
        children = Collections.synchronizedList(new ArrayList<>());
        String osProperty = System.getProperty("os.name");

        files.put(FD_STDIN, new ChannelWrapper());
        files.put(FD_STDOUT, new ChannelWrapper());
        files.put(FD_STDERR, new ChannelWrapper());
        if (osProperty != null && osProperty.toLowerCase(Locale.ENGLISH).contains("win")) {
            filePaths.put(FD_STDIN, "STDIN");
            filePaths.put(FD_STDOUT, "STDOUT");
            filePaths.put(FD_STDERR, "STDERR");
        } else {
            filePaths.put(FD_STDIN, "/dev/stdin");
            filePaths.put(FD_STDOUT, "/dev/stdout");
            filePaths.put(FD_STDERR, "/dev/stderr");
        }

        children.add(new ProcessGroup(children)); // PID 0 is special, and refers to all processes
                                                  // in the process group
        inodes = new HashMap<>();
    }

    @TruffleBoundary(allowInlining = true)
    public void setEnv(Env env) {
        synchronized (files) {
            files.get(FD_STDIN).setNewChannel(env.in());
            files.get(FD_STDOUT).setNewChannel(env.out());
            files.get(FD_STDERR).setNewChannel(env.err());
        }
    }

    private void addFD(int fd, Channel channel) {
        addFD(fd, channel, null);
    }

    @TruffleBoundary
    private void addFD(int fd, Channel channel, String path) {
        files.put(fd, new ChannelWrapper(channel));
        if (path != null) {
            filePaths.put(fd, path);
        }
    }

    @TruffleBoundary
    private void removeFD(int fd) throws IOException {
        ChannelWrapper channelWrapper = files.getOrDefault(fd, null);

        if (channelWrapper != null) {
            synchronized (files) {
                if (channelWrapper.cnt == 1) {
                    channelWrapper.channel.close();
                } else if (channelWrapper.cnt > 1) {
                    channelWrapper.cnt -= 1;
                }

                files.remove(fd);
                filePaths.remove(fd);
            }
        }
    }

    @TruffleBoundary
    private void dupFD(int fd1, int fd2) {
        ChannelWrapper channelWrapper = files.getOrDefault(fd1, null);
        if (channelWrapper != null) {
            synchronized (files) {
                channelWrapper.cnt += 1;
                files.put(fd2, channelWrapper);
            }
        }
    }

    @TruffleBoundary(allowInlining = true)
    public Channel getFileChannel(int fd, ValueProfile classProfile) {
        ChannelWrapper channelWrapper = files.getOrDefault(fd, null);
        if (channelWrapper != null) {
            return classProfile.profile(channelWrapper.channel);
        }
        return null;
    }

    @TruffleBoundary(allowInlining = true)
    public Channel getFileChannel(int fd) {
        ChannelWrapper channelWrapper = files.getOrDefault(fd, null);
        if (channelWrapper != null) {
            return channelWrapper.channel;
        }
        return null;
    }

    @TruffleBoundary
    public String getFilePath(int fd) {
        return filePaths.getOrDefault(fd, null);
    }

    @TruffleBoundary
    public PSocket getSocket(int fd) {
        if (sockets.size() > fd) {
            return sockets.get(fd);
        }
        return null;
    }

    @TruffleBoundary(allowInlining = true)
    public void close(int fd) {
        try {
            removeFD(fd);
        } catch (IOException ignored) {
        }
    }

    @TruffleBoundary
    public void closeSocket(int fd) {
        if (sockets.size() > fd) {
            sockets.set(fd, null);
        }
    }

    @TruffleBoundary
    public int openSocket(PSocket socket) {
        int fd = nextFreeSocketFd();
        sockets.set(fd, socket);
        return fd;
    }

    @TruffleBoundary
    public void reopenSocket(PSocket socket, int fd) {
        sockets.set(fd, socket);
    }

    @TruffleBoundary(allowInlining = true)
    public void fdopen(int fd, Channel fc) {
        files.get(fd).channel = fc;
    }

    /**
     * Open a newly created Channel
     *
     * @param path the path associated with the newly open Channel
     * @param fc the newly created Channel
     * @return the file descriptor associated with the new Channel
     */
    @TruffleBoundary(allowInlining = true)
    public int open(TruffleFile path, Channel fc) {
        int fd = nextFreeFd();
        addFD(fd, fc, path.getAbsoluteFile().getPath());
        return fd;
    }

    @TruffleBoundary(allowInlining = true)
    public int dup(int fd) {
        int dupFd = nextFreeFd();
        dupFD(fd, dupFd);
        return dupFd;
    }

    @TruffleBoundary(allowInlining = true)
    public int dup2(int fd, int fd2) throws IOException {
        removeFD(fd2);
        dupFD(fd, fd2);
        return fd2;
    }

    public boolean fsync(int fd) {
        return files.getOrDefault(fd, null) != null;
    }

    @TruffleBoundary(allowInlining = true)
    public void ftruncate(int fd, long size) throws IOException {
        Channel channel = getFileChannel(fd);
        if (channel instanceof SeekableByteChannel) {
            ((SeekableByteChannel) channel).truncate(size);
        }
    }

    @TruffleBoundary(allowInlining = true)
    public int[] pipe() throws IOException {
        Pipe pipe = Pipe.open();
        int readFD = nextFreeFd();
        addFD(readFD, pipe.source());

        int writeFD = nextFreeFd();
        addFD(writeFD, pipe.sink());

        return new int[]{readFD, writeFD};
    }

    @TruffleBoundary(allowInlining = true)
    private int nextFreeFd() {
        synchronized (files) {
            int fd1 = files.firstKey();
            for (int fd2 : files.keySet()) {
                if (fd2 == fd1) {
                    continue;
                }
                if (fd2 - fd1 > 1) {
                    return fd1 + 1;
                } else {
                    fd1 = fd2;
                }
            }
            return files.lastKey() + 1;
        }
    }

    @TruffleBoundary(allowInlining = true)
    private int nextFreeSocketFd() {
        synchronized (sockets) {
            for (int i = 0; i < sockets.size(); i++) {
                PSocket socket = sockets.get(i);
                if (socket == null) {
                    return i;
                }
            }
            sockets.add(null);
            return sockets.size() - 1;
        }
    }

    @TruffleBoundary(allowInlining = true)
    public int registerChild(Process child) {
        int pid = nextFreePid();
        children.set(pid, child);
        return pid;
    }

    @TruffleBoundary(allowInlining = true)
    private int nextFreePid() {
        synchronized (children) {
            for (int i = 0; i < children.size(); i++) {
                Process openPath = children.get(i);
                if (openPath == null) {
                    return i;
                }
            }
            children.add(null);
            return children.size() - 1;
        }
    }

    @TruffleBoundary(allowInlining = true)
    public void sigdfl(int pid) throws ArrayIndexOutOfBoundsException {
        children.get(pid); // just for the side-effect
    }

    @TruffleBoundary(allowInlining = true)
    public void sigterm(int pid) throws ArrayIndexOutOfBoundsException {
        Process process = children.get(pid);
        process.destroy();
    }

    @TruffleBoundary(allowInlining = true)
    public void sigkill(int pid) throws ArrayIndexOutOfBoundsException {
        Process process = children.get(pid);
        process.destroyForcibly();
    }

    @TruffleBoundary(allowInlining = true)
    public int waitpid(int pid) throws ArrayIndexOutOfBoundsException, InterruptedException {
        Process process = children.get(pid);
        int exitStatus = process.waitFor();
        children.set(pid, null);
        return exitStatus;
    }

    @TruffleBoundary(allowInlining = true)
    public int exitStatus(int pid) throws ArrayIndexOutOfBoundsException {
        Process process = children.get(pid);
        if (process.isAlive()) {
            return Integer.MIN_VALUE;
        } else {
            return process.exitValue();
        }
    }

    @TruffleBoundary(allowInlining = true)
    public int getInodeId(String canonical) {
        synchronized (inodes) {
            int inodeId;
            if (!inodes.containsKey(canonical)) {
                inodeId = inodeCnt++;
                inodes.put(canonical, inodeId);
            } else {
                inodeId = inodes.get(canonical);
            }
            return inodeId;
        }
    }
}
