fake sFTP server :
    docker run -p 22:22 -d  atmoz/sftp osbsap:osbsap$23:::upload
	shell login into the pod and create /home/osbsap/archive folder
ariba_fake_https :
    C:\Users\msentissi\Documents\CLIENTS\IAM\API Mgmt\Product\WSDL_Recette\_Complexe\MasterDataImport_Response_V1 (SFTP)\ariba_fake_https
mvn clean install
set MAVEN_OPTS=-Xmx6144m
set sFTP_PWD=osbsap$23
set ARIBA_UPLOAD_URL=http://localhost:3000/upload
mvn spring-boot:run

see instructions in Dockerfile as well