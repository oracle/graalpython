/* HPy public API */

/*
 * IMPORTANT: In order to ensure backwards compatibility of HPyContext, it is
 * necessary to define the order of the context members. To do so, use macro
 * 'HPy_ID(idx)' for context handles and functions. When adding members, it
 * doesn't matter where they are located in this file. It's just important that
 * the maximum context index is incremented by exactly one.
 */

#ifdef AUTOGEN

/* Constants */
HPy_ID(0) HPy h_None;
HPy_ID(1) HPy h_True;
HPy_ID(2) HPy h_False;
HPy_ID(3) HPy h_NotImplemented;
HPy_ID(4) HPy h_Ellipsis;

/* Exceptions */
HPy_ID(5) HPy h_BaseException;
HPy_ID(6) HPy h_Exception;
HPy_ID(7) HPy h_StopAsyncIteration;
HPy_ID(8) HPy h_StopIteration;
HPy_ID(9) HPy h_GeneratorExit;
HPy_ID(10) HPy h_ArithmeticError;
HPy_ID(11) HPy h_LookupError;
HPy_ID(12) HPy h_AssertionError;
HPy_ID(13) HPy h_AttributeError;
HPy_ID(14) HPy h_BufferError;
HPy_ID(15) HPy h_EOFError;
HPy_ID(16) HPy h_FloatingPointError;
HPy_ID(17) HPy h_OSError;
HPy_ID(18) HPy h_ImportError;
HPy_ID(19) HPy h_ModuleNotFoundError;
HPy_ID(20) HPy h_IndexError;
HPy_ID(21) HPy h_KeyError;
HPy_ID(22) HPy h_KeyboardInterrupt;
HPy_ID(23) HPy h_MemoryError;
HPy_ID(24) HPy h_NameError;
HPy_ID(25) HPy h_OverflowError;
HPy_ID(26) HPy h_RuntimeError;
HPy_ID(27) HPy h_RecursionError;
HPy_ID(28) HPy h_NotImplementedError;
HPy_ID(29) HPy h_SyntaxError;
HPy_ID(30) HPy h_IndentationError;
HPy_ID(31) HPy h_TabError;
HPy_ID(32) HPy h_ReferenceError;
HPy_ID(33) HPy h_SystemError;
HPy_ID(34) HPy h_SystemExit;
HPy_ID(35) HPy h_TypeError;
HPy_ID(36) HPy h_UnboundLocalError;
HPy_ID(37) HPy h_UnicodeError;
HPy_ID(38) HPy h_UnicodeEncodeError;
HPy_ID(39) HPy h_UnicodeDecodeError;
HPy_ID(40) HPy h_UnicodeTranslateError;
HPy_ID(41) HPy h_ValueError;
HPy_ID(42) HPy h_ZeroDivisionError;
HPy_ID(43) HPy h_BlockingIOError;
HPy_ID(44) HPy h_BrokenPipeError;
HPy_ID(45) HPy h_ChildProcessError;
HPy_ID(46) HPy h_ConnectionError;
HPy_ID(47) HPy h_ConnectionAbortedError;
HPy_ID(48) HPy h_ConnectionRefusedError;
HPy_ID(49) HPy h_ConnectionResetError;
HPy_ID(50) HPy h_FileExistsError;
HPy_ID(51) HPy h_FileNotFoundError;
HPy_ID(52) HPy h_InterruptedError;
HPy_ID(53) HPy h_IsADirectoryError;
HPy_ID(54) HPy h_NotADirectoryError;
HPy_ID(55) HPy h_PermissionError;
HPy_ID(56) HPy h_ProcessLookupError;
HPy_ID(57) HPy h_TimeoutError;
// EnvironmentError, IOError and WindowsError are intentionally omitted (they
// are all aliases of OSError since Python 3.3).

/* Warnings */
HPy_ID(58) HPy h_Warning;
HPy_ID(59) HPy h_UserWarning;
HPy_ID(60) HPy h_DeprecationWarning;
HPy_ID(61) HPy h_PendingDeprecationWarning;
HPy_ID(62) HPy h_SyntaxWarning;
HPy_ID(63) HPy h_RuntimeWarning;
HPy_ID(64) HPy h_FutureWarning;
HPy_ID(65) HPy h_ImportWarning;
HPy_ID(66) HPy h_UnicodeWarning;
HPy_ID(67) HPy h_BytesWarning;
HPy_ID(68) HPy h_ResourceWarning;

/* Types */
HPy_ID(69) HPy h_BaseObjectType;   /* built-in 'object' */
HPy_ID(70) HPy h_TypeType;         /* built-in 'type' */
HPy_ID(71) HPy h_BoolType;         /* built-in 'bool' */
HPy_ID(72) HPy h_LongType;         /* built-in 'int' */
HPy_ID(73) HPy h_FloatType;        /* built-in 'float' */
HPy_ID(74) HPy h_UnicodeType;      /* built-in 'str' */
HPy_ID(75) HPy h_TupleType;        /* built-in 'tuple' */
HPy_ID(76) HPy h_ListType;         /* built-in 'list' */
HPy_ID(238) HPy h_ComplexType;     /* built-in 'complex' */
HPy_ID(239) HPy h_BytesType;       /* built-in 'bytes' */
HPy_ID(240) HPy h_MemoryViewType;  /* built-in 'memoryview' */
HPy_ID(241) HPy h_CapsuleType;     /* built-in 'capsule' */
HPy_ID(242) HPy h_SliceType;       /* built-in 'slice' */
HPy_ID(263) HPy h_DictType;         /* built-in 'dict' */

/* Reflection */
HPy_ID(243) HPy h_Builtins;        /* dict of builtins */

#endif

HPy_ID(77)
HPy HPy_Dup(HPyContext *ctx, HPy h);
HPy_ID(78)
void HPy_Close(HPyContext *ctx, HPy h);

HPy_ID(79)
HPy HPyLong_FromInt32_t(HPyContext *ctx, int32_t value);
HPy_ID(80)
HPy HPyLong_FromUInt32_t(HPyContext *ctx, uint32_t value);
HPy_ID(81)
HPy HPyLong_FromInt64_t(HPyContext *ctx, int64_t v);
HPy_ID(82)
HPy HPyLong_FromUInt64_t(HPyContext *ctx, uint64_t v);
HPy_ID(83)
HPy HPyLong_FromSize_t(HPyContext *ctx, size_t value);
HPy_ID(84)
HPy HPyLong_FromSsize_t(HPyContext *ctx, HPy_ssize_t value);

HPy_ID(85)
int32_t HPyLong_AsInt32_t(HPyContext *ctx, HPy h);
HPy_ID(86)
uint32_t HPyLong_AsUInt32_t(HPyContext *ctx, HPy h);
HPy_ID(87)
uint32_t HPyLong_AsUInt32_tMask(HPyContext *ctx, HPy h);
HPy_ID(88)
int64_t HPyLong_AsInt64_t(HPyContext *ctx, HPy h);
HPy_ID(89)
uint64_t HPyLong_AsUInt64_t(HPyContext *ctx, HPy h);
HPy_ID(90)
uint64_t HPyLong_AsUInt64_tMask(HPyContext *ctx, HPy h);
HPy_ID(91)
size_t HPyLong_AsSize_t(HPyContext *ctx, HPy h);
HPy_ID(92)
HPy_ssize_t HPyLong_AsSsize_t(HPyContext *ctx, HPy h);
HPy_ID(93)
void* HPyLong_AsVoidPtr(HPyContext *ctx, HPy h);
HPy_ID(94)
double HPyLong_AsDouble(HPyContext *ctx, HPy h);

HPy_ID(95)
HPy HPyFloat_FromDouble(HPyContext *ctx, double v);
HPy_ID(96)
double HPyFloat_AsDouble(HPyContext *ctx, HPy h);

HPy_ID(97)
HPy HPyBool_FromBool(HPyContext *ctx, bool v);


/* abstract.h */
HPy_ID(98)
HPy_ssize_t HPy_Length(HPyContext *ctx, HPy h);

