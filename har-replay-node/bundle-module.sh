#!/usr/bin/env bash

set -e

TAG=0.0.4
OUTZIP="${PWD}/har-replay-proxy-${TAG}.zip"
rm -f "${OUTZIP}"
SCRATCHDIR=$(mktemp --directory --tmpdir har-replay-proxy-bundle-XXXXXXXXXX)
#git clone --depth 1 --single-branch --branch 0.0.1 https://github.com/mike10004/har-replay-proxy.git "${SCRATCHDIR}"
git clone --quiet https://github.com/mike10004/har-replay-proxy.git "${SCRATCHDIR}"
pushd "${SCRATCHDIR}" >/dev/null
STAGEDIR_PARENT=$(mktemp --directory --tmpdir har-replay-proxy-bundle-XXXXXXXXXX)
STAGEDIR="${STAGEDIR_PARENT}/har-replay-proxy"
mkdir -p "${STAGEDIR}"
git archive "${TAG}" | tar xf - -C "${STAGEDIR}"
popd >/dev/null
pushd "${STAGEDIR}" >/dev/null
npm install --quiet --production
rm -r ./images/
popd >/dev/null
pushd "${STAGEDIR_PARENT}" >/dev/null
zip -rq "${OUTZIP}" har-replay-proxy
echo "created ${OUTZIP}"
rm -rf "${STAGEDIR_PARENT}" "${SCRATCHDIR}"
