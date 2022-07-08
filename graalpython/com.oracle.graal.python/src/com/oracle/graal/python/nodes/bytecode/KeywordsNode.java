/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.lib.PyObjectFunctionStr;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.argument.keywords.ExpandKeywordStarargsNode;
import com.oracle.graal.python.nodes.argument.keywords.NonMappingException;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;

@GenerateUncached
public abstract class KeywordsNode extends PNodeWithContext {
    public abstract PKeyword[] execute(Frame frame, Object sourceCollection, int stackTop);

    @Specialization
    static PKeyword[] kwords(VirtualFrame frame, Object sourceCollection, int stackTop,
                    @Cached ExpandKeywordStarargsNode expandKeywordStarargsNode,
                    @Cached PRaiseNode raise) {
        try {
            return expandKeywordStarargsNode.execute(sourceCollection);
        } catch (NonMappingException e) {
            Object functionName = getFunctionName(frame, stackTop);
            throw raise.raise(PythonBuiltinClassType.TypeError, ErrorMessages.ARG_AFTER_MUST_BE_MAPPING, functionName, e.getObject());
        }
    }

    private static Object getFunctionName(VirtualFrame frame, int stackTop) {
        /*
         * The instruction is only emitted when generating CALL_FUNCTION_KW. The stack layout at
         * this point is [kwargs kw, callable].
         */
        Object callable = frame.getObject(stackTop - 2);
        return PyObjectFunctionStr.getUncached().execute(frame, callable);
    }

    public static KeywordsNode create() {
        return KeywordsNodeGen.create();
    }

    public static KeywordsNode getUncached() {
        return KeywordsNodeGen.getUncached();
    }
}
