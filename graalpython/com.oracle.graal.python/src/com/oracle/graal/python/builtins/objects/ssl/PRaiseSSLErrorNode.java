/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.ssl;

import static com.oracle.graal.python.builtins.objects.ssl.SSLErrorCode.ERROR_CERT_VERIFICATION;

import java.util.IllegalFormatException;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.formatting.ErrorMessageFormatter;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

@GenerateUncached
public abstract class PRaiseSSLErrorNode extends Node {
    protected abstract PException execute(Node node, SSLErrorCode type, String message, Object[] args);

    public final PException raise(SSLErrorCode type, String message, Object... args) {
        return execute(this, type, message, args);
    }

    public static PException raiseUncached(Node node, SSLErrorCode type, String message, Object... args) {
        return PRaiseSSLErrorNodeGen.getUncached().execute(node, type, message, args);
    }

    public static PException raiseUncached(Node node, SSLErrorCode type, Exception e) {
        return raiseUncached(node, type, getMessage(e));
    }

    @TruffleBoundary
    private static String getMessage(Exception e) {
        String msg = e.getMessage();
        return msg != null ? msg : e.getClass().getSimpleName();
    }

    @Specialization
    static PException raise(Node node, SSLErrorCode type, String format, Object[] formatArgs,
                    @Cached PythonObjectFactory factory,
                    @Cached WriteAttributeToObjectNode writeAttribute) {
        String message = getFormattedMessage(format, formatArgs);
        PBaseException exception = factory.createBaseException(type.getType(), factory.createTuple(new Object[]{type.getErrno(), message}));
        writeAttribute.execute(exception, "errno", type.getErrno());
        writeAttribute.execute(exception, "strerror", message);
        // TODO properly populate reason/lib attrs, this are dummy values
        writeAttribute.execute(exception, "reason", message);
        writeAttribute.execute(exception, "library", "[SSL]");
        if (type == ERROR_CERT_VERIFICATION) {
            // not trying to be 100% correct,
            // use code = 1 (X509_V_ERR_UNSPECIFIED) and msg from jdk exception instead
            // see openssl x509_txt.c#X509_verify_cert_error_string
            writeAttribute.execute(exception, "verify_code", 1);
            writeAttribute.execute(exception, "verify_message", message);
        }
        return PRaiseNode.raise(node, exception, PythonOptions.isPExceptionWithJavaStacktrace(PythonLanguage.get(node)));
    }

    @TruffleBoundary
    private static String getFormattedMessage(String format, Object... args) {
        try {
            // pre-format for custom error message formatter
            if (ErrorMessageFormatter.containsCustomSpecifier(format)) {
                return new ErrorMessageFormatter().format(format, args);
            }
            return String.format(format, args);
        } catch (IllegalFormatException e) {
            throw CompilerDirectives.shouldNotReachHere("error while formatting \"" + format + "\"", e);
        }
    }
}
