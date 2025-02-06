/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.bytecode;

import static com.oracle.truffle.api.CompilerDirectives.shouldNotReachHere;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.compiler.OpCodes.MakeTypeParamKind;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.strings.TruffleString;

@GenerateUncached
public abstract class MakeTypeParamNode extends PNodeWithContext {

    public abstract int execute(VirtualFrame frame, int initialStackTop, int kind);

    @Specialization
    int makeTypeParam(VirtualFrame frame, int initialStackTop, int kind,
                    @Cached PythonObjectFactory factory) {
        int stackTop = initialStackTop;

        Object evaluateBound = null;
        Object evaluateConstraints = null;

        if (kind == MakeTypeParamKind.TYPE_VAR_WITH_BOUND) {
            evaluateBound = frame.getObject(stackTop);
            frame.setObject(stackTop--, null);
        } else if (kind == MakeTypeParamKind.TYPE_VAR_WITH_CONSTRAINTS) {
            evaluateConstraints = frame.getObject(stackTop);
            frame.setObject(stackTop--, null);
        }
        TruffleString name = (TruffleString) frame.getObject(stackTop);
        frame.setObject(stackTop--, null);

        Object result = switch (kind) {
            case MakeTypeParamKind.TYPE_VAR, MakeTypeParamKind.TYPE_VAR_WITH_BOUND, MakeTypeParamKind.TYPE_VAR_WITH_CONSTRAINTS -> factory.createTypeVar(PythonBuiltinClassType.PTypeVar, name,
                            evaluateBound == null ? PNone.NONE : null, evaluateBound,
                            evaluateConstraints == null ? factory.createEmptyTuple() : null, evaluateConstraints,
                            false, false, true);
            case MakeTypeParamKind.PARAM_SPEC -> factory.createParamSpec(PythonBuiltinClassType.PParamSpec, name, PNone.NONE, false, false, true);
            case MakeTypeParamKind.TYPE_VAR_TUPLE -> factory.createTypeVarTuple(PythonBuiltinClassType.PTypeVarTuple, name);
            default -> throw shouldNotReachHere();
        };

        frame.setObject(++stackTop, result);
        return stackTop;
    }

    public static MakeTypeParamNode create() {
        return MakeTypeParamNodeGen.create();
    }

    public static MakeTypeParamNode getUncached() {
        return MakeTypeParamNodeGen.getUncached();
    }
}
