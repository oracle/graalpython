/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.HashNotImplemented;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotRichCompare;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.lib.PyObjectRichCompare;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PKeyWrapper)
@HashNotImplemented
public final class KeyWrapperBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = KeyWrapperBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return KeyWrapperBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
    }

    @Slot(value = Slot.SlotKind.tp_richcompare, isComplex = true)
    @GenerateNodeFactory
    abstract static class KeyWrapperRichCmpNode extends TpSlotRichCompare.RichCmpBuiltinNode {
        @Specialization
        boolean doCompare(VirtualFrame frame, PKeyWrapper self, PKeyWrapper other, TpSlotRichCompare.RichCmpOp op,
                        @Bind("this") Node inliningTarget,
                        @Cached CallNode callNode,
                        @Cached PyObjectRichCompare richCompareNode,
                        @Cached PyObjectIsTrueNode isTrueNode) {
            final Object cmpResult = callNode.execute(frame, self.getCmp(), self.getObject(), other.getObject());
            return isTrueNode.execute(frame, richCompareNode.execute(frame, inliningTarget, cmpResult, 0, op));
        }

        @Fallback
        @SuppressWarnings("unused")
        static boolean fallback(Object self, Object other, TpSlotRichCompare.RichCmpOp op,
                        @Bind("this") Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, PythonBuiltinClassType.TypeError, OTHER_ARG_MUST_BE_KEY);
        }
    }

    @Builtin(name = J___CALL__, minNumOfPositionalArgs = 2, declaresExplicitSelf = true, parameterNames = {"$self", "obj"})
    @GenerateNodeFactory
    public abstract static class KWCallNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object call(PKeyWrapper self, Object obj,
                        @Bind PythonLanguage language) {
            final PKeyWrapper keyWrapper = PFactory.createKeyWrapper(language, self.getCmp());
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
