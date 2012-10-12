<#--
  -----------------------------------------------------------------------
  curn: Customizable Utilitarian RSS Notifier

  FreeMarker template used to generate plain text output in conjunction
  with the curn FreeMarkerOutputHandler class.
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
${title}
<#if extraText != "">
${wrapText (extraText)}
</#if>

<#list channels as channel>
---------------------------------------------------------------------------
${wrapText (channel.title, 0)}
${channel.url}
<#if channel.date?exists>
${channel.date?string("E, dd MMM, yyyy 'at' HH:mm:ss")}
</#if>
<#if (channel.rssFormat?exists)>
${channel.rssFormat}
</#if>

<#list channel.items as item>
${wrapText (item.title, 4)}
<#if item.author?exists>
${indentText (item.author, 4)}
</#if>

${indentText (item.url, 4)}
<#assign desc = stripHTML(item.description)>
<#if desc != "">

${wrapText (desc, 8)}
</#if>

</#list>
</#list>

---------------------------------------------------------------------------
<#if (curn.showToolInfo)>
curn, ${curn.version} (build ${curn.buildID})
Generated ${dateGenerated?string("EEEEEE, dd MMMM, yyyy 'at' HH:mm:ss zzz")}
</#if>
