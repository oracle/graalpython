/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.map;

import static com.oracle.graal.python.nodes.BuiltinNames.J_MAP;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotIterNext;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotIterNext.TpIterNextBuiltin;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedLoopConditionProfile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PMap)
public final class MapBuiltins extends PythonBuiltins {

    public static final TpSlots SLOTS = MapBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return MapBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = J_MAP, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    abstract static class MapNode extends PythonVarargsBuiltinNode {
        @Specialization
        static PMap doit(VirtualFrame frame, Object cls, Object[] args, PKeyword[] keywords,
                        @Bind Node inliningTarget,
                        @Cached(inline = false /* uncommon path */) TypeNodes.HasObjectInitNode hasObjectInitNode,
                        @Cached InlinedLoopConditionProfile loopProfile,
                        @Cached PyObjectGetIter getIter,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape,
                        @Cached PRaiseNode raiseNode) {
            if (keywords.length > 0 && hasObjectInitNode.executeCached(cls)) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.S_TAKES_NO_KEYWORD_ARGS, "map()");
            }
            if (args.length < 2) {
                throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.MAP_MUST_HAVE_AT_LEAST_TWO_ARGUMENTS);
            }
            PMap map = PFactory.createMap(cls, getInstanceShape.execute(cls));
            map.setFunction(args[0]);
            Object[] iterators = new Object[args.length - 1];
            loopProfile.profileCounted(inliningTarget, iterators.length);
            for (int i = 0; loopProfile.inject(inliningTarget, i < iterators.length); i++) {
                iterators[i] = getIter.execute(frame, inliningTarget, args[i + 1]);
            }
            map.setIterators(iterators);
            return map;
        }
    }

    @Slot(value = SlotKind.tp_iternext, isComplex = true)
    @GenerateNodeFactory
    abstract static class NextNode extends TpIterNextBuiltin {
        @Specialization(guards = "self.getIterators().length == 1")
        static Object doOne(VirtualFrame frame, PMap self,
                        @Bind Node inliningTarget,
                        @Shared @Cached TpSlots.GetObjectSlotsNode getSlots,
                        @Shared @Cached TpSlotIterNext.CallSlotTpIterNextNode callTpIternext,
                        @Shared @Cached CallNode callNode) {
            Object iterator = self.getIterators()[0];
            TpSlot iternext = getSlots.execute(inliningTarget, iterator).tp_iternext();
            Object item = callTpIternext.execute(frame, inliningTarget, iternext, iterator);
            return callNode.execute(frame, self.getFunction(), item);
        }

        @Specialization(replaces = "doOne")
        static Object doNext(VirtualFrame frame, PMap self,
                        @Bind Node inliningTarget,
                        @Shared @Cached TpSlots.GetObjectSlotsNode getSlots,
                        @Shared @Cached TpSlotIterNext.CallSlotTpIterNextNode callTpIternext,
                        @Shared @Cached CallNode callNode,
                        @Cached InlinedLoopConditionProfile loopProfile) {
            Object[] iterators = self.getIterators();
            Object[] arguments = new Object[iterators.length];
            loopProfile.profileCounted(inliningTarget, iterators.length);
            for (int i = 0; loopProfile.inject(inliningTarget, i < iterators.length); i++) {
                Object iterator = iterators[i];
                TpSlot iternext = getSlots.execute(inliningTarget, iterator).tp_iternext();
                arguments[i] = callTpIternext.execute(frame, inliningTarget, iternext, iterator);
            }
            return callNode.execute(frame, self.getFunction(), arguments);
        }
    }

    @Slot(value = SlotKind.tp_iter, isComplex = true)
    @GenerateNodeFactory
    abstract static class IterNode extends PythonUnaryBuiltinNode {

        @Specialization
        static PMap iter(PMap self) {
            return self;
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class ReduceNode extends PythonBinaryBuiltinNode {
        @Specialization
        static PTuple doit(PMap self, @SuppressWarnings("unused") Object ignored,
                        @Bind PythonLanguage language) {
            Object[] iterators = self.getIterators();
            Object[] args = new Object[iterators.length + 1];
            args[0] = self.getFunction();
            System.arraycopy(iterators, 0, args, 1, iterators.length);
            return PFactory.createTuple(language, new Object[]{PythonBuiltinClassType.PMap, PFactory.createTuple(language, args)});
        }
    }
}
