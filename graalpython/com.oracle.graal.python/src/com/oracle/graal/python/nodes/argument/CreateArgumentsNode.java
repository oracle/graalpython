/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;

import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.function.Signature;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.method.PMethod;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.argument.CreateArgumentsNodeGen.ApplyPositionalArgumentsNodeGen;
import com.oracle.graal.python.nodes.argument.CreateArgumentsNodeGen.CreateAndCheckArgumentsNodeGen;
import com.oracle.graal.python.nodes.argument.CreateArgumentsNodeGen.FillDefaultsNodeGen;
import com.oracle.graal.python.nodes.argument.CreateArgumentsNodeGen.FillKwDefaultsNodeGen;
import com.oracle.graal.python.nodes.argument.CreateArgumentsNodeGen.FindKwDefaultNodeGen;
import com.oracle.graal.python.nodes.argument.CreateArgumentsNodeGen.HandleTooManyArgumentsNodeGen;
import com.oracle.graal.python.nodes.code.GetSignatureNode;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;

@ImportStatic({PythonOptions.class, PGuards.class})
public abstract class CreateArgumentsNode extends PNodeWithContext {
    public static CreateArgumentsNode create() {
        return CreateArgumentsNodeGen.create();
    }

    @Specialization(guards = {"isMethod(method)", "method == cachedMethod"}, limit = "getVariableArgumentInlineCacheLimit()")
    Object[] doMethodCached(PythonObject method, Object[] userArguments, PKeyword[] keywords,
                    @Cached("create()") CreateAndCheckArgumentsNode createAndCheckArgumentsNode,
                    @Cached("create()") GetSignatureNode getSignatureNode,
                    @Cached("method") @SuppressWarnings("unused") PythonObject cachedMethod) {

        // We do not directly cache these objects because they are compilation final anyway and the
        // getter check the appropriate assumptions.
        Signature signature = getSignatureNode.execute(cachedMethod);
        Object[] defaults = getDefaults(cachedMethod);
        PKeyword[] kwdefaults = getKwDefaults(cachedMethod);
        Object self = getSelf(cachedMethod);
        return createAndCheckArgumentsNode.execute(method, userArguments, keywords, signature, self, defaults, kwdefaults, isMethodCall(self));
    }

    @Specialization(guards = {"isMethod(method)", "getFunction(method) == cachedFunction",
                    "getSelf(method) == cachedSelf"}, limit = "getVariableArgumentInlineCacheLimit()", replaces = "doMethodCached")
    Object[] doMethodFunctionAndSelfCached(PythonObject method, Object[] userArguments, PKeyword[] keywords,
                    @Cached("create()") CreateAndCheckArgumentsNode createAndCheckArgumentsNode,
                    @Cached("getFunction(method)") @SuppressWarnings("unused") Object cachedFunction,
                    @Cached("create()") GetSignatureNode getSignatureNode,
                    @Cached("getSelf(method)") Object cachedSelf) {

        // We do not directly cache these objects because they are compilation final anyway and the
        // getter check the appropriate assumptions.
        Signature signature = getSignatureNode.execute(cachedFunction);
        Object[] defaults = getDefaults(cachedFunction);
        PKeyword[] kwdefaults = getKwDefaults(cachedFunction);
        return createAndCheckArgumentsNode.execute(method, userArguments, keywords, signature, cachedSelf, defaults, kwdefaults, isMethodCall(cachedSelf));
    }

    @Specialization(guards = {"isMethod(method)", "getFunction(method) == cachedFunction"}, limit = "getVariableArgumentInlineCacheLimit()", replaces = "doMethodFunctionAndSelfCached")
    Object[] doMethodFunctionCached(PythonObject method, Object[] userArguments, PKeyword[] keywords,
                    @Cached("create()") CreateAndCheckArgumentsNode createAndCheckArgumentsNode,
                    @Cached("create()") GetSignatureNode getSignatureNode,
                    @Cached("getFunction(method)") @SuppressWarnings("unused") Object cachedFunction) {

        // We do not directly cache these objects because they are compilation final anyway and the
        // getter check the appropriate assumptions.
        Signature signature = getSignatureNode.execute(cachedFunction);
        Object[] defaults = getDefaults(cachedFunction);
        PKeyword[] kwdefaults = getKwDefaults(cachedFunction);
        Object self = getSelf(method);
        return createAndCheckArgumentsNode.execute(method, userArguments, keywords, signature, self, defaults, kwdefaults, isMethodCall(self));
    }

