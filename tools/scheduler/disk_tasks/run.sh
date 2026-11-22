#!/bin/bash
set -eu
cd /home/lain/reports/xiaobao-takeover/20261114-r3/source-3c3f643
exec java -Xmx512m -XX:ActiveProcessorCount=1 -cp "$(cat /home/lain/reports/xiaobao-takeover/20261114-r3/classpath.txt)" clojure.main /home/lain/projects/lain-workspace/tools/scheduler/disk_tasks/main.clj "$1"
