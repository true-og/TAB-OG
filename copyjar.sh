#!/usr/bin/env bash

VERSION="$1"

# Clear existing build folder
rm -rf build/libs

# Ensure the target directory exists
mkdir -p build/libs

# Copy and rename the jar, using $VERSION in the final filename
cp jar/build/libs/TAB-*-SNAPSHOT.jar "build/libs/TAB-${VERSION}.jar"
