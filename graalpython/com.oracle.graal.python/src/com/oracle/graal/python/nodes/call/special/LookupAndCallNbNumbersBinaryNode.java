/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.objects.type.MethodsFlags.NB_MULTIPLY;
import static com.oracle.graal.python.builtins.objects.type.MethodsFlags.SLOT1BINFULL;
import static com.oracle.graal.python.builtins.objects.type.MethodsFlags.SQ_REPEAT;
import static com.oracle.truffle.api.dsl.Cached.Shared;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsSameTypeNode;
import com.oracle.graal.python.lib.GetMethodsFlagsNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.util.Supplier;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

// cpython://Objects/abstract.c#binary_op1
// cpython://Objects/typeobject.c#SLOT1BINFULL
@ImportStatic({PythonOptions.class, SpecialMethodSlot.class})
abstract class LookupAndCallNbNumbersBinaryNode extends LookupAndCallBinaryNode {

    LookupAndCallNbNumbersBinaryNode(Supplier<NotImplementedHandler> handlerFactory) {
        super(handlerFactory, false);
    }

    private static Object lookupAttrId(VirtualFrame frame, Object obj, Object objClass,
                    LookupSpecialMethodSlotNode getattr) {
        return getattr.execute(frame, objClass, obj);
    }

    // cpython://Objects/abstract.c#PyNumber_Multiply
    protected abstract static class PyNumberMultiplyNode extends LookupAndCallNbNumbersBinaryNode {
        PyNumberMultiplyNode(Supplier<NotImplementedHandler> handlerFactory) {
            super(handlerFactory);
        }

        @Specialization(guards = {"left.getClass() == cachedLeftClass", "right.getClass() == cachedRightClass"}, limit = "5")
        @SuppressWarnings("truffle-static-method")
        Object mulC(VirtualFrame frame, Object left, Object right,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Cached("left.getClass()") Class<?> cachedLeftClass,
                        @SuppressWarnings("unused") @Cached("right.getClass()") Class<?> cachedRightClass,
                        @Exclusive @Cached GetClassNode getClassNode,
                        @Exclusive @Cached("create(Mul)") LookupSpecialMethodSlotNode getattr,
                        @Exclusive @Cached GetMethodsFlagsNode getMethodsFlagsNode,
                        @Exclusive @Cached GetClassNode getlClassNode,
                        @Exclusive @Cached GetClassNode getrClassNode,
                        @Exclusive @Cached InlinedConditionProfile p1,
                        @Exclusive @Cached InlinedConditionProfile p2,
                        @Exclusive @Cached InlinedConditionProfile p3,
                        @Exclusive @Cached BinaryOp1Node binaryOp1Node,
                        @Exclusive @Cached Slot1BINFULLNode slot1BINFULLNode) {
            return mul(frame, left, right, inliningTarget, getClassNode, getattr, getMethodsFlagsNode, getlClassNode,
                            getrClassNode, p1, p2, p3, binaryOp1Node, slot1BINFULLNode);
        }

