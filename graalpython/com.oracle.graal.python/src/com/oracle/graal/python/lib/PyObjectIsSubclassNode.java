/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.nodes.SpecialMethodNames.T___SUBCLASSCHECK__;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.call.special.LookupSpecialMethodNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinClassExactProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;

/**
 * Equivalent of CPython's {@code PyObject_IsSubclass}. Implements tuple recursion for
 * {@code classinfo} and the {@code __subclasscheck__} fallback protocol.
 */
@GenerateInline(false)
@GenerateUncached
@ImportStatic(PGuards.class)
public abstract class PyObjectIsSubclassNode extends PyObjectRecursiveBinaryCheckNode {
    @Override
    PyObjectRecursiveBinaryCheckNode createRecursive() {
        return create();
    }

    @Override
    PyObjectRecursiveBinaryCheckNode getUncachedRecursive() {
        return getUncached();
    }

    @NeverDefault
    public static PyObjectIsSubclassNode create() {
        return PyObjectIsSubclassNodeGen.create();
    }

    public static PyObjectIsSubclassNode getUncached() {
        return PyObjectIsSubclassNodeGen.getUncached();
    }

    @Specialization(guards = "!isPTuple(cls)", insertBefore = "doTupleConstantLen")
    static boolean isSubclass(VirtualFrame frame, Object derived, Object cls, @SuppressWarnings("unused") int depth,
                    @Bind Node inliningTarget,
                    @Cached GetClassNode getClsClassNode,
                    @Cached IsBuiltinClassExactProfile classProfile,
                    @Cached LookupSpecialMethodNode.Dynamic subclassCheckLookup,
                    @Cached CallBinaryMethodNode callSubclassCheck,
                    @Cached PyObjectIsTrueNode isTrueNode,
                    @Cached TypeNodes.GenericSubclassCheckNode genericSubclassCheckNode,
                    @Cached InlinedBranchProfile noInstanceCheckProfile) {
        if (classProfile.profileClass(inliningTarget, getClsClassNode.execute(inliningTarget, cls), PythonBuiltinClassType.PythonClass)) {
            // Avoid the lookup and call overhead when we know we're calling type.__subclasscheck__.
            return genericSubclassCheckNode.execute(frame, inliningTarget, derived, cls);
        }
        Object method = subclassCheckLookup.execute(frame, inliningTarget, getClsClassNode.execute(inliningTarget, cls), T___SUBCLASSCHECK__, cls);
        if (PGuards.isNoValue(method)) {
            noInstanceCheckProfile.enter(inliningTarget);
            return genericSubclassCheckNode.execute(frame, inliningTarget, derived, cls);
        }
        Object result = callSubclassCheck.executeObject(frame, method, cls, derived);
        return isTrueNode.execute(frame, result);
    }
}
