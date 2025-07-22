/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.nodes.argument;

import static com.oracle.graal.python.builtins.objects.str.StringUtils.joinUncached;
import static com.oracle.graal.python.nodes.BuiltinNames.T_SELF;
import static com.oracle.graal.python.nodes.StringLiterals.T_COMMA_SPACE;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.method.PMethod;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.argument.CreateArgumentsNodeGen.ApplyPositionalArgumentsNodeGen;
import com.oracle.graal.python.nodes.argument.CreateArgumentsNodeGen.HandleTooManyArgumentsNodeGen;
import com.oracle.graal.python.nodes.classes.IsSubtypeNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
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
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.profiles.InlinedIntValueProfile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;

@GenerateUncached
@GenerateInline
@GenerateCached(false)
public abstract class CreateArgumentsNode extends PNodeWithContext {

    /**
     * Creates a {@link PArguments} array from the provided arguments and metadata.
     *
     * @param inliningTarget The inlining target.
     * @param callableOrName This object can either be the function/method object or just a name
     *            ({@link TruffleString}). It is primarily used to create error messages. It is also
     *            used to check if the function
     * @param userArguments The positional arguments as provided by the caller (must not be
     *            {@code null} but may be empty).
     * @param keywords The keyword arguments as provided by the caller (must not be {@code null} but
     *            may be empty).
     * @param signature The callee's signature (i.e. specifies how the callee needs to be invoked).
     * @param self The primary (aka. self) object.
     * @param classObject The class object (if the invoked method is a class method).
     * @param defaults Array of default values for positional arguments.
     * @param kwdefaults Array of default values for keyword arguments.
     * @param methodcall Indicates if this creates arguments for a method call. This is only used
     *            for creating better error messages.
     * @return A {@link PArguments} array.
     */
    public abstract Object[] execute(Node inliningTarget, Object callableOrName, Object[] userArguments, PKeyword[] keywords, Signature signature, Object self, Object classObject,
                    Object[] defaults, PKeyword[] kwdefaults, boolean methodcall);

    @Specialization
    static Object[] doIt(Node inliningTarget, Object callableOrName, Object[] userArguments, PKeyword[] keywords, Signature signature, Object self, Object classObject, Object[] defaults,
                    PKeyword[] kwdefaults, boolean methodcall,
                    @Cached InlinedIntValueProfile lenProfile,
                    @Cached InlinedIntValueProfile maxPosProfile,
                    @Cached InlinedIntValueProfile numKwdsProfile,
                    @Cached LazyApplyKeywordsNode applyKeywords,
                    @Cached LazyHandleTooManyArgumentsNode handleTooManyArgumentsNode,
                    @Cached ApplyPositionalArguments applyPositional,
                    @Cached LazyFillDefaultsNode fillDefaultsNode,
                    @Cached LazyFillKwDefaultsNode fillKwDefaultsNode,
                    @Cached CheckEnclosingTypeNode checkEnclosingTypeNode) {
        int cachedLen = lenProfile.profile(inliningTarget, userArguments.length);
        int cachedMaxPos = maxPosProfile.profile(inliningTarget, signature.getMaxNumOfPositionalArgs());
        int cachedNumKwds = numKwdsProfile.profile(inliningTarget, signature.getNumOfRequiredKeywords());
        assert userArguments.length == cachedLen;

        // see PyPy's Argument#_match_signature method

        // expected formal arguments, without * and **
        boolean too_many_args = false;

        // positional args, kwd only args, varargs, var kwds
        Object[] scope_w = PArguments.create(cachedMaxPos + cachedNumKwds);

        int upfront = 0;
        // put the special w_firstarg into the scope, if it exists
        if (self != null) {
            upfront++;
            if (cachedMaxPos > 0) {
                PArguments.setArgument(scope_w, 0, self);
            }
            if (classObject != null) {
                upfront++;
                PArguments.setArgument(scope_w, 1, classObject);
            }
        }

        int avail = cachedLen + upfront;

        // put as many positional input arguments into place as available
        int input_argcount = applyPositional.execute(inliningTarget, userArguments, scope_w, upfront, cachedMaxPos, cachedLen);

        // collect extra positional arguments into the *vararg
        if (signature.takesVarArgs()) {
            int args_left = cachedMaxPos - upfront;
            if (args_left < 0) {
                // everything goes to starargs, including any magic self
                assert upfront == 1;
                Object[] varargs = new Object[cachedLen + 1];
                varargs[0] = self;
                PythonUtils.arraycopy(userArguments, 0, varargs, 1, cachedLen);
                PArguments.setVariableArguments(scope_w, varargs);
            } else if (cachedLen > args_left) {
                PArguments.setVariableArguments(scope_w, Arrays.copyOfRange(userArguments, args_left, userArguments.length));
            } else {
                // no varargs
            }
        } else if (avail > cachedMaxPos) {
            too_many_args = true;
        }

        // handle keyword arguments
        // match the keywords given at the call site to the argument names.
        // the called node takes and collect the rest in the keywords.
        if (keywords.length > 0) {
            // the lazy node acts as a profile
            applyKeywords.get(inliningTarget).execute(callableOrName, signature, scope_w, keywords);
        }

        if (too_many_args) {
            // the node acts as a profile
            int adjustCount = self instanceof PythonModule ? 1 : 0;
            throw handleTooManyArgumentsNode.get(inliningTarget).execute(scope_w, callableOrName, signature, cachedMaxPos, cachedNumKwds, defaults.length, avail, methodcall, adjustCount);
        }

        boolean more_filling = input_argcount < cachedMaxPos + cachedNumKwds;
        if (more_filling) {

            // then, fill the normal arguments with defaults_w (if needed)
            fillDefaultsNode.get(inliningTarget).execute(callableOrName, signature, scope_w, defaults, input_argcount, cachedMaxPos);

            // finally, fill kwonly arguments with w_kw_defs (if needed)
            fillKwDefaultsNode.get(inliningTarget).execute(callableOrName, scope_w, signature, kwdefaults, cachedMaxPos, cachedNumKwds);
        }

        // Now we know that everything is fine, so check compatibility of the enclosing type.
        // If we are calling a built-in method, and it's function has an enclosing type, we
        // need to check if 'self' is a subtype of the function's enclosing type.
        checkEnclosingTypeNode.execute(inliningTarget, signature, callableOrName, scope_w);

        return scope_w;
    }

