# Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# The Universal Permissive License (UPL), Version 1.0
#
# Subject to the condition set forth below, permission is hereby granted to any
# person obtaining a copy of this software, associated documentation and/or
# data (collectively the "Software"), free of charge and under any and all
# copyright rights in the Software, and any and all patent rights owned or
# freely licensable by each licensor hereunder covering either (i) the
# unmodified Software as contributed to or provided by such licensor, or (ii)
# the Larger Works (as defined below), to deal in both
#
# (a) the Software, and
#
# (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
# one is included with the Software each a "Larger Work" to which the Software
# is contributed by such licensors),
#
# without restriction, including without limitation the rights to copy, create
# derivative works of, display, perform, and distribute the Software and make,
# use, sell, offer for sale, import, export, have made, and have sold the
# Software and the Larger Work(s), and to sublicense the foregoing rights on
# either these or other terms.
#
# This license is subject to the following condition:
#
# The above copyright notice and either this complete permission notice or at a
# minimum a reference to the UPL must be included in all copies or substantial
# portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.

import polyglot
import java

try:
    java.type("org.apache.arrow.vector.BaseFixedWidthVector")
except KeyError:
    raise ImportError("It is not possible to import Apache Arrow Vector classes because arrow-vector package is not on the class path. Please add this library to your project.")

if not __graalpython__.host_import_enabled:
    raise NotImplementedError("Host lookup is not allowed. You can allow it while building python context.")
else:
    class TinyIntVector:

        def __len__(self):
            return self.getValueCount()

        def __arrow_c_array__(self, requested_schema=None):
            return __graalpython__.export_arrow_vector(self)


    class SmallIntVector:

        def __len__(self):
            return self.getValueCount()

        def __arrow_c_array__(self, requested_schema=None):
            return __graalpython__.export_arrow_vector(self)


    class IntVector:

        def __len__(self):
            return self.getValueCount()

        def __arrow_c_array__(self, requested_schema=None):
            return __graalpython__.export_arrow_vector(self)


    class BigIntVector:

        def __len__(self):
            return self.getValueCount()

        def __arrow_c_array__(self, requested_schema=None):
            return __graalpython__.export_arrow_vector(self)


    class BitVector:

        def __len__(self):
            return self.getValueCount()

        def __arrow_c_array__(self, requested_schema=None):
            return __graalpython__.export_arrow_vector(self)


    class Float2Vector:

        def __len__(self):
            return self.getValueCount()

        def __arrow_c_array__(self, requested_schema=None):
            return __graalpython__.export_arrow_vector(self)


    class Float4Vector:

        def __len__(self):
            return self.getValueCount()

        def __arrow_c_array__(self, requested_schema=None):
            return __graalpython__.export_arrow_vector(self)


    class Float8Vector:

        def __len__(self):
            return self.getValueCount()

        def __arrow_c_array__(self, requested_schema=None):
            return __graalpython__.export_arrow_vector(self)


    # Ints
    int8_vector_class_path = java.type("org.apache.arrow.vector.TinyIntVector")
    int16_vector_class_path = java.type("org.apache.arrow.vector.SmallIntVector")
    int32_vector_class_path = java.type("org.apache.arrow.vector.IntVector")
    int64_vector_class_path = java.type("org.apache.arrow.vector.BigIntVector")
    # Boolean
    boolean_vector_class_path = java.type("org.apache.arrow.vector.BitVector")
    # Floats
    float2_vector_class_path = java.type("org.apache.arrow.vector.Float2Vector")
    float4_vector_class_path = java.type("org.apache.arrow.vector.Float4Vector")
    float8_vector_class_path = java.type("org.apache.arrow.vector.Float8Vector")

    polyglot.register_interop_type(int8_vector_class_path, TinyIntVector)
    polyglot.register_interop_type(int16_vector_class_path, SmallIntVector)
    polyglot.register_interop_type(int32_vector_class_path, IntVector)
    polyglot.register_interop_type(int64_vector_class_path, BigIntVector)

    polyglot.register_interop_type(boolean_vector_class_path, BitVector)

    polyglot.register_interop_type(float2_vector_class_path, Float2Vector)
    polyglot.register_interop_type(float4_vector_class_path, Float4Vector)
    polyglot.register_interop_type(float8_vector_class_path, Float8Vector)