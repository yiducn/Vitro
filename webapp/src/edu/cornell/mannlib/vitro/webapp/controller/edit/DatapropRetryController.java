package edu.cornell.mannlib.vitro.webapp.controller.edit;

/* $This file is distributed under the terms of the license in /doc/license.txt$ */

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.cornell.mannlib.vedit.beans.EditProcessObject;
import edu.cornell.mannlib.vedit.beans.FormObject;
import edu.cornell.mannlib.vedit.beans.Option;
import edu.cornell.mannlib.vedit.controller.BaseEditController;
import edu.cornell.mannlib.vedit.forwarder.PageForwarder;
import edu.cornell.mannlib.vedit.forwarder.impl.UrlForwarder;
import edu.cornell.mannlib.vedit.util.FormUtils;
import edu.cornell.mannlib.vedit.validator.impl.EnumValuesValidator;
import edu.cornell.mannlib.vedit.validator.impl.IntValidator;
import edu.cornell.mannlib.vedit.validator.impl.XMLNameValidator;
import edu.cornell.mannlib.vitro.webapp.beans.DataProperty;
import edu.cornell.mannlib.vitro.webapp.beans.Ontology;
import edu.cornell.mannlib.vitro.webapp.beans.Portal;
import edu.cornell.mannlib.vitro.webapp.beans.VClass;
import edu.cornell.mannlib.vitro.webapp.controller.Controllers;
import edu.cornell.mannlib.vitro.webapp.controller.VitroRequest;
import edu.cornell.mannlib.vitro.webapp.controller.edit.utils.RoleLevelOptionsSetup;
import edu.cornell.mannlib.vitro.webapp.dao.DataPropertyDao;
import edu.cornell.mannlib.vitro.webapp.dao.DatatypeDao;
import edu.cornell.mannlib.vitro.webapp.dao.OntologyDao;
import edu.cornell.mannlib.vitro.webapp.dao.VClassDao;
import edu.cornell.mannlib.vitro.webapp.edit.listener.impl.EditProhibitionListener;


public class DatapropRetryController extends BaseEditController {
	
	private static final Log log = LogFactory.getLog(DatapropRetryController.class.getName());

