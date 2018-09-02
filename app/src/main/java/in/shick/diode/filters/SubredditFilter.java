package in.shick.diode.filters;

import java.util.regex.Pattern;


/**
 * Represents a subreddit filter, for use by RedditFilterEngine
 * @author tordo
 *
 */
public class SubredditFilter
{
    /** The name of this filter */
    protected String m_name;
    /** The regex pattern to match */
    protected Pattern m_pattern;
    /** The name of the subreddit to match */
    protected String m_subreddit;
    /** The pattern string */
    protected String m_patternstring;
    /** Whether or not this filter is enabled */
    protected boolean m_enabled;
    /** Delimiter for to/fromString */
    private static final String DELIM = "\t";

    /**
     * Subreddit filter constructor
     * @param name Name of the filter
     * @param sub Name of the subreddit filter
     * @param pattern Pattern to exclude
     */
    public SubredditFilter(String name, String sub,boolean enabled, String pattern)
    {
        setName(name);
        setPattern(pattern);
        setSubreddit(sub);
        setEnabled(enabled);
    }

    /**
     * Set name of the filter
     * @param name
     */
    public void setName(String name)
    {
        m_name = name;
    }

    /**
     * Set pattern to exclude
     * @param pattern
     */
    public void setPattern(String pattern)
    {
        m_patternstring = pattern;
        setPattern(Pattern.compile("(?i)" + Pattern.quote(pattern)));
    }

    /**
     *
     * @return The unescaped pattern string
     */
    public String getPatternString()
    {
        return m_patternstring;
    }

    /**
     * Set pattern to exclude
     * @param p
     */
    public void setPattern(Pattern p)
    {
        m_pattern = p;

    }

    /** Get filter pattern
     *
     * @return The pattern to exclude
     */
    public Pattern getPattern()
    {
        return m_pattern;
    }

    /**
     * Set subreddit
     * @param sub subreddit
     */
    public void setSubreddit(String sub)
    {
        m_subreddit = sub;
    }

    /**
     * Get subreddit
     * @return name of subreddit
     */
    public String getSubReddit()
    {
        return m_subreddit;
    }

    /**
     * Get name
     * @return name
     */
    public String getName()
    {
        return m_name;
    }

    /**
     * Filter enabled
     * @return True if filter is enabled, false otherwise
     */
    public boolean isEnabled() {
        return m_enabled;
    }

    /**
     * Enable/disable filter
     * @param e true to enable, false to disable
     */
    public void setEnabled(boolean e)
    {
        m_enabled = e;
    }

    /**
     * Turn this subreddit filter into a string for saving in preferences
     * @return serialized filter, can be parsed with fromString
     */
    public String toString() {
        return m_name + DELIM + m_subreddit + DELIM + m_enabled + DELIM + m_patternstring;
    }

    /**
     * Build a SubredditFilter from string
     * @param serialized A SubredditFilter as serialized by SubredditFilter.toString()
     * @return The SubredditFilter constructed from the supplied string
     * */
    public static SubredditFilter fromString(String serialized) {
        String[] fields = serialized.split(DELIM,4);
        return new SubredditFilter(fields[0],fields[1],Boolean.parseBoolean(fields[2]), fields[3]);
    }

}

