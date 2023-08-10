/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.channels.FileLock;
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

import com.oracle.graal.python.util.SuppressFBWarnings;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.Env;

/**
 * This class manages the set of file descriptors and child PIDs of a context. File descriptors are
 * associated with {@link String} paths and {@link Channel}s, their capabilities depending on the
 * kind of channel.
 *
 * It also manages the list of virtual child PIDs.
 *
 * This class is an implementation detail of {@link EmulatedPosixSupport}.
 */
abstract class PosixResources extends PosixSupport {
    private static final int FD_STDIN = 0;
    private static final int FD_STDOUT = 1;
    private static final int FD_STDERR = 2;

    /** Context-local file-descriptor mappings and PID mappings */
    protected final PythonContext context;
    private final SortedMap<Integer, ChannelWrapper> files;
    protected final Map<Integer, String> filePaths;
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
                if (child != null && child != this) {
                    int exitCode = child.waitFor();
                    int childIndex = children.indexOf(child);
                    if (childIndex > 0) {
                        children.set(childIndex, null);
                    }
                    return exitCode;
                }
            }
            throw new IndexOutOfBoundsException();
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
                if (child != null && child != this && !child.isAlive()) {
                    return child.exitValue();
                }
            }
            throw new IllegalThreadStateException();
        }

        @Override
        public void destroy() {
            for (Process child : children) {
                if (child != null && child != this) {
                    child.destroy();
                }
            }
        }

        @Override
        // Can be removed once the `graalpython` suite has mxversion >= 6.27.1.
        // Until then, the analysis does not see an implementation with side effects.
        @SuppressFBWarnings(value = "RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT")
        public Process destroyForcibly() {
            for (Process child : children) {
                if (child != null && child != this) {
                    child.destroyForcibly();
                }
            }
            return this;
        }
    }

    private static class ChannelWrapper {
        Channel channel;
        int cnt;
        FileLock lock;
        boolean isStandardStream;

        ChannelWrapper(Channel channel) {
            this(channel, 1);
        }

        ChannelWrapper(Channel channel, int cnt) {
            this(channel, cnt, false);
        }

        ChannelWrapper(Channel channel, int cnt, boolean isStandardStream) {
            this.channel = channel;
            this.cnt = cnt;
            this.isStandardStream = isStandardStream;
        }

        static ChannelWrapper createForStandardStream() {
            return new ChannelWrapper(null, 0, true);
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

    protected PosixResources(PythonContext context) {
        this.context = context;
        files = Collections.synchronizedSortedMap(new TreeMap<>());
        filePaths = Collections.synchronizedMap(new HashMap<>());
        children = Collections.synchronizedList(new ArrayList<>());
        String osProperty = System.getProperty("os.name");

        files.put(FD_STDIN, ChannelWrapper.createForStandardStream());
        files.put(FD_STDOUT, ChannelWrapper.createForStandardStream());
        files.put(FD_STDERR, ChannelWrapper.createForStandardStream());
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

    @TruffleBoundary
    @Override
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
    protected boolean removeFD(int fd) throws IOException {
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
            return true;
        }
        return false;
    }

    /**
     * ATTENTION: This method must be used in a synchronized block (sync on {@link #files}) until.
     */
    @TruffleBoundary
    private void dupFD(int fd1, int fd2) {
        ChannelWrapper channelWrapper = files.getOrDefault(fd1, null);
        String path = filePaths.get(fd1);
        if (channelWrapper != null) {
            channelWrapper.cnt += 1;
            files.put(fd2, channelWrapper);
            if (path != null) {
                filePaths.put(fd2, path);
            }
        }
    }

    protected boolean isStandardStream(int fd) {
        ChannelWrapper channelWrapper = files.get(fd);
        return channelWrapper != null && channelWrapper.isStandardStream;
    }

    @TruffleBoundary
    public FileLock getFileLock(int fd) {
        ChannelWrapper channelWrapper = files.getOrDefault(fd, null);
        if (channelWrapper != null) {
            return channelWrapper.lock;
        }
        return null;
    }

    @TruffleBoundary
    public void setFileLock(int fd, FileLock lock) {
        ChannelWrapper channelWrapper = files.getOrDefault(fd, null);
        if (channelWrapper != null) {
            channelWrapper.lock = lock;
        }
    }

    @TruffleBoundary
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
    public void close(int fd) {
        try {
            removeFD(fd);
        } catch (IOException ignored) {
        }
    }

    @TruffleBoundary
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
    @TruffleBoundary
    public int open(TruffleFile path, Channel fc) {
        synchronized (files) {
            int fd = nextFreeFd();
            addFD(fd, fc, path.getAbsoluteFile().getPath());
            return fd;
        }
    }

    @TruffleBoundary
    public int dup(int fd) {
        synchronized (files) {
            int dupFd = nextFreeFd();
            dupFD(fd, dupFd);
            return dupFd;
        }
    }

    @TruffleBoundary
    public int dup2(int fd, int fd2) throws IOException {
        synchronized (files) {
            removeFD(fd2);
            dupFD(fd, fd2);
            return fd2;
        }
    }

    @TruffleBoundary
    public boolean fsync(int fd) {
        return files.getOrDefault(fd, null) != null;
    }

    @TruffleBoundary
    public Object ftruncate(int fd, long size) throws IOException {
        Channel channel = getFileChannel(fd);
        if (channel instanceof SeekableByteChannel) {
            return ((SeekableByteChannel) channel).truncate(size);
        }
        return null;
    }

    @TruffleBoundary
    public int[] pipe() throws IOException {
        synchronized (files) {
            Pipe pipe = Pipe.open();
            int readFD = nextFreeFd();
            addFD(readFD, pipe.source());

            int writeFD = nextFreeFd();
            addFD(writeFD, pipe.sink());

            return new int[]{readFD, writeFD};
        }
    }

    /**
     * ATTENTION: This method must be used in a synchronized block (sync on {@link #files}) until
     * the gained file descriptors are written to the map. Otherwise, concurrent threads may get the
     * same FDs.
     */
    @TruffleBoundary
    private int nextFreeFd() {
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

    @TruffleBoundary
    protected int registerChild(Process child) {
        synchronized (children) {
            for (int i = 0; i < children.size(); i++) {
                Process openPath = children.get(i);
                if (openPath == null) {
                    children.set(i, child);
                    return i;
                }
            }
            children.add(child);
            return children.size() - 1;
        }
    }

    private Process getChild(int pid) throws IndexOutOfBoundsException {
        if (pid < -1) {
            throw new IndexOutOfBoundsException("we do not support process groups");
        } else if (pid == -1) {
            // -1 - any child process.
            // 0 - any child process with the same process group.
            return children.get(0);
        } else {
            return children.get(pid);
        }
    }

    @TruffleBoundary(allowInlining = true)
    public void sigdfl(int pid) throws IndexOutOfBoundsException {
        getChild(pid); // just for the side-effect
    }

    @TruffleBoundary(allowInlining = true)
    public void sigterm(int pid) throws IndexOutOfBoundsException {
        Process process = getChild(pid);
        process.destroy();
    }

    @TruffleBoundary(allowInlining = true)
    public void sigkill(int pid) throws IndexOutOfBoundsException {
        Process process = getChild(pid);
        process.destroyForcibly();
    }

    @TruffleBoundary(allowInlining = true)
    public int waitpid(int pid) throws IndexOutOfBoundsException, InterruptedException {
        Process process = getChild(pid);
        int exitStatus = process.waitFor();
        if (pid > 0) { // cannot delete process groups
            children.set(pid, null);
        }
        return exitStatus;
    }

    @TruffleBoundary(allowInlining = true)
    public int[] exitStatus(int pid) throws IndexOutOfBoundsException {
        if (pid == -1) {
            for (int childPid = 1; childPid < children.size(); ++childPid) {
                Process child = children.get(childPid);
                if (child != null && !child.isAlive()) {
                    children.set(childPid, null);
                    return new int[]{childPid, child.exitValue()};
                }
            }
        } else {
            Process process = getChild(pid);
            if (!process.isAlive()) {
                children.set(pid, null);
                return new int[]{pid, process.exitValue()};
            }
        }
        return new int[]{0, 0};
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

    @TruffleBoundary
    int assignFileDescriptor(Channel channel) {
        synchronized (files) {
            int fd = nextFreeFd();
            addFD(fd, channel);
            return fd;
        }
    }

    @TruffleBoundary
    Channel getChannel(int fd) {
        ChannelWrapper channelWrapper = files.getOrDefault(fd, null);
        if (channelWrapper == null) {
            return null;
        }
        return channelWrapper.channel;
    }
}
