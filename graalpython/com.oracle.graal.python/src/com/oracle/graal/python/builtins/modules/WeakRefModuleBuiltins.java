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
package com.oracle.graal.python.builtins.modules;

import static com.oracle.graal.python.nodes.BuiltinNames.J__WEAKREF;
import static com.oracle.graal.python.nodes.BuiltinNames.T__WEAKREF;
import static com.oracle.graal.python.nodes.StringLiterals.T_REF;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.ArrayList;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.GetTypeMemberNode;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeMember;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.referencetype.PReferenceType;
import com.oracle.graal.python.builtins.objects.referencetype.PReferenceType.WeakRefStorage;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetMroNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.WriteUnraisableNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.InlineIsBuiltinClassProfile;
import com.oracle.graal.python.nodes.object.InlinedGetClassNode;
import com.oracle.graal.python.runtime.AsyncHandler;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.HiddenKey;

@CoreFunctions(defineModule = J__WEAKREF, isEager = true)
public class WeakRefModuleBuiltins extends PythonBuiltins {
    private static final HiddenKey weakRefQueueKey = new HiddenKey("weakRefQueue");
    private final ReferenceQueue<Object> weakRefQueue = new ReferenceQueue<>();

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return WeakRefModuleBuiltinsFactory.getFactories();
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
        weakrefModule.setAttribute(weakRefQueueKey, weakRefQueue);
        PythonBuiltinClass refType = core.lookupType(PythonBuiltinClassType.PReferenceType);
        weakrefModule.setAttribute(T_REF, refType);
        refType.setAttribute(weakRefQueueKey, weakRefQueue);
        final PythonContext ctx = core.getContext();
        core.getContext().registerAsyncAction(() -> {
            if (!ctx.isGcEnabled()) {
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
                if (reference instanceof PReferenceType.WeakRefStorage) {
                    refs.add((PReferenceType.WeakRefStorage) reference);
                }
                reference = weakRefQueue.poll();
            } while (reference != null);
            if (!refs.isEmpty()) {
                return new WeakrefCallbackAction(refs.toArray(new PReferenceType.WeakRefStorage[0]));
            }
            return null;
        });
    }

    // ReferenceType constructor
    @Builtin(name = "ReferenceType", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3, constructsClass = PythonBuiltinClassType.PReferenceType)
    @GenerateNodeFactory
    public abstract static class ReferenceTypeNode extends PythonTernaryBuiltinNode {
        @Child private ReadAttributeFromObjectNode readQueue = ReadAttributeFromObjectNode.create();
        @Child private CExtNodes.GetTypeMemberNode getTpWeaklistoffsetNode;

        @Specialization(guards = "!isNativeObject(object)")
        public PReferenceType refType(Object cls, Object object, @SuppressWarnings("unused") PNone none) {
            return factory().createReferenceType(cls, object, null, getWeakReferenceQueue());
        }

        @Specialization(guards = {"!isNativeObject(object)", "!isPNone(callback)"})
        public PReferenceType refTypeWithCallback(Object cls, Object object, Object callback) {
            return factory().createReferenceType(cls, object, callback, getWeakReferenceQueue());
        }

        @Specialization
        @SuppressWarnings("truffle-static-method")
        public PReferenceType refType(Object cls, PythonAbstractNativeObject pythonObject, Object callback,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedGetClassNode getClassNode,
                        @Cached InlineIsBuiltinClassProfile profile,
                        @Cached GetMroNode getMroNode) {
            Object actualCallback = callback instanceof PNone ? null : callback;
            Object clazz = getClassNode.execute(inliningTarget, pythonObject);

            // if the object is a type, a weak ref is allowed
            if (profile.profileClass(inliningTarget, clazz, PythonBuiltinClassType.PythonClass)) {
                return factory().createReferenceType(cls, pythonObject, actualCallback, getWeakReferenceQueue());
            }

            // if the object's type is a native type, we need to consider 'tp_weaklistoffset'
            if (PGuards.isNativeClass(clazz) || clazz instanceof PythonClass && ((PythonClass) clazz).needsNativeAllocation()) {
                for (Object base : getMroNode.execute(clazz)) {
                    if (PGuards.isNativeClass(base)) {
                        if (getTpWeaklistoffsetNode == null) {
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            getTpWeaklistoffsetNode = insert(GetTypeMemberNode.create());
                        }
                        Object tpWeaklistoffset = getTpWeaklistoffsetNode.execute(base, NativeMember.TP_WEAKLISTOFFSET);
                        if (tpWeaklistoffset != PNone.NO_VALUE) {
                            return factory().createReferenceType(cls, pythonObject, actualCallback, getWeakReferenceQueue());
                        }
                    }
                }
            }
            return refType(cls, pythonObject, actualCallback);
        }

        @Fallback
        public PReferenceType refType(@SuppressWarnings("unused") Object cls, Object object, @SuppressWarnings("unused") Object callback) {
            throw raise(PythonErrorType.TypeError, ErrorMessages.CANNOT_CREATE_WEAK_REFERENCE_TO, object);
        }

        @SuppressWarnings("unchecked")
        private ReferenceQueue<Object> getWeakReferenceQueue() {
            Object queueObject = readQueue.execute(getCore().lookupType(PythonBuiltinClassType.PReferenceType), weakRefQueueKey);
            if (queueObject instanceof ReferenceQueue) {
                ReferenceQueue<Object> queue = (ReferenceQueue<Object>) queueObject;
                return queue;
            } else {
                if (getContext().isCoreInitialized()) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new IllegalStateException("the weak reference queue was modified!");
                } else {
                    // returning a null reference queue is fine, it just means
                    // that the finalizer won't run
                    return null;
                }
            }
        }
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
        public Object getRefs(@SuppressWarnings("unused") PythonObject pythonObject) {
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
