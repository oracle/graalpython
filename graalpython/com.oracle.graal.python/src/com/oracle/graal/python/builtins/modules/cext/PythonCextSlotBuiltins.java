/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiCallPath.Ignored;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.TP_ALLOC;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.TP_DEALLOC;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.TP_FREE;
import static com.oracle.graal.python.builtins.objects.cext.capi.NativeMember.TP_VECTORCALL_OFFSET;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.CHAR_PTR;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.ConstCharPtrAsTruffleString;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Int;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Pointer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyASCIIObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyAsyncMethods;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyBufferProcs;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyByteArrayObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyCFunctionObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyCMethodObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyCompactUnicodeObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyDescrObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyFrameObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyGetSetDef;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyInstanceMethodObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyListObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyLongObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyMappingMethods;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyMemberDef;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyMethodDef;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyMethodDescrObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyMethodObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyModuleDef;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyModuleObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyNumberMethods;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectBorrowed;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectPtr;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectWrapper;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PySequenceMethods;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PySetObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PySliceObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyThreadState;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyTupleObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyTypeObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyUnicodeObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyVarObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Py_ssize_t;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.UNSIGNED_INT;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.UNSIGNED_LONG;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Void;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.WCHAR_T_PTR;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.allocfunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.binaryfunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.descrgetfunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.descrsetfunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.destructor;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.freefunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.getattrfunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.getattrofunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.getbufferproc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.getiterfunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.getter;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.hashfunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.initproc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.inquiry;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.iternextfunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.lenfunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.mmap_object;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.newfunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.objobjargproc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.objobjproc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.releasebufferproc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.reprfunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.richcmpfunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.setattrfunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.setattrofunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.setter;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.ssizeargfunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.ssizeobjargproc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.ternaryfunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.traverseproc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.unaryfunc;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.vectorcallfunc;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___BASICSIZE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DICTOFFSET__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DOC__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___ITEMSIZE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___MODULE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___NAME__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___WEAKLISTOFFSET__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T_RICHCMP;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ABS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___AND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___BOOL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___CALL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___DIVMOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___FLOAT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___FLOORDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GETATTRIBUTE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___GET__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___HASH__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IADD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IAND__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IFLOORDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ILSHIFT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IMOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IMUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___INDEX__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___INT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___INVERT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IOR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IPOW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IRSHIFT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ISUB__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ITRUEDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___IXOR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___LEN__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___LSHIFT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___MOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___MUL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___NEG__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___NEW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___NEXT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___OR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___POS__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___POW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___RSHIFT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___SETATTR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___SETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___SET__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___STR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___SUB__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___TRUEDIV__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___XOR__;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBinaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiUnaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.ctypes.StgDictObject;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject.PInteropGetAttributeNode;
import com.oracle.graal.python.builtins.objects.bytes.PByteArray;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.cext.capi.CApiContext;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.AsCharPointerNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.LookupNativeMemberInMRONode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.ObSizeNode;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.PCallCapiFunction;
import com.oracle.graal.python.builtins.objects.cext.capi.ExternalFunctionNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.ManagedMethodWrappers;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeMember;
import com.oracle.graal.python.builtins.objects.cext.capi.PThreadState;
import com.oracle.graal.python.builtins.objects.cext.capi.PyLongDigitsWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyMappingMethodsWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyMethodDefWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyNumberMethodsWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PyProcsWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PySequenceArrayWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PySequenceMethodsWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.PythonNativeWrapper;
import com.oracle.graal.python.builtins.objects.cext.capi.UnicodeObjectNodes.UnicodeAsWideCharNode;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions;
import com.oracle.graal.python.builtins.objects.cext.common.CArrayWrappers.CStringWrapper;
import com.oracle.graal.python.builtins.objects.cext.common.CExtCommonNodes.SizeofWCharNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtContext;
import com.oracle.graal.python.builtins.objects.common.DynamicObjectStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageAddAllToOther;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageForEach;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageForEachCallback;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetItemWithHash;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorKey;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorKeyHash;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorNext;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorValue;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageLen;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.GetSetDescriptor;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.method.PDecoratedMethod;
import com.oracle.graal.python.builtins.objects.method.PMethod;
import com.oracle.graal.python.builtins.objects.mmap.PMMap;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.set.PBaseSet;
import com.oracle.graal.python.builtins.objects.slice.PSlice;
import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.str.StringNodes.StringLenNode;
import com.oracle.graal.python.builtins.objects.str.StringNodes.StringMaterializeNode;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.builtins.objects.type.TypeBuiltins;
import com.oracle.graal.python.builtins.objects.type.TypeFlags;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetMroStorageNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetSubclassesNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetSuperClassNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetTypeFlagsNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.attributes.LookupCallableSlotInMRONode;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToBuiltinTypeNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.InlineIsBuiltinClassProfile;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.GetDictIfExistsNode;
import com.oracle.graal.python.nodes.object.GetOrCreateDictNode;
import com.oracle.graal.python.nodes.object.InlinedGetClassNode;
import com.oracle.graal.python.nodes.object.SetDictNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.PosixSupportLibrary;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.NativeSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.profiles.ValueProfile;
import com.oracle.truffle.api.strings.TruffleString;

public final class PythonCextSlotBuiltins {

    @CApiBuiltin(name = "Py_get_PyListObject_ob_item", ret = PyObjectPtr, args = {PyListObject}, call = Ignored)
    @CApiBuiltin(name = "Py_get_PyTupleObject_ob_item", ret = PyObjectPtr, args = {PyTupleObject}, call = Ignored)
    public abstract static class Py_get_PSequence_ob_item extends CApiUnaryBuiltinNode {

        @Specialization
        public Object get(PSequence object,
                        @Cached("createClassProfile()") ValueProfile classProfile) {
            SequenceStorage sequenceStorage = classProfile.profile(object.getSequenceStorage());
            if (sequenceStorage instanceof NativeSequenceStorage) {
                return ((NativeSequenceStorage) sequenceStorage).getPtr();
            }
            return new PySequenceArrayWrapper(object, 4);
        }
    }

    @CApiBuiltin(ret = CHAR_PTR, args = {mmap_object}, call = Ignored)
    public abstract static class Py_get_mmap_object_data extends CApiUnaryBuiltinNode {

        @Specialization
        Object get(PMMap object,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            try {
                return posixLib.mmapGetPointer(getPosixSupport(), object.getPosixSupportHandle());
            } catch (PosixSupportLibrary.UnsupportedPosixFeatureException e) {
                return new PySequenceArrayWrapper(object, 1);
            }
        }
    }

    @CApiBuiltin(ret = Py_ssize_t, args = {PyASCIIObject}, call = Ignored)
    public abstract static class Py_get_PyASCIIObject_length extends CApiUnaryBuiltinNode {

        @Specialization
        public long get(Object object,
                        @Cached StringLenNode stringLenNode) {
            return stringLenNode.execute(object);
        }
    }

    @CApiBuiltin(ret = UNSIGNED_INT, args = {PyASCIIObject}, call = Ignored)
    public abstract static class Py_get_PyASCIIObject_state_ascii extends CApiUnaryBuiltinNode {

        @TruffleBoundary
        private static boolean doCheck(TruffleString value, CharsetEncoder asciiEncoder) {
            asciiEncoder.reset();
            return asciiEncoder.canEncode(value.toJavaStringUncached());
        }

        @CompilationFinal private CharsetEncoder asciiEncoder;

        @Specialization
        public int get(PString object,
                        @Cached ConditionProfile storageProfile,
                        @Cached StringMaterializeNode materializeNode) {
            // important: avoid materialization of native sequences
            if (storageProfile.profile(object.isNativeCharSequence())) {
                return object.getNativeCharSequence().isAsciiOnly() ? 1 : 0;
            }

            if (asciiEncoder == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                asciiEncoder = StandardCharsets.US_ASCII.newEncoder();
            }
            return doCheck(materializeNode.execute(object), asciiEncoder) ? 1 : 0;
        }
    }

    @CApiBuiltin(ret = UNSIGNED_INT, args = {PyASCIIObject}, call = Ignored)
    public abstract static class Py_get_PyASCIIObject_state_compact extends CApiUnaryBuiltinNode {

        @Specialization
        public int get(@SuppressWarnings("unused") Object object) {
            return 0;
        }
    }

    @CApiBuiltin(ret = UNSIGNED_INT, args = {PyASCIIObject}, call = Ignored)
    public abstract static class Py_get_PyASCIIObject_state_kind extends CApiUnaryBuiltinNode {

        @Specialization
        public int get(PString object,
                        @Cached ConditionProfile storageProfile,
                        @Cached SizeofWCharNode sizeofWcharNode) {
            // important: avoid materialization of native sequences
            if (storageProfile.profile(object.isNativeCharSequence())) {
                return object.getNativeCharSequence().getElementSize() & 0b111;
            }
            return (int) sizeofWcharNode.execute(PythonContext.get(sizeofWcharNode).getCApiContext()) & 0b111;
        }
    }

    @CApiBuiltin(ret = UNSIGNED_INT, args = {PyASCIIObject}, call = Ignored)
    public abstract static class Py_get_PyASCIIObject_state_ready extends CApiUnaryBuiltinNode {

        @Specialization
        public int get(@SuppressWarnings("unused") Object object) {
            return 1;
        }
    }

    @CApiBuiltin(ret = WCHAR_T_PTR, args = {PyASCIIObject}, call = Ignored)
    public abstract static class Py_get_PyASCIIObject_wstr extends CApiUnaryBuiltinNode {

        @Specialization
        public Object get(Object object,
                        @Cached UnicodeAsWideCharNode asWideCharNode,
                        @Cached SizeofWCharNode sizeofWcharNode) {
            int elementSize = (int) sizeofWcharNode.execute(PythonContext.get(sizeofWcharNode).getCApiContext());
            return new PySequenceArrayWrapper(asWideCharNode.executeNativeOrder(object, elementSize), elementSize);
        }
    }

    @CApiBuiltin(ret = PyTypeObject, args = {PyCMethodObject}, call = Ignored)
    public abstract static class Py_get_PyCMethodObject_mm_class extends CApiUnaryBuiltinNode {
        @Specialization
        static Object get(PBuiltinMethod object) {
            return object.getClassObject();
        }
    }

