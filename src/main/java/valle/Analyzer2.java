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
                    ctx.mainMotion = new Analyzer.Motion( Analyzer.Motion.Type.MAIN_MOTION, bill, null );
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
                if(ctx.activeMotion == null) {
                    throw new RuntimeException( "Can't be IN_MOTION, there is no activeMotion set" );
                }

                Matcher takenUp = Patterns.takenUp.matcher( pg.contents );
                if(takenUp.matches()) {
                    throw new RuntimeException( "Can't be taken up, there is an active main motion already?" );
                }

                Matcher laidOver = Patterns.laidOver.matcher( pg.contents );
                if(laidOver.matches()) {
                    System.err.printf("%s was laid over%n", ctx.activeMotion.proposal);
                    if(ctx.activeMotion != ctx.mainMotion) {
                        throw new RuntimeException( String.format("Can non-main motions be laid over? At %s", pg.toString() ));
                    }
                    ctx.activeMotion = null;
                    ctx.mainMotion = null;
                    return NEXT_MOTION;
                }

                Matcher referredToCommittee = Patterns.referredToCommittee.matcher( pg.contents );
                if(referredToCommittee.matches()) {
                    System.err.printf("%s was referred to committee%n", ctx.activeMotion);
                    if(ctx.activeMotion != ctx.mainMotion) {
                        throw new RuntimeException( String.format("Can non-main motions be referred to committee? At %s", pg.toString() ));
                    }
                    ctx.activeMotion = null;
                    ctx.mainMotion = null;
                    return NEXT_MOTION;
                }

                Matcher amendmentAdoptedByVote = Patterns.amendmentAdoptedByVote.matcher( pg.contents );
                if(amendmentAdoptedByVote.matches()) {
                    String amendmentNo = amendmentAdoptedByVote.group( 2 );
                    ctx.activeMotion = new Analyzer.Motion( Analyzer.Motion.Type.AMEND, String.format( "House Amendment %s of %s", amendmentNo, ctx.activeMotion.proposal ), ctx.activeMotion );
                    ctx.activeVote = new VoteContext();
                    ctx.postVoteState = POST_ADOPT_VOTE;
                    return IN_LONGFORM_VOTE;
                }

                Matcher amendmentAdopted = Patterns.amendmentAdopted.matcher( pg.contents );
                if(amendmentAdopted.matches()) {
                    String rep = amendmentAdopted.group( 1 );
                    String amendmentNo = amendmentAdopted.group( 2 );
                    Analyzer.Motion motion = new Analyzer.Motion( Analyzer.Motion.Type.AMEND,
                            String.format( "House Amendment %s of %s", amendmentNo, ctx.activeMotion.proposal ),
                            ctx.activeMotion );
                    System.err.printf("%s from %s adopted%n", motion.proposal, rep );
                    ctx.addAction( new AdoptWithoutVote( motion ) );
                    return IN_MOTION;
                }

                Matcher billAdopted = Patterns.billAdoptedByVote.matcher( pg.contents );
                if(billAdopted.matches()) {
                    String rep = billAdopted.group( 1 );
                    String bill = billAdopted.group( 2 );
                    if(!ctx.mainMotion.proposal.toLowerCase().contains( bill.toLowerCase().trim() )) {
                        throw new RuntimeException( String.format("Expected adoption of %s to refer to main motion, which is %s.", bill, ctx.mainMotion ) );
                    }
                    ctx.activeVote = new VoteContext();
                    ctx.postVoteState = POST_ADOPT_VOTE;
                    return IN_LONGFORM_VOTE;
                }

                Matcher billPassedByVote = Patterns.billPassedByVote.matcher( pg.contents );
                if(billPassedByVote.matches()) {
                    String rep = billPassedByVote.group( 1 );
                    String bill = billPassedByVote.group( 2 );
                    if(!ctx.mainMotion.proposal.toLowerCase().contains( bill.toLowerCase().trim() )) {
                        throw new RuntimeException( String.format("Expected adoption of %s to refer to main motion, which is %s.", bill, ctx.mainMotion ) );
                    }
                    ctx.activeVote = new VoteContext();
                    ctx.postVoteState = POST_ADOPT_VOTE;
                    return IN_LONGFORM_VOTE;
                }

                Matcher motionDefeated = Patterns.motionDefeated.matcher( pg.contents );
                if(motionDefeated.matches()) {
                    ctx.addAction( new DefeatedWithoutVote( ctx.activeMotion ) );
                    ctx.activeMotion = ctx.activeMotion.relatesTo;
                    return IN_MOTION;
                }

                Matcher motionAdopted = Patterns.motionAdopted.matcher( pg.contents );
                if(motionAdopted.matches()) {
                    ctx.addAction( new AdoptWithoutVote( ctx.activeMotion ) );
                    return this.handleMotionAdopted(ctx, pg);
                }

                Matcher motionDefeatedByVote = Patterns.motionDefeatedByVote.matcher( pg.contents );
                if(motionDefeatedByVote.matches()) {
                    ctx.activeVote = new VoteContext();
                    ctx.postVoteState = POST_DEFEAT_VOTE;
                    return IN_LONGFORM_VOTE;
                }

                Matcher motionAdoptedByVote = Patterns.motionAdoptedByVote.matcher( pg.contents );
                if(motionAdoptedByVote.matches()) {
                    ctx.activeVote = new VoteContext();
                    ctx.postVoteState = POST_ADOPT_VOTE;
                    return IN_LONGFORM_VOTE;
                }

                Matcher movedPreviousQuestion = Patterns.movedPreviousQuestion.matcher( pg.contents );
                if(movedPreviousQuestion.matches()) {
                    String rep = movedPreviousQuestion.group( 1 );
                    System.err.printf("%s moved the previous question%n", rep );
                    ctx.activeMotion = new Analyzer.Motion( Analyzer.Motion.Type.END_DEBATE, "move the previous question", ctx.activeMotion );
                    return IN_MOTION;
                }

                Matcher moveToReconsiderVote = Patterns.moveToReconsiderVote.matcher( pg.contents );
                if(moveToReconsiderVote.matches()) {
                    String rep = movedPreviousQuestion.group( 1 );
                    System.err.printf("%s moved the previous question%n", rep );
                    ctx.activeMotion = new Analyzer.Motion( Analyzer.Motion.Type.RECONSIDER_VOTE, "reconsider vote", ctx.activeMotion );
                    return IN_MOTION;
                }

                Matcher moveToRefuseReceding = Patterns.moveToRefuseReceding.matcher( pg.contents );
                if(moveToRefuseReceding.matches()) {
                    String rep = moveToRefuseReceding.group( 1 );
                    System.err.printf("%s moved the house refuse to recede%n", rep );
                    ctx.activeMotion = new Analyzer.Motion( Analyzer.Motion.Type.REFUSE_TO_RECEDE, "refuse to recede", ctx.activeMotion );
                    return IN_MOTION;
                }

                Matcher moveToRefuseToAdopt = Patterns.moveToRefuseToAdopt.matcher( pg.contents );
                if(moveToRefuseToAdopt.matches()) {
                    String rep = moveToRefuseToAdopt.group( 1 );
                    System.err.printf("%s moved the house refuse to adopt%n", rep );
                    ctx.activeMotion = new Analyzer.Motion( Analyzer.Motion.Type.REFUSE_TO_ADOPT, "refuse to adopt", ctx.activeMotion );
                    return IN_MOTION;
                }

                Matcher moveToAdopt = Patterns.moveToAdopt.matcher( pg.contents );
                if(moveToAdopt.matches()) {
                    String rep = moveToAdopt.group( 1 );
                    String bill = moveToAdopt.group( 2 );
                    System.err.printf("%s moved to adopt %s%n", rep, bill );
                    ctx.activeMotion = new Analyzer.Motion( Analyzer.Motion.Type.ADOPT, "adopt", ctx.activeMotion );
                    return IN_MOTION;
                }

                Matcher moveToRecommit = Patterns.moveToRecommit.matcher( pg.contents );
                if(moveToRecommit.matches()) {
                    String rep = moveToRecommit.group( 1 );
                    System.err.printf("%s moved to recommit to committee%n", rep );
                    ctx.activeMotion = new Analyzer.Motion( Analyzer.Motion.Type.REFER_TO_COMMITTEE, "recommit", ctx.activeMotion );
                    return IN_MOTION;
                }

                Matcher miscMovement = Patterns.miscMovement.matcher( pg.contents );
                if(miscMovement.matches()) {
                    String rep = miscMovement.group( 1 );
                    String motion = miscMovement.group( 2 );
                    System.err.printf("%s moved that %s%n", rep, motion );
                    ctx.activeMotion = new Analyzer.Motion( Analyzer.Motion.Type.MISC, motion, ctx.activeMotion );
                    return IN_MOTION;
                }

                System.err.println("    [ Unmatched:" + pg.contents.replace( "\\n", "\\\n" ) + "]");
                if(pg.contents.contains( "moved that" )) {
                    throw new RuntimeException( "Appear to have missed motion: " + pg.contents );
                }
                return IN_MOTION;
            }
        },
        POST_ADOPT_VOTE {
            @Override
            State analyze( Analyzer2 ctx, Paragraph pg )
            {
                ctx.addAction( new AdoptedByVote( ctx.activeMotion, ctx.activeVote.toVote() ) );
                ctx.activeVote = null;
                return this.handleMotionAdopted(ctx, pg);
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

        State handleMotionAdopted( Analyzer2 ctx, Paragraph pg ) {
            switch(ctx.activeMotion.type) {
            case END_DEBATE: ctx.activeMotion = ctx.mainMotion; break;
            case AMEND:
                ctx.activeMotion = ctx.activeMotion.relatesTo;
                break;
            // These are movements that clear the stack
            case REFUSE_TO_ADOPT:
            case REFUSE_TO_RECEDE:
            case ADOPT:
            case MAIN_MOTION:
            case REFER_TO_COMMITTEE:
                ctx.mainMotion = null;
                ctx.activeMotion = null;
                break;
            default:
                throw new RuntimeException( String.format( "Don't know how to handle adoption of %s", ctx.activeMotion ) );
            }
            if(ctx.activeMotion != null) {
                return IN_MOTION.analyze( ctx, pg );
            }
            return NEXT_MOTION.analyze( ctx, pg );
        }
    }

    public List<Analyzer.Action> analyze( String url, Iterable<String> inputLines ) {
        this.out = new ArrayList<>();

        State state = State.NEXT_MOTION;
        for ( Paragraph pg : new Sanitizer().sanitize( url, inputLines ) )
        {
            System.err.println(pg.contents);
            state = state.analyze( this, pg );
            System.err.printf("== %s | %s | %s%n", state, activeMotion, pg.source);
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

    public static class AdoptedByVote implements Analyzer.Action
    {
        public final Analyzer.Motion motion;
        public final Analyzer.Vote vote;

        public AdoptedByVote( Analyzer.Motion motion, Analyzer.Vote vote )
        {
            this.motion = motion;
            this.vote = vote;
        }

        @Override
        public String toString()
        {
            return "AdoptedByVote{" + "motion=" + motion + ", vote=" + vote + '}';
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
            AdoptedByVote that = (AdoptedByVote) o;
            return motion.equals( that.motion ) && vote.equals( that.vote );
        }

        @Override
        public int hashCode()
        {
            return Objects.hash( motion, vote );
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

        // SB 514, as amended, was referred to the Committee on Fiscal Review pursuant to  Rule 53.
        static Pattern referredToCommittee = Pattern.compile("\\s*([^,]+), (.*) was referred to (.*) pursuant to.*");

        // On motion of Representative Merideth, House Amendment No. 1, as amended, was  adopted by the following vote, the ayes and noes having been demanded by Representative  Merideth:
        static Pattern amendmentAdoptedByVote = Pattern.compile( "\\s*On motion of (.+), House Amendment No. (\\d+).*was\\s+adopted\\s+by\\s+the\\s+following.*" );

        // On motion of Representative Smith, House Amendment No. 2 was adopted.
        static Pattern amendmentAdopted = Pattern.compile( "\\s*On motion of (.+), House Amendment No. (\\d+).*was adopted.*" );

        // On motion of Representative Patterson, SS HCS HB 677 was adopted by the following  vote
        static Pattern billAdoptedByVote = Pattern.compile( "\\s*On motion of (.+), (.*)\\s+was\\s+adopted\\s+by\\s+the\\s+following\\s+vote.*" );

        // On motion of Representative Rone, SB 21 was truly agreed to and finally passed by the  following vote:
        // On motion of Representative Swan, SB 358, as amended, was read the third time and  passed by the following vote:
        static Pattern billPassedByVote = Pattern.compile( "\\s*On motion of Representative (.*),\\s+([^,]+)(?:, as amended,)?(?:(?: was truly)|(?: was read)).*passed\\s+by\\s+the\\s+following.*" );

        // Which motion was defeated.
        static Pattern motionDefeated = Pattern.compile( "\\s*Which motion was defeated\\.?\\s*" );

        // Which motion was adopted.
        static Pattern motionAdopted = Pattern.compile( "\\s*Which motion was adopted\\.?\\s*" );

        // Which motion was defeated by the following vote
        static Pattern motionDefeatedByVote = Pattern.compile( "\\s*Which motion was defeated by the following vote.*" );

        // Which motion was adopted by the following vote:
        static Pattern motionAdoptedByVote = Pattern.compile( "\\s*Which motion was adopted by the following vote.*" );

        // Representative Eggleston moved the previous question.
        static Pattern movedPreviousQuestion = Pattern.compile( "\\s*Representative (.+) moved the previous question.*" );

        // Representative Rone moved that the title of HCS SB 21 be agreed to
        // Representative Ross moved that the House refuse to recede from its position on  HCS SB 36, as amended, and grant the Senate a conference.
        static Pattern miscMovement = Pattern.compile( "\\s*Representative (.+) moved that (.*).*" );

        // Representative Eggleston moved that HCS HB 548 be recommitted to the Committee on  Rules - Legislative Oversight.
        static Pattern moveToRecommit = Pattern.compile( "\\s+Representative Eggleston moved that\\s+(.*)\\s+be\\s+recommitted\\s+to\\s+the.*" );

        // Representative Rone moved that HCS SB 21 be adopted.
        static Pattern moveToAdopt = Pattern.compile( "\\s*Representative (.*) moved that (.*) be adopted.*" );

        // Representative Ross moved that the House refuse to recede from its position on  HCS SB 36, as amended, and grant the Senate a conference.
        static Pattern moveToRefuseReceding = Pattern.compile( "\\s*Representative (.*)\\s+moved\\s+that\\s+the House refuse to recede.*" );

        // Representative Griesheimer moved that the House refuse to adopt SS HCS#2 HB 499  and request the Senate to recede from its position and, failing to do so, grant the House a  conference.
        static Pattern moveToRefuseToAdopt = Pattern.compile( "\\s*Representative (.*)\\s+moved\\s+that\\s+the\\s+House\\s+refuse\\s+to\\s+adopt.*" );

        // Representative Coleman (32), having voted on the prevailing side, moved that the vote by  which HCS SB 182, as amended, was adopted be reconsidered.
        static Pattern moveToReconsiderVote = Pattern.compile( "\\s+Representative ([^,]+),.*moved that\\s+the\\s+vote\\s+by\\s+which\\s+(.*),\\s+as\\s+amended,\\s+was\\s+adopted be reconsidered.*" );

        // HCR 1, HR 1, HCS HB 1158, SS SB 213, SB 185, HCS SB 275, SS HB 138, HCS SCS SB 174, SBs 70 & 128, HB 616
        // HCS HBs 275 & 853, SCR 1, HCB 1, SS HCS#2, SS SB 145, HJR 30, SS#2 SCR 14, HBs 243 & 544, CCR SS SCS HCS HB 399
        // SS SCS SJRs 14 & 9, HCS HJRs 48, HRB 1
        static Pattern bill = Pattern.compile( ".*((HRB \\d+)|(HCS HJRs \\d+)|(SS SCS SJRs \\d+ & \\d+)|(CCR SS SCS HCS HB \\d+)|(HBs \\d+ & \\d+)|(SS#?\\d+ SCR \\d+)|(HJR ?#?\\d+)|(HCB ?#?\\d+)|(SCR ?#?\\d+)|(HB ?#?\\d+)|(HCS HBs \\d+ & \\d+)|(SBs \\d+ & \\d+)|(SB ?#?\\d)|(HCS SCS SB ?#?\\d+)|(SS HB ?#?\\d+)|(HCS SB ?#?\\d+)|(SS SB ?#?\\d+)|(HCS HB ?#?\\d+)|(SS HCS ?#?\\d+)|(HCR ?#?\\d+)|(HR ?#?\\d+)).*" );

    }
}
