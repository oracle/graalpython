///usr/bin/env jbang "$0" "$@" ; exit $?

//DEPS org.graalvm.python:graalpy-jbang:${env.GRAALPY_VERSION:24.2.0}
// resource dir with blanks
//PYTHON_RESOURCES_DIRECTORY   

public class EmptyPythonResourceCommentWithBlanks {
    public static void main(String[] args) {
    }
}