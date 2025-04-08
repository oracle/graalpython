/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetCachedTpSlotsNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.expression.BinaryOpNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.bytecode.OperationProxy;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

@GenerateInline(false)
@GenerateUncached
@OperationProxy.Proxyable
public abstract class PyNumberPowerNode extends BinaryOpNode {
    public abstract Object execute(VirtualFrame frame, Object v, Object w, Object z);

    @Override
    public final Object execute(VirtualFrame frame, Object left, Object right) {
        return execute(frame, left, right, PNone.NONE);
    }

    @Specialization
    public static Object doIt(VirtualFrame frame, Object v, Object w, Object z,
                    @Bind Node inliningTarget,
                    @Cached GetClassNode getVClass,
                    @Cached GetClassNode getWClass,
                    @Cached GetCachedTpSlotsNode getVSlots,
                    @Cached GetCachedTpSlotsNode getWSlots,
                    @Cached CallTernaryOpNode callTernaryOpNode,
                    @Cached PRaiseNode raiseNode) {
        Object classV = getVClass.execute(inliningTarget, v);
        Object classW = getWClass.execute(inliningTarget, w);
        TpSlot slotV = getVSlots.execute(inliningTarget, classV).nb_power();
        TpSlot slotW = getWSlots.execute(inliningTarget, classW).nb_power();
        Object result = callTernaryOpNode.execute(frame, inliningTarget, v, classV, slotV, w, classW, slotW, z);
        if (result != PNotImplemented.NOT_IMPLEMENTED) {
            return result;
        }
        return raiseNotSupported(inliningTarget, v, w, z, raiseNode);
    }

    @InliningCutoff
    private static PException raiseNotSupported(Node inliningTarget, Object v, Object w, Object z, PRaiseNode raiseNode) {
        if (z == PNone.NONE) {
            return raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.UNSUPPORTED_OPERAND_TYPES_FOR_S_P_AND_P, "** or pow()", v, w);
        } else {
            return raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.UNSUPPORTED_OPERAND_TYPES_FOR_S_P_P_P, "** or pow()", v, w, z);
        }
    }

    @NeverDefault
    public static PyNumberPowerNode create() {
        return PyNumberPowerNodeGen.create();
    }

    public static PyNumberPowerNode getUncached() {
        return PyNumberPowerNodeGen.getUncached();
    }
}
