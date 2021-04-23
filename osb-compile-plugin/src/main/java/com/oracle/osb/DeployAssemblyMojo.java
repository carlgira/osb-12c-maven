package com.oracle.osb;

import com.bea.wli.config.Ref;
import com.bea.wli.config.customization.Customization;
import com.bea.wli.config.importexport.ImportResult;
import com.bea.wli.config.resource.Diagnostics;
import com.bea.wli.sb.management.configuration.ALSBConfigurationMBean;
import com.bea.wli.sb.management.importexport.ALSBImportPlan;
import com.bea.wli.sb.management.importexport.ALSBJarInfo;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import java.util.List;
import java.util.Map;

import oracle.sb.maven.plugin.deploy.MBeanHelper;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.xmlbeans.XmlException;

/**
 * Implementation of {@link AbstractMojo} for deploying OSB assembly project to server. The server credentials should be
 * provided via the {@link serverUrl}, {@link serverUsername} and {@link serverPassword} parameters. The file that will
 * deployed to the OSB server, is the Maven project/build/finalName file, located in the {@link archiveOutputDir}
 * directory.
 */
@Mojo(name = "deploy-assembly", defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST )
@Execute(goal = "deploy-assembly", phase = LifecyclePhase.PRE_INTEGRATION_TEST)
public class DeployAssemblyMojo extends AbstractMojo {

    //General settings
    private static final String SESSION_NAME_PREFIX = "ServiceBusPlugin";
    private static final String SESSION_ACTIVATION_DESCRIPTION = "Published from ServiceBusPlugin.";

    @Parameter(defaultValue = "${project}")
    private MavenProject project;

    @Parameter(property = "server.url", required = true)
    private String serverUrl;

    @Parameter(property = "server.username", required = true)
    private String serverUsername;

    @Parameter(property = "server.password", required = true)
    private String serverPassword;

    @Parameter(property = "deployment.preserve.credentials", defaultValue = "true")
    private boolean deploymentPreserveCredentals;

    @Parameter(property = "deployment.preserve.envValues", defaultValue = "true")
    private boolean deploymentPreserveEnvValues;

    @Parameter(property = "deployment.preserve.operationalValues", defaultValue = "true")
    private boolean deploymentPreserveOperationalValues;

    @Parameter(property = "deployment.preserve.securityAndPolicyConfig", defaultValue = "true")
    private boolean deploymentPreserveSecurityAndPolicyConfig;

    @Parameter(property = "deployment.preserve.accessControlPolicies", defaultValue = "true")
    private boolean deploymentPreserveAccessControlPolicies;

    @Parameter(property = "deployment.customization.file", required = false)
    private File deploymentCustomizationFile;

    @Parameter(property = "deployment.session.activate", defaultValue = "true")
    private boolean deploymentSessionActivate;

    @Parameter(property = "deployment.session.discardOnError", defaultValue = "true")
    private boolean deploymentSessionDiscardOnError;

    /**
     * Session name.
     */
    private String sessionName;

    /**
     * {@link MBeanHelper} instance to execute actions on OSB server.
     */
    private MBeanHelper mBeanHelper;

    @Override
    public void execute() throws MojoExecutionException {
        File artifactFile = new File(project.getBuild().getDirectory(), "sbconfig.sbar");
        getLog().info("Deploying assembly [" + artifactFile.getAbsolutePath() + "]");

        createSession();
        importArtifact(artifactFile);
        applyCustomizationFile();

        if (deploymentSessionActivate) {
            if (!hasConflicts()) {
                activateSession();
            } else {
                if (deploymentSessionDiscardOnError) {
                    discardSession();
                }

                throw new MojoExecutionException("Session could not be activated due to existing conflicts");
            }
        }
    }

    /**
     * Get instance of {@link MBeanHelper} by logging in to Weblogic server {@link serverUrl}, using the
     * {@link serverUsername} and {@link serverPassword} credentials.
     *
     * @return {@link MBeanHelper} instance.
     */
    private MBeanHelper getMBeanHelper() {
        if (mBeanHelper == null) {
            mBeanHelper = new MBeanHelper(serverUrl, serverUsername, serverPassword, false);
        }

        return mBeanHelper;
    }

    /**
     * Constructs the {@link ALSBImportPlan} that should be used when deploying an archive file. The import plan is
     * based on the default import plan in combination with the {@link deploymentPreserveAccessControlPolicies},
     * {@link deploymentPreserveCredentals}, {@link deploymentPreserveEnvValues},
     * {@link deploymentPreserveOperationalValues} and {@link deploymentPreserveSecurityAndPolicyConfig} settings.
     *
     * @return Instance of {@link ALSBImportPlan}
     * @throws MojoExecutionException
     */
    private ALSBImportPlan getImportPlan() throws MojoExecutionException {
        try {
            ALSBConfigurationMBean configMBean = getMBeanHelper().getConfigMBean(sessionName);
            ALSBJarInfo jarInfo = configMBean.getImportJarInfo();
            ALSBImportPlan importPlan = jarInfo.getDefaultImportPlan();

            importPlan.setPreserveExistingAccessControlPolicies(deploymentPreserveAccessControlPolicies);
            importPlan.setPreserveExistingCredentials(deploymentPreserveCredentals);
            importPlan.setPreserveExistingEnvValues(deploymentPreserveEnvValues);
            importPlan.setPreserveExistingOperationalValues(deploymentPreserveOperationalValues);
            importPlan.setPreserveExistingSecurityAndPolicyConfig(deploymentPreserveSecurityAndPolicyConfig);

            return importPlan;
        } catch (Exception ex) {
            throw new MojoExecutionException("Failed to retreive default import plan");
        }
    }

