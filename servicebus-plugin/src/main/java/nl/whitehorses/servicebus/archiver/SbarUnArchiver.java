package nl.whitehorses.servicebus.archiver;

import java.io.File;

import org.codehaus.plexus.archiver.zip.ZipUnArchiver;

/**
 * Class to unarchive <i>sbar</i> archive files. To achieve this, the <i>sbar</i> just has to be unpacked like a
 * <i>zip</i> file. This class is an extension of the {@link ZipUnArchiver}.
 */
public class SbarUnArchiver extends ZipUnArchiver {

    /**
     * Default constructor.
     */
    public SbarUnArchiver() {
        super();
    }

    /**
     * Constructor.
     *
     * @param file File.
     */
    public SbarUnArchiver(File file) {
        super(file);
    }
}
