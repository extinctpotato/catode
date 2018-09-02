package in.shick.diode.filters;

import in.shick.diode.settings.RedditSettings;
import in.shick.diode.things.ThingInfo;
import java.util.ArrayList;
import android.content.Context;
import android.util.Log;


/** Filter engine
 * This class contains a set of SubredditFilters, and allows for determining whether a ThingInfo
 * matches any of the filters
 * @author tordo
 */
public class RedditFilterEngine
{
    /** The filters */
    protected ArrayList<SubredditFilter> m_filters;
    /** Context (for RedditSettings) */
    protected Context m_context;
    /** Tag for logging */
    private static final String TAG = "RedditFilterEngine";

    /**
     * Constructor
     * @param c
     */
    public RedditFilterEngine(Context c)
    {
        setContext(c);
        initialize();
    }

    /**
     * Set context
     * @param c
     */
    public void setContext(Context c)
    {
        m_context = c;
    }

    /**
     * Initialize filters
     * Pull filters from RedditPreferences
     */
    protected void initialize()
    {
        RedditSettings s = new RedditSettings();
        s.loadRedditPreferences(m_context, null);
        m_filters = s.getFilters();

    }

    /**
     * The big fat filtering method. This method checks if the supplied ThingInfo (what a peculiar name btw)
     * should be filtered away
     * @param t
     * @return true if post should be discarded, false otherwise
     */
    public boolean isFiltered(ThingInfo t)
    {
        if(m_filters == null)
        {
            Log.d(TAG, "isFiltered: Not initialized!");
            return false;
        }
        for(SubredditFilter f : m_filters)
        {
            // Check if the post is in the correct subreddit
            if(f.isEnabled() && t.getSubreddit().equalsIgnoreCase(f.getSubReddit()))
            {
                if(f.getPattern().matcher(t.getTitle()).find()) {
                    // We found a match! The post sohuld be filtered
                    return true;
                }
            }
        }
        // We got this far without finding a match
        return false;
    }
}
