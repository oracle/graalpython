/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.call.special;

import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
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
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Similar to CPython's lookup_maybe_method. If the found method is a function, it is returned
 * unbound. In the rare case that the method is a different type of descriptor, the descriptor is
 * called and the result returned wrapped in a {@code BoundDescriptor} object to be able to
 * differentiate it from the unbound case. {@link CallUnaryMethodNode} and other method calling
 * nodes handle this wrapper.
 */
public abstract class LookupSpecialMethodNode extends LookupSpecialBaseNode {
    protected final TruffleString name;

    public LookupSpecialMethodNode(TruffleString name) {
        this.name = name;
    }

    @NeverDefault
    public static LookupSpecialMethodNode create(TruffleString name) {
        return LookupSpecialMethodNodeGen.create(name);
    }

    @Specialization
    Object lookup(VirtualFrame frame, Object type, Object receiver,
                    @Bind("this") Node inliningTarget,
                    @Cached(parameters = "name") LookupAttributeInMRONode lookupMethod,
                    @Cached MaybeBindDescriptorNode bind) {
        return bind.execute(frame, inliningTarget, lookupMethod.execute(type), receiver, type);
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    public abstract static class Dynamic extends Node {

        public abstract Object execute(Frame frame, Node inliningTarget, Object type, TruffleString name, Object receiver);

        public static Object executeUncached(Object type, TruffleString name, Object receiver) {
            return LookupSpecialMethodNodeGen.DynamicNodeGen.getUncached().execute(null, null, type, name, receiver);
        }

        @Specialization
        static Object lookup(VirtualFrame frame, Node inliningTarget, Object type, TruffleString name, Object receiver,
                        @Cached MaybeBindDescriptorNode bind,
                        @Cached(inline = false) LookupAttributeInMRONode.Dynamic lookupAttr) {
            Object descriptor = lookupAttr.execute(type, name);
            return bind.execute(frame, inliningTarget, descriptor, receiver, type);
        }
    }
}
