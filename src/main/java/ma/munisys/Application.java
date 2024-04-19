package ma.munisys;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.net.ssl.X509TrustManager;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http.HttpComponent;
import org.apache.camel.http.common.HttpOperationFailedException;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//import org.springframework.context.annotation.ImportResource;

@SpringBootApplication
//@ImportResource({"classpath:spring/camel-context.xml"})
public class Application extends RouteBuilder {

    private static String ARIBA_SHARED_SECRET  = System.getenv().getOrDefault("ARIBA_SHARED_SECRET", "rabat2121");
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Override
    public void configure() {

           // Configure SSLContextParameters to trust all certificates
			SSLContextParameters sslContextParameters = new SSLContextParameters();
			
			TrustManagersParameters trustManagersParameters = new TrustManagersParameters();
			trustManagersParameters.setTrustManager(new X509TrustManager() {
				public java.security.cert.X509Certificate[] getAcceptedIssuers() {
					return null;
				}

				public void checkClientTrusted(
					java.security.cert.X509Certificate[] certs, String authType) {
				}

				public void checkServerTrusted(
					java.security.cert.X509Certificate[] certs, String authType) {
				}
			});
			
			sslContextParameters.setTrustManagers(trustManagersParameters);

        // Set up HTTP component with custom SSLContextParameters
        HttpComponent httpComponent = getContext().getComponent("https", HttpComponent.class);
        httpComponent.setSslContextParameters(sslContextParameters);
        httpComponent.setX509HostnameVerifier(NoopHostnameVerifier.INSTANCE);


        //from("sftp:130.24.80.193:22?username=sftpuser&password=123.pwdMuis&disconnect=false&delete=true");
        String sFtpHost     = System.getenv().getOrDefault("sFTP_HOST", "localhost");
        String sFtpPort     = System.getenv().getOrDefault("sFTP_PORT", "22");
        String sFtpDir      = System.getenv().getOrDefault("sFTP_DIR", "/upload");
        String sFtpUser     = System.getenv().getOrDefault("sFTP_USER", "osbsap");

        String sFtpPassword = System.getenv().getOrDefault("sFTP_PWD", ""); 
        String sFtpDeleteFile = System.getenv().getOrDefault("sFTP_DELETE_FILE", "false"); // True or false
        
        //String ArchiveDir = System.getenv().getOrDefault("ARCHIVE_DIR", "/upload/sftp_archive");

        // Fixing file received partially at Ariba side. Possible issue : The sftp camel connecter start processing it before SAP has ended its file creating :
        //https://stackoverflow.com/questions/13844564/camel-route-picking-up-file-before-ftp-is-complete
        //  uri="file:pathName?initialDelay=10s&amp;move=ARCHIVE&amp;sortBy=ignoreCase:file:name&amp;readLock=fileLock&amp;readLockCheckInterval=5000&amp;readLockTimeout=10m&amp;filter=#FileFilter
        // https://camel.apache.org/components/4.0.x/sftp-component.html#_component_options
        
        String sftpURI = "sftp:" + sFtpHost + ":"+sFtpPort+sFtpDir+"?readLock=changed&readLockCheckInterval=10000&readLockTimeout=10m&stepwise=false&username="+sFtpUser+"&password=RAW("+sFtpPassword+")&disconnect=false&delete="+sFtpDeleteFile+"&autoCreateKnownHostsFile=true";

        // Depracated now using move=done in sFTP camel URI // String sftpURI_arch = "sftp:" + sFtpHost + ":"+sFtpPort+ArchiveDir+"?username="+sFtpUser+"&password=RAW("+sFtpPassword+")&disconnect=false&delete="+sFtpDeleteFile+"&knownHostsFile=/tmp/sapqual6_public_key";
        //String sftpURI = "sftp:" + sFtpHost + ":"+sFtpPort+sFtpDir+"?username="+sFtpUser+"&password=RAW(osbsap$23)&disconnect=false&delete="+sFtpDeleteFile+"&knownHostsFile=/tmp/sapqual6_public_key";

        String ARIBA_UPLOAD_URL     = System.getenv().getOrDefault("ARIBA_UPLOAD_URL", "https://10.96.16.101/Buyer/fileupload?partition=par1iam");
        //String ARIBA_UPLOAD_URL     = System.getenv().getOrDefault("ARIBA_UPLOAD_URL", "http://localhost:3000/upload");
        
        onException(HttpOperationFailedException.class)
            .continued(true) // continue processing the route after catching the exception
            .log("Muis Exception occurred: ${exception.message}") // log the exception message
            .process(Application::muisGetHttpException);

        from(sftpURI) // fake sFTP : docker run -p 22:22 -d atmoz/sftp foo:pass:::upload // &resumeDownload=true&streamDownload=false
            .routeId("muis_route1_ftp_listener")
            .log("MUIS SFTP adapter version tag iam_5.0-zip")
            .log("MUIS : ${file:name} downloaded from sftp")
            .to("file:/tmp/ariba")
            .process(Application::checkIfIncrementalLoad);

        from("file:/tmp/ariba/renamed") // This route will fire after muisRenameFile() is called by checkIfIncrementalLoad()
            .routeId("muis_route2_zip")
            .marshal()
            
            .zipFile()
            .log("MUIS : ${file:name} compressed using zipFile() ")
            
            //.gzipDeflater() // Previous SOA PTF used to use XOP/MTOM compression of SOAP messages
            //.log("MUIS : ${file:name} compressed using gzipDeflater() ")
            
            //.zipDeflater()
            //.log("MUIS : ${file:name} compressed using zipDeflater() ")
            //.process(ZipFromBinary::zipfile) @TODO
            //.log("MUIS : ${file:name} compressed using ZipFromBinary omnidata class ")
            .log("MUIS : to /tmp/ariba/${file:name}")
            .to("file:/tmp/ariba?fileName=${file:name}") // /tmp in localhost / local container
            //.to("file:/tmp/ariba") // /tmp in localhost / local container
            
            //.multicast() // https://camel.apache.org/components/4.0.x/eips/multicast-eip.html
            //.stopOnException() // https://camel.apache.org/components/4.0.x/eips/multicast-eip.html#_stop_processing_in_case_of_exception : stop processing further routes (if exepection), and let the exception be propagated back
            //.parallelProcessing() // A multicast option that we disable for now, as it seems to cause trouble (File received partially! @ ARIBA side and @ STP archive side as well)
            .to("direct:muis_upload_toAriba")
            //.to("direct:muis_archive_file")
        .end();

        from("direct:muis_upload_toAriba")
            .routeId("muis_route3_uploadAriba")
            //.setHeader("MUIS_FULLLOAD", constant("true"))
            .process(Application::toMultipart)
            .log("MUIS : POSTing ${header.CamelFileName} to /upload")
        
            .setHeader(Exchange.HTTP_METHOD, constant("POST"))
            .setHeader(Exchange.CONTENT_TYPE, constant("multipart/form-data"))
            
            //.to("http://localhost:3000/upload")
            .log("Using ARIBA Upload URL : " + ARIBA_UPLOAD_URL)
            // .process(Application::dumpMessageFromExchange)
            .toD(ARIBA_UPLOAD_URL)
                .convertBodyTo(String.class)
                .log(LoggingLevel.INFO, "ARIBA Backend response headers : \n${in.headers} \n")
                .log(LoggingLevel.INFO, "ARIBA Backend response body : \n${body} \n")
            .choice()
				.when(simple("${header.CamelHttpResponseCode} == '200'"))
					.log(LoggingLevel.INFO, "File successfully uploaded to Ariba. header.CamelHttpResponseCode :  ${header.CamelHttpResponseCode}\n\n")
				.otherwise()
                    .log(LoggingLevel.ERROR, "Failed uploading file to Ariba\n\n")
            .end()
            //.process(Application::muisDeleteFile)
        .end();

        // from("direct:muis_archive_file")
        //     .log("ARIBA_HTTP_RESPONSE_CODE : ${in.headers.ARIBA_HTTP_RESPONSE_CODE}")
        //     .log("Archiving file to : " + ArchiveDir + " in sftp workspace")
        //     .to(sftpURI_arch) // Archive file, in sftp server @todo need an sftp camel connector here as well
        // .end();
    }

