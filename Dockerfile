FROM registry.redhat.io/openjdk/openjdk-11-rhel7:1.1-12
WORKDIR /opt/app
ARG JAR_FILE=target/muis-iam-fuse-sftp-0.0.1-SNAPSHOT.jar
COPY ${JAR_FILE} app.jar
COPY Ariba-rec.cer /tmp/Ariba-rec.cer
RUN touch /tmp/sapqual6_public_key && chmod 775 /tmp/sapqual6_public_key
USER root
RUN keytool -import -noprompt -deststorepass changeit -alias ariba-rec -file /tmp/Ariba-rec.cer -keystore /usr/lib/jvm/java-11-openjdk-11.0.10.0.9-0.el7_9.x86_64/lib/security/cacerts

ENTRYPOINT ["java","-jar","app.jar"]
#ENTRYPOINT ["/bin/sh"]
# mvn spring-boot:run
# mvn clean install

# Start Docker deamon
# docker build -t iam-sftp-adapter:iam_2.0 .
# Tag it and push to quay
# docker tag iam-sftp-adapter:iam_2.0 quay.io/msentissi/iam-sftp-adapter:iam_2.0
# docker login registry.redhat.io 
# docker push quay.io/msentissi/iam-sftp-adapter:iam_2.0
# OR tag it and push to dockerhub
# docker push msentissi/iam-sftp-adapter:iam_2.0

# docker run --rm -ti iam-sftp-adapter:iam_2.0