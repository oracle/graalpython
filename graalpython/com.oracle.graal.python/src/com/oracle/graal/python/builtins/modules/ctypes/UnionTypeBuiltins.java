/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.ctypes;

import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NEW__;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.util.List;

import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.ctypes.StructUnionTypeBuiltins.PyCStructUnionTypeUpdateStgDict;
import com.oracle.graal.python.builtins.modules.ctypes.StructUnionTypeBuiltins.StructUnionTypeNewNode;
import com.oracle.graal.python.builtins.objects.object.ObjectNodes;
import com.oracle.graal.python.builtins.objects.object.ObjectNodes.GenericSetAttrNode;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSetAttr.SetAttrBuiltinNode;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleString.EqualNode;

@CoreFunctions(extendClasses = PythonBuiltinClassType.UnionType)
public final class UnionTypeBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = UnionTypeBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return UnionTypeBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___NEW__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    protected abstract static class NewNode extends StructUnionTypeNewNode {
        @Override
        protected boolean isStruct() {
            return false;
        }
    }

    @Slot(value = SlotKind.tp_setattro, isComplex = true)
    @GenerateNodeFactory
    protected abstract static class SetattrNode extends SetAttrBuiltinNode {
        @Specialization
        static void doStringKey(VirtualFrame frame, Object object, TruffleString key, Object value,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached ObjectNodes.GenericSetAttrNode genericSetAttrNode,
                        @Shared @Cached WriteAttributeToObjectNode write,
                        @Shared @Cached TruffleString.EqualNode equalNode,
                        @Shared @Cached PyCStructUnionTypeUpdateStgDict updateStgDict,
                        @Shared @Cached PythonObjectFactory factory) {
            genericSetAttrNode.execute(inliningTarget, frame, object, key, value, write);
            updateStgDictIfNecessary(frame, object, key, value, equalNode, updateStgDict, factory);
        }

        @Specialization
        @InliningCutoff
        static void doGenericKey(VirtualFrame frame, Object object, Object keyObject, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToTruffleStringNode castKeyNode,
                        @Cached PRaiseNode.Lazy raiseNode,
                        @Shared @Cached ObjectNodes.GenericSetAttrNode genericSetAttrNode,
                        @Shared @Cached WriteAttributeToObjectNode write,
                        @Shared @Cached TruffleString.EqualNode equalNode,
                        @Shared @Cached PyCStructUnionTypeUpdateStgDict updateStgDict,
                        @Shared @Cached PythonObjectFactory factory) {
            TruffleString key = GenericSetAttrNode.castAttributeKey(inliningTarget, keyObject, castKeyNode, raiseNode);
            genericSetAttrNode.execute(inliningTarget, frame, object, key, value, write);
            updateStgDictIfNecessary(frame, object, key, value, equalNode, updateStgDict, factory);
        }

        private static void updateStgDictIfNecessary(VirtualFrame frame, Object object, TruffleString key, Object value,
                        EqualNode equalNode, PyCStructUnionTypeUpdateStgDict updateStgDict, PythonObjectFactory factory) {
            if (equalNode.execute(key, StructUnionTypeBuiltins.T__FIELDS_, TS_ENCODING)) {
                updateStgDict.execute(frame, object, value, false, factory);
            }
        }
    }

}
