/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.attributes.LookupInheritedAttributeNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

/**
 * Similar to CPython's lookup_maybe_method. If the found method is a function, it is returned
 * unbound. In the rare case that the method is a different type of descriptor, the descriptor is
 * called and the result returned wrapped in a {@link BoundDescriptor} object to be able to
 * differentiate it from the unbound case. {@link CallUnaryMethodNode} and other method calling
 * nodes handle this wrapper.
 */
public abstract class LookupSpecialMethodNode extends Node {
    protected final String name;
    private final boolean ignoreDescriptorException;
    @Child private LookupInheritedAttributeNode lookupGet;
    @Child private CallNode callGet;

    /**
     * Note: may return bound descriptor result wrapped in {@link BoundDescriptor} object. It needs
     * to be passed to a method calling node, such as {@link CallUnaryMethodNode}, or handled
     * explicitly.
     */
    public abstract Object execute(VirtualFrame frame, Object type, Object receiver);

    LookupSpecialMethodNode(String name, boolean ignoreDescriptorException) {
        this.name = name;
        this.ignoreDescriptorException = ignoreDescriptorException;
    }

    public static LookupSpecialMethodNode create(String name, boolean ignoreDescriptorException) {
        return LookupSpecialMethodNodeGen.create(name, ignoreDescriptorException);
    }

    public static LookupSpecialMethodNode create(String name) {
        return LookupSpecialMethodNodeGen.create(name, true);
    }

    public static class BoundDescriptor {
        public final Object descriptor;

        public BoundDescriptor(Object descriptor) {
            this.descriptor = descriptor;
        }
    }

    @Specialization
    Object lookup(VirtualFrame frame, Object type, Object receiver,
                    @Cached("create(name)") LookupAttributeInMRONode lookupAttr) {
        Object descriptor = lookupAttr.execute(type);
        if (descriptor == PNone.NO_VALUE || descriptor instanceof PBuiltinFunction || descriptor instanceof PFunction) {
            // Return unbound to avoid constructing the bound object
            return descriptor;
        }
        // Acts as a profile
        Object getMethod = ensureLookupGet().execute(descriptor);
        if (getMethod != PNone.NO_VALUE) {
            try {
                return new BoundDescriptor(ensureCallGet().execute(frame, getMethod, descriptor, receiver, type));
            } catch (PException pe) {
                if (ignoreDescriptorException) {
                    return PNone.NO_VALUE;
                }
                throw pe;
            }
        }
        return descriptor;
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

    @GenerateUncached
    public abstract static class Dynamic extends Node {

        public abstract Object execute(Object type, Object name, Object receiver, boolean ignoreDescriptorException);

        public static Dynamic create() {
            return LookupSpecialMethodNodeGen.DynamicNodeGen.create();
        }

        public static Dynamic getUncached() {
            return LookupSpecialMethodNodeGen.DynamicNodeGen.getUncached();
        }

        @Specialization
        Object lookup(Object type, Object name, Object receiver, boolean ignoreDescriptorException,
                        @Cached LookupAttributeInMRONode.Dynamic lookupAttr,
                        @Cached LookupInheritedAttributeNode.Dynamic lookupGet,
                        @Cached CallNode callGet) {
            Object descriptor = lookupAttr.execute(type, name);
            if (descriptor == PNone.NO_VALUE || descriptor instanceof PBuiltinFunction || descriptor instanceof PFunction) {
                // Return unbound to avoid constructing the bound object
                return descriptor;
            }
            Object getMethod = lookupGet.execute(descriptor, SpecialMethodNames.__GET__);
            if (getMethod != PNone.NO_VALUE) {
                try {
                    return new BoundDescriptor(callGet.execute(getMethod, descriptor, receiver, type));
                } catch (PException pe) {
                    if (ignoreDescriptorException) {
                        return PNone.NO_VALUE;
                    }
                    throw pe;
                }
            }
            return descriptor;
        }
    }
}
