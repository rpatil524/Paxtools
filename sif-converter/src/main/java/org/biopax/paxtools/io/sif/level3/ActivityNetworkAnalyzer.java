package org.biopax.paxtools.io.sif.level3;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.biopax.paxtools.controller.ModelUtils;
import org.biopax.paxtools.model.BioPAXElement;
import org.biopax.paxtools.model.BioPAXLevel;
import org.biopax.paxtools.model.Model;
import org.biopax.paxtools.model.level3.*;
import org.biopax.paxtools.model.level3.Process;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * This class analyzes the network to predict the effect of modifications on PEs on their activity.
 *
 * NOTE: This method is experimental and makes several assumptions based on the existing data sources.
 * It might not work universally or return unexpected results for untested data sets. Use with caution.
 *
 * @author Emek Demir
 */
public class ActivityNetworkAnalyzer
{
    private static final Log log = LogFactory.getLog(ActivityNetworkAnalyzer.class);

	Map<BioPAXElement, Set<PEStateChange>> stateChangeMap;

	Map<Conversion, Set<EntityReference>> extendedControls;

	private static final Control EXTENDED;

	static
	{
		EXTENDED = BioPAXLevel.L3.getDefaultFactory().create(
				Control.class,"http://biopax" + ".org/generated/ExtendedControl");
		EXTENDED.setControlType(ControlType.ACTIVATION);
		EXTENDED.addName("Inferred from complex binding");
	}

	/**
	 * Given a model this method will analyze the states and populate the stateChangeMap and extendedControls maps.
	 * @param model to be analyzed.
	 */
	public void analyzeStates(Model model)
	{
		GroupMap groupMap = Grouper.inferGroups(model);
		ModelUtils.replaceEquivalentFeatures(model);

		stateChangeMap = new HashMap<BioPAXElement, Set<PEStateChange>>();
		extendedControls = new HashMap<Conversion, Set<EntityReference>>();

		for (EntityReference pr : model.getObjects(EntityReference.class))
		{
			if (!pr.getRDFId().startsWith("http://biopax.org/generated/fixer/normalizeGenerics/"))
			{

				Set<PEStateChange> stateChanges = stateChangeMap.get(pr);
				if (stateChanges == null)
				{
					stateChanges = new HashSet<PEStateChange>();
					stateChangeMap.put(pr, stateChanges);
				}
				for (SimplePhysicalEntity spe : pr.getEntityReferenceOf())
				{
					scanInteractions(groupMap, stateChanges, pr, spe);

				}
			}
		}
	}

	private void scanInteractions(GroupMap groupMap, Set<PEStateChange> stateChanges, EntityReference pr,
			PhysicalEntity spe)
	{
		for (Interaction interaction : spe.getParticipantOf())
		{
			if (interaction instanceof Conversion)
			{
				Simplify.entityHasAChange(pr, (Conversion) interaction, groupMap, stateChanges, extendedControls);
			}
		}

		for (PhysicalEntity generic : spe.getMemberPhysicalEntityOf())
		{

			scanInteractions(groupMap, stateChanges, pr, generic);
		}

		for (Complex complex : spe.getComponentOf())
		{

			scanInteractions(groupMap, stateChanges, pr, complex);
		}
	}

	/**
	 * @param spe
	 * @return all preceding states of a SimplePhysicalEntity. Preceding states are other spes of the
	 * same EntityReference that are converted into this spe in one conversion.
	 */
	public Set<SimplePhysicalEntity> getPrecedingStates(SimplePhysicalEntity spe)
	{
		Set<SimplePhysicalEntity> result = new HashSet<SimplePhysicalEntity>();
		EntityReference er = spe.getEntityReference();
		Set<PEStateChange> peStateChanges = stateChangeMap.get(er);
		for (PEStateChange peStateChange : peStateChanges)
		{
			SimplePhysicalEntity next = peStateChange.changedInto(spe);
			if (next != null)
			{
				result.add(peStateChange.left);
			}
		}
		return result;
	}

	public Set<PEStateChange> getAllStates(EntityReference er)
	{
		return stateChangeMap.get(er);
	}
	/**
	 * @param spe
	 * @return all succeeding states of a SimplePhysicalEntity. Preceding states are other spes of the
	 * same EntityReference that this spe is converted into in one conversion.
	 */
	public Set<SimplePhysicalEntity> getSucceedingStates(SimplePhysicalEntity spe)
	{
		Set<SimplePhysicalEntity> result = new HashSet<SimplePhysicalEntity>();
		EntityReference er = spe.getEntityReference();
		Set<PEStateChange> peStateChanges = stateChangeMap.get(er);
		for (PEStateChange peStateChange : peStateChanges)
		{
			SimplePhysicalEntity next = peStateChange.changedFrom(spe);
			if (next != null)
			{
				result.add(peStateChange.left);
			}
		}
		return result;
	}

