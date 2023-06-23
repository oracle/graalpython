/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.exception;

import static com.oracle.graal.python.nodes.ErrorMessages.S_IS_AN_INVALID_ARG_FOR_S;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___STR__;
import static com.oracle.graal.python.nodes.StringLiterals.T_NAME;
import static com.oracle.graal.python.nodes.StringLiterals.T_PATH;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.EmptyStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageCopy;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageSetItem;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.lib.PyUnicodeCheckExactNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.object.GetDictIfExistsNode;
import com.oracle.graal.python.nodes.object.InlinedGetClassNode;
import com.oracle.graal.python.nodes.util.SplitArgsNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.ImportError)
public final class ImportErrorBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ImportErrorBuiltinsFactory.getFactories();
    }

    protected static final int IDX_MSG = 0;
    protected static final int IDX_NAME = 1;
    protected static final int IDX_PATH = 2;
    public static final int IMPORT_ERR_NUM_ATTRS = IDX_PATH + 1;

    public static final BaseExceptionAttrNode.StorageFactory IMPORT_ERROR_ATTR_FACTORY = (args, factory) -> {
        Object[] attrs = new Object[IMPORT_ERR_NUM_ATTRS];
        if (args.length == 1) {
            attrs[IDX_MSG] = args[0];
        }
        return attrs;
    };

    /*
     * The user-visible signature is: 'ImportError.__init__(self, /, name, path)'. That means, the
     * init method takes 'name' and 'path' as keyword-only parameters. We still need to specify
     * 'takesVarArgs = true' and ' takesVarKeywordArgs = true' because otherwise the created root
     * node would fail.
     */
    @Builtin(name = J___INIT__, minNumOfPositionalArgs = 1, //
                    takesVarArgs = true, varArgsMarker = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class ImportErrorInitNode extends PythonVarargsBuiltinNode {
        private static final TruffleString NAME = tsLiteral("name");
        private static final TruffleString PATH = tsLiteral("path");

        @Child private SplitArgsNode splitArgsNode;

        @Override
        public Object varArgExecute(VirtualFrame frame, Object self, Object[] arguments, PKeyword[] keywords) throws VarargsBuiltinDirectInvocationNotSupported {
            if (arguments.length == 0) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw VarargsBuiltinDirectInvocationNotSupported.INSTANCE;
            }
            if (splitArgsNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                splitArgsNode = insert(SplitArgsNode.create());
            }
            Object[] argsWithoutSelf = splitArgsNode.execute(arguments);
            return execute(frame, arguments[0], argsWithoutSelf, keywords);
        }

        @Specialization
        Object init(PBaseException self, Object[] args, PKeyword[] kwargs,
                        @Cached BaseExceptionBuiltins.BaseExceptionInitNode baseExceptionInitNode,
                        @Cached TruffleString.EqualNode equalNode) {
            baseExceptionInitNode.execute(self, args);
            Object[] attrs = IMPORT_ERROR_ATTR_FACTORY.create(args, null);
            for (PKeyword kw : kwargs) {
                TruffleString kwName = kw.getName();
                if (equalNode.execute(kwName, NAME, TS_ENCODING)) {
                    attrs[IDX_NAME] = kw.getValue();
                } else if (equalNode.execute(kwName, PATH, TS_ENCODING)) {
                    attrs[IDX_PATH] = kw.getValue();
                } else {
                    throw raise(PythonBuiltinClassType.TypeError, S_IS_AN_INVALID_ARG_FOR_S, kw.getName(), "ImportError");
                }
            }
            self.setExceptionAttributes(attrs);
            return PNone.NONE;
        }
    }

    @Builtin(name = "msg", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, allowsDelete = true, doc = "exception message")
    @GenerateNodeFactory
    public abstract static class ImportErrorMsgNode extends PythonBuiltinNode {
        @Specialization
        Object generic(PBaseException self, Object value,
                        @Cached BaseExceptionAttrNode attrNode) {
            return attrNode.execute(self, value, IDX_MSG, IMPORT_ERROR_ATTR_FACTORY);
        }
    }

    @Builtin(name = "name", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, allowsDelete = true, doc = "module name")
    @GenerateNodeFactory
    public abstract static class ImportErrorNameNode extends PythonBuiltinNode {
        @Specialization
        Object generic(PBaseException self, Object value,
                        @Cached BaseExceptionAttrNode attrNode) {
            return attrNode.execute(self, value, IDX_NAME, IMPORT_ERROR_ATTR_FACTORY);
        }
    }

    @Builtin(name = "path", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, allowsDelete = true, doc = "module path")
    @GenerateNodeFactory
    public abstract static class ImportErrorPathNode extends PythonBuiltinNode {
        @Specialization
        Object generic(PBaseException self, Object value,
                        @Cached BaseExceptionAttrNode attrNode) {
            return attrNode.execute(self, value, IDX_PATH, IMPORT_ERROR_ATTR_FACTORY);
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ImportErrorReduceNode extends PythonUnaryBuiltinNode {
        private Object getState(PBaseException self, GetDictIfExistsNode getDictIfExistsNode, HashingStorageSetItem setHashingStorageItem, HashingStorageCopy copyStorageNode,
                        BaseExceptionAttrNode attrNode) {
            PDict dict = getDictIfExistsNode.execute(self);
            final Object name = attrNode.get(self, IDX_NAME, IMPORT_ERROR_ATTR_FACTORY);
            final Object path = attrNode.get(self, IDX_PATH, IMPORT_ERROR_ATTR_FACTORY);
            if (name != null || path != null) {
                HashingStorage storage = (dict != null) ? copyStorageNode.execute(dict.getDictStorage()) : EmptyStorage.INSTANCE;
                if (name != null) {
                    storage = setHashingStorageItem.execute(storage, T_NAME, name);
                }
                if (path != null) {
                    storage = setHashingStorageItem.execute(storage, T_PATH, path);
                }
                return factory().createDict(storage);
            } else if (dict != null) {
                return dict;
            } else {
                return PNone.NONE;
            }
        }

        @Specialization
        Object reduce(PBaseException self,
                        @Bind("this") Node inliningTarget,
                        @Cached BaseExceptionAttrNode attrNode,
                        @Cached InlinedGetClassNode getClassNode,
                        @Cached GetDictIfExistsNode getDictIfExistsNode,
                        @Cached ExceptionNodes.GetArgsNode getArgsNode,
                        @Cached HashingStorageSetItem setHashingStorageItem,
                        @Cached HashingStorageCopy copyStorageNode) {
            Object clazz = getClassNode.execute(inliningTarget, self);
            Object args = getArgsNode.execute(inliningTarget, self);
            Object state = getState(self, getDictIfExistsNode, setHashingStorageItem, copyStorageNode, attrNode);
            if (state == PNone.NONE) {
                return factory().createTuple(new Object[]{clazz, args});
            }
            return factory().createTuple(new Object[]{clazz, args, state});
        }
    }

    @Builtin(name = J___STR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ImportErrorStrNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object str(VirtualFrame frame, PBaseException self,
                        @Cached BaseExceptionAttrNode attrNode,
                        @Cached BaseExceptionBuiltins.StrNode exStrNode,
                        @Cached PyUnicodeCheckExactNode unicodeCheckExactNode) {
            final Object msg = attrNode.get(self, IDX_MSG, IMPORT_ERROR_ATTR_FACTORY);
            if (msg != PNone.NONE && unicodeCheckExactNode.execute(msg)) {
                return msg;
            } else {
                return exStrNode.execute(frame, self);
            }
        }
    }
}