    public static void checkIfIncrementalLoad(final Exchange exchange) throws IOException {

        final Logger LOGGER =  LoggerFactory.getLogger(Application.class.getName());
        String filename = exchange.getIn().toString();
        LOGGER.info("Muis checkIfIncrementalLoad(), filename : " + filename);

        if (filename.substring(0, 3).equals("INC")) {
            exchange.getIn().setHeader("MUIS_FULLLOAD", "false");
            LOGGER.info("Muis checkIfIncrementalLoad() : Setting MUIS_FULLLOAD header to false");
            Application:muisRenameFile(filename, false);
            
            File file = new File("/tmp/ariba/" + filename.substring(3) +".inc"); // This will act as a flag to tell the toMultipart() that it's a INC load
            file.createNewFile();

        } else {
            exchange.getIn().setHeader("MUIS_FULLLOAD", "true");
            LOGGER.info("Muis checkIfIncrementalLoad() : Setting MUIS_FULLLOAD header to true");
            Application:muisRenameFile(filename, true);
            File file = new File("/tmp/ariba/" + filename + ".full");
            file.createNewFile();
        }
    }

    public static void  toMultipart(final Exchange exchange) {

        final Logger LOGGER =  LoggerFactory.getLogger(Application.class.getName());

        MultipartEntityBuilder entity = MultipartEntityBuilder.create();

        //LOGGER.info(exchange.getOut().getHeaders().toString());
        //String FullLoad = exchange.getIn().getHeader("MUIS_FULLLOAD").toString();

        String filename = exchange.getIn().toString();
        LOGGER.info("Muis toMultipart(), filename : " + filename);

        File f = new File("/tmp/ariba/" + filename + ".inc");
        String FullLoad = (f.exists() && !f.isDirectory())? "false" : "true";
        
        Path path = Paths.get("/tmp/ariba/" + filename + ".zip");
        
        try {
            // Encode the file as a multipart entity…
            entity.addBinaryBody(
                    "content",
                    Files.readAllBytes(path),
                    ContentType.create("multipart/form-data","UTF-8"),
                    filename + ".zip"
                );
            
        } catch (IOException e) {
            LOGGER.info("Muis issue while reading " + path);
            e.printStackTrace();
        }
        
        entity.addTextBody("sharedsecret", ARIBA_SHARED_SECRET);
        entity.addTextBody("event", "Import Batch Data");
        entity.addTextBody("fullload", FullLoad);
      
        // Set multipart entity as the outgoing message’s body…
        LOGGER.info("Muis toMultipart(), content : mimeType='multipart/form-data', charset='UTF-8', path : /tmp/ariba/" + filename + ".zip");
        LOGGER.info("Muis toMultipart(), sharedsecret : *******");
        LOGGER.info("Muis toMultipart(), event : Import Batch Data");
        LOGGER.info("Muis toMultipart(), FullLoad : " + FullLoad);
        exchange.getOut().setBody(entity.build());
    }

