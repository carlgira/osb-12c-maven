package nl.whitehorses.servicebus.archiver;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;

import java.text.SimpleDateFormat;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.io.IOUtils;

import org.codehaus.plexus.archiver.AbstractArchiver;
import org.codehaus.plexus.archiver.ArchiveEntry;
import org.codehaus.plexus.archiver.Archiver;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.ResourceIterator;
import org.codehaus.plexus.archiver.util.ResourceUtils;
import org.codehaus.plexus.components.io.resources.PlexusIoResource;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.xml.sax.SAXException;

/**
 * Implementation of the {@link AbstractArchiver} class. This class can be used for archiving <i>sbar</i> files.
 * All files will be added to the <i>sbar</i> archive. If multiple <i>ExportInfo</i> files are provided, these files
 * will be merged into one single <i>ExportInfo</i> file.
 */
public class SbarArchiver extends AbstractArchiver {

    //XML namespace
    private static final String NS_XMLNS = "http://www.w3.org/2000/xmlns/";
    private static final String NS_XMLNS_PREFIX = "xmlns";

    //ExportInfo file namespace
    private static final String NS_IMPORT_EXPORT = "http://www.bea.com/wli/config/importexport";
    private static final String NS_IMPORT_EXPORT_PREFIX = "imp";

    //ExportInfo file elements
    private static final String ELEMENT_XML_FRAGMENT = "xml-fragment";
    private static final String ELEMENT_PROPERTIES = "properties";
    private static final String ELEMENT_PROPERTY = "property";

    //ExportInfo file element attributes
    private static final String ATTRIBUTE_NAME = "name";
    private static final String ATTRIBUTE_VALUE = "value";
    private static final String ATTRIBUTE_VERSION = "version";
    private static final String ATTRIBUTE_VERSION_VALUE = "v2";
    private static final String ATTRIBUTE_USERNAME = "username";
    private static final String ATTRIBUTE_DESCRIPTION = "description";
    private static final String ATTRIBUTE_EXPORTTIME = "exporttime";
    private static final String ATTRIBUTE_EXPORTTIME_FORMAT = "EEE MMM dd HH:mm:ss z yyyy";
    private static final String ATTRIBUTE_PRODUCTNAME = "productname";
    private static final String ATTRIBUTE_PRODUCTVERSION = "productversion";
    private static final String ATTRIBUTE_PROJECT_LEVEL_EXPORT = "projectLevelExport";

    //General settings
    private static final int BUFFER_SIZE = 1024;
    private static final String ARCHIVE_TYPE = "sbar";
    private static final String FILENAME_EXPORT_INFO = "ExportInfo";
    private static final String DEFAULT_ENCODING = "UTF8";

    //Output streams
    private FileOutputStream fileOutputStream;
    private BufferedOutputStream bufferedOutputStream;
    private ZipOutputStream sbarOutputSteam;

    //List containing all export info files which have to be merged into one
    private List<Document> exportInfoFiles = new ArrayList<Document>();

    /**
     * Default constructor.
     */
    public SbarArchiver() {
        super();
        setDuplicateBehavior(Archiver.DUPLICATES_ADD);
    }

    @Override
    protected String getArchiveType() {
        return ARCHIVE_TYPE;
    }


    @Override
    protected void execute() throws IOException, ArchiverException {
        ResourceIterator resources = getResources();

        if (!resources.hasNext() && !hasVirtualFiles()) {
            throw new ArchiverException("You must set at least one file.");
        }

        getLogger().info("Writing [" + getArchiveType() + "] archive...");

        fileOutputStream = new FileOutputStream(getDestFile());
        bufferedOutputStream = new BufferedOutputStream(fileOutputStream);
        sbarOutputSteam = new ZipOutputStream(bufferedOutputStream);

        while (resources.hasNext()) {
            ArchiveEntry entry = resources.next();

            if (entry.getResource().isFile()) {
                addFile(entry);
            }
        }

        addExportInfoFile();

        getLogger().info("Archive completed: [" + getDestFile() + "].");
    }

    @Override
    protected void close() throws IOException {
        IOUtils.closeQuietly(sbarOutputSteam);
        IOUtils.closeQuietly(bufferedOutputStream);
        IOUtils.closeQuietly(fileOutputStream);
    }

    /**
     * Add file to archive. If provided {@link ArchiveEntry} is the <i>ExportInfo</i> file, this fill will not (yet) be
     * added to the archive, but will be locally stored in memory. Use {@link #addExportInfoFile()}, after adding all
     * files to the archive, to add the <i>ExportInfo</i> file.
     *
     * @param entry File to add to the archive as {@link ArchiveEntry}.
     * @throws ArchiverException
     */
    private void addFile(ArchiveEntry entry) throws ArchiverException {
        PlexusIoResource resource = entry.getResource();

        if (ResourceUtils.isSame(resource, getDestFile())) {
            throw new ArchiverException("A sbar file cannot include itself");
        }

        if (FILENAME_EXPORT_INFO.equalsIgnoreCase(entry.getName())) {
            addContentToExportInfo(entry);
        } else {
            getLogger().debug("Adding file [" + entry.getName() + "] to archive");

            try {
                addFileToArchive(resource.getName(), resource.getContents());
            } catch (IOException ex) {
                throw new ArchiverException("ArchiverException occurred while adding file to archive", ex);
            }
        }
    }

