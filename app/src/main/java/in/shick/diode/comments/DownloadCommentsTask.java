package in.shick.diode.comments;

import android.os.AsyncTask;
import android.text.Html;
import android.text.SpannableString;
import android.text.Spanned;
import android.util.Log;
import android.view.Window;
import android.widget.Toast;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.ObjectMapper;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.LinkedList;

import in.shick.diode.comments.ProcessCommentsTask.DeferredCommentProcessing;
import in.shick.diode.common.CacheInfo;
import in.shick.diode.common.Common;
import in.shick.diode.common.Constants;
import in.shick.diode.common.ProgressInputStream;
import in.shick.diode.common.util.Assert;
import in.shick.diode.common.util.StringUtils;
import in.shick.diode.common.util.Util;
import in.shick.diode.settings.RedditSettings;
import in.shick.diode.things.Listing;
import in.shick.diode.things.ListingData;
import in.shick.diode.things.ThingInfo;
import in.shick.diode.things.ThingListing;

/**
 * Task takes in a subreddit name string and thread id, downloads its data, parses
 * out the comments, and communicates them back to the UI as they are read.
 *
 * Requires the following navigation variables to be set:
 * mSettings.subreddit
 * mSettings.threadId
 * mMoreChildrenId (can be "")
 * mSortByUrl
 */
