/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.ast;

import static com.oracle.graal.python.builtins.modules.ast.AstModuleBuiltins.T__ATTRIBUTES;
import static com.oracle.graal.python.builtins.modules.ast.AstModuleBuiltins.T__FIELDS;
import static com.oracle.graal.python.builtins.modules.ast.AstModuleBuiltins.T__FIELD_TYPES;
import static com.oracle.graal.python.nodes.ErrorMessages.P_GOT_MULTIPLE_VALUES_FOR_ARGUMENT_S;
import static com.oracle.graal.python.nodes.ErrorMessages.S_CONSTRUCTOR_TAKES_AT_MOST_D_POSITIONAL_ARGUMENT_S;
import static com.oracle.graal.python.nodes.ErrorMessages.WARN_AST_FIELD_S_MISSING_FROM_P_FIELD_TYPES;
import static com.oracle.graal.python.nodes.ErrorMessages.WARN_P_INIT_GOT_UNEXPECTED_KEYWORD_S;
import static com.oracle.graal.python.nodes.ErrorMessages.WARN_P_INIT_MISSING_REQUIRED_POSITIONAL_ARGUMENT_S;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___DICT__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DICT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.DeprecationWarning;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.util.Arrays;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.WarningsModuleBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetItem;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageLen;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.set.PSet;
import com.oracle.graal.python.builtins.objects.set.SetNodes;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.types.PGenericAlias;
import com.oracle.graal.python.builtins.objects.types.PUnionType;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectReprAsTruffleStringNode;
import com.oracle.graal.python.lib.PyObjectSetAttrO;
import com.oracle.graal.python.lib.PySequenceContainsNode;
import com.oracle.graal.python.lib.PySequenceGetItemNode;
import com.oracle.graal.python.lib.PySequenceSizeNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetOrCreateDictNode;
import com.oracle.graal.python.nodes.object.SetDictNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

@CoreFunctions(extendClasses = PythonBuiltinClassType.AST)
public final class AstBuiltins extends PythonBuiltins {

    public static final TpSlots SLOTS = AstBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return AstBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = "AST", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class AstNode extends PythonVarargsBuiltinNode {

        @Specialization
        static PythonObject generic(Object cls, @SuppressWarnings("unused") Object[] varargs, @SuppressWarnings("unused") PKeyword[] kwargs,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape) {
            return PFactory.createPythonObject(cls, getInstanceShape.execute(cls));
        }
    }