HPy_ID(99)
int HPyNumber_Check(HPyContext *ctx, HPy h);
HPy_ID(100)
HPy HPy_Add(HPyContext *ctx, HPy h1, HPy h2);
HPy_ID(101)
HPy HPy_Subtract(HPyContext *ctx, HPy h1, HPy h2);
HPy_ID(102)
HPy HPy_Multiply(HPyContext *ctx, HPy h1, HPy h2);
HPy_ID(103)
HPy HPy_MatrixMultiply(HPyContext *ctx, HPy h1, HPy h2);
HPy_ID(104)
HPy HPy_FloorDivide(HPyContext *ctx, HPy h1, HPy h2);
HPy_ID(105)
HPy HPy_TrueDivide(HPyContext *ctx, HPy h1, HPy h2);
HPy_ID(106)
HPy HPy_Remainder(HPyContext *ctx, HPy h1, HPy h2);
HPy_ID(107)
HPy HPy_Divmod(HPyContext *ctx, HPy h1, HPy h2);
HPy_ID(108)
HPy HPy_Power(HPyContext *ctx, HPy h1, HPy h2, HPy h3);
HPy_ID(109)
HPy HPy_Negative(HPyContext *ctx, HPy h1);
HPy_ID(110)
HPy HPy_Positive(HPyContext *ctx, HPy h1);
HPy_ID(111)
HPy HPy_Absolute(HPyContext *ctx, HPy h1);
HPy_ID(112)
HPy HPy_Invert(HPyContext *ctx, HPy h1);
HPy_ID(113)
HPy HPy_Lshift(HPyContext *ctx, HPy h1, HPy h2);
HPy_ID(114)
HPy HPy_Rshift(HPyContext *ctx, HPy h1, HPy h2);
HPy_ID(115)
HPy HPy_And(HPyContext *ctx, HPy h1, HPy h2);
HPy_ID(116)
HPy HPy_Xor(HPyContext *ctx, HPy h1, HPy h2);
HPy_ID(117)
HPy HPy_Or(HPyContext *ctx, HPy h1, HPy h2);
HPy_ID(118)
HPy HPy_Index(HPyContext *ctx, HPy h1);
HPy_ID(119)
HPy HPy_Long(HPyContext *ctx, HPy h1);
HPy_ID(120)
HPy HPy_Float(HPyContext *ctx, HPy h1);

HPy_ID(121)
HPy HPy_InPlaceAdd(HPyContext *ctx, HPy h1, HPy h2);
HPy_ID(122)
HPy HPy_InPlaceSubtract(HPyContext *ctx, HPy h1, HPy h2);
HPy_ID(123)
HPy HPy_InPlaceMultiply(HPyContext *ctx, HPy h1, HPy h2);
HPy_ID(124)
HPy HPy_InPlaceMatrixMultiply(HPyContext *ctx, HPy h1, HPy h2);
HPy_ID(125)
HPy HPy_InPlaceFloorDivide(HPyContext *ctx, HPy h1, HPy h2);
HPy_ID(126)
HPy HPy_InPlaceTrueDivide(HPyContext *ctx, HPy h1, HPy h2);
HPy_ID(127)
HPy HPy_InPlaceRemainder(HPyContext *ctx, HPy h1, HPy h2);
HPy_ID(128)
HPy HPy_InPlacePower(HPyContext *ctx, HPy h1, HPy h2, HPy h3);
HPy_ID(129)
HPy HPy_InPlaceLshift(HPyContext *ctx, HPy h1, HPy h2);
HPy_ID(130)
HPy HPy_InPlaceRshift(HPyContext *ctx, HPy h1, HPy h2);
HPy_ID(131)
HPy HPy_InPlaceAnd(HPyContext *ctx, HPy h1, HPy h2);
HPy_ID(132)
HPy HPy_InPlaceXor(HPyContext *ctx, HPy h1, HPy h2);
HPy_ID(133)
HPy HPy_InPlaceOr(HPyContext *ctx, HPy h1, HPy h2);

HPy_ID(134)
int HPyCallable_Check(HPyContext *ctx, HPy h);

/**
 * Call a Python object.
 *
 * :param ctx:
 *     The execution context.
 * :param callable:
 *     A handle to the Python object to call (must not be ``HPy_NULL``).
 * :param args:
 *     A handle to a tuple containing the positional arguments (must not be
 *     ``HPy_NULL`` but can, of course, be empty).
 * :param kw:
 *     A handle to a Python dictionary containing the keyword arguments (may be
 *     ``HPy_NULL``).
 *
 * :returns:
 *     The result of the call on success, or ``HPy_NULL`` in case of an error.
 */
HPy_ID(135)
HPy HPy_CallTupleDict(HPyContext *ctx, HPy callable, HPy args, HPy kw);

/**
 * Call a Python object.
 *
 * :param ctx:
 *     The execution context.
 * :param callable:
 *     A handle to the Python object to call (must not be ``HPy_NULL``).
 * :param args:
 *     A pointer to an array of positional and keyword arguments. This argument
 *     must not be ``NULL`` if ``nargs > 0`` or
 *     ``HPy_Length(ctx, kwnames) > 0``.
 * :param nargs:
 *     The number of positional arguments in ``args``.
 * :param kwnames:
 *     A handle to the tuple of keyword argument names (may be ``HPy_NULL``).
 *     The values of the keyword arguments are also passed in ``args`` appended
 *     to the positional arguments. Argument ``nargs`` does not include the
 *     keyword argument count.
 *
 * :returns:
 *     The result of the call on success, or ``HPy_NULL`` in case of an error.
 */
HPy_ID(261)
HPy HPy_Call(HPyContext *ctx, HPy callable, const HPy *args, size_t nargs, HPy kwnames);

/**
 * Call a method of a Python object.
 *
 * :param ctx:
 *     The execution context.
 * :param name:
 *     A handle to the name (a Unicode object) of the method. Must not be
 *     ``HPy_NULL``.
 * :param args:
 *     A pointer to an array of the arguments. The receiver is ``args[0]``, and
 *     the positional and keyword arguments are starting at ``args[1]``. This
 *     argument must not be ``NULL`` since a receiver is always required.
 * :param nargs:
 *     The number of positional arguments in ``args`` including the receiver at
 *     ``args[0]`` (therefore, ``nargs`` must be at least ``1``).
 * :param kwnames:
 *     A handle to the tuple of keyword argument names (may be ``HPy_NULL``).
 *     The values of the keyword arguments are also passed in ``args`` appended
 *     to the positional arguments. Argument ``nargs`` does not include the
 *     keyword argument count.
 *
 * :returns:
 *     The result of the call on success, or ``HPy_NULL`` in case of an error.
 */
HPy_ID(262)
HPy HPy_CallMethod(HPyContext *ctx, HPy name, const HPy *args, size_t nargs, HPy kwnames);

/**
 * Return a new iterator for iterable object ``obj``. This is the equivalent
 * of the Python expression ``iter(obj)``.
 *
 * :param ctx:
 *     The execution context.
 * :param obj:
 *     An iterable Python object (must not be ``HPy_NULL``). If the object is
 *     not iterable, a ``TypeError`` will be raised.
 *
 * :returns:
 *     The new iterator, ``obj`` itself if it is already an iterator, or
 *     ``HPy_NULL`` on failure.
 */
HPy_ID(269)
HPy HPy_GetIter(HPyContext *ctx, HPy obj);

/**
 * Return the next value from iterator ``obj``.
 *
 * :param ctx:
 *     The execution context.
 * :param obj:
 *     An iterator Python object (must not be ``HPy_NULL``). This can be 
 *     verified with ``HPy_IterCheck``. Otherwise, the behavior is undefined.
 *
 * :returns:
 *     The new value in iterator ``obj``, or ``HPy_NULL`` on failure. If the
 *     iterator was exhausted normally, an exception will not be set. In
 *     case of some other error, one will be set.
 */
HPy_ID(270)
HPy HPyIter_Next(HPyContext *ctx, HPy obj);

/**
 * Tests if an object is an instance of a Python iterator.
 *
 * :param ctx:
 *     The execution context.
 * :param obj:
 *     A handle to an arbitrary object (must not be ``HPy_NULL``).
 *
 * :returns:
 *     Non-zero if object ``obj`` provides the ``Iterator`` protocol, and ``0``
 *     otherwise.
 */
HPy_ID(271)
int HPyIter_Check(HPyContext *ctx, HPy obj);

/* pyerrors.h */
HPy_ID(136)
void HPy_FatalError(HPyContext *ctx, const char *message);
HPy_ID(137)
HPy HPyErr_SetString(HPyContext *ctx, HPy h_type, const char *utf8_message);
HPy_ID(138)
HPy HPyErr_SetObject(HPyContext *ctx, HPy h_type, HPy h_value);

/**
 * Similar to :c:func:`HPyErr_SetFromErrnoWithFilenameObjects` but takes one
 * filename (a C string) that will be decoded using
 * :c:func:`HPyUnicode_DecodeFSDefault`.
 *
 * :param ctx:
 *     The execution context.
 * :param h_type:
 *     The exception type to raise.
 * :param filename_fsencoded:
 *     a filename; may be ``NULL``
 *
 * :return:
 *     always returns ``HPy_NULL``
 */
HPy_ID(139)
HPy HPyErr_SetFromErrnoWithFilename(HPyContext *ctx, HPy h_type, const char *filename_fsencoded);