    @Specialization(guards = {"isFunction(callable)", "callable == cachedCallable"}, limit = "getVariableArgumentInlineCacheLimit()")
    Object[] doFunctionCached(PythonObject callable, Object[] userArguments, PKeyword[] keywords,
                    @Cached("create()") CreateAndCheckArgumentsNode createAndCheckArgumentsNode,
                    @Cached("create()") GetSignatureNode getSignatureNode,
                    @Cached("callable") @SuppressWarnings("unused") PythonObject cachedCallable) {

        // We do not directly cache these objects because they are compilation final anyway and the
        // getter check the appropriate assumptions.
        Signature signature = getSignatureNode.execute(cachedCallable);
        Object[] defaults = getDefaults(cachedCallable);
        PKeyword[] kwdefaults = getKwDefaults(cachedCallable);
        return createAndCheckArgumentsNode.execute(callable, userArguments, keywords, signature, null, defaults, kwdefaults, false);
    }

    @Specialization(replaces = {"doFunctionCached", "doMethodCached", "doMethodFunctionAndSelfCached", "doMethodFunctionCached"})
    Object[] uncached(PythonObject callable, Object[] userArguments, PKeyword[] keywords,
                    @Cached("create()") CreateAndCheckArgumentsNode createAndCheckArgumentsNode) {

        // mostly we will be calling proper functions directly here,
        // but sometimes also methods that have functions directly.
        // In all other cases, the arguments

        Signature signature = getSignatureUncached(callable);
        Object[] defaults = getDefaultsUncached(callable);
        PKeyword[] kwdefaults = getKwDefaultsUncached(callable);
        Object self = getSelf(callable);
        boolean methodcall = !(self instanceof PythonModule);

        return createAndCheckArgumentsNode.execute(callable, userArguments, keywords, signature, self, defaults, kwdefaults, methodcall);
    }

    protected abstract static class CreateAndCheckArgumentsNode extends PNodeWithContext {

        @Child private ApplyKeywordsNode applyKeywords;
        @Child private HandleTooManyArgumentsNode handleTooManyArgumentsNode;
        @Child private ApplyPositionalArguments applyPositional = ApplyPositionalArguments.create();
        @Child private FillDefaultsNode fillDefaultsNode;
        @Child private FillKwDefaultsNode fillKwDefaultsNode;

        public static CreateAndCheckArgumentsNode create() {
            return CreateAndCheckArgumentsNodeGen.create();
        }

        public abstract Object[] execute(PythonObject callable, Object[] userArguments, PKeyword[] keywords, Signature signature, Object self, Object[] defaults, PKeyword[] kwdefaults,
                        boolean methodcall);

        @Specialization(guards = {"userArguments.length == cachedLength", "signature.getMaxNumOfPositionalArgs() == cachedMaxPos", "signature.getNumOfRequiredKeywords() == cachedNumKwds"})
        Object[] doCached0(PythonObject callable, Object[] userArguments, PKeyword[] keywords, Signature signature, Object self, Object[] defaults, PKeyword[] kwdefaults,
                        boolean methodcall,
                        @Cached("userArguments.length") int cachedLength,
                        @Cached("signature.getMaxNumOfPositionalArgs()") int cachedMaxPos,
                        @Cached("signature.getNumOfRequiredKeywords()") int cachedNumKwds) {

            return createAndCheckArguments(callable, userArguments, cachedLength, keywords, signature, self, defaults, kwdefaults, methodcall, cachedMaxPos, cachedNumKwds);
        }

        @Specialization(guards = "userArguments.length == cachedLength", replaces = "doCached0")
        Object[] doCached(PythonObject callable, Object[] userArguments, PKeyword[] keywords, Signature signature, Object self, Object[] defaults, PKeyword[] kwdefaults,
                        boolean methodcall,
                        @Cached("userArguments.length") int cachedLength) {

            return createAndCheckArguments(callable, userArguments, cachedLength, keywords, signature, self, defaults, kwdefaults, methodcall, signature.getMaxNumOfPositionalArgs(),
                            signature.getNumOfRequiredKeywords());
        }

