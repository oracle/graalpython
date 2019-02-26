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
import com.oracle.graal.python.builtins.objects.function.Arity;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PBuiltinFunction;
import com.oracle.graal.python.builtins.objects.function.PFunction;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.builtins.objects.method.PBuiltinMethod;
import com.oracle.graal.python.builtins.objects.method.PMethod;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.argument.CreateArgumentsNodeGen.ApplyPositionalArgumentsNodeGen;
import com.oracle.graal.python.nodes.argument.CreateArgumentsNodeGen.CreateAndCheckArgumentsNodeGen;
import com.oracle.graal.python.nodes.argument.CreateArgumentsNodeGen.HandleTooManyArgumentsNodeGen;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;

@ImportStatic({PythonOptions.class, PGuards.class})
public abstract class CreateArgumentsNode extends PNodeWithContext {
    public static CreateArgumentsNode create() {
        return CreateArgumentsNodeGen.create();
    }

    @Specialization(guards = {"isMethod(method)", "method == cachedMethod"}, limit = "getVariableArgumentInlineCacheLimit()")
    @ExplodeLoop
    Object[] doMethodCached(PythonObject method, Object[] userArguments, PKeyword[] keywords,
                    @Cached("create()") CreateAndCheckArgumentsNode createAndCheckArgumentsNode,
                    @Cached("method") @SuppressWarnings("unused") PythonObject cachedMethod) {

        // We do not directly cache these objects because they are compilation final anyway and the
        // getter check the appropriate assumptions.
        Arity arity = getArity(cachedMethod);
        Object[] defaults = getDefaults(cachedMethod);
        PKeyword[] kwdefaults = getKwDefaults(cachedMethod);
        Object self = getSelf(cachedMethod);
        return createAndCheckArgumentsNode.execute(method, userArguments, keywords, arity, self, defaults, kwdefaults, isMethodCall(self));
    }

    @Specialization(guards = {"isMethod(method)", "getFunction(method) == cachedFunction",
                    "getSelf(method) == cachedSelf"}, limit = "getVariableArgumentInlineCacheLimit()", replaces = "doMethodCached")
    @ExplodeLoop
    Object[] doMethodFunctionCached(PythonObject method, Object[] userArguments, PKeyword[] keywords,
                    @Cached("create()") CreateAndCheckArgumentsNode createAndCheckArgumentsNode,
                    @Cached("getFunction(method)") @SuppressWarnings("unused") Object cachedFunction,
                    @Cached("getSelf(method)") Object cachedSelf) {

        // We do not directly cache these objects because they are compilation final anyway and the
        // getter check the appropriate assumptions.
        Arity arity = getArity(cachedFunction);
        Object[] defaults = getDefaults(cachedFunction);
        PKeyword[] kwdefaults = getKwDefaults(cachedFunction);
        return createAndCheckArgumentsNode.execute(method, userArguments, keywords, arity, cachedSelf, defaults, kwdefaults, isMethodCall(cachedSelf));
    }

    @Specialization(guards = {"isFunction(callable)", "callable == cachedCallable"}, limit = "getVariableArgumentInlineCacheLimit()")
    @ExplodeLoop
    Object[] doFunctionCached(PythonObject callable, Object[] userArguments, PKeyword[] keywords,
                    @Cached("create()") CreateAndCheckArgumentsNode createAndCheckArgumentsNode,
                    @Cached("callable") @SuppressWarnings("unused") PythonObject cachedCallable) {

        // We do not directly cache these objects because they are compilation final anyway and the
        // getter check the appropriate assumptions.
        Arity arity = getArity(cachedCallable);
        Object[] defaults = getDefaults(cachedCallable);
        PKeyword[] kwdefaults = getKwDefaults(cachedCallable);
        return createAndCheckArgumentsNode.execute(callable, userArguments, keywords, arity, null, defaults, kwdefaults, false);
    }

    @Specialization(replaces = {"doFunctionCached", "doMethodCached", "doMethodFunctionCached"})
    Object[] uncached(PythonObject callable, Object[] userArguments, PKeyword[] keywords,
                    @Cached("create()") CreateAndCheckArgumentsNode createAndCheckArgumentsNode) {

        // mostly we will be calling proper functions directly here,
        // but sometimes also methods that have functions directly.
        // In all other cases, the arguments

        Arity arity = getArity(callable);
        Object[] defaults = getDefaults(callable);
        PKeyword[] kwdefaults = getKwDefaults(callable);
        Object self = getSelf(callable);
        boolean methodcall = !(self instanceof PythonModule);

        return createAndCheckArgumentsNode.execute(callable, userArguments, keywords, arity, self, defaults, kwdefaults, methodcall);
    }

