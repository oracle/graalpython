/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupSpecialMethodSlotNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.nodes.LoopNode;

/**
 * Equivalent of CPython's {@code PySequence_Contains}.
 */
@GenerateUncached
@ImportStatic(SpecialMethodSlot.class)
public abstract class PySequenceContainsNode extends PNodeWithContext {
    public abstract boolean execute(Frame frame, Object container, Object key);

    public final boolean execute(Object container, Object key) {
        return execute(null, container, key);
    }

    @Specialization
    boolean contains(Frame frame, Object container, Object key,
                    @Cached GetClassNode getReceiverClass,
                    @Cached(parameters = "Contains") LookupSpecialMethodSlotNode lookupContains,
                    @Cached IsBuiltinClassProfile noContainsProfile,
                    @Cached CallBinaryMethodNode callContains,
                    @Cached PyObjectGetIter getIter,
                    @Cached IsBuiltinClassProfile noIterProfile,
                    @Cached PRaiseNode raiseNode,
                    @Cached GetClassNode getIterClass,
                    @Cached(parameters = "Next") LookupSpecialMethodSlotNode lookupIternext,
                    @Cached IsBuiltinClassProfile noNextProfile,
                    @Cached CallUnaryMethodNode callNext,
                    @Cached PyObjectRichCompareBool.EqNode eqNode,
                    @Cached IsBuiltinClassProfile stopIterationProfile,
                    @Cached PyObjectIsTrueNode isTrue) {
        Object type = getReceiverClass.execute(container);
        Object contains = PNone.NO_VALUE;
        try {
            contains = lookupContains.execute(frame, type, container);
        } catch (PException e) {
            e.expectAttributeError(noContainsProfile);
        }
        Object result = PNotImplemented.NOT_IMPLEMENTED;
        if (!(contains instanceof PNone)) {
            result = callContains.executeObject(frame, contains, container, key);
        }
        if (result == PNotImplemented.NOT_IMPLEMENTED) {
            Object iterator;
            try {
                iterator = getIter.execute(frame, container);
            } catch (PException e) {
                e.expectTypeError(noIterProfile);
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, ErrorMessages.IS_NOT_A_CONTAINER, container);
            }
            Object next = PNone.NO_VALUE;
            try {
                next = lookupIternext.execute(frame, getIterClass.execute(iterator), iterator);
            } catch (PException e) {
                e.expect(PythonBuiltinClassType.AttributeError, noNextProfile);
            }
            if (next instanceof PNone) {
                throw raiseNode.raise(PythonBuiltinClassType.TypeError, ErrorMessages.OBJ_NOT_ITERABLE, iterator);
            }
            int i = 0;
            while (true) {
                if (CompilerDirectives.hasNextTier()) {
                    i++;
                }
                try {
                    if (eqNode.execute(frame, callNext.executeObject(frame, next, iterator), key)) {
                        if (CompilerDirectives.hasNextTier()) {
                            LoopNode.reportLoopCount(this, i);
                        }
                        return true;
                    }
                } catch (PException e) {
                    e.expectStopIteration(stopIterationProfile);
                    if (CompilerDirectives.hasNextTier()) {
                        LoopNode.reportLoopCount(this, i);
                    }
                    return false;
                }
            }
        } else {
            return isTrue.execute(frame, result);
        }
    }
}
