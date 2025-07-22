/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;
import static com.oracle.graal.python.nodes.StringLiterals.T_NAME;
import static com.oracle.graal.python.nodes.StringLiterals.T_PATH;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
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
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.lib.PyUnicodeCheckExactNode;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonVarargsBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetDictIfExistsNode;
import com.oracle.graal.python.runtime.object.PFactory;
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

    public static final TpSlots SLOTS = ImportErrorBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ImportErrorBuiltinsFactory.getFactories();
    }

    protected static final int IDX_MSG = 0;
    protected static final int IDX_NAME = 1;
    protected static final int IDX_PATH = 2;
    public static final int IMPORT_ERR_NUM_ATTRS = IDX_PATH + 1;

    public static final BaseExceptionAttrNode.StorageFactory IMPORT_ERROR_ATTR_FACTORY = (args) -> {
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
    @Slot(value = SlotKind.tp_init, isComplex = true)
    @SlotSignature(minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class ImportErrorInitNode extends PythonVarargsBuiltinNode {
        private static final TruffleString NAME = tsLiteral("name");
        private static final TruffleString PATH = tsLiteral("path");

        @Specialization
        static Object init(PBaseException self, Object[] args, PKeyword[] kwargs,
                        @Bind Node inliningTarget,
                        @Cached BaseExceptionBuiltins.BaseExceptionInitNode baseExceptionInitNode,
                        @Cached TruffleString.EqualNode equalNode,
                        @Cached PRaiseNode raiseNode) {
            baseExceptionInitNode.execute(self, args);
            Object[] attrs = IMPORT_ERROR_ATTR_FACTORY.create(args);
            for (PKeyword kw : kwargs) {
                TruffleString kwName = kw.getName();
                if (equalNode.execute(kwName, NAME, TS_ENCODING)) {
                    attrs[IDX_NAME] = kw.getValue();
                } else if (equalNode.execute(kwName, PATH, TS_ENCODING)) {
                    attrs[IDX_PATH] = kw.getValue();
                } else {
                    throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, S_IS_AN_INVALID_ARG_FOR_S, kw.getName(), "ImportError");
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
        private static Object getState(Node inliningTarget, PBaseException self, GetDictIfExistsNode getDictIfExistsNode, HashingStorageSetItem setHashingStorageItem,
                        HashingStorageCopy copyStorageNode, BaseExceptionAttrNode attrNode, PythonLanguage language) {
            PDict dict = getDictIfExistsNode.execute(self);
            final Object name = attrNode.get(self, IDX_NAME, IMPORT_ERROR_ATTR_FACTORY);
            final Object path = attrNode.get(self, IDX_PATH, IMPORT_ERROR_ATTR_FACTORY);
            if (name != null || path != null) {
                HashingStorage storage = (dict != null) ? copyStorageNode.execute(inliningTarget, dict.getDictStorage()) : EmptyStorage.INSTANCE;
                if (name != null) {
                    storage = setHashingStorageItem.execute(inliningTarget, storage, T_NAME, name);
                }
                if (path != null) {
                    storage = setHashingStorageItem.execute(inliningTarget, storage, T_PATH, path);
                }
                return PFactory.createDict(language, storage);
            } else if (dict != null) {
                return dict;
            } else {
                return PNone.NONE;
            }
        }

        @Specialization
        static Object reduce(PBaseException self,
                        @Bind Node inliningTarget,
                        @Cached BaseExceptionAttrNode attrNode,
                        @Cached GetClassNode getClassNode,
                        @Cached GetDictIfExistsNode getDictIfExistsNode,
                        @Cached ExceptionNodes.GetArgsNode getArgsNode,
                        @Cached HashingStorageSetItem setHashingStorageItem,
                        @Cached HashingStorageCopy copyStorageNode,
                        @Bind PythonLanguage language) {
            Object clazz = getClassNode.execute(inliningTarget, self);
            Object args = getArgsNode.execute(inliningTarget, self);
            Object state = getState(inliningTarget, self, getDictIfExistsNode, setHashingStorageItem, copyStorageNode, attrNode, language);
            if (state == PNone.NONE) {
                return PFactory.createTuple(language, new Object[]{clazz, args});
            }
            return PFactory.createTuple(language, new Object[]{clazz, args, state});
        }
    }

    @Slot(value = SlotKind.tp_str, isComplex = true)
    @GenerateNodeFactory
    public abstract static class ImportErrorStrNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object str(VirtualFrame frame, PBaseException self,
                        @Bind Node inliningTarget,
                        @Cached BaseExceptionAttrNode attrNode,
                        @Cached BaseExceptionBuiltins.StrNode exStrNode,
                        @Cached PyUnicodeCheckExactNode unicodeCheckExactNode) {
            final Object msg = attrNode.get(self, IDX_MSG, IMPORT_ERROR_ATTR_FACTORY);
            if (msg != PNone.NONE && unicodeCheckExactNode.execute(inliningTarget, msg)) {
                return msg;
            } else {
                return exStrNode.execute(frame, self);
            }
        }
    }
}
