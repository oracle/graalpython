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
package com.oracle.graal.python.builtins.modules.csv;

import static com.oracle.graal.python.builtins.modules.csv.CSVModuleBuiltins.NOT_SET;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

@CoreFunctions(extendClasses = PythonBuiltinClassType.CSVDialect)
public final class CSVDialectBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return CSVDialectBuiltinsFactory.getFactories();
    }

    @Builtin(name = "delimiter", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class DelimiterNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doIt(CSVDialect self) {
            return self.delimiter;
        }
    }

    @Builtin(name = "doublequote", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class DoubleQuoteNode extends PythonUnaryBuiltinNode {
        @Specialization
        static boolean doIt(CSVDialect self) {
            return self.doubleQuote;
        }
    }

    @Builtin(name = "escapechar", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class EscapeCharNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doIt(CSVDialect self) {
            return self.escapeChar.equals(NOT_SET) ? PNone.NONE : self.escapeChar;
        }
    }

    @Builtin(name = "lineterminator", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class LineTerminatorNode extends PythonUnaryBuiltinNode {
        @Specialization
        static String doIt(CSVDialect self) {
            return self.lineTerminator;
        }
    }

    @Builtin(name = "quotechar", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class QuoteCharNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object doIt(CSVDialect self) {
            return self.quoteChar.equals(NOT_SET) ? PNone.NONE : self.quoteChar;
        }
    }

    @Builtin(name = "quoting", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class QuotingNode extends PythonUnaryBuiltinNode {
        @Specialization
        static int doIt(CSVDialect self) {
            return self.quoting.ordinal();
        }
    }

    @Builtin(name = "skipinitialspace", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class SkipInitialSpaceNode extends PythonUnaryBuiltinNode {
        @Specialization
        static boolean doIt(CSVDialect self) {
            return self.skipInitialSpace;
        }
    }

    @Builtin(name = "strict", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class StrictNode extends PythonUnaryBuiltinNode {
        @Specialization
        static boolean doIt(CSVDialect self) {
            return self.strict;
        }
    }
}
