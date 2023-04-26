/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
// skip GIL
package com.oracle.graal.python.builtins.objects.cext.capi;

import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___MODULE__;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyMemoryViewObject__exports;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyMemoryViewObject__flags;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyObject__ob_refcnt;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyObject__ob_type;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_alloc;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_as_buffer;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_dealloc;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_del;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_free;
import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyTypeObject__tp_vectorcall_offset;
import static com.oracle.graal.python.builtins.objects.cext.structs.CStructs.PyMemoryViewObject;
import static com.oracle.graal.python.builtins.objects.type.TypeBuiltins.TYPE_ALLOC;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DICTOFFSET__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___WEAKLISTOFFSET__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___NEW__;
import static com.oracle.graal.python.nodes.object.BuiltinClassProfiles.InlineIsBuiltinClassProfile.profileClassSlowPath;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.ctypes.StgDictObject;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject.PInteropGetAttributeNode;
import com.oracle.graal.python.builtins.objects.PythonAbstractObjectFactory.PInteropGetAttributeNodeGen;
import com.oracle.graal.python.builtins.objects.bytes.PBytesLike;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.AllToJavaNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.MaterializeDelegateNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ToSulongNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.TransformExceptionToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.LookupNativeI64MemberInMRONodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodesFactory.LookupNativeMemberInMRONodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.NativeToPythonNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNewRefNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitionsFactory.PythonToNativeNewRefNodeGen;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitionsFactory.PythonToNativeNodeGen;
import com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers.CStringWrapper;
import com.oracle.graal.python.builtins.objects.cext.common.CExtContext;
import com.oracle.graal.python.builtins.objects.cext.structs.CFields;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccessFactory;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructs;
import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageAddAllToOther;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.memoryview.PMemoryView;
import com.oracle.graal.python.builtins.objects.method.PDecoratedMethod;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.builtins.objects.type.TypeBuiltins;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetMroStorageNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetTypeFlagsNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetBaseClassesNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetBasicSizeNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetDictOffsetNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetItemSizeNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetSuperClassNodeGen;
import com.oracle.graal.python.builtins.objects.type.TypeNodesFactory.GetTypeFlagsNodeGen;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNodeGen;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONodeGen;
import com.oracle.graal.python.nodes.attributes.LookupNativeSlotNode;
import com.oracle.graal.python.nodes.attributes.LookupNativeSlotNodeGen.LookupNativeGetattroSlotNodeGen;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.builtins.FunctionNodes.GetFunctionCodeNode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.InlineIsBuiltinClassProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetOrCreateDictNode;
import com.oracle.graal.python.nodes.object.InlinedGetClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.NativeSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.HiddenKey;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.utilities.TriState;

@ExportLibrary(InteropLibrary.class)
public abstract class DynamicObjectNativeWrapper extends PythonNativeWrapper {
    static final String J_GP_OBJECT = "gp_object";
    static final TruffleString T_GP_OBJECT = tsLiteral(J_GP_OBJECT);
    static final TruffleString T_VALUE = tsLiteral("value");
    private DynamicObjectStorage nativeMemberStore;

    public DynamicObjectNativeWrapper() {
    }

    public DynamicObjectNativeWrapper(Object delegate) {
        super(delegate);
    }

    public DynamicObjectStorage createNativeMemberStore(PythonLanguage lang) {
        if (nativeMemberStore == null) {
            nativeMemberStore = new DynamicObjectStorage(lang);
        }
        return nativeMemberStore;
    }

    public DynamicObjectStorage getNativeMemberStore() {
        return nativeMemberStore;
    }

    @ExportMessage
    protected boolean isNull() {
        return getDelegate() == PNone.NO_VALUE;
    }

    @ExportMessage
    protected boolean isExecutable() {
        return true;
    }

