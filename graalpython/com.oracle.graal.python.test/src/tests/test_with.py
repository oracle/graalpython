# Copyright (c) 2018, Oracle and/or its affiliates.
# Copyright (c) 2013-2016, Regents of the University of California
#
# All rights reserved.
#
# Redistribution and use in source and binary forms, with or without modification, are
# permitted provided that the following conditions are met:
#
# 1. Redistributions of source code must retain the above copyright notice, this list of
# conditions and the following disclaimer.
# 2. Redistributions in binary form must reproduce the above copyright notice, this list of
# conditions and the following disclaimer in the documentation and/or other materials provided
# with the distribution.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
# OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
# MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
# COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
# EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
# GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
# AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
# NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
# OF THE POSSIBILITY OF SUCH DAMAGE.

# Qunaibit 02/05/2014
# With Statement

a = 5

LOG = []

class Sample:
    def __enter__(self):
        LOG.append("__enter__")
        return self

    def __exit__(self, type, value, trace):
        LOG.append("type: %s" % type)
        LOG.append("value: %s" % value)
#         LOG.append("trace: %s" % trace) # trace back is not supported yet
        return False

    def do_something(self):
        bar = 1/0
        return bar + 10

def test_with():
    try:
        with Sample() as sample:
            a = 5
            sample.do_something()
    except ZeroDivisionError:
        LOG.append("Exception has been thrown correctly")

    else:
        LOG.append("This is not correct!!")

    finally:
        LOG.append("a = %s" % a)

    assert LOG[0] == "__enter__"
    assert LOG[1] == "type: <class 'ZeroDivisionError'>"
    assert LOG[2] == "value: division by zero"
    assert LOG[3] == "Exception has been thrown correctly"
    assert LOG[4] == "a = 5"
