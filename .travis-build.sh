#!/usr/bin/env bash

echo "running as $(whoami)"

set -e

SRC_DIR=$(readlink -f $(dirname $0))
CLONE_DIR=$(mktemp --directory --tmpdir=${PWD})
echo "cloning source directory $SRC_DIR to $CLONE_DIR"
mkdir -p "${CLONE_DIR}/target"
TESTFILE="${CLONE_DIR}/target/testfile"
touch ${TESTFILE}
echo "touched ${TESTFILE}"
cd ${CLONE_DIR}
MVN='/usr/local/maven-3.5.2/bin/mvn'
echo "mvn: resolving dependencies"
${MVN} -B --settings travis-maven-settings.xml dependency:resolve dependency:resolve-plugins >/dev/null
${MVN} -B --settings travis-maven-settings.xml verify -Dhar-replay.chromedriver.chrome.arguments=--no-sandbox
