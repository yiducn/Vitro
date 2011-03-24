package edu.cornell.mannlib.vitro.webapp.utils;

/* $This file is distributed under the terms of the license in /doc/license.txt$ */

import javax.servlet.ServletContext;

public class NamespaceMapperFactory {

	public static NamespaceMapper getNamespaceMapper(ServletContext servletContext) {
		return (NamespaceMapper) servletContext.getAttribute("NamespaceMapper");
	}
	
}