    public void doPost (HttpServletRequest request, HttpServletResponse response) {

        if (!checkLoginStatus(request,response))
            return;

        try {
            super.doGet(request,response);
        } catch (Exception e) {
            log.error("DatapropRetryController encountered exception calling super.doGet()");
        }

        //create an EditProcessObject for this and put it in the session
        EditProcessObject epo = super.createEpo(request);
        epo.setBeanClass(DataProperty.class);

        DatatypeDao dDao = getWebappDaoFactory().getDatatypeDao();
        DataPropertyDao dpDao = getWebappDaoFactory().getDataPropertyDao();
        epo.setDataAccessObject(dpDao);
        OntologyDao ontDao = getWebappDaoFactory().getOntologyDao();
        VClassDao vclassDao = getWebappDaoFactory().getVClassDao();

        DataProperty objectForEditing = null;
        String action = null;
        if (epo.getAction() == null) {
            action = "insert";
            epo.setAction("insert");
        } else {
            action = epo.getAction();
        }
        if (epo.getUseRecycledBean()) {
            objectForEditing = (DataProperty) epo.getNewBean();
        } else {
            String uri = request.getParameter("uri");
            if (uri != null) {
                try {
                    objectForEditing = dpDao.getDataPropertyByURI(uri);
                    epo.setOriginalBean(objectForEditing);
                    action = "update";
                    epo.setAction("update");
                } catch (NullPointerException e) {
                    log.error("Need to implement 'record not found' error message.");
                }
            } else {
                action = "insert";
                epo.setAction("insert");
                objectForEditing = new DataProperty();
                epo.setOriginalBean(objectForEditing);
            }
        }

        //put this in the parent class?
        //make a simple mask for the class's id
        Object[] simpleMaskPair = new Object[2];
        simpleMaskPair[0]="URI";
        simpleMaskPair[1]=objectForEditing.getURI();
        epo.getSimpleMask().add(simpleMaskPair);

        //set any validators

        LinkedList lnList = new LinkedList();
        lnList.add(new XMLNameValidator());
        epo.getValidatorMap().put("LocalName",lnList);

        LinkedList vlist = new LinkedList();
        vlist.add(new IntValidator(0,99));
        epo.getValidatorMap().put("StatusId",vlist);

        //set the getMethod so we can retrieve a new bean after we've inserted it
        try {
            Class[] args = new Class[1];
            args[0] = String.class;
            epo.setGetMethod(dpDao.getClass().getDeclaredMethod("getDataPropertyByURI",args));
        } catch (NoSuchMethodException e) {
            log.error("DatapropRetryController could not find the getDataPropertyByURI method in the facade");
        }

        VitroRequest vreq = new VitroRequest(request);
        Portal currPortal = vreq.getPortal();
        int currPortalId = 1;
        if (currPortal != null) {
            currPortalId = currPortal.getPortalId();
        }
        //make a postinsert pageforwarder that will send us to a new class's fetch screen
        epo.setPostInsertPageForwarder(new DataPropertyInsertPageForwarder(currPortalId));
        //make a postdelete pageforwarder that will send us to the list of properties
        epo.setPostDeletePageForwarder(new UrlForwarder("listDatatypeProperties?home="+currPortalId));

        //set up any listeners
        List changeListenerList = new ArrayList();
        changeListenerList.add(new EditProhibitionListener(getServletContext()));
        epo.setChangeListenerList(changeListenerList);


        FormObject foo = new FormObject();
        foo.setErrorMap(epo.getErrMsgMap()); // retain error messages from previous time through the form
        
        epo.setFormObject(foo);
        String html = FormUtils.htmlFormFromBean(objectForEditing,action,foo);
        //for now, this is also making the value hash - need to separate this out
        
        HashMap optionMap = new HashMap();
        List namespaceList = FormUtils.makeOptionListFromBeans(ontDao.getAllOntologies(),"URI","Name", ((objectForEditing.getNamespace()==null) ? "" : objectForEditing.getNamespace()), null, (objectForEditing.getNamespace()!=null));
	    namespaceList.add(new Option(getWebappDaoFactory().getDefaultNamespace(),"default"));
        optionMap.put("Namespace", namespaceList);
        
        List<Option> domainOptionList = FormUtils.makeVClassOptionList(getWebappDaoFactory(), objectForEditing.getDomainClassURI());
        domainOptionList.add(0, new Option("","(none specified)"));
        optionMap.put("DomainClassURI", domainOptionList);
        
        List datatypeOptionList = FormUtils.makeOptionListFromBeans(dDao.getAllDatatypes(),"Uri","Name",objectForEditing.getRangeDatatypeURI(),null);
        datatypeOptionList.add(0,new Option("http://www.w3.org/1999/02/22-rdf-syntax-ns#XMLLiteral","XML literal (allows XHTML markup)"));
        datatypeOptionList.add(0,new Option(null,"untyped (use if language tags desired)"));
        optionMap.put("RangeDatatypeURI", datatypeOptionList);
        
        List groupOptList = FormUtils.makeOptionListFromBeans(getWebappDaoFactory().getPropertyGroupDao().getPublicGroups(true),"URI","Name", ((objectForEditing.getGroupURI()==null) ? "" : objectForEditing.getGroupURI()), null, (objectForEditing.getGroupURI()!=null));
        groupOptList.add(0,new Option("","none"));
        optionMap.put("GroupURI", groupOptList);

        optionMap.put("HiddenFromDisplayBelowRoleLevelUsingRoleUri",RoleLevelOptionsSetup.getDisplayOptionsList(objectForEditing));    
        optionMap.put("ProhibitedFromUpdateBelowRoleLevelUsingRoleUri",RoleLevelOptionsSetup.getUpdateOptionsList(objectForEditing));

        foo.setOptionLists(optionMap);
        
        request.setAttribute("functional",objectForEditing.getFunctional());
        
        //checkboxes are pretty annoying : we don't know if someone *unchecked* a box, so we have to default to false on updates.
        if (objectForEditing.getURI() != null) {
        	objectForEditing.setFunctional(false);
        }

        foo.setErrorMap(epo.getErrMsgMap());

        RequestDispatcher rd = request.getRequestDispatcher(Controllers.BASIC_JSP);
        request.setAttribute("formHtml",html);
        request.setAttribute("bodyJsp","/templates/edit/formBasic.jsp");
        request.setAttribute("colspan","4");
        request.setAttribute("scripts","/templates/edit/formBasic.js");
        request.setAttribute("formJsp","/templates/edit/specific/dataprop_retry.jsp");
        request.setAttribute("title","Datatype Property Editing Form");
        request.setAttribute("_action",action);
        request.setAttribute("unqualifiedClassName","DatatypeProperty");
        setRequestAttributes(request,epo);

        try {
            rd.forward(request, response);
        } catch (Exception e) {
            log.error("DatatypeRetryController could not forward to view.");
            log.error(e.getMessage());
            log.error(e.getStackTrace());
        }

    }

    public void doGet (HttpServletRequest request, HttpServletResponse response) {
        doPost(request, response);
    }

    class DataPropertyInsertPageForwarder implements PageForwarder {

        private int portalId = 1;

        public DataPropertyInsertPageForwarder(int currPortalId) {
            portalId = currPortalId;
        }

        public void doForward(HttpServletRequest request, HttpServletResponse response, EditProcessObject epo){
            String newPropertyUrl = "datapropEdit?home="+portalId+"&uri=";
            DataProperty p = (DataProperty) epo.getNewBean();
            try {
                newPropertyUrl += URLEncoder.encode(p.getURI(),"UTF-8");
            } catch (Exception e) {
                log.error(this.getClass().getName()+" could not use UTF-8 encoding to encode new URL");
            }
            try {
                response.sendRedirect(newPropertyUrl);
            } catch (IOException ioe) {
                log.error("DataPropertyInsertPageForwarder could not send redirect.");
            }
        }

    }

}
