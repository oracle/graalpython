/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.ssl;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__STR__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.exception.BaseExceptionDataAttrNode;
import com.oracle.graal.python.builtins.objects.exception.OsErrorBuiltins;
import com.oracle.graal.python.builtins.objects.exception.PBaseException;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.lib.PyObjectStrAsObjectNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

@CoreFunctions(extendClasses = PythonBuiltinClassType.SSLError)
public class SSLErrorBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return SSLErrorBuiltinsFactory.getFactories();
    }

    @CompilerDirectives.ValueType
    public static class SSLErrorData extends OsErrorBuiltins.OSErrorData {
        private Object reason;
        private Object library;
        private Object verifyCode;
        private Object verifyMessage;

        public SSLErrorData(OsErrorBuiltins.OSErrorData data) {
            this.setMyerrno(data.getMyerrno());
            this.setFilename(data.getFilename());
            this.setFilename2(data.getFilename2());
            this.setStrerror(data.getStrerror());
            this.setWinerror(data.getWinerror());
        }

        public Object getReason() {
            return reason;
        }

        public void setReason(Object reason) {
            this.reason = reason;
        }

        public Object getLibrary() {
            return library;
        }

        public void setLibrary(Object library) {
            this.library = library;
        }

        public Object getVerifyCode() {
            return verifyCode;
        }

        public void setVerifyCode(Object verifyCode) {
            this.verifyCode = verifyCode;
        }

        public Object getVerifyMessage() {
            return verifyMessage;
        }

        public void setVerifyMessage(Object verifyMessage) {
            this.verifyMessage = verifyMessage;
        }
    }

    @Builtin(name = __INIT__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class SSLErrorInitNode extends PythonBuiltinNode {
        public abstract Object execute(VirtualFrame frame, PBaseException self, Object[] args, PKeyword[] kwds);

        @Specialization
        Object init(VirtualFrame frame, PBaseException self, Object[] args, PKeyword[] kwds,
                    @Cached OsErrorBuiltins.OSErrorInitNode initNode) {
            initNode.execute(frame, self, args, kwds);
            SSLErrorData sslData = new SSLErrorData((OsErrorBuiltins.OSErrorData) self.getData());
            self.setData(sslData);
            return PNone.NONE;
        }
    }

    @Builtin(name = "reason", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, doc = "exception strerror")
    @GenerateNodeFactory
    public abstract static class SSLErrorReasonNode extends BaseExceptionDataAttrNode {
        @Override
        protected Object get(PBaseException.Data data) {
            assert data instanceof SSLErrorData;
            return ((SSLErrorData) data).getReason();
        }

        @Override
        protected void set(PBaseException.Data data, Object value) {
            assert data instanceof SSLErrorData;
            ((SSLErrorData) data).setReason(value);
        }
    }

    @Builtin(name = "library", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, doc = "exception strerror")
    @GenerateNodeFactory
    public abstract static class SSLErrorLibNode extends BaseExceptionDataAttrNode {
        @Override
        protected Object get(PBaseException.Data data) {
            assert data instanceof SSLErrorData;
            return ((SSLErrorData) data).getLibrary();
        }

        @Override
        protected void set(PBaseException.Data data, Object value) {
            assert data instanceof SSLErrorData;
            ((SSLErrorData) data).setLibrary(value);
        }
    }

    @Builtin(name = "verify_code", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, doc = "exception strerror")
    @GenerateNodeFactory
    public abstract static class SSLErrorVerifCodeNode extends BaseExceptionDataAttrNode {
        @Override
        protected Object get(PBaseException.Data data) {
            assert data instanceof SSLErrorData;
            return ((SSLErrorData) data).getVerifyCode();
        }

        @Override
        protected void set(PBaseException.Data data, Object value) {
            assert data instanceof SSLErrorData;
            ((SSLErrorData) data).setVerifyCode(value);
        }
    }

    @Builtin(name = "verify_message", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, doc = "exception strerror")
    @GenerateNodeFactory
    public abstract static class SSLErrorVerifMsgNode extends BaseExceptionDataAttrNode {
        @Override
        protected Object get(PBaseException.Data data) {
            assert data instanceof SSLErrorData;
            return ((SSLErrorData) data).getVerifyMessage();
        }

        @Override
        protected void set(PBaseException.Data data, Object value) {
            assert data instanceof SSLErrorData;
            ((SSLErrorData) data).setVerifyMessage(value);
        }
    }

    @Builtin(name = __STR__, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class StrNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object str(VirtualFrame frame, PBaseException self,
                        @Cached PyObjectStrAsObjectNode strNode) {
            assert self.getData() instanceof OsErrorBuiltins.OSErrorData;
            Object strerror = ((OsErrorBuiltins.OSErrorData) self.getData()).getStrerror();
            if (PGuards.isString(strerror)) {
                return strerror;
            }
            return strNode.execute(frame, self.getArgs());
        }
    }

}
