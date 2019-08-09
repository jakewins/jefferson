package valle;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

// Consumes a stream of lines, strips out page headers and footers, and joins line-broken sentences
// into single lines.
public class Sanitizer
{
    public static class Paragraph {
        public final Analyzer.Source source;
        public final String contents;

        public Paragraph( Analyzer.Source source, String contents )
        {
            this.source = source;
            this.contents = contents;
        }
    }

    public Iterable<Paragraph> sanitize(String url, Iterable<String> in) {
        List<Paragraph> out = new ArrayList<>();

        StringBuilder current = new StringBuilder();

        int lineNo = 1;
        for ( String line : in )
        {
            if(line.isBlank() || Patterns.header.matcher( line ).matches() ) {
                if(current.length() > 0) {
                    out.add( new Paragraph( new Analyzer.Source( url, lineNo ), current.toString() ) );
                    current = new StringBuilder();
                }
            } else {
                current.append( line );
                current.append( "  " );
            }
            lineNo++;
        }

        if(current.length() > 0) {
            out.add( new Paragraph( new Analyzer.Source( url, lineNo ), current.toString() ) );
        }
        return out;
    }
}
