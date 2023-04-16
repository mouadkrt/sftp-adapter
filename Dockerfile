FROM registry.redhat.io/ubi8/openjdk-11:1.14-12
#FROM openjdk:19-jdk-alpine3.16
WORKDIR /opt/app
ARG JAR_FILE=target/muis-iam-fuse-sftp-0.0.1-SNAPSHOT.jar
COPY ${JAR_FILE} app.jar
RUN touch /tmp/sapqual6_public_key && chmod 775 /tmp/sapqual6_public_key
#RUN cat /tmp/sapqual6_public_key >> ~/.ssh/
#COPY src /opt/app/src
#COPY pom.xml  /opt/app/pom.xml
ENTRYPOINT ["java","-jar","app.jar"]
#ENTRYPOINT ["/bin/sh"]
# mvn spring-boot:run
# mvn clean install

# Start Docker deamon
# docker build -t iam-sftp-adapter:iam_1.3 .
# Tag it and push to quay
# docker tag iam-sftp-adapter:iam_1.3 quay.io/msentissi/iam-sftp-adapter:iam_1.3
# docker login registry.redhat.io 
# docker push quay.io/msentissi/iam-sftp-adapter:iam_1.3
# OR tag it and push to dockerhub
# docker push msentissi/iam-sftp-adapter:iam_1.3

# docker run --rm -ti iam-sftp-adapter:iam_1.3