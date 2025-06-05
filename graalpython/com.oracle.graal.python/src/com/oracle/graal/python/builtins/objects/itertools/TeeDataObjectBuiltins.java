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
package com.oracle.graal.python.builtins.objects.itertools;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.builtins.modules.ItertoolsModuleBuiltins.warnPickleDeprecated;
import static com.oracle.graal.python.nodes.ErrorMessages.ARG_D_MUST_BE_S_NOT_P;
import static com.oracle.graal.python.nodes.ErrorMessages.S_MUST_BE_S;
import static com.oracle.graal.python.nodes.ErrorMessages.TDATAOBJECT_SHOULDNT_HAVE_NEXT;
import static com.oracle.graal.python.nodes.ErrorMessages.TDATAOBJECT_SHOULD_NOT_HAVE_MORE_LINKS;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.LenNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PTeeDataObject})
public final class TeeDataObjectBuiltins extends PythonBuiltins {

    static final int LINKCELLS = 128;

    public static final TpSlots SLOTS = TeeDataObjectBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return TeeDataObjectBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = "_tee_dataobject", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class TeeDataObjectNode extends PythonVarargsBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        PTeeDataObject construct(Object cls, Object[] arguments, PKeyword[] keywords,
                        @Bind("this") Node inliningTarget,
                        @Cached TypeNodes.IsTypeNode isTypeNode,
                        @Cached InlinedBranchProfile errorProfile,
                        @Bind PythonLanguage language) {
            if (!isTypeNode.execute(inliningTarget, cls)) {
                errorProfile.enter(inliningTarget);
                throw PRaiseNode.raiseStatic(this, TypeError, ErrorMessages.IS_NOT_TYPE_OBJ, "'cls'", cls);
            }
            return PFactory.createTeeDataObject(language);
        }
    }

    @Slot(value = SlotKind.tp_init, isComplex = true)
    @SlotSignature(name = "TeeDataObject", minNumOfPositionalArgs = 1, parameterNames = {"$self", "it", "values", "nxt"})
    @ArgumentClinic(name = "values", defaultValue = "PNone.NONE", useDefaultForNone = true)
    @ArgumentClinic(name = "nxt", defaultValue = "PNone.NONE", useDefaultForNone = true)
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonQuaternaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return TeeDataObjectBuiltinsClinicProviders.InitNodeClinicProviderGen.INSTANCE;
        }

        abstract Object execute(VirtualFrame frame, PTeeDataObject self, Object it, Object values, Object nxt);

        @Specialization(guards = "isNone(values)")
        static Object init(PTeeDataObject self, Object it, @SuppressWarnings("unused") Object values, @SuppressWarnings("unused") PNone nxt) {
            init(self, it, null);
            return PNone.NONE;
        }

        @Specialization(guards = "isNone(values)")
        static Object init(PTeeDataObject self, Object it, @SuppressWarnings("unused") Object values, PTeeDataObject nxt) {
            init(self, it, nxt);
            return PNone.NONE;
        }

        private static void init(PTeeDataObject self, Object it, PTeeDataObject nxt) {
            self.setIt(it);
            self.setValues(new Object[LINKCELLS]);
            self.setNumread(0);
            self.setRunning(false);
            self.setNextlink(nxt);
        }

        @Specialization
        static Object init(VirtualFrame frame, PTeeDataObject self, Object it, PList values, Object nxt,
                        @Bind("this") Node inliningTarget,
                        @Cached LenNode lenNode,
                        @Cached SequenceStorageNodes.GetInternalObjectArrayNode getInternalObjectArrayNode,
                        @Cached InlinedBranchProfile numreadLCProfile,
                        @Cached PRaiseNode raiseNode) {
            int numread = (int) lenNode.execute(frame, values);
            if (numread == LINKCELLS) {
                numreadLCProfile.enter(inliningTarget);
                if (!(nxt instanceof PTeeDataObject)) {
                    throw raiseNode.raise(inliningTarget, ValueError, S_MUST_BE_S, "_tee_dataobject next link", "_tee_dataobject");
                }
            } else if (numread > LINKCELLS) {
                throw raiseNode.raise(inliningTarget, ValueError, TDATAOBJECT_SHOULD_NOT_HAVE_MORE_LINKS, LINKCELLS);
            } else if (!(nxt instanceof PNone)) {
                throw raiseNode.raise(inliningTarget, ValueError, TDATAOBJECT_SHOULDNT_HAVE_NEXT);
            }
            self.setIt(it);
            Object[] valuesArray = getInternalObjectArrayNode.execute(inliningTarget, values.getSequenceStorage());
            Object[] obj = new Object[LINKCELLS];
            PythonUtils.arraycopy(valuesArray, 0, obj, 0, numread);
            self.setValues(obj);
            self.setNumread(numread);
            self.setRunning(false);
            self.setNextlink(nxt == PNone.NONE ? null : (PTeeDataObject) nxt);
            return PNone.NONE;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!isList(values)", "!isNone(values)"})
        static Object init(VirtualFrame frame, PTeeDataObject self, Object it, Object values, Object nxt,
                        @Bind("this") Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ARG_D_MUST_BE_S_NOT_P, "teedataobject()", 2, "list", values);
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonUnaryBuiltinNode {
        abstract Object execute(VirtualFrame frame, PythonObject self);

        @Specialization
        static Object reduce(PTeeDataObject self,
                        @Bind("this") Node inliningTarget,
                        @Cached GetClassNode getClass,
                        @Bind PythonLanguage language) {
            warnPickleDeprecated();
            int numread = self.getNumread();
            Object[] values = new Object[numread];
            PythonUtils.arraycopy(self.getValues(), 0, values, 0, numread);
            Object type = getClass.execute(inliningTarget, self);
            Object nextlink = self.getNextlink();
            PTuple tuple = PFactory.createTuple(language, new Object[]{self.getIt(), PFactory.createList(language, values), nextlink == null ? PNone.NONE : nextlink});
            return PFactory.createTuple(language, new Object[]{type, tuple});
        }
    }
}
