package jefferson.analyzer;

public class Source
{
    public final String sourceUrl;
    public final int lineNo;

    public Source( String sourceUrl, int lineNo )
    {
        this.sourceUrl = sourceUrl;
        this.lineNo = lineNo;
    }

    @Override
    public String toString()
    {
        return "Source{" + "sourceUrl='" + sourceUrl + '\'' + ", lineNo=" + lineNo + '}';
    }
}
