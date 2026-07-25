#!/bin/bash

DIRNAME="$( cd "$( dirname "$0" )" && pwd )"
GRADLE_HOME="$DIRNAME"
CLASSPATH=$GRADLE_HOME/gradle/wrapper/gradle-wrapper.jar

# Determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

exec "$JAVACMD" -Xmx64m -Xms64m \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain "$@"
