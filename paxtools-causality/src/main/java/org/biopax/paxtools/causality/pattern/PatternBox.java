package org.biopax.paxtools.causality.pattern;

import org.biopax.paxtools.causality.pattern.c.ConBox;
import org.biopax.paxtools.causality.pattern.c.Equality;
import org.biopax.paxtools.causality.pattern.c.Type;
import org.biopax.paxtools.model.level3.SimplePhysicalEntity;

/**
 * @author Ozgun Babur
 */
public class PatternBox
{
	public static Pattern inSameComplex()
	{
		Pattern p = new Pattern(5);
		int i = 0;
		p.addConstraint(ConBox.erToPE(), i, ++i);
		p.addConstraint(ConBox.complexes(), i, ++i);
		p.addConstraint(ConBox.complexMembers(), i, ++i);
		p.addConstraint(new Type(SimplePhysicalEntity.class), i);
		p.addConstraint(ConBox.peToER(), i, ++i);
		p.addConstraint(new Equality(false), 0, i);
		return p;
	}

	public static Pattern controlsStateChange(boolean considerGenerics)
	{
		Pattern p = new Pattern(considerGenerics ? 13 : 10);
		int i = 0;
		p.addConstraint(ConBox.erToPE(), i, ++i);
		p.addConstraint(ConBox.withComplexes(), i, ++i);
		if (considerGenerics) p.addConstraint(ConBox.genericEquiv(), i, ++i);
		p.addConstraint(ConBox.controlsConv(), i, ++i);
		p.addConstraint(ConBox.left(), i, ++i); // PE_L
		p.addConstraint(ConBox.right(), i-1, ++i); // PE_R
		p.addConstraint(new Equality(false), i-1, i);
		p.addConstraint(ConBox.withSimpleMembers(), i-1, ++i);
		p.addConstraint(ConBox.withSimpleMembers(), i-1, ++i);
		if (considerGenerics) p.addConstraint(ConBox.genericEquiv(), i-1, i++);
		if (considerGenerics) p.addConstraint(ConBox.genericEquiv(), i-1, i++);
		p.addConstraint(ConBox.peToER(), i-1, ++i);
		p.addConstraint(ConBox.peToER(), i-1, ++i);
		p.addConstraint(new Equality(true), i-1, i);
		return p;
	}
}