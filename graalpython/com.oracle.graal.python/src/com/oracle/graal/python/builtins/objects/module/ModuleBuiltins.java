/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__DOC__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__LOADER__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__NAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__PACKAGE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__SPEC__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETATTR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INIT__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.AttributeError;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.common.PHashingCollection;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltins;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.nodes.ErrorMessages;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__DICT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__DIR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__GETATTRIBUTE__;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.builtins.ListNodes;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.expression.CoerceToBooleanNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PythonModule)
public class ModuleBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ModuleBuiltinsFactory.getFactories();
    }

    @Builtin(name = __INIT__, minNumOfPositionalArgs = 2, declaresExplicitSelf = true, parameterNames = {"self", "name", "doc"})
    @GenerateNodeFactory
    @TypeSystemReference(PythonArithmeticTypes.class)
    public abstract static class ModuleNode extends PythonBuiltinNode {
        @Specialization(limit = "1")
        public PNone module(PythonModule self, String name, Object doc,
                        @Cached WriteAttributeToObjectNode writeName,
                        @Cached WriteAttributeToObjectNode writeDoc,
                        @Cached WriteAttributeToObjectNode writePackage,
                        @Cached WriteAttributeToObjectNode writeLoader,
                        @Cached WriteAttributeToObjectNode writeSpec,
                        @CachedLibrary("self") PythonObjectLibrary lib) {
            // create dict if missing
            if (lib.getDict(self) == null) {
                try {
                    lib.setDict(self, factory().createDictFixedStorage(self));
                } catch (UnsupportedMessageException e) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new IllegalStateException(e);
                }
            }

            // init
            writeName.execute(self, __NAME__, name);
            if (doc != PNone.NO_VALUE) {
                writeDoc.execute(self, __DOC__, doc);
            } else {
                writeDoc.execute(self, __DOC__, PNone.NONE);
            }
            writePackage.execute(self, __PACKAGE__, PNone.NONE);
            writeLoader.execute(self, __LOADER__, PNone.NONE);
            writeSpec.execute(self, __SPEC__, PNone.NONE);
            return PNone.NONE;
        }
    }

    @Builtin(name = __DIR__, minNumOfPositionalArgs = 1, declaresExplicitSelf = true)
    @GenerateNodeFactory
    public abstract static class ModuleDirNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object dir(VirtualFrame frame, PythonModule self,
                        @Cached CastToJavaStringNode castToJavaStringNode,
                        @Cached IsBuiltinClassProfile isDictProfile,
                        @Cached HashingCollectionNodes.GetDictStorageNode getDictStorageNode,
                        @Cached ListNodes.ConstructListNode constructListNode,
                        @Cached CallNode callNode,
                        @CachedLibrary(limit = "1") HashingStorageLibrary hashLib,
                        @CachedLibrary(limit = "1") PythonObjectLibrary pol) {
            Object dict = pol.lookupAttribute(self, frame, __DICT__);
            if (isDict(dict, isDictProfile)) {
                HashingStorage dictStorage = getDictStorageNode.execute((PHashingCollection) dict);
                Object dirFunc = hashLib.getItem(dictStorage, __DIR__);
                if (dirFunc != null) {
                    return callNode.execute(dirFunc);
                } else {
                    return constructListNode.execute(dict);
                }
            } else {
                String name = getName(self, pol, hashLib, castToJavaStringNode);
                throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.IS_NOT_A_DICTIONARY, name);
            }
        }

        private String getName(PythonModule self, PythonObjectLibrary pol, HashingStorageLibrary hashLib, CastToJavaStringNode castToJavaStringNode) {
            PDict dict = pol.getDict(self);
            if (dict != null) {
                Object name = hashLib.getItem(dict.getDictStorage(), __NAME__);
                if (name != null) {
                    return castToJavaStringNode.execute(name);
                }
            }
            throw raise(PythonBuiltinClassType.SystemError, ErrorMessages.NAMELESS_MODULE);
        }

        protected static boolean isDict(Object object, IsBuiltinClassProfile profile) {
            return profile.profileObject(object, PythonBuiltinClassType.PDict);
        }
    }

    @Builtin(name = __DICT__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    public abstract static class ModuleDictNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = {"isNoValue(none)"}, limit = "1")
        Object dict(PythonModule self, @SuppressWarnings("unused") PNone none,
                        @CachedLibrary("self") PythonObjectLibrary lib) {
            PDict dict = lib.getDict(self);
            if (dict == null) {
                if (self.getShape().getPropertyCount() == 0) {
                    return PNone.NONE;
                }
                dict = factory().createDictFixedStorage(self);
                try {
                    lib.setDict(self, dict);
                } catch (UnsupportedMessageException e) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new IllegalStateException(e);
                }
            }
            return dict;
        }

        @Specialization(limit = "1")
        Object dict(PythonModule self, PDict dict,
                        @CachedLibrary("self") PythonObjectLibrary lib) {
            try {
                lib.setDict(self, dict);
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException(e);
            }
            return PNone.NONE;
        }

        @Specialization(guards = "isNoValue(none)", limit = "1")
        Object dict(PythonAbstractNativeObject self, @SuppressWarnings("unused") PNone none,
                        @CachedLibrary("self") PythonObjectLibrary lib) {
            PDict dict = lib.getDict(self);
            if (dict == null) {
                raise(self, none);
            }
            return dict;
        }

        @Specialization(guards = {"!isNoValue(mapping)", "!isDict(mapping)"})
        Object dict(@SuppressWarnings("unused") Object self, Object mapping) {
            throw raise(TypeError, ErrorMessages.DICT_MUST_BE_SET_TO_DICT, mapping);
        }

        @Fallback
        Object raise(Object self, @SuppressWarnings("unused") Object dict) {
            throw raise(PythonBuiltinClassType.TypeError, "descriptor '__dict__' for 'module' objects doesn't apply to a '%p' object", self);
        }
    }

    @Builtin(name = __GETATTRIBUTE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ModuleGetattritbuteNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object getattribute(VirtualFrame frame, PythonModule self, Object keyObj,
                        @Cached("create()") IsBuiltinClassProfile isAttrError,
                        @Cached("create()") ObjectBuiltins.GetAttributeNode objectGetattrNode,
                        @Cached("create()") ReadAttributeFromObjectNode readGetattr,
                        @Cached("createBinaryProfile()") ConditionProfile customGetAttr,
                        @Cached("create()") CallNode callNode,
                        @Cached("createIfTrueNode()") CoerceToBooleanNode castToBooleanNode,
                        @Cached CastToJavaStringNode castKeyToStringNode,
                        @Cached CastToJavaStringNode castNameToStringNode) {
            String key;
            try {
                key = castKeyToStringNode.execute(keyObj);
            } catch (CannotCastException e) {
                throw raise(PythonBuiltinClassType.TypeError, ErrorMessages.ATTR_NAME_MUST_BE_STRING, keyObj);
            }

            try {
                return objectGetattrNode.call(frame, self, key);
            } catch (PException e) {
                e.expect(PythonBuiltinClassType.AttributeError, isAttrError);
                Object getAttr = readGetattr.execute(self, __GETATTR__);
                if (customGetAttr.profile(getAttr != PNone.NO_VALUE)) {
                    return callNode.execute(frame, getAttr, key);
                } else {
                    String moduleName;
                    try {
                        moduleName = castNameToStringNode.execute(readGetattr.execute(self, __NAME__));
                    } catch (CannotCastException ce) {
                        // we just don't have the module name
                        moduleName = null;
                    }
                    if (moduleName != null) {
                        Object moduleSpec = readGetattr.execute(self, __SPEC__);
                        if (moduleSpec != PNone.NO_VALUE) {
                            Object isInitializing = readGetattr.execute(moduleSpec, "_initializing");
                            if (isInitializing != PNone.NO_VALUE && castToBooleanNode.executeBoolean(frame, isInitializing)) {
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
            throw raise(TypeError, ErrorMessages.DESCRIPTOR_REQUIRES_OBJ, __GETATTRIBUTE__, "module", self);
        }

    }
}