        @Specialization(replaces = "mulC")
        @SuppressWarnings("truffle-static-method")
        Object mul(VirtualFrame frame, Object left, Object right,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached GetClassNode getClassNode,
                        @Exclusive @Cached("create(Mul)") LookupSpecialMethodSlotNode getattr,
                        @Exclusive @Cached GetMethodsFlagsNode getMethodsFlagsNode,
                        @Exclusive @Cached GetClassNode getlClassNode,
                        @Exclusive @Cached GetClassNode getrClassNode,
                        @Exclusive @Cached InlinedConditionProfile p1,
                        @Exclusive @Cached InlinedConditionProfile p2,
                        @Exclusive @Cached InlinedConditionProfile p3,
                        @Exclusive @Cached BinaryOp1Node binaryOp1Node,
                        @Exclusive @Cached Slot1BINFULLNode slot1BINFULLNode) {
            Object lClass = getlClassNode.execute(inliningTarget, left);
            Object rClass = getrClassNode.execute(inliningTarget, right);
            long lFlags = getMethodsFlagsNode.execute(inliningTarget, lClass);
            long rFlags = getMethodsFlagsNode.execute(inliningTarget, rClass);
            if (p1.profile(inliningTarget, ((lFlags | rFlags) & NB_MULTIPLY) != 0)) {
                Object result;
                SpecialMethodSlot slot = SpecialMethodSlot.Mul;
                SpecialMethodSlot rslot = SpecialMethodSlot.RMul;
                if (p2.profile(inliningTarget, BinaryOp1Node.isBothSLOT1BINFULL(lFlags, rFlags) || doSLOT1BINFULL(slot, lFlags, rFlags))) {
                    result = slot1BINFULLNode.execute(frame, left, right, slot, rslot, lFlags, rFlags, lClass, rClass);
                } else {
                    result = binaryOp1Node.execute(frame, left, right, slot, rslot, lFlags, rFlags, lClass, rClass);
                }
                if (result != PNotImplemented.NOT_IMPLEMENTED) {
                    return result;
                }
            }
            if (p3.profile(inliningTarget, ((lFlags | rFlags) & SQ_REPEAT) != 0)) {
                Object seqObj = (lFlags & SQ_REPEAT) != 0 ? left : right;
                Object seqClass = getClassNode.execute(inliningTarget, seqObj);
                Object callable = getattr.execute(frame, seqClass, seqObj);
                return ensureDispatch().executeObject(frame, callable, seqObj, seqObj == right ? left : right);
            }
            return runErrorHandler(frame, left, right);
        }

        @Override
        public TruffleString getName() {
            return SpecialMethodSlot.Mul.getName();
        }

        @Override
        public TruffleString getRname() {
            return SpecialMethodSlot.RMul.getName();
        }
    }

    // cpython://Objects/abstract.c#binary_op
    protected abstract static class BinaryOpNode extends LookupAndCallNbNumbersBinaryNode {

        protected final SpecialMethodSlot slot;
        protected final SpecialMethodSlot rslot;

        BinaryOpNode(SpecialMethodSlot slot, SpecialMethodSlot rslot, Supplier<NotImplementedHandler> handlerFactory) {
            super(handlerFactory);
            assert slot != null;
            assert rslot != null;
            this.slot = slot;
            this.rslot = rslot;
        }

        @Specialization(guards = {"left.getClass() == cachedLeftClass", "right.getClass() == cachedRightClass"}, limit = "5")
        @SuppressWarnings("truffle-static-method")
        Object binaryOpC(VirtualFrame frame, Object left, Object right,
                        @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Cached("left.getClass()") Class<?> cachedLeftClass,
                        @SuppressWarnings("unused") @Cached("right.getClass()") Class<?> cachedRightClass,
                        @Exclusive @Cached GetMethodsFlagsNode getMethodsFlagsNode,
                        @Exclusive @Cached GetClassNode getlClassNode,
                        @Exclusive @Cached GetClassNode getrClassNode,
                        @Exclusive @Cached InlinedConditionProfile p1,
                        @Exclusive @Cached InlinedConditionProfile p2,
                        @Exclusive @Cached BinaryOp1Node binaryOp1Node,
                        @Exclusive @Cached Slot1BINFULLNode slot1BINFULLNode) {
            return binaryOp(frame, left, right, inliningTarget, getMethodsFlagsNode, getlClassNode, getrClassNode, p1, p2,
                            binaryOp1Node, slot1BINFULLNode);
        }

