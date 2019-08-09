import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDocument;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.io.RandomAccessFile;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.text.PDFTextStripper;
import valle.Analyzer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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

        List<Analyzer.Event> events = new ArrayList<>();
        int i=0;
        try
        {
            for (; i < journals.size(); i++ )
            {
                events.addAll(analyze( journals.get( i ) ));
            }
        } catch(Exception e) {
            System.err.printf("%d/%d%n", i, journals.size() );
            throw e;
        }

        int absent=0, withMajority=0, total=0;
        for ( Analyzer.Event event : events )
        {
            if(!event.vote().isRepInvolved( search )) {
                continue;
            }
            String action = event.action().replace( ",", " " ).trim();
            switch(event.vote().voteOfRep( search )) {

            case AYES:
                total++;
                if(event.vote().noes.size() > 30) {
                    System.out.printf("'%s',y,%d,%d,%s,%d%n", action, event.vote().ayes.size(), event.vote().noes.size(), event.source().sourceUrl, event.source().lineNo);
                }
                if(event.vote().noes.size() < event.vote().ayes.size()) {
                    withMajority++;
                }
                break;
            case NOES:
                total++;
                if(event.vote().ayes.size() > 30) {
                    System.out.printf("'%s',n,%d,%d,%s,%d%n", action, event.vote().ayes.size(), event.vote().noes.size(), event.source().sourceUrl, event.source().lineNo);
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

    private static List<Analyzer.Event> analyze( Path path ) throws IOException
    {
        System.err.printf("Analyzing %s..%n", path.getFileName() );
        String raw = Files.readString( path );

        String pdfFile = path.getFileName().toString().replace( ".txt", "" );

        return new Analyzer().analyze(
                String.format( "https://house.mo.gov/billtracking/bills191/jrnpdf/%s", pdfFile ),
                Arrays.asList( raw.split( "\n" ) ) );
    }
}
