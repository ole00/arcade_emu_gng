/* *************************************************
 * This code is part of the Arktayt Emulator.
 * It's free, as long as this whole message is preserved.  
 * Written for educational purposes.
 *************************************************/


package res;

import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ResourceLoader {
	
	private static ResourceLoader instance;
	private String zipName;
	private ZipFile zipFile;
	
	public static ResourceLoader getInstance() {
		if (instance == null) {
			instance = new ResourceLoader();
		}
		return instance;
	}
	
	public InputStream getZipResource(String zipName, String resName) throws IOException {
		if (this.zipName != null) {
			if (!this.zipName.equals(zipName)) {
				if (zipFile != null) {
					zipFile.close();
				}
				zipFile = null;
				this.zipName = zipName;
				zipFile = new ZipFile(zipName);
			}
		} else {
			this.zipName = zipName;
			zipFile = new ZipFile(zipName);
		}
		
	    final Enumeration<? extends ZipEntry> entries = zipFile.entries();
	    
	    while (entries.hasMoreElements()) {
	        final ZipEntry zipEntry = entries.nextElement();
	        if (!zipEntry.isDirectory()) {
	            if (zipEntry.getName().equals(resName)) {
	                return zipFile.getInputStream(zipEntry);
	            }
	        }
	    }
	    return null;
	}
	
	public void closeZipResource(String zipName) throws IOException {
		if (this.zipName.equals(zipName)) {
			if (zipFile != null) {
				this.zipName = null;
				ZipFile tmp = zipFile;
				zipFile = null;
				tmp.close();
			}
		}
	}
}
