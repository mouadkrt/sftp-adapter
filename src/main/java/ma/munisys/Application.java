package ma.munisys;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.logging.LogLevel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
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

        //from("sftp:130.24.80.193:22?username=sftpuser&password=123.pwdMuis&disconnect=false&delete=true");
        String sFtpHost     = System.getenv().getOrDefault("sFTP_HOST", "localhost");
        String sFtpPort     = System.getenv().getOrDefault("sFTP_PORT", "22");
        String sFtpDir      = System.getenv().getOrDefault("sFTP_DIR", "/upload");
        String sFtpUser     = System.getenv().getOrDefault("sFTP_USER", "osbsap");

        String sFtpPassword = "osbsap$23"; //System.getenv().getOrDefault("sFTP_PWD", "osbsap$23"); // TEST
        //String sFtpPassword = "ariba$$23"; //System.getenv().getOrDefault("sFTP_PWD", "ariba$$23"); // PROD et PRA

        String sFtpDeleteFile = System.getenv().getOrDefault("sFTP_DELETE_FILE", "true"); // True or false
        String ArchiveDir = System.getenv().getOrDefault("ARCHIVE_DIR", "/upload/sftp_archive"); // True or false
        String sftpURI = "sftp:" + sFtpHost + ":"+sFtpPort+sFtpDir+"?username="+sFtpUser+"&password=RAW("+sFtpPassword+")&disconnect=false&delete="+sFtpDeleteFile+"&knownHostsFile=/tmp/sapqual6_public_key";
        String sftpURI_arch = "sftp:" + sFtpHost + ":"+sFtpPort+ArchiveDir+"?username="+sFtpUser+"&password=RAW("+sFtpPassword+")&disconnect=false&delete="+sFtpDeleteFile+"&knownHostsFile=/tmp/sapqual6_public_key";
        //String sftpURI = "sftp:" + sFtpHost + ":"+sFtpPort+sFtpDir+"?username="+sFtpUser+"&password=RAW(osbsap$23)&disconnect=false&delete="+sFtpDeleteFile+"&knownHostsFile=/tmp/sapqual6_public_key";

        String ARIBA_UPLOAD_URL     = System.getenv().getOrDefault("ARIBA_UPLOAD_URL", "https://10.96.16.101/Buyer/fileupload?partition=par1iam");
        
        from(sftpURI) // fake sFTP : docker run -p 22:22 -d atmoz/sftp foo:pass:::upload // &resumeDownload=true&streamDownload=false
            .log("MUIS SFTP adapter version tag iam_2.1-rec")
            .log("MUIS : ${file:name} downloaded from sftp")
            .marshal()
            .zipFile() // Previous SOA PTF used to use XOP/MTOM compression of SOAP messages
            .log("MUIS : ${file:name} compressed")
            .to("file:/tmp") // /tmp in localhost / local container
            
            .multicast() // https://camel.apache.org/components/4.0.x/eips/multicast-eip.html
            .stopOnException() // https://camel.apache.org/components/4.0.x/eips/multicast-eip.html#_stop_processing_in_case_of_exception : stop processing further routes (if exepection), and let the exception be propagated back
            //.parallelProcessing() // A multicast option that we disable for now, as it seems to cause trouble (File received partially! @ ARIBA side and @ STP archive side as well)
            .to("direct:muis_zip_upload_toAriba","direct:muis_archive_file")
        .end();

        from("direct:muis_zip_upload_toAriba")
            .process(Application::toMultipart)
            .log("MUIS : POSTing ${header.CamelFileName} to /upload")
        
            .setHeader(Exchange.HTTP_METHOD, constant("POST"))
            .setHeader(Exchange.CONTENT_TYPE, constant("multipart/form-data"))
            
            //.to("http://localhost:3000/upload")
            .log("Using ARIBA Upload URL : " + ARIBA_UPLOAD_URL)
            .toD(ARIBA_UPLOAD_URL)
            .log("MUIS : HTTP response status: ${header.CamelHttpResponseCode}")
            .log("MUIS : HTTP response body:\n${body}")
        .end();

        from("direct:muis_archive_file")
            .log("Archiving file to : " + ArchiveDir + " in sftp workspace")
            .to(sftpURI_arch) // Archive file, in sftp server @todo need an sftp camel connector here as well
        .end();

    }

    public static void  toMultipart(final Exchange exchange) {

        final Logger LOGGER =  LoggerFactory.getLogger(Application.class.getName());

        MultipartEntityBuilder entity = MultipartEntityBuilder.create();

        String filename = exchange.getIn().toString();
        Path path = Paths.get("/tmp/" + filename + ".zip");
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
        entity.addTextBody("fullload", "true");
      
        // Set multipart entity as the outgoing message’s body…
        exchange.getOut().setBody(entity.build());
    }

}