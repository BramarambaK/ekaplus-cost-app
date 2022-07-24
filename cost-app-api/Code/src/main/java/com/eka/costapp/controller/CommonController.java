package com.eka.costapp.controller;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.jar.Attributes;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.eka.costapp.service.ManifestService;



@RestController
@CrossOrigin
@RequestMapping("/common")
public class CommonController {

	@Autowired
	ManifestService manifestService;

	/**
	 * Gets the manifest attributes.
	 *
	 * @return the manifest attributes
	 * @throws FileNotFoundException the file not found exception
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	@GetMapping(value = "/getManifestInfo")
	public ResponseEntity<Attributes> getManifestAttributes() {
		ResponseEntity<Attributes> respEntity=new ResponseEntity<Attributes>(manifestService.getManifestAttributes(), HttpStatus.OK);
		return respEntity;    		
	}

}
