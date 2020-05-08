/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.runtime.exception;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;

public abstract class PythonErrorType {
    public static final PythonBuiltinClassType ArithmeticError = PythonBuiltinClassType.ArithmeticError;
    public static final PythonBuiltinClassType AssertionError = PythonBuiltinClassType.AssertionError;
    public static final PythonBuiltinClassType AttributeError = PythonBuiltinClassType.AttributeError;
    public static final PythonBuiltinClassType BaseException = PythonBuiltinClassType.PBaseException;
    public static final PythonBuiltinClassType BytesWarning = PythonBuiltinClassType.BytesWarning;
    public static final PythonBuiltinClassType DeprecationWarning = PythonBuiltinClassType.DeprecationWarning;
    public static final PythonBuiltinClassType Exception = PythonBuiltinClassType.Exception;
    public static final PythonBuiltinClassType FloatingPointError = PythonBuiltinClassType.FloatingPointError;
    public static final PythonBuiltinClassType IOError = PythonBuiltinClassType.OSError;
    public static final PythonBuiltinClassType ImportError = PythonBuiltinClassType.ImportError;
    public static final PythonBuiltinClassType ImportWarning = PythonBuiltinClassType.ImportWarning;
    public static final PythonBuiltinClassType IndexError = PythonBuiltinClassType.IndexError;
    public static final PythonBuiltinClassType KeyboardInterrupt = PythonBuiltinClassType.KeyboardInterrupt;
    public static final PythonBuiltinClassType KeyError = PythonBuiltinClassType.KeyError;
    public static final PythonBuiltinClassType LookupError = PythonBuiltinClassType.LookupError;
    public static final PythonBuiltinClassType MemoryError = PythonBuiltinClassType.MemoryError;
    public static final PythonBuiltinClassType NameError = PythonBuiltinClassType.NameError;
    public static final PythonBuiltinClassType NotImplementedError = PythonBuiltinClassType.NotImplementedError;
    public static final PythonBuiltinClassType OSError = PythonBuiltinClassType.OSError;
    public static final PythonBuiltinClassType OverflowError = PythonBuiltinClassType.OverflowError;
    public static final PythonBuiltinClassType PendingDeprecationWarning = PythonBuiltinClassType.PendingDeprecationWarning;
    public static final PythonBuiltinClassType ResourceWarning = PythonBuiltinClassType.ResourceWarning;
    public static final PythonBuiltinClassType RuntimeError = PythonBuiltinClassType.RuntimeError;
    public static final PythonBuiltinClassType RuntimeWarning = PythonBuiltinClassType.RuntimeWarning;
    public static final PythonBuiltinClassType StopIteration = PythonBuiltinClassType.StopIteration;
    public static final PythonBuiltinClassType SyntaxError = PythonBuiltinClassType.SyntaxError;
    public static final PythonBuiltinClassType SyntaxWarning = PythonBuiltinClassType.SyntaxWarning;
    public static final PythonBuiltinClassType SystemError = PythonBuiltinClassType.SystemError;
    public static final PythonBuiltinClassType SystemExit = PythonBuiltinClassType.SystemExit;
    public static final PythonBuiltinClassType TypeError = PythonBuiltinClassType.TypeError;
    public static final PythonBuiltinClassType UnboundLocalError = PythonBuiltinClassType.UnboundLocalError;
    public static final PythonBuiltinClassType UnicodeEncodeError = PythonBuiltinClassType.UnicodeEncodeError;
    public static final PythonBuiltinClassType UnicodeDecodeError = PythonBuiltinClassType.UnicodeDecodeError;
    public static final PythonBuiltinClassType UnicodeError = PythonBuiltinClassType.UnicodeError;
    public static final PythonBuiltinClassType UnicodeWarning = PythonBuiltinClassType.UnicodeWarning;
    public static final PythonBuiltinClassType UserWarning = PythonBuiltinClassType.UserWarning;
    public static final PythonBuiltinClassType ValueError = PythonBuiltinClassType.ValueError;
    public static final PythonBuiltinClassType Warning = PythonBuiltinClassType.Warning;
    public static final PythonBuiltinClassType ZeroDivisionError = PythonBuiltinClassType.ZeroDivisionError;
    public static final PythonBuiltinClassType BufferError = PythonBuiltinClassType.BufferError;
    public static final PythonBuiltinClassType FileNotFoundError = PythonBuiltinClassType.FileNotFoundError;
    public static final PythonBuiltinClassType ZipImportError = PythonBuiltinClassType.ZipImportError;
    public static final PythonBuiltinClassType ZLibError = PythonBuiltinClassType.ZLibError;
    public static final PythonBuiltinClassType LZMAError = PythonBuiltinClassType.LZMAError;
    public static final PythonBuiltinClassType StructError = PythonBuiltinClassType.StructError;
}
