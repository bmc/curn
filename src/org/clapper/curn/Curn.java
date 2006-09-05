/*---------------------------------------------------------------------------*\
  $Id$
  ---------------------------------------------------------------------------
  This software is released under a BSD-style license:

  Copyright (c) 2004-2006 Brian M. Clapper. All rights reserved.

  Redistribution and use in source and binary forms, with or without
  modification, are permitted provided that the following conditions are
  met:

  1.  Redistributions of source code must retain the above copyright notice,
      this list of conditions and the following disclaimer.

  2.  The end-user documentation included with the redistribution, if any,
      must include the following acknowlegement:

        "This product includes software developed by Brian M. Clapper
        (bmc@clapper.org, http://www.clapper.org/bmc/). That software is
        copyright (c) 2004-2006 Brian M. Clapper."

      Alternately, this acknowlegement may appear in the software itself,
      if wherever such third-party acknowlegements normally appear.

  3.  Neither the names "clapper.org", "clapper.org Java Utility Library",
      nor any of the names of the project contributors may be used to
      endorse or promote products derived from this software without prior
      written permission. For written permission, please contact
      bmc@clapper.org.

  4.  Products derived from this software may not be called "clapper.org
      Java Utility Library", nor may "clapper.org" appear in their names
      without prior written permission of Brian M.a Clapper.

  THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
  WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
  MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN
  NO EVENT SHALL BRIAN M. CLAPPER BE LIABLE FOR ANY DIRECT, INDIRECT,
  INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
  NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
  DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
  THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
  THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
\*---------------------------------------------------------------------------*/

package org.clapper.curn;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.Map;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Properties;
import java.util.Queue;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.clapper.curn.parser.RSSParserFactory;
import org.clapper.curn.parser.RSSParser;
import org.clapper.curn.parser.RSSParserException;
import org.clapper.curn.parser.RSSChannel;

import org.clapper.util.config.ConfigurationException;
import org.clapper.util.logging.Logger;

/**
 * <p><i>curn</i>: Customizable Utilitarian RSS Notifier.</p>
 *
 * <p><i>curn</i> is an RSS reader. It scans a configured set of URLs, each
 * one representing an RSS feed, and summarizes the results in an
 * easy-to-read text format. <i>curn</i> keeps track of URLs it's seen
 * before, using an on-disk cache; when using the cache, it will suppress
 * displaying URLs it has already reported (though that behavior can be
 * disabled). <i>curn</i> can be extended to use any RSS parser; its
 * built-in RSS parser, the
 * {@link org.clapper.curn.parser.minirss.MiniRSSParser MiniRSSParser}
 * class, can handle files in
 * {@link <a href="http://www.atomenabled.org/developers/">Atom</a>}
 * format (0.3) and RSS formats
 * {@link <a target="_top" href="http://backend.userland.com/rss091">0.91</a>},
 * 0.92,
 * {@link <a target="_top" href="http://web.resource.org/rss/1.0/">1.0</a>} and
 * {@link <a target="_top" href="http://blogs.law.harvard.edu/tech/rss">2.0</a>}.</p>
 *
 * <p>The <tt>Curn</tt> class represents the API entry point into the
 * <i>curn</i> processing. Any program can call a <tt>Curn</tt> object's
 * {@link #processRSSFeeds processRSSFeeds()} method to invoke a <i>curn</i>
 * run. In practice, most people use the existing <tt>Tool</tt> command-line
 * program.</p>
 *
 * @version <tt>$Revision$</tt>
 */
public class Curn
{
    /*----------------------------------------------------------------------*\
                             Private Constants
    \*----------------------------------------------------------------------*/

    /*----------------------------------------------------------------------*\
                            Private Data Items
    \*----------------------------------------------------------------------*/

    private CurnConfig config = null;
    private Date currentTime = new Date();
    private MetaPlugIn metaPlugIn = null;            // NOPMD

    private final Collection<ConfiguredOutputHandler> configuredOutputHandlers =
        new ArrayList<ConfiguredOutputHandler>();

    /**
     * For log messages
     */
    private static final Logger log = new Logger (Curn.class);

    /*----------------------------------------------------------------------*\
                                Constructor
    \*----------------------------------------------------------------------*/