    @ExportMessage
    protected Object execute(Object[] arguments,
                    @Cached PythonAbstractObject.PExecuteNode executeNode,
                    @Cached AllToJavaNode allToJavaNode,
                    @Cached NativeToPythonNode selfToJava,
                    @Cached PythonToNativeNewRefNode toNewRefNode,
                    @Cached TransformExceptionToNativeNode transformExceptionToNativeNode,
                    @Exclusive @Cached GilNode gil) throws UnsupportedMessageException {
        boolean mustRelease = gil.acquire();
        try {
            Object[] converted;
            Object function = getDelegate();
            if (function instanceof PBuiltinFunction && CExtContext.isMethNoArgs(((PBuiltinFunction) function).getFlags()) && arguments.length == 2) {
                /*
                 * The C function signature for METH_NOARGS is: methNoArgs(PyObject* self, PyObject*
                 * dummy); So we need to trim away the dummy argument, otherwise we will get an
                 * error.
                 */
                converted = new Object[]{selfToJava.execute(arguments[0])};
            } else if (function instanceof PBuiltinFunction && CExtContext.isMethVarargs(((PBuiltinFunction) function).getFlags()) && arguments.length == 2) {
                converted = allToJavaNode.execute(arguments);
                assert converted[1] instanceof PTuple;
                SequenceStorage argsStorage = ((PTuple) converted[1]).getSequenceStorage();
                Object[] wrapArgs = new Object[argsStorage.length() + 1];
                wrapArgs[0] = converted[0];
                PythonUtils.arraycopy(argsStorage.getInternalArray(), 0, wrapArgs, 1, argsStorage.length());
                converted = wrapArgs;
            } else {
                converted = allToJavaNode.execute(arguments);
            }

            Object result = executeNode.execute(function, converted);

            /*
             * If a native wrapper is executed, we directly wrap some managed function and assume
             * that new references are returned. So, we increase the ref count for each native
             * object here.
             */
            return toNewRefNode.execute(result);
        } catch (PException e) {
            transformExceptionToNativeNode.execute(e);
            return toNewRefNode.execute(PythonContext.get(gil).getNativeNull());
        } finally {
            gil.release(mustRelease);
        }
    }

    @GenerateUncached
    abstract static class IntArrayToNativePySSizeArray extends Node {
        public abstract Object execute(int[] array);

        @Specialization
        static Object getShape(int[] intArray,
                        @Cached CStructAccess.AllocateNode alloc,
                        @Cached CStructAccess.WriteLongNode write) {
            Object mem = alloc.alloc(intArray.length * Long.BYTES);
            for (int i = 0; i < intArray.length; i++) {
                write.write(mem, intArray[i]);
            }
            return mem;
        }
    }

    // TO NATIVE, IS POINTER, AS POINTER
    @GenerateUncached
    abstract static class ToNativeNode extends Node {
        public abstract void execute(PythonNativeWrapper obj);

        static boolean isMemoryView(PythonNativeWrapper w) {
            return w.getDelegate() instanceof PMemoryView;
        }

        @Specialization(guards = "!isMemoryView(obj)")
        static void doPythonNativeWrapper(PythonNativeWrapper obj) {
            if (!obj.isNative()) {
                CApiTransitions.firstToNative(obj);
            }
        }

