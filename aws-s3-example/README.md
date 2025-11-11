# AWS S3 Example with jClouds

This example demonstrates how to use Apache jClouds to interact with AWS S3 using different authentication methods.

## Prerequisites

- Java 11 or higher
- AWS CLI configured with appropriate credentials
- Docker (for running tests with LocalStack)

## Configuration

Before running the application, copy `gradle.properties.template` to `gradle.properties` and customize it with your own values:

```bash
cp gradle.properties.template gradle.properties
```

Edit `gradle.properties` to set your AWS profiles, bucket names, role ARNs, and web identity tokens. The `gradle.properties` file is gitignored to prevent committing sensitive information.

## Running the Application

The application supports multiple authentication methods that can be selected using Gradle project properties.

**IMPORTANT**: Always use the project-specific task notation (`:aws-s3-example:run`) to avoid configuration conflicts with other example projects.

### Available Authentication Methods

#### 1. Classic AWS CLI Profile (`classic`)

Uses static AWS CLI credentials from a classic CLI profile.

**Note**: Static credentials work for S3 operations, but NOT for RDS IAM authentication.

```bash
# Use default configuration from gradle.properties
./gradlew :aws-s3-example:run -PauthMethod=classic
```

#### 2. AWS SSO Profile (`sso`)

Uses temporary credentials from an SSO-enabled AWS CLI profile. Make sure to authenticate first:

```bash
# Authenticate with SSO first
aws sso login --profile my-sso-profile

# Run the application
./gradlew :aws-s3-example:run -PauthMethod=sso
```

#### 3. IRSA Simulation (`irsa`) - Default

Simulates the IAM Roles for Service Accounts (IRSA) environment used in Kubernetes/EKS. This is useful for testing EKS workloads locally.

```bash
# Make sure you're NOT logged in with AWS SSO (logout if needed)
aws sso logout

# Use default IRSA configuration (default authentication method)
./gradlew :aws-s3-example:run

# Or explicitly specify the authentication method
./gradlew :aws-s3-example:run -PauthMethod=irsa
```

### Configuration

All authentication parameters are configured in `gradle.properties`. Copy `gradle.properties.template` to `gradle.properties` and customize with your values:

**Classic Authentication:**
- **classic.awsProfile**: AWS CLI profile name
- **classic.bucket**: S3 bucket name

**SSO Authentication:**
- **sso.awsProfile**: AWS CLI profile name
- **sso.bucket**: S3 bucket name

**IRSA Authentication:**
- **irsa.region**: AWS region (e.g., `eu-central-1`)
- **irsa.roleArn**: IAM role ARN (e.g., `arn:aws:iam::123456789012:role/my-role`)
- **irsa.bucket**: S3 bucket name
- **irsa.webIdentityToken**: Web identity token from EKS service account

## Running Tests

The project includes integration tests that use LocalStack to simulate AWS S3 locally.

### Run all tests

```bash
./gradlew :aws-s3-example:test
```

### Run tests with clean build

```bash
./gradlew :aws-s3-example:cleanTest :aws-s3-example:test --rerun-tasks
```

### Test Configuration

Tests automatically use the Docker environment configured in your current Docker context. If you're using WSL2 or a non-default Docker setup, the build automatically detects and configures the appropriate Docker host.

## Logging Configuration

### Application Logging

Debug logging for AWS credentials can be controlled via the system property in the run configuration. By default, it's enabled.

### Test Logging

Test logging is configured in `src/test/resources/logback-test.xml`:

- TestContainers: `INFO` level
- Docker client: `WARN` level
- Application code: `INFO` level

To enable detailed jClouds and AWS SDK logging for tests, uncomment the relevant logger configurations in `logback-test.xml`.

## Project Structure

```
aws-s3-example/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── org/jclouds/examples/aws/s3/
│   │   │       └── JcloudsS3Application.java
│   │   └── resources/
│   │       └── logback.xml
│   └── test/
│       ├── java/
│       │   └── org/jclouds/examples/aws/s3/
│       │       └── JcloudsS3LocalStackTest.java
│       └── resources/
│           └── logback-test.xml
├── build.gradle
└── README.md
```

## What the Application Does

The `JcloudsS3Application` demonstrates basic S3 operations:

1. Creates a BlobStore context with AWS credentials
2. Lists and displays S3 buckets
3. Performs basic S3 operations on the specified bucket

## What the Tests Do

The `JcloudsS3LocalStackTest` validates S3 operations against a local LocalStack container:

1. Starts a LocalStack container with S3 service
2. Creates a test bucket
3. Uploads an object to S3
4. Lists objects in the bucket
5. Downloads and verifies the object
6. Cleans up by deleting the object and bucket

## Troubleshooting

### Docker Connection Issues

If tests fail with Docker connection errors, ensure:

1. Docker daemon is running
2. Your Docker context is properly configured
3. If using WSL2, the Docker daemon is accessible via TCP (port 2375)

### Authentication Issues

If the application fails to authenticate:

1. **CLI profile**: Check `~/.aws/credentials` and `~/.aws/config`
2. **SSO profile**: Run `aws sso login --profile <profile-name>`
3. **IRSA**: Ensure the web identity token and role ARN are valid

### Debug Mode

To see detailed credential resolution and AWS SDK logging, uncomment the relevant logger configurations in `src/main/resources/logback.xml`:

```xml
<logger name="org.jclouds" level="DEBUG" />
<logger name="software.amazon.awssdk" level="DEBUG"/>
<logger name="software.amazon.awssdk.auth.credentials" level="DEBUG"/>
```

This will output detailed information about credential provider resolution and AWS API calls.
