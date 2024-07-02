/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.common.ObjectHashMap;
import com.oracle.graal.python.builtins.objects.common.ObjectHashMap.GetNode;
import com.oracle.graal.python.builtins.objects.common.ObjectHashMap.PutNode;
import com.oracle.graal.python.builtins.objects.common.ObjectHashMapFactory;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.nodes.BuiltinNames;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.util.ArrayBuilder;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

// there is no value to (DSL & host)-inline this node, it can only make the caller node bigger
@GenerateCached
@GenerateUncached
@GenerateInline(false)
public abstract class GetRegisteredClassNode extends PNodeWithContext {

    public abstract Object execute(Object foreignObject);

    @Specialization(assumptions = "getLanguage().noInteropTypeRegisteredAssumption")
    static Object noRegisteredClass(Object foreignObject,
                    @Shared @Cached GetForeignObjectClassNode getForeignObjectClassNode) {
        return getForeignObjectClassNode.execute(foreignObject);
    }

    // Always return ForeignObject for meta objects (classes), because custom behavior should only
    // be added to instances.
    @Specialization(guards = "interopLibrary.isMetaObject(foreignObject) || !interopLibrary.hasMetaObject(foreignObject)")
    static Object getClassLookup(Object foreignObject,
                    @SuppressWarnings("unused") @CachedLibrary(limit = "getCallSiteInlineCacheMaxDepth()") @Exclusive InteropLibrary interopLibrary,
                    @Shared @Cached GetForeignObjectClassNode getForeignObjectClassNode) {
        return getForeignObjectClassNode.execute(foreignObject);
    }

    @Specialization(guards = {"isSingleContext()",
                    "!objectLibrary.isMetaObject(foreignObject)",
                    "objectLibrary.hasMetaObject(foreignObject)",
                    "objectLibrary.isIdentical(metaClass, cachedMetaClass, metaClassLibrary)"}, limit = "getCallSiteInlineCacheMaxDepth()", assumptions = "getContext().interopTypeRegistryCacheValidAssumption.getAssumption()")
    static Object getCachedClassLookup(@SuppressWarnings("unused") Object foreignObject,
                    @SuppressWarnings("unused") @CachedLibrary("foreignObject") InteropLibrary objectLibrary,
                    @SuppressWarnings("unused") @Bind("getMetaObject(objectLibrary, foreignObject)") Object metaClass,
                    @SuppressWarnings("unused") @Cached(value = "metaClass") Object cachedMetaClass,
                    @SuppressWarnings("unused") @CachedLibrary("cachedMetaClass") InteropLibrary metaClassLibrary,
                    @Cached(value = "lookupUncached($node, foreignObject, cachedMetaClass, metaClassLibrary)") Object pythonClass) {
        return pythonClass;
    }

