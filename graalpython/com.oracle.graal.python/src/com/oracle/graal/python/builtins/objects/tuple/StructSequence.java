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
package com.oracle.graal.python.builtins.objects.tuple;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.NotImplementedError;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DOC__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___NEW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___REDUCE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___REPR__;
import static com.oracle.graal.python.util.PythonUtils.EMPTY_TRUFFLESTRING_ARRAY;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringArrayUncached;
import static com.oracle.graal.python.util.PythonUtils.tsArray;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.cext.PythonAbstractNativeObject;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.builtins.objects.getsetdescriptor.GetSetDescriptor;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.builtins.objects.type.TypeFlags;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot.TpSlotBuiltin;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.HiddenAttr;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.PRootNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.runtime.sequence.storage.NativeSequenceStorage;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Allows definitions of tuple-like structs with named fields (like structseq.c in CPython).
 *
 * @see com.oracle.graal.python.builtins.modules.PosixModuleBuiltins for an example
 */
public class StructSequence {

    /** The <it>visible</it> length (excludes unnamed fields) of the structseq type. */
    public static final TruffleString T_N_SEQUENCE_FIELDS = tsLiteral("n_sequence_fields");

    /** The <it>real</it> length (includes unnamed fields) of the structseq type. */
    public static final TruffleString T_N_FIELDS = tsLiteral("n_fields");

    /** The number of unnamed fields. */
    public static final TruffleString T_N_UNNAMED_FIELDS = tsLiteral("n_unnamed_fields");

    /**
     * The equivalent of {@code PyStructSequence_Desc} except of the {@code name}. We don't need the
     * type's name in the descriptor and this will improve code sharing.
     */
    public static sealed class Descriptor permits BuiltinTypeDescriptor {
        public final int inSequence;
        public final TruffleString[] fieldNames;
        public final TruffleString[] fieldDocStrings;

        public Descriptor(int inSequence, TruffleString[] fieldNames, TruffleString[] fieldDocStrings) {
            assert fieldDocStrings == null || fieldNames.length == fieldDocStrings.length;
            this.inSequence = inSequence;
            this.fieldNames = fieldNames;
            this.fieldDocStrings = fieldDocStrings;
        }
    }

    /**
     * Very similar to {@code PyStructSequence_Desc} but already specific to a built-in type. Used
     * for built-in structseq objects. All BuiltinTypeDescriptor instances should be kept in
     * {@code static final} fields to ensure there is a finite number of them.
     */
    public static final class BuiltinTypeDescriptor extends Descriptor {
        public final PythonBuiltinClassType type;