    @CApiBuiltin(ret = PyMethodDef, args = {PyCFunctionObject}, call = Ignored)
    public abstract static class Py_get_PyCFunctionObject_m_ml extends CApiUnaryBuiltinNode {
        @Specialization
        static Object get(PythonObject object,
                        @CachedLibrary(limit = "3") DynamicObjectLibrary dylib) {
            Object methodDefPtr = dylib.getOrDefault(object, PythonCextMethodBuiltins.METHOD_DEF_PTR, null);
            if (methodDefPtr != null) {
                return methodDefPtr;
            }
            return new PyMethodDefWrapper(object);
        }
    }

    @CApiBuiltin(ret = PyObjectBorrowed, args = {PyCFunctionObject}, call = Ignored)
    public abstract static class Py_get_PyCFunctionObject_m_module extends CApiUnaryBuiltinNode {
        @Specialization
        Object get(Object object,
                        @Cached PyObjectLookupAttr lookup) {
            Object module = lookup.execute(null, object, T___MODULE__);
            return module != PNone.NO_VALUE ? module : getNativeNull();
        }
    }

    @CApiBuiltin(ret = PyObjectBorrowed, args = {PyCFunctionObject}, call = Ignored)
    public abstract static class Py_get_PyCFunctionObject_m_self extends CApiUnaryBuiltinNode {
        @Specialization
        static Object get(PBuiltinMethod object) {
            return object.getSelf();
        }

        @Specialization
        static Object get(PMethod object) {
            return object.getSelf();
        }
    }

    @CApiBuiltin(ret = PyObjectBorrowed, args = {PyCFunctionObject}, call = Ignored)
    public abstract static class Py_get_PyCFunctionObject_m_weakreflist extends CApiUnaryBuiltinNode {
        @Specialization
        static int get(@SuppressWarnings("unused") Object object) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @CApiBuiltin(ret = vectorcallfunc, args = {PyCFunctionObject}, call = Ignored)
    public abstract static class Py_get_PyCFunctionObject_vectorcall extends CApiUnaryBuiltinNode {
        @Specialization
        static int get(@SuppressWarnings("unused") Object object) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @CApiBuiltin(ret = Pointer, args = {PyByteArrayObject}, call = Ignored)
    public abstract static class Py_get_PyByteArrayObject_ob_start extends CApiUnaryBuiltinNode {

        @Specialization
        static Object doObStart(PByteArray object,
                        @Cached("createClassProfile()") ValueProfile classProfile) {
            SequenceStorage sequenceStorage = classProfile.profile(object.getSequenceStorage());
            if (sequenceStorage instanceof NativeSequenceStorage) {
                return ((NativeSequenceStorage) sequenceStorage).getPtr();
            }
            return new PySequenceArrayWrapper(object, 1);
        }
    }

    @CApiBuiltin(ret = Py_ssize_t, args = {PyByteArrayObject}, call = Ignored)
    public abstract static class Py_get_PyByteArrayObject_ob_exports extends CApiUnaryBuiltinNode {

        @Specialization
        static long get(PByteArray object) {
            return object.getExports();
        }
    }

    @CApiBuiltin(ret = Void, args = {PyByteArrayObject, Int}, call = Ignored)
    public abstract static class Py_set_PyByteArrayObject_ob_exports extends CApiBinaryBuiltinNode {

        @Specialization
        static Object set(PByteArray object, int value) {
            object.setExports(value);
            return PNone.NO_VALUE;
        }
    }

    @CApiBuiltin(ret = Py_ssize_t, args = {PyCompactUnicodeObject}, call = Ignored)
    public abstract static class Py_get_PyCompactUnicodeObject_wstr_length extends CApiUnaryBuiltinNode {

        @Specialization
        public long get(Object object,
                        @Cached UnicodeAsWideCharNode asWideCharNode,
                        @Cached SizeofWCharNode sizeofWcharNode) {
            long sizeofWchar = sizeofWcharNode.execute(PythonContext.get(sizeofWcharNode).getCApiContext());
            PBytes result = asWideCharNode.executeNativeOrder(object, sizeofWchar);
            return result.getSequenceStorage().length() / sizeofWchar;
        }
    }

    @CApiBuiltin(ret = PyObjectBorrowed, args = {PyDescrObject}, call = Ignored)
    public abstract static class Py_get_PyDescrObject_d_name extends CApiUnaryBuiltinNode {

        @Specialization
        public Object get(PBuiltinFunction object) {
            return object.getCApiName();
        }

        @Specialization
        public Object get(GetSetDescriptor object) {
            return object.getCApiName();
        }
    }

    @CApiBuiltin(ret = PyTypeObject, args = {PyDescrObject}, call = Ignored)
    public abstract static class Py_get_PyDescrObject_d_type extends CApiUnaryBuiltinNode {

        @Specialization
        public Object get(PBuiltinFunction object) {
            Object enclosingType = object.getEnclosingType();
            return enclosingType != null ? enclosingType : getNativeNull();
        }

        @Specialization
        public Object get(GetSetDescriptor object) {
            return object.getType();
        }
    }

    @CApiBuiltin(ret = Int, args = {PyFrameObject}, call = Ignored)
    public abstract static class Py_get_PyFrameObject_f_lineno extends CApiUnaryBuiltinNode {
        @Specialization
        public int get(PFrame frame) {
            return frame.getLine();
        }
    }

    @CApiBuiltin(ret = Pointer, args = {PyGetSetDef}, call = Ignored)
    public abstract static class Py_get_PyGetSetDef_closure extends CApiUnaryBuiltinNode {
        @Specialization
        static int get(@SuppressWarnings("unused") Object object) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @CApiBuiltin(ret = ConstCharPtrAsTruffleString, args = {PyGetSetDef}, call = Ignored)
    public abstract static class Py_get_PyGetSetDef_doc extends CApiUnaryBuiltinNode {
        @Specialization
        Object get(PythonObject object,
                        @Cached PythonAbstractObject.PInteropGetAttributeNode getAttrNode,
                        @Cached AsCharPointerNode asCharPointerNode) {
            Object doc = getAttrNode.execute(object, T___DOC__);
            if (PGuards.isPNone(doc)) {
                return getNULL();
            } else {
                return asCharPointerNode.execute(doc);
            }
        }
    }

    @CApiBuiltin(ret = getter, args = {PyGetSetDef}, call = Ignored)
    public abstract static class Py_get_PyGetSetDef_get extends CApiUnaryBuiltinNode {
        @Specialization
        static int get(@SuppressWarnings("unused") Object object) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @CApiBuiltin(ret = ConstCharPtrAsTruffleString, args = {PyGetSetDef}, call = Ignored)
    public abstract static class Py_get_PyGetSetDef_name extends CApiUnaryBuiltinNode {
        @Specialization
        Object get(PythonObject object,
                        @Cached PythonAbstractObject.PInteropGetAttributeNode getAttrNode,
                        @Cached AsCharPointerNode asCharPointerNode) {
            Object name = getAttrNode.execute(object, T___NAME__);
            if (PGuards.isPNone(name)) {
                return getNULL();
            } else {
                return asCharPointerNode.execute(name);
            }
        }
    }

    @CApiBuiltin(ret = setter, args = {PyGetSetDef}, call = Ignored)
    public abstract static class Py_get_PyGetSetDef_set extends CApiUnaryBuiltinNode {
        @Specialization
        static int get(@SuppressWarnings("unused") Object object) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @CApiBuiltin(ret = Pointer, args = {PyLongObject}, call = Ignored)
    public abstract static class Py_get_PyLongObject_ob_digit extends CApiUnaryBuiltinNode {
        @Specialization
        static Object get(int object) {
            return new PyLongDigitsWrapper(object);
        }

        @Specialization
        static Object get(long object) {
            return new PyLongDigitsWrapper(object);
        }

        @Specialization
        static Object get(PInt object) {
            return new PyLongDigitsWrapper(object);
        }
    }

    @CApiBuiltin(ret = objobjargproc, args = {PyMappingMethods}, call = Ignored)
    public abstract static class Py_get_PyMappingMethods_mp_ass_subscript extends PyGetTypeSlotNode {

        Py_get_PyMappingMethods_mp_ass_subscript() {
            super(T___SETITEM__);
        }
    }

    @CApiBuiltin(ret = lenfunc, args = {PyMappingMethods}, call = Ignored)
    public abstract static class Py_get_PyMappingMethods_mp_length extends PyGetTypeSlotNode {

        Py_get_PyMappingMethods_mp_length() {
            super(T___LEN__);
        }
    }

    @CApiBuiltin(ret = binaryfunc, args = {PyMappingMethods}, call = Ignored)
    public abstract static class Py_get_PyMappingMethods_mp_subscript extends PyGetTypeSlotNode {

        Py_get_PyMappingMethods_mp_subscript() {
            super(T___GETITEM__);
        }
    }

    @CApiBuiltin(ret = Pointer, args = {PyMethodDef}, call = Ignored)
    public abstract static class Py_get_PyMethodDef_ml_doc extends CApiUnaryBuiltinNode {

        @Specialization
        Object get(PythonObject object,
                        @Cached ReadAttributeFromObjectNode getAttrNode,
                        @Cached CastToTruffleStringNode castToStringNode) {
            Object doc = getAttrNode.execute(object, PyMethodDefWrapper.__C_DOC__);
            if (doc != PNone.NO_VALUE) {
                return doc;
            }
            doc = getAttrNode.execute(object, T___DOC__);
            if (!PGuards.isPNone(doc)) {
                try {
                    return new CStringWrapper(castToStringNode.execute(doc));
                } catch (CannotCastException e) {
                    // fall through
                }
            }
            return getNULL();
        }
    }

    @CApiBuiltin(ret = Int, args = {PyMethodDef}, call = Ignored)
    public abstract static class Py_get_PyMethodDef_ml_flags extends CApiUnaryBuiltinNode {
        @Specialization
        static int get(Object object) {
            if (object instanceof PBuiltinFunction) {
                return ((PBuiltinFunction) object).getFlags();
            } else if (object instanceof PBuiltinMethod) {
                return ((PBuiltinMethod) object).getFunction().getFlags();
            }
            return 0;
        }
    }

    @CApiBuiltin(ret = Pointer, args = {PyMethodDef}, call = Ignored)
    public abstract static class Py_get_PyMethodDef_ml_meth extends CApiUnaryBuiltinNode {

