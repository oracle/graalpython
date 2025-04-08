# Defines parameters for the code generation

# Handwritten trampolines:
NO_TRAMPOLINES = {
    '_HPy_New',
    'HPy_FatalError',
}

# Generated trampoline returns given constant,
# but the context function is void
RETURN_CONSTANT = {
    'HPyErr_SetString': 'HPy_NULL',
    'HPyErr_SetObject': 'HPy_NULL',
    'HPyErr_SetFromErrnoWithFilenameObjects': 'HPy_NULL',
    'HPyErr_NoMemory': 'HPy_NULL'
}

# If the HPy function delegates to C Python API of a different name or, in the
# case of None, if the HPy function is implemented by hand
SPECIAL_CASES = {
    'HPy_Dup': None,
    'HPy_Close': None,
    'HPyField_Load': None,
    'HPyField_Store': None,
    'HPyModule_Create': None,
    'HPy_GetAttr': 'PyObject_GetAttr',
    'HPy_GetAttr_s': 'PyObject_GetAttrString',
    'HPy_HasAttr': 'PyObject_HasAttr',
    'HPy_HasAttr_s': 'PyObject_HasAttrString',
    'HPy_SetAttr': 'PyObject_SetAttr',
    'HPy_SetAttr_s': 'PyObject_SetAttrString',
    'HPy_GetIter': 'PyObject_GetIter',
    'HPy_GetItem': 'PyObject_GetItem',
    'HPy_GetItem_i': None,
    'HPy_GetItem_s': None,
    'HPy_GetSlice': 'PySequence_GetSlice',
    'HPy_SetItem': 'PyObject_SetItem',
    'HPy_SetItem_i': None,
    'HPy_SetItem_s': None,
    'HPy_SetSlice': 'PySequence_SetSlice',
    'HPy_DelItem': 'PyObject_DelItem',
    'HPy_DelItem_i': None,
    'HPy_DelItem_s': None,
    'HPy_DelSlice': 'PySequence_DelSlice',
    'HPy_Contains': 'PySequence_Contains',
    'HPy_Length': 'PyObject_Length',
    'HPy_CallTupleDict': None,
    'HPy_Call': None, # 'PyObject_Vectorcall', no auto arg conversion
    'HPy_CallMethod': None, # 'PyObject_VectorcallMethod',no auto arg conversion
    'HPy_FromPyObject': None,
    'HPy_AsPyObject': None,
    '_HPy_AsStruct_Object': None,
    '_HPy_AsStruct_Type': None,
    '_HPy_AsStruct_Long': None,
    '_HPy_AsStruct_Float': None,
    '_HPy_AsStruct_Unicode': None,
    '_HPy_AsStruct_Tuple': None,
    '_HPy_AsStruct_List': None,
    '_HPy_AsStruct_Dict': None,
    '_HPy_AsStruct_Legacy': None,
    '_HPyType_GetBuiltinShape': None,
    '_HPy_CallRealFunctionFromTrampoline': None,
    '_HPy_CallDestroyAndThenDealloc': None,
    'HPyErr_Occurred': None,
    'HPy_FatalError': None,
    'HPy_Add': 'PyNumber_Add',
    'HPy_Subtract': 'PyNumber_Subtract',
    'HPy_Multiply': 'PyNumber_Multiply',
    'HPy_MatrixMultiply': 'PyNumber_MatrixMultiply',
    'HPy_FloorDivide': 'PyNumber_FloorDivide',
    'HPy_TrueDivide': 'PyNumber_TrueDivide',
    'HPy_Remainder': 'PyNumber_Remainder',
    'HPy_Divmod': 'PyNumber_Divmod',
    'HPy_Power': 'PyNumber_Power',
    'HPy_Negative': 'PyNumber_Negative',
    'HPy_Positive': 'PyNumber_Positive',
    'HPy_Absolute': 'PyNumber_Absolute',
    'HPy_Invert': 'PyNumber_Invert',
    'HPy_Lshift': 'PyNumber_Lshift',
    'HPy_Rshift': 'PyNumber_Rshift',
    'HPy_And': 'PyNumber_And',
    'HPy_Xor': 'PyNumber_Xor',
    'HPy_Or': 'PyNumber_Or',
    'HPy_Index': 'PyNumber_Index',
    'HPy_Long': 'PyNumber_Long',
    'HPy_Float': 'PyNumber_Float',
    'HPy_InPlaceAdd': 'PyNumber_InPlaceAdd',
    'HPy_InPlaceSubtract': 'PyNumber_InPlaceSubtract',
    'HPy_InPlaceMultiply': 'PyNumber_InPlaceMultiply',
    'HPy_InPlaceMatrixMultiply': 'PyNumber_InPlaceMatrixMultiply',
    'HPy_InPlaceFloorDivide': 'PyNumber_InPlaceFloorDivide',
    'HPy_InPlaceTrueDivide': 'PyNumber_InPlaceTrueDivide',
    'HPy_InPlaceRemainder': 'PyNumber_InPlaceRemainder',
    'HPy_InPlacePower': 'PyNumber_InPlacePower',
    'HPy_InPlaceLshift': 'PyNumber_InPlaceLshift',
    'HPy_InPlaceRshift': 'PyNumber_InPlaceRshift',
    'HPy_InPlaceAnd': 'PyNumber_InPlaceAnd',
    'HPy_InPlaceXor': 'PyNumber_InPlaceXor',
    'HPy_InPlaceOr': 'PyNumber_InPlaceOr',
    '_HPy_New': None,
    'HPyType_FromSpec': None,
    'HPyType_GenericNew': None,
    'HPy_Repr': 'PyObject_Repr',
    'HPy_Str': 'PyObject_Str',
    'HPy_ASCII': 'PyObject_ASCII',
    'HPy_Bytes': 'PyObject_Bytes',
    'HPy_IsTrue': 'PyObject_IsTrue',
    'HPy_RichCompare': 'PyObject_RichCompare',
    'HPy_RichCompareBool': 'PyObject_RichCompareBool',
    'HPy_Hash': 'PyObject_Hash',
    'HPyIter_Next': 'PyIter_Next',
    'HPyIter_Check': 'PyIter_Check',
    'HPyListBuilder_New': None,
    'HPyListBuilder_Set': None,
    'HPyListBuilder_Build': None,
    'HPyListBuilder_Cancel': None,
    'HPyTuple_FromArray': None,
    'HPyTupleBuilder_New': None,
    'HPyTupleBuilder_Set': None,
    'HPyTupleBuilder_Build': None,
    'HPyTupleBuilder_Cancel': None,
    'HPyTracker_New': None,
    'HPyTracker_Add': None,
    'HPyTracker_ForgetAll': None,
    'HPyTracker_Close': None,
    '_HPy_Dump': None,
    'HPy_Type': None,
    'HPy_TypeCheck': None,
    'HPy_Is': None,
    'HPyBytes_FromStringAndSize': None,
    'HPy_LeavePythonExecution': 'PyEval_SaveThread',
    'HPy_ReenterPythonExecution': 'PyEval_RestoreThread',
    'HPyGlobal_Load': None,
    'HPyGlobal_Store': None,
    'HPyCapsule_New': None,
    'HPyCapsule_Get': None,
    'HPyCapsule_Set': None,
    'HPyLong_FromInt32_t': None,
    'HPyLong_FromUInt32_t': None,
    'HPyLong_FromInt64_t': None,
    'HPyLong_FromUInt64_t': None,
    'HPyLong_AsInt32_t': None,
    'HPyLong_AsUInt32_t': None,
    'HPyLong_AsUInt32_tMask': None,
    'HPyLong_AsInt64_t': None,
    'HPyLong_AsUInt64_t': None,
    'HPyLong_AsUInt64_tMask': None,
    'HPyBool_FromBool': 'PyBool_FromLong',
    'HPy_Compile_s': None,
    'HPy_EvalCode': 'PyEval_EvalCode',
    'HPyContextVar_Get': None,
    'HPyType_GetName': None,
    'HPyType_IsSubtype': None,
    'HPy_SetCallFunction': None,
}

