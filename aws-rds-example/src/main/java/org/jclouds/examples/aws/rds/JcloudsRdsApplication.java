package org.jclouds.examples.aws.rds;

import org.jclouds.ContextBuilder;
import org.jclouds.datasource.DataSourceContext;
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule;

import com.google.inject.Module;
import com.google.common.collect.ImmutableSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

public class JcloudsRdsApplication {

    // This is the AWS RDS provider identifier
    private static final String PROVIDER = "aws-rds";

    private static final Logger logger = LoggerFactory.getLogger(JcloudsRdsApplication.class.getName());

    public static void main(String[] args) {
        logger.info("Running Jclouds RDS example...");

        try {
            // Example JDBC URL for RDS. Format: jdbc:mysql://hostname:port/database
            // In real usage, this would be your actual RDS endpoint
            String jdbcUrl = args.length > 0 ? args[0] :
                "jdbc:mysql://my-rds-instance.us-east-1.rds.amazonaws.com:3306/mydb";

            // Database username (identity)
            String username = args.length > 1 ? args[1] : "admin";

            // Database password (credential) - can be empty string for IAM authentication
            String password = args.length > 2 ? args[2] : "";

            logger.info("Connecting to RDS with endpoint: " + jdbcUrl);
            logger.info("Username: " + username);
            logger.info("Using IAM Authentication: " + (password.isEmpty() ? "Yes" : "No"));

            // Create DataSource context for AWS RDS
            DataSourceContext dataSourceContext = ContextBuilder.newBuilder(PROVIDER)
                .endpoint(jdbcUrl)
                .credentials(username, password)
                .modules(ImmutableSet.<Module>of(new SLF4JLoggingModule()))
                .buildView(DataSourceContext.class);

            // Get the DataSource
            DataSource dataSource = dataSourceContext.getDataSource();
            logger.info("DataSource created successfully!");

            // Print DataSource information
            logger.info("DataSource class: " + dataSource.getClass().getName());

            // Get connection to print connection details
            logger.info("Attempting to establish database connection...");
            try (Connection connection = dataSource.getConnection()) {
                DatabaseMetaData metaData = connection.getMetaData();

                logger.info("=== Database Connection Information ===");
                logger.info("Database Product: " + metaData.getDatabaseProductName());
                logger.info("Database Version: " + metaData.getDatabaseProductVersion());
                logger.info("Driver Name: " + metaData.getDriverName());
                logger.info("Driver Version: " + metaData.getDriverVersion());
                logger.info("JDBC URL: " + metaData.getURL());
                logger.info("Username: " + metaData.getUserName());
                logger.info("Connection valid: " + connection.isValid(5));

                logger.info("Successfully connected to RDS database!");
            }

            dataSourceContext.close();
            logger.info("Jclouds RDS example completed successfully!");

        } catch (Exception e) {
            logger.error("ERROR: " + e.getMessage(), e);
            System.exit(1);
        }
    }
}
