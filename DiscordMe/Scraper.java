package DiscordMe;

import com.jaunt.Element;
import com.jaunt.JauntException;
import com.jaunt.UserAgent;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.postgresql.util.PSQLException;

import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.stream.IntStream;


/**
 * A basic scraper for discord.me
 * @author  Nyefan
 * contact  nyefancoding@gmail.com
 * github   github.com/nyefan
 * @version 1.1
 * @since   2016-12(DEC)-08
 * depends  jaunt1.2.3, json-simple-1.1.1
 * @implNote
 * TODO     alter the input parsing to handle options
 * TODO     allow a plaintext file of queries to be passed in
 * TODO     allow a plaintext file to be specified for output
 * TODO     alter the json scheme to use an Oauth token or similar security measure
 */
public class Scraper {

    private static String searchTerm;
    private static int pageNumber;

    /**
     * @param   args    arg[0] should be the tag to search for; arg[1] should be the number of pages to scrape
     * exit     1       no search term was input
     * exit     2       the jaunt license has expired
     * exit     3       no valid json file containing server connection data has been provided
     * exit     4       the db_url, username, or password provided by DBINFO.json is invalid
     * exit     5       Discord.me is not available or has altered its layout
     */
    public static void main(String... args) {

        if (LocalDateTime.now().isAfter(LocalDate.of(2017, 1, 1).atStartOfDay())) {
            System.err.println("The Jaunt license has expired.  Please download the newest version to refresh the license.");
            System.exit(2);
        }

        try {
            searchTerm = args[0];
        } catch (IndexOutOfBoundsException ioobe) {
            System.err.println(ioobe);
            System.exit(1);
        }

        try {
            pageNumber = Integer.parseInt(args[1]);
        } catch (IndexOutOfBoundsException ioobe) {
            pageNumber = 1;
        }

        try {
            JSONObject connectionParameters = (JSONObject) new JSONParser().parse(new FileReader("DBINFO.json"));
            Database db = new Database(connectionParameters.get("db_url").toString(),
                    connectionParameters.get("user").toString(),
                    connectionParameters.get("token").toString());
            //TODO: insert the query results into the database
            QueryAndPrintResultsToConsole(searchTerm, pageNumber);
        } catch (ParseException | IOException e) {
            e.printStackTrace();
            System.exit(3);
        } catch (PSQLException e) {
            e.printStackTrace();
            System.exit(4);
        }

    }

    /**
     * Completes the query and prints the results to the console
     * @param   searchTerm  The tag to be entered in the search box on discord.me
     * @param   pageNumber  The number of pages (set of 32) to query, starting from 1
     */
    private static void QueryAndPrintResultsToConsole(String searchTerm, int pageNumber) {
        System.out.println("Discord.me rankings by term - " + searchTerm + ": ");
        System.out.println("Pages 1-" + pageNumber);

        String[] serverNames = queryPages(searchTerm, 1, pageNumber);
        IntStream.rangeClosed(1, serverNames.length)
                //This will break non-catastrophically if the number of servers queried is over 999
                .mapToObj(i -> String.format("%4s: %s", "#" + i, serverNames[i-1]))
                .forEach(System.out::println);
    }

    /**
     * Returns a list of Server Names in the order they are ranked by discord.me for a given tag and page
     * @param   searchTerm  The tag to be entered in the search box on discord.me
     * @param   pageNumber  The page (set of 32) to query
     * @return  An array of Server Names from the queried page - length 32
     */
    private static String[] queryPage(String searchTerm, int pageNumber) throws JauntException {

        UserAgent userAgent = new UserAgent();
        return userAgent.visit(String.format("https://discord.me/servers/%s/%s", pageNumber, searchTerm))
                .findFirst("<div class=col-md-8>")
                .findEvery("<span class=server-name>")
                .toList()
                .stream()
                .map(Element::innerHTML)
                .toArray(String[]::new);
    }

    /**
     * Returns a list of Server Names in the order they are ranked by discord.me for a given tag and set of (inclusive)
     * pages.  The caller is responsible for ensuring that first <= last
     * @param   searchTerm    The tag to be entered in the search box on discord.me
     * @param   first         The first page (set of 32) in the range to query
     * @param   last          The last page (set of 32) in the range to query
     * @return  an array of Servery Names from the queried page range
     */
    private static String[] queryPages(String searchTerm, int first, int last) {
        return IntStream.rangeClosed(first, last)
                .mapToObj((int i) -> {
                    String[] qp = new String[0];
                    try {
                        qp = queryPage(searchTerm, i);
                    } catch (JauntException e) {
                        e.printStackTrace();
                        //This is preferable to writing an incomplete list to the db
                        System.exit(5);
                    }
                    return qp;
                })
                .flatMap(Arrays::stream)
                .toArray(String[]::new);
    }

}