        @TruffleBoundary
        private static Object createFunctionWrapper(Object object) {
            int flags = Py_get_PyMethodDef_ml_flags.get(object);
            PythonNativeWrapper wrapper;
            if (CExtContext.isMethNoArgs(flags)) {
                wrapper = PyProcsWrapper.createUnaryFuncWrapper(object);
            } else if (CExtContext.isMethO(flags)) {
                wrapper = PyProcsWrapper.createBinaryFuncWrapper(object);
            } else if (CExtContext.isMethVarargsWithKeywords(flags)) {
                wrapper = PyProcsWrapper.createVarargKeywordWrapper(object);
            } else if (CExtContext.isMethVarargs(flags)) {
                wrapper = PyProcsWrapper.createVarargWrapper(object);
            } else {
                throw CompilerDirectives.shouldNotReachHere("other signature " + Integer.toHexString(flags));
            }
            return wrapper;
        }

        @Specialization
        Object getMethFromBuiltinFunction(PBuiltinFunction object) {
            PKeyword[] kwDefaults = object.getKwDefaults();
            for (int i = 0; i < kwDefaults.length; i++) {
                if (ExternalFunctionNodes.KW_CALLABLE.equals(kwDefaults[i].getName())) {
                    return kwDefaults[i].getValue();
                }
            }
            return createFunctionWrapper(object);
        }

        @Specialization
        Object getMethFromBuiltinMethod(PBuiltinMethod object) {
            return getMethFromBuiltinFunction(object.getFunction());
        }

        @Fallback
        Object getMeth(Object object) {
            return createFunctionWrapper(object);
        }
    }

    @CApiBuiltin(ret = Pointer, args = {PyMethodDef}, call = Ignored)
    public abstract static class Py_get_PyMethodDef_ml_name extends CApiUnaryBuiltinNode {

        @Specialization
        Object getName(PythonObject object,
                        @Cached PythonAbstractObject.PInteropGetAttributeNode getAttrNode,
                        @Cached CastToTruffleStringNode castToStringNode) {
            Object name = getAttrNode.execute(object, SpecialAttributeNames.T___NAME__);
            if (!PGuards.isPNone(name)) {
                try {
                    return new CStringWrapper(castToStringNode.execute(name));
                } catch (CannotCastException e) {
                    // fall through
                }
            }
            return getNULL();
        }
    }

    @CApiBuiltin(ret = PyMethodDef, args = {PyMethodDescrObject}, call = Ignored)
    public abstract static class Py_get_PyMethodDescrObject_d_method extends CApiUnaryBuiltinNode {

        @Specialization
        public Object get(PythonObject object) {
            return new PyMethodDefWrapper(object);
        }
    }

    @CApiBuiltin(ret = PyObjectBorrowed, args = {PyInstanceMethodObject}, call = Ignored)
    public abstract static class Py_get_PyInstanceMethodObject_func extends CApiUnaryBuiltinNode {
        @Specialization
        static Object get(PDecoratedMethod object) {
            return object.getCallable();
        }
    }

    @CApiBuiltin(ret = PyObjectBorrowed, args = {PyMethodObject}, call = Ignored)
    public abstract static class Py_get_PyMethodObject_im_func extends CApiUnaryBuiltinNode {
        @Specialization
        static Object get(PBuiltinMethod object) {
            return object.getFunction();
        }

        @Specialization
        static Object get(PMethod object) {
            return object.getFunction();
        }
    }

    @CApiBuiltin(ret = PyObjectBorrowed, args = {PyMethodObject}, call = Ignored)
    public abstract static class Py_get_PyMethodObject_im_self extends CApiUnaryBuiltinNode {

        @Specialization
        static Object get(PBuiltinMethod object) {
            return object.getSelf();
        }

        @Specialization
        static Object get(PMethod object) {
            return object.getSelf();
        }
    }

    @CApiBuiltin(ret = ConstCharPtrAsTruffleString, args = {PyModuleDef}, call = Ignored)
    public abstract static class Py_get_PyModuleDef_m_doc extends CApiUnaryBuiltinNode {
        @Specialization
        static int get(@SuppressWarnings("unused") Object object) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @CApiBuiltin(ret = PyMethodDef, args = {PyModuleDef}, call = Ignored)
    public abstract static class Py_get_PyModuleDef_m_methods extends CApiUnaryBuiltinNode {
        @Specialization
        static int get(@SuppressWarnings("unused") Object object) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @CApiBuiltin(ret = ConstCharPtrAsTruffleString, args = {PyModuleDef}, call = Ignored)
    public abstract static class Py_get_PyModuleDef_m_name extends CApiUnaryBuiltinNode {
        @Specialization
        static int get(@SuppressWarnings("unused") Object object) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @CApiBuiltin(ret = Py_ssize_t, args = {PyModuleDef}, call = Ignored)
    public abstract static class Py_get_PyModuleDef_m_size extends CApiUnaryBuiltinNode {
        @Specialization
        static int get(@SuppressWarnings("unused") Object object) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    @CApiBuiltin(ret = PyModuleDef, args = {PyModuleObject}, call = Ignored)
    public abstract static class Py_get_PyModuleObject_md_def extends CApiUnaryBuiltinNode {
        @Specialization
        static Object get(PythonModule object) {
            return object.getNativeModuleDef();
        }
    }

    @CApiBuiltin(ret = PyObjectBorrowed, args = {PyModuleObject}, call = Ignored)
    public abstract static class Py_get_PyModuleObject_md_dict extends CApiUnaryBuiltinNode {
        @Specialization
        static Object get(Object object,
                        @Exclusive @Cached PythonAbstractObject.PInteropGetAttributeNode getDictNode) {
            return getDictNode.execute(object, SpecialAttributeNames.T___DICT__);
        }
    }

    @CApiBuiltin(ret = Pointer, args = {PyModuleObject}, call = Ignored)
    public abstract static class Py_get_PyModuleObject_md_state extends CApiUnaryBuiltinNode {
        @Specialization
        static Object get(PythonModule object) {
            return object.getNativeModuleState();
        }
    }

    @CApiBuiltin(ret = unaryfunc, args = {PyNumberMethods}, call = Ignored)
    public abstract static class Py_get_PyNumberMethods_nb_absolute extends PyGetTypeSlotNode {

        Py_get_PyNumberMethods_nb_absolute() {
            super(T___ABS__);
        }
    }

    @CApiBuiltin(ret = binaryfunc, args = {PyNumberMethods}, call = Ignored)
    public abstract static class Py_get_PyNumberMethods_nb_add extends PyGetTypeSlotNode {

        Py_get_PyNumberMethods_nb_add() {
            super(T___ADD__);
        }
    }

    @CApiBuiltin(ret = binaryfunc, args = {PyNumberMethods}, call = Ignored)
    public abstract static class Py_get_PyNumberMethods_nb_and extends PyGetTypeSlotNode {

        Py_get_PyNumberMethods_nb_and() {
            super(T___AND__);
        }
    }

    @CApiBuiltin(ret = inquiry, args = {PyNumberMethods}, call = Ignored)
    public abstract static class Py_get_PyNumberMethods_nb_bool extends PyGetTypeSlotNode {

        Py_get_PyNumberMethods_nb_bool() {
            super(T___BOOL__);
        }
    }

    @CApiBuiltin(ret = binaryfunc, args = {PyNumberMethods}, call = Ignored)
    public abstract static class Py_get_PyNumberMethods_nb_divmod extends PyGetTypeSlotNode {

        Py_get_PyNumberMethods_nb_divmod() {
            super(T___DIVMOD__);
        }
    }

    @CApiBuiltin(ret = unaryfunc, args = {PyNumberMethods}, call = Ignored)
    public abstract static class Py_get_PyNumberMethods_nb_float extends PyGetTypeSlotNode {

        Py_get_PyNumberMethods_nb_float() {
            super(T___FLOAT__);
        }
    }

    @CApiBuiltin(ret = binaryfunc, args = {PyNumberMethods}, call = Ignored)
    public abstract static class Py_get_PyNumberMethods_nb_floor_divide extends PyGetTypeSlotNode {

        Py_get_PyNumberMethods_nb_floor_divide() {
            super(T___FLOORDIV__);
        }
    }

    @CApiBuiltin(ret = unaryfunc, args = {PyNumberMethods}, call = Ignored)
    public abstract static class Py_get_PyNumberMethods_nb_index extends PyGetTypeSlotNode {

        Py_get_PyNumberMethods_nb_index() {
            super(T___INDEX__);
        }
    }

    @CApiBuiltin(ret = binaryfunc, args = {PyNumberMethods}, call = Ignored)
    public abstract static class Py_get_PyNumberMethods_nb_inplace_add extends PyGetTypeSlotNode {

        Py_get_PyNumberMethods_nb_inplace_add() {
            super(T___IADD__);
        }
    }

    @CApiBuiltin(ret = binaryfunc, args = {PyNumberMethods}, call = Ignored)
    public abstract static class Py_get_PyNumberMethods_nb_inplace_and extends PyGetTypeSlotNode {

        Py_get_PyNumberMethods_nb_inplace_and() {
            super(T___IAND__);
        }
    }

    @CApiBuiltin(ret = binaryfunc, args = {PyNumberMethods}, call = Ignored)
    public abstract static class Py_get_PyNumberMethods_nb_inplace_floor_divide extends PyGetTypeSlotNode {

        Py_get_PyNumberMethods_nb_inplace_floor_divide() {
            super(T___IFLOORDIV__);
        }
    }

    @CApiBuiltin(ret = binaryfunc, args = {PyNumberMethods}, call = Ignored)
    public abstract static class Py_get_PyNumberMethods_nb_inplace_lshift extends PyGetTypeSlotNode {

        Py_get_PyNumberMethods_nb_inplace_lshift() {
            super(T___ILSHIFT__);
        }
    }

    @CApiBuiltin(ret = binaryfunc, args = {PyNumberMethods}, call = Ignored)
    public abstract static class Py_get_PyNumberMethods_nb_inplace_multiply extends PyGetTypeSlotNode {

        Py_get_PyNumberMethods_nb_inplace_multiply() {
            super(T___IMUL__);
        }
    }

    @CApiBuiltin(ret = binaryfunc, args = {PyNumberMethods}, call = Ignored)
    public abstract static class Py_get_PyNumberMethods_nb_inplace_or extends PyGetTypeSlotNode {

        Py_get_PyNumberMethods_nb_inplace_or() {
            super(T___IOR__);
        }
    }

