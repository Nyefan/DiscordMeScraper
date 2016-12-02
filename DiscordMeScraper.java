/**
 * Created by Nyefan on 12/1/2016.
 * Contact at nyefancoding@gmail.com
 * or through Github at github.com/nyefan
 *
 * @author  Nyefan
 * @contact  nyefancoding@gmail.com
 * @github  github.com/nyefan
 * @version 1.0
 * @since   2016-12(DEC)-02
 * @depends jaunt
 * @TODO    allow a plaintext file of queries to be passed in
 * @TODO    allow a plaintext file to be specified for output
 * @TODO    code in a hard End Of Life in keeping with the jaunt license
 */

import com.jaunt.Element;
import com.jaunt.JauntException;
import com.jaunt.UserAgent;

import java.util.stream.IntStream;

public class DiscordMeScraper {

    private static String searchTerm;
    private static int pageNumber;

    public static void main(String... args) {
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

        System.out.println("Discord.me rankings by term - " + searchTerm + ": ");
        System.out.println("Pages 1-" + pageNumber);

        IntStream.rangeClosed(1, pageNumber)
                .forEach(i -> {
                    try {
                        queryPage(searchTerm, i);
                    } catch (JauntException je) {
                        System.err.println(je);
                    }
                });

    }


    /**
     * Prints the results of the query to System.out
     * @param   searchTerm  The tag to be entered in the search box on discord.me
     * @param   pageNumber  The page (set of 32) to query; this must be less than 999/32
     * @throws  JauntException
     */
    private static void queryPage(String searchTerm, int pageNumber) throws JauntException {

        UserAgent userAgent = new UserAgent();
        String[] serverNames = userAgent.visit(String.format("https://discord.me/servers/%s/%s", pageNumber, searchTerm))
                .findFirst("<div class=col-md-8>")
                .findEvery("<span class=server-name>")
                .toList()
                .stream()
                .map(Element::innerHTML)
                .toArray(String[]::new);
        IntStream.rangeClosed(1, serverNames.length)
                //This will break if the number of servers queried is over 999
                .mapToObj(i -> String.format("%4s: %s", "#" + (32*(pageNumber-1)+i), serverNames[i-1]))
                .forEach(System.out::println);
    }

}
