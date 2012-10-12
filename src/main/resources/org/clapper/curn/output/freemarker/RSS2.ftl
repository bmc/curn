<#--
  -----------------------------------------------------------------------
  curn: Customizable Utilitarian RSS Notifier

  FreeMarker template used to generate RSS 2.0 output from a feed,
  with the curn SaveAsRSSPlugIn plug-in class.
  -----------------------------------------------------------------------
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
-->
<?xml version="1.0" encoding="${encoding}"?>
<!--
curn, ${curn.version} (build ${curn.buildID})
Generated ${dateGenerated?string("EEEEEE, dd MMMM, yyyy 'at' HH:mm:ss zzz")}
-->

<#-- Only works if the number of channels is 1. -->

<#if (channels?size > 1)>
<#stop "RSS2 Freemarker template only supports one feed at a time.">
</#if>

<#if (channels?size == 0)>
<#stop "RSS2 Freemarker template requires a feed.">
</#if>

<#assign channel = channels?first>

<rss version="2.0" xmlns:dc="http://purl.org/dc/elements/1.1/">
  <channel>
    <title>${escapeHTML(title)}</title>
    <link>${channel.url}</link>
    <description>${escapeHTML(channel.description)}</description>
    <#if channel.date?exists>
    <pubDate>${channel.date?string("EEE, dd MMM yyyy HH:mm:ss zzz")}</pubDate>
    <#else>
    <pubDate>${dateGenerated?string("EEE, dd MMM yyyy HH:mm:ss zzz")}</pubDate>
    </#if>
    <generator>curn, ${curn.version}</generator>

    <#list channel.items as item>
    <item>
      <title>${escapeHTML(item.title)}</title>
      <link>${escapeHTML(item.url)}</link>
      <#if item.date?exists>
      <pubDate>${item.date?string("EEE, dd MMM yyyy HH:mm:ss zzz")}</pubDate>
      <#else>
      <pubDate>${dateGenerated?string("EEE, dd MMM yyyy HH:mm:ss zzz")}</pubDate>
      </#if>
      <guid>${item.id}</guid>
      <description>${escapeHTML(item.description)}</description>
      <author>${escapeHTML(item.author)}</author>
    </item>
    </#list>
  </channel>
</rss>

