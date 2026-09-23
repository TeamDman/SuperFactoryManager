#!/usr/bin/env bash
# Experimental build compiler for the frozen Vox JAR. Its manifest embeds the
# JDK vendor/version, so an arbitrary Java 17 cannot reproduce the locked hash.
# Keep this old compiler separate from the JDK used to run the game.
set -euo pipefail

if [[ $# -ne 1 || "$1" != /* ]]; then
    echo 'Usage: install-jbr.sh /absolute/path/to/new-jdk-directory' >&2
    exit 2
fi
if [[ "$(uname -s)" != Linux || "$(uname -m)" != x86_64 ]]; then
    echo 'This compiler archive supports Linux x86_64 only.' >&2
    exit 2
fi

destination=$(realpath --canonicalize-missing -- "$1")
if [[ -e "$destination" || -L "$destination" ]]; then
    echo "Refusing to overwrite existing JDK destination: $destination" >&2
    exit 2
fi

# Official release and checksum:
# https://github.com/JetBrains/JetBrainsRuntime/releases/tag/jbr-release-17.0.6b829.9
# https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-17.0.6-linux-x64-b829.9.tar.gz.checksum
url=https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-17.0.6-linux-x64-b829.9.tar.gz
sha512=0fd8056a31115dbe177418e7884ad546a56908fa8241c749784971a6769044a9b72e3acfca8b621a45c6c8a74c92ed630659220f65248aaab083af7f036e9bb4

parent=$(dirname -- "$destination")
mkdir -p -- "$parent"
staging=$(mktemp -d -- "$parent/.sfm-jbr.XXXXXX")
trap 'rm -rf -- "$staging"' EXIT

curl --fail --location --silent --show-error --retry 3 \
    --connect-timeout 20 --max-time 300 "$url" --output "$staging/jbr.tar.gz"
printf '%s  %s\n' "$sha512" "$staging/jbr.tar.gz" | sha512sum --check --status
mkdir "$staging/sdk"
tar --extract --gzip --file "$staging/jbr.tar.gz" \
    --directory "$staging/sdk" --strip-components=1

version=$("$staging/sdk/bin/java" -version 2>&1)
grep -Fq 'JBR-17.0.6+10-829.9' <<< "$version"
"$staging/sdk/bin/javac" -version 2>&1 | grep -Fxq 'javac 17.0.6'
"$staging/sdk/bin/jar" --version 2>&1 | grep -Fxq 'jar 17.0.6'
printf '%s\n' "$sha512" > "$staging/sdk/.sfm-ci-archive.sha512"
mv --no-target-directory -- "$staging/sdk" "$destination"
printf '%s\n' "$version" >&2
# Only the installed path is written to stdout, for command substitution.
printf '%s\n' "$destination"
