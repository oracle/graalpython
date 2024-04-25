/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper.DELITEM;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.ExternalFunctionWrapperInvokeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTiming;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions;
import com.oracle.graal.python.builtins.objects.type.MethodsFlags;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupSpecialMethodSlotNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

/**
 * Equivalent of CPython's {@code PySequence_DelItem}. For native object it would only call
 * {@code sq_ass_item} and never {@code mp_ass_subscript}.
 */
@ImportStatic({PGuards.class, SpecialMethodSlot.class, ExternalFunctionNodes.PExternalFunctionWrapper.class})
@GenerateInline(false) // One lazy usage, one eager usage => not worth it
@GenerateUncached
public abstract class PySequenceDelItemNode extends Node {
    private static final NativeCAPISymbol SYMBOL = NativeCAPISymbol.FUN_PY_SEQUENCE_DEL_ITEM;
    private static final CApiTiming C_API_TIMING = CApiTiming.create(true, SYMBOL.getName());

    public abstract Object execute(Frame frame, Object object, int index);

    public final Object execute(Object object, int index) {
        return execute(null, object, index);
    }

    @Specialization(guards = "!isNativeObject(object)")
    static Object doGenericManaged(VirtualFrame frame, Object object, int index,
                    @Bind("this") Node inliningTarget,
                    @Cached GetClassNode getClassNode,
                    @Cached GetMethodsFlagsNode getMethodsFlagsNode,
                    @Cached(parameters = "DelItem") LookupSpecialMethodSlotNode lookupDelItem,
                    @Cached CallBinaryMethodNode callDelItem,
                    @Cached PRaiseNode.Lazy raise) {
        Object type = getClassNode.execute(inliningTarget, object);
        if ((getMethodsFlagsNode.execute(inliningTarget, type) & MethodsFlags.SQ_ASS_ITEM) != 0) {
            Object delItem = lookupDelItem.execute(frame, type, object);
            assert delItem != PNone.NO_VALUE;
            return callDelItem.executeObject(frame, delItem, object, index);
        }
        if ((getMethodsFlagsNode.execute(inliningTarget, type) & MethodsFlags.MP_ASS_SUBSCRIPT) != 0) {
            throw raise.get(inliningTarget).raise(TypeError, ErrorMessages.IS_NOT_A_SEQUENCE, object);
        } else {
            throw raise.get(inliningTarget).raise(TypeError, ErrorMessages.OBJ_DOES_NOT_SUPPORT_ITEM_DELETION, object);
        }
    }

    @Specialization
    static Object doNative(VirtualFrame frame, PythonAbstractNativeObject object, int index,
                    @Bind("this") Node inliningTarget,
                    @Cached CApiTransitions.PythonToNativeNode toNativeNode,
                    @Cached ExternalFunctionWrapperInvokeNode invokeNode) {
        Object executable = CApiContext.getNativeSymbol(inliningTarget, SYMBOL);
        return invokeNode.execute(frame, DELITEM, C_API_TIMING, SYMBOL.getTsName(), executable, new Object[]{toNativeNode.execute(object), index});
    }
}
