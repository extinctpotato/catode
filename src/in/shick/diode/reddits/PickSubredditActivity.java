/*
 * Copyright 2009 Andrew Shu
 *
 * This file is part of "diode".
 *
 * "diode" is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * "diode" is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with "diode".  If not, see <http://www.gnu.org/licenses/>.
 */

package in.shick.diode.reddits;

import java.net.URL;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.CookieSyncManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;

import in.shick.diode.R;
import in.shick.diode.common.CacheInfo;
import in.shick.diode.common.Common;
import in.shick.diode.common.Constants;
import in.shick.diode.common.RedditIsFunHttpClientFactory;
import in.shick.diode.common.util.CollectionUtils;
import in.shick.diode.common.util.Util;
import in.shick.diode.settings.RedditSettings;

public final class PickSubredditActivity extends ListActivity {

    private static final String TAG = "PickSubredditActivity";

    // Group 1: inner
    private final Pattern MY_SUBREDDITS_OUTER = Pattern.compile("YOUR FRONT PAGE SUBREDDITS.*?<ul>(.*?)</ul>", Pattern.CASE_INSENSITIVE);
    // Group 3: subreddit name. Repeat the matcher.find() until it fails.
    private final Pattern MY_SUBREDDITS_INNER = Pattern.compile("<a(.*?)/r/(.*?)>(.+?)</a>");

    private boolean refresh = true;
    private RedditSettings mSettings = new RedditSettings();
    private HttpClient mClient = RedditIsFunHttpClientFactory.getGzipHttpClient();

    private PickSubredditAdapter mSubredditsAdapter;
    private ArrayList<SubredditInfo> mSubredditsList;
    private static final Object ADAPTER_LOCK = new Object();
    private EditText mEt;

    private AsyncTask<?, ?, ?> mCurrentTask = null;
    private final Object mCurrentTaskLock = new Object();

    public static final String[] DEFAULT_SUBREDDITS = {
        Constants.FRONTPAGE_STRING,
        "all",
        "diode",
        "pics",
        "funny",
        "politics",
        "gaming",
        "askreddit",
        "worldnews",
        "videos",
        "iama",
        "todayilearned",
        "wtf",
        "aww",
        "technology",
        "science",
        "music",
        "askscience",
        "movies",
        "bestof",
        "fffffffuuuuuuuuuuuu",
        "programming",
        "comics",
        "offbeat",
        "environment",
        "business",
        "entertainment",
        "economics",
        "trees",
        "linux",
        "android"
    };

    // A list of special subreddits that can be viewed, but cannot be used for submissions. They inherit from the FakeSubreddit class
    // in the redditdev source, so we use the same naming here. Note: Should we add r/Random and r/Friends?
    public static final String[] FAKE_SUBREDDITS = {
        Constants.FRONTPAGE_STRING,
        "all"
    };



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSubredditsList = new ArrayList<SubredditInfo>();

        CookieSyncManager.createInstance(getApplicationContext());

        mSettings.loadRedditPreferences(this, mClient);
        setRequestedOrientation(mSettings.getRotation());
        requestWindowFeature(Window.FEATURE_PROGRESS);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        setTheme(mSettings.getTheme());
        setContentView(R.layout.pick_subreddit_view);
        registerForContextMenu(getListView());

        mSubredditsList = getCachedSubredditsList();

        if (CollectionUtils.isEmpty(mSubredditsList))
            restoreLastNonConfigurationInstance();

        if (CollectionUtils.isEmpty(mSubredditsList)) {
            new DownloadRedditsTask().execute();
        }
        else {
            resetUI(new PickSubredditAdapter(this, mSubredditsList));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        CookieSyncManager.getInstance().startSync();
    }

    @Override
    public void onPause() {
        super.onPause();
        CookieSyncManager.getInstance().stopSync();
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        // Avoid having to re-download and re-parse the subreddits list
        // when rotating or opening keyboard.
        return mSubredditsList;
    }

    @SuppressWarnings("unchecked")
    private void restoreLastNonConfigurationInstance() {
        mSubredditsList = (ArrayList<SubredditInfo>) getLastNonConfigurationInstance();
    }

