#!/usr/bin/env bash

set -e

SRC_DIR=$(readlink -f $(dirname $0))
#BUILD_DIR="$SRC_DIR/target"
#mkdir -p "${BUILD_DIR}"
#CLONED=$(mktemp --directory --tmpdir=${BUILD_DIR} clone_for_travis_debug_XXXXXXXX)
#CURRENT_BRANCH=$(git branch -l | grep '^*' | cut -f2 -d' ')
#git clone --quiet --single-branch --branch=${CURRENT_BRANCH} . ${CLONED}
DEST_DIR=/home/travis/har-replay
echo "starting container with $SRC_DIR mounted as $DEST_DIR"
TRAVIS_IMAGE="travisci/ci-garnet:packer-1512502276-986baf0"
CONTAINER_ID=$(docker run --rm --mount type=bind,source="$SRC_DIR",target=${DEST_DIR},readonly ${TRAVIS_IMAGE} /sbin/init)
echo "started container ${CONTAINER_ID}"
sleep 1
docker exec ${CONTAINER_ID} su --command ${DEST_DIR}/.travis-build.sh travis
docker stop ${CONTAINER_ID}
