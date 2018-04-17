## Interop

#### Interop from Python

You can import the `polyglot` module to interact with other languages.

```python
import polyglot
```

You can import a global value from the entire polyglot scope:
```python
imported_polyglot_global = polyglot.import_value("global_name")
```
This global should then work as expected, accessing items sends `READ` messages,
calling methods sends `INVOKE` messages etc.

You can evaluate some code in another language:
```python
polyglot.eval(string="1 + 1", language="ruby")
```

This kind of `polyglot.eval` also works with the somewhat obscure mime-types:
```python
polyglot.eval(string="1 + 1", language="application/x-ruby")
```

It also works with the path to a file:
```python
polyglot.eval(file="./my_ruby_file.rb", language="ruby")
```

If you pass a file, you can also try to rely on the file-based language detection:
```python
polyglot.eval(file="./my_ruby_file.rb")
```

To export something from Python to other Polyglot languages so they can import
it:
```python
foo = object() polyglot.export_value(foo, "python_foo")
```

The export function can be used as a decorator, in this case the function name
is used as the globally exported name:
```python
@polyglot.export_value
def python_method():
    return "Hello from Python!"
```

#### Python responses to Truffle interop messages

###### READ
If the key is a String try using `__getattribute__` to read. If that fails or
the key isn't a String, but there is both a `__len__` and a `__getitem__`
method, call `__getitem__`.

Since the `KEYS` message returns an object's attributes, `READ` thus prefers the
object attributes if the key is a String, and only falls back to `__getitem__`
if the key is not a String or the object actually looks like a sequence.

Since disambiguation is hard here, to be explicit when using String keys, the
key may be prefixed with `@` to force `__getattribute__` access or with `[` to
force `__getitem__` access. If an item is accessed that starts with `@`, this
will mean we first try to read it as an attribute without the `@`, only to fall
back to reading it as an item. If the performance of this access is an issue,
always prefixing may be advisable.

###### UNBOX
* `str` => `java.lang.String`
* `byte` => `java.lang.String`, assuming Java system encoding
* `float` => `double`
* `int` => `int` or `long`, if it fits, otherwise raises an interop exception

###### WRITE
If the key is an attribute defined directly on the object (not inherited), use
`__setattr__`. If the key is a String and there is a `keys`, `items`, `values`
and a `__setitem__` method, we assume this is a Mapping and try to set the key
using `__setitem__`. If the key is a String, but not all of `keys`, `items`, and
`values` exists, we use `__setattr__`. Otherwise, we try to use `__setitem__` if
that exists, or otherwise fall back to `__setattr__`.

Just as with `READ`, disambiguation is hard here, so to be explicit when using
String keys, the key may be prefixed with `@` to force `__setattr__` access or
with `[` to force `__setitem__` access.

###### REMOVE
The remove message follows the same logic as the `WRITE` message, except with
`__delattr__` and `__delitem__`. It returns true if the removal was successful.

###### EXECUTE
Call the `__call__` method of the receiver with the provided arguments.

###### IS_EXECUTABLE
Returns true if the receiver has inherited a `__call__` field.

###### IS_INSTANTIABLE
Returns true only for python classes, i.e., type instances constructed through
the `type` constructor.

###### INVOKE
The equivalent of `receiver.methodname(arguments)`.

###### NEW
Calls the constructor only if the receiver object is a Python class.

###### IS_NULL
Returns true for None only.

###### HAS_SIZE
According to the Truffle interop contract answering `true` to `HAS_SIZE` implies
that indexed element access is available. Thus, we answer `true` here only for
(sub-)instances of `tuple`, `list`, `array.array`, `bytearray`, `bytes`, `str`,
and `range`.

###### GET_SIZE
Calls `__len__`. Just because `GET_SIZE` returns something positive does not
mean that accessing the object using integers is possible. We have no way of
knowing what the `__getitem__` method does with an integer argument. Use
`HAS_SIZE` first for a more conservative check if indexed access is possible.

###### IS_BOXED
Returns true for those values that can be unboxed using the `UNBOX` message.

###### KEY_INFO
This will lookup the key using `READ`, assume it is readable and writable, and
check `IS_EXECUTABLE`.

###### HAS_KEYS
Returns true for any boxed Python object, so small integers, booleans, or floats
usually don't return true.

###### KEYS
This returns the direct attributes of the receiver object, which would usually
be available through `__getattribute__`. 

The `KEYS` message requires the returned object to have only `java.lang.String`
items. If the object responds to `keys`, `values`, `items`, and `__getitem__`,
we assume it is Mapping, and we present the result of the `keys` method in
combination with the attributes if, and only if, all keys are strings. This is
roughly parallel to how `READ` and `WRITE` would be handled for string keys.

It's still possible that none of the keys can be `READ`: the `READ` message uses
Python semantics for lookup, which means that an inherited descriptor with a
`__get__` method may still come before the object's keys and do anything
(including raising an `AttributeError`).

###### IS_POINTER
Returns true if the object is a Python function defined in a Python C extension
module and the function pointer is a native pointer.

###### AS_POINTER
Returns the underlying C function pointer for the Python C extension module.

###### TO_NATIVE
Returns the underlying TruffleObject for a Python C extension module function
if that is a native pointer.