    protected abstract static class CreateAndCheckArgumentsNode extends PNodeWithContext {

        @Child private ApplyKeywordsNode applyKeywords;
        @Child private HandleTooManyArgumentsNode handleTooManyArgumentsNode;
        @Child private ApplyPositionalArguments applyPositional = ApplyPositionalArguments.create();

        public static CreateAndCheckArgumentsNode create() {
            return CreateAndCheckArgumentsNodeGen.create();
        }

        public abstract Object[] execute(PythonObject callable, Object[] userArguments, PKeyword[] keywords, Arity arity, Object self, Object[] defaults, PKeyword[] kwdefaults, boolean methodcall);

        @Specialization(guards = {"userArguments.length == cachedLength", "arity.getMaxNumOfPositionalArgs() == cachedMaxPos", "arity.getNumOfRequiredKeywords() == cachedNumKwds"})
        Object[] doCached0(PythonObject callable, Object[] userArguments, PKeyword[] keywords, Arity arity, Object self, Object[] defaults, PKeyword[] kwdefaults,
                        boolean methodcall,
                        @Cached("userArguments.length") int cachedLength,
                        @Cached("arity.getMaxNumOfPositionalArgs()") int cachedMaxPos,
                        @Cached("arity.getNumOfRequiredKeywords()") int cachedNumKwds) {

            return createAndCheckArguments(callable, userArguments, cachedLength, keywords, arity, self, defaults, kwdefaults, methodcall, cachedMaxPos, cachedNumKwds);
        }

        @Specialization(guards = "userArguments.length == cachedLength", replaces = "doCached0")
        Object[] doCached(PythonObject callable, Object[] userArguments, PKeyword[] keywords, Arity arity, Object self, Object[] defaults, PKeyword[] kwdefaults,
                        boolean methodcall,
                        @Cached("userArguments.length") int cachedLength) {

            return createAndCheckArguments(callable, userArguments, cachedLength, keywords, arity, self, defaults, kwdefaults, methodcall, arity.getMaxNumOfPositionalArgs(),
                            arity.getNumOfRequiredKeywords());
        }

        @Specialization(replaces = "doCached")
        Object[] doUncached(PythonObject callable, Object[] userArguments, PKeyword[] keywords, Arity arity, Object self, Object[] defaults, PKeyword[] kwdefaults, boolean methodcall) {
            return createAndCheckArguments(callable, userArguments, userArguments.length, keywords, arity, self, defaults, kwdefaults, methodcall, arity.getMaxNumOfPositionalArgs(),
                            arity.getNumOfRequiredKeywords());
        }

        private Object[] createAndCheckArguments(PythonObject callable, Object[] args_w, int num_args, PKeyword[] keywords, Arity arity, Object self, Object[] defaults, PKeyword[] kwdefaults,
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
            if (arity.takesVarArgs()) {
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
                applyKeywords(callable, arity, scope_w, keywords);
            }

            if (too_many_args) {
                throw handleTooManyArguments(scope_w, callable, arity, co_argcount, co_kwonlyargcount, defaults.length, avail, methodcall);
            }

            boolean more_filling = input_argcount < co_argcount + co_kwonlyargcount;
            if (more_filling) {
                int firstDefaultArgIdx = co_argcount - defaults.length;

                int missingCnt = 0;
                String[] missingNames = new String[co_argcount + co_kwonlyargcount];

                // then, fill the normal arguments with defaults_w (if needed)
                for (int i = input_argcount; i < co_argcount; i++) {
                    if (PArguments.getArgument(scope_w, i) != null) {
                        continue;
                    }
                    int defnum = i - firstDefaultArgIdx;
                    if (defnum >= 0) {
                        PArguments.setArgument(scope_w, i, defaults[defnum]);
                    } else {
                        missingNames[missingCnt++] = arity.getParameterIds()[i];
                    }
                }

                if (missingCnt > 0) {
                    throw raise(PythonBuiltinClassType.TypeError, "%s() missing %d required positional argument%s: %s",
                                    getName(callable),
                                    missingCnt,
                                    missingCnt == 1 ? "" : "s",
                                    String.join(",", Arrays.copyOf(missingNames, missingCnt)));
                }

                // finally, fill kwonly arguments with w_kw_defs (if needed)
                kwnames: for (int i = co_argcount; i < co_argcount + co_kwonlyargcount; i++) {
                    if (PArguments.getArgument(scope_w, i) != null) {
                        continue;
                    }
                    String kwname = arity.getKeywordNames()[i - co_argcount];
                    for (int j = 0; j < kwdefaults.length; j++) {
                        if (kwdefaults[j].getName().equals(kwname)) {
                            PArguments.setArgument(scope_w, i, kwdefaults[j].getValue());
                            continue kwnames;
                        }
                    }
                    missingNames[missingCnt++] = kwname;
                }

                if (missingCnt > 0) {
                    throw raise(PythonBuiltinClassType.TypeError, "%s() missing %d required keyword-only argument%s: %s",
                                    getName(callable),
                                    missingCnt,
                                    missingCnt == 1 ? "" : "s",
                                    String.join(",", Arrays.copyOf(missingNames, missingCnt)));
                }
            }

            return scope_w;
        }

