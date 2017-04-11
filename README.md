*curn*: Customizable Utilitarian RSS Notifier
=============================================

**WARNING**: I no longer use or maintain _curn_. It is, effectively,
[abadonware](https://en.wikipedia.org/wiki/Abandonware).

*curn* is an RSS reader. It scans a configured set of URLs, each one
representing an RSS feed, and summarizes the results. By default, *curn*
keeps track of individual items within each RSS feed, using an on-disk
cache; when using the cache, it will suppress displaying information for
items it has already processed.

Unlike many RSS readers, *curn* does not use a graphical user interface. It
is a command-line utility, intended to be run periodically in the
background by a command scheduler such as *cron*(8) (on Unix-like systems)
or the Windows Scheduler Service (on Windows).

*curn* can read RSS feeds from any URL that's supported by Java's runtime.
When querying HTTP sites, *curn* uses the HTTP `If-Modified-Since` and
`Last-Modified` headers to suppress retrieving and processing feeds that
haven't changed. By default, it also requests that the remote HTTP server
gzip the XML before sending it. (Some HTTP servers honor the request; some
don't.) These measures both minimize network bandwidth and ensure that
*curn* is as kind as possible to the remote RSS servers.

To download *curn*, or to read the User's Guide and other documentation,
please visit the [*curn* home page][].

*curn* is Copyright &copy; 2004-2012 Brian M. Clapper

[*curn* home page]: http://software.clapper.org/curn/
