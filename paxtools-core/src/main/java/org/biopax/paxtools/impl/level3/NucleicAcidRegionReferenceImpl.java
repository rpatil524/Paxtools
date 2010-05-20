package org.biopax.paxtools.impl.level3;

import org.biopax.paxtools.model.level3.NucleicAcidReference;
import org.biopax.paxtools.model.level3.NucleicAcidRegionReference;
import org.biopax.paxtools.model.level3.SequenceLocation;
import org.biopax.paxtools.model.level3.SequenceRegionVocabulary;

import javax.persistence.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@org.hibernate.annotations.Entity(dynamicUpdate = true, dynamicInsert = true)
public abstract class NucleicAcidRegionReferenceImpl
		extends SequenceEntityReferenceImpl
		implements NucleicAcidRegionReference
{
	private Set<NucleicAcidRegionReference> subRegion;
	private SequenceLocation absoluteRegion;
	private Set<SequenceRegionVocabulary> regionType;
	private NucleicAcidReference containerEntityReference;


	private Set<NucleicAcidRegionReference> subRegionOf;


	public NucleicAcidRegionReferenceImpl() {
		regionType = new HashSet<SequenceRegionVocabulary>();
		subRegion = new HashSet<NucleicAcidRegionReference>();
	}
	
	@ManyToMany(targetEntity = NucleicAcidRegionReferenceImpl.class, cascade={CascadeType.ALL})
	@JoinTable(name="subRegion")
	public Set<NucleicAcidRegionReference> getSubRegion()
	{
		return subRegion;
	}

	protected void setSubRegion(Set<NucleicAcidRegionReference> subRegion)
	{
		this.subRegion = subRegion;
	}

	public void addSubRegion(NucleicAcidRegionReference regionReference)
	{
		subRegion.add(regionReference);
		this.subRegionOf.add(regionReference);
	}

	public void removeSubRegion(NucleicAcidRegionReference regionReference)
	{
		subRegion.remove(regionReference);
		this.subRegionOf.remove(regionReference);

	}

	@ManyToMany(targetEntity = NucleicAcidRegionReferenceImpl.class, mappedBy = "subRegion")
	public Set<NucleicAcidRegionReference> getSubRegionOf()
	{
		return subRegionOf;
	}

	protected void setSubRegionOf(Set<NucleicAcidRegionReference> subRegionOf)
	{
		this.subRegionOf = subRegionOf;
	}


	@OneToOne(targetEntity = SequenceLocationImpl.class, cascade={CascadeType.ALL})
	public SequenceLocation getAbsoluteRegion()
	{
		return this.absoluteRegion;
	}

	public void setAbsoluteRegion(SequenceLocation absoluteRegion)
	{
		this.absoluteRegion = absoluteRegion;

	}

	@ManyToMany(targetEntity = SequenceRegionVocabularyImpl.class, cascade={CascadeType.ALL})
	@JoinTable(name="regionType") 		
	public Set<SequenceRegionVocabulary> getRegionType()
	{
		return this.regionType;
	}

	public void addRegionType(SequenceRegionVocabulary regionType)
	{
		this.regionType.add(regionType);
	}

	public void removeRegionType(SequenceRegionVocabulary regionType)
	{
		this.regionType.remove(regionType);
	}

	protected void setRegionType(Set<SequenceRegionVocabulary> regionType)
	{
		this.regionType = regionType;
	}

	@ManyToOne(targetEntity = NucleicAcidReferenceImpl.class, cascade = {CascadeType.ALL})
	public NucleicAcidReference getContainerEntityReference()
	{
		return this.containerEntityReference;
	}

	public void setContainerEntityReference(NucleicAcidReference containerEntityReference)
	{
		this.containerEntityReference = containerEntityReference;
	}

}