    /**
     * Locally store <i>ExportInfo</i> file. This has to be done so all <i>ExportInfo</i> files can be merged to add
     * one single <i>ExportInfo</i> file to the archive.
     *
     * @param entry <i>ExportInfo</i> file to add to the archive as {@link ArchiveEntry}.
     * @throws ArchiverException
     */
    private void addContentToExportInfo(ArchiveEntry entry) throws ArchiverException {
        InputStream inputStream = null;
        StringWriter writer = new StringWriter();

        try {
            IOUtils.copy(entry.getInputStream(), writer, DEFAULT_ENCODING);

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            Document xmlDocument = docBuilder.parse(new ByteArrayInputStream(writer.toString().getBytes()));

            exportInfoFiles.add(xmlDocument);
        } catch (IOException ex) {
            throw new ArchiverException("IOException occurred while reading ExportInfo file", ex);
        } catch (ParserConfigurationException ex) {
            throw new ArchiverException("ParserConfigurationException occurred while parsing ExportInfo file", ex);
        } catch (SAXException ex) {
            throw new ArchiverException("SAXException occurred while parsing ExportInfo file", ex);
        } finally {
            IOUtils.closeQuietly(inputStream);
            IOUtils.closeQuietly(writer);
        }
    }

    /**
     * Add file to archive.
     *
     * @param fileName Filename of the file.
     * @param inputStream {@link InputStream} with content of the file.
     * @throws ArchiverException
     */
    @SuppressWarnings("oracle.jdeveloper.java.nested-assignment")
    private void addFileToArchive(String fileName, InputStream inputStream) throws ArchiverException {
        try {
            int length;
            byte[] buffer = new byte[BUFFER_SIZE];
            sbarOutputSteam.putNextEntry(new ZipEntry(fileName));

            while ((length = inputStream.read(buffer)) > 0) {
                sbarOutputSteam.write(buffer, 0, length);
            }

            sbarOutputSteam.closeEntry();
        } catch (IOException ex) {
            throw new ArchiverException("IOException occurred while writing file to archive", ex);
        } finally {
            if (inputStream != null) {
                IOUtils.closeQuietly(inputStream);
            }
        }
    }

    /**
     * Add <i>ExportInfo</i> file to archive. This method should be called after all files are added to the archive.
     *
     * @throws ArchiverException
     */
    private void addExportInfoFile() throws ArchiverException {
        if (exportInfoFiles.size() > 0) {
            getLogger().debug("Adding [" + FILENAME_EXPORT_INFO + "] to archive");

            String exportInfoContent = constructExportInfoContent();
            addFileToArchive(FILENAME_EXPORT_INFO, new ByteArrayInputStream(exportInfoContent.getBytes()));
        }
    }

