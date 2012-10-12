#                                                                  -*- ruby -*-
# Buildr Buildfile for curn
#
# To build curn, you'll need Apache Buildr.
# ---------------------------------------------------------------------------

# Environment variables

ENV['USER'] ||= 'bmc'
ENV['HOME'] ||= "/home/#{ENV['USER']}"
ENV['IZPACK_HOME'] ||= "#{ENV['HOME']}/java/IzPack"

# Constants

CURN_VERSION          = '3.2.9'
GROUP                 = 'org.clapper'
CURN_JAR_NAME         = 'curn'
CURN_BOOT_JAR_NAME    = 'curn-boot'
CURN_PLUGINS_JAR_NAME = 'curn-plugins'
API_DOC_TARGET        = '../gh-pages/api'
CHANGELOG_TARGET      = '../gh-pages/CHANGELOG.txt'
TOP                   = File.expand_path(".")
SRC                   = File.expand_path(TOP, 'src')
INSTALLER_SRC         = File.expand_path(SRC, 'installer')
TARGET                = File.expand_path(TOP, 'target')

# Dependencies.
ASM_VERSION       = '3.3.1'
 
JAVAUTIL          = 'org.clapper:javautil:jar:3.0.1'
FREEMARKER        = 'org.freemarker:freemarker:jar:2.3.19'
JAVAMAIL          = 'javax.mail:mail:jar:1.4.4'
JDOM              = 'org.jdom:jdom:jar:1.1'
COMMONS_LOGGING   = 'commons-logging:commons-logging:jar:1.1.1'
ROME              = 'rome:rome:jar:1.0'
ASM               = "asm:asm:jar:#{ASM_VERSION}"
ASM_COMMONS       = "asm:asm-commons:jar:#{ASM_VERSION}"
COMMONS_IO        = "commons-io:commons-io:jar:2.4"
COMPILE_ARTIFACTS = [ASM, ASM_COMMONS, COMMONS_LOGGING, FREEMARKER, JAVAMAIL,
                    JAVAUTIL, JDOM, ROME, COMMONS_IO]
IZPACK_VERSION    = '4.3.5'
IZPACK            = "org.codehaus.izpack:izpack-standalone-compiler:jar:#{IZPACK_VERSION}"

# Some local tasks and task aliases
Project.local_task :installer
Project.local_task :copydoc
Project.local_task :devinstall

define 'curn' do
  project.version = CURN_VERSION
  project.group   = GROUP

  # Repos
  repositories.remote << 'http://repo1.maven.org/maven2/'
  repositories.remote << 'http://maven.clapper.org/'

  # Compilation
  compile.using :target => '1.6', :lint => 'all', :deprecation => true
  compile.with ASM, ASM_COMMONS, COMMONS_LOGGING, FREEMARKER, JAVAMAIL,
               JAVAUTIL, JDOM, ROME, COMMONS_IO
  # Main jar
  package(:jar, :id => CURN_JAR_NAME).
    exclude(_('target/classes/**/Bootstrap*.class')).
    exclude(_('target/classes/**/plugins/*.class')).
    exclude(_('target/resources/**/*.ftl'))

  # Plugins jar
  package(:jar, :id => CURN_PLUGINS_JAR_NAME).
    clean.
    include('target/classes/org/clapper/curn/plugins',
            :as => 'org/clapper/curn/plugins').
    include('target/resources/org/clapper/curn/output/freemarker',
            :as => 'org/clapper/curn/output/freemarker')

  # Bootstrap jar
  package(:jar, :id => CURN_BOOT_JAR_NAME).
    clean.
    include('target/classes/org/clapper/curn/Bootstrap.class',
            :as => 'org/clapper/curn/Bootstrap.class').
    include('target/classes/org/clapper/curn/BootstrapException.class',
            :as => 'org/clapper/curn/BootstrapException.class')

  PROPERTY_FILE_VARIABLES = {
    'version'   => project.version,
    'name'      => name,
    'copyright' => 'Copyright (c) 2004-2011 Brian M. Clapper.'
  }

  resources.filter.using :maven, PROPERTY_FILE_VARIABLES

  task :copydoc => :doc do
    rm_rf API_DOC_TARGET
    cp_r 'target/doc', API_DOC_TARGET
    cp 'docs/CHANGELOG', CHANGELOG_TARGET
  end

  # ----------------------------------------------------------------------
  # The IzPack installer
  # ----------------------------------------------------------------------

  # Create the installer jar.
  #
  # NOTE: Assumes IzPack is installed in a location specified by environment
  # variable $IZPACK_HOME
  task :installer => [:package, :doc, :installerxml] do
    require 'tempfile'

    # Verify installation of IzPack.
    IZPACK_HOME = ENV['IZPACK_HOME']
    puts IZPACK_HOME
    if !File.exists? IZPACK_HOME
      raise Exception.new "IZPACK_HOME #{IZPACK_HOME} doesn't exist."
    elsif !File.directory? IZPACK_HOME
      raise Exception.new "IZPACK_HOME #{IZPACK_HOME} is not a directory."
    end

    # Use BuildrIzPack, but only to launch IzPack. Its DSL isn't complete
    # and requires building custom XML for more complex options. It's easier
    # to use a template XML installer and edit it on the fly.
    # Create the installation temporary directory.

    Dir.mktmpdir do |tmp_dir|

      # Copy the dependent jars there.
      deps = []
      compile.dependencies.each do |d|
        cp d.to_s, tmp_dir
        deps << File.basename(d.to_s)
      end

      # Filter the template install.xml file into one with the variables
      # substituted

      filter('src/installer').
        into('target').
        include('*.xml').
        using('CURN_VERSION'      => version,
              'INSTALL_TMP'       => tmp_dir,
              'TOP_DIR'           => TOP,
              'SRC_INSTALL'       => "#{TOP}/src/installer",
              'RELEASE_DIR'       => "#{TOP}/target",
              'CURN_JAR_FILE'     => "#{CURN_JAR_NAME}-#{version}.jar",
              'CURNBOOT_JAR_FILE' => "#{CURN_BOOT_JAR_NAME}-#{version}.jar",
              'PLUGINS_JAR_FILE'  => "#{CURN_PLUGINS_JAR_NAME}-#{version}.jar",
              'ASM_VERSION'       => ASM_VERSION,
              'DOCS_DIR'          => _('docs'),
              'DEP_JAR_DIR'       => tmp_dir,
              'JAVADOCS_DIR'      => _('target/doc')).
        run

      # Generate the installer.
      cmd = "#{IZPACK_HOME}/bin/compile target/install.xml -b #{TOP} " +
            "-h #{IZPACK_HOME} -o target/curn-installer-#{version}.jar"
      puts "+ #{cmd}"
      sh cmd
    end
  end

  # Create the installer XML from the XML template. NOTE: IzPack must
  # already be installed, and its home directory must be specified via the
  # IZPACK_HOME environment variable.
  task :installerxml do
  end

  task :devinstall => :compile do
      FileUtils.mkdir_p "target/fake_install/lib"
      FileUtils.mkdir_p "target/fake_install/plugins"
      cp "target/#{CURN_JAR_NAME}-#{version}.jar", 'target/fake_install/lib/curn.jar'
      cp "target/#{CURN_BOOT_JAR_NAME}-#{version}.jar", 'target/fake_install/lib/curnboot.jar'
      cp "target/#{CURN_PLUGINS_JAR_NAME}-#{version}.jar", 'target/fake_install/plugins/curn-plugins.jar'
      compile.dependencies.each do |d|
        cp d.to_s, 'target/fake_install/lib'
      end
  end
end


