# AWS RDS Example with jClouds

This example demonstrates how to use Apache jClouds to connect to AWS RDS using different authentication methods, including IAM database authentication.

## Prerequisites

- Java 11 or higher
- AWS CLI configured with appropriate credentials (for SSO/IRSA methods)
- PostgreSQL or MySQL RDS instance configured for your authentication method
- For IAM authentication: RDS instance must have IAM database authentication enabled

## Configuration

Before running the application, copy `gradle.properties.template` to `gradle.properties` and customize it with your own values:

```bash
cp gradle.properties.template gradle.properties
```

Edit `gradle.properties` to set your database connection details, AWS profiles, role ARNs, and web identity tokens. The `gradle.properties` file is gitignored to prevent committing sensitive information.

## Running the Application

The application supports multiple authentication methods that can be selected using Gradle project properties.

**IMPORTANT**: Always use the project-specific task notation (`:aws-rds-example:run`) to avoid configuration conflicts with other example projects.

### Available Authentication Methods

#### 1. Direct Database Authentication (`direct`) - Default

Uses static database username/password authentication without AWS credentials or IAM database authentication. This is the traditional database authentication method.

```bash
# Use default configuration from gradle.properties
./gradlew :aws-rds-example:run

# Or explicitly specify the authentication method
./gradlew :aws-rds-example:run -PauthMethod=direct
```

#### 2. AWS SSO with IAM Database Authentication (`sso`)

