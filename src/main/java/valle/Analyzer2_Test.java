package valle;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class Analyzer2_Test
{
    public static void main(String ... argv) throws Exception
    {
        boolean assertsEnabled = false;
        assert assertsEnabled = true;
        if(!assertsEnabled) {
            throw new AssertionError( "Assertions must be enabled to run tests." );
        }

        boolean passed = true;
        Class<?> cls = MethodHandles.lookup().lookupClass();
        for ( Method subject : cls.getDeclaredMethods() )
        {
            if( ! ( subject.getName().startsWith( "test" ) ) ) {
                continue;
            }
            try {
                subject.invoke( cls.getConstructors()[0].newInstance() );
                System.err.printf("PASS: %s%n", subject.getName());
            } catch( InvocationTargetException e ) {
                if(e.getCause() != null && e.getCause() instanceof AssertionError ) {
                    AssertionError ae = (AssertionError) e.getCause();
                    System.err.printf("FAIL: %s: %s @ %s%n", subject.getName(), ae.getMessage(), ae.getStackTrace()[0].toString());
                    passed = false;
                } else {
                    throw e;
                }
            }
        }

        if(!passed) {
            System.exit( 1 );
        }
    }

    public void testParseMotionOrder() throws Exception {
        List<Analyzer.Action> actions =
                new Analyzer2().analyze( "http://example.com", loadJournal( "HB10_taken_up_amended_and_laid_over.txt" ) );

        Analyzer.Motion hb10 = new Analyzer.Motion( Analyzer.Motion.Type.MAIN_MOTION, "HCS HB 10", null );
        assertEq( new Analyzer2.AdoptWithoutVote( new Analyzer.Motion( Analyzer.Motion.Type.AMEND, "House Amendment 2 of HCS HB 10", hb10 ) ), actions.get( 0 ) );
        assertEq( new Analyzer2.AdoptWithoutVote( new Analyzer.Motion( Analyzer.Motion.Type.AMEND, "House Amendment 3 of HCS HB 10", hb10 ) ), actions.get( 1 ) );
        assertEq( new Analyzer2.AdoptWithoutVote( new Analyzer.Motion( Analyzer.Motion.Type.AMEND, "House Amendment 4 of HCS HB 10", hb10 ) ), actions.get( 2 ) );
        assertEq( new Analyzer2.AdoptWithoutVote( new Analyzer.Motion( Analyzer.Motion.Type.AMEND, "House Amendment 5 of HCS HB 10", hb10 ) ), actions.get( 3 ) );
        assertEq( new Analyzer2.DefeatedWithoutVote( new Analyzer.Motion( Analyzer.Motion.Type.AMEND, "House Amendment 6 of HCS HB 10", hb10 ) ), actions.get( 4 ) );

        assert actions.get( 5 ) instanceof Analyzer2.DefeatedByVote: String.format("Expected DefeatedByVote got %s", actions.get( 5 ));
        assertEq( 40, actions.get( 5 ).vote().ayes.size() );
        assertEq( 101, actions.get( 5 ).vote().noes.size() );
        assert actions.get( 6 ) instanceof Analyzer2.DefeatedByVote: String.format("Expected DefeatedByVote got %s", actions.get( 6 ));
        assertEq( 51, actions.get( 6 ).vote().ayes.size() );
        assertEq( 85, actions.get( 6 ).vote().noes.size() );
    }

    private void assertEq(Object expected, Object actual) {
        assert expected.equals(actual) : String.format("Expected \n  %s \nto be \n  %s", actual, expected);
    }

    private List<String> loadJournal(String name) throws IOException
    {
        try( InputStream in = getClass().getResourceAsStream( String.format( "/testdata/%s", name ) )) {
            if(in == null) {
                throw new AssertionError( String.format("Can't find %s", name) );
            }
            Scanner scanner = new Scanner( in ).useDelimiter( "\\A" );
            if(!scanner.hasNext()) {
                throw new AssertionError( String.format("%s is empty?", name) );
            }
            return Arrays.asList( scanner.next().split( "\n" ) );
        }
    }
}