    @CApiBuiltin(ret = ternaryfunc, args = {PyNumberMethods}, call = Ignored)
    public abstract static class Py_get_PyNumberMethods_nb_inplace_power extends PyGetTypeSlotNode {

        Py_get_PyNumberMethods_nb_inplace_power() {
            super(T___IPOW__);
        }
    }

    @CApiBuiltin(ret = binaryfunc, args = {PyNumberMethods}, call = Ignored)
    public abstract static class Py_get_PyNumberMethods_nb_inplace_remainder extends PyGetTypeSlotNode {

        Py_get_PyNumberMethods_nb_inplace_remainder() {
            super(T___IMOD__);
        }
    }

    @CApiBuiltin(ret = binaryfunc, args = {PyNumberMethods}, call = Ignored)
    public abstract static class Py_get_PyNumberMethods_nb_inplace_rshift extends PyGetTypeSlotNode {

        Py_get_PyNumberMethods_nb_inplace_rshift() {
            super(T___IRSHIFT__);
        }
    }

    @CApiBuiltin(ret = binaryfunc, args = {PyNumberMethods}, call = Ignored)
    public abstract static class Py_get_PyNumberMethods_nb_inplace_subtract extends PyGetTypeSlotNode {

        Py_get_PyNumberMethods_nb_inplace_subtract() {
            super(T___ISUB__);
        }
    }

    @CApiBuiltin(ret = binaryfunc, args = {PyNumberMethods}, call = Ignored)
    public abstract static class Py_get_PyNumberMethods_nb_inplace_true_divide extends PyGetTypeSlotNode {

        Py_get_PyNumberMethods_nb_inplace_true_divide() {
            super(T___ITRUEDIV__);
        }
    }

    @CApiBuiltin(ret = binaryfunc, args = {PyNumberMethods}, call = Ignored)
    public abstract static class Py_get_PyNumberMethods_nb_inplace_xor extends PyGetTypeSlotNode {

        Py_get_PyNumberMethods_nb_inplace_xor() {
            super(T___IXOR__);
        }
    }

    @CApiBuiltin(ret = unaryfunc, args = {PyNumberMethods}, call = Ignored)
    public abstract static class Py_get_PyNumberMethods_nb_int extends PyGetTypeSlotNode {

        Py_get_PyNumberMethods_nb_int() {
            super(T___INT__);
        }
    }

    @CApiBuiltin(ret = unaryfunc, args = {PyNumberMethods}, call = Ignored)
    public abstract static class Py_get_PyNumberMethods_nb_invert extends PyGetTypeSlotNode {

        Py_get_PyNumberMethods_nb_invert() {
            super(T___INVERT__);
        }
    }

    @CApiBuiltin(ret = binaryfunc, args = {PyNumberMethods}, call = Ignored)
    public abstract static class Py_get_PyNumberMethods_nb_lshift extends PyGetTypeSlotNode {

        Py_get_PyNumberMethods_nb_lshift() {
            super(T___LSHIFT__);
        }
    }

    @CApiBuiltin(ret = binaryfunc, args = {PyNumberMethods}, call = Ignored)
    public abstract static class Py_get_PyNumberMethods_nb_multiply extends PyGetTypeSlotNode {

        Py_get_PyNumberMethods_nb_multiply() {
            super(T___MUL__);
        }
    }

    @CApiBuiltin(ret = unaryfunc, args = {PyNumberMethods}, call = Ignored)
    public abstract static class Py_get_PyNumberMethods_nb_negative extends PyGetTypeSlotNode {

        Py_get_PyNumberMethods_nb_negative() {
            super(T___NEG__);
        }
    }

    @CApiBuiltin(ret = binaryfunc, args = {PyNumberMethods}, call = Ignored)
    public abstract static class Py_get_PyNumberMethods_nb_or extends PyGetTypeSlotNode {

        Py_get_PyNumberMethods_nb_or() {
            super(T___OR__);
        }
    }

    @CApiBuiltin(ret = unaryfunc, args = {PyNumberMethods}, call = Ignored)
    public abstract static class Py_get_PyNumberMethods_nb_positive extends PyGetTypeSlotNode {

        Py_get_PyNumberMethods_nb_positive() {
            super(T___POS__);
        }
    }

    @CApiBuiltin(ret = ternaryfunc, args = {PyNumberMethods}, call = Ignored)
    public abstract static class Py_get_PyNumberMethods_nb_power extends PyGetTypeSlotNode {

        Py_get_PyNumberMethods_nb_power() {
            super(T___POW__);
        }
    }

    @CApiBuiltin(ret = binaryfunc, args = {PyNumberMethods}, call = Ignored)
    public abstract static class Py_get_PyNumberMethods_nb_remainder extends PyGetTypeSlotNode {

        Py_get_PyNumberMethods_nb_remainder() {
            super(T___MOD__);
        }
    }

    @CApiBuiltin(ret = binaryfunc, args = {PyNumberMethods}, call = Ignored)
    public abstract static class Py_get_PyNumberMethods_nb_rshift extends PyGetTypeSlotNode {

        Py_get_PyNumberMethods_nb_rshift() {
            super(T___RSHIFT__);
        }
    }

    @CApiBuiltin(ret = binaryfunc, args = {PyNumberMethods}, call = Ignored)
    public abstract static class Py_get_PyNumberMethods_nb_subtract extends PyGetTypeSlotNode {

        Py_get_PyNumberMethods_nb_subtract() {
            super(T___SUB__);
        }
    }

    @CApiBuiltin(ret = binaryfunc, args = {PyNumberMethods}, call = Ignored)
    public abstract static class Py_get_PyNumberMethods_nb_true_divide extends PyGetTypeSlotNode {

        Py_get_PyNumberMethods_nb_true_divide() {
            super(T___TRUEDIV__);
        }
    }

    @CApiBuiltin(ret = binaryfunc, args = {PyNumberMethods}, call = Ignored)
    public abstract static class Py_get_PyNumberMethods_nb_xor extends PyGetTypeSlotNode {

        Py_get_PyNumberMethods_nb_xor() {
            super(T___XOR__);
        }
    }

    @CApiBuiltin(ret = Py_ssize_t, args = {PyObjectWrapper}, call = Ignored)
    public abstract static class Py_get_PyObject_ob_refcnt extends CApiUnaryBuiltinNode {

        @Specialization
        public Object get(PythonNativeWrapper wrapper) {
            return wrapper.getRefCount();
        }
    }

    @CApiBuiltin(ret = PyTypeObject, args = {PyObject}, call = Ignored)
    public abstract static class Py_get_PyObject_ob_type extends CApiUnaryBuiltinNode {

        @Specialization
        public Object get(Object object,
                        @Cached InlinedGetClassNode getClassNode) {
            return getClassNode.execute(this, object);
        }
    }

    @CApiBuiltin(ret = binaryfunc, args = {PySequenceMethods}, call = Ignored)
    public abstract static class Py_get_PySequenceMethods_sq_concat extends PyGetTypeSlotNode {

        Py_get_PySequenceMethods_sq_concat() {
            super(T___MUL__);
        }
    }

    @CApiBuiltin(ret = ssizeargfunc, args = {PySequenceMethods}, call = Ignored)
    public abstract static class Py_get_PySequenceMethods_sq_item extends PyGetTypeSlotNode {

        Py_get_PySequenceMethods_sq_item() {
            super(T___GETITEM__);
        }
    }

    @CApiBuiltin(ret = ssizeargfunc, args = {PySequenceMethods}, call = Ignored)
    public abstract static class Py_get_PySequenceMethods_sq_repeat extends PyGetTypeSlotNode {

        Py_get_PySequenceMethods_sq_repeat() {
            super(T___MUL__);
        }
    }

    @CApiBuiltin(ret = Py_ssize_t, args = {PySetObject}, call = Ignored)
    public abstract static class Py_get_PySetObject_used extends CApiUnaryBuiltinNode {

        @Specialization
        static long get(PBaseSet object,
                        @Cached HashingStorageLen lenNode) {
            return lenNode.execute(object.getDictStorage());
        }
    }

    @CApiBuiltin(ret = PyObject, args = {PySliceObject}, call = Ignored)
    public abstract static class Py_get_PySliceObject_start extends CApiUnaryBuiltinNode {
        @Specialization
        static Object doStart(PSlice object) {
            return object.getStart();
        }
    }

    @CApiBuiltin(ret = PyObject, args = {PySliceObject}, call = Ignored)
    public abstract static class Py_get_PySliceObject_step extends CApiUnaryBuiltinNode {
        @Specialization
        static Object doStep(PSlice object) {
            return object.getStep();
        }
    }

    @CApiBuiltin(ret = PyObject, args = {PySliceObject}, call = Ignored)
    public abstract static class Py_get_PySliceObject_stop extends CApiUnaryBuiltinNode {
        @Specialization
        static Object doStop(PSlice object) {
            return object.getStop();
        }
    }

    @CApiBuiltin(ret = PyObjectBorrowed, args = {PyThreadState}, call = Ignored)
    public abstract static class Py_get_PyThreadState_dict extends CApiUnaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        static Object get(PThreadState receiver,
                        @Cached PythonObjectFactory factory) {
            PDict threadStateDict = receiver.getThreadState().getDict();
            if (threadStateDict == null) {
                threadStateDict = factory.createDict();
                receiver.getThreadState().setDict(threadStateDict);
            }
            return threadStateDict;
        }
    }

    @CApiBuiltin(ret = allocfunc, args = {PyTypeObject}, call = Ignored)
    public abstract static class Py_get_PyTypeObject_tp_alloc extends CApiUnaryBuiltinNode {

        @Specialization
        public Object get(PythonManagedClass object,
                        @Cached LookupNativeMemberInMRONode lookupNativeMemberNode) {
            return lookupNativeMemberNode.execute(object, TP_ALLOC, TypeBuiltins.TYPE_ALLOC);
        }
    }

    @CApiBuiltin(ret = PyAsyncMethods, args = {PyTypeObject}, call = Ignored)
    public abstract static class Py_get_PyTypeObject_tp_as_async extends CApiUnaryBuiltinNode {

        @Specialization
        public Object get(@SuppressWarnings("unused") PythonManagedClass object) {
            return getNULL();
        }
    }

    @CApiBuiltin(ret = PyBufferProcs, args = {PyTypeObject}, call = Ignored)
    public abstract static class Py_get_PyTypeObject_tp_as_buffer extends CApiUnaryBuiltinNode {

