/*---------------------------------------------------------------------------*\
  $Id$
\*---------------------------------------------------------------------------*/

package org.clapper.curn.htmloutput;

import org.clapper.curn.OutputHandler;
import org.clapper.curn.CurnException;
import org.clapper.curn.Util;
import org.clapper.curn.Version;
import org.clapper.curn.FeedInfo;
import org.clapper.curn.ConfigFile;
import org.clapper.curn.parser.RSSChannel;
import org.clapper.curn.parser.RSSItem;

import org.clapper.util.config.ConfigurationException;
import org.clapper.util.config.NoSuchSectionException;

import org.clapper.util.text.Unicode;
import org.clapper.util.text.TextUtils;

import org.clapper.util.misc.Logger;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.FileWriter;
import java.io.File;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

import java.util.Date;
import java.util.Collection;
import java.util.Iterator;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

import java.net.URL;

import org.w3c.dom.Node;

import org.w3c.dom.html.HTMLTableRowElement;
import org.w3c.dom.html.HTMLTableCellElement;
import org.w3c.dom.html.HTMLAnchorElement;

/**
 * Provides an output handler that produces HTML output.
 *
 * @see OutputHandler
 * @see org.clapper.curn.curn
 * @see org.clapper.curn.parser.RSSChannel
 *
 * @version <tt>$Revision$</tt>
 */
public class HTMLOutputHandler implements OutputHandler
{
    /*----------------------------------------------------------------------*\
                             Private Constants
    \*----------------------------------------------------------------------*/

    private static final DateFormat OUTPUT_DATE_FORMAT =
                             new SimpleDateFormat ("dd MMM, yyyy, HH:mm:ss");

    /*----------------------------------------------------------------------*\
                           Private Instance Data
    \*----------------------------------------------------------------------*/

    private PrintWriter           out                 = null;
    private File                  outputFile          = null;
    private RSSOutputHTML         doc                 = null;
    private String                oddItemRowClass     = null;
    private String                oddChannelClass     = null;
    private int                   rowCount            = 0;
    private int                   channelCount        = 0;
    private ConfigFile            config              = null;
    private HTMLTableRowElement   channelRow          = null;
    private HTMLTableRowElement   channelSeparatorRow = null;
    private Node                  channelRowParent    = null;
    private HTMLAnchorElement     itemAnchor          = null;
    private HTMLTableCellElement  itemTitleTD         = null;
    private HTMLTableCellElement  itemDescTD          = null;
    private HTMLTableCellElement  channelTD           = null;
    private boolean               saveOnly            = false;

    /**
     * For logging
     */
    private static Logger log = new Logger (HTMLOutputHandler.class);

    /*----------------------------------------------------------------------*\
                                Constructor
    \*----------------------------------------------------------------------*/

    /**
     * Construct a new <tt>HTMLOutputHandler</tt>.
     */
    public HTMLOutputHandler()
    {
    }

    /*----------------------------------------------------------------------*\
                              Public Methods
    \*----------------------------------------------------------------------*/

    /**
     * Initializes the output handler for another set of RSS channels.
     *
     * @param config  the parsed <i>curn</i> configuration data
     *
     * @throws ConfigurationException  configuration error
     * @throws CurnException           some other initialization error
     */
    public void init (ConfigFile config)
        throws ConfigurationException,
               CurnException
    {
        this.doc                 = new RSSOutputHTML();
        this.config              = config;
        this.oddChannelClass     = doc.getElementChannelTD().getClassName();
        this.oddItemRowClass     = doc.getElementItemTitleTD().getClassName();
        this.channelRow          = doc.getElementChannelRow();
        this.channelSeparatorRow = doc.getElementChannelSeparatorRow();
        this.channelRowParent    = channelRow.getParentNode();
        this.itemAnchor          = doc.getElementItemAnchor();
        this.itemTitleTD         = doc.getElementItemTitleTD();
        this.itemDescTD          = doc.getElementItemDescTD();
        this.channelTD           = doc.getElementChannelTD();

        String saveAs = null;

        try
        {
            String sectionName;

            sectionName = config.getOutputHandlerSectionName (this.getClass());
            if (sectionName != null)
            {
                saveAs = config.getOptionalStringValue (sectionName,
                                                        "SaveAs",
                                                        null);
                saveOnly = config.getOptionalBooleanValue (sectionName,
                                                           "SaveOnly",
                                                           false);

                if (saveOnly && (saveAs == null))
                {
                    throw new ConfigurationException (sectionName,
                                                      "SaveOnly can only be "
                                                    + "specified if SaveAs "
                                                    + "is defined.");
                }
            }
        }

        catch (NoSuchSectionException ex)
        {
            throw new ConfigurationException (ex);
        }

        if (saveAs != null)
            outputFile = new File (saveAs);

        else
        {
            try
            {
                outputFile = File.createTempFile ("curn", "txt");
                outputFile.deleteOnExit();
            }

            catch (IOException ex)
            {
                throw new CurnException ("Can't create temporary file.");
            }
        }

        try
        {
            log.debug ("Opening output file \"" + outputFile + "\"");
            this.out = new PrintWriter (new FileWriter (outputFile));
        }

        catch (IOException ex)
        {
            throw new CurnException ("Can't open file \""
                                   + outputFile
                                   + "\" for output",
                                     ex);
        }
    }