/**
 * A convenience function to raise an exception when a C library function has
 * returned an error and set the C variable ``errno``. It constructs an
 * instance of the provided exception type ``h_type`` by calling
 * ``h_type(errno, strerror(errno), filename1, 0, filename2)``. The exception
 * instance is then raised.
 *
 * :param ctx:
 *     The execution context.
 * :param h_type:
 *     The exception type to raise.
 * :param filename1:
 *     A filename; may be ``HPy_NULL``. In the case of ``h_type`` is the
 *     ``OSError`` exception, this is used to define the filename attribute of
 *     the exception instance.
 * :param filename2:
 *     another filename argument; may be ``HPy_NULL``
 *
 * :return:
 *     always returns ``HPy_NULL``
 */
HPy_ID(140)
HPy HPyErr_SetFromErrnoWithFilenameObjects(HPyContext *ctx, HPy h_type, HPy filename1, HPy filename2);
/* note: HPyErr_Occurred() returns a flag 0-or-1, instead of a 'PyObject *' */
HPy_ID(141)
int HPyErr_Occurred(HPyContext *ctx);
HPy_ID(142)
int HPyErr_ExceptionMatches(HPyContext *ctx, HPy exc);
HPy_ID(143)
HPy HPyErr_NoMemory(HPyContext *ctx);
HPy_ID(144)
void HPyErr_Clear(HPyContext *ctx);
HPy_ID(145)
HPy HPyErr_NewException(HPyContext *ctx, const char *utf8_name, HPy base, HPy dict);
HPy_ID(146)
HPy HPyErr_NewExceptionWithDoc(HPyContext *ctx, const char *utf8_name, const char *utf8_doc, HPy base, HPy dict);
HPy_ID(147)
int HPyErr_WarnEx(HPyContext *ctx, HPy category, const char *utf8_message, HPy_ssize_t stack_level);
HPy_ID(148)
void HPyErr_WriteUnraisable(HPyContext *ctx, HPy obj);

/* object.h */
HPy_ID(149)
int HPy_IsTrue(HPyContext *ctx, HPy h);

/**
 * Create a type from a :c:struct:`HPyType_Spec` and an additional list of
 * specification parameters.
 *
 * :param ctx:
 *     The execution context.
 * :param spec:
 *     The type spec to use to create the type.
 * :param params:
 *     A 0-terminated list of type specification parameters or ``NULL``.
 *
 * :returns: a handle of the created type on success, ``HPy_NULL`` on failure.
 */
HPy_ID(150)
HPy HPyType_FromSpec(HPyContext *ctx, HPyType_Spec *spec,
                     HPyType_SpecParam *params);
HPy_ID(151)
HPy HPyType_GenericNew(HPyContext *ctx, HPy type, const HPy *args, HPy_ssize_t nargs, HPy kw);

HPy_ID(152)
HPy HPy_GetAttr(HPyContext *ctx, HPy obj, HPy name);
HPy_ID(153)
HPy HPy_GetAttr_s(HPyContext *ctx, HPy obj, const char *utf8_name);

HPy_ID(154)
int HPy_HasAttr(HPyContext *ctx, HPy obj, HPy name);
HPy_ID(155)
int HPy_HasAttr_s(HPyContext *ctx, HPy obj, const char *utf8_name);

HPy_ID(156)
int HPy_SetAttr(HPyContext *ctx, HPy obj, HPy name, HPy value);
HPy_ID(157)
int HPy_SetAttr_s(HPyContext *ctx, HPy obj, const char *utf8_name, HPy value);

HPy_ID(158)
HPy HPy_GetItem(HPyContext *ctx, HPy obj, HPy key);
HPy_ID(159)
HPy HPy_GetItem_i(HPyContext *ctx, HPy obj, HPy_ssize_t idx);
HPy_ID(160)
HPy HPy_GetItem_s(HPyContext *ctx, HPy obj, const char *utf8_key);

/**
 * Return the slice of sequence object ``obj`` between ``start`` and ``end``.
 * This is the equivalent of the Python expression ``obj[start:end]``.
 *
 * :param ctx:
 *     The execution context.
 * :param obj:
 *     A sliceable Python object (must not be ``HPy_NULL`` otherwise a
 *     ``SystemError`` will be raised). If the object is not sliceable, a
 *     ``TypeError`` will be raised.
 * :param start:
 *     The start index (inclusive).
 * :param end:
 *     The end index (exclusive).
 *
 * :returns:
 *     The requested slice or ``HPy_NULL`` on failure.
 */
HPy_ID(266)
HPy HPy_GetSlice(HPyContext *ctx, HPy obj, HPy_ssize_t start, HPy_ssize_t end);

HPy_ID(161)
int HPy_Contains(HPyContext *ctx, HPy container, HPy key);

HPy_ID(162)
int HPy_SetItem(HPyContext *ctx, HPy obj, HPy key, HPy value);
HPy_ID(163)
int HPy_SetItem_i(HPyContext *ctx, HPy obj, HPy_ssize_t idx, HPy value);
HPy_ID(164)
int HPy_SetItem_s(HPyContext *ctx, HPy obj, const char *utf8_key, HPy value);

/**
 * Assign the sequence object ``value`` to the slice in sequence object ``obj``
 * from ``start`` to ``end``. This is the equivalent of the Python statement
 * ``obj[start:end] = value``.
 *
 * :param ctx:
 *     The execution context.
 * :param obj:
 *     A sliceable Python object (must not be ``HPy_NULL`` otherwise a
 *     ``SystemError`` will be raised). If the object is not sliceable, a
 *     ``TypeError`` will be raised.
 * :param start:
 *     The start index (inclusive).
 * :param end:
 *     The end index (exclusive).
 * :param value:
 *     The sequence object to assign (must not be ``HPy_NULL``).
 *
 * :returns:
 *     ``0`` on success; ``-1`` on failure
 */
HPy_ID(267)
int HPy_SetSlice(HPyContext *ctx, HPy obj, HPy_ssize_t start, HPy_ssize_t end, HPy value);

HPy_ID(235)
int HPy_DelItem(HPyContext *ctx, HPy obj, HPy key);
HPy_ID(236)
int HPy_DelItem_i(HPyContext *ctx, HPy obj, HPy_ssize_t idx);
HPy_ID(237)
int HPy_DelItem_s(HPyContext *ctx, HPy obj, const char *utf8_key);

/**
 * Delete the slice of sequence object ``obj`` between ``start`` and ``end``.
 * This is the equivalent of the Python statement ``del obj[start:end]``.
 *
 * :param ctx:
 *     The execution context.
 * :param obj:
 *     A sliceable Python object (must not be ``HPy_NULL`` otherwise a
 *     ``SystemError`` will be raised). If the object is not sliceable, a
 *     ``TypeError`` will be raised.
 * :param start:
 *     The start index (inclusive).
 * :param end:
 *     The end index (exclusive).
 *
 * :returns:
 *     ``0`` on success; ``-1`` on failure
 */
HPy_ID(268)
int HPy_DelSlice(HPyContext *ctx, HPy obj, HPy_ssize_t start, HPy_ssize_t end);

/**
 * Returns the type of the given object ``obj``.
 *
 * On failure, raises ``SystemError`` and returns ``HPy_NULL``. This is
 * equivalent to the Python expression``type(obj)``.
 *
 * :param ctx:
 *     The execution context.
 * :param obj:
 *     a Python object (must not be ``HPy_NULL``)
 *
 * :returns:
 *     The type of ``obj`` or ``HPy_NULL`` in case of errors.
 */
HPy_ID(165)
HPy HPy_Type(HPyContext *ctx, HPy obj);

/**
 * Checks if ``ob`` is an instance of ``type`` or any subtype of ``type``.
 *
 * :param ctx:
 *     The execution context.
 * :param obj:
 *     a Python object (must not be ``HPy_NULL``)
 * :param type:
 *     A Python type object. This argument must not be ``HPy_NULL`` and must be
 *     a type (i.e. it must inherit from Python ``type``). If this is not the
 *     case, the behavior is undefined (verification of the argument is only
 *     done in debug mode).
 *
 * :returns:
 *     Non-zero if object ``obj`` is an instance of type ``type`` or an instance
 *     of a subtype of ``type``, and ``0`` otherwise.
 */
HPy_ID(166)
int HPy_TypeCheck(HPyContext *ctx, HPy obj, HPy type);