    @GenerateInline
    @GenerateCached(false)
    @GenerateUncached
    abstract static class LazyHandleTooManyArgumentsNode extends Node {
        public final HandleTooManyArgumentsNode get(Node inliningTarget) {
            return execute(inliningTarget);
        }

        public abstract HandleTooManyArgumentsNode execute(Node inliningTarget);

        @Specialization
        static HandleTooManyArgumentsNode doIt(@Cached(inline = false) HandleTooManyArgumentsNode node) {
            return node;
        }
    }

    @GenerateUncached
    @GenerateInline(false) // Error handler that we explicitly do not want to inline
    protected abstract static class HandleTooManyArgumentsNode extends PNodeWithContext {

        public abstract PException execute(Object[] scope_w, Object callable, Signature signature, int co_argcount, int co_kwonlyargcount, int ndefaults, int avail, boolean methodcall,
                        int adjustCount);

        @Specialization(guards = {"co_kwonlyargcount == cachedKwOnlyArgCount", "cachedKwOnlyArgCount <= 32"}, limit = "3")
        @ExplodeLoop
        static PException doCached(Object[] scope_w, Object callable, Signature signature, int co_argcount, @SuppressWarnings("unused") int co_kwonlyargcount, int ndefaults, int avail,
                        boolean methodcall, int adjustCount,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode raise,
                        @Shared @Cached TruffleString.EqualNode equalNode,
                        @Cached("co_kwonlyargcount") int cachedKwOnlyArgCount) {
            int kwonly_given = 0;
            for (int i = 0; i < cachedKwOnlyArgCount; i++) {
                if (PArguments.getArgument(scope_w, co_argcount + i) != null) {
                    kwonly_given += 1;
                }
            }
            boolean forgotSelf = methodcall && avail + 1 == co_argcount && (signature.getParameterIds().length == 0 || !equalNode.execute(signature.getParameterIds()[0], T_SELF, TS_ENCODING));
            TruffleString name = getName(callable, signature);
            throw raiseTooManyArguments(inliningTarget, name, co_argcount - adjustCount, ndefaults, avail - adjustCount, forgotSelf, kwonly_given, raise);
        }

        @Specialization(replaces = "doCached")
        static PException doUncached(Object[] scope_w, Object callable, Signature signature, int co_argcount, int co_kwonlyargcount, int ndefaults, int avail, boolean methodcall, int adjustCount,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode raise,
                        @Shared @Cached TruffleString.EqualNode equalNode) {
            int kwonly_given = 0;
            for (int i = 0; i < co_kwonlyargcount; i++) {
                if (PArguments.getArgument(scope_w, co_argcount + i) != null) {
                    kwonly_given += 1;
                }
            }
            boolean forgotSelf = methodcall && avail + 1 == co_argcount && (signature.getParameterIds().length == 0 || !equalNode.execute(signature.getParameterIds()[0], T_SELF, TS_ENCODING));
            TruffleString name = getName(callable, signature);
            throw raiseTooManyArguments(inliningTarget, name, co_argcount - adjustCount, ndefaults, avail - adjustCount, forgotSelf, kwonly_given, raise);
        }

