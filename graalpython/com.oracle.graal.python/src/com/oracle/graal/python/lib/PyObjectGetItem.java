/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.lib;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___CLASS_GETITEM__;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.dict.DictBuiltins;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.list.ListBuiltins;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.tuple.TupleBuiltins;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupSpecialMethodSlotNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinClassExactProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

/**
 * Equivalent of CPython's {@code PyObject_GetItem}.
 */
@GenerateUncached
@GenerateInline(inlineByDefault = true)
@GenerateCached
public abstract class PyObjectGetItem extends PNodeWithContext {
    public static Object executeUncached(Object receiver, Object key) {
        return PyObjectGetItemNodeGen.getUncached().execute(null, null, receiver, key);
    }

    public final Object executeCached(Frame frame, Object object, Object key) {
        return execute(frame, this, object, key);
    }

    public abstract Object execute(Frame frame, Node inliningTarget, Object object, Object key);

    @Specialization(guards = "cannotBeOverriddenForImmutableType(object)")
    static Object doList(VirtualFrame frame, PList object, Object key,
                    @Cached ListBuiltins.GetItemNode getItemNode) {
        return getItemNode.execute(frame, object, key);
    }

    @Specialization(guards = "cannotBeOverriddenForImmutableType(object)")
    static Object doTuple(VirtualFrame frame, PTuple object, Object key,
                    @Cached TupleBuiltins.GetItemNode getItemNode) {
        return getItemNode.execute(frame, object, key);
    }

    @InliningCutoff // TODO: inline this probably?
    @Specialization(guards = "cannotBeOverriddenForImmutableType(object)")
    static Object doDict(VirtualFrame frame, PDict object, Object key,
                    @Cached DictBuiltins.GetItemNode getItemNode) {
        return getItemNode.execute(frame, object, key);
    }

    @InliningCutoff // no point inlining the complex case
    @Specialization(replaces = {"doList", "doTuple", "doDict"})
    static Object doGeneric(VirtualFrame frame, Node inliningTarget, Object object, Object key,
                    @Cached GetClassNode getClassNode,
                    @Cached(parameters = "GetItem", inline = false) LookupSpecialMethodSlotNode lookupGetItem,
                    @Cached(inline = false) CallBinaryMethodNode callGetItem,
                    @Cached(inline = false) LazyPyObjectGetItemClass getItemClass,
                    @Cached PRaiseNode.Lazy raise) {
        Object type = getClassNode.execute(inliningTarget, object);
        Object getItem = lookupGetItem.execute(frame, type, object);
        if (getItem != PNone.NO_VALUE) {
            return callGetItem.executeObject(frame, getItem, object, key);
        }
        Object item = getItemClass.get(inliningTarget).execute(frame, object, key);
        if (item != PNone.NO_VALUE) {
            return item;
        }
        throw raise.get(inliningTarget).raise(TypeError, ErrorMessages.OBJ_NOT_SUBSCRIPTABLE, object);
    }

    @GenerateUncached
    @GenerateCached
    @GenerateInline(false)
    abstract static class LazyPyObjectGetItemClass extends Node {
        public final PyObjectGetItemClass get(Node inliningTarget) {
            return execute(inliningTarget);
        }

        abstract PyObjectGetItemClass execute(Node inliningTarget);

        @Specialization
        static PyObjectGetItemClass doIt(@Cached PyObjectGetItemClass node) {
            return node;
        }
    }

    @GenerateUncached
    @GenerateInline(false) // used only lazily
    abstract static class PyObjectGetItemClass extends PNodeWithContext {
        public abstract Object execute(Frame frame, Object maybeType, Object key);

        @Specialization
        static Object doGeneric(VirtualFrame frame, Object type, Object key,
                        @Bind("this") Node inliningTarget,
                        @Cached TypeNodes.IsTypeNode isTypeNode,
                        @Cached PyObjectLookupAttr lookupClassGetItem,
                        @Cached IsBuiltinClassExactProfile isBuiltinClassProfile,
                        @Cached PythonObjectFactory factory,
                        @Cached CallNode callClassGetItem) {
            if (isTypeNode.execute(inliningTarget, type)) {
                Object classGetitem = lookupClassGetItem.execute(frame, inliningTarget, type, T___CLASS_GETITEM__);
                if (classGetitem != PNone.NO_VALUE) {
                    return callClassGetItem.execute(frame, classGetitem, key);
                }
                if (isBuiltinClassProfile.profileClass(inliningTarget, type, PythonBuiltinClassType.PythonClass)) {
                    // Special case type[int], but disallow other types so str[int] fails
                    return factory.createGenericAlias(type, key);
                }
            }
            return PNone.NO_VALUE;
        }
    }

    @NeverDefault
    public static PyObjectGetItem create() {
        return PyObjectGetItemNodeGen.create();
    }

    public static PyObjectGetItem getUncached() {
        return PyObjectGetItemNodeGen.getUncached();
    }
}
