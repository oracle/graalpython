/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.exception;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__INIT__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

@CoreFunctions(extendClasses = PythonBuiltinClassType.SystemExit)
public final class SystemExitBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return SystemExitBuiltinsFactory.getFactories();
    }

    @CompilerDirectives.ValueType
    public static final class SystemExitData extends PBaseException.Data {
        private Object code;

        private SystemExitData() {

        }

        public Object getCode() {
            return code;
        }

        public void setCode(Object code) {
            this.code = code;
        }

        public static SystemExitData create(Object code) {
            final SystemExitData data = new SystemExitData();
            data.setCode(code);
            return data;
        }

        public static SystemExitData create(PythonObjectFactory factory, Object[] args) {
            final SystemExitData data = new SystemExitData();
            if (args.length == 1) {
                data.setCode(args[0]);
            } else {
                data.setCode(factory.createTuple(args));
            }
            return data;
        }
    }

    @Builtin(name = __INIT__, minNumOfPositionalArgs = 1, takesVarArgs = true)
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonBuiltinNode {
        @Specialization
        Object initNoArgs(PBaseException self, Object[] args,
                        @Cached BaseExceptionBuiltins.BaseExceptionInitNode baseExceptionInitNode) {
            self.setData(SystemExitData.create(factory(), args));
            baseExceptionInitNode.execute(self, args);
            return PNone.NONE;
        }
    }

    @Builtin(name = "code", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, doc = "exception code")
    @GenerateNodeFactory
    public abstract static class CodeNode extends PythonBuiltinNode {
        @Specialization(guards = "isNoValue(none)")
        public Object code(PBaseException self, @SuppressWarnings("unused") PNone none) {
            final Object data = self.getData();
            assert data instanceof SystemExitData;
            return ((SystemExitData) data).getCode();
        }

        @Specialization(guards = "!isNoValue(value)")
        public Object code(PBaseException self, Object value) {
            final Object data = self.getData();
            assert data instanceof SystemExitData;
            ((SystemExitData) data).setCode(value);
            return PNone.NONE;
        }
    }
}
