FROM registry.redhat.io/ubi8/openjdk-11:1.14-12
WORKDIR /opt/app
ARG JAR_FILE=target/muis-iam-fuse-sftp-0.0.1-SNAPSHOT.jar
COPY ${JAR_FILE} app.jar
ENTRYPOINT ["java","-jar","app.jar"]

# mvn spring-boot:run
# mvn clean install

# Start Docker deamon
# docker build -t iam-sftp-adapter:iam_0.2 .
# Tag it and push to quay
# docker tag iam-sftp-adapter:iam_0.2 quay.io/msentissi/iam-sftp-adapter:iam_0.2
# docker login registry.redhat.io 
# docker push quay.io/msentissi/iam-sftp-adapter:iam_0.2
# OR tag it and push to dockerhub
# docker push msentissi/iam-complex-trf:1.5