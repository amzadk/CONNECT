/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package gov.hhs.fha.nhinc.docquery.nhin.deferred.response.proxy;

import gov.hhs.fha.nhinc.proxy.ComponentProxyObjectFactory;

/**
 *
 * @author jhoppesc
 */
public class NhinDocQueryDeferredResponseProxyObjectFactory extends ComponentProxyObjectFactory {
    private static final String CONFIG_FILE_NAME = "DocumentQueryDeferredProxyConfig.xml";
    private static final String BEAN_NAME = "nhindocquerydeferredresponse";

    protected String getConfigFileName()
    {
        return CONFIG_FILE_NAME;
    }

    public NhinDocQueryDeferredResponseProxy getNhinDocQueryDeferredResponseProxy()
    {
        return getBean(BEAN_NAME, NhinDocQueryDeferredResponseProxy.class);
   }

}
