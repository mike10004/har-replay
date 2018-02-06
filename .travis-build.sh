#!/usr/bin/env bash

echo "running as $(whoami) in ${PWD}"

set -e

SRC_DIR=$(readlink -f $(dirname $0))
CLONE_DIR=$(mktemp --directory --tmpdir=/home/travis)
echo "cloning source directory $SRC_DIR to $CLONE_DIR"
cd /home/travis/har-replay
CURRENT_BRANCH=$(git branch -l | grep '^*' | cut -f2 -d' ')
echo "CURRENT_BRANCH=${CURRENT_BRANCH}"
git clone --quiet --single-branch --branch=${CURRENT_BRANCH} /home/travis/har-replay ${CLONE_DIR}
mkdir -p "${CLONE_DIR}/target"
TESTFILE="${CLONE_DIR}/target/testfile"
touch ${TESTFILE}
echo "touched ${TESTFILE}"
cd ${CLONE_DIR}
echo "inside $CLONE_DIR"
MVN='/usr/local/maven-3.5.2/bin/mvn'
echo "mvn: resolving dependencies"
${MVN} -B --settings travis-maven-settings.xml dependency:resolve dependency:resolve-plugins > /tmp/install.log
${MVN} -B --settings travis-maven-settings.xml verify -Dhar-replay.chromedriver.chrome.arguments=--no-sandbox