        @Specialization
        public Object get(PythonManagedClass object,
                        @Cached LookupNativeMemberInMRONode lookupTpAsBufferNode) {
            Object result = lookupTpAsBufferNode.execute(object, NativeMember.TP_AS_BUFFER, TypeBuiltins.TYPE_AS_BUFFER);
            if (result == PNone.NO_VALUE) {
                return getNULL();
            }
            return result;
        }
    }

    @CApiBuiltin(ret = PyMappingMethods, args = {PyTypeObject}, call = Ignored)
    public abstract static class Py_get_PyTypeObject_tp_as_mapping extends CApiUnaryBuiltinNode {

        @Specialization
        public Object get(PythonManagedClass object,
                        @Cached(parameters = "GetItem") LookupCallableSlotInMRONode lookupGetitem,
                        @Cached(parameters = "Len") LookupCallableSlotInMRONode lookupLen) {
            if (lookupGetitem.execute(object) != PNone.NO_VALUE && lookupLen.execute(object) != PNone.NONE) {
                return new PyMappingMethodsWrapper(object);
            } else {
                return getNULL();
            }
        }
    }

    @CApiBuiltin(ret = PyNumberMethods, args = {PyTypeObject}, call = Ignored)
    public abstract static class Py_get_PyTypeObject_tp_as_number extends CApiUnaryBuiltinNode {

        @Specialization
        public Object get(PythonManagedClass object) {
            // TODO check for type and return 'NULL'
            return new PyNumberMethodsWrapper(object);
        }
    }

    @CApiBuiltin(ret = PySequenceMethods, args = {PyTypeObject}, call = Ignored)
    public abstract static class Py_get_PyTypeObject_tp_as_sequence extends CApiUnaryBuiltinNode {

        @Specialization
        public Object get(PythonManagedClass object,
                        @Cached(parameters = "Len") LookupCallableSlotInMRONode lookupLen) {
            if (lookupLen.execute(object) != PNone.NO_VALUE) {
                return new PySequenceMethodsWrapper(object);
            } else {
                return getNULL();
            }
        }
    }

    @CApiBuiltin(ret = PyTypeObject, args = {PyTypeObject}, call = Ignored)
    public abstract static class Py_get_PyTypeObject_tp_base extends CApiUnaryBuiltinNode {

        private static Object ensureClassObject(PythonContext context, Object klass) {
            if (klass instanceof PythonBuiltinClassType) {
                return context.lookupType((PythonBuiltinClassType) klass);
            }
            return klass;
        }

        @Specialization
        public Object get(PythonManagedClass object,
                        @Cached GetSuperClassNode getSuperClassNode) {
            Object superClass = getSuperClassNode.execute(object);
            if (superClass != null) {
                return ensureClassObject(getContext(), superClass);
            }
            return getNativeNull();
        }
    }

    @CApiBuiltin(ret = Py_ssize_t, args = {PyTypeObject}, call = Ignored)
    public abstract static class Py_get_PyTypeObject_tp_basicsize extends CApiUnaryBuiltinNode {

        @Specialization
        public Object get(PythonManagedClass object,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached PInteropGetAttributeNode getAttrNode) {
            Object val = getAttrNode.execute(object, T___BASICSIZE__);
            return val != PNone.NO_VALUE ? asSizeNode.executeExact(null, val) : 0L;
        }
    }

    @CApiBuiltin(ret = ternaryfunc, args = {PyTypeObject}, call = Ignored)
    public abstract static class Py_get_PyTypeObject_tp_call extends PyGetTypeSlotNode {

        Py_get_PyTypeObject_tp_call() {
            super(T___CALL__);
        }
    }

    @CApiBuiltin(ret = destructor, args = {PyTypeObject}, call = Ignored)
    public abstract static class Py_get_PyTypeObject_tp_dealloc extends CApiUnaryBuiltinNode {

        @Specialization
        public Object get(PythonManagedClass object,
                        @Cached LookupNativeMemberInMRONode lookupNativeMemberNode) {
            return lookupNativeMemberNode.execute(object, TP_DEALLOC, TypeBuiltins.TYPE_DEALLOC);
        }
    }

    @CApiBuiltin(ret = destructor, args = {PyTypeObject}, call = Ignored)
    public abstract static class Py_get_PyTypeObject_tp_del extends CApiUnaryBuiltinNode {

        @Specialization
        public Object get(PythonManagedClass object,
                        @Cached LookupNativeMemberInMRONode lookupNativeMemberNode) {
            // TODO: is this correct, "DEALLOC"?
            return lookupNativeMemberNode.execute(object, TP_DEALLOC, TypeBuiltins.TYPE_DEALLOC);
        }
    }

    @CApiBuiltin(ret = PyObjectBorrowed, args = {PyTypeObject}, call = Ignored)
    public abstract static class Py_get_PyTypeObject_tp_dict extends CApiUnaryBuiltinNode {

        @Specialization
        public Object get(PythonManagedClass object,
                        @Cached GetOrCreateDictNode getDict,
                        @Cached HashingStorageAddAllToOther addAllToOtherNode) {
            // TODO(fa): we could cache the dict instance on the class' native wrapper
            PDict dict = getDict.execute(object);
            if (dict instanceof StgDictObject) {
                return dict;
            }
            HashingStorage dictStorage = dict.getDictStorage();
            if (dictStorage instanceof DynamicObjectStorage) {
                // reuse the existing and modifiable storage
                return dict;
            }
            HashingStorage storage = new DynamicObjectStorage(object.getStorage());
            dict.setDictStorage(storage);
            if (dictStorage != null) {
                // copy all mappings to the new storage
                addAllToOtherNode.execute(null, dictStorage, dict);
            }
            return dict;
        }
    }

    @CApiBuiltin(ret = Py_ssize_t, args = {PyTypeObject}, call = Ignored)
    public abstract static class Py_get_PyTypeObject_tp_dictoffset extends CApiUnaryBuiltinNode {

        @Specialization
        public Object get(PythonManagedClass object,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached PInteropGetAttributeNode getAttrNode) {
            // TODO properly implement 'tp_dictoffset' for builtin classes
            if (object instanceof PythonBuiltinClass) {
                return 0L;
            }
            Object dictoffset = getAttrNode.execute(object, T___DICTOFFSET__);
            return dictoffset != PNone.NO_VALUE ? asSizeNode.executeExact(null, dictoffset) : 0L;
        }
    }

    @CApiBuiltin(ret = ConstCharPtrAsTruffleString, args = {PyTypeObject}, call = Ignored)
    public abstract static class Py_get_PyTypeObject_tp_doc extends CApiUnaryBuiltinNode {

        @Specialization
        public Object get(PythonManagedClass object,
                        @Cached CastToTruffleStringNode castToStringNode,
                        @Cached PInteropGetAttributeNode getAttrNode) {
            // return a C string wrapper that really allocates 'char*' on TO_NATIVE
            Object docObj = getAttrNode.execute(object, SpecialAttributeNames.T___DOC__);
            if (docObj instanceof TruffleString) {
                return new CStringWrapper((TruffleString) docObj);
            } else if (docObj instanceof PString) {
                return new CStringWrapper(castToStringNode.execute(docObj));
            }
            return getNULL();
        }
    }

    @CApiBuiltin(ret = UNSIGNED_LONG, args = {PyTypeObject}, call = Ignored)
    public abstract static class Py_get_PyTypeObject_tp_flags extends CApiUnaryBuiltinNode {

        @Specialization
        public Object get(PythonManagedClass object,
                        @Cached GetTypeFlagsNode getTypeFlagsNode) {
            return getTypeFlagsNode.execute(object);
        }
    }

    /*
     * PyUnicode slots
     */

    @CApiBuiltin(ret = freefunc, args = {PyTypeObject}, call = Ignored)
    public abstract static class Py_get_PyTypeObject_tp_free extends CApiUnaryBuiltinNode {

        @Specialization
        public Object get(PythonManagedClass object,
                        @Cached LookupNativeMemberInMRONode lookupNativeMemberNode) {
            return lookupNativeMemberNode.execute(object, TP_FREE, TypeBuiltins.TYPE_FREE);
        }
    }

    @CApiBuiltin(ret = getattrofunc, args = {PyTypeObject}, call = Ignored)
    public abstract static class Py_get_PyTypeObject_tp_getattro extends PyGetTypeSlotNode {

        Py_get_PyTypeObject_tp_getattro() {
            super(T___GETATTRIBUTE__);
        }
    }

    @CApiBuiltin(ret = hashfunc, args = {PyTypeObject}, call = Ignored)
    public abstract static class Py_get_PyTypeObject_tp_hash extends PyGetTypeSlotNode {

        Py_get_PyTypeObject_tp_hash() {
            super(T___HASH__);
        }
    }

    @CApiBuiltin(ret = initproc, args = {PyTypeObject}, call = Ignored)
    public abstract static class Py_get_PyTypeObject_tp_init extends PyGetTypeSlotNode {

        Py_get_PyTypeObject_tp_init() {
            super(T___INIT__);
        }
    }

    @CApiBuiltin(ret = Py_ssize_t, args = {PyTypeObject}, call = Ignored)
    public abstract static class Py_get_PyTypeObject_tp_itemsize extends CApiUnaryBuiltinNode {

        @Specialization
        public Object get(PythonManagedClass object,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached PInteropGetAttributeNode getAttrNode) {
            Object val = getAttrNode.execute(object, T___ITEMSIZE__);
            // If the attribute does not exist, this means that we take 'tp_itemsize' from the base
            // object which is by default 0 (see typeobject.c:PyBaseObject_Type).
            if (val == PNone.NO_VALUE) {
                return 0L;
            }
            return asSizeNode.executeExact(null, val);
        }
    }

    @CApiBuiltin(ret = getiterfunc, args = {PyTypeObject}, call = Ignored)
    public abstract static class Py_get_PyTypeObject_tp_iter extends PyGetTypeSlotNode {

        Py_get_PyTypeObject_tp_iter() {
            super(T___ITER__);
        }
    }

    @CApiBuiltin(ret = iternextfunc, args = {PyTypeObject}, call = Ignored)
    public abstract static class Py_get_PyTypeObject_tp_iternext extends PyGetTypeSlotNode {

        Py_get_PyTypeObject_tp_iternext() {
            super(T___NEXT__);
        }
    }

