/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules;

import java.io.IOException;
import java.nio.channels.Channel;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.runtime.PosixResources;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ValueProfile;

@CoreFunctions(defineModule = "fcntl")
public class FcntlModuleBuiltins extends PythonBuiltins {
    private static final int LOCK_SH = 1;
    private static final int LOCK_EX = 2;
    private static final int LOCK_NB = 4;
    private static final int LOCK_UN = 8;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return FcntlModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(PythonCore core) {
        builtinConstants.put("LOCK_SH", LOCK_SH);
        builtinConstants.put("LOCK_EX", LOCK_EX);
        builtinConstants.put("LOCK_NB", LOCK_NB);
        builtinConstants.put("LOCK_UN", LOCK_UN);
        super.initialize(core);
    }

    @Builtin(name = "flock", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class FlockNode extends PythonBinaryBuiltinNode {
        @Specialization
        synchronized PNone flock(VirtualFrame frame, int fd, int operation,
                        @Cached("createClassProfile()") ValueProfile profile) {
            PosixResources rs = getContext().getResources();
            Channel channel;
            try {
                channel = rs.getFileChannel(fd, profile);
            } catch (IndexOutOfBoundsException e) {
                throw raiseOSError(frame, OSErrorEnum.EBADFD);
            }
            if (channel instanceof FileChannel) {
                FileChannel fc = (FileChannel) channel;
                FileLock lock = rs.getFileLock(fd);
                try {
                    lock = doLockOperation(operation, fc, lock);
                } catch (IOException e) {
                    throw raiseOSError(frame, e);
                }
                rs.setFileLock(fd, lock);
            }
            return PNone.NONE;
        }

        @TruffleBoundary
        private static FileLock doLockOperation(int operation, FileChannel fc, FileLock oldLock) throws IOException {
            FileLock lock = oldLock;
            if (lock == null) {
                if ((operation & LOCK_SH) != 0) {
                    if ((operation & LOCK_NB) != 0) {
                        lock = fc.tryLock(0, Long.MAX_VALUE, true);
                    } else {
                        lock = fc.lock(0, Long.MAX_VALUE, true);
                    }
                } else if ((operation & LOCK_EX) != 0) {
                    if ((operation & LOCK_NB) != 0) {
                        lock = fc.tryLock();
                    } else {
                        lock = fc.lock();
                    }
                } else {
                    // not locked, that's ok
                }
            } else {
                if ((operation & LOCK_UN) != 0) {
                    lock.release();
                    lock = null;
                } else if ((operation & LOCK_EX) != 0) {
                    if (lock.isShared()) {
                        if ((operation & LOCK_NB) != 0) {
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
    }
}
