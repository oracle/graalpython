/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.cext.hpy;

import com.oracle.graal.python.builtins.objects.cext.common.CExtAsPythonObjectNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtToJavaNode;
import com.oracle.graal.python.builtins.objects.cext.common.CExtToNativeNode;
import com.oracle.graal.python.builtins.objects.cext.common.ConversionNodeSupplier;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyAsHandleNodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyAsNativeInt64NodeGen;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyNodesFactory.HPyAsPythonObjectNodeGen;
import com.oracle.truffle.api.CompilerDirectives;

public abstract class GraalHPyConversionNodeSupplier {

    /**
     * A conversion node supplier for converting Python objects to native {@code HPy} compatible
     * values and also the opposite direction, for converting native {@code HPy} values to Python
     * objects.
     */
    public static final ConversionNodeSupplier HANDLE = new ConversionNodeSupplier() {

        @Override
        public CExtToNativeNode createToNativeNode() {
            return HPyAsHandleNodeGen.create();
        }

        @Override
        public CExtToNativeNode getUncachedToNativeNode() {
            return HPyAsHandleNodeGen.getUncached();
        }

        @Override
        public CExtAsPythonObjectNode createAsPythonObjectNode() {
            return HPyAsPythonObjectNodeGen.create();
        }

        @Override
        public CExtAsPythonObjectNode getUncachedAsPythonObjectNode() {
            return HPyAsPythonObjectNodeGen.getUncached();
        }

        @Override
        public CExtToJavaNode createToJavaNode() {
            return HPyAsPythonObjectNodeGen.create();
        }

        @Override
        public CExtToJavaNode getUncachedToJavaNode() {
            return HPyAsPythonObjectNodeGen.getUncached();
        }
    };

    /**
     * A conversion node supplier only for converting to native {@code int64_t} compatible values.
     * The opposite direction (converting native values to Python objects) is not implemented.
     */
    public static final ConversionNodeSupplier TO_INT64 = new ConversionNodeSupplier() {

        @Override
        public CExtToNativeNode createToNativeNode() {
            return HPyAsNativeInt64NodeGen.create();
        }

        @Override
        public CExtToNativeNode getUncachedToNativeNode() {
            return HPyAsNativeInt64NodeGen.getUncached();
        }

        @Override
        public CExtAsPythonObjectNode createAsPythonObjectNode() {
            throw CompilerDirectives.shouldNotReachHere("unsupported");
        }

        @Override
        public CExtAsPythonObjectNode getUncachedAsPythonObjectNode() {
            throw CompilerDirectives.shouldNotReachHere("unsupported");
        }

        @Override
        public CExtToJavaNode createToJavaNode() {
            throw CompilerDirectives.shouldNotReachHere("unsupported");
        }

        @Override
        public CExtToJavaNode getUncachedToJavaNode() {
            throw CompilerDirectives.shouldNotReachHere("unsupported");
        }
    };
}