    @CApiBuiltin(ret = PyObjectBorrowed, args = {PyTypeObject}, call = Ignored)
    public abstract static class Py_get_PyTypeObject_tp_mro extends CApiUnaryBuiltinNode {

        @Specialization
        public Object get(PythonManagedClass object,
                        @Cached GetMroStorageNode getMroStorageNode,
                        @Cached PythonObjectFactory factory) {
            if (object.mroStore == null) {
                object.mroStore = factory.createTuple(getMroStorageNode.execute(object));
            }
            return object.mroStore;
        }
    }

    @CApiBuiltin(ret = ConstCharPtrAsTruffleString, args = {PyTypeObject}, call = Ignored)
    public abstract static class Py_get_PyTypeObject_tp_name extends CApiUnaryBuiltinNode {

        @Specialization
        public Object get(PythonManagedClass object) {
            // return a C string wrapper that really allocates 'char*' on TO_NATIVE
            return object.getClassNativeWrapper().getNameWrapper();
        }
    }

    @CApiBuiltin(ret = newfunc, args = {PyTypeObject}, call = Ignored)
    public abstract static class Py_get_PyTypeObject_tp_new extends CApiUnaryBuiltinNode {

        @Specialization
        public Object get(PythonManagedClass object,
                        @Cached ConditionProfile profileNewType,
                        @Cached LookupAttributeInMRONode.Dynamic getAttrNode,
                        @Cached PCallCapiFunction callGetNewfuncTypeidNode) {
            // T___new__ is magically a staticmethod for Python types. The tp_new slot lookup
            // expects to get the function
            Object newFunction = getAttrNode.execute(object, T___NEW__);
            if (profileNewType.profile(newFunction instanceof PDecoratedMethod)) {
                newFunction = ((PDecoratedMethod) newFunction).getCallable();
            }
            return ManagedMethodWrappers.createKeywords(newFunction, callGetNewfuncTypeidNode.call(NativeCAPISymbol.FUN_GET_NEWFUNC_TYPE_ID));
        }
    }

    @CApiBuiltin(ret = reprfunc, args = {PyTypeObject}, call = Ignored)
    public abstract static class Py_get_PyTypeObject_tp_repr extends PyGetTypeSlotNode {

        Py_get_PyTypeObject_tp_repr() {
            super(T___REPR__);
        }
    }

    @CApiBuiltin(ret = richcmpfunc, args = {PyTypeObject}, call = Ignored)
    public abstract static class Py_get_PyTypeObject_tp_richcompare extends PyGetTypeSlotNode {

        Py_get_PyTypeObject_tp_richcompare() {
            super(T_RICHCMP);
        }
    }

    @CApiBuiltin(ret = setattrofunc, args = {PyTypeObject}, call = Ignored)
    public abstract static class Py_get_PyTypeObject_tp_setattro extends PyGetTypeSlotNode {

        Py_get_PyTypeObject_tp_setattro() {
            super(T___SETATTR__);
        }
    }

    @CApiBuiltin(ret = reprfunc, args = {PyTypeObject}, call = Ignored)
    public abstract static class Py_get_PyTypeObject_tp_str extends PyGetTypeSlotNode {

        Py_get_PyTypeObject_tp_str() {
            super(T___STR__);
        }
    }

    @CApiBuiltin(ret = PyObject, args = {PyTypeObject}, call = Ignored)
    public abstract static class Py_get_PyTypeObject_tp_subclasses extends CApiUnaryBuiltinNode {

        @Specialization
        public Object get(@SuppressWarnings("unused") PythonManagedClass object) {
            // TODO create dict view on subclasses set
            return factory().createDict();
        }
    }

    @CApiBuiltin(ret = descrgetfunc, args = {PyTypeObject}, call = Ignored)
    public abstract static class Py_get_PyTypeObject_tp_descr_get extends PyGetTypeSlotNode {

        Py_get_PyTypeObject_tp_descr_get() {
            super(T___GET__);
        }
    }

    @CApiBuiltin(ret = descrsetfunc, args = {PyTypeObject}, call = Ignored)
    public abstract static class Py_get_PyTypeObject_tp_descr_set extends PyGetTypeSlotNode {

        Py_get_PyTypeObject_tp_descr_set() {
            super(T___SET__);
        }
    }

    @CApiBuiltin(name = "Py_get_PyTypeObject_tp_traverse", ret = traverseproc, args = {PyTypeObject}, call = Ignored)
    @CApiBuiltin(name = "Py_get_PyTypeObject_tp_clear", ret = inquiry, args = {PyTypeObject}, call = Ignored)
    public abstract static class Py_get_PyTypeObject_tp_TraverseClear extends CApiUnaryBuiltinNode {
        public static final TruffleString T_SEQUENCE_CLEAR = tsLiteral("sequence_clear");

        @Specialization
        public Object get(PythonManagedClass object,
                        @Bind("this") Node inliningTarget,
                        @Cached InlineIsBuiltinClassProfile isTupleProfile,
                        @Cached InlineIsBuiltinClassProfile isDictProfile,
                        @Cached InlineIsBuiltinClassProfile isListProfile) {
            if (isTupleProfile.profileClass(inliningTarget, object, PythonBuiltinClassType.PTuple) || isDictProfile.profileClass(inliningTarget, object, PythonBuiltinClassType.PDict) ||
                            isListProfile.profileClass(inliningTarget, object, PythonBuiltinClassType.PList)) {

                // TODO: return a proper traverse function, or at least a dummy
                return getNULL();
            }
            return getNULL();
        }
    }

    @CApiBuiltin(ret = Py_ssize_t, args = {PyTypeObject}, call = Ignored)
    public abstract static class Py_get_PyTypeObject_tp_vectorcall_offset extends CApiUnaryBuiltinNode {

        @Specialization
        public Object get(PythonManagedClass object,
                        @Cached LookupNativeMemberInMRONode lookupNativeMemberNode,
                        @Cached PyNumberAsSizeNode asSizeNode) {
            Object val = lookupNativeMemberNode.execute(object, TP_VECTORCALL_OFFSET, TypeBuiltins.TYPE_VECTORCALL_OFFSET);
            return val == PNone.NO_VALUE ? 0L : asSizeNode.executeExact(null, val);
        }
    }

    @CApiBuiltin(ret = UNSIGNED_INT, args = {PyTypeObject}, call = Ignored)
    public abstract static class Py_get_PyTypeObject_tp_version_tag extends CApiUnaryBuiltinNode {

        @Specialization
        public Object get(@SuppressWarnings("unused") Object object) {
            return 0;
        }
    }

    @CApiBuiltin(ret = Py_ssize_t, args = {PyTypeObject}, call = Ignored)
    public abstract static class Py_get_PyTypeObject_tp_weaklistoffset extends CApiUnaryBuiltinNode {

        @Specialization
        public Object get(PythonManagedClass object,
                        @Cached LookupAttributeInMRONode.Dynamic getAttrNode,
                        @Cached PyNumberAsSizeNode asSizeNode) {
            Object val = getAttrNode.execute(object, T___WEAKLISTOFFSET__);
            // If the attribute does not exist, this means that we take 'tp_itemsize' from the base
            // object which is by default 0 (see typeobject.c:PyBaseObject_Type).
            if (val == PNone.NO_VALUE) {
                return 0L;
            }
            return asSizeNode.executeExact(null, val);
        }
    }

    @CApiBuiltin(ret = Pointer, args = {PyUnicodeObject}, call = Ignored)
    public abstract static class Py_get_PyUnicodeObject_data extends CApiUnaryBuiltinNode {

        @Specialization
        public Object get(PString object,
                        @Cached UnicodeAsWideCharNode asWideCharNode,
                        @Cached SizeofWCharNode sizeofWcharNode) {
            int elementSize = (int) sizeofWcharNode.execute(PythonContext.get(sizeofWcharNode).getCApiContext());

            if (object.isNativeCharSequence()) {
                // in this case, we can just return the pointer
                return object.getNativeCharSequence().getPtr();
            }
            return new PySequenceArrayWrapper(asWideCharNode.executeNativeOrder(object, elementSize), elementSize);
        }
    }

    @CApiBuiltin(ret = Py_ssize_t, args = {PyVarObject}, call = Ignored)
    public abstract static class Py_get_PyVarObject_ob_size extends CApiUnaryBuiltinNode {

        @Specialization
        static long get(Object object,
                        @Cached ObSizeNode obSizeNode) {
            return obSizeNode.execute(object);
        }
    }

    @CApiBuiltin(ret = Void, args = {PyFrameObject, Int}, call = Ignored)
    public abstract static class Py_set_PyFrameObject_f_lineno extends CApiBinaryBuiltinNode {
        @Specialization
        public Object set(PFrame frame, int value) {
            frame.setLine(value);
            return PNone.NONE;
        }
    }

    @CApiBuiltin(ret = Void, args = {PyModuleObject, PyModuleDef}, call = Ignored)
    public abstract static class Py_set_PyModuleObject_md_def extends CApiBinaryBuiltinNode {
        @Specialization
        static Object set(PythonModule object, Object value) {
            object.setNativeModuleDef(value);
            return PNone.NO_VALUE;
        }
    }

    @CApiBuiltin(ret = Void, args = {PyModuleObject, Pointer}, call = Ignored)
    public abstract static class Py_set_PyModuleObject_md_state extends CApiBinaryBuiltinNode {
        @Specialization
        static Object set(PythonModule object, Object value) {
            object.setNativeModuleState(value);
            return PNone.NO_VALUE;
        }
    }

    @CApiBuiltin(ret = Void, args = {PyObjectWrapper, Py_ssize_t}, call = Ignored)
    public abstract static class Py_set_PyObject_ob_refcnt extends CApiBinaryBuiltinNode {

        @Specialization
        public Object set(PythonNativeWrapper wrapper, long value) {
            CApiTransitions.setRefCount(wrapper, value);
            return PNone.NONE;
        }
    }

    @CApiBuiltin(ret = Void, args = {PyTypeObject, allocfunc}, call = Ignored)
    public abstract static class Py_set_PyTypeObject_tp_alloc extends CApiBinaryBuiltinNode {

        @Specialization(guards = "isPythonClass(object)")
        static Object doTpAlloc(Object object, Object allocFunc,
                        @Cached WriteAttributeToObjectNode writeAttrNode) {
            writeAttrNode.execute(object, TypeBuiltins.TYPE_ALLOC, allocFunc);
            return PNone.NO_VALUE;
        }
    }

