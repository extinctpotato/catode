package in.shick.diode.saved;

/**
 * SavedContent.java
 * @author John Zavidniak
 * A class which holds information related to a saved comment. This is
 * used in association with the SQLite Database to save and retrieve
 * a user's saved content
 */

public class SavedContent
{

    private int id;
    private String user;
    private String author;
    private String body;
    private String linkId;
    private String commentId;
    private String subreddit;

    /**
     * @param user the current logged in user
     * @param author the author of the comment
     * @param body the html of the comment's body
     * @param linkId equivalent to link_id in the comment's ThingInfo
     * @param commentId equivalent to id in the comment's ThingInfo
     * @param subreddit the subreddit the comment exists in
     */
    public SavedContent(String user, String author, String body, String linkId, String commentId, String subreddit)
    {
        this.user = user;
        this.author = author;
        this.body = body;
        this.linkId = linkId;
        this.commentId = commentId;
        this.subreddit = subreddit;
    }

    /**
     * @param id the id of this object in the SQLite database
     * @param user the current logged in user
     * @param author the author of the comment
     * @param body the html of the comment's body
     * @param linkId equivalent to link_id in the comment's ThingInfo
     * @param commentId equivalent to id in the comment's ThingInfo
     * @param subreddit the subreddit the comment exists in
     */
    public SavedContent(int id, String user, String author, String body, String linkId,
                        String commentId, String subreddit)
    {
        this.id = id;
        this.user = user;
        this.author = author;
        this.body = body;
        this.linkId = linkId;
        this.commentId = commentId;
        this.subreddit = subreddit;
    }

    public int getId()
    {
        return id;
    }

    public String getUser()
    {
        return user;
    }

    public String getAuthor()
    {
        return author;
    }

    public String getBody()
    {
        return body;
    }

    public String getLinkId()
    {
        return linkId;
    }

    public String getCommentId()
    {
        return commentId;
    }

    public String getSubreddit()
    {
        return subreddit;
    }

    public void setId(int id)
    {
        this.id = id;
    }

    public void setUser(String user)
    {
        this.user = user;
    }

    public void setAuthor(String author)
    {
        this.author = author;
    }

    public void setBody(String body)
    {
        this.body = body;
    }

    public void setLinkId(String linkId)
    {
        this.linkId = linkId;
    }

    public void setCommentId(String commentId)
    {
        this.commentId = commentId;
    }

    public void setSubreddit(String subreddit)
    {
        this.subreddit = subreddit;
    }

}