        @TruffleBoundary
        @Specialization(guards = "isMemoryView(obj)")
        void doPythonNativeWrapper(PythonNativeWrapper obj,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedGetClassNode getClass,
                        @Cached PythonToNativeNewRefNode toNative,
                        @Cached CStructAccess.AllocateNode allocNode,
                        @Cached CStructAccess.GetElementPtrNode getElementNode,
                        @Cached CStructAccess.WritePointerNode writePointerNode,
                        @Cached CStructAccess.WriteLongNode writeI64Node,
                        @Cached CStructAccess.WriteIntNode writeI32Node,
                        @Cached SequenceNodes.GetSequenceStorageNode getStorage,
                        @Cached SequenceNodes.SetSequenceStorageNode setStorage,
                        @Cached CExtNodes.PointerAddNode pointerAddNode,
                        @Cached PySequenceArrayWrapper.ToNativeStorageNode toNativeStorageNode,
                        @Cached IntArrayToNativePySSizeArray intArrayToNativePySSizeArray,
                        @Cached CExtNodes.AsCharPointerNode asCharPointerNode,
                        @CachedLibrary(limit = "3") InteropLibrary lib) {
            if (!obj.isNative()) {
                PMemoryView object = (PMemoryView) obj.getDelegate();
                obj.setRefCount(Long.MAX_VALUE / 2); // make this object immortal

                Object mem = allocNode.alloc(PyMemoryViewObject);
                writeI64Node.write(mem, PyObject__ob_refcnt, 0x1000); // TODO: immortal for now
                writePointerNode.write(mem, PyObject__ob_type, toNative.execute(getClass.execute(inliningTarget, object)));
                writeI32Node.write(mem, PyMemoryViewObject__flags, object.getFlags());
                writeI64Node.write(mem, PyMemoryViewObject__exports, object.getExports().get());
                // TODO: ignoring mbuf, hash and weakreflist for now

                Object view = getElementNode.getElementPtr(mem, CFields.PyMemoryViewObject__view);

                Object buf;
                if (object.getBufferPointer() == null) {
                    // TODO GR-21120: Add support for PArray
                    PSequence owner = (PSequence) object.getOwner();
                    NativeSequenceStorage nativeStorage = toNativeStorageNode.execute(getStorage.execute(owner), owner instanceof PBytesLike);
                    if (nativeStorage == null) {
                        throw CompilerDirectives.shouldNotReachHere("cannot allocate native storage");
                    }
                    setStorage.execute(inliningTarget, owner, nativeStorage);
                    Object pointer = nativeStorage.getPtr();
                    if (object.getOffset() == 0) {
                        buf = pointer;
                    } else {
                        buf = pointerAddNode.execute(pointer, object.getOffset());
                    }
                } else {
                    if (object.getOffset() == 0) {
                        buf = object.getBufferPointer();
                    } else {
                        buf = pointerAddNode.execute(object.getBufferPointer(), object.getOffset());
                    }
                }
                writePointerNode.write(view, CFields.Py_buffer__buf, buf);

                if (object.getOwner() != null) {
                    writePointerNode.write(view, CFields.Py_buffer__obj, toNative.execute(object.getOwner()));
                }
                writeI64Node.write(view, CFields.Py_buffer__len, object.getLength());
                writeI64Node.write(view, CFields.Py_buffer__itemsize, object.getItemSize());
                writeI32Node.write(view, CFields.Py_buffer__readonly, PInt.intValue(object.isReadOnly()));
                writeI32Node.write(view, CFields.Py_buffer__ndim, object.getDimensions());
                if (object.getFormatString() != null) {
                    writePointerNode.write(view, CFields.Py_buffer__format, asCharPointerNode.execute(object.getFormatString()));
                }
                if (object.getBufferShape() != null) {
                    writePointerNode.write(view, CFields.Py_buffer__shape, intArrayToNativePySSizeArray.execute(object.getBufferShape()));
                }
                if (object.getBufferStrides() != null) {
                    writePointerNode.write(view, CFields.Py_buffer__strides, intArrayToNativePySSizeArray.execute(object.getBufferStrides()));
                }
                if (object.getBufferSuboffsets() != null) {
                    writePointerNode.write(view, CFields.Py_buffer__suboffsets, intArrayToNativePySSizeArray.execute(object.getBufferSuboffsets()));
                }

                long ptr = coerceToLong(mem, lib);
                CApiTransitions.firstToNative(obj, ptr);
            }
        }
    }

    @GenerateUncached
    abstract static class ToNativeTypeNode extends Node {
        public abstract void execute(PythonNativeWrapper obj);

        static Object allocateType() {
            return CStructAccessFactory.AllocateNodeGen.getUncached().alloc(CStructs.PyTypeObject);
        }

        static Object lookup(PythonManagedClass clazz, CFields member, HiddenKey hiddenName) {
            Object result = LookupNativeMemberInMRONodeGen.getUncached().execute(clazz, member, hiddenName);
            if (result == PNone.NO_VALUE) {
                return PythonContext.get(null).getNativeNull().getPtr();
            }
            return result;
        }

        static long lookupSize(PythonManagedClass clazz, CFields member, HiddenKey hiddenName) {
            return LookupNativeI64MemberInMRONodeGen.getUncached().execute(clazz, member, hiddenName);
        }

        static Object lookup(PythonManagedClass obj, SlotMethodDef slot) {
            return LookupNativeSlotNode.executeUncached(obj, slot);
        }

        static boolean hasSlot(PythonManagedClass clazz, SlotMethodDef slot) {
            return LookupNativeSlotNode.executeUncached(clazz, slot) != PythonContext.get(null).getNativeNull().getPtr();
        }

        private static long getBasicSizeAttribute(PythonManagedClass clazz, Object name) {
            PyNumberAsSizeNode asSizeNode = PyNumberAsSizeNodeGen.getUncached();
            Object val = ReadAttributeFromObjectNode.getUncached().execute(clazz, name);
            return val != PNone.NO_VALUE ? asSizeNode.executeExact(null, val) : CStructs.PyObject.size();
        }

