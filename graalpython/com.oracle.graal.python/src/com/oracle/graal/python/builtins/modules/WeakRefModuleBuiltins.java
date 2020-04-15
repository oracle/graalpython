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

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.CExtNodes.GetTypeMemberNode;
import com.oracle.graal.python.builtins.objects.cext.NativeMemberNames;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.referencetype.PReferenceType;
import com.oracle.graal.python.builtins.objects.referencetype.PReferenceType.WeakRefStorage;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.object.GetLazyClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.AsyncHandler;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.HiddenKey;

@CoreFunctions(defineModule = "_weakref")
public class WeakRefModuleBuiltins extends PythonBuiltins {
    private static final HiddenKey weakRefQueueKey = new HiddenKey("weakRefQueue");
    private final ReferenceQueue<Object> weakRefQueue = new ReferenceQueue<>();

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return WeakRefModuleBuiltinsFactory.getFactories();
    }

    private static class WeakrefCallbackAction implements AsyncHandler.AsyncAction {
        private final WeakRefStorage reference;

        public WeakrefCallbackAction(PReferenceType.WeakRefStorage reference) {
            this.reference = reference;
        }

        public Object callable() {
            return reference.getCallback();
        }

        public Object[] arguments() {
            return new Object[]{reference.getRef()};
        }
    }

    @Override
    public void postInitialize(PythonCore core) {
        super.postInitialize(core);
        PythonModule weakrefModule = core.lookupBuiltinModule("_weakref");
        weakrefModule.setAttribute(weakRefQueueKey, weakRefQueue);
        core.lookupType(PythonBuiltinClassType.PReferenceType).setAttribute(weakRefQueueKey, weakRefQueue);

        core.getContext().registerAsyncAction(() -> {
            Reference<? extends Object> reference = null;
            try {
                reference = weakRefQueue.remove();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (reference instanceof PReferenceType.WeakRefStorage) {
                return new WeakrefCallbackAction((PReferenceType.WeakRefStorage) reference);
            } else {
                return null;
            }
        });
    }

    // ReferenceType constructor
    @Builtin(name = "ReferenceType", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3, constructsClass = PythonBuiltinClassType.PReferenceType)
    @GenerateNodeFactory
    public abstract static class ReferenceTypeNode extends PythonBuiltinNode {
        @Child private ReadAttributeFromObjectNode readQueue = ReadAttributeFromObjectNode.create();
        @Child private CExtNodes.GetTypeMemberNode getTpWeaklistoffsetNode;

        @Specialization(guards = "!isNativeObject(object)")
        public PReferenceType refType(LazyPythonClass cls, Object object, @SuppressWarnings("unused") PNone none) {
            return factory().createReferenceType(cls, object, null, getWeakReferenceQueue());
        }

        @Specialization(guards = "!isNativeObject(object)")
        public PReferenceType refType(LazyPythonClass cls, Object object, Object callback) {
            if (callback instanceof PNone) {
                return factory().createReferenceType(cls, object, null, getWeakReferenceQueue());
            } else {
                return factory().createReferenceType(cls, object, callback, getWeakReferenceQueue());
            }
        }

        @Specialization
        public PReferenceType refType(LazyPythonClass cls, PythonAbstractNativeObject pythonObject, Object callback,
                        @Cached("create()") GetLazyClassNode getClassNode,
                        @Cached("create()") IsBuiltinClassProfile profile) {
            Object actualCallback = callback instanceof PNone ? null : callback;
            LazyPythonClass clazz = getClassNode.execute(pythonObject);

            // if the object is a type, a weak ref is allowed
            if (profile.profileClass(clazz, PythonBuiltinClassType.PythonClass)) {
                return factory().createReferenceType(cls, pythonObject, actualCallback, getWeakReferenceQueue());
            }

            // if the object's type is a native type, we need to consider 'tp_weaklistoffset'
            if (PGuards.isNativeClass(clazz)) {
                if (getTpWeaklistoffsetNode == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    getTpWeaklistoffsetNode = insert(GetTypeMemberNode.create());
                }
                Object tpWeaklistoffset = getTpWeaklistoffsetNode.execute(clazz, NativeMemberNames.TP_WEAKLISTOFFSET);
                if (tpWeaklistoffset != PNone.NO_VALUE) {
                    return factory().createReferenceType(cls, pythonObject, actualCallback, getWeakReferenceQueue());
                }
            }
            return refType(cls, pythonObject, actualCallback);
        }

        @Fallback
        public PReferenceType refType(@SuppressWarnings("unused") Object cls, Object object, @SuppressWarnings("unused") Object callback) {
            throw raise(PythonErrorType.TypeError, "cannot create weak reference to '%p' object", object);
        }

        @SuppressWarnings("unchecked")
        private ReferenceQueue<Object> getWeakReferenceQueue() {
            Object queueObject = readQueue.execute(getCore().lookupType(PythonBuiltinClassType.PReferenceType), weakRefQueueKey);
            if (queueObject instanceof ReferenceQueue) {
                ReferenceQueue<Object> queue = (ReferenceQueue<Object>) queueObject;
                return queue;
            } else {
                if (getContext().getCore().isInitialized()) {
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

        @Specialization
        public Object getCount(@SuppressWarnings("unused") PNone none) {
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
