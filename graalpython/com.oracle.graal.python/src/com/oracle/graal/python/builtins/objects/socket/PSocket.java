/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.socket;

import java.net.InetSocketAddress;

import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;

public class PSocket extends PythonBuiltinObject {
    public static final int AF_UNSPEC = 0;
    public static final int AF_INET = 2;
    public static final int AF_INET6 = 23;

    public static final int SOCK_DGRAM = 1;
    public static final int SOCK_STREAM = 2;

    private static final InetSocketAddress EPHEMERAL_ADDRESS = new InetSocketAddress(0);

    private final int family;
    private final int type;
    private final int proto;

    private double timeout;

    private InetSocketAddress address = EPHEMERAL_ADDRESS;

    public PSocket(LazyPythonClass cls, int family, int type, int proto) {
        super(cls);
        this.family = family;
        this.type = type;
        this.proto = proto;
    }

    public int getFamily() {
        return family;
    }

    public int getType() {
        return type;
    }

    public int getProto() {
        return proto;
    }

    public double getTimeout() {
        return timeout;
    }

    public void setTimeout(double timeout) {
        this.timeout = timeout;
    }

    public InetSocketAddress getAddress() {
        return address;
    }

    public void setBlocking(boolean blocking) {
        if (blocking) {
            this.setTimeout(-1.0);
        } else {
            this.setTimeout(0.0);
        }
    }
}
