/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.object;

import static com.oracle.graal.python.nodes.BuiltinNames.T_POLYGLOT;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___MODULE__;

import java.util.ArrayList;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeFlags;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.strings.TruffleString;

// there is no value to (DSL & host)-inline this node, it can only make the caller node bigger
@GenerateCached
@GenerateUncached
@GenerateInline(false)
public abstract class GetForeignObjectClassNode extends PNodeWithContext {

    public static GetForeignObjectClassNode getUncached() {
        return GetForeignObjectClassNodeGen.getUncached();
    }

    /** Based on the types and traits of {@link InteropLibrary} */
    public enum Trait {
        /*
         * We need more specific traits before container traits, so that for example in R::c(1) +
         * R::c(2), the + operator with two foreign list+number uses ForeignNumber.__add__ + and not
         * list.__add__.
         */

        // The type field is only set for cases which are already implemented.

        // First in MRO
        // Interop types first as they are the most concrete/specific types
        NULL("None", PythonBuiltinClassType.PNone),
        BOOLEAN("Boolean", PythonBuiltinClassType.ForeignBoolean),
        NUMBER("Number", PythonBuiltinClassType.ForeignNumber), // int, float, complex
        STRING("String", PythonBuiltinClassType.PString),
        EXCEPTION("Exception", PythonBuiltinClassType.PBaseException),
        META_OBJECT("AbstractClass", PythonBuiltinClassType.ForeignAbstractClass),

        // Interop traits
        EXECUTABLE("Executable", PythonBuiltinClassType.ForeignExecutable),
        INSTANTIABLE("Instantiable", PythonBuiltinClassType.ForeignInstantiable),

        // Container traits/types must be last, see comment above
        // Hash before Array so that foreign dict+list prefers dict.[]
        HASH("Dict", PythonBuiltinClassType.PDict),
        // Array before Iterable so that foreign list+iterable prefers list.__iter__
        ARRAY("List", PythonBuiltinClassType.PList),
        // Iterator before Iterable so that foreign iterator+iterable prefers iterator.__iter__
        ITERATOR("Iterator", PythonBuiltinClassType.PIterator),
        ITERABLE("Iterable", PythonBuiltinClassType.ForeignIterable);
        // Last in MRO

        public static final Trait[] VALUES = Trait.values();
        public static final int COMBINATIONS = 1 << VALUES.length;

        final String name;
        final int bit;
        final PythonBuiltinClassType type;

        Trait(String name, PythonBuiltinClassType type) {
            this.name = name;
            this.bit = 1 << ordinal();
            this.type = type;
        }

        boolean isSet(int traits) {
            return (traits & bit) != 0;
        }
    }

    public abstract PythonManagedClass execute(Object object);

    // isSingleContext() because making cachedTraits PE constant has no value in multi-context
    @Specialization(guards = {"isSingleContext()", "traits == cachedTraits"}, limit = "getCallSiteInlineCacheMaxDepth()")
    PythonManagedClass cached(Object object,
                    @CachedLibrary("object") InteropLibrary interop,
                    @Bind("getTraits(object, interop)") int traits,
                    @Bind PythonContext context,
                    @Cached("traits") int cachedTraits) {
        assert IsForeignObjectNode.executeUncached(object);
        return classForTraits(context, cachedTraits);
    }

    @Specialization(replaces = "cached", limit = "getCallSiteInlineCacheMaxDepth()")
    PythonManagedClass uncached(Object object,
                    @Bind PythonContext context,
                    @CachedLibrary("object") InteropLibrary interop) {
        assert IsForeignObjectNode.executeUncached(object);
        return classForTraits(context, getTraits(object, interop));
    }

    protected static int getTraits(Object object, InteropLibrary interop) {
        // Alphabetic order here as it does not matter
        return (interop.hasArrayElements(object) ? Trait.ARRAY.bit : 0) +
                        (interop.isBoolean(object) ? Trait.BOOLEAN.bit : 0) +
                        (interop.isException(object) ? Trait.EXCEPTION.bit : 0) +
                        (interop.isExecutable(object) ? Trait.EXECUTABLE.bit : 0) +
                        (interop.hasHashEntries(object) ? Trait.HASH.bit : 0) +
                        (interop.isInstantiable(object) ? Trait.INSTANTIABLE.bit : 0) +
                        (interop.hasIterator(object) ? Trait.ITERABLE.bit : 0) +
                        (interop.isIterator(object) ? Trait.ITERATOR.bit : 0) +
                        (interop.isMetaObject(object) ? Trait.META_OBJECT.bit : 0) +
                        (interop.isNull(object) ? Trait.NULL.bit : 0) +
                        (interop.isNumber(object) ? Trait.NUMBER.bit : 0) +
                        (interop.isString(object) ? Trait.STRING.bit : 0);
    }

