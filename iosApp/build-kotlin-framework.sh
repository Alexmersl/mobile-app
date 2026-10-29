#!/bin/sh
# Фаза сборки Xcode: собирает и встраивает фреймворк Shared из Gradle-модуля :shared.
# Подпись и релиз сюда не входят.
set -e

if [ "YES" = "$OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED" ]; then
  echo "Skipping Gradle build task invocation due to OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED environment variable set to YES"
  exit 0
fi

# Xcode запускает скрипт без окружения терминала: подберём JDK и Android SDK, если они не заданы.
if [ -z "$JAVA_HOME" ] && ! /usr/libexec/java_home >/dev/null 2>&1; then
  for candidate in \
    "/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
    "/Applications/IntelliJ IDEA.app/Contents/jbr/Contents/Home"; do
    if [ -x "$candidate/bin/java" ]; then
      export JAVA_HOME="$candidate"
      break
    fi
  done
fi
if [ -z "$ANDROID_HOME" ] && [ -z "$ANDROID_SDK_ROOT" ] && [ -d "$HOME/Library/Android/sdk" ]; then
  export ANDROID_HOME="$HOME/Library/Android/sdk"
fi

cd "$SRCROOT/.."
./gradlew :shared:embedAndSignAppleFrameworkForXcode
