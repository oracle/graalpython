/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.builtins.objects.cext.capi.PyDateTimeCAPIWrapper.T_FOLD;
import static com.oracle.graal.python.builtins.objects.cext.capi.PyDateTimeCAPIWrapper.T_FROMTIMESTAMP;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.Int;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObject;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectBorrowed;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyObjectTransfer;
import static com.oracle.graal.python.builtins.objects.cext.capi.transitions.ArgDescriptor.PyTypeObject;

import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApi10BuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApi5BuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApi6BuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApi7BuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApi9BuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBinaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiBuiltin;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiQuaternaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiTernaryBuiltinNode;
import com.oracle.graal.python.builtins.modules.cext.PythonCextBuiltins.CApiUnaryBuiltinNode;
import com.oracle.graal.python.builtins.objects.cext.capi.PyDateTimeCAPIWrapper;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.nodes.argument.keywords.ExpandKeywordStarargsNode;
import com.oracle.graal.python.nodes.argument.positional.ExecutePositionalStarargsNode;
import com.oracle.graal.python.nodes.call.special.CallVarargsMethodNode;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;

/**
 * Implementations of the functions in PyDateTimeCAPI. See also {@link PyDateTimeCAPIWrapper}.
 */
public final class PythonCextDateTimeBuiltins {

    @CApiBuiltin(ret = PyObjectTransfer, args = {Int, Int, Int, PyTypeObject}, call = Ignored)
    abstract static class PyTruffleDateTimeCAPI_Date_FromDate extends CApiQuaternaryBuiltinNode {
        @Specialization
        static Object values(int year, int month, int day, Object type,
                        @Cached CallVarargsMethodNode call) {
            return call.execute(null, type, new Object[]{year, month, day}, PKeyword.EMPTY_KEYWORDS);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {Int, Int, Int, Int, Int, Int, Int, PyObject, PyTypeObject}, call = Ignored)
    abstract static class PyTruffleDateTimeCAPI_DateTime_FromDateAndTime extends CApi9BuiltinNode {
        @Specialization
        static Object values(int year, int month, int day, int hour, int minute, int second, int usecond, Object tzinfo, Object type,
                        @Cached CallVarargsMethodNode call) {
            return call.execute(null, type, new Object[]{year, month, day, hour, minute, second, usecond, tzinfo}, PKeyword.EMPTY_KEYWORDS);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {Int, Int, Int, Int, PyObject, PyTypeObject}, call = Ignored)
    abstract static class PyTruffleDateTimeCAPI_Time_FromTime extends CApi6BuiltinNode {
        @Specialization
        static Object values(int hour, int minute, int second, int usecond, Object tzinfo, Object type,
                        @Cached CallVarargsMethodNode call) {
            return call.execute(null, type, new Object[]{hour, minute, second, usecond, tzinfo}, PKeyword.EMPTY_KEYWORDS);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {Int, Int, Int, Int, PyTypeObject}, call = Ignored)
    abstract static class PyTruffleDateTimeCAPI_Delta_FromDelta extends CApi5BuiltinNode {
        @Specialization
        static Object values(int days, int seconds, int useconds, @SuppressWarnings("unused") int normalize, Object type,
                        @Cached CallVarargsMethodNode call) {
            // TODO: "normalize" is ignored for the time being
            return call.execute(null, type, new Object[]{days, seconds, useconds}, PKeyword.EMPTY_KEYWORDS);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject}, call = Ignored)
    abstract static class PyTruffleDateTimeCAPI_TimeZone_FromTimeZone extends CApiBinaryBuiltinNode {
        @Specialization
        Object values(Object offset, Object name,
                        @Cached CallVarargsMethodNode call) {
            return call.execute(null, getCApiContext().timezoneType, new Object[]{offset, name}, PKeyword.EMPTY_KEYWORDS);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject, PyObject}, call = Ignored)
    abstract static class PyTruffleDateTimeCAPI_DateTime_FromTimestamp extends CApiTernaryBuiltinNode {
        @Specialization
        static Object values(Object type, Object args, Object kwargs,
                        @Cached ExecutePositionalStarargsNode starArgsNode,
                        @Cached ExpandKeywordStarargsNode kwArgsNode,
                        @Cached PyObjectLookupAttr lookupNode,
                        @Cached CallVarargsMethodNode call) {
            Object[] callArgs = starArgsNode.executeWith(null, args);
            PKeyword[] kwds = kwArgsNode.execute(kwargs);
            Object fromTSCallable = lookupNode.execute(null, type, T_FROMTIMESTAMP);
            return call.execute(null, fromTSCallable, callArgs, kwds);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {PyObject, PyObject}, call = Ignored)
    abstract static class PyTruffleDateTimeCAPI_Date_FromTimestamp extends CApiBinaryBuiltinNode {
        @Specialization
        static Object values(Object type, Object args,
                        @Cached ExecutePositionalStarargsNode starArgsNode,
                        @Cached PyObjectLookupAttr lookupNode,
                        @Cached CallVarargsMethodNode call) {
            Object[] callArgs = starArgsNode.executeWith(null, args);
            Object fromTSCallable = lookupNode.execute(null, type, T_FROMTIMESTAMP);
            return call.execute(null, fromTSCallable, callArgs, PKeyword.EMPTY_KEYWORDS);
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {Int, Int, Int, Int, Int, Int, Int, PyObject, Int, PyTypeObject}, call = Ignored)
    abstract static class PyTruffleDateTimeCAPI_DateTime_FromDateAndTimeAndFold extends CApi10BuiltinNode {
        @Specialization
        static Object values(int year, int month, int day, int hour, int minute, int second, int usecond, Object tzinfo, int fold, Object type,
                        @Cached CallVarargsMethodNode call) {
            return call.execute(null, type, new Object[]{year, month, day, hour, minute, second, usecond, tzinfo}, new PKeyword[]{new PKeyword(T_FOLD, fold)});
        }
    }

    @CApiBuiltin(ret = PyObjectTransfer, args = {Int, Int, Int, Int, PyObject, Int, PyTypeObject}, call = Ignored)
    abstract static class PyTruffleDateTimeCAPI_Time_FromTimeAndFold extends CApi7BuiltinNode {
        @Specialization
        static Object values(int hour, int minute, int second, int usecond, Object tzinfo, int fold, Object type,
                        @Cached CallVarargsMethodNode call) {
            return call.execute(null, type, new Object[]{hour, minute, second, usecond, tzinfo}, new PKeyword[]{new PKeyword(T_FOLD, fold)});
        }
    }

    @CApiBuiltin(ret = PyObjectBorrowed, args = {PyObject}, call = Direct)
    abstract static class PyTruffle_PyDateTime_GET_TZINFO extends CApiUnaryBuiltinNode {
        @Specialization
        static Object get(Object obj,
                        @Cached PyObjectGetAttr getAttr) {
            return getAttr.execute(null, obj, PyDateTimeCAPIWrapper.T_TZINFO);
        }
    }
}
