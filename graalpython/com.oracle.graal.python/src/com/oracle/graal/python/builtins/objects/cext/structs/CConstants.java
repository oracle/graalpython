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
package com.oracle.graal.python.builtins.objects.cext.structs;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.SystemError;
import static com.oracle.graal.python.nodes.ErrorMessages.INTERNAL_INT_OVERFLOW;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import com.oracle.graal.python.annotations.CApiConstants;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

/**
 * Helper enum to extract constants from the C space. Constants are limited to the range of "long"
 * values, but '-1' is not allowed at the moment.
 */
@CApiConstants
public enum CConstants {
    PYLONG_BITS_IN_DIGIT,
    READONLY,
    CHAR_MIN;

    @CompilationFinal(dimensions = 1) public static final CConstants[] VALUES = values();

    @CompilationFinal private long longValue = -1;
    @CompilationFinal private int intValue = -1;

    public long longValue() {
        long o = longValue;
        if (o == -1) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            resolve();
            return longValue;
        }
        return o;
    }

    /**
     * Returns the value of this constant as an "int", throwing a
     * {@link PythonBuiltinClassType#SystemError} if the values is outside the "int" range.
     */
    public int intValue() {
        int o = intValue;
        if (o == -1) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            resolve();
            if (intValue == -1) {
                throw PRaiseNode.raiseUncached(null, SystemError, INTERNAL_INT_OVERFLOW);
            }
            return intValue;
        }
        return o;
    }

    private static void resolve() {
        CompilerAsserts.neverPartOfCompilation();
        Object constantsPointer = PCallCapiFunction.callUncached(NativeCAPISymbol.FUN_PYTRUFFLE_CONSTANTS);
        long[] constants = CStructAccessFactory.ReadI64NodeGen.getUncached().readLongArray(constantsPointer, VALUES.length);
        for (CConstants constant : VALUES) {
            constant.longValue = constants[constant.ordinal()];
            if (constant.longValue == -1) {
                throw PRaiseNode.raiseUncached(null, SystemError, toTruffleStringUncached("internal limitation - cannot extract constants with value '-1'"));
            }
            if ((constant.longValue & 0xFFFF0000L) == 0xDEAD0000L) {
                throw PRaiseNode.raiseUncached(null, SystemError, toTruffleStringUncached("marker value reached, regenerate C code (mx python-capi)"));
            }
            if (constant.longValue == (int) constant.longValue) {
                constant.intValue = (int) constant.longValue;
            }
        }
    }
}
