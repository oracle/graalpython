/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetObjectSlotsNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryFunc.CallSlotBinaryFuncNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSizeArgFun.CallSlotSizeArgFun;
import com.oracle.graal.python.lib.PySequenceGetItemNode.IndexForSqSlot;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinClassExactProfile;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Fallback;
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

    @Specialization(guards = "isBuiltinList(object)")
    static Object doList(VirtualFrame frame, PList object, Object key,
                    @Cached ListBuiltins.GetItemNode getItemNode) {
        return getItemNode.execute(frame, object, key);
    }

    @Specialization(guards = "isBuiltinTuple(object)")
    static Object doTuple(VirtualFrame frame, PTuple object, Object key,
                    @Cached TupleBuiltins.GetItemNode getItemNode) {
        return getItemNode.execute(frame, object, key);
    }

    @InliningCutoff // TODO: inline this probably?
    @Specialization(guards = "isBuiltinDict(object)")
    static Object doDict(VirtualFrame frame, PDict object, Object key,
                    @Cached DictBuiltins.GetItemNode getItemNode) {
        return getItemNode.execute(frame, object, key);
    }

    @Specialization(replaces = {"doList", "doTuple", "doDict"})
    static Object doGeneric(VirtualFrame frame, Node inliningTarget, Object object, Object key,
                    @Cached GetObjectSlotsNode getSlotsNode,
                    @Cached PyObjectGetItemGeneric genericNode) {
        TpSlots slots = getSlotsNode.execute(inliningTarget, object);
        return genericNode.execute(frame, inliningTarget, object, slots, key);
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    abstract static class PyObjectGetItemGeneric extends PNodeWithContext {
        public abstract Object execute(Frame frame, Node inliningTarget, Object object, TpSlots objectKlassSlots, Object key);

        @Specialization(guards = "slots.mp_subscript() != null")
        static Object doMapping(VirtualFrame frame, Node inliningTarget, Object object, TpSlots slots, Object key,
                        @Cached CallSlotBinaryFuncNode callNode) {
            return callNode.execute(frame, inliningTarget, slots.mp_subscript(), object, key);
        }

        @Specialization(guards = {"slots.mp_subscript() == null", "slots.sq_item() != null", "key >= 0"})
        static Object doSequenceFastPath(VirtualFrame frame, Node inliningTarget, Object object, TpSlots slots, int key,
                        @Exclusive @Cached CallSlotSizeArgFun callSqItem) {
            return callSqItem.execute(frame, inliningTarget, slots.sq_item(), object, key);
        }

        // Note: this is uncommon path, but DSL wouldn't know, so it does not generate
        // specialization data-class.
        @Specialization(guards = {"slots.mp_subscript() == null", "slots.sq_item() != null"}, replaces = "doSequenceFastPath")
        @InliningCutoff // uncommon case: defining sq_item, but not mp_subscript
        static Object doSequence(VirtualFrame frame, Node inliningTarget, Object object, TpSlots slots, Object key,
                        @Cached IndexForSqSlot indexForSqSlot,
                        @Exclusive @Cached CallSlotSizeArgFun callSqItem) {
            int index = indexForSqSlot.execute(frame, inliningTarget, object, slots, key);
            return callSqItem.execute(frame, inliningTarget, slots.sq_item(), object, index);
        }

        @Fallback
        @InliningCutoff
        static Object tryType(VirtualFrame frame, Node inliningTarget, Object maybeType, @SuppressWarnings("unused") TpSlots slots, Object key,
                        @Cached TypeNodes.IsTypeNode isTypeNode,
                        @Cached PyObjectLookupAttr lookupClassGetItem,
                        @Cached IsBuiltinClassExactProfile isBuiltinClassProfile,
                        @Cached(inline = false) PythonObjectFactory factory,
                        @Cached(inline = false) CallNode callClassGetItem,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (isTypeNode.execute(inliningTarget, maybeType)) {
                Object classGetitem = lookupClassGetItem.execute(frame, inliningTarget, maybeType, T___CLASS_GETITEM__);
                if (!(classGetitem instanceof PNone)) {
                    return callClassGetItem.execute(frame, classGetitem, key);
                }
                if (isBuiltinClassProfile.profileClass(inliningTarget, maybeType, PythonBuiltinClassType.PythonClass)) {
                    // Special case type[int], but disallow other types so str[int] fails
                    return factory.createGenericAlias(maybeType, key);
                }
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.TYPE_NOT_SUBSCRIPTABLE, maybeType);
            }
            throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.OBJ_NOT_SUBSCRIPTABLE, maybeType);
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
