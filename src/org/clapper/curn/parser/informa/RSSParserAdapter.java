/*---------------------------------------------------------------------------*\
  $Id$
\*---------------------------------------------------------------------------*/

package org.clapper.curn.parser.informa;

import org.clapper.curn.parser.RSSParser;
import org.clapper.curn.parser.RSSChannel;
import org.clapper.curn.parser.RSSParserException;

import de.nava.informa.core.ChannelIF;
import de.nava.informa.core.ParseException;
import de.nava.informa.impl.basic.ChannelBuilder;

import org.apache.commons.logging.LogFactory;

import java.io.InputStream;
import java.io.IOException;

/**
 * This class implements the <tt>RSSParser</tt> interface and defines an
 * adapter for the
 * {@link <a href="http://informa.sourceforge.net/">Informa</a>}
 * RSS Parser. Informa supports the
 * {@link <a href="http://backend.userland.com/rss091">0.91</a>}, 0.92,
 * {@link <a href="http://web.resource.org/rss/1.0/">1.0</a>} and
 * {@link <a href="http://blogs.law.harvard.edu/tech/rss">2.0</a>} RSS formats,
 * but does <i>not</i> support the
 * {@link <a href="http://www.atomenabled.org/developers/">Atom</a>} format.
 *
 * @see org.clapper.curn.parser.RSSParserFactory
 * @see org.clapper.curn.parser.RSSParser
 * @see RSSChannelAdapter
 *
 * @version <tt>$Revision$</tt>
 */
public class RSSParserAdapter implements RSSParser
{
    /*----------------------------------------------------------------------*\
                                Constructor
    \*----------------------------------------------------------------------*/

    /**
     * Default constructor.
     */
    public RSSParserAdapter()
    {
        // Disable Informa logging for now.

        LogFactory logFactory = LogFactory.getFactory();
        logFactory.setAttribute ("org.apache.commons.logging.Log",
                                 "org.apache.commons.logging.impl.NoOpLog");
    }

    /*----------------------------------------------------------------------*\
                              Public Methods
    \*----------------------------------------------------------------------*/

    /**
     * Parse an RSS feed.
     *
     * @param stream  the <tt>InputStream</tt> for the feed
     *
     * @return an <tt>RSSChannel</tt> object representing the RSS data from
     *         the site.
     *
     * @throws IOException        unable to read from URL
     * @throws RSSParserException unable to parse RSS XML
     */
    public RSSChannel parseRSSFeed (InputStream stream)
        throws IOException,
               RSSParserException
    {
        try
        {
            ChannelBuilder builder = new ChannelBuilder();
            ChannelIF      channel;

            channel = de.nava.informa.parsers.FeedParser.parse (builder,
                                                                stream);

            return new RSSChannelAdapter (channel);
        }

        catch (ParseException ex)
        {
            throw new RSSParserException (ex);
        }
    }
}