    @CApiBuiltin(ret = Void, args = {PyTypeObject, PyBufferProcs}, call = Ignored)
    public abstract static class Py_set_PyTypeObject_tp_as_buffer extends CApiBinaryBuiltinNode {

        @Specialization(guards = "isPythonClass(object)")
        static Object set(Object object, Object bufferProcs,
                        @Cached WriteAttributeToObjectNode writeAttrNode) {
            writeAttrNode.execute(object, TypeBuiltins.TYPE_AS_BUFFER, bufferProcs);
            return PNone.NO_VALUE;
        }
    }

    @CApiBuiltin(ret = Void, args = {PyTypeObject, Py_ssize_t}, call = Ignored)
    public abstract static class Py_set_PyTypeObject_tp_basicsize extends CApiBinaryBuiltinNode {

        @Specialization(guards = "isPythonClass(object)")
        static Object doTpBasicsize(Object object, long basicsize,
                        @Bind("this") Node inliningTarget,
                        @Cached WriteAttributeToObjectNode writeAttrNode,
                        @Cached WriteAttributeToBuiltinTypeNode writeAttrToBuiltinNode,
                        @Cached InlinedConditionProfile isBuiltinProfile,
                        @Cached InlineIsBuiltinClassProfile profile) {
            if (profile.profileClass(inliningTarget, object, PythonBuiltinClassType.PythonClass)) {
                writeAttrNode.execute(object, TypeBuiltins.TYPE_BASICSIZE, basicsize);
            } else if (isBuiltinProfile.profile(inliningTarget, object instanceof PythonBuiltinClass || object instanceof PythonBuiltinClassType)) {
                writeAttrToBuiltinNode.execute(object, T___BASICSIZE__, basicsize);
            } else {
                writeAttrNode.execute(object, T___BASICSIZE__, basicsize);
            }
            return PNone.NO_VALUE;
        }
    }

    @CApiBuiltin(ret = Void, args = {PyTypeObject, destructor}, call = Ignored)
    public abstract static class Py_set_PyTypeObject_tp_dealloc extends CApiBinaryBuiltinNode {

        @Specialization(guards = "isPythonClass(object)")
        static Object doTpFree(Object object, Object func,
                        @Cached WriteAttributeToObjectNode writeAttrNode) {
            writeAttrNode.execute(object, TypeBuiltins.TYPE_DEALLOC, func);
            return PNone.NO_VALUE;
        }
    }

    @CApiBuiltin(ret = Void, args = {PyTypeObject, PyObject}, call = Ignored)
    public abstract static class Py_set_PyTypeObject_tp_dict extends CApiBinaryBuiltinNode {

        @Specialization
        static Object doTpDict(PythonManagedClass object, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached GetDictIfExistsNode getDict,
                        @Cached SetDictNode setDict,
                        @Cached WriteAttributeToObjectNode writeAttrNode,
                        @Cached HashingStorageGetIterator getIterator,
                        @Cached HashingStorageIteratorNext itNext,
                        @Cached HashingStorageIteratorKey itKey,
                        @Cached HashingStorageIteratorValue itValue,
                        @Cached IsBuiltinObjectProfile isPrimitiveDictProfile1,
                        @Cached IsBuiltinObjectProfile isPrimitiveDictProfile2) {
            if (isBuiltinDict(inliningTarget, isPrimitiveDictProfile1, isPrimitiveDictProfile2, value)) {
                // special and fast case: commit items and change store
                PDict d = (PDict) value;
                HashingStorage storage = d.getDictStorage();
                HashingStorageIterator it = getIterator.execute(storage);
                while (itNext.execute(storage, it)) {
                    writeAttrNode.execute(object, itKey.execute(storage, it), itValue.execute(storage, it));
                }
                PDict existing = getDict.execute(object);
                if (existing != null) {
                    d.setDictStorage(existing.getDictStorage());
                } else {
                    d.setDictStorage(new DynamicObjectStorage(object.getStorage()));
                }
                setDict.execute(object, d);
            } else {
                // TODO custom mapping object
            }
            return PNone.NO_VALUE;
        }

        private static boolean isBuiltinDict(Node inliningTarget, IsBuiltinObjectProfile isPrimitiveDictProfile1, IsBuiltinObjectProfile isPrimitiveDictProfile2, Object value) {
            return value instanceof PDict &&
                            (isPrimitiveDictProfile1.profileObject(inliningTarget, value, PythonBuiltinClassType.PDict) ||
                                            isPrimitiveDictProfile2.profileObject(inliningTarget, value, PythonBuiltinClassType.StgDict));
        }
    }

    @CApiBuiltin(ret = Void, args = {PyTypeObject, Py_ssize_t}, call = Ignored)
    public abstract static class Py_set_PyTypeObject_tp_dictoffset extends CApiBinaryBuiltinNode {

        @Specialization
        static Object doTpDictoffset(PythonManagedClass object, long value,
                        @Cached PythonAbstractObject.PInteropSetAttributeNode setAttrNode) {
            // TODO properly implement 'tp_dictoffset' for builtin classes
            if (!(object instanceof PythonBuiltinClass)) {
                try {
                    setAttrNode.execute(object, T___DICTOFFSET__, value);
                } catch (CannotCastException e) {
                    throw CompilerDirectives.shouldNotReachHere("non-integer passed to tp_dictoffset assignment");
                } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
            }
            return PNone.NO_VALUE;
        }

    }

    @CApiBuiltin(ret = Void, args = {PyTypeObject, UNSIGNED_LONG}, call = Ignored)
    public abstract static class Py_set_PyTypeObject_tp_flags extends CApiBinaryBuiltinNode {
        @Specialization
        static Object doTpFlags(PythonManagedClass object, long flags,
                        @Cached TypeNodes.SetTypeFlagsNode setTypeFlagsNode) {
            if (object instanceof PythonBuiltinClass) {
                /*
                 * Assert that we try to set the same flags, except the abc flags for sequence and
                 * mapping. If there is a difference, this means we did not properly maintain our
                 * flag definition in TypeNodes.GetTypeFlagsNode.
                 */
                assert assertFlagsInSync(object, flags);
            }
            setTypeFlagsNode.execute(object, flags);
            return PNone.NO_VALUE;
        }

        @TruffleBoundary
        private static boolean assertFlagsInSync(PythonManagedClass object, long newFlags) {
            long expected = GetTypeFlagsNode.getUncached().execute(object) & ~TypeFlags.COLLECTION_FLAGS;
            long actual = newFlags & ~TypeFlags.COLLECTION_FLAGS;
            assert expected == actual : "type flags of " + object.getName() + " definitions are out of sync: expected " + expected + " vs. actual " + actual;
            return true;
        }
    }

    @CApiBuiltin(ret = Void, args = {PyTypeObject, freefunc}, call = Ignored)
    public abstract static class Py_set_PyTypeObject_tp_free extends CApiBinaryBuiltinNode {

        @Specialization(guards = "isPythonClass(object)")
        static Object set(Object object, Object freeFunc,
                        @Cached WriteAttributeToObjectNode writeAttrNode) {
            writeAttrNode.execute(object, TypeBuiltins.TYPE_FREE, freeFunc);
            return PNone.NO_VALUE;
        }
    }

    @CApiBuiltin(ret = Void, args = {PyTypeObject, Py_ssize_t}, call = Ignored)
    public abstract static class Py_set_PyTypeObject_tp_itemsize extends CApiBinaryBuiltinNode {

        @Specialization(guards = "isPythonClass(object)")
        static Object doTpItemsize(Object object, long itemsize,
                        @Cached WriteAttributeToObjectNode writeAttrNode,
                        @Cached ConditionProfile profile) {
            if (!profile.profile(object instanceof PythonBuiltinClass)) {
                // not expected to happen ...
                writeAttrNode.execute(object, T___ITEMSIZE__, itemsize);
            }
            return PNone.NO_VALUE;
        }
    }

    @CApiBuiltin(ret = Void, args = {PyTypeObject, PyObject}, call = Ignored)
    public abstract static class Py_set_PyTypeObject_tp_subclasses extends CApiBinaryBuiltinNode {

        @GenerateUncached
        abstract static class EachSubclassAdd extends HashingStorageForEachCallback<Set<PythonAbstractClass>> {

            @Override
            public abstract Set<PythonAbstractClass> execute(Frame frame, Node inliningTarget, HashingStorage storage, HashingStorageIterator it, Set<PythonAbstractClass> subclasses);

            @Specialization
            public Set<PythonAbstractClass> doIt(Frame frame, @SuppressWarnings("unused") Node inliningTarget, HashingStorage storage, HashingStorageIterator it, Set<PythonAbstractClass> subclasses,
                            @Cached HashingStorageIteratorKey itKey,
                            @Cached HashingStorageIteratorKeyHash itKeyHash,
                            @Cached HashingStorageGetItemWithHash getItemNode) {
                long hash = itKeyHash.execute(storage, it);
                Object key = itKey.execute(storage, it);
                setAdd(subclasses, (PythonClass) getItemNode.execute(frame, storage, key, hash));
                return subclasses;
            }

            @TruffleBoundary
            protected static void setAdd(Set<PythonAbstractClass> set, PythonClass cls) {
                set.add(cls);
            }
        }

        @Specialization
        static Object doTpSubclasses(PythonClass object, PDict dict,
                        @Cached GetSubclassesNode getSubclassesNode,
                        @Cached EachSubclassAdd eachNode,
                        @Cached HashingStorageForEach forEachNode) {
            HashingStorage storage = dict.getDictStorage();
            Set<PythonAbstractClass> subclasses = getSubclassesNode.execute(object);
            forEachNode.execute(null, storage, eachNode, subclasses);
            return PNone.NO_VALUE;
        }
    }

    @CApiBuiltin(ret = Void, args = {PyTypeObject, Py_ssize_t}, call = Ignored)
    public abstract static class Py_set_PyTypeObject_tp_vectorcall_offset extends CApiBinaryBuiltinNode {

        @Specialization(guards = "isPythonClass(object)")
        static Object doTpVectorcallOffset(Object object, long offset,
                        @Cached WriteAttributeToObjectNode writeAttrNode) {
            writeAttrNode.execute(object, TypeBuiltins.TYPE_VECTORCALL_OFFSET, offset);
            return PNone.NO_VALUE;
        }
    }

