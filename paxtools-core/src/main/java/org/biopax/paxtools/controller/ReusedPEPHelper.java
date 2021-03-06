package org.biopax.paxtools.controller;

import org.biopax.paxtools.model.BioPAXElement;
import org.biopax.paxtools.model.Model;
import org.biopax.paxtools.model.level2.physicalEntityParticipant;
import org.biopax.paxtools.model.level2.sequenceFeature;
import org.biopax.paxtools.model.level2.sequenceParticipant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * This class contains methods for handling reused PEPs - a historically common problem in BioPAX L2 exports.
 *
 * Note: This class might eventually be deprecated as this problem is mostly fixed and BioPAX L3 is making L2
 * obsolete.
 */
public class ReusedPEPHelper
{
    private static final Logger log = LoggerFactory.getLogger(ReusedPEPHelper.class);

    private final Model model;
    private final Map<physicalEntityParticipant, physicalEntityParticipant> duplicatedPeps;

    /**
     * @param model the biopax model
     */
    public ReusedPEPHelper(Model model)
    {
        this.model = model;
        duplicatedPeps = new HashMap<physicalEntityParticipant, physicalEntityParticipant>();
    }

    public Object fixReusedPEP(physicalEntityParticipant pep, BioPAXElement bpe)
    {
        if (duplicated(pep, bpe))
        {
        	log.info(pep.getUri() + " is reused, duplicating it to fix");

        	String syntheticID = createSyntheticID(pep, bpe);
            
            if (model.containsID(syntheticID))
            {
                pep = (physicalEntityParticipant) model.getByID(syntheticID);
            }
            else
            {
            	physicalEntityParticipant duplicated = (physicalEntityParticipant) model
            		.addNew(pep.getModelInterface(), syntheticID);
                duplicatedPeps.put(duplicated, pep);
                pep = duplicated;
            }
        }
        return pep;
    }

    private boolean duplicated(physicalEntityParticipant pep, BioPAXElement bpe)
    {
        boolean result = false;

        if (!pep.isPARTICIPANTSof().isEmpty())
        {
            if (pep.isPARTICIPANTSof().iterator().next().equals(bpe))
            {
                log.debug("Unexpected multiple participant statements");
            }
            else
            {
                result = true;
            }
        }
        else if (pep.isCOMPONENTof() != null)
        {
            if (pep.isCOMPONENTof().equals(bpe))
            {
                log.debug("Unexpected multiple participant statements");
            }
            else
            {
                result = true;
            }
        }

        return result;

    }

    private String createSyntheticID(physicalEntityParticipant pep,
                                     BioPAXElement bpe)
    {
        return "http://patywaycommons.org/synthetic"
               + createDataStringFromURI(pep.getUri(),bpe.getUri());
    }

    private String createDataStringFromURI(String... uris)
    {
        String ssp = "";
        String fragment = "";

        for (String uri : uris)
        {
            try
            {
                URI suri = new URI(uri);
                ssp += suri.getSchemeSpecificPart() + "_";
                fragment += suri.getFragment() + "_";
            }
            catch (URISyntaxException e)
            {
                throw new RuntimeException(e);
            }
        }
        return ssp + "#" + fragment;
    }

    public void copyPEPFields()
    {
        Set<physicalEntityParticipant> physicalEntityParticipants = duplicatedPeps.keySet();
        for (physicalEntityParticipant dup : physicalEntityParticipants)
        {
            copyPEPFields(dup, duplicatedPeps.get(dup));
        }
    }

    private void copyPEPFields(physicalEntityParticipant duplicated,
                               physicalEntityParticipant pep)
    {
        duplicated.setCELLULAR_LOCATION(pep.getCELLULAR_LOCATION());
        duplicated.setCOMMENT(pep.getCOMMENT());
        duplicated.setSTOICHIOMETRIC_COEFFICIENT(pep.getSTOICHIOMETRIC_COEFFICIENT());
        duplicated.setPHYSICAL_ENTITY(pep.getPHYSICAL_ENTITY());
        if (pep instanceof sequenceParticipant)
        {
            Set<sequenceFeature> sfSet =
                    ((sequenceParticipant) pep)
                            .getSEQUENCE_FEATURE_LIST();
            for (sequenceFeature sf : sfSet)
            {
                ((sequenceParticipant) duplicated)
                        .addSEQUENCE_FEATURE_LIST(sf);
            }
        }
	}

}
