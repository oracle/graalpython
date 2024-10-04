/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.strings.TruffleString;

import java.util.ArrayList;

import static com.oracle.graal.python.nodes.BuiltinNames.T_POLYGLOT;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___MODULE__;

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
        // TODO dir(foreign) should list both foreign object members and attributes from class
        // First in MRO
        // The type field is only set for cases which are already implemented.
        HASH("Dict", PythonBuiltinClassType.PDict), // must be before Array
        ARRAY("List", PythonBuiltinClassType.PList), // must be before Iterable
        EXCEPTION("Exception", PythonBuiltinClassType.PBaseException),
        EXECUTABLE("Executable"),
        INSTANTIABLE("Instantiable"),
        ITERATOR("Iterator", PythonBuiltinClassType.PIterator), // must be before Iterable
        ITERABLE("Iterable"),
        META_OBJECT("Type"), // PythonBuiltinClassType.PythonClass ?
        NULL("None", PythonBuiltinClassType.PNone),
        NUMBER("Number"), // int, float, complex
        POINTER("Pointer"),
        STRING("String"); // PythonBuiltinClassType.PString
        // Last in MRO

        public static final Trait[] VALUES = Trait.values();
        public static final int COMBINATIONS = 1 << VALUES.length;

        final String name;
        final int bit;
        final PythonBuiltinClassType type;

        Trait(String name) {
            this(name, null);
        }

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
    @Specialization(guards = {"isSingleContext()", "getTraits(object, interop) == cachedTraits"}, limit = "getCallSiteInlineCacheMaxDepth()")
    PythonManagedClass cached(Object object,
                    @CachedLibrary("object") InteropLibrary interop,
                    @Cached("getTraits(object, interop)") int cachedTraits) {
        assert IsForeignObjectNode.executeUncached(object);
        return classForTraits(cachedTraits);
    }

    @Specialization(replaces = "cached", limit = "getCallSiteInlineCacheMaxDepth()")
    PythonManagedClass uncached(Object object,
                    @CachedLibrary("object") InteropLibrary interop) {
        assert IsForeignObjectNode.executeUncached(object);
        return classForTraits(getTraits(object, interop));
    }

    protected static int getTraits(Object object, InteropLibrary interop) {
        return (interop.hasHashEntries(object) ? Trait.HASH.bit : 0) +
                        (interop.hasArrayElements(object) ? Trait.ARRAY.bit : 0) +
                        (interop.isException(object) ? Trait.EXCEPTION.bit : 0) +
                        (interop.isExecutable(object) ? Trait.EXECUTABLE.bit : 0) +
                        (interop.isInstantiable(object) ? Trait.INSTANTIABLE.bit : 0) +
                        (interop.isIterator(object) ? Trait.ITERATOR.bit : 0) +
                        (interop.hasIterator(object) ? Trait.ITERABLE.bit : 0) +
                        (interop.isMetaObject(object) ? Trait.META_OBJECT.bit : 0) +
                        (interop.isNull(object) ? Trait.NULL.bit : 0) +
                        (interop.isNumber(object) ? Trait.NUMBER.bit : 0) +
                        (interop.isPointer(object) ? Trait.POINTER.bit : 0) +
                        (interop.isString(object) ? Trait.STRING.bit : 0);
    }

    private PythonManagedClass classForTraits(int traits) {
        PythonManagedClass pythonClass = getContext().polyglotForeignClasses[traits];
        if (pythonClass == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            pythonClass = resolvePolyglotForeignClass(traits);
            getContext().polyglotForeignClasses[traits] = pythonClass;
        }
        return pythonClass;
    }

    private PythonManagedClass resolvePolyglotForeignClass(int traits) {
        PythonBuiltinClass base = getContext().lookupType(PythonBuiltinClassType.ForeignObject);
        if (traits == 0) {
            return base;
        }

        var traitsList = new ArrayList<PythonAbstractClass>();
        traitsList.add(base);

        var nameBuilder = new StringBuilder("Foreign");
        for (Trait trait : Trait.VALUES) {
            if (trait.isSet(traits)) {
                if (trait.type != null) {
                    traitsList.add(getContext().lookupType(trait.type));
                }

                if (trait == Trait.ITERABLE && Trait.ARRAY.isSet(traits)) {
                    // foreign Array are Iterable by default, so it seems redundant in the name
                    if (Trait.ITERABLE.isSet(traits)) {
                        // Iterable already implied by List in the name, skip it
                    } else {
                        nameBuilder.append("NotIterable");
                    }
                } else {
                    nameBuilder.append(trait.name);
                }
            }
        }
        // reversed() to have the right MRO, and have e.g. list before Iterable before ForeignObject
        PythonAbstractClass[] bases = traitsList.reversed().toArray(PythonAbstractClass.EMPTY_ARRAY);

        TruffleString name = PythonUtils.toTruffleStringUncached(nameBuilder.toString());

        PythonModule polyglotModule = getContext().lookupBuiltinModule(T_POLYGLOT);

        PythonClass pythonClass = getContext().factory().createPythonClassAndFixupSlots(getLanguage(), PythonBuiltinClassType.PythonClass, name, base, bases);
        pythonClass.setAttribute(T___MODULE__, T_POLYGLOT);

        polyglotModule.setAttribute(name, pythonClass);

        return pythonClass;
    }

}
