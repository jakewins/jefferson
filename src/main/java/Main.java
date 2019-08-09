import valle.Analyzer;
import valle.Analyzer2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Main
{
    public static void main(String args[]) throws IOException
    {
        String search = args[0];
        List<Path> journals =
                Files.list( Path.of( "./journals" ) ).filter(
                        p -> p.getFileName().toString().endsWith( ".txt" ) )
                        .collect( Collectors.toList() );

        List<Analyzer.Action> actions = new ArrayList<>();
        int i=0;
        try
        {
            for (; i < journals.size(); i++ )
            {
                actions.addAll(analyze( journals.get( i ) ));
            }
        } catch(Exception e) {
            System.err.printf("%d/%d%n", i, journals.size() );
            throw e;
        }

        int absent=0, withMajority=0, total=0;
        for ( Analyzer.Action event : actions )
        {
            if(!event.vote().isRepInvolved( search )) {
                continue;
            }
            String motion = event.motion().proposal.replace( ",", " " ).trim();
            switch(event.vote().voteOfRep( search )) {

            case AYES:
                total++;
                if(event.vote().noes.size() > 30) {
                    System.out.printf("\"%s\",y,%d,%d,%s,%d%n", motion, event.vote().ayes.size(), event.vote().noes.size(), event.source().sourceUrl, event.source().lineNo);
                }
                if(event.vote().noes.size() < event.vote().ayes.size()) {
                    withMajority++;
                }
                break;
            case NOES:
                total++;
                if(event.vote().ayes.size() > 30) {
                    System.out.printf("\"%s\",n,%d,%d,%s,%d%n", motion, event.vote().ayes.size(), event.vote().noes.size(), event.source().sourceUrl, event.source().lineNo);
                }
                if(event.vote().noes.size() > event.vote().ayes.size()) {
                    withMajority++;
                }
                break;
            case ABSENT:
            case ABSENT_WITH_LEAVE:
                total++;
                absent++;
                break;
            default:
                break;
            }
        }
        System.out.printf("Total: %d%n", total);
        System.out.printf("WithMajority: %d (%f%%)%n", withMajority, ((double)withMajority)/((double)total));
        System.out.printf("Absent: %d (%f%%)%n", absent, ((double)absent)/((double)total));
    }

    private static List<Analyzer.Action> analyze( Path path ) throws IOException
    {
        System.err.printf("Analyzing %s..%n", path.getFileName() );
        String raw = Files.readString( path );

        String pdfFile = path.getFileName().toString().replace( ".txt", "" );

        return new Analyzer2().analyze(
                String.format( "https://house.mo.gov/billtracking/bills191/jrnpdf/%s", pdfFile ),
                Arrays.asList( raw.split( "\n" ) ) );
    }
}
