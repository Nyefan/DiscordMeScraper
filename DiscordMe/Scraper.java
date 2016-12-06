package DiscordMe;

import com.jaunt.Element;
import com.jaunt.JauntException;
import com.jaunt.UserAgent;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.stream.IntStream;


/**
 * A basic scraper for discord.me
 * @author  Nyefan
 * contact  nyefancoding@gmail.com
 * github   github.com/nyefan
 * @version 1.1
 * @since   2016-12(DEC)-02
 * depends  jaunt
 * TODO     alter the input parsing to handle options
 * TODO     allow a plaintext file of queries to be passed in
 * TODO     allow a plaintext file to be specified for output
 */
public class Scraper {

    private static String searchTerm;
    private static int pageNumber;

    /**
     * @param   args    arg[0] should be the tag to search for; arg[1] should be the number of pages to scrape
     * exit     1       no search term was input
     * exit     2       the jaunt license has expired
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

        QueryAndPrintResultsToConsole(searchTerm, pageNumber);

    }

    /**
     * Completes the query and prints the results to the console
     * @param   searchTerm  The tag to be entered in the search box on discord.me
     * @param   pageNumber  The number of pages (set of 32) to query, starting from 1
     */
    private static void QueryAndPrintResultsToConsole(String searchTerm, int pageNumber) {
        System.out.println("Discord.me rankings by term - " + searchTerm + ": ");
        System.out.println("Pages 1-" + pageNumber);

        IntStream.rangeClosed(1, pageNumber)
                .forEach(i -> {
                    try {
                        String[] serverNames = queryPage(searchTerm, i);
                        IntStream.rangeClosed(1, serverNames.length)
                                //This will break non-catastrophically if the number of servers queried is over 999
                                .mapToObj(j -> String.format("%4s: %s", "#" + (32*(i-1)+j), serverNames[j-1]))
                                .forEach(System.out::println);
                    } catch (JauntException je) {
                        System.err.println(je);
                    }
                });
    }

    /**
     * Returns a list of Server Names in the order they are ranked by discord.me for a given tag
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

}
