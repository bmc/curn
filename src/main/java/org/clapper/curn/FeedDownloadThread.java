/*---------------------------------------------------------------------------*\
  This software is released under a BSD license, adapted from
  <http://opensource.org/licenses/bsd-license.php>

  Copyright &copy; 2004-2010 Brian M. Clapper.
  All rights reserved.

  Redistribution and use in source and binary forms, with or without
  modification, are permitted provided that the following conditions are met:

  * Redistributions of source code must retain the above copyright notice,
    this list of conditions and the following disclaimer.

  * Redistributions in binary form must reproduce the above copyright notice,
    this list of conditions and the following disclaimer in the documentation
    and/or other materials provided with the distribution.

  * Neither the name "clapper.org", "curn", nor the names of the project's
    contributors may be used to endorse or promote products derived from
    this software without specific prior written permission.

  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
  IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
  THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
  PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
  CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
  EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
  PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
  PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
  LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
  NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
\*---------------------------------------------------------------------------*/


package org.clapper.curn;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;

import org.apache.commons.io.IOUtils;
import org.clapper.curn.parser.RSSChannel;
import org.clapper.curn.parser.RSSItem;
import org.clapper.curn.parser.RSSLink;
import org.clapper.curn.parser.RSSParser;
import org.clapper.curn.parser.RSSParserException;
import org.clapper.util.logging.Logger;
import org.clapper.util.text.TextUtil;



class FeedDownloadThread implements Runnable
{
    /*----------------------------------------------------------------------*\
                             Private Constants
    \*----------------------------------------------------------------------*/

    private static final String HTTP_CONTENT_TYPE_CHARSET_FIELD = "charset=";
    private static final int    HTTP_CONTENT_TYPE_CHARSET_FIELD_LEN =
                                      HTTP_CONTENT_TYPE_CHARSET_FIELD.length();

    /*----------------------------------------------------------------------*\
                           Private Instance Data
    \*----------------------------------------------------------------------*/

    private final Logger                  log;                         //NOPMD
    private final String                  id;
    private final CurnConfig              configuration;
    private final RSSParser               rssParser;
    private final FeedCache               cache;
    private final Queue<FeedInfo>         feedQueue;
    private       FeedException           exception = null;
    private final MetaPlugIn              metaPlugIn = MetaPlugIn.getMetaPlugIn();
    private       RSSChannel              channel = null;
    private       FeedDownloadDoneHandler feedDownloadDoneHandler = null;

    private static AtomicInteger nextThreadID = new AtomicInteger(0);

    /*----------------------------------------------------------------------*\
                               Inner Classes
    \*----------------------------------------------------------------------*/

    /**
     * Encapsulates information about a downloaded feed.
     */
    private class DownloadedTempFile
    {
        File    file;
        String  encoding;
        int     bytesDownloaded;

        DownloadedTempFile(File   tempFile,
                           String encoding,
                           int    bytesDownloaded)
        {
            this.file = tempFile;
            this.encoding = encoding;
            this.bytesDownloaded = bytesDownloaded;
        }
    }

    /*----------------------------------------------------------------------*\
                                Constructor
    \*----------------------------------------------------------------------*/

    /**
     * Create a new <tt>FeedDownloadThread</tt> object to download feeds.
     *
     * @param parser          the RSS parser to use
     * @param feedCache       the feed cache to save cache data to
     * @param configFile      the parsed configuration file
     * @param feedQueue       list of feeds to be processed. The list is
     *                        assumed to be shared across multiple threads,
     *                        and must be thread safe.
     * @param feedDoneHandler called when afeed is finished downloading
     */
    FeedDownloadThread(RSSParser               parser,
                       FeedCache               feedCache,
                       CurnConfig              configFile,
                       Queue<FeedInfo>         feedQueue,
                       FeedDownloadDoneHandler feedDoneHandler)
    {
        this.id = String.valueOf(nextThreadID.getAndIncrement());

        String name = "FeedDownloadThread-" + this.id;

        Thread.currentThread().setName(name);
        this.log = new Logger(name);
        this.configuration = configFile;
        this.rssParser = parser;
        this.cache = feedCache;
        this.feedQueue = feedQueue;
        this.feedDownloadDoneHandler = feedDoneHandler;

        //setPriority (getPriority() + 1);
    }

    /*----------------------------------------------------------------------*\
                              Public Methods
    \*----------------------------------------------------------------------*/

