package DiscordMe;

import org.postgresql.util.PSQLException;


import java.sql.*;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * A basic scraper for discord.me
 * @author  Nyefan
 * contact  nyefancoding@gmail.com
 * github   github.com/nyefan
 * @version 1.0
 * @since   2016-12(DEC)-08
 * depends  posgresql-9.4.1212
 */
public class Database {

    private Connection connection;

    /**
     * Prevents this object from being created without being connected to a database
     */
    private Database() {
        throw new IllegalStateException("This contructor should not be used.");
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
            connection.setAutoCommit(false);
        } catch (ClassNotFoundException | SQLException e) {
            genericHandleException(e);
        }
        System.out.println("Opened database successfully");
    }

    /**
     * Creates a new table in the open database
     * @param   tableDefinition    The definition of the table to create; this should include the entire sql command
     */
    public void createTable(String tableDefinition) {
        try {
            directStatement(tableDefinition);
            commit();
        } catch (SQLException sqle) {
            genericHandleException(sqle);
        }
    }

    /**
     * Inserts the provided row of data into the psql data in a non-generic way
     * @param   tableName       The name of the table to which append this row of data
     * @param   pullNumber      The ID number of this scrape
     * @param   ldt             The DateTime at which the scrape was acquired - this should be UTC
     * @param   searchTerm      The tag which the data represents
     * @param   rankings        The list of servers associated with this tag
     * @return  The PSQL string that will be used to insert the data
     * @throws  SQLException    The input data is wrong or cannot be parsed
     */
    public String insert(String tableName, int pullNumber, LocalDateTime ldt, Optional<String> searchTerm, String[] rankings) throws SQLException {

        StringBuilder insertString = new StringBuilder(
            String.format("insert into %s (pullnumber, pulltime, searchterm, servername, rank) values ", tableName))
                .append(
                    IntStream.rangeClosed(1, rankings.length)
                        .mapToObj(i -> String.format(
                            "(%d, '%s', '%s', %s, %d)",
                            pullNumber,
                            ldt.toString(),
                            searchTerm.orElse("NULL"),
                            rankings[i-1],
                            i))
                        .collect(Collectors.joining(", ")))
                .append(";");


        directStatement(insertString.toString());

        return insertString.toString();
    }

    /**
     * Allows a generic PSQL query statement to be executed on the DB
     * The caller is responsible for closing the returned ResultSet
     * @param   query           The PSQL query statement to execute
     * @return  The result of the PSQL query
     * @throws  SQLException    The query could not be processed
     */
    public ResultSet directQuery(String query) throws SQLException {
        Statement statement;
        ResultSet results;
        statement = connection.createStatement();
        results = statement.executeQuery(query);

        return results;
    }

    /**
     * Allow a generic non-returning PSQL statement to be executed on the DB
     * @param statementString   The PSQL statement to execute
     * @throws SQLException     The statement could not be processed
     */
    public void directStatement(String statementString) throws SQLException {
        Statement statement = connection.createStatement();
        statement.executeUpdate(statementString);
        statement.close();
    }

    /**
     * After a set of statements has been queued into the DB, call this to commit the changes
     */
    public void commit() {
        try {
            connection.commit();
        } catch (SQLException sqle) {
            genericHandleException(sqle);
        }
    }

    /**
     * Closes the DB once the user has completed their task(s)
     * This should be called at the first opportunity to prevent memory leaks and to prevent transfer data overuse
     * @throws  SQLException    The Database is probably already closed
     */
    public void close() throws SQLException {
        connection.close();
    }

    /**
     * Print the exception's stack trace, name, and message; and attempt to continue execution
     * @param   e   The exception to "handle"
     */
    private void genericHandleException(Exception e) {
        e.printStackTrace();
        System.err.println(e.getClass().getName() + ": " + e.getMessage());
    }
}