        private static PException raiseTooManyArguments(Node inliningTarget, TruffleString name, int co_argcount, int ndefaults, int avail, boolean forgotSelf, int kwonly_given, PRaiseNode raise) {
            String forgotSelfMsg = forgotSelf ? ". Did you forget 'self' in the function definition?" : "";
            if (ndefaults > 0) {
                if (kwonly_given == 0) {
                    throw raise.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.TAKES_FROM_D_TO_D_POS_ARG_S_BUT_D_S_GIVEN_S,
                                    name,
                                    co_argcount - ndefaults,
                                    co_argcount,
                                    co_argcount == 1 ? "" : "s",
                                    avail,
                                    avail == 1 ? "was" : "were",
                                    forgotSelfMsg);
                } else {
                    throw raise.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.TAKES_FROM_D_TO_D_POS_ARG_S_BUT_D_POS_ARG_S,
                                    name,
                                    co_argcount - ndefaults,
                                    co_argcount,
                                    co_argcount == 1 ? "" : "s",
                                    avail,
                                    avail == 1 ? "" : "s",
                                    kwonly_given,
                                    kwonly_given == 1 ? "" : "s",
                                    forgotSelfMsg);
                }
            } else {
                if (kwonly_given == 0) {
                    throw raise.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.TAKES_D_POS_ARG_S_BUT_GIVEN_S,
                                    name,
                                    co_argcount - ndefaults,
                                    co_argcount == 1 ? "" : "s",
                                    avail,
                                    avail == 1 ? "was" : "were",
                                    forgotSelfMsg);
                } else {
                    throw raise.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.TAKES_D_POS_ARG_S_BUT_D_POS_ARG_S,
                                    name,
                                    co_argcount,
                                    co_argcount == 1 ? "" : "s",
                                    avail,
                                    avail == 1 ? "" : "s",
                                    kwonly_given,
                                    kwonly_given == 1 ? "" : "s",
                                    forgotSelfMsg);
                }
            }
        }

        protected static HandleTooManyArgumentsNode getUncached() {
            return HandleTooManyArgumentsNodeGen.getUncached();
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    @ImportStatic(PythonOptions.class)
    abstract static class CheckEnclosingTypeNode extends Node {

        public abstract void execute(Node inliningTarget, Signature signature, Object callable, Object[] scope_w);

        @Specialization(guards = "checkEnclosingType(signature, callable)")
        static void doEnclosingTypeCheck(Node inliningTarget, @SuppressWarnings("unused") Signature signature, PBuiltinFunction callable, @SuppressWarnings("unused") Object[] scope_w,
                        @Bind("getSelf(scope_w)") Object self,
                        @Cached GetClassNode getClassNode,
                        @Cached(inline = false) IsSubtypeNode isSubtypeMRONode,
                        @Cached PRaiseNode raiseNode) {
            if (!isSubtypeMRONode.execute(getClassNode.execute(inliningTarget, self), callable.getEnclosingType())) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw raiseNode.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.DESCR_S_FOR_P_OBJ_DOESNT_APPLY_TO_P,
                                callable.getName(), callable.getEnclosingType(), self);
            }
        }

        @Specialization(guards = "!checkEnclosingType(signature, callable)")
        @SuppressWarnings("unused")
        static void doNothing(Signature signature, Object callable, Object[] scope_w) {
            // do nothing
        }

        static boolean checkEnclosingType(Signature signature, Object function) {
            return signature.checkEnclosingType() && function instanceof PBuiltinFunction && ((PBuiltinFunction) function).getEnclosingType() != null;
        }

        static Object getSelf(Object[] scope_w) {
            return PArguments.getArgument(scope_w, 0);
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    protected abstract static class ApplyPositionalArguments extends Node {

        public abstract int execute(Node inliningTarget, Object[] args_w, Object[] scope_w, int upfront, int co_argcount, int num_args);

        @Specialization(guards = "upfront < co_argcount")
        static int doIt(Object[] args_w, Object[] scope_w, int upfront, int co_argcount, int num_args) {
            int take = Math.min(num_args, co_argcount - upfront);
            PythonUtils.arraycopy(args_w, 0, scope_w, PArguments.USER_ARGUMENTS_OFFSET + upfront, take);
            return upfront + take;
        }

        @Specialization(guards = "upfront >= co_argcount")
        @SuppressWarnings("unused")
        static int doNothing(Object[] args_w, Object[] scope_w, int upfront, int co_argcount, int num_args) {
            return 0;
        }

        protected static ApplyPositionalArguments getUncached() {
            return ApplyPositionalArgumentsNodeGen.getUncached();
        }
    }

    @GenerateCached(false)
    @GenerateInline
    @GenerateUncached
    abstract static class LazyApplyKeywordsNode extends Node {
        public final ApplyKeywordsNode get(Node inliningTarget) {
            return execute(inliningTarget);
        }

        public abstract ApplyKeywordsNode execute(Node inliningTarget);

        @Specialization
        static ApplyKeywordsNode doIt(@Cached(inline = false) ApplyKeywordsNode applyKeywordsNode) {
            return applyKeywordsNode;
        }
    }

    /**
     * This class is *only* used to apply arguments given as keywords by the caller to positional
     * arguments of the same names. The remaining arguments are left in the PKeywords of the
     * combined arguments.
     *
     * @author tim
     */
    // TODO qualified name is a workaround for a DSL bug
    @com.oracle.truffle.api.dsl.GenerateUncached
    @GenerateInline(false) // Intentionally lazy
    protected abstract static class ApplyKeywordsNode extends PNodeWithContext {
        public abstract Object[] execute(Object callee, Signature calleeSignature, Object[] arguments, PKeyword[] keywords);

        @Specialization(guards = {"kwLen == keywords.length", "calleeSignature == cachedSignature", "kwLen <= 32"}, limit = "3")
        @ExplodeLoop
        static Object[] applyCached(Object callee, @SuppressWarnings("unused") Signature calleeSignature, Object[] arguments, PKeyword[] keywords,
                        @Bind Node inliningTarget,
                        // TODO: GR-46101 make lazy once this Truffle DSL issue is fixed
                        @Shared @Cached PRaiseNode raise,
                        @Cached("keywords.length") int kwLen,
                        @SuppressWarnings("unused") @Cached("calleeSignature") Signature cachedSignature,
                        @Cached("cachedSignature.takesVarKeywordArgs()") boolean takesVarKwds,
                        @Cached(value = "cachedSignature.getParameterIds()", dimensions = 1) TruffleString[] parameters,
                        @Cached("parameters.length") int positionalParamNum,
                        @Cached(value = "cachedSignature.getKeywordNames()", dimensions = 1) TruffleString[] kwNames,
                        @Exclusive @Cached InlinedBranchProfile posArgOnlyPassedAsKeywordProfile,
                        @Exclusive @Cached InlinedBranchProfile kwOnlyIdxFoundProfile,
                        @Exclusive @Cached SearchNamedParameterNode searchParamNode,
                        @Exclusive @Cached SearchNamedParameterNode searchKwNode) {
            PKeyword[] unusedKeywords = takesVarKwds ? PKeyword.create(kwLen) : null;
            // same as below
            int k = 0;
            int additionalKwds = 0;
            TruffleString lastWrongKeyword = null;
            int positionalOnlyArgIndex = calleeSignature.getPositionalOnlyArgIndex();
            List<TruffleString> posArgOnlyPassedAsKeywordNames = null;
            for (int i = 0; i < kwLen; i++) {
                PKeyword kwArg = keywords[i];
                TruffleString name = kwArg.getName();
                int kwIdx = searchParamNode.execute(inliningTarget, parameters, name);
                if (kwIdx == -1) {
                    int kwOnlyIdx = searchKwNode.execute(inliningTarget, kwNames, name);
                    if (kwOnlyIdx != -1) {
                        kwOnlyIdxFoundProfile.enter(inliningTarget);
                        kwIdx = kwOnlyIdx + positionalParamNum;
                    }
                }

                if (kwIdx != -1) {
                    if (positionalOnlyArgIndex > -1 && kwIdx < positionalOnlyArgIndex) {
                        if (unusedKeywords != null) {
                            unusedKeywords[k++] = kwArg;
                        } else {
                            posArgOnlyPassedAsKeywordProfile.enter(inliningTarget);
                            posArgOnlyPassedAsKeywordNames = addPosArgOnlyPassedAsKeyword(posArgOnlyPassedAsKeywordNames, name);
                        }
                    } else {
                        if (PArguments.getArgument(arguments, kwIdx) != null) {
                            throw raise.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.S_PAREN_GOT_MULTIPLE_VALUES_FOR_ARG, getName(callee, cachedSignature), name);
                        }
                        PArguments.setArgument(arguments, kwIdx, kwArg.getValue());
                    }
                } else if (unusedKeywords != null) {
                    unusedKeywords[k++] = kwArg;
                } else {
                    additionalKwds++;
                    lastWrongKeyword = name;
                }
            }
            storeKeywordsOrRaise(callee, arguments, unusedKeywords, k, additionalKwds, lastWrongKeyword, posArgOnlyPassedAsKeywordNames, inliningTarget, posArgOnlyPassedAsKeywordProfile, raise,
                            cachedSignature);
            return arguments;
        }

        @Specialization(replaces = "applyCached")
        static Object[] applyUncached(Object callee, Signature calleeSignature, Object[] arguments, PKeyword[] keywords,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode raise,
                        @Exclusive @Cached InlinedBranchProfile posArgOnlyPassedAsKeywordProfile,
                        @Exclusive @Cached InlinedBranchProfile kwOnlyIdxFoundProfile,
                        @Exclusive @Cached SearchNamedParameterNode searchParamNode,
                        @Exclusive @Cached SearchNamedParameterNode searchKwNode) {
            TruffleString[] parameters = calleeSignature.getParameterIds();
            int positionalParamNum = parameters.length;
            TruffleString[] kwNames = calleeSignature.getKeywordNames();
            int kwLen = keywords.length;
            PKeyword[] unusedKeywords = calleeSignature.takesVarKeywordArgs() ? PKeyword.create(kwLen) : null;
            // same as above
            int k = 0;
            int additionalKwds = 0;
            TruffleString lastWrongKeyword = null;
            int positionalOnlyArgIndex = calleeSignature.getPositionalOnlyArgIndex();
            List<TruffleString> posArgOnlyPassedAsKeywordNames = null;
            for (int i = 0; i < kwLen; i++) {
                PKeyword kwArg = keywords[i];
                TruffleString name = kwArg.getName();
                int kwIdx = searchParamNode.execute(inliningTarget, parameters, name);
                if (kwIdx == -1) {
                    int kwOnlyIdx = searchKwNode.execute(inliningTarget, kwNames, name);
                    if (kwOnlyIdx != -1) {
                        kwOnlyIdxFoundProfile.enter(inliningTarget);
                        kwIdx = kwOnlyIdx + positionalParamNum;
                    }
                }

                if (kwIdx != -1) {
                    if (positionalOnlyArgIndex > -1 && kwIdx < positionalOnlyArgIndex) {
                        if (unusedKeywords != null) {
                            unusedKeywords[k++] = kwArg;
                        } else {
                            posArgOnlyPassedAsKeywordProfile.enter(inliningTarget);
                            posArgOnlyPassedAsKeywordNames = addPosArgOnlyPassedAsKeyword(posArgOnlyPassedAsKeywordNames, name);
                        }
                    } else {
                        if (PArguments.getArgument(arguments, kwIdx) != null) {
                            throw raise.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.S_PAREN_GOT_MULTIPLE_VALUES_FOR_ARG, getName(callee, calleeSignature), name);
                        }
                        PArguments.setArgument(arguments, kwIdx, kwArg.getValue());
                    }
                } else if (unusedKeywords != null) {
                    unusedKeywords[k++] = kwArg;
                } else {
                    additionalKwds++;
                    lastWrongKeyword = name;
                }
            }
            storeKeywordsOrRaise(callee, arguments, unusedKeywords, k, additionalKwds, lastWrongKeyword,
                            posArgOnlyPassedAsKeywordNames, inliningTarget, posArgOnlyPassedAsKeywordProfile, raise, calleeSignature);
            return arguments;
        }

        @TruffleBoundary
        private static List<TruffleString> addPosArgOnlyPassedAsKeyword(List<TruffleString> names, TruffleString name) {
            if (names == null) {
                List<TruffleString> newList = new ArrayList<>();
                newList.add(name);
                return newList;
            }
            names.add(name);
            return names;
        }

        private static void storeKeywordsOrRaise(Object callee, Object[] arguments, PKeyword[] unusedKeywords, int unusedKeywordCount, int tooManyKeywords, TruffleString lastWrongKeyword,
                        List<TruffleString> posArgOnlyPassedAsKeywordNames, Node inliningTarget, InlinedBranchProfile posArgOnlyPassedAsKeywordProfile, PRaiseNode raise, Signature signature) {
            if (tooManyKeywords == 1) {
                throw raise.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.GOT_UNEXPECTED_KEYWORD_ARG, getName(callee, signature), lastWrongKeyword);
            } else if (tooManyKeywords > 1) {
                throw raise.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.GOT_UNEXPECTED_KEYWORD_ARG, getName(callee, signature), tooManyKeywords);
            } else if (posArgOnlyPassedAsKeywordNames != null) {
                posArgOnlyPassedAsKeywordProfile.enter(inliningTarget);
                TruffleString names = joinUncached(T_COMMA_SPACE, posArgOnlyPassedAsKeywordNames);
                throw raise.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.GOT_SOME_POS_ONLY_ARGS_PASSED_AS_KEYWORD, getName(callee, signature), names);
            } else if (unusedKeywords != null) {
                PArguments.setKeywordArguments(arguments, Arrays.copyOf(unusedKeywords, unusedKeywordCount));
            }
        }

        @GenerateUncached
        @GenerateInline
        @GenerateCached(false)
        protected abstract static class SearchNamedParameterNode extends Node {
            public abstract int execute(Node inliningTarget, TruffleString[] parameters, TruffleString name);

            @Idempotent // I think this is true, I would just like the assertion
            protected static boolean nameIsAtIndex(TruffleString[] parameters, TruffleString name, int index) {
                return uncached(parameters, name, TruffleString.EqualNode.getUncached()) == index;
            }

            @SuppressWarnings("unused")
            @Specialization(guards = {"name == cachedName", "parameters == cachedParameters", "nameIsAtIndex(cachedParameters, cachedName, index)"}, limit = "1")
            static int cachedSingle(TruffleString[] parameters, TruffleString name,
                            @Cached("name") TruffleString cachedName,
                            @Cached(value = "parameters", dimensions = 0) TruffleString[] cachedParameters,
                            @Shared @Cached(inline = false) TruffleString.EqualNode equalNode,
                            @Cached("uncached(parameters, name, equalNode)") int index) {
                return index;
            }

            @Specialization(guards = {"cachedLen == parameters.length", "cachedLen <= 32"}, replaces = "cachedSingle", limit = "3")
            @ExplodeLoop
            static int cached(TruffleString[] parameters, TruffleString name,
                            @Cached("parameters.length") int cachedLen,
                            @Shared @Cached(inline = false) TruffleString.EqualNode equalNode) {
                int idx = -1;
                for (int i = 0; i < cachedLen; i++) {
                    if (equalNode.execute(parameters[i], name, TS_ENCODING)) {
                        idx = i;
                    }
                }
                return idx;
            }

            @Specialization(replaces = "cached")
            static int uncached(TruffleString[] parameters, TruffleString name,
                            @Shared @Cached(inline = false) TruffleString.EqualNode equalNode) {
                for (int i = 0; i < parameters.length; i++) {
                    if (equalNode.execute(parameters[i], name, TS_ENCODING)) {
                        return i;
                    }
                }
                return -1;
            }
        }
    }

    protected abstract static class FillBaseNode extends PNodeWithContext {

        protected static PException raiseMissing(Node inliningTarget, Object callable, TruffleString[] missingNames, int missingCnt, TruffleString type, PRaiseNode raise, Signature signature) {
            throw raise.raise(inliningTarget, PythonBuiltinClassType.TypeError, ErrorMessages.MISSING_D_REQUIRED_S_ARGUMENT_S_S,
                            getName(callable, signature),
                            missingCnt,
                            type,
                            missingCnt == 1 ? "" : "s",
                            missingCnt == 1 ? missingNames[0]
                                            : joinArgNames(missingNames, missingCnt));
        }

        @TruffleBoundary
        private static TruffleString joinArgNames(TruffleString[] missingNames, int missingCnt) {
            TruffleStringBuilder sb = TruffleStringBuilder.create(TS_ENCODING);
            sb.appendStringUncached(missingNames[0]);
            if (missingCnt == 2) {
                sb.appendStringUncached(toTruffleStringUncached("' and '"));
            } else {
                TruffleString delim = toTruffleStringUncached("', '");
                for (int i = 1; i < missingCnt - 1; ++i) {
                    sb.appendStringUncached(delim);
                    sb.appendStringUncached(missingNames[i]);
                }
                sb.appendStringUncached(toTruffleStringUncached("', and '"));
            }
            sb.appendStringUncached(missingNames[missingCnt - 1]);
            return sb.toStringUncached();
        }

        protected static boolean checkIterations(int input_argcount, int co_argcount) {
            return co_argcount - input_argcount < 32;
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    protected abstract static class LazyFillDefaultsNode extends Node {
        public final FillDefaultsNode get(Node inliningTarget) {
            return execute(inliningTarget);
        }

        public abstract FillDefaultsNode execute(Node inliningTarget);

        @Specialization
        static FillDefaultsNode doIt(@Cached(inline = false) FillDefaultsNode node) {
            return node;
        }
    }

    @GenerateUncached
    @GenerateInline(false) // Intentionally lazy
    protected abstract static class FillDefaultsNode extends FillBaseNode {

        public abstract void execute(Object callable, Signature signature, Object[] scope_w, Object[] defaults, int input_argcount, int co_argcount);

        @Specialization(guards = {"input_argcount == cachedInputArgcount", "co_argcount == cachedArgcount", "checkIterations(input_argcount, co_argcount)"}, limit = "3")
        @ExplodeLoop
        static void doCached(Object callable, Signature signature, Object[] scope_w, Object[] defaults, @SuppressWarnings("unused") int input_argcount, @SuppressWarnings("unused") int co_argcount,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode raise,
                        @Cached("input_argcount") int cachedInputArgcount,
                        @Cached("co_argcount") int cachedArgcount,
                        @Shared @Cached InlinedConditionProfile missingProfile) {
            TruffleString[] missingNames = new TruffleString[cachedArgcount - cachedInputArgcount];
            int firstDefaultArgIdx = cachedArgcount - defaults.length;
            int missingCnt = 0;
            for (int i = cachedInputArgcount; i < cachedArgcount; i++) {
                if (PArguments.getArgument(scope_w, i) != null) {
                    continue;
                }
                int defnum = i - firstDefaultArgIdx;
                if (defnum >= 0) {
                    PArguments.setArgument(scope_w, i, defaults[defnum]);
                } else {
                    missingNames[missingCnt++] = signature.getParameterIds()[i];
                }
            }
            if (missingProfile.profile(inliningTarget, missingCnt > 0)) {
                throw raiseMissing(inliningTarget, callable, missingNames, missingCnt, toTruffleStringUncached("positional"), raise, signature);
            }
        }

        @Specialization(replaces = "doCached")
        static void doUncached(Object callable, Signature signature, Object[] scope_w, Object[] defaults, int input_argcount, int co_argcount,
                        @Bind Node inliningTarget,
                        @Shared @Cached PRaiseNode raise,
                        @Shared @Cached InlinedConditionProfile missingProfile) {
            TruffleString[] missingNames = new TruffleString[co_argcount - input_argcount];
            int firstDefaultArgIdx = co_argcount - defaults.length;
            int missingCnt = 0;
            for (int i = input_argcount; i < co_argcount; i++) {
                if (PArguments.getArgument(scope_w, i) != null) {
                    continue;
                }
                int defnum = i - firstDefaultArgIdx;
                if (defnum >= 0) {
                    PArguments.setArgument(scope_w, i, defaults[defnum]);
                } else {
                    missingNames[missingCnt++] = signature.getParameterIds()[i];
                }
            }
            if (missingProfile.profile(inliningTarget, missingCnt > 0)) {
                throw raiseMissing(inliningTarget, callable, missingNames, missingCnt, toTruffleStringUncached("positional"), raise, signature);
            }
        }
    }

    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    protected abstract static class LazyFillKwDefaultsNode extends FillBaseNode {
        public final FillKwDefaultsNode get(Node inliningTarget) {
            return execute(inliningTarget);
        }

        protected abstract FillKwDefaultsNode execute(Node inliningTarget);

        @Specialization
        static FillKwDefaultsNode doIt(@Cached(inline = false) FillKwDefaultsNode node) {
            return node;
        }
    }

    @GenerateUncached
    @GenerateInline(false) // Intentionally lazy
    protected abstract static class FillKwDefaultsNode extends FillBaseNode {
        public abstract void execute(Object callable, Object[] scope_w, Signature signature, PKeyword[] kwdefaults, int co_argcount, int co_kwonlyargcount);

        @Specialization(guards = {"co_argcount == cachedArgcount", "co_kwonlyargcount == cachedKwOnlyArgcount", "checkIterations(co_argcount, co_kwonlyargcount)"}, limit = "2")
        @ExplodeLoop
        static void doCached(Object callable, Object[] scope_w, Signature signature, PKeyword[] kwdefaults, @SuppressWarnings("unused") int co_argcount,
                        @SuppressWarnings("unused") int co_kwonlyargcount,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached PRaiseNode raise,
                        @Exclusive @Cached FindKwDefaultNode findKwDefaultNode,
                        @Cached("co_argcount") int cachedArgcount,
                        @Cached("co_kwonlyargcount") int cachedKwOnlyArgcount,
                        @Exclusive @Cached InlinedConditionProfile missingProfile) {
            TruffleString[] missingNames = new TruffleString[cachedKwOnlyArgcount];
            int missingCnt = 0;
            for (int i = cachedArgcount; i < cachedArgcount + cachedKwOnlyArgcount; i++) {
                if (PArguments.getArgument(scope_w, i) != null) {
                    continue;
                }

                TruffleString kwname = signature.getKeywordNames()[i - cachedArgcount];
                PKeyword kwdefault = findKwDefaultNode.execute(inliningTarget, kwdefaults, kwname);
                if (kwdefault != null) {
                    PArguments.setArgument(scope_w, i, kwdefault.getValue());
                } else {
                    missingNames[missingCnt++] = kwname;
                }
            }
            if (missingProfile.profile(inliningTarget, missingCnt > 0)) {
                throw raiseMissing(inliningTarget, callable, missingNames, missingCnt, toTruffleStringUncached("keyword-only"), raise, signature);
            }
        }

        @Specialization(replaces = "doCached")
        static void doUncached(Object callable, Object[] scope_w, Signature signature, PKeyword[] kwdefaults, int co_argcount, int co_kwonlyargcount,
                        @Bind Node inliningTarget,
                        @Exclusive @Cached PRaiseNode raise,
                        @Exclusive @Cached FindKwDefaultNode findKwDefaultNode,
                        @Exclusive @Cached InlinedConditionProfile missingProfile) {
            TruffleString[] missingNames = new TruffleString[co_kwonlyargcount];
            int missingCnt = 0;
            for (int i = co_argcount; i < co_argcount + co_kwonlyargcount; i++) {
                if (PArguments.getArgument(scope_w, i) != null) {
                    continue;
                }

                TruffleString kwname = signature.getKeywordNames()[i - co_argcount];
                PKeyword kwdefault = findKwDefaultNode.execute(inliningTarget, kwdefaults, kwname);
                if (kwdefault != null) {
                    PArguments.setArgument(scope_w, i, kwdefault.getValue());
                } else {
                    missingNames[missingCnt++] = kwname;
                }
            }
            if (missingProfile.profile(inliningTarget, missingCnt > 0)) {
                throw raiseMissing(inliningTarget, callable, missingNames, missingCnt, toTruffleStringUncached("keyword-only"), raise, signature);
            }
        }
    }

    /**
     * finds a keyword-default value by a given name
     */
    @GenerateUncached
    @GenerateInline
    @GenerateCached(false)
    protected abstract static class FindKwDefaultNode extends Node {

        public abstract PKeyword execute(Node inliningTarget, PKeyword[] kwdefaults, TruffleString kwname);

        @Idempotent
        protected final boolean isSingleContext() {
            return PythonLanguage.get(this).isSingleContext();
        }

        @Idempotent // I think this is true, I would just like the assertion
        protected static boolean kwIsCorrect(PKeyword[] kwdefaults, TruffleString kwname, PKeyword result) {
            return doUncached(kwdefaults, kwname, TruffleString.EqualNode.getUncached()) == result;
        }

        protected static TruffleString.EqualNode getUncachedEqualNode() {
            return TruffleString.EqualNode.getUncached();
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"kwname == cachedKwName", "kwdefaults == cachedKwdefaults", "isSingleContext()", "kwIsCorrect(cachedKwdefaults, cachedKwName, result)"}, limit = "1")
        PKeyword cachedSingle(PKeyword[] kwdefaults, TruffleString kwname,
                        @Cached("kwname") TruffleString cachedKwName,
                        @Cached(value = "kwdefaults", weak = true, dimensions = 0) PKeyword[] cachedKwdefaults,
                        @Cached(value = "doUncached(kwdefaults, kwname, getUncachedEqualNode())", weak = true) PKeyword result) {
            return result;
        }

        @Specialization(guards = {"kwdefaults.length == cachedLength", "cachedLength < 32"}, replaces = "cachedSingle", limit = "3")
        @ExplodeLoop(kind = LoopExplosionKind.FULL_UNROLL_UNTIL_RETURN)
        static PKeyword doCached(PKeyword[] kwdefaults, TruffleString kwname,
                        @Cached("kwdefaults.length") int cachedLength,
                        @Shared @Cached(inline = false) TruffleString.EqualNode equalNode) {
            for (int j = 0; j < cachedLength; j++) {
                if (equalNode.execute(kwdefaults[j].getName(), kwname, TS_ENCODING)) {
                    return kwdefaults[j];
                }
            }
            return null;
        }

        @Specialization(replaces = "doCached")
        static PKeyword doUncached(PKeyword[] kwdefaults, TruffleString kwname,
                        @Shared @Cached(inline = false) TruffleString.EqualNode equalNode) {
            for (int j = 0; j < kwdefaults.length; j++) {
                if (equalNode.execute(kwdefaults[j].getName(), kwname, TS_ENCODING)) {
                    return kwdefaults[j];
                }
            }
            return null;
        }
    }

    protected static TruffleString getName(Object callable, Signature signature) {
        if (!signature.getRaiseErrorName().isEmpty()) {
            return signature.getRaiseErrorName();
        }
        if (callable instanceof TruffleString ts) {
            return ts;
        }
        if (callable instanceof PCode) {
            return ((PCode) callable).getName();
        }
        return getProperty(callable, QualnameGetter.INSTANCE);
    }

    protected static Object getSelf(Object callable) {
        if (callable instanceof PBuiltinMethod) {
            return ((PBuiltinMethod) callable).getSelf();
        } else if (callable instanceof PMethod) {
            return ((PMethod) callable).getSelf();
        }
        return null;
    }

    protected static Object getClassObject(Object callable) {
        if (callable instanceof PBuiltinMethod) {
            return ((PBuiltinMethod) callable).getClassObject();
        }
        return null;
    }

    protected static Object getFunction(Object callable) {
        if (callable instanceof PBuiltinMethod) {
            return ((PBuiltinMethod) callable).getFunction();
        } else if (callable instanceof PMethod) {
            return ((PMethod) callable).getFunction();
        }
        return null;
    }

    private static <T> T getProperty(Object callable, Getter<T> getter) {
        if (callable instanceof PFunction) {
            return getter.fromPFunction((PFunction) callable);
        } else if (callable instanceof PBuiltinFunction) {
            return getter.fromPBuiltinFunction((PBuiltinFunction) callable);
        } else if (callable instanceof PBuiltinMethod) {
            PBuiltinFunction function = ((PBuiltinMethod) callable).getBuiltinFunction();
            return getter.fromPBuiltinFunction(function);
        } else if (callable instanceof PMethod) {
            Object function = ((PMethod) callable).getFunction();
            if (function instanceof PBuiltinFunction) {
                return getter.fromPBuiltinFunction((PBuiltinFunction) function);
            } else if (function instanceof PFunction) {
                return getter.fromPFunction((PFunction) function);
            }
        }
        CompilerDirectives.transferToInterpreterAndInvalidate();
        throw new IllegalStateException("cannot create arguments for non-function-or-method");
    }

    private abstract static class Getter<T> {
        public abstract T fromPFunction(PFunction fun);

        public abstract T fromPBuiltinFunction(PBuiltinFunction fun);
    }

    private static final class QualnameGetter extends Getter<TruffleString> {
        private static final QualnameGetter INSTANCE = new QualnameGetter();

        @Override
        public TruffleString fromPFunction(PFunction fun) {
            return fun.getQualname();
        }

        @Override
        public TruffleString fromPBuiltinFunction(PBuiltinFunction fun) {
            return fun.getQualname();
        }
    }
}