    /**
     * Run the thread. Pulls the next <tt>FeedInfo</tt> object from the
     * feed queue (the list passed to the constructor) and processes it.
     * The thread stops running when it has finished downloading a feed and
     * it finds that the feed queue is empty.
     */
    public void run()
    {
        boolean done = false;

        log.info("Thread is alive at priority " +
                 Thread.currentThread().getPriority());

        while (! done)
        {
            FeedInfo feed = null;

            log.debug("Checking feed queue.");
            feed = feedQueue.poll();

            if (feed == null)
            {
                log.info("Queue of feeds is empty. Nothing left to do.");
                done = true;
            }

            else
            {
                processFeed(feed);
            }
        }

        log.debug("Thread is finishing.");
    }

    /*----------------------------------------------------------------------*\
                          Package-visible Methods
    \*----------------------------------------------------------------------*/

    /**
     * Processes the specified feed. This method is called by {@link #run}.
     * It's also intended to be called directly, when <i>curn</i> is
     * running in non-threaded mode. Once this method returns, use the
     * {@link #errorOccurred} method to determine whether a feed-processing
     * error occurred, and the {@link #getException} method to receive the
     * exception if an error did occur. (If an error does occur, this method
     * logs it regardless.)
     *
     * @param feed  The <tt>FeedInfo</tt> object for the feed to be processed
     *
     * @see #errorOccurred
     * @see #getException
     */
    void processFeed(final FeedInfo feed)
    {
        this.exception = null;
        this.channel = null;

        try
        {
            log.info("Processing feed: " + feed.getURL().toString());

            channel = handleFeed(feed, rssParser);
            if (channel != null)
                feedDownloadDoneHandler.feedFinished(feed, channel);
        }

        catch (FeedException ex)
        {
            this.exception = new FeedException
                (feed,
                 Constants.BUNDLE_NAME,
                 "FeedDownloadThread.downloadError",
                 "(Config file \"{0}\") error downloading feed",
                 new Object[]
                 {
                     configuration.getConfigurationFileURL(),
                 },
                 ex);
            log.error(ex.getMessages(true), this.exception);
        }

        catch (CurnException ex)
        {
            this.exception = new FeedException
                (feed,
                 Constants.BUNDLE_NAME,
                 "FeedDownloadThread.downloadError",
                 "(Config file \"{0}\") error downloading feed",
                 new Object[]
                 {
                     configuration.getConfigurationFileURL(),
                 },
                 ex);
            log.error(ex.getMessages(true), this.exception);
        }
    }

    /**
     * Get the parsed channel data.
     *
     * @return the channel data
     */
    RSSChannel getParsedChannelData()
    {
        return channel;
    }

    /**
     * Determine whether an error occurred during processing of the most
     * recent feed. If an error did occur, you can use {@link #getException}
     * to get the corresponding exception.
     *
     * @return <tt>true</tt> if an error occurred while processing the last
     *         feed, <tt>false</tt> if no error occurred
     *
     * @see #processFeed
     * @see #getException
     */
    boolean errorOccurred()
    {
        return (this.exception != null);
    }

    /**
     * If an error occurred during processing of the most recent feed,
     * this method will return the exception associated with the error.
     *
     * @return the exception associated with the most recent error, or
     *         null if no error has occurred
     *
     * @see #processFeed
     * @see #errorOccurred
     */
    FeedException getException()
    {
        return this.exception;
    }

    /*----------------------------------------------------------------------*\
                              Private Methods
    \*----------------------------------------------------------------------*/

