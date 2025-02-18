/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.objects.ints.IntBuiltins.AddNode.add;
import static com.oracle.graal.python.lib.CallBinaryOpNode.raiseNotSupported;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.PNotImplemented;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetCachedTpSlotsNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryFunc.CallSlotBinaryFuncNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotBinaryOp.ReversibleSlot;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.expression.BinaryOpNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.OverflowException;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.strings.TruffleString;

@GenerateCached(false)
@ImportStatic(PGuards.class)
abstract class PyNumberAddBaseNode extends BinaryOpNode {

    /*
     * All the following fast paths need to be kept in sync with the corresponding builtin functions
     * in IntBuiltins, but it additionally needs to check PInts for only builtin ints
     */
    // XXX this could benefit from the type system conversions, but that would also unpack PFloat
    // which we don't want

    @Specialization(rewriteOn = ArithmeticException.class)
    static int doII(int left, int right) {
        return Math.addExact(left, right);
    }

    @Specialization(replaces = "doII", rewriteOn = ArithmeticException.class)
    static long doLL(long left, long right) {
        return Math.addExact(left, right);
    }

    @Specialization(replaces = "doLL")
    static Object doLLOvf(long x, long y,
                    @Bind PythonLanguage language) {
        /* Inlined version of Math.addExact(x, y) with BigInteger fallback. */
        long r = x + y;
        // HD 2-12 Overflow iff both arguments have the opposite sign of the result
        if (((x ^ r) & (y ^ r)) < 0) {
            return PFactory.createInt(language, add(PInt.longToBigInteger(x), PInt.longToBigInteger(y)));
        }
        return r;
    }

    @Specialization(guards = "isBuiltinPInt(left)", rewriteOn = OverflowException.class)
    static Object doPLNarrow(PInt left, long right) throws OverflowException {
        return PInt.longValueExact(add(left.getValue(), PInt.longToBigInteger(right)));
    }

    @Specialization(guards = "isBuiltinPInt(left)", replaces = "doPLNarrow")
    static Object doPL(PInt left, long right,
                    @Bind PythonLanguage language) {
        return PFactory.createInt(language, add(left.getValue(), PInt.longToBigInteger(right)));
    }

    @Specialization(guards = "isBuiltinPInt(right)", rewriteOn = OverflowException.class)
    static Object doLPNarrow(long left, PInt right) throws OverflowException {
        return PInt.longValueExact(add(PInt.longToBigInteger(left), right.getValue()));
    }

    @Specialization(guards = "isBuiltinPInt(right)", replaces = "doLPNarrow")
    static Object doLP(long left, PInt right,
                    @Bind PythonLanguage language) {
        return PFactory.createInt(language, add(PInt.longToBigInteger(left), right.getValue()));
    }

    @Specialization(guards = {"isBuiltinPInt(left)", "isBuiltinPInt(right)"}, rewriteOn = OverflowException.class)
    static Object doPPNarrow(PInt left, PInt right) throws OverflowException {
        return PInt.longValueExact(add(left.getValue(), right.getValue()));
    }

    @Specialization(guards = {"isBuiltinPInt(left)", "isBuiltinPInt(right)"}, replaces = "doPPNarrow")
    static Object doPP(PInt left, PInt right,
                    @Bind PythonLanguage language) {
        return PFactory.createInt(language, add(left.getValue(), right.getValue()));
    }

    /*
     * All the following fast paths need to be kept in sync with the corresponding builtin functions
     * in FloatBuiltins
     */

    @Specialization
    public static double doDD(double left, double right) {
        return left + right;
    }

    @Specialization
    public static double doDL(double left, long right) {
        return left + right;
    }

    @Specialization
    public static double doLD(long left, double right) {
        return left + right;
    }

    @Specialization
    public static double doDI(double left, int right) {
        return left + right;
    }

    @Specialization
    public static double doID(int left, double right) {
        return left + right;
    }
}

@GenerateInline(inlineByDefault = true)
@GenerateUncached
public abstract class PyNumberAddNode extends PyNumberAddBaseNode {
    public abstract Object execute(VirtualFrame frame, Node inliningTarget, Object v, Object w);

    @Override
    public final Object execute(VirtualFrame frame, Object left, Object right) {
        return executeCached(frame, left, right);
    }

    public final Object executeCached(VirtualFrame frame, Object v, Object w) {
        return execute(frame, this, v, w);
    }

    public abstract int executeInt(VirtualFrame frame, Node inliningTarget, int left, int right) throws UnexpectedResultException;

    public abstract double executeDouble(VirtualFrame frame, Node inliningTarget, double left, double right) throws UnexpectedResultException;

    @Specialization(guards = {"isBuiltinList(left)", "isBuiltinList(right)"})
    static PList doPList(Node inliningTarget, PList left, PList right,
                    @Shared @Cached SequenceStorageNodes.ConcatListOrTupleNode concatNode,
                    @Bind PythonLanguage language) {
        SequenceStorage newStore = concatNode.execute(inliningTarget, left.getSequenceStorage(), right.getSequenceStorage());
        return PFactory.createList(language, newStore);
    }

    @Specialization(guards = {"isBuiltinTuple(left)", "isBuiltinTuple(right)"})
    static PTuple doTuple(Node inliningTarget, PTuple left, PTuple right,
                    @Shared @Cached SequenceStorageNodes.ConcatListOrTupleNode concatNode,
                    @Bind PythonLanguage language) {
        SequenceStorage concatenated = concatNode.execute(inliningTarget, left.getSequenceStorage(), right.getSequenceStorage());
        return PFactory.createTuple(language, concatenated);
    }

    @Specialization
    static TruffleString doIt(TruffleString left, TruffleString right,
                    @Cached(inline = false) TruffleString.ConcatNode concatNode) {
        return concatNode.execute(left, right, TS_ENCODING, false);
    }

    @Fallback
    static Object doIt(VirtualFrame frame, Node inliningTarget, Object v, Object w,
                    @Exclusive @Cached GetClassNode getVClass,
                    @Cached GetCachedTpSlotsNode getVSlots,
                    @Cached GetCachedTpSlotsNode getWSlots,
                    @Exclusive @Cached GetClassNode getWClass,
                    @Cached CallBinaryOp1Node callBinaryOp1Node,
                    @Cached InlinedBranchProfile hasNbAddResult,
                    @Cached CallSlotBinaryFuncNode callBinarySlotNode,
                    @Cached PRaiseNode raiseNode) {
        Object classV = getVClass.execute(inliningTarget, v);
        Object classW = getWClass.execute(inliningTarget, w);
        TpSlots slotsV = getVSlots.execute(inliningTarget, classV);
        TpSlots slotsW = getWSlots.execute(inliningTarget, classW);
        Object result = callBinaryOp1Node.execute(frame, inliningTarget, v, classV, slotsV, w, classW, slotsW, ReversibleSlot.NB_ADD);
        if (result != PNotImplemented.NOT_IMPLEMENTED) {
            hasNbAddResult.enter(inliningTarget);
            return result;
        }
        if (slotsV.sq_concat() != null) {
            return callBinarySlotNode.execute(frame, inliningTarget, slotsV.sq_concat(), v, w);
        }
        return raiseNotSupported(inliningTarget, v, w, "+", raiseNode);
    }

    @NeverDefault
    public static PyNumberAddNode create() {
        return PyNumberAddNodeGen.create();
    }
}