        @Specialization(replaces = "doCached")
        Object[] doUncached(PythonObject callable, Object[] userArguments, PKeyword[] keywords, Signature signature, Object self, Object[] defaults, PKeyword[] kwdefaults, boolean methodcall) {
            return createAndCheckArguments(callable, userArguments, userArguments.length, keywords, signature, self, defaults, kwdefaults, methodcall, signature.getMaxNumOfPositionalArgs(),
                            signature.getNumOfRequiredKeywords());
        }

        private Object[] createAndCheckArguments(PythonObject callable, Object[] args_w, int num_args, PKeyword[] keywords, Signature signature, Object self, Object[] defaults, PKeyword[] kwdefaults,
                        boolean methodcall, int co_argcount, int co_kwonlyargcount) {
            assert args_w.length == num_args;

            // see PyPy's Argument#_match_signature method

            // expected formal arguments, without * and **
            boolean too_many_args = false;

            // positional args, kwd only args, varargs, var kwds
            Object[] scope_w = PArguments.create(co_argcount + co_kwonlyargcount);

            int upfront = 0;
            // put the special w_firstarg into the scope, if it exists
            if (self != null) {
                upfront = 1;
                if (co_argcount > 0) {
                    PArguments.setArgument(scope_w, 0, self);
                }
            }

            int avail = num_args + upfront;

            // put as many positional input arguments into place as available
            int input_argcount = applyPositional.execute(args_w, scope_w, upfront, co_argcount, num_args);

            // collect extra positional arguments into the *vararg
            if (signature.takesVarArgs()) {
                int args_left = co_argcount - upfront;
                if (args_left < 0) {
                    // everything goes to starargs, including any magic self
                    assert upfront == 1;
                    Object[] varargs = new Object[num_args + 1];
                    varargs[0] = self;
                    System.arraycopy(args_w, 0, varargs, 1, num_args);
                    PArguments.setVariableArguments(scope_w, varargs);
                } else if (num_args > args_left) {
                    PArguments.setVariableArguments(scope_w, Arrays.copyOfRange(args_w, args_left, args_w.length));
                } else {
                    // no varargs
                }
            } else if (avail > co_argcount) {
                too_many_args = true;
            }

            // handle keyword arguments
            // match the keywords given at the call site to the argument names.
            // the called node takes and collect the rest in the keywords.
            if (keywords.length > 0) {
                // the node acts as a profile
                applyKeywords(callable, signature, scope_w, keywords);
            }

            if (too_many_args) {
                // the node acts as a profile
                throw handleTooManyArguments(scope_w, callable, signature, co_argcount, co_kwonlyargcount, defaults.length, avail, methodcall);
            }

            boolean more_filling = input_argcount < co_argcount + co_kwonlyargcount;
            if (more_filling) {

                // then, fill the normal arguments with defaults_w (if needed)
                fillDefaults(callable, signature, scope_w, defaults, input_argcount, co_argcount);

                // finally, fill kwonly arguments with w_kw_defs (if needed)
                fillKwDefaults(callable, scope_w, signature, kwdefaults, co_argcount, co_kwonlyargcount);
            }

            return scope_w;
        }

        private void applyKeywords(Object callable, Signature signature, Object[] scope_w, PKeyword[] keywords) {
            if (applyKeywords == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                applyKeywords = insert(ApplyKeywordsNode.create());
            }
            applyKeywords.execute(callable, signature, scope_w, keywords);
        }

        private PException handleTooManyArguments(Object[] scope_w, Object callable, Signature signature, int co_argcount, int co_kwonlyargcount, int ndefaults, int avail, boolean methodcall) {
            if (handleTooManyArgumentsNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                handleTooManyArgumentsNode = insert(HandleTooManyArgumentsNode.create());
            }
            return handleTooManyArgumentsNode.execute(scope_w, callable, signature, co_argcount, co_kwonlyargcount, ndefaults, avail, methodcall);
        }

        private void fillDefaults(Object callable, Signature signature, Object[] scope_w, Object[] defaults, int input_argcount, int co_argcount) {
            if (fillDefaultsNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                fillDefaultsNode = insert(FillDefaultsNode.create());
            }
            fillDefaultsNode.execute(callable, signature, scope_w, defaults, input_argcount, co_argcount);
        }