    @CApiBuiltin(ret = PyObjectBorrowed, args = {PyTypeObject}, call = Ignored)
    public abstract static class Py_get_PyTypeObject_tp_bases extends CApiUnaryBuiltinNode {

        @Specialization
        Object doTpBases(PythonManagedClass type,
                        @Cached TypeNodes.GetBaseClassesNode getBaseClassesNode) {
            if (type.basesTuple == null) {
                type.basesTuple = factory().createTuple(getBaseClassesNode.execute(type));
            }
            return type.basesTuple;
        }
    }

    @CApiBuiltin(name = "Py_get_dummy", ret = Pointer, args = {Pointer}, call = Ignored)
    @CApiBuiltin(name = "Py_get_PyTypeObject_tp_setattr", ret = setattrfunc, args = {PyTypeObject}, call = Ignored)
    @CApiBuiltin(name = "Py_get_PyTypeObject_tp_getattr", ret = getattrfunc, args = {PyTypeObject}, call = Ignored)
    @CApiBuiltin(name = "Py_get_PyTypeObject_tp_methods", ret = PyMethodDef, args = {PyTypeObject}, call = Ignored)
    @CApiBuiltin(name = "Py_get_PyTypeObject_tp_members", ret = PyMemberDef, args = {PyTypeObject}, call = Ignored)
    @CApiBuiltin(name = "Py_get_PyTypeObject_tp_getset", ret = PyGetSetDef, args = {PyTypeObject}, call = Ignored)
    @CApiBuiltin(name = "Py_get_PyTypeObject_tp_is_gc", ret = inquiry, args = {PyTypeObject}, call = Ignored)
    @CApiBuiltin(name = "Py_get_PyTypeObject_tp_finalize", ret = destructor, args = {PyTypeObject}, call = Ignored)
    @CApiBuiltin(name = "Py_get_PyTypeObject_tp_vectorcall", ret = vectorcallfunc, args = {PyTypeObject}, call = Ignored)
    @CApiBuiltin(name = "Py_get_PySequenceMethods_sq_length", ret = lenfunc, args = {PySequenceMethods}, call = Ignored)
    @CApiBuiltin(name = "Py_get_PySequenceMethods_sq_ass_item", ret = ssizeobjargproc, args = {PySequenceMethods}, call = Ignored)
    @CApiBuiltin(name = "Py_get_PySequenceMethods_sq_contains", ret = objobjproc, args = {PySequenceMethods}, call = Ignored)
    @CApiBuiltin(name = "Py_get_PySequenceMethods_sq_inplace_concat", ret = binaryfunc, args = {PySequenceMethods}, call = Ignored)
    @CApiBuiltin(name = "Py_get_PySequenceMethods_sq_inplace_repeat", ret = ssizeargfunc, args = {PySequenceMethods}, call = Ignored)
    @CApiBuiltin(name = "Py_get_PyAsyncMethods_am_await", ret = unaryfunc, args = {PyAsyncMethods}, call = Ignored)
    @CApiBuiltin(name = "Py_get_PyAsyncMethods_am_aiter", ret = unaryfunc, args = {PyAsyncMethods}, call = Ignored)
    @CApiBuiltin(name = "Py_get_PyAsyncMethods_am_anext", ret = unaryfunc, args = {PyAsyncMethods}, call = Ignored)
    @CApiBuiltin(name = "Py_get_PyNumberMethods_nb_matrix_multiply", ret = binaryfunc, args = {PyNumberMethods}, call = Ignored)
    @CApiBuiltin(name = "Py_get_PyNumberMethods_nb_inplace_matrix_multiply", ret = binaryfunc, args = {PyNumberMethods}, call = Ignored)
    @CApiBuiltin(name = "Py_get_PyBufferProcs_bf_getbuffer", ret = getbufferproc, args = {PyBufferProcs}, call = Ignored)
    @CApiBuiltin(name = "Py_get_PyBufferProcs_bf_releasebuffer", ret = releasebufferproc, args = {PyBufferProcs}, call = Ignored)
    public abstract static class PyGetSlotDummyPtr extends CApiUnaryBuiltinNode {

        @Specialization
        public Object get(@SuppressWarnings("unused") Object object) {
            return getNULL();
        }
    }

    @CApiBuiltin(name = "Py_get_PyTypeObject_tp_cache", ret = PyObjectBorrowed, args = {PyTypeObject}, call = Ignored)
    @CApiBuiltin(name = "Py_get_PyTypeObject_tp_weaklist", ret = PyObjectBorrowed, args = {PyTypeObject}, call = Ignored)
    public abstract static class PyGetSlotDummyPyPtr extends CApiUnaryBuiltinNode {

        @Specialization
        public Object get(@SuppressWarnings("unused") Object object) {
            return getNativeNull();
        }
    }

    public abstract static class PyGetTypeSlotNode extends CApiUnaryBuiltinNode {
        private final TruffleString name;
        private @Child LookupAttributeInMRONode lookup;

        PyGetTypeSlotNode(TruffleString name) {
            this.name = name;
            lookup = LookupAttributeInMRONode.create(name);
        }

        @Specialization
        public Object getWrapper(PythonNativeWrapper object) {
            return getProcsWrapper(object.getDelegate());
        }

        @Specialization
        public Object getClass(PythonManagedClass object) {
            return getProcsWrapper(object);
        }

        @TruffleBoundary
        private Object getProcsWrapper(Object type) {
            Object value = lookup.execute(type);
            if (value instanceof PNone) {
                // both None and NO_VALUE can appear
                return getNULL();
            }
            /*
             * The method can be a slot wrapper that already wraps a native slot function. If it
             * matches in type and slot name, we should unwrap it to avoid nesting multiple
             * wrappers.
             */
            if (value instanceof PBuiltinFunction function) {
                Object wrappedPtr = ExternalFunctionNodes.tryGetHiddenCallable(function);
                if (wrappedPtr != null && name.equalsUncached(function.getName(), TS_ENCODING) &&
                                function.getEnclosingType() != null && IsSubtypeNode.getUncached().execute(type, function.getEnclosingType())) {
                    return wrappedPtr;
                }
            }
            CApiContext cApiContext = PythonContext.get(this).getCApiContext();
            return cApiContext.getOrCreateProcWrapper(value, this::createProcsWrapper);
        }

        private Object createProcsWrapper(Object value) {
            switch (getRetDescriptor()) {
                case unaryfunc:
                case reprfunc:
                case iternextfunc:
                case getiterfunc:
                    return new PyProcsWrapper.UnaryFuncWrapper(value);
                case binaryfunc:
                    return new PyProcsWrapper.BinaryFuncWrapper(value);
                case ternaryfunc:
                    return new PyProcsWrapper.TernaryFunctionWrapper(value);
                case hashfunc:
                    return new PyProcsWrapper.HashfuncWrapper(value);
                case inquiry:
                    return new PyProcsWrapper.InquiryWrapper(value);
                case initproc:
                    return new PyProcsWrapper.InitWrapper(value);
                case ssizeargfunc:
                    return new PyProcsWrapper.SsizeargfuncWrapper(value);
                case lenfunc:
                    return new PyProcsWrapper.LenfuncWrapper(value);
                case richcmpfunc:
                    return new PyProcsWrapper.RichcmpFunctionWrapper(value);
                case objobjargproc:
                case setattrofunc:
                case descrsetfunc:
                    return new PyProcsWrapper.SetAttrWrapper(value);
                case getattrofunc:
                    return new PyProcsWrapper.GetAttrWrapper(value);
                case descrgetfunc:
                    return new PyProcsWrapper.DescrGetFunctionWrapper(value);
            }
            throw CompilerDirectives.shouldNotReachHere("descriptor: " + getRetDescriptor());
        }
    }

    @CApiBuiltin(name = "Py_set_PyVarObject_ob_size", ret = Void, args = {PyVarObject, Py_ssize_t}, call = Ignored)
    @CApiBuiltin(name = "Py_set_PyTypeObject_tp_getattr", ret = Void, args = {PyTypeObject, getattrfunc}, call = Ignored)
    @CApiBuiltin(name = "Py_set_PyTypeObject_tp_getattro", ret = Void, args = {PyTypeObject, getattrofunc}, call = Ignored)
    @CApiBuiltin(name = "Py_set_PyTypeObject_tp_setattr", ret = Void, args = {PyTypeObject, setattrfunc}, call = Ignored)
    @CApiBuiltin(name = "Py_set_PyTypeObject_tp_setattro", ret = Void, args = {PyTypeObject, setattrofunc}, call = Ignored)
    @CApiBuiltin(name = "Py_set_PyTypeObject_tp_finalize", ret = Void, args = {PyTypeObject, destructor}, call = Ignored)
    @CApiBuiltin(name = "Py_set_PyTypeObject_tp_iter", ret = Void, args = {PyTypeObject, getiterfunc}, call = Ignored)
    @CApiBuiltin(name = "Py_set_PyTypeObject_tp_iternext", ret = Void, args = {PyTypeObject, iternextfunc}, call = Ignored)
    @CApiBuiltin(name = "Py_set_PyTypeObject_tp_base", ret = Void, args = {PyTypeObject, PyTypeObject}, call = Ignored)
    @CApiBuiltin(name = "Py_set_PyTypeObject_tp_bases", ret = Void, args = {PyTypeObject, PyObject}, call = Ignored)
    @CApiBuiltin(name = "Py_set_PyTypeObject_tp_clear", ret = Void, args = {PyTypeObject, inquiry}, call = Ignored)
    @CApiBuiltin(name = "Py_set_PyTypeObject_tp_mro", ret = Void, args = {PyTypeObject, PyObject}, call = Ignored)
    @CApiBuiltin(name = "Py_set_PyTypeObject_tp_new", ret = Void, args = {PyTypeObject, newfunc}, call = Ignored)
    @CApiBuiltin(name = "Py_set_PyTypeObject_tp_traverse", ret = Void, args = {PyTypeObject, traverseproc}, call = Ignored)
    @CApiBuiltin(name = "Py_set_PyTypeObject_tp_weaklistoffset", ret = Void, args = {PyTypeObject, Py_ssize_t}, call = Ignored)
    public abstract static class PySetSlotDummyPtr extends CApiBinaryBuiltinNode {

        @SuppressWarnings("unused")
        @Specialization
        public Object set(Object object, Object value) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }
}
