/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltinExecutable;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltinNode;

public abstract class PythonCextBuiltinRegistry {

    private PythonCextBuiltinRegistry() {
        // no instances
    }

    /*
     * GENERATED CODE - DO NOT MODIFY
     */
    // {{start CAPI_BUILTINS}}
// GENERATED CODE - see PythonCextBuiltins
    static CApiBuiltinNode createBuiltinNode(int id) {
        switch (id) {
            case 0:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyIndex_CheckNodeGen.create();
            case 1:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyIter_NextNodeGen.create();
            case 2:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyMapping_CheckNodeGen.create();
            case 3:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyMapping_ItemsNodeGen.create();
            case 4:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyMapping_KeysNodeGen.create();
            case 5:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyMapping_SizeNodeGen.create();
            case 6:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyMapping_ValuesNodeGen.create();
            case 7:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyNumber_AbsoluteNodeGen.create();
            case 8:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyNumber_CheckNodeGen.create();
            case 9:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyNumber_DivmodNodeGen.create();
            case 10:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyNumber_FloatNodeGen.create();
            case 11:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyNumber_InPlacePowerNodeGen.create();
            case 12:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyNumber_IndexNodeGen.create();
            case 13:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyNumber_IndexNodeGen.create();
            case 14:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyNumber_LongNodeGen.create();
            case 15:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyNumber_PowerNodeGen.create();
            case 16:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyNumber_ToBaseNodeGen.create();
            case 17:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyObject_GetDocNodeGen.create();
            case 18:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyObject_GetItemNodeGen.create();
            case 19:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyObject_GetItemNodeGen.create();
            case 20:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyObject_LengthHintNodeGen.create();
            case 21:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyObject_SetDocNodeGen.create();
            case 22:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyObject_SizeNodeGen.create();
            case 23:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PySequence_CheckNodeGen.create();
            case 24:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PySequence_ConcatNodeGen.create();
            case 25:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PySequence_ContainsNodeGen.create();
            case 26:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PySequence_DelItemNodeGen.create();
            case 27:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PySequence_GetItemNodeGen.create();
            case 28:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PySequence_GetSliceNodeGen.create();
            case 29:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PySequence_InPlaceConcatNodeGen.create();
            case 30:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PySequence_InPlaceRepeatNodeGen.create();
            case 31:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PySequence_ListNodeGen.create();
            case 32:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PySequence_RepeatNodeGen.create();
            case 33:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PySequence_SetItemNodeGen.create();
            case 34:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PySequence_SizeNodeGen.create();
            case 35:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PySequence_SizeNodeGen.create();
            case 36:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PySequence_TupleNodeGen.create();
            case 37:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyTruffleNumber_BinOpNodeGen.create();
            case 38:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyTruffleNumber_InPlaceBinOpNodeGen.create();
            case 39:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextAbstractBuiltinsFactory.PyTruffleNumber_UnaryOpNodeGen.create();
            case 40:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBoolBuiltinsFactory.PyTruffle_FalseFactory.create();
            case 41:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBoolBuiltinsFactory.PyTruffle_TrueFactory.create();
            case 42:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyEval_RestoreThreadFactory.create();
            case 43:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyEval_SaveThreadFactory.create();
            case 44:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyFrame_NewFactory.create();
            case 45:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyGILState_EnsureFactory.create();
            case 46:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyGILState_ReleaseFactory.create();
            case 47:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyObject_GC_TrackFactory.create();
            case 48:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyObject_GC_UnTrackFactory.create();
            case 49:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTraceMalloc_TrackFactory.create();
            case 50:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTraceMalloc_UntrackFactory.create();
            case 51:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffleModule_AddFunctionToModuleFactory.create();
            case 52:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffleToCharPointerFactory.create();
            case 53:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffleType_AddFunctionToTypeFactory.create();
            case 54:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffleType_AddGetSetFactory.create();
            case 55:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffleType_AddMemberFactory.create();
            case 56:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffleType_AddSlotFactory.create();
            case 57:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_Arg_ParseTupleAndKeywordsFactory.create();
            case 58:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_ByteArray_EmptyWithCapacityFactory.create();
            case 59:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_Bytes_AsStringFactory.create();
            case 60:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_Bytes_CheckEmbeddedNullFactory.create();
            case 61:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_Bytes_EmptyWithCapacityFactory.create();
            case 62:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_Compute_MroFactory.create();
            case 63:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_DebugFactory.create();
            case 64:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_DebugTraceFactory.create();
            case 65:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_FatalErrorFuncFactory.create();
            case 66:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_FileSystemDefaultEncodingFactory.create();
            case 67:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_Get_Inherited_Native_SlotsFactory.create();
            case 68:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_LogStringFactory.create();
            case 69:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_MemoryViewFromBufferFactory.create();
            case 70:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_Native_OptionsFactory.create();
            case 71:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_NewTypeDictFactory.create();
            case 72:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_OS_DoubleToStringFactory.create();
            case 73:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_OS_StringToDoubleFactory.create();
            case 74:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_Object_AllocFactory.create();
            case 75:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_Object_FreeFactory.create();
            case 76:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_Register_NULLFactory.create();
            case 77:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_Set_Native_SlotsFactory.create();
            case 78:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_Set_SulongTypeFactory.create();
            case 79:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_ToNativeFactory.create();
            case 80:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_Trace_TypeFactory.create();
            case 81:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_TypeFactory.create();
            case 82:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_Type_ModifiedFactory.create();
            case 83:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_Unicode_FromFormatFactory.create();
            case 84:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_tss_createFactory.create();
            case 85:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_tss_deleteFactory.create();
            case 86:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_tss_getFactory.create();
            case 87:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory.PyTruffle_tss_setFactory.create();
            case 88:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory._PyTraceMalloc_NewReferenceFactory.create();
            case 89:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltinsFactory._PyTruffle_Trace_FreeFactory.create();
            case 90:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBytesBuiltinsFactory.PyBytes_FromObjectFactory.create();
            case 91:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBytesBuiltinsFactory.PyBytes_SizeFactory.create();
            case 92:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBytesBuiltinsFactory.PyTruffleByteArray_FromStringAndSizeFactory.create();
            case 93:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBytesBuiltinsFactory.PyTruffleBytes_ConcatFactory.create();
            case 94:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBytesBuiltinsFactory.PyTruffleBytes_FromFormatFactory.create();
            case 95:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBytesBuiltinsFactory.PyTruffleBytes_FromStringAndSizeFactory.create();
            case 96:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBytesBuiltinsFactory._PyBytes_JoinFactory.create();
            case 97:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBytesBuiltinsFactory._PyTruffleBytes_ResizeFactory.create();
            case 98:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextBytesBuiltinsFactory._PyTruffleBytes_ResizeFactory.create();
            case 99:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextCapsuleBuiltinsFactory.PyCapsule_GetContextFactory.create();
            case 100:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextCapsuleBuiltinsFactory.PyCapsule_GetDestructorFactory.create();
            case 101:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextCapsuleBuiltinsFactory.PyCapsule_GetNameFactory.create();
            case 102:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextCapsuleBuiltinsFactory.PyCapsule_GetPointerFactory.create();
            case 103:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextCapsuleBuiltinsFactory.PyCapsule_ImportFactory.create();
            case 104:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextCapsuleBuiltinsFactory.PyCapsule_IsValidFactory.create();
            case 105:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextCapsuleBuiltinsFactory.PyCapsule_NewFactory.create();
            case 106:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextCapsuleBuiltinsFactory.PyCapsule_SetContextFactory.create();
            case 107:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextCapsuleBuiltinsFactory.PyCapsule_SetDestructorFactory.create();
            case 108:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextCapsuleBuiltinsFactory.PyCapsule_SetNameFactory.create();
            case 109:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextCapsuleBuiltinsFactory.PyCapsule_SetPointerFactory.create();
            case 110:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextCEvalBuiltinsFactory.PyEval_GetBuiltinsFactory.create();
            case 111:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextCEvalBuiltinsFactory.PyThread_acquire_lockFactory.create();
            case 112:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextCEvalBuiltinsFactory.PyThread_allocate_lockFactory.create();
            case 113:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextCEvalBuiltinsFactory.PyThread_release_lockFactory.create();
            case 114:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextCEvalBuiltinsFactory._PyTruffleEval_EvalCodeExFactory.create();
            case 115:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextClassBuiltinsFactory.PyInstanceMethod_NewFactory.create();
            case 116:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextClassBuiltinsFactory.PyMethod_NewFactory.create();
            case 117:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextCodeBuiltinsFactory.PyCode_NewFactory.create();
            case 118:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextCodeBuiltinsFactory.PyCode_NewEmptyFactory.create();
            case 119:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextCodeBuiltinsFactory.PyCode_NewWithPosOnlyArgsFactory.create();
            case 120:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextComplexBuiltinsFactory.PyComplex_FromDoublesFactory.create();
            case 121:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextComplexBuiltinsFactory.PyComplex_ImagAsDoubleFactory.create();
            case 122:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextComplexBuiltinsFactory.PyComplex_RealAsDoubleFactory.create();
            case 123:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextComplexBuiltinsFactory.PyTruffleComplex_AsCComplexFactory.create();
            case 124:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextContextBuiltinsFactory.PyContextVar_NewFactory.create();
            case 125:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextContextBuiltinsFactory.PyContextVar_SetFactory.create();
            case 126:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextContextBuiltinsFactory.PyTruffleContextVar_GetFactory.create();
            case 127:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDescrBuiltinsFactory.PyDictProxy_NewFactory.create();
            case 128:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDescrBuiltinsFactory.PyTruffleDescr_NewClassMethodFactory.create();
            case 129:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDescrBuiltinsFactory.PyTruffleDescr_NewGetSetFactory.create();
            case 130:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDictBuiltinsFactory.PyDict_ClearFactory.create();
            case 131:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDictBuiltinsFactory.PyDict_ContainsFactory.create();
            case 132:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDictBuiltinsFactory.PyDict_CopyFactory.create();
            case 133:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDictBuiltinsFactory.PyDict_DelItemFactory.create();
            case 134:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDictBuiltinsFactory.PyDict_GetItemFactory.create();
            case 135:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDictBuiltinsFactory.PyDict_GetItemWithErrorFactory.create();
            case 136:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDictBuiltinsFactory.PyDict_KeysFactory.create();
            case 137:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDictBuiltinsFactory.PyDict_MergeFactory.create();
            case 138:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDictBuiltinsFactory.PyDict_NewFactory.create();
            case 139:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDictBuiltinsFactory.PyDict_SetDefaultFactory.create();
            case 140:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDictBuiltinsFactory.PyDict_SetItemFactory.create();
            case 141:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDictBuiltinsFactory.PyDict_SizeFactory.create();
            case 142:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDictBuiltinsFactory.PyDict_UpdateFactory.create();
            case 143:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDictBuiltinsFactory.PyDict_ValuesFactory.create();
            case 144:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDictBuiltinsFactory.PyTruffleDict_NextFactory.create();
            case 145:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDictBuiltinsFactory._PyDict_PopFactory.create();
            case 146:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextDictBuiltinsFactory._PyDict_SetItem_KnownHashFactory.create();
            case 147:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextErrBuiltinsFactory.PyErr_DisplayFactory.create();
            case 148:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextErrBuiltinsFactory.PyErr_GivenExceptionMatchesFactory.create();
            case 149:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextErrBuiltinsFactory.PyErr_NewExceptionFactory.create();
            case 150:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextErrBuiltinsFactory.PyErr_NewExceptionWithDocFactory.create();
            case 151:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextErrBuiltinsFactory.PyErr_OccurredFactory.create();
            case 152:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextErrBuiltinsFactory.PyErr_PrintExFactory.create();
            case 153:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextErrBuiltinsFactory.PyErr_RestoreFactory.create();
            case 154:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextErrBuiltinsFactory.PyErr_SetExcInfoFactory.create();
            case 155:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextErrBuiltinsFactory.PyException_GetContextFactory.create();
            case 156:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextErrBuiltinsFactory.PyException_SetCauseFactory.create();
            case 157:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextErrBuiltinsFactory.PyException_SetContextFactory.create();
            case 158:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextErrBuiltinsFactory.PyException_SetTracebackFactory.create();
            case 159:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextErrBuiltinsFactory.PyTruffleErr_FetchFactory.create();
            case 160:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextErrBuiltinsFactory.PyTruffleErr_GetExcInfoFactory.create();
            case 161:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextErrBuiltinsFactory._PyErr_BadInternalCallFactory.create();
            case 162:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextErrBuiltinsFactory._PyErr_CreateAndSetExceptionFactory.create();
            case 163:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextErrBuiltinsFactory._PyErr_WriteUnraisableMsgFactory.create();
            case 164:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextFileBuiltinsFactory.PyFile_WriteObjectFactory.create();
            case 165:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextFloatBuiltinsFactory.PyFloat_AsDoubleFactory.create();
            case 166:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextFloatBuiltinsFactory.PyFloat_FromDoubleFactory.create();
            case 167:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextFuncBuiltinsFactory.PyClassMethod_NewFactory.create();
            case 168:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextFuncBuiltinsFactory.PyStaticMethod_NewFactory.create();
            case 169:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextGenericAliasBuiltinsFactory.Py_GenericAliasFactory.create();
            case 170:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextHashBuiltinsFactory.PyTruffleHash_InitSecretFactory.create();
            case 171:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextHashBuiltinsFactory.PyTruffle_HashConstantFactory.create();
            case 172:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextHashBuiltinsFactory._PyTruffle_HashBytesFactory.create();
            case 173:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextHashBuiltinsFactory._Py_HashDoubleFactory.create();
            case 174:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextImportBuiltinsFactory.PyImport_GetModuleDictFactory.create();
            case 175:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextImportBuiltinsFactory.PyImport_ImportModuleFactory.create();
            case 176:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextImportBuiltinsFactory.PyImport_ImportModuleFactory.create();
            case 177:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextImportBuiltinsFactory.PyImport_ImportModuleFactory.create();
            case 178:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextImportBuiltinsFactory.PyImport_ImportModuleLevelObjectFactory.create();
            case 179:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextIterBuiltinsFactory.PyCallIter_NewFactory.create();
            case 180:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextIterBuiltinsFactory.PySeqIter_NewFactory.create();
            case 181:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextListBuiltinsFactory.PyList_AppendFactory.create();
            case 182:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextListBuiltinsFactory.PyList_AsTupleFactory.create();
            case 183:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextListBuiltinsFactory.PyList_GetItemFactory.create();
            case 184:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextListBuiltinsFactory.PyList_GetSliceFactory.create();
            case 185:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextListBuiltinsFactory.PyList_InsertFactory.create();
            case 186:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextListBuiltinsFactory.PyList_NewFactory.create();
            case 187:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextListBuiltinsFactory.PyList_ReverseFactory.create();
            case 188:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextListBuiltinsFactory.PyList_SetItemFactory.create();
            case 189:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextListBuiltinsFactory.PyList_SetSliceFactory.create();
            case 190:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextListBuiltinsFactory.PyList_SizeFactory.create();
            case 191:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextListBuiltinsFactory.PyList_SortFactory.create();
            case 192:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextListBuiltinsFactory._PyList_ExtendFactory.create();
            case 193:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextLongBuiltinsFactory.PyLong_AsVoidPtrFactory.create();
            case 194:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextLongBuiltinsFactory.PyLong_FromDoubleFactory.create();
            case 195:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextLongBuiltinsFactory.PyLong_FromLongLongFactory.create();
            case 196:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextLongBuiltinsFactory.PyLong_FromLongLongFactory.create();
            case 197:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextLongBuiltinsFactory.PyLong_FromLongLongFactory.create();
            case 198:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextLongBuiltinsFactory.PyLong_FromLongLongFactory.create();
            case 199:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextLongBuiltinsFactory.PyLong_FromUnsignedLongLongFactory.create();
            case 200:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextLongBuiltinsFactory.PyLong_FromUnsignedLongLongFactory.create();
            case 201:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextLongBuiltinsFactory.PyTruffleLong_AsPrimitiveFactory.create();
            case 202:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextLongBuiltinsFactory.PyTruffleLong_FromStringFactory.create();
            case 203:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextLongBuiltinsFactory.PyTruffleLong_OneFactory.create();
            case 204:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextLongBuiltinsFactory.PyTruffleLong_ZeroFactory.create();
            case 205:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextLongBuiltinsFactory._PyLong_SignFactory.create();
            case 206:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextMemoryViewBuiltinsFactory.PyMemoryView_FromObjectFactory.create();
            case 207:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextMemoryViewBuiltinsFactory.PyMemoryView_GetContiguousFactory.create();
            case 208:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextMethodBuiltinsFactory.PyTruffleCMethod_NewExFactory.create();
            case 209:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextModuleBuiltinsFactory.PyModule_AddIntConstantFactory.create();
            case 210:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextModuleBuiltinsFactory.PyModule_AddObjectRefFactory.create();
            case 211:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextModuleBuiltinsFactory.PyModule_GetNameObjectFactory.create();
            case 212:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextModuleBuiltinsFactory.PyModule_NewObjectFactory.create();
            case 213:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextModuleBuiltinsFactory.PyModule_NewObjectFactory.create();
            case 214:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextModuleBuiltinsFactory.PyModule_SetDocStringFactory.create();
            case 215:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextModuleBuiltinsFactory._PyTruffleModule_CreateInitialized_PyModule_NewFactory.create();
            case 216:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextModuleBuiltinsFactory._PyTruffleModule_GetAndIncMaxModuleNumberFactory.create();
            case 217:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextNamespaceBuiltinsFactory._PyNamespace_NewFactory.create();
            case 218:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyCallable_CheckFactory.create();
            case 219:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyObject_ASCIIFactory.create();
            case 220:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyObject_AsFileDescriptorFactory.create();
            case 221:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyObject_BytesFactory.create();
            case 222:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyObject_DelItemFactory.create();
            case 223:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyObject_DirFactory.create();
            case 224:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyObject_FormatFactory.create();
            case 225:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyObject_GetIterFactory.create();
            case 226:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyObject_HasAttrFactory.create();
            case 227:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyObject_HasAttrFactory.create();
            case 228:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyObject_HashFactory.create();
            case 229:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyObject_HashNotImplementedFactory.create();
            case 230:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyObject_IsInstanceFactory.create();
            case 231:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyObject_IsSubclassFactory.create();
            case 232:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyObject_IsTrueFactory.create();
            case 233:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyObject_ReprFactory.create();
            case 234:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyObject_RichCompareFactory.create();
            case 235:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyObject_SetItemFactory.create();
            case 236:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyObject_StrFactory.create();
            case 237:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyObject_TypeFactory.create();
            case 238:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyTruffleObject_CallFunctionObjArgsFactory.create();
            case 239:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyTruffleObject_CallMethodObjArgsFactory.create();
            case 240:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyTruffleObject_GenericGetAttrFactory.create();
            case 241:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyTruffleObject_GenericSetAttrFactory.create();
            case 242:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyTruffle_NoValueFactory.create();
            case 243:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyTruffle_NoneFactory.create();
            case 244:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory.PyTruffle_NotImplementedFactory.create();
            case 245:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory._PyObject_Call1Factory.create();
            case 246:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory._PyObject_CallMethod1Factory.create();
            case 247:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory._PyObject_DumpFactory.create();
            case 248:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextObjectBuiltinsFactory._PyTruffleObject_MakeTpCallFactory.create();
            case 249:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextPosixmoduleBuiltinsFactory.PyOS_FSPathFactory.create();
            case 250:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextPyLifecycleBuiltinsFactory.Py_AtExitFactory.create();
            case 251:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextPyStateBuiltinsFactory.PyThreadState_GetFactory.create();
            case 252:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextPyStateBuiltinsFactory.PyTruffleState_FindModuleFactory.create();
            case 253:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextPythonRunBuiltinsFactory.PyRun_StringFlagsFactory.create();
            case 254:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSetBuiltinsFactory.PyFrozenSet_NewFactory.create();
            case 255:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSetBuiltinsFactory.PySet_AddFactory.create();
            case 256:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSetBuiltinsFactory.PySet_ClearFactory.create();
            case 257:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSetBuiltinsFactory.PySet_ContainsFactory.create();
            case 258:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSetBuiltinsFactory.PySet_DiscardFactory.create();
            case 259:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSetBuiltinsFactory.PySet_NewFactory.create();
            case 260:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSetBuiltinsFactory.PySet_PopFactory.create();
            case 261:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSetBuiltinsFactory.PySet_SizeFactory.create();
            case 262:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSetBuiltinsFactory._PyTruffleSet_NextEntryFactory.create();
            case 263:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSliceBuiltinsFactory.PySlice_NewFactory.create();
            case 264:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSliceBuiltinsFactory.PyTruffle_EllipsisFactory.create();
            case 265:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrFactory.create();
            case 266:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrFactory.create();
            case 267:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrFactory.create();
            case 268:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrFactory.create();
            case 269:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrFactory.create();
            case 270:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrFactory.create();
            case 271:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrFactory.create();
            case 272:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrFactory.create();
            case 273:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrFactory.create();
            case 274:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrFactory.create();
            case 275:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrFactory.create();
            case 276:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrFactory.create();
            case 277:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrFactory.create();
            case 278:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrFactory.create();
            case 279:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrFactory.create();
            case 280:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrFactory.create();
            case 281:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrFactory.create();
            case 282:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrFactory.create();
            case 283:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrFactory.create();
            case 284:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrFactory.create();
            case 285:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrFactory.create();
            case 286:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrFactory.create();
            case 287:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPtrFactory.create();
            case 288:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPyPtrFactory.create();
            case 289:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPyPtrFactory.create();
            case 290:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PyGetSlotDummyPyPtrFactory.create();
            case 291:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PySetSlotDummyPtrFactory.create();
            case 292:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PySetSlotDummyPtrFactory.create();
            case 293:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PySetSlotDummyPtrFactory.create();
            case 294:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PySetSlotDummyPtrFactory.create();
            case 295:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PySetSlotDummyPtrFactory.create();
            case 296:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PySetSlotDummyPtrFactory.create();
            case 297:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PySetSlotDummyPtrFactory.create();
            case 298:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PySetSlotDummyPtrFactory.create();
            case 299:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PySetSlotDummyPtrFactory.create();
            case 300:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PySetSlotDummyPtrFactory.create();
            case 301:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PySetSlotDummyPtrFactory.create();
            case 302:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PySetSlotDummyPtrFactory.create();
            case 303:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PySetSlotDummyPtrFactory.create();
            case 304:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PySetSlotDummyPtrFactory.create();
            case 305:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.PySetSlotDummyPtrFactory.create();
            case 306:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PSequence_ob_itemFactory.create();
            case 307:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PSequence_ob_itemFactory.create();
            case 308:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyASCIIObject_lengthFactory.create();
            case 309:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyASCIIObject_state_asciiFactory.create();
            case 310:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyASCIIObject_state_compactFactory.create();
            case 311:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyASCIIObject_state_kindFactory.create();
            case 312:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyASCIIObject_state_readyFactory.create();
            case 313:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyASCIIObject_wstrFactory.create();
            case 314:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyByteArrayObject_ob_exportsFactory.create();
            case 315:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyByteArrayObject_ob_startFactory.create();
            case 316:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyCFunctionObject_m_mlFactory.create();
            case 317:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyCFunctionObject_m_moduleFactory.create();
            case 318:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyCFunctionObject_m_selfFactory.create();
            case 319:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyCFunctionObject_m_weakreflistFactory.create();
            case 320:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyCFunctionObject_vectorcallFactory.create();
            case 321:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyCMethodObject_mm_classFactory.create();
            case 322:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyCompactUnicodeObject_wstr_lengthFactory.create();
            case 323:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyDescrObject_d_nameFactory.create();
            case 324:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyDescrObject_d_typeFactory.create();
            case 325:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyFrameObject_f_linenoFactory.create();
            case 326:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyGetSetDef_closureFactory.create();
            case 327:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyGetSetDef_docFactory.create();
            case 328:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyGetSetDef_getFactory.create();
            case 329:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyGetSetDef_nameFactory.create();
            case 330:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyGetSetDef_setFactory.create();
            case 331:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyInstanceMethodObject_funcFactory.create();
            case 332:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyLongObject_ob_digitFactory.create();
            case 333:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyMappingMethods_mp_ass_subscriptFactory.create();
            case 334:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyMappingMethods_mp_lengthFactory.create();
            case 335:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyMappingMethods_mp_subscriptFactory.create();
            case 336:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyMethodDef_ml_docFactory.create();
            case 337:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyMethodDef_ml_flagsFactory.create();
            case 338:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyMethodDef_ml_methFactory.create();
            case 339:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyMethodDef_ml_nameFactory.create();
            case 340:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyMethodDescrObject_d_methodFactory.create();
            case 341:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyMethodObject_im_funcFactory.create();
            case 342:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyMethodObject_im_selfFactory.create();
            case 343:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyModuleDef_m_docFactory.create();
            case 344:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyModuleDef_m_methodsFactory.create();
            case 345:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyModuleDef_m_nameFactory.create();
            case 346:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyModuleDef_m_sizeFactory.create();
            case 347:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyModuleObject_md_defFactory.create();
            case 348:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyModuleObject_md_dictFactory.create();
            case 349:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyModuleObject_md_stateFactory.create();
            case 350:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_absoluteFactory.create();
            case 351:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_addFactory.create();
            case 352:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_andFactory.create();
            case 353:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_boolFactory.create();
            case 354:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_divmodFactory.create();
            case 355:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_floatFactory.create();
            case 356:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_floor_divideFactory.create();
            case 357:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_indexFactory.create();
            case 358:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_inplace_addFactory.create();
            case 359:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_inplace_andFactory.create();
            case 360:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_inplace_floor_divideFactory.create();
            case 361:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_inplace_lshiftFactory.create();
            case 362:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_inplace_multiplyFactory.create();
            case 363:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_inplace_orFactory.create();
            case 364:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_inplace_powerFactory.create();
            case 365:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_inplace_remainderFactory.create();
            case 366:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_inplace_rshiftFactory.create();
            case 367:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_inplace_subtractFactory.create();
            case 368:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_inplace_true_divideFactory.create();
            case 369:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_inplace_xorFactory.create();
            case 370:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_intFactory.create();
            case 371:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_invertFactory.create();
            case 372:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_lshiftFactory.create();
            case 373:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_multiplyFactory.create();
            case 374:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_negativeFactory.create();
            case 375:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_orFactory.create();
            case 376:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_positiveFactory.create();
            case 377:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_powerFactory.create();
            case 378:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_remainderFactory.create();
            case 379:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_rshiftFactory.create();
            case 380:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_subtractFactory.create();
            case 381:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_true_divideFactory.create();
            case 382:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyNumberMethods_nb_xorFactory.create();
            case 383:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyObject_ob_refcntFactory.create();
            case 384:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyObject_ob_typeFactory.create();
            case 385:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PySequenceMethods_sq_concatFactory.create();
            case 386:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PySequenceMethods_sq_itemFactory.create();
            case 387:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PySequenceMethods_sq_repeatFactory.create();
            case 388:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PySetObject_usedFactory.create();
            case 389:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PySliceObject_startFactory.create();
            case 390:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PySliceObject_stepFactory.create();
            case 391:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PySliceObject_stopFactory.create();
            case 392:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_TraverseClearFactory.create();
            case 393:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_TraverseClearFactory.create();
            case 394:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_allocFactory.create();
            case 395:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_as_asyncFactory.create();
            case 396:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_as_bufferFactory.create();
            case 397:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_as_mappingFactory.create();
            case 398:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_as_numberFactory.create();
            case 399:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_as_sequenceFactory.create();
            case 400:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_baseFactory.create();
            case 401:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_basicsizeFactory.create();
            case 402:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_callFactory.create();
            case 403:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_deallocFactory.create();
            case 404:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_delFactory.create();
            case 405:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_dictFactory.create();
            case 406:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_dictoffsetFactory.create();
            case 407:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_docFactory.create();
            case 408:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_flagsFactory.create();
            case 409:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_freeFactory.create();
            case 410:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_getattroFactory.create();
            case 411:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_hashFactory.create();
            case 412:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_initFactory.create();
            case 413:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_itemsizeFactory.create();
            case 414:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_iterFactory.create();
            case 415:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_iternextFactory.create();
            case 416:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_mroFactory.create();
            case 417:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_nameFactory.create();
            case 418:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_newFactory.create();
            case 419:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_reprFactory.create();
            case 420:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_richcompareFactory.create();
            case 421:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_setattroFactory.create();
            case 422:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_strFactory.create();
            case 423:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_subclassesFactory.create();
            case 424:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_vectorcall_offsetFactory.create();
            case 425:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_version_tagFactory.create();
            case 426:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyTypeObject_tp_weaklistoffsetFactory.create();
            case 427:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyUnicodeObject_dataFactory.create();
            case 428:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_PyVarObject_ob_sizeFactory.create();
            case 429:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_get_mmap_object_dataFactory.create();
            case 430:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_set_PyByteArrayObject_ob_exportsFactory.create();
            case 431:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_set_PyFrameObject_f_linenoFactory.create();
            case 432:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_set_PyModuleObject_md_defFactory.create();
            case 433:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_set_PyModuleObject_md_stateFactory.create();
            case 434:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_set_PyObject_ob_refcntFactory.create();
            case 435:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_set_PyTypeObject_tp_allocFactory.create();
            case 436:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_set_PyTypeObject_tp_as_bufferFactory.create();
            case 437:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_set_PyTypeObject_tp_basicsizeFactory.create();
            case 438:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_set_PyTypeObject_tp_deallocFactory.create();
            case 439:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_set_PyTypeObject_tp_dictFactory.create();
            case 440:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_set_PyTypeObject_tp_dictoffsetFactory.create();
            case 441:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_set_PyTypeObject_tp_flagsFactory.create();
            case 442:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_set_PyTypeObject_tp_freeFactory.create();
            case 443:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_set_PyTypeObject_tp_itemsizeFactory.create();
            case 444:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_set_PyTypeObject_tp_subclassesFactory.create();
            case 445:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSlotBuiltinsFactory.Py_set_PyTypeObject_tp_vectorcall_offsetFactory.create();
            case 446:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextStructSeqBuiltinsFactory.PyStructSequence_NewFactory.create();
            case 447:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextStructSeqBuiltinsFactory.PyTruffleStructSequence_InitType2Factory.create();
            case 448:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextStructSeqBuiltinsFactory.PyTruffleStructSequence_NewTypeFactory.create();
            case 449:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextSysBuiltinsFactory.PySys_GetObjectFactory.create();
            case 450:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextTracebackBuiltinsFactory.PyTraceBack_HereFactory.create();
            case 451:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextTracebackBuiltinsFactory._PyTraceback_AddFactory.create();
            case 452:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextTupleBuiltinsFactory.PyTuple_GetItemFactory.create();
            case 453:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextTupleBuiltinsFactory.PyTuple_GetSliceFactory.create();
            case 454:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextTupleBuiltinsFactory.PyTuple_NewFactory.create();
            case 455:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextTupleBuiltinsFactory.PyTuple_SetItemFactory.create();
            case 456:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextTupleBuiltinsFactory.PyTuple_SizeFactory.create();
            case 457:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextTypeBuiltinsFactory.PyType_IsSubtypeFactory.create();
            case 458:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextTypeBuiltinsFactory._PyType_LookupFactory.create();
            case 459:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyTruffleUnicode_DecodeFactory.create();
            case 460:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyTruffleUnicode_DecodeUTF8StatefulFactory.create();
            case 461:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyTruffleUnicode_InternInPlaceFactory.create();
            case 462:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyTruffleUnicode_NewFactory.create();
            case 463:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyTruffle_Unicode_AsUnicodeAndSizeFactory.create();
            case 464:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyTruffle_Unicode_AsWideCharFactory.create();
            case 465:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyTruffle_Unicode_DecodeUTF32Factory.create();
            case 466:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyTruffle_Unicode_FromWcharFactory.create();
            case 467:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyUnicode_AsEncodedStringFactory.create();
            case 468:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyUnicode_AsUnicodeEscapeStringFactory.create();
            case 469:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyUnicode_CompareFactory.create();
            case 470:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyUnicode_CompareFactory.create();
            case 471:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyUnicode_ConcatFactory.create();
            case 472:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyUnicode_ContainsFactory.create();
            case 473:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyUnicode_DecodeFSDefaultFactory.create();
            case 474:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyUnicode_EncodeFSDefaultFactory.create();
            case 475:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyUnicode_FindCharFactory.create();
            case 476:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyUnicode_FormatFactory.create();
            case 477:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyUnicode_FromEncodedObjectFactory.create();
            case 478:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyUnicode_FromObjectFactory.create();
            case 479:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyUnicode_FromOrdinalFactory.create();
            case 480:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyUnicode_FromStringFactory.create();
            case 481:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyUnicode_GetLengthFactory.create();
            case 482:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyUnicode_JoinFactory.create();
            case 483:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyUnicode_ReadCharFactory.create();
            case 484:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyUnicode_ReplaceFactory.create();
            case 485:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyUnicode_SplitFactory.create();
            case 486:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyUnicode_SubstringFactory.create();
            case 487:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory.PyUnicode_TailmatchFactory.create();
            case 488:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory._PyUnicode_AsASCIIStringFactory.create();
            case 489:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory._PyUnicode_AsLatin1StringFactory.create();
            case 490:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextUnicodeBuiltinsFactory._PyUnicode_AsUTF8StringFactory.create();
            case 491:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextWarnBuiltinsFactory._PyTruffleErr_WarnFactory.create();
            case 492:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextWeakrefBuiltinsFactory.PyObject_ClearWeakRefsFactory.create();
            case 493:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextWeakrefBuiltinsFactory.PyWeakref_GetObjectFactory.create();
            case 494:
                return com.oracle.graal.python.builtins.modules.cext.PythonCextWeakrefBuiltinsFactory.PyWeakref_NewRefFactory.create();
        }
        return null;
    }