    /**
     * Actually processes a feed. This method is called by checkFeed()
     * after checkFeed() determines that there's a reason to try to download
     * the feed (i.e., the feed has a "save as" setting, and/or parsing is
     * desired.
     * @param feedInfo      the info about the feed
     * @param parser        the RSS parser to use, or null if parsing is to
     *                      be skipped
     *
     * @return the <tt>RSSChannel</tt> representing the parsed feed, if
     *         parsing was enabled; otherwise, null.
     *
     * @throws FeedException  feed download error
     * @throws CurnException  some other error (e.g., plug-in error)
     */
    private RSSChannel handleFeed(final FeedInfo   feedInfo,
                                  final RSSParser  parser)
        throws FeedException,
               CurnException
    {
        URL         feedURL = feedInfo.getURL();
        String      feedURLString = feedURL.toString();
        RSSChannel  resultChannel = null;

        try
        {
            log.info("Checking for new data from RSS feed " + feedURLString);

            boolean forceDownload = metaPlugIn.forceFeedDownload(feedInfo, cache);
            log.debug("Feed \"" + feedURLString + "\": forceDownload=" +
                      forceDownload);

            // Open the connection.

            URLConnection conn = feedURL.openConnection();

            if (! metaPlugIn.runPreFeedDownloadPlugIn(feedInfo, conn))
            {
                log.debug("Feed " + feedInfo.getURL().toString() +
                          ": A plug-in disabled the feed.");
            }

            else
            {
                resultChannel = downloadAndProcessFeed(feedInfo, parser,
                                                       conn, forceDownload);
            }
        }

        catch (MalformedURLException ex)
        {
            throw new FeedException(feedInfo, ex);
        }

        catch (IOException ex)
        {
            throw new FeedException(feedInfo, ex);
        }

        return resultChannel;
    }

    /**
     * Unconditionally download and process a feed. Only called by
     * handleFeed().
     *
     * @param feedInfo      the info about the feed
     * @param parser        the RSS parser to use, or null if parsing is to
     *                      be skipped
     * @param urlConn       open URLConnection for the feed
     * @param forceDownload <tt>true</tt> to force the download even if the
     *                      feed hasn't changed, <tt>false</tt> to observe
     *                      the normal rules
     *
     * @return the <tt>RSSChannel</tt> representing the parsed feed, if
     *         parsing was enabled; otherwise, null.
     *
     * @throws FeedException  feed download error
     * @throws CurnException  some other error (e.g., plug-in error)
     */
    private RSSChannel downloadAndProcessFeed(final FeedInfo      feedInfo,
                                              final RSSParser     parser,
                                              final URLConnection urlConn,
                                              final boolean       forceDownload)
        throws FeedException,
               CurnException
    {
        RSSChannel  resultChannel = null;
        URL feedURL = feedInfo.getURL();

        try
        {
            // Don't download the channel if it hasn't been modified since
            // we last checked it. We set the If-Modified-Since header, to
            // tell the web server not to return the content if it's not
            // newer than what we saw before. However, as a double-check
            // (for web servers that ignore the header), we also check the
            // Last-Modified header, if any, that's returned; if it's not
            // newer, we don't bother to parse and process the returned
            // XML.

            if (! forceDownload)
                setIfModifiedSinceHeader(urlConn, feedInfo, cache);

            // If the feed has actually changed, or if downloading is force,
            // process it.

            if ((! forceDownload) &&
                (! feedHasChanged(urlConn, feedInfo, cache)))
            {
                log.info ("Feed has not changed. Skipping it.");
            }

            else
            {
                log.debug("Feed may have changed. " +
                          "Downloading and processing it.");

                // Download the feed to a file. We'll parse the file.

                DownloadedTempFile tempFile = downloadFeed(urlConn, feedInfo);

                if (tempFile.bytesDownloaded == 0)
                {
                    log.debug("Feed \"" + feedURL + "\" returned no data.");
                }

                else
                {
                    metaPlugIn.runPostFeedDownloadPlugIn(feedInfo,
                                                         tempFile.file,
                                                         tempFile.encoding);

                    if (parser == null)
                    {
                        log.debug("No RSS parser. Skipping XML parse phase.");
                    }

                    else
                    {
                        log.debug("Using RSS parser " +
                                  parser.getClass().getName() +
                                  " to parse \"" + feedURL + "\"");

                        InputStream is = new FileInputStream(tempFile.file);
                        resultChannel = parser.parseRSSFeed(feedURL,
                                                            is,
                                                            tempFile.encoding);
                        is.close();

                        // Make sure the channel has a link.

                        Collection<RSSLink> links = resultChannel.getLinks();
                        if ((links == null) || (links.size() == 0))
                        {
                            RSSLink link = new RSSLink(feedURL,
                                                       "text/xml",
                                                       RSSLink.Type.SELF);
                            resultChannel.setLinks(Collections.singleton(link));
                        }

                        if (! metaPlugIn.runPostFeedParsePlugIn(feedInfo,
                                                                cache,
                                                                resultChannel))
                        {
                            resultChannel = null;
                        }

                        if (resultChannel != null)
                        {
                            processChannelItems(resultChannel, feedInfo);
                            if (resultChannel.getItems().size() == 0)
                                resultChannel = null;
                        }

                        if (resultChannel != null)
                        {
                            boolean ok = metaPlugIn.runPostFeedProcessPlugIn(
                                feedInfo, cache, resultChannel
                            );
                            if (! ok)
                                resultChannel = null;
                        }
                    }
                }

                tempFile.file.delete();
                if (cache != null)
                {
                    cache.addToCache(feedURL,
                                     new Date(urlConn.getLastModified()),
                                     feedInfo);
                }
            }
        }

        catch (IOException ex)
        {
            throw new FeedException(feedInfo, ex);
        }

        catch (RSSParserException ex)
        {
            throw new FeedException(feedInfo, ex);
        }

        log.debug("downloadAndProcessFeed(): Feed=" +
                  feedInfo.getURL() + ", returning " +
                  ((resultChannel == null) ? "null" : resultChannel.toString()));

        return resultChannel;
    }

