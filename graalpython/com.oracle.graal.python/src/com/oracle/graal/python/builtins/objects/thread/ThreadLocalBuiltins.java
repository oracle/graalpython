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
package com.oracle.graal.python.builtins.objects.thread;

import static com.oracle.graal.python.nodes.ErrorMessages.ATTR_NAME_MUST_BE_STRING;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___DICT__;

import java.util.List;

import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetItem;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltins;
import com.oracle.graal.python.builtins.objects.object.ObjectNodes.GenericSetAttrWithDictNode;
import com.oracle.graal.python.builtins.objects.str.StringNodes.CastToTruffleStringChecked0Node;
import com.oracle.graal.python.builtins.objects.str.StringNodes.CastToTruffleStringChecked1Node;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetObjectSlotsNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotDescrGet.CallSlotDescrGet;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotDescrSet;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotGetAttr.GetAttrBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSetAttr.SetAttrBuiltinNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.attributes.MergedObjectTypeModuleGetAttributeNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PThreadLocal)
public final class ThreadLocalBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = ThreadLocalBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ThreadLocalBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = "_local", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class ThreadLocalNode extends PythonBuiltinNode {
        @Specialization
        static PThreadLocal construct(Object cls, Object[] args, PKeyword[] keywordArgs,
                        @Bind Node inliningTarget,
                        @Cached TpSlots.GetCachedTpSlotsNode getSlots,
                        @Cached PRaiseNode raiseNode,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape) {
            if (args.length != 0 || keywordArgs.length != 0) {
                TpSlots slots = getSlots.execute(inliningTarget, cls);
                if (slots.tp_init() == ObjectBuiltins.SLOTS.tp_init()) {
                    throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.INITIALIZATION_ARGUMENTS_ARE_NOT_SUPPORTED);
                }
            }
            return PFactory.createThreadLocal(cls, getInstanceShape.execute(cls), args, keywordArgs);
        }
    }

    @Builtin(name = J___DICT__, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class DictNode extends PythonUnaryBuiltinNode {
        @Specialization
        PDict repr(VirtualFrame frame, PThreadLocal self,
                        @Cached ThreadLocalNodes.GetThreadLocalDict getThreadLocalDict) {
            return getThreadLocalDict.execute(frame, self);
        }
    }

    @Slot(value = SlotKind.tp_getattro, isComplex = true)
    @ImportStatic(PGuards.class)
    @GenerateNodeFactory
    public abstract static class GetAttributeNode extends GetAttrBuiltinNode {
        @Child private CallSlotDescrGet callGetNode;

        /**
         * Keep in sync with
         * {@link com.oracle.graal.python.builtins.objects.object.ObjectBuiltins.GetAttributeNode}
         * and {@link com.oracle.graal.python.builtins.objects.type.TypeBuiltins.GetattributeNode}
         * and {@link MergedObjectTypeModuleGetAttributeNode}
         */
        @Specialization
        Object doIt(VirtualFrame frame, PThreadLocal object, Object keyObj,
                        @Bind Node inliningTarget,
                        @Cached ThreadLocalNodes.GetThreadLocalDict getThreadLocalDict,
                        @Cached LookupAttributeInMRONode.Dynamic lookup,
                        @Cached GetClassNode getClassNode,
                        @Cached GetObjectSlotsNode getDescrSlotsNode,
                        @Cached CastToTruffleStringChecked1Node castToString,
                        @Cached HashingStorageGetItem getDictStorageItem,
                        @Cached InlinedConditionProfile hasDescProfile,
                        @Cached InlinedConditionProfile hasDescrGetProfile,
                        @Cached InlinedConditionProfile hasValueProfile,
                        @Cached PRaiseNode raiseNode) {
            // Note: getting thread local dict has potential side-effects, don't move
            PDict localDict = getThreadLocalDict.execute(frame, object);

            TruffleString key = castToString.cast(inliningTarget, keyObj, ErrorMessages.ATTR_NAME_MUST_BE_STRING, keyObj);

            Object type = getClassNode.execute(inliningTarget, object);
            Object descr = lookup.execute(type, key);
            boolean hasDescr = hasDescProfile.profile(inliningTarget, descr != PNone.NO_VALUE);

            TpSlot get = null;
            boolean hasDescrGet = false;
            if (hasDescr) {
                var descrSlots = getDescrSlotsNode.execute(inliningTarget, descr);
                get = descrSlots.tp_descr_get();
                hasDescrGet = hasDescrGetProfile.profile(inliningTarget, get != null);
                if (hasDescrGet && TpSlotDescrSet.PyDescr_IsData(descrSlots)) {
                    return dispatchDescrGet(frame, object, type, descr, get);
                }
            }

            // The only difference between all 3 nodes
            Object value = getDictStorageItem.execute(frame, inliningTarget, localDict.getDictStorage(), key);
            if (hasValueProfile.profile(inliningTarget, value != null)) {
                return value;
            }

            if (hasDescr) {
                if (!hasDescrGet) {
                    return descr;
                } else {
                    return dispatchDescrGet(frame, object, type, descr, get);
                }
            }

            throw raiseNode.raiseAttributeError(inliningTarget, ErrorMessages.OBJ_P_HAS_NO_ATTR_S, object, key);
        }

        private Object dispatchDescrGet(VirtualFrame frame, Object object, Object type, Object descr, TpSlot get) {
            if (callGetNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callGetNode = insert(CallSlotDescrGet.create());
            }
            return callGetNode.executeCached(frame, get, descr, object, type);
        }
    }

    @ImportStatic(PGuards.class)
    @Slot(value = SlotKind.tp_setattro, isComplex = true)
    @GenerateNodeFactory
    public abstract static class SetattrNode extends SetAttrBuiltinNode {
        @Specialization
        @InliningCutoff
        static void doGeneric(VirtualFrame frame, PThreadLocal object, Object keyObject, Object value,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached ThreadLocalNodes.GetThreadLocalDict getThreadLocalDict,
                        @Cached CastToTruffleStringChecked0Node castKeyNode,
                        @Exclusive @Cached GenericSetAttrWithDictNode setAttrWithDictNode) {
            // Note: getting thread local dict has potential side-effects, don't move
            PDict localDict = getThreadLocalDict.execute(frame, object);
            TruffleString key = castKeyNode.cast(inliningTarget, keyObject, ATTR_NAME_MUST_BE_STRING);
            setAttrWithDictNode.execute(inliningTarget, frame, object, key, value, localDict);
        }
    }
}