    /**
     * Instantiate a new <tt>Curn</tt> object and loads its plugins.
     *
     * @throws CurnException on error
     */
    public Curn()
        throws CurnException
    {
        metaPlugIn = MetaPlugIn.getMetaPlugIn();
        logEnvironmentInfo();
    }

    /*----------------------------------------------------------------------*\
                              Public Methods
    \*----------------------------------------------------------------------*/

    /**
     * Run <i>curn</i> against a configuration file.
     *
     * @param configPath      path to the configuration data
     * @param useCache        whether or not to use the cache
     *
     * @throws CurnException on error
     */
    public void run (final String configPath, final boolean useCache)
        throws CurnException
    {
        metaPlugIn.runStartupPlugIn();

        try
        {
            this.config = loadConfig (configPath);
            processRSSFeeds (useCache);
        }

        catch (ConfigurationException ex)
        {
            throw new CurnException (ex);
        }

        catch (RSSParserException ex)
        {
            throw new CurnException (ex);
        }

        finally
        {
            metaPlugIn.runShutdownPlugIn();
        }
    }

    /**
     * Set the cache's notion of the current time. This method will change
     * the time used when reading and pruning the cache from the current time
     * to the specified time. This method must be called before
     * <tt>processRSSFeeds()</tt>.
     *
     * @param newTime  the time to pretend is the current time
     */
    public void setCurrentTime (final Date newTime)
    {
        this.currentTime = newTime;
    }

    /*----------------------------------------------------------------------*\
                              Private Methods
    \*----------------------------------------------------------------------*/

    /**
     * Read the RSS feeds specified in a parsed configuration, writing them
     * to the output handler(s) specified in the configuration.
     *
     * @param useCache whether or not to use the cache
     *
     * @throws IOException             unable to open or read a required file
     * @throws ConfigurationException  error in configuration file
     * @throws RSSParserException      error parsing XML feed(s)
     * @throws CurnException           any other error
     */
    private void processRSSFeeds (final boolean useCache)
        throws ConfigurationException,
               RSSParserException,
               CurnException
    {
        Map<FeedInfo,RSSChannel> channels;
        boolean                  parsingEnabled = true;
        File                     cacheFile = config.getCacheFile();
        FeedCache                cache = null;

        loadOutputHandlers (config);

        if (useCache && (cacheFile != null))
        {
            cache = new FeedCache (config);
            cache.setCurrentTime (currentTime);
            cache.loadCache();
            metaPlugIn.runCacheLoadedPlugIn (cache);
        }

        if (config.isDownloadOnly())
        {
            // No output handlers. No need to instantiate a parser.

            log.debug ("Config is download-only. Skipping XML parse phase.");
            parsingEnabled = false;
        }

        Collection<FeedInfo> feeds = config.getFeeds();
        if (feeds.size() == 0)
        {
            throw new ConfigurationException (Constants.BUNDLE_NAME,
                                              "Curn.noConfiguredFeeds",
                                              "No configured RSS feed URLs.");
        }

        channels = downloadFeeds(parsingEnabled, cache, config);

        log.debug ("After downloading, total (parsed) channels = " +
                   channels.size());

        if (channels.size() > 0)
            outputChannels (channels);

        log.debug ("cacheFile=" +
                   ((cacheFile == null) ? "null" : cacheFile.getPath()) +
                   ", mustUpdateCache=" + config.mustUpdateCache());

        if ((cache != null) && config.mustUpdateCache())
        {
            int totalCacheBackups = config.totalCacheBackups();
            metaPlugIn.runPreCacheSavePlugIn (cache);
            cache.saveCache (totalCacheBackups);
        }
    }

    private CurnConfig loadConfig (final String configPath)
        throws CurnException
    {
        try
        {
            config = new CurnConfig();
            config.load (configPath);
            MetaPlugIn.getMetaPlugIn().runPostConfigPlugIn (config);
            return config;
        }

        catch (FileNotFoundException ex)
        {
            throw new CurnException (Constants.BUNDLE_NAME,
                                     "Curn.cantFindConfig",
                                     "Cannot find configuration file \"{0}\"",
                                     new Object[] {configPath},
                                     ex);
        }

        catch (IOException ex)
        {
            throw new CurnException (Constants.BUNDLE_NAME,
                                     "Curn.cantReadConfig",
                                     "I/O error reading configuration file " +
                                     "\"{0}\"",
                                     new Object[] {configPath},
                                     ex);
        }

        catch (ConfigurationException ex)
        {
            throw new CurnException (ex);
        }
    }