        @Specialization(replaces = "binaryOpC")
        @SuppressWarnings("truffle-static-method")
        Object binaryOp(VirtualFrame frame, Object left, Object right,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached GetMethodsFlagsNode getMethodsFlagsNode,
                        @Exclusive @Cached GetClassNode getlClassNode,
                        @Exclusive @Cached GetClassNode getrClassNode,
                        @Exclusive @Cached InlinedConditionProfile p1,
                        @Exclusive @Cached InlinedConditionProfile p2,
                        @Exclusive @Cached BinaryOp1Node binaryOp1Node,
                        @Exclusive @Cached Slot1BINFULLNode slot1BINFULLNode) {
            Object lClass = getlClassNode.execute(inliningTarget, left);
            Object rClass = getrClassNode.execute(inliningTarget, right);
            long lFlags = getMethodsFlagsNode.execute(inliningTarget, lClass);
            long rFlags = getMethodsFlagsNode.execute(inliningTarget, rClass);
            if (p1.profile(inliningTarget, ((lFlags | rFlags) & slot.getMethodsFlag()) != 0)) {
                Object result;
                if (p2.profile(inliningTarget, BinaryOp1Node.isBothSLOT1BINFULL(lFlags, rFlags) || doSLOT1BINFULL(slot, lFlags, rFlags))) {
                    result = slot1BINFULLNode.execute(frame, left, right, slot, rslot, lFlags, rFlags, lClass, rClass);
                } else {
                    result = binaryOp1Node.execute(frame, left, right, slot, rslot, lFlags, rFlags, lClass, rClass);
                }
                if (result != PNotImplemented.NOT_IMPLEMENTED) {
                    return result;
                }
            }
            return runErrorHandler(frame, left, right);
        }

        @Override
        public TruffleString getName() {
            return slot.getName();
        }

        @Override
        public TruffleString getRname() {
            return rslot.getName();
        }
    }

    @GenerateInline(inlineByDefault = true)
    abstract static class DispatchSpecialMethodSlotNode extends Node {

        abstract Object execute(VirtualFrame frame, Node inliningTarget, Object callable,
                        Object leftValue, Object rightValue, Object lClass, SpecialMethodSlot op);

