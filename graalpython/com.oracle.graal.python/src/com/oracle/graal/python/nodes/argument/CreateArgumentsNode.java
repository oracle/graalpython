/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;

@ImportStatic(PythonOptions.class)
public abstract class CreateArgumentsNode extends PNodeWithContext {
    public static CreateArgumentsNode create() {
        return CreateArgumentsNodeGen.create();
    }

    /* @formatter:off
    @Specialization(guards = {"self == cachedSelf", "userArguments.length == cachedLen"}, limit = "getVariableArgumentInlineCacheLimit()")
    @ExplodeLoop
    Object[] cached(Object self, Object[] userArguments,
                    @Cached("self == null") boolean selfIsNull,
                    @Cached("userArguments.length") int cachedLen) {
        Object[] arguments;
        int offset = 0;
        if (selfIsNull) {
            arguments = PArguments.create(cachedLen);
        } else {
            offset = 1;
            arguments = PArguments.create(cachedLen + offset);
            arguments[PArguments.USER_ARGUMENTS_OFFSET] = self;
        }
        for (int i = 0; i < cachedLen; i++) {
            arguments[PArguments.USER_ARGUMENTS_OFFSET + offset + i] = userArguments[i];
        }
        return arguments;
    }
    @formatter:on
*/
    @Specialization// (replaces = "cached")
    Object[] uncached(PythonObject callable, Object[] userArguments, PKeyword[] keywords,
                    @Cached("create()") ApplyKeywordsNode applyKeywords) {
        Arity arity = null;
        Object self = null;
        Object[] defaults = new Object[0];
        PKeyword[] kwdefaults = PKeyword.EMPTY_KEYWORDS;
        boolean methodcall = false;
        String name = null;

        // mostly we will be calling proper functions directly here,
        // but sometimes also methods that have functions directly.
        // In all other cases, the arguments

        if (callable instanceof PFunction) {
            arity = ((PFunction) callable).getArity();
            defaults = ((PFunction) callable).getDefaults();
            name = ((PFunction) callable).getName();
            kwdefaults = ((PFunction) callable).getKwDefaults();
        } else if (callable instanceof PBuiltinFunction) {
            arity = ((PBuiltinFunction) callable).getArity();
            name = ((PBuiltinFunction) callable).getName();
            defaults = ((PBuiltinFunction) callable).getDefaults();
            kwdefaults = ((PBuiltinFunction) callable).getKwDefaults();
        } else if (callable instanceof PBuiltinMethod) {
            self = ((PBuiltinMethod) callable).getSelf();
            methodcall = !(self instanceof PythonModule);
            if (((PBuiltinMethod) callable).getFunction() instanceof PBuiltinFunction) {
                arity = ((PBuiltinFunction) ((PBuiltinMethod) callable).getFunction()).getArity();
                name = ((PBuiltinFunction) ((PBuiltinMethod) callable).getFunction()).getName();
                defaults = ((PBuiltinFunction) ((PBuiltinMethod) callable).getFunction()).getDefaults();
                kwdefaults = ((PBuiltinFunction) ((PBuiltinMethod) callable).getFunction()).getKwDefaults();
            } else if (((PBuiltinMethod) callable).getFunction() instanceof PFunction) {
                arity = ((PFunction) ((PBuiltinMethod) callable).getFunction()).getArity();
                defaults = ((PFunction) ((PBuiltinMethod) callable).getFunction()).getDefaults();
                name = ((PFunction) ((PBuiltinMethod) callable).getFunction()).getName();
                kwdefaults = ((PFunction) ((PBuiltinMethod) callable).getFunction()).getKwDefaults();
            }
        } else if (callable instanceof PMethod) {
            self = ((PMethod) callable).getSelf();
            methodcall = !(self instanceof PythonModule);
            if (((PMethod) callable).getFunction() instanceof PBuiltinFunction) {
                arity = ((PBuiltinFunction) ((PMethod) callable).getFunction()).getArity();
                name = ((PBuiltinFunction) ((PMethod) callable).getFunction()).getName();
                defaults = ((PBuiltinFunction) ((PMethod) callable).getFunction()).getDefaults();
                kwdefaults = ((PBuiltinFunction) ((PMethod) callable).getFunction()).getKwDefaults();
            } else if (((PMethod) callable).getFunction() instanceof PFunction) {
                arity = ((PFunction) ((PMethod) callable).getFunction()).getArity();
                defaults = ((PFunction) ((PMethod) callable).getFunction()).getDefaults();
                name = ((PFunction) ((PMethod) callable).getFunction()).getName();
                kwdefaults = ((PFunction) ((PMethod) callable).getFunction()).getKwDefaults();
            }
        } else {
            throw new IllegalStateException("cannot create arguments for non-function-or-method");
        }

        // see PyPy's Argument#_match_signature method
        int co_argcount = arity.getMaxNumOfPositionalArgs(); // expected formal arguments, without
                                                             // */**
        int co_kwonlyargcount = arity.getNumOfRequiredKeywords();
        boolean too_many_args = false;

        Object[] scope_w = PArguments.create(Math.max(userArguments.length, arity.getMaxNumOfPositionalArgs()));

        // put the special w_firstarg into the scope, if it exists
        int upfront = 0;
        if (self != null) {
            upfront = 1;
            if (co_argcount > 0) {
                PArguments.setArgument(scope_w, 0, self);
            }
        }

        Object[] args_w = userArguments;
        int num_args = args_w.length;
        int avail = num_args + upfront;

        // put as many positional input arguments into place as available
        int input_argcount = upfront;
        if (input_argcount < co_argcount) {
            int take = Math.min(num_args, co_argcount - upfront);

            // letting the JIT unroll this loop is safe, because take is always
            // smaller than co_argcount
            for (int i = 0; i < take; i++) {
                PArguments.setArgument(scope_w, i + input_argcount, args_w[i]);
            }
            input_argcount += take;
        }

        // collect extra positional arguments into the *vararg
        if (arity.takesVarArgs()) {
            // we collect the varargs on the caller site
            // PyPy, however, collects them here and puts them in a tuple.
            // TODO: (tfel) (args) consider if we also might want this.
            int args_left = co_argcount - upfront;
            if (args_left < 0) {
                // everything goes to starargs, we already put self in position 0
                // above
                for (int j = 0; j < args_w.length; j++) {
                    PArguments.setArgument(scope_w, upfront + j, args_w[j]);
                }
            } else if (num_args > args_left) {
                for (int j = args_left; j < args_w.length; j++) {
                    PArguments.setArgument(scope_w, j, args_w[j]);
                }
            } else {
                // no starargs
            }
        } else if (avail > co_argcount) {
            too_many_args = true;
        }

        // handle keyword arguments
        // match the keywords given at the call site to the argument names
        // the called node takes and collect the rest in the keywords
        // this also does the first check for for missing positional arguments and fill them from
        // the kwds.
        if (keywords.length > 0) {
            scope_w = applyKeywords.execute(name, arity, scope_w, keywords);
        }

        boolean more_filling = input_argcount < co_argcount || keywords.length < co_kwonlyargcount;
        int firstDefaultArgIdx = 0;
        if (more_filling) {
            firstDefaultArgIdx = co_argcount - (defaults == null ? 0 : defaults.length);
            // pypy fills more arguments from keywords here, but we've already done that in the
            // apply keywords node
        }

        if (too_many_args) {
            int kwonly_given = 0;
            for (int i = co_argcount; i < co_argcount + co_kwonlyargcount; i++) {
                if (PArguments.getArgument(scope_w, i) != null) {
                    kwonly_given += 1;
                }
            }

            if (defaults != null && defaults.length > 0) {
                if (kwonly_given == 0) {
                    throw raise(PythonBuiltinClassType.TypeError, "%s() takes from %d to %d positional arguments but %d %s given%s",
                                    name,
                                    co_argcount - defaults.length,
                                    co_argcount,
                                    avail,
                                    avail == 1 ? "was" : "were",
                                    (methodcall && avail + 1 == co_argcount && (arity.getParameterIds().length == 0 || !arity.getParameterIds()[0].equals("self")))
                                                    ? ". Did you forget 'self' in the function definition?"
                                                    : "");
                } else {
                    throw raise(PythonBuiltinClassType.TypeError, "%s() takes from %d to %d positional arguments but %d positional argument%s (and %d keyword-only argument%s) were given%s",
                                    name,
                                    co_argcount - defaults.length,
                                    co_argcount,
                                    avail,
                                    avail == 1 ? "" : "s",
                                    kwonly_given,
                                    kwonly_given == 1 ? "" : "s",
                                    (methodcall && avail + 1 == co_argcount && (arity.getParameterIds().length == 0 || !arity.getParameterIds()[0].equals("self")))
                                                    ? ". Did you forget 'self' in the function definition?"
                                                    : "");
                }
            } else {
                if (kwonly_given == 0) {
                    throw raise(PythonBuiltinClassType.TypeError, "%s() takes from %d positional argument%s but %d %s given%s",
                                    name,
                                    co_argcount,
                                    co_argcount == 1 ? "" : "s",
                                    avail,
                                    avail == 1 ? "was" : "were",
                                    (methodcall && avail + 1 == co_argcount && (arity.getParameterIds().length == 0 || !arity.getParameterIds()[0].equals("self")))
                                                    ? ". Did you forget 'self' in the function definition?"
                                                    : "");
                } else {
                    throw raise(PythonBuiltinClassType.TypeError, "%s() takes %d positional argument%s but %d positional argument%s (and %d keyword-only argument%s) were given%s",
                                    name,
                                    co_argcount,
                                    co_argcount == 1 ? "" : "s",
                                    avail,
                                    avail == 1 ? "" : "s",
                                    kwonly_given,
                                    kwonly_given == 1 ? "" : "s",
                                    (methodcall && avail + 1 == co_argcount && (arity.getParameterIds().length == 0 || !arity.getParameterIds()[0].equals("self")))
                                                    ? ". Did you forget 'self' in the function definition?"
                                                    : "");
                }
            }
        }

        if (more_filling) {
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
                                name,
                                missingCnt,
                                missingCnt == 1 ? "" : "s",
                                String.join(",", Arrays.copyOf(missingNames, missingCnt)));
            }

            // finally, fill kwonly arguments with w_kw_defs (if needed)
            PKeyword[] givenKwds = PArguments.getKeywordArguments(scope_w);
            String[] kwOnlyNames = arity.getKeywordNames();
            kwnames: for (String kwname : kwOnlyNames) {
                for (int j = 0; j < givenKwds.length; j++) {
                    if (givenKwds[j].getName().equals(kwname)) {
                        continue kwnames; // we have it
                    }
                }
                for (int j = 0; j < kwdefaults.length; j++) {
                    if (kwdefaults[j].getName().equals(kwname)) {
                        givenKwds = Arrays.copyOf(givenKwds, givenKwds.length + 1);
                        givenKwds[givenKwds.length - 1] = kwdefaults[j];
                        continue kwnames;
                    }
                }
                missingNames[missingCnt++] = kwname;
            }

            PArguments.setKeywordArguments(scope_w, givenKwds);

            if (missingCnt > 0) {
                throw raise(PythonBuiltinClassType.TypeError, "%s() missing %d required keyword-only argument%s: %s",
                                name,
                                missingCnt,
                                missingCnt == 1 ? "" : "s",
                                String.join(",", Arrays.copyOf(missingNames, missingCnt)));
            }
        }

        return scope_w;
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
