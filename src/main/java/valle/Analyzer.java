package valle;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Analyzer
{
    public static class Vote {
        public final Set<String> ayes;
        public final Set<String> noes;
        public final Set<String> absent;
        public final Set<String> absentWithLeave;
        public final Set<String> present;

        public Vote( Analyzer ctx )
        {
            this(ctx.ayes, ctx.noes, ctx.absent, ctx.absentWithLeave, ctx.present );
        }

        public Vote( String[] ayes, String[] noes, String[] absent, String[] absentWithLeave, String[] present )
        {
            this.ayes = ayes == null ? new HashSet<>() : new HashSet<>( Arrays.asList( ayes ) );
            this.noes = noes == null ? new HashSet<>() : new HashSet<>( Arrays.asList( noes ) );
            this.absent = absent == null ? new HashSet<>() : new HashSet<>( Arrays.asList( absent ) );
            this.absentWithLeave = absentWithLeave == null ? new HashSet<>() : new HashSet<>( Arrays.asList( absentWithLeave ) );
            this.present = present == null ? new HashSet<>() : new HashSet<>( Arrays.asList( present ) );
        }

        public boolean isRepInvolved( String name ) {
            return voteOfRep( name ) != null;
        }

        public VoteGroup voteOfRep( String name ) {
            if( containsIgnoreCase(name, ayes)) {
                return VoteGroup.AYES;
            }
            if( containsIgnoreCase(name, noes)) {
                return VoteGroup.NOES;
            }
            if( containsIgnoreCase(name, absent)) {
                return VoteGroup.ABSENT;
            }
            if( containsIgnoreCase(name, absentWithLeave)) {
                return VoteGroup.ABSENT_WITH_LEAVE;
            }
            if( containsIgnoreCase(name, present)) {
                return VoteGroup.PRESENT;
            }
            return null;
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
            Vote vote = (Vote) o;
            return ayes.equals( vote.ayes ) && noes.equals( vote.noes ) &&
                    absent.equals( vote.absent ) &&
                    absentWithLeave.equals( vote.absentWithLeave ) &&
                    present.equals( vote.present );
        }

        @Override
        public int hashCode()
        {
            return Objects.hash( ayes, noes, absent, absentWithLeave, present );
        }

        @Override
        public String toString()
        {
            return "Vote{" + "ayes=" + ayes + ", noes=" + noes + ", absent=" + absent +
                    ", absentWithLeave=" + absentWithLeave + ", present=" + present + '}';
        }

        public String diff( Vote other )
        {
            String ayeDiff = Analyzer.diff( ayes, other.ayes );
            String noeDiff = Analyzer.diff( noes, other.noes );
            String absentDiff = Analyzer.diff( absent, other.absent );
            String absentWithLeaveDiff = Analyzer.diff( absentWithLeave, other.absentWithLeave );
            String presentDiff = Analyzer.diff( present, other.present );
            return String.format(
                    "ayes: %s%n" +
                    "noes: %s%n" +
                    "absent: %s%n" +
                    "absentWithLeave: %s%n" +
                    "present: %s",
                    ayeDiff.isEmpty() ? "equal" : ayeDiff,
                    noeDiff.isEmpty() ? "equal" : noeDiff,
                    absentDiff.isEmpty() ? "equal" : absentDiff,
                    absentWithLeaveDiff.isEmpty() ? "equal" : absentWithLeaveDiff,
                    presentDiff.isEmpty() ? "equal" : presentDiff);
        }
    }

    public static class Source {
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

    public static class Motion {

        public enum Type {
            // The current main thing the assembly is considering
            MAIN,
            // A sub-motion relating directly to the main motion at hand, like an amendment,
            // postponing or a motion to the previous question, aka lets vote right now
            SUBSIDIARY,
            // A motion not relating to the main motion that takes precedence, like a motion to adjourn
            PRIVILEGED,
            // Random other motions, like appeals to the chair, questions about voting etc
            INCIDENTAL,
            // We were not able to figure out what type of motion took place
            UNKNOWN
        }

        public final Motion.Type type;

        public final String proposal;
        // If this is a motion about another motion - like a motion to amend a bill - this
        // is the motion this motion is talking about. So if this motion is to adopt an amendment,
        // then the thing being amended is pointed to here.
        public final Motion relatesTo;

        public Motion( Motion.Type type, String proposal, Motion relatesTo )
        {
            this.type = type;
            this.proposal = proposal;
            this.relatesTo = relatesTo;
        }

        @Override
        public String toString()
        {
            return "Motion{" + "type=" + type + ", proposal='" + proposal + '\'' + ", relatesTo=" +
                    relatesTo + '}';
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
            Motion motion = (Motion) o;
            return type == motion.type && proposal.equals( motion.proposal ) &&
                    Objects.equals( relatesTo, motion.relatesTo );
        }

        @Override
        public int hashCode()
        {
            return Objects.hash( type, proposal, relatesTo );
        }
    }

    public interface Action
    {
        Vote vote();
        Motion motion();
        default Source source() { return null; }
    }

    public static class SourcedAction implements Action
    {
        private final Action delegate;
        private final Source source;

        public SourcedAction( Action delegate, Source source) {
            this.delegate = delegate;
            this.source = source;
        }

        @Override
        public Vote vote()
        {
            return delegate.vote();
        }

        @Override
        public Motion motion()
        {
            return delegate.motion();
        }

        @Override
        public Source source()
        {
            return source;
        }

        @Override
        public String toString()
        {
            return delegate + " @ " + source;
        }
    }

    public static class CommitteeVote implements Action
    {
        public final String committee;
        public final Motion motion;
        public final Vote vote;

        public CommitteeVote( Analyzer ctx, Motion motion )
        {
            this.committee = ctx.activeCommittee;
            this.motion = motion;
            this.vote = new Vote( ctx );
        }

        @Override
        public String toString()
        {
            return "CommitteeVote{" + "committee='" + committee + '\'' + ", motion='" + motion +
                    '\'' + ", vote=" + vote + '}';
        }

        @Override
        public Vote vote()
        {
            return vote;
        }

        @Override
        public Motion motion()
        {
            return motion;
        }
    }

    public static class HouseVote implements Action
    {
        public final Motion motion;
        public final Vote vote;

        public HouseVote( Analyzer ctx, Motion motion )
        {
            this.motion = motion;
            this.vote = new Vote( ctx );
        }

        @Override
        public String toString()
        {
            return "HouseVote{" + "motion='" + motion + '\'' + ", vote=" + vote + '}';
        }

        @Override
        public Vote vote()
        {
            return vote;
        }

        @Override
        public Motion motion()
        {
            return motion;
        }
    }

    public enum State {
        // In this state we are trying to gain some sort of foothold by looking for things we know
        SEEKING {
            @Override
            public State analyze( Analyzer ctx, String line )
            {
                if(line.isBlank()) {
                    return SEEKING;
                }

                Matcher committeeMatch = Patterns.committeeReport.matcher( line );
                if( committeeMatch.matches() ) {
                    ctx.clearVoteState();
                    ctx.activeCommittee = committeeMatch.group( 2 );
                    System.err.printf("Analyzing committee report from %s..%n", ctx.activeCommittee );
                    return COMMITTEE_REPORT_INTRO.analyze( ctx, line );
                }

                Matcher committeeAdoptedReport = Patterns.committeeAdoptedReportByVote.matcher( line );
                if( committeeAdoptedReport.matches() ) {
                    ctx.clearVoteState();
                    ctx.activeCommittee = committeeAdoptedReport.group( 1 );
                    ctx.action = "some report";
                    System.err.printf("Analyzing committee adopting report from %s..%n", ctx.activeCommittee );
                    return COMMITTEE_REPORT_INTRO.analyze( ctx, line );
                }

                Matcher unknownCommitteeVoted = Patterns.unknownCommitteeVoted.matcher( line );
                if( unknownCommitteeVoted.matches() ) {
                    ctx.clearVoteState();
                    ctx.activeCommittee = "some committee";
                    ctx.currentVoteGroup = null;
                    ctx.action = "some report";
                    System.err.printf("WARNING: Unknown commitee voted..%n" );
                    return COMMITTEE_REPORT;
                }

                Matcher journalApprovalMatch = Patterns.journalApproval.matcher( line );
                if( journalApprovalMatch.matches() ) {
                    System.err.printf("Analyzing journal approval votes..%n" );
                    ctx.clearVoteState();
                    ctx.postVoteBlockState = JOURNAL_APPROVAL;
                    return VOTEBLOCK_LONGFORM;
                }

                Matcher motionOf = Patterns.motionOf.matcher( line );
                if( motionOf.matches() ) {
                    System.err.printf("Analyzing motion..%n" );
                    ctx.clearVoteState();
                    ctx.postVoteBlockState = MOTION_END;
                    ctx.voteExpected = false;
                    ctx.action = motionOf.group( 2 );
                    return MOTION_START.analyze( ctx, line );
                }

                Matcher moveToAdopt = Patterns.moveToAdopt.matcher( line );
                if( moveToAdopt.matches() ) {
                    System.err.printf("Analyzing move to adopt..%n" );
                    ctx.clearVoteState();
                    ctx.action = moveToAdopt.group( 2 );
                    return SEEKING;
                }

                Matcher takenUp = Patterns.takenUp.matcher( line );
                Matcher bill = Patterns.bill.matcher( line );
                if( takenUp.matches() && bill.matches() ) {
                    System.err.printf("Analyzing %s taken up..%n", bill.group( 1 ) );
                    ctx.clearVoteState();
                    ctx.action = bill.group( 1 );
                    return SEEKING;
                }

                Matcher motionWasXByVote = Patterns.whichMotionWasXByVote.matcher( line );
                if( motionWasXByVote.matches() ) {
                    if(ctx.action == null) {
                        System.err.println("WARN: Don't know which motion is being voted on, ignoring..");
                        ctx.clearVoteState();
                        ctx.postVoteBlockState = SEEKING;
                        return SEEK_VOTE;
                    }
                    System.err.printf("Analyzing motion on %s by vote..%n", ctx.action);
                    ctx.clearVoteState();
                    ctx.postVoteBlockState = MOTION_END;
                    ctx.voteExpected = true;
                    return MOTION_START.analyze( ctx, line );
                }

                // Note that there are both House and Senate roll calls, since the Senate is included in these journals during joint sessions
                if( line.contains( "The following roll call indicated" ) || line.contains( "called the roll" )) {
                    System.err.printf("Analyzing roll call..%n");
                    ctx.clearVoteState();
                    ctx.postVoteBlockState = SEEKING;
                    return SEEK_VOTE;
                }

                Matcher adoptedByVote = Patterns.xWasAdoptedByVote.matcher( line );
                if( adoptedByVote.matches() ) {
                    System.err.printf("WARNING: Ignoring unknown thing adopted: %s..%n", adoptedByVote.group( 1 ) );
                    ctx.clearVoteState();
                    ctx.action = adoptedByVote.group( 1 );
                    ctx.postVoteBlockState = SEEKING;
                    return SEEK_VOTE;
                }

                Matcher xWasDefeatedByVote = Patterns.xWasDefeatedByVote.matcher( line );
                if( xWasDefeatedByVote.matches() ) {
                    System.err.printf("WARNING: Ignoring unknown thing defeated: %s..%n", xWasDefeatedByVote.group( 1 ) );
                    ctx.clearVoteState();
                    ctx.action = xWasDefeatedByVote.group( 1 );
                    ctx.postVoteBlockState = SEEKING;
                    return SEEK_VOTE;
                }

                Matcher rulingOfChairSustainedByVote = Patterns.rulingOfChairSustainedByVote.matcher( line );
                if(rulingOfChairSustainedByVote.matches()) {
                    System.err.printf("WARNING: Ignoring unknown chair ruling sustained..%n" );
                    ctx.clearVoteState();
                    ctx.action = "ruling by chair sustained";
                    ctx.postVoteBlockState = SEEKING;
                    return SEEK_VOTE;
                }

                if(line.toLowerCase().contains( "ayes:" )) {
                    throw new RuntimeException( "Missed vote: " + line );
                }

                return SEEKING;
            }
        },

        // Fast-forward to the next long form vote and parse it
        SEEK_VOTE {
            @Override
            public State analyze( Analyzer ctx, String line )
            {
                if(line.toLowerCase().contains( "ayes:" )) {
                    return VOTEBLOCK_LONGFORM.analyze( ctx, line );
                }

                return SEEK_VOTE;
            }
        },

        JOURNAL_APPROVAL {
            @Override
            public State analyze( Analyzer ctx, String line )
            {
                // We don't care about this
                return SEEKING;
            }
        },

        MOTION_START {
            @Override
            public State analyze( Analyzer ctx, String line )
            {
                if(line.isBlank()) {
                    if(ctx.action == null) {
                        throw new RuntimeException( "Can't find action" );
                    }
                    if(!ctx.voteExpected) {
                        return SEEKING;
                    }
                    ctx.postVoteBlockState = MOTION_END;
                    return VOTEBLOCK_LONGFORM;
                }

                Matcher billMatch = Patterns.bill.matcher( line );
                if( billMatch.matches() ) {
                    ctx.action = billMatch.group( 1 );
                }

                Matcher statuteMatch = Patterns.statutes.matcher( line );
                if( statuteMatch.matches() ) {
                    ctx.action = statuteMatch.group( 1 );
                }

                if( line.contains( "House adjourned" )) {
                    ctx.action = "House adjourned";
                }

                if( line.contains( "vote" )) {
                    ctx.voteExpected = true;
                }

                return MOTION_START;
            }
        },

        MOTION_END {
            @Override
            public State analyze( Analyzer ctx, String line )
            {
                ctx.addEvent( new HouseVote( ctx, new Motion( Motion.Type.UNKNOWN, "..", null ) ) );
                ctx.clearVoteState();
                ctx.action = null;
                ctx.voteExpected = false;
                ctx.postVoteBlockState = null;
                return SEEKING;
            }
        },

        COMMITTEE_REPORT_INTRO {
            @Override
            public State analyze( Analyzer ctx, String line )
            {
                if(line.isBlank()) {
                    if(ctx.action == null) {
                        throw new RuntimeException( "Can't find bill in " + line );
                    }
                    if(!ctx.action.contains( "Do Pass" )
                            && !ctx.action.contains( "return" )
                            && !ctx.action.contains( "Be Introduced" )
                            && !ctx.action.contains( "adopted" )) {
                        throw new RuntimeException( "Unable to figure out proposed action, please backtrack." );
                    }
                    return COMMITTEE_REPORT;
                }

                Matcher billMatch = Patterns.bill.matcher( line );
                if( billMatch.matches() ) {
                    ctx.action = (ctx.action == null ? ctx.action : "") + billMatch.group( 1 );
                }

                Matcher statuteMatch = Patterns.statutes.matcher( line );
                if( statuteMatch.matches() ) {
                    ctx.action = (ctx.action == null ? ctx.action : "") + statuteMatch.group( 1 );
                }

                if(line.contains( "returned" )) {
                    ctx.action = "return to committee " + (ctx.action == null ? ctx.action : "");
                }

                if(line.contains( "adopted" )) {
                    ctx.action = "adopted " + (ctx.action == null ? ctx.action : "");
                }

                if(line.contains( "Pass" )) {
                    if(ctx.action == null) {
                        System.err.println("WARN: Failed to find bill");
                        ctx.action = "UNKNOWN BILL";
                    }
                    ctx.action = "Do Pass " + ctx.action;
                }

                if(line.contains( "Be Introduced" )) {
                    if(ctx.action == null) {
                        throw new RuntimeException( "Can't find bill in " + line );
                    }
                    ctx.action = "Be Introduced " + ctx.action;
                }

                return COMMITTEE_REPORT_INTRO;
            }
        },

        COMMITTEE_REPORT {
            @Override
            public State analyze( Analyzer ctx, String line )
            {
                if(line.isBlank() || line.startsWith( "Date:" ) || line.startsWith( "/s/" )) {
                    // If we find blank space after handling the Absentees, that's the end of
                    // a committee report.
                    if(ctx.currentVoteGroup == VoteGroup.ABSENT || line.startsWith( "Date:" ) || line.startsWith( "/s/" ))
                    {
                        ctx.addEvent( new CommitteeVote( ctx, new Motion( Motion.Type.UNKNOWN, "..", null ) ) );
                        ctx.clearVoteState();
                        ctx.action = null;
                        ctx.voteExpected = false;
                        ctx.postVoteBlockState = null;
                        return SEEKING;
                    }

                    return COMMITTEE_REPORT;
                }

                if(line.contains( "Ayes" )) {
                    ctx.currentVoteGroup = VoteGroup.AYES;
                    ctx.postVoteBlockState = COMMITTEE_REPORT;
                    return VOTEBLOCK_SHORTFORM.analyze( ctx, line );
                }
                if(line.contains( "Noes" )) {
                    ctx.currentVoteGroup = VoteGroup.NOES;
                    ctx.postVoteBlockState = COMMITTEE_REPORT;
                    return VOTEBLOCK_SHORTFORM.analyze( ctx, line );
                }
                if(line.contains( "Present" )) {
                    ctx.currentVoteGroup = VoteGroup.PRESENT;
                    ctx.postVoteBlockState = COMMITTEE_REPORT;
                    return VOTEBLOCK_SHORTFORM.analyze( ctx, line );
                }
                if(line.contains( "Absent" )) {
                    ctx.currentVoteGroup = VoteGroup.ABSENT;
                    ctx.postVoteBlockState = COMMITTEE_REPORT;
                    return VOTEBLOCK_SHORTFORM.analyze( ctx, line );
                }

                throw new RuntimeException( String.format("Don't know how to analyze `%s` in COMMITTEE_REPORT.", line) );
            }
        },

        // Currently parsing a shortform vote block, ie
        // Ayes (18): Black (7), Busick, Francis, Haden, Haffner, Hovis, Hurst, Kelly (141), Knight, Love, Morse (151), Muntzel, Pollitt (52), Reedy, Rone,
        // Sharpe, Spencer and Stephens (128)
        VOTEBLOCK_SHORTFORM {
            @Override
            public State analyze( Analyzer ctx, String line )
            {
                if(line.isBlank()) {
                    return ctx.postVoteBlockState.analyze( ctx, line );
                }

                // (0) is not a district, so it should only match if the line is like
                // Noes (0)
                // Indicating noone voted for this option
                if(line.contains( "(0)" )) {
                    ctx.currentVoteGroup.set( ctx, new String[0] );
                    return ctx.postVoteBlockState;
                }
                String[] votes = ctx.currentVoteGroup.get( ctx );

                if(line.contains( ":" )) {
                    line = line.split( ":" )[1];
                }

                String[] additional = trim( line.split( ",|(and)" ) );
                ctx.currentVoteGroup.set( ctx, concat(votes, additional) );
                return VOTEBLOCK_SHORTFORM;
            }
        },

        VOTEBLOCK_LONGFORM {
            @Override
            public State analyze( Analyzer ctx, String line )
            {
                if(ctx.postVoteBlockState == null) {
                    throw new IllegalStateException( "postVoteBlockState cannot be null when entering VOTEBLOCK_LONGFORM" );
                }
                if(line.isBlank()) {
                    return VOTEBLOCK_LONGFORM;
                }

                if(line.contains( "AYES:" )) {
                    ctx.currentVoteGroup = VoteGroup.AYES;
                    ctx.currentVoteGroup.set( ctx, null );
                    return VOTEBLOCK_LONGFORM;
                } else if(line.contains( "NOES:" )) {
                    ctx.currentVoteGroup = VoteGroup.NOES;
                    ctx.currentVoteGroup.set( ctx, null );
                    return VOTEBLOCK_LONGFORM;
                } else if(line.contains( "PRESENT:" )) {
                    ctx.currentVoteGroup = VoteGroup.PRESENT;
                    ctx.currentVoteGroup.set( ctx, null );
                    return VOTEBLOCK_LONGFORM;
                } else if(line.contains( "ABSENT WITH LEAVE" )) {
                    ctx.currentVoteGroup = VoteGroup.ABSENT_WITH_LEAVE;
                    ctx.currentVoteGroup.set( ctx, null );
                    return VOTEBLOCK_LONGFORM;
                } else if(line.contains( "ABSENT" )) {
                    ctx.currentVoteGroup = VoteGroup.ABSENT;
                    ctx.currentVoteGroup.set( ctx, null );
                    return VOTEBLOCK_LONGFORM;
                } else if(line.contains( "VACANCIES:" )) {
                    ctx.currentVoteGroup = VoteGroup.VACANCIES;
                    ctx.currentVoteGroup.set( ctx, null );
                    return ctx.postVoteBlockState;
                }

                // Random gunk that indicates we've reached the end of a vote block
                if(line.contains( "the" ) || line.contains( "JOINT SESSION" )) {
                    return ctx.postVoteBlockState.analyze( ctx, line );
                }

                String[] votes = ctx.currentVoteGroup.get( ctx );
                String[] additional = parseVoteBlock( line );
                ctx.currentVoteGroup.set( ctx, concat(votes, additional) );

                return VOTEBLOCK_LONGFORM;
            }
        }
        ;

        public abstract State analyze( Analyzer ctx, String line );
    }

    public enum VoteGroup {
        AYES {
            @Override
            String[] get( Analyzer ctx ) { return ctx.ayes == null ? new String[0] : ctx.ayes; }
            @Override
            void set( Analyzer ctx, String[] votes ) { ctx.ayes = votes; }
        },
        NOES {
            @Override
            String[] get( Analyzer ctx ) { return ctx.noes == null ? new String[0] : ctx.noes; }
            @Override
            void set( Analyzer ctx, String[] votes ) { ctx.noes = votes; }
        },
        ABSENT {
            @Override
            String[] get( Analyzer ctx ) { return ctx.absent == null ? new String[0] : ctx.absent; }
            @Override
            void set( Analyzer ctx, String[] votes ) { ctx.absent = votes; }
        },
        ABSENT_WITH_LEAVE {
            @Override
            String[] get( Analyzer ctx ) { return ctx.absentWithLeave == null ? new String[0] : ctx.absentWithLeave; }
            @Override
            void set( Analyzer ctx, String[] votes ) { ctx.absentWithLeave = votes; }
        },
        PRESENT {
            @Override
            String[] get( Analyzer ctx ) { return ctx.present == null ? new String[0] : ctx.present; }
            @Override
            void set( Analyzer ctx, String[] votes ) { ctx.present = votes; }
        },
        VACANCIES {
            @Override
            String[] get( Analyzer ctx ) { return ctx.vacancies == null ? new String[0] : ctx.vacancies; }
            @Override
            void set( Analyzer ctx, String[] votes ) { ctx.vacancies = votes; }
        },;

        abstract String[] get( Analyzer ctx );
        abstract void set( Analyzer ctx, String[] votes );
    }

    private State state;
    private String source;
    private int lineNo;

    private List<Action> out;
    private String action;
    private String activeCommittee;
    private String[] ayes;
    private String[] noes;
    private String[] present;
    private String[] absent;
    private String[] absentWithLeave;
    private String[] vacancies;

    // 'ayes', 'noes'.. ie. which group of votes are we currently parsing; used for multi-line
    // vote groups.
    private VoteGroup currentVoteGroup;
    // Once vote group is analyzed, move back to this state
    private State postVoteBlockState;

    // Set if we expect a vote block coming
    private boolean voteExpected;

    public List<Action> analyze( String url, Iterable<String> inputLines ) {
        this.source = url;
        this.out = new ArrayList<>();
        this.state = State.SEEKING;
        this.voteExpected = false;

        this.lineNo = 1;
        for ( String line : inputLines )
        {
            if(Patterns.header.matcher( line ).matches() ) {
                line = "";
            }
//            System.err.printf("%s: %s%n", state, line);

            state = state.analyze( this, line );
            this.lineNo++;
        }

        return out;
    }

    private void clearVoteState()
    {
        ayes = null;
        noes = null;
        absent = null;
        present = null;
        absentWithLeave = null;
        vacancies = null;
    }

    private void addEvent( Action ev) {
        out.add( new SourcedAction( ev, new Source( source, lineNo ) ) );
    }

    private static String[] parseVoteBlock(String line) {
        List<String> out = new ArrayList<>();
        String[] parts = line.split( " {2}" );
        for ( String part : parts )
        {
            if(part.isBlank()) {
                continue;
            }
            part = part.trim();
            if(!part.toLowerCase().matches( "[a-z’'.-]+( [a-z0-9’'.-]+)*" )) {
                System.err.println(out);
                System.err.printf("L: '%s'%n", line);
                throw new RuntimeException( "!!" );
            }
            out.add( part );
        }
        return out.toArray( new String[0] );
    }

    private static String[] trim(String[] tokens) {
        ArrayList<String> out = new ArrayList<>();
        for ( String token : tokens )
        {
            token = token.strip();
            if(token.isEmpty()) {
                continue;
            }
            out.add( token );
        }
        return out.toArray( new String[0] );
    }

    private static <T> T[] concat(T[] a, T[] b) {
        int aLen = a.length;
        int bLen = b.length;

        @SuppressWarnings("unchecked")
        T[] c = (T[]) Array.newInstance(a.getClass().getComponentType(), aLen + bLen);
        System.arraycopy(a, 0, c, 0, aLen);
        System.arraycopy(b, 0, c, aLen, bLen);

        return c;
    }

    private static boolean containsIgnoreCase(String search, Set<String> haystack) {
        if(haystack == null) {
            return false;
        }
        for ( String rep : haystack )
        {
            if(rep.equalsIgnoreCase( search )) {
                return true;
            }
        }
        return false;
    }

    // Subtract the set b from the set a
    private static <T> Set<T> subtract(Set<T> a, Set<T> b) {
        Set<T> out = new HashSet<T>();
        for ( T it : a )
        {
            if(!b.contains( it )) {
                out.add( it );
            }
        }
        return out;
    }


    // Describe the difference between sets left and right, from the perspective of set left
    // Return the empty string if the sets are equal.
    private static <T> String diff(Set<T> left, Set<T> right) {
        Set<T> extra = subtract( left, right );
        Set<T> missing = subtract( right, left );
        if(extra.size() > 0 && missing.size() > 0) {
            return String.format( "extra elements: %s, missing elements: %s", extra, missing );
        } else if(extra.size() > 0) {
            return String.format( "extra elements: %s", extra );
        } else if(missing.size() > 0) {
            return String.format( "missing elements: %s", missing );
        } else {
            return "";
        }
    }
}

class Patterns {
    static Pattern journalApproval = Pattern.compile(".*The Journal of the (.*) day was approved as (.*) by the following vote.*");
    static Pattern committeeReport = Pattern.compile( ".*Your (Special )?Committee on (.*),.*" );
    // On motion of Representative Dogan, House Amendment No. 2, as amended, was
    // On motion of Representative Schroer, House Amendment No. 1 was adopted.
    // On motion of Representative Patterson, SS HCS HB 677 was adopted by the following
    static Pattern motionOf = Pattern.compile(".*On motion of Representative (.*), (.*)(?:,| was).*");
    // Representative Taylor moved that House Amendment No. 1 be adopted
    static Pattern moveToAdopt = Pattern.compile(" Representative (.+) moved that (.+)");
    // Which motion was defeated by the following vote, the ayes and noes having been
    // Which motion was adopted by the following vote:
    static Pattern whichMotionWasXByVote = Pattern.compile(".*Which motion was (.+) by the following vote.*");
    // HB 445, relating to ethics, was taken up by Representative Dogan.
    static Pattern takenUp = Pattern.compile(".*was taken up.*");
    // The emergency clause was adopted by the following vote
    static Pattern xWasAdoptedByVote = Pattern.compile("(.*) was adopted by the following vote.*");
    // The emergency clause was defeated by the following vote:
    static Pattern xWasDefeatedByVote = Pattern.compile("(.*) was defeated by the following vote.*");
    // The Committee on Ethics adopted this report by a vote of 10 to 0:
    static Pattern committeeAdoptedReportByVote = Pattern.compile("(.+) adopted this report by a vote of.*");
    // This report was adopted by the Committee
    static Pattern unknownCommitteeVoted = Pattern.compile("This report was adopted by the Committee by a vote.*");

    // The ruling of the Chair was sustained by the following vote, the ayes and noes having
    static Pattern rulingOfChairSustainedByVote = Pattern.compile(".*The ruling of the Chair was sustained by the following vote.*");

    // HCR 1, HR 1, HCS HB 1158, SS SB 213, SB 185, HCS SB 275, SS HB 138, HCS SCS SB 174, SBs 70 & 128, HB 616
    // HCS HBs 275 & 853, SCR 1, HCB 1, SS HCS#2, SS SB 145, HJR 30, SS#2 SCR 14, HBs 243 & 544, CCR SS SCS HCS HB 399
    // SS SCS SJRs 14 & 9, HCS HJRs 48, HRB 1
    static Pattern bill = Pattern.compile( ".*((HRB \\d+)|(HCS HJRs \\d+)|(SS SCS SJRs \\d+ & \\d+)|(CCR SS SCS HCS HB \\d+)|(HBs \\d+ & \\d+)|(SS#?\\d+ SCR \\d+)|(HJR ?#?\\d+)|(HCB ?#?\\d+)|(SCR ?#?\\d+)|(HB ?#?\\d+)|(HCS HBs \\d+ & \\d+)|(SBs \\d+ & \\d+)|(SB ?#?\\d)|(HCS SCS SB ?#?\\d+)|(SS HB ?#?\\d+)|(HCS SB ?#?\\d+)|(SS SB ?#?\\d+)|(HCS HB ?#?\\d+)|(SS HCS ?#?\\d+)|(HCR ?#?\\d+)|(HR ?#?\\d+)).*" );

    // 33.282, RSMo
    static Pattern statutes = Pattern.compile( ".*((\\d+.\\d+), RSMo).*" );

    static Pattern header = Pattern.compile("(\\s*(\\d+)\\s*Journal of the House.*)|([a-zA-Z-]+ Day–[a-zA-Z]+, [a-zA-Z]+ \\d+, \\d+\\s*\\d+\\s*)");
}
