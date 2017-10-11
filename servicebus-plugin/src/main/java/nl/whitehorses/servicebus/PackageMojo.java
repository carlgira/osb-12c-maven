package nl.whitehorses.servicebus;

import nl.whitehorses.servicebus.configjar.ConfigJarSettings;
import nl.whitehorses.servicebus.configjar.ExportLevel;

import java.io.File;
import java.io.IOException;

import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import oracle.sb.maven.plugin.configjar.ConfigJarExec;

import org.apache.commons.lang.ArrayUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.xmlbeans.XmlException;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.xml.sax.SAXException;

/**
 * Implementation of {@link AbstractMojo}for packaging OSB projects to <i>sbar</i> archive. The 
 * {@link nl.whitehorses.servicebus.configjar.ConfigJarSettings} class will be used for generating the Configjar 
 * settings file. This is a custom implementation. The default Oracle {@link ConfigJarExec}class will be used to execute
 * the Configjar tool.
 */
@Mojo(name = "package", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true)
@Execute(goal = "package", phase = LifecyclePhase.PACKAGE)
public class PackageMojo extends AbstractMojo {

    //General settings
    private static final String SBAR_FILENAME = "sbconfig.sbar";

    //Resources file XPath expressions
    private static final String XPATH_INCLUDES = "/resources/includes/include";
    private static final String XPATH_EXCLUDES = "/resources/excludes/exclude";

    @Parameter(defaultValue = "${project}")
    private MavenProject project;

    @Parameter(property = "oracle.home", required = true)
    private File oracleHome;

    @Parameter(required = true)
    private String projectDir;

    @Parameter(defaultValue = "false")
    private boolean system;

    @Parameter(required = true)
    private ExportLevel exportLevel;

    @Parameter
    private String[] excludes;

    @Parameter
    private String[] includes;

    @Parameter(property = "deploy.file", required = false)
    private File resources;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        File outputDir = new File(this.project.getBuild().getDirectory());

        if (!outputDir.isAbsolute()) {
            outputDir = new File(this.project.getBasedir(), this.project.getBuild().getDirectory());
        }

        File artifactfile = new File(outputDir, SBAR_FILENAME);

        if(artifactfile.exists()){
            return;
        }

        try {
            new ConfigJarSettings().create(this.project, artifactfile, outputDir, system, exportLevel, getIncludes(),
                                           getExcludes(), this.projectDir);
        } catch (IOException ex) {
            throw new MojoExecutionException("IOException occurred while creating configjar settings file", ex);
        } catch (XmlException ex) {
            throw new MojoExecutionException("XmlException occurred while creating configjar settings file", ex);
        } catch (ParserConfigurationException ex) {
            throw new MojoExecutionException("ParserConfigurationException occurred while creating configjar settings file",
                                             ex);
        }

        try {
            if (new ConfigJarExec().execute(this.oracleHome, outputDir)) {
                this.project.getArtifact().setFile(artifactfile);
            } else {
                throw new MojoFailureException("Failed to create sbar archive.");
            }
        } catch (Exception ex) {
            throw new MojoExecutionException("Exception occurred while executing configjar.", ex);
        }
    }

    /**
     * Get resource entries from {@link resources} file using XPath expression.
     *
     * @param xpath XPath expression.
     * @return Resource entries from {@link resources} file.
     * @throws MojoExecutionException
     */
    private String[] getResourceEntriesFromFile(String xpath) throws MojoExecutionException {
        List<String> resourceEntries = new ArrayList<String>();

        if (resources != null) {
            try {
                DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder = builderFactory.newDocumentBuilder();
                Document document = builder.parse(resources);

                XPath xpathBuilder = XPathFactory.newInstance().newXPath();
                NodeList nodeList = (NodeList) xpathBuilder.compile(xpath).evaluate(document, XPathConstants.NODESET);

                for (int i = 0; nodeList != null && i < nodeList.getLength(); i++) {
                    Node node = nodeList.item(i);
                    if (node.getNodeType() == Node.ELEMENT_NODE) {
                        resourceEntries.add(node.getFirstChild().getNodeValue());
                    }
                }
            } catch (ParserConfigurationException ex) {
                throw new MojoExecutionException("Failed to parse resources file.", ex);
            } catch (IOException ex) {
                throw new MojoExecutionException("Failed to parse resources file.", ex);
            } catch (SAXException ex) {
                throw new MojoExecutionException("Failed to parse resources file.", ex);
            } catch (XPathExpressionException ex) {
                throw new MojoExecutionException("Failed to parse resources file.", ex);
            }
        }

        return resourceEntries.toArray(new String[resourceEntries.size()]);
    }

    /**
     * Get all files to include in the archive. If both the {@link includes} parameter and the {@link resources}
     * parameter is used in the pom.xml file, both lists will be included in the archive.
     *
     * @return All files to include in the archive.
     * @throws MojoExecutionException
     */
    private String[] getIncludes() throws MojoExecutionException {
        String[] includesFromResources = getResourceEntriesFromFile(XPATH_INCLUDES);
        return (String[]) ArrayUtils.addAll(includesFromResources, includes);
    }

    /**
     * Get all files to exclude the archive. If both the {@link excludes} parameter and the {@link resources}
     * parameter is used in the pom.xml file, both lists will be excluded from the archive.
     *
     * @return All files to exclude from the archive.
     * @throws MojoExecutionException
     */
    private String[] getExcludes() throws MojoExecutionException {
        String[] excludesFromResources = getResourceEntriesFromFile(XPATH_EXCLUDES);
        return (String[]) ArrayUtils.addAll(excludesFromResources, excludes);
    }
}
