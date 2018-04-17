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
package com.oracle.graal.python.builtins.objects.cpyobject;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

@CoreFunctions(extendClasses = PythonObject.class)
public class PyObjectBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinNode>> getNodeFactories() {
        return PyObjectBuiltinsFactory.getFactories();
    }

    @Builtin(name = "ob_refcnt", fixedNumOfArguments = 1, isGetter = true)
    @GenerateNodeFactory
    static abstract class ObRefcnt extends PythonUnaryBuiltinNode {
        @Specialization
        int run(@SuppressWarnings("unused") Object object) {
            return 0;
        }
    }

    @Builtin(name = "ob_base", fixedNumOfArguments = 1, isGetter = true)
    @GenerateNodeFactory
    static abstract class ObBasecnt extends PythonUnaryBuiltinNode {
        @Specialization
        Object run(Object object) {
            return object;
        }
    }

    @Builtin(name = "ob_type", fixedNumOfArguments = 1, isGetter = true)
    @GenerateNodeFactory
    static abstract class ObType extends PythonUnaryBuiltinNode {
        @Child GetClassNode getClass = GetClassNode.create();

        @Specialization
        PythonClass run(Object object) {
            return getClass.execute(object);
        }
    }

    @Builtin(name = "ob_size", fixedNumOfArguments = 1, isGetter = true)
    @GenerateNodeFactory
    static abstract class ObSize extends PythonUnaryBuiltinNode {
        @Child LookupAndCallUnaryNode callLenNode = LookupAndCallUnaryNode.create(SpecialMethodNames.__LEN__);

        @Specialization
        int run(Object object) {
            try {
                return callLenNode.executeInt(object);
            } catch (UnexpectedResultException e) {
                return -1;
            }
        }
    }

    @Builtin(name = "ob_sval", fixedNumOfArguments = 1, isGetter = true)
    @GenerateNodeFactory
    static abstract class ObSval extends PythonUnaryBuiltinNode {

        @Specialization
        Object run(PBytes object) {
            return object.getInternalByteArray();
        }
    }
}
