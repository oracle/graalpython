/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
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
package com.oracle.graal.python.annotations;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@Repeatable(value = Builtins.class)
public @interface Builtin {

    String name() default "";

    String doc() default "";

    /**
     * Most builtins are not OS specific. If specified, the builtin is included only if the os
     * matches
     */
    PythonOS os() default PythonOS.PLATFORM_ANY;

    int minNumOfPositionalArgs() default 0;

    int maxNumOfPositionalArgs() default -1;

    int numOfPositionalOnlyArgs() default -1;

    boolean isGetter() default false;

    boolean isSetter() default false;

    boolean allowsDelete() default false;

    boolean takesVarArgs() default false;

    boolean takesVarKeywordArgs() default false;

    String[] parameterNames() default {};

    String[] keywordOnlyNames() default {};

    boolean isClassmethod() default false;

    boolean isStaticmethod() default false;

    /**
     * Most built-ins don't ever need the frame or they should be able to deal with receiving a
     * {@code null} frame. This should be set to {@code true} for those builtins that do need a full
     * frame.
     */
    boolean needsFrame() default false;

    /**
     * By default the caller frame bit is set on-demand, but for some builtins it might be useful to
     * always force passing the caller frame.
     */
    boolean alwaysNeedsCallerFrame() default false;

    /**
     * Module functions should be bound to their module, meaning they would take the module itself
     * as "self" parameter. We omit this by default, but if the builtin does explicitly specify the
     * self argument, set this to true.
     */
    boolean declaresExplicitSelf() default false;

    String raiseErrorName() default "";

    boolean forceSplitDirectCalls() default false;

    /**
     * If set to {@code true}, then this builtin will be initialized and registered in
     * PythonBuiltins#initialize. Otherwise, it will be ignored even when it has a generated node
     * factory. This is useful when we want to initialize the builtin manually in different way
     * (e.g., wrap it in method, descriptor, ...). By convention set this to {@code false} also for
     * builtins declared outside of PythonBuiltins subclass to document the intent to not initialize
     * them automatically.
     */
    boolean autoRegister() default true;
}