    // used to catch the exception
    static Object getMetaObject(InteropLibrary interopLibrary, Object foreignObject) {
        try {
            return interopLibrary.getMetaObject(foreignObject);
        } catch (UnsupportedMessageException e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }

    static Object lookupUncached(Node inliningTarget, Object foreignObject, Object metaObject, InteropLibrary metaClassLibrary) {
        return lookup(inliningTarget, InlinedConditionProfile.getUncached(), foreignObject, metaObject, metaClassLibrary, ObjectHashMapFactory.GetNodeGen.getUncached());
    }

    @Specialization(replaces = "getCachedClassLookup", guards = {"!objectLibrary.isMetaObject(foreignObject)", "objectLibrary.hasMetaObject(foreignObject)"})
    static Object getFullLookupNode(Object foreignObject,
                    @Bind("$node") Node node,
                    @Cached InlinedConditionProfile inlinedConditionProfile,
                    @Cached ObjectHashMap.GetNode getNode,
                    @CachedLibrary(limit = "getCallSiteInlineCacheMaxDepth()") @Exclusive InteropLibrary objectLibrary,
                    @CachedLibrary(limit = "getCallSiteInlineCacheMaxDepth()") @Exclusive InteropLibrary classLibrary) {
        return lookup(node, inlinedConditionProfile, foreignObject, getMetaObject(objectLibrary, foreignObject), classLibrary, getNode);
    }

    static Object lookup(Node inliningTarget,
                    InlinedConditionProfile builtClassFoundProfile,
                    Object foreignObject,
                    Object foreignMetaObject,
                    InteropLibrary interopLibrary,
                    GetNode getNode) {
        try {
            // lookup generated classes
            // For now, we assume that the keys in the InteropGeneratedClassCache and
            // InteropTypeRegistry do not call back to any python code.
            // Otherwise, the pattern listed in IndirectCallContext#enter must be used.
            var possiblePythonClass = getNode.execute(null,
                            inliningTarget,
                            PythonContext.get(inliningTarget).interopGeneratedClassCache,
                            foreignMetaObject,
                            interopLibrary.identityHashCode(foreignMetaObject));

            return builtClassFoundProfile.profile(inliningTarget, possiblePythonClass != null)
                            ? possiblePythonClass
                            : lookupWithInheritance(inliningTarget, foreignObject, foreignMetaObject, interopLibrary);
        } catch (UnsupportedMessageException e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }

    private static PythonClass buildClassAndRegister(Object foreignObject,
                    Object foreignMetaObject,
                    Object[] bases,
                    Node inliningTarget,
                    PutNode putNode,
                    InteropLibrary interopLibrary) throws UnsupportedMessageException {
        var context = PythonContext.get(inliningTarget);
        String languageName;
        try {
            languageName = context.getEnv().getLanguageInfo(interopLibrary.getLanguage(foreignMetaObject)).getName();
        } catch (UnsupportedMessageException e) {
            // some objects might not expose a language. Use "Foreign" as default
            languageName = "Foreign";
        }
        languageName = languageName.equals("Host") ? "Java" : languageName;
        var className = PythonUtils.toTruffleStringUncached(String.format(
                        "%s_%s_generated",
                        languageName,
                        interopLibrary.getMetaQualifiedName(foreignMetaObject)));

        // use length + 1 to make space for the foreign class
        var basesWithForeign = Arrays.copyOf(bases, bases.length + 1, PythonAbstractClass[].class);
        basesWithForeign[basesWithForeign.length - 1] = GetForeignObjectClassNode.getUncached().execute(foreignObject);
        PythonClass pythonClass;
        try {
            // The call might not succeed if, for instance, the MRO can't be constructed
            pythonClass = context.factory().createPythonClassAndFixupSlots(PythonLanguage.get(inliningTarget), PythonBuiltinClassType.PythonClass, className, bases[0], basesWithForeign);
        } catch (PException e) {
            // Catch the error to additionally print the collected classes and specify the error
            // occurred during class creation
            throw PRaiseNode.getUncached().raiseWithCause(PythonBuiltinClassType.TypeError, e, ErrorMessages.INTEROP_CLASS_CREATION_NOT_POSSIBLE,
                            interopLibrary.getMetaQualifiedName(foreignMetaObject),
                            Arrays.toString(basesWithForeign));
        }
        var module = context.lookupBuiltinModule(BuiltinNames.T_POLYGLOT);
        // set module attribute for nicer debug print, but don't add it to the module to prevent
        // access to the class
        pythonClass.setAttribute(SpecialAttributeNames.T___MODULE__, module.getAttribute(SpecialAttributeNames.T___NAME__));
        putNode.put(null, inliningTarget, context.interopGeneratedClassCache, foreignMetaObject, InteropLibrary.getUncached().identityHashCode(foreignMetaObject), pythonClass);
        return pythonClass;
    }

    @TruffleBoundary
    private static Object lookupWithInheritance(Node inliningTarget, Object foreignObject, Object foreignMetaObject, InteropLibrary interopLibrary) {
        // Try the full and expensive lookup
        try {
            var registry = PythonContext.get(inliningTarget).interopTypeRegistry;
            var foundClasses = new LinkedHashSet<>();
            var getNode = ObjectHashMapFactory.GetNodeGen.getUncached();
            // Search first directly for the class as the key
            // It always return an array of python classes or null
            var possiblePythonClasses = (Object[]) getNode.execute(null,
                            inliningTarget,
                            registry,
                            foreignMetaObject,
                            interopLibrary.identityHashCode(foreignMetaObject));
            if (possiblePythonClasses != null) {
                Collections.addAll(foundClasses, possiblePythonClasses);
            }

            // Now search also for parent classes
            searchAllParentClassesBfs(new Object[]{foreignMetaObject}, foundClasses, registry, getNode, inliningTarget);

            return foundClasses.isEmpty()
                            ? GetForeignObjectClassNode.getUncached().execute(foreignObject)
                            : buildClassAndRegister(foreignObject,
                                            foreignMetaObject,
                                            foundClasses.toArray(),
                                            inliningTarget,
                                            ObjectHashMapFactory.PutNodeGen.getUncached(),
                                            interopLibrary);
        } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }

    private static void searchAllParentClassesBfs(Object[] children,
                    LinkedHashSet<Object> foundClasses,
                    ObjectHashMap registry,
                    ObjectHashMap.GetNode getNode,
                    Node inliningTarget) throws UnsupportedMessageException, InvalidArrayIndexException {
        if (children == null || children.length == 0) {
            // branch finished
            return;
        }
        ArrayBuilder<Object> grandChildren = new ArrayBuilder<>();
        var interopLibrary = InteropLibrary.getUncached();
        for (var child : children) {
            if (!interopLibrary.hasMetaParents(child)) {
                continue;
            }
            var parents = interopLibrary.getMetaParents(child);
            var size = interopLibrary.getArraySize(parents);

            // iterate over all parents of the current class/interface.
            for (long i = 0; i < size; ++i) {
                if (!interopLibrary.isArrayElementReadable(parents, i)) {
                    continue;
                }
                var metaObject = interopLibrary.readArrayElement(parents, i);

                // The registry stores either null or an array as values
                Object[] possibleClasses = (Object[]) getNode.execute(null,
                                inliningTarget,
                                registry,
                                metaObject,
                                interopLibrary.identityHashCode(metaObject));

                if (possibleClasses != null) {
                    Collections.addAll(foundClasses, possibleClasses);
                }
                grandChildren.add(metaObject);
            }
        }
        searchAllParentClassesBfs(grandChildren.toArray(PythonUtils.EMPTY_OBJECT_ARRAY),
                        foundClasses,
                        registry,
                        getNode,
                        inliningTarget);
    }
}
