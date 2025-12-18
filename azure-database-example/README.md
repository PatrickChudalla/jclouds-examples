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

1. **Azure Database Instance**: Must have Entra ID authentication enabled. See [Configure and manage Entra ID authentication](https://learn.microsoft.com/en-us/azure/postgresql/flexible-server/how-to-configure-sign-in-azure-ad-authentication).

2. **Entra ID Admin Setup**: Set up an Entra ID admin for your database server:
   ```bash
   # Get your user details
   USER_OBJECT_ID=$(az ad signed-in-user show --query id -o tsv)
   USER_EMAIL=$(az ad signed-in-user show --query userPrincipalName -o tsv)

   # Get your resource group
   RG=$(az postgres flexible-server list --query "[?fullyQualifiedDomainName=='your-server.postgres.database.azure.com'].resourceGroup" -o tsv)

   SERVER_NAME=<your-server>

   # Enable Entra ID authentication (keep password auth enabled for initial admin access)
   az postgres flexible-server update \
     --resource-group $RG \
     --name $SERVER_NAME \
     --microsoft-entra-auth Enabled \
     --password-auth Enabled

   # Create Entra ID admin
   az postgres flexible-server microsoft-entra-admin create \
     --resource-group $RG \
     --server-name $SERVER_NAME \
     --object-id $USER_OBJECT_ID \
     --display-name "$USER_EMAIL"
   ```

   **Note**: You may need to delete an existing Entra ID admin first if one is already configured:
   ```bash
   # Delete existing Entra ID admin (if needed)
   az postgres flexible-server microsoft-entra-admin delete \
     --resource-group $RG \
     --server-name $SERVER_NAME
   ```

3. **Database User**: Must be created as an Entra ID user:
   ```bash
   # Connect to the database using Entra ID token
   ACCESS_TOKEN=$(az account get-access-token --resource https://ossrdbms-aad.database.windows.net --query accessToken -o tsv)
   PGPASSWORD=$ACCESS_TOKEN psql "host=your-server.postgres.database.azure.com port=5432 dbname=your-database user=$USER_EMAIL sslmode=require"
   ```

   ```sql
   -- PostgreSQL: Create Entra ID user and grant permissions
   CREATE ROLE "user-or-identity-name@domain.com" WITH LOGIN;
   GRANT ALL PRIVILEGES ON DATABASE mydatabase TO "user-or-identity-name@domain.com";
   GRANT ALL ON SCHEMA public TO "user-or-identity-name@domain.com";
   GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO "user-or-identity-name@domain.com";
   GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO "user-or-identity-name@domain.com";

   -- MySQL: Connect as Entra ID admin user and run:
   CREATE AADUSER 'user-or-identity-name@domain.com';
   GRANT ALL PRIVILEGES ON mydatabase.* TO 'user-or-identity-name@domain.com'@'%';
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
- For Entra ID auth: Verify the user is created with `CREATE ROLE` (PostgreSQL) or `CREATE AADUSER` (MySQL) as shown in the setup instructions above

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
| **Database Setup** | `GRANT rds_iam TO user` | `CREATE ROLE "user" WITH LOGIN` |
| **K8s Identity** | IRSA (IAM Roles for Service Accounts) | Workload Identity |

## Testing Workload Identity in AKS

This section describes how to test the Workload Identity authentication method in a real Azure Kubernetes Service (AKS) cluster.

### Prerequisites for AKS Testing

1. **AKS Cluster** with Workload Identity enabled
2. **User-Assigned Managed Identity** created in Azure
3. **PostgreSQL Flexible Server** with Entra ID authentication enabled
4. **Federated Identity Credential** linking the Kubernetes Service Account to the Azure Managed Identity

### Step 1: Create AKS Cluster with Workload Identity

```bash
# Set variables
RESOURCE_GROUP="rg-ahc"
CLUSTER_NAME="your-aks-cluster"
LOCATION="germanywestcentral"

# Create AKS cluster with Workload Identity enabled...
az aks create \
  --resource-group $RESOURCE_GROUP \
  --name $CLUSTER_NAME \
  --location $LOCATION \
  --enable-oidc-issuer \
  --enable-workload-identity \
  --node-count 1

# ... OR enable OIDC issuer withing already existing cluster
az aks update \
  --resource-group $RESOURCE_GROUP \
  --name $AKS_NAME \
  --enable-oidc-issuer \
  --enable-workload-identity

# Get cluster credentials
az aks get-credentials --resource-group $RESOURCE_GROUP --name $CLUSTER_NAME
```

### Step 2: Create User-Assigned Managed Identity

```bash
# Create the managed identity
IDENTITY_NAME="jclouds-db-workload-identity"
az identity create \
  --name $IDENTITY_NAME \
  --resource-group $RESOURCE_GROUP \
  --location $LOCATION

# Get identity details
CLIENT_ID=$(az identity show --name $IDENTITY_NAME --resource-group $RESOURCE_GROUP --query clientId -o tsv)
PRINCIPAL_ID=$(az identity show --name $IDENTITY_NAME --resource-group $RESOURCE_GROUP --query principalId -o tsv)
TENANT_ID=$(az account show --query tenantId -o tsv)

echo "Client ID: $CLIENT_ID"
echo "Principal ID: $PRINCIPAL_ID"
echo "Tenant ID: $TENANT_ID"
```

### Step 3: Register Managed Identity as Database Admin

**IMPORTANT**: Managed Identities cannot be added to Azure Database as regular users with `CREATE ROLE`. They must be registered as Entra ID administrators using the Azure CLI:

```bash
# Set your PostgreSQL server name
SERVER_NAME="your-postgres-server"

# Register the managed identity as Entra ID admin
az postgres flexible-server microsoft-entra-admin create \
  --resource-group $RESOURCE_GROUP \
  --server-name $SERVER_NAME \
  --object-id $PRINCIPAL_ID \
  --display-name "$IDENTITY_NAME" \
  --type ServicePrincipal

# Verify the admin was created
az postgres flexible-server microsoft-entra-admin show \
  --resource-group $RESOURCE_GROUP \
  --server-name $SERVER_NAME \
  --object-id $PRINCIPAL_ID
```

This command automatically creates the database user with the correct configuration for token-based authentication.

### Step 4: Create Kubernetes Service Account

```bash
# Create namespace (optional, or use 'default')
NAMESPACE="default"

# Create service account
cat <<EOF | kubectl apply -f -
apiVersion: v1
kind: ServiceAccount
metadata:
  annotations:
    azure.workload.identity/client-id: "$CLIENT_ID"
  name: jclouds-db-sa
  namespace: $NAMESPACE
EOF
```

### Step 5: Create Federated Identity Credential

```bash
# Get AKS OIDC issuer URL
AKS_OIDC_ISSUER=$(az aks show --resource-group $RESOURCE_GROUP --name $CLUSTER_NAME --query oidcIssuerProfile.issuerUrl -o tsv)

echo "AKS OIDC Issuer: $AKS_OIDC_ISSUER"

# Create federated identity credential
az identity federated-credential create \
  --name "jclouds-db-federated-credential" \
  --identity-name $IDENTITY_NAME \
  --resource-group $RESOURCE_GROUP \
  --issuer "$AKS_OIDC_ISSUER" \
  --subject "system:serviceaccount:$NAMESPACE:jclouds-db-sa" \
  --audiences "api://AzureADTokenExchange"

# Verify the federated credential
az identity federated-credential show \
  --name "jclouds-db-federated-credential" \
  --identity-name $IDENTITY_NAME \
  --resource-group $RESOURCE_GROUP \
  --query "{subject: subject, issuer: issuer, audiences: audiences}" -o json
```

**Important**: The `subject` must exactly match the format `system:serviceaccount:<namespace>:<service-account-name>`.

### Step 6: Configure Database Firewall

```bash
# Allow Azure services to access the database
az postgres flexible-server firewall-rule create \
  --resource-group $RESOURCE_GROUP \
  --name $SERVER_NAME \
  --rule-name AllowAzureServices \
  --start-ip-address 0.0.0.0 \
  --end-ip-address 0.0.0.0

# Optional: Add your local IP for testing
MY_IP=$(curl -s https://api.ipify.org)
az postgres flexible-server firewall-rule create \
  --resource-group $RESOURCE_GROUP \
  --name $SERVER_NAME \
  --rule-name AllowMyIP \
  --start-ip-address $MY_IP \
  --end-ip-address $MY_IP
```

### Step 7: Deploy Test Pod

```bash
# Set your database connection details
DB_HOST="your-server.postgres.database.azure.com"
DB_NAME="platformdb"
DB_USER="$IDENTITY_NAME"

# Create test pod with jClouds
cat <<EOF | kubectl apply -f -
apiVersion: v1
kind: Pod
metadata:
  name: jclouds-db-test
  namespace: $NAMESPACE
  labels:
    azure.workload.identity/use: "true"
spec:
  serviceAccountName: jclouds-db-sa
  containers:
  - name: jclouds
    image: mcr.microsoft.com/openjdk/jdk:21-ubuntu
    command: ["sleep", "infinity"]
    env:
    - name: AZURE_CLIENT_ID
      value: "$CLIENT_ID"
    - name: AZURE_TENANT_ID
      value: "$TENANT_ID"
EOF

# Wait for pod to be ready
kubectl wait --for=condition=Ready pod/jclouds-db-test -n $NAMESPACE --timeout=60s
```

### Step 8: Install Dependencies in Pod

```bash
# Install git, gradle, and azure-cli
kubectl exec jclouds-db-test -n $NAMESPACE -- bash -c '
apt-get update && apt-get install -y git curl unzip

# Install Gradle
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install gradle 8.5

# Install Azure CLI
curl -sL https://aka.ms/InstallAzureCLIDeb | bash
'
```

### Step 9: Clone and Build jClouds

```bash
# Clone repositories
kubectl exec jclouds-db-test -n $NAMESPACE -- bash -c '
cd /root
git clone https://github.com/apache/jclouds.git
git clone https://github.com/jclouds/jclouds-examples.git

# Build jclouds azure-database provider
cd /root/jclouds
./gradlew :providers:azure-database:build -x test

# Install to local maven repository
./gradlew :providers:azure-database:publishToMavenLocal
'
```

### Step 10: Configure and Run Test

```bash
# Configure gradle.properties for workload identity
kubectl exec jclouds-db-test -n $NAMESPACE -- bash -c "
cat > /root/jclouds-examples/azure-database-example/gradle.properties <<EOF
# Workload Identity Authentication
workloadIdentity.jdbcUrl=jdbc:postgresql://$DB_HOST:5432/$DB_NAME?sslmode=require
workloadIdentity.dbUsername=$DB_USER
workloadIdentity.tenantId=$TENANT_ID
workloadIdentity.clientId=$CLIENT_ID
EOF
"

# Run the application
kubectl exec jclouds-db-test -n $NAMESPACE -- bash -c '
export AZURE_CLIENT_ID='"$CLIENT_ID"'
export AZURE_TENANT_ID='"$TENANT_ID"'
export AZURE_FEDERATED_TOKEN_FILE=/var/run/secrets/azure/tokens/azure-identity-token

cd /root/jclouds-examples
source "$HOME/.sdkman/bin/sdkman-init.sh"
./gradlew :azure-database-example:run -PauthMethod=workload-identity
'
```

### Expected Output

If everything is configured correctly, you should see:

```
> Task :azure-database-example:run
Running Jclouds Azure Database real world example...
=== Azure Database Connection Configuration ===
- Provider: azure-database
- JDBC URL: jdbc:postgresql://your-server.postgres.database.azure.com:5432/platformdb?sslmode=require
- Database User: jclouds-db-workload-identity
- Authentication Method: Azure Entra ID (access token generated from ambient credentials)
===============================================

DataSource created successfully!
Attempting to establish database connection...

=== Database Connection Information ===
- Database Product: PostgreSQL
- Database Version: 15.15
- Driver Name: PostgreSQL JDBC Driver
- Driver Version: 42.7.1
- JDBC URL: jdbc:postgresql://your-server.postgres.database.azure.com:5432/platformdb?sslmode=require
- Username: jclouds-db-workload-identity
- Connection valid: true
=======================================

Successfully connected to Azure database!

BUILD SUCCESSFUL
```

### Troubleshooting AKS Workload Identity

**"Couldn't acquire access token from Workload Identity"**:
- Verify federated credential subject matches exactly: `system:serviceaccount:<namespace>:<sa-name>`
- Check the OIDC issuer URL is correct
- Ensure the service account has the correct annotation

**"password authentication failed for user"**:
- The managed identity must be registered as an Entra ID admin using the Azure CLI
- DO NOT use `CREATE ROLE` for managed identities - it won't work
- Verify with: `az postgres flexible-server microsoft-entra-admin list`

**Pod doesn't have federated token**:
- Check pod has label: `azure.workload.identity/use: "true"`
- Verify serviceAccountName is correct
- Check if `/var/run/secrets/azure/tokens/azure-identity-token` exists in pod

**Firewall issues**:
- Ensure firewall rule `0.0.0.0` to `0.0.0.0` exists (allows Azure services)
- AKS pods connect from Azure internal IPs

### Cleanup

```bash
# Delete pod
kubectl delete pod jclouds-db-test -n $NAMESPACE

# Delete service account
kubectl delete serviceaccount jclouds-db-sa -n $NAMESPACE

# Delete federated credential
az identity federated-credential delete \
  --name "jclouds-db-federated-credential" \
  --identity-name $IDENTITY_NAME \
  --resource-group $RESOURCE_GROUP \
  --yes

# Delete managed identity
az identity delete \
  --name $IDENTITY_NAME \
  --resource-group $RESOURCE_GROUP

# Remove database admin (optional)
az postgres flexible-server microsoft-entra-admin delete \
  --resource-group $RESOURCE_GROUP \
  --server-name $SERVER_NAME \
  --yes
```

## Additional Resources

- [Azure Database for PostgreSQL - Entra ID authentication](https://learn.microsoft.com/en-us/azure/postgresql/single-server/concepts-azure-ad-authentication)
- [Azure Database for MySQL - Entra ID authentication](https://learn.microsoft.com/en-us/azure/mysql/single-server/concepts-azure-ad-authentication)
- [Azure Managed Identity](https://learn.microsoft.com/en-us/azure/active-directory/managed-identities-azure-resources/overview)
- [Azure Workload Identity for AKS](https://learn.microsoft.com/en-us/azure/aks/workload-identity-overview)
- [Azure Identity SDK for Java](https://learn.microsoft.com/en-us/java/api/overview/azure/identity-readme)