        private static long getSizeAttribute(PythonManagedClass clazz, TruffleString name) {
            PyNumberAsSizeNode asSizeNode = PyNumberAsSizeNodeGen.getUncached();
            PInteropGetAttributeNode getAttrNode = PInteropGetAttributeNodeGen.getUncached();

            Object val = getAttrNode.execute(clazz, name);
            // If the attribute does not exist, this means that we take, e.g., 'tp_itemsize' from
            // the base object which is by default 0 (see typeobject.c:PyBaseObject_Type).
            return val != PNone.NO_VALUE ? asSizeNode.executeExact(null, val) : 0L;
        }

        static void populateType(PythonNativeWrapper obj, PythonManagedClass clazz, Object mem, boolean isType) {
            CompilerAsserts.neverPartOfCompilation();
            ToSulongNode toSulong = ToSulongNode.getUncached();
            PythonToNativeNode toNative = PythonToNativeNodeGen.getUncached();
            PythonToNativeNewRefNode toNativeNewRef = PythonToNativeNewRefNodeGen.getUncached();
            IsBuiltinClassProfile isBuiltin = IsBuiltinClassProfile.getUncached();
            LookupAttributeInMRONode.Dynamic lookupAttrNode = LookupAttributeInMRONodeGen.DynamicNodeGen.getUncached();
            PyNumberAsSizeNode asSizeNode = PyNumberAsSizeNodeGen.getUncached();
            PInteropGetAttributeNode getAttrNode = PInteropGetAttributeNodeGen.getUncached();
            CastToTruffleStringNode castToStringNode = CastToTruffleStringNode.getUncached();
            CStructAccess.WritePointerNode writePtrNode = CStructAccessFactory.WritePointerNodeGen.getUncached();
            CStructAccess.WriteLongNode writeI64Node = CStructAccessFactory.WriteLongNodeGen.getUncached();
            CStructAccess.WriteIntNode writeI32Node = CStructAccessFactory.WriteIntNodeGen.getUncached();
            GetTypeFlagsNode getTypeFlagsNode = GetTypeFlagsNodeGen.getUncached();
            PyMappingMethodsWrapper.AllocateNode allocMapping = PyMappingMethodsWrapperFactory.AllocateNodeGen.getUncached();
            PySequenceMethodsWrapper.AllocateNode allocSequence = PySequenceMethodsWrapperFactory.AllocateNodeGen.getUncached();
            PyNumberMethodsWrapper.AllocateNode allocNumber = PyNumberMethodsWrapperFactory.AllocateNodeGen.getUncached();

            GetMroStorageNode getMroStorageNode = GetMroStorageNode.getUncached();
            PythonObjectFactory factory = PythonObjectFactory.getUncached();
            Object nullValue = PythonContext.get(null).getNativeNull().getPtr();

            writeI64Node.write(mem, PyObject__ob_refcnt, obj.getRefCount());
            if (isType) {
                // self-reference
                writePtrNode.write(mem, PyObject__ob_type, mem);
            } else {
                writePtrNode.write(mem, PyObject__ob_type, toSulong.execute(GetClassNode.getUncached().execute(clazz)));
            }

            Object superClass = GetSuperClassNodeGen.getUncached().execute(clazz);
            if (superClass == null) {
                superClass = PythonContext.get(null).getNativeNull();
            } else if (superClass instanceof PythonBuiltinClassType builtinClass) {
                superClass = PythonContext.get(null).lookupType(builtinClass);
            }

            writeI64Node.write(mem, CFields.PyVarObject__ob_size, 0L);

            writePtrNode.write(mem, CFields.PyTypeObject__tp_name, clazz.getClassNativeWrapper().getNameWrapper());
            writeI64Node.write(mem, CFields.PyTypeObject__tp_basicsize, GetBasicSizeNodeGen.getUncached().execute(null, clazz));
            writeI64Node.write(mem, CFields.PyTypeObject__tp_itemsize, GetItemSizeNodeGen.getUncached().execute(null, clazz));
            writePtrNode.write(mem, CFields.PyTypeObject__tp_dealloc, lookup(clazz, PyTypeObject__tp_dealloc, TypeBuiltins.TYPE_DEALLOC));
            writeI64Node.write(mem, CFields.PyTypeObject__tp_vectorcall_offset, lookupSize(clazz, PyTypeObject__tp_vectorcall_offset, TypeBuiltins.TYPE_VECTORCALL_OFFSET));
            writePtrNode.write(mem, CFields.PyTypeObject__tp_getattr, nullValue);
            writePtrNode.write(mem, CFields.PyTypeObject__tp_setattr, nullValue);
            writePtrNode.write(mem, CFields.PyTypeObject__tp_as_async, nullValue);
            writePtrNode.write(mem, CFields.PyTypeObject__tp_repr, lookup(clazz, SlotMethodDef.TP_REPR));
            writePtrNode.write(mem, CFields.PyTypeObject__tp_as_number, isBuiltin.profileClass(clazz, PythonBuiltinClassType.PythonObject) ? nullValue : allocNumber.execute(clazz));
            writePtrNode.write(mem, CFields.PyTypeObject__tp_as_sequence, hasSlot(clazz, SlotMethodDef.SQ_LENGTH) ? allocSequence.execute(clazz) : nullValue);
            writePtrNode.write(mem, CFields.PyTypeObject__tp_as_mapping, hasSlot(clazz, SlotMethodDef.MP_LENGTH) ? allocMapping.execute(clazz) : nullValue);
            writePtrNode.write(mem, CFields.PyTypeObject__tp_hash, lookup(clazz, SlotMethodDef.TP_HASH));
            writePtrNode.write(mem, CFields.PyTypeObject__tp_call, lookup(clazz, SlotMethodDef.TP_CALL));
            writePtrNode.write(mem, CFields.PyTypeObject__tp_str, lookup(clazz, SlotMethodDef.TP_STR));
            writePtrNode.write(mem, CFields.PyTypeObject__tp_getattro, LookupNativeGetattroSlotNodeGen.getUncached().execute(clazz));
            writePtrNode.write(mem, CFields.PyTypeObject__tp_setattro, lookup(clazz, SlotMethodDef.TP_SETATTRO));
            writePtrNode.write(mem, CFields.PyTypeObject__tp_as_buffer, lookup(clazz, PyTypeObject__tp_as_buffer, TypeBuiltins.TYPE_AS_BUFFER));
            writeI64Node.write(mem, CFields.PyTypeObject__tp_flags, getTypeFlagsNode.execute(clazz));

            // return a C string wrapper that really allocates 'char*' on TO_NATIVE
            Object docObj = getAttrNode.execute(clazz, SpecialAttributeNames.T___DOC__);
            if (docObj instanceof TruffleString) {
                docObj = new CStringWrapper((TruffleString) docObj);
            } else if (docObj instanceof PString) {
                docObj = new CStringWrapper(castToStringNode.execute(docObj));
            }
            writePtrNode.write(mem, CFields.PyTypeObject__tp_doc, toSulong.execute(docObj));
            // TODO: return a proper traverse/clear function, or at least a dummy
            writePtrNode.write(mem, CFields.PyTypeObject__tp_traverse, nullValue);
            writePtrNode.write(mem, CFields.PyTypeObject__tp_clear, nullValue);
            writePtrNode.write(mem, CFields.PyTypeObject__tp_richcompare, lookup(clazz, SlotMethodDef.TP_RICHCOMPARE));
            Object val = lookupAttrNode.execute(clazz, T___WEAKLISTOFFSET__);
            // If the attribute does not exist, this means that we take 'tp_itemsize' from the base
            // object which is by default 0 (see typeobject.c:PyBaseObject_Type).
            writeI64Node.write(mem, CFields.PyTypeObject__tp_weaklistoffset, val == PNone.NO_VALUE ? 0L : asSizeNode.executeExact(null, val));
            writePtrNode.write(mem, CFields.PyTypeObject__tp_iter, lookup(clazz, SlotMethodDef.TP_ITER));
            writePtrNode.write(mem, CFields.PyTypeObject__tp_iternext, lookup(clazz, SlotMethodDef.TP_ITERNEXT));
            writePtrNode.write(mem, CFields.PyTypeObject__tp_methods, nullValue);
            writePtrNode.write(mem, CFields.PyTypeObject__tp_members, nullValue);
            writePtrNode.write(mem, CFields.PyTypeObject__tp_getset, nullValue);
            if (!isType) {
                // "object" base needs to be initialized explicitly in capi.c
                writePtrNode.write(mem, CFields.PyTypeObject__tp_base, toNative.execute(superClass));
            }

            GetOrCreateDictNode getDict = GetOrCreateDictNode.getUncached();
            HashingStorageAddAllToOther addAllToOtherNode = HashingStorageAddAllToOther.getUncached();
            // TODO(fa): we could cache the dict instance on the class' native wrapper
            PDict dict = getDict.execute(clazz);
            if (!(dict instanceof StgDictObject)) {
                HashingStorage dictStorage = dict.getDictStorage();
                if (!(dictStorage instanceof DynamicObjectStorage)) {
                    HashingStorage storage = new DynamicObjectStorage(clazz.getStorage());
                    dict.setDictStorage(storage);
                    if (dictStorage != null) {
                        // copy all mappings to the new storage
                        addAllToOtherNode.execute(null, dictStorage, dict);
                    }
                }
            }
            writePtrNode.write(mem, CFields.PyTypeObject__tp_dict, toNative.execute(dict));
            writePtrNode.write(mem, CFields.PyTypeObject__tp_descr_get, lookup(clazz, SlotMethodDef.TP_DESCR_GET));
            writePtrNode.write(mem, CFields.PyTypeObject__tp_descr_set, lookup(clazz, SlotMethodDef.TP_DESCR_SET));

            // TODO properly implement 'tp_dictoffset' for builtin classes
            writeI64Node.write(mem, CFields.PyTypeObject__tp_dictoffset, GetDictOffsetNodeGen.getUncached().execute(null, clazz));
            writePtrNode.write(mem, CFields.PyTypeObject__tp_init, lookup(clazz, SlotMethodDef.TP_INIT));
            writePtrNode.write(mem, CFields.PyTypeObject__tp_alloc, lookup(clazz, PyTypeObject__tp_alloc, TYPE_ALLOC));
            // T___new__ is magically a staticmethod for Python types. The tp_new slot lookup
            // expects to get the function
            Object newFunction = getAttrNode.execute(clazz, T___NEW__);
            if (newFunction instanceof PDecoratedMethod) {
                newFunction = ((PDecoratedMethod) newFunction).getCallable();
            }
            writePtrNode.write(mem, CFields.PyTypeObject__tp_new, ManagedMethodWrappers.createKeywords(newFunction));
            writePtrNode.write(mem, CFields.PyTypeObject__tp_free, lookup(clazz, PyTypeObject__tp_free, TypeBuiltins.TYPE_FREE));
            writePtrNode.write(mem, CFields.PyTypeObject__tp_is_gc, nullValue);
            if (clazz.basesTuple == null) {
                clazz.basesTuple = factory.createTuple(GetBaseClassesNodeGen.getUncached().execute(clazz));
            }
            writePtrNode.write(mem, CFields.PyTypeObject__tp_bases, toNative.execute(clazz.basesTuple));
            if (clazz.mroStore == null) {
                clazz.mroStore = factory.createTuple(getMroStorageNode.execute(clazz));
            }
            writePtrNode.write(mem, CFields.PyTypeObject__tp_mro, toNative.execute(clazz.mroStore));
            writePtrNode.write(mem, CFields.PyTypeObject__tp_cache, nullValue);
            writePtrNode.write(mem, CFields.PyTypeObject__tp_subclasses, toNativeNewRef.execute(factory.createDict()));
            writePtrNode.write(mem, CFields.PyTypeObject__tp_weaklist, nullValue);
            writePtrNode.write(mem, CFields.PyTypeObject__tp_del, lookup(clazz, PyTypeObject__tp_del, TypeBuiltins.TYPE_DEL));
            writeI32Node.write(mem, CFields.PyTypeObject__tp_version_tag, 0);
            writePtrNode.write(mem, CFields.PyTypeObject__tp_finalize, nullValue);
            writePtrNode.write(mem, CFields.PyTypeObject__tp_vectorcall, nullValue);
        }

