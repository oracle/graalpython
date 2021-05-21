/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.BuiltinMethodDescriptor;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.LookupInMROBaseNode;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ValueProfile;

public abstract class LookupSpecialBaseNode extends Node {
    @Child LookupInMROBaseNode lookupNode; // this should be initialized by the subclass
    @Child private LookupInheritedAttributeNode lookupGet;
    @Child private CallNode callGet;
    private final ValueProfile lookupResProfile = ValueProfile.createClassProfile();

    public final Object execute(VirtualFrame frame, Object type, Object receiver) {
        Object descriptor = lookupResProfile.profile(lookupNode.execute(type));
        if (descriptor == PNone.NO_VALUE || descriptor instanceof PBuiltinFunction || descriptor instanceof PFunction || descriptor instanceof BuiltinMethodDescriptor) {
            // Return unbound to avoid constructing the bound object
            return descriptor;
        }
        // Acts as a profile
        Object getMethod = ensureLookupGet().execute(descriptor);
        if (getMethod != PNone.NO_VALUE) {
            return new BoundDescriptor(ensureCallGet().execute(frame, getMethod, descriptor, receiver, type));
        }
        // CPython considers non-descriptors already bound
        return new BoundDescriptor(descriptor);
    }

    private LookupInheritedAttributeNode ensureLookupGet() {
        if (lookupGet == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            lookupGet = insert(LookupInheritedAttributeNode.create(SpecialMethodNames.__GET__));
        }
        return lookupGet;
    }

    private CallNode ensureCallGet() {
        if (callGet == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            callGet = insert(CallNode.create());
        }
        return callGet;
    }

    public static class BoundDescriptor {
        public final Object descriptor;

        public BoundDescriptor(Object descriptor) {
            this.descriptor = descriptor;
        }
    }
}