    private void loadOutputHandlers (final CurnConfig configuration)
        throws ConfigurationException,
               CurnException
    {
        if (configuration.totalOutputHandlers() > 0)
        {
            for (ConfiguredOutputHandler cfgHandler : configuration.getOutputHandlers())
            {
                // Ensure that the output handler can be instantiated.

                String className = cfgHandler.getClassName();

                log.debug ("Instantiating output handler \"" +
                           cfgHandler.getName() +
                           "\", of type " +
                           className);
                OutputHandler handler = cfgHandler.getOutputHandler();

                log.debug ("Initializing output handler \"" +
                           cfgHandler.getName() +
                           "\", of type " +
                           className);

                handler.init (config, cfgHandler);

                // Save it.

                configuredOutputHandlers.add (cfgHandler);
            }
        }
    }

    /**
     * Download the configured feeds using multiple simultaneous threads.
     * This method is called when the configured number of concurrent
     * download threads is greater than 1.
     *
     * @param parsingEnabled <tt>true</tt> if parsing is to be done,
     *                       <tt>false</tt> otherwise
     * @param feedCache      the loaded cache of feed data; may be modified
     * @param configuration  the parsed configuration
     *
     * @return a <tt>Map</tt> of <tt>RSSChannel</tt> objects, indexed
     *         by <tt>FeedInfo</tt>
     *
     * @throws RSSParserException error parsing feeds
     * @throws CurnException      some other error
     */
    private Map<FeedInfo,RSSChannel>
    downloadFeeds (final boolean    parsingEnabled,
                   final FeedCache  feedCache,
                   final CurnConfig configuration)
        throws RSSParserException,
               CurnException
    {
        int maxThreads = configuration.getMaxThreads();
        Collection<FeedInfo> feeds = configuration.getFeeds();
        int totalFeeds = feeds.size();
        final Map<FeedInfo,RSSChannel> channels =
            new ConcurrentHashMap<FeedInfo,RSSChannel>(totalFeeds,
                                                       0.75f,
                                                       maxThreads);
        final Queue<FeedInfo> feedQueue  = new ConcurrentLinkedQueue<FeedInfo>();
        final RSSParser parser = (parsingEnabled ? getRSSParser(configuration)
                                                 : null);

        if (maxThreads > totalFeeds)
            maxThreads = totalFeeds;

        log.info ("Doing multithreaded download of feeds, using " +
                  maxThreads + " threads.");

        // Fill the feed queue and make it a synchronized list.

        for (FeedInfo feedInfo : feeds)
             feedQueue.offer(feedInfo);

        if (feedQueue.size() == 0)
        {
            throw new CurnException (Constants.BUNDLE_NAME,
                                     "Curn.allFeedsDisabled",
                                     "All configured RSS feeds are disabled.");
        }

        // Create the thread objects in a concurrent thread pool. They'll pull
        // feeds off the queue themselves. Note that the concurrent Executor
        // model is to spawn multiple threads that run against a single
        // object. FeedDownloadThread isn't organized that way; it assumes it
        // can keep state in instance variables, which wouldn't work if
        // multiple threads were executing against a single FeedDownloadThread
        // object. So, we use a simple Runnable front-end whose run() method
        // creates and invokes individual FeedDownloadThread objects.

        ExecutorService threadPool;
        if (maxThreads == 1)
            threadPool = Executors.newSingleThreadExecutor();
        else
            threadPool = Executors.newFixedThreadPool(maxThreads);

        // Create a FeedDownloadHandler to handle the completion of each
        // feed.

        final FeedDownloadDoneHandler feedDownloadDoneHandler =
            new FeedDownloadDoneHandler()
        {
            public void feedFinished(FeedInfo feedInfo, RSSChannel channel)
            {
                channels.put(feedInfo, channel);
            }
        };

        final CountDownLatch doneLatch = new CountDownLatch(maxThreads);
        final CountDownLatch startLatch = new CountDownLatch(1);
        Runnable r = new Runnable()
        {
            public void run()
            {
                new FeedDownloadThread(parser,
                                       feedCache,
                                       configuration,
                                       feedQueue,
                                       feedDownloadDoneHandler,
                                       startLatch,
                                       doneLatch)
                   .run();
            }
        };

        // Start the download threads.

        log.info("Starting " + maxThreads + " feed-download threads.");
        log.debug ("Main thread priority is " +
                   Thread.currentThread().getPriority());

        for (int i = 0; i < maxThreads; i++)
            threadPool.execute(r);

        // Open the starting gate.

        log.info("Issuing the start signal.");
        startLatch.countDown();

        log.info("All feeds have been parceled out to threads. Waiting " +
                  "for threads to complete.");

        boolean threadsLeft = true;
        while (threadsLeft)
        {
            try
            {
                doneLatch.await();
                threadsLeft = false;
            }
            catch (InterruptedException ex)
            {
                log.error("Main thread interrupted while waiting on " +
                          "CountDownLatch", ex);
            }
        }

        log.info("Feed download threads are done.");

        // Reap the threads.

        threadPool.shutdown();

        // Finally, remove any entries that still have null channels. (This
        // can happen if there's no new data in a feed.)

        for (Iterator<Map.Entry<FeedInfo,RSSChannel>> it =
                 channels.entrySet().iterator();
             it.hasNext(); )
        {
            Map.Entry<FeedInfo,RSSChannel> mapEntry = it.next();
            if (mapEntry.getValue() == null)
                it.remove();
        }

        // Copy the channels to a LinkedHashMap in feed order.

        LinkedHashMap<FeedInfo,RSSChannel> result =
            new LinkedHashMap<FeedInfo,RSSChannel>(totalFeeds);
        for (FeedInfo feedInfo : feeds)
        {
            RSSChannel channel = channels.get(feedInfo);
            if (channel != null)
                result.put(feedInfo, channel);
        }

        return result;
    }

