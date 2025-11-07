package org.jclouds.examples.aws.s3;

import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;

import org.jclouds.ContextBuilder;
import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.BlobStoreContext;
import org.jclouds.blobstore.domain.Blob;
import org.jclouds.blobstore.options.ListContainerOptions;
import org.jclouds.io.payloads.StringPayload;
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule;

import com.google.inject.Module;
import com.google.common.collect.ImmutableSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3;

import static org.jclouds.s3.reference.S3Constants.PROPERTY_S3_VIRTUAL_HOST_BUCKETS;

import org.junit.Test;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;

import java.time.Duration;
import java.util.Properties;

public class JcloudsS3LocalStackTest {

    private static final String PROVIDER = "aws-s3"; // Jclouds provider for S3

    private static final Logger logger = LoggerFactory.getLogger(JcloudsS3LocalStackTest.class.getName());

    @Test
    public void testS3WithLocalStack() throws Exception {
        logger.info("Running Jclouds S3 LocalStack test...");

        LocalStackContainer localstack = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:s3-latest"))
            .withServices(S3)
            .withStartupTimeout(Duration.ofMinutes(3));

        try {
            logger.info("Starting LocalStack testcontainer");
            Startables.deepStart(localstack).get();
            String endpoint = localstack.getEndpointOverride(S3).toString();
            String accessKey = localstack.getAccessKey();
            String secretKey = localstack.getSecretKey();

            // Uncomment to use manually started LocalStack Docker container
            // String endpoint = "http://localhost:4566"; // Change to your LocalStack S3 endpoint/port
            // String accessKey = "test"; // LocalStack default
            // String secretKey = "test"; // LocalStack default

            // !! Important Note !! Override properties to force path-style access and avoid
            // this issue: java.net.UnknownHostException: <AWS S3 bucket name>.localhost
            Properties blobStoreProperties = new Properties();
            blobStoreProperties.setProperty(PROPERTY_S3_VIRTUAL_HOST_BUCKETS, "false");

            BlobStoreContext blobStoreContext = ContextBuilder.newBuilder(PROVIDER)
                    .endpoint(endpoint)
                    .credentials(accessKey, secretKey)
                    .overrides(blobStoreProperties)
                    .modules(ImmutableSet.<Module>of(new SLF4JLoggingModule()))
                    .buildView(BlobStoreContext.class);
            BlobStore blobStore = blobStoreContext.getBlobStore();

            String bucketName = "testbucket";
            String objectKey = "jclouds/hello.txt";
            String content = "Hello, jclouds S3 on LocalStack!";

            // Create bucket (container)
            logger.info("Creating bucket: " + bucketName);
            blobStore.createContainerInLocation(null, bucketName);

            // Upload a simple text object
            logger.info("Uploading object: " + objectKey);
            Blob blob = blobStore.blobBuilder(objectKey)
                    .payload(new StringPayload(content))
                    .build();
            blobStore.putBlob(bucketName, blob);

            // List objects in the bucket
            logger.info("Available objects:");
            blobStore.list(bucketName, ListContainerOptions.Builder.recursive())
                    .stream()
                    .map(storageMetadata -> storageMetadata.getName())
                    .forEach(name -> logger.info("- " + name));

            logger.info("Searching for uploaded object...");
            boolean found = blobStore.list(bucketName, ListContainerOptions.Builder.recursive()).stream()
                    .anyMatch(meta -> meta.getName().equals(objectKey));
            assertTrue("Blob store bucket should contain the object: " + objectKey, found);

            // Download and print the object
            logger.info("Downloading object: " + objectKey);
            Blob downloaded = blobStore.getBlob(bucketName, objectKey);
            assertNotNull("Blob should not be null", downloaded);

            String downloadedContent = new String(downloaded.getPayload().openStream().readAllBytes());
            logger.info("Downloaded content: " + downloadedContent);

            // Clean up
            logger.info("Cleaning up - removing object: " + objectKey);
            blobStore.removeBlob(bucketName, objectKey);
            logger.info("Deleting bucket: " + bucketName);
            blobStore.deleteContainer(bucketName);

            blobStoreContext.close();
            logger.info("Jclouds S3 LocalStack test completed successfully!");
        } finally {
            logger.info("Stopping LocalStack testcontainer");
            if (localstack.isRunning()) {
                localstack.stop();
            }
            localstack.close();
        }
    }
}
