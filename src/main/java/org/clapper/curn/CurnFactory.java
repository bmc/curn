/*---------------------------------------------------------------------------*\
  This software is released under a BSD license, adapted from
  <http://opensource.org/licenses/bsd-license.php>

  Copyright &copy; 2004-2012 Brian M. Clapper.
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

import org.clapper.util.io.WordWrapWriter;
import org.clapper.util.logging.Logger;

/**
 * <p>This static singleton class is used to allocate a new {@link Curn}
 * object for RSS processing. Hiding the allocation behind a factory allows
 * for various bootstrap activities, including the installation and use of
 * a different class loader that adds the plug-in jars and directories
 * to the load path at runtime.</p>
 *
 * @version <tt>$Revision$</tt>
 */
public class CurnFactory
{
    /*----------------------------------------------------------------------*\
                            Private Data Items
    \*----------------------------------------------------------------------*/

    /**
     * For log messages
     */
    private static Logger log = new Logger (CurnFactory.class); // NOPMD

    /*----------------------------------------------------------------------*\
                                Constructor
    \*----------------------------------------------------------------------*/

    /**
     * Cannot be instantiated.
     */
    private CurnFactory()
    {
    }

    /*----------------------------------------------------------------------*\
                              Public Methods
    \*----------------------------------------------------------------------*/

    /**
     * Create a new {@link Curn} object. The resulting object will be
     * loaded via a different class loader. This method also implicitly
     * loads the plug-ins.
     *
     * @return the <tt>Curn</tt> object
     *
     * @throws CurnException on error
     */
    public static Curn newCurn()
        throws CurnException
    {
        // Load the plug-ins.

        PlugInManager.loadPlugIns();

        return new Curn(new WordWrapWriter(System.err));
    }
}
