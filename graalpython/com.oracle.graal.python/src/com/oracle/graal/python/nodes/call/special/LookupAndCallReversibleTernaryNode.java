/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsSameTypeNode;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.util.Supplier;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.ReportPolymorphism.Megamorphic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.strings.TruffleString;

@ImportStatic({SpecialMethodNames.class, PythonOptions.class})
public abstract class LookupAndCallReversibleTernaryNode extends LookupAndCallTernaryNode {

    @Child private GetClassNode thirdGetClassNode;
    @Child private CallTernaryMethodNode reverseDispatchNode;
    @Child private CallTernaryMethodNode thirdDispatchNode;
    @Child protected LookupSpecialMethodNode getThirdAttrNode;

    @Child protected NotImplementedHandler handler;
    protected final Supplier<NotImplementedHandler> handlerFactory;

    LookupAndCallReversibleTernaryNode(TruffleString name, Supplier<NotImplementedHandler> handlerFactory) {
        super(name);
        this.handlerFactory = handlerFactory;
    }

    public LookupAndCallReversibleTernaryNode(SpecialMethodSlot slot, Supplier<NotImplementedHandler> handlerFactory) {
        super(slot);
        this.handlerFactory = handlerFactory;
    }

    @Specialization(guards = "v.getClass() == cachedVClass", limit = "getCallSiteInlineCacheMaxDepth()")
    Object callObjectR(VirtualFrame frame, Object v, Object w, Object z,
                    @SuppressWarnings("unused") @Cached("v.getClass()") Class<?> cachedVClass,
                    @Cached("createLookup()") LookupSpecialBaseNode getattr,
                    @Cached("createLookup()") LookupSpecialBaseNode getattrR,
                    @Cached GetClassNode getClass,
                    @Cached GetClassNode getClassR,
                    @Cached IsSubtypeNode isSubtype,
                    @Cached IsSameTypeNode isSameTypeNode,
                    @Cached BranchProfile notImplementedBranch) {
        return doCallObjectR(frame, v, w, z, getattr, getattrR, getClass, getClassR, isSubtype, isSameTypeNode, notImplementedBranch);
    }

    @Specialization(replaces = "callObjectR")
    @Megamorphic
    Object callObjectRMegamorphic(VirtualFrame frame, Object v, Object w, Object z,
                    @Cached("createLookup()") LookupSpecialBaseNode getattr,
                    @Cached("createLookup()") LookupSpecialBaseNode getattrR,
                    @Cached GetClassNode getClass,
                    @Cached GetClassNode getClassR,
                    @Cached IsSubtypeNode isSubtype,
                    @Cached IsSameTypeNode isSameTypeNode,
                    @Cached BranchProfile notImplementedBranch) {
        return doCallObjectR(frame, v, w, z, getattr, getattrR, getClass, getClassR, isSubtype, isSameTypeNode, notImplementedBranch);
    }

    private Object doCallObjectR(VirtualFrame frame, Object v, Object w, Object z, LookupSpecialBaseNode getattr, LookupSpecialBaseNode getattrR, GetClassNode getClass, GetClassNode getClassR,
                    IsSubtypeNode isSubtype, IsSameTypeNode isSameTypeNode, BranchProfile notImplementedBranch) {
        // c.f. mostly slot_nb_power and wrap_ternaryfunc_r. like
        // cpython://Object/abstract.c#ternary_op we try all three combinations, and the structure
        // of this method is modeled after this. However, this method also merges the logic from
        // slot_nb_power/wrap_ternaryfunc_r in that it swaps arguments around. The reversal is
        // undone for builtin functions in BuiltinFunctionRootNode, just like it would be undone in
        // CPython using its slot wrappers
        Object leftClass = getClass.execute(v);
        Object rightClass = getClassR.execute(w);

        Object result = PNotImplemented.NOT_IMPLEMENTED;
        Object leftCallable = getattr.execute(frame, leftClass, v);
        Object rightCallable = PNone.NO_VALUE;

        if (!isSameTypeNode.execute(leftClass, rightClass)) {
            rightCallable = getattrR.execute(frame, rightClass, w);
            if (rightCallable == leftCallable) {
                rightCallable = PNone.NO_VALUE;
            }
        }
        if (leftCallable != PNone.NO_VALUE) {
            if (rightCallable != PNone.NO_VALUE && isSubtype.execute(frame, rightClass, leftClass)) {
                result = ensureReverseDispatch().execute(frame, rightCallable, v, w, z);
                if (result != PNotImplemented.NOT_IMPLEMENTED) {
                    return result;
                }
                rightCallable = PNone.NO_VALUE;
            }
            result = dispatchNode.execute(frame, leftCallable, v, w, z);
            if (result != PNotImplemented.NOT_IMPLEMENTED) {
                return result;
            }
        }
        if (rightCallable != PNone.NO_VALUE) {
            result = ensureReverseDispatch().execute(frame, rightCallable, v, w, z);
            if (result != PNotImplemented.NOT_IMPLEMENTED) {
                return result;
            }
        }

        Object zCallable = ensureGetAttrZ().execute(frame, ensureThirdGetClass().execute(z), z);
        if (zCallable != PNone.NO_VALUE && zCallable != leftCallable && zCallable != rightCallable) {
            result = ensureThirdDispatch().execute(frame, zCallable, v, w, z);
            if (result != PNotImplemented.NOT_IMPLEMENTED) {
                return result;
            }
        }

        notImplementedBranch.enter();
        if (handlerFactory != null) {
            if (handler == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                handler = insert(handlerFactory.get());
            }
            return handler.execute(v, w, z);
        }
        return result;
    }

    protected GetClassNode ensureThirdGetClass() {
        if (thirdGetClassNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            thirdGetClassNode = insert(GetClassNode.create());
        }
        return thirdGetClassNode;
    }

    protected CallTernaryMethodNode ensureReverseDispatch() {
        // this also serves as a branch profile
        if (reverseDispatchNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            reverseDispatchNode = insert(CallTernaryMethodNode.create());
        }
        return reverseDispatchNode;
    }

    protected CallTernaryMethodNode ensureThirdDispatch() {
        // this also serves as a branch profile
        if (thirdDispatchNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            thirdDispatchNode = insert(CallTernaryMethodNode.create());
        }
        return thirdDispatchNode;
    }

    protected LookupSpecialMethodNode ensureGetAttrZ() {
        // this also serves as a branch profile
        if (getThirdAttrNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getThirdAttrNode = insert(LookupSpecialMethodNode.create(name));
        }
        return getThirdAttrNode;
    }
}