/**
 * Return the type's name.
 *
 * Equivalent to getting the type's ``__name__`` attribute. If you want to
 * retrieve the type's name as a handle that refers to a ``str``, then just use
 * ``HPy_GetAttr_s(ctx, type, "__name__")``.
 *
 * :param ctx:
 *     The execution context.
 * :param type:
 *     A Python type object. This argument must not be ``HPy_NULL`` and must be
 *     a type (i.e. it must inherit from Python ``type``). If this is not the
 *     case, the behavior is undefined (verification of the argument is only
 *     done in debug mode).
 *
 * :returns:
 *     The name of the type as C string (UTF-8 encoded) or ``NULL`` in case of
 *     an error. The returned pointer is read-only and guaranteed to be valid as
 *     long as the handle ``type`` is valid.
 */
HPy_ID(253)
const char *HPyType_GetName(HPyContext *ctx, HPy type);

/**
 * Checks if ``sub`` is a subtype of ``type``.
 *
 * This function only checks for actual subtypes, which means that
 * ``__subclasscheck__()`` is not called on ``type``.
 *
 * :param ctx:
 *     The execution context.
 * :param sub:
 *     A Python type object. This argument must not be ``HPy_NULL`` and must be
 *     a type (i.e. it must inherit from Python ``type``). If this is not the
 *     case, the behavior is undefined (verification of the argument is only
 *     done in debug mode).
 * :param type:
 *     A Python type object. This argument must not be ``HPy_NULL`` and must be
 *     a type (i.e. it must inherit from Python ``type``). If this is not the
 *     case, the behavior is undefined (verification of the argument is only
 *     done in debug mode).
 *
 * :returns:
 *     Non-zero if ``sub`` is a subtype of ``type``.
 */
HPy_ID(254)
int HPyType_IsSubtype(HPyContext *ctx, HPy sub, HPy type);

HPy_ID(167)
int HPy_Is(HPyContext *ctx, HPy obj, HPy other);

HPy_ID(168)
void* _HPy_AsStruct_Object(HPyContext *ctx, HPy h);
HPy_ID(169)
void* _HPy_AsStruct_Legacy(HPyContext *ctx, HPy h);
HPy_ID(228)
void* _HPy_AsStruct_Type(HPyContext *ctx, HPy h);
HPy_ID(229)
void* _HPy_AsStruct_Long(HPyContext *ctx, HPy h);
HPy_ID(230)
void* _HPy_AsStruct_Float(HPyContext *ctx, HPy h);
HPy_ID(231)
void* _HPy_AsStruct_Unicode(HPyContext *ctx, HPy h);
HPy_ID(232)
void* _HPy_AsStruct_Tuple(HPyContext *ctx, HPy h);
HPy_ID(233)
void* _HPy_AsStruct_List(HPyContext *ctx, HPy h);
HPy_ID(264)
void* _HPy_AsStruct_Dict(HPyContext *ctx, HPy h);
HPy_ID(234)
HPyType_BuiltinShape _HPyType_GetBuiltinShape(HPyContext *ctx, HPy h_type);

HPy_ID(170)
HPy _HPy_New(HPyContext *ctx, HPy h_type, void **data);

HPy_ID(171)
HPy HPy_Repr(HPyContext *ctx, HPy obj);
HPy_ID(172)
HPy HPy_Str(HPyContext *ctx, HPy obj);
HPy_ID(173)
HPy HPy_ASCII(HPyContext *ctx, HPy obj);
HPy_ID(174)
HPy HPy_Bytes(HPyContext *ctx, HPy obj);

HPy_ID(175)
HPy HPy_RichCompare(HPyContext *ctx, HPy v, HPy w, int op);
HPy_ID(176)
int HPy_RichCompareBool(HPyContext *ctx, HPy v, HPy w, int op);

HPy_ID(177)
HPy_hash_t HPy_Hash(HPyContext *ctx, HPy obj);

/* bytesobject.h */
HPy_ID(178)
int HPyBytes_Check(HPyContext *ctx, HPy h);
HPy_ID(179)
HPy_ssize_t HPyBytes_Size(HPyContext *ctx, HPy h);
HPy_ID(180)
HPy_ssize_t HPyBytes_GET_SIZE(HPyContext *ctx, HPy h);
HPy_ID(181)
const char* HPyBytes_AsString(HPyContext *ctx, HPy h);
HPy_ID(182)
const char* HPyBytes_AS_STRING(HPyContext *ctx, HPy h);
HPy_ID(183)
HPy HPyBytes_FromString(HPyContext *ctx, const char *bytes);
HPy_ID(184)
HPy HPyBytes_FromStringAndSize(HPyContext *ctx, const char *bytes, HPy_ssize_t len);

/* unicodeobject.h */
HPy_ID(185)
HPy HPyUnicode_FromString(HPyContext *ctx, const char *utf8);
HPy_ID(186)
int HPyUnicode_Check(HPyContext *ctx, HPy h);
HPy_ID(187)
HPy HPyUnicode_AsASCIIString(HPyContext *ctx, HPy h);
HPy_ID(188)
HPy HPyUnicode_AsLatin1String(HPyContext *ctx, HPy h);
HPy_ID(189)
HPy HPyUnicode_AsUTF8String(HPyContext *ctx, HPy h);
HPy_ID(190)
const char* HPyUnicode_AsUTF8AndSize(HPyContext *ctx, HPy h, HPy_ssize_t *size);
HPy_ID(191)
HPy HPyUnicode_FromWideChar(HPyContext *ctx, const wchar_t *w, HPy_ssize_t size);
HPy_ID(192)
HPy HPyUnicode_DecodeFSDefault(HPyContext *ctx, const char *v);
HPy_ID(193)
HPy HPyUnicode_DecodeFSDefaultAndSize(HPyContext *ctx, const char *v, HPy_ssize_t size);
HPy_ID(194)
HPy HPyUnicode_EncodeFSDefault(HPyContext *ctx, HPy h);
HPy_ID(195)
HPy_UCS4 HPyUnicode_ReadChar(HPyContext *ctx, HPy h, HPy_ssize_t index);
HPy_ID(196)
HPy HPyUnicode_DecodeASCII(HPyContext *ctx, const char *ascii, HPy_ssize_t size, const char *errors);
HPy_ID(197)
HPy HPyUnicode_DecodeLatin1(HPyContext *ctx, const char *latin1, HPy_ssize_t size, const char *errors);

/**
 * Decode a bytes-like object to a Unicode object.
 *
 * The bytes of the bytes-like object are decoded according to the given
 * encoding and using the error handling defined by ``errors``.
 *
 * :param ctx:
 *     The execution context.
 * :param obj:
 *     A bytes-like object. This can be, for example, Python *bytes*,
 *     *bytearray*, *memoryview*, *array.array* and objects that support the
 *     Buffer protocol. If this argument is `HPy_NULL``, a ``SystemError`` will
 *     be raised. If the argument is not a bytes-like object, a ``TypeError``
 *     will be raised.
 * :param encoding:
 *     The name (UTF-8 encoded C string) of the encoding to use. If the encoding
 *     does not exist, a ``LookupError`` will be raised. If this argument is
 *     ``NULL``, the default encoding ``UTF-8`` will be used.
 * :param errors:
 *     The error handling (UTF-8 encoded C string) to use when decoding. The
 *     possible values depend on the used encoding. This argument may be
 *     ``NULL`` in which case it will default to ``"strict"``.
 *
 * :returns:
 *     A handle to a ``str`` object created from the decoded bytes or
 *     ``HPy_NULL`` in case of errors.
 */
HPy_ID(255)
HPy HPyUnicode_FromEncodedObject(HPyContext *ctx, HPy obj, const char *encoding, const char *errors);

/**
 * Return a substring of ``str``, from character index ``start`` (included) to
 * character index ``end`` (excluded).
 *
 * Indices ``start`` and ``end`` must not be negative, otherwise an
 * ``IndexError`` will be raised. If ``start >= len(str)`` or if
 * ``end < start``, an empty string will be returned. If ``end > len(str)`` then
 * ``end == len(str)`` will be assumed.
 *
 * :param ctx:
 *     The execution context.
 * :param str:
 *     A Python Unicode object (must not be ``HPy_NULL``). Otherwise, the
 *     behavior is undefined (verification of the argument is only done in
 *     debug mode).
 * :param start:
 *     The non-negative start index (inclusive).
 * :param end:
 *    The non-negative end index (exclusive).
 *
 * :returns:
 *     The requested substring or ``HPy_NULL`` in case of an error.
 */
HPy_ID(256)
HPy HPyUnicode_Substring(HPyContext *ctx, HPy str, HPy_ssize_t start, HPy_ssize_t end);

/* listobject.h */

/**
 * Tests if an object is an instance of a Python list.
 *
 * :param ctx:
 *     The execution context.
 * :param h:
 *     A handle to an arbitrary object (must not be ``HPy_NULL``).
 *
 * :returns:
 *     Non-zero if object ``h`` is an instance of type ``list`` or an instance
 *     of a subtype of ``list``, and ``0`` otherwise.
 */