    @Slot(value = SlotKind.tp_init, isComplex = true)
    @SlotSignature(minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    abstract static class InitNode extends PythonVarargsBuiltinNode {

        @Specialization
        protected Object doIt(VirtualFrame frame, Object self, Object[] args, PKeyword[] kwArgs,
                        @Bind Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached PyObjectLookupAttr lookupAttrNode,
                        @Cached PySequenceSizeNode sequenceSizeNode,
                        @Cached PySequenceGetItemNode getItemNode,
                        @Cached SetNodes.ConstructSetNode constructSetNode,
                        @Cached SetNodes.DiscardNode discardNode,
                        @Cached PySequenceContainsNode containsNode,
                        @Cached PyObjectSetAttrO setAttrNode,
                        @Cached HashingStorageGetItem getDictItemNode,
                        @Cached HashingStorageLen storageLenNode,
                        @Bind PythonLanguage language,
                        @Bind PythonContext context,
                        @Cached PyObjectReprAsTruffleStringNode reprNode,
                        @Cached WarningsModuleBuiltins.WarnNode warnNode,
                        @Cached PRaiseNode raiseNode) {
            Object fieldsObj = lookupAttrNode.execute(frame, inliningTarget, self, T__FIELDS);
            int numFields = sequenceSizeNode.execute(frame, inliningTarget, fieldsObj);
            PSet remainingFields = constructSetNode.execute(frame, fieldsObj);
            if (numFields < args.length) {
                throw raiseNode.raise(inliningTarget, TypeError, S_CONSTRUCTOR_TAKES_AT_MOST_D_POSITIONAL_ARGUMENT_S, self, numFields, numFields == 1 ? "" : "s");
            }
            for (int i = 0; i < args.length; ++i) {
                Object field = getItemNode.execute(frame, fieldsObj, i);
                setAttrNode.execute(frame, inliningTarget, self, field, args[i]);
                discardNode.execute(frame, remainingFields, field);
            }
            Object selfType = getClassNode.execute(inliningTarget, self);
            for (PKeyword kwArg : kwArgs) {
                if (containsNode.execute(frame, inliningTarget, fieldsObj, kwArg.getName())) {
                    if (!discardNode.execute(frame, remainingFields, kwArg.getName())) {
                        throw raiseNode.raise(inliningTarget, TypeError, P_GOT_MULTIPLE_VALUES_FOR_ARGUMENT_S, self, kwArg.getName());
                    }
                } else {
                    Object attributesObj = lookupAttrNode.execute(frame, inliningTarget, selfType, T__ATTRIBUTES);
                    if (!containsNode.execute(frame, inliningTarget, attributesObj, kwArg.getName())) {
                        warnNode.warnFormat(frame, DeprecationWarning, WARN_P_INIT_GOT_UNEXPECTED_KEYWORD_S, self, kwArg.getName());
                    }
                }
                setAttrNode.execute(frame, inliningTarget, self, kwArg.getName(), kwArg.getValue());
            }
            if (storageLenNode.execute(inliningTarget, remainingFields.getDictStorage()) > 0) {
                Object fieldTypesObj = lookupAttrNode.execute(frame, inliningTarget, selfType, T__FIELD_TYPES);
                if (!(fieldTypesObj instanceof PDict fieldTypes)) {
                    return PNone.NONE;
                }
                for (int i = 0; i < numFields; i++) {
                    Object field = getItemNode.execute(frame, fieldsObj, i);
                    if (containsNode.execute(frame, inliningTarget, remainingFields, field)) {
                        Object fieldType = getDictItemNode.execute(frame, inliningTarget, fieldTypes.getDictStorage(), field);
                        if (fieldType == null) {
                            warnNode.warnFormat(frame, DeprecationWarning, WARN_AST_FIELD_S_MISSING_FROM_P_FIELD_TYPES, reprNode.execute(frame, inliningTarget, field), self);
                        } else if (fieldType instanceof PUnionType) {
                            // Optional fields have a None default on the class.
                        } else if (fieldType instanceof PGenericAlias) {
                            setAttrNode.execute(frame, inliningTarget, self, field, PFactory.createList(language));
                        } else if (fieldType == AstModuleBuiltins.getAstState(context).clsExprContextTy) {
                            setAttrNode.execute(frame, inliningTarget, self, field, AstModuleBuiltins.getAstState(context).singletonLoad);
                        } else {
                            warnNode.warnFormat(frame, DeprecationWarning, WARN_P_INIT_MISSING_REQUIRED_POSITIONAL_ARGUMENT_S, self, reprNode.execute(frame, inliningTarget, field));
                        }
                    }
                }
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = J___DICT__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    public abstract static class DictNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = "isNoValue(none)")
        static Object doit(PythonObject self, @SuppressWarnings("unused") PNone none,
                        @Bind Node inliningTarget,
                        @Cached GetOrCreateDictNode getDict) {
            return getDict.execute(inliningTarget, self);
        }

        @Specialization
        static Object dict(PythonObject self, PDict dict,
                        @Bind Node inliningTarget,
                        @Cached SetDictNode setDict) {
            setDict.execute(inliningTarget, self, dict);
            return PNone.NONE;
        }

        @Specialization(guards = {"!isNoValue(d)", "!isDict(d)"})
        @SuppressWarnings("unused")
        static Object setDict(PythonObject self, Object d,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.DICT_MUST_BE_SET_TO_DICT, d);
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonBuiltinNode {
        @Specialization
        @SuppressWarnings("unused")
        static Object doit(VirtualFrame frame, PythonObject self, Object ignored,
                        @Bind Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached PyObjectLookupAttr lookupAttr,
                        @Cached PySequenceSizeNode sequenceSizeNode,
                        @Cached PySequenceGetItemNode getItemNode,
                        @Cached HashingStorageGetItem getDictItemNode,
                        @Bind PythonLanguage language) {
            Object clazz = getClassNode.execute(inliningTarget, self);
            Object dict = lookupAttr.execute(frame, inliningTarget, self, T___DICT__);
            Object fieldsObj = lookupAttr.execute(frame, inliningTarget, clazz, T__FIELDS);
            int numFields = sequenceSizeNode.execute(frame, inliningTarget, fieldsObj);
            PDict selfDict = (PDict) dict;
            int numPositionalArgs = 0;
            for (int i = 0; i < numFields; i++) {
                Object field = getItemNode.execute(frame, fieldsObj, i);
                if (!getDictItemNode.hasKey(frame, inliningTarget, selfDict.getDictStorage(), field)) {
                    break;
                }
                numPositionalArgs++;
            }
            PNone[] positionalArgs = new PNone[numPositionalArgs];
            Arrays.fill(positionalArgs, PNone.NONE);
            return PFactory.createTuple(language, new Object[]{clazz, PFactory.createTuple(language, positionalArgs), dict});
        }
    }
}
