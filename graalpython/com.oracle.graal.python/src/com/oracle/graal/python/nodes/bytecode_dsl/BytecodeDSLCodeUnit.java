/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.bytecode_dsl;

import static com.oracle.graal.python.util.PythonUtils.isInterned;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * A context and language/engine-independent representation of code for the bytecode DSL
 * interpreter. Contains the bytecode supplier and related data, like constants and local variable
 * names. It doesn't contain the filename to make it easier to keep in native images.
 */
public final class BytecodeDSLCodeUnit {
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

    public final int classcellIndex;
    public final int selfIndex;
    public final int yieldFromGeneratorIndex;
    public final int instrumentationDataIndex;
    public final int maxProfileCEventStackSize;
    private final BytecodeSupplier supplier;

    public BytecodeDSLCodeUnit(TruffleString name, TruffleString qualname, int argCount, int kwOnlyArgCount, int positionalOnlyArgCount, int flags, TruffleString[] names, TruffleString[] varnames,
                    TruffleString[] cellvars, TruffleString[] freevars, int[] cell2arg, Object[] constants, int startLine, int startColumn, int endLine, int endColumn,
                    int classcellIndex, int selfIndex, int yieldFromGeneratorIndex, int instrumentationDataIndex, int maxProfileCEventStackSize, BytecodeSupplier supplier) {
        assert isInterned(name);
        this.name = name;
        assert qualname == null || isInterned(qualname);
        this.qualname = qualname != null ? qualname : name;
        this.argCount = argCount;
        this.kwOnlyArgCount = kwOnlyArgCount;
        this.positionalOnlyArgCount = positionalOnlyArgCount;
        this.flags = flags;
        assert isInterned(names);
        this.names = names;
        assert isInterned(varnames);
        this.varnames = varnames;
        assert isInterned(cellvars);
        this.cellvars = cellvars;
        assert isInterned(freevars);
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
        this.classcellIndex = classcellIndex;
        this.selfIndex = selfIndex;
        this.supplier = supplier;
        this.yieldFromGeneratorIndex = yieldFromGeneratorIndex;
        this.instrumentationDataIndex = instrumentationDataIndex;
        this.maxProfileCEventStackSize = maxProfileCEventStackSize;
    }

    public abstract static class BytecodeSupplier {
        public abstract PBytecodeDSLRootNode createRootNode(PythonLanguage language);

        public abstract byte[] createSerializedBytecode(PythonLanguage language);
    }

    public BytecodeDSLCodeUnit withFlags(int flags) {
        return new BytecodeDSLCodeUnit(name, qualname, argCount, kwOnlyArgCount, positionalOnlyArgCount, flags,
                        names, varnames, cellvars, freevars, cell2arg, constants,
                        startLine, startColumn, endLine, endColumn, classcellIndex, selfIndex, yieldFromGeneratorIndex, instrumentationDataIndex, maxProfileCEventStackSize, supplier);
    }

    @TruffleBoundary
    public PBytecodeDSLRootNode createRootNode(PythonLanguage language, boolean isInternal) {
        // We must not cache deserialized root, because the code unit may be shared by multiple
        // engines. The caller is responsible for ensuring the caching of the resulting root node if
        // necessary
        PBytecodeDSLRootNode rootNode = supplier.createRootNode(language);
        rootNode.setMetadata(this, null, isInternal);
        return rootNode;
    }

    public byte[] getSerialized(PythonLanguage language) {
        CompilerAsserts.neverPartOfCompilation();
        return supplier.createSerializedBytecode(language);
    }

    public TruffleString getDocstring() {
        // The first constant in the code unit is the docstring (if available) or PNone.
        if (constants.length > 0 && constants[0] instanceof TruffleString docstring) {
            return docstring;
        }
        return null;
    }

    public SourceSection getSourceSection(Source source) {
        if (!source.hasCharacters()) {
            return source.createUnavailableSection();
        }
        try {
            int truffleStartColumn = Math.max(startColumn + 1, 1);
            int truffleEndColumn = Math.max(endColumn + 1, 1);
            if (truffleEndColumn == source.getLineLength(endLine) + 1) {
                truffleEndColumn--;
            }
            return source.createSection(startLine, truffleStartColumn, endLine, truffleEndColumn);
        } catch (IllegalArgumentException e) {
            // TODO GR-40896 we don't track source ranges of f-strings correctly
            // Also consider sources created from ast module
            return source.createUnavailableSection();
        }
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

    public boolean isIterableCoroutine() {
        return (flags & PCode.CO_ITERABLE_COROUTINE) != 0;
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

    public Signature computeSignature() {
        int posArgCount = argCount + positionalOnlyArgCount;
        TruffleString[] parameterNames = Arrays.copyOf(varnames, posArgCount);
        TruffleString[] kwOnlyNames = Arrays.copyOfRange(varnames, posArgCount, posArgCount + kwOnlyArgCount);
        int varArgsIndex = takesVarArgs() ? posArgCount : -1;
        return new Signature(positionalOnlyArgCount,
                        takesVarKeywordArgs(),
                        varArgsIndex,
                        parameterNames,
                        kwOnlyNames);
    }

    @Override
    public String toString() {
        return toString(false, null);
    }

    /**
     * @param optimized Whether to print the initial state of the bytecode or current state, if
     *            available, where some instructions may be transformed, e.g., quickened.
     * @param rootNode The root node if available.
     */
    public String toString(boolean optimized, RootNode rootNode) {
        StringBuilder sb = new StringBuilder();

        sb.append("Disassembly of ").append(qualname).append(":\n");

        List<String> flagNames = new ArrayList<>();
        if (isGenerator()) {
            flagNames.add("CO_GENERATOR");
        }
        if (isCoroutine()) {
            flagNames.add("CO_COROUTINE");
        }
        if (isAsyncGenerator()) {
            flagNames.add("CO_ASYNC_GENERATOR");
        }
        if (!flagNames.isEmpty()) {
            sb.append("Flags: ").append(String.join(" | ", flagNames)).append("\n");
        }

        if (rootNode instanceof PBytecodeDSLRootNode dslRoot) {
            sb.append(dslRoot.dump());
            sb.append('\n');
        } else {
            sb.append("bytecode not available\n");
        }

        for (Object c : constants) {
            if (c instanceof BytecodeDSLCodeUnit cd) {
                sb.append('\n');
                sb.append(cd.toString(optimized, null));
            }
        }

        return sb.toString();
    }
}
