#!/usr/bin/env bash

set -e

SRC_DIR=$(readlink -f $(dirname $0))
DEST_DIR=/home/travis/har-replay
echo "starting container with $SRC_DIR mounted as $DEST_DIR"
TRAVIS_IMAGE="travisci/ci-garnet:packer-1512502276-986baf0"
SU_ARGS="--command ${DEST_DIR}/.travis-build.sh"
DOCKER_RETAIN_CONTAINER=${DOCKER_RETAIN_CONTAINER:-0}
if [ "${DOCKER_RETAIN_CONTAINER}" == "1" ] ; then
  DOCKER_ARGS=""
else
  DOCKER_ARGS="--rm"
fi
docker run ${DOCKER_ARGS} --mount type=bind,source="$SRC_DIR",target=${DEST_DIR},readonly ${TRAVIS_IMAGE} su ${SU_ARGS} travis
echo "docker exited $?"
