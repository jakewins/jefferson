package valle;

import org.apache.pdfbox.cos.COSDocument;
import org.apache.pdfbox.io.RandomAccessFile;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Strip
{
    public static void main(String args[]) throws IOException
    {
        List<Path> unstripped =
                Files.list( Path.of( "/home/jake/Code/toy/chesney/valle/journals" ) ).filter(
                        p -> p.getFileName().toString().endsWith( ".pdf" ) && !Files.exists(
                                p.getParent().resolve( p.getFileName() + ".txt" ) ) ).collect(
                        Collectors.toList() );

        for ( Path in : unstripped )
        {
            Path out = in.getParent().resolve( String.format("%s.txt", in.getFileName().toString()) );
            System.err.printf("Parsing %s..%n", in);
            String raw = parsePdf( in );

            Files.writeString( out, raw,
                    StandardOpenOption.CREATE_NEW );
        }

    }

    private static String parsePdf(Path path) throws IOException {
        PDFParser parser = new PDFParser(new RandomAccessFile( new File(path.toString()), "r"));
        parser.parse();
        try ( COSDocument cosDoc = parser.getDocument()) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText( new PDDocument(cosDoc) );
        }
    }
}