HPy_ID(198)
int HPyList_Check(HPyContext *ctx, HPy h);

/**
 * Creates a new list instance with length ``len``.
 *
 * :param ctx:
 *     The execution context.
 * :param len:
 *     A Python list object (must not be ``HPy_NULL``). Otherwise, a
 *     ``SystemError`` will be raised.
 *
 * :returns:
 *     The new list instance on success, or ``HPy_NULL`` on failure.
 */
HPy_ID(199)
HPy HPyList_New(HPyContext *ctx, HPy_ssize_t len);

/**
 * Append item ``h_item`` to list ``h_list``.
 *
 * :param ctx:
 *     The execution context.
 * :param h_list:
 *     A Python list object (must not be ``HPy_NULL``). Otherwise, a
 *     ``SystemError`` will be raised.
 * :param h_item:
 *    The item to append (must not be ``HPy_NULL``).
 *
 * :returns:
 *     Return ``0`` if successful; return ``-1`` and set an exception if
 *     unsuccessful.
 */
HPy_ID(200)
int HPyList_Append(HPyContext *ctx, HPy h_list, HPy h_item);

/**
 * Insert the item ``h_item`` into list ``h_list`` in front of index ``index``.
 *
 * :param ctx:
 *     The execution context.
 * :param h_list:
 *     A Python list object (must not be ``HPy_NULL``). Otherwise, a
 *     ``SystemError`` will be raised.
 * :param index:
 *     The index where the element should be inserted before. A negative index
 *     is allowed and is then interpreted to be relative to the end of sequence.
 *     E.g. ``index == -1`` is the last element.
 *     If ``index < -n`` (where ``n`` is the length of the list), it will be
 *     replaced by ``0``. If ``index > n``, it will be replaced by ``n``.
 * :param h_item:
 *    The item to insert (must not be ``HPy_NULL``).
 *
 * :returns:
 *     Return ``0`` if successful; return ``-1`` and set an exception if
 *     unsuccessful.
 */
HPy_ID(265)
int HPyList_Insert(HPyContext *ctx, HPy h_list, HPy_ssize_t index, HPy h_item);

/* dictobject.h */

/**
 * Tests if an object is an instance of a Python dict.
 *
 * :param ctx:
 *     The execution context.
 * :param h:
 *     A handle to an arbitrary object (must not be ``HPy_NULL``).
 *
 * :returns:
 *     Non-zero if object ``h`` is an instance of type ``dict`` or an instance
 *     of a subtype of ``dict``, and ``0`` otherwise.
 */
HPy_ID(201)
int HPyDict_Check(HPyContext *ctx, HPy h);

/**
 * Creates a new empty Python dictionary.
 *
 * :param ctx:
 *     The execution context.
 *
 * :returns:
 *     A handle to the new and empty Python dictionary or ``HPy_NULL`` in case
 *     of an error.
 */
HPy_ID(202)
HPy HPyDict_New(HPyContext *ctx);

/**
 * Returns a list of all keys from the dictionary.
 *
 * Note: This function will directly access the storage of the dict object and
 * therefore ignores if method ``keys`` was overwritten.
 *
 * :param ctx:
 *     The execution context.
 * :param h:
 *     A Python dict object. If this argument is ``HPy_NULL`` or not an
 *     instance of a Python dict, a ``SystemError`` will be raised.
 *
 * :returns:
 *     A Python list object containing all keys of the given dictionary or
 *     ``HPy_NULL`` in case of an error.
 */
HPy_ID(257)
HPy HPyDict_Keys(HPyContext *ctx, HPy h);

/**
 * Creates a copy of the provided Python dict object.
 *
 * :param ctx:
 *     The execution context.
 * :param h:
 *     A Python dict object. If this argument is ``HPy_NULL`` or not an
 *     instance of a Python dict, a ``SystemError`` will be raised.
 *
 * :returns:
 *     Return a new dictionary that contains the same key-value pairs as ``h``
 *     or ``HPy_NULL`` in case of an error.
 */
HPy_ID(258)
HPy HPyDict_Copy(HPyContext *ctx, HPy h);

/* tupleobject.h */

/**
 * Tests if an object is an instance of a Python tuple.
 *
 * :param ctx:
 *     The execution context.
 * :param h:
 *     A handle to an arbitrary object (must not be ``HPy_NULL``).
 *
 * :returns:
 *     Non-zero if object ``h`` is an instance of type ``tuple`` or an instance
 *     of a subtype of ``tuple``, and ``0`` otherwise.
 */
HPy_ID(203)
int HPyTuple_Check(HPyContext *ctx, HPy h);

/**
 * Create a tuple from an array.
 *
 * Note: Consider to use the convenience function :c:func:`HPyTuple_Pack` to
 * create a tuple.
 *
 * :param ctx:
 *     The execution context.
 * :param items:
 *     An array of items to use for initialization of the tuple.
 * :param n:
 *     The number of elements in array ``items``.
 *
 * :return:
 *     A new tuple with ``n`` elements or ``HPy_NULL`` in case of an error
 *     occurred.
 */
HPy_ID(204)
HPy HPyTuple_FromArray(HPyContext *ctx, const HPy items[], HPy_ssize_t n);

/* sliceobject.h */

/**
 * Creates a new empty Python slice object.
 *
 * :param ctx:
 *     The execution context.
 * 
 * :param start:
 *     A handle to an object to be used as the slice start value.
 * :param end:
 *     A handle to an object to be used as the slice end value.
 * :param step:
 *     A handle to an object to be used as the slice step value.
 *
 * :returns:
 *     A handle to the new and empty Python slice object or ``HPy_NULL`` in case
 *     of an error.
 */
HPy_ID(272)
HPy HPySlice_New(HPyContext *ctx, HPy start, HPy stop, HPy step);

/**
 * Extract the start, stop and step data members from a slice object as C
 * integers.
 *
 * The slice members may be arbitrary int-like objects. If they are not Python
 * int objects, they will be coerced to int objects by calling their
 * ``__index__`` method.
 *
 * If a slice member value is out of bounds, it will be set to the maximum value
 * of ``HPy_ssize_t`` if the member was a positive number, or to the minimum
 * value of ``HPy_ssize_t`` if it was a negative number.
 *
 * :param ctx:
 *     The execution context.
 * :param slice:
 *     A handle to a Python slice object. This argument must be a slice object
 *     and must not be ``HPy_NULL``. Otherwise, behavior is undefined.
 * :param start:
 *     A pointer to a variable where to write the unpacked slice start. Must not
 *     be ``NULL``.
 * :param end:
 *     A pointer to a variable where to write the unpacked slice end. Must not
 * :param step:
 *     A pointer to a variable where to write the unpacked slice step. Must not
 *     be ``NULL``.
 *
 * :returns:
 *     ``-1`` on error, ``0`` on success
 */

HPy_ID(259)
int HPySlice_Unpack(HPyContext *ctx, HPy slice, HPy_ssize_t *start, HPy_ssize_t *stop, HPy_ssize_t *step);

/* import.h */
HPy_ID(205)
HPy HPyImport_ImportModule(HPyContext *ctx, const char *utf8_name);

/* pycapsule.h */
HPy_ID(244)
HPy HPyCapsule_New(HPyContext *ctx, void *pointer, const char *utf8_name, HPyCapsule_Destructor *destructor);
HPy_ID(245)
void* HPyCapsule_Get(HPyContext *ctx, HPy capsule, _HPyCapsule_key key, const char *utf8_name);
HPy_ID(246)
int HPyCapsule_IsValid(HPyContext *ctx, HPy capsule, const char *utf8_name);
HPy_ID(247)
int HPyCapsule_Set(HPyContext *ctx, HPy capsule, _HPyCapsule_key key, void *value);

/* integration with the old CPython API */
HPy_ID(206)
HPy HPy_FromPyObject(HPyContext *ctx, cpy_PyObject *obj);
HPy_ID(207)
cpy_PyObject *HPy_AsPyObject(HPyContext *ctx, HPy h);

/* internal helpers which need to be exposed to modules for practical reasons :( */
HPy_ID(208)
void _HPy_CallRealFunctionFromTrampoline(HPyContext *ctx,
                                         HPyFunc_Signature sig,
                                         HPyCFunction func,
                                         void *args);

/* Builders */

/**
 * Create a new list builder for ``size`` elements. The builder is then able to
 * take at most ``size`` elements. This function does not raise any
 * exception (even if running out of memory).
 *
 * :param ctx:
 *     The execution context.
 * :param size:
 *     The number of elements to hold.
 */
HPy_ID(209)
HPyListBuilder HPyListBuilder_New(HPyContext *ctx, HPy_ssize_t size);