    /**
     * Download a feed.
     *
     * @param conn     the <tt>URLConnection</tt> for the feed
     * @param feedInfo the <tt>FeedInfo</tt> object for the feed
     *
     * @return the <tt>DownloadedTempFile</tt> object that captures the
     *         details about the downloaded file
     *
     * @throws IOException   I/O error
     * @throws CurnException some other error
     */
    private DownloadedTempFile downloadFeed(final URLConnection conn,
                                            final FeedInfo      feedInfo)
        throws CurnException,
               IOException
    {
        URL feedURL = feedInfo.getURL();
        String feedURLString = feedURL.toString();
        int totalBytes = 0;
        File tempFile = CurnUtil.createTempXMLFile();

        log.debug("Downloading \"" + feedURLString + "\" to file \"" +
                  tempFile.getPath());

        InputStream urlStream = getURLInputStream(conn);

        /* Determine the character set encoding to use.
         * When downloading, all we are doing is copying bytes.
         * If we get an indication of encoding, we pass it along
         * to Rome or whatnot to use in interpreting those bytes.
         */

        String protocol = feedURL.getProtocol();
        String encoding = null; // null unless we see some other indication.

        if (protocol.equals("http") || protocol.equals("https"))
        {
            String contentTypeHeader = conn.getContentType();

            if (contentTypeHeader != null)
            {
                encoding = contentTypeCharSet(contentTypeHeader);
                log.debug("HTTP server says encoding for \"" +
                          feedURLString +
                          "\" is \"" +
                          ((encoding == null) ? "<null>" : encoding) +
                          "\"");
            }
        }

        else if (protocol.equals("file"))
        {
            // Assume the same default encoding used by "SaveAsEncoding",
            // unless explicitly specified.

            encoding = Constants.DEFAULT_SAVE_AS_ENCODING;
            log.debug("Default encoding for \"" + feedURLString +
                      "\" is \"" + encoding + "\"");
        }

        if (feedInfo.getForcedCharacterEncoding() != null)
        {
            encoding = feedInfo.getForcedCharacterEncoding();
        }

        OutputStream tempOutput = null;
        try {
            tempOutput = new FileOutputStream(tempFile);
            totalBytes = IOUtils.copy(urlStream, tempOutput);
        } finally {
            IOUtils.closeQuietly(tempOutput);
            IOUtils.closeQuietly(urlStream);
        }

        // It's possible for totalBytes to be zero if, for instance, the
        // use of the If-Modified-Since header caused an HTTP server to
        // return no content.
        // It's possible for the encoding to be null if nothing gave us a clue.

        return new DownloadedTempFile(tempFile, encoding, totalBytes);
    }



    /**
     * Given a content-type header, extract the character set information.
     *
     * @param contentType  the content type header
     *
     * @return the character set, or null if not available
     */
    private String contentTypeCharSet(final String contentType)
    {
        String result = null;
        String[] fields = TextUtil.split(contentType, "; \t");


        for (int i = 0; i < fields.length; i++)
        {
            // Compare in a case-insensitive fashion. Some servers (e.g.,
            // versions of Microsoft's IIS) will specify "Charset=", not
            // "charset=".

            String s = fields[i].toLowerCase();
            if (s.startsWith(HTTP_CONTENT_TYPE_CHARSET_FIELD) &&
                (s.length() > HTTP_CONTENT_TYPE_CHARSET_FIELD_LEN))
            {
                // Strip any quotes from the beginning and end of the field.
                // Some web servers tack them on, some don't. This isn't,
                // strictly speaking, kosher, according to the HTTP spec.
                // But curn has to deal with real-life, including server
                // brokenness.

                result = fields[i].substring(HTTP_CONTENT_TYPE_CHARSET_FIELD_LEN)
                                  .replace("\"", "");

                break;
            }
        }

        return result;
    }

