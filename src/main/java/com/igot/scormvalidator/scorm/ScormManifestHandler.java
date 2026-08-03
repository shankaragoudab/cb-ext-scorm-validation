package com.igot.scormvalidator.scorm;

import com.igot.scormvalidator.scorm.model.RepairAction;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.igot.scormvalidator.scorm.ScormConstants.ADLCP_NAMESPACE_URI;
import static com.igot.scormvalidator.scorm.ScormConstants.DEFAULT_SCORM_VERSION;
import static com.igot.scormvalidator.scorm.ScormConstants.IMSCP_NAMESPACE_URI;
import static com.igot.scormvalidator.scorm.ScormConstants.SCORM_TYPE_ATTR;
import static com.igot.scormvalidator.scorm.ScormConstants.SCO_VALUE;
import static com.igot.scormvalidator.scorm.ScormConstants.XSI_NAMESPACE_URI;

/**
 * DOM-based read/write/repair helpers for {@code imsmanifest.xml}. Manifests found in the wild
 * are inconsistent about namespace prefixes, so reads are namespace-agnostic (plain
 * {@code getElementsByTagName} local-name lookups), matching the Node reference implementation.
 */
@Component
public class ScormManifestHandler {

    /**
     * Parses {@code manifestFile} into a DOM {@link Document}. External entity resolution and
     * DTD loading are disabled since this parses untrusted uploaded content.
     */
    public Document parse(File manifestFile) throws Exception {
        DocumentBuilderFactory factory = newSecureFactory();
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(manifestFile);
    }

    /**
     * Builds a minimal, valid SCORM 1.2 manifest DOM referencing {@code launchFile}, equivalent
     * to the Node reference's {@code buildMinimalManifest} template.
     */
    public Document buildMinimal(String launchFile) {
        try {
            DocumentBuilderFactory factory = newSecureFactory();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.newDocument();

            Element manifest = doc.createElement("manifest");
            manifest.setAttribute("identifier", "course_manifest");
            manifest.setAttribute("version", "1");
            manifest.setAttribute("xmlns", IMSCP_NAMESPACE_URI);
            manifest.setAttribute("xmlns:adlcp", ADLCP_NAMESPACE_URI);
            manifest.setAttribute("xmlns:xsi", XSI_NAMESPACE_URI);
            doc.appendChild(manifest);

            Element metadata = doc.createElement("metadata");
            Element schema = doc.createElement("schema");
            schema.setTextContent("ADL SCORM");
            Element schemaVersion = doc.createElement("schemaversion");
            schemaVersion.setTextContent(DEFAULT_SCORM_VERSION);
            metadata.appendChild(schema);
            metadata.appendChild(schemaVersion);
            manifest.appendChild(metadata);

            Element organizations = doc.createElement("organizations");
            organizations.setAttribute("default", "org_1");
            Element organization = doc.createElement("organization");
            organization.setAttribute("identifier", "org_1");
            Element orgTitle = doc.createElement("title");
            orgTitle.setTextContent("Course");
            organization.appendChild(orgTitle);
            Element item = doc.createElement("item");
            item.setAttribute("identifier", "item_1");
            item.setAttribute("identifierref", "resource_1");
            Element itemTitle = doc.createElement("title");
            itemTitle.setTextContent("Course");
            item.appendChild(itemTitle);
            organization.appendChild(item);
            organizations.appendChild(organization);
            manifest.appendChild(organizations);

            Element resources = doc.createElement("resources");
            Element resource = doc.createElement("resource");
            resource.setAttribute("identifier", "resource_1");
            resource.setAttribute("type", "webcontent");
            resource.setAttribute(SCORM_TYPE_ATTR, SCO_VALUE);
            resource.setAttribute("href", launchFile);
            Element file = doc.createElement("file");
            file.setAttribute("href", launchFile);
            resource.appendChild(file);
            resources.appendChild(resource);
            manifest.appendChild(resources);

            return doc;
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("Unable to build minimal SCORM manifest", e);
        }
    }

