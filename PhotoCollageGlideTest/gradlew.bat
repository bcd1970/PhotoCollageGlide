@echo off
@rem -------------------------------------------------------------------------
@rem Gradle startup script for Windows
@rem -------------------------------------------------------------------------

setlocal

set DIR=%~dp0
set APP_HOME=%DIR%

set CLASSPATH=%APP_HOME%gradle\wrapper\gradle-wrapper.jar
set DEFAULT_JVM_OPTS=

if defined JAVA_HOME (
  set JAVA_EXE=%JAVA_HOME%\bin\java.exe
) else (
  set JAVA_EXE=java
)

"%JAVA_EXE%" %DEFAULT_JVM_OPTS% -Dorg.gradle.appname=gradlew -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

endlocal
