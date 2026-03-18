/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___ANNOTATIONS__;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.cell.PCell;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.compiler.BytecodeCodeUnit;
import com.oracle.graal.python.compiler.OpCodes;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToPythonObjectNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

public abstract class MakeFunctionNode extends PNodeWithContext {
    private final int codeIndex;
    private final BytecodeCodeUnit codeUnit;
    @CompilationFinal private PCode cachedCode;

    private final Assumption codeStableAssumption = Truffle.getRuntime().createAssumption("code stable assumption");

    public abstract int execute(VirtualFrame frame, Object globals, int initialStackTop, int flags);

    public MakeFunctionNode(int codeIndex, BytecodeCodeUnit codeUnit) {
        this.codeIndex = codeIndex;
        this.codeUnit = codeUnit;
    }

    @Specialization
    int makeFunction(VirtualFrame frame, Object globals, int initialStackTop, int flags,
                    @Bind PythonLanguage language,
                    @Cached WriteAttributeToPythonObjectNode writeAttrNode) {
        int stackTop = initialStackTop;

        PCode code = cachedCode;
        if (code == null) {
            code = PArguments.getCodeObject(frame).getOrCreateChildCode(codeIndex, codeUnit);
            if (PythonLanguage.get(this).isSingleContext()) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                /*
                 * We cannot initialize the cached code in create, because that may be called
                 * without langauge context when materializing nodes for instrumentation
                 */
                cachedCode = code;
            } else {
                // In multi-context mode we have to create the code for every execution
            }
        }

        PCell[] closure = null;
        Object annotations = null;
        PKeyword[] kwdefaults = null;
        Object[] defaults = null;

        if ((flags & OpCodes.MakeFunctionFlags.HAS_CLOSURE) != 0) {
            closure = (PCell[]) frame.getObject(stackTop);
            frame.setObject(stackTop--, null);
        }
        if ((flags & OpCodes.MakeFunctionFlags.HAS_ANNOTATIONS) != 0) {
            annotations = frame.getObject(stackTop);
            frame.setObject(stackTop--, null);
        }
        if ((flags & OpCodes.MakeFunctionFlags.HAS_KWONLY_DEFAULTS) != 0) {
            kwdefaults = (PKeyword[]) frame.getObject(stackTop);
            frame.setObject(stackTop--, null);
        }
        if ((flags & OpCodes.MakeFunctionFlags.HAS_DEFAULTS) != 0) {
            defaults = (Object[]) frame.getObject(stackTop);
            frame.setObject(stackTop--, null);
        }

        PFunction function = PFactory.createFunction(language, codeUnit.name, codeUnit.qualname, code, (PythonObject) globals, defaults, kwdefaults, closure, codeStableAssumption);

        if (annotations != null) {
            writeAttrNode.execute(function, T___ANNOTATIONS__, annotations);
        }

        frame.setObject(++stackTop, function);
        return stackTop;
    }

    @NeverDefault
    public static MakeFunctionNode create(int codeIndex, BytecodeCodeUnit codeUnit) {
        return MakeFunctionNodeGen.create(codeIndex, codeUnit);
    }
}
