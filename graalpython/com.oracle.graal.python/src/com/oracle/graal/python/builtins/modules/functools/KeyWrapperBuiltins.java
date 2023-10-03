/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.functools;

import static com.oracle.graal.python.nodes.ErrorMessages.OTHER_ARG_MUST_BE_KEY;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CALL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___EQ__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___LE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___LT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___HASH__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.expression.BinaryComparisonNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PKeyWrapper)
public final class KeyWrapperBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return KeyWrapperBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
        core.lookupType(PythonBuiltinClassType.PKeyWrapper).setAttribute(T___HASH__, PNone.NONE);
    }

    abstract static class WrapperKeyCompareNode extends PythonBinaryBuiltinNode {
        @Child private BinaryComparisonNode comparisonNode;
        @Child private CallNode callNode;

        protected BinaryComparisonNode ensureComparisonNode() {
            if (comparisonNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                comparisonNode = insert(createCmp());
            }
            return comparisonNode;
        }

        protected CallNode ensureCallNode() {
            if (callNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callNode = insert(CallNode.create());
            }
            return callNode;
        }

        BinaryComparisonNode createCmp() {
            throw CompilerDirectives.shouldNotReachHere();
        }

        @Specialization
        boolean doCompare(VirtualFrame frame, PKeyWrapper self, PKeyWrapper other,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectIsTrueNode isTrueNode) {
            final Object cmpResult = ensureCallNode().execute(frame, self.getCmp(), self.getObject(), other.getObject());
            return isTrueNode.execute(frame, inliningTarget, ensureComparisonNode().executeObject(frame, cmpResult, 0));
        }

        @Fallback
        @SuppressWarnings("unused")
        static boolean fallback(Object self, Object other,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(PythonBuiltinClassType.TypeError, OTHER_ARG_MUST_BE_KEY);
        }
    }

    @Builtin(name = J___LE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class KWLeNode extends WrapperKeyCompareNode {
        @Override
        BinaryComparisonNode createCmp() {
            return BinaryComparisonNode.LeNode.create();
        }
    }

    @Builtin(name = J___LT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class KWLtNode extends WrapperKeyCompareNode {
        @Override
        BinaryComparisonNode createCmp() {
            return BinaryComparisonNode.LtNode.create();
        }
    }

    @Builtin(name = J___GE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class KWGeNode extends WrapperKeyCompareNode {
        @Override
        BinaryComparisonNode createCmp() {
            return BinaryComparisonNode.GeNode.create();
        }
    }

    @Builtin(name = J___GT__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class KWGtNode extends WrapperKeyCompareNode {
        @Override
        BinaryComparisonNode createCmp() {
            return BinaryComparisonNode.GtNode.create();
        }
    }

    @Builtin(name = J___EQ__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class KWEqNode extends WrapperKeyCompareNode {
        @Override
        BinaryComparisonNode createCmp() {
            return BinaryComparisonNode.EqNode.create();
        }
    }

    @Builtin(name = J___CALL__, minNumOfPositionalArgs = 2, declaresExplicitSelf = true, parameterNames = {"$self", "obj"})
    @GenerateNodeFactory
    public abstract static class KWCallNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object call(PKeyWrapper self, Object obj,
                        @Cached PythonObjectFactory factory) {
            final PKeyWrapper keyWrapper = factory.createKeyWrapper(self.getCmp());
            keyWrapper.setObject(obj);
            return keyWrapper;
        }
    }

    @Builtin(name = "obj", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, doc = "Value wrapped by a key function.")
    @GenerateNodeFactory
    public abstract static class KeyWrapperObjNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(value)")
        static Object doGet(PKeyWrapper self, @SuppressWarnings("unused") PNone value) {
            return self.getObject();
        }

        @Specialization
        static Object doSet(PKeyWrapper self, Object value) {
            self.setObject(value);
            return PNone.NONE;
        }
    }
}
