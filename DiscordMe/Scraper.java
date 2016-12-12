package DiscordMe;

import com.jaunt.Element;
import com.jaunt.JauntException;
import com.jaunt.UserAgent;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.postgresql.util.PSQLException;

import java.io.FileReader;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Optional;
import java.util.Scanner;
import java.util.stream.IntStream;
import java.util.stream.Stream;


/**
 * A basic scraper for discord.me
 *
 * @author Nyefan
 *         contact  nyefancoding@gmail.com
 *         github   github.com/nyefan
 * @version 1.2
 * @since 2016-12(DEC)-11
 * depends  jaunt1.2.3, json-simple-1.1.1
 * TODO ensure this isn't the source of external connections failing
 */
public class Scraper {

    private static String[] searchTerms;
    private static int maxPages;
    private static int pullNumber;
    private static Database db;

    /**
     * exit     1       no valid json file containing the query parameters has been provided
     * exit     2       the jaunt license has expired
     * exit     3       no valid json file containing server connection data has been provided
     * exit     4       the db_url, username, or password provided by DBINFO.json is invalid
     * exit     5       Discord.me is not available or has altered its layout
     */
    public static void main(String... args) {

        tryInitialization();

        IntStream.rangeClosed(1, searchTerms.length)
                .forEach(i -> {
                    try {
                        System.out.print(String.format("Inserting results of query '%s'...", searchTerms[i - 1]));
                        db.insert(
                                "rankings",
                                pullNumber,
                                LocalDateTime.now(ZoneId.of("UTC")),
                                Optional.ofNullable(searchTerms[i - 1]),
                                queryPages(searchTerms[i - 1], 1, maxPages));
                        System.out.println("Done!");
                    } catch (SQLException e) {
                        System.err.println(String.format("Search Term '%s' failed.", searchTerms[i - 1]));
                        e.printStackTrace();
                    }
                });

        db.commit();

    }

    /**
     * Completes the query and prints the results to the console
     *
     * @param searchTerm The tag to be entered in the search box on discord.me
     * @param pageNumber The number of pages (set of 32) to query, starting from 1
     */
    private static void queryAndPrintResultsToConsole(String searchTerm, int pageNumber) {
        System.out.println(LocalDateTime.now(ZoneId.of("UTC")).format(DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")));
        System.out.println("Discord.me rankings by term - " + searchTerm + ": ");
        System.out.println("Pages 1-" + pageNumber);

        String[] serverNames = queryPages(searchTerm, 1, pageNumber);
        IntStream.rangeClosed(1, serverNames.length)
                //This will break non-catastrophically if the number of servers queried is over 999
                .mapToObj(i -> String.format("%4s: %s", "#" + i, serverNames[i - 1].replace("$ServerName$", "")))
                .forEach(System.out::println);
    }

    /**
     * Returns a list of Server Names in the order they are ranked by discord.me for a given tag and page
     *
     * @param searchTerm The tag to be entered in the search box on discord.me
     * @param pageNumber The page (set of 32) to query
     * @return An array of Server Names from the queried page - length 32
     */
    private static String[] queryPage(String searchTerm, int pageNumber) throws JauntException {

        final String dollarQuote = "$ServerName$";


        UserAgent userAgent = new UserAgent();
        return userAgent.visit(String.format("https://discord.me/servers/%s/%s", pageNumber, searchTerm))
                .findFirst("<div class=col-md-8>")
                .findEvery("<span class=server-name>")
                .toList()
                .stream()
                .map(Element::innerHTML)
                .map(i -> new StringBuilder(dollarQuote).append(i).append(dollarQuote).toString())
                .toArray(String[]::new);
    }

    /**
     * Returns a list of Server Names in the order they are ranked by discord.me for a given tag and set of (inclusive)
     * pages.  The caller is responsible for ensuring that first <= last
     *
     * @param searchTerm The tag to be entered in the search box on discord.me
     * @param first      The first page (set of 32) in the range to query
     * @param last       The last page (set of 32) in the range to query
     * @return an array of Server Names from the queried page range
     */
    private static String[] queryPages(String searchTerm, int first, int last) {
        return IntStream
                .rangeClosed(first, last)
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

    /**
     * Abstracts the initialization out of the main function
     */
    private static void tryInitialization() {
        if (LocalDateTime.now().isAfter(LocalDate.of(2017, 1, 1).atStartOfDay())) {
            System.err.println("The Jaunt license has expired.  Please download the newest version to refresh the license.");
            System.exit(2);
        }

        try {
            final JSONObject queryParameters = (JSONObject) new JSONParser().parse(new FileReader("SEARCHTERMS.json"));
            searchTerms = Stream
                    .of(((JSONArray) queryParameters.get("search_terms")).toArray())
                    .map(i -> (String) i)
                    .toArray(String[]::new);
            maxPages = Integer.parseInt((String) queryParameters.get("max_pages"));
        } catch (ParseException | IOException | NullPointerException e) {
            e.printStackTrace();
            System.exit(1);
        }

        try {
            final JSONObject connectionParameters = (JSONObject) new JSONParser().parse(new FileReader("DBINFO.json"));

            String db_url = (String) connectionParameters.get("db_url");
            String user = (String) connectionParameters.get("user");
            String pass = (String) connectionParameters.get("token");

            final Scanner in = new Scanner(System.in);

            if (db_url == null) {
                System.out.print("Please enter the database url or fix DBINFO.json: ");
                db_url = in.nextLine();
            }

            if (user == null) {
                System.out.print("Please enter your username: ");
                user = in.nextLine();
            }

            if (pass == null) {
                System.out.println("Please enter your password: ");
                pass = in.nextLine();
            }

            db = new Database(db_url, user, pass);
        } catch (ParseException | IOException e) {
            e.printStackTrace();
            System.exit(3);
        } catch (PSQLException e) {
            e.printStackTrace();
            System.exit(4);
        } finally {
            try {
                if (db != null) {
                    db.close();
                }
            } catch (SQLException sqle) {
                sqle.printStackTrace();
            }
        }

        try {
            ResultSet pullNumberTable = db.directQuery("select max(pullnumber) from rankings");
            pullNumberTable.next();
            pullNumber = pullNumberTable.getInt(1) + 1;
            pullNumberTable.close();
        } catch (SQLException e) {
            e.printStackTrace();
            System.err.println("This should not happen - it means the sql query generated by the program is wrong.");
        }
    }
}
