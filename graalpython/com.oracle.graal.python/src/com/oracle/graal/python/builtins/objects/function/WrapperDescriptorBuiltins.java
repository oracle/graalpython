/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.function;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.method.PMethod;
import com.oracle.graal.python.builtins.objects.str.StringUtils;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotDescrGet.DescrGetBuiltinNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.WrapperDescriptor)
public final class WrapperDescriptorBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = WrapperDescriptorBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return WrapperDescriptorBuiltinsFactory.getFactories();
    }

    @Slot(SlotKind.tp_descr_get)
    @GenerateUncached
    @GenerateNodeFactory
    @SuppressWarnings("unused")
    public abstract static class GetNode extends DescrGetBuiltinNode {
        @Specialization(guards = {"!isNoValue(instance)"})
        static PMethod doMethod(PFunction self, Object instance, Object klass,
                        @Bind PythonLanguage language) {
            return PFactory.createMethod(language, PythonBuiltinClassType.MethodWrapper, PythonBuiltinClassType.MethodWrapper.getInstanceShape(language), instance, self);
        }

        @Specialization(guards = "isNoValue(instance)")
        static Object doFunction(PFunction self, Object instance, Object klass) {
            return self;
        }

        @Specialization(guards = {"!isNoValue(instance)"})
        static PBuiltinMethod doBuiltinMethod(PBuiltinFunction self, Object instance, Object klass,
                        @Bind PythonLanguage language) {
            return PFactory.createBuiltinMethod(language, PythonBuiltinClassType.MethodWrapper, PythonBuiltinClassType.MethodWrapper.getInstanceShape(language), instance, self);
        }

        @Specialization(guards = "isNoValue(instance)")
        static Object doBuiltinFunction(PBuiltinFunction self, Object instance, Object klass) {
            return self;
        }
    }

    @Slot(value = SlotKind.tp_repr, isComplex = true)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        static TruffleString reprClassFunction(PBuiltinFunction self,
                        @Cached StringUtils.SimpleTruffleStringFormatNode simpleTruffleStringFormatNode) {
            if (self.getEnclosingType() == null) {
                // XXX: this is wrong
                return simpleTruffleStringFormatNode.format("<builtin_function_or_method '%s'>", self.getName());
            } else {
                return simpleTruffleStringFormatNode.format("<slot wrapper '%s' of '%s' objects>", self.getName(), TypeNodes.GetNameNode.executeUncached(self.getEnclosingType()));
            }
        }
    }
}
