package in.shick.diode.comments;

import in.shick.diode.R;
import in.shick.diode.common.Common;
import in.shick.diode.common.Constants;
import in.shick.diode.common.RedditIsFunHttpClientFactory;
import in.shick.diode.common.util.Util;
import in.shick.diode.mail.PeekEnvelopeTask;
import in.shick.diode.markdown.Markdown;
import in.shick.diode.markdown.MarkdownURL;
import in.shick.diode.saved.SavedContent;
import in.shick.diode.saved.SavedDBHandler;
import in.shick.diode.settings.RedditPreferencesPage;
import in.shick.diode.settings.RedditSettings;
import in.shick.diode.things.ThingInfo;
import in.shick.diode.user.ProfileActivity;

import java.util.ArrayList;
import java.util.List;

import org.apache.http.client.HttpClient;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.telephony.PhoneNumberUtils;
import android.text.Html;
import android.text.Spanned;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.CookieSyncManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

/**
 * SavedCommentsActivity.java
 * @author John Zavidniak
 * This class manages everything related to the viewing and
 * manipulation of saved comments.
 */

public class SavedCommentsActivity extends Activity
{

    private class Adapter extends ArrayAdapter<SavedContent>
    {

        private final Context context;
        private final List<SavedContent> data;
        private final int rowResourceId;

        public Adapter(Context context, int textViewResourceId,
                       List<SavedContent> data)
        {
            super(context, textViewResourceId, data);
            this.context = context;
            this.data = data;
            this.rowResourceId = textViewResourceId;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent)
        {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View rowView = inflater.inflate(rowResourceId, null);

            TextView author = (TextView) rowView.findViewById(R.id.submitter);
            TextView body = (TextView) rowView.findViewById(R.id.body);

            SavedContent sc = data.get(position);

            author.setText(sc.getAuthor());
            body.setText(createSpanned(sc.getBody()));

            return rowView;
        }

    }

    private static final String TAG = "SavedCommentsActivity";

    private final HttpClient mClient = RedditIsFunHttpClientFactory.getGzipHttpClient();

    private final RedditSettings mSettings = new RedditSettings();

    private List<SavedContent> savedContent;

    private SavedDBHandler sdbh;

    private SavedContent currentSavedContent;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        CookieSyncManager.createInstance(getApplicationContext());
        mSettings.loadRedditPreferences(this, mClient);

        setRequestedOrientation(mSettings.getRotation());
        setTheme(mSettings.getTheme());
        requestWindowFeature(Window.FEATURE_PROGRESS);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        setContentView(R.layout.saved_comments);

        sdbh = new SavedDBHandler(this);
        savedContent = sdbh.getSavedContent(mSettings.getUsername());

        Adapter lAdapter = new Adapter(this, R.layout.saved_comments_item, savedContent);

        ListView listview = (ListView) findViewById(R.id.savedcommentslv);

        listview.setAdapter(lAdapter);

