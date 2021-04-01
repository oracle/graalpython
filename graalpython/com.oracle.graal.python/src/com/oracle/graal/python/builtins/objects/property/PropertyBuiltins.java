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
package com.oracle.graal.python.builtins.objects.property;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__ISABSTRACTMETHOD__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.call.special.CallBinaryMethodNode;
import com.oracle.graal.python.nodes.call.special.CallUnaryMethodNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PProperty)
public class PropertyBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PropertyBuiltinsFactory.getFactories();
    }

    @Builtin(name = SpecialMethodNames.__INIT__, parameterNames = {"$self", "fget", "fset", "fdel", "doc"})
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
                PythonObjectLibrary fgetLib = PythonObjectLibrary.getFactory().getUncached(fget);
                Object get_doc = fgetLib.lookupAttribute(fget, null, SpecialAttributeNames.__DOC__);
                if (get_doc != PNone.NO_VALUE) {
                    PythonObjectLibrary selfLib = PythonObjectLibrary.getFactory().getUncached(self);
                    if (IsBuiltinClassProfile.profileClassSlowPath(selfLib.getLazyPythonClass(self), PythonBuiltinClassType.PProperty)) {
                        self.setDoc(get_doc);
                    } else {
                        /*
                         * If this is a property subclass, put __doc__ in dict of the subclass
                         * instance instead, otherwise it gets shadowed by __doc__ in the class's
                         * dict.
                         */
                        WriteAttributeToObjectNode.getUncached().execute(self, SpecialAttributeNames.__DOC__, get_doc);
                    }
                    self.setGetterDoc(true);
                }
            }
            return PNone.NONE;
        }
    }

    @Builtin(name = "fget", parameterNames = "$self", isGetter = true)
    @GenerateNodeFactory
    abstract static class PropertyFGetNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object doGeneric(PProperty self) {
            Object fget = self.getFget();
            return fget != null ? fget : PNone.NONE;
        }

    }

    @Builtin(name = "fset", parameterNames = "$self", isGetter = true)
    @GenerateNodeFactory
    abstract static class PropertyFSetNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object doGeneric(PProperty self) {
            Object fset = self.getFset();
            return fset != null ? fset : PNone.NONE;
        }

    }

    @Builtin(name = "fdel", parameterNames = "$self", isGetter = true)
    @GenerateNodeFactory
    abstract static class PropertyFDelNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object doGeneric(PProperty self) {
            Object fdel = self.getFdel();
            return fdel != null ? fdel : PNone.NONE;
        }

    }

    @Builtin(name = SpecialAttributeNames.__DOC__, parameterNames = "$self", isGetter = true)
    @GenerateNodeFactory
    abstract static class PropertyDocNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object doGeneric(PProperty self) {
            Object doc = self.getDoc();
            return doc != null ? doc : PNone.NONE;
        }

    }

    abstract static class PropertyCopyingNode extends PythonBinaryBuiltinNode {

        /**
         * Similar to {@code descrobject.c: property_copy}
         */
        @TruffleBoundary
        static Object copy(PProperty pold, Object getArg, Object setArg, Object delArg) {

            Object type = PythonObjectLibrary.getUncached().getLazyPythonClass(pold);

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
            return CallNode.getUncached().execute(type, get, set, del, doc);
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

    @Builtin(name = SpecialMethodNames.__GET__, minNumOfPositionalArgs = 2, parameterNames = {"$self", "obj", "type"})
    @GenerateNodeFactory
    abstract static class PropertyGetNode extends PythonTernaryBuiltinNode {
        @Child private CallUnaryMethodNode callNode;

        @Specialization(guards = "isPNone(obj)")
        static Object doNone(VirtualFrame frame, PProperty self, Object obj, @SuppressWarnings("unused") Object type) {
            return self;
        }

        @Specialization(replaces = "doNone")
        Object doGeneric(VirtualFrame frame, PProperty self, Object obj, @SuppressWarnings("unused") Object type) {
            if (PGuards.isPNone(obj)) {
                return self;
            }

            Object fget = self.getFget();
            if (fget == null) {
                throw raise(PythonBuiltinClassType.AttributeError, "unreadable attribute");
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

    @Builtin(name = SpecialMethodNames.__SET__, minNumOfPositionalArgs = 2, parameterNames = {"$self", "obj", "value"})
    @GenerateNodeFactory
    abstract static class PropertySetNode extends PythonTernaryBuiltinNode {
        @Child private CallUnaryMethodNode callDeleteNode;
        @Child private CallBinaryMethodNode callSetNode;

        @Specialization
        Object doGeneric(VirtualFrame frame, PProperty self, Object obj, Object value) {
            boolean delete = value == PNone.NO_VALUE;
            Object func = delete ? self.getFdel() : self.getFset();
            if (func == null) {
                throw raise(PythonBuiltinClassType.AttributeError, delete ? "can't delete attribute" : "can't set attribute");
            }

            if (delete) {
                ensureCallDeleteNode().executeObject(frame, func, obj);
            } else {
                ensureCallSetNode().executeObject(frame, func, obj, value);
            }
            return PNone.NONE;
        }

        private CallUnaryMethodNode ensureCallDeleteNode() {
            if (callDeleteNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callDeleteNode = insert(CallUnaryMethodNode.create());
            }
            return callDeleteNode;
        }

        private CallBinaryMethodNode ensureCallSetNode() {
            if (callSetNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                callSetNode = insert(CallBinaryMethodNode.create());
            }
            return callSetNode;
        }
    }

    @Builtin(name = SpecialMethodNames.__ISABSTRACTMETHOD__, parameterNames = "$self", isGetter = true)
    @GenerateNodeFactory
    abstract static class PropertyIsAbstractMethodNode extends PythonUnaryBuiltinNode {

        @Specialization
        static boolean doGeneric(VirtualFrame frame, PProperty self,
                        @CachedLibrary(limit = "4") PythonObjectLibrary lib) {
            if (isAbstract(frame, lib, self.getFget())) {
                return true;
            }
            if (isAbstract(frame, lib, self.getFset())) {
                return true;
            }
            if (isAbstract(frame, lib, self.getFdel())) {
                return true;
            }
            return false;
        }

        private static boolean isAbstract(VirtualFrame frame, PythonObjectLibrary lib, Object func) {
            if (func == null) {
                return false;
            }
            Object result = lib.lookupAttribute(func, frame, __ISABSTRACTMETHOD__);
            if (result != PNone.NO_VALUE) {
                return lib.isTrue(result, frame);
            }
            return false;
        }
    }
}