    static final CApiBuiltinExecutable[] builtins = {
                    new CApiBuiltinExecutable("PyIndex_Check", null, null, null, 0),
                    new CApiBuiltinExecutable("PyIter_Next", null, null, null, 1),
                    new CApiBuiltinExecutable("PyMapping_Check", null, null, null, 2),
                    new CApiBuiltinExecutable("PyMapping_Items", null, null, null, 3),
                    new CApiBuiltinExecutable("PyMapping_Keys", null, null, null, 4),
                    new CApiBuiltinExecutable("PyMapping_Size", null, null, null, 5),
                    new CApiBuiltinExecutable("PyMapping_Values", null, null, null, 6),
                    new CApiBuiltinExecutable("PyNumber_Absolute", null, null, null, 7),
                    new CApiBuiltinExecutable("PyNumber_Check", null, null, null, 8),
                    new CApiBuiltinExecutable("PyNumber_Divmod", null, null, null, 9),
                    new CApiBuiltinExecutable("PyNumber_Float", null, null, null, 10),
                    new CApiBuiltinExecutable("PyNumber_InPlacePower", null, null, null, 11),
                    new CApiBuiltinExecutable("_PyNumber_Index", null, null, null, 12),
                    new CApiBuiltinExecutable("PyNumber_Index", null, null, null, 13),
                    new CApiBuiltinExecutable("PyNumber_Long", null, null, null, 14),
                    new CApiBuiltinExecutable("PyNumber_Power", null, null, null, 15),
                    new CApiBuiltinExecutable("PyNumber_ToBase", null, null, null, 16),
                    new CApiBuiltinExecutable("PyObject_GetDoc", null, null, null, 17),
                    new CApiBuiltinExecutable("PyObject_GetItem", null, null, null, 18),
                    new CApiBuiltinExecutable("PyTruffleObject_GetItemString", null, null, null, 19),
                    new CApiBuiltinExecutable("PyObject_LengthHint", null, null, null, 20),
                    new CApiBuiltinExecutable("PyObject_SetDoc", null, null, null, 21),
                    new CApiBuiltinExecutable("PyObject_Size", null, null, null, 22),
                    new CApiBuiltinExecutable("PySequence_Check", null, null, null, 23),
                    new CApiBuiltinExecutable("PySequence_Concat", null, null, null, 24),
                    new CApiBuiltinExecutable("PySequence_Contains", null, null, null, 25),
                    new CApiBuiltinExecutable("PySequence_DelItem", null, null, null, 26),
                    new CApiBuiltinExecutable("PySequence_GetItem", null, null, null, 27),
                    new CApiBuiltinExecutable("PySequence_GetSlice", null, null, null, 28),
                    new CApiBuiltinExecutable("PySequence_InPlaceConcat", null, null, null, 29),
                    new CApiBuiltinExecutable("PySequence_InPlaceRepeat", null, null, null, 30),
                    new CApiBuiltinExecutable("PySequence_List", null, null, null, 31),
                    new CApiBuiltinExecutable("PySequence_Repeat", null, null, null, 32),
                    new CApiBuiltinExecutable("PySequence_SetItem", null, null, null, 33),
                    new CApiBuiltinExecutable("PySequence_Length", null, null, null, 34),
                    new CApiBuiltinExecutable("PySequence_Size", null, null, null, 35),
                    new CApiBuiltinExecutable("PySequence_Tuple", null, null, null, 36),
                    new CApiBuiltinExecutable("PyTruffleNumber_BinOp", null, null, null, 37),
                    new CApiBuiltinExecutable("PyTruffleNumber_InPlaceBinOp", null, null, null, 38),
                    new CApiBuiltinExecutable("PyTruffleNumber_UnaryOp", null, null, null, 39),
                    new CApiBuiltinExecutable("PyTruffle_False", null, null, null, 40),
                    new CApiBuiltinExecutable("PyTruffle_True", null, null, null, 41),
                    new CApiBuiltinExecutable("PyEval_RestoreThread", null, null, null, 42),
                    new CApiBuiltinExecutable("PyEval_SaveThread", null, null, null, 43),
                    new CApiBuiltinExecutable("PyFrame_New", null, null, null, 44),
                    new CApiBuiltinExecutable("PyGILState_Ensure", null, null, null, 45),
                    new CApiBuiltinExecutable("PyGILState_Release", null, null, null, 46),
                    new CApiBuiltinExecutable("PyObject_GC_Track", null, null, null, 47),
                    new CApiBuiltinExecutable("PyObject_GC_UnTrack", null, null, null, 48),
                    new CApiBuiltinExecutable("PyTraceMalloc_Track", null, null, null, 49),
                    new CApiBuiltinExecutable("PyTraceMalloc_Untrack", null, null, null, 50),
                    new CApiBuiltinExecutable("PyTruffleModule_AddFunctionToModule", null, null, null, 51),
                    new CApiBuiltinExecutable("PyTruffleToCharPointer", null, null, null, 52),
                    new CApiBuiltinExecutable("PyTruffleType_AddFunctionToType", null, null, null, 53),
                    new CApiBuiltinExecutable("PyTruffleType_AddGetSet", null, null, null, 54),
                    new CApiBuiltinExecutable("PyTruffleType_AddMember", null, null, null, 55),
                    new CApiBuiltinExecutable("PyTruffleType_AddSlot", null, null, null, 56),
                    new CApiBuiltinExecutable("PyTruffle_Arg_ParseTupleAndKeywords", null, null, null, 57),
                    new CApiBuiltinExecutable("PyTruffle_ByteArray_EmptyWithCapacity", null, null, null, 58),
                    new CApiBuiltinExecutable("PyTruffle_Bytes_AsString", null, null, null, 59),
                    new CApiBuiltinExecutable("PyTruffle_Bytes_CheckEmbeddedNull", null, null, null, 60),
                    new CApiBuiltinExecutable("PyTruffle_Bytes_EmptyWithCapacity", null, null, null, 61),
                    new CApiBuiltinExecutable("PyTruffle_Compute_Mro", null, null, null, 62),
                    new CApiBuiltinExecutable("PyTruffle_Debug", null, null, null, 63),
                    new CApiBuiltinExecutable("PyTruffle_DebugTrace", null, null, null, 64),
                    new CApiBuiltinExecutable("PyTruffle_FatalErrorFunc", null, null, null, 65),
                    new CApiBuiltinExecutable("PyTruffle_FileSystemDefaultEncoding", null, null, null, 66),
                    new CApiBuiltinExecutable("PyTruffle_Get_Inherited_Native_Slots", null, null, null, 67),
                    new CApiBuiltinExecutable("PyTruffle_LogString", null, null, null, 68),
                    new CApiBuiltinExecutable("PyTruffle_MemoryViewFromBuffer", null, null, null, 69),
                    new CApiBuiltinExecutable("PyTruffle_Native_Options", null, null, null, 70),
                    new CApiBuiltinExecutable("PyTruffle_NewTypeDict", null, null, null, 71),
                    new CApiBuiltinExecutable("PyTruffle_OS_DoubleToString", null, null, null, 72),
                    new CApiBuiltinExecutable("PyTruffle_OS_StringToDouble", null, null, null, 73),
                    new CApiBuiltinExecutable("PyTruffle_Object_Alloc", null, null, null, 74),
                    new CApiBuiltinExecutable("PyTruffle_Object_Free", null, null, null, 75),
                    new CApiBuiltinExecutable("PyTruffle_Register_NULL", null, null, null, 76),
                    new CApiBuiltinExecutable("PyTruffle_Set_Native_Slots", null, null, null, 77),
                    new CApiBuiltinExecutable("PyTruffle_Set_SulongType", null, null, null, 78),
                    new CApiBuiltinExecutable("PyTruffle_ToNative", null, null, null, 79),
                    new CApiBuiltinExecutable("PyTruffle_Trace_Type", null, null, null, 80),
                    new CApiBuiltinExecutable("PyTruffle_Type", null, null, null, 81),
                    new CApiBuiltinExecutable("PyTruffle_Type_Modified", null, null, null, 82),
                    new CApiBuiltinExecutable("PyTruffle_Unicode_FromFormat", null, null, null, 83),
                    new CApiBuiltinExecutable("PyTruffle_tss_create", null, null, null, 84),
                    new CApiBuiltinExecutable("PyTruffle_tss_delete", null, null, null, 85),
                    new CApiBuiltinExecutable("PyTruffle_tss_get", null, null, null, 86),
                    new CApiBuiltinExecutable("PyTruffle_tss_set", null, null, null, 87),
                    new CApiBuiltinExecutable("_PyTraceMalloc_NewReference", null, null, null, 88),
                    new CApiBuiltinExecutable("_PyTruffle_Trace_Free", null, null, null, 89),
                    new CApiBuiltinExecutable("PyBytes_FromObject", null, null, null, 90),
                    new CApiBuiltinExecutable("PyBytes_Size", null, null, null, 91),
                    new CApiBuiltinExecutable("PyTruffleByteArray_FromStringAndSize", null, null, null, 92),
                    new CApiBuiltinExecutable("PyTruffleBytes_Concat", null, null, null, 93),
                    new CApiBuiltinExecutable("PyTruffleBytes_FromFormat", null, null, null, 94),
                    new CApiBuiltinExecutable("PyTruffleBytes_FromStringAndSize", null, null, null, 95),
                    new CApiBuiltinExecutable("_PyBytes_Join", null, null, null, 96),
                    new CApiBuiltinExecutable("PyByteArray_Resize", null, null, null, 97),
                    new CApiBuiltinExecutable("_PyTruffleBytes_Resize", null, null, null, 98),
                    new CApiBuiltinExecutable("PyCapsule_GetContext", null, null, null, 99),
                    new CApiBuiltinExecutable("PyCapsule_GetDestructor", null, null, null, 100),
                    new CApiBuiltinExecutable("PyCapsule_GetName", null, null, null, 101),
                    new CApiBuiltinExecutable("PyCapsule_GetPointer", null, null, null, 102),
                    new CApiBuiltinExecutable("PyCapsule_Import", null, null, null, 103),
                    new CApiBuiltinExecutable("PyCapsule_IsValid", null, null, null, 104),
                    new CApiBuiltinExecutable("PyCapsule_New", null, null, null, 105),
                    new CApiBuiltinExecutable("PyCapsule_SetContext", null, null, null, 106),
                    new CApiBuiltinExecutable("PyCapsule_SetDestructor", null, null, null, 107),
                    new CApiBuiltinExecutable("PyCapsule_SetName", null, null, null, 108),
                    new CApiBuiltinExecutable("PyCapsule_SetPointer", null, null, null, 109),
                    new CApiBuiltinExecutable("PyEval_GetBuiltins", null, null, null, 110),
                    new CApiBuiltinExecutable("PyThread_acquire_lock", null, null, null, 111),
                    new CApiBuiltinExecutable("PyThread_allocate_lock", null, null, null, 112),
                    new CApiBuiltinExecutable("PyThread_release_lock", null, null, null, 113),
                    new CApiBuiltinExecutable("_PyTruffleEval_EvalCodeEx", null, null, null, 114),
                    new CApiBuiltinExecutable("PyInstanceMethod_New", null, null, null, 115),
                    new CApiBuiltinExecutable("PyMethod_New", null, null, null, 116),
                    new CApiBuiltinExecutable("PyCode_New", null, null, null, 117),
                    new CApiBuiltinExecutable("PyCode_NewEmpty", null, null, null, 118),
                    new CApiBuiltinExecutable("PyCode_NewWithPosOnlyArgs", null, null, null, 119),
                    new CApiBuiltinExecutable("PyComplex_FromDoubles", null, null, null, 120),
                    new CApiBuiltinExecutable("PyComplex_ImagAsDouble", null, null, null, 121),
                    new CApiBuiltinExecutable("PyComplex_RealAsDouble", null, null, null, 122),
                    new CApiBuiltinExecutable("PyTruffleComplex_AsCComplex", null, null, null, 123),
                    new CApiBuiltinExecutable("PyContextVar_New", null, null, null, 124),
                    new CApiBuiltinExecutable("PyContextVar_Set", null, null, null, 125),
                    new CApiBuiltinExecutable("PyTruffleContextVar_Get", null, null, null, 126),
                    new CApiBuiltinExecutable("PyDictProxy_New", null, null, null, 127),
                    new CApiBuiltinExecutable("PyTruffleDescr_NewClassMethod", null, null, null, 128),
                    new CApiBuiltinExecutable("PyTruffleDescr_NewGetSet", null, null, null, 129),
                    new CApiBuiltinExecutable("PyDict_Clear", null, null, null, 130),
                    new CApiBuiltinExecutable("PyDict_Contains", null, null, null, 131),
                    new CApiBuiltinExecutable("PyDict_Copy", null, null, null, 132),
                    new CApiBuiltinExecutable("PyDict_DelItem", null, null, null, 133),
                    new CApiBuiltinExecutable("PyDict_GetItem", null, null, null, 134),
                    new CApiBuiltinExecutable("PyDict_GetItemWithError", null, null, null, 135),
                    new CApiBuiltinExecutable("PyDict_Keys", null, null, null, 136),
                    new CApiBuiltinExecutable("PyDict_Merge", null, null, null, 137),
                    new CApiBuiltinExecutable("PyDict_New", null, null, null, 138),
                    new CApiBuiltinExecutable("PyDict_SetDefault", null, null, null, 139),
                    new CApiBuiltinExecutable("PyDict_SetItem", null, null, null, 140),
                    new CApiBuiltinExecutable("PyDict_Size", null, null, null, 141),
                    new CApiBuiltinExecutable("PyDict_Update", null, null, null, 142),
                    new CApiBuiltinExecutable("PyDict_Values", null, null, null, 143),
                    new CApiBuiltinExecutable("PyTruffleDict_Next", null, null, null, 144),
                    new CApiBuiltinExecutable("_PyDict_Pop", null, null, null, 145),
                    new CApiBuiltinExecutable("_PyDict_SetItem_KnownHash", null, null, null, 146),
                    new CApiBuiltinExecutable("PyErr_Display", null, null, null, 147),
                    new CApiBuiltinExecutable("PyErr_GivenExceptionMatches", null, null, null, 148),
                    new CApiBuiltinExecutable("PyErr_NewException", null, null, null, 149),
                    new CApiBuiltinExecutable("PyErr_NewExceptionWithDoc", null, null, null, 150),
                    new CApiBuiltinExecutable("PyErr_Occurred", null, null, null, 151),
                    new CApiBuiltinExecutable("PyErr_PrintEx", null, null, null, 152),
                    new CApiBuiltinExecutable("PyErr_Restore", null, null, null, 153),
                    new CApiBuiltinExecutable("PyErr_SetExcInfo", null, null, null, 154),
                    new CApiBuiltinExecutable("PyException_GetContext", null, null, null, 155),
                    new CApiBuiltinExecutable("PyException_SetCause", null, null, null, 156),
                    new CApiBuiltinExecutable("PyException_SetContext", null, null, null, 157),
                    new CApiBuiltinExecutable("PyException_SetTraceback", null, null, null, 158),
                    new CApiBuiltinExecutable("PyTruffleErr_Fetch", null, null, null, 159),
                    new CApiBuiltinExecutable("PyTruffleErr_GetExcInfo", null, null, null, 160),
                    new CApiBuiltinExecutable("_PyErr_BadInternalCall", null, null, null, 161),
                    new CApiBuiltinExecutable("_PyErr_CreateAndSetException", null, null, null, 162),
                    new CApiBuiltinExecutable("_PyErr_WriteUnraisableMsg", null, null, null, 163),
                    new CApiBuiltinExecutable("PyFile_WriteObject", null, null, null, 164),
                    new CApiBuiltinExecutable("PyFloat_AsDouble", null, null, null, 165),
                    new CApiBuiltinExecutable("PyFloat_FromDouble", null, null, null, 166),
                    new CApiBuiltinExecutable("PyClassMethod_New", null, null, null, 167),
                    new CApiBuiltinExecutable("PyStaticMethod_New", null, null, null, 168),
                    new CApiBuiltinExecutable("Py_GenericAlias", null, null, null, 169),
                    new CApiBuiltinExecutable("PyTruffleHash_InitSecret", null, null, null, 170),
                    new CApiBuiltinExecutable("PyTruffle_HashConstant", null, null, null, 171),
                    new CApiBuiltinExecutable("_PyTruffle_HashBytes", null, null, null, 172),
                    new CApiBuiltinExecutable("_Py_HashDouble", null, null, null, 173),
                    new CApiBuiltinExecutable("PyImport_GetModuleDict", null, null, null, 174),
                    new CApiBuiltinExecutable("PyImport_ImportModule", null, null, null, 175),
                    new CApiBuiltinExecutable("PyImport_Import", null, null, null, 176),
                    new CApiBuiltinExecutable("PyImport_ImportModuleNoBlock", null, null, null, 177),
                    new CApiBuiltinExecutable("PyImport_ImportModuleLevelObject", null, null, null, 178),
                    new CApiBuiltinExecutable("PyCallIter_New", null, null, null, 179),
                    new CApiBuiltinExecutable("PySeqIter_New", null, null, null, 180),
                    new CApiBuiltinExecutable("PyList_Append", null, null, null, 181),
                    new CApiBuiltinExecutable("PyList_AsTuple", null, null, null, 182),
                    new CApiBuiltinExecutable("PyList_GetItem", null, null, null, 183),
                    new CApiBuiltinExecutable("PyList_GetSlice", null, null, null, 184),
                    new CApiBuiltinExecutable("PyList_Insert", null, null, null, 185),
                    new CApiBuiltinExecutable("PyList_New", null, null, null, 186),
                    new CApiBuiltinExecutable("PyList_Reverse", null, null, null, 187),
                    new CApiBuiltinExecutable("PyList_SetItem", null, null, null, 188),
                    new CApiBuiltinExecutable("PyList_SetSlice", null, null, null, 189),
                    new CApiBuiltinExecutable("PyList_Size", null, null, null, 190),
                    new CApiBuiltinExecutable("PyList_Sort", null, null, null, 191),
                    new CApiBuiltinExecutable("_PyList_Extend", null, null, null, 192),
                    new CApiBuiltinExecutable("PyLong_AsVoidPtr", null, null, null, 193),
                    new CApiBuiltinExecutable("PyLong_FromDouble", null, null, null, 194),
                    new CApiBuiltinExecutable("PyLong_FromSsize_t", null, null, null, 195),
                    new CApiBuiltinExecutable("PyLong_FromSize_t", null, null, null, 196),
                    new CApiBuiltinExecutable("PyLong_FromLong", null, null, null, 197),
                    new CApiBuiltinExecutable("PyLong_FromLongLong", null, null, null, 198),
                    new CApiBuiltinExecutable("PyLong_FromUnsignedLong", null, null, null, 199),
                    new CApiBuiltinExecutable("PyLong_FromUnsignedLongLong", null, null, null, 200),
                    new CApiBuiltinExecutable("PyTruffleLong_AsPrimitive", null, null, null, 201),
                    new CApiBuiltinExecutable("PyTruffleLong_FromString", null, null, null, 202),
                    new CApiBuiltinExecutable("PyTruffleLong_One", null, null, null, 203),
                    new CApiBuiltinExecutable("PyTruffleLong_Zero", null, null, null, 204),
                    new CApiBuiltinExecutable("_PyLong_Sign", null, null, null, 205),
                    new CApiBuiltinExecutable("PyMemoryView_FromObject", null, null, null, 206),
                    new CApiBuiltinExecutable("PyMemoryView_GetContiguous", null, null, null, 207),
                    new CApiBuiltinExecutable("PyTruffleCMethod_NewEx", null, null, null, 208),
                    new CApiBuiltinExecutable("PyModule_AddIntConstant", null, null, null, 209),
                    new CApiBuiltinExecutable("PyModule_AddObjectRef", null, null, null, 210),
                    new CApiBuiltinExecutable("PyModule_GetNameObject", null, null, null, 211),
                    new CApiBuiltinExecutable("PyModule_NewObject", null, null, null, 212),
                    new CApiBuiltinExecutable("PyModule_New", null, null, null, 213),
                    new CApiBuiltinExecutable("PyModule_SetDocString", null, null, null, 214),
                    new CApiBuiltinExecutable("_PyTruffleModule_CreateInitialized_PyModule_New", null, null, null, 215),
                    new CApiBuiltinExecutable("_PyTruffleModule_GetAndIncMaxModuleNumber", null, null, null, 216),
                    new CApiBuiltinExecutable("_PyNamespace_New", null, null, null, 217),
                    new CApiBuiltinExecutable("PyCallable_Check", null, null, null, 218),
                    new CApiBuiltinExecutable("PyObject_ASCII", null, null, null, 219),
                    new CApiBuiltinExecutable("PyObject_AsFileDescriptor", null, null, null, 220),
                    new CApiBuiltinExecutable("PyObject_Bytes", null, null, null, 221),
                    new CApiBuiltinExecutable("PyObject_DelItem", null, null, null, 222),
                    new CApiBuiltinExecutable("PyObject_Dir", null, null, null, 223),
                    new CApiBuiltinExecutable("PyObject_Format", null, null, null, 224),
                    new CApiBuiltinExecutable("PyObject_GetIter", null, null, null, 225),
                    new CApiBuiltinExecutable("PyObject_HasAttr", null, null, null, 226),
                    new CApiBuiltinExecutable("PyObject_HasAttrString", null, null, null, 227),
                    new CApiBuiltinExecutable("PyObject_Hash", null, null, null, 228),
                    new CApiBuiltinExecutable("PyObject_HashNotImplemented", null, null, null, 229),
                    new CApiBuiltinExecutable("PyObject_IsInstance", null, null, null, 230),
                    new CApiBuiltinExecutable("PyObject_IsSubclass", null, null, null, 231),
                    new CApiBuiltinExecutable("PyObject_IsTrue", null, null, null, 232),
                    new CApiBuiltinExecutable("PyObject_Repr", null, null, null, 233),
                    new CApiBuiltinExecutable("PyObject_RichCompare", null, null, null, 234),
                    new CApiBuiltinExecutable("PyObject_SetItem", null, null, null, 235),
                    new CApiBuiltinExecutable("PyObject_Str", null, null, null, 236),
                    new CApiBuiltinExecutable("PyObject_Type", null, null, null, 237),
                    new CApiBuiltinExecutable("PyTruffleObject_CallFunctionObjArgs", null, null, null, 238),
                    new CApiBuiltinExecutable("PyTruffleObject_CallMethodObjArgs", null, null, null, 239),
                    new CApiBuiltinExecutable("PyTruffleObject_GenericGetAttr", null, null, null, 240),
                    new CApiBuiltinExecutable("PyTruffleObject_GenericSetAttr", null, null, null, 241),
                    new CApiBuiltinExecutable("PyTruffle_NoValue", null, null, null, 242),
                    new CApiBuiltinExecutable("PyTruffle_None", null, null, null, 243),
                    new CApiBuiltinExecutable("PyTruffle_NotImplemented", null, null, null, 244),
                    new CApiBuiltinExecutable("_PyObject_Call1", null, null, null, 245),
                    new CApiBuiltinExecutable("_PyObject_CallMethod1", null, null, null, 246),
                    new CApiBuiltinExecutable("_PyObject_Dump", null, null, null, 247),
                    new CApiBuiltinExecutable("_PyTruffleObject_MakeTpCall", null, null, null, 248),
                    new CApiBuiltinExecutable("PyOS_FSPath", null, null, null, 249),
                    new CApiBuiltinExecutable("Py_AtExit", null, null, null, 250),
                    new CApiBuiltinExecutable("PyThreadState_Get", null, null, null, 251),
                    new CApiBuiltinExecutable("PyTruffleState_FindModule", null, null, null, 252),
                    new CApiBuiltinExecutable("PyRun_StringFlags", null, null, null, 253),
                    new CApiBuiltinExecutable("PyFrozenSet_New", null, null, null, 254),
                    new CApiBuiltinExecutable("PySet_Add", null, null, null, 255),
                    new CApiBuiltinExecutable("PySet_Clear", null, null, null, 256),
                    new CApiBuiltinExecutable("PySet_Contains", null, null, null, 257),
                    new CApiBuiltinExecutable("PySet_Discard", null, null, null, 258),
                    new CApiBuiltinExecutable("PySet_New", null, null, null, 259),
                    new CApiBuiltinExecutable("PySet_Pop", null, null, null, 260),
                    new CApiBuiltinExecutable("PySet_Size", null, null, null, 261),
                    new CApiBuiltinExecutable("_PyTruffleSet_NextEntry", null, null, null, 262),
                    new CApiBuiltinExecutable("PySlice_New", null, null, null, 263),
                    new CApiBuiltinExecutable("PyTruffle_Ellipsis", null, null, null, 264),
                    new CApiBuiltinExecutable("Py_get_dummy", null, null, null, 265),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_setattr", null, null, null, 266),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_getattr", null, null, null, 267),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_methods", null, null, null, 268),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_members", null, null, null, 269),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_getset", null, null, null, 270),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_descr_get", null, null, null, 271),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_descr_set", null, null, null, 272),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_is_gc", null, null, null, 273),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_finalize", null, null, null, 274),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_vectorcall", null, null, null, 275),
                    new CApiBuiltinExecutable("Py_get_PySequenceMethods_sq_length", null, null, null, 276),
                    new CApiBuiltinExecutable("Py_get_PySequenceMethods_sq_ass_item", null, null, null, 277),
                    new CApiBuiltinExecutable("Py_get_PySequenceMethods_sq_contains", null, null, null, 278),
                    new CApiBuiltinExecutable("Py_get_PySequenceMethods_sq_inplace_concat", null, null, null, 279),
                    new CApiBuiltinExecutable("Py_get_PySequenceMethods_sq_inplace_repeat", null, null, null, 280),
                    new CApiBuiltinExecutable("Py_get_PyAsyncMethods_am_await", null, null, null, 281),
                    new CApiBuiltinExecutable("Py_get_PyAsyncMethods_am_aiter", null, null, null, 282),
                    new CApiBuiltinExecutable("Py_get_PyAsyncMethods_am_anext", null, null, null, 283),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_matrix_multiply", null, null, null, 284),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_inplace_matrix_multiply", null, null, null, 285),
                    new CApiBuiltinExecutable("Py_get_PyBufferProcs_bf_getbuffer", null, null, null, 286),
                    new CApiBuiltinExecutable("Py_get_PyBufferProcs_bf_releasebuffer", null, null, null, 287),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_bases", null, null, null, 288),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_cache", null, null, null, 289),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_weaklist", null, null, null, 290),
                    new CApiBuiltinExecutable("Py_set_PyVarObject_ob_size", null, null, null, 291),
                    new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_getattr", null, null, null, 292),
                    new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_getattro", null, null, null, 293),
                    new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_setattr", null, null, null, 294),
                    new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_setattro", null, null, null, 295),
                    new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_finalize", null, null, null, 296),
                    new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_iter", null, null, null, 297),
                    new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_iternext", null, null, null, 298),
                    new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_base", null, null, null, 299),
                    new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_bases", null, null, null, 300),
                    new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_clear", null, null, null, 301),
                    new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_mro", null, null, null, 302),
                    new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_new", null, null, null, 303),
                    new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_traverse", null, null, null, 304),
                    new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_weaklistoffset", null, null, null, 305),
                    new CApiBuiltinExecutable("Py_get_PyListObject_ob_item", null, null, null, 306),
                    new CApiBuiltinExecutable("Py_get_PyTupleObject_ob_item", null, null, null, 307),
                    new CApiBuiltinExecutable("Py_get_PyASCIIObject_length", null, null, null, 308),
                    new CApiBuiltinExecutable("Py_get_PyASCIIObject_state_ascii", null, null, null, 309),
                    new CApiBuiltinExecutable("Py_get_PyASCIIObject_state_compact", null, null, null, 310),
                    new CApiBuiltinExecutable("Py_get_PyASCIIObject_state_kind", null, null, null, 311),
                    new CApiBuiltinExecutable("Py_get_PyASCIIObject_state_ready", null, null, null, 312),
                    new CApiBuiltinExecutable("Py_get_PyASCIIObject_wstr", null, null, null, 313),
                    new CApiBuiltinExecutable("Py_get_PyByteArrayObject_ob_exports", null, null, null, 314),
                    new CApiBuiltinExecutable("Py_get_PyByteArrayObject_ob_start", null, null, null, 315),
                    new CApiBuiltinExecutable("Py_get_PyCFunctionObject_m_ml", null, null, null, 316),
                    new CApiBuiltinExecutable("Py_get_PyCFunctionObject_m_module", null, null, null, 317),
                    new CApiBuiltinExecutable("Py_get_PyCFunctionObject_m_self", null, null, null, 318),
                    new CApiBuiltinExecutable("Py_get_PyCFunctionObject_m_weakreflist", null, null, null, 319),
                    new CApiBuiltinExecutable("Py_get_PyCFunctionObject_vectorcall", null, null, null, 320),
                    new CApiBuiltinExecutable("Py_get_PyCMethodObject_mm_class", null, null, null, 321),
                    new CApiBuiltinExecutable("Py_get_PyCompactUnicodeObject_wstr_length", null, null, null, 322),
                    new CApiBuiltinExecutable("Py_get_PyDescrObject_d_name", null, null, null, 323),
                    new CApiBuiltinExecutable("Py_get_PyDescrObject_d_type", null, null, null, 324),
                    new CApiBuiltinExecutable("Py_get_PyFrameObject_f_lineno", null, null, null, 325),
                    new CApiBuiltinExecutable("Py_get_PyGetSetDef_closure", null, null, null, 326),
                    new CApiBuiltinExecutable("Py_get_PyGetSetDef_doc", null, null, null, 327),
                    new CApiBuiltinExecutable("Py_get_PyGetSetDef_get", null, null, null, 328),
                    new CApiBuiltinExecutable("Py_get_PyGetSetDef_name", null, null, null, 329),
                    new CApiBuiltinExecutable("Py_get_PyGetSetDef_set", null, null, null, 330),
                    new CApiBuiltinExecutable("Py_get_PyInstanceMethodObject_func", null, null, null, 331),
                    new CApiBuiltinExecutable("Py_get_PyLongObject_ob_digit", null, null, null, 332),
                    new CApiBuiltinExecutable("Py_get_PyMappingMethods_mp_ass_subscript", null, null, null, 333),
                    new CApiBuiltinExecutable("Py_get_PyMappingMethods_mp_length", null, null, null, 334),
                    new CApiBuiltinExecutable("Py_get_PyMappingMethods_mp_subscript", null, null, null, 335),
                    new CApiBuiltinExecutable("Py_get_PyMethodDef_ml_doc", null, null, null, 336),
                    new CApiBuiltinExecutable("Py_get_PyMethodDef_ml_flags", null, null, null, 337),
                    new CApiBuiltinExecutable("Py_get_PyMethodDef_ml_meth", null, null, null, 338),
                    new CApiBuiltinExecutable("Py_get_PyMethodDef_ml_name", null, null, null, 339),
                    new CApiBuiltinExecutable("Py_get_PyMethodDescrObject_d_method", null, null, null, 340),
                    new CApiBuiltinExecutable("Py_get_PyMethodObject_im_func", null, null, null, 341),
                    new CApiBuiltinExecutable("Py_get_PyMethodObject_im_self", null, null, null, 342),
                    new CApiBuiltinExecutable("Py_get_PyModuleDef_m_doc", null, null, null, 343),
                    new CApiBuiltinExecutable("Py_get_PyModuleDef_m_methods", null, null, null, 344),
                    new CApiBuiltinExecutable("Py_get_PyModuleDef_m_name", null, null, null, 345),
                    new CApiBuiltinExecutable("Py_get_PyModuleDef_m_size", null, null, null, 346),
                    new CApiBuiltinExecutable("Py_get_PyModuleObject_md_def", null, null, null, 347),
                    new CApiBuiltinExecutable("Py_get_PyModuleObject_md_dict", null, null, null, 348),
                    new CApiBuiltinExecutable("Py_get_PyModuleObject_md_state", null, null, null, 349),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_absolute", null, null, null, 350),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_add", null, null, null, 351),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_and", null, null, null, 352),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_bool", null, null, null, 353),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_divmod", null, null, null, 354),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_float", null, null, null, 355),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_floor_divide", null, null, null, 356),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_index", null, null, null, 357),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_inplace_add", null, null, null, 358),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_inplace_and", null, null, null, 359),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_inplace_floor_divide", null, null, null, 360),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_inplace_lshift", null, null, null, 361),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_inplace_multiply", null, null, null, 362),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_inplace_or", null, null, null, 363),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_inplace_power", null, null, null, 364),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_inplace_remainder", null, null, null, 365),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_inplace_rshift", null, null, null, 366),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_inplace_subtract", null, null, null, 367),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_inplace_true_divide", null, null, null, 368),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_inplace_xor", null, null, null, 369),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_int", null, null, null, 370),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_invert", null, null, null, 371),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_lshift", null, null, null, 372),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_multiply", null, null, null, 373),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_negative", null, null, null, 374),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_or", null, null, null, 375),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_positive", null, null, null, 376),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_power", null, null, null, 377),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_remainder", null, null, null, 378),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_rshift", null, null, null, 379),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_subtract", null, null, null, 380),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_true_divide", null, null, null, 381),
                    new CApiBuiltinExecutable("Py_get_PyNumberMethods_nb_xor", null, null, null, 382),
                    new CApiBuiltinExecutable("Py_get_PyObject_ob_refcnt", null, null, null, 383),
                    new CApiBuiltinExecutable("Py_get_PyObject_ob_type", null, null, null, 384),
                    new CApiBuiltinExecutable("Py_get_PySequenceMethods_sq_concat", null, null, null, 385),
                    new CApiBuiltinExecutable("Py_get_PySequenceMethods_sq_item", null, null, null, 386),
                    new CApiBuiltinExecutable("Py_get_PySequenceMethods_sq_repeat", null, null, null, 387),
                    new CApiBuiltinExecutable("Py_get_PySetObject_used", null, null, null, 388),
                    new CApiBuiltinExecutable("Py_get_PySliceObject_start", null, null, null, 389),
                    new CApiBuiltinExecutable("Py_get_PySliceObject_step", null, null, null, 390),
                    new CApiBuiltinExecutable("Py_get_PySliceObject_stop", null, null, null, 391),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_traverse", null, null, null, 392),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_clear", null, null, null, 393),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_alloc", null, null, null, 394),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_as_async", null, null, null, 395),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_as_buffer", null, null, null, 396),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_as_mapping", null, null, null, 397),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_as_number", null, null, null, 398),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_as_sequence", null, null, null, 399),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_base", null, null, null, 400),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_basicsize", null, null, null, 401),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_call", null, null, null, 402),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_dealloc", null, null, null, 403),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_del", null, null, null, 404),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_dict", null, null, null, 405),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_dictoffset", null, null, null, 406),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_doc", null, null, null, 407),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_flags", null, null, null, 408),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_free", null, null, null, 409),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_getattro", null, null, null, 410),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_hash", null, null, null, 411),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_init", null, null, null, 412),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_itemsize", null, null, null, 413),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_iter", null, null, null, 414),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_iternext", null, null, null, 415),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_mro", null, null, null, 416),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_name", null, null, null, 417),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_new", null, null, null, 418),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_repr", null, null, null, 419),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_richcompare", null, null, null, 420),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_setattro", null, null, null, 421),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_str", null, null, null, 422),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_subclasses", null, null, null, 423),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_vectorcall_offset", null, null, null, 424),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_version_tag", null, null, null, 425),
                    new CApiBuiltinExecutable("Py_get_PyTypeObject_tp_weaklistoffset", null, null, null, 426),
                    new CApiBuiltinExecutable("Py_get_PyUnicodeObject_data", null, null, null, 427),
                    new CApiBuiltinExecutable("Py_get_PyVarObject_ob_size", null, null, null, 428),
                    new CApiBuiltinExecutable("Py_get_mmap_object_data", null, null, null, 429),
                    new CApiBuiltinExecutable("Py_set_PyByteArrayObject_ob_exports", null, null, null, 430),
                    new CApiBuiltinExecutable("Py_set_PyFrameObject_f_lineno", null, null, null, 431),
                    new CApiBuiltinExecutable("Py_set_PyModuleObject_md_def", null, null, null, 432),
                    new CApiBuiltinExecutable("Py_set_PyModuleObject_md_state", null, null, null, 433),
                    new CApiBuiltinExecutable("Py_set_PyObject_ob_refcnt", null, null, null, 434),
                    new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_alloc", null, null, null, 435),
                    new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_as_buffer", null, null, null, 436),
                    new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_basicsize", null, null, null, 437),
                    new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_dealloc", null, null, null, 438),
                    new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_dict", null, null, null, 439),
                    new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_dictoffset", null, null, null, 440),
                    new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_flags", null, null, null, 441),
                    new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_free", null, null, null, 442),
                    new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_itemsize", null, null, null, 443),
                    new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_subclasses", null, null, null, 444),
                    new CApiBuiltinExecutable("Py_set_PyTypeObject_tp_vectorcall_offset", null, null, null, 445),
                    new CApiBuiltinExecutable("PyStructSequence_New", null, null, null, 446),
                    new CApiBuiltinExecutable("PyTruffleStructSequence_InitType2", null, null, null, 447),
                    new CApiBuiltinExecutable("PyTruffleStructSequence_NewType", null, null, null, 448),
                    new CApiBuiltinExecutable("PySys_GetObject", null, null, null, 449),
                    new CApiBuiltinExecutable("PyTraceBack_Here", null, null, null, 450),
                    new CApiBuiltinExecutable("_PyTraceback_Add", null, null, null, 451),
                    new CApiBuiltinExecutable("PyTuple_GetItem", null, null, null, 452),
                    new CApiBuiltinExecutable("PyTuple_GetSlice", null, null, null, 453),
                    new CApiBuiltinExecutable("PyTuple_New", null, null, null, 454),
                    new CApiBuiltinExecutable("PyTuple_SetItem", null, null, null, 455),
                    new CApiBuiltinExecutable("PyTuple_Size", null, null, null, 456),
                    new CApiBuiltinExecutable("PyType_IsSubtype", null, null, null, 457),
                    new CApiBuiltinExecutable("_PyType_Lookup", null, null, null, 458),
                    new CApiBuiltinExecutable("PyTruffleUnicode_Decode", null, null, null, 459),
                    new CApiBuiltinExecutable("PyTruffleUnicode_DecodeUTF8Stateful", null, null, null, 460),
                    new CApiBuiltinExecutable("PyTruffleUnicode_InternInPlace", null, null, null, 461),
                    new CApiBuiltinExecutable("PyTruffleUnicode_New", null, null, null, 462),
                    new CApiBuiltinExecutable("PyTruffle_Unicode_AsUnicodeAndSize", null, null, null, 463),
                    new CApiBuiltinExecutable("PyTruffle_Unicode_AsWideChar", null, null, null, 464),
                    new CApiBuiltinExecutable("PyTruffle_Unicode_DecodeUTF32", null, null, null, 465),
                    new CApiBuiltinExecutable("PyTruffle_Unicode_FromWchar", null, null, null, 466),
                    new CApiBuiltinExecutable("PyUnicode_AsEncodedString", null, null, null, 467),
                    new CApiBuiltinExecutable("PyUnicode_AsUnicodeEscapeString", null, null, null, 468),
                    new CApiBuiltinExecutable("_PyUnicode_EqualToASCIIString", null, null, null, 469),
                    new CApiBuiltinExecutable("PyUnicode_Compare", null, null, null, 470),
                    new CApiBuiltinExecutable("PyUnicode_Concat", null, null, null, 471),
                    new CApiBuiltinExecutable("PyUnicode_Contains", null, null, null, 472),
                    new CApiBuiltinExecutable("PyUnicode_DecodeFSDefault", null, null, null, 473),
                    new CApiBuiltinExecutable("PyUnicode_EncodeFSDefault", null, null, null, 474),
                    new CApiBuiltinExecutable("PyUnicode_FindChar", null, null, null, 475),
                    new CApiBuiltinExecutable("PyUnicode_Format", null, null, null, 476),
                    new CApiBuiltinExecutable("PyUnicode_FromEncodedObject", null, null, null, 477),
                    new CApiBuiltinExecutable("PyUnicode_FromObject", null, null, null, 478),
                    new CApiBuiltinExecutable("PyUnicode_FromOrdinal", null, null, null, 479),
                    new CApiBuiltinExecutable("PyUnicode_FromString", null, null, null, 480),
                    new CApiBuiltinExecutable("PyUnicode_GetLength", null, null, null, 481),
                    new CApiBuiltinExecutable("PyUnicode_Join", null, null, null, 482),
                    new CApiBuiltinExecutable("PyUnicode_ReadChar", null, null, null, 483),
                    new CApiBuiltinExecutable("PyUnicode_Replace", null, null, null, 484),
                    new CApiBuiltinExecutable("PyUnicode_Split", null, null, null, 485),
                    new CApiBuiltinExecutable("PyUnicode_Substring", null, null, null, 486),
                    new CApiBuiltinExecutable("PyUnicode_Tailmatch", null, null, null, 487),
                    new CApiBuiltinExecutable("_PyUnicode_AsASCIIString", null, null, null, 488),
                    new CApiBuiltinExecutable("_PyUnicode_AsLatin1String", null, null, null, 489),
                    new CApiBuiltinExecutable("_PyUnicode_AsUTF8String", null, null, null, 490),
                    new CApiBuiltinExecutable("_PyTruffleErr_Warn", null, null, null, 491),
                    new CApiBuiltinExecutable("PyObject_ClearWeakRefs", null, null, null, 492),
                    new CApiBuiltinExecutable("PyWeakref_GetObject", null, null, null, 493),
                    new CApiBuiltinExecutable("PyWeakref_NewRef", null, null, null, 494),
    };
    // {{end CAPI_BUILTINS}}

}
