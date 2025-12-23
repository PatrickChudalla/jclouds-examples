package org.jclouds.examples.azure.blob;

import org.testcontainers.containers.GenericContainer;
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

import org.junit.Test;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;

import java.time.Duration;

public class JcloudsAzureBlobAzuriteTest {

    private static final String PROVIDER = "azureblob"; // Jclouds provider for Azure Blob Storage

    private static final Logger logger = LoggerFactory.getLogger(JcloudsAzureBlobAzuriteTest.class.getName());

    // Azurite default credentials (well-known development credentials)
    private static final String AZURITE_ACCOUNT_NAME = "devstoreaccount1";
    private static final String AZURITE_ACCOUNT_KEY = "Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==";
    private static final int AZURITE_BLOB_PORT = 10000;

    @Test
    public void testAzureBlobWithAzurite() throws Exception {
        logger.info("Running Jclouds Azure Blob Storage Azurite test...");

        // Start Azurite container (Azure Storage Emulator)
        GenericContainer<?> azurite = new GenericContainer<>(
            DockerImageName.parse("mcr.microsoft.com/azure-storage/azurite:latest"))
            .withExposedPorts(AZURITE_BLOB_PORT)
            .withCommand("azurite-blob", "--blobHost", "0.0.0.0")
            .withStartupTimeout(Duration.ofMinutes(3));

        try {
            logger.info("Starting Azurite testcontainer");
            Startables.deepStart(azurite).get();

            String host = azurite.getHost();
            Integer port = azurite.getMappedPort(AZURITE_BLOB_PORT);
            String endpoint = String.format("http://%s:%d/%s", host, port, AZURITE_ACCOUNT_NAME);

            logger.info("Azurite endpoint: " + endpoint);

            // Uncomment to use manually started Azurite Docker container
            // String endpoint = "http://localhost:10000/devstoreaccount1";

            BlobStoreContext blobStoreContext = ContextBuilder.newBuilder(PROVIDER)
                    .endpoint(endpoint)
                    .credentials(AZURITE_ACCOUNT_NAME, AZURITE_ACCOUNT_KEY)
                    .modules(ImmutableSet.<Module>of(new SLF4JLoggingModule()))
                    .buildView(BlobStoreContext.class);
            BlobStore blobStore = blobStoreContext.getBlobStore();

            String containerName = "testcontainer";
            String blobKey = "jclouds/hello.txt";
            String content = "Hello, jclouds Azure Blob Storage on Azurite!";

            // Create container
            logger.info("Creating container: " + containerName);
            blobStore.createContainerInLocation(null, containerName);

            // Upload a simple text blob
            logger.info("Uploading blob: " + blobKey);
            Blob blob = blobStore.blobBuilder(blobKey)
                    .payload(new StringPayload(content))
                    .build();
            blobStore.putBlob(containerName, blob);

            // List blobs in the container
            logger.info("Available blobs:");
            blobStore.list(containerName, ListContainerOptions.Builder.recursive())
                    .stream()
                    .map(storageMetadata -> storageMetadata.getName())
                    .forEach(name -> logger.info("- " + name));

            logger.info("Searching for uploaded blob...");
            boolean found = blobStore.list(containerName, ListContainerOptions.Builder.recursive()).stream()
                    .anyMatch(meta -> meta.getName().equals(blobKey));
            assertTrue("Blob store container should contain the blob: " + blobKey, found);

            // Download and print the blob
            logger.info("Downloading blob: " + blobKey);
            Blob downloaded = blobStore.getBlob(containerName, blobKey);
            assertNotNull("Blob should not be null", downloaded);

            String downloadedContent = new String(downloaded.getPayload().openStream().readAllBytes());
            logger.info("Downloaded content: " + downloadedContent);

            // Clean up
            logger.info("Cleaning up - removing blob: " + blobKey);
            blobStore.removeBlob(containerName, blobKey);
            logger.info("Deleting container: " + containerName);
            blobStore.deleteContainer(containerName);

            blobStoreContext.close();
            logger.info("Jclouds Azure Blob Storage Azurite test completed successfully!");
        } finally {
            logger.info("Stopping Azurite testcontainer");
            if (azurite.isRunning()) {
                azurite.stop();
            }
            azurite.close();
        }
    }
}
