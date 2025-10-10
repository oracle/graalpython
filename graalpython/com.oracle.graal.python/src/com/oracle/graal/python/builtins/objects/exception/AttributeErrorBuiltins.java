/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.exception;

import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GETSTATE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;
import static com.oracle.graal.python.nodes.StringLiterals.T_NAME;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsInternedLiteral;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.EmptyStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetDictIfExistsNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.profiles.InlinedLoopConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.AttributeError)
public final class AttributeErrorBuiltins extends PythonBuiltins {

    public static final TpSlots SLOTS = AttributeErrorBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return AttributeErrorBuiltinsFactory.getFactories();
    }

    private static final int IDX_NAME = 0;
    private static final int IDX_OBJ = 1;
    private static final int NUM_ATTRS = IDX_OBJ + 1;

    private static final TruffleString T_OBJ = tsInternedLiteral("obj");

    private static final BaseExceptionAttrNode.StorageFactory ATTR_FACTORY = (args) -> new Object[NUM_ATTRS];

    public static Object[] dataForObjKey(Object obj, Object key) {
        return new Object[]{key, obj};
    }

    @Slot(value = SlotKind.tp_init, isComplex = true)
    @SlotSignature(minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    abstract static class InitNode extends PythonVarargsBuiltinNode {

        @Specialization
        static Object init(PBaseException self, Object[] args, PKeyword[] kwargs,
                        @Bind Node inliningTarget,
                        @Cached BaseExceptionBuiltins.BaseExceptionInitNode baseExceptionInitNode,
                        @Cached TruffleString.EqualNode equalNameNode,
                        @Cached TruffleString.EqualNode equalObjNode,
                        @Cached InlinedLoopConditionProfile loopProfile,
                        @Cached PRaiseNode raiseNode) {
            baseExceptionInitNode.execute(self, args);
            Object[] attrs = new Object[NUM_ATTRS];
            loopProfile.profileCounted(inliningTarget, kwargs.length);
            for (int i = 0; loopProfile.inject(inliningTarget, i < kwargs.length); i++) {
                PKeyword kw = kwargs[i];
                TruffleString kwName = kw.getName();
                if (equalNameNode.execute(kwName, T_NAME, TS_ENCODING)) {
                    attrs[IDX_NAME] = kw.getValue();
                } else if (equalObjNode.execute(kwName, T_OBJ, TS_ENCODING)) {
                    attrs[IDX_OBJ] = kw.getValue();
                } else {
                    throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.S_IS_AN_INVALID_ARG_FOR_S, kw.getName(), "AttributeError");
                }
            }
            self.setExceptionAttributes(attrs);
            return PNone.NONE;
        }
    }

    @Builtin(name = "name", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, allowsDelete = true, doc = "attribute name")
    @GenerateNodeFactory
    public abstract static class NameNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object generic(PBaseException self, Object value,
                        @Cached BaseExceptionAttrNode attrNode) {
            return attrNode.execute(self, value, IDX_NAME, ATTR_FACTORY);
        }
    }

    @Builtin(name = "obj", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, allowsDelete = true, doc = "object")
    @GenerateNodeFactory
    public abstract static class ObjNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object generic(PBaseException self, Object value,
                        @Cached BaseExceptionAttrNode attrNode) {
            return attrNode.execute(self, value, IDX_OBJ, ATTR_FACTORY);
        }
    }

    @Builtin(name = J___GETSTATE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class GetStateNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object get(PBaseException self,
                        @Bind Node inliningTarget,
                        @Cached BaseExceptionAttrNode attrNode,
                        @Cached GetDictIfExistsNode getDictIfExistsNode,
                        @Cached HashingStorageNodes.HashingStorageSetItem setHashingStorageItem,
                        @Cached HashingStorageNodes.HashingStorageCopy copyStorageNode,
                        @Bind PythonLanguage language) {
            PDict dict = getDictIfExistsNode.execute(self);
            /*
             * Note from CPython: We specifically are not pickling the obj attribute since there are
             * many cases where it is unlikely to be picklable.
             */
            Object name = attrNode.get(self, IDX_NAME, ATTR_FACTORY);
            if (name != null) {
                HashingStorage storage = (dict != null) ? copyStorageNode.execute(inliningTarget, dict.getDictStorage()) : EmptyStorage.INSTANCE;
                storage = setHashingStorageItem.execute(inliningTarget, storage, T_NAME, name);
                return PFactory.createDict(language, storage);
            } else if (dict != null) {
                return dict;
            } else {
                return PNone.NONE;
            }
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object reduce(VirtualFrame frame, PBaseException self,
                        @Bind Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached ExceptionNodes.GetArgsNode getArgsNode,
                        @Cached GetStateNode getStateNode,
                        @Bind PythonLanguage language) {
            Object clazz = getClassNode.execute(inliningTarget, self);
            Object args = getArgsNode.execute(inliningTarget, self);
            Object state = getStateNode.execute(frame, self);
            return PFactory.createTuple(language, new Object[]{clazz, args, state});
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    public abstract static class SetAttributeErrorContext extends Node {
        public abstract PException execute(Node inliningTarget, PException e, Object obj, Object name);

        @Specialization
        static PException set(Node inliningTarget, PException e, Object obj, Object name,
                        @Cached BuiltinClassProfiles.IsBuiltinObjectProfile errorProfile,
                        @Cached BaseExceptionAttrNode attrNode,
                        @Cached InlinedConditionProfile writeAttrsProfile) {
            e.expectAttributeError(inliningTarget, errorProfile);
            if (writeAttrsProfile.profile(inliningTarget, e.getUnreifiedException() instanceof PBaseException exception &&
                            exception.getExceptionAttributes() != null &&
                            exception.getExceptionAttributes()[IDX_NAME] == null && exception.getExceptionAttributes()[IDX_OBJ] == null)) {
                PBaseException exception = (PBaseException) e.getUnreifiedException();
                attrNode.set(exception, name, IDX_NAME, ATTR_FACTORY);
                attrNode.set(exception, obj, IDX_OBJ, ATTR_FACTORY);
            }
            throw e;
        }
    }
}
