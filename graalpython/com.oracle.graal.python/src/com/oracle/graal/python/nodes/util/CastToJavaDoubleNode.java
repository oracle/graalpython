/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.util;

import static com.oracle.graal.python.builtins.objects.cext.structs.CFields.PyFloatObject__ob_fval;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.MathGuards;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.cext.structs.CStructAccess;
import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.GetClassNode.GetPythonObjectClassNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Casts a Python "number" to a Java double without coercion. <b>ATTENTION:</b> If the cast fails,
 * because the object is not a Python float, the node will throw a {@link CannotCastException}.
 */
@GenerateUncached
@GenerateInline
@GenerateCached(false)
@TypeSystemReference(PythonArithmeticTypes.class)
@ImportStatic(MathGuards.class)
public abstract class CastToJavaDoubleNode extends PNodeWithContext {

    public abstract double execute(Node inliningTarget, Object x);

    @Specialization
    static double toDouble(double x) {
        return x;
    }

    @Specialization
    static double doBoolean(boolean x) {
        return x ? 1.0 : 0.0;
    }

    @Specialization
    static double doInt(int x) {
        return x;
    }

    @Specialization
    static double doLong(long x) {
        return x;
    }

    @Specialization
    double doPInt(PInt x) {
        return x.doubleValueWithOverflow(this);
    }

    @Specialization
    static double doString(@SuppressWarnings("unused") TruffleString object) {
        throw CannotCastException.INSTANCE;
    }

    @Specialization
    static double doPBCT(@SuppressWarnings("unused") PythonBuiltinClassType object) {
        throw CannotCastException.INSTANCE;
    }

    @Specialization
    static double doNativeObject(Node inliningTarget, PythonAbstractNativeObject x,
                    @Cached GetPythonObjectClassNode getClassNode,
                    @Cached(inline = false) IsSubtypeNode isSubtypeNode,
                    @Cached(inline = false) CStructAccess.ReadDoubleNode read) {
        if (isSubtypeNode.execute(getClassNode.execute(inliningTarget, x), PythonBuiltinClassType.PFloat)) {
            return read.readFromObj(x, PyFloatObject__ob_fval);
        }
        // the object's type is not a subclass of 'float'
        throw CannotCastException.INSTANCE;
    }

    public static Double doInterop(Object obj,
                    InteropLibrary interopLibrary) {
        try {
            if (interopLibrary.fitsInDouble(obj)) {
                return interopLibrary.asDouble(obj);
            }
            if (interopLibrary.fitsInLong(obj)) {
                return (double) interopLibrary.asLong(obj);
            }
            if (interopLibrary.isBoolean(obj)) {
                return interopLibrary.asBoolean(obj) ? 1.0 : 0.0;
            }
        } catch (UnsupportedMessageException e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
        return null;
    }

    @Specialization(guards = "!isNumber(obj)")
    static double doGeneric(Object obj,
                    @CachedLibrary(limit = "3") InteropLibrary interopLibrary) {
        Double d = doInterop(obj, interopLibrary);
        if (d != null) {
            return d;
        }
        throw CannotCastException.INSTANCE;
    }
}
