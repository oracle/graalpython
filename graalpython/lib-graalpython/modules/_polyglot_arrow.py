# Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
import gc
import atexit

try:
    java.type("org.apache.arrow.vector.BaseFixedWidthVector")
except KeyError:
    raise ImportError(
        "It is not possible to import Apache Arrow Vector classes because arrow-vector package is not on the class path. Please add this library to your project.")

if not __graalpython__.host_import_enabled:
    raise NotImplementedError("Host lookup is not allowed. You can allow it while building python context.")


class TinyIntVector:

    def __len__(self):
        return self.getValueCount()

    def __arrow_c_array__(self, requested_schema=None):
        return Data.export_vector(self)


class SmallIntVector:

    def __len__(self):
        return self.getValueCount()

    def __arrow_c_array__(self, requested_schema=None):
        return Data.export_vector(self)


class IntVector:

    def __len__(self):
        return self.getValueCount()

    def __arrow_c_array__(self, requested_schema=None):
        return Data.export_vector(self)


class BigIntVector:

    def __len__(self):
        return self.getValueCount()

    def __arrow_c_array__(self, requested_schema=None):
        return Data.export_vector(self)


class BitVector:

    def __len__(self):
        return self.getValueCount()

    def __arrow_c_array__(self, requested_schema=None):
        return Data.export_vector(self)


class Float2Vector:

    def __len__(self):
        return self.getValueCount()

    def __arrow_c_array__(self, requested_schema=None):
        return Data.export_vector(self)


class Float4Vector:

    def __len__(self):
        return self.getValueCount()

    def __arrow_c_array__(self, requested_schema=None):
        return Data.export_vector(self)


class Float8Vector:

    def __len__(self):
        return self.getValueCount()

    def __arrow_c_array__(self, requested_schema=None):
        return Data.export_vector(self)

class Table:

    def __arrow_c_array__(self, requested_schema=None):
        return Data.export_table(self)

class ArrowArray:
    _jarrow_array_class = java.type("org.apache.arrow.c.ArrowArray")
    _graalpy_arrow_array_class = java.type("com.oracle.graal.python.nodes.arrow.ArrowArray")

    @staticmethod
    def allocate_new(allocator):
        return ArrowArray._jarrow_array_class.allocateNew(allocator)

    @staticmethod
    def transfer_to_managed(arrow_array):
        snapshot = arrow_array.snapshot()
        managed_arrow_array = ArrowArray._graalpy_arrow_array_class.allocate(
            snapshot.length,
            snapshot.null_count,
            snapshot.offset,
            snapshot.n_buffers,
            snapshot.n_children,
            snapshot.buffers,
            snapshot.children,
            snapshot.dictionary,
            snapshot.release,
            snapshot.private_data
        )

        arrow_array.close()
        return managed_arrow_array


class ArrowSchema:
    _jarrow_schema_class = java.type("org.apache.arrow.c.ArrowSchema")
    _graalpy_arrow_schema_class = java.type("com.oracle.graal.python.nodes.arrow.ArrowSchema")

    @staticmethod
    def allocate_new(allocator):
        return ArrowSchema._jarrow_schema_class.allocateNew(allocator)

    @staticmethod
    def transfer_to_managed(arrow_schema):
        snapshot = arrow_schema.snapshot()
        managed_arrow_schema = ArrowSchema._graalpy_arrow_schema_class.allocate(
            snapshot.format,
            snapshot.name,
            snapshot.metadata,
            snapshot.flags,
            snapshot.n_children,
            snapshot.children,
            snapshot.dictionary,
            snapshot.release,
            snapshot.private_data
        )
        arrow_schema.close()
        return managed_arrow_schema


class Data:
    _jdata_class = java.type("org.apache.arrow.c.Data")

    @staticmethod
    def export_table(table: Table):
        vector_schema_root = table.toVectorSchemaRoot()
        allocator = vector_schema_root.getFieldVectors().getFirst().getAllocator().getRoot()
        arrow_array = ArrowArray.allocate_new(allocator)
        arrow_schema = ArrowSchema.allocate_new(allocator)
        Data._jdata_class.exportVectorSchemaRoot(allocator, vector_schema_root, None, arrow_array, arrow_schema)
        vector_schema_root.close()
        managed_arrow_array = ArrowArray.transfer_to_managed(arrow_array)
        managed_arrow_schema = ArrowSchema.transfer_to_managed(arrow_schema)

        return __graalpython__.create_arrow_py_capsule(managed_arrow_array.memoryAddress(), managed_arrow_schema.memoryAddress())

    @staticmethod
    def export_vector(vector):
        allocator = vector.getAllocator()
        arrow_array = ArrowArray.allocate_new(allocator)
        arrow_schema = ArrowSchema.allocate_new(allocator)

        Data._jdata_class.exportVector(allocator, vector, None, arrow_array, arrow_schema)
        managed_arrow_array = ArrowArray.transfer_to_managed(arrow_array)
        managed_arrow_schema = ArrowSchema.transfer_to_managed(arrow_schema)

        return __graalpython__.create_arrow_py_capsule(managed_arrow_array.memoryAddress(), managed_arrow_schema.memoryAddress())


__enabled_java_integration = False


def enable_java_integration(allow_method_overwrites: bool = False):
    """
    This method enables passing Java Apache Arrow vector classes directly to Python code without copying any memory.
    It basically calls `polyglot.register_interop_type` on every vector class defined in the library.
    Calling the method more than once has no effect.

    If allow_method_overwrites=True, defining the same method is explicitly allowed.
    """
    global __enabled_java_integration
    if not __enabled_java_integration:
        __enabled_java_integration = True
        # Ints
        int8_vector_class = java.type("org.apache.arrow.vector.TinyIntVector")
        int16_vector_class = java.type("org.apache.arrow.vector.SmallIntVector")
        int32_vector_class = java.type("org.apache.arrow.vector.IntVector")
        int64_vector_class = java.type("org.apache.arrow.vector.BigIntVector")
        # Boolean
        boolean_vector_class = java.type("org.apache.arrow.vector.BitVector")
        # Floats
        float2_vector_class = java.type("org.apache.arrow.vector.Float2Vector")
        float4_vector_class = java.type("org.apache.arrow.vector.Float4Vector")
        float8_vector_class = java.type("org.apache.arrow.vector.Float8Vector")
        # Table
        table_class = java.type("org.apache.arrow.vector.table.Table")

        polyglot.register_interop_type(int8_vector_class, TinyIntVector,
                                       allow_method_overwrites=allow_method_overwrites)
        polyglot.register_interop_type(int16_vector_class, SmallIntVector,
                                       allow_method_overwrites=allow_method_overwrites)
        polyglot.register_interop_type(int32_vector_class, IntVector,
                                       allow_method_overwrites=allow_method_overwrites)
        polyglot.register_interop_type(int64_vector_class, BigIntVector,
                                       allow_method_overwrites=allow_method_overwrites)
        polyglot.register_interop_type(boolean_vector_class, BitVector,
                                       allow_method_overwrites=allow_method_overwrites)
        polyglot.register_interop_type(float2_vector_class, Float2Vector,
                                       allow_method_overwrites=allow_method_overwrites)
        polyglot.register_interop_type(float4_vector_class, Float4Vector,
                                       allow_method_overwrites=allow_method_overwrites)
        polyglot.register_interop_type(float8_vector_class, Float8Vector,
                                       allow_method_overwrites=allow_method_overwrites)
        polyglot.register_interop_type(table_class, Table, allow_method_overwrites=allow_method_overwrites)



        def force_gc():
            gc.collect()
            gc.collect()
            gc.collect()

        atexit.register(force_gc)
