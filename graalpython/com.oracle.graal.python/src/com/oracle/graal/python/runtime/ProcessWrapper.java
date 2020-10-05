/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.Channels;
import java.nio.channels.Pipe;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

/**
 * Extends a Java {@link Process} by providing selectable channels for reading the child's stdout
 * and stderr and writing to child's stdin. This is accomplished by creating (for each stream) a
 * {@link Pipe} and a thread that 'pumps' the data between the pipe and the stdio/stdout/stderr of
 * the child process. This has several limitations and drawbacks:
 * <ul>
 * <li>needs extra resources (pipes, threads)</li>
 * <li>pumping data between channels introduces buffering</li>
 * <li>errors detected in the pumping threads need to be reported by the other end of the pipe,
 * which obscures the stacktrace and makes debugging more difficult</li>
 * <li>closing one of the channels provided by this class might not result in immediate closing of
 * the child's stream - this means that the child might not detect EPIPE in the first write
 * operation it attempts</li>
 * </ul>
 */
public final class ProcessWrapper extends Process {

    private final Process process;
    private final Pipe inPipe;
    private final Pipe outPipe;
    private final Pipe errPipe;
    private final ChannelPump inThread;
    private final ChannelPump outThread;
    private final ChannelPump errThread;

    public ProcessWrapper(Process process, boolean pipeStdin, boolean pipeStdout, boolean pipeStderr) throws IOException {
        this.process = process;
        if (pipeStdin) {
            inPipe = Pipe.open();
            inThread = new ChannelPump("stdin", inPipe.source(), Channels.newChannel(process.getOutputStream()));
            inThread.start();
        } else {
            inPipe = null;
            inThread = null;
        }
        if (pipeStdout) {
            outPipe = Pipe.open();
            outThread = new ChannelPump("stdout", Channels.newChannel(process.getInputStream()), outPipe.sink());
            outThread.start();
        } else {
            outPipe = null;
            outThread = null;
        }
        if (pipeStderr) {
            errPipe = Pipe.open();
            errThread = new ChannelPump("stderr", Channels.newChannel(process.getErrorStream()), errPipe.sink());
            errThread.start();
        } else {
            errPipe = null;
            errThread = null;
        }
    }

    public Channel getOutputChannel() {
        assert inPipe != null;
        return inPipe.sink();
    }

    public Channel getInputChannel() {
        assert outPipe != null;
        return outPipe.source();
    }

    public Channel getErrorChannel() {
        assert errPipe != null;
        return errPipe.source();
    }

    @Override
    public OutputStream getOutputStream() {
        throw new UnsupportedOperationException();
    }

    @Override
    public InputStream getInputStream() {
        throw new UnsupportedOperationException();
    }

    @Override
    public InputStream getErrorStream() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int waitFor() throws InterruptedException {
        int retVal = process.waitFor();
        if (inThread != null) {
            inThread.join();
        }
        if (outThread != null) {
            outThread.join();
        }
        if (errThread != null) {
            errThread.join();
        }
        return retVal;
    }

    @Override
    public int exitValue() {
        return process.exitValue();
    }

    @Override
    public void destroy() {
        process.destroy();
    }

    @Override
    public Process destroyForcibly() {
        return process.destroyForcibly();
    }

    @Override
    public boolean isAlive() {
        return process.isAlive();
    }

    static class ChannelPump extends Thread {

        private static final int BUF_SIZE = 8192;

        private final ReadableByteChannel source;
        private final WritableByteChannel sink;

        ChannelPump(String streamName, ReadableByteChannel source, WritableByteChannel sink) {
            super("ChannelPump-" + streamName);
            this.source = source;
            this.sink = sink;
        }

        @Override
        public void run() {
            ByteBuffer buf = ByteBuffer.allocate(BUF_SIZE);
            try {
                while (true) {
                    buf.clear();
                    int r = source.read(buf);
                    if (r == -1) {
                        return;
                    }
                    buf.flip();
                    while (buf.hasRemaining()) {
                        sink.write(buf);
                    }
                }
            } catch (IOException e) {
                // TODO report the error to the other end of the pipe
                // (for now just close both channels)
            } finally {
                try {
                    sink.close();
                } catch (IOException ignored) {
                }
                try {
                    source.close();
                } catch (IOException ignored) {
                }
            }
        }
    }
}
