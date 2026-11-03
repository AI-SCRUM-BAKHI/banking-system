#!/bin/sh

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

if [ -z "${GRADLE_USER_HOME:-}" ]; then
  export GRADLE_USER_HOME="$APP_HOME/.gradle-home"
fi

if [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVA_EXECUTABLE="$JAVA_HOME/bin/java"
else
  JAVA_EXECUTABLE="java"
fi

exec "$JAVA_EXECUTABLE" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
