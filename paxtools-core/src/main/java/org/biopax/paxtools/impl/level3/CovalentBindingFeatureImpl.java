package org.biopax.paxtools.impl.level3;

import org.biopax.paxtools.model.BioPAXElement;
import org.biopax.paxtools.model.level3.CovalentBindingFeature;
import org.biopax.paxtools.model.level3.SequenceModificationVocabulary;


public class CovalentBindingFeatureImpl extends BindingFeatureImpl implements CovalentBindingFeature
{
	private SequenceModificationVocabulary modificationType;

	public CovalentBindingFeatureImpl() {
	}
	
	@Override
	public Class<? extends CovalentBindingFeature> getModelInterface()
	{
		return CovalentBindingFeature.class;
	}

	public SequenceModificationVocabulary getModificationType()
	{
		return this.modificationType;
	}

	public void setModificationType(SequenceModificationVocabulary modificationType)
	{
		this.modificationType = modificationType;
	}

	@Override
	protected boolean semanticallyEquivalent(BioPAXElement element)
	{
		if(!(element instanceof CovalentBindingFeature))
			return false;
		
		CovalentBindingFeature that = (CovalentBindingFeature) element;
		SequenceModificationVocabulary type = that.getModificationType();
		boolean value = ((type == null) ?
		                this.getModificationType() == null :
		                this.getModificationType().equals(type));
		return value && super.semanticallyEquivalent(element);
	}

	@Override
	public int equivalenceCode()
	{
		return super.equivalenceCode() +
		       29 * (modificationType == null ? 0 : modificationType.equivalenceCode());
	}
}
