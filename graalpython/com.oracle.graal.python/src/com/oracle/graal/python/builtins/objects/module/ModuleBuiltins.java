/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GETATTRIBUTE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___DIR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GETATTRIBUTE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GETATTR__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.AttributeError;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetItem;
import com.oracle.graal.python.builtins.objects.common.PHashingCollection;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.module.ModuleBuiltinsClinicProviders.ModuleNodeClinicProviderGen;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltins;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithRaise;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.builtins.ListNodes;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.expression.CoerceToBooleanNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
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
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PythonModule)
public final class ModuleBuiltins extends PythonBuiltins {

    public static final TruffleString T__INITIALIZING = tsLiteral("_initializing");

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ModuleBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___INIT__, minNumOfPositionalArgs = 2, declaresExplicitSelf = true, parameterNames = {"self", "name", "doc"})
    @GenerateNodeFactory
    @ArgumentClinic(name = "name", conversion = ArgumentClinic.ClinicConversion.TString)
    public abstract static class ModuleNode extends PythonClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return ModuleNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        public PNone module(PythonModule self, TruffleString name, Object doc,
                        @Bind("this") Node inliningTarget,
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
        Object dir(VirtualFrame frame, PythonModule self,
                        @Bind("this") Node inliningTarget,
                        @Cached IsBuiltinObjectProfile isDictProfile,
                        @Cached ListNodes.ConstructListNode constructListNode,
                        @Cached CallNode callNode,
                        @Cached PyObjectLookupAttr lookup,
                        @Cached HashingStorageGetItem getItem) {
            Object dict = lookup.execute(frame, inliningTarget, self, T___DICT__);
            if (isDictProfile.profileObject(inliningTarget, dict, PythonBuiltinClassType.PDict)) {
                HashingStorage dictStorage = ((PHashingCollection) dict).getDictStorage();
                Object dirFunc = getItem.execute(inliningTarget, dictStorage, T___DIR__);
                if (dirFunc != null) {
                    return callNode.execute(frame, dirFunc);
                } else {
                    return constructListNode.execute(frame, dict);
                }
            } else {
                throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.IS_NOT_A_DICTIONARY, "<module>.__dict__");
            }
        }
    }

    @Builtin(name = J___DICT__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true)
    @GenerateNodeFactory
    public abstract static class ModuleDictNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = {"isNoValue(none)"}, limit = "1")
        Object doManagedCachedShape(PythonModule self, @SuppressWarnings("unused") PNone none,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached GetDictIfExistsNode getDict,
                        @Shared @Cached SetDictNode setDict,
                        @CachedLibrary("self") DynamicObjectLibrary dynamicObjectLibrary) {
            PDict dict = getDict.execute(self);
            if (dict == null) {
                if (hasInitialProperties(dynamicObjectLibrary, self)) {
                    return PNone.NONE;
                }
                dict = createDict(inliningTarget, self, setDict);
            }
            return dict;
        }

        @Specialization(guards = "isNoValue(none)", replaces = "doManagedCachedShape")
        Object doManaged(PythonModule self, @SuppressWarnings("unused") PNone none,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached GetDictIfExistsNode getDict,
                        @Shared @Cached SetDictNode setDict) {
            PDict dict = getDict.execute(self);
            if (dict == null) {
                if (hasInitialPropertiesUncached(self)) {
                    return PNone.NONE;
                }
                dict = createDict(inliningTarget, self, setDict);
            }
            return dict;
        }

        @Specialization(guards = "isNoValue(none)")
        Object doNativeObject(PythonAbstractNativeObject self, @SuppressWarnings("unused") PNone none,
                        @Shared @Cached GetDictIfExistsNode getDict) {
            PDict dict = getDict.execute(self);
            if (dict == null) {
                doError(self, none);
            }
            return dict;
        }

        @Fallback
        Object doError(Object self, @SuppressWarnings("unused") Object dict) {
            throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.DESCRIPTOR_DICT_FOR_MOD_OBJ_DOES_NOT_APPLY_FOR_P, self);
        }

        private PDict createDict(Node inliningTarget, PythonModule self, SetDictNode setDict) {
            PDict dict = factory().createDictFixedStorage(self);
            setDict.execute(inliningTarget, self, dict);
            return dict;
        }

        @TruffleBoundary
        private static boolean hasInitialPropertiesUncached(PythonModule self) {
            return hasInitialProperties(DynamicObjectLibrary.getUncached(), self);
        }

        private static boolean hasInitialProperties(DynamicObjectLibrary dynamicObjectLibrary, PythonModule self) {
            return hasInitialPropertyCount(dynamicObjectLibrary, self) && initialPropertiesChanged(dynamicObjectLibrary, self);
        }

        private static boolean hasInitialPropertyCount(DynamicObjectLibrary dynamicObjectLibrary, PythonModule self) {
            return dynamicObjectLibrary.getShape(self).getPropertyCount() == PythonModule.INITIAL_MODULE_ATTRS.length;
        }

        @ExplodeLoop
        private static boolean initialPropertiesChanged(DynamicObjectLibrary lib, PythonModule self) {
            for (int i = 0; i < PythonModule.INITIAL_MODULE_ATTRS.length; i++) {
                if (lib.getOrDefault(self, PythonModule.INITIAL_MODULE_ATTRS[i], PNone.NO_VALUE) != PNone.NO_VALUE) {
                    return false;
                }
            }
            return true;
        }
    }

    @Builtin(name = J___GETATTRIBUTE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ModuleGetattritbuteNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object getattributeString(VirtualFrame frame, PythonModule self, TruffleString key,
                        @Shared("getattr") @Cached ObjectBuiltins.GetAttributeNode objectGetattrNode,
                        @Shared("handleException") @Cached HandleGetattrExceptionNode handleException) {
            try {
                return objectGetattrNode.execute(frame, self, key);
            } catch (PException e) {
                return handleException.execute(frame, self, key, e);
            }
        }

        @Specialization
        Object getattribute(VirtualFrame frame, PythonModule self, Object keyObj,
                        @Bind("this") Node inliningTarget,
                        @Cached CastToTruffleStringNode castKeyToStringNode,
                        @Shared("getattr") @Cached ObjectBuiltins.GetAttributeNode objectGetattrNode,
                        @Shared("handleException") @Cached HandleGetattrExceptionNode handleException) {
            TruffleString key;
            try {
                key = castKeyToStringNode.execute(inliningTarget, keyObj);
            } catch (CannotCastException e) {
                throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.ATTR_NAME_MUST_BE_STRING, keyObj);
            }
            return getattributeString(frame, self, key, objectGetattrNode, handleException);
        }

        protected abstract static class HandleGetattrExceptionNode extends PNodeWithRaise {
            public abstract Object execute(VirtualFrame frame, PythonModule self, TruffleString key, PException e);

            @Specialization
            Object getattribute(VirtualFrame frame, PythonModule self, TruffleString key, PException e,
                            @Bind("this") Node inliningTarget,
                            @Cached IsBuiltinObjectProfile isAttrError,
                            @Cached ReadAttributeFromObjectNode readGetattr,
                            @Cached InlinedConditionProfile customGetAttr,
                            @Cached CallNode callNode,
                            @Cached CoerceToBooleanNode.YesNode castToBooleanNode,
                            @Cached CastToTruffleStringNode castNameToStringNode) {
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
                            if (isInitializing != PNone.NO_VALUE && castToBooleanNode.executeBoolean(frame, inliningTarget, isInitializing)) {
                                throw raise(AttributeError, ErrorMessages.MODULE_PARTIALLY_INITIALIZED_S_HAS_NO_ATTR_S, moduleName, key);
                            }
                        }
                        throw raise(AttributeError, ErrorMessages.MODULE_S_HAS_NO_ATTR_S, moduleName, key);
                    }
                    throw raise(AttributeError, ErrorMessages.MODULE_HAS_NO_ATTR_S, key);
                }
            }
        }

        @Specialization(guards = "!isPythonModule(self)")
        Object getattribute(Object self, @SuppressWarnings("unused") Object key) {
            throw raise(TypeError, ErrorMessages.DESCRIPTOR_S_REQUIRES_S_OBJ_RECEIVED_P, T___GETATTRIBUTE__, "module", self);
        }
    }

    @Builtin(name = J___ANNOTATIONS__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, allowsDelete = true)
    @GenerateNodeFactory
    abstract static class AnnotationsNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(value)")
        Object get(Object self, @SuppressWarnings("unused") Object value,
                        @Shared("read") @Cached ReadAttributeFromObjectNode read,
                        @Shared("write") @Cached WriteAttributeToObjectNode write) {
            Object annotations = read.execute(self, T___ANNOTATIONS__);
            if (annotations == PNone.NO_VALUE) {
                annotations = factory().createDict();
                write.execute(self, T___ANNOTATIONS__, annotations);
            }
            return annotations;
        }

        @Specialization(guards = "isDeleteMarker(value)")
        Object delete(Object self, @SuppressWarnings("unused") Object value,
                        @Shared("read") @Cached ReadAttributeFromObjectNode read,
                        @Shared("write") @Cached WriteAttributeToObjectNode write) {
            Object annotations = read.execute(self, T___ANNOTATIONS__);
            if (annotations == PNone.NO_VALUE) {
                throw raise(AttributeError, new Object[]{T___ANNOTATIONS__});
            }
            write.execute(self, T___ANNOTATIONS__, PNone.NO_VALUE);
            return PNone.NONE;
        }

        @Fallback
        Object set(Object self, Object value,
                        @Shared("write") @Cached WriteAttributeToObjectNode write) {
            write.execute(self, T___ANNOTATIONS__, value);
            return PNone.NONE;
        }
    }
}
