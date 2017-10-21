package com.company;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class MysqlConnect {
    // init connection object
    private Connection connection;
    // init properties object
    private Properties properties;

    // create properties
    private Properties getProperties() throws IOException {
        if (properties == null) {
            properties = new Properties();
            FileInputStream in = new FileInputStream("config/db.properties");
            properties.load(in);
            in.close();
        }
        return properties;
    }

    // connect database
    public Connection connect() {
        if (connection == null) {
            try {
                Class.forName(getProperties().getProperty("driver"));
                connection = DriverManager.getConnection(getProperties().getProperty("url"), getProperties());
                System.out.println("Connection Successful");
            } catch (ClassNotFoundException | SQLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return connection;
    }

    // disconnect database
    public void disconnect() {
        if (connection != null) {
            try {
                connection.close();
                connection = null;
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
}