    /**
     * Merge all locally stored <i>ExportInfo</i> file into single <i>ExportInfo</i> file. This single file will be
     * added to the archive by the {@link #addExportInfoFile()} method. The following properties will be used:
     * <ul>
     *  <li><b>username:</b> ServiceBus</li>
     *  <li><b>description:</b> (empty)</li>
     *  <li><b>exporttime:</b> Current date in EEE MMM dd HH:mm:ss z yyy format (example: Tue Apr 26 09:57:44 CEST
     *  2016)</li>
     *  <li><b>productname:</b> Oracle Service Bus</li>
     *  <li><b>productversion:</b> 12.1.3.0.0</li>
     *  <li><b>projectLevelExport:</b> true if all locally stored <i>ExportInfo</i> files have configured this setting
     *  to true. If any of the <i>ExportInfo</i> files has the <i>projectLevelExport</i> property configured to false,
     *  this property will be set to false.</li>
     * </ul>
     *
     * @return Content of the merged <i>ExportInfo</i> file as {@link String} instance.
     * @throws ArchiverException
     */
    private String constructExportInfoContent() throws ArchiverException {
        String result = null;

        try {
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            Document doc = docBuilder.newDocument();

            Element xmlFragmentElement = doc.createElement(ELEMENT_XML_FRAGMENT);
            xmlFragmentElement.setAttribute(ATTRIBUTE_NAME, getDestFile().getName());
            xmlFragmentElement.setAttribute(ATTRIBUTE_VERSION, ATTRIBUTE_VERSION_VALUE);
            xmlFragmentElement.setAttributeNS(NS_XMLNS, NS_XMLNS_PREFIX + ":" + NS_IMPORT_EXPORT_PREFIX,
                                              NS_IMPORT_EXPORT);
            doc.appendChild(xmlFragmentElement);

            Element propertiesElement = doc.createElementNS(NS_IMPORT_EXPORT, ELEMENT_PROPERTIES);
            propertiesElement.setPrefix(NS_IMPORT_EXPORT_PREFIX);
            xmlFragmentElement.appendChild(propertiesElement);

            addPropertyToExportInfo(doc, propertiesElement, ATTRIBUTE_USERNAME,
                                    getPropertyFromFirstExportInfo(ATTRIBUTE_USERNAME));
            addPropertyToExportInfo(doc, propertiesElement, ATTRIBUTE_DESCRIPTION,
                                    getPropertyFromFirstExportInfo(ATTRIBUTE_DESCRIPTION));
            addPropertyToExportInfo(doc, propertiesElement, ATTRIBUTE_EXPORTTIME,
                                    new SimpleDateFormat(ATTRIBUTE_EXPORTTIME_FORMAT,
                                                         Locale.ENGLISH).format(new Date()));
            addPropertyToExportInfo(doc, propertiesElement, ATTRIBUTE_PRODUCTNAME,
                                    getPropertyFromFirstExportInfo(ATTRIBUTE_PRODUCTNAME));
            addPropertyToExportInfo(doc, propertiesElement, ATTRIBUTE_PRODUCTVERSION,
                                    getPropertyFromFirstExportInfo(ATTRIBUTE_PRODUCTVERSION));
            addPropertyToExportInfo(doc, propertiesElement, ATTRIBUTE_PROJECT_LEVEL_EXPORT,
                                    isExportInfoProjectLevel() + "");

            for (Document exportInfoFile : exportInfoFiles) {
                XPath xPath = XPathFactory.newInstance().newXPath();
                NodeList nodeList =
                    (NodeList) xPath.compile("//*[local-name()='exportedItemInfo']").evaluate(exportInfoFile,
                                                                                              XPathConstants.NODESET);

                for (int i = 0; i < nodeList.getLength(); i++) {
                    Node node = nodeList.item(i);
                    xmlFragmentElement.appendChild(doc.adoptNode(node));
                }
            }

            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(doc), new StreamResult(writer));
            result = writer.getBuffer().toString();
        } catch (ParserConfigurationException ex) {
            throw new ArchiverException("ParserConfigurationException occurred while merging ExportInfo files", ex);
        } catch (XPathExpressionException ex) {
            throw new ArchiverException("ParserConfigurationException occurred while merging ExportInfo files", ex);
        } catch (TransformerConfigurationException ex) {
            throw new ArchiverException("TransformerConfigurationException occurred while merging ExportInfo files",
                                        ex);
        } catch (TransformerException ex) {
            throw new ArchiverException("TransformerException occurred while merging ExportInfo files", ex);
        }

        return result;
    }

    /**
     * Add property to merged <i>ExportInfo</i> file.
     *
     * @param doc XML document of the <i>ExportInfo</i> file.
     * @param propertiesElement Properties element within the <i>ExportInfo</i> file.
     * @param name Name of the property.
     * @param value Value of the property.
     */
    private void addPropertyToExportInfo(Document doc, Element propertiesElement, String name, String value) {
        Element propertyElement = doc.createElementNS(NS_IMPORT_EXPORT, ELEMENT_PROPERTY);
        propertyElement.setAttribute(ATTRIBUTE_NAME, name);
        propertyElement.setAttribute(ATTRIBUTE_VALUE, value);
        propertiesElement.appendChild(propertyElement);
    }

    /**
     * Method to check if any of the locally stored <i>ExportInfo</i> files has configured the <i>projectLevelExport</i>
     * property to false.
     *
     * @return If any of the <i>ExportInfo</i> files has configured this property to false. This method will return
     * false. If all of the <i>ExportInfo</i> files have configured this property to true, this method will return true.
     * @throws ArchiverException
     */
    private boolean isExportInfoProjectLevel() throws ArchiverException {
        boolean result = true;

        for (Document doc : exportInfoFiles) {
            String projectLevelExport = getPropertyFromExportInfo(doc, ATTRIBUTE_PROJECT_LEVEL_EXPORT);
            result = "TRUE".equalsIgnoreCase(projectLevelExport);

            if (!result) {
                break;
            }
        }

        return result;
    }

    /**
     * Method to retreive property value from first <i>ExportInfo</i> file in {@link exportInfoFiles} array.
     *
     * @param property Property name.
     * @return Property value.
     * @throws ArchiverException
     */
    private String getPropertyFromFirstExportInfo(String property) throws ArchiverException {
        String result = null;

        if (exportInfoFiles.size() >= 1) {
            result = getPropertyFromExportInfo(exportInfoFiles.get(0), property);
        }

        return result;
    }

    /**
     * Method to retreive property value from <i>ExportInfo</i> file.
     *
     * @param exportInfo <i>ExportInfo</i> file as {@link Document} instance.
     * @param property Property name.
     * @return Property value.
     * @throws ArchiverException
     */
    private String getPropertyFromExportInfo(Document exportInfo, String property) throws ArchiverException {
        String result = null;

        try {
            XPath xPath = XPathFactory.newInstance().newXPath();
            result =
                xPath.compile("/xml-fragment/*[local-name()='properties']/*[local-name()='property' and @name='" +
                              property + "']/@value").evaluate(exportInfo);
        } catch (XPathExpressionException ex) {
            throw new ArchiverException("Failed to read property [" + property + "] from ExportInfo file", ex);
        }

        return result;
    }
}