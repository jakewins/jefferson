package jefferson.domain;

import jefferson.analyzer.Source;

import java.util.HashMap;
import java.util.Map;

public interface Action
{
    Vote vote();
    Motion motion();
    default Source source() { return null; }

    default Map<String, Object> toMap() {
        Map<String, Object> out = new HashMap<>();
        out.put( "motion", motion().toMap() );
        if(vote() != null) {
            out.put( "vote", vote().toMap() );
        }
        return out;
    }
}
