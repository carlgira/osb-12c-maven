package nl.whitehorses.servicebus.configjar;

import java.io.File;

import java.io.IOException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.maven.project.MavenProject;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Class to generate ConfigJar settings file for generating <i>sbar</i> archive.
 */
public class ConfigJarSettings {

    //Configjar file namespace
    private static final String NS_CONFIGJAR = "http://www.bea.com/alsb/tools/configjar/config";

    //Configjar file elements
    private static final String ELEMENT_CONFIGJAR_SETTINGS = "configjarSettings";
    private static final String ELEMENT_SOURCE = "source";
    private static final String ELEMENT_SYSTEM = "system";
    private static final String ELEMENT_PROJECT = "project";
    private static final String ELEMENT_FILESET = "fileset";
    private static final String ELEMENT_INCLUDE = "include";
    private static final String ELEMENT_EXCLUDE = "exclude";
    private static final String ELEMENT_CONFIGJAR = "configjar";
    private static final String ELEMENT_RESOURCE_LEVEL = "resourceLevel";
    private static final String ELEMENT_PROJECT_LEVEL = "projectLevel";

    //Configjar file element attributes
    private static final String ATTRIBUTE_DIR = "dir";
    private static final String ATTRIBUTE_NAME = "name";
    private static final String ATTRIBUTE_JAR = "jar";
    private static final String ATTRIBUTE_INCLUDE_DEPENDENCIES = "includeDependencies";

    //General settings
    private static final String DEFAULT_FILENAME = "configjar/settings.xml";
    private static final String[] DEFAULT_PROJECT_EXCLUDES = new String[] {
        "*/overview.xml", "/pom.xml", "*/.settings/**", "*/.data/**"
    };
    private static final String[] DEFAULT_SYSTEM_EXCLUDES = new String[] { "/pom.xml", "*/.data/**" };

    /**
     * Method to generate the Configjar settings file.
     *
     * @param project Maven project.
     * @param sbarFile {@link File} instance of the sbar file location.
     * @param outputDir {@link File} instance of the output directory for the Configjar settings file.
     * @param system True if Configjar settings file should be created with system elements. Otherwise project elements
     * will be used in the settings file.
     * @param exportLevel Export level, project or resource.
     * @param includes String array containing all files which have to be included in the sbar archive.
     * @param excludes String array containing all files which have to be excluded from the sbar archive.
     * @throws ParserConfigurationException
     * @throws XmlException
     * @throws IOException
     */
    public void create(MavenProject project, File sbarFile, File outputDir, boolean system, ExportLevel exportLevel,
                       String[] includes, String[] excludes, String projectDir) throws ParserConfigurationException, XmlException,
                                                                    IOException {
        String projectName = project.getBasedir().getName();
        //String projectDir = project.getBasedir().toString();
        String sbarLocation = sbarFile.toString();
        String[] combinedExcludes = excludes;

        if (system) {
            combinedExcludes = (String[]) ArrayUtils.addAll(excludes, DEFAULT_SYSTEM_EXCLUDES);
        } else {
            combinedExcludes = (String[]) ArrayUtils.addAll(excludes, DEFAULT_PROJECT_EXCLUDES);
        }

        File settingsFile = new File(outputDir, DEFAULT_FILENAME);
        XmlObject settings =
            constructConfigjarXML(exportLevel, system, projectName, projectDir, sbarLocation, includes,
                                  combinedExcludes);
        FileUtils.copyInputStreamToFile(settings.newInputStream(), settingsFile);
    }

    /**
     * Construct content of the Configjar settings file.
     *
     * @param exportLevel Export level, project or resource.
     * @param system True if Configjar settings file should be created with system elements. Otherwise project elements
     * will be used in the settings file.
     * @param projectName Maven project name.
     * @param projectDir Maven project base directory.
     * @param sbarLocation Location of te sbar archive file.
     * @param includes String array containing all files which have to be included in the sbar archive.
     * @param excludes String array containing all files which have to be excluded from the sbar archive.
     * @return Content of the Configjar settings file.
     * @throws ParserConfigurationException
     * @throws XmlException
     */
    private XmlObject constructConfigjarXML(ExportLevel exportLevel, boolean system, String projectName,
                                            String projectDir, String sbarLocation, String[] includes,
                                            String[] excludes) throws ParserConfigurationException, XmlException {
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

        Document doc = docBuilder.newDocument();
        Element configjarSettingsElement = doc.createElementNS(NS_CONFIGJAR, ELEMENT_CONFIGJAR_SETTINGS);
        doc.appendChild(configjarSettingsElement);

        Element sourceElement = doc.createElementNS(NS_CONFIGJAR, ELEMENT_SOURCE);
        configjarSettingsElement.appendChild(sourceElement);

        Element projectElement = doc.createElementNS(NS_CONFIGJAR, system ? ELEMENT_SYSTEM : ELEMENT_PROJECT);
        projectElement.setAttribute(ATTRIBUTE_DIR, projectDir);
        sourceElement.appendChild(projectElement);

        Element filesetElement = doc.createElementNS(NS_CONFIGJAR, ELEMENT_FILESET);
        sourceElement.appendChild(filesetElement);

        for (String include : includes) {
            Element includeElement = doc.createElementNS(NS_CONFIGJAR, ELEMENT_INCLUDE);
            includeElement.setAttribute(ATTRIBUTE_NAME, include);
            filesetElement.appendChild(includeElement);
        }

        for (String exclude : excludes) {
            Element excludeElement = doc.createElementNS(NS_CONFIGJAR, ELEMENT_EXCLUDE);
            excludeElement.setAttribute(ATTRIBUTE_NAME, exclude);
            filesetElement.appendChild(excludeElement);
        }

        Element configjarElement = doc.createElementNS(NS_CONFIGJAR, ELEMENT_CONFIGJAR);
        configjarElement.setAttribute(ATTRIBUTE_JAR, sbarLocation);
        configjarSettingsElement.appendChild(configjarElement);

        if (system || exportLevel == ExportLevel.RESOURCE) {
            Element resourceLevelElement = doc.createElementNS(NS_CONFIGJAR, ELEMENT_RESOURCE_LEVEL);
            resourceLevelElement.setAttribute(ATTRIBUTE_INCLUDE_DEPENDENCIES, "false");
            configjarElement.appendChild(resourceLevelElement);
        } else {
            Element projectLevelElement = doc.createElementNS(NS_CONFIGJAR, ELEMENT_PROJECT_LEVEL);
            configjarElement.appendChild(projectLevelElement);

            Element projectLevelProjectElement = doc.createElementNS(NS_CONFIGJAR, ELEMENT_PROJECT);
            projectLevelProjectElement.setTextContent(projectName);
            projectLevelElement.appendChild(projectLevelProjectElement);
        }

        return XmlObject.Factory.parse(doc.getChildNodes().item(0));
    }
}

