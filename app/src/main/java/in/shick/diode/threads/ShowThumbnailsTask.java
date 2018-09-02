package in.shick.diode.threads;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.net.MalformedURLException;
import java.util.HashMap;

import android.widget.ProgressBar;
import in.shick.diode.common.Constants;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;

import android.app.ListActivity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import in.shick.diode.R;
import in.shick.diode.common.util.StringUtils;
import in.shick.diode.things.ThingInfo;
import in.shick.diode.threads.ShowThumbnailsTask.ThumbnailLoadAction;

public class ShowThumbnailsTask extends AsyncTask<ThumbnailLoadAction, ThumbnailLoadAction, Void> {

    private final ListActivity mActivity;
    private final HttpClient mClient;
    private final Integer mDefaultThumbnailResource;

    private static final HashMap<String, SoftReference<Bitmap>> cache = new HashMap<String, SoftReference<Bitmap>>();

    private static final String TAG = "ShowThumbnailsTask";

    public ShowThumbnailsTask(ListActivity activity, HttpClient client, Integer defaultThumbnailResource) {
        this.mActivity = activity;
        this.mClient = client;
        this.mDefaultThumbnailResource = defaultThumbnailResource;
    }

    public static class ThumbnailLoadAction {
        public final ThingInfo thingInfo;
        public final ImageView imageView;  // prefer imageView; if it's null, use threadIndex
        public final int threadIndex;
        public ProgressBar loadBar;
        public ThumbnailLoadAction(ThingInfo thingInfo, ImageView imageView, int threadIndex) {
            this.thingInfo = thingInfo;
            this.imageView = imageView;
            this.threadIndex = threadIndex;
        }
        public ThumbnailLoadAction(ThingInfo thingInfo, ImageView imageView, int threadIndex, ProgressBar progressBar) {
            this(thingInfo, imageView, threadIndex);
            this.loadBar = progressBar;
        }
    }

    @Override
    protected Void doInBackground(ThumbnailLoadAction... thumbnailLoadActions) {
        for (ThumbnailLoadAction thumbnailLoadAction : thumbnailLoadActions) {
            loadThumbnail(thumbnailLoadAction.thingInfo);
            publishProgress(thumbnailLoadAction);
        }
        return null;
    }

    // TODO use external storage cache if present
    private void loadThumbnail(ThingInfo thingInfo) {
        if (Constants.NSFW_STRING.equalsIgnoreCase(thingInfo.getThumbnail()) || Constants.DEFAULT_STRING.equals(thingInfo.getThumbnail()) || Constants.SUBMIT_KIND_SELF.equals(thingInfo.getThumbnail()) || StringUtils.isEmpty(thingInfo.getThumbnail())) {
            thingInfo.setThumbnailResource(mDefaultThumbnailResource);
        }
        else {
            SoftReference<Bitmap> ref;
            Bitmap bitmap;

            ref = cache.get(thingInfo.getThumbnail());
            if (ref != null) {
                bitmap = ref.get();
                if (bitmap != null) {
                    thingInfo.setThumbnailBitmap(bitmap);
                    return;
                }
            }

            bitmap = readBitmapFromNetwork(thingInfo.getThumbnail());

            ref = new SoftReference<Bitmap>(bitmap);
            cache.put(thingInfo.getThumbnail(), ref);
            thingInfo.setThumbnailBitmap(ref.get());
        }
    }

    private InputStream fetch(String urlString) throws MalformedURLException, IOException {
        HttpGet request = new HttpGet(urlString);
        HttpResponse response = mClient.execute(request);
        return response.getEntity().getContent();
    }

    private Bitmap readBitmapFromNetwork( String url ) {
        if (url == null || Constants.NSFW_STRING.equalsIgnoreCase(url))
            return null;

        InputStream is = null;
        BufferedInputStream bis = null;
        Bitmap bmp = null;
        try {
            // http://blog.donnfelker.com/2010/04/29/android-odd-error-in-defaulthttpclient/
            if (!url.startsWith("http://") && !url.startsWith("https://"))
                url = "http://" + url;

            is = fetch(url);
            bis = new BufferedInputStream(is);
            bmp = BitmapFactory.decodeStream(bis);
        } catch (MalformedURLException e) {
            Log.e(TAG, "Bad ad URL", e);
        } catch (IOException e) {
            Log.e(TAG, "Could not get remote ad image: " + url, e);
        } finally {
            try {
                if( is != null )
                    is.close();
                if( bis != null )
                    bis.close();
            } catch (IOException e) {
                Log.w(TAG, "Error closing stream.");
            }
        }
        return bmp;
    }

    @Override
    protected void onProgressUpdate(ThumbnailLoadAction... thumbnailLoadActions) {
        for (ThumbnailLoadAction thumbnailLoadAction : thumbnailLoadActions)
            refreshThumbnailUI(thumbnailLoadAction);
    }

    private void refreshThumbnailUI(ThumbnailLoadAction thumbnailLoadAction) {
        ImageView imageView = null;
        ProgressBar progressBar = thumbnailLoadAction.loadBar;
        if (thumbnailLoadAction.imageView != null) {
            imageView = thumbnailLoadAction.imageView;
        }
        else {
            if (isCurrentlyOnScreenUI(thumbnailLoadAction.threadIndex)) {
                int positionOnScreen = thumbnailLoadAction.threadIndex - mActivity.getListView().getFirstVisiblePosition();
                View v = mActivity.getListView().getChildAt(positionOnScreen);
                if (v != null) {
                    View thumbnailImageView = v.findViewById(R.id.thumbnail);
                    if (thumbnailImageView != null) {
                        imageView = (ImageView) thumbnailImageView;
                    }
                }
            }
        }
        ThingInfo thingInfo = thumbnailLoadAction.thingInfo;
        // The ImageView element will have its tag set to the id that it is currently displaying.
        // When the app reaches this code, the indeterminate progress bar should be visible, and will
        // need to be hidden when the 'proper' image is loaded.
        if (imageView != null && thingInfo.getId().equals(imageView.getTag())) {
            if (progressBar != null) {
                progressBar.setVisibility(View.GONE);
            }
            imageView.setVisibility(View.VISIBLE);

            if (thingInfo.getThumbnailBitmap() != null)
                imageView.setImageBitmap(thingInfo.getThumbnailBitmap());
            else if (thingInfo.getThumbnailResource() != null)
                imageView.setImageResource(thingInfo.getThumbnailResource());
        }
    }

    private boolean isCurrentlyOnScreenUI(int position) {
        return position >= mActivity.getListView().getFirstVisiblePosition() &&
               position <= mActivity.getListView().getLastVisiblePosition();
    }

}