        @Specialization
        @TruffleBoundary
        void doPythonNativeWrapper(DynamicObjectNativeWrapper obj) {
            if (!obj.isNative()) {
                assert !(obj.getDelegate() instanceof PythonBuiltinClass) || IsSubtypeNode.getUncached().execute(obj.getDelegate(), PythonBuiltinClassType.PBaseException);
                Object ptr = allocateType();

                obj.initializeBuiltinType(ptr);
            }
        }

    }

    public void initializeBuiltinType(Object ptr) {
        setRefCount(Long.MAX_VALUE / 2); // make this object immortal
        boolean isType = InlineIsBuiltinClassProfile.profileClassSlowPath(getDelegate(), PythonBuiltinClassType.PythonClass);
        ToNativeTypeNode.populateType(this, (PythonManagedClass) getDelegate(), ptr, isType);
        if (!isNative()) {
            CApiTransitions.firstToNative(this, coerceToLong(ptr, InteropLibrary.getUncached()));
        }
    }

    /**
     * Used to wrap {@link PythonAbstractObject} when used in native code. This wrapper mimics the
     * correct shape of the corresponding native type {@code struct _object}.
     */
    @ExportLibrary(InteropLibrary.class)
    public static class PythonObjectNativeWrapper extends DynamicObjectNativeWrapper {