/**
 * Assign an element to a certain index of the builder. Valid indices are in
 * range ``0 <= index < size`` where ``size`` is the value passed to
 * :c:func:`HPyListBuilder_New`. This function does not raise any exception.
 *
 * :param ctx:
 *     The execution context.
 * :param builder:
 *     A list builder handle.
 * :param index:
 *     The index to assign the object to.
 * :param h_item:
 *     An HPy handle of the object to store or ``HPy_NULL``. Please note that
 *     HPy **never** steals handles and so, ``h_item`` needs to be closed by
 *     the caller.
 */
HPy_ID(210)
void HPyListBuilder_Set(HPyContext *ctx, HPyListBuilder builder,
                        HPy_ssize_t index, HPy h_item);

/**
 * Build a list from a list builder.
 *
 * :param ctx:
 *     The execution context.
 * :param builder:
 *     A list builder handle.
 *
 * :returns:
 *     An HPy handle to a list containing the values inserted with
 *     :c:func:`HPyListBuilder_Set` or ``HPy_NULL`` in case an error occurred
 *     during building or earlier when creating the builder or setting the
 *     items.
 */
HPy_ID(211)
HPy HPyListBuilder_Build(HPyContext *ctx, HPyListBuilder builder);

/**
 * Cancel building of a tuple and free any acquired resources.
 * This function ignores if any error occurred previously when using the tuple
 * builder.
 *
 * :param ctx:
 *     The execution context.
 * :param builder:
 *     A tuple builder handle.
 */
HPy_ID(212)
void HPyListBuilder_Cancel(HPyContext *ctx, HPyListBuilder builder);

/**
 * Create a new tuple builder for ``size`` elements. The builder is then able
 * to take at most ``size`` elements. This function does not raise any
 * exception (even if running out of memory).
 *
 * :param ctx:
 *     The execution context.
 * :param size:
 *     The number of elements to hold.
 */
HPy_ID(213)
HPyTupleBuilder HPyTupleBuilder_New(HPyContext *ctx, HPy_ssize_t size);

/**
 * Assign an element to a certain index of the builder. Valid indices are in
 * range ``0 <= index < size`` where ``size`` is the value passed to
 * :c:func:`HPyTupleBuilder_New`. This function does not raise * any exception.
 *
 * :param ctx:
 *     The execution context.
 * :param builder:
 *     A tuple builder handle.
 * :param index:
 *     The index to assign the object to.
 * :param h_item:
 *     An HPy handle of the object to store or ``HPy_NULL``. Please note that
 *     HPy **never** steals handles and so, ``h_item`` needs to be closed by
 *     the caller.
 */
HPy_ID(214)
void HPyTupleBuilder_Set(HPyContext *ctx, HPyTupleBuilder builder,
                         HPy_ssize_t index, HPy h_item);

/**
 * Build a tuple from a tuple builder.
 *
 * :param ctx:
 *     The execution context.
 * :param builder:
 *     A tuple builder handle.
 *
 * :returns:
 *     An HPy handle to a tuple containing the values inserted with
 *     :c:func:`HPyTupleBuilder_Set` or ``HPy_NULL`` in case an error occurred
 *     during building or earlier when creating the builder or setting the
 *     items.
 */
HPy_ID(215)
HPy HPyTupleBuilder_Build(HPyContext *ctx, HPyTupleBuilder builder);

/**
 * Cancel building of a tuple and free any acquired resources.
 * This function ignores if any error occurred previously when using the tuple
 * builder.
 *
 * :param ctx:
 *     The execution context.
 * :param builder:
 *     A tuple builder handle.
 */
HPy_ID(216)
void HPyTupleBuilder_Cancel(HPyContext *ctx, HPyTupleBuilder builder);

/* Helper for correctly closing handles */

HPy_ID(217)
HPyTracker HPyTracker_New(HPyContext *ctx, HPy_ssize_t size);
HPy_ID(218)
int HPyTracker_Add(HPyContext *ctx, HPyTracker ht, HPy h);
HPy_ID(219)
void HPyTracker_ForgetAll(HPyContext *ctx, HPyTracker ht);
HPy_ID(220)
void HPyTracker_Close(HPyContext *ctx, HPyTracker ht);

/**
 * HPyFields should be used ONLY in parts of memory which is known to the GC,
 * e.g. memory allocated by HPy_New:
 *
 *   - NEVER declare a local variable of type HPyField
 *   - NEVER use HPyField on a struct allocated by e.g. malloc()
 *
 * **CPython's note**: contrary to PyObject*, you don't need to manually
 * manage refcounting when using HPyField: if you use HPyField_Store to
 * overwrite an existing value, the old object will be automatically decrefed.
 * This means that you CANNOT use HPyField_Store to write memory which
 * contains uninitialized values, because it would try to decref a dangling
 * pointer.
 *
 * Note that HPy_New automatically zeroes the memory it allocates, so
 * everything works well out of the box. In case you are using manually
 * allocated memory, you should initialize the HPyField to HPyField_NULL.
 *
 * Note the difference:
 *
 *   - ``obj->f = HPyField_NULL``: this should be used only to initialize
 *     uninitialized memory. If you use it to overwrite a valid HPyField, you
 *     will cause a memory leak (at least on CPython)
 *
 *   - HPyField_Store(ctx, &obj->f, HPy_NULL): this does the right thing and
 *     decref the old value. However, you CANNOT use it if the memory is not
 *     initialized.
 *
 * Note: target_object and source_object are there in case an implementation
 * needs to add write and/or read barriers on the objects. They are ignored by
 * CPython but e.g. PyPy needs a write barrier.
*/
HPy_ID(221)
void HPyField_Store(HPyContext *ctx, HPy target_object, HPyField *target_field, HPy h);
HPy_ID(222)
HPy HPyField_Load(HPyContext *ctx, HPy source_object, HPyField source_field);

/**
 * Leaving Python execution: for releasing GIL and other use-cases.
 *
 * In most situations, users should prefer using convenience macros:
 * HPy_BEGIN_LEAVE_PYTHON(context)/HPy_END_LEAVE_PYTHON(context)
 *
 * HPy extensions may leave Python execution when running Python independent
 * code: long-running computations or blocking operations. When an extension
 * has left the Python execution it must not call any HPy API other than
 * HPy_ReenterPythonExecution. It can access pointers returned by HPy API,
 * e.g., HPyUnicode_AsUTF8String, provided that they are valid at the point
 * of calling HPy_LeavePythonExecution.
 *
 * Python execution must be reentered on the same thread as where it was left.
 * The leave/enter calls must not be nested. Debug mode will, in the future,
 * enforce these constraints.
 *
 * Python implementations may use this knowledge however they wish. The most
 * obvious use case is to release the GIL, in which case the
 * HPy_BEGIN_LEAVE_PYTHON/HPy_END_LEAVE_PYTHON becomes equivalent to
 * Py_BEGIN_ALLOW_THREADS/Py_END_ALLOW_THREADS.
*/
HPy_ID(223)
void HPy_ReenterPythonExecution(HPyContext *ctx, HPyThreadState state);
HPy_ID(224)
HPyThreadState HPy_LeavePythonExecution(HPyContext *ctx);