    private PythonManagedClass classForTraits(PythonContext context, int traits) {
        PythonManagedClass pythonClass = context.getPolyglotForeignClasses(traits);
        if (pythonClass == null) {
            if (isSingleContext(this)) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
            }
            pythonClass = resolvePolyglotForeignClassAndSetInCache(context, traits);
        }
        return pythonClass;
    }

    @TruffleBoundary
    private PythonManagedClass resolvePolyglotForeignClassAndSetInCache(PythonContext context, int traits) {
        PythonManagedClass pythonClass = resolvePolyglotForeignClass(context, traits);
        context.setPolyglotForeignClasses(traits, pythonClass);
        return pythonClass;
    }

    // Special naming rules:
    // Foreign...Instantiable...AbstractClass -> Foreign......Class
    // Foreign...List...Iterable -> Foreign...List... (since all Python lists are iterable)
    // Foreign...Dict...Iterable -> Foreign...Dict... (since all Python dicts are iterable)
    @TruffleBoundary
    private PythonManagedClass resolvePolyglotForeignClass(PythonContext context, int traits) {
        PythonBuiltinClass base = context.lookupType(PythonBuiltinClassType.ForeignObject);
        if (traits == 0) {
            return base;
        }

        /*
         * For foreign array/hash+iterable, ignore the iterable trait completely, foreign
         * arrays/hashes inherit from the Python list/dict class and all lists/dicts are iterables
         * (they have __iter__)
         */
        if ((Trait.ARRAY.isSet(traits) || Trait.HASH.isSet(traits)) && Trait.ITERABLE.isSet(traits)) {
            return classForTraits(context, traits - Trait.ITERABLE.bit);
        }

        // If there is a single trait we build a new class using the trait.type
        boolean singleTrait = Integer.bitCount(traits) == 1;

        var traitsList = new ArrayList<PythonAbstractClass>();

        var nameBuilder = new StringBuilder("Foreign");
        for (Trait trait : Trait.VALUES) {
            if (trait.isSet(traits)) {
                assert !(trait == Trait.ITERABLE && Trait.ARRAY.isSet(traits));

                if (singleTrait) {
                    if (trait.type != null) {
                        traitsList.add(context.lookupType(trait.type));
                    }
                } else {
                    traitsList.add(classForTraits(context, trait.bit));
                }

                if (trait == Trait.META_OBJECT) {
                    if (Trait.INSTANTIABLE.isSet(traits)) {
                        nameBuilder.append("Class");
                    } else {
                        nameBuilder.append("AbstractClass");
                    }
                } else if (trait == Trait.INSTANTIABLE && Trait.META_OBJECT.isSet(traits)) {
                    // Dealt with above
                } else {
                    nameBuilder.append(trait.name);
                }
            }
        }

        TruffleString name = PythonUtils.toTruffleStringUncached(nameBuilder.toString());

        // If the single-trait class already inherits from ForeignObject, return it as-is, the name
        // would clash and no need to create a composed class
        if (singleTrait && traitsList.size() == 1 && traitsList.get(0) instanceof PythonBuiltinClass pbc && pbc.getType().getName().equals(name)) {
            return pbc;
        }

        traitsList.add(base);

        PythonAbstractClass[] bases = traitsList.toArray(PythonAbstractClass.EMPTY_ARRAY);

        PythonModule polyglotModule = context.lookupBuiltinModule(T_POLYGLOT);

        PythonClass pythonClass = PFactory.createPythonClassAndFixupSlots(null, context.getLanguage(), name, base, bases);
        pythonClass.setAttribute(T___MODULE__, T_POLYGLOT);
        if (!Trait.INSTANTIABLE.isSet(traits)) {
            pythonClass.setTpSlots(pythonClass.getTpSlots().copy().set(TpSlots.TpSlotMeta.TP_NEW, null).build());
            TypeNodes.SetTypeFlagsNode.executeUncached(pythonClass, TypeNodes.GetTypeFlagsNode.executeUncached(pythonClass) | TypeFlags.DISALLOW_INSTANTIATION);
        }

        assert polyglotModule.getAttribute(name) == PNone.NO_VALUE : name;
        polyglotModule.setAttribute(name, pythonClass);

        return pythonClass;
    }

    public void defineSingleTraitClasses(PythonContext context) {
        PythonModule polyglotModule = context.lookupBuiltinModule(T_POLYGLOT);
        for (Trait trait : Trait.VALUES) {
            PythonManagedClass traitClass = classForTraits(context, trait.bit);
            assert polyglotModule.getAttribute(traitClass.getName()) == traitClass : traitClass.getName();
        }
    }

}