        public PythonObjectNativeWrapper(PythonAbstractObject object) {
            super(object);
        }

        public static DynamicObjectNativeWrapper wrap(PythonAbstractObject obj, ConditionProfile noWrapperProfile) {
            // important: native wrappers are cached
            DynamicObjectNativeWrapper nativeWrapper = obj.getNativeWrapper();
            if (noWrapperProfile.profile(nativeWrapper == null)) {
                nativeWrapper = new PythonObjectNativeWrapper(obj);
                obj.setNativeWrapper(nativeWrapper);
            }
            return nativeWrapper;
        }

        public static DynamicObjectNativeWrapper wrapNewRef(PythonAbstractObject obj, ConditionProfile noWrapperProfile) {
            // important: native wrappers are cached
            DynamicObjectNativeWrapper nativeWrapper = obj.getNativeWrapper();
            if (noWrapperProfile.profile(nativeWrapper == null)) {
                nativeWrapper = new PythonObjectNativeWrapper(obj);
                obj.setNativeWrapper(nativeWrapper);
            } else {
                // it already existed, so we need to increase the reference count
                CApiTransitions.incRef(nativeWrapper, 1);
            }
            return nativeWrapper;
        }

        @Override
        public String toString() {
            CompilerAsserts.neverPartOfCompilation();
            return PythonUtils.formatJString("PythonObjectNativeWrapper(%s, isNative=%s)", getDelegate(), isNative());
        }
    }