    /**
     * Get the input stream for a URL. Handles compressed data.
     *
     * @param conn the <tt>URLConnection</tt> to process
     *
     * @return the <tt>InputStream</tt>
     *
     * @throws IOException I/O error
     */
    private InputStream getURLInputStream(final URLConnection conn)
        throws IOException
    {
        InputStream is = conn.getInputStream();
        String ce = conn.getHeaderField("content-encoding");

        if (ce != null)
        {
            String urlString = conn.getURL().toString();

            log.debug("URL \"" + urlString + "\" -> Content-Encoding: " + ce);
            if (ce.indexOf("gzip") != -1)
            {
                log.debug("URL \"" + urlString +
                          "\" is compressed. Using GZIPInputStream.");
                is = new GZIPInputStream(is);
            }
        }

        return is;
    }

    /**
     * Conditionally set the header that "If-Modified-Since" header for a
     * feed. Must be called on a <tt>URLConnection</tt> before the
     * <tt>InputStream</tt> is retrieved. Uses the feed cache to set the
     * value.
     *
     * @param conn     the <tt>URLConnection</tt> on which to set the
     *                 header
     * @param feedInfo the information on the feed
     * @param cache    the cache
     */
    private void setIfModifiedSinceHeader(final URLConnection conn,
                                          final FeedInfo      feedInfo,
                                          final FeedCache     cache)
    {
        long     lastSeen = 0;
        URL      feedURL = feedInfo.getURL();

        if (cache != null)
        {
            FeedCacheEntry entry = cache.getEntryByURL(feedURL);

            if (entry != null)
            {
                lastSeen = entry.getTimestamp();

                if (lastSeen > 0)
                {
                    if (log.isDebugEnabled())
                    {
                        log.debug("Setting If-Modified-Since header for " +
                                  "feed \"" + feedURL.toString() + "\" to: " +
                                  String.valueOf (lastSeen) + " (" +
                                  new Date (lastSeen).toString() + ")");
                    }

                    conn.setIfModifiedSince (lastSeen);
                }
            }
        }
    }

    /**
     * Query the appropriate URL connection headers to determine whether
     * the remote server thinks feed data has changed since the last time
     * the feed was downloaded. Must be called on a <tt>URLConnection</tt>
     * after the <tt>InputStream</tt> is retrieved. Uses the feed cache to
     * set the value.
     *
     * @param conn     the <tt>URLConnection</tt> whose headers are to be
     *                 checked
     * @param feedInfo the information on the feed
     * @param cache    the cache
     *
     * @throws IOException I/O error
     */
    private boolean feedHasChanged(final URLConnection conn,
                                   final FeedInfo      feedInfo,
                                   final FeedCache     cache)
        throws IOException
    {
        long     lastSeen = 0;
        long     lastModified = 0;
        boolean  hasChanged = false;
        URL      feedURL = feedInfo.getURL();

        if (cache != null)
        {
            FeedCacheEntry entry = cache.getEntryByURL (feedURL);

            if (entry != null)
                lastSeen = entry.getTimestamp();
        }

        if (lastSeen == 0)
        {
            log.debug("Feed \"" + feedURL.toString() +
                      "\" has no recorded last-seen time.");
            hasChanged = true;
        }

        else if ((lastModified = conn.getLastModified()) == 0)
        {
            log.debug("Feed \"" + feedURL.toString() +
                      "\" provides no last-modified time.");
            hasChanged = true;
        }

        else if (lastSeen >= lastModified)
        {
            log.debug("Feed \"" + feedURL.toString() +
                      "\" has Last-Modified time of " +
                      new Date (lastModified).toString() +
                      ", which is not newer than last-seen time of " +
                      new Date (lastSeen).toString() +
                      ". Feed has no new data.");
        }

        else
        {
            log.debug("Feed \"" + feedURL.toString() +
                      "\" has Last-Modified time of " +
                      new Date (lastModified).toString() +
                      ", which is newer than last-seen time of " +
                      new Date (lastSeen).toString() +
                      ". Feed might have new data.");
            hasChanged = true;
        }

        return hasChanged;
    }

