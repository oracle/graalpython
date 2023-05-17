/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.compiler;

import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___CLASS__;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.util.Arrays;

import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * A context-independent representation of code for bytecode interpreter. Contains the actual
 * bytecode and all the related data, like constants or exception handler ranges. It doesn't contain
 * the filename to make it easier to keep in native images.
 */
public abstract class CodeUnit {
    public final TruffleString name;
    public final TruffleString qualname;

    public final int argCount;
    public final int kwOnlyArgCount;
    public final int positionalOnlyArgCount;

    public final int flags;

    @CompilationFinal(dimensions = 1) public final TruffleString[] names;
    @CompilationFinal(dimensions = 1) public final TruffleString[] varnames;
    @CompilationFinal(dimensions = 1) public final TruffleString[] cellvars;
    @CompilationFinal(dimensions = 1) public final TruffleString[] freevars;
    @CompilationFinal(dimensions = 1) public final int[] cell2arg;
    @CompilationFinal(dimensions = 1) public final int[] arg2cell;

    @CompilationFinal(dimensions = 1) public final Object[] constants;

    public final int startLine;
    public final int startColumn;
    public final int endLine;
    public final int endColumn;

    public CodeUnit(TruffleString name, TruffleString qualname,
                    int argCount, int kwOnlyArgCount, int positionalOnlyArgCount, int flags,
                    TruffleString[] names, TruffleString[] varnames, TruffleString[] cellvars,
                    TruffleString[] freevars, int[] cell2arg, Object[] constants, int startLine, int startColumn,
                    int endLine, int endColumn) {
        this.name = name;
        this.qualname = qualname != null ? qualname : name;
        this.argCount = argCount;
        this.kwOnlyArgCount = kwOnlyArgCount;
        this.positionalOnlyArgCount = positionalOnlyArgCount;
        this.flags = flags;
        this.names = names;
        this.varnames = varnames;
        this.cellvars = cellvars;
        this.freevars = freevars;
        this.cell2arg = cell2arg;
        int[] arg2cellValue = null;
        if (cell2arg != null) {
            arg2cellValue = new int[getTotalArgCount()];
            Arrays.fill(arg2cellValue, -1);
            for (int i = 0; i < cell2arg.length; i++) {
                if (cell2arg[i] >= 0) {
                    arg2cellValue[cell2arg[i]] = i;
                }
            }
        }
        this.arg2cell = arg2cellValue;
        this.constants = constants;

        this.startLine = startLine;
        this.startColumn = startColumn;
        this.endLine = endLine;
        this.endColumn = endColumn;
    }

    public SourceSection getSourceSection(Source source) {
        return SourceMap.getSourceSection(source, startLine, startColumn, endLine, endColumn);
    }

    public boolean takesVarKeywordArgs() {
        return (flags & PCode.CO_VARKEYWORDS) != 0;
    }

    public boolean takesVarArgs() {
        return (flags & PCode.CO_VARARGS) != 0;
    }

    public boolean isGenerator() {
        return (flags & PCode.CO_GENERATOR) != 0;
    }

    public boolean isCoroutine() {
        return (flags & PCode.CO_COROUTINE) != 0;
    }

    public boolean isAsyncGenerator() {
        return (flags & PCode.CO_ASYNC_GENERATOR) != 0;
    }

    public boolean isGeneratorOrCoroutine() {
        return (flags & (PCode.CO_GENERATOR | PCode.CO_COROUTINE | PCode.CO_ASYNC_GENERATOR | PCode.CO_ITERABLE_COROUTINE)) != 0;
    }

    public int getRegularArgCount() {
        return argCount + positionalOnlyArgCount + kwOnlyArgCount;
    }

    public int getTotalArgCount() {
        int count = getRegularArgCount();
        if (takesVarArgs()) {
            count++;
        }
        if (takesVarKeywordArgs()) {
            count++;
        }
        return count;
    }

    public final Signature computeSignature() {
        int posArgCount = argCount + positionalOnlyArgCount;
        TruffleString[] parameterNames = Arrays.copyOf(varnames, posArgCount);
        TruffleString[] kwOnlyNames = Arrays.copyOfRange(varnames, posArgCount, posArgCount + kwOnlyArgCount);
        int varArgsIndex = takesVarArgs() ? posArgCount : -1;
        return new Signature(positionalOnlyArgCount,
                        takesVarKeywordArgs(),
                        varArgsIndex,
                        positionalOnlyArgCount > 0,
                        parameterNames,
                        kwOnlyNames);
    }

    public final int getClassCellIndex() {
        for (int i = 0; i < this.freevars.length; i++) {
            if (T___CLASS__.equalsUncached(this.freevars[i], TS_ENCODING)) {
                return i + varnames.length + cellvars.length;
            }
        }
        return -1;
    }

    public final int getSelfIndex() {
        if (getTotalArgCount() != 0) {
            if (cell2arg != null) {
                for (int i = 0; i < cell2arg.length; i++) {
                    if (cell2arg[i] == 0) {
                        return i + varnames.length;
                    }
                }
            }
            return 0;
        }
        return -1;
    }

}
