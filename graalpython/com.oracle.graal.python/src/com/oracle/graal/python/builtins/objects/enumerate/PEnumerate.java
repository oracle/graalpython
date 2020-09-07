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
package com.oracle.graal.python.builtins.objects.enumerate;

import com.oracle.graal.python.builtins.objects.ints.PInt;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.profiles.ConditionProfile;

public final class PEnumerate extends PythonBuiltinObject {

    private final Object iterator;
    private long index;
    private PInt bigIndex;

    public PEnumerate(Object clazz, Shape instanceShape, Object iterator, PInt start) {
        this(clazz, instanceShape, iterator, -1);
        this.bigIndex = start;
    }

    public PEnumerate(Object clazz, Shape instanceShape, Object iterator, long start) {
        super(clazz, instanceShape);
        this.iterator = iterator;
        this.index = start;
    }

    public Object getDecoratedIterator() {
        return iterator;
    }

    public Object getAndIncrementIndex(PythonObjectFactory factory, ConditionProfile bigIntIndexProfile) {
        if (bigIntIndexProfile.profile(bigIndex != null)) {
            PInt idx = bigIndex;
            bigIndex = factory.createInt(bigIndex.inc());
            return idx;
        }
        return index++;
    }

    public Object getIndex(ConditionProfile bigIntIndexProfile) {
        if (bigIntIndexProfile.profile(bigIndex != null)) {
            return bigIndex;
        }
        return index;
    }
}