        private void fillKwDefaults(Object callable, Object[] scope_w, Signature signature, PKeyword[] kwdefaults, int co_argcount, int co_kwonlyargcount) {
            if (fillKwDefaultsNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                fillKwDefaultsNode = insert(FillKwDefaultsNode.create());
            }
            fillKwDefaultsNode.execute(callable, scope_w, signature, kwdefaults, co_argcount, co_kwonlyargcount);
        }

    }

    protected abstract static class HandleTooManyArgumentsNode extends PNodeWithContext {

        public abstract PException execute(Object[] scope_w, Object callable, Signature signature, int co_argcount, int co_kwonlyargcount, int ndefaults, int avail, boolean methodcall);

        @Specialization(guards = {"co_kwonlyargcount == cachedKwOnlyArgCount"})
        @ExplodeLoop
        PException doCached(Object[] scope_w, Object callable, Signature signature, int co_argcount, @SuppressWarnings("unused") int co_kwonlyargcount, int ndefaults, int avail,
                        boolean methodcall,
                        @Cached("co_kwonlyargcount") int cachedKwOnlyArgCount) {
            int kwonly_given = 0;
            for (int i = 0; i < cachedKwOnlyArgCount; i++) {
                if (PArguments.getArgument(scope_w, co_argcount + i) != null) {
                    kwonly_given += 1;
                }
            }

            boolean forgotSelf = methodcall && avail + 1 == co_argcount && (signature.getParameterIds().length == 0 || !signature.getParameterIds()[0].equals("self"));
            throw raiseTooManyArguments(callable, co_argcount, ndefaults, avail, forgotSelf, kwonly_given);
        }

        @Specialization(replaces = "doCached")
        PException doUncached(Object[] scope_w, Object callable, Signature signature, int co_argcount, int co_kwonlyargcount, int ndefaults, int avail, boolean methodcall) {
            int kwonly_given = 0;
            for (int i = 0; i < co_kwonlyargcount; i++) {
                if (PArguments.getArgument(scope_w, co_argcount + i) != null) {
                    kwonly_given += 1;
                }
            }

            boolean forgotSelf = methodcall && avail + 1 == co_argcount && (signature.getParameterIds().length == 0 || !signature.getParameterIds()[0].equals("self"));
            throw raiseTooManyArguments(callable, co_argcount, ndefaults, avail, forgotSelf, kwonly_given);
        }

