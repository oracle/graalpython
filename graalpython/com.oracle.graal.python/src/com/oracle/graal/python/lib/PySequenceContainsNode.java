/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.lib.PySequenceIterSearchNode.LazyPySequenceIterSeachNode;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupSpecialMethodSlotNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.nodes.Node;

/**
 * Equivalent of CPython's {@code PySequence_Contains}.
 */
@GenerateUncached
@GenerateInline
@GenerateCached(false)
@ImportStatic(SpecialMethodSlot.class)
public abstract class PySequenceContainsNode extends PNodeWithContext {
    public abstract boolean execute(Frame frame, Node inlining, Object container, Object key);

    @Specialization
    static boolean contains(Frame frame, Node inliningTarget, Object container, Object key,
                    @Cached GetClassNode getReceiverClass,
                    @Cached(parameters = "Contains", inline = false) LookupSpecialMethodSlotNode lookupContains,
                    @Cached IsBuiltinObjectProfile noContainsProfile,
                    @Cached(inline = false) CallBinaryMethodNode callContains,
                    @Cached LazyPySequenceIterSeachNode iterSearch,
                    @Cached PyObjectIsTrueNode isTrue) {
        Object type = getReceiverClass.execute(inliningTarget, container);
        Object contains = PNone.NO_VALUE;
        try {
            contains = lookupContains.execute(frame, type, container);
        } catch (PException e) {
            e.expectAttributeError(inliningTarget, noContainsProfile);
        }
        Object result = PNotImplemented.NOT_IMPLEMENTED;
        if (!(contains instanceof PNone)) {
            result = callContains.executeObject(frame, contains, container, key);
        }
        if (result == PNotImplemented.NOT_IMPLEMENTED) {
            return iterSearch.get(inliningTarget).executeCached(frame, container, key, PySequenceIterSearchNode.PY_ITERSEARCH_CONTAINS) == 1;
        } else {
            return isTrue.execute(frame, inliningTarget, result);
        }
    }
}
