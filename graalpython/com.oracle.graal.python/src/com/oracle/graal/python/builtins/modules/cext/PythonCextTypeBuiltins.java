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
package com.oracle.graal.python.builtins.modules.cext;

import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Direct;
import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Ignored;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.ConstCharPtrAsTruffleString;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Int;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Pointer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyBufferProcs;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectBorrowed;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectTransfer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyTypeObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Py_ssize_t;
import static com.oracle.graal.python.builtins.objects.cext.common.CExtContext.METH_CLASS;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_name;
import static com.oracle.graal.python.nodes.HiddenAttr.AS_BUFFER;
import static com.oracle.graal.python.nodes.HiddenAttr.METHOD_DEF_PTR;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DOC__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___NAME__;
import static com.oracle.graal.python.util.PythonUtils.EMPTY_OBJECT_ARRAY;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApi7BuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApi8BuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBinaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiUnaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.PyObjectSetAttrNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.PythonNativeClass;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiMemberAccessNodes.ReadMemberNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiMemberAccessNodes.WriteMemberNode;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.GetterRoot;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.PExternalFunctionWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes.SetterRoot;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor;
import com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers.CArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.EnsureExecutableNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtContext;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.GetSetDescriptor;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.lib.PyDictGetItem;
import com.oracle.graal.python.lib.PyDictSetDefault;
import com.oracle.graal.python.lib.PyDictSetItem;
import com.oracle.graal.python.nodes.HiddenAttr;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToPythonObjectNode;
import com.oracle.graal.python.nodes.object.GetDictIfExistsNode;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.runtime.sequence.storage.MroSequenceStorage;
import com.oracle.graal.python.util.Function;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.utilities.CyclicAssumption;

public final class PythonCextTypeBuiltins {

