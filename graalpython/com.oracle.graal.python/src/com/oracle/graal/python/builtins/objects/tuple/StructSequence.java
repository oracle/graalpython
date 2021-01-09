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
package com.oracle.graal.python.builtins.objects.tuple;

import static com.oracle.graal.python.nodes.SpecialAttributeNames.__DOC__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__NEW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REDUCE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import java.util.Arrays;

import org.graalvm.collections.EconomicMap;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions;
import com.oracle.graal.python.builtins.modules.BuiltinFunctionsFactory;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageLibrary;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GetItemNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.ToArrayNode;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PArguments.ThreadState;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.GetSetDescriptor;
import com.oracle.graal.python.builtins.objects.object.ObjectNodes.GetFullyQualifiedClassNameNode;
import com.oracle.graal.python.builtins.objects.tuple.StructSequenceFactory.NewNodeGen;
import com.oracle.graal.python.builtins.objects.tuple.StructSequenceFactory.ReduceNodeGen;
import com.oracle.graal.python.builtins.objects.tuple.StructSequenceFactory.ReprNodeGen;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.builtins.ListNodes.FastConstructListNode;
import com.oracle.graal.python.nodes.function.BuiltinFunctionRootNode;
import com.oracle.graal.python.nodes.function.BuiltinFunctionRootNode.StandaloneBuiltinFactory;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.ObjectSequenceStorage;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.graal.python.util.Supplier;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;

/**
 * Allows definitions of tuple-like structs with named fields (like structseq.c in CPython).
 *
 * @see com.oracle.graal.python.builtins.modules.PosixModuleBuiltins for an example
 */
public class StructSequence {

    public static final class Descriptor {
        public final PythonBuiltinClassType type;
        public final String docString;
        public final int inSequence;
        public final String[] fieldNames;
        public final String[] fieldDocStrings;

        public Descriptor(PythonBuiltinClassType type, String docString, int inSequence, String[] fieldNames, String[] fieldDocStrings) {
            assert fieldNames.length == fieldDocStrings.length;
            assert type.getBase() == PythonBuiltinClassType.PTuple;
            assert !type.isAcceptableBase();
            this.type = type;
            this.docString = docString;
            this.inSequence = inSequence;
            this.fieldNames = fieldNames;
            this.fieldDocStrings = fieldDocStrings;
        }

        // This shifts the names in a confusing way, but that is what CPython does:
        // >>> s = os.stat('.')
        // >>> s.st_atime
        // 1607033732.3041613
        // >>> s
        // os.stat_result(..., st_atime=1607033732, ...)
        //
        // note that st_atime accessed by name returns float (index 10), but in repr the same label
        // is assigned to the int value at index 7
        String[] getFieldsForRepr() {
            String[] fieldNamesForRepr = new String[inSequence];
            int k = 0;
            for (int idx = 0; idx < fieldNames.length && k < inSequence; ++idx) {
                if (fieldNames[idx] != null) {
                    fieldNamesForRepr[k++] = fieldNames[idx];
                }
            }
            // each field < inSequence must have a name
            assert k == inSequence;
            return fieldNamesForRepr;
        }
    }

    public static void initType(PythonCore core, Descriptor desc) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        PythonBuiltinClass klass = core.lookupType(desc.type);

        // create descriptors for accessing named fields by their names
        int unnamedFields = 0;
        for (int idx = 0; idx < desc.fieldNames.length; ++idx) {
            if (desc.fieldNames[idx] != null) {
                createMember(core, klass, desc.fieldNames[idx], desc.fieldDocStrings[idx], idx);
            } else {
                unnamedFields++;
            }
        }

        createMethod(core, klass, ReprNode.class, () -> ReprNodeGen.create(desc), false);
        createMethod(core, klass, ReduceNode.class, () -> ReduceNodeGen.create(desc), false);

        if (klass.getAttribute(__NEW__) == PNone.NO_VALUE) {
            createMethod(core, klass, NewNode.class, () -> NewNodeGen.create(desc), true);
        }

