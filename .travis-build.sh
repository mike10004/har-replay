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
export PATH="/usr/local/phantomjs/bin:/usr/local/phantomjs:/usr/local/neo4j-3.2.7/bin:/usr/local/maven-3.5.2/bin:/usr/local/cmake-3.9.2/bin:/usr/local/clang-5.0.0/bin:$PATH"
echo "exported PATH=$PATH"
echo "mvn: resolving dependencies"
mvn -B --settings travis-maven-settings.xml -Ptravis dependency:resolve dependency:resolve-plugins > /tmp/mvn-dependency-resolve.log
echo "mvn: verify"
mvn -B --settings travis-maven-settings.xml -Ptravis verify 1>/tmp/mvn-verify.log 2>/tmp/mvn-verify-stderr.log
