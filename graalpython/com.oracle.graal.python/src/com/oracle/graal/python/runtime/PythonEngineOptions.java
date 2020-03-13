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
package com.oracle.graal.python.runtime;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionValues;

public class PythonEngineOptions {
    @CompilationFinal private OptionValues optionValues;

    private final int builtinsInliningMaxCallerSize;
    private final boolean catchAllExceptions;
    private final boolean emulatedJython;
    private final boolean exposeInternalSources;
    private final boolean withThread;

    PythonEngineOptions(OptionValues optionValues) {
        CompilerAsserts.neverPartOfCompilation();
        this.optionValues = optionValues;
        this.builtinsInliningMaxCallerSize = readIntegerOption(PythonOptions.BuiltinsInliningMaxCallerSize);
        this.catchAllExceptions = readBooleanOption(PythonOptions.CatchAllExceptions);
        this.emulatedJython = readBooleanOption(PythonOptions.EmulateJython);
        this.exposeInternalSources = readBooleanOption(PythonOptions.ExposeInternalSources);
        this.withThread = readBooleanOption(PythonOptions.WithThread);
    }

    public static PythonEngineOptions fromOptionValues(OptionValues optionValues) {
        return new PythonEngineOptions(optionValues);
    }

    private boolean readBooleanOption(OptionKey<Boolean> key) {
        return key.getValue(optionValues);
    }

    private int readIntegerOption(OptionKey<Integer> key) {
        return key.getValue(optionValues);
    }

    public boolean areInternalSourcesExposed() {
        return this.exposeInternalSources;
    }

    public boolean isCatchingAllExcetptionsEnabled() {
        return this.catchAllExceptions;
    }

    public boolean isJythonEmulated() {
        return this.emulatedJython;
    }

    public boolean isWithThread() {
        return this.withThread;
    }

    public int getBuiltinsInliningMaxCallerSize() {
        return this.builtinsInliningMaxCallerSize;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 53 * hash + this.builtinsInliningMaxCallerSize;
        hash = 53 * hash + (this.catchAllExceptions ? 1 : 0);
        hash = 53 * hash + (this.emulatedJython ? 1 : 0);
        hash = 53 * hash + (this.exposeInternalSources ? 1 : 0);
        hash = 53 * hash + (this.withThread ? 1 : 0);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final PythonEngineOptions other = (PythonEngineOptions) obj;
        if (this.builtinsInliningMaxCallerSize != other.builtinsInliningMaxCallerSize) {
            return false;
        }
        if (this.catchAllExceptions != other.catchAllExceptions) {
            return false;
        }
        if (this.emulatedJython != other.emulatedJython) {
            return false;
        }
        if (this.exposeInternalSources != other.exposeInternalSources) {
            return false;
        }
        return this.withThread == other.withThread;
    }
}
