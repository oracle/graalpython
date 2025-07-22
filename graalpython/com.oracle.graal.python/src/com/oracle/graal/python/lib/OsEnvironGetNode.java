/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.lib;

import static com.oracle.graal.python.nodes.BuiltinNames.T_ENVIRON;
import static com.oracle.graal.python.nodes.BuiltinNames.T_OS;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.KeyError;

import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.statement.AbstractImportNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Helper to do os.environ.get(name) from Java. Only uncached for now. Returns <code>null</code>
 * when the key is not found or the value is not a string.
 */
@GenerateUncached
@GenerateCached(false)
public abstract class OsEnvironGetNode extends PNodeWithContext {
    protected abstract TruffleString execute(TruffleString name);

    public static TruffleString executeUncached(TruffleString name) {
        return OsEnvironGetNodeGen.getUncached().execute(name);
    }

    @TruffleBoundary
    @Specialization
    static TruffleString doLookup(TruffleString name) {
        Object os = AbstractImportNode.importModule(T_OS);
        Object environ = PyObjectGetAttr.executeUncached(os, T_ENVIRON);
        try {
            Object value = PyObjectGetItem.executeUncached(environ, name);
            return CastToTruffleStringNode.executeUncached(value);
        } catch (PException e) {
            e.expectUncached(KeyError);
        } catch (CannotCastException e) {
            // treat as if not there
        }
        return null;
    }
}
