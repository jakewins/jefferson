package valle;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

public class Analyzer_Test
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

    public void testParseVoteBlock() {
        List<Analyzer.Event> events =
                new Analyzer().analyze( "http://example.com", Arrays.asList( ("\n" +
                        " On motion of Representative Eggleston, HCS HB 548, as amended, was ordered \n" +
                        "perfected and printed by the following vote, the ayes and noes having been demanded pursuant \n" +
                        "to Article III, Section 26 of the Constitution: \n" + " \n" +
                        "AYES: 078  \n" + " \n" + "Allred  Anderson  Bailey  Baker  Basye  \n" +
                        "Billington  Black 137  Bondon  Bromley  Busick  \n" +
                        "Chipman  Christofanelli  Coleman 32  Coleman 97  Deaton  \n" +
                        "DeGroot  Dinkins  Dohrman  Eggleston  Eslinger  \n" +
                        "Falkner III  Fitzwater  Francis  Gregory  Grier  \n" +
                        "Griesheimer  Griffith  Haffner  Hannegan  Hansen  \n" +
                        "Helms  Henderson  Hill  Hovis  Hudson  \n" +
                        "Kelly 141  Knight  Kolkmeyer  Lovasco  Lynch  \n" +
                        "Mayhew  Messenger  Miller  Morris 140  Morse 151  \n" +
                        "O'Donnell  Patterson  Pike  Pollitt 52  Pollock 123  \n" +
                        "Porter  Toalson Reisch  Remole  Richey  Riggs  \n" +
                        "Roberts 161  Rone  Ruth  Schnelting  Schroer  \n" +
                        "Sharpe  Shaul 113  Shawan  Simmons  Smith  \n" +
                        "Sommer  Spencer  Stacy  Tate  Taylor  \n" +
                        "Trent  Veit  Vescovo  Walsh  Wiemann  \n" +
                        "Wilson  Wright  Mr. Speaker                \n" +
                        " \n" +
                        "NOES: 072  \n" +
                        " \n" + "Andrews  Appelbaum  Bangert  Baringer  Barnes  \n" +
                        "Beck  Black 7  Bland Manlove  Bosley  Brown 27  \n" +
                        "Brown 70  Burnett  Burns  Butz  Carpenter  \n" +
                        "Chappelle-Nadal  Clemens  Dogan  Ellebracht  Ellington  \n" +
                        "Evans  Fishel  Franks Jr.  Gannon  Gray  \n" +
                        "Green  Haden  Houx  Hurst  Ingle  \n" +
                        "Justus  Kelley 127  Kendrick  Kidd  Lavender  \n" +
                        "Love  Mackey  McCreery  McDaniel  McGaugh  \n" +
                        "McGee  McGirl  Merideth  Mitten  Moon  \n" +
                        "Morgan  Mosley  Murphy  Neely  Pfautsch  \n" +
                        "Pierson Jr.  Pogue  Price  Quade  Razer  \n" +
                        "Reedy  Rehder  Roberts 77  Rogers  Rowland  \n" +
                        "Runions  Sain  Sauls  Shields  Stephens 128  \n" +
                        "Stevens 46  Swan  Unsicker  Walker  Washington  \n" +
                        "Windham  Wood                       \n" + " \n" + "PRESENT: 000  \n" +
                        " \n" + "ABSENT WITH LEAVE: 011  \n" + " \n" +
                        "Carter  Hicks  Muntzel  Pietzman  Plocher  \n" +
                        "Proudie  Roden  Roeber  Ross  Shull 16  \n" +
                        "Solon                              \n" +
                        " \n" +
                        "VACANCIES: 002  \n" +
                        "1446 Journal of the House ").split( "\n" ) ) );

        assert events.size() == 1: String.format("There should be one event, a vote, found %s", events);

        Analyzer.Event event = events.get( 0 );
        Analyzer.Vote vote = event.vote();
        Analyzer.Vote expectedVote =
                new Analyzer.Vote(
                        new String[]{"Allred", "Anderson", "Bailey", "Baker", "Basye", "Billington", "Black 137", "Bondon", "Bromley", "Busick", "Chipman", "Christofanelli", "Coleman 32", "Coleman 97", "Deaton", "DeGroot", "Dinkins", "Dohrman", "Eggleston", "Eslinger", "Falkner III", "Fitzwater", "Francis", "Gregory", "Grier", "Griesheimer", "Griffith", "Haffner", "Hannegan", "Hansen", "Helms", "Henderson", "Hill", "Hovis", "Hudson", "Kelly 141", "Knight", "Kolkmeyer", "Lovasco", "Lynch", "Mayhew", "Messenger", "Miller", "Morris 140", "Morse 151", "O'Donnell", "Patterson", "Pike", "Pollitt 52", "Pollock 123", "Porter", "Toalson Reisch", "Remole", "Richey", "Riggs", "Roberts 161", "Rone", "Ruth", "Schnelting", "Schroer", "Sharpe", "Shaul 113", "Shawan", "Simmons", "Smith", "Sommer", "Spencer", "Stacy", "Tate", "Taylor", "Trent", "Veit", "Vescovo", "Walsh", "Wiemann", "Wilson", "Wright", "Mr. Speaker"},
                        new String[]{"Andrews", "Appelbaum", "Bangert", "Baringer", "Barnes", "Beck", "Black 7", "Bland Manlove", "Bosley", "Brown 27", "Brown 70", "Burnett", "Burns", "Butz", "Carpenter", "Chappelle-Nadal", "Clemens", "Dogan", "Ellebracht", "Ellington", "Evans", "Fishel", "Franks Jr.", "Gannon", "Gray", "Green", "Haden", "Houx", "Hurst", "Ingle", "Justus", "Kelley 127", "Kendrick", "Kidd", "Lavender", "Love", "Mackey", "McCreery", "McDaniel", "McGaugh", "McGee", "McGirl", "Merideth", "Mitten", "Moon", "Morgan", "Mosley", "Murphy", "Neely", "Pfautsch", "Pierson Jr.", "Pogue", "Price", "Quade", "Razer", "Reedy", "Rehder", "Roberts 77", "Rogers", "Rowland", "Runions", "Sain", "Sauls", "Shields", "Stephens 128", "Stevens 46", "Swan", "Unsicker", "Walker", "Washington", "Windham", "Wood"},
                        new String[]{},
                        new String[]{"Shull 16", "Proudie", "Roden", "Muntzel", "Pietzman","Solon", "Ross", "Carter", "Hicks", "Plocher", "Roeber"},
                        new String[]{} );
        assert vote == expectedVote : String.format("Vote does not match expected. Diff: %n  %s%n", vote.diff(expectedVote).replace( "\n", "\n  " ));
    }
}
