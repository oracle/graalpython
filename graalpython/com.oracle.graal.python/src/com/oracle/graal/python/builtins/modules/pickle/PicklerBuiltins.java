package com.oracle.graal.python.builtins.modules.pickle;

import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.J_ATTR_BIN;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.J_ATTR_DISPATCH_TABLE;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.J_ATTR_FAST;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.J_ATTR_MEMO;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.J_DEFAULT_PICKLE_PROTOCOL;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.J_METHOD_CLEAR_MEMO;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.J_METHOD_DUMP;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.J_METHOD_PERSISTENT_ID;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.T_ATTR_DISPATCH_TABLE;
import static com.oracle.graal.python.builtins.modules.pickle.PickleUtils.T_METHOD_PERSISTENT_ID;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SIZEOF__;

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
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorNext;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorValue;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyCallableCheckNode;
import com.oracle.graal.python.lib.PyLongAsLongNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

@CoreFunctions(extendClasses = PythonBuiltinClassType.Pickler)
public class PicklerBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PicklerBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___INIT__, minNumOfPositionalArgs = 2, declaresExplicitSelf = true, parameterNames = {"$self", "file", "protocol", "fix_imports", "buffer_callback"})
    @ArgumentClinic(name = "protocol", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = J_DEFAULT_PICKLE_PROTOCOL, useDefaultForNone = true)
    @ArgumentClinic(name = "fix_imports", conversion = ArgumentClinic.ClinicConversion.Boolean, defaultValue = "true")
    @GenerateNodeFactory
    public abstract static class PicklerInitNode extends PythonClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return PicklerBuiltinsClinicProviders.PicklerInitNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static Object init(VirtualFrame frame, PPickler self, Object file, int protocol, boolean fixImports, Object bufferCallback,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectLookupAttr lookup,
                        @Cached PRaiseNode.Lazy raiseNode) {
            self.setProtocol(inliningTarget, raiseNode, protocol, fixImports);
            self.setOutputStream(frame, inliningTarget, raiseNode, lookup, file);
            self.setBufferCallback(inliningTarget, raiseNode, bufferCallback);
            self.initInternals(frame, inliningTarget, lookup);
            return PNone.NONE;
        }
    }

    // functions

    @Builtin(name = J_METHOD_DUMP, minNumOfPositionalArgs = 2, parameterNames = {"$self", "obj"})
    @GenerateNodeFactory
    public abstract static class PicklerDumpNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object dump(VirtualFrame frame, PPickler self, Object obj,
                        @Bind("this") Node inliningTarget,
                        @Cached PPickler.FlushToFileNode flushToFileNode,
                        @Cached PPickler.DumpNode dumpNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (self.getWrite() == null) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.PicklingError, ErrorMessages.INIT_CALLED_WITH, "Pickler", self);
            }
            self.clearBuffer();
            dumpNode.execute(frame, self, obj);
            flushToFileNode.execute(frame, self);
            return PNone.NONE;
        }
    }

    @Builtin(name = J_METHOD_CLEAR_MEMO, minNumOfPositionalArgs = 1, parameterNames = {"$self"})
    @GenerateNodeFactory
    public abstract static class PicklerClearMemoNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object clearMemo(PPickler self) {
            self.clearMemo();
            return PNone.NONE;
        }
    }

    @Builtin(name = J___SIZEOF__, minNumOfPositionalArgs = 1, parameterNames = {"$self"})
    @GenerateNodeFactory
    public abstract static class PicklerSizeofNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object sizeof(@SuppressWarnings("unused") PPickler self) {
            // TODO: implement __sizeof__
            return -1;
        }
    }

    // attributes
    @Builtin(name = J_ATTR_DISPATCH_TABLE, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true, allowsDelete = true)
    @GenerateNodeFactory
    public abstract static class PicklerDispatchTableNode extends PythonBuiltinNode {
        @Specialization(guards = "isNoValue(none)")
        static Object get(PPickler self, @SuppressWarnings("unused") PNone none,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            final Object dispatchTable = self.getDispatchTable();
            if (dispatchTable == null) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.AttributeError, T_ATTR_DISPATCH_TABLE);
            }
            return dispatchTable;
        }

        @Specialization(guards = "isDeleteMarker(marker)")
        static Object delete(PPickler self, @SuppressWarnings("unused") Object marker,
                        @Bind("this") Node inliningTarget,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            final Object dispatchTable = self.getDispatchTable();
            if (dispatchTable == null) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.AttributeError, T_ATTR_DISPATCH_TABLE);
            }
            self.setDispatchTable(null);
            return PNone.NONE;
        }

        @Specialization(guards = {"!isNoValue(value)", "!isDeleteMarker(value)"})
        static Object set(PPickler self, Object value) {
            self.setDispatchTable(value);
            return PNone.NONE;
        }
    }

    @Builtin(name = J_ATTR_BIN, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    public abstract static class PicklerBinNode extends PythonBuiltinNode {
        @Specialization(guards = "isNoValue(none)")
        Object get(PPickler self, @SuppressWarnings("unused") PNone none) {
            return self.getBin();
        }

        @Specialization(guards = {"!isNoValue(value)", "!isDeleteMarker(value)"})
        Object set(VirtualFrame frame, PPickler self, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached PyLongAsLongNode asLongNode) {
            // truncation is fine - we are only interested in the boolean nature of this int (same
            // as cPython)
            self.setBin((int) asLongNode.execute(frame, inliningTarget, value));
            return PNone.NONE;
        }
    }

    @Builtin(name = J_ATTR_FAST, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    public abstract static class PicklerFastNode extends PythonBuiltinNode {
        @Specialization(guards = "isNoValue(none)")
        Object get(PPickler self, @SuppressWarnings("unused") PNone none) {
            return self.getFast();
        }

        @Specialization(guards = {"!isNoValue(value)", "!isDeleteMarker(value)"})
        Object set(VirtualFrame frame, PPickler self, Object value,
                        @Bind("this") Node inliningTarget,
                        @Cached PyLongAsLongNode asLongNode) {
            // truncation is fine - we are only interested in the boolean nature of this int (same
            // as cPython)
            self.setFast((int) asLongNode.execute(frame, inliningTarget, value));
            return PNone.NONE;
        }
    }

    @Builtin(name = J_ATTR_MEMO, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    public abstract static class PicklerMemoNode extends PythonBuiltinNode {
        @Specialization(guards = "isNoValue(none)")
        static Object get(PPickler self, @SuppressWarnings("unused") PNone none,
                        @Cached PythonObjectFactory factory) {
            return factory.createPicklerMemoProxy(self);
        }

        @Specialization(guards = {"!isNoValue(obj)", "!isDeleteMarker(obj)"})
        static Object set(VirtualFrame frame, PPickler self, Object obj,
                        @Bind("this") Node inliningTarget,
                        @Cached HashingCollectionNodes.GetHashingStorageNode getHashingStorageNode,
                        @Cached SequenceNodes.GetSequenceStorageNode getSequenceStorageNode,
                        @Cached SequenceStorageNodes.GetItemNode getItemNode,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached PyObjectSizeNode sizeNode,
                        @Cached HashingStorageGetIterator getIter,
                        @Cached HashingStorageIteratorNext iterNext,
                        @Cached HashingStorageIteratorValue iterValue,
                        @Cached PRaiseNode.Lazy raiseNode) {
            MemoTable newMemo;
            if (obj instanceof PPicklerMemoProxy) {
                final PPickler pickler = ((PPicklerMemoProxy) obj).getPickler();
                newMemo = pickler.getMemo().copy();
            } else if (obj instanceof PDict) {
                newMemo = new MemoTable();
                final HashingStorage dictStorage = getHashingStorageNode.execute(frame, inliningTarget, obj);
                HashingStorageIterator it = getIter.execute(inliningTarget, dictStorage);
                while (iterNext.execute(inliningTarget, dictStorage, it)) {
                    Object value = iterValue.execute(inliningTarget, dictStorage, it);
                    if (!(value instanceof PTuple) || sizeNode.execute(frame, inliningTarget, value) != 2) {
                        throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, ErrorMessages.VALUES_MUST_BE_2TUPLES, "memo");
                    }
                    SequenceStorage tupleStorage = getSequenceStorageNode.execute(inliningTarget, value);
                    int memoId = asSizeNode.executeExact(frame, inliningTarget, getItemNode.execute(frame, tupleStorage, 0));
                    Object memoObj = getItemNode.execute(frame, tupleStorage, 1);
                    newMemo.set(memoObj, memoId);
                }
            } else {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, ErrorMessages.ATTR_MUST_BE_A_OR_B_NOT_C, "memo", "PicklerMemoProxy", "dict", obj);
            }
            self.setMemo(newMemo);
            return PNone.NONE;
        }
    }

    @Builtin(name = J_METHOD_PERSISTENT_ID, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    public abstract static class PicklerPersistentIdNode extends PythonBuiltinNode {
        @Specialization(guards = "isNoValue(none)")
        static Object get(PPickler self, @SuppressWarnings("unused") PNone none,
                        @Bind("this") Node inliningTarget,
                        @Cached PythonObjectFactory factory,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            final Object persFunc = self.getPersFunc();
            if (persFunc == null) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.AttributeError, T_METHOD_PERSISTENT_ID);
            }
            return PickleUtils.reconstructMethod(factory, persFunc, self.getPersFuncSelf());
        }

        @Specialization(guards = {"!isNoValue(obj)", "!isDeleteMarker(obj)"})
        static Object set(PPickler self, Object obj,
                        @Bind("this") Node inliningTarget,
                        @Cached PyCallableCheckNode callableCheck,
                        @Exclusive @Cached PRaiseNode.Lazy raiseNode) {
            if (PGuards.isDeleteMarker(obj)) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, ErrorMessages.ATRIBUTE_DELETION_NOT_SUPPORTED);
            }
            if (!callableCheck.execute(inliningTarget, obj)) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, ErrorMessages.ATTR_MUST_BE_A_CALLABLE, T_METHOD_PERSISTENT_ID, "one argument");
            }
            self.setPersFuncSelf(null);
            self.setPersFunc(obj);
            return PNone.NONE;
        }
    }
}
