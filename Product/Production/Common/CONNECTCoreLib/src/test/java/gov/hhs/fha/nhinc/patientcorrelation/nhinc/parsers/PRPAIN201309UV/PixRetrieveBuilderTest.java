package gov.hhs.fha.nhinc.patientcorrelation.nhinc.parsers.PRPAIN201309UV;

import static org.junit.Assert.assertEquals;

import java.util.List;

import gov.hhs.fha.nhinc.common.nhinccommon.AssertionType;
import gov.hhs.fha.nhinc.common.nhinccommon.QualifiedSubjectIdentifierType;
import gov.hhs.fha.nhinc.common.patientcorrelationfacade.RetrievePatientCorrelationsRequestType;
import java.util.ArrayList;

import org.hl7.v3.PRPAIN201309UV02;
import org.junit.Test;

/**
 * @author achidambaram
 *
 */
public class PixRetrieveBuilderTest {

    @Test
    public void testcreatePixRetrieve() {
        PixRetrieveBuilder builder = new PixRetrieveBuilder();
        PRPAIN201309UV02 pixRetrieve = new PRPAIN201309UV02();
        pixRetrieve = builder.createPixRetrieve(createRetrievePatientCorrelationsRequest());
        assertEquals(pixRetrieve.getControlActProcess().getAuthorOrPerformer().get(0).getAssignedPerson()
            .getValue().getId().get(0).getNullFlavor().get(0), "NA");
        assertEquals(pixRetrieve.getInteractionId().getExtension(), "PRPA_IN201309");
        assertEquals(pixRetrieve.getId().getRoot(), "1.1");
    }

    @Test
    public void testcreatePixRetrieveWhenAANull() {
        PixRetrieveBuilder builder = new PixRetrieveBuilder();
        PRPAIN201309UV02 pixRetrieve = new PRPAIN201309UV02();
        pixRetrieve = builder.createPixRetrieve(createRetrievePatientCorrelationsRequestWhenAANull());
        assertEquals(pixRetrieve.getControlActProcess().getAuthorOrPerformer().get(0).getAssignedPerson()
            .getValue().getId().get(0).getNullFlavor().get(0), "NA");
        assertEquals(pixRetrieve.getInteractionId().getExtension(), "PRPA_IN201309");
        assertEquals(pixRetrieve.getId().getRoot(), "1.1");

    }

    @Test
    public void testcreatePixRetrieveWithTargetCommunityPrefix() {
        PixRetrieveBuilder builder = new PixRetrieveBuilder();
        List<String> homeCommIds = null;

        homeCommIds = builder.stripCommunityIdsPrefix(null);
        assertEquals(homeCommIds, null);

        homeCommIds = builder.stripCommunityIdsPrefix(new ArrayList<String>());
        assertEquals(homeCommIds, null);

        homeCommIds = builder.stripCommunityIdsPrefix(getHomeCommunitiesIdsWithPrefix());
        assertEquals(homeCommIds.get(0), "1.1");
        assertEquals(homeCommIds.get(1), "2.2");
    }

    private RetrievePatientCorrelationsRequestType createRetrievePatientCorrelationsRequest() {
        RetrievePatientCorrelationsRequestType patcorrReq = new RetrievePatientCorrelationsRequestType();
        patcorrReq.setQualifiedPatientIdentifier(createQualifiedSubjectIdentifier());
        patcorrReq.setAssertion(createAssertion());
        patcorrReq.getTargetAssigningAuthority().add("2.2");
        return patcorrReq;
    }

    private RetrievePatientCorrelationsRequestType createRetrievePatientCorrelationsRequestWhenAANull() {
        RetrievePatientCorrelationsRequestType patcorrReq = new RetrievePatientCorrelationsRequestType();
        patcorrReq.setQualifiedPatientIdentifier(createQualifiedSubjectIdentifier());
        patcorrReq.setAssertion(createAssertion());
        return patcorrReq;
    }

    private QualifiedSubjectIdentifierType createQualifiedSubjectIdentifier() {
        QualifiedSubjectIdentifierType subject = new QualifiedSubjectIdentifierType();
        subject.setAssigningAuthorityIdentifier("1.1");
        subject.setSubjectIdentifier("D123401");
        return subject;
    }

    private AssertionType createAssertion() {
        AssertionType assertion = new AssertionType();
        return assertion;
    }

    private List<String> getHomeCommunitiesIdsWithPrefix() {
        List<String> homeCommunityIds = new ArrayList<String>();
        homeCommunityIds.add("urn:oid:1.1");
        homeCommunityIds.add("2.2");
        return homeCommunityIds;
    }

}
