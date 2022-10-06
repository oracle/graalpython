# Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
import re
import sys
from typing import List, Tuple, NamedTuple, Union, Optional, Iterable

from asdl import asdl

NAME_OVERRIDES = {
    'argtypes': 'argTypes',
    'asname': 'asName',
    'boolop': 'BoolOpTy',
    'cmpop': 'CmpOpTy',
    'ctx': 'context',
    'elt': 'element',
    'elts': 'elements',
    'excepthandler': 'ExceptHandlerTy',
    'finalbody': 'finalBody',
    'kwonlyargs': 'kwOnlyArgs',
    'kwarg': 'kwArg',
    'lineno': 'lineNo',
    'orelse': 'orElse',
    'posonlyargs': 'posOnlyArgs',
    'simple': 'isSimple',
    'unaryop': 'UnaryOpTy',
    'vararg': 'varArg',
    'withitem': 'WithItemTy',
}


class Name(NamedTuple):
    python: str
    java: str
    ts_prefix: str

    @property
    def ts_literal(self):
        """The name for the static final TruffleString field."""
        return f'T_{self.ts_prefix}_{self.python.upper()}'

    @property
    def ts_literal_qn(self):
        """The name for the static final TruffleString field with the 'AstState.' prefix."""
        return f'AstState.{self.ts_literal}'

    @property
    def cls_field(self):
        """The name for the static final PythonClass field."""
        return f'cls{self.java}'

    @property
    def singleton_field(self):
        """The name for the static final PythonObject field for enum constants."""
        return f'singleton{self.java}'


class Field(NamedTuple):
    name: Name
    type: Name
    is_optional: bool
    is_sequence: bool
    doc: str

    @property
    def is_nullable(self):
        return self.is_optional or self.is_sequence

    @property
    def convertor(self):
        if self.type.python == 'identifier':
            return f'obj2{self.type.python}'
        if self.is_sequence:
            assert self.type.java.endswith('[]')
            return f'obj2{self.type.java[:-2]}'
        return f'obj2{self.type.java}'


class Enum(NamedTuple):
    name: Name
    members: Tuple[Name, ...]

    @property
    def doc(self):
        return self.name.python + ' = ' + ' | '.join(m.python for m in self.members)


class ConcreteClass(NamedTuple):
    name: Name
    outer_name: Optional[Name]
    fields: Tuple[Field, ...]
    attributes: Tuple[Field, ...]
    outer_has_attributes: bool

    @property
    def full_name(self):
        return f'{self.outer_name.java}.{self.name.java}' if self.outer_name else self.name.java

    @property
    def doc(self):
        fields = ', '.join(f.doc for f in self.fields)
        if fields:
            fields = f'({fields})'
        return self.name.python + fields


class AbstractClass(NamedTuple):
    name: Name
    inner_classes: Tuple[ConcreteClass, ...]
    attributes: Tuple[Field, ...]

    @property
    def doc(self):
        sep = "\n{}| ".format(" " * (len(self.name.python) + 1))
        return self.name.python + ' = ' + sep.join(t.doc for t in self.inner_classes)


Type = Union[Enum, ConcreteClass, AbstractClass]


class Module(NamedTuple):
    types: Tuple[Type, ...]

    def collect_field_names(self) -> Iterable[Name]:
        c = FieldNameCollector()
        c.visit(self)
        return c.field_names

    def collect_class_names(self) -> Iterable[Name]:
        c = ClassNameCollector()
        c.visit(self)
        return c.class_names

    def collect_types(self) -> Iterable[Type]:
        for t in self.types:
            yield t
            if isinstance(t, AbstractClass):
                yield from t.inner_classes

    def collect_enum_constants(self) -> Iterable[Name]:
        c = EnumNameCollector()
        c.visit(self)
        assert len(c.names) == len(set(c.names)), 'duplicate enum constants names are not supported'
        return c.names


def snake_to_lower_camel(s: str) -> str:
    components = s.split('_')
    return components[0] + ''.join(c.capitalize() for c in components[1:])


def snake_to_upper_camel(s: str) -> str:
    return ''.join(c.capitalize() for c in s.split('_'))


def camel_to_snake(s: str) -> str:
    return re.sub('(?!^)([A-Z]+)', r'_\1', s).lower()


def convert_type_name(name: str) -> str:
    assert name not in asdl.builtin_types
    return NAME_OVERRIDES.get(name, snake_to_upper_camel(name) + 'Ty')


def convert_constructor_name(name: str) -> str:
    assert name not in asdl.builtin_types
    return name


def convert_field_name(name: str) -> str:
    return NAME_OVERRIDES.get(name, snake_to_lower_camel(name))


def get_java_type_for_field(field: asdl.Field, java_name: str) -> str:
    if field.type == 'int':
        type_name = 'boolean' if java_name.startswith('is') else 'int'
    elif field.type in ('string', 'identifier'):
        type_name = 'String'
    elif field.type == 'constant':
        type_name = 'ConstantValue'
    else:
        type_name = convert_type_name(field.type)
    if field.seq:
        type_name += '[]'
    return type_name