    /**
     * Display the list of <tt>RSSItem</tt> news items to whatever output
     * is defined for the underlying class.
     *
     * @param channel  The channel containing the items to emit. The method
     *                 should emit all the items in the channel; the caller
     *                 is responsible for clearing out any items that should
     *                 not be seen.
     * @param feedInfo Information about the feed, from the configuration
     *
     * @throws CurnException  unable to write output
     */
    public void displayChannel (RSSChannel  channel,
                                FeedInfo    feedInfo)
        throws CurnException
    {
        Collection items = channel.getItems();

        if (items.size() == 0)
            return;

        int       i = 0;
        Iterator  it;
        Date      date;

        channelCount++;

        // Insert separator row first.

        channelRowParent.insertBefore (channelSeparatorRow.cloneNode (true),
                                       channelSeparatorRow);
        // Do the rows of output.

        doc.getElementChannelDate().removeAttribute ("id");
        doc.getElementItemDate().removeAttribute ("id");

        for (i = 0, it = items.iterator(); it.hasNext(); i++, rowCount++)
        {
            RSSItem item = (RSSItem) it.next();

            if (i == 0)
            {
                // First row in channel has channel title and link.

                doc.setTextChannelTitle (channel.getTitle());

                date = null;
                if (config.showDates())
                    date = channel.getPublicationDate();
                if (date != null)
                    doc.setTextChannelDate (OUTPUT_DATE_FORMAT.format (date));
                else
                    doc.setTextChannelDate ("");

                itemAnchor.setHref (item.getLink().toExternalForm());
            }

            else
            {
                doc.setTextChannelTitle ("");
                doc.setTextChannelDate ("");
                itemAnchor.setHref ("");
            }

            String title = item.getTitle();
            doc.setTextItemTitle ((title == null) ? "(No Title)" : title);

            String desc = null;
            if (! feedInfo.summarizeOnly())
            {
                desc = item.getSummary();
                if (TextUtils.stringIsEmpty (desc))
                {
                    // Hack for feeds that have no summary but have
                    // content. If the content is small enough, use it as
                    // the summary.

                    desc = item.getFirstContentOfType (new String[]
                                                       {
                                                           "text/plain",
                                                           "text/html"
                                                       });
                    if (! TextUtils.stringIsEmpty (desc))
                    {
                        desc = desc.trim();
                        if (desc.length() > CONTENT_AS_SUMMARY_MAXSIZE)
                            desc = null;
                    }
                }
            }

            else
            {
                if (TextUtils.stringIsEmpty (desc))
                    desc = null;
                else
                    desc = desc.trim();
            }

            if (desc == null)
                desc = String.valueOf (Unicode.NBSP);

            doc.setTextItemDescription (desc);

            itemAnchor.setHref (item.getLink().toExternalForm());

            date = null;
            if (config.showDates())
                date = item.getPublicationDate();
            if (date != null)
                doc.setTextItemDate (OUTPUT_DATE_FORMAT.format (date));
            else
                doc.setTextItemDate ("");

            itemTitleTD.removeAttribute ("class");
            itemDescTD.removeAttribute ("class");

            if ((rowCount % 2) == 1)
            {
                // Want to use the "odd row" class to distinguish the
                // rows. For the description, though, only do that if
                // if's not empty.

                itemTitleTD.setAttribute ("class", oddItemRowClass);

                if (desc != null)
                    itemDescTD.setAttribute ("class", oddItemRowClass);
            }

            if ((channelCount % 2) == 1)
                channelTD.setClassName (oddChannelClass);
            else
                channelTD.setClassName ("");

            channelRowParent.insertBefore (channelRow.cloneNode (true),
                                           channelSeparatorRow);
        }

    }
    
    /**
     * Flush any buffered-up output.
     *
     * @throws CurnException  unable to write output
     */
    public void flush() throws CurnException
    {
        // Remove the cloneable row.

        removeElement (doc.getElementChannelRow());

        // Add configuration info, if available.

        doc.setTextVersion (Version.VERSION);

        URL configFileURL = config.getConfigurationFileURL();
        if (configFileURL == null)
            removeElement (doc.getElementConfigFileRow());
        else
            doc.setTextConfigURL (configFileURL.toString());

        // Write the document.

        log.debug ("Generating HTML");

        out.print (doc.toDocument());
        out.flush();

        // Kill the document.

        doc = null;
    }

    /**
     * Get the content (i.e., MIME) type for output produced by this output
     * handler.
     *
     * @return the content type
     */
    public String getContentType()
    {
        return "text/html";
    }

    /**
     * Get an <tt>InputStream</tt> that can be used to read the output data
     * produced by the handler, if applicable.
     *
     * @return an open input stream, or null if no suitable output was produced
     *
     * @throws CurnException an error occurred
     */
    public InputStream getGeneratedOutput()
        throws CurnException
    {
        InputStream result = null;

        if (hasGeneratedOutput())
        {
            try
            {
                result = new FileInputStream (outputFile);
            }

            catch (FileNotFoundException ex)
            {
                throw new CurnException ("Can't re-open file \""
                                       + outputFile
                                       + "\"",
                                         ex);
            }
        }

        return result;
    }

    /**
     * Determine whether this handler has produced any actual output (i.e.,
     * whether {@link #getGeneratedOutput()} will return a non-null
     * <tt>InputStream</tt> if called).
     *
     * @return <tt>true</tt> if the handler has produced output,
     *         <tt>false</tt> if not
     *
     * @see #getGeneratedOutput
     * @see #getContentType
     */
    public boolean hasGeneratedOutput()
    {
        return (! saveOnly) &&
               (outputFile != null) &&
               (outputFile.length() > 0);
    }

    /*----------------------------------------------------------------------*\
                              Private Methods
    \*----------------------------------------------------------------------*/

    /**
     * Remove an element from the document.
     *
     * @param element the <tt>Node</tt> representing the element in the DOM
     */
    private void removeElement (Node element)
    {
        Node parentNode = element.getParentNode();

        if (parentNode != null)
            parentNode.removeChild (element);
    }
}