    /**
     * Get a new instance of an RSS parser.
     *
     * @param configuration the parsed configuration
     *
     * @return the RSSParser
     *
     * @throws RSSParserException error instantiating parser
     */
    private RSSParser getRSSParser (final CurnConfig configuration)
        throws RSSParserException
    {
        String parserClassName = configuration.getRSSParserClassName();
        log.info ("Getting parser \"" + parserClassName + "\"");
        return RSSParserFactory.getRSSParser (parserClassName);
    }

    private void outputChannels (final Map<FeedInfo,RSSChannel> channels)
        throws CurnException,
               ConfigurationException
    {
        OutputHandler             handler;
        Collection<OutputHandler> outputHandlers =
            new ArrayList<OutputHandler>();

        // Dump the output to each output handler

        for (ConfiguredOutputHandler cfgHandler : configuredOutputHandlers)
        {
            log.info ("Preparing to call output handler \"" +
                      cfgHandler.getName() +
                      "\", of type " +
                      cfgHandler.getClassName());

            handler = cfgHandler.getOutputHandler();
            outputHandlers.add (handler);

            for (FeedInfo fi : channels.keySet())
            {
                // Use a copy of the channel. That way, the plug-ins and
                // the output handler can modify its content freely, without
                // affecting anyone else.

                RSSChannel channel = channels.get (fi).makeCopy();
                metaPlugIn.runPreFeedOutputPlugIn (fi, channel, handler);
                handler.displayChannel (channel, fi);
                metaPlugIn.runPostFeedOutputPlugIn (fi, handler);
            }

            handler.flush();
            ReadOnlyOutputHandler ro = new ReadOnlyOutputHandler (handler);
            if (! metaPlugIn.runPostOutputHandlerFlushPlugIn (ro))
                cfgHandler.disable();
        }

        metaPlugIn.runPostOutputPlugIn (outputHandlers);
        outputHandlers.clear();
        outputHandlers = null;
    }

    /**
     * Log all system properties and other information about the Java VM, as
     * well as other environmental trivia deemed useful to log.
     */
    private void logEnvironmentInfo()
    {
        log.info (Version.getFullVersion());

        Properties properties = System.getProperties();
        TreeSet<String> sortedNames = new TreeSet<String>();
        for (Enumeration<?> e = properties.propertyNames();
             e.hasMoreElements(); )
        {
            sortedNames.add ((String) e.nextElement());
        }

        log.info("Using org.clapper.util library version: " +
                 org.clapper.util.misc.Version.getVersion());
        log.info("--- Start of Java properties");
        for (String name : sortedNames)
            log.info(name + "=" + properties.getProperty(name));

        log.info("--- End of Java properties");
    }
}
