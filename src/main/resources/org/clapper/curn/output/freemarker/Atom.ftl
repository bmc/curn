<#--
  -----------------------------------------------------------------------
  curn: Customizable Utilitarian RSS Notifier

  FreeMarker template used to generate ATOM output from a feed,
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
<#stop "Atom Freemarker template only supports one feed at a time.">
</#if>

<#if (channels?size == 0)>
<#stop "Atom Freemarker template requires a feed.">
</#if>

<#assign channel = channels?first>

<feed xmlns="http://www.w3.org/2005/Atom">

  <title>${escapeHTML(title)}</title>
  <link href="${escapeHTML(channel.url)}"/>
  <#if channel.date?exists>
  <updated>${channel.date?string("yyyy-MM-dd'T'HH:mm:ssZ")}</updated>
  <#else>
  <updated>${dateGenerated?string("yyyy-MM-dd'T'HH:mm:ssZ")}</updated>
  </#if>
  <#list channel.authors as author>
  <author>
    <name>${author}</name>
  </author>
  </#list>
  <id>${channel.id}</id>

  <#list channel.items as item>
  <entry>
    <title>${escapeHTML(item.title)}</title>
    <link href="${escapeHTML(item.url)}"/>
    <id>${escapeHTML(item.id)}</id>
    <#if item.date?exists>
    <updated>${item.date?string("yyyy-MM-dd'T'HH:mm:ssZ")}</updated>
    <#else>
    <updated>${dateGenerated?string("yyyy-MM-dd'T'HH:mm:ssZ")}</updated>
    </#if>
    <summary>${escapeHTML(item.description)}</summary>
    <#list item.authors as author>
    <author>
      <name>${escapeHTML(author)}</name>
    </author>
    </#list>
  </entry>
  </#list>

</feed>
