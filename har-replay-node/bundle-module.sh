#!/usr/bin/env bash

set -e
TAG=$1
if [ -z "${TAG}" ] ; then
  echo "bundle-module.sh: requires argument: tree-like" >&2
  exit 1
fi


OUTZIP="${PWD}/har-replay-proxy-${TAG}.zip"
rm -f "${OUTZIP}"
SCRATCHDIR=$(mktemp --directory --tmpdir har-replay-proxy-bundle-XXXXXXXXXX)
git clone --quiet -c advice.detachedHead=false --depth 1 --single-branch --branch "${TAG}" https://github.com/mike10004/har-replay-proxy.git "${SCRATCHDIR}"
pushd "${SCRATCHDIR}" >/dev/null
STAGEDIR_PARENT=$(mktemp --directory --tmpdir har-replay-proxy-bundle-XXXXXXXXXX)
STAGEDIR="${STAGEDIR_PARENT}/har-replay-proxy"
mkdir -p "${STAGEDIR}"
git archive "${TAG}" | tar xf - -C "${STAGEDIR}"
popd >/dev/null
pushd "${STAGEDIR}" >/dev/null
npm install --silent --progress false --production
rm -r ./images/
popd >/dev/null
pushd "${STAGEDIR_PARENT}" >/dev/null
zip -rq "${OUTZIP}" har-replay-proxy
echo "created ${OUTZIP}"
rm -rf "${STAGEDIR_PARENT}" "${SCRATCHDIR}"
