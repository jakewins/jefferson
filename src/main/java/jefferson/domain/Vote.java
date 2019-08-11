package jefferson.domain;

import jefferson.analyzer.Collections;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class Vote
{
    public final Set<String> ayes;
    public final Set<String> noes;
    public final Set<String> absent;
    public final Set<String> absentWithLeave;
    public final Set<String> present;

    public enum Group {
        AYES,
        NOES,
        ABSENT,
        ABSENT_WITH_LEAVE,
        PRESENT
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

    public Group voteOfRep( String name ) {
        if( Collections.containsIgnoreCase(name, ayes)) {
            return Group.AYES;
        }
        if( Collections.containsIgnoreCase(name, noes)) {
            return Group.NOES;
        }
        if( Collections.containsIgnoreCase(name, absent)) {
            return Group.ABSENT;
        }
        if( Collections.containsIgnoreCase(name, absentWithLeave)) {
            return Group.ABSENT_WITH_LEAVE;
        }
        if( Collections.containsIgnoreCase(name, present)) {
            return Group.PRESENT;
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
        String ayeDiff = Collections.diff( ayes, other.ayes );
        String noeDiff = Collections.diff( noes, other.noes );
        String absentDiff = Collections.diff( absent, other.absent );
        String absentWithLeaveDiff = Collections.diff( absentWithLeave, other.absentWithLeave );
        String presentDiff = Collections.diff( present, other.present );
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
