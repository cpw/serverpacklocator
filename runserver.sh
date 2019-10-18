#!/bin/sh

exec java ${JVM_XX_OPTS} -jar $(dirname $0)/serverpacklocator-@version@.jar "$@"