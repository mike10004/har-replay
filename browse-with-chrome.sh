#!/bin/bash

set -e

if [ ! -f "target/classes/har-replay-proxy.zip" ] ; then
  echo "browse-with-chrome: please execute 'mvn install' first" >&2
  exit 1
fi

echo "browse-with-chrome: started"
mvn exec:java -Dexec.mainClass=com.github.mike10004.harreplay.BrowseHarWithChromeExample \
              -Dexec.classpathScope=test \
              -Dexec.cleanupDaemonThreads=true \
              -Dexec.daemonThreadJoinTimeout=500 >/dev/null
echo "browse-with-chrome: finished"
