/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates.
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
package com.oracle.graal.python.nodes.argument;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.InvalidAssumptionException;
import com.oracle.truffle.api.profiles.ValueProfile;

public abstract class ReadIndexedArgumentNode extends ReadArgumentNode {
    private final int index;
    private final ValueProfile profile = ValueProfile.createClassProfile();

    ReadIndexedArgumentNode(int index) {
        this.index = index;
    }

    public static ReadIndexedArgumentNode create(int idx) {
        return ReadIndexedArgumentNodeGen.create(idx);
    }

    @Specialization(rewriteOn = InvalidAssumptionException.class)
    Object readArg(VirtualFrame frame) throws InvalidAssumptionException {
        Object argumentAt = PArguments.getArgument(frame, index);
        if (argumentAt == null) {
            throw new InvalidAssumptionException();
        }
        return profile.profile(argumentAt);
    }

    @Specialization(replaces = "readArg")
    Object readArgOffBounds(VirtualFrame frame) {
        Object argumentAt = PArguments.getArgument(frame, index);
        if (argumentAt == null) {
            return PNone.NO_VALUE;
        }
        return profile.profile(argumentAt);
    }

    public int getIndex() {
        return index;
    }
}