/**
 * HPyGlobal is an alternative to module state. HPyGlobal must be a statically
 * allocated C global variable registered in HPyModuleDef.globals array.
 * A HPyGlobal can be used only after the HPy module where it is registered was
 * created using HPyModule_Create.
 *
 * HPyGlobal serves as an identifier of a Python object that should be globally
 * available per one Python interpreter. Python objects referenced by HPyGlobals
 * are destroyed automatically on the interpreter exit (not necessarily the
 * process exit).
 *
 * HPyGlobal instance does not allow anything else but loading and storing
 * a HPy handle using a HPyContext. Even if the HPyGlobal C variable may
 * be shared between threads or different interpreter instances within one
 * process, the API to load and store a handle from HPyGlobal is thread-safe (but
 * like any other HPy API must not be called in HPy_LeavePythonExecution blocks).
 *
 * Given that a handle to object X1 is stored to HPyGlobal using HPyContext of
 * Python interpreter I1, then loading a handle from the same HPyGlobal using
 * HPyContext of Python interpreter I1 should give a handle to the same object
 * X1. Another Python interpreter I2 running within the same process and using
 * the same HPyGlobal variable will not be able to load X1 from it, it will have
 * its own view on what is stored in the given HPyGlobal.
 *
 * Python interpreters may use indirection to isolate different interpreter
 * instances, but alternative techniques such as copy-on-write or immortal
 * objects can be used to avoid that indirection (even selectively on per
 * object basis using tagged pointers).
 *
 * CPython HPy implementation may even provide configuration option that
 * switches between a faster version that directly stores PyObject* to
 * HPyGlobal but does not support subinterpreters, or a version that supports
 * subinterpreters. For now, CPython HPy always stores PyObject* directly
 * to HPyGlobal.
 *
 * While the standard implementation does not fully enforce the documented
 * contract, the HPy debug mode will enforce it (not implemented yet).
 *
 * **Implementation notes:**
 * All Python interpreters running in one process must be compatible, because
 * they will share all HPyGlobal C level variables. The internal data stored
 * in HPyGlobal are specific for each HPy implementation, each implementation
 * is also responsible for handling thread-safety when initializing the
 * internal data in HPyModule_Create. Note that HPyModule_Create may be called
 * concurrently depending on the semantics of the Python implementation (GIL vs
 * no GIL) and also depending on the whether there may be multiple instances of
 * given Python interpreter running within the same process. In the future, HPy
 * ABI may include a contract that internal data of each HPyGlobal must be
 * initialized to its address using atomic write and HPy implementations will
 * not be free to choose what to store in HPyGlobal, however, this will allow
 * multiple different HPy implementations within one process. This contract may
 * also be activated only by some runtime option, letting the HPy implementation
 * use more optimized HPyGlobal implementation otherwise.
*/
HPy_ID(225)
void HPyGlobal_Store(HPyContext *ctx, HPyGlobal *global, HPy h);
HPy_ID(226)
HPy HPyGlobal_Load(HPyContext *ctx, HPyGlobal global);

/* Debugging helpers */
HPy_ID(227)
void _HPy_Dump(HPyContext *ctx, HPy h);

/* Evaluating Python statements/expressions */

/**
 * Parse and compile the Python source code.
 *
 * :param ctx:
 *     The execution context.
 * :param utf8_source:
 *     Python source code given as UTF-8 encoded C string (must not be ``NULL``).
 * :param utf8_filename:
 *     The filename (UTF-8 encoded C string) to use for construction of the code
 *     object. It may appear in tracebacks or in ``SyntaxError`` exception
 *     messages.
 * :param kind:
 *     The source kind which tells the parser if a single expression, statement,
 *     or a whole file should be parsed (see enum :c:enum:`HPy_SourceKind`).
 *
 * :returns:
 *     A Python code object resulting from the parsed and compiled Python source
 *     code or ``HPy_NULL`` in case of errors.
 */
HPy_ID(248)
HPy HPy_Compile_s(HPyContext *ctx, const char *utf8_source, const char *utf8_filename, HPy_SourceKind kind);

/**
 * Evaluate a precompiled code object.
 *
 * Code objects can be compiled from a string using :c:func:`HPy_Compile_s`.
 *
 * :param ctx:
 *     The execution context.
 * :param code:
 *     The code object to evaluate.
 * :param globals:
 *     A Python dictionary defining the global variables for the evaluation.
 * :param locals:
 *     A mapping object defining the local variables for the evaluation.
 *
 * :returns:
 *     The result produced by the executed code. May be ``HPy_NULL`` in case of
 *     errors.
 */
HPy_ID(249)
HPy HPy_EvalCode(HPyContext *ctx, HPy code, HPy globals, HPy locals);
HPy_ID(250)
HPy HPyContextVar_New(HPyContext *ctx, const char *name, HPy default_value);
HPy_ID(251)
int32_t HPyContextVar_Get(HPyContext *ctx, HPy context_var, HPy default_value, HPy *result);
HPy_ID(252)
HPy HPyContextVar_Set(HPyContext *ctx, HPy context_var, HPy value);

/**
 * Set the call function for the given object.
 *
 * By defining slot ``HPy_tp_call`` for some type, instances of this type will
 * be callable objects. The specified call function will be used by default for
 * every instance. This should account for the most common case (every instance
 * of an object uses the same call function) but to still provide the necessary
 * flexibility, function ``HPy_SetCallFunction`` allows to set different (maybe
 * specialized) call functions for each instance. This must be done in the
 * constructor of an object.
 *
 * A more detailed description on how to use that function can be found in
 * section :ref:`porting-guide:calling protocol`.
 *
 * :param ctx:
 *     The execution context.
 * :param h:
 *     A handle to an object implementing the call protocol, i.e., the object's
 *     type must have slot ``HPy_tp_call``. Otherwise, a ``TypeError`` will be
 *     raised. This argument must not be ``HPy_NULL``.
 * :param def:
 *     A pointer to the call function definition to set (must not be
 *     ``NULL``). The definition is usually created using
 *     :c:macro:`HPyDef_CALL_FUNCTION`
 *
 * :returns:
 *     ``0`` in case of success and ``-1`` in case of an error.
 */
HPy_ID(260)
int HPy_SetCallFunction(HPyContext *ctx, HPy h, HPyCallFunction *func);

/* *******
   hpyfunc
   *******

   These typedefs are used to generate the various macros used by
   include/common/hpyfunc.h
*/
typedef HPy (*HPyFunc_noargs)(HPyContext *ctx, HPy self);
typedef HPy (*HPyFunc_o)(HPyContext *ctx, HPy self, HPy arg);
typedef HPy (*HPyFunc_varargs)(HPyContext *ctx, HPy self, const HPy *args, size_t nargs);
typedef HPy (*HPyFunc_keywords)(HPyContext *ctx, HPy self, const HPy *args,
                                size_t nargs, HPy kwnames);

typedef HPy (*HPyFunc_unaryfunc)(HPyContext *ctx, HPy);
typedef HPy (*HPyFunc_binaryfunc)(HPyContext *ctx, HPy, HPy);
typedef HPy (*HPyFunc_ternaryfunc)(HPyContext *ctx, HPy, HPy, HPy);
typedef int (*HPyFunc_inquiry)(HPyContext *ctx, HPy);
typedef HPy_ssize_t (*HPyFunc_lenfunc)(HPyContext *ctx, HPy);
typedef HPy (*HPyFunc_ssizeargfunc)(HPyContext *ctx, HPy, HPy_ssize_t);
typedef HPy (*HPyFunc_ssizessizeargfunc)(HPyContext *ctx, HPy, HPy_ssize_t, HPy_ssize_t);
typedef int (*HPyFunc_ssizeobjargproc)(HPyContext *ctx, HPy, HPy_ssize_t, HPy);
typedef int (*HPyFunc_ssizessizeobjargproc)(HPyContext *ctx, HPy, HPy_ssize_t, HPy_ssize_t, HPy);
typedef int (*HPyFunc_objobjargproc)(HPyContext *ctx, HPy, HPy, HPy);
typedef void (*HPyFunc_freefunc)(HPyContext *ctx, void *);
typedef HPy (*HPyFunc_getattrfunc)(HPyContext *ctx, HPy, char *);
typedef HPy (*HPyFunc_getattrofunc)(HPyContext *ctx, HPy, HPy);
typedef int (*HPyFunc_setattrfunc)(HPyContext *ctx, HPy, char *, HPy);
typedef int (*HPyFunc_setattrofunc)(HPyContext *ctx, HPy, HPy, HPy);
typedef HPy (*HPyFunc_reprfunc)(HPyContext *ctx, HPy);
typedef HPy_hash_t (*HPyFunc_hashfunc)(HPyContext *ctx, HPy);
typedef HPy (*HPyFunc_richcmpfunc)(HPyContext *ctx, HPy, HPy, HPy_RichCmpOp);
typedef HPy (*HPyFunc_getiterfunc)(HPyContext *ctx, HPy);
typedef HPy (*HPyFunc_iternextfunc)(HPyContext *ctx, HPy);
typedef HPy (*HPyFunc_descrgetfunc)(HPyContext *ctx, HPy, HPy, HPy);
typedef int (*HPyFunc_descrsetfunc)(HPyContext *ctx, HPy, HPy, HPy);
typedef int (*HPyFunc_initproc)(HPyContext *ctx, HPy self,
                                const HPy *args, HPy_ssize_t nargs, HPy kw);
typedef HPy (*HPyFunc_newfunc)(HPyContext *ctx, HPy type, const HPy *args,
                               HPy_ssize_t nargs, HPy kw);
typedef HPy (*HPyFunc_getter)(HPyContext *ctx, HPy, void *);
typedef int (*HPyFunc_setter)(HPyContext *ctx, HPy, HPy, void *);
typedef int (*HPyFunc_objobjproc)(HPyContext *ctx, HPy, HPy);
typedef int (*HPyFunc_getbufferproc)(HPyContext *ctx, HPy, HPy_buffer *, int);
typedef void (*HPyFunc_releasebufferproc)(HPyContext *ctx, HPy, HPy_buffer *);
typedef int (*HPyFunc_traverseproc)(void *object, HPyFunc_visitproc visit, void *arg);
typedef void (*HPyFunc_destructor)(HPyContext *ctx, HPy);

