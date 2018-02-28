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

# The commands run from this point on should line up with
# the `install` and `script` config elements from .travis.yml
echo "install: true"
true
echo "mvn: install"
mvn -B --settings travis-maven-settings.xml -Ptravis install 1>/tmp/mvn-install.log 2>/tmp/mvn-install-stderr.log
echo "mvn: install finished with exit code $?"