    @CApiBuiltin(ret = PyObjectBorrowed, args = {PyTypeObject, PyObject}, call = Direct)
    abstract static class _PyType_Lookup extends CApiBinaryBuiltinNode {
        @Specialization
        Object doGeneric(Object type, Object name,
                        @Bind Node inliningTarget,
                        @Cached CastToTruffleStringNode castToTruffleStringNode,
                        @Cached TypeNodes.GetMroStorageNode getMroStorageNode,
                        @Cached PythonCextBuiltins.PromoteBorrowedValue promoteBorrowedValue,
                        @Cached CStructAccess.ReadObjectNode getNativeDict,
                        @Cached GetDictIfExistsNode getDictIfExistsNode,
                        @CachedLibrary(limit = "3") DynamicObjectLibrary dylib,
                        @Cached PyDictGetItem getItem,
                        @Cached PyDictSetItem setItem) {
            TruffleString key = castToTruffleStringNode.castKnownString(inliningTarget, name);
            MroSequenceStorage mro = getMroStorageNode.execute(inliningTarget, type);
            for (int i = 0; i < mro.length(); i++) {
                PythonAbstractClass cls = mro.getPythonClassItemNormalized(i);
                PDict dict;
                if (cls instanceof PythonAbstractNativeObject nativeCls) {
                    Object dictObj = getNativeDict.readFromObj(nativeCls, CFields.PyTypeObject__tp_dict);
                    if (dictObj instanceof PDict d) {
                        dict = d;
                    } else {
                        continue;
                    }
                } else if (cls instanceof PythonManagedClass managedCls) {
                    dict = getDictIfExistsNode.execute(managedCls);
                } else {
                    throw CompilerDirectives.shouldNotReachHere();
                }
                Object value;
                if (dict == null) {
                    value = dylib.getOrDefault((DynamicObject) cls, key, null);
                } else {
                    value = getItem.execute(null, inliningTarget, dict, key);
                }
                if (value != null && value != PNone.NO_VALUE) {
                    Object promoted = promoteBorrowedValue.execute(inliningTarget, value);
                    if (promoted != null) {
                        if (dict == null) {
                            dylib.put((DynamicObject) cls, key, promoted);
                        } else {
                            setItem.execute(null, inliningTarget, dict, key, promoted);
                        }
                        return promoted;
                    }
                    return value;
                }
            }
            return getNativeNull();
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyTypeObject, ConstCharPtrAsTruffleString}, call = Ignored)
    abstract static class GraalPyPrivate_Compute_Mro extends CApiBinaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        static Object doIt(PythonNativeClass self, TruffleString className,
                        @Bind Node inliningTarget) {
            PythonAbstractClass[] doSlowPath = TypeNodes.ComputeMroNode.doSlowPath(inliningTarget, self);
            return PFactory.createTuple(PythonLanguage.get(null), new MroSequenceStorage(className, doSlowPath));
        }
    }

    static final class NativeTypeDictStorage extends DynamicObject {
        public NativeTypeDictStorage(Shape shape) {
            super(shape);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyTypeObject}, call = Ignored)
    abstract static class GraalPyPrivate_NewTypeDict extends CApiUnaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        static PDict doGeneric(PythonNativeClass nativeClass) {
            PythonLanguage language = PythonLanguage.get(null);
            NativeTypeDictStorage nativeTypeStore = new NativeTypeDictStorage(language.getEmptyShape());
            PDict dict = PFactory.createDict(language, new DynamicObjectStorage(nativeTypeStore));
            HiddenAttr.WriteNode.executeUncached(dict, HiddenAttr.INSTANCESHAPE, language.getShapeForClass(nativeClass));
            return dict;
        }
    }

    @CApiBuiltin(ret = ArgDescriptor.Void, args = {PyTypeObject}, call = Direct)
    abstract static class PyType_Modified extends CApiUnaryBuiltinNode {
        @TruffleBoundary
        @Specialization
        static Object doIt(PythonAbstractClass object,
                        @Bind Node inliningTarget) {
            if (object instanceof PythonAbstractNativeObject clazz) {
                PythonContext context = PythonContext.get(inliningTarget);
                CyclicAssumption nativeClassStableAssumption = context.getNativeClassStableAssumption(clazz, false);
                if (nativeClassStableAssumption != null) {
                    nativeClassStableAssumption.invalidate("PyType_Modified(\"" + TypeNodes.GetNameNode.executeUncached(clazz).toJavaStringUncached() + "\") called");
                }
                MroSequenceStorage mroStorage = TypeNodes.GetMroStorageNode.executeUncached(clazz);
                mroStorage.lookupChanged();
                // Reload slots from native, which also invalidates cached slot lookups
                clazz.setTpSlots(TpSlots.fromNative(clazz, context));
            } else {
                MroSequenceStorage mroStorage = TypeNodes.GetMroStorageNode.executeUncached(object);
                mroStorage.lookupChanged();
            }
            return PNone.NO_VALUE;
        }
    }

    @CApiBuiltin(ret = Int, args = {Pointer}, call = Ignored)
    abstract static class GraalPyPrivate_Trace_Type extends CApiUnaryBuiltinNode {
        private static final TruffleLogger LOGGER = CApiContext.getLogger(GraalPyPrivate_Trace_Type.class);

        @Specialization
        @TruffleBoundary
        int trace(Object ptr) {
            LOGGER.fine(() -> PythonUtils.formatJString("Initializing native type %s (ptr = %s)",
                            CStructAccess.ReadCharPtrNode.getUncached().read(ptr, PyTypeObject__tp_name),
                            CApiContext.asHex(CApiContext.asPointer(ptr, InteropLibrary.getUncached()))));
            return 0;
        }
    }

    @GenerateInline
    @GenerateCached(false)
    @ImportStatic(CExtContext.class)
    abstract static class NewClassMethodNode extends Node {

        abstract Object execute(Node inliningTarget, Object methodDefPtr, TruffleString name, Object methObj, Object flags, Object wrapper, Object type, Object doc);

        @Specialization(guards = "isClassOrStaticMethod(flags)")
        static Object classOrStatic(Node inliningTarget, Object methodDefPtr, TruffleString name, Object methObj, int flags, int wrapper, Object type, Object doc,
                        @Bind PythonLanguage language,
                        @Exclusive @Cached HiddenAttr.WriteNode writeHiddenAttrNode,
                        @Cached(inline = false) WriteAttributeToPythonObjectNode writeAttrNode) {
            PythonAbstractObject func = PExternalFunctionWrapper.createWrapperFunction(name, methObj, type, flags, wrapper, language);
            writeHiddenAttrNode.execute(inliningTarget, func, METHOD_DEF_PTR, methodDefPtr);
            PythonObject function;
            if ((flags & METH_CLASS) != 0) {
                function = PFactory.createClassmethodFromCallableObj(language, func);
            } else {
                function = PFactory.createStaticmethodFromCallableObj(language, func);
            }
            writeAttrNode.execute(function, T___NAME__, name);
            writeAttrNode.execute(function, T___DOC__, doc);
            return function;
        }

        @Specialization(guards = "!isClassOrStaticMethod(flags)")
        static Object doNativeCallable(Node inliningTarget, Object methodDefPtr, TruffleString name, Object methObj, int flags, int wrapper, Object type, Object doc,
                        @Bind PythonLanguage language,
                        @Cached PyObjectSetAttrNode setattr,
                        @Exclusive @Cached HiddenAttr.WriteNode writeNode) {
            PythonAbstractObject func = PExternalFunctionWrapper.createWrapperFunction(name, methObj, type, flags, wrapper, language);
            setattr.execute(inliningTarget, func, T___NAME__, name);
            setattr.execute(inliningTarget, func, T___DOC__, doc);
            writeNode.execute(inliningTarget, func, METHOD_DEF_PTR, methodDefPtr);
            return func;
        }
    }

    @CApiBuiltin(ret = Int, args = {Pointer, PyTypeObject, PyObject, ConstCharPtrAsTruffleString, Pointer, Int, Int, ConstCharPtrAsTruffleString}, call = Ignored)
    abstract static class GraalPyPrivate_Type_AddFunctionToType extends CApi8BuiltinNode {

        @Specialization
        static int classMethod(Object methodDefPtr, Object type, Object dict, TruffleString name, Object cfunc, int flags, int wrapper, Object doc,
                        @Bind Node inliningTarget,
                        @Cached NewClassMethodNode newClassMethodNode,
                        @Cached PyDictSetDefault setDefault) {
            Object func = newClassMethodNode.execute(inliningTarget, methodDefPtr, name, cfunc, flags, wrapper, type, doc);
            setDefault.execute(null, inliningTarget, dict, name, func);
            return 0;
        }
    }

    @CApiBuiltin(ret = Int, args = {PyTypeObject}, call = Ignored)
    abstract static class GraalPyPrivate_Type_AddOperators extends CApiUnaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        static int addOperators(PythonAbstractNativeObject type) {
            TpSlots.addOperatorsToNative(type);
            return 0;
        }
    }

    @CApiBuiltin(ret = Int, args = {PyTypeObject, PyObject, ConstCharPtrAsTruffleString, Int, Py_ssize_t, Int, ConstCharPtrAsTruffleString}, call = CApiCallPath.Ignored)
    public abstract static class GraalPyPrivate_Type_AddMember extends CApi7BuiltinNode {

        @Specialization
        @TruffleBoundary
        public static int addMember(Object clazz, PDict tpDict, TruffleString memberName, int memberType, long offset, int canSet, Object memberDoc) {
            // note: 'doc' may be NULL; in this case, we would store 'None'
            PythonLanguage language = PythonLanguage.get(null);
            PBuiltinFunction getterObject = ReadMemberNode.createBuiltinFunction(language, clazz, memberName, memberType, (int) offset);

            Object setterObject = null;
            if (canSet != 0) {
                setterObject = WriteMemberNode.createBuiltinFunction(language, clazz, memberName, memberType, (int) offset);
            }

            // create member descriptor
            GetSetDescriptor memberDescriptor = PFactory.createMemberDescriptor(language, getterObject, setterObject, memberName, clazz);
            WriteAttributeToPythonObjectNode.getUncached().execute(memberDescriptor, SpecialAttributeNames.T___DOC__, memberDoc);

            // add member descriptor to tp_dict
            PyDictSetDefault.executeUncached(tpDict, memberName, memberDescriptor);
            return 0;
        }
    }

    @GenerateInline
    @GenerateCached(false)
    abstract static class CreateGetSetNode extends Node {

        abstract GetSetDescriptor execute(Node inliningTarget, TruffleString name, Object cls, Object getter, Object setter, Object doc, Object closure);

        @Specialization
        @TruffleBoundary
        static GetSetDescriptor createGetSet(Node inliningTarget, TruffleString name, Object cls, Object getter, Object setter, Object doc, Object closure,
                        @CachedLibrary(limit = "2") InteropLibrary interopLibrary) {
            assert !(doc instanceof CArrayWrapper);
            // note: 'doc' may be NULL; in this case, we would store 'None'
            PBuiltinFunction get = null;
            PythonLanguage language = PythonLanguage.get(inliningTarget);
            if (!interopLibrary.isNull(getter)) {
                RootCallTarget getterCT = getterCallTarget(name, language);
                getter = EnsureExecutableNode.executeUncached(getter, PExternalFunctionWrapper.GETTER);
                get = PFactory.createBuiltinFunction(language, name, cls, EMPTY_OBJECT_ARRAY, ExternalFunctionNodes.createKwDefaults(getter, closure), 0, getterCT);
            }

            PBuiltinFunction set = null;
            boolean hasSetter = !interopLibrary.isNull(setter);
            if (hasSetter) {
                RootCallTarget setterCT = setterCallTarget(name, language);
                setter = EnsureExecutableNode.executeUncached(setter, PExternalFunctionWrapper.SETTER);
                set = PFactory.createBuiltinFunction(language, name, cls, EMPTY_OBJECT_ARRAY, ExternalFunctionNodes.createKwDefaults(setter, closure), 0, setterCT);
            }

            // create get-set descriptor
            GetSetDescriptor descriptor = PFactory.createGetSetDescriptor(language, get, set, name, cls, hasSetter);
            WriteAttributeToPythonObjectNode.executeUncached(descriptor, T___DOC__, doc);
            return descriptor;
        }

        @TruffleBoundary
        private static RootCallTarget getterCallTarget(TruffleString name, PythonLanguage lang) {
            Function<PythonLanguage, RootNode> rootNodeFunction = l -> new GetterRoot(l, name, PExternalFunctionWrapper.GETTER);
            return lang.createCachedExternalFunWrapperCallTarget(rootNodeFunction, GetterRoot.class, PExternalFunctionWrapper.GETTER, name, true, false);
        }

        @TruffleBoundary
        private static RootCallTarget setterCallTarget(TruffleString name, PythonLanguage lang) {
            Function<PythonLanguage, RootNode> rootNodeFunction = l -> new SetterRoot(l, name, PExternalFunctionWrapper.SETTER);
            return lang.createCachedExternalFunWrapperCallTarget(rootNodeFunction, SetterRoot.class, PExternalFunctionWrapper.SETTER, name, true, false);
        }
    }

    @CApiBuiltin(ret = Int, args = {PyTypeObject, PyObject, ConstCharPtrAsTruffleString, Pointer, Pointer, ConstCharPtrAsTruffleString, Pointer}, call = Ignored)
    abstract static class GraalPyPrivate_Type_AddGetSet extends CApi7BuiltinNode {

        @Specialization
        static int doGeneric(Object cls, PDict dict, TruffleString name, Object getter, Object setter, Object doc, Object closure,
                        @Bind Node inliningTarget,
                        @Cached CreateGetSetNode createGetSetNode,
                        @Cached PyDictSetDefault setDefault) {
            GetSetDescriptor descr = createGetSetNode.execute(inliningTarget, name, cls, getter, setter, doc, closure);
            setDefault.execute(null, inliningTarget, dict, name, descr);
            return 0;
        }
    }

    @CApiBuiltin(ret = ArgDescriptor.Void, args = {PyTypeObject, PyBufferProcs}, call = Ignored)
    abstract static class GraalPyPrivate_Type_SetBufferProcs extends CApiBinaryBuiltinNode {

        @Specialization
        static Object setBuiltinClassType(PythonBuiltinClassType clazz, Object bufferProcs,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached HiddenAttr.WriteNode writeAttrNode) {
            writeAttrNode.execute(inliningTarget, PythonContext.get(inliningTarget).lookupType(clazz), AS_BUFFER, bufferProcs);
            return PNone.NO_VALUE;
        }

        @Specialization(guards = "isPythonClass(object)")
        static Object set(PythonAbstractObject object, Object bufferProcs,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached HiddenAttr.WriteNode writeAttrNode) {
            writeAttrNode.execute(inliningTarget, object, AS_BUFFER, bufferProcs);
            return PNone.NO_VALUE;
        }
    }
}
