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

import static com.oracle.graal.python.nodes.ErrorMessages.ATTR_NAME_MUST_BE_STRING;
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
import com.oracle.graal.python.builtins.objects.str.StringNodes.CastToTruffleStringCheckedNode;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeBuiltins;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSetAttr.SetAttrBuiltinNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
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

@CoreFunctions(extendClasses = PythonBuiltinClassType.PyCStructType)
public final class PyCStructTypeBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = PyCStructTypeBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PyCStructTypeBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___NEW__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    protected abstract static class NewNode extends StructUnionTypeNewNode {
    }

    @Slot(value = SlotKind.tp_set_attro, isComplex = true)
    @GenerateNodeFactory
    protected abstract static class SetattrNode extends SetAttrBuiltinNode {
        @Specialization
        void doStringKey(VirtualFrame frame, Object object, TruffleString key, Object value,
                        @Shared @Cached TypeBuiltins.SetattrNode typeSetAttr,
                        @Shared @Cached TruffleString.EqualNode equalNode,
                        @Shared @Cached PyCStructUnionTypeUpdateStgDict updateStgDict,
                        @Shared @Cached PythonObjectFactory factory) {
            // CPython just delegates to "PyType_Type.tp_setattro" with the comment:
            /* XXX Should we disallow deleting _fields_? */
            typeSetAttr.executeSetAttr(frame, object, key, value);
            if (equalNode.execute(key, StructUnionTypeBuiltins.T__FIELDS_, TS_ENCODING)) {
                updateStgDict.execute(frame, object, value, true, factory);
            }
        }

        @Specialization
        @InliningCutoff
        void doGeneric(VirtualFrame frame, Object object, Object keyObject, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToTruffleStringCheckedNode castKeyToStringNode,
                        @Shared @Cached TypeBuiltins.SetattrNode typeSetAttr,
                        @Shared @Cached TruffleString.EqualNode equalNode,
                        @Shared @Cached PyCStructUnionTypeUpdateStgDict updateStgDict,
                        @Shared @Cached PythonObjectFactory factory) {
            TruffleString key = castKeyToStringNode.cast(inliningTarget, keyObject, ATTR_NAME_MUST_BE_STRING, keyObject);
            doStringKey(frame, object, key, value, typeSetAttr, equalNode, updateStgDict, factory);
        }
    }
}
