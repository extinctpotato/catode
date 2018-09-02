package in.shick.diode.saved;

import java.util.LinkedList;
import java.util.List;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * SavedDBHandler.java
 * @author John Zavidniak
 * This class manages adding, removing, and retrieving things from the database
 * of saved content
 */

public class SavedDBHandler extends SQLiteOpenHelper
{

    private static final int DB_VERSION = 1;

    private static final String DB_NAME = "SavedContentDB";
    private static final String TABLE_SAVED_CONTENT = "saved_content";

    private static final String KEY_ID = "id";
    private static final String KEY_USER = "user";
    private static final String KEY_AUTHOR = "author";
    private static final String KEY_BODY = "body";
    private static final String KEY_LINK_ID = "link_id";
    private static final String KEY_COMMENT_ID = "comment_id";
    private static final String KEY_SUBREDDIT = "subreddit";

    public SavedDBHandler(Context context)
    {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db)
    {
        String createSavedContentTable = "CREATE TABLE " + TABLE_SAVED_CONTENT + "(" + KEY_ID + " INTEGER PRIMARY KEY,"
                                         + KEY_USER + " TEXT," + KEY_AUTHOR + " TEXT," + KEY_BODY + " TEXT,"
                                         + KEY_LINK_ID + " TEXT," + KEY_COMMENT_ID + " TEXT," + KEY_SUBREDDIT + " TEXT)";
        db.execSQL(createSavedContentTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
    {
        db.execSQL("DROP IF TABLE EXISTS " + TABLE_SAVED_CONTENT);
        onCreate(db);
    }

    /**
     * Add new content to the saved content table
     * @param sc the saved content to add
     */
    public void addSavedContent(SavedContent sc)
    {
        SQLiteDatabase sdb = this.getWritableDatabase();

        ContentValues cValues = new ContentValues();
        cValues.put(KEY_USER, sc.getUser());
        cValues.put(KEY_AUTHOR, sc.getAuthor());
        cValues.put(KEY_BODY, sc.getBody());
        cValues.put(KEY_LINK_ID, sc.getLinkId());
        cValues.put(KEY_COMMENT_ID, sc.getCommentId());
        cValues.put(KEY_SUBREDDIT, sc.getSubreddit());

        sdb.insert(TABLE_SAVED_CONTENT, null, cValues);
        sdb.close();
    }

    /**
     * Delete content from the saved content table
     * @param sc the saved content to delete
     */
    public void deleteSavedContent(SavedContent sc)
    {
        SQLiteDatabase sdb = this.getWritableDatabase();

        String[] args = {sc.getUser(), sc.getLinkId(), sc.getCommentId()};
        sdb.delete(TABLE_SAVED_CONTENT, KEY_USER + "= ? AND " + KEY_LINK_ID + "= ? AND " + KEY_COMMENT_ID + "= ?",
                   args);

        sdb.close();
    }

    /**
     * Purge the saved content table of all saved content
     */
    public void deleteAllSavedContent()
    {
        SQLiteDatabase sdb = this.getWritableDatabase();

        sdb.delete(TABLE_SAVED_CONTENT, "1", null);

        sdb.close();
    }

    /**
     * Determine whether or not the saved content table contains a specific comment
     * @param user the user who saved the comment
     * @param commentId the id of the comment
     * @return true if the saved content table contains the comment, false if not
     */
    public boolean containsComment(String user, String commentId)
    {
        SQLiteDatabase sdb = this.getReadableDatabase();

        String[] columns = {KEY_ID, KEY_USER, KEY_AUTHOR, KEY_BODY, KEY_LINK_ID, KEY_COMMENT_ID, KEY_SUBREDDIT};
        String[] args = {user, commentId};
        Cursor cursor = sdb.query(TABLE_SAVED_CONTENT, columns, KEY_USER + "= ? AND " +
                                  KEY_COMMENT_ID + "= ?", args, null, null, null);

        if (!cursor.moveToFirst())
        {
            return false;
        }

        cursor.close();
        return true;
    }

    /**
     * Get the saved content associated with the given id
     * @param id the id to use to find the saved content
     * @return
     */
    public SavedContent getSavedContent(int id)
    {
        SQLiteDatabase sdb = this.getReadableDatabase();

        String[] columns = {KEY_ID, KEY_USER, KEY_AUTHOR, KEY_BODY, KEY_LINK_ID, KEY_COMMENT_ID, KEY_SUBREDDIT};
        String[] arg = {String.valueOf(id)};
        Cursor cursor = sdb.query(TABLE_SAVED_CONTENT, columns, KEY_ID + "= ?", arg, null, null, null);

        if (cursor != null)
        {
            cursor.moveToFirst();
        }
        id = Integer.parseInt(cursor.getString(0));
        String user = cursor.getString(1);
        String author = cursor.getString(2);
        String body = cursor.getString(3);
        String linkId = cursor.getString(4);
        String commentId = cursor.getString(5);
        String subreddit = cursor.getString(6);

        cursor.close();
        sdb.close();
        return new SavedContent(id, user, author, body, linkId, commentId, subreddit);
    }

    /**
     * Get all the saved content that was saved by the given user
     * @param user the user whose saved content should be returned
     * @return a list of all the saved content owned by the given user
     */
    public List<SavedContent> getSavedContent(String user)
    {
        LinkedList<SavedContent> saved = new LinkedList<SavedContent>();

        SQLiteDatabase sdb = this.getReadableDatabase();

        String[] columns = {KEY_ID, KEY_USER, KEY_AUTHOR, KEY_BODY, KEY_LINK_ID, KEY_COMMENT_ID, KEY_SUBREDDIT};
        String[] arg = {user};
        Cursor cursor = sdb.query(TABLE_SAVED_CONTENT, columns, KEY_USER + "= ?", arg, null, null, null);

        if (cursor == null)
        {
            return null;
        }
        if (cursor.moveToFirst())
        {
            int id;
            String author;
            String body;
            String linkId;
            String commentId;
            String subreddit;
            do
            {
                id = Integer.parseInt(cursor.getString(0));
                author = cursor.getString(2);
                body = cursor.getString(3);
                linkId = cursor.getString(4);
                commentId = cursor.getString(5);
                subreddit = cursor.getString(6);
                saved.addFirst(new SavedContent(id, user, author, body, linkId, commentId, subreddit));
            }
            while (cursor.moveToNext());
        }

        cursor.close();
        sdb.close();
        return saved;
    }

    /**
     * @return the size of the saved content table
     */
    public int getSavedContentTableSize()
    {
        SQLiteDatabase sdb = this.getReadableDatabase();

        String query = "SELECT * FROM " + TABLE_SAVED_CONTENT;
        Cursor cursor = sdb.rawQuery(query, null);

        cursor.close();
        return cursor.getCount();
    }

}