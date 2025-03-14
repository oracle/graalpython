/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates.
 * Copyright (c) 2014, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.oracle.graal.python.builtins.objects.foreign;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.AttributeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.objects.str.StringUtils.simpleTruffleStringFormatUncached;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___DIR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___HASH__;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.PythonAbstractObject;
import com.oracle.graal.python.builtins.objects.list.ListBuiltins;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltins;
import com.oracle.graal.python.builtins.objects.object.ObjectNodes;
import com.oracle.graal.python.builtins.objects.set.PSet;
import com.oracle.graal.python.builtins.objects.set.SetNodes;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TpSlots.GetObjectSlotsNode;
import com.oracle.graal.python.builtins.objects.type.TypeBuiltins;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlot;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotGetAttr.GetAttrBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSetAttr.SetAttrBuiltinNode;
import com.oracle.graal.python.lib.PyObjectReprAsObjectNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.builtins.ListNodes;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.interop.PForeignToPTypeNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

/*
 * NOTE: We are not using IndirectCallContext here in this file
 * because it seems unlikely that these interop messages would call back to Python
 * and that we would also need precise frame info for that case.
 * Adding it shouldn't hurt peak, but might be a non-trivial overhead in interpreter.
 */
@CoreFunctions(extendClasses = PythonBuiltinClassType.ForeignObject)
public final class ForeignObjectBuiltins extends PythonBuiltins {
    public static TpSlots SLOTS = ForeignObjectBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ForeignObjectBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___HASH__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class HashNode extends PythonUnaryBuiltinNode {
        @Specialization(limit = "getCallSiteInlineCacheMaxDepth()")
        static int hash(Object self,
                        @CachedLibrary("self") InteropLibrary library) {
            if (library.hasIdentity(self)) {
                try {
                    return library.identityHashCode(self);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
            }
            return hashCodeBoundary(self);
        }

        @TruffleBoundary
        private static int hashCodeBoundary(Object self) {
            return self.hashCode();
        }
    }

    @Slot(value = SlotKind.tp_getattro, isComplex = true)
    @GenerateNodeFactory
    abstract static class GetAttributeNode extends GetAttrBuiltinNode {
        @Specialization
        static Object doIt(VirtualFrame frame, Object self, Object name,
                        @Bind("this") Node inliningTarget,
                        @Cached ObjectBuiltins.GetAttributeNode objectGetattrNode,
                        @Cached IsBuiltinObjectProfile isAttrError,
                        @Cached ForeignGetattrNode foreignGetattrNode) {
            /*
             * We want the default Python attribute lookup first and try foreign members last.
             * Because method calls in a Python source should prioritize Python methods over foreign
             * methods.
             */
            try {
                return objectGetattrNode.execute(frame, self, name);
            } catch (PException e) {
                e.expect(inliningTarget, PythonBuiltinClassType.AttributeError, isAttrError);
                return foreignGetattrNode.execute(inliningTarget, self, name);
            }
        }
    }

    @TruffleBoundary(allowInlining = true)
    private static String asJavaPrefixedMethod(String prefix, String member) {
        return prefix + member.substring(0, 1).toUpperCase() + member.substring(1);
    }

    @GenerateInline
    @GenerateCached(false)
    @ImportStatic(PythonOptions.class)
    public abstract static class ForeignGetattrNode extends Node {
        public abstract Object execute(Node inliningTarget, Object object, Object name);

        @Specialization
        static Object doIt(Node inliningTarget, Object object, Object memberObj,
                        @CachedLibrary(limit = "getAttributeAccessInlineCacheMaxDepth()") InteropLibrary read,
                        @Cached(inline = false) CastToJavaStringNode castToString,
                        @Cached(inline = false) GilNode gil,
                        @Cached(inline = false) PForeignToPTypeNode toPythonNode,
                        @Cached PRaiseNode raiseNode) {
            gil.release(true);
            try {
                String member;
                try {
                    member = castToString.execute(memberObj);
                } catch (CannotCastException e) {
                    throw raiseNode.raise(inliningTarget, TypeError, ErrorMessages.ATTR_NAME_MUST_BE_STRING, memberObj);
                }

                if (read.isMemberReadable(object, member)) {
                    return toPythonNode.executeConvert(read.readMember(object, member));
                } else if (PythonLanguage.get(inliningTarget).getEngineOption(PythonOptions.EmulateJython)) {
                    // no profile, above condition should fold to false when EmulateJython is off
                    if (PythonContext.get(inliningTarget).getEnv().isHostObject(object)) {
                        String getter = asJavaPrefixedMethod("get", member);
                        if (read.isMemberInvocable(object, getter)) {
                            try {
                                return toPythonNode.executeConvert(read.invokeMember(object, getter));
                            } catch (UnsupportedTypeException ignored) {
                                // fall through to AttributeError
                            }
                        }
                    }
                }
            } catch (UnknownIdentifierException | UnsupportedMessageException | ArityException ignore) {
            } finally {
                gil.acquire();
            }
            throw raiseNode.raise(inliningTarget, AttributeError, ErrorMessages.FOREIGN_OBJ_HAS_NO_ATTR_S, memberObj);
        }
    }

    @ImportStatic(PGuards.class)
    @Slot(value = SlotKind.tp_setattro, isComplex = true)
    @GenerateNodeFactory
    abstract static class SetattrNode extends SetAttrBuiltinNode {
        @Specialization(guards = "!isNoValue(value)")
        static void doSet(Object object, Object key, Object value,
                        @Bind("this") Node inliningTarget,
                        @Shared @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Shared @Cached CastToJavaStringNode castToString,
                        @Shared @Cached GilNode gil,
                        @Shared @Cached PRaiseNode raiseNode) {
            gil.release(true);
            String member;
            try {
                member = castToString.execute(key);
            } catch (CannotCastException e) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.ATTR_NAME_MUST_BE_STRING, key);
            }
            try {
                try {
                    lib.writeMember(object, member, value);
                    return;
                } catch (UnknownIdentifierException | UnsupportedMessageException e) {
                    if (PythonLanguage.get(inliningTarget).getEngineOption(PythonOptions.EmulateJython)) {
                        // no profile, above condition folds to false when EmulateJython is off
                        try {
                            if (PythonContext.get(inliningTarget).getEnv().isHostObject(object)) {
                                String setter = asJavaPrefixedMethod("set", member);
                                if (lib.isMemberInvocable(object, setter)) {
                                    lib.invokeMember(object, setter, value);
                                    return;
                                }
                            }
                        } catch (ArityException | UnsupportedMessageException | UnknownIdentifierException ignore) {
                            // fall through to AttributeError
                        }
                    }
                }
            } catch (UnsupportedTypeException e) {
                throw raiseNode.raise(inliningTarget, PythonErrorType.TypeError, ErrorMessages.INVALID_TYPE_FOR_S, key);
            } finally {
                gil.acquire();
            }
            throw raiseNode.raise(inliningTarget, PythonErrorType.AttributeError, ErrorMessages.FOREIGN_OBJ_HAS_NO_ATTR_S, key);
        }

