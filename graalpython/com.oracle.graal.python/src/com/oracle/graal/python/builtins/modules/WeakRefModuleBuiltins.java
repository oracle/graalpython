/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.removeNativeWeakRef;
import static com.oracle.graal.python.nodes.BuiltinNames.J__WEAKREF;
import static com.oracle.graal.python.nodes.BuiltinNames.T__WEAKREF;
import static com.oracle.graal.python.nodes.BuiltinNames.T_PROXY_TYPE;
import static com.oracle.graal.python.nodes.BuiltinNames.T_CALLABLE_PROXY_TYPE;
import static com.oracle.graal.python.nodes.HiddenAttr.WEAK_REF_QUEUE;
import static com.oracle.graal.python.nodes.StringLiterals.T_REF;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.ArrayList;
import java.util.List;

import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.referencetype.PReferenceType;
import com.oracle.graal.python.builtins.objects.referencetype.PReferenceType.WeakRefStorage;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.TypeFlags;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.nodes.HiddenAttr;
import com.oracle.graal.python.nodes.WriteUnraisableNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.runtime.AsyncHandler;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

@CoreFunctions(defineModule = J__WEAKREF, isEager = true)
public final class WeakRefModuleBuiltins extends PythonBuiltins {
    private final ReferenceQueue<Object> weakRefQueue = new ReferenceQueue<>();

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return WeakRefModuleBuiltinsFactory.getFactories();
    }

    public static int getBuiltinTypeWeaklistoffset(PythonBuiltinClassType cls) {
        // @formatter:off
        return switch (cls) {
            case PythonClass -> 368; // type
            case PSet, // set
                    PFrozenSet // frozenset
                    -> 192;
            case PMemoryView, // memoryview
                    PCode // code
                    -> 136;
            case PBuiltinFunctionOrMethod, // builtin_function_or_method
                    PGenerator, // generator
                    PCoroutine, // coroutine
                    PythonModule, // module
                    PThreadLocal, // _thread._local
                    PRLock, // _thread.RLock
                    PBufferedRWPair, // _io.BufferedRWPair
                    PAsyncGenerator, // async_generator
                    PGenericAlias
                    -> 40;
            case PMethod, // method
                    PFileIO, // _io.FileIO
                    PTee // itertools._tee
                    -> 32;
            case PFunction -> 80; // function
            case PIOBase, // _io._IOBase
                    PRawIOBase, // _io._RawIOBase
                    PBufferedIOBase, // _io._BufferedIOBase
                    PTextIOBase, // _io._TextIOBase
                    PLock // _thread.LockType
                    -> 24;
            case PBytesIO, // _io.BytesIO
                    PPartial // functools.partial
                    -> 48;
            case PStringIO -> 112; // _io.StringIO
            case PBufferedReader, // _io.BufferedReader
                    PBufferedWriter, // _io.BufferedWriter
                    PBufferedRandom, // _io.BufferedRandom
                    PLruCacheWrapper
                    -> 144;
            case PickleBuffer -> 96; // _pickle.PickleBuffer
            case PTextIOWrapper -> 176; // _io.TextIOWrapper
            case POrderedDict -> 104;
            default -> 0;
            // @formatter:on
        };
    }

    private static class WeakrefCallbackAction extends AsyncHandler.AsyncPythonAction {
        private final WeakRefStorage[] references;
        private int index;

        public WeakrefCallbackAction(WeakRefStorage[] weakRefStorages) {
            this.references = weakRefStorages;
            this.index = 0;
        }

        @Override
        public Object callable() {
            return references[index].getCallback();
        }

        @Override
        public Object[] arguments() {
            return new Object[]{references[index].getRef()};
        }

        @Override
        public boolean proceed() {
            index++;
            return index < references.length;
        }

        @Override
        protected void handleException(PException e) {
            /*
             * see 'weakrefobject.c:handle_callback'
             */
            WriteUnraisableNode.getUncached().execute(e.getEscapedException(), null, callable());
        }
    }

    @Override
    public void postInitialize(Python3Core core) {
        super.postInitialize(core);
        PythonModule weakrefModule = core.lookupBuiltinModule(T__WEAKREF);
        HiddenAttr.WriteNode.executeUncached(weakrefModule, WEAK_REF_QUEUE, weakRefQueue);
        PythonBuiltinClass refType = core.lookupType(PythonBuiltinClassType.PReferenceType);
        weakrefModule.setAttribute(T_REF, refType);
        HiddenAttr.WriteNode.executeUncached(refType, WEAK_REF_QUEUE, weakRefQueue);
        // FIXME we should intrinsify those types
        TypeNodes.SetTypeFlagsNode.executeUncached(weakrefModule.getAttribute(T_PROXY_TYPE), TypeFlags.DEFAULT | TypeFlags.HAVE_GC);
        TypeNodes.SetTypeFlagsNode.executeUncached(weakrefModule.getAttribute(T_CALLABLE_PROXY_TYPE), TypeFlags.DEFAULT | TypeFlags.HAVE_GC);
        final PythonContext ctx = core.getContext();
        core.getContext().registerAsyncAction(() -> {
            if (!ctx.getGcState().isEnabled()) {
                return null;
            }
            Reference<? extends Object> reference = null;
            if (PythonOptions.AUTOMATIC_ASYNC_ACTIONS) {
                try {
                    reference = weakRefQueue.remove();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            } else {
                reference = weakRefQueue.poll();
            }
            ArrayList<PReferenceType.WeakRefStorage> refs = new ArrayList<>();
            do {
                if (reference instanceof PReferenceType.WeakRefStorage ref) {
                    long ptr = ref.getPointer();
                    if (ptr > 0) {
                        if (!ctx.isFinalizing()) {
                            /* avoid race condition of deallocating an object at exit */
                            refs.add(ref);
                            removeNativeWeakRef(ctx, ptr);
                        }
                    } else {
                        refs.add(ref);
                    }
                }
                reference = weakRefQueue.poll();
            } while (reference != null);
            if (!refs.isEmpty()) {
                return new WeakrefCallbackAction(refs.toArray(new PReferenceType.WeakRefStorage[0]));
            }
            return null;
        });
    }

    // getweakrefcount(obj)
    @Builtin(name = "getweakrefcount", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class GetWeakRefCountNode extends PythonBuiltinNode {
        @Specialization
        public Object getCount(PReferenceType pReferenceType) {
            return pReferenceType.getWeakRefCount();
        }

        @Fallback
        public Object getCount(@SuppressWarnings("unused") Object none) {
            return 0;
        }
    }

    // getweakrefs()
    @Builtin(name = "getweakrefs", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class GetWeakRefsNode extends PythonBuiltinNode {
        @Specialization
        public Object getRefs(@SuppressWarnings("unused") Object object) {
            return PNone.NONE;
        }
    }

    // _remove_dead_weakref()
    @Builtin(name = "_remove_dead_weakref", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class RemoveDeadWeakRefsNode extends PythonBuiltinNode {
        @Specialization
        public Object removeDeadRefs(@SuppressWarnings("unused") PDict dict, @SuppressWarnings("unused") Object key) {
            // TODO: do the removal from the dict ... (_weakref.c:60)
            return PNone.NONE;
        }
    }
}
