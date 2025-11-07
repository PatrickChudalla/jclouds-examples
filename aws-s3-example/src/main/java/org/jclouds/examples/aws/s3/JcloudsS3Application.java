package org.jclouds.examples.aws.s3;

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

public class JcloudsS3Application {

    // This is the only place where this application refers to a specific cloud provider (AWS S3 in this case)
    private static final String PROVIDER = "aws-s3";

    private static final Logger logger = LoggerFactory.getLogger(JcloudsS3Application.class.getName());

    public static void main(String[] args) {
        logger.info("Running Jclouds S3 real world example...");

        try {
            // Create BlobStore context for real AWS S3
            BlobStoreContext blobStoreContext = ContextBuilder.newBuilder(PROVIDER)
                .modules(ImmutableSet.<Module>of(new SLF4JLoggingModule()))
                .buildView(BlobStoreContext.class);
            BlobStore blobStore = blobStoreContext.getBlobStore();

            String bucketName = args.length > 0 ? args[0] : "jclouds-playground";
            String objectKey = "jclouds/hello.txt";
            String content = "Hello, jclouds S3 on real AWS!";

            // Create bucket (container) in the specified region
            // Note: Bucket names must be globally unique across all AWS accounts
            logger.info("Checking if bucket exists: " + bucketName);
            if (!blobStore.containerExists(bucketName)) {
                logger.info("Creating bucket: " + bucketName);
                blobStore.createContainerInLocation(null, bucketName);

                // Wait a moment for bucket creation to propagate
                Thread.sleep(2000);
            } else {
                logger.info("Using existing bucket: " + bucketName);
            }

            // Upload a simple text object
            logger.info("Uploading object: " + objectKey);
            Blob blob = blobStore.blobBuilder(objectKey)
                    .payload(new StringPayload(content))
                    .build();
            blobStore.putBlob(bucketName, blob);

            // List objects in the bucket
            // logger.info("Available objects:");
            // blobStore.list(bucketName, ListContainerOptions.Builder.recursive())
            // .stream()
            // .map(storageMetadata -> storageMetadata.getName())
            // .forEach(name -> logger.info(" - " + name));

            logger.info("Searching for uploaded object...");
            boolean found = blobStore.list(bucketName, ListContainerOptions.Builder.recursive()).stream()
                    .anyMatch(meta -> meta.getName().equals(objectKey));
            if (found) {
                logger.info("Blob store bucket contains the object: " + objectKey);
            } else {
                throw new RuntimeException("Blob store bucket does not contain expected object: " + objectKey);
            }

            // Download and print the object
            logger.info("Downloading object: " + objectKey);
            Blob downloaded = blobStore.getBlob(bucketName, objectKey);
            if (downloaded != null) {
                String downloadedContent = new String(downloaded.getPayload().openStream().readAllBytes());
                logger.info("Downloaded content: " + downloadedContent);
            } else {
                throw new RuntimeException("Blob not found!");
            }

            // Clean up
            // Optionally delete the object (uncomment if you want to clean up)
            // logger.info("Cleaning up - removing object: " + objectKey);
            // blobStore.removeBlob(bucketName, objectKey);

            // Optionally delete the bucket (uncomment if you want to clean up completely)
            // Note: Only delete if the bucket is empty and you created it for trying out
            // logger.info("Deleting bucket: " + bucketName);
            // blobStore.deleteContainer(bucketName);

            blobStoreContext.close();
            logger.info("Jclouds S3 real world example completed successfully!");
        } catch (Exception e) {
            logger.error("ERROR: " + e.getMessage());
            System.exit(1);
        }
    }
}