        listview.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position,
                                    long id) {
                currentSavedContent = savedContent.get(position);
                showDialog(Constants.DIALOG_SAVED_COMMENTS);
            }

        });
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        CookieSyncManager.getInstance().stopSync();
        mSettings.saveRedditPreferences(this);
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        int prevTheme = mSettings.getTheme();
        mSettings.loadRedditPreferences(this, mClient);

        if (mSettings.getTheme() != prevTheme)
        {
            relaunchActivity();
        }
        else
        {
            CookieSyncManager.getInstance().startSync();
            setRequestedOrientation(mSettings.getRotation());

            if (mSettings.isLoggedIn())
                new PeekEnvelopeTask(this, mClient, mSettings.getMailNotificationStyle()).execute();
        }
    }

    private void relaunchActivity()
    {
        finish();
        startActivity(getIntent());
    }

    /**
     * Populates the menu.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        super.onCreateOptionsMenu(menu);

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.saved_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu)
    {
        super.onPrepareOptionsMenu(menu);

        if (mSettings.isLoggedIn())
        {
            menu.findItem(R.id.user_profile_menu_id).setVisible(true);
            menu.findItem(R.id.user_profile_menu_id).setTitle(
                String.format(getResources().getString(R.string.user_profile), mSettings.getUsername()));
        }
        else
        {
            menu.findItem(R.id.user_profile_menu_id).setVisible(false);
        }

        MenuItem src, dest;
        src = Util.isLightTheme(mSettings.getTheme()) ? menu.findItem(R.id.dark_menu_id) :
              menu.findItem(R.id.light_menu_id);
        dest = menu.findItem(R.id.light_dark_menu_id);
        dest.setTitle(src.getTitle());
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId())
        {
        case R.id.user_profile_menu_id:
            Intent profileIntent = new Intent(getApplicationContext(), ProfileActivity.class);
            startActivity(profileIntent);
            break;
        case R.id.preferences_menu_id:
            Intent prefsIntent = new Intent(getApplicationContext(), RedditPreferencesPage.class);
            startActivity(prefsIntent);
            break;
        case R.id.light_dark_menu_id:
            mSettings.setTheme(Util.getInvertedTheme(mSettings.getTheme()));
            relaunchActivity();
            break;
        case android.R.id.home:
            Common.goHome(this);
            break;
        default:
            throw new IllegalArgumentException("Unexpected action value "+item.getItemId());
        }

        return true;
    }

    @Override
    protected Dialog onCreateDialog(int id)
    {
        final Dialog dialog;

        switch (id)
        {
        case Constants.DIALOG_SAVED_COMMENTS:
            dialog = new SavedCommentsDialog(this, mSettings);
            break;
        default:
            throw new IllegalArgumentException("Unexpected dialog id "+id);
        }

        return dialog;
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog)
    {
        super.onPrepareDialog(id, dialog);

        switch (id)
        {
        case Constants.DIALOG_SAVED_COMMENTS:
            final TextView author = (TextView) dialog.findViewById(R.id.submitter);
            final TextView body = (TextView) dialog.findViewById(R.id.body);

            author.setText(currentSavedContent.getAuthor());

            StringBuilder bodyText = new StringBuilder();
            String[] pieces = currentSavedContent.getBody().split("[\\r\\n]+");
            int charCount = 0;
            for (int i = 0; i < pieces.length && i < 9; i++)
            {
                if (charCount > 200)
                {
                    break;
                }
                bodyText.append(pieces[i]);
                charCount += pieces[i].length();
            }
            body.setText(createSpanned(bodyText.toString().trim()));

            final Button viewComment = (Button) dialog.findViewById(R.id.view_saved_comment);
            final Button unsaveComment = (Button) dialog.findViewById(R.id.unsave_comment);
            final Button linkButton = (Button) dialog.findViewById(R.id.thread_link_button);
            viewComment.setEnabled(true);
            unsaveComment.setEnabled(true);

            ThingInfo ti = new ThingInfo();
            if (ti.getUrls() != null && ti.getUrls().isEmpty()) {
                Markdown.getURLs(currentSavedContent.getBody(), ti.getUrls());
            }
            linkToEmbeddedURLs(linkButton, ti.getUrls());

            unsaveComment.setOnClickListener(new OnClickListener()
            {

                @Override
                public void onClick(View v)
                {
                    sdbh.deleteSavedContent(currentSavedContent);
                    removeDialog(Constants.DIALOG_SAVED_COMMENTS);
                    relaunchActivity();
                }

            });

            viewComment.setOnClickListener(new OnClickListener()
            {

                @Override
                public void onClick(View v)
                {
                    ThingInfo commentThing = new ThingInfo();
                    commentThing.setLink_id(currentSavedContent.getLinkId());
                    commentThing.setId(currentSavedContent.getCommentId());

                    removeDialog(Constants.DIALOG_SAVED_COMMENTS);

                    Intent i = new Intent(SavedCommentsActivity.this, CommentsListActivity.class);
                    i.setData(Util.createCommentUriNoContext(commentThing));
                    i.putExtra(Constants.EXTRA_SUBREDDIT, currentSavedContent.getSubreddit());
                    startActivity(i);
                }

            });
            break;
        default:
            throw new IllegalArgumentException("Unexpected dialog id "+id);
        }
    }

    /**
     * @param bodyHtml escaped HTML (like in reddit Thing's body_html)
     */
    private CharSequence createSpanned(String bodyHtml)
    {
        try
        {
            // get unescaped HTML
            bodyHtml = Html.fromHtml(bodyHtml).toString();
            // fromHtml doesn't support all HTML tags. convert <code> and <pre>
            bodyHtml = Util.convertHtmlTags(bodyHtml);

            Spanned body = Html.fromHtml(bodyHtml);
            // remove last 2 newline character
            if (body.length() > 2)
                return body.subSequence(0, body.length()-2);
            else
                return "";
        }
        catch (Exception e)
        {
            if (Constants.LOGGING) Log.e(TAG, "createSpanned failed", e);
            return null;
        }
    }

    private void linkToEmbeddedURLs(Button linkButton, final ArrayList<MarkdownURL> vtUrls)
    {
        final ArrayList<String> urls = new ArrayList<String>();
        int urlsCount = vtUrls.size();
        String url;
        for (int i = 0; i < urlsCount; i++)
        {
            if (urls.contains((url = vtUrls.get(i).url)))
            {
                continue;
            }
            urls.add(url);
        }
        if (urlsCount == 0)
        {
            linkButton.setEnabled(false);
        }
        else
        {
            linkButton.setEnabled(true);
            linkButton.setOnClickListener(new OnClickListener()
            {
                public void onClick(View v)
                {
                    removeDialog(Constants.DIALOG_SAVED_COMMENTS);

                    ArrayAdapter<MarkdownURL> adapter =
                        new ArrayAdapter<MarkdownURL>(SavedCommentsActivity.this,
                                                      android.R.layout.select_dialog_item, vtUrls)
                    {
                        public View getView(int position, View convertView, ViewGroup parent)
                        {
                            TextView tv;
                            if (convertView == null)
                            {
                                tv = (TextView) ((LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE))
                                     .inflate(android.R.layout.select_dialog_item, null);
                            }
                            else
                            {
                                tv = (TextView) convertView;
                            }

                            String url = getItem(position).url;
                            String anchorText = getItem(position).anchorText;
                            if (Constants.LOGGING) Log.d(TAG, "links url="+url + " anchorText="+anchorText);

                            Drawable d = null;
                            try
                            {
                                d = getPackageManager().getActivityIcon(new Intent(Intent.ACTION_VIEW,
                                                                        Uri.parse(url)));
                            } catch (NameNotFoundException ignore)
                            {
                            }
                            if (d != null)
                            {
                                d.setBounds(0, 0, d.getIntrinsicHeight(), d.getIntrinsicHeight());
                                tv.setCompoundDrawablePadding(10);
                                tv.setCompoundDrawables(d, null, null, null);
                            }

                            final String telPrefix = "tel:";
                            if (url.startsWith(telPrefix))
                            {
                                url = PhoneNumberUtils.formatNumber(url.substring(telPrefix.length()));
                            }

                            if (anchorText != null)
                                tv.setText(Html.fromHtml("<span>" + anchorText + "</span><br /><small>" + url +
                                                         "</small>"));
                            else
                                tv.setText(Html.fromHtml(url));

                            return tv;
                        }
                    };

                    AlertDialog.Builder b = new AlertDialog.Builder(new ContextThemeWrapper(
                                SavedCommentsActivity.this, mSettings.getDialogTheme()));

                    DialogInterface.OnClickListener click = new DialogInterface.OnClickListener()
                    {
                        public final void onClick(DialogInterface dialog, int which)
                        {
                            if (which >= 0)
                            {
                                Common.launchBrowser(SavedCommentsActivity.this, urls.get(which),
                                                     null, false, false, mSettings.isUseExternalBrowser(),
                                                     mSettings.isSaveHistory());
                            }
                        }
                    };

                    b.setTitle(R.string.select_link_title);
                    b.setCancelable(true);
                    b.setAdapter(adapter, click);

                    b.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener()
                    {
                        public final void onClick(DialogInterface dialog, int which)
                        {
                            dialog.dismiss();
                        }
                    });

                    b.show();
                }
            });
        }
    }

}