package DiscordMe;

import com.jaunt.Element;
import com.jaunt.JauntException;
import com.jaunt.UserAgent;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Scanner;
import java.util.stream.IntStream;
import java.util.stream.Stream;


/**
 * A basic scraper for discord.me
 * depends  jaunt1.2.3, json-simple-1.1.1
 * SEARCHTERMS.json:query_type can take values "file", "console", or "database"
 * @author Nyefan
 *         contact  nyefancoding@gmail.com
 *         github   github.com/nyefan
 * @version 1.2
 * @since 2016-12(DEC)-11
 * TODO: enumerate the possible values of "queryType"
 * TODO: consider using a switch-case table for the queryTypes in Scraper::main
 */
public class Scraper {

    private static String[] searchTerms;
    private static int maxPages;
    private static int pullNumber;
    private static Database db;
    private static JSONObject queryParameters;
    private static String queryType = "database";

    /**
     * exit     1       no valid json file containing the query parameters has been provided
     * exit     2       the jaunt license has expired
     * exit     3       no valid json file containing server connection data has been provided
     * exit     4       the db_url, username, or password provided by DBINFO.json is invalid
     * exit     5       Discord.me is not available or has altered its layout
     */
    public static void main(String... args) {

        tryInitialization();

        if (queryType.equalsIgnoreCase("database")) {
            Arrays.stream(searchTerms)
                    .parallel()
                    .forEach(Scraper::queryAndInsertResultsInDatabase);
            db.commit();
        } else if (queryType.equalsIgnoreCase("console")) {
            Arrays.stream(searchTerms)
                    .forEachOrdered(Scraper::queryAndPrintResultsToConsole);
        } else if (queryType.equalsIgnoreCase("file")) {
            Arrays.stream(searchTerms)
                    .parallel()
                    .forEach(Scraper::queryAndPrintResultsToFile);
        }
    }

    /**
     * Performs the scrape for a single searchTerm and prints the results to the console
     * @param term The tag to be entered in the search box on discord.me
     */
    private static void queryAndPrintResultsToConsole(String term) {
        System.out.println(LocalDateTime.now(ZoneId.of("UTC")).format(DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")));
        System.out.println("Discord.me rankings by term - " + term + ": ");
        System.out.println("Pages 1-" + maxPages);

        String[] serverNames = queryPages(term, 1, maxPages);
        IntStream.rangeClosed(1, serverNames.length)
                //This will break non-catastrophically if the number of servers queried is over 999
                .mapToObj(i -> String.format("%4s: %s", "#" + i, serverNames[i - 1].replace("$ServerName$", "")))
                .forEach(System.out::println);
    }

    /**
     * Performs the scrape for a single searchTerm and prints the results to the local file "<searchTerm>.out"
     * @param term The term to query
     */
    private static void queryAndPrintResultsToFile(String term) {
        String[] serverNames = queryPages(term, 1, maxPages);
        String nonNullTerm = (term==null|term.equals(""))?"Front Page":term;
        String outputFolderName = "results";
        Path outputFolderPath = Paths.get(outputFolderName);
        Path outputFilePath = Paths.get(String.format("%s/%s.out", outputFolderName, nonNullTerm));

        try {
            if (Files.notExists(outputFolderPath)) {
                Files.createDirectory(outputFolderPath);
            }

            if (Files.notExists(outputFilePath)) {
                Files.createFile(outputFilePath);
            }

            try (BufferedWriter outputFile = Files.newBufferedWriter(outputFilePath)) {
                //TODO: scrape the data and write it to the file
                outputFile.write(LocalDateTime.now(ZoneId.of("UTC")).format(DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")));
                outputFile.newLine();
                outputFile.write("Discord.me rankings by term - " + term + ": ");
                outputFile.newLine();
                outputFile.write("Pages 1-" + maxPages);
                outputFile.newLine();

                IntStream.rangeClosed(1, serverNames.length)
                        //This will break non-catastrophically if the number of servers queried is over 999
                        .mapToObj(i -> String.format("%4s: %s", "#" + i, serverNames[i - 1].replace("$ServerName$", "")))
                        .forEach((str) -> {
                            try {
                                outputFile.write(str);
                                outputFile.newLine();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });
                System.out.println(String.format("Inserting results of query '%s'...Done!", term));
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    /**
     * Performs the scrape for a single searchTerm and pushes that result to the Database
     * @param term The term to query
     */
    private static void queryAndInsertResultsInDatabase(String term) {
        try {
            db.insert(
                    "rankings",
                    pullNumber,
                    LocalDateTime.now(ZoneId.of("UTC")),
                    term,
                    queryPages(term, 1, maxPages));
            System.out.println(String.format("Inserting results of query '%s'...Done!", term));
        } catch (SQLException e) {
            System.err.println(String.format("Search Term '%s' failed.", term));
            e.printStackTrace();
        }
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
                .map(i -> dollarQuote + i + dollarQuote)
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
     * TODO early termination for empty pages
     */
    private static String[] queryPages(String searchTerm, int first, int last) {
        return IntStream
                .rangeClosed(first, last)
                .parallel()
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
        //Ensure that the Jaunt license is not violated
        if (LocalDateTime.now().isAfter(LocalDate.of(2017, 1, 1).atStartOfDay())) {
            System.err.println("The Jaunt license has expired.  Please download the newest version to refresh the license.");
            System.exit(2);
        }

        //Load the search terms from SEARCHTERMS.json
        try {
            queryParameters = (JSONObject) new JSONParser().parse(new FileReader("SEARCHTERMS.json"));
            searchTerms = Stream
                    .of(((JSONArray) queryParameters.get("search_terms")).toArray())
                    .map(i -> (String) i)
                    .toArray(String[]::new);
            maxPages = Integer.parseInt((String) queryParameters.get("max_pages"));
        } catch (ParseException | IOException | NullPointerException e) {
            e.printStackTrace();
            System.exit(1);
        }

        //Determine the query type
        try {
            queryType = (String) queryParameters.getOrDefault("query_type", queryType);
        } catch (ClassCastException | NullPointerException e) {
            //do nothing; the program will perform the default type of query
        }

        //Connect to the database if appropriate
        if(queryType.equalsIgnoreCase("database")) {
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
                    System.out.print("Please enter your token: ");
                    pass = in.nextLine();
                }

                db = new Database(db_url, user, pass);
            } catch (ParseException | IOException e) {
                e.printStackTrace();
                System.exit(3);
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
}
