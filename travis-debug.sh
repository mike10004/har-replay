#!/usr/bin/env bash

set -e

SRC_DIR=$(readlink -f $(dirname $0))
DEST_DIR=/home/travis/har-replay
echo "starting container with $SRC_DIR mounted as $DEST_DIR"
TRAVIS_IMAGE="travisci/ci-garnet:packer-1512502276-986baf0"
SU_ARGS="--command ${DEST_DIR}/.travis-build.sh"
docker run --rm --mount type=bind,source="$SRC_DIR",target=${DEST_DIR},readonly ${TRAVIS_IMAGE} su ${SU_ARGS} travis
echo "docker exited $?"
