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

public abstract class PythonUnaryClinicBuiltinNode extends PythonUnaryBuiltinNode {
    @Child ArgumentCastNode castNode;

    /**
     * Returns the provider of argument clinic logic. It should be singleton instance of a class
     * generated from the {@link ArgumentClinic} annotations.
     */
    protected abstract ArgumentClinicProvider getArgumentClinic();

    private Object cast(VirtualFrame frame, int argIndex, Object value) {
        // no point in using argument clinic if the only argument does not have a cast node
        ArgumentClinicProvider clinic = getArgumentClinic();
        assert clinic.hasCastNode(argIndex);
        if (castNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            castNode = insert(clinic.createCastNode(argIndex, this));
        }
        return castNode.execute(frame, value);
    }

    @Override
    public Object call(VirtualFrame frame, Object arg) {
        return execute(frame, cast(frame, 0, arg));
    }

    @Override
    public boolean callBool(VirtualFrame frame, boolean arg) throws UnexpectedResultException {
        if (getArgumentClinic().acceptsBoolean(0)) {
            return executeBool(frame, arg);
        } else {
            return PGuards.expectBoolean(call(frame, arg));
        }
    }

    @Override
    public int callInt(VirtualFrame frame, int arg) throws UnexpectedResultException {
        if (getArgumentClinic().acceptsInt(0)) {
            return executeInt(frame, arg);
        } else {
            return PGuards.expectInteger(call(frame, arg));
        }
    }

    @Override
    public long callLong(VirtualFrame frame, long arg) throws UnexpectedResultException {
        if (getArgumentClinic().acceptsLong(0)) {
            return executeLong(frame, arg);
        } else {
            return PGuards.expectLong(call(frame, arg));
        }
    }

    @Override
    public double callDouble(VirtualFrame frame, double arg) throws UnexpectedResultException {
        if (getArgumentClinic().acceptsDouble(0)) {
            return executeDouble(frame, arg);
        } else {
            return PGuards.expectDouble(call(frame, arg));
        }
    }

    @Override
    public boolean callBool(VirtualFrame frame, int arg) throws UnexpectedResultException {
        if (getArgumentClinic().acceptsInt(0)) {
            return executeBool(frame, arg);
        } else {
            return PGuards.expectBoolean(call(frame, arg));
        }
    }

    @Override
    public boolean callBool(VirtualFrame frame, long arg) throws UnexpectedResultException {
        if (getArgumentClinic().acceptsLong(0)) {
            return executeBool(frame, arg);
        } else {
            return PGuards.expectBoolean(call(frame, arg));
        }
    }

    @Override
    public boolean callBool(VirtualFrame frame, double arg) throws UnexpectedResultException {
        if (getArgumentClinic().acceptsDouble(0)) {
            return executeBool(frame, arg);
        } else {
            return PGuards.expectBoolean(call(frame, arg));
        }
    }
}