    /**
     * Serializes {@code doc} to {@code target} as indented UTF-8 XML.
     */
    public void write(Document doc, File target) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        tf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new java.io.IOException("Unable to create directory: " + parent);
        }
        try (OutputStream outputStream = Files.newOutputStream(target.toPath())) {
            transformer.transform(new DOMSource(doc), new StreamResult(outputStream));
        }
    }

    public String getSchemaVersion(Document doc) {
        NodeList nodes = doc.getElementsByTagName("schemaversion");
        if (nodes.getLength() > 0) {
            String text = nodes.item(0).getTextContent();
            if (text != null && !text.trim().isEmpty()) {
                return text.trim();
            }
        }
        return "Unknown";
    }

    public String getTitle(Document doc) {
        NodeList generalNodes = doc.getElementsByTagName("general");
        for (int i = 0; i < generalNodes.getLength(); i++) {
            Node general = generalNodes.item(i);
            if (!(general instanceof Element)) {
                continue;
            }
            NodeList titles = ((Element) general).getElementsByTagName("title");
            for (int j = 0; j < titles.getLength(); j++) {
                NodeList langStrings = ((Element) titles.item(j)).getElementsByTagName("langstring");
                if (langStrings.getLength() > 0) {
                    String text = langStrings.item(0).getTextContent();
                    if (text != null && !text.trim().isEmpty()) {
                        return text.trim();
                    }
                }
            }
        }
        return null;
    }

    public List<Element> getResourceElements(Document doc) {
        List<Element> result = new ArrayList<>();
        NodeList resourcesBlocks = doc.getElementsByTagName("resources");
        if (resourcesBlocks.getLength() == 0) {
            return result;
        }
        Element resourcesEl = (Element) resourcesBlocks.item(0);
        NodeList resourceNodes = resourcesEl.getElementsByTagName("resource");
        for (int i = 0; i < resourceNodes.getLength(); i++) {
            result.add((Element) resourceNodes.item(i));
        }
        return result;
    }

    public boolean hasOrganizations(Document doc) {
        return doc.getElementsByTagName("organizations").getLength() > 0;
    }

    public Optional<RepairAction> ensureAdlcpNamespace(Document doc) {
        Element root = doc.getDocumentElement();
        if (root == null) {
            return Optional.empty();
        }
        if (!root.hasAttribute("xmlns:adlcp")) {
            root.setAttribute("xmlns:adlcp", ADLCP_NAMESPACE_URI);
            return Optional.of(RepairAction.builder()
                    .code("ADDED_NAMESPACE")
                    .description("Added missing adlcp namespace declaration to manifest root")
                    .build());
        }
        return Optional.empty();
    }

    public Optional<RepairAction> ensureMetadata(Document doc, String preferredVersion) {
        NodeList metadataNodes = doc.getElementsByTagName("metadata");
        if (metadataNodes.getLength() > 0) {
            return Optional.empty();
        }
        Element root = doc.getDocumentElement();
        Element metadata = doc.createElement("metadata");
        Element schema = doc.createElement("schema");
        schema.setTextContent("ADL SCORM");
        Element schemaVersion = doc.createElement("schemaversion");
        String version = (preferredVersion != null && !preferredVersion.isBlank()
                && !"Unknown".equalsIgnoreCase(preferredVersion)) ? preferredVersion : DEFAULT_SCORM_VERSION;
        schemaVersion.setTextContent(version);
        metadata.appendChild(schema);
        metadata.appendChild(schemaVersion);

        Node firstChild = root.getFirstChild();
        if (firstChild != null) {
            root.insertBefore(metadata, firstChild);
        } else {
            root.appendChild(metadata);
        }
        return Optional.of(RepairAction.builder()
                .code("ADDED_METADATA")
                .description("Added missing metadata/schema block")
                .build());
    }

    /**
     * Ensures the primary resource declares {@code adlcp:scormtype="sco"} and that its launch
     * file actually exists, substituting a discovered launch file when it doesn't (or creating a
     * fresh {@code <resources>} block if none exists at all).
     */
    public ScormTypeFixResult ensureScormTypeAndFixLaunchFile(Document doc, File manifestFile, File extractedRoot) {
        List<RepairAction> repairs = new ArrayList<>();
        NodeList resourcesBlocks = doc.getElementsByTagName("resources");

        if (resourcesBlocks.getLength() == 0) {
            return createResourcesBlock(doc, doc.getDocumentElement(), manifestFile, extractedRoot, repairs, true);
        }

        Element resourcesEl = (Element) resourcesBlocks.item(0);
        NodeList resourceNodes = resourcesEl.getElementsByTagName("resource");
        if (resourceNodes.getLength() == 0) {
            return createResourcesBlock(doc, resourcesEl, manifestFile, extractedRoot, repairs, false);
        }

        Element primary = (Element) resourceNodes.item(0);
        if (!primary.hasAttribute(SCORM_TYPE_ATTR)) {
            primary.setAttribute(SCORM_TYPE_ATTR, SCO_VALUE);
            repairs.add(RepairAction.builder()
                    .code("SET_SCORMTYPE")
                    .description("Set adlcp:scormtype=\"sco\" on primary resource")
                    .build());
        }

        String href = primary.hasAttribute("href") ? primary.getAttribute("href") : null;
        File resolved = (href != null && !href.isBlank()) ? ScormZipUtil.resolveRelative(manifestFile, href) : null;
        if (href == null || href.isBlank() || resolved == null || !resolved.exists()) {
            File found = ScormZipUtil.findLaunchFile(extractedRoot);
            if (found == null) {
                return new ScormTypeFixResult(repairs, null);
            }
            String relativeHref = relativize(manifestFile, found);
            primary.setAttribute("href", relativeHref);
            repairs.add(RepairAction.builder()
                    .code("FIXED_LAUNCH_FILE")
                    .description("Fixed broken launch file reference -> " + relativeHref)
                    .build());
            return new ScormTypeFixResult(repairs, relativeHref);
        }

        return new ScormTypeFixResult(repairs, href);
    }

    private ScormTypeFixResult createResourcesBlock(Document doc, Element parent, File manifestFile,
                                                      File extractedRoot, List<RepairAction> repairs,
                                                      boolean createResourcesElement) {
        File found = ScormZipUtil.findLaunchFile(extractedRoot);
        if (found == null) {
            return new ScormTypeFixResult(repairs, null);
        }
        String relativeHref = relativize(manifestFile, found);

        Element resourcesEl = parent;
        if (createResourcesElement) {
            resourcesEl = doc.createElement("resources");
            parent.appendChild(resourcesEl);
        }

        Element resource = doc.createElement("resource");
        resource.setAttribute("identifier", "resource_1");
        resource.setAttribute("type", "webcontent");
        resource.setAttribute(SCORM_TYPE_ATTR, SCO_VALUE);
        resource.setAttribute("href", relativeHref);
        Element file = doc.createElement("file");
        file.setAttribute("href", relativeHref);
        resource.appendChild(file);
        resourcesEl.appendChild(resource);

        repairs.add(RepairAction.builder()
                .code("CREATED_RESOURCES_BLOCK")
                .description("Created missing <resources> block with launch file " + relativeHref)
                .build());
        return new ScormTypeFixResult(repairs, relativeHref);
    }

    private String relativize(File manifestFile, File target) {
        Path manifestDir = manifestFile.getParentFile().toPath().toAbsolutePath().normalize();
        Path targetPath = target.toPath().toAbsolutePath().normalize();
        return manifestDir.relativize(targetPath).toString().replace(File.separatorChar, '/');
    }

    private DocumentBuilderFactory newSecureFactory() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory;
    }

    /**
     * Small result holder for {@link #ensureScormTypeAndFixLaunchFile}: the repair actions taken
     * and the resolved launch file relative href, or {@code null} launch file if the package has
     * no launchable content anywhere and is therefore unrepairable.
     */
    public static class ScormTypeFixResult {
        private final List<RepairAction> repairs;
        private final String launchFile;

        public ScormTypeFixResult(List<RepairAction> repairs, String launchFile) {
            this.repairs = repairs;
            this.launchFile = launchFile;
        }

        public List<RepairAction> getRepairs() {
            return repairs;
        }

        public String getLaunchFile() {
            return launchFile;
        }
    }
}