    public static void muisGetHttpException(Exchange exchange) {
            // e won't be null because we only catch HttpOperationFailedException;
            // otherwise, we'd need to check for null.
            final HttpOperationFailedException e =
                    exchange.getProperty(Exchange.EXCEPTION_CAUGHT, HttpOperationFailedException.class);
            // Do something with the responseBody
            System.out.println(" - muisGetHttpException getResponseBody() : " + e.getResponseBody() + "\n");
            System.out.println(" - muisGetHttpException getStatusCode() : " + e.getStatusCode() + "\n");
            System.out.println(" - muisGetHttpException getResponseHeaders() : " + e.getResponseHeaders() + "\n");
    }

    public static String muisRenameFile(String filename, boolean full_load) {

        final Logger LOGGER =  LoggerFactory.getLogger(Application.class.getName());
        String oldFilePath = "/tmp/ariba/" + filename;
        String newFilePath = "/tmp/ariba/renamed/" + ( full_load ? filename : filename.substring(3) );
        
        // Create File objects for both old and new files
           Path oldFile = Paths.get(oldFilePath); 
           Path newFile = Paths.get(newFilePath);
           try {
           Files.move(oldFile,newFile);
           LOGGER.info("muisRenameFile() : Moving file " + oldFile + " to " + newFile); 
            } 
            catch (IOException e) { 
                LOGGER.error("muisRenameFile() : operation failed " + e.getMessage()); 
            } 
 
        return newFilePath;
    }

    public static void muisDeleteFile(Exchange exchange) {

        Application:emptyFolder(new File("/tmp/ariba/"));
    }

    public static void emptyFolder(File folder) {
        File[] files = folder.listFiles();
        if(files!=null) { //some JVMs return null for empty dirs
            for(File f: files) {
                if(!f.isDirectory()) {
                     f.delete();
                }
            }
        }
        folder.delete();
    }
}