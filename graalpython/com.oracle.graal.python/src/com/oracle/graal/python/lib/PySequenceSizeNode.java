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
import static com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper.LENFUNC;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTiming;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupSpecialMethodSlotNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaLongExactNode;
import com.oracle.graal.python.runtime.sequence.PSequence;
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
 * Equivalent of CPython's {@code PySequence_Size}. For native object it would only call
 * {@code sq_length} and never {@code mp_length}. Unlike CPython's {@code PySequence_Size} this node
 * does not return -1 on error it instead raises TypeError
 */
@ImportStatic({PGuards.class, SpecialMethodSlot.class, ExternalFunctionNodes.PExternalFunctionWrapper.class})
@GenerateInline(value = false)
@GenerateUncached
public abstract class PySequenceSizeNode extends Node {
    private static final NativeCAPISymbol SYMBOL = NativeCAPISymbol.FUN_PY_SEQUENCE_SIZE;
    private static final CApiTiming C_API_TIMING = CApiTiming.create(true, SYMBOL.getName());

    public abstract long execute(Frame frame, Object object);

    public final long execute(Object object) {
        return execute(null, object);
    }

    @Specialization
    static long doPSequence(PSequence object) {
        return object.getSequenceStorage().length();
    }

    @Specialization(guards = {"!isNativeObject(object)", "!isPSequence(object)"})
    static long doGenericManaged(VirtualFrame frame, Object object,
                    @Bind("this") Node inliningTarget,
                    @Cached GetClassNode getClassNode,
                    @Cached PySequenceCheckNode sequenceCheckNode,
                    @Cached PyMappingCheckNode mappingCheckNode,
                    @Cached(parameters = "Len") LookupSpecialMethodSlotNode lookupLen,
                    @Cached CallUnaryMethodNode callLen,
                    @Cached CastToJavaLongExactNode toJavaLongExactNode,
                    @Cached PRaiseNode.Lazy raise) {
        if (sequenceCheckNode.execute(inliningTarget, object)) {
            Object type = getClassNode.execute(inliningTarget, object);
            Object len = lookupLen.execute(frame, type, object);
            assert len != PNone.NO_VALUE;
            Object result = callLen.executeObject(frame, len, object);
            try {
                return toJavaLongExactNode.execute(inliningTarget, result);
            } catch (CannotCastException cce) {
                throw raise.get(inliningTarget).raise(TypeError, ErrorMessages.OBJ_CANNOT_BE_INTERPRETED_AS_INTEGER, object);
            }
        }
        if (mappingCheckNode.execute(inliningTarget, object)) {
            throw raise.get(inliningTarget).raise(TypeError, ErrorMessages.IS_NOT_A_SEQUENCE, object);
        } else {
            throw raise.get(inliningTarget).raise(TypeError, ErrorMessages.OBJ_HAS_NO_LEN, object);
        }
    }

    @Specialization
    static long doNative(VirtualFrame frame, PythonAbstractNativeObject object,
                    @Bind("this") Node inliningTarget,
                    @Cached CApiTransitions.PythonToNativeNode toNativeNode,
                    @Cached CExtNodes.ImportCAPISymbolNode importCAPISymbolNode,
                    @Cached ExternalFunctionNodes.ExternalFunctionInvokeNode invokeNode) {
        Object executable = importCAPISymbolNode.execute(inliningTarget, SYMBOL);
        Object size = invokeNode.execute(frame, LENFUNC, C_API_TIMING, SYMBOL.getTsName(), executable, new Object[]{toNativeNode.execute(object)});
        assert PGuards.isInteger(size);
        return (long) size;
    }
}