def is_enum(sum_node: asdl.Sum) -> bool:
    """Determines if a sum can be represented by a trivial Java enum (i.e. there are no fields)"""
    return isinstance(sum_node, asdl.Sum) and not sum_node.attributes and all(not t.fields for t in sum_node.types)


def attributes_are_empty_or_source_range(attributes: List[asdl.Field]) -> bool:
    """All SST nodes in GraalPy have a SourceRange instance. In the current version
    of the Python.asdl file, all attributes have the same structure and are only used
    to represent source locations. Therefore, we don't need to support generic
    attributes at all - we just check that any attributes, if present, indeed map to
    our SourceRange class to future-proof this script.
    """
    names = ('lineno', 'col_offset', 'end_lineno', 'end_col_offset')
    if not attributes:
        return True
    if len(attributes) != len(names):
        return False
    return all(attr.name == name and attr.type == 'int' and not attr.seq for attr, name in zip(attributes, names))


class AsdlToModelConvertor:
    def visit(self, obj, *args):
        return getattr(self, "visit_" + obj.__class__.__name__.lower())(obj, *args)

    def visit_module(self, node: asdl.Module) -> Module:
        return Module(tuple(self.visit_type(dfn) for dfn in node.dfns))

    def visit_type(self, node: asdl.Type) -> Type:
        name = Name(node.name, convert_type_name(node.name), 'T')
        return self.visit(node.value, name)

    def visit_product(self, node: asdl.Product, name: Name) -> Type:
        assert attributes_are_empty_or_source_range(node.attributes), f'unsupported attributes in {name}'
        fields = tuple(self.visit_field(f) for f in node.fields)
        attributes = tuple(self.visit_field(f) for f in node.attributes)
        return ConcreteClass(name, None, fields, attributes, False)

    def visit_sum(self, node: asdl.Sum, name: Name) -> Type:
        if is_enum(node):
            assert not node.attributes, 'attributes in enums are not supported'
            members = tuple(Name(t.name, convert_constructor_name(t.name), 'C') for t in node.types)
            return Enum(name, members)
        else:
            assert attributes_are_empty_or_source_range(node.attributes), f'unsupported attributes in {name}'
            attributes = tuple(self.visit_field(f) for f in node.attributes)
            inner_classes = tuple(self.visit_constructor(t, name, bool(attributes)) for t in node.types)
            return AbstractClass(name, inner_classes, attributes)

    def visit_constructor(self, node: asdl.Constructor, outer_name: Name, outer_has_attributes: bool) -> ConcreteClass:
        name = Name(node.name, convert_constructor_name(node.name), 'C')
        fields = tuple(self.visit_field(f) for f in node.fields)
        return ConcreteClass(name, outer_name, fields, (), outer_has_attributes)

    @staticmethod
    def visit_field(node: asdl.Field) -> Field:
        assert node.name is not None, 'unnamed fields are not supported'
        name = Name(node.name, convert_field_name(node.name), 'F')
        typ = Name(node.type, get_java_type_for_field(node, name.java), '')
        return Field(name, typ, node.opt, node.seq, str(node))


class Visitor:
    def visit(self, obj, *args):
        m = getattr(self, "visit_" + camel_to_snake(obj.__class__.__name__), None)
        if m:
            return m(obj, *args)


class Collector(Visitor):
    def visit_module(self, module: Module):
        for t in module.types:
            self.visit(t)

    def visit_abstract_class(self, c: AbstractClass):
        for t in c.inner_classes:
            self.visit(t)

    def visit_concrete_class(self, c: ConcreteClass):
        for f in c.fields:
            self.visit(f)


class FieldNameCollector(Collector):
    field_names = set()

    def visit_abstract_class(self, c: AbstractClass):
        super().visit_abstract_class(c)
        self.field_names.update(a.name for a in c.attributes)

    def visit_concrete_class(self, c: ConcreteClass):
        super().visit_concrete_class(c)
        self.field_names.update(a.name for a in c.attributes)

    def visit_field(self, f: Field):
        self.field_names.add(f.name)


class ClassNameCollector(Collector):
    class_names = set()

    def visit_abstract_class(self, c: AbstractClass):
        super().visit_abstract_class(c)
        self.class_names.add(c.name)

    def visit_concrete_class(self, c: ConcreteClass):
        super().visit_concrete_class(c)
        self.class_names.add(c.name)

    def visit_enum(self, c: Enum):
        self.class_names.add(c.name)
        for m in c.members:
            self.class_names.add(m)


class EnumNameCollector(Collector):
    names = list()

    def visit_enum(self, c: Enum):
        self.names.extend(c.members)


def load(filename: str) -> Module:
    mod = asdl.parse(filename)
    if not asdl.check(mod):
        sys.exit(1)
    return AsdlToModelConvertor().visit_module(mod)