    /**
     * Process all the items for a channel.
     *
     * @param channel   the channel
     * @param feedInfo  the feed information for the channel
     *
     * @throws RSSParserException    parser exception
     * @throws MalformedURLException bad URL
     */
    private void processChannelItems(final RSSChannel  channel,
                                     final FeedInfo    feedInfo)
        throws RSSParserException,
               MalformedURLException
    {
        Collection<RSSItem> items;
        String              channelName;

        RSSLink selfLink = channel.getLink(RSSLink.Type.SELF);
        if (selfLink == null)
            channelName = feedInfo.getURL().toString();
        else
            channelName = selfLink.getURL().toString();

        items = channel.getItems();

        // First, weed out the ones we don't care about.

        log.info("Channel \"" + channelName + "\": " +
                 String.valueOf (items.size()) + " total items");
        for (Iterator<RSSItem> it = items.iterator(); it.hasNext(); )
        {
            RSSItem item     = it.next();
            RSSLink itemLink = item.getURL();

            if (itemLink == null)
            {
                log.debug("Skipping item with null URL.");
                it.remove();
                continue;
            }

            URL itemURL  = itemLink.getURL();

            // Normalize the URL and save it.

            itemURL = CurnUtil.normalizeURL(itemURL);
            itemLink.setURL(itemURL);

            // Skip it if it's cached--unless it's sticky. We cache by item
            // URL.

            log.debug ("Item link: " + itemURL);
            if ((cache != null) && (! itemIsNew (item, itemURL)))
            {
                log.debug("Discarding old, cached item.");
                it.remove();
            }
        }

        // Add all the items to the cache, and adjust whatever items are to
        // be adjusted.

        if ((items.size() > 0) && (cache != null))
        {
            for (RSSItem item : items)
            {
                RSSLink itemLink = item.getURL();
                assert (itemLink != null);
                URL itemURL = itemLink.getURL();

                log.debug ("Caching URL: " + itemURL);
                cache.addToCache (itemURL,
                                  item.getPublicationDate(),
                                  feedInfo);
            }
        }

        // Change the channel's items to the ones that are left.

        log.debug ("Setting channel items: total=" + items.size());
        channel.setItems (items);
    }

    /**
     * Determine whether an item is cached.
     *
     * @param item    the item to test
     * @param itemURL the item's normalized URL (which might not be the
     *                same as the URL in the item itself)
     *
     * @return true if cached, false if not
     */
    private boolean itemIsNew (final RSSItem item, final URL itemURL)
    {
        String   itemURLString = itemURL.toString();
        boolean  isNew         = true;
        FeedCacheEntry cacheEntry = cache.getEntryForItem(item);

        if (cacheEntry == null)
        {
            log.debug("URL \"" + itemURLString +
                      "\" is not in the cache. It's new.");
        }

        else if (cacheEntry.isSticky())
        {
            log.debug("URL \"" + itemURLString + "\" is marked sticky. " +
                      "Treating it as new.");
            isNew = true;
        }

        else
        {
            Date cachePubDate = cacheEntry.getPublicationDate();
            Date itemPubDate  = item.getPublicationDate();

            log.debug("URL \"" + itemURLString + "\" is cached and non-sticky.");
            if ((cachePubDate == null) || (itemPubDate == null))
            {
                log.debug("Missing publication date in item and/or " +
                           "cache for URL \"" + itemURLString +
                           "\". Assuming URL is old, since it is in the " +
                           "cache. Skipping it.");
                isNew = false;
            }

            else
            {
                log.debug("URL \"" + itemURLString +
                          "\": Cached publication date is " +
                          cachePubDate.toString() +
                          "\", item publication date is " + itemPubDate);
                if (itemPubDate.after(cachePubDate))
                {
                    log.debug("URL \"" + itemURLString +
                              "\" is newer than cached publication date. " +
                              "Keeping it.");
                }

                else
                {
                    log.debug("URL \"" + itemURLString +
                              "\" is not newer than cached publication date. " +
                              "Skipping it.");
                    isNew = false;
                }
            }
        }

        log.debug("URL \"" + itemURLString + "\" is new? " + isNew);
        return isNew;
    }
}
