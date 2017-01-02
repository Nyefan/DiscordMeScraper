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

    private static String[] searchTerms = new String[]{""};
    private static int maxPages = -1;
    private static int pullNumber;
    private static Database db;
    private static JSONObject queryParameters;
    private static String queryType = "file";

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
                .map(i -> dollarQuote + i.trim().substring(0, Math.min(i.trim().length(), 50)) + dollarQuote)//TODO: kill magic number
                .toArray(String[]::new);
    }

    /**
     * Returns a list of Server Names in the order they are ranked by discord.me for a given tag and set of (inclusive)
     * pages.  The caller is responsible for ensuring that first <= last
     *
     * @param searchTerm The tag to be entered in the search box on discord.me
     * @param first      The first page (set of 32) in the range to query
     * @param last       The last page (set of 32) in the range to query
     * @return an array of Server Names for the queried search term from the queried page range
     * TODO early termination for empty pages
     */
    private static String[] queryPages(String searchTerm, int first, int last) {
        if (last == -1) {
            return queryAllPages(searchTerm);
        }

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
     * Returns a list of all Server Names in the order they are ranked by discord.me for a given tag.
     *
     * @param searchTerm The tag to be entered in the search box on discord.me
     * @return an array of Server Names for the queried search term
     */
    private static String[] queryAllPages(String searchTerm) {
        return queryPages(searchTerm, 1, findLastPage(searchTerm));
    }

    /**
     * Helper function for queryAllPages(String).  Returns the maximum page number on which results exist for a given
     * search term
     *
     * @param searchTerm The tag to be entered in the search bos on discord.me
     * @return the maximum page number on which results exist for the queried search term
     */
    private static int findLastPage(String searchTerm) {
        int lowerBound = 0;
        int upperBound = 32; //This is high because a binomial search down will be faster as a smaller page is served by discord.me
        boolean upperBoundFound = false;
        boolean lastPageFound = false;
        String[] qp = new String[0];

        //find an upper bound
        while(!upperBoundFound) {
            try {
                qp = queryPage(searchTerm, upperBound);
            } catch (JauntException e) {
                e.printStackTrace();
                System.exit(5);
            }
            if(qp.length == 0) {
                upperBoundFound = true;
            } else {
                lowerBound = upperBound;
                upperBound = 2*upperBound;
            }
        }

        //binary search
        //This could be made while(true), moving the return statement to the final if, but this variation is clearer in intent, imo
        while(!lastPageFound) {
            int testPageNumber = (upperBound-lowerBound)/2+lowerBound;
            try {
                qp = queryPage(searchTerm, testPageNumber);
            } catch (JauntException e) {
                e.printStackTrace();
                System.exit(5);
            }
            if(qp.length == 0) {
                upperBound = testPageNumber;
            } else {
                lowerBound = testPageNumber;
            }

            if(lowerBound+1==upperBound) {
                lastPageFound = true;
            }
        }

        return lowerBound;
    }

    /**
     * Abstracts the initialization out of the main function
     */
    private static void tryInitialization() {
        //Ensure that the Jaunt license is not violated
        if (LocalDateTime.now().isAfter(LocalDate.of(2017, 2, 1).atStartOfDay())) {
            System.err.println("The Jaunt license has expired.  Please download the newest version to refresh the license.");
            System.exit(2);
        }

        //Load SEARCHTERMS.json
        try {
            queryParameters = (JSONObject) new JSONParser().parse(new FileReader("resources/SEARCHTERMS.json"));
        } catch (ParseException | IOException e) {
            //TODO: set up a logging module
            //for now, do nothing; the program will use default values
        }

        //Load the search terms from SEARCHTERMS.json, defaulting to an empty string
        try {
            searchTerms = Stream
                    .of(((JSONArray) queryParameters.get("search_terms")).toArray())
                    .map(i -> (String) i)
                    .toArray(String[]::new);
        } catch (NullPointerException e) {
            //do nothing; the program will use the default value
        }

        //Load the maximum number of pages to query from SEARCHTERMS.json, defaulting to -1 (all)
        try {
            String tmpMaxPages = (String) queryParameters.get("max_pages");
            maxPages = (tmpMaxPages.equalsIgnoreCase("all")) ? -1 : Integer.parseInt(tmpMaxPages);
        } catch (NullPointerException e) {
            //do nothing; the program will use the default value
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
                final JSONObject connectionParameters = (JSONObject) new JSONParser().parse(new FileReader("resources/DBINFO.json"));

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
