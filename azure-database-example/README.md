# Azure Database Example with jClouds

This example demonstrates how to use Apache jClouds to connect to Azure Database (PostgreSQL or MySQL) using different authentication methods, including Azure Entra ID (formerly Azure AD) authentication.

## Prerequisites

- Java 11 or higher
- Azure CLI configured with appropriate credentials (for Managed Identity testing)
- Azure Database for PostgreSQL or MySQL instance
- For Entra ID authentication: Database must be configured for Azure Entra ID authentication

## Configuration

Before running the application, copy `gradle.properties.template` to `gradle.properties` and customize it with your own values:

```bash
cp gradle.properties.template gradle.properties
```

Edit `gradle.properties` to set your database connection details, Azure credentials, and authentication parameters. The `gradle.properties` file is gitignored to prevent committing sensitive information.

## Running the Application

The application supports multiple authentication methods that can be selected using Gradle project properties.

**IMPORTANT**: Always use the project-specific task notation (`:azure-database-example:run`) to avoid configuration conflicts with other example projects.

### Available Authentication Methods

#### 1. Direct Database Authentication (`direct`) - Default

Uses static database username/password authentication without Azure credentials or Entra ID authentication. This is the traditional database authentication method.

```bash
# Use default configuration from gradle.properties
./gradlew :azure-database-example:run

# Or explicitly specify the authentication method
./gradlew :azure-database-example:run -PauthMethod=direct
```

#### 2. Azure Managed Identity (`managed-identity`)

