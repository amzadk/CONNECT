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
package gov.hhs.fha.nhinc.docquery.outbound;

import gov.hhs.fha.nhinc.aspect.OutboundProcessingEvent;
import gov.hhs.fha.nhinc.common.auditlog.AdhocQueryResponseMessageType;
import gov.hhs.fha.nhinc.common.eventcommon.AdhocQueryRequestEventType;
import gov.hhs.fha.nhinc.common.eventcommon.AdhocQueryRequestMessageType;
import gov.hhs.fha.nhinc.common.nhinccommon.AssertionType;
import gov.hhs.fha.nhinc.common.nhinccommon.HomeCommunityType;
import gov.hhs.fha.nhinc.common.nhinccommon.NhinTargetCommunitiesType;
import gov.hhs.fha.nhinc.common.nhinccommon.NhinTargetSystemType;
import gov.hhs.fha.nhinc.common.nhinccommon.QualifiedSubjectIdentifierType;
import gov.hhs.fha.nhinc.common.nhinccommonadapter.CheckPolicyRequestType;
import gov.hhs.fha.nhinc.common.nhinccommonadapter.CheckPolicyResponseType;
import gov.hhs.fha.nhinc.common.nhinccommonentity.RespondingGatewayCrossGatewayQuerySecuredRequestType;
import gov.hhs.fha.nhinc.connectmgr.ConnectionManager;
import gov.hhs.fha.nhinc.connectmgr.ConnectionManagerCache;
import gov.hhs.fha.nhinc.connectmgr.NhinEndpointManager;
import gov.hhs.fha.nhinc.connectmgr.UrlInfo;
import gov.hhs.fha.nhinc.docquery.DocQueryAuditLog;
import gov.hhs.fha.nhinc.docquery.aspect.AdhocQueryRequestDescriptionBuilder;
import gov.hhs.fha.nhinc.docquery.aspect.AdhocQueryResponseDescriptionBuilder;
import gov.hhs.fha.nhinc.docquery.entity.OutboundDocQueryDelegate;
import gov.hhs.fha.nhinc.docquery.entity.OutboundDocQueryOrchestratable;
import gov.hhs.fha.nhinc.docquery.entity.OutboundDocQueryOrchestratable_a0;
import gov.hhs.fha.nhinc.docquery.entity.OutboundDocQueryOrchestratable_a1;
import gov.hhs.fha.nhinc.docquery.entity.OutboundDocQueryProcessor;
import gov.hhs.fha.nhinc.gateway.aggregator.document.DocumentConstants;
import gov.hhs.fha.nhinc.gateway.executorservice.ExecutorServiceHelper;
import gov.hhs.fha.nhinc.gateway.executorservice.NhinCallableRequest;
import gov.hhs.fha.nhinc.gateway.executorservice.NhinTaskExecutor;
import gov.hhs.fha.nhinc.nhinclib.NhincConstants;
import gov.hhs.fha.nhinc.nhinclib.NhincConstants.NHIN_SERVICE_NAMES;
import gov.hhs.fha.nhinc.nhinclib.NullChecker;
import gov.hhs.fha.nhinc.orchestration.OutboundDelegate;
import gov.hhs.fha.nhinc.orchestration.OutboundResponseProcessor;
import gov.hhs.fha.nhinc.policyengine.PolicyEngineChecker;
import gov.hhs.fha.nhinc.policyengine.adapter.proxy.PolicyEngineProxy;
import gov.hhs.fha.nhinc.policyengine.adapter.proxy.PolicyEngineProxyObjectFactory;
import gov.hhs.fha.nhinc.properties.PropertyAccessor;
import gov.hhs.fha.nhinc.transform.document.DocumentTransformConstants;
import gov.hhs.fha.nhinc.util.format.PatientIdFormatUtil;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import oasis.names.tc.ebxml_regrep.xsd.query._3.AdhocQueryRequest;
import oasis.names.tc.ebxml_regrep.xsd.query._3.AdhocQueryResponse;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.AdhocQueryType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.RegistryObjectListType;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.SlotType1;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.ValueListType;
import oasis.names.tc.ebxml_regrep.xsd.rs._3.RegistryError;
import oasis.names.tc.ebxml_regrep.xsd.rs._3.RegistryErrorList;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class StandardOutboundDocQuery implements OutboundDocQuery {

    private Log log = null;
    private ExecutorService regularExecutor = null;
    private ExecutorService largejobExecutor = null;
    private ConnectionManager connectionManager;
    private DocQueryAuditLog auditLog = new DocQueryAuditLog();
    

    /**
     * Add default constructor that is used by test cases Note that implementations should always use constructor that
     * takes the executor services as input.
     */
    public StandardOutboundDocQuery() {
        // for this default test case, we just create default executor services
        // with a thread pool of 1
        log = createLogger();
        regularExecutor = Executors.newFixedThreadPool(1);
        largejobExecutor = Executors.newFixedThreadPool(1);
        connectionManager = ConnectionManagerCache.getInstance();
    }

    /**
     * We construct the orch impl class with references to both executor services that could be used for this particular
     * orchestration instance. Determination of which executor service to use (largejob or regular) is based on the size
     * of the correlationsResult and configs.
     * 
     * @param e regular executor.
     * @param le largejob executor.
     */
    public StandardOutboundDocQuery(ExecutorService e, ExecutorService le) {
        log = createLogger();
        setExecutorService(e, le);
        connectionManager = ConnectionManagerCache.getInstance();
    }

    /**
     * Sets the executor services to be used for fan out.
     * 
     * @param regularExecutor
     * @param largeJobExecutor
     */
    public void setExecutorService(ExecutorService e, ExecutorService le) {
        regularExecutor = e;
        largejobExecutor = le;
    }

    /**
     * @return Log log
     */
    protected Log createLogger() {
        return ((log != null) ? log : LogFactory.getLog(getClass()));
    }

    /**
     * 
     * @param adhocQueryRequest The AdhocQueryRequest message received.
     * @param assertion Assertion received.
     * @param targets Target to send request.
     * @return AdhocQueryResponse from Entity Interface.
     */
    @Override
    @OutboundProcessingEvent(beforeBuilder = AdhocQueryRequestDescriptionBuilder.class, afterReturningBuilder = AdhocQueryResponseDescriptionBuilder.class, serviceType = "Document Query", version = "")
    public AdhocQueryResponse respondingGatewayCrossGatewayQuery(AdhocQueryRequest adhocQueryRequest,
            AssertionType assertion, NhinTargetCommunitiesType targets) {
        log.debug("EntityDocQueryOrchImpl.respondingGatewayCrossGatewayQuery...");

        AdhocQueryResponse response = new AdhocQueryResponse();
        boolean responseIsSpecA0 = getApiLevel(getLocalHomeCommunityId(),
                NhincConstants.NHIN_SERVICE_NAMES.DOCUMENT_QUERY);

        log.debug("EntityDocQueryOrchImpl set responseIsSpecA0=" + responseIsSpecA0);
        
        OutboundDelegate nd = new OutboundDocQueryDelegate();
        OutboundResponseProcessor np = null;
        if (responseIsSpecA0) {
            np = new OutboundDocQueryProcessor(NhincConstants.GATEWAY_API_LEVEL.LEVEL_g0);
        } else {
            np = new OutboundDocQueryProcessor(NhincConstants.GATEWAY_API_LEVEL.LEVEL_g1);
        }

        List<UrlInfo> urlInfoList = null;
        boolean isTargeted = false;
        // audit initial request
        RespondingGatewayCrossGatewayQuerySecuredRequestType request = new RespondingGatewayCrossGatewayQuerySecuredRequestType();
        request.setAdhocQueryRequest(adhocQueryRequest);
        request.setNhinTargetCommunities(targets);
        auditInitialEntityRequest(request, assertion, auditLog);

        try {
            // Obtain all the URLs for the targets being sent to
            urlInfoList = getEndpointForNhinTargetCommunities(targets, NhincConstants.DOC_QUERY_SERVICE_NAME);

            isTargeted |= isTargeted(targets);
            
            if (!isTargeted || NullChecker.isNotNullish(urlInfoList)) {
                RegistryErrorList homeCommunityErrorList = new RegistryErrorList();
                // Validate that the message is not null

                if (adhocQueryRequest != null && adhocQueryRequest.getAdhocQuery() != null
                        && NullChecker.isNotNullish(adhocQueryRequest.getAdhocQuery().getSlot())) {
                    List<SlotType1> slotList = adhocQueryRequest.getAdhocQuery().getSlot();
                    
                    String localAA = new StandardOutboundDocQueryHelper().getLocalAssigningAuthority(slotList);
                    String uniquePatientId = new StandardOutboundDocQueryHelper().getUniquePatientId(slotList);
                    
                    log.debug("EntityDocQueryOrchImpl uniquePatientId: " + uniquePatientId + " and localAA=" + localAA);
                   
                    
                    List<QualifiedSubjectIdentifierType> correlationsResult = retrieveCorrelation(slotList,
                            urlInfoList, assertion, isTargeted, getLocalHomeCommunityId());

                    if (NullChecker.isNotNullish(correlationsResult)) {

                        List<NhinCallableRequest<OutboundDocQueryOrchestratable>> callableList = new ArrayList<NhinCallableRequest<OutboundDocQueryOrchestratable>>();
                        String transactionId = (UUID.randomUUID()).toString();

                        // we hold the error messages for any failed policy checks in policyErrList
                        RegistryErrorList policyErrList = new RegistryErrorList();

                        for (QualifiedSubjectIdentifierType identifier : correlationsResult) {
                            log.debug("EntityDocQueryOrchImpl correlated target aaid="
                                    + identifier.getAssigningAuthorityIdentifier());
                            NhinTargetSystemType target = new NhinTargetSystemType();
                            HomeCommunityType targetCommunity = getTargetCommunity(
                                    identifier.getAssigningAuthorityIdentifier(), localAA, getLocalHomeCommunityId());
                            if (targetCommunity != null) {
                                target.setHomeCommunity(targetCommunity);
                            }

                            log.debug("EntityDocQueryOrchImpl correlated target hcid="
                                    + targetCommunity.getHomeCommunityId());
                            if (isValidPolicy(adhocQueryRequest, assertion, targetCommunity)) {
                                
                                // clone the request
                                AdhocQueryRequest clonedRequest = cloneRequest(adhocQueryRequest);
                                setPatientIdOnRequest(clonedRequest,
                                        identifier.getSubjectIdentifier(), identifier.getAssigningAuthorityIdentifier());
                                // set the home community id to the target hcid
                                String targetHomeCommunityId = target.getHomeCommunity().getHomeCommunityId();
                                setTargetHomeCommunityId(clonedRequest, targetHomeCommunityId);
                                for (UrlInfo urlInfo : urlInfoList) {
                                    if (urlInfo.getHcid().equals(targetHomeCommunityId)) {
                                        if (NullChecker.isNotNullish(urlInfo.getUrl())) {
                                            OutboundDocQueryOrchestratable message = new OutboundDocQueryOrchestratable(
                                                    nd, np, null, null, assertion,
                                                    NhincConstants.DOC_QUERY_SERVICE_NAME, target, clonedRequest);
                                            callableList.add(new NhinCallableRequest<OutboundDocQueryOrchestratable>(
                                                    message));
                                        } else {
                                            homeCommunityErrorList = createHomeCommunityError(homeCommunityErrorList,
                                                    urlInfo);
                                        }
                                    }
                                }
                                if (log.isDebugEnabled()) {
                                log.debug("EntityDocQueryOrchImpl added NhinCallableRequest" + " for hcid="
                                        + target.getHomeCommunity().getHomeCommunityId() + " with subjectIdentifier"
                                        + identifier.getSubjectIdentifier() + "and assigning authority id"
                                        + identifier.getAssigningAuthorityIdentifier());
                                }
                            } else {
                                policyErrList = createPolicyError(policyErrList,
                                        identifier.getAssigningAuthorityIdentifier(), target.getHomeCommunity()
                                                .getHomeCommunityId());
                            }
                        }
                        // note that if responseIsSpecA0 taskexecutor is set to return OutboundDocQueryOrchestratable_a0
                        // else taskexecutor set to return OutboundDocQueryOrchestratable_a1
                        if (responseIsSpecA0) {
                            response = specResponseA0(correlationsResult, callableList, transactionId, response,
                                    policyErrList);
                        } else {
                            response = specResponseA1(correlationsResult, callableList, transactionId, response,
                                    policyErrList);
                        }

                        log.debug("EntityDocQueryOrchImpl taskexecutor done and received response");

                    } else {
                        response = createPatientIdError(slotList, response);
                    }
                } else {
                    log.error("Incomplete doc query message");
                    response = createErrorResponse("XDSRepositoryError", "Incomplete/empty adhocquery message");
                }
                response = addHomeCommunityError(response, homeCommunityErrorList);
            } else {
                log.error("No URLs found for targeted DocQueryRequest");
                response = createErrorResponse(DocumentConstants.XDS_QUERY_ERRORCODE_HOMECOMMUNITY_ERROR,
                        "No URLs found for targeted DocQueryRequest");
                RegistryObjectListType regObjList = new RegistryObjectListType();
                response.setRegistryObjectList(regObjList);
            }
        } catch (Exception e) {
            log.error("Error occured processing doc query on entity interface: " + e.getMessage(), e);
            response = createErrorResponse("XDSRepositoryError", "Fault encountered processing internal document query"
                    + " exception=" + e.getMessage());
        }
        auditDocQueryResponse(response, assertion, auditLog);
        if (log.isDebugEnabled()) {
            log.debug("Exiting EntityDocQueryOrchImpl.respondingGatewayCrossGatewayQuery...");
        }
        return response;
    }

    /**
     * @param request
     * @param subjectIdentifier
     * @param assigningAuthorityIdentifier
     * @return
     */
    protected String setPatientIdOnRequest(AdhocQueryRequest request, String subjectIdentifier,
            String assigningAuthorityIdentifier) {
        String formattedPatientId = PatientIdFormatUtil.hl7EncodePatientId(subjectIdentifier,
                assigningAuthorityIdentifier);
        List<SlotType1> slotType1 = request.getAdhocQuery().getSlot();
        Iterator<SlotType1> iterSlotType1 = slotType1.iterator();
        while (iterSlotType1.hasNext()) {
            SlotType1 slot = iterSlotType1.next();
            if ((slot.getName() != null)
                    && (slot.getName().equals(DocumentTransformConstants.EBXML_DOCENTRY_PATIENT_ID))) {
                ValueListType slotValueList = new ValueListType();
                slotValueList.getValue().add(formattedPatientId);
                slot.setValueList(slotValueList);
            }
        }
        return formattedPatientId;
    }

    /**
     * @param targets
     * @return
     */
    protected boolean isTargeted(NhinTargetCommunitiesType targets) {
        return targets != null && NullChecker.isNotNullish(targets.getNhinTargetCommunity());
    }

    /**
     * @param slotList
     * @param response
     * @return
     */
    private AdhocQueryResponse createPatientIdError(List<SlotType1> slotList, AdhocQueryResponse response) {
        boolean slotPresent = patientIdSlot(slotList);
        if (!slotPresent) {
            response = createErrorResponse(DocumentConstants.EBXML_DOCQUERY_STORED_QUERY_MISSIN_PARAM,
                    "Required parameter XDSDocumentEntryPatientId, not present in query request.");
        } else {
            if (log.isDebugEnabled()) {
                log.debug("EntityDocQueryOrchImpl no patient found, return empty list");
            }
            response = createErrorResponse("XDSUnknownPatientId", "No patient correlations found.");
        }
        return response;
    }

    /**
     * @param request
     * @param sTargetHomeCommunityId
     * @return
     */
    private AdhocQueryRequest setTargetHomeCommunityId(AdhocQueryRequest request, String sTargetHomeCommunityId) {
        if (NullChecker.isNotNullish(sTargetHomeCommunityId)) {
            if (!(sTargetHomeCommunityId.startsWith("urn:oid:"))) {
                sTargetHomeCommunityId = "urn:oid:" + sTargetHomeCommunityId;
            }
            request.getAdhocQuery().setHome(sTargetHomeCommunityId);
        }
        return request;
    }

    /**
     * @param response
     * @param homeCommunityErrorList
     * @return
     */
    private AdhocQueryResponse addHomeCommunityError(AdhocQueryResponse response,
            RegistryErrorList homeCommunityErrorList) {
        if (homeCommunityErrorList.getRegistryError() != null && !homeCommunityErrorList.getRegistryError().isEmpty()) {
            if (response.getRegistryErrorList() == null) {
                response.setRegistryErrorList(homeCommunityErrorList);
            } else if (response.getRegistryErrorList().getRegistryError() != null) {
                response.getRegistryErrorList().getRegistryError().addAll(homeCommunityErrorList.getRegistryError());
            }
            if (response.getStatus().equals(DocumentConstants.XDS_QUERY_RESPONSE_STATUS_SUCCESS)) {
                response.setStatus(DocumentConstants.XDS_QUERY_RESPONSE_STATUS_PARTIALSUCCESS);
            }
        }
        return response;
    }

    /**
     * @param policyErrList
     * @param assigningAuthorityIdentifier
     * @param string
     * @return
     */
    private RegistryErrorList createPolicyError(RegistryErrorList policyErrList, String assigningAuthorityIdentifier,
            String target) {
        RegistryError regErr = new RegistryError();
        regErr.setCodeContext("Policy Check Failed for homeId=" + target + " and aaId=" + assigningAuthorityIdentifier);
        regErr.setErrorCode("XDSRepositoryError");
        regErr.setSeverity(NhincConstants.XDS_REGISTRY_ERROR_SEVERITY_ERROR);
        policyErrList.getRegistryError().add(regErr);
        return policyErrList;
    }

    /**
     * @param homeCommunityErrorList RegistryErrorList to add HomeCommunityError for DocQuery.
     * @param urlInfo urlInfo for the targeted community.
     * @return
     */
    private RegistryErrorList createHomeCommunityError(RegistryErrorList homeCommunityErrorList, UrlInfo urlInfo) {
        RegistryError regErr = new RegistryError();
        regErr.setCodeContext("No URLs found for targeted DocQueryRequest for homecommunity:" + urlInfo.getHcid());
        regErr.setErrorCode(DocumentConstants.XDS_QUERY_ERRORCODE_HOMECOMMUNITY_ERROR);
        regErr.setSeverity(NhincConstants.XDS_REGISTRY_ERROR_SEVERITY_ERROR);
        homeCommunityErrorList.getRegistryError().add(regErr);
        return homeCommunityErrorList;
    }

    /**
     * @param localHomeCommunityId Requesting gateway CommunityId.
     * @param documentQuery Name of Service (DocumentQuery)
     * @return gateway APILevel (g0, g1)
     */
    // quick rig for testing to switch between a0 and a1
    // note that a0 and a1 would be handled by different methods if they were different
    protected boolean getApiLevel(String localHomeCommunityId, NHIN_SERVICE_NAMES documentQuery) {
        boolean responseIsSpecA0 = false;
        NhinEndpointManager nem = new NhinEndpointManager();
        NhincConstants.GATEWAY_API_LEVEL gatewayLevel = nem.getApiVersion(getLocalHomeCommunityId(),
                NhincConstants.NHIN_SERVICE_NAMES.DOCUMENT_QUERY);
        switch (gatewayLevel) {
        case LEVEL_g0:
            responseIsSpecA0 = true;
            break;
        case LEVEL_g1:
            responseIsSpecA0 = false;
            break;
        default:
            responseIsSpecA0 = true;
            break;
        }
        return responseIsSpecA0;
    }

    /**
     * This method returns url list for the targets.
     * 
     * @param targets Targets received.
     * @param docQueryServiceName ServiceName (DocumentQuery)
     * @return list of url's of targets.
     */
    protected List<UrlInfo> getEndpointForNhinTargetCommunities(NhinTargetCommunitiesType targets,
            String docQueryServiceName) {
        List<UrlInfo> urlInfoList = null;
        try {
            urlInfoList = connectionManager.getEndpointURLFromNhinTargetCommunities(targets,
                    NhincConstants.DOC_QUERY_SERVICE_NAME);
        } catch (Exception ex) {
            log.error("EntityDocQueryOrchImpl Failed to obtain target URLs", ex);
        }
        return urlInfoList;
    }

    /**
     * @param assigningAuthorityIdentifier Target AA identifier received from correlation
     * @param localAA AssigningAuthorityId of local HomeCommunityId.
     * @param localHomeCommunityId Home CommunityId of requesting Gateway.
     * @return targetCommunity (Nhin)
     */
    protected HomeCommunityType getTargetCommunity(String assigningAuthorityIdentifier, String localAA,
            String localHomeCommunityId) {
        HomeCommunityType targetCommunity = new StandardOutboundDocQueryHelper().lookupHomeCommunityId(
                assigningAuthorityIdentifier, localAA, localHomeCommunityId);
        return targetCommunity;
    }

    /**
     * @param slotList slotList extracted from AdhocQuery Request message.
     * @param urlInfoList List of url's of targets received from ConnectionManagerCache.
     * @param assertion Assertion received.
     * @param isTargeted Targetted to any community (true); otherwise false.S
     * @param localHomeCommunityId HomeCommunityId of requesting Gateway.S
     * @return List of Qualified Identifiers from targeted communities.
     */
    protected List<QualifiedSubjectIdentifierType> retrieveCorrelation(List<SlotType1> slotList,
            List<UrlInfo> urlInfoList, AssertionType assertion, boolean isTargeted, String localHomeCommunityId) {

        List<QualifiedSubjectIdentifierType> correlationResult = new ArrayList<QualifiedSubjectIdentifierType>();
        correlationResult = new StandardOutboundDocQueryHelper().retreiveCorrelations(slotList, urlInfoList, assertion,
                isTargeted, localHomeCommunityId);
        return correlationResult;
    }

    /**
     * @param slotList slotList extracted from AdhocQuery Request message.
     * @return patientIdslot from slotList passed.
     */
    protected boolean patientIdSlot(List<SlotType1> slotList) {
        return (new StandardOutboundDocQueryHelper().patientIdSlot(slotList));

    }

    /**
     * @param request The AdhocQuery Request to be audited.
     * @param assertion Assertion received.
     * @param auditLog get instance of DocQueryAuditLog Object.
     */
    protected void auditInitialEntityRequest(RespondingGatewayCrossGatewayQuerySecuredRequestType request,
            AssertionType assertion, DocQueryAuditLog auditLog) {

        if (auditLog != null) {
            auditLog.auditDQRequest(request.getAdhocQueryRequest(), assertion,
                    NhincConstants.AUDIT_LOG_INBOUND_DIRECTION, NhincConstants.AUDIT_LOG_ENTITY_INTERFACE,
                    getLocalHomeCommunityId());
        }
    }

    /**
     * @param response DocQuery Response to be audit logged.
     * @param assertion Assertion received.
     * @param auditLog get instance of DocQueryAuditLog Object.
     */
    protected void auditDocQueryResponse(AdhocQueryResponse response, AssertionType assertion, DocQueryAuditLog auditLog) {

        if (auditLog != null) {
            AdhocQueryResponseMessageType auditMsg = new AdhocQueryResponseMessageType();
            auditMsg.setAdhocQueryResponse(response);
            auditMsg.setAssertion(assertion);
            auditLog.auditDQResponse(response, assertion, NhincConstants.AUDIT_LOG_OUTBOUND_DIRECTION,
                    NhincConstants.AUDIT_LOG_ENTITY_INTERFACE, getLocalHomeCommunityId());
        }
    }

    /**
     * @param request AdhocQuery request received.
     * @return AdhocQUery Request.
     */
    protected AdhocQueryRequest cloneRequest(AdhocQueryRequest request) {
        AdhocQueryRequest newRequest = new AdhocQueryRequest();
        AdhocQueryType currentQuery = request.getAdhocQuery();

        AdhocQueryType newQuery = new AdhocQueryType();
        List<SlotType1> newslotList = new ArrayList<SlotType1>();
        List<SlotType1> slotList = currentQuery.getSlot();
        for (SlotType1 slot : slotList) {
            SlotType1 newSlot = new SlotType1();
            newSlot.setName(slot.getName());
            newSlot.setSlotType(slot.getSlotType());

            ValueListType valueListType = new ValueListType();
            List<String> valueList = new ArrayList<String>();
            for (String value : slot.getValueList().getValue()) {
                valueList.add(value);
            }
            valueListType.getValue().addAll(valueList);
            newSlot.setValueList(valueListType);

            newslotList.add(newSlot);
        }
        newQuery.getSlot().addAll(newslotList);

        newQuery.getClassification().addAll(currentQuery.getClassification());
        newQuery.getExternalIdentifier().addAll(currentQuery.getExternalIdentifier());

        newQuery.setDescription(currentQuery.getDescription());
        newQuery.setHome(currentQuery.getHome());
        newQuery.setId(currentQuery.getId());
        newQuery.setLid(currentQuery.getLid());
        newQuery.setName(currentQuery.getName());
        newQuery.setObjectType(currentQuery.getObjectType());
        newQuery.setQueryExpression(currentQuery.getQueryExpression());
        newQuery.setStatus(currentQuery.getStatus());
        newQuery.setVersionInfo(currentQuery.getVersionInfo());
        newRequest.setAdhocQuery(newQuery);

        newRequest.setComment(request.getComment());
        // newRequest.setFederated(request.isFederated());
        newRequest.setFederation(request.getFederation());
        newRequest.setId(request.getId());
        newRequest.setMaxResults(request.getMaxResults());
        newRequest.setResponseOption(request.getResponseOption());
        newRequest.setStartIndex(request.getStartIndex());

        log.debug("EntityDocQueryOrchImpl::cloneRequest generated new AdhocQueryRequest");
        return newRequest;
    }

    /**
     * @return local HomeCommunityId from gateway.property file.
     */
    protected String getLocalHomeCommunityId() {
        String sHomeCommunity = null;
        try {
            sHomeCommunity = PropertyAccessor.getInstance().getProperty(NhincConstants.GATEWAY_PROPERTY_FILE,
                    NhincConstants.HOME_COMMUNITY_ID_PROPERTY);
        } catch (Exception ex) {
            log.error(ex.getMessage());
        }
        return sHomeCommunity;
    }

    /**
     * Policy Check verification done here.
     * 
     * @param queryRequest The AdhocQuery Request recieved.
     * @param assertion Assertion received.
     * @param targetCommunity TargetCommunities where request needs to be send.
     * @return boolean return true if decision is PERMIT; else Denied
     */
    protected boolean isValidPolicy(AdhocQueryRequest queryRequest, AssertionType assertion,
            HomeCommunityType targetCommunity) {
        boolean isValid = false;
        AdhocQueryRequestEventType checkPolicy = new AdhocQueryRequestEventType();
        AdhocQueryRequestMessageType checkPolicyMessage = new AdhocQueryRequestMessageType();
        checkPolicyMessage.setAdhocQueryRequest(queryRequest);
        checkPolicyMessage.setAssertion(assertion);
        checkPolicy.setMessage(checkPolicyMessage);
        checkPolicy.setDirection(NhincConstants.POLICYENGINE_OUTBOUND_DIRECTION);
        checkPolicy.setInterface(NhincConstants.AUDIT_LOG_ENTITY_INTERFACE);
        checkPolicy.setReceivingHomeCommunity(targetCommunity);
        PolicyEngineChecker policyChecker = new PolicyEngineChecker();
        CheckPolicyRequestType policyReq = policyChecker.checkPolicyAdhocQuery(checkPolicy);
        PolicyEngineProxyObjectFactory policyEngFactory = new PolicyEngineProxyObjectFactory();
        PolicyEngineProxy policyProxy = policyEngFactory.getPolicyEngineProxy();
        CheckPolicyResponseType policyResp = policyProxy.checkPolicy(policyReq, assertion);
        /* if response='permit' */
        if (policyResp.getResponse().getResult().get(0).getDecision().value().equals(NhincConstants.POLICY_PERMIT)) {
            isValid = true;
        }
        return isValid;
    }

    /**
     * This method returns Response with RegistryErrorList if there is any failure.
     * 
     * @param errorCode The ErrorCode that needs to be set to the AdhocQueryResponse (Errorcodes are defined in spec).
     * @param codeContext The codecontext defines the reason of failure of AdhocQueryRequest.
     * @return AdhocQueryResponse.
     */
    private AdhocQueryResponse createErrorResponse(String errorCode, String codeContext) {
        AdhocQueryResponse response = new AdhocQueryResponse();
        response.setRegistryErrorList(createErrorListWithError(errorCode, codeContext));
        response.setStatus(DocumentConstants.XDS_QUERY_RESPONSE_STATUS_FAILURE);

        RegistryObjectListType regObjectList = new RegistryObjectListType();
        response.setRegistryObjectList(regObjectList);

        return response;
    }

    /**
     * This method returns RegistryErrorList for AdhocQueryResponse.
     * 
     * @param errorCode The ErrorCode that needs to be set to the AdhocQueryResponse (Errorcodes are defined in spec).
     * @param codeContext The codecontext defines the reason of failure of AdhocQueryRequest.
     * @return registryErrorList.
     */
    private RegistryErrorList createErrorListWithError(String errorCode, String codeContext) {
        RegistryErrorList regErrList = new RegistryErrorList();
        RegistryError regErr = new RegistryError();
        regErrList.getRegistryError().add(regErr);
        regErr.setCodeContext(codeContext);
        regErr.setErrorCode(errorCode);
        regErr.setSeverity(NhincConstants.XDS_REGISTRY_ERROR_SEVERITY_ERROR);
        return regErrList;
    }

    /**
     * @param correlationsResult List of correlated Patients identified from targets.
     * @param callableList Nhin callable List for DocQuery message.
     * @param transactionId TransactionId for the message.
     * @param response Null AdhocQuery Response.
     * @param policyErrList Policy Error List if present; else empty.
     * @return AdhocQueryresponse from entity Interface.
     * @throws InterruptedException Throws Exception.
     * @throws ExecutionException Throws Exception.
     */
    protected AdhocQueryResponse specResponseA0(List<QualifiedSubjectIdentifierType> correlationsResult,
            List<NhinCallableRequest<OutboundDocQueryOrchestratable>> callableList, String transactionId,
            AdhocQueryResponse response, RegistryErrorList policyErrList) throws InterruptedException,
            ExecutionException {
        OutboundDocQueryOrchestratable_a0 orchResponseg0 = null;
        if (!callableList.isEmpty()) {
            NhinTaskExecutor<OutboundDocQueryOrchestratable_a0, OutboundDocQueryOrchestratable> dqexecutor = new NhinTaskExecutor<OutboundDocQueryOrchestratable_a0, OutboundDocQueryOrchestratable>(
                    ExecutorServiceHelper.checkExecutorTaskIsLarge(correlationsResult.size()) ? largejobExecutor
                            : regularExecutor, callableList, transactionId);
            dqexecutor.executeTask();
            orchResponseg0 = (OutboundDocQueryOrchestratable_a0) dqexecutor.getFinalResponse();
            response = orchResponseg0.getCumulativeResponse();
        } else {
            log.debug("No callable requests were sent out.  " + "Setting response to failure.");
            response.setStatus(DocumentConstants.XDS_QUERY_RESPONSE_STATUS_FAILURE);

            if (response.getRegistryErrorList() == null) {
                RegistryErrorList regErrList = createErrorListWithError(
                        DocumentConstants.XDS_RETRIEVE_ERRORCODE_REPOSITORY_ERROR,
                        "Unable to find any callable targets.");
                response.setRegistryErrorList(regErrList);
            } else {
                RegistryError regErr = new RegistryError();
                regErr.setCodeContext("Unable to find any callable targets.");
                regErr.setErrorCode(DocumentConstants.XDS_RETRIEVE_ERRORCODE_REPOSITORY_ERROR);
                regErr.setSeverity(NhincConstants.XDS_REGISTRY_ERROR_SEVERITY_ERROR);
                response.getRegistryErrorList().getRegistryError().add(regErr);
            }
        }

        // add any errors from policyErrList to response
        if (policyErrList.getRegistryError() != null && !policyErrList.getRegistryError().isEmpty()) {
            if (response.getRegistryErrorList() == null) {
                response.setRegistryErrorList(policyErrList);
            } else if (response.getRegistryErrorList().getRegistryError() != null) {
                response.getRegistryErrorList().getRegistryError().addAll(policyErrList.getRegistryError());
            }
        }

        if (response.getRegistryObjectList() == null) {
            RegistryObjectListType regObjectList = new RegistryObjectListType();
            response.setRegistryObjectList(regObjectList);
        }

        return response;

    }

    /**
     * @param correlationsResult List of correlated Patients identified from targets.
     * @param callableList Nhin callable List for DocQuery message.
     * @param transactionId TransactionId for the message.
     * @param response Null AdhocQuery Response.
     * @param policyErrList Policy Error List if present; else empty.
     * @return AdhocQueryresponse from entity Interface.
     * @throws InterruptedException Throws Exception.
     * @throws ExecutionException Throws Exception.
     */
    protected AdhocQueryResponse specResponseA1(List<QualifiedSubjectIdentifierType> correlationsResult,
            List<NhinCallableRequest<OutboundDocQueryOrchestratable>> callableList, String transactionId,
            AdhocQueryResponse response, RegistryErrorList policyErrList) throws InterruptedException,
            ExecutionException {
        OutboundDocQueryOrchestratable_a1 orchResponseg1 = null;
        if (!callableList.isEmpty()) {
            NhinTaskExecutor<OutboundDocQueryOrchestratable_a1, OutboundDocQueryOrchestratable> dqexecutor = new NhinTaskExecutor<OutboundDocQueryOrchestratable_a1, OutboundDocQueryOrchestratable>(
                    ExecutorServiceHelper.getInstance().checkExecutorTaskIsLarge(correlationsResult.size()) ? largejobExecutor
                            : regularExecutor, callableList, transactionId);
            dqexecutor.executeTask();
            orchResponseg1 = (OutboundDocQueryOrchestratable_a1) dqexecutor.getFinalResponse();
            response = orchResponseg1.getCumulativeResponse();
        } else {
            log.debug("No callable requests were sent out.  Setting response to failure.");
            response.setStatus(DocumentConstants.XDS_QUERY_RESPONSE_STATUS_FAILURE);

            if (response.getRegistryErrorList() == null) {
                RegistryErrorList regErrList = createErrorListWithError(
                        DocumentConstants.XDS_RETRIEVE_ERRORCODE_REPOSITORY_ERROR,
                        "Unable to find any callable targets.");
                response.setRegistryErrorList(regErrList);
            } else {
                RegistryError regErr = new RegistryError();
                regErr.setCodeContext("Unable to find any callable targets.");
                regErr.setErrorCode(DocumentConstants.XDS_RETRIEVE_ERRORCODE_REPOSITORY_ERROR);
                regErr.setSeverity(NhincConstants.XDS_REGISTRY_ERROR_SEVERITY_ERROR);
                response.getRegistryErrorList().getRegistryError().add(regErr);
            }
        }

        // add any errors from policyErrList to response
        if (policyErrList.getRegistryError() != null && !policyErrList.getRegistryError().isEmpty()) {
            if (response.getRegistryErrorList() == null) {
                response.setRegistryErrorList(policyErrList);
            } else if (response.getRegistryErrorList().getRegistryError() != null) {
                response.getRegistryErrorList().getRegistryError().addAll(policyErrList.getRegistryError());
            }
        }

        if (response.getRegistryObjectList() == null) {
            RegistryObjectListType regObjectList = new RegistryObjectListType();
            response.setRegistryObjectList(regObjectList);
        }

        return response;

    }

}