        public BuiltinTypeDescriptor(PythonBuiltinClassType type, int inSequence, String[] fieldNames, String[] fieldDocStrings) {
            super(inSequence, toTruffleStringArrayUncached(fieldNames), toTruffleStringArrayUncached(fieldDocStrings));
            assert type.getBase() == PythonBuiltinClassType.PTuple;
            assert !type.isAcceptableBase();
            this.type = type;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof BuiltinTypeDescriptor that)) {
                return false;
            }
            if (!super.equals(o)) {
                return false;
            }
            return type == that.type;
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), type);
        }
    }

    @TruffleBoundary
    public static void initType(Python3Core core, BuiltinTypeDescriptor desc) {
        initType(core.getContext(), core.lookupType(desc.type), desc);
    }

    @TruffleBoundary
    public static void initType(PythonContext context, PythonAbstractClass klass, Descriptor desc) {
        assert IsSubtypeNode.getUncached().execute(klass, PythonBuiltinClassType.PTuple);
        PythonLanguage language = context.getLanguage();

        long flags = TypeNodes.GetTypeFlagsNode.executeUncached(klass);
        if ((flags & TypeFlags.IMMUTABLETYPE) != 0) {
            // Temporarily open the type for mutation
            TypeNodes.SetTypeFlagsNode.executeUncached(klass, flags & ~TypeFlags.IMMUTABLETYPE);
        }

        // create descriptors for accessing named fields by their names
        int unnamedFields = 0;
        List<TruffleString> namedFields = new ArrayList<>(desc.fieldNames.length);
        for (int idx = 0; idx < desc.fieldNames.length; ++idx) {
            TruffleString fieldName = desc.fieldNames[idx];
            if (fieldName != null) {
                TruffleString doc = desc.fieldDocStrings == null ? null : desc.fieldDocStrings[idx];
                createMember(language, klass, fieldName, doc, idx);
                namedFields.add(fieldName);
            } else {
                unnamedFields++;
            }
        }
        WriteAttributeToObjectNode writeAttrNode = WriteAttributeToObjectNode.getUncached(true);
        if (klass instanceof PythonManagedClass managedClass) {
            /*
             * The methods and slots are already set for each PBCT, but we need to store the field
             * names.
             */
            HiddenAttr.WriteNode.executeUncached(managedClass, HiddenAttr.STRUCTSEQ_FIELD_NAMES, namedFields.toArray(new TruffleString[0]));
        } else if (klass instanceof PythonAbstractNativeObject) {
            /*
             * We need to add the methods. Note that PyType_Ready already ran, so we just write the
             * method wrappers. We take them from any builtin that doesn't override them and rebind
             * them to the type. Field names are already populated in tp_members on the native side.
             */
            PythonBuiltinClass template = context.lookupType(PythonBuiltinClassType.PFloatInfo);
            copyMethod(language, klass, T___NEW__, template);
            copyMethod(language, klass, T___REDUCE__, template);

            // Wrappers of "new" slots perform self type validation according to CPython semantics,
            // so we cannot reuse them, but we create and cache one call-target shared among all
            // native structseq subclasses
            PBuiltinFunction reprWrapperDescr = TpSlotBuiltin.createNativeReprWrapperDescriptor(context, StructSequenceBuiltins.SLOTS, klass);
            WriteAttributeToObjectNode.getUncached(true).execute(klass, T___REPR__, reprWrapperDescr);
        }
        writeAttrNode.execute(klass, T_N_SEQUENCE_FIELDS, desc.inSequence);
        writeAttrNode.execute(klass, T_N_FIELDS, desc.fieldNames.length);
        writeAttrNode.execute(klass, T_N_UNNAMED_FIELDS, unnamedFields);

        TypeNodes.SetTypeFlagsNode.executeUncached(klass, flags);
    }

    private static void copyMethod(PythonLanguage language, PythonAbstractClass klass, TruffleString name, PythonBuiltinClass template) {
        PBuiltinFunction templateMethod = (PBuiltinFunction) template.getAttribute(name);
        PBuiltinFunction method = PFactory.createBuiltinFunction(language, templateMethod, klass);
        WriteAttributeToObjectNode.getUncached(true).execute(klass, name, method);
    }

    private static void createMember(PythonLanguage language, Object klass, TruffleString name, TruffleString doc, int idx) {
        RootCallTarget callTarget = language.createStructSeqIndexedMemberAccessCachedCallTarget((l) -> new GetStructMemberNode(l, idx), idx);
        PBuiltinFunction getter = PFactory.createBuiltinFunction(language, name, klass, 0, 0, callTarget);
        GetSetDescriptor callable = PFactory.createGetSetDescriptor(language, getter, null, name, klass, false);
        if (doc != null) {
            callable.setAttribute(T___DOC__, doc);
        }
        WriteAttributeToObjectNode.getUncached(true).execute(klass, name, callable);
    }

    private static class GetStructMemberNode extends PRootNode {
        public static final Signature SIGNATURE = new Signature(-1, false, -1, tsArray("$self"), EMPTY_TRUFFLESTRING_ARRAY);
        private final int fieldIdx;

        GetStructMemberNode(PythonLanguage language, int fieldIdx) {
            super(language);
            this.fieldIdx = fieldIdx;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            PTuple self = (PTuple) PArguments.getArgument(frame, 0);
            if (self.getSequenceStorage() instanceof NativeSequenceStorage && fieldIdx >= self.getSequenceStorage().length()) {
                throw PRaiseNode.raiseStatic(this, NotImplementedError, ErrorMessages.UNSUPPORTED_ACCESS_OF_STRUCT_SEQUENCE_NATIVE_STORAGE);
            }
            return SequenceStorageNodes.GetItemScalarNode.executeUncached(self.getSequenceStorage(), fieldIdx);
        }

        @Override
        public Signature getSignature() {
            return SIGNATURE;
        }

        @Override
        public boolean isInternal() {
            return true;
        }

        @Override
        public boolean isPythonInternal() {
            return true;
        }
    }

    static TruffleString getTpName(Object cls) {
        if (cls instanceof PythonBuiltinClassType) {
            return ((PythonBuiltinClassType) cls).getPrintName();
        } else if (cls instanceof PythonBuiltinClass) {
            return ((PythonBuiltinClass) cls).getType().getPrintName();
        }
        return GetNameNode.executeUncached(cls);
    }
}
