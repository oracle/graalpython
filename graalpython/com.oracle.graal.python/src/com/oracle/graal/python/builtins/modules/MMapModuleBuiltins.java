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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.mmap.PMMap;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.profiles.BranchProfile;

@CoreFunctions(defineModule = "mmap")
public class MMapModuleBuiltins extends PythonBuiltins {
    private static final int ACCESS_DEFAULT = 0;
    private static final int ACCESS_READ = 1;
    private static final int ACCESS_WRITE = 2;
    private static final int ACCESS_COPY = 3;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return MMapModuleBuiltinsFactory.getFactories();
    }

    public MMapModuleBuiltins() {
        builtinConstants.put("ACCESS_DEFAULT", ACCESS_DEFAULT);
        builtinConstants.put("ACCESS_READ", ACCESS_READ);
        builtinConstants.put("ACCESS_WRITE", ACCESS_WRITE);
        builtinConstants.put("ACCESS_COPY", ACCESS_COPY);
    }

    @Builtin(name = "mmap", minNumOfPositionalArgs = 3, parameterNames = {"cls", "fd", "length", "tagname", "access", "offset"}, constructsClass = PythonBuiltinClassType.PMMap)
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class MMapNode extends PythonBuiltinNode {

        private final BranchProfile invalidLengthProfile = BranchProfile.create();

        @Specialization(guards = {"isAnonymous(fd)", "isNoValue(access)", "isNoValue(offset)"})
        PMMap doAnonymous(LazyPythonClass clazz, @SuppressWarnings("unused") long fd, int length, @SuppressWarnings("unused") Object tagname, @SuppressWarnings("unused") PNone access,
                        @SuppressWarnings("unused") PNone offset) {
            checkLength(length);
            return factory().createMMap(clazz, new AnonymousMap(length), length, 0);
        }

        @Specialization(guards = {"fd >= 0", "isNoValue(access)", "isNoValue(offset)"})
        PMMap doFile(LazyPythonClass clazz, long fd, int length, Object tagname, @SuppressWarnings("unused") PNone access, @SuppressWarnings("unused") PNone offset) {
            return doFile(clazz, fd, length, tagname, ACCESS_DEFAULT, 0);
        }

        @Specialization(guards = {"fd >= 0", "isNoValue(offset)"})
        PMMap doFile(LazyPythonClass clazz, long fd, int length, Object tagname, int access, @SuppressWarnings("unused") PNone offset) {
            return doFile(clazz, fd, length, tagname, access, 0);
        }

        // mmap(fileno, length, tagname=None, access=ACCESS_DEFAULT[, offset])
        @Specialization(guards = "fd >= 0")
        PMMap doFile(LazyPythonClass clazz, long fd, long length, @SuppressWarnings("unused") Object tagname, @SuppressWarnings("unused") int access, long offset) {
            checkLength(length);
            int ifd;
            try {
                ifd = PInt.intValueExact(fd);
            } catch (ArithmeticException e) {
                throw raise(ValueError, ErrorMessages.INVALID_FILE_DESCRIPTOR);
            }

            String path = getContext().getResources().getFilePath(ifd);
            TruffleFile truffleFile = getContext().getEnv().getPublicTruffleFile(path);

            // TODO(fa) correctly honor access flags
            Set<StandardOpenOption> options = set(StandardOpenOption.READ, StandardOpenOption.WRITE);

            // we create a new channel otherwise we cannot guarantee that the cursor is exclusive
            SeekableByteChannel fileChannel;
            try {
                fileChannel = truffleFile.newByteChannel(options);
                position(fileChannel, offset);

                long actualLen;
                if (length == 0) {
                    try {
                        actualLen = PMMap.size(fileChannel) - offset;
                    } catch (IOException e) {
                        throw raiseOSError(null, OSErrorEnum.EIO, e);
                    }
                } else {
                    actualLen = length;
                }

                return factory().createMMap(clazz, fileChannel, actualLen, offset);
            } catch (IOException e) {
                throw raise(ValueError, ErrorMessages.CANNOT_MMAP_FILE);
            }
        }

        @TruffleBoundary
        private static Set<StandardOpenOption> set(StandardOpenOption... options) {
            Set<StandardOpenOption> s = new HashSet<>();
            for (StandardOpenOption o : options) {
                s.add(o);
            }
            return s;
        }

        @Specialization(guards = "isIllegal(fd)")
        @SuppressWarnings("unused")
        PMMap doAnonymous(LazyPythonClass clazz, int fd, Object length, Object tagname, PNone access, PNone offset) {
            throw raise(PythonBuiltinClassType.OSError);
        }

        protected static boolean isAnonymous(long fd) {
            return fd == -1;
        }

        protected static boolean isIllegal(long fd) {
            return fd < -1;
        }

        private void checkLength(long length) {
            if (length < 0) {
                invalidLengthProfile.enter();
                throw raise(PythonBuiltinClassType.OverflowError, ErrorMessages.MEM_MAPPED_LENGTH_MUST_BE_POSITIVE);
            }
        }

        @TruffleBoundary
        private static void position(SeekableByteChannel ch, long offset) throws IOException {
            ch.position(offset);
        }
    }

    private static class AnonymousMap implements SeekableByteChannel {
        private final byte[] data;

        private boolean open = true;
        private int cur;

        public AnonymousMap(int cap) {
            this.data = new byte[cap];
        }

        public boolean isOpen() {
            return open;
        }

        public void close() throws IOException {
            open = false;
        }

        public int read(ByteBuffer dst) throws IOException {
            int nread = Math.min(dst.remaining(), data.length - cur);
            dst.put(data, cur, nread);
            return nread;
        }

        public int write(ByteBuffer src) throws IOException {
            int nwrite = Math.min(src.remaining(), data.length - cur);
            src.get(data, cur, nwrite);
            return nwrite;
        }

        public long position() throws IOException {
            return cur;
        }

        public SeekableByteChannel position(long newPosition) throws IOException {
            if (newPosition < 0) {
                throw new IllegalArgumentException();
            }
            cur = (int) newPosition;
            return this;
        }

        public long size() throws IOException {
            return data.length;
        }

        public SeekableByteChannel truncate(long size) throws IOException {
            for (int i = 0; i < size; i++) {
                data[i] = 0;
            }
            return this;
        }
    }
}
