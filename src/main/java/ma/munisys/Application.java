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

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Override
    public void configure() {

        //from("sftp:130.24.80.193:22?username=sftpuser&password=123.pwdMuis&disconnect=false&delete=true");
        String sFtpHost     = System.getenv().getOrDefault("sFTP_HOST", "localhost");
        String sFtpPort     = System.getenv().getOrDefault("sFTP_PORT", "22");
        String sFtpDir      = System.getenv().getOrDefault("sFTP_DIR", "/upload");
        String sFtpUser     = System.getenv().getOrDefault("sFTP_USER", "foo");
        String sFtpPassword = System.getenv().getOrDefault("sFTP_PWD", "pass");
        String sFtpDeleteFile = System.getenv().getOrDefault("sFTP_DELETE_FILE", "false"); // True or false
        String ArchiveDir = System.getenv().getOrDefault("ARCHIVE_DIR", "/sftp_archive"); // True or false
        String sftpURI = "sftp:" + sFtpHost + ":"+sFtpPort+sFtpDir+"?username="+sFtpUser+"&password="+sFtpPassword+"&disconnect=false&delete="+sFtpDeleteFile;

        String ARIBA_UPLOAD_URL = System.getenv().getOrDefault("ARIBA_UPLOAD_URL", "https://10.96.16.101/Buyer/fileupload?partition=par1iam");
        from(sftpURI) // fake sFTP : docker run -p 22:22 -d atmoz/sftp foo:pass:::upload // &resumeDownload=true&streamDownload=false
            .log("MUIS : ${file:name} downloaded from sftp")
            .marshal()
            .zipFile() // Previous SOA PTF used to use XOP/MTOM compression of SOAP messages
            .log("MUIS : ${file:name} compressed")
            .to("file:/tmp")
            
            .multicast()
            .parallelProcessing()
            .to("direct:muis_archive_file","direct:muis_zip_upload_toAriba")
        .end();

        from("direct:muis_archive_file")
            .log("Archiving file to : " + ArchiveDir)
            .to("file:"+ArchiveDir) // Archive file, will get logged on C:\sftp_archive on Windows
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
        
        entity.addTextBody("sharedsecret", "rabat2121");
        entity.addTextBody("event", "Import Batch Data");
        entity.addTextBody("fullload", "true");
      
        // Set multipart entity as the outgoing message’s body…
        exchange.getOut().setBody(entity.build());
    }

}