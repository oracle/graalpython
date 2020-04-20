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
package com.oracle.graal.python.runtime.exception;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

public final class ExceptionUtils {
    private ExceptionUtils() {
    }

    /**
     * this method is similar to 'PyErr_WriteUnraisable'
     */
    @TruffleBoundary
    public static void printPythonLikeStackTrace(Throwable e) {
        List<TruffleStackTraceElement> stackTrace = TruffleStackTrace.getStackTrace(e);
        if (stackTrace != null) {
            ArrayList<String> stack = new ArrayList<>();
            for (TruffleStackTraceElement frame : stackTrace) {

                StringBuilder sb = new StringBuilder();
                Node location = frame.getLocation();
                SourceSection sourceSection = location != null ? location.getSourceSection() : null;
                String rootName = frame.getTarget().getRootNode().getName();
                if (sourceSection != null) {
                    sb.append("  ");
                    String path = sourceSection.getSource().getPath();
                    if (path != null) {
                        sb.append("File ");
                    }
                    sb.append('"');
                    sb.append(sourceSection.getSource().getName());
                    sb.append("\", line ");
                    sb.append(sourceSection.getStartLine());
                    sb.append(", in ");
                    sb.append(rootName);
                    stack.add(sb.toString());
                }
            }
            System.err.println("Traceback (most recent call last):");
            ListIterator<String> listIterator = stack.listIterator(stack.size());
            while (listIterator.hasPrevious()) {
                System.err.println(listIterator.previous());
            }
        }
        System.err.println(e.getMessage());
    }
}