    void resetUI(PickSubredditAdapter adapter) {
        findViewById(R.id.loading_light).setVisibility(View.GONE);
        findViewById(R.id.loading_dark).setVisibility(View.GONE);

        synchronized (ADAPTER_LOCK) {
            if (adapter == null) {
                // Reset the list to be empty.
                mSubredditsList = new ArrayList<SubredditInfo>();
                mSubredditsAdapter = new PickSubredditAdapter(this, mSubredditsList);
            } else {
                mSubredditsAdapter = adapter;
            }
            setListAdapter(mSubredditsAdapter);
            mSubredditsAdapter.mLoading = false;
            mSubredditsAdapter.notifyDataSetChanged();  // Just in case
        }
        Common.updateListDrawables(this, mSettings.getTheme());

        // Set the EditText to do same thing as onListItemClick
        mEt = (EditText) findViewById(R.id.pick_subreddit_input);
        if (mEt != null) {
            mEt.setOnKeyListener(new OnKeyListener() {
                public boolean onKey(View v, int keyCode, KeyEvent event) {
                    if ((event.getAction() == KeyEvent.ACTION_DOWN) && (keyCode == KeyEvent.KEYCODE_ENTER)) {
                        returnSubreddit(mEt.getText().toString().trim());
                        return true;
                    }
                    return false;
                }
            });
            mEt.setFocusableInTouchMode(true);
        }
        Button goButton = (Button) findViewById(R.id.pick_subreddit_button);
        if (goButton != null) {
            goButton.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    returnSubreddit(mEt.getText().toString().trim());
                }
            });
        }

        getListView().requestFocus();
    }


    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        SubredditInfo item = mSubredditsAdapter.getItem(position);
        returnSubreddit(item.name);
    }

    private void returnSubreddit(String subreddit) {
        Intent intent = new Intent();
        intent.setData(Util.createSubredditUri(subreddit.toLowerCase()));
        setResult(RESULT_OK, intent);
        finish();
    }

    private void enableLoadingScreen() {
        if (Util.isLightTheme(mSettings.getTheme())) {
            findViewById(R.id.loading_light).setVisibility(View.VISIBLE);
            findViewById(R.id.loading_dark).setVisibility(View.GONE);
        } else {
            findViewById(R.id.loading_light).setVisibility(View.GONE);
            findViewById(R.id.loading_dark).setVisibility(View.VISIBLE);
        }
        synchronized (ADAPTER_LOCK) {
            if (mSubredditsAdapter != null)
                mSubredditsAdapter.mLoading = true;
        }
        getWindow().setFeatureInt(Window.FEATURE_PROGRESS, Window.PROGRESS_START);
    }

    private void disableLoadingScreen() {
        findViewById(R.id.loading_dark).setVisibility(View.GONE);
        findViewById(R.id.loading_light).setVisibility(View.GONE);
        getWindow().setFeatureInt(Window.FEATURE_PROGRESS, Window.PROGRESS_END);
    }

    class DownloadRedditsTask extends AsyncTask<Void, Void, ArrayList<SubredditInfo>> {
        @Override
        public ArrayList<SubredditInfo> doInBackground(Void... voidz) {
            HttpEntity entity = null;
            try {

                ArrayList<SubredditInfo> reddits = null;
                if(refresh) {

                    HttpGet request = new HttpGet(Constants.REDDIT_BASE_URL + "/subreddits/mine/subscriber.json?limit=100");
                    // Set timeout to 15 seconds
                    HttpParams params = request.getParams();
                    HttpConnectionParams.setConnectionTimeout(params, 15000);
                    HttpConnectionParams.setSoTimeout(params, 15000);

                    HttpResponse response = mClient.execute(request);
                    entity = response.getEntity();
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode rootNode = mapper.readValue(entity.getContent(), JsonNode.class);
                    entity.consumeContent();

                    reddits = new ArrayList<SubredditInfo>();
                    for(JsonNode ee : rootNode.get("data").get("children")) {
                        ee = ee.get("data");
                        SubredditInfo sr = new SubredditInfo();
                        sr.name = ee.get("display_name").getTextValue();
                        sr.description = ee.get("title").getTextValue();
                        sr.nsfw = ee.get("over18").getBooleanValue();
                        sr.subscribers = ee.get("subscribers").getIntValue();
                        sr.url = new URL(Constants.REDDIT_BASE_URL + ee.get("url").getTextValue());
                        sr.created = new Date((long) ee.get("created").getIntValue() * 1000);
                        reddits.add(sr);
                    }
                    Collections.sort(reddits);
                    // insert the frontpage at the head of the list
                    SubredditInfo fp = new SubredditInfo();
                    fp.name = Constants.FRONTPAGE_STRING;
                    reddits.add(0, fp);
                    // insert /r/all as well (this is a really gross way to do these. . .)
                    fp = new SubredditInfo();
                    fp.name = "all";
                    reddits.add(1, fp);
                    CacheInfo.setCachedSubredditList(getApplicationContext(), reddits);
                    refresh = false;
                } else {
                    reddits = getCachedSubredditsList();
                }
                return reddits;
            }
            catch(Throwable e) {
            }
            return null;
        }

        @Override
        public void onPreExecute() {
            super.onPreExecute();
            synchronized (mCurrentTaskLock) {
                if (mCurrentTask != null) {
                    this.cancel(true);
                    return;
                }
                mCurrentTask = this;
            }
            enableLoadingScreen();
        }

        @Override
        public void onPostExecute(ArrayList<SubredditInfo> reddits) {
            synchronized (mCurrentTaskLock) {
                mCurrentTask = null;
            }
            disableLoadingScreen();

            if (reddits == null || reddits.size() == 0) {
                // Need to make a copy because Arrays.asList returns List backed by original array
                mSubredditsList = new ArrayList<SubredditInfo>();
                for(String ee : DEFAULT_SUBREDDITS) {
                    SubredditInfo info = new SubredditInfo();
                    info.name = ee;
                    mSubredditsList.add(info);
                }
            } else {
                mSubredditsList = reddits;
            }
            //addFakeSubredditsUnlessSuppressed();
            resetUI(new PickSubredditAdapter(PickSubredditActivity.this, mSubredditsList));
            super.onPostExecute(reddits);
        }
    }

    private final class PickSubredditAdapter extends ArrayAdapter<SubredditInfo> {
        private LayoutInflater mInflater;
        private boolean mLoading = true;
        private int mFrequentSeparatorPos = ListView.INVALID_POSITION;
        private NumberFormat mSubscriberFormat;


        public PickSubredditAdapter(Context context, List<SubredditInfo> objects) {
            super(context, 0, objects);

            mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            mSubscriberFormat = NumberFormat.getInstance();
        }

        @Override
        public boolean isEmpty() {
            if (mLoading) {
                // We don't want the empty state to show when loading.
                return false;
            } else {
                return super.isEmpty();
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == mFrequentSeparatorPos) {
                // We don't want the separator view to be recycled.
                return IGNORE_ITEM_VIEW_TYPE;
            }
            return super.getItemViewType(position);
        }


        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view;

            // Here view may be passed in for re-use, or we make a new one.
            if (convertView == null) {
                view = mInflater.inflate(R.layout.subreddit_list_entry, null);
            } else {
                view = convertView;
            }

            SubredditInfo subject = mSubredditsAdapter.getItem(position);

            TextView text = (TextView) view.findViewById(R.id.name);
            text.setText(subject.name);

            text = (TextView) view.findViewById(R.id.age);
            if(subject.created != null)
            {
                text.setText(subject.getAgeString(PickSubredditActivity.this));
            }
            else
            {
                text.setText(null);
            }

            text = (TextView) view.findViewById(R.id.subscribers);
            if(subject.subscribers > 0)
            {
                text.setText(String.format(getString(R.string.subscriber_count_format),
                                           mSubscriberFormat.format(subject.subscribers)));
            }
            else
            {
                text.setText(null);
            }

            text = (TextView) view.findViewById(R.id.nsfw);
            if(subject.nsfw == true) {
                text.setVisibility(View.VISIBLE);
            } else {
                text.setVisibility(View.GONE);
            }

            text = (TextView) view.findViewById(R.id.description);
            text.setText(subject.description);

            return view;
        }
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        Dialog dialog;
        ProgressDialog pdialog;

        switch (id) {
        // "Please wait"
        case Constants.DIALOG_LOADING_REDDITS_LIST:
            pdialog = new ProgressDialog(new ContextThemeWrapper(this, mSettings.getDialogTheme()));
            pdialog.setMessage("Loading your reddits...");
            pdialog.setIndeterminate(true);
            pdialog.setCancelable(true);
            dialog = pdialog;
            break;
        default:
            throw new IllegalArgumentException("Unexpected dialog id "+id);
        }
        return dialog;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home:
            Common.goHome(this);
            break;
        case R.id.refresh_subreddit_list:
            refresh = true;
            new DownloadRedditsTask().execute();
            break;
        case R.id.random_subreddit:
            returnSubreddit("random");
            break;

        default:
            throw new IllegalArgumentException("Unexpected action value "+item.getItemId());
        }
        return true;
    }

    @Override
    protected void onRestoreInstanceState(Bundle state) {
        super.onRestoreInstanceState(state);
        final int[] myDialogs = {
            Constants.DIALOG_LOADING_REDDITS_LIST,
        };
        for (int dialog : myDialogs) {
            try {
                removeDialog(dialog);
            } catch (IllegalArgumentException e) {
                // Ignore.
            }
        }
    }

    /**
     * Populates the menu.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.subreddit_list, menu);
        return true;
    }

    protected ArrayList<SubredditInfo> getCachedSubredditsList() {
        ArrayList<SubredditInfo> reddits = null;
        if (Constants.USE_SUBREDDITS_CACHE) {
            if (CacheInfo.checkFreshSubredditListCache(getApplicationContext())) {
                reddits = CacheInfo.getCachedSubredditList(getApplicationContext());
                if (Constants.LOGGING) Log.d(TAG, "cached subreddit list:" + reddits);
            }
        }
        return reddits;
    }
}
