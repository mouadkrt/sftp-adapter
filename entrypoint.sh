#!/bin/bash

cd /usr/lib/jvm/java-11-openjdk-11.0.10.0.9-0.el7_9.x86_64/lib/security

keytool -import -cacerts -noprompt -trustcacerts -deststorepass changeit -alias ariba -file /certs/Ariba.cer
java ${JAVA_OPTS} -Djsse.enableSNIExtension=false -jar /opt/app/app.jar