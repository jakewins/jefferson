package jefferson;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import jefferson.analyzer.Analyzer;
import jefferson.domain.Action;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Main
{
    public static void main(String ... args) throws IOException
    {
        String session = args[0];
        List<Path> journals =
                Files.list( Path.of( "./journals/" + session ) )
                        .filter( p -> p.getFileName().toString().endsWith( ".txt" ) )
                        .collect( Collectors.toList() );

        List<Map<String, Object>> actions = new ArrayList<>();
        int i=0;
        try
        {
            Analyzer analyzer = new Analyzer();
            for (; i < journals.size(); i++ )
            {
                Path path = journals.get( i );
                System.err.printf("Analyzing %s..%n", path.getFileName() );
                String raw = Files.readString( path );

                String pdfFile = path.getFileName().toString().replace( ".txt", "" );

                for ( Action action : analyzer.analyze(
                        String.format( "https://house.mo.gov/billtracking/bills%s/jrnpdf/%s",
                                session, pdfFile ), Arrays.asList( raw.split( "\n" ) ) ) )
                {
                    actions.add( action.toMap() );
                }
            }
        } catch(Exception e) {
            System.err.printf("%d/%d%n", i, journals.size() );
            throw e;
        }

        ObjectMapper objectMapper = new ObjectMapper();
        ObjectWriter objectWriter = objectMapper.writerWithDefaultPrettyPrinter();
        System.out.println( objectWriter.writeValueAsString( actions ));
    }
}