typedef void (*HPyFunc_destroyfunc)(void *);

// Note: separate type, because we need a different trampoline
typedef HPy (*HPyFunc_mod_create)(HPyContext *ctx, HPy);


/* ~~~ HPySlot_Slot ~~~

   The following enum is used to generate autogen_hpyslot.h, which contains:

     - The real definition of the enum HPySlot_Slot

     - the macros #define _HPySlot_SIGNATURE_*

*/

// NOTE: if you uncomment/enable a slot below, make sure to write a corresponding
// test in test_slots.py

/* Note that the magic numbers are the same as CPython */
typedef enum {
    HPy_bf_getbuffer = SLOT(1, HPyFunc_GETBUFFERPROC),
    HPy_bf_releasebuffer = SLOT(2, HPyFunc_RELEASEBUFFERPROC),
    HPy_mp_ass_subscript = SLOT(3, HPyFunc_OBJOBJARGPROC),
    HPy_mp_length = SLOT(4, HPyFunc_LENFUNC),
    HPy_mp_subscript = SLOT(5, HPyFunc_BINARYFUNC),
    HPy_nb_absolute = SLOT(6, HPyFunc_UNARYFUNC),
    HPy_nb_add = SLOT(7, HPyFunc_BINARYFUNC),
    HPy_nb_and = SLOT(8, HPyFunc_BINARYFUNC),
    HPy_nb_bool = SLOT(9, HPyFunc_INQUIRY),
    HPy_nb_divmod = SLOT(10, HPyFunc_BINARYFUNC),
    HPy_nb_float = SLOT(11, HPyFunc_UNARYFUNC),
    HPy_nb_floor_divide = SLOT(12, HPyFunc_BINARYFUNC),
    HPy_nb_index = SLOT(13, HPyFunc_UNARYFUNC),
    HPy_nb_inplace_add = SLOT(14, HPyFunc_BINARYFUNC),
    HPy_nb_inplace_and = SLOT(15, HPyFunc_BINARYFUNC),
    HPy_nb_inplace_floor_divide = SLOT(16, HPyFunc_BINARYFUNC),
    HPy_nb_inplace_lshift = SLOT(17, HPyFunc_BINARYFUNC),
    HPy_nb_inplace_multiply = SLOT(18, HPyFunc_BINARYFUNC),
    HPy_nb_inplace_or = SLOT(19, HPyFunc_BINARYFUNC),
    HPy_nb_inplace_power = SLOT(20, HPyFunc_TERNARYFUNC),
    HPy_nb_inplace_remainder = SLOT(21, HPyFunc_BINARYFUNC),
    HPy_nb_inplace_rshift = SLOT(22, HPyFunc_BINARYFUNC),
    HPy_nb_inplace_subtract = SLOT(23, HPyFunc_BINARYFUNC),
    HPy_nb_inplace_true_divide = SLOT(24, HPyFunc_BINARYFUNC),
    HPy_nb_inplace_xor = SLOT(25, HPyFunc_BINARYFUNC),
    HPy_nb_int = SLOT(26, HPyFunc_UNARYFUNC),
    HPy_nb_invert = SLOT(27, HPyFunc_UNARYFUNC),
    HPy_nb_lshift = SLOT(28, HPyFunc_BINARYFUNC),
    HPy_nb_multiply = SLOT(29, HPyFunc_BINARYFUNC),
    HPy_nb_negative = SLOT(30, HPyFunc_UNARYFUNC),
    HPy_nb_or = SLOT(31, HPyFunc_BINARYFUNC),
    HPy_nb_positive = SLOT(32, HPyFunc_UNARYFUNC),
    HPy_nb_power = SLOT(33, HPyFunc_TERNARYFUNC),
    HPy_nb_remainder = SLOT(34, HPyFunc_BINARYFUNC),
    HPy_nb_rshift = SLOT(35, HPyFunc_BINARYFUNC),
    HPy_nb_subtract = SLOT(36, HPyFunc_BINARYFUNC),
    HPy_nb_true_divide = SLOT(37, HPyFunc_BINARYFUNC),
    HPy_nb_xor = SLOT(38, HPyFunc_BINARYFUNC),
    HPy_sq_ass_item = SLOT(39, HPyFunc_SSIZEOBJARGPROC),
    HPy_sq_concat = SLOT(40, HPyFunc_BINARYFUNC),
    HPy_sq_contains = SLOT(41, HPyFunc_OBJOBJPROC),
    HPy_sq_inplace_concat = SLOT(42, HPyFunc_BINARYFUNC),
    HPy_sq_inplace_repeat = SLOT(43, HPyFunc_SSIZEARGFUNC),
    HPy_sq_item = SLOT(44, HPyFunc_SSIZEARGFUNC),
    HPy_sq_length = SLOT(45, HPyFunc_LENFUNC),
    HPy_sq_repeat = SLOT(46, HPyFunc_SSIZEARGFUNC),
    //HPy_tp_alloc = SLOT(47, HPyFunc_X),      NOT SUPPORTED
    //HPy_tp_base = SLOT(48, HPyFunc_X),
    //HPy_tp_bases = SLOT(49, HPyFunc_X),
    HPy_tp_call = SLOT(50, HPyFunc_KEYWORDS),
    //HPy_tp_clear = SLOT(51, HPyFunc_X),      NOT SUPPORTED, use tp_traverse
    //HPy_tp_dealloc = SLOT(52, HPyFunc_X),    NOT SUPPORTED
    //HPy_tp_del = SLOT(53, HPyFunc_X),
    HPy_tp_descr_get = SLOT(54, HPyFunc_TERNARYFUNC),
    //HPy_tp_descr_set = SLOT(55, HPyFunc_X),
    //HPy_tp_doc = SLOT(56, HPyFunc_X),
    //HPy_tp_getattr = SLOT(57, HPyFunc_X),
    //HPy_tp_getattro = SLOT(58, HPyFunc_X),
    HPy_tp_hash = SLOT(59, HPyFunc_HASHFUNC),
    HPy_tp_init = SLOT(60, HPyFunc_INITPROC),
    //HPy_tp_is_gc = SLOT(61, HPyFunc_X),
    //HPy_tp_iter = SLOT(62, HPyFunc_X),
    //HPy_tp_iternext = SLOT(63, HPyFunc_X),
    //HPy_tp_methods = SLOT(64, HPyFunc_X),    NOT SUPPORTED
    HPy_tp_new = SLOT(65, HPyFunc_NEWFUNC),
    HPy_tp_repr = SLOT(66, HPyFunc_REPRFUNC),
    HPy_tp_richcompare = SLOT(67, HPyFunc_RICHCMPFUNC),
    //HPy_tp_setattr = SLOT(68, HPyFunc_X),
    //HPy_tp_setattro = SLOT(69, HPyFunc_X),
    HPy_tp_str = SLOT(70, HPyFunc_REPRFUNC),
    HPy_tp_traverse = SLOT(71, HPyFunc_TRAVERSEPROC),
    //HPy_tp_members = SLOT(72, HPyFunc_X),    NOT SUPPORTED
    //HPy_tp_getset = SLOT(73, HPyFunc_X),     NOT SUPPORTED
    //HPy_tp_free = SLOT(74, HPyFunc_X),       NOT SUPPORTED
    HPy_nb_matrix_multiply = SLOT(75, HPyFunc_BINARYFUNC),
    HPy_nb_inplace_matrix_multiply = SLOT(76, HPyFunc_BINARYFUNC),
    //HPy_am_await = SLOT(77, HPyFunc_X),
    //HPy_am_aiter = SLOT(78, HPyFunc_X),
    //HPy_am_anext = SLOT(79, HPyFunc_X),
    HPy_tp_finalize = SLOT(80, HPyFunc_DESTRUCTOR),

    /* extra HPy slots */
    HPy_tp_destroy = SLOT(1000, HPyFunc_DESTROYFUNC),

    /**
     * Module create slot: the function receives loader spec and should
     * return an HPy handle representing the module. Currently, creating
     * real module objects cannot be done by user code, so the only other
     * useful thing that this slot can do is to create another object that
     * can work as a module, such as SimpleNamespace.
     */
    HPy_mod_create = SLOT(2000, HPyFunc_MOD_CREATE),
    /**
     * Module exec slot: the function receives module object that was created
     * by the runtime from HPyModuleDef. This slot can do any initialization
     * of the module, such as adding types. There can be multiple exec slots
     * and they will be executed in the declaration order.
     */
    HPy_mod_exec = SLOT(2001, HPyFunc_INQUIRY),

} HPySlot_Slot;
