package valle;

import valle.Sanitizer.Paragraph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Analyzer2
{
    private List<Analyzer.Action> out;
    private Analyzer.Motion mainMotion;
    private Analyzer.Motion activeMotion;
    private VoteContext activeVote;
    private State postVoteState;

    enum State {
        NEXT_MOTION {
            @Override
            State analyze( Analyzer2 ctx, Paragraph pg )
            {
                Matcher takenUp = Patterns.takenUp.matcher( pg.contents );
                if(takenUp.matches()) {
                    String bill = takenUp.group( 1 );
                    String rep = takenUp.group( 2 );
                    System.err.printf("%s taken up by %s%n", bill, rep );
                    ctx.mainMotion = new Analyzer.Motion( Analyzer.Motion.Type.MAIN, bill, null );
                    ctx.activeMotion = ctx.mainMotion;
                    return IN_MOTION;
                }
                return NEXT_MOTION;
            }
        },

        IN_MOTION {
            @Override
            State analyze( Analyzer2 ctx, Paragraph pg )
            {
                Matcher laidOver = Patterns.laidOver.matcher( pg.contents );
                if(laidOver.matches()) {
                    System.err.printf("%s was laid over%n", ctx.activeMotion.proposal);
                    if(ctx.activeMotion.type != Analyzer.Motion.Type.MAIN) {
                        throw new RuntimeException( String.format("Can non-main motions be laid over? At %s", pg.toString() ));
                    }
                    ctx.activeMotion = null;
                    ctx.mainMotion = null;
                    return NEXT_MOTION;
                }

                Matcher offeredAmendment = Patterns.offeredAmendment.matcher( pg.contents );
                if(offeredAmendment.matches()) {
                    String rep = offeredAmendment.group( 1 );
                    String amendmentNo = offeredAmendment.group( 2 );
                    System.err.printf("%s offers amending %s with Amendment %s%n", rep, ctx.activeMotion.proposal, amendmentNo );
                    ctx.activeMotion = new Analyzer.Motion( Analyzer.Motion.Type.SUBSIDIARY, String.format( "House Amendment %s of %s", amendmentNo, ctx.activeMotion.proposal ), ctx.activeMotion );
                    return IN_MOTION;
                }

                Matcher amendmentAdopted = Patterns.amendmentAdopted.matcher( pg.contents );
                if(amendmentAdopted.matches()) {
                    String rep = amendmentAdopted.group( 1 );
                    String amendmentNo = amendmentAdopted.group( 2 );
                    // Sanity check
                    if(!ctx.activeMotion.proposal.startsWith( String.format( "House Amendment %s", amendmentNo ) )) {
                        throw new RuntimeException( String.format("Expected %s to be active motion, but %s was just adopted? At %s", ctx.activeMotion.proposal, amendmentNo, pg.source) );
                    }
                    ctx.addAction( new AdoptWithoutVote( ctx.activeMotion ) );
                    ctx.activeMotion = ctx.activeMotion.relatesTo;
                    return IN_MOTION;
                }

                Matcher motionDefeated = Patterns.motionDefeated.matcher( pg.contents );
                if(motionDefeated.matches()) {
                    ctx.addAction( new DefeatedWithoutVote( ctx.activeMotion ) );
                    ctx.activeMotion = ctx.activeMotion.relatesTo;
                    return IN_MOTION;
                }

                Matcher motionDefeatedByVote = Patterns.motionDefeatedByVote.matcher( pg.contents );
                if(motionDefeatedByVote.matches()) {
                    ctx.activeVote = new VoteContext();
                    ctx.postVoteState = POST_DEFEAT_VOTE;
                    return IN_LONGFORM_VOTE;
                }

                System.err.println("    [ Unmatched:" + pg.contents.replace( "\\n", "\\\n" ) + "]");
                return IN_MOTION;
            }
        },
        POST_DEFEAT_VOTE {
            @Override
            State analyze( Analyzer2 ctx, Paragraph pg )
            {
                ctx.addAction( new DefeatedByVote( ctx.activeMotion, ctx.activeVote.toVote() ) );
                ctx.activeVote = null;
                ctx.activeMotion = ctx.activeMotion.relatesTo;
                if(ctx.activeMotion != null) {
                    return IN_MOTION.analyze( ctx, pg );
                }
                return NEXT_MOTION.analyze( ctx, pg );
            }
        },
        IN_LONGFORM_VOTE {
            @Override
            State analyze( Analyzer2 ctx, Paragraph pg )
            {
                if(pg.contents.contains( "AYES" ) || pg.contents.contains( "NOES" )
                        || pg.contents.contains( "PRESENT" ) || pg.contents.contains( "ABSENT" )
                        || pg.contents.contains( "ABSENT WITH LEAVE" )) {
                    String[] parts = pg.contents.split( ":" );
                    String voteGroup = parts[0].toLowerCase().trim();
                    String expected = parts[1];
                    int expectedVotes = Integer.parseInt( expected.trim() );
                    if(expectedVotes > 0) {
                        ctx.activeVote.currentVoteGroup = voteGroup;
                        ctx.activeVote.setExpectedVotesInCurrentGroup(expectedVotes);
                        return IN_LONGFORM_VOTE_REP_BLOCK;
                    }
                    return IN_LONGFORM_VOTE;
                }
                if(pg.contents.contains( "VACANCIES" )) {
                    return IN_LONGFORM_VOTE;
                }
                // Once we reach something we don't recognize as voting, exit vote parsing
                State postVoteState = ctx.postVoteState;
                ctx.postVoteState = null;
                return postVoteState.analyze( ctx, pg );
            }
        },
        IN_LONGFORM_VOTE_REP_BLOCK {
            @Override
            State analyze( Analyzer2 ctx, Paragraph pg )
            {
                List<String> out = new ArrayList<>();
                String[] parts = pg.contents.split( " {2}" );
                for ( String part : parts )
                {
                    if(part.isBlank()) {
                        continue;
                    }
                    part = part.trim();
                    if(!part.toLowerCase().matches( "[a-z’'.-]+( [a-z0-9’'.-]+)*" )) {
                        System.err.println(out);
                        System.err.printf("L: '%s'%n", pg.contents);
                        throw new RuntimeException( "!!" );
                    }
                    out.add( part );
                }
                ctx.activeVote.addVotesToCurrentGroup( out.toArray(new String[]{}) );
                if(ctx.activeVote.votesInCurrentGroup().length == ctx.activeVote.expectedVotesInCurrentGroup()) {
                    return IN_LONGFORM_VOTE;
                }
                if(ctx.activeVote.votesInCurrentGroup().length > ctx.activeVote.expectedVotesInCurrentGroup()) {
                    throw new RuntimeException( String.format("Expected %d votes but found more: %s",
                            ctx.activeVote.expectedVotesInCurrentGroup(),
                            Arrays.toString(ctx.activeVote.votesInCurrentGroup())) );
                }

                // There must've been a page break in the middle of the vote block, keep looking for votes.
                return IN_LONGFORM_VOTE_REP_BLOCK;
            }
        };

        abstract State analyze( Analyzer2 ctx, Paragraph pg );
    }

    public List<Analyzer.Action> analyze( String url, Iterable<String> inputLines ) {
        this.out = new ArrayList<>();

        State state = State.NEXT_MOTION;
        for ( Paragraph pg : new Sanitizer().sanitize( url, inputLines ) )
        {
            State oldState = state;
            System.err.println(pg.contents);
            state = state.analyze( this, pg );
            System.err.printf("== %s | %s%n", state, activeMotion);
        }

        return out;
    }

    void addAction( Analyzer.Action action ) {
        System.err.println(action);
        this.out.add( action );
    }

    public static class DefeatedWithoutVote implements Analyzer.Action
    {
        public final Analyzer.Motion motion;

        public DefeatedWithoutVote( Analyzer.Motion motion )
        {
            this.motion = motion;
        }

        @Override
        public String toString()
        {
            return "DefeatedWithoutVote{" + "motion=" + motion + '}';
        }

        @Override
        public Analyzer.Vote vote()
        {
            return null;
        }

        @Override
        public Analyzer.Motion motion()
        {
            return motion;
        }

        @Override
        public boolean equals( Object o )
        {
            if ( this == o )
            {
                return true;
            }
            if ( o == null || getClass() != o.getClass() )
            {
                return false;
            }
            DefeatedWithoutVote that = (DefeatedWithoutVote) o;
            return motion.equals( that.motion );
        }

        @Override
        public int hashCode()
        {
            return Objects.hash( motion );
        }
    }

    public static class DefeatedByVote implements Analyzer.Action
    {
        public final Analyzer.Motion motion;
        public final Analyzer.Vote vote;

        public DefeatedByVote( Analyzer.Motion motion, Analyzer.Vote vote )
        {
            this.motion = motion;
            this.vote = vote;
        }

        @Override
        public String toString()
        {
            return "DefeatedByVote{" + "motion=" + motion + ", vote=" + vote + '}';
        }

        @Override
        public Analyzer.Vote vote()
        {
            return vote;
        }

        @Override
        public Analyzer.Motion motion()
        {
            return motion;
        }

        @Override
        public boolean equals( Object o )
        {
            if ( this == o )
            {
                return true;
            }
            if ( o == null || getClass() != o.getClass() )
            {
                return false;
            }
            DefeatedByVote that = (DefeatedByVote) o;
            return motion.equals( that.motion ) && vote.equals( that.vote );
        }

        @Override
        public int hashCode()
        {
            return Objects.hash( motion, vote );
        }
    }


    public static class AdoptWithoutVote implements Analyzer.Action
    {
        public final Analyzer.Motion motion;

        public AdoptWithoutVote( Analyzer.Motion motion )
        {
            this.motion = motion;
        }

        @Override
        public String toString()
        {
            return "AdoptWithoutVote{" + "motion=" + motion + '}';
        }

        @Override
        public Analyzer.Vote vote()
        {
            return null;
        }

        @Override
        public Analyzer.Motion motion()
        {
            return motion;
        }

        @Override
        public boolean equals( Object o )
        {
            if ( this == o )
            {
                return true;
            }
            if ( o == null || getClass() != o.getClass() )
            {
                return false;
            }
            AdoptWithoutVote that = (AdoptWithoutVote) o;
            return motion.equals( that.motion );
        }

        @Override
        public int hashCode()
        {
            return Objects.hash( motion );
        }
    }

    // Used while parsing votes
    static class VoteContext {
        private int expectedAyes = 0;
        private int expectedNoes = 0;
        private int expectedAbsent = 0;
        private int expectedPresent = 0;
        private int expectedAbsentWithLeave = 0;
        private String currentVoteGroup = null;
        private String[] ayes = new String[]{};
        private String[] noes = new String[]{};
        private String[] present = new String[]{};
        private String[] absent = new String[]{};
        private String[] absentWithLeave = new String[]{};
        private String[] vacancies = new String[]{};

        int expectedVotesInCurrentGroup() {
            switch(currentVoteGroup.toLowerCase()) {
            case "ayes": return expectedAyes;
            case "noes": return expectedNoes;
            case "present": return expectedPresent;
            case "absent with leave": return expectedAbsentWithLeave;
            }
            throw new RuntimeException( "Unknown vote group: " + currentVoteGroup );
        }

        String[] votesInCurrentGroup() {
            switch(currentVoteGroup.toLowerCase()) {
            case "ayes": return ayes;
            case "noes": return noes;
            case "present": return present;
            case "absent with leave": return absentWithLeave;
            }
            throw new RuntimeException( "Unknown vote group: " + currentVoteGroup );
        }

        void addVotesToCurrentGroup(String[] votes) {
            switch(currentVoteGroup.toLowerCase()) {
            case "ayes": ayes = Collections.concat( ayes, votes ); break;
            case "noes": noes = Collections.concat( noes, votes ); break;
            case "present": present = Collections.concat( present, votes ); break;
            case "absent with leave": absentWithLeave = Collections.concat( absentWithLeave, votes ); break;
            default: throw new RuntimeException( "Unknown vote group: " + currentVoteGroup );
            }
        }

        public void setExpectedVotesInCurrentGroup( int expectedVotes ) {
            switch(currentVoteGroup.toLowerCase()) {
            case "ayes": expectedAyes = expectedVotes; break;
            case "noes": expectedNoes = expectedVotes; break;
            case "present": expectedPresent = expectedVotes; break;
            case "absent with leave": expectedAbsentWithLeave = expectedVotes; break;
            default: throw new RuntimeException( "Unknown vote group: " + currentVoteGroup );
            }
        }

        public Analyzer.Vote toVote()
        {
            if(ayes.length != expectedAyes) { throw new RuntimeException( String.format("Expected %d ayes found %d", expectedAyes, ayes.length) ); }
            if(noes.length != expectedNoes) { throw new RuntimeException( String.format("Expected %d noes found %d", expectedNoes, noes.length) ); }
            if(absent.length != expectedAbsent) { throw new RuntimeException( String.format("Expected %d absent found %d", expectedAbsent, absent.length) ); }
            if(absentWithLeave.length != expectedAbsentWithLeave) { throw new RuntimeException( String.format("Expected %d absentWithLeave found %d", expectedAbsentWithLeave, absentWithLeave.length) ); }
            if(present.length != expectedPresent) { throw new RuntimeException( String.format("Expected %d present found %d", expectedPresent, present.length) ); }
            return new Analyzer.Vote( ayes, noes, absent, absentWithLeave, present );
        }
    }


    static class Patterns {
        // HCS HB 10, as amended, to appropriate money for the expenses, grants, refunds, and
        // .. et cetera
        // ending June 30, 2020, was again taken up by Representative Smith.
        static Pattern takenUp = Pattern.compile("\\s*([^,]+),.*was(?: again)? taken up by (.*)\\.?\\s*");

        // HCS HB 10, as amended, was laid over.
        static Pattern laidOver = Pattern.compile("\\s*([^,]+),.*was laid over\\.?\\s*");

        // Representative Smith offered House Amendment No. 2.
        //  Representative Unsicker offered House Amendment No. 8.
        static Pattern offeredAmendment = Pattern.compile("\\s*(.+) offered House Amendment No. (\\d+).*");

        // On motion of Representative Smith, House Amendment No. 2 was adopted.
        static Pattern amendmentAdopted = Pattern.compile( "\\s*On motion of (.+), House Amendment No. (\\d+) was adopted.*" );

        // Which motion was defeated.
        static Pattern motionDefeated = Pattern.compile( "\\s*Which motion was defeated\\.?\\s*" );

        // Which motion was defeated by the following vote
        static Pattern motionDefeatedByVote = Pattern.compile( "\\s*Which motion was defeated by the following vote.*" );


        // HCR 1, HR 1, HCS HB 1158, SS SB 213, SB 185, HCS SB 275, SS HB 138, HCS SCS SB 174, SBs 70 & 128, HB 616
        // HCS HBs 275 & 853, SCR 1, HCB 1, SS HCS#2, SS SB 145, HJR 30, SS#2 SCR 14, HBs 243 & 544, CCR SS SCS HCS HB 399
        // SS SCS SJRs 14 & 9, HCS HJRs 48, HRB 1
        static Pattern bill = Pattern.compile( ".*((HRB \\d+)|(HCS HJRs \\d+)|(SS SCS SJRs \\d+ & \\d+)|(CCR SS SCS HCS HB \\d+)|(HBs \\d+ & \\d+)|(SS#?\\d+ SCR \\d+)|(HJR ?#?\\d+)|(HCB ?#?\\d+)|(SCR ?#?\\d+)|(HB ?#?\\d+)|(HCS HBs \\d+ & \\d+)|(SBs \\d+ & \\d+)|(SB ?#?\\d)|(HCS SCS SB ?#?\\d+)|(SS HB ?#?\\d+)|(HCS SB ?#?\\d+)|(SS SB ?#?\\d+)|(HCS HB ?#?\\d+)|(SS HCS ?#?\\d+)|(HCR ?#?\\d+)|(HR ?#?\\d+)).*" );

    }
}
