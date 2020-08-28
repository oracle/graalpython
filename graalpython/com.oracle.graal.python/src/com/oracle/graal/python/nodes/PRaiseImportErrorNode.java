/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.nodes.call.special.CallVarargsMethodNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.formatting.ErrorMessageFormatter;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.CachedLanguage;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;

@GenerateUncached
@ImportStatic(PGuards.class)
public abstract class PRaiseImportErrorNode extends Node {
    private static final ErrorMessageFormatter FORMATTER = new ErrorMessageFormatter();

    public abstract PException execute(Frame frame, Object name, Object path, String format, Object[] formatArgs);

    public final PException raiseImportError(Frame frame, Object name, Object path, String format, Object... formatArgs) {
        return execute(frame, name, path, format, formatArgs);
    }

    public final PException raiseImportError(Frame frame, String format, Object... formatArgs) {
        return execute(frame, PNone.NO_VALUE, PNone.NO_VALUE, format, formatArgs);
    }

    @CompilerDirectives.TruffleBoundary
    private static String getFormattedMessage(PythonObjectLibrary pol, String format, Object[] formatArgs) {
        return FORMATTER.format(pol, format, formatArgs);
    }

    @Specialization
    PException raiseImportError(VirtualFrame frame, Object name, Object path, String format, Object[] formatArgs,
                    @Cached CallVarargsMethodNode callNode,
                    @CachedLibrary(limit = "3") PythonObjectLibrary pol,
                    @CachedLanguage PythonLanguage language,
                    @CachedContext(PythonLanguage.class) PythonContext context) {
        PythonCore core = context.getCore();
        PBaseException error = (PBaseException) callNode.execute(frame, core.lookupType(PythonBuiltinClassType.ImportError),
                        new Object[]{getFormattedMessage(pol, format, formatArgs)}, new PKeyword[]{
                                        new PKeyword("name", name), new PKeyword("path", path)});
        return PRaiseNode.raise(this, error, PythonOptions.isPExceptionWithJavaStacktrace(language));
    }

    public static PRaiseImportErrorNode create() {
        return PRaiseImportErrorNodeGen.create();
    }
}