        @Specialization(guards = "isNoValue(value)")
        static void doDelete(Object object, Object key, @SuppressWarnings("unused") PNone value,
                        @Bind("this") Node inliningTarget,
                        @Shared @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Shared @Cached CastToJavaStringNode castToString,
                        @Shared @Cached GilNode gil,
                        @Shared @Cached PRaiseNode raiseNode) {
            gil.release(true);
            try {
                lib.removeMember(object, castToString.execute(key));
            } catch (CannotCastException e) {
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.ATTR_NAME_MUST_BE_STRING, key);
            } catch (UnknownIdentifierException | UnsupportedMessageException e) {
                throw raiseNode.raise(inliningTarget, PythonErrorType.AttributeError, ErrorMessages.FOREIGN_OBJ_HAS_NO_ATTR_S, key);
            } finally {
                gil.acquire();
            }
        }
    }

    @Builtin(name = J___DIR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class DirNode extends PythonUnaryBuiltinNode {
        @Specialization
        protected Object doIt(VirtualFrame frame, Object object,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @CachedLibrary(limit = "3") InteropLibrary arrayInterop,
                        @CachedLibrary(limit = "3") InteropLibrary stringInterop,
                        @Cached TruffleString.SwitchEncodingNode switchEncodingNode,
                        @Cached GilNode gil,
                        @Cached InlinedConditionProfile profile,
                        @Cached GetClassNode getClassNode,
                        @Cached TypeBuiltins.DirNode typeDirNode,
                        @Cached SetNodes.AddNode addNode,
                        @Cached(inline = false) ListBuiltins.ListSortNode sortNode,
                        @Cached(inline = false) ListNodes.ConstructListNode constructListNode) {
            // Inspired by ObjectBuiltins.DirNode
            var pythonClass = getClassNode.execute(inliningTarget, object);
            PSet attributes = typeDirNode.execute(frame, pythonClass);

            if (profile.profile(inliningTarget, lib.hasMembers(object))) {
                final Object members;
                gil.release(true);
                try {
                    members = lib.getMembers(object);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere("foreign object claims to have members, but does not return them");
                } finally {
                    gil.acquire();
                }

                try {
                    long size = arrayInterop.getArraySize(members);
                    for (int i = 0; i < size; i++) {
                        TruffleString memberString = stringInterop.asTruffleString(arrayInterop.readArrayElement(members, i));
                        memberString = switchEncodingNode.execute(memberString, TS_ENCODING);
                        addNode.execute(frame, attributes, memberString);
                    }
                } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
            }

            // set to sorted list, like in PyObjectDir
            PList list = constructListNode.execute(frame, attributes);
            sortNode.execute(frame, list);
            return list;
        }
    }

    @Slot(value = SlotKind.tp_str, isComplex = true)
    @GenerateNodeFactory
    abstract static class StrNode extends PythonUnaryBuiltinNode {
        @Child private TruffleString.SwitchEncodingNode switchEncodingNode;

        private static final TpSlot FOREIGN_REPR = SLOTS.tp_repr();

        @Specialization
        Object str(VirtualFrame frame, Object object,
                        @Bind("this") Node inliningTarget,
                        @Cached GetObjectSlotsNode getSlots,
                        @Cached PyObjectReprAsObjectNode reprNode,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached ObjectNodes.DefaultObjectReprNode defaultReprNode,
                        @Cached InlinedBranchProfile isIterator,
                        @Cached InlinedBranchProfile defaultCase) {
            // Check if __repr__ is defined before foreign, if so call that, like object.__str__
            // would do
            TpSlots slots = getSlots.execute(inliningTarget, object);
            if (slots.tp_repr() != FOREIGN_REPR) {
                return reprNode.execute(frame, inliningTarget, object);
            }

            if (lib.isIterator(object)) {
                isIterator.enter(inliningTarget);
                return defaultReprNode.execute(frame, inliningTarget, object);
            }

            defaultCase.enter(inliningTarget);
            return defaultConversion(frame, lib, object);
        }

        protected TruffleString.SwitchEncodingNode getSwitchEncodingNode() {
            if (switchEncodingNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                switchEncodingNode = insert(TruffleString.SwitchEncodingNode.create());
            }
            return switchEncodingNode;
        }

        protected TruffleString defaultConversion(VirtualFrame frame, InteropLibrary lib, Object object) {
            try {
                return getSwitchEncodingNode().execute(lib.asTruffleString(lib.toDisplayString(object)), TS_ENCODING);
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere("toDisplayString result not convertible to String");
            }
        }
    }

    @Slot(value = SlotKind.tp_repr, isComplex = true)
    @GenerateNodeFactory
    abstract static class ReprNode extends StrNode {
        @Child private ObjectNodes.DefaultObjectReprNode defaultReprNode;

        @Override
        protected TruffleString defaultConversion(VirtualFrame frame, InteropLibrary lib, Object object) {
            try {
                if (getContext().getEnv().isHostObject(object)) {
                    boolean isMetaObject = lib.isMetaObject(object);
                    Object metaObject = null;
                    if (isMetaObject) {
                        metaObject = object;
                    } else if (lib.hasMetaObject(object)) {
                        metaObject = lib.getMetaObject(object);
                    }
                    if (metaObject != null) {
                        TruffleString displayName = getSwitchEncodingNode().execute(lib.asTruffleString(lib.toDisplayString(metaObject)), TS_ENCODING);
                        return simpleTruffleStringFormatUncached("<%s[%s] at 0x%s>", isMetaObject ? "JavaClass" : "JavaObject", displayName,
                                        PythonAbstractObject.systemHashCodeAsHexString(object));
                    }
                }
            } catch (UnsupportedMessageException e) {
                // fallthrough to default
            }
            if (defaultReprNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                defaultReprNode = insert(ObjectNodes.DefaultObjectReprNode.create());
            }
            return defaultReprNode.executeCached(frame, object);
        }
    }

}
