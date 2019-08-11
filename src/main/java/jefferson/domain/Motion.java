package jefferson.domain;

import java.util.Objects;

public class Motion
{

    public enum Classification {
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

    public enum Type {
        MAIN_MOTION( Classification.MAIN ),
        AMEND( Classification.SUBSIDIARY ),
        ADOPT( Classification.SUBSIDIARY ),
        RECONSIDER_VOTE( Classification.SUBSIDIARY ),
        MISC( Classification.SUBSIDIARY ),
        END_DEBATE( Classification.SUBSIDIARY ),
        REFER_TO_COMMITTEE( Classification.SUBSIDIARY ),
        REFUSE_TO_RECEDE( Classification.SUBSIDIARY ),
        REFUSE_TO_ADOPT( Classification.SUBSIDIARY ),
        ;
        public final Classification classification;

        Type( Classification classification )
        {
            this.classification = classification;
        }
    }

    public final Type type;

    public final String proposal;
    // If this is a motion about another motion - like a motion to amend a bill - this
    // is the motion this motion is talking about. So if this motion is to adopt an amendment,
    // then the thing being amended is pointed to here.
    public final Motion relatesTo;

    public Motion( Type type, String proposal, Motion relatesTo )
    {
        this.type = type;
        this.proposal = proposal;
        this.relatesTo = relatesTo;
    }

    // Backtrack until we find the main motion
    public Motion mainMotion()
    {
        if(relatesTo == null) {
            if(type != Type.MAIN_MOTION) {
                throw new IllegalStateException( this + " does not relate to any other motion, but is not a MAIN_MOTION?" );
            }
            return this;
        }
        return relatesTo.mainMotion();
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