Uses temporary AWS credentials from SSO to generate RDS IAM authentication tokens. Before using this method, ensure all prerequisites in [Requirements for IAM Database Authentication](#requirements-for-iam-database-authentication) are met.

**Important**: This method requires temporary AWS credentials with session tokens from AWS STS. Static AWS credentials do NOT work with RDS IAM authentication.

```bash
# Authenticate with SSO first
aws sso login --profile my-sso-profile

# Run the application
./gradlew :aws-rds-example:run -PauthMethod=sso
```

#### 3. IRSA Simulation with IAM Database Authentication (`irsa`)

Simulates the IAM Roles for Service Accounts (IRSA) environment used in Kubernetes/EKS. Uses web identity token federation to obtain temporary AWS credentials for RDS IAM authentication. Before using this method, ensure all prerequisites in [Requirements for IAM Database Authentication](#requirements-for-iam-database-authentication) are met.

**Important**: This method requires temporary AWS credentials with session tokens from AWS STS. Static AWS credentials do NOT work with RDS IAM authentication.

```bash
# Make sure you're NOT logged in with AWS SSO (logout if needed)
aws sso logout

# Run the application
./gradlew :aws-rds-example:run -PauthMethod=irsa
```

### Configuration

All authentication parameters are configured in `gradle.properties`. Copy `gradle.properties.template` to `gradle.properties` and customize with your values:

**Direct Authentication:**
- **direct.jdbcUrl**: JDBC connection string (e.g., `jdbc:postgresql://host:5432/database`)
- **direct.dbUsername**: Database username
- **direct.dbPassword**: Database password

**SSO Authentication:**
- **sso.awsProfile**: AWS CLI profile name for SSO authentication
- **sso.jdbcUrl**: JDBC connection string
- **sso.dbUsername**: Database username (must be configured for IAM auth in RDS)

**IRSA Authentication:**
- **irsa.region**: AWS region (e.g., `eu-central-1`)
- **irsa.roleArn**: IAM role ARN (e.g., `arn:aws:iam::123456789012:role/my-role`)
- **irsa.jdbcUrl**: JDBC connection string
- **irsa.dbUsername**: Database username (must be configured for IAM auth in RDS)
- **irsa.webIdentityToken**: Web identity token from EKS service account

## IAM Database Authentication

### What is IAM Database Authentication?

IAM database authentication allows you to authenticate to your RDS database using AWS IAM credentials instead of a database password. This provides several benefits:

- **No password management**: Passwords are generated dynamically and expire after 15 minutes
- **Centralized access control**: Use IAM policies to control database access
- **Audit trail**: All authentication attempts are logged in CloudTrail

### Requirements for IAM Database Authentication

1. **RDS Instance**: Must have IAM database authentication enabled. See [Enabling and disabling IAM database authentication](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/UsingWithRDS.IAMDBAuth.Enabling.html).
2. **Database User**: Must be created with IAM authentication grants (see [Creating a database account using IAM authentication](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/UsingWithRDS.IAMDBAuth.DBAccounts.html)):
   ```sql
   -- PostgreSQL
   CREATE USER myuser;
   GRANT rds_iam TO myuser;

   -- MySQL
   CREATE USER myuser IDENTIFIED WITH AWSAuthenticationPlugin AS 'RDS';
   ```
3. **IAM Role/User**: Must have permission to generate auth tokens (see [Creating and using an IAM policy for IAM database access](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/UsingWithRDS.IAMDBAuth.IAMPolicy.html)):
   ```json
   {
     "Effect": "Allow",
     "Action": "rds-db:connect",
     "Resource": "arn:aws:rds-db:region:account:dbuser:db-instance-id/db-username"
   }
   ```
4. **AWS Credentials**: Must be **temporary credentials with session tokens** (from SSO or IRSA)
   - Static AWS credentials (access key + secret key only) do NOT work
   - The RDS token generator requires session tokens from AWS STS for security

### How It Works

When using `sso` or `irsa` authentication methods:

1. The application obtains temporary AWS credentials from the credential provider chain
2. The `RdsIamAuthTokenGenerator` uses these credentials to generate a 15-minute auth token
3. The auth token is used as the database password
4. HikariCP connection pool automatically refreshes tokens as needed

## Project Structure

```
aws-rds-example/
├── src/
│   └── main/
│       ├── java/
│       │   └── org/jclouds/examples/aws/rds/
│       │       └── JcloudsRdsApplication.java
│       └── resources/
│           └── logback.xml
├── build.gradle
└── README.md
```

## What the Application Does

The `JcloudsRdsApplication` demonstrates RDS connectivity:

1. Creates a DataSource context with authentication method (direct, SSO, or IRSA)
2. Connects to the RDS database using HikariCP connection pool
3. Executes a simple query to verify connectivity
4. Displays connection information and results

## Troubleshooting

### Authentication Issues

**Direct Method:**
- Verify JDBC URL, username, and password in `gradle.properties`
- Check database security group allows connections from your IP
- Verify database user exists and has appropriate permissions

**SSO Method:**
- Run `aws sso login --profile <profile-name>` before running the application
- Verify the AWS profile has permission to generate RDS auth tokens (`rds-db:connect`)
- Check that the database user is configured for IAM authentication

**IRSA Method:**
- Ensure you're NOT logged in with AWS SSO (run `aws sso logout`)
- Verify the web identity token is valid and not expired
- Check that the IAM role has permission to generate RDS auth tokens
- Verify the database user is configured for IAM authentication

### Common Errors

**"Access denied for user"**:
- For direct auth: Check username/password
- For IAM auth: Verify the database user has IAM authentication enabled (`GRANT rds_iam` in PostgreSQL)

**"Cannot generate auth token"**:
- Verify you're using temporary AWS credentials (not static credentials)
- Check that the IAM policy includes `rds-db:connect` permission
- Ensure the resource ARN in the policy matches your database and user

**"Connection timeout"**:
- Check database security group allows inbound connections
- Verify the JDBC URL is correct
- Ensure your network can reach the RDS instance

### Debug Mode

To see detailed credential resolution and database connection logging, uncomment the relevant logger configurations in `src/main/resources/logback.xml`:

```xml
<logger name="org.jclouds" level="DEBUG" />
<logger name="software.amazon.awssdk" level="DEBUG"/>
<logger name="software.amazon.awssdk.auth.credentials" level="DEBUG"/>
<logger name="com.zaxxer.hikari" level="DEBUG"/>
```

This will output detailed information about:
- AWS credential provider resolution
- RDS auth token generation
- Database connection pool behavior
- SQL query execution

## Additional Resources

- [RDS IAM Database Authentication](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/UsingWithRDS.IAMDBAuth.html)
- [IAM Roles for Service Accounts (IRSA)](https://docs.aws.amazon.com/eks/latest/userguide/iam-roles-for-service-accounts.html)
- [AWS SSO Configuration](https://docs.aws.amazon.com/cli/latest/userguide/cli-configure-sso.html)