################################################################################
#                    Configuration for auto-generating docs                    #
################################################################################

# A manual mapping of between CPython C API functions and HPy API functions.
# Most of the mapping will be generated automatically from 'public_api.h' if an
# HPy API function is not a special case (see 'conf.py'). However, in some
# cases, it might be that we have inline helper functions or something similar
# that map to a CPython C API function which cannot be determined automatically.
# In those cases, the mapping can be manually specified here. Also, manual
# mapping will always take precedence over automatically derived mappings.
DOC_MANUAL_API_MAPPING = {
    # key = C API function name
    # value = HPy API function name
    'Py_FatalError': 'HPy_FatalError',
    'PyContextVar_Get': 'HPyContextVar_Get',
    'PyLong_FromLong': 'HPyLong_FromLong',
    'PyLong_FromLongLong': 'HPyLong_FromLongLong',
    'PyLong_FromUnsignedLong': 'HPyLong_FromUnsignedLong',
    'PyLong_FromUnsignedLongLong': 'HPyLong_FromUnsignedLongLong',
    'PyLong_AsLong': 'HPyLong_AsLong',
    'PyLong_AsLongLong': 'HPyLong_AsLongLong',
    'PyLong_AsUnsignedLong': 'HPyLong_AsUnsignedLong',
    'PyLong_AsUnsignedLongMask': 'HPyLong_AsUnsignedLongMask',
    'PyLong_AsUnsignedLongLong': 'HPyLong_AsUnsignedLongLong',
    'PyLong_AsUnsignedLongLongMask': 'HPyLong_AsUnsignedLongLongMask',
    'PyBool_FromLong': 'HPyBool_FromLong',
    'PyObject_TypeCheck': 'HPy_TypeCheck',
    'PySlice_AdjustIndices': 'HPySlice_AdjustIndices',
    'PyType_IsSubtype': 'HPyType_IsSubtype',
    'PyObject_Call': 'HPy_CallTupleDict',
    'PyObject_Type': 'HPy_Type',
    'PyObject_Vectorcall': 'HPy_Call',
    'PyObject_VectorcallMethod': 'HPy_CallMethod',
}

# Some C API functions are documented in very different pages.
DOC_C_API_PAGES_SPECIAL_CASES = {
    'Py_FatalError': 'sys',
    'PyEval_SaveThread': 'init',
    'PyEval_RestoreThread': 'init',
    'PyEval_EvalCode': 'veryhigh',
    'PyObject_Call': 'call',
    'PyObject_Vectorcall': 'call',
    'PyObject_VectorcallMethod': 'call',
}

# We assume that, e.g., prefix 'PyLong_Something' belongs to 'longobject.c' and
# its documentation is in '.../3/c-api/long.html'. In some cases, the prefix
# maps to a different page and this can be specified here. E.g.
# 'PyErr_Something' is documented in page '.../3/c-api/exceptions.html'
DOC_PREFIX_TABLE = {
    'err': 'exceptions',
    'contextvar': 'contextvars'
}