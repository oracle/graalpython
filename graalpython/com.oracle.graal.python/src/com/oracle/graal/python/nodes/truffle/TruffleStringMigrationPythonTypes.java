/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.truffle;

import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import com.oracle.truffle.api.dsl.ImplicitCast;
import com.oracle.truffle.api.dsl.TypeSystem;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Temporary {@link TypeSystem} used during migration of j.l.String -> TruffleString. Once the
 * migration is complete, this type system can be deleted.
 *
 * The same implicit cast is also in {@link PythonTypes} and {@link PythonArithmeticTypes}
 */
@TypeSystem
public abstract class TruffleStringMigrationPythonTypes {

    @ImplicitCast
    public static TruffleString fromJavaString(String value) {
        assert false;
        return TruffleString.fromJavaStringUncached(value, TS_ENCODING);
    }

    /**
     * Used in places where we don't expect a {@link String}.
     */
    public static Object assertNoJavaString(Object o) {
        assert !(o instanceof String);
        return o;
    }

    /**
     * Used in places where we accept a {@link String}, but we want it silently converted to a
     * {@link TruffleString}.
     */
    public static Object ensureNoJavaString(Object o) {
        if (o instanceof String) {
            return toTruffleStringUncached((String) o);
        }
        return o;
    }

    public static boolean isJavaString(Object o) {
        assert !(o instanceof String);
        return false;
    }

    public static boolean containsJavaString(Object[] elements) {
        if (elements == null) {
            return false;
        }
        for (Object o : elements) {
            if (o instanceof String) {
                return true;
            }
        }
        return false;
    }
}
