/* $This file is distributed under the terms of the license in /doc/license.txt$ */
package edu.cornell.mannlib.vitro.webapp.servlet.setup;

import java.sql.SQLException;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;

import org.apache.commons.dbcp.BasicDataSource;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.hp.hpl.jena.sdb.SDB;
import com.hp.hpl.jena.sdb.SDBFactory;
import com.hp.hpl.jena.sdb.Store;
import com.hp.hpl.jena.sdb.StoreDesc;
import com.hp.hpl.jena.sdb.sql.SDBConnection;
import com.hp.hpl.jena.sdb.store.DatabaseType;
import com.hp.hpl.jena.sdb.store.LayoutType;
import com.hp.hpl.jena.sdb.util.StoreUtils;

import edu.cornell.mannlib.vitro.webapp.config.ConfigurationProperties;
import edu.cornell.mannlib.vitro.webapp.rdfservice.RDFService;
import edu.cornell.mannlib.vitro.webapp.rdfservice.RDFServiceFactory;
import edu.cornell.mannlib.vitro.webapp.rdfservice.filter.SameAsFilteringRDFServiceFactory;
import edu.cornell.mannlib.vitro.webapp.rdfservice.impl.RDFServiceFactorySingle;
import edu.cornell.mannlib.vitro.webapp.rdfservice.impl.RDFServiceUtils;
import edu.cornell.mannlib.vitro.webapp.rdfservice.impl.sdb.RDFServiceSDB;
import edu.cornell.mannlib.vitro.webapp.rdfservice.impl.sparql.RDFServiceSparql;
import edu.cornell.mannlib.vitro.webapp.startup.StartupStatus;

public class RDFServiceSetup extends JenaDataSourceSetupBase 
implements javax.servlet.ServletContextListener {
    private static final Log log = LogFactory.getLog(RDFServiceSetup.class);

    @Override
    public void contextDestroyed(ServletContextEvent arg0) {
        // nothing to do   
    }

    @Override
    public void contextInitialized(ServletContextEvent sce) {    
        ServletContext ctx = sce.getServletContext();
        StartupStatus ss = StartupStatus.getBean(ctx);    
        try {
            String endpointURI = ConfigurationProperties.getBean(sce).getProperty(
                    "VitroConnection.DataSource.endpointURI");
            if (endpointURI != null) {
                useEndpoint(endpointURI, ctx);
            } else {
                useSDB(ctx, ss);
            }
            
            //experimental
            //RDFServiceFactory factory = RDFServiceUtils.getRDFServiceFactory(ctx);
            //RDFServiceUtils.setRDFServiceFactory(ctx, new SameAsFilteringRDFServiceFactory(factory));
            
        } catch (SQLException e) {
            ss.fatal(this, "Exception in RDFServiceSetup", e);
        }        
    }
    
    private void useEndpoint(String endpointURI, ServletContext ctx) {
        RDFService rdfService = new RDFServiceSparql(endpointURI);
        RDFServiceFactory rdfServiceFactory = new RDFServiceFactorySingle(rdfService);
        RDFServiceUtils.setRDFServiceFactory(ctx, rdfServiceFactory);
        log.info("Using endpoint at " + endpointURI);
    }

    private void useSDB(ServletContext ctx, StartupStatus ss) throws SQLException {
        BasicDataSource bds = getApplicationDataSource(ctx);
        if( bds == null ){
            ss.fatal(this, "A DataSource must be setup before SDBSetup "+
                    "is run. Make sure that JenaPersistentDataSourceSetup runs before "+
                    "SDBSetup.");
            return;
        }
        
        // union default graph
        SDB.getContext().set(SDB.unionDefaultGraph, true) ;

        StoreDesc storeDesc = makeStoreDesc(ctx);
        setApplicationStoreDesc(storeDesc, ctx);     
        
        Store store = connectStore(bds, storeDesc);
        setApplicationStore(store, ctx);
        
        if (!isSetUp(store)) {            
            JenaPersistentDataSourceSetup.thisIsFirstStartup();
            setupSDB(ctx, store);
        }
        
        RDFService rdfService = new RDFServiceSDB(bds, storeDesc);
        RDFServiceFactory rdfServiceFactory = new RDFServiceFactorySingle(rdfService);
        RDFServiceUtils.setRDFServiceFactory(ctx, rdfServiceFactory);
        
        log.info("SDB store ready for use");
        
    }
    
    
    /**
     * Tests whether an SDB store has been formatted and populated for use.
     * @param store
     * @return
     */
    private boolean isSetUp(Store store) throws SQLException {
        if (!(StoreUtils.isFormatted(store))) {
            return false;
        }
        
        // even if the store exists, it may be empty
        
        try {
            return (SDBFactory.connectNamedModel(
                    store, 
                    JenaDataSourceSetupBase.JENA_TBOX_ASSERTIONS_MODEL))
                            .size() > 0;    
        } catch (Exception e) { 
            return false;
        }
    }
    
    public static StoreDesc makeStoreDesc(ServletContext ctx) {
        String layoutStr = ConfigurationProperties.getBean(ctx).getProperty(
                "VitroConnection.DataSource.sdb.layout", "layout2/hash");
        String dbtypeStr = ConfigurationProperties.getBean(ctx).getProperty(
                "VitroConnection.DataSource.dbtype", "MySQL");
       return new StoreDesc(
                LayoutType.fetch(layoutStr),
                DatabaseType.fetch(dbtypeStr) );
    }

    public static Store connectStore(BasicDataSource bds, StoreDesc storeDesc)
            throws SQLException {
        SDBConnection conn = new SDBConnection(bds.getConnection());
        return SDBFactory.connectStore(conn, storeDesc);
    }

    protected static void setupSDB(ServletContext ctx, Store store) {
        log.info("Initializing SDB store");
        store.getTableFormatter().create();
        store.getTableFormatter().truncate();
    }

}
