/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 *     one is included with the Software (each a "Larger Work" to which the
 *     Software is contributed by such licensors),
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

import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.truffle.PythonTypes;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.nodes.Node;

@TypeSystemReference(PythonTypes.class)
@ImportStatic(PythonOptions.class)
abstract class CallSpecialMethodNode extends Node {
    protected static boolean isUnary(PBuiltinFunction func) {
        return func.getBuiltinNode() instanceof PythonUnaryBuiltinNode;
    }

    protected static boolean isBinary(PBuiltinFunction func) {
        return func.getBuiltinNode() instanceof PythonBinaryBuiltinNode;
    }

    protected static boolean isTernary(PBuiltinFunction func) {
        return func.getBuiltinNode() instanceof PythonTernaryBuiltinNode;
    }

    protected static boolean isVarargs(PBuiltinFunction func) {
        return func.getBuiltinNode() instanceof PythonVarargsBuiltinNode;
    }

    protected static PythonUnaryBuiltinNode getUnary(PBuiltinFunction func) {
        return (PythonUnaryBuiltinNode) func.getBuiltinNode().emptyCopy();
    }

    protected static PythonBinaryBuiltinNode getBinary(PBuiltinFunction func) {
        return (PythonBinaryBuiltinNode) func.getBuiltinNode().emptyCopy();
    }

    protected static PythonTernaryBuiltinNode getTernary(PBuiltinFunction func) {
        return (PythonTernaryBuiltinNode) func.getBuiltinNode().emptyCopy();
    }

    protected static PythonVarargsBuiltinNode getVarargs(PBuiltinFunction func) {
        return (PythonVarargsBuiltinNode) func.getBuiltinNode().emptyCopy();
    }
}
