/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__QUALNAME__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__OBJCLASS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PBuiltinFunction)
public class BuiltinFunctionBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return BuiltinFunctionBuiltinsFactory.getFactories();
    }

    @Builtin(name = __REPR__, minNumOfPositionalArgs = 1)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "self.getEnclosingType() == null")
        @TruffleBoundary
        Object reprModuleFunction(PBuiltinFunction self) {
            // (tfel): these really shouldn't be accessible, I think
            return String.format("<built-in function %s>", self.getName());
        }

        @Specialization(guards = "self.getEnclosingType() != null")
        @TruffleBoundary
        Object reprClassFunction(PBuiltinFunction self) {
            return String.format("<method '%s' of '%s' objects>", self.getName(), GetNameNode.doSlowPath(self.getEnclosingType()));
        }
    }

    @Builtin(name = __NAME__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class NameNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(noValue)")
        Object getName(PBuiltinFunction self, @SuppressWarnings("unused") PNone noValue) {
            return self.getName();
        }

        @Specialization(guards = "!isNoValue(value)")
        Object setName(@SuppressWarnings("unused") PBuiltinFunction self, @SuppressWarnings("unused") Object value) {
            throw raise(PythonErrorType.AttributeError, ErrorMessages.ATTR_S_OF_S_IS_NOT_WRITABLE, "__name__", "builtin function");
        }
    }

    @Builtin(name = __QUALNAME__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class QualnameNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(noValue)")
        Object getQualname(PBuiltinFunction self, @SuppressWarnings("unused") PNone noValue) {
            return self.getQualname();
        }

        @Specialization(guards = "!isNoValue(value)")
        Object setQualname(@SuppressWarnings("unused") PBuiltinFunction self, @SuppressWarnings("unused") Object value) {
            throw raise(PythonErrorType.AttributeError, ErrorMessages.ATTR_S_OF_S_IS_NOT_WRITABLE, "__qualname__", "builtin function");
        }
    }

    @Builtin(name = __OBJCLASS__, minNumOfPositionalArgs = 1, isGetter = true)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class ObjclassNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "self.getEnclosingType() == null")
        Object objclassMissing(@SuppressWarnings("unused") PBuiltinFunction self) {
            throw raise(PythonErrorType.AttributeError, ErrorMessages.OBJ_S_HAS_NO_ATTR_S, "builtin_function_or_method", "__objclass__");
        }

        @Specialization(guards = "self.getEnclosingType() != null")
        @TruffleBoundary
        Object objclass(PBuiltinFunction self,
                        @Cached("createBinaryProfile()") ConditionProfile profile) {
            return getPythonClass(self.getEnclosingType(), profile);
        }
    }
}
