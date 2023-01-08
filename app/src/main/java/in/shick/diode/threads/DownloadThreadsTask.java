package in.shick.diode.threads;

import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import android.net.Uri;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.type.TypeFactory;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import in.shick.diode.common.CacheInfo;
import in.shick.diode.common.Common;
import in.shick.diode.common.Constants;
import in.shick.diode.common.ProgressInputStream;
import in.shick.diode.common.util.StringUtils;
import in.shick.diode.filters.RedditFilterEngine;
import in.shick.diode.things.HnItem;
import in.shick.diode.things.Listing;
import in.shick.diode.things.ListingData;
import in.shick.diode.things.ThingInfo;
import in.shick.diode.things.ThingListing;
import in.shick.diode.settings.RedditSettings;




/**
 * Given a subreddit name string, starts the threadlist-download-thread going.
 *
 * @param subreddit The name of a subreddit ("android", "gaming", etc.)
 *        If the number of elements in subreddit is >= 2, treat 2nd element as "after"
 */
public abstract class DownloadThreadsTask extends AsyncTask<Void, Long, Boolean> implements PropertyChangeListener {

    static final String TAG = "DownloadThreadsTask";

    protected Context mContext;
    protected final HttpClient mClient;
    private ObjectMapper mOm;

    protected String mSubreddit;
    protected String mSortByUrl = Constants.ThreadsSort.SORT_BY_HOT_URL;
    protected String mSortByUrlExtra = "";
    protected String mAfter;
    protected String mBefore;
    protected int mCount;
    protected String mLastAfter = null;
    protected String mLastBefore = null;
    protected int mLastCount = 0;
    protected boolean mIsSearch = false;
    protected RedditSettings mSettings = new RedditSettings();

    //the GET parameters to be passed when performing a search
    //just get it to recognize the query first, get sort working later.
    protected String mSearchQuery;
    protected String mSortSearch; //not implemented yet

    Uri mDTTSavedURI;

    protected String mUserError = "Error retrieving subreddit info.";

    protected RedditFilterEngine mFilterEngine;
    // Progress bar
    protected long mContentLength = 0;

    // Downloaded data
    protected ArrayList<ThingInfo> mThingInfos = new ArrayList<ThingInfo>();
    protected String mModhash = null;
    protected ArrayList<HnItem> mHnItemsList = new ArrayList<HnItem>();

    public DownloadThreadsTask(Context context, HttpClient client, ObjectMapper om,
                               String sortByUrl, String sortByUrlExtra,
                               String subreddit, String query) {
        this(context, client, om, sortByUrl, sortByUrlExtra, subreddit, null, null, Constants.DEFAULT_THREAD_DOWNLOAD_LIMIT);
        mSearchQuery = query;
    }

    public DownloadThreadsTask(Context context, HttpClient client, ObjectMapper om,
                               String sortByUrl, String sortByUrlExtra,
                               String subreddit, String query, boolean isSearch) {
        this(context, client, om, sortByUrl, sortByUrlExtra, subreddit, null, null, Constants.DEFAULT_THREAD_DOWNLOAD_LIMIT);
        mSearchQuery = query;
        mIsSearch = isSearch;
    }

    public DownloadThreadsTask(Context context, HttpClient client, ObjectMapper om,
                               String sortByUrl, String sortByUrlExtra,
                               String subreddit, String query, String after, String before, boolean isSearch) {
        this(context, client, om, sortByUrl, sortByUrlExtra, subreddit, after, before, Constants.DEFAULT_THREAD_DOWNLOAD_LIMIT);
        mSearchQuery = query;
        mIsSearch = isSearch;
    }

    public DownloadThreadsTask(Context context, HttpClient client, ObjectMapper om,
                               String sortByUrl, String sortByUrlExtra,
                               String subreddit, Uri redditURI) {
        this(context, client, om, sortByUrl, sortByUrlExtra, subreddit, null, null, Constants.DEFAULT_THREAD_DOWNLOAD_LIMIT);
        mDTTSavedURI = redditURI;
    }

