/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.runtime.formatting;

import java.util.Formatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

/**
 * Custom formatter adding Python-specific conversions often required in error messages.
 * <p>
 * The following conversions are additionally available to {@link Formatter}:
 * <dl>
 * <dt>%p</dt>
 * <dd>Determines the Python class of the corresponding object and prints its name.</dd>
 * <dt>%P</dt>
 * <dd>Determines the Python class of the corresponding object and prints the class' {@code repr}
 * string.</dd>
 * </dl>
 * </p>
 */
public class ErrorMessageFormatter {

    private static final Object REMOVED_MARKER = new Object();

    // %[argument_index$][flags][width][.precision][t]conversion
    private static final String formatSpecifier = "%(\\d+\\$)?([-#+ 0,(\\<]*)?(\\d+)?(\\.\\d+)?([tT])?([a-zA-Z%])";

    private static Pattern fsPattern = Pattern.compile(formatSpecifier);

    public String format(String format, Object... args) {
        return format(null, format, args);
    }

    public String format(PythonObjectLibrary lib, String format, Object... args) {
        Matcher m = fsPattern.matcher(format);
        StringBuilder sb = new StringBuilder(format);
        int removedCnt = 0;
        int matchIdx = 0;
        int idx = 0;
        int offset = 0;
        while (m.find(idx)) {
            String group = m.group();
            if ("%p".equals(group)) {
                String name = getClassName(lib, args[matchIdx]);
                sb.replace(m.start() + offset, m.end() + offset, name);
                offset += name.length() - (m.end() - m.start());
                args[matchIdx] = REMOVED_MARKER;
                removedCnt++;
            } else if ("%P".equals(group)) {
                String name = "<class \'" + getClassName(lib, args[matchIdx]) + "\'>";
                sb.replace(m.start() + offset, m.end() + offset, name);
                offset += name.length() - (m.end() - m.start());
                args[matchIdx] = REMOVED_MARKER;
                removedCnt++;
            } else if ("%m".equals(group) && args[matchIdx] instanceof Throwable) {
                // If the format arg is not a Throwable, 'String.format' will do the error handling
                // and throw an IllegalFormatException for us.
                String exceptionMessage = getMessage((Throwable) args[matchIdx]);
                sb.replace(m.start() + offset, m.end() + offset, exceptionMessage);
                offset += exceptionMessage.length() - (m.end() - m.start());
                args[matchIdx] = REMOVED_MARKER;
                removedCnt++;
            }

            idx = m.end();
            // '%%' is an escape sequence and does not consume an argument
            if (!"%%".equals(group)) {
                matchIdx++;
            }
        }
        return String.format(sb.toString(), compact(args, removedCnt));
    }

    @TruffleBoundary
    private static String getMessage(Throwable exception) {
        return exception.getClass().getSimpleName() + ": " + exception.getMessage();
    }

    private static String getClassName(PythonObjectLibrary lib, Object obj) {
        if (lib != null) {
            return GetNameNode.doSlowPath(lib.getLazyPythonClass(obj));
        }
        return GetNameNode.doSlowPath(PythonObjectLibrary.getUncached().getLazyPythonClass(obj));
    }

    /**
     * Use this method to check if a given format string contains any of the custom format
     * specifiers handled by this formatter.
     */
    public static boolean containsCustomSpecifier(String format) {
        int pidx = -1;
        while ((pidx = format.indexOf('%', pidx + 1)) != -1 && pidx + 1 < format.length()) {
            char c = format.charAt(pidx + 1);
            if (c == 'p' || c == 'P' || c == 'm') {
                return true;
            }
        }
        return false;
    }

    private static Object[] compact(Object[] args, int removedCnt) {
        Object[] compacted = new Object[args.length - removedCnt];
        int j = 0;
        for (int i = 0; i < args.length; i++) {
            if (args[i] != REMOVED_MARKER) {
                compacted[j++] = args[i];
            }
        }
        return compacted;
    }
}