	/**
	 * This method writes out the results of the stateNetworkAnalysis for each detected state change.
	 *
	 * @param out
	 * @throws IOException
	 */
	public void writeStateNetworkAnalysis(OutputStream out) throws IOException
	{
		Writer writer = new OutputStreamWriter(out);
		log.debug("stateChangeMap.size = " + stateChangeMap.values().size());
		int ineligible = 0;
		int eligible = 0;
		for (BioPAXElement bpe : stateChangeMap.keySet())
		{
			if (bpe instanceof ProteinReference)
			{
				EntityReference er = (EntityReference) bpe;
				Set<PEStateChange> sc = stateChangeMap.get(bpe);
				for (PEStateChange sChange : sc)
				{
					if (isEligibleProteinModification(sChange))
					{
						Set<Pathway> pathwayComponentOf = sChange.getConv().getPathwayComponentOf();
						for (Pathway pathway : pathwayComponentOf)
						{
							String s = pathway.getName().toString();
							if (s.isEmpty())
							{
								log.debug("Empty name pathway = " + pathway);
							}
							writer.write(s + ";");
						}
						writer.write("\t");
						writer.write(sChange.getConv().getName().toString());
						writer.write("\t");
						writer.write(er.getName().toString());
						writer.write("\t");
						writer.write(er.getXref().toString());
						writer.write("\t");
						writer.write(printControls(getDeltaControl(sChange)));
						writer.write("\t");
						writer.write(sChange.getDeltaFeatures().toString());
						writer.write("\t");
						writer.write(sChange.getControllersAsString());
						writer.write("\n");

						eligible++;
					} else
					{
						ineligible++;
					}

				}
			} else
			{
				log.debug("bpe = " + bpe);
			}

		}
		log.debug("ineligible = " + ineligible);
		log.debug("eligible = " + eligible);
		writer.flush();


	}

	private Map<Control, Boolean> getDeltaControl(PEStateChange sChange)
	{
		Map<Control, Boolean> dc = sChange.getDeltaControls();
		if (!dc.isEmpty() || sChange.getRight() == null)
		{
			return dc;
		}
		else
		{
			//Look ahead:
			PhysicalEntity rightRoot = sChange.getRightRoot();
			for (PEStateChange next : stateChangeMap.get(sChange.getRight().getEntityReference()))
			{
				if (nextFollows(rightRoot, next))
				{
					dc = next.getDeltaControls();
				}
			}
			if (dc.isEmpty())
			{
				dc = new HashMap<Control, Boolean>();
				HashSet<SimplePhysicalEntity> partners = new HashSet<SimplePhysicalEntity>();
				Simplify.getSimpleMembers(rightRoot, partners);
				for (SimplePhysicalEntity partner : partners)
				{
					for (PEStateChange next : stateChangeMap.get(partner.getEntityReference()))
					{
						if (nextFollows(rightRoot, next))
						{
							Set<EntityReference> xC = extendedControls.get(next.getConv());
							if (xC != null && xC.contains(sChange.getRight().getEntityReference()))
							{
								log.debug("extended");
								dc.put(EXTENDED, false);
							}
						}
					}

				}
			}
		}
		return dc;
	}

	private boolean nextFollows(PhysicalEntity rightRoot, PEStateChange next)
	{
		return next != null && next.getLeftRoot() != null && next.getLeftRoot().equals(rightRoot);
	}

	private String printControls(Map<Control, Boolean> dc)
	{
		StringBuilder ctString = new StringBuilder();
		for (Control control : dc.keySet())
		{
			Boolean direction = dc.get(control);
			ctString.append(direction ? "Lost activity:" : "Gained activity").append(":").append(
					control.getControlType());
			for (Process process : control.getControlled())
			{
				ctString.append(process.getName()).append(" ,");
			}
			ctString.append("; ");
		}
		return ctString.toString();
	}

	private boolean isEligibleProteinModification(PEStateChange sChange)
	{
		for (EntityFeature ef : sChange.getDeltaFeatures().keySet())
		{
			if (ef instanceof ModificationFeature && !sChange.getDeltaFeatures().get(ef).equals(ChangeType.UNCHANGED))
			{
				return true;
			}
		}
		return false;
	}


}