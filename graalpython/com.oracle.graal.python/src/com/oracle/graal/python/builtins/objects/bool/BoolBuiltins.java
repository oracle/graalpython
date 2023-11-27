/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates.
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
package com.oracle.graal.python.builtins.objects.bool;

import static com.oracle.graal.python.nodes.SpecialMethodNames.J___AND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___OR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RAND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ROR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___RXOR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___STR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___XOR__;
import static com.oracle.graal.python.nodes.StringLiterals.T_FALSE;
import static com.oracle.graal.python.nodes.StringLiterals.T_TRUE;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.ints.IntBuiltins;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.Boolean)
public final class BoolBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return BoolBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___STR__, minNumOfPositionalArgs = 1)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class StrNode extends PythonBuiltinNode {
        @Specialization
        public static TruffleString doLong(long self) {
            return self == 1 ? T_TRUE : T_FALSE;
        }

        @Specialization
        public static TruffleString doPInt(PInt self) {
            return self.isZero() ? T_FALSE : T_TRUE;
        }
    }

    @Builtin(name = J___REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class RepNode extends StrNode {
    }

    abstract static class BaseBoolBinaryNode extends PythonBinaryBuiltinNode {
        static boolean atLeastOneIsNotBoolean(Object self, Object other) {
            return !PGuards.isBoolean(self) || !PGuards.isBoolean(other);
        }
    }

    @Builtin(name = J___AND__, minNumOfPositionalArgs = 2)
    @Builtin(name = J___RAND__, minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class AndNode extends BaseBoolBinaryNode {
        @Specialization
        static Object doBool(boolean self, boolean other) {
            return self && other;
        }

        @Specialization(guards = "atLeastOneIsNotBoolean(self, other)")
        static Object doOther(VirtualFrame frame, Object self, Object other,
                        @Cached IntBuiltins.AndNode andNode) {
            return andNode.execute(frame, self, other);
        }
    }

    @Builtin(name = J___OR__, minNumOfPositionalArgs = 2)
    @Builtin(name = J___ROR__, minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class OrNode extends BaseBoolBinaryNode {
        @Specialization
        static Object doBool(boolean self, boolean other) {
            return self || other;
        }

        @Specialization(guards = "atLeastOneIsNotBoolean(self, other)")
        static Object doOther(VirtualFrame frame, Object self, Object other,
                        @Cached IntBuiltins.OrNode orNode) {
            return orNode.execute(frame, self, other);
        }
    }

    @Builtin(name = J___XOR__, minNumOfPositionalArgs = 2)
    @Builtin(name = J___RXOR__, minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class XorNode extends BaseBoolBinaryNode {
        @Specialization
        static Object doBool(boolean self, boolean other) {
            return self ^ other;
        }

        @Specialization(guards = "atLeastOneIsNotBoolean(self, other)")
        static Object doOther(VirtualFrame frame, Object self, Object other,
                        @Cached IntBuiltins.XorNode xorNode) {
            return xorNode.execute(frame, self, other);
        }
    }
}
