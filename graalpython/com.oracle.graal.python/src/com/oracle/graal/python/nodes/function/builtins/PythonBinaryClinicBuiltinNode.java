/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.function.builtins;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentCastNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.UnexpectedResultException;

public abstract class PythonBinaryClinicBuiltinNode extends PythonBinaryBuiltinNode {
    private @Children ArgumentCastNode[] castNodes;

    /**
     * Returns the provider of argument clinic logic. It should be singleton instance of a class
     * generated from the {@link ArgumentClinic} annotations.
     */
    protected abstract ArgumentClinicProvider getArgumentClinic();

    private Object cast(ArgumentClinicProvider clinic, VirtualFrame frame, int argIndex, Object value) {
        if (!clinic.hasCastNode(argIndex)) {
            return value;
        } else {
            return castWithNode(clinic, frame, argIndex, value);
        }
    }

    protected Object castWithNode(ArgumentClinicProvider clinic, VirtualFrame frame, int argIndex, Object value) {
        if (castNodes == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            castNodes = new ArgumentCastNode[2];
        }
        if (castNodes[argIndex] == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            castNodes[argIndex] = insert(clinic.createCastNode(argIndex, this));
        }
        return castNodes[argIndex].execute(frame, value);
    }

    @Override
    public final Object call(VirtualFrame frame, Object arg, Object arg2) {
        ArgumentClinicProvider clinic = getArgumentClinic();
        return execute(frame, cast(clinic, frame, 0, arg), cast(clinic, frame, 1, arg2));
    }

    @Override
    public final boolean callBool(VirtualFrame frame, boolean arg, boolean arg2) throws UnexpectedResultException {
        ArgumentClinicProvider clinic = getArgumentClinic();
        if (clinic.acceptsBoolean(0) && clinic.acceptsBoolean(1)) {
            return executeBool(frame, arg, arg2);
        } else {
            return PGuards.expectBoolean(call(frame, arg, arg2));
        }
    }

    @Override
    public final int callInt(VirtualFrame frame, boolean arg, boolean arg2) throws UnexpectedResultException {
        ArgumentClinicProvider clinic = getArgumentClinic();
        if (clinic.acceptsBoolean(0) && clinic.acceptsBoolean(1)) {
            return executeInt(frame, arg, arg2);
        } else {
            return PGuards.expectInteger(call(frame, arg, arg2));
        }
    }

    @Override
    public final int callInt(VirtualFrame frame, int arg, int arg2) throws UnexpectedResultException {
        ArgumentClinicProvider clinic = getArgumentClinic();
        if (clinic.acceptsInt(0) && clinic.acceptsInt(1)) {
            return executeInt(frame, arg, arg2);
        } else {
            return PGuards.expectInteger(call(frame, arg, arg2));
        }
    }

    @Override
    public final long callLong(VirtualFrame frame, long arg, long arg2) throws UnexpectedResultException {
        ArgumentClinicProvider clinic = getArgumentClinic();
        if (clinic.acceptsLong(0) && clinic.acceptsLong(1)) {
            return executeLong(frame, arg, arg2);
        } else {
            return PGuards.expectLong(call(frame, arg, arg2));
        }
    }

    @Override
    public final double callDouble(VirtualFrame frame, long arg, double arg2) throws UnexpectedResultException {
        ArgumentClinicProvider clinic = getArgumentClinic();
        if (clinic.acceptsLong(0) && clinic.acceptsDouble(1)) {
            return executeDouble(frame, arg, arg2);
        } else {
            return PGuards.expectDouble(call(frame, arg, arg2));
        }
    }

    @Override
    public final double callDouble(VirtualFrame frame, double arg, long arg2) throws UnexpectedResultException {
        ArgumentClinicProvider clinic = getArgumentClinic();
        if (clinic.acceptsDouble(0) && clinic.acceptsLong(1)) {
            return executeDouble(frame, arg, arg2);
        } else {
            return PGuards.expectDouble(call(frame, arg, arg2));
        }
    }

    @Override
    public final double callDouble(VirtualFrame frame, double arg, double arg2) throws UnexpectedResultException {
        ArgumentClinicProvider clinic = getArgumentClinic();
        if (clinic.acceptsDouble(0) && clinic.acceptsDouble(1)) {
            return executeDouble(frame, arg, arg2);
        } else {
            return PGuards.expectDouble(call(frame, arg, arg2));
        }
    }

    @Override
    public final boolean callBool(VirtualFrame frame, int arg, int arg2) throws UnexpectedResultException {
        ArgumentClinicProvider clinic = getArgumentClinic();
        if (clinic.acceptsInt(0) && clinic.acceptsInt(1)) {
            return executeBool(frame, arg, arg2);
        } else {
            return PGuards.expectBoolean(call(frame, arg, arg2));
        }
    }

    @Override
    public final boolean callBool(VirtualFrame frame, long arg, long arg2) throws UnexpectedResultException {
        ArgumentClinicProvider clinic = getArgumentClinic();
        if (clinic.acceptsLong(0) && clinic.acceptsLong(1)) {
            return executeBool(frame, arg, arg2);
        } else {
            return PGuards.expectBoolean(call(frame, arg, arg2));
        }
    }

    @Override
    public final boolean callBool(VirtualFrame frame, long arg, double arg2) throws UnexpectedResultException {
        ArgumentClinicProvider clinic = getArgumentClinic();
        if (clinic.acceptsLong(0) && clinic.acceptsDouble(1)) {
            return executeBool(frame, arg, arg2);
        } else {
            return PGuards.expectBoolean(call(frame, arg, arg2));
        }
    }

    @Override
    public final boolean callBool(VirtualFrame frame, double arg, long arg2) throws UnexpectedResultException {
        ArgumentClinicProvider clinic = getArgumentClinic();
        if (clinic.acceptsDouble(0) && clinic.acceptsLong(1)) {
            return executeBool(frame, arg, arg2);
        } else {
            return PGuards.expectBoolean(call(frame, arg, arg2));
        }
    }

    @Override
    public final boolean callBool(VirtualFrame frame, double arg, double arg2) throws UnexpectedResultException {
        ArgumentClinicProvider clinic = getArgumentClinic();
        if (clinic.acceptsDouble(0) && clinic.acceptsDouble(1)) {
            return executeBool(frame, arg, arg2);
        } else {
            return PGuards.expectBoolean(call(frame, arg, arg2));
        }
    }
}
