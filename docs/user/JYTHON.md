# About Jython Compatibility

Full Jython compatibility is not a goal of this project. One major reason for
this is that most Jython code that uses Java integration will be based on a
stable Jython release, and these only come in Python 2.x versions. The GraalVM
implementation of Python, in contrast, is only targeting Python 3.x.

Nonetheless, there are certain features of Jython's Java integration that we can
offer similarly. Some features are more expensive to offer, and thus are hidden
behind a command line flag, `--python.EmulateJython`.

## Importing Java Classes and Packages

In the default mode, Java classes can only be imported through the `java`
package. Additionally, only Java classes can be imported, not packages. In
Jython compatibility mode, importing works more directly, at the cost worse
performance for import failures in the general case.

#### Normal mode

Suppose you want to import the class `sun.misc.Signal`, normal usage looks like
this:

    import java.sun.misc.Signal as Signal

For the `java` namespace, you do not have to repeat the `java` name (but can):

    import java.lang.System as System

#### Jython mode

In Jython mode, Java packages can be imported and traversed, and they can be
imported directly without going through the `java` module.

    import sun.misc.Signal as Signal

    import org.antlr

    type(org.antlr.v4.runtime) # => module
    org.antlr.v4.runtime.Token

The downside of this is that everytime an import fails (and there are many
speculative imports in the standard library), we ask the Java classloader to
list currently available packages and traverse them to check if we should create
a Java package. This slows down startup significantly.

## Interacting with Java Objects

Once you get hold of a Java object or class, interaction in both modes works
naturally. Public fields and methods can be read and invoked as expected.

## Subclassing Java Classes and Implementing Interfaces with Python Classes

This is not supported at all right now, there's no emulation available even in
Jython compatibility mode. We have not seen many uses of this in the wild. Let
us know if this is of interest to you!

## Catching Java exceptions

By default this is not allowed, because of the additional cost of checking for
Java exceptions in the except statement execution. However, in Jython
compatibility mode, the common case of catching a Java exception directly works:

    import java.lang.NumberFormatException as NumberFormatException
    import java.lang.Integer as Integer

    try:
        Integer.parseInt("99", 8)
    except NumberFormatException as e:
        pass

Note that even in this mode, Java exceptions are never caught by generic except
handlers, so this *will not* work:

    import java.lang.Integer as Integer

    try:
        Integer.parseInt("99", 8)
    except:
        pass
