/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import com.oracle.graal.python.builtins.modules.MathModuleBuiltins;
import com.oracle.graal.python.builtins.modules.SysModuleBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.type.SpecialMethodSlot;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.LookupCallableSlotInMRONode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.call.special.MaybeBindDescriptorNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.nodes.util.CastUnsignedToJavaLongHashNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@ImportStatic(SpecialMethodSlot.class)
@GenerateUncached
@GenerateCached(false)
@GenerateInline
public abstract class PyObjectHashNode extends PNodeWithContext {
    public static long executeUncached(Object value) {
        return PyObjectHashNodeGen.getUncached().execute(null, null, value);
    }

    public abstract long execute(Frame frame, Node inliningTarget, Object object);

    public abstract long execute(Frame frame, Node inliningTarget, TruffleString object);

    public static long avoidNegative1(long hash) {
        // CPython uses -1 to signify error status
        return hash == -1 ? -2 : hash;
    }

    @Specialization
    @InliningCutoff
    public static long hash(TruffleString object,
                    @Shared @Cached(inline = false) TruffleString.HashCodeNode hashCodeNode) {
        return avoidNegative1(hashCodeNode.execute(object, TS_ENCODING));
    }

    @Specialization(guards = "isBuiltinPString(object)")
    @InliningCutoff
    static long hash(Node inliningTarget, PString object,
                    @Cached CastToTruffleStringNode cast,
                    @Shared @Cached(inline = false) TruffleString.HashCodeNode hashCodeNode) {
        return hash(cast.execute(inliningTarget, object), hashCodeNode);
    }

    @Specialization
    public static long hash(boolean object) {
        return object ? 1 : 0;
    }

    @Specialization
    public static long hash(int object) {
        return avoidNegative1(object);
    }

    @Specialization
    public static long hash(long object) {
        long h = object % SysModuleBuiltins.HASH_MODULUS;
        return avoidNegative1(h);
    }

    // Adapted from CPython _Py_HashDouble
    @Specialization
    public static long hash(double object) {
        if (!Double.isFinite(object)) {
            if (Double.isInfinite(object)) {
                return object > 0 ? SysModuleBuiltins.HASH_INF : -SysModuleBuiltins.HASH_INF;
            }
            return SysModuleBuiltins.HASH_NAN;
        }

        double[] frexpRes = MathModuleBuiltins.FrexpNode.frexp(object);
        double m = frexpRes[0];
        int e = (int) frexpRes[1];
        int sign = 1;
        if (m < 0) {
            sign = -1;
            m = -m;
        }
        long x = 0;
        while (m != 0.0) {
            x = ((x << 28) & SysModuleBuiltins.HASH_MODULUS) | x >> (SysModuleBuiltins.HASH_BITS - 28);
            m *= 268435456.0; /* 2**28 */
            e -= 28;
            long y = (long) m; /* pull out integer part */
            m -= y;
            x += y;
            if (x >= SysModuleBuiltins.HASH_MODULUS) {
                x -= SysModuleBuiltins.HASH_MODULUS;
            }
        }
        e = e >= 0 ? e % SysModuleBuiltins.HASH_BITS : SysModuleBuiltins.HASH_BITS - 1 - ((-1 - e) % SysModuleBuiltins.HASH_BITS);
        x = ((x << e) & SysModuleBuiltins.HASH_MODULUS) | x >> (SysModuleBuiltins.HASH_BITS - e);
        x = x * sign;
        return avoidNegative1(x);
    }

    @Fallback
    @InliningCutoff
    static long hash(VirtualFrame frame, Node inliningTarget, Object object,
                    @Cached GetClassNode getClassNode,
                    @Cached(parameters = "Hash", inline = false) LookupCallableSlotInMRONode lookupHash,
                    @Cached MaybeBindDescriptorNode bindDescriptorNode,
                    @Cached(inline = false) CallUnaryMethodNode callHash,
                    @Cached CastUnsignedToJavaLongHashNode cast,
                    @Cached PRaiseNode.Lazy raiseNode) {
        /* This combines the logic from abstract.c:PyObject_Hash and typeobject.c:slot_tp_hash */
        Object type = getClassNode.execute(inliningTarget, object);
        // We have to do the lookup and bind steps separately to avoid binding possible None
        Object hashDescr = lookupHash.execute(type);
        if (hashDescr != PNone.NO_VALUE && hashDescr != PNone.NONE) {
            try {
                hashDescr = bindDescriptorNode.execute(frame, inliningTarget, hashDescr, object, type);
            } catch (PException e) {
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.UNHASHABLE_TYPE_P, object);
            }
            Object result = callHash.executeObject(frame, hashDescr, object);
            try {
                return avoidNegative1(cast.execute(inliningTarget, result));
            } catch (CannotCastException e) {
                throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.HASH_SHOULD_RETURN_INTEGER);
            }
        }
        throw raiseNode.get(inliningTarget).raise(TypeError, ErrorMessages.UNHASHABLE_TYPE_P, object);
    }

    public static PyObjectHashNode getUncached() {
        return PyObjectHashNodeGen.getUncached();
    }
}
