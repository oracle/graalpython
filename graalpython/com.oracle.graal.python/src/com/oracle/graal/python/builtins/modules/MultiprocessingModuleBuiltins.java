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

import java.util.List;
import java.util.concurrent.Semaphore;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.PythonLanguage.SharedMultiprocessingData;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.thread.PSemLock;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.builtins.ListNodes;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.builtins.Python3Core;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OSError;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.util.CastToJavaIntLossyNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.util.ArrayBuilder;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.CachedLanguage;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
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
                        @Cached CastToJavaIntExactNode castUnlinkToIntNode,
                        @CachedLanguage PythonLanguage lang) {
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
                if (semaphoreExists(lang, name)) {
                    throw raise(PythonBuiltinClassType.FileExistsError, ErrorMessages.SEMAPHORE_NAME_TAKEN, name);
                } else {
                    semaphorePut(lang, semaphore, name);
                }
            }
            return factory().createSemLock(cls, name, kind, semaphore);
        }

        @TruffleBoundary
        private static Object semaphorePut(PythonLanguage lang, Semaphore semaphore, String name) {
            return lang.namedSemaphores.put(name, semaphore);
        }

        @TruffleBoundary
        private static boolean semaphoreExists(PythonLanguage lang, String name) {
            return lang.namedSemaphores.containsKey(name);
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
        PNone doit(String name,
                        @CachedLanguage PythonLanguage lang) {
            Semaphore prev = semaphoreRemove(name, lang);
            if (prev == null) {
                throw raise(PythonBuiltinClassType.FileNotFoundError, ErrorMessages.NO_SUCH_FILE_OR_DIR, "semaphores", name);
            }
            return PNone.NONE;
        }

        @TruffleBoundary
        private static Semaphore semaphoreRemove(String name, PythonLanguage lang) {
            return lang.namedSemaphores.remove(name);
        }
    }

    @Builtin(name = "_spawn_context", minNumOfPositionalArgs = 2, parameterNames = {"fd", "sentinel"})
    @GenerateNodeFactory
    abstract static class SpawnContextNode extends PythonBuiltinNode {
        @Specialization
        static long spawn(int fd, int sentinel,
                        @CachedContext(PythonLanguage.class) PythonContext context) {
            long tid = context.spawnTruffleContext(fd, sentinel);
            return convertTid(tid);
        }
    }

    @Builtin(name = "_gettid")
    @GenerateNodeFactory
    abstract static class GetTidNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        static long getTid() {
            return convertTid(Thread.currentThread().getId());
        }
    }

    @Builtin(name = "_waittid", minNumOfPositionalArgs = 2, parameterNames = {"tid", "options"})
    @GenerateNodeFactory
    abstract static class WaitTidNode extends PythonBinaryBuiltinNode {
        @Specialization
        PTuple waittid(long id, @SuppressWarnings("unused") int options,
                        @CachedLanguage PythonLanguage lang) {
            long tid = convertTid(id);
            // TODO implement for options - WNOHANG and 0
            Thread thread = lang.getChildContextThread(tid);
            if (thread != null && thread.isAlive()) {
                return factory().createTuple(new Object[]{0, 0});
            }

            PythonContext.ChildContextData data = lang.getChildContextData(tid);
            return factory().createTuple(new Object[]{id, data != null ? data.getExitCode() : 0});
        }
    }

    @Builtin(name = "_terminate_spawned_thread", minNumOfPositionalArgs = 1, parameterNames = {"tid"})
    @GenerateNodeFactory
    abstract static class TerminateThreadNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        Object terminate(long id,
                        @CachedLanguage PythonLanguage language) {
            Thread thread = language.getChildContextThread(convertTid(id));
            if (thread != null && thread.isAlive()) {
                PythonContext.ChildContextData data = language.getChildContextData(convertTid(id));
                try {
                    data.awaitRunning();
                    TruffleContext truffleCtx = data.getCtx();
                    if (!truffleCtx.isCancelling() && data.compareAndSetExiting(false, true)) {
                        LOGGER.fine("terminating spawned thread");
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
        PTuple pipe(@Cached GilNode gil,
                        @CachedContext(PythonLanguage.class) PythonContext ctx,
                        @SuppressWarnings("unused") @CachedLanguage PythonLanguage language,
                        @Cached("language.getSharedMultiprocessingData()") SharedMultiprocessingData sharedData) {
            int[] pipe;
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
                        @SuppressWarnings("unused") @CachedLanguage PythonLanguage lang,
                        @Cached("lang.getSharedMultiprocessingData()") SharedMultiprocessingData sharedData,
                        @CachedLibrary("data") PythonObjectLibrary lib,
                        @Cached GilNode gil) {
            gil.release(true);
            try {
                byte[] bytes = lib.getBufferBytes(data);
                sharedData.addSharedContextData(fd, bytes, () -> {
                    throw PRaiseNode.raiseUncached(this, OSError, ErrorMessages.BAD_FILE_DESCRIPTOR);
                });
                return bytes.length;
            } catch (UnsupportedMessageException ex) {
                throw CompilerDirectives.shouldNotReachHere();
            } finally {
                gil.acquire();
            }
        }

        @Specialization(limit = "1")
        Object doWrite(long fd, PBytes data,
                        @SuppressWarnings("unused") @CachedLanguage PythonLanguage lang,
                        @Cached("lang.getSharedMultiprocessingData()") SharedMultiprocessingData sharedData,
                        @CachedLibrary("data") PythonObjectLibrary lib,
                        @Cached GilNode gil) {
            return doWrite((int) fd, data, lang, sharedData, lib, gil);
        }
    }

    @Builtin(name = "_read", minNumOfPositionalArgs = 2, parameterNames = {"fd", "length"})
    @GenerateNodeFactory
    public abstract static class ReadNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object doRead(int fd, @SuppressWarnings("unused") Object length,
                        @SuppressWarnings("unused") @CachedLanguage PythonLanguage lang,
                        @Cached("lang.getSharedMultiprocessingData()") SharedMultiprocessingData sharedData,
                        @Cached GilNode gil) {
            gil.release(true);
            try {
                Object data = sharedData.takeSharedContextData(this, fd, () -> {
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
                        @SuppressWarnings("unused") @CachedLanguage PythonLanguage lang,
                        @Cached("lang.getSharedMultiprocessingData()") SharedMultiprocessingData sharedData,
                        @Cached GilNode gil) {
            return doRead((int) fd, length, lang, sharedData, gil);
        }
    }

    @Builtin(name = "_close", minNumOfPositionalArgs = 1, parameterNames = {"fd"})
    @GenerateNodeFactory
    public abstract static class CloseNode extends PythonUnaryBuiltinNode {
        @Specialization
        static PNone close(@SuppressWarnings("unused") Object fd) {
            // noop, gets cleared on ctx close
            return PNone.NONE;
        }
    }

    @Builtin(name = "_select", minNumOfPositionalArgs = 1, parameterNames = {"rlist", "timeout"})
    @GenerateNodeFactory
    abstract static class SelectNode extends PythonBuiltinNode {
        @Specialization
        Object doGeneric(VirtualFrame frame, Object rlist,
                        @SuppressWarnings("unused") @CachedLanguage PythonLanguage lang,
                        @Cached("lang.getSharedMultiprocessingData()") SharedMultiprocessingData sharedData,
                        @Cached PyObjectSizeNode sizeNode,
                        @Cached("createGetItem()") LookupAndCallBinaryNode callGetItemNode,
                        @Cached ListNodes.FastConstructListNode constructListNode,
                        @Cached CastToJavaIntLossyNode castToJava,
                        @Cached GilNode gil) {
            ArrayBuilder<Integer> notEmpty = new ArrayBuilder<>();
            gil.release(true);
            try {
                PSequence pSequence = constructListNode.execute(frame, rlist);
                for (int i = 0; i < sizeNode.execute(frame, pSequence); i++) {
                    Object pythonObject = callGetItemNode.executeObject(frame, pSequence, i);
                    int fd = toInt(castToJava, pythonObject);
                    if (!sharedData.isEmpty(fd, () -> {
                        throw PRaiseNode.getUncached().raise(OSError, ErrorMessages.BAD_FILE_DESCRIPTOR);
                    })) {
                        notEmpty.add(fd);
                    }
                }
            } finally {
                gil.acquire();
            }
            PList res = factory().createList(notEmpty.toObjectArray(new Object[0]));
            return res;
        }

        private static int toInt(CastToJavaIntLossyNode castToJava, Object pythonObject) {
            try {
                return castToJava.execute(pythonObject);
            } catch (CannotCastException e) {
                throw CompilerDirectives.shouldNotReachHere();
            }
        }

        static LookupAndCallBinaryNode createGetItem() {
            return LookupAndCallBinaryNode.create(SpecialMethodNames.__GETITEM__);
        }
    }

}
