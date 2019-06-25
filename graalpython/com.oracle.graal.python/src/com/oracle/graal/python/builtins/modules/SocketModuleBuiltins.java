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
package com.oracle.graal.python.builtins.modules;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.socket.PSocket;
import com.oracle.graal.python.builtins.objects.type.LazyPythonClass;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

@CoreFunctions(defineModule = "_socket")
public class SocketModuleBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return SocketModuleBuiltinsFactory.getFactories();
    }

    // socket(family=AF_INET, type=SOCK_STREAM, proto=0, fileno=None)
    @Builtin(name = "socket", minNumOfPositionalArgs = 1, parameterNames = {"cls", "family", "type", "proto", "fileno"}, constructsClass = PythonBuiltinClassType.PSocket)
    @GenerateNodeFactory
    public abstract static class SocketNode extends PythonBuiltinNode {
        @Specialization(guards = {"isNoValue(family)", "isNoValue(type)", "isNoValue(proto)", "isNoValue(fileno)"})
        Object socket(LazyPythonClass cls, @SuppressWarnings("unused") PNone family, @SuppressWarnings("unused") PNone type, @SuppressWarnings("unused") PNone proto,
                        @SuppressWarnings("unused") PNone fileno) {
            return createSocketInternal(cls, PSocket.AF_INET, PSocket.SOCK_STREAM, 0);
        }

        @Specialization(guards = {"isNoValue(type)", "isNoValue(proto)", "isNoValue(fileno)"})
        Object socket(LazyPythonClass cls, int family, @SuppressWarnings("unused") PNone type, @SuppressWarnings("unused") PNone proto, @SuppressWarnings("unused") PNone fileno) {
            return createSocketInternal(cls, family, PSocket.SOCK_STREAM, 0);
        }

        @Specialization(guards = {"isNoValue(proto)", "isNoValue(fileno)"})
        Object socket(LazyPythonClass cls, int family, int type, @SuppressWarnings("unused") PNone proto, @SuppressWarnings("unused") PNone fileno) {
            return createSocketInternal(cls, family, type, 0);
        }

        @Specialization(guards = {"isNoValue(fileno)"})
        Object socket(LazyPythonClass cls, int family, int type, int proto, @SuppressWarnings("unused") PNone fileno) {
            return createSocketInternal(cls, family, type, proto);
        }

        private Object createSocketInternal(LazyPythonClass cls, int family, int type, int proto) {
            if (getContext().getEnv().isNativeAccessAllowed()) {
                return factory().createSocket(cls, family, type, proto);
            } else {
                throw raise(PythonErrorType.OSError, "creating sockets not allowed");
            }
        }
    }

    @Builtin(name = "gethostname", minNumOfPositionalArgs = 0)
    @GenerateNodeFactory
    public abstract static class GetHostnameNode extends PythonBuiltinNode {
        @Specialization
        @TruffleBoundary
        String doGeneric() {
            try {
                return InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException e) {
                throw raise(PythonBuiltinClassType.OSError);
            }
        }
    }
}
