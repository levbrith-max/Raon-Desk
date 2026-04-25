#!/bin/sh
# Gradle Wrapper — script de lancement automatique
# Ce fichier permet à GitHub Actions et Replit de compiler sans installer Gradle manuellement

##############################################################################
# Gradle start up script for UN*X
##############################################################################
APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

die () {
    echo
    echo "ERROR: $*"
    echo
    exit 1
} >&2

warn () {
    echo "$*"
} >&2

# OS detection
case "`uname`" in
  CYGWIN* )
    APP_HOME=`cygpath --path --mixed "$APP_HOME"`
    CLASSPATH=`cygpath --path --mixed "$CLASSPATH"`
    ;;
  MSYS* | MINGW* )
    APP_HOME=`cygpath --path --mixed "$APP_HOME"`
    CLASSPATH=`cygpath --path --mixed "$CLASSPATH"`
    ;;
esac

APP_HOME=$( cd "${APP_HOME:-./}" && pwd -P ) || die "APP_HOME not found"

if [ "$1" = "" ]; then
  set -- help
fi

exec java $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS \
  "-Dorg.gradle.appname=$APP_BASE_NAME" \
  -classpath "$CLASSPATH" \
  org.gradle.wrapper.GradleWrapperMain \
  "$@"
