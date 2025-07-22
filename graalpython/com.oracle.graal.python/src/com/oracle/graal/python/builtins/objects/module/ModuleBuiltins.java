/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.module;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.nodes.BuiltinNames.J_MODULE;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___ANNOTATIONS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___DICT__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___ANNOTATIONS__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DICT__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DOC__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___LOADER__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___NAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___PACKAGE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___SPEC__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___DIR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___DIR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GETATTRIBUTE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GETATTR__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.AttributeError;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

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
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.module.ModuleBuiltinsClinicProviders.ModuleNodeClinicProviderGen;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltins;
import com.oracle.graal.python.builtins.objects.str.StringNodes.CastToTruffleStringCheckedNode;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotGetAttr.GetAttrBuiltinNode;
import com.oracle.graal.python.lib.PyDictGetItem;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.builtins.ListNodes;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.GetDictIfExistsNode;
import com.oracle.graal.python.nodes.object.GetOrCreateDictNode;
import com.oracle.graal.python.nodes.object.SetDictNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PythonModule)
public final class ModuleBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = ModuleBuiltinsSlotsGen.SLOTS;

    public static final TruffleString T__INITIALIZING = tsLiteral("_initializing");

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ModuleBuiltinsFactory.getFactories();
    }

    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = J_MODULE, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class ModuleNewNode extends PythonBuiltinNode {

        @Specialization
        @SuppressWarnings("unused")
        static Object doGeneric(Object cls, Object[] varargs, PKeyword[] kwargs,
                        @Bind Node inliningTarget,
                        @Bind PythonLanguage language,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape) {
            return PFactory.createPythonModule(language, cls, getInstanceShape.execute(cls));
        }
    }

    @Slot(value = SlotKind.tp_init, isComplex = true)
    @SlotSignature(name = "module", minNumOfPositionalArgs = 2, parameterNames = {"self", "name", "doc"})
    @ArgumentClinic(name = "name", conversion = ArgumentClinic.ClinicConversion.TString)
    @GenerateNodeFactory
    public abstract static class ModuleNode extends PythonClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ModuleNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        public PNone module(PythonModule self, TruffleString name, Object doc,
                        @Bind Node inliningTarget,
                        @Cached WriteAttributeToObjectNode writeName,
                        @Cached WriteAttributeToObjectNode writeDoc,
                        @Cached WriteAttributeToObjectNode writePackage,
                        @Cached WriteAttributeToObjectNode writeLoader,
                        @Cached WriteAttributeToObjectNode writeSpec,
                        @Cached GetOrCreateDictNode getDict) {
            // create dict if missing
            getDict.execute(inliningTarget, self);

            // init
            writeName.execute(self, T___NAME__, name);
            if (doc != PNone.NO_VALUE) {
                writeDoc.execute(self, T___DOC__, doc);
            } else {
                writeDoc.execute(self, T___DOC__, PNone.NONE);
            }
            writePackage.execute(self, T___PACKAGE__, PNone.NONE);
            writeLoader.execute(self, T___LOADER__, PNone.NONE);
            writeSpec.execute(self, T___SPEC__, PNone.NONE);
            return PNone.NONE;
        }
    }

    @Builtin(name = J___DIR__, minNumOfPositionalArgs = 1, declaresExplicitSelf = true)
    @GenerateNodeFactory
    public abstract static class ModuleDirNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object dir(VirtualFrame frame, PythonModule self,
                        @Bind Node inliningTarget,
                        @Cached PyDictGetItem pyDictGetItem,
                        @Cached ListNodes.ConstructListNode constructListNode,
                        @Cached CallNode callNode,
                        @Cached PyObjectLookupAttr lookup,
                        @Cached PRaiseNode raiseNode) {
            Object dictObj = lookup.execute(frame, inliningTarget, self, T___DICT__);
            if (dictObj instanceof PDict dict) {
                Object dirFunc = pyDictGetItem.execute(frame, inliningTarget, dict, T___DIR__);
                if (dirFunc != null) {
                    return callNode.execute(frame, dirFunc);
                } else {
                    return constructListNode.execute(frame, dict);
                }
            } else {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.IS_NOT_A_DICTIONARY, "<module>.__dict__");
            }
        }
    }

    @Builtin(name = J___DICT__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true)
    @GenerateNodeFactory
    public abstract static class ModuleDictNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = "isNoValue(none)")
        static Object doManaged(PythonModule self, @SuppressWarnings("unused") PNone none,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached GetDictIfExistsNode getDict,
                        @Cached SetDictNode setDict) {
            PDict dict = getDict.execute(self);
            if (dict == null) {
                dict = createDict(inliningTarget, self, setDict);
            }
            return dict;
        }

        @Specialization(guards = "isNoValue(none)")
        static Object doNativeObject(PythonAbstractNativeObject self, @SuppressWarnings("unused") PNone none,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached GetDictIfExistsNode getDict,
                        @Cached PRaiseNode raiseNode) {
            PDict dict = getDict.execute(self);
            if (dict == null) {
                doError(self, none, raiseNode);
            }
            return dict;
        }

        @Fallback
        static Object doError(Object self, @SuppressWarnings("unused") Object dict,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.DESCRIPTOR_DICT_FOR_MOD_OBJ_DOES_NOT_APPLY_FOR_P, self);
        }

        private static PDict createDict(Node inliningTarget, PythonModule self, SetDictNode setDict) {
            PDict dict = PFactory.createDictFixedStorage(PythonLanguage.get(inliningTarget), self);
            setDict.execute(inliningTarget, self, dict);
            return dict;
        }
    }

    @Slot(value = SlotKind.tp_getattro, isComplex = true)
    @GenerateNodeFactory
    public abstract static class ModuleGetattributeNode extends GetAttrBuiltinNode {
        @Specialization
        static Object getattributeStr(VirtualFrame frame, PythonModule self, TruffleString key,
                        @Shared @Cached ObjectBuiltins.GetAttributeNode objectGetattrNode,
                        @Shared @Cached HandleGetattrExceptionNode handleException) {
            try {
                return objectGetattrNode.execute(frame, self, key);
            } catch (PException e) {
                return handleException.execute(frame, self, key, e);
            }
        }

        @Specialization(replaces = "getattributeStr")
        @InliningCutoff
        static Object getattribute(VirtualFrame frame, PythonModule self, Object keyObj,
                        @Bind Node inliningTarget,
                        @Cached CastToTruffleStringCheckedNode castKeyToStringNode,
                        @Shared @Cached ObjectBuiltins.GetAttributeNode objectGetattrNode,
                        @Shared @Cached HandleGetattrExceptionNode handleException) {
            TruffleString key = castKeyToStringNode.cast(inliningTarget, keyObj, ErrorMessages.ATTR_NAME_MUST_BE_STRING, keyObj);
            try {
                return objectGetattrNode.execute(frame, self, key);
            } catch (PException e) {
                return handleException.execute(frame, self, key, e);
            }
        }

        // Note: this is similar to the "use __getattribute__, if error fallback to __getattr__"
        // dance that is normally done in the slot wrapper of __getattribute__/__getattr__ Python
        // level methods. This case is, however, slightly different.
        @GenerateInline(false) // footprint reduction 56 -> 37
        protected abstract static class HandleGetattrExceptionNode extends PNodeWithContext {
            public abstract Object execute(VirtualFrame frame, PythonModule self, TruffleString key, PException e);

            @Specialization
            static Object getattribute(VirtualFrame frame, PythonModule self, TruffleString key, PException e,
                            @Bind Node inliningTarget,
                            @Cached IsBuiltinObjectProfile isAttrError,
                            @Cached ReadAttributeFromObjectNode readGetattr,
                            @Cached InlinedConditionProfile customGetAttr,
                            @Cached CallNode callNode,
                            @Cached PyObjectIsTrueNode castToBooleanNode,
                            @Cached CastToTruffleStringNode castNameToStringNode,
                            @Cached PRaiseNode raiseNode) {
                e.expect(inliningTarget, PythonBuiltinClassType.AttributeError, isAttrError);
                Object getAttr = readGetattr.execute(self, T___GETATTR__);
                if (customGetAttr.profile(inliningTarget, getAttr != PNone.NO_VALUE)) {
                    return callNode.execute(frame, getAttr, key);
                } else {
                    TruffleString moduleName;
                    try {
                        moduleName = castNameToStringNode.execute(inliningTarget, readGetattr.execute(self, T___NAME__));
                    } catch (CannotCastException ce) {
                        // we just don't have the module name
                        moduleName = null;
                    }
                    if (moduleName != null) {
                        Object moduleSpec = readGetattr.execute(self, T___SPEC__);
                        if (moduleSpec != PNone.NO_VALUE) {
                            Object isInitializing = readGetattr.execute(moduleSpec, T__INITIALIZING);
                            if (isInitializing != PNone.NO_VALUE && castToBooleanNode.execute(frame, isInitializing)) {
                                throw raiseNode.raise(inliningTarget, AttributeError, ErrorMessages.MODULE_PARTIALLY_INITIALIZED_S_HAS_NO_ATTR_S, moduleName, key);
                            }
                        }
                        throw raiseNode.raise(inliningTarget, AttributeError, ErrorMessages.MODULE_S_HAS_NO_ATTR_S, moduleName, key);
                    }
                    throw raiseNode.raise(inliningTarget, AttributeError, ErrorMessages.MODULE_HAS_NO_ATTR_S, key);
                }
            }
        }

        @Specialization(guards = "!isPythonModule(self)")
        static Object getattribute(Object self, @SuppressWarnings("unused") Object key,
                        @Bind Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, TypeError, ErrorMessages.DESCRIPTOR_S_REQUIRES_S_OBJ_RECEIVED_P, T___GETATTRIBUTE__, "module", self);
        }
    }

    @Builtin(name = J___ANNOTATIONS__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, allowsDelete = true)
    @GenerateNodeFactory
    abstract static class AnnotationsNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(value)")
        static Object get(Object self, @SuppressWarnings("unused") Object value,
                        @Bind Node inliningTarget,
                        @Shared("read") @Cached ReadAttributeFromObjectNode read,
                        @Shared("write") @Cached WriteAttributeToObjectNode write,
                        @Cached InlinedBranchProfile createAnnotations) {
            Object annotations = read.execute(self, T___ANNOTATIONS__);
            if (annotations == PNone.NO_VALUE) {
                createAnnotations.enter(inliningTarget);
                annotations = PFactory.createDict(PythonLanguage.get(inliningTarget));
                write.execute(self, T___ANNOTATIONS__, annotations);
            }
            return annotations;
        }

        @Specialization(guards = "isDeleteMarker(value)")
        static Object delete(Object self, @SuppressWarnings("unused") Object value,
                        @Bind Node inliningTarget,
                        @Shared("read") @Cached ReadAttributeFromObjectNode read,
                        @Shared("write") @Cached WriteAttributeToObjectNode write,
                        @Cached PRaiseNode raiseNode) {
            Object annotations = read.execute(self, T___ANNOTATIONS__);
            if (annotations == PNone.NO_VALUE) {
                throw raiseNode.raise(inliningTarget, AttributeError, new Object[]{T___ANNOTATIONS__});
            }
            write.execute(self, T___ANNOTATIONS__, PNone.NO_VALUE);
            return PNone.NONE;
        }

        @Fallback
        static Object set(Object self, Object value,
                        @Shared("write") @Cached WriteAttributeToObjectNode write) {
            write.execute(self, T___ANNOTATIONS__, value);
            return PNone.NONE;
        }
    }

    @Slot(value = SlotKind.tp_repr, isComplex = true)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {

        public static final TruffleString T__MODULE_REPR = tsLiteral("_module_repr");

        @Specialization
        Object repr(VirtualFrame frame, Object self,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @Cached PyObjectCallMethodObjArgs callMethod) {
            return callMethod.execute(frame, inliningTarget, context.getImportlib(), T__MODULE_REPR, self);
        }
    }
}