    /**
     * Create session which can be used to import the artifact.
     *
     * @throws MojoExecutionException
     */
    private void createSession() throws MojoExecutionException {
        sessionName = SESSION_NAME_PREFIX + "_" + project.getArtifactId() + "_" + System.currentTimeMillis();
        getLog().info("Creating session [" + sessionName + "]");

        try {
            getMBeanHelper().getSessionMBean().createSession(sessionName);
        } catch (Exception ex) {
            throw new MojoExecutionException("Unable to create session [" + sessionName + "]", ex);
        }
    }

    /**
     * Import artifact to session.
     *
     * @param artifact Artifact file that needs to be imported as {@link File} instance.
     * @throws MojoExecutionException
     */
    private void importArtifact(File artifact) throws MojoExecutionException {
        getLog().info("Importing artifact [" + artifact.getAbsolutePath() + "] to session [" + sessionName + "]");

        try {
            ALSBConfigurationMBean configMBean = getMBeanHelper().getConfigMBean(sessionName);
            configMBean.uploadJarFile(FileUtils.readFileToByteArray(artifact));
            ImportResult result = configMBean.importUploaded(getImportPlan());

            if (!result.getFailed().isEmpty()) {
                throw new MojoFailureException("Import of artifact to session failed");
            }
        } catch (IOException ex) {
            throw new MojoExecutionException("Unable to read artifact [" + artifact.getAbsolutePath() + "]", ex);
        } catch (Exception ex) {
            throw new MojoExecutionException("Unable to import artifact [" + artifact.getAbsolutePath() +
                                             "] to session [" + sessionName + "]", ex);
        }
    }


    /**
     * Apply {@link deploymentCustomizationFile} as customization file to session {@link sessionName} if
     * {@link deploymentCustomizationFile} is not null.
     *
     * @throws MojoExecutionException
     */
    private void applyCustomizationFile() throws MojoExecutionException {
        if (deploymentCustomizationFile != null) {
            getLog().info("Applying customization file [" + deploymentCustomizationFile.getAbsolutePath() + "]");
            FileInputStream inputStream = null;

            try {
                ALSBConfigurationMBean configMBean = getMBeanHelper().getConfigMBean(sessionName);
                inputStream = new FileInputStream(deploymentCustomizationFile);
                List<Customization> customizations = Customization.fromXML(inputStream, null);
                configMBean.customize(customizations);
            } catch (XmlException ex) {
                throw new MojoExecutionException("Unable to parse XML from customization file [" +
                                                 deploymentCustomizationFile.getAbsolutePath() + "]", ex);
            } catch (IOException ex) {
                throw new MojoExecutionException("Unable to read customization file [" +
                                                 deploymentCustomizationFile.getAbsolutePath() + "]", ex);
            } catch (Exception ex) {
                throw new MojoExecutionException("Unable to apply customization file [" +
                                                 deploymentCustomizationFile.getAbsolutePath() + "] to session [" +
                                                 sessionName + "]", ex);
            } finally {
                IOUtils.closeQuietly(inputStream);
            }
        }
    }

    /**
     * Check if there are conflicts in the existing session.
     *
     * @return FALSE if there are no conflicts in the existing session, TRUE otherwise.
     * @throws MojoExecutionException
     */
    private boolean hasConflicts() throws MojoExecutionException {
        getLog().info("Checking for conflicts in session [" + sessionName + "]");

        try {
            ALSBConfigurationMBean configMBean = getMBeanHelper().getConfigMBean(sessionName);
            Map<Ref, Diagnostics> diagnosticMap = configMBean.getDiagnostics(null);

            for (Diagnostics diags : diagnosticMap.values()) {
                if (!diags.getSeverity().isValidSeverity()) {
                    return true;
                }
            }
        } catch (Exception ex) {
            throw new MojoExecutionException("An unexpected exception occurred while checking session [" + sessionName +
                                             "] for conflicts", ex);
        }

        return false;
    }

    /**
     * Activate session.
     *
     * @throws MojoExecutionException
     */
    private void activateSession() throws MojoExecutionException {
        getLog().info("Activating session [" + sessionName + "]");

        try {
            getMBeanHelper().getSessionMBean().activateSession(sessionName, SESSION_ACTIVATION_DESCRIPTION);
        } catch (Exception ex) {
            throw new MojoExecutionException("Unable to activate session [" + sessionName + "]", ex);
        }
    }

    /**
     * Discard session.
     *
     * @throws MojoExecutionException
     */
    private void discardSession() throws MojoExecutionException {
        getLog().info("Discarding session [" + sessionName + "]");

        try {
            getMBeanHelper().getSessionMBean().discardSession(sessionName);
        } catch (Exception ex) {
            throw new MojoExecutionException("Unable to discard session [" + sessionName + "]", ex);
        }
    }
}