        klass.setAttribute(__DOC__, desc.docString);
        klass.setAttribute("n_sequence_fields", desc.inSequence);
        klass.setAttribute("n_fields", desc.fieldNames.length);
        klass.setAttribute("n_unnamed_fields", unnamedFields);
    }

    private static void createMember(PythonCore core, PythonBuiltinClass klass, String name, String doc, int idx) {
        PythonLanguage language = core.getLanguage();
        RootCallTarget callTarget = language.getOrComputeBuiltinCallTarget(GetStructMemberNode.class.getName() + idx, () -> new GetStructMemberNode(language, idx));
        PBuiltinFunction getter = core.factory().createBuiltinFunction(name, klass, 0, callTarget);
        GetSetDescriptor callable = core.factory().createGetSetDescriptor(getter, null, name, klass, false);
        callable.setAttribute(__DOC__, doc);
        klass.setAttribute(name, callable);
    }

    private static void createMethod(PythonCore core, PythonBuiltinClass klass, Class<?> nodeClass, Supplier<PythonBuiltinBaseNode> nodeSupplier, boolean constructor) {
        Builtin builtin = nodeClass.getAnnotation(Builtin.class);
        RootCallTarget callTarget = core.getLanguage().getOrComputeBuiltinCallTarget(nodeClass.getName() + klass.getType().getPrintName(), () -> {
            PythonBuiltinClassType constructsClass = constructor ? klass.getType() : PythonBuiltinClassType.nil;
            NodeFactory<PythonBuiltinBaseNode> nodeFactory = new StandaloneBuiltinFactory<>(nodeSupplier.get());
            return new BuiltinFunctionRootNode(core.getLanguage(), builtin, nodeFactory, true, constructsClass);
        });
        PBuiltinFunction function = core.factory().createBuiltinFunction(builtin.name(), klass, constructor ? 1 : 0, callTarget);
        klass.setAttribute(builtin.name(), function);
    }

    @Builtin(name = __NEW__, minNumOfPositionalArgs = 2, parameterNames = {"$cls", "sequence", "dict"})
    public abstract static class NewNode extends PythonTernaryBuiltinNode {

        @CompilationFinal(dimensions = 1) private final String[] fieldNames;
        private final int inSequence;
        private final PythonBuiltinClassType type;

        NewNode(Descriptor desc) {
            this.fieldNames = desc.fieldNames;
            this.inSequence = desc.inSequence;
            this.type = desc.type;
        }

        public static NewNode create(Descriptor desc) {
            return NewNodeGen.create(desc);
        }

        @Specialization(guards = "isNoValue(dict)")
        public PTuple withoutDict(Object cls, Object sequence, @SuppressWarnings("unused") PNone dict,
                        @Cached FastConstructListNode fastConstructListNode,
                        @Cached ToArrayNode toArrayNode,
                        @Cached IsBuiltinClassProfile notASequenceProfile,
                        @Cached BranchProfile wrongLenProfile,
                        @Cached BranchProfile needsReallocProfile) {
            Object[] src = sequenceToArray(sequence, fastConstructListNode, toArrayNode, notASequenceProfile);
            Object[] dst = processSequence(src, wrongLenProfile, needsReallocProfile);
            for (int i = src.length; i < dst.length; ++i) {
                dst[i] = PNone.NONE;
            }
            return factory().createTuple(cls, new ObjectSequenceStorage(dst, inSequence));
        }

        @Specialization
        public PTuple withDict(VirtualFrame frame, Object cls, Object sequence, PDict dict,
                        @Cached FastConstructListNode fastConstructListNode,
                        @Cached ToArrayNode toArrayNode,
                        @Cached IsBuiltinClassProfile notASequenceProfile,
                        @Cached BranchProfile wrongLenProfile,
                        @Cached BranchProfile needsReallocProfile,
                        @CachedLibrary(limit = "1") HashingStorageLibrary dictLib) {
            Object[] src = sequenceToArray(sequence, fastConstructListNode, toArrayNode, notASequenceProfile);
            Object[] dst = processSequence(src, wrongLenProfile, needsReallocProfile);
            HashingStorage hs = dict.getDictStorage();
            ThreadState threadState = PArguments.getThreadState(frame);
            for (int i = src.length; i < dst.length; ++i) {
                Object o = dictLib.getItemWithState(hs, fieldNames[i], threadState);
                dst[i] = o == null ? PNone.NONE : o;
            }
            return factory().createTuple(cls, new ObjectSequenceStorage(dst, inSequence));
        }

        @Specialization(guards = {"!isNoValue(dict)", "!isDict(dict)"})
        @SuppressWarnings("unused")
        public PTuple doDictError(VirtualFrame frame, Object cls, Object sequence, Object dict) {
            throw raise(TypeError, ErrorMessages.TAKES_A_DICT_AS_SECOND_ARG_IF_ANY, type.getPrintName());
        }

        private Object[] sequenceToArray(Object sequence, FastConstructListNode fastConstructListNode, ToArrayNode toArrayNode, IsBuiltinClassProfile notASequenceProfile) {
            PSequence seq;
            try {
                seq = fastConstructListNode.execute(sequence);
            } catch (PException e) {
                e.expect(TypeError, notASequenceProfile);
                throw raise(TypeError, ErrorMessages.CONSTRUCTOR_REQUIRES_A_SEQUENCE);
            }
            return toArrayNode.execute(seq.getSequenceStorage());
        }

        private Object[] processSequence(Object[] src, BranchProfile wrongLenProfile, BranchProfile needsReallocProfile) {
            int len = src.length;
            int minLen = inSequence;
            int maxLen = fieldNames.length;

            if (len < minLen || len > maxLen) {
                wrongLenProfile.enter();
                if (minLen == maxLen) {
                    throw raise(TypeError, ErrorMessages.TAKES_A_D_SEQUENCE, type.getPrintName(), minLen, len);
                }
                if (len < minLen) {
                    throw raise(TypeError, ErrorMessages.TAKES_AN_AT_LEAST_D_SEQUENCE, type.getPrintName(), minLen, len);
                } else {    // len > maxLen
                    throw raise(TypeError, ErrorMessages.TAKES_AN_AT_MOST_D_SEQUENCE, type.getPrintName(), maxLen, len);
                }
            }

            if (len != maxLen) {
                needsReallocProfile.enter();
                Object[] dst = new Object[maxLen];
                PythonUtils.arraycopy(src, 0, dst, 0, len);
                return dst;
            }
            return src;
        }
    }

    @Builtin(name = __REDUCE__, minNumOfPositionalArgs = 1)
    abstract static class ReduceNode extends PythonUnaryBuiltinNode {

        @CompilationFinal(dimensions = 1) private final String[] fieldNames;
        private final int inSequence;

        ReduceNode(Descriptor desc) {
            this.fieldNames = desc.fieldNames;
            this.inSequence = desc.inSequence;
        }

        @Specialization
        public PTuple reduce(PTuple self,
                        @Cached GetClassNode getClass) {
            assert self.getSequenceStorage() instanceof ObjectSequenceStorage;
            Object[] data = CompilerDirectives.castExact(self.getSequenceStorage(), ObjectSequenceStorage.class).getInternalArray();
            assert data.length == fieldNames.length;
            PTuple seq;
            PDict dict;
            if (fieldNames.length == inSequence) {
                seq = factory().createTuple(data);
                dict = factory().createDict();
            } else {
                EconomicMap<String, Object> map = EconomicMap.create(fieldNames.length - inSequence);
                for (int i = inSequence; i < fieldNames.length; ++i) {
                    putToMap(map, fieldNames[i], data[i]);
                }
                seq = factory().createTuple(Arrays.copyOf(data, inSequence));
                dict = factory().createDict(map);
            }
            PTuple seqDictPair = factory().createTuple(new Object[]{seq, dict});
            return factory().createTuple(new Object[]{getClass.execute(self), seqDictPair});
        }

        @TruffleBoundary
        private static void putToMap(EconomicMap<String, Object> map, String key, Object value) {
            map.put(key, value);
        }
    }

    @Builtin(name = __REPR__, minNumOfPositionalArgs = 1)
    abstract static class ReprNode extends PythonUnaryBuiltinNode {

        @CompilationFinal(dimensions = 1) private final String[] fieldNames;

        ReprNode(Descriptor desc) {
            this.fieldNames = desc.getFieldsForRepr();
        }

        @Specialization
        public String repr(VirtualFrame frame, PTuple self,
                        @Cached GetFullyQualifiedClassNameNode getFullyQualifiedClassNameNode,
                        @Cached("createNotNormalized()") GetItemNode getItemNode,
                        @Cached BuiltinFunctions.ReprNode reprNode,
                        @Cached CastToJavaStringNode castToStringNode) {
            StringBuilder buf = PythonUtils.newStringBuilder();
            PythonUtils.append(buf, getFullyQualifiedClassNameNode.execute(frame, self));
            PythonUtils.append(buf, '(');
            SequenceStorage tupleStore = self.getSequenceStorage();
            if (fieldNames.length > 0) {
                PythonUtils.append(buf, fieldNames[0]);
                PythonUtils.append(buf, '=');
                PythonUtils.append(buf, castToStringNode.execute(reprNode.call(frame, getItemNode.execute(frame, tupleStore, 0))));
                for (int i = 1; i < fieldNames.length; i++) {
                    PythonUtils.append(buf, ", ");
                    PythonUtils.append(buf, fieldNames[i]);
                    PythonUtils.append(buf, '=');
                    PythonUtils.append(buf, castToStringNode.execute(reprNode.call(frame, getItemNode.execute(frame, tupleStore, i))));
                }
            }
            PythonUtils.append(buf, ')');
            return PythonUtils.sbToString(buf);
        }

        protected static BuiltinFunctions.ReprNode createRepr() {
            return BuiltinFunctionsFactory.ReprNodeFactory.create();
        }
    }

    private static class GetStructMemberNode extends PRootNode {
        private final int fieldIdx;

        GetStructMemberNode(PythonLanguage language, int fieldIdx) {
            super(language);
            this.fieldIdx = fieldIdx;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            PTuple self = (PTuple) PArguments.getArgument(frame, 0);
            return self.getSequenceStorage().getItemNormalized(fieldIdx);
        }

        @Override
        public Signature getSignature() {
            return new Signature(-1, false, -1, false, new String[]{"$self"}, PythonUtils.EMPTY_STRING_ARRAY);
        }

        @Override
        public boolean isPythonInternal() {
            return true;
        }
    }
}
