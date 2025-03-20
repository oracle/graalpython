/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetCachedTpSlotsNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.IsSameTypeNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotRichCompare.CallSlotRichCmpNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotRichCompare.RichCmpOp;
import com.oracle.graal.python.lib.PyObjectRichCompareNodeGen.RichCompareBinaryOpNodeGen;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.expression.BinaryOp;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsNode;
import com.oracle.graal.python.nodes.truffle.PythonIntegerAndFloatTypes;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.EqualNode;

@GenerateInline
@GenerateUncached
@GenerateCached(false)
@TypeSystemReference(PythonIntegerAndFloatTypes.class)
public abstract class PyObjectRichCompare extends Node {
    public abstract Object execute(VirtualFrame frame, Node inliningTarget, Object a, Object b, RichCmpOp op);

    public static Object executeUncached(Object a, Object b, RichCmpOp op) {
        return PyObjectRichCompareNodeGen.getUncached().execute(null, null, a, b, op);
    }

    @Specialization
    static boolean doInts(int a, int b, RichCmpOp op) {
        return op.compare(a, b);
    }

    @Specialization
    static boolean doDoubles(double a, double b, RichCmpOp op) {
        return op.compare(a, b);
    }

    @Specialization
    static boolean doLongs(long a, long b, RichCmpOp op) {
        return op.compare(a, b);
    }

    @Specialization(guards = "op.isEqOrNe()")
    static boolean doStrings(TruffleString a, TruffleString b, RichCmpOp op,
                    @Cached EqualNode equalNode) {
        return equalNode.execute(a, b, PythonUtils.TS_ENCODING) == op.isEq();
    }

    @Fallback
    @InliningCutoff
    static Object doIt(VirtualFrame frame, Object v, Object w, RichCmpOp op,
                    @Cached(inline = false) GenericRichCompare richCompare) {
        return richCompare.execute(frame, v, w, op);
    }

    @GenerateInline(false)
    public abstract static class RichCompareBinaryOp extends Node implements BinaryOp {
        private final RichCmpOp op;

        protected RichCompareBinaryOp(RichCmpOp op) {
            this.op = op;
        }

        public static RichCompareBinaryOp create(RichCmpOp op) {
            return RichCompareBinaryOpNodeGen.create(op);
        }

        @Specialization
        final Object doIt(VirtualFrame frame, Object left, Object right,
                        @Cached PyObjectRichCompare richCmpNode) {
            return richCmpNode.execute(frame, this, left, right, op);
        }
    }

    @GenerateInline(false)
    @GenerateUncached
    public abstract static class GenericRichCompare extends Node {
        public abstract Object execute(VirtualFrame frame, Object a, Object b, RichCmpOp op);

        @Specialization
        static Object doIt(VirtualFrame frame, Object v, Object w, RichCmpOp op,
                        @Bind Node inliningTarget,
                        @Cached GetClassNode getVClass,
                        @Cached GetClassNode getWClass,
                        @Cached GetCachedTpSlotsNode getVSlots,
                        @Cached GetCachedTpSlotsNode getWSlots,
                        @Cached IsSameTypeNode isSameTypeNode,
                        @Cached IsSubtypeNode isSubtypeNode,
                        @Cached CallSlotRichCmpNode callRichCmpSwapped,
                        @Cached CallSlotRichCmpNode callRichCmp,
                        @Cached IsNode isNode,
                        @Cached PRaiseNode raiseNode) {
            Object vClass = getVClass.execute(inliningTarget, v);
            Object wClass = getWClass.execute(inliningTarget, w);
            TpSlots wSlots = getWSlots.execute(inliningTarget, wClass);
            boolean checkedReverseOp = false;
            if (wSlots.tp_richcmp() != null && !isSameTypeNode.execute(inliningTarget, vClass, wClass) &&
                            isSubtypeNode.execute(wClass, vClass)) {
                checkedReverseOp = true;
                Object result = callRichCmpSwapped.execute(frame, inliningTarget, wSlots.tp_richcmp(), w, v, op.getSwapped());
                if (result != PNotImplemented.NOT_IMPLEMENTED) {
                    return result;
                }
            }
            TpSlots vSlots = getVSlots.execute(inliningTarget, vClass);
            if (vSlots.tp_richcmp() != null) {
                Object result = callRichCmp.execute(frame, inliningTarget, vSlots.tp_richcmp(), v, w, op);
                if (result != PNotImplemented.NOT_IMPLEMENTED) {
                    return result;
                }
            }
            if (wSlots.tp_richcmp() != null && !checkedReverseOp) {
                Object result = callRichCmpSwapped.execute(frame, inliningTarget, wSlots.tp_richcmp(), w, v, op.getSwapped());
                if (result != PNotImplemented.NOT_IMPLEMENTED) {
                    return result;
                }
            }
            // If neither object implements it, provide a sensible default for == and !=, but raise
            // an exception for ordering.
            return switch (op) {
                case Py_EQ, Py_NE -> isNode.execute(v, w) == op.isEq();
                default ->
                    throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.NOT_SUPPORTED_BETWEEN_INSTANCES, op.getOpName(), v, w);
            };
        }
    }
}
