FROM registry.redhat.io/ubi8/openjdk-11:1.14-12
#FROM registry.redhat.io/rhel7@sha256:646de18f10f6d78b4bdbb2a4139d78e76386c6e86a86333e1cf5a0a428289c4e
#FROM openjdk:19-jdk-alpine3.16
#FROM alpine:3.17.3
#FROM alpine/openssl
WORKDIR /opt/app
ARG JAR_FILE=target/muis-iam-fuse-sftp-0.0.1-SNAPSHOT.jar
COPY ${JAR_FILE} app.jar
RUN touch /tmp/sapqual6_public_key && chmod 775 /tmp/sapqual6_public_key
USER root
RUN sed -e '/jdk.tls.disabledAlgorithms=/ s/^#*/#/g' -i /etc/java/java-11-openjdk/java-11-openjdk-11.0.18.0.10-2.el8_7.x86_64/conf/security/java.security
RUN echo "jdk.tls.disabledAlgorithms=RC4, DES, MD5withRSA, DH keySize < 1024, EC keySize < 224, 3DES_EDE_CBC, anon, NULL, include jdk.disabled.namedCurves" >> /etc/java/java-11-openjdk/java-11-openjdk-11.0.18.0.10-2.el8_7.x86_64/conf/security/java.security
#RUN cat /tmp/sapqual6_public_key >> ~/.ssh/
#COPY src /opt/app/src
#COPY pom.xml  /opt/app/pom.xml
#ENTRYPOINT ["java","-Dhttps.protocols=TLSv1.0,TLSv1.1,TLSv1.2,TLSv1.3", "-jar","app.jar"]
#ENTRYPOINT ["java","-jar","app.jar"]
ENTRYPOINT ["/bin/sh"]
# mvn spring-boot:run
# mvn clean install

# Start Docker deamon
# docker build -t iam-sftp-adapter:iam_1.7 .
# Tag it and push to quay
# docker tag iam-sftp-adapter:iam_1.7 quay.io/msentissi/iam-sftp-adapter:iam_1.7
# docker login registry.redhat.io 
# docker push quay.io/msentissi/iam-sftp-adapter:iam_1.7
# OR tag it and push to dockerhub
# docker push msentissi/iam-sftp-adapter:iam_1.7

# docker run --rm -ti iam-sftp-adapter:iam_1.7