        private void applyKeywords(Object callable, Arity arity, Object[] scope_w, PKeyword[] keywords) {
            if (applyKeywords == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                applyKeywords = insert(ApplyKeywordsNode.create());
            }
            applyKeywords.execute(callable, arity, scope_w, keywords);
        }

        private PException handleTooManyArguments(Object[] scope_w, Object callable, Arity arity, int co_argcount, int co_kwonlyargcount, int ndefaults, int avail, boolean methodcall) {
            if (handleTooManyArgumentsNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                handleTooManyArgumentsNode = insert(HandleTooManyArgumentsNode.create());
            }
            return handleTooManyArgumentsNode.execute(scope_w, callable, arity, co_argcount, co_kwonlyargcount, ndefaults, avail, methodcall);
        }

    }

    protected abstract static class HandleTooManyArgumentsNode extends PNodeWithContext {

        public abstract PException execute(Object[] scope_w, Object callable, Arity arity, int co_argcount, int co_kwonlyargcount, int ndefaults, int avail, boolean methodcall);

        @Specialization(guards = {"co_kwonlyargcount == cachedKwOnlyArgCount"})
        @ExplodeLoop
        PException doCached(Object[] scope_w, Object callable, Arity arity, int co_argcount, @SuppressWarnings("unused") int co_kwonlyargcount, int ndefaults, int avail,
                        boolean methodcall,
                        @Cached("co_kwonlyargcount") int cachedKwOnlyArgCount) {
            int kwonly_given = 0;
            for (int i = 0; i < cachedKwOnlyArgCount; i++) {
                if (PArguments.getArgument(scope_w, co_argcount + i) != null) {
                    kwonly_given += 1;
                }
            }

            boolean forgotSelf = methodcall && avail + 1 == co_argcount && (arity.getParameterIds().length == 0 || !arity.getParameterIds()[0].equals("self"));
            throw raiseTooManyArguments(callable, co_argcount, ndefaults, avail, forgotSelf, kwonly_given);
        }

        @Specialization(replaces = "doCached")
        PException doUncached(Object[] scope_w, Object callable, Arity arity, int co_argcount, int co_kwonlyargcount, int ndefaults, int avail, boolean methodcall) {
            int kwonly_given = 0;
            for (int i = 0; i < co_kwonlyargcount; i++) {
                if (PArguments.getArgument(scope_w, co_argcount + i) != null) {
                    kwonly_given += 1;
                }
            }

            boolean forgotSelf = methodcall && avail + 1 == co_argcount && (arity.getParameterIds().length == 0 || !arity.getParameterIds()[0].equals("self"));
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

    protected static Arity getArity(Object callable) {
        return getProperty(callable, ArityGetter.INSTANCE);
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

    private static final class ArityGetter extends Getter<Arity> {
        private static final ArityGetter INSTANCE = new ArityGetter();

        @Override
        public Arity fromPFunction(PFunction fun) {
            return fun.getArity();
        }

        @Override
        public Arity fromPBuiltinFunction(PBuiltinFunction fun) {
            return fun.getArity();
        }
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
