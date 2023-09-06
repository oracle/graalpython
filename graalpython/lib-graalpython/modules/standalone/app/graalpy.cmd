@echo off
REM Invoke the GraalPy launcher through Maven, passing any arguments passed to
REM this script via GRAAL_PYTHON_ARGS. To avoid having to deal with multiple
REM layers of escaping, we store the arguments into GRAAL_PYTHON_ARGS delimited
REM with vertical tabs.

REM Since BAT files cannot easily generate vertical tabs, we create a helper
REM script to do it for us. We're calling Maven soon anyway, so Java must be
REM available.
set JAVA="%JAVA_HOME%/bin/java"
if not defined JAVA_HOME set JAVA=java
echo class VTabCreator { public static void main(String[] args) { System.out.print('\013'); } } > VTabCreator.java
for /f "delims=" %%i in ('%JAVA% VTabCreator.java') do set VTAB=%%i
del VTabCreator.java

REM Store each argument separated by vtab
set GRAAL_PYTHON_ARGS=
:loop
set GRAAL_PYTHON_ARGS=%GRAAL_PYTHON_ARGS%%VTAB%%~1
shift /1
if not "%~1"=="" goto loop

mvn -f "%~dp0pom.xml" exec:exec -Dexec.executable=java -Dexec.args="--module-path %%classpath -Dorg.graalvm.launcher.executablename=%~0 --module org.graalvm.py.launcher/com.oracle.graal.python.shell.GraalPythonMain"