        @Specialization
        static Object dispatch(VirtualFrame frame, Node inliningTarget, Object callable,
                        Object leftValue, Object rightValue, Object lClass, SpecialMethodSlot op,
                        @Cached InlinedConditionProfile isEnclosingProfile,
                        @Cached(inline = false) CallBinaryMethodNode dispatch,
                        @Cached(inline = false) IsSubtypeNode isSubtype,
                        @Cached GetEnclosingType getEnclosingType,
                        @Cached PRaiseNode.Lazy raiseNode,
                        @Cached GetNameNode getNameNode) {
            // see descrobject.c/wrapperdescr_call()
            Object enclosing = getEnclosingType.execute(inliningTarget, callable);
            if (isEnclosingProfile.profile(inliningTarget, enclosing != null && !isSubtype.execute(lClass, enclosing))) {
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.DESCRIPTOR_S_REQUIRES_S_OBJ_RECEIVED_P, op.getName(), getNameNode.execute(inliningTarget, lClass), leftValue);
            }
            return dispatch.executeObject(frame, callable, leftValue, rightValue);
        }
    }

    @SuppressWarnings("truffle-inlining")       // footprint reduction 56 -> 38
    protected abstract static class BinaryOp1Node extends Node {

        abstract Object execute(VirtualFrame frame, Object left, Object right,
                        SpecialMethodSlot slot, SpecialMethodSlot rslot,
                        long lFlags, long rFlags,
                        Object lClass, Object rClass);

        protected static boolean isSLOT1BINFULL(long mflags) {
            return (mflags & SLOT1BINFULL) != 0;
        }

        protected static boolean canDoOp(SpecialMethodSlot slot, long mflags) {
            return (mflags & slot.getMethodsFlag()) != 0;
        }

        // This specialization implements the logic from cpython://Objects/abstract.c#binary_op1
        @Specialization(guards = {
                        "!isSLOT1BINFULL(vMethodsFlags)",
                        "canDoOp(slot, vMethodsFlags)",
                        "!canDoOp(slot, wMethodsFlags)",
        })
        @SuppressWarnings("truffle-static-method")
        Object binaryOp1vOnlySlot(VirtualFrame frame, Object v, Object w,
                        SpecialMethodSlot slot, @SuppressWarnings("unused") SpecialMethodSlot rslot,
                        @SuppressWarnings("unused") long vMethodsFlags, @SuppressWarnings("unused") long wMethodsFlags,
                        Object vClass, @SuppressWarnings("unused") Object wClass,
                        @Bind("this") Node node,
                        @Exclusive @Cached InlinedConditionProfile p1,
                        @Shared @Cached("create(slot)") LookupSpecialMethodSlotNode opSlot,
                        @Exclusive @Cached DispatchSpecialMethodSlotNode dispatchNode) {
            Object slotv = lookupAttrId(frame, v, vClass, opSlot);
            if (p1.profile(node, slotv != PNone.NO_VALUE)) {
                // x = slotv(v, w);
                return dispatchNode.execute(frame, node, slotv, v, w, vClass, slot);
            }
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        @Specialization(guards = {
                        "!isSLOT1BINFULL(wMethodsFlags)",
                        "!canDoOp(slot, vMethodsFlags)",
                        "canDoOp(slot, wMethodsFlags)",
        })
        @SuppressWarnings("truffle-static-method")
        Object binaryOp1wOnlySlot(VirtualFrame frame, Object v, Object w,
                        SpecialMethodSlot slot, @SuppressWarnings("unused") SpecialMethodSlot rslot,
                        @SuppressWarnings("unused") long vMethodsFlags, @SuppressWarnings("unused") long wMethodsFlags,
                        @SuppressWarnings("unused") Object vClass, Object wClass,
                        @Bind("this") Node node,
                        @Exclusive @Cached InlinedConditionProfile p2,
                        @Shared @Cached("create(slot)") LookupSpecialMethodSlotNode opSlot,
                        @Exclusive @Cached DispatchSpecialMethodSlotNode dispatchNode) {
            Object slotw = lookupAttrId(frame, w, wClass, opSlot);
            if (p2.profile(node, slotw != PNone.NO_VALUE)) {
                // x = slotw(v, w);
                // mq: TODO: swap back `v, w` once all specializations are covered (GR-<1????>)
                return dispatchNode.execute(frame, node, slotw, w, v, wClass, slot);
            }
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        @Specialization(guards = {
                        "!isSLOT1BINFULL(vMethodsFlags)",
                        "!isSLOT1BINFULL(wMethodsFlags)",
                        "canDoOp(slot, vMethodsFlags)",
                        "canDoOp(slot, wMethodsFlags)",
        })
        @SuppressWarnings("truffle-static-method")
        Object binaryOp1fullSlots(VirtualFrame frame, Object v, Object w,
                        SpecialMethodSlot slot, @SuppressWarnings("unused") SpecialMethodSlot rslot,
                        @SuppressWarnings("unused") long vMethodsFlags, @SuppressWarnings("unused") long wMethodsFlags,
                        Object vClass, Object wClass,
                        @Bind("this") Node node,
                        @Shared @Cached("create(slot)") LookupSpecialMethodSlotNode opSlot,
                        @Exclusive @Cached InlinedConditionProfile p1,
                        @Exclusive @Cached InlinedConditionProfile p2,
                        @Exclusive @Cached InlinedConditionProfile p3,
                        @Exclusive @Cached InlinedConditionProfile p4,
                        @Exclusive @Cached IsSubtypeNode isSubtype,
                        @Exclusive @Cached AreSameCallables areSameCallables,
                        @Exclusive @Cached IsSameTypeNode isSameTypeNode,
                        @Exclusive @Cached DispatchSpecialMethodSlotNode dispatchNode) {
            Object slotv = lookupAttrId(frame, v, vClass, opSlot);
            Object slotw = PNone.NO_VALUE;
            if (!isSameTypeNode.execute(node, wClass, vClass)) {
                slotw = lookupAttrId(frame, w, wClass, opSlot);
                if (areSameCallables.execute(node, slotv, slotw)) {
                    slotw = PNone.NO_VALUE;
                }
            }

            if (p1.profile(node, slotv != PNone.NO_VALUE)) {
                Object x;
                if (p3.profile(node, slotw != PNone.NO_VALUE && isSubtype.execute(frame, wClass, vClass))) {
                    // x = slotw(v, w);
                    x = dispatchNode.execute(frame, node, slotw, v, w, wClass, slot);

                    if (x != PNotImplemented.NOT_IMPLEMENTED) {
                        return x;
                    }
                    slotw = PNone.NO_VALUE;
                }
                // x = slotv(v, w);
                x = dispatchNode.execute(frame, node, slotv, v, w, vClass, slot);
                if (p4.profile(node, maybeMissingSpecialization(x, slot, slotw, vClass, wClass))) {
                    x = dispatchNode.execute(frame, node, slotv, w, v, vClass, slot);
                }

                if (x != PNotImplemented.NOT_IMPLEMENTED) {
                    return x;
                }
            }
            if (p2.profile(node, slotw != PNone.NO_VALUE)) {
                // x = slotw(v, w);
                // mq: TODO: swap back `v, w` once all specializations are covered (GR-<1????>)
                return dispatchNode.execute(frame, node, slotw, w, v, wClass, slot);
            }
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        protected static boolean isBothSLOT1BINFULL(long left, long right) {
            return (left & right & SLOT1BINFULL) != 0;
        }

        @Specialization(guards = {
                        "!isBothSLOT1BINFULL(vMethodsFlags, wMethodsFlags)",
                        "isSLOT1BINFULL(vMethodsFlags) || isSLOT1BINFULL(wMethodsFlags)",
                        "canDoOp(slot, vMethodsFlags)",
                        "canDoOp(slot, wMethodsFlags)",
        })
        @SuppressWarnings("truffle-static-method")
        Object binaryOp1full(VirtualFrame frame, Object v, Object w,
                        SpecialMethodSlot slot, SpecialMethodSlot rslot,
                        long vMethodsFlags, long wMethodsFlags,
                        Object vClass, Object wClass,
                        @Bind("this") Node node,
                        @Shared @Cached("create(slot)") LookupSpecialMethodSlotNode opSlot,
                        @Exclusive @Cached InlinedConditionProfile p1,
                        @Exclusive @Cached InlinedConditionProfile p2,
                        @Exclusive @Cached InlinedConditionProfile p3,
                        @Exclusive @Cached InlinedConditionProfile p4,
                        @Exclusive @Cached InlinedConditionProfile p5,
                        @Exclusive @Cached InlinedConditionProfile p6,
                        @Exclusive @Cached InlinedConditionProfile p7,
                        @Exclusive @Cached InlinedConditionProfile p8,
                        @Exclusive @Cached IsSubtypeNode isSubtype,
                        @Exclusive @Cached AreSameCallables areSameCallables,
                        @Exclusive @Cached IsSameTypeNode isSameTypeNode,
                        @Cached Slot1BINFULLNode slot1BINFULLNode,
                        @Exclusive @Cached DispatchSpecialMethodSlotNode dispatchNode) {
            long op = slot.getMethodsFlag();
            boolean slotvIsBinFullOp = (vMethodsFlags & SLOT1BINFULL) != 0;
            boolean slotwIsBinFullOp = (wMethodsFlags & SLOT1BINFULL) != 0;
            Object slotv = PNone.NO_VALUE;
            if ((vMethodsFlags & op) != 0) {
                slotv = slotvIsBinFullOp ? null : lookupAttrId(frame, v, vClass, opSlot);
            }

            Object slotw = PNone.NO_VALUE;
            if (p5.profile(node, (wMethodsFlags & op) != 0 && !isSameTypeNode.execute(node, wClass, vClass))) {
                slotw = p7.profile(node, slotwIsBinFullOp) ? null : lookupAttrId(frame, w, wClass, opSlot);
                if (p6.profile(node, !slotvIsBinFullOp && !slotwIsBinFullOp && areSameCallables.execute(node, slotv, slotw))) {
                    slotw = PNone.NO_VALUE;
                }
            }

            if (p1.profile(node, slotv != PNone.NO_VALUE)) {
                Object x;
                if (p3.profile(node, slotw != PNone.NO_VALUE && isSubtype.execute(frame, wClass, vClass))) {
                    if (p7.profile(node, slotwIsBinFullOp)) {
                        x = slot1BINFULLNode.execute(frame,
                                        v, w, slot, rslot, vMethodsFlags, wMethodsFlags, vClass, wClass);
                    } else {
                        // x = slotw(v, w);
                        x = dispatchNode.execute(frame, node, slotw, v, w, wClass, slot);
                    }

                    if (x != PNotImplemented.NOT_IMPLEMENTED) {
                        return x;
                    }
                    slotw = PNone.NO_VALUE;
                }
                if (p8.profile(node, slotvIsBinFullOp)) {
                    x = slot1BINFULLNode.execute(frame, v, w, slot, rslot, vMethodsFlags, wMethodsFlags, vClass, wClass);
                } else {
                    // x = slotv(v, w);
                    x = dispatchNode.execute(frame, node, slotv, v, w, vClass, slot);
                    if (p4.profile(node, maybeMissingSpecialization(x, slot, slotw, vClass, wClass))) {
                        x = dispatchNode.execute(frame, node, slotv, w, v, vClass, slot);
                    }
                }

                if (x != PNotImplemented.NOT_IMPLEMENTED) {
                    return x;
                }
            }
            if (p2.profile(node, slotw != PNone.NO_VALUE)) {
                if (p7.profile(node, slotwIsBinFullOp)) {
                    return slot1BINFULLNode.execute(frame, v, w, slot, rslot, vMethodsFlags, wMethodsFlags, vClass, wClass);
                } else {
                    // x = slotw(v, w);
                    // mq: TODO: swap back `v, w` once all specializations are covered (GR-<1????>)
                    return dispatchNode.execute(frame, node, slotw, w, v, wClass, slot);
                }
            }
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        private static boolean maybeMissingSpecialization(Object result, SpecialMethodSlot slot, Object otherSlot, Object lClass, Object rClass) {
            // Special case in our builtins since we might be missing some specializations.
            // e.g. int builtins
            return result == PNotImplemented.NOT_IMPLEMENTED &&
                            otherSlot == PNone.NO_VALUE &&
                            lClass == rClass &&
                            (slot == SpecialMethodSlot.Mul ||
                                            slot == SpecialMethodSlot.And ||
                                            slot == SpecialMethodSlot.Or ||
                                            slot == SpecialMethodSlot.Xor);
        }
    }

    protected static boolean doSLOT1BINFULL(SpecialMethodSlot slot, long left, long right) {
        long op = slot.getMethodsFlag();
        return ((left & SLOT1BINFULL) != 0 && (left & op) != 0 && (right & op) == 0) ||
                        ((right & SLOT1BINFULL) != 0 && (right & op) != 0 && (left & op) == 0);
    }

    // This specialization implements the logic in cpython://Objects/typeobject.c#SLOT1BINFULL
    @SuppressWarnings("truffle-inlining")       // footprint reduction 48 -> 31
    protected abstract static class Slot1BINFULLNode extends Node {

        abstract Object execute(VirtualFrame frame, Object left, Object right,
                        SpecialMethodSlot slot, SpecialMethodSlot rslot,
                        long lFlags, long rFlags,
                        Object lClass, Object rClass);

        protected static boolean isSLOT1BINFULL(long mflags) {
            return (mflags & SLOT1BINFULL) != 0;
        }

        @Specialization(guards = {"isSLOT1BINFULL(lFlags)", "!isSLOT1BINFULL(rFlags)"})
        static Object slot1binfullLeftOnly(VirtualFrame frame, Object left, Object right,
                        SpecialMethodSlot slot, @SuppressWarnings("unused") SpecialMethodSlot rslot,
                        long lFlags, @SuppressWarnings("unused") long rFlags,
                        Object lClass, @SuppressWarnings("unused") Object rClass,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached InlinedConditionProfile p1,
                        @Shared @Cached("create(slot)") LookupSpecialMethodSlotNode opId,
                        @Exclusive @Cached DispatchSpecialMethodSlotNode dispatchNode) {
            if (p1.profile(inliningTarget, (lFlags & SLOT1BINFULL) != 0)) {
                Object leftCallable = lookupAttrId(frame, left, lClass, opId);
                return dispatchNode.execute(frame, inliningTarget, leftCallable, left, right, lClass, slot);
            }
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        @Specialization(guards = {"!isSLOT1BINFULL(lFlags)", "isSLOT1BINFULL(rFlags)"})
        static Object slot1binfullRightOnly(VirtualFrame frame, Object left, Object right,
                        @SuppressWarnings("unused") SpecialMethodSlot slot, SpecialMethodSlot rslot,
                        @SuppressWarnings("unused") long lFlags, @SuppressWarnings("unused") long rFlags,
                        @SuppressWarnings("unused") Object lClass, Object rClass,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached InlinedConditionProfile p2,
                        @Shared @Cached("create(rslot)") LookupSpecialMethodSlotNode ropId,
                        @Exclusive @Cached DispatchSpecialMethodSlotNode dispatchNode) {
            Object rOpRightCallable = lookupAttrId(frame, right, rClass, ropId);
            if (p2.profile(inliningTarget, rOpRightCallable != PNone.NO_VALUE)) {
                return dispatchNode.execute(frame, inliningTarget, rOpRightCallable, right, left, rClass, rslot);
            }
            return PNotImplemented.NOT_IMPLEMENTED;
        }

        @Specialization(guards = {"isSLOT1BINFULL(lFlags)", "isSLOT1BINFULL(rFlags)"})
        static Object slot1binfull(VirtualFrame frame, Object left, Object right,
                        SpecialMethodSlot slot, SpecialMethodSlot rslot,
                        long lFlags, long rFlags,
                        Object lClass, Object rClass,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached("create(slot)") LookupSpecialMethodSlotNode opId,
                        @Shared @Cached("create(rslot)") LookupSpecialMethodSlotNode ropId,
                        @Exclusive @Cached InlinedConditionProfile p1,
                        @Exclusive @Cached InlinedConditionProfile p2,
                        @Exclusive @Cached InlinedConditionProfile p3,
                        @Exclusive @Cached InlinedConditionProfile p4,
                        @Exclusive @Cached InlinedConditionProfile p5,
                        @Exclusive @Cached InlinedConditionProfile p6,
                        @Exclusive @Cached InlinedConditionProfile p7,
                        @Cached IsSameTypeNode isSameTypeNode,
                        @Cached IsSubtypeNode isSubtype,
                        @Cached AreSameCallables areSameCallables,
                        @Exclusive @Cached DispatchSpecialMethodSlotNode dispatchNode) {
            boolean pyIsType = isSameTypeNode.execute(inliningTarget, lClass, rClass);
            boolean doRight = !pyIsType && (rFlags & SLOT1BINFULL) != 0;
            Object rOpRightCallable = null;
            if (p1.profile(inliningTarget, (lFlags & SLOT1BINFULL) != 0)) {
                Object r;
                if (p3.profile(inliningTarget, doRight && isSubtype.execute(frame, rClass, lClass))) {
                    // method_is_overloaded inlined
                    boolean isOverloaded;
                    rOpRightCallable = lookupAttrId(frame, right, rClass, ropId);
                    if (p2.profile(inliningTarget, rOpRightCallable != PNone.NO_VALUE)) {
                        Object rOpLeftCallable = lookupAttrId(frame, left, lClass, ropId);
                        if (p4.profile(inliningTarget, rOpLeftCallable == PNone.NO_VALUE)) {
                            isOverloaded = true;
                        } else {
                            isOverloaded = !areSameCallables.execute(inliningTarget, rOpLeftCallable, rOpRightCallable);
                        }
                    } else {
                        isOverloaded = false;
                    }
                    if (p5.profile(inliningTarget, isOverloaded)) {
                        r = dispatchNode.execute(frame, inliningTarget, rOpRightCallable, right, left, rClass, rslot);
                        if (r != PNotImplemented.NOT_IMPLEMENTED) {
                            return r;
                        }
                        doRight = false;
                    }
                }
                Object leftCallable = lookupAttrId(frame, left, lClass, opId);
                if (p7.profile(inliningTarget, leftCallable != PNone.NO_VALUE)) {
                    r = dispatchNode.execute(frame, inliningTarget, leftCallable, left, right, lClass, slot);
                    if (r != PNotImplemented.NOT_IMPLEMENTED || pyIsType) {
                        return r;
                    }
                }
            }
            if (p6.profile(inliningTarget, doRight)) {
                if (rOpRightCallable == null) {
                    rOpRightCallable = lookupAttrId(frame, right, rClass, ropId);
                }
                if (p2.profile(inliningTarget, rOpRightCallable != PNone.NO_VALUE)) {
                    return dispatchNode.execute(frame, inliningTarget, rOpRightCallable, right, left, rClass, rslot);
                }
            }
            return PNotImplemented.NOT_IMPLEMENTED;
        }
    }

}