        private PException raiseTooManyArguments(Object callable, int co_argcount, int ndefaults, int avail, boolean forgotSelf, int kwonly_given) {
            String forgotSelfMsg = forgotSelf ? ". Did you forget 'self' in the function definition?" : "";
            if (ndefaults > 0) {
                if (kwonly_given == 0) {
                    throw raise(PythonBuiltinClassType.TypeError, "%s() takes from %d to %d positional argument%s but %d %s given%s",
                                    getName(callable),
                                    co_argcount - ndefaults,
                                    co_argcount,
                                    co_argcount == 1 ? "" : "s",
                                    avail,
                                    avail == 1 ? "was" : "were",
                                    forgotSelfMsg);
                } else {
                    throw raise(PythonBuiltinClassType.TypeError, "%s() takes from %d to %d positional argument%s but %d positional argument%s (and %d keyword-only argument%s) were given%s",
                                    getName(callable),
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
                    throw raise(PythonBuiltinClassType.TypeError, "%s() takes %d positional argument%s but %d %s given%s",
                                    getName(callable),
                                    co_argcount - ndefaults,
                                    co_argcount == 1 ? "" : "s",
                                    avail,
                                    avail == 1 ? "was" : "were",
                                    forgotSelfMsg);
                } else {
                    throw raise(PythonBuiltinClassType.TypeError, "%s() takes %d positional argument%s but %d positional argument%s (and %d keyword-only argument%s) were given%s",
                                    getName(callable),
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

        public static HandleTooManyArgumentsNode create() {
            return HandleTooManyArgumentsNodeGen.create();
        }
    }

    protected abstract static class ApplyPositionalArguments extends Node {

        public abstract int execute(Object[] args_w, Object[] scope_w, int upfront, int co_argcount, int num_args);

        @Specialization(guards = "upfront < co_argcount")
        int doIt(Object[] args_w, Object[] scope_w, int upfront, int co_argcount, int num_args) {
            int take = Math.min(num_args, co_argcount - upfront);
            System.arraycopy(args_w, 0, scope_w, PArguments.USER_ARGUMENTS_OFFSET + upfront, take);
            return upfront + take;
        }

        @Specialization(guards = "upfront >= co_argcount")
        @SuppressWarnings("unused")
        int doNothing(Object[] args_w, Object[] scope_w, int upfront, int co_argcount, int num_args) {
            return upfront;
        }

        public static ApplyPositionalArguments create() {
            return ApplyPositionalArgumentsNodeGen.create();
        }
    }

    protected abstract static class FillBaseNode extends PNodeWithContext {

        protected PException raiseMissing(Object callable, String[] missingNames, int missingCnt) {
            throw raise(PythonBuiltinClassType.TypeError, "%s() missing %d required positional argument%s: %s",
                            getName(callable),
                            missingCnt,
                            missingCnt == 1 ? "" : "s",
                            String.join(",", Arrays.copyOf(missingNames, missingCnt)));
        }

        protected static boolean checkIterations(int input_argcount, int co_argcount) {
            return co_argcount - input_argcount < 32;
        }
    }

    protected abstract static class FillDefaultsNode extends FillBaseNode {

        public abstract void execute(Object callable, Signature signature, Object[] scope_w, Object[] defaults, int input_argcount, int co_argcount);

        @Specialization(guards = {"input_argcount == cachedInputArgcount", "co_argcount == cachedArgcount", "checkIterations(input_argcount, co_argcount)"})
        @ExplodeLoop
        void doCached(Object callable, Signature signature, Object[] scope_w, Object[] defaults, @SuppressWarnings("unused") int input_argcount, @SuppressWarnings("unused") int co_argcount,
                        @Cached("input_argcount") int cachedInputArgcount,
                        @Cached("co_argcount") int cachedArgcount,
                        @Cached("createBinaryProfile()") ConditionProfile missingProfile) {
            String[] missingNames = new String[cachedArgcount - cachedInputArgcount];
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
            if (missingProfile.profile(missingCnt > 0)) {
                throw raiseMissing(callable, missingNames, missingCnt);
            }
        }

        @Specialization(replaces = "doCached")
        void doUncached(Object callable, Signature signature, Object[] scope_w, Object[] defaults, int input_argcount, int co_argcount,
                        @Cached("createBinaryProfile()") ConditionProfile missingProfile) {
            String[] missingNames = new String[co_argcount - input_argcount];
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
            if (missingProfile.profile(missingCnt > 0)) {
                throw raiseMissing(callable, missingNames, missingCnt);
            }
        }

        public static FillDefaultsNode create() {
            return FillDefaultsNodeGen.create();
        }
    }

    protected abstract static class FillKwDefaultsNode extends FillBaseNode {

        @Child private FindKwDefaultNode findKwDefaultNode;

        public abstract void execute(Object callable, Object[] scope_w, Signature signature, PKeyword[] kwdefaults, int co_argcount, int co_kwonlyargcount);

        @Specialization(guards = {"co_argcount == cachedArgcount", "co_kwonlyargcount == cachedKwOnlyArgcount", "checkIterations(co_argcount, co_kwonlyargcount)"})
        @ExplodeLoop
        void doCached(Object callable, Object[] scope_w, Signature signature, PKeyword[] kwdefaults, @SuppressWarnings("unused") int co_argcount, @SuppressWarnings("unused") int co_kwonlyargcount,
                        @Cached("co_argcount") int cachedArgcount,
                        @Cached("co_kwonlyargcount") int cachedKwOnlyArgcount,
                        @Cached("createBinaryProfile()") ConditionProfile missingProfile) {
            String[] missingNames = new String[cachedKwOnlyArgcount];
            int missingCnt = 0;
            for (int i = cachedArgcount; i < cachedArgcount + cachedKwOnlyArgcount; i++) {
                if (PArguments.getArgument(scope_w, i) != null) {
                    continue;
                }

                String kwname = signature.getKeywordNames()[i - cachedArgcount];
                PKeyword kwdefault = findKwDefault(kwdefaults, kwname);
                if (kwdefault != null) {
                    PArguments.setArgument(scope_w, i, kwdefault.getValue());
                } else {
                    missingNames[missingCnt++] = kwname;
                }
            }
            if (missingProfile.profile(missingCnt > 0)) {
                throw raiseMissing(callable, missingNames, missingCnt);
            }
        }

        @Specialization(replaces = "doCached")
        void doUncached(Object callable, Object[] scope_w, Signature signature, PKeyword[] kwdefaults, int co_argcount, int co_kwonlyargcount,
                        @Cached("createBinaryProfile()") ConditionProfile missingProfile) {
            String[] missingNames = new String[co_kwonlyargcount];
            int missingCnt = 0;
            for (int i = co_argcount; i < co_argcount + co_kwonlyargcount; i++) {
                if (PArguments.getArgument(scope_w, i) != null) {
                    continue;
                }

                String kwname = signature.getKeywordNames()[i - co_argcount];
                PKeyword kwdefault = findKwDefault(kwdefaults, kwname);
                if (kwdefault != null) {
                    PArguments.setArgument(scope_w, i, kwdefault.getValue());
                } else {
                    missingNames[missingCnt++] = kwname;
                }
            }
            if (missingProfile.profile(missingCnt > 0)) {
                throw raiseMissing(callable, missingNames, missingCnt);
            }
        }

        private PKeyword findKwDefault(PKeyword[] kwdefaults, String kwname) {
            if (findKwDefaultNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                findKwDefaultNode = insert(FindKwDefaultNode.create());
            }
            return findKwDefaultNode.execute(kwdefaults, kwname);
        }

        public static FillKwDefaultsNode create() {
            return FillKwDefaultsNodeGen.create();
        }
    }

    /** finds a keyword-default value by a given name */
    protected abstract static class FindKwDefaultNode extends Node {

        public abstract PKeyword execute(PKeyword[] kwdefaults, String kwname);

        @Specialization(guards = {"kwdefaults.length == cachedLength", "kwdefaults.length < 32"})
        @ExplodeLoop
        PKeyword doCached(PKeyword[] kwdefaults, String kwname,
                        @Cached("kwdefaults.length") int cachedLength) {
            for (int j = 0; j < cachedLength; j++) {
                if (kwdefaults[j].getName().equals(kwname)) {
                    return kwdefaults[j];
                }
            }
            return null;
        }

        @Specialization(replaces = "doCached")
        PKeyword doUncached(PKeyword[] kwdefaults, String kwname) {
            for (int j = 0; j < kwdefaults.length; j++) {
                if (kwdefaults[j].getName().equals(kwname)) {
                    return kwdefaults[j];
                }
            }
            return null;
        }

        public static FindKwDefaultNode create() {
            return FindKwDefaultNodeGen.create();
        }
    }

    protected static Signature getSignatureUncached(Object callable) {
        return getProperty(callable, UncachedSignatureGetter.INSTANCE);
    }

    protected static Object[] getDefaultsUncached(Object callable) {
        return getProperty(callable, UncachedDefaultsGetter.INSTANCE);
    }

    protected static PKeyword[] getKwDefaultsUncached(Object callable) {
        return getProperty(callable, UncachedKwDefaultsGetter.INSTANCE);
    }

    protected static Object[] getDefaults(Object callable) {
        return getProperty(callable, DefaultsGetter.INSTANCE);
    }

    protected static PKeyword[] getKwDefaults(Object callable) {
        return getProperty(callable, KwDefaultsGetter.INSTANCE);
    }

    protected static String getName(Object callable) {
        return getProperty(callable, NameGetter.INSTANCE);
    }

    protected static Object getSelf(Object callable) {
        if (callable instanceof PBuiltinMethod) {
            return ((PBuiltinMethod) callable).getSelf();
        } else if (callable instanceof PMethod) {
            return ((PMethod) callable).getSelf();
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

    protected static boolean isMethodCall(Object self) {
        return !(self instanceof PythonModule);
    }

    private static <T> T getProperty(Object callable, Getter<T> getter) {
        if (callable instanceof PFunction) {
            return getter.fromPFunction((PFunction) callable);
        } else if (callable instanceof PBuiltinFunction) {
            return getter.fromPBuiltinFunction((PBuiltinFunction) callable);
        } else if (callable instanceof PBuiltinMethod) {
            Object function = ((PBuiltinMethod) callable).getFunction();
            if (function instanceof PBuiltinFunction) {
                return getter.fromPBuiltinFunction((PBuiltinFunction) function);
            } else if (function instanceof PFunction) {
                return getter.fromPFunction((PFunction) function);
            }
        } else if (callable instanceof PMethod) {
            Object function = ((PMethod) callable).getFunction();
            if (function instanceof PBuiltinFunction) {
                return getter.fromPBuiltinFunction((PBuiltinFunction) function);
            } else if (function instanceof PFunction) {
                return getter.fromPFunction((PFunction) function);
            }
        }
        CompilerDirectives.transferToInterpreter();
        throw new IllegalStateException("cannot create arguments for non-function-or-method");
    }

    private abstract static class Getter<T> {
        public abstract T fromPFunction(PFunction fun);

        public abstract T fromPBuiltinFunction(PBuiltinFunction fun);
    }

    private static final class DefaultsGetter extends Getter<Object[]> {
        private static final DefaultsGetter INSTANCE = new DefaultsGetter();

        @Override
        public Object[] fromPFunction(PFunction fun) {
            return fun.getDefaults();
        }

        @Override
        public Object[] fromPBuiltinFunction(PBuiltinFunction fun) {
            return fun.getDefaults();
        }
    }

    private static final class KwDefaultsGetter extends Getter<PKeyword[]> {
        private static final KwDefaultsGetter INSTANCE = new KwDefaultsGetter();

        @Override
        public PKeyword[] fromPFunction(PFunction fun) {
            return fun.getKwDefaults();
        }

        @Override
        public PKeyword[] fromPBuiltinFunction(PBuiltinFunction fun) {
            return fun.getKwDefaults();
        }
    }

    private static final class UncachedSignatureGetter extends Getter<Signature> {
        private static final UncachedSignatureGetter INSTANCE = new UncachedSignatureGetter();

        @Override
        public Signature fromPFunction(PFunction fun) {
            return fun.getCode().getSignature();
        }

        @Override
        public Signature fromPBuiltinFunction(PBuiltinFunction fun) {
            return fun.getSignature();
        }
    }

    private static final class UncachedDefaultsGetter extends Getter<Object[]> {
        private static final UncachedDefaultsGetter INSTANCE = new UncachedDefaultsGetter();

        @Override
        public Object[] fromPFunction(PFunction fun) {
            return fun.getUncachedDefaultValues();
        }

        @Override
        public Object[] fromPBuiltinFunction(PBuiltinFunction fun) {
            return fun.getDefaults();
        }
    }

    private static final class UncachedKwDefaultsGetter extends Getter<PKeyword[]> {
        private static final UncachedKwDefaultsGetter INSTANCE = new UncachedKwDefaultsGetter();

        @Override
        public PKeyword[] fromPFunction(PFunction fun) {
            return fun.getUncachedKwDefaults();
        }

        @Override
        public PKeyword[] fromPBuiltinFunction(PBuiltinFunction fun) {
            return fun.getKwDefaults();
        }
    }

    private static final class NameGetter extends Getter<String> {
        private static final NameGetter INSTANCE = new NameGetter();

        @Override
        public String fromPFunction(PFunction fun) {
            return fun.getName();
        }

        @Override
        public String fromPBuiltinFunction(PBuiltinFunction fun) {
            return fun.getName();
        }
    }

    public final Object[] execute(PFunction callable, Object[] userArguments) {
        return execute(callable, userArguments, PKeyword.EMPTY_KEYWORDS);
    }

    public final Object[] execute(PBuiltinFunction callable, Object[] userArguments) {
        return execute(callable, userArguments, PKeyword.EMPTY_KEYWORDS);
    }

    public final Object[] execute(PMethod callable, Object[] userArguments) {
        return execute(callable, userArguments, PKeyword.EMPTY_KEYWORDS);
    }

    public final Object[] execute(PBuiltinMethod callable, Object[] userArguments) {
        return execute(callable, userArguments, PKeyword.EMPTY_KEYWORDS);
    }

    public abstract Object[] execute(PFunction callable, Object[] userArguments, PKeyword[] keywords);

    public abstract Object[] execute(PBuiltinFunction callable, Object[] userArguments, PKeyword[] keywords);

    public abstract Object[] execute(PMethod callable, Object[] userArguments, PKeyword[] keywords);

    public abstract Object[] execute(PBuiltinMethod callable, Object[] userArguments, PKeyword[] keywords);
}
