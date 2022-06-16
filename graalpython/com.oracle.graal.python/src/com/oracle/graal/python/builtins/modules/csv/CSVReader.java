/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.csv;

import static com.oracle.graal.python.builtins.modules.csv.CSVReader.ReaderState.START_RECORD;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.strings.TruffleStringBuilder;

public final class CSVReader extends PythonBuiltinObject {

    enum ReaderState {
        START_RECORD,
        START_FIELD,
        ESCAPED_CHAR,
        IN_FIELD,
        IN_QUOTED_FIELD,
        ESCAPE_IN_QUOTED_FIELD,
        QUOTE_IN_QUOTED_FIELD,
        EAT_CRNL,
        AFTER_ESCAPED_CRNL
    }

    final Object inputIter; /* iterate over this for input lines */
    final CSVDialect dialect; /* parsing dialect */
    ReaderState state; /* current CSV parse state */
    TruffleStringBuilder field; /* temporary buffer */
    boolean numericField; /* treat field as numeric */
    int lineNum; /* Source-file line number */
    long fieldLimit; /* Cached copy of CSVModuleBuiltins.fieldLimit */

    public CSVReader(Object cls, Shape instanceShape, Object inputIter, CSVDialect dialect) {
        super(cls, instanceShape);
        this.inputIter = inputIter;
        this.dialect = dialect;
        lineNum = 0;
    }

    void parseReset() {
        this.field = TruffleStringBuilder.create(TS_ENCODING);
        this.state = START_RECORD;
        this.numericField = false;
    }
}
