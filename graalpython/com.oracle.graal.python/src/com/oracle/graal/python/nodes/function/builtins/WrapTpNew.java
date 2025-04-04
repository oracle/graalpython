/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.function.builtins;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetCachedTpSlotsNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetBaseClassNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsTypeNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotPythonSingle;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedLoopConditionProfile;

/**
 * Implements cpython://Objects/typeobject.c#tp_new_wrapper.
 */
public final class WrapTpNew extends SlotWrapper {
    private final PythonBuiltinClassType owner;
    @Child private CheckNode checkNode;

    public WrapTpNew(BuiltinCallNode func, PythonBuiltinClassType owner) {
        super(func);
        this.owner = owner;
    }

    @GenerateInline(false)
    abstract static class CheckNode extends Node {
        abstract void execute(PythonBuiltinClassType owner, Object cls);

        @Specialization
        static void check(PythonBuiltinClassType owner, Object cls,
                        @Bind Node inliningTarget,
                        @Cached IsTypeNode isTypeNode,
                        @Cached IsSubtypeNode isSubtypeNode,
                        @Cached GetCachedTpSlotsNode getSlotsCls,
                        @Cached GetCachedTpSlotsNode getSlotsBase1,
                        @Cached GetCachedTpSlotsNode getSlotsBase2,
                        @Cached GetBaseClassNode getBase1,
                        @Cached GetBaseClassNode getBase2,
                        @Cached InlinedLoopConditionProfile loopProfile,
                        @Cached PRaiseNode raiseNotType,
                        @Cached PRaiseNode raiseNotSubytpe,
                        @Cached PRaiseNode raiseNotSafe) {
            if (!isTypeNode.execute(inliningTarget, cls)) {
                throw raiseNotType.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.NEW_X_ISNT_TYPE_OBJ, owner.getName(), cls);
            }
            if (!isSubtypeNode.execute(cls, owner)) {
                throw raiseNotSubytpe.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.IS_NOT_SUBTYPE_OF, owner.getName(), cls, cls, owner.getName());
            }
            /*
             * CPython comment: Check that the use doesn't do something silly and unsafe like
             * object.__new__(dict). To do this, we check that the most derived base that's not a
             * heap type is this type.
             */
            // We unroll the first iteration for better specialization
            Object staticBase = cls;
            TpSlot staticBaseNew = getSlotsCls.execute(inliningTarget, staticBase).tp_new();
            if (staticBaseNew instanceof TpSlotPythonSingle) {
                staticBase = getBase1.execute(inliningTarget, staticBase);
                staticBaseNew = getSlotsBase1.execute(inliningTarget, staticBase).tp_new();
                while (loopProfile.profile(inliningTarget, staticBaseNew instanceof TpSlotPythonSingle)) {
                    staticBase = getBase2.execute(inliningTarget, staticBase);
                    staticBaseNew = getSlotsBase2.execute(inliningTarget, staticBase).tp_new();
                }
            }
            if (staticBaseNew != owner.getSlots().tp_new()) {
                throw raiseNotSafe.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.NEW_IS_NOT_SAFE_USE_ELSE, owner.getName(), cls, cls);
            }
        }

        @NeverDefault
        public static CheckNode create() {
            return WrapTpNewFactory.CheckNodeGen.create();
        }
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object cls;
        try {
            // should always succeed, since the signature check was already done
            cls = PArguments.getArgument(frame, 1);
        } catch (ArrayIndexOutOfBoundsException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException(owner.getName() + ".__new__ called without arguments");
        }
        if (cls != owner) {
            if (checkNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                checkNode = insert(CheckNode.create());
            }
            checkNode.execute(owner, cls);
        }
        return super.execute(frame);
    }
}