public class DownloadCommentsTask extends AsyncTask<Integer, Long, Boolean>
    implements PropertyChangeListener {


    private static final String TAG = "DownloadCommentsTask";
    private final ObjectMapper mObjectMapper = Common.getObjectMapper();

    private static AsyncTask<?, ?, ?> mCurrentDownloadCommentsTask = null;
    private static final Object mCurrentDownloadCommentsTaskLock = new Object();

    private ProcessCommentsTask mProcessCommentsTask;

    private CommentsListActivity mActivity;
    private String mSubreddit;
    private String mThreadId;
    private String mThreadTitle;
    private String mThingInfoIdToPositionTo;
    private RedditSettings mSettings;
    private HttpClient mClient;

    // offset of the first comment being loaded; 0 if it includes OP
    private int mPositionOffset = 0;
    private int mIndentation = 0;
    private String mMoreChildrenId = "";
    private ThingInfo mOpThingInfo = null;

    // Progress bar
    private long mContentLength = 0;

    private String mJumpToCommentId = "";
    private int mJumpToCommentFoundIndex = -1;

    private int mJumpToCommentContext = 0;

    // Lazily-created if the author has a comment in the thread.
    private SpannableString m_OPSpan = null;

    /**
     * List holding the comments to be appended at the end.
     * Used when loading an entire thread.
     */
    private final LinkedList<ThingInfo> mDeferredAppendList = new LinkedList<ThingInfo>();
    /**
     * List holding the comments to be inserted at mPositionOffset; the existing comment there will be removed.
     * Used for "load more comments" links.
     */
    private final LinkedList<ThingInfo> mDeferredReplacementList = new LinkedList<ThingInfo>();

    /**
     * Protected default constructor for subclasses, don't allow it to be called normally.
     */
    protected DownloadCommentsTask() {
        // Do nothing
    }
    /**
     * Default constructor to do normal comments page
     */
    public DownloadCommentsTask(
        CommentsListActivity activity,
        String subreddit,
        String threadId,
        RedditSettings settings,
        HttpClient client,
        String contextOP,
        int contextAmount
    ) {
        attach(activity);
        this.mSubreddit = subreddit;
        this.mThreadId = threadId;
        this.mSettings = settings;
        this.mClient = client;
        this.mProcessCommentsTask = new ProcessCommentsTask(mActivity);
        this.mJumpToCommentId = contextOP;
        this.mJumpToCommentContext = contextAmount;
    }

    public DownloadCommentsTask withPositionTo(String thingId) {
        this.mThingInfoIdToPositionTo = StringUtils.isEmpty(thingId) ? "" : thingId;
        return this;
    }

    /**
     * "load more comments" starting at this position
     * @param moreChildrenId The reddit thing-id of the "more" children comment
     * @param morePosition Position in local list to insert
     * @param indentation The indentation level of the child.
     */
    public DownloadCommentsTask prepareLoadMoreComments(String moreChildrenId, int morePosition, int indentation) {
        mMoreChildrenId = moreChildrenId;
        mPositionOffset = morePosition;
        mIndentation = indentation;
        mThingInfoIdToPositionTo = "";
        return this;
    }

    // XXX: maxComments is unused for now
    public Boolean doInBackground(Integer... maxComments) {
        HttpEntity entity = null;
        try {
            StringBuilder sb = new StringBuilder(Constants.HN_BASE_URL);
            if (mSubreddit != null) {
                sb.append("/r/").append(mSubreddit.trim());
            }
            if (mMoreChildrenId != null && mMoreChildrenId.length() > 0) {
                sb.append("/comments/")
                .append(mThreadId)
                .append("/z/").append(mMoreChildrenId).append("/.json?")
                .append(mSettings.getCommentsSortByUrl());
                // Loading more with context makes no sense
            } else {
                sb.append("/comments/")
                .append(mThreadId)
                .append("/.json?")
                .append(mSettings.getCommentsSortByUrl());
                if (!StringUtils.isEmpty(mJumpToCommentId) && mJumpToCommentContext > 0) {
                    sb.append("&comment=")
                    .append(mJumpToCommentId)
                    .append("&context=")
                    .append(mJumpToCommentContext);
                }
            }

            String url = sb.toString();
            if (Constants.LOGGING) Log.d(TAG, "Loading comments from URL: " + url);
            InputStream in = null;
            boolean currentlyUsingCache = false;

            if (Constants.USE_COMMENTS_CACHE) {
                try {
                    if (CacheInfo.checkFreshThreadCache(mActivity.getApplicationContext())
                            && url.equals(CacheInfo.getCachedThreadUrl(mActivity.getApplicationContext()))) {
                        in = mActivity.openFileInput(Constants.FILENAME_THREAD_CACHE);
                        mContentLength = mActivity.getFileStreamPath(Constants.FILENAME_THREAD_CACHE).length();
                        currentlyUsingCache = true;
                        if (Constants.LOGGING) Log.d(TAG, "Using cached thread JSON, length=" + mContentLength);
                    }
                } catch (Exception cacheEx) {
                    if (Constants.LOGGING) Log.w(TAG, "skip cache", cacheEx);
                }
            }

            // If we couldn't use the cache, then do HTTP request
            if (!currentlyUsingCache) {
                HttpGet request = new HttpGet(url);
                HttpResponse response = mClient.execute(request);

                // Read the header to get Content-Length since entity.getContentLength() returns -1
                Header contentLengthHeader = response.getFirstHeader("Content-Length");
                if (contentLengthHeader != null) {
                    mContentLength = Long.valueOf(contentLengthHeader.getValue());
                    if (Constants.LOGGING) Log.d(TAG, "Content length: "+mContentLength);
                }
                else {
                    mContentLength = -1;
                    if (Constants.LOGGING) Log.d(TAG, "Content length: UNAVAILABLE");
                }

                entity = response.getEntity();
                in = entity.getContent();

                if (Constants.USE_COMMENTS_CACHE) {
                    in = CacheInfo.writeThenRead(mActivity.getApplicationContext(), in, Constants.FILENAME_THREAD_CACHE);
                    try {
                        CacheInfo.setCachedThreadUrl(mActivity.getApplicationContext(), url);
                    } catch (IOException e) {
                        if (Constants.LOGGING) Log.e(TAG, "error on setCachedThreadId", e);
                    }
                }
            }

            // setup a special InputStream to report progress
            ProgressInputStream pin = new ProgressInputStream(in, mContentLength);
            pin.addPropertyChangeListener(this);

            parseCommentsJSON(pin);
            if (Constants.LOGGING) Log.d(TAG, "parseCommentsJSON completed");

            pin.close();
            in.close();

            return true;

        } catch (Exception e) {
            if (Constants.LOGGING) Log.e(TAG, "DownloadCommentsTask", e);
        } finally {
            if (entity != null) {
                try {
                    entity.consumeContent();
                } catch (Exception e2) {
                    if (Constants.LOGGING) Log.e(TAG, "entity.consumeContent()", e2);
                }
            }
        }
        return false;
    }

    private void replaceCommentsAtPositionUI(final Collection<ThingInfo> comments, final int position) {
        mActivity.mObjectStates.mCommentsList.remove(position);
        mActivity.mObjectStates.mCommentsList.addAll(position, comments);
        mActivity.mCommentsAdapter.notifyDataSetChanged();
    }

    /**
     * defer insertion of comment for adding at end of entire comments list
     */
    private void deferCommentAppend(ThingInfo comment) {
        mDeferredAppendList.add(comment);
    }

    /**
     * defer insertion of comment for "more" case
     */
    private void deferCommentReplacement(ThingInfo comment) {
        mDeferredReplacementList.add(comment);
    }

    /**
     * tell if inserting entire thread, versus loading "more comments"
     */
    private boolean isInsertingEntireThread() {
        return mPositionOffset == 0;
    }

    private void disableLoadingScreenKeepProgress() {
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mActivity.resetUI(mActivity.mCommentsAdapter);
            }
        });
    }

    private void parseCommentsJSON(
        InputStream in
    ) throws IOException, JsonParseException {
        int insertedCommentIndex;
        String genericListingError = "Not a comments listing";
        try {
            Listing[] listings = mObjectMapper.readValue(in, Listing[].class);

            // listings[0] is a thread Listing for the OP.
            // process same as a thread listing more or less

            Assert.assertEquals(Constants.JSON_LISTING, listings[0].getKind(), genericListingError);

            // Save modhash, ignore "after" and "before" which are meaningless in this context (and probably null)
            ListingData threadListingData = listings[0].getData();
            if (StringUtils.isEmpty(threadListingData.getModhash()))
                mSettings.setModhash(null);
            else
                mSettings.setModhash(threadListingData.getModhash());

            if (Constants.LOGGING) Log.d(TAG, "Successfully got OP listing[0]: modhash "+mSettings.getModhash());
            ThingListing threadThingListing = threadListingData.getChildren()[0];
            Assert.assertEquals(Constants.THREAD_KIND, threadThingListing.getKind(), genericListingError);
            // listings[1] is a comment Listing for the comments
            ListingData commentListingData = listings[1].getData();
            if (isInsertingEntireThread()) {
                parseOP(threadThingListing.getData(), commentListingData.getChildren().length == 0 ? null : commentListingData.getChildren()[0].getData());
                insertedCommentIndex = 0;  // we just inserted the OP into position 0
                if (!StringUtils.isEmpty(mJumpToCommentId) && mJumpToCommentContext > 0) {
                    // If viewing context, then the 'first' item will be the context-viewing warning.
                    insertedCommentIndex++;
                }

                // at this point we've started displaying comments, so disable the loading screen
                disableLoadingScreenKeepProgress();
            }
            else {
                insertedCommentIndex = mPositionOffset - 1;  // -1 because we +1 for the first comment
            }

            // Go through the children and get the ThingInfos
            for (ThingListing commentThingListing : commentListingData.getChildren()) {
                // insert the comment and its replies, prefix traversal order
                insertedCommentIndex = insertNestedComment(commentThingListing, 0, insertedCommentIndex + 1);
            }

            mProcessCommentsTask.mergeLowPriorityListToMainList();

        } catch (Exception ex) {
            if (Constants.LOGGING) Log.e(TAG, "parseCommentsJSON", ex);
        }
    }

    private void parseOP(final ThingInfo data, final ThingInfo contextOP) {
        data.setIndent(0);
        data.setClicked(Common.isClicked(mActivity, data.getUrl()));

        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mActivity.mObjectStates.mCommentsList.add(0, data);
                if (mJumpToCommentContext > 0) {
                    // Need to add a fake comment to get it to draw in the comments list properly, without
                    // 'hacking' the adapter to work properly.
                    ThingInfo tempInfo = new ThingInfo();
                    tempInfo.setIsContextPlaceholder(true);
                    // Set the parent ID to allow the user to view the parent thread.
                    tempInfo.setParent_id(contextOP.getParent_id());
                    tempInfo.setId(mJumpToCommentId);
                    mActivity.mObjectStates.mCommentsList.add(1, tempInfo);
                }

                // Archived should always take precedence over locked, since archived is more restrictive than locked
                // (You can still vote when a post is locked.)
                if (data.isArchived()) {
                    ThingInfo tempInfo = new ThingInfo();
                    tempInfo.setIsArchivedPlaceholder(true);
                    mActivity.mObjectStates.mCommentsList.add(1, tempInfo);
                } else if (data.isLocked()) {
                    // Need to add a fake comment to get it to draw in the comments list properly, without
                    // 'hacking' the adapter to work properly.
                    ThingInfo tempInfo = new ThingInfo();
                    tempInfo.setIsLockedPlaceholder(true);
                    mActivity.mObjectStates.mCommentsList.add(1, tempInfo);
                }
            }
        });

        if (data.isIs_self() && data.getSelftext_html() != null) {
            // HTML to Spanned
            String unescapedHtmlSelftext = Html.fromHtml(data.getSelftext_html()).toString();
            Spanned selftext = Html.fromHtml(Util.convertHtmlTags(unescapedHtmlSelftext));

            // remove last 2 newline characters
            if (selftext.length() > 2)
                data.setSpannedSelftext(selftext.subSequence(0, selftext.length()-2));
            else
                data.setSpannedSelftext("");
        }

        // We might not have a title if we've intercepted a plain link to a thread.
        mThreadTitle = data.getTitle();
        mActivity.setThreadTitle(mThreadTitle);
        mSubreddit = data.getSubreddit();
        mThreadId = data.getId();

        mOpThingInfo = data;
    }

    /**
     * Recursive method to insert comment tree into the mCommentsList,
     * with proper list order and indentation
     */
    int insertNestedComment(ThingListing commentThingListing, int indentLevel, int insertedCommentIndex) {
        ThingInfo ci = commentThingListing.getData();
        // First test for moderator distinguished
        if (Constants.DISTINGUISHED_MODERATOR.equalsIgnoreCase(ci.getDistinguished())) {
            SpannableString distSS = new SpannableString(ci.getAuthor() + " [M]");
            distSS.setSpan(Util.getModeratorSpan(mActivity.getApplicationContext()), 0, distSS.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ci.setSSAuthor(distSS);
        } else if (Constants.DISTINGUISHED_ADMIN.equalsIgnoreCase(ci.getDistinguished())) {
            SpannableString distSS = new SpannableString(ci.getAuthor() + " [A]");
            distSS.setSpan(Util.getAdminSpan(mActivity.getApplicationContext()), 0, distSS.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ci.setSSAuthor(distSS);
        } else if (mOpThingInfo != null && mOpThingInfo.getAuthor().equalsIgnoreCase(ci.getAuthor())) {
            if (m_OPSpan == null) {
                m_OPSpan = new SpannableString(mOpThingInfo.getAuthor() + " [S]");
                m_OPSpan.setSpan(Util.getOPSpan(mActivity.getApplicationContext(), mSettings.getTheme()), 0, m_OPSpan.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            ci.setSSAuthor(m_OPSpan);
        }
        // Add comment to deferred append/replace list
        if (isInsertingEntireThread())
            deferCommentAppend(ci);
        else
            deferCommentReplacement(ci);

        // Keep track of jump target
        if (isHasJumpTarget()) {
            if (!isFoundJumpTargetComment() && mJumpToCommentId.equals(ci.getId()))
                processJumpTarget(ci, insertedCommentIndex);
        }

        if (isHasJumpTarget()) {
            // if we have found the jump target, then we did the messy stuff already. just append to main processing list.
            if (isFoundJumpTargetComment()) {
                mProcessCommentsTask.addDeferred(new DeferredCommentProcessing(ci, insertedCommentIndex));
            }
            // try to handle the context search, if we want context
            else if (mJumpToCommentContext > 0) {
                // any comment could be in the context; we don't know yet. so append to the high-priority "context" list
                mProcessCommentsTask.addDeferredHighPriority(new DeferredCommentProcessing(ci, insertedCommentIndex));

                // we push overflow onto the low priority list, since overflow will end up above the jump target, off the top of the screen.
                // TODO don't use LinkedList.size()
                mProcessCommentsTask.moveHighPriorityOverflowToLowPriority(mJumpToCommentContext);
            }
            // if no context search, then push comments to low priority list until we find the jump target comment
            else {
                mProcessCommentsTask.addDeferredLowPriority(new DeferredCommentProcessing(ci, insertedCommentIndex));
            }
        }
        // if there is no jump target, there's just a single deferred-processing list to worry about.
        else {
            mProcessCommentsTask.addDeferred(new DeferredCommentProcessing(ci, insertedCommentIndex));
        }

        // Formatting that applies to all items, both real comments and "more" entries
        ci.setIndent(mIndentation + indentLevel);

        // Handle "more" entry
        if (Constants.MORE_KIND.equals(commentThingListing.getKind())) {
            if(!Constants.CONTINUE_THIS_THREAD_KIND_ID.equals(commentThingListing.getData().getId())) {
                ci.setLoadMoreCommentsPlaceholder(true);
                if (Constants.LOGGING) Log.v(TAG, "new more position at " + (insertedCommentIndex));
            } else {
                ci.setIsContinueThisThreadPlaceholder(true);
                ci.setId(ci.getParent_id().replace(Constants.COMMENT_KIND+"_", ""));
                if (Constants.LOGGING) Log.v(TAG, "new continue this thread position at " + (insertedCommentIndex));
            }

            return insertedCommentIndex;
        }

        // Regular comment

        // Skip things that are not comments, which shouldn't happen
        if (!Constants.COMMENT_KIND.equals(commentThingListing.getKind())) {
            if (Constants.LOGGING) Log.e(TAG, "comment whose kind is \""+commentThingListing.getKind()+"\" (expected "+Constants.COMMENT_KIND+")");
            return insertedCommentIndex;
        }

        // handle the replies
        Listing repliesListing = ci.getReplies();
        if (repliesListing == null)
            return insertedCommentIndex;
        ListingData repliesListingData = repliesListing.getData();
        if (repliesListingData == null)
            return insertedCommentIndex;
        ThingListing[] replyThingListings = repliesListingData.getChildren();
        if (replyThingListings == null)
            return insertedCommentIndex;

        for (ThingListing replyThingListing : replyThingListings) {
            insertedCommentIndex = insertNestedComment(replyThingListing, indentLevel + 1, insertedCommentIndex + 1);
        }
        return insertedCommentIndex;
    }

    private boolean isHasJumpTarget() {
        return ! StringUtils.isEmpty(mJumpToCommentId);
    }

    private boolean isFoundJumpTargetComment() {
        return mJumpToCommentFoundIndex != -1;
    }

    private void processJumpTarget(ThingInfo comment, int commentIndex) {
        mJumpToCommentFoundIndex = (commentIndex - mJumpToCommentContext) > 0 ? (commentIndex - mJumpToCommentContext) : 0;
        mProcessCommentsTask.mergeHighPriorityListToMainList();
    }

    /**
     * Call from UI Thread
     */
    private void insertCommentsUI() {
        mActivity.mObjectStates.mCommentsList.addAll(mDeferredAppendList);
        mActivity.mCommentsAdapter.notifyDataSetChanged();
    }

    /**
     * Process the slow steps and refresh each new comment
     */
    private void processDeferredComments() {
        mProcessCommentsTask.withPositionTo(mThingInfoIdToPositionTo);
        mProcessCommentsTask.execute();
    }

    private void showOPThumbnail() {
    }

    @Override
    public void onPreExecute() {
        if (mThreadId == null) {
            if (Constants.LOGGING) Log.e(TAG, "mSettings.threadId == null");
            this.cancel(true);
            return;
        }
        synchronized (mCurrentDownloadCommentsTaskLock) {
            if (mCurrentDownloadCommentsTask != null) {
                this.cancel(true);
                return;
            }
            mCurrentDownloadCommentsTask = this;
        }

        if (isInsertingEntireThread()) {
            if (mActivity.mCommentsAdapter != null)
                mActivity.mCommentsAdapter.clear();
            else
                mActivity.resetUI(null);

            // Do loading screen when loading new thread; otherwise when "loading more comments" don't show it
            mActivity.enableLoadingScreen();
        }

        if (mContentLength == -1)
            mActivity.getWindow().setFeatureInt(Window.FEATURE_PROGRESS, Window.PROGRESS_INDETERMINATE_ON);

        if (mThreadTitle != null)
            mActivity.setTitle(mThreadTitle + " : " + mSubreddit);
    }

    @Override
    public void onPostExecute(Boolean success) {
        if (isInsertingEntireThread()) {
            insertCommentsUI();
            if (isFoundJumpTargetComment())
                mActivity.getListView().setSelection(mJumpToCommentFoundIndex);
        }
        else if (!mDeferredReplacementList.isEmpty()) {
            replaceCommentsAtPositionUI(mDeferredReplacementList, mPositionOffset);
        }

        // have to wait till onPostExecute to do this, to ensure they've been inserted by UI thread
        processDeferredComments();

        if (Common.shouldLoadThumbnails(mActivity, mSettings))
            showOPThumbnail();

        if (mContentLength == -1)
            mActivity.getWindow().setFeatureInt(Window.FEATURE_PROGRESS, Window.PROGRESS_INDETERMINATE_OFF);
        else
            mActivity.getWindow().setFeatureInt(Window.FEATURE_PROGRESS, Window.PROGRESS_END);

        if (success) {
            // We should clear any replies the user was composing.
            mActivity.setShouldClearReply(true);

            // Set title in android titlebar
            if (mThreadTitle != null)
                mActivity.setTitle(mThreadTitle + " : " + mSubreddit);
        } else {
            if (!isCancelled()) {
                Common.showErrorToast("Error downloading comments. Please try again.", Toast.LENGTH_LONG, mActivity);
                mActivity.resetUI(null);
            }
        }

        synchronized (mCurrentDownloadCommentsTaskLock) {
            mCurrentDownloadCommentsTask = null;
        }
    }

    @Override
    public void onProgressUpdate(Long... progress) {
        if (mContentLength == -1)
            mActivity.getWindow().setFeatureInt(Window.FEATURE_PROGRESS, Window.PROGRESS_INDETERMINATE_ON);
        else
            mActivity.getWindow().setFeatureInt(Window.FEATURE_PROGRESS, progress[0].intValue() * (Window.PROGRESS_END-1) / (int) mContentLength);
    }

    @Override
    public void propertyChange(PropertyChangeEvent event) {
        publishProgress((Long) event.getNewValue());
    }
    void detach() {
        if (Constants.LOGGING) Log.d(TAG, "DownloadCommentsTask: Activity detached.");
        mActivity=null;
    }

    void attach(CommentsListActivity activity) {
        if (Constants.LOGGING) Log.d(TAG, "DownloadCommentsTask: Activity attached.");
        mActivity=activity;
    }
}

