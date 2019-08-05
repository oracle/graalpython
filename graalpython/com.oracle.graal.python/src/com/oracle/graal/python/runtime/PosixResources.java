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
import java.nio.channels.Channel;
import java.nio.channels.Channels;
import java.nio.channels.Pipe;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
    /** Context-local file-descriptor mappings and PID mappings */
    private final List<Channel> files;
    private final List<String> filePaths;
    private final List<Process> children;
    private final Map<String, Integer> inodes;
    private int inodeCnt = 0;

    public PosixResources() {
        files = Collections.synchronizedList(new ArrayList<>());
        filePaths = Collections.synchronizedList(new ArrayList<>());
        children = Collections.synchronizedList(new ArrayList<>());
        String osProperty = System.getProperty("os.name");
        if (osProperty != null && osProperty.toLowerCase(Locale.ENGLISH).contains("win")) {
            filePaths.add("STDIN");
            filePaths.add("STDOUT");
            filePaths.add("STDERR");
        } else {
            filePaths.add("/dev/stdin");
            filePaths.add("/dev/stdout");
            filePaths.add("/dev/stderr");
        }
        files.add(null);
        files.add(null);
        files.add(null);
        children.add(null); // PID 0 is special, don't hand it out
        inodes = new HashMap<>();
    }

    @TruffleBoundary(allowInlining = true)
    public Channel getFileChannel(int fd, ValueProfile classProfile) {
        if (files.size() > fd) {
            return classProfile.profile(files.get(fd));
        }
        return null;
    }

    @TruffleBoundary(allowInlining = true)
    public Channel getFileChannel(int fd) {
        if (files.size() > fd && fd >= 0) {
            return files.get(fd);
        }
        return null;
    }

    @TruffleBoundary
    public String getFilePath(int fd) {
        if (filePaths.size() > fd) {
            return filePaths.get(fd);
        }
        return null;
    }

    @TruffleBoundary(allowInlining = true)
    public void close(int fd) {
        if (filePaths.size() > fd) {
            files.set(fd, null);
            filePaths.set(fd, null);
        }
    }

    @TruffleBoundary(allowInlining = true)
    public void fdopen(int fd, Channel fc) {
        files.set(fd, fc);
    }

    @TruffleBoundary(allowInlining = true)
    public int open(TruffleFile path, Channel fc) {
        int fd = nextFreeFd();
        files.set(fd, fc);
        filePaths.set(fd, path.getAbsoluteFile().getPath());
        return fd;
    }

    @TruffleBoundary(allowInlining = true)
    public int dup(int fd) {
        int dupFd = nextFreeFd();
        files.set(dupFd, getFileChannel(fd));
        filePaths.set(dupFd, getFilePath(fd));
        return dupFd;
    }

    @TruffleBoundary(allowInlining = true)
    public int[] pipe() throws IOException {
        Pipe pipe = Pipe.open();
        int read = nextFreeFd();
        files.set(read, pipe.source());
        int write = nextFreeFd();
        files.set(write, pipe.sink());
        return new int[]{read, write};
    }

    @TruffleBoundary(allowInlining = true)
    private int nextFreeFd() {
        synchronized (filePaths) {
            for (int i = 0; i < filePaths.size(); i++) {
                String openPath = filePaths.get(i);
                Channel openChannel = files.get(i);
                if (openPath == null && openChannel == null) {
                    return i;
                }
            }
            files.add(null);
            filePaths.add(null);
            return filePaths.size() - 1;
        }
    }

    @TruffleBoundary(allowInlining = true)
    public void setEnv(Env env) {
        files.set(0, Channels.newChannel(env.in()));
        files.set(1, Channels.newChannel(env.out()));
        files.set(2, Channels.newChannel(env.err()));
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
