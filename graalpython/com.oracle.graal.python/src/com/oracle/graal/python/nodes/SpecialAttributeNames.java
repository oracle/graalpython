/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes;

import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import com.oracle.truffle.api.strings.TruffleString;

public abstract class SpecialAttributeNames {
    public static final TruffleString T___ = tsLiteral("_");

    public static final String J___DOC__ = "__doc__";
    public static final TruffleString T___DOC__ = tsLiteral(J___DOC__);

    public static final String J___DEFAULTS__ = "__defaults__";
    public static final TruffleString T___DEFAULTS__ = tsLiteral(J___DEFAULTS__);

    public static final String J___CODE__ = "__code__";
    public static final TruffleString T___CODE__ = tsLiteral(J___CODE__);

    public static final String J___GLOBALS__ = "__globals__";

    public static final String J___CLASSCELL__ = "__classcell__";
    public static final TruffleString T___CLASSCELL__ = tsLiteral(J___CLASSCELL__);

    public static final String J___CLOSURE__ = "__closure__";

    public static final String J___ANNOTATIONS__ = "__annotations__";
    public static final TruffleString T___ANNOTATIONS__ = tsLiteral(J___ANNOTATIONS__);

    public static final String J___KWDEFAULTS__ = "__kwdefaults__";

    public static final String J___SELF__ = "__self__";

    public static final String J___FUNC__ = "__func__";

    public static final String J___MODULE__ = "__module__";
    public static final TruffleString T___MODULE__ = tsLiteral(J___MODULE__);

    public static final String J___DICT__ = "__dict__";
    public static final TruffleString T___DICT__ = tsLiteral(J___DICT__);

    public static final String J___CLASS__ = "__class__";
    public static final TruffleString T___CLASS__ = tsLiteral(J___CLASS__);

    public static final String J___BASE__ = "__base__";

    public static final TruffleString T___NEWOBJ__ = tsLiteral("__newobj__");

    public static final TruffleString T___NEWOBJ_EX__ = tsLiteral("__newobj_ex__");

    public static final String J___BASES__ = "__bases__";
    public static final TruffleString T___BASES__ = tsLiteral(J___BASES__);

    public static final String J___NAME__ = "__name__";
    public static final TruffleString T___NAME__ = tsLiteral(J___NAME__);

    public static final String J___QUALNAME__ = "__qualname__";
    public static final TruffleString T___QUALNAME__ = tsLiteral(J___QUALNAME__);

    public static final String J___MRO__ = "__mro__";

    public static final TruffleString T___LOADER__ = tsLiteral("__loader__");

    public static final TruffleString T___PACKAGE__ = tsLiteral("__package__");

    public static final TruffleString T___SPEC__ = tsLiteral("__spec__");

    public static final TruffleString T___PATH__ = tsLiteral("__path__");

    public static final TruffleString T___FILE__ = tsLiteral("__file__");

    public static final TruffleString T___LIBRARY__ = tsLiteral("__library__");

    public static final TruffleString T___ORIGNAME__ = tsLiteral("__origname__");

    public static final TruffleString T___CACHED__ = tsLiteral("__cached__");

    public static final String J___TEXT_SIGNATURE__ = "__text_signature__";
    public static final TruffleString T___TEXT_SIGNATURE__ = tsLiteral(J___TEXT_SIGNATURE__);

    public static final String J___TRACEBACK__ = "__traceback__";
    public static final TruffleString T___TRACEBACK__ = tsLiteral(J___TRACEBACK__);

    public static final String J___CAUSE__ = "__cause__";
    public static final TruffleString T___CAUSE__ = tsLiteral(J___CAUSE__);

    public static final String J___CONTEXT__ = "__context__";
    public static final TruffleString T___CONTEXT__ = tsLiteral(J___CONTEXT__);

    public static final String J___SUPPRESS_CONTEXT__ = "__suppress_context__";

    public static final String J___BASICSIZE__ = "__basicsize__";

    public static final TruffleString T___SLOTS__ = tsLiteral("__slots__");

    public static final TruffleString T___SLOTNAMES__ = tsLiteral("__slotnames__");

    public static final String J___DICTOFFSET__ = "__dictoffset__";

    public static final String J___WEAKLISTOFFSET__ = "__weaklistoffset__";
    public static final TruffleString T___WEAKLISTOFFSET__ = tsLiteral(J___WEAKLISTOFFSET__);

    public static final String J___WEAKREFOFFSET__ = "__weakrefoffset__";

    public static final String J___ITEMSIZE__ = "__itemsize__";

    public static final TruffleString T___WEAKREF__ = tsLiteral("__weakref__");

    public static final TruffleString T___ALL__ = tsLiteral("__all__");

    public static final String J___FLAGS__ = "__flags__";

    public static final String J___ABSTRACTMETHODS__ = "__abstractmethods__";
    public static final TruffleString T___ABSTRACTMETHODS__ = tsLiteral(J___ABSTRACTMETHODS__);

    public static final TruffleString T___ORIG_BASES__ = tsLiteral("__orig_bases__");

    public static final String J___ORIGIN__ = "__origin__";
    public static final TruffleString T___ORIGIN__ = tsLiteral(J___ORIGIN__);

    public static final String J___ARGS__ = "__args__";
    public static final TruffleString T___ARGS__ = tsLiteral(J___ARGS__);

    public static final String J___PARAMETERS__ = "__parameters__";
    public static final TruffleString T___PARAMETERS__ = tsLiteral(J___PARAMETERS__);

    public static final String J___ORIG_CLASS__ = "__orig_class__";
    public static final TruffleString T___ORIG_CLASS__ = tsLiteral(J___ORIG_CLASS__);

    public static final String J___WRAPPED__ = "__wrapped__";

    // specific to super
    public static final String J___THISCLASS__ = "__thisclass__";
    public static final String J___SELF_CLASS__ = "__self_class__";

    public static final String J___MATCH_ARGS__ = "__match_args__";
    public static final TruffleString T___MATCH_ARGS__ = tsLiteral(J___MATCH_ARGS__);

    public static final String J___VECTORCALLOFFSET__ = "__vectorcalloffset__";
    public static final TruffleString T___VECTORCALLOFFSET__ = tsLiteral(J___VECTORCALLOFFSET__);
}
