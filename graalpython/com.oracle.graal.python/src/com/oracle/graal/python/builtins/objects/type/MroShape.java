/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.type;

import static com.oracle.graal.python.nodes.SpecialAttributeNames.__BASICSIZE__;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromDynamicObjectNode;
import com.oracle.graal.python.runtime.sequence.storage.MroSequenceStorage;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.Shape;

/**
 * Mirrors MRO sequence, but contains only {@link Shape}s of the {@link PythonManagedClass}es in the
 * corresponding MRO sequences. This object is context independent and is intended to be cached in
 * the AST.
 *
 * {@link MroShape} cannot always be constructed. Most notably if the MRO contains native classes,
 * but also in other cases.
 *
 * Instances of {@link MroShape} form a tree that allows to intern them and then rely on object
 * identity when comparing two {@link MroShape}s.
 */
public final class MroShape {
    private final Shape shape;
    private final MroShape parent;
    private final int size;
    private final ConcurrentHashMap<Shape, MroShape> children = new ConcurrentHashMap<>();

    public MroShape(Shape shape, MroShape parent, int size) {
        this.shape = shape;
        this.parent = parent;
        this.size = size;
    }

    public static MroShape createRoot() {
        CompilerAsserts.neverPartOfCompilation();
        return new MroShape(null, null, 0);
    }

    public static MroShape create(MroSequenceStorage mro, PythonLanguage lang) {
        CompilerAsserts.neverPartOfCompilation();
        MroShape mroShape = lang.getMroShapeRoot();
        for (int i = mro.length() - 1; i >= 0; i--) {
            PythonAbstractClass element = mro.getItemNormalized(i);
            if (PythonManagedClass.isInstance(element)) {
                PythonManagedClass managedClass = PythonManagedClass.cast(element);
                if (managedClass.hasDict(DynamicObjectLibrary.getUncached())) {
                    // On top of not having a shape, the dictionary may also contain items with side
                    // effecting __eq__ and/or __hash__
                    return null;
                }
                mroShape = mroShape.add(managedClass.getShape());
            } else {
                return null;
            }
        }
        return mroShape;
    }

    public MroShapeLookupResult lookup(String attrName) {
        CompilerAsserts.neverPartOfCompilation();
        int index = 0;
        MroShape curr = this;
        while (curr.parent != null) {
            if (curr.shape.getProperty(attrName) != null) {
                return new MroShapeLookupResult(index);
            }
            index++;
            curr = curr.parent;
        }
        return MroShapeLookupResult.createNotFound();
    }

    private MroShape add(Shape newShape) {
        return children.computeIfAbsent(newShape, s -> new MroShape(s, this, size + 1));
    }

    public static final class MroShapeLookupResult extends Node {
        private static final int NOT_FOUND_INDEX = -1;
        private final int mroIndex;
        @Child ReadAttributeFromDynamicObjectNode readNode;

        public MroShapeLookupResult(int mroIndex) {
            this.mroIndex = mroIndex;
            if (mroIndex != NOT_FOUND_INDEX) {
                readNode = ReadAttributeFromDynamicObjectNode.create();
            }
        }

        static MroShapeLookupResult createNotFound() {
            return new MroShapeLookupResult(NOT_FOUND_INDEX);
        }

        public Object getFromMro(MroSequenceStorage mro, Object key) {
            if (mroIndex == NOT_FOUND_INDEX) {
                return PNone.NO_VALUE;
            } else {
                return readNode.execute(PythonManagedClass.cast(mro.getItemNormalized(mroIndex)), key);
            }
        }
    }

    /**
     * Utility method intended only for assertions and debugging, use in {@code assert}. Sprinkle
     * over attribute lookup nodes and other relevant places.
     */
    @SuppressWarnings("unused")
    public static boolean validate(Object obj) {
        if (!(obj instanceof PythonClass)) {
            return true;
        }
        PythonClass klass = (PythonClass) obj;
        MroSequenceStorage mro = klass.getMethodResolutionOrder();
        if (mro == null) {
            return true;
        }
        MroShape mroShape = klass.getMroShape();
        for (int i = 0; i < mro.length(); i++) {
            if (!(mro.getItemNormalized(i) instanceof PythonManagedClass)) {
                // If there is a non-managed class, we give up on maintaining an MRO shape
                assert mroShape == null : mro.getClassName();
                return true;
            }
        }
        if (mroShape == null) {
            // We could have bailed out from maintaining the MRO shape for some other reason
            return true;
        }
        MroShape currMroShape = mroShape;
        for (int i = 0; i < mro.length(); i++) {
            PythonManagedClass kls = (PythonManagedClass) mro.getItemNormalized(i);
            if (kls.getShape() != currMroShape.shape) {
                String message = String.format("mro:%s,index:%d,curr klass:%s\nactual shape:\n%s,\nexpected shape:\n%s",
                                mro.getClassName(), i, kls, kls.getShape(), currMroShape.shape);
                Set<Object> klsShapeProps = kls.getShape().getPropertyList().stream().filter(x -> !x.isHidden()).map(x -> x.getKey()).collect(Collectors.toSet());
                klsShapeProps.removeIf(x -> DynamicObjectLibrary.getUncached().getOrDefault(kls, x, null) == PNone.NO_VALUE);
                Set<Object> currMroShapeProps = currMroShape.shape.getPropertyList().stream().filter(x -> !x.isHidden()).map(x -> x.getKey()).collect(Collectors.toSet());
                Set<Object> diff = new HashSet<>(klsShapeProps);
                diff.addAll(currMroShapeProps);
                diff.removeIf(x -> klsShapeProps.contains(x) && currMroShapeProps.contains(x));
                // We ignore difference for special attributes that should not influence the MRO
                // lookup results
                diff.remove(__BASICSIZE__);
                assert diff.size() == 0 : message + ",diff:" + String.join(",", diff.stream().map(Object::toString).collect(Collectors.joining(", ")));
            }
            currMroShape = currMroShape.parent;
        }
        assert currMroShape == PythonLanguage.getCurrent().getMroShapeRoot() : mro.getClassName();
        return true;
    }
}
