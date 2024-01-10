/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.pickle;

import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.J_ATTR_MEMO;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.J_METHOD_FIND_CLASS;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.J_METHOD_LOAD;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.J_METHOD_PERSISTENT_LOAD;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.T_METHOD_PERSISTENT_LOAD;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INIT__;

import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingCollectionNodes;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorKey;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorNext;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorValue;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.lib.PyCallableCheckNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.Unpickler)
public class UnpicklerBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return UnpicklerBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___INIT__, minNumOfPositionalArgs = 2, declaresExplicitSelf = true, parameterNames = {"$self", "file"}, varArgsMarker = true, keywordOnlyNames = {"fix_imports", "encoding",
                    "errors",
                    "buffers"})
    @ArgumentClinic(name = "fix_imports", conversion = ArgumentClinic.ClinicConversion.Boolean, defaultValue = "true")
    @ArgumentClinic(name = "encoding", conversion = ArgumentClinic.ClinicConversion.TString, defaultValue = "T_ASCII_UPPERCASE")
    @ArgumentClinic(name = "errors", conversion = ArgumentClinic.ClinicConversion.TString, defaultValue = "T_STRICT")
    @GenerateNodeFactory
    public abstract static class UnpicklerInitNode extends PythonClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return UnpicklerBuiltinsClinicProviders.UnpicklerInitNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static Object load(VirtualFrame frame, PUnpickler self, Object file, boolean fixImports, TruffleString encoding, TruffleString errors, Object buffers,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectLookupAttr lookup,
                        @Cached PyObjectGetIter getIter,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            self.setInputStream(frame, inliningTarget, raiseNode, lookup, file);
            self.setInputEncoding(encoding, errors);
            self.setBuffers(frame, inliningTarget, getIter, buffers);
            self.setFixImports(fixImports);
            self.initInternals(frame, inliningTarget, lookup);
            self.setProto(0);
            return PNone.NONE;
        }
    }

    // functions
    @Builtin(name = J_METHOD_LOAD, minNumOfPositionalArgs = 1, parameterNames = {"$self"})
    @GenerateNodeFactory
    public abstract static class UnpicklerLoadNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object load(VirtualFrame frame, PUnpickler self,
                        @Bind("this") Node inliningTarget,
                        @Cached PUnpickler.LoadNode loadNode,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            if (self.getRead() == null) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.UnpicklingError, ErrorMessages.INIT_CALLED_WITH, "Unpickler", self);
            }
            return loadNode.execute(frame, self);
        }
    }

    @Builtin(name = J_METHOD_FIND_CLASS, minNumOfPositionalArgs = 3, parameterNames = {"$self", "module", "name"})
    @ArgumentClinic(name = "module", conversion = ArgumentClinic.ClinicConversion.TString)
    @ArgumentClinic(name = "name", conversion = ArgumentClinic.ClinicConversion.TString)
    @GenerateNodeFactory
    public abstract static class UnpicklerFindClassNode extends PythonTernaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return UnpicklerBuiltinsClinicProviders.UnpicklerFindClassNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static Object findClass(VirtualFrame frame, PUnpickler self, TruffleString module, TruffleString name,
                        @Cached PUnpickler.FindClassNode findClassNode) {
            return findClassNode.execute(frame, self, module, name);
        }
    }

    @Builtin(name = J_METHOD_PERSISTENT_LOAD, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    public abstract static class UnpicklerPersistentLoadNode extends PythonBuiltinNode {
        @Specialization(guards = "isNoValue(none)")
        static Object get(PUnpickler self, @SuppressWarnings("unused") PNone none,
                        @Bind("this") Node inliningTarget,
                        @Cached PythonObjectFactory factory,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            final Object persFunc = self.getPersFunc();
            if (persFunc == null) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.AttributeError, T_METHOD_PERSISTENT_LOAD);
            }
            return PickleUtils.reconstructMethod(factory, persFunc, self.getPersFuncSelf());
        }

        @Specialization(guards = "!isNoValue(obj)")
        static Object set(PUnpickler self, Object obj,
                        @Bind("this") Node inliningTarget,
                        @Cached PyCallableCheckNode callableCheck,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            if (PGuards.isDeleteMarker(obj)) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, ErrorMessages.ATRIBUTE_DELETION_NOT_SUPPORTED);
            }
            if (!callableCheck.execute(inliningTarget, obj)) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, ErrorMessages.ATTR_MUST_BE_A_CALLABLE, T_METHOD_PERSISTENT_LOAD, "one argument");
            }
            self.setPersFuncSelf(null);
            self.setPersFunc(obj);
            return PNone.NONE;
        }
    }

    @Builtin(name = J_ATTR_MEMO, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    public abstract static class UnpicklerMemoNode extends PythonBuiltinNode {
        @Specialization(guards = "isNoValue(none)")
        static Object get(PUnpickler self, @SuppressWarnings("unused") PNone none,
                        @Cached PythonObjectFactory factory) {
            return factory.createUnpicklerMemoProxy(self);
        }

        @Specialization(guards = {"!isNoValue(obj)", "!isDeleteMarker(obj)"})
        static Object set(VirtualFrame frame, PUnpickler self, Object obj,
                        @Bind("this") Node inliningTarget,
                        @Cached HashingCollectionNodes.GetHashingStorageNode getHashingStorageNode,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached PyObjectSizeNode sizeNode,
                        @Cached HashingStorageGetIterator getStorageIter,
                        @Cached HashingStorageIteratorNext storageIterNext,
                        @Cached HashingStorageIteratorKey storageIterKey,
                        @Cached HashingStorageIteratorValue storageIterValue,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (obj instanceof PUnpicklerMemoProxy) {
                final PUnpickler unpickler = ((PUnpicklerMemoProxy) obj).getUnpickler();
                self.setMemo(unpickler.getMemoCopy());
            } else if (obj instanceof PDict) {
                self.setMemo(new Object[sizeNode.execute(frame, inliningTarget, obj)]);
                final HashingStorage dictStorage = getHashingStorageNode.execute(frame, inliningTarget, obj);
                HashingStorageIterator it = getStorageIter.execute(inliningTarget, dictStorage);
                while (storageIterNext.execute(inliningTarget, dictStorage, it)) {
                    Object key = storageIterKey.execute(inliningTarget, dictStorage, it);
                    Object value = storageIterValue.execute(inliningTarget, dictStorage, it);
                    if (!PGuards.canBeInteger(key)) {
                        throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, ErrorMessages.MEMO_KEY_MUST_BE_INT);
                    }
                    final int idx = asSizeNode.executeExact(frame, inliningTarget, key);
                    if (idx < 0) {
                        throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.ValueError, ErrorMessages.MEMO_KEY_MUST_BE_POS_INT);
                    }
                    self.memoPut(idx, value);
                }
            } else {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, ErrorMessages.ATTR_MUST_BE_A_OR_B_NOT_C, "memo", "UnpicklerMemoProxy", "dict", obj);
            }
            return PNone.NONE;
        }
    }
}
