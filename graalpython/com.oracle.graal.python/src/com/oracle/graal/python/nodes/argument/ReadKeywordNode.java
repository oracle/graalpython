/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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

import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.ValueProfile;

public abstract class ReadKeywordNode extends ReadArgumentNode {
    private final String name;
    private final ValueProfile profile = ValueProfile.createClassProfile();

    public static ReadKeywordNode create(String name) {
        return ReadKeywordNodeGen.create(name);
    }

    ReadKeywordNode(String name) {
        this.name = name;
    }

    private static PKeyword getKeyword(VirtualFrame frame, String name) {
        PKeyword[] keywordArguments = PArguments.getKeywordArguments(frame);
        for (PKeyword keyword : keywordArguments) {
            if (keyword.getName().equals(name)) {
                return keyword;
            }
        }
        return null;
    }

    @ExplodeLoop
    private static PKeyword getKeywordUnrollSafe(VirtualFrame frame, String name, int length) {
        PKeyword[] keywordArguments = PArguments.getKeywordArguments(frame);
        for (int i = 0; i < length; i++) {
            PKeyword keyword = keywordArguments[i];
            if (keyword.getName().equals(name)) {
                return keyword;
            }
        }
        return null;
    }

    protected static int getKeywordLength(VirtualFrame frame) {
        return PArguments.getKeywordArguments(frame).length;
    }

    @Specialization(guards = {"cachedLen == getKeywordLength(frame)"})
    Object cached(VirtualFrame frame,
                    @Cached("getKeywordLength(frame)") int cachedLen) {
        PKeyword keyword = getKeywordUnrollSafe(frame, name, cachedLen);
        return returnKeyword(keyword);
    }

    @Specialization
    Object uncached(VirtualFrame frame) {
        return returnKeyword(getKeyword(frame, name));
    }

    private Object returnKeyword(PKeyword keyword) {
        assert keyword != null : "a keyword was null. This cannot happen, the arity check should have taken care of raising a TypeError";
        return profile.profile(keyword.getValue());
    }

    public String getName() {
        return name;
    }
}