    public DownloadThreadsTask(Context context, HttpClient client, ObjectMapper om,
                               String sortByUrl, String sortByUrlExtra,
                               String subreddit, Uri redditURI, String after, String before, int count) {
        this(context, client, om, sortByUrl, sortByUrlExtra, subreddit, after, before, count);
        mDTTSavedURI = redditURI;
    }

    public DownloadThreadsTask(Context context, HttpClient client, ObjectMapper om,
                               String sortByUrl, String sortByUrlExtra,
                               String subreddit) {
        this(context, client, om, sortByUrl, sortByUrlExtra, subreddit, null, null, Constants.DEFAULT_THREAD_DOWNLOAD_LIMIT);
    }

    public DownloadThreadsTask(Context context, HttpClient client, ObjectMapper om,
                               String sortByUrl, String sortByUrlExtra,
                               String subreddit, String after, String before, int count) {
        mSettings.loadRedditPreferences(context, null);
        mContext = context;
        mClient = client;
        mOm = om;
        mSortByUrl = sortByUrl;
        mSortByUrlExtra = sortByUrlExtra;
        mDTTSavedURI = null;
        if (subreddit != null) {
            mSubreddit = subreddit;
        } else {
            mSubreddit = Constants.FRONTPAGE_STRING;
        }

        mSortSearch = sortByUrl;

        mAfter = after;
        mBefore = before;
        mCount = count;
        mFilterEngine = new RedditFilterEngine(mContext);
    }

    public Boolean doInBackground(Void... zzz) {
        HttpEntity entity = null;
        boolean isAfter = false;
        boolean isBefore = false;
        String url;
        StringBuilder sb;
        // If refreshing or something, use the previously used URL to get the threads.
        // Picking a new subreddit will erase the saved URL, getting rid of after= and before=.
        // subreddit.length != 0 means you are going Next or Prev, which creates new URL.

        // Load specified HN item list task
        sb = new StringBuilder(Constants.HN_BASE_URL + "/v0/")
                .append(mSubreddit.trim())
                .append(".json");

        // "before" always comes back null unless you provide correct "count"
        //if (mAfter != null) {
        //    // count: 25, 50, ...
        //    sb = sb.append("&count=").append(mCount)
        //         .append("&after=").append(mAfter).append("&");
        //    isAfter = true;
        //}
        //else if (mBefore != null) {
        //    // count: nothing, 26, 51, ...
        //    sb = sb.append("&count=").append(mCount + 1 - Constants.DEFAULT_THREAD_DOWNLOAD_LIMIT)
        //         .append("&before=").append(mBefore).append("&");
        //    isBefore = true;
        //}

        url = sb.toString();
        if (Constants.LOGGING) Log.d(TAG, "url=" + url);

        InputStream in = null;
        boolean currentlyUsingCache = false;

        HttpGet request;
        try {
            request = new HttpGet(url);
        } catch (IllegalArgumentException e) {
            mUserError = "Invalid subreddit.";
            if (Constants.LOGGING) Log.e(TAG, "IllegalArgumentException", e);
            return false;
        }
        HttpResponse response = null;
        try {
            response = mClient.execute(request);
        } catch (IOException e) {
            if (Constants.LOGGING) Log.e(TAG, "Error while fetching items", e);
            return false;
        }

        // Read the header to get Content-Length since entity.getContentLength() returns -1
        Header contentLengthHeader = response.getFirstHeader("Content-Length");

        entity = response.getEntity();
        try {
            in = entity.getContent();
        } catch (IOException e) {
            if (Constants.LOGGING) Log.e(TAG, "Unable to get content", e);
            return false;
        }

        if (contentLengthHeader != null) {
            mContentLength = Long.valueOf(contentLengthHeader.getValue());
            if (Constants.LOGGING) Log.d(TAG, "Content length [sent]: " + mContentLength);
        } else {
            mContentLength = -1;
            if (Constants.LOGGING) Log.d(TAG, "Content length not available");
        }

        ProgressInputStream pin = new ProgressInputStream(in, mContentLength);
        pin.addPropertyChangeListener(this);

        List<Integer> listing = new ArrayList<Integer>();
        try {
            listing = mOm.readValue(pin,
                    TypeFactory
                            .defaultInstance()
                            .constructCollectionType(List.class, Integer.class));
        } catch (JsonParseException e) {
            mUserError = "Unable to parse items for " + mSubreddit;
        } catch (IOException e) {
            mUserError = "I/O exception occurred while parsing items for " + mSubreddit;
        } finally {
            try {
                pin.close();
                in.close();
            } catch (IOException e) {
                if (Constants.LOGGING) Log.e(TAG, "failed to close input stream", e);
            }
            try {
                entity.consumeContent();
            } catch (Exception e2) {
                if (Constants.LOGGING) Log.e(TAG, "entity.consumeContent()", e2);
            }
        }

        fetchHnThreads(listing, 1);
        saveState();
        return true;
    }

