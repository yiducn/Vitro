package edu.cornell.mannlib.vitro.webapp.dao;

/* $This file is distributed under the terms of the license in /doc/license.txt$ */

import java.util.List;

import edu.cornell.mannlib.vitro.webapp.beans.ObjectProperty;
import edu.cornell.mannlib.vitro.webapp.beans.Property;

public interface PropertyDao {
	
    void addSuperproperty(Property property, Property superproperty);
    
    void addSuperproperty(String propertyURI, String superpropertyURI);
    
    void removeSuperproperty(Property property, Property superproperty);
    
    void removeSuperproperty(String propertyURI, String superpropertyURI);
    
    void addSubproperty(Property property, Property subproperty);
    
    void addSubproperty(String propertyURI, String subpropertyURI);
    
    void removeSubproperty(Property property, Property subproperty);
    
    void removeSubproperty(String propertyURI, String subpropertyURI);
    
    void addEquivalentProperty(String propertyURI, String equivalentPropertyURI);
    
    void addEquivalentProperty(Property property, Property equivalentProperty);
    
    void removeEquivalentProperty(String propertyURI, String equivalentPropertyURI);
    
    void removeEquivalentProperty(Property property, Property equivalentProperty);
    
    List <String> getSubPropertyURIs(String propertyURI);

    List <String> getAllSubPropertyURIs(String propertyURI);

    List <String> getSuperPropertyURIs(String propertyURI);

    List <String> getAllSuperPropertyURIs(String propertyURI);
    
    List <String> getEquivalentPropertyURIs(String propertyURI);
    
}
