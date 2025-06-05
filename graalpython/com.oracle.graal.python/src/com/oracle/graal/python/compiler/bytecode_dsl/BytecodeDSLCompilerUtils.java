/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.compiler.bytecode_dsl;

import java.util.HashMap;

import com.oracle.graal.python.pegparser.sst.ArgTy;
import com.oracle.graal.python.pegparser.sst.ArgumentsTy;

class BytecodeDSLCompilerUtils {
    public static final ArgumentsTy NO_ARGS = new ArgumentsTy(new ArgTy[0], null, null, null, null, null, null, null);
    public static final ArgumentsTy TYPE_PARAMS_DEFAULTS = createSimpleArgs(".defaults", null);
    public static final ArgumentsTy TYPE_PARAMS_KWDEFAULTS = createSimpleArgs(".kwdefaults", null);
    public static final ArgumentsTy TYPE_PARAMS_DEFAULTS_KWDEFAULTS = createSimpleArgs(".defaults", ".kwdefaults");
    private static final String COMPREHENSION_ARGUMENT_NAME = ".0";
    public static final ArgumentsTy COMPREHENSION_ARGS = createSimpleArgs(COMPREHENSION_ARGUMENT_NAME, null);

    static <T> int len(T[] arr) {
        return arr == null ? 0 : arr.length;
    }

    static boolean hasDefaultArgs(ArgumentsTy args) {
        return args != null && len(args.defaults) > 0;
    }

    static boolean hasDefaultKwargs(ArgumentsTy args) {
        if (args != null && len(args.kwDefaults) != 0) {
            for (int i = 0; i < args.kwDefaults.length; i++) {
                if (args.kwDefaults[i] != null) {
                    return true;
                }
            }
        }
        return false;
    }

    private static ArgumentsTy createSimpleArgs(String arg1, String arg2) {
        ArgTy[] posArgs;
        if (arg2 == null) {
            posArgs = new ArgTy[]{
                            new ArgTy(arg1, null, null, null)
            };
        } else {
            posArgs = new ArgTy[]{
                            new ArgTy(arg1, null, null, null),
                            new ArgTy(arg2, null, null, null),
            };
        }
        return new ArgumentsTy(posArgs, null, null, null, null, null, null, null);
    }

    static <T> int addObject(HashMap<T, Integer> dict, T o) {
        Integer v = dict.get(o);
        if (v == null) {
            v = dict.size();
            dict.put(o, v);
        }
        return v;
    }
}