    protected void fetchHnThreads(List<Integer> items, int page) {
        int maxItems = 10;
        int startIndex = maxItems * (page - 1);
        int stopIndex = startIndex + maxItems;

        for (ListIterator<Integer> i = items.listIterator(startIndex); i.hasNext() && i.nextIndex() < stopIndex;) {
            StringBuilder sb = new StringBuilder(Constants.HN_BASE_URL + "/v0/")
                    .append("item/")
                    .append(i.next())
                    .append(".json");
            if (Constants.LOGGING) Log.d(TAG, "fetchHnThreads: " + sb);

            HttpGet request = new HttpGet(sb.toString());
            HttpResponse response = null;
            InputStream in = null;
            HnItem thread;
            try {
                response = mClient.execute(request);
            } catch (IOException e) {
                if (Constants.LOGGING) Log.e(TAG, "Error while fetching item ", e);
                continue;
            }
            try {
                in = response.getEntity().getContent();
            } catch (IOException e) {
                if (Constants.LOGGING) Log.e(TAG, "Error while getting body ", e);
                continue;
            }

            try {
                thread = mOm.readValue(in, HnItem.class);
            } catch (Exception e) {
                if (Constants.LOGGING) Log.e(TAG, "Error while parsing thread ", e);
                continue;
            }

            mHnItemsList.add(thread);
        }
    }

    protected void parseSubredditJSON(InputStream in)
    throws IOException, JsonParseException, IllegalStateException {

        String genericListingError = "Not a subreddit listing";
        List<Integer> listing;
        try {
            //List<Integer> listing = new ArrayList<Integer>();
            //listing = mOm.readValue(in, listing.getClass());
            //Listing listing = mOm.readValue(in, Listing.class);

            //if (!Constants.JSON_LISTING.equals(listing.getKind()))
            //    throw new IllegalStateException(genericListingError);
            //// Save the modhash, after, and before
            //ListingData data = listing.getData();
            //if (StringUtils.isEmpty(data.getModhash()))
            //    mModhash = null;
            //else
            //    mModhash = data.getModhash();

            //mLastAfter = mAfter;
            //mLastBefore = mBefore;
            //mAfter = data.getAfter();
            //mBefore = data.getBefore();

            //// Go through the children and get the ThingInfos
            //for (ThingListing tiContainer : data.getChildren()) {
            //    // Only add entries that are threads. kind="t3"
            //    if (Constants.THREAD_KIND.equals(tiContainer.getKind())) {
            //        ThingInfo ti = tiContainer.getData();
            //        ti.setClicked(Common.isClicked(mContext, ti.getUrl()));
            //        if((mSettings.getShowNSFW() || !ti.isOver_18()) && !mFilterEngine.isFiltered(ti)) {
            //            mThingInfos.add(ti);
            //        }
            //    }
            //}
        } catch (Exception ex) {
            if (Constants.LOGGING) Log.e(TAG, "parseSubredditJSON", ex);
        }
    }

    abstract protected void saveState();
}
