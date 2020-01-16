/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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
package com.oracle.graal.python.builtins;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface Builtin {

    String name() default "";

    String doc() default "";

    PythonBuiltinClassType[] constructsClass() default {};

    PythonBuiltinClassType[] base() default {};

    int minNumOfPositionalArgs() default 0;

    int maxNumOfPositionalArgs() default -1;

    boolean isGetter() default false;

    boolean isSetter() default false;

    boolean takesVarArgs() default false;

    boolean varArgsMarker() default false;

    boolean takesVarKeywordArgs() default false;

    String[] parameterNames() default {};

    String[] keywordOnlyNames() default {};

    boolean isPublic() default true;

    boolean isClassmethod() default false;

    boolean isStaticmethod() default false;

    /**
     * Some built-ins don't ever need the frame. This should be set to false for builtins that do
     * not access the frame and only pass it to nodes that can deal with the frame being
     * {@code null}.
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
}
