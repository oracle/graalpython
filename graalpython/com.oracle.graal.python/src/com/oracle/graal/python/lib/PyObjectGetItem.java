/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;

/**
 * Equivalent of CPython's {@code PyObject_GetItem}.
 */
@GenerateUncached
public abstract class PyObjectGetItem extends PNodeWithContext {
    public abstract Object execute(Frame frame, Object object, Object key);

    @Specialization(guards = "cannotBeOverridden(object, getClassNode)", limit = "1")
    Object doList(VirtualFrame frame, PList object, Object key,
                    @SuppressWarnings("unused") @Shared("getClass") @Cached GetClassNode getClassNode,
                    @Cached ListBuiltins.GetItemNode getItemNode) {
        return getItemNode.execute(frame, object, key);
    }

    @Specialization(guards = "cannotBeOverridden(object, getClassNode)", limit = "1")
    Object doTuple(VirtualFrame frame, PTuple object, Object key,
                    @SuppressWarnings("unused") @Shared("getClass") @Cached GetClassNode getClassNode,
                    @Cached TupleBuiltins.GetItemNode getItemNode) {
        return getItemNode.execute(frame, object, key);
    }

    @Specialization(guards = "cannotBeOverridden(object, getClassNode)", limit = "1")
    Object doDict(VirtualFrame frame, PDict object, Object key,
                    @SuppressWarnings("unused") @Shared("getClass") @Cached GetClassNode getClassNode,
                    @Cached DictBuiltins.GetItemNode getItemNode) {
        return getItemNode.execute(frame, object, key);
    }

    @Specialization(replaces = {"doList", "doTuple", "doDict"})
    Object doGeneric(VirtualFrame frame, Object object, Object key,
                    @Shared("getClass") @Cached GetClassNode getClassNode,
                    @Cached(parameters = "GetItem") LookupSpecialMethodSlotNode lookupGetItem,
                    @Cached CallBinaryMethodNode callGetItem,
                    @Cached PyObjectGetItemClass getItemClass,
                    @Cached PRaiseNode raise) {
        Object type = getClassNode.execute(object);
        Object getItem = lookupGetItem.execute(frame, type, object);
        if (getItem != PNone.NO_VALUE) {
            return callGetItem.executeObject(frame, getItem, object, key);
        }
        Object item = getItemClass.execute(frame, type, key);
        if (item != PNone.NO_VALUE) {
            return item;
        }
        throw raise.raise(TypeError, ErrorMessages.OBJ_NOT_SUBSCRIPTABLE, object);
    }

    @GenerateUncached
    abstract static class PyObjectGetItemClass extends PNodeWithContext {
        public abstract Object execute(Frame frame, Object maybeType, Object key);

        @Specialization
        Object doGeneric(VirtualFrame frame, Object type, Object key,
                        @Cached TypeNodes.IsTypeNode isTypeNode,
                        @Cached PyObjectLookupAttr lookupClassGetItem,
                        @Cached CallNode callClassGetItem) {
            if (isTypeNode.execute(type)) {
                Object classGetitem = lookupClassGetItem.execute(frame, type, T___CLASS_GETITEM__);
                if (classGetitem != PNone.NO_VALUE) {
                    return callClassGetItem.execute(frame, classGetitem, key);
                }
            }
            return PNone.NO_VALUE;
        }
    }

    public static PyObjectGetItem getUncached() {
        return PyObjectGetItemNodeGen.getUncached();
    }
}
