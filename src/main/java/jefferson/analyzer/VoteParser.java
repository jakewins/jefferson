package jefferson.analyzer;

import jefferson.Sanitizer;
import jefferson.domain.Vote;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class VoteParser
{
    enum State {
        DONE {
            @Override
            State analyze( VoteParser ctx, Sanitizer.Paragraph pg )
            {
                throw new RuntimeException( "Vote parser at terminal state, needs .newVote() called" );
            }
        },
        // Looking for the next vote block statrt, like AYES: .., NOES: ..
        SEEK {
            @Override
            State analyze( VoteParser ctx, Sanitizer.Paragraph pg )
            {
                if(pg.source.lineNo == 352 && pg.source.sourceUrl.contains( "004" )) {
                    System.out.println("!!");
                }
                if( isVoteGroupHeader( pg ) ) {
                    String[] parts = pg.contents.split( ":", 2 );
                    String voteGroup = parts[0].toLowerCase().trim();
                    String expected = parts[1];

                    // In some (rare) cases, the vote block comes without a linebreak, so
                    // it's like "AYES: 123 Bob Tina Albert ..,
                    // and if that happens, break the part after the number off and parse it
                    // as a vote block
                    if(expected.toLowerCase().matches( "\\s*\\d+\\s+[a-z]+.*" )) {
                        System.err.println("Vote block on same line, breaking apart..");
                        parts = expected.trim().split( " ", 2 );
                        expected = parts[0];
                        String voteBlock = parts[1];
                        int expectedVotes = Integer.parseInt( expected.trim() );
                        if(expectedVotes > 0) {
                            ctx.vote.currentVoteGroup = voteGroup;
                            ctx.vote.setExpectedVotesInCurrentGroup(expectedVotes);
                            return IN_VOTEBLOCK.analyze( ctx, new Sanitizer.Paragraph( pg.source, voteBlock ) );
                        }
                        return SEEK;
                    }

                    int expectedVotes = Integer.parseInt( expected.trim() );
                    if(expectedVotes > 0) {
                        ctx.vote.currentVoteGroup = voteGroup;
                        ctx.vote.setExpectedVotesInCurrentGroup(expectedVotes);
                        return IN_VOTEBLOCK;
                    }
                    return SEEK;
                }
                if(pg.contents.contains( "VACANCIES" )) {
                    return SEEK;
                }
                // Once we reach something we don't recognize as voting, exit vote parsing
                return DONE;
            }
        },
        IN_VOTEBLOCK {
            @Override
            State analyze( VoteParser ctx, Sanitizer.Paragraph pg )
            {
                if( isVoteGroupHeader( pg ) ) {
                    System.err.println(ctx.vote.currentVoteGroup);
                    System.err.println(Arrays.toString(ctx.vote.votesInCurrentGroup()));
                    System.err.printf("%s/%s%n", ctx.vote.votesInCurrentGroup().length, ctx.vote.expectedVotesInCurrentGroup());
                    throw new RuntimeException( "Encountered vote group header before finding all expected votes." );
                }
                List<String> out = null;
                // Once we have enough known reps, the known rep parse method is more reliable
                // This will return null if it fails, so we'll go back to the split method.
                // If that fails we run the known rep method again and get the error out of it.
                if(ctx.knownReps.size() > 100) {
                    out = ctx.parseUsingKnownRepMethod( pg );
                }
                if(out == null) {
                    out = ctx.parseUsingSplitMethod( pg );
                    System.err.println("Parsed using split method.");
                } else {
                    System.err.println("Parsed using known rep method.");
                }
                if(!sanityCheck( out )) {
                    out = ctx.parseUsingKnownRepMethod( pg );
                    if(!sanityCheck( out )) {
                        System.err.println(out);
                        throw new RuntimeException( "Failed sanity check after known rep method" );
                    }
                }
                ctx.addVotesToCurrentGroup( out.toArray(new String[]{}) );
                if(ctx.vote.votesInCurrentGroup().length == ctx.vote.expectedVotesInCurrentGroup()) {
                    return SEEK;
                }
                if(ctx.vote.votesInCurrentGroup().length > ctx.vote.expectedVotesInCurrentGroup()) {
                    throw new RuntimeException( String.format("Expected %d votes but found more: %s",
                            ctx.vote.expectedVotesInCurrentGroup(),
                            Arrays.toString(ctx.vote.votesInCurrentGroup())) );
                }

                // There must've been a page break in the middle of the vote block, keep looking for votes.
                return IN_VOTEBLOCK;
            }
        };

        private static boolean isVoteGroupHeader( Sanitizer.Paragraph pg )
        {
            return pg.contents.contains( "AYES" ) || pg.contents.contains( "NOES" )
                    || pg.contents.contains( "PRESENT" ) || pg.contents.contains( "ABSENT" )
                    || pg.contents.contains( "ABSENT WITH LEAVE" );
        }

        abstract State analyze( VoteParser ctx, Sanitizer.Paragraph pg );
    }

    // Most vote blocks will be well formatted, with two spaces between each name. This lets us
    // parse by just splitting and cleaning up
    private List<String> parseUsingSplitMethod( Sanitizer.Paragraph pg )
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
                System.err.printf("L: '%s' / %s%n", pg.contents, vote);
                System.out.println(Arrays.toString( vote.votesInCurrentGroup() ));
                System.out.println(vote.currentVoteGroup);
                System.out.println(vote.expectedVotesInCurrentGroup());
                System.out.println(vote.votesInCurrentGroup().length);
                throw new RuntimeException( "!!" );
            }
            out.add( part );
        }
        return out;
    }

    // This uses the list of known reps to try to work them out of the paragraph.
    // This only works if we know about all the reps that may be in the paragraph, so we use
    // this as a fallback if the split method doesn't work. That way the split method can build
    // up a list of known reps, hopefully, and once we hit a paragraph the split method fails at
    // hopefully we've got enough known reps to use this method.
    private List<String> parseUsingKnownRepMethod( Sanitizer.Paragraph pg ) {
        if(pg.source.lineNo == 559) {
            System.out.println("!");
        }
        List<String> out = new ArrayList<>();
        String content = pg.contents.toLowerCase();
        for ( String knownRep : knownReps ) {
            int index = content.indexOf( knownRep );
            if(index == -1) {
                continue;
            }
            out.add( knownRep );
            content = content.replace( knownRep, "" );
        }

        if(!content.isBlank()) {
            System.err.println("Split vote parse method failed, remaining content: " + content);
            return null;
        }

        return out;
    }

    // Check that the list we've parsed seems to actually contain reasonable names
    private static boolean sanityCheck(List<String> reps) {
        if(reps == null) {
            return false;
        }
        for ( String rep : reps )
        {
            if(!rep.toLowerCase().matches( "[a-z’'.-]+( [a-z0-9’'.-]+){0,2}" )) {
                return false;
            }
        }
        return true;
    }

    private Analyzer.VoteContext vote;
    private State state = State.DONE;

    // If we fail in parsing, we fall back to trying to pull these names out.
    // To reduce the search space, these are all lowercase
    private final SortedSet<String> knownReps = new TreeSet<>( (a, b) -> {
        // Sort by string length, because some of the shorter names may be substrings in
        // the longer names, so we need to pull out the longer names first.
        int difference = Comparator.comparingInt( String::length ).reversed().compare( a, b );
        // We can't return 0, because then treeset thinks the strings are equal and deletes one
        if(difference != 0) {
            return difference;
        }
        // If they are the same length, sort alphabetically
        return a.compareTo( b );
    } );

    public void newVote() {
        vote = new Analyzer.VoteContext();
        state = State.SEEK;
    }

    public Vote toVote()
    {
        return vote.toVote();
    }

    // Parse longform vote, you need to call newVote() before you start feeding stuff to this.
    // Returns true when it has completed parsing, at which point the paragraph just passed in
    // is *not* processed and should be handled as a paragraph coming in after the vote block.
    public boolean analyze( Sanitizer.Paragraph pg ) {
        state = state.analyze( this, pg );
        return state == State.DONE;
    }

    // Called during parsing as we find votes
    private void addVotesToCurrentGroup( String[] reps ) {
        knownReps.addAll( Arrays.stream( reps ).map( String::toLowerCase ).map( String::trim ).collect( Collectors.toList() ) );
        vote.addVotesToCurrentGroup( reps );
    }
}
