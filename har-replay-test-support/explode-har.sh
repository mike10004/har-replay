#!/usr/bin/env bash

HAR_FILE=$1
if [ -z "${HAR_FILE}" ] ; then
  echo "arguments must be HARFILE OUTPUTDIR" >&2
  exit 1
fi

OUTPUT_DIR=$2

mvn exec:java \
    -Dexec.mainClass=io.github.mike10004.harreplay.tests.HarExploder \
    -Dexec.classpathScope=test \
    -Dexec.args="${OUTPUT_DIR}" < ${HAR_FILE}
