package com.eka.costapp.service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.owasp.esapi.ESAPI;
import org.owasp.esapi.Logger;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Service;




/**
 * The Class ManifestService.
 */
@Service
public class ManifestService {

	/** The Constant logger. */
	final static  Logger logger = ESAPI.getLogger(ManifestService.class);
	/**
	 * Gets the manifest attributes.
	 *
	 * @return the manifest attributes
	 * @throws FileNotFoundException the file not found exception
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public Attributes getManifestAttributes(){
		 Manifest mf=null;
		 InputStream resourceAsStream=null;
		 Attributes atts=null;
	    try {
	    	 resourceAsStream = SpringBootApplication.class.getResourceAsStream("/META-INF/MANIFEST.MF");
	 	     mf = new Manifest();
			 mf.read(resourceAsStream);
			 atts = mf.getMainAttributes();
		}catch (FileNotFoundException fno){
			logger.error(Logger.EVENT_FAILURE, fno.getLocalizedMessage(), fno);
		} catch (IOException ioe) {
			logger.error(Logger.EVENT_FAILURE, ioe.getLocalizedMessage(), ioe);
		}
	    return atts;	    		
	}


}