    @ExportLibrary(InteropLibrary.class)
    public static final class PrimitiveNativeWrapper extends DynamicObjectNativeWrapper {

        public static final byte PRIMITIVE_STATE_BOOL = 1;
        public static final byte PRIMITIVE_STATE_BYTE = 1 << 1;
        public static final byte PRIMITIVE_STATE_INT = 1 << 2;
        public static final byte PRIMITIVE_STATE_LONG = 1 << 3;
        public static final byte PRIMITIVE_STATE_DOUBLE = 1 << 4;

        private final byte state;
        private final long value;
        private final double dvalue;

        private PrimitiveNativeWrapper(byte state, long value) {
            assert state != PRIMITIVE_STATE_DOUBLE;
            this.state = state;
            this.value = value;
            this.dvalue = 0.0;
        }

        private PrimitiveNativeWrapper(double dvalue) {
            this.state = PRIMITIVE_STATE_DOUBLE;
            this.value = 0;
            this.dvalue = dvalue;
        }

        public byte getState() {
            return state;
        }

        public boolean getBool() {
            return value != 0;
        }

        public byte getByte() {
            return (byte) value;
        }

        public int getInt() {
            return (int) value;
        }

        public long getLong() {
            return value;
        }

        public double getDouble() {
            return dvalue;
        }

        public boolean isBool() {
            return state == PRIMITIVE_STATE_BOOL;
        }

        public boolean isByte() {
            return state == PRIMITIVE_STATE_BYTE;
        }

        public boolean isInt() {
            return state == PRIMITIVE_STATE_INT;
        }

        public boolean isLong() {
            return state == PRIMITIVE_STATE_LONG;
        }

        public boolean isDouble() {
            return state == PRIMITIVE_STATE_DOUBLE;
        }

        public boolean isIntLike() {
            return (state & (PRIMITIVE_STATE_BYTE | PRIMITIVE_STATE_INT | PRIMITIVE_STATE_LONG)) != 0;
        }

        public boolean isSubtypeOfInt() {
            return !isDouble();
        }

        // this method exists just for readability
        public Object getMaterializedObject() {
            return getDelegate();
        }

        // this method exists just for readability
        public void setMaterializedObject(Object materializedPrimitive) {
            setDelegate(materializedPrimitive);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }

