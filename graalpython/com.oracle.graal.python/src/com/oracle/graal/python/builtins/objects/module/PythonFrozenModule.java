/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.module;

import static com.oracle.graal.python.nodes.StringLiterals.T_LANGLE;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import java.io.IOException;
import java.io.InputStream;

import org.graalvm.nativeimage.ImageInfo;

import com.oracle.graal.python.builtins.modules.MarshalModuleBuiltins;
import com.oracle.graal.python.compiler.CodeUnit;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.strings.TruffleString;

public final class PythonFrozenModule {
    private final String symbol;
    private final TruffleString originalName;
    private final boolean isPackage;
    private CodeUnit code;

    private void initCode() {
        try {
            InputStream resourceAsStream = PythonFrozenModule.class.getResourceAsStream("Frozen" + symbol + "." + getSuffix());
            if (resourceAsStream != null) {
                byte[] bytes = resourceAsStream.readAllBytes();
                // TODO exception handling
                code = MarshalModuleBuiltins.deserializeCodeUnit(null, null, bytes);
            }
        } catch (IOException e) {
            // fall-through
        }
    }

    private static String getSuffix() {
        if (PythonOptions.ENABLE_BYTECODE_DSL_INTERPRETER) {
            return "bin_dsl";
        } else {
            return "bin";
        }
    }

    public PythonFrozenModule(String symbol, String originalName, boolean isPackage) {
        this(symbol, toTruffleStringUncached(originalName), isPackage);
    }

    private PythonFrozenModule(String symbol, TruffleString originalName, boolean isPackage) {
        this.symbol = symbol;
        this.originalName = originalName;
        this.isPackage = isPackage;
        if (ImageInfo.inImageBuildtimeCode()) {
            initCode();
        }
    }

    public PythonFrozenModule asPackage(boolean flag) {
        if (flag == isPackage) {
            return this;
        } else {
            TruffleString origName = originalName;
            if (isPackage) {
                origName = T_LANGLE.concatUncached(originalName, TS_ENCODING, false);
            }
            return new PythonFrozenModule(symbol, origName, flag);
        }
    }

    public TruffleString getOriginalName() {
        return originalName;
    }

    public CodeUnit getCode() {
        if (!ImageInfo.inImageCode() && code == null) {
            initCode();
        }
        return code;
    }

    public boolean isPackage() {
        return isPackage;
    }
}
