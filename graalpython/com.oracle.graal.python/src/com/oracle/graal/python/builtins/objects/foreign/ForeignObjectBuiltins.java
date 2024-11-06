/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates.
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
import static com.oracle.graal.python.nodes.SpecialAttributeNames.J___BASES__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___BASES__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CALL__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___DIR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___HASH__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INSTANCECHECK__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NEW__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___STR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___INSTANCECHECK__;
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
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.object.ObjectBuiltins;
import com.oracle.graal.python.builtins.objects.object.ObjectNodes;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotGetAttr.GetAttrBuiltinNode;
import com.oracle.graal.python.builtins.objects.type.slots.TpSlotSetAttr.SetAttrBuiltinNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.interop.PForeignToPTypeNode;
import com.oracle.graal.python.nodes.object.BuiltinClassProfiles.IsBuiltinObjectProfile;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsForeignObjectNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.IndirectCallData;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.strings.TruffleString;

/*
 * NOTE: We are not using IndirectCallContext here in this file (except for CallNode)
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

    @Builtin(name = J___ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {

        @Specialization(limit = "3")
        static Object doGeneric(Object object,
                        @Cached PRaiseNode raiseNode,
                        @CachedLibrary("object") InteropLibrary lib,
                        @Cached PForeignToPTypeNode convertNode,
                        @Cached GilNode gil) {
            gil.release(true);
            try {
                if (lib.hasIterator(object)) {
                    return convertNode.executeConvert(lib.getIterator(object));
                }
            } catch (UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            } finally {
                gil.acquire();
            }
            throw raiseNode.raise(TypeError, ErrorMessages.FOREIGN_OBJ_ISNT_ITERABLE);
        }
    }

    @Builtin(name = J___NEW__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    abstract static class NewNode extends PythonBuiltinNode {
        @Specialization(guards = {"isForeignObjectNode.execute(inliningTarget, callee)", "!isNoValue(callee)", "keywords.length == 0"}, limit = "1")
        static Object doInteropCall(Object callee, Object[] arguments, @SuppressWarnings("unused") PKeyword[] keywords,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @SuppressWarnings("unused") @Cached IsForeignObjectNode isForeignObjectNode,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached PForeignToPTypeNode toPTypeNode,
                        @Cached GilNode gil,
                        @Cached PRaiseNode.Lazy raiseNode) {
            gil.release(true);
            try {
                Object res = lib.instantiate(callee, arguments);
                return toPTypeNode.executeConvert(res);
            } catch (ArityException | UnsupportedTypeException | UnsupportedMessageException e) {
                throw raiseNode.get(inliningTarget).raise(PythonErrorType.TypeError, ErrorMessages.INVALID_INSTANTIATION_OF_FOREIGN_OBJ);
            } finally {
                gil.acquire();
            }
        }

        @Fallback
        @SuppressWarnings("unused")
        static Object doGeneric(Object callee, Object arguments, Object keywords,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(PythonErrorType.TypeError, ErrorMessages.INVALID_INSTANTIATION_OF_FOREIGN_OBJ);
        }
    }

    @Builtin(name = J___CALL__, minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    public abstract static class CallNode extends PythonBuiltinNode {
        public final Object executeWithArgs(VirtualFrame frame, Object callee, Object[] arguments) {
            return execute(frame, callee, arguments, PKeyword.EMPTY_KEYWORDS);
        }

        public abstract Object execute(VirtualFrame frame, Object callee, Object[] arguments, PKeyword[] keywords);

        @Specialization(guards = {"isForeignObjectNode.execute(inliningTarget, callee)", "!isNoValue(callee)", "keywords.length == 0"}, limit = "1")
        static Object doInteropCall(VirtualFrame frame, Object callee, Object[] arguments, @SuppressWarnings("unused") PKeyword[] keywords,
                        @SuppressWarnings("unused") @Bind("this") Node inliningTarget,
                        @Cached("createFor(this)") IndirectCallData indirectCallData,
                        @SuppressWarnings("unused") @Cached IsForeignObjectNode isForeignObjectNode,
                        @CachedLibrary(limit = "4") InteropLibrary lib,
                        @Cached PForeignToPTypeNode toPTypeNode,
                        @Cached GilNode gil,
                        @Cached PRaiseNode.Lazy raiseNode) {
            PythonLanguage language = PythonLanguage.get(inliningTarget);
            PythonContext context = PythonContext.get(inliningTarget);
            try {
                Object state = IndirectCallContext.enter(frame, language, context, indirectCallData);
                gil.release(true);
                try {
                    if (lib.isExecutable(callee)) {
                        return toPTypeNode.executeConvert(lib.execute(callee, arguments));
                    } else {
                        return toPTypeNode.executeConvert(lib.instantiate(callee, arguments));
                    }
                } finally {
                    gil.acquire();
                    IndirectCallContext.exit(frame, language, context, state);
                }
            } catch (ArityException | UnsupportedTypeException | UnsupportedMessageException e) {
                throw raiseNode.get(inliningTarget).raise(PythonErrorType.TypeError, ErrorMessages.INVALID_INSTANTIATION_OF_FOREIGN_OBJ);
            }
        }

        @Fallback
        @SuppressWarnings("unused")
        static Object doGeneric(Object callee, Object arguments, Object keywords,
                        @Cached PRaiseNode raiseNode) {
            throw raiseNode.raise(PythonErrorType.TypeError, ErrorMessages.INVALID_INSTANTIATION_OF_FOREIGN_OBJ);
        }

        @NeverDefault
        public static CallNode create() {
            return ForeignObjectBuiltinsFactory.CallNodeFactory.create(null);
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
    abstract static class ForeignGetattrNode extends Node {
        abstract Object execute(Node inliningTarget, Object object, Object name);

        @Specialization
        static Object doIt(Node inliningTarget, Object object, Object memberObj,
                        @CachedLibrary(limit = "getAttributeAccessInlineCacheMaxDepth()") InteropLibrary read,
                        @Cached(inline = false) CastToJavaStringNode castToString,
                        @Cached(inline = false) GilNode gil,
                        @Cached(inline = false) PForeignToPTypeNode toPythonNode,
                        @Cached PRaiseNode.Lazy raiseNode) {
            gil.release(true);
            try {
                String member = castToString.execute(memberObj);
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
            } catch (CannotCastException e) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, ErrorMessages.ATTR_NAME_MUST_BE_STRING, memberObj);
            } catch (UnknownIdentifierException | UnsupportedMessageException | ArityException ignore) {
            } finally {
                gil.acquire();
            }
            throw raiseNode.get(inliningTarget).raise(PythonErrorType.AttributeError, ErrorMessages.FOREIGN_OBJ_HAS_NO_ATTR_S, memberObj);
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
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            gil.release(true);
            String member;
            try {
                member = castToString.execute(key);
            } catch (CannotCastException e) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, ErrorMessages.ATTR_NAME_MUST_BE_STRING, key);
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
                throw raiseNode.get(inliningTarget).raise(PythonErrorType.TypeError, ErrorMessages.INVALID_TYPE_FOR_S, key);
            } finally {
                gil.acquire();
            }
            throw raiseNode.get(inliningTarget).raise(PythonErrorType.AttributeError, ErrorMessages.FOREIGN_OBJ_HAS_NO_ATTR_S, key);
        }

        @Specialization(guards = "isNoValue(value)")
        static void doDelete(Object object, Object key, @SuppressWarnings("unused") PNone value,
                        @Bind("this") Node inliningTarget,
                        @Shared @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Shared @Cached CastToJavaStringNode castToString,
                        @Shared @Cached GilNode gil,
                        @Shared @Cached PRaiseNode.Lazy raiseNode) {
            gil.release(true);
            try {
                lib.removeMember(object, castToString.execute(key));
            } catch (CannotCastException e) {
                throw raiseNode.get(inliningTarget).raise(PythonBuiltinClassType.TypeError, ErrorMessages.ATTR_NAME_MUST_BE_STRING, key);
            } catch (UnknownIdentifierException | UnsupportedMessageException e) {
                throw raiseNode.get(inliningTarget).raise(PythonErrorType.AttributeError, ErrorMessages.FOREIGN_OBJ_HAS_NO_ATTR_S, key);
            } finally {
                gil.acquire();
            }
        }
    }

    // TODO dir(foreign) should list both foreign object members and attributes from class
    @Builtin(name = J___DIR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class DirNode extends PythonUnaryBuiltinNode {
        @Specialization
        protected Object doIt(Object object,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached GilNode gil,
                        @Cached PythonObjectFactory.Lazy factory) {
            if (lib.hasMembers(object)) {
                gil.release(true);
                try {
                    return lib.getMembers(object);
                } catch (UnsupportedMessageException e) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new IllegalStateException("foreign object claims to have members, but does not return them");
                } finally {
                    gil.acquire();
                }
            } else {
                return factory.get(inliningTarget).createList();
            }
        }
    }

    @Builtin(name = J___STR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class StrNode extends PythonUnaryBuiltinNode {
        @Child private TruffleString.SwitchEncodingNode switchEncodingNode;

        @Specialization
        Object str(VirtualFrame frame, Object object,
                        @Bind("this") Node inliningTarget,
                        @Cached GetClassNode getClassNode,
                        @Cached(parameters = "T___REPR__") LookupAttributeInMRONode lookupAttributeInMRONode,
                        @Cached(parameters = "Repr") LookupAndCallUnaryNode reprNode,
                        @CachedLibrary(limit = "3") InteropLibrary lib,
                        @Cached ObjectNodes.DefaultObjectReprNode defaultReprNode,
                        @Cached InlinedBranchProfile isIterator,
                        @Cached InlinedBranchProfile defaultCase) {
            // Check if __repr__ is defined before foreign, if so call that, like object.__str__
            // would do
            var klass = getClassNode.execute(inliningTarget, object);
            var repr = lookupAttributeInMRONode.execute(klass);
            var foreignObjectBuiltinsRepr = lookupAttributeInMRONode.execute(PythonBuiltinClassType.ForeignObject);
            if (repr != foreignObjectBuiltinsRepr) {
                return reprNode.executeObject(frame, object);
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

    @Builtin(name = J___REPR__, minNumOfPositionalArgs = 1)
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

    @Builtin(name = J___BASES__, minNumOfPositionalArgs = 1, isGetter = true, isSetter = false)
    @GenerateNodeFactory
    @ImportStatic(PGuards.class)
    abstract static class BasesNode extends PythonUnaryBuiltinNode {
        @Specialization(limit = "3")
        static Object getBases(Object self,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary("self") InteropLibrary lib,
                        @Cached PythonObjectFactory factory,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (lib.isMetaObject(self)) {
                return factory.createTuple(PythonUtils.EMPTY_OBJECT_ARRAY);
            } else {
                throw raiseNode.get(inliningTarget).raise(AttributeError, ErrorMessages.FOREIGN_OBJ_HAS_NO_ATTR_S, T___BASES__);
            }
        }
    }

    @Builtin(name = J___INSTANCECHECK__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @ImportStatic(PGuards.class)
    abstract static class InstancecheckNode extends PythonBinaryBuiltinNode {
        @Specialization(limit = "3")
        static Object check(Object self, Object instance,
                        @Bind("this") Node inliningTarget,
                        @CachedLibrary("self") InteropLibrary lib,
                        @Cached GilNode gil,
                        @Cached PRaiseNode.Lazy raiseNode) {
            if (lib.isMetaObject(self)) {
                gil.release(true);
                try {
                    return lib.isMetaInstance(self, instance);
                } catch (UnsupportedMessageException e) {
                    throw CompilerDirectives.shouldNotReachHere();
                } finally {
                    gil.acquire();
                }
            } else {
                throw raiseNode.get(inliningTarget).raise(AttributeError, ErrorMessages.FOREIGN_OBJ_HAS_NO_ATTR_S, T___INSTANCECHECK__);
            }
        }
    }

}
