package jefferson.domain;

import jefferson.analyzer.Source;

public interface Action
{
    Vote vote();
    Motion motion();
    default Source source() { return null; }
}