Uses Azure Managed Identity to automatically obtain access tokens. This method works when running on Azure resources (VMs, App Service, Container Instances, AKS, etc.) with Managed Identity enabled. Before using this method, ensure all prerequisites in [Requirements for Azure Entra ID Authentication](#requirements-for-azure-entra-id-authentication) are met.

**Important**: This requires running on an Azure resource with Managed Identity configured.

```bash
# Run on Azure VM/App Service/Container with Managed Identity
./gradlew :azure-database-example:run -PauthMethod=managed-identity
```

#### 3. Azure Workload Identity (`workload-identity`)

Simulates the Azure Workload Identity environment used in Azure Kubernetes Service (AKS). Uses federated identity credentials to obtain access tokens for Entra ID authentication. Before using this method, ensure all prerequisites in [Requirements for Azure Entra ID Authentication](#requirements-for-azure-entra-id-authentication) are met.

**Important**: This requires federated identity credentials configured in Azure Entra ID.

```bash
# Run with Workload Identity (typically in AKS)
./gradlew :azure-database-example:run -PauthMethod=workload-identity
```

### Configuration

All authentication parameters are configured in `gradle.properties`. Copy `gradle.properties.template` to `gradle.properties` and customize with your values:

**Direct Authentication:**
- **direct.jdbcUrl**: JDBC connection string (e.g., `jdbc:postgresql://myserver.postgres.database.azure.com:5432/mydatabase?sslmode=require`)
- **direct.dbUsername**: Database username
- **direct.dbPassword**: Database password

**Managed Identity Authentication:**
- **managedIdentity.jdbcUrl**: JDBC connection string
- **managedIdentity.dbUsername**: Database username (must be an Entra ID user or managed identity name)
- **managedIdentity.clientId**: (Optional) Client ID for user-assigned managed identity

**Workload Identity Authentication:**
- **workloadIdentity.jdbcUrl**: JDBC connection string
- **workloadIdentity.dbUsername**: Database username (must be an Entra ID user)
- **workloadIdentity.tenantId**: Azure Entra ID tenant ID
- **workloadIdentity.clientId**: Application (client) ID of the user-assigned managed identity
- **workloadIdentity.federatedToken**: Federated identity token from AKS

## Azure Entra ID Authentication

### What is Azure Entra ID Authentication?

Azure Entra ID (formerly Azure AD) authentication allows you to authenticate to your Azure Database using Azure identity credentials instead of a database password. This provides several benefits:

- **No password management**: Passwords are replaced with short-lived access tokens
- **Centralized access control**: Use Azure RBAC to control database access
- **Audit trail**: All authentication attempts are logged in Azure Monitor
- **Enhanced security**: Supports Managed Identity and Workload Identity

### Requirements for Azure Entra ID Authentication

1. **Azure Database Instance**: Must have Entra ID authentication enabled. See [Configure and manage Azure AD authentication](https://learn.microsoft.com/en-us/azure/postgresql/single-server/how-to-configure-sign-in-azure-ad-authentication).

2. **Database User**: Must be created as an Entra ID user:
   ```sql
   -- PostgreSQL: Connect as Entra ID admin user and run:
   SELECT * FROM pgaadauth_create_principal('<user-or-identity-name>', false, false);
   GRANT ALL PRIVILEGES ON DATABASE mydatabase TO "<user-or-identity-name>";

   -- MySQL: Connect as Entra ID admin user and run:
   CREATE AADUSER '<user-or-identity-name>';
   GRANT ALL PRIVILEGES ON mydatabase.* TO '<user-or-identity-name>'@'%';
   ```

3. **Azure Identity**:
   - **Managed Identity**: Enable system-assigned or user-assigned managed identity on your Azure resource
   - **Workload Identity**: Configure federated identity credentials for your AKS service account

4. **Azure Role Assignment**: The identity must have appropriate permissions to access the database:
   - Assign the identity appropriate database roles using the SQL commands above

### How It Works

When using `managed-identity` or `workload-identity` authentication methods:

1. The application obtains an access token from Azure Entra ID using the configured identity
2. The access token is used as the database password
3. The Azure Database validates the token with Entra ID
4. HikariCP connection pool automatically refreshes tokens as needed

### Authentication Flow Details

**Managed Identity:**
- Azure provides an IMDS (Instance Metadata Service) endpoint on the VM/container
- The Azure Identity SDK calls this endpoint to get access tokens
- No credentials need to be stored in the application

**Workload Identity:**
- AKS injects a federated token into the pod at `/var/run/secrets/azure/tokens/azure-identity-token`
- The Azure Identity SDK exchanges this token for an Entra ID access token
- The access token is used to authenticate to the database

## Project Structure

```
azure-database-example/
├── src/
│   └── main/
│       ├── java/
│       │   └── org/jclouds/examples/azure/database/
│       │       └── JcloudsAzureDatabaseApplication.java
│       └── resources/
│           └── logback.xml
├── build.gradle
├── gradle.properties.template
└── README.md
```

## What the Application Does

The `JcloudsAzureDatabaseApplication` demonstrates Azure Database connectivity:

1. Creates a DataSource context with authentication method (direct, managed-identity, or workload-identity)
2. Connects to the Azure Database using HikariCP connection pool
3. Executes metadata queries to verify connectivity
4. Displays connection information and results

## Troubleshooting

### Authentication Issues

**Direct Method:**
- Verify JDBC URL, username, and password in `gradle.properties`
- Check Azure Database firewall rules allow connections from your IP
- Verify database user exists and has appropriate permissions
- Ensure SSL is properly configured (`sslmode=require` for PostgreSQL, `useSSL=true` for MySQL)

**Managed Identity Method:**
- Verify Managed Identity is enabled on your Azure resource
- Check that the identity has been added as a database user
- Ensure the identity has appropriate permissions
- Verify you're running on an Azure resource (VM, App Service, Container, etc.)

**Workload Identity Method:**
- Verify federated identity credentials are configured correctly
- Check that the service account is properly annotated in AKS
- Ensure the federated token is valid and not expired
- Verify the identity has been added as a database user

### Common Errors

**"Access denied for user"**:
- For direct auth: Check username/password
- For Entra ID auth: Verify the user is created with `pgaadauth_create_principal` (PostgreSQL) or `CREATE AADUSER` (MySQL)

**"Cannot obtain access token"**:
- Verify the Azure identity has appropriate permissions
- Check that Entra ID authentication is enabled on the database
- Ensure network connectivity to Azure Entra ID endpoints

**"Connection timeout"**:
- Check Azure Database firewall rules
- Verify the JDBC URL is correct
- Ensure your network can reach the Azure Database endpoint
- Check that SSL/TLS is properly configured

**"SSL connection required"**:
- Add `sslmode=require` to PostgreSQL JDBC URL
- Add `useSSL=true` to MySQL JDBC URL
- Verify SSL certificates are properly configured

### Debug Mode

To see detailed credential resolution and database connection logging, uncomment the relevant logger configurations in `src/main/resources/logback.xml`:

```xml
<logger name="org.jclouds" level="DEBUG" />
<logger name="com.azure" level="DEBUG"/>
<logger name="com.azure.identity" level="DEBUG"/>
<logger name="com.zaxxer.hikari" level="DEBUG"/>
```

This will output detailed information about:
- Azure credential provider resolution
- Access token acquisition
- Database connection pool behavior
- SQL query execution

## Differences from AWS RDS Example

While this example follows a similar structure to the AWS RDS example, there are key differences:

| Feature | AWS RDS | Azure Database |
|---------|---------|----------------|
| **IAM Auth** | IAM Database Authentication | Azure Entra ID Authentication |
| **Token Generation** | `RdsIamAuthTokenGenerator` | Azure Identity SDK |
| **Credentials** | AWS credentials (SSO, IRSA) | Azure credentials (Managed Identity, Workload Identity) |
| **Token Validity** | 15 minutes | Variable (typically 60 minutes) |
| **Database Setup** | `GRANT rds_iam TO user` | `pgaadauth_create_principal()` |
| **K8s Identity** | IRSA (IAM Roles for Service Accounts) | Workload Identity |

## Additional Resources

- [Azure Database for PostgreSQL - Entra ID authentication](https://learn.microsoft.com/en-us/azure/postgresql/single-server/concepts-azure-ad-authentication)
- [Azure Database for MySQL - Entra ID authentication](https://learn.microsoft.com/en-us/azure/mysql/single-server/concepts-azure-ad-authentication)
- [Azure Managed Identity](https://learn.microsoft.com/en-us/azure/active-directory/managed-identities-azure-resources/overview)
- [Azure Workload Identity for AKS](https://learn.microsoft.com/en-us/azure/aks/workload-identity-overview)
- [Azure Identity SDK for Java](https://learn.microsoft.com/en-us/java/api/overview/azure/identity-readme)
