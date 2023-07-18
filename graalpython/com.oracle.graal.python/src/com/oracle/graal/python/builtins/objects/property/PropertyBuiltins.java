/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.python.nodes.ErrorMessages.CANT_DELETE_ATTRIBUTE;
import static com.oracle.graal.python.nodes.ErrorMessages.CANT_DELETE_ATTRIBUTE_S;
import static com.oracle.graal.python.nodes.ErrorMessages.CANT_SET_ATTRIBUTE;
import static com.oracle.graal.python.nodes.ErrorMessages.CANT_SET_ATTRIBUTE_S;
import static com.oracle.graal.python.nodes.ErrorMessages.UNREADABLE_ATTRIBUTE;
import static com.oracle.graal.python.nodes.ErrorMessages.UNREADABLE_ATTRIBUTE_S;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___DOC__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___DOC__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___DELETE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GET__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ISABSTRACTMETHOD__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SET_NAME__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SET__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___ISABSTRACTMETHOD__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.lib.PyObjectIsTrueNode;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.lib.PyObjectReprAsTruffleStringNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.InlineIsBuiltinClassProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PProperty)
public final class PropertyBuiltins extends PythonBuiltins {
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
                Object get_doc = PyObjectLookupAttr.getUncached().execute(null, fget, T___DOC__);
                if (get_doc != PNone.NO_VALUE) {
                    if (InlineIsBuiltinClassProfile.profileClassSlowPath(GetClassNode.getUncached().execute(self), PythonBuiltinClassType.PProperty)) {
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
        Object doSet(PProperty self, Object value) {
            /*
             * That's a bit unfortunate: if we define 'isGetter = true' and 'isSetter = false' then
             * this will use a GetSetDescriptor which has a slightly different error message for
             * read-only attributes. So, we also define setters but they immediately throw an error
             * with expected message. This should be fixed by distinguishing between getset and
             * member descriptors.
             */
            throw raise(PythonBuiltinClassType.AttributeError, ErrorMessages.READONLY_ATTRIBUTE);
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

            Object type = GetClassNode.getUncached().execute(pold);

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
            if (InlineIsBuiltinClassProfile.profileClassSlowPath(type, PythonBuiltinClassType.PProperty)) {
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

    @Builtin(name = J___GET__, minNumOfPositionalArgs = 2, parameterNames = {"$self", "obj", "type"})
    @GenerateNodeFactory
    abstract static class PropertyGetNode extends PythonTernaryBuiltinNode {
        @Child private CallUnaryMethodNode callNode;

        @Specialization(guards = "isPNone(obj)")
        static Object doNone(PProperty self, @SuppressWarnings("unused") Object obj, @SuppressWarnings("unused") Object type) {
            return self;
        }

        @Specialization(replaces = "doNone")
        Object doGeneric(VirtualFrame frame, PProperty self, Object obj, @SuppressWarnings("unused") Object type) {
            if (PGuards.isPNone(obj)) {
                return self;
            }

            Object fget = self.getFget();
            if (fget == null) {
                if (self.getPropertyName() != null) {
                    throw raise(AttributeError, UNREADABLE_ATTRIBUTE_S, PyObjectReprAsTruffleStringNode.getUncached().execute(frame, self.getPropertyName()));
                } else {
                    throw raise(AttributeError, UNREADABLE_ATTRIBUTE);
                }
            }
            return ensureCallNode().executeObject(frame, fget, obj);
        }

        private CallUnaryMethodNode ensureCallNode() {
            if (callNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callNode = insert(CallUnaryMethodNode.create());
            }
            return callNode;
        }
    }

    @Builtin(name = J___SET__, minNumOfPositionalArgs = 3, parameterNames = {"$self", "obj", "value"})
    @GenerateNodeFactory
    abstract static class PropertySetNode extends PythonTernaryBuiltinNode {
        @Child private CallBinaryMethodNode callSetNode;

        @Specialization
        Object doGeneric(VirtualFrame frame, PProperty self, Object obj, Object value) {
            Object func = self.getFset();
            if (func == null) {
                if (self.getPropertyName() != null) {
                    throw raise(AttributeError, CANT_SET_ATTRIBUTE_S, PyObjectReprAsTruffleStringNode.getUncached().execute(frame, self.getPropertyName()));
                } else {
                    throw raise(AttributeError, CANT_SET_ATTRIBUTE);
                }
            }
            ensureCallSetNode().executeObject(frame, func, obj, value);
            return PNone.NONE;
        }

        private CallBinaryMethodNode ensureCallSetNode() {
            if (callSetNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callSetNode = insert(CallBinaryMethodNode.create());
            }
            return callSetNode;
        }
    }

    @Builtin(name = J___DELETE__, minNumOfPositionalArgs = 2, parameterNames = {"$self", "obj"})
    @GenerateNodeFactory
    abstract static class PropertyDeleteNode extends PythonBinaryBuiltinNode {
        @Child private CallUnaryMethodNode callDeleteNode;

        @Specialization
        Object doGeneric(VirtualFrame frame, PProperty self, Object obj) {
            Object func = self.getFdel();
            if (func == null) {
                if (self.getPropertyName() != null) {
                    throw raise(AttributeError, CANT_DELETE_ATTRIBUTE_S, PyObjectReprAsTruffleStringNode.getUncached().execute(frame, self.getPropertyName()));
                } else {
                    throw raise(AttributeError, CANT_DELETE_ATTRIBUTE);
                }
            }
            ensureCallDeleteNode().executeObject(frame, func, obj);
            return PNone.NONE;
        }

        private CallUnaryMethodNode ensureCallDeleteNode() {
            if (callDeleteNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callDeleteNode = insert(CallUnaryMethodNode.create());
            }
            return callDeleteNode;
        }
    }

    @Builtin(name = J___ISABSTRACTMETHOD__, parameterNames = "$self", isGetter = true)
    @GenerateNodeFactory
    abstract static class PropertyIsAbstractMethodNode extends PythonUnaryBuiltinNode {

        @Specialization
        static boolean doGeneric(VirtualFrame frame, PProperty self,
                        @Cached PyObjectLookupAttr lookup,
                        @Cached PyObjectIsTrueNode isTrueNode) {
            if (isAbstract(frame, lookup, isTrueNode, self.getFget())) {
                return true;
            }
            if (isAbstract(frame, lookup, isTrueNode, self.getFset())) {
                return true;
            }
            return isAbstract(frame, lookup, isTrueNode, self.getFdel());
        }

        private static boolean isAbstract(VirtualFrame frame, PyObjectLookupAttr lookup, PyObjectIsTrueNode isTrueNode, Object func) {
            if (func == null) {
                return false;
            }
            Object result = lookup.execute(frame, func, T___ISABSTRACTMETHOD__);
            if (result != PNone.NO_VALUE) {
                return isTrueNode.execute(frame, result);
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
