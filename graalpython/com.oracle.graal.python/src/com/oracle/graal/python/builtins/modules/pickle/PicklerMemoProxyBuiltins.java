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
package com.oracle.graal.python.builtins.modules.pickle;

import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;

import java.util.LinkedHashMap;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.pickle.MemoTable.MemoIterator;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PicklerMemoProxy)
public class PicklerMemoProxyBuiltins extends PythonBuiltins {

    public static final TpSlots SLOTS = PicklerMemoProxyBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PicklerMemoProxyBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = "PicklerMemoProxy", minNumOfPositionalArgs = 2, parameterNames = {"$cls", "pickler"})
    @GenerateNodeFactory
    abstract static class ConstructPicklerMemoProxyNode extends PythonBinaryBuiltinNode {
        @Specialization
        PPicklerMemoProxy construct(Object cls, PPickler pickler,
                        @Bind PythonLanguage language,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape) {
            return PFactory.createPicklerMemoProxy(language, pickler, cls, getInstanceShape.execute(cls));
        }
    }

    @Builtin(name = "clear", minNumOfPositionalArgs = 1, parameterNames = {"$self"})
    @GenerateNodeFactory
    public abstract static class PicklerMemoProxyClearNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object clear(PPicklerMemoProxy self) {
            final MemoTable memoTable = self.getPickler().getMemo();
            memoTable.clear();
            return PNone.NONE;
        }
    }

    @TruffleBoundary
    public static PDict picklerMemoCopyImpl(MemoTable memoTable) {
        PythonLanguage language = PythonLanguage.get(null);
        LinkedHashMap<Object, Object> copy = new LinkedHashMap<>();
        MemoIterator iterator = memoTable.iterator();
        while (iterator.advance()) {
            copy.put(System.identityHashCode(iterator.key()),
                            PFactory.createTuple(language, new Object[]{iterator.value(), iterator.key()}));

        }
        return PFactory.createDictFromMapGeneric(language, copy);
    }

    @Builtin(name = "copy", minNumOfPositionalArgs = 1, parameterNames = {"$self"})
    @GenerateNodeFactory
    public abstract static class PicklerMemoProxyCopyNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object copy(PPicklerMemoProxy self) {
            final MemoTable memoTable = self.getPickler().getMemo();
            return picklerMemoCopyImpl(memoTable);
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1, parameterNames = {"$self"})
    @GenerateNodeFactory
    public abstract static class PicklerMemoProxyReduceNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object reduce(PPicklerMemoProxy self,
                        @Bind PythonLanguage language) {
            final MemoTable memoTable = self.getPickler().getMemo();
            final PDict dictMemoCopy = picklerMemoCopyImpl(memoTable);
            final PTuple dictArgs = PFactory.createTuple(language, new Object[]{dictMemoCopy});
            return PFactory.createTuple(language, new Object[]{PythonBuiltinClassType.PDict, dictArgs});
        }
    }
}
