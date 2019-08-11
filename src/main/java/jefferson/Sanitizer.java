package jefferson;

import jefferson.analyzer.Source;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

// Consumes a stream of lines, strips out page headers and footers, and joins line-broken sentences
// into single lines.
public class Sanitizer
{
    public static class Paragraph {
        public final Source source;
        public final String contents;

        public Paragraph( Source source, String contents )
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
            if(line.isBlank() || header.matcher( line ).matches() ) {
                if(current.length() > 0) {
                    out.add( new Paragraph( new Source( url, lineNo ), current.toString() ) );
                    current = new StringBuilder();
                }
            } else {
                current.append( line );
                current.append( "  " );
            }
            lineNo++;
        }

        if(current.length() > 0) {
            out.add( new Paragraph( new Source( url, lineNo ), current.toString() ) );
        }
        return out;
    }

    static Pattern header = Pattern.compile("(\\s*(\\d+)\\s*Journal of the House.*)|([a-zA-Z-]+ Day–[a-zA-Z]+, [a-zA-Z]+ \\d+, \\d+\\s*\\d+\\s*)");
}
