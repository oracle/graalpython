/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.property;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.AttributeError;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___DOC__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DOC__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ISABSTRACTMETHOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SET_NAME__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ISABSTRACTMETHOD__;

import java.util.List;

import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotDescrGet.DescrGetBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotDescrSet.DescrSetBuiltinNode;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectReprAsTruffleStringNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinClassExactProfile;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.GetClassNode.GetPythonObjectClassNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PProperty)
public final class PropertyBuiltins extends PythonBuiltins {
    public static final TpSlots SLOTS = PropertyBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PropertyBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___INIT__, parameterNames = {"$self", "fget", "fset", "fdel", "doc"})
    @GenerateNodeFactory
    abstract static class PropertyInitNode extends PythonBuiltinNode {

        @Specialization
        @TruffleBoundary
        static Object doGeneric(PProperty self, Object fget, Object fset, Object fdel, Object doc) {
            /*
             * CPython explicitly checks if the objects are 'NONE' and if so, they set 'NULL'. Also,
             * they just allow 'NULL' (which indicates a missing parameter and is our
             * 'PNone.NO_VALUE').
             */
            if (!PGuards.isPNone(fget)) {
                self.setFget(fget);
            }
            if (!PGuards.isPNone(fset)) {
                self.setFset(fset);
            }
            if (!PGuards.isPNone(fdel)) {
                self.setFdel(fdel);
            }
            if (doc != PNone.NO_VALUE) {
                self.setDoc(doc);
            }
            // if no docstring given and the getter has one, use that one
            if ((doc == PNone.NO_VALUE || doc == PNone.NONE) && fget != PNone.NO_VALUE) {
                Object get_doc = PyObjectLookupAttr.executeUncached(fget, T___DOC__);
                if (get_doc != PNone.NO_VALUE) {
                    if (IsBuiltinClassExactProfile.profileClassSlowPath(GetPythonObjectClassNode.executeUncached(self), PythonBuiltinClassType.PProperty)) {
                        self.setDoc(get_doc);
                    } else {
                        /*
                         * If this is a property subclass, put __doc__ in dict of the subclass
                         * instance instead, otherwise it gets shadowed by __doc__ in the class's
                         * dict.
                         */
                        WriteAttributeToObjectNode.getUncached().execute(self, T___DOC__, get_doc);
                    }
                    self.setGetterDoc(true);
                }
            }
            return PNone.NONE;
        }
    }

    abstract static class PropertyFuncNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = "!isNoValue(value)")
        @SuppressWarnings("unused")
        static Object doSet(PProperty self, Object value,
                        @Cached PRaiseNode raiseNode) {
            /*
             * That's a bit unfortunate: if we define 'isGetter = true' and 'isSetter = false' then
             * this will use a GetSetDescriptor which has a slightly different error message for
             * read-only attributes. So, we also define setters but they immediately throw an error
             * with expected message. This should be fixed by distinguishing between getset and
             * member descriptors.
             */
            throw raiseNode.raise(PythonBuiltinClassType.AttributeError, ErrorMessages.READONLY_ATTRIBUTE);
        }
    }

    @Builtin(name = "fget", minNumOfPositionalArgs = 1, parameterNames = {"$self", "value"}, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class PropertyFGetNode extends PropertyFuncNode {

        @Specialization(guards = "isNoValue(value)", insertBefore = "doSet")
        static Object doGet(PProperty self, @SuppressWarnings("unused") PNone value) {
            Object fget = self.getFget();
            return fget != null ? fget : PNone.NONE;
        }
    }

    @Builtin(name = "fset", minNumOfPositionalArgs = 1, parameterNames = {"$self", "value"}, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class PropertyFSetNode extends PropertyFuncNode {

        @Specialization(guards = "isNoValue(value)", insertBefore = "doSet")
        static Object doGet(PProperty self, @SuppressWarnings("unused") PNone value) {
            Object fset = self.getFset();
            return fset != null ? fset : PNone.NONE;
        }
    }

    @Builtin(name = "fdel", minNumOfPositionalArgs = 1, parameterNames = {"$self", "value"}, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class PropertyFDelNode extends PropertyFuncNode {

        @Specialization(guards = "isNoValue(value)", insertBefore = "doSet")
        static Object doGet(PProperty self, @SuppressWarnings("unused") PNone value) {
            Object fdel = self.getFdel();
            return fdel != null ? fdel : PNone.NONE;
        }
    }

    @Builtin(name = J___DOC__, minNumOfPositionalArgs = 1, parameterNames = {"$self", "value"}, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    abstract static class PropertyDocNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = "isNoValue(value)")
        static Object doGet(PProperty self, @SuppressWarnings("unused") PNone value) {
            Object doc = self.getDoc();
            return doc != null ? doc : PNone.NONE;
        }

        @Specialization(guards = "!isNoValue(value)")
        static Object doSet(PProperty self, Object value) {
            self.setDoc(value);
            return PNone.NONE;
        }
    }

    abstract static class PropertyCopyingNode extends PythonBinaryBuiltinNode {

        /**
         * Similar to {@code descrobject.c: property_copy}
         */
        @TruffleBoundary
        static Object copy(PProperty pold, Object getArg, Object setArg, Object delArg) {

            Object type = GetPythonObjectClassNode.executeUncached(pold);

            Object get;
            if (PGuards.isPNone(getArg)) {
                get = pold.getFget() != null ? pold.getFget() : PNone.NONE;
            } else {
                get = getArg;
            }
            Object set;
            if (PGuards.isPNone(setArg)) {
                set = pold.getFset() != null ? pold.getFset() : PNone.NONE;
            } else {
                set = setArg;
            }
            Object del;
            if (PGuards.isPNone(delArg)) {
                del = pold.getFdel() != null ? pold.getFdel() : PNone.NONE;
            } else {
                del = delArg;
            }
            Object doc;
            if (pold.getGetterDoc() && get != PNone.NONE) {
                /* make _init use __doc__ from getter */
                doc = PNone.NONE;
            } else {
                doc = pold.getDoc() != null ? pold.getDoc() : PNone.NONE;
            }

            // shortcut: create new property object directly
            if (IsBuiltinClassProfile.profileClassSlowPath(type, PythonBuiltinClassType.PProperty)) {
                PProperty copy = PythonObjectFactory.getUncached().createProperty();
                PropertyInitNode.doGeneric(copy, get, set, del, doc);
                return copy;
            }
            PProperty newProp = (PProperty) CallNode.getUncached().execute(type, get, set, del, doc);
            newProp.setPropertyName(pold.getPropertyName());
            return newProp;
        }

    }

    @Builtin(name = "getter", parameterNames = {"$self", "getter"}, doc = "Descriptor to change the getter on a property.")
    @GenerateNodeFactory
    abstract static class PropertyGetterNode extends PropertyCopyingNode {

        @Specialization
        static Object doGeneric(PProperty self, Object getter) {
            return copy(self, getter, PNone.NO_VALUE, PNone.NO_VALUE);
        }
    }

    @Builtin(name = "setter", parameterNames = {"$self", "setter"}, doc = "Descriptor to change the setter on a property.")
    @GenerateNodeFactory
    abstract static class PropertySetterNode extends PropertyCopyingNode {

        @Specialization
        static Object doGeneric(PProperty self, Object setter) {
            return copy(self, PNone.NO_VALUE, setter, PNone.NO_VALUE);
        }

    }

    @Builtin(name = "deleter", parameterNames = {"$self", "deleter"}, doc = "Descriptor to change the deleter on a property.")
    @GenerateNodeFactory
    abstract static class PropertyDeleterNode extends PropertyCopyingNode {

        @Specialization
        static Object doGeneric(PProperty self, Object deleter) {
            return copy(self, PNone.NO_VALUE, PNone.NO_VALUE, deleter);
        }
    }

    @GenerateInline(false) // error path only, don't inline
    @GenerateUncached
    abstract static class PropertyErrorNode extends Node {
        abstract PException execute(VirtualFrame frame, PProperty self, Object obj, String what);

        @Specialization
        PException error(VirtualFrame frame, PProperty self, Object obj, String what,
                        @Bind("this") Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached TypeNodes.GetQualNameNode getQualNameNode,
                        @Cached PyObjectReprAsTruffleStringNode reprNode,
                        @Cached PRaiseNode raiseNode) {
            TruffleString qualName = getQualNameNode.execute(inliningTarget, getClassNode.execute(inliningTarget, obj));
            if (self.getPropertyName() != null) {
                TruffleString propertyName = reprNode.execute(frame, inliningTarget, self.getPropertyName());
                throw raiseNode.raise(AttributeError, ErrorMessages.PROPERTY_S_OF_S_OBJECT_HAS_NO_S, propertyName, qualName, what);
            } else {
                throw raiseNode.raise(AttributeError, ErrorMessages.PROPERTY_OF_S_OBJECT_HAS_NO_S, qualName, what);
            }
        }
    }

    @Slot(value = SlotKind.tp_descr_get, isComplex = true)
    @GenerateUncached
    @GenerateNodeFactory
    abstract static class PropertyGetNode extends DescrGetBuiltinNode {

        @Specialization
        static Object get(VirtualFrame frame, PProperty self, Object obj, @SuppressWarnings("unused") Object type,
                        @Bind("this") Node inliningTarget,
                        @Cached InlinedConditionProfile objIsPNoneProfile,
                        @Cached CallUnaryMethodNode callNode,
                        @Cached PropertyErrorNode propertyErrorNode) {
            if (objIsPNoneProfile.profile(inliningTarget, PGuards.isPNone(obj))) {
                return self;
            }

            Object fget = self.getFget();
            if (fget == null) {
                raisePropertyError(frame, self, obj, propertyErrorNode);
            }
            return callNode.executeObject(frame, fget, obj);
        }

        @InliningCutoff
        private static void raisePropertyError(VirtualFrame frame, PProperty self, Object obj, PropertyErrorNode propertyErrorNode) {
            throw propertyErrorNode.execute(frame, self, obj, "getter");
        }
    }

    @Slot(value = SlotKind.tp_descr_set, isComplex = true)
    @GenerateNodeFactory
    abstract static class DescrSet extends DescrSetBuiltinNode {
        @Specialization(guards = "!isNoValue(value)")
        void doGenericSet(VirtualFrame frame, PProperty self, Object obj, Object value,
                        @Cached CallBinaryMethodNode callNode,
                        @Shared @Cached PropertyErrorNode propertyErrorNode) {
            Object func = self.getFset();
            if (func == null) {
                throw propertyErrorNode.execute(frame, self, obj, "setter");
            }
            callNode.executeObject(frame, func, obj, value);
        }

        @Specialization(guards = "isNoValue(value)")
        void doGenericDel(VirtualFrame frame, PProperty self, Object obj, Object value,
                        @Cached CallUnaryMethodNode callNode,
                        @Shared @Cached PropertyErrorNode propertyErrorNode) {
            Object func = self.getFdel();
            if (func == null) {
                throw propertyErrorNode.execute(frame, self, obj, "deleter");
            }
            callNode.executeObject(frame, func, obj);
        }
    }

    @Builtin(name = J___ISABSTRACTMETHOD__, parameterNames = "$self", isGetter = true)
    @GenerateNodeFactory
    abstract static class PropertyIsAbstractMethodNode extends PythonUnaryBuiltinNode {

        @Specialization
        static boolean doGeneric(VirtualFrame frame, PProperty self,
                        @Bind("this") Node inliningTarget,
                        @Cached PyObjectLookupAttr lookup,
                        @Cached PyObjectIsTrueNode isTrueNode) {
            if (isAbstract(frame, inliningTarget, lookup, isTrueNode, self.getFget())) {
                return true;
            }
            if (isAbstract(frame, inliningTarget, lookup, isTrueNode, self.getFset())) {
                return true;
            }
            return isAbstract(frame, inliningTarget, lookup, isTrueNode, self.getFdel());
        }

        private static boolean isAbstract(VirtualFrame frame, Node inliningTarget, PyObjectLookupAttr lookup, PyObjectIsTrueNode isTrueNode, Object func) {
            if (func == null) {
                return false;
            }
            Object result = lookup.execute(frame, inliningTarget, func, T___ISABSTRACTMETHOD__);
            if (result != PNone.NO_VALUE) {
                return isTrueNode.execute(frame, inliningTarget, result);
            }
            return false;
        }
    }

    @Builtin(name = J___SET_NAME__, minNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    abstract static class SetNameNode extends PythonTernaryBuiltinNode {
        @Specialization
        Object setName(PProperty self, @SuppressWarnings("unused") Object type, Object name) {
            self.setPropertyName(name);
            return PNone.NONE;
        }
    }
}
