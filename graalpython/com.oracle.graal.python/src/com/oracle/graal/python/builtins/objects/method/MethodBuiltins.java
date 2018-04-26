/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
 * Copyright (c) 2014, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.oracle.graal.python.builtins.objects.method;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__CALL__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.argument.CreateArgumentsNode;
import com.oracle.graal.python.nodes.attributes.GetAttributeNode;
import com.oracle.graal.python.nodes.call.CallDispatchNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

@CoreFunctions(extendClasses = PMethod.class)
public class MethodBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinNode>> getNodeFactories() {
        return MethodBuiltinsFactory.getFactories();
    }

    @Builtin(name = __CALL__, minNumOfArguments = 1, takesVariableArguments = true, takesVariableKeywords = true)
    @GenerateNodeFactory
    public abstract static class CallNode extends PythonBuiltinNode {
        @Child private CallDispatchNode dispatch = CallDispatchNode.create("callCall");
        @Child private CreateArgumentsNode createArgs = CreateArgumentsNode.create();

        @Specialization
        protected Object doIt(PMethod self, Object[] arguments, PKeyword[] keywords) {
            return dispatch.executeCall(self.__func__(), createArgs.executeWithSelf(self.__self__(), arguments), keywords);
        }

        @Specialization
        protected Object doIt(PBuiltinMethod self, Object[] arguments, PKeyword[] keywords) {
            return dispatch.executeCall(self.__func__(), createArgs.executeWithSelf(self.__self__(), arguments), keywords);
        }
    }

    @Builtin(name = "__self__", fixedNumOfArguments = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class SelfNode extends PythonBuiltinNode {
        @Specialization
        protected Object doIt(PMethod self) {
            return self.__self__();
        }

        @Specialization
        protected Object doIt(PBuiltinMethod self) {
            return self.__self__();
        }
    }

    @Builtin(name = "__func__", fixedNumOfArguments = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class FuncNode extends PythonBuiltinNode {
        @Specialization
        protected Object doIt(PMethod self) {
            return self.__func__();
        }

        @Specialization
        protected Object doIt(PBuiltinMethod self) {
            return self.__func__();
        }
    }

    @Builtin(name = SpecialAttributeNames.__NAME__, fixedNumOfArguments = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class NameNode extends PythonBuiltinNode {
        @Specialization
        protected Object doIt(PMethod self,
                        @Cached("create()") GetAttributeNode getCode) {
            return getCode.execute(self.__func__(), SpecialAttributeNames.__NAME__);
        }
    }

    @Builtin(name = SpecialAttributeNames.__CODE__, fixedNumOfArguments = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class CodeNode extends PythonBuiltinNode {
        @Specialization
        protected Object doIt(PMethod self,
                        @Cached("create()") GetAttributeNode getCode) {
            return getCode.execute(self.__func__(), SpecialAttributeNames.__CODE__);
        }
    }

    @Builtin(name = SpecialMethodNames.__EQ__, fixedNumOfArguments = 2)
    @GenerateNodeFactory
    abstract static class EqNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean eq(PMethod self, PMethod other) {
            return self.__func__() == other.__func__() && self.__self__() == other.__self__();
        }

        @Specialization
        boolean eq(PBuiltinMethod self, PBuiltinMethod other) {
            return self.__func__() == other.__func__() && self.__self__() == other.__self__();
        }

        @Fallback
        boolean eq(@SuppressWarnings("unused") Object self, @SuppressWarnings("unused") Object other) {
            return false;
        }
    }
}
