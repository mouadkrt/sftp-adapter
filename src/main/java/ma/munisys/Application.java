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
        from("sftp:localhost:22/upload?username=foo&password=pass&disconnect=false&delete=true") // fake sFTP : docker run -p 22:22 -d atmoz/sftp foo:pass:::upload // &resumeDownload=true&streamDownload=false
            .marshal().zipFile() // Previous SOA PTF used to use XOP/MTOM compression of SOAP messages
            .to("file:/tmp")
            .log("Downloaded file ${file:name} complete.")
            .multicast()
            .parallelProcessing()
            .to("direct:muis_archive_file","direct:muis_zip_upload_toAriba")
        .end();

        from("direct:muis_archive_file")
            .to("file:/sftp_archive") // Archive file, will get logged on C:\sftp_archive on Windows
        .end();

        from("direct:muis_zip_upload_toAriba")
            .process(Application::toMultipart)
            .log("POSTing ${header.CamelFileName} to /upload")
        
            .setHeader(Exchange.HTTP_METHOD, constant("POST"))
            .setHeader(Exchange.CONTENT_TYPE, constant("multipart/form-data"))
            
            .to("http://localhost:3000/upload")

            .log("HTTP response status: ${header.CamelHttpResponseCode}")
            .log("HTTP response body:\n${body}")
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
                    "iam_sap_file",
                    Files.readAllBytes(path),
                    ContentType.create("multipart/form-data","UTF-8"),
                    filename + ".zip"
                );
            
        } catch (IOException e) {
            LOGGER.info("Muis issue while reading " + path);
            e.printStackTrace();
        }
      
        // Set multipart entity as the outgoing message’s body…
        exchange.getOut().setBody(entity.build());
    }

}