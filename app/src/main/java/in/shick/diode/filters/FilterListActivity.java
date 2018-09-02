package in.shick.diode.filters;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import in.shick.diode.R;
import in.shick.diode.settings.RedditSettings;
import java.util.ArrayList;

/**
 * Activity for configuring filters
 * @author tordo
 *
 */
public class FilterListActivity extends Activity
{
    /** The display for the list of filters */
    ListView m_filterListView;
    /** The filters we are displaying */
    ArrayList<SubredditFilter> m_filters;
    /** The adapter showing our filters */
    FilterAdapter m_adapter;
    /** Reddit settings*/
    RedditSettings m_settings;
    /** Constant indiciating dialog to confirm deletion of all filters */
    public static final int DIALOG_CLEAR = 0;

    @Override
    public void onCreate(Bundle b)
    {
        super.onCreate(b);
        setContentView(R.layout.filter_list_activity);

        m_filterListView = (ListView) findViewById(R.id.filter_list);

        ((Button) findViewById(R.id.add_button)).setOnClickListener(
        new Button.OnClickListener() {
            public void onClick(View view) {
                Intent i = new Intent(FilterListActivity.this,
                                      FilterEditActivity.class);
                startActivity(i);
            }
        });

        m_settings = new RedditSettings();
        m_settings.loadRedditPreferences(this,null);
    }

    @Override
    public void onPause()
    {
        // Save filters on pause
        m_settings.setFilters(m_filters);
        m_settings.saveRedditPreferences(this);
        super.onPause();
    }

    @Override
    public void onResume()
    {
        // Re-load filters on resume (i.e. after editing)
        m_settings.loadRedditPreferences(this,null);
        m_filters = m_settings.getFilters();
        m_adapter = new FilterAdapter(this,m_filters);
        m_filterListView.setAdapter(m_adapter);

        super.onResume();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        MenuInflater m = getMenuInflater();
        m.inflate(R.menu.filter_options_menu,menu);

        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch(item.getItemId())
        {
        case R.id.clear:
            showDialog(DIALOG_CLEAR);
            return true;
        default:
            return false;
        }
    }

    @Override
    protected Dialog onCreateDialog(int id)
    {
        Dialog dialog;
        switch(id)
        {
        case DIALOG_CLEAR:
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getString(R.string.filter_clear_confirm_title))
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setCancelable(true)
            .setPositiveButton(getString(R.string.filter_clear_confirm_yes),
            new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    m_filters.clear();
                    m_adapter.notifyDataSetChanged();
                }
            })
            .setNegativeButton(getString(R.string.filter_clear_confirm_no),
            new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    dialog.cancel();
                }
            });

            dialog = builder.create();

            break;
        default:
            dialog = null;
        }
        return dialog;
    }

}
