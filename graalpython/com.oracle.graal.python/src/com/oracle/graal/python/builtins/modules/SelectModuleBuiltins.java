/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.builtins.ListNodes.FastConstructListNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.util.CoerceToDoubleNode;
import com.oracle.graal.python.nodes.util.CoerceToFileDescriptorNode;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.IntSequenceStorage;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ControlFlowException;

@CoreFunctions(defineModule = "select")
public class SelectModuleBuiltins extends PythonBuiltins {

    public SelectModuleBuiltins() {
        builtinConstants.put("error", PythonErrorType.OSError);
    }

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return SelectModuleBuiltinsFactory.getFactories();
    }

    @Builtin(name = "select", minNumOfPositionalArgs = 3, parameterNames = {"rlist", "wlist", "xlist", "timeout"})
    @GenerateNodeFactory
    abstract static class SelectNode extends PythonBuiltinNode {

        @Specialization(limit = "3")
        PTuple doWithoutTimeout(VirtualFrame frame, Object rlist, Object wlist, Object xlist, @SuppressWarnings("unused") PNone timeout,
                        @CachedLibrary("rlist") PythonObjectLibrary rlistLibrary,
                        @CachedLibrary("wlist") PythonObjectLibrary wlistLibrary,
                        @CachedLibrary("xlist") PythonObjectLibrary xlistLibrary,
                        @Cached CoerceToDoubleNode coerceToDoubleNode,
                        @Cached("createGetItem()") LookupAndCallBinaryNode callGetItemNode,
                        @Cached FastConstructListNode constructListNode,
                        @CachedLibrary(limit = "3") PythonObjectLibrary itemLib) {
            return doGeneric(frame, rlist, wlist, xlist, PNone.NONE, rlistLibrary, wlistLibrary, xlistLibrary, coerceToDoubleNode, callGetItemNode, constructListNode, itemLib);
        }

        @Specialization(replaces = "doWithoutTimeout", limit = "3")
        PTuple doGeneric(VirtualFrame frame, Object rlist, Object wlist, Object xlist, Object timeout,
                        @CachedLibrary("rlist") PythonObjectLibrary rlistLibrary,
                        @CachedLibrary("wlist") PythonObjectLibrary wlistLibrary,
                        @CachedLibrary("xlist") PythonObjectLibrary xlistLibrary,
                        @Cached CoerceToDoubleNode coerceToDoubleNode,
                        @Cached("createGetItem()") LookupAndCallBinaryNode callGetItemNode,
                        @Cached FastConstructListNode constructListNode,
                        @CachedLibrary(limit = "3") PythonObjectLibrary itemLib) {

            ChannelFD[] readFDs;
            ChannelFD[] writeFDs;
            ChannelFD[] xFDs;
            try {
                readFDs = seq2set(frame, rlist, rlistLibrary, itemLib, callGetItemNode, constructListNode);
                writeFDs = seq2set(frame, wlist, wlistLibrary, itemLib, callGetItemNode, constructListNode);
                xFDs = seq2set(frame, xlist, xlistLibrary, itemLib, callGetItemNode, constructListNode);
            } catch (NonSelectableChannel e) {
                // If one of the channels is not selectable, we do what we did before: just return
                // everything.
                return factory().createTuple(new Object[]{rlist, wlist, xlist});
            }

            // IMPORTANT: The meaning of the timeout value is slightly different:
            // 'timeout == 0.0' means we should not block and return immediately. However, the Java
            // API does not allow a non-blocking select. So we set the timeout to 1 ms.
            //
            // 'timeout == None' means we should wait indefinitely, i.e., we need to pass 0 to the
            // Java API.
            long timeoutMillis;
            if (!PGuards.isPNone(timeout)) {
                double timeoutSecs = coerceToDoubleNode.execute(frame, timeout);
                timeoutMillis = timeoutSecs != 0.0 ? (long) (timeoutSecs * 1000.0) : 1L;
            } else {
                timeoutMillis = 0;
            }

            if (timeoutMillis < 0) {
                throw raise(PythonBuiltinClassType.ValueError, "timeout must be non-negative");
            }

            try {
                doSelect(readFDs, writeFDs, xFDs, timeoutMillis);
            } catch (ClosedChannelException e) {
                // If the channel was closed (this can only happen concurrently between resolving
                // the FD to the channel and registration), we provided an incorrect file
                // descriptor. The errno code for that is EBADF.
                throw raiseOSError(frame, OSErrorEnum.EBADF);
            } catch (IOException e) {
                throw raiseOSError(frame, e);
            } catch (RuntimeException e) {
                throw raise(PythonBuiltinClassType.SystemError, e);
            }

            return factory().createTuple(new PList[]{toList(readFDs), toList(writeFDs), toList(xFDs)});
        }

        @TruffleBoundary
        private static void doSelect(ChannelFD[] readFDs, ChannelFD[] writeFDs, ChannelFD[] xFDs, long timeoutMillis) throws IOException {
            Selector selector = Selector.open();

            for (ChannelFD readFD : readFDs) {
                readFD.channel.configureBlocking(false);
                readFD.channel.register(selector, SelectionKey.OP_READ);
            }

            for (ChannelFD writeFD : writeFDs) {
                writeFD.channel.configureBlocking(false);
                writeFD.channel.register(selector, SelectionKey.OP_WRITE);
            }

            for (ChannelFD xFD : xFDs) {
                // TODO(fa): not sure if these ops are representing
                // "exceptional condition pending"
                xFD.channel.configureBlocking(false);
                xFD.channel.register(selector, SelectionKey.OP_ACCEPT | SelectionKey.OP_CONNECT);
            }

            int selected = selector.select(timeoutMillis);

            // remove non-selected channels from given lists
            int deleted = 0;
            for (int i = 0; i < readFDs.length; i++) {
                ChannelFD readFD = readFDs[i];
                SelectionKey selectionKey = readFD.channel.keyFor(selector);
                if (!selectionKey.isReadable()) {
                    readFDs[i] = null;
                    deleted++;
                }
            }

            for (int i = 0; i < writeFDs.length; i++) {
                ChannelFD writeFD = writeFDs[i];
                SelectionKey selectionKey = writeFD.channel.keyFor(selector);
                if (!selectionKey.isWritable()) {
                    writeFDs[i] = null;
                    deleted++;
                }
            }

            for (int i = 0; i < xFDs.length; i++) {
                ChannelFD xFD = xFDs[i];
                SelectionKey selectionKey = xFD.channel.keyFor(selector);
                if (!(selectionKey.isAcceptable() || selectionKey.isConnectable())) {
                    xFDs[i] = null;
                    deleted++;
                }
            }
            assert selected == (readFDs.length + writeFDs.length + xFDs.length) - deleted;
        }

        private ChannelFD[] seq2set(VirtualFrame frame, Object sequence, PythonObjectLibrary sequenceLib, PythonObjectLibrary itemLib, LookupAndCallBinaryNode callGetItemNode,
                        FastConstructListNode constructListNode) {
            PArguments.ThreadState threadState = PArguments.getThreadState(frame);
            int len = sequenceLib.lengthWithState(sequence, threadState);
            ChannelFD[] result = new ChannelFD[len];

            PSequence pSequence = constructListNode.execute(sequence);

            for (int i = 0; i < len; i++) {
                int fd = itemLib.asFileDescriptorWithState(callGetItemNode.executeObject(frame, pSequence, i), threadState);
                Channel fileChannel = getContext().getResources().getFileChannel(fd);
                if (!(fileChannel instanceof SelectableChannel)) {
                    throw NonSelectableChannel.INSTANCE;
                }
                result[i] = new ChannelFD(fd, (SelectableChannel) fileChannel);
            }
            return result;
        }

        private PList toList(ChannelFD[] arr) {
            int cnt = 0;
            for (ChannelFD channelFD : arr) {
                if (channelFD != null) {
                    cnt++;
                }
            }
            int[] fds = new int[cnt];
            for (ChannelFD channelFD : arr) {
                if (channelFD != null) {
                    fds[fds.length - (cnt--)] = channelFD.fd;
                }
            }
            return factory().createList(new IntSequenceStorage(fds));
        }

        static LookupAndCallBinaryNode createGetItem() {
            return LookupAndCallBinaryNode.create(SpecialMethodNames.__GETITEM__);
        }

        @ValueType
        private static final class ChannelFD {
            private final int fd;
            private final SelectableChannel channel;

            private ChannelFD(int fd, SelectableChannel channel) {
                this.fd = fd;
                this.channel = channel;
            }
        }

        private static final class NonSelectableChannel extends ControlFlowException {
            private static final long serialVersionUID = 1L;

            static final NonSelectableChannel INSTANCE = new NonSelectableChannel();
        }

    }
}
