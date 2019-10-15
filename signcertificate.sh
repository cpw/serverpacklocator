#!/bin/bash

java -cp $(dirname $0)/serverpacklocator-@version@.jar cpw.mods.forge.serverpacklocator.cert.CertSigner "$@"