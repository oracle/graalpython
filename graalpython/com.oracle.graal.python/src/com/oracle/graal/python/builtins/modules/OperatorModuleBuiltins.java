/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.python.builtins.modules;

import java.math.BigInteger;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.expression.BinaryComparisonNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;

@CoreFunctions(defineModule = OperatorModuleBuiltins.MODULE_NAME)
public class OperatorModuleBuiltins extends PythonBuiltins {

    protected static final String MODULE_NAME = "_operator";

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return OperatorModuleBuiltinsFactory.getFactories();
    }

    @Builtin(name = "truth", minNumOfPositionalArgs = 1)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class TruthNode extends PythonUnaryBuiltinNode {

        @Specialization
        public boolean doBoolean(boolean value) {
            return value;
        }

        @Specialization
        public boolean doNone(@SuppressWarnings("unused") PNone value) {
            return false;
        }

        @Specialization
        public boolean doInt(long value) {
            return value != 0;
        }

        @Specialization
        @TruffleBoundary
        public boolean doPInt(PInt value) {
            return !value.getValue().equals(BigInteger.ZERO);
        }

        @Specialization
        public boolean doDouble(double value) {
            return value != 0;
        }

        @Specialization
        public boolean doString(String value) {
            return !value.isEmpty();
        }

        private @Child LookupAndCallUnaryNode boolNode;
        private @Child LookupAndCallUnaryNode lenNode;

        @Fallback
        public boolean doObject(VirtualFrame frame, Object value) {
            if (boolNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                boolNode = insert((LookupAndCallUnaryNode.create(SpecialMethodNames.__BOOL__)));
            }
            Object result = boolNode.executeObject(frame, value);
            if (result != PNone.NO_VALUE) {
                return (boolean) result;
            }
            if (lenNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                lenNode = insert((LookupAndCallUnaryNode.create(SpecialMethodNames.__LEN__)));
            }

            result = lenNode.executeObject(frame, value);
            if (result == PNone.NO_VALUE) {
                return false;
            }
            return (int) result != 0;
        }
    }

    @Builtin(name = "eq", minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class EqNode extends PythonBinaryBuiltinNode {

        @Specialization
        public boolean doBoolean(boolean value1, boolean value2) {
            return value1 == value2;
        }

        @Specialization
        public boolean doNone(@SuppressWarnings("unused") PNone value1, @SuppressWarnings("unused") PNone value2) {
            return true;
        }

        @Specialization
        public boolean doInt(long value1, long value2) {
            return value1 == value2;
        }

        @Specialization
        public boolean doDouble(double value1, double value2) {
            return value1 == value2;
        }

        @Specialization
        public boolean doString(String value1, String value2) {
            return value1.equals(value2);
        }

        private @Child BinaryComparisonNode equalsNode;

        @Fallback
        public boolean doObject(VirtualFrame frame, Object value1, Object value2) {
            if (value1 == value2) {
                return true;
            }
            if (equalsNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                equalsNode = insert((BinaryComparisonNode.create(SpecialMethodNames.__EQ__, SpecialMethodNames.__EQ__, "==")));
            }
            return equalsNode.executeBool(frame, value1, value2);
        }
    }

    @Builtin(name = "getitem", minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class GetItemNode extends PythonBinaryBuiltinNode {

        @Specialization
        public Object doDict(PDict dict, Object item) {
            return dict.getItem(item);
        }

        @Specialization
        public Object doSequence(VirtualFrame frame, PSequence value, Object index,
                        @Cached("create()") SequenceStorageNodes.GetItemNode getItemNode) {
            return getItemNode.execute(frame, value.getSequenceStorage(), index);
        }

        @Specialization
        public Object doObject(VirtualFrame frame, Object value, Object index,
                        @Cached("create(__GETITEM__)") LookupAndCallBinaryNode getItemNode) {
            return getItemNode.executeObject(frame, value, index);
        }
    }

    // _compare_digest
    @Builtin(name = "_compare_digest", minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class CompareDigestNode extends PythonBinaryBuiltinNode {

        @Specialization
        public boolean doString(String arg1, String arg2) {
            return arg1.equals(arg2);
        }

    }
}
