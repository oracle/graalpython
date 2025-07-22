/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.typing;

import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CLASS_GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INIT_SUBCLASS__;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.TypingModuleBuiltins.CallTypingArgsKwargsNode;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PGeneric)
public final class GenericBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return GenericBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___CLASS_GETITEM__, minNumOfPositionalArgs = 1, isClassmethod = true, takesVarArgs = true, takesVarKeywordArgs = true, doc = """
                    Parameterizes a generic class.

                    At least, parameterizing a generic class is the *main* thing this
                    method does. For example, for some generic class `Foo`, this is called
                    when we do `Foo[int]` - there, with `cls=Foo` and `params=int`.

                    However, note that this method is also called when defining generic
                    classes in the first place with `class Foo[T]: ...`.
                    """)
    @GenerateNodeFactory
    public abstract static class ClassGetItemNode extends PythonBuiltinNode {
        private static final TruffleString T_GENERIC_CLASS_ITEM = tsLiteral("_generic_class_getitem");

        @Specialization
        static Object classGetItem(VirtualFrame frame, Object cls, Object[] args, PKeyword[] kwargs,
                        @Bind Node inliningTarget,
                        @Cached CallTypingArgsKwargsNode callTypingNode) {
            return callTypingNode.execute(frame, inliningTarget, T_GENERIC_CLASS_ITEM, cls, args, kwargs);
        }
    }

    @Builtin(name = J___INIT_SUBCLASS__, minNumOfPositionalArgs = 1, isClassmethod = true, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class InitSubclassNode extends PythonBuiltinNode {
        private static final TruffleString T_GENERIC_INIT_SUBCLASS = tsLiteral("_generic_init_subclass");

        @Specialization
        static Object initSubclass(VirtualFrame frame, Object cls, Object[] args, PKeyword[] kwargs,
                        @Bind Node inliningTarget,
                        @Cached CallTypingArgsKwargsNode callTypingNode) {
            return callTypingNode.execute(frame, inliningTarget, T_GENERIC_INIT_SUBCLASS, cls, args, kwargs);
        }
    }
}
