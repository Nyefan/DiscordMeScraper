package DiscordMe;

import org.postgresql.util.PSQLException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * A basic scraper for discord.me
 * @author  Nyefan
 * contact  nyefancoding@gmail.com
 * github   github.com/nyefan
 * @version 1.0
 * @since   2016-12(DEC)-08
 * depends  posgresql-9.4.1212
 * @implNote
 * TODO     add an insert command
 * TODO     wrap insert statements in a rowObject of some kind
 */
public class Database {

    private Connection connection;

    /**
     * Prevents this object from being created without being connected to a database
     */
    private Database() {

    }

    /**
     * Returns an instance of Database connected to the psqldb
     * @param   databaseURL the URL at which the database can be accessed
     * @param   username    the user to access the database as
     * @param   password    the password with which to access the database
     * TODO     perhaps change this to public Database connect(...) and remove all public constructors
     */
    public Database(String databaseURL, String username, String password) throws PSQLException {
        try {
            Class.forName("org.postgresql.Driver");
            connection = DriverManager
                    .getConnection(databaseURL,
                            username,
                            password);
        } catch (Exception e) {
            genericHandleException(e);
        }
        System.out.println("Opened database successfully");
    }

    /**
     * Creates a new table in the open database
     * @param   tableDefinition    The definition of the table to create; this should include the entire sql command
     */
    public void createTable(String tableDefinition) {
        Statement statement;

        try {
            statement = connection.createStatement();
            statement.executeUpdate(tableDefinition);
            statement.close();
        } catch (Exception e) {
            genericHandleException(e);
        }
    }

    private void genericHandleException(Exception e) {
        e.printStackTrace();
        System.err.println(e.getClass().getName() + ": " + e.getMessage());
    }
}
