/*
 * Copyright (c) 2012, United States Government, as represented by the Secretary of Health and Human Services.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above
 *       copyright notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the documentation
 *       and/or other materials provided with the distribution.
 *     * Neither the name of the United States Government nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE UNITED STATES GOVERNMENT BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package gov.hhs.fha.nhinc.connectmgr.uddi.proxy;

import org.apache.log4j.Logger;

import org.uddi.api_v3.BusinessDetail;
import org.uddi.api_v3.BusinessList;
import org.uddi.api_v3.FindBusiness;
import org.uddi.api_v3.FindQualifiers;
import org.uddi.api_v3.GetBusinessDetail;
import org.uddi.api_v3.Name;

import gov.hhs.fha.nhinc.messaging.client.CONNECTClient;
import gov.hhs.fha.nhinc.messaging.service.port.ServicePortDescriptor;
import gov.hhs.fha.nhinc.nhin_uddi_api_v3.UDDIInquiryPortType;

/**
 *
 * @author richard.ettema
 */
public class UDDIFindBusinessProxyJuddiV3Impl extends UDDIFindBusinessProxyBase {

    private static final Logger LOG = Logger.getLogger(UDDIFindBusinessProxyJuddiV3Impl.class);

    @Override
    public BusinessList findBusinessesFromUDDI() throws UDDIFindBusinessException {
        LOG.debug("Using jUDDI V3 Implementation for UDDI Business Info Service");

        BusinessList oBusinessList = null;

        try {
            // load relevant property info
            loadProperties();

            // Make the call...
            // -----------------
            FindBusiness oSearchParams = new FindBusiness();

            Name findName = new Name();
            FindQualifiers qualifiers = new FindQualifiers();

            findName.setValue("%");
            qualifiers.getFindQualifier().add("approximateMatch");

            oSearchParams.getName().add(findName);
            oSearchParams.setFindQualifiers(qualifiers);

            int maxRows = getMaxResults();
            
            if(maxRows > 0){
                 oSearchParams.setMaxRows(getMaxResults());
            }
           
            ServicePortDescriptor<UDDIInquiryPortType> portDescriptor = new UDDIFindBusinessProxyServicePortDescriptor();
            CONNECTClient<UDDIInquiryPortType> client = getCONNECTClientUnsecured(portDescriptor, uddiInquiryUrl, null);
            oBusinessList = (BusinessList) client.invokePort(UDDIInquiryPortType.class, "findBusiness", oSearchParams);

        } catch (Exception e) {
            String sErrorMessage = "Failed to call 'find_business' web service on the NHIN UDDI server.  Error: "
                    + e.getMessage();
            LOG.error(sErrorMessage, e);
            throw new UDDIFindBusinessException(sErrorMessage, e);
        }

        return oBusinessList;
    }

    @Override
    public BusinessDetail getBusinessDetail(GetBusinessDetail searchParams) throws UDDIFindBusinessException {
        
        return super.getBusinessDetail(searchParams);
    }

}