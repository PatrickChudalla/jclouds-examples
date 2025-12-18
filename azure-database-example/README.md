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

Uses Azure Managed Identity to automatically obtain access tokens. This method works when running on Azure resources (VMs, App Service, Container Instances, AKS, etc.) with Managed Identity enabled. Before using this method, ensure all prerequisites in [Azure Entra ID Managed Identity Authentication](#azure-entra-id-managed-identity-authentication) are met.

**Important**: This requires running on an Azure resource with Managed Identity configured.

```bash
# Run on Azure VM/App Service/Container with Managed Identity
./gradlew :azure-database-example:run -PauthMethod=managed-identity
```

#### 3. Azure Workload Identity (`workload-identity`)

Simulates the Azure Workload Identity environment used in Azure Kubernetes Service (AKS). Uses federated identity credentials to obtain access tokens for Entra ID authentication. Before using this method, ensure all prerequisites in [Requirements for Azure Entra ID Workload Identity Authentication](#requirements-for-azure-entra-id-workload-identity-authentication) are met.

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

## Azure Entra ID Managed Identity Authentication

### What is Azure Entra ID Managed Identity Authentication?

Azure Entra ID (formerly Azure AD) authentication allows you to authenticate to your Azure Database using Azure identity credentials instead of a database password. 

### Requirements for Azure Entra ID Managed Identity Authentication

1. **Azure Database Instance**: Must have Entra ID authentication enabled. See [Configure and manage Entra ID authentication](https://learn.microsoft.com/en-us/azure/postgresql/flexible-server/how-to-configure-sign-in-azure-ad-authentication).

   ```bash
   # Enable Entra ID authentication (keep password auth enabled for initial admin access)
   az postgres flexible-server update \
     --resource-group $RG \
     --name $SERVER_NAME \
     --microsoft-entra-auth Enabled \
     --password-auth Enabled
   ```

2. **Entra ID Admin Setup**: Set up an Entra ID admin for your database server:
   ```bash
   # Get your user details
   USER_OBJECT_ID=$(az ad signed-in-user show --query id -o tsv)
   USER_EMAIL=$(az ad signed-in-user show --query userPrincipalName -o tsv)

   # Get your resource group
   RG=$(az postgres flexible-server list --query "[?fullyQualifiedDomainName=='your-server.postgres.database.azure.com'].resourceGroup" -o tsv)

   SERVER_NAME=<your-server>

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


## Requirements for Azure Entra ID Workload Identity Authentication

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

### Step 2: Enable PostgreSQL Entra ID Authentication

Must have Entra ID authentication enabled. See [Configure and manage Entra ID authentication](https://learn.microsoft.com/en-us/azure/postgresql/flexible-server/how-to-configure-sign-in-azure-ad-authentication).

```bash
# Set your PostgreSQL server name
SERVER_NAME="your-postgres-server"

# Enable Entra ID authentication (keep password auth enabled for initial admin access)
az postgres flexible-server update \
  --resource-group $RESOURCE_GROUP \
  --name $SERVER_NAME \
  --microsoft-entra-auth Enabled \
  --password-auth Enabled
```

### Step 3: Create User-Assigned Managed Identity

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

### Step 4: Register Managed Identity as Database Admin

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

### Step 5: Create Kubernetes Service Account

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

### Step 6: Create Federated Identity Credential

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

### Step 7: Configure Database Firewall

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

### Step 8: Deploy Test Pod

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

### Step 9: Install Dependencies in Pod

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

### Step 10: Clone and Build jClouds

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

### Step 11: Configure and Run Test

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

## Additional Resources

- [Azure Database for PostgreSQL - Entra ID authentication](https://learn.microsoft.com/en-us/azure/postgresql/single-server/concepts-azure-ad-authentication)
- [Azure Database for MySQL - Entra ID authentication](https://learn.microsoft.com/en-us/azure/mysql/single-server/concepts-azure-ad-authentication)
- [Azure Managed Identity](https://learn.microsoft.com/en-us/azure/active-directory/managed-identities-azure-resources/overview)
- [Azure Workload Identity for AKS](https://learn.microsoft.com/en-us/azure/aks/workload-identity-overview)
- [Azure Identity SDK for Java](https://learn.microsoft.com/en-us/java/api/overview/azure/identity-readme)
