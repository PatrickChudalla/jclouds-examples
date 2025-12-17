package org.jclouds.examples.azure.blob;

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

public class JcloudsAzureBlobApplication {

    // This is the only place where this application refers to a specific cloud provider (Azure Blob Storage in this case)
    private static final String PROVIDER = "azureblob";

    private static final Logger logger = LoggerFactory.getLogger(JcloudsAzureBlobApplication.class.getName());

    public static void main(String[] args) {
        logger.info("Running Jclouds Azure Blob Storage real world example...");

        try {
            String containerName = args.length > 0 ? args[0] : "jclouds-playground";
            String blobKey = "jclouds/hello.txt";
            String content = "Hello, jclouds Azure Blob Storage!";

            // Read Azure credentials from environment variables
            String accountName = null;
            String accountKey = null;

            // Try connection string first
            String connectionString = System.getenv("AZURE_STORAGE_CONNECTION_STRING");
            if (connectionString != null && !connectionString.isEmpty()) {
                // Parse connection string to extract account name and key
                for (String part : connectionString.split(";")) {
                    String[] keyValue = part.split("=", 2);
                    if (keyValue.length == 2) {
                        if (keyValue[0].equals("AccountName")) {
                            accountName = keyValue[1];
                        } else if (keyValue[0].equals("AccountKey")) {
                            accountKey = keyValue[1];
                        }
                    }
                }
            } else {
                // Fall back to separate environment variables
                accountName = System.getenv("AZURE_STORAGE_ACCOUNT");
                accountKey = System.getenv("AZURE_STORAGE_KEY");
            }

            if (accountName == null || accountName.isEmpty()) {
                throw new IllegalArgumentException("Azure Storage account name not found. Please set AZURE_STORAGE_CONNECTION_STRING or AZURE_STORAGE_ACCOUNT environment variable.");
            }
            if (accountKey == null || accountKey.isEmpty()) {
                throw new IllegalArgumentException("Azure Storage account key not found. Please set AZURE_STORAGE_CONNECTION_STRING or AZURE_STORAGE_KEY environment variable.");
            }

            logger.info("=== Azure Blob Storage Connection Configuration ===");
            logger.info("- Provider: " + PROVIDER);
            logger.info("- Account: " + accountName);
            logger.info("- Container: " + containerName);
            logger.info("- Authentication Method: Account Key");
            logger.info("===================================================");

            // Create BlobStore context for Azure Blob Storage
            // jClouds Azure provider requires explicit credentials (identity = account name, credential = account key)
            BlobStoreContext blobStoreContext = ContextBuilder.newBuilder(PROVIDER)
                .credentials(accountName, accountKey)
                .modules(ImmutableSet.<Module>of(new SLF4JLoggingModule()))
                .buildView(BlobStoreContext.class);
            BlobStore blobStore = blobStoreContext.getBlobStore();

            // Create container (equivalent to AWS S3 bucket)
            // Note: Container names must be lowercase and follow Azure naming rules
            logger.info("Checking if container exists: " + containerName);
            if (!blobStore.containerExists(containerName)) {
                logger.info("Creating container: " + containerName);
                blobStore.createContainerInLocation(null, containerName);

                // Wait a moment for container creation to propagate
                Thread.sleep(2000);
            } else {
                logger.info("Using existing container: " + containerName);
            }

            // Upload a simple text blob
            logger.info("Uploading blob: " + blobKey);
            Blob blob = blobStore.blobBuilder(blobKey)
                    .payload(new StringPayload(content))
                    .build();
            blobStore.putBlob(containerName, blob);

            // List blobs in the container
            // logger.info("Available blobs:");
            // blobStore.list(containerName, ListContainerOptions.Builder.recursive())
            // .stream()
            // .map(storageMetadata -> storageMetadata.getName())
            // .forEach(name -> logger.info(" - " + name));

            logger.info("Searching for uploaded blob...");
            boolean found = blobStore.list(containerName, ListContainerOptions.Builder.recursive()).stream()
                    .anyMatch(meta -> meta.getName().equals(blobKey));
            if (found) {
                logger.info("Blob store container contains the blob: " + blobKey);
            } else {
                throw new RuntimeException("Blob store container does not contain expected blob: " + blobKey);
            }

            // Download and print the blob
            logger.info("Downloading blob: " + blobKey);
            Blob downloaded = blobStore.getBlob(containerName, blobKey);
            if (downloaded != null) {
                String downloadedContent = new String(downloaded.getPayload().openStream().readAllBytes());
                logger.info("Downloaded content: " + downloadedContent);
            } else {
                throw new RuntimeException("Blob not found!");
            }

            // Clean up
            // Optionally delete the blob (uncomment if you want to clean up)
            // logger.info("Cleaning up - removing blob: " + blobKey);
            // blobStore.removeBlob(containerName, blobKey);

            // Optionally delete the container (uncomment if you want to clean up completely)
            // Note: Only delete if the container is empty and you created it for trying out
            // logger.info("Deleting container: " + containerName);
            // blobStore.deleteContainer(containerName);

            blobStoreContext.close();
            logger.info("Jclouds Azure Blob Storage real world example completed successfully!");
        } catch (Exception e) {
            logger.error("ERROR: " + e.getMessage());
            System.exit(1);
        }
    }
}
