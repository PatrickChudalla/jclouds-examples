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

    // This is the only place where this application refers to a specific cloud provider (AWS RDS in this case)
    private static final String PROVIDER = "aws-rds";

    private static final Logger logger = LoggerFactory.getLogger(JcloudsRdsApplication.class.getName());

    public static void main(String[] args) {
        logger.info("Running Jclouds RDS real world example...");

        try {
            // Example JDBC URL for RDS: jdbc:mysql://hostname:port/database
            String jdbcUrl = args.length > 0 ? args[0] :
                "jdbc:mysql://my-rds-instance.us-east-1.rds.amazonaws.com:3306/mydb";

            // Database username (identity) - from environment variable
            String username = System.getenv("DB_USERNAME");
            if (username == null || username.isEmpty()) {
                username = "admin";
            }

            // Database password (credential) - from environment variable
            // Can be empty string for IAM authentication
            String password = System.getenv("DB_PASSWORD");
            if (password == null) {
                password = "";
            }

            logger.info("=== RDS Connection Configuration ===");
            logger.info("- Provider: " + PROVIDER);
            logger.info("- JDBC URL: " + jdbcUrl);
            logger.info("- Database User: " + username);
            if (password.isEmpty()) {
                logger.info("- Authentication Method: IAM (database auth token generated from ambient temporary credentials enabling passwordless database connections according to underlying IAM roles)");
            } else {
                logger.info("- Authentication Method: Password (static database password provided)");
                logger.info("- Database Password: " + (password.length() > 0 ? password.substring(0, Math.min(8, password.length())) + "..." : "(empty)"));
            }
            logger.info("====================================");

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
                logger.info("- Database Product: " + metaData.getDatabaseProductName());
                logger.info("- Database Version: " + metaData.getDatabaseProductVersion());
                logger.info("- Driver Name: " + metaData.getDriverName());
                logger.info("- Driver Version: " + metaData.getDriverVersion());
                logger.info("- JDBC URL: " + metaData.getURL());
                logger.info("- Username: " + metaData.getUserName());
                logger.info("- Connection valid: " + connection.isValid(5));
                logger.info("=======================================");

                logger.info("Successfully connected to RDS database!");
            }

            dataSourceContext.close();
            logger.info("Jclouds RDS real world example completed successfully!");

        } catch (Exception e) {
            logger.error("ERROR: " + e.getMessage());
            System.exit(1);
        }
    }
}
