# Azure Blob Storage Example with jClouds

This example demonstrates how to use Apache jClouds to interact with Azure Blob Storage using different authentication methods.

## Prerequisites

- Java 11 or higher
- Azure Storage Account with appropriate credentials
- Docker (for running tests with Azurite)

## Configuration

Before running the application, copy `gradle.properties.template` to `gradle.properties` and customize it with your own values:

```bash
cp gradle.properties.template gradle.properties
```

Edit `gradle.properties` to set your Azure Storage account name, access keys, and container names. The `gradle.properties` file is gitignored to prevent committing sensitive information.

## Running the Application

The application supports multiple authentication methods that can be selected using Gradle project properties.

**IMPORTANT**: Always use the project-specific task notation (`:azure-blob-example:run`) to avoid configuration conflicts with other example projects.

### Available Authentication Methods

#### 1. Connection String (`connectionString`)

Uses an Azure Storage connection string which contains both the account name and access key.

```bash
# Use configuration from gradle.properties
./gradlew :azure-blob-example:run -PauthMethod=connectionString
```

#### 2. Account Key (`accountKey`) - Default

Uses Azure Storage account name and key separately via environment variables.

```bash
# Use default account key configuration (default authentication method)
./gradlew :azure-blob-example:run

# Or explicitly specify the authentication method
./gradlew :azure-blob-example:run -PauthMethod=accountKey
```

### Configuration

All authentication parameters are configured in `gradle.properties`. Copy `gradle.properties.template` to `gradle.properties` and customize with your values:

**Connection String Authentication:**
- **connectionString.value**: Full Azure Storage connection string
- **connectionString.container**: Blob container name

**Account Key Authentication:**
- **accountKey.accountName**: Azure Storage account name
- **accountKey.accountKey**: Azure Storage account key
- **accountKey.container**: Blob container name

## Running Tests

The project includes integration tests that use Azurite (Azure Storage Emulator) to simulate Azure Blob Storage locally.

### Run all tests

```bash
./gradlew :azure-blob-example:test
```

### Run tests with clean build

```bash
./gradlew :azure-blob-example:cleanTest :azure-blob-example:test --rerun-tasks
```

### Test Configuration

Tests automatically use the Docker environment configured in your current Docker context. If you're using WSL2 or a non-default Docker setup, the build automatically detects and configures the appropriate Docker host.

## Logging Configuration

### Application Logging

Application logging is configured in `src/main/resources/logback.xml` with INFO level by default.

### Test Logging

Test logging is configured in `src/test/resources/logback-test.xml`:

- TestContainers: `INFO` level
- Docker client: `WARN` level
- Application code: `INFO` level

To enable detailed jClouds and Azure SDK logging for tests, uncomment the relevant logger configurations in `logback-test.xml`.

## Project Structure

```
azure-blob-example/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── org/jclouds/examples/azure/blob/
│   │   │       └── JcloudsAzureBlobApplication.java
│   │   └── resources/
│   │       └── logback.xml
│   └── test/
│       ├── java/
│       │   └── org/jclouds/examples/azure/blob/
│       │       └── JcloudsAzureBlobAzuriteTest.java
│       └── resources/
│           └── logback-test.xml
├── build.gradle
├── gradle.properties.template
└── README.md
```

## What the Application Does

The `JcloudsAzureBlobApplication` demonstrates basic Azure Blob Storage operations:

1. Creates a BlobStore context with Azure credentials
2. Checks if a container exists, creates it if necessary
3. Uploads a text blob to the container
4. Lists and verifies the blob exists
5. Downloads and displays the blob content
6. Optionally cleans up resources

## What the Tests Do

The `JcloudsAzureBlobAzuriteTest` validates Azure Blob Storage operations against a local Azurite container:

1. Starts an Azurite container (Azure Storage Emulator)
2. Creates a test container
3. Uploads a blob to the container
4. Lists blobs in the container
5. Downloads and verifies the blob
6. Cleans up by deleting the blob and container

## Troubleshooting

### Docker Connection Issues

If tests fail with Docker connection errors, ensure:

1. Docker daemon is running
2. Your Docker context is properly configured
3. If using WSL2, the Docker daemon is accessible via TCP (port 2375)

### Authentication Issues

If the application fails to authenticate:

1. **Connection String**: Verify the connection string format and credentials
2. **Account Key**: Check that account name and key are correct
3. **Permissions**: Ensure the account has appropriate permissions for the container

### Container Naming Rules

Azure container names must follow these rules:

- Must be lowercase
- Must be between 3 and 63 characters
- Must start with a letter or number
- Can contain only letters, numbers, and hyphens
- Hyphens must be preceded and followed by a letter or number

### Debug Mode

To see detailed jClouds and Azure SDK logging, uncomment the relevant logger configurations in `src/main/resources/logback.xml`:

```xml
<logger name="org.jclouds" level="DEBUG" />
<logger name="jclouds.wire" level="DEBUG" />
<logger name="jclouds.headers" level="DEBUG" />
<logger name="com.azure" level="DEBUG"/>
```

This will output detailed information about credential resolution and Azure API calls.

## Additional Notes

### Azurite vs Real Azure Storage

- **Azurite** is a local Azure Storage emulator for development and testing
- It uses well-known development credentials that are publicly available
- For production, always use real Azure Storage accounts with proper credentials

### jClouds Provider Abstraction

The code uses jClouds' provider-agnostic BlobStore API, making it easy to switch between cloud providers. Only the provider string (`"azureblob"`) is cloud-specific, while all other operations use the standard BlobStore interface.
