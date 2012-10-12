#!/bin/sh
#
# Front-end Unix shell script for the curn RSS reader
#
# $Id$
# ---------------------------------------------------------------------------
# This software is released under a BSD-style license:
#
# Copyright (c) 2004-2009 Brian M. Clapper. All rights reserved.
# Copyright (c) 2012 Basis Technology Corp.
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are
# met:
#
# 1. Redistributions of source code must retain the above copyright notice,
#    this list of conditions and the following disclaimer.
#
# 2. The end-user documentation included with the redistribution, if any,
#    must include the following acknowlegement:
#
#       "This product includes software developed by Brian M. Clapper
#       (bmc@clapper.org, http://www.clapper.org/bmc/). That software is
#       copyright (c) 2004-2009 Brian M. Clapper."
#
#    Alternately, this acknowlegement may appear in the software itself,
#    if wherever such third-party acknowlegements normally appear.
#
# 3. Neither the names "clapper.org", "curn" nor any of the names of the
#    project contributors may be used to endorse or promote products
#    derived from this software without prior written permission. For
#    written permission, please contact bmc@clapper.org.
#
# 4. Products derived from this software may not be called "curn" nor may
#    "clapper.org" appear in their names without prior written permission
#    of Brian M. Clapper.
#
# THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
# WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
# MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN
# NO EVENT SHALL BRIAN M. CLAPPER BE LIABLE FOR ANY DIRECT, INDIRECT,
# INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
# NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
# DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
# THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
# (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
# THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
# ---------------------------------------------------------------------------

vm_opts=
dev=false
while [ $# -gt 0 ]
do
    case "$1" in
        -D*|-X*)
            vm_opts="$vm_opts $1"
	    shift
	    ;;
        -dev)
            dev=true
            shift
            ;;
        -debug)
            vm_opts="$vm_opts -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=8000"
            shift
            ;;
        *)
	    break
	    ;;
    esac
done

if [ "$CURN_JAVA_VM_ARGS" != "" ]
then
    vm_opts="$vm_opts $CURN_JAVA_VM_ARGS"
fi

if [ $dev = "true" ] ; then
    INSTALL_PATH=target/fake_install
fi

exec $JAVA_HOME/bin/java \
-classpath \
$INSTALL_PATH/lib/curnboot.jar \
-ea \
-client \
$vm_opts \
-Dcurn.home=$INSTALL_PATH \
org.clapper.curn.Bootstrap \
$INSTALL_PATH/lib \
$INSTALL_PATH/plugins \
@user.home/curn/plugins \
@user.home/.curn/plugins \
@user.home/curn/lib \
@user.home/.curn/lib \
-- \
org.clapper.curn.Tool "${@}"
