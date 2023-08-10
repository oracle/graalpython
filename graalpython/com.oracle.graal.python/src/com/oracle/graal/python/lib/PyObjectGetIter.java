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

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.range.PIntRange;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupSpecialMethodSlotNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.nodes.Node;

/**
 * Equivalent PyObject_GetIter
 */
@GenerateUncached
@GenerateCached
@GenerateInline(inlineByDefault = true)
@ImportStatic(SpecialMethodSlot.class)
public abstract class PyObjectGetIter extends Node {
    public static Object executeUncached(Object obj) {
        return PyObjectGetIterNodeGen.getUncached().execute(null, null, obj);
    }

    public final Object executeCached(Frame frame, Object receiver) {
        return execute(frame, this, receiver);
    }

    public abstract Object execute(Frame frame, Node inliningTarget, Object receiver);

    @Specialization
    static Object getIterRange(PIntRange object,
                    @Shared @Cached(inline = false) PythonObjectFactory factory) {
        return factory.createIntRangeIterator(object);
    }

    @Specialization
    @InliningCutoff
    static Object getIter(Frame frame, Node inliningTarget, Object receiver,
                    @Cached GetClassNode getReceiverClass,
                    @Cached(parameters = "Iter", inline = false) LookupSpecialMethodSlotNode lookupIter,
                    @Cached PySequenceCheckNode sequenceCheckNode,
                    @Shared @Cached(inline = false) PythonObjectFactory factory,
                    @Cached PRaiseNode.Lazy raise,
                    @Cached(inline = false) CallUnaryMethodNode callIter,
                    @Cached PyIterCheckNode checkNode) {
        Object type = getReceiverClass.execute(inliningTarget, receiver);
        Object iterMethod = PNone.NO_VALUE;
        try {
            iterMethod = lookupIter.execute(frame, type, receiver);
        } catch (PException e) {
            // ignore
        }
        if (iterMethod instanceof PNone) {
            if (sequenceCheckNode.execute(inliningTarget, receiver)) {
                return factory.createSequenceIterator(receiver);
            }
        } else {
            Object result = callIter.executeObject(frame, iterMethod, receiver);
            if (!checkNode.execute(inliningTarget, result)) {
                throw raise.get(inliningTarget).raise(TypeError, ErrorMessages.RETURNED_NONITER, result);
            }
            return result;
        }
        throw raise.get(inliningTarget).raise(TypeError, ErrorMessages.OBJ_NOT_ITERABLE, receiver);
    }

    @NeverDefault
    public static PyObjectGetIter create() {
        return PyObjectGetIterNodeGen.create();
    }

    public static PyObjectGetIter getUncached() {
        return PyObjectGetIterNodeGen.getUncached();
    }
}
