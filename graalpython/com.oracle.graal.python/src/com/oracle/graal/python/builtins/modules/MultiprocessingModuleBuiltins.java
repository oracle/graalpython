/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OSError;

import java.util.List;
import java.util.concurrent.Semaphore;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.thread.PSemLock;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PConstructAndRaiseNode;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.builtins.ListNodes;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.nodes.util.CastToJavaIntLossyNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonContext.SharedMultiprocessingData;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.ArrayBuilder;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;

@CoreFunctions(defineModule = "_multiprocessing")
public class MultiprocessingModuleBuiltins extends PythonBuiltins {

    private static final TruffleLogger LOGGER = PythonLanguage.getLogger(MultiprocessingModuleBuiltins.class);

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return MultiprocessingModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        // TODO: add necessary entries to the dict
        builtinConstants.put("flags", core.factory().createDict());
        super.initialize(core);
    }

    @Builtin(name = "SemLock", parameterNames = {"cls", "kind", "value", "maxvalue", "name", "unlink"}, constructsClass = PythonBuiltinClassType.PSemLock)
    @GenerateNodeFactory
    abstract static class ConstructSemLockNode extends PythonBuiltinNode {
        @Specialization
        PSemLock construct(Object cls, Object kindObj, Object valueObj, Object maxvalueObj, Object nameObj, Object unlinkObj,
                        @Cached CastToJavaStringNode castNameNode,
                        @Cached CastToJavaIntExactNode castKindToIntNode,
                        @Cached CastToJavaIntExactNode castValueToIntNode,
                        @Cached CastToJavaIntExactNode castMaxvalueToIntNode,
                        @Cached CastToJavaIntExactNode castUnlinkToIntNode) {
            int kind = castKindToIntNode.execute(kindObj);
            if (kind != PSemLock.RECURSIVE_MUTEX && kind != PSemLock.SEMAPHORE) {
                throw raise(PythonBuiltinClassType.ValueError, ErrorMessages.UNRECOGNIZED_KIND);
            }
            int value = castValueToIntNode.execute(valueObj);
            castMaxvalueToIntNode.execute(maxvalueObj); // executed for the side-effect, but ignored
                                                        // on posix
            Semaphore semaphore = newSemaphore(value);
            int unlink = castUnlinkToIntNode.execute(unlinkObj);
            String name;
            try {
                name = castNameNode.execute(nameObj);
            } catch (CannotCastException e) {
                throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.ARG_D_MUST_BE_S_NOT_P, "SemLock", 4, "str", nameObj);
            }
            if (unlink == 0) {
                // CPython creates a named semaphore, and if unlink != 0 unlinks
                // it directly so it cannot be access by other processes. We
                // have to explicitly link it, so we do that here if we
                // must. CPython always uses O_CREAT | O_EXCL for creating named
                // semaphores, so a conflict raises.
                SharedMultiprocessingData multiprocessing = getContext().getSharedMultiprocessingData();
                if (multiprocessing.getNamedSemaphore(name) != null) {
                    throw raise(PythonBuiltinClassType.FileExistsError, ErrorMessages.SEMAPHORE_NAME_TAKEN, name);
                } else {
                    multiprocessing.putNamedSemaphore(name, semaphore);
                }
            }
            return factory().createSemLock(cls, name, kind, semaphore);
        }

        @TruffleBoundary
        private static Semaphore newSemaphore(int value) {
            return new Semaphore(value);
        }
    }

    @GenerateNodeFactory
    @Builtin(name = "sem_unlink", parameterNames = {"name"})
    abstract static class SemUnlink extends PythonUnaryBuiltinNode {
        @Specialization
        PNone doit(VirtualFrame frame, String name,
                        @Cached PConstructAndRaiseNode constructAndRaiseNode) {
            Semaphore prev = getContext().getSharedMultiprocessingData().removeNamedSemaphore(name);
            if (prev == null) {
                throw constructAndRaiseNode.raiseFileNotFoundError(frame, ErrorMessages.NO_SUCH_FILE_OR_DIR, "semaphores", name);
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "_spawn_context", minNumOfPositionalArgs = 3, parameterNames = {"fd", "sentinel", "keepFds"})
    @GenerateNodeFactory
    abstract static class SpawnContextNode extends PythonBuiltinNode {
        @Specialization
        long spawn(VirtualFrame frame, int fd, int sentinel, PList keepFds,
                        @Cached SequenceStorageNodes.LenNode lenNode,
                        @Cached SequenceStorageNodes.GetItemNode getItem,
                        @Cached CastToJavaIntExactNode castToJavaIntNode) {
            SequenceStorage storage = keepFds.getSequenceStorage();
            int length = lenNode.execute(storage);
            int[] keep = new int[length];
            for (int i = 0; i < length; i++) {
                Object item = getItem.execute(frame, storage, i);
                keep[i] = castToJavaIntNode.execute(item);
            }
            PythonContext context = getContext();
            long tid = context.spawnTruffleContext(fd, sentinel, keep);
            return convertTid(tid);
        }
    }

    @Builtin(name = "_gettid")
    @GenerateNodeFactory
    abstract static class GetTidNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        long getTid() {
            return convertTid(getContext().getMainThread().getId());
        }
    }

    @Builtin(name = "_waittid", minNumOfPositionalArgs = 2, parameterNames = {"tid", "options"})
    @GenerateNodeFactory
    abstract static class WaitTidNode extends PythonBinaryBuiltinNode {
        @Specialization
        PTuple waittid(long id, @SuppressWarnings("unused") int options) {
            long tid = convertTid(id);
            // TODO implement for options - WNOHANG and 0
            final SharedMultiprocessingData multiprocessing = getContext().getSharedMultiprocessingData();
            Thread thread = multiprocessing.getChildContextThread(tid);
            if (thread != null && thread.isAlive()) {
                return factory().createTuple(new Object[]{0, 0, 0});
            }

            PythonContext.ChildContextData data = multiprocessing.getChildContextData(tid);
            /*
             * The assumption made here is that once _waittid returns the exit code, the caller
             * caches it and never calls _waittid again, so we do not need to keep the data and can
             * clean it. See popen_truffleprocess that calls the _waittid builtin.
             */
            multiprocessing.removeChildContextData(tid);
            return factory().createTuple(new Object[]{id, data.wasSignaled() ? data.getExitCode() : 0, data.getExitCode()});
        }
    }

    @Builtin(name = "_terminate_spawned_thread", minNumOfPositionalArgs = 2, parameterNames = {"tid", "sig"})
    @GenerateNodeFactory
    abstract static class TerminateThreadNode extends PythonBinaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        Object terminate(long id, PInt sig) {
            final SharedMultiprocessingData multiprocessing = getContext().getSharedMultiprocessingData();
            Thread thread = multiprocessing.getChildContextThread(convertTid(id));
            if (thread != null && thread.isAlive()) {
                PythonContext.ChildContextData data = multiprocessing.getChildContextData(convertTid(id));
                try {
                    data.awaitRunning();
                    TruffleContext truffleCtx = data.getTruffleContext();
                    if (truffleCtx != null && !truffleCtx.isCancelling() && data.compareAndSetExiting(false, true)) {
                        LOGGER.fine("terminating spawned thread");
                        data.setSignaled(sig.intValue());
                        truffleCtx.closeCancelled(this, "_terminate_spawned_thread");
                    }
                } catch (InterruptedException ex) {
                    LOGGER.finest("got interrupt while terminating spawned thread");
                }
            }
            return PNone.NONE;
        }
    }

    private static long convertTid(long tid) {
        return tid * -1;
    }

    @Builtin(name = "_pipe", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    abstract static class PipeNode extends PythonBuiltinNode {

        @Specialization
        PTuple pipe(@Cached GilNode gil) {
            int[] pipe;
            PythonContext ctx = getContext();
            SharedMultiprocessingData sharedData = ctx.getSharedMultiprocessingData();
            gil.release(true);
            try {
                pipe = sharedData.pipe();
                ctx.getChildContextFDs().add(pipe[0]);
                ctx.getChildContextFDs().add(pipe[1]);
            } finally {
                gil.acquire();
            }
            return factory().createTuple(new Object[]{pipe[0], pipe[1]});
        }
    }

    @Builtin(name = "_write", minNumOfPositionalArgs = 2, parameterNames = {"fd", "data"})
    @GenerateNodeFactory
    public abstract static class WriteNode extends PythonBinaryBuiltinNode {
        @Specialization(limit = "1")
        Object doWrite(int fd, PBytes data,
                        @CachedLibrary("data") PythonBufferAccessLibrary bufferLib,
                        @Cached GilNode gil) {
            SharedMultiprocessingData sharedData = getContext().getSharedMultiprocessingData();
            gil.release(true);
            try {
                byte[] bytes = bufferLib.getCopiedByteArray(data);
                sharedData.addPipeData(fd, bytes,
                                () -> {
                                    throw PRaiseNode.raiseUncached(this, OSError, ErrorMessages.BAD_FILE_DESCRIPTOR);
                                },
                                () -> {
                                    throw PConstructAndRaiseNode.getUncached().raiseOSError(null, OSErrorEnum.EPIPE.getNumber(), OSErrorEnum.EPIPE.getMessage(), null);
                                });
                return bytes.length;
            } finally {
                gil.acquire();
            }
        }

        @Specialization(limit = "1")
        Object doWrite(long fd, PBytes data,
                        @CachedLibrary("data") PythonBufferAccessLibrary bufferLib,
                        @Cached GilNode gil) {
            return doWrite((int) fd, data, bufferLib, gil);
        }
    }

    @Builtin(name = "_read", minNumOfPositionalArgs = 2, parameterNames = {"fd", "length"})
    @GenerateNodeFactory
    public abstract static class ReadNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object doRead(int fd, @SuppressWarnings("unused") Object length,
                        @Cached GilNode gil) {
            SharedMultiprocessingData sharedData = getContext().getSharedMultiprocessingData();
            gil.release(true);
            try {
                Object data = sharedData.takePipeData(this, fd, () -> {
                    throw PRaiseNode.raiseUncached(this, OSError, ErrorMessages.BAD_FILE_DESCRIPTOR);
                });
                if (data == PNone.NONE) {
                    return factory().createBytes(PythonUtils.EMPTY_BYTE_ARRAY, 0, 0);
                }
                return factory().createBytes((byte[]) data);
            } finally {
                gil.acquire();
            }
        }

        @Specialization
        Object doRead(long fd, Object length,
                        @Cached GilNode gil) {
            return doRead((int) fd, length, gil);
        }
    }

    @Builtin(name = "_close", minNumOfPositionalArgs = 1, parameterNames = {"fd"})
    @GenerateNodeFactory
    public abstract static class CloseNode extends PythonUnaryBuiltinNode {
        @Specialization
        PNone close(@SuppressWarnings("unused") int fd) {
            assert fd < 0;
            SharedMultiprocessingData sharedData = getContext().getSharedMultiprocessingData();
            if (!sharedData.decrementFDRefCount(fd)) {
                sharedData.closePipe(fd);
            }
            return PNone.NONE;
        }

        @Specialization
        PNone close(@SuppressWarnings("unused") long fd) {
            return close((int) fd);
        }
    }

    @Builtin(name = "_select", minNumOfPositionalArgs = 1, parameterNames = {"rlist"})
    @GenerateNodeFactory
    abstract static class SelectNode extends PythonBuiltinNode {
        @Specialization
        Object doGeneric(VirtualFrame frame, Object rlist,
                        @Cached PyObjectSizeNode sizeNode,
                        @Cached PyObjectGetItem getItem,
                        @Cached ListNodes.FastConstructListNode constructListNode,
                        @Cached CastToJavaIntLossyNode castToJava,
                        @Cached GilNode gil) {
            ArrayBuilder<Integer> notEmpty = new ArrayBuilder<>();
            SharedMultiprocessingData sharedData = getContext().getSharedMultiprocessingData();
            PSequence pSequence = constructListNode.execute(frame, rlist);
            for (int i = 0; i < sizeNode.execute(frame, pSequence); i++) {
                Object pythonObject = getItem.execute(frame, pSequence, i);
                int fd = toInt(castToJava, pythonObject);
                gil.release(true);
                try {
                    if (!sharedData.isBlocking(fd)) {
                        notEmpty.add(fd);
                    }
                } finally {
                    gil.acquire();
                }
            }
            return factory().createList(notEmpty.toObjectArray(new Object[0]));
        }

        private static int toInt(CastToJavaIntLossyNode castToJava, Object pythonObject) {
            try {
                return castToJava.execute(pythonObject);
            } catch (CannotCastException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }
    }

}
