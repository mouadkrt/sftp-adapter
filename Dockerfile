FROM registry.redhat.io/openjdk/openjdk-11-rhel7:1.1-12
WORKDIR /opt/app
ARG JAR_FILE=target/muis-iam-fuse-sftp-0.0.1-SNAPSHOT.jar
COPY ${JAR_FILE} app.jar
#COPY Ariba-rec.cer /tmp/Ariba-rec.cer
#COPY Ariba-prod.cer /tmp/Ariba-prod.cer
#RUN touch /tmp/sapqual6_public_key && chmod 775 /tmp/sapqual6_public_key
USER root
RUN mkdir -p /tmp/ariba/renamed /certs
RUN chmod -R 777 /tmp/ariba /tmp/ariba/renamed /usr/lib/jvm/java-11-openjdk-11.0.10.0.9-0.el7_9.x86_64/lib/security/cacerts
#RUN keytool -import -noprompt -deststorepass changeit -alias ariba-rec -file /tmp/Ariba-rec.cer -keystore /usr/lib/jvm/java-11-openjdk-11.0.10.0.9-0.el7_9.x86_64/lib/security/cacerts
#RUN keytool -import -noprompt -deststorepass changeit -alias ariba-prod -file /tmp/Ariba-prod.cer -keystore /usr/lib/jvm/java-11-openjdk-11.0.10.0.9-0.el7_9.x86_64/lib/security/cacerts
ENV JAVA_OPTS=""
#ENTRYPOINT java ${JAVA_OPTS} -Djsse.enableSNIExtension=false -jar app.jar

COPY /entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh
ENTRYPOINT ["sh", "/usr/local/bin/entrypoint.sh"]
#ENTRYPOINT ["/bin/sh"]

# mvn spring-boot:run

# Need to build a new image? , lo tienes abajo :
    # mvn clean install

    # Start Docker deamon
    # docker build -t iam-sftp-adapter:iam_5.0-zip .
    # Tag it and push to quay
    # docker tag iam-sftp-adapter:iam_5.0-zip quay.io/msentissi/iam-sftp-adapter:iam_5.0-zip
    # docker login registry.redhat.io 
    # docker push quay.io/msentissi/iam-sftp-adapter:iam_5.0-zip
    # OR tag it and push to dockerhub
    #   docker push msentissi/iam-sftp-adapter:iam_5.0-zip

    # docker run --rm -ti iam-sftp-adapter:iam_5.0-zip
    # docker run --env JAVA_OPTS="-Xms256m -Xmx5024m" --network host --rm -ti iam-sftp-adapter:iam_5.0-zip
    # docker run --rm -ti --network="host" -e sFTP_HOST="130.24.80.145" -e sFTP_PWD="123456" iam-sftp-adapter:iam_5.0-zip
    # java.lang.OutOfMemoryError ? Exec into the container to check heap space : jhsdb jmap --heap --pid 1, or : java -XX:+PrintFlagsFinal -version | grep HeapSize
        # Look for HeapConfiguration section : MaxHeapSize 
    # Use the following to create big test file on windows : fsutil file createnew test 1073741824  (1048576 = 1MB, 1073741824 = 1GB, 5368709120 =5GB)

## Need to use in Openshift ?  :
    # pull the image locally   
    # podman pull quay.io/msentissi/myImage:myTag   
    # oc projet myProject
# oc get is (locate the route of your ImageStream : <ImageStreamRoute>)
# tag the concerned image 
    # podman tag quay.io/msentissi/myImage:myTag <ImageStreamRoute>/myImage:myTag
# login to openshift local registry
    # podman login -u kubeadmin -p $(oc whoami -t) --tls-verify=false default-route-openshift-image-registry.apps.okd.iamdg.net.ma
# push image to openshift Registry
    # podman push --tls-verify=false default-route-openshift-image-registry.apps.okd.iamdg.net.ma/myImage:myTag  
# To use the new image, change/update the tag in (triggers > -type : ImageChange > from > name OR spec.template.spec.containers.image) in deployment[config] to the value of dockerImageReference in the ImageStreamTag you just pushed