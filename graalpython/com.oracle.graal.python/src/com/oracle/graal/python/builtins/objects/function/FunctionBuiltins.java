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

package com.oracle.graal.python.builtins.objects.function;

import static com.oracle.graal.python.nodes.SpecialAttributeNames.__NAME__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GET__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.method.PMethod;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

@CoreFunctions(extendClasses = PFunction.class)
public class FunctionBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return FunctionBuiltinsFactory.getFactories();
    }

    @SuppressWarnings("unused")
    @Builtin(name = __GET__, fixedNumOfArguments = 3)
    @GenerateNodeFactory
    public abstract static class GetNode extends PythonTernaryBuiltinNode {
        @Specialization(guards = {"self.isStatic()"})
        protected Object doStatic(PFunction self, Object instance, Object klass) {
            return self;
        }

        @Specialization(guards = {"self.isStatic()"})
        protected Object doBuiltinStatic(PBuiltinFunction self, Object instance, Object klass) {
            return self;
        }

        @Specialization(guards = {"!isNone(instance)", "!self.isStatic()"})
        protected PMethod doMethod(PFunction self, Object instance, Object klass) {
            return factory().createMethod(instance, self);
        }

        @Specialization(guards = {"!isNone(instance)", "!self.isStatic()"})
        protected PBuiltinMethod doBuiltinMethod(PBuiltinFunction self, Object instance, Object klass) {
            return factory().createBuiltinMethod(instance, self);
        }

        @Specialization
        protected Object doFunction(PFunction self, PNone instance, Object klass) {
            return self;
        }

        @Specialization
        protected Object doBuiltinFunction(PBuiltinFunction self, PNone instance, Object klass) {
            return self;
        }
    }

    @Builtin(name = __NAME__, minNumOfArguments = 1, maxNumOfArguments = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class NameNode extends PythonBuiltinNode {
        @Child WriteAttributeToObjectNode writeNode;

        @Specialization(guards = "isNoValue(noValue)")
        Object getName(PFunction self, @SuppressWarnings("unused") PNone noValue,
                        @Cached("create()") ReadAttributeFromObjectNode readNode) {
            Object storedName = readNode.execute(self, __NAME__);
            if (storedName == PNone.NO_VALUE) {
                return self.getName();
            } else {
                return storedName;
            }
        }

        @Specialization(guards = "isNoValue(noValue)")
        Object getName(PBuiltinFunction self, @SuppressWarnings("unused") PNone noValue) {
            return self.getName();
        }

        @Specialization
        Object setName(PFunction self, String value) {
            if (writeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                writeNode = insert(WriteAttributeToObjectNode.create());
            }
            writeNode.execute(self, __NAME__, value);
            return PNone.NONE;
        }

        @Specialization
        Object setName(PFunction self, PString value) {
            return setName(self, value.getValue());
        }

        @SuppressWarnings("unused")
        @Specialization
        Object setName(PBuiltinFunction self, Object value) {
            throw raise(PythonErrorType.AttributeError, "attribute '__name__' of builtin function is not writable");
        }

        @SuppressWarnings("unused")
        @Fallback
        Object setName(Object self, Object value) {
            throw raise(PythonErrorType.TypeError, "__name__ must be set to a string object");
        }
    }

}