            CompilerAsserts.neverPartOfCompilation();

            PrimitiveNativeWrapper other = (PrimitiveNativeWrapper) obj;
            if (other.state == state && other.value == value && other.dvalue == dvalue) {
                // n.b.: in the equals, we also require the native pointer to be the same. The
                // reason for this is to avoid native pointer sharing. Handles are shared if the
                // objects are equal but in this case we must not share because otherwise we would
                // mess up the reference counts.
                return getNativePointer() == other.getNativePointer();
            }
            return false;
        }

        @Override
        public int hashCode() {
            return (Long.hashCode(value) ^ Long.hashCode(Double.doubleToRawLongBits(dvalue)) ^ state);
        }

        @Override
        public String toString() {
            String typeName;
            if (isIntLike()) {
                typeName = "int";
            } else if (isDouble()) {
                typeName = "float";
            } else if (isBool()) {
                typeName = "bool";
            } else {
                typeName = "unknown";
            }
            return "PrimitiveNativeWrapper(" + typeName + "(" + value + ")" + ')';
        }

        public static PrimitiveNativeWrapper createBool(boolean val) {
            return new PrimitiveNativeWrapper(PRIMITIVE_STATE_BOOL, PInt.intValue(val));
        }

        public static PrimitiveNativeWrapper createByte(byte val) {
            return new PrimitiveNativeWrapper(PRIMITIVE_STATE_BYTE, val);
        }

        public static PrimitiveNativeWrapper createInt(int val) {
            return new PrimitiveNativeWrapper(PRIMITIVE_STATE_INT, val);
        }

        public static PrimitiveNativeWrapper createLong(long val) {
            return new PrimitiveNativeWrapper(PRIMITIVE_STATE_LONG, val);
        }

        public static PrimitiveNativeWrapper createDouble(double val) {
            return new PrimitiveNativeWrapper(val);
        }

        @ExportMessage
        @TruffleBoundary
        int identityHashCode() {
            int val = Byte.hashCode(state) ^ Long.hashCode(value);
            if (Double.isNaN(dvalue)) {
                return val;
            } else {
                return val ^ Double.hashCode(dvalue);
            }
        }

        @ExportMessage
        TriState isIdenticalOrUndefined(Object obj) {
            if (obj instanceof PrimitiveNativeWrapper) {
                /*
                 * This basically emulates singletons for boxed values. However, we need to do so to
                 * preserve the invariant that storing an object into a list and getting it out (in
                 * the same critical region) returns the same object.
                 */
                PrimitiveNativeWrapper other = (PrimitiveNativeWrapper) obj;
                if (other.state == state && other.value == value && (other.dvalue == dvalue || Double.isNaN(dvalue) && Double.isNaN(other.dvalue))) {
                    /*
                     * n.b.: in the equals, we also require the native pointer to be the same. The
                     * reason for this is to avoid native pointer sharing. Handles are shared if the
                     * objects are equal but in this case we must not share because otherwise we
                     * would mess up the reference counts.
                     */
                    return TriState.valueOf(this.getNativePointer() == other.getNativePointer());
                }
                return TriState.FALSE;
            } else {
                return TriState.UNDEFINED;
            }
        }

        @ExportMessage
        abstract static class AsPointer {

            @Specialization(guards = {"obj.isBool()", "!obj.isNative()"})
            static long doBoolNotNative(PrimitiveNativeWrapper obj,
                            @Cached MaterializeDelegateNode materializeNode) {
                // special case for True and False singletons
                PInt boxed = (PInt) materializeNode.execute(obj);
                assert obj.getNativePointer() == boxed.getNativeWrapper().getNativePointer();
                return obj.getNativePointer();
            }

            @Specialization(guards = {"!obj.isBool() || obj.isNative()"})
            static long doBoolNative(PrimitiveNativeWrapper obj) {
                return obj.getNativePointer();
            }
        }
    }

    @ExportMessage
    protected boolean isPointer() {
        return getDelegate() == PNone.NO_VALUE || isNative();
    }

    @ExportMessage
    protected long asPointer() {
        return getDelegate() == PNone.NO_VALUE ? 0L : getNativePointer();
    }

    @ExportMessage
    protected void toNative(
                    @Cached ToNativeNode toNativeNode) {
        if (getDelegate() != PNone.NO_VALUE) {
            toNativeNode.execute(this);
        }
    }
}
