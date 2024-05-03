---
layout: docs-experimental
toc_group: python
link_title: Interoperability
permalink: /reference-manual/python/Interoperability/
---
# Interoperability

Besides being primarily recommended to use in your Java application, GraalPy can interoperate with other Graal languages (languages implemented on the [Truffle framework](https://www.graalvm.org/latest/graalvm-as-a-platform/language-implementation-framework/)).
This means that you can use the objects and functions provided by those other languages directly from your Python scripts.

## Interacting with Java from Python scripts

Java is the host language of the JVM and runs the GraalPy interpreter itself.
To interoperate with Java from Python scripts, use the `java` module:
```python
import java
BigInteger = java.type("java.math.BigInteger")
myBigInt = BigInteger.valueOf(42)
# a public Java methods can just be called
myBigInt.shiftLeft(128) # returns a <JavaObject[java.math.BigInteger] at ...>
# Java method names that are keywords in Python must be accessed using `getattr`
getattr(myBigInt, "not")() # returns a <JavaObject[java.math.BigInteger] at ...>
byteArray = myBigInt.toByteArray()
# Java arrays can act like Python lists
assert len(byteArray) == 1 and byteArray[0] == 42
```

<aside markdown="1">
For plain Python users, the `java` module is only available when running on the JVM distribution.
</aside>

To import packages from the `java` namespace, you can also use the conventional Python import syntax:
```python
import java.util.ArrayList
from java.util import ArrayList
assert java.util.ArrayList == ArrayList

al = ArrayList()
al.add(1)
al.add(12)
assert list(al) == [1, 12]
```

In addition to the `type` built-in method, the `java` module exposes the following methods:

Built-in                 | Specification
---                      | ---
`instanceof(obj, class)` | returns `True` if `obj` is an instance of `class` (`class` must be a foreign object class)
`is_function(obj)`       | returns `True` if `obj` is a Java host language function wrapped using interop
`is_object(obj)`         | returns `True` if `obj` if the argument is Java host language object wrapped using interop
`is_symbol(obj)`         | returns `True` if `obj` if the argument is a Java host symbol, representing the constructor and static members of a Java class, as obtained by `java.type`

```python
ArrayList = java.type('java.util.ArrayList')
my_list = ArrayList()
assert java.is_symbol(ArrayList)
assert not java.is_symbol(my_list)
assert java.is_object(ArrayList)
assert java.is_function(my_list.add)
assert java.instanceof(my_list, ArrayList)
```

See [Polyglot Programming](https://github.com/oracle/graal/blob/master/docs/reference-manual/polyglot-programming.md) and [Embed Languages](https://github.com/oracle/graal/blob/master/docs/reference-manual/embedding/embed-languages.md) for more information about interoperability with other programming languages.

## Interacting with other dynamic languages from Python scripts

More general, non-JVM specific interactions with other languages from Python scripts are achieved via the _polyglot_ API.
This includes all interactions with dynamic languages supported via the [Truffle framework](https://www.graalvm.org/latest/graalvm-as-a-platform/language-implementation-framework/), including JavaScript and Ruby.

### Installing other dynamic languages

Other languages can be included by using their respective Maven dependencies in the same manner as GraalPy.
For example, if you have already configured a Maven project with GraalPy, add the following dependency to gain access to JavaScript:
```xml
<dependency>
    <groupId>org.graalvm.polyglot</groupId>
    <artifactId>js</artifactId>
    <version>24.0.0</version>
</dependency>
```

<aside markdown="1">
For Python developers, other languages are only available for the GraalPy JVM distributions after using the `libexec/graalpy-polyglot-get` command from the distribution's root directory.
To install JavaScript, for example:
```shell
libexec/graalpy-polyglot-get js
```
</aside>

### Examples

1. Import the `polyglot` module to interact with other languages:
   ```python
   import polyglot
   ```

2. Evaluate inlined code in another language:
   ```python
   assert polyglot.eval(string="1 + 1", language="js") == 2
   ```

3. Evaluate code from a file:
   ```python
   with open("./my_js_file.js", "w") as f:
       f.write("Polyglot.export('JSMath', Math)")
   polyglot.eval(path="./my_js_file.js", language="js")
   ```

4. Import a glocal value from the polyglot scope:
   ```python
   Math = polyglot.import_value("JSMath")
   ```

    This global value should then work as expected:
    * Accessing attributes reads from the *polyglot members* namespace:
      ```python
      assert Math.E == 2.718281828459045
      ```

    * Calling a method on the result attempts to do a straight `invoke` and falls back to reading the member and trying to execute it.
      ```python
      assert Math.toString() == "[object Math]"
      ```

    * Accessing items is supported both with strings and numbers.
      ```python
      assert Math["PI"] == 3.141592653589793
      ```

5. Use the JavaScript regular expression engine to match Python strings:
   ```python
   js_re = polyglot.eval(string="RegExp()", language="js")

   pattern = js_re.compile(".*(?:we have (?:a )?matching strings?(?:[!\\?] )?)(.*)")

   if pattern.exec("This string does not match"): raise SystemError("that shouldn't happen")

   md = pattern.exec("Look, we have matching strings! This string was matched by Graal.js")

   assert "Graal.js" in md[1]
    ```

    This program matches Python strings using the JavaScript regular expression object. Python reads the captured group from the JavaScript result and checks for a substring in it.

## Exporting Python Objects to other Languages

The `polyglot` module can be used to expose Python objects to JVM languages and other Graal languages (languages implemented on the [Truffle framework](https://www.graalvm.org/latest/graalvm-as-a-platform/language-implementation-framework/)).

1. You can export some object from Python to other languages so they can import it:
   ```python
   import ssl
   polyglot.export_value(value=ssl, name="python_ssl")
   ```
   
   Then use it in (for example) from JavaScript code:
   ```js
   Polyglot.import('python_ssl).get_server_certificate(["oracle.com", 443])
   ```

2. You can decorate a Python function to export it by name:
   ```python
   @polyglot.export_value
   def python_method():
       return "Hello from Python!"
   ```

   Then use it (for example) from Java code:
   ```java
   import org.graalvm.polyglot.*;

   class Main {
       public static void main(String[] args) {
           try (var context = Context.create()) {
               context.eval(Source.newBuilder("python", "file:///python_script.py").build());

               String result = context.
                   getPolyglotBindings().
                   getMember("python_method").
                   execute().
                   asString();
               assert result.equals("Hello from Python!");
           }
       }
    }
    ```

## Mapping Types between Python and Other Languages

The interop protocol defines different "types" which can overlap in all kinds of ways and have restrictions on how they can interact with Python.

### Interop Types to Python

Most importantly and upfront: all foreign objects passed into Python have the Python type `foreign`.
There is no emulation of (for example) objects that are of interop type "boolean" to have the Python type `bool`.
This is because interop types can overlap in ways that the Python built-in types cannot, and we have yet to define which type should take precedence and such situations.
We do expect to change this in the future, however.
For now, the `foreign` type defines all of the Python special methods for type conversion that are used throughout the interpreter (methods such as `__add__`, `__int__`, `__str__`, `__getitem__`, and so on)
and these try to "do the right thing" based on the interop type (or raise an exception).

Types not listed in the table below have no special interpretation in Python.

| Interop Type                        | Python Interpretation                                                                                                                                                                   |
|:--------------------------------|:------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `null`         | `null` is like `None`. Important to know: interop `null` values are all identical to `None`. JavaScript defines two "null-like" values; `undefined` and `null`, which are *not* identical, but when passed to Python, they are treated so.                                                                                                        |
| `boolean`      | `boolean` behaves like Python booleans, including the fact that in Python, all booleans are also integers (1 and 0 for true and false, respectively).                                                                                                                                                                                             |
| `number`       | `number` Behaves like Python numbers. Python only has one integer and one floating point type, but ranges are imported in some places such as typed arrays.                                                                                                                                                                                       |
| `string`       | Behaves in the same way as a Python string.                                                                                                                                                                                                                                                                                                       |
| `buffer`       | Buffers are also a concept in Python's native API (albeit slightly different). Interop buffers are treated in the same was as Python buffers in some places (such as `memoryview`) to avoid copies of data.                                                                                                                                       |
| `array`        | An `array` can be used with subscript access in the same way as Python lists, with integers and slices as indices.                                                                                                                                                                                                                                |
| `hash`         | A `hash` can be used with subscript access in the same way as Python dictionaries, with any "hashable" object as a key. "Hashable" follows Python semantics: generally every interop type with an identity is deemed "hashable". Note that if an interop object is of type `Array` **and** `Hash`, the behavior of subscript access is undefined. |
| `members`      | An object of type `members` can be read using conventional Python `.` notation or `getattr` and related functions.                                                                                                                                                                                                                                |
| `iterable`     | An `iterable` is treated in the same way as any Python object with an `__iter__` method. That is, it can be used in a loop and other places that accept Python iterables.                                                                                                                                                                         |
| `iterator`     | An `iterator` is treated in the same way as any Python object with a `__next__` method.                                                                                                                                                                                                                                                           |
| `exception`    | An `exception` can be caught in a generic `except` clause.                                                                                                                                                                                                                                                                                        |
| `MetaObject`   | Meta objects can be used in subtype and `isinstance` checks.                                                                                                                                                                                                                                                                                      |
| `executable`   | An `executable` object can be executed as a function, but never with keyword arguments.                                                                                                                                                                                                                                                           |
| `instantiable` | An `instantiable` object can be called just like a Python type, but never with keyword arguments.                                                                                                                    |

### Python to Interop Types

| Interop Type                        | Python Interpretation                                                                                                                                                                   |
|:--------------------------------|:------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `null`         | Only `None`.                                                                                                                                          |
| `boolean`      | Only subtypes of Python `bool`. Note that in contrast to Python semantics, Python `bool` is *never* also an interop number.                           |
| `number`       | Only subtypes of `int` and `float`.                                                                                                                   |
| `string`       | Only subtypes of `str`.                                                                                                                               |
| `array`        | Any object with `__getitem__` and `__len__` methods, but not if it also has `keys`, `values`, and `items` methods (in the same way that `dict` does.) |
| `hash`         | Only subtypes of `dict`.                                                                                                                              |
| `members`      | Any Python object. Note that the rules for readable/writable are a bit ad-hoc, since checking that is not part of the Python MOP.                     |
| `iterable`     | Any Python object that has `__iter__` or a `__getitem__` methods.                                                                                     |
| `iterator`     | Any Python object with a `__next__` method.                                                                                                           |
| `exception`    | Any Python `BaseException` subtype.                                                                                                                   |
| `MetaObject`   | Any Python `type`.                                                                                                                                    |
| `executable`   | Any Python object  with a `__call__` method.                                                                                                          |
| `instantiable` | Any Python `type`.                                                                                                                                    |

## The Interoperability Extension API

It is possible to extend the interoperability protocol directly from Python via a simple API defined in the `polyglot` module. 
The purpose of this API is to enable custom / user defined types to take part in the interop ecosystem. 
This is particularly useful for external types which are not compatible by default with the interop protocol. 
An example in this sense are the `numpy` numeric types (for example, `numpy.int32`) which are not supported by default by the interop protocol. 

### The API 

| Function                        | Description                                                                                                                                                                   |
|:--------------------------------|:------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| register_interop_behavior       | Takes the receiver **type** as first argument. The remainder keyword arguments correspond to the respective interop messages. Not All interop messages are supported. |
| get_registered_interop_behavior | Takes the receiver **type** as first argument. Returns the list of extended interop messages for the given type.                                                      |
| @interop_behavior               | Class decorator, takes the receiver **type** as only argument. The interop messages are extended via **static** methods defined in the decorated class (supplier).            |

#### Supported messages 

The majority (with some exceptions) of the interop messages are supported by the interop behavior extension API, as shown in the table below.  
The naming convention for the `register_interop_behavior` keyword arguments follows the _snake_case_ naming convention, i.e. the interop `fitsInLong` message 
becomes `fits_in_long` and so on. Each message can be extended with a **pure python function** (default keyword arguments, free vars and cell vars are not allowed) or a **boolean constant**. 
The table below describes the supported interop messages:

| Message                  | Extension argument name     | Expected return type                                                                                  |
|:-------------------------|:----------------------------|-------------------------------------------------------------------------------------------------------|
| isBoolean                | is_boolean                  | bool                                                                                                  |
| isDate                   | is_date                     | bool                                                                                                  |
| isDuration               | is_duration                 | bool                                                                                                  |
| isIterator               | is_iterator                 | bool                                                                                                  |
| isNumber                 | is_number                   | bool                                                                                                  |
| isString                 | is_string                   | bool                                                                                                  |
| isTime                   | is_time                     | bool                                                                                                  |
| isTimeZone               | is_time_zone                | bool                                                                                                  |
| isExecutable             | is_executable               | bool                                                                                                  |
| fitsInBigInteger         | fits_in_big_integer         | bool                                                                                                  |
| fitsInByte               | fits_in_byte                | bool                                                                                                  |
| fitsInDouble             | fits_in_double              | bool                                                                                                  |
| fitsInFloat              | fits_in_float               | bool                                                                                                  |
| fitsInInt                | fits_in_int                 | bool                                                                                                  |
| fitsInLong               | fits_in_long                | bool                                                                                                  |
| fitsInShort              | fits_in_short               | bool                                                                                                  |
| asBigInteger             | as_big_integer              | int                                                                                                   |
| asBoolean                | as_boolean                  | bool                                                                                                  |
| asByte                   | as_byte                     | int                                                                                                   |
| asDate                   | as_date                     | 3-tuple with the following elements: (`year`: int, `month`: int, `day`: int)                          |
| asDouble                 | as_double                   | float                                                                                                 |
| asDuration               | as_duration                 | 2-tuple with the following elements: (`seconds`: long, `nano_adjustment`: long)                       |
| asFloat                  | as_float                    | float                                                                                                 |
| asInt                    | as_int                      | int                                                                                                   |
| asLong                   | as_long                     | int                                                                                                   |
| asShort                  | as_short                    | int                                                                                                   |
| asString                 | as_string                   | str                                                                                                   |
| asTime                   | as_time                     | 4-tuple with the following elements:  (`hour`: int, `minute`: int, `second`: int, `microsecond`: int) |
| asTimeZone               | as_time_zone                | a string (the timezone) or int (utc delta in seconds)                                                 |
| execute                  | execute                     | object                                                                                                |
| readArrayElement         | read_array_element          | object                                                                                                |
| getArraySize             | get_array_size              | int                                                                                                   |
| hasArrayElements         | has_array_elements          | bool                                                                                                  |
| isArrayElementReadable   | is_array_element_readable   | bool                                                                                                  |
| isArrayElementModifiable | is_array_element_modifiable | bool                                                                                                  |
| isArrayElementInsertable | is_array_element_insertable | bool                                                                                                  |
| isArrayElementRemovable  | is_array_element_removable  | bool                                                                                                  |
| removeArrayElement       | remove_array_element        | NoneType                                                                                              |
| writeArrayElement        | write_array_element         | NoneType                                                                                              |
| hasIterator              | has_iterator                | bool                                                                                                  |
| hasIteratorNextElement   | has_iterator_next_element   | bool                                                                                                  |
| getIterator              | get_iterator                | a python iterator                                                                                     |
| getIteratorNextElement   | get_iterator_next_element   | object                                                                                                |
| hasHashEntries           | has_hash_entries            | bool                                                                                                  |
| getHashEntriesIterator   | get_hash_entries_iterator   | a python iterator                                                                                     |
| getHashKeysIterator      | get_hash_keys_iterator      | a python iterator                                                                                     |
| getHashSize              | get_hash_size               | int                                                                                                   |
| getHashValuesIterator    | get_hash_values_iterator    | a python iterator                                                                                     |
| isHashEntryReadable      | is_hash_entry_readable      | bool                                                                                                  |
| isHashEntryModifiable    | is_hash_entry_modifiable    | bool                                                                                                  |
| isHashEntryInsertable    | is_hash_entry_insertable    | bool                                                                                                  |
| isHashEntryRemovable     | is_hash_entry_removable     | bool                                                                                                  |
| readHashValue            | read_hash_value             | object                                                                                                |
| writeHashEntry           | write_hash_entry            | NoneType                                                                                              |
| removeHashEntry          | remove_hash_entry           | NoneType                                                                                              | 

### Usage Example

A simple `register_interop_behavior` API is available to register interop behaviors for existing types:

```python
import polyglot
import numpy

polyglot.register_interop_behavior(numpy.int32,
    is_number=True,
    fitsInByte=lambda v: -128 <= v < 128,
    fitsInShort=lambda v: -0x8000 <= v < 0x8000
    fitsInInt=True,
    fitsInLong=True,
    fitsInBigInteger=True,
    asByte=int,
    asShort=int,
    asInt=int,
    asLong=int,
    asBigInteger=int,
)
```

The `@interop_behavior` decorator may be more convenient when declaring more behaviors.
Interop message extension is achieved via **static** methods of the decorated class.
The names of the static methods are identical to the keyword names expected by `register_interop_behavior`.

```python
from polyglot import interop_behavior
import numpy

@interop_behavior(numpy.float64)
class Int8InteropBehaviorSupplier:
    @staticmethod
    def is_number(_): 
        return True

    @staticmethod
    def fitsInDouble(_):
        return True

    @staticmethod
    def asDouble(v):
        return float(v)
```

Both classes can then behave as expected when embedded:
```java
import java.nio.file.Files;
import java.nio.file.Path;
import org.graalvm.polyglot.Context;

class Main {
    public static void main(String[] args) {
        try (var context = Context.create()) {
            context.eval("python", Files.readString(Path.of("path/to/interop/behavior/script.py")));
            assert context.eval("python", "numpy.float64(12)").asDouble() == 12.0;
            assert context.eval("python", "numpy.int32(12)").asByte() == 12;
        }
    }
